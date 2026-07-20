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
    val binanceMode: String,
    val tradingBudget: Double,
    val tradingBudgetPeriod: String,
    val tradingRiskLevel: String,
    val atrMultiplier: Double,
    val positionSlicePercent: Double,
    val binanceLiveBalances: String = ""
) {
    fun formatToContextBlock(): String {
        val binanceLine = if (isBinanceConnected) {
            "[Binance Integration] CONNECTED in $binanceMode mode (Key: $binanceApiKeyMasked)\n            [Binance Live Balances] $binanceLiveBalances"
        } else {
            "[Binance Integration] NOT CONNECTED / No API keys linked"
        }
        val budgetLine = if (tradingBudget > 0) {
            "[Lumina Trading Budget] Allocated: $${String.format("%.2f", tradingBudget)} USD ($tradingBudgetPeriod) | Risk Profile: $tradingRiskLevel | ATR Stop Weight: ${atrMultiplier}x | Position Slice: ${positionSlicePercent}%"
        } else {
            "[Lumina Trading Budget] No budget currently allocated | Risk Profile: $tradingRiskLevel | ATR Stop Weight: ${atrMultiplier}x | Position Slice: ${positionSlicePercent}%"
        }
        return """
            --- UNIFIED SYSTEM CONTEXT ---
            [Portfolio & Balance] Cash Balance: $${String.format("%.2f", portfolioCash)} USD. Holdings: ${if (holdings.isEmpty()) "None" else holdings.joinToString(", ") { "${it.symbol} (${it.amountOwned} units)" }}
            $binanceLine
            $budgetLine
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
        
        val budget = try { com.example.aza.security.SecureCredentialStore.getTradingBudget() } catch (e: Exception) { 0.0 }
        val budgetPeriod = try { com.example.aza.security.SecureCredentialStore.getTradingBudgetPeriod() } catch (e: Exception) { "daily" }
        val riskLevel = try { com.example.aza.security.SecureCredentialStore.getTradingRiskLevel() } catch (e: Exception) { "CONSERVATIVE" }
        val atrMultiplier = try { com.example.aza.security.SecureCredentialStore.getAtrMultiplier() } catch (e: Exception) { 2.0 }
        val positionSlicePercent = try { com.example.aza.security.SecureCredentialStore.getPositionSlicePercent() } catch (e: Exception) { 10.0 }

        val liveBalancesText = if (isConnected) {
            val result = try {
                kotlinx.coroutines.runBlocking { com.example.aza.connector.BinanceConnector.fetchAccountBalances() }
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (result.isSuccess) {
                val list = result.getOrThrow()
                if (list.isEmpty()) {
                    "No assets with non-zero balances found on Binance."
                } else {
                    list.joinToString(", ") { "${it.asset}: ${String.format(java.util.Locale.US, "%.6f", it.total)} (Free: ${String.format(java.util.Locale.US, "%.6f", it.free)}, Locked: ${String.format(java.util.Locale.US, "%.6f", it.locked)})" }
                }
            } else {
                "Failed to fetch live balances: ${result.exceptionOrNull()?.message}"
            }
        } else {
            "N/A (Integration inactive)"
        }

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
            binanceMode = mode,
            tradingBudget = budget,
            tradingBudgetPeriod = budgetPeriod,
            tradingRiskLevel = riskLevel,
            atrMultiplier = atrMultiplier,
            positionSlicePercent = positionSlicePercent,
            binanceLiveBalances = liveBalancesText
        )
    }
}
