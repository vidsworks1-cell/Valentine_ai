package com.example.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "knowledge_base")
data class KnowledgeEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String, // "Pattern", "Risk", "Regime", "Strategy", "User Feedback"
    val description: String,
    val evidence: String,
    val confidenceScore: Double, // 0.0 to 1.0
    val dateCreated: Long = System.currentTimeMillis(),
    val timesConfirmed: Int = 1,
    val isConfirmed: Boolean = true
)

@Entity(tableName = "skill_library")
data class SkillEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String, // e.g. "Detect Trend", "Detect Range", "Detect Breakout", "Detect Reversal", "Identify Fake Breakout", "Position Sizing", "Risk Calculation", "Trade Exit Optimization"
    val description: String,
    val successRate: Double = 0.0,
    val failureRate: Double = 0.0,
    val confidence: Double = 0.5,
    val lastUpdated: Long = System.currentTimeMillis(),
    val associatedRegimes: String // e.g., "TRENDING, VOLATILE"
)

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_base ORDER BY dateCreated DESC")
    fun getAllKnowledgeFlow(): Flow<List<KnowledgeEntry>>

    @Query("SELECT * FROM knowledge_base ORDER BY dateCreated DESC")
    suspend fun getAllKnowledgeDirect(): List<KnowledgeEntry>

    @Query("SELECT * FROM knowledge_base WHERE id = :id LIMIT 1")
    suspend fun getKnowledgeById(id: Int): KnowledgeEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledge(entry: KnowledgeEntry): Long

    @Update
    suspend fun updateKnowledge(entry: KnowledgeEntry)

    @Query("DELETE FROM knowledge_base")
    suspend fun clearAllKnowledge()

    // Skills
    @Query("SELECT * FROM skill_library ORDER BY name ASC")
    fun getAllSkillsFlow(): Flow<List<SkillEntry>>

    @Query("SELECT * FROM skill_library ORDER BY name ASC")
    suspend fun getAllSkillsDirect(): List<SkillEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: SkillEntry): Long

    @Update
    suspend fun updateSkill(skill: SkillEntry)

    @Query("DELETE FROM skill_library")
    suspend fun clearAllSkills()
}
