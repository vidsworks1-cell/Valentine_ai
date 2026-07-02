package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "aza_objectives")
data class AzaObjective(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val missionId: Int? = null,
    val objective: String,
    val title: String = objective,
    val targetPlatform: String = "ALL", // YOUTUBE, TIKTOK, CRYPTO, FREELANCE, ALL
    val targetBudget: Double = 0.0,
    val status: String = "ACTIVE", // ACTIVE, COMPLETED, CANCELLED
    val createdAt: Long = System.currentTimeMillis(),
    val priority: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val updatedAt: Long = System.currentTimeMillis(),
    val currentTask: String = "None",
    val progress: Float = 0f, // 0f to 1f
    val completionReason: String = ""
)

@Entity(
    tableName = "aza_agent_tasks",
    foreignKeys = [
        ForeignKey(
            entity = AzaObjective::class,
            parentColumns = ["id"],
            childColumns = ["objectiveId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("objectiveId")]
)
data class AzaAgentTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val objectiveId: Int,
    val assignedAgent: String, // e.g. "Crypto Trading Agent", "Social Content Director", "Gig proposal writer"
    val title: String,
    val description: String,
    val priority: String, // HIGH, MEDIUM, LOW
    val status: String, // PENDING_APPROVAL, APPROVED, RUNNING, COMPLETED, REJECTED, FAILED
    val estimatedBudget: Double = 0.0,
    val executionLog: String = "",
    val performanceImpact: String = "",
    val requiredSkill: String = "",
    val input: String = "",
    val output: String = "",
    val dependencies: String = "", // comma-separated task title/id/index dependencies
    val retryCount: Int = 0
)

@Dao
interface AzaGoalDao {
    @Query("SELECT * FROM aza_objectives WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED') ORDER BY createdAt DESC LIMIT 1")
    fun getActiveObjectiveFlow(): Flow<AzaObjective?>

    @Query("SELECT * FROM aza_objectives WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED') ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveObjectiveDirect(): AzaObjective?

    @Query("SELECT * FROM aza_objectives WHERE id = :objectiveId")
    suspend fun getObjectiveByIdDirect(objectiveId: Int): AzaObjective?

    @Query("SELECT * FROM aza_objectives ORDER BY createdAt DESC")
    fun getAllObjectivesFlow(): Flow<List<AzaObjective>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObjective(objective: AzaObjective): Long

    @Update
    suspend fun updateObjective(objective: AzaObjective)

    @Query("SELECT * FROM aza_agent_tasks WHERE objectiveId = :objectiveId ORDER BY priority DESC")
    fun getTasksForObjectiveFlow(objectiveId: Int): Flow<List<AzaAgentTask>>

    @Query("SELECT * FROM aza_agent_tasks WHERE objectiveId = :objectiveId")
    suspend fun getTasksForObjectiveDirect(objectiveId: Int): List<AzaAgentTask>

    @Query("SELECT * FROM aza_agent_tasks WHERE status = 'PENDING_APPROVAL'")
    fun getPendingApprovalTasksFlow(): Flow<List<AzaAgentTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: AzaAgentTask): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<AzaAgentTask>)

    @Update
    suspend fun updateTask(task: AzaAgentTask)

    @Query("SELECT * FROM aza_objectives WHERE missionId = :missionId")
    suspend fun getObjectivesForMissionDirect(missionId: Int): List<AzaObjective>

    @Query("DELETE FROM aza_objectives")
    suspend fun clearAllObjectives()

    @Query("DELETE FROM aza_agent_tasks")
    suspend fun clearAllTasks()
}
