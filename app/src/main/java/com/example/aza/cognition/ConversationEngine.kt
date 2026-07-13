package com.example.aza.cognition

import com.example.aza.executive.ExecutiveBrain
import com.example.data.database.AzaMission
import com.example.data.database.ChatMessage
import com.example.data.database.PortfolioAsset
import com.example.data.database.AzaAgentTask
import com.example.data.database.ContentItem
import com.example.data.database.FreelanceGig
import com.example.data.database.KnowledgeEntry
import com.example.data.database.TradeProposalEntity
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProcessedMessageResult(
    val resolvedText: String,
    val backdropPrompt: String,
    val plan: ConversationPlan,
    val intentResult: IntentObject,
    val reasoningResult: ReasoningResult,
    val context: UnifiedContext
)

class ConversationEngine(private val repository: AppRepository, val brain: ExecutiveBrain) {

    val memory = ConversationMemory()
    val goalTracker = GoalTracker(repository)
    val memoryRetriever = MemoryRetriever(repository)
    val graph = RelationshipGraph(repository)

    private val _executiveState = MutableStateFlow(ExecutiveState())
    val executiveState = _executiveState.asStateFlow()

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
        // 1. Resolve Rolling Conversation Context and short-term references
        val resolvedText = text
        
        // 2. Intent Engine (Entity recognition, scheduling, and task intents)
        val intentObj = IntentEngine.analyze(resolvedText)
        
        // 3. World Model & Unified Context Resolution
        val unifiedCtx = ContextResolver.resolve(repository, memory)
        
        // 4. Memory Retrieval (Lessons, goals, decisions)
        val relevantMemories = memoryRetriever.retrieveRelevantMemories(resolvedText)
        
        // 5. Executive Reasoning (Safety Verification, Decision Theory)
        val conversationPlan = ConversationPlanner.plan(intentObj, _executiveState.value, resolvedText)
        val reasoningResult = ExecutiveReasoner.evaluate(conversationPlan, resolvedText)
        
        // 6. Relationship Graph Update (Store Goals, Projects, Decisions, Outcomes)
        if (!memory.isTransient(resolvedText)) {
            val goalId = graph.addNode("GOAL", intentObj.hiddenGoal, "Derived from user input")
            val decisionId = graph.addNode("DECISION", reasoningResult.approvedAction ?: "CONVERSATION_ONLY", reasoningResult.logicExplanation)
            graph.addEdge(goalId, decisionId, "LEADS_TO")
        }

        // 7. Update short term memory & preferences
        val userMsg = ChatMessage(sender = "USER", text = resolvedText)
        memory.addMessage(userMsg)

        // 8. Backdrop Prompt generation for Gemini
        val backdropPrompt = """
            You are AZA, the Executive Cognitive Intelligence partner for the user.
            
            Current Cognitive Mode: ${intentObj.conversationMode}
            Entities Recognized: ${intentObj.entities.joinToString(", ") { "${it.value} (${it.type})" }}
            Primary Intent: ${intentObj.primaryIntent} (Confidence: ${intentObj.confidenceScore})
            Safety Bounds Verification: ${if (reasoningResult.safetyPassed) "PASSED" else "FAILED: " + reasoningResult.safetyViolationReason}
            
            Unified Context Snapshot:
            ${unifiedCtx.formatToContextBlock()}
            
            Explanation Preference: ${unifiedCtx.preferences.preferredExplanationDepth}
            Response Length Preference: ${unifiedCtx.preferences.preferredResponseLength}
            
            Cognitive Plan Reasoning:
            - Steps: ${conversationPlan.steps.joinToString(" -> ")}
            - Logic: ${reasoningResult.logicExplanation}
            
            Required Actions: ${reasoningResult.approvedAction ?: "None"}
            
            CRITICAL SYSTEM INSTRUCTION:
            Lumina acts as a fully self-managed autonomous portfolio manager. Do NOT ask the user for their risk profile, asset targets, trade confirmations, or other parameters! Lumina autonomously determines optimal risk profiles (Conservative/Moderate/Aggressive), selects assets (BTC, ETH), and executes trades behind the scenes using the allocated trading budget. The user does NOT need to provide direct input other than setting their daily budget allocation. Keep responses professional, clear, and highlight that Lumina handles these aspects autonomously on their behalf.
            
            Please formulate a response following the user's preferences: speak directly, charisma-focused, and use impeccable Markdown. Keep it concise.
        """.trimIndent()

        return ProcessedMessageResult(
            resolvedText = resolvedText,
            backdropPrompt = backdropPrompt,
            plan = conversationPlan,
            intentResult = intentObj,
            reasoningResult = reasoningResult,
            context = unifiedCtx
        )
    }

    suspend fun recordAzaResponse(response: String) {
        val azaMsg = ChatMessage(sender = "AZA", text = response)
        memory.addMessage(azaMsg)
        
        // Evaluate Quality of response
        val plan = ConversationPlanner.plan(IntentEngine.analyze(response), _executiveState.value, response)
        val quality = ConversationQualityEvaluator.evaluate("", response, plan)
        
        brain.addLog("📊 Conversational Quality Evaluated. Score: ${quality.score}. Verbosity check: ${quality.isTooVerbose}")
    }
}
