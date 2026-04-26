package com.example.personaeval.data.repository

import com.example.personaeval.data.local.dao.EvaluationDao
import com.example.personaeval.data.local.entity.EvaluationEntity
import kotlinx.coroutines.flow.Flow

class EvaluationRepository(
    private val evaluationDao: EvaluationDao
) {

    fun getEvaluationsByStudentId(studentId: Int): Flow<List<EvaluationEntity>> =
        evaluationDao.getEvaluationsByStudentId(studentId)

    /** 获取已采纳的评价 — 用于历史评价展示 */
    fun getAdoptedEvaluationsByStudentId(studentId: Int): Flow<List<EvaluationEntity>> =
        evaluationDao.getAdoptedEvaluationsByStudentId(studentId)

    suspend fun getEvaluationListByStudentId(studentId: Int): List<EvaluationEntity> =
        evaluationDao.getEvaluationListByStudentId(studentId)

    suspend fun insertEvaluations(evaluations: List<EvaluationEntity>) {
        evaluationDao.insertAll(evaluations)
    }

    suspend fun adoptEvaluation(id: Int, studentId: Int, template: String) {
        evaluationDao.unadoptByStudentAndTemplate(studentId, template)
        evaluationDao.adoptById(id)
    }

    suspend fun deleteEvaluation(id: Int) {
        evaluationDao.deleteById(id)
    }
}
