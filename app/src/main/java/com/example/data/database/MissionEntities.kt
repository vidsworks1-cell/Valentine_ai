package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "aza_missions")
data class AzaMission(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val status: String = "Active", // Active, Paused, Completed, Failed
    val progress: Float = 0f, // 0f to 100f
    val createdAt: Long = System.currentTimeMillis(),
    val lastActivity: Long = System.currentTimeMillis(),
    val assignedAgents: String = "", // comma-separated e.g. "TradingAgent, RiskAgent"
    val priority: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val currentTask: String = "None",
    val reviewWasSuccessful: Boolean = false,
    val reviewDelayingTasks: String = "",
    val reviewBestAgent: String = "",
    val reviewMistakes: String = "",
    val reviewLessonsLearned: String = "",
    val hasReview: Boolean = false,
    val category: String = "Custom", // Trading, Content Creation, YouTube Growth, Freelance, Research, Learning, Custom
    val schedule: String = "One-Shot", // Daily, Weekly, Recurring, One-Shot
    val budget: Double = 0.0,
    val deadline: String = "",
    val successCriteria: String = ""
)

@Dao
interface AzaMissionDao {
    @Query("SELECT * FROM aza_missions ORDER BY createdAt DESC")
    fun getAllMissionsFlow(): Flow<List<AzaMission>>

    @Query("SELECT * FROM aza_missions WHERE id = :id LIMIT 1")
    suspend fun getMissionById(id: Int): AzaMission?

    @Query("SELECT * FROM aza_missions WHERE status = 'Active' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveMissionDirect(): AzaMission?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMission(mission: AzaMission): Long

    @Update
    suspend fun updateMission(mission: AzaMission)

    @Delete
    suspend fun deleteMission(mission: AzaMission)

    @Query("SELECT id FROM aza_missions WHERE status = 'Paused'")
    suspend fun getPausedMissionIds(): List<Int>

    @Query("DELETE FROM aza_missions")
    suspend fun clearAllMissions()
}
