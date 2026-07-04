package com.example.aza.conversation

import java.util.Locale

object EmotionAnalyzer {

    fun analyze(text: String): EmotionAnalysisResult {
        val lower = text.lowercase(Locale.US).trim()
        val scores = mutableMapOf<EmotionType, Double>()

        // Initialize all scores to a baseline of 0.05
        EmotionType.values().forEach { scores[it] = 0.05 }

        // 1. Excited (!, awesome, amazing, so cool, wow, can't wait)
        var excitedScore = 0.05
        if (lower.contains("!") || lower.contains("awesome") || lower.contains("amazing") || 
            lower.contains("so cool") || lower.contains("wow") || lower.contains("cant wait") ||
            lower.contains("stoked") || lower.contains("thrilled")
        ) {
            excitedScore += 0.5
        }
        scores[EmotionType.EXCITED] = excitedScore

        // 2. Frustrated (wrong, broken, why did, error, fail, stupid, hate, annoying, slow, issue)
        var frustratedScore = 0.05
        if (lower.contains("wrong") || lower.contains("broken") || lower.contains("fail") || 
            lower.contains("stupid") || lower.contains("hate") || lower.contains("annoying") || 
            lower.contains("slow") || lower.contains("issue") || lower.contains("bother") ||
            lower.contains("nonsense") || lower.contains("wtf")
        ) {
            frustratedScore += 0.55
        }
        scores[EmotionType.FRUSTRATED] = frustratedScore

        // 3. Uncertain (not sure, maybe, perhaps, i think, check if, is it okay, do you think, confusing)
        var uncertainScore = 0.05
        if (lower.contains("not sure") || lower.contains("maybe") || lower.contains("perhaps") || 
            lower.contains("i think") || lower.contains("is it okay") || lower.contains("confused") ||
            lower.contains("confusing") || lower.contains("wonder if") || lower.contains("doubt")
        ) {
            uncertainScore += 0.5
        }
        scores[EmotionType.UNCERTAIN] = uncertainScore

        // 4. Curious (how to, what is, tell me more, explain, why, learn, explore, question, details)
        var curiousScore = 0.05
        if (lower.contains("how to") || lower.contains("what is") || lower.contains("tell me more") || 
            lower.contains("explain") || lower.contains("why") || lower.contains("learn") || 
            lower.contains("explore") || lower.contains("details") || lower.contains("curious")
        ) {
            curiousScore += 0.45
        }
        scores[EmotionType.CURIOUS] = curiousScore

        // 5. Focused (let's do, prioritize, execute, action, task, build, implement, setup, analyze, run)
        var focusedScore = 0.05
        if (lower.contains("let's do") || lower.contains("prioritize") || lower.contains("execute") || 
            lower.contains("action") || lower.contains("task") || lower.contains("build") || 
            lower.contains("implement") || lower.contains("setup") || lower.contains("analyze") || 
            lower.contains("run") || lower.contains("optimize")
        ) {
            focusedScore += 0.4
        }
        scores[EmotionType.FOCUSED] = focusedScore

        // 6. Celebratory (congrats, congrats!, won, profit, success, success!, made it, solved, hurray, cheers)
        var celebratoryScore = 0.05
        if (lower.contains("congrats") || lower.contains("won") || lower.contains("profit") || 
            lower.contains("success") || lower.contains("made it") || lower.contains("solved") || 
            lower.contains("cheers") || lower.contains("yay") || lower.contains("hurrah")
        ) {
            celebratoryScore += 0.6
        }
        scores[EmotionType.CELEBRATORY] = celebratoryScore

        // 7. Calm / Neutral (everything else, baseline or polite)
        var calmScore = 0.2
        if (lower.startsWith("hello") || lower.startsWith("hi") || lower.contains("okay") || 
            lower.contains("thanks") || lower.contains("neutral")
        ) {
            calmScore += 0.4
        }
        scores[EmotionType.CALM] = calmScore

        val primaryEntry = scores.maxByOrNull { it.value }!!
        val primaryEmotion = primaryEntry.key

        return EmotionAnalysisResult(
            primaryEmotion = primaryEmotion,
            scores = scores,
            description = "Detected primary tone as $primaryEmotion. Intensity: ${String.format(Locale.US, "%.2f", primaryEntry.value)}"
        )
    }
}
