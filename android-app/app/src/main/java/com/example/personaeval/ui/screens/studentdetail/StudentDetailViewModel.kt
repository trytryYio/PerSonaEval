package com.example.personaeval.ui.screens.studentdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personaeval.data.local.entity.StudentEntity
import com.example.personaeval.data.local.entity.StudentTraitsEntity
import com.example.personaeval.data.local.entity.EvaluationEntity
import com.example.personaeval.data.repository.StudentRepository
import com.example.personaeval.data.repository.EvaluationRepository
import com.example.personaeval.di.DiModule
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StudentDetailViewModel(private val studentId: Int) : ViewModel() {
    private val studentRepo = DiModule.getStudentRepository()
    private val evalRepo = DiModule.getEvaluationRepository()

    private val _uiState = MutableStateFlow(StudentDetailUiState())
    val uiState: StateFlow<StudentDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            studentRepo.getStudentsByClassId(-1).collect { /* ignore */ }
        }
        // 观察学生信息
        viewModelScope.launch {
            // 直接从 DAO 获取学生信息并刷新
            val student = studentRepo.getStudentById(studentId)
            if (student != null) {
                _uiState.update { it.copy(student = student) }
            }
        }
        viewModelScope.launch {
            studentRepo.getTraitsByStudentId(studentId).collect { traits ->
                _uiState.update { it.copy(traits = traits) }
            }
        }
        viewModelScope.launch {
            // 只订阅已采纳的评价作为历史评价
            evalRepo.getAdoptedEvaluationsByStudentId(studentId).collect { evals ->
                _uiState.update { it.copy(evaluations = evals) }
            }
        }
    }

    fun toggleEditMode() { _uiState.update { it.copy(editMode = !it.editMode) } }

    fun addTrait(name: String, pct: Float) {
        viewModelScope.launch {
            studentRepo.addTrait(studentId, name, pct)
            refreshStudent()
        }
    }

    fun updateTrait(trait: StudentTraitsEntity) {
        viewModelScope.launch {
            studentRepo.updateTrait(trait)
            refreshStudent()
        }
    }

    fun deleteTrait(id: Int) {
        viewModelScope.launch {
            studentRepo.deleteTrait(id)
            refreshStudent()
        }
    }

    fun updatePersonalEval(studentId: Int, personalEval: String?) {
        viewModelScope.launch {
            val student = studentRepo.getStudentById(studentId) ?: return@launch
            studentRepo.updateStudent(student.copy(personalEval = personalEval, updatedAt = System.currentTimeMillis()))
            refreshStudent()
        }
    }

    fun updateSuggestion(studentId: Int, suggestion: String?) {
        viewModelScope.launch {
            val student = studentRepo.getStudentById(studentId) ?: return@launch
            studentRepo.updateStudent(student.copy(suggestion = suggestion, updatedAt = System.currentTimeMillis()))
            refreshStudent()
        }
    }

    private suspend fun refreshStudent() {
        val student = studentRepo.getStudentById(studentId)
        if (student != null) {
            _uiState.update { it.copy(student = student) }
        }
    }
}
