package com.example.personaeval.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "student_traits",
    foreignKeys = [ForeignKey(
        entity = StudentEntity::class,
        parentColumns = ["id"],
        childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("studentId")]
)
data class StudentTraitsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val studentId: Int,
    val trait: String,
    val percentage: Float,
    val updatedAt: Long? = null
)
