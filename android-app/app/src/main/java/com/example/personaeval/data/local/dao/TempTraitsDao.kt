package com.example.personaeval.data.local.dao

import androidx.room.*
import com.example.personaeval.data.local.entity.TempTraitsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TempTraitsDao {
    @Query("SELECT * FROM temp_traits WHERE studentId = :studentId")
    fun getTraitsByStudentId(studentId: Int): Flow<List<TempTraitsEntity>>

    @Query("SELECT * FROM temp_traits WHERE studentId = :studentId")
    suspend fun getTraitsListByStudentId(studentId: Int): List<TempTraitsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(traits: List<TempTraitsEntity>)

    @Query("DELETE FROM temp_traits WHERE studentId = :studentId")
    suspend fun deleteByStudentId(studentId: Int)
}
