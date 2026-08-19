package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CounterfactualEvaluationEntity
import com.example.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CounterfactualLearningSection(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val evaluations by viewModel.counterfactualEvaluations.collectAsState()
    val summary by viewModel.counterfactualSummary.collectAsState()

    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedTradeId by remember { mutableStateOf<String?>(null) }

    val filteredEvaluations = remember(evaluations, selectedFilter) {
        when (selectedFilter) {
            "SKIPPED" -> evaluations.filter { it.bestAlternativeScenario == "SKIPPED" || it.skippedOutcome == "AVOIDED_LOSS" }
            "DELAYED" -> evaluations.filter { it.bestAlternativeScenario == "DELAYED_1_CANDLE" || it.delayed1CandleOutcome == "BETTER_ENTRY" }
            "LOWER_RISK" -> evaluations.filter { it.bestAlternativeScenario == "LOWER_RISK" || it.lowerRiskOutcome == "REDUCED_DRAWDOWN" }
            "EARLIER_TP1" -> evaluations.filter { it.bestAlternativeScenario == "EARLIER_TP1" || it.earlierTp1Outcome == "CAPTURED_REVERSAL_GAIN" }
            "WIDER_SL" -> evaluations.filter { it.bestAlternativeScenario == "WIDER_SL" || it.widerSlOutcome == "SURVIVED_NOISE_WON" }
            "ALT_STRATEGY" -> evaluations.filter { it.bestAlternativeScenario == "ALT_STRATEGY" || it.altStrategyOutcome == "BETTER_REGIME_FIT" }
            "OPTIMAL" -> evaluations.filter { it.wasExecutedOptimal }
            else -> evaluations
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Hero Header & Action
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = "Counterfactual Learning",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "COUNTERFACTUAL SHADOW LAB",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Learning from Plausible Alternatives (What-If Simulations)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.batchSimulateAllCounterfactuals() },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-simulate Shadow Evaluations",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "For every trade, Lumina continuously asks: 'If I had made a different decision, would the outcome have been better?' Simulating skipped trades, candle delays, risk adjustments, earlier profit-taking, wider stops, and alternative strategies builds decision wisdom without risking additional capital.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Key Metric KPI Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadowMetricCard(
                    title = "Shadow Trades",
                    value = "${summary.totalEvaluatedTrades}",
                    subtitle = "Simulated",
                    icon = Icons.Default.Science,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                ShadowMetricCard(
                    title = "Avoided Loss Alpha",
                    value = "+$${String.format(Locale.US, "%.0f", summary.avoidedLossesUSD)}",
                    subtitle = "Capital Preserved",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadowMetricCard(
                    title = "Optimal Rate",
                    value = "${String.format(Locale.US, "%.1f", summary.executedOptimalRatePct)}%",
                    subtitle = "Best Decision",
                    icon = Icons.Default.CheckCircle,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )
                ShadowMetricCard(
                    title = "Average Regret",
                    value = "$${String.format(Locale.US, "%.1f", summary.averageRegretScoreUSD)}",
                    subtitle = "Per Trade Delta",
                    icon = Icons.Default.Balance,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Top Policy Recommendations derived from Counterfactuals
        if (summary.topPolicyLessons.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SYNTHESIZED COUNTERFACTUAL POLICY LESSONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        summary.topPolicyLessons.forEach { lesson ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "• ",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = lesson,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Filter Chips Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "EXPLORE COUNTERFACTUAL SCENARIOS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" },
                            label = { Text("All Trades (${evaluations.size})", fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "SKIPPED",
                            onClick = { selectedFilter = "SKIPPED" },
                            label = { Text("What if Skipped?", fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "DELAYED",
                            onClick = { selectedFilter = "DELAYED" },
                            label = { Text("1 Candle Later", fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "LOWER_RISK",
                            onClick = { selectedFilter = "LOWER_RISK" },
                            label = { Text("0.5% vs 1.5% Risk", fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "EARLIER_TP1",
                            onClick = { selectedFilter = "EARLIER_TP1" },
                            label = { Text("Earlier TP1", fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "WIDER_SL",
                            onClick = { selectedFilter = "WIDER_SL" },
                            label = { Text("Wider Stop (+0.5 ATR)", fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "ALT_STRATEGY",
                            onClick = { selectedFilter = "ALT_STRATEGY" },
                            label = { Text("Alt Strategy", fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "OPTIMAL",
                            onClick = { selectedFilter = "OPTIMAL" },
                            label = { Text("Optimal Trades", fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Evaluations List
        if (filteredEvaluations.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No counterfactual evaluations matching filter.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(filteredEvaluations) { eval ->
                val isExpanded = selectedTradeId == eval.tradeId
                CounterfactualTradeCard(
                    evaluation = eval,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        selectedTradeId = if (isExpanded) null else eval.tradeId
                    }
                )
            }
        }
    }
}

@Composable
private fun ShadowMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = tint
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CounterfactualTradeCard(
    evaluation: CounterfactualEvaluationEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val isWin = evaluation.executedPnL > 0
    val pnlColor = if (isWin) Color(0xFF4ADE80) else if (evaluation.executedPnL < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Symbol, Side, Date, Executed PnL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (evaluation.executedSide == "BUY") Color(0xFF22C55E).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = evaluation.executedSide,
                            color = if (evaluation.executedSide == "BUY") Color(0xFF22C55E) else Color(0xFFEF4444),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = evaluation.symbol,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = evaluation.marketRegime,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (isWin) "+" else "") + "$${String.format(Locale.US, "%.2f", evaluation.executedPnL)}",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = pnlColor
                    )
                    Text(
                        text = dateFormat.format(Date(evaluation.timestamp)),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Strategy & Best Alternative Summary Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Strategy: ${evaluation.strategyName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (evaluation.wasExecutedOptimal) {
                    Surface(
                        color = Color(0xFF22C55E).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Optimal Decision", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Insights, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Best: ${formatScenarioName(evaluation.bestAlternativeScenario)} (+$${String.format(Locale.US, "%.0f", evaluation.regretScore)})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }

            // Expanded: 6 What-If Counterfactual Scenarios Comparison
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = "WHAT-IF COUNTERFACTUAL SCENARIOS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // 1. What if Skipped?
                    ScenarioRow(
                        title = "1. What if we skipped it?",
                        deltaPnL = evaluation.skippedDeltaPnL,
                        outcome = evaluation.skippedOutcome,
                        reasoning = evaluation.skippedReasoning,
                        icon = Icons.Default.Block
                    )

                    // 2. What if entered 1 candle later?
                    ScenarioRow(
                        title = "2. What if entered 1 candle later?",
                        deltaPnL = evaluation.delayed1CandleDeltaPnL,
                        outcome = evaluation.delayed1CandleOutcome,
                        reasoning = evaluation.delayed1CandleReasoning,
                        icon = Icons.Default.Update
                    )

                    // 3. What if risk were lower (0.5% vs 1.5%)?
                    ScenarioRow(
                        title = "3. What if risk were lower (0.5%)?",
                        deltaPnL = evaluation.lowerRiskDeltaPnL,
                        outcome = evaluation.lowerRiskOutcome,
                        reasoning = evaluation.lowerRiskReasoning,
                        icon = Icons.Default.Shield
                    )

                    // 4. What if TP1 were earlier?
                    ScenarioRow(
                        title = "4. What if TP1 were earlier (1.0R)?",
                        deltaPnL = evaluation.earlierTp1DeltaPnL,
                        outcome = evaluation.earlierTp1Outcome,
                        reasoning = evaluation.earlierTp1Reasoning,
                        icon = Icons.Default.Savings
                    )

                    // 5. What if stop-loss were wider (+0.5 ATR)?
                    ScenarioRow(
                        title = "5. What if stop-loss were wider (+0.5 ATR)?",
                        deltaPnL = evaluation.widerSlDeltaPnL,
                        outcome = evaluation.widerSlOutcome,
                        reasoning = evaluation.widerSlReasoning,
                        icon = Icons.Default.Tune
                    )

                    // 6. What if another strategy had been selected?
                    ScenarioRow(
                        title = "6. What if selected '${evaluation.altStrategyName}'?",
                        deltaPnL = evaluation.altStrategyDeltaPnL,
                        outcome = evaluation.altStrategyOutcome,
                        reasoning = evaluation.altStrategyReasoning,
                        icon = Icons.Default.SwapCalls
                    )

                    // AI Policy Synthesis Card
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "LUMINA SHADOW LESSON",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = evaluation.aiPolicyLesson,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioRow(
    title: String,
    deltaPnL: Double,
    outcome: String,
    reasoning: String,
    icon: ImageVector
) {
    val isPositive = deltaPnL > 0
    val deltaColor = if (isPositive) Color(0xFF4ADE80) else if (deltaPnL < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                Surface(
                    color = deltaColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = (if (isPositive) "+" else "") + "$${String.format(Locale.US, "%.2f", deltaPnL)} delta",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = deltaColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = reasoning,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatScenarioName(key: String): String {
    return when (key) {
        "SKIPPED" -> "Skipped"
        "DELAYED_1_CANDLE" -> "1 Candle Later"
        "LOWER_RISK" -> "0.5% Risk"
        "EARLIER_TP1" -> "Earlier TP1"
        "WIDER_SL" -> "Wider Stop (+0.5 ATR)"
        "ALT_STRATEGY" -> "Alt Strategy"
        "EXECUTED_OPTIMAL" -> "Executed Optimal"
        else -> key
    }
}
