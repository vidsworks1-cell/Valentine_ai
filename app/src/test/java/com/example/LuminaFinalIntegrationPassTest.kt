package com.example

import com.example.aza.strategy.CounterfactualLifecycleStage
import com.example.aza.strategy.DecisionAuthority
import com.example.aza.strategy.LuminaDecisionQualityEngine
import com.example.aza.strategy.LuminaMissedOpportunityEngine
import com.example.aza.strategy.LuminaRiskEngine
import com.example.aza.strategy.MarketCandle
import com.example.aza.strategy.SignalType
import com.example.aza.strategy.StrategyEvaluationEngine
import com.example.aza.strategy.TradeActionIntent
import com.example.aza.strategy.TradeProposal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LuminaFinalIntegrationPassTest {

    @Before
    fun setUp() {
        LuminaMissedOpportunityEngine.clearLedger()
        LuminaDecisionQualityEngine.clearLedger()
    }

    @Test
    fun testRule1_NoStaleStrategyParametersSurviveSubstitution() {
        val prices = listOf(100.0, 102.0, 104.0, 106.0, 108.0, 110.0, 112.0, 114.0, 116.0, 118.0, 120.0, 122.0, 124.0, 126.0, 128.0)
        val proposal = TradeProposal(
            strategyName = "Mean Reversion",
            market = "BTCUSDT",
            signal = SignalType.BUY,
            confidenceScore = 60.0,
            entryPrice = 128.0,
            stopLoss = 124.0,
            takeProfit = 136.0,
            positionSize = 2.0,
            riskRewardRatio = 2.0,
            explanation = "Testing Mean Reversion proposal"
        )
        val decision = StrategyEvaluationEngine.buildPipelineDecisionContext(
            symbol = "BTCUSDT",
            side = "BUY",
            requestedStrategy = "Mean Reversion",
            proposal = proposal,
            prices = prices,
            accountBalance = 10000.0,
            sampleSize = 25,
            recentWinRatePct = 35.0, // Low win rate triggers substitution
            recentExpectancyR = 0.05
        )

        assertNotNull(decision)
        if (decision.wasSubstituted) {
            assertNotEquals("Mean Reversion", decision.effectiveStrategy)
            assertTrue(decision.substitutionReason.contains("underperforming") || decision.substitutionReason.contains("retaining"))
        }
    }

    @Test
    fun testRule3And4_HardGuardsAndPortfolioGuardRemainFinalAuthority() {
        val prices = listOf(100.0, 98.0, 96.0, 94.0, 92.0, 90.0, 88.0, 86.0, 84.0, 82.0, 80.0, 78.0, 76.0, 74.0, 72.0)
        val proposal = TradeProposal(
            strategyName = "Trend Following",
            market = "BNBUSDT",
            signal = SignalType.BUY,
            confidenceScore = 70.0,
            entryPrice = 72.0,
            stopLoss = 68.0,
            takeProfit = 80.0,
            positionSize = 10.0,
            riskRewardRatio = 2.0,
            explanation = "Testing Daily Loss Breach"
        )
        val decision = StrategyEvaluationEngine.buildPipelineDecisionContext(
            symbol = "BNBUSDT",
            side = "BUY",
            requestedStrategy = "Trend Following",
            proposal = proposal,
            prices = prices,
            accountBalance = 10000.0,
            dailyPnL = -250.0 // Loss breaches $200 daily max loss guard
        )

        assertFalse("Trade must NOT be authorized when hard daily loss guard fails", decision.isExecutionAuthorized)
        assertNotEquals(DecisionAuthority.NONE, decision.rejectionAuthority)
        assertTrue(decision.decisionState.name.contains("REJECTED"))
    }

    @Test
    fun testRule5_RiskDoubleCountingPreventedInWaterfall() {
        val waterfall = LuminaRiskEngine.calculateRiskWaterfall(
            symbol = "BTCUSDT",
            side = "BUY",
            marketRegime = "VOLATILE_CHOP",
            volatilityState = "EXTREME_VOLATILITY",
            explicitRegimeMult = 0.60,
            explicitVolMult = 0.50
        )

        val volStage = waterfall.stages.first { it.stageName == "VOLATILITY_ADJUSTMENT" }
        assertTrue("Double counting must be prevented when regime already penalized volatility", volStage.preventedDoubleCounting)
        assertEquals(0.85, volStage.multiplier, 0.001)
    }

    @Test
    fun testRule8_ChampionStatusRequiresSufficientEvidence() {
        val canAdaptSmallSample = LuminaDecisionQualityEngine.canSubsystemAdapt("PortfolioGuard")
        assertFalse("Champion adaptation requires sample size >= 30", canAdaptSmallSample)
    }

    @Test
    fun testRule10_MissedOpportunitiesNotLossesAvoidedUntilResolved() {
        val record = LuminaMissedOpportunityEngine.recordRejectedOpportunity(
            symbol = "SOLUSDT",
            rejectionSubsystem = "LiquidityGuard",
            rejectionCategory = "SLIPPAGE",
            entry = 100.0,
            hypotheticalSL = 95.0,
            hypotheticalTP = 110.0,
            direction = "BUY",
            strategy = "Breakout",
            regime = "NORMAL",
            confidence = 65.0,
            expectedValue = 1.2
        )

        assertEquals(CounterfactualLifecycleStage.REJECTED, record.lifecycleStage)
        val initialMsg = record.getAttributionMessage()
        assertTrue("Prior to resolution, message must state potential loss exposure avoided", initialMsg.contains("Potential loss exposure avoided:"))
        assertFalse("Prior to resolution, message MUST NOT state realized loss avoided", initialMsg.contains("Counterfactual outcome: avoided loss of"))

        // Resolve counterfactual
        LuminaMissedOpportunityEngine.resolveCounterfactual(
            record.id,
            listOf(MarketCandle(1000L, 98.0, 99.0, 94.0, 95.0, 50.0))
        )

        val resolvedMsg = record.getAttributionMessage()
        assertTrue("After resolution, message states counterfactual outcome: avoided loss", resolvedMsg.contains("Counterfactual outcome: avoided loss of"))
    }

    @Test
    fun testRule11_12_13_UniqueDecisionContextCanonicalRejectionAndAuthority() {
        val prices = listOf(50.0, 52.0, 54.0, 56.0, 58.0, 60.0, 62.0, 64.0, 66.0, 68.0, 70.0, 72.0, 74.0, 76.0, 78.0)
        val proposal = TradeProposal(
            strategyName = "Trend Following",
            market = "BTCUSDT",
            signal = SignalType.BUY,
            confidenceScore = 75.0,
            entryPrice = 78.0,
            stopLoss = 74.0,
            takeProfit = 86.0,
            positionSize = 5.0,
            riskRewardRatio = 2.0,
            explanation = "Testing unique DecisionContext"
        )
        val context1 = StrategyEvaluationEngine.buildPipelineDecisionContext("BTCUSDT", "BUY", "Trend Following", proposal, prices)
        val context2 = StrategyEvaluationEngine.buildPipelineDecisionContext("BTCUSDT", "BUY", "Trend Following", proposal, prices)

        assertNotEquals("Each DecisionContext must have a unique ID", context1.id, context2.id)
        assertNotNull(context1.rejectionAuthority)
        assertNotNull(context1.capitalRejectionReason)
    }

    @Test
    fun testRule15_ScaleInsRespectPortfolioControls() {
        val waterfallScaleIn = LuminaRiskEngine.calculateRiskWaterfall(
            symbol = "BTCUSDT",
            actionIntent = TradeActionIntent.SCALE_IN
        )

        val posStage = waterfallScaleIn.stages.first { it.stageName == "POSITION_EXPOSURE_ADJUSTMENT" }
        assertEquals("Scale-in position multiplier must be scaled down to 0.50x", 0.50, posStage.multiplier, 0.01)
    }
}
