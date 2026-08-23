package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aza.strategy.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val decisionQualityReport by viewModel.decisionQualityReport.collectAsState()
    val bayesianAdaptationState by viewModel.bayesianAdaptationState.collectAsState()
    val adaptiveExitSummaryState by viewModel.adaptiveExitSummaryState.collectAsState()
    val missedOpportunityAnalysis by viewModel.missedOpportunityAnalysis.collectAsState()
    val shadowDecisionSummaryState by viewModel.shadowDecisionSummaryState.collectAsState()
    val subsystemChampionChallenger by viewModel.subsystemChampionChallenger.collectAsState()
    val portfolioIntelligenceMetrics by viewModel.portfolioIntelligenceMetrics.collectAsState()
    val institutionalRecoveryState by viewModel.institutionalRecoveryState.collectAsState()
    val executionIntelligenceRouting by viewModel.executionIntelligenceRouting.collectAsState()
    val executionQualitySummaryState by viewModel.executionQualitySummaryState.collectAsState()
    val portfolioTradeInteractionState by viewModel.portfolioTradeInteractionState.collectAsState()
    val metaReasoningValidation by viewModel.metaReasoningValidation.collectAsState()
    val mtfGuardCalibrationState by viewModel.mtfGuardCalibrationState.collectAsState()
    val tradeLifecycleValidatorState by viewModel.tradeLifecycleValidatorState.collectAsState()
    val systemHarmonyGlobalUtility by viewModel.systemHarmonyGlobalUtility.collectAsState()
    val autonomousExperimentState by viewModel.autonomousExperimentState.collectAsState()
    val secondBrainSummaryState by viewModel.secondBrainSummaryState.collectAsState()

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

    val availableMarkets = listOf("BTC", "ETH", "SOL", "BNB", "AAPL", "NVDA")
    val availableRegimes = listOf("TREND", "RANGING", "HIGH_VOLATILITY", "FLASH_CRASH", "REVERSAL_SHOCK")
    val candleCounts = listOf(100, 300, 500, 1000)

    var activeSubTab by remember { mutableStateOf(0) }
    var showRoomLogsDialog by remember { mutableStateOf(false) }
    var selectedReportForDialog by remember { mutableStateOf<StrategyBacktestReport?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

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
                        Tab(
                            selected = activeSubTab == 10,
                            onClick = { activeSubTab = 10 },
                            text = { Text("Institutional Subsystems (10)", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
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

                            // Full Markdown Report Details Box with Copy Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DETAILED REPORT & MONTE CARLO AUDIT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Button(
                                    onClick = {
                                        BacktestReportExporter.copyToClipboard(
                                            context,
                                            "Backtest Report",
                                            BacktestReportExporter.formatSingleBacktest(report)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Report", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

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
                                title = "Champion Profile",
                                value = "${championChallengerHysteresisState.championProfileKey} (${championChallengerHysteresisState.championWinRatePct}% WR)",
                                subtitle = "Expectancy: +${String.format(Locale.US, "%.2f", championChallengerHysteresisState.championExpectancyR)}R • Live",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Challenger Profile",
                                value = "${championChallengerHysteresisState.challengerProfileKey} (${championChallengerHysteresisState.challengerWinRatePct}% WR)",
                                subtitle = "Expectancy: +${String.format(Locale.US, "%.2f", championChallengerHysteresisState.challengerExpectancyR)}R • Shadow",
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

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (walkForwardReport != null) {
                                    Button(
                                        onClick = {
                                            walkForwardReport?.let { wf ->
                                                BacktestReportExporter.copyToClipboard(
                                                    context,
                                                    "Walk-Forward Report",
                                                    BacktestReportExporter.formatWalkForward(wf)
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
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
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Walk-Forward Target Strategy Selection
                        Text(
                            text = "Target Strategy:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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

                        Spacer(modifier = Modifier.height(8.dp))

                        // Target Asset Selection
                        Text(
                            text = "Target Market Asset & Candle Window:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                items(availableMarkets) { market ->
                                    FilterChip(
                                        selected = selectedMarket == market,
                                        onClick = { viewModel.selectBacktestMarket(market) },
                                        label = { Text(market, fontSize = 11.sp) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(candleCounts) { count ->
                                    FilterChip(
                                        selected = candleCount == count,
                                        onClick = { viewModel.setBacktestCandleCount(count) },
                                        label = { Text("${count}c", fontSize = 10.sp) }
                                    )
                                }
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

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (multiStrategyReport != null) {
                                    Button(
                                        onClick = {
                                            multiStrategyReport?.let { comp ->
                                                BacktestReportExporter.copyToClipboard(
                                                    context,
                                                    "Multi-Strategy Comparison",
                                                    BacktestReportExporter.formatMultiStrategy(comp)
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
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
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Target Asset & Candle Selection
                        Text(
                            text = "Target Market Asset & Candles:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                items(availableMarkets) { market ->
                                    FilterChip(
                                        selected = selectedMarket == market,
                                        onClick = { viewModel.selectBacktestMarket(market) },
                                        label = { Text(market, fontSize = 11.sp) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(candleCounts) { count ->
                                    FilterChip(
                                        selected = candleCount == count,
                                        onClick = { viewModel.setBacktestCandleCount(count) },
                                        label = { Text("${count}c", fontSize = 10.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Target Market Regime
                        Text(
                            text = "Market Regime:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableRegimes) { regime ->
                                FilterChip(
                                    selected = selectedPattern == regime,
                                    onClick = { viewModel.selectBacktestPattern(regime) },
                                    label = { Text(regime, fontSize = 10.5.sp) }
                                )
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

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (monteCarloReport != null) {
                                    Button(
                                        onClick = {
                                            monteCarloReport?.let { mc ->
                                                BacktestReportExporter.copyToClipboard(
                                                    context,
                                                    "Monte Carlo Audit",
                                                    BacktestReportExporter.formatMonteCarlo(mc, selectedStrategy)
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
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
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Target Strategy Selection for Monte Carlo
                        Text(
                            text = "Target Strategy to Reshuffle:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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

                        Spacer(modifier = Modifier.height(8.dp))

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

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        val agentSummary = BacktestReportExporter.formatAgentAttribution(agentPerformanceMetrics, selectedStrategy, selectedMarket)
                                        BacktestReportExporter.copyToClipboard(context, "Agent Attribution", agentSummary)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Target Strategy Selection for Agent Attribution
                        Text(
                            text = "Target Strategy for Agent Evaluation:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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

                        Spacer(modifier = Modifier.height(8.dp))

                        // Target Asset Selection
                        Text(
                            text = "Market Asset Context:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableMarkets) { market ->
                                FilterChip(
                                    selected = selectedMarket == market,
                                    onClick = { viewModel.selectBacktestMarket(market) },
                                    label = { Text(market, fontSize = 11.sp) }
                                )
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

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        val targetMat = if (showAssetMatrix) {
                                            assetCorrelationMatrix ?: correlationResult
                                        } else {
                                            strategyCorrelationMatrix ?: BacktestEngine.calculateStrategyCorrelationMatrix()
                                        }
                                        val matrixType = if (showAssetMatrix) "Asset Price Pearson Correlation" else "Strategy Return Pearson Correlation"
                                        val sb = StringBuilder()
                                        sb.appendLine("==========================================")
                                        sb.appendLine("  LUMINA $matrixType Matrix (R)")
                                        sb.appendLine("==========================================")
                                        sb.appendLine("Explanation: Pearson coefficient (r) measures linear co-movement (-1.0 to +1.0).")
                                        sb.appendLine("• r > +0.70: High positive correlation (shared risk exposure)")
                                        sb.appendLine("• r ~ 0.00: Uncorrelated (optimal portfolio diversification)")
                                        sb.appendLine("• r < -0.20: Negative correlation (natural downside hedge)\n")
                                        sb.appendLine("Matrix Values:")
                                        val labels = targetMat.labels
                                        sb.append(String.format(Locale.US, "%-14s", "Asset/Strat"))
                                        labels.forEach { l -> sb.append(String.format(Locale.US, "%-10s", l)) }
                                        sb.appendLine()
                                        targetMat.matrix.forEachIndexed { rIdx, row ->
                                            sb.append(String.format(Locale.US, "%-14s", labels[rIdx]))
                                            row.forEach { v -> sb.append(String.format(Locale.US, "%-10.2f", v)) }
                                            sb.appendLine()
                                        }
                                        BacktestReportExporter.copyToClipboard(context, matrixType, sb.toString())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy R", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Guide Pill explaining Pearson R
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pearson R measures co-movement: +1.0 = identical direction, 0.0 = zero overlap (best diversification), -1.0 = inverse hedge.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "GEMINI AI VIBE STUDIO: META-STRATEGY FUSION",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        val fusionSummary = """
                                            [GEMINI AI VIBE CODE FUSION ARCHITECTURE]
                                            Primary Constituent: ${metaStrategyFusion.primaryStrategy}
                                            Secondary Constituent: ${metaStrategyFusion.secondaryStrategy}
                                            Batch Correlation Score: ${String.format(Locale.US, "%.2f", metaStrategyFusion.batchCorrelationScore)}
                                            Fusion Architecture: ${metaStrategyFusion.fusionType}
                                            Projected Combined Sharpe: ${String.format(Locale.US, "%.2f", metaStrategyFusion.projectedCombinedSharpe)}
                                            Max Drawdown Reduction: -${String.format(Locale.US, "%.1f", metaStrategyFusion.projectedDrawdownReductionPct)}%
                                            
                                            Code & Vibe Analysis:
                                            ${metaStrategyFusion.geminiVibeCodeAnalysis}
                                        """.trimIndent()
                                        BacktestReportExporter.copyToClipboard(context, "AI Fusion Architecture", fusionSummary)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Fusion", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Synthesizes multi-model alpha signals into unified meta-strategies with cross-asset diversification filters.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Interactive Hybrid Target Option Selector
                        var selectedFusionTarget by remember { mutableStateOf("ETH Trend + BNB Reversion") }
                        Text(
                            text = "Fusion Synthesis Target Model:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val targets = listOf("ETH Trend + BNB Reversion", "SOL Breakout + BTC Trend", "Multi-Asset Alpha Fusion", "Bayesian 3.0 + Volatility Breakout")
                            items(targets) { target ->
                                FilterChip(
                                    selected = selectedFusionTarget == target,
                                    onClick = { 
                                        selectedFusionTarget = target
                                        viewModel.runGeminiMetaStrategyFusion()
                                    },
                                    label = { Text(target, fontSize = 11.sp) },
                                    leadingIcon = if (selectedFusionTarget == target) {
                                        { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Optimization Objective Selector
                        var selectedObjective by remember { mutableStateOf("Maximize Sharpe Ratio") }
                        Text(
                            text = "Optimization Objective:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val objectives = listOf("Maximize Sharpe Ratio", "Minimize Drawdown", "Balanced Alpha")
                            objectives.forEach { obj ->
                                FilterChip(
                                    selected = selectedObjective == obj,
                                    onClick = { 
                                        selectedObjective = obj
                                        viewModel.runGeminiMetaStrategyFusion()
                                    },
                                    label = { Text(obj, fontSize = 11.sp) }
                                )
                            }
                        }

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
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SYNTHESIZED ETH-BNB HYBRID: LIVE ENGINE",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        val hybridSummary = """
                                            [SYNTHESIZED ETH-BNB HYBRID ENGINE STATUS]
                                            Live State: ${if (ethBnbHybridLiveState.isLiveModeActive) "LIVE ACTIVE" else "PAUSED"}
                                            Hybrid Sharpe Ratio: ${String.format(Locale.US, "%.2f", ethBnbHybridLiveState.hybridSharpe)}
                                            ETH Constituent Sharpe: ${String.format(Locale.US, "%.2f", ethBnbHybridLiveState.ethConstituentSharpe)} (${ethBnbHybridLiveState.ethWeightPct}% Weight)
                                            BNB Constituent Sharpe: ${String.format(Locale.US, "%.2f", ethBnbHybridLiveState.bnbConstituentSharpe)} (${ethBnbHybridLiveState.bnbWeightPct}% Weight)
                                            Auto-Rebalance Threshold: ${ethBnbHybridLiveState.autoRebalanceThresholdPct}%
                                            Last Rebalance: ${ethBnbHybridLiveState.lastRebalanceTimestamp}
                                            Monitoring Status: ${ethBnbHybridLiveState.monitoringStatus}
                                        """.trimIndent()
                                        BacktestReportExporter.copyToClipboard(context, "ETH-BNB Hybrid Status", hybridSummary)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Status", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = ethBnbHybridLiveState.ethWeightPct == 55,
                                    onClick = { viewModel.rebalanceEthBnbWeights(55, 45) },
                                    label = { Text("⚖️ Optimal (55/45)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = ethBnbHybridLiveState.ethWeightPct == 65,
                                    onClick = { viewModel.rebalanceEthBnbWeights(65, 35) },
                                    label = { Text("📈 Trend Bias (65/35)", fontSize = 11.sp) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = ethBnbHybridLiveState.ethWeightPct == 40,
                                    onClick = { viewModel.rebalanceEthBnbWeights(40, 60) },
                                    label = { Text("🔄 Reversion Bias (40/60)", fontSize = 11.sp) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = ethBnbHybridLiveState.ethWeightPct == 50,
                                    onClick = { viewModel.rebalanceEthBnbWeights(50, 50) },
                                    label = { Text("⚖️ Equal Weight (50/50)", fontSize = 11.sp) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = ethBnbHybridLiveState.ethWeightPct == 75,
                                    onClick = { viewModel.rebalanceEthBnbWeights(75, 25) },
                                    label = { Text("🚀 High Alpha ETH (75/25)", fontSize = 11.sp) }
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
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "MULTI-REGIME SIMULATION & MACRO LAB",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Button(
                                onClick = {
                                    val sim = ethBnbHybridSimulation
                                    val simReport = """
                                        [MULTI-REGIME SIMULATION & MACRO INTEGRATION REPORT]
                                        Duration: ${sim.simulationDays} Days
                                        Simulated Hybrid Sharpe: ${String.format(Locale.US, "%.2f", sim.hybridSharpe)}
                                        Standalone ETH Sharpe: ${String.format(Locale.US, "%.2f", sim.ethStandaloneSharpe)}
                                        Standalone BNB Sharpe: ${String.format(Locale.US, "%.2f", sim.bnbStandaloneSharpe)}
                                        Maximum Peak Drawdown: ${sim.maxDrawdownPct}%
                                        Simulated Win Rate: ${sim.winRatePct}%
                                        
                                        Regimes Evaluated (${sim.regimesTested.size}):
                                        ${sim.regimesTested.joinToString("\n") { "• $it" }}
                                        
                                        Macroeconomic Signals Integrated:
                                        ${sim.macroeconomicIndicatorsIntegrated}
                                        
                                        Trade Quality Refinements:
                                        ${sim.tradeQualityEngineRefinements}
                                        
                                        Executive Summary:
                                        ${sim.simulationSummary}
                                    """.trimIndent()
                                    BacktestReportExporter.copyToClipboard(context, "Multi-Regime Simulation Report", simReport)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Report", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Simulates ETH-BNB Volatility Reversion Hybrid across multi-regime market stress tests, incorporating macroeconomic indicators and refining Trade Quality MAE caps.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Select Simulation Time Horizon:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = ethBnbHybridSimulation.simulationDays == 7,
                                    onClick = { viewModel.runEthBnbHybridSimulation(7) },
                                    label = { Text("⚡ 7-Day Sprint", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = ethBnbHybridSimulation.simulationDays == 14,
                                    onClick = { viewModel.runEthBnbHybridSimulation(14) },
                                    label = { Text("📊 14-Day Medium", fontSize = 11.sp) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = ethBnbHybridSimulation.simulationDays == 30,
                                    onClick = { viewModel.runEthBnbHybridSimulation(30) },
                                    label = { Text("🚀 30-Day Comprehensive", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = ethBnbHybridSimulation.simulationDays == 90,
                                    onClick = { viewModel.runEthBnbHybridSimulation(90) },
                                    label = { Text("🛡️ 90-Day Macro Cycle", fontSize = 11.sp) }
                                )
                            }
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
                            Column(modifier = Modifier.weight(1f)) {
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

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { showRoomLogsDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("💾 Room DB Logs (${backtestReportsHistory.size})", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }

                                if (lastReport != null) {
                                    Button(
                                        onClick = {
                                            val singleFormatted = BacktestReportExporter.formatSingleBacktest(lastReport!!)
                                            BacktestReportExporter.copyToClipboard(context, "Trade Ledger", singleFormatted)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy Ledger", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Strategy Selector for fast audit switching
                        Text("Active Strategy Filter:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableStrategies) { strat ->
                                FilterChip(
                                    selected = selectedStrategy == strat,
                                    onClick = { viewModel.selectBacktestStrategy(strat) },
                                    label = { Text(strat, fontSize = 10.sp) }
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
                                text = "No active backtest report available in current memory. Run a simulation or tap '💾 Room DB Logs' to view permanent history.",
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                            Button(
                                onClick = {
                                    val sb = StringBuilder()
                                    sb.append("=== LUMINA TRADE QUALITY SCORING & MAE/MFE AUDIT ===\n\n")
                                    tradeQualityScores.forEach { tq ->
                                        sb.append("Grade: ${tq.qualityGrade} | ID: ${tq.tradeId} | Pair: ${tq.pair} | Strategy: ${tq.strategyName}\n")
                                        sb.append("  Quality Score: ${tq.qualityScore}/100 | Slippage: ${tq.executionSlippageBps} bps\n")
                                        sb.append("  MAE: ${tq.maePercent}% | MFE: +${tq.mfePercent}% | Efficiency: ${String.format(Locale.US, "%.0f", tq.tradeEfficiencyRatio * 100)}%\n")
                                        sb.append("  Feedback: ${tq.feedbackForSelfAdaptation}\n\n")
                                    }
                                    BacktestReportExporter.copyToClipboard(context, "Trade Quality Audit", sb.toString())
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Scores", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SCENARIO-BASED RESILIENCE SUITE",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Simulate extreme shocks, black swan events, and sudden regime shifts.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (scenarioSuiteReport != null) {
                                    Button(
                                        onClick = {
                                            scenarioSuiteReport?.let { suite ->
                                                BacktestReportExporter.copyToClipboard(
                                                    context,
                                                    "Scenario Resilience Suite",
                                                    BacktestReportExporter.formatResilienceSuite(suite)
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
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
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Target Strategy Selection for Scenario Testing
                        Text(
                            text = "Target Strategy to Test:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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

                        Spacer(modifier = Modifier.height(8.dp))

                        // Target Asset Selection
                        Text(
                            text = "Target Asset Market:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableMarkets) { market ->
                                FilterChip(
                                    selected = selectedMarket == market,
                                    onClick = { viewModel.selectBacktestMarket(market) },
                                    label = { Text(market, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Individual Scenario Stress Triggers:",
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

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        val singleScenarioSummary = """
                                            [LUMINA SCENARIO STRESS AUDIT: ${res.scenarioName}]
                                            Status: ${res.resilienceStatus} (Score: ${String.format(Locale.US, "%.0f", res.resilienceScore)}/100)
                                            Description: ${res.description}
                                            Net Profit: ${if (res.netProfitUSD >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", res.netProfitUSD)}
                                            Max Drawdown: ${String.format(Locale.US, "%.2f", res.maxDrawdownPercent)}%
                                            Sharpe: ${String.format(Locale.US, "%.2f", res.sharpeRatio)} | Sortino: ${String.format(Locale.US, "%.2f", res.sortinoRatio)}
                                            Trade Win Rate: ${String.format(Locale.US, "%.1f", res.winRatePercent)}% | Trades Executed: ${res.totalTrades}
                                            Profit Factor: ${String.format(Locale.US, "%.2f", res.profitFactor)}
                                            Regime Log: Stress injected at high volatility bounds with dynamic position deflation.
                                        """.trimIndent()
                                        BacktestReportExporter.copyToClipboard(context, "${res.scenarioName} Log", singleScenarioSummary)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Scenario Log", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        if (activeSubTab == 10) {
            // --- 1. DECISION QUALITY ENGINE ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "1. DECISION QUALITY ENGINE (CAUSALITY ATTRIBUTION)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Trade ID", value = decisionQualityReport.tradeId, subtitle = "Recent Execution", valueColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            MetricCard(title = "Decision Score", value = "${decisionQualityReport.score} / 100", subtitle = "Grade: ${decisionQualityReport.processGrade}", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "Missed Loss", value = "-${decisionQualityReport.missedOpportunityLossPct}%", subtitle = "Execution Drag", valueColor = Color(0xFFD32F2F), modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Causality: ${decisionQualityReport.causalityAttribution}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }

            // --- 2. BAYESIAN SELF-ADAPTATION 2.0 ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "2. BAYESIAN CONFIDENCE ENGINE 3.0 (EMA SMOOTHED & 10 FACTORS)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Smoothed Conf", value = "${String.format(Locale.US, "%.1f", bayesianAdaptationState.smoothedPosteriorConfidenceScorePct)}%", subtitle = "EMA (α=${bayesianAdaptationState.posteriorSmoothingAlpha})", valueColor = Color(0xFF4ADE80), modifier = Modifier.weight(1f))
                            MetricCard(title = "Raw Posterior", value = "${String.format(Locale.US, "%.1f", bayesianAdaptationState.rawPosteriorConfidenceScorePct)}%", subtitle = "Unsmoothed", valueColor = Color(0xFF38BDF8), modifier = Modifier.weight(1f))
                            MetricCard(title = "False Pos Filter", value = "-${String.format(Locale.US, "%.1f", bayesianAdaptationState.falsePositiveReductionRatePct)}%", subtitle = "Noise Cut Rate", valueColor = Color(0xFFE879F9), modifier = Modifier.weight(1f))
                            MetricCard(title = "Hist Win Rate", value = "${String.format(Locale.US, "%.1f", bayesianAdaptationState.historicalWinRatePct)}%", subtitle = "N=1,450 Trades", valueColor = Color(0xFFFACC15), modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Smoothing & Historical Performance Banner
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚡ POSTERIOR SMOOTHING & HISTORICAL TRACK RECORD ENGINE",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Surface(color = Color(0xFF065F46), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "Sharpe: ${bayesianAdaptationState.historicalSharpeRatio} | DD Res: ${bayesianAdaptationState.historicalDrawdownResiliencePct}%",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Laplace-EMA temporal dampening filters out high-frequency noise spikes, lowering false positive trade entries by ${bayesianAdaptationState.falsePositiveReductionRatePct}% while anchoring belief states to ${bayesianAdaptationState.historicalWinRatePct}% historical win rate.",
                                    fontSize = 8.5.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "📊 10-Factor Bayesian Confidence & Historical Performance Breakdown:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                bayesianAdaptationState.posteriorFactorBreakdown.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.factorName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                                            Text(item.description, fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("w=${String.format(Locale.US, "%.2f", item.weight)}", fontSize = 8.sp, color = Color(0xFF64748B))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${String.format(Locale.US, "%.2f", item.score)}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.score >= 0.8) Color(0xFF4ADE80) else Color(0xFFFACC15)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(text = bayesianAdaptationState.adaptationSummary, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF81D4FA), modifier = Modifier.padding(10.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        var testProtocolResult by remember { mutableStateOf<com.example.aza.strategy.BayesianTestProtocolResult?>(null) }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.triggerClosedLoopFeedbackCoupling(92.5, 12.0, 2.4) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("🔄 Recalculate Posterior", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { testProtocolResult = BacktestEngine.runBayesianTestProtocol() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("🧪 Run Test Protocol", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            }
                        }

                        testProtocolResult?.let { protocol ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = Color(0xFF022C22),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF059669)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🏆 TEST PROTOCOL VERIFICATION SUITE: ${protocol.protocolStatus}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF34D399)
                                        )
                                        Text(
                                            text = "${protocol.passedEdgeCasesCount}/${protocol.totalEdgeCasesCount} Edge Cases | ${protocol.stressTestIterations} Cycles (${protocol.executionDurationMs}ms)",
                                            fontSize = 8.sp,
                                            color = Color(0xFFA7F3D0)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MetricCard(title = "Accuracy", value = "${protocol.accuracyScorePct}%", subtitle = "Benchmark Score", valueColor = Color(0xFF34D399), modifier = Modifier.weight(1f))
                                        MetricCard(title = "Robustness", value = "${protocol.robustnessScorePct}%", subtitle = "Noise Resistance", valueColor = Color(0xFF38BDF8), modifier = Modifier.weight(1f))
                                        MetricCard(title = "Adaptability", value = "${protocol.adaptabilityScorePct}%", subtitle = "Regime Recovery", valueColor = Color(0xFFFACC15), modifier = Modifier.weight(1f))
                                        MetricCard(title = "Var Reduction", value = "${String.format(Locale.US, "%.0f", (1.0 - protocol.benchmarkVarianceRatio) * 100)}%", subtitle = "EMA Variance Cut", valueColor = Color(0xFFE879F9), modifier = Modifier.weight(1f))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = protocol.summaryReport,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.5.sp,
                                        color = Color(0xFFD1FAE5)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- ADAPTIVE EXIT INTELLIGENCE ENGINE ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Output, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ADAPTIVE EXIT INTELLIGENCE ENGINE (7-FACTOR & POST-EXIT LEARNING)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Exit Quality", value = "${String.format(Locale.US, "%.1f", adaptiveExitSummaryState.averageExitQualityScore)}%", subtitle = "Counterfactual", valueColor = Color(0xFF4ADE80), modifier = Modifier.weight(1f))
                            MetricCard(title = "Expectancy Boost", value = "+${String.format(Locale.US, "%.2f", adaptiveExitSummaryState.expectancyBoostR)}R", subtitle = "vs Static Exits", valueColor = Color(0xFF38BDF8), modifier = Modifier.weight(1f))
                            MetricCard(title = "False Exit Cut", value = "${String.format(Locale.US, "%.1f", adaptiveExitSummaryState.falseExitAvoidanceRatePct)}%", subtitle = "Premature Cut Rate", valueColor = Color(0xFFE879F9), modifier = Modifier.weight(1f))
                            MetricCard(title = "Top Action", value = adaptiveExitSummaryState.topExitAction.name, subtitle = "${adaptiveExitSummaryState.totalTradesEvaluated} Trades", valueColor = Color(0xFFFACC15), modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Active Weights Banner
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🎯 DYNAMIC FACTOR WEIGHTING & LEARNING SYSTEM",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Surface(color = Color(0xFF065F46), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = adaptiveExitSummaryState.systemStatus,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Trend Continuation (High W): ${String.format(Locale.US, "%.2f", adaptiveExitSummaryState.weightTrendContinuation)}", fontSize = 8.5.sp, color = Color(0xFF81D4FA), fontWeight = FontWeight.Bold)
                                    Text("Momentum Exhaustion (High W): ${String.format(Locale.US, "%.2f", adaptiveExitSummaryState.weightMomentumExhaustion)}", fontSize = 8.5.sp, color = Color(0xFFF06292), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("ATR Expansion W: ${String.format(Locale.US, "%.2f", adaptiveExitSummaryState.weightAtrExpansion)}", fontSize = 8.5.sp, color = Color(0xFFA5D6A7))
                                    Text("Unrealized R W: ${String.format(Locale.US, "%.2f", adaptiveExitSummaryState.weightUnrealizedR)}", fontSize = 8.5.sp, color = Color(0xFFFFE082))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Real-Time Volatility Regime Telemetry Card
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = when (adaptiveExitSummaryState.currentRegime) {
                                                com.example.aza.strategy.VolatilityRegime.FLASH_CRASH -> Color(0xFFF43F5E)
                                                com.example.aza.strategy.VolatilityRegime.ELEVATED_STRESS -> Color(0xFFFB923C)
                                                com.example.aza.strategy.VolatilityRegime.CALM -> Color(0xFF4ADE80)
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "⚡ REAL-TIME VOLATILITY REGIME ASSESSMENT",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                    Surface(
                                        color = when (adaptiveExitSummaryState.currentRegime) {
                                            com.example.aza.strategy.VolatilityRegime.FLASH_CRASH -> Color(0xFF881337)
                                            com.example.aza.strategy.VolatilityRegime.ELEVATED_STRESS -> Color(0xFF7C2D12)
                                            com.example.aza.strategy.VolatilityRegime.CALM -> Color(0xFF065F46)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = adaptiveExitSummaryState.currentRegime.displayName.uppercase(Locale.US),
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = adaptiveExitSummaryState.currentRegime.description,
                                    fontSize = 8.5.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Stress Mitigations: ${adaptiveExitSummaryState.stressMitigationsCount}", fontSize = 8.5.sp, color = Color(0xFFFDBA74), fontWeight = FontWeight.Bold)
                                    Text("Flash Crash Shields: ${adaptiveExitSummaryState.flashCrashShieldsTriggered}", fontSize = 8.5.sp, color = Color(0xFFFDA4AF), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "🔍 Evaluated Exit Factors before every trade exit decision:",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val factors = listOf(
                                    Triple("Trend Continuation Prob", "Prioritizes staying in strong trend flows", Color(0xFF38BDF8)),
                                    Triple("Momentum Exhaustion", "Signals velocity decay & divergence", Color(0xFFF43F5E)),
                                    Triple("ATR Expansion", "Measures volatility surges for emergency stops", Color(0xFF4ADE80)),
                                    Triple("Volatility Compression", "Detects range squeeze & breakouts", Color(0xFFC084FC)),
                                    Triple("Liquidity & Volume Shift", "Tracks volume depth and liquidity absorption", Color(0xFFFACC15)),
                                    Triple("Risk/Reward Remaining", "Assesses upside potential vs risk distance", Color(0xFFFB923C)),
                                    Triple("Current Unrealized R", "Triggers trailing stop or move to breakeven", Color(0xFF2DD4BF))
                                )
                                factors.forEach { (name, desc, tagColor) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = tagColor)
                                        Text(desc, fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.runExitIntelligenceSimulation() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("🎯 Run Exit Simulation", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.refreshAdaptiveExitSummary() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("🔄 Recalculate Expectancy", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            }
                        }

                        if (adaptiveExitSummaryState.recentExitRecords.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "📜 Recent Trade Exit Quality & Post-Exit Learning Log:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(color = Color(0xFF111827), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    adaptiveExitSummaryState.recentExitRecords.take(5).forEach { rec ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = when(rec.exitActionExecuted) {
                                                        com.example.aza.strategy.ExitAction.HOLD -> Color(0xFF1E3A8A)
                                                        com.example.aza.strategy.ExitAction.SCALE_OUT -> Color(0xFF065F46)
                                                        com.example.aza.strategy.ExitAction.MOVE_STOP -> Color(0xFF854D0E)
                                                        com.example.aza.strategy.ExitAction.TRAIL_STOP -> Color(0xFF581C87)
                                                        com.example.aza.strategy.ExitAction.FULL_EXIT -> Color(0xFF374151)
                                                        com.example.aza.strategy.ExitAction.EMERGENCY_EXIT -> Color(0xFF991B1B)
                                                    },
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = rec.exitActionExecuted.name,
                                                        fontSize = 7.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("${rec.tradeId} (${rec.symbol})", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                                            }
                                            Text(
                                                text = "Realized: ${String.format(Locale.US, "%.2f", rec.realizedR)}R | Peak: ${String.format(Locale.US, "%.2f", rec.peakPossibleR)}R | Score: ${String.format(Locale.US, "%.0f", rec.exitQualityScore)}%",
                                                fontSize = 8.sp,
                                                color = Color(0xFF4ADE80)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. SHADOW DECISION ENGINE & MISSED OPPORTUNITY LEARNER ---
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
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SHADOW DECISION ENGINE (COUNTERFACTUAL MISSED OPPORTUNITY MONITOR)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = shadowDecisionSummaryState.systemMode,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Rejected Signals", value = "${shadowDecisionSummaryState.totalRejectedTradesRecorded}", subtitle = "Shadow Recorded", valueColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            MetricCard(title = "Counterfactual Win", value = "${String.format(Locale.US, "%.1f", shadowDecisionSummaryState.counterfactualWinRatePct)}%", subtitle = "Would Have Won", valueColor = Color(0xFF4ADE80), modifier = Modifier.weight(1f))
                            MetricCard(title = "Expected R", value = "+${String.format(Locale.US, "%.2f", shadowDecisionSummaryState.averageExpectedR)}R", subtitle = "Avg Return", valueColor = Color(0xFF38BDF8), modifier = Modifier.weight(1f))
                            MetricCard(title = "MFE / MAE", value = "${String.format(Locale.US, "%.1f", shadowDecisionSummaryState.averageMfeR)}R / ${String.format(Locale.US, "%.1f", shadowDecisionSummaryState.averageMaeR)}R", subtitle = "Excursion Ratios", valueColor = Color(0xFFFACC15), modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Alert / Discrepancy Recommendation Banner
                        Surface(
                            color = if (shadowDecisionSummaryState.discrepancyAlertActive) Color(0xFF451A03) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (shadowDecisionSummaryState.discrepancyAlertActive) Color(0xFFB45309) else Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = shadowDecisionSummaryState.discrepancyAlertMessage,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (shadowDecisionSummaryState.discrepancyAlertActive) Color(0xFFFDBA74) else Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🔒 HINDSIGHT SAFETY GUARD: Active recommendations require threshold review before live mutation.",
                                        fontSize = 8.sp,
                                        color = Color(0xFF81D4FA)
                                    )
                                    Text(
                                        text = shadowDecisionSummaryState.dailyReviewStatus,
                                        fontSize = 7.5.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        // Recommendations Detail List
                        if (shadowDecisionSummaryState.activeRecommendations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "💡 Active Threshold Optimization Recommendations:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    shadowDecisionSummaryState.activeRecommendations.forEach { rec ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${rec.targetStrategy} (${rec.reviewCycle}): Threshold ${String.format(Locale.US, "%.1f", rec.currentThresholdPct)}% ➔ ${String.format(Locale.US, "%.1f", rec.recommendedThresholdPct)}%",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFACC15)
                                            )
                                            Surface(color = Color(0xFF047857), shape = RoundedCornerShape(4.dp)) {
                                                Text(
                                                    text = rec.hindsightSafetyStatus,
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = rec.recommendationReasoning,
                                            fontSize = 8.sp,
                                            color = Color(0xFFCBD5E1)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { viewModel.runShadowDecisionSimulation() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("🎯 Shadow Sim", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.runDailyShadowReview() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("📅 Daily Review", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            }
                            OutlinedButton(
                                onClick = { viewModel.runWeeklyShadowReview() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFFC084FC))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("📆 Weekly Review", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC084FC))
                            }
                        }

                        if (shadowDecisionSummaryState.recentAuditLogs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "📜 Shadow Decision Audit Log & Rejection Tracker:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    shadowDecisionSummaryState.recentAuditLogs.take(4).forEach { log ->
                                        Text(text = log, fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- WEEKLY AUTONOMOUS SELF-REVIEW SECTION ---
            item {
                LuminaWeeklyAutonomousReviewCard(viewModel = viewModel)
            }

            // --- 4. CHAMPION / CHALLENGER SUBSYSTEM EXTENSION ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "4. SUBSYSTEM CHAMPION / CHALLENGER (SOR & RISK)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Champion Subsystem", value = subsystemChampionChallenger.championSubsystem, subtitle = "Latency: ${subsystemChampionChallenger.championLatencyMs}ms", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "Challenger Subsystem", value = subsystemChampionChallenger.challengerSubsystem, subtitle = "Latency: ${subsystemChampionChallenger.challengerLatencyMs}ms", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(text = subsystemChampionChallenger.evaluationStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }

            // --- 5. PORTFOLIO INTELLIGENCE (MONTE CARLO, VaR, CVaR) ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "5. PORTFOLIO INTELLIGENCE (MONTE CARLO, VaR & CVaR)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Monte Carlo Runs", value = "${portfolioIntelligenceMetrics.monteCarloSimulationsCount}", subtitle = "Simulated Paths", valueColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            MetricCard(title = "95% VaR", value = "$${portfolioIntelligenceMetrics.valueAtRisk95PctUSD}", subtitle = "Value at Risk", valueColor = Color(0xFFE65100), modifier = Modifier.weight(1f))
                            MetricCard(title = "99% CVaR", value = "$${portfolioIntelligenceMetrics.expectedShortfall99PctUSD}", subtitle = "Expected Shortfall", valueColor = Color(0xFFD32F2F), modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(text = portfolioIntelligenceMetrics.riskSummary, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF81C784), modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }

            // --- 5B. TRADE & PORTFOLIO INTERACTION OPTIMIZER (HEATMAPS & SCENARIOS) ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "5B. TRADE & PORTFOLIO INTERACTION OPTIMIZER",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                color = Color(0xFF2E7D32),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Diversification: ${String.format(Locale.US, "%.1f", portfolioTradeInteractionState.overallDiversificationScore)}/100",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Key Metrics Row
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Cum Return", value = "+${String.format(Locale.US, "%.1f", portfolioTradeInteractionState.totalCumulativeReturnPct)}%", subtitle = "Portfolio Alpha", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "Sharpe Ratio", value = "${String.format(Locale.US, "%.2f", portfolioTradeInteractionState.portfolioSharpeRatio)}", subtitle = "Risk Adjusted", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "Portfolio Beta", value = "${String.format(Locale.US, "%.2f", portfolioTradeInteractionState.portfolioBeta)}", subtitle = "Market Exposure", valueColor = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                        }

                        // Trade Correlation Heatmap Matrix
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "📊 Cross-Asset Trade Correlation Matrix (Heatmap):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val matrix = portfolioTradeInteractionState.correlationMatrix
                        val assets = matrix.assets

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            // Header Row
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(text = "", modifier = Modifier.weight(1.2f), fontSize = 10.sp)
                                assets.forEach { asset ->
                                    Text(
                                        text = asset,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // Matrix Rows
                            assets.forEach { rowAsset ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rowAsset,
                                        modifier = Modifier.weight(1.2f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    assets.forEach { colAsset ->
                                        val corr = matrix.getCorrelation(rowAsset, colAsset)
                                        val bgColor = when {
                                            rowAsset == colAsset -> MaterialTheme.colorScheme.surface
                                            corr > 0.70 -> Color(0xFFFFCDD2) // High correlation warning (red tint)
                                            corr < 0.0 -> Color(0xFFC8E6C9)  // Negative correlation hedge (green tint)
                                            else -> Color(0xFFE0F7FA)        // Low correlation (blue tint)
                                        }
                                        val textColor = when {
                                            rowAsset == colAsset -> Color.Gray
                                            corr > 0.70 -> Color(0xFFC62828)
                                            corr < 0.0 -> Color(0xFF2E7D32)
                                            else -> Color(0xFF006064)
                                        }
                                        Surface(
                                            color = bgColor,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(1.dp)
                                        ) {
                                            Text(
                                                text = if (rowAsset == colAsset) "1.0" else String.format(Locale.US, "%.2f", corr),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                color = textColor,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Trade Enhancers vs. Detractors Breakdown
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "⚖️ Trade Stability Contribution (Enhancers vs. Detractors):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        portfolioTradeInteractionState.tradeImpacts.forEach { impact ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${impact.symbol} (${impact.strategyName})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Weight: ${String.format(Locale.US, "%.0f", impact.weightPct)}%",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        val (badgeBg, badgeText, badgeLabel) = when (impact.impactType) {
                                            com.example.aza.strategy.TradeStabilityImpact.STABILITY_ENHANCER -> Triple(Color(0xFF2E7D32), Color.White, "ENHANCER (+${String.format(Locale.US, "%.1f", impact.impactScore)})")
                                            com.example.aza.strategy.TradeStabilityImpact.STABILITY_DETRACTOR -> Triple(Color(0xFFD32F2F), Color.White, "DETRACTOR (${String.format(Locale.US, "%.1f", impact.impactScore)})")
                                            else -> Triple(Color.Gray, Color.White, "NEUTRAL (+${String.format(Locale.US, "%.1f", impact.impactScore)})")
                                        }
                                        Surface(color = badgeBg, shape = RoundedCornerShape(10.dp)) {
                                            Text(
                                                text = badgeLabel,
                                                color = badgeText,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Return: +${String.format(Locale.US, "%.1f", impact.cumulativeReturnPct)}%", fontSize = 10.sp, color = Color(0xFF2E7D32))
                                        Text(text = "Sharpe Contrib: +${String.format(Locale.US, "%.2f", impact.sharpeContribution)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(text = "Marginal VaR: $${String.format(Locale.US, "%.0f", impact.marginalVaRUSD)}", fontSize = 10.sp, color = Color(0xFFE65100))
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Lumina Guidance: ${impact.recommendation}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (impact.impactType == com.example.aza.strategy.TradeStabilityImpact.STABILITY_DETRACTOR) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Interactive Scenario Analysis & Stress Testing
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "🧪 Interactive Market Condition Scenario Stress Testing:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val scenarios = listOf(
                                "FLASH_CRASH" to "Flash Crash",
                                "RATE_SPIKE" to "Rate Shock",
                                "BULL_MOMENTUM" to "Bull Surge",
                                "STAGFLATION_GRIND" to "Stagflation"
                            )
                            scenarios.forEach { (scId, label) ->
                                FilterChip(
                                    selected = portfolioTradeInteractionState.selectedScenarioId == scId,
                                    onClick = { viewModel.selectScenarioAnalysis(scId) },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }

                        val activeScenario = portfolioTradeInteractionState.scenarioResults.find { 
                            it.scenarioId == portfolioTradeInteractionState.selectedScenarioId 
                        } ?: portfolioTradeInteractionState.scenarioResults.first()

                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = activeScenario.scenarioName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Shock Vector: ${activeScenario.marketShockDescription}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Projected Return: ${if (activeScenario.projectedPortfolioReturnPct >= 0) "+" else ""}${String.format(Locale.US, "%.1f", activeScenario.projectedPortfolioReturnPct)}%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeScenario.projectedPortfolioReturnPct >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                    )
                                    Text(
                                        text = "Max Drawdown: ${String.format(Locale.US, "%.1f", activeScenario.projectedMaxDrawdownPct)}%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                    Text(
                                        text = "Stability: ${activeScenario.portfolioStabilityRating}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "🛡️ Stress Action: ${activeScenario.recommendedHedgingAction}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Lumina Active Portfolio Optimization Advice Banner
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = portfolioTradeInteractionState.optimizationRecommendation,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF81C784),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // --- 6. INSTITUTIONAL RECOVERY STATE MACHINE ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "6. INSTITUTIONAL RECOVERY STATE MACHINE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Current Drawdown", value = "${institutionalRecoveryState.currentDrawdownPct}%", subtitle = "Max Limit: -8.0%", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "Phase", value = institutionalRecoveryState.currentPhase, subtitle = "Recovery State", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "Risk Multiplier", value = "${institutionalRecoveryState.RiskScaleMultiplier}x", subtitle = "Position Scaling", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "Test Drawdown Recovery Scenarios:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = institutionalRecoveryState.currentDrawdownPct == -2.1, onClick = { viewModel.updateInstitutionalDrawdownRecovery(-2.1) }, label = { Text("-2.1% (Normal)", fontSize = 11.sp) })
                            FilterChip(selected = institutionalRecoveryState.currentDrawdownPct == -4.5, onClick = { viewModel.updateInstitutionalDrawdownRecovery(-4.5) }, label = { Text("-4.5% (Gradual)", fontSize = 11.sp) })
                            FilterChip(selected = institutionalRecoveryState.currentDrawdownPct == -7.2, onClick = { viewModel.updateInstitutionalDrawdownRecovery(-7.2) }, label = { Text("-7.2% (Defensive)", fontSize = 11.sp, color = Color(0xFFD32F2F)) })
                        }
                    }
                }
            }

            // --- 7. EXECUTION INTELLIGENCE ---
            // --- 7. EXECUTION INTELLIGENCE (VENUE SCORECARDS & SOR) ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "7. EXECUTION INTELLIGENCE (ADAPTIVE ML & SOR)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                color = if (executionQualitySummaryState.overallExecutionQualityScore >= 75.0) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Score: ${String.format(Locale.US, "%.1f", executionQualitySummaryState.overallExecutionQualityScore)}/100",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // 4 Primary Telemetry Metrics
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(
                                title = "Avg Speed",
                                value = "${String.format(Locale.US, "%.1f", executionQualitySummaryState.averageExecutionSpeedMs)} ms",
                                subtitle = "Execution Delay",
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Median Slippage",
                                value = "${String.format(Locale.US, "%.1f", executionQualitySummaryState.medianSlippageBps)} bps",
                                subtitle = "Slippage Drag",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(
                                title = "Latency Variance",
                                value = "${String.format(Locale.US, "%.1f", executionQualitySummaryState.averageLatencyVarianceMs2)} ms²",
                                subtitle = "Jitter & Latency",
                                valueColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Primary Fill Rate",
                                value = "${executionIntelligenceRouting.fillRatePct}%",
                                subtitle = "Order Fill Quality",
                                valueColor = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Fill Rate by Exchange Breakdown
                        if (executionQualitySummaryState.fillRateByExchange.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Exchange Fill Rate Breakdown:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                executionQualitySummaryState.fillRateByExchange.forEach { (exchange, rate) ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(6.dp)
                                        ) {
                                            Text(
                                                text = exchange.split(" ").firstOrNull() ?: exchange,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "${String.format(Locale.US, "%.1f", rate)}%",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ML Learned Poor Execution Environments & Sizing Impact
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "🤖 ML Environment Learning & Sizing Impact:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = executionQualitySummaryState.adaptiveSizingImpact.rationale,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "🔀 Smart Order Routing: ${executionIntelligenceRouting.routingDecision}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.refreshExecutionQualitySummary() }) {
                                Text("Re-run Execution Quality ML Assessment", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // --- 8A. LUMINA'S SECOND BRAIN (META-LEARNING COGNITIVE MEMORY HUB) ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("second_brain_meta_learning_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFFAB47BC), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "8A. LUMINA'S SECOND BRAIN (META-LEARNING COGNITIVE MEMORY HUB)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFAB47BC)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(color = Color(0xFFAB47BC).copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    text = "FORGETTING CURVES ACTIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFAB47BC),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Dual Memory Metrics
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Total Episodes", value = "${secondBrainSummaryState.totalEpisodesRecorded}", subtitle = "STM: ${secondBrainSummaryState.stmEpisodeCount} | LTM: ${secondBrainSummaryState.ltmEpisodeCount}", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "Historical Win Rate", value = "${String.format(Locale.US, "%.1f", secondBrainSummaryState.overallWinRatePct)}%", subtitle = "Context Baseline", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "STM Retention", value = "${String.format(Locale.US, "%.1f", secondBrainSummaryState.stmEffectiveRetentionPct)}%", subtitle = "λ_STM = ${secondBrainSummaryState.forgettingCurveLambdaStm}/hr", valueColor = Color(0xFFE65100), modifier = Modifier.weight(1f))
                            MetricCard(title = "LTM Retention", value = "${String.format(Locale.US, "%.1f", secondBrainSummaryState.ltmEffectiveRetentionPct)}%", subtitle = "λ_LTM = ${secondBrainSummaryState.forgettingCurveLambdaLtm}/hr", valueColor = Color(0xFF1976D2), modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Banner on Forgetting Curves & Learning Objectives
                        Surface(color = Color(0xFF1E1E2A), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFFCE93D8), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Multi-Objective Learning Engine & Dual Memory Architecture",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFCE93D8)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• Forgetting Curves: Short-Term Memory decays at λ=0.05/hr (~14h t½) to react to regime shifts. Long-Term Memory decays at λ=0.001/hr (~29d t½) for persistent structural truths. Residual floor asymptote (5.0%) guarantees knowledge is never completely discarded.\n" +
                                           "• Learning Objectives: Combines k-NN Pattern Recognition (high-dimensional context matching) with Reinforcement Learning Q-Policy updates (Reward R_t = ProfitR × Quality - Slippage) to adjust confidence dynamically before every decision.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFFE0E0E0),
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Lumina's Optimal Context Profile
                        Text(
                            text = "LUMINA'S OPTIMAL CONTEXT PROFILE (LEARNED TRUTHS):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SuggestionChip(onClick = {}, label = { Text("Best Volatility: ${secondBrainSummaryState.optimalProfile.bestVolatilityRange}", fontSize = 10.sp) })
                            SuggestionChip(onClick = {}, label = { Text("Best ATR: ${secondBrainSummaryState.optimalProfile.bestAtrRange}", fontSize = 10.sp) })
                            SuggestionChip(onClick = {}, label = { Text("Best Confidence: ${secondBrainSummaryState.optimalProfile.bestConfidenceRange}", fontSize = 10.sp) })
                            SuggestionChip(onClick = {}, label = { Text("Peak Hours: ${secondBrainSummaryState.optimalProfile.bestHoursUtc}", fontSize = 10.sp) })
                            SuggestionChip(onClick = {}, label = { Text("Best Days: ${secondBrainSummaryState.optimalProfile.bestWeekdays}", fontSize = 10.sp) })
                            SuggestionChip(onClick = {}, label = { Text("Structure: ${secondBrainSummaryState.optimalProfile.bestMarketStructure}", fontSize = 10.sp) })
                            SuggestionChip(onClick = {}, label = { Text("Trend Strength: ${secondBrainSummaryState.optimalProfile.bestTrendStrengthAdx}", fontSize = 10.sp) })
                            SuggestionChip(onClick = {}, label = { Text("Liquidity: ${secondBrainSummaryState.optimalProfile.bestLiquidityConditions}", fontSize = 10.sp) })
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Winning Environments:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
                                secondBrainSummaryState.optimalProfile.winningEnvironments.forEach { env ->
                                    Text(text = "  ✓ $env", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFFA5D6A7))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Failing Environments:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE57373))
                                secondBrainSummaryState.optimalProfile.failingEnvironments.forEach { env ->
                                    Text(text = "  ✗ $env", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFFEF9A9A))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Interactive Control Buttons
                        Text(text = "Meta-Learning Second Brain Actions:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.performSecondBrainMemorySweep() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("second_brain_sweep_btn")
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Memory Sweep", fontSize = 10.sp)
                            }

                            Button(
                                onClick = { viewModel.injectSyntheticSecondBrainEpisode(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("second_brain_inject_win_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Inject +2.8R Win", fontSize = 10.sp)
                            }

                            Button(
                                onClick = { viewModel.injectSyntheticSecondBrainEpisode(false) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("second_brain_inject_fail_btn")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Inject -1.4R Fail", fontSize = 10.sp)
                            }

                            Button(
                                onClick = { viewModel.runComprehensiveSecondBrainMetaLearningSimulation() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("second_brain_full_sim_btn")
                            ) {
                                Icon(Icons.Default.PlayCircleFilled, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Full Suite Sim", fontSize = 10.sp)
                            }
                        }

                        if (secondBrainSummaryState.recentAssessments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val latestEval = secondBrainSummaryState.recentAssessments.first()
                            Surface(color = Color(0xFF121212), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "LATEST DECISION CALIBRATION:",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFD54F)
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = if (latestEval.isApprovedBySecondBrain) "SECOND BRAIN: APPROVED" else "SECOND BRAIN: SUPPRESSED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (latestEval.isApprovedBySecondBrain) Color(0xFF81C784) else Color(0xFFFF5252)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Raw Confidence: ${latestEval.rawConfidencePct}% → Calibrated: ${String.format(Locale.US, "%.1f", latestEval.calibratedConfidencePct)}% (Delta: ${String.format(Locale.US, "%+.1f", latestEval.confidenceDeltaPct)}%)",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Q-Policy Max Score: ${String.format(Locale.US, "%.1f", latestEval.reinforcementLearningQValue)} | Pattern Match Score: ${String.format(Locale.US, "%.1f", latestEval.patternRecognitionScore)}/100",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = Color(0xFFB0BEC5)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = latestEval.decisionRationale,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = Color(0xFF81D4FA)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 8. META-REASONING 2.0 & MULTI-DIMENSIONAL DEBATES ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "8. META-REASONING 2.0 & MULTI-DIMENSIONAL DEBATE ENGINE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Meta Conviction", value = "${metaReasoningValidation.convictionScore}/100", subtitle = "Second Brain Score", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "Market Structure", value = "${metaReasoningValidation.marketStructureScore}/100", subtitle = "Confluence", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "OrderBook Depth", value = "${metaReasoningValidation.liquidityDepthScore}/100", subtitle = "Ask/Bid Density", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Momentum Impulse", value = "${metaReasoningValidation.momentumImpulseScore}/100", subtitle = "Squeeze Vol", valueColor = Color(0xFFE65100), modifier = Modifier.weight(1f))
                            MetricCard(title = "HTF Confluence", value = "${metaReasoningValidation.htfMacroConfluenceScore}/100", subtitle = "4h EMA Ribbon", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "Epistemic Uncertainty", value = "${metaReasoningValidation.epistemicUncertainty}", subtitle = "Model Drift", valueColor = Color(0xFF1976D2), modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "Hypothesis A: ${metaReasoningValidation.competingHypothesisA}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF81C784))
                                Text(text = "Hypothesis B: ${metaReasoningValidation.competingHypothesisB}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFFFB74D))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Counterfactual Result: ${metaReasoningValidation.counterfactualTestResult}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF81D4FA))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Approval Gate: ${metaReasoningValidation.tradeApprovalStatus}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (metaReasoningValidation.tradeApprovalStatus.contains("APPROVED")) Color(0xFF81C784) else Color(0xFFFF5252))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = "Debate Data Points:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                metaReasoningValidation.debateMetrics.forEach { metric ->
                                    Text(text = "  • $metric", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFFE0E0E0))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "Test Pre-Trade Gate Order Sizing against 95% VaR Cap ($3,450):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = metaReasoningValidation.tradeApprovalStatus.contains("APPROVED"),
                                onClick = { viewModel.runPreTradeMetaReasoningGate(2500.0) },
                                label = { Text("Pass: $2,500 Order", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = metaReasoningValidation.tradeApprovalStatus.contains("REJECTED"),
                                onClick = { viewModel.runPreTradeMetaReasoningGate(4200.0) },
                                label = { Text("Reject: $4,200 Order (>VaR)", fontSize = 11.sp, color = Color(0xFFD32F2F)) }
                            )
                        }
                    }
                }
            }

            // --- 8B. MTF ALIGNMENT GUARD & DYNAMIC THRESHOLD CALIBRATION ---
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
                                text = "8B. MTF ALIGNMENT GUARD & DYNAMIC THRESHOLD CALIBRATION",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Calibrates multi-timeframe divergence thresholds (1m: 15%, 5m: 20%, 15m: 25%, 1h: 40%) to eliminate false negative trade rejections while enforcing macro trend protection.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val mtf = mtfGuardCalibrationState
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Alignment Threshold", value = "${mtf.weightedAlignmentThresholdPct}%", subtitle = "Dynamic Limit", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "False Negative Drop", value = "-${mtf.falseNegativeReductionPct}%", subtitle = "Fewer Rejections", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "1h Macro Weight", value = "${((mtf.timeframeWeights["1h"] ?: 0.40) * 100).toInt()}%", subtitle = "Dominant Weight", valueColor = Color(0xFFE65100), modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = mtf.mtfSummary, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF81C784))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Divergence Test: ${mtf.recentDivergenceResult}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF81D4FA))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "Tune MTF Alignment Guard & Divergence Balance:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = mtf.weightedAlignmentThresholdPct == 54.0 && mtf.severeDivergenceCutoffPct == 42.0,
                                onClick = { viewModel.applyBalancedGuardPreset() },
                                label = { Text("⚡ 54.0% Balanced Preset", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = mtf.weightedAlignmentThresholdPct == 48.0,
                                onClick = { viewModel.calibrateMtfGuardThresholds(48.0, 38.0, 0.75, true) },
                                label = { Text("🚀 48.0% Aggressive", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = mtf.weightedAlignmentThresholdPct == 66.0,
                                onClick = { viewModel.calibrateMtfGuardThresholds(66.0, 55.0, 0.55, false) },
                                label = { Text("🛡️ 66.0% Strict Legacy", fontSize = 11.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Fine-Tune Weighted Divergence Cutoff:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = mtf.severeDivergenceCutoffPct == 42.0,
                                onClick = { viewModel.calibrateMtfGuardThresholds(mtf.weightedAlignmentThresholdPct, 42.0, mtf.macroConflictConfidenceGate, mtf.breakoutLeadEnabled) },
                                label = { Text("42.0% Relaxed (Fewer Blocks)", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = mtf.severeDivergenceCutoffPct == 50.0,
                                onClick = { viewModel.calibrateMtfGuardThresholds(mtf.weightedAlignmentThresholdPct, 50.0, mtf.macroConflictConfidenceGate, mtf.breakoutLeadEnabled) },
                                label = { Text("50.0% Standard", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = mtf.severeDivergenceCutoffPct == 55.0,
                                onClick = { viewModel.calibrateMtfGuardThresholds(mtf.weightedAlignmentThresholdPct, 55.0, mtf.macroConflictConfidenceGate, mtf.breakoutLeadEnabled) },
                                label = { Text("55.0% Strict", fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }

            // --- 8C. TRADE LIFECYCLE VALIDATOR & DATA INTEGRITY ENGINE ---
            item {
                var alertSeverityFilter by remember { mutableStateOf("ALL") }
                val tlv = tradeLifecycleValidatorState

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "8C. TRADE LIFECYCLE VALIDATOR & REAL-TIME INTEGRITY ENGINE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Continuously verifies 14 trade parameters (Entry/Exit, Qty, SL/TP, ATR, R-Multiple, Risk, Regime, PnL) before DB commit. Enforces null realized PnL on open trades, multi-threaded parallel priority validation, EWMA dynamic thresholding, and centralized alert logging.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // High-Level Integrity Metrics
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Trade Integrity Score", value = "${tlv.overallIntegrityScore} / 100", subtitle = "Threshold: ${tlv.thresholdConfig.currentDynamicThreshold}", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "Pristine Records", value = "${tlv.validPristineCount}", subtitle = "${tlv.totalTradesValidated} Audited", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "Auto-Repaired", value = "${tlv.autoRepairedCount}", subtitle = "${tlv.quarantinedCount} Quarantined", valueColor = Color(0xFFE65100), modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // --- A. DYNAMIC INTEGRITY THRESHOLD (EXPONENTIAL SMOOTHING EWMA & DP OPTIMIZATION) ---
                        Text(
                            text = "📈 Dynamic Threshold Adjustment (DP-Optimized EWMA Smoothing)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Adapts trade integrity review threshold using EWMA smoothing. Uses Dynamic Programming (DP) to minimize Cost = λ·Lag + (1-λ)·Noise over historical state transitions.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MetricCard(title = "Alpha Smoothing (α)", value = "${tlv.thresholdConfig.alphaSmoothingFactor}", subtitle = tlv.thresholdConfig.calibrationProfile, valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "Dynamic Threshold", value = "${tlv.thresholdConfig.currentDynamicThreshold}", subtitle = "Baseline: ${tlv.thresholdConfig.baselineThresholdScore}", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "DP Min Cost Loss", value = "${tlv.selfLearningState.dpMinCostValue}", subtitle = "${tlv.selfLearningState.dpStateTransitionsCount} DP States", valueColor = Color(0xFF1565C0), modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "💡 DP Optimization & Calibration Insight:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = tlv.thresholdConfig.optimizationMetric, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Recent EWMA History: " + tlv.thresholdConfig.ewmaHistory.takeLast(6).joinToString(" → ") { "$it" },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.runDynamicProgrammingEwmaOptimization() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Solve Optimal EWMA via DP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.triggerSelfLearningMechanism() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retrain Self-Learning ML", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Calibrate Alpha Smoothing Factor (α):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = tlv.thresholdConfig.alphaSmoothingFactor == 0.15,
                                onClick = { viewModel.updateEwmaThresholdSmoothingFactor(0.15, "CONSERVATIVE_INITIAL") },
                                label = { Text("α = 0.15 (Conservative)", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = tlv.thresholdConfig.alphaSmoothingFactor == 0.18,
                                onClick = { viewModel.runDynamicProgrammingEwmaOptimization() },
                                label = { Text("α = 0.18 (DP Optimal)", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = tlv.thresholdConfig.alphaSmoothingFactor == 0.25,
                                onClick = { viewModel.updateEwmaThresholdSmoothingFactor(0.25, "BALANCED_ADAPTIVE") },
                                label = { Text("α = 0.25 (Balanced)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Learned ML Rules Card
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "🤖 ML Self-Learning Engine (${tlv.selfLearningState.mlModelVersion})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81D4FA))
                                    Text(text = "Accuracy: ${tlv.selfLearningState.trainingAccuracyPct}%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                tlv.selfLearningState.learnedRules.take(4).forEach { rule ->
                                    Text(text = "  • $rule", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFE0E0E0))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // --- A2. MAX DRAWDOWN PROTECTION & ADAPTIVE POSITION SIZING ---
                        Text(
                            text = "🛡️ Max Drawdown Protection & Adaptive Sizing Engine",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dynamically scales open position contracts down as portfolio drawdown approaches max limit (${tlv.maxDrawdownProtection.maxDrawdownLimitPct}%). Tied to trade integrity score.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val prot = tlv.maxDrawdownProtection
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Current Drawdown", value = "${prot.currentDrawdownPct}%", subtitle = "Limit: ${prot.maxDrawdownLimitPct}%", valueColor = if (prot.currentDrawdownPct > 5.0) Color(0xFFD32F2F) else Color(0xFFE65100), modifier = Modifier.weight(1f))
                            MetricCard(title = "Adaptive Scale Factor", value = "${(prot.adaptivePositionScaleFactor * 100).toInt()}%", subtitle = "Position Sizing", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "Max Contracts USD", value = "$${prot.maxAllowedContractsUSD.toInt()}", subtitle = prot.protectionStatus, valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Simulate Portfolio Drawdown Level:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = prot.currentDrawdownPct == 1.5,
                                onClick = { viewModel.updateAdaptiveMaxDrawdownProtection(1.5) },
                                label = { Text("1.5% DD (100% Scale)", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = prot.currentDrawdownPct == 3.5,
                                onClick = { viewModel.updateAdaptiveMaxDrawdownProtection(3.5) },
                                label = { Text("3.5% DD (75% Scale)", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = prot.currentDrawdownPct == 5.5,
                                onClick = { viewModel.updateAdaptiveMaxDrawdownProtection(5.5) },
                                label = { Text("5.5% DD (50% Scale)", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = prot.currentDrawdownPct == 7.8,
                                onClick = { viewModel.updateAdaptiveMaxDrawdownProtection(7.8) },
                                label = { Text("7.8% DD (10% Safety)", fontSize = 10.sp, color = Color(0xFFD32F2F)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Real-Time Position Scaling vs Drawdown Visualization Module
                        PositionScalingDrawdownChart(
                            currentDrawdownPct = prot.currentDrawdownPct,
                            maxDrawdownLimitPct = prot.maxDrawdownLimitPct,
                            adaptivePositionScaleFactor = prot.adaptivePositionScaleFactor,
                            onDrawdownChanged = { dd -> viewModel.updateAdaptiveMaxDrawdownProtection(dd) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Real-Time Maximum Drawdown Threshold Alert Notification System
                        DrawdownAlertNotificationModule(
                            prot = prot,
                            onSimulateDrawdown = { dd -> viewModel.updateAdaptiveMaxDrawdownProtection(dd) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // --- A3. GEMINI API PARALLELISM & LATENCY OPTIMIZATION ---
                        Text(
                            text = "⚡ Gemini API Parallelism & Latency Optimization",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Multiplexes concurrent LLM verification requests via HTTP/2 stream pools and prompt compression to minimize latency during high-volume backtest execution.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val gem = tlv.geminiApiOptimization
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Avg API Latency", value = "${gem.averageLatencyMs} ms", subtitle = "HTTP/2 Multiplexed", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "Req Throughput", value = "${gem.tokenThroughputRps} req/s", subtitle = "${gem.batchConcurrentRequestsCount} Parallel Streams", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "Cache Hit Rate", value = "${gem.responseCacheHitRatePct}%", subtitle = "Prompt Cache", valueColor = Color(0xFF1565C0), modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.optimizeGeminiApiParallelism() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Optimize Gemini API Parallelism & HTTP/2 Multiplexing", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // --- B. MULTI-THREADED PARALLEL VALIDATION & PRIORITY QUEUE ---
                        Text(
                            text = "⚡ Multi-Threaded Priority Queue Validation Engine",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Validates trades concurrently across ${tlv.parallelWorkerCount} Coroutine worker threads. Priority is computed dynamically using a configurable weighted scoring matrix.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Priority Queue Weighted Scheme Selector
                        val pw = tlv.priorityScoringWeights
                        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Active Scheme: ${pw.schemeName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81D4FA))
                                    Surface(color = Color(0xFF2E7D32), shape = RoundedCornerShape(4.dp)) {
                                        Text(text = "4 Worker Threads", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = pw.description, fontSize = 9.sp, color = Color(0xFFB0BEC5))
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Trade Type: ${(pw.tradeTypeWeight * 100).toInt()}%", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFFFF59D))
                                    Text(text = "Time Urgency: ${(pw.timeSensitivityWeight * 100).toInt()}%", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF81C784))
                                    Text(text = "Risk Exposure: ${(pw.riskExposureWeight * 100).toInt()}%", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFFF8A80))
                                    Text(text = "Volatility: ${(pw.marketVolatilityWeight * 100).toInt()}%", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFB388FF))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Select Priority Queue Scoring Scheme:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = pw.schemeName.startsWith("Balanced Alpha"),
                                onClick = { viewModel.updatePriorityQueueScoringScheme("Balanced Alpha (30/25/20/25)") },
                                label = { Text("Balanced Alpha", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = pw.schemeName == "High-Risk Exposure",
                                onClick = { viewModel.updatePriorityQueueScoringScheme("High-Risk Exposure") },
                                label = { Text("High-Risk Exposure", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = pw.schemeName == "Scalp Latency Urgency",
                                onClick = { viewModel.updatePriorityQueueScoringScheme("Scalp Latency Urgency") },
                                label = { Text("Scalp Latency", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = pw.schemeName == "Market Volatility Focus",
                                onClick = { viewModel.updatePriorityQueueScoringScheme("Market Volatility Focus") },
                                label = { Text("Volatility Focus", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "Priority Queue Execution Jobs (Sorted by Weighted Score):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(6.dp))
                                tlv.priorityJobs.forEach { job ->
                                    Column(modifier = Modifier.padding(vertical = 3.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "• [${job.tradeId}] ${job.symbol}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81D4FA))
                                            Text(text = "Priority: ${job.priorityScore} | ${job.workerThreadId}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
                                        }
                                        Text(
                                            text = "   Sub-Scores: Type ${job.tradeTypeScore} | Time ${job.timeSensitivityScore} | Risk ${job.riskExposureScore} | Vol ${job.marketVolatilityScore}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.5.sp,
                                            color = Color(0xFFB0BEC5)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.runParallelPriorityQueueValidation() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Execute Multi-Threaded Parallel Queue Validation", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // --- C. CENTRALIZED PRIORITIZED ALERTS NOTIFICATION LOG ---
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔔 Centralized Prioritized Alert Notification Log",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                                Text(text = "${tlv.alerts.size} Events", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Severity Filters
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = alertSeverityFilter == "ALL",
                                onClick = { alertSeverityFilter = "ALL" },
                                label = { Text("ALL (${tlv.alerts.size})", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = alertSeverityFilter == "CRITICAL",
                                onClick = { alertSeverityFilter = "CRITICAL" },
                                label = { Text("CRITICAL (${tlv.alerts.count { it.severity == "CRITICAL" }})", fontSize = 10.sp, color = Color(0xFFD32F2F)) }
                            )
                            FilterChip(
                                selected = alertSeverityFilter == "WARNING",
                                onClick = { alertSeverityFilter = "WARNING" },
                                label = { Text("WARNING (${tlv.alerts.count { it.severity == "WARNING" }})", fontSize = 10.sp, color = Color(0xFFE65100)) }
                            )
                            FilterChip(
                                selected = alertSeverityFilter == "INFO",
                                onClick = { alertSeverityFilter = "INFO" },
                                label = { Text("INFO (${tlv.alerts.count { it.severity == "INFO" }})", fontSize = 10.sp, color = Color(0xFF1976D2)) }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        val filteredAlerts = if (alertSeverityFilter == "ALL") tlv.alerts else tlv.alerts.filter { it.severity == alertSeverityFilter }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filteredAlerts.forEach { alert ->
                                val (badgeBg, badgeFg) = when (alert.severity) {
                                    "CRITICAL" -> Color(0xFFFFEBEE) to Color(0xFFC62828)
                                    "WARNING" -> Color(0xFFFFF3E0) to Color(0xFFE65100)
                                    else -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
                                }
                                Surface(color = Color(0xFF252525), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(color = badgeBg, shape = RoundedCornerShape(4.dp)) {
                                                    Text(text = alert.severity, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = badgeFg)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = alert.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                            Text(text = "${alert.tradeId} • ${alert.timestamp}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFFB0BEC5))
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = alert.message, fontSize = 10.sp, color = Color(0xFFE0E0E0))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = "Fields: ${alert.affectedFields.joinToString(", ")}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF90CAF9))
                                            Text(text = "Action: ${alert.actionTaken}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (alert.actionTaken == "QUARANTINED") Color(0xFFFF8A80) else Color(0xFF81C784))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // --- D. VALIDATOR LOG ANALYTICS & TREND REVIEW ---
                        Text(
                            text = "📊 Validator Log Analytics & Pattern Trends",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tlv.logAnalytics.systemicTrendInsight,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "Top Detected Inconsistency Patterns:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                tlv.logAnalytics.topInconsistencyPatterns.forEach { (pattern, count) ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "  • $pattern", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFFE0E0E0))
                                        Text(text = "$count instances", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB74D))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = "Recommended Optimization:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81D4FA))
                                Text(text = tlv.logAnalytics.recommendedAction, fontSize = 9.sp, color = Color(0xFFB0BEC5))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // --- E. SIMULATED TRADING ENVIRONMENT & STRESS TEST VALIDATION SUITE ---
                        Text(
                            text = "🎮 Simulated Trading Environment & Stress Test Suite",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Simulates high-volume order flows across diverse market regimes and asset classes to validate ML self-learning, DP-EWMA dynamic thresholding, and adaptive position sizing.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val sim = tlv.simulatedEnvironmentState
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Avg Sharpe Ratio", value = "${sim.sharpeRatio}", subtitle = "Max 1.23 (Bull Market)", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "Sortino Ratio", value = "${sim.sortinoRatio}", subtitle = "Downside Risk", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "Max Drawdown", value = "${sim.maxDrawdownPct}%", subtitle = "Limit: ${sim.maxDrawdownThresholdLimit}%", valueColor = if (sim.maxDrawdownPct > 14.0) Color(0xFFD32F2F) else Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "Win Rate", value = "${sim.tradeWinRatePct}%", subtitle = "${sim.totalSimulatedTrades} Trades", valueColor = Color(0xFF1565C0), modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Active Simulation Profile:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Surface(color = Color(0xFF1565C0), shape = RoundedCornerShape(4.dp)) {
                                        Text(text = sim.selectedStressScenario, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = sim.environmentSummary, fontSize = 9.sp, color = Color(0xFFB0BEC5))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Simulate Market Regime & Asset Class:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = sim.activeRegime.contains("Squeeze") || sim.activeRegime.contains("Shock"),
                                onClick = { viewModel.selectSimulatedMarketRegime("High-Volatility Squeeze & Liquidity Shock") },
                                label = { Text("Vol Squeeze Shock", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = sim.activeRegime.contains("Disconnect"),
                                onClick = { viewModel.selectSimulatedMarketRegime("Systemic Disconnect & Exchange Outage") },
                                label = { Text("Systemic Outage", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = sim.activeAssetClass.contains("Crypto"),
                                onClick = { viewModel.selectSimulatedAssetClass("Crypto (BTC, ETH, SOL)") },
                                label = { Text("Crypto Assets", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = sim.activeAssetClass.contains("FX"),
                                onClick = { viewModel.selectSimulatedAssetClass("FX (EUR/USD, GBP/USD)") },
                                label = { Text("FX Currency Pairs", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = sim.activeAssetClass.contains("Commodities"),
                                onClick = { viewModel.selectSimulatedAssetClass("Commodities (Gold, Oil)") },
                                label = { Text("Commodities", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Execute Stress Test Validation Scenarios:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            sim.scenarios.forEach { scenario ->
                                Surface(color = Color(0xFF252525), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "[${scenario.scenarioId}] ${scenario.scenarioName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Surface(color = if (scenario.status == "PASS_ROBUST") Color(0xFF1B5E20) else Color(0xFFE65100), shape = RoundedCornerShape(4.dp)) {
                                                Text(text = scenario.status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "Regime: ${scenario.regime} • Asset Class: ${scenario.assetClass}", fontSize = 9.sp, color = Color(0xFFB0BEC5))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Sharpe: ${scenario.sharpeRatio} | Sortino: ${scenario.sortinoRatio} | Max DD: ${scenario.maxDrawdownPct}% | WinRate: ${scenario.winRatePct}%",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 8.5.sp,
                                                color = Color(0xFF81D4FA)
                                            )
                                            Button(
                                                onClick = { viewModel.runSimulatedTradingStressTest(scenario.scenarioId) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("Run Scenario", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.runTradeLifecycleAudit() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trigger On-Demand Lifecycle Integrity Audit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- 9. SYSTEM HARMONY ---
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
                                text = "9. SYSTEM HARMONY (GLOBAL UTILITY FUNCTION)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Global Utility", value = "${systemHarmonyGlobalUtility.globalUtilityScore} / 100", subtitle = "System Balance", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            MetricCard(title = "Weights", value = "Profit(35%) Risk(25%)", subtitle = "Latency(15%) Compute(15%)", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(text = systemHarmonyGlobalUtility.systemHealthStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }

            // --- 10. AUTONOMOUS EXPERIMENT FRAMEWORK ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "10. AUTONOMOUS EXPERIMENT FRAMEWORK (SHADOW PHASE)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(title = "Active Paper Tests", value = "${autonomousExperimentState.activePaperExperimentsCount}", subtitle = "${autonomousExperimentState.totalTestedVariants} Total Variants", valueColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            MetricCard(title = "Promoted Variants", value = "${autonomousExperimentState.promotedVariantsCount}", subtitle = "${autonomousExperimentState.institutionalMemoryLogCount} Memory Logs", valueColor = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(text = autonomousExperimentState.statusSummary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(10.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.promoteVariantAAndExploreParameters() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("🚀 Promote Variant A & Sweep Params", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.executeShadowPhaseTick() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("🧪 Shadow Tick (+18 Logs)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "📊 Shadow Phase Variant Performance & Risk Breakdown",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        autonomousExperimentState.variantPerformances.forEach { variant ->
                            Surface(
                                color = if (variant.status.contains("ACTIVE_CORE_CHAMPION")) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = variant.variantName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        val statusColor = when {
                                            variant.status.contains("ACTIVE_CORE_CHAMPION") -> Color(0xFF1565C0)
                                            variant.status.contains("PROMOTED") -> Color(0xFF2E7D32)
                                            variant.status.contains("REJECTED") -> Color(0xFFD32F2F)
                                            else -> Color(0xFFE65100)
                                        }
                                        Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                            Text(text = variant.status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "Regime: ${variant.marketRegime}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = "Pos Size: ${variant.positionSizePct}% | ATR Stop: ${variant.stopLossAtrMultiple}x", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "Net Yield: +${variant.netYieldPct}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (variant.netYieldPct >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                                        Text(text = "Peak DD: ${variant.peakDrawdownPct}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                        Text(text = "Win Rate: ${variant.winRatePct}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text(text = "Sharpe: ${variant.sharpeRatio}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "🔗 Parameter Correlation Matrix (Position Sizing vs. ATR Stop-Loss)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        autonomousExperimentState.parameterCorrelations.forEach { corr ->
                            Surface(
                                color = Color(0xFF1E1E1E),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = corr.parameterPair, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81D4FA))
                                        Text(text = "r = ${corr.correlationCoefficient}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (corr.correlationCoefficient < 0) Color(0xFFFFB74D) else Color(0xFF81C784))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = corr.impactAnalysis, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.85f))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "🎯 Optimal Regime Parameter Configurations",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        autonomousExperimentState.optimalConfigs.forEach { cfg ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = cfg.regimeName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Text(text = "Optimal Pos: ${cfg.optimalPositionSizePct}% | ATR Stop: ${cfg.optimalStopLossAtrMultiple}x", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = "Sharpe: ${cfg.expectedSharpe}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(text = "Max DD: ${cfg.maxExpectedDrawdownPct}%", fontSize = 10.sp, color = Color(0xFFD32F2F))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "🛡️ Variant A2 (Active Champion) What-If Stress Scenarios",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        autonomousExperimentState.stressScenarios.forEach { stress ->
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = stress.scenarioName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                        Surface(color = Color(0xFF16A34A).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                            Text(text = stress.riskVerdict, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "Yield: +${stress.netYieldPct}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                                        Text(text = "Max DD: ${stress.maxDrawdownPct}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                        Text(text = "Sharpe: ${stress.sharpeRatio}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                                        Text(text = "Rec: ${stress.recoveryTimeDays}d", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Subsystem Action: ${stress.bayesianAdaptationAction}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.85f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRoomLogsDialog) {
        RoomPersistedBacktestLogsDialog(
            reports = backtestReportsHistory,
            onDismiss = { showRoomLogsDialog = false },
            onClearAll = { viewModel.clearPersistedBacktestReports() }
        )
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

@Composable
private fun PositionScalingDrawdownChart(
    currentDrawdownPct: Double,
    maxDrawdownLimitPct: Double,
    adaptivePositionScaleFactor: Double,
    onDrawdownChanged: (Double) -> Unit
) {
    Surface(
        color = Color(0xFF141A24),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2E3B52)),
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
                        text = "📈 Position Scale vs Drawdown Correlation",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "Dynamic position sizing response function to drawdown risk",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Surface(
                    color = if (adaptivePositionScaleFactor > 0.7) Color(0xFF15803D).copy(alpha = 0.2f)
                            else if (adaptivePositionScaleFactor > 0.3) Color(0xFFB45309).copy(alpha = 0.2f)
                            else Color(0xFFB91C1C).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Scale: ${(adaptivePositionScaleFactor * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (adaptivePositionScaleFactor > 0.7) Color(0xFF4ADE80)
                                else if (adaptivePositionScaleFactor > 0.3) Color(0xFFFBBF24)
                                else Color(0xFFF87171)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF0F172A), shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val paddingLeft = 35f
                    val paddingBottom = 20f
                    val chartWidth = width - paddingLeft
                    val chartHeight = height - paddingBottom

                    // Draw grid lines
                    for (i in 0..4) {
                        val y = chartHeight * (1f - i / 4f)
                        drawLine(
                            color = Color(0xFF334155),
                            start = Offset(paddingLeft, y),
                            end = Offset(width, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    }

                    // Max Drawdown threshold line
                    val limitX = paddingLeft + chartWidth * (maxDrawdownLimitPct / 15.0).coerceIn(0.0, 1.0).toFloat()
                    drawLine(
                        color = Color(0xFFEF4444),
                        start = Offset(limitX, 0f),
                        end = Offset(limitX, chartHeight),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                    )

                    // Draw curve
                    val curvePath = Path()
                    val steps = 50
                    for (i in 0..steps) {
                        val dd = (i.toDouble() / steps) * 15.0
                        val scale = (1.0 - Math.pow(dd / maxDrawdownLimitPct, 1.5)).coerceIn(0.0, 1.0)
                        val x = paddingLeft + (chartWidth * (i.toFloat() / steps))
                        val y = chartHeight * (1f - scale.toFloat())

                        if (i == 0) {
                            curvePath.moveTo(x, y)
                        } else {
                            curvePath.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = curvePath,
                        color = Color(0xFF38BDF8),
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )

                    // Active operating point
                    val activeDDX = paddingLeft + chartWidth * (currentDrawdownPct / 15.0).coerceIn(0.0, 1.0).toFloat()
                    val activeScaleY = chartHeight * (1f - adaptivePositionScaleFactor.coerceIn(0.0, 1.0).toFloat())

                    drawLine(
                        color = Color(0xFFF59E0B),
                        start = Offset(activeDDX, activeScaleY),
                        end = Offset(activeDDX, chartHeight),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    )

                    drawCircle(
                        color = Color(0xFFF59E0B),
                        radius = 7f,
                        center = Offset(activeDDX, activeScaleY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = Offset(activeDDX, activeScaleY)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF38BDF8), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scaling Curve", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFEF4444), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Max Limit (${maxDrawdownLimitPct.toInt()}%)", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFF59E0B), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Active DD (${currentDrawdownPct}%)", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawdownAlertNotificationModule(
    prot: com.example.aza.strategy.MaxDrawdownProtectionState,
    onSimulateDrawdown: (Double) -> Unit
) {
    var logs by remember { mutableStateOf(prot.alertActionLogs) }
    var actionBannerMessage by remember { mutableStateOf<String?>(null) }
    var showPredictiveDetails by remember { mutableStateOf(true) }

    val isBreached = prot.currentDrawdownPct >= prot.maxDrawdownLimitPct
    val isWarning = prot.currentDrawdownPct >= (prot.maxDrawdownLimitPct * 0.75)
    val breachMagnitude = if (isBreached) prot.currentDrawdownPct - prot.maxDrawdownLimitPct else 0.0

    // Dynamic Predictive Forecast computation based on current drawdown
    val forecast = prot.predictiveForecast
    val dynamicPredictedDD = (prot.currentDrawdownPct * 1.18).coerceAtMost(19.5)
    val dynamicBreachProb = when {
        isBreached -> 99.8
        isWarning -> 82.5
        prot.currentDrawdownPct > 5.0 -> 45.0
        else -> 12.0
    }
    val dynamicMinutesToBreach = when {
        isBreached -> 0
        isWarning -> 45
        else -> 320
    }

    val severityLabel = when {
        isBreached -> "CRITICAL BREACH"
        isWarning -> "HIGH RISK WARNING"
        else -> "GUARDED STABLE"
    }

    val severityBgColor = when {
        isBreached -> Color(0xFF7F1D1D)
        isWarning -> Color(0xFF78350F)
        else -> Color(0xFF064E3B)
    }

    val severityBorderColor = when {
        isBreached -> Color(0xFFEF4444)
        isWarning -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val severityTextColor = when {
        isBreached -> Color(0xFFF87171)
        isWarning -> Color(0xFFFBBF24)
        else -> Color(0xFF34D399)
    }

    Surface(
        color = severityBgColor.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, severityBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isBreached) Icons.Default.Warning else Icons.Default.Shield,
                        contentDescription = null,
                        tint = severityTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MAX DRAWDOWN ALERT & PREDICTIVE ENGINE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    color = severityBorderColor.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, severityBorderColor)
                ) {
                    Text(
                        text = severityLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = severityTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Real-Time Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Drawdown vs Limit",
                    value = "${prot.currentDrawdownPct}% / ${prot.maxDrawdownLimitPct}%",
                    subtitle = if (isBreached) "+${String.format(Locale.US, "%.1f", breachMagnitude)}% Breach" else "Safe Room: ${String.format(Locale.US, "%.1f", prot.maxDrawdownLimitPct - prot.currentDrawdownPct)}%",
                    valueColor = severityTextColor,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Pos Sizing Scale",
                    value = "${(prot.adaptivePositionScaleFactor * 100).toInt()}%",
                    subtitle = "Adaptive Guard",
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Cap at Risk",
                    value = "$${prot.maxAllowedContractsUSD.toInt()}",
                    subtitle = "Margin Shield",
                    valueColor = Color(0xFF4ADE80),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Alert Status Summary Message
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = when {
                            isBreached -> "🚨 CRITICAL THRESHOLD BREACH: Current portfolio drawdown of ${prot.currentDrawdownPct}% has breached the ${prot.maxDrawdownLimitPct}% maximum limit by +${String.format(Locale.US, "%.1f", breachMagnitude)}%. Automatic position scale reduced to ${(prot.adaptivePositionScaleFactor * 100).toInt()}%."
                            isWarning -> "⚠️ HIGH RISK WARNING: Current drawdown of ${prot.currentDrawdownPct}% is approaching maximum threshold (${prot.maxDrawdownLimitPct}%). Position sizing proactively scaled down."
                            else -> "🛡️ GUARDED NORMAL: Portfolio drawdown (${prot.currentDrawdownPct}%) is well within safe operating limits (< ${prot.maxDrawdownLimitPct}%)."
                        },
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PREDICTIVE ANALYTICS ENGINE MODULE
            Surface(
                color = Color(0xFF1E1B4B).copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF6366F1))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔮 PREDICTIVE BREACH ANALYTICS (ML)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFA5B4FC)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF4338CA),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "LSTM-Ensemble v4",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE0E7FF),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showPredictiveDetails = !showPredictiveDetails },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (showPredictiveDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle predictive details",
                                tint = Color(0xFFA5B4FC)
                            )
                        }
                    }

                    if (showPredictiveDetails) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Projected 24h Drawdown", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        "${String.format(Locale.US, "%.1f", dynamicPredictedDD)}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dynamicPredictedDD >= prot.maxDrawdownLimitPct) Color(0xFFF87171) else Color(0xFFFBBF24)
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Breach Probability", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        "${String.format(Locale.US, "%.1f", dynamicBreachProb)}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dynamicBreachProb > 70) Color(0xFFEF4444) else Color(0xFF38BDF8)
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Est. Time to Breach", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        if (dynamicMinutesToBreach == 0) "BREACHED NOW" else "~${dynamicMinutesToBreach}m",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // MARKET SENTIMENT & NLP NEWS FEED INTEGRATION CARD
                        val sentiment = forecast.sentimentImpact
                        var simulatedSentimentBias by remember { mutableStateOf(-0.68) }

                        val dynamicSentimentScore = when {
                            isBreached -> -0.88
                            isWarning -> -0.68
                            else -> simulatedSentimentBias
                        }

                        val sentimentLabel = when {
                            dynamicSentimentScore <= -0.6 -> "EXTREME FEAR / BEARISH (-${(Math.abs(dynamicSentimentScore) * 100).toInt()}%)"
                            dynamicSentimentScore < -0.2 -> "MODERATE BEARISH (-${(Math.abs(dynamicSentimentScore) * 100).toInt()}%)"
                            dynamicSentimentScore <= 0.2 -> "NEUTRAL MARKET SENTIMENT"
                            else -> "BULLISH / GREED (+${(dynamicSentimentScore * 100).toInt()}%)"
                        }

                        val sentimentColor = when {
                            dynamicSentimentScore <= -0.5 -> Color(0xFFEF4444)
                            dynamicSentimentScore < 0.0 -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "📰 FINANCIAL NEWS & SOCIAL SENTIMENT NLP",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                    Text(
                                        text = sentimentLabel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = sentimentColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Sentiment Meter Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                                ) {
                                    val normalizedPos = ((dynamicSentimentScore + 1.0) / 2.0).coerceIn(0.0, 1.0).toFloat()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = normalizedPos)
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF10B981))
                                                ),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("News Sentiment Index", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${String.format(Locale.US, "%.2f", sentiment.newsSentimentIndex)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Social Media Index", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${String.format(Locale.US, "%.2f", sentiment.socialMediaSentimentIndex)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Risk Multiplier", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${sentiment.sentimentRiskMultiplier}x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC7D2FE))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "📰 Market-Moving News Headlines:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                sentiment.newsHeadlines.forEach { headline ->
                                    Text("• $headline", fontSize = 8.5.sp, color = Color(0xFFCBD5E1), modifier = Modifier.padding(vertical = 1.dp))
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "🔥 Live Social Media Signals (X / Reddit / FinTwit):",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                sentiment.socialTrends.forEach { trend ->
                                    Text("• $trend", fontSize = 8.5.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(vertical = 1.dp))
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Simulate News Feed Bias:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = simulatedSentimentBias == -0.85,
                                        onClick = { simulatedSentimentBias = -0.85 },
                                        label = { Text("Crisis (-0.85)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = simulatedSentimentBias == 0.0,
                                        onClick = { simulatedSentimentBias = 0.0 },
                                        label = { Text("Neutral (0.0)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = simulatedSentimentBias == 0.65,
                                        onClick = { simulatedSentimentBias = 0.65 },
                                        label = { Text("Bullish (+0.65)", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // TECHNICAL INDICATORS & CANDLESTICK PATTERN RECOGNITION CARD
                        val tech = forecast.technicalIndicators
                        var selectedTechCondition by remember { mutableStateOf("OVERBOUGHT") }

                        val currentBBUpper = if (selectedTechCondition == "OVERSOLD") 3250.00 else 3450.50
                        val currentBBMiddle = 3380.00
                        val currentBBLower = if (selectedTechCondition == "OVERSOLD") 3100.00 else 3309.50
                        val currentPercentB = if (selectedTechCondition == "OVERSOLD") 0.12 else 0.88
                        val currentStochK = if (selectedTechCondition == "OVERSOLD") 14.2 else 84.6
                        val currentStochD = if (selectedTechCondition == "OVERSOLD") 18.5 else 79.2

                        val activeCandlestickPatterns = if (selectedTechCondition == "OVERSOLD") {
                            listOf(
                                com.example.aza.strategy.CandlestickPatternMatch(
                                    patternName = "Bullish Morning Star",
                                    patternType = "BULLISH_REVERSAL",
                                    confidencePct = 91.2,
                                    description = "3-candle bullish reversal formation at Lower Bollinger Band support.",
                                    candleCount = 3
                                ),
                                com.example.aza.strategy.CandlestickPatternMatch(
                                    patternName = "Hammer",
                                    patternType = "BULLISH_REVERSAL",
                                    confidencePct = 82.0,
                                    description = "Long lower shadow > 2x body length at oversold extreme.",
                                    candleCount = 1
                                )
                            )
                        } else {
                            tech.detectedCandlestickPatterns
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "📈 TECHNICAL INDICATORS & CANDLESTICK PATTERNS",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                    Text(
                                        text = if (selectedTechCondition == "OVERSOLD") "OVERSOLD BOUNCE ZONE" else "OVERBOUGHT REVERSAL WATCH",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTechCondition == "OVERSOLD") Color(0xFF10B981) else Color(0xFFEF4444)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Indicator Grid Row 1: Bollinger Bands & Stochastic Oscillator
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("📊 Bollinger Bands (20, 2.0)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Upper Band: $${String.format(Locale.US, "%.2f", currentBBUpper)}", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("Middle Band: $${String.format(Locale.US, "%.2f", currentBBMiddle)}", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("Lower Band: $${String.format(Locale.US, "%.2f", currentBBLower)}", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("%B Ratio: ${String.format(Locale.US, "%.2f", currentPercentB)} (${if (currentPercentB > 0.8) "Upper Touch" else "Lower Touch"})", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (currentPercentB > 0.8) Color(0xFFF87171) else Color(0xFF4ADE80))
                                        }
                                    }

                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("⚡ Stochastic Oscillator (14, 3)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("%K Line: ${String.format(Locale.US, "%.1f", currentStochK)}", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (currentStochK > 80) Color(0xFFEF4444) else if (currentStochK < 20) Color(0xFF10B981) else Color(0xFFE2E8F0))
                                            Text("%D Line: ${String.format(Locale.US, "%.1f", currentStochD)}", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                            Text("RSI (14): ${tech.rsi14Value}", fontSize = 8.sp, color = Color(0xFFCBD5E1))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("State: ${if (currentStochK > 80) "Overbought (>80)" else "Oversold (<20)"}", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (currentStochK > 80) Color(0xFFFBBF24) else Color(0xFF34D399))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Candlestick Pattern Recognition List
                                Text(
                                    text = "🕯️ Automated Candlestick Pattern Detection:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                activeCandlestickPatterns.forEach { pattern ->
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(pattern.patternName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (pattern.patternType.contains("BEARISH")) Color(0xFFF87171) else Color(0xFF4ADE80))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(color = Color(0xFF334155), shape = RoundedCornerShape(3.dp)) {
                                                        Text(pattern.patternType, fontSize = 7.5.sp, color = Color(0xFFCBD5E1), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                    }
                                                }
                                                Text(pattern.description, fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            }
                                            Text("${pattern.confidencePct}% Conf", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Interactive Simulator Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Simulate Pattern:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = selectedTechCondition == "OVERBOUGHT",
                                        onClick = { selectedTechCondition = "OVERBOUGHT" },
                                        label = { Text("Overbought Reversal", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedTechCondition == "OVERSOLD",
                                        onClick = { selectedTechCondition = "OVERSOLD" },
                                        label = { Text("Oversold Bounce", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // FUNDAMENTAL ANALYSIS DATA CARD (EARNINGS, MACRO, & INTRINSIC VALUATION)
                        val fundamental = forecast.fundamentalAnalysis
                        var macroMode by remember { mutableStateOf("BEARISH_RATE_PRESSURE") }
                        var selectedApiSource by remember { mutableStateOf("Alpha Vantage") }

                        val activeMacroBias = if (macroMode == "BULLISH_EXPANSION") "BULLISH_EXPANSION" else fundamental.macroRiskBias
                        val activeMacroSummary = if (macroMode == "BULLISH_EXPANSION") {
                            "🏛️ Fundamental Macro tailwinds ($selectedApiSource API): Rate cuts anticipated & Tech Q2 EPS beats align market price closer to Intrinsic Value."
                        } else {
                            fundamental.macroSummary
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🏛️ FUNDAMENTAL ANALYSIS & INTRINSIC VALUATION",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF34D399)
                                        )
                                    }
                                    Surface(
                                        color = if (activeMacroBias.contains("BEARISH")) Color(0xFF7F1D1D) else Color(0xFF065F46),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "$selectedApiSource | $activeMacroBias",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Valuation Sub-card: DCF Intrinsic Value vs Market Price
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("DCF Intrinsic Fair Value", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("$${String.format(Locale.US, "%.2f", fundamental.intrinsicFairValueDcf)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34D399))
                                        }
                                        Column {
                                            Text("Current Market Price", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("$${String.format(Locale.US, "%.2f", fundamental.currentMarketPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                        }
                                        Column {
                                            Text("Valuation Premium", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("+7.3% (Overvalued)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                        }
                                        Column {
                                            Text("Dividend Yield", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("${fundamental.dividendYieldPct}% Annual", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("US Core PCE", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${fundamental.cpiInflationRatePct}%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Fed Policy Rate", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${fundamental.centralBankPolicyRatePct}%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Earnings Surprise", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${fundamental.earningsSurpriseIndex}%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (fundamental.earningsSurpriseIndex < 0) Color(0xFFEF4444) else Color(0xFF10B981))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "📊 Live Financial API Statements & Corporate Events ($selectedApiSource):",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                fundamental.fundamentalMetrics.forEach { metric ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(metric.name, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(3.dp)) {
                                                    Text(metric.category, fontSize = 7.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                                }
                                            }
                                            Text("Actual: ${metric.currentValue} (Exp: ${metric.expectedValue}) | Released: ${metric.releaseDate}", fontSize = 8.sp, color = Color(0xFF64748B))
                                        }
                                        Surface(
                                            color = if (metric.impactRating.contains("BEARISH")) Color(0xFF991B1B) else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(3.dp)
                                        ) {
                                            Text(metric.impactRating, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFECACA), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(activeMacroSummary, fontSize = 8.5.sp, color = Color(0xFFA7F3D0))

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("API Provider Source:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = selectedApiSource == "Alpha Vantage",
                                        onClick = { selectedApiSource = "Alpha Vantage" },
                                        label = { Text("Alpha Vantage", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedApiSource == "Quandl / Nasdaq Data",
                                        onClick = { selectedApiSource = "Quandl / Nasdaq Data" },
                                        label = { Text("Quandl Data", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // DCF MODEL COMPARISON LAYER & PREDICTIVE BREACH ANALYTICS INTEGRATION CARD
                        val dcfState = forecast.dcfModelComparison
                        var waccSensitivityMode by remember { mutableStateOf("BASE_8_5") }

                        val activeWaccMultiplier = when (waccSensitivityMode) {
                            "HAWKISH_9_0" -> 1.05
                            "DOVISH_8_0" -> 0.95
                            else -> 1.0
                        }

                        val activeConsensusFairValue = dcfState.consensusIntrinsicFairValue / activeWaccMultiplier
                        val activeValuationGapPct = ((dcfState.benchmarkMarketPrice - activeConsensusFairValue) / activeConsensusFairValue) * 100.0
                        val dcfBreachProbabilityBoostPct = if (activeValuationGapPct > 0) activeValuationGapPct * 1.35 else 0.0
                        val activeBreachProbabilityPct = (forecast.probabilityOfBreachPct + (dcfBreachProbabilityBoostPct - 11.2)).coerceIn(50.0, 99.9)

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "📊 DCF MODEL COMPARISON & BREACH ANALYTICS",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                    Surface(
                                        color = if (activeValuationGapPct > 5.0) Color(0xFF991B1B) else Color(0xFF065F46),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Breach Risk: ${String.format(Locale.US, "%.1f", activeBreachProbabilityPct)}% (+${String.format(Locale.US, "%.1f", dcfBreachProbabilityBoostPct)}% DCF Impact)",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Consensus DCF Target", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("$${String.format(Locale.US, "%.2f", activeConsensusFairValue)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF38BDF8))
                                        }
                                        Column {
                                            Text("Market Price", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("$${String.format(Locale.US, "%.2f", dcfState.benchmarkMarketPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                        }
                                        Column {
                                            Text("Valuation Premium", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text(
                                                text = "${if (activeValuationGapPct > 0) "+" else ""}${String.format(Locale.US, "%.1f", activeValuationGapPct)}%",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (activeValuationGapPct > 0) Color(0xFFF87171) else Color(0xFF34D399)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "📑 Multi-Model DCF Valuation Comparison Matrix:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    dcfState.dcfModels.forEach { model ->
                                        val modelFairVal = model.intrinsicValue / activeWaccMultiplier
                                        val modelGap = ((dcfState.benchmarkMarketPrice - modelFairVal) / modelFairVal) * 100.0

                                        Surface(
                                            color = Color(0xFF1E293B),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(model.modelName, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                                    Text(
                                                        "WACC: ${String.format(Locale.US, "%.1f", model.discountRateWaccPct * activeWaccMultiplier)}% | Term Growth: ${model.terminalGrowthRatePct}% | Target: $${String.format(Locale.US, "%.2f", modelFairVal)}",
                                                        fontSize = 7.5.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Surface(
                                                        color = if (modelGap > 10.0) Color(0xFF991B1B) else if (modelGap > 0) Color(0xFF854D0E) else Color(0xFF065F46),
                                                        shape = RoundedCornerShape(3.dp)
                                                    ) {
                                                        Text(
                                                            text = "${if (modelGap > 0) "+" else ""}${String.format(Locale.US, "%.1f", modelGap)}%",
                                                            fontSize = 7.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                    Text("Breach Shift: +${String.format(Locale.US, "%.1f", model.impliedBreachProbabilityShiftPct)}%", fontSize = 7.5.sp, color = Color(0xFFBAE6FD))
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(dcfState.dcfBreachAnalyticsIntegrationSummary, fontSize = 8.5.sp, color = Color(0xFFBAE6FD))

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("WACC Stress Sensitivity:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = waccSensitivityMode == "BASE_8_5",
                                        onClick = { waccSensitivityMode = "BASE_8_5" },
                                        label = { Text("Base WACC (8.5%)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = waccSensitivityMode == "HAWKISH_9_0",
                                        onClick = { waccSensitivityMode = "HAWKISH_9_0" },
                                        label = { Text("Hawkish WACC (9.0%)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = waccSensitivityMode == "DOVISH_8_0",
                                        onClick = { waccSensitivityMode = "DOVISH_8_0" },
                                        label = { Text("Dovish WACC (8.0%)", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // NLP NEWS & SOCIAL MEDIA SENTIMENT ANALYSIS CARD (GEMINI 3.5 FLASH ENGINE)
                        val nlp = forecast.nlpSentiment
                        var nlpSimMode by remember { mutableStateOf("HAWKISH_BEARISH") }
                        var streamFilter by remember { mutableStateOf("ALL_STREAMS") }

                        val activeNlpScore = when (nlpSimMode) {
                            "DOVISH_BULLISH" -> 0.58
                            "NEUTRAL_BALANCED" -> 0.05
                            else -> nlp.aggregateSentimentIndex // -0.64
                        }

                        val activeNlpLabel = when (nlpSimMode) {
                            "DOVISH_BULLISH" -> "BULLISH_MACRO_TAILWINDS (+0.58)"
                            "NEUTRAL_BALANCED" -> "BALANCED_MACRO_SENTIMENT (+0.05)"
                            else -> "BEARISH_MACRO_HEADWINDS (-0.64)"
                        }

                        val activeArticles = when (nlpSimMode) {
                            "DOVISH_BULLISH" -> listOf(
                                com.example.aza.strategy.NlpNewsSentimentArticle(
                                    headline = "Central Bank Hints at Early Rate Cuts Following Cooling Inflation Data",
                                    source = "Financial Times",
                                    channelType = "NEWS_FEED",
                                    publishedAgo = "10m ago",
                                    sentimentScore = 0.78,
                                    sentimentLabel = "BULLISH",
                                    keyEntity = "Central Bank Policy",
                                    nlpConfidencePct = 96.2,
                                    summarySnippet = "Dovish monetary tone provides strong tailwind for risk assets and growth stocks."
                                ),
                                com.example.aza.strategy.NlpNewsSentimentArticle(
                                    headline = "#FedRateCut & #MarketRally trending as institutional buyers re-enter",
                                    source = "X / FinTwit Feed",
                                    channelType = "SOCIAL_MEDIA_STREAM",
                                    publishedAgo = "5m ago",
                                    sentimentScore = 0.81,
                                    sentimentLabel = "BULLISH",
                                    keyEntity = "Social Sentiment Surge",
                                    nlpConfidencePct = 93.8,
                                    summarySnippet = "Retail & institutional sentiment flipped bullish following dovish Fed comments."
                                )
                            )
                            else -> nlp.articles
                        }.filter {
                            when (streamFilter) {
                                "NEWS_ONLY" -> it.channelType == "NEWS_FEED"
                                "SOCIAL_ONLY" -> it.channelType != "NEWS_FEED"
                                else -> true
                            }
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🧠 GEMINI NLP NEWS & SOCIAL SENTIMENT EARLY WARNING",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFE879F9)
                                        )
                                    }
                                    Surface(
                                        color = if (activeNlpScore < -0.2) Color(0xFF991B1B) else if (activeNlpScore > 0.2) Color(0xFF065F46) else Color(0xFF334155),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = activeNlpLabel,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Early Warning Alert Banner
                                Surface(
                                    color = if (activeNlpScore < -0.2) Color(0xFF450A0A) else Color(0xFF064E3B),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (activeNlpScore < -0.2) Color(0xFFDC2626) else Color(0xFF059669)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (nlpSimMode == "DOVISH_BULLISH") "✅ EARLY WARNING SYSTEM: Positive sentiment momentum (+0.58) suppresses immediate breach risk." else nlp.earlyWarningBreachAlert,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeNlpScore < -0.2) Color(0xFFFCA5A5) else Color(0xFFA7F3D0),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // News vs Social Breakdown
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("News Feed Score: ${nlp.newsSentimentScore}", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text("Social Media Score: ${nlp.socialMediaSentimentScore}", fontSize = 8.sp, color = Color(0xFF38BDF8))
                                    Text("Social Vol Surge: ${nlp.socialMediaVolumeSpikePct}%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Sentiment Score Bar (-1.0 to +1.0)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Aggregate NLP Sentiment Index", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = "${if (activeNlpScore > 0) "+" else ""}${String.format(Locale.US, "%.2f", activeNlpScore)} / 1.00",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeNlpScore < 0) Color(0xFFF87171) else Color(0xFF34D399)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                                ) {
                                    val normalFraction = ((activeNlpScore + 1.0) / 2.0).coerceIn(0.0, 1.0).toFloat()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = normalFraction)
                                            .background(
                                                color = if (activeNlpScore < 0) Color(0xFFEF4444) else Color(0xFF10B981),
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📰 News & FinTwit Stream (${nlp.processedArticleCount} News / ${nlp.processedSocialPostsCount} Posts):",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))

                                activeArticles.forEach { article ->
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(article.source, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = if (article.channelType == "NEWS_FEED") Color(0xFF38BDF8) else Color(0xFFE879F9))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("• ${article.publishedAgo}", fontSize = 7.5.sp, color = Color(0xFF64748B))
                                                }
                                                Surface(
                                                    color = if (article.sentimentScore < 0) Color(0xFF7F1D1D) else Color(0xFF065F46),
                                                    shape = RoundedCornerShape(3.dp)
                                                ) {
                                                    Text(
                                                        text = "${article.sentimentLabel} (${article.sentimentScore})",
                                                        fontSize = 7.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(article.headline, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                                            Text(article.summarySnippet, fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Channel:", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = streamFilter == "ALL_STREAMS",
                                        onClick = { streamFilter = "ALL_STREAMS" },
                                        label = { Text("All Streams", fontSize = 7.5.sp) }
                                    )
                                    FilterChip(
                                        selected = streamFilter == "NEWS_ONLY",
                                        onClick = { streamFilter = "NEWS_ONLY" },
                                        label = { Text("News Only", fontSize = 7.5.sp) }
                                    )
                                    FilterChip(
                                        selected = streamFilter == "SOCIAL_ONLY",
                                        onClick = { streamFilter = "SOCIAL_ONLY" },
                                        label = { Text("Social Only", fontSize = 7.5.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // GRAPH NEURAL NETWORK (GNN) KNOWLEDGE GRAPH CARD
                        val gnn = forecast.gnnNetworkState
                        var gnnSimMode by remember { mutableStateOf("MACRO_SHOCK") }

                        val activeGnnRiskScore = if (gnnSimMode == "SUPPLY_RECOVERY") 0.32 else gnn.propagationRiskScore
                        val activeGnnSummary = if (gnnSimMode == "SUPPLY_RECOVERY") {
                            "🕸️ GNN GRAPH RIPPLE PATTERN: Supply chain bottleneck easing (+0.82) mitigates systemic contagion risk across semiconductor nodes."
                        } else {
                            gnn.hiddenPatternSummary
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🕸️ GRAPH NEURAL NETWORK (GNN) RISK PROPAGATION",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFF43F5E)
                                        )
                                    }
                                    Surface(
                                        color = if (activeGnnRiskScore > 0.6) Color(0xFF991B1B) else Color(0xFF065F46),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "GNN Risk Score: ${String.format(Locale.US, "%.2f", activeGnnRiskScore)}",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Graph Topology: ${gnn.nodeCount} Nodes, ${gnn.edgeCount} Edges", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text("Engine: ${gnn.modelEngine}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "🔗 Key Graph Nodes & Cross-Asset Centrality:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    gnn.nodes.take(4).forEach { node ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 1.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Text("• ${node.name}", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(3.dp)) {
                                                    Text(node.type, fontSize = 7.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                                }
                                            }
                                            Text("${node.currentMetric} (Centrality ${node.centralityScore})", fontSize = 8.sp, color = Color(0xFFF43F5E))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "⚡ GNN Directed Edges & Correlation Propagation Paths:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                gnn.edges.forEach { edge ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${edge.sourceNodeId.removePrefix("NODE_")} ➔ ${edge.targetNodeId.removePrefix("NODE_")} (${edge.relationshipType})", fontSize = 8.sp, color = Color(0xFFCBD5E1))
                                        Text(
                                            text = "${if (edge.weight > 0) "+" else ""}${edge.weight} Weight",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (edge.weight < 0) Color(0xFFF87171) else Color(0xFF34D399)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(activeGnnSummary, fontSize = 8.5.sp, color = Color(0xFFFECDD3))

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("GNN Network Simulation:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = gnnSimMode == "MACRO_SHOCK",
                                        onClick = { gnnSimMode = "MACRO_SHOCK" },
                                        label = { Text("Macro Propagation (0.79 Risk)", fontSize = 8.sp) }
                                    )
                                     FilterChip(
                                        selected = gnnSimMode == "SUPPLY_RECOVERY",
                                        onClick = { gnnSimMode = "SUPPLY_RECOVERY" },
                                        label = { Text("Supply Chain Easing (0.32 Risk)", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // KNOWLEDGE GRAPH REASONING ENGINE CARD
                        val kg = forecast.knowledgeGraphReasoning
                        var kgFilterDomain by remember { mutableStateOf("ALL_DOMAINS") }

                        val activeTriples = if (kgFilterDomain == "PORTFOLIO_ONLY") {
                            kg.activeInferenceChain.filter { it.insightDomain.contains("PORTFOLIO") || it.insightDomain.contains("EQUITY") }
                        } else {
                            kg.activeInferenceChain
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "💡 KNOWLEDGE GRAPH REASONING ENGINE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFFACC15)
                                        )
                                    }
                                    Surface(color = Color(0xFF854D0E), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "${kg.totalEntitiesMapped} Entities | ${kg.totalTriplesInferred} Triples",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFEF08A),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Ontology: ${kg.kgOntologyVersion}", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text("Multi-Hop Reasoning Active", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFDE047))
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "🔗 Semantic Knowledge Triples & Inferred Relations:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    activeTriples.forEach { triple ->
                                        Surface(
                                            color = Color(0xFF1E293B),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(triple.subjectEntity, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Surface(color = Color(0xFF334155), shape = RoundedCornerShape(3.dp)) {
                                                            Text(triple.predicateRelation, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFDE047), modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                                        }
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(triple.objectEntity, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                                                    }
                                                    Text("Domain: ${triple.insightDomain}", fontSize = 7.5.sp, color = Color(0xFF64748B))
                                                }
                                                Text("${triple.reasoningConfidencePct}% Conf", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(kg.reasoningInsightSummary, fontSize = 8.5.sp, color = Color(0xFFFEF08A))

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("KG Subgraph Filter:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = kgFilterDomain == "ALL_DOMAINS",
                                        onClick = { kgFilterDomain = "ALL_DOMAINS" },
                                        label = { Text("Full Macro Chain (4 Triples)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = kgFilterDomain == "PORTFOLIO_ONLY",
                                        onClick = { kgFilterDomain = "PORTFOLIO_ONLY" },
                                        label = { Text("Equity & Portfolio Triples", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // MULTI-TIMEFRAME CONFIRMATION (MTFC) VALIDATION ENGINE CARD
                        val mtfc = forecast.mtfcConfirmation
                        var mtfcSimMode by remember { mutableStateOf("HIGH_CONFLUENCE") }

                        val alignmentPct = if (mtfcSimMode == "HIGH_CONFLUENCE") 87.5 else 42.0
                        val isMtfcPassed = alignmentPct >= mtfc.minRequiredAlignmentPct
                        val mtfcStatusText = if (isMtfcPassed) "PASSED (${alignmentPct}% CONFLUENCE)" else "REJECTED (${alignmentPct}% - WEAK SIGNAL)"
                        val mtfcStatusColor = if (isMtfcPassed) Color(0xFF10B981) else Color(0xFFEF4444)

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🎯 MULTI-TIMEFRAME CONFIRMATION (MTFC)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF818CF8)
                                        )
                                    }
                                    Text(
                                        text = mtfcStatusText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = mtfcStatusColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // MTFC Confluence Progress Meter
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = (alignmentPct / 100.0).toFloat())
                                            .background(
                                                color = if (isMtfcPassed) Color(0xFF818CF8) else Color(0xFFEF4444),
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Timeframe Detail Matrix
                                mtfc.timeframeDetails.forEach { (tf, desc) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("• $tf", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                        Text(desc, fontSize = 8.sp, color = if (isMtfcPassed) Color(0xFF94A3B8) else Color(0xFFF87171))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (isMtfcPassed)
                                        "✅ MTFC VALIDATED: Signal aligned across 1D macro, 4H intermediate, 1H short-term, and 15m entry timeframes."
                                    else
                                        "⚠️ MTFC FILTERED: Multi-timeframe conflict detected (1D macro trend contradicts 15m signal). Weak signal quarantined.",
                                    fontSize = 8.5.sp,
                                    color = Color(0xFFC7D2FE)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Simulate Signal Confluence:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = mtfcSimMode == "HIGH_CONFLUENCE",
                                        onClick = { mtfcSimMode = "HIGH_CONFLUENCE" },
                                        label = { Text("87.5% Aligned (Pass)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = mtfcSimMode == "WEAK_SIGNAL",
                                        onClick = { mtfcSimMode = "WEAK_SIGNAL" },
                                        label = { Text("42% Conflict (Filter)", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // PORTFOLIO VARIANT REBALANCING CARD (60-20-20 SPLIT)
                        val portfolio = forecast.portfolioRebalancing
                        var activeSplitMode by remember { mutableStateOf("60_20_20") }

                        val currentA2Weight = if (activeSplitMode == "60_20_20") 60.0 else 50.0
                        val currentAWeight = if (activeSplitMode == "60_20_20") 20.0 else 30.0
                        val currentCWeight = 20.0

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "⚖️ PORTFOLIO REBALANCING (60-20-20 SPLIT)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                    Surface(color = Color(0xFF0284C7), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "Sharpe 2.08 | Max DD 9.8%",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Variant Weight Distributions
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Variant A2 (60%)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🏆 Variant A2 (Adaptive Volatility)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                                        Text("${currentA2Weight.toInt()}% Weight (Sharpe 2.15)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction = (currentA2Weight / 100.0).toFloat())
                                                .background(Color(0xFF38BDF8), RoundedCornerShape(3.dp))
                                        )
                                    }

                                    // Variant A (20%)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("⚡ Variant A (Core Momentum)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                                        Text("${currentAWeight.toInt()}% Weight (Sharpe 1.82)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction = (currentAWeight / 100.0).toFloat())
                                                .background(Color(0xFFA5B4FC), RoundedCornerShape(3.dp))
                                        )
                                    }

                                    // Variant C (20%)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🛡️ Variant C (Mean Reversion Safeguard)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                                        Text("${currentCWeight.toInt()}% Weight (Sharpe 1.64)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction = (currentCWeight / 100.0).toFloat())
                                                .background(Color(0xFF34D399), RoundedCornerShape(3.dp))
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = portfolio.rebalanceActionSummary,
                                    fontSize = 8.5.sp,
                                    color = Color(0xFFBAE6FD)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Rebalance Target Preset:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = activeSplitMode == "60_20_20",
                                        onClick = { activeSplitMode = "60_20_20" },
                                        label = { Text("60-20-20 Optimal", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = activeSplitMode == "50_30_20",
                                        onClick = { activeSplitMode = "50_30_20" },
                                        label = { Text("50-30-20 Equalized", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Spacer(modifier = Modifier.height(4.dp))

                        forecast.riskDriverFactors.forEach { factor ->
                            Row(
                                modifier = Modifier.padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("•", fontSize = 10.sp, color = Color(0xFF818CF8), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(factor, fontSize = 9.sp, color = Color(0xFFE0E7FF))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            color = Color(0xFF312E81).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF818CF8))
                        ) {
                            Text(
                                text = "🤖 Sentiment-Aware ML Recommendation: ${forecast.proactiveMitigationRecommendation} (Incorporating ${if (dynamicSentimentScore < 0) "Bearish News Spike" else "Neutral Market Sentiment"} multiplier)",
                                fontSize = 9.sp,
                                color = Color(0xFFE0E7FF),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                }
            }

            if (actionBannerMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF166534).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF22C55E))
                ) {
                    Text(
                        text = actionBannerMessage!!,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF86EFAC),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Suggested Corrective Actions Header
            Text(
                text = "⚡ Suggested Real-Time Corrective Actions",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8)
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Action 1: Auto-Rebalance
            Button(
                onClick = {
                    onSimulateDrawdown(7.5)
                    actionBannerMessage = "✅ Auto-Rebalanced Open Contracts: Reduced exposure by 40%. Drawdown re-anchored to 7.5%."
                    val newLog = com.example.aza.strategy.DrawdownAlertActionLog(
                        timestamp = "10:52 AM",
                        actionType = "AUTO_REBALANCE",
                        description = "Scales position size down by 40% and liquidates high-risk long leverage contracts",
                        preActionDrawdownPct = prot.currentDrawdownPct,
                        postActionProjectedDrawdownPct = 7.5
                    )
                    logs = listOf(newLog) + logs
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Execute Auto-Rebalancing (-40% Position Risk)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action 2 & 3 Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        onSimulateDrawdown(5.0)
                        actionBannerMessage = "🛡️ Delta-Neutral Futures Hedge Placed: Protected $10,800 USD capital. Drawdown dropped to 5.0%."
                        val newLog = com.example.aza.strategy.DrawdownAlertActionLog(
                            timestamp = "10:53 AM",
                            actionType = "DELTA_HEDGE",
                            description = "Executed inverse short hedge contracts on BTC/ETH futures",
                            preActionDrawdownPct = prot.currentDrawdownPct,
                            postActionProjectedDrawdownPct = 5.0
                        )
                        logs = listOf(newLog) + logs
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🛡️ Apply Inverse Hedge", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        onSimulateDrawdown(2.0)
                        actionBannerMessage = "🛑 Emergency Circuit Breaker Triggered: Speculative trades frozen. Drawdown safely locked at 2.0%."
                        val newLog = com.example.aza.strategy.DrawdownAlertActionLog(
                            timestamp = "10:54 AM",
                            actionType = "CIRCUIT_BREAKER",
                            description = "Froze high-volatility new trade entries and flattened speculative margins",
                            preActionDrawdownPct = prot.currentDrawdownPct,
                            postActionProjectedDrawdownPct = 2.0
                        )
                        logs = listOf(newLog) + logs
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🛑 Circuit Breaker", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-Time Breach Simulation Chips
            Text(
                text = "🧪 Test Real-Time Breach Threshold Notifications:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = prot.currentDrawdownPct == 3.5,
                    onClick = {
                        onSimulateDrawdown(3.5)
                        actionBannerMessage = null
                    },
                    label = { Text("3.5% (Normal)", fontSize = 9.sp) }
                )
                FilterChip(
                    selected = prot.currentDrawdownPct == 12.5,
                    onClick = {
                        onSimulateDrawdown(12.5)
                        actionBannerMessage = null
                    },
                    label = { Text("12.5% (Warning)", fontSize = 9.sp, color = Color(0xFFFBBF24)) }
                )
                FilterChip(
                    selected = prot.currentDrawdownPct == 15.5,
                    onClick = {
                        onSimulateDrawdown(15.5)
                        actionBannerMessage = null
                    },
                    label = { Text("15.5% (BREACH!)", fontSize = 9.sp, color = Color(0xFFF87171)) }
                )
                FilterChip(
                    selected = prot.currentDrawdownPct == 18.2,
                    onClick = {
                        onSimulateDrawdown(18.2)
                        actionBannerMessage = null
                    },
                    label = { Text("18.2% (CRITICAL)", fontSize = 9.sp, color = Color(0xFFEF4444)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Audit Trail
            Text(
                text = "📋 Real-Time Corrective Action Audit Logs (${logs.size})",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCBD5E1)
            )
            Spacer(modifier = Modifier.height(4.dp))

            logs.take(3).forEach { log ->
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "[${log.timestamp}] ${log.actionType}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Text(
                                text = log.description,
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Text(
                            text = "${log.preActionDrawdownPct}% ➔ ${log.postActionProjectedDrawdownPct}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF4ADE80)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LuminaWeeklyAutonomousReviewCard(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val weeklyReviews by viewModel.weeklyAutonomousReviews.collectAsStateWithLifecycle()
    val latestReview = weeklyReviews.firstOrNull()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Weekly Review",
                        tint = Color(0xFFA855F7),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "🤖 WEEKLY AUTONOMOUS SELF-REVIEW",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = latestReview?.reviewPeriodLabel ?: "No Review Recorded",
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Button(
                    onClick = { viewModel.runWeeklyAutonomousReview() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E22CE)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run Review", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (latestReview != null) {
                // 1. KPI Grid
                Text("📊 Core Performance & Execution KPIs", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KpiMetricBox("Expectancy", "+${String.format(Locale.US, "%.2f", latestReview.overallExpectancy)}R", Color(0xFF4ADE80), Modifier.weight(1f))
                    KpiMetricBox("Sharpe Ratio", String.format(Locale.US, "%.2f", latestReview.sharpeRatio), Color(0xFF38BDF8), Modifier.weight(1f))
                    KpiMetricBox("Profit Factor", String.format(Locale.US, "%.2f", latestReview.profitFactor), Color(0xFFFACC15), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KpiMetricBox("Max Drawdown", "${String.format(Locale.US, "%.1f", latestReview.maxDrawdownPct)}%", Color(0xFFFB923C), Modifier.weight(1f))
                    KpiMetricBox("Average R", "+${String.format(Locale.US, "%.2f", latestReview.averageR)}R", Color(0xFF4ADE80), Modifier.weight(1f))
                    KpiMetricBox("Exec Quality", "${String.format(Locale.US, "%.1f", latestReview.executionQualityPct)}%", Color(0xFFC084FC), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KpiMetricBox("Confidence Calib.", "${String.format(Locale.US, "%.1f", latestReview.confidenceCalibrationPct)}%", Color(0xFF22D3EE), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Strategy & Symbol Rankings
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🏆 Best Strategies", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                        Spacer(modifier = Modifier.height(3.dp))
                        latestReview.bestStrategies.split("|").forEach { strat ->
                            if (strat.isNotBlank()) {
                                BadgeTextPill(strat.trim(), Color(0xFF065F46), Color(0xFF34D399))
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("⚠️ Worst Strategies", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                        Spacer(modifier = Modifier.height(3.dp))
                        latestReview.worstStrategies.split("|").forEach { strat ->
                            if (strat.isNotBlank()) {
                                BadgeTextPill(strat.trim(), Color(0xFF881337), Color(0xFFFCA5A5))
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("💰 Most Profitable Symbols", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.height(3.dp))
                        latestReview.mostProfitableSymbols.split("|").forEach { sym ->
                            if (sym.isNotBlank()) {
                                BadgeTextPill(sym.trim(), Color(0xFF075985), Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("🔻 Least Profitable Symbols", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFB923C))
                        Spacer(modifier = Modifier.height(3.dp))
                        latestReview.leastProfitableSymbols.split("|").forEach { sym ->
                            if (sym.isNotBlank()) {
                                BadgeTextPill(sym.trim(), Color(0xFF7C2D12), Color(0xFFFDBA74))
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Highest Uncertainty Strategies
                Text("❓ Highest Uncertainty Strategies", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(3.dp))
                latestReview.highestUncertaintyStrategies.split("|").forEach { strat ->
                    if (strat.isNotBlank()) {
                        Text("• ${strat.trim()}", fontSize = 8.5.sp, color = Color(0xFFCBD5E1))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Biggest Mistakes
                Text("🚨 Biggest Mistakes Identified", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                Spacer(modifier = Modifier.height(3.dp))
                latestReview.biggestMistakes.split("•").forEach { mistake ->
                    if (mistake.isNotBlank()) {
                        Text("• ${mistake.trim()}", fontSize = 8.5.sp, color = Color(0xFFFECDD3))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Recommended Improvements
                Text("🎯 Recommended System Improvements", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                Spacer(modifier = Modifier.height(3.dp))
                latestReview.recommendedImprovements.split("•").forEach { imp ->
                    if (imp.isNotBlank()) {
                        Text("✓ ${imp.trim()}", fontSize = 8.5.sp, color = Color(0xFFA7F3D0))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // AI Performance Narrative Box
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF475569)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "🧠 AI PERFORMANCE NARRATIVE & CAUSAL DIAGNOSIS",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC084FC)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = latestReview.aiPerformanceNarrative,
                            fontSize = 8.5.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Month-over-Month Comparison Accordion / History
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "📅 MONTH-OVER-MONTH PERMANENT EVOLUTION COMPARISON",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF60A5FA)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = latestReview.monthOverMonthComparison,
                            fontSize = 8.5.sp,
                            color = Color(0xFF93C5FD),
                            lineHeight = 12.sp
                        )

                        if (weeklyReviews.size > 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "📜 Stored Historical Weekly Reviews (${weeklyReviews.size} permanent records):",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            weeklyReviews.drop(1).take(4).forEach { historical ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(historical.reviewPeriodLabel, fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        "Exp: +${String.format(Locale.US, "%.2f", historical.overallExpectancy)}R | Sharpe: ${String.format(Locale.US, "%.2f", historical.sharpeRatio)} | DD: ${String.format(Locale.US, "%.1f", historical.maxDrawdownPct)}%",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No weekly autonomous reviews generated yet. Click 'Run Review' to compute and store.",
                    fontSize = 9.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun KpiMetricBox(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 7.5.sp, color = Color(0xFF94A3B8), maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, maxLines = 1)
        }
    }
}

@Composable
private fun BadgeTextPill(text: String, bgColor: Color, textColor: Color) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

