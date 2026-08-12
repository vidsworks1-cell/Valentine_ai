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
                
                // Rule Demotion: If a permanent rule's strategy drops below 50% WR or has negative expectancy, strip "Rule:" status
                if (totalTrades >= 5 && winRate < 50.0 && updated.category.startsWith("Rule: ")) {
                    val demotedCategory = updated.category.removePrefix("Rule: ")
                    val demotedScore = (updated.confidenceScore - 0.20).coerceAtLeast(0.30)
                    updated = updated.copy(
                        category = demotedCategory,
                        confidenceScore = demotedScore,
                        evidence = "${updated.evidence} (DEMOTED FROM PERMANENT RULE due to strategy win-rate dropping to ${String.format(Locale.US, "%.1f%%", winRate)})"
                    )
                    logs.add("📉 RULE DEMOTED: Demoted permanent rule '${entry.title}' back to hypothesis due to poor win-rate (${String.format(Locale.US, "%.1f%%", winRate)}%).")
                }
                
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
                        // Resolve contradiction by decaying older entry e1
                        val olderEntry = if (e1.dateCreated <= e2.dateCreated) e1 else e2
                        val newerEntry = if (e1.dateCreated <= e2.dateCreated) e2 else e1
                        
                        val newScore = (olderEntry.confidenceScore - 0.20).coerceAtLeast(0.20)
                        val demotedCategory = if (newScore < 0.50 && olderEntry.category.startsWith("Rule: ")) olderEntry.category.removePrefix("Rule: ") else olderEntry.category
                        
                        val resolvedOlder = olderEntry.copy(
                            confidenceScore = newScore,
                            category = demotedCategory,
                            evidence = "${olderEntry.evidence} (CONTRADICTION RESOLVED: Decayed by 20% in favor of newer empirical rule ID ${newerEntry.id})"
                        )
                        repository.updateKnowledge(resolvedOlder)
                        logs.add("⚡ CONTRADICTION RESOLVED: Decayed older rule [ID ${olderEntry.id}] '${olderEntry.title}' (${String.format(Locale.US, "%.0f%%", newScore * 100.0)}) in favor of newer empirical rule [ID ${newerEntry.id}] '${newerEntry.title}'.")
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
