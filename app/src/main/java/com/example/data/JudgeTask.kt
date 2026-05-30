package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "judge_tasks")
data class JudgeTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,                // عنوان المهمة / التنبيه
    val description: String,          // الوصف التفصيلي
    val dueDate: String,              // التاريخ المطلوب (e.g. "YYYY-MM-DD")
    val isCompleted: Boolean = false, // حالة الانتهاء
    val caseId: Int? = null,          // القضية المرتبطة (إن وجدت)
    val createdAt: Long = System.currentTimeMillis()
)
