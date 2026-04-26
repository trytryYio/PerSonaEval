package com.example.personaeval.ui.screens.studentlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personaeval.data.local.entity.StudentEntity
import com.example.personaeval.data.local.entity.StudentTraitsEntity
import com.example.personaeval.data.local.entity.EvaluationEntity
import com.example.personaeval.data.local.entity.TemplateEntity
import com.example.personaeval.data.repository.StudentRepository
import com.example.personaeval.data.repository.EvaluationRepository
import com.example.personaeval.data.repository.TemplateRepository
import com.example.personaeval.data.llm.LlmService
import com.example.personaeval.di.DiModule
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StudentListUiState(
    val students: List<StudentEntity> = emptyList(),
    val traitsMap: Map<Int, List<StudentTraitsEntity>> = emptyMap(),
    val evaluationsMap: Map<Int, List<EvaluationEntity>> = emptyMap(),
    val isLoading: Boolean = false,
    val editMode: Boolean = false,
    val lessonContent: String = "",
    val classAtmosphere: String = "",
    val performanceNotes: Map<Int, String> = emptyMap(),
    val templates: List<TemplateEntity> = emptyList(),
    val selectedTemplateId: Int? = null,
    val isTemplateLoading: Boolean = false,
    val error: String? = null
)

class StudentListViewModel(private val classId: Int) : ViewModel() {
    private val studentRepo = DiModule.getStudentRepository()
    private val evalRepo = DiModule.getEvaluationRepository()
    private val templateRepo = DiModule.getTemplateRepository()
    private val llmService = DiModule.getLlmService()

    private val _uiState = MutableStateFlow(StudentListUiState())
    val uiState: StateFlow<StudentListUiState> = _uiState.asStateFlow()

    init {
        // 观察学生列表
        viewModelScope.launch {
            studentRepo.getStudentsByClassId(classId).collect { students ->
                val traitsMap = mutableMapOf<Int, List<StudentTraitsEntity>>()
                for (s in students) {
                    traitsMap[s.id] = studentRepo.getTraitsListByStudentId(s.id)
                }
                _uiState.update { it.copy(students = students, traitsMap = traitsMap) }
            }
        }
        // 观察所有学生的评价
        viewModelScope.launch {
            studentRepo.getStudentsByClassId(classId).collect { students ->
                val evalsMap = mutableMapOf<Int, List<EvaluationEntity>>()
                for (s in students) {
                    evalsMap[s.id] = evalRepo.getEvaluationListByStudentId(s.id)
                }
                _uiState.update { it.copy(evaluationsMap = evalsMap) }
            }
        }
        // 观察模板列表
        viewModelScope.launch {
            templateRepo.getAllTemplates().collect { templates ->
                _uiState.update { it.copy(templates = templates) }
            }
        }
    }

    fun toggleEditMode() { _uiState.update { it.copy(editMode = !it.editMode) } }

    fun updateLessonContent(v: String) { _uiState.update { it.copy(lessonContent = v) } }
    fun updateClassAtmosphere(v: String) { _uiState.update { it.copy(classAtmosphere = v) } }
    fun updatePerformanceNotes(studentId: Int, notes: String) {
        _uiState.update { it.copy(performanceNotes = it.performanceNotes.toMutableMap().apply { put(studentId, notes) }) }
    }

    fun selectTemplate(templateId: Int?) {
        _uiState.update { it.copy(selectedTemplateId = templateId) }
    }

    fun addStudent(name: String) {
        viewModelScope.launch { studentRepo.insertStudent(classId, name) }
    }

    fun updateStudent(student: StudentEntity) {
        viewModelScope.launch { studentRepo.updateStudent(student) }
    }

    fun deleteStudent(id: Int) {
        viewModelScope.launch { studentRepo.deleteStudent(id) }
    }

    // === 性格特点 CRUD（班级页面弹窗使用） ===

    fun addTrait(studentId: Int, name: String, pct: Float) {
        viewModelScope.launch {
            studentRepo.addTrait(studentId, name, pct)
            refreshTraits()
        }
    }

    fun updateTrait(trait: StudentTraitsEntity) {
        viewModelScope.launch {
            studentRepo.updateTrait(trait)
            refreshTraits()
        }
    }

    fun deleteTrait(traitId: Int) {
        viewModelScope.launch {
            studentRepo.deleteTrait(traitId)
            refreshTraits()
        }
    }

    private suspend fun refreshTraits() {
        val students = _uiState.value.students
        val traitsMap = mutableMapOf<Int, List<StudentTraitsEntity>>()
        for (s in students) {
            traitsMap[s.id] = studentRepo.getTraitsListByStudentId(s.id)
        }
        _uiState.update { it.copy(traitsMap = traitsMap) }
    }

    /**
     * 单个学生生成评价
     */
    fun generateSingle(studentId: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.lessonContent.isBlank()) {
                _uiState.update { it.copy(error = "请先输入课程内容") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            try {
                val student = studentRepo.getStudentById(studentId) ?: run {
                    _uiState.update { it.copy(isLoading = false, error = "学生不存在") }
                    return@launch
                }
                val traits = studentRepo.getTraitsListByStudentId(studentId)
                val traitsPct = traits.associate { it.trait to it.percentage }
                val notes = state.performanceNotes[studentId] ?: ""
                // 未提及的学生默认"中规中矩"
                val fullNotes = if (notes.isBlank()) {
                    "中规中矩" + if (state.classAtmosphere.isNotBlank()) "\n课堂整体氛围：${state.classAtmosphere}" else ""
                } else {
                    notes + if (state.classAtmosphere.isNotBlank()) "\n课堂整体氛围：${state.classAtmosphere}" else ""
                }

                // 获取选中的模板
                val selectedTemplate = state.selectedTemplateId?.let { templateRepo.getTemplateById(it) }
                val apiKey = DiModule.getApiKey()
                val results = llmService.generateEvaluations(
                    studentName = student.name,
                    traitsRaw = student.traits ?: "",
                    traitsPct = traitsPct,
                    lessonContent = state.lessonContent,
                    lessonNotes = fullNotes,
                    apiKey = apiKey.ifBlank { null },
                    templateId = selectedTemplate?.id,
                    personalEvalFormat = selectedTemplate?.personalEvalFormat,
                    suggestionFormat = selectedTemplate?.suggestionFormat
                )

                // 保存到数据库（isAdopted 默认 false）
                val evals = results.map { r ->
                    EvaluationEntity(
                        studentId = studentId,
                        lessonContent = state.lessonContent,
                        lessonDate = System.currentTimeMillis(),
                        performanceNotes = notes.ifBlank { "中规中矩" },
                        template = r.template,
                        content = r.content,
                        personalEval = r.personalEval.ifBlank { null },
                        suggestion = r.suggestion.ifBlank { null },
                        templateId = selectedTemplate?.id,
                        createdAt = System.currentTimeMillis(),
                        isAdopted = false
                    )
                }
                evalRepo.insertEvaluations(evals)

                // 更新学生的个人评价和提升建议（取最后一条结果的内容）
                if (results.any { it.personalEval.isNotBlank() || it.suggestion.isNotBlank() }) {
                    val lastResult = results.last()
                    studentRepo.updateStudent(student.copy(
                        personalEval = lastResult.personalEval.ifBlank { student.personalEval },
                        suggestion = lastResult.suggestion.ifBlank { student.suggestion },
                        updatedAt = System.currentTimeMillis()
                    ))
                }

                // 刷新评价数据
                refreshEvaluations()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "生成失败：${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 批量生成全部学生评价
     */
    fun generateAll() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.lessonContent.isBlank()) {
                _uiState.update { it.copy(error = "请先输入课程内容") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 获取选中的模板
                val selectedTemplate = state.selectedTemplateId?.let { templateRepo.getTemplateById(it) }

                for (student in state.students) {
                    try {
                        val traits = state.traitsMap[student.id] ?: emptyList()
                        val traitsPct = traits.associate { it.trait to it.percentage }
                        val notes = state.performanceNotes[student.id] ?: ""
                        val fullNotes = if (notes.isBlank()) {
                            "中规中矩" + if (state.classAtmosphere.isNotBlank()) "\n课堂整体氛围：${state.classAtmosphere}" else ""
                        } else {
                            notes + if (state.classAtmosphere.isNotBlank()) "\n课堂整体氛围：${state.classAtmosphere}" else ""
                        }

                        val apiKey = DiModule.getApiKey()
                        val results = llmService.generateEvaluations(
                            studentName = student.name,
                            traitsRaw = student.traits ?: "",
                            traitsPct = traitsPct,
                            lessonContent = state.lessonContent,
                            lessonNotes = fullNotes,
                            apiKey = apiKey.ifBlank { null },
                            templateId = selectedTemplate?.id,
                            personalEvalFormat = selectedTemplate?.personalEvalFormat,
                            suggestionFormat = selectedTemplate?.suggestionFormat
                        )

                        val evals = results.map { r ->
                            EvaluationEntity(
                                studentId = student.id,
                                lessonContent = state.lessonContent,
                                lessonDate = System.currentTimeMillis(),
                                performanceNotes = notes.ifBlank { "中规中矩" },
                                template = r.template,
                                content = r.content,
                                personalEval = r.personalEval.ifBlank { null },
                                suggestion = r.suggestion.ifBlank { null },
                                templateId = selectedTemplate?.id,
                                createdAt = System.currentTimeMillis(),
                                isAdopted = false
                            )
                        }
                        evalRepo.insertEvaluations(evals)

                        // 更新学生个人评价和建议
                        if (results.any { it.personalEval.isNotBlank() || it.suggestion.isNotBlank() }) {
                            val lastResult = results.last()
                            studentRepo.updateStudent(student.copy(
                                personalEval = lastResult.personalEval.ifBlank { student.personalEval },
                                suggestion = lastResult.suggestion.ifBlank { student.suggestion },
                                updatedAt = System.currentTimeMillis()
                            ))
                        }
                    } catch (e: Exception) {
                        // 单个失败不影响其他学生
                    }
                }
                // 刷新评价数据
                refreshEvaluations()
            } finally {
                _uiState.update { it.copy(isLoading = false) } }
        }
    }

    /**
     * 采纳评价（同一 template 先取消之前的）
     */
    fun adoptEvaluation(evalId: Int, studentId: Int, template: String) {
        viewModelScope.launch {
            evalRepo.adoptEvaluation(evalId, studentId, template)
            refreshEvaluations()
        }
    }

    // === 模板管理 ===

    fun createTemplate(name: String, personalEvalFormat: String, suggestionFormat: String) {
        viewModelScope.launch {
            templateRepo.insertTemplate(TemplateEntity(
                name = name,
                personalEvalFormat = personalEvalFormat,
                suggestionFormat = suggestionFormat,
                isDefault = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    fun updateTemplate(template: TemplateEntity) {
        viewModelScope.launch { templateRepo.updateTemplate(template) }
    }

    fun deleteTemplate(id: Int) {
        viewModelScope.launch { templateRepo.deleteTemplate(id) }
    }

    fun aiGenerateTemplate(description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTemplateLoading = true) }
            try {
                val apiKey = DiModule.getApiKey()
                val result = llmService.generateTemplateByAi(description, apiKey.ifBlank { null })
                if (result.personalEval.isNotBlank() || result.suggestion.isNotBlank()) {
                    templateRepo.insertTemplate(TemplateEntity(
                        name = "AI模板 - ${description.take(10)}",
                        personalEvalFormat = result.personalEval,
                        suggestionFormat = result.suggestion,
                        isDefault = false,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    ))
                } else {
                    _uiState.update { it.copy(error = "AI生成模板失败，请重试") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "AI生成失败：${e.message}") }
            } finally {
                _uiState.update { it.copy(isTemplateLoading = false) }
            }
        }
    }

    private suspend fun refreshEvaluations() {
        val students = _uiState.value.students
        val evalsMap = mutableMapOf<Int, List<EvaluationEntity>>()
        for (s in students) {
            evalsMap[s.id] = evalRepo.getEvaluationListByStudentId(s.id)
        }
        _uiState.update { it.copy(evaluationsMap = evalsMap) }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
