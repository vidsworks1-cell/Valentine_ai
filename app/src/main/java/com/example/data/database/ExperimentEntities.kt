package com.example.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "experiments")
data class Experiment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val strategyName: String,
    val market: String,
    val timeframe: String,
    val riskLevel: String, // "Low", "Medium", "High"
    val isActive: Boolean = true,
    val initialBalance: Double = 10000.0,
    val currentBalance: Double = 10000.0,
    val dailyReturn: Double = 0.0, // in percentage
    val weeklyReturn: Double = 0.0,
    val monthlyReturn: Double = 0.0,
    val winRate: Double = 0.0,
    val profitFactor: Double = 1.0,
    val sharpeRatio: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val averageTradeDuration: Double = 0.0, // in minutes
    val totalSimulatedFees: Double = 0.0,
    val totalSimulatedSlippage: Double = 0.0,
    val netProfit: Double = 0.0,
    val riskAdjustedReturn: Double = 0.0, // Sharpe or return/risk ratio
    val stabilityScore: Double = 0.0, // Measure of equity curve smoothness (0-100)
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "experiment_trades")
data class ExperimentTrade(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val experimentId: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val symbol: String,
    val direction: String, // "BUY" or "SELL"
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Double,
    val profitLoss: Double,
    val fees: Double,
    val slippage: Double,
    val durationMin: Long,
    val entryReason: String,
    val exitReason: String,
    val confidence: Double,
    val marketSnapshot: String, // Descriptive market status at trade time
    val alternativeAction: String // Retrospective improvement recommendation
)

@Dao
interface ExperimentDao {
    @Query("SELECT * FROM experiments ORDER BY createdAt DESC")
    fun getAllExperimentsFlow(): Flow<List<Experiment>>

    @Query("SELECT * FROM experiments WHERE id = :id LIMIT 1")
    suspend fun getExperimentByIdDirect(id: Int): Experiment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperiment(experiment: Experiment): Long

    @Update
    suspend fun updateExperiment(experiment: Experiment)

    @Query("SELECT * FROM experiment_trades WHERE experimentId = :experimentId ORDER BY timestamp DESC")
    fun getTradesForExperimentFlow(experimentId: Int): Flow<List<ExperimentTrade>>

    @Query("SELECT * FROM experiment_trades WHERE experimentId = :experimentId ORDER BY timestamp DESC")
    suspend fun getTradesForExperimentDirect(experimentId: Int): List<ExperimentTrade>

    @Query("SELECT * FROM experiment_trades ORDER BY timestamp DESC")
    fun getAllExperimentTradesFlow(): Flow<List<ExperimentTrade>>

    @Query("SELECT * FROM experiment_trades ORDER BY timestamp DESC")
    suspend fun getAllExperimentTradesDirect(): List<ExperimentTrade>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperimentTrade(trade: ExperimentTrade): Long

    @Query("DELETE FROM experiments")
    suspend fun clearAllExperiments()

    @Query("DELETE FROM experiment_trades")
    suspend fun clearAllExperimentTrades()
}
