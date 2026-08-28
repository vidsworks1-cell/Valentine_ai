package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trade_reviews_library")
data class TradeReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val tradeId: String,
    val symbol: String,
    val side: String, // BUY / SELL
    val strategyName: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val pnlUsd: Double,
    val pnlR: Double,
    val holdingDurationMinutes: Double,
    val timestamp: Long = System.currentTimeMillis(),
    
    // 4 Key Dimension Scores (0 - 100)
    val decisionQuality: Int,
    val entryTimingQuality: Int,
    val exitQuality: Int,
    val riskSizingQuality: Int,
    val overallScore: Int,
    
    // Structured Review Attributes
    val regimeSelection: String, // e.g. "Correct", "Suboptimal", "Misaligned"
    val regimeName: String, // e.g. "Volatile Breakout Trend", "Range-bound Chop"
    val confidenceCalibration: String, // e.g. "Accurately Calibrated", "Slightly optimistic", "Conservative"
    val confidenceScore: Double, // e.g. 0.85
    val missedOpportunity: String, // e.g. "Scale in after confirmation"
    val lessonLearned: String, // e.g. "Wait for 15-minute momentum confirmation in volatile BTC conditions."
    val actionablePolicy: String, // Rule for continuous improvement
    val category: String, // "ENTRY_TIMING", "RISK_SIZING", "REGIME_FILTER", "EXIT_DISCIPLINE", "POSITION_MANAGEMENT"

    // Self-Verifying Decision Quality Audit Attributes
    val decisionQualityTen: Double = 8.7, // e.g. 8.7/10
    val rootCause: String = "Execution timing", // "Execution timing", "Poor signal generation", "Market randomness", "Risk management"
    val auditReasonsList: String = "✓ Correct market regime\n✓ Correct strategy selection\n✓ Good confidence calibration\n✗ Stop placed too wide\n✗ Entry was 0.6 ATR late",
    val decisionQualityLearning: String = "Decision was excellent. Keep taking trades like this.",
    val currentPnlLearning: String = "Strategy lost.",
    val processVsOutcomeType: String = "GOOD_DECISION_LOSING_TRADE", // "GOOD_DECISION_LOSING_TRADE", "BAD_DECISION_WINNING_TRADE", "GOOD_DECISION_WINNING_TRADE", "BAD_DECISION_LOSING_TRADE"
    val isOperatorInitiated: Boolean = false,
    val operatorAction: String? = null
)

@Dao
interface TradeReviewDao {
    @Query("SELECT * FROM trade_reviews_library ORDER BY timestamp DESC")
    fun getAllReviewsFlow(): Flow<List<TradeReviewEntity>>

    @Query("SELECT * FROM trade_reviews_library ORDER BY timestamp DESC")
    suspend fun getAllReviewsDirect(): List<TradeReviewEntity>

    @Query("SELECT * FROM trade_reviews_library WHERE symbol = :symbol ORDER BY timestamp DESC")
    fun getReviewsForSymbolFlow(symbol: String): Flow<List<TradeReviewEntity>>

    @Query("SELECT * FROM trade_reviews_library WHERE category = :category ORDER BY timestamp DESC")
    fun getReviewsForCategoryFlow(category: String): Flow<List<TradeReviewEntity>>

    @Query("SELECT * FROM trade_reviews_library WHERE tradeId = :tradeId LIMIT 1")
    suspend fun getReviewByTradeId(tradeId: String): TradeReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: TradeReviewEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviews(reviews: List<TradeReviewEntity>)

    @Query("SELECT COUNT(*) FROM trade_reviews_library")
    suspend fun getReviewCount(): Int

    @Query("DELETE FROM trade_reviews_library")
    suspend fun clearAllReviews()
}
