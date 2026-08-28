package com.example.aza.strategy

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 10-Step Canonical Lifecycle Enum for Lumina Trade Verification & Execution.
 */
enum class TradeLifecycleStep(val stepNumber: Int, val title: String, val shortDesc: String) {
    STEP_1_DETECT_SIGNAL(1, "Detect a signal", "Strategy / ML signal generated with entry, SL, TP"),
    STEP_2_PLACE_ORDER_BINANCE(2, "Place order on Binance", "Signed POST /api/v3/order dispatched with HMAC-SHA256"),
    STEP_3_CONFIRM_ORDER_APPEARS(3, "Confirm order appears on Binance", "Binance returns valid orderId and HTTP 200"),
    STEP_4_LET_IT_FILL(4, "Let it fill", "Order status verified FILLED with executed quantity"),
    STEP_5_VERIFY_POSITION_REGISTERED(5, "Verify position is registered", "Position saved to Room DB as OPEN and tracked in memory"),
    STEP_6_CLOSE_POSITION_AI_OR_MANUAL(6, "Close position (AI or manual)", "Triggered by AI TP/SL/Harvest or Manual Sell button"),
    STEP_7_CONFIRM_SELL_APPEARS(7, "Confirm sell appears on Binance", "Signed SELL order confirmed executed with orderId on Binance"),
    STEP_8_CONFIRM_ASSET_DISAPPEARS(8, "Confirm asset disappears from holdings", "Holdings refreshed & position status set to CLOSED"),
    STEP_9_CONFIRM_USDT_RELEASED(9, "Confirm USDT is released", "Spot USDT balance credited with trade proceeds + net profit"),
    STEP_10_RECORD_COMPLETED_TRADE(10, "Confirm Lumina records completed trade", "Position review logged with exact PnL, fees, and AI reflections")
}

enum class LifecycleStepStatus {
    PENDING,
    IN_PROGRESS,
    VERIFIED_SUCCESS,
    FAILED,
    SKIPPED
}

data class LifecycleStepRecord(
    val step: TradeLifecycleStep,
    val status: LifecycleStepStatus = LifecycleStepStatus.PENDING,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val dataPayload: Map<String, String> = emptyMap()
)

data class Trade10StepAudit(
    val auditId: String,
    val symbol: String,
    val side: String = "BUY",
    val allocatedUsdt: Double = 10.50,
    val currentStepIndex: Int = 0,
    val steps: List<LifecycleStepRecord> = TradeLifecycleStep.values().map { 
        LifecycleStepRecord(step = it, status = LifecycleStepStatus.PENDING, details = "Awaiting trigger") 
    },
    val isCompleted: Boolean = false,
    val entryPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val quantity: Double = 0.0,
    val grossPnl: Double = 0.0,
    val netPnl: Double = 0.0,
    val usdtBalanceBefore: Double = 0.0,
    val usdtBalanceAfter: Double = 0.0,
    val buyOrderId: String? = null,
    val sellOrderId: String? = null,
    val closeReason: String? = null,
    val executionMode: String = "LIVE_VERIFIED_BINANCE",
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

object LuminaTradeLifecycle10StepEngine {
    private const val TAG = "LuminaLifecycle10Step"

    private val _activeAudits = MutableStateFlow<List<Trade10StepAudit>>(emptyList())
    val activeAudits: StateFlow<List<Trade10StepAudit>> = _activeAudits.asStateFlow()

    private val _latestCompletedAudit = MutableStateFlow<Trade10StepAudit?>(null)
    val latestCompletedAudit: StateFlow<Trade10StepAudit?> = _latestCompletedAudit.asStateFlow()

    private val _isCycleRunning = MutableStateFlow(false)
    val isCycleRunning: StateFlow<Boolean> = _isCycleRunning.asStateFlow()

    /**
     * Start a new 10-step audit for an initiated trade.
     */
    fun startNewAudit(
        symbol: String,
        allocatedUsdt: Double,
        currentUsdtBalance: Double,
        executionMode: String = "LIVE_VERIFIED_BINANCE"
    ): Trade10StepAudit {
        val auditId = "AZA_10STEP_" + System.currentTimeMillis().toString().takeLast(8)
        val initialAudit = Trade10StepAudit(
            auditId = auditId,
            symbol = symbol,
            allocatedUsdt = allocatedUsdt,
            usdtBalanceBefore = currentUsdtBalance,
            executionMode = executionMode
        )
        val currentList = _activeAudits.value.toMutableList()
        currentList.add(0, initialAudit)
        _activeAudits.value = currentList.take(20)
        return initialAudit
    }

    /**
     * Update a specific step in an active audit.
     */
    fun updateStep(
        auditId: String,
        step: TradeLifecycleStep,
        status: LifecycleStepStatus,
        details: String,
        data: Map<String, String> = emptyMap(),
        entryPrice: Double? = null,
        exitPrice: Double? = null,
        quantity: Double? = null,
        netPnl: Double? = null,
        buyOrderId: String? = null,
        sellOrderId: String? = null,
        usdtBalanceAfter: Double? = null
    ): Trade10StepAudit? {
        val currentList = _activeAudits.value.toMutableList()
        val index = currentList.indexOfFirst { it.auditId == auditId }
        if (index == -1) return null

        val current = currentList[index]
        val updatedSteps = current.steps.map { s ->
            if (s.step == step) {
                s.copy(
                    status = status,
                    details = details,
                    timestamp = System.currentTimeMillis(),
                    dataPayload = data
                )
            } else s
        }

        val allSuccess = updatedSteps.all { it.status == LifecycleStepStatus.VERIFIED_SUCCESS }
        val updated = current.copy(
            steps = updatedSteps,
            currentStepIndex = step.stepNumber - 1,
            isCompleted = allSuccess,
            entryPrice = entryPrice ?: current.entryPrice,
            exitPrice = exitPrice ?: current.exitPrice,
            quantity = quantity ?: current.quantity,
            netPnl = netPnl ?: current.netPnl,
            buyOrderId = buyOrderId ?: current.buyOrderId,
            sellOrderId = sellOrderId ?: current.sellOrderId,
            usdtBalanceAfter = usdtBalanceAfter ?: current.usdtBalanceAfter,
            completedAt = if (allSuccess) System.currentTimeMillis() else current.completedAt
        )

        currentList[index] = updated
        _activeAudits.value = currentList

        if (allSuccess) {
            _latestCompletedAudit.value = updated
            Log.i(TAG, "✓ 10-STEP LIFECYCLE AUDIT FULLY VERIFIED & COMPLETED for ${updated.symbol} (Audit #${updated.auditId})")
        }

        return updated
    }

    fun setCycleRunning(running: Boolean) {
        _isCycleRunning.value = running
    }
}
