package com.example.aza.conversation

import java.util.Locale

object IntentAnalyzer {

    fun analyze(text: String): IntentAnalysisResult {
        val lower = text.lowercase(Locale.US).trim()
        val scores = mutableMapOf<IntentType, Double>()

        // Initialize all intent scores to a baseline of 0.05
        IntentType.values().forEach { scores[it] = 0.05 }

        // 1. Question Pattern (How, What, Why, Who, When, Where, ?, is there, can you explain)
        var questionScore = 0.05
        if (lower.contains("?") || lower.startsWith("how") || lower.startsWith("what") || 
            lower.startsWith("why") || lower.startsWith("can you") || lower.startsWith("explain") ||
            lower.startsWith("show me") || lower.contains("tell me about") || lower.contains("who is")
        ) {
            questionScore += 0.6
        }
        scores[IntentType.QUESTION] = questionScore

        // 2. Request Pattern (Buy, Sell, Deposit, Apply, Create, Draft, Run, Surf, Browse, Search, executing actions)
        var requestScore = 0.05
        if (lower.contains("buy") || lower.contains("sell") || lower.contains("deposit") || 
            lower.contains("apply") || lower.contains("create") || lower.contains("draft") || 
            lower.contains("surf") || lower.contains("browse") || lower.contains("search") ||
            lower.startsWith("do ") || lower.startsWith("please ") || lower.contains("generate")
        ) {
            requestScore += 0.5
        }
        scores[IntentType.REQUEST] = requestScore

        // 3. Planning Pattern (Plan, roadmap, schedule, next step, upcoming, outline, prepare)
        var planningScore = 0.05
        if (lower.contains("plan") || lower.contains("roadmap") || lower.contains("schedule") || 
            lower.contains("next step") || lower.contains("upcoming") || lower.contains("timeline") ||
            lower.contains("milestones") || lower.contains("strategy plan")
        ) {
            planningScore += 0.55
        }
        scores[IntentType.PLANNING] = planningScore

        // 4. Explanation Pattern (Explain why, details of, why did you, give me the reasoning, describe the background)
        var explanationScore = 0.05
        if (lower.contains("explain why") || lower.contains("why did you") || lower.contains("reasoning") ||
            lower.contains("how does it work") || lower.contains("details on") || lower.contains("deep dive") ||
            lower.contains("elaborate")
        ) {
            explanationScore += 0.6
        }
        scores[IntentType.EXPLANATION] = explanationScore

        // 5. Correction Pattern (No, incorrect, wrong, change that, fix, modify, not like that, instead)
        var correctionScore = 0.05
        if (lower.startsWith("no") || lower.contains("incorrect") || lower.contains("wrong") || 
            lower.contains("change that") || lower.contains("fix") || lower.contains("modify") || 
            lower.contains("not like that") || lower.contains("instead") || lower.contains("correction")
        ) {
            correctionScore += 0.65
        }
        scores[IntentType.CORRECTION] = correctionScore

        // 6. Feedback Pattern (Good job, perfect, bad, awesome, excellent, thank you, nice, thanks, terrible)
        var feedbackScore = 0.05
        if (lower.contains("good job") || lower.contains("perfect") || lower.contains("bad") || 
            lower.contains("awesome") || lower.contains("excellent") || lower.contains("thank") || 
            lower.contains("nice") || lower.contains("great") || lower.contains("love it") || 
            lower.contains("dislike") || lower.contains("terrible")
        ) {
            feedbackScore += 0.7
        }
        scores[IntentType.FEEDBACK] = feedbackScore

        // 7. Casual Conversation (Hello, hi, hey, yo, good morning, how are you, what's up)
        var casualScore = 0.05
        if (lower.startsWith("hello") || lower.startsWith("hi") || lower.startsWith("hey") || 
            lower.startsWith("yo ") || lower.contains("good morning") || lower.contains("how are you") || 
            lower.contains("what's up") || lower.contains("greeting") || lower == "yo" || lower == "hey"
        ) {
            casualScore += 0.75
        }
        scores[IntentType.CASUAL] = casualScore

        // 8. Personal Discussion (I feel, my day, myself, my goals, personal, life, user's mood/life details)
        var personalScore = 0.05
        if (lower.contains("i feel") || lower.contains("my day") || lower.contains("myself") || 
            lower.contains("my life") || lower.contains("personally") || lower.contains("my family") ||
            lower.contains("about me") || lower.contains("my dream")
        ) {
            personalScore += 0.5
        }
        scores[IntentType.PERSONAL] = personalScore

        // 9. Trading Advice (Market, crypto, bitcoin, eth, stock, portfolio, trading, advice, chart, indicators)
        var tradingScore = 0.05
        if (lower.contains("market") || lower.contains("crypto") || lower.contains("bitcoin") || 
            lower.contains("eth") || lower.contains("stock") || lower.contains("portfolio") || 
            lower.contains("trading") || lower.contains("advice") || lower.contains("chart") || 
            lower.contains("indicators") || lower.contains("asset") || lower.contains("investing")
        ) {
            tradingScore += 0.55
        }
        scores[IntentType.TRADING_ADVICE] = tradingScore

        // 10. Content Planning (Video, script, tiktok, youtube, instagram, content, social media, draft content, publication)
        var contentScore = 0.05
        if (lower.contains("video") || lower.contains("script") || lower.contains("tiktok") || 
            lower.contains("youtube") || lower.contains("instagram") || lower.contains("content") || 
            lower.contains("social media") || lower.contains("reels") || lower.contains("engagement")
        ) {
            contentScore += 0.55
        }
        scores[IntentType.CONTENT_PLANNING] = contentScore

        // 11. Freelance Planning (Gig, client, contract, proposal, apply, freelance, budget, upwork, fiverr)
        var freelanceScore = 0.05
        if (lower.contains("gig") || lower.contains("client") || lower.contains("contract") || 
            lower.contains("proposal") || lower.contains("freelance") || lower.contains("budget") || 
            lower.contains("upwork") || lower.contains("fiverr") || lower.contains("job board")
        ) {
            freelanceScore += 0.55
        }
        scores[IntentType.FREELANCE_PLANNING] = freelanceScore

        // 12. System Control (Toggle, settings, connection, link, unlink, reset, mode, configure, autopilot)
        var systemScore = 0.05
        if (lower.contains("toggle") || lower.contains("settings") || lower.contains("connection") || 
            lower.contains("link") || lower.contains("unlink") || lower.contains("reset") || 
            lower.contains("autopilot") || lower.contains("configure") || lower.contains("hardware")
        ) {
            systemScore += 0.5
        }
        scores[IntentType.SYSTEM_CONTROL] = systemScore

        // 13. Executive Decision (Approve, reject, decline, override, authority, decision, audit log, approve task)
        var executiveScore = 0.05
        if (lower.contains("approve") || lower.contains("reject") || lower.contains("decline") || 
            lower.contains("override") || lower.contains("authority") || lower.contains("decision") || 
            lower.contains("audit log") || lower.contains("authorize")
        ) {
            executiveScore += 0.5
        }
        scores[IntentType.EXECUTIVE_DECISION] = executiveScore

        // Find the entry with the highest score
        val primaryEntry = scores.maxByOrNull { it.value }!!
        val primaryIntent = primaryEntry.key
        
        // Build an explanation
        val explanation = "Classified primary intent as $primaryIntent based on matching terms in text: \"$text\". Score: ${String.format(Locale.US, "%.2f", primaryEntry.value)}"

        return IntentAnalysisResult(
            primaryIntent = primaryIntent,
            confidenceScores = scores,
            explanation = explanation
        )
    }
}
