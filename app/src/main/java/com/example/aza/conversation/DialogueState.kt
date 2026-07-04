package com.example.aza.conversation

import java.util.UUID

enum class IntentType {
    QUESTION,
    REQUEST,
    PLANNING,
    EXPLANATION,
    CORRECTION,
    FEEDBACK,
    CASUAL,
    PERSONAL,
    TRADING_ADVICE,
    CONTENT_PLANNING,
    FREELANCE_PLANNING,
    SYSTEM_CONTROL,
    EXECUTIVE_DECISION
}

enum class EmotionType {
    EXCITED,
    FRUSTRATED,
    UNCERTAIN,
    CURIOUS,
    FOCUSED,
    CELEBRATORY,
    CALM
}

data class IntentAnalysisResult(
    val primaryIntent: IntentType,
    val confidenceScores: Map<IntentType, Double>,
    val explanation: String
)

data class EmotionAnalysisResult(
    val primaryEmotion: EmotionType,
    val scores: Map<EmotionType, Double>,
    val description: String
)

data class DialogueState(
    val conversationId: String = UUID.randomUUID().toString(),
    val activeTopics: List<String> = emptyList(),
    val unresolvedQuestions: List<String> = emptyList(),
    val previousExplanation: String? = null,
    val lastDetectedIntent: IntentAnalysisResult? = null,
    val lastDetectedEmotion: EmotionAnalysisResult? = null,
    val lastReasoningSteps: List<String> = emptyList(),
    val followUpReferences: Map<String, String> = emptyMap(),
    val lastAzaMessage: String? = null,
    val contextSummaries: Map<String, String> = emptyMap()
)
