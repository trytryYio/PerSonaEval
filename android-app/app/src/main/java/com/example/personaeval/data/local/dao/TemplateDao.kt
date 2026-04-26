package com.example.personaeval.data.local.dao

import androidx.room.*
import com.example.personaeval.data.local.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Query("SELECT * FROM template ORDER BY isDefault DESC, createdAt ASC")
    fun getAllTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM template ORDER BY isDefault DESC, createdAt ASC")
    suspend fun getAllTemplatesList(): List<TemplateEntity>

    @Query("SELECT * FROM template WHERE id = :id")
    suspend fun getTemplateById(id: Int): TemplateEntity?

    @Insert
    suspend fun insert(template: TemplateEntity): Long

    @Update
    suspend fun update(template: TemplateEntity)

    @Query("DELETE FROM template WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM template")
    suspend fun deleteAll()
}
