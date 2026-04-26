package com.example.personaeval

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.personaeval.theme.MyApplicationTheme
import com.example.personaeval.di.DiModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiModule.init(applicationContext)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // 有默认 key，不需要强制弹窗；仅在用户主动设置时弹出
                var showApiKeyDialog by remember { mutableStateOf(false) }
                var showSettingsScreen by remember { mutableStateOf(false) }

                if (showApiKeyDialog) {
                    ApiKeyDialog(
                        hasExistingKey = DiModule.hasApiKey(),
                        currentKey = DiModule.getApiKey(),
                        onConfirm = { key ->
                            DiModule.saveApiKey(key)
                            showApiKeyDialog = false
                        },
                        onDismiss = {
                            showApiKeyDialog = false
                        }
                    )
                }

                if (showSettingsScreen) {
                    SettingsScreen(
                        onBack = { showSettingsScreen = false },
                        onNavigateToApiKey = { showApiKeyDialog = true }
                    )
                } else {
                    MainNavigation(
                        onNavigateToSettings = { showSettingsScreen = true }
                    )
                }
            }
        }
    }
}

/**
 * 设置页面 — API Key管理 + 数据管理
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToApiKey: () -> Unit
) {
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Key 管理模块
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("API Key 管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val hasKey = DiModule.hasApiKey()
                    if (hasKey) {
                        val key = DiModule.getApiKey()
                        val maskedKey = if (key.length > 8) "${key.take(4)}...${key.takeLast(4)}" else "****"
                        Text("当前 Key：$maskedKey", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onNavigateToApiKey) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("修改")
                            }
                            OutlinedButton(
                                onClick = {
                                    DiModule.clearApiKey()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("清除")
                            }
                        }
                    } else {
                        Text("未设置自定义 API Key（使用系统默认）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNavigateToApiKey) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("设置 API Key")
                        }
                    }
                }
            }

            // 数据管理模块
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("数据管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导出数据")
                        }
                        OutlinedButton(onClick = { showImportDialog = true }) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导入数据")
                        }
                    }
                    if (importResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(importResult!!, style = MaterialTheme.typography.bodySmall, color = if (importResult!!.startsWith("✅")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    // 导出弹窗
    if (showExportDialog) {
        var exportFormat by remember { mutableStateOf("JSON") }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出数据") },
            text = {
                Column {
                    Text("选择导出格式：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = exportFormat == "JSON", onClick = { exportFormat = "JSON" }, label = { Text("JSON") })
                        FilterChip(selected = exportFormat == "CSV", onClick = { exportFormat = "CSV" }, label = { Text("CSV") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val result = DataExportImportHelper.exportData(context, exportFormat)
                    importResult = result
                    showExportDialog = false
                }) { Text("确认导出") }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("取消") } }
        )
    }

    // 导入弹窗
    if (showImportDialog) {
        var importMode by remember { mutableStateOf("merge") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入数据") },
            text = {
                Column {
                    Text("选择导入方式：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = importMode == "merge", onClick = { importMode = "merge" }, label = { Text("合并数据") })
                        FilterChip(selected = importMode == "replace", onClick = { importMode = "replace" }, label = { Text("覆盖原有数据") })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("将从 Downloads/personaeval_export.json 读取数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val result = DataExportImportHelper.importData(context, importMode)
                    importResult = result
                    showImportDialog = false
                }) { Text("确认导入") }
            },
            dismissButton = { TextButton(onClick = { showImportDialog = false }) { Text("取消") } }
        )
    }
}

/**
 * 数据导出/导入辅助类
 */
object DataExportImportHelper {

    fun exportData(context: android.content.Context, format: String): String {
        return try {
            val db = com.example.personaeval.data.local.database.AppDatabase.getDatabase(context)
            val exportDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "personaeval_export.$format")
            
            kotlinx.coroutines.runBlocking {
                when (format) {
                    "JSON" -> {
                        val classes = db.classDao().getAllClassesList()
                        val students = db.studentDao().getAllStudentsList()
                        val traits = db.studentTraitsDao().getAllTraitsList()
                        val evaluations = db.evaluationDao().getAllEvaluationsList()
                        val templates = db.templateDao().getAllTemplatesList()

                        val json = kotlinx.serialization.json.buildJsonObject {
                            put("classes", kotlinx.serialization.json.JsonArray(classes.map { 
                                kotlinx.serialization.json.buildJsonObject {
                                    put("id", it.id)
                                    put("grade", it.grade)
                                    put("name", it.name)
                                    put("studentCount", it.studentCount ?: 0)
                                }
                            }))
                            put("students", kotlinx.serialization.json.JsonArray(students.map {
                                kotlinx.serialization.json.buildJsonObject {
                                    put("id", it.id)
                                    put("classId", it.classId)
                                    put("name", it.name)
                                    put("traits", it.traits ?: "")
                                    put("personalEval", it.personalEval ?: "")
                                    put("suggestion", it.suggestion ?: "")
                                }
                            }))
                            put("traits", kotlinx.serialization.json.JsonArray(traits.map {
                                kotlinx.serialization.json.buildJsonObject {
                                    put("id", it.id)
                                    put("studentId", it.studentId)
                                    put("trait", it.trait)
                                    put("percentage", it.percentage)
                                }
                            }))
                            put("evaluations", kotlinx.serialization.json.JsonArray(evaluations.map {
                                kotlinx.serialization.json.buildJsonObject {
                                    put("id", it.id)
                                    put("studentId", it.studentId)
                                    put("template", it.template)
                                    put("content", it.content)
                                    put("personalEval", it.personalEval ?: "")
                                    put("suggestion", it.suggestion ?: "")
                                    put("isAdopted", it.isAdopted)
                                }
                            }))
                            put("templates", kotlinx.serialization.json.JsonArray(templates.map {
                                kotlinx.serialization.json.buildJsonObject {
                                    put("id", it.id)
                                    put("name", it.name)
                                    put("personalEvalFormat", it.personalEvalFormat)
                                    put("suggestionFormat", it.suggestionFormat)
                                    put("isDefault", it.isDefault)
                                }
                            }))
                        }
                        exportDir.writeText(json.toString())
                        "✅ 导出成功：${exportDir.absolutePath}"
                    }
                    "CSV" -> {
                        val students = db.studentDao().getAllStudentsList()
                        val csv = StringBuilder("id,classId,name,traits,personalEval,suggestion\n")
                        for (s in students) {
                            csv.append("${s.id},${s.classId},\"${s.name}\",\"${s.traits ?: ""}\",\"${s.personalEval ?: ""}\",\"${s.suggestion ?: ""}\"\n")
                        }
                        exportDir.writeText(csv.toString())
                        "✅ 导出成功：${exportDir.absolutePath}"
                    }
                    else -> "❌ 不支持的格式"
                }
            }
        } catch (e: Exception) {
            "❌ 导出失败：${e.message}"
        }
    }

    fun importData(context: android.content.Context, mode: String): String {
        return try {
            val importFile = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "personaeval_export.JSON")
            if (!importFile.exists()) {
                return "❌ 未找到导入文件：${importFile.absolutePath}"
            }
            val content = importFile.readText()
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val jsonObj = json.parseToJsonElement(content).jsonObject

            val db = com.example.personaeval.data.local.database.AppDatabase.getDatabase(context)

            kotlinx.coroutines.runBlocking {
                if (mode == "replace") {
                    db.templateDao().deleteAll()
                }

                var importCount = 0
                val templatesArray = jsonObj["templates"]?.jsonArray
                if (templatesArray != null) {
                    for (elem in templatesArray) {
                        val obj = elem.jsonObject
                        val name = obj["name"]?.jsonPrimitive?.content ?: continue
                        if (name == "系统默认模板") continue
                        db.templateDao().insert(com.example.personaeval.data.local.entity.TemplateEntity(
                            name = name,
                            personalEvalFormat = obj["personalEvalFormat"]?.jsonPrimitive?.content ?: "",
                            suggestionFormat = obj["suggestionFormat"]?.jsonPrimitive?.content ?: "",
                            isDefault = false,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        ))
                        importCount++
                    }
                }

                "✅ 导入成功：$importCount 条模板数据"
            }
        } catch (e: Exception) {
            "❌ 导入失败：${e.message}"
        }
    }
}

@Composable
private fun ApiKeyDialog(
    hasExistingKey: Boolean,
    currentKey: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = { if (hasExistingKey) onDismiss() },
        title = { Text("设置阿里云 API Key") },
        text = {
            Column {
                Text(
                    "请输入你的阿里云 Dashscope API Key，用于调用千问大模型生成学生评价。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(key) },
                enabled = key.isNotBlank()
            ) { Text("确认") }
        },
        dismissButton = {
            if (hasExistingKey) {
                TextButton(onClick = onDismiss) { Text("跳过") }
            }
        }
    )
}
