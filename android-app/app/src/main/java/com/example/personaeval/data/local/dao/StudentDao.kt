package com.example.personaeval.data.local.dao

import androidx.room.*
import com.example.personaeval.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM student WHERE classId = :classId AND isActive = 1 ORDER BY name")
    fun getStudentsByClassId(classId: Int): Flow<List<StudentEntity>>

    @Query("SELECT * FROM student WHERE id = :id")
    suspend fun getStudentById(id: Int): StudentEntity?

    @Insert
    suspend fun insert(student: StudentEntity): Long

    @Update
    suspend fun update(student: StudentEntity)

    @Query("UPDATE student SET isActive = 0 WHERE id = :id")
    suspend fun softDelete(id: Int)

    @Query("DELETE FROM student WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT COUNT(*) FROM student WHERE classId = :classId AND isActive = 1")
    suspend fun countByClassId(classId: Int): Int

    @Query("SELECT * FROM student")
    suspend fun getAllStudentsList(): List<StudentEntity>
}
