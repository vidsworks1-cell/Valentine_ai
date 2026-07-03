package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "market_opportunities")
data class OpportunityEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Double,
    val strategy: String,
    val risk: Double,
    val expectedReward: Double,
    val currentRegime: String,
    val status: String // OBSERVING, WAITING_CONFIRMATION, READY, EXPIRED
)

@Entity(tableName = "trade_proposals")
data class TradeProposalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val timestamp: Long = System.currentTimeMillis(),
    val side: String, // BUY, SELL
    val entry: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val positionSize: Double,
    val expectedRiskReward: Double,
    val confidence: Double,
    val supportingEvidence: String,
    val knowledgeRulesMatched: String,
    val rejectedAlternatives: String,
    val expectedDrawdown: Double,
    val exposure: Double,
    val status: String // PENDING, READY, EXPIRED
)

@Dao
interface MarketIntelligenceDao {
    @Query("SELECT * FROM market_opportunities ORDER BY timestamp DESC")
    fun getAllOpportunitiesFlow(): Flow<List<OpportunityEntity>>

    @Query("SELECT * FROM market_opportunities ORDER BY timestamp DESC")
    suspend fun getAllOpportunitiesDirect(): List<OpportunityEntity>

    @Query("SELECT * FROM market_opportunities WHERE id = :id LIMIT 1")
    suspend fun getOpportunityById(id: Int): OpportunityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpportunity(opportunity: OpportunityEntity): Long

    @Update
    suspend fun updateOpportunity(opportunity: OpportunityEntity)

    @Query("DELETE FROM market_opportunities")
    suspend fun clearAllOpportunities()

    @Query("SELECT * FROM trade_proposals ORDER BY timestamp DESC")
    fun getAllProposalsFlow(): Flow<List<TradeProposalEntity>>

    @Query("SELECT * FROM trade_proposals ORDER BY timestamp DESC")
    suspend fun getAllProposalsDirect(): List<TradeProposalEntity>

    @Query("SELECT * FROM trade_proposals WHERE id = :id LIMIT 1")
    suspend fun getProposalById(id: Int): TradeProposalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProposal(proposal: TradeProposalEntity): Long

    @Update
    suspend fun updateProposal(proposal: TradeProposalEntity)

    @Query("DELETE FROM trade_proposals")
    suspend fun clearAllProposals()
}
