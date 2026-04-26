package com.example.personaeval.data.repository

import com.example.personaeval.data.local.dao.ClassDao
import com.example.personaeval.data.local.entity.ClassEntity
import kotlinx.coroutines.flow.Flow

class ClassRepository(private val classDao: ClassDao) {

    fun getAllClasses(): Flow<List<ClassEntity>> = classDao.getAllClasses()

    suspend fun getClassById(id: Int): ClassEntity? = classDao.getClassById(id)

    suspend fun insertClass(grade: String, name: String, studentCount: Int? = null): Long {
        return classDao.insert(ClassEntity(grade = grade, name = name, studentCount = studentCount, createdAt = System.currentTimeMillis()))
    }

    suspend fun updateClass(cls: ClassEntity) = classDao.update(cls)

    suspend fun deleteClass(id: Int) = classDao.softDelete(id)
}
