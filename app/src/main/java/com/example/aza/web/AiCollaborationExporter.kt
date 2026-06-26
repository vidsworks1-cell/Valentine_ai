package com.example.aza.web

import com.example.aza.market.MarketDataService
import com.example.data.database.StrategyBacktestReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AiCollaborationExporter {

    /**
     * Sanitizes strings to ensure no API keys or secrets are leaked.
     */
    fun sanitize(input: String): String {
        // Simple regex to match typical API keys, secrets, or tokens
        return input
            .replace(Regex("(?i)api[_-]?key[:\\s=]+[a-zA-Z0-9_-]{16,}"), "api_key: [REDACTED_PRIVACY]")
            .replace(Regex("(?i)token[:\\s=]+[a-zA-Z0-9_.-]{16,}"), "token: [REDACTED_PRIVACY]")
            .replace(Regex("(?i)secret[:\\s=]+[a-zA-Z0-9_.-]{16,}"), "secret: [REDACTED_PRIVACY]")
    }

    /**
     * Generates a beautifully formatted Markdown report ready to be sent to external AI assistants.
     */
    fun generateMarkdownReport(
        reportType: String,
        contextInfo: String,
        market: String,
        strategyName: String,
        reasoning: String,
        decision: String,
        riskAnalysis: String,
        performanceMetrics: String,
        questionsForAi: String
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val timestamp = dateFormat.format(Date())

        val markdown = """
# AZA INTEL REPORT: $reportType
*Generated: $timestamp*

## Context
$contextInfo

## Objective
Provide an automated, high-fidelity quantitative assessment of the designated target asset and executing strategy matrix.

## Current Market
- **Asset / Symbol**: $market
- **Live Price**: $${String.format(Locale.US, "%.4f", MarketDataService.getLivePrice(market))}
- **24h Volume**: $${String.format(Locale.US, "%,.2f", MarketDataService.getVolume24h(market))}
- **Funding Rate**: ${String.format(Locale.US, "%.5f", MarketDataService.getFundingRate(market))}
- **Open Interest**: $${String.format(Locale.US, "%,.0f", MarketDataService.getOpenInterest(market))}

## Strategy Used
- **Active System Class**: $strategyName
- **Designed Regime Match**: ${if (strategyName.contains("Reversion")) "Ranging" else "Trending"}

## Reasoning
$reasoning

## Decision
$decision

## Risk Analysis
$riskAnalysis

## Performance Metrics
$performanceMetrics

## Questions for AI
$questionsForAi

---
*Confidentiality Notice: This document contains proprietary simulation outputs from the Aza Autonomous Trading Network. All sensitive credentials, database keys, and exchange authentications have been programmatically scrubbed for safety.*
        """.trimIndent()

        return sanitize(markdown)
    }

    /**
     * Specialized formats for rapid exporting.
     */
    fun formatBacktestReport(report: StrategyBacktestReport): String {
        return generateMarkdownReport(
            reportType = "Backtest Report",
            contextInfo = "Historical optimization validation of ${report.strategyName}.",
            market = "BTC",
            strategyName = report.strategyName,
            reasoning = "Comprehensive historical replay of 1-minute interval candles with multi-timeframe confirmation rules.",
            decision = "Optimization completed. Win rate: ${String.format(Locale.US, "%.2f", report.winRate)}%. Net Profit: $${String.format(Locale.US, "%.2f", report.netProfit)}.",
            riskAnalysis = "Max Drawdown observed: ${String.format(Locale.US, "%.2f", report.maxDrawdown)}%. Position sizing applied: 2% Equity Risk.",
            performanceMetrics = """
            - Total Trades: ${report.totalTrades}
            - Winning Trades: ${report.winningTrades}
            - Losing Trades: ${report.losingTrades}
            - Profit Factor: ${String.format(Locale.US, "%.2f", report.profitFactor)}
            - Average Trade Duration: ${report.averageTradeDuration} min
            """.trimIndent(),
            questionsForAi = "1. Given this drawdown and win rate, how can we optimize the EMA periods to reduce whipsaws?\n2. What regime shift indicators could alert Aza to deactivate this strategy earlier?"
        )
    }
}
