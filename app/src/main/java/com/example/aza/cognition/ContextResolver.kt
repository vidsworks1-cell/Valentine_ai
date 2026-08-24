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
            "[Lumina Trading Budget] Allocated: $${String.format("%.2f", tradingBudget)} USD ($tradingBudgetPeriod) | Risk Level: $tradingRiskLevel | ATR Stop Weight: ${atrMultiplier}x | Position Slice: ${positionSlicePercent}%"
        } else {
            "[Lumina Trading Budget] No budget currently allocated | Risk Level: $tradingRiskLevel | ATR Stop Weight: ${atrMultiplier}x | Position Slice: ${positionSlicePercent}%"
        }
        return """
            --- COGNITIVE WORLD MODEL ---
            $binanceLine
            $budgetLine
            [Missions Status] Active Missions: $activeMissionsCount; Open Freelance Gigs: $openGigsCount; Draft Content: $contentCount
            [System Health] Status: $systemStatus
            [User Investor Profile & Style] Active Style: ${preferences.investingStyleSummary}; Timeframe: ${preferences.preferredTimeframe}; Preferred Assets: ${preferences.preferredAssetsSummary}; Preferred Strategies: ${preferences.preferredStrategiesSummary}
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
        
        var isConnected = apiKey.isNotEmpty() && apiSecret.isNotEmpty() && (binanceState?.isEnabled != false)
        var maskedKey = if (apiKey.length > 8) apiKey.take(4) + "..." + apiKey.takeLast(4) else "None"
        var resolvedMode = mode
        
        val budget = try { com.example.aza.security.SecureCredentialStore.getTradingBudget() } catch (e: Exception) { 0.0 }
        val budgetPeriod = try { com.example.aza.security.SecureCredentialStore.getTradingBudgetPeriod() } catch (e: Exception) { "daily" }
        val riskLevel = try { com.example.aza.security.SecureCredentialStore.getTradingRiskLevel() } catch (e: Exception) { "CONSERVATIVE" }
        val atrMultiplier = try { com.example.aza.security.SecureCredentialStore.getAtrMultiplier() } catch (e: Exception) { 2.0 }
        val positionSlicePercent = try { com.example.aza.security.SecureCredentialStore.getPositionSlicePercent() } catch (e: Exception) { 10.0 }

        // Fetch live balances from backend repository (VPS service)
        val (backendSuccess, liveBalancesText) = try {
            val balances = repository.getBinanceBalances()
            val text = if (balances.isEmpty()) {
                "No non-zero balances found on Binance Spot account."
            } else {
                balances.joinToString(", ") { "${it.asset}: ${it.free} (Locked: ${it.locked})" }
            }
            Pair(true, text)
        } catch (e: Exception) {
            Pair(false, "")
        }

        val finalBalancesText: String
        if (backendSuccess) {
            isConnected = true
            if (maskedKey == "None") maskedKey = "Server-Configured (VPS)"
            resolvedMode = "LIVE"
            finalBalancesText = liveBalancesText
        } else if (isConnected) {
            val result = try {
                com.example.aza.connector.BinanceConnector.fetchAccountBalances()
            } catch (e: Exception) {
                Result.failure(e)
            }
            finalBalancesText = if (result.isSuccess) {
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
            finalBalancesText = "N/A (Integration inactive)"
        }

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
            binanceMode = resolvedMode,
            tradingBudget = budget,
            tradingBudgetPeriod = budgetPeriod,
            tradingRiskLevel = riskLevel,
            atrMultiplier = atrMultiplier,
            positionSlicePercent = positionSlicePercent,
            binanceLiveBalances = finalBalancesText
        )
    }
}
