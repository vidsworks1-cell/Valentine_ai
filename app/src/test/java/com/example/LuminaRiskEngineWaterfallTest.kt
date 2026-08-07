package com.example

import com.example.aza.strategy.LuminaRiskEngine
import com.example.aza.strategy.StrategyState
import com.example.aza.strategy.TradeActionIntent
import com.example.aza.strategy.UncertaintyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuminaRiskEngineWaterfallTest {

    @Test
    fun testExplicitTenStageRiskWaterfallPipeline() {
        val equity = 10000.0
        val entry = 600.0
        val sl = 588.0 // 2% distance = 0.02

        val result = LuminaRiskEngine.calculateRiskWaterfall(
            symbol = "BNBUSDT",
            side = "BUY",
            accountEquity = equity,
            entryPrice = entry,
            stopLossPrice = sl,
            baseRiskPct = 0.015, // 1.5%
            strategyState = StrategyState.ACTIVE,
            marketRegime = "TRENDING",
            volatilityState = "NORMAL",
            recentWinRatePct = 58.0,
            recentExpectancyR = 0.45,
            sampleSize = 35,
            uncertaintyLevel = UncertaintyLevel.LOW,
            isProbation = false,
            portfolioHeatPct = 3.0,
            dailyDrawdownUSD = 0.0,
            maxDailyLossUSD = 200.0,
            correlationExposure = 0.20,
            actionIntent = TradeActionIntent.NEW_POSITION,
            portfolioVolatilityPct = 10.0,
            symbolConcentrationPct = 0.10
        )

        assertNotNull(result)
        assertEquals(10, result.stages.size)

        // Verify stage names in order
        val expectedStages = listOf(
            "BASE_RISK",
            "STRATEGY_ADJUSTMENT",
            "REGIME_ADJUSTMENT",
            "VOLATILITY_ADJUSTMENT",
            "PERFORMANCE_ADJUSTMENT",
            "PROBATION_ADJUSTMENT",
            "PORTFOLIO_HEAT_ADJUSTMENT",
            "CORRELATION_ADJUSTMENT",
            "POSITION_EXPOSURE_ADJUSTMENT",
            "FINAL_RISK"
        )
        for (i in expectedStages.indices) {
            assertEquals(expectedStages[i], result.stages[i].stageName)
        }

        // Verify bounds & stage outputs
        result.stages.forEach { stage ->
            assertTrue("Output must meet min bound", stage.boundedOutputRiskPct >= 0.0)
            assertTrue("Stage multiplier must be positive or zero", stage.multiplier >= 0.0)
            assertTrue("Reason must not be empty", stage.reason.isNotEmpty())
        }

        // Verify risk percentage <= 2.0% (max equity risk limit)
        assertTrue("Final risk must not exceed max limit of 2.0%", result.finalRiskPct <= 0.020)
    }

    @Test
    fun testDoubleCountingPreventionInVolatilityAdjustment() {
        // Regime is CHOP/VOLATILE (penalizes volatility) and volatility state is EXTREME
        val result = LuminaRiskEngine.calculateRiskWaterfall(
            symbol = "BTCUSDT",
            side = "BUY",
            marketRegime = "VOLATILE_CHOP",
            volatilityState = "EXTREME_VOLATILITY",
            explicitRegimeMult = 0.60,
            explicitVolMult = 0.50
        )

        val volStage = result.stages.first { it.stageName == "VOLATILITY_ADJUSTMENT" }
        assertTrue("Double counting must be explicitly prevented", volStage.preventedDoubleCounting)
        assertEquals(0.85, volStage.multiplier, 0.001) // Dampened from 0.50 to 0.85 to avoid double penalizing
    }

    @Test
    fun testDoubleCountingPreventionInPortfolioHeatAdjustment() {
        val result = LuminaRiskEngine.calculateRiskWaterfall(
            symbol = "ETHUSDT",
            side = "BUY",
            recentWinRatePct = 35.0,
            recentExpectancyR = -0.10,
            explicitPerfMult = 0.60,
            explicitPortfolioHeatMult = 0.50
        )

        val heatStage = result.stages.first { it.stageName == "PORTFOLIO_HEAT_ADJUSTMENT" }
        assertTrue("Heat double-counting must be explicitly prevented when performance already penalized", heatStage.preventedDoubleCounting)
        assertEquals(0.80, heatStage.multiplier, 0.001) // Dampened from 0.50 to 0.80
    }

    @Test
    fun testExplicitConversions() {
        val equity = 20000.0
        val entry = 100.0
        val sl = 95.0 // SL distance = (100 - 95)/100 = 0.05 (5%)

        val result = LuminaRiskEngine.calculateRiskWaterfall(
            symbol = "SOLUSDT",
            side = "BUY",
            accountEquity = equity,
            entryPrice = entry,
            stopLossPrice = sl,
            baseRiskPct = 0.010, // 1.0% = $200
            explicitStrategyMult = 1.0,
            explicitRegimeMult = 1.0,
            explicitVolMult = 1.0,
            explicitPerfMult = 1.0,
            explicitProbationMult = 1.0,
            explicitPortfolioHeatMult = 1.0,
            explicitCorrelationMult = 1.0,
            explicitPositionMult = 1.0
        )

        // Final risk = 1.0% = 0.01
        assertEquals(0.01, result.finalRiskPct, 0.0001)

        // Risk Capital USD = $20000 * 0.01 = $200.0
        assertEquals(200.0, result.riskCapitalUSD, 0.01)

        // Position Notional USD = $200.0 / 0.05 = $4000.0
        assertEquals(4000.0, result.positionNotionalUSD, 0.01)

        // Position Quantity = $4000.0 / 100.0 = 40.0 units
        assertEquals(40.0, result.positionQuantity, 0.01)
    }

    @Test
    fun testUserPromptScenarioMultipliersAudit() {
        // Re-enact prompt's scenario:
        // Base Risk = 1.5%
        // Strategy = 0.5, Regime = 0.5, Volatility = 1.0, Streak/DD = 1.0, Probation = 0.25
        val result = LuminaRiskEngine.calculateRiskWaterfall(
            symbol = "BTCUSDT",
            side = "BUY",
            accountEquity = 10000.0,
            entryPrice = 60000.0,
            stopLossPrice = 58800.0,
            baseRiskPct = 0.015,
            explicitStrategyMult = 0.5,
            explicitRegimeMult = 0.5,
            explicitVolMult = 1.0,
            explicitPerfMult = 1.0,
            explicitProbationMult = 0.25,
            explicitPortfolioHeatMult = 1.0,
            explicitCorrelationMult = 1.0,
            explicitPositionMult = 1.0
        )

        // Expected output: 0.015 * 0.5 * 0.5 * 1.0 * 1.0 * 0.25 = 0.0009375 ≈ 0.09375%
        assertEquals(0.0009375, result.finalRiskPct, 0.00001)

        // Conversions: Risk Capital = $10,000 * 0.0009375 = $9.375
        assertEquals(9.375, result.riskCapitalUSD, 0.01)

        // Stop Loss distance = 1200 / 60000 = 0.02 (2.0%)
        // Position Notional = $9.375 / 0.02 = $468.75
        assertEquals(468.75, result.positionNotionalUSD, 0.01)

        // Position Quantity = 468.75 / 60000 = 0.0078125 BTC
        assertEquals(0.0078125, result.positionQuantity, 0.0001)
    }

    @Test
    fun testRiskLimitSafetyCeilingNotExceeded() {
        val result = LuminaRiskEngine.calculateRiskWaterfall(
            baseRiskPct = 0.050, // 5.0% requested base risk
            explicitStrategyMult = 2.0,
            explicitRegimeMult = 2.0
        )

        // Must be clamped to absolute maximum system limit of 2.0% (0.02)
        assertEquals(LuminaRiskEngine.MAX_EQUITY_RISK_LIMIT_PCT, result.finalRiskPct, 0.00001)
        assertTrue("System risk limits must never be increased", result.finalRiskPct <= 0.020)
    }
}
