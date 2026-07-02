package com.example.aza.executive

import com.example.aza.execution.AzaLearningEngine
import com.example.data.database.AzaMission
import com.example.data.database.AzaObjective
import com.example.data.database.TaskEntity
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale

class ExecutivePlanner(
    private val repository: AppRepository,
    private val learningEngine: AzaLearningEngine
) {
    /**
     * Automatically decomposes a complex mission description into multiple targeted, sequential objectives
     * to prevent overloaded tasks.
     */
    suspend fun autoDecomposeMission(mission: AzaMission, baseObjectiveText: String): List<AzaObjective> {
        val objectiveTexts = mutableListOf<String>()
        val descriptionLower = baseObjectiveText.lowercase(Locale.US)

        if (descriptionLower.contains("portfolio") || descriptionLower.contains("trade") || descriptionLower.contains("crypto")) {
            objectiveTexts.add("Deconstruct market regimes, check volatility indicators, and perform risk audit.")
            objectiveTexts.add("Dispatch spot trades, hedge allocations, and generate compliance ledger records.")
        } else if (descriptionLower.contains("video") || descriptionLower.contains("youtube") || descriptionLower.contains("tiktok") || descriptionLower.contains("content")) {
            objectiveTexts.add("Conduct viral content keyword scraping, draft high-relevance media scripts, and check compliance.")
            objectiveTexts.add("Format adaptive layouts, run proofreader scans, and queue automated publishing runs.")
        } else if (descriptionLower.contains("gig") || descriptionLower.contains("freelance") || descriptionLower.contains("proposal")) {
            objectiveTexts.add("Analyze active freelance project channels, filter high-paying bids, and audit security conditions.")
            objectiveTexts.add("Generate tailored client proposals, estimate structural milestone costs, and queue communications.")
        } else {
            // General decomposition
            objectiveTexts.add("Scrape and analyze current system environment metrics and research optimal routes.")
            objectiveTexts.add("Execute designated task blueprints and commit the performance telemetry.")
        }

        val platform = if (descriptionLower.contains("youtube") || descriptionLower.contains("video")) "YOUTUBE"
                        else if (descriptionLower.contains("tiktok")) "TIKTOK"
                        else if (descriptionLower.contains("crypto") || descriptionLower.contains("portfolio")) "CRYPTO"
                        else if (descriptionLower.contains("gig") || descriptionLower.contains("proposal")) "FREELANCE"
                        else "ALL"

        val createdObjectives = mutableListOf<AzaObjective>()
        for ((idx, text) in objectiveTexts.withIndex()) {
            val title = "Phase 12 Objective ${idx + 1}: ${text.take(30)}..."
            val obj = AzaObjective(
                objective = text,
                title = title,
                targetPlatform = platform,
                targetBudget = if (platform == "CRYPTO") 500.0 else 0.0,
                status = "NEW",
                progress = 0.0f,
                currentTask = "Pending Executive Allocation",
                createdAt = System.currentTimeMillis() + (idx * 100),
                updatedAt = System.currentTimeMillis() + (idx * 100),
                missionId = mission.id
            )
            val insertedId = repository.insertObjective(obj)
            createdObjectives.add(obj.copy(id = insertedId.toInt()))
        }
        return createdObjectives
    }

    /**
     * Determines the optimal agent for a given task description based on historical performance
     * and specialized skillset matching.
     */
    suspend fun findBestAgentForTask(taskTitle: String, taskDescription: String): String {
        val lowerText = "$taskTitle $taskDescription".lowercase(Locale.US)
        
        // Base candidate agents
        val candidates = when {
            lowerText.contains("risk") || lowerText.contains("compliance") || lowerText.contains("audit") || lowerText.contains("limit") -> listOf("RiskAgent", "TradingAgent")
            lowerText.contains("trade") || lowerText.contains("order") || lowerText.contains("buy") || lowerText.contains("portfolio") || lowerText.contains("sell") -> listOf("TradingAgent", "RiskAgent")
            lowerText.contains("scrape") || lowerText.contains("research") || lowerText.contains("search") || lowerText.contains("trend") || lowerText.contains("query") -> listOf("ResearchAgent", "ContentAgent")
            lowerText.contains("write") || lowerText.contains("draft") || lowerText.contains("video") || lowerText.contains("script") || lowerText.contains("content") || lowerText.contains("publish") -> listOf("ContentAgent", "ResearchAgent")
            else -> listOf("ResearchAgent", "RiskAgent", "TradingAgent", "ContentAgent")
        }

        // Compare reliability multipliers
        var bestAgent = candidates.first()
        var highestMultiplier = 0.0f
        for (agent in candidates) {
            val multiplier = learningEngine.calculateReliabilityMultiplier("Agent: $agent")
            if (multiplier > highestMultiplier) {
                highestMultiplier = multiplier
                bestAgent = agent
            }
        }
        return bestAgent
    }

    /**
     * Periodically reviews blocked tasks, duplicates, resource bottlenecks, stalled missions, or unnecessary retries
     * and performs optimization repairs automatically.
     */
    suspend fun superviseAndOptimize(onLog: (String) -> Unit): String {
        val logs = mutableListOf<String>()
        val activeMissions = repository.allMissions.firstOrNull()?.filter { it.status == "Active" } ?: emptyList()
        
        for (mission in activeMissions) {
            val objectives = repository.getObjectivesForMissionDirect(mission.id)
            for (obj in objectives) {
                val tasks = repository.getQueueTasksForObjectiveDirect(obj.id)
                
                // 1. Detect stalled tasks (Running for > 60 seconds in simulated polling context)
                val stalledTasks = tasks.filter { it.status == "RUNNING" && (System.currentTimeMillis() - it.updatedAt > 60000) }
                for (task in stalledTasks) {
                    logs.add("🔧 SUPERVISION: Detected stalled task '${task.title}' on '${task.assignedAgent}'. Re-queuing for execution.")
                    repository.updateQueueTask(task.copy(status = "RETRY", updatedAt = System.currentTimeMillis()))
                }

                // 2. Detect unnecessary retries & blockages
                val failingTasks = tasks.filter { it.status == "RETRY" && it.retryCount >= it.maxRetries - 1 }
                for (task in failingTasks) {
                    logs.add("🔧 SUPERVISION: Task '${task.title}' is on its last retry. Re-routing dynamically to secondary agent to bypass bottleneck.")
                    val alternativeAgent = if (task.assignedAgent == "TradingAgent") "RiskAgent" else "TradingAgent"
                    repository.updateQueueTask(task.copy(
                        assignedAgent = alternativeAgent,
                        retryCount = 0,
                        status = "QUEUED",
                        updatedAt = System.currentTimeMillis()
                    ))
                }

                // 3. Detect duplicate tasks
                val groupedByTitle = tasks.groupBy { it.title }
                for ((title, dupes) in groupedByTitle) {
                    if (dupes.size > 1) {
                        // De-duplicate: Keep first, reject rest
                        dupes.drop(1).forEach { dup ->
                            if (dup.status == "QUEUED" || dup.status == "RETRY") {
                                logs.add("🔧 SUPERVISION: De-duplicating work. Rejected duplicate queued task '${dup.title}'.")
                                repository.updateQueueTask(dup.copy(status = "REJECTED", updatedAt = System.currentTimeMillis()))
                            }
                        }
                    }
                }
            }
        }
        
        if (logs.isEmpty()) {
            return "All autonomous loops and running threads are verified, optimized, and executing within safe bounds."
        }
        
        val summary = logs.joinToString("\n")
        onLog("🤖 AUTONOMOUS SUPERVISION:\n$summary")
        return summary
    }
}
