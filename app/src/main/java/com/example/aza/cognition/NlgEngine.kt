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
        "I'm focusing directly on",
        "It's a great strategy to look into",
        "Regarding our roadmap and milestones for",
        "Reviewing the operational parameters of",
        "Analyzing our central milestones around"
    )

    private val COGNITIVE_OPENERS = listOf(
        "I'm fully synchronized and ready to assist.",
        "I've processed our current status and am fully aligned with our direction.",
        "Ready to formulate some clear tactical options.",
        "Our operations are running smoothly in real-time.",
        "I've calibrated our current business indicators."
    )

    private val ANALYTICAL_TRANSITIONS = listOf(
        "When we evaluate our current position, we find that",
        "Checking our active parameters confirms that",
        "Looking closely at our operations shows that",
        "Based on our primary objective parameters,",
        "An analysis of our current progress shows that"
    )

    private val STRATEGIC_PROMPTS = listOf(
        "How would you like to prioritize this next?",
        "What are your thoughts on this tactical direction?",
        "We can adjust our dynamic models anytime. What is your next move?",
        "Shall we move forward with this approach or look at other areas?",
        "Let me know your thoughts on our next steps."
    )

    private val CONCLUSIONS_NLG = listOf(
        "Let me know if we should refine our parameters or start a new task.",
        "I'll continue monitoring these systems in autopilot. What is our next objective?",
        "I'm ready whenever you are. Let me know how you'd like to proceed.",
        "Let's stay focused on these goals. What should we tackle next?"
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
        val profile = context.relationshipProfile
        val style = profile.investingStyle
        val assets = profile.preferredAssets.joinToString(", ").ifEmpty { "BTC, ETH, SOL" }
        val strats = profile.preferredStrategies.joinToString(", ").ifEmpty { "Breakout Momentum, Volatility Expansion" }
        val cashStr = String.format(Locale.US, "%,.2f", context.portfolioCash)

        val warmGreetings = listOf(
            "I hear you! Let's take a look at where we stand and how we can best align with your targets.",
            "Great to connect! I've been monitoring market structures closely through the lens of your strategy.",
            "Absolutely. Let's touch base on your portfolio trajectory and active market setups.",
            "I'm right here with you. Let's evaluate our latest positions and strategic opportunities."
        )
        val greeting = warmGreetings[rand.nextInt(warmGreetings.size)]

        val tailoredGuidance = when (style.uppercase(Locale.US)) {
            "AGGRESSIVE", "AGGRESSIVE_GROWTH" -> {
                "🚀 **Tailored Guidance (Aggressive Growth Profile)**\n" +
                "Given your preference for high-momentum growth and focus on **$assets**, Lumina is scanning for high-conviction breakout setups and volatility expansions (**$strats**). We target 1:2.5+ risk-reward ratios with aggressive trailing stops to capture explosive moves."
            }
            "CONSERVATIVE", "CONSERVATIVE_PRESERVATION" -> {
                "🛡️ **Tailored Guidance (Conservative Capital Preservation)**\n" +
                "In line with your defensive investment stance, Lumina prioritizes capital preservation across **$assets**. We apply tight 1.5x ATR stops, multi-timeframe confirmation, and hedged position sizing to keep drawdowns minimal."
            }
            "SWING_TRADING", "SWING" -> {
                "📈 **Tailored Guidance (Disciplined Swing Trader Profile)**\n" +
                "Matching your swing trading timeframe (${profile.preferredTimeframe}), Lumina focuses on 1H/4H trend-following and key structural level breakouts across **$assets**. We use multi-indicator momentum confirmation to enter positions before major macro expansions."
            }
            "DAY_TRADING", "DAY_TRADER", "SCALP" -> {
                "⚡ **Tailored Guidance (High-Frequency Day Trader Profile)**\n" +
                "Tailored to your intraday focus (${profile.preferredTimeframe}), Lumina monitors 15M/1H liquidity sweeps and orderbook orderflow across **$assets**. We lock in quick profits at key pivot levels with tight VWAP risk controls."
            }
            "LONG_TERM_HODL", "HODL", "VALUE" -> {
                "🏛️ **Tailored Guidance (Long-Term Macro HODL & DCA Profile)**\n" +
                "Aligned with your long-term wealth accumulation goals, Lumina executes strategic dollar-cost-averaging into **$assets** based on on-chain metrics, macro sentiment indicators, and fundamental valuation floors."
            }
            else -> {
                "⚖️ **Tailored Guidance (Balanced Moderate Growth Profile)**\n" +
                "Matching your balanced investment approach, Lumina deploys a hybrid of Breakout Momentum and Mean Reversion models across **$assets**. We maintain a disciplined 1.0% risk allocation per position with active profit scaling."
            }
        }

        val systemOverview = "Currently, our paper liquidity pool stands at **$$cashStr USD** with active daemon execution enabled."

        val actionBlock = if (actionExecutedNotice.isNotEmpty()) {
            "\n\n✨ **System Update:**\n$actionExecutedNotice"
        } else ""

        val conversationalPrompts = listOf(
            "How would you like us to tune our focus next? We can adjust asset weightings or strategy parameters anytime!",
            "What asset or setup are you curious about right now? I'm ready to analyze it with you.",
            "Let me know if you want to test new parameters or adjust our daily trading allocation!",
            "Feel free to share any specific setups or market thoughts you'd like me to evaluate!"
        )
        val prompt = conversationalPrompts[rand.nextInt(conversationalPrompts.size)]

        return """
            $greeting
            
            $tailoredGuidance
            
            $systemOverview$actionBlock
            
            $prompt
        """.trimIndent()
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
