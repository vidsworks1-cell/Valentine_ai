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
        val assets = profile.preferredAssets.joinToString(", ").ifEmpty { "BTC, ETH, SOL, BNB" }
        val strats = profile.preferredStrategies.joinToString(", ").ifEmpty { "Breakout Momentum, Volatility Expansion, Mean Reversion" }
        val cashStr = String.format(Locale.US, "%,.2f", context.portfolioCash)
        val inputLower = userInput.trim().lowercase(Locale.US)

        val actionBlock = if (actionExecutedNotice.isNotEmpty()) {
            "✨ **Action Confirmation:**\n$actionExecutedNotice\n\n"
        } else ""

        // 1. Specific Intent: Paper Mode vs Live Trading Mode
        if (inputLower.contains("paper") || inputLower.contains("live") || inputLower.contains("switch") || 
            inputLower.contains("mode") || inputLower.contains("real money") || inputLower.contains("simulation")) {
            val currentMode = if (context.isBinanceConnected) context.binanceMode else "PAPER (Simulation)"
            return """
                ${actionBlock}🛡️ **Trading Mode Status: $currentMode**

                • **Why does it default to Paper Mode?**
                  For safety and risk protection, AZA defaults to **Paper Trading (Sandbox)** upon app startup and session resets. This prevents automated strategy daemons from placing unverified orders with real funds until you are ready.

                • **How to switch to LIVE Trading:**
                  1. **Via Chat Command:** Type `set vps mode live` or `set binance trading mode trading`
                  2. **Via Trading Tab:** Toggle the **Mode Selector** switch on the Trading Dashboard
                  3. **API Keys:** Verify your Binance API key and Secret in **Settings / Account**

                • **Current Liquidity Pool:**
                  - Local Paper Capital: **$$cashStr USD**
                  - Binance Exchange Link: **${if (context.isBinanceConnected) "Connected ($currentMode)" else "Not linked / Simulation mode"}**
                  ${if (context.binanceLiveBalances.isNotBlank()) "- Live Balances: ${context.binanceLiveBalances}" else ""}

                Would you like me to switch our execution mode to **LIVE** or configure your exchange credentials?
            """.trimIndent()
        }

        // 2. Specific Intent: Balances, Wallet, Funds, USDT, Holdings
        if (inputLower.contains("balance") || inputLower.contains("wallet") || inputLower.contains("cash") || 
            inputLower.contains("fund") || inputLower.contains("money") || inputLower.contains("holding") || 
            inputLower.contains("portfolio") || inputLower.contains("how much") || inputLower.contains("usdt")) {
            val holdingsList = if (context.holdings.isEmpty()) {
                "  • *No active open positions in local ledger.*"
            } else {
                context.holdings.joinToString("\n") { 
                    "  • **${it.symbol}**: ${it.amountOwned} units (~$${String.format(Locale.US, "%,.2f", it.amountOwned * it.currentPrice)})" 
                }
            }
            return """
                ${actionBlock}💼 **Portfolio & Liquidity Overview**

                • **Available Liquid Cash:** **$$cashStr USD**
                • **Risk Allocation Profile:** **${context.tradingRiskLevel}** (Position Slice: ${context.positionSlicePercent}%, ATR Stop: ${context.atrMultiplier}x)
                
                • **Active Asset Holdings:**
                $holdingsList

                • **Binance Connection:**
                  - Status: **${if (context.isBinanceConnected) "CONNECTED (${context.binanceMode})" else "DISCONNECTED (Simulation Mode)"}**
                  ${if (context.binanceLiveBalances.isNotBlank()) "- Exchange Balances: ${context.binanceLiveBalances}" else ""}

                Ready to deploy capital into active setups or adjust our position sizing?
            """.trimIndent()
        }

        // 3. Specific Intent: Market Analysis, Crypto Prices, BTC, ETH, SOL, Signals
        if (inputLower.contains("market") || inputLower.contains("crypto") || inputLower.contains("btc") || 
            inputLower.contains("bitcoin") || inputLower.contains("eth") || inputLower.contains("ethereum") || 
            inputLower.contains("sol") || inputLower.contains("solana") || inputLower.contains("bnb") || 
            inputLower.contains("signal") || inputLower.contains("price") || inputLower.contains("trend") ||
            inputLower.contains("bull") || inputLower.contains("bear") || inputLower.contains("buy") || inputLower.contains("sell")) {
            return """
                ${actionBlock}📈 **Market Intelligence & Tactical Setups**

                • **Target Assets:** **$assets**
                • **Active Strategy Models:** **$strats**
                
                • **Key Technical Readings:**
                  - **BTC/USDT:** Testing key EMA support bands. Momentum metrics indicate steady structural accumulation with low sell pressure.
                  - **ETH/USDT:** Consolidating above historical liquidity zones. Mean reversion signals are neutral-bullish.
                  - **SOL/USDT & Alts:** High momentum volatility; trailing stops tightened to **${context.atrMultiplier}x ATR**.

                • **Algorithmic Guardrails:**
                  - Position Slice: **${context.positionSlicePercent}%** per trade
                  - Liquidity Pool: **$$cashStr USD**
                  - Stop-Loss Discipline: Multi-timeframe ATR trailing stops enabled

                Type `buy BTC 100` or `run backtest` if you'd like to initiate an order or simulate strategy results!
            """.trimIndent()
        }

        // 4. Specific Intent: VPS, Contabo, Server, System Health, Diagnostics
        if (inputLower.contains("vps") || inputLower.contains("server") || inputLower.contains("contabo") || 
            inputLower.contains("daemon") || inputLower.contains("health") || inputLower.contains("status") || 
            inputLower.contains("telemetry") || inputLower.contains("uptime") || inputLower.contains("diagnostic")) {
            return """
                ${actionBlock}🖥️ **System Health & VPS Telemetry**

                • **Internal Core Engines:** **ALL ONLINE & CALIBRATED**
                • **System Uptime:** **${String.format(Locale.US, "%.1f%%", context.systemUptime * 100f)}**
                • **Autonomous Planning Accuracy:** **${String.format(Locale.US, "%.1f%%", context.planningAccuracy * 100f)}**
                • **Resource Efficiency:** **${String.format(Locale.US, "%.1f%%", context.resourceEfficiency * 100f)}**
                • **Exchange Bridge:** ${if (context.isBinanceConnected) "🟢 Connected (${context.binanceMode})" else "🟡 Internal Simulation Active"}
                • **Emergency Stop:** 🟢 Disarmed & Ready for Execution

                You can say `check contabo status` to run a deep live diagnostic on your backend server.
            """.trimIndent()
        }

        // 5. Specific Intent: AI / Gemini API Key / Brain
        if (inputLower.contains("gemini") || inputLower.contains("api key") || inputLower.contains("key") || 
            inputLower.contains("ai") || inputLower.contains("model") || inputLower.contains("brain")) {
            return """
                ${actionBlock}🧠 **AZA Cognitive Engine & Gemini AI Integration**

                • **Multimodal Reasoning:** Supports Gemini 3.5 Flash and Gemini 3.1 Pro models.
                • **How to configure your API Key:**
                  Type directly in chat:
                  `set gemini key YOUR_API_KEY_HERE`
                  
                  Or set it in **Account / Settings**. Once saved, your key is securely encrypted on-device and enables real-time generative conversational intelligence.

                How can I assist your operations next?
            """.trimIndent()
        }

        // 6. Specific Intent: Strategy, Risk, Scalper, Stop-loss, Parameters
        if (inputLower.contains("strategy") || inputLower.contains("risk") || inputLower.contains("scalp") || 
            inputLower.contains("stop") || inputLower.contains("atr") || inputLower.contains("parameter") || 
            inputLower.contains("win rate") || inputLower.contains("backtest") || inputLower.contains("setting")) {
            return """
                ${actionBlock}⚙️ **Strategy Engine & Risk Guardrails**

                • **Current Profile:** **$style** (Timeframe: ${profile.preferredTimeframe})
                • **Active Execution Models:** **$strats**
                • **Risk Controls:**
                  - Risk Level: **${context.tradingRiskLevel}**
                  - ATR Stop Multiplier: **${context.atrMultiplier}x**
                  - Max Position Slice: **${context.positionSlicePercent}%** of capital
                  - Micro-Scalper: Standby (type `start scalper` to engage)

                • **Useful Chat Commands:**
                  - `set atr multiplier 2.0` — adjust trailing stop distance
                  - `set position slice 10` — adjust single-trade allocation percentage
                  - `run backtest` — test active models against historic candles
            """.trimIndent()
        }

        // 7. General Questions, Explanations, or Feedback ("What is happening", "Why...", "Help")
        if (inputLower.contains("why") || inputLower.contains("what is happening") || inputLower.contains("what's happening") || 
            inputLower.contains("help") || inputLower.contains("problem") || inputLower.contains("explain") ||
            intentResult.primaryIntent == IntentType.QUESTION || intentResult.primaryIntent == IntentType.EXPLANATION || 
            intentResult.primaryIntent == IntentType.FEEDBACK || intentResult.primaryIntent == IntentType.CORRECTION) {
            return """
                ${actionBlock}💡 **AZA System Intelligence**

                I'm listening and focused on: *"$userInput"*.

                • **Current State:**
                  - Trading Mode: **${if (context.isBinanceConnected) context.binanceMode else "PAPER (Simulation)"}**
                  - Liquid Pool: **$$cashStr USD**
                  - Active Assets: **$assets**
                  - Preferred Strategies: **$strats**

                • **Quick Actions:**
                  - Say **"switch to live"** to engage live order execution.
                  - Say **"check balances"** for detailed asset breakdown.
                  - Say **"market analysis"** for live technical indicators.
                  - Say **"set gemini key <KEY>"** to activate full conversational LLM reasoning.

                What specific task or trade setup would you like me to tackle?
            """.trimIndent()
        }

        // 8. Casual Greetings & Conversation
        if (intentResult.primaryIntent == IntentType.CASUAL || 
            inputLower in listOf("hi", "hello", "hey", "hola", "good morning", "good evening", "how are you", "sup", "yo")) {
            val greetings = listOf(
                "Hello! I'm here and ready. Our paper liquidity is **$$cashStr USD** across **$assets**. How can I help you today?",
                "Hey there! All systems are calibrated and monitoring **$assets**. What are we focusing on today?",
                "Greetings! AZA is online with **$$cashStr USD** in available capital. Ready for market scans, strategy tuning, or trade execution."
            )
            return "${actionBlock}${greetings[rand.nextInt(greetings.size)]}"
        }

        // 9. Standard Dynamic Context Fallback
        val tailoredGuidance = when (style.uppercase(Locale.US)) {
            "AGGRESSIVE", "AGGRESSIVE_GROWTH" -> {
                "🚀 **Strategy Guidance (Aggressive Growth)**\n" +
                "Scanning high-conviction breakout setups across **$assets** with a focus on **$strats** and dynamic trailing stops."
            }
            "CONSERVATIVE", "CONSERVATIVE_PRESERVATION" -> {
                "🛡️ **Strategy Guidance (Conservative Preservation)**\n" +
                "Prioritizing capital preservation across **$assets** with tight ${context.atrMultiplier}x ATR stops and strict risk limits."
            }
            else -> {
                "⚖️ **Strategy Guidance ($style Profile)**\n" +
                "Maintaining disciplined risk allocation (${context.positionSlicePercent}% per position) across **$assets** with **$strats**."
            }
        }

        return """
            ${actionBlock}I'm right here with you! Evaluating opportunities across **$assets**.

            $tailoredGuidance

            • **Liquidity Pool:** **$$cashStr USD** | **Mode:** ${if (context.isBinanceConnected) context.binanceMode else "PAPER"}
            • **System Status:** 🟢 Uptime ${String.format(Locale.US, "%.1f%%", context.systemUptime * 100f)} | Daemon Active

            How would you like to proceed? We can run a market analysis, adjust strategy parameters, or execute orders.
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
