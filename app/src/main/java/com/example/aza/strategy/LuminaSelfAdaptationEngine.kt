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
 * holistic strategy quality, capital eligibility score, and the empirical probability decomposition.
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
    val rationale: String,
    val matrixRiskMultiplier: Double = 1.0,
    val matrixCooldownSeconds: Long = 0L,
    val matrixAdaptationTier: MatrixAdaptationTier = MatrixAdaptationTier.NEUTRAL,
    
    // Explicit Empirical Probability Components:
    // Final Confidence = Model Prediction × Historical Calibration × Regime Performance × Symbol Performance × Strategy Performance × Recent Rolling Performance
    val modelPrediction: Double = rawConfidence,
    val historicalCalibrationFactor: Double = 1.0,
    val historicalBucketWinRate: Double = 0.74,
    val regimePerformanceFactor: Double = 1.0,
    val symbolPerformanceFactor: Double = 1.0,
    val strategyPerformanceFactor: Double = 1.0,
    val recentRollingPerformanceFactor: Double = 1.0
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
    private val strategyEloRatings = mutableMapOf<String, Double>()

    fun getStrategyElo(strategyName: String): Double {
        return strategyEloRatings.getOrDefault(strategyName, 1500.0)
    }

    /**
     * Updates strategy Elo rating after trade outcome:
     * Delta Elo = K * (ActualScore - ExpectedScore)
     * K = 32.0 (standard Elo K-factor)
     */
    fun updateStrategyElo(strategyName: String, isWin: Boolean, profitR: Double = 1.0) {
        val currentElo = getStrategyElo(strategyName)
        val expectedScore = 1.0 / (1.0 + Math.pow(10.0, (1500.0 - currentElo) / 400.0))
        val actualScore = if (isWin) (if (profitR > 1.5) 1.2 else 1.0) else 0.0
        val kFactor = 32.0
        val newElo = (currentElo + kFactor * (actualScore - expectedScore)).coerceIn(1000.0, 2200.0)
        strategyEloRatings[strategyName] = newElo
    }

    /**
     * Calculates Elo-Proportional Capital Allocation:
     * Capital Share_s = exp((Elo_s - 1500) / 400) / sum(exp((Elo_i - 1500) / 400))
     * Returns a map of strategyName -> recommended risk allowance % (e.g. 0.25% to 2.0%).
     */
    fun calculateEloProportionalCapitalAllocation(activeStrategies: List<String>): Map<String, Double> {
        if (activeStrategies.isEmpty()) return emptyMap()

        val eloMap = activeStrategies.associateWith { getStrategyElo(it) }
        val expWeights = eloMap.mapValues { (_, elo) ->
            Math.exp((elo - 1500.0) / 400.0)
        }
        val sumExp = expWeights.values.sum().coerceAtLeast(1e-6)

        return eloMap.mapValues { (strat, elo) ->
            val share = expWeights.getValue(strat) / sumExp
            val baseRisk = share * 5.0 // total portfolio pool allocation share
            when {
                elo >= 1600.0 -> baseRisk.coerceIn(0.0125, 0.0200) // High Elo Champion: up to 2.0% risk
                elo >= 1450.0 -> baseRisk.coerceIn(0.0075, 0.0125) // Standard Active: 0.75% - 1.25% risk
                else -> baseRisk.coerceIn(0.0025, 0.0050)          // Low Elo / Probation: restricted to 0.25% - 0.50%
            }
        }
    }

    init {
        seedInitialCalibrationTables()
    }

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
        return if (cleanSym.isNotEmpty()) {
            StrategyAlphaMemory.buildProfileKey(cleanSym, strategyName)
        } else {
            strategyName
        }
    }

    fun getOrCreateConfidenceTable(strategyName: String, symbol: String = ""): MutableList<ConfidenceBucketRecord> {
        val key = buildStrategySymbolKey(strategyName, symbol)
        return strategyConfidenceTables.getOrPut(key) { createStandardBuckets() }
    }

    /**
     * Seeds initial empirical confidence calibration tables for benchmark profiles.
     */
    private fun seedInitialCalibrationTables() {
        val profiles = listOf("VEB_BNB", "VEB_ETH", "VEB_SOL", "VEB_BTC", "MOM_BTC", "MR_ETH")
        for (p in profiles) {
            val table = strategyConfidenceTables.getOrPut(p) { createStandardBuckets() }
            // Seed empirical trade distributions across confidence ranges
            table.forEach { bucket ->
                when (bucket.rangeLabel) {
                    "0.85-0.90" -> {
                        bucket.totalTrades = 24
                        bucket.winningTrades = if (p == "VEB_BNB" || p == "VEB_BTC") 18 else 16 // ~75% WR
                        bucket.totalPnlR = 14.5
                        bucket.grossProfitsR = 21.0
                        bucket.grossLossesR = 6.5
                    }
                    "0.90-0.95" -> {
                        bucket.totalTrades = 19
                        bucket.winningTrades = if (p == "VEB_BNB") 14 else if (p == "VEB_ETH") 12 else 15 // ~74% WR
                        bucket.totalPnlR = 12.8
                        bucket.grossProfitsR = 18.2
                        bucket.grossLossesR = 5.4
                    }
                    "0.80-0.85" -> {
                        bucket.totalTrades = 18
                        bucket.winningTrades = 11 // ~61% WR
                        bucket.totalPnlR = 6.4
                        bucket.grossProfitsR = 13.0
                        bucket.grossLossesR = 6.6
                    }
                    "0.75-0.80" -> {
                        bucket.totalTrades = 15
                        bucket.winningTrades = 8 // ~53% WR
                        bucket.totalPnlR = 2.1
                        bucket.grossProfitsR = 9.5
                        bucket.grossLossesR = 7.4
                    }
                    "0.70-0.75" -> {
                        bucket.totalTrades = 12
                        bucket.winningTrades = 6 // ~50% WR
                        bucket.totalPnlR = 0.5
                        bucket.grossProfitsR = 7.0
                        bucket.grossLossesR = 6.5
                    }
                }
            }
        }
    }

    /**
     * Records trade outcome into the Confidence Calibration Table and Regime × Symbol × Strategy Learning Matrix.
     * Records both symbol-specific and global strategy tables to support symbol-dependent allocation.
     */
    fun recordTradeOutcomeForCalibration(
        strategyName: String,
        predictedConfidence: Double,
        isWin: Boolean,
        pnlR: Double,
        symbol: String = "",
        marketRegime: String = "TRENDING",
        holdTimeMinutes: Double = 30.0,
        isOperatorInitiated: Boolean = false
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
            
            // Only update Strategy Elo and Online SGD Model if the trade was an autonomous decision (not operator override)
            if (!isOperatorInitiated) {
                updateStrategyElo(key, isWin, pnlR)

                // Trigger Online SGD Machine Learning Model Weight Update
                val eloVal = getStrategyElo(key)
                val totalT = table.sumOf { it.totalTrades }
                val winsT = table.sumOf { it.winningTrades }
                val wrPct = if (totalT > 0) (winsT.toDouble() / totalT) * 100.0 else 50.0
                OnlineConfidenceMLEngine.updateOnlineSGD(
                    rawConfidence = predictedConfidence,
                    metaConviction = 75.0,
                    volatilityRatio = 1.0,
                    eloRating = eloVal,
                    winRatePct = wrPct,
                    actualOutcomeWon = isWin
                )
            }
        }

        // Record in Regime × Symbol × Strategy Learning Matrix
        val normSym = if (symbol.isNotBlank()) symbol else "BTC"
        RegimeSymbolStrategyLearningMatrix.recordTrade(
            symbol = normSym,
            regime = marketRegime,
            strategy = strategyName,
            isWin = isWin,
            pnlR = pnlR,
            holdTimeMinutes = holdTimeMinutes
        )

        // Record in Strategy Alpha Memory for continuous independent profile evaluation
        StrategyAlphaMemory.recordTrade(
            symbol = normSym,
            strategyName = strategyName,
            isWin = isWin,
            pnlR = pnlR,
            holdTimeMinutes = holdTimeMinutes,
            marketRegime = marketRegime
        )
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
     * Calculates Bayesian Posterior Win Rate with empirical Beta(4.0, 6.0) prior (prior mean = 40.0%).
     * Shrinks raw confidence towards empirical posterior when sample size is small or performance decays.
     */
    fun calculateBayesianPosteriorWinRate(
        wins: Int,
        totalTrades: Int,
        priorAlpha: Double = 4.0,
        priorBeta: Double = 6.0
    ): Double {
        if (totalTrades <= 0) return priorAlpha / (priorAlpha + priorBeta)
        val posteriorAlpha = priorAlpha + wins
        val posteriorBeta = priorBeta + (totalTrades - wins)
        return (posteriorAlpha / (posteriorAlpha + posteriorBeta)).coerceIn(0.05, 0.95)
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
        // Query independent profile statistics from Strategy Alpha Memory if available
        val profileAlpha = if (symbol.isNotBlank()) {
            StrategyAlphaMemory.getProfile(symbol, strategyName)
        } else null

        val effTotalTrades = if (profileAlpha != null && profileAlpha.trades > 0) profileAlpha.trades else totalTrades
        val effLongTermWR = if (profileAlpha != null && profileAlpha.trades > 0) profileAlpha.winRatePct else longTermWinRatePct
        val effRecentWR = if (profileAlpha != null && profileAlpha.rolling50.trades > 0) profileAlpha.rolling50.winRatePct else recentWinRatePct
        val effLongTermExp = if (profileAlpha != null && profileAlpha.trades > 0) profileAlpha.expectancyR else longTermExpectancyR
        val effRecentExp = if (profileAlpha != null && profileAlpha.rolling50.trades > 0) profileAlpha.rolling50.expectancyR else recentExpectancyR
        val effMaxDD = if (profileAlpha != null && profileAlpha.trades > 0) (profileAlpha.maxDrawdownR * 1.5).coerceIn(1.0, 25.0) else maxDrawdownPct

        val effectiveKey = buildStrategySymbolKey(strategyName, symbol)
        val wins = ((effLongTermWR / 100.0) * effTotalTrades).toInt()
        val (uncertainty, ciWidth) = calculateUncertaintyDetails(wins, effTotalTrades)
        val zScore = if (strategyName.contains("Mean Reversion", ignoreCase = true)) 2.326 else 1.96
        val wilsonLower = calculateWilsonLowerBound(wins, effTotalTrades, zScore)
        val bayesWinRate = calculateBayesianPosteriorWinRate(wins, effTotalTrades)

        val (blendedWR, blendedExp) = calculateBlendedPerformance(
            effLongTermWR, effRecentWR,
            effLongTermExp, effRecentExp,
            effTotalTrades
        )

        val regimeAdj = getRegimeAdjustment(strategyName, marketRegime, volatilityState)

        val state = evaluateStrategyState(
            strategyName = effectiveKey,
            totalTrades = effTotalTrades,
            winRatePct = blendedWR,
            expectancyR = blendedExp,
            maxDrawdownPct = effMaxDD
        )

        // Lookup empirical confidence bucket calibration from Profile Calibration Table
        val table = getOrCreateConfidenceTable(strategyName, symbol)
        val targetConf = rawConfidence.coerceIn(0.30, 0.99)
        val bucket = table.find { targetConf >= it.minConfidence && targetConf < it.maxConfidence } ?: table.last()

        // 1. Model Prediction
        val modelPrediction = targetConf

        // 2. Historical Calibration Factor (Empirical Profile Bucket Win Rate)
        val historicalBucketWinRate = if (bucket.totalTrades >= 3) {
            (bucket.winRatePct / 100.0).coerceIn(0.10, 0.98)
        } else {
            // Beta prior / Wilson blend for low-observation buckets
            ((wilsonLower * 0.45) + (bayesWinRate * 0.35) + (targetConf * 0.20)).coerceIn(0.10, 0.95)
        }
        val historicalCalibrationFactor = if (modelPrediction > 0.05) {
            (historicalBucketWinRate / modelPrediction).coerceIn(0.30, 1.40)
        } else 1.0

        // 3. Regime Performance Factor (from Regime × Symbol × Strategy Learning Matrix)
        val normSym = if (symbol.isNotBlank()) StrategyAlphaMemory.normalizeSymbol(symbol) else "BTC"
        val matrixCell = RegimeSymbolStrategyLearningMatrix.getOrCreateCell(
            symbol = normSym,
            regime = marketRegime,
            strategy = strategyName
        )
        val matrixAdapt = RegimeSymbolStrategyLearningMatrix.getAdaptation(
            symbol = normSym,
            regime = marketRegime,
            strategy = strategyName
        )
        val regimePerformanceFactor = if (matrixCell.trades >= 3) {
            val baseWR = effLongTermWR.coerceAtLeast(35.0)
            val ratio = matrixCell.winRate / baseWR
            (1.0 + (ratio - 1.0) * 0.50).coerceIn(0.70, 1.25)
        } else {
            (1.0 + (matrixAdapt.confidenceOffset * 1.5)).coerceIn(0.80, 1.20)
        }

        // 4. Symbol Performance Factor (from Strategy Alpha Memory)
        val symbolPerformanceFactor = if (profileAlpha != null && profileAlpha.trades >= 5) {
            val symbolRatio = profileAlpha.winRatePct / 50.0
            (1.0 + (symbolRatio - 1.0) * 0.40).coerceIn(0.75, 1.25)
        } else 1.00

        // 5. Strategy Performance Factor (from Strategy Elo / Lifetime Edge)
        val currentElo = getStrategyElo(effectiveKey)
        val strategyPerformanceFactor = (1.0 + (currentElo - 1500.0) / 4000.0).coerceIn(0.80, 1.20)

        // 6. Recent Rolling Performance Factor (from Profile Rolling Horizons)
        val recentRollingPerformanceFactor = if (profileAlpha != null && profileAlpha.rolling50.trades >= 5) {
            val rollingWR = profileAlpha.rolling50.winRatePct
            val lifetimeWR = profileAlpha.winRatePct.coerceAtLeast(35.0)
            val rollingRatio = rollingWR / lifetimeWR
            (1.0 + (rollingRatio - 1.0) * 0.50).coerceIn(0.70, 1.25)
        } else {
            val rollingRatio = effRecentWR / effLongTermWR.coerceAtLeast(35.0)
            (1.0 + (rollingRatio - 1.0) * 0.40).coerceIn(0.75, 1.25)
        }

        // Multi-tier Bayesian Shrinkage dampening for Mean Reversion
        val isMeanReversion = strategyName.contains("Mean Reversion", ignoreCase = true)
        val meanReversionDampener = if (isMeanReversion) {
            when {
                blendedWR < 45.0 || blendedExp < -0.10 -> 0.85 // 15% dampening when win rate < 45% or negative expectancy
                blendedWR < 50.0 || blendedExp <= 0.0 -> 0.92  // 8% dampening when win rate < 50%
                marketRegime.contains("TRENDING", ignoreCase = true) && !marketRegime.contains("PULLBACK", ignoreCase = true) -> 0.95 // 5% dampening in strong trending non-pullback regimes
                else -> 1.00
            }
        } else 1.00

        // MULTIPLICATIVE EMPIRICAL PROBABILITY:
        // Final Confidence = Model Prediction × Historical Calibration × Regime Performance × Symbol Performance × Strategy Performance × Recent Rolling Performance
        val unconstrainedEmpiricalProbability = (
            modelPrediction *
            historicalCalibrationFactor *
            regimePerformanceFactor *
            symbolPerformanceFactor *
            strategyPerformanceFactor *
            recentRollingPerformanceFactor *
            meanReversionDampener
        ).coerceIn(0.05, 0.98)

        // Online Machine Learning Confidence Model Prediction
        val mlProb = OnlineConfidenceMLEngine.predict(
            rawConfidence = targetConf,
            metaConviction = metaConviction,
            volatilityRatio = 1.0,
            eloRating = currentElo,
            winRatePct = blendedWR
        )
        // Blend Empirical decomposition (80%) with ML Model prediction (20%)
        val blendedEmpiricalProb = ((unconstrainedEmpiricalProbability * 0.80) + (mlProb * 0.20)).coerceIn(0.05, 0.98)

        // Low Sample Size Safeguard via Wilson Confidence Intervals:
        // Never allow raw model confidence to bypass calibration; low sample sizes shrink to Wilson lower bound
        val rawCalibratedProb = if (uncertainty == UncertaintyLevel.HIGH || effTotalTrades < 20) {
            val lambda = ((20 - effTotalTrades).coerceAtLeast(0) / 20.0).coerceIn(0.0, 0.85)
            ((1.0 - lambda) * blendedEmpiricalProb + lambda * wilsonLower).coerceIn(0.05, 0.98)
        } else {
            blendedEmpiricalProb
        }
        val calibratedProb = if (uncertainty == UncertaintyLevel.HIGH) {
            rawCalibratedProb.coerceIn(targetConf - 0.05, targetConf + 0.05)
        } else {
            rawCalibratedProb
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

        val baseCapitalEligibility = ((rawQuality / 100.0) * stateMultiplier * uncertaintyMultiplier).coerceIn(0.0, 1.0)
        // Scale risk capital allocation dynamically by Regime × Symbol × Strategy matrix multiplier
        val finalCapitalEligibilityScore = (baseCapitalEligibility * matrixAdapt.riskMultiplier).coerceIn(0.0, 1.0)

        val rationale = buildString {
            append("Empirical Prob: ${String.format(Locale.US, "%.2f", calibratedProb)} (Raw Model: ${String.format(Locale.US, "%.2f", modelPrediction)} × HistCalib: ${String.format(Locale.US, "%.2f", historicalCalibrationFactor)} [BucketWR: ${String.format(Locale.US, "%.0f%%", historicalBucketWinRate * 100)}] × Regime: ${String.format(Locale.US, "%.2f", regimePerformanceFactor)} × Sym: ${String.format(Locale.US, "%.2f", symbolPerformanceFactor)} × Strat: ${String.format(Locale.US, "%.2f", strategyPerformanceFactor)} × Rolling: ${String.format(Locale.US, "%.2f", recentRollingPerformanceFactor)}) | ")
            append("State: ${state.displayName} | Quality: ${String.format(Locale.US, "%.1f", rawQuality)}/100 | ")
            append("Capital Eligibility: ${String.format(Locale.US, "%.2f", finalCapitalEligibilityScore)} (RiskMult: x${String.format(Locale.US, "%.2f", matrixAdapt.riskMultiplier)}) | ")
            append("Uncertainty: $uncertainty (N=$effTotalTrades, Wilson Lower: ${String.format(Locale.US, "%.2f", wilsonLower)}, CI Width: ${String.format(Locale.US, "%.2f", ciWidth)}) | ")
            append("Matrix Tier: ${matrixAdapt.tier.displayName} (Cooldown: +${matrixAdapt.cooldownAdditionalSeconds}s)")
        }

        return CalibrationBreakdown(
            rawConfidence = rawConfidence,
            calibratedConfidence = calibratedProb,
            metaConviction = metaConviction,
            strategyQuality = rawQuality,
            capitalEligibilityScore = finalCapitalEligibilityScore,
            uncertaintyLevel = uncertainty,
            wilsonLowerBound = wilsonLower,
            confidenceIntervalWidth = ciWidth,
            strategyState = state,
            rationale = rationale,
            matrixRiskMultiplier = matrixAdapt.riskMultiplier,
            matrixCooldownSeconds = matrixAdapt.cooldownAdditionalSeconds,
            matrixAdaptationTier = matrixAdapt.tier,
            modelPrediction = modelPrediction,
            historicalCalibrationFactor = historicalCalibrationFactor,
            historicalBucketWinRate = historicalBucketWinRate,
            regimePerformanceFactor = regimePerformanceFactor,
            symbolPerformanceFactor = symbolPerformanceFactor,
            strategyPerformanceFactor = strategyPerformanceFactor,
            recentRollingPerformanceFactor = recentRollingPerformanceFactor
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

    /**
     * Calibrates and adapts an individual symbol profile for Volatility Expansion Breakout strategy
     * based on empirical win rate, expectancy, and regime performance from the Learning Matrix.
     */
    fun adaptVolatilityBreakoutProfileForSymbol(
        symbol: String,
        winRatePct: Double,
        expectancyR: Double,
        totalTrades: Int,
        marketRegime: String = "TRENDING"
    ): VolatilityBreakoutProfile {
        val profile = VolatilityBreakoutProfileRegistry.getProfile(symbol)
        
        // Check Strategy Alpha Memory for independent symbol profile metrics
        val profileAlpha = StrategyAlphaMemory.getProfile(symbol, "Volatility Expansion Breakout")
        val effWR = if (profileAlpha.trades > 0) profileAlpha.winRatePct else winRatePct
        val effExp = if (profileAlpha.trades > 0) profileAlpha.expectancyR else expectancyR
        val effTrades = if (profileAlpha.trades > 0) profileAlpha.trades else totalTrades

        // Consult Regime × Symbol × Strategy Learning Matrix for progressive adaptation
        val matrixAdapt = RegimeSymbolStrategyLearningMatrix.getAdaptation(
            symbol = symbol,
            regime = marketRegime,
            strategy = "Volatility Expansion Breakout"
        )

        // Self-adaptation rules:
        // 1. If high win rate (>=55%) and positive expectancy (>=0.15R), reward with confidence bonus boost
        if ((effWR >= 55.0 && effExp >= 0.15) || matrixAdapt.tier == MatrixAdaptationTier.OPTIMAL) {
            profile.confidenceBonus = (profile.confidenceBonus + 0.01 + matrixAdapt.confidenceOffset).coerceIn(0.0, 0.14)
            profile.takeProfitMultiplier = (profile.takeProfitMultiplier + 0.10).coerceIn(2.0, 4.5)
            profile.cooldownDurationSeconds = max(60L, profile.cooldownDurationSeconds - 15L)
        } else if (matrixAdapt.isDegraded || effWR <= 41.0 || effExp <= -0.15) {
            // 2. If underperforming, apply progressive adaptation:
            // Reduce confidence bonus, tighten squeeze threshold, increase cooldown
            profile.confidenceBonus = (profile.confidenceBonus + matrixAdapt.confidenceOffset).coerceIn(-0.10, 0.12)
            profile.squeezeThreshold = (profile.squeezeThreshold * 0.95).coerceIn(0.025, 0.080)
            if (profile.breakoutConfirmationBars < 2) {
                profile.breakoutConfirmationBars = 2
            }
            val dynamicCooldown = (profile.cooldownDurationSeconds * matrixAdapt.cooldownMultiplier).toLong() + matrixAdapt.cooldownAdditionalSeconds
            profile.cooldownDurationSeconds = dynamicCooldown.coerceIn(60L, 420L)
        } else if (matrixAdapt.isRecovering) {
            // 3. Progressive recovery: Restore confidence gradually
            profile.confidenceBonus = (profile.confidenceBonus + 0.01).coerceIn(-0.02, 0.10)
            profile.cooldownDurationSeconds = max(90L, profile.cooldownDurationSeconds - 20L)
        }

        // Apply regime adaptation if specified
        if (marketRegime.isNotBlank()) {
            val currentPenalties = profile.regimePenalties.toMutableMap()
            val currentPen = currentPenalties[marketRegime] ?: 0.0
            if (effWR >= 60.0 || matrixAdapt.tier == MatrixAdaptationTier.OPTIMAL) {
                currentPenalties[marketRegime] = (currentPen + 0.01).coerceIn(-0.25, 0.15)
            } else if (effWR <= 41.0 || matrixAdapt.isDegraded) {
                currentPenalties[marketRegime] = (currentPen - 0.02).coerceIn(-0.25, 0.15)
            }
            profile.regimePenalties = currentPenalties
        }

        VolatilityBreakoutProfileRegistry.updateProfile(symbol, profile)
        return profile
    }

    /**
     * Online Stochastic Gradient Descent (SGD) Machine Learning Model for Real-Time Confidence Calibration.
     * Feature Vector X: [RawConf, MetaConviction, VolatilityRatio, StrategyEloZ, RollingWinRate, Bias]
     */
    object OnlineConfidenceMLEngine {
        // Initial Feature Weights
        private val weights = doubleArrayOf(0.80, 0.60, -0.20, 0.40, 0.50, -0.30)
        private const val learningRate = 0.04
        private const val l2Reg = 0.001
        private var totalTrainSteps = 0
        private var recentCumulativeLoss = 0.22

        fun predict(
            rawConfidence: Double,
            metaConviction: Double,
            volatilityRatio: Double = 1.0,
            eloRating: Double = 1500.0,
            winRatePct: Double = 50.0
        ): Double {
            val eloZ = (eloRating - 1500.0) / 400.0
            val wr = (winRatePct / 100.0).coerceIn(0.0, 1.0)
            val z = (rawConfidence * weights[0]) +
                    ((metaConviction / 100.0) * weights[1]) +
                    (volatilityRatio * weights[2]) +
                    (eloZ * weights[3]) +
                    (wr * weights[4]) +
                    weights[5]
            return 1.0 / (1.0 + Math.exp(-z))
        }

        fun updateOnlineSGD(
            rawConfidence: Double,
            metaConviction: Double,
            volatilityRatio: Double,
            eloRating: Double,
            winRatePct: Double,
            actualOutcomeWon: Boolean
        ) {
            val y = if (actualOutcomeWon) 1.0 else 0.0
            val pHat = predict(rawConfidence, metaConviction, volatilityRatio, eloRating, winRatePct)
            val error = y - pHat
            val loss = - (y * Math.log(pHat.coerceAtLeast(1e-6)) + (1 - y) * Math.log((1 - pHat).coerceAtLeast(1e-6)))
            recentCumulativeLoss = (recentCumulativeLoss * 0.95) + (loss * 0.05)

            val x = doubleArrayOf(
                rawConfidence,
                metaConviction / 100.0,
                volatilityRatio,
                (eloRating - 1500.0) / 400.0,
                winRatePct / 100.0,
                1.0
            )

            for (i in weights.indices) {
                weights[i] += learningRate * (error * x[i] - l2Reg * weights[i])
            }
            totalTrainSteps++
        }

        fun getModelMetrics(): Pair<Int, Double> = Pair(totalTrainSteps, recentCumulativeLoss)
    }

    /**
     * Return Distribution Shift Engine: Evaluates rolling return skewness, kurtosis, and distribution distance.
     * Detects asymmetrical fat-tail downside risk and structural regime shifts.
     */
    object ReturnDistributionShiftEngine {
        data class DistributionShiftReport(
            val isShiftDetected: Boolean,
            val skewness: Double,
            val excessKurtosis: Double,
            val wassersteinDistance: Double,
            val riskScaleFactor: Double,
            val thresholdOffset: Double,
            val rationale: String
        )

        fun evaluateShift(recentReturnsR: List<Double>, benchmarkReturnsR: List<Double> = emptyList()): DistributionShiftReport {
            if (recentReturnsR.size < 10) {
                return DistributionShiftReport(
                    isShiftDetected = false,
                    skewness = 0.0,
                    excessKurtosis = 0.0,
                    wassersteinDistance = 0.0,
                    riskScaleFactor = 1.0,
                    thresholdOffset = 0.0,
                    rationale = "Insufficient sample size (N=${recentReturnsR.size} < 10); distribution shift check bypassed."
                )
            }

            val n = recentReturnsR.size.toDouble()
            val mean = recentReturnsR.average()
            val sumSq = recentReturnsR.map { val d = it - mean; d * d }.sum()
            val variance = sumSq / n
            val stdDev = kotlin.math.sqrt(variance).coerceAtLeast(1e-5)

            // Calculate Skewness: E[(X-mu)^3] / stdDev^3
            val sumCube = recentReturnsR.map { val d = it - mean; d * d * d }.sum()
            val skewness = (sumCube / n) / (stdDev * stdDev * stdDev)

            // Calculate Excess Kurtosis: E[(X-mu)^4] / stdDev^4 - 3.0
            val sumFourth = recentReturnsR.map { val d = it - mean; d * d * d * d }.sum()
            val excessKurtosis = ((sumFourth / n) / (stdDev * stdDev * stdDev * stdDev)) - 3.0

            // Calculate 1D Wasserstein / Earth Mover's Distance if benchmark returns available
            val wassersteinDist = if (benchmarkReturnsR.size >= 10) {
                val sorted1 = recentReturnsR.sorted()
                val sorted2 = benchmarkReturnsR.sorted()
                val minLen = minOf(sorted1.size, sorted2.size)
                (0 until minLen).map { Math.abs(sorted1[it] - sorted2[it]) }.sum() / minLen.toDouble()
            } else 0.0

            val isNegativeSkewSpike = skewness < -1.0
            val isFatTailKurtosis = excessKurtosis > 3.0
            val isDistributionShift = wassersteinDist > 0.25

            val isShiftDetected = isNegativeSkewSpike || isFatTailKurtosis || isDistributionShift

            val riskScale = when {
                isNegativeSkewSpike && isFatTailKurtosis -> 0.50 // Heavy fat-tail downside risk
                isShiftDetected -> 0.70                          // Moderate distribution shift
                else -> 1.00
            }

            val thresholdOffset = when {
                isNegativeSkewSpike && isFatTailKurtosis -> +0.05
                isShiftDetected -> +0.03
                else -> 0.0
            }

            val rationale = if (isShiftDetected) {
                "⚠️ RETURN DISTRIBUTION SHIFT DETECTED: Skewness=${String.format(Locale.US, "%.2f", skewness)}, Excess Kurtosis=${String.format(Locale.US, "%.2f", excessKurtosis)}, Wasserstein Dist=${String.format(Locale.US, "%.2f", wassersteinDist)} -> Scaling risk to ${String.format(Locale.US, "%.0f%%", riskScale * 100.0)}, Threshold Offset: +${String.format(Locale.US, "%.2f", thresholdOffset)}"
            } else {
                "Return distribution stable: Skewness=${String.format(Locale.US, "%.2f", skewness)}, Excess Kurtosis=${String.format(Locale.US, "%.2f", excessKurtosis)}."
            }

            return DistributionShiftReport(
                isShiftDetected = isShiftDetected,
                skewness = skewness,
                excessKurtosis = excessKurtosis,
                wassersteinDistance = wassersteinDist,
                riskScaleFactor = riskScale,
                thresholdOffset = thresholdOffset,
                rationale = rationale
            )
        }
    }
}
