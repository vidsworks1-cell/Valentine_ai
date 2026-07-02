package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "task_queue")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val objectiveId: Int,
    val title: String,
    val description: String,
    val assignedAgent: String,
    val requiredSkill: String,
    val status: String = "QUEUED", // QUEUED, WAITING, RUNNING, COMPLETED, FAILED, RETRY
    val estimatedBudget: Double = 0.0,
    val executionLog: String = "",
    val outputSummary: String = "",
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val priority: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "task_executions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class TaskExecution(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val status: String, // RUNNING, COMPLETED, FAILED
    val errorMessage: String? = null,
    val systemDiagnostics: String = "", // Battery, Network, etc.
    val outcomeSummary: String = ""
)

@Entity(
    tableName = "task_dependencies",
    primaryKeys = ["taskId", "dependsOnTaskId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId"), Index("dependsOnTaskId")]
)
data class TaskDependency(
    val taskId: Int,
    val dependsOnTaskId: Int
)

@Dao
interface TaskQueueDao {
    @Query("SELECT * FROM task_queue ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task_queue WHERE status = :status ORDER BY priority DESC, createdAt ASC")
    suspend fun getTasksByStatusDirect(status: String): List<TaskEntity>

    @Query("SELECT * FROM task_queue WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Int): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("SELECT * FROM task_queue WHERE objectiveId = :objectiveId")
    suspend fun getTasksByObjectiveDirect(objectiveId: Int): List<TaskEntity>

    @Query("DELETE FROM task_queue")
    suspend fun clearAllTasks()

    // Task Executions
    @Query("SELECT * FROM task_executions WHERE taskId = :taskId ORDER BY startedAt DESC")
    fun getExecutionsForTaskFlow(taskId: Int): Flow<List<TaskExecution>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(execution: TaskExecution): Long

    @Update
    suspend fun updateExecution(execution: TaskExecution)

    @Query("DELETE FROM task_executions")
    suspend fun clearAllExecutions()

    // Task Dependencies
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDependency(dep: TaskDependency)

    @Query("SELECT dependsOnTaskId FROM task_dependencies WHERE taskId = :taskId")
    suspend fun getDependenciesForTask(taskId: Int): List<Int>

    @Query("SELECT taskId FROM task_dependencies WHERE dependsOnTaskId = :dependsOnTaskId")
    suspend fun getDependentTasks(dependsOnTaskId: Int): List<Int>

    @Query("DELETE FROM task_dependencies")
    suspend fun clearAllDependencies()
}
