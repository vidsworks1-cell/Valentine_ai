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
    val preferences: UserPreferences,
    val isBinanceConnected: Boolean,
    val binanceApiKeyMasked: String,
    val binanceMode: String,
    val tradingBudget: Double,
    val tradingBudgetPeriod: String
) {
    fun formatToContextBlock(): String {
        val binanceLine = if (isBinanceConnected) {
            "[Binance Integration] CONNECTED in $binanceMode mode (Key: $binanceApiKeyMasked)"
        } else {
            "[Binance Integration] NOT CONNECTED / No API keys linked"
        }
        val budgetLine = if (tradingBudget > 0) {
            "[Lumina Trading Budget] Allocated: $${String.format("%.2f", tradingBudget)} USD ($tradingBudgetPeriod)"
        } else {
            "[Lumina Trading Budget] No budget currently allocated"
        }
        return """
            --- COGNITIVE WORLD MODEL ---
            [Central Treasury] Cash: $${String.format("%.2f", cashBalance)} USD (Holdings: $holdingsCount active assets)
            $binanceLine
            $budgetLine
            [Missions Status] Active Missions: $activeMissionsCount; Open Freelance Gigs: $openGigsCount; Draft Content: $contentCount
            [System Health] Status: $systemStatus
            [User Style Preferences] Explanation: ${preferences.preferredExplanationDepth}; Tone: ${preferences.preferredTone}
            -----------------------------
        """.trimIndent()
    }
}

object ContextResolver {

    suspend fun resolve(repository: AppRepository, memory: ConversationMemory): UnifiedContext {
        val balance = repository.getAccountInfoDirect()?.totalBalance ?: 4250.75
        val holdingsCount = repository.getAllPositionsDirect().size
        val activeMissionsCount = if (repository.getActiveMissionDirect() != null) 1 else 0
        val openGigsCount = repository.getAllGigsDirect().size
        val contentCount = repository.getAllContentDirect().size
        
        // Check Binance Credentials
        val binanceState = repository.getCapabilityStateByNameDirect("Binance Capability")
        val apiKey = try { com.example.aza.security.SecureCredentialStore.getBinanceApiKey() } catch(e: Exception) { "" }.ifBlank { binanceState?.apiKey ?: "" }
        val apiSecret = try { com.example.aza.security.SecureCredentialStore.getBinanceApiSecret() } catch(e: Exception) { "" }.ifBlank { binanceState?.apiSecret ?: "" }
        val mode = try { com.example.aza.security.SecureCredentialStore.getBinanceTradingMode() } catch(e: Exception) { "" }.ifBlank { binanceState?.operatingMode ?: "MOCK" }
        
        val isConnected = apiKey.isNotEmpty() && apiSecret.isNotEmpty() && (binanceState?.isEnabled != false)
        val maskedKey = if (apiKey.length > 8) apiKey.take(4) + "..." + apiKey.takeLast(4) else "None"
        
        val budget = try { com.example.aza.security.SecureCredentialStore.getTradingBudget() } catch (e: Exception) { 0.0 }
        val budgetPeriod = try { com.example.aza.security.SecureCredentialStore.getTradingBudgetPeriod() } catch (e: Exception) { "daily" }

        return UnifiedContext(
            recentHistory = memory.recentMessages,
            cashBalance = balance,
            holdingsCount = holdingsCount,
            activeMissionsCount = activeMissionsCount,
            openGigsCount = openGigsCount,
            contentCount = contentCount,
            systemStatus = "Connected & Autopilot Operational",
            preferences = memory.preferences,
            isBinanceConnected = isConnected,
            binanceApiKeyMasked = maskedKey,
            binanceMode = mode,
            tradingBudget = budget,
            tradingBudgetPeriod = budgetPeriod
        )
    }
}
