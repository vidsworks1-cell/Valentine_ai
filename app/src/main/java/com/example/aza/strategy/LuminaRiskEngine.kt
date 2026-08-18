package com.example.aza.strategy

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Stage in the explicit Lumina Risk Waterfall pipeline.
 */
data class RiskWaterfallStage(
    val stageName: String,
    val inputRiskPct: Double,
    val multiplier: Double,
    val rawOutputRiskPct: Double,
    val boundedOutputRiskPct: Double,
    val minBound: Double,
    val maxBound: Double,
    val reason: String,
    val preventedDoubleCounting: Boolean = false
)

/**
 * Complete audit result of the explicit Lumina Risk Waterfall.
 */
data class RiskWaterfallResult(
    val baseRiskPct: Double,
    val finalRiskPct: Double,
    val riskCapitalUSD: Double,
    val positionNotionalUSD: Double,
    val positionQuantity: Double,
    val stages: List<RiskWaterfallStage>,
    val accountEquity: Double,
    val entryPrice: Double,
    val stopLossPrice: Double,
    val stopLossDistancePct: Double,
    val auditSummary: String
)

/**
 * Lumina Risk Engine — Multiplier Audit & Refinement.
 *
 * Implements an explicit, deterministic Risk Waterfall pipeline:
 * BASE_RISK
 * -> STRATEGY_ADJUSTMENT
 * -> REGIME_ADJUSTMENT
 * -> VOLATILITY_ADJUSTMENT
 * -> PERFORMANCE_ADJUSTMENT
 * -> PROBATION_ADJUSTMENT
 * -> PORTFOLIO_HEAT_ADJUSTMENT
 * -> CORRELATION_ADJUSTMENT
 * -> POSITION/EXPOSURE_ADJUSTMENT
 * -> FINAL_RISK
 *
 * Prevents accidental double/triple scaling by enforcing bounded stage multipliers,
 * cross-stage double-counting checks, and explicit conversions between equity risk %,
 * risk capital USD, position notional USD, and position quantity.
 */
object LuminaRiskEngine {

    // Global Risk Limits — DO NOT INCREASE RISK LIMITS
    const val MAX_EQUITY_RISK_LIMIT_PCT: Double = 0.020 // 2.0% Max Risk per Trade
    const val MIN_EQUITY_RISK_LIMIT_PCT: Double = 0.0001 // 0.01% Min Risk per Trade
    const val DEFAULT_BASE_RISK_PCT: Double = 0.015 // 1.5% Baseline Risk

    /**
     * Executes the 10-stage explicit Risk Waterfall calculation.
     */
    fun calculateRiskWaterfall(
        symbol: String = "BTCUSDT",
        side: String = "BUY",
        accountEquity: Double = 10000.0,
        entryPrice: Double = 60000.0,
        stopLossPrice: Double = 58800.0,
        baseRiskPct: Double = DEFAULT_BASE_RISK_PCT,
        strategyState: StrategyState = StrategyState.ACTIVE,
        marketRegime: String = "NORMAL",
        volatilityState: String = "NORMAL",
        recentWinRatePct: Double = 55.0,
        recentExpectancyR: Double = 0.50,
        sampleSize: Int = 30,
        uncertaintyLevel: UncertaintyLevel = UncertaintyLevel.LOW,
        isProbation: Boolean = false,
        portfolioHeatPct: Double = 4.0,
        portfolio24hDrawdownPct: Double = 0.0,
        dailyDrawdownUSD: Double = 0.0,
        maxDailyLossUSD: Double = 200.0,
        correlationExposure: Double = 0.0,
        orderBookDepthWithin05PctUSD: Double = 100000.0,
        actionIntent: TradeActionIntent = TradeActionIntent.NEW_POSITION,
        portfolioVolatilityPct: Double = 12.0,
        symbolConcentrationPct: Double = 0.15,
        isSpreadHedge: Boolean = false,
        strategyName: String = "",
        explicitStrategyMult: Double? = null,
        explicitRegimeMult: Double? = null,
        explicitVolMult: Double? = null,
        explicitPerfMult: Double? = null,
        explicitProbationMult: Double? = null,
        explicitPortfolioHeatMult: Double? = null,
        explicitCorrelationMult: Double? = null,
        explicitPositionMult: Double? = null
    ): RiskWaterfallResult {
        val stages = mutableListOf<RiskWaterfallStage>()
        val safeEquity = max(1.0, accountEquity)
        val safeEntryPrice = max(0.000001, entryPrice)
        val rawSlDistance = abs(safeEntryPrice - stopLossPrice) / safeEntryPrice
        val stopLossDistancePct = max(0.001, rawSlDistance) // Min 0.1% stop loss distance

        // Stage 1: BASE_RISK
        val boundedBaseRisk = baseRiskPct.coerceIn(MIN_EQUITY_RISK_LIMIT_PCT, MAX_EQUITY_RISK_LIMIT_PCT)
        val stage1 = RiskWaterfallStage(
            stageName = "BASE_RISK",
            inputRiskPct = baseRiskPct,
            multiplier = 1.0,
            rawOutputRiskPct = baseRiskPct,
            boundedOutputRiskPct = boundedBaseRisk,
            minBound = MIN_EQUITY_RISK_LIMIT_PCT,
            maxBound = MAX_EQUITY_RISK_LIMIT_PCT,
            reason = "Baseline equity risk input (${String.format(Locale.US, "%.2f", baseRiskPct * 100)}%) bounded to [0.1%, 2.0%]."
        )
        stages.add(stage1)
        var currentRisk = stage1.boundedOutputRiskPct

        // Stage 2: STRATEGY_ADJUSTMENT
        val stratMult = explicitStrategyMult ?: when (strategyState) {
            StrategyState.CHAMPION -> 1.00
            StrategyState.ACTIVE -> 0.85
            StrategyState.PRELIMINARY_OBSERVATION -> 0.60
            StrategyState.RESTRICTED -> 0.45
            StrategyState.DEGRADED -> 0.25
            StrategyState.RECOVERY -> 0.20
            StrategyState.SUSPENDED -> 0.00
        }
        val stratMin = 0.0
        val stratMax = 1.0
        val rawStratOutput = currentRisk * stratMult
        val boundedStratOutput = rawStratOutput.coerceIn(currentRisk * stratMin, currentRisk * stratMax)
        val stage2 = RiskWaterfallStage(
            stageName = "STRATEGY_ADJUSTMENT",
            inputRiskPct = currentRisk,
            multiplier = stratMult,
            rawOutputRiskPct = rawStratOutput,
            boundedOutputRiskPct = boundedStratOutput,
            minBound = stratMin,
            maxBound = stratMax,
            reason = "Strategy state (${strategyState.displayName}) multiplier: ${String.format(Locale.US, "%.2f", stratMult)}"
        )
        stages.add(stage2)
        currentRisk = stage2.boundedOutputRiskPct

        // Stage 3: REGIME_ADJUSTMENT
        val regimeMult = explicitRegimeMult ?: when {
            marketRegime.contains("TREND") || marketRegime.contains("BULLISH") || marketRegime.contains("BEARISH") -> 1.00
            marketRegime.contains("BREAKOUT") || marketRegime.contains("MOMENTUM") -> 1.00
            marketRegime.contains("RANGE") || marketRegime.contains("NORMAL") -> 0.85
            marketRegime.contains("VOLATIL") || marketRegime.contains("CHOP") -> 0.60
            marketRegime.contains("DEEP_DRAWDOWN") -> 0.40
            else -> 0.80
        }
        val regimeMin = 0.25
        val regimeMax = 1.25
        val rawRegimeOutput = currentRisk * regimeMult
        val boundedRegimeOutput = rawRegimeOutput.coerceIn(currentRisk * regimeMin, currentRisk * regimeMax)
        val stage3 = RiskWaterfallStage(
            stageName = "REGIME_ADJUSTMENT",
            inputRiskPct = currentRisk,
            multiplier = regimeMult,
            rawOutputRiskPct = rawRegimeOutput,
            boundedOutputRiskPct = boundedRegimeOutput,
            minBound = regimeMin,
            maxBound = regimeMax,
            reason = "Market regime ($marketRegime) structural trend fit multiplier: ${String.format(Locale.US, "%.2f", regimeMult)}"
        )
        stages.add(stage3)
        currentRisk = stage3.boundedOutputRiskPct

        // Stage 4: VOLATILITY_ADJUSTMENT (Double-counting check against Regime Adjustment)
        val baseVolFactor = explicitVolMult ?: when {
            volatilityState.contains("EXTREME") -> 0.50
            volatilityState.contains("HIGH") -> 0.70
            volatilityState.contains("LOW") -> 1.10
            else -> 1.00
        }
        val regimeAlreadyPenalizedVol = regimeMult <= 0.65 && (marketRegime.contains("VOLATIL") || marketRegime.contains("CHOP"))
        var effectiveVolMult = baseVolFactor
        var volDoubleCountPrevented = false

        if (regimeAlreadyPenalizedVol && baseVolFactor < 1.0) {
            // Prevent double-counting volatility penalty if regime already reduced risk by >= 35% due to volatility
            effectiveVolMult = max(baseVolFactor, 0.85)
            volDoubleCountPrevented = true
        }

        val volMin = 0.30
        val volMax = 1.20
        val rawVolOutput = currentRisk * effectiveVolMult
        val boundedVolOutput = rawVolOutput.coerceIn(currentRisk * volMin, currentRisk * volMax)
        val stage4Reason = if (volDoubleCountPrevented) {
            "Volatility state ($volatilityState) multiplier adjusted to ${String.format(Locale.US, "%.2f", effectiveVolMult)} (DAMPENED: Regime adjustment already penalized volatility)."
        } else {
            "Volatility state ($volatilityState) multiplier: ${String.format(Locale.US, "%.2f", effectiveVolMult)}"
        }
        val stage4 = RiskWaterfallStage(
            stageName = "VOLATILITY_ADJUSTMENT",
            inputRiskPct = currentRisk,
            multiplier = effectiveVolMult,
            rawOutputRiskPct = rawVolOutput,
            boundedOutputRiskPct = boundedVolOutput,
            minBound = volMin,
            maxBound = volMax,
            reason = stage4Reason,
            preventedDoubleCounting = volDoubleCountPrevented
        )
        stages.add(stage4)
        currentRisk = stage4.boundedOutputRiskPct

        // Stage 5: PERFORMANCE_ADJUSTMENT (with Regime × Symbol × Strategy Learning Matrix Integration)
        val matrixAdapt = if (strategyName.isNotBlank()) {
            RegimeSymbolStrategyLearningMatrix.getAdaptation(
                symbol = symbol,
                regime = marketRegime,
                strategy = strategyName
            )
        } else null

        val basePerfMult = explicitPerfMult ?: when {
            recentWinRatePct >= 60.0 && recentExpectancyR >= 0.60 -> 1.10
            recentWinRatePct >= 50.0 && recentExpectancyR >= 0.30 -> 1.00
            recentWinRatePct >= 40.0 -> 0.85
            else -> 0.60
        }
        val perfMult = if (matrixAdapt != null) {
            (basePerfMult * matrixAdapt.riskMultiplier).coerceIn(0.20, 1.25)
        } else basePerfMult

        val perfMin = 0.20
        val perfMax = 1.25
        val rawPerfOutput = currentRisk * perfMult
        val boundedPerfOutput = rawPerfOutput.coerceIn(currentRisk * perfMin, currentRisk * perfMax)
        val stage5Reason = if (matrixAdapt != null) {
            "Performance & Learning Matrix (${matrixAdapt.tier.displayName}, WR: ${String.format(Locale.US, "%.1f", recentWinRatePct)}%, Exp: ${String.format(Locale.US, "%.2f", recentExpectancyR)}R, MatrixRisk: x${String.format(Locale.US, "%.2f", matrixAdapt.riskMultiplier)}) multiplier: ${String.format(Locale.US, "%.2f", perfMult)}"
        } else {
            "Recent performance (WR: ${String.format(Locale.US, "%.1f", recentWinRatePct)}%, Exp: ${String.format(Locale.US, "%.2f", recentExpectancyR)}R) multiplier: ${String.format(Locale.US, "%.2f", perfMult)}"
        }
        val stage5 = RiskWaterfallStage(
            stageName = "PERFORMANCE_ADJUSTMENT",
            inputRiskPct = currentRisk,
            multiplier = perfMult,
            rawOutputRiskPct = rawPerfOutput,
            boundedOutputRiskPct = boundedPerfOutput,
            minBound = perfMin,
            maxBound = perfMax,
            reason = stage5Reason
        )
        stages.add(stage5)
        currentRisk = stage5.boundedOutputRiskPct

        // Stage 6: PROBATION_ADJUSTMENT
        val probationMult = explicitProbationMult ?: when {
            isProbation || strategyState == StrategyState.PRELIMINARY_OBSERVATION -> 0.25
            sampleSize < 20 || uncertaintyLevel == UncertaintyLevel.HIGH -> 0.50
            sampleSize in 20..49 || uncertaintyLevel == UncertaintyLevel.MEDIUM -> 0.80
            else -> 1.00
        }
        val probationMin = 0.25
        val probationMax = 1.00
        val rawProbationOutput = currentRisk * probationMult
        val boundedProbationOutput = rawProbationOutput.coerceIn(currentRisk * probationMin, currentRisk * probationMax)
        val stage6 = RiskWaterfallStage(
            stageName = "PROBATION_ADJUSTMENT",
            inputRiskPct = currentRisk,
            multiplier = probationMult,
            rawOutputRiskPct = rawProbationOutput,
            boundedOutputRiskPct = boundedProbationOutput,
            minBound = probationMin,
            maxBound = probationMax,
            reason = "Sample size N=$sampleSize, probation=$isProbation, uncertainty=$uncertaintyLevel multiplier: ${String.format(Locale.US, "%.2f", probationMult)}"
        )
        stages.add(stage6)
        currentRisk = stage6.boundedOutputRiskPct

        // Stage 7: PORTFOLIO_HEAT_ADJUSTMENT & THERMAL THROTTLING
        val lossRatio = if (maxDailyLossUSD > 0) dailyDrawdownUSD / maxDailyLossUSD else 0.0
        val isThermalThrottleActive = portfolio24hDrawdownPct <= -3.0
        val baseHeatMult = explicitPortfolioHeatMult ?: when {
            isThermalThrottleActive -> 0.50 // Thermal Throttle drops max risk cap from 6.0% to 3.0%
            lossRatio >= 0.75 -> 0.50
            portfolioHeatPct > 10.0 -> 0.70
            portfolioHeatPct > 5.0 -> 0.85
            else -> 1.00
        }

        var heatDoubleCountPrevented = false
        var effectiveHeatMult = baseHeatMult

        // If Performance Adjustment or Strategy Adjustment already penalized to <= 0.3x, cap heat penalty
        if (perfMult <= 0.60 && baseHeatMult < 1.0 && !isThermalThrottleActive) {
            effectiveHeatMult = max(baseHeatMult, 0.80)
            heatDoubleCountPrevented = true
        }

        val heatMin = 0.10
        val heatMax = 1.00
        val rawHeatOutput = currentRisk * effectiveHeatMult
        val boundedHeatOutput = rawHeatOutput.coerceIn(currentRisk * heatMin, currentRisk * heatMax)
        val stage7Reason = when {
            isThermalThrottleActive -> "🔥 PORTFOLIO THERMAL THROTTLE ACTIVATED: 24h drawdown (${String.format(Locale.US, "%.1f", portfolio24hDrawdownPct)}%) crossed -3.0% threshold. Capped total portfolio risk allowance from 6.0% down to 3.0%."
            heatDoubleCountPrevented -> "Portfolio heat (${String.format(Locale.US, "%.1f", portfolioHeatPct)}%) multiplier adjusted to ${String.format(Locale.US, "%.2f", effectiveHeatMult)} (DAMPENED: Performance adjustment already penalized account drawdown)."
            else -> "Portfolio heat (${String.format(Locale.US, "%.1f", portfolioHeatPct)}%), daily loss ratio (${String.format(Locale.US, "%.1f", lossRatio * 100)}%) multiplier: ${String.format(Locale.US, "%.2f", effectiveHeatMult)}"
        }
        val stage7 = RiskWaterfallStage(
            stageName = "PORTFOLIO_HEAT_ADJUSTMENT",
            inputRiskPct = currentRisk,
            multiplier = effectiveHeatMult,
            rawOutputRiskPct = rawHeatOutput,
            boundedOutputRiskPct = boundedHeatOutput,
            minBound = heatMin,
            maxBound = heatMax,
            reason = stage7Reason,
            preventedDoubleCounting = heatDoubleCountPrevented
        )
        stages.add(stage7)
        currentRisk = stage7.boundedOutputRiskPct

        // Stage 8: CORRELATION_ADJUSTMENT
        val corrMult = explicitCorrelationMult ?: when {
            isSpreadHedge -> 1.00 // Waive correlation penalty for spread hedges
            correlationExposure > 0.80 -> 0.50
            correlationExposure > 0.60 -> 0.75
            else -> 1.00
        }
        val corrMin = 0.20
        val corrMax = 1.00
        val rawCorrOutput = currentRisk * corrMult
        val boundedCorrOutput = rawCorrOutput.coerceIn(currentRisk * corrMin, currentRisk * corrMax)
        val stage8Reason = if (isSpreadHedge) {
            "Delta-neutral spread hedge detected. Same-direction correlation penalty waived (multiplier: 1.00x)."
        } else {
            "Correlation exposure (${String.format(Locale.US, "%.2f", correlationExposure)}) multiplier: ${String.format(Locale.US, "%.2f", corrMult)}"
        }
        val stage8 = RiskWaterfallStage(
            stageName = "CORRELATION_ADJUSTMENT",
            inputRiskPct = currentRisk,
            multiplier = corrMult,
            rawOutputRiskPct = rawCorrOutput,
            boundedOutputRiskPct = boundedCorrOutput,
            minBound = corrMin,
            maxBound = corrMax,
            reason = stage8Reason
        )
        stages.add(stage8)
        currentRisk = stage8.boundedOutputRiskPct

        // Stage 9: POSITION_EXPOSURE_ADJUSTMENT & MICROSTRUCTURE LIQUIDITY GUARD
        var intentMult = explicitPositionMult ?: when (actionIntent) {
            TradeActionIntent.NEW_POSITION -> 1.00
            TradeActionIntent.SCALE_IN -> 0.50
            TradeActionIntent.HEDGE -> 0.80
            TradeActionIntent.REVERSAL -> 0.70
            TradeActionIntent.REDUCE_POSITION -> 0.50
        }
        if (explicitPositionMult == null && symbolConcentrationPct > 0.25) {
            intentMult *= 0.80
        }

        // Microstructure Liquidity Guard: Check if depth within 0.5% of mid-price can absorb position size without excessive market impact
        val estimatedNotionalUSD = safeEquity * currentRisk / stopLossDistancePct
        var liquidityScaleMult = 1.0
        var liquidityGuardReason = ""
        if (explicitPositionMult == null && orderBookDepthWithin05PctUSD > 0.0) {
            val estimatedImpactBps = (estimatedNotionalUSD / orderBookDepthWithin05PctUSD) * 50.0
            if (estimatedNotionalUSD > orderBookDepthWithin05PctUSD * 0.80 || estimatedImpactBps > 20.0) {
                liquidityScaleMult = (orderBookDepthWithin05PctUSD / max(1.0, estimatedNotionalUSD)).coerceIn(0.25, 0.90)
                intentMult *= liquidityScaleMult
                liquidityGuardReason = " | 💧 LIQUIDITY GUARD ACTIVATED: Order book depth ($${String.format(Locale.US, "%.0f", orderBookDepthWithin05PctUSD)} within 0.5%) cannot absorb $${String.format(Locale.US, "%.0f", estimatedNotionalUSD)} position without > 20.0 bps impact (${String.format(Locale.US, "%.1f", estimatedImpactBps)} bps est.). Sizing scaled by ${String.format(Locale.US, "%.2f", liquidityScaleMult)}x."
            }
        }

        val posMin = 0.10
        val posMax = 1.00
        val rawPosOutput = currentRisk * intentMult
        val boundedPosOutput = rawPosOutput.coerceIn(currentRisk * posMin, currentRisk * posMax)
        val stage9 = RiskWaterfallStage(
            stageName = "POSITION_EXPOSURE_ADJUSTMENT",
            inputRiskPct = currentRisk,
            multiplier = intentMult,
            rawOutputRiskPct = rawPosOutput,
            boundedOutputRiskPct = boundedPosOutput,
            minBound = posMin,
            maxBound = posMax,
            reason = "Action intent (${actionIntent.name}), concentration (${String.format(Locale.US, "%.1f", symbolConcentrationPct * 100)}%) multiplier: ${String.format(Locale.US, "%.2f", intentMult)}$liquidityGuardReason"
        )
        stages.add(stage9)
        currentRisk = stage9.boundedOutputRiskPct

        // Stage 10: FINAL_RISK
        val finalRiskPct = currentRisk.coerceIn(MIN_EQUITY_RISK_LIMIT_PCT, MAX_EQUITY_RISK_LIMIT_PCT)
        val stage10 = RiskWaterfallStage(
            stageName = "FINAL_RISK",
            inputRiskPct = currentRisk,
            multiplier = 1.0,
            rawOutputRiskPct = currentRisk,
            boundedOutputRiskPct = finalRiskPct,
            minBound = MIN_EQUITY_RISK_LIMIT_PCT,
            maxBound = MAX_EQUITY_RISK_LIMIT_PCT,
            reason = "Final equity risk percentage clamped to absolute system safety limit [0.01%, 2.00%]."
        )
        stages.add(stage10)

        // Explicit conversions
        val riskCapitalUSD = safeEquity * finalRiskPct
        val positionNotionalUSD = riskCapitalUSD / stopLossDistancePct
        val positionQuantity = positionNotionalUSD / safeEntryPrice

        val summary = buildString {
            append("LUMINA RISK WATERFALL AUDIT:\n")
            append("Symbol: $symbol $side | Equity: $${String.format(Locale.US, "%.2f", safeEquity)} | Entry: $${String.format(Locale.US, "%.2f", safeEntryPrice)} | SL: $${String.format(Locale.US, "%.2f", stopLossPrice)} (Dist: ${String.format(Locale.US, "%.2f", stopLossDistancePct * 100)}%)\n")
            append("Base Risk: ${String.format(Locale.US, "%.2f", baseRiskPct * 100)}% -> Final Risk: ${String.format(Locale.US, "%.4f", finalRiskPct * 100)}%\n")
            append("CONVERSIONS: Risk Capital = $${String.format(Locale.US, "%.2f", riskCapitalUSD)} USD | Position Notional = $${String.format(Locale.US, "%.2f", positionNotionalUSD)} USD | Quantity = ${String.format(Locale.US, "%.4f", positionQuantity)} units\n")
            append("STAGES:\n")
            stages.forEach { stage ->
                append("  [${stage.stageName}] In: ${String.format(Locale.US, "%.4f", stage.inputRiskPct * 100)}% * Mult: ${String.format(Locale.US, "%.2f", stage.multiplier)} -> Out: ${String.format(Locale.US, "%.4f", stage.boundedOutputRiskPct * 100)}% (Bounds: [${String.format(Locale.US, "%.2f", stage.minBound)}, ${String.format(Locale.US, "%.2f", stage.maxBound)}]) | ${stage.reason}\n")
            }
        }

        return RiskWaterfallResult(
            baseRiskPct = baseRiskPct,
            finalRiskPct = finalRiskPct,
            riskCapitalUSD = riskCapitalUSD,
            positionNotionalUSD = positionNotionalUSD,
            positionQuantity = positionQuantity,
            stages = stages,
            accountEquity = safeEquity,
            entryPrice = safeEntryPrice,
            stopLossPrice = stopLossPrice,
            stopLossDistancePct = stopLossDistancePct,
            auditSummary = summary
        )
    }
}
