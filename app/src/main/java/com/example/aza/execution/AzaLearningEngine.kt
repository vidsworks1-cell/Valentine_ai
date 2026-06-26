package com.example.aza.execution

import com.example.data.database.AzaLearningRecord
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Learning Engine of the Autonomous Objective Loop.
 * Consolidates past execution outcomes and adjusts skill scores dynamically.
 */
class AzaLearningEngine(private val repository: AppRepository) {

    /**
     * Commits a new execution outcome to persistent database.
     */
    suspend fun recordOutcome(
        skillName: String,
        success: Boolean,
        latencyMs: Long,
        confidence: Float,
        retryCount: Int,
        userApproval: Boolean,
        outcomeQuality: Double
    ) {
        val record = AzaLearningRecord(
            skillName = skillName,
            success = success,
            failure = !success,
            latencyMs = latencyMs,
            confidence = confidence,
            retryCount = retryCount,
            userApproval = userApproval,
            outcomeQuality = outcomeQuality
        )
        repository.insertLearningRecord(record)
    }

    /**
     * Dynamically enhances or adjusts skill confidence scores based on historic success trends.
     * Future routing favors skills with high reliability and low failure tallies.
     */
    suspend fun calculateReliabilityMultiplier(skillName: String): Float {
        val records = repository.learningRecords.firstOrNull() ?: return 1.0f
        val skillRuns = records.filter { it.skillName == skillName }
        if (skillRuns.isEmpty()) return 1.0f // default neutral standard

        val successfulCount = skillRuns.count { it.success }
        val totalCount = skillRuns.size
        
        val rawRatio = successfulCount.toFloat() / totalCount.toFloat()
        // Boost factor: scale from 0.5x to 1.2x based on historic performance
        return (rawRatio * 0.7f + 0.5f).coerceIn(0.1f, 1.3f)
    }

    /**
     * Records a completed trade outcome and updates both the general learning records
     * and specific strategy performance statistics in the database.
     */
    suspend fun recordTradeOutcome(
        strategyName: String,
        market: String,
        profit: Double,
        latencyMs: Long
    ) {
        val isWin = profit > 0.0
        val record = AzaLearningRecord(
            skillName = "Strategy: $strategyName",
            success = isWin,
            failure = !isWin,
            latencyMs = latencyMs,
            confidence = if (isWin) 0.9f else 0.5f,
            retryCount = 0,
            userApproval = true,
            outcomeQuality = if (isWin) 1.0 else 0.0
        )
        repository.insertLearningRecord(record)

        val existing = repository.getStrategyByNameDirect(strategyName)
        if (existing != null) {
            val nextTotal = existing.totalPaperTrades + 1
            val nextWins = existing.winningPaperTrades + (if (isWin) 1 else 0)
            val nextWinRate = (nextWins.toDouble() / nextTotal) * 100.0
            val nextNetProfit = existing.paperNetProfit + profit
            val nextConfidence = (existing.confidence + (if (isWin) 0.05 else -0.05)).coerceIn(0.1, 1.0)
            val nextLatency = if (existing.avgLatencyMs == 0L) latencyMs else (existing.avgLatencyMs + latencyMs) / 2

            val updated = existing.copy(
                totalPaperTrades = nextTotal,
                winningPaperTrades = nextWins,
                paperWinRate = nextWinRate,
                paperNetProfit = nextNetProfit,
                confidence = nextConfidence,
                avgLatencyMs = nextLatency,
                currentSignal = if (isWin) "WIN - $market (+$${String.format("%.2f", profit)})" else "LOSS - $market ($${String.format("%.2f", profit)})"
            )
            repository.updateStrategy(updated)
        }
    }
}
