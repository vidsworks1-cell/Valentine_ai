package com.example.aza.strategy

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Principled 7-State Strategy Lifecycle Model.
 */
enum class StrategyState(val displayName: String, val description: String) {
    PRELIMINARY_OBSERVATION("Preliminary Observation", "Insufficient sample size (N < 15); initial observation in progress."),
    ACTIVE("Active Operational", "Standard operational state meeting performance and stability criteria."),
    RESTRICTED("Restricted Sizing", "Sub-optimal performance or elevated drawdown; capital allocation scaled down."),
    DEGRADED("Degraded Performance", "Poor win rate or expectancy; stringent entry gating applied."),
    SUSPENDED("Suspended Quarantined", "Severe drawdown (>15%) or continuous loss streak; quarantined from live execution."),
    RECOVERY("Paper Recovery", "Undergoing paper execution re-evaluation after suspension."),
    CHAMPION("Dominant Champion", "Top-tier performer with robust sample size (N >= 30) and high expectancy.")
}

/**
 * Statistical uncertainty quantification level based on sample size and variance.
 */
enum class UncertaintyLevel {
    HIGH,    // N < 20: High variance, wide CI bounds. Limit calibration adjustments.
    MEDIUM,  // 20 <= N < 50: Moderate statistical stability. Standard calibration.
    LOW      // N >= 50: High statistical confidence. Full empirical weighting.
}

/**
 * Empirical Confidence Calibration Table record for a specific confidence range.
 * Tracks actual trades, win rate, expectancy, profit factor, and average R multiple
 * to fix model overconfidence and non-discriminating confidence predictions.
 */
data class ConfidenceBucketRecord(
    val rangeLabel: String,      // e.g. "0.85-0.90"
    val minConfidence: Double,   // 0.85
    val maxConfidence: Double,   // 0.90
    var totalTrades: Int = 0,
    var winningTrades: Int = 0,
    var totalPnlR: Double = 0.0,
    var grossProfitsR: Double = 0.0,
    var grossLossesR: Double = 0.0
) {
    val winRatePct: Double get() = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades.toDouble()) * 100.0 else 0.0
    val expectancyR: Double get() = if (totalTrades > 0) totalPnlR / totalTrades.toDouble() else 0.0
    val profitFactor: Double get() = if (grossLossesR > 1e-5) grossProfitsR / grossLossesR else if (grossProfitsR > 0) 5.0 else 1.0
    val averageRMultiple: Double get() = if (totalTrades > 0) totalPnlR / totalTrades.toDouble() else 0.0
}

/**
 * Explicit Calibration and Capital Eligibility breakdown.
 * Separates raw model confidence, calibration-adjusted probability, meta conviction,
 * holistic strategy quality, and capital eligibility score.
 */
data class CalibrationBreakdown(
    val rawConfidence: Double,
    val calibratedConfidence: Double,
    val metaConviction: Double,
    val strategyQuality: Double,          // 0.0 to 100.0
    val capitalEligibilityScore: Double,  // 0.0 to 1.0
    val uncertaintyLevel: UncertaintyLevel,
    val wilsonLowerBound: Double,
    val confidenceIntervalWidth: Double,
    val strategyState: StrategyState,
    val rationale: String
)

/**
 * Counterfactual entry tracking rejected trade candidates for aggregated post-analysis.
 */
data class CounterfactualEntry(
    val id: String = "CF-${System.currentTimeMillis() % 1000000}",
    val symbol: String,
    val strategyName: String,
    val side: String,
    val rejectionReason: String,
    val wouldHaveWon: Boolean,
    val wouldHavePnlR: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Aggregate recommendation derived from counterfactual learning.
 */
data class CounterfactualAdaptationRecommendation(
    val strategyName: String,
    val totalCounterfactuals: Int,
    val winRatePct: Double,
    val netPnlR: Double,
    val recommendedThresholdAdjustment: Double, // e.g. -0.02 or +0.02
    val rationale: String
)

/**
 * Regime performance memory record.
 */
data class RegimeMemoryRecord(
    var sampleSize: Int = 0,
    var winRatePct: Double = 50.0,
    var expectancyR: Double = 0.0
)

/**
 * LUMINA SELF-ADAPTATION 2.0 - CALIBRATION & STATE ENGINE
 * Enforces principled strategy lifecycle state transitions, controlled recency weighting,
 * statistical uncertainty estimation, regime memory sample gating, aggregated counterfactual learning,
 * anti-oscillation hysteresis, and hard risk constraint preservation.
 */
object LuminaSelfAdaptationEngine {

    private val regimeMemory = mutableMapOf<String, RegimeMemoryRecord>()
    private val counterfactualLedger = mutableListOf<CounterfactualEntry>()
    private val currentStrategyStates = mutableMapOf<String, StrategyState>()
    private val lastStateTransitionTradeCounts = mutableMapOf<String, Int>()
    private val strategyConfidenceTables = mutableMapOf<String, MutableList<ConfidenceBucketRecord>>()

    fun createStandardBuckets(): MutableList<ConfidenceBucketRecord> {
        return mutableListOf(
            ConfidenceBucketRecord("0.30-0.35", 0.30, 0.35),
            ConfidenceBucketRecord("0.35-0.40", 0.35, 0.40),
            ConfidenceBucketRecord("0.40-0.45", 0.40, 0.45),
            ConfidenceBucketRecord("0.45-0.50", 0.45, 0.50),
            ConfidenceBucketRecord("0.50-0.55", 0.50, 0.55),
            ConfidenceBucketRecord("0.55-0.60", 0.55, 0.60),
            ConfidenceBucketRecord("0.60-0.65", 0.60, 0.65),
            ConfidenceBucketRecord("0.65-0.70", 0.65, 0.70),
            ConfidenceBucketRecord("0.70-0.75", 0.70, 0.75),
            ConfidenceBucketRecord("0.75-0.80", 0.75, 0.80),
            ConfidenceBucketRecord("0.80-0.85", 0.80, 0.85),
            ConfidenceBucketRecord("0.85-0.90", 0.85, 0.90),
            ConfidenceBucketRecord("0.90-0.95", 0.90, 0.95),
            ConfidenceBucketRecord("0.95-1.00", 0.95, 1.00)
        )
    }

    fun buildStrategySymbolKey(strategyName: String, symbol: String = ""): String {
        val cleanSym = symbol.trim().uppercase()
        return if (cleanSym.isNotEmpty()) "$strategyName@$cleanSym" else strategyName
    }

    fun getOrCreateConfidenceTable(strategyName: String, symbol: String = ""): MutableList<ConfidenceBucketRecord> {
        val key = buildStrategySymbolKey(strategyName, symbol)
        return strategyConfidenceTables.getOrPut(key) { createStandardBuckets() }
    }

    /**
     * Records trade outcome into the Confidence Calibration Table for empirical self-adaptation.
     * Records both symbol-specific and global strategy tables to support symbol-dependent allocation.
     */
    fun recordTradeOutcomeForCalibration(
        strategyName: String,
        predictedConfidence: Double,
        isWin: Boolean,
        pnlR: Double,
        symbol: String = ""
    ) {
        val symbolKey = buildStrategySymbolKey(strategyName, symbol)
        val keysToUpdate = if (symbol.isNotBlank()) listOf(symbolKey, strategyName) else listOf(strategyName)

        for (key in keysToUpdate) {
            val table = strategyConfidenceTables.getOrPut(key) { createStandardBuckets() }
            val conf = predictedConfidence.coerceIn(0.30, 0.99)
            val bucket = table.find { conf >= it.minConfidence && conf < it.maxConfidence } ?: table.last()

            bucket.totalTrades++
            if (isWin) bucket.winningTrades++
            bucket.totalPnlR += pnlR
            if (pnlR > 0) bucket.grossProfitsR += pnlR else bucket.grossLossesR += kotlin.math.abs(pnlR)
        }
    }

    fun getConfidenceCalibrationTable(strategyName: String, symbol: String = ""): List<ConfidenceBucketRecord> {
        return getOrCreateConfidenceTable(strategyName, symbol)
    }

    /**
     * Determines statistical uncertainty level and CI width from sample size and win rate.
     */
    fun calculateUncertaintyDetails(wins: Int, totalTrades: Int): Pair<UncertaintyLevel, Double> {
        if (totalTrades < 1) {
            return Pair(UncertaintyLevel.HIGH, 1.0)
        }
        val level = when {
            totalTrades < 20 -> UncertaintyLevel.HIGH
            totalTrades < 50 -> UncertaintyLevel.MEDIUM
            else -> UncertaintyLevel.LOW
        }
        val p = (wins.toDouble() / totalTrades.toDouble()).coerceIn(0.01, 0.99)
        val z = 1.96 // 95% CI
        val ciWidth = 2.0 * z * sqrt((p * (1.0 - p)) / totalTrades.toDouble())
        return Pair(level, ciWidth)
    }

    /**
     * Calculates Wilson lower bound score for sample proportion.
     */
    fun calculateWilsonLowerBound(wins: Int, total: Int, confidenceLevel: Double = 1.96): Double {
        if (total <= 0) return 0.50
        val p = wins.toDouble() / total.toDouble()
        val z = confidenceLevel
        val z2 = z * z
        val denominator = 1.0 + z2 / total
        val centre = p + z2 / (2.0 * total)
        val spread = sqrt((p * (1.0 - p) + z2 / (4.0 * total)) / total)
        return ((centre - z * spread) / denominator).coerceIn(0.0, 1.0)
    }

    /**
     * Controlled recency weighting.
     * Prevents last few trades from completely rewriting long-term strategy beliefs.
     */
    fun calculateBlendedPerformance(
        longTermWinRatePct: Double,
        recentWinRatePct: Double,
        longTermExpectancyR: Double,
        recentExpectancyR: Double,
        totalTrades: Int
    ): Pair<Double, Double> {
        if (totalTrades < 15) {
            // For small sample sizes, rely primarily on long-term / prior benchmark to avoid noise overfitting
            return Pair(longTermWinRatePct, longTermExpectancyR)
        }
        // Controlled recency blending: 60% long-term baseline + 40% recent window
        val blendedWR = (longTermWinRatePct * 0.60) + (recentWinRatePct * 0.40)
        val blendedExp = (longTermExpectancyR * 0.60) + (recentExpectancyR * 0.40)
        return Pair(blendedWR, blendedExp)
    }

    /**
     * Regime Memory lookup with minimum sample size gating (N >= 10 required for strong bonuses/penalties).
     */
    fun getRegimeAdjustment(
        strategyName: String,
        marketRegime: String,
        volatilityState: String
    ): Double {
        val key = "${strategyName}_${marketRegime}_${volatilityState}"
        val record = regimeMemory[key] ?: return 0.0

        if (record.sampleSize < 10) {
            // Scale adjustment down proportionally by sample count if N < 10
            val scaleFactor = record.sampleSize / 10.0
            val rawAdj = ((record.winRatePct - 50.0) / 100.0) * 0.10
            return (rawAdj * scaleFactor).coerceIn(-0.02, 0.02)
        }

        val rawAdj = ((record.winRatePct - 50.0) / 100.0) * 0.15 + (record.expectancyR * 0.05)
        return rawAdj.coerceIn(-0.10, 0.10)
    }

    /**
     * Updates regime memory record with new trade outcome.
     */
    fun recordRegimeOutcome(
        strategyName: String,
        marketRegime: String,
        volatilityState: String,
        isWin: Boolean,
        pnlR: Double
    ) {
        val key = "${strategyName}_${marketRegime}_${volatilityState}"
        val record = regimeMemory.getOrPut(key) { RegimeMemoryRecord() }
        val newCount = record.sampleSize + 1
        val winVal = if (isWin) 100.0 else 0.0
        record.winRatePct = ((record.winRatePct * record.sampleSize) + winVal) / newCount
        record.expectancyR = ((record.expectancyR * record.sampleSize) + pnlR) / newCount
        record.sampleSize = newCount
    }

    /**
     * Evaluates State Transition with Anti-Oscillation Hysteresis and Minimum Dwell Count.
     */
    fun evaluateStrategyState(
        strategyName: String,
        totalTrades: Int,
        winRatePct: Double,
        expectancyR: Double,
        maxDrawdownPct: Double = 4.0
    ): StrategyState {
        val previousState = currentStrategyStates[strategyName] ?: StrategyState.PRELIMINARY_OBSERVATION
        val lastTradeCount = lastStateTransitionTradeCounts[strategyName] ?: 0

        // Rule 1: Emergency Suspension if severe drawdown occurs regardless of trade count
        if (maxDrawdownPct > 15.0) {
            currentStrategyStates[strategyName] = StrategyState.SUSPENDED
            lastStateTransitionTradeCounts[strategyName] = totalTrades
            return StrategyState.SUSPENDED
        }

        // Rule 2: Minimum sample size for initial observation
        if (totalTrades < 15) {
            currentStrategyStates[strategyName] = StrategyState.PRELIMINARY_OBSERVATION
            return StrategyState.PRELIMINARY_OBSERVATION
        }

        // Rule 3: Enforce Minimum Dwell Count (must remain in current state for at least 5 trades before changing)
        val dwellTrades = totalTrades - lastTradeCount
        if (dwellTrades < 5 && previousState != StrategyState.PRELIMINARY_OBSERVATION) {
            return previousState
        }

        // Rule 4: Anti-Oscillation Hysteresis Buffers with Expectancy-First Degradation
        val newState = when (previousState) {
            StrategyState.PRELIMINARY_OBSERVATION -> {
                when {
                    winRatePct >= 55.0 && expectancyR >= 0.40 && totalTrades >= 30 -> StrategyState.CHAMPION
                    winRatePct >= 45.0 && expectancyR >= 0.15 -> StrategyState.ACTIVE
                    expectancyR < -0.05 || winRatePct < 30.0 -> StrategyState.DEGRADED
                    winRatePct < 40.0 || expectancyR < 0.05 -> StrategyState.RESTRICTED
                    else -> StrategyState.ACTIVE
                }
            }
            StrategyState.ACTIVE -> {
                when {
                    winRatePct >= 58.0 && expectancyR >= 0.45 && totalTrades >= 30 && maxDrawdownPct <= 5.0 -> StrategyState.CHAMPION
                    expectancyR < -0.05 || winRatePct < 32.0 || maxDrawdownPct > 12.0 -> StrategyState.DEGRADED
                    winRatePct < 42.0 || expectancyR < 0.08 || maxDrawdownPct > 8.0 -> StrategyState.RESTRICTED
                    else -> StrategyState.ACTIVE
                }
            }
            StrategyState.RESTRICTED -> {
                when {
                    winRatePct > 48.0 && expectancyR > 0.12 && maxDrawdownPct <= 6.0 -> StrategyState.ACTIVE
                    expectancyR < 0.0 || winRatePct < 30.0 || maxDrawdownPct > 12.0 -> StrategyState.SUSPENDED
                    winRatePct < 35.0 || expectancyR < 0.01 -> StrategyState.DEGRADED
                    else -> StrategyState.RESTRICTED
                }
            }
            StrategyState.DEGRADED -> {
                when {
                    winRatePct > 44.0 && expectancyR > 0.10 -> StrategyState.RESTRICTED
                    expectancyR < 0.0 || winRatePct < 32.0 || maxDrawdownPct > 12.0 -> StrategyState.SUSPENDED
                    else -> StrategyState.DEGRADED
                }
            }
            StrategyState.SUSPENDED -> StrategyState.RECOVERY
            StrategyState.RECOVERY -> {
                if (winRatePct >= 50.0 && expectancyR >= 0.20) StrategyState.RESTRICTED else StrategyState.RECOVERY
            }
            StrategyState.CHAMPION -> {
                if (winRatePct < 52.0 || expectancyR < 0.25 || maxDrawdownPct > 6.0) StrategyState.ACTIVE else StrategyState.CHAMPION
            }
        }

        if (newState != previousState) {
            currentStrategyStates[strategyName] = newState
            lastStateTransitionTradeCounts[strategyName] = totalTrades
        }

        return newState
    }

    /**
     * Explicit Calibration Pipeline separating raw confidence, calibrated probability,
     * meta conviction, holistic strategy quality, and capital eligibility score.
     */
    fun calibrateStrategyAndCapital(
        rawConfidence: Double,
        metaConviction: Double,
        longTermWinRatePct: Double,
        recentWinRatePct: Double,
        longTermExpectancyR: Double,
        recentExpectancyR: Double,
        totalTrades: Int,
        maxDrawdownPct: Double = 4.0,
        marketRegime: String = "RANGE_BOUND",
        volatilityState: String = "NORMAL",
        strategyName: String = "DefaultStrategy",
        symbol: String = ""
    ): CalibrationBreakdown {
        val effectiveKey = buildStrategySymbolKey(strategyName, symbol)
        val wins = ((longTermWinRatePct / 100.0) * totalTrades).toInt()
        val (uncertainty, ciWidth) = calculateUncertaintyDetails(wins, totalTrades)
        val wilsonLower = calculateWilsonLowerBound(wins, totalTrades)

        val (blendedWR, blendedExp) = calculateBlendedPerformance(
            longTermWinRatePct, recentWinRatePct,
            longTermExpectancyR, recentExpectancyR,
            totalTrades
        )

        val regimeAdj = getRegimeAdjustment(strategyName, marketRegime, volatilityState)

        val state = evaluateStrategyState(
            strategyName = effectiveKey,
            totalTrades = totalTrades,
            winRatePct = blendedWR,
            expectancyR = blendedExp,
            maxDrawdownPct = maxDrawdownPct
        )

        // Lookup empirical confidence bucket calibration if historical trades exist
        val table = getOrCreateConfidenceTable(strategyName, symbol)
        val targetConf = rawConfidence.coerceIn(0.30, 0.99)
        val bucket = table.find { targetConf >= it.minConfidence && targetConf < it.maxConfidence } ?: table.last()

        val baseCalibratedProb = if (uncertainty == UncertaintyLevel.HIGH) {
            // High uncertainty: Cap adjustment at max +/- 0.05 from raw confidence
            val target = (wilsonLower * 0.50) + (rawConfidence * 0.50)
            val delta = (target - rawConfidence).coerceIn(-0.05, 0.05)
            (rawConfidence + delta).coerceIn(0.10, 0.95)
        } else {
            val sampleWeight = (totalTrades / 50.0).coerceAtMost(1.0)
            val empiricalTarget = (wilsonLower * 0.60) + (blendedExp.coerceIn(0.0, 1.0) * 0.40)
            val blended = (rawConfidence * (1.0 - (sampleWeight * 0.60))) + (empiricalTarget * (sampleWeight * 0.60)) + regimeAdj
            blended.coerceIn(0.10, 0.98)
        }

        val calibratedProb = if (bucket.totalTrades >= 5) {
            val empiricalWR = bucket.winRatePct / 100.0
            val bucketWeight = (bucket.totalTrades / 25.0).coerceAtMost(0.70)
            val bucketAdjusted = (baseCalibratedProb * (1.0 - bucketWeight)) + (empiricalWR * bucketWeight)
            if (empiricalWR < 0.45 && rawConfidence >= 0.75) {
                // Fix inverse discrimination (e.g. overconfidence on losing setups)
                kotlin.math.min(bucketAdjusted, empiricalWR + 0.10).coerceIn(0.10, 0.98)
            } else {
                bucketAdjusted.coerceIn(0.10, 0.98)
            }
        } else {
            baseCalibratedProb
        }

        // Calculate Holistic Strategy Quality (0 to 100)
        // Expectancy E[R] is the primary measure of trading edge rather than raw confidence
        val expQuality = (((blendedExp.coerceIn(-1.0, 3.0) + 1.0) / 4.0) * 50.0).coerceIn(0.0, 50.0)
        val wrQuality = (blendedWR * 0.35).coerceIn(0.0, 35.0)
        val profitFactorQuality = if (bucket.profitFactor > 1.0) ((bucket.profitFactor - 1.0) * 10.0).coerceAtMost(15.0) else 0.0
        val ddPenalty = (maxDrawdownPct * 1.5).coerceAtMost(20.0)
        val rawQuality = (expQuality + wrQuality + profitFactorQuality - ddPenalty + (regimeAdj * 100.0)).coerceIn(5.0, 99.0)

        // Calculate Capital Eligibility Score (0.0 to 1.0)
        val stateMultiplier = when (state) {
            StrategyState.CHAMPION -> 1.00
            StrategyState.ACTIVE -> 0.85
            StrategyState.PRELIMINARY_OBSERVATION -> 0.60
            StrategyState.RESTRICTED -> 0.45
            StrategyState.DEGRADED -> 0.25
            StrategyState.RECOVERY -> 0.20
            StrategyState.SUSPENDED -> 0.00
        }

        val uncertaintyMultiplier = when (uncertainty) {
            UncertaintyLevel.LOW -> 1.00
            UncertaintyLevel.MEDIUM -> 0.85
            UncertaintyLevel.HIGH -> 0.65
        }

        val capitalEligibilityScore = ((rawQuality / 100.0) * stateMultiplier * uncertaintyMultiplier).coerceIn(0.0, 1.0)

        val rationale = buildString {
            append("State: ${state.displayName} | Quality: ${String.format(Locale.US, "%.1f", rawQuality)}/100 | ")
            append("Capital Eligibility: ${String.format(Locale.US, "%.2f", capitalEligibilityScore)} | ")
            append("Uncertainty: $uncertainty (N=$totalTrades, CI Width: ${String.format(Locale.US, "%.2f", ciWidth)}) | ")
            append("Calibrated Conf: ${String.format(Locale.US, "%.2f", calibratedProb)} (Raw: ${String.format(Locale.US, "%.2f", rawConfidence)})")
        }

        return CalibrationBreakdown(
            rawConfidence = rawConfidence,
            calibratedConfidence = calibratedProb,
            metaConviction = metaConviction,
            strategyQuality = rawQuality,
            capitalEligibilityScore = capitalEligibilityScore,
            uncertaintyLevel = uncertainty,
            wilsonLowerBound = wilsonLower,
            confidenceIntervalWidth = ciWidth,
            strategyState = state,
            rationale = rationale
        )
    }

    /**
     * Records counterfactual outcome for a rejected trade candidate.
     */
    fun recordCounterfactual(
        symbol: String,
        strategyName: String,
        side: String,
        rejectionReason: String,
        wouldHaveWon: Boolean,
        wouldHavePnlR: Double
    ) {
        val entry = CounterfactualEntry(
            symbol = symbol,
            strategyName = strategyName,
            side = side,
            rejectionReason = rejectionReason,
            wouldHaveWon = wouldHaveWon,
            wouldHavePnlR = wouldHavePnlR
        )
        counterfactualLedger.add(entry)
    }

    /**
     * Evaluates aggregated counterfactual learning.
     * Only returns non-zero threshold adjustments if N_counter >= 10.
     */
    fun evaluateAggregatedCounterfactuals(strategyName: String): CounterfactualAdaptationRecommendation {
        val entries = counterfactualLedger.filter { it.strategyName.equals(strategyName, ignoreCase = true) }
        val count = entries.size

        if (count < 10) {
            return CounterfactualAdaptationRecommendation(
                strategyName = strategyName,
                totalCounterfactuals = count,
                winRatePct = 0.0,
                netPnlR = 0.0,
                recommendedThresholdAdjustment = 0.0,
                rationale = "Sample size ($count counterfactuals) insufficient (N >= 10 required); retaining thresholds."
            )
        }

        val wins = entries.count { it.wouldHaveWon }
        val winRatePct = (wins.toDouble() / count.toDouble()) * 100.0
        val netPnlR = entries.sumOf { it.wouldHavePnlR }

        val adjustment = when {
            winRatePct >= 65.0 && netPnlR >= 3.0 -> -0.03 // Relax threshold slightly if rejected trades were highly profitable
            winRatePct <= 30.0 || netPnlR <= -2.0 -> +0.03 // Tighten threshold if rejected trades were indeed losing trades
            else -> 0.0
        }

        return CounterfactualAdaptationRecommendation(
            strategyName = strategyName,
            totalCounterfactuals = count,
            winRatePct = winRatePct,
            netPnlR = netPnlR,
            recommendedThresholdAdjustment = adjustment,
            rationale = "Aggregated $count counterfactuals (WR: ${String.format(Locale.US, "%.1f", winRatePct)}%, Net: +${String.format(Locale.US, "%.2f", netPnlR)}R) -> Recommended Threshold Delta: ${String.format(Locale.US, "%+.2f", adjustment)}"
        )
    }

    fun clearLedgers() {
        regimeMemory.clear()
        counterfactualLedger.clear()
        currentStrategyStates.clear()
        lastStateTransitionTradeCounts.clear()
    }
}
