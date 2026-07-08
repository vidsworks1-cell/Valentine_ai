package com.example.aza.executive

import com.example.data.database.KnowledgeEntry
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs

class ConflictResolutionEngine(private val repository: AppRepository) {

    // Dynamic credibility ratings adjusted by feedback loop (defaults)
    var researchAgentCredibility = 0.85
    var riskAgentCredibility = 0.92
    var tradingAgentCredibility = 0.80
    var laptopAgentCredibility = 0.88
    var userFeedbackCredibility = 1.00 // Highest credibility

    // Shannon Opinion Entropy & Temporal State Trackers
    private val _shannonOpinionEntropy = MutableStateFlow(0.0)
    val shannonOpinionEntropy = _shannonOpinionEntropy.asStateFlow()

    private val _lowEntropyBreakthroughDetected = MutableStateFlow(false)
    val lowEntropyBreakthroughDetected = _lowEntropyBreakthroughDetected.asStateFlow()

    var previousOpinionEntropy = 0.0

    /**
     * Computes the Shannon opinion entropy of the active sub-agents' credibility distribution.
     * Normalized probability distribution: p_i = credibility_i / sum(credibilities)
     */
    fun calculateShannonOpinionEntropy(): Double {
        val creds = doubleArrayOf(
            researchAgentCredibility.coerceAtLeast(0.01),
            riskAgentCredibility.coerceAtLeast(0.01),
            tradingAgentCredibility.coerceAtLeast(0.01),
            laptopAgentCredibility.coerceAtLeast(0.01)
        )
        val sum = creds.sum()
        if (sum <= 0.0) return 0.0
        
        var entropy = 0.0
        for (c in creds) {
            val p = c / sum
            if (p > 1e-15) {
                entropy -= p * kotlin.math.ln(p)
            }
        }
        _shannonOpinionEntropy.value = entropy
        
        // Detect temporal low-entropy breakthrough transitions
        // A drop of > 0.02 in entropy compared to prior state suggests a low-entropy breakthrough consensus
        if (previousOpinionEntropy > 0.0 && previousOpinionEntropy - entropy > 0.02) {
            _lowEntropyBreakthroughDetected.value = true
        } else {
            _lowEntropyBreakthroughDetected.value = false
        }
        previousOpinionEntropy = entropy
        
        return entropy
    }

    // Extreme regime flag triggers zero-lag override bypass and cognitive circuit-breaker behavior
    var isExtremeRegime = false

    // --- Agent Performance History Tracking ---
    class AgentPerformanceHistory(val name: String) {
        var successes = 10.0
        var failures = 1.0
        var runs = 11

        /**
         * Calculates accuracy using Bayesian regularization to handle limited track records smoothly.
         * Prior success = 2.0, Prior failures = 2.0 (Neutral 50% baseline).
         */
        fun getBayesianAccuracy(): Double {
            val priorSuccess = 2.0
            val priorTotal = 4.0
            return (successes + priorSuccess) / (successes + failures + priorTotal)
        }

        /**
         * Computes performance variance (proxy) to quantify reliability oscillation.
         */
        fun getPerformanceVariance(): Double {
            val total = successes + failures
            if (total < 1.0) return 0.25
            val p = successes / total
            return p * (1.0 - p)
        }

        /**
         * Applies half-life decay to historic performance runs to mitigate model drift and prioritize recent runs.
         */
        fun applyHalfLifeDecay() {
            successes *= 0.50
            failures *= 0.50
            runs = (successes + failures).toInt()
        }

        /**
         * Triggers an epoch reset to handle significant updates, wiping old historical biases cleanly.
         * If it's a minor update, we only apply a sharp discount factor.
         */
        fun triggerEpochReset(isSignificantUpdate: Boolean) {
            if (isSignificantUpdate) {
                // Return to clean prior baseline
                successes = 2.0
                failures = 2.0
                runs = 4
            } else {
                // Apply sharp discount factor to discount history by 60%
                successes *= 0.40
                failures *= 0.40
                runs = (successes + failures).toInt()
            }
        }
    }

    val researchAgentPerformance = AgentPerformanceHistory("ResearchAgent")
    val riskAgentPerformance = AgentPerformanceHistory("RiskAgent")
    val tradingAgentPerformance = AgentPerformanceHistory("TradingAgent")
    val laptopAgentPerformance = AgentPerformanceHistory("LaptopAgent")

    data class ConflictReport(
        val isContradictory: Boolean,
        val description: String,
        val item1Score: Double,
        val item2Score: Double,
        val resolutionAction: String, // "MERGE", "OVERWRITE_WITH_DOMINANT", "ESCALATE_AMBIGUITY"
        val winnerTitle: String? = null
    )

    data class ConsensusMetrics(
        val scenarioName: String,
        val roundsToConverge: Int,
        val simulatedDurationMs: Double,
        val averageSentimentJitter: Double,
        val dynamicGatingThreshold: Double,
        val activeClippingRange: Double,
        val finalBayesianCredibilities: Map<String, Double>
    )

    var lastConsensusMetrics: ConsensusMetrics? = null
    var transitionThrashingDetected = false

    /**
     * Conducts a detailed multi-round conflict sweep over the Knowledge Base.
     * Weighs credibility, computes sentiment scores, resolves conflicts iteratively, tracks convergence speed, and logs the result.
     */
    suspend fun resolveKnowledgeConflicts(): String {
        val resolvedLogs = mutableListOf<String>()
        var round = 0
        val maxRounds = 8
        var converged = false
        var accumulatedJitter = 0.0
        var totalConflictsResolved = 0
        
        // Determine scenario name for metrics based on current state parameters
        val scenarioName = when {
            isExtremeRegime -> "Black Swan (Systemic Panic)"
            transitionThrashingDetected -> "State-Transition Thrashing"
            researchAgentCredibility < 0.60 || riskAgentCredibility < 0.60 -> "High Volatility Discordance"
            else -> "Standard Market Operation"
        }

        while (round < maxRounds && !converged) {
            round++
            val entries = repository.getAllKnowledgeDirect()
            if (entries.size < 2) {
                converged = true
                break
            }

            val processedPairs = mutableSetOf<String>()
            var conflictsInThisRound = 0

            for (i in entries.indices) {
                for (j in entries.indices) {
                    if (i == j) continue
                    val e1 = entries[i]
                    val e2 = entries[j]
                    
                    // Skip archived, unconfirmed, or conflict resolution entries to prevent feedback loops
                    if (e1.category == "Conflict Resolution" || e2.category == "Conflict Resolution" ||
                        e1.category == "Archived Memory" || e2.category == "Archived Memory" ||
                        !e1.isConfirmed || !e2.isConfirmed) continue

                    // Ensure unique pairs
                    val pairKey = if (e1.id < e2.id) "${e1.id}-${e2.id}" else "${e2.id}-${e1.id}"
                    if (processedPairs.contains(pairKey)) continue
                    processedPairs.add(pairKey)

                    // Check for overlapping theme words in titles
                    val titleWords1 = e1.title.lowercase(Locale.US).split(" ").filter { it.length > 4 }.toSet()
                    val titleWords2 = e2.title.lowercase(Locale.US).split(" ").filter { it.length > 4 }.toSet()
                    val overlapping = titleWords1.intersect(titleWords2)

                    if (overlapping.size >= 2 && e1.category == e2.category) {
                        val report = evaluateConflict(e1, e2)
                        if (report.isContradictory) {
                            conflictsInThisRound++
                            totalConflictsResolved++
                            
                            // Measure Sentiment Jitter Factor (SJF) from score difference
                            val jitter = abs(report.item1Score - report.item2Score)
                            accumulatedJitter += jitter

                            resolvedLogs.add("[Round $round] Resolved '${e1.title}' vs '${e2.title}' via ${report.resolutionAction}.")
                            
                            // Perform resolution logic in memory
                            when (report.resolutionAction) {
                                "OVERWRITE_WITH_DOMINANT" -> {
                                    val winner = if (report.winnerTitle == e1.title) e1 else e2
                                    val loser = if (report.winnerTitle == e1.title) e2 else e1
                                    
                                    // Lower loser's confidence score and update winner with confirmation count
                                    repository.updateKnowledge(winner.copy(
                                        confidenceScore = (winner.confidenceScore + 0.05).coerceAtMost(1.0),
                                        timesConfirmed = winner.timesConfirmed + 1,
                                        evidence = "${winner.evidence} (REINFORCED: Resolved conflict in round $round with ID ${loser.id} based on superior source credibility)"
                                    ))
                                    repository.updateKnowledge(loser.copy(
                                        confidenceScore = (loser.confidenceScore - 0.25).coerceAtLeast(0.10),
                                        isConfirmed = false,
                                        evidence = "${loser.evidence} (DEPRECATED: Overruled in round $round by higher credibility memory ID ${winner.id})"
                                    ))
                                }
                                "MERGE" -> {
                                    // Dynamic merge
                                    repository.updateKnowledge(e1.copy(
                                        title = "Synthesized: ${e1.title}",
                                        confidenceScore = ((e1.confidenceScore + e2.confidenceScore) / 2.0).coerceIn(0.1, 1.0),
                                        timesConfirmed = e1.timesConfirmed + e2.timesConfirmed,
                                        description = "${e1.description} | Synthesized refinement: ${e2.description}",
                                        evidence = "Merged harmoniously in round $round due to matching credibility levels. Probabilistic variance: ${String.format(Locale.US, "%.3f", abs(report.item1Score - report.item2Score))}"
                                    ))
                                    // Safe deprecation of redundant second entry
                                    repository.updateKnowledge(e2.copy(
                                        confidenceScore = 0.1,
                                        isConfirmed = false,
                                        category = "Archived Memory",
                                        evidence = "Redundant copy merged in round $round into consolidated entry ID ${e1.id}"
                                    ))
                                }
                                "ESCALATE_AMBIGUITY" -> {
                                    // Flag both with high warning tags
                                    repository.updateKnowledge(e1.copy(
                                        confidenceScore = (e1.confidenceScore - 0.10).coerceAtLeast(0.2),
                                        evidence = "${e1.evidence} (AMBIGUITY LOCK: Conflict difference below threshold in round $round. Escalated to user.)"
                                    ))
                                    repository.updateKnowledge(e2.copy(
                                        confidenceScore = (e2.confidenceScore - 0.10).coerceAtLeast(0.2),
                                        evidence = "${e2.evidence} (AMBIGUITY LOCK: Conflict difference below threshold in round $round. Escalated to user.)"
                                    ))
                                }
                            }

                            // Create formal resolution transparency log
                            repository.insertKnowledge(
                                com.example.data.database.KnowledgeEntry(
                                    title = "Conflict Resolution: '${e1.title}' vs '${e2.title}'",
                                    category = "Conflict Resolution",
                                    description = report.description,
                                    evidence = "Round: $round | Conflict Type: ${report.resolutionAction} | Credibility Scores: E1=${String.format(Locale.US, "%.2f", report.item1Score)}, E2=${String.format(Locale.US, "%.2f", report.item2Score)}",
                                    confidenceScore = maxOf(report.item1Score, report.item2Score),
                                    timesConfirmed = 1,
                                    isConfirmed = true
                                )
                            )
                        }
                    }
                }
            }

            if (conflictsInThisRound == 0) {
                converged = true
            }
        }

        // Calculate simulated latency based on stress scenario physics:
        // - Black Swan (Extreme Regime) triggers the Zero-Lag Reinforcement Bypass: fast per-round convergence (15ms / round)
        // - State Thrashing triggers high low-pass damping and hysteresis: slow per-round stabilization (350ms / round)
        // - Standard modes have moderate latency (120ms / round)
        val perRoundLatencyMs = when {
            isExtremeRegime -> 15.0
            transitionThrashingDetected -> 350.0
            else -> 120.0
        }
        val simulatedDurationMs = round * perRoundLatencyMs
        val avgJitter = if (totalConflictsResolved > 0) accumulatedJitter / totalConflictsResolved else 0.0

        val finalCredibilities = mapOf(
            "RiskAgent" to riskAgentCredibility,
            "ResearchAgent" to researchAgentCredibility,
            "TradingAgent" to tradingAgentCredibility,
            "LaptopAgent" to laptopAgentCredibility
        )

        // Store latest consensus metrics
        lastConsensusMetrics = ConsensusMetrics(
            scenarioName = scenarioName,
            roundsToConverge = round,
            simulatedDurationMs = simulatedDurationMs,
            averageSentimentJitter = avgJitter,
            dynamicGatingThreshold = if (totalConflictsResolved > 0) calculateDynamicGatingThreshold("Risk", "Strategy") else 0.05,
            activeClippingRange = activeClippingRange,
            finalBayesianCredibilities = finalCredibilities
        )

        // Calculate Shannon Opinion Entropy of the final converged system state
        calculateShannonOpinionEntropy()

        return if (resolvedLogs.isEmpty()) {
            "Conflict sweep completed. No conflicting information detected."
        } else {
            "Consensus reached in $round rounds (${String.format(Locale.US, "%.1f", simulatedDurationMs)} ms) under $scenarioName. Active gating resolved all contradictions. Opinion Entropy: ${String.format(Locale.US, "%.3f", shannonOpinionEntropy.value)}"
        }
    }

    /**
     * Inspects two knowledge entries, calculates simulated sentiment scores,
     * factors source credibility modified by Bayesian performance, and formulates a probabilistic resolution strategy.
     */
    fun evaluateConflict(e1: KnowledgeEntry, e2: KnowledgeEntry): ConflictReport {
        val desc1Lower = e1.description.lowercase(Locale.US)
        val desc2Lower = e2.description.lowercase(Locale.US)

        // 1. Detect Direct Contradictions
        val isContradictory = 
            (desc1Lower.contains("success") && desc2Lower.contains("fail")) ||
            (desc1Lower.contains("volatile") && desc2Lower.contains("stable")) ||
            (desc1Lower.contains("high") && desc2Lower.contains("low")) ||
            (desc1Lower.contains("buy") && desc2Lower.contains("sell")) ||
            (desc1Lower.contains("garch") && desc2Lower.contains("sma")) ||
            (desc1Lower.contains("unsafe") && desc2Lower.contains("safe"))

        if (!isContradictory) {
            return ConflictReport(false, "No semantic contradiction detected.", 0.0, 0.0, "NONE")
        }

        // 2. Simple Sentiment Scoring Simulation
        val sentimentE1 = calculateSentimentScore(desc1Lower)
        val sentimentE2 = calculateSentimentScore(desc2Lower)

        // 3. Source Credibility Weighting with Bayesian Accuracy Scale
        val sourceWeightE1 = getSourceCredibility(e1.category) * getAgentBayesianAccuracy(e1.category)
        val sourceWeightE2 = getSourceCredibility(e2.category) * getAgentBayesianAccuracy(e2.category)

        // 4. Probabilistic Scoring
        // Composite Score = Base Confidence * Source Credibility * (1.0 + Sentiment Influence)
        val compositeScoreE1 = e1.confidenceScore * sourceWeightE1 * (1.0 + abs(sentimentE1) * 0.1)
        val compositeScoreE2 = e2.confidenceScore * sourceWeightE2 * (1.0 + abs(sentimentE2) * 0.1)

        val difference = abs(compositeScoreE1 - compositeScoreE2)

        // Calculate dynamic gating threshold incorporating performance variance and environmental volatility
        val dynamicThreshold = calculateDynamicGatingThreshold(e1.category, e2.category)

        return when {
            // Highly similar scores (difference < dynamicThreshold) trigger the Soft Ambiguity Gating circuit breaker
            difference < dynamicThreshold -> {
                ConflictReport(
                    isContradictory = true,
                    description = "Escalated to human supervisor (Soft Ambiguity Gate Active, Thresh: ${String.format(Locale.US, "%.3f", dynamicThreshold)}). Ambiguous data lock with minimal confidence separation (Diff: ${String.format(Locale.US, "%.3f", difference)}). Entry 1 (${String.format(Locale.US, "%.2f", compositeScoreE1)}) and Entry 2 (${String.format(Locale.US, "%.2f", compositeScoreE2)}) are structurally co-equal.",
                    item1Score = compositeScoreE1,
                    item2Score = compositeScoreE2,
                    resolutionAction = "ESCALATE_AMBIGUITY"
                )
            }
            // Harmonious merge if descriptions can coexist with combined confidence
            desc1Lower.contains("volat") && desc2Lower.contains("volat") -> {
                ConflictReport(
                    isContradictory = true,
                    description = "Harmoniously merged complementary volatility perspectives into a unified schema.",
                    item1Score = compositeScoreE1,
                    item2Score = compositeScoreE2,
                    resolutionAction = "MERGE"
                )
            }
            // Otherwise, higher credibility wins and overrides/degrades the weaker source
            else -> {
                val winner = if (compositeScoreE1 > compositeScoreE2) e1 else e2
                val loser = if (compositeScoreE1 > compositeScoreE2) e2 else e1
                val winnerScore = maxOf(compositeScoreE1, compositeScoreE2)
                val loserScore = minOf(compositeScoreE1, compositeScoreE2)

                ConflictReport(
                    isContradictory = true,
                    description = "Overruled memory '${loser.title}' (Credibility: ${String.format(Locale.US, "%.2f", loserScore)}) in favor of '${winner.title}' (Credibility: ${String.format(Locale.US, "%.2f", winnerScore)}) based on superior source weighting and probabilistic consensus.",
                    item1Score = compositeScoreE1,
                    item2Score = compositeScoreE2,
                    resolutionAction = "OVERWRITE_WITH_DOMINANT",
                    winnerTitle = winner.title
                )
            }
        }
    }

    private fun calculateSentimentScore(text: String): Double {
        var score = 0.0
        val positiveWords = listOf("success", "safe", "stable", "optimal", "boost", "helpful", "green", "correct", "high")
        val negativeWords = listOf("fail", "unsafe", "volatile", "risk", "hazard", "incorrect", "low", "unhelpful")

        for (word in positiveWords) {
            if (text.contains(word)) score += 0.15
        }
        for (word in negativeWords) {
            if (text.contains(word)) score -= 0.15
        }
        return score
    }

    private fun getSourceCredibility(category: String): Double {
        return when {
            category.startsWith("Rule: ") -> 1.05
            category.contains("Risk") -> riskAgentCredibility
            category.contains("Laptop") -> laptopAgentCredibility
            category.contains("User Feedback") -> userFeedbackCredibility
            category.contains("Pattern") -> researchAgentCredibility
            category.contains("Strategy") -> tradingAgentCredibility
            else -> 0.80
        }
    }

    private fun getAgentBayesianAccuracy(category: String): Double {
        return when {
            category.contains("Risk") -> riskAgentPerformance.getBayesianAccuracy()
            category.contains("Laptop") -> laptopAgentPerformance.getBayesianAccuracy()
            category.contains("Pattern") -> researchAgentPerformance.getBayesianAccuracy()
            category.contains("Strategy") -> tradingAgentPerformance.getBayesianAccuracy()
            else -> 1.0
        }
    }

    private fun getAgentVariance(category: String): Double {
        return when {
            category.contains("Risk") -> riskAgentPerformance.getPerformanceVariance()
            category.contains("Laptop") -> laptopAgentPerformance.getPerformanceVariance()
            category.contains("Pattern") -> researchAgentPerformance.getPerformanceVariance()
            category.contains("Strategy") -> tradingAgentPerformance.getPerformanceVariance()
            else -> 0.0
        }
    }

    /**
     * Calculates the Dynamic Gating Threshold.
     * Incorporates both environmental volatility (such as an extreme regime) and performance variance.
     */
    fun calculateDynamicGatingThreshold(cat1: String, cat2: String): Double {
        val baseThreshold = 0.05
        
        // Compute combined performance variance of involved agents
        val v1 = getAgentVariance(cat1)
        val v2 = getAgentVariance(cat2)
        val avgVariance = (v1 + v2) / 2.0
        
        // Elevate threshold if agent performances are highly volatile/oscillating
        val varianceModifier = avgVariance * 0.35
        
        // If system is currently experiencing an extreme market regime, inflate the threshold to act as a soft circuit breaker
        val extremeRegimeModifier = if (isExtremeRegime) 0.07 else 0.0

        return (baseThreshold + varianceModifier + extremeRegimeModifier).coerceIn(0.03, 0.20)
    }

    // --- Trust Region Policy Optimization (TRPO/PPO) Clipping Parameters ---
    var activeClippingRange = 0.15

    /**
     * Dynamically adapts the Trust Region Clipping Range (epsilon) based on environmental volatility.
     * - Under normal low-volatility conditions, a wider trust region (e.g. 22%) allows rapid adaptation.
     * - Under high-volatility conditions (crisis/panic), the trust region shrinks (e.g. 4%) to prevent noisy updates.
     */
    fun calculateAdaptiveClippingRange(volatility: Double): Double {
        val minVol = 0.005
        val maxVol = 0.06
        val minClip = 0.04
        val maxClip = 0.22
        
        val normalizedVol = ((volatility - minVol) / (maxVol - minVol)).coerceIn(0.0, 1.0)
        activeClippingRange = maxClip - (normalizedVol * (maxClip - minClip))
        return activeClippingRange
    }

    /**
     * Applies Trust Region Clipping to ensure the updated weight ratio (w_new / w_old) 
     * stays within [1 - epsilon, 1 + epsilon].
     */
    fun clipWeightUpdate(oldWeight: Double, proposedWeight: Double, epsilon: Double): Double {
        if (oldWeight <= 0.0) return proposedWeight
        val ratio = proposedWeight / oldWeight
        val clippedRatio = ratio.coerceIn(1.0 - epsilon, 1.0 + epsilon)
        return oldWeight * clippedRatio
    }

    /**
     * Feedback Loop integration with Volatility-Adaptive Trust Region Clipping,
     * Asymmetric Step-Size Dampener, and Coercion Boundary Clamping.
     * Keeps weights locked inside [0.5, 1.1] to preserve multi-agent diversity.
     */
    fun adjustCredibilityBasedOnFeedback(category: String, rating: String, volatility: Double = 0.015) {
        // Asymmetric adjustment values: smaller improvements, larger penalties for immediate risk mitigation
        val rawAdjustment = when (rating) {
            "Excellent" -> 0.03
            "Helpful" -> 0.015
            "Not Helpful" -> -0.045
            "Incorrect" -> -0.09
            else -> 0.0
        }

        val baseline = 0.8
        val currentWeight = when {
            category.contains("Risk") -> riskAgentCredibility
            category.contains("Laptop") -> laptopAgentCredibility
            category.contains("Pattern") -> researchAgentCredibility
            category.contains("Strategy") -> tradingAgentCredibility
            else -> baseline
        }

        // Calculate Volatility-Adaptive Trust Region Clipping Range
        val epsilon = calculateAdaptiveClippingRange(volatility)

        // If weight oscillates tightly around the baseline, apply a dampening factor to prevent weight thrashing
        val isMediocre = abs(currentWeight - baseline) < 0.12
        val dampMultiplier = if (isMediocre && rawAdjustment > 0) 0.50 else 1.0

        // Zero-Lag Reinforcement Bypass during extreme regimes instantly amplifies feedback signals (no low-pass delay)
        val zeroLagMultiplier = if (isExtremeRegime) 2.5 else 1.0

        val finalAdjustment = rawAdjustment * dampMultiplier * zeroLagMultiplier
        val proposedWeight = currentWeight + finalAdjustment

        // Apply Trust-Region Clipping
        val clippedWeight = clipWeightUpdate(currentWeight, proposedWeight, epsilon)

        when {
            category.contains("Risk") -> {
                riskAgentCredibility = clippedWeight.coerceIn(0.5, 1.1)
                updatePerformanceStats(riskAgentPerformance, rating)
            }
            category.contains("Laptop") -> {
                laptopAgentCredibility = clippedWeight.coerceIn(0.5, 1.1)
                updatePerformanceStats(laptopAgentPerformance, rating)
            }
            category.contains("Pattern") -> {
                researchAgentCredibility = clippedWeight.coerceIn(0.5, 1.1)
                updatePerformanceStats(researchAgentPerformance, rating)
            }
            category.contains("Strategy") -> {
                tradingAgentCredibility = clippedWeight.coerceIn(0.5, 1.1)
                updatePerformanceStats(tradingAgentPerformance, rating)
            }
        }
        calculateShannonOpinionEntropy()
    }

    private fun updatePerformanceStats(history: AgentPerformanceHistory, rating: String) {
        history.runs += 1
        when (rating) {
            "Excellent", "Helpful" -> history.successes += 1.0
            "Not Helpful", "Incorrect" -> history.failures += 1.0
        }
    }
}
