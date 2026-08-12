package com.example.aza.strategy

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Matrix classification distinguishing trade execution status vs counterfactual/realized outcome.
 */
enum class DecisionMatrixClassification {
    APPROVED_WON,              // True Positive: Approved & Trade Won
    APPROVED_LOST,             // False Positive: Approved & Trade Lost
    REJECTED_WOULD_HAVE_WON,   // False Negative / False Rejection: Rejected & Counterfactual Won
    REJECTED_WOULD_HAVE_LOST   // True Negative / Useful Rejection: Rejected & Counterfactual Lost
}

/**
 * Explicit architecture distinction separating Decision Process Quality from Trade Outcome.
 */
enum class ProcessOutcomeCategory {
    GOOD_DECISION_WINNING_TRADE, // High DQS (>=65.0), Favorable outcome
    GOOD_DECISION_LOSING_TRADE,  // High DQS (>=65.0), Unfavorable outcome (Valid risk, standard market noise)
    BAD_DECISION_WINNING_TRADE,   // Low DQS (<65.0), Favorable outcome (Lucky outcome, bad process)
    BAD_DECISION_LOSING_TRADE     // Low DQS (<65.0), Unfavorable outcome (Poor process & bad outcome)
}

/**
 * Evaluates the 8 core quality dimensions for every decision.
 */
data class QualityDimensionScores(
    val signalQuality: Double,          // 0.0 - 100.0
    val strategyQuality: Double,        // 0.0 - 100.0
    val mtfQuality: Double,             // 0.0 - 100.0
    val liquidityQuality: Double,       // 0.0 - 100.0
    val metaReasoningQuality: Double,   // 0.0 - 100.0
    val riskSizingQuality: Double,      // 0.0 - 100.0
    val portfolioDecisionQuality: Double,// 0.0 - 100.0
    val executionQuality: Double        // 0.0 - 100.0
) {
    /**
     * Composite Decision Quality Score (DQS) evaluating process rigor independent of outcome.
     * Category weights are dynamically scaled according to the active market regime.
     */
    fun calculateDecisionQualityScore(marketRegime: String = "RANGE"): Double {
        val weights = when (marketRegime.uppercase(Locale.US)) {
            "TRENDING", "TRENDING_UP", "TRENDING_DOWN", "BULL", "BEAR" -> {
                // HTF Alignment (25%) & Strategy/Momentum (20%) priority
                doubleArrayOf(0.15, 0.20, 0.25, 0.05, 0.15, 0.08, 0.07, 0.05)
            }
            "VOLATILE", "HIGH_VOLATILITY", "ELEVATED_STRESS", "FLASH_CRASH" -> {
                // Liquidity & Order Flow Absorption (35%) & Risk Sizing (20%) priority
                doubleArrayOf(0.10, 0.10, 0.05, 0.35, 0.10, 0.20, 0.05, 0.05)
            }
            else -> {
                // Baseline RANGE_BOUND / RANGING weights
                doubleArrayOf(0.15, 0.15, 0.15, 0.10, 0.15, 0.10, 0.15, 0.05)
            }
        }
        val weightedScore = (signalQuality * weights[0]) +
                (strategyQuality * weights[1]) +
                (mtfQuality * weights[2]) +
                (liquidityQuality * weights[3]) +
                (metaReasoningQuality * weights[4]) +
                (riskSizingQuality * weights[5]) +
                (portfolioDecisionQuality * weights[6]) +
                (executionQuality * weights[7])
        return weightedScore.coerceIn(0.0, 100.0)
    }
}

/**
 * Complete evaluation record for a single capital allocation decision.
 */
data class DecisionEvaluationRecord(
    val decisionId: String,
    val timestamp: Long,
    val symbol: String,
    val isApproved: Boolean,
    val primaryGuardOrSubsystem: String,
    val qualityDimensions: QualityDimensionScores,
    val dqsScore: Double,
    val pnlUSD: Double, // Positive for win, negative for loss (realized or counterfactual)
    val matrixClassification: DecisionMatrixClassification,
    val processOutcomeCategory: ProcessOutcomeCategory,
    val counterfactualOutcome: CounterfactualOutcome?,
    val rationale: String
)

/**
 * Subsystem statistical breakdown including uncertainty and evidence constraints.
 */
data class SubsystemGuardStatistics(
    val subsystemName: String,
    val totalDecisions: Int,
    val sampleSize: Int,
    val minRequiredSampleSize: Int,
    val isEvidenceSufficientForAdaptation: Boolean,

    val approvedWonCount: Int,
    val approvedLostCount: Int,
    val rejectedWouldHaveWonCount: Int,
    val rejectedWouldHaveLostCount: Int,

    val precision: Double,               // Useful Rejections / Total Rejections
    val falseRejectionRate: Double,      // Rejected would have won / Total Rejections
    val falseAcceptanceRate: Double,     // Approved Lost / Total Approved
    val expectedValueContributionUSD: Double, // Delta EV added ($)
    val counterfactualValueUSD: Double,  // Avoided Loss USD - Missed Profit USD
    val uncertaintyMargin: Double,       // 95% Confidence Interval width (1.96 / sqrt(N))
    val adaptationReadinessMessage: String
)

/**
 * LUMINA DECISION QUALITY ENGINE
 *
 * Closed-loop decision quality evaluation layer.
 * Focuses on capital allocation quality rather than trade volume.
 * Explicitly separates process quality (DQS) from trade outcome.
 * Implements minimum evidence constraints before subsystem parameters can adapt.
 */
object LuminaDecisionQualityEngine {

    const val MIN_EVIDENCE_SAMPLE_SIZE = 30
    private val decisionLedger = mutableListOf<DecisionEvaluationRecord>()
    private val auditLogs = mutableListOf<String>()

    /**
     * Evaluates and records a decision, computing DQS and classifying process vs outcome.
     */
    fun evaluateAndRecordDecision(
        decisionId: String,
        symbol: String,
        isApproved: Boolean,
        primarySubsystem: String,
        qualityDimensions: QualityDimensionScores,
        pnlUSD: Double, // Realized PnL if approved, hypothetical PnL if rejected
        counterfactualOutcome: CounterfactualOutcome? = null,
        marketRegime: String = "RANGE"
    ): DecisionEvaluationRecord {

        val dqsScore = qualityDimensions.calculateDecisionQualityScore(marketRegime)
        val isTradeWon = pnlUSD > 0.0

        // 1. Matrix Classification
        val matrixClassification = when {
            isApproved && isTradeWon -> DecisionMatrixClassification.APPROVED_WON
            isApproved && !isTradeWon -> DecisionMatrixClassification.APPROVED_LOST
            !isApproved && isTradeWon -> DecisionMatrixClassification.REJECTED_WOULD_HAVE_WON
            else -> DecisionMatrixClassification.REJECTED_WOULD_HAVE_LOST
        }

        // 2. Process vs Outcome Category (Explicit distinction)
        val isGoodProcess = dqsScore >= 65.0
        val processOutcomeCategory = when {
            isGoodProcess && isTradeWon -> ProcessOutcomeCategory.GOOD_DECISION_WINNING_TRADE
            isGoodProcess && !isTradeWon -> ProcessOutcomeCategory.GOOD_DECISION_LOSING_TRADE
            !isGoodProcess && isTradeWon -> ProcessOutcomeCategory.BAD_DECISION_WINNING_TRADE
            else -> ProcessOutcomeCategory.BAD_DECISION_LOSING_TRADE
        }

        val processDescriptor = when (processOutcomeCategory) {
            ProcessOutcomeCategory.GOOD_DECISION_WINNING_TRADE -> "✅ GOOD DECISION + WINNING TRADE: Rigorous process with positive outcome."
            ProcessOutcomeCategory.GOOD_DECISION_LOSING_TRADE -> "🛡️ GOOD DECISION + LOSING TRADE: High-quality decision process (DQS ${String.format(Locale.US, "%.1f", dqsScore)}); outcome lost to expected market noise/risk distribution."
            ProcessOutcomeCategory.BAD_DECISION_WINNING_TRADE -> "⚠️ BAD DECISION + WINNING TRADE: Sub-standard process (DQS ${String.format(Locale.US, "%.1f", dqsScore)}); trade won due to lucky market conditions."
            ProcessOutcomeCategory.BAD_DECISION_LOSING_TRADE -> "⛔ BAD DECISION + LOSING TRADE: Poor process execution combined with loss."
        }

        val record = DecisionEvaluationRecord(
            decisionId = decisionId,
            timestamp = System.currentTimeMillis(),
            symbol = symbol,
            isApproved = isApproved,
            primaryGuardOrSubsystem = primarySubsystem,
            qualityDimensions = qualityDimensions,
            dqsScore = dqsScore,
            pnlUSD = pnlUSD,
            matrixClassification = matrixClassification,
            processOutcomeCategory = processOutcomeCategory,
            counterfactualOutcome = counterfactualOutcome,
            rationale = processDescriptor
        )

        synchronized(decisionLedger) {
            decisionLedger.add(record)
            if (decisionLedger.size > 1000) {
                decisionLedger.removeAt(0)
            }
        }

        logAudit("📊 DECISION QUALITY RECORDED [$decisionId]: Subsystem '$primarySubsystem' | DQS: ${String.format(Locale.US, "%.1f", dqsScore)}/100 | Category: ${processOutcomeCategory.name} | Matrix: ${matrixClassification.name}")

        return record
    }

    /**
     * Evaluates decision quality directly from a DecisionContext and counterfactual/realized outcome.
     */
    fun evaluateDecisionContext(
        context: DecisionContext,
        pnlUSD: Double,
        counterfactualOutcome: CounterfactualOutcome? = null
    ): DecisionEvaluationRecord {
        val isApproved = context.isExecutionAuthorized

        val dimensions = QualityDimensionScores(
            signalQuality = context.calibratedConfidence.coerceIn(0.0, 100.0),
            strategyQuality = context.strategyQuality.coerceIn(0.0, 100.0),
            mtfQuality = context.weightedMtfScore.coerceIn(0.0, 100.0),
            liquidityQuality = context.volumeLiquidityScore.coerceIn(0.0, 100.0),
            metaReasoningQuality = context.metaConviction.coerceIn(0.0, 100.0),
            riskSizingQuality = (100.0 - (context.correlationExposure * 100.0)).coerceIn(0.0, 100.0),
            portfolioDecisionQuality = (context.capitalEligibilityScore * 100.0).coerceIn(0.0, 100.0),
            executionQuality = 85.0
        )

        val primarySubsystem = if (context.rejectionAuthority != DecisionAuthority.NONE) {
            context.rejectionAuthority.name
        } else {
            "PortfolioExecutionEngine"
        }

        val marketRegime = context.marketRegime ?: "RANGE"
        return evaluateAndRecordDecision(
            decisionId = context.id,
            symbol = context.symbol,
            isApproved = isApproved,
            primarySubsystem = primarySubsystem,
            qualityDimensions = dimensions,
            pnlUSD = pnlUSD,
            counterfactualOutcome = counterfactualOutcome,
            marketRegime = marketRegime
        )
    }

    /**
     * Calculates subsystem statistics with minimum evidence constraints and uncertainty margins.
     */
    fun calculateSubsystemStatistics(subsystemName: String? = null): List<SubsystemGuardStatistics> {
        val records = synchronized(decisionLedger) { decisionLedger.toList() }
        val filtered = if (subsystemName != null) {
            records.filter { it.primaryGuardOrSubsystem.equals(subsystemName, ignoreCase = true) }
        } else {
            records
        }

        val grouped = filtered.groupBy { it.primaryGuardOrSubsystem }

        return grouped.map { (subsys, list) ->
            val total = list.size

            val appWon = list.count { it.matrixClassification == DecisionMatrixClassification.APPROVED_WON }
            val appLost = list.count { it.matrixClassification == DecisionMatrixClassification.APPROVED_LOST }
            val rejWouldWin = list.count { it.matrixClassification == DecisionMatrixClassification.REJECTED_WOULD_HAVE_WON }
            val rejWouldLose = list.count { it.matrixClassification == DecisionMatrixClassification.REJECTED_WOULD_HAVE_LOST }

            val totalApproved = appWon + appLost
            val totalRejected = rejWouldWin + rejWouldLose

            val precision = if (totalRejected > 0) rejWouldLose.toDouble() / totalRejected else 0.0
            val falseRejectionRate = if (totalRejected > 0) rejWouldWin.toDouble() / totalRejected else 0.0
            val falseAcceptanceRate = if (totalApproved > 0) appLost.toDouble() / totalApproved else 0.0

            val avoidedLossUSD = list.filter { it.matrixClassification == DecisionMatrixClassification.REJECTED_WOULD_HAVE_LOST }
                .sumOf { abs(it.pnlUSD) }
            val missedProfitUSD = list.filter { it.matrixClassification == DecisionMatrixClassification.REJECTED_WOULD_HAVE_WON }
                .sumOf { abs(it.pnlUSD) }

            val counterfactualValueUSD = avoidedLossUSD - missedProfitUSD

            val approvedNetPnL = list.filter { it.isApproved }.sumOf { it.pnlUSD }
            val expectedValueContributionUSD = approvedNetPnL + counterfactualValueUSD

            val isSufficientEvidence = total >= MIN_EVIDENCE_SAMPLE_SIZE
            val uncertaintyMargin = 1.96 / sqrt(max(1.0, total.toDouble()))

            val readinessMessage = if (isSufficientEvidence) {
                "✅ ADAPTATION READY: Sample size N=$total >= $MIN_EVIDENCE_SAMPLE_SIZE threshold. Subsystem parameters may adapt based on evidence."
            } else {
                "🔒 ADAPTATION BLOCKED: Insufficient sample size N=$total < $MIN_EVIDENCE_SAMPLE_SIZE. Guard adaptation requires more evidence to prevent overfitting to noise."
            }

            SubsystemGuardStatistics(
                subsystemName = subsys,
                totalDecisions = total,
                sampleSize = total,
                minRequiredSampleSize = MIN_EVIDENCE_SAMPLE_SIZE,
                isEvidenceSufficientForAdaptation = isSufficientEvidence,
                approvedWonCount = appWon,
                approvedLostCount = appLost,
                rejectedWouldHaveWonCount = rejWouldWin,
                rejectedWouldHaveLostCount = rejWouldLose,
                precision = precision,
                falseRejectionRate = falseRejectionRate,
                falseAcceptanceRate = falseAcceptanceRate,
                expectedValueContributionUSD = expectedValueContributionUSD,
                counterfactualValueUSD = counterfactualValueUSD,
                uncertaintyMargin = uncertaintyMargin,
                adaptationReadinessMessage = readinessMessage
            )
        }
    }

    /**
     * Checks whether a subsystem is allowed to adapt its threshold based on evidence.
     */
    fun canSubsystemAdapt(subsystemName: String): Boolean {
        val statsList = calculateSubsystemStatistics(subsystemName)
        if (statsList.isEmpty()) return false
        return statsList.first().isEvidenceSufficientForAdaptation
    }

    private fun logAudit(message: String) {
        synchronized(auditLogs) {
            auditLogs.add("[${System.currentTimeMillis()}] $message")
            if (auditLogs.size > 500) auditLogs.removeAt(0)
        }
    }

    fun getAuditLogs(): List<String> {
        return synchronized(auditLogs) { auditLogs.toList() }
    }

    fun clearLedger() {
        synchronized(decisionLedger) {
            decisionLedger.clear()
        }
        synchronized(auditLogs) {
            auditLogs.clear()
        }
    }
}
