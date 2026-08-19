package com.example.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "counterfactual_evaluations")
data class CounterfactualEvaluationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tradeId: String,
    val symbol: String,
    val strategyName: String,
    val marketRegime: String,
    val executedSide: String, // "BUY", "SELL"
    val executedEntryPrice: Double,
    val executedExitPrice: Double,
    val executedPnL: Double,
    val executedPnLPct: Double,
    val executedRiskPct: Double, // e.g. 1.5%
    val executedOutcome: String, // "WIN", "LOSS", "BREAKEVEN"
    
    // Scenario 1: Skipped
    val skippedDeltaPnL: Double,
    val skippedOutcome: String, // "AVOIDED_LOSS", "MISSED_PROFIT", "NEUTRAL"
    val skippedReasoning: String,
    
    // Scenario 2: Entered 1 Candle Later
    val delayed1CandleEntryPrice: Double,
    val delayed1CandleExitPrice: Double,
    val delayed1CandlePnL: Double,
    val delayed1CandleDeltaPnL: Double,
    val delayed1CandleOutcome: String, // "BETTER_ENTRY", "WORSE_ENTRY", "MISSED_TRIGGER"
    val delayed1CandleReasoning: String,
    
    // Scenario 3: Lower Risk (0.5% vs executed 1.5%)
    val lowerRiskPositionSize: Double,
    val lowerRiskPnL: Double,
    val lowerRiskDeltaPnL: Double,
    val lowerRiskOutcome: String, // "REDUCED_DRAWDOWN", "LOWER_RETURN", "OPTIMAL_SHARPE"
    val lowerRiskReasoning: String,
    
    // Scenario 4: Earlier TP1 (1.0R / partial profit)
    val earlierTp1PnL: Double,
    val earlierTp1DeltaPnL: Double,
    val earlierTp1Outcome: String, // "CAPTURED_REVERSAL_GAIN", "CAPPED_RUNNER_PROFIT", "NEUTRAL"
    val earlierTp1Reasoning: String,
    
    // Scenario 5: Wider Stop Loss (+0.5 ATR)
    val widerSlPrice: Double,
    val widerSlPnL: Double,
    val widerSlDeltaPnL: Double,
    val widerSlOutcome: String, // "SURVIVED_NOISE_WON", "INCREASED_LOSS", "NEUTRAL"
    val widerSlReasoning: String,
    
    // Scenario 6: Alternative Strategy Selected
    val altStrategyName: String,
    val altStrategyPnL: Double,
    val altStrategyDeltaPnL: Double,
    val altStrategyOutcome: String, // "BETTER_REGIME_FIT", "WORSE_FIT", "NEUTRAL"
    val altStrategyReasoning: String,
    
    // Overall Aggregate Evaluation
    val bestAlternativeScenario: String, // "EXECUTED_OPTIMAL", "SKIPPED", "DELAYED_1_CANDLE", "LOWER_RISK", "EARLIER_TP1", "WIDER_SL", "ALT_STRATEGY"
    val regretScore: Double, // max(0.0, bestAlternativePnL - executedPnL)
    val wasExecutedOptimal: Boolean,
    val aiPolicyLesson: String
)

@Dao
interface CounterfactualDao {
    @Query("SELECT * FROM counterfactual_evaluations ORDER BY timestamp DESC")
    fun getAllEvaluationsFlow(): Flow<List<CounterfactualEvaluationEntity>>

    @Query("SELECT * FROM counterfactual_evaluations ORDER BY timestamp DESC")
    suspend fun getAllEvaluationsDirect(): List<CounterfactualEvaluationEntity>

    @Query("SELECT * FROM counterfactual_evaluations WHERE tradeId = :tradeId LIMIT 1")
    suspend fun getEvaluationByTradeId(tradeId: String): CounterfactualEvaluationEntity?

    @Query("SELECT * FROM counterfactual_evaluations WHERE symbol = :symbol ORDER BY timestamp DESC")
    suspend fun getEvaluationsBySymbol(symbol: String): List<CounterfactualEvaluationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvaluation(entity: CounterfactualEvaluationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvaluations(list: List<CounterfactualEvaluationEntity>)

    @Query("DELETE FROM counterfactual_evaluations")
    suspend fun clearAll()
}
