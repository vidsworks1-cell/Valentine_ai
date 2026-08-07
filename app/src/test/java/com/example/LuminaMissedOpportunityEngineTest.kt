package com.example

import com.example.aza.strategy.CounterfactualLifecycleStage
import com.example.aza.strategy.CounterfactualOutcome
import com.example.aza.strategy.LuminaMissedOpportunityEngine
import com.example.aza.strategy.MarketCandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LuminaMissedOpportunityEngineTest {

    @Before
    fun setUp() {
        LuminaMissedOpportunityEngine.clearLedger()
    }

    @Test
    fun testLifecycleTransitionsAndUnresolvedAttributionPhrase() {
        // 1. Record a rejected opportunity
        val record = LuminaMissedOpportunityEngine.recordRejectedOpportunity(
            symbol = "BTCUSDT",
            rejectionSubsystem = "MTFGuard",
            rejectionCategory = "MTF_ALIGNMENT_FAILURE",
            entry = 60000.0,
            hypotheticalSL = 58800.0, // 2% risk = $20 on $1000
            hypotheticalTP = 62400.0,
            direction = "BUY",
            strategy = "Momentum Breakout",
            regime = "TRENDING",
            confidence = 72.0,
            expectedValue = 1.8,
            monitoringHorizon = 10,
            positionNotionalUSD = 1000.0
        )

        assertNotNull(record)
        assertEquals(CounterfactualLifecycleStage.REJECTED, record.lifecycleStage)
        assertNull(record.realizedCounterfactualResult)

        // Verify message before resolution: must NOT claim realized loss avoided
        val initialMsg = record.getAttributionMessage()
        assertTrue("Must state potential loss exposure avoided prior to resolution", initialMsg.contains("Potential loss exposure avoided:"))
        assertTrue("Must explicitly state outcome is unknown pending resolution", initialMsg.contains("UNKNOWN pending counterfactual resolution"))

        // Transition to MONITORING
        val started = LuminaMissedOpportunityEngine.startMonitoring(record.id)
        assertTrue(started)
        assertEquals(CounterfactualLifecycleStage.MONITORING, record.lifecycleStage)
    }

    @Test
    fun testCounterfactualResolutionAvoidedLoss() {
        val record = LuminaMissedOpportunityEngine.recordRejectedOpportunity(
            symbol = "ETHUSDT",
            rejectionSubsystem = "LiquidityGuard",
            rejectionCategory = "SLIPPAGE_RISK",
            entry = 3000.0,
            hypotheticalSL = 2940.0, // SL at 2940
            hypotheticalTP = 3120.0,
            direction = "BUY",
            strategy = "Breakout",
            regime = "HIGH_VOLATILITY",
            confidence = 68.0,
            expectedValue = 1.5,
            monitoringHorizon = 5,
            positionNotionalUSD = 1000.0
        )

        // Market candles hit stop loss (low = 2930 <= 2940)
        val lookahead = listOf(
            MarketCandle(1000L, 2980.0, 3010.0, 2970.0, 2990.0, 100.0),
            MarketCandle(1060L, 2970.0, 2980.0, 2930.0, 2940.0, 150.0)
        )

        val resolved = LuminaMissedOpportunityEngine.resolveCounterfactual(record.id, lookahead)

        assertNotNull(resolved)
        assertEquals(CounterfactualLifecycleStage.COUNTERFACTUAL_RESOLVED, resolved!!.lifecycleStage)
        assertEquals(CounterfactualOutcome.AVOIDED_LOSS, resolved.realizedCounterfactualResult)

        // Verify message after resolution
        val resolvedMsg = resolved.getAttributionMessage()
        assertTrue("Must state counterfactual outcome: avoided loss", resolvedMsg.contains("Counterfactual outcome: avoided loss of \$20.00"))
    }

    @Test
    fun testCounterfactualResolutionMissedProfit() {
        val record = LuminaMissedOpportunityEngine.recordRejectedOpportunity(
            symbol = "SOLUSDT",
            rejectionSubsystem = "RiskGuard",
            rejectionCategory = "EXPOSURE_LIMIT",
            entry = 150.0,
            hypotheticalSL = 145.0,
            hypotheticalTP = 160.0,
            direction = "BUY",
            strategy = "Mean Reversion",
            regime = "RANGE_BOUND",
            confidence = 74.0,
            expectedValue = 2.0,
            monitoringHorizon = 5,
            positionNotionalUSD = 1500.0
        )

        // Market candles hit take profit (high = 162 >= 160)
        val lookahead = listOf(
            MarketCandle(1000L, 151.0, 155.0, 150.0, 154.0, 50.0),
            MarketCandle(1060L, 154.0, 162.0, 153.0, 160.0, 80.0)
        )

        val resolved = LuminaMissedOpportunityEngine.resolveCounterfactual(record.id, lookahead)

        assertNotNull(resolved)
        assertEquals(CounterfactualLifecycleStage.COUNTERFACTUAL_RESOLVED, resolved!!.lifecycleStage)
        assertEquals(CounterfactualOutcome.MISSED_PROFIT, resolved.realizedCounterfactualResult)

        val msg = resolved.getAttributionMessage()
        assertTrue("Must state counterfactual outcome: missed profit", msg.contains("Counterfactual outcome: missed profit of \$100.00"))
    }

    @Test
    fun testSubsystemAttributionAndOverConservatismWarning() {
        // Record 5 rejections for "ConfidenceFilter": 4 missed profits, 1 avoided loss
        repeat(4) { idx ->
            val rec = LuminaMissedOpportunityEngine.recordRejectedOpportunity(
                symbol = "BTCUSDT",
                rejectionSubsystem = "ConfidenceFilter",
                rejectionCategory = "LOW_CONFIDENCE",
                entry = 50000.0,
                hypotheticalSL = 49000.0,
                hypotheticalTP = 52000.0,
                direction = "BUY",
                strategy = "Trend Follow",
                regime = "TRENDING",
                confidence = 79.0,
                expectedValue = 2.0,
                positionNotionalUSD = 1000.0
            )
            // Hit TP
            LuminaMissedOpportunityEngine.resolveCounterfactual(
                rec.id,
                listOf(MarketCandle(1000L, 50500.0, 52500.0, 50000.0, 52000.0, 100.0))
            )
        }

        // 1 avoided loss
        val lossRec = LuminaMissedOpportunityEngine.recordRejectedOpportunity(
            symbol = "BTCUSDT",
            rejectionSubsystem = "ConfidenceFilter",
            rejectionCategory = "LOW_CONFIDENCE",
            entry = 50000.0,
            hypotheticalSL = 49000.0,
            hypotheticalTP = 52000.0,
            direction = "BUY",
            strategy = "Trend Follow",
            regime = "TRENDING",
            confidence = 79.0,
            expectedValue = 2.0,
            positionNotionalUSD = 1000.0
        )
        // Hit SL
        LuminaMissedOpportunityEngine.resolveCounterfactual(
            lossRec.id,
            listOf(MarketCandle(1000L, 49500.0, 49800.0, 48800.0, 49000.0, 100.0))
        )

        val metricsList = LuminaMissedOpportunityEngine.calculateSubsystemAttribution("ConfidenceFilter")
        assertEquals(1, metricsList.size)

        val metrics = metricsList.first()
        assertEquals("ConfidenceFilter", metrics.subsystem)
        assertEquals(5, metrics.totalRejections)
        assertEquals(5, metrics.resolvedRejections)
        assertEquals(1, metrics.avoidedLossesCount)
        assertEquals(4, metrics.missedProfitsCount)

        // False rejection rate = 4 / 5 = 80% (0.80)
        assertEquals(0.80, metrics.falseRejectionRate, 0.001)

        // Verify over-conservatism warning
        assertTrue("Must trigger over-conservatism warning when false rejection rate > 40%", metrics.optimizationWarning.contains("OVER-CONSERVATISM WARNING"))
    }

    @Test
    fun testExplicitLifecycleStageTransitions_Rejected_To_Monitoring_To_Resolved() {
        // Record opportunity -> REJECTED stage
        val record = LuminaMissedOpportunityEngine.recordRejectedOpportunity(
            symbol = "AVAXUSDT",
            rejectionSubsystem = "PortfolioGuard",
            rejectionCategory = "MAX_CONCURRENT_POSITIONS",
            entry = 30.0,
            hypotheticalSL = 28.5,
            hypotheticalTP = 33.0,
            direction = "BUY",
            strategy = "Breakout",
            regime = "BULLISH",
            confidence = 81.0,
            expectedValue = 1.6,
            monitoringHorizon = 10,
            positionNotionalUSD = 1000.0
        )

        assertEquals("Initial stage must be REJECTED", CounterfactualLifecycleStage.REJECTED, record.lifecycleStage)
        assertNull("Initial realized result must be null", record.realizedCounterfactualResult)

        // Step 1: Transition REJECTED -> MONITORING
        val transitionSuccess = LuminaMissedOpportunityEngine.startMonitoring(record.id)
        assertTrue("Transitioning REJECTED -> MONITORING must return true", transitionSuccess)
        assertEquals("Stage after startMonitoring must be MONITORING", CounterfactualLifecycleStage.MONITORING, record.lifecycleStage)

        // Step 2: Transition MONITORING -> COUNTERFACTUAL_RESOLVED
        val lookahead = listOf(
            MarketCandle(1000L, 30.0, 31.0, 29.8, 30.5, 50.0),
            MarketCandle(1060L, 30.5, 33.5, 30.2, 33.0, 80.0) // Hits TP at 33.0
        )
        val resolved = LuminaMissedOpportunityEngine.resolveCounterfactual(record.id, lookahead)

        assertNotNull(resolved)
        assertEquals("Stage after resolution must be COUNTERFACTUAL_RESOLVED", CounterfactualLifecycleStage.COUNTERFACTUAL_RESOLVED, resolved!!.lifecycleStage)
        assertEquals("Outcome must be MISSED_PROFIT", CounterfactualOutcome.MISSED_PROFIT, resolved.realizedCounterfactualResult)
    }

    @Test
    fun testCalculateSubsystemAttributionInconclusiveAndNeutralHandling() {
        // 1. Inconclusive (empty candles)
        val recordInconclusive = LuminaMissedOpportunityEngine.recordRejectedOpportunity(
            symbol = "DOTUSDT",
            rejectionSubsystem = "RiskGuard",
            rejectionCategory = "SLIPPAGE",
            entry = 7.0,
            hypotheticalSL = 6.5,
            hypotheticalTP = 8.0,
            strategy = "MeanReversion",
            regime = "NORMAL",
            confidence = 70.0,
            expectedValue = 1.2
        )
        LuminaMissedOpportunityEngine.resolveCounterfactual(recordInconclusive.id, emptyList())

        // 2. Neutral (flat candles, PnL < 0.1% notional)
        val recordNeutral = LuminaMissedOpportunityEngine.recordRejectedOpportunity(
            symbol = "DOTUSDT",
            rejectionSubsystem = "RiskGuard",
            rejectionCategory = "SLIPPAGE",
            entry = 100.0,
            hypotheticalSL = 90.0,
            hypotheticalTP = 110.0,
            strategy = "MeanReversion",
            regime = "NORMAL",
            confidence = 70.0,
            expectedValue = 1.2,
            positionNotionalUSD = 1000.0
        )
        // Exit price = close = 100.02 -> PnL = $0.20 (< $1.00 threshold for 0.1% of $1000)
        LuminaMissedOpportunityEngine.resolveCounterfactual(
            recordNeutral.id,
            listOf(MarketCandle(1000L, 100.0, 100.05, 99.98, 100.02, 10.0))
        )

        val metricsList = LuminaMissedOpportunityEngine.calculateSubsystemAttribution("RiskGuard")
        assertEquals(1, metricsList.size)
        val metrics = metricsList.first()

        assertEquals(2, metrics.totalRejections)
        assertEquals(2, metrics.resolvedRejections)
        assertEquals(1, metrics.inconclusiveCount)
        assertEquals(1, metrics.neutralCount)
        assertEquals(0, metrics.avoidedLossesCount)
        assertEquals(0, metrics.missedProfitsCount)
        assertEquals(0.0, metrics.totalAvoidedLossUSD, 0.001)
        assertEquals(0.0, metrics.totalMissedProfitUSD, 0.001)
        assertEquals(0.0, metrics.netCounterfactualValueUSD, 0.001)
        assertEquals(0.0, metrics.falseRejectionRate, 0.001)
        assertTrue(metrics.optimizationWarning.contains("counterfactual balance normal"))
    }
}
