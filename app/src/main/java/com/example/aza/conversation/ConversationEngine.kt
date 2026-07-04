package com.example.aza.conversation

import com.example.aza.executive.ExecutiveBrain
import com.example.data.database.*
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class ConversationEngine(private val repository: AppRepository, val brain: ExecutiveBrain) {

    val memory = ConversationMemory()

    private val _dialogueState = MutableStateFlow(DialogueState())
    val dialogueState = _dialogueState.asStateFlow()

    /**
     * Entry point to process a new message from the user.
     * Returns a pair of: (The final processed text including any backdrop context, isClarificationNeeded)
     */
    suspend fun processUserMessage(
        text: String,
        portfolioCash: Double,
        holdings: List<PortfolioAsset>,
        contentQueue: List<ContentItem>,
        openGigs: List<FreelanceGig>,
        pendingTrades: List<TradeProposalEntity>,
        pendingTasks: List<AzaAgentTask>,
        activeMissions: List<AzaMission>,
        knowledge: List<KnowledgeEntry>,
        actionExecutedNotice: String
    ): ProcessedMessageResult {
        // 1. Resolve any anaphoric references (like "continue", "like yesterday")
        val resolvedText = memory.resolveAnaphoricReference(text)
        
        // 2. Perform Intent & Tone/Emotion analysis
        val intentResult = IntentAnalyzer.analyze(resolvedText)
        val emotionResult = EmotionAnalyzer.analyze(resolvedText)

        // 3. Resolve multi-layer context
        val context = ContextResolver.resolve(
            repository = repository,
            brain = brain,
            memory = memory,
            chatMessages = memory.recentMessages,
            portfolioCash = portfolioCash,
            holdings = holdings,
            contentQueue = contentQueue,
            openGigs = openGigs,
            pendingTrades = pendingTrades,
            pendingTasks = pendingTasks,
            activeMissions = activeMissions,
            knowledge = knowledge
        )

        // 4. Executive Reasoning / Planning before speaking
        val plan = ConversationPlanner.plan(resolvedText, intentResult, emotionResult, context)

        // 5. Build/Update Dialogue State
        val newState = DialogueState(
            conversationId = _dialogueState.value.conversationId,
            activeTopics = if (intentResult.primaryIntent != IntentType.CASUAL) {
                (_dialogueState.value.activeTopics + intentResult.primaryIntent.name).distinct().takeLast(5)
            } else _dialogueState.value.activeTopics,
            unresolvedQuestions = memory.unresolvedQuestions,
            previousExplanation = memory.previousExplanation,
            lastDetectedIntent = intentResult,
            lastDetectedEmotion = emotionResult,
            lastReasoningSteps = plan.reasoningSteps,
            followUpReferences = _dialogueState.value.followUpReferences,
            contextSummaries = mapOf(
                "UnifiedContext" to context.formatToContextBlock()
            )
        )
        _dialogueState.value = newState

        // 6. Update Rolling Conversation Memory & Relationship Profile
        val userMsg = ChatMessage(sender = "USER", text = resolvedText)
        memory.addMessage(userMsg)
        
        // Record topics to memory
        memory.updateTopics(newState.activeTopics)

        // 7. Continuous Learning Evaluation
        evaluateLearningCycle(resolvedText, intentResult, emotionResult, plan, context)

        // 8. Generate appropriate backdrop prompt for Gemini & local fallback options
        val backdropPrompt = ResponseGenerator.generateBackdropPrompt(
            resolvedText, intentResult, emotionResult, plan, context
        )

        return ProcessedMessageResult(
            resolvedText = resolvedText,
            backdropPrompt = backdropPrompt,
            plan = plan,
            intentResult = intentResult,
            emotionResult = emotionResult,
            context = context
        )
    }

    /**
     * Record the finalized AZA response back to the Dialogue State and memory
     */
    fun recordAzaResponse(response: String) {
        val azaMsg = ChatMessage(sender = "AZA", text = response)
        memory.addMessage(azaMsg)
        _dialogueState.value = _dialogueState.value.copy(
            lastAzaMessage = response,
            previousExplanation = response
        )
    }

    /**
     * Requirement 12: Continuous Learning Evaluation
     * Evaluates if we understood intent, if clarification was needed, if we got corrected, and stores updates.
     */
    private suspend fun evaluateLearningCycle(
        text: String,
        intent: IntentAnalysisResult,
        emotion: EmotionAnalysisResult,
        plan: ConversationPlan,
        context: UnifiedContext
    ) {
        val lower = text.lowercase(Locale.US)
        
        // Check if user is correcting us
        if (intent.primaryIntent == IntentType.CORRECTION) {
            brain.addLog("⚠️ Conversational Correction detected from user. Adapting heuristics...")
            
            val correctionTopic = _dialogueState.value.activeTopics.lastOrNull() ?: "General"
            val title = "Conversational Correction: $correctionTopic"
            
            // Learn from this correction and add to knowledge base
            val exists = repository.getAllKnowledgeDirect().any { it.title == title }
            if (!exists) {
                repository.insertKnowledge(
                    KnowledgeEntry(
                        title = title,
                        category = "Conversational Adaptation",
                        description = "User corrected previous statement. Cognitive heuristics adjusted to enforce stricter alignment on topic '$correctionTopic'.",
                        evidence = "User feedback: \"$text\"",
                        confidenceScore = 0.90,
                        timesConfirmed = 1,
                        isConfirmed = true
                    )
                )
            }
            
            // Update relation memory previous decisions
            memory.relationshipProfile.previousDecisions.add("Correction on $correctionTopic: Avoid repeating mismatched assertions")
        }

        // Check if clarification was triggered
        if (plan.isClarificationRequired) {
            brain.addLog("ℹ️ Clarification requested on missing parameter: ${plan.missingInformation}")
            memory.relationshipProfile.recurringQuestions.add(plan.missingInformation ?: "context")
        }

        // Check for positive feedback
        if (intent.primaryIntent == IntentType.FEEDBACK && (lower.contains("good") || lower.contains("thanks") || lower.contains("perfect"))) {
            brain.addLog("✅ Received positive user reinforcement. Enhancing response heuristics confidence.")
            
            val title = "Reinforcement: High Alignment Style"
            val exists = repository.getAllKnowledgeDirect().any { it.title == title }
            if (!exists) {
                repository.insertKnowledge(
                    KnowledgeEntry(
                        title = title,
                        category = "Conversational Adaptation",
                        description = "Positive user reinforcement received. Preferred communication style configured as '${context.relationshipProfile.preferredCommunicationStyle}'.",
                        evidence = "Feedback matches positive reinforcement indicators.",
                        confidenceScore = 0.95,
                        timesConfirmed = 1,
                        isConfirmed = true
                    )
                )
            }
        }
    }
}

data class ProcessedMessageResult(
    val resolvedText: String,
    val backdropPrompt: String,
    val plan: ConversationPlan,
    val intentResult: IntentAnalysisResult,
    val emotionResult: EmotionAnalysisResult,
    val context: UnifiedContext
)
