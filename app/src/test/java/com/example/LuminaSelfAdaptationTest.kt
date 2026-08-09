package com.example

import com.example.aza.strategy.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LuminaSelfAdaptationTest {

    @Before
    fun setUp() {
        LuminaSelfAdaptationEngine.clearLedgers()
    }

    @Test
    fun testStrategyStateTransitionsAndSampleLimits() {
        // Sample size < 15 must yield PRELIMINARY_OBSERVATION
        val stateObs = LuminaSelfAdaptationEngine.evaluateStrategyState(
            strategyName = "TestBreakout",
            totalTrades = 10,
            winRatePct = 60.0,
            expectancyR = 0.50
        )
        assertEquals(StrategyState.PRELIMINARY_OBSERVATION, stateObs)

        // Sample size >= 15 with solid performance yields ACTIVE
        val stateActive = LuminaSelfAdaptationEngine.evaluateStrategyState(
            strategyName = "TestBreakout",
            totalTrades = 16,
            winRatePct = 52.0,
            expectancyR = 0.30
        )
        assertEquals(StrategyState.ACTIVE, stateActive)

        // Severe drawdown (> 15%) triggers SUSPENDED immediately
        val stateSuspended = LuminaSelfAdaptationEngine.evaluateStrategyState(
            strategyName = "TestBreakout",
            totalTrades = 20,
            winRatePct = 30.0,
            expectancyR = -0.10,
            maxDrawdownPct = 16.5
        )
        assertEquals(StrategyState.SUSPENDED, stateSuspended)
    }

    @Test
    fun testExplicitCalibrationFieldsInDecisionContext() {
        val prices = listOf(67000.0, 67100.0, 67050.0, 66950.0, 67200.0, 67180.0, 67300.0, 67250.0, 67400.0, 67350.0, 67500.0)
        val proposal = TradeProposal(
            strategyName = "Mean Reversion",
            market = "BTCUSDT",
            signal = SignalType.BUY,
            confidenceScore = 0.82,
            entryPrice = 67350.0,
            stopLoss = 66800.0,
            takeProfit = 68400.0,
            positionSize = 0.05,
            riskRewardRatio = 1.9,
            explanation = "Range entry"
        )

        val context = StrategyEvaluationEngine.buildPipelineDecisionContext(
            symbol = "BTCUSDT",
            side = "BUY",
            requestedStrategy = "Mean Reversion",
            proposal = proposal,
            prices = prices,
            mtfWeightedScore = 80.0,
            mtfThreshold = 66.0,
            metaConviction = 88.0,
            sampleSize = 25,
            recentWinRatePct = 56.0,
            recentExpectancyR = 0.40
        )

        assertEquals(0.82, context.rawModelConfidence, 0.01)
        assertTrue("Calibrated confidence should be computed", context.calibratedConfidence > 0.0)
        assertEquals(88.0, context.metaConviction, 0.01)
        assertTrue("Strategy quality should be positive", context.strategyQuality > 0.0)
        assertTrue("Capital eligibility score should be bounded [0..1]", context.capitalEligibilityScore in 0.0..1.0)
        assertNotNull(context.uncertaintyLevel)
        assertNotNull(context.strategyStateEnum)
    }

    @Test
    fun testUncertaintyHighLimitsCalibrationAdjustments() {
        // Small sample N = 10 -> HIGH uncertainty
        val breakdown = LuminaSelfAdaptationEngine.calibrateStrategyAndCapital(
            rawConfidence = 0.80,
            metaConviction = 85.0,
            longTermWinRatePct = 37.5,
            recentWinRatePct = 33.0,
            longTermExpectancyR = 0.02,
            recentExpectancyR = 0.01,
            totalTrades = 10,
            maxDrawdownPct = 4.0,
            strategyName = "Volatility Expansion Breakout"
        )

        assertEquals(UncertaintyLevel.HIGH, breakdown.uncertaintyLevel)
        assertEquals(StrategyState.PRELIMINARY_OBSERVATION, breakdown.strategyState)
        // Delta between raw (0.80) and calibrated confidence must be bounded to max +/- 0.05 under HIGH uncertainty
        val delta = kotlin.math.abs(breakdown.calibratedConfidence - 0.80)
        assertTrue("High uncertainty must cap calibration adjustments to <= 0.05", delta <= 0.051)
        assertTrue("Capital eligibility score must be scaled conservatively", breakdown.capitalEligibilityScore <= 0.65)
    }

    @Test
    fun testControlledRecencyWeighting() {
        // Long-term WR 60.0%, but recent 5 trades produced WR 20.0%
        val (blendedWR, blendedExp) = LuminaSelfAdaptationEngine.calculateBlendedPerformance(
            longTermWinRatePct = 60.0,
            recentWinRatePct = 20.0,
            longTermExpectancyR = 0.50,
            recentExpectancyR = -0.10,
            totalTrades = 40
        )

        // Blended WR should be 0.60 * 60.0 + 0.40 * 20.0 = 44.0%
        assertEquals(44.0, blendedWR, 0.1)
        // Blended Exp should be 0.60 * 0.50 + 0.40 * -0.10 = 0.26R
        assertEquals(0.26, blendedExp, 0.01)
    }

    @Test
    fun testRegimeMemorySampleGating() {
        // 5 trades in HIGH_VOLATILITY regime (N < 10)
        repeat(5) {
            LuminaSelfAdaptationEngine.recordRegimeOutcome("Breakout", "HIGH_VOLATILITY", "HIGH", true, 1.2)
        }
        val smallSampleAdj = LuminaSelfAdaptationEngine.getRegimeAdjustment("Breakout", "HIGH_VOLATILITY", "HIGH")
        assertTrue("Regime adjustment for N < 10 must be scaled down", kotlin.math.abs(smallSampleAdj) <= 0.02)

        // Add 5 more trades to reach N = 10
        repeat(5) {
            LuminaSelfAdaptationEngine.recordRegimeOutcome("Breakout", "HIGH_VOLATILITY", "HIGH", true, 1.2)
        }
        val fullSampleAdj = LuminaSelfAdaptationEngine.getRegimeAdjustment("Breakout", "HIGH_VOLATILITY", "HIGH")
        assertTrue("Regime adjustment for N >= 10 can apply full weight", fullSampleAdj > smallSampleAdj)
    }

    @Test
    fun testCounterfactualAggregatedLearning() {
        // Single counterfactual entry
        LuminaSelfAdaptationEngine.recordCounterfactual("BTCUSDT", "Breakout", "BUY", "MTF_GUARD", true, 1.5)
        val singleRec = LuminaSelfAdaptationEngine.evaluateAggregatedCounterfactuals("Breakout")
        assertEquals("Single counterfactual must not trigger threshold delta", 0.0, singleRec.recommendedThresholdAdjustment, 0.001)

        // Add 10 losing counterfactuals
        repeat(10) {
            LuminaSelfAdaptationEngine.recordCounterfactual("BTCUSDT", "Breakout", "BUY", "MTF_GUARD", false, -1.0)
        }
        val aggRec = LuminaSelfAdaptationEngine.evaluateAggregatedCounterfactuals("Breakout")
        assertTrue("Aggregated losing counterfactuals (N >= 10) must tighten threshold (+0.03)", aggRec.recommendedThresholdAdjustment > 0.0)
    }

    @Test
    fun testAntiOscillationHysteresisAndDwellPeriod() {
        // Set initial ACTIVE state at trade 20
        LuminaSelfAdaptationEngine.evaluateStrategyState("OscillatingStrat", 20, 50.0, 0.25)

        // Trade 21 has slight WR dip (41.5%) - dwell period (needs 5 trades) prevents rapid toggle
        val stateDwell = LuminaSelfAdaptationEngine.evaluateStrategyState("OscillatingStrat", 21, 41.5, 0.07)
        assertEquals("Must remain ACTIVE during minimum dwell count", StrategyState.ACTIVE, stateDwell)

        // Trade 26 (dwell satisfied) switches to RESTRICTED due to WR < 42%
        val stateRestricted = LuminaSelfAdaptationEngine.evaluateStrategyState("OscillatingStrat", 26, 41.0, 0.05)
        assertEquals("Must transition to RESTRICTED after dwell period", StrategyState.RESTRICTED, stateRestricted)
    }

    @Test
    fun testHardRiskConstraintsMustOverrideSelfAdaptation() {
        val prices = listOf(67000.0, 67100.0, 67050.0, 66950.0, 67200.0)
        val proposal = TradeProposal(
            strategyName = "Liquidity Sweep",
            market = "BTCUSDT",
            signal = SignalType.BUY,
            confidenceScore = 0.88,
            entryPrice = 67200.0,
            stopLoss = 66800.0,
            takeProfit = 68000.0,
            positionSize = 0.05,
            riskRewardRatio = 2.0,
            explanation = "High conviction sweep setup"
        )

        // MTF Score is severely failing (35.0%), but strategy adaptation score and meta conviction are high (92.0)
        val context = StrategyEvaluationEngine.buildPipelineDecisionContext(
            symbol = "BTCUSDT",
            side = "BUY",
            requestedStrategy = "Liquidity Sweep",
            proposal = proposal,
            prices = prices,
            mtfWeightedScore = 35.0,
            mtfThreshold = 66.0,
            metaConviction = 92.0,
            sampleSize = 40,
            recentWinRatePct = 65.0,
            recentExpectancyR = 0.55
        )

        assertFalse("Self-adaptation must NOT bypass hard MTF Guard rejection", context.isExecutionApproved)
        assertEquals("Decision state must be SIGNAL_REJECTED", OverallDecisionState.SIGNAL_REJECTED, context.decisionState)
        assertEquals("Rejection authority must be MTF_GUARD", DecisionAuthority.MTF_GUARD, context.rejectionAuthority)
    }

    @Test
    fun testConfidenceCalibrationTableAndInverseDiscriminationCorrection() {
        val stratName = "Mean Reversion"

        // Simulate 10 trades recorded in the high-confidence bucket (0.85-0.90) with mostly losses (2 wins, 8 losses = 20% win rate)
        repeat(2) {
            LuminaSelfAdaptationEngine.recordTradeOutcomeForCalibration(stratName, 0.88, true, 1.2)
        }
        repeat(8) {
            LuminaSelfAdaptationEngine.recordTradeOutcomeForCalibration(stratName, 0.88, false, -1.0)
        }

        val table = LuminaSelfAdaptationEngine.getConfidenceCalibrationTable(stratName)
        val bucket85 = table.find { it.rangeLabel == "0.85-0.90" }

        assertNotNull(bucket85)
        assertEquals(10, bucket85!!.totalTrades)
        assertEquals(2, bucket85.winningTrades)
        assertEquals(20.0, bucket85.winRatePct, 0.01)

        // Calibrate strategy with raw confidence 0.88. Empirical WR = 20%.
        // Self-adaptation must clamp down calibrated confidence to prevent overconfidence in losing setups
        val breakdown = LuminaSelfAdaptationEngine.calibrateStrategyAndCapital(
            rawConfidence = 0.88,
            metaConviction = 80.0,
            longTermWinRatePct = 50.0,
            recentWinRatePct = 50.0,
            longTermExpectancyR = 0.20,
            recentExpectancyR = 0.20,
            totalTrades = 30,
            strategyName = stratName
        )

        assertTrue(
            "Calibrated confidence (${breakdown.calibratedConfidence}) must be penalized heavily below raw (0.88) due to empirical bucket win rate (20%)",
            breakdown.calibratedConfidence < 0.35
        )
    }

    @Test
    fun testSymbolDependentStrategyTrustAllocation() {
        val strat = "Breakout Momentum"

        // ETHUSDT: Record 10 winning trades (100% WR)
        repeat(10) {
            LuminaSelfAdaptationEngine.recordTradeOutcomeForCalibration(strat, 0.80, true, 2.0, symbol = "ETHUSDT")
        }

        // BTCUSDT: Record 10 losing trades (0% WR)
        repeat(10) {
            LuminaSelfAdaptationEngine.recordTradeOutcomeForCalibration(strat, 0.80, false, -1.0, symbol = "BTCUSDT")
        }

        val ethBreakdown = LuminaSelfAdaptationEngine.calibrateStrategyAndCapital(
            rawConfidence = 0.80,
            metaConviction = 80.0,
            longTermWinRatePct = 52.0,
            recentWinRatePct = 55.0,
            longTermExpectancyR = 0.40,
            recentExpectancyR = 0.45,
            totalTrades = 30,
            strategyName = strat,
            symbol = "ETHUSDT"
        )

        val btcBreakdown = LuminaSelfAdaptationEngine.calibrateStrategyAndCapital(
            rawConfidence = 0.80,
            metaConviction = 80.0,
            longTermWinRatePct = 30.0,
            recentWinRatePct = 25.0,
            longTermExpectancyR = -0.20,
            recentExpectancyR = -0.30,
            totalTrades = 30,
            strategyName = strat,
            symbol = "BTCUSDT"
        )

        assertTrue("ETHUSDT should have significantly higher capital eligibility score than BTCUSDT", ethBreakdown.capitalEligibilityScore > btcBreakdown.capitalEligibilityScore)
        assertTrue("ETHUSDT calibrated confidence should be higher than BTCUSDT", ethBreakdown.calibratedConfidence > btcBreakdown.calibratedConfidence)
    }
}
