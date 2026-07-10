package com.example.aza.conversation

import com.example.data.database.ChatMessage

data class RelationshipProfile(
    var preferredCommunicationStyle: String = "Analytical, charismatic, and business-focused",
    val longTermGoals: MutableList<String> = mutableListOf("Grow paper-trading asset values", "Automate video creation and freelance bids safely"),
    val recurringProjects: MutableList<String> = mutableListOf("AI Developer Tutorials", "BTC/ETH Spot Trading"),
    var preferredExplanations: String = "Structured breakdowns with clear risk-benefit summaries",
    val previousDecisions: MutableList<String> = mutableListOf("Enable Paper Execution initially"),
    val recurringQuestions: MutableList<String> = mutableListOf(),
    val collaborationHistory: MutableList<String> = mutableListOf("Successfully configured the multi-agent consensus system"),

    // Dynamic APC parameters (Adaptive Preference Calibration)
    var technicalLevelScore: Double = 0.70,     // 0.0 (Layman) -> 1.0 (Deep Math/Theory)
    var verbosityPreference: Double = 0.60,    // 0.0 (Brief/Bullet points) -> 1.0 (Detailed breakdown)
    var formalityPreference: Double = 0.80,    // 0.0 (Casual/Friendly) -> 1.0 (Strict/Professional)
    var decisionControlPreference: Double = 0.50, // 0.0 (Manual/Approval-heavy) -> 1.0 (Autopilot/Autonomous)
    
    // Additional Calibration metrics
    var totalAdaptiveCycles: Int = 0,
    var lastCalibratedAt: Long = 0L
)

class ConversationMemory {

    val relationshipProfile = RelationshipProfile()
    
    // Rolling Conversational Context
    private val _recentMessages = mutableListOf<ChatMessage>()
    val recentMessages: List<ChatMessage> get() = _recentMessages

    fun setMessages(messages: List<ChatMessage>) {
        _recentMessages.clear()
        _recentMessages.addAll(messages.takeLast(20))
    }

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
            // Run continuous Adaptive Preference Calibration
            runAdaptivePreferenceCalibration(message.text)

            // Update goals if mentioned
            if (text.contains("my goal is") || text.contains("i want to achieve")) {
                val goal = message.text.substringAfter("goal is", "").substringAfter("achieve", "").trim()
                if (goal.isNotEmpty() && !relationshipProfile.longTermGoals.contains(goal)) {
                    relationshipProfile.longTermGoals.add(goal)
                }
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

    /**
     * Run linguistic analysis to calibrate style preferences (technical level, verbosity, formality, autopilot control).
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
        relationshipProfile.technicalLevelScore = (relationshipProfile.technicalLevelScore + techShift).coerceIn(0.10, 1.0)
        
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
        relationshipProfile.verbosityPreference = (relationshipProfile.verbosityPreference + verbosityShift).coerceIn(0.15, 1.0)
        
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
        relationshipProfile.formalityPreference = (relationshipProfile.formalityPreference + formalityShift).coerceIn(0.20, 1.0)
        
        // --- 4. Decision Control Preference Calibration (Autopilot vs Manual Approval)
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
        relationshipProfile.decisionControlPreference = (relationshipProfile.decisionControlPreference + decisionShift).coerceIn(0.10, 1.0)
        
        relationshipProfile.totalAdaptiveCycles++
        relationshipProfile.lastCalibratedAt = System.currentTimeMillis()
        
        // --- 5. Generate Natural Language Descriptive Summaries for Prompts ---
        relationshipProfile.preferredCommunicationStyle = when {
            relationshipProfile.verbosityPreference < 0.35 -> "Highly concise, direct and brief"
            relationshipProfile.verbosityPreference > 0.75 -> "Extremely detailed, in-depth and thorough"
            else -> "Analytical, charismatic, and business-focused"
        }
        
        relationshipProfile.preferredExplanations = when {
            relationshipProfile.technicalLevelScore < 0.40 -> "Layman explanations with helpful metaphors"
            relationshipProfile.technicalLevelScore > 0.80 -> "Rigorous, highly technical breakdowns with mathematical/code precision"
            else -> "Structured breakdowns with clear risk-benefit summaries"
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
