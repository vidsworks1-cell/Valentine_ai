package com.example.aza.strategy

import java.util.Random

data class ExecutionResult(
    val filledSize: Double,
    val averageExecutionPrice: Double,
    val slippagePaid: Double,
    val feesPaid: Double,
    val latencyMs: Long,
    val isPartialFill: Boolean
)

object PaperExecutionSimulator {

    private val random = Random()

    // Configuration
    var simulateSlippagePercent = 0.001 // 0.1% base slippage
    var simulateFeePercent = 0.0006 // 0.06% maker/taker fee
    var baseLatencyMs = 20L

    /**
     * Simulates placing a market order (BUY or SELL) on paper.
     */
    fun simulateMarketOrder(
        symbol: String,
        isBuy: Boolean,
        targetSize: Double,
        limitOrSignalPrice: Double,
        exchange: String = "Binance Direct API",
        strategyId: String = "MomentumBreakout",
        marketRegime: MarketRegime = MarketRegime.TRENDING_UP
    ): ExecutionResult {
        val reqTime = System.currentTimeMillis()
        // 1. Latency Simulation
        val latency = baseLatencyMs + random.nextInt(40) // 20ms to 60ms

        // 2. Slippage Simulation
        val slippageMultiplier = 1.0 + (random.nextDouble() * simulateSlippagePercent * (if (isBuy) 1.0 else -1.0))
        val executedPrice = limitOrSignalPrice * slippageMultiplier
        val slippagePaid = kotlin.math.abs(executedPrice - limitOrSignalPrice) * targetSize
        val slippageBps = (kotlin.math.abs(executedPrice - limitOrSignalPrice) / limitOrSignalPrice) * 10000.0

        // 3. Partial Fill Simulation (Occurs 8% of the time)
        val isPartial = random.nextDouble() < 0.08
        val fillRatio = if (isPartial) 0.70 + (random.nextDouble() * 0.25) else 1.0 // fill between 70% and 95%
        val filledSize = targetSize * fillRatio

        // 4. Trading Fee Simulation
        val notionalValue = filledSize * executedPrice
        val fees = notionalValue * simulateFeePercent

        val result = ExecutionResult(
            filledSize = filledSize,
            averageExecutionPrice = executedPrice,
            slippagePaid = slippagePaid,
            feesPaid = fees,
            latencyMs = latency,
            isPartialFill = isPartial
        )

        // Record Telemetry
        ExecutionIntelligenceEngine.recordExecution(
            ExecutionTelemetryRecord(
                exchange = exchange,
                symbol = symbol,
                strategyId = strategyId,
                marketRegime = marketRegime,
                isEntry = true,
                requestedTimestampMs = reqTime,
                executedTimestampMs = reqTime + latency,
                targetSize = targetSize,
                filledSize = filledSize,
                limitOrSignalPrice = limitOrSignalPrice,
                executedPrice = executedPrice,
                slippageBps = slippageBps,
                spreadBps = 1.2,
                latencyMs = latency,
                orderQualityScore = if (isPartial) 78.0 else 96.0,
                fillQualityScore = if (isPartial) 80.0 else 98.0
            )
        )

        return result
    }

    /**
     * Simulates triggering Stop Loss (SL) or Take Profit (TP).
     * SL typically has higher slippage due to market distress.
     */
    fun simulateTriggerOrder(
        symbol: String,
        isStopLoss: Boolean,
        positionSize: Double,
        triggerPrice: Double,
        exchange: String = "Binance Direct API",
        strategyId: String = "MomentumBreakout",
        marketRegime: MarketRegime = MarketRegime.TRENDING_UP
    ): ExecutionResult {
        val reqTime = System.currentTimeMillis()
        val latency = baseLatencyMs + random.nextInt(60) // 20ms to 80ms
        
        // SL has more slippage (gapping), TP has less/positive slippage
        val slippageFactor = if (isStopLoss) {
            simulateSlippagePercent * 1.8 // SL slippage is higher
        } else {
            simulateSlippagePercent * 0.5 // TP slippage is lower
        }

        val executedPrice = triggerPrice * (1.0 - (random.nextDouble() * slippageFactor))
        val slippagePaid = kotlin.math.abs(executedPrice - triggerPrice) * positionSize
        val slippageBps = (kotlin.math.abs(executedPrice - triggerPrice) / triggerPrice) * 10000.0
        val fees = positionSize * executedPrice * simulateFeePercent

        val result = ExecutionResult(
            filledSize = positionSize,
            averageExecutionPrice = executedPrice,
            slippagePaid = slippagePaid,
            feesPaid = fees,
            latencyMs = latency,
            isPartialFill = false
        )

        ExecutionIntelligenceEngine.recordExecution(
            ExecutionTelemetryRecord(
                exchange = exchange,
                symbol = symbol,
                strategyId = strategyId,
                marketRegime = marketRegime,
                isEntry = false,
                requestedTimestampMs = reqTime,
                executedTimestampMs = reqTime + latency,
                targetSize = positionSize,
                filledSize = positionSize,
                limitOrSignalPrice = triggerPrice,
                executedPrice = executedPrice,
                slippageBps = slippageBps,
                spreadBps = 1.5,
                latencyMs = latency,
                orderQualityScore = if (isStopLoss) 82.0 else 94.0,
                fillQualityScore = if (isStopLoss) 85.0 else 96.0
            )
        )

        return result
    }
}
