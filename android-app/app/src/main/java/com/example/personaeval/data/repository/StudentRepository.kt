package com.example.personaeval.data.repository

import com.example.personaeval.data.local.dao.StudentDao
import com.example.personaeval.data.local.dao.StudentTraitsDao
import com.example.personaeval.data.local.dao.TempTraitsDao
import com.example.personaeval.data.local.entity.StudentEntity
import com.example.personaeval.data.local.entity.StudentTraitsEntity
import com.example.personaeval.data.local.entity.TempTraitsEntity
import kotlinx.coroutines.flow.Flow

class StudentRepository(
    private val studentDao: StudentDao,
    private val studentTraitsDao: StudentTraitsDao,
    private val tempTraitsDao: TempTraitsDao
) {

    fun getStudentsByClassId(classId: Int): Flow<List<StudentEntity>> =
        studentDao.getStudentsByClassId(classId)

    suspend fun getStudentById(id: Int): StudentEntity? = studentDao.getStudentById(id)

    suspend fun insertStudent(classId: Int, name: String, traits: String? = null): Long {
        val now = System.currentTimeMillis()
        return studentDao.insert(StudentEntity(classId = classId, name = name, traits = traits, createdAt = now, updatedAt = now))
    }

    suspend fun updateStudent(student: StudentEntity) = studentDao.update(student)

    suspend fun deleteStudent(id: Int) = studentDao.softDelete(id)

    // === 性格画像 ===

    fun getTraitsByStudentId(studentId: Int): Flow<List<StudentTraitsEntity>> =
        studentTraitsDao.getTraitsByStudentId(studentId)

    suspend fun getTraitsListByStudentId(studentId: Int): List<StudentTraitsEntity> =
        studentTraitsDao.getTraitsListByStudentId(studentId)

    /** 迭代更新长期性格画像（加权移动平均，alpha=0.7） */
    suspend fun iterateTraits(studentId: Int, tempTraits: Map<String, Float>, alpha: Float = 0.7f) {
        val existing = studentTraitsDao.getTraitsListByStudentId(studentId)
        val oldTraits = existing.associate { it.trait to it.percentage }

        val allKeys = oldTraits.keys + tempTraits.keys
        val newTraits = mutableMapOf<String, Float>()

        for (trait in allKeys) {
            val oldVal = oldTraits[trait] ?: 0f
            val tempVal = tempTraits[trait] ?: 0f
            newTraits[trait] = oldVal * alpha + tempVal * (1 - alpha)
        }

        // 归一化到 100%
        val total = newTraits.values.sum()
        if (total > 0) {
            for (trait in newTraits.keys) {
                newTraits[trait] = (newTraits[trait]!! / total * 100).let { 
                    (it * 10).toInt() / 10f // 保留1位小数
                }
            }
        }

        // 替换数据库
        studentTraitsDao.deleteByStudentId(studentId)
        studentTraitsDao.insertAll(newTraits.map { (trait, pct) ->
            StudentTraitsEntity(studentId = studentId, trait = trait, percentage = pct, updatedAt = System.currentTimeMillis())
        })
    }

    /** 保存当堂临时性格（覆盖旧数据） */
    suspend fun saveTempTraits(studentId: Int, traits: Map<String, Float>) {
        tempTraitsDao.deleteByStudentId(studentId)
        tempTraitsDao.insertAll(traits.map { (trait, pct) ->
            TempTraitsEntity(studentId = studentId, trait = trait, percentage = pct, createdAt = System.currentTimeMillis())
        })
    }

    suspend fun getTempTraitsList(studentId: Int): List<TempTraitsEntity> =
        tempTraitsDao.getTraitsListByStudentId(studentId)

    /** 增删改单个性格特点 */
    suspend fun addTrait(studentId: Int, trait: String, percentage: Float) {
        val traits = mapOf(trait to percentage)
        iterateTraits(studentId, traits)
    }

    suspend fun updateTrait(traitEntity: StudentTraitsEntity) {
        studentTraitsDao.update(traitEntity)
    }

    suspend fun deleteTrait(id: Int) {
        studentTraitsDao.deleteById(id)
    }
}
