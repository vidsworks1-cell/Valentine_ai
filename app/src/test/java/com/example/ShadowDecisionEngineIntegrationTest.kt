package com.example

import com.example.aza.strategy.CounterfactualLifecycleStage
import com.example.aza.strategy.CounterfactualOutcome
import com.example.aza.strategy.LuminaDecisionQualityEngine
import com.example.aza.strategy.LuminaMissedOpportunityEngine
import com.example.aza.strategy.MarketCandle
import com.example.aza.strategy.ShadowDecisionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShadowDecisionEngineIntegrationTest {

    @Before
    fun setUp() {
        LuminaMissedOpportunityEngine.clearLedger()
        LuminaDecisionQualityEngine.clearLedger()
    }

    @Test
    fun testShadowDecisionEngineIntegrationWithMissedOpportunityEngine() {
        val lookaheadCandles = listOf(
            MarketCandle(1000L, 100.0, 102.0, 99.5, 101.0, 1000.0),
            MarketCandle(1060L, 101.0, 105.0, 100.5, 104.5, 1200.0) // Hits TP at 104.0 (rejection 100.0 + 2.0*2.0 = 104.0)
        )

        val rejectedRecord = ShadowDecisionEngine.recordRejectedTradeAndSimulate(
            symbol = "BTCUSDT",
            strategyName = "Momentum Breakout",
            confidenceScorePct = 76.0,
            marketRegime = "HIGH_VOLATILITY",
            reasonRejected = "MTFGuard: Discrepancy between 1H and 15M trend",
            atr = 2.0,
            marketState = "VOLATILE_EXPANSION",
            rejectionPrice = 100.0,
            lookaheadCandles = lookaheadCandles
        )

        assertNotNull(rejectedRecord)
        assertEquals("BTCUSDT", rejectedRecord.symbol)
        assertEquals("Momentum Breakout", rejectedRecord.strategyName)
        assertTrue("Trade should have won with lookahead candles reaching TP", rejectedRecord.wouldHaveWon)

        // Verify that LuminaMissedOpportunityEngine recorded and resolved the counterfactual
        val subsystemMetrics = LuminaMissedOpportunityEngine.calculateSubsystemAttribution("MTFGuard")
        assertEquals(1, subsystemMetrics.size)
        val metric = subsystemMetrics.first()
        assertEquals("MTFGuard", metric.subsystem)
        assertEquals(1, metric.totalRejections)
        assertEquals(1, metric.resolvedRejections)
        assertEquals(1, metric.missedProfitsCount)
    }

    @Test
    fun testShadowDecisionEngineDailyAndWeeklyReviews() {
        val lookahead = listOf(
            MarketCandle(1000L, 100.0, 105.0, 99.0, 104.0, 1000.0)
        )

        // Record multiple rejected trades to trigger discrepancy thresholds
        repeat(6) { idx ->
            ShadowDecisionEngine.recordRejectedTradeAndSimulate(
                symbol = "ETHUSDT",
                strategyName = "Trend Follower",
                confidenceScorePct = 81.0,
                marketRegime = "BULLISH_TREND",
                reasonRejected = "Confidence (81%) below 85% live threshold",
                atr = 3.0,
                marketState = "TRENDING",
                rejectionPrice = 100.0,
                lookaheadCandles = lookahead
            )
        }

        val dailySummary = ShadowDecisionEngine.runDailyReview()
        assertNotNull(dailySummary)
        assertTrue(dailySummary.totalRejectedTradesRecorded >= 6)
        assertTrue(dailySummary.dailyReviewStatus.contains("ACTIVE"))

        val weeklySummary = ShadowDecisionEngine.runWeeklyReview()
        assertNotNull(weeklySummary)
        assertTrue(weeklySummary.weeklyReviewStatus.contains("ACTIVE"))

        val summary = ShadowDecisionEngine.getShadowEngineSummary()
        assertTrue("Discrepancy alert must be active when counterfactual win rate is high", summary.discrepancyAlertActive)
        assertTrue(summary.recentAuditLogs.isNotEmpty())
    }

    @Test
    fun testShadowDecisionEngineSimulationProtocol() {
        val summary = ShadowDecisionEngine.runShadowDecisionSimulationProtocol()

        assertNotNull(summary)
        assertTrue("Protocol must record rejected trades", summary.totalRejectedTradesRecorded > 0)
        assertTrue("Protocol must populate recent missed opportunities", summary.recentMissedOpportunities.isNotEmpty())
        assertEquals("CONCURRENT_SHADOW_MONITORING", summary.systemMode)
    }
}
