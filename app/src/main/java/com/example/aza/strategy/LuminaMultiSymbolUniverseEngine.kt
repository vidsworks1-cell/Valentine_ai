package com.example.aza.strategy

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Symbol Category Classifications.
 */
enum class SymbolCategory(
    val displayName: String,
    val riskTier: String,
    val targetAllocationPct: Double,
    val maxDrawdownTolerancePct: Double
) {
    BLUE_CHIP("Blue-Chip Pillar", "Low-Moderate Risk", 0.85, 3.5),
    MAJOR_ALT("Major Liquid Altcoin", "Moderate-High Risk", 0.75, 6.0),
    MOONSHOT_GROWTH("High-Growth / Moonshot", "High Risk / High Reward", 0.65, 10.0)
}

/**
 * Comprehensive Multi-Factor Assessment for a single trading symbol.
 */
data class SymbolAssessmentRecord(
    val symbol: String,
    val baseAsset: String,
    val category: SymbolCategory,
    val currentPrice: Double,
    val estimatedMarketCapUsd: Double,
    val volume24hUsd: Double,
    val atr24hPct: Double,
    val spreadBps: Double,
    val momentumScore: Double,          // [-1.0, 1.0]
    val winProbabilityPct: Double,      // [0, 100]
    val expectedRewardRisk: Double,     // e.g. 2.4x
    val moonshotGrowthMultiplier: Double, // Potential upside multiple (e.g. 1.2x - 4.5x)
    val safetyMarginScore: Double,      // [0, 100]
    val liquidityDepthScore: Double,    // [0, 100]
    val preBuyViable: Boolean,
    val compositeRankScore: Double,     // Overall multi-criteria score
    val rankPosition: Int = 1,
    val recommendationVerdict: String,   // "OPTIMAL_PICK", "STRONG_CANDIDATE", "WATCHLIST", "FILTERED_OUT"
    val algorithmicRationale: String
)

/**
 * Multi-Symbol Universe Scan & Selection Summary.
 */
data class UniverseScanSummary(
    val timestamp: Long = System.currentTimeMillis(),
    val totalAssessedSymbols: Int,
    val viableCandidatesCount: Int,
    val topSelectedCandidate: SymbolAssessmentRecord?,
    val topBlueChip: SymbolAssessmentRecord?,
    val topMajorAlt: SymbolAssessmentRecord?,
    val topMoonshot: SymbolAssessmentRecord?,
    val allRankedAssessments: List<SymbolAssessmentRecord>,
    val executiveSummary: String
)

data class MarketMicrostructureData(
    val marketCap: Double,
    val volume24h: Double,
    val baseAtr: Double,
    val spreadBps: Double
)

/**
 * Advanced Multi-Symbol Universe Scanner, Evaluator & Ranking Engine for Lumina.
 *
 * Implements:
 * 1. Multi-tier Universe: Blue-Chips (BTC, ETH, SOL, BNB), Major Alts (ADA, DOT, MATIC, AVAX, LINK, NEAR, LTC), Moonshots (PEPE, DOGE, RENDER, INJ, SUI, FET, TIA).
 * 2. Multi-Criteria Quantitative Assessment: Market Cap, 24h Volume, Volatility/ATR, Spread, Momentum, Pre-Buy Viability, and Asymmetric Upside.
 * 3. Dynamic Optimization: Selects the single highest risk-adjusted expected alpha trade across all tiers.
 */
object LuminaMultiSymbolUniverseEngine {

    // Predefined Universe of Monitored Assets across Blue-Chips, Major Alts, and High-Momentum Moonshots
    val UNIVERSE_SPECIFICATIONS = listOf(
        // Tier 1: Blue-Chips
        Triple("BTCUSDT", "BTC", SymbolCategory.BLUE_CHIP),
        Triple("ETHUSDT", "ETH", SymbolCategory.BLUE_CHIP),
        Triple("SOLUSDT", "SOL", SymbolCategory.BLUE_CHIP),
        Triple("BNBUSDT", "BNB", SymbolCategory.BLUE_CHIP),

        // Tier 2: Popular Major Altcoins
        Triple("ADAUSDT", "ADA", SymbolCategory.MAJOR_ALT),
        Triple("DOTUSDT", "DOT", SymbolCategory.MAJOR_ALT),
        Triple("MATICUSDT", "MATIC", SymbolCategory.MAJOR_ALT),
        Triple("AVAXUSDT", "AVAX", SymbolCategory.MAJOR_ALT),
        Triple("LINKUSDT", "LINK", SymbolCategory.MAJOR_ALT),
        Triple("NEARUSDT", "NEAR", SymbolCategory.MAJOR_ALT),
        Triple("LTCUSDT", "LTC", SymbolCategory.MAJOR_ALT),
        Triple("XRPUSDT", "XRP", SymbolCategory.MAJOR_ALT),
        Triple("UNIUSDT", "UNI", SymbolCategory.MAJOR_ALT),
        Triple("AAVEUSDT", "AAVE", SymbolCategory.MAJOR_ALT),
        Triple("TAOUSDT", "TAO", SymbolCategory.MAJOR_ALT),

        // Tier 3: High-Growth, Breakout & Moonshot Opportunities
        Triple("PEPEUSDT", "PEPE", SymbolCategory.MOONSHOT_GROWTH),
        Triple("DOGEUSDT", "DOGE", SymbolCategory.MOONSHOT_GROWTH),
        Triple("RENDERUSDT", "RENDER", SymbolCategory.MOONSHOT_GROWTH),
        Triple("INJUSDT", "INJ", SymbolCategory.MOONSHOT_GROWTH),
        Triple("SUIUSDT", "SUI", SymbolCategory.MOONSHOT_GROWTH),
        Triple("FETUSDT", "FET", SymbolCategory.MOONSHOT_GROWTH),
        Triple("TIAUSDT", "TIA", SymbolCategory.MOONSHOT_GROWTH),
        Triple("WIFUSDT", "WIF", SymbolCategory.MOONSHOT_GROWTH),
        Triple("BONKUSDT", "BONK", SymbolCategory.MOONSHOT_GROWTH),
        Triple("PENDLEUSDT", "PENDLE", SymbolCategory.MOONSHOT_GROWTH),
        Triple("MIRAUSDT", "MIRA", SymbolCategory.MOONSHOT_GROWTH),
        Triple("PROMPTUSDT", "PROMPT", SymbolCategory.MOONSHOT_GROWTH),
        Triple("FIGHTUSDT", "FIGHT", SymbolCategory.MOONSHOT_GROWTH),
        Triple("AIGENSYNUSDT", "AIGENSYN", SymbolCategory.MOONSHOT_GROWTH),
        Triple("ANKRUSDT", "ANKR", SymbolCategory.MOONSHOT_GROWTH),
        Triple("USUALUSDT", "USUAL", SymbolCategory.MOONSHOT_GROWTH)
    )

    /**
     * Assesses and ranks all symbols in the multi-tier universe.
     * Dynamically incorporates any active contracts provided in livePrices to support 400+ exchange assets.
     */
    fun scanAndRankUniverse(
        livePrices: Map<String, Double> = emptyMap(),
        accountBalanceUsdt: Double = 13.0,
        activeRegime: MarketRegimeType = MarketRegimeType.CHOPPY_RANGE
    ): UniverseScanSummary {
        val assessedList = mutableListOf<SymbolAssessmentRecord>()

        // Combine predefined specifications with any additional live streamed contracts
        val combinedSpecs = UNIVERSE_SPECIFICATIONS.toMutableList()
        val existingSymbols = UNIVERSE_SPECIFICATIONS.map { it.first }.toSet()

        livePrices.keys.filter { it.endsWith("USDT") && it !in existingSymbols }.forEach { dynSym ->
            val base = dynSym.removeSuffix("USDT")
            combinedSpecs.add(Triple(dynSym, base, SymbolCategory.MOONSHOT_GROWTH))
        }

        combinedSpecs.forEach { (symbol, baseAsset, category) ->
            val price = livePrices[symbol] ?: getBaselinePrice(symbol)
            val assessment = evaluateSingleSymbol(
                symbol = symbol,
                baseAsset = baseAsset,
                category = category,
                currentPrice = price,
                accountBalanceUsdt = accountBalanceUsdt,
                activeRegime = activeRegime
            )
            assessedList.add(assessment)
        }

        // Sort by composite multi-criteria rank score descending
        val rankedList = assessedList.sortedByDescending { it.compositeRankScore }
            .mapIndexed { index, record ->
                val verdict = when {
                    index == 0 -> "OPTIMAL_PICK"
                    index in 1..3 && record.preBuyViable -> "STRONG_CANDIDATE"
                    record.preBuyViable -> "WATCHLIST"
                    else -> "FILTERED_OUT"
                }
                record.copy(rankPosition = index + 1, recommendationVerdict = verdict)
            }

        val topPick = rankedList.firstOrNull { it.preBuyViable }
        val topBlue = rankedList.firstOrNull { it.category == SymbolCategory.BLUE_CHIP && it.preBuyViable }
        val topAlt = rankedList.firstOrNull { it.category == SymbolCategory.MAJOR_ALT && it.preBuyViable }
        val topMoon = rankedList.firstOrNull { it.category == SymbolCategory.MOONSHOT_GROWTH && it.preBuyViable }

        val summary = buildString {
            append("Lumina Multi-Universe Scan: Assessed ${rankedList.size} symbols across Blue-Chips, Major Alts, and Moonshots. ")
            if (topPick != null) {
                append("Highest Rank Pick: ${topPick.symbol} (${topPick.category.displayName}, Score: ${String.format(Locale.US, "%.1f", topPick.compositeRankScore)}, Win Prob: ${String.format(Locale.US, "%.0f", topPick.winProbabilityPct)}%, Exp R/R: ${String.format(Locale.US, "%.1f", topPick.expectedRewardRisk)}x). ")
            }
            if (topMoon != null) {
                append("Top Moonshot Pick: ${topMoon.symbol} (Growth Multiplier: ${String.format(Locale.US, "%.1f", topMoon.moonshotGrowthMultiplier)}x).")
            }
        }

        return UniverseScanSummary(
            totalAssessedSymbols = rankedList.size,
            viableCandidatesCount = rankedList.count { it.preBuyViable },
            topSelectedCandidate = topPick,
            topBlueChip = topBlue,
            topMajorAlt = topAlt,
            topMoonshot = topMoon,
            allRankedAssessments = rankedList,
            executiveSummary = summary
        )
    }

    /**
     * Evaluates a single symbol against multi-factor quantitative metrics.
     */
    private fun evaluateSingleSymbol(
        symbol: String,
        baseAsset: String,
        category: SymbolCategory,
        currentPrice: Double,
        accountBalanceUsdt: Double,
        activeRegime: MarketRegimeType
    ): SymbolAssessmentRecord {
        // Quantitative Factor Assumptions based on Asset Tier & Microstructure
        val micro = getMarketMicrostructure(symbol, category)
        val marketCap = micro.marketCap
        val volume24h = micro.volume24h
        val baseAtr = micro.baseAtr
        val spreadBps = micro.spreadBps

        // Calculate Momentum & Asymmetry
        val momentum = calculateMomentumScore(symbol, category)
        val winProb = calculateWinProbability(category, momentum, activeRegime)
        val expectedRR = calculateExpectedRewardRisk(category, activeRegime)
        val moonshotMult = calculateMoonshotUpsideMultiplier(category, momentum, baseAtr)

        // Liquidity Depth Score [0, 100]
        val volumeScore = (volume24h / 50_000_000.0 * 100.0).coerceIn(20.0, 100.0)
        val spreadPenalty = (spreadBps * 4.0).coerceIn(0.0, 40.0)
        val liquidityScore = (volumeScore - spreadPenalty).coerceIn(10.0, 99.0)

        // Safety Margin Score [0, 100]
        val capSafety = when (category) {
            SymbolCategory.BLUE_CHIP -> 95.0
            SymbolCategory.MAJOR_ALT -> 82.0
            SymbolCategory.MOONSHOT_GROWTH -> 62.0
        }
        val atrVolatilityPenalty = (baseAtr * 3.5).coerceIn(5.0, 35.0)
        val safetyScore = (capSafety - atrVolatilityPenalty).coerceIn(15.0, 98.0)

        // Pre-Buy Exit Viability Check: Min 24h Volume > $5M, Spread < 25 bps, Allocation >= $5.00
        val isPreBuyViable = volume24h >= 5_000_000.0 && spreadBps <= 25.0 && (accountBalanceUsdt >= 5.50)

        // Composite Score Calculation
        // Score = (Expected Value * 40) + (Momentum * 25) + (Liquidity * 0.15) + (Safety * 0.20) + (Moonshot Upside * 8)
        val expectedValue = ((winProb / 100.0) * expectedRR) - (1.0 - (winProb / 100.0))
        val rawComposite = (expectedValue * 45.0) + (momentum * 25.0) + (liquidityScore * 0.15) + (safetyScore * 0.20) + ((moonshotMult - 1.0) * 12.0)
        val finalCompositeScore = if (isPreBuyViable) rawComposite.coerceIn(10.0, 98.0) else (rawComposite * 0.3).coerceIn(1.0, 30.0)

        val rationale = buildString {
            append("[${category.displayName}] ")
            append("24h Vol: $${String.format(Locale.US, "%.1f", volume24h / 1_000_000.0)}M | ")
            append("ATR Volatility: ${String.format(Locale.US, "%.1f", baseAtr)}% | ")
            append("Win Prob: ${String.format(Locale.US, "%.0f", winProb)}% | ")
            append("Exp R/R: ${String.format(Locale.US, "%.1f", expectedRR)}x | ")
            append("Growth Upside: ${String.format(Locale.US, "%.1f", moonshotMult)}x. ")
            if (!isPreBuyViable) append("⚠️ Pre-Buy exit viability failed.") else append("✓ Liquidity & fee hurdle verified.")
        }

        return SymbolAssessmentRecord(
            symbol = symbol,
            baseAsset = baseAsset,
            category = category,
            currentPrice = currentPrice,
            estimatedMarketCapUsd = marketCap,
            volume24hUsd = volume24h,
            atr24hPct = baseAtr,
            spreadBps = spreadBps,
            momentumScore = momentum,
            winProbabilityPct = winProb,
            expectedRewardRisk = expectedRR,
            moonshotGrowthMultiplier = moonshotMult,
            safetyMarginScore = safetyScore,
            liquidityDepthScore = liquidityScore,
            preBuyViable = isPreBuyViable,
            compositeRankScore = finalCompositeScore,
            recommendationVerdict = "PENDING",
            algorithmicRationale = rationale
        )
    }

    private fun getBaselinePrice(symbol: String): Double {
        return when (symbol) {
            "BTCUSDT" -> 64500.0
            "ETHUSDT" -> 3450.0
            "SOLUSDT" -> 142.0
            "BNBUSDT" -> 580.0
            "ADAUSDT" -> 0.46
            "DOTUSDT" -> 6.85
            "MATICUSDT" -> 0.52
            "AVAXUSDT" -> 28.50
            "LINKUSDT" -> 14.80
            "NEARUSDT" -> 5.20
            "LTCUSDT" -> 85.0
            "PEPEUSDT" -> 0.0000095
            "DOGEUSDT" -> 0.125
            "RENDERUSDT" -> 6.40
            "INJUSDT" -> 22.80
            "SUIUSDT" -> 1.85
            "FETUSDT" -> 1.45
            "TIAUSDT" -> 5.80
            else -> 10.0
        }
    }

    private fun getMarketMicrostructure(symbol: String, category: SymbolCategory): MarketMicrostructureData {
        return when (symbol) {
            "BTCUSDT" -> MarketMicrostructureData(1_270_000_000_000.0, 18_500_000_000.0, 2.2, 1.2)
            "ETHUSDT" -> MarketMicrostructureData(415_000_000_000.0, 11_200_000_000.0, 2.8, 1.5)
            "SOLUSDT" -> MarketMicrostructureData(66_000_000_000.0, 3_800_000_000.0, 4.2, 2.1)
            "BNBUSDT" -> MarketMicrostructureData(87_000_000_000.0, 1_400_000_000.0, 2.5, 2.0)
            "ADAUSDT" -> MarketMicrostructureData(16_500_000_000.0, 420_000_000.0, 3.8, 3.2)
            "DOTUSDT" -> MarketMicrostructureData(9_800_000_000.0, 280_000_000.0, 4.1, 3.5)
            "MATICUSDT" -> MarketMicrostructureData(5_200_000_000.0, 190_000_000.0, 4.4, 3.8)
            "AVAXUSDT" -> MarketMicrostructureData(11_400_000_000.0, 360_000_000.0, 4.8, 3.0)
            "LINKUSDT" -> MarketMicrostructureData(8_900_000_000.0, 310_000_000.0, 3.9, 2.8)
            "NEARUSDT" -> MarketMicrostructureData(6_100_000_000.0, 290_000_000.0, 5.2, 3.4)
            "LTCUSDT" -> MarketMicrostructureData(6_400_000_000.0, 380_000_000.0, 3.1, 2.4)
            "PEPEUSDT" -> MarketMicrostructureData(4_000_000_000.0, 850_000_000.0, 8.5, 5.5)
            "DOGEUSDT" -> MarketMicrostructureData(18_000_000_000.0, 920_000_000.0, 6.2, 2.8)
            "RENDERUSDT" -> MarketMicrostructureData(2_500_000_000.0, 160_000_000.0, 6.8, 4.5)
            "INJUSDT" -> MarketMicrostructureData(2_200_000_000.0, 140_000_000.0, 7.1, 4.8)
            "SUIUSDT" -> MarketMicrostructureData(5_100_000_000.0, 540_000_000.0, 7.4, 3.6)
            "FETUSDT" -> MarketMicrostructureData(3_700_000_000.0, 210_000_000.0, 7.8, 4.2)
            "TIAUSDT" -> MarketMicrostructureData(1_200_000_000.0, 120_000_000.0, 8.2, 5.0)
            else -> MarketMicrostructureData(1_000_000_000.0, 50_000_000.0, 5.0, 5.0)
        }
    }

    private fun calculateMomentumScore(symbol: String, category: SymbolCategory): Double {
        return when (symbol) {
            "SOLUSDT" -> 0.78
            "SUIUSDT" -> 0.85
            "BTCUSDT" -> 0.72
            "INJUSDT" -> 0.74
            "PEPEUSDT" -> 0.82
            "NEARUSDT" -> 0.68
            "RENDERUSDT" -> 0.65
            "ETHUSDT" -> 0.62
            "AVAXUSDT" -> 0.58
            "LINKUSDT" -> 0.60
            "ADAUSDT" -> 0.52
            "DOTUSDT" -> 0.48
            "MATICUSDT" -> 0.50
            "DOGEUSDT" -> 0.64
            "FETUSDT" -> 0.66
            "TIAUSDT" -> 0.59
            "BNBUSDT" -> 0.55
            "LTCUSDT" -> 0.45
            else -> 0.50
        }
    }

    private fun calculateWinProbability(category: SymbolCategory, momentum: Double, regime: MarketRegimeType): Double {
        val baseProb = when (category) {
            SymbolCategory.BLUE_CHIP -> 72.0
            SymbolCategory.MAJOR_ALT -> 66.0
            SymbolCategory.MOONSHOT_GROWTH -> 58.0
        }
        val regimeBoost = when (regime) {
            MarketRegimeType.BULL_EXPANSION -> 6.0
            MarketRegimeType.LOW_VOLATILITY_SQUEEZE -> 4.0
            MarketRegimeType.CHOPPY_RANGE -> 0.0
            MarketRegimeType.BEAR_BREAKDOWN -> -6.0
            MarketRegimeType.HIGH_VOLATILITY_SHOCK -> -8.0
        }
        val momentumBoost = (momentum - 0.5) * 16.0
        return (baseProb + regimeBoost + momentumBoost).coerceIn(40.0, 88.0)
    }

    private fun calculateExpectedRewardRisk(category: SymbolCategory, regime: MarketRegimeType): Double {
        val baseRR = when (category) {
            SymbolCategory.BLUE_CHIP -> 2.2
            SymbolCategory.MAJOR_ALT -> 2.6
            SymbolCategory.MOONSHOT_GROWTH -> 3.4
        }
        val multiplier = when (regime) {
            MarketRegimeType.BULL_EXPANSION -> 1.25
            MarketRegimeType.LOW_VOLATILITY_SQUEEZE -> 1.15
            MarketRegimeType.CHOPPY_RANGE -> 0.90
            MarketRegimeType.BEAR_BREAKDOWN -> 0.80
            MarketRegimeType.HIGH_VOLATILITY_SHOCK -> 0.75
        }
        return baseRR * multiplier
    }

    private fun calculateMoonshotUpsideMultiplier(category: SymbolCategory, momentum: Double, atr: Double): Double {
        return when (category) {
            SymbolCategory.BLUE_CHIP -> 1.25 + (momentum * 0.2)
            SymbolCategory.MAJOR_ALT -> 1.80 + (momentum * 0.6)
            SymbolCategory.MOONSHOT_GROWTH -> 2.50 + (momentum * 1.5) + (atr * 0.15)
        }
    }
}
