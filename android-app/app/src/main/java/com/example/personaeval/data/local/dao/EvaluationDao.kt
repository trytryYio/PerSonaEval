package com.example.personaeval.data.local.dao

import androidx.room.*
import com.example.personaeval.data.local.entity.EvaluationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvaluationDao {
    @Query("SELECT * FROM evaluation WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun getEvaluationsByStudentId(studentId: Int): Flow<List<EvaluationEntity>>

    @Query("SELECT * FROM evaluation WHERE studentId = :studentId ORDER BY createdAt DESC")
    suspend fun getEvaluationListByStudentId(studentId: Int): List<EvaluationEntity>

    /** 只获取已采纳的评价 — 用于学生详情页历史评价 */
    @Query("SELECT * FROM evaluation WHERE studentId = :studentId AND isAdopted = 1 ORDER BY createdAt DESC")
    fun getAdoptedEvaluationsByStudentId(studentId: Int): Flow<List<EvaluationEntity>>

    @Insert
    suspend fun insert(evaluation: EvaluationEntity): Long

    @Insert
    suspend fun insertAll(evaluations: List<EvaluationEntity>)

    @Query("UPDATE evaluation SET isAdopted = 0 WHERE studentId = :studentId AND template = :template")
    suspend fun unadoptByStudentAndTemplate(studentId: Int, template: String)

    @Query("UPDATE evaluation SET isAdopted = 1 WHERE id = :id")
    suspend fun adoptById(id: Int)

    @Query("DELETE FROM evaluation WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM evaluation")
    suspend fun getAllEvaluationsList(): List<EvaluationEntity>
}
