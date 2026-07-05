package com.example.aza.strategy

import com.example.data.database.StrategyBacktestReport
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class MarketCandle(
    val timestamp: Long, // in minutes since reference
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class BacktestTrade(
    val entryPrice: Double,
    val exitPrice: Double,
    val entryTime: Long,
    val exitTime: Long,
    val profit: Double,
    val isWin: Boolean,
    val durationMin: Long
)

object BacktestEngine {

    fun generateMockCandles(market: String, seedPrice: Double, count: Int, pattern: String = "TREND"): List<MarketCandle> {
        val candles = mutableListOf<MarketCandle>()
        var currentPrice = seedPrice
        var currentTime = System.currentTimeMillis() - (count * 60 * 1000L) // Count minutes in the past

        val random = java.util.Random(42) // Stable seed for reproducibility

        for (i in 0 until count) {
            val changePercent = when (pattern) {
                "TREND" -> {
                    // Overall upward momentum
                    val drift = 0.05 // upward drift
                    (random.nextGaussian() * 1.2) + drift
                }
                "CYCLIC" -> {
                    // Oscillating wave
                    val angle = (i.toDouble() / count) * 4.0 * Math.PI
                    val drift = Math.sin(angle) * 1.8
                    (random.nextGaussian() * 0.8) + drift
                }
                else -> {
                    // Random walk
                    random.nextGaussian() * 1.5
                }
            }

            val open = currentPrice
            val close = currentPrice * (1.0 + (changePercent / 100.0))
            val high = max(open, close) * (1.0 + (abs(random.nextGaussian()) * 0.5 / 100.0))
            val low = kotlin.math.min(open, close) * (1.0 - (abs(random.nextGaussian()) * 0.5 / 100.0))
            val volume = 1000.0 + random.nextDouble() * 5000.0

            candles.add(MarketCandle(currentTime, open, high, low, close, volume))
            currentPrice = close
            currentTime += 60 * 1000L // Increment 1 minute
        }
        return candles
    }

    fun runBacktest(
        strategy: TradingStrategy,
        market: String,
        candles: List<MarketCandle>,
        initialBalance: Double = 10000.0
    ): StrategyBacktestReport {
        if (candles.isEmpty()) {
            return StrategyBacktestReport(
                strategyName = strategy.name,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRate = 0.0,
                netProfit = 0.0,
                averageProfit = 0.0,
                averageLoss = 0.0,
                profitFactor = 1.0,
                maxDrawdown = 0.0,
                averageTradeDuration = 0L,
                reportDetails = "No candle data available."
            )
        }

        var cash = initialBalance
        var positionSize = 0.0 // Units
        var entryPrice = 0.0
        var entryTime = 0L
        var stopLoss = 0.0
        var takeProfit = 0.0

        val completedTrades = mutableListOf<BacktestTrade>()
        var peakValue = initialBalance
        var maxDrawdownVal = 0.0

        val closingPrices = mutableListOf<Double>()

        for (i in candles.indices) {
            val candle = candles[i]
            closingPrices.add(candle.close)

            val currentPortfolioValue = cash + (positionSize * candle.close)
            if (currentPortfolioValue > peakValue) {
                peakValue = currentPortfolioValue
            } else if (peakValue > 0.0) {
                val dd = ((peakValue - currentPortfolioValue) / peakValue) * 100.0
                if (dd > maxDrawdownVal) {
                    maxDrawdownVal = dd
                }
            }

            if (positionSize == 0.0) {
                // Look for entry
                val shouldEnter = strategy.shouldEnter(market, closingPrices)
                if (shouldEnter) {
                    // Position Sizing: Risk 2% of equity
                    val maxLossAllowed = cash * 0.02
                    val tempStopLoss = strategy.calculateStopLoss(market, candle.close, closingPrices)
                    val slDistance = candle.close - tempStopLoss
                    val targetUnits = if (slDistance > 0) maxLossAllowed / slDistance else (cash * 0.1) / candle.close

                    val simResult = PaperExecutionSimulator.simulateMarketOrder(market, isBuy = true, targetSize = targetUnits, limitOrSignalPrice = candle.close)
                    entryPrice = simResult.averageExecutionPrice
                    entryTime = candle.timestamp
                    stopLoss = strategy.calculateStopLoss(market, entryPrice, closingPrices)
                    takeProfit = strategy.calculateTakeProfit(market, entryPrice, closingPrices)
                    
                    // Cap to available cash
                    positionSize = kotlin.math.min(simResult.filledSize, cash / entryPrice)
                    cash -= (positionSize * entryPrice) + simResult.feesPaid
                }
            } else {
                // We have a position. Check exit conditions.
                var exitPrice = 0.0
                var exitReason = ""
                var shouldExit = false

                if (candle.low <= stopLoss) {
                    exitPrice = stopLoss
                    exitReason = "Stop Loss Hit"
                    shouldExit = true
                } else if (candle.high >= takeProfit) {
                    exitPrice = takeProfit
                    exitReason = "Take Profit Hit"
                    shouldExit = true
                } else if (strategy.shouldExit(market, closingPrices, positionSize)) {
                    exitPrice = candle.close
                    exitReason = "Strategy Signal Exit"
                    shouldExit = true
                }

                if (shouldExit) {
                    val isSL = exitReason == "Stop Loss Hit"
                    val isTP = exitReason == "Take Profit Hit"
                    val simResult = if (isSL || isTP) {
                        PaperExecutionSimulator.simulateTriggerOrder(market, isStopLoss = isSL, positionSize = positionSize, triggerPrice = exitPrice)
                    } else {
                        PaperExecutionSimulator.simulateMarketOrder(market, isBuy = false, targetSize = positionSize, limitOrSignalPrice = exitPrice)
                    }

                    exitPrice = simResult.averageExecutionPrice
                    val grossProceeds = positionSize * exitPrice
                    val tradeProfit = grossProceeds - (positionSize * entryPrice) - simResult.feesPaid
                    cash += grossProceeds - simResult.feesPaid

                    val durationMs = candle.timestamp - entryTime
                    val durationMin = max(1, durationMs / (60 * 1000))

                    completedTrades.add(
                        BacktestTrade(
                            entryPrice = entryPrice,
                            exitPrice = exitPrice,
                            entryTime = entryTime,
                            exitTime = candle.timestamp,
                            profit = tradeProfit,
                            isWin = tradeProfit > 0,
                            durationMin = durationMin
                        )
                    )

                    positionSize = 0.0
                }
            }
        }

        // Close any outstanding position at the end
        if (positionSize > 0.0) {
            val finalCandle = candles.last()
            val finalPrice = finalCandle.close
            val grossProceeds = positionSize * finalPrice
            val tradeProfit = grossProceeds - (positionSize * entryPrice)
            cash += grossProceeds

            val durationMs = finalCandle.timestamp - entryTime
            val durationMin = max(1, durationMs / (60 * 1000))

            completedTrades.add(
                BacktestTrade(
                    entryPrice = entryPrice,
                    exitPrice = finalPrice,
                    entryTime = entryTime,
                    exitTime = finalCandle.timestamp,
                    profit = tradeProfit,
                    isWin = tradeProfit > 0,
                    durationMin = durationMin
                )
            )
        }

        // Calculations
        val total = completedTrades.size
        val winning = completedTrades.count { it.isWin }
        val losing = total - winning
        val winRate = if (total > 0) (winning.toDouble() / total) * 100.0 else 0.0
        val netProfit = cash - initialBalance

        val winningTradesList = completedTrades.filter { it.isWin }
        val losingTradesList = completedTrades.filter { !it.isWin }

        val avgProfit = if (winning > 0) winningTradesList.map { it.profit }.average() else 0.0
        val avgLoss = if (losing > 0) losingTradesList.map { it.profit }.average() else 0.0

        val grossWins = winningTradesList.sumOf { it.profit }
        val grossLosses = abs(losingTradesList.sumOf { it.profit })
        val profitFactor = if (grossLosses > 0.0) grossWins / grossLosses else if (grossWins > 0.0) 99.9 else 1.0

        val avgDuration = if (total > 0) completedTrades.map { it.durationMin }.average().toLong() else 0L

        // Details Report Builder
        val detailsSb = StringBuilder()
        detailsSb.append("### BACKTEST REPORT: ${strategy.name} (v${strategy.version}) ###\n")
        detailsSb.append("Market: $market | Mode: ${strategy.timeframe} simulation\n")
        detailsSb.append("--------------------------------------------------\n")
        detailsSb.append("• Starting Equity: $${String.format(Locale.US, "%,.2f", initialBalance)} USD\n")
        detailsSb.append("• Ending Equity: $${String.format(Locale.US, "%,.2f", cash)} USD\n")
        detailsSb.append("• Net Profit/Loss: ${if (netProfit >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", netProfit)} USD\n")
        detailsSb.append("• Win Rate: ${String.format(Locale.US, "%.1f", winRate)}% ($winning W / $losing L)\n")
        detailsSb.append("• Profit Factor: ${String.format(Locale.US, "%.2f", profitFactor)}\n")
        detailsSb.append("• Max Drawdown: ${String.format(Locale.US, "%.2f", maxDrawdownVal)}%\n")
        detailsSb.append("• Avg Trade Duration: $avgDuration mins\n")
        detailsSb.append("--------------------------------------------------\n")
        
        val sampleSizeValidation = when {
            total >= 30 -> "✅ STATISTICALLY SIGNIFICANT SAMPLE (N = $total >= 30). This backtest exhibits a robust sample size satisfying the Central Limit Theorem. Strategy metrics have high predictive and descriptive validity."
            total >= 10 -> "⚠️ MODERATE SAMPLE SIZE (10 <= N = $total < 30). These metrics offer decent preliminary insight, but caution is advised. Run with 500+ or 1000+ candles to achieve high statistical significance."
            else -> "❌ INSUFFICIENT SAMPLE SIZE (N = $total < 10). The trade count is too low to produce statistically meaningful metrics. Highly susceptible to over-fitting and random variance. Recommend increasing the history length (candles) to trigger more trading opportunities."
        }
        detailsSb.append("### STATISTICAL SAMPLE VALIDATION ###\n")
        detailsSb.append("$sampleSizeValidation\n")
        detailsSb.append("--------------------------------------------------\n")
        
        detailsSb.append("### Trade Ledger (Sample):\n")
        completedTrades.take(15).forEachIndexed { index, t ->
            val winStr = if (t.isWin) "WIN" else "LOSS"
            detailsSb.append("${index + 1}. [$winStr] Entry: $${String.format(Locale.US, "%,.2f", t.entryPrice)} | Exit: $${String.format(Locale.US, "%,.2f", t.exitPrice)} | Profit: ${if (t.profit >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", t.profit)} | Dur: ${t.durationMin}m\n")
        }
        if (completedTrades.size > 15) {
            detailsSb.append("... and ${completedTrades.size - 15} more trades logged.")
        }

        return StrategyBacktestReport(
            strategyName = strategy.name,
            totalTrades = total,
            winningTrades = winning,
            losingTrades = losing,
            winRate = winRate,
            netProfit = netProfit,
            averageProfit = avgProfit,
            averageLoss = avgLoss,
            profitFactor = profitFactor,
            maxDrawdown = maxDrawdownVal,
            averageTradeDuration = avgDuration,
            reportDetails = detailsSb.toString()
        )
    }
}
