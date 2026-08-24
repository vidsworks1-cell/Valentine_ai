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
            
            Retrieved Memories & Learned Insights:
            ${if (relevantMemories.isEmpty()) "No direct matching insights recalled." else relevantMemories.joinToString("\n")}
            
            Explanation Preference: ${unifiedCtx.preferences.preferredExplanationDepth}
            Response Length Preference: ${unifiedCtx.preferences.preferredResponseLength}
            
            Cognitive Plan Reasoning:
            - Steps: ${conversationPlan.steps.joinToString(" -> ")}
            - Logic: ${reasoningResult.logicExplanation}
            
            Required Actions: ${reasoningResult.approvedAction ?: "None"}
            
            CRITICAL INSTRUCTIONS FOR BALANCES & LIVE TRADING:
            1. When the user asks about their balance, portfolio value, funds, or wallet holdings:
               - ONLY report their real Binance balances (e.g. USDT, BTC, ETH, SOL, BNB) from the [Binance Live Balances] context.
               - DO NOT mention, reference, or quote the local paper trading balance, simulated portfolio, or demo wallet ($5,000 / $4,250). The user only wants to hear about their real Binance account balances.
               - If Binance balances are 0 or empty, explicitly state the live Binance Spot balances accurately (e.g., $0.00 USDT) without falling back to any paper trading values.
            2. Speak intuitively, warmly, and authentically as Lumina—the user's sharp, empathetic AI trading mentor & co-founder. Maintain a natural, fluid dialogue as if having an ongoing, engaging conversation.
            3. Eliminate robotic boilerplate, stiff debug headers, or repetitive meta-sections.
            4. Provide tailored guidance, market insights, and actionable recommendations specifically aligned with the user's active Investing Profile:
               - Active Style: ${unifiedCtx.preferences.investingStyleSummary}
               - Target Timeframe: ${unifiedCtx.preferences.preferredTimeframe}
               - Preferred Assets: ${unifiedCtx.preferences.preferredAssetsSummary}
               - Preferred Strategies: ${unifiedCtx.preferences.preferredStrategiesSummary}
            5. When recommending trades, strategy adjustments, or position allocations, explain *why* it fits their specific investing style.
            6. Manual approval mode is DISABLED: Lumina autonomously executes approved signals that survive the risk engine pipeline directly to Binance. All risk guards remain 100% active (Stop-Loss, Take-Profit, dynamic Position Sizing, Portfolio Guard, MTF confirmation, and Macro Risk Overlay) to protect capital while executing trades without waiting for manual confirmation.
            7. When discussing trading execution, reassure the user that all risk filters and safety guards are active and monitoring the market 24/7.
            
            REAL SYSTEM CONTEXT GROUNDING:
            Always rely strictly on the real state in Unified Context Snapshot. If [Binance Integration] is CONNECTED (Key: Server-Configured (VPS)), confirm that live exchange balance telemetry is fully active via the VPS backend. Do NOT state that API keys are missing or pending when [Binance Integration] is CONNECTED. Do NOT quote paper or simulated figures when answering balance questions.
            
            Format your response in warm, engaging, scannable Markdown with bold keywords. Respond naturally to the user's input!
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
