package com.example.aza.conversation

data class ConversationPlan(
    val actualQuestion: String,
    val missingInformation: String?,
    val relevantMemories: List<String>,
    val contributingAgents: List<String>,
    val isClarificationRequired: Boolean,
    val isActionRequired: Boolean,
    val isUserSimplyTalking: Boolean,
    val reasoningSteps: List<String>
)

object ConversationPlanner {

    fun plan(
        userInput: String,
        intentResult: IntentAnalysisResult,
        emotionResult: EmotionAnalysisResult,
        context: UnifiedContext
    ): ConversationPlan {
        val steps = mutableListOf<String>()
        steps.add("Started cognitive reasoning for input: \"$userInput\"")
        steps.add("Step 1: Analyzed user intent as ${intentResult.primaryIntent} with ${(intentResult.confidenceScores[intentResult.primaryIntent] ?: 0.0) * 100}% confidence.")
        steps.add("Step 2: Identified tone as ${emotionResult.primaryEmotion}.")

        val relevantMemories = mutableListOf<String>()
        val contributingAgents = mutableListOf<String>()
        var isClarificationRequired = false
        var isActionRequired = false
        var isUserSimplyTalking = false
        var missingInfo: String? = null

        // Context Matching & Memory retrieval
        if (context.relationshipProfile.longTermGoals.isNotEmpty()) {
            relevantMemories.add("Goal: ${context.relationshipProfile.longTermGoals.first()}")
        }
        
        // Detect Contributing Agents
        when (intentResult.primaryIntent) {
            IntentType.TRADING_ADVICE -> {
                contributingAgents.add("TradingAgent")
                contributingAgents.add("RiskAgent")
                steps.add("Step 3: Designated TradingAgent and RiskAgent as core expert contributors.")
            }
            IntentType.CONTENT_PLANNING -> {
                contributingAgents.add("ContentAgent")
                steps.add("Step 3: Designated ContentAgent as core expert contributor.")
            }
            IntentType.FREELANCE_PLANNING -> {
                contributingAgents.add("FreelanceAgent")
                contributingAgents.add("ResearchAgent")
                steps.add("Step 3: Designated FreelanceAgent and ResearchAgent as core expert contributors.")
            }
            IntentType.QUESTION -> {
                contributingAgents.add("ResearchAgent")
                contributingAgents.add("KnowledgeAgent")
                steps.add("Step 3: Querying Knowledge Base and Search Index for factual insights.")
            }
            IntentType.REQUEST -> {
                isActionRequired = true
                steps.add("Step 3: Scanned input for active executable commands.")
                
                // Check if action matches but is missing critical params
                val lower = userInput.lowercase()
                if (lower.contains("buy") || lower.contains("sell")) {
                    contributingAgents.add("TradingAgent")
                    val hasAmount = lower.contains(Regex("""\d+"""))
                    val hasSymbol = lower.contains("btc") || lower.contains("eth") || lower.contains("aapl") || lower.contains("nvda")
                    if (!hasAmount || !hasSymbol) {
                        isClarificationRequired = true
                        missingInfo = if (!hasSymbol) "Asset Symbol (e.g. BTC, AAPL)" else "Transaction Amount/Size"
                        steps.add("Step 4: CRITICAL AMBIGUITY: Request lacks essential trading parameters ($missingInfo). Initiating Clarification Protocol.")
                    }
                } else if (lower.contains("script") || lower.contains("content")) {
                    contributingAgents.add("ContentAgent")
                    val hasPlatform = lower.contains("tiktok") || lower.contains("youtube") || lower.contains("instagram")
                    if (!hasPlatform) {
                        isClarificationRequired = true
                        missingInfo = "Target Social Platform (e.g. TikTok, YouTube)"
                        steps.add("Step 4: Context missing target media channel. Initiating platform clarification.")
                    }
                } else if (lower.contains("apply") || lower.contains("proposal")) {
                    contributingAgents.add("FreelanceAgent")
                }
            }
            IntentType.CASUAL, IntentType.PERSONAL -> {
                isUserSimplyTalking = true
                contributingAgents.add("DiagnosticsAgent")
                steps.add("Step 3: Casual interaction. Prioritizing relation continuity over analytical audits.")
            }
            else -> {
                contributingAgents.add("DiagnosticsAgent")
                steps.add("Step 3: Routing general query to DiagnosticsAgent.")
            }
        }

        if (!isClarificationRequired) {
            steps.add("Step 4: Verified complete input alignment. No critical information missing.")
        }
        
        steps.add("Step 5: Synthesizing final multi-agent output package.")

        return ConversationPlan(
            actualQuestion = userInput,
            missingInformation = missingInfo,
            relevantMemories = relevantMemories,
            contributingAgents = contributingAgents,
            isClarificationRequired = isClarificationRequired,
            isActionRequired = isActionRequired,
            isUserSimplyTalking = isUserSimplyTalking,
            reasoningSteps = steps
        )
    }
}
