package com.example.aza.agent

import com.example.data.database.AzaAgentTask

/**
 * Registry acting as a dynamically ranked compatibility engine to select high-capability agents.
 */
class AgentRegistry {
    private val agents = mutableMapOf<String, AutonomousAgent>()

    init {
        // Legacy compatibility agents
        register(CryptoTradingAgent())
        register(ContentCreationAgent())
        register(FreelanceAgent())
        register(ComplianceYieldAuditorAgent())
        register(WebSurferAgent())

        // Modern Phase 10 independent agents
        register(TradingAgent())
        register(ResearchAgent())
        register(KnowledgeAgent())
        register(RiskAgent())
        register(ContentAgent())
        register(DiagnosticsAgent())
    }

    /**
     * Registers a specialized agent.
     */
    fun register(agent: AutonomousAgent) {
        agents[agent.id] = agent
    }

    /**
     * Retrieves an agent directly by ID.
     */
    fun getAgentById(id: String): AutonomousAgent? {
        return agents[id]
    }

    /**
     * Standardizes legacy text field strings to resolve the required skill keyword.
     */
    private fun resolveSkillFromAssignedAgent(assignedAgent: String): String {
        val lower = assignedAgent.lowercase()
        return when {
            lower.contains("trading") || lower.contains("crypto") -> "crypto_trading"
            lower.contains("content") || lower.contains("social") || lower.contains("director") -> "content_creation"
            lower.contains("freelance") || lower.contains("proposal") || lower.contains("writer") -> "freelance_writing"
            lower.contains("compliance") || lower.contains("audit") || lower.contains("auditor") -> "compliance_audit"
            lower.contains("surf") || lower.contains("web") || lower.contains("browser") || lower.contains("search") || lower.contains("explorer") -> "web_surfing"
            else -> "compliance_audit"
        }
    }

    /**
     * Selects the absolute HIGHEST ranked agent that can fulfill the needed capability.
     * Calculates the suitability rank score as: (confidence * priority).
     */
    fun findAgentForTask(task: AzaAgentTask): AutonomousAgent? {
        val targetSkill = resolveSkillFromAssignedAgent(task.assignedAgent)
        
        // Find agents advertising the requested competency and score their matching capabilities
        val rankedOptions = agents.values.flatMap { agent ->
            agent.capabilities()
                .filter { it.skill == targetSkill }
                .map { capability ->
                    // Suitability rank score is the product of confidence factor and priority ordering
                    val suitScore = capability.confidence * capability.priority
                    agent to suitScore
                }
        }

        // Return the agent with highest ranking score
        val selectedAgent = rankedOptions
            .sortedByDescending { it.second }
            .map { it.first }
            .firstOrNull()

        // Fallback safely to our security compliance agent
        return selectedAgent ?: agents["compliance_yield_auditor"]
    }

    /**
     * Lists all registered agents in the system.
     */
    fun getAllAgents(): List<AutonomousAgent> {
        return agents.values.toList()
    }
}
