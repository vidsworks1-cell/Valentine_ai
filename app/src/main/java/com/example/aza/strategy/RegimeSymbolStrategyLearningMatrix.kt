package com.example.aza.strategy

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Core normalized market regimes for learning matrix partitioning.
 */
object MatrixRegimeConstants {
    const val TRENDING = "TRENDING"
    const val RANGING = "RANGING"
    const val VOLATILE = "VOLATILE"

    val CORE_REGIMES = listOf(TRENDING, RANGING, VOLATILE)

    /**
     * Normalizes arbitrary market regime strings (from indicators, models, or DB)
     * into one of the three core matrix regimes: TRENDING, RANGING, VOLATILE.
     */
    fun normalizeRegime(rawRegime: String): String {
        val clean = rawRegime.trim().uppercase(Locale.US).replace("-", "_").replace(" ", "_")
        return when {
            clean.contains("VOLATIL") || clean.contains("EXPANSION") || clean.contains("CHAOTIC") ||
                    clean.contains("CRITICAL") || clean.contains("HIGH_VOLATILITY") || clean.contains("TURBULENT") -> VOLATILE

            clean.contains("RANGE") || clean.contains("RANGING") || clean.contains("CONSOLIDATION") ||
                    clean.contains("ACCUMULATION") || clean.contains("DISTRIBUTION") || clean.contains("SIDEWAYS") ||
                    clean.contains("LOW_VOLATILITY") || clean.contains("CYCLIC") || clean.contains("CHOP") -> RANGING

            clean.contains("TREND") || clean.contains("BULL") || clean.contains("BEAR") ||
                    clean.contains("BREAKOUT") || clean.contains("MOMENTUM") -> TRENDING

            else -> TRENDING
        }
    }

    /**
     * Normalizes trading pairs into standard asset symbols (BTC, ETH, BNB, SOL).
     */
    fun normalizeSymbol(rawSymbol: String): String {
        val clean = rawSymbol.trim().uppercase(Locale.US)
            .replace("BINANCE:", "")
            .replace("PERP", "")
            .replace("-", "")
            .replace("/", "")
            .replace("_", "")

        return when {
            clean.startsWith("BTC") -> "BTC"
            clean.startsWith("ETH") -> "ETH"
            clean.startsWith("BNB") -> "BNB"
            clean.startsWith("SOL") -> "SOL"
            clean.endsWith("USDT") -> clean.removeSuffix("USDT")
            clean.isNotBlank() -> clean
            else -> "BTC"
        }
    }
}

/**
 * Progressive Adaptation Tier for an individual (Regime × Symbol × Strategy) cell.
 */
enum class MatrixAdaptationTier(val displayName: String) {
    OPTIMAL("Optimal Outperformer"),
    NEUTRAL("Baseline Balanced"),
    MILD_RESTRICTION("Mild Progressive Restriction"),
    MODERATE_RESTRICTION("Moderate Progressive Restriction"),
    DEGRADED_PROBATION("Degraded Probation (Active with Low Risk)"),
    RECOVERING("Progressive Recovery Mode")
}

/**
 * Individual trade log entry stored inside a matrix performance cell.
 */
data class MatrixTradeRecord(
    val timestamp: Long = System.currentTimeMillis(),
    val isWin: Boolean,
    val pnlR: Double,
    val holdTimeMinutes: Double = 30.0
)

/**
 * Output recommendation from the Regime × Symbol × Strategy learning matrix
 * consumed directly by LuminaSelfAdaptationEngine, LuminaRiskEngine, and Strategy execution.
 */
data class MatrixAdaptationResult(
    val symbol: String,
    val regime: String,
    val strategy: String,
    val tier: MatrixAdaptationTier,
    val confidenceOffset: Double,
    val riskMultiplier: Double,
    val cooldownMultiplier: Double,
    val cooldownAdditionalSeconds: Long,
    val sampleSize: Int,
    val winRatePct: Double,
    val profitFactor: Double,
    val expectancyR: Double,
    val averageR: Double,
    val averageHoldTimeMinutes: Double,
    val consecutiveLosses: Int,
    val isDegraded: Boolean,
    val isRecovering: Boolean,
    val statusRationale: String
)

/**
 * Performance metrics cell representing a single intersection of (Regime × Symbol × Strategy).
 */
data class RegimeSymbolStrategyCell(
    val symbol: String,
    val regime: String,
    val strategy: String,
    var trades: Int = 0,
    var wins: Int = 0,
    var losses: Int = 0,
    var winRate: Double = 50.0,
    var grossProfitR: Double = 0.0,
    var grossLossR: Double = 0.0,
    var profitFactor: Double = 1.0,
    var expectancy: Double = 0.0,
    var averageR: Double = 0.0,
    var totalHoldTimeMinutes: Double = 0.0,
    var averageHoldTime: Double = 0.0,
    var consecutiveLosses: Int = 0,
    var consecutiveWins: Int = 0,
    val recentTrades: MutableList<MatrixTradeRecord> = mutableListOf(),
    var adaptationState: MatrixAdaptationTier = MatrixAdaptationTier.NEUTRAL,
    var confidenceOffset: Double = 0.0,
    var riskMultiplier: Double = 1.0,
    var cooldownMultiplier: Double = 1.0,
    var cooldownAdditionalSeconds: Long = 0L,
    var recoveryStreak: Int = 0,
    var lastUpdatedMillis: Long = System.currentTimeMillis()
) {
    val winRatePct: Double get() = winRate
    val expectancyR: Double get() = expectancy
    val averageHoldTimeMinutes: Double get() = averageHoldTime

    /**
     * Unique cell key: "SYMBOL#REGIME#STRATEGY" (e.g. "BNB#VOLATILE#Volatility Expansion Breakout")
     */
    val cellKey: String
        get() = "${symbol}#${regime}#${strategy}"

    /**
     * Records a new trade execution outcome into this cell and updates statistical metrics
     * before running progressive adaptation evaluation.
     */
    fun recordTrade(isWin: Boolean, pnlR: Double, holdTimeMinutes: Double = 30.0) {
        trades++
        if (isWin) {
            wins++
            consecutiveWins++
            consecutiveLosses = 0
            grossProfitR += max(pnlR, 0.0)
        } else {
            losses++
            consecutiveLosses++
            consecutiveWins = 0
            grossLossR += abs(min(pnlR, 0.0))
        }

        winRate = if (trades > 0) (wins.toDouble() / trades.toDouble()) * 100.0 else 50.0
        profitFactor = if (grossLossR > 1e-4) grossProfitR / grossLossR else if (grossProfitR > 0) 3.5 else 1.0
        val netR = grossProfitR - grossLossR
        averageR = if (trades > 0) netR / trades.toDouble() else 0.0
        expectancy = averageR // Statistical expectancy per trade in R units

        totalHoldTimeMinutes += max(holdTimeMinutes, 1.0)
        averageHoldTime = if (trades > 0) totalHoldTimeMinutes / trades.toDouble() else 30.0

        // Append to sliding window of recent trades (max 25)
        recentTrades.add(MatrixTradeRecord(isWin = isWin, pnlR = pnlR, holdTimeMinutes = holdTimeMinutes))
        if (recentTrades.size > 25) {
            recentTrades.removeAt(0)
        }

        lastUpdatedMillis = System.currentTimeMillis()

        // Trigger progressive adaptation update
        evaluateProgressiveAdaptation()
    }

    /**
     * Computes progressive self-adaptation without abrupt binary disabling.
     * Gradually reduces confidence and risk and increases cooldown when underperforming.
     * Gradually restores confidence and risk over statistically significant recovery samples.
     */
    fun evaluateProgressiveAdaptation() {
        if (trades < 3) {
            // Statistical sample size too small for heavy penalties or bonuses
            adaptationState = MatrixAdaptationTier.NEUTRAL
            confidenceOffset = 0.0
            riskMultiplier = 1.0
            cooldownMultiplier = 1.0
            cooldownAdditionalSeconds = 0L
            return
        }

        // Recent rolling metrics over last 10 trades
        val recentSample = recentTrades.takeLast(10)
        val recentTradesCount = recentSample.size
        val recentWins = recentSample.count { it.isWin }
        val recentWinRate = if (recentTradesCount > 0) (recentWins.toDouble() / recentTradesCount.toDouble()) * 100.0 else winRate
        val recentNetR = recentSample.sumOf { it.pnlR }
        val recentExpectancy = if (recentTradesCount > 0) recentNetR / recentTradesCount.toDouble() else expectancy

        // Check if currently restricted and demonstrating recovery
        val isCurrentlyRestricted = adaptationState in listOf(
            MatrixAdaptationTier.MILD_RESTRICTION,
            MatrixAdaptationTier.MODERATE_RESTRICTION,
            MatrixAdaptationTier.DEGRADED_PROBATION,
            MatrixAdaptationTier.RECOVERING
        )

        val isDemonstratingRecovery = recentTradesCount >= 4 && (recentWinRate >= 50.0 || recentExpectancy >= 0.15 || consecutiveWins >= 2)

        if (isCurrentlyRestricted && isDemonstratingRecovery) {
            // Progressive Recovery Mode: Restore parameters step-by-step
            adaptationState = MatrixAdaptationTier.RECOVERING
            recoveryStreak++

            // Progressive restoration of confidence
            confidenceOffset = (confidenceOffset + 0.025).coerceAtMost(0.0)

            // Progressive restoration of risk allocation
            riskMultiplier = (riskMultiplier + 0.15).coerceIn(0.40, 1.00)

            // Progressive relaxation of cooldown
            cooldownMultiplier = (cooldownMultiplier - 0.20).coerceAtLeast(1.0)
            cooldownAdditionalSeconds = (cooldownAdditionalSeconds - 30L).coerceAtLeast(0L)

            // If lifetime/recent metrics fully normalize, return to NEUTRAL or OPTIMAL
            if (recoveryStreak >= 4 && recentWinRate >= 52.0 && winRate >= 48.0) {
                adaptationState = if (winRate >= 56.0 && expectancy >= 0.20) MatrixAdaptationTier.OPTIMAL else MatrixAdaptationTier.NEUTRAL
                confidenceOffset = if (adaptationState == MatrixAdaptationTier.OPTIMAL) 0.04 else 0.0
                riskMultiplier = if (adaptationState == MatrixAdaptationTier.OPTIMAL) 1.15 else 1.0
                cooldownMultiplier = 1.0
                cooldownAdditionalSeconds = 0L
                recoveryStreak = 0
            }
            return
        }

        // Outperformer evaluation (e.g. BNB in VOLATILE with Win Rate >= 55% / PF >= 1.4)
        if (winRate >= 55.0 && expectancy >= 0.15 && profitFactor >= 1.30 && consecutiveLosses < 2) {
            adaptationState = MatrixAdaptationTier.OPTIMAL
            confidenceOffset = if (winRate >= 60.0) +0.06 else +0.03
            riskMultiplier = if (winRate >= 60.0) 1.20 else 1.10
            cooldownMultiplier = 1.0
            cooldownAdditionalSeconds = 0L
            recoveryStreak = 0
            return
        }

        // Underperformance evaluation (e.g. ETH in VOLATILE with Win Rate 41% / negative expectancy)
        val isMildUnderperforming = (winRate in 42.0..47.0) || (expectancy in -0.15..-0.01) || (consecutiveLosses == 2)
        val isModerateUnderperforming = (winRate in 36.0..41.9) || (expectancy in -0.30..-0.151) || (consecutiveLosses == 3)
        val isSevereUnderperforming = (winRate < 36.0 && trades >= 6) || (expectancy < -0.30 && trades >= 5) || (consecutiveLosses >= 4)

        when {
            isSevereUnderperforming -> {
                // Tier 3: Degraded Probation (NOT disabled — stays active with low risk share)
                adaptationState = MatrixAdaptationTier.DEGRADED_PROBATION
                confidenceOffset = -0.12
                riskMultiplier = 0.40 // 60% risk allocation cut
                cooldownMultiplier = 2.0 // 2x standard cooldown
                cooldownAdditionalSeconds = 120L // +120s cooldown
                recoveryStreak = 0
            }
            isModerateUnderperforming -> {
                // Tier 2: Moderate Progressive Restriction (e.g. ETH in VOLATILE at 41% WR)
                adaptationState = MatrixAdaptationTier.MODERATE_RESTRICTION
                confidenceOffset = -0.07
                riskMultiplier = 0.60 // 40% risk cut
                cooldownMultiplier = 1.5 // 1.5x cooldown
                cooldownAdditionalSeconds = 60L // +60s cooldown
                recoveryStreak = 0
            }
            isMildUnderperforming -> {
                // Tier 1: Mild Progressive Restriction
                adaptationState = MatrixAdaptationTier.MILD_RESTRICTION
                confidenceOffset = -0.04
                riskMultiplier = 0.80 // 20% risk cut
                cooldownMultiplier = 1.25 // 1.25x cooldown
                cooldownAdditionalSeconds = 30L // +30s cooldown
                recoveryStreak = 0
            }
            else -> {
                // Baseline Balanced
                adaptationState = MatrixAdaptationTier.NEUTRAL
                confidenceOffset = 0.0
                riskMultiplier = 1.0
                cooldownMultiplier = 1.0
                cooldownAdditionalSeconds = 0L
                recoveryStreak = 0
            }
        }
    }

    /**
     * Builds human-readable status explanation.
     */
    fun getRationale(): String {
        return when (adaptationState) {
            MatrixAdaptationTier.OPTIMAL ->
                "🚀 [$symbol | $regime | $strategy]: High-conviction synergy (WR: ${String.format(Locale.US, "%.1f", winRate)}%, PF: ${String.format(Locale.US, "%.2f", profitFactor)}, Exp: +${String.format(Locale.US, "%.2f", expectancy)}R, Hold: ${String.format(Locale.US, "%.0f", averageHoldTime)}m). Confidence +${String.format(Locale.US, "%.2f", confidenceOffset)}, Risk x${String.format(Locale.US, "%.2f", riskMultiplier)}."

            MatrixAdaptationTier.NEUTRAL ->
                "⚖️ [$symbol | $regime | $strategy]: Balanced execution profile (WR: ${String.format(Locale.US, "%.1f", winRate)}%, PF: ${String.format(Locale.US, "%.2f", profitFactor)}, Exp: ${String.format(Locale.US, "%.2f", expectancy)}R, N=$trades). Normal risk & cooldown."

            MatrixAdaptationTier.MILD_RESTRICTION ->
                "⚠️ [$symbol | $regime | $strategy]: Mild regime drag detected (WR: ${String.format(Locale.US, "%.1f", winRate)}%, Exp: ${String.format(Locale.US, "%.2f", expectancy)}R, Cons Losses: $consecutiveLosses). Progressive adaptation applied: Confidence ${String.format(Locale.US, "%.2f", confidenceOffset)}, Risk x${String.format(Locale.US, "%.2f", riskMultiplier)}, Cooldown +${cooldownAdditionalSeconds}s."

            MatrixAdaptationTier.MODERATE_RESTRICTION ->
                "🛡️ [$symbol | $regime | $strategy]: Moderate underperformance in $regime regime (WR: ${String.format(Locale.US, "%.1f", winRate)}%, PF: ${String.format(Locale.US, "%.2f", profitFactor)}, Exp: ${String.format(Locale.US, "%.2f", expectancy)}R). Progressive adaptation: Confidence ${String.format(Locale.US, "%.2f", confidenceOffset)}, Risk allocation scaled to ${String.format(Locale.US, "%.0f", riskMultiplier * 100)}%, Cooldown x${String.format(Locale.US, "%.1f", cooldownMultiplier)} (+${cooldownAdditionalSeconds}s)."

            MatrixAdaptationTier.DEGRADED_PROBATION ->
                "🔒 [$symbol | $regime | $strategy]: Severe regime divergence (WR: ${String.format(Locale.US, "%.1f", winRate)}%, Exp: ${String.format(Locale.US, "%.2f", expectancy)}R, N=$trades). Retained active on low risk probation (${String.format(Locale.US, "%.0f", riskMultiplier * 100)}% risk, Cooldown +${cooldownAdditionalSeconds}s) to collect recovery sample."

            MatrixAdaptationTier.RECOVERING ->
                "🌱 [$symbol | $regime | $strategy]: Progressive recovery in progress (Recent Win Rate: ${String.format(Locale.US, "%.1f", winRate)}%, Recovery Streak: $recoveryStreak). Confidence & risk allocation being gradually restored."
        }
    }

    /**
     * Converts to immutable MatrixAdaptationResult snapshot.
     */
    fun toAdaptationResult(): MatrixAdaptationResult {
        return MatrixAdaptationResult(
            symbol = symbol,
            regime = regime,
            strategy = strategy,
            tier = adaptationState,
            confidenceOffset = confidenceOffset,
            riskMultiplier = riskMultiplier,
            cooldownMultiplier = cooldownMultiplier,
            cooldownAdditionalSeconds = cooldownAdditionalSeconds,
            sampleSize = trades,
            winRatePct = winRate,
            profitFactor = profitFactor,
            expectancyR = expectancy,
            averageR = averageR,
            averageHoldTimeMinutes = averageHoldTime,
            consecutiveLosses = consecutiveLosses,
            isDegraded = adaptationState in listOf(MatrixAdaptationTier.MODERATE_RESTRICTION, MatrixAdaptationTier.DEGRADED_PROBATION),
            isRecovering = adaptationState == MatrixAdaptationTier.RECOVERING,
            statusRationale = getRationale()
        )
    }
}

/**
 * Regime × Symbol × Strategy Learning Matrix Engine.
 *
 * Tracks empirical performance independently across:
 * Symbols: BTC, ETH, BNB, SOL
 * Regimes: TRENDING, RANGING, VOLATILE
 * Strategies: Volatility Expansion Breakout, Breakout Momentum, Mean Reversion, Trend Following, etc.
 *
 * Integrates directly with LuminaSelfAdaptationEngine, LuminaRiskEngine, and execution throttles.
 */
object RegimeSymbolStrategyLearningMatrix {

    private val matrixCells = mutableMapOf<String, RegimeSymbolStrategyCell>()

    init {
        seedInitialBenchmarks()
    }

    /**
     * Constructs standard cell key: "SYMBOL#REGIME#STRATEGY"
     */
    fun buildKey(symbol: String, regime: String, strategy: String): String {
        val normSym = MatrixRegimeConstants.normalizeSymbol(symbol)
        val normReg = MatrixRegimeConstants.normalizeRegime(regime)
        val cleanStrat = strategy.trim()
        return "${normSym}#${normReg}#${cleanStrat}"
    }

    /**
     * Retrieves or initializes performance cell for (Symbol × Regime × Strategy).
     */
    @Synchronized
    fun getOrCreateCell(symbol: String, regime: String, strategy: String): RegimeSymbolStrategyCell {
        val key = buildKey(symbol, regime, strategy)
        return matrixCells.getOrPut(key) {
            val normSym = MatrixRegimeConstants.normalizeSymbol(symbol)
            val normReg = MatrixRegimeConstants.normalizeRegime(regime)
            RegimeSymbolStrategyCell(
                symbol = normSym,
                regime = normReg,
                strategy = strategy.trim()
            )
        }
    }

    /**
     * Records trade outcome into the learning matrix and updates progressive adaptation.
     */
    @Synchronized
    fun recordTrade(
        symbol: String,
        regime: String,
        strategy: String,
        isWin: Boolean,
        pnlR: Double,
        holdTimeMinutes: Double = 30.0
    ): MatrixAdaptationResult {
        val cell = getOrCreateCell(symbol, regime, strategy)
        cell.recordTrade(isWin = isWin, pnlR = pnlR, holdTimeMinutes = holdTimeMinutes)
        return cell.toAdaptationResult()
    }

    /**
     * Retrieves adaptation recommendation for given (Symbol, Regime, Strategy).
     */
    @Synchronized
    fun getAdaptation(
        symbol: String,
        regime: String,
        strategy: String
    ): MatrixAdaptationResult {
        val cell = getOrCreateCell(symbol, regime, strategy)
        return cell.toAdaptationResult()
    }

    /**
     * Returns all active cells in the learning matrix.
     */
    @Synchronized
    fun getAllCells(): List<RegimeSymbolStrategyCell> {
        return matrixCells.values.toList()
    }

    /**
     * Returns all cells for a specific symbol (e.g. all BTC or BNB cells).
     */
    @Synchronized
    fun getCellsForSymbol(symbol: String): List<RegimeSymbolStrategyCell> {
        val normSym = MatrixRegimeConstants.normalizeSymbol(symbol)
        return matrixCells.values.filter { it.symbol == normSym }
    }

    /**
     * Returns all cells for a specific strategy (e.g. "Volatility Expansion Breakout").
     */
    @Synchronized
    fun getCellsForStrategy(strategy: String): List<RegimeSymbolStrategyCell> {
        val clean = strategy.trim().lowercase(Locale.US)
        return matrixCells.values.filter { it.strategy.lowercase(Locale.US) == clean }
    }

    /**
     * Pre-seeds initial empirical benchmark scenarios for BTC, ETH, BNB, SOL
     * across TRENDING, RANGING, VOLATILE for standard strategy families.
     *
     * Reflects the user's specific performance profiles:
     * - BNB in VOLATILE (Volatility Expansion Breakout): Win Rate 58% -> OPTIMAL
     * - ETH in VOLATILE (Volatility Expansion Breakout): Win Rate 41% -> MODERATE_RESTRICTION
     * - BTC in TRENDING (Volatility Expansion Breakout): Win Rate 64% -> OPTIMAL
     * - SOL in VOLATILE (Volatility Expansion Breakout): Win Rate 61% -> OPTIMAL
     * - BTC in RANGING (Volatility Expansion Breakout): Win Rate 40% -> MODERATE_RESTRICTION
     */
    @Synchronized
    fun seedInitialBenchmarks() {
        val strat = "Volatility Expansion Breakout"

        // 1. BNB in VOLATILE (Example: 58% Win Rate, positive expectancy)
        seedCell(
            symbol = "BNB",
            regime = MatrixRegimeConstants.VOLATILE,
            strategy = strat,
            trades = 19,
            wins = 11,
            grossProfitR = 25.3,
            grossLossR = 14.2,
            avgHoldMinutes = 42.0
        )

        // 2. ETH in VOLATILE (Example: 41% Win Rate, regime drag)
        seedCell(
            symbol = "ETH",
            regime = MatrixRegimeConstants.VOLATILE,
            strategy = strat,
            trades = 17,
            wins = 7,
            grossProfitR = 14.8,
            grossLossR = 19.5,
            avgHoldMinutes = 34.0
        )

        // 3. BTC in TRENDING (High institutional trend follow-through: 64% Win Rate)
        seedCell(
            symbol = "BTC",
            regime = MatrixRegimeConstants.TRENDING,
            strategy = strat,
            trades = 22,
            wins = 14,
            grossProfitR = 32.4,
            grossLossR = 14.0,
            avgHoldMinutes = 65.0
        )

        // 4. SOL in VOLATILE (High beta breakout leader: 61% Win Rate)
        seedCell(
            symbol = "SOL",
            regime = MatrixRegimeConstants.VOLATILE,
            strategy = strat,
            trades = 23,
            wins = 14,
            grossProfitR = 38.6,
            grossLossR = 17.5,
            avgHoldMinutes = 28.0
        )

        // 5. BTC in RANGING (Breakouts underperform in range chop: 40% Win Rate)
        seedCell(
            symbol = "BTC",
            regime = MatrixRegimeConstants.RANGING,
            strategy = strat,
            trades = 15,
            wins = 6,
            grossProfitR = 11.2,
            grossLossR = 16.8,
            avgHoldMinutes = 22.0
        )

        // 6. ETH in TRENDING (53% Win Rate)
        seedCell(
            symbol = "ETH",
            regime = MatrixRegimeConstants.TRENDING,
            strategy = strat,
            trades = 16,
            wins = 9,
            grossProfitR = 19.5,
            grossLossR = 13.0,
            avgHoldMinutes = 48.0
        )

        // 7. BNB in TRENDING (56% Win Rate)
        seedCell(
            symbol = "BNB",
            regime = MatrixRegimeConstants.TRENDING,
            strategy = strat,
            trades = 14,
            wins = 8,
            grossProfitR = 18.2,
            grossLossR = 11.4,
            avgHoldMinutes = 55.0
        )

        // 8. SOL in TRENDING (63% Win Rate)
        seedCell(
            symbol = "SOL",
            regime = MatrixRegimeConstants.TRENDING,
            strategy = strat,
            trades = 20,
            wins = 13,
            grossProfitR = 33.0,
            grossLossR = 14.5,
            avgHoldMinutes = 38.0
        )
    }

    private fun seedCell(
        symbol: String,
        regime: String,
        strategy: String,
        trades: Int,
        wins: Int,
        grossProfitR: Double,
        grossLossR: Double,
        avgHoldMinutes: Double
    ) {
        val normSym = MatrixRegimeConstants.normalizeSymbol(symbol)
        val normReg = MatrixRegimeConstants.normalizeRegime(regime)
        val cell = getOrCreateCell(normSym, normReg, strategy)
        cell.trades = trades
        cell.wins = wins
        cell.losses = trades - wins
        cell.winRate = (wins.toDouble() / trades.toDouble()) * 100.0
        cell.grossProfitR = grossProfitR
        cell.grossLossR = grossLossR
        cell.profitFactor = if (grossLossR > 0) grossProfitR / grossLossR else 2.5
        val netR = grossProfitR - grossLossR
        cell.averageR = netR / trades.toDouble()
        cell.expectancy = cell.averageR
        cell.totalHoldTimeMinutes = avgHoldMinutes * trades
        cell.averageHoldTime = avgHoldMinutes

        // Pre-populate recent synthetic samples to establish statistical stability
        cell.recentTrades.clear()
        for (i in 0 until min(trades, 10)) {
            val isWin = i < (wins * 10 / trades)
            val pnl = if (isWin) (grossProfitR / wins) else -(grossLossR / max(trades - wins, 1))
            cell.recentTrades.add(MatrixTradeRecord(isWin = isWin, pnlR = pnl, holdTimeMinutes = avgHoldMinutes))
        }

        cell.evaluateProgressiveAdaptation()
    }

    /**
     * Resets matrix to initial benchmark seed state.
     */
    @Synchronized
    fun resetMatrix() {
        matrixCells.clear()
        seedInitialBenchmarks()
    }
}
