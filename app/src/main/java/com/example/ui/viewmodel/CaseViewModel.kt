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
import java.text.SimpleDateFormat
import java.util.*

class CaseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = CaseRepository(database.caseDao(), database.taskDao())

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
        notes: String
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
                notes = notes
            )
            repository.insertCase(courtCase)
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
            tableRows.append("""
                <tr>
                    <td>${index + 1}</td>
                    <td>${item.caseNumber}</td>
                    <td>${item.caseName}</td>
                    <td>${item.court}</td>
                    <td>${item.lawyer.ifBlank { "غير محدد" }}</td>
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
                    <h1>جمهوريـــة مـصـر العربيـــة</h1>
                    <h2>وزارة العــــدل</h2>
                    <h3>أرشيف وسجل القضايا الخاص بالمكتب القضائي</h3>
                    <p>تاريخ استخراج التقرير: $todayStr</p>
                </div>
                
                <table>
                    <thead>
                        <tr>
                            <th style="width: 5%">م</th>
                            <th style="width: 15%">رقم القضية</th>
                            <th style="width: 30%">اسم وموضوع القضية</th>
                            <th style="width: 20%">المحكمة</th>
                            <th style="width: 15%">المحامي</th>
                            <th style="width: 15%">تاريخ الجلسة القادمة</th>
                        </tr>
                    </thead>
                    <tbody>
                        $tableRows
                    </tbody>
                </table>
                
                <div class="footer">
                    * تم تصدير هذا التقرير تلقائياً عبر تطبيق (سجل القاضي الذكي) في $todayStr.
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
