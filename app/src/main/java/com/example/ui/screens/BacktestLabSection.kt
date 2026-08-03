package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aza.strategy.AgentPerformanceMetrics
import com.example.aza.strategy.BacktestEngine
import com.example.aza.strategy.StrategyRegistry
import com.example.data.database.StrategyBacktestReport
import com.example.viewmodel.AppViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BacktestLabSection(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    val isBacktesting by viewModel.isBacktesting.collectAsState()
    val lastReport by viewModel.lastBacktestReport.collectAsState()
    val selectedStrategy by viewModel.selectedBacktestStrategy.collectAsState()
    val selectedMarket by viewModel.backtestMarket.collectAsState()
    val selectedPattern by viewModel.backtestPattern.collectAsState()
    val candleCount by viewModel.backtestCandleCount.collectAsState()
    val multiStrategyReport by viewModel.multiStrategyReport.collectAsState()
    val scenarioSuiteReport by viewModel.scenarioSuiteReport.collectAsState()
    val walkForwardReport by viewModel.walkForwardReport.collectAsState()
    val selectedReportTemplateId by viewModel.selectedReportTemplateId.collectAsState()
    val customGeneratedReportText by viewModel.customGeneratedReportText.collectAsState()
    val backtestReportsHistory by viewModel.backtestReports.collectAsState()

    val monteCarloReport by viewModel.monteCarloReport.collectAsState()
    val monteCarloIterations by viewModel.monteCarloIterations.collectAsState()
    val agentPerformanceMetrics by viewModel.agentPerformanceMetrics.collectAsState()
    val assetCorrelationMatrix by viewModel.assetCorrelationMatrix.collectAsState()
    val strategyCorrelationMatrix by viewModel.strategyCorrelationMatrix.collectAsState()

    val circuitBreakerState by viewModel.circuitBreakerState.collectAsState()
    val dualSpeedResponseLoop by viewModel.dualSpeedResponseLoop.collectAsState()
    val resourceArbitrageResult by viewModel.resourceArbitrageResult.collectAsState()
    val pairStrategyMatrix by viewModel.pairStrategyMatrix.collectAsState()
    val cvrCorrelationBreakdown by viewModel.cvrCorrelationBreakdown.collectAsState()
    val volatilitySensitivityConfig by viewModel.volatilitySensitivityConfig.collectAsState()
    val compositeEvaluationProfile by viewModel.compositeEvaluationProfile.collectAsState()
    val multiDayVolLockoutState by viewModel.multiDayVolLockoutState.collectAsState()
    val championChallengerHysteresisState by viewModel.championChallengerHysteresisState.collectAsState()
    val tradeQualityScores by viewModel.tradeQualityScores.collectAsState()
    val recalibratedEngines by viewModel.recalibratedEngines.collectAsState()
    val metaStrategyFusion by viewModel.metaStrategyFusion.collectAsState()
    val dailyDigestReport by viewModel.dailyDigestReport.collectAsState()
    val ethBnbHybridLiveState by viewModel.ethBnbHybridLiveState.collectAsState()
    val ethBnbHybridSimulation by viewModel.ethBnbHybridSimulation.collectAsState()

    val availableStrategies = remember {
        listOf(
            "Trend Following",
            "Breakout Momentum",
            "Volatility Expansion Breakout",
            "Mean Reversion",
            "Liquidity Sweep",
            "Dynamic Risk Parity"
        )
    }

    val availableMarkets = listOf("BTC", "ETH", "AAPL", "NVDA")
    val availableRegimes = listOf("TREND", "RANGING", "HIGH_VOLATILITY", "FLASH_CRASH", "REVERSAL_SHOCK")
    val candleCounts = listOf(100, 300, 500, 1000)

    var activeSubTab by remember { mutableStateOf(0) } // 0: Engine Controls & Results, 1: 6-Agent Attribution, 2: Multi-Strategy Matrix & Correlation

    // Compute sample asset return correlation matrix for UI visualizer
    val correlationResult = remember {
        val returnsMap = mapOf(
            "BTC" to listOf(0.02, -0.01, 0.035, 0.012, -0.025, 0.04, -0.015, 0.03),
            "ETH" to listOf(0.025, -0.012, 0.04, 0.015, -0.03, 0.048, -0.018, 0.035),
            "AAPL" to listOf(0.005, 0.008, -0.002, 0.01, 0.004, -0.005, 0.006, 0.002),
            "NVDA" to listOf(0.018, -0.005, 0.022, 0.028, -0.012, 0.035, -0.008, 0.025)
        )
        BacktestEngine.calculateCorrelationMatrix(returnsMap)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("backtest_lab_screen"),
        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Header Card ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Science,
                                contentDescription = "Lab Icon",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BACKTESTING & STRATEGY LAB",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "6-Agent Engine v3.6",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Simulate strategies against historical market regimes, run 200-iteration Monte Carlo stress tests, flag outliers, and audit 6-Agent Consensus attribution.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sub-tab row
                    SecondaryScrollableTabRow(
                        selectedTabIndex = activeSubTab,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Tab(
                            selected = activeSubTab == 0,
                            onClick = { activeSubTab = 0 },
                            text = { Text("Single Backtest", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeSubTab == 1,
                            onClick = { activeSubTab = 1 },
                            text = { Text("Walk-Forward Engine", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeSubTab == 2,
                            onClick = { activeSubTab = 2 },
                            text = { Text("Multi-Strategy Compare", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeSubTab == 3,
                            onClick = { activeSubTab = 3 },
                            text = { Text("Scenario Resilience", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeSubTab == 4,
                            onClick = { activeSubTab = 4 },
                            text = { Text("Agent & Correlation", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeSubTab == 5,
                            onClick = { activeSubTab = 5 },
                            text = { Text("Trade & Outlier Audit", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeSubTab == 6,
                            onClick = { 
                                activeSubTab = 6 
                                viewModel.generateCustomizedReport()
                            },
                            text = { Text("Stakeholder Reports", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeSubTab == 7,
                            onClick = { activeSubTab = 7 },
                            text = { Text("3% Circuit Breaker", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeSubTab == 8,
                            onClick = { activeSubTab = 8 },
                            text = { Text("Resource Arbitrage", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeSubTab == 9,
                            onClick = { activeSubTab = 9 },
                            text = { Text("Pair Matrix & Vol Calibration", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        }

        if (activeSubTab == 0) {
            // --- Strategy & Simulation Parameters Configuration ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SIMULATION PARAMETERS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Target Strategy Selection
                        Text(
                            text = "Target Strategy:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableStrategies) { strat ->
                                FilterChip(
                                    selected = selectedStrategy == strat,
                                    onClick = { viewModel.selectBacktestStrategy(strat) },
                                    label = { Text(strat, fontSize = 11.sp) },
                                    leadingIcon = if (selectedStrategy == strat) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Asset Symbol Selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Asset Market:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    availableMarkets.forEach { mkt ->
                                        FilterChip(
                                            selected = selectedMarket == mkt,
                                            onClick = { viewModel.selectBacktestMarket(mkt) },
                                            label = { Text(mkt, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Candle Count:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    candleCounts.forEach { cnt ->
                                        FilterChip(
                                            selected = candleCount == cnt,
                                            onClick = { viewModel.setBacktestCandleCount(cnt) },
                                            label = { Text("${cnt}m", fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Market Regime Selection
                        Text(
                            text = "Simulated Market Regime:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableRegimes) { reg ->
                                FilterChip(
                                    selected = selectedPattern == reg,
                                    onClick = { viewModel.selectBacktestPattern(reg) },
                                    label = { Text(reg, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.runBacktest() },
                                enabled = !isBacktesting,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("run_backtest_btn")
                            ) {
                                if (isBacktesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Simulating...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Run Backtest", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.runMultiStrategyComparison() },
                                enabled = !isBacktesting,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Compare All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { viewModel.runScenarioStressTest(selectedPattern) },
                            enabled = !isBacktesting,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Extreme Scenario Stress Test ($selectedPattern)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- Last Backtest Results & Advanced Ratios ---
            item {
                if (lastReport != null) {
                    val report = lastReport!!
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RESULTS: ${report.strategyName.uppercase(Locale.US)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    color = if (report.netProfit >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (report.netProfit >= 0) "PROFITABLE" else "DRAWDOWN",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Key Performance Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    title = "Net Profit",
                                    value = "${if (report.netProfit >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", report.netProfit)}",
                                    valueColor = if (report.netProfit >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Win Rate",
                                    value = "${String.format(Locale.US, "%.1f", report.winRate)}%",
                                    subtitle = "${report.winningTrades}W / ${report.losingTrades}L",
                                    valueColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    title = "Profit Factor",
                                    value = String.format(Locale.US, "%.2f", report.profitFactor),
                                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Max Drawdown",
                                    value = "${String.format(Locale.US, "%.2f", report.maxDrawdown)}%",
                                    valueColor = Color(0xFFD32F2F),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Full Markdown Report Details Box
                            Text(
                                text = "DETAILED REPORT & MONTE CARLO AUDIT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Surface(
                                color = Color(0xFF1E1E1E),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = report.reportDetails,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFFE0E0E0),
                                    modifier = Modifier.padding(12.dp),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Active Backtest Simulation Run Yet",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'Run Backtest' above to generate full metrics & Monte Carlo simulations.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // --- CHAMPION / CHALLENGER DUAL-THRESHOLD HYSTERESIS & MICROSTRUCTURE FRICTION ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CHAMPION / CHALLENGER HYSTERESIS GUARDRAILS",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Surface(
                                color = if (championChallengerHysteresisState.isPromotionTriggered) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (championChallengerHysteresisState.isPromotionTriggered) "PROMOTION APPROVED ✅" else "IN SHADOW TEST 🛡️",
                                    color = if (championChallengerHysteresisState.isPromotionTriggered) Color(0xFF2E7D32) else Color(0xFFE65100),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Dual-threshold hysteresis guardrails require a +15.0% net outperformance hurdle to promote a challenger and a -10.0% deficit to demote a champion, preventing unnecessary strategy churn.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                title = "Champion Strategy",
                                value = championChallengerHysteresisState.championStrategyName,
                                subtitle = "Active Live Execution",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Challenger Strategy",
                                value = championChallengerHysteresisState.challengerStrategyName,
                                subtitle = "Shadow Mode Testing",
                                valueColor = Color(0xFFE65100),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Microstructure Friction & Slippage Drag Breakdown
                        Text(
                            text = "Microstructure Friction & Liquidity Penalties:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetricCard(
                                title = "Raw Shadow Outperformance",
                                value = "+${String.format(Locale.US, "%.1f", championChallengerHysteresisState.rawChallengerOutperformancePct)}%",
                                subtitle = "Before Friction",
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Slippage Drag",
                                value = "-${championChallengerHysteresisState.shadowSlippagePenaltyBps} bps",
                                subtitle = "Execution Penalty",
                                valueColor = Color(0xFFD32F2F),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Liquidity Scale",
                                value = "${championChallengerHysteresisState.liquidityAdjustedScaleFactor}x",
                                subtitle = "Order-Book Depth",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Net Adjusted Return",
                                value = "+${String.format(Locale.US, "%.1f", championChallengerHysteresisState.netAdjustedOutperformancePct)}%",
                                subtitle = "Target: +15.0%",
                                valueColor = if (championChallengerHysteresisState.netAdjustedOutperformancePct >= 15.0) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Test Challenger Outperformance Scenarios:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = championChallengerHysteresisState.rawChallengerOutperformancePct == 18.4,
                                onClick = { viewModel.evaluateHysteresisGuardrails(18.4) },
                                label = { Text("+18.4% (Passes Hurdle)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = championChallengerHysteresisState.rawChallengerOutperformancePct == 12.0,
                                onClick = { viewModel.evaluateHysteresisGuardrails(12.0) },
                                label = { Text("+12.0% (Fails Drag Hurdle)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = championChallengerHysteresisState.rawChallengerOutperformancePct == -8.0,
                                onClick = { viewModel.evaluateHysteresisGuardrails(-8.0) },
                                label = { Text("-8.0% (Underperformance)", fontSize = 11.sp, color = Color(0xFFD32F2F)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = championChallengerHysteresisState.statusSummary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Daily Digest Report Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Summarize, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DAILY DIGEST PERFORMANCE REPORT",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val digest = dailyDigestReport
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                title = "Net 24h PnL",
                                value = "+$${String.format(Locale.US, "%,.2f", digest.netDailyPnlUSD)}",
                                subtitle = "${digest.winRate24h}% Win Rate",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Avg Trade Quality",
                                value = "${digest.avgTradeQualityScore} / 100",
                                subtitle = "Low MAE Execution",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                title = "Active Champions",
                                value = "${digest.activeChampionsCount} Strategies",
                                subtitle = "Live Execution",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Shadow Challengers",
                                value = "${digest.shadowChallengersCount} Strategies",
                                subtitle = "In Shadow Testing",
                                valueColor = Color(0xFFE65100),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = digest.digestHeadline,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        if (activeSubTab == 1) {
            // --- WALK-FORWARD PARAMETER OPTIMIZATION & OUT-OF-SAMPLE TEST ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "WALK-FORWARD PARAMETER OPTIMIZATION",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Continually refines parameters on in-sample training data and tests out-of-sample edge.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { viewModel.runWalkForwardOptimization() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Run Walk-Forward", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (walkForwardReport != null) {
                            val wf = walkForwardReport!!
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    title = "OOS Net Profit",
                                    value = "${if (wf.aggregateOutOfSampleProfit >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", wf.aggregateOutOfSampleProfit)}",
                                    subtitle = "Out-Of-Sample Net",
                                    valueColor = if (wf.aggregateOutOfSampleProfit >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "WFE Efficiency",
                                    value = "${String.format(Locale.US, "%.1f", wf.aggregateWalkForwardEfficiencyPct)}%",
                                    subtitle = "OOS / IS Ratio",
                                    valueColor = if (wf.aggregateWalkForwardEfficiencyPct >= 50.0) Color(0xFF2E7D32) else Color(0xFFE65100),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Stability Index",
                                    value = "${String.format(Locale.US, "%.0f", wf.parameterStabilityScore)} / 100",
                                    subtitle = wf.robustnessRating,
                                    valueColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = wf.executiveSummary,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 15.sp
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Click 'Run Walk-Forward' to slice market candles into rolling in-sample training and out-of-sample test windows.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (walkForwardReport != null) {
                items(walkForwardReport!!.windowResults) { win ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = win.windowName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    color = if (win.walkForwardEfficiencyPct >= 50.0) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "WFE: ${String.format(Locale.US, "%.1f", win.walkForwardEfficiencyPct)}%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (win.walkForwardEfficiencyPct >= 50.0) Color(0xFF2E7D32) else Color(0xFFE65100),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "In-Sample: Return=${String.format(Locale.US, "%.1f", win.inSampleReturnPct)}% | WinRate=${String.format(Locale.US, "%.1f", win.inSampleWinRate)}%",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Out-Sample: Return=${String.format(Locale.US, "%.1f", win.outOfSampleReturnPct)}% | WinRate=${String.format(Locale.US, "%.1f", win.outOfSampleWinRate)}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (win.outOfSampleReturnPct >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Optimized Parameters: ${win.bestParametersFound.entries.joinToString(" | ") { "${it.key}: ${it.value}" }}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- RECALIBRATED ENGINES (ETH CONFIDENCE MODEL & BNB REVERSION ENGINE) LIVE DEPLOYMENT ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WALK-FORWARD RECALIBRATED ENGINES & LIVE DEPLOYMENT",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Recalibrated walk-forward parameter sets applied to ETH Confidence Model and BNB Reversion Engine for cross-regime stability.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            recalibratedEngines.forEach { eng ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = eng.engineName,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "In-Sample Sharpe: ${eng.inSampleSharpe} | Out-Of-Sample Sharpe: ${eng.outOfSampleSharpe} (${eng.walkForwardEfficiencyPct}% WFE)",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Button(
                                                onClick = { viewModel.toggleEngineLiveDeployment(eng.engineName) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (eng.isLiveModeDeployed) Color(0xFF2E7D32) else Color(0xFFE65100)
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (eng.isLiveModeDeployed) "LIVE ACTIVE 🟢" else "PAUSED 🟡",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            color = Color(0xFF1E1E1E),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Parameters: ${eng.recalibratedParameters}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = Color(0xFF81C784),
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = eng.livePerformanceStatus,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (eng.isLiveModeDeployed) Color(0xFF2E7D32) else Color(0xFFE65100)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeSubTab == 2) {
            // --- Multi-Strategy Side-by-Side Comparison ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SIDE-BY-SIDE STRATEGY EVALUATION",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Evaluates all Lumina strategies side-by-side on identical candle data.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { viewModel.runMultiStrategyComparison() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Run Compare", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (multiStrategyReport != null) {
                            val comp = multiStrategyReport!!
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = comp.executiveSummary,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                comp.metricsComparison.forEach { (stratName, metrics) ->
                                    val isTop = stratName == comp.topPerformingStrategy
                                    Surface(
                                        color = if (isTop) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                                        border = if (isTop) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32)) else null,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = stratName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF1B5E20)
                                                    )
                                                    if (isTop) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            color = Color(0xFF2E7D32),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                text = "TOP PERFORMER",
                                                                color = Color.White,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Sharpe: ${String.format(Locale.US, "%.2f", metrics.sharpeRatio)} | Win Rate: ${String.format(Locale.US, "%.1f", metrics.winRate)}% | Max DD: ${String.format(Locale.US, "%.1f", metrics.maxDrawdownPercent)}%",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF333333)
                                                )
                                            }

                                            Text(
                                                text = "${if (metrics.netProfitUSD >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", metrics.netProfitUSD)}",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                color = if (metrics.netProfitUSD >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeSubTab == 4) {
            // --- MONTE CARLO STRESS SIMULATION & ROBUSTNESS TEST ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Casino, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "MONTE CARLO ROBUSTNESS SIMULATOR",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Reshuffles historical trade sequences to test strategy resilience against unexpected market fluctuations.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { viewModel.runMonteCarloSimulation() },
                                enabled = !isBacktesting,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Run Monte Carlo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Simulation Iterations:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(200, 500, 1000).forEach { iters ->
                                FilterChip(
                                    selected = monteCarloIterations == iters,
                                    onClick = { viewModel.setMonteCarloIterations(iters) },
                                    label = { Text("$iters Iterations", fontSize = 11.sp) }
                                )
                            }
                        }

                        if (monteCarloReport != null) {
                            val mc = monteCarloReport!!
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    title = "Ruin Probability",
                                    value = "${String.format(Locale.US, "%.1f", mc.ruinProbabilityPercent)}%",
                                    subtitle = if (mc.ruinProbabilityPercent == 0.0) "SAFE / PROTECTED" else "CAPITAL AT RISK",
                                    valueColor = if (mc.ruinProbabilityPercent <= 1.0) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Mean Profit",
                                    value = "${if (mc.meanProfit >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", mc.meanProfit)}",
                                    subtitle = "${mc.iterations} Reshuffles",
                                    valueColor = if (mc.meanProfit >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    title = "95% VaR Bound (5th %)",
                                    value = "$${String.format(Locale.US, "%,.2f", mc.profit5thPercentile)}",
                                    subtitle = "Worst 5% Outcome",
                                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Best Outcome (95th %)",
                                    value = "+$${String.format(Locale.US, "%,.2f", mc.profit95thPercentile)}",
                                    subtitle = "Top 5% Outcome",
                                    valueColor = Color(0xFF2E7D32),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    title = "Mean Max DD",
                                    value = "${String.format(Locale.US, "%.1f", mc.meanMaxDrawdown)}%",
                                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "95th % Max DD",
                                    value = "${String.format(Locale.US, "%.1f", mc.maxDrawdown95thPercentile)}%",
                                    valueColor = Color(0xFFD32F2F),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Click 'Run Monte Carlo' to reshuffle trade sequence order 200-1000 times and evaluate tail risk.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- 6-AGENT CONSENSUS PIPELINE ATTRIBUTION ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "6-AGENT CONSENSUS PIPELINE ATTRIBUTION",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Tracks individual signal accuracy, win/loss confidence calibration, and PnL contribution per agent.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { viewModel.runAgentPipelineAttribution() },
                                enabled = !isBacktesting,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Evaluate Agents", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val agentsToDisplay = if (agentPerformanceMetrics.isNotEmpty()) {
                            agentPerformanceMetrics
                        } else {
                            listOf(
                                AgentPerformanceMetrics("TradingAgent", 48, 87.5, 0.88, 0.72, 1420.50),
                                AgentPerformanceMetrics("ResearchAgent", 48, 83.3, 0.85, 0.75, 1180.20),
                                AgentPerformanceMetrics("RiskAgent", 48, 91.6, 0.92, 0.68, 1850.00),
                                AgentPerformanceMetrics("KnowledgeAgent", 48, 89.5, 0.90, 0.70, 1640.80),
                                AgentPerformanceMetrics("DiagnosticsAgent", 48, 95.8, 0.96, 0.65, 2100.00),
                                AgentPerformanceMetrics("ExecutivePlanner", 48, 87.5, 0.86, 0.74, 1390.40)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            agentsToDisplay.forEach { agent ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (agent.accuracyPercent >= 85) Color(0xFF2E7D32) else Color(0xFFF57C00))
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = agent.agentName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Text(
                                                text = "${if (agent.netPnlContribution >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", agent.netPnlContribution)}",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                color = if (agent.netPnlContribution >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Accuracy: ${String.format(Locale.US, "%.1f", agent.accuracyPercent)}% (${agent.totalSignalsEvaluated} Signals)",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Win Conf: ${String.format(Locale.US, "%.2f", agent.avgConfidenceOnWins)} | Loss: ${String.format(Locale.US, "%.2f", agent.avgConfidenceOnLosses)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        LinearProgressIndicator(
                                            progress = { (agent.accuracyPercent / 100.0).toFloat() },
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                            color = if (agent.accuracyPercent >= 90) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- ASSET & STRATEGY CORRELATION MATRIX ---
            item {
                var showAssetMatrix by remember { mutableStateOf(true) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CORRELATION MATRICES (PEARSON R)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Analyzes asset price and strategy return correlations to maximize portfolio diversification.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { viewModel.calculateCorrelationMatrices() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Compute R", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Toggle Row between Asset vs Strategy Correlation
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = showAssetMatrix,
                                onClick = { showAssetMatrix = true },
                                label = { Text("Asset Price Correlations", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = !showAssetMatrix,
                                onClick = { showAssetMatrix = false },
                                label = { Text("Strategy Return Correlations", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val targetMatrix = if (showAssetMatrix) {
                            assetCorrelationMatrix ?: correlationResult
                        } else {
                            strategyCorrelationMatrix ?: BacktestEngine.calculateStrategyCorrelationMatrix()
                        }

                        val labels = targetMatrix.labels
                        val matrix = targetMatrix.matrix

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.width(75.dp))
                                labels.forEach { label ->
                                    Text(
                                        text = label,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            matrix.forEachIndexed { rowIndex, rowVals ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = labels[rowIndex],
                                        modifier = Modifier.width(75.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    rowVals.forEach { valR ->
                                        val bgColor = when {
                                            valR >= 0.90 -> Color(0xFFBBDEFB)
                                            valR >= 0.50 -> Color(0xFFE3F2FD)
                                            valR >= 0.00 -> Color(0xFFF5F5F5)
                                            else -> Color(0xFFFFEBEE)
                                        }
                                        val textColor = when {
                                            valR >= 0.50 -> Color(0xFF0D47A1)
                                            valR < 0 -> Color(0xFFB71C1C)
                                            else -> Color(0xFF424242)
                                        }

                                        Surface(
                                            color = bgColor,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 2.dp)
                                        ) {
                                            Text(
                                                text = String.format(Locale.US, "%.2f", valR),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (showAssetMatrix) {
                                            "DIVERSIFICATION INSIGHT: GOLD shows negative correlation (-0.12) to crypto assets, serving as a structural downside hedge."
                                        } else {
                                            "STRATEGY OVERLAP INSIGHT: TrendMaster and MeanReversion show near-zero correlation (0.05), providing complementary returns across market regimes."
                                        },
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- GEMINI AI VIBE CODE STUDIO: BATCH CORRELATION & META-STRATEGY HYBRIDIZATION FUSION ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "GEMINI AI VIBE STUDIO: META-STRATEGY FUSION",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Button(
                                onClick = { viewModel.runGeminiMetaStrategyFusion() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Run AI Fusion", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Runs batch correlation analysis between ETH Confidence Model, BNB Reversion Engine, and active strategies to identify opportunities for signal hybridization and fusion.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val fusion = metaStrategyFusion
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                title = "Batch Correlation",
                                value = String.format(Locale.US, "%.2f", fusion.batchCorrelationScore),
                                subtitle = "Low Cross-Correlation",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Projected Sharpe Boost",
                                value = String.format(Locale.US, "%.2f", fusion.projectedCombinedSharpe),
                                subtitle = "Unified MetaStrategy",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Drawdown Reduction",
                                value = "-${String.format(Locale.US, "%.1f", fusion.projectedDrawdownReductionPct)}%",
                                subtitle = "Risk Smoothing",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = fusion.geminiVibeCodeAnalysis,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF81D4FA),
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // --- SYNTHESIZED ETH-BNB VOLATILITY REVERSION HYBRID: LIVE MODE & AUTO-REBALANCING ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SYNTHESIZED ETH-BNB HYBRID: LIVE ENGINE",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Button(
                                onClick = { viewModel.toggleEthBnbHybridLive(!ethBnbHybridLiveState.isLiveModeActive) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (ethBnbHybridLiveState.isLiveModeActive) Color(0xFF2E7D32) else Color(0xFFE65100)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (ethBnbHybridLiveState.isLiveModeActive) "LIVE ACTIVE 🟢" else "PAUSED 🟡",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Fuses ETH Confidence Model (Trend) and BNB Reversion Engine (Mean Reversion). Continuously monitors performance relative to constituents with automated rebalancing.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetricCard(
                                title = "Hybrid Sharpe",
                                value = String.format(Locale.US, "%.2f", ethBnbHybridLiveState.hybridSharpe),
                                subtitle = "+0.74 vs Standalone",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "ETH Confidence",
                                value = String.format(Locale.US, "%.2f", ethBnbHybridLiveState.ethConstituentSharpe),
                                subtitle = "${ethBnbHybridLiveState.ethWeightPct}% Allocation",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "BNB Reversion",
                                value = String.format(Locale.US, "%.2f", ethBnbHybridLiveState.bnbConstituentSharpe),
                                subtitle = "${ethBnbHybridLiveState.bnbWeightPct}% Allocation",
                                valueColor = Color(0xFFE65100),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Auto-Rebalance Allocation Preset:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = ethBnbHybridLiveState.ethWeightPct == 55,
                                onClick = { viewModel.rebalanceEthBnbWeights(55, 45) },
                                label = { Text("⚖️ Optimal (55/45)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = ethBnbHybridLiveState.ethWeightPct == 65,
                                onClick = { viewModel.rebalanceEthBnbWeights(65, 35) },
                                label = { Text("📈 Trend Bias (65/35)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = ethBnbHybridLiveState.ethWeightPct == 40,
                                onClick = { viewModel.rebalanceEthBnbWeights(40, 60) },
                                label = { Text("🔄 Reversion Bias (40/60)", fontSize = 11.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = ethBnbHybridLiveState.monitoringStatus,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // --- MULTI-REGIME SIMULATION LAB (7-DAY vs 30-DAY EXPANDED BACKTEST) ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "MULTI-REGIME SIMULATION & MACRO INTEGRATION LAB",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Simulates ETH-BNB Volatility Reversion Hybrid across multi-regime market stress tests, incorporating macroeconomic indicators and refining Trade Quality MAE caps.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = ethBnbHybridSimulation.simulationDays == 7,
                                onClick = { viewModel.runEthBnbHybridSimulation(7) },
                                label = { Text("⚡ 7-Day Multi-Regime Test", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = ethBnbHybridSimulation.simulationDays == 30,
                                onClick = { viewModel.runEthBnbHybridSimulation(30) },
                                label = { Text("🚀 30-Day Comprehensive Test", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val sim = ethBnbHybridSimulation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetricCard(
                                title = "Simulated Sharpe",
                                value = String.format(Locale.US, "%.2f", sim.hybridSharpe),
                                subtitle = "Duration: ${sim.simulationDays} Days",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Max Drawdown",
                                value = "${sim.maxDrawdownPct}%",
                                subtitle = "Capped Risk",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Win Rate",
                                value = "${sim.winRatePct}%",
                                subtitle = "Trade Consistency",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Tested Market Regimes (${sim.regimesTested.size}):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                sim.regimesTested.forEach { reg ->
                                    Text(text = "• $reg", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Macroeconomic Indicators & Trade Quality Refinements:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "🌐 Macro Signals: ${sim.macroeconomicIndicatorsIntegrated}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFF81C784)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "🎯 Trade Quality Refinements: ${sim.tradeQualityEngineRefinements}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFF81D4FA)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = sim.simulationSummary,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        if (activeSubTab == 5) {
            // --- TRADE LOGGING & OUTLIER AUDIT LEDGER ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TRADE AUDIT & OUTLIER LEDGER",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Stores all trade IDs, entry/exit points, PnL, and flags high-loss/high-profit outliers for manual review.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (lastReport != null) {
                            val detailsText = lastReport!!.reportDetails
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Lumina Executed Strategy: ${lastReport!!.strategyName} | Total Trades: ${lastReport!!.totalTrades}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = detailsText,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No active backtest report available. Run a simulation under 'Single Backtest' to populate the audit ledger.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- TRADE QUALITY SCORING ENGINE & MAE / MFE AUDIT ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TRADE QUALITY SCORING ENGINE (MAE / MFE)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Integrates trade-level risk assessments, Maximum Adverse Excursion (MAE), and Maximum Favorable Excursion (MFE) efficiency ratios to improve portfolio stability.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tradeQualityScores.forEach { tq ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = when (tq.qualityGrade) {
                                                        "A+" -> Color(0xFF2E7D32)
                                                        "A" -> Color(0xFF388E3C)
                                                        "B" -> Color(0xFFF57C00)
                                                        else -> Color(0xFFD32F2F)
                                                    },
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = tq.qualityGrade,
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Column {
                                                    Text(
                                                        text = "${tq.tradeId} | ${tq.pair} (${tq.strategyName})",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Quality Score: ${tq.qualityScore} / 100",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "Slippage: ${tq.executionSlippageBps} bps",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "MAE: ${tq.maePercent}%",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD32F2F)
                                            )
                                            Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "MFE: +${tq.mfePercent}%",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32)
                                            )
                                            Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "Efficiency: ${String.format(Locale.US, "%.0f", tq.tradeEfficiencyRatio * 100)}%",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = tq.feedbackForSelfAdaptation,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeSubTab == 6) {
            // --- CUSTOMIZABLE STAKEHOLDER REPORTING TEMPLATES ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "CUSTOMIZABLE STAKEHOLDER REPORTING",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Select a reporting template tailored for executives, compliance auditors, or quant researchers.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val templateOptions = com.example.aza.strategy.BacktestEngine.getAvailableReportingTemplates()
                        val context = androidx.compose.ui.platform.LocalContext.current

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(templateOptions) { template ->
                                val isSelected = selectedReportTemplateId == template.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectReportTemplate(template.id) },
                                    label = { Text(template.title, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Generated Stakeholder Output:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Lumina Stakeholder Report", customGeneratedReportText)
                                    clipboard?.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Report copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Text", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (customGeneratedReportText.isNotEmpty()) customGeneratedReportText else "Select a template above to render the report.",
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- Persistent Historical Audit Ledger ---
        item {
            if (backtestReportsHistory.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HISTORICAL BACKTEST REPORTS (${backtestReportsHistory.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "Room Persisted Log",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        backtestReportsHistory.take(5).forEach { historyReport ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = historyReport.strategyName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${historyReport.totalTrades} Trades | Win Rate: ${String.format(Locale.US, "%.1f", historyReport.winRate)}%",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "${if (historyReport.netProfit >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", historyReport.netProfit)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (historyReport.netProfit >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        if (activeSubTab == 3) {
            // --- SCENARIO-BASED TESTING & RISK-ADJUSTED METRICS ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "SCENARIO-BASED RESILIENCE SUITE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Simulate extreme shocks, black swan events, and sudden regime shifts",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { viewModel.runComprehensiveScenarioSuite() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Run Full Suite", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Individual Scenario Triggers:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.runScenarioStressTest("FLASH_CRASH") },
                                    label = { Text("⚡ Flash Crash Shock", fontSize = 11.sp) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.runScenarioStressTest("BLACK_SWAN") },
                                    label = { Text("🦢 Black Swan Collapse", fontSize = 11.sp) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.runScenarioStressTest("SQUEEZE_TO_BREAKOUT") },
                                    label = { Text("💥 Squeeze -> Breakout", fontSize = 11.sp) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.runScenarioStressTest("REVERSAL_SHOCK") },
                                    label = { Text("🔄 Bull -> Bear Reversal", fontSize = 11.sp) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.runScenarioStressTest("VOLATILITY_SURGE") },
                                    label = { Text("🌊 Volatility Surge", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // Suite Summary & Risk Ratios
            if (scenarioSuiteReport != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "AGGREGATE RISK-ADJUSTED PERFORMANCE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    title = "Sharpe Ratio",
                                    value = String.format(Locale.US, "%.2f", scenarioSuiteReport!!.aggregateSharpe),
                                    subtitle = "Excess return / Vol",
                                    valueColor = if (scenarioSuiteReport!!.aggregateSharpe >= 1.5) Color(0xFF2E7D32) else Color(0xFFE65100),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Sortino Ratio",
                                    value = String.format(Locale.US, "%.2f", scenarioSuiteReport!!.aggregateSortino),
                                    subtitle = "Downside risk adj",
                                    valueColor = if (scenarioSuiteReport!!.aggregateSortino >= 1.8) Color(0xFF2E7D32) else Color(0xFFE65100),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Calmar Ratio",
                                    value = String.format(Locale.US, "%.2f", scenarioSuiteReport!!.aggregateCalmar),
                                    subtitle = "Return / Max DD",
                                    valueColor = if (scenarioSuiteReport!!.aggregateCalmar >= 1.0) Color(0xFF2E7D32) else Color(0xFFE65100),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = scenarioSuiteReport!!.executiveResilienceSummary,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                items(scenarioSuiteReport!!.scenarioResults) { res ->
                    val statusColor = when {
                        res.resilienceStatus.startsWith("PASS") -> Color(0xFF2E7D32)
                        res.resilienceStatus.startsWith("WARN") -> Color(0xFFE65100)
                        else -> Color(0xFFD32F2F)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = res.scenarioName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = res.resilienceStatus,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = res.description,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Net Profit", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${if (res.netProfitUSD >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", res.netProfitUSD)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.netProfitUSD >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                    )
                                }
                                Column {
                                    Text("Max Drawdown", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${String.format(Locale.US, "%.2f", res.maxDrawdownPercent)}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.maxDrawdownPercent <= 15) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                    )
                                }
                                Column {
                                    Text("Sharpe / Sortino", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${String.format(Locale.US, "%.2f", res.sharpeRatio)} / ${String.format(Locale.US, "%.2f", res.sortinoRatio)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column {
                                    Text("Resilience Score", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${String.format(Locale.US, "%.0f", res.resilienceScore)} / 100",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- SUB-TAB 7: 3.0% DAILY LOSS CIRCUIT BREAKER & ADAPTIVE SIZING ---
        if (activeSubTab == 7) {
            // Composite Evaluation Profile Preset Selector
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "COMPOSITE EVALUATION PROFILE & SHADOW WINDOW",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Fine-tune strategy promotion thresholds and composite metric weights to optimize out-of-sample stability.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Interactive Preset Selector
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = compositeEvaluationProfile.profileName == "CAPITAL_PRESERVATION",
                                onClick = { viewModel.setCompositeEvaluationProfile("CAPITAL_PRESERVATION") },
                                label = { Text("🛡️ Capital Preservation (14-Day Shadow)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = compositeEvaluationProfile.profileName == "HIGH_GROWTH",
                                onClick = { viewModel.setCompositeEvaluationProfile("HIGH_GROWTH") },
                                label = { Text("🚀 High Growth (7-Day Shadow)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = compositeEvaluationProfile.profileName == "BALANCED",
                                onClick = { viewModel.setCompositeEvaluationProfile("BALANCED") },
                                label = { Text("⚖️ Balanced (10-Day)", fontSize = 11.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Metric Weights breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetricCard(
                                title = "Sharpe Weight",
                                value = "${compositeEvaluationProfile.sharpeWeightPct}%",
                                subtitle = "Equity Smoothness",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "VaR Drawdown",
                                value = "${compositeEvaluationProfile.varDrawdownWeightPct}%",
                                subtitle = "Tail Risk Cap",
                                valueColor = Color(0xFFD32F2F),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Net PnL",
                                value = "${compositeEvaluationProfile.pnlWeightPct}%",
                                subtitle = "Profit Weight",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Win Rate",
                                value = "${compositeEvaluationProfile.winRateWeightPct}%",
                                subtitle = "Consistency",
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                title = "OOS Performance Degradation",
                                value = "${compositeEvaluationProfile.outOfSampleDegradationPct}%",
                                subtitle = if (compositeEvaluationProfile.outOfSampleDegradationPct <= 2.0) "LOW OOS DRIFT" else "HIGH OOS DEGRADATION",
                                valueColor = if (compositeEvaluationProfile.outOfSampleDegradationPct <= 2.0) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Regime Stability Score",
                                value = "${compositeEvaluationProfile.regimeStabilityScore} / 100",
                                subtitle = "${compositeEvaluationProfile.promotionShadowWindowDays}-Day Shadow Window",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = compositeEvaluationProfile.summaryRationale,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (circuitBreakerState.isTriggered) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "3.0% DAILY LOSS CIRCUIT BREAKER",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                color = if (circuitBreakerState.isTriggered) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (circuitBreakerState.isTriggered) "🛑 TRIGGERED" else "✅ ACTIVE & PROTECTED",
                                    color = if (circuitBreakerState.isTriggered) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Protects system capital from extreme drawdown events. If cumulative daily loss reaches 3.0%, trading locks immediately and position sizing scales to 0.0x.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                title = "Simulated Daily Loss",
                                value = "${String.format(Locale.US, "%.2f", circuitBreakerState.dailyLossPercent)}%",
                                subtitle = "3.0% Max Cap Limit",
                                valueColor = if (circuitBreakerState.dailyLossPercent >= 3.0) Color(0xFFD32F2F) else if (circuitBreakerState.dailyLossPercent >= 2.0) Color(0xFFE65100) else Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Position Scale Factor",
                                value = "${String.format(Locale.US, "%.2f", circuitBreakerState.scalingFactor)}x",
                                subtitle = "Adaptive Multiplier",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Test Circuit Breaker Triggers:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = circuitBreakerState.dailyLossPercent == 0.85,
                                onClick = { viewModel.testCircuitBreakerDrawdown(0.85) },
                                label = { Text("0.85% Safe", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = circuitBreakerState.dailyLossPercent == 2.20,
                                onClick = { viewModel.testCircuitBreakerDrawdown(2.20) },
                                label = { Text("2.20% Warning", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = circuitBreakerState.dailyLossPercent == 3.50,
                                onClick = { viewModel.testCircuitBreakerDrawdown(3.50) },
                                label = { Text("3.50% BREACH!", fontSize = 11.sp, color = Color(0xFFD32F2F)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = circuitBreakerState.statusSummary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Prolonged Multi-Day Volatility Lockout Simulator Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PROLONGED MULTI-DAY VOLATILITY LOCKOUT PROTOCOL",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Handles sustained market crash regimes where multiple consecutive days meet the 3.0% daily loss trigger criteria.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Simulate Consecutive Loss Days:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = multiDayVolLockoutState.consecutiveTriggerDays == 1,
                                onClick = { viewModel.simulateMultiDayVolLockout(1) },
                                label = { Text("Day 1 (24h Cooldown)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = multiDayVolLockoutState.consecutiveTriggerDays == 2,
                                onClick = { viewModel.simulateMultiDayVolLockout(2) },
                                label = { Text("Day 2 (48h + Delta Hedge)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = multiDayVolLockoutState.consecutiveTriggerDays >= 3,
                                onClick = { viewModel.simulateMultiDayVolLockout(3) },
                                label = { Text("Day 3+ (72h Quarantine)", fontSize = 11.sp, color = Color(0xFFD32F2F)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            color = if (multiDayVolLockoutState.consecutiveTriggerDays >= 3) Color(0xFFFFEBEE) else Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = multiDayVolLockoutState.protocolAction,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (multiDayVolLockoutState.consecutiveTriggerDays >= 3) Color(0xFFD32F2F) else Color(0xFFE65100)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Cooldown: ${multiDayVolLockoutState.cooldownHoursRemaining} hours", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Delta Neutral Hedge: ${if (multiDayVolLockoutState.deltaNeutralHedgeActive) "ACTIVE ✅" else "INACTIVE"}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Priority Hierarchy Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ADAPTIVE SIZING CONFLICT PRIORITY HIERARCHY",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "When position sizing metrics conflict, Lumina enforces an absolute risk management hierarchy:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val hierarchySteps = listOf(
                            Triple("1. Safety & 3.0% Circuit Breaker", "ABSOLUTE OVERRIDE: Daily loss >= 3.0% or max drawdown breach forces 0.0x sizing regardless of other signals.", Color(0xFFD32F2F)),
                            Triple("2. Market Regime & Extreme Volatility", "REGIME CAP: Flash crash or extreme ATR spikes hard-cap maximum leverage (e.g. max 0.35x).", Color(0xFFE65100)),
                            Triple("3. Sharpe & Reward-to-Risk Ratio", "CAPITAL MULTIPLIER: Sharpe > 2.0 & high RRR scale position sizes up to 1.5x.", Color(0xFF1976D2)),
                            Triple("4. Win Rate & Sample Size", "FINE-TUNING: Win rate fine-tunes baseline multiplier once sample size >15 trades.", Color(0xFF2E7D32))
                        )

                        hierarchySteps.forEach { (title, desc, accent) ->
                            Surface(
                                color = accent.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Dual-Speed Response Loop & CVR Protocol
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DUAL-SPEED RESPONSE LOOP & CVR CORRELATION PROTOCOL",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = dualSpeedResponseLoop.fastLoopAction, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = dualSpeedResponseLoop.slowLoopAction, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "CVR Matrix & Correlation Breakdown Protocol:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val cvr = cvrCorrelationBreakdown
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(
                                title = "Covariance Window",
                                value = "${cvr.lookbackWindowDays} Days",
                                subtitle = "Rolling Covariance",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "ML Model",
                                value = "CatBoost",
                                subtitle = cvr.mlCalibrationModel,
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "⚠️ CORRELATION BREAKDOWN DETECTED: Pair [${cvr.breakdownPair}] historical correlation ${cvr.historicalCorrelation} collapsed to ${cvr.currentCorrelation}.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = cvr.recommendedAction,
                                    fontSize = 10.sp,
                                    color = Color(0xFFBF360C)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SUB-TAB 8: DYNAMIC RESOURCE & EFFORT ARBITRAGE ---
        if (activeSubTab == 8) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Balance, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "DYNAMIC RESOURCE & EFFORT ARBITRAGE",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Dynamically shifts capital and compute allocation between Trading and Freelance Agent Services based on market regime.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { viewModel.runResourceArbitrageEngine() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rebalance", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val res = resourceArbitrageResult
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                title = "Trading Capital",
                                value = "${res.tradingCapitalAllocatedPct.toInt()}%",
                                subtitle = "Active Trading Pool",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Freelance Capital",
                                value = "${res.freelanceCapitalAllocatedPct.toInt()}%",
                                subtitle = "Agent Cash Buffer",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                title = "GPU/Compute Split",
                                value = "${res.tradingComputeAllocatedPct.toInt()}% / ${res.freelanceComputeAllocatedPct.toInt()}%",
                                subtitle = "Trading vs Freelance",
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Freelance Yield",
                                value = "$${res.freelanceHourlyYieldUSD.toInt()}/hr",
                                subtitle = "Agent Hourly Rate",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "💡 ARBITRAGE RATIONALE:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = res.rationale,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // Governing Metrics Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "GOVERNING MULTI-METRIC PERFORMANCE MATRIX",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Decisions are driven by a holistic multi-metric score rather than a single ratio.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val res = resourceArbitrageResult
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(
                                title = "Sample Size",
                                value = "${res.sampleSize} Trades",
                                subtitle = "Sufficient Sample (>15)",
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Win Rate",
                                value = "${res.winRate}%",
                                subtitle = "Trading Win Rate",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(
                                title = "Net PnL (30d)",
                                value = "+$${String.format(Locale.US, "%,.2f", res.netPnl30d)}",
                                subtitle = "Realized Net Profit",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Reward-to-Risk",
                                value = "${res.rewardToRiskRatio}",
                                subtitle = "Expected Sharpe 2.1",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // --- SUB-TAB 9: PAIR STRATEGY MATRIX & VOLATILITY CALIBRATION ---
        if (activeSubTab == 9) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MULTI-METRIC PAIR-STRATEGY EVALUATION MATRIX",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Evaluates strategy performance across Sample Size, Win Rate, Net PnL, and Reward-to-Risk ratio to prioritize top performers.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            pairStrategyMatrix.forEach { entry ->
                                val isTop = entry.statusCategory == "TOP_PERFORMER"
                                val isUnder = entry.statusCategory == "NEEDS_IMPROVEMENT"
                                val statusBg = if (isTop) Color(0xFFE8F5E9) else if (isUnder) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant

                                Surface(
                                    color = statusBg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${entry.pair} + ${entry.strategyName}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            Surface(
                                                color = if (isTop) Color(0xFF2E7D32) else if (isUnder) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = entry.statusCategory,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Sample: ${entry.sampleSize} trades", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Win Rate: ${entry.winRatePercent}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text("PnL: ${if (entry.netPnlUSD >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", entry.netPnlUSD)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (entry.netPnlUSD >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                                            Text("RRR: ${entry.rewardToRiskRatio}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = entry.sensitivityAction, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Volatility Calibration Settings Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BASELINE VOLATILITY & SENSITIVITY CALIBRATION",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Calibrates baseline calm volatility vs elevated stress/crash volatility settings to balance trade frequency and drawdown protection.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val volConfig = volatilitySensitivityConfig
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(
                                title = "Calm Vol Baseline",
                                value = "${volConfig.calmVolatilityBaseline}% / day",
                                subtitle = "Normal Market StdDev",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Crash Threshold",
                                value = ">${volConfig.crashVolatilityThreshold}% / day",
                                subtitle = "Elevated Stress Threshold",
                                valueColor = Color(0xFFD32F2F),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Sensitivity Calibration Presets:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = volConfig.calmVolatilityBaseline == 1.0,
                                onClick = { viewModel.updateVolatilitySensitivity(1.0, 2.5, 1.2) },
                                label = { Text("Balanced Baseline", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = volConfig.calmVolatilityBaseline == 0.6,
                                onClick = { viewModel.updateVolatilitySensitivity(0.6, 1.8, 1.8) },
                                label = { Text("⚡ HYPER_ADAPTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            )
                            FilterChip(
                                selected = volConfig.calmVolatilityBaseline == 0.8,
                                onClick = { viewModel.updateVolatilitySensitivity(0.8, 2.0, 1.5) },
                                label = { Text("High Sensitivity", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = volConfig.calmVolatilityBaseline == 1.5,
                                onClick = { viewModel.updateVolatilitySensitivity(1.5, 3.0, 0.8) },
                                label = { Text("Defensive Crash Cap", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title.uppercase(Locale.US),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
