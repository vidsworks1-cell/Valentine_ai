package com.example.aza.cognition

import com.example.data.database.ChatMessage

data class UserPreferences(
    var preferredExplanationDepth: String = "Structured breakdowns with clear risk-benefit summaries",
    var preferredTechnicalLevel: String = "Highly technical and analytical",
    var preferredTone: String = "Charismatic, professional and business-focused",
    var preferredResponseLength: String = "Balanced, concise paragraphs with bold keywords",
    var preferredDecisionStyle: String = "Autopilot with strict approval verification",
    
    // Dynamic APC parameters
    var technicalLevelScore: Double = 0.70,
    var verbosityPreference: Double = 0.60,
    var formalityPreference: Double = 0.80,
    var decisionControlPreference: Double = 0.50
)

class ConversationMemory {
    val preferences = UserPreferences()
    private val _recentMessages = mutableListOf<ChatMessage>()
    val recentMessages: List<ChatMessage> get() = _recentMessages

    fun setMessages(messages: List<ChatMessage>) {
        _recentMessages.clear()
        _recentMessages.addAll(messages.takeLast(20))
    }

    fun addMessage(message: ChatMessage) {
        _recentMessages.add(message)
        if (_recentMessages.size > 20) {
            _recentMessages.removeAt(0)
        }
        
        if (message.sender == "USER") {
            runAdaptivePreferenceCalibration(message.text)
        }
    }

    /**
     * Run linguistic calibration on UserPreferences dimensions
     */
    fun runAdaptivePreferenceCalibration(text: String) {
        val lower = text.lowercase(java.util.Locale.US)
        val words = lower.split(Regex("\\s+"))
        
        // --- 1. Technical Level Score Calibration ---
        val techCues = listOf(
            "entropy", "regularization", "stochastic", "ppo", "trpo", "asymmetry", 
            "volatility", "slippage", "microstructure", "database", "repository", 
            "garch", "exponential", "dampener", "quantitative", "consensus", "hyperparameter",
            "heuristic", "algorithmic", "mathematical", "formulas", "equation", "proof"
        )
        val simpleCues = listOf(
            "simply", "layman", "metaphor", "analogy", "explain like", "eli5", 
            "simple", "basic", "beginner", "easy", "plain english", "summary"
        )
        
        var techShift = 0.0
        techCues.forEach { if (lower.contains(it)) techShift += 0.08 }
        simpleCues.forEach { if (lower.contains(it)) techShift -= 0.12 }
        preferences.technicalLevelScore = (preferences.technicalLevelScore + techShift).coerceIn(0.10, 1.0)
        
        // --- 2. Verbosity Preference Calibration ---
        val briefCues = listOf(
            "brief", "concise", "short", "bullet", "tl;dr", "tldr", "quick", "fast",
            "summarize", "in a nutshell", "less words", "stop talking", "cut to the chase"
        )
        val deepCues = listOf(
            "detail", "comprehensive", "depth", "deep dive", "exhaustive", "full breakdown",
            "explain everything", "step by step", "elaborate", "background"
        )
        
        var verbosityShift = 0.0
        briefCues.forEach { if (lower.contains(it)) verbosityShift -= 0.15 }
        deepCues.forEach { if (lower.contains(it)) verbosityShift += 0.12 }
        if (words.size > 25) verbosityShift += 0.05
        if (words.size < 5 && words.isNotEmpty()) verbosityShift -= 0.02
        preferences.verbosityPreference = (preferences.verbosityPreference + verbosityShift).coerceIn(0.15, 1.0)
        
        // --- 3. Formality Preference Calibration ---
        val casualCues = listOf(
            "hey", "hi", "yo", "what's up", "dude", "buddy", "cool", "awesome", "great",
            "haha", "lol", "chill", "relaxed", "fun"
        )
        val formalCues = listOf(
            "please", "thank you", "sincerely", "regards", "verify", "compliance",
            "auditor", "analysis", "execute", "strategize", "formal", "professional"
        )
        
        var formalityShift = 0.0
        casualCues.forEach { if (lower.contains(it)) formalityShift -= 0.10 }
        formalCues.forEach { if (lower.contains(it)) formalityShift += 0.05 }
        preferences.formalityPreference = (preferences.formalityPreference + formalityShift).coerceIn(0.20, 1.0)
        
        // --- 4. Decision Control Preference Calibration
        val manualCues = listOf(
            "ask me", "my permission", "i want to approve", "let me verify", "do not execute",
            "manual", "stop before", "safety first", "confirm with me", "wait for me"
        )
        val autoCues = listOf(
            "autopilot", "auto", "execute automatically", "run in background", "autonomous",
            "you decide", "handle it", "no need to ask", "i trust you", "fully automate"
        )
        
        var decisionShift = 0.0
        manualCues.forEach { if (lower.contains(it)) decisionShift -= 0.12 }
        autoCues.forEach { if (lower.contains(it)) decisionShift += 0.15 }
        preferences.decisionControlPreference = (preferences.decisionControlPreference + decisionShift).coerceIn(0.10, 1.0)
        
        // Map back to strings for existing backward compatibility
        preferences.preferredResponseLength = when {
            preferences.verbosityPreference < 0.35 -> "Highly brief, concise and direct"
            preferences.verbosityPreference > 0.75 -> "Extremely detailed, in-depth and comprehensive"
            else -> "Balanced, concise paragraphs with bold keywords"
        }
        
        preferences.preferredExplanationDepth = when {
            preferences.technicalLevelScore < 0.40 -> "Layman explanations using helpful metaphors"
            preferences.technicalLevelScore > 0.80 -> "Rigorous, highly technical breakdowns with mathematical/code precision"
            else -> "Structured breakdowns with clear risk-benefit summaries"
        }
        
        preferences.preferredTone = when {
            preferences.formalityPreference < 0.45 -> "Relaxed, friendly and informal"
            preferences.formalityPreference > 0.75 -> "Charismatic, professional and business-focused"
            else -> "Balanced, supportive and professional"
        }
    }

    fun isTransient(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower == "hi" || lower == "hello" || lower == "hey" || lower == "thanks" || lower == "ok" || lower == "yes" || lower == "no"
    }
}
