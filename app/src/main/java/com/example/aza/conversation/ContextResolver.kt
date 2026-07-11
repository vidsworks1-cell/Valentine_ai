package com.example.aza.conversation

import com.example.aza.executive.ExecutiveBrain
import com.example.data.database.*
import com.example.data.repository.AppRepository

data class UnifiedContext(
    val recentChatHistory: List<ChatMessage>,
    val portfolioCash: Double,
    val holdings: List<PortfolioAsset>,
    val contentQueueCount: Int,
    val openGigsCount: Int,
    val pendingTradesCount: Int,
    val pendingTasksCount: Int,
    val activeMissions: List<AzaMission>,
    val knowledgeInsightsCount: Int,
    val relationshipProfile: RelationshipProfile,
    val systemUptime: Float,
    val planningAccuracy: Float,
    val agentUtilization: Float,
    val resourceEfficiency: Float,
    val brainRecommendations: List<String>,
    val isBinanceConnected: Boolean,
    val binanceApiKeyMasked: String,
    val binanceMode: String
) {
    fun formatToContextBlock(): String {
        val binanceLine = if (isBinanceConnected) {
            "[Binance Integration] CONNECTED in $binanceMode mode (Key: $binanceApiKeyMasked)"
        } else {
            "[Binance Integration] NOT CONNECTED / No API keys linked"
        }
        return """
            --- UNIFIED SYSTEM CONTEXT ---
            [Portfolio & Balance] Cash Balance: $${String.format("%.2f", portfolioCash)} USD. Holdings: ${if (holdings.isEmpty()) "None" else holdings.joinToString(", ") { "${it.symbol} (${it.amountOwned} units)" }}
            $binanceLine
            [Executive Brain State] Planning Accuracy: $planningAccuracy%, Agent Utilization: $brainRecommendations, System Uptime: $systemUptime%
            [Pending Authorizations] Trade Proposals: $pendingTradesCount pending, Agent Tasks: $pendingTasksCount pending
            [Missions Engine] Active Missions: ${if (activeMissions.isEmpty()) "None" else activeMissions.joinToString(", ") { "${it.title} (${(it.progress * 100).toInt()}% progress)" }}
            [Knowledge Base] Total Learned Insights: $knowledgeInsightsCount
            [Content Studio] $contentQueueCount drafts in queue
            [Freelance Studio] $openGigsCount active contracts/gigs listed
            [Relationship Memory Profile] Preferred style: ${relationshipProfile.preferredCommunicationStyle}. Goals: ${relationshipProfile.longTermGoals.joinToString("; ")}. Recurring Projects: ${relationshipProfile.recurringProjects.joinToString("; ")}
            ------------------------------
        """.trimIndent()
    }
}

object ContextResolver {

    fun resolve(
        repository: AppRepository,
        brain: ExecutiveBrain,
        memory: ConversationMemory,
        chatMessages: List<ChatMessage>,
        portfolioCash: Double,
        holdings: List<PortfolioAsset>,
        contentQueue: List<ContentItem>,
        openGigs: List<FreelanceGig>,
        pendingTrades: List<TradeProposalEntity>,
        pendingTasks: List<AzaAgentTask>,
        activeMissions: List<AzaMission>,
        knowledge: List<KnowledgeEntry>
    ): UnifiedContext {
        // Retrieve Binance integration status dynamically
        val binanceState = kotlinx.coroutines.runBlocking { repository.getCapabilityStateByNameDirect("Binance Capability") }
        val apiKey = try { com.example.aza.security.SecureCredentialStore.getBinanceApiKey() } catch(e: Exception) { "" }.ifBlank { binanceState?.apiKey ?: "" }
        val apiSecret = try { com.example.aza.security.SecureCredentialStore.getBinanceApiSecret() } catch(e: Exception) { "" }.ifBlank { binanceState?.apiSecret ?: "" }
        val mode = try { com.example.aza.security.SecureCredentialStore.getBinanceTradingMode() } catch(e: Exception) { "" }.ifBlank { binanceState?.operatingMode ?: "MOCK" }
        
        val isConnected = apiKey.isNotEmpty() && apiSecret.isNotEmpty() && (binanceState?.isEnabled != false)
        val maskedKey = if (apiKey.length > 8) apiKey.take(4) + "..." + apiKey.takeLast(4) else "None"

        return UnifiedContext(
            recentChatHistory = chatMessages.takeLast(10),
            portfolioCash = portfolioCash,
            holdings = holdings,
            contentQueueCount = contentQueue.size,
            openGigsCount = openGigs.size,
            pendingTradesCount = pendingTrades.count { it.status == "PENDING" },
            pendingTasksCount = pendingTasks.size,
            activeMissions = activeMissions,
            knowledgeInsightsCount = knowledge.size,
            relationshipProfile = memory.relationshipProfile,
            systemUptime = brain.systemUptime.value,
            planningAccuracy = brain.planningAccuracy.value,
            agentUtilization = brain.agentUtilization.value,
            resourceEfficiency = brain.resourceEfficiency.value,
            brainRecommendations = brain.recommendations.value,
            isBinanceConnected = isConnected,
            binanceApiKeyMasked = maskedKey,
            binanceMode = mode
        )
    }
}
