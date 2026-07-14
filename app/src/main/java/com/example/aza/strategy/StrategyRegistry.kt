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

    fun applyParameters(strategy: TradingStrategy, bestConfigParams: String) {
        if (bestConfigParams.isEmpty()) return
        val params = mutableMapOf<String, Double>()
        bestConfigParams.split(",").forEach { pair ->
            val parts = pair.split("=")
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim().toDoubleOrNull()
                if (value != null) {
                    params[key] = value
                }
            }
        }
        try {
            strategy.setParameters(params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
