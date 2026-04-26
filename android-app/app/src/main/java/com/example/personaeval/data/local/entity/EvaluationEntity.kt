package com.example.personaeval.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "evaluation",
    foreignKeys = [ForeignKey(
        entity = StudentEntity::class,
        parentColumns = ["id"],
        childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("studentId")]
)
data class EvaluationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val studentId: Int,
    val lessonContent: String,
    val lessonDate: Long? = null,
    val performanceNotes: String? = null,
    val template: String,  // "80/20", "65/35", "90/10"
    val content: String,
    val personalEval: String? = null,    // 个人评价模块内容
    val suggestion: String? = null,      // 提升建议模块内容
    val templateId: Int? = null,         // 使用的模板ID（null=系统默认）
    val createdAt: Long? = null,
    val isAdopted: Boolean = false
)
