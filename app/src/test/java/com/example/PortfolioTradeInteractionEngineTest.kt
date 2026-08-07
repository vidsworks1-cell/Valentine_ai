package com.example

import com.example.aza.strategy.PortfolioTradeInteractionEngine
import com.example.aza.strategy.TradeStabilityImpact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PortfolioTradeInteractionEngineTest {

    @Test
    fun testCorrelationMatrixCalculation() {
        val matrix = PortfolioTradeInteractionEngine.getCorrelationMatrix()
        assertNotNull("Correlation matrix should not be null", matrix)
        assertEquals("Self-correlation should be 1.0", 1.0, matrix.getCorrelation("BTC", "BTC"), 0.001)
        assertTrue("BTC vs ETH correlation should be high positive", matrix.getCorrelation("BTC", "ETH") > 0.70)
        assertTrue("BTC vs EURUSD correlation should be negative", matrix.getCorrelation("BTC", "EURUSD") < 0.0)
    }

    @Test
    fun testTradeImpactsAndEnhancerDetractorClassification() {
        val impacts = PortfolioTradeInteractionEngine.calculateTradeImpacts()
        assertTrue("Should evaluate trade impacts across positions", impacts.isNotEmpty())

        val enhancers = impacts.filter { it.impactType == TradeStabilityImpact.STABILITY_ENHANCER }
        val detractors = impacts.filter { it.impactType == TradeStabilityImpact.STABILITY_DETRACTOR }

        assertTrue("Should identify stability enhancers", enhancers.isNotEmpty())
        assertTrue("Should identify stability detractors", detractors.isNotEmpty())
        assertTrue("Detractors should have negative impact scores", detractors.all { it.impactScore < 0.0 })
    }

    @Test
    fun testScenarioStressTesting() {
        val scenarios = PortfolioTradeInteractionEngine.runScenarioAnalysis("FLASH_CRASH")
        assertEquals("Should include 4 distinct market scenarios", 4, scenarios.size)

        val flashCrash = scenarios.first { it.scenarioId == "FLASH_CRASH" }
        assertTrue("Flash crash should project negative return", flashCrash.projectedPortfolioReturnPct < 0.0)
        assertNotNull("Recommended hedging action should be present", flashCrash.recommendedHedgingAction)
    }

    @Test
    fun testProposedTradeInteractionSizingAdjustment() {
        // Test high-correlation trade warning
        val (multCorr, msgCorr) = PortfolioTradeInteractionEngine.evaluateProposedTradeInteraction("ETH", "MeanReversion", 1000.0)
        assertTrue("High correlation trade should be scaled down (< 1.0)", multCorr < 1.0)

        // Test negative-correlation diversification boost
        val (multHedge, msgHedge) = PortfolioTradeInteractionEngine.evaluateProposedTradeInteraction("EURUSD", "MacroGrid", 1000.0)
        assertTrue("Negative correlation trade should receive sizing boost (> 1.0)", multHedge > 1.0)
    }
}
