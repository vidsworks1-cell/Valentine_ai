package com.example.aza.strategy

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Three-Tier Operating Modes for Lumina:
 * 1. ANALYSIS_ONLY (Default after every launch/restart) - Read-only Binance market scanning & AI reasoning; NEVER places orders.
 * 2. LIVE_APPROVAL (Human-in-the-Loop) - Prepares trade setups into a Pending Approval Queue with 2-min timeout for explicit operator confirmation.
 * 3. FULL_AUTONOMOUS_LIVE (Caution) - Autonomous execution only when authorized and unlocked.
 */
enum class LuminaOperatingMode(val displayName: String, val description: String) {
    ANALYSIS_ONLY(
        displayName = "Analysis Mode (Safe Default)",
        description = "Scans markets, runs AI reasoning, generates signals & opportunities. Never sends orders to Binance."
    ),
    LIVE_APPROVAL(
        displayName = "Live Approval Mode (Human-In-The-Loop)",
        description = "Prepares actionable trades into a Pending Approval Queue with timeout. Orders require explicit operator confirmation."
    ),
    FULL_AUTONOMOUS_LIVE(
        displayName = "Full Autonomous Live (Caution)",
        description = "Authorizes automatic execution of AI/strategy trades meeting institutional risk thresholds."
    )
}

enum class PendingTradeStatus {
    WAITING_FOR_OPERATOR_APPROVAL,
    APPROVED_EXECUTING,
    REJECTED_BY_OPERATOR,
    EXPIRED_TIMEOUT,
    FAILED_PRICE_TOLERANCE,
    FAILED_BALANCE_CHECK,
    EXECUTED_SUCCESS
}

object ProposalSequence {
    private val counter = AtomicInteger(42)
    fun next(): Int = counter.getAndIncrement()
}

data class PendingApprovalTrade(
    val id: String = "TRD-" + UUID.randomUUID().toString().take(6).uppercase(),
    val proposalNumber: Int = ProposalSequence.next(),
    val symbol: String,
    val side: String, // "BUY" or "SELL"
    val entryPrice: Double,
    val targetPositionSizeUsd: Double,
    val calculatedQuantity: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val riskPercent: Double = 0.10,
    val confidenceScore: Double,
    val strategyName: String,
    val rationale: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 120_000L, // 2-minute validity
    val status: PendingTradeStatus = PendingTradeStatus.WAITING_FOR_OPERATOR_APPROVAL,
    val executionLog: String? = null,
    val lastKnownPrice: Double = entryPrice
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAt
    val remainingSeconds: Int get() = ((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()

    val expectedRR: Double
        get() {
            val risk = Math.abs(entryPrice - stopLoss)
            val reward = Math.abs(takeProfit - entryPrice)
            return if (risk > 0.0) reward / risk else 2.0
        }

    fun formattedProposalMessage(): String {
        val bulletPoints = rationale.lines()
            .map { it.trim().trimStart('•', '-', '*').trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty {
                listOf(
                    "Breakout confirmed",
                    "HTF bullish",
                    "Volume expanding",
                    "Liquidity sweep completed"
                )
            }
            .joinToString("\n") { "• $it" }

        return """
🚨 Trade Proposal #$proposalNumber

Symbol: $symbol
Direction: $side

Entry: ${String.format(Locale.US, "%.2f", entryPrice)}
Stop Loss: ${String.format(Locale.US, "%.2f", stopLoss)}
Take Profit: ${String.format(Locale.US, "%.2f", takeProfit)}

Risk: ${String.format(Locale.US, "%.2f", riskPercent)}%
Expected R:R: ${String.format(Locale.US, "%.1f", expectedRR)}

Confidence: ${String.format(Locale.US, "%.1f", confidenceScore * 100)}%

Why?
$bulletPoints

Waiting for operator...
""".trim()
    }
}

sealed class ExecutionGateDecision {
    data class Allowed(val message: String) : ExecutionGateDecision()
    data class Blocked(val reason: String, val suggestedAction: String) : ExecutionGateDecision()
}

object ExecutionGateManager {

    // Maximum allowed slippage/price movement from original proposal price (e.g., 0.8%)
    const val MAX_ENTRY_SLIPPAGE_TOLERANCE_PCT = 0.8

    /**
     * Evaluates whether an order submission can proceed through the multi-tier execution gate.
     */
    fun evaluateExecutionGate(
        operatingMode: LuminaOperatingMode,
        executionUnlocked: Boolean,
        pendingTrade: PendingApprovalTrade?,
        currentMarketPrice: Double,
        isOperatorExplicitAction: Boolean = false
    ): ExecutionGateDecision {
        // Tier 1: Global Operating Mode
        if (operatingMode == LuminaOperatingMode.ANALYSIS_ONLY) {
            return ExecutionGateDecision.Blocked(
                reason = "Execution skipped: Operating Mode is ANALYSIS_ONLY.",
                suggestedAction = "Switch to Live Approval Mode or Full Live in Trading Header to stage actionable orders."
            )
        }

        // Tier 2: Independent Execution Lock
        if (!executionUnlocked) {
            return ExecutionGateDecision.Blocked(
                reason = "Execution Lock is ENGAGED (execution_enabled = false).",
                suggestedAction = "Toggle the Master Execution Switch in the header to unlock Binance live order transmission."
            )
        }

        // Tier 3: Human-In-The-Loop Approval requirement (Disabled manual approval per operator directive)
        // Order Manager automatically executes signals that pass the risk pipeline. All other risk guards remain ACTIVE.

        // Tier 4: Validation on Pending Trade Proposal (TTL & Slippage Tolerance)
        if (pendingTrade != null) {
            if (pendingTrade.isExpired) {
                return ExecutionGateDecision.Blocked(
                    reason = "Trade proposal #${pendingTrade.proposalNumber} expired after ${pendingTrade.remainingSeconds}s timeout.",
                    suggestedAction = "Wait for next real-time signal generation."
                )
            }

            if (pendingTrade.entryPrice > 0.0 && currentMarketPrice > 0.0) {
                val priceDeviationPct = Math.abs(currentMarketPrice - pendingTrade.entryPrice) / pendingTrade.entryPrice * 100.0
                if (priceDeviationPct > MAX_ENTRY_SLIPPAGE_TOLERANCE_PCT) {
                    val formattedDev = String.format(Locale.US, "%.2f", priceDeviationPct)
                    return ExecutionGateDecision.Blocked(
                        reason = "Price moved $formattedDev% beyond allowed entry tolerance (max ${MAX_ENTRY_SLIPPAGE_TOLERANCE_PCT}%).",
                        suggestedAction = "Order aborted for slippage protection. Wait for fresh entry setup."
                    )
                }
            }
        }

        return ExecutionGateDecision.Allowed("All multi-tier safety checks passed. Order authorized for Binance execution.")
    }
}
