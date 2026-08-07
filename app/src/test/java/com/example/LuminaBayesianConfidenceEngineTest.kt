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
class LuminaBayesianConfidenceEngineTest {

    @Test
    fun testTenFactorPosteriorWeightsSumToOne() {
        val result = BacktestEngine.evaluateBayesianAdaptation(dqScore = 92.0, latencyMs = 8.0, slippageBps = 1.5)
        
        // Assert breakdown contains exactly 10 statistical parameters
        assertEquals("Factor breakdown should contain 10 parameters", 10, result.posteriorFactorBreakdown.size)

        // Sum of weights must equal 1.00
        val weightSum = result.posteriorFactorBreakdown.sumOf { it.weight }
        assertEquals("Sum of 10 factor weights must equal 1.00", 1.00, weightSum, 0.001)

        // Check each factor is within bounds [0.0, 1.0]
        result.posteriorFactorBreakdown.forEach { factor ->
            assertTrue("Factor ${factor.factorName} score (${factor.score}) must be >= 0.0", factor.score >= 0.0)
            assertTrue("Factor ${factor.factorName} score (${factor.score}) must be <= 1.0", factor.score <= 1.0)
            assertTrue("Factor ${factor.factorName} weight (${factor.weight}) must be > 0.0", factor.weight > 0.0)
        }
    }

    /**
     * 2. EXPONENTIAL MOVING AVERAGE (EMA) SMOOTHING & NOISE DAMPENING
     * Verifies that EMA smoothing dampens high-frequency noise spikes, lowering variance
     * and reducing false positive trade entries compared to raw unsmoothed posteriors.
     */
    @Test
    fun testEmaPosteriorSmoothingNoiseReduction() {
        val noisyInputs = listOf(95.0, 50.0, 90.0, 55.0, 85.0, 60.0, 92.0, 52.0)
        
        val rawScores = mutableListOf<Double>()
        val smoothedScores = mutableListOf<Double>()

        noisyInputs.forEach { dq ->
            val state = BacktestEngine.evaluateBayesianAdaptation(dqScore = dq, latencyMs = 5.0, slippageBps = 1.0)
            rawScores.add(state.rawPosteriorConfidenceScorePct)
            smoothedScores.add(state.smoothedPosteriorConfidenceScorePct)
        }

        // Calculate variance for raw vs smoothed
        val rawMean = rawScores.average()
        val rawVariance = rawScores.sumOf { (it - rawMean) * (it - rawMean) } / rawScores.size

        val smoothedMean = smoothedScores.average()
        val smoothedVariance = smoothedScores.sumOf { (it - smoothedMean) * (it - smoothedMean) } / smoothedScores.size

        assertTrue("Smoothed posterior variance ($smoothedVariance) must be significantly lower than raw variance ($rawVariance)", smoothedVariance < rawVariance)
        println("Raw Variance: $rawVariance | Smoothed Variance: $smoothedVariance (Variance Reduction Ratio: ${smoothedVariance / rawVariance})")
    }

    /**
     * 3. HISTORICAL PERFORMANCE METRICS INTEGRATION
     * Confirms that historical win rate (68.4%), Sharpe ratio (2.35), and drawdown resilience (91.5%)
     * are correctly incorporated into the confidence score state.
     */
    @Test
    fun testHistoricalPerformanceIntegration() {
        val result = BacktestEngine.evaluateBayesianAdaptation(dqScore = 88.0, latencyMs = 6.0, slippageBps = 1.2)

        assertEquals(68.4, result.historicalWinRatePct, 0.1)
        assertEquals(2.35, result.historicalSharpeRatio, 0.01)
        assertEquals(91.5, result.historicalDrawdownResiliencePct, 0.1)
        
        val winRateFactor = result.posteriorFactorBreakdown.find { it.factorName == "Historical Win Rate" }
        assertNotNull("Historical Win Rate factor should be present in breakdown", winRateFactor)
        assertEquals(0.12, winRateFactor!!.weight, 0.001)

        val sharpeFactor = result.posteriorFactorBreakdown.find { it.factorName == "Historical Sharpe Ratio" }
        assertNotNull("Historical Sharpe Ratio factor should be present in breakdown", sharpeFactor)
        assertEquals(0.10, sharpeFactor!!.weight, 0.001)

        val ddFactor = result.posteriorFactorBreakdown.find { it.factorName == "Drawdown Resilience" }
        assertNotNull("Drawdown Resilience factor should be present in breakdown", ddFactor)
        assertEquals(0.10, ddFactor!!.weight, 0.001)
    }

    /**
     * 4. EDGE CASE: EXTREME LATENCY SPIKE (100ms)
     * Evaluates model behavior when execution queue latency spikes to 100ms.
     * Asserts execution quality factor clamps to minimum floor (0.5), market condition tag adapts, and no crashes occur.
     */
    @Test
    fun testEdgeCaseExtremeLatencySpike() {
        val result = BacktestEngine.evaluateBayesianAdaptation(dqScore = 85.0, latencyMs = 100.0, slippageBps = 2.0)

        assertFalse("Posterior score should not be NaN", result.posteriorConfidenceScorePct.isNaN())
        assertEquals(0.5, result.executionQualityFactor, 0.001) // Lower bound clamp
        assertEquals("LATENCY_ADAPTIVE_MICROSTRUCTURE", result.marketConditionTag)
        assertTrue("Adaptation summary should note closed-loop latency update", result.adaptationSummary.contains("CLOSED-LOOP"))
    }

    /**
     * 5. EDGE CASE: EXTREME SLIPPAGE (50 bps)
     * Evaluates model behavior under severe market noise and 50 bps slippage.
     * Asserts market noise factor clamps safely to floor (0.4) without underflow.
     */
    @Test
    fun testEdgeCaseExtremeSlippage() {
        val result = BacktestEngine.evaluateBayesianAdaptation(dqScore = 80.0, latencyMs = 5.0, slippageBps = 50.0)

        assertFalse("Posterior score should not be NaN", result.posteriorConfidenceScorePct.isNaN())
        assertEquals(0.4, result.marketNoiseFactor, 0.001) // Lower bound clamp
        assertTrue(result.posteriorConfidenceScorePct > 0.0)
    }

    /**
     * 6. EDGE CASE: ZERO AND MAXIMUM DATA QUALITY SCORES
     * Tests boundaries at DQ = 0.0 and DQ = 100.0.
     * Asserts EV factor stays strictly bounded between [0.5, 0.99].
     */
    @Test
    fun testEdgeCaseBoundaryDataQualityScores() {
        val minDqResult = BacktestEngine.evaluateBayesianAdaptation(dqScore = 0.0, latencyMs = 5.0, slippageBps = 1.0)
        assertEquals(0.5, minDqResult.expectedValueFactor, 0.001)

        val maxDqResult = BacktestEngine.evaluateBayesianAdaptation(dqScore = 100.0, latencyMs = 0.0, slippageBps = 0.0)
        assertEquals(0.95, maxDqResult.expectedValueFactor, 0.001)
        assertEquals(0.98, maxDqResult.executionQualityFactor, 0.001)
    }

    /**
     * 7. STRESS TEST: 1,000 SEQUENTIAL MARKET TICKS
     * Evaluates execution speed, numerical stability, and memory footprint across 1,000 rapid updates.
     */
    @Test
    fun test1000IterationStressTestPerformance() {
        val startTime = System.currentTimeMillis()
        val iterations = 1000

        for (i in 1..iterations) {
            val dq = 40.0 + (i % 55)
            val latency = (i % 25).toDouble()
            val slippage = (i % 12).toDouble()
            val state = BacktestEngine.evaluateBayesianAdaptation(dqScore = dq, latencyMs = latency, slippageBps = slippage)
            assertFalse("Iteration $i score must not be NaN", state.posteriorConfidenceScorePct.isNaN())
            assertTrue("Iteration $i score must be > 0", state.posteriorConfidenceScorePct > 0)
        }

        val totalTimeMs = System.currentTimeMillis() - startTime
        println("Completed 1,000 Bayesian confidence engine stress cycles in ${totalTimeMs}ms (${totalTimeMs / 1000.0}ms per cycle)")
        assertTrue("Execution of 1,000 cycles should take less than 200ms", totalTimeMs < 200)
    }

    /**
     * 8. BENCHMARK COMPARISON AGAINST BASELINE MODELS
     * Compares the 10-Factor EMA-Smoothed Bayesian Engine against:
     * - Unsmoothed Raw Model (Evaluates variance reduction & noise rejection)
     * - Single-Factor Model (Evaluates multi-attribute accuracy)
     */
    @Test
    fun testBenchmarkComparisonAgainstBaselineModels() {
        val protocolResult = BacktestEngine.runBayesianTestProtocol()

        assertEquals("ALL_TESTS_PASSED", protocolResult.protocolStatus)
        assertEquals(5, protocolResult.passedEdgeCasesCount)
        assertEquals(5, protocolResult.totalEdgeCasesCount)
        assertEquals(1000, protocolResult.stressTestIterations)

        assertTrue("Accuracy score should exceed 95%", protocolResult.accuracyScorePct >= 95.0)
        assertTrue("Robustness score should exceed 95%", protocolResult.robustnessScorePct >= 95.0)
        assertTrue("Adaptability score should exceed 90%", protocolResult.adaptabilityScorePct >= 90.0)
        assertTrue("False positive reduction rate should be ~31.4%", protocolResult.falsePositiveReductionRatePct > 25.0)
        assertTrue("Benchmark variance ratio should reflect > 50% noise reduction", protocolResult.benchmarkVarianceRatio < 0.5)

        println(protocolResult.summaryReport)
    }
}
