package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class CaseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = CaseRepository(database.caseDao(), database.taskDao(), database.sessionLogDao())

    // UI state states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Active, 1: Archived, 2: Alerts, 3: Tasks
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _selectedCase = MutableStateFlow<CourtCase?>(null)
    val selectedCase: StateFlow<CourtCase?> = _selectedCase.asStateFlow()

    // Lists from DB
    val activeCases: StateFlow<List<CourtCase>> = repository.activeCases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedCases: StateFlow<List<CourtCase>> = repository.archivedCases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<JudgeTask>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessionLogs: StateFlow<List<SessionLog>> = repository.allSessionLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live filtered search cases
    val searchResults: StateFlow<List<CourtCase>> = _searchQuery
        .debounce(100)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allCases
            } else {
                repository.searchCases(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Computed: Alerts for upcoming sessions (sessions details within next 5 days or past 2 days, or today)
    val sessionAlerts: StateFlow<List<CourtCase>> = repository.activeCases
        .map { list ->
            list.filter { isSessionApproaching(it.nextSessionDate) }
                .sortedBy { it.nextSessionDate }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun selectCase(courtCase: CourtCase?) {
        _selectedCase.value = courtCase
    }

    // Database Actions - Cases
    fun addCase(
        caseNumber: String,
        day: Int,
        month: Int,
        year: Int,
        caseName: String,
        court: String,
        lawyer: String,
        nextSessionDate: String,
        notes: String,
        status: String = "قيد النظر",
        ruling: String = "",
        judgeName: String = "",
        caseYear: String = "",
        caseType: String = "",
        caseSubject: String = "",
        disputeParties: String = ""
    ) {
        viewModelScope.launch {
            val courtCase = CourtCase(
                caseNumber = caseNumber,
                registrationDay = day,
                registrationMonth = month,
                registrationYear = year,
                caseName = caseName,
                court = court,
                lawyer = lawyer,
                nextSessionDate = nextSessionDate,
                notes = notes,
                status = status,
                ruling = ruling,
                judgeName = judgeName,
                caseYear = caseYear,
                caseType = caseType,
                caseSubject = caseSubject,
                disputeParties = disputeParties
            )
            repository.insertCase(courtCase)
        }
    }

    // Database Actions - Daily Journal Session Logs
    fun addSessionLog(
        caseId: Int,
        lastSessionDate: String,
        decision: String,
        nextSessionDate: String,
        logNotes: String
    ) {
        viewModelScope.launch {
            val log = SessionLog(
                caseId = caseId,
                lastSessionDate = lastSessionDate,
                decision = decision,
                nextSessionDate = nextSessionDate,
                logNotes = logNotes
            )
            repository.insertSessionLog(log)

            // Dynamic Sync: Update the court case attributes reactively when a new daily log is added
            val courtCase = repository.getCaseById(caseId)
            if (courtCase != null) {
                val updatedCase = courtCase.copy(
                    nextSessionDate = nextSessionDate,
                    ruling = decision,
                    notes = if (logNotes.isNotBlank()) logNotes else courtCase.notes
                )
                repository.updateCase(updatedCase)
                
                // If the updated case is currently loaded in details view, refresh it
                if (_selectedCase.value?.id == caseId) {
                    _selectedCase.value = updatedCase
                }
            }
        }
    }

    fun getSessionLogsForCase(caseId: Int): Flow<List<SessionLog>> {
        return repository.getSessionLogsForCase(caseId)
    }

    fun deleteSessionLog(log: SessionLog) {
        viewModelScope.launch {
            repository.deleteSessionLog(log)
        }
    }

    fun updateCase(courtCase: CourtCase) {
        viewModelScope.launch {
            repository.updateCase(courtCase)
            // If selected, update the detail view
            if (_selectedCase.value?.id == courtCase.id) {
                _selectedCase.value = courtCase
            }
        }
    }

    fun toggleArchiveCase(courtCase: CourtCase) {
        viewModelScope.launch {
            repository.updateCase(courtCase.copy(isArchived = !courtCase.isArchived))
            if (_selectedCase.value?.id == courtCase.id) {
                _selectedCase.value = courtCase.copy(isArchived = !courtCase.isArchived)
            }
        }
    }

    fun deleteCase(courtCase: CourtCase) {
        viewModelScope.launch {
            repository.deleteCase(courtCase)
            if (_selectedCase.value?.id == courtCase.id) {
                _selectedCase.value = null
            }
        }
    }

    // Database Actions - Tasks
    fun addTask(title: String, description: String, dueDate: String, caseId: Int? = null) {
        viewModelScope.launch {
            val task = JudgeTask(
                title = title,
                description = description,
                dueDate = dueDate,
                caseId = caseId
            )
            repository.insertTask(task)
        }
    }

    fun toggleTaskCompletion(task: JudgeTask) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: JudgeTask) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // Helper: Calculate days between today and next session
    private fun isSessionApproaching(dateStr: String): Boolean {
        if (dateStr.isBlank()) return false
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return try {
            val sessionDate = format.parse(dateStr) ?: return false
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val diffInMillis = sessionDate.time - today.time
            val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)

            // Alert if session is today or in the next 5 days
            diffInDays in 0..5
        } catch (e: Exception) {
            false
        }
    }

    // Native Printing of Case Record (HTML Report)
    fun printCasesReport(context: Context, cases: List<CourtCase>) {
        if (cases.isEmpty()) {
            Toast.makeText(context, "لا توجد قضايا لطباعتها في التقرير", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager == null) {
                    Toast.makeText(context, "الطباعة غير مدعومة على هذا الجهاز", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val jobName = "تقرير سجل القضايا - القاضي " + SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
                val htmlContent = generatePrintableHtml(cases)

                // Build modern print job
                val printAdapter = WebView(context).apply {
                    loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                }.createPrintDocumentAdapter(jobName)

                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ أثناء الطباعة: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // HTML generator for reports
    private fun generatePrintableHtml(cases: List<CourtCase>): String {
        val todayStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
        val tableRows = StringBuilder()

        for ((index, item) in cases.withIndex()) {
            val lawyerAndJudge = buildString {
                append(item.lawyer.ifBlank { "غير محدد" })
                if (item.judgeName.isNotBlank()) {
                    append(" / ج: ")
                    append(item.judgeName)
                }
            }
            tableRows.append("""
                <tr>
                    <td>${index + 1}</td>
                    <td>${item.caseNumber}</td>
                    <td>${item.caseName}</td>
                    <td>${item.court}</td>
                    <td>$lawyerAndJudge</td>
                    <td>${item.status}</td>
                    <td>${item.ruling.ifBlank { "لم يصدر" }}</td>
                    <td dir="ltr">${item.nextSessionDate}</td>
                </tr>
            """.trimIndent())
        }

        return """
            <!DOCTYPE html>
            <html dir="rtl" lang="ar">
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Cairo', 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        color: #1a2c26;
                        margin: 20px;
                        background-color: #fff;
                    }
                    .header {
                        text-align: center;
                        border-bottom: 2px double #1E4D3E;
                        padding-bottom: 15px;
                        margin-bottom: 30px;
                    }
                    .header h1 {
                        font-size: 24px;
                        color: #1E4D3E;
                        margin: 0 0 10px 0;
                    }
                    .header p {
                        font-size: 14px;
                        color: #555;
                        margin: 0;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 20px;
                    }
                    th, td {
                        border: 1px solid #1E4D3E;
                        padding: 10px;
                        text-align: right;
                        font-size: 12px;
                    }
                    th {
                        background-color: #1E4D3E;
                        color: #ffffff;
                    }
                    tr:nth-child(even) th {
                        background-color: #1a2c26;
                    }
                    tr:nth-child(even) {
                        background-color: #f9faf9;
                    }
                    .footer {
                        margin-top: 50px;
                        text-align: left;
                        font-size: 11px;
                        color: #777;
                        border-top: 1px solid #ccc;
                        padding-top: 10px;
                    }
                </style>
                <title>سجل القضايا</title>
            </head>
            <body>
                <div class="header">
                    <h1>الجمهوريـــة اليمنيـــة</h1>
                    <h2>مكـتـب المحـامـي عبـداللطيـف السيـقـل</h2>
                    <h3>أرشيف وسجل القضايا الخاص بالمكتب القضائي</h3>
                    <p>تاريخ استخراج التقرير: $todayStr</p>
                </div>
                
                <table>
                    <thead>
                        <tr>
                            <th style="width: 5%">م</th>
                            <th style="width: 15%">رقم القضية</th>
                            <th style="width: 20%">اسم وموضوع القضية</th>
                            <th style="width: 15%">المحكمة</th>
                            <th style="width: 15%">المحامي / القاضي</th>
                            <th style="width: 10%">الحالة</th>
                            <th style="width: 10%">منطوق الجلسة</th>
                            <th style="width: 10%">تاريخ الجلسة</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${tableRows.toString()}
                    </tbody>
                </table>
                
                <div class="footer">
                    * تم تصدير هذا التقرير تلقائياً عبر تطبيق (سجل القاضي الذكي) في $todayStr.
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    // JSON Backup & Restore Helpers
    fun exportBackup(): String {
        val casesList = activeCases.value + archivedCases.value
        val tasksList = allTasks.value
        
        val casesJson = casesList.joinToString(",") { case ->
            """
            {
              "caseNumber": "${escapeJson(case.caseNumber)}",
              "registrationDay": ${case.registrationDay},
              "registrationMonth": ${case.registrationMonth},
              "registrationYear": ${case.registrationYear},
              "caseName": "${escapeJson(case.caseName)}",
              "court": "${escapeJson(case.court)}",
              "lawyer": "${escapeJson(case.lawyer)}",
              "nextSessionDate": "${escapeJson(case.nextSessionDate)}",
              "notes": "${escapeJson(case.notes)}",
              "status": "${escapeJson(case.status)}",
              "ruling": "${escapeJson(case.ruling)}",
              "judgeName": "${escapeJson(case.judgeName)}",
              "isArchived": ${case.isArchived},
              "createdAt": ${case.createdAt},
              "caseYear": "${escapeJson(case.caseYear)}",
              "caseType": "${escapeJson(case.caseType)}",
              "caseSubject": "${escapeJson(case.caseSubject)}",
              "disputeParties": "${escapeJson(case.disputeParties)}"
            }
            """.trimIndent()
        }
        
        val tasksJson = tasksList.joinToString(",") { task ->
            """
            {
              "title": "${escapeJson(task.title)}",
              "description": "${escapeJson(task.description)}",
              "dueDate": "${escapeJson(task.dueDate)}",
              "isCompleted": ${task.isCompleted},
              "createdAt": ${task.createdAt}
            }
            """.trimIndent()
        }
        
        return """{"cases":[$casesJson],"tasks":[$tasksJson]}"""
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\r", "")
                  .replace("\n", "\\n")
                  .replace("\t", "\\t")
    }

    fun restoreBackup(context: Context, jsonString: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val root = org.json.JSONObject(jsonString)
                val casesArray = root.optJSONArray("cases")
                val tasksArray = root.optJSONArray("tasks")
                
                var restoredCasesCount = 0
                var restoredTasksCount = 0
                
                if (casesArray != null) {
                    for (i in 0 until casesArray.length()) {
                        val jobj = casesArray.getJSONObject(i)
                        val courtCase = CourtCase(
                            caseNumber = jobj.optString("caseNumber", ""),
                            registrationDay = jobj.optInt("registrationDay", 1),
                            registrationMonth = jobj.optInt("registrationMonth", 1),
                            registrationYear = jobj.optInt("registrationYear", 2026),
                            caseName = jobj.optString("caseName", ""),
                            court = jobj.optString("court", ""),
                            lawyer = jobj.optString("lawyer", ""),
                            nextSessionDate = jobj.optString("nextSessionDate", ""),
                            notes = jobj.optString("notes", ""),
                            status = jobj.optString("status", "قيد النظر"),
                            ruling = jobj.optString("ruling", ""),
                            judgeName = jobj.optString("judgeName", ""),
                            isArchived = jobj.optBoolean("isArchived", false),
                            createdAt = jobj.optLong("createdAt", System.currentTimeMillis()),
                            caseYear = jobj.optString("caseYear", ""),
                            caseType = jobj.optString("caseType", ""),
                            caseSubject = jobj.optString("caseSubject", ""),
                            disputeParties = jobj.optString("disputeParties", "")
                        )
                        repository.insertCase(courtCase)
                        restoredCasesCount++
                    }
                }
                
                if (tasksArray != null) {
                    for (i in 0 until tasksArray.length()) {
                        val jobj = tasksArray.getJSONObject(i)
                        val task = JudgeTask(
                            title = jobj.optString("title", ""),
                            description = jobj.optString("description", ""),
                            dueDate = jobj.optString("dueDate", ""),
                            isCompleted = jobj.optBoolean("isCompleted", false),
                            createdAt = jobj.optLong("createdAt", System.currentTimeMillis())
                        )
                        repository.insertTask(task)
                        restoredTasksCount++
                    }
                }
                
                Toast.makeText(context, "تمت استعادة $restoredCasesCount قضية و $restoredTasksCount تذكير عاجل بنجاح!", Toast.LENGTH_LONG).show()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "تنسيق غير عاجل أو تالف")
            }
        }
    }

    fun printSessionLogsReport(context: Context, courtCase: CourtCase, logs: List<SessionLog>) {
        viewModelScope.launch {
            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager == null) {
                    Toast.makeText(context, "الطباعة غير مدعومة على هذا الجهاز", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val jobName = "سجل جلسات القضية رقم " + courtCase.caseNumber
                val htmlContent = generateSessionLogHtml(courtCase, logs)

                val printAdapter = WebView(context).apply {
                    loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                }.createPrintDocumentAdapter(jobName)

                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ أثناء الطباعة: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun generateSessionLogHtml(courtCase: CourtCase, logs: List<SessionLog>): String {
        val todayStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
        val logRows = StringBuilder()

        if (logs.isEmpty()) {
            logRows.append("""
                <tr>
                    <td colspan="5" style="text-align: center; color: #777; padding: 20px;">لا توجد جلسات مسجلة في سجل هذه القضية بعد.</td>
                </tr>
            """.trimIndent())
        } else {
            for ((index, item) in logs.withIndex()) {
                logRows.append("""
                    <tr>
                        <td>${index + 1}</td>
                        <td dir="ltr" style="text-align: center;">${item.lastSessionDate}</td>
                        <td>${item.decision}</td>
                        <td dir="ltr" style="text-align: center;">${item.nextSessionDate}</td>
                        <td>${item.logNotes.ifBlank { "بلا ملاحظات" }}</td>
                    </tr>
                """.trimIndent())
            }
        }

        return """
            <!DOCTYPE html>
            <html dir="rtl" lang="ar">
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Cairo', 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        color: #1a2c26;
                        margin: 20px;
                        background-color: #fff;
                    }
                    .header {
                        text-align: center;
                        border-bottom: 2px double #1E4D3E;
                        padding-bottom: 15px;
                        margin-bottom: 25px;
                    }
                    .header h1 {
                        font-size: 24px;
                        color: #1E4D3E;
                        margin: 0 0 5px 0;
                    }
                    .header h2 {
                        font-size: 18px;
                        color: #a48130;
                        margin: 0 0 10px 0;
                    }
                    .header h3 {
                        font-size: 14px;
                        color: #555;
                        margin: 0 0 5px 0;
                    }
                    .case-info {
                        background-color: #f4f6f4;
                        border: 1px solid #1E4D3E;
                        border-radius: 8px;
                        padding: 15px;
                        margin-bottom: 25px;
                    }
                    .case-info h4 {
                        margin: 0 0 10px 0;
                        color: #1E4D3E;
                        font-size: 16px;
                        border-bottom: 1px solid #ddd;
                        padding-bottom: 5px;
                    }
                    .info-grid {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 10px;
                        font-size: 13px;
                    }
                    .info-item {
                        margin-bottom: 5px;
                    }
                    .info-label {
                        font-weight: bold;
                        color: #1E4D3E;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 20px;
                    }
                    th, td {
                        border: 1px solid #1E4D3E;
                        padding: 10px;
                        text-align: right;
                        font-size: 12px;
                    }
                    th {
                        background-color: #1E4D3E;
                        color: #ffffff;
                    }
                    tr:nth-child(even) {
                        background-color: #f9faf9;
                    }
                    .footer {
                        margin-top: 50px;
                        text-align: left;
                        font-size: 11px;
                        color: #777;
                        border-top: 1px solid #ccc;
                        padding-top: 10px;
                    }
                    .signature-box {
                        margin-top: 40px;
                        display: flex;
                        justify-content: space-between;
                        font-size: 13px;
                        font-weight: bold;
                    }
                </style>
                <title>سجل جلسات القضية</title>
            </head>
            <body>
                <div class="header">
                    <h1>الجمهوريـــة اليمنيـــة</h1>
                    <h2>مكـتـب المحـامـي عبـداللطيـف السيـقـل</h2>
                    <h3>اليومية القضائية وسجل متابعة الجلسات التفصيلي</h3>
                    <p>تاريخ وتوقيت الطباعة: $todayStr</p>
                </div>

                <div class="case-info">
                    <h4>تفاصيل ملف القضية الأساسي</h4>
                    <div class="info-grid">
                        <div class="info-item"><span class="info-label">رقم القضية:</span> ${courtCase.caseNumber}</div>
                        <div class="info-item"><span class="info-label">سنة القضية:</span> ${courtCase.caseYear.ifBlank { "غير مسجل" }}</div>
                        <div class="info-item"><span class="info-label">نوع القضية:</span> ${courtCase.caseType.ifBlank { "غير مسجل" }}</div>
                        <div class="info-item"><span class="info-label">موضوع القضية:</span> ${courtCase.caseSubject.ifBlank { "غير مسجل" }}</div>
                        <div class="info-item"><span class="info-label">أطراف الخصومة:</span> ${courtCase.disputeParties.ifBlank { courtCase.caseName }}</div>
                        <div class="info-item"><span class="info-label">المحكمة المختصة:</span> ${courtCase.court}</div>
                        <div class="info-item"><span class="info-label">رئيس الهيئة / القاضي:</span> ${courtCase.judgeName.ifBlank { "غير مسجل" }}</div>
                        <div class="info-item"><span class="info-label">المحامي الوكيل:</span> ${courtCase.lawyer.ifBlank { "غير مسجل" }}</div>
                    </div>
                </div>

                <h3 style="color: #1E4D3E; border-bottom: 2px solid #1E4D3E; padding-bottom: 5px; margin-bottom: 15px;">جدول تسلسل سير الجلسات والقرارات اليومية</h3>
                <table>
                    <thead>
                        <tr>
                            <th style="width: 5%">م</th>
                            <th style="width: 15%">تاريخ الجلسة</th>
                            <th style="width: 35%">قرار ومنطوق الجلسة</th>
                            <th style="width: 15%">تاريخ الجلسة القادمة</th>
                            <th style="width: 30%">مذكرات وملاحظات</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${logRows.toString()}
                    </tbody>
                </table>
                
                <div class="signature-box">
                    <div>توقيع وتعميد المحامي الوكيل: ........................</div>
                    <div>ختم وتصديق مكتب المحاماة الرسمي</div>
                </div>

                <div class="footer">
                    * تم تصدير واستخراج هذا التقرير تلقائياً عبر تطبيق (سجل القاضي الذكي) - تطوير سامي القادري في $todayStr.
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
