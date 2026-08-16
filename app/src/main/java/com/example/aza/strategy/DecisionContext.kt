package com.example.aza.strategy

import java.util.Locale

/**
 * Stages in the Lumina Decision Pipeline.
 */
enum class DecisionStage {
    SIGNAL_EVALUATED,
    STRATEGY_SUBSTITUTED,
    CONFIDENCE_CALIBRATED,
    CAPITAL_EVALUATED,
    EXECUTION_AUTHORIZED,
    REJECTED
}

/**
 * Authorities responsible for decision approvals or rejections.
 */
enum class DecisionAuthority {
    NONE,
    STRATEGY_ENGINE,
    MTF_GUARD,
    LIQUIDITY_GUARD,
    META_REASONING,
    PORTFOLIO_GUARD,
    EXECUTION_SAFETY
}

/**
 * Explicit multi-stage decision state.
 * Separates Signal Quality from Capital Eligibility and Execution Authorization.
 */
enum class OverallDecisionState {
    SIGNAL_REJECTED,
    SIGNAL_APPROVED,
    CAPITAL_REJECTED,
    CAPITAL_APPROVED,
    EXECUTION_REJECTED,
    EXECUTION_APPROVED
}

/**
 * Result of an individual guard evaluation.
 */
data class GuardCheckResult(
    val guardName: String,
    val isPassed: Boolean,
    val actualValue: Double,
    val thresholdValue: Double,
    val rationale: String,
    val isHardGuard: Boolean = true
)

/**
 * Individual recorded contribution to confidence scoring to prevent double-counting.
 */
data class ConfidenceContribution(
    val source: String,
    val rawContribution: Double,
    val boundedContribution: Double,
    val reason: String,
    val overlapsWithOtherSignal: Boolean = false
)

enum class MtfAlignmentStatus {
    STRONG_ALIGNMENT,
    ACCEPTABLE_ALIGNMENT,
    WEAK_ALIGNMENT,
    CONFLICT,
    INSUFFICIENT_DATA
}

data class TimeframeState(
    val timeframe: String, // "1D", "4H", "1H", "15m"
    val trendBias: String, // "BULLISH", "BEARISH", "NEUTRAL"
    val confidence: Double = 0.80,
    val isStale: Boolean = false,
    val isMissing: Boolean = false
)

data class MtfDiagnostics(
    val mtfRawScore: Double,
    val mtfWeightedScore: Double,
    val mtfRequiredScore: Double,
    val mtfStatus: MtfAlignmentStatus,
    val mtfConflictLevel: Double,
    val htfDirectionalAlignment: String, // "ALIGNED", "NEUTRAL", "OPPOSING"
    val isEarlyBreakoutLead: Boolean = false,
    val isHtfConflict: Boolean = false,
    val timeframeStates: List<TimeframeState> = emptyList(),
    val rationale: String = ""
)

enum class TradeActionIntent {
    NEW_POSITION,
    REDUCE_POSITION,
    HEDGE,
    REVERSAL,
    SCALE_IN
}

/**
 * Single immutable Decision Context carrying full state for a trade candidate.
 */
data class DecisionContext(
    val id: String = "DEC-${java.util.UUID.randomUUID().toString().take(8).uppercase(java.util.Locale.US)}",
    val timestamp: Long = System.currentTimeMillis(),
    val symbol: String,
    val side: String, // "BUY", "SELL", "HOLD"
    val originalStrategy: String,
    val effectiveStrategy: String,
    val wasSubstituted: Boolean = false,
    val substitutionReason: String = "",
    val substitutionScore: Double = 0.0,
    val whyOriginalRejected: String = "",
    val whyReplacementSelected: String = "",
    val recalculatedFields: List<String> = emptyList(),
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val riskRewardRatio: Double,
    val marketRegime: String,
    val volatilityState: String,
    val rawConfidence: Double,
    val adaptedConfidence: Double,
    val metaConviction: Double,
    val rawModelConfidence: Double = rawConfidence,
    val calibratedConfidence: Double = adaptedConfidence,
    val strategyQuality: Double = 75.0,
    val capitalEligibilityScore: Double = 0.80,
    val uncertaintyLevel: UncertaintyLevel = UncertaintyLevel.HIGH,
    val strategyStateEnum: StrategyState = StrategyState.ACTIVE,
    val mtfScore: Double,
    val weightedMtfScore: Double,
    val mtfThreshold: Double,
    val volumeLiquidityScore: Double,
    val volumeLiquidityThreshold: Double,
    val strategyExpectancyR: Double,
    val strategyWinRatePct: Double,
    val sampleSize: Int,
    val strategyState: String, // "Preliminary Observation", "Active Champion", "Quarantined"
    val riskAllocationPct: Double,
    val portfolioExposureUSD: Double,
    val correlationExposure: Double,
    val actionIntent: TradeActionIntent = TradeActionIntent.NEW_POSITION,
    val capitalRejectionReason: String = "",
    val netExposureUSD: Double = portfolioExposureUSD,
    val grossExposureUSD: Double = portfolioExposureUSD,
    val portfolioVolatilityPct: Double = 12.0,
    val symbolConcentrationPct: Double = 0.15,
    val portfolioGuardAuditLog: String = "",
    val riskWaterfallResult: RiskWaterfallResult? = null,
    val mtfDiagnostics: MtfDiagnostics? = null,
    val guardResults: List<GuardCheckResult> = emptyList(),
    val confidenceContributions: List<ConfidenceContribution> = emptyList(),
    val decisionState: OverallDecisionState = OverallDecisionState.SIGNAL_REJECTED,
    val rejectionAuthority: DecisionAuthority = DecisionAuthority.NONE,
    val rejectionReason: String = "",
    val pipelineVersion: String = "LUMINA-DECISION-ENGINE-V2.1"
) {
    val isSignalApproved: Boolean
        get() = isSignalValidated

    val isCapitalApproved: Boolean
        get() = isCapitalEligible

    val isExecutionApproved: Boolean
        get() = isExecutionAuthorized

    val isSignalValidated: Boolean
        get() = decisionState == OverallDecisionState.SIGNAL_APPROVED ||
                decisionState == OverallDecisionState.CAPITAL_REJECTED ||
                decisionState == OverallDecisionState.CAPITAL_APPROVED ||
                decisionState == OverallDecisionState.EXECUTION_REJECTED ||
                decisionState == OverallDecisionState.EXECUTION_APPROVED

    val isCapitalEligible: Boolean
        get() = decisionState == OverallDecisionState.CAPITAL_APPROVED ||
                decisionState == OverallDecisionState.EXECUTION_APPROVED

    val isExecutionAuthorized: Boolean
        get() = decisionState == OverallDecisionState.EXECUTION_APPROVED

    val signalValidated: String
        get() = if (isSignalValidated) "YES" else "NO"

    val capitalEligible: String
        get() = if (isCapitalEligible) "YES" else "NO"

    val executionAuthorized: String
        get() = if (isExecutionAuthorized) "YES" else "NO"

    val hasFailedHardGuard: Boolean
        get() = guardResults.any { it.isHardGuard && !it.isPassed }
}

/**
 * Centralized, mathematically rigorous guard evaluators.
 * Guarantees actualValue >= thresholdValue -> PASS and actualValue < thresholdValue -> FAIL.
 */
object GuardEvaluator {
    fun checkMinThreshold(
        guardName: String,
        actual: Double,
        threshold: Double,
        isHardGuard: Boolean = true,
        unit: String = "%"
    ): GuardCheckResult {
        val pass = actual >= threshold
        val formatStr = if (unit == "%") "%.1f%%" else "%.2f"
        val actualFmt = String.format(Locale.US, formatStr, actual)
        val threshFmt = String.format(Locale.US, formatStr, threshold)
        val rationale = if (pass) {
            "✅ $guardName PASSED: Confluence valid ($actualFmt >= $threshFmt)"
        } else {
            "⛔ $guardName FAILED: Insufficient confluence ($actualFmt < $threshFmt)"
        }
        return GuardCheckResult(
            guardName = guardName,
            isPassed = pass,
            actualValue = actual,
            thresholdValue = threshold,
            rationale = rationale,
            isHardGuard = isHardGuard
        )
    }

    fun checkMaxThreshold(
        guardName: String,
        actual: Double,
        maxThreshold: Double,
        isHardGuard: Boolean = true,
        unit: String = "%"
    ): GuardCheckResult {
        val pass = actual <= maxThreshold
        val formatStr = if (unit == "%") "%.1f%%" else "%.2f"
        val actualFmt = String.format(Locale.US, formatStr, actual)
        val threshFmt = String.format(Locale.US, formatStr, maxThreshold)
        val rationale = if (pass) {
            "✅ $guardName PASSED: Risk within safe limits ($actualFmt <= $threshFmt)"
        } else {
            "⛔ $guardName FAILED: Risk exceeds safe limits ($actualFmt > $threshFmt)"
        }
        return GuardCheckResult(
            guardName = guardName,
            isPassed = pass,
            actualValue = actual,
            thresholdValue = maxThreshold,
            rationale = rationale,
            isHardGuard = isHardGuard
        )
    }
}

/**
 * Institutional Multi-Timeframe (MTF) Alignment Evaluator.
 * Distinguishes raw timeframe agreement, weighted agreement, HTF alignment,
 * HTF conflicts, early breakout lead exceptions, and missing/stale data.
 */
object MtfEvaluator {

    fun evaluateMtfDiagnostics(
        side: String,
        timeframeStates: List<TimeframeState>,
        requiredScore: Double = 66.0
    ): MtfDiagnostics {
        if (timeframeStates.isEmpty()) {
            return MtfDiagnostics(
                mtfRawScore = 0.0,
                mtfWeightedScore = 0.0,
                mtfRequiredScore = requiredScore,
                mtfStatus = MtfAlignmentStatus.INSUFFICIENT_DATA,
                mtfConflictLevel = 100.0,
                htfDirectionalAlignment = "NEUTRAL",
                rationale = "⛔ MTF INSUFFICIENT DATA: No timeframe data provided."
            )
        }

        val missingOrStaleCount = timeframeStates.count { it.isMissing || it.isStale }
        val htfStates = timeframeStates.filter { it.timeframe.equals("1D", ignoreCase = true) || it.timeframe.equals("4H", ignoreCase = true) }
        val htfMissingOrStale = htfStates.any { it.isMissing || it.isStale }

        if (htfMissingOrStale || missingOrStaleCount >= 2) {
            return MtfDiagnostics(
                mtfRawScore = 0.0,
                mtfWeightedScore = 0.0,
                mtfRequiredScore = requiredScore,
                mtfStatus = MtfAlignmentStatus.INSUFFICIENT_DATA,
                mtfConflictLevel = 100.0,
                htfDirectionalAlignment = "NEUTRAL",
                timeframeStates = timeframeStates,
                rationale = "⛔ MTF INSUFFICIENT DATA: Critical higher timeframe data is missing or stale."
            )
        }

        val expectedBias = if (side.equals("BUY", ignoreCase = true)) "BULLISH" else "BEARISH"
        val oppositeBias = if (side.equals("BUY", ignoreCase = true)) "BEARISH" else "BULLISH"

        var agreeingCount = 0
        var totalNonMissing = 0

        var weightedSum = 0.0
        var totalWeight = 0.0

        val has1D = timeframeStates.any { it.timeframe.equals("1D", ignoreCase = true) }
        val has4H = timeframeStates.any { it.timeframe.equals("4H", ignoreCase = true) }

        timeframeStates.forEach { tfState ->
            if (!tfState.isMissing && !tfState.isStale) {
                totalNonMissing++

                val weight = when (tfState.timeframe.uppercase(Locale.US)) {
                    "1D" -> if (has4H) 0.25 else 0.50
                    "4H" -> if (has1D) 0.25 else 0.50
                    "1H" -> 0.30
                    "15M" -> 0.20
                    else -> 0.20
                }

                val agreementFactor = when {
                    tfState.trendBias.equals(expectedBias, ignoreCase = true) -> 1.0
                    tfState.trendBias.equals("NEUTRAL", ignoreCase = true) -> 0.50
                    else -> 0.0
                }

                if (tfState.trendBias.equals(expectedBias, ignoreCase = true)) {
                    agreeingCount++
                }

                weightedSum += weight * agreementFactor * tfState.confidence
                totalWeight += weight * tfState.confidence
            }
        }

        val rawScore = if (totalNonMissing > 0) (agreeingCount.toDouble() / totalNonMissing.toDouble()) * 100.0 else 0.0
        val weightedScore = if (totalWeight > 0.0) (weightedSum / totalWeight) * 100.0 else 0.0

        // Higher Timeframe (HTF) Alignment Analysis
        var isHtfConflict = false
        val validHtfStates = htfStates.filter { !it.isMissing && !it.isStale }
        val htfOpposing = validHtfStates.any { it.trendBias.equals(oppositeBias, ignoreCase = true) && it.confidence >= 0.55 }
        val htfMatching = validHtfStates.isNotEmpty() && validHtfStates.all { it.trendBias.equals(expectedBias, ignoreCase = true) }

        val htfAlignment = when {
            htfOpposing -> {
                isHtfConflict = true
                "OPPOSING"
            }
            htfMatching -> "ALIGNED"
            else -> "NEUTRAL"
        }

        // Check Early Breakout Lead Exception
        // Lower timeframes (15m, 1H) leading a breakout while HTF is neutral (not opposing)
        val ltfState = timeframeStates.find { it.timeframe.equals("15m", ignoreCase = true) }
        val mtfState = timeframeStates.find { it.timeframe.equals("1H", ignoreCase = true) }
        val ltfMtfLeadingBreakout = ltfState != null && mtfState != null &&
                ltfState.trendBias.equals(expectedBias, ignoreCase = true) &&
                mtfState.trendBias.equals(expectedBias, ignoreCase = true) &&
                ltfState.confidence >= 0.75 && mtfState.confidence >= 0.75

        val isEarlyBreakoutLead = ltfMtfLeadingBreakout && htfAlignment == "NEUTRAL"

        var conflictLevel = ((1.0 - (weightedScore / 100.0)) * 100.0).coerceIn(0.0, 100.0)
        if (isHtfConflict) {
            conflictLevel = kotlin.math.max(conflictLevel, 75.0)
        }

        val status = when {
            isHtfConflict || weightedScore < 40.0 -> MtfAlignmentStatus.CONFLICT
            weightedScore >= 80.0 -> MtfAlignmentStatus.STRONG_ALIGNMENT
            weightedScore >= requiredScore || (isEarlyBreakoutLead && weightedScore >= 50.0) -> MtfAlignmentStatus.ACCEPTABLE_ALIGNMENT
            else -> MtfAlignmentStatus.WEAK_ALIGNMENT
        }

        val rationale = buildString {
            when (status) {
                MtfAlignmentStatus.STRONG_ALIGNMENT -> append("✅ STRONG MTF ALIGNMENT: Signal aligned across timeframes (Weighted ${String.format(Locale.US, "%.1f%%", weightedScore)} >= ${String.format(Locale.US, "%.1f%%", requiredScore)}).")
                MtfAlignmentStatus.ACCEPTABLE_ALIGNMENT -> {
                    if (isEarlyBreakoutLead) {
                        append("⚡ EARLY BREAKOUT LEAD: LTF/MTF momentum leading breakout (Weighted ${String.format(Locale.US, "%.1f%%", weightedScore)}). HTF is neutral.")
                    } else {
                        append("✅ ACCEPTABLE MTF ALIGNMENT: Confluence valid (Weighted ${String.format(Locale.US, "%.1f%%", weightedScore)} >= ${String.format(Locale.US, "%.1f%%", requiredScore)}).")
                    }
                }
                MtfAlignmentStatus.WEAK_ALIGNMENT -> append("⚠️ WEAK MTF ALIGNMENT: Weighted agreement (${String.format(Locale.US, "%.1f%%", weightedScore)}) is below required threshold (${String.format(Locale.US, "%.1f%%", requiredScore)}).")
                MtfAlignmentStatus.CONFLICT -> {
                    if (isHtfConflict) {
                        append("⛔ MTF MACRO CONFLICT: Macro higher-timeframe directly contradicts signal side ($oppositeBias vs $expectedBias). Signal quarantined.")
                    } else {
                        append("⛔ MTF SEVERE CONFLICT: Multi-timeframe contradiction detected across timeframes (Weighted ${String.format(Locale.US, "%.1f%%", weightedScore)}).")
                    }
                }
                MtfAlignmentStatus.INSUFFICIENT_DATA -> append("⛔ MTF INSUFFICIENT DATA: Missing or stale timeframe data.")
            }
        }

        return MtfDiagnostics(
            mtfRawScore = rawScore,
            mtfWeightedScore = weightedScore,
            mtfRequiredScore = requiredScore,
            mtfStatus = status,
            mtfConflictLevel = conflictLevel,
            htfDirectionalAlignment = htfAlignment,
            isEarlyBreakoutLead = isEarlyBreakoutLead,
            isHtfConflict = isHtfConflict,
            timeframeStates = timeframeStates,
            rationale = rationale
        )
    }
}

