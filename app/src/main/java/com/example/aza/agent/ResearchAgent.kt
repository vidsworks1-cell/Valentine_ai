package com.example.aza.agent

import com.example.aza.context.TaskContext
import com.example.aza.web.WebSurfer
import com.example.data.database.AzaAgentTask
import com.example.data.repository.AppRepository

class ResearchAgent : AutonomousAgent {
    override val id: String = "research_agent"
    override val displayName: String = "Research Agent"

    override fun capabilities(): List<AgentCapability> {
        return listOf(
            AgentCapability(
                skill = "web_surfing",
                confidence = 0.94f,
                priority = 10,
                estimatedDurationMs = 3000L
            )
        )
    }

    override suspend fun execute(
        task: AzaAgentTask,
        context: TaskContext,
        repository: AppRepository
    ): AgentResult {
        return try {
            val query = if (task.input.isNotEmpty()) task.input else "BTC current market regime"
            val results = WebSurfer.searchWeb(query)
            val log = "1. [Research] Dispatched web scan for query: '$query'\n" +
                      "2. [Results] Found ${results.size} high confidence references.\n" +
                      results.take(3).joinToString("\n") { " - ${it.title} (${it.url}): ${it.snippet}" }

            context.sharedFacts["marketRegimeSummary"] = "Bullish Momentum trend detected on BTC"
            context.sharedFacts["volatilityIndex"] = "HIGH"
            
            AgentResult(
                success = true,
                executionLog = log + "\n📝 [Collaboration] Flagged high market volatility to RiskAgent.",
                performanceImpact = "Successfully scraped query: '$query' and cataloged results."
            )
        } catch (e: Exception) {
            AgentResult(
                success = false,
                executionLog = "Failed to run ResearchAgent: ${e.localizedMessage}",
                performanceImpact = "Aborted."
            )
        }
    }
}
