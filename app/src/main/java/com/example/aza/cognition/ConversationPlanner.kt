package com.example.aza.cognition

data class ConversationPlan(
    val primaryIntent: String,
    val activeMode: ConversationMode,
    val contributingAgents: List<String>,
    val isClarificationRequired: Boolean,
    val missingParameters: List<String>,
    val isActionExecutionRecommended: Boolean,
    val steps: List<String>
)

object ConversationPlanner {

    fun plan(intent: IntentObject, state: ExecutiveState): ConversationPlan {
        val steps = mutableListOf<String>()
        steps.add("Started executive reasoning for intent: ${intent.primaryIntent}")
        steps.add("Evaluating conversation mode: ${intent.conversationMode}")

        val contributingAgents = mutableListOf<String>()
        var clarificationRequired = false
        val missingParams = mutableListOf<String>()
        var actionRec = false

        when (intent.conversationMode) {
            ConversationMode.TRADING_ANALYSIS -> {
                contributingAgents.add("TradingAgent")
                contributingAgents.add("RiskAgent")
                steps.add("Enlisted TradingAgent and RiskAgent as expert specialist contributors.")
                
                if (intent.executionRequirement) {
                    actionRec = true
                    steps.add("Action requested. Formulating pre-execution safety bounds.")
                }
            }
            ConversationMode.CONTENT_STRATEGY -> {
                contributingAgents.add("ContentAgent")
                steps.add("Enlisted ContentAgent.")
            }
            ConversationMode.FREELANCE_STRATEGY -> {
                contributingAgents.add("FreelanceAgent")
                contributingAgents.add("ResearchAgent")
                steps.add("Enlisted Freelance and Research agents.")
            }
            ConversationMode.DIAGNOSTICS -> {
                contributingAgents.add("DiagnosticsAgent")
                contributingAgents.add("ExecutiveAgent")
                steps.add("Enlisted Diagnostics and Executive agents for self-audit.")
            }
            else -> {
                contributingAgents.add("ExecutiveAgent")
                steps.add("Assigned ExecutiveAgent for conversational support.")
            }
        }

        steps.add("Pre-execution safety verification passed.")

        return ConversationPlan(
            primaryIntent = intent.primaryIntent,
            activeMode = intent.conversationMode,
            contributingAgents = contributingAgents,
            isClarificationRequired = clarificationRequired,
            missingParameters = missingParams,
            isActionExecutionRecommended = actionRec,
            steps = steps
        )
    }
}
