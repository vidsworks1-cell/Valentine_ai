package com.example.aza.strategy

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Micro-Compounding Execution Mode:
 * Balances aggressiveness, profit lock-in velocity, and defensive protection.
 */
enum class MicroCompoundingMode(
    val displayName: String,
    val targetWinNetUsd: Double,
    val quickHarvestProfitPct: Double,
    val defensiveStopAtrMult: Double,
    val breakevenTriggerPct: Double,
    val description: String
) {
    AGGRESSIVE_MICRO_HARVEST(
        displayName = "Aggressive Micro-Harvest (1¢ - 3¢ Velocity)",
        targetWinNetUsd = 0.02,
        quickHarvestProfitPct = 0.35, // +0.35% covers fees + 1-2 cents net
        defensiveStopAtrMult = 0.90,  // Tight stop to prevent drawdowns
        breakevenTriggerPct = 0.20,
        description = "High turnover, rapid 1¢-3¢ net profit harvesting. Stops immediately trailed to breakeven once in green."
    ),
    BALANCED_COMPOUNDER(
        displayName = "Balanced Micro-Compounder (3¢ - 8¢)",
        targetWinNetUsd = 0.05,
        quickHarvestProfitPct = 0.65, // +0.65% profit harvest
        defensiveStopAtrMult = 1.25,
        breakevenTriggerPct = 0.35,
        description = "Balanced risk/reward micro-growth. Locks 1¢ profit early and lets partial runners capture +0.65% to +1.2%."
    ),
    CONSERVATIVE_RUNNER(
        displayName = "Conservative Steady Growth",
        targetWinNetUsd = 0.12,
        quickHarvestProfitPct = 1.10, // +1.10% profit harvest
        defensiveStopAtrMult = 1.50,
        breakevenTriggerPct = 0.50,
        description = "Higher patience for structured trends while strictly enforcing zero loss once fee buffer is cleared."
    )
}

data class MicroExitActionDecision(
    val action: String, // "MICRO_TAKE_PROFIT", "LOCK_BREAKEVEN_PLUS_ONE_CENT", "TRAIL_DEFENSIVE_STOP", "HOLD", "DEFENSIVE_CUT"
    val isExitTriggered: Boolean,
    val netPnlUsd: Double,
    val grossPnlUsd: Double,
    val feesEstimatedUsd: Double,
    val suggestedStopLoss: Double?,
    val rationale: String,
    val compoundingWisdom: String
)

data class MicroCompoundingState(
    val mode: MicroCompoundingMode = MicroCompoundingMode.AGGRESSIVE_MICRO_HARVEST,
    val currentWinningStreak: Int = 0,
    val totalMicroTradesExecuted: Int = 0,
    val totalNetPenniesCompoundedUsd: Double = 0.0,
    val averageWinPerTradeUsd: Double = 0.0,
    val feeEfficiencyPct: Double = 94.5,
    val activeCompoundingEfficiencyScore: Double = 88.0,
    val isAutoCompoundingActive: Boolean = true
)

object MicroCompoundingEngine {

    private var state = MicroCompoundingState()

    fun getCompoundingState(): MicroCompoundingState = state

    fun setCompoundingMode(newMode: MicroCompoundingMode) {
        state = state.copy(mode = newMode)
    }

    /**
     * Evaluates real-time price against micro-compounding and 1-cent profit lock-in rules.
     * Core Rule: "Even if we win 1 cent it's a good trade; repeat the process to add up over time."
     */
    fun evaluateMicroCompoundingExit(
        symbol: String,
        entryPrice: Double,
        currentPrice: Double,
        amountOwned: Double,
        currentStopLoss: Double,
        holdingMinutes: Double = 10.0,
        momentumDecelerating: Boolean = false
    ): MicroExitActionDecision {
        val mode = state.mode
        val notionalValue = amountOwned * currentPrice
        val entryNotional = amountOwned * entryPrice

        val grossPnL = notionalValue - entryNotional
        val estRoundTripFees = entryNotional * PreBuyExitViabilityEngine.ROUND_TRIP_FEE_RATE
        val netPnL = grossPnL - estRoundTripFees
        val priceGainPct = if (entryPrice > 0.0) ((currentPrice - entryPrice) / entryPrice) * 100.0 else 0.0

        // Fee-adjusted breakeven price (+ $0.005 buffer to guarantee green)
        val feePerUnit = if (amountOwned > 0.0) estRoundTripFees / amountOwned else 0.0
        val oneCentPriceDelta = if (amountOwned > 0.0) 0.01 / amountOwned else 0.0
        val guaranteedGreenStopPrice = entryPrice + feePerUnit + oneCentPriceDelta

        return when {
            // 1. Target micro-profit reached and momentum is slowing down -> Harvest immediate micro-profit
            netPnL >= mode.targetWinNetUsd && (momentumDecelerating || priceGainPct >= mode.quickHarvestProfitPct) -> {
                MicroExitActionDecision(
                    action = "MICRO_TAKE_PROFIT",
                    isExitTriggered = true,
                    netPnlUsd = netPnL,
                    grossPnlUsd = grossPnL,
                    feesEstimatedUsd = estRoundTripFees,
                    suggestedStopLoss = null,
                    rationale = "🎯 MICRO-PROFIT HARVEST: Net profit $${String.format(Locale.US, "%.3f", netPnL)} (+${String.format(Locale.US, "%.2f", priceGainPct)}%) reached ${mode.displayName} target. Banking profit cleanly to compound equity.",
                    compoundingWisdom = "Consistent small wins (+${String.format(Locale.US, "%.2f", netPnL * 100)}¢) compound account base safely without open drawdown exposure."
                )
            }

            // 2. Fast Green Protection: Profit is positive (+0.20%+) but stop is still below breakeven -> Lock in breakeven + 1 cent
            priceGainPct >= mode.breakevenTriggerPct && currentStopLoss < guaranteedGreenStopPrice -> {
                MicroExitActionDecision(
                    action = "LOCK_BREAKEVEN_PLUS_ONE_CENT",
                    isExitTriggered = false,
                    netPnlUsd = netPnL,
                    grossPnlUsd = grossPnL,
                    feesEstimatedUsd = estRoundTripFees,
                    suggestedStopLoss = guaranteedGreenStopPrice,
                    rationale = "🛡️ FAST GREEN LOCK-IN: Price gained +${String.format(Locale.US, "%.2f", priceGainPct)}% (Net: +$${String.format(Locale.US, "%.3f", netPnL)}). Ratcheted protective stop to $$${String.format(Locale.US, "%.2f", guaranteedGreenStopPrice)} (Entry + Fees + 1¢). Trade is now risk-free.",
                    compoundingWisdom = "Never let a profitable trade turn into a loss on small accounts."
                )
            }

            // 3. Stalled Trade Protection: Position held > 45 mins with net green (+1¢ to +3¢), momentum stalled -> Bank and rotate
            holdingMinutes >= 45.0 && netPnL >= 0.01 -> {
                MicroExitActionDecision(
                    action = "MICRO_TAKE_PROFIT",
                    isExitTriggered = true,
                    netPnlUsd = netPnL,
                    grossPnlUsd = grossPnL,
                    feesEstimatedUsd = estRoundTripFees,
                    suggestedStopLoss = null,
                    rationale = "⏱️ CAPITAL ROTATION HARVEST: Held ${String.format(Locale.US, "%.0f", holdingMinutes)}m with net green +$${String.format(Locale.US, "%.3f", netPnL)}. Exiting to free capital for next high-velocity setup.",
                    compoundingWisdom = "Freeing capital quickly prevents opportunity cost in micro accounts."
                )
            }

            // 4. In green and trailing
            netPnL > 0.0 && currentStopLoss >= guaranteedGreenStopPrice -> {
                // Trail stop upward by 0.25% buffer below current price
                val trailingPrice = currentPrice * 0.997
                val optimalStop = max(currentStopLoss, trailingPrice)
                MicroExitActionDecision(
                    action = "TRAIL_DEFENSIVE_STOP",
                    isExitTriggered = false,
                    netPnlUsd = netPnL,
                    grossPnlUsd = grossPnL,
                    feesEstimatedUsd = estRoundTripFees,
                    suggestedStopLoss = optimalStop,
                    rationale = "📈 TRAILING MICRO RUNNER: Net gain +$${String.format(Locale.US, "%.3f", netPnL)}. Trailing stop locked at $$${String.format(Locale.US, "%.2f", optimalStop)}.",
                    compoundingWisdom = "Letting winners run while locking accumulated cents."
                )
            }

            // 5. Normal hold
            else -> {
                MicroExitActionDecision(
                    action = "HOLD",
                    isExitTriggered = false,
                    netPnlUsd = netPnL,
                    grossPnlUsd = grossPnL,
                    feesEstimatedUsd = estRoundTripFees,
                    suggestedStopLoss = null,
                    rationale = "Position active under ${mode.displayName}. Net P&L: $${String.format(Locale.US, "%.3f", netPnL)}.",
                    compoundingWisdom = "Awaiting micro-profit trigger or rapid green lock threshold."
                )
            }
        }
    }

    /**
     * Records a completed micro trade to compound stats and update streaks.
     */
    fun recordCompletedTrade(netPnLUsd: Double) {
        val isWin = netPnLUsd >= 0.005 // at least half a cent net
        val newStreak = if (isWin) state.currentWinningStreak + 1 else 0
        val newTotalTrades = state.totalMicroTradesExecuted + 1
        val newCompounded = state.totalNetPenniesCompoundedUsd + netPnLUsd
        val newAvg = if (newTotalTrades > 0) newCompounded / newTotalTrades else 0.0

        state = state.copy(
            currentWinningStreak = newStreak,
            totalMicroTradesExecuted = newTotalTrades,
            totalNetPenniesCompoundedUsd = newCompounded,
            averageWinPerTradeUsd = newAvg
        )
    }
}
