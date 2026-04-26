package com.example.personaeval.di

import android.content.Context
import android.content.SharedPreferences
import com.example.personaeval.data.local.dao.ClassDao
import com.example.personaeval.data.local.dao.StudentDao
import com.example.personaeval.data.local.dao.StudentTraitsDao
import com.example.personaeval.data.local.dao.TempTraitsDao
import com.example.personaeval.data.local.dao.EvaluationDao
import com.example.personaeval.data.local.dao.TemplateDao
import com.example.personaeval.data.local.database.AppDatabase
import com.example.personaeval.data.repository.ClassRepository
import com.example.personaeval.data.repository.StudentRepository
import com.example.personaeval.data.repository.EvaluationRepository
import com.example.personaeval.data.repository.TemplateRepository
import com.example.personaeval.data.llm.LlmService

object DiModule {

    private var database: AppDatabase? = null
    private var classRepository: ClassRepository? = null
    private var studentRepository: StudentRepository? = null
    private var evaluationRepository: EvaluationRepository? = null
    private var templateRepository: TemplateRepository? = null
    private var llmService: LlmService? = null
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (database == null) {
            database = AppDatabase.getDatabase(context)
        }
        if (prefs == null) {
            prefs = context.getSharedPreferences("persona_eval_prefs", Context.MODE_PRIVATE)
        }
    }

    private fun getDatabase(): AppDatabase {
        return database ?: throw IllegalStateException("Not initialized. Call init() first.")
    }

    // === DAO ===
    fun getClassDao(): ClassDao = getDatabase().classDao()
    fun getStudentDao(): StudentDao = getDatabase().studentDao()
    fun getStudentTraitsDao(): StudentTraitsDao = getDatabase().studentTraitsDao()
    fun getTempTraitsDao(): TempTraitsDao = getDatabase().tempTraitsDao()
    fun getEvaluationDao(): EvaluationDao = getDatabase().evaluationDao()
    fun getTemplateDao(): TemplateDao = getDatabase().templateDao()

    // === Repository ===
    fun getClassRepository(): ClassRepository {
        return classRepository ?: ClassRepository(getClassDao()).also { classRepository = it }
    }

    fun getStudentRepository(): StudentRepository {
        return studentRepository ?: StudentRepository(getStudentDao(), getStudentTraitsDao(), getTempTraitsDao()).also { studentRepository = it }
    }

    fun getEvaluationRepository(): EvaluationRepository {
        return evaluationRepository ?: EvaluationRepository(getEvaluationDao()).also { evaluationRepository = it }
    }

    fun getTemplateRepository(): TemplateRepository {
        return templateRepository ?: TemplateRepository(getTemplateDao()).also { templateRepository = it }
    }

    // === LLM Service ===
    fun getLlmService(): LlmService {
        return llmService ?: LlmService().also { llmService = it }
    }

    // === API Key ===
    fun getApiKey(): String {
        val saved = prefs?.getString("dashscope_api_key", "") ?: ""
        return saved.ifBlank { AppDatabase.DEFAULT_API_KEY }
    }

    fun saveApiKey(key: String) {
        prefs?.edit()?.putString("dashscope_api_key", key)?.apply()
    }

    fun clearApiKey() {
        prefs?.edit()?.remove("dashscope_api_key")?.apply()
    }

    /** 是否已保存过自定义 Key（区别于默认 Key） */
    fun hasApiKey(): Boolean {
        return (prefs?.getString("dashscope_api_key", "") ?: "").isNotBlank()
    }
}
