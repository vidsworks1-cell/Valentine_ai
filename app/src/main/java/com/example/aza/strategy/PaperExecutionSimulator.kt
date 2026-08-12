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

    /**
     * Simulates Adaptive Limit Chasing order execution:
     * 1. Places limit order at 1.0x ATR inside order book spread to save slippage.
     * 2. If unfilled after 500ms, chases order by 0.25x ATR or auto-converts to market order if conviction >= 85.0.
     */
    fun simulateAdaptiveLimitChaseOrder(
        symbol: String,
        isBuy: Boolean,
        targetSize: Double,
        signalPrice: Double,
        atr: Double,
        convictionScore: Double,
        exchange: String = "Binance Direct API",
        strategyId: String = "AdaptiveLimitChase",
        marketRegime: MarketRegime = MarketRegime.HIGH_VOLATILITY
    ): LimitChaseOrderResult {
        // 1. Initial Limit Order placed 1.0x ATR inside the spread
        val spreadOffset = if (atr > 0.0) (atr * 0.05).coerceAtMost(signalPrice * 0.002) else signalPrice * 0.0005
        val initialLimitPrice = if (isBuy) signalPrice - spreadOffset else signalPrice + spreadOffset
        
        // 2. Chase logic simulation
        var currentLimitPrice = initialLimitPrice
        var elapsedMs = 0L
        var chasesCount = 0
        var isConvertedToMarket = false
        
        // Check if filled inside 500ms
        val fillProbabilityInside500ms = 0.65
        if (random.nextDouble() < fillProbabilityInside500ms) {
            elapsedMs = baseLatencyMs + random.nextInt(300) // filled fast inside spread
        } else {
            // Unfilled after 500ms -> evaluate chase vs auto-convert
            elapsedMs = 500L + random.nextInt(200)
            if (convictionScore >= 85.0) {
                // Auto-convert to market order due to high conviction
                isConvertedToMarket = true
                currentLimitPrice = signalPrice // market price
            } else {
                // Chase by 0.25x ATR inside order book spread
                chasesCount = 1
                val chaseOffset = if (atr > 0.0) (atr * 0.25).coerceAtMost(signalPrice * 0.005) else signalPrice * 0.001
                currentLimitPrice = if (isBuy) currentLimitPrice + chaseOffset else currentLimitPrice - chaseOffset
            }
        }
        
        val baseResult = simulateMarketOrder(
            symbol = symbol,
            isBuy = isBuy,
            targetSize = targetSize,
            limitOrSignalPrice = currentLimitPrice,
            exchange = exchange,
            strategyId = strategyId,
            marketRegime = marketRegime
        )
        
        val savedSlippageBps = ((kotlin.math.abs(signalPrice - baseResult.averageExecutionPrice) / signalPrice) * 10000.0)
        
        return LimitChaseOrderResult(
            initialLimitPrice = initialLimitPrice,
            finalExecutedPrice = baseResult.averageExecutionPrice,
            fillTimeMs = elapsedMs,
            chasesCount = chasesCount,
            isAutoConvertedToMarket = isConvertedToMarket,
            savedSlippageBps = savedSlippageBps,
            executionResult = baseResult.copy(latencyMs = elapsedMs)
        )
    }
}

data class LimitChaseOrderResult(
    val initialLimitPrice: Double,
    val finalExecutedPrice: Double,
    val fillTimeMs: Long,
    val chasesCount: Int,
    val isAutoConvertedToMarket: Boolean,
    val savedSlippageBps: Double,
    val executionResult: ExecutionResult
)
