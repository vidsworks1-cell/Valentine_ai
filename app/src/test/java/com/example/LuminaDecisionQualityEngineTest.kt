package com.example

import com.example.aza.strategy.CounterfactualOutcome
import com.example.aza.strategy.DecisionMatrixClassification
import com.example.aza.strategy.LuminaDecisionQualityEngine
import com.example.aza.strategy.ProcessOutcomeCategory
import com.example.aza.strategy.QualityDimensionScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LuminaDecisionQualityEngineTest {

    @Before
    fun setUp() {
        LuminaDecisionQualityEngine.clearLedger()
    }

    @Test
    fun testQualityDimensionsCalculation() {
        val dimensions = QualityDimensionScores(
            signalQuality = 80.0,
            strategyQuality = 90.0,
            mtfQuality = 85.0,
            liquidityQuality = 70.0,
            metaReasoningQuality = 88.0,
            riskSizingQuality = 75.0,
            portfolioDecisionQuality = 80.0,
            executionQuality = 90.0
        )

        val dqs = dimensions.calculateDecisionQualityScore()
        // Weighted: (80*0.15) + (90*0.15) + (85*0.15) + (70*0.10) + (88*0.15) + (75*0.10) + (80*0.15) + (90*0.05)
        // = 12 + 13.5 + 12.75 + 7 + 13.2) + 7.5 + 12 + 4.5 = 82.45
        assertEquals(82.45, dqs, 0.01)
    }

    @Test
    fun testProcessVsOutcomeDistinctionLosingTradeCanBeGoodDecision() {
        // High quality decision process (DQS = 85.0), but trade lost due to risk distribution ($ -50 PnL)
        val dimensions = QualityDimensionScores(
            signalQuality = 85.0,
            strategyQuality = 85.0,
            mtfQuality = 85.0,
            liquidityQuality = 85.0,
            metaReasoningQuality = 85.0,
            riskSizingQuality = 85.0,
            portfolioDecisionQuality = 85.0,
            executionQuality = 85.0
        )

        val record = LuminaDecisionQualityEngine.evaluateAndRecordDecision(
            decisionId = "DEC-GOOD-LOSS-101",
            symbol = "BTCUSDT",
            isApproved = true,
            primarySubsystem = "PortfolioExecutionEngine",
            qualityDimensions = dimensions,
            pnlUSD = -50.0
        )

        assertNotNull(record)
        assertEquals(DecisionMatrixClassification.APPROVED_LOST, record.matrixClassification)
        assertEquals(ProcessOutcomeCategory.GOOD_DECISION_LOSING_TRADE, record.processOutcomeCategory)
        assertTrue(record.dqsScore >= 65.0)
        assertTrue(record.rationale.contains("GOOD DECISION + LOSING TRADE"))
    }

    @Test
    fun testProcessVsOutcomeDistinctionWinningTradeCanBeBadDecision() {
        // Low quality decision process (DQS < 50), but trade won due to luck ($ +120 PnL)
        val poorDimensions = QualityDimensionScores(
            signalQuality = 30.0,
            strategyQuality = 40.0,
            mtfQuality = 20.0,
            liquidityQuality = 30.0,
            metaReasoningQuality = 25.0,
            riskSizingQuality = 40.0,
            portfolioDecisionQuality = 30.0,
            executionQuality = 50.0
        )

        val record = LuminaDecisionQualityEngine.evaluateAndRecordDecision(
            decisionId = "DEC-BAD-WIN-102",
            symbol = "ETHUSDT",
            isApproved = true,
            primarySubsystem = "PortfolioExecutionEngine",
            qualityDimensions = poorDimensions,
            pnlUSD = 120.0
        )

        assertNotNull(record)
        assertEquals(DecisionMatrixClassification.APPROVED_WON, record.matrixClassification)
        assertEquals(ProcessOutcomeCategory.BAD_DECISION_WINNING_TRADE, record.processOutcomeCategory)
        assertTrue(record.dqsScore < 65.0)
        assertTrue(record.rationale.contains("BAD DECISION + WINNING TRADE"))
    }

    @Test
    fun testMatrixClassificationsAndSubsystemStatistics() {
        val dims = QualityDimensionScores(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0)

        // 1. Approved Won
        LuminaDecisionQualityEngine.evaluateAndRecordDecision("D1", "BTC", true, "MTFGuard", dims, 100.0)
        // 2. Approved Lost
        LuminaDecisionQualityEngine.evaluateAndRecordDecision("D2", "BTC", true, "MTFGuard", dims, -50.0)
        // 3. Rejected Would Have Won (False Rejection)
        LuminaDecisionQualityEngine.evaluateAndRecordDecision("D3", "BTC", false, "MTFGuard", dims, 80.0, CounterfactualOutcome.MISSED_PROFIT)
        // 4. Rejected Would Have Lost (Useful Rejection)
        LuminaDecisionQualityEngine.evaluateAndRecordDecision("D4", "BTC", false, "MTFGuard", dims, -60.0, CounterfactualOutcome.AVOIDED_LOSS)

        val stats = LuminaDecisionQualityEngine.calculateSubsystemStatistics("MTFGuard").first()

        assertEquals("MTFGuard", stats.subsystemName)
        assertEquals(4, stats.totalDecisions)
        assertEquals(1, stats.approvedWonCount)
        assertEquals(1, stats.approvedLostCount)
        assertEquals(1, stats.rejectedWouldHaveWonCount)
        assertEquals(1, stats.rejectedWouldHaveLostCount)

        // Precision = Useful Rejections / Total Rejections = 1 / 2 = 0.50
        assertEquals(0.50, stats.precision, 0.001)
        // False Rejection Rate = 1 / 2 = 0.50
        assertEquals(0.50, stats.falseRejectionRate, 0.001)
        // False Acceptance Rate = 1 / 2 = 0.50
        assertEquals(0.50, stats.falseAcceptanceRate, 0.001)

        // Counterfactual Value = Avoided Loss ($60) - Missed Profit ($80) = -$20
        assertEquals(-20.0, stats.counterfactualValueUSD, 0.001)
    }

    @Test
    fun testMinimumEvidenceConstraintForAdaptation() {
        val dims = QualityDimensionScores(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0)

        // Record 15 decisions (below threshold of 30)
        repeat(15) { idx ->
            LuminaDecisionQualityEngine.evaluateAndRecordDecision("D-TINY-$idx", "SOLUSDT", false, "LiquidityGuard", dims, -30.0)
        }

        val canAdaptSmallSample = LuminaDecisionQualityEngine.canSubsystemAdapt("LiquidityGuard")
        assertFalse("Subsystem MUST NOT adapt with small sample size N=15 < 30", canAdaptSmallSample)

        val statsSmall = LuminaDecisionQualityEngine.calculateSubsystemStatistics("LiquidityGuard").first()
        assertFalse(statsSmall.isEvidenceSufficientForAdaptation)
        assertTrue(statsSmall.adaptationReadinessMessage.contains("ADAPTATION BLOCKED"))

        // Add 15 more decisions to reach N=30
        repeat(15) { idx ->
            LuminaDecisionQualityEngine.evaluateAndRecordDecision("D-LARGE-$idx", "SOLUSDT", false, "LiquidityGuard", dims, -30.0)
        }

        val canAdaptLargeSample = LuminaDecisionQualityEngine.canSubsystemAdapt("LiquidityGuard")
        assertTrue("Subsystem MAY adapt when sample size N=30 meets minimum evidence threshold", canAdaptLargeSample)

        val statsLarge = LuminaDecisionQualityEngine.calculateSubsystemStatistics("LiquidityGuard").first()
        assertTrue(statsLarge.isEvidenceSufficientForAdaptation)
        assertTrue(statsLarge.adaptationReadinessMessage.contains("ADAPTATION READY"))
    }

    @Test
    fun testAuditLogging() {
        val dims = QualityDimensionScores(90.0, 90.0, 90.0, 90.0, 90.0, 90.0, 90.0, 90.0)
        LuminaDecisionQualityEngine.evaluateAndRecordDecision("AUDIT-1", "BTCUSDT", true, "RiskGuard", dims, 200.0)

        val logs = LuminaDecisionQualityEngine.getAuditLogs()
        assertTrue("Audit logs must capture recorded decisions", logs.isNotEmpty())
        assertTrue(logs.first().contains("DECISION QUALITY RECORDED [AUDIT-1]"))
    }

    @Test
    fun testProcessOutcomeCategoryEdgeCasesAndThresholdBoundaries() {
        // High Quality dimensions yielding exact DQS = 65.0
        // Weights: signal(0.15), strategy(0.15), mtf(0.15), liquidity(0.10), meta(0.15), risk(0.10), portfolio(0.15), exec(0.05)
        // Set all to 65.0 -> DQS = 65.0
        val exact65Dims = QualityDimensionScores(65.0, 65.0, 65.0, 65.0, 65.0, 65.0, 65.0, 65.0)
        assertEquals(65.0, exact65Dims.calculateDecisionQualityScore(), 0.001)

        val record65Win = LuminaDecisionQualityEngine.evaluateAndRecordDecision("E1", "BTC", true, "RiskGuard", exact65Dims, 50.0)
        assertEquals("DQS = 65.0 with win must be GOOD_DECISION_WINNING_TRADE", ProcessOutcomeCategory.GOOD_DECISION_WINNING_TRADE, record65Win.processOutcomeCategory)

        val record65Loss = LuminaDecisionQualityEngine.evaluateAndRecordDecision("E2", "BTC", true, "RiskGuard", exact65Dims, -50.0)
        assertEquals("DQS = 65.0 with loss must be GOOD_DECISION_LOSING_TRADE", ProcessOutcomeCategory.GOOD_DECISION_LOSING_TRADE, record65Loss.processOutcomeCategory)

        // Dimensions yielding DQS = 64.9
        val justBelow65Dims = QualityDimensionScores(64.9, 64.9, 64.9, 64.9, 64.9, 64.9, 64.9, 64.9)
        val recordBelow65Win = LuminaDecisionQualityEngine.evaluateAndRecordDecision("E3", "BTC", true, "RiskGuard", justBelow65Dims, 50.0)
        assertEquals("DQS < 65.0 with win must be BAD_DECISION_WINNING_TRADE", ProcessOutcomeCategory.BAD_DECISION_WINNING_TRADE, recordBelow65Win.processOutcomeCategory)

        val recordBelow65Loss = LuminaDecisionQualityEngine.evaluateAndRecordDecision("E4", "BTC", true, "RiskGuard", justBelow65Dims, -50.0)
        assertEquals("DQS < 65.0 with loss must be BAD_DECISION_LOSING_TRADE", ProcessOutcomeCategory.BAD_DECISION_LOSING_TRADE, recordBelow65Loss.processOutcomeCategory)

        // Zero PnL (breakeven trade: pnlUSD > 0 is false)
        val recordZeroPnLGood = LuminaDecisionQualityEngine.evaluateAndRecordDecision("E5", "BTC", true, "RiskGuard", exact65Dims, 0.0)
        assertEquals("DQS = 65.0 with PnL = 0.0 must be GOOD_DECISION_LOSING_TRADE", ProcessOutcomeCategory.GOOD_DECISION_LOSING_TRADE, recordZeroPnLGood.processOutcomeCategory)

        val recordZeroPnLBad = LuminaDecisionQualityEngine.evaluateAndRecordDecision("E6", "BTC", true, "RiskGuard", justBelow65Dims, 0.0)
        assertEquals("DQS < 65.0 with PnL = 0.0 must be BAD_DECISION_LOSING_TRADE", ProcessOutcomeCategory.BAD_DECISION_LOSING_TRADE, recordZeroPnLBad.processOutcomeCategory)
    }
}
