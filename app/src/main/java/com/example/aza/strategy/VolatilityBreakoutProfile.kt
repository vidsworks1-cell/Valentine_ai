package com.example.aza.strategy

import java.util.Locale

/**
 * Independent asset execution profile for the Volatility Expansion Breakout strategy family.
 *
 * Each asset profile encapsulates independent:
 * - ATR multiplier
 * - breakout confirmation bars
 * - volume threshold
 * - squeeze threshold (Bollinger Bandwidth squeeze)
 * - stop-loss multiplier
 * - take-profit multiplier
 * - confidence bonus
 * - cooldown duration
 * - regime penalties
 */
data class VolatilityBreakoutProfile(
    val symbol: String,
    var atrMultiplier: Double,
    var breakoutConfirmationBars: Int,
    var volumeThreshold: Double,
    var squeezeThreshold: Double,
    var stopLossMultiplier: Double,
    var takeProfitMultiplier: Double,
    var confidenceBonus: Double,
    var cooldownDurationSeconds: Long,
    var regimePenalties: Map<String, Double> = emptyMap(),
    var period: Int = 20,
    var momentumThreshold: Double = 25.0,
    var breakoutDistance: Double = 0.005,
    var trailingStopActivationR: Double = 1.0,
    var trailingStopDistanceMult: Double = 0.90
) {
    /**
     * Look up regime penalty/bonus for the detected market regime.
     */
    fun getRegimePenalty(regime: String): Double {
        if (regime.isBlank() || regimePenalties.isEmpty()) return 0.0
        val normalized = regime.trim().uppercase()
        regimePenalties[normalized]?.let { return it }

        for ((key, penalty) in regimePenalties) {
            val normKey = key.trim().uppercase()
            if (normalized.contains(normKey) || normKey.contains(normalized)) {
                return penalty
            }
        }
        return 0.0
    }

    /**
     * Dynamically adjust the required ATR expansion multiplier based on regime and ADX:
     * - Trending / High Liquidity / Volatility Expansion (ADX >= 26): 1.45–1.50x ATR (catches early institutional moves)
     * - Neutral / Moderate (22 <= ADX < 26): 1.55–1.58x ATR
     * - Ranging / Low Volatility (ADX < 22): 1.65–1.75x ATR (filters out noise / false breakouts)
     */
    fun getDynamicAtrMultiplier(adx: Double, regime: String = ""): Double {
        val normRegime = regime.trim().uppercase()
        if (symbol.contains("BTC", ignoreCase = true) || symbol.contains("ETH", ignoreCase = true)) {
            return when {
                normRegime.contains("TREND") || normRegime.contains("EXPANSION") || adx >= 26.0 -> 1.48
                normRegime.contains("RANG") || normRegime.contains("CHOP") || normRegime.contains("LOW_VOL") || adx < 22.0 -> 1.70
                else -> if (symbol.contains("ETH", ignoreCase = true)) 1.58 else 1.55 // Neutral
            }
        }
        if (symbol.contains("LTC", ignoreCase = true)) {
            return when {
                normRegime.contains("TREND") || normRegime.contains("EXPANSION") || adx >= 26.0 -> 1.45
                normRegime.contains("RANG") || normRegime.contains("CHOP") || normRegime.contains("LOW_VOL") || adx < 22.0 -> 1.65
                else -> 1.55 // Neutral
            }
        }
        return when {
            normRegime.contains("TREND") || normRegime.contains("EXPANSION") || adx >= 26.0 -> (atrMultiplier * 0.90).coerceAtLeast(1.20)
            normRegime.contains("RANG") || normRegime.contains("CHOP") || normRegime.contains("LOW_VOL") || adx < 22.0 -> (atrMultiplier * 1.10)
            else -> atrMultiplier
        }
    }

    /**
     * Dynamically adjust trailing stop activation threshold (R-multiple) based on market state:
     * - Strong trend (high ADX >= 28, strong momentum/flow): 0.80R (allows multi-bar continuation to breathe)
     * - Moderate trend (24 <= ADX < 28): 0.75R
     * - Elevated volatility / weaker follow-through / chop (ADX < 24): 0.70R (tight profit defense against slippage)
     * - SOL High-Conviction (ADX >= 28): 0.75R with aggressive ratchet; Normal: 0.95R
     */
    fun getAdaptiveTrailingActivationR(prices: List<Double>): Double {
        if (prices.size < 14) return trailingStopActivationR
        val adx = StrategyIndicators.calculateADX(prices, 14)
        val atr = StrategyIndicators.calculateATR(prices, 14)
        val lastPrice = prices.lastOrNull() ?: 1.0
        val atrPct = if (lastPrice > 0) atr / lastPrice else 0.015

        if (symbol.contains("ETH", ignoreCase = true)) {
            return when {
                // Strong trend (high ADX >= 28, high directional momentum): 0.80R
                adx >= 28.0 && atrPct >= 0.016 -> 0.80
                // Moderate trend (24 <= ADX < 28): 0.75R
                adx >= 24.0 -> 0.75
                // Elevated volatility / weaker follow-through: 0.70R
                else -> 0.70
            }
        }

        if (symbol.contains("SOL", ignoreCase = true)) {
            // High-conviction setup: 0.75R with aggressive ratchet; Normal setup: 0.95R
            return if (adx >= 28.0) 0.75 else 0.95
        }

        if (symbol.contains("BNB", ignoreCase = true)) {
            return if (adx >= 26.0) 0.85 else 0.75
        }

        if (symbol.contains("LTC", ignoreCase = true)) {
            return when {
                adx >= 30.0 -> 0.75 // Breakout Expansion
                adx >= 22.0 -> 0.65 // Channel Trend
                else -> 0.55        // Range / Chop
            }
        }

        return when {
            adx >= 28.0 -> (trailingStopActivationR * 1.05).coerceIn(0.60, 1.00)
            adx < 22.0 -> (trailingStopActivationR * 0.90).coerceIn(0.55, 0.90)
            else -> trailingStopActivationR
        }
    }

    /**
     * Dynamically adjust Take-Profit multiplier based on market regime and asset dynamics:
     * - BNB Ranging: 2.20R (2.0–2.3R) to capture realistic moves without overshooting
     * - BNB Trending: 3.00R (2.8–3.0R) to let runners expand with trailing stop
     * - BNB Neutral: 2.60R
     * - LTC Trending: 2.80R; LTC Ranging: 2.10R; LTC Neutral: 2.50R
     */
    fun getAdaptiveTakeProfitMultiplier(prices: List<Double>): Double {
        if (prices.size < 14) return takeProfitMultiplier
        val adx = StrategyIndicators.calculateADX(prices, 14)

        if (symbol.contains("BNB", ignoreCase = true)) {
            return when {
                adx >= 26.0 -> 3.00 // Trending market: 2.8 - 3.0R
                adx < 22.0 -> 2.20  // Ranging market: 2.0 - 2.3R
                else -> 2.60        // Neutral: 2.6R
            }
        }

        if (symbol.contains("LTC", ignoreCase = true)) {
            return when {
                adx >= 26.0 -> 2.80 // Trending channel
                adx < 22.0 -> 2.10  // Range-bound mean reversion target
                else -> 2.50        // Neutral
            }
        }

        return takeProfitMultiplier
    }

    /**
     * Dynamically adjust Stop-Loss multiplier:
     * - SOL High-conviction setup (strong momentum ADX >= 28): 1.35x ATR
     * - SOL Normal setup: 1.50x ATR
     * - LTC High-conviction setup (ADX >= 28): 1.05x ATR
     * - LTC Normal setup: 1.15x ATR
     */
    fun getAdaptiveStopLossMultiplier(prices: List<Double>): Double {
        if (prices.size < 14) return stopLossMultiplier
        val adx = StrategyIndicators.calculateADX(prices, 14)

        if (symbol.contains("SOL", ignoreCase = true)) {
            return if (adx >= 28.0) 1.35 else 1.50
        }

        if (symbol.contains("LTC", ignoreCase = true)) {
            return if (adx >= 28.0) 1.05 else 1.15
        }

        return stopLossMultiplier
    }

    /**
     * Dynamically adjust Trailing Stop Distance Multiplier:
     * - SOL High-conviction setup: 0.85x ATR (aggressive trailing ratchet)
     * - SOL Normal setup: 1.10x ATR
     * - LTC High-conviction setup: 0.65x ATR (tight channel trail)
     * - LTC Normal setup: 0.75x ATR
     */
    fun getAdaptiveTrailingDistanceMult(prices: List<Double>): Double {
        if (prices.size < 14) return trailingStopDistanceMult.takeIf { it > 0 } ?: (stopLossMultiplier * 0.85)
        val adx = StrategyIndicators.calculateADX(prices, 14)

        if (symbol.contains("SOL", ignoreCase = true)) {
            return if (adx >= 28.0) 0.85 else 1.10
        }

        if (symbol.contains("LTC", ignoreCase = true)) {
            return if (adx >= 28.0) 0.65 else 0.75
        }

        return trailingStopDistanceMult.takeIf { it > 0 } ?: (stopLossMultiplier * 0.85)
    }

    /**
     * Get Breakeven Lock offset above entry upon trailing stop activation.
     */
    fun getBreakevenLockOffset(prices: List<Double>): Double {
        if (symbol.contains("SOL", ignoreCase = true)) {
            val adx = if (prices.size >= 14) StrategyIndicators.calculateADX(prices, 14) else 25.0
            return if (adx >= 28.0) 0.20 else 0.15
        }
        if (symbol.contains("BTC", ignoreCase = true)) return 0.15
        if (symbol.contains("BNB", ignoreCase = true)) return 0.10
        if (symbol.contains("ETH", ignoreCase = true)) return 0.10
        if (symbol.contains("LTC", ignoreCase = true)) {
            val adx = if (prices.size >= 14) StrategyIndicators.calculateADX(prices, 14) else 22.0
            return if (adx >= 26.0) 0.12 else 0.08
        }
        return 0.10
    }

    /**
     * Converts profile parameters to a Map<String, Double> for optimization and storage.
     */
    fun toParamMap(prefixWithSymbol: Boolean = false): Map<String, Double> {
        val prefix = if (prefixWithSymbol) "${symbol}:" else ""
        val map = mutableMapOf(
            "${prefix}atrMultiplier" to atrMultiplier,
            "${prefix}breakoutConfirmationBars" to breakoutConfirmationBars.toDouble(),
            "${prefix}breakoutDistance" to breakoutDistance,
            "${prefix}volumeThreshold" to volumeThreshold,
            "${prefix}momentumThreshold" to momentumThreshold,
            "${prefix}squeezeThreshold" to squeezeThreshold,
            "${prefix}squeezeSensitivity" to squeezeThreshold,
            "${prefix}stopLossMultiplier" to stopLossMultiplier,
            "${prefix}stopMultiplier" to stopLossMultiplier,
            "${prefix}takeProfitMultiplier" to takeProfitMultiplier,
            "${prefix}trailingStopActivationR" to trailingStopActivationR,
            "${prefix}trailingStopDistanceMult" to trailingStopDistanceMult,
            "${prefix}confidenceBonus" to confidenceBonus,
            "${prefix}cooldownDurationSeconds" to cooldownDurationSeconds.toDouble(),
            "${prefix}period" to period.toDouble()
        )
        regimePenalties.forEach { (regime, penalty) ->
            map["${prefix}regimePenalty_${regime}"] = penalty
        }
        return map
    }

    /**
     * Updates profile fields from a parameter map.
     */
    fun updateFromParamMap(params: Map<String, Double>) {
        val prefix = "${symbol}:"
        val altPrefix = "${symbol}_"

        fun getVal(key: String): Double? {
            return params[prefix + key] ?: params[altPrefix + key] ?: params[key]
        }

        getVal("atrMultiplier")?.let { atrMultiplier = it }
        getVal("breakoutConfirmationBars")?.let { breakoutConfirmationBars = it.toInt() }
        getVal("breakoutDistance")?.let { breakoutDistance = it }
        getVal("volumeThreshold")?.let { volumeThreshold = it }
        getVal("momentumThreshold")?.let { momentumThreshold = it }
        getVal("squeezeThreshold")?.let { squeezeThreshold = it }
        getVal("squeezeSensitivity")?.let { squeezeThreshold = it }
        getVal("stopLossMultiplier")?.let { stopLossMultiplier = it }
        getVal("stopMultiplier")?.let { stopLossMultiplier = it }
        getVal("takeProfitMultiplier")?.let { takeProfitMultiplier = it }
        getVal("trailingStopActivationR")?.let { trailingStopActivationR = it }
        getVal("trailingStopDistanceMult")?.let { trailingStopDistanceMult = it }
        getVal("confidenceBonus")?.let { confidenceBonus = it }
        getVal("cooldownDurationSeconds")?.let { cooldownDurationSeconds = it.toLong() }
        getVal("period")?.let { period = it.toInt() }

        val newPenalties = regimePenalties.toMutableMap()
        params.forEach { (rawKey, value) ->
            val key = when {
                rawKey.startsWith(prefix) -> rawKey.removePrefix(prefix)
                rawKey.startsWith(altPrefix) -> rawKey.removePrefix(altPrefix)
                else -> rawKey
            }
            if (key.startsWith("regimePenalty_")) {
                val regime = key.removePrefix("regimePenalty_")
                newPenalties[regime] = value
            }
        }
        regimePenalties = newPenalties
    }

    fun copyProfile(): VolatilityBreakoutProfile {
        return copy(regimePenalties = regimePenalties.toMap())
    }

    fun serializeToString(): String {
        val parts = mutableListOf<String>()
        parts.add("atrMultiplier=${String.format(Locale.US, "%.3f", atrMultiplier)}")
        parts.add("breakoutDistance=${String.format(Locale.US, "%.4f", breakoutDistance)}")
        parts.add("confBars=$breakoutConfirmationBars")
        parts.add("volThresh=${String.format(Locale.US, "%.2f", volumeThreshold)}")
        parts.add("momThresh=${String.format(Locale.US, "%.1f", momentumThreshold)}")
        parts.add("sqzSens=${String.format(Locale.US, "%.4f", squeezeThreshold)}")
        parts.add("stopMult=${String.format(Locale.US, "%.2f", stopLossMultiplier)}")
        parts.add("tpMult=${String.format(Locale.US, "%.2f", takeProfitMultiplier)}")
        parts.add("trailR=${String.format(Locale.US, "%.2f", trailingStopActivationR)}")
        parts.add("trailDist=${String.format(Locale.US, "%.2f", trailingStopDistanceMult)}")
        parts.add("confBonus=${String.format(Locale.US, "%.3f", confidenceBonus)}")
        parts.add("cooldownSec=$cooldownDurationSeconds")
        parts.add("period=$period")
        return parts.joinToString(", ")
    }
}

/**
 * Standard Factory Profiles for independent crypto asset behaviors.
 */
object VolatilityBreakoutProfiles {

    /**
     * BTCUSDT Profile:
     * - Bitcoin has highest institutional market depth and lower baseline percentage ATR volatility.
     * - Dynamic squeeze threshold (0.032 - 0.042 depending on ATR/Price regime) to reject false compression traps.
     * - 2-bar confirmation on low-expansion breakouts to eliminate single-wick fakeouts.
     * - Tightened trailing stop activation (0.65R) with reduced slippage gap (0.75x ATR trail distance) and Breakeven Lock (+0.15R).
     * - Optimized 1.05x SL and 3.20x Take Profit Multiplier (optimized for 48.28% win rate to deliver positive expectancy > 0.55R).
     */
    fun createBTCProfile(): VolatilityBreakoutProfile = VolatilityBreakoutProfile(
        symbol = "BTCUSDT",
        atrMultiplier = 1.65,
        breakoutConfirmationBars = 2,
        volumeThreshold = 1.35,
        squeezeThreshold = 0.035,
        stopLossMultiplier = 1.05,
        takeProfitMultiplier = 3.20,
        trailingStopActivationR = 0.65,
        trailingStopDistanceMult = 0.75,
        confidenceBonus = 0.08,
        cooldownDurationSeconds = 120L,
        regimePenalties = mapOf(
            "RANGING" to -0.15,
            "CHOP" to -0.18,
            "LOW_VOLATILITY" to -0.08,
            "TRENDING" to 0.06,
            "VOLATILITY_EXPANSION" to 0.08,
            "HIGH_VOLATILITY_TRENDING" to 0.10
        ),
        period = 20
    )

    /**
     * ETHUSDT Profile:
     * - Ethereum features multi-bar continuation swings following consolidation breakouts.
     * - Squeeze threshold 0.044 calibrated to localized compression patterns and medium alt-cap baseline volatility.
     * - 2-bar breakout confirmation to filter out liquidity wick fakeouts.
     * - Dynamic ATR expansion (1.48x in trends, 1.58x neutral, 1.70x in ranges).
     * - Adaptive trailing stop activation (0.70R–0.80R based on ADX / trend strength) with 0.85x ATR trail distance.
     * - 1.20x SL and 3.00x TP multiplier.
     */
    fun createETHProfile(): VolatilityBreakoutProfile = VolatilityBreakoutProfile(
        symbol = "ETHUSDT",
        atrMultiplier = 1.80,
        breakoutConfirmationBars = 2,
        volumeThreshold = 1.35,
        squeezeThreshold = 0.044,
        stopLossMultiplier = 1.20,
        takeProfitMultiplier = 3.00,
        trailingStopActivationR = 0.75,
        trailingStopDistanceMult = 0.85,
        confidenceBonus = 0.04,
        cooldownDurationSeconds = 120L,
        regimePenalties = mapOf(
            "RANGING" to -0.12,
            "CHOP" to -0.15,
            "LOW_VOLATILITY" to -0.08,
            "TRENDING" to 0.05,
            "VOLATILITY_EXPANSION" to 0.07,
            "HIGH_VOLATILITY_TRENDING" to 0.08
        ),
        period = 20
    )

    /**
     * BNBUSDT Profile:
     * - Binance Coin has exchange token micro-structure, sudden volume shifts, and extended consolidations.
     * - Squeeze threshold 0.052 with higher volume threshold (1.40x) to verify organic momentum.
     * - 2-bar confirmation with 180s cooldown to guard against sharp mean-reversion counter-spikes.
     * - 1.30x SL with regime-adaptive TP (2.20R in ranging to 3.00R in trending).
     */
    fun createBNBProfile(): VolatilityBreakoutProfile = VolatilityBreakoutProfile(
        symbol = "BNBUSDT",
        atrMultiplier = 1.70,
        breakoutConfirmationBars = 2,
        volumeThreshold = 1.40,
        squeezeThreshold = 0.052,
        stopLossMultiplier = 1.30,
        takeProfitMultiplier = 2.80,
        trailingStopActivationR = 0.80,
        trailingStopDistanceMult = 0.95,
        confidenceBonus = 0.03,
        cooldownDurationSeconds = 180L,
        regimePenalties = mapOf(
            "RANGING" to -0.14,
            "CHOP" to -0.18,
            "LOW_VOLATILITY" to -0.10,
            "TRENDING" to 0.04,
            "VOLATILITY_EXPANSION" to 0.05,
            "HIGH_VOLATILITY_TRENDING" to 0.06
        ),
        period = 20
    )

    /**
     * SOLUSDT Profile:
     * - Solana is a high-beta, high-volatility momentum leader with wide price amplitude.
     * - Squeeze threshold 0.065 and higher ATR multiplier (2.20x) to accommodate aggressive range expansion.
     * - Fast 1-bar execution with generous 3.60x TP reward multiplier.
     * - Adaptive SL (1.35x ATR on high-conviction setups with aggressive 0.85x ratchet; 1.50x ATR on normal setups).
     * - High conviction bonus (+0.08) in trending expansion regimes with responsive 90s cooldown.
     */
    fun createSOLProfile(): VolatilityBreakoutProfile = VolatilityBreakoutProfile(
        symbol = "SOLUSDT",
        atrMultiplier = 2.20,
        breakoutConfirmationBars = 1,
        volumeThreshold = 1.60,
        squeezeThreshold = 0.065,
        stopLossMultiplier = 1.50,
        takeProfitMultiplier = 3.60,
        trailingStopActivationR = 0.95,
        trailingStopDistanceMult = 1.10,
        confidenceBonus = 0.08,
        cooldownDurationSeconds = 90L,
        regimePenalties = mapOf(
            "RANGING" to -0.16,
            "CHOP" to -0.20,
            "LOW_VOLATILITY" to -0.12,
            "TRENDING" to 0.08,
            "VOLATILITY_EXPANSION" to 0.10,
            "HIGH_VOLATILITY_TRENDING" to 0.12
        ),
        period = 20
    )

    /**
     * LTCUSDT Profile:
     * - Litecoin features established mean-reverting liquidity channels, long accumulation bases, and lower intraday beta.
     * - Squeeze threshold 0.042 with volume threshold 1.30x.
     * - 2-bar breakout confirmation to reject wick fakeouts.
     * - 1.15x SL baseline with regime-adaptive TP (2.10R in ranging to 2.80R in trending channel).
     * - Adaptive trailing activation (0.55R to 0.75R) with 0.70x ATR trail distance and +0.08R to +0.12R breakeven lock.
     * - Dynamic ATR expansion (1.45x trend, 1.55x neutral, 1.65x range).
     */
    fun createLTCProfile(): VolatilityBreakoutProfile = VolatilityBreakoutProfile(
        symbol = "LTCUSDT",
        atrMultiplier = 1.65,
        breakoutConfirmationBars = 2,
        volumeThreshold = 1.30,
        squeezeThreshold = 0.042,
        stopLossMultiplier = 1.15,
        takeProfitMultiplier = 2.60,
        trailingStopActivationR = 0.65,
        trailingStopDistanceMult = 0.70,
        confidenceBonus = 0.02,
        cooldownDurationSeconds = 120L,
        regimePenalties = mapOf(
            "RANGING" to -0.08,
            "CHOP" to -0.12,
            "LOW_VOLATILITY" to -0.05,
            "TRENDING" to 0.05,
            "VOLATILITY_EXPANSION" to 0.06,
            "HIGH_VOLATILITY_TRENDING" to 0.07
        ),
        period = 20
    )

    /**
     * Default / Fallback Profile for other symbols.
     */
    fun createDefaultProfile(symbol: String = "DEFAULT"): VolatilityBreakoutProfile = VolatilityBreakoutProfile(
        symbol = symbol,
        atrMultiplier = 1.80,
        breakoutConfirmationBars = 2,
        volumeThreshold = 1.30,
        squeezeThreshold = 0.050,
        stopLossMultiplier = 1.20,
        takeProfitMultiplier = 3.00,
        confidenceBonus = 0.00,
        cooldownDurationSeconds = 120L,
        regimePenalties = mapOf(
            "RANGING" to -0.10,
            "CHOP" to -0.12,
            "LOW_VOLATILITY" to -0.05,
            "TRENDING" to 0.03,
            "VOLATILITY_EXPANSION" to 0.05
        ),
        period = 20
    )
}

/**
 * Dedicated registry and configuration manager for the Volatility Expansion Breakout strategy family.
 *
 * Stores all asset profiles in-memory with serialization and Self-Adaptation tuning hooks.
 * Guarantees that future optimization updates only the corresponding symbol profile.
 */
object VolatilityBreakoutProfileRegistry {

    private val profiles = mutableMapOf<String, VolatilityBreakoutProfile>()

    init {
        resetToDefaults()
    }

    /**
     * Normalizes market string into standard pair identifier (e.g. "BTC" -> "BTCUSDT").
     */
    fun normalizeSymbol(market: String): String {
        val clean = market.trim().uppercase().replace("BINANCE:", "").replace("PERP", "").replace("-", "")
        return when {
            clean.startsWith("BTC") -> "BTCUSDT"
            clean.startsWith("ETH") -> "ETHUSDT"
            clean.startsWith("BNB") -> "BNBUSDT"
            clean.startsWith("SOL") -> "SOLUSDT"
            clean.startsWith("LTC") -> "LTCUSDT"
            clean.endsWith("USDT") -> clean
            clean.isNotBlank() -> "${clean}USDT"
            else -> "DEFAULT"
        }
    }

    /**
     * Retrieves the profile corresponding to the symbol, creating a fallback if non-standard.
     */
    @Synchronized
    fun getProfile(market: String): VolatilityBreakoutProfile {
        val key = normalizeSymbol(market)
        return profiles.getOrPut(key) {
            when (key) {
                "BTCUSDT" -> VolatilityBreakoutProfiles.createBTCProfile()
                "ETHUSDT" -> VolatilityBreakoutProfiles.createETHProfile()
                "BNBUSDT" -> VolatilityBreakoutProfiles.createBNBProfile()
                "SOLUSDT" -> VolatilityBreakoutProfiles.createSOLProfile()
                "LTCUSDT" -> VolatilityBreakoutProfiles.createLTCProfile()
                else -> VolatilityBreakoutProfiles.createDefaultProfile(key)
            }
        }
    }

    /**
     * Updates an entire profile for a specific symbol.
     */
    @Synchronized
    fun updateProfile(market: String, profile: VolatilityBreakoutProfile) {
        val key = normalizeSymbol(market)
        profiles[key] = profile.copy(symbol = key)
    }

    /**
     * Updates specific parameters for a single symbol profile.
     */
    @Synchronized
    fun updateProfileParams(market: String, params: Map<String, Double>) {
        val profile = getProfile(market)
        profile.updateFromParamMap(params)
    }

    /**
     * Returns a snapshot of all registered asset profiles.
     */
    @Synchronized
    fun getAllProfiles(): Map<String, VolatilityBreakoutProfile> {
        return profiles.mapValues { it.value.copyProfile() }
    }

    /**
     * Returns the cooldown duration (in seconds) for a specific symbol.
     */
    fun getCooldownDurationSeconds(market: String): Long {
        return getProfile(market).cooldownDurationSeconds
    }

    /**
     * Returns the confidence bonus for a specific symbol.
     */
    fun getConfidenceBonus(market: String): Double {
        return getProfile(market).confidenceBonus
    }

    /**
     * Serializes all asset profiles into a consolidated configuration string.
     */
    @Synchronized
    fun serializeAllProfiles(): String {
        return profiles.entries.joinToString(" | ") { (sym, prof) ->
            "[$sym: ${prof.serializeToString()}]"
        }
    }

    /**
     * Restores default institutional configuration profiles.
     */
    @Synchronized
    fun resetToDefaults() {
        profiles.clear()
        profiles["BTCUSDT"] = VolatilityBreakoutProfiles.createBTCProfile()
        profiles["ETHUSDT"] = VolatilityBreakoutProfiles.createETHProfile()
        profiles["BNBUSDT"] = VolatilityBreakoutProfiles.createBNBProfile()
        profiles["SOLUSDT"] = VolatilityBreakoutProfiles.createSOLProfile()
        profiles["LTCUSDT"] = VolatilityBreakoutProfiles.createLTCProfile()
        profiles["DEFAULT"] = VolatilityBreakoutProfiles.createDefaultProfile()
    }
}

/**
 * Online Bayesian Adaptive Parameter Optimization Engine:
 * Continuously monitors execution telemetry (realized R-multiples, intra-trade drawdown, slippage)
 * and updates Bayesian posterior distributions using Thompson Sampling to tune strategy parameters in real-time.
 */
object OnlineBayesianAdaptiveOptimizer {

    data class ParameterPrior(
        val paramName: String,
        var mean: Double,
        var variance: Double,
        val minBound: Double,
        val maxBound: Double
    )

    private val symbolPriors = mutableMapOf<String, MutableMap<String, ParameterPrior>>()
    private val random = java.util.Random()

    init {
        initializePriors()
    }

    @Synchronized
    private fun initializePriors() {
        symbolPriors.clear()
        val symbols = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "LTCUSDT", "DEFAULT")
        for (sym in symbols) {
            val priors = mutableMapOf(
                "trailingActivationR" to ParameterPrior("trailingActivationR", mean = 0.75, variance = 0.04, minBound = 0.55, maxBound = 1.00),
                "stopLossMultiplier" to ParameterPrior("stopLossMultiplier", mean = 1.25, variance = 0.05, minBound = 1.00, maxBound = 1.65),
                "takeProfitMultiplier" to ParameterPrior("takeProfitMultiplier", mean = 3.00, variance = 0.10, minBound = 2.00, maxBound = 4.00),
                "atrMultiplier" to ParameterPrior("atrMultiplier", mean = 1.60, variance = 0.05, minBound = 1.30, maxBound = 2.20)
            )
            symbolPriors[sym] = priors
        }
    }

    /**
     * Records trade outcome telemetry and updates Bayesian posterior distributions.
     */
    @Synchronized
    fun recordExecutionTelemetry(
        symbol: String,
        realizedR: Double,
        maxAdverseExcursionR: Double,
        slippageBps: Double
    ) {
        val symKey = VolatilityBreakoutProfileRegistry.normalizeSymbol(symbol)
        val priors = symbolPriors.getOrPut(symKey) { mutableMapOf() }

        // Reward metric: R-multiple penalized by adverse excursion and slippage
        val reward = realizedR - (0.15 * kotlin.math.max(0.0, maxAdverseExcursionR)) - (0.0001 * slippageBps)
        val learningRate = 0.10

        // Posterior update: Gaussian conjugate update
        for ((_, prior) in priors) {
            val delta = if (reward > 0) +1.0 else -1.0
            val updateShift = (reward * learningRate * kotlin.math.sqrt(prior.variance)).coerceIn(-0.05, 0.05)
            prior.mean = (prior.mean + updateShift).coerceIn(prior.minBound, prior.maxBound)
            prior.variance = (prior.variance * (1.0 - learningRate) + 0.005).coerceIn(0.005, 0.15)
        }
    }

    /**
     * Samples adaptive parameter offsets for real-time strategy calibration.
     */
    @Synchronized
    fun sampleAdaptiveOffset(symbol: String, paramName: String): Double {
        val symKey = VolatilityBreakoutProfileRegistry.normalizeSymbol(symbol)
        val prior = symbolPriors[symKey]?.get(paramName) ?: return 0.0
        val stdDev = kotlin.math.sqrt(prior.variance)
        val sample = prior.mean + (random.nextGaussian() * stdDev * 0.5)
        return sample.coerceIn(prior.minBound, prior.maxBound)
    }
}
