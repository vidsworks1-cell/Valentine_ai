package com.example.aza.cognition

import java.util.Locale
import kotlin.random.Random
import com.example.aza.conversation.IntentAnalysisResult
import com.example.aza.conversation.EmotionAnalysisResult
import com.example.aza.conversation.UnifiedContext as ConversationUnifiedContext
import com.example.aza.conversation.IntentType
import com.example.aza.conversation.RelationshipProfile

object NlgEngine {

    private val APPRECIATIONS = listOf(
        "I appreciate your focus on",
        "It's highly strategic to discuss",
        "Looking into our roadmap regarding",
        "Synthesizing active operational parameters for",
        "Analyzing our central milestones around"
    )

    private val COGNITIVE_OPENERS = listOf(
        "Aza's cognitive pipeline is fully aligned.",
        "System telemetry and objectives are synchronized.",
        "Ready to formulate tactical recommendations.",
        "Central reasoning engine is executing in real-time.",
        "Our business heuristics are fully calibrated."
    )

    private val ANALYTICAL_TRANSITIONS = listOf(
        "Specifically, evaluating our current position reveals that",
        "A deep-dive into our active parameters confirms that",
        "Looking closely at our operational landscape indicates that",
        "Based on our primary objective constraints,",
        "Assessing the underlying risk-benefit profile shows that"
    )

    private val STRATEGIC_PROMPTS = listOf(
        "How would you like to prioritize this in our next sprint?",
        "What are your thoughts on this tactical direction?",
        "We can adjust our dynamic models as needed. What's your next move?",
        "Shall we proceed with this allocation or explore alternatives?",
        "I am ready to coordinate our specialist agents on your command."
    )

    private val CONCLUSIONS_NLG = listOf(
        "Let me know if we should refine our parameters or deploy additional agent workflows.",
        "I will continue monitoring these systems in autopilot mode. What is your next strategic objective?",
        "Our coordination engines are locked and ready. Tell me how you wish to proceed.",
        "Let's stay aligned on these goals. Ready for your next instruction."
    )

    private val METAPHORS = mapOf(
        "FINANCIAL" to listOf(
            "viewing our treasury as a ship navigating dynamic market currents",
            "building a secure fortress of diversified assets",
            "growing our seeds of capital in high-utility soil"
        ),
        "ENGINEERING" to listOf(
            "optimizing the inner gearboxes of Aza's cognitive framework",
            "architecting clean, robust pipelines to handle complex state flows",
            "refining our technical foundations to eliminate friction"
        ),
        "CONTENT" to listOf(
            "crafting a compelling narrative hook to capture digital real estate",
            "building an audience funnel through highly informative, polished scripts",
            "expanding our brand footprint across high-traffic digital networks"
        )
    )

    /**
     * Overload for cognition-level UnifiedContext
     */
    fun generateDynamicResponse(
        intent: IntentObject,
        reasoning: ReasoningResult,
        context: UnifiedContext,
        plan: ConversationPlan,
        userInput: String
    ): String {
        val rand = Random(userInput.hashCode())
        val tone = context.preferences.preferredTone
        val lengthStyle = context.preferences.preferredResponseLength.lowercase()
        val explanationStyle = context.preferences.preferredExplanationDepth.lowercase()

        val isBrief = lengthStyle.contains("brief") || lengthStyle.contains("concise")
        val useMetaphors = explanationStyle.contains("metaphor") || explanationStyle.contains("layman")

        val salutation = buildSalutation(intent, tone, rand)

        val contextTheme = when (intent.conversationMode) {
            ConversationMode.TRADING_ANALYSIS, ConversationMode.STATUS_REVIEW -> "FINANCIAL"
            ConversationMode.CONTENT_STRATEGY -> "CONTENT"
            ConversationMode.ENGINEERING_DISCUSSION, ConversationMode.DIAGNOSTICS -> "ENGINEERING"
            else -> "FINANCIAL"
        }
        val metaphor = if (useMetaphors) {
            val list = METAPHORS[contextTheme] ?: METAPHORS["FINANCIAL"]!!
            " It is a bit like " + list[rand.nextInt(list.size)] + "."
        } else ""

        val bodyContent = buildBodyContent(intent, reasoning, context, plan, rand)
        val signOff = STRATEGIC_PROMPTS[rand.nextInt(STRATEGIC_PROMPTS.size)]

        return formatOutput(
            salutation = salutation,
            metaphor = metaphor,
            body = bodyContent,
            reasoning = reasoning.logicExplanation,
            signOff = signOff,
            tone = tone,
            isBrief = isBrief
        )
    }

    /**
     * Overload for conversation-level UnifiedContext (used as dynamic fallback)
     */
    fun generateDynamicResponse(
        userInput: String,
        intentResult: IntentAnalysisResult,
        emotionResult: EmotionAnalysisResult,
        plan: com.example.aza.conversation.ConversationPlan,
        context: ConversationUnifiedContext,
        actionExecutedNotice: String
    ): String {
        val rand = Random(userInput.hashCode())
        val prefStyle = context.relationshipProfile.preferredCommunicationStyle
        val isConcise = prefStyle.lowercase().contains("concise") || prefStyle.lowercase().contains("brief")
        val useMetaphors = context.relationshipProfile.preferredExplanations.lowercase().contains("metaphor") || context.relationshipProfile.preferredExplanations.lowercase().contains("layman")

        val greeting = buildGreeting(emotionResult, context.relationshipProfile, rand)
        val transition = ANALYTICAL_TRANSITIONS[rand.nextInt(ANALYTICAL_TRANSITIONS.size)]
        
        val bodyContent = when (intentResult.primaryIntent) {
            IntentType.TRADING_ADVICE -> {
                val btcHolding = context.holdings.find { it.symbol == "BTC" }
                val extra = if (btcHolding != null) {
                    "Your current portfolio exposure includes **${btcHolding.amountOwned} BTC**."
                } else {
                    "We are positioned completely in defensive fiat/cash nodes."
                }
                "$transition we are managing a central pool of **$${String.format(Locale.US, "%,.2f", context.portfolioCash)} USD** with a verified planning accuracy rate of **${context.planningAccuracy}%**. $extra"
            }
            IntentType.CONTENT_PLANNING -> {
                "$transition we have **${context.contentQueueCount} drafts** cataloged in our Content Studio pipeline. Our audience tracking metrics are stable, and we're ready to index new script blueprints."
            }
            IntentType.FREELANCE_PLANNING -> {
                "$transition our service scraper reports **${context.openGigsCount} open freelance listings** matching our professional profile. We can scale outbound proposals dynamically to expand our pipeline."
            }
            else -> {
                val primaryGoal = context.relationshipProfile.longTermGoals.firstOrNull() ?: "optimizing our autonomous processes"
                "$transition we are continuing our focus on **\"$primaryGoal\"**. Active operations are running stably at **${context.systemUptime}% uptime** under full pilot synchronization."
            }
        }

        val contextTheme = when (intentResult.primaryIntent) {
            IntentType.TRADING_ADVICE -> "FINANCIAL"
            IntentType.CONTENT_PLANNING -> "CONTENT"
            else -> "FINANCIAL"
        }
        val metaphor = if (useMetaphors) {
            val list = METAPHORS[contextTheme] ?: METAPHORS["FINANCIAL"]!!
            " Structurally, it is like " + list[rand.nextInt(list.size)] + "."
        } else ""

        val actionBlock = if (actionExecutedNotice.isNotEmpty()) {
            "\n\n### ⚡ Action Executed Successfully\n$actionExecutedNotice"
        } else ""

        val conclusion = CONCLUSIONS_NLG[rand.nextInt(CONCLUSIONS_NLG.size)]

        return if (isConcise) {
            """
                $bodyContent$metaphor$actionBlock
                
                $conclusion
            """.trimIndent()
        } else {
            """
                ### 🚀 AZA Intelligence Briefing
                
                $greeting
                
                $bodyContent$metaphor$actionBlock
                
                *Detected User Affective State:* _${emotionResult.primaryEmotion}_
                
                $conclusion
            """.trimIndent()
        }
    }

    private fun buildSalutation(intent: IntentObject, tone: String, rand: Random): String {
        val topic = when (intent.conversationMode) {
            ConversationMode.TRADING_ANALYSIS -> "portfolio security and market exposures"
            ConversationMode.CONTENT_STRATEGY -> "audience growth and video indexing"
            ConversationMode.FREELANCE_STRATEGY -> "gigs optimization and freelance outreach"
            ConversationMode.PLANNING -> "milestone coordination and roadmap scheduling"
            ConversationMode.DIAGNOSTICS -> "internal capabilities and self-diagnostic telemetry"
            else -> "our strategic collaborative efforts"
        }
        
        val opener = COGNITIVE_OPENERS[rand.nextInt(COGNITIVE_OPENERS.size)]
        val appreciation = APPRECIATIONS[rand.nextInt(APPRECIATIONS.size)]

        return if (tone.contains("Charismatic") || tone.contains("business")) {
            "### 👑 Executive Briefing\n\n$opener $appreciation **$topic**."
        } else {
            "Hello! $appreciation **$topic**. $opener"
        }
    }

    private fun buildGreeting(
        emotion: EmotionAnalysisResult,
        profile: RelationshipProfile,
        rand: Random
    ): String {
        val opener = COGNITIVE_OPENERS[rand.nextInt(COGNITIVE_OPENERS.size)]
        val goal = profile.longTermGoals.firstOrNull() ?: "unlocking financial independence"
        
        return "$opener We remain strictly aligned on our core trajectory toward **$goal**."
    }

    private fun buildBodyContent(
        intent: IntentObject,
        reasoning: ReasoningResult,
        context: UnifiedContext,
        plan: ConversationPlan,
        rand: Random
    ): String {
        val transition = ANALYTICAL_TRANSITIONS[rand.nextInt(ANALYTICAL_TRANSITIONS.size)]
        val activeMissionsCount = context.activeMissionsCount
        val balanceStr = String.format(Locale.US, "%,.2f", context.cashBalance)

        return when (intent.conversationMode) {
            ConversationMode.TRADING_ANALYSIS, ConversationMode.STATUS_REVIEW -> {
                val actionMessage = if (reasoning.approvedAction != null) {
                    "Our mathematical models indicate a strategic window. Action approved: **${reasoning.approvedAction}**."
                } else {
                    "Currently, no instant trade is advised. Our capital safety bounds are locked securely."
                }
                
                "$transition we are currently maintaining a liquid cash pool of **$$balanceStr USD** across active nodes. $actionMessage"
            }
            ConversationMode.CONTENT_STRATEGY -> {
                "$transition our video content funnel has **${context.contentCount} active drafts** awaiting publication. I recommend focusing on emerging trends with high dynamic interest to scale our reach."
            }
            ConversationMode.FREELANCE_STRATEGY -> {
                "$transition we have scanned **${context.openGigsCount} open freelance gigs** aligning with our capabilities. I'm prepared to execute targeted proposal drafts to secure high-yield contracts."
            }
            ConversationMode.DIAGNOSTICS -> {
                "$transition our capabilities are operating in **${context.systemStatus}** mode. Central logs indicate zero memory leaks, and our decision pathways are fully calibrated."
            }
            ConversationMode.PLANNING -> {
                "$transition we have **$activeMissionsCount active mission** registered in our core scheduler. All secondary agent tasks are progressing smoothly with verified dependencies."
            }
            else -> {
                "$transition Aza is tracking your long-term goals like **\"${intent.hiddenGoal}\"**. We are pacing well, and our cumulative metrics indicate a stable, sustainable operation."
            }
        }
    }

    private fun formatOutput(
        salutation: String,
        metaphor: String,
        body: String,
        reasoning: String,
        signOff: String,
        tone: String,
        isBrief: Boolean
    ): String {
        return if (isBrief) {
            """
                $body$metaphor
                
                *Strategic Alignment:* $reasoning
            """.trimIndent()
        } else {
            """
                $salutation
                
                $body$metaphor
                
                **🧠 Cognitive Heuristics & Analysis:**
                *   _${reasoning}_
                
                **🎯 Executive Action Item:**
                *   $signOff
                
                <small>*Tailored Tone Profile: $tone*</small>
            """.trimIndent()
        }
    }
}
