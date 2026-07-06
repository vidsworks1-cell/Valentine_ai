package com.example.aza.cognition

data class ResponsePlan(
    val facts: List<String>,
    val internalReasoning: String,
    val recommendedActions: List<String>,
    val explanationPref: String
)

object ResponsePlanner {

    fun formulate(plan: ConversationPlan, reasoning: ReasoningResult, context: UnifiedContext): ResponsePlan {
        val facts = mutableListOf<String>()
        facts.add("Simulated Cash Treasury: $${String.format("%.2f", context.cashBalance)} USD")
        facts.add("Pending Actions: None")
        
        val recommendedActions = mutableListOf<String>()
        if (reasoning.approvedAction != null) {
            recommendedActions.add(reasoning.approvedAction)
        }

        return ResponsePlan(
            facts = facts,
            internalReasoning = reasoning.logicExplanation,
            recommendedActions = recommendedActions,
            explanationPref = context.preferences.preferredExplanationDepth
        )
    }
}
