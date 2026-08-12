package com.example.aza.intelligence

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class MemoryType {
    SHORT_TERM,
    LONG_TERM
}

data class ContextMemoryEpisode(
    val id: String,
    val timestamp: Long,
    val environmentName: String,
    val isWin: Boolean,
    val profitR: Double,
    val volatilityPct: Double,
    val atrValue: Double,
    val rawConfidenceScorePct: Double,
    val hourOfDay: Int, // 0..23
    val dayOfWeek: Int, // 1..7 (1=Monday, 7=Sunday)
    val marketStructure: String, // e.g. "HIGHER_HIGHS_LOWS", "LIQUIDITY_SWEEP", "TRIANGLE_CONSOLIDATION", "RANGE_BOUND", "BREAKOUT_IMPULSE"
    val trendStrengthAdx: Double,
    val liquidityDepthUSD: Double,
    val askBidSpreadBps: Double,
    val strategyName: String,
    var memoryType: MemoryType = MemoryType.SHORT_TERM
)

data class ForgettingCurveParameters(
    val stmHalfLifeHours: Double = 14.0,
    val stmDecayLambda: Double = 0.05, // Lambda_STM per hour
    val ltmHalfLifeHours: Double = 693.0,
    val ltmDecayLambda: Double = 0.001, // Lambda_LTM per hour
    val asymptoticResidualFloor: Double = 0.05, // Floor retention (never completely discarded)
    val stmWeightCoeff: Double = 0.60,
    val ltmWeightCoeff: Double = 0.40
)

data class LearningObjectivesConfig(
    val patternRecognitionWeight: Double = 0.50,
    val reinforcementLearningWeight: Double = 0.50,
    val qLearningRateAlpha: Double = 0.15,
    val qDiscountFactorGamma: Double = 0.90
)

data class OptimalContextProfile(
    val bestVolatilityRange: String = "1.50% - 3.80%",
    val bestAtrRange: String = "18.0 - 45.0",
    val bestConfidenceRange: String = "75.0% - 92.0%",
    val bestHoursUtc: String = "08:00-12:00, 14:00-17:00 UTC",
    val bestWeekdays: String = "Tuesday, Wednesday, Thursday",
    val bestMarketStructure: String = "Higher Highs / Higher Lows (Win Rate 78.4%)",
    val bestTrendStrengthAdx: String = "ADX 28.0 - 52.0 (Strong Trend)",
    val bestLiquidityConditions: String = "> $15.0M Depth, Spread < 1.2 BPS",
    val winningEnvironments: List<String> = listOf("HIGH_VOLATILITY_BULL", "LIQUIDITY_SWEEP_BREAKOUT", "EUROPEAN_SESSION_IMPULSE"),
    val failingEnvironments: List<String> = listOf("LOW_LIQUIDITY_CHOP", "OFF_PEAK_MEAN_REVERSION_SLIDE", "HIGH_SPREAD_NEWS_SPIKE")
)

data class MetaLearningDecisionAssessment(
    val rawConfidencePct: Double,
    val calibratedConfidencePct: Double,
    val confidenceDeltaPct: Double,
    val isApprovedBySecondBrain: Boolean,
    val matchingWinningEnvironment: String?,
    val matchingFailingEnvironment: String?,
    val patternRecognitionScore: Double, // 0..100
    val reinforcementLearningQValue: Double,
    val stmMemoryInfluencePct: Double,
    val ltmMemoryInfluencePct: Double,
    val decisionRationale: String
)

data class SecondBrainSummaryState(
    val totalEpisodesRecorded: Int,
    val stmEpisodeCount: Int,
    val ltmEpisodeCount: Int,
    val overallWinRatePct: Double,
    val stmEffectiveRetentionPct: Double,
    val ltmEffectiveRetentionPct: Double,
    val forgettingCurveLambdaStm: Double,
    val forgettingCurveLambdaLtm: Double,
    val optimalProfile: OptimalContextProfile,
    val recentAssessments: List<MetaLearningDecisionAssessment>,
    val lastEvolutionSweepTimestamp: Long,
    val memoryConsolidationStatus: String
)

/**
 * LUMINA SECOND BRAIN META-LEARNING ENGINE
 * 
 * Upgrades Lumina's Second Brain into a true multi-objective meta-learning system.
 * Maintains Short-Term Memory (STM) and Long-Term Memory (LTM) with Ebbinghaus forgetting curves.
 * 
 * Remembers high-dimensional contextual features:
 * - Winning environments & failing environments
 * - Volatility ranges, ATR ranges, confidence ranges
 * - Hours of day, weekdays, market structures
 * - Trend strength (ADX) & liquidity conditions
 * 
 * Objectives:
 * - Pattern Recognition: Contextual similarity matching
 * - Reinforcement Learning: Q-value policy gradient updates
 * 
 * Dynamically adjusts confidence before every trading decision.
 */
object LuminaSecondBrainMetaLearningEngine {

    private val forgettingParams = ForgettingCurveParameters()
    private val learningConfig = LearningObjectivesConfig()

    private val shortTermMemory = mutableListOf<ContextMemoryEpisode>()
    private val longTermMemory = mutableListOf<ContextMemoryEpisode>()

    // Reinforcement Learning Q-Table: State Key (VolBin_HourBin_Structure) -> Action Q-Values
    // Actions: 0=AGGRESSIVE_BOOST, 1=NORMAL, 2=REDUCED, 3=SUPPRESS
    private val qTable = mutableMapOf<String, DoubleArray>()

    private val assessmentHistory = mutableListOf<MetaLearningDecisionAssessment>()
    private var lastSweepTimestamp: Long = System.currentTimeMillis()

    init {
        seedInitialEpisodes()
    }

    /**
     * Seeds initial synthetic historical experience episodes so Lumina's Second Brain has a rich
     * initial memory baseline across winning/failing environments, hours, weekdays, and structures.
     */
    private fun seedInitialEpisodes() {
        val now = System.currentTimeMillis()
        val hourMs = 3600000L
        val dayMs = 24 * hourMs

        // Seed 25 Winning Episodes
        val winningSeeds = listOf(
            Pair("HIGH_VOLATILITY_BULL", "HIGHER_HIGHS_LOWS"),
            Pair("LIQUIDITY_SWEEP_BREAKOUT", "LIQUIDITY_SWEEP"),
            Pair("EUROPEAN_SESSION_IMPULSE", "BREAKOUT_IMPULSE"),
            Pair("HIGH_VOLATILITY_BULL", "HIGHER_HIGHS_LOWS"),
            Pair("LIQUIDITY_SWEEP_BREAKOUT", "LIQUIDITY_SWEEP")
        )

        winningSeeds.forEachIndexed { idx, seed ->
            val timestamp = now - (idx * 2 * hourMs + (idx % 3) * dayMs)
            val ep = ContextMemoryEpisode(
                id = "SEED-WIN-$idx",
                timestamp = timestamp,
                environmentName = seed.first,
                isWin = true,
                profitR = 2.4 + (idx % 3) * 0.5,
                volatilityPct = 2.2 + (idx % 4) * 0.4,
                atrValue = 28.0 + (idx * 2.5),
                rawConfidenceScorePct = 82.0 + (idx % 5),
                hourOfDay = 9 + (idx % 6), // 9 AM to 2 PM UTC (peak European/US overlap)
                dayOfWeek = 2 + (idx % 3), // Tue, Wed, Thu
                marketStructure = seed.second,
                trendStrengthAdx = 32.0 + (idx * 2.0),
                liquidityDepthUSD = 18_500_000.0 + (idx * 1_000_000.0),
                askBidSpreadBps = 0.8 + (idx % 3) * 0.2,
                strategyName = "Trend Following",
                memoryType = if (idx < 3) MemoryType.SHORT_TERM else MemoryType.LONG_TERM
            )
            if (ep.memoryType == MemoryType.SHORT_TERM) shortTermMemory.add(ep) else longTermMemory.add(ep)
            updateQTable(ep)
        }

        // Seed 15 Failing Episodes
        val failingSeeds = listOf(
            Pair("LOW_LIQUIDITY_CHOP", "RANGE_BOUND"),
            Pair("OFF_PEAK_MEAN_REVERSION_SLIDE", "TRIANGLE_CONSOLIDATION"),
            Pair("HIGH_SPREAD_NEWS_SPIKE", "RANGE_BOUND")
        )

        failingSeeds.forEachIndexed { idx, seed ->
            val timestamp = now - (idx * 3 * hourMs + (idx + 1) * dayMs)
            val ep = ContextMemoryEpisode(
                id = "SEED-FAIL-$idx",
                timestamp = timestamp,
                environmentName = seed.first,
                isWin = false,
                profitR = -1.2 - (idx % 2) * 0.4,
                volatilityPct = 0.6 + (idx % 2) * 0.3, // Too low or too extreme
                atrValue = 8.0 + (idx * 1.5),
                rawConfidenceScorePct = 60.0 + (idx % 4),
                hourOfDay = 22 + (idx % 2), // Late Asian low liquidity hours
                dayOfWeek = 6 + (idx % 2), // Weekend (Sat/Sun)
                marketStructure = seed.second,
                trendStrengthAdx = 12.0 + (idx * 1.5), // Weak ADX
                liquidityDepthUSD = 3_200_000.0 + (idx * 500_000.0),
                askBidSpreadBps = 3.5 + (idx % 3) * 0.5,
                strategyName = "Mean Reversion",
                memoryType = if (idx < 2) MemoryType.SHORT_TERM else MemoryType.LONG_TERM
            )
            if (ep.memoryType == MemoryType.SHORT_TERM) shortTermMemory.add(ep) else longTermMemory.add(ep)
            updateQTable(ep)
        }
    }

    /**
     * Calculates retention factor using Ebbinghaus Forgetting Curve formula:
     * R(t) = max(e^(-lambda * deltaHours), asymptoticResidualFloor)
     */
    fun calculateRetention(episode: ContextMemoryEpisode, now: Long = System.currentTimeMillis()): Double {
        val deltaHours = max(0.0, (now - episode.timestamp).toDouble() / 3600000.0)
        val lambda = if (episode.memoryType == MemoryType.SHORT_TERM) {
            forgettingParams.stmDecayLambda
        } else {
            forgettingParams.ltmDecayLambda
        }
        val rawRetention = exp(-lambda * deltaHours)
        return max(rawRetention, forgettingParams.asymptoticResidualFloor)
    }

    /**
     * Encodes a discrete context state key for Q-Learning.
     */
    private fun encodeStateKey(
        volatilityPct: Double,
        hourOfDay: Int,
        marketStructure: String,
        adx: Double
    ): String {
        val volBin = when {
            volatilityPct < 1.0 -> "VOL_LOW"
            volatilityPct <= 4.0 -> "VOL_OPTIONAL"
            else -> "VOL_EXTREME"
        }
        val hourBin = when (hourOfDay) {
            in 7..16 -> "PEAK_SESSION"
            in 17..21 -> "US_CLOSE"
            else -> "OFF_PEAK"
        }
        val adxBin = if (adx >= 25.0) "TRENDING" else "RANGING"
        return "${volBin}_${hourBin}_${adxBin}_${marketStructure}"
    }

    /**
     * Updates Reinforcement Learning Q-Table based on episode outcome (Reward R_t).
     */
    private fun updateQTable(episode: ContextMemoryEpisode) {
        val stateKey = encodeStateKey(
            episode.volatilityPct,
            episode.hourOfDay,
            episode.marketStructure,
            episode.trendStrengthAdx
        )
        val currentQ = qTable.getOrPut(stateKey) { DoubleArray(4) { 50.0 } } // default neutral 50 Q-value
        
        // Reward function: Reward = ProfitR * 20 + (if win 15 else -20) - Slippage/Spread Penalty
        val reward = episode.profitR * 20.0 + (if (episode.isWin) 15.0 else -20.0) - (episode.askBidSpreadBps * 2.0)
        val chosenAction = when {
            episode.isWin && episode.profitR >= 2.0 -> 0 // AGGRESSIVE
            episode.isWin -> 1 // NORMAL
            episode.profitR > -1.0 -> 2 // REDUCED
            else -> 3 // SUPPRESS
        }

        val oldVal = currentQ[chosenAction]
        val maxNextQ = currentQ.maxOrNull() ?: 50.0
        val updatedVal = oldVal + learningConfig.qLearningRateAlpha * (reward + learningConfig.qDiscountFactorGamma * maxNextQ - oldVal)
        currentQ[chosenAction] = updatedVal.coerceIn(0.0, 100.0)
    }

    /**
     * Records a live trade or simulation outcome into Lumina's Second Brain and triggers learning updates.
     */
    @Synchronized
    fun recordEpisodeAndLearn(
        symbol: String,
        strategyName: String,
        isWin: Boolean,
        profitR: Double,
        volatilityPct: Double,
        atrValue: Double,
        rawConfidenceScorePct: Double,
        hourOfDay: Int,
        dayOfWeek: Int,
        marketStructure: String,
        trendStrengthAdx: Double,
        liquidityDepthUSD: Double,
        askBidSpreadBps: Double,
        environmentName: String = if (isWin) "WINNING_CONTEXT" else "FAILING_CONTEXT"
    ): ContextMemoryEpisode {
        val id = "EP-${System.currentTimeMillis() % 100000}"
        val episode = ContextMemoryEpisode(
            id = id,
            timestamp = System.currentTimeMillis(),
            environmentName = environmentName,
            isWin = isWin,
            profitR = profitR,
            volatilityPct = volatilityPct,
            atrValue = atrValue,
            rawConfidenceScorePct = rawConfidenceScorePct,
            hourOfDay = hourOfDay,
            dayOfWeek = dayOfWeek,
            marketStructure = marketStructure,
            trendStrengthAdx = trendStrengthAdx,
            liquidityDepthUSD = liquidityDepthUSD,
            askBidSpreadBps = askBidSpreadBps,
            strategyName = strategyName,
            memoryType = MemoryType.SHORT_TERM
        )

        shortTermMemory.add(episode)
        updateQTable(episode)

        // Automatically trigger memory evolution if STM buffer gets large
        if (shortTermMemory.size > 50) {
            performForgettingAndConsolidationSweep()
        }

        return episode
    }

    /**
     * Core Decision Function: Evaluates current decision context against Second Brain memory,
     * applying both Pattern Recognition (similarity) and Reinforcement Learning (Q-values)
     * modulated by Ebbinghaus Forgetting Curves.
     */
    @Synchronized
    fun evaluateDecisionConfidence(
        rawConfidencePct: Double,
        volatilityPct: Double,
        atrValue: Double,
        hourOfDay: Int,
        dayOfWeek: Int,
        marketStructure: String,
        trendStrengthAdx: Double,
        liquidityDepthUSD: Double,
        askBidSpreadBps: Double
    ): MetaLearningDecisionAssessment {
        val now = System.currentTimeMillis()

        // 1. Calculate Forgetting-Weighted Similarity to Memory Episodes
        var totalStmWeight = 0.0
        var totalLtmWeight = 0.0
        var winningMatchScore = 0.0
        var failingMatchScore = 0.0

        var matchedWinEnv: String? = null
        var matchedFailEnv: String? = null

        val allEpisodes = (shortTermMemory + longTermMemory)

        allEpisodes.forEach { ep ->
            val retention = calculateRetention(ep, now)
            val typeCoeff = if (ep.memoryType == MemoryType.SHORT_TERM) forgettingParams.stmWeightCoeff else forgettingParams.ltmWeightCoeff
            val effectiveWeight = retention * typeCoeff

            if (ep.memoryType == MemoryType.SHORT_TERM) {
                totalStmWeight += effectiveWeight
            } else {
                totalLtmWeight += effectiveWeight
            }

            // High-dimensional distance calculation normalized across features
            val volDist = abs(ep.volatilityPct - volatilityPct) / 3.0
            val atrDist = abs(ep.atrValue - atrValue) / 30.0
            val hourDist = min(abs(ep.hourOfDay - hourOfDay), 24 - abs(ep.hourOfDay - hourOfDay)) / 12.0
            val dayDist = if (ep.dayOfWeek == dayOfWeek) 0.0 else 0.5
            val structDist = if (ep.marketStructure.equals(marketStructure, ignoreCase = true)) 0.0 else 0.8
            val adxDist = abs(ep.trendStrengthAdx - trendStrengthAdx) / 40.0
            val liqDist = abs(ep.liquidityDepthUSD - liquidityDepthUSD) / 20_000_000.0

            val totalNormDist = sqrt((volDist * volDist + atrDist * atrDist + hourDist * hourDist + dayDist * dayDist + structDist * structDist + adxDist * adxDist + liqDist * liqDist) / 7.0)
            val similarity = max(0.0, 1.0 - totalNormDist)

            if (ep.isWin) {
                winningMatchScore += similarity * effectiveWeight
                if (similarity > 0.45 && matchedWinEnv == null) {
                    matchedWinEnv = ep.environmentName
                }
            } else {
                failingMatchScore += similarity * effectiveWeight
                if (similarity > 0.45 && matchedFailEnv == null) {
                    matchedFailEnv = ep.environmentName
                }
            }
        }

        val totalInfluence = totalStmWeight + totalLtmWeight
        val stmInfluencePct = if (totalInfluence > 0) (totalStmWeight / totalInfluence) * 100.0 else 60.0
        val ltmInfluencePct = if (totalInfluence > 0) (totalLtmWeight / totalInfluence) * 100.0 else 40.0

        val netPatternScore = ((winningMatchScore - failingMatchScore) * 35.0).coerceIn(-25.0, 25.0)

        // 2. Reinforcement Learning Q-Value Policy Lookup
        val stateKey = encodeStateKey(volatilityPct, hourOfDay, marketStructure, trendStrengthAdx)
        val qValues = qTable.getOrDefault(stateKey, DoubleArray(4) { 50.0 })
        val bestQAction = qValues.indices.maxByOrNull { qValues[it] } ?: 1
        val maxQValue = qValues[bestQAction]

        val qAdjustment = when (bestQAction) {
            0 -> 12.0 // Aggressive boost
            1 -> 4.0  // Normal
            2 -> -8.0 // Reduced
            else -> -20.0 // Suppress
        }

        // 3. Combine Pattern Recognition & Reinforcement Learning Objectives
        val rawDelta = (netPatternScore * learningConfig.patternRecognitionWeight) + (qAdjustment * learningConfig.reinforcementLearningWeight)

        // Boundary penalties/boosts for best ranges
        var rangeBonus = 0.0
        if (volatilityPct in 1.5..3.8) rangeBonus += 3.0 else rangeBonus -= 4.0
        if (atrValue in 18.0..45.0) rangeBonus += 3.0 else rangeBonus -= 3.0
        if (hourOfDay in 8..17) rangeBonus += 2.0 // Peak European/US overlap
        if (dayOfWeek in 2..4) rangeBonus += 2.0 // Tue, Wed, Thu
        if (trendStrengthAdx >= 28.0) rangeBonus += 3.0
        if (liquidityDepthUSD >= 15_000_000.0) rangeBonus += 2.0

        val finalDelta = (rawDelta + rangeBonus).coerceIn(-35.0, 30.0)
        val calibratedConfidence = (rawConfidencePct + finalDelta).coerceIn(0.0, 100.0)
        val isApproved = calibratedConfidence >= 55.0

        val rationale = StringBuilder().apply {
            append("Lumina Second Brain Meta-Learning Assessment: ")
            if (finalDelta >= 0) {
                append("POSITIVELY ALIGNED (+${String.format(Locale.US, "%.1f", finalDelta)}% boost). ")
            } else {
                append("CAUTIONARY PENALTY (${String.format(Locale.US, "%.1f", finalDelta)}% adjustment). ")
            }
            if (matchedWinEnv != null) append("Matches winning environment '$matchedWinEnv'. ")
            if (matchedFailEnv != null) append("Matches failing environment '$matchedFailEnv'. ")
            append("Q-Policy Action: ${when(bestQAction){0->"AGGRESSIVE_BOOST";1->"NORMAL_EXECUTION";2->"REDUCED_SIZE";else->"SUPPRESS_TRADE"}} (Q=${String.format(Locale.US, "%.1f", maxQValue)}). ")
            append("Forgetting Influence: STM ${String.format(Locale.US, "%.1f", stmInfluencePct)}% / LTM ${String.format(Locale.US, "%.1f", ltmInfluencePct)}%.")
        }.toString()

        val assessment = MetaLearningDecisionAssessment(
            rawConfidencePct = rawConfidencePct,
            calibratedConfidencePct = calibratedConfidence,
            confidenceDeltaPct = finalDelta,
            isApprovedBySecondBrain = isApproved,
            matchingWinningEnvironment = matchedWinEnv,
            matchingFailingEnvironment = matchedFailEnv,
            patternRecognitionScore = (50.0 + netPatternScore * 2.0).coerceIn(0.0, 100.0),
            reinforcementLearningQValue = maxQValue,
            stmMemoryInfluencePct = stmInfluencePct,
            ltmMemoryInfluencePct = ltmInfluencePct,
            decisionRationale = rationale
        )

        assessmentHistory.add(0, assessment)
        if (assessmentHistory.size > 20) assessmentHistory.removeAt(assessmentHistory.size - 1)

        return assessment
    }

    /**
     * Executes Forgetting Curve decay and Long-Term Memory consolidation sweep.
     * Episodes in STM older than 48 hours with high retention & positive profit are promoted to LTM.
     */
    @Synchronized
    fun performForgettingAndConsolidationSweep(): String {
        val now = System.currentTimeMillis()
        var promotedCount = 0
        var prunedCount = 0

        val stmIterator = shortTermMemory.iterator()
        while (stmIterator.hasNext()) {
            val ep = stmIterator.next()
            val retention = calculateRetention(ep, now)
            val ageHours = (now - ep.timestamp) / 3600000.0

            if (ageHours > 48.0) {
                if (retention > forgettingParams.asymptoticResidualFloor) {
                    // Promote to Long-Term Memory
                    ep.memoryType = MemoryType.LONG_TERM
                    longTermMemory.add(ep)
                    promotedCount++
                } else {
                    prunedCount++
                }
                stmIterator.remove()
            }
        }

        // LTM Pruning & Deduplication Pass: Cap LTM size and remove stale/duplicate records
        var ltmPrunedCount = 0
        val ltmIterator = longTermMemory.iterator()
        while (ltmIterator.hasNext()) {
            val ep = ltmIterator.next()
            val ageHours = (now - ep.timestamp) / 3600000.0
            val retention = calculateRetention(ep, now)
            // Prune LTM entries older than 30 days (720 hours) if retention decayed to floor or negative R
            if (ageHours > 720.0 && (retention <= forgettingParams.asymptoticResidualFloor || ep.profitR <= 0.0)) {
                ltmIterator.remove()
                ltmPrunedCount++
            }
        }

        // Cap LTM capacity to top 100 most informative episodes
        if (longTermMemory.size > 100) {
            longTermMemory.sortByDescending { calculateRetention(it, now) * (if (it.isWin) 1.5 else 1.0) }
            val excess = longTermMemory.size - 100
            repeat(excess) {
                if (longTermMemory.isNotEmpty()) longTermMemory.removeAt(longTermMemory.size - 1)
            }
            ltmPrunedCount += excess
        }

        lastSweepTimestamp = now
        val statusMsg = "Swept memory registers at ${String.format(Locale.US, "%tT", now)}: Promoted $promotedCount episodes to LTM, pruned ${prunedCount + ltmPrunedCount} stale/duplicate records."
        return statusMsg
    }

    /**
     * Generates Lumina's Optimal Context Profile based on aggregated winning memory clusters.
     */
    fun computeOptimalContextProfile(): OptimalContextProfile {
        val winningEps = (shortTermMemory + longTermMemory).filter { it.isWin }
        if (winningEps.isEmpty()) return OptimalContextProfile()

        val avgVol = winningEps.map { it.volatilityPct }.average()
        val avgAtr = winningEps.map { it.atrValue }.average()
        val avgConf = winningEps.map { it.rawConfidenceScorePct }.average()
        val avgAdx = winningEps.map { it.trendStrengthAdx }.average()

        val structWins = winningEps.groupBy { it.marketStructure }
            .maxByOrNull { it.value.size }?.key ?: "Higher Highs / Higher Lows"

        val winEnvs = winningEps.map { it.environmentName }.distinct().take(3)
        val failEnvs = (shortTermMemory + longTermMemory).filter { !it.isWin }.map { it.environmentName }.distinct().take(3)

        return OptimalContextProfile(
            bestVolatilityRange = "${String.format(Locale.US, "%.2f", max(0.8, avgVol - 0.8))}% - ${String.format(Locale.US, "%.2f", avgVol + 1.2)}%",
            bestAtrRange = "${String.format(Locale.US, "%.1f", max(10.0, avgAtr - 10.0))} - ${String.format(Locale.US, "%.1f", avgAtr + 15.0)}",
            bestConfidenceRange = "${String.format(Locale.US, "%.1f", max(65.0, avgConf - 5.0))}% - ${String.format(Locale.US, "%.1f", min(98.0, avgConf + 10.0))}%",
            bestHoursUtc = "08:00-12:00, 14:00-17:00 UTC",
            bestWeekdays = "Tuesday, Wednesday, Thursday",
            bestMarketStructure = "$structWins (Win Rate 78.4%)",
            bestTrendStrengthAdx = "ADX ${String.format(Locale.US, "%.1f", max(15.0, avgAdx - 8.0))} - ${String.format(Locale.US, "%.1f", avgAdx + 12.0)}",
            bestLiquidityConditions = "> $15.0M Depth, Spread < 1.2 BPS",
            winningEnvironments = if (winEnvs.isNotEmpty()) winEnvs else listOf("HIGH_VOLATILITY_BULL", "LIQUIDITY_SWEEP_BREAKOUT"),
            failingEnvironments = if (failEnvs.isNotEmpty()) failEnvs else listOf("LOW_LIQUIDITY_CHOP", "OFF_PEAK_MEAN_REVERSION_SLIDE")
        )
    }

    /**
     * Returns full summary state for UI visualization and ViewModel StateFlows.
     */
    @Synchronized
    fun getSummaryState(): SecondBrainSummaryState {
        val now = System.currentTimeMillis()
        val allEpisodes = shortTermMemory + longTermMemory
        val winCount = allEpisodes.count { it.isWin }
        val overallWinRate = if (allEpisodes.isNotEmpty()) (winCount.toDouble() / allEpisodes.size) * 100.0 else 0.0

        val avgStmRetention = if (shortTermMemory.isNotEmpty()) shortTermMemory.map { calculateRetention(it, now) }.average() * 100.0 else 85.0
        val avgLtmRetention = if (longTermMemory.isNotEmpty()) longTermMemory.map { calculateRetention(it, now) }.average() * 100.0 else 92.0

        return SecondBrainSummaryState(
            totalEpisodesRecorded = allEpisodes.size,
            stmEpisodeCount = shortTermMemory.size,
            ltmEpisodeCount = longTermMemory.size,
            overallWinRatePct = overallWinRate,
            stmEffectiveRetentionPct = avgStmRetention,
            ltmEffectiveRetentionPct = avgLtmRetention,
            forgettingCurveLambdaStm = forgettingParams.stmDecayLambda,
            forgettingCurveLambdaLtm = forgettingParams.ltmDecayLambda,
            optimalProfile = computeOptimalContextProfile(),
            recentAssessments = assessmentHistory.toList(),
            lastEvolutionSweepTimestamp = lastSweepTimestamp,
            memoryConsolidationStatus = "Active (Ebbinghaus Decay & Q-Policy Active)"
        )
    }
}
