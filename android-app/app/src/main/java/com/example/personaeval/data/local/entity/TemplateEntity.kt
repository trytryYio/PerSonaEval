package com.example.personaeval.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 学习模板表 — 支持自定义评价模板
 * 每个模板包含"个人评价"和"提升建议"两个模块
 */
@Entity(tableName = "template")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,                          // 模板名称
    val personalEvalFormat: String = "",       // "个人评价"模块格式定义
    val suggestionFormat: String = "",         // "提升建议"模块格式定义
    val isDefault: Boolean = false,            // 是否系统默认模板
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)
