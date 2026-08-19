package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aza.strategy.TradeReviewAgent
import com.example.data.database.TradeReviewEntity
import com.example.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeReviewsSection(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val rawReviews by viewModel.tradeReviews.collectAsState()
    val allReviews = remember(rawReviews) {
        if (rawReviews.isNotEmpty()) rawReviews else TradeReviewAgent.generateSeedReviews()
    }

    var selectedSymbolFilter by remember { mutableStateOf("ALL") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var expandedReviewId by remember { mutableStateOf<String?>(null) }

    val filteredReviews = remember(allReviews, selectedSymbolFilter, selectedCategoryFilter, searchQuery) {
        allReviews.filter { review ->
            val matchSymbol = selectedSymbolFilter == "ALL" || review.symbol.equals(selectedSymbolFilter, ignoreCase = true)
            val matchCategory = selectedCategoryFilter == "ALL" || review.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchQuery = searchQuery.isBlank() ||
                    review.lessonLearned.contains(searchQuery, ignoreCase = true) ||
                    review.missedOpportunity.contains(searchQuery, ignoreCase = true) ||
                    review.actionablePolicy.contains(searchQuery, ignoreCase = true) ||
                    review.symbol.contains(searchQuery, ignoreCase = true)
            matchSymbol && matchCategory && matchQuery
        }
    }

    // KPI Metrics calculation
    val totalCount = allReviews.size
    val avgDecisionQuality = if (totalCount > 0) allReviews.map { it.decisionQuality }.average() else 92.0
    val avgEntryTiming = if (totalCount > 0) allReviews.map { it.entryTimingQuality }.average() else 85.0
    val avgExitQuality = if (totalCount > 0) allReviews.map { it.exitQuality }.average() else 74.0
    val avgRiskSizing = if (totalCount > 0) allReviews.map { it.riskSizingQuality }.average() else 96.0
    val correctRegimesCount = allReviews.count { it.regimeSelection.equals("Correct", ignoreCase = true) }
    val regimeAccuracyPct = if (totalCount > 0) (correctRegimesCount.toDouble() / totalCount) * 100.0 else 85.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Agent Title & Overview Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
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
                                    .size(38.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "TRADE REVIEW AGENT",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Continuous Improvement & Structured Lessons Library",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                val lessonsText = buildString {
                                    appendLine("=== LUMINA STRUCTURED TRADE LESSONS LIBRARY ===")
                                    appendLine("Total Evaluated Trades: ${allReviews.size}")
                                    appendLine("Avg Decision Quality: ${String.format(Locale.US, "%.1f", avgDecisionQuality)}/100")
                                    appendLine("Avg Entry Timing: ${String.format(Locale.US, "%.1f", avgEntryTiming)}/100")
                                    appendLine("Avg Exit Quality: ${String.format(Locale.US, "%.1f", avgExitQuality)}/100")
                                    appendLine("Avg Risk Sizing: ${String.format(Locale.US, "%.1f", avgRiskSizing)}/100")
                                    appendLine("Regime Accuracy: ${String.format(Locale.US, "%.1f", regimeAccuracyPct)}%")
                                    appendLine("--------------------------------------------------\n")
                                    allReviews.forEachIndexed { i, rev ->
                                        appendLine("[${i + 1}] ${rev.symbol} ${rev.side} (${rev.strategyName}) - Trade ID: ${rev.tradeId}")
                                        appendLine(" • Decision quality: ${rev.decisionQuality}/100")
                                        appendLine(" • Entry timing: ${rev.entryTimingQuality}/100")
                                        appendLine(" • Exit quality: ${rev.exitQuality}/100")
                                        appendLine(" • Risk sizing: ${rev.riskSizingQuality}/100")
                                        appendLine(" • Regime selection: ${rev.regimeSelection} (${rev.regimeName})")
                                        appendLine(" • Confidence calibration: ${rev.confidenceCalibration} (${String.format(Locale.US, "%.0f%%", rev.confidenceScore * 100)})")
                                        appendLine(" • Missed opportunity: ${rev.missedOpportunity}")
                                        appendLine(" • Lesson learned: \"${rev.lessonLearned}\"")
                                        appendLine(" • Actionable policy: ${rev.actionablePolicy}")
                                        appendLine("")
                                    }
                                }
                                clipboardManager.setText(AnnotatedString(lessonsText))
                                Toast.makeText(context, "Copied ${allReviews.size} lessons to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Lessons", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4 Core Quality Metrics Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScoreMetricCard(
                            label = "Decision Quality",
                            score = avgDecisionQuality.toInt(),
                            color = Color(0xFF22C55E),
                            modifier = Modifier.weight(1f)
                        )
                        ScoreMetricCard(
                            label = "Entry Timing",
                            score = avgEntryTiming.toInt(),
                            color = Color(0xFF3B82F6),
                            modifier = Modifier.weight(1f)
                        )
                        ScoreMetricCard(
                            label = "Exit Quality",
                            score = avgExitQuality.toInt(),
                            color = Color(0xFFA855F7),
                            modifier = Modifier.weight(1f)
                        )
                        ScoreMetricCard(
                            label = "Risk Sizing",
                            score = avgRiskSizing.toInt(),
                            color = Color(0xFFEAB308),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Accumulated Wisdom: ${allReviews.size} Policy Lessons",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Regime Selection Accuracy: ${String.format(Locale.US, "%.1f", regimeAccuracyPct)}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 2. Search & Filter Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search lessons, rules, missed opportunities...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Symbol and Category Filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Symbol:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("ALL", "BTC", "ETH", "SOL", "BNB").forEach { sym ->
                        FilterChip(
                            selected = selectedSymbolFilter == sym,
                            onClick = { selectedSymbolFilter = sym },
                            label = { Text(sym, fontSize = 11.sp, fontWeight = if (selectedSymbolFilter == sym) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Category:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("ALL", "ENTRY_TIMING", "EXIT_DISCIPLINE", "RISK_SIZING", "POSITION_MANAGEMENT", "REGIME_FILTER").forEach { cat ->
                        val label = cat.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // 3. Trade Reviews Header Count
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STRUCTURED TRADE REVIEWS (${filteredReviews.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Sorted by Most Recent",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // 4. Detailed Structured Review Cards
        items(filteredReviews, key = { it.tradeId }) { review ->
            DetailedTradeReviewCard(
                review = review,
                isExpanded = expandedReviewId == review.tradeId,
                onToggleExpand = {
                    expandedReviewId = if (expandedReviewId == review.tradeId) null else review.tradeId
                },
                onCopy = {
                    val singleReviewText = buildString {
                        appendLine("=== TRADE REVIEW: ${review.symbol} ${review.side} (Trade ID: ${review.tradeId}) ===")
                        appendLine("Decision quality: ${review.decisionQuality}/100")
                        appendLine("Entry timing: ${review.entryTimingQuality}/100")
                        appendLine("Exit quality: ${review.exitQuality}/100")
                        appendLine("Risk sizing: ${review.riskSizingQuality}/100")
                        appendLine("Regime selection: ${review.regimeSelection}")
                        appendLine("Confidence calibration: ${review.confidenceCalibration}")
                        appendLine("Missed opportunity: ${review.missedOpportunity}")
                        appendLine("Lesson learned: \"${review.lessonLearned}\"")
                        appendLine("Actionable Policy: ${review.actionablePolicy}")
                    }
                    clipboardManager.setText(AnnotatedString(singleReviewText))
                    Toast.makeText(context, "Copied review for ${review.symbol}!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun ScoreMetricCard(
    label: String,
    score: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$score/100",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.5.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DetailedTradeReviewCard(
    review: TradeReviewEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopy: () -> Unit
) {
    val isWin = review.pnlR > 0.0
    val pnlColor = if (isWin) Color(0xFF22C55E) else Color(0xFFEF4444)
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (review.overallScore >= 90) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Symbol, Side, PnL, Decision Grade, Copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (review.side == "BUY") Color(0xFF22C55E).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${review.symbol} ${review.side}",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = if (review.side == "BUY") Color(0xFF22C55E) else Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${if (isWin) "+" else ""}${String.format(Locale.US, "%.2f", review.pnlR)}R (${if (isWin) "+" else ""}$${String.format(Locale.US, "%.2f", review.pnlUsd)})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = pnlColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when {
                            review.overallScore >= 90 -> MaterialTheme.colorScheme.primaryContainer
                            review.overallScore >= 80 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Score: ${review.overallScore}/100",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (review.overallScore >= 90) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Review",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Dimension Review Breakdown in exact user format
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ReviewScoreRow(label = "Decision quality", score = review.decisionQuality, max = 100)
                    ReviewScoreRow(label = "Entry timing", score = review.entryTimingQuality, max = 100)
                    ReviewScoreRow(label = "Exit quality", score = review.exitQuality, max = 100)
                    ReviewScoreRow(label = "Risk sizing", score = review.riskSizingQuality, max = 100)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Qualitative Evaluation Tags
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                QualitativeRow(
                    label = "Regime selection",
                    value = review.regimeSelection,
                    statusColor = if (review.regimeSelection.equals("Correct", ignoreCase = true)) Color(0xFF22C55E) else Color(0xFFEAB308),
                    subtext = "(${review.regimeName})"
                )
                QualitativeRow(
                    label = "Confidence calibration",
                    value = review.confidenceCalibration,
                    statusColor = when {
                        review.confidenceCalibration.contains("Accurate", ignoreCase = true) -> Color(0xFF22C55E)
                        review.confidenceCalibration.contains("Slightly", ignoreCase = true) -> Color(0xFF3B82F6)
                        else -> Color(0xFFEAB308)
                    },
                    subtext = "(${String.format(Locale.US, "%.0f%%", review.confidenceScore * 100)})"
                )
                QualitativeRow(
                    label = "Missed opportunity",
                    value = review.missedOpportunity,
                    statusColor = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Highlighted Lesson Learned Box
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lesson learned:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${review.lessonLearned}\"",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Policy Rule: ${review.actionablePolicy}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "LUMINA LEARNED",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Expanded trade specifics
            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Strategy: ${review.strategyName} • Duration: ${review.holdingDurationMinutes.toInt()}m",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(Date(review.timestamp)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Entry: $${String.format(Locale.US, "%,.2f", review.entryPrice)} • Exit: $${String.format(Locale.US, "%,.2f", review.exitPrice)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Trade ID: ${review.tradeId}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewScoreRow(
    label: String,
    score: Int,
    max: Int = 100
) {
    val fraction = (score.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    val color = when {
        score >= 90 -> Color(0xFF22C55E)
        score >= 75 -> Color(0xFF3B82F6)
        score >= 60 -> Color(0xFFEAB308)
        else -> Color(0xFFEF4444)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1.2f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.weight(2f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .width(90.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$score/$max",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.width(48.dp)
            )
        }
    }
}

@Composable
private fun QualitativeRow(
    label: String,
    value: String,
    statusColor: Color,
    subtext: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            if (subtext != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = subtext,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
