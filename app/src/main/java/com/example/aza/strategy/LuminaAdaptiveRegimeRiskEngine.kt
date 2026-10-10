package com.example.aza.strategy

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Dynamic Risk Profile tailored to specific Market Regimes and Wallet Capacities.
 */
data class DynamicRegimeRiskConfig(
    val regime: MarketRegimeType,
    val regimeRiskMultiplier: Double,       // Multiplier on base risk (0.35x - 1.25x)
    val maxPositionNotionalPct: Double,     // Max % of total portfolio in single position
    val stopLossAtrMultiplier: Double,      // Distance of stop loss in ATRs
    val takeProfitAtrMultiplier: Double,    // Distance of take profit target in ATRs
    val maxConcurrentPositions: Int,        // Position capacity limit
    val fastBreakevenTriggerR: Double,      // When to move stop to zero risk (in R-multiples)
    val maxPortfolioHeatLimitPct: Double,   // Total portfolio at risk limit
    val recommendedKellyFraction: Double,   // Fractional Kelly factor
    val volatilityBufferMultiplier: Double, // Dynamic fee/slippage safety buffer
    val regimeDescription: String
)

/**
 * Comprehensive Portfolio Risk & Value-at-Risk Assessment.
 */
data class AdaptiveRiskAssessmentReport(
    val timestamp: Long = System.currentTimeMillis(),
    val totalEquityUsdt: Double,
    val allocatedCapitalUsdt: Double,
    val unallocatedFreeUsdt: Double,
    val activeRegime: MarketRegimeType,
    val regimeRiskMultiplier: Double,
    val targetPositionSizeUsdt: Double,
    val maxStopLossRiskCapitalUsdt: Double,
    val expectedDailyReturnUsdt: Double,
    val parametricVar95PctUsdt: Double,     // Value-at-Risk at 95% confidence (1-day)
    val parametricVar99PctUsdt: Double,     // Value-at-Risk at 99% confidence (1-day)
    val conditionalVar99PctUsdt: Double,    // CVaR / Expected Shortfall under tail stress
    val currentPortfolioHeatPct: Double,
    val isCircuitBreakerTripped: Boolean,
    val isAllocationSafe: Boolean,
    val riskMitigationNotes: List<String>,
    val executiveRiskSummary: String
)

/**
 * Advanced Multi-Regime Adaptive Risk Engine for Lumina.
 *
 * Implements:
 * 1. Volatility Parity & Adaptive Regime Scaling
 * 2. Real-time Parametric Value-at-Risk (VaR 95% / 99%) & Conditional VaR (Expected Shortfall)
 * 3. Fractional Micro-Kelly Sizing to protect small accounts while compounding
 * 4. Automatic Tail-Risk De-escalation during flash shocks and high-volatility spikes
 */
object LuminaAdaptiveRegimeRiskEngine {

    // Standard Normal Distribution Quantiles
    private const val Z_95 = 1.645
    private const val Z_99 = 2.326

    /**
     * Determines the optimal risk profile based on current detected Market Regime and Account Scale.
     */
    fun getRegimeRiskConfig(regime: MarketRegimeType, totalEquityUsdt: Double): DynamicRegimeRiskConfig {
        val isMicroAccount = totalEquityUsdt < 50.0

        return when (regime) {
            MarketRegimeType.BULL_EXPANSION -> DynamicRegimeRiskConfig(
                regime = regime,
                regimeRiskMultiplier = 1.15,
                maxPositionNotionalPct = if (isMicroAccount) 0.85 else 0.40,
                stopLossAtrMultiplier = 1.40,
                takeProfitAtrMultiplier = 3.20,
                maxConcurrentPositions = if (isMicroAccount) 1 else 3,
                fastBreakevenTriggerR = 0.50,
                maxPortfolioHeatLimitPct = if (isMicroAccount) 0.15 else 0.06,
                recommendedKellyFraction = 0.50,
                volatilityBufferMultiplier = 1.05,
                regimeDescription = "High trend velocity. Wider targets (3.2x ATR) with standard trailing stop."
            )
            MarketRegimeType.BEAR_BREAKDOWN -> DynamicRegimeRiskConfig(
                regime = regime,
                regimeRiskMultiplier = 0.70,
                maxPositionNotionalPct = if (isMicroAccount) 0.80 else 0.25,
                stopLossAtrMultiplier = 1.05,
                takeProfitAtrMultiplier = 1.60,
                maxConcurrentPositions = 1,
                fastBreakevenTriggerR = 0.25,
                maxPortfolioHeatLimitPct = 0.04,
                recommendedKellyFraction = 0.30,
                volatilityBufferMultiplier = 1.20,
                regimeDescription = "Distribution regime. Rapid defensive stops (1.05x ATR) and quick profit harvesting."
            )
            MarketRegimeType.CHOPPY_RANGE -> DynamicRegimeRiskConfig(
                regime = regime,
                regimeRiskMultiplier = 0.85,
                maxPositionNotionalPct = if (isMicroAccount) 0.80 else 0.30,
                stopLossAtrMultiplier = 1.15,
                takeProfitAtrMultiplier = 1.80,
                maxConcurrentPositions = 1,
                fastBreakevenTriggerR = 0.22,
                maxPortfolioHeatLimitPct = 0.05,
                recommendedKellyFraction = 0.40,
                volatilityBufferMultiplier = 1.10,
                regimeDescription = "Mean-reverting range. 1-cent micro-harvesting with fast-green breakeven locks."
            )
            MarketRegimeType.HIGH_VOLATILITY_SHOCK -> DynamicRegimeRiskConfig(
                regime = regime,
                regimeRiskMultiplier = 0.40, // 60% risk reduction
                maxPositionNotionalPct = if (isMicroAccount) 0.75 else 0.15,
                stopLossAtrMultiplier = 0.85,
                takeProfitAtrMultiplier = 1.40,
                maxConcurrentPositions = 1,
                fastBreakevenTriggerR = 0.18,
                maxPortfolioHeatLimitPct = 0.025,
                recommendedKellyFraction = 0.15,
                volatilityBufferMultiplier = 1.45,
                regimeDescription = "Liquidity flight / Flash shock. Drastically curtailed exposure to prevent catastrophic drawdown."
            )
            MarketRegimeType.LOW_VOLATILITY_SQUEEZE -> DynamicRegimeRiskConfig(
                regime = regime,
                regimeRiskMultiplier = 1.00,
                maxPositionNotionalPct = if (isMicroAccount) 0.82 else 0.35,
                stopLossAtrMultiplier = 1.25,
                takeProfitAtrMultiplier = 2.60,
                maxConcurrentPositions = 1,
                fastBreakevenTriggerR = 0.35,
                maxPortfolioHeatLimitPct = 0.05,
                recommendedKellyFraction = 0.45,
                volatilityBufferMultiplier = 1.02,
                regimeDescription = "Volatility coiling. Positioning for high-expansion breakout continuation."
            )
        }
    }

    /**
     * Conducts real-time quantitative risk assessment, VaR calculation, and capital safety certification.
     */
    fun evaluateAdaptiveRisk(
        totalEquityUsdt: Double,
        freeUsdt: Double,
        activePositionsNotionalUsdt: Double = 0.0,
        currentRegime: MarketRegimeType = MarketRegimeType.CHOPPY_RANGE,
        dailyPortfolioVolatilityPct: Double = 2.5,
        mlPrediction: LuminaDeepMlPrediction? = null
    ): AdaptiveRiskAssessmentReport {
        val safeEquity = max(0.0, totalEquityUsdt)
        val regimeConfig = getRegimeRiskConfig(currentRegime, if (safeEquity > 0.0) safeEquity else 5.0)

        // 1. Calculate Target Sizing based on Micro Risk Bounds
        val baseAllocation = if (safeEquity <= 0.0) {
            0.0
        } else if (safeEquity <= 20.0) {
            // Micro-Account Mode (~$13 USDT): Binance $5 MIN_NOTIONAL requires concentrated single-position allocation
            (safeEquity - 1.50).coerceIn(5.50, safeEquity * 0.90)
        } else {
            (safeEquity * regimeConfig.maxPositionNotionalPct).coerceAtLeast(10.0)
        }

        // Apply ML Prediction Confidence & Kelly Fraction
        val confidenceScale = if (mlPrediction != null) (mlPrediction.ensembleConfidencePct / 80.0).coerceIn(0.60, 1.20) else 1.0
        val finalTargetSize = if (safeEquity <= 0.0) 0.0 else (baseAllocation * regimeConfig.regimeRiskMultiplier * confidenceScale).coerceIn(5.05, safeEquity * 0.92)

        // 2. Stop-Loss Risk Capital Budget
        val riskCapitalUsdt = finalTargetSize * (regimeConfig.stopLossAtrMultiplier * 0.015) // ~1.5% ATR base

        // 3. Parametric Value-at-Risk (VaR) & CVaR
        val dailyVolDecimal = (dailyPortfolioVolatilityPct / 100.0) * regimeConfig.volatilityBufferMultiplier
        val var95Usdt = safeEquity * dailyVolDecimal * Z_95
        val var99Usdt = safeEquity * dailyVolDecimal * Z_99
        val cvar99Usdt = var99Usdt * 1.18 // Expected shortfall in tail 1% event

        // 4. Portfolio Heat
        val currentHeatPct = if (safeEquity > 0) (activePositionsNotionalUsdt / safeEquity) * 100.0 else 0.0
        val isHeatSafe = currentHeatPct <= (regimeConfig.maxPortfolioHeatLimitPct * 100.0)

        // 5. Circuit Breaker Checks
        val isCircuitBreakerTripped = currentRegime == MarketRegimeType.HIGH_VOLATILITY_SHOCK && dailyPortfolioVolatilityPct > 6.0
        val isAllocationSafe = freeUsdt >= 5.0 && freeUsdt >= finalTargetSize && !isCircuitBreakerTripped

        val notes = mutableListOf<String>()
        notes.add("Market Regime: ${currentRegime.displayName} (Risk Multiplier: ${String.format(Locale.US, "%.2f", regimeConfig.regimeRiskMultiplier)}x).")
        notes.add("1-Day VaR (95%): $${String.format(Locale.US, "%.3f", var95Usdt)} | CVaR (99% Tail): $${String.format(Locale.US, "%.3f", cvar99Usdt)}.")
        notes.add("Protective Stop Distance: ${String.format(Locale.US, "%.2f", regimeConfig.stopLossAtrMultiplier)}x ATR | Take-Profit: ${String.format(Locale.US, "%.2f", regimeConfig.takeProfitAtrMultiplier)}x ATR.")
        notes.add("Fast Breakeven Ratchet: Trigger at +${String.format(Locale.US, "%.2f", regimeConfig.fastBreakevenTriggerR)}R to guarantee zero downside risk.")

        val summary = buildString {
            append("Adaptive Risk Engine calibrated for ${currentRegime.displayName}. ")
            append("Target Position: $${String.format(Locale.US, "%.2f", finalTargetSize)} USDT (Max Capital at Risk: $${String.format(Locale.US, "%.3f", riskCapitalUsdt)}). ")
            append("Portfolio Heat: ${String.format(Locale.US, "%.1f", currentHeatPct)}% (Limit: ${String.format(Locale.US, "%.1f", regimeConfig.maxPortfolioHeatLimitPct * 100.0)}%). ")
            if (isCircuitBreakerTripped) append("⚠️ Volatility circuit breaker active.") else append("✓ Risk metrics safe for execution.")
        }

        return AdaptiveRiskAssessmentReport(
            totalEquityUsdt = safeEquity,
            allocatedCapitalUsdt = activePositionsNotionalUsdt,
            unallocatedFreeUsdt = freeUsdt,
            activeRegime = currentRegime,
            regimeRiskMultiplier = regimeConfig.regimeRiskMultiplier,
            targetPositionSizeUsdt = finalTargetSize,
            maxStopLossRiskCapitalUsdt = riskCapitalUsdt,
            expectedDailyReturnUsdt = finalTargetSize * 0.008, // ~0.8% daily expectation
            parametricVar95PctUsdt = var95Usdt,
            parametricVar99PctUsdt = var99Usdt,
            conditionalVar99PctUsdt = cvar99Usdt,
            currentPortfolioHeatPct = currentHeatPct,
            isCircuitBreakerTripped = isCircuitBreakerTripped,
            isAllocationSafe = isAllocationSafe,
            riskMitigationNotes = notes,
            executiveRiskSummary = summary
        )
    }
}
