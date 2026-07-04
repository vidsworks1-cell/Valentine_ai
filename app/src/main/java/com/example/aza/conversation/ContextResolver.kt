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
    val brainRecommendations: List<String>
) {
    fun formatToContextBlock(): String {
        return """
            --- UNIFIED SYSTEM CONTEXT ---
            [Portfolio & Balance] Cash Balance: $${String.format("%.2f", portfolioCash)} USD. Holdings: ${if (holdings.isEmpty()) "None" else holdings.joinToString(", ") { "${it.symbol} (${it.amountOwned} units)" }}
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
            brainRecommendations = brain.recommendations.value
        )
    }
}
