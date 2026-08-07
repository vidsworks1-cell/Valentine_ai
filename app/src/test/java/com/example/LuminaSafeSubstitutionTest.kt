package com.example

import com.example.aza.strategy.DecisionContext
import com.example.aza.strategy.SignalType
import com.example.aza.strategy.StrategyEvaluationEngine
import com.example.aza.strategy.StrategyRegistry
import com.example.aza.strategy.TradeProposal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuminaSafeSubstitutionTest {

    @Test
    fun testSubstitutionRecalculatesAllFieldsAndPreventsStaleParameterRisk() {
        val prices = List(50) { 3000.0 + (it * 2.5) }
        
        // Stale / high risk proposal from original strategy
        val staleProposal = TradeProposal(
            strategyName = "Volatility Expansion Breakout",
            market = "ETHUSDT",
            signal = SignalType.BUY,
            confidenceScore = 0.95, // Stale high confidence
            entryPrice = 3125.0,
            stopLoss = 2500.0, // Stale wide stop loss from original strategy
            takeProfit = 4500.0, // Stale wide take profit
            positionSize = 5.0, // Stale large position size
            riskRewardRatio = 2.2,
            explanation = "Original Volatility Expansion proposal"
        )

        // Force underperforming metrics for original strategy to trigger substitution
        val context = StrategyEvaluationEngine.buildPipelineDecisionContext(
            symbol = "ETHUSDT",
            side = "BUY",
            requestedStrategy = "Volatility Expansion Breakout",
            proposal = staleProposal,
            prices = prices,
            sampleSize = 25,
            recentWinRatePct = 37.5,
            recentExpectancyR = 0.02,
            accountBalance = 10000.0,
            currentRiskAllocationPct = 0.05
        )

        assertTrue("Substitution must trigger when original strategy is underperforming", context.wasSubstituted)
        assertEquals("Volatility Expansion Breakout", context.originalStrategy)
        assertEquals("Liquidity Sweep", context.effectiveStrategy)

        // Verify stop loss, take profit, position size, and confidence are RECALCULATED from replacement strategy
        val replacementStrategy = StrategyRegistry.getStrategy("Liquidity Sweep")!!
        val replacementProposal = replacementStrategy.evaluate("ETHUSDT", prices, 10000.0, 0.05)

        assertEquals("Stop loss must be recalculated from replacement strategy", replacementProposal.stopLoss, context.stopLoss, 0.01)
        assertEquals("Take profit must be recalculated from replacement strategy", replacementProposal.takeProfit, context.takeProfit, 0.01)
        assertEquals("Position size must be recalculated from replacement strategy", replacementProposal.positionSize, context.portfolioExposureUSD / context.entryPrice, 0.01)

        assertNotEquals("Stop loss must NOT equal stale stop loss from original strategy", 2500.0, context.stopLoss, 0.01)
        assertNotEquals("Position size must NOT equal stale position size from original strategy", 5.0, context.portfolioExposureUSD / context.entryPrice, 0.01)

        // Verify recalculated fields list
        assertTrue("Recalculated fields must contain stopLoss", context.recalculatedFields.contains("stopLoss"))
        assertTrue("Recalculated fields must contain takeProfit", context.recalculatedFields.contains("takeProfit"))
        assertTrue("Recalculated fields must contain positionSize", context.recalculatedFields.contains("positionSize"))
        assertTrue("Recalculated fields must contain riskMultiplier", context.recalculatedFields.contains("riskMultiplier"))
        assertTrue("Recalculated fields must contain strategyState", context.recalculatedFields.contains("strategyState"))
        assertTrue("Recalculated fields must contain mtfCompatibility", context.recalculatedFields.contains("mtfCompatibility"))
        assertTrue("Recalculated fields must contain executionRoute", context.recalculatedFields.contains("executionRoute"))
    }

    @Test
    fun testSubstitutionEligibilityGatingRejectsIneligibleCandidate() {
        val candidatePool = listOf(
            StrategyEvaluationEngine.SubstitutionCandidateProfile(
                strategyName = "IneligibleSmallSample",
                sampleSize = 8, // Ineligible: N < 15
                winRatePct = 80.0,
                expectancyR = 0.50,
                designedRegimes = listOf("ANY")
            ),
            StrategyEvaluationEngine.SubstitutionCandidateProfile(
                strategyName = "IneligibleNegativeExpectancy",
                sampleSize = 25,
                winRatePct = 60.0,
                expectancyR = 0.05, // Ineligible: Expectancy < 0.10
                designedRegimes = listOf("ANY")
            ),
            StrategyEvaluationEngine.SubstitutionCandidateProfile(
                strategyName = "IneligibleSideMismatch",
                sampleSize = 30,
                winRatePct = 65.0,
                expectancyR = 0.60,
                designedRegimes = listOf("ANY"),
                supportedSides = listOf("SELL") // Ineligible for BUY
            )
        )

        val result = StrategyEvaluationEngine.evaluateStrategySubstitution(
            requestedStrategy = "Volatility Expansion Breakout",
            currentRegime = "HIGH_VOLATILITY",
            recentWinRatePct = 35.0,
            recentExpectancyR = 0.02,
            sampleSize = 20,
            side = "BUY",
            candidatePool = candidatePool
        )

        assertFalse("Substitution must be rejected when no candidate passes eligibility", result.wasSubstituted)
        assertEquals("Original strategy retained upon candidate rejection", "Volatility Expansion Breakout", result.substitutedStrategyName)
        assertTrue("Reasoning must log rejection", result.whyReplacementSelected.contains("REJECTED"))
    }

    @Test
    fun testCompositeRankingPrefersExpectancyOverRawWinRate() {
        val candidateHighWinRateLowExp = StrategyEvaluationEngine.SubstitutionCandidateProfile(
            strategyName = "HighWinRateLowExp",
            sampleSize = 30,
            winRatePct = 85.0, // High raw win rate
            expectancyR = 0.12, // Low expectancy
            recentStabilityScore = 0.50,
            maxDrawdownPct = 8.0,
            executionQualityScore = 0.70,
            riskEfficiency = 1.1,
            designedRegimes = listOf("ANY")
        )

        val candidateModerateWinRateHighExp = StrategyEvaluationEngine.SubstitutionCandidateProfile(
            strategyName = "ModerateWinRateHighExp",
            sampleSize = 35,
            winRatePct = 58.0, // Moderate win rate
            expectancyR = 0.55, // High expectancy
            recentStabilityScore = 0.85,
            maxDrawdownPct = 4.0,
            executionQualityScore = 0.90,
            riskEfficiency = 2.2,
            designedRegimes = listOf("ANY")
        )

        val candidatePool = listOf(candidateHighWinRateLowExp, candidateModerateWinRateHighExp)

        val result = StrategyEvaluationEngine.evaluateStrategySubstitution(
            requestedStrategy = "Volatility Expansion Breakout",
            currentRegime = "HIGH_VOLATILITY",
            recentWinRatePct = 35.0,
            recentExpectancyR = 0.02,
            sampleSize = 20,
            side = "BUY",
            candidatePool = candidatePool
        )

        assertTrue("Substitution must occur", result.wasSubstituted)
        assertEquals("Composite score must select candidate with higher expectancy over raw win rate", "ModerateWinRateHighExp", result.substitutedStrategyName)
    }

    @Test
    fun testAuditLoggingContainsAllRequiredFields() {
        val result = StrategyEvaluationEngine.evaluateStrategySubstitution(
            requestedStrategy = "Volatility Expansion Breakout",
            currentRegime = "HIGH_VOLATILITY",
            recentWinRatePct = 37.5,
            recentExpectancyR = 0.02,
            sampleSize = 25,
            side = "BUY"
        )

        assertTrue(result.wasSubstituted)
        assertTrue("Audit log must contain ORIGINAL_STRATEGY", result.substitutionReasoning.contains("ORIGINAL_STRATEGY"))
        assertTrue("Audit log must contain SUBSTITUTED_STRATEGY", result.substitutionReasoning.contains("SUBSTITUTED_STRATEGY"))
        assertTrue("Audit log must contain SUBSTITUTION_SCORE", result.substitutionReasoning.contains("SUBSTITUTION_SCORE"))
        assertTrue("Audit log must contain WHY_ORIGINAL_REJECTED", result.substitutionReasoning.contains("WHY_ORIGINAL_REJECTED"))
        assertTrue("Audit log must contain WHY_REPLACEMENT_SELECTED", result.substitutionReasoning.contains("WHY_REPLACEMENT_SELECTED"))
        assertTrue("Audit log must contain RECALCULATED_FIELDS", result.substitutionReasoning.contains("RECALCULATED_FIELDS"))
    }
}
