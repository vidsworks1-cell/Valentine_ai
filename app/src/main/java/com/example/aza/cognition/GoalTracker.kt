package com.example.aza.cognition

import com.example.data.database.AzaObjective
import com.example.data.repository.AppRepository

class GoalTracker(private val repository: AppRepository) {
    val coreLongTermGoals = listOf(
        "Financial Freedom",
        "Grow Trading Capital",
        "Develop YouTube Channel",
        "Generate Freelance Income",
        "Improve AZA",
        "Build Atlas AI"
    )

    suspend fun alignMessageWithGoals(text: String): List<String> {
        val textLower = text.lowercase()
        val alignedGoals = mutableListOf<String>()
        
        if (textLower.contains("portfolio") || textLower.contains("trade") || textLower.contains("crypto") || textLower.contains("money") || textLower.contains("risk")) {
            alignedGoals.add("Grow Trading Capital")
            alignedGoals.add("Financial Freedom")
        }
        if (textLower.contains("video") || textLower.contains("script") || textLower.contains("youtube") || textLower.contains("channel")) {
            alignedGoals.add("Develop YouTube Channel")
        }
        if (textLower.contains("gig") || textLower.contains("freelance") || textLower.contains("proposal") || textLower.contains("client") || textLower.contains("contract")) {
            alignedGoals.add("Generate Freelance Income")
        }
        if (textLower.contains("review itself") || textLower.contains("improve aza") || textLower.contains("diagnose") || textLower.contains("diagnostics") || textLower.contains("cognition")) {
            alignedGoals.add("Improve AZA")
        }
        if (textLower.contains("atlas") || textLower.contains("atlas ai")) {
            alignedGoals.add("Build Atlas AI")
        }
        
        return alignedGoals.ifEmpty { listOf("Financial Freedom") }
    }
}
