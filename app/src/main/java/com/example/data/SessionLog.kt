package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_logs")
data class SessionLog(
    @PrimaryKey(autoGenerate = true) val logId: Int = 0,
    val caseId: Int,                  // الرقم التعريفي للقضية المرتبطة
    val lastSessionDate: String,      // تاريخ آخر جلسة
    val decision: String,             // قرار المحكمة في الجلسة (المنطوق أو القرار)
    val nextSessionDate: String,      // تاريخ الجلسة القادمة من اليومية
    val logNotes: String,             // الملاحظات المدخلة
    val timestamp: Long = System.currentTimeMillis()
)
