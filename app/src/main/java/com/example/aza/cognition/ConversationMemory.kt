package com.example.aza.cognition

import com.example.data.database.ChatMessage

data class UserPreferences(
    var preferredExplanationDepth: String = "Structured breakdowns with clear risk-benefit summaries",
    var preferredTechnicalLevel: String = "Highly technical and analytical",
    var preferredTone: String = "Charismatic, professional and business-focused",
    var preferredResponseLength: String = "Balanced, concise paragraphs with bold keywords",
    var preferredDecisionStyle: String = "Autopilot with strict approval verification"
)

class ConversationMemory {
    val preferences = UserPreferences()
    private val _recentMessages = mutableListOf<ChatMessage>()
    val recentMessages: List<ChatMessage> get() = _recentMessages

    fun addMessage(message: ChatMessage) {
        _recentMessages.add(message)
        if (_recentMessages.size > 20) {
            _recentMessages.removeAt(0)
        }
        
        val text = message.text.lowercase()
        if (message.sender == "USER") {
            if (text.contains("be brief") || text.contains("be short") || text.contains("speak briefly")) {
                preferences.preferredResponseLength = "Highly brief, concise and direct"
            } else if (text.contains("be detailed") || text.contains("explain in detail")) {
                preferences.preferredResponseLength = "Extremely detailed, in-depth and comprehensive"
            }
            if (text.contains("explain simply") || text.contains("layman terms") || text.contains("metaphor")) {
                preferences.preferredExplanationDepth = "Layman explanations using helpful metaphors"
            }
        }
    }

    fun isTransient(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower == "hi" || lower == "hello" || lower == "hey" || lower == "thanks" || lower == "ok" || lower == "yes" || lower == "no"
    }
}
