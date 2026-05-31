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
    val status: String = "قيد النظر",    // الحالة (قيد النظر، مؤجلة، شطب، محكومة، إلخ)
    val ruling: String = "",         // منطوق الجلسة (الحكم أو القرار المتخذ)
    val judgeName: String = "",      // اسم القاضي
    val isArchived: Boolean = false, // حالة الأرشفة
    val createdAt: Long = System.currentTimeMillis(),
    
    // New fields
    val caseYear: String = "",       // سنة القضية
    val caseType: String = "",       // نوع القضية (جزائية، مدنية، تجارية، إدارية، أحوال شخصية)
    val caseSubject: String = "",    // موضوع القضية (خيانة أمانة، احتيال، تنفيذ التزام، فسخ للكراهية)
    val disputeParties: String = ""  // أطراف الخصومة (المدعي، المدعى عليه، المدخل، المتدخل)
)
