package com.example.aza.cognition

data class QualityMetrics(
    val isMisunderstood: Boolean,
    val isTooVerbose: Boolean,
    val isCorrectAgentSelected: Boolean,
    val score: Double
)

object ConversationQualityEvaluator {

    fun evaluate(userInput: String, response: String, plan: ConversationPlan): QualityMetrics {
        val textLower = userInput.lowercase()
        val responseLower = response.lowercase()
        
        var misunderstood = false
        var correctAgents = true
        
        if (textLower.contains("buy") && !responseLower.contains("buy") && !responseLower.contains("trade") && !responseLower.contains("clarify")) {
            misunderstood = true
        }
        
        if (plan.contributingAgents.isEmpty()) {
            correctAgents = false
        }

        val verbosity = response.length > 1500

        var score = 1.0
        if (misunderstood) score -= 0.4
        if (verbosity) score -= 0.1
        if (!correctAgents) score -= 0.2

        return QualityMetrics(
            isMisunderstood = misunderstood,
            isTooVerbose = verbosity,
            isCorrectAgentSelected = correctAgents,
            score = score
        )
    }
}
