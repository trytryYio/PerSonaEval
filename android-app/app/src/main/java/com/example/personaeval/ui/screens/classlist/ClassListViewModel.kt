package com.example.personaeval.ui.screens.classlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personaeval.data.local.entity.ClassEntity
import com.example.personaeval.data.repository.ClassRepository
import com.example.personaeval.data.repository.StudentRepository
import com.example.personaeval.data.llm.LlmService
import com.example.personaeval.di.DiModule
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ClassListUiState(
    val classes: List<ClassEntity> = emptyList(),
    val isLoading: Boolean = false,
    val editMode: Boolean = false,
    val error: String? = null,
    val isExtracting: Boolean = false
)

class ClassListViewModel : ViewModel() {
    private val classRepo = DiModule.getClassRepository()
    private val studentRepo = DiModule.getStudentRepository()
    private val llmService = DiModule.getLlmService()

    private val _uiState = MutableStateFlow(ClassListUiState())
    val uiState: StateFlow<ClassListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            classRepo.getAllClasses().collect { classes ->
                _uiState.update { it.copy(classes = classes) }
            }
        }
    }

    fun toggleEditMode() {
        _uiState.update { it.copy(editMode = !it.editMode) }
    }

    fun addClass(grade: String, name: String) {
        viewModelScope.launch {
            classRepo.insertClass(grade, name)
        }
    }

    fun updateClass(cls: ClassEntity) {
        viewModelScope.launch {
            classRepo.updateClass(cls)
        }
    }

    fun deleteClass(id: Int) {
        viewModelScope.launch {
            classRepo.deleteClass(id)
        }
    }

    fun aiExtract(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExtracting = true) }
            try {
                val apiKey = DiModule.getApiKey()
                val result = llmService.extractStudents(text, apiKey.ifBlank { null })

                if (result.students.isEmpty()) {
                    _uiState.update { it.copy(isExtracting = false, error = "未识别到学生信息") }
                    return@launch
                }

                // 创建班级
                val classId = classRepo.insertClass(
                    grade = result.grade.ifBlank { "未知年级" },
                    name = result.className.ifBlank { "未知班级" },
                    studentCount = result.totalCount.ifZero { result.students.size }
                ).toInt()

                // 创建学生 + 性格画像
                for (student in result.students) {
                    val studentId = studentRepo.insertStudent(
                        classId = classId,
                        name = student.name,
                        traits = student.traitsRaw
                    ).toInt()

                    if (student.traitsPct.isNotEmpty()) {
                        studentRepo.saveTempTraits(studentId, student.traitsPct)
                        studentRepo.iterateTraits(studentId, student.traitsPct)
                    }
                }

                _uiState.update { it.copy(isExtracting = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExtracting = false, error = "AI识别失败：${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun Int.ifZero(block: () -> Int): Int = if (this == 0) block() else this
}
