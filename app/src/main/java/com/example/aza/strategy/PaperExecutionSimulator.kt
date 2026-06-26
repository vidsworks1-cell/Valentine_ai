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
        limitOrSignalPrice: Double
    ): ExecutionResult {
        // 1. Latency Simulation
        val latency = baseLatencyMs + random.nextInt(40) // 20ms to 60ms

        // 2. Slippage Simulation
        // For a BUY order, slippage increases execution price (worse entry).
        // For a SELL order, slippage decreases execution price (worse exit).
        val slippageMultiplier = 1.0 + (random.nextDouble() * simulateSlippagePercent * (if (isBuy) 1.0 else -1.0))
        val executedPrice = limitOrSignalPrice * slippageMultiplier
        val slippagePaid = kotlin.math.abs(executedPrice - limitOrSignalPrice) * targetSize

        // 3. Partial Fill Simulation (Occurs 8% of the time)
        val isPartial = random.nextDouble() < 0.08
        val fillRatio = if (isPartial) 0.70 + (random.nextDouble() * 0.25) else 1.0 // fill between 70% and 95%
        val filledSize = targetSize * fillRatio

        // 4. Trading Fee Simulation
        val notionalValue = filledSize * executedPrice
        val fees = notionalValue * simulateFeePercent

        return ExecutionResult(
            filledSize = filledSize,
            averageExecutionPrice = executedPrice,
            slippagePaid = slippagePaid,
            feesPaid = fees,
            latencyMs = latency,
            isPartialFill = isPartial
        )
    }

    /**
     * Simulates triggering Stop Loss (SL) or Take Profit (TP).
     * SL typically has higher slippage due to market distress.
     */
    fun simulateTriggerOrder(
        symbol: String,
        isStopLoss: Boolean,
        positionSize: Double,
        triggerPrice: Double
    ): ExecutionResult {
        val latency = baseLatencyMs + random.nextInt(60) // 20ms to 80ms
        
        // SL has more slippage (gapping), TP has less/positive slippage
        val slippageFactor = if (isStopLoss) {
            simulateSlippagePercent * 1.8 // SL slippage is higher
        } else {
            simulateSlippagePercent * 0.5 // TP slippage is lower
        }

        // On position exit (which is a SELL if we were long):
        // SL slippage reduces executed price (worse sell exit price).
        // TP slippage is generally closer, but still slightly worse on average in fast markets.
        val executedPrice = triggerPrice * (1.0 - (random.nextDouble() * slippageFactor))
        val slippagePaid = kotlin.math.abs(executedPrice - triggerPrice) * positionSize
        val fees = positionSize * executedPrice * simulateFeePercent

        return ExecutionResult(
            filledSize = positionSize,
            averageExecutionPrice = executedPrice,
            slippagePaid = slippagePaid,
            feesPaid = fees,
            latencyMs = latency,
            isPartialFill = false // Exits are fully filled eventually
        )
    }
}
