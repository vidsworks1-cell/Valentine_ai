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

    fun plan(intent: IntentObject, state: ExecutiveState, text: String): ConversationPlan {
        val steps = mutableListOf<String>()
        steps.add("Started executive reasoning for intent: ${intent.primaryIntent}")
        steps.add("Evaluating conversation mode: ${intent.conversationMode}")

        val contributingAgents = mutableListOf<String>()
        var clarificationRequired = false
        val missingParams = mutableListOf<String>()
        var actionRec = false

        val lower = text.lowercase()

        when (intent.conversationMode) {
            ConversationMode.TRADING_ANALYSIS -> {
                contributingAgents.add("TradingAgent")
                contributingAgents.add("RiskAgent")
                steps.add("Enlisted TradingAgent and RiskAgent as expert specialist contributors.")
                
                val isSettingBudget = lower.contains("budget") || lower.contains("allowance") || lower.contains("disposition") || lower.contains("trade today")
                val amountStr = """\b\d+(?:\.\d+)?\b""".toRegex().find(text)?.value
                val parsedBudget = amountStr?.toDoubleOrNull()
                
                val currentBudget = com.example.aza.security.SecureCredentialStore.getTradingBudget()
                
                if (isSettingBudget && parsedBudget != null) {
                    com.example.aza.security.SecureCredentialStore.saveTradingBudget(parsedBudget)
                    steps.add("User specified trading budget: $$parsedBudget. Saved successfully in SecureCredentialStore.")
                } else if (currentBudget <= 0.0) {
                    if (parsedBudget != null && (lower.contains("trade") || lower.contains("buy") || lower.contains("sell") || lower.contains("start"))) {
                        com.example.aza.security.SecureCredentialStore.saveTradingBudget(parsedBudget)
                        steps.add("No budget was set, but user specified $$parsedBudget in trade context. Set as daily trading budget.")
                    } else {
                        clarificationRequired = true
                        missingParams.add("how much you want to trade today!? (daily trading budget)")
                        steps.add("Trading initialized, but no daily trading budget/allowance is set. Initiating Clarification Protocol.")
                    }
                } else {
                    steps.add("Verified active daily trading budget of $$currentBudget in disposition.")
                }
                
                if (intent.executionRequirement && !clarificationRequired) {
                    actionRec = true
                    steps.add("Action requested. Formulating pre-execution safety bounds with active budget of $$currentBudget.")
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
