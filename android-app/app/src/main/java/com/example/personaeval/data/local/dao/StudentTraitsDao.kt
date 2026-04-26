package com.example.personaeval.data.local.dao

import androidx.room.*
import com.example.personaeval.data.local.entity.StudentTraitsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentTraitsDao {
    @Query("SELECT * FROM student_traits WHERE studentId = :studentId")
    fun getTraitsByStudentId(studentId: Int): Flow<List<StudentTraitsEntity>>

    @Query("SELECT * FROM student_traits WHERE studentId = :studentId")
    suspend fun getTraitsListByStudentId(studentId: Int): List<StudentTraitsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(traits: List<StudentTraitsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trait: StudentTraitsEntity): Long

    @Update
    suspend fun update(trait: StudentTraitsEntity)

    @Query("DELETE FROM student_traits WHERE studentId = :studentId")
    suspend fun deleteByStudentId(studentId: Int)

    @Query("DELETE FROM student_traits WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM student_traits")
    suspend fun getAllTraitsList(): List<StudentTraitsEntity>
}
