package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "court_cases")
data class CourtCase(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val caseNumber: String,          // رقم القضية
    val registrationDay: Int,        // يوم التسجيل
    val registrationMonth: Int,      // شهر التسجيل
    val registrationYear: Int,       // سنة التسجيل
    val caseName: String,            // اسم القضية
    val court: String,               // المحكمة
    val lawyer: String,              // المحامي
    val nextSessionDate: String,     // تاريخ الجلسة القادمة (e.g. "YYYY-MM-DD")
    val notes: String,               // ملاحظات
    val isArchived: Boolean = false, // حالة الأرشفة
    val createdAt: Long = System.currentTimeMillis()
)
