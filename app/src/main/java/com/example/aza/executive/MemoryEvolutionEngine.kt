package com.example.aza.executive

import com.example.data.database.KnowledgeEntry
import com.example.data.repository.AppRepository
import java.util.Locale

class MemoryEvolutionEngine(private val repository: AppRepository) {

    /**
     * Periodically evolves the Knowledge Hub by performing rule promotion,
     * confidence decay, contradiction checks, and knowledge merging.
     */
    suspend fun evolveMemory(onLog: (String) -> Unit): String {
        val logs = mutableListOf<String>()
        val entries = repository.getAllKnowledgeDirect()
        val evolvedEntries = mutableListOf<KnowledgeEntry>()

        if (entries.isEmpty()) {
            return "Knowledge Hub is currently empty. No memory evolution processes required."
        }

        // 1. Permanent Rules Promotion
        // Promote frequently successful lessons (timesConfirmed >= 3) to "Permanent Guideline" status
        for (entry in entries) {
            var updated = entry
            if (entry.timesConfirmed >= 3 && !entry.category.startsWith("Rule: ")) {
                val newCategory = "Rule: ${entry.category}"
                updated = entry.copy(
                    category = newCategory,
                    confidenceScore = 1.0, // perfect confidence for proven rules
                    evidence = "${entry.evidence} (PROMOTED TO PERMANENT RULE AFTER MULTIPLE VERIFICATIONS)"
                )
                logs.add("🧠 MEMORY EVOLUTION: Promoted lesson '${entry.title}' to a permanent system rule due to high replication success.")
            }

            // 2. Gradual Confidence Decay
            // Rarely used or older lessons slightly decay in confidence score over time (e.g. older than 5 minutes for demo/polling)
            val ageMs = System.currentTimeMillis() - entry.dateCreated
            if (ageMs > 120000 && entry.timesConfirmed == 1) { // Old and only confirmed once
                val decayedScore = (entry.confidenceScore - 0.05).coerceAtLeast(0.10)
                if (decayedScore != entry.confidenceScore) {
                    updated = updated.copy(
                        confidenceScore = decayedScore,
                        evidence = "${updated.evidence} (Confidence decayed slightly due to inactivity)"
                    )
                    logs.add("📉 MEMORY EVOLUTION: Decayed confidence of inactive lesson '${entry.title}' to ${String.format(Locale.US, "%.1f", decayedScore * 100.0)}%.")
                }
            }
            
            if (updated != entry) {
                repository.updateKnowledge(updated)
            }
            evolvedEntries.add(updated)
        }

        // 3. Contradiction Detection & Suggested Merges
        // Detect contradictions between entries based on conflicting patterns in description
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
                        // Flag them by appending warning to evidence
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
}
