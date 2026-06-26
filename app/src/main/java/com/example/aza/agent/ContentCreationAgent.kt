package com.example.aza.agent

import com.example.aza.context.AzaEvent
import com.example.aza.context.TaskContext
import com.example.data.database.AzaAgentTask
import com.example.data.database.ContentItem
import com.example.data.repository.AppRepository

class ContentCreationAgent : AutonomousAgent {
    override val id: String = "content_creation"
    override val displayName: String = "Social Content Director"

    override fun capabilities(): List<AgentCapability> {
        return listOf(
            AgentCapability(
                skill = "content_creation",
                confidence = 0.90f,
                priority = 8,
                estimatedDurationMs = 1200L
            )
        )
    }

    override suspend fun execute(
        task: AzaAgentTask,
        context: TaskContext,
        repository: AppRepository
    ): AgentResult {
        return try {
            // Read trading outcome (profitToday) written by CryptoTradingAgent in the shared context
            val tradeProfit = context.sharedFacts["profitToday"] as? Double ?: 0.0
            val btcTrend = context.sharedFacts["btcTrend"] as? String ?: "Passive"
            
            val detailTag = if (tradeProfit > 0.0) {
                "Showing real performance of $${tradeProfit} USD on $btcTrend momentum."
            } else {
                "Decomposing complex strategic planning trees."
            }

            val title = "Aza Autonomy - Decomposing Objectives on Android"
            val item = ContentItem(
                title = title,
                platform = "TIKTOK",
                scriptOutline = "Intro (0:00-0:05): Show Aza interface breaking tasks live! $detailTag\nBody (0:05-0:30): Step-by-step review and approval UI demo.\nOutro (0:30-0:40): Call to check link in bio for modern architecture blueprint.",
                status = "SCHEDULED",
                engagementCount = 0
            )
            repository.insertContent(item)
            
            val logDetails = "Structured dynamic script about '$title'. Estimated engagement: 18,400 content ripples. (Context profits used: $${tradeProfit})"
            val metrics = "Content Item Outline registered under TikTok SCHEDULED queue"

            // Write ContentCreation outputs to context
            context.sharedFacts["contentPublishedTitle"] = title

            // Push ContentCreated event to context pipeline
            context.pendingEvents.add(
                AzaEvent.ContentCreated(
                    title = title,
                    platform = "TIKTOK",
                    status = "SCHEDULED"
                )
            )

            AgentResult(
                success = true,
                executionLog = logDetails,
                performanceImpact = metrics
            )
        } catch (e: Exception) {
            AgentResult(
                success = false,
                executionLog = "Failed to run Social Content Director: ${e.localizedMessage}",
                performanceImpact = "No content schedule registered.",
                errorMessage = e.localizedMessage
            )
        }
    }
}
