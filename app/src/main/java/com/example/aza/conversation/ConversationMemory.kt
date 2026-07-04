package com.example.aza.conversation

import com.example.data.database.ChatMessage

data class RelationshipProfile(
    var preferredCommunicationStyle: String = "Analytical, charismatic, and business-focused",
    val longTermGoals: MutableList<String> = mutableListOf("Grow paper-trading asset values", "Automate video creation and freelance bids safely"),
    val recurringProjects: MutableList<String> = mutableListOf("AI Developer Tutorials", "BTC/ETH Spot Trading"),
    var preferredExplanations: String = "Structured breakdowns with clear risk-benefit summaries",
    val previousDecisions: MutableList<String> = mutableListOf("Enable Paper Execution initially"),
    val recurringQuestions: MutableList<String> = mutableListOf(),
    val collaborationHistory: MutableList<String> = mutableListOf("Successfully configured the multi-agent consensus system")
)

class ConversationMemory {

    val relationshipProfile = RelationshipProfile()
    
    // Rolling Conversational Context
    private val _recentMessages = mutableListOf<ChatMessage>()
    val recentMessages: List<ChatMessage> get() = _recentMessages

    val ongoingTopics = mutableListOf<String>()
    val unresolvedQuestions = mutableListOf<String>()
    var previousExplanation: String? = null
    val followUpReferences = mutableMapOf<String, String>()
    var lastActiveDecision: String? = null

    fun addMessage(message: ChatMessage) {
        _recentMessages.add(message)
        if (_recentMessages.size > 20) {
            _recentMessages.removeAt(0)
        }
        
        // Analyze message to update relationship profile or rolling context
        val text = message.text.lowercase()
        if (message.sender == "USER") {
            // Update goals if mentioned
            if (text.contains("my goal is") || text.contains("i want to achieve")) {
                val goal = message.text.substringAfter("goal is", "").substringAfter("achieve", "").trim()
                if (goal.isNotEmpty() && !relationshipProfile.longTermGoals.contains(goal)) {
                    relationshipProfile.longTermGoals.add(goal)
                }
            }
            
            // Update preferred style or explanations
            if (text.contains("be more concise") || text.contains("speak briefly")) {
                relationshipProfile.preferredCommunicationStyle = "Highly concise, direct and brief"
            } else if (text.contains("explain in detail") || text.contains("be detailed")) {
                relationshipProfile.preferredCommunicationStyle = "Extremely detailed, in-depth and thorough"
            }
            
            if (text.contains("explain with metaphors") || text.contains("explain simply")) {
                relationshipProfile.preferredExplanations = "Layman explanations with helpful metaphors"
            }
            
            // Track unresolved questions
            if (text.contains("?") && !unresolvedQuestions.contains(message.text)) {
                unresolvedQuestions.add(message.text)
                if (unresolvedQuestions.size > 5) unresolvedQuestions.removeAt(0)
            }
        } else if (message.sender == "AZA") {
            // AZA responded, check if we answered a recent question
            if (unresolvedQuestions.isNotEmpty()) {
                unresolvedQuestions.removeAt(0) // Resolve top question
            }
            previousExplanation = message.text
        }
    }

    fun updateTopics(topics: List<String>) {
        ongoingTopics.clear()
        ongoingTopics.addAll(topics)
    }

    fun resolveAnaphoricReference(text: String): String {
        val lower = text.lowercase().trim()
        
        // Handle "continue"
        if (lower == "continue" || lower == "go on" || lower == "proceed") {
            val lastTopic = ongoingTopics.lastOrNull() ?: "the previous discussion"
            return "Continuing our analysis on $lastTopic. Let's expand on the key items we discussed."
        }
        
        // Handle "like yesterday"
        if (lower.contains("like yesterday") || lower.contains("as discussed yesterday")) {
            val goalStr = relationshipProfile.longTermGoals.joinToString(", ")
            return "Referencing our previous strategy and goals: $goalStr."
        }
        
        // Handle "the previous strategy"
        if (lower.contains("previous strategy") || lower.contains("the last strategy")) {
            val decision = relationshipProfile.previousDecisions.lastOrNull() ?: "Paper Spot Trading"
            return "Applying our active strategy: $decision."
        }
        
        // Handle "that mission"
        if (lower.contains("that mission") || lower.contains("the active mission")) {
            return "Looking closer at our active mission context to continue execution."
        }
        
        return text
    }
}
