package com.example.aza.executive

import com.example.data.database.KnowledgeEntry
import com.example.data.database.AzaMission
import com.example.data.repository.AppRepository
import java.util.Locale

class MemoryEvolutionEngine(private val repository: AppRepository) {

    /**
     * Periodically evolves the Knowledge Hub by performing rule promotion,
     * performance-based confidence decay, contradiction checks, and knowledge merging.
     */
    suspend fun evolveMemory(onLog: (String) -> Unit): String {
        val logs = mutableListOf<String>()
        val entries = repository.getAllKnowledgeDirect()
        val strategies = repository.getAllStrategiesDirect()
        val evolvedEntries = mutableListOf<KnowledgeEntry>()

        if (entries.isEmpty()) {
            return "Knowledge Hub is currently empty. No memory evolution processes required."
        }

        // 1. Permanent Rules Promotion
        // Promote frequently successful lessons (timesConfirmed >= 3) to "Permanent Guideline" status
        for (entry in entries) {
            var updated = entry
            if (entry.timesConfirmed >= 3 && !entry.category.startsWith("Rule: ") && entry.confidenceScore >= 0.85) {
                val newCategory = "Rule: ${entry.category}"
                updated = entry.copy(
                    category = newCategory,
                    confidenceScore = 1.0, // perfect confidence for proven rules
                    evidence = "${entry.evidence} (PROMOTED TO PERMANENT RULE AFTER MULTIPLE VERIFICATIONS)"
                )
                logs.add("🧠 MEMORY EVOLUTION: Promoted lesson '${entry.title}' to a permanent system rule due to high replication success.")
            }

            // 2. Gradual & Performance-Based Confidence Decay
            // Instead of treating validated knowledge as permanent, we continuously re-evaluate.
            // A hypothesis confidence decays if related trading strategy prediction accuracy drops.
            val titleLower = entry.title.lowercase(Locale.US)
            val descLower = entry.description.lowercase(Locale.US)
            
            // Try to map to an active strategy to check prediction accuracy
            val matchingStrategy = strategies.find { strategy ->
                val stratLower = strategy.strategyName.lowercase(Locale.US)
                titleLower.contains(stratLower) || 
                descLower.contains(stratLower) ||
                (titleLower.contains("rsi") && stratLower.contains("mean reversion")) ||
                (titleLower.contains("ema") && stratLower.contains("trend following"))
            }

            if (matchingStrategy != null) {
                val winRate = matchingStrategy.paperWinRate
                val totalTrades = matchingStrategy.totalPaperTrades + matchingStrategy.totalTrades
                
                // If we have enough sample trades (e.g. > 0) and the win rate is declining or low
                if (totalTrades > 0 && winRate < 60.0) {
                    // Decay factor: larger drop for worse performance
                    val decayDelta = when {
                        winRate < 40.0 -> 0.08
                        winRate < 50.0 -> 0.05
                        else -> 0.03
                    }
                    val decayedScore = (entry.confidenceScore - decayDelta).coerceAtLeast(0.10)
                    
                    if (decayedScore != entry.confidenceScore) {
                        updated = updated.copy(
                            confidenceScore = decayedScore,
                            evidence = "${updated.evidence} (Confidence decayed from ${String.format(Locale.US, "%.0f%%", entry.confidenceScore * 100.0)} to ${String.format(Locale.US, "%.0f%%", decayedScore * 100.0)} due to dropping strategy win-rate: ${String.format(Locale.US, "%.1f%%", winRate)} across $totalTrades trades)"
                        )
                        logs.add("📉 PERFORMANCE DECAY: Decayed confidence of hypothesis '${entry.title}' to ${String.format(Locale.US, "%.1f", decayedScore * 100.0)}% due to declining win-rate (${String.format(Locale.US, "%.1f%%", winRate)}%).")
                        
                        // Check if confidence falls below threshold (75%)
                        // Lumina starts searching for alternative explanations!
                        if (decayedScore < 0.75 && entry.confidenceScore >= 0.75) {
                            logs.add("⚠️ CRITICAL THRESHOLD: Confidence in hypothesis '${entry.title}' fell below 75% (currently ${String.format(Locale.US, "%.1f%%", decayedScore * 100.0)}). Instigating automated search for alternative explanations.")
                            triggerAlternativeExplanationSearch(entry, matchingStrategy.strategyName)
                        }
                    }
                }
            } else {
                // Inactivity decay for old single-confirmed non-strategy entries
                val ageMs = System.currentTimeMillis() - entry.dateCreated
                if (ageMs > 120000 && entry.timesConfirmed == 1 && entry.category != "Rule: Strategy" && !entry.category.startsWith("Rule:")) {
                    val decayedScore = (entry.confidenceScore - 0.04).coerceAtLeast(0.10)
                    if (decayedScore != entry.confidenceScore) {
                        updated = updated.copy(
                            confidenceScore = decayedScore,
                            evidence = "${updated.evidence} (Confidence decayed slightly due to inactivity)"
                        )
                        logs.add("📉 INACTIVITY DECAY: Decayed confidence of inactive lesson '${entry.title}' to ${String.format(Locale.US, "%.1f", decayedScore * 100.0)}%.")
                    }
                }
            }
            
            if (updated != entry) {
                repository.updateKnowledge(updated)
            }
            evolvedEntries.add(updated)
        }

        // 3. Contradiction Detection & Suggested Merges
        val totalSize = evolvedEntries.size
        for (i in 0 until totalSize) {
            for (j in i + 1 until totalSize) {
                val e1 = evolvedEntries[i]
                val e2 = evolvedEntries[j]
                
                val titleWords = e1.title.split(" ").filter { it.length > 4 }.toSet()
                val otherWords = e2.title.split(" ").filter { it.length > 4 }.toSet()
                val overlapping = titleWords.intersect(otherWords)
                
                // If title is highly similar and category matches, but descriptions conflict
                if (overlapping.size >= 2 && e1.category == e2.category) {
                    val desc1 = e1.description.lowercase(Locale.US)
                    val desc2 = e2.description.lowercase(Locale.US)
                    
                    val isConflict = (desc1.contains("success") && desc2.contains("fail")) ||
                                     (desc1.contains("volatile") && desc2.contains("stable")) ||
                                     (desc1.contains("high") && desc2.contains("low"))
                    
                    if (isConflict) {
                        logs.add("⚠️ CONTRADICTION DETECTED: [ID ${e1.id}] '${e1.title}' conflicts with [ID ${e2.id}] '${e2.title}'. Recommendation generated to archive or merge them.")
                        if (!e1.evidence.contains("CONTRADICTION FLAG")) {
                            repository.updateKnowledge(e1.copy(evidence = "${e1.evidence} (CONTRADICTION FLAG: Conflicts with record ID ${e2.id})"))
                        }
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            return "Knowledge Hub memory evolved successfully. All entries match current context guidelines."
        }

        val summary = logs.joinToString("\n")
        onLog("🧠 MEMORY EVOLUTION REPORT:\n$summary")
        return summary
    }

    /**
     * Programmatically spawns a high-priority Research Mission to search for alternative explanations for the decayed hypothesis.
     */
    private suspend fun triggerAlternativeExplanationSearch(entry: KnowledgeEntry, strategyName: String) {
        val existingMissions = repository.getAllMissionsDirect()
        val missionTitle = "Alternative Explanation Search: ${entry.title.take(30)}"
        val exists = existingMissions.any { it.title == missionTitle && it.status == "Active" }
        
        if (!exists) {
            val mission = AzaMission(
                title = missionTitle,
                description = "Confidence in our hypothesis '${entry.title}' decayed below 75% due to declining trade prediction accuracy. Lumina is actively auditing the model and searching the web for alternative microstructure or indicator explanations to optimize our $strategyName parameters.",
                status = "Active",
                progress = 10f,
                assignedAgents = "ResearchAgent, RiskAgent",
                priority = "HIGH",
                currentTask = "Dispatching ResearchAgent to parse order flow shifts and indicators...",
                category = "Research",
                schedule = "One-Shot",
                successCriteria = "Identify at least two high-credibility microstructural or parameter calibrations that resolve the performance drawdowns."
            )
            repository.insertMission(mission)
        }
    }
}
