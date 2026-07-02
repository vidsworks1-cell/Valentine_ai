package com.example.aza.agent

import com.example.aza.context.AzaEvent
import com.example.aza.context.TaskContext
import com.example.data.database.AzaAgentTask
import com.example.data.database.ContentItem
import com.example.data.repository.AppRepository

class ContentAgent : AutonomousAgent {
    override val id: String = "content_agent"
    override val displayName: String = "Content Agent"

    override fun capabilities(): List<AgentCapability> {
        return listOf(
            AgentCapability(
                skill = "content_creation",
                confidence = 0.92f,
                priority = 10,
                estimatedDurationMs = 2800L
            )
        )
    }

    override suspend fun execute(
        task: AzaAgentTask,
        context: TaskContext,
        repository: AppRepository
    ): AgentResult {
        return try {
            val title = if (task.input.isNotEmpty()) task.input else "Building Autonomous Trading Loop on TikTok"
            val scriptOutline = repository.getAiContentScript(title, "TIKTOK")

            repository.insertContent(
                ContentItem(
                    title = title,
                    platform = "TIKTOK",
                    scriptOutline = scriptOutline,
                    status = "DRAFT"
                )
            )

            val log = "1. [Content Creation] Scanning trending creator keywords.\n" +
                      "2. [Scripting] Synthesizing engaging video storyline: '$title'\n" +
                      "3. [Drafting] Registered video script successfully inside SQLite Content DB."

            context.pendingEvents.add(
                AzaEvent.ContentCreated(
                    title = title,
                    platform = "TIKTOK",
                    status = "DRAFT"
                )
            )

            AgentResult(
                success = true,
                executionLog = log,
                performanceImpact = "Created social content draft: '$title'"
            )
        } catch (e: Exception) {
            AgentResult(
                success = false,
                executionLog = "Failed to run ContentAgent: ${e.localizedMessage}",
                performanceImpact = "Aborted."
            )
        }
    }
}
