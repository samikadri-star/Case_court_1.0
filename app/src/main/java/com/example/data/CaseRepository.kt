package com.example.data

import kotlinx.coroutines.flow.Flow

class CaseRepository(
    private val caseDao: CaseDao,
    private val taskDao: TaskDao,
    private val sessionLogDao: SessionLogDao
) {
    
    val allCases: Flow<List<CourtCase>> = caseDao.getAllCases()
    val activeCases: Flow<List<CourtCase>> = caseDao.getActiveCases()
    val archivedCases: Flow<List<CourtCase>> = caseDao.getArchivedCases()
    val allTasks: Flow<List<JudgeTask>> = taskDao.getAllTasks()
    val pendingTasks: Flow<List<JudgeTask>> = taskDao.getPendingTasks()
    val allSessionLogs: Flow<List<SessionLog>> = sessionLogDao.getAllSessionLogs()

    fun getSessionLogsForCase(caseId: Int): Flow<List<SessionLog>> {
        return sessionLogDao.getSessionLogsForCase(caseId)
    }

    suspend fun insertSessionLog(log: SessionLog): Long {
        return sessionLogDao.insertSessionLog(log)
    }

    suspend fun deleteSessionLog(log: SessionLog) {
        sessionLogDao.deleteSessionLog(log)
    }

    suspend fun getCaseById(id: Int): CourtCase? {
        return caseDao.getCaseById(id)
    }

    suspend fun insertCase(courtCase: CourtCase): Long {
        return caseDao.insertCase(courtCase)
    }

    suspend fun updateCase(courtCase: CourtCase) {
        caseDao.updateCase(courtCase)
    }

    suspend fun deleteCase(courtCase: CourtCase) {
        caseDao.deleteCase(courtCase)
    }

    fun searchCases(query: String): Flow<List<CourtCase>> {
        return caseDao.searchCases(query)
    }

    suspend fun insertTask(task: JudgeTask): Long {
        return taskDao.insertTask(task)
    }

    suspend fun updateTask(task: JudgeTask) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: JudgeTask) {
        taskDao.deleteTask(task)
    }
}
