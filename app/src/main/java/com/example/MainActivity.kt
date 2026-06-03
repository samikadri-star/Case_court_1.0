package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CourtCase
import com.example.data.JudgeTask
import com.example.ui.theme.*
import com.example.ui.viewmodel.CaseViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: CaseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Force Layout Direction to Right-to-Left (RTL) for perfect Arabic alignment
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        JudgeAppMainScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JudgeAppMainScreen(
    viewModel: CaseViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeCases by viewModel.activeCases.collectAsStateWithLifecycle()
    val archivedCases by viewModel.archivedCases.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val sessionAlerts by viewModel.sessionAlerts.collectAsStateWithLifecycle()
    
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedCase by viewModel.selectedCase.collectAsStateWithLifecycle()

    var showAddCaseDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showEditCaseDialog by remember { mutableStateOf<CourtCase?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    // SAF Document Launchers for Local File Backup & Restore
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val backupStr = viewModel.exportBackup()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(backupStr.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "تم تصدير وحفظ ملف النسخة الاحتياطية بنجاح في جهازك!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "فشل حفظ الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    viewModel.restoreBackup(context, jsonString, onSuccess = {
                        Toast.makeText(context, "تمت استعادة السجل القضائي بالكامل من الملف المختار بنجاح!", Toast.LENGTH_LONG).show()
                    }, onError = { err ->
                        Toast.makeText(context, "خطأ في بنية الملف وتصنيفه: $err", Toast.LENGTH_LONG).show()
                    })
                }
            } catch (e: Exception) {
                Toast.makeText(context, "فشل قراءة الملف المختار للجلسات: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // Quick filters state
    var selectedCourtFilter by remember { mutableStateOf<String?>(null) }
    var selectedYearFilter by remember { mutableStateOf<Int?>(null) }

    // Aggregate unique courts and years for quick filter chips
    val allCourts = remember(activeCases, archivedCases) {
        (activeCases + archivedCases).map { it.court }.filter { it.isNotBlank() }.distinct()
    }
    val allYears = remember(activeCases, archivedCases) {
        (activeCases + archivedCases).map { it.registrationYear }.distinct().sortedDescending()
    }

    // Apply quick filters onto list
    val currentCasesList = remember(selectedTab, activeCases, archivedCases, searchResults, searchQuery, selectedCourtFilter, selectedYearFilter) {
        val baseList = when {
            searchQuery.isNotBlank() -> searchResults
            selectedTab == 0 -> activeCases
            selectedTab == 1 -> archivedCases
            else -> activeCases
        }
        
        baseList.filter { case ->
            val matchesCourt = selectedCourtFilter == null || case.court == selectedCourtFilter
            val matchesYear = selectedYearFilter == null || case.registrationYear == selectedYearFilter
            matchesCourt && matchesYear
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
        ) {
            
            // Royal Court Header Logo & Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // Fine gold bottom accent underline
                        drawLine(
                            color = CourtGold,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 2f
                        )
                    }
                    .background(CourtGreen)
                    .padding(vertical = 16.dp, horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Justice Pillars / Scales Custom Vector Composed dynamically
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CourtGold.copy(alpha = 0.2f))
                        .border(1.dp, CourtGold, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = "وقار القضاء",
                        tint = CourtGold,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "السجل القضائي الذكي",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "منظومة إدارة وأرشفة القضايا ومتابعة الجلسات المكتوبية",
                        fontSize = 11.sp,
                        color = CourtGold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // Royal Action Buttons: Backup, Restore, and Print
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Backup Action Button (Saves to a local file in phone storage)
                    IconButton(
                        onClick = {
                            try {
                                createBackupLauncher.launch("sajjal_al_qada_backup.json")
                            } catch (e: Exception) {
                                Toast.makeText(context, "فشل حفظ الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CourtGold.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "تصدير نسخة احتياطية لملف",
                            tint = CourtGold
                        )
                    }

                    // Restore Action Button
                    IconButton(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CourtGold.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "استعادة نسخة احتياطية",
                            tint = CourtGold
                        )
                    }

                    // Global Print Button
                    if (selectedTab in 0..1) {
                        IconButton(
                            onClick = {
                                viewModel.printCasesReport(context, currentCasesList)
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CourtGold.copy(alpha = 0.15f))
                                .testTag("print_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "طباعة السجل",
                                tint = CourtGold
                            )
                        }
                    }
                }
            }

            // Segmented Top Navigation Tabs with badges for Alerts & Tasks
            CustomJudgeTabs(
                selectedTab = selectedTab,
                onTabSelected = { viewModel.setSelectedTab(it) },
                alertsCount = sessionAlerts.size,
                pendingTasksCount = allTasks.count { !it.isCompleted }
            )

            // Search Bar & Advanced Settings (Shown on main views)
            if (selectedTab in 0..1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("بحث متوافق برقم القضية، الاسم، المحكمة أو المحامي...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CourtGreen) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CourtGold,
                            unfocusedBorderColor = CourtGold.copy(alpha = 0.3f),
                            focusedContainerColor = DarkEmerald,
                            unfocusedContainerColor = SoftGray.copy(alpha = 0.5f),
                            focusedTextColor = WarmWhite,
                            unfocusedTextColor = WarmWhite.copy(alpha = 0.8f)
                        ),
                        singleLine = true
                    )

                    // Advanced Quick Filters Row (Courts / Years)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "تصفية",
                                tint = AntiqueGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        // Clear filter pill
                        if (selectedCourtFilter != null || selectedYearFilter != null) {
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        selectedCourtFilter = null
                                        selectedYearFilter = null
                                    },
                                    label = { Text("عرض الكل", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        labelColor = CrimsonAlert
                                    )
                                )
                            }
                        }

                        // Courts filters
                        items(allCourts) { court ->
                            val isSelected = selectedCourtFilter == court
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCourtFilter = if (isSelected) null else court },
                                label = { Text(court, fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CourtGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        // Years filters
                        items(allYears) { year ->
                            val isSelected = selectedYearFilter == year
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedYearFilter = if (isSelected) null else year },
                                label = { Text(year.toString(), fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CourtGold,
                                    selectedLabelColor = DeepCharcoal
                                )
                            )
                        }
                    }
                }
            }

            // Core Layout Panel Swappable content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> ActiveCasesTabContent(
                        cases = currentCasesList,
                        onCaseSelected = { viewModel.selectCase(it) },
                        onAddCaseClick = { showAddCaseDialog = true }
                    )
                    1 -> ArchivedCasesTabContent(
                        cases = currentCasesList,
                        onCaseSelected = { viewModel.selectCase(it) }
                    )
                    2 -> SessionAlertsTabContent(
                        alerts = sessionAlerts,
                        onCaseSelected = { viewModel.selectCase(it) }
                    )
                    3 -> JudgeTasksTabContent(
                        tasks = allTasks,
                        onAddTaskClick = { showAddTaskDialog = true },
                        onToggleTask = { viewModel.toggleTaskCompletion(it) },
                        onDeleteTask = { viewModel.deleteTask(it) }
                    )
                    4 -> DailyJournalTabContent(
                        viewModel = viewModel
                    )
                }
            }

            // Trademark design signature at the bottom edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "تطوير: سامي القادري - ٧٧٧٤٨٤١٦٠",
                    fontSize = 11.sp,
                    color = CourtGreen.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Restore Backup Modal Dialog
        if (showRestoreDialog) {
            RestoreBackupDialog(
                onDismiss = { showRestoreDialog = false },
                onRestore = { json ->
                    viewModel.restoreBackup(
                        context = context,
                        jsonString = json,
                        onSuccess = { showRestoreDialog = false },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                onSelectFile = {
                    showRestoreDialog = false
                    try {
                        restoreBackupLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "فشل فتح مستعرض الملفات: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        // Expanded Case Detail View overlays as a bottom modal sheet dialogue
        selectedCase?.let { courtCase ->
            CaseDetailDialog(
                courtCase = courtCase,
                onDismiss = { viewModel.selectCase(null) },
                onArchiveToggle = { 
                    viewModel.toggleArchiveCase(courtCase)
                    Toast.makeText(context, if (courtCase.isArchived) "تم نقل القضية للملف الساري" else "تم نقل القضية للأرشيف المكتبي", Toast.LENGTH_SHORT).show()
                },
                onDelete = {
                    viewModel.deleteCase(courtCase)
                    Toast.makeText(context, "تم حذف سجل القضية بالكامل", Toast.LENGTH_SHORT).show()
                },
                onEdit = {
                    showEditCaseDialog = courtCase
                }
            )
        }

        // Add Case dialogue window
        if (showAddCaseDialog) {
            AddOrEditCaseDialog(
                onDismiss = { showAddCaseDialog = false },
                onSave = { number, d, m, y, name, court, lawyer, sessionDate, notes, status, ruling, judgeName, cYear, cType, cSubject, cParties ->
                    viewModel.addCase(number, d, m, y, name, court, lawyer, sessionDate, notes, status, ruling, judgeName, cYear, cType, cSubject, cParties)
                    showAddCaseDialog = false
                    Toast.makeText(context, "تم حفظ سجل القضية الجديد بنجاح", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Edit Case dialogue window
        showEditCaseDialog?.let { courtCase ->
            AddOrEditCaseDialog(
                courtCase = courtCase,
                onDismiss = { showEditCaseDialog = null },
                onSave = { number, d, m, y, name, court, lawyer, sessionDate, notes, status, ruling, judgeName, cYear, cType, cSubject, cParties ->
                    viewModel.updateCase(
                        courtCase.copy(
                            caseNumber = number,
                            registrationDay = d,
                            registrationMonth = m,
                            registrationYear = y,
                            caseName = name,
                            court = court,
                            lawyer = lawyer,
                            nextSessionDate = sessionDate,
                            notes = notes,
                            status = status,
                            ruling = ruling,
                            judgeName = judgeName,
                            caseYear = cYear,
                            caseType = cType,
                            caseSubject = cSubject,
                            disputeParties = cParties
                        )
                    )
                    showEditCaseDialog = null
                    Toast.makeText(context, "تم تحديث السجل القضائي بنجاح", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Add Task dialogue window
        if (showAddTaskDialog) {
            AddTaskDialog(
                onDismiss = { showAddTaskDialog = false },
                onSave = { title, desc, dueDate ->
                    viewModel.addTask(title, desc, dueDate)
                    showAddTaskDialog = false
                    Toast.makeText(context, "تم إضافة المهمة بنجاح", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// Custom Navigation Tabs for the Judge
@Composable
fun CustomJudgeTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    alertsCount: Int,
    pendingTasksCount: Int
) {
    val tabs = listOf<Pair<String, ImageVector>>(
        Pair("القضايا النشطة", Icons.Default.Folder),
        Pair("الأرشيف القضائي", Icons.Default.Inventory),
        Pair("تنبيهات الجلسات", Icons.Default.NotificationsActive),
        Pair("جدول المهام", Icons.Default.Task),
        Pair("اليومية القضائية", Icons.Default.History)
    )

    ScrollableTabRow(
        selectedTabIndex = selectedTab,
        containerColor = SoftGray,
        contentColor = CourtGreen,
        edgePadding = 8.dp,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                color = CourtGreen,
                height = 3.dp
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        tabs.forEachIndexed { index, pair ->
            val isSelected = selectedTab == index
            Tab(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = pair.second,
                            contentDescription = null,
                            tint = if (isSelected) CourtGreen else SlateText.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = pair.first,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) CourtGreen else SlateText.copy(alpha = 0.7f)
                        )
                        
                        // Action Badges for alert numbers
                        if (index == 2 && alertsCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CrimsonAlert)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = alertsCount.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (index == 3 && pendingTasksCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CourtGold)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = pendingTasksCount.toString(),
                                    color = DeepCharcoal,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

// -------------------------------- TAB 1: ACTIVE CASES --------------------------------
@Composable
fun ActiveCasesTabContent(
    cases: List<CourtCase>,
    onCaseSelected: (CourtCase) -> Unit,
    onAddCaseClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (cases.isEmpty()) {
            EmptyListPlaceholder(
                icon = Icons.Default.Folder,
                message = "لا توجد قضايا نشطة مسجلة حالياً",
                tip = "قم بإضافة قضية جديدة وسجل جميع بياناتها وتواريخ الجلسات بالضغط على زر الإضافة أدناه"
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp, start = 16.dp, end = 16.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(cases, key = { it.id }) { item ->
                    CaseCard(courtCase = item, onClick = { onCaseSelected(item) })
                }
            }
        }

        // Add Floating Action Button (FAB)
        FloatingActionButton(
            onClick = onAddCaseClick,
            containerColor = CourtGold,
            contentColor = CourtGreen,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
                .testTag("add_case_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة سجل قضية")
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة قضية جديدة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// -------------------------------- TAB 2: ARCHIVED CASES --------------------------------
@Composable
fun ArchivedCasesTabContent(
    cases: List<CourtCase>,
    onCaseSelected: (CourtCase) -> Unit
) {
    if (cases.isEmpty()) {
        EmptyListPlaceholder(
            icon = Icons.Default.Inventory,
            message = "الأرشيف القضائي فارغ حالياً",
            tip = "يمكنك أرشفة القضايا التي تم الحكم فيها أو تسويتها بالكامل من خلال الضغط على خيار الأرشفة داخل بطاقة تفاصيل القضية"
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(cases, key = { it.id }) { item ->
                CaseCard(courtCase = item, isArchivedTheme = true, onClick = { onCaseSelected(item) })
            }
        }
    }
}

// -------------------------------- TAB 3: ALERTS --------------------------------
@Composable
fun SessionAlertsTabContent(
    alerts: List<CourtCase>,
    onCaseSelected: (CourtCase) -> Unit
) {
    if (alerts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(CourtGold.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = CourtGreen,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "جميع الجلسات مستقرة وآمنة",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CourtGreen,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "لا توجد جلسات مجدولة خلال الـ 5 أيام القادمة تتطلب تحضيراً عاجلاً.",
                    fontSize = 12.sp,
                    color = SlateText.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SoftAlert),
                border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "تحذير عاجل",
                        tint = CrimsonAlert,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "جلسات عاجلة مقبلة في الـ 5 أيام القادمة (${alerts.size} قضايا تحتاج المذاكرة والاستعداد)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonAlert
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(alerts, key = { it.id }) { item ->
                    val progressDays = calculateRemainingDays(item.nextSessionDate)
                    val progressText = when (progressDays) {
                        0 -> "اليوم!"
                        1 -> "غداً!"
                        2 -> "متبقي يومان"
                        else -> "متبقي $progressDays أيام"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCaseSelected(item) },
                        border = BorderStroke(1.5.dp, if (progressDays <= 1) CrimsonAlert else CourtGold),
                        colors = CardDefaults.cardColors(
                            containerColor = if (progressDays <= 1) SoftAlert else DarkEmerald
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (progressDays <= 1) CrimsonAlert else CourtGold)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = progressText,
                                        color = if (progressDays <= 1) Color.White else DeepCharcoal,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "رقم القضية: ${item.caseNumber}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateText,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.caseName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CourtGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntiqueGold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(item.court, fontSize = 11.sp, color = SlateText.copy(alpha = 0.8f))
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntiqueGold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جلسة: ${item.nextSessionDate}", fontSize = 11.sp, color = SlateText.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------- TAB 4: TASKS --------------------------------
@Composable
fun JudgeTasksTabContent(
    tasks: List<JudgeTask>,
    onAddTaskClick: () -> Unit,
    onToggleTask: (JudgeTask) -> Unit,
    onDeleteTask: (JudgeTask) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (tasks.isEmpty()) {
            EmptyListPlaceholder(
                icon = Icons.Default.Task,
                message = "قائمة المهام القضائية فارغة",
                tip = "اضغط على زر الإضافة بالأسفل لإنشاء تذكير عاجل أو مهمة تحضيرية قبل المداولات والجلوس بساحة المحكمة"
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp, start = 16.dp, end = 16.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Pending tasks header
                val pending = tasks.filter { !it.isCompleted }
                val completed = tasks.filter { it.isCompleted }

                if (pending.isNotEmpty()) {
                    item {
                        Text(
                            text = "المهام الجارية والتذكيرات المكتوبية (${pending.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CourtGreen,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(pending, key = { it.id }) { task ->
                        TaskItemRow(task, onToggle = { onToggleTask(task) }, onDelete = { onDeleteTask(task) })
                    }
                }

                if (completed.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "المهام المنجزة بقلم القاضي (${completed.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(completed, key = { it.id }) { task ->
                        TaskItemRow(task, isCompleted = true, onToggle = { onToggleTask(task) }, onDelete = { onDeleteTask(task) })
                    }
                }
            }
        }

        // Add task FAB
        FloatingActionButton(
            onClick = onAddTaskClick,
            containerColor = CourtGold,
            contentColor = CourtGreen,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
                .testTag("add_task_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مهمة لتنبيهات المحكمة")
                Spacer(modifier = Modifier.width(6.dp))
                Text("تذكير/مهمة جديدة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// -------------------------------- SHARED SUBCOMPOSABLES --------------------------------

@Composable
fun CaseCard(
    courtCase: CourtCase,
    isArchivedTheme: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("case_card_${courtCase.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isArchivedTheme) SoftGray.copy(alpha = 0.7f) else DarkEmerald
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(
            1.dp,
            if (isArchivedTheme) Color.Gray.copy(alpha = 0.2f) else CourtGold.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Case Number Gold Emblem
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isArchivedTheme) Color.Gray.copy(alpha = 0.2f) else CourtGold.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = courtCase.caseNumber,
                        fontWeight = FontWeight.Bold,
                        color = if (isArchivedTheme) Color.Gray else CourtGreen,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = courtCase.caseName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isArchivedTheme) Color.Gray else DeepCharcoal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "محكمة: ${courtCase.court}",
                        fontSize = 11.sp,
                        color = SlateText.copy(alpha = 0.6f)
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CourtGreen.copy(alpha = 0.08f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "الحالة: ${courtCase.status}",
                                fontSize = 10.sp,
                                color = CourtGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (courtCase.judgeName.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CourtGold.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "القاضي: ${courtCase.judgeName}",
                                    fontSize = 10.sp,
                                    color = CourtGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (courtCase.isArchived) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Gray.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("مؤرشفة مكتبياً", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "تفاصيل",
                        tint = AntiqueGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = SlateText.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isArchivedTheme) Color.Gray else CourtGold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "تاريخ القيد: ${courtCase.registrationDay}/${courtCase.registrationMonth}/${courtCase.registrationYear}",
                        fontSize = 11.sp,
                        color = SlateText.copy(alpha = 0.6f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isArchivedTheme) Color.Gray else CourtGreen
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "الجلسة القادمة: ${courtCase.nextSessionDate}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isArchivedTheme) Color.Gray else CourtGreen
                    )
                }
            }
        }
    }
}

@Composable
fun TaskItemRow(
    task: JudgeTask,
    isCompleted: Boolean = false,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) SoftGray.copy(alpha = 0.5f) else DarkEmerald
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = CourtGreen,
                    uncheckedColor = CourtGreen.copy(alpha = 0.6f)
                ),
                modifier = Modifier.testTag("task_check_${task.id}")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted) SlateText.copy(alpha = 0.5f) else DeepCharcoal
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        fontSize = 11.sp,
                        color = SlateText.copy(alpha = if (isCompleted) 0.3f else 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Due date warning if matches
                if (task.dueDate.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "تاريخ الاستحقاق",
                            tint = if (isCompleted) Color.Gray else CourtGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "تاريخ المطلوب: ${task.dueDate}",
                            fontSize = 10.sp,
                            color = if (isCompleted) Color.Gray else CourtGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف المهمة",
                    tint = CrimsonAlert.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun EmptyListPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    tip: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(45.dp))
                    .background(SoftGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CourtGreen.copy(alpha = 0.5f),
                    modifier = Modifier.size(45.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                fontSize = 15.sp,
                color = SlateText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tip,
                fontSize = 11.sp,
                color = SlateText.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

// -------------------------------- DIALOGS --------------------------------

@Composable
fun CaseDetailDialog(
    courtCase: CourtCase,
    onDismiss: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 620.dp)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
            border = BorderStroke(1.dp, CourtGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with Badge status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تفاصيل ملف القضية",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CourtGreen
                    )

                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (courtCase.isArchived) Color.Gray.copy(alpha = 0.2f) else CourtGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (courtCase.isArchived) "ملف مؤرشف" else "قضية جارية",
                            color = if (courtCase.isArchived) Color.Gray else CourtGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Case metadata items with elegant spacing
                DetailFieldItem(label = "رقم القضية", value = courtCase.caseNumber, icon = Icons.Default.Info)
                DetailFieldItem(label = "سنة القضية", value = courtCase.caseYear.ifBlank { "غير محددة" }, icon = Icons.Default.DateRange)
                DetailFieldItem(label = "نوع القضية", value = courtCase.caseType.ifBlank { "غير محدد" }, icon = Icons.Default.Gavel)
                DetailFieldItem(label = "موضوع القضية", value = courtCase.caseSubject.ifBlank { "غير محدد" }, icon = Icons.Default.Info)
                DetailFieldItem(label = "أطراف الخصومة", value = courtCase.disputeParties.ifBlank { "غير محدد" }, icon = Icons.Default.Person)
                DetailFieldItem(label = "تاريخ القيد والتسجيل", value = "${courtCase.registrationDay} / ${courtCase.registrationMonth} / ${courtCase.registrationYear}", icon = Icons.Default.DateRange)
                DetailFieldItem(label = "اسم وموضوع الدعوى (الأطراف)", value = courtCase.caseName, icon = Icons.Default.Gavel, highlight = true)
                DetailFieldItem(label = "المحكمة المختصة الدائرة", value = courtCase.court, icon = Icons.Default.AccountBalance)
                DetailFieldItem(label = "رئيس الجلسة / اسم القاضي", value = courtCase.judgeName.ifBlank { "غير محدد" }, icon = Icons.Default.Person)
                DetailFieldItem(label = "المحامي الوكيل الصادر باسمه", value = courtCase.lawyer.ifBlank { "غير محدد / لم يحضر وكيل" }, icon = Icons.Default.Person)
                DetailFieldItem(label = "تاريخ الجلسة القادمة", value = courtCase.nextSessionDate, icon = Icons.Default.DateRange, isDate = true)
                DetailFieldItem(label = "الحالة الحالية للقضية", value = courtCase.status, icon = Icons.Default.Gavel, highlight = true)
                DetailFieldItem(label = "منطوق وآخر قرار للجلسة", value = courtCase.ruling.ifBlank { "لم يصدر منطوق أو حكم بعد" }, icon = Icons.Default.Info)
                DetailFieldItem(label = "مذكرات وملاحظات مكتب المحامي", value = courtCase.notes.ifBlank { "بلا ملاحظات مسجلة" }, icon = Icons.Default.Info)

                Spacer(modifier = Modifier.height(24.dp))

                // Actions buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onEdit,
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تعديل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onArchiveToggle()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGold),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Icon(
                            imageVector = if (courtCase.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (courtCase.isArchived) "التبديل لجارية" else "نقل للأرشيف",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepCharcoal
                        )
                    }

                    Button(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حذف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dismiss flat button
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CourtGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إغلاق الملف", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DetailFieldItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlight: Boolean = false,
    isDate: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDate) CrimsonAlert else AntiqueGold,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SlateText.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (highlight) CourtGold.copy(alpha = 0.08f)
                    else if (isDate) CrimsonAlert.copy(alpha = 0.05f)
                    else SoftGray.copy(alpha = 0.4f)
                )
                .border(
                    1.dp,
                    if (highlight) CourtGold.copy(alpha = 0.3f)
                    else if (isDate) CrimsonAlert.copy(alpha = 0.2f)
                    else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .padding(10.dp)
        ) {
            Text(
                text = value,
                fontSize = if (highlight) 14.sp else 13.sp,
                fontWeight = if (highlight || isDate) FontWeight.Bold else FontWeight.Normal,
                color = if (isDate) CrimsonAlert else DeepCharcoal,
                lineHeight = 18.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrEditCaseDialog(
    courtCase: CourtCase? = null,
    onDismiss: () -> Unit,
    onSave: (
        caseNumber: String,
        day: Int,
        month: Int,
        year: Int,
        caseName: String,
        court: String,
        lawyer: String,
        nextSessionDate: String,
        notes: String,
        status: String,
        ruling: String,
        judgeName: String,
        caseYear: String,
        caseType: String,
        caseSubject: String,
        disputeParties: String
    ) -> Unit
) {
    var caseNumber by remember { mutableStateOf(courtCase?.caseNumber ?: "") }
    var caseName by remember { mutableStateOf(courtCase?.caseName ?: "") }
    var court by remember { mutableStateOf(courtCase?.court ?: "") }
    var lawyer by remember { mutableStateOf(courtCase?.lawyer ?: "") }
    var notes by remember { mutableStateOf(courtCase?.notes ?: "") }
    var status by remember { mutableStateOf(courtCase?.status ?: "قيد النظر") }
    var ruling by remember { mutableStateOf(courtCase?.ruling ?: "") }
    var judgeName by remember { mutableStateOf(courtCase?.judgeName ?: "") }
    var caseYear by remember { mutableStateOf(courtCase?.caseYear ?: "") }
    var caseType by remember { mutableStateOf(courtCase?.caseType ?: "") }
    var caseSubject by remember { mutableStateOf(courtCase?.caseSubject ?: "") }
    var disputeParties by remember { mutableStateOf(courtCase?.disputeParties ?: "") }

    // Registration date components
    val calendar = Calendar.getInstance()
    var regDay by remember { mutableStateOf(courtCase?.registrationDay?.toString() ?: calendar.get(Calendar.DAY_OF_MONTH).toString()) }
    var regMonth by remember { mutableStateOf(courtCase?.registrationMonth?.toString() ?: (calendar.get(Calendar.MONTH) + 1).toString()) }
    var regYear by remember { mutableStateOf(courtCase?.registrationYear?.toString() ?: calendar.get(Calendar.YEAR).toString()) }

    // Next Session date: "yyyy-MM-dd"
    val defaultFutureDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
        Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000) // Default 7 days from now
    )
    var nextSessionDate by remember { mutableStateOf(courtCase?.nextSessionDate ?: defaultFutureDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val currentParts = nextSessionDate.split("-")
        val yearVal = currentParts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)
        val monthVal = (currentParts.getOrNull(1)?.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
        val dayVal = currentParts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)

        android.app.DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                val formattedMonth = String.format(Locale.US, "%02d", selectedMonth + 1)
                val formattedDay = String.format(Locale.US, "%02d", selectedDayOfMonth)
                nextSessionDate = "$selectedYear-$formattedMonth-$formattedDay"
                showDatePicker = false
            },
            yearVal,
            monthVal,
            dayVal
        ).apply {
            setOnDismissListener {
                showDatePicker = false
            }
            show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 620.dp)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
            border = BorderStroke(1.dp, CourtGreen)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (courtCase == null) "قيد وإدخال ملف قضية" else "تعديل بيانات ملف القضية",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CourtGreen,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Input values
                OutlinedTextField(
                    value = caseNumber,
                    onValueChange = { caseNumber = it },
                    label = { Text("١. رقم القضية (سجلي الاستدلالي)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_case_number"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = caseYear,
                    onValueChange = { caseYear = it },
                    label = { Text("٢. سنة القضية") },
                    placeholder = { Text("مثال: ٢٠٢٦ أو ١٤٤٧هـ") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_case_year"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = caseType,
                    onValueChange = { caseType = it },
                    label = { Text("٣. نوع القضية") },
                    placeholder = { Text("جزائية، مدنية، تجارية، إدارية، أحوال شخصية...إلخ") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_case_type"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = caseSubject,
                    onValueChange = { caseSubject = it },
                    label = { Text("٤. موضوع القضية") },
                    placeholder = { Text("خيانة أمانة، احتيال، تنفيذ التزام، فسخ للكراهية...إلخ") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_case_subject"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = disputeParties,
                    onValueChange = { disputeParties = it },
                    label = { Text("٥. أطراف الخصومة") },
                    placeholder = { Text("المدعي، المدعى عليه، المدخل، المتدخل...إلخ") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_case_parties"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Registration date components helper
                Text(
                    text = "٦. تاريخ القيد والتسجيل باليوم والشهر والسنة",
                    fontSize = 11.sp,
                    color = SlateText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = regDay,
                        onValueChange = { if (it.length <= 2) regDay = it },
                        label = { Text("يوم") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("input_reg_day"),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = regMonth,
                        onValueChange = { if (it.length <= 2) regMonth = it },
                        label = { Text("شهر") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("input_reg_month"),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = regYear,
                        onValueChange = { if (it.length <= 4) regYear = it },
                        label = { Text("سنة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.5f).testTag("input_reg_year"),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = court,
                    onValueChange = { court = it },
                    label = { Text("٧. المحكمة المختصة") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_case_court"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = lawyer,
                    onValueChange = { lawyer = it },
                    label = { Text("٨. المحامي الوكيل") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_case_lawyer"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text("٩. حالة القضية القضائية") },
                    placeholder = { Text("مثال: قيد النظر، مؤجلة، شطب، محكومة") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_case_status"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = judgeName,
                    onValueChange = { judgeName = it },
                    label = { Text("١٠. القاضي متولي القضية") },
                    placeholder = { Text("أدخل اسم القاضي أو الهيئة القضائية...") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_case_judge_name"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("١١. ملاحظات ومذكرات وتفاصيل") },
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_case_notes"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (caseNumber.isBlank() || disputeParties.isBlank() || court.isBlank()) {
                                return@Button
                            }
                            val day = regDay.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
                            val month = regMonth.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)
                            val year = regYear.toIntOrNull() ?: calendar.get(Calendar.YEAR)

                            val finalCaseName = disputeParties
                            val finalNextSessionDate = courtCase?.nextSessionDate ?: ""
                            val finalRuling = courtCase?.ruling ?: ""

                            onSave(
                                caseNumber, day, month, year, finalCaseName, court, lawyer, finalNextSessionDate, notes, status, finalRuling, judgeName,
                                caseYear, caseType, caseSubject, disputeParties
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen),
                        modifier = Modifier.weight(1f).testTag("save_case_button"),
                        enabled = caseNumber.isNotBlank() && disputeParties.isNotBlank() && court.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("حفظ السجل القضائي", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.5f)
                    ) {
                        Text("إلغاء")
                    }
                }
            }
        }
    }
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, dueDate: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val defaultFutureDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
        Date(System.currentTimeMillis() + 1 * 24 * 60 * 60 * 1000) // Next day
    )
    var dueDate by remember { mutableStateOf(defaultFutureDate) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 580.dp)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
            border = BorderStroke(1.dp, CourtGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "إضافة مهمة تذكيرية جديدة",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CourtGreen,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المهمة (مثال: دراسة مذكرات الرد)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_task_title"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("تفاصيل وتذكير إضافي") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_task_desc"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("تاريخ المطلوب والتنفيذ (YYYY-MM-DD)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_task_due_date"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (title.isBlank()) return@Button
                            onSave(title, desc, dueDate)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen),
                        enabled = title.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("save_task_button")
                    ) {
                        Text("حفظ المهمة", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.5f)
                    ) {
                        Text("إلغاء")
                    }
                }
            }
        }
    }
}

// Global days calculator
fun calculateRemainingDays(dateStr: String): Int {
    if (dateStr.isBlank()) return 999
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return try {
        val sessionDate = format.parse(dateStr) ?: return 999
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val diffInMillis = sessionDate.time - today.time
        val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)
        diffInDays.toInt()
    } catch (e: Exception) {
        999
    }
}

@Composable
fun RestoreBackupDialog(
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
    onSelectFile: () -> Unit
) {
    var backupText by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 580.dp)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkEmerald),
            border = BorderStroke(1.dp, CourtGreen)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "استعادة النسخة الاحتياطية",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CourtGreen,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // High-profile File Picker Option
                Button(
                    onClick = {
                        onSelectFile()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CourtGold),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استيراد من ملف نسخة احتياطية (.json)", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Divider(modifier = Modifier.weight(1f), color = SoftGray)
                    Text(
                        text = "أو لصق يدوي",
                        fontSize = 10.sp,
                        color = AntiqueGold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Divider(modifier = Modifier.weight(1f), color = SoftGray)
                }

                Text(
                    text = "قم بلصق كود النسخة الاحتياطية (JSON) الذي قمت بنسخه مسبقاً في الحقل أدناه لإستعادة كافة القضايا والتذكيرات المكتوبية فوراً:",
                    fontSize = 11.sp,
                    color = SlateText,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = backupText,
                    onValueChange = { backupText = it },
                    label = { Text("كود النسخة الاحتياطية الاستردادي") },
                    placeholder = { Text("الصق الكود هنا...") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (backupText.isNotBlank()) {
                                onRestore(backupText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen),
                        modifier = Modifier.weight(1f),
                        enabled = backupText.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("استيراد واستعادة", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text("إلغاء")
                    }
                }
            }
        }
    }
}

// -------------------------------- TAB 5: DAILY JOURNAL & TIMELINE --------------------------------
@Composable
fun DailyJournalTabContent(
    viewModel: CaseViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeCases by viewModel.activeCases.collectAsStateWithLifecycle(emptyList())
    val archivedCases by viewModel.archivedCases.collectAsStateWithLifecycle(emptyList())
    val allCases = remember(activeCases, archivedCases) { activeCases + archivedCases }

    var selectedCaseId by remember { mutableStateOf<Int?>(null) }
    var showCasePickerDialog by remember { mutableStateOf(false) }

    val selectedCase = remember(allCases, selectedCaseId) {
        allCases.find { it.id == selectedCaseId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal)
            .padding(16.dp)
    ) {
        // Selector Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkEmerald),
            border = BorderStroke(1.dp, SoftGray)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "اليومية وسجل متابعة الجلسات في الجمهورية اليمنية",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CourtGold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedCase?.let { "القضية المحددة: ${it.caseName}" } ?: "لم يتم اختيار ملف قضية للمتابعة بعد",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (selectedCase != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "رقم القضية: ${selectedCase.caseNumber} • المحكمة: ${selectedCase.court}",
                            fontSize = 11.sp,
                            color = AntiqueGold
                        )
                    }
                }

                Button(
                    onClick = { showCasePickerDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CourtGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    val labelText = if (selectedCase == null) "اختر ملف قضية" else "تغيير القضية"
                    Text(labelText, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (selectedCase == null) {
            // Placeholder empty state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(24.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkEmerald),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = CourtGold.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "سجل اليومية القضائية الرسمي",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "اختر أي قضية محفوظة في مكتب المحامي عبداللطيف السيقل للبدء في تدوين القرارات اليومية وجلساتها المتعاقبة، والاحتفاظ بالسجل التاريخي مع ميزة طباعته.",
                            fontSize = 12.sp,
                            color = AntiqueGold,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showCasePickerDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CourtGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("البحث وتحديد ملف قضية", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Interactive layout with Form and Logs timeline
            val sessionLogs by viewModel.getSessionLogsForCase(selectedCase.id).collectAsStateWithLifecycle(emptyList())

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Column: Core Session Timeline Scribe & Actions (takes 1.2 weight)
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                ) {
                    // Logs title & print actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سجل الجلسات التاريخي (${sessionLogs.size} جلسات)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )

                        if (sessionLogs.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    viewModel.printSessionLogsReport(context, selectedCase, sessionLogs)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CourtGold.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = "طباعة تقرير سير الجلسات",
                                    tint = CourtGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (sessionLogs.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkEmerald),
                                border = BorderStroke(1.dp, SoftGray)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "السجل فارغ تماماً",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = AntiqueGold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "لم يتم تسجيل أي قرارات يومية في هذه القضية بعد. استخدم استمارة الإدخال المقابلة لإضافة أول محضر جلسة.",
                                        fontSize = 11.sp,
                                        color = AntiqueGold.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sessionLogs) { log ->
                                    SessionLogItemCard(log = log, onDelete = { viewModel.deleteSessionLog(it) })
                                }
                            }
                        }
                    }
                }

                // Right Column: Input form to record next timeline state (takes 1f weight)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkEmerald),
                    border = BorderStroke(1.dp, SoftGray)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "تسجيل قرار وجلسة جديدة",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CourtGreen,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        var lastSessionDate by remember { mutableStateOf("") }
                        var decision by remember { mutableStateOf("") }
                        var nextSessionDate by remember { mutableStateOf("") }
                        var logNotes by remember { mutableStateOf("") }
                        
                        var showLastDatePicker by remember { mutableStateOf(false) }
                        var showNextDatePicker by remember { mutableStateOf(false) }

                        // Date Pickers dialog popups
                        val calendar = Calendar.getInstance()
                        if (showLastDatePicker) {
                            android.app.DatePickerDialog(
                                context,
                                { _, sy, sm, sd ->
                                    val formattedMonth = String.format(Locale.US, "%02d", sm + 1)
                                    val formattedDay = String.format(Locale.US, "%02d", sd)
                                    lastSessionDate = "$sy-$formattedMonth-$formattedDay"
                                    showLastDatePicker = false
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).apply {
                                setOnDismissListener { showLastDatePicker = false }
                                show()
                            }
                        }

                        if (showNextDatePicker) {
                            android.app.DatePickerDialog(
                                context,
                                { _, sy, sm, sd ->
                                    val formattedMonth = String.format(Locale.US, "%02d", sm + 1)
                                    val formattedDay = String.format(Locale.US, "%02d", sd)
                                    nextSessionDate = "$sy-$formattedMonth-$formattedDay"
                                    showNextDatePicker = false
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).apply {
                                setOnDismissListener { showNextDatePicker = false }
                                show()
                            }
                        }

                        // Last Session Date Input
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLastDatePicker = true }
                        ) {
                            OutlinedTextField(
                                value = lastSessionDate,
                                onValueChange = {},
                                label = { Text("تاريخ الجلسة الأخيرة") },
                                placeholder = { Text("أدخل تاريخ جلسة اليوم...") },
                                readOnly = true,
                                enabled = false,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = SlateText,
                                    disabledBorderColor = SoftGray,
                                    disabledLabelColor = CourtGreen
                                ),
                                trailingIcon = {
                                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null, tint = CourtGold)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Decision / Status
                        OutlinedTextField(
                            value = decision,
                            onValueChange = { decision = it },
                            label = { Text("قرار ومنطوق الجلسة") },
                            placeholder = { Text("تحت الدراسة، التأجيل لحضور المدعي...") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                        )

                        // Quick-decision buttons for speedy judicial entry
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("تأجيل لتقديم رد", "حجز للحكم", "ندب خبير").forEach { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CourtGold.copy(alpha = 0.12f))
                                        .clickable { decision = suggestion }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(suggestion, fontSize = 9.sp, color = SlateText)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Next Session Date
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNextDatePicker = true }
                        ) {
                            OutlinedTextField(
                                value = nextSessionDate,
                                onValueChange = {},
                                label = { Text("تاريخ الجلسة القادمة المجدولة") },
                                placeholder = { Text("تحديد موعد المتابعة القادمة") },
                                readOnly = true,
                                enabled = false,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = SlateText,
                                    disabledBorderColor = SoftGray,
                                    disabledLabelColor = CourtGreen
                                ),
                                trailingIcon = {
                                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null, tint = CourtGold)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Notes Scribe
                        OutlinedTextField(
                            value = logNotes,
                            onValueChange = { logNotes = it },
                            label = { Text("ملاحظات وتفاصيل التدوين اليومي") },
                            placeholder = { Text("كتابة مذكرات أو مهام ثانوية مطلوبة...") },
                            minLines = 3,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (lastSessionDate.isBlank() || decision.isBlank() || nextSessionDate.isBlank()) {
                                    Toast.makeText(context, "الرجاء تعبئة مواعيد الجلسات والقرار لتسجيل اليومية", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.addSessionLog(
                                    caseId = selectedCase.id,
                                    lastSessionDate = lastSessionDate,
                                    decision = decision,
                                    nextSessionDate = nextSessionDate,
                                    logNotes = logNotes
                                )
                                // success reset
                                lastSessionDate = ""
                                decision = ""
                                nextSessionDate = ""
                                logNotes = ""
                                Toast.makeText(context, "تم قيد اليومية وتحديث القضية تلقائياً", Toast.LENGTH_SHORT).show()
                            },
                            enabled = lastSessionDate.isNotBlank() && decision.isNotBlank() && nextSessionDate.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = CourtGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("قيد الجلسة في اليومية", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Modal Case Picker Dialog
    if (showCasePickerDialog) {
        CasePickerDialog(
            cases = allCases,
            onDismiss = { showCasePickerDialog = false },
            onSelect = { 
                selectedCaseId = it.id
                showCasePickerDialog = false
            }
        )
    }
}

@Composable
fun SessionLogItemCard(
    log: com.example.data.SessionLog,
    onDelete: (com.example.data.SessionLog) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkEmerald),
        border = BorderStroke(1.dp, SoftGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CourtGreen.copy(alpha = 0.1f))
                            .padding(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = null, tint = CourtGreen, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "جلسة تاريخ: ${log.lastSessionDate}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CourtGreen
                    )
                }

                IconButton(
                    onClick = { onDelete(log) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف الجلسة", tint = CrimsonAlert, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Decision
            Text(
                text = "القرار المتخذ والمنطوق:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CourtGold
            )
            Text(
                text = log.decision,
                fontSize = 12.sp,
                color = SlateText,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            // Next session timeline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.History, contentDescription = null, tint = AntiqueGold, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "تاريخ الجلسة القادمة: ${log.nextSessionDate}",
                    fontSize = 11.sp,
                    color = AntiqueGold,
                    fontWeight = FontWeight.Bold
                )
            }

            if (log.logNotes.isNotBlank()) {
                Divider(modifier = Modifier.padding(vertical = 6.dp), color = SoftGray)
                Text(
                    text = "ملاحظات ومذكرات المتابعة:",
                    fontSize = 10.sp,
                    color = AntiqueGold,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = log.logNotes,
                    fontSize = 11.sp,
                    color = WarmWhite
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasePickerDialog(
    cases: List<CourtCase>,
    onDismiss: () -> Unit,
    onSelect: (CourtCase) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCases = remember(cases, searchQuery) {
        if (searchQuery.isBlank()) cases else {
            cases.filter { 
                it.caseNumber.contains(searchQuery, ignoreCase = true) || 
                it.caseName.contains(searchQuery, ignoreCase = true) ||
                it.court.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 600.dp)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkEmerald),
            border = BorderStroke(1.dp, CourtGreen)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "البحث واختيار ملف قضية",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CourtGreen,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("ابحث بالاسم، رقم القضية، المحكمة...") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CourtGold) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CourtGreen)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp, min = 120.dp)
                ) {
                    if (filteredCases.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "لا توجد قضايا مطابقة للبحث",
                                color = AntiqueGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredCases) { courtCase ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(courtCase) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                    border = BorderStroke(1.dp, SoftGray)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = courtCase.caseName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "رقم: ${courtCase.caseNumber}",
                                                fontSize = 11.sp,
                                                color = CourtGold,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "المحكمة: ${courtCase.court}",
                                                fontSize = 11.sp,
                                                color = AntiqueGold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("إغلاق", fontSize = 12.sp)
                }
            }
        }
    }
}
