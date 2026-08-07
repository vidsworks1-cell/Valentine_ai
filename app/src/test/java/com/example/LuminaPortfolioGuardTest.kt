package com.example

import com.example.aza.strategy.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LuminaPortfolioGuardTest {

    @Test
    fun testSignalValidatedYesCapitalEligibleNoOnOppositePosition() {
        val prices = listOf(
            680.0, 675.0, 670.0, 665.0, 660.0, 655.0, 650.0, 645.0, 640.0, 635.0,
            630.0, 625.0, 620.0, 615.0, 610.0, 605.0, 600.0, 595.0, 590.0, 580.0
        )
        val proposal = TradeProposal(
            strategyName = "Momentum Breakout",
            market = "BNBUSDT",
            signal = SignalType.SELL,
            confidenceScore = 0.75,
            entryPrice = 580.0,
            stopLoss = 590.0,
            takeProfit = 560.0,
            positionSize = 0.5,
            riskRewardRatio = 2.0,
            explanation = "Sell breakdown setup"
        )

        // Conviction = 75.8%, opposite position exists, hedge threshold = 82%
        val context = StrategyEvaluationEngine.buildPipelineDecisionContext(
            symbol = "BNBUSDT",
            side = "SELL",
            requestedStrategy = "Momentum Breakout",
            proposal = proposal,
            prices = prices,
            mtfWeightedScore = 80.0,
            mtfThreshold = 66.0,
            metaConviction = 75.8,
            hasOppositePosition = true,
            recentWinRatePct = 65.0,
            recentExpectancyR = 0.65,
            sampleSize = 30
        )

        // Verify clean separation of Signal Quality from Capital Eligibility
        System.out.println("DEBUG test context: decisionState=${context.decisionState}, rejectionAuthority=${context.rejectionAuthority}, rejectionReason=${context.rejectionReason}, isSignalValidated=${context.isSignalValidated}")
        context.guardResults.forEach { g ->
            System.out.println("  Guard ${g.guardName}: passed=${g.isPassed}, rationale=${g.rationale}")
        }
        assertTrue("Signal must be validated when strategy/MTF/RRR pass", context.isSignalValidated)
        assertEquals("YES", context.signalValidated)

        assertFalse("Capital must NOT be eligible when opposite exposure is below hedge threshold", context.isCapitalEligible)
        assertEquals("NO", context.capitalEligible)

        assertFalse("Execution must NOT be authorized when capital allocation is rejected", context.isExecutionAuthorized)
        assertEquals("NO", context.executionAuthorized)

        assertEquals("Decision state must be CAPITAL_REJECTED", OverallDecisionState.CAPITAL_REJECTED, context.decisionState)
        assertEquals("Rejection authority must be PORTFOLIO_GUARD", DecisionAuthority.PORTFOLIO_GUARD, context.rejectionAuthority)
        assertEquals("Capital rejection reason must state OPPOSITE_EXPOSURE_BELOW_HEDGE_THRESHOLD", "OPPOSITE_EXPOSURE_BELOW_HEDGE_THRESHOLD", context.capitalRejectionReason)
    }

    @Test
    fun testDynamicHedgeThresholdCalculation() {
        val volatileRegimeState = PortfolioGuardState(
            accountBalance = 10000.0,
            netExposureUSD = 2000.0
        )

        // High volatility + high net exposure reduction lowers dynamic threshold from 0.82 to ~0.72
        val eval = PortfolioTradeInteractionEngine.evaluatePortfolioCapitalAuthority(
            symbol = "BTCUSDT",
            side = "SELL",
            proposedSizeUSD = 1000.0,
            incomingConfidence = 0.75,
            marketRegime = "HIGH_VOLATILITY",
            hasOppositePosition = true,
            portfolioState = volatileRegimeState
        )

        assertTrue("Dynamic threshold should adjust lower under high volatility and net exposure reduction", eval.dynamicHedgeThreshold < 0.82)
        assertTrue("Hedge should pass if incoming confidence exceeds dynamic threshold", eval.isCapitalEligible)
        assertEquals(TradeActionIntent.HEDGE, eval.actionIntent)
    }

    @Test
    fun testPyramidingScaleInControls() {
        val stateNotInProfit = PortfolioGuardState(
            existingPositionExposureUSD = 2000.0,
            existingPositionUnrealizedPnLPct = -1.5, // Loss
            existingScaleInCount = 1
        )

        val evalNotInProfit = PortfolioTradeInteractionEngine.evaluatePortfolioCapitalAuthority(
            symbol = "ETHUSDT",
            side = "BUY",
            proposedSizeUSD = 1000.0,
            incomingConfidence = 0.80,
            hasSameSidePosition = true,
            isPyramidScaleIn = true,
            portfolioState = stateNotInProfit
        )

        assertFalse("Pyramid scale-in must be rejected when existing position is not in profit", evalNotInProfit.isCapitalEligible)
        assertTrue("Rejection reason must identify pyramid rule violation", evalNotInProfit.capitalRejectionReason.contains("PYRAMID_POSITION_NOT_IN_PROFIT"))
        assertEquals(TradeActionIntent.SCALE_IN, evalNotInProfit.actionIntent)

        val stateInProfit = PortfolioGuardState(
            existingPositionExposureUSD = 2000.0,
            existingPositionUnrealizedPnLPct = 3.5, // Profit
            existingScaleInCount = 1,
            maxScaleInsAllowed = 3
        )

        val evalInProfit = PortfolioTradeInteractionEngine.evaluatePortfolioCapitalAuthority(
            symbol = "ETHUSDT",
            side = "BUY",
            proposedSizeUSD = 1000.0,
            incomingConfidence = 0.80,
            hasSameSidePosition = true,
            isPyramidScaleIn = true,
            portfolioState = stateInProfit
        )

        assertTrue("Pyramid scale-in must be approved when existing position is in profit and within limits", evalInProfit.isCapitalEligible)
    }

    @Test
    fun testActionIntentClassificationAndAuditLogging() {
        val evalHedge = PortfolioTradeInteractionEngine.evaluatePortfolioCapitalAuthority(
            symbol = "SOLUSDT",
            side = "SELL",
            proposedSizeUSD = 500.0,
            incomingConfidence = 0.70,
            hasOppositePosition = true
        )

        assertEquals("Action intent for opposite position signal must be HEDGE", TradeActionIntent.HEDGE, evalHedge.actionIntent)
        assertTrue("Audit log must contain ACTION_INTENT", evalHedge.auditLog.contains("ACTION_INTENT: HEDGE"))
        assertTrue("Audit log must contain SIGNAL_VALIDATED", evalHedge.auditLog.contains("SIGNAL_VALIDATED: YES"))
        assertTrue("Audit log must contain CAPITAL_ELIGIBLE", evalHedge.auditLog.contains("CAPITAL_ELIGIBLE:"))
        assertTrue("Audit log must contain PORTFOLIO_METRICS", evalHedge.auditLog.contains("PORTFOLIO_METRICS:"))
    }
}
