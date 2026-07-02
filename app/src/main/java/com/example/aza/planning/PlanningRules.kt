package com.example.aza.planning

import com.example.data.database.AzaAgentTask

/**
 * Rules and templates to decompose high-level objective goals into executable tasks.
 */
object PlanningRules {

    data class RuleTemplate(
        val goalKeyword: String,
        val tasks: List<TaskBlueprint>
    )

    data class TaskBlueprint(
        val title: String,
        val description: String,
        val assignedAgent: String,
        val requiredSkill: String,
        val priority: String = "MEDIUM",
        val estimatedBudget: Double = 0.0,
        val dependencies: String = ""
    )

    private val templates = listOf(
        RuleTemplate(
            goalKeyword = "trading",
            tasks = listOf(
                TaskBlueprint(
                    title = "Research current market regime",
                    description = "Analyze recent price action and volatility to determine trend or range regime.",
                    assignedAgent = "ResearchAgent",
                    requiredSkill = "web_surfing",
                    priority = "HIGH"
                ),
                TaskBlueprint(
                    title = "Backtest strategy",
                    description = "Validate candidate strategy performance using historical candle distributions.",
                    assignedAgent = "TradingAgent",
                    requiredSkill = "crypto_trading",
                    dependencies = "Research current market regime"
                ),
                TaskBlueprint(
                    title = "Validate risk limits",
                    description = "Evaluate leverage, drawdown constraints, and size limits against safety parameters.",
                    assignedAgent = "RiskAgent",
                    requiredSkill = "compliance_audit",
                    priority = "HIGH"
                ),
                TaskBlueprint(
                    title = "Wait for signal & Execute trade",
                    description = "Monitor live feed for strategy crossover signals and dispatch paper market execution.",
                    assignedAgent = "TradingAgent",
                    requiredSkill = "crypto_trading",
                    dependencies = "Backtest strategy,Validate risk limits"
                ),
                TaskBlueprint(
                    title = "Record outcome & Update Knowledge Base",
                    description = "Log net profit/loss, slippage, and fees. Commit learned experiences to persistent storage.",
                    assignedAgent = "KnowledgeAgent",
                    requiredSkill = "knowledge_management",
                    dependencies = "Wait for signal & Execute trade"
                )
            )
        ),
        RuleTemplate(
            goalKeyword = "grow",
            tasks = listOf(
                TaskBlueprint(
                    title = "Research current market regime",
                    description = "Scan high-volatility pairings and macro indicators.",
                    assignedAgent = "ResearchAgent",
                    requiredSkill = "web_surfing"
                ),
                TaskBlueprint(
                    title = "Optimize position sizing risk limits",
                    description = "Verify safe leverage bounds based on portfolio size.",
                    assignedAgent = "RiskAgent",
                    requiredSkill = "compliance_audit"
                ),
                TaskBlueprint(
                    title = "Execute tactical asset trades",
                    description = "Place buy/sell paper targets based on momentum indicators.",
                    assignedAgent = "TradingAgent",
                    requiredSkill = "crypto_trading",
                    dependencies = "Research current market regime,Optimize position sizing risk limits"
                ),
                TaskBlueprint(
                    title = "Extract lessons & Refine strategy",
                    description = "Analyze trade logs and update performance parameters.",
                    assignedAgent = "KnowledgeAgent",
                    requiredSkill = "knowledge_management",
                    dependencies = "Execute tactical asset trades"
                )
            )
        ),
        RuleTemplate(
            goalKeyword = "content",
            tasks = listOf(
                TaskBlueprint(
                    title = "Research trending content keywords",
                    description = "Analyze trending development or finance hashtags and topics.",
                    assignedAgent = "ResearchAgent",
                    requiredSkill = "web_surfing"
                ),
                TaskBlueprint(
                    title = "Draft script outline & storyboard",
                    description = "Create engaging high-retention video layouts and script outlines.",
                    assignedAgent = "ContentAgent",
                    requiredSkill = "content_creation",
                    dependencies = "Research trending content keywords"
                ),
                TaskBlueprint(
                    title = "Verify platform copyright & compliance guidelines",
                    description = "Ensure script outline matches legal copyright policies.",
                    assignedAgent = "RiskAgent",
                    requiredSkill = "compliance_audit"
                ),
                TaskBlueprint(
                    title = "Publish content drafts to social channel",
                    description = "Register ideas as ready drafts on corresponding channels.",
                    assignedAgent = "ContentAgent",
                    requiredSkill = "content_creation",
                    dependencies = "Draft script outline & storyboard,Verify platform copyright & compliance guidelines"
                )
            )
        ),
        RuleTemplate(
            goalKeyword = "freelance",
            tasks = listOf(
                TaskBlueprint(
                    title = "Scan for matching freelance gigs",
                    description = "Identify premium client contracts and budgets.",
                    assignedAgent = "ResearchAgent",
                    requiredSkill = "web_surfing"
                ),
                TaskBlueprint(
                    title = "Write tailored proposal proposal text",
                    description = "Generate customized client pitch highlight portfolio skills.",
                    assignedAgent = "FreelanceAgent",
                    requiredSkill = "freelance_writing",
                    dependencies = "Scan for matching freelance gigs"
                ),
                TaskBlueprint(
                    title = "Perform budget and safety validation audits",
                    description = "Check escrow requirements and client safety reputation.",
                    assignedAgent = "RiskAgent",
                    requiredSkill = "compliance_audit"
                ),
                TaskBlueprint(
                    title = "Submit active gig bids",
                    description = "Submit proposed bids to client databases.",
                    assignedAgent = "FreelanceAgent",
                    requiredSkill = "freelance_writing",
                    dependencies = "Write tailored proposal proposal text,Perform budget and safety validation audits"
                )
            )
        )
    )

    private val defaultBlueprint = listOf(
        TaskBlueprint(
            title = "Identify goal prerequisites",
            description = "Map baseline needs and dependencies.",
            assignedAgent = "ResearchAgent",
            requiredSkill = "web_surfing"
        ),
        TaskBlueprint(
            title = "Perform diagnostics check",
            description = "Audit core system health variables.",
            assignedAgent = "DiagnosticsAgent",
            requiredSkill = "compliance_audit"
        ),
        TaskBlueprint(
            title = "Execute operational tasks",
            description = "Run specialized workflows under supervisor.",
            assignedAgent = "FreelanceAgent",
            requiredSkill = "freelance_writing",
            dependencies = "Identify goal prerequisites,Perform diagnostics check"
        )
    )

    fun getBlueprintsForGoal(goal: String): List<TaskBlueprint> {
        val lowerGoal = goal.lowercase()
        val template = templates.find { lowerGoal.contains(it.goalKeyword) }
        return template?.tasks ?: defaultBlueprint
    }
}
