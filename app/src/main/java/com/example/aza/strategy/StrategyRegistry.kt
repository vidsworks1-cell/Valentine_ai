package com.example.aza.strategy

object StrategyRegistry {
    private val strategies = mutableMapOf<String, TradingStrategy>()

    init {
        register(TrendFollowingStrategy())
        register(BreakoutStrategy())
        register(MeanReversionStrategy())
    }

    fun register(strategy: TradingStrategy) {
        strategies[strategy.name] = strategy
    }

    fun getAllStrategies(): List<TradingStrategy> {
        return strategies.values.toList()
    }

    fun getStrategy(name: String): TradingStrategy? {
        return strategies[name]
    }
}
