package com.example.aza.agent

data class SkillMetadata(
    val skillName: String,
    val description: String,
    var confidence: Float,
    val supportedInputs: List<String>,
    val supportedOutputs: List<String>,
    val executionCost: Double,
    val averageLatencyMs: Long,
    val implementingAgentId: String
)

class SkillRegistry {
    private val skills = mutableMapOf<String, SkillMetadata>()

    init {
        // Automatically scan and register skills from our primary suite of agents
        val agentSuite = listOf(
            CryptoTradingAgent(),
            ContentCreationAgent(),
            FreelanceAgent(),
            ComplianceYieldAuditorAgent(),
            WebSurferAgent(),
            TradingAgent(),
            ResearchAgent(),
            KnowledgeAgent(),
            RiskAgent(),
            ContentAgent(),
            DiagnosticsAgent()
        )
        autoRegisterSkills(agentSuite)
    }

    /**
     * Scans agent capability specifications and registers skills dynamically.
     */
    fun autoRegisterSkills(agents: List<AutonomousAgent>) {
        for (agent in agents) {
            for (cap in agent.capabilities()) {
                val inputs = when (cap.skill) {
                    "crypto_trading" -> listOf("market_trend", "balance_usd")
                    "market_analysis" -> listOf("market_trend")
                    "content_creation" -> listOf("profit_usd", "btc_trend")
                    "freelance_writing" -> listOf("available_gigs")
                    "compliance_audit" -> listOf("transaction_records", "balance_usd")
                    "web_surfing" -> listOf("web_query")
                    "knowledge_management" -> listOf("outcome_quality", "confidence")
                    else -> listOf("generic_input")
                }
                val outputs = when (cap.skill) {
                    "crypto_trading" -> listOf("order_status", "profit_usd")
                    "market_analysis" -> listOf("indicators", "trend_signal")
                    "content_creation" -> listOf("content_id", "platform")
                    "freelance_writing" -> listOf("proposal_text", "bid_status")
                    "compliance_audit" -> listOf("compliance_approved", "audited_balance")
                    "web_surfing" -> listOf("scraped_content")
                    "knowledge_management" -> listOf("knowledge_created")
                    else -> listOf("generic_output")
                }
                register(
                    SkillMetadata(
                        skillName = cap.skill,
                        description = "Automated capability routing for agent skill: ${cap.skill}",
                        confidence = cap.confidence,
                        supportedInputs = inputs,
                        supportedOutputs = outputs,
                        executionCost = if (cap.skill == "crypto_trading") 250.0 else 0.0,
                        averageLatencyMs = cap.estimatedDurationMs,
                        implementingAgentId = agent.id
                    )
                )
            }
        }
    }

    fun register(metadata: SkillMetadata) {
        skills[metadata.skillName] = metadata
    }

    fun getSkill(name: String): SkillMetadata? = skills[name]

    fun getAllSkills(): List<SkillMetadata> = skills.values.toList()

    /**
     * Resolves the best available skill implementation based on the task description or assigned name.
     */
    fun findBestSkillForTask(assignedAgent: String): SkillMetadata? {
        val lower = assignedAgent.lowercase()
        return when {
            lower.contains("trading") || lower.contains("crypto") -> skills["crypto_trading"]
            lower.contains("market") || lower.contains("analysis") -> skills["market_analysis"]
            lower.contains("content") || lower.contains("social") || lower.contains("director") -> skills["content_creation"]
            lower.contains("freelance") || lower.contains("proposal") || lower.contains("writer") -> skills["freelance_writing"]
            lower.contains("compliance") || lower.contains("audit") || lower.contains("auditor") -> skills["compliance_audit"]
            lower.contains("surf") || lower.contains("web") || lower.contains("browser") || lower.contains("search") || lower.contains("explorer") -> skills["web_surfing"]
            else -> skills["compliance_audit"]
        }
    }
}
