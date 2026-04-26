package com.example.personaeval.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "student",
    foreignKeys = [ForeignKey(
        entity = ClassEntity::class,
        parentColumns = ["id"],
        childColumns = ["classId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("classId")]
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val classId: Int,
    val name: String,
    val traits: String? = null,
    val personalEval: String? = null,    // 个人评价（综合）
    val suggestion: String? = null,      // 提升建议（综合）
    val createdAt: Long? = null,
    val updatedAt: Long? = null,         // 最后更新时间
    val isActive: Boolean = true
)
