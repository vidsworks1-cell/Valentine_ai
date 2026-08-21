package com.example.aza.strategy

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OptimizationDataExporter {

    fun generateFullOptimizationJson(
        profiles: List<ProfileAlphaStats>,
        activeVersions: Map<String, ProfileParameterVersion>,
        optimizationHistory: List<OptimizationRunRecord>,
        champion: ProfileAlphaStats?,
        challenger: ProfileAlphaStats?,
        horizon: String = "ALL",
        strategyFilter: String = "ALL",
        symbolFilter: String = "ALL"
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val nowStr = dateFormat.format(Date())

        val totalTrades = profiles.sumOf { it.trades }
        val totalWins = profiles.sumOf { it.wins }
        val totalLosses = profiles.sumOf { it.losses }
        val totalPnlR = profiles.sumOf { it.totalPnlR }
        val winRate = if (totalTrades > 0) (totalWins.toDouble() / totalTrades) * 100.0 else 0.0
        val expectancy = if (totalTrades > 0) totalPnlR / totalTrades else 0.0
        val profitFactor = profiles.map { it.profitFactor }.average().let { if (it.isNaN()) 1.0 else it }
        val maxDrawdown = profiles.maxOfOrNull { it.maxDrawdownR } ?: 0.0
        val sharpe = profiles.map { it.sharpeRatio }.average().let { if (it.isNaN()) 0.0 else it }

        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"exportedAt\": \"$nowStr\",\n")
        sb.append("  \"system\": \"Lumina Alpha Optimization Engine\",\n")
        sb.append("  \"filters\": {\n")
        sb.append("    \"horizon\": \"$horizon\",\n")
        sb.append("    \"strategy\": \"$strategyFilter\",\n")
        sb.append("    \"symbol\": \"$symbolFilter\"\n")
        sb.append("  },\n")
        sb.append("  \"aggregateMetrics\": {\n")
        sb.append("    \"totalTrades\": $totalTrades,\n")
        sb.append("    \"totalWins\": $totalWins,\n")
        sb.append("    \"totalLosses\": $totalLosses,\n")
        sb.append("    \"winRatePct\": ${String.format(Locale.US, "%.2f", winRate)},\n")
        sb.append("    \"expectancyR\": ${String.format(Locale.US, "%.4f", expectancy)},\n")
        sb.append("    \"profitFactor\": ${String.format(Locale.US, "%.2f", profitFactor)},\n")
        sb.append("    \"maxDrawdownR\": ${String.format(Locale.US, "%.2f", maxDrawdown)},\n")
        sb.append("    \"averageSharpeRatio\": ${String.format(Locale.US, "%.2f", sharpe)}\n")
        sb.append("  },\n")

        // Strategy Profiles
        sb.append("  \"strategyProfiles\": [\n")
        profiles.forEachIndexed { index, p ->
            val isChamp = champion?.profileKey == p.profileKey
            val isChall = challenger?.profileKey == p.profileKey
            val ver = activeVersions[p.profileKey]
            val params = ver?.parameters ?: AutonomousParameterTuningEngine.getActiveParameters(p.profileKey)

            sb.append("    {\n")
            sb.append("      \"profileKey\": \"${p.profileKey}\",\n")
            sb.append("      \"symbol\": \"${p.symbol}\",\n")
            sb.append("      \"strategyCode\": \"${p.strategyCode}\",\n")
            sb.append("      \"isChampion\": $isChamp,\n")
            sb.append("      \"isChallenger\": $isChall,\n")
            sb.append("      \"alphaScore\": ${String.format(Locale.US, "%.2f", p.calculateAlphaScore())},\n")
            sb.append("      \"performance\": {\n")
            sb.append("        \"trades\": ${p.trades},\n")
            sb.append("        \"wins\": ${p.wins},\n")
            sb.append("        \"losses\": ${p.losses},\n")
            sb.append("        \"winRatePct\": ${String.format(Locale.US, "%.2f", p.winRatePct)},\n")
            sb.append("        \"expectancyR\": ${String.format(Locale.US, "%.4f", p.expectancyR)},\n")
            sb.append("        \"profitFactor\": ${String.format(Locale.US, "%.2f", p.profitFactor)},\n")
            sb.append("        \"averageWinR\": ${String.format(Locale.US, "%.2f", p.averageWinR)},\n")
            sb.append("        \"averageLossR\": ${String.format(Locale.US, "%.2f", p.averageLossR)},\n")
            sb.append("        \"maxDrawdownR\": ${String.format(Locale.US, "%.2f", p.maxDrawdownR)},\n")
            sb.append("        \"sharpeRatio\": ${String.format(Locale.US, "%.2f", p.sharpeRatio)},\n")
            sb.append("        \"totalPnlR\": ${String.format(Locale.US, "%.2f", p.totalPnlR)}\n")
            sb.append("      },\n")
            sb.append("      \"activeParameters\": {\n")
            sb.append("        \"period\": ${params.period},\n")
            sb.append("        \"atrMultiplier\": ${params.atrMultiplier},\n")
            sb.append("        \"breakoutDistance\": ${params.breakoutDistance},\n")
            sb.append("        \"breakoutConfirmationBars\": ${params.breakoutConfirmationBars},\n")
            sb.append("        \"volumeThreshold\": ${params.volumeThreshold},\n")
            sb.append("        \"momentumThreshold\": ${params.momentumThreshold},\n")
            sb.append("        \"squeezeSensitivity\": ${params.squeezeSensitivity},\n")
            sb.append("        \"stopLossMultiplier\": ${params.stopMultiplier},\n")
            sb.append("        \"takeProfitMultiplier\": ${params.takeProfitMultiplier},\n")
            sb.append("        \"trailingStopActivationR\": ${params.trailingStopActivationR},\n")
            sb.append("        \"trailingStopDistanceMult\": ${params.trailingStopDistanceMult}\n")
            sb.append("      },\n")
            sb.append("      \"rollingStats\": {\n")
            sb.append("        \"r50\": {\"trades\": ${p.rolling50.trades}, \"winRatePct\": ${p.rolling50.winRatePct}, \"expectancyR\": ${p.rolling50.expectancyR}, \"profitFactor\": ${p.rolling50.profitFactor}},\n")
            sb.append("        \"r100\": {\"trades\": ${p.rolling100.trades}, \"winRatePct\": ${p.rolling100.winRatePct}, \"expectancyR\": ${p.rolling100.expectancyR}, \"profitFactor\": ${p.rolling100.profitFactor}},\n")
            sb.append("        \"r250\": {\"trades\": ${p.rolling250.trades}, \"winRatePct\": ${p.rolling250.winRatePct}, \"expectancyR\": ${p.rolling250.expectancyR}, \"profitFactor\": ${p.rolling250.profitFactor}}\n")
            sb.append("      }\n")
            sb.append("    }${if (index < profiles.size - 1) "," else ""}\n")
        }
        sb.append("  ],\n")

        // Walk-Forward Optimization Runs
        sb.append("  \"walkForwardOptimizationHistory\": [\n")
        optimizationHistory.take(20).forEachIndexed { index, run ->
            sb.append("    {\n")
            sb.append("      \"runId\": \"${run.runId}\",\n")
            sb.append("      \"profileKey\": \"${run.profileKey}\",\n")
            sb.append("      \"symbol\": \"${run.symbol}\",\n")
            sb.append("      \"triggerTradeCount\": ${run.triggerTradeCount},\n")
            sb.append("      \"decision\": \"${run.decision.name}\",\n")
            sb.append("      \"decisionRationale\": \"${escapeJson(run.decisionRationale)}\",\n")
            sb.append("      \"expectancyDelta\": ${String.format(Locale.US, "%.4f", run.expectancyDelta)},\n")
            sb.append("      \"profitFactorDelta\": ${String.format(Locale.US, "%.2f", run.profitFactorDelta)},\n")
            sb.append("      \"maxDrawdownDelta\": ${String.format(Locale.US, "%.2f", run.maxDrawdownDelta)},\n")
            sb.append("      \"appliedVersion\": \"${run.appliedVersionTag ?: "NONE"}\",\n")
            sb.append("      \"inSampleMetrics\": {\"trades\": ${run.candidateInSampleMetrics.trades}, \"winRatePct\": ${run.candidateInSampleMetrics.winRatePct}, \"expectancyR\": ${run.candidateInSampleMetrics.expectancyR}, \"profitFactor\": ${run.candidateInSampleMetrics.profitFactor}},\n")
            sb.append("      \"outOfSampleMetrics\": {\"trades\": ${run.candidateOutOfSampleMetrics.trades}, \"winRatePct\": ${run.candidateOutOfSampleMetrics.winRatePct}, \"expectancyR\": ${run.candidateOutOfSampleMetrics.expectancyR}, \"profitFactor\": ${run.candidateOutOfSampleMetrics.profitFactor}}\n")
            sb.append("    }${if (index < minOf(20, optimizationHistory.size) - 1) "," else ""}\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    fun generateOptimizationCsv(
        profiles: List<ProfileAlphaStats>,
        activeVersions: Map<String, ProfileParameterVersion>
    ): String {
        val sb = StringBuilder()
        sb.append("ProfileKey,Symbol,Strategy,Trades,WinRatePct,ExpectancyR,ProfitFactor,AvgWinR,AvgLossR,MaxDrawdownR,SharpeRatio,TotalPnlR,AlphaScore,ATR_Mult,BreakoutDist,ConfBars,VolThresh,MomThresh,SqzSens,StopLossMult,TakeProfitMult,TrailActR,TrailDistMult\n")
        profiles.forEach { p ->
            val ver = activeVersions[p.profileKey]
            val params = ver?.parameters ?: AutonomousParameterTuningEngine.getActiveParameters(p.profileKey)
            sb.append("${p.profileKey},")
            sb.append("${p.symbol},")
            sb.append("${p.strategyCode},")
            sb.append("${p.trades},")
            sb.append("${String.format(Locale.US, "%.2f", p.winRatePct)},")
            sb.append("${String.format(Locale.US, "%.4f", p.expectancyR)},")
            sb.append("${String.format(Locale.US, "%.2f", p.profitFactor)},")
            sb.append("${String.format(Locale.US, "%.2f", p.averageWinR)},")
            sb.append("${String.format(Locale.US, "%.2f", p.averageLossR)},")
            sb.append("${String.format(Locale.US, "%.2f", p.maxDrawdownR)},")
            sb.append("${String.format(Locale.US, "%.2f", p.sharpeRatio)},")
            sb.append("${String.format(Locale.US, "%.2f", p.totalPnlR)},")
            sb.append("${String.format(Locale.US, "%.2f", p.calculateAlphaScore())},")
            sb.append("${params.atrMultiplier},")
            sb.append("${params.breakoutDistance},")
            sb.append("${params.breakoutConfirmationBars},")
            sb.append("${params.volumeThreshold},")
            sb.append("${params.momentumThreshold},")
            sb.append("${params.squeezeSensitivity},")
            sb.append("${params.stopMultiplier},")
            sb.append("${params.takeProfitMultiplier},")
            sb.append("${params.trailingStopActivationR},")
            sb.append("${params.trailingStopDistanceMult}\n")
        }
        return sb.toString()
    }

    fun generateLlmOptimizerPrompt(
        profiles: List<ProfileAlphaStats>,
        activeVersions: Map<String, ProfileParameterVersion>,
        champion: ProfileAlphaStats?,
        challenger: ProfileAlphaStats?
    ): String {
        val sb = StringBuilder()
        sb.append("# LUMINA QUANT STRATEGY OPTIMIZATION PROMPT\n\n")
        sb.append("You are an expert quantitative trading researcher optimizing Lumina's algorithmic trading system.\n\n")
        sb.append("## CURRENT PERFORMANCE CONTEXT\n")
        if (champion != null) {
            val cParams = AutonomousParameterTuningEngine.getActiveParameters(champion.profileKey)
            sb.append("### Champion Profile (${champion.profileKey}):\n")
            sb.append("- Symbol: ${champion.symbol} (${champion.strategyCode})\n")
            sb.append("- Trades: ${champion.trades} | Win Rate: ${String.format(Locale.US, "%.2f", champion.winRatePct)}% | Expectancy: ${String.format(Locale.US, "%.4f", champion.expectancyR)}R | Profit Factor: ${String.format(Locale.US, "%.2f", champion.profitFactor)}\n")
            sb.append("- Max Drawdown: ${String.format(Locale.US, "%.2f", champion.maxDrawdownR)}R | Sharpe: ${String.format(Locale.US, "%.2f", champion.sharpeRatio)}\n")
            sb.append("- Parameters: ${cParams.formattedSummary()}\n\n")
        }
        if (challenger != null) {
            val chParams = AutonomousParameterTuningEngine.getActiveParameters(challenger.profileKey)
            sb.append("### Challenger Profile (${challenger.profileKey}):\n")
            sb.append("- Symbol: ${challenger.symbol} (${challenger.strategyCode})\n")
            sb.append("- Trades: ${challenger.trades} | Win Rate: ${String.format(Locale.US, "%.2f", challenger.winRatePct)}% | Expectancy: ${String.format(Locale.US, "%.4f", challenger.expectancyR)}R | Profit Factor: ${String.format(Locale.US, "%.2f", challenger.profitFactor)}\n")
            sb.append("- Parameters: ${chParams.formattedSummary()}\n\n")
        }

        sb.append("## ALL STRATEGY PROFILES\n")
        profiles.forEach { p ->
            val pParams = AutonomousParameterTuningEngine.getActiveParameters(p.profileKey)
            sb.append("- **${p.profileKey}** (${p.symbol}): WinRate=${String.format(Locale.US, "%.1f", p.winRatePct)}%, E[R]=${String.format(Locale.US, "%.3f", p.expectancyR)}, PF=${String.format(Locale.US, "%.2f", p.profitFactor)}, MaxDD=${String.format(Locale.US, "%.2f", p.maxDrawdownR)}R. Params: ATR=${pParams.atrMultiplier}x, Dist=${pParams.breakoutDistance}, Vol=${pParams.volumeThreshold}x, SL=${pParams.stopMultiplier}x, TP=${pParams.takeProfitMultiplier}x\n")
        }

        sb.append("\n## OPTIMIZATION OBJECTIVE\n")
        sb.append("Based on the data above, recommend refined parameter adjustments to:\n")
        sb.append("1. Maximize Expectancy (E[R]) and Profit Factor while keeping Max Drawdown under 3.0R.\n")
        sb.append("2. Identify underperforming/probationary profiles and propose specific parameter ranges (ATR multiplier, Breakout distance, Volume threshold, SL/TP).\n")
        sb.append("3. Provide calibrated hyperparameters in a valid JSON mapping for direct ingestion.\n")

        return sb.toString()
    }

    fun generateSingleProfileJson(
        profile: ProfileAlphaStats,
        version: ProfileParameterVersion?
    ): String {
        val params = version?.parameters ?: AutonomousParameterTuningEngine.getActiveParameters(profile.profileKey)
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"profileKey\": \"${profile.profileKey}\",\n")
        sb.append("  \"symbol\": \"${profile.symbol}\",\n")
        sb.append("  \"strategyCode\": \"${profile.strategyCode}\",\n")
        sb.append("  \"alphaScore\": ${String.format(Locale.US, "%.2f", profile.calculateAlphaScore())},\n")
        sb.append("  \"performance\": {\n")
        sb.append("    \"trades\": ${profile.trades},\n")
        sb.append("    \"wins\": ${profile.wins},\n")
        sb.append("    \"losses\": ${profile.losses},\n")
        sb.append("    \"winRatePct\": ${String.format(Locale.US, "%.2f", profile.winRatePct)},\n")
        sb.append("    \"expectancyR\": ${String.format(Locale.US, "%.4f", profile.expectancyR)},\n")
        sb.append("    \"profitFactor\": ${String.format(Locale.US, "%.2f", profile.profitFactor)},\n")
        sb.append("    \"maxDrawdownR\": ${String.format(Locale.US, "%.2f", profile.maxDrawdownR)},\n")
        sb.append("    \"sharpeRatio\": ${String.format(Locale.US, "%.2f", profile.sharpeRatio)},\n")
        sb.append("    \"totalPnlR\": ${String.format(Locale.US, "%.2f", profile.totalPnlR)}\n")
        sb.append("  },\n")
        sb.append("  \"parameters\": {\n")
        sb.append("    \"period\": ${params.period},\n")
        sb.append("    \"atrMultiplier\": ${params.atrMultiplier},\n")
        sb.append("    \"breakoutDistance\": ${params.breakoutDistance},\n")
        sb.append("    \"breakoutConfirmationBars\": ${params.breakoutConfirmationBars},\n")
        sb.append("    \"volumeThreshold\": ${params.volumeThreshold},\n")
        sb.append("    \"momentumThreshold\": ${params.momentumThreshold},\n")
        sb.append("    \"squeezeSensitivity\": ${params.squeezeSensitivity},\n")
        sb.append("    \"stopLossMultiplier\": ${params.stopMultiplier},\n")
        sb.append("    \"takeProfitMultiplier\": ${params.takeProfitMultiplier},\n")
        sb.append("    \"trailingStopActivationR\": ${params.trailingStopActivationR},\n")
        sb.append("    \"trailingStopDistanceMult\": ${params.trailingStopDistanceMult}\n")
        sb.append("  }\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun escapeJson(input: String): String {
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
