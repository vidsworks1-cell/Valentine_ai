package com.example.aza.strategy

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// Helper functions for indicators
object StrategyIndicators {
    fun calculateSMA(prices: List<Double>, period: Int): Double {
        if (prices.isEmpty()) return 0.0
        if (prices.size < period) return prices.average()
        return prices.takeLast(period).average()
    }

    fun calculateRSI(prices: List<Double>, period: Int): Double {
        if (prices.size <= period) return 50.0
        var gains = 0.0
        var losses = 0.0
        for (i in (prices.size - period) until prices.size) {
            val change = prices[i] - prices[i - 1]
            if (change > 0) gains += change else losses += abs(change)
        }
        if (losses == 0.0) return 100.0
        val rs = gains / losses
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun calculateBollingerBands(prices: List<Double>, period: Int): Triple<Double, Double, Double> {
        val sma = calculateSMA(prices, period)
        if (prices.size < period) return Triple(sma, sma, sma)
        val variance = prices.takeLast(period).map { (it - sma) * (it - sma) }.average()
        val stdDev = sqrt(variance)
        return Triple(sma, sma + 2 * stdDev, sma - 2 * stdDev)
    }
}

class TrendFollowingStrategy(
    var emaShortPeriod: Int = 12,
    var emaLongPeriod: Int = 26,
    var rsiPeriod: Int = 14
) : TradingStrategy {
    override val name = "Trend Following"
    override val version = "1.0.0"
    override val description = "Follows strong upward market momentum using exponential moving average (EMA) crossovers."
    override val supportedMarkets = listOf("BTC", "ETH", "SOL", "AAPL", "NVDA")
    override val riskProfile = "Conservative"
    override val timeframe = "4H"
    override val requiredIndicators = listOf("EMA (12, 26, 200)", "RSI (14)")

    override val designedRegimes = listOf("Trending", "Low volatility")
    override fun getParameters(): Map<String, Double> = mapOf(
        "emaShortPeriod" to emaShortPeriod.toDouble(),
        "emaLongPeriod" to emaLongPeriod.toDouble(),
        "rsiPeriod" to rsiPeriod.toDouble()
    )
    override fun setParameters(params: Map<String, Double>) {
        params["emaShortPeriod"]?.let { emaShortPeriod = it.toInt() }
        params["emaLongPeriod"]?.let { emaLongPeriod = it.toInt() }
        params["rsiPeriod"]?.let { rsiPeriod = it.toInt() }
    }

    override fun analyzeMarket(market: String, prices: List<Double>): String {
        if (prices.size < 2) return "Insufficient data"
        val emaShort = StrategyIndicators.calculateSMA(prices, emaShortPeriod)
        val emaLong = StrategyIndicators.calculateSMA(prices, emaLongPeriod)
        val rsi = StrategyIndicators.calculateRSI(prices, rsiPeriod)
        return "EMA($emaShortPeriod): ${String.format("%.2f", emaShort)}, EMA($emaLongPeriod): ${String.format("%.2f", emaLong)}, RSI: ${String.format("%.1f", rsi)}"
    }

    override fun shouldEnter(market: String, prices: List<Double>): Boolean {
        if (prices.size < emaLongPeriod) return false
        val emaShort = StrategyIndicators.calculateSMA(prices, emaShortPeriod)
        val emaLong = StrategyIndicators.calculateSMA(prices, emaLongPeriod)
        val rsi = StrategyIndicators.calculateRSI(prices, rsiPeriod)
        return emaShort > emaLong && rsi < 65.0
    }

    override fun shouldExit(market: String, prices: List<Double>, currentPosition: Double): Boolean {
        if (prices.size < emaLongPeriod || currentPosition <= 0) return false
        val emaShort = StrategyIndicators.calculateSMA(prices, emaShortPeriod)
        val emaLong = StrategyIndicators.calculateSMA(prices, emaLongPeriod)
        val rsi = StrategyIndicators.calculateRSI(prices, rsiPeriod)
        return emaShort < emaLong || rsi > 78.0
    }

    override fun calculateStopLoss(market: String, entryPrice: Double, prices: List<Double>): Double {
        return entryPrice * 0.98 // 2% SL
    }

    override fun calculateTakeProfit(market: String, entryPrice: Double, prices: List<Double>): Double {
        return entryPrice * 1.05 // 5% TP
    }

    override fun calculatePositionSize(market: String, accountBalance: Double, entryPrice: Double, riskPercent: Double): Double {
        val amountToRisk = accountBalance * (riskPercent / 100.0)
        val maxLossPerUnit = entryPrice * 0.02
        return amountToRisk / maxLossPerUnit
    }

    override fun evaluate(market: String, prices: List<Double>, accountBalance: Double, riskPercent: Double): TradeProposal {
        val lastPrice = prices.lastOrNull() ?: 0.0
        val isEnter = shouldEnter(market, prices)
        val isExit = shouldExit(market, prices, 1.0)
        val emaShort = StrategyIndicators.calculateSMA(prices, emaShortPeriod)
        val emaLong = StrategyIndicators.calculateSMA(prices, emaLongPeriod)
        val rsi = StrategyIndicators.calculateRSI(prices, rsiPeriod)

        val signal = when {
            isEnter -> SignalType.BUY
            isExit -> SignalType.SELL
            else -> SignalType.HOLD
        }

        val confidence = if (signal == SignalType.BUY) 0.80 else if (signal == SignalType.SELL) 0.70 else 0.50
        val entry = lastPrice
        val sl = calculateStopLoss(market, entry, prices)
        val tp = calculateTakeProfit(market, entry, prices)
        val size = calculatePositionSize(market, accountBalance, entry, riskPercent)
        val rrr = 2.5

        val explanation = when (signal) {
            SignalType.BUY -> "Golden Cross crossover signal. Short-term EMA is ${String.format("%.2f", emaShort)} which is above Long-term EMA ${String.format("%.2f", emaLong)} with RSI in favorable zone (${String.format("%.1f", rsi)})."
            SignalType.SELL -> "Bearish decay crossover. Short-term EMA is decaying below Long-term EMA."
            SignalType.HOLD -> "Market is consolidative. Indicator readings: EMA cross sideways, RSI is normal at ${String.format("%.1f", rsi)}."
        }

        return TradeProposal(name, market, signal, confidence, entry, sl, tp, size, rrr, explanation)
    }
}

class BreakoutStrategy(
    var period: Int = 20
) : TradingStrategy {
    override val name = "Breakout"
    override val version = "1.1.0"
    override val description = "Captures sharp price movements exceeding local resistance/support boundaries."
    override val supportedMarkets = listOf("BTC", "ETH", "SOL")
    override val riskProfile = "Aggressive"
    override val timeframe = "1H"
    override val requiredIndicators = listOf("Bollinger Bands", "Average True Range (ATR)")

    override val designedRegimes = listOf("Trending", "High volatility")
    override fun getParameters(): Map<String, Double> = mapOf(
        "period" to period.toDouble()
    )
    override fun setParameters(params: Map<String, Double>) {
        params["period"]?.let { period = it.toInt() }
    }

    override fun analyzeMarket(market: String, prices: List<Double>): String {
        if (prices.size < 2) return "Insufficient data"
        val (_, upper, lower) = StrategyIndicators.calculateBollingerBands(prices, period)
        return "BB Upper: ${String.format("%.2f", upper)}, BB Lower: ${String.format("%.2f", lower)}"
    }

    override fun shouldEnter(market: String, prices: List<Double>): Boolean {
        if (prices.size < period) return false
        val lastPrice = prices.last()
        val (_, upper, _) = StrategyIndicators.calculateBollingerBands(prices.dropLast(1), period)
        return lastPrice > upper
    }

    override fun shouldExit(market: String, prices: List<Double>, currentPosition: Double): Boolean {
        if (prices.size < period || currentPosition <= 0) return false
        val lastPrice = prices.last()
        val (mid, _, lower) = StrategyIndicators.calculateBollingerBands(prices, period)
        return lastPrice < mid || lastPrice < lower
    }

    override fun calculateStopLoss(market: String, entryPrice: Double, prices: List<Double>): Double {
        return entryPrice * 0.96 // 4% SL
    }

    override fun calculateTakeProfit(market: String, entryPrice: Double, prices: List<Double>): Double {
        return entryPrice * 1.10 // 10% TP
    }

    override fun calculatePositionSize(market: String, accountBalance: Double, entryPrice: Double, riskPercent: Double): Double {
        val amountToRisk = accountBalance * (riskPercent / 100.0)
        val maxLossPerUnit = entryPrice * 0.04
        return amountToRisk / maxLossPerUnit
    }

    override fun evaluate(market: String, prices: List<Double>, accountBalance: Double, riskPercent: Double): TradeProposal {
        val lastPrice = prices.lastOrNull() ?: 0.0
        val isEnter = shouldEnter(market, prices)
        val isExit = shouldExit(market, prices, 1.0)
        val (_, upper, lower) = StrategyIndicators.calculateBollingerBands(prices, period)

        val signal = when {
            isEnter -> SignalType.BUY
            isExit -> SignalType.SELL
            else -> SignalType.HOLD
        }

        val confidence = if (signal == SignalType.BUY) 0.85 else if (signal == SignalType.SELL) 0.75 else 0.50
        val entry = lastPrice
        val sl = calculateStopLoss(market, entry, prices)
        val tp = calculateTakeProfit(market, entry, prices)
        val size = calculatePositionSize(market, accountBalance, entry, riskPercent)
        val rrr = 2.5

        val explanation = when (signal) {
            SignalType.BUY -> "Volatility breakout. Last price ${String.format("%.2f", lastPrice)} exploded above Bollinger Band Upper channel (${String.format("%.2f", upper)}), signaling major trend initiation."
            SignalType.SELL -> "Breakout failure or mean reversion exit. Price dropped back inside Bollinger Band mid channel."
            SignalType.HOLD -> "Price is trading inside standard Bollinger Band boundaries (${String.format("%.2f", lower)} - ${String.format("%.2f", upper)})."
        }

        return TradeProposal(name, market, signal, confidence, entry, sl, tp, size, rrr, explanation)
    }
}

class MeanReversionStrategy(
    var rsiPeriod: Int = 14,
    var oversoldThreshold: Double = 30.0,
    var overboughtThreshold: Double = 70.0
) : TradingStrategy {
    override val name = "Mean Reversion"
    override val version = "1.0.2"
    override val description = "Exploits temporary overbought/oversold price extensions to return to structural equilibrium."

    override val designedRegimes = listOf("Ranging", "Low volatility")
    override fun getParameters(): Map<String, Double> = mapOf(
        "rsiPeriod" to rsiPeriod.toDouble(),
        "oversoldThreshold" to oversoldThreshold,
        "overboughtThreshold" to overboughtThreshold
    )
    override fun setParameters(params: Map<String, Double>) {
        params["rsiPeriod"]?.let { rsiPeriod = it.toInt() }
        params["oversoldThreshold"]?.let { oversoldThreshold = it }
        params["overboughtThreshold"]?.let { overboughtThreshold = it }
    }
    override val supportedMarkets = listOf("BTC", "ETH", "AAPL")
    override val riskProfile = "Moderate"
    override val timeframe = "30M"
    override val requiredIndicators = listOf("RSI", "Stochastic Oscillator", "Pivot Points")

    override fun analyzeMarket(market: String, prices: List<Double>): String {
        if (prices.size < 2) return "Insufficient data"
        val rsi = StrategyIndicators.calculateRSI(prices, rsiPeriod)
        return "RSI: ${String.format("%.1f", rsi)}"
    }

    override fun shouldEnter(market: String, prices: List<Double>): Boolean {
        val rsi = StrategyIndicators.calculateRSI(prices, rsiPeriod)
        return rsi < oversoldThreshold
    }

    override fun shouldExit(market: String, prices: List<Double>, currentPosition: Double): Boolean {
        if (currentPosition <= 0) return false
        val rsi = StrategyIndicators.calculateRSI(prices, rsiPeriod)
        return rsi > overboughtThreshold
    }

    override fun calculateStopLoss(market: String, entryPrice: Double, prices: List<Double>): Double {
        return entryPrice * 0.97 // 3% SL
    }

    override fun calculateTakeProfit(market: String, entryPrice: Double, prices: List<Double>): Double {
        return entryPrice * 1.06 // 6% TP
    }

    override fun calculatePositionSize(market: String, accountBalance: Double, entryPrice: Double, riskPercent: Double): Double {
        val amountToRisk = accountBalance * (riskPercent / 100.0)
        val maxLossPerUnit = entryPrice * 0.03
        return amountToRisk / maxLossPerUnit
    }

    override fun evaluate(market: String, prices: List<Double>, accountBalance: Double, riskPercent: Double): TradeProposal {
        val lastPrice = prices.lastOrNull() ?: 0.0
        val isEnter = shouldEnter(market, prices)
        val isExit = shouldExit(market, prices, 1.0)
        val rsi = StrategyIndicators.calculateRSI(prices, rsiPeriod)

        val signal = when {
            isEnter -> SignalType.BUY
            isExit -> SignalType.SELL
            else -> SignalType.HOLD
        }

        val confidence = if (signal == SignalType.BUY) 0.78 else if (signal == SignalType.SELL) 0.68 else 0.50
        val entry = lastPrice
        val sl = calculateStopLoss(market, entry, prices)
        val tp = calculateTakeProfit(market, entry, prices)
        val size = calculatePositionSize(market, accountBalance, entry, riskPercent)
        val rrr = 2.0

        val explanation = when (signal) {
            SignalType.BUY -> "Extremely oversold conditions detected. RSI is ${String.format("%.1f", rsi)} which is below oversold threshold $oversoldThreshold. High probability of snapping back up to mean price."
            SignalType.SELL -> "Price fully recovered. RSI reached overbought zone at ${String.format("%.1f", rsi)}."
            SignalType.HOLD -> "Price trading near standard mean values. RSI is stable at ${String.format("%.1f", rsi)}."
        }

        return TradeProposal(name, market, signal, confidence, entry, sl, tp, size, rrr, explanation)
    }
}
