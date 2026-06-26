package com.example.aza.strategy

enum class SignalType {
    BUY, SELL, HOLD
}

data class TradeProposal(
    val strategyName: String,
    val market: String,
    val signal: SignalType,
    val confidenceScore: Double,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val positionSize: Double,
    val riskRewardRatio: Double,
    val explanation: String
)

interface TradingStrategy {
    val name: String
    val version: String
    val description: String
    val supportedMarkets: List<String>
    val riskProfile: String
    val timeframe: String
    val requiredIndicators: List<String>
    
    // Phase 5: Autonomous Evaluation & Optimization
    val designedRegimes: List<String> get() = listOf("Trending")
    fun getParameters(): Map<String, Double> = emptyMap()
    fun setParameters(params: Map<String, Double>) {}

    fun analyzeMarket(market: String, prices: List<Double>): String
    fun shouldEnter(market: String, prices: List<Double>): Boolean
    fun shouldExit(market: String, prices: List<Double>, currentPosition: Double): Boolean
    fun calculateStopLoss(market: String, entryPrice: Double, prices: List<Double>): Double
    fun calculateTakeProfit(market: String, entryPrice: Double, prices: List<Double>): Double
    fun calculatePositionSize(market: String, accountBalance: Double, entryPrice: Double, riskPercent: Double): Double

    fun evaluate(
        market: String,
        prices: List<Double>,
        accountBalance: Double,
        riskPercent: Double = 2.0
    ): TradeProposal
}
