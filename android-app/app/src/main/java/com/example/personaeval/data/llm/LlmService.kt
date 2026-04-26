package com.example.personaeval.data.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import com.example.personaeval.data.local.database.AppDatabase
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 阿里云千问 API 服务 - 移植自 Python 后端 extractor.py + generator.py
 * 
 * Dashscope API 返回格式（新版）：
 * {"output": {"choices": [{"message": {"role": "assistant", "content": "..."}}]}, "request_id": "...", ...}
 */
class LlmService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)  // 批量生成需要更长超时
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"

        private val TEMPLATES = mapOf(
            "80/20" to TemplateConfig("偏夸奖", "80% 夸奖，20% 委婉提醒不足。以肯定为主，委婉地提出 1 个小建议。"),
            "65/35" to TemplateConfig("平衡", "65% 夸奖，35% 建设性反馈。先肯定优点，然后具体指出需要改进的地方，给出可操作的建议。"),
            "90/10" to TemplateConfig("鼓励", "90% 鼓励，10% 轻微提醒。几乎全是正面评价，不足的地方一笔带过，用希望/期待的语气。"),
        )
    }

    data class TemplateConfig(val label: String, val instruction: String)

    // === 数据类 ===

    @Serializable
    data class ExtractResult(
        val grade: String = "",
        val className: String = "",
        val totalCount: Int = 0,
        val students: List<ExtractedStudent> = emptyList()
    )

    @Serializable
    data class ExtractedStudent(
        val name: String = "",
        val traitsRaw: String = "",
        val traitsPct: Map<String, Float> = emptyMap()
    )

    @Serializable
    data class EvaluationResult(
        val template: String,
        val label: String,
        val content: String,
        val personalEval: String = "",   // 个人评价模块
        val suggestion: String = ""      // 提升建议模块
    )

    @Serializable
    data class TemplateGenerateResult(
        val personalEval: String = "",
        val suggestion: String = ""
    )

    // === 核心方法 ===

    /**
     * 从文本中提取班级+学生+性格占比（对标 extractor.py）
     */
    suspend fun extractStudents(text: String, apiKey: String?): ExtractResult {
        val key = apiKey?.ifBlank { null } ?: AppDatabase.DEFAULT_API_KEY
        val prompt = """你是一个教育机构的老师。请分析以下文本，提取班级和学生信息，并分析每个学生的性格特点百分比。

输入文本：
$text

要求：
1. 提取年级和班级名称
2. 提取每个学生的名字和特点描述
3. 把每个学生的特点转成百分比格式（总和100%），提取3-6个特点标签
4. 负面特点用委婉的标签，例如："烦人精"→"爱提问"，"懒"→"缺乏动力"，"不爱作答"→"偏重口头表达"
5. 输出纯JSON格式

输出格式：
{"grade":"八年级","className":"a班","students":[{"name":"小明","traitsRaw":"活泼好动...","traitsPct":{"活泼好动":40,"爱提问":30,"偏重口头表达":20,"缺乏专注力":10}}]}

注意：只输出JSON，不要其他文字。"""

        val responseText = callLlm(prompt, key, temperature = 0.5, maxTokens = 1000)
            ?: return ExtractResult()

        val cleaned = cleanJson(responseText)
        return try {
            json.decodeFromString<ExtractResult>(cleaned)
        } catch (e: Exception) {
            // 尝试宽松解析：把 students 从 JSON 中提取
            try {
                val jsonObj = json.parseToJsonElement(cleaned).jsonObject
                val studentsArray = jsonObj["students"]?.jsonArray ?: return ExtractResult()
                val students = studentsArray.map { elem ->
                    val obj = elem.jsonObject
                    ExtractedStudent(
                        name = obj["name"]?.jsonPrimitive?.content ?: "",
                        traitsRaw = obj["traitsRaw"]?.jsonPrimitive?.content ?: "",
                        traitsPct = obj["traitsPct"]?.jsonObject?.mapValues { 
                            it.value.jsonPrimitive.floatOrNull ?: 0f 
                        } ?: emptyMap()
                    )
                }
                ExtractResult(
                    grade = jsonObj["grade"]?.jsonPrimitive?.content ?: "",
                    className = jsonObj["className"]?.jsonPrimitive?.content ?: "",
                    totalCount = jsonObj["totalCount"]?.jsonPrimitive?.intOrNull ?: students.size,
                    students = students
                )
            } catch (e2: Exception) {
                ExtractResult()
            }
        }
    }

    /**
     * 为单个学生生成3份评价（对标 generator.py generate_all_templates）
     * 支持自定义模板
     */
    suspend fun generateEvaluations(
        studentName: String,
        traitsRaw: String,
        traitsPct: Map<String, Float>,
        lessonContent: String,
        lessonNotes: String = "",
        apiKey: String? = null,
        templateId: Int? = null,
        personalEvalFormat: String? = null,
        suggestionFormat: String? = null
    ): List<EvaluationResult> {
        val key = apiKey?.ifBlank { null } ?: AppDatabase.DEFAULT_API_KEY
        val results = mutableListOf<EvaluationResult>()

        for ((templateKey, config) in TEMPLATES) {
            try {
                val content = generateSingle(
                    studentName, traitsRaw, traitsPct, lessonContent, lessonNotes,
                    templateKey, config, key,
                    personalEvalFormat, suggestionFormat
                )
                results.add(EvaluationResult(templateKey, config.label, content))
            } catch (e: Exception) {
                results.add(EvaluationResult(templateKey, config.label, "生成失败：${e.message}"))
            }
        }

        // 如果有自定义模板，额外生成一份按模板格式的评价
        if (personalEvalFormat != null || suggestionFormat != null) {
            try {
                val (pEval, sugg) = generateWithTemplate(
                    studentName, traitsRaw, traitsPct, lessonContent, lessonNotes,
                    personalEvalFormat ?: "", suggestionFormat ?: "", key
                )
                // 将模板化内容附加到每个结果
                results.forEachIndexed { idx, _ ->
                    results[idx] = results[idx].copy(personalEval = pEval, suggestion = sugg)
                }
            } catch (_: Exception) { }
        }

        return results
    }

    /**
     * 批量生成评价
     */
    suspend fun batchGenerate(
        students: List<BatchStudentInfo>,
        lessonContent: String,
        lessonNotesGlobal: String = "",
        apiKey: String? = null
    ): Map<String, List<EvaluationResult>> {
        val key = apiKey?.ifBlank { null } ?: AppDatabase.DEFAULT_API_KEY
        val results = mutableMapOf<String, List<EvaluationResult>>()

        for (student in students) {
            try {
                val evals = generateEvaluations(
                    studentName = student.name,
                    traitsRaw = student.traitsRaw,
                    traitsPct = student.traitsPct,
                    lessonContent = lessonContent,
                    lessonNotes = student.lessonNotes,
                    apiKey = key
                )
                results[student.name] = evals
            } catch (e: Exception) {
                results[student.name] = listOf(
                    EvaluationResult("80/20", "偏夸奖", "生成失败：${e.message}")
                )
            }
        }
        return results
    }

    data class BatchStudentInfo(
        val name: String,
        val traitsRaw: String,
        val traitsPct: Map<String, Float>,
        val lessonNotes: String = ""
    )

    /**
     * 用自定义模板格式分别生成个人评价和提升建议
     */
    private suspend fun generateWithTemplate(
        studentName: String,
        traitsRaw: String,
        traitsPct: Map<String, Float>,
        lessonContent: String,
        lessonNotes: String,
        personalEvalFormat: String,
        suggestionFormat: String,
        apiKey: String
    ): TemplateGenerateResult {
        val traitsText = traitsPct.entries.joinToString("、") { "${it.key} (${it.value.toInt()}%)" }

        val prompt = """你是一位培训机构的老师。请根据以下信息，分别生成"个人评价"和"提升建议"两个模块的内容。

学生：$studentName
性格特点：$traitsText
原始描述：$traitsRaw
课程内容：$lessonContent
${if (lessonNotes.isNotBlank()) "当堂表现：$lessonNotes" else ""}

请严格按以下JSON格式输出，不要加其他文字：
{"personalEval":"这里是个人评价内容","suggestion":"这里是提升建议内容"}

个人评价格式要求：
$personalEvalFormat

提升建议格式要求：
$suggestionFormat

写作要求：
1. 用平常聊天的语气，像发微信一样自然
2. 每个模块 50-100 字
3. 负面特点委婉表达
4. emoji 最多1个
5. 不要用：展现了、彰显了、优异、卓越、显著、积极态度、值得肯定、有待加强、需要改进"""

        val responseText = callLlm(prompt, apiKey, temperature = 0.7, maxTokens = 500)
            ?: return TemplateGenerateResult()

        val cleaned = cleanJson(responseText)
        return try {
            json.decodeFromString<TemplateGenerateResult>(cleaned)
        } catch (e: Exception) {
            // 宽松解析
            try {
                val jsonObj = json.parseToJsonElement(cleaned).jsonObject
                TemplateGenerateResult(
                    personalEval = jsonObj["personalEval"]?.jsonPrimitive?.content ?: "",
                    suggestion = jsonObj["suggestion"]?.jsonPrimitive?.content ?: ""
                )
            } catch (e2: Exception) {
                TemplateGenerateResult()
            }
        }
    }

    /**
     * AI 生成学习模板（根据用户描述）
     */
    suspend fun generateTemplateByAi(description: String, apiKey: String? = null): TemplateGenerateResult {
        val key = apiKey?.ifBlank { null } ?: AppDatabase.DEFAULT_API_KEY

        val prompt = """你是一位教育培训专家。请根据以下描述，生成一份学生评价模板，包含"个人评价"和"提升建议"两个模块的格式定义。

用户描述：$description

请严格按以下JSON格式输出，不要加其他文字：
{"personalEval":"个人评价模块的格式定义，描述如何评价学生","suggestion":"提升建议模块的格式定义，描述如何给出建议"}

要求：
1. 每个模块格式定义要清晰，包含关键要素和写作方向
2. 格式定义应能让 AI 按照此格式生成具体评价
3. 语言简洁明了，200字以内"""

        val responseText = callLlm(prompt, key, temperature = 0.7, maxTokens = 500)
            ?: return TemplateGenerateResult()

        val cleaned = cleanJson(responseText)
        return try {
            json.decodeFromString<TemplateGenerateResult>(cleaned)
        } catch (e: Exception) {
            try {
                val jsonObj = json.parseToJsonElement(cleaned).jsonObject
                TemplateGenerateResult(
                    personalEval = jsonObj["personalEval"]?.jsonPrimitive?.content ?: "",
                    suggestion = jsonObj["suggestion"]?.jsonPrimitive?.content ?: ""
                )
            } catch (e2: Exception) {
                TemplateGenerateResult()
            }
        }
    }

    // === 私有方法 ===

    private suspend fun generateSingle(
        studentName: String,
        traitsRaw: String,
        traitsPct: Map<String, Float>,
        lessonContent: String,
        lessonNotes: String,
        templateKey: String,
        config: TemplateConfig,
        apiKey: String,
        personalEvalFormat: String? = null,
        suggestionFormat: String? = null
    ): String {
        val traitsText = traitsPct.entries.joinToString("、") { "${it.key} (${it.value.toInt()}%)" }

        val customFormatSection = if (!personalEvalFormat.isNullOrBlank() || !suggestionFormat.isNullOrBlank()) {
            """
自定义格式要求：
${if (!personalEvalFormat.isNullOrBlank()) "个人评价模块格式：\n$personalEvalFormat" else ""}
${if (!suggestionFormat.isNullOrBlank()) "提升建议模块格式：\n$suggestionFormat" else ""}

请在评价内容中按上述格式分别生成"个人评价"和"提升建议"两个独立部分。"""
        } else ""

        val prompt = """你是一位培训机构的老师，需要给学生家长发课后反馈。请用平常跟人微信聊天的语气写。

学生：$studentName
性格特点：$traitsText
原始描述：$traitsRaw
课程内容：$lessonContent
${if (lessonNotes.isNotBlank()) "当堂表现：$lessonNotes" else ""}

${config.instruction}

按这个格式写：

${studentName}家长您好，这是${studentName}近期的课程反馈：

课程主题：$lessonContent

课堂表现：（写一段话）

课后建议：（写 1-2 条具体建议）
$customFormatSection
重要写作要求（必须遵守）：
1. 用平常聊天的语气，像发微信一样自然
2. 不要用这些词：展现了、展示出、彰显了、优异、卓越、显著、积极态度、值得肯定、有待加强、需要改进、建议加强、较为、十分、进一步、提升、培养、至关重要、不可或缺、举足轻重、意味深长、引人入胜、丰富多彩、此外、总而言之、综上所述、总体而言
3. 不要用"不仅...更..."、"不仅...还..."这类结构
4. 不要用破折号——
5. 不要用"作为"代替"是"，直接说"是"
6. 内容要具体，提到学生实际表现
7. 总字数 100-200 字
8. 负面特点要委婉表达，比如"烦人精"说成"爱思考"，"懒"说成"需要更多动力"
9. emoji 最多用 1-2 个，放在句末
10. 写完后自己检查一遍，把上面禁止的词都替换掉"""

        val responseText = callLlm(prompt, apiKey, temperature = 0.7, maxTokens = 500)
            ?: return "评价生成失败，请重试。"

        return postHumanize(responseText.trim())
    }

    /**
     * 调用阿里云 Dashscope API
     * 
     * 请求格式：需要加 result_format=message 确保返回 choices 格式
     * 返回格式：{"output": {"choices": [{"message": {"role": "assistant", "content": "..."}}]}}
     */
    private suspend fun callLlm(prompt: String, apiKey: String, temperature: Double = 0.7, maxTokens: Int = 500): String? {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = buildJsonObject {
                    put("model", "qwen-turbo")
                    put("input", buildJsonObject {
                        put("messages", buildJsonArray {
                            add(buildJsonObject {
                                put("role", "user")
                                put("content", prompt)
                            })
                        })
                    })
                    put("parameters", buildJsonObject {
                        put("temperature", temperature)
                        put("max_tokens", maxTokens)
                        put("result_format", "message")  // 确保返回 choices 格式
                    })
                }.toString()

                val request = Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody(mediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: return@withContext null
                    
                    if (!response.isSuccessful) {
                        return@withContext "API_ERROR: HTTP ${response.code} - $responseBody"
                    }

                    val jsonResp = json.parseToJsonElement(responseBody).jsonObject
                    
                    // 新版 Dashscope API 返回格式：output.choices[0].message.content
                    val content = jsonResp["output"]?.jsonObject
                        ?.get("choices")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.content
                    
                    if (content != null) return@withContext content

                    // 兼容旧格式：output.text
                    val text = jsonResp["output"]?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content
                    
                    if (text != null) return@withContext text

                    // 都没有，返回原始响应用于调试
                    return@withContext "API_PARSE_ERROR: $responseBody"
                }
            } catch (e: Exception) {
                "API_EXCEPTION: ${e.javaClass.simpleName} - ${e.message}"
            }
        }
    }

    /** 清理 JSON 字符串中的 markdown 代码块标记 */
    private fun cleanJson(text: String): String {
        var cleaned = text.trim()
        // 去掉 API 错误前缀
        if (cleaned.startsWith("API_ERROR:") || cleaned.startsWith("API_PARSE_ERROR:") || cleaned.startsWith("API_EXCEPTION:")) {
            return cleaned  // 保留错误信息
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replace(Regex("^```(?:json)?\\s*"), "")
            cleaned = cleaned.replace(Regex("\\s*```$"), "")
        }
        return cleaned.trim()
    }

    /** 后处理：清除 AI 写作痕迹（对标 generator.py post_humanize） */
    private fun postHumanize(text: String): String {
        // 如果是 API 错误，直接返回
        if (text.startsWith("API_ERROR:") || text.startsWith("API_PARSE_ERROR:") || text.startsWith("API_EXCEPTION:")) {
            return "评价生成失败：${text.substringAfter(": ")}"
        }
        
        var result = text
        val replacements = mapOf(
            "展现了" to "表现了", "展示出" to "表现出", "彰显了" to "说明了",
            "优异" to "很好", "卓越" to "不错", "显著" to "明显",
            "积极态度" to "劲头", "展现出了" to "表现出了",
            "具有良好的" to "有不错的", "值得肯定" to "值得表扬",
            "有待加强" to "还要加油", "需要改进" to "需要注意",
            "建议加强" to "可以多", "较为" to "比较", "十分" to "很",
            "进一步" to "再", "提升" to "提高", "培养" to "养成",
            "良好的" to "好的", "至关重要" to "很重要",
            "不可或缺的" to "不可少的", "不仅" to "不但",
            "此外" to "另外", "总而言之" to "总的来说",
            "综上所述" to "", "总体而言" to "总的来说",
        )
        for ((from, to) in replacements) {
            result = result.replace(from, to)
        }
        result = result.replace("——", "，").replace("—", "，")
        result = result.replace("能够", "能").replace("继续保持", "保持住")
        result = result.replace("作为", "是")
        result = result.replace("代表着", "是").replace("意味着", "说明")
        result = Regex("不仅仅是(.+?)，更是").replace(result, "主要是")
        result = Regex("不仅是(.+?)，也是").replace(result, "既是")
        return result.trim()
    }
}
