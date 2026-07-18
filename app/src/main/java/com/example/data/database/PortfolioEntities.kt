package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "binance_positions")
data class BinancePositionEntity(
    @PrimaryKey val symbol: String, // e.g., "BTCUSDT"
    val entryPrice: Double,
    val currentPrice: Double,
    val amountOwned: Double,
    val realizedPnL: Double = 0.0,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val stopLossPrice: Double = 0.0,
    val takeProfitPrice: Double = 0.0,
    val status: String = "OPEN", // "OPEN", "CLOSED"
    
    // Mission & Position Linking
    val missionId: Int? = null,
    val strategyName: String = "AZA_STRATEGY",
    val knowledgeRuleId: Int? = null,
    val approvalDecisionId: Int? = null,
    val proposalId: Int? = null
)

@Entity(tableName = "position_reviews")
data class PositionReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val size: Double,
    val realizedPnL: Double,
    val holdingDurationMinutes: Double,
    val entryQuality: String, // e.g., "EXCELLENT", "GOOD", "FOLLOWER"
    val exitQuality: String, // e.g., "EXCELLENT", "GOOD", "SL_HIT", "TP_HIT"
    val strategyPerformance: String,
    val executionQuality: String,
    val slippage: Double,
    val fees: Double,
    val marketRegime: String,
    val lessonsLearned: String,
    val suggestedImprovements: String,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Links for Traceability
    val missionId: Int? = null,
    val strategyName: String = "AZA_STRATEGY",
    val proposalId: Int? = null,

    // Additional requested fields
    val entryTime: Long = System.currentTimeMillis() - 3600000,
    val side: String = "BUY",
    val stopLossPrice: Double = 0.0,
    val takeProfitPrice: Double = 0.0,
    val confidenceScore: Double = 1.0,
    val entryReason: String = "Algorithmic confirmation of breakout trend."
)

@Entity(tableName = "position_alerts")
data class PositionAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "SL_APPROACHING", "TP_APPROACHING", "LARGE_LOSS", "LARGE_GAIN", "VOLATILITY", "CONNECTION_LOSS", "SYNC_FAILURE"
    val symbol: String,
    val message: String,
    val isRead: Boolean = false
)

@Dao
interface PortfolioPositionDao {
    @Query("SELECT * FROM binance_positions")
    fun getAllPositionsFlow(): Flow<List<BinancePositionEntity>>

    @Query("SELECT * FROM binance_positions")
    suspend fun getAllPositionsDirect(): List<BinancePositionEntity>

    @Query("SELECT * FROM binance_positions WHERE symbol = :symbol LIMIT 1")
    suspend fun getPositionBySymbol(symbol: String): BinancePositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosition(position: BinancePositionEntity)

    @Update
    suspend fun updatePosition(position: BinancePositionEntity)

    @Query("DELETE FROM binance_positions WHERE symbol = :symbol")
    suspend fun deletePosition(symbol: String)

    @Query("DELETE FROM binance_positions")
    suspend fun clearAllPositions()

    // Position Reviews
    @Query("SELECT * FROM position_reviews ORDER BY timestamp DESC")
    fun getAllReviewsFlow(): Flow<List<PositionReviewEntity>>

    @Query("SELECT * FROM position_reviews ORDER BY timestamp DESC")
    suspend fun getAllReviewsDirect(): List<PositionReviewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: PositionReviewEntity): Long

    @Query("DELETE FROM position_reviews")
    suspend fun clearAllReviews()

    // Alerts
    @Query("SELECT * FROM position_alerts ORDER BY timestamp DESC")
    fun getAllAlertsFlow(): Flow<List<PositionAlertEntity>>

    @Query("SELECT * FROM position_alerts ORDER BY timestamp DESC")
    suspend fun getAllAlertsDirect(): List<PositionAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: PositionAlertEntity): Long

    @Query("UPDATE position_alerts SET isRead = 1 WHERE id = :id")
    suspend fun markAlertAsRead(id: Int)

    @Query("DELETE FROM position_alerts")
    suspend fun clearAllAlerts()
}
