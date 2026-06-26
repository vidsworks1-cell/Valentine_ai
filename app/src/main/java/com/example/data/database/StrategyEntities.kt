package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "strategy_metadata")
data class StrategyEntity(
    @PrimaryKey val strategyName: String,
    val version: String,
    val description: String,
    val supportedMarkets: String,
    val riskProfile: String,
    val timeframe: String,
    val requiredIndicators: String,
    val isActive: Boolean = false,
    val totalPaperTrades: Int = 0,
    val winningPaperTrades: Int = 0,
    val paperWinRate: Double = 0.0,
    val paperNetProfit: Double = 0.0,
    val avgLatencyMs: Long = 0,
    val confidence: Double = 1.0,
    val ranking: Int = 0,
    val currentSignal: String = "HOLD",
    
    // Phase 5 Evaluation Metrics
    val totalTrades: Int = 0,
    val winRate: Double = 0.0,
    val averageProfit: Double = 0.0,
    val averageLoss: Double = 0.0,
    val profitFactor: Double = 1.0,
    val maxDrawdown: Double = 0.0,
    val sharpeRatio: Double = 0.0,
    val currentConfidenceScore: Double = 1.0,
    val designedRegimes: String = "Trending, High volatility",
    val bestConfigParams: String = ""
)

@Entity(tableName = "strategy_optimization_history")
data class StrategyOptimizationRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val strategyName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val paramConfig: String,
    val inSampleProfit: Double,
    val outOfSampleProfit: Double,
    val winRate: Double,
    val drawdown: Double
)

@Entity(tableName = "strategy_backtest_reports")
data class StrategyBacktestReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val strategyName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    val netProfit: Double,
    val averageProfit: Double,
    val averageLoss: Double,
    val profitFactor: Double,
    val maxDrawdown: Double,
    val averageTradeDuration: Long, // in minutes
    val reportDetails: String
)

@Entity(tableName = "trade_audit_logs")
data class TradeAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val strategyName: String,
    val marketRegime: String,
    val signal: String,
    val entryReason: String,
    val exitReason: String,
    val confidence: Double,
    val positionSize: Double,
    val expectedProfit: Double,
    val actualProfit: Double,
    val slippage: Double,
    val fees: Double,
    val executionLatencyMs: Long
)

@Entity(tableName = "exported_report_history")
data class ExportedReportHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val reportType: String,
    val strategyName: String,
    val market: String,
    val exportTimestamp: String,
    val formattedContent: String
)
