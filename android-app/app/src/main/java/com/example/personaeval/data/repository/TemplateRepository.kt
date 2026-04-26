package com.example.personaeval.data.repository

import com.example.personaeval.data.local.dao.TemplateDao
import com.example.personaeval.data.local.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

class TemplateRepository(
    private val templateDao: TemplateDao
) {

    fun getAllTemplates(): Flow<List<TemplateEntity>> =
        templateDao.getAllTemplates()

    suspend fun getAllTemplatesList(): List<TemplateEntity> =
        templateDao.getAllTemplatesList()

    suspend fun getTemplateById(id: Int): TemplateEntity? =
        templateDao.getTemplateById(id)

    suspend fun insertTemplate(template: TemplateEntity): Long =
        templateDao.insert(template)

    suspend fun updateTemplate(template: TemplateEntity) =
        templateDao.update(template)

    suspend fun deleteTemplate(id: Int) =
        templateDao.deleteById(id)

    suspend fun deleteAllTemplates() =
        templateDao.deleteAll()
}
