package com.example.aza.executive

import com.example.data.database.KnowledgeEntry
import com.example.data.repository.AppRepository
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

class DynamicExponentialEstimator(private val repository: AppRepository) {

    // --- Core Parameters ---
    var sensitivityConstant = 1.5
    var criticalLiquidityPercentile = 0.20 // 20% critical threshold
    var decayAlpha = 0.30 // Adaptive exponential smoothing decay factor (alpha)
    
    // --- Hysteresis & Low-Pass Smoothing Parameters ---
    var smoothedVolatility = 0.015
    var smoothedOFI = 0.0
    val volatilityUpperThreshold = 0.032
    val volatilityLowerThreshold = 0.025
    val ofiUpperThreshold = 0.65
    val ofiLowerThreshold = 0.45
    val lowPassVolatilityAlpha = 0.15 // 15% new volatility, 85% historic memory
    val lowPassOfiAlpha = 0.20        // 20% new OFI, 80% historic memory

    // --- Rolling States & Metrics ---
    var currentRegime = "RANGE" // BULL, BEAR, RANGE, VOLATILE
    var currentVolatility = 0.015
    var currentOFI = 0.0
    var currentLiquidity = 0.85
    var crossAssetCorrelationDivergence = 0.12
    
    // --- Robustness Tracker States ---
    private val regimeHistory = mutableListOf<String>()
    private val regimeTransitionTimes = mutableListOf<Long>()
    var transitionThrashingDetected = false
    var weightEntropy = 1.0
    
    // --- Performance Tracking (Simulated or Real-time) ---
    var maxDrawdown = 0.125
    var sharpeRatio = 2.15

    // --- Max Drawdown Protection (MDP) & Dynamic Position Sizing (DPS) ---
    var peakPortfolioValue = 5000.0
    var currentPortfolioValue = 5000.0
    var calculatedDrawdownPercent = 0.0
    var maxAllowableDrawdownPercent = 10.0 // 10% maximum allowable drawdown
    var isCircuitBreakerTriggered = false

    /**
     * Updates the portfolio's net value, updates peak value, calculates drawdown percentage,
     * and triggers a Circuit Breaker if drawdown meets or exceeds the critical threshold.
     */
    fun updatePortfolioValueAndEvaluateDrawdown(currentValue: Double): Double {
        currentPortfolioValue = currentValue
        if (currentValue > peakPortfolioValue) {
            peakPortfolioValue = currentValue
        }
        calculatedDrawdownPercent = if (peakPortfolioValue > 0.0) {
            ((peakPortfolioValue - currentValue) / peakPortfolioValue) * 100.0
        } else {
            0.0
        }
        // Sync with the legacy maxDrawdown metric (as a decimal fraction)
        maxDrawdown = calculatedDrawdownPercent / 100.0
        
        if (calculatedDrawdownPercent >= maxAllowableDrawdownPercent) {
            isCircuitBreakerTriggered = true
            // Enforce Circuit Breaker: disable live order placement
            com.example.aza.connector.BinanceConnector.setOrderPlacementEnabled(false)
        }
        return calculatedDrawdownPercent
    }

    /**
     * Reset the Drawdown circuit breaker manually once risk has been mitigated.
     */
    fun resetCircuitBreaker(newBaseValue: Double) {
        isCircuitBreakerTriggered = false
        peakPortfolioValue = newBaseValue
        currentPortfolioValue = newBaseValue
        calculatedDrawdownPercent = 0.0
        maxDrawdown = 0.0
    }

    /**
     * Calculates the dynamically sized position using Kelly Criterion, Volatility (ATR) scaling, and equity curve drawdown feedback.
     */
    fun calculateDynamicPositionSize(
        symbol: String,
        accountBalance: Double,
        entryPrice: Double,
        prices: List<Double>,
        strategyWinRate: Double = 0.55,
        strategyRiskReward: Double = 2.0
    ): Double {
        // 1. Circuit Breaker Override: If drawdown has breached, size is absolutely 0.0
        if (isCircuitBreakerTriggered) {
            return 0.0
        }

        // 2. Base Risk percentage determination (Starts at 2.0% of account balance)
        var baseRiskPercent = 2.0

        // 3. Equity Curve Feedback: Scale back risk percentage proportionally to current drawdown
        // As drawdown approaches the maximum allowable drawdown (e.g. 10.0%), risk percentage scales down to 0%
        val drawdownMultiplier = (1.0 - (calculatedDrawdownPercent / maxAllowableDrawdownPercent)).coerceIn(0.0, 1.0)
        baseRiskPercent *= drawdownMultiplier

        // 4. Volatility Scaling: Scale down risk if market volatility is elevated (VOLATILE regime)
        val regimeMultiplier = when (currentRegime) {
            "VOLATILE" -> 0.40  // Shrink base risk to 40% of its value
            "BEAR" -> 0.70      // Reduce to 70% in high-risk bear environments
            "BULL" -> 1.00      // Full standard sizing in bullish runs
            else -> 0.85        // Moderate size in quiet ranges
        }
        baseRiskPercent *= regimeMultiplier

        // If transition thrashing is detected, shrink position size by an additional 50% to prevent whipsawing
        if (transitionThrashingDetected) {
            baseRiskPercent *= 0.50
        }

        // Convert final risk percentage to absolute dollar risk
        val dollarRiskAmount = accountBalance * (baseRiskPercent / 100.0)

        // 5. ATR Volatility Stop Distance Sizing
        val atr = com.example.aza.strategy.StrategyIndicators.calculateATR(prices, 14)
        val stopLossDist = if (atr > 0.0) atr * 2.0 else entryPrice * 0.02
        
        // Raw ATR position units sizing
        val rawAtrUnits = dollarRiskAmount / stopLossDist
        val atrSizeInDollars = rawAtrUnits * entryPrice

        // 6. Kelly Criterion Dynamic Sizing Overlay
        // Kelly Formula: f* = (p * R - (1 - p)) / R
        // where p = win rate, R = risk reward ratio
        val kellyFraction = if (strategyRiskReward > 0.0) {
            ((strategyWinRate * strategyRiskReward) - (1.0 - strategyWinRate)) / strategyRiskReward
        } else {
            0.0
        }
        
        // Clamp Kelly fraction to secure ranges [0.0, 0.5] to prevent overallocation, and use a Quarter-Kelly (0.25) factor
        val clampedKelly = kellyFraction.coerceIn(0.0, 0.5)
        val quarterKellyFraction = clampedKelly * 0.25
        val kellySizeInDollars = accountBalance * quarterKellyFraction

        // 7. Harmonious Synthesis: Choose conservative size between Kelly-suggested size and ATR-risk-controlled size
        val finalSuggestedSizeInDollars = if (kellySizeInDollars > 0.0) {
            kotlin.math.min(atrSizeInDollars, kellySizeInDollars)
        } else {
            atrSizeInDollars
        }

        // Clamp final dollar position size between 1% and 15% of total account balance to guarantee capital preservation
        val minSize = accountBalance * 0.01
        val maxSize = accountBalance * 0.15
        
        return finalSuggestedSizeInDollars.coerceIn(minSize, maxSize)
    }

    data class RiskMetrics(
        val regime: String,
        val estimatedVol: Double,
        val suggestedDecay: Double,
        val gatedOFIInfluence: Double,
        val divergence: Double,
        val isStopRunRisk: Boolean
    )

    data class RobustnessCheckReport(
        val weightEntropy: Double,
        val transitionFrequencyHz: Double,
        val thrashingWarning: Boolean,
        val isDiversified: Boolean
    )

    /**
     * Updates internal market microstructure inputs and runs hierarchical gating.
     */
    fun updateMarketMicrostructure(ofi: Double, liquidity: Double) {
        currentOFI = ofi
        currentLiquidity = liquidity
        // Apply low-pass exponential smoothing on OFI to filter out micro-fluctuations
        smoothedOFI = (lowPassOfiAlpha * ofi) + ((1.0 - lowPassOfiAlpha) * smoothedOFI)
    }

    /**
     * Analyzes recent prices to update historical volatility and adaptively select the decay factor (alpha).
     * Integrates a dual-threshold Schmitt Trigger to prevent state-transition whipsawing/thrashing.
     */
    fun analyzePricesAndAdapt(prices: List<Double>): Double {
        if (prices.size < 2) return decayAlpha

        // Calculate simple returns volatility
        val returns = mutableListOf<Double>()
        for (i in 1 until prices.size) {
            val ret = (prices[i] - prices[i - 1]) / prices[i - 1]
            returns.add(ret)
        }
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        currentVolatility = sqrt(variance)

        // Apply low-pass exponential smoothing to raw volatility to prioritize macro trends
        smoothedVolatility = (lowPassVolatilityAlpha * currentVolatility) + ((1.0 - lowPassVolatilityAlpha) * smoothedVolatility)

        // Classify/Partition Market Regime using a Schmitt Trigger (Dual-Threshold Hysteresis)
        val oldRegime = currentRegime
        currentRegime = when (oldRegime) {
            "VOLATILE" -> {
                // Hysteresis exit: only leave VOLATILE if smoothed volatility drops below lower threshold
                if (smoothedVolatility < volatilityLowerThreshold) {
                    evaluateRegimeFromOFI()
                } else {
                    "VOLATILE"
                }
            }
            "BULL", "BEAR" -> {
                if (smoothedVolatility > volatilityUpperThreshold) {
                    "VOLATILE"
                } else {
                    // Hysteresis exit: check if momentum drops below lower threshold to return to RANGE
                    val absOfi = abs(smoothedOFI)
                    if (absOfi < ofiLowerThreshold) {
                        "RANGE"
                    } else {
                        // Maintain existing trend direction or switch if it fully flips
                        if (smoothedOFI > 0) "BULL" else "BEAR"
                    }
                }
            }
            else -> { // RANGE
                if (smoothedVolatility > volatilityUpperThreshold) {
                    "VOLATILE"
                } else {
                    // Hysteresis entry: requires substantial momentum exceeding upper threshold
                    val absOfi = abs(smoothedOFI)
                    if (absOfi > ofiUpperThreshold && smoothedVolatility > 0.008) {
                        if (smoothedOFI > 0) "BULL" else "BEAR"
                    } else {
                        "RANGE"
                    }
                }
            }
        }

        // Dynamically adjust decay factor (alpha) based on classified regime
        decayAlpha = when (currentRegime) {
            "VOLATILE" -> 0.75  // Rapid adaptability to sudden shifts
            "BULL" -> 0.45
            "BEAR" -> 0.55      // Bears fall faster, adapt slightly quicker
            else -> 0.20        // Deep, stable smoothing
        }

        // Keep history of state changes
        recordRegimeTransition(currentRegime)
        
        return decayAlpha
    }

    private fun evaluateRegimeFromOFI(): String {
        return if (abs(smoothedOFI) > ofiUpperThreshold && smoothedVolatility > 0.008) {
            if (smoothedOFI > 0) "BULL" else "BEAR"
        } else {
            "RANGE"
        }
    }

    /**
     * Tracks the frequency of regime state transitions (to detect instability/state-transition thrashing).
     */
    private fun recordRegimeTransition(newRegime: String) {
        val now = System.currentTimeMillis()
        if (regimeHistory.isEmpty() || regimeHistory.last() != newRegime) {
            regimeHistory.add(newRegime)
            regimeTransitionTimes.add(now)
            if (regimeHistory.size > 20) {
                regimeHistory.removeAt(0)
                regimeTransitionTimes.removeAt(0)
            }
        }

        // Evaluate transition frequency in the last 1 minute
        val oneMinuteAgo = now - 60_000
        val recentTransitionsCount = regimeTransitionTimes.count { it > oneMinuteAgo }
        // Thrashing defined as > 5 state switches within 60 seconds
        transitionThrashingDetected = recentTransitionsCount > 5
    }

    /**
     * Evaluates a hierarchical gating network: Liquidity acts as a gate.
     * - High liquidity (above critical percentile): Order Flow Imbalance (OFI) has clean directional momentum impact.
     * - Weak liquidity (below critical percentile): OFI has a high chance of a stop-run or divergence scenario;
     *   directional signals are suppressed or flagged.
     */
    fun evaluateHierarchicalGating(): Double {
        val isStopRunScenario = currentLiquidity < criticalLiquidityPercentile

        return if (isStopRunScenario) {
            // Under weak liquidity, suppress clean directional momentum to avoid stop-runs
            currentOFI * sensitivityConstant * 0.15
        } else {
            // Clean high-liquidity transmission
            currentOFI * sensitivityConstant
        }
    }

    /**
     * Calculates divergence using rolling historic cross-asset correlations.
     */
    fun calculateCrossAssetDivergence(crossCorrelations: List<Double>): Double {
        if (crossCorrelations.isEmpty()) return crossAssetCorrelationDivergence
        
        // Simple standard deviation of correlation values as a proxy for cross-asset divergence
        val mean = crossCorrelations.average()
        val variance = crossCorrelations.map { (it - mean) * (it - mean) }.average()
        crossAssetCorrelationDivergence = sqrt(variance)
        return crossAssetCorrelationDivergence
    }

    /**
     * Gathers all estimated risk parameters and issues a consolidated state.
     */
    fun estimateRiskMetrics(): RiskMetrics {
        val gatedInfluence = evaluateHierarchicalGating()
        val isStopRun = currentLiquidity < criticalLiquidityPercentile

        return RiskMetrics(
            regime = currentRegime,
            estimatedVol = currentVolatility,
            suggestedDecay = decayAlpha,
            gatedOFIInfluence = gatedInfluence,
            divergence = crossAssetCorrelationDivergence,
            isStopRunRisk = isStopRun
        )
    }

    /**
     * Monitors agent weight distributions (Entropy) and state transition frequencies
     * to check for system-wide stability.
     */
    fun monitorRobustness(agentWeights: Map<String, Double>): RobustnessCheckReport {
        // Compute Shannon entropy of weights to detect excessive polarization
        val sum = agentWeights.values.sum()
        var entropy = 0.0
        if (sum > 0) {
            for (weight in agentWeights.values) {
                val p = weight / sum
                if (p > 0.0) {
                    entropy -= p * (ln(p) / ln(2.0))
                }
            }
        }
        weightEntropy = entropy

        // Transition frequency in Hz (switches per second) over the tracked window
        var freqHz = 0.0
        if (regimeTransitionTimes.size >= 2) {
            val dtSec = (regimeTransitionTimes.last() - regimeTransitionTimes.first()) / 1000.0
            if (dtSec > 0.0) {
                freqHz = (regimeTransitionTimes.size - 1) / dtSec
            }
        }

        return RobustnessCheckReport(
            weightEntropy = entropy,
            transitionFrequencyHz = freqHz,
            thrashingWarning = transitionThrashingDetected,
            isDiversified = entropy > 1.2 // High entropy suggests diverse participation
        )
    }

    /**
     * Walk-Forward Optimization (WFO) simulation. Fine-tunes parameters
     * based on Precision and Recall feedback curve analysis to optimize trading stops.
     * Automatically triggers a parametric sweep across regime-shift stress scenarios.
     */
    fun runWalkForwardOptimization(precision: Double, recall: Double) {
        val f1Score = if (precision + recall > 0) (2 * precision * recall) / (precision + recall) else 0.0
        
        if (f1Score < 0.65) {
            // Increase sensitivity and raise critical liquidity percentile slightly to filter false positives
            sensitivityConstant = (sensitivityConstant * 1.05).coerceAtMost(3.0)
            criticalLiquidityPercentile = (criticalLiquidityPercentile + 0.02).coerceAtMost(0.40)
        } else if (f1Score > 0.85) {
            // High precision-recall, relax boundaries slightly for more trading opportunities
            sensitivityConstant = (sensitivityConstant * 0.98).coerceAtLeast(0.8)
            criticalLiquidityPercentile = (criticalLiquidityPercentile - 0.01).coerceAtLeast(0.10)
        }

        // Execute regime-aware parametric sweeps to adapt limits based on simulated market stress walks
        runRegimeAwareParametricSweeps()
    }

    /**
     * Runs parametric sensitivity sweeps over simulated regime transition scenarios
     * to dynamically calibrate risk limits (like maxAllowableDrawdownPercent).
     * This simulates the effect of four critical regime shift scenarios:
     * 1. FLASH_CRASH (Liquidity shock, high volatility, negative OFI) - Test MDP protection speed
     * 2. TREND_REVERSAL (Bull to Bear transition) - Test DPS scaling adaptability
     * 3. WHIPSAW_THRASHING (High frequency regime oscillation) - Test whipsaw dampening performance
     * 4. RANGE_DAMPENING (Transition to quiet consolidation) - Test opportunity recovery limits
     */
    fun runRegimeAwareParametricSweeps() {
        val candidateDrawdowns = listOf(5.0, 7.5, 10.0, 12.5, 15.0)
        var bestDrawdownLimit = maxAllowableDrawdownPercent
        var highestScenarioScore = -Double.MAX_VALUE

        for (candidate in candidateDrawdowns) {
            var totalScenarioScore = 0.0
            
            // --- Scenario 1: FLASH_CRASH Simulation ---
            // Sells off severely. High volatility (0.075), zero liquidity, negative OFI (-0.9)
            // A tighter drawdown cap acts as a circuit breaker, limiting losses to the threshold plus slippage
            val rawCrashDecline = -20.0
            val slippagePenalty = 0.5 // real-market latency friction
            val crashLoss = if (rawCrashDecline < -candidate) -candidate - slippagePenalty else rawCrashDecline
            val crashScore = crashLoss * 1.5 // Flash crash weighted heavily at 1.5x
            totalScenarioScore += crashScore
            
            // --- Scenario 2: TREND_REVERSAL (Bull to Bear) Simulation ---
            // Transition from active Bull trend (+10.0%) to Bear downtrend (-12.0%)
            // Sizing scaling factor diminishes position sizes as the portfolio drawdown grows.
            // If candidate drawdown is too tight, we halt prematurely. If too loose, we bleed capital.
            val dpsSizingAdaptationFactor = (1.0 - (6.0 / candidate)).coerceAtLeast(0.0)
            val reversalLoss = -12.0 * (1.0 - dpsSizingAdaptationFactor)
            val reversalScore = if (reversalLoss < -candidate) -candidate else reversalLoss
            totalScenarioScore += reversalScore * 1.0 // Standard weighting
            
            // --- Scenario 3: WHIPSAW_THRASHING (Noise / Regime Oscillation) ---
            // Multiple short-term regime changes trigger false breakout stop-outs.
            // A tight limit of 5.0% halts on normal noise (false positive penalty of -4.0).
            // A wide limit of 15.0% accepts too many whipsaws (friction loss of -6.0).
            // Optimal limit is balanced (7.5% - 10.0%)
            val falseHaltPenalty = if (candidate <= 5.0) -4.0 else 0.0
            val noiseFriction = -5.0 * (1.0 - (7.5 / candidate)).coerceIn(0.0, 1.0)
            val whipsawScore = (falseHaltPenalty + noiseFriction) * 1.2
            totalScenarioScore += whipsawScore
            
            // --- Scenario 4: RANGE_DAMPENING (Transition to Quiet Consolidation) ---
            // Low volatility mean-reverting environment. Captures +8.0% return.
            // If candidate is too tight (e.g. 5.0%), we might be stuck in a halted breaker state (0% recovery).
            // A larger limit offers recovery breathing room.
            val recoveryDowntimeMultiplier = if (candidate <= 5.0) 0.0 else if (candidate <= 7.5) 0.5 else 1.0
            val recoveryGain = 8.0 * recoveryDowntimeMultiplier
            val rangeScore = recoveryGain * 0.8
            totalScenarioScore += rangeScore

            if (totalScenarioScore > highestScenarioScore) {
                highestScenarioScore = totalScenarioScore
                bestDrawdownLimit = candidate
            }
        }

        // Apply low-pass exponential smoothing filter to prevent high-frequency limit adjustments
        maxAllowableDrawdownPercent = (0.85 * maxAllowableDrawdownPercent) + (0.15 * bestDrawdownLimit)
    }

    /**
     * Evaluates core risk and performance KPIs against predefined threshold bounds.
     * Generates a list of high-priority PositionAlertEntity objects when anomalies are detected,
     * such as drawdown approaching maximum limits, performance decay (low Sharpe), regime thrashing,
     * or extreme volatility shocks.
     */
    fun evaluateKpiAlerts(): List<com.example.data.database.PositionAlertEntity> {
        val alerts = mutableListOf<com.example.data.database.PositionAlertEntity>()
        val now = System.currentTimeMillis()

        // 1. Drawdown Proximity & Breaker Alerts
        if (isCircuitBreakerTriggered) {
            alerts.add(
                com.example.data.database.PositionAlertEntity(
                    timestamp = now,
                    type = "LARGE_LOSS",
                    symbol = "SYSTEM_RISK",
                    message = String.format(
                        Locale.US,
                        "🛑 CRITICAL: Max Drawdown Circuit Breaker Tripped! Current: %.2f%% >= Limit: %.1f%%. Live trading halted.",
                        calculatedDrawdownPercent,
                        maxAllowableDrawdownPercent
                    )
                )
            )
        } else if (calculatedDrawdownPercent >= maxAllowableDrawdownPercent * 0.75) {
            alerts.add(
                com.example.data.database.PositionAlertEntity(
                    timestamp = now,
                    type = "LARGE_LOSS",
                    symbol = "SYSTEM_RISK",
                    message = String.format(
                        Locale.US,
                        "⚠️ WARNING: Portfolio Drawdown approaching critical threshold. Current: %.2f%% (Limit: %.1f%%).",
                        calculatedDrawdownPercent,
                        maxAllowableDrawdownPercent
                    )
                )
            )
        }

        // 2. Sharpe Ratio Performance Alerts
        if (sharpeRatio < 1.0 && sharpeRatio > 0.0) {
            alerts.add(
                com.example.data.database.PositionAlertEntity(
                    timestamp = now,
                    type = "SYNC_FAILURE",
                    symbol = "SYSTEM_RISK",
                    message = String.format(
                        Locale.US,
                        "⚠️ PERFORMANCE DECAY: Sharpe Ratio has degraded to %.2f (Threshold: 1.0). Risk-adjusted return is suboptimal.",
                        sharpeRatio
                    )
                )
            )
        } else if (sharpeRatio <= 0.0) {
            alerts.add(
                com.example.data.database.PositionAlertEntity(
                    timestamp = now,
                    type = "SYNC_FAILURE",
                    symbol = "SYSTEM_RISK",
                    message = String.format(
                        Locale.US,
                        "🚨 PERFORMANCE FAILURE: Sharpe Ratio is negative (%.2f). Strategy optimization sweep recommended.",
                        sharpeRatio
                    )
                )
            )
        }

        // 3. Regime Thrashing & Whipsaw Alerts
        if (transitionThrashingDetected) {
            alerts.add(
                com.example.data.database.PositionAlertEntity(
                    timestamp = now,
                    type = "VOLATILITY",
                    symbol = "SYSTEM_RISK",
                    message = "⚠️ REGIME THRASHING: High frequency regime transitions detected. Position sizes reduced by 50% to mitigate whipsaws."
                )
            )
        }

        // 4. Volatility Spikes
        if (currentVolatility > volatilityUpperThreshold * 1.5) {
            alerts.add(
                com.example.data.database.PositionAlertEntity(
                    timestamp = now,
                    type = "VOLATILITY",
                    symbol = "SYSTEM_RISK",
                    message = String.format(
                        Locale.US,
                        "⚡ VOLATILITY SPIKE: Estimated volatility is extremely high (%.4f). Scaling down base allocations.",
                        currentVolatility
                    )
                )
            )
        }

        return alerts
    }

    /**
     * Publishes the estimated risk parameters into the Shared Memory (Knowledge base).
     */
    suspend fun publishMetricsToKnowledgeBase() {
        val metrics = estimateRiskMetrics()
        val title = "Dynamic Risk Parameter Estimate: [${metrics.regime}]"
        
        val summaryText = String.format(
            Locale.US,
            "Active Regime: %s | Volatility: %.4f | Decay Alpha: %.2f | Gated OFI Influence: %.2f | Max Drawdown Limit: %.1f%% | Stop-Run Risk: %b",
            metrics.regime, metrics.estimatedVol, metrics.suggestedDecay, metrics.gatedOFIInfluence, maxAllowableDrawdownPercent, metrics.isStopRunRisk
        )

        repository.insertKnowledge(
            KnowledgeEntry(
                title = title,
                category = "Dynamic Risk Estimator",
                description = summaryText,
                evidence = "Decay: ${String.format(Locale.US, "%.3f", metrics.suggestedDecay)}, Sweep Limit: ${String.format(Locale.US, "%.1f%%", maxAllowableDrawdownPercent)}, Gated Influence: ${String.format(Locale.US, "%.3f", metrics.gatedOFIInfluence)}, Divergence: ${String.format(Locale.US, "%.3f", metrics.divergence)}, Thrashing: $transitionThrashingDetected",
                confidenceScore = 0.95,
                timesConfirmed = 1,
                isConfirmed = true
            )
        )

        // Evaluate core risk and performance KPIs against predefined threshold bounds and trigger alerts
        val alerts = evaluateKpiAlerts()
        for (alert in alerts) {
            val exists = repository.getAllAlertsDirect().any { it.symbol == alert.symbol && it.type == alert.type && it.message == alert.message }
            if (!exists) {
                repository.insertAlert(alert)
            }
        }
    }
}
