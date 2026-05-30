package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {
    @Query("SELECT * FROM court_cases ORDER BY registrationYear DESC, registrationMonth DESC, registrationDay DESC")
    fun getAllCases(): Flow<List<CourtCase>>

    @Query("SELECT * FROM court_cases WHERE id = :id")
    suspend fun getCaseById(id: Int): CourtCase?

    @Query("SELECT * FROM court_cases WHERE isArchived = 0 ORDER BY nextSessionDate ASC")
    fun getActiveCases(): Flow<List<CourtCase>>

    @Query("SELECT * FROM court_cases WHERE isArchived = 1 ORDER BY nextSessionDate DESC")
    fun getArchivedCases(): Flow<List<CourtCase>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCase(courtCase: CourtCase): Long

    @Update
    suspend fun updateCase(courtCase: CourtCase)

    @Delete
    suspend fun deleteCase(courtCase: CourtCase)

    @Query("""
        SELECT * FROM court_cases 
        WHERE caseNumber LIKE '%' || :query || '%' 
        OR caseName LIKE '%' || :query || '%' 
        OR court LIKE '%' || :query || '%' 
        OR lawyer LIKE '%' || :query || '%' 
        OR notes LIKE '%' || :query || '%'
        ORDER BY registrationYear DESC
    """)
    fun searchCases(query: String): Flow<List<CourtCase>>
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM judge_tasks ORDER BY dueDate ASC, createdAt DESC")
    fun getAllTasks(): Flow<List<JudgeTask>>

    @Query("SELECT * FROM judge_tasks WHERE isCompleted = 0 ORDER BY dueDate ASC")
    fun getPendingTasks(): Flow<List<JudgeTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: JudgeTask): Long

    @Update
    suspend fun updateTask(task: JudgeTask)

    @Delete
    suspend fun deleteTask(task: JudgeTask)
}

@Database(entities = [CourtCase::class, JudgeTask::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "judge_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
