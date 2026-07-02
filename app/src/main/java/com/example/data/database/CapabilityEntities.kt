package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "capability_states")
data class CapabilityStateEntity(
    @PrimaryKey val name: String,
    val description: String,
    val supportedActions: String, // Comma-separated list of actions
    val requiredPermissions: String, // Comma-separated list of permission names
    val isEnabled: Boolean = true,
    val status: String = "ONLINE", // ONLINE, OFFLINE
    val healthState: String = "HEALTHY", // HEALTHY, DEGRADED, FAILING
    val lastSuccessfulExecution: Long = 0,
    val errorCount: Int = 0,
    val successCount: Int = 0,
    val currentWorkload: Int = 0,
    val operatingMode: String = "MOCK", // MOCK, LIVE
    val authPolicy: String = "AUTONOMOUS", // AUTONOMOUS, APPROVAL_REQUIRED
    val apiKey: String = "",
    val apiSecret: String = "",
    val apiUsageCount: Int = 0,
    val rateLimitStatus: String = "100/100"
)

@Entity(tableName = "capability_audit_logs")
data class CapabilityAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val missionId: Int?,
    val capabilityUsed: String,
    val actionPerformed: String,
    val plannerDecision: String, // e.g. "APPROVED", "DENIED"
    val permissionLevel: String, // e.g. "READ", "WRITE", etc.
    val result: String,
    val durationMs: Long,
    val errors: String?
)

@Dao
interface CapabilityDao {
    @Query("SELECT * FROM capability_states")
    fun getAllCapabilityStatesFlow(): Flow<List<CapabilityStateEntity>>

    @Query("SELECT * FROM capability_states")
    suspend fun getAllCapabilityStates(): List<CapabilityStateEntity>

    @Query("SELECT * FROM capability_states WHERE name = :name LIMIT 1")
    suspend fun getCapabilityStateByName(name: String): CapabilityStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCapabilityState(state: CapabilityStateEntity)

    @Update
    suspend fun updateCapabilityState(state: CapabilityStateEntity)

    @Query("SELECT * FROM capability_audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogsFlow(): Flow<List<CapabilityAuditLog>>

    @Insert
    suspend fun insertAuditLog(log: CapabilityAuditLog): Long

    @Query("DELETE FROM capability_audit_logs")
    suspend fun clearAllAuditLogs()
}
