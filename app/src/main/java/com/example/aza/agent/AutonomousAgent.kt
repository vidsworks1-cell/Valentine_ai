package com.example.aza.agent

import com.example.aza.context.TaskContext
import com.example.data.database.AzaAgentTask
import com.example.data.repository.AppRepository

/**
 * Result of an agent's task execution.
 */
data class AgentResult(
    val success: Boolean,
    val executionLog: String,
    val performanceImpact: String,
    val errorMessage: String? = null,
    val retryCount: Int = 0
)

/**
 * Defines capability metadata advertised by an agent.
 * Enables ranked capability routing based on skill keyword matches.
 */
data class AgentCapability(
    val skill: String,              // Unique skill identifier matching task profiles (e.g. "crypto_trading")
    val confidence: Float,          // Measured confidence rating (0.0 to 1.0)
    val priority: Int,              // Priority bias of this agent relative to other agents for this skill
    val estimatedDurationMs: Long   // Expected time window of completion in milliseconds
)

/**
 * Interface representing a specialized autonomous agent in Aza's decentralized network.
 */
interface AutonomousAgent {
    val id: String              // Unique identifier (e.g., "crypto_trading")
    val displayName: String     // User-facing display name (e.g., "Crypto Trading Agent")
    
    /**
     * Advertises the specific capabilities of this agent.
     */
    fun capabilities(): List<AgentCapability>
    
    /**
     * Executes the matching task within the shared TaskContext sandbox.
     */
    suspend fun execute(
        task: AzaAgentTask,
        context: TaskContext,
        repository: AppRepository
    ): AgentResult

    /**
     * Determines if the agent can handle the specific task.
     */
    fun canHandle(task: AzaAgentTask): Boolean {
        return capabilities().any { it.skill == task.requiredSkill }
    }

    /**
     * Cancels an ongoing task execution.
     */
    fun cancel(task: AzaAgentTask) {
        // Default empty implementation
    }

    /**
     * Estimates cost in credits/dollars for running the task.
     */
    fun estimateCost(task: AzaAgentTask): Double {
        val cap = capabilities().find { it.skill == task.requiredSkill }
        return if (task.requiredSkill == "crypto_trading") 250.0 else 0.0
    }

    /**
     * Estimates execution time in milliseconds.
     */
    fun estimateTime(task: AzaAgentTask): Long {
        val cap = capabilities().find { it.skill == task.requiredSkill }
        return cap?.estimatedDurationMs ?: 1500L
    }
}
