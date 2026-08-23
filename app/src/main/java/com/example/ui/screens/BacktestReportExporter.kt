package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.example.aza.intelligence.SecondBrainSummaryState
import com.example.aza.strategy.*
import com.example.data.database.StrategyBacktestReport
import java.util.Locale

object BacktestReportExporter {

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "📋 $label copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun formatSingleBacktest(report: StrategyBacktestReport): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" LUMINA BACKTEST REPORT: ${report.strategyName.uppercase(Locale.US)}")
            appendLine("==================================================")
            appendLine("Strategy: ${report.strategyName}")
            appendLine("Total Trades: ${report.totalTrades} (Winning: ${report.winningTrades}, Losing: ${report.losingTrades})")
            appendLine("Win Rate: ${String.format(Locale.US, "%.1f", report.winRate)}%")
            appendLine("Net Profit: $${String.format(Locale.US, "%,.2f", report.netProfit)} USD")
            appendLine("Max Drawdown: ${String.format(Locale.US, "%.2f", report.maxDrawdown)}%")
            appendLine("Profit Factor: ${String.format(Locale.US, "%.2f", report.profitFactor)}")
            appendLine("Average Profit: $${String.format(Locale.US, "%,.2f", report.averageProfit)}")
            appendLine("Average Loss: $${String.format(Locale.US, "%,.2f", report.averageLoss)}")
            appendLine("Average Trade Duration: ${report.averageTradeDuration} min")
            appendLine("Execution Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(report.timestamp))}")
            appendLine("\n--- DETAILED TRADE & EXECUTION LOGS ---")
            appendLine(report.reportDetails)
            appendLine("==================================================")
        }
    }

    fun formatMonteCarlo(mc: MonteCarloResult, strategyName: String = "Lumina Strategy"): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" LUMINA MONTE CARLO STRESS SIMULATION AUDIT")
            appendLine("==================================================")
            appendLine("Target Strategy: $strategyName")
            appendLine("Iterations: ${mc.iterations}")
            appendLine("Mean Simulated Profit: $${String.format(Locale.US, "%,.2f", mc.meanProfit)} USD")
            appendLine("95% VaR Lower Bound (5th %ile): $${String.format(Locale.US, "%,.2f", mc.profit5thPercentile)} USD")
            appendLine("Profit Upper Bound (95th %ile): $${String.format(Locale.US, "%,.2f", mc.profit95thPercentile)} USD")
            appendLine("Mean Max Drawdown: ${String.format(Locale.US, "%.2f", mc.meanMaxDrawdown)}%")
            appendLine("95th %ile Worst Max Drawdown: ${String.format(Locale.US, "%.2f", mc.maxDrawdown95thPercentile)}%")
            appendLine("Account Ruin Risk (<50% Capital): ${String.format(Locale.US, "%.1f", mc.ruinProbabilityPercent)}%")
            appendLine("==================================================")
        }
    }

    fun formatWalkForward(wf: WalkForwardOptimizationReport): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" LUMINA WALK-FORWARD PARAMETER OPTIMIZATION")
            appendLine("==================================================")
            appendLine("Strategy: ${wf.strategyName} | Asset: ${wf.market}")
            appendLine("Rolling Windows Tested: ${wf.totalWindowsTested}")
            appendLine("Aggregate In-Sample Net Profit: $${String.format(Locale.US, "%,.2f", wf.aggregateInSampleProfit)} USD")
            appendLine("Aggregate Out-of-Sample Net Profit: $${String.format(Locale.US, "%,.2f", wf.aggregateOutOfSampleProfit)} USD")
            appendLine("Walk-Forward Efficiency (WFE): ${String.format(Locale.US, "%.1f", wf.aggregateWalkForwardEfficiencyPct)}%")
            appendLine("Parameter Stability Index: ${String.format(Locale.US, "%.1f", wf.parameterStabilityScore)} / 100")
            appendLine("Robustness Rating: ${wf.robustnessRating}")
            appendLine("\n--- EXECUTIVE SUMMARY ---")
            appendLine(wf.executiveSummary)
            appendLine("\n--- ROLLING WINDOW PERFORMANCE ---")
            wf.windowResults.forEach { win ->
                appendLine("[Window ${win.windowIndex}: ${win.windowName}]")
                appendLine("  In-Sample Profit: $${String.format(Locale.US, "%,.2f", win.inSampleNetProfit)} (${String.format(Locale.US, "%.1f", win.inSampleWinRate)}% WR, Sharpe: ${String.format(Locale.US, "%.2f", win.inSampleSharpe)})")
                appendLine("  Out-Of-Sample Profit: $${String.format(Locale.US, "%,.2f", win.outOfSampleNetProfit)} (${String.format(Locale.US, "%.1f", win.outOfSampleWinRate)}% WR, Sharpe: ${String.format(Locale.US, "%.2f", win.outOfSampleSharpe)})")
                appendLine("  WFE: ${String.format(Locale.US, "%.1f", win.walkForwardEfficiencyPct)}%")
                appendLine("  Optimized Parameters: ${win.bestParametersFound}")
            }
            appendLine("==================================================")
        }
    }

    fun formatMultiStrategy(comp: MultiStrategyComparisonReport): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" LUMINA MULTI-STRATEGY COMPARATIVE AUDIT")
            appendLine("==================================================")
            appendLine("Asset Market: ${comp.market} | Regime: ${comp.regime}")
            appendLine("Top Performing Strategy: ${comp.topPerformingStrategy}")
            appendLine("\n--- EXECUTIVE SUMMARY ---")
            appendLine(comp.executiveSummary)
            appendLine("\n--- STRATEGY COMPARISON MATRIX ---")
            comp.metricsComparison.forEach { (name, m) ->
                val prefix = if (name == comp.topPerformingStrategy) "⭐ [TOP] " else "  • "
                appendLine("$prefix$name:")
                appendLine("    Net Profit: $${String.format(Locale.US, "%,.2f", m.netProfitUSD)} USD")
                appendLine("    Win Rate: ${String.format(Locale.US, "%.1f", m.winRate)}% (${m.totalTrades} Trades)")
                appendLine("    Max Drawdown: ${String.format(Locale.US, "%.2f", m.maxDrawdownPercent)}%")
                appendLine("    Profit Factor: ${String.format(Locale.US, "%.2f", m.profitFactor)}")
                appendLine("    Sharpe: ${String.format(Locale.US, "%.2f", m.sharpeRatio)} | Sortino: ${String.format(Locale.US, "%.2f", m.sortinoRatio)} | Calmar: ${String.format(Locale.US, "%.2f", m.calmarRatio)}")
            }
            appendLine("==================================================")
        }
    }

    fun formatResilienceSuite(suite: ComprehensiveScenarioSuiteReport): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" LUMINA SCENARIO-BASED RESILIENCE SUITE AUDIT")
            appendLine("==================================================")
            appendLine("Scenarios Tested: ${suite.totalScenariosTested}")
            appendLine("Passed Resilience Scenarios: ${suite.passedScenariosCount} / ${suite.totalScenariosTested}")
            appendLine("Aggregate Sharpe: ${String.format(Locale.US, "%.2f", suite.aggregateSharpe)}")
            appendLine("Aggregate Sortino: ${String.format(Locale.US, "%.2f", suite.aggregateSortino)}")
            appendLine("Aggregate Calmar: ${String.format(Locale.US, "%.2f", suite.aggregateCalmar)}")
            appendLine("\n--- EXECUTIVE SUMMARY ---")
            appendLine(suite.executiveResilienceSummary)
            appendLine("\n--- SCENARIO BREAKDOWN ---")
            suite.scenarioResults.forEach { res ->
                appendLine("[${res.scenarioName}] (${res.market} - ${res.strategyName})")
                appendLine("  Result: ${res.resilienceStatus} | Resilience Score: ${String.format(Locale.US, "%.0f", res.resilienceScore)}/100")
                appendLine("  Net PnL: ${if (res.netProfitUSD >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", res.netProfitUSD)} (${String.format(Locale.US, "%.2f", res.returnPercent)}%)")
                appendLine("  Max DD: ${String.format(Locale.US, "%.2f", res.maxDrawdownPercent)}% | Win Rate: ${String.format(Locale.US, "%.1f", res.winRatePercent)}% | Trades: ${res.totalTrades}")
                appendLine("  Sharpe: ${String.format(Locale.US, "%.2f", res.sharpeRatio)} | Sortino: ${String.format(Locale.US, "%.2f", res.sortinoRatio)} | Calmar: ${String.format(Locale.US, "%.2f", res.calmarRatio)}")
                appendLine("  Description: ${res.description}")
            }
            appendLine("==================================================")
        }
    }

    fun formatAgentAttribution(agents: List<AgentPerformanceMetrics>, strategyName: String = "Lumina Pipeline", market: String = "Multi-Asset"): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" 6-AGENT CONSENSUS & ATTRIBUTION AUDIT")
            appendLine("==================================================")
            appendLine("Strategy: $strategyName | Market: $market")
            agents.forEach { a ->
                appendLine("Agent: ${a.agentName}")
                appendLine("  Accuracy: ${String.format(Locale.US, "%.1f", a.accuracyPercent)}% (${a.totalSignalsEvaluated} Total Signals)")
                appendLine("  Avg Win Confidence: ${String.format(Locale.US, "%.2f", a.avgConfidenceOnWins)} | Avg Loss Confidence: ${String.format(Locale.US, "%.2f", a.avgConfidenceOnLosses)}")
                appendLine("  Net PnL Contribution: ${if (a.netPnlContribution >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", a.netPnlContribution)} USD")
                appendLine("--------------------------------------------------")
            }
            val totalPnl = agents.sumOf { it.netPnlContribution }
            val avgAcc = if (agents.isNotEmpty()) agents.map { it.accuracyPercent }.average() else 0.0
            appendLine("Consensus Average Accuracy: ${String.format(Locale.US, "%.1f", avgAcc)}%")
            appendLine("Total Collaborative Net PnL: ${if (totalPnl >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", totalPnl)} USD")
            appendLine("==================================================")
        }
    }

    fun formatPearsonCorrelation(
        assetMatrix: CorrelationMatrixResult,
        stratMatrix: CorrelationMatrixResult
    ): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" PEARSON CORRELATION (r) & CO-MOVEMENT AUDIT")
            appendLine("==================================================")
            appendLine("Purpose: Calculates Pearson Correlation Coefficients (r) from -1.0 to +1.0 across assets and strategy returns.")
            appendLine("Low/negative correlation minimizes systemic portfolio drawdowns and prevents correlated liquidation cascades.")
            appendLine("\n--- ASSET RETURN CORRELATION MATRIX ---")
            assetMatrix.labels.forEachIndexed { i, labelA ->
                appendLine("[$labelA]")
                assetMatrix.labels.forEachIndexed { j, labelB ->
                    val r = assetMatrix.matrix.getOrNull(i)?.getOrNull(j) ?: 0.0
                    appendLine("  -> vs $labelB: r = ${String.format(Locale.US, "%+.3f", r)}")
                }
            }
            appendLine("\n--- STRATEGY RETURN CORRELATION MATRIX ---")
            stratMatrix.labels.forEachIndexed { i, labelA ->
                appendLine("[$labelA]")
                stratMatrix.labels.forEachIndexed { j, labelB ->
                    val r = stratMatrix.matrix.getOrNull(i)?.getOrNull(j) ?: 0.0
                    appendLine("  -> vs $labelB: r = ${String.format(Locale.US, "%+.3f", r)}")
                }
            }
            appendLine("==================================================")
        }
    }

    fun formatGeminiAiFusion(fusion: MetaStrategyHybridizationFusion): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" GEMINI AI VIBE STUDIO: META-STRATEGY FUSION AUDIT")
            appendLine("==================================================")
            appendLine("Primary Strategy: ${fusion.primaryStrategy}")
            appendLine("Secondary Strategy: ${fusion.secondaryStrategy}")
            appendLine("Fusion Architecture: ${fusion.fusionType}")
            appendLine("Batch Cross-Correlation Score: ${String.format(Locale.US, "%.3f", fusion.batchCorrelationScore)}")
            appendLine("Projected Combined Sharpe Ratio: ${String.format(Locale.US, "%.2f", fusion.projectedCombinedSharpe)}")
            appendLine("Projected Drawdown Reduction: -${String.format(Locale.US, "%.1f", fusion.projectedDrawdownReductionPct)}%")
            appendLine("\n--- GEMINI AI VIBE ARCHITECTURE & CODE SYNTHESIS ---")
            appendLine(fusion.geminiVibeCodeAnalysis)
            appendLine("==================================================")
        }
    }

    fun formatEthBnbHybrid(liveState: EthBnbHybridLiveState): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" SYNTHESIZED ETH-BNB HYBRID ENGINE AUDIT")
            appendLine("==================================================")
            appendLine("Live Engine Mode: ${if (liveState.isLiveModeActive) "ACTIVE 🟢" else "PAUSED 🟡"}")
            appendLine("Hybrid Sharpe Ratio: ${String.format(Locale.US, "%.2f", liveState.hybridSharpe)}")
            appendLine("ETH Trend Component Sharpe: ${String.format(Locale.US, "%.2f", liveState.ethConstituentSharpe)} (${liveState.ethWeightPct}% Allocation)")
            appendLine("BNB Mean Reversion Sharpe: ${String.format(Locale.US, "%.2f", liveState.bnbConstituentSharpe)} (${liveState.bnbWeightPct}% Allocation)")
            appendLine("Auto-Rebalance Trigger Threshold: ${liveState.autoRebalanceThresholdPct}% (Triggered: ${liveState.autoRebalanceTriggered})")
            appendLine("Last Rebalance Timestamp: ${liveState.lastRebalanceTimestamp}")
            appendLine("\n--- MONITORING STATUS ---")
            appendLine(liveState.monitoringStatus)
            appendLine("==================================================")
        }
    }

    fun formatMultiRegimeSimulation(sim: EthBnbHybridMultiRegimeSimulation): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" MULTI-REGIME STRESS SIMULATION & MACRO LAB (${sim.simulationDays}-DAY)")
            appendLine("==================================================")
            appendLine("Simulation Duration: ${sim.simulationDays} Days")
            appendLine("Hybrid Sharpe: ${String.format(Locale.US, "%.2f", sim.hybridSharpe)}")
            appendLine("ETH Standalone Sharpe: ${String.format(Locale.US, "%.2f", sim.ethStandaloneSharpe)}")
            appendLine("BNB Standalone Sharpe: ${String.format(Locale.US, "%.2f", sim.bnbStandaloneSharpe)}")
            appendLine("Max Peak Drawdown: ${sim.maxDrawdownPct}%")
            appendLine("Win Rate: ${sim.winRatePct}%")
            appendLine("\n--- TESTED MARKET REGIMES ---")
            sim.regimesTested.forEach { reg -> appendLine("  • $reg") }
            appendLine("\n--- INTEGRATED MACROECONOMIC INDICATORS ---")
            appendLine(sim.macroeconomicIndicatorsIntegrated)
            appendLine("\n--- TRADE QUALITY ENGINE REFINEMENTS ---")
            appendLine(sim.tradeQualityEngineRefinements)
            appendLine("\n--- SIMULATION SUMMARY ---")
            appendLine(sim.simulationSummary)
            appendLine("==================================================")
        }
    }

    fun formatTradeOutlierAudit(
        lastReport: StrategyBacktestReport?,
        qualityScores: List<TradeQualityScore>
    ): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" TRADE LOGGING, OUTLIER LEDGER & MAE/MFE AUDIT")
            appendLine("==================================================")
            if (lastReport != null) {
                appendLine("Strategy: ${lastReport.strategyName} | Total Trades: ${lastReport.totalTrades} | Win Rate: ${String.format(Locale.US, "%.1f", lastReport.winRate)}%")
                appendLine("Net Profit: $${String.format(Locale.US, "%,.2f", lastReport.netProfit)} USD | Max DD: ${String.format(Locale.US, "%.2f", lastReport.maxDrawdown)}%")
                appendLine("\n--- EXECUTION DETAILS ---")
                appendLine(lastReport.reportDetails)
            } else {
                appendLine("No active backtest report currently selected.")
            }
            appendLine("\n--- TRADE QUALITY SCORES (MAE / MFE) ---")
            qualityScores.forEach { tq ->
                appendLine("Trade ID: ${tq.tradeId} | ${tq.pair} (${tq.strategyName})")
                appendLine("  Grade: ${tq.qualityGrade} | Score: ${tq.qualityScore}/100 | Slippage: ${tq.executionSlippageBps} bps")
                appendLine("  MAE (Max Adverse Excursion): ${tq.maePercent}% | MFE (Max Favorable Excursion): +${tq.mfePercent}%")
                appendLine("  Efficiency Ratio: ${tq.tradeEfficiencyRatio} | Self-Adaptation Note: ${tq.feedbackForSelfAdaptation}")
                appendLine("--------------------------------------------------")
            }
            appendLine("==================================================")
        }
    }

    fun formatRoomPersistedLogs(reports: List<StrategyBacktestReport>): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" ROOM DATABASE PERSISTED BACKTEST AUDIT LOGS (${reports.size} ENTRIES)")
            appendLine("==================================================")
            reports.forEachIndexed { idx, r ->
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date(r.timestamp))
                appendLine("#${idx + 1} | [${r.strategyName}] ($dateStr)")
                appendLine("    Net Profit: $${String.format(Locale.US, "%,.2f", r.netProfit)} | Win Rate: ${String.format(Locale.US, "%.1f", r.winRate)}% | Trades: ${r.totalTrades} | Max DD: ${String.format(Locale.US, "%.2f", r.maxDrawdown)}%")
                appendLine("    Profit Factor: ${String.format(Locale.US, "%.2f", r.profitFactor)} | Avg Duration: ${r.averageTradeDuration} min")
            }
            appendLine("==================================================")
        }
    }

    fun formatCircuitBreaker(state: CircuitBreakerState, volLockout: MultiDayVolLockoutState): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" 3.0% DAILY LOSS CIRCUIT BREAKER & VOLATILITY LOCKOUT AUDIT")
            appendLine("==================================================")
            appendLine("Circuit Breaker Status: ${if (state.isTriggered) "🛑 TRIGGERED (TRADING HALTED)" else "✅ ACTIVE & PROTECTED"}")
            appendLine("Daily Cumulative Loss: ${String.format(Locale.US, "%.2f", state.dailyLossPercent)}% (Max Limit: ${state.maxDailyLossLimitPercent}%)")
            appendLine("Adaptive Position Scaling Factor: ${String.format(Locale.US, "%.2f", state.scalingFactor)}x")
            appendLine("Cooldown Minutes Remaining: ${state.cooldownMinutesRemaining} min")
            appendLine("Breaker Status Summary: ${state.statusSummary}")
            appendLine("\n--- MULTI-DAY VOLATILITY LOCKOUT STATUS ---")
            appendLine("Lockout Status: ${volLockout.lockoutStatus}")
            appendLine("Consecutive Trigger Days: ${volLockout.consecutiveTriggerDays} Days")
            appendLine("Position Sizing Cap: ${volLockout.positionSizingCap}x")
            appendLine("Delta Neutral Hedge: ${if (volLockout.deltaNeutralHedgeActive) "ACTIVE" else "INACTIVE"}")
            appendLine("Cooldown Hours Remaining: ${volLockout.cooldownHoursRemaining}h")
            appendLine("Protocol Action: ${volLockout.protocolAction}")
            appendLine("==================================================")
        }
    }

    fun formatResourceArbitrage(res: ResourceArbitrageResult): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" DYNAMIC RESOURCE & EFFORT ARBITRAGE AUDIT")
            appendLine("==================================================")
            appendLine("Trading Capital Allocated: ${res.tradingCapitalAllocatedPct.toInt()}%")
            appendLine("Freelance Agent Capital Allocated: ${res.freelanceCapitalAllocatedPct.toInt()}%")
            appendLine("Trading Compute / GPU Split: ${res.tradingComputeAllocatedPct.toInt()}% Trading / ${res.freelanceComputeAllocatedPct.toInt()}% Freelance")
            appendLine("Trading Expected Sharpe: ${res.tradingExpectedSharpe}")
            appendLine("Freelance Agent Hourly Yield: $${res.freelanceHourlyYieldUSD.toInt()} / hr")
            appendLine("\n--- GOVERNING MULTI-METRIC PERFORMANCE MATRIX ---")
            appendLine("Sample Size: ${res.sampleSize} Trades")
            appendLine("Trading Win Rate: ${res.winRate}%")
            appendLine("Net 30d Realized PnL: +$${String.format(Locale.US, "%,.2f", res.netPnl30d)}")
            appendLine("Reward-to-Risk Ratio: ${res.rewardToRiskRatio}")
            appendLine("\n--- ARBITRAGE RATIONALE ---")
            appendLine(res.rationale)
            appendLine("==================================================")
        }
    }

    fun formatBayesianConfidence(state: BayesianAdaptationState): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" BAYESIAN CONFIDENCE ENGINE 3.0 AUDIT")
            appendLine("==================================================")
            appendLine("Smoothed Posterior Confidence: ${String.format(Locale.US, "%.1f", state.smoothedPosteriorConfidenceScorePct)}% (EMA α=${state.posteriorSmoothingAlpha})")
            appendLine("Raw Posterior Confidence: ${String.format(Locale.US, "%.1f", state.rawPosteriorConfidenceScorePct)}%")
            appendLine("False Positive Reduction Rate: -${String.format(Locale.US, "%.1f", state.falsePositiveReductionRatePct)}%")
            appendLine("Historical Win Rate: ${String.format(Locale.US, "%.1f", state.historicalWinRatePct)}%")
            appendLine("Historical Sharpe Ratio: ${state.historicalSharpeRatio}")
            appendLine("Drawdown Resilience: ${state.historicalDrawdownResiliencePct}%")
            appendLine("Market Condition Tag: ${state.marketConditionTag}")
            appendLine("\n--- 10 STATISTICAL FACTOR WEIGHTS ---")
            appendLine("  • Expected Value (EV): ${String.format(Locale.US, "%.2f", state.expectedValueFactor)}")
            appendLine("  • Sample Size Reliability: ${String.format(Locale.US, "%.2f", state.sampleSizeReliabilityFactor)}")
            appendLine("  • Regime Stability: ${String.format(Locale.US, "%.2f", state.regimeStabilityFactor)}")
            appendLine("  • Market Noise Filtering: ${String.format(Locale.US, "%.2f", state.marketNoiseFactor)}")
            appendLine("  • Strategy Stability: ${String.format(Locale.US, "%.2f", state.strategyStabilityFactor)}")
            appendLine("  • Recent Performance Decay: ${String.format(Locale.US, "%.2f", state.recentPerformanceDecayFactor)}")
            appendLine("  • Execution Quality: ${String.format(Locale.US, "%.2f", state.executionQualityFactor)}")
            appendLine("\n--- ADAPTATION SUMMARY ---")
            appendLine(state.adaptationSummary)
            appendLine("==================================================")
        }
    }

    fun formatAdaptiveExit(summary: AdaptiveExitSummary): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" ADAPTIVE EXIT EXECUTION ENGINE AUDIT")
            appendLine("==================================================")
            appendLine("Average Exit Quality Score: ${String.format(Locale.US, "%.1f", summary.averageExitQualityScore)}% / 100")
            appendLine("Expectancy Boost: +${String.format(Locale.US, "%.2f", summary.expectancyBoostR)}R")
            appendLine("False Exit Avoidance Rate: ${String.format(Locale.US, "%.1f", summary.falseExitAvoidanceRatePct)}%")
            appendLine("Total Evaluated Exits: ${summary.totalTradesEvaluated}")
            appendLine("Top Exit Action: ${summary.topExitAction}")
            appendLine("Current Volatility Regime: ${summary.currentRegime.displayName}")
            appendLine("System Status: ${summary.systemStatus}")
            appendLine("Stress Mitigations: ${summary.stressMitigationsCount} | Flash Crash Shields: ${summary.flashCrashShieldsTriggered}")
            appendLine("\n--- ADAPTIVE FACTOR WEIGHTS ---")
            appendLine("  • Trend Continuation: ${String.format(Locale.US, "%.2f", summary.weightTrendContinuation)}")
            appendLine("  • Momentum Exhaustion: ${String.format(Locale.US, "%.2f", summary.weightMomentumExhaustion)}")
            appendLine("  • ATR Expansion: ${String.format(Locale.US, "%.2f", summary.weightAtrExpansion)}")
            appendLine("  • Unrealized R: ${String.format(Locale.US, "%.2f", summary.weightUnrealizedR)}")
            appendLine("==================================================")
        }
    }

    fun formatShadowDecision(summary: ShadowEngineSummary, missed: MissedOpportunityShadowAnalysis): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" COUNTERFACTUAL SHADOW DECISION ENGINE AUDIT")
            appendLine("==================================================")
            appendLine("System Mode: ${summary.systemMode}")
            appendLine("Total Rejected Trades Recorded: ${summary.totalRejectedTradesRecorded}")
            appendLine("Counterfactual Win Rate: ${String.format(Locale.US, "%.1f", summary.counterfactualWinRatePct)}%")
            appendLine("Average Expected R-Multiple: ${if (summary.averageExpectedR >= 0) "+" else ""}${String.format(Locale.US, "%.2f", summary.averageExpectedR)}R")
            appendLine("Average MFE: +${String.format(Locale.US, "%.2f", summary.averageMfeR)}R | Average MAE: -${String.format(Locale.US, "%.2f", summary.averageMaeR)}R")
            appendLine("Discrepancy Alert Active: ${if (summary.discrepancyAlertActive) "⚠️ YES (${summary.discrepancyAlertMessage})" else "✅ NO (REJECTIONS JUSTIFIED)"}")
            appendLine("Daily Review: ${summary.dailyReviewStatus} | Weekly Review: ${summary.weeklyReviewStatus}")
            appendLine("\n--- MISSED OPPORTUNITY DEEP DIVE ---")
            appendLine("Shadow Trades Evaluated: ${missed.shadowTradeCount}")
            appendLine("False Negatives Identified: ${missed.falseNegativesCount}")
            appendLine("Missed Potential Yield: ${String.format(Locale.US, "%.2f", missed.missedPotentialYieldPct)}% ($${String.format(Locale.US, "%,.2f", missed.shadowExecutionProfitUSD)} USD)")
            appendLine("Primary Rejection Cause: ${missed.primaryRejectionCause}")
            appendLine("Calibration Recommendation: ${missed.recommendation}")
            appendLine("==================================================")
        }
    }

    fun formatSecondBrain(summary: SecondBrainSummaryState): String {
        return buildString {
            appendLine("==================================================")
            appendLine(" LUMINA SECOND BRAIN & META-LEARNING ENGINE AUDIT")
            appendLine("==================================================")
            appendLine("Total Episodes Recorded: ${summary.totalEpisodesRecorded}")
            appendLine("Short-Term Memory (STM) Episodes: ${summary.stmEpisodeCount}")
            appendLine("Long-Term Memory (LTM) Episodes: ${summary.ltmEpisodeCount}")
            appendLine("Overall Win Rate: ${String.format(Locale.US, "%.1f", summary.overallWinRatePct)}%")
            appendLine("STM Retention: ${String.format(Locale.US, "%.1f", summary.stmEffectiveRetentionPct)}% | LTM Retention: ${String.format(Locale.US, "%.1f", summary.ltmEffectiveRetentionPct)}%")
            appendLine("Memory Consolidation Status: ${summary.memoryConsolidationStatus}")
            appendLine("\n--- OPTIMAL CONTEXT PROFILE ---")
            appendLine("  • Best Volatility: ${summary.optimalProfile.bestVolatilityRange}")
            appendLine("  • Best ATR: ${summary.optimalProfile.bestAtrRange}")
            appendLine("  • Best Confidence: ${summary.optimalProfile.bestConfidenceRange}")
            appendLine("  • Best Hours (UTC): ${summary.optimalProfile.bestHoursUtc}")
            appendLine("  • Best Weekdays: ${summary.optimalProfile.bestWeekdays}")
            appendLine("  • Best Market Structure: ${summary.optimalProfile.bestMarketStructure}")
            appendLine("  • Best Trend Strength (ADX): ${summary.optimalProfile.bestTrendStrengthAdx}")
            appendLine("  • Best Liquidity: ${summary.optimalProfile.bestLiquidityConditions}")
            appendLine("\n--- WINNING ENVIRONMENTS ---")
            summary.optimalProfile.winningEnvironments.forEach { env -> appendLine("  ✓ $env") }
            appendLine("\n--- FAILING ENVIRONMENTS ---")
            summary.optimalProfile.failingEnvironments.forEach { env -> appendLine("  ✗ $env") }
            appendLine("==================================================")
        }
    }
}
