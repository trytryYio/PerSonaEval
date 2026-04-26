package com.example.personaeval.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.personaeval.data.local.dao.ClassDao
import com.example.personaeval.data.local.dao.StudentDao
import com.example.personaeval.data.local.dao.StudentTraitsDao
import com.example.personaeval.data.local.dao.TempTraitsDao
import com.example.personaeval.data.local.dao.EvaluationDao
import com.example.personaeval.data.local.dao.TemplateDao
import com.example.personaeval.data.local.entity.ClassEntity
import com.example.personaeval.data.local.entity.StudentEntity
import com.example.personaeval.data.local.entity.StudentTraitsEntity
import com.example.personaeval.data.local.entity.TempTraitsEntity
import com.example.personaeval.data.local.entity.EvaluationEntity
import com.example.personaeval.data.local.entity.TemplateEntity

@Database(
    entities = [
        ClassEntity::class,
        StudentEntity::class,
        StudentTraitsEntity::class,
        TempTraitsEntity::class,
        EvaluationEntity::class,
        TemplateEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classDao(): ClassDao
    abstract fun studentDao(): StudentDao
    abstract fun studentTraitsDao(): StudentTraitsDao
    abstract fun tempTraitsDao(): TempTraitsDao
    abstract fun evaluationDao(): EvaluationDao
    abstract fun templateDao(): TemplateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 默认阿里云 Dashscope API Key */
        const val DEFAULT_API_KEY = "sk-43434062596d44979a152c8e763d6e20"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "persona_eval_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            seedTestData(db)
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // 如果 class 表为空则填充测试数据
                            val cursor = db.query("SELECT COUNT(*) FROM class")
                            cursor.moveToFirst()
                            val count = cursor.getInt(0)
                            cursor.close()
                            if (count == 0) {
                                seedTestData(db)
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * 硬编码测试数据：班级"测试"+ 6 个学生 + 性格画像
         * 列名必须与 Entity 定义完全一致！
         */
        private fun seedTestData(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()

            // 班级 — ClassEntity 列: id, grade, name, studentCount, createdAt, isActive, templatePatterns
            db.execSQL("INSERT INTO class (id, grade, name, studentCount, createdAt, isActive, templatePatterns) VALUES (1, '高二', '3班', 6, $now, 1, '80/20,65/35,90/10')")

            // 6 个学生 — StudentEntity 列: id, classId, name, traits, personalEval, suggestion, createdAt, updatedAt, isActive
            db.execSQL("INSERT INTO student (id, classId, name, traits, personalEval, suggestion, createdAt, updatedAt, isActive) VALUES (1, 1, '张明轩', '性格外向活泼，课堂上积极发言但偶尔走神，喜欢和同学讨论问题，思维敏捷但不够细致，责任心较强', NULL, NULL, $now, $now, 1)")
            db.execSQL("INSERT INTO student (id, classId, name, traits, personalEval, suggestion, createdAt, updatedAt, isActive) VALUES (2, 1, '李思雨', '安静内敛，做事认真细致，成绩稳定，不太主动发言但笔记做得很全面，有点完美主义倾向', NULL, NULL, $now, $now, 1)")
            db.execSQL("INSERT INTO student (id, classId, name, traits, personalEval, suggestion, createdAt, updatedAt, isActive) VALUES (3, 1, '王浩然', '幽默风趣，善于活跃气氛，数学能力强但语文偏弱，注意力容易分散，需要老师经常提醒', NULL, NULL, $now, $now, 1)")
            db.execSQL("INSERT INTO student (id, classId, name, traits, personalEval, suggestion, createdAt, updatedAt, isActive) VALUES (4, 1, '陈晓琳', '温和友善，乐于助人，课堂上认真听讲但创新思维不足，执行力强，适合做班干部', NULL, NULL, $now, $now, 1)")
            db.execSQL("INSERT INTO student (id, classId, name, traits, personalEval, suggestion, createdAt, updatedAt, isActive) VALUES (5, 1, '刘子豪', '性格急躁冲动，体育特长生，精力旺盛，课堂纪律需要加强引导，但对感兴趣的话题参与度很高', NULL, NULL, $now, $now, 1)")
            db.execSQL("INSERT INTO student (id, classId, name, traits, personalEval, suggestion, createdAt, updatedAt, isActive) VALUES (6, 1, '赵雨萱', '独立自主，阅读量大，表达能力强，偶尔显得有些固执己见，逻辑思维突出', NULL, NULL, $now, $now, 1)")

            // 性格画像 — StudentTraitsEntity 列: id, studentId, trait, percentage, updatedAt
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (1, 1, '外向活跃', 30, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (2, 1, '积极发言', 25, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (3, 1, '走神', 10, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (4, 1, '思维敏捷', 20, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (5, 1, '责任心强', 15, $now)")

            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (6, 2, '安静内敛', 30, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (7, 2, '认真细致', 25, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (8, 2, '完美主义', 15, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (9, 2, '缺乏主动性', 15, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (10, 2, '笔记全面', 15, $now)")

            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (11, 3, '幽默风趣', 25, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (12, 3, '活跃气氛', 25, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (13, 3, '注意力分散', 20, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (14, 3, '数学能力强', 20, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (15, 3, '语文偏弱', 10, $now)")

            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (16, 4, '温和友善', 30, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (17, 4, '认真听讲', 25, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (18, 4, '执行力强', 20, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (19, 4, '乐于助人', 15, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (20, 4, '创新不足', 10, $now)")

            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (21, 5, '急躁冲动', 20, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (22, 5, '精力旺盛', 30, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (23, 5, '体育特长', 20, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (24, 5, '纪律需加强', 15, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (25, 5, '兴趣驱动', 15, $now)")

            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (26, 6, '独立自主', 25, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (27, 6, '逻辑思维突出', 25, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (28, 6, '阅读量大', 20, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (29, 6, '固执己见', 15, $now)")
            db.execSQL("INSERT INTO student_traits (id, studentId, trait, percentage, updatedAt) VALUES (30, 6, '表达能力强', 15, $now)")

            // 默认学习模板 — TemplateEntity 列: id, name, personalEvalFormat, suggestionFormat, isDefault, createdAt, updatedAt
            db.execSQL("INSERT INTO template (id, name, personalEvalFormat, suggestionFormat, isDefault, createdAt, updatedAt) VALUES (1, '系统默认模板', '按这个格式写：\n\n{学生名}家长您好，这是{学生名}近期的课程反馈：\n\n课程主题：{课程内容}\n\n课堂表现：（写一段话）', '课后建议：（写 1-2 条具体建议）\n\n重要写作要求（必须遵守）：\n1. 用平常聊天的语气，像发微信一样自然\n2. 内容要具体，提到学生实际表现\n3. 总字数 100-200 字\n4. 负面特点要委婉表达\n5. emoji 最多用 1-2 个，放在句末', 1, $now, $now)")
        }
    }
}
