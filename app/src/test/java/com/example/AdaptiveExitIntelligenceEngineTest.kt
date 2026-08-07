package com.example

import com.example.aza.strategy.AdaptiveExitIntelligenceEngine
import com.example.aza.strategy.ExitAction
import com.example.aza.strategy.MarketCandle
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
}
