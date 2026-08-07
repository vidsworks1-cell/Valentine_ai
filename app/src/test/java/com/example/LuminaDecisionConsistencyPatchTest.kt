package com.example

import com.example.aza.strategy.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LuminaDecisionConsistencyPatchTest {

    @Test
    fun testMathematicalGuardThresholdLogic() {
        val passResult = GuardEvaluator.checkMinThreshold("VolumeLiquidityGuard", 75.1, 70.0)
        assertTrue("75.1% >= 70.0% must produce PASS", passResult.isPassed)
        assertTrue("Rationale string must state valid confluence", passResult.rationale.contains("75.1% >= 70.0%"))
        assertFalse("Rationale must not contain insufficient confluence string", passResult.rationale.contains("Insufficient"))

        val failResult = GuardEvaluator.checkMinThreshold("VolumeLiquidityGuard", 65.0, 70.0)
        assertFalse("65.0% < 70.0% must produce FAIL", failResult.isPassed)
        assertTrue("Rationale string must state insufficient confluence", failResult.rationale.contains("65.0% < 70.0%"))
    }

    @Test
    fun testMetaReasoningCannotOverrideHardGuardFailure() {
        val prices = listOf(67000.0, 67100.0, 67050.0, 66950.0, 67200.0, 67180.0, 67300.0, 67250.0, 67400.0, 67350.0, 67500.0, 67450.0, 67600.0, 67550.0, 67700.0)
        val proposal = TradeProposal(
            strategyName = "Mean Reversion",
            market = "BTCUSDT",
            signal = SignalType.BUY,
            confidenceScore = 0.85,
            entryPrice = 67700.0,
            stopLoss = 67300.0,
            takeProfit = 68700.0,
            positionSize = 0.05,
            riskRewardRatio = 2.5,
            explanation = "Entry in range"
        )

        // MTF Score is 55.0% (< threshold 66.0%), but Meta Reasoning conviction is 95.0%
        val context = StrategyEvaluationEngine.buildPipelineDecisionContext(
            symbol = "BTCUSDT",
            side = "BUY",
            requestedStrategy = "Mean Reversion",
            proposal = proposal,
            prices = prices,
            mtfWeightedScore = 55.0,
            mtfThreshold = 66.0,
            metaConviction = 95.0
        )

        assertFalse("Execution must NOT be approved when MTF hard guard fails", context.isExecutionApproved)
        assertEquals("Decision state must be SIGNAL_REJECTED", OverallDecisionState.SIGNAL_REJECTED, context.decisionState)
        assertEquals("Rejection authority must be MTF_GUARD", DecisionAuthority.MTF_GUARD, context.rejectionAuthority)
        assertTrue("Rejection rationale must identify MTF guard failure", context.rejectionReason.contains("MTF"))
    }

    @Test
    fun testStrategySubstitutionPipelineReEvaluation() {
        val prices = listOf(67000.0, 67100.0, 67050.0, 66950.0, 67200.0, 67180.0, 67300.0, 67250.0, 67400.0, 67350.0, 67500.0, 67450.0, 67600.0, 67550.0, 67700.0)
        val proposal = TradeProposal(
            strategyName = "Volatility Expansion Breakout",
            market = "ETHUSDT",
            signal = SignalType.SELL,
            confidenceScore = 0.72,
            entryPrice = 3500.0,
            stopLoss = 3550.0,
            takeProfit = 3400.0,
            positionSize = 0.05,
            riskRewardRatio = 2.0,
            explanation = "Breakout setup"
        )

        // Force underperforming parameters to trigger substitution
        val context = StrategyEvaluationEngine.buildPipelineDecisionContext(
            symbol = "ETHUSDT",
            side = "SELL",
            requestedStrategy = "Volatility Expansion Breakout",
            proposal = proposal,
            prices = prices,
            sampleSize = 25,
            recentWinRatePct = 35.0,
            recentExpectancyR = 0.05
        )

        assertTrue("Substitution flag must be true when strategy is underperforming", context.wasSubstituted)
        assertEquals("Effective strategy must be substituted to Liquidity Sweep", "Liquidity Sweep", context.effectiveStrategy)
        assertEquals("Original strategy must remain Volatility Expansion Breakout", "Volatility Expansion Breakout", context.originalStrategy)
        assertTrue("Substitution rationale must detail performance reasons", context.substitutionReason.contains("underperforming"))
    }

    @Test
    fun testSingleDecisionContextImmutabilityAndAuthorityTracking() {
        val auditReport = EngineValidationSuite.runDecisionConsistencyAuditSuiteValidation()
        assertEquals("PASS (Correct)", auditReport["Guard 75.1% >= 70.0% Result"])
        assertEquals("NO (SAFE - Meta Reasoning blocked from overriding hard guard)", auditReport["Meta Reasoning Overridden?"])
        assertEquals("SIGNAL_REJECTED", auditReport["MTF Hard Fail State"])
        assertEquals("MTF_GUARD", auditReport["MTF Hard Fail Authority"])
    }
}
