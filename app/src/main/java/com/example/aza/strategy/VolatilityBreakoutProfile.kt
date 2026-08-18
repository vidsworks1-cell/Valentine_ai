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
     * - Tighter squeeze bandwidth (0.038) to detect genuine institutional consolidation releases.
     * - Fast 1-bar breakout confirmation to avoid late entry slippage on high-liquidity books.
     * - Conservative 1.1x SL and 2.8x TP multiplier.
     */
    fun createBTCProfile(): VolatilityBreakoutProfile = VolatilityBreakoutProfile(
        symbol = "BTCUSDT",
        atrMultiplier = 1.60,
        breakoutConfirmationBars = 1,
        volumeThreshold = 1.25,
        squeezeThreshold = 0.038,
        stopLossMultiplier = 1.10,
        takeProfitMultiplier = 2.80,
        confidenceBonus = 0.06,
        cooldownDurationSeconds = 120L,
        regimePenalties = mapOf(
            "RANGING" to -0.10,
            "CHOP" to -0.14,
            "LOW_VOLATILITY" to -0.06,
            "TRENDING" to 0.04,
            "VOLATILITY_EXPANSION" to 0.06,
            "HIGH_VOLATILITY_TRENDING" to 0.07
        ),
        period = 20
    )

    /**
     * ETHUSDT Profile:
     * - Ethereum features multi-bar continuation swings following consolidation breakouts.
     * - Squeeze threshold 0.048 accommodates medium alt-cap baseline volatility.
     * - 2-bar breakout confirmation to filter out liquidity wick fakeouts.
     * - 1.2x SL and 3.0x TP multiplier.
     */
    fun createETHProfile(): VolatilityBreakoutProfile = VolatilityBreakoutProfile(
        symbol = "ETHUSDT",
        atrMultiplier = 1.80,
        breakoutConfirmationBars = 2,
        volumeThreshold = 1.35,
        squeezeThreshold = 0.048,
        stopLossMultiplier = 1.20,
        takeProfitMultiplier = 3.00,
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
     * - 1.3x SL and 3.2x TP multiplier.
     */
    fun createBNBProfile(): VolatilityBreakoutProfile = VolatilityBreakoutProfile(
        symbol = "BNBUSDT",
        atrMultiplier = 1.70,
        breakoutConfirmationBars = 2,
        volumeThreshold = 1.40,
        squeezeThreshold = 0.052,
        stopLossMultiplier = 1.30,
        takeProfitMultiplier = 3.20,
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
     * - Fast 1-bar execution with generous 1.5x SL and 3.6x TP reward multiplier.
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
        profiles["DEFAULT"] = VolatilityBreakoutProfiles.createDefaultProfile()
    }
}
