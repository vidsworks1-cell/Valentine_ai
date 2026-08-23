package com.example.aza.strategy

import com.example.data.api.BinanceBalance
import com.example.data.api.ContaboTrade
import com.example.data.api.ExecutionReconciliationResponse
import com.example.data.api.StaleTradeMismatch
import com.example.data.database.BinancePositionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RealBinanceHolding(
    val symbol: String,
    val baseAsset: String,
    val free: Double,
    val locked: Double,
    val total: Double,
    val estimatedValueUsd: Double = 0.0
)

data class PositionSyncResult(
    val prunedGhostPositions: List<BinancePositionEntity>,
    val verifiedRealPositions: List<BinancePositionEntity>,
    val realBinanceHoldings: List<RealBinanceHolding>,
    val syncLogs: List<String>
)

object ExecutionReconciliationEngine {

    /**
     * Extracts the base asset ticker from a trading symbol.
     * e.g., "BTCUSDT" -> "BTC", "ETHUSDT" -> "ETH", "SOLUSDT" -> "SOL", "BNBUSDT" -> "BNB", "BTC" -> "BTC"
     */
    fun extractBaseAsset(symbol: String): String {
        val clean = symbol.uppercase().trim()
        return when {
            clean.endsWith("USDT") -> clean.removeSuffix("USDT")
            clean.endsWith("BUSD") -> clean.removeSuffix("BUSD")
            clean.endsWith("USDC") -> clean.removeSuffix("USDC")
            clean.endsWith("FDUSD") -> clean.removeSuffix("FDUSD")
            clean.endsWith("USD") -> clean.removeSuffix("USD")
            else -> clean
        }
    }

    /**
     * Pre-execution safety guard to prevent impossible close orders.
     * Checks if the account actually holds the base asset on Binance Spot.
     * Returns (canExecute, reason).
     */
    fun validateCanExecuteClose(
        symbol: String,
        requestedQty: Double,
        spotBalances: List<BinanceBalance>
    ): Pair<Boolean, String> {
        val baseAsset = extractBaseAsset(symbol)
        val balance = spotBalances.find { it.asset.equals(baseAsset, ignoreCase = true) }
        val free = balance?.free ?: 0.0

        return when {
            balance == null || (free <= 0.000001 && (balance.locked <= 0.000001)) -> {
                Pair(
                    false,
                    "Zero Binance Spot balance for $baseAsset (0.00 available). Order aborted to prevent impossible exchange rejection."
                )
            }
            free < 0.000001 && balance.locked > 0.000001 -> {
                Pair(
                    false,
                    "All $baseAsset on Binance Spot is currently locked in other open limit orders (${balance.locked} locked). Cancel existing orders first."
                )
            }
            else -> {
                Pair(true, "Verified Binance Spot balance: $free $baseAsset available.")
            }
        }
    }

    /**
     * AUTOMATIC SYNCHRONIZATION ENGINE:
     * 1. Compares internal positions (status == "OPEN") with Binance Spot balances.
     * 2. Automatically flags and removes/closes positions that no longer exist on Binance (ghosts).
     * 3. Retains and updates verified positions that match real Binance holdings.
     * 4. Identifies untracked real Binance holdings so Lumina only manages real exchange holdings.
     */
    fun autoSynchronizeInternalPositions(
        activeDbPositions: List<BinancePositionEntity>,
        spotBalances: List<BinanceBalance>,
        currentPrices: Map<String, Double> = emptyMap()
    ): PositionSyncResult {
        val prunedGhostPositions = mutableListOf<BinancePositionEntity>()
        val verifiedRealPositions = mutableListOf<BinancePositionEntity>()
        val syncLogs = mutableListOf<String>()

        val balanceMap = spotBalances.associateBy { it.asset.uppercase() }
        val activePositions = activeDbPositions.filter { it.status == "OPEN" && it.closedAt == null }

        for (pos in activePositions) {
            val baseAsset = extractBaseAsset(pos.symbol)
            val spotBal = balanceMap[baseAsset]
            val spotFree = spotBal?.free ?: 0.0
            val spotLocked = spotBal?.locked ?: 0.0
            val totalSpot = spotFree + spotLocked

            if (totalSpot < 0.000001) {
                // Ghost position! Exists in local DB / memory, but 0 balance on Binance Spot.
                val price = currentPrices[pos.symbol] ?: pos.currentPrice.takeIf { it > 0.0 } ?: pos.entryPrice
                val pruned = pos.copy(
                    status = "CLOSED",
                    currentPrice = price,
                    closedAt = System.currentTimeMillis()
                )
                prunedGhostPositions.add(pruned)
                syncLogs.add("Auto-pruned ghost position for ${pos.symbol}: 0.00 $baseAsset on Binance Spot. Prevented impossible close order retries.")
            } else {
                // Verified real holding!
                val price = currentPrices[pos.symbol] ?: pos.currentPrice.takeIf { it > 0.0 } ?: pos.entryPrice
                val verified = pos.copy(
                    amountOwned = totalSpot,
                    currentPrice = price
                )
                verifiedRealPositions.add(verified)
            }
        }

        // Aggregate real Binance non-quote holdings
        val quoteCurrencies = setOf("USDT", "USDC", "BUSD", "FDUSD", "USD", "EUR")
        val realHoldings = spotBalances
            .filter { !quoteCurrencies.contains(it.asset.uppercase()) && (it.free + it.locked) > 0.0001 }
            .map { bal ->
                val sym = "${bal.asset.uppercase()}USDT"
                val price = currentPrices[sym] ?: 0.0
                val total = bal.free + bal.locked
                RealBinanceHolding(
                    symbol = sym,
                    baseAsset = bal.asset.uppercase(),
                    free = bal.free,
                    locked = bal.locked,
                    total = total,
                    estimatedValueUsd = total * price
                )
            }

        return PositionSyncResult(
            prunedGhostPositions = prunedGhostPositions,
            verifiedRealPositions = verifiedRealPositions,
            realBinanceHoldings = realHoldings,
            syncLogs = syncLogs
        )
    }

    /**
     * Executes real-time reconciliation comparing:
     * 1. Binance Spot Balances (Free + Locked)
     * 2. Active Positions & Trades where `exit_time IS NULL` or `status == "OPEN"`
     *
     * Detects:
     * - ORPHAN_DATABASE_POSITION: Open in DB, but 0.00 balance on Binance Spot
     * - UNTRACKED_SPOT_HOLDING: Positive balance on Binance Spot, but missing in DB
     * - SIZE_MISMATCH: Significant divergence between DB tracked quantity and exchange balance
     * - STALE_TIME_BREACH: Trade open > 48 hours without update
     */
    fun reconcileSpotBalancesWithOpenTrades(
        spotBalances: List<BinanceBalance>,
        activeDbPositions: List<BinancePositionEntity>,
        contaboTrades: List<ContaboTrade> = emptyList(),
        timeDriftMs: Double = -12.0,
        rateLimitAvailable: Int = 1200
    ): ExecutionReconciliationResponse {
        val mismatches = mutableListOf<StaleTradeMismatch>()
        val activePositions = activeDbPositions.filter { it.status == "OPEN" && it.closedAt == null }
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        // Build Spot Balance Map (Asset -> Total Free + Locked)
        val balanceMap = spotBalances.associateBy { it.asset.uppercase() }

        // 1. Audit Active Room Database Positions (exit_time IS NULL / status == "OPEN")
        for (pos in activePositions) {
            val baseAsset = extractBaseAsset(pos.symbol)
            val spotBal = balanceMap[baseAsset]
            val spotFree = spotBal?.free ?: 0.0
            val spotLocked = spotBal?.locked ?: 0.0
            val totalSpot = spotFree + spotLocked

            val positionDurationHours = (now - pos.openedAt) / (1000.0 * 3600.0)
            val currentPrice = if (pos.currentPrice > 0.0) pos.currentPrice else pos.entryPrice
            val unrealizedPnl = (currentPrice - pos.entryPrice) * pos.amountOwned

            if (totalSpot < 0.000001) {
                // Orphan database position: marked open with exit_time IS NULL, but zero exchange balance
                mismatches.add(
                    StaleTradeMismatch(
                        symbol = pos.symbol,
                        tradeId = pos.proposalId ?: pos.missionId,
                        side = "BUY",
                        trackedSize = pos.amountOwned,
                        spotFree = spotFree,
                        spotLocked = spotLocked,
                        discrepancyType = "ORPHAN_DATABASE_POSITION",
                        description = "Database has active trade (exit_time IS NULL) of ${String.format(Locale.US, "%.4f", pos.amountOwned)} $baseAsset, but Binance Spot balance is 0.00. Likely closed/filled externally.",
                        entryPrice = pos.entryPrice,
                        currentPrice = currentPrice,
                        unrealizedPnl = unrealizedPnl,
                        entryTime = dateFormat.format(Date(pos.openedAt)),
                        isStale = true
                    )
                )
            } else if (pos.amountOwned > 0.00001 && Math.abs(totalSpot - pos.amountOwned) / pos.amountOwned > 0.15) {
                // Size mismatch: significant delta between local DB and exchange spot holdings
                mismatches.add(
                    StaleTradeMismatch(
                        symbol = pos.symbol,
                        tradeId = pos.proposalId ?: pos.missionId,
                        side = "BUY",
                        trackedSize = pos.amountOwned,
                        spotFree = spotFree,
                        spotLocked = spotLocked,
                        discrepancyType = "SIZE_MISMATCH",
                        description = "Quantity delta detected: Local DB tracks ${String.format(Locale.US, "%.4f", pos.amountOwned)} $baseAsset, but Binance Spot reports ${String.format(Locale.US, "%.4f", totalSpot)} $baseAsset.",
                        entryPrice = pos.entryPrice,
                        currentPrice = currentPrice,
                        unrealizedPnl = unrealizedPnl,
                        entryTime = dateFormat.format(Date(pos.openedAt)),
                        isStale = false
                    )
                )
            } else if (positionDurationHours > 48.0) {
                // Stale time breach: open for over 48 hours without closure
                mismatches.add(
                    StaleTradeMismatch(
                        symbol = pos.symbol,
                        tradeId = pos.proposalId ?: pos.missionId,
                        side = "BUY",
                        trackedSize = pos.amountOwned,
                        spotFree = spotFree,
                        spotLocked = spotLocked,
                        discrepancyType = "STALE_TIME_BREACH",
                        description = "Stale trade duration breach: Open for ${String.format(Locale.US, "%.1f", positionDurationHours)} hours without lifecycle exit execution.",
                        entryPrice = pos.entryPrice,
                        currentPrice = currentPrice,
                        unrealizedPnl = unrealizedPnl,
                        entryTime = dateFormat.format(Date(pos.openedAt)),
                        isStale = true
                    )
                )
            }
        }

        // 2. Audit Contabo VPS Trades where exit_time is NULL
        val openContaboTrades = contaboTrades.filter { it.exitTime == null && it.outcome == null }
        for (trade in openContaboTrades) {
            val baseAsset = extractBaseAsset(trade.symbol)
            val spotBal = balanceMap[baseAsset]
            val totalSpot = (spotBal?.free ?: 0.0) + (spotBal?.locked ?: 0.0)

            // Check if already covered by Room DB mismatch
            val alreadyCovered = mismatches.any { it.symbol.equals(trade.symbol, ignoreCase = true) }
            if (!alreadyCovered && totalSpot < 0.000001) {
                mismatches.add(
                    StaleTradeMismatch(
                        symbol = trade.symbol,
                        tradeId = trade.id,
                        side = trade.side,
                        trackedSize = trade.positionSize,
                        spotFree = spotBal?.free ?: 0.0,
                        spotLocked = spotBal?.locked ?: 0.0,
                        discrepancyType = "ORPHAN_DATABASE_POSITION",
                        description = "Contabo VPS trade #${trade.id} has exit_time IS NULL, but Binance Spot balance is 0.00 $baseAsset.",
                        entryPrice = trade.entryPrice,
                        currentPrice = trade.entryPrice,
                        unrealizedPnl = 0.0,
                        entryTime = trade.entryTime,
                        isStale = true
                    )
                )
            }
        }

        // 3. Audit Untracked Spot Holdings (Binance Spot balance > 0, but no open trade with exit_time IS NULL)
        val trackedBaseAssets = (activePositions.map { extractBaseAsset(it.symbol) } + openContaboTrades.map { extractBaseAsset(it.symbol) }).toSet()
        val quoteCurrencies = setOf("USDT", "USDC", "BUSD", "FDUSD", "USD", "EUR")

        var untrackedSpotCount = 0
        for (bal in spotBalances) {
            val asset = bal.asset.uppercase()
            val total = bal.free + bal.locked
            if (!quoteCurrencies.contains(asset) && total > 0.0001 && !trackedBaseAssets.contains(asset)) {
                untrackedSpotCount++
                mismatches.add(
                    StaleTradeMismatch(
                        symbol = "${asset}USDT",
                        tradeId = null,
                        side = "BUY",
                        trackedSize = 0.0,
                        spotFree = bal.free,
                        spotLocked = bal.locked,
                        discrepancyType = "UNTRACKED_SPOT_HOLDING",
                        description = "Binance Spot holds ${String.format(Locale.US, "%.4f", total)} $asset, but no active trade (exit_time IS NULL) is tracked in Lumina.",
                        entryPrice = 0.0,
                        currentPrice = 0.0,
                        unrealizedPnl = 0.0,
                        entryTime = dateFormat.format(Date()),
                        isStale = false
                    )
                )
            }
        }

        val staleTradesCount = mismatches.count { it.isStale }
        val discrepanciesFound = mismatches.size

        val summary = when {
            discrepanciesFound == 0 -> "All ${activePositions.size} open positions and Binance Spot balances are 100% reconciled and synchronized."
            staleTradesCount > 0 -> "Found $staleTradesCount stale/orphan trades with exit_time IS NULL against zero Binance Spot balance. Safe reconciliation recommended."
            else -> "Detected $discrepanciesFound balance discrepancies between local database and Binance Spot wallet."
        }

        return ExecutionReconciliationResponse(
            status = if (discrepanciesFound == 0) "RECONCILED" else "MISMATCH_DETECTED",
            reconciledAt = dateFormat.format(Date()),
            connectionStatus = if (discrepanciesFound == 0) "CONNECTED_SYNCED" else "DISCREPANCY_FLAGGED",
            exchangeTimeDriftMs = timeDriftMs,
            rateLimitAvailable = rateLimitAvailable,
            activeLocalPositionsCount = activePositions.size,
            discrepanciesFound = discrepanciesFound,
            mismatches = mismatches,
            staleTradesCount = staleTradesCount,
            untrackedSpotCount = untrackedSpotCount,
            spotBalancesChecked = spotBalances.size,
            reconciliationSummary = summary
        )
    }

    /**
     * Generates a comprehensive markdown reconciliation audit report for clipboard export.
     */
    fun formatReconciliationAudit(response: ExecutionReconciliationResponse): String {
        return buildString {
            appendLine("🛡️ **LUMINA BINANCE SPOT RECONCILIATION AUDIT**")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("• **Timestamp**: ${response.reconciledAt ?: "Live"}")
            appendLine("• **Sync Status**: ${response.status} (${response.connectionStatus})")
            appendLine("• **Active Monitored Trades (exit_time IS NULL)**: ${response.activeLocalPositionsCount}")
            appendLine("• **Spot Balances Audited**: ${response.spotBalancesChecked} assets")
            appendLine("• **Discrepancies Found**: ${response.discrepanciesFound}")
            appendLine("• **Stale / Orphan Trades**: ${response.staleTradesCount}")
            appendLine("• **Untracked Spot Holdings**: ${response.untrackedSpotCount}")
            appendLine("• **Time Drift**: ${response.exchangeTimeDriftMs} ms | Rate Limit Remaining: ${response.rateLimitAvailable}/1200")
            appendLine("• **Executive Summary**: ${response.reconciliationSummary}")
            appendLine()

            if (response.mismatches.isNotEmpty()) {
                appendLine("### 📋 DISCREPANCY & STALE POSITION BREAKDOWN:")
                response.mismatches.forEachIndexed { idx, m ->
                    appendLine("${idx + 1}. **${m.symbol}** [${m.discrepancyType}]")
                    appendLine("   - Description: ${m.description}")
                    appendLine("   - Tracked Size: ${m.trackedSize} | Binance Free: ${m.spotFree} | Locked: ${m.spotLocked}")
                    if (m.entryPrice > 0.0) {
                        appendLine("   - Entry Price: $${String.format(Locale.US, "%,.2f", m.entryPrice)} | Est. PnL: $${String.format(Locale.US, "%+.2f", m.unrealizedPnl)}")
                    }
                    appendLine("   - Opened At: ${m.entryTime}")
                    appendLine("   - Action Recommended: ${if (m.isStale) "Safe Close & Archive to Position Reviews" else "Synchronize & Re-weight"}")
                    appendLine()
                }
            } else {
                appendLine("✅ **Zero discrepancies detected.** All open positions in database correspond directly to verified Binance Spot balances.")
            }
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }
}
