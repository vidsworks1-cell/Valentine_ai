package com.example

import com.example.aza.intelligence.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LuminaSecondBrainMetaLearningTest {

    @Test
    fun testDualMemoryArchitectureAndForgettingCurves() {
        val state = LuminaSecondBrainMetaLearningEngine.getSummaryState()

        // Verify initial state populated with seed memory
        assertTrue("Total episodes should be > 0", state.totalEpisodesRecorded > 0)
        assertTrue("STM episodes should be > 0", state.stmEpisodeCount > 0)
        assertTrue("LTM episodes should be > 0", state.ltmEpisodeCount > 0)

        // Verify forgetting parameters
        assertEquals("Lambda STM should be 0.05", 0.05, state.forgettingCurveLambdaStm, 0.001)
        assertEquals("Lambda LTM should be 0.001", 0.001, state.forgettingCurveLambdaLtm, 0.0001)

        // Test retention calculation
        val epStm = ContextMemoryEpisode(
            id = "TEST-STM",
            timestamp = System.currentTimeMillis() - (24 * 3600000L), // 24 hours ago
            environmentName = "TEST_ENV",
            isWin = true,
            profitR = 2.0,
            volatilityPct = 2.5,
            atrValue = 30.0,
            rawConfidenceScorePct = 80.0,
            hourOfDay = 10,
            dayOfWeek = 3,
            marketStructure = "HIGHER_HIGHS_LOWS",
            trendStrengthAdx = 30.0,
            liquidityDepthUSD = 20_000_000.0,
            askBidSpreadBps = 1.0,
            strategyName = "Breakout",
            memoryType = MemoryType.SHORT_TERM
        )

        val retention24h = LuminaSecondBrainMetaLearningEngine.calculateRetention(epStm, System.currentTimeMillis())
        // e^(-0.05 * 24) = e^(-1.2) approx 0.301
        assertTrue("Retention after 24h in STM should decay to ~30%", retention24h > 0.25 && retention24h < 0.35)

        epStm.memoryType = MemoryType.LONG_TERM
        val retentionLtm24h = LuminaSecondBrainMetaLearningEngine.calculateRetention(epStm, System.currentTimeMillis())
        // e^(-0.001 * 24) = e^(-0.024) approx 0.976
        assertTrue("Retention after 24h in LTM should remain very high (>95%)", retentionLtm24h > 0.95)
    }

    @Test
    fun testPatternRecognitionMatchingWinningAndFailingEnvironments() {
        // Evaluate trade in ideal winning conditions (Peak session, high liquidity, strong ADX, Bull structure)
        val winningEval = LuminaSecondBrainMetaLearningEngine.evaluateDecisionConfidence(
            rawConfidencePct = 78.0,
            volatilityPct = 2.5,
            atrValue = 32.0,
            hourOfDay = 10, // Peak European session
            dayOfWeek = 3,  // Wednesday
            marketStructure = "HIGHER_HIGHS_LOWS",
            trendStrengthAdx = 35.0,
            liquidityDepthUSD = 20_000_000.0,
            askBidSpreadBps = 0.8
        )

        assertTrue("Calibrated confidence should boost or remain high in winning context", winningEval.calibratedConfidencePct >= winningEval.rawConfidencePct)
        assertTrue("Should be approved by Second Brain", winningEval.isApprovedBySecondBrain)
        assertNotNull("Matching winning environment should be identified", winningEval.matchingWinningEnvironment)

        // Evaluate trade in bad failing conditions (Off-peak, low liquidity, high spread, low ADX chop)
        val failingEval = LuminaSecondBrainMetaLearningEngine.evaluateDecisionConfidence(
            rawConfidencePct = 55.0,
            volatilityPct = 0.5, // Low vol chop
            atrValue = 8.0,
            hourOfDay = 23, // Off-peak late hours
            dayOfWeek = 7,  // Sunday
            marketStructure = "RANGE_BOUND",
            trendStrengthAdx = 10.0, // Weak ADX
            liquidityDepthUSD = 2_000_000.0,
            askBidSpreadBps = 4.5
        )

        assertTrue("Calibrated confidence should be significantly lower in failing context", failingEval.calibratedConfidencePct < failingEval.rawConfidencePct)
        assertFalse("Should be suppressed by Second Brain in bad context", failingEval.isApprovedBySecondBrain)
        assertNotNull("Matching failing environment should be identified", failingEval.matchingFailingEnvironment)
    }

    @Test
    fun testReinforcementLearningQPolicyAndEpisodeLearning() {
        val initialSummary = LuminaSecondBrainMetaLearningEngine.getSummaryState()
        val initialEpisodeCount = initialSummary.totalEpisodesRecorded

        // Inject 5 consecutive failing trades in a specific context (Off-peak chop)
        for (i in 1..5) {
            LuminaSecondBrainMetaLearningEngine.recordEpisodeAndLearn(
                symbol = "ETHUSDT",
                strategyName = "MeanReversionTest",
                isWin = false,
                profitR = -1.8,
                volatilityPct = 0.7,
                atrValue = 10.0,
                rawConfidenceScorePct = 65.0,
                hourOfDay = 23,
                dayOfWeek = 6,
                marketStructure = "RANGE_BOUND",
                trendStrengthAdx = 12.0,
                liquidityDepthUSD = 3_000_000.0,
                askBidSpreadBps = 3.0,
                environmentName = "OFF_PEAK_CHOP_SLIDE"
            )
        }

        val updatedSummary = LuminaSecondBrainMetaLearningEngine.getSummaryState()
        assertEquals("Episode count should increase by 5", initialEpisodeCount + 5, updatedSummary.totalEpisodesRecorded)

        // Now evaluate a trade proposal in that exact same off-peak chop context
        val evalResult = LuminaSecondBrainMetaLearningEngine.evaluateDecisionConfidence(
            rawConfidencePct = 70.0,
            volatilityPct = 0.7,
            atrValue = 10.0,
            hourOfDay = 23,
            dayOfWeek = 6,
            marketStructure = "RANGE_BOUND",
            trendStrengthAdx = 12.0,
            liquidityDepthUSD = 3_000_000.0,
            askBidSpreadBps = 3.0
        )

        assertTrue("Confidence delta should be negative due to RL Q-Policy penalization", evalResult.confidenceDeltaPct < 0.0)
        assertTrue("Decision rationale should indicate caution or suppression", evalResult.decisionRationale.contains("CAUTIONARY") || evalResult.decisionRationale.contains("SUPPRESS"))
    }

    @Test
    fun testOptimalContextProfileComputation() {
        val profile = LuminaSecondBrainMetaLearningEngine.computeOptimalContextProfile()

        assertNotNull("Best volatility range should not be null", profile.bestVolatilityRange)
        assertNotNull("Best ATR range should not be null", profile.bestAtrRange)
        assertNotNull("Best confidence range should not be null", profile.bestConfidenceRange)
        assertFalse("Winning environments should not be empty", profile.winningEnvironments.isEmpty())
        assertFalse("Failing environments should not be empty", profile.failingEnvironments.isEmpty())
    }

    @Test
    fun testMemorySweepAndConsolidation() {
        val sweepLog = LuminaSecondBrainMetaLearningEngine.performForgettingAndConsolidationSweep()
        assertNotNull("Sweep log should be generated", sweepLog)
        assertTrue("Sweep log should describe operation", sweepLog.contains("Swept memory registers"))
    }

    @Test
    fun test500IterationStressTestPerformance() {
        val startTime = System.currentTimeMillis()

        for (i in 1..500) {
            val eval = LuminaSecondBrainMetaLearningEngine.evaluateDecisionConfidence(
                rawConfidencePct = 50.0 + (i % 45),
                volatilityPct = 1.0 + (i % 5) * 0.5,
                atrValue = 15.0 + (i % 30),
                hourOfDay = i % 24,
                dayOfWeek = 1 + (i % 7),
                marketStructure = if (i % 2 == 0) "HIGHER_HIGHS_LOWS" else "RANGE_BOUND",
                trendStrengthAdx = 15.0 + (i % 35),
                liquidityDepthUSD = 5_000_000.0 + (i % 20) * 1_000_000.0,
                askBidSpreadBps = 0.5 + (i % 5) * 0.5
            )

            assertFalse("Calibrated confidence should not be NaN", eval.calibratedConfidencePct.isNaN())
            assertTrue("Calibrated confidence should be between 0 and 100", eval.calibratedConfidencePct in 0.0..100.0)
        }

        val duration = System.currentTimeMillis() - startTime
        println("Completed 500 Second Brain meta-learning evaluations in ${duration}ms")
        assertTrue("500 evaluations should run in under 200ms", duration < 200)
    }
}
