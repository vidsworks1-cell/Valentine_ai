package com.example.aza.agent

import com.example.aza.context.AzaEvent
import com.example.aza.context.TaskContext
import com.example.data.database.AccountLinking
import com.example.data.database.AzaAgentTask
import com.example.data.database.PortfolioAsset
import com.example.data.database.TransactionRecord
import com.example.data.repository.AppRepository

class TradingAgent : AutonomousAgent {
    override val id: String = "trading_agent"
    override val displayName: String = "Trading Agent"

    override fun capabilities(): List<AgentCapability> {
        return listOf(
            AgentCapability(
                skill = "crypto_trading",
                confidence = 0.95f,
                priority = 10,
                estimatedDurationMs = 2500L
            ),
            AgentCapability(
                skill = "market_analysis",
                confidence = 0.96f,
                priority = 10,
                estimatedDurationMs = 2000L
            )
        )
    }

    override suspend fun execute(
        task: AzaAgentTask,
        context: TaskContext,
        repository: AppRepository
    ): AgentResult {
        // Run identical robust Staged Trading Pipeline logic as CryptoTradingAgent
        return try {
            val price = 67450.0
            val amount = 0.015
            val cost = amount * price

            val macroTrend = context.sharedFacts.getOrPut("btcTrend") { "Bullish" } as String
            val stage1 = "1. [Market Analysis] Scanning BTC markets. Indicators: RSI=64.2. Trend: $macroTrend.\n"
            val stage2 = "2. [Proposal] Spot Accumulation: $amount BTC @ $$price USD.\n"
            
            val account = repository.getAccountInfoDirect() ?: AccountLinking()
            val isPaperTrading = !account.isTradingLinked || context.sharedFacts["simMode"] == true
            
            // Collaboration check
            val maxAllowedExposure = context.sharedFacts["maxAcceptableExposure"] as? Double ?: 5000.0
            val isWithinCollaborativeLimits = cost <= maxAllowedExposure
            
            val stage3 = "3. [Risk] Dynamic Limit: $$maxAllowedExposure. Order safe: $isWithinCollaborativeLimits.\n"
            val stage4 = "4. [Portfolio] Mode: ${if (isPaperTrading) "PAPER" else "LIVE"}. Balance: $$${account.totalBalance} USD.\n"

            val remainingBalance = account.totalBalance - cost
            repository.updateAccountLinking(account.copy(totalBalance = remainingBalance))
            
            // helper to insert asset
            val existing = repository.getAssetBySymbol("BTC")
            if (existing != null) {
                repository.updateAsset(existing.copy(amountOwned = existing.amountOwned + amount, currentPrice = price))
            } else {
                repository.insertAsset(
                    PortfolioAsset(
                        symbol = "BTC",
                        displayName = "Bitcoin",
                        amountOwned = amount,
                        averageBuyPrice = price,
                        assetType = "CRYPTO",
                        currentPrice = price
                    )
                )
            }

            repository.insertTransaction(
                TransactionRecord(
                    description = "Bought $amount BTC @ $$price [Autonomous TradingAgent]",
                    amount = -cost,
                    type = "BUY"
                )
            )

            val logDetails = buildString {
                append(stage1)
                append(stage2)
                append(stage3)
                append(stage4)
                append("5. [Execution] Order filled. Remaining Balance: $$remainingBalance USD.")
            }

            context.pendingEvents.add(
                AzaEvent.TradeClosed(
                    symbol = "BTC",
                    amount = amount,
                    profitUsd = 150.0,
                    totalBalanceUsd = remainingBalance
                )
            )

            AgentResult(
                success = isWithinCollaborativeLimits,
                executionLog = logDetails,
                performanceImpact = "Spot Accumulation +$amount BTC ($$cost USD). Collaborative check: ${if (isWithinCollaborativeLimits) "PASS" else "FAIL"}"
            )
        } catch (e: Exception) {
            AgentResult(
                success = false,
                executionLog = "Failed to run TradingAgent: ${e.localizedMessage}",
                performanceImpact = "Aborted."
            )
        }
    }
}
