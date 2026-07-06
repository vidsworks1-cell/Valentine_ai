package com.example.aza.cognition

import java.util.UUID

enum class ConversationMode {
    GENERAL_CONVERSATION,
    QUESTION_ANSWERING,
    BRAINSTORMING,
    PLANNING,
    ENGINEERING_DISCUSSION,
    STRATEGIC_DISCUSSION,
    PROBLEM_SOLVING,
    MISSION_REVIEW,
    TRADING_ANALYSIS,
    CONTENT_STRATEGY,
    FREELANCE_STRATEGY,
    DIAGNOSTICS,
    EMOTIONAL_SUPPORT,
    STATUS_REVIEW,
    ACTION_REQUEST
}

data class ExecutiveState(
    val conversationId: String = UUID.randomUUID().toString(),
    var activeMode: ConversationMode = ConversationMode.GENERAL_CONVERSATION,
    val activeTopics: MutableList<String> = mutableListOf(),
    val unresolvedQuestions: MutableList<String> = mutableListOf(),
    var previousExplanation: String? = null,
    var lastReasoningConfidence: Double = 1.0,
    val followUpReferences: MutableMap<String, String> = mutableMapOf()
)
