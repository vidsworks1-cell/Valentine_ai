package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "executive_decisions")
data class ExecutiveDecisionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val participatingAgents: String,
    val confidenceScores: String,
    val chosenAction: String,
    val rejectedAlternatives: String,
    val outcome: String,
    val lessonsLearned: String
)

@Dao
interface ExecutiveDecisionDao {
    @Query("SELECT * FROM executive_decisions ORDER BY timestamp DESC")
    fun getAllDecisionsFlow(): Flow<List<ExecutiveDecisionRecord>>

    @Insert
    suspend fun insertDecision(decision: ExecutiveDecisionRecord): Long

    @Query("DELETE FROM executive_decisions")
    suspend fun clearAllDecisions()
}
