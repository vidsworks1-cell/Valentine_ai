package com.example

import com.example.aza.strategy.ExecutionIntelligenceEngine
import com.example.aza.strategy.ExecutionTelemetryRecord
import com.example.aza.strategy.MarketRegime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExecutionIntelligenceEngineTest {

    @Test
    fun testTelemetryRecordingAndExecutionQualityScoreCalculation() {
        ExecutionIntelligenceEngine.runExecutionIntelligenceSimulation()

        val summary = ExecutionIntelligenceEngine.getExecutionQualitySummary()
        assertNotNull("Summary should not be null", summary)
        assertTrue("Overall score should be bounded 0..100", summary.overallExecutionQualityScore in 0.0..100.0)
        assertTrue("Average execution speed should be positive", summary.averageExecutionSpeedMs >= 0.0)
        assertTrue("Median slippage should be tracked", summary.medianSlippageBps >= 0.0)
        assertTrue("Exchange fill rate map should contain entries", summary.fillRateByExchange.isNotEmpty())
        assertTrue("Latency variance should be non-negative", summary.averageLatencyVarianceMs2 >= 0.0)
    }

    @Test
    fun testPoorEnvironmentDetectionAndSizingAdjustment() {
        val now = System.currentTimeMillis()

        // Inject 5 bad execution records for OKX on BTC during LIQUIDITY_CRUNCH
        repeat(5) { i ->
            ExecutionIntelligenceEngine.recordExecution(
                ExecutionTelemetryRecord(
                    exchange = "OKX Global",
                    symbol = "BTC",
                    strategyId = "MeanReversion",
                    marketRegime = MarketRegime.LIQUIDITY_CRUNCH,
                    isEntry = true,
                    requestedTimestampMs = now - (i * 1000L),
                    executedTimestampMs = now - (i * 1000L) + 250L, // 250ms delay
                    targetSize = 2.0,
                    filledSize = 1.2, // 60% fill rate
                    limitOrSignalPrice = 50000.0,
                    executedPrice = 50250.0, // High slippage (50 bps)
                    slippageBps = 50.0,
                    spreadBps = 12.0,
                    latencyMs = 180L,
                    orderQualityScore = 40.0,
                    fillQualityScore = 45.0
                )
            )
        }

        val adjustment = ExecutionIntelligenceEngine.evaluateEnvironmentQuality(
            exchange = "OKX Global",
            symbol = "BTC",
            strategyId = "MeanReversion",
            marketRegime = MarketRegime.LIQUIDITY_CRUNCH
        )

        assertTrue("Environment should be flagged as poor execution", adjustment.isPoorExecutionEnvironment)
        assertTrue("Confidence multiplier should be reduced (< 1.0)", adjustment.confidenceMultiplier < 1.0)
        assertTrue("Position size multiplier should be reduced (< 1.0)", adjustment.positionSizeMultiplier < 1.0)
        assertTrue("Uncertainty should increase (> 0.0%)", adjustment.uncertaintyIncreasePct > 0.0)
        assertTrue("Routing recommendation should suggest alternate venue", adjustment.routingRecommendation.contains("Reroute"))
    }
}
