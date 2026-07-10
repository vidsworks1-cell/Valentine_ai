package com.example.aza.conversation

import com.example.aza.executive.ExecutiveBrain
import com.example.data.database.*
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import com.example.aza.cognition.ConversationEngine as CognitionConversationEngine

class ConversationEngine(private val repository: AppRepository, val brain: ExecutiveBrain) {

    val memory = ConversationMemory()
    val cognitionEngine = CognitionConversationEngine(repository, brain)

    private val _dialogueState = MutableStateFlow(DialogueState())
    val dialogueState = _dialogueState.asStateFlow()

    suspend fun loadHistory() {
        try {
            val messages = repository.getAllChatMessagesDirect()
            memory.setMessages(messages)
            cognitionEngine.memory.setMessages(messages)
            
            // Set the unresolved questions and previous explanations if any
            if (messages.isNotEmpty()) {
                val lastAza = messages.findLast { it.sender == "AZA" }?.text
                _dialogueState.value = _dialogueState.value.copy(
                    lastAzaMessage = lastAza ?: "",
                    previousExplanation = lastAza ?: ""
                )
            }
        } catch (e: Exception) {
            brain.addLog("⚠️ Failed to load conversational memory from DB: ${e.localizedMessage}")
        }
    }

    /**
     * Entry point to process a new message from the user.
     * Delegates fully to our premium executive cognition module.
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
        
        // 1. Process via Executive Cognition Engine
        val cognitionResult = cognitionEngine.processUserMessage(
            text = text,
            portfolioCash = portfolioCash,
            holdings = holdings,
            contentQueue = contentQueue,
            openGigs = openGigs,
            pendingTrades = pendingTrades,
            pendingTasks = pendingTasks,
            activeMissions = activeMissions,
            knowledge = knowledge,
            actionExecutedNotice = actionExecutedNotice
        )

        // 2. Perform original context resolution for UI compatibility
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

        // 3. Map cognition results into original IntentAnalysisResult & EmotionAnalysisResult
        val intentResult = IntentAnalysisResult(
            primaryIntent = when (cognitionResult.intentResult.primaryIntent) {
                "TRADING_EXECUTION" -> IntentType.TRADING_ADVICE
                "PORTFOLIO_REVIEW" -> IntentType.EXECUTIVE_DECISION
                "CONTENT_PLANNING" -> IntentType.CONTENT_PLANNING
                "FREELANCE_PLANNING" -> IntentType.FREELANCE_PLANNING
                "TASK_MANAGEMENT" -> IntentType.PLANNING
                "EXPLANATION" -> IntentType.EXPLANATION
                "SYSTEM_DIAGNOSTICS" -> IntentType.SYSTEM_CONTROL
                "EMOTIONAL_FEEDBACK" -> IntentType.FEEDBACK
                else -> IntentType.CASUAL
            },
            confidenceScores = mapOf(),
            explanation = "Executive Goal Alignment: " + cognitionResult.intentResult.hiddenGoal
        )

        val emotionResult = EmotionAnalysisResult(
            primaryEmotion = when (cognitionResult.intentResult.emotionalTone) {
                "FOCUSED" -> EmotionType.FOCUSED
                "UNCERTAIN" -> EmotionType.UNCERTAIN
                "CALM" -> EmotionType.CALM
                else -> EmotionType.CALM
            },
            scores = mapOf(),
            description = "Executive Tone Level: " + cognitionResult.intentResult.emotionalTone
        )

        // 4. Construct original ConversationPlan based on executive cognitive step traces
        val plan = ConversationPlan(
            actualQuestion = text,
            missingInformation = if (cognitionResult.plan.isClarificationRequired) cognitionResult.plan.missingParameters.firstOrNull() else null,
            relevantMemories = emptyList(),
            contributingAgents = cognitionResult.plan.contributingAgents,
            isClarificationRequired = cognitionResult.plan.isClarificationRequired,
            isActionRequired = cognitionResult.plan.isActionExecutionRecommended,
            isUserSimplyTalking = cognitionResult.intentResult.primaryIntent == "CASUAL",
            reasoningSteps = cognitionResult.plan.steps + cognitionResult.reasoningResult.logicExplanation
        )

        // 5. Update DialogueState
        val newState = DialogueState(
            conversationId = _dialogueState.value.conversationId,
            activeTopics = if (cognitionResult.intentResult.primaryIntent != "CASUAL") {
                (_dialogueState.value.activeTopics + cognitionResult.intentResult.primaryIntent).distinct().takeLast(5)
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
        val userMsg = ChatMessage(sender = "USER", text = text)
        memory.addMessage(userMsg)
        memory.updateTopics(newState.activeTopics)

        // 7. Run adaptation checks
        evaluateLearningCycle(text, intentResult, emotionResult, plan, context)

        return ProcessedMessageResult(
            resolvedText = text,
            backdropPrompt = cognitionResult.backdropPrompt,
            plan = plan,
            intentResult = intentResult,
            emotionResult = emotionResult,
            context = context
        )
    }

    /**
     * Record the finalized AZA response back to the Dialogue State and memory
     */
    suspend fun recordAzaResponse(response: String) {
        val azaMsg = ChatMessage(sender = "AZA", text = response)
        memory.addMessage(azaMsg)
        _dialogueState.value = _dialogueState.value.copy(
            lastAzaMessage = response,
            previousExplanation = response
        )
        cognitionEngine.recordAzaResponse(response)
    }

    /**
     * Continuous Learning Evaluation
     */
    private suspend fun evaluateLearningCycle(
        text: String,
        intent: IntentAnalysisResult,
        emotion: EmotionAnalysisResult,
        plan: ConversationPlan,
        context: UnifiedContext
    ) {
        val lower = text.lowercase(Locale.US)
        
        if (intent.primaryIntent == IntentType.CORRECTION) {
            brain.addLog("⚠️ Conversational Correction detected from user. Adapting heuristics...")
            
            val correctionTopic = _dialogueState.value.activeTopics.lastOrNull() ?: "General"
            val title = "Conversational Correction: $correctionTopic"
            
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
            
            memory.relationshipProfile.previousDecisions.add("Correction on $correctionTopic: Avoid repeating mismatched assertions")
        }

        if (plan.isClarificationRequired) {
            brain.addLog("ℹ️ Clarification requested on missing parameter: ${plan.missingInformation}")
            memory.relationshipProfile.recurringQuestions.add(plan.missingInformation ?: "context")
        }

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
