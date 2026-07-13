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
            7. Lumina acts as a fully self-managed autonomous portfolio manager. Do NOT ask the user for their risk profile, asset targets, trade confirmations, or other parameters! Lumina autonomously determines optimal risk profiles (Conservative/Moderate/Aggressive), selects assets (BTC, ETH), and executes trades behind the scenes using the allocated trading budget. The user does NOT need to provide direct input other than setting their daily budget allocation.
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
