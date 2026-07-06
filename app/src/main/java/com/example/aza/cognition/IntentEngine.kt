package com.example.aza.cognition

import java.util.Locale

data class Entity(
    val value: String,
    val type: String // NAME, DATE, LOCATION, ORGANIZATION
)

data class IntentObject(
    val primaryIntent: String,
    val secondaryIntents: List<String>,
    val entities: List<Entity>,
    val hiddenGoal: String,
    val emotionalTone: String,
    val urgency: String, // HIGH, MEDIUM, LOW
    val conversationMode: ConversationMode,
    val executionRequirement: Boolean,
    val confidenceScore: Double
)

object IntentEngine {

    fun analyze(text: String): IntentObject {
        val lower = text.lowercase(Locale.US).trim()
        val entities = mutableListOf<Entity>()
        
        // Entity Extraction
        val dateRegex = """\b\d{4}-\d{2}-\d{2}\b|\b(?:today|tomorrow|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""".toRegex(RegexOption.IGNORE_CASE)
        dateRegex.findAll(text).forEach {
            entities.add(Entity(it.value, "DATE"))
        }
        
        val orgRegex = """\b(?:Binance|YouTube|TikTok|Instagram|Fiverr|Upwork|Stripe|Google|Aza|Atlas)\b""".toRegex(RegexOption.IGNORE_CASE)
        orgRegex.findAll(text).forEach {
            entities.add(Entity(it.value, "ORGANIZATION"))
        }

        val locationRegex = """\b(?:USDT|USD|BTC|ETH|AAPL|NVDA|London|New York|Crypto|Spot)\b""".toRegex()
        locationRegex.findAll(text).forEach {
            entities.add(Entity(it.value, "LOCATION"))
        }

        var primaryIntent = "CASUAL"
        val secondaryIntents = mutableListOf<String>()
        var mode = ConversationMode.GENERAL_CONVERSATION
        var execution = false
        var confidence = 0.85
        var hiddenGoal = "Maintain pleasant interaction"
        var urgency = "LOW"
        var emotionalTone = "CALM"

        if (lower.contains("buy") || lower.contains("sell") || lower.contains("deposit") || lower.contains("execute") || lower.contains("trade")) {
            primaryIntent = "TRADING_EXECUTION"
            secondaryIntents.add("RISK_AUDIT")
            mode = ConversationMode.TRADING_ANALYSIS
            execution = true
            hiddenGoal = "Optimize portfolio allocations and grow trading capital safely"
            urgency = "HIGH"
            emotionalTone = "FOCUSED"
            confidence = 0.95
        } else if (lower.contains("portfolio") || lower.contains("balance") || lower.contains("how is my")) {
            primaryIntent = "PORTFOLIO_REVIEW"
            mode = ConversationMode.STATUS_REVIEW
            hiddenGoal = "Verify financial health and capital safety"
            confidence = 0.90
        } else if (lower.contains("video") || lower.contains("script") || lower.contains("tiktok") || lower.contains("youtube") || lower.contains("content")) {
            primaryIntent = "CONTENT_PLANNING"
            mode = ConversationMode.CONTENT_STRATEGY
            hiddenGoal = "Accelerate digital growth and develop YouTube channel"
            confidence = 0.92
        } else if (lower.contains("gig") || lower.contains("freelance") || lower.contains("proposal") || lower.contains("client")) {
            primaryIntent = "FREELANCE_PLANNING"
            mode = ConversationMode.FREELANCE_STRATEGY
            hiddenGoal = "Maximize service monetization and freelance revenue"
            confidence = 0.92
        } else if (lower.contains("plan") || lower.contains("roadmap") || lower.contains("schedule") || lower.contains("next step") || lower.contains("milestone")) {
            primaryIntent = "TASK_MANAGEMENT"
            mode = ConversationMode.PLANNING
            hiddenGoal = "Organize upcoming milestones and tasks systematically"
            confidence = 0.88
        } else if (lower.contains("explain") || lower.contains("why did") || lower.contains("how does") || lower.contains("reasoning")) {
            primaryIntent = "EXPLANATION"
            mode = ConversationMode.QUESTION_ANSWERING
            hiddenGoal = "Understand system reasoning and technical architecture details"
            confidence = 0.90
        } else if (lower.contains("review yourself") || lower.contains("diagnose") || lower.contains("health") || lower.contains("status")) {
            primaryIntent = "SYSTEM_DIAGNOSTICS"
            mode = ConversationMode.DIAGNOSTICS
            hiddenGoal = "Audit operational state and improve Aza capabilities"
            confidence = 0.94
        } else if (lower.contains("worry") || lower.contains("nervous") || lower.contains("afraid") || lower.contains("happy")) {
            primaryIntent = "EMOTIONAL_FEEDBACK"
            mode = ConversationMode.EMOTIONAL_SUPPORT
            hiddenGoal = "Provide cognitive comfort and strategic alignment"
            emotionalTone = "UNCERTAIN"
            confidence = 0.80
        }

        return IntentObject(
            primaryIntent = primaryIntent,
            secondaryIntents = secondaryIntents,
            entities = entities,
            hiddenGoal = hiddenGoal,
            emotionalTone = emotionalTone,
            urgency = urgency,
            conversationMode = mode,
            executionRequirement = execution,
            confidenceScore = confidence
        )
    }
}
