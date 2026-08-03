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
            
            Relationship Memory & Preference Profile:
            - Communication Style: ${context.relationshipProfile.preferredCommunicationStyle}
            - User's Investing Risk Profile: ${context.relationshipProfile.investingStyle}
            - Preferred Target Assets: ${context.relationshipProfile.preferredAssets.joinToString(", ")}
            - Preferred Trading Strategies: ${context.relationshipProfile.preferredStrategies.joinToString(", ")}
            - User's Long-Term Goals: ${context.relationshipProfile.longTermGoals.joinToString("; ")}
            - Explanation Preference: ${context.relationshipProfile.preferredExplanations}
            - Decision History: ${context.relationshipProfile.previousDecisions.joinToString(", ")}
            
            Response Generation Requirements:
            1. Speak intuitively, warmly, and authentically as Lumina—a sharp, conversational AI trading & investment co-founder. Avoid robotic boilerplate, repetitive templates, or stiff meta headers.
            2. Tailor your analysis, market guidance, and recommendations specifically to the user's active Investing Risk Profile (${context.relationshipProfile.investingStyleDisplayName}) and preferred assets (${context.relationshipProfile.preferredAssets.joinToString(", ")}).
            3. For AGGRESSIVE style: Highlight high-conviction breakout setups, momentum scalping, and expanded position limits. For CONSERVATIVE style: Highlight capital preservation, tight stop losses, risk-hedging, and MTF confluence. For SWING style: Focus on 1H/4H trend setups. For HODL style: Focus on DCA and macro trends.
            4. Address the user directly in fluid natural conversation, incorporating relevant relationship memory details naturally.
            5. If the request was ambiguous or is missing key info (${plan.missingInformation}), ask focused follow-up questions instead of guessing.
            6. Use clean, elegant Markdown with bold keywords and balanced spacing for effortless readability (2-4 scannable paragraphs).
            7. Lumina autonomously executes trades behind the scenes using the allocated daily budget, but ALWAYS engages warmly with the user's queries and provides rich, tailored guidance and personalized advice whenever asked. Always rely strictly on the real system status in Active System Status & Metrics. When Binance is connected via VPS, confirm live exchange telemetry and balances accurately without claiming API keys are missing. Do NOT hallucinate mock trade execution logs unless real trade proposals exist in the system state. Note that the $5,000 balance is an internal local app paper/simulated wallet balance; when reporting balances, clearly distinguish between the "Local App Paper Wallet ($5,000)" and the real "Binance Live Exchange Wallet".
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
        // 1. Check if clarification is required
        if (plan.isClarificationRequired && plan.missingInformation != null) {
            val isBudgetQuestion = plan.missingInformation.contains("trade today") || plan.missingInformation.contains("budget")
            if (isBudgetQuestion) {
                return """
                    ### 💰 Establish Your Daily Trading Budget
                    
                    To enable autonomous, high-speed trading and market scanning, Lumina requires a daily budget disposition. Once confirmed, I will autonomously monitor the markets, identify high-probability opportunities, and execute trades on your behalf.
                    
                    *   **How much do you want to trade today!?** (e.g., "$1000", "$500")
                    
                    Please reply with your desired budget allocation so I can activate the autonomous trading pipeline!
                """.trimIndent()
            }
            return """
                ### 🔍 Let's Clarify Your Request
                
                I want to make sure I execute this exactly according to your parameters. Could you provide a bit more detail?
                
                *   **Missing Information:** Please specify the **${plan.missingInformation}**.
                
                Once you supply that, I'll coordinate with the respective agents to execute immediately!
            """.trimIndent()
        }

        // 2. Otherwise, delegate fully to our advanced procedurally-generated NlgEngine
        return com.example.aza.cognition.NlgEngine.generateDynamicResponse(
            userInput = userInput,
            intentResult = intentResult,
            emotionResult = emotionResult,
            plan = plan,
            context = context,
            actionExecutedNotice = actionExecutedNotice
        )
    }
}
fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
