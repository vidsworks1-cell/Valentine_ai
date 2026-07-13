package com.example.aza.cognition

import java.util.Locale

object ResponseComposer {

    fun compose(plan: ResponsePlan, context: UnifiedContext, originalInput: String): String {
        // Map ResponsePlan and context back into the NlgEngine's dynamic parameters
        val intent = IntentEngine.analyze(originalInput)
        val conversationPlan = ConversationPlanner.plan(intent, ExecutiveState(), originalInput)
        val reasoningResult = ReasoningResult(
            approvedAction = plan.recommendedActions.firstOrNull(),
            expectedUtility = 0.8,
            safetyPassed = true,
            safetyViolationReason = null,
            logicExplanation = plan.internalReasoning
        )
        
        return NlgEngine.generateDynamicResponse(
            intent = intent,
            reasoning = reasoningResult,
            context = context,
            plan = conversationPlan,
            userInput = originalInput
        )
    }
}
