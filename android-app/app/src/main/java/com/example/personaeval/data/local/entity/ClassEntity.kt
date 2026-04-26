package com.example.personaeval.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class")
data class ClassEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val grade: String,
    val name: String,
    val studentCount: Int? = null,
    val createdAt: Long? = null,
    val isActive: Boolean = true,
    val templatePatterns: String? = null
)
