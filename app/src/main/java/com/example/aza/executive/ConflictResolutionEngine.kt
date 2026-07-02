package com.example.aza.executive

import com.example.data.database.KnowledgeEntry
import com.example.data.repository.AppRepository
import java.util.Locale
import kotlin.math.abs

class ConflictResolutionEngine(private val repository: AppRepository) {

    // Dynamic credibility ratings adjusted by feedback loop (defaults)
    var researchAgentCredibility = 0.85
    var riskAgentCredibility = 0.92
    var tradingAgentCredibility = 0.80
    var laptopAgentCredibility = 0.88
    var userFeedbackCredibility = 1.00 // Highest credibility

    data class ConflictReport(
        val isContradictory: Boolean,
        val description: String,
        val item1Score: Double,
        val item2Score: Double,
        val resolutionAction: String, // "MERGE", "OVERWRITE_WITH_DOMINANT", "ESCALATE_AMBIGUITY"
        val winnerTitle: String? = null
    )

    /**
     * Conducts a detailed conflict scan over the Knowledge Base.
     * Weighs credibility, computes sentiment scores, resolves the conflict, and logs the result.
     */
    suspend fun resolveKnowledgeConflicts(): String {
        val entries = repository.getAllKnowledgeDirect()
        val resolvedLogs = mutableListOf<String>()

        if (entries.size < 2) {
            return "Insufficient memory entries to execute conflict resolution sweep."
        }

        val processedPairs = mutableSetOf<String>()

        for (i in entries.indices) {
            for (j in entries.indices) {
                if (i == j) continue
                val e1 = entries[i]
                val e2 = entries[j]
                
                // Ensure unique pairs
                val pairKey = if (e1.id < e2.id) "${e1.id}-${e2.id}" else "${e2.id}-${e1.id}"
                if (processedPairs.contains(pairKey)) continue
                processedPairs.add(pairKey)

                // Skip already resolved/archived notifications to prevent infinite loops
                if (e1.category == "Conflict Resolution" || e2.category == "Conflict Resolution") continue

                // Check for overlapping theme words in titles
                val titleWords1 = e1.title.lowercase(Locale.US).split(" ").filter { it.length > 4 }.toSet()
                val titleWords2 = e2.title.lowercase(Locale.US).split(" ").filter { it.length > 4 }.toSet()
                val overlapping = titleWords1.intersect(titleWords2)

                if (overlapping.size >= 2 && e1.category == e2.category) {
                    val report = evaluateConflict(e1, e2)
                    if (report.isContradictory) {
                        resolvedLogs.add("Resolved conflict between [ID ${e1.id}] '${e1.title}' and [ID ${e2.id}] '${e2.title}' via ${report.resolutionAction}.")
                        
                        // Perform resolution logic in memory
                        when (report.resolutionAction) {
                            "OVERWRITE_WITH_DOMINANT" -> {
                                val winner = if (report.winnerTitle == e1.title) e1 else e2
                                val loser = if (report.winnerTitle == e1.title) e2 else e1
                                
                                // Lower loser's confidence score and update winner with confirmation count
                                repository.updateKnowledge(winner.copy(
                                    confidenceScore = (winner.confidenceScore + 0.05).coerceAtMost(1.0),
                                    timesConfirmed = winner.timesConfirmed + 1,
                                    evidence = "${winner.evidence} (REINFORCED: Resolved conflict with ID ${loser.id} based on superior source credibility of ${String.format(Locale.US, "%.1f", maxOf(report.item1Score, report.item2Score) * 100.0)}%)"
                                ))
                                repository.updateKnowledge(loser.copy(
                                    confidenceScore = (loser.confidenceScore - 0.25).coerceAtLeast(0.10),
                                    isConfirmed = false,
                                    evidence = "${loser.evidence} (DEPRECATED: Overruled by higher credibility memory ID ${winner.id})"
                                ))
                            }
                            "MERGE" -> {
                                // Dynamic merge
                                repository.updateKnowledge(e1.copy(
                                    title = "Synthesized: ${e1.title}",
                                    confidenceScore = ((e1.confidenceScore + e2.confidenceScore) / 2.0).coerceIn(0.1, 1.0),
                                    timesConfirmed = e1.timesConfirmed + e2.timesConfirmed,
                                    description = "${e1.description} | Synthesized refinement: ${e2.description}",
                                    evidence = "Merged harmoniously due to matching credibility levels. Probabilistic variance: ${String.format(Locale.US, "%.3f", abs(report.item1Score - report.item2Score))}"
                                ))
                                // Safe deprecation of redundant second entry
                                repository.updateKnowledge(e2.copy(
                                    confidenceScore = 0.1,
                                    isConfirmed = false,
                                    category = "Archived Memory",
                                    evidence = "Redundant copy merged into consolidated entry ID ${e1.id}"
                                ))
                            }
                            "ESCALATE_AMBIGUITY" -> {
                                // Flag both with high warning tags
                                repository.updateKnowledge(e1.copy(
                                    confidenceScore = (e1.confidenceScore - 0.10).coerceAtLeast(0.2),
                                    evidence = "${e1.evidence} (AMBIGUITY LOCK: Conflict difference < 5%. Escalated to user for review.)"
                                ))
                                repository.updateKnowledge(e2.copy(
                                    confidenceScore = (e2.confidenceScore - 0.10).coerceAtLeast(0.2),
                                    evidence = "${e2.evidence} (AMBIGUITY LOCK: Conflict difference < 5%. Escalated to user for review.)"
                                ))
                            }
                        }

                        // Create formal resolution transparency log
                        repository.insertKnowledge(
                            KnowledgeEntry(
                                title = "Conflict Resolution: '${e1.title}' vs '${e2.title}'",
                                category = "Conflict Resolution",
                                description = report.description,
                                evidence = "Conflict Type: ${report.resolutionAction} | Credibility Scores: E1=${String.format(Locale.US, "%.2f", report.item1Score)}, E2=${String.format(Locale.US, "%.2f", report.item2Score)}",
                                confidenceScore = maxOf(report.item1Score, report.item2Score),
                                timesConfirmed = 1,
                                isConfirmed = true
                            )
                        )
                    }
                }
            }
        }

        return if (resolvedLogs.isEmpty()) {
            "Conflict resolution sweep completed. No conflicting information detected."
        } else {
            "Conflict resolution sweep completed successfully:\n" + resolvedLogs.joinToString("\n")
        }
    }

    /**
     * Inspects two knowledge entries, calculates simulated sentiment scores,
     * factors source credibility, and formulates a probabilistic resolution strategy.
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
        // Positive/Cautionary/Risk words affect how heavily we prioritize the recommendation
        val sentimentE1 = calculateSentimentScore(desc1Lower)
        val sentimentE2 = calculateSentimentScore(desc2Lower)

        // 3. Source Credibility Weighting
        val sourceWeightE1 = getSourceCredibility(e1.category)
        val sourceWeightE2 = getSourceCredibility(e2.category)

        // 4. Probabilistic Scoring
        // Composite Score = Base Confidence * Source Credibility * (1.0 + Sentiment Influence)
        val compositeScoreE1 = e1.confidenceScore * sourceWeightE1 * (1.0 + abs(sentimentE1) * 0.1)
        val compositeScoreE2 = e2.confidenceScore * sourceWeightE2 * (1.0 + abs(sentimentE2) * 0.1)

        val difference = abs(compositeScoreE1 - compositeScoreE2)

        return when {
            // Highly similar scores (difference < 0.05) cannot be cleanly resolved, so we escalate
            difference < 0.05 -> {
                ConflictReport(
                    isContradictory = true,
                    description = "Escalated to human supervisor. Ambiguous data lock with minimal confidence separation (Diff: ${String.format(Locale.US, "%.3f", difference)}). Entry 1 (Score: ${String.format(Locale.US, "%.2f", compositeScoreE1)}) and Entry 2 (Score: ${String.format(Locale.US, "%.2f", compositeScoreE2)}) have conflicting directives.",
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

    /**
     * Feedback Loop integration. Adjusts agent credibility weights dynamically based on user feedback.
     */
    fun adjustCredibilityBasedOnFeedback(category: String, rating: String) {
        val adjustment = when (rating) {
            "Excellent" -> 0.04
            "Helpful" -> 0.01
            "Not Helpful" -> -0.03
            "Incorrect" -> -0.08
            else -> 0.0
        }

        when {
            category.contains("Risk") -> riskAgentCredibility = (riskAgentCredibility + adjustment).coerceIn(0.5, 1.1)
            category.contains("Laptop") -> laptopAgentCredibility = (laptopAgentCredibility + adjustment).coerceIn(0.5, 1.1)
            category.contains("Pattern") -> researchAgentCredibility = (researchAgentCredibility + adjustment).coerceIn(0.5, 1.1)
            category.contains("Strategy") -> tradingAgentCredibility = (tradingAgentCredibility + adjustment).coerceIn(0.5, 1.1)
        }
    }
}
