package com.example.aza.strategy

object StrategyRegistry {
    private val strategies = mutableMapOf<String, TradingStrategy>()

    init {
        register(TrendFollowingStrategy())
        register(BreakoutStrategy())
        register(MeanReversionStrategy())
        register(MeanReversionGridStrategy())
        register(BreakoutGridHybridStrategy())
        register(LiquiditySweepStrategy())
        register(VolatilityExpansionBreakoutStrategy())
    }

    fun register(strategy: TradingStrategy) {
        strategies[strategy.name] = strategy
    }

    fun getAllStrategies(): List<TradingStrategy> {
        return strategies.values.toList()
    }

    fun getStrategy(name: String): TradingStrategy? {
        strategies[name]?.let { return it }
        return when (name.lowercase().trim()) {
            "breakout momentum", "breakout_momentum" -> strategies["Breakout"]
            "trend following", "trend_following" -> strategies["Trend Following"]
            "mean reversion", "mean_reversion" -> strategies["Mean Reversion"]
            "volatility expansion breakout", "veb" -> strategies["Volatility Expansion Breakout"]
            else -> strategies.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }

    fun applyParameters(strategy: TradingStrategy, bestConfigParams: String) {
        if (bestConfigParams.isEmpty()) return
        val params = mutableMapOf<String, Double>()
        // Normalize brackets and pipes if compound serialization is used
        val cleanConfig = bestConfigParams.replace("[", "").replace("]", "")
        val rawTokens = cleanConfig.split(Regex("[,|]"))
        for (token in rawTokens) {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split("=")
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
