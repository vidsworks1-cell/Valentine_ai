package com.example

import com.example.aza.strategy.MarketCandle
import com.example.aza.strategy.ShadowDecisionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShadowDecisionEngineTest {

    @Test
    fun testRecordRejectedTradeAndCounterfactualSimulation() {
        val lookaheadCandles = List(15) { i ->
            MarketCandle(
                timestamp = 1000L + i * 60,
                open = 100.0 + i * 1.2,
                high = 100.5 + i * 1.5,
                low = 99.8 + i * 1.0,
                close = 101.0 + i * 1.3,
                volume = 1500.0
            )
        }

        val record = ShadowDecisionEngine.recordRejectedTradeAndSimulate(
            symbol = "BTC",
            strategyName = "Momentum Squeeze",
            confidenceScorePct = 78.0,
            marketRegime = "BULLISH_TREND",
            reasonRejected = "Confidence 78.0% below 85.0% live threshold",
            atr = 2.0,
            marketState = "High Volatility Squeeze",
            rejectionPrice = 100.0,
            lookaheadCandles = lookaheadCandles
        )

        assertNotNull("Rejected trade record should be non-null", record)
        assertEquals("BTC", record.symbol)
        assertEquals("Momentum Squeeze", record.strategyName)
        assertEquals(78.0, record.confidenceScorePct, 0.01)
        assertEquals(2.0, record.atr, 0.01)
        assertTrue("MFE should be recorded and positive", record.mfe > 0.0)
        assertTrue("Trade should be marked as winning counterfactual", record.wouldHaveWon)
    }

    @Test
    fun testShadowEngineSimulationProtocolAndSummary() {
        val summary = ShadowDecisionEngine.runShadowDecisionSimulationProtocol()

        assertNotNull("Summary should not be null", summary)
        assertTrue("Recorded rejected trades count should be positive", summary.totalRejectedTradesRecorded > 0)
        assertTrue("Counterfactual win rate should be calculated", summary.counterfactualWinRatePct in 0.0..100.0)
        assertTrue("Audit log should contain entries", summary.recentAuditLogs.isNotEmpty())
    }

    @Test
    fun testPeriodicReviews() {
        ShadowDecisionEngine.runShadowDecisionSimulationProtocol()
        val dailySummary = ShadowDecisionEngine.runDailyReview()
        assertNotNull("Daily review summary should not be null", dailySummary)
        assertTrue("Daily review status should be active", dailySummary.dailyReviewStatus.contains("ACTIVE"))

        val weeklySummary = ShadowDecisionEngine.runWeeklyReview()
        assertNotNull("Weekly review summary should not be null", weeklySummary)
        assertTrue("Weekly review status should be active", weeklySummary.weeklyReviewStatus.contains("ACTIVE"))
    }
}
