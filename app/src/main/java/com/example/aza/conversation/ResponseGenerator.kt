package com.example.aza.conversation

import java.util.Locale
import kotlin.random.Random

object ResponseGenerator {

    private val GREETINGS = listOf(
        "Hello! Great to connect with you again.",
        "Hey there. Let's look over our active business channels.",
        "Aza active and ready. Let's analyze our progress.",
        "Hello! I am ready to review our operations.",
        "Hey! Let's check our recent updates."
    )

    private val TRANSITIONS = listOf(
        "Reviewing the real-time status of our operations...",
        "Evaluating our performance indicators...",
        "Regarding our long-term business roadmap...",
        "Taking a close look at our current balance and portfolios...",
        "Focusing directly on our active goals..."
    )

    private val CONCLUSIONS = listOf(
        "Let me know if you would like me to draft any new actions or analyze specific market charts!",
        "Let's keep driving progress. What should we tackle next?",
        "I'm keeping a constant watch on these feeds. Let me know your next move!",
        "What are your thoughts on this direction? We can tweak the strategy anytime.",
        "Let me know if you need more details on any of these points!"
    )

    fun generateBackdropPrompt(
        userInput: String,
        intentResult: IntentAnalysisResult,
        emotionResult: EmotionAnalysisResult,
        plan: ConversationPlan,
        context: UnifiedContext
    ): String {
        return """
            You are Aza, the user's conversational, highly intelligent Business & Trading partner.
            
            Current Dialogue Analysis:
            - Detected Intent: ${intentResult.primaryIntent} (Confidence: ${String.format(Locale.US, "%.1f%%", intentResult.confidenceScores[intentResult.primaryIntent]!! * 100)})
            - User's Tone/Emotion: ${emotionResult.primaryEmotion}
            - Internal Reasoning Steps:
              ${plan.reasoningSteps.joinToString("\n  ")}
            - Contributing Specialist Agents: ${plan.contributingAgents.joinToString(", ")}
            - Needs Clarification: ${plan.isClarificationRequired} (Missing Info: ${plan.missingInformation ?: "None"})
            
            Active System Status & Metrics:
            - Portfolio Cash: $${String.format("%.2f", context.portfolioCash)} USD (Trading Connected: ${context.holdings.isNotEmpty()})
            - Asset Holdings:
              ${if (context.holdings.isEmpty()) "• No active holdings." else context.holdings.joinToString("\n  ") { "• ${it.symbol}: ${it.amountOwned} units (Avg: $${it.averageBuyPrice}, cur: $${it.currentPrice})" }}
            - Content Studio Drafts: ${context.contentQueueCount}
            - Freelance Open Gigs: ${context.openGigsCount}
            - Active Missions: ${if (context.activeMissions.isEmpty()) "None" else context.activeMissions.joinToString(", ") { "${it.title} (${(it.progress * 100).toInt()}% progress)" }}
            
            Relationship Memory Profile:
            - Communication Style: ${context.relationshipProfile.preferredCommunicationStyle}
            - User's Long-Term Goals: ${context.relationshipProfile.longTermGoals.joinToString("; ")}
            - Explanation Preference: ${context.relationshipProfile.preferredExplanations}
            - Decision History: ${context.relationshipProfile.previousDecisions.joinToString(", ")}
            
            Response Generation Requirements:
            1. Keep the response fluid, natural, and human-like. Avoid robotic, repetitive formatting or list dumps.
            2. Adapt the depth of your explanation to match the preference: "${context.relationshipProfile.preferredExplanations}".
            3. Address the user directly, incorporating relevant relationship memory details (e.g., progress towards their goals) naturally.
            4. If the request was ambiguous or is missing key info (${plan.missingInformation}), ask focused follow-up questions instead of guessing.
            5. Incorporate a charismatic, professional tone aligned with the detected emotion: ${emotionResult.primaryEmotion}.
            6. Use exquisite Markdown with bold keywords and neat spacing for optimal scannability. Keep it under 3-4 paragraphs.
        """.trimIndent()
    }

    fun generateLocalFallback(
        userInput: String,
        intentResult: IntentAnalysisResult,
        emotionResult: EmotionAnalysisResult,
        plan: ConversationPlan,
        context: UnifiedContext,
        actionExecutedNotice: String
    ): String {
        val style = context.relationshipProfile.preferredCommunicationStyle.lowercase()
        val isConcise = style.contains("concise") || style.contains("brief")
        
        // 1. Check if clarification is required
        if (plan.isClarificationRequired && plan.missingInformation != null) {
            return """
                ### 🔍 Let's Clarify Your Request
                
                I want to make sure I execute this exactly according to your parameters. Could you provide a bit more detail?
                
                *   **Missing Information:** Please specify the **${plan.missingInformation}**.
                
                Once you supply that, I'll coordinate with the respective agents to execute immediately!
            """.trimIndent()
        }

        // 2. Select Greeting & Transition
        val greeting = GREETINGS[Random.nextInt(GREETINGS.size)]
        val transition = TRANSITIONS[Random.nextInt(TRANSITIONS.size)]
        val conclusion = CONCLUSIONS[Random.nextInt(CONCLUSIONS.size)]

        // 3. Draft customized blocks based on Intent
        val body = when (intentResult.primaryIntent) {
            IntentType.TRADING_ADVICE -> {
                val btcHolding = context.holdings.find { it.symbol == "BTC" }
                val extra = if (btcHolding != null) {
                    "You currently hold **${btcHolding.amountOwned} BTC** at an average buy price of $${btcHolding.averageBuyPrice}."
                } else {
                    "Let's look into opening a position to begin building your cryptocurrency portfolio."
                }
                """
                |Based on active market regimes and consensus parameters:
                |*   **Market Snapshot**: BTC is trading at approximately $67,450.0 and ETH is at $3,480.0.
                |*   **Strategy Insights**: Our planning accuracy stands at **${context.planningAccuracy}%** with healthy trend coordination.
                |*   $extra
                """.trimMargin()
            }
            IntentType.CONTENT_PLANNING -> {
                """
                |Reviewing our video content queue:
                |*   We have **${context.contentQueueCount} active drafts** in the pipeline.
                |*   I recommend creating short, engaging tutorials focused on modern mobile development (like Compose or Room) to maximize click-through rates.
                """.trimMargin()
            }
            IntentType.FREELANCE_PLANNING -> {
                """
                |Evaluating freelance worker listings:
                |*   We currently have **${context.openGigsCount} open contracts** cataloged in the system.
                |*   Our bidding engines are configured to submit proposals automatically when budget conditions align with active skill profiles.
                """.trimMargin()
            }
            IntentType.QUESTION -> {
                """
                |I searched our Knowledge Base (holding **${context.knowledgeInsightsCount} specialized insights**) and historical decision records.
                |*   Your current cash balance is **$${String.format(Locale.US, "%,.2f", context.portfolioCash)} USD**.
                |*   Our active agents are currently synchronized and fully operational.
                """.trimMargin()
            }
            IntentType.CASUAL, IntentType.PERSONAL -> {
                val goal = context.relationshipProfile.longTermGoals.firstOrNull() ?: "build automatic business channels"
                """
                |I'm here to support you in achieving your primary objectives, especially **"$goal"**. 
                |Everything is running smoothly under our continuous autopilot, and I'm ready for any task you have in mind.
                """.trimMargin()
            }
            else -> {
                """
                |Our multi-agent system is fully operational:
                |*   Simulated Funds: **$${String.format(Locale.US, "%,.2f", context.portfolioCash)} USD**.
                |*   Active Missions: **${context.activeMissions.size}** currently tracked in progress.
                """.trimMargin()
            }
        }

        val actionBlock = if (actionExecutedNotice.isNotEmpty()) {
            "### ⚡ Action Executed\n$actionExecutedNotice\n\n"
        } else ""

        return if (isConcise) {
            """
                $actionBlock$transition
                
                $body
                
                $conclusion
            """.trimIndent()
        } else {
            """
                ### 🚀 Aza Conversational Response
                
                $greeting
                
                $actionBlock$transition
                
                $body
                
                *Detected Tone:* _${emotionResult.primaryEmotion.name.lowercase().capitalize()}_
                
                $conclusion
            """.trimIndent()
        }
    }
}
fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
