package com.example.personaeval.data.local.dao

import androidx.room.*
import com.example.personaeval.data.local.entity.ClassEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassDao {
    @Query("SELECT * FROM class WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAllClasses(): Flow<List<ClassEntity>>

    @Query("SELECT * FROM class WHERE id = :id")
    suspend fun getClassById(id: Int): ClassEntity?

    @Insert
    suspend fun insert(cls: ClassEntity): Long

    @Update
    suspend fun update(cls: ClassEntity)

    @Query("UPDATE class SET isActive = 0 WHERE id = :id")
    suspend fun softDelete(id: Int)

    @Query("DELETE FROM class WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM class")
    suspend fun getAllClassesList(): List<ClassEntity>
}
