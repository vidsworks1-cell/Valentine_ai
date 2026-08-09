package com.example

import com.example.aza.strategy.AdaptiveExitIntelligenceEngine
import com.example.aza.strategy.ExitAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdaptiveExitIntelligenceEngineTest {

    @Test
    fun testEvaluateExitActionWithMacroEventAndSentiment() {
        val prices = List(30) { i -> 100.0 + i * 0.5 }
        
        // High economic event risk (e.g. CPI release imminent) + position in profit
        val preNewsResult = AdaptiveExitIntelligenceEngine.evaluateExitAction(
            market = "BTC",
            entryPrice = 100.0,
            currentPrice = 108.0,
            initialStopLoss = 98.0,
            initialTakeProfit = 115.0,
            currentStopLoss = 102.0,
            closingPrices = prices,
            economicEventRisk = 0.85,
            newsSentimentScore = 0.20
        )

        assertNotNull("Result should not be null", preNewsResult)
        assertEquals("Pre-news scale out expected", ExitAction.SCALE_OUT, preNewsResult.action)
        assertTrue("Scale out percentage should be 50%", preNewsResult.scaleOutPercentage == 0.50)
        assertEquals(0.85, preNewsResult.metrics.economicEventRisk, 0.01)
        assertEquals(0.20, preNewsResult.metrics.newsSentimentScore, 0.01)
    }

    @Test
    fun testAssetClassProfileAdaptation() {
        val prices = List(30) { i -> 100.0 + i * 0.4 }

        // Forex pair auto-inference (EURUSD)
        val forexResult = AdaptiveExitIntelligenceEngine.evaluateExitAction(
            market = "EURUSD",
            entryPrice = 100.0,
            currentPrice = 103.0,
            initialStopLoss = 98.0,
            initialTakeProfit = 110.0,
            currentStopLoss = 99.0,
            closingPrices = prices,
            economicEventRisk = 0.40,
            newsSentimentScore = 0.10
        )

        assertNotNull(forexResult)
        assertEquals(com.example.aza.strategy.AssetClassProfile.FOREX, forexResult.metrics.assetClassProfile)
        assertTrue(forexResult.rationale.contains("Forex"))

        // Equities auto-inference (NVDA)
        val equitiesResult = AdaptiveExitIntelligenceEngine.evaluateExitAction(
            market = "NVDA",
            entryPrice = 100.0,
            currentPrice = 103.0,
            initialStopLoss = 98.0,
            initialTakeProfit = 110.0,
            currentStopLoss = 99.0,
            closingPrices = prices,
            economicEventRisk = 0.20,
            newsSentimentScore = 0.75
        )

        assertNotNull(equitiesResult)
        assertEquals(com.example.aza.strategy.AssetClassProfile.EQUITIES, equitiesResult.metrics.assetClassProfile)
        assertTrue(equitiesResult.rationale.contains("Equities"))
    }

    @Test
    fun testOrderBookAndEventDrivenTrendContinuationWeightedAverage() {
        val prices = List(30) { i -> 100.0 + i * 0.5 }

        // Order Book: Strong bid volume (70%), positive order flow imbalance (+0.60) -> orderBookScore ~ 0.725
        val obMetrics = com.example.aza.strategy.OrderBookMetrics(
            bidVolume = 700.0,
            askVolume = 300.0,
            orderFlowImbalance = 0.60
        )

        // Event Driven: Low economic risk (0.10), positive news (+0.50) -> eventDrivenScore ~ 0.72
        val evMetrics = com.example.aza.strategy.EventDrivenMetrics(
            economicEventRisk = 0.10,
            newsSentimentScore = 0.50,
            hasUpcomingEarningsOrEvent = false,
            eventImpactScore = 0.0
        )

        val result = AdaptiveExitIntelligenceEngine.evaluateExitAction(
            market = "BTCUSDT",
            entryPrice = 100.0,
            currentPrice = 108.0,
            initialStopLoss = 98.0,
            initialTakeProfit = 120.0,
            currentStopLoss = 104.0,
            closingPrices = prices,
            orderBookMetrics = obMetrics,
            eventDrivenMetrics = evMetrics
        )

        assertNotNull(result)
        assertEquals(0.70, result.metrics.orderBookMetrics.bidAskVolumeRatio, 0.01)
        assertTrue("Trend continuation prob should reflect 60% OB + 20% Event + 20% Tech", result.metrics.trendContinuationProb > 0.65)
    }

    @Test
    fun testMomentumExhaustionWithSentimentAndOrderFlowImbalance() {
        val prices = List(30) { i -> 100.0 + i * 0.5 } // Strong uptrend -> high RSI

        // Order Book: Heavy sell order flow imbalance (-0.80)
        val obMetrics = com.example.aza.strategy.OrderBookMetrics(
            bidVolume = 200.0,
            askVolume = 800.0,
            orderFlowImbalance = -0.80
        )

        // Event Driven: Severe negative news (-0.70)
        val evMetrics = com.example.aza.strategy.EventDrivenMetrics(
            economicEventRisk = 0.30,
            newsSentimentScore = -0.70
        )

        val result = AdaptiveExitIntelligenceEngine.evaluateExitAction(
            market = "ETHUSDT",
            entryPrice = 100.0,
            currentPrice = 114.0,
            initialStopLoss = 98.0,
            initialTakeProfit = 120.0,
            currentStopLoss = 108.0,
            closingPrices = prices,
            orderBookMetrics = obMetrics,
            eventDrivenMetrics = evMetrics
        )

        assertNotNull(result)
        assertTrue("Momentum exhaustion should be elevated due to adverse sentiment and sell imbalance", result.metrics.momentumExhaustion > 0.85)
    }
}
