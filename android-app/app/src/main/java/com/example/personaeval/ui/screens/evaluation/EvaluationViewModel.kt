package com.example.personaeval.ui.screens.evaluation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personaeval.data.local.entity.EvaluationEntity
import com.example.personaeval.data.repository.EvaluationRepository
import com.example.personaeval.di.DiModule
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EvaluationUiState(
    val evaluations: List<EvaluationEntity> = emptyList(),
    val isLoading: Boolean = false,
    val editMode: Boolean = false,
    val error: String? = null
)

class EvaluationViewModel(private val studentId: Int) : ViewModel() {
    private val evalRepo = DiModule.getEvaluationRepository()

    private val _uiState = MutableStateFlow(EvaluationUiState())
    val uiState: StateFlow<EvaluationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            evalRepo.getEvaluationsByStudentId(studentId).collect { evals ->
                _uiState.update { it.copy(evaluations = evals) }
            }
        }
    }

    fun toggleEditMode() { _uiState.update { it.copy(editMode = !it.editMode) } }

    fun adoptEvaluation(id: Int, studentId: Int, template: String) {
        viewModelScope.launch { evalRepo.adoptEvaluation(id, studentId, template) }
    }

    fun deleteEvaluation(id: Int) {
        viewModelScope.launch { evalRepo.deleteEvaluation(id) }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
