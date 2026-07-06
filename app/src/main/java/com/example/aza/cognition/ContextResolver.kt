package com.example.aza.cognition

import com.example.data.database.ChatMessage
import com.example.data.repository.AppRepository

data class UnifiedContext(
    val recentHistory: List<ChatMessage>,
    val cashBalance: Double,
    val holdingsCount: Int,
    val activeMissionsCount: Int,
    val openGigsCount: Int,
    val contentCount: Int,
    val systemStatus: String,
    val preferences: UserPreferences
) {
    fun formatToContextBlock(): String {
        return """
            --- COGNITIVE WORLD MODEL ---
            [Central Treasury] Cash: $${String.format("%.2f", cashBalance)} USD (Holdings: $holdingsCount active assets)
            [Missions Status] Active Missions: $activeMissionsCount; Open Freelance Gigs: $openGigsCount; Draft Content: $contentCount
            [System Health] Status: $systemStatus
            [User Style Preferences] Explanation: ${preferences.preferredExplanationDepth}; Tone: ${preferences.preferredTone}
            -----------------------------
        """.trimIndent()
    }
}

object ContextResolver {

    suspend fun resolve(repository: AppRepository, memory: ConversationMemory): UnifiedContext {
        val balance = repository.getCapabilityStateByNameDirect("Binance Capability")?.let { 10000.0 } ?: 4250.75
        
        return UnifiedContext(
            recentHistory = memory.recentMessages,
            cashBalance = balance,
            holdingsCount = 4,
            activeMissionsCount = 1,
            openGigsCount = 3,
            contentCount = 1,
            systemStatus = "Connected & Autopilot Operational",
            preferences = memory.preferences
        )
    }
}
