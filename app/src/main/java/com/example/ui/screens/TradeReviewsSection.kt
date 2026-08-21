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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeReviewsSection(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val rawReviews by viewModel.tradeReviews.collectAsState()
    val allReviews = remember(rawReviews) {
        if (rawReviews.isNotEmpty()) rawReviews else TradeReviewAgent.generateSeedReviews()
    }

    var selectedAuditFilter by remember { mutableStateOf("ALL") }
    var selectedSymbolFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var expandedReviewId by remember { mutableStateOf<String?>(null) }
    var showParadigmExplainer by remember { mutableStateOf(false) }

    val filteredReviews = remember(allReviews, selectedAuditFilter, selectedSymbolFilter, searchQuery) {
        allReviews.filter { review ->
            val matchAuditFilter = when (selectedAuditFilter) {
                "ALL" -> true
                "GOOD_LOSS" -> review.processVsOutcomeType == "GOOD_DECISION_LOSING_TRADE"
                "BAD_WIN" -> review.processVsOutcomeType == "BAD_DECISION_WINNING_TRADE"
                "GOOD_WIN" -> review.processVsOutcomeType == "GOOD_DECISION_WINNING_TRADE"
                "BAD_LOSS" -> review.processVsOutcomeType == "BAD_DECISION_LOSING_TRADE"
                else -> true
            }
            val matchSymbol = selectedSymbolFilter == "ALL" || review.symbol.contains(selectedSymbolFilter, ignoreCase = true)
            val matchQuery = searchQuery.isBlank() ||
                    review.tradeId.contains(searchQuery, ignoreCase = true) ||
                    review.symbol.contains(searchQuery, ignoreCase = true) ||
                    review.rootCause.contains(searchQuery, ignoreCase = true) ||
                    review.auditReasonsList.contains(searchQuery, ignoreCase = true) ||
                    review.decisionQualityLearning.contains(searchQuery, ignoreCase = true) ||
                    review.lessonLearned.contains(searchQuery, ignoreCase = true)

            matchAuditFilter && matchSymbol && matchQuery
        }
    }

    // KPI Metrics calculation
    val totalCount = allReviews.size
    val avgDqsTen = if (totalCount > 0) allReviews.map { it.decisionQualityTen }.average() else 8.4
    val goodDecisionsCount = allReviews.count { it.decisionQualityTen >= 6.5 }
    val luckyWinsCount = allReviews.count { it.processVsOutcomeType == "BAD_DECISION_WINNING_TRADE" }
    val noiseLossesCount = allReviews.count { it.processVsOutcomeType == "GOOD_DECISION_LOSING_TRADE" }

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
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "DECISION QUALITY AUDIT",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Self-Verifying Closed-Trade Evaluation Engine",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                val allAuditsText = buildString {
                                    appendLine("=== LUMINA DECISION QUALITY AUDITS ===")
                                    appendLine("Self-Verifying Engine: Separating Process Quality from PnL Outcome")
                                    appendLine("Average Decision Quality: ${String.format(Locale.US, "%.1f", avgDqsTen)}/10")
                                    appendLine("--------------------------------------------------\n")
                                    allReviews.forEachIndexed { _, rev ->
                                        appendLine("Trade ${rev.tradeId}")
                                        appendLine("Prediction: ${rev.side} ${rev.symbol}")
                                        appendLine("Outcome: ${if (rev.pnlR > 0) "WIN" else "LOSS"} (${if (rev.pnlR > 0) "+" else ""}${String.format(Locale.US, "%.2f", rev.pnlR)}R)")
                                        appendLine("Decision Quality: ${String.format(Locale.US, "%.1f", rev.decisionQualityTen)}/10")
                                        appendLine("Reason:\n${rev.auditReasonsList}")
                                        appendLine("Root Cause: ${rev.rootCause}")
                                        appendLine("Decision-Quality Learning: \"${rev.decisionQualityLearning}\"")
                                        appendLine("Current Learning: \"${rev.currentPnlLearning}\"")
                                        appendLine("--------------------------------------------------")
                                    }
                                }
                                clipboardManager.setText(AnnotatedString(allAuditsText))
                                Toast.makeText(context, "Copied ${allReviews.size} audits to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Audits", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4 Core Decision Quality Audit KPI Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScoreMetricCard(
                            label = "Avg Decision Quality",
                            value = "${String.format(Locale.US, "%.1f", avgDqsTen)}/10",
                            color = Color(0xFF22C55E),
                            modifier = Modifier.weight(1f)
                        )
                        ScoreMetricCard(
                            label = "Noise Losses Shield",
                            value = "$noiseLossesCount Trades",
                            color = Color(0xFF3B82F6),
                            modifier = Modifier.weight(1f)
                        )
                        ScoreMetricCard(
                            label = "Lucky Wins Flagged",
                            value = "$luckyWinsCount Trades",
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f)
                        )
                        ScoreMetricCard(
                            label = "Process Decoupling",
                            value = "ACTIVE",
                            color = Color(0xFFA855F7),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Philosophy Callout
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Why Decision Quality Auditing Matters",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                TextButton(
                                    onClick = { showParadigmExplainer = !showParadigmExplainer },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(
                                        if (showParadigmExplainer) "Hide Details ▲" else "Compare Trade A vs B ▼",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = "A good decision can still lose because of normal market randomness. A bad decision can still win by luck. Lumina audits decision quality to improve the process itself without reinforcing lucky flaws or discarding sound strategies.",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            if (showParadigmExplainer) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Trade A
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = Color(0xFF3B82F6).copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("Trade A (Good Setup, Loss)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF2563EB))
                                            Text("• Excellent setup & regime\n• Lost to unexpected news\n• PnL Learning: \"Strategy lost\"\n• Audit Learning: \"Decision was excellent. Keep taking trades like this.\"", fontSize = 9.5.sp, lineHeight = 13.sp)
                                        }
                                    }

                                    // Trade B
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("Trade B (Wrong Setup, Win)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFFD97706))
                                            Text("• Wrong strategy & regime\n• Won by random spike\n• PnL Learning: \"Strategy won\"\n• Audit Learning: \"Decision quality was poor. Don't repeat this.\"", fontSize = 9.5.sp, lineHeight = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
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
                    placeholder = { Text("Search by Trade #, symbol, reason, root cause...", fontSize = 12.sp) },
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

                // Audit Paradigm Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audit Type:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(
                        "ALL" to "All Audits",
                        "GOOD_LOSS" to "🛡️ Good / Lost (Noise)",
                        "BAD_WIN" to "⚠️ Bad / Won (Lucky)",
                        "GOOD_WIN" to "✅ Good / Won",
                        "BAD_LOSS" to "⛔ Bad / Lost"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedAuditFilter == key,
                            onClick = { selectedAuditFilter = key },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (selectedAuditFilter == key) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                // Symbol Filter Chips
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
                    text = "CLOSED TRADE AUDIT LOGS (${filteredReviews.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Self-Verifying Feedback Loop",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 4. Clean & Structured Decision Quality Audit Cards (Exact User Specification)
        items(filteredReviews, key = { it.tradeId }) { review ->
            DecisionQualityAuditCard(
                review = review,
                isExpanded = expandedReviewId == review.tradeId,
                onToggleExpand = {
                    expandedReviewId = if (expandedReviewId == review.tradeId) null else review.tradeId
                },
                onCopy = {
                    val singleAuditText = buildString {
                        appendLine("Trade ${review.tradeId}\n")
                        appendLine("Prediction:\n${review.side} ${review.symbol}\n")
                        appendLine("Outcome:\n${if (review.pnlR > 0) "WIN" else "LOSS"} (${if (review.pnlR > 0) "+" else ""}${String.format(Locale.US, "%.2f", review.pnlR)}R, ${if (review.pnlR > 0) "+" else ""}$${String.format(Locale.US, "%.2f", review.pnlUsd)})\n")
                        appendLine("Decision Quality:\n${String.format(Locale.US, "%.1f", review.decisionQualityTen)}/10\n")
                        appendLine("Reason:\n${review.auditReasonsList}\n")
                        appendLine("Root Cause:\n${review.rootCause}\n")
                        appendLine("Decision-Quality Learning:\n\"${review.decisionQualityLearning}\"\n")
                        appendLine("Current Learning:\n\"${review.currentPnlLearning}\"")
                    }
                    clipboardManager.setText(AnnotatedString(singleAuditText))
                    Toast.makeText(context, "Copied Trade ${review.tradeId} audit!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun ScoreMetricCard(
    label: String,
    value: String,
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
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DecisionQualityAuditCard(
    review: TradeReviewEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopy: () -> Unit
) {
    val isWin = review.pnlR > 0.0
    val pnlColor = if (isWin) Color(0xFF22C55E) else Color(0xFFEF4444)
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }

    val (badgeBg, badgeFg, badgeText) = when (review.processVsOutcomeType) {
        "GOOD_DECISION_LOSING_TRADE" -> Triple(
            Color(0xFF3B82F6).copy(alpha = 0.15f),
            Color(0xFF2563EB),
            "🛡️ GOOD DECISION / LOSS (MARKET NOISE)"
        )
        "BAD_DECISION_WINNING_TRADE" -> Triple(
            Color(0xFFF59E0B).copy(alpha = 0.15f),
            Color(0xFFD97706),
            "⚠️ BAD DECISION / WIN (LUCKY OUTCOME)"
        )
        "GOOD_DECISION_WINNING_TRADE" -> Triple(
            Color(0xFF22C55E).copy(alpha = 0.15f),
            Color(0xFF16A34A),
            "✅ GOOD DECISION / WIN (REINFORCE EDGE)"
        )
        else -> Triple(
            Color(0xFFEF4444).copy(alpha = 0.15f),
            Color(0xFFDC2626),
            "⛔ BAD DECISION / LOSS (INVALIDATE SETUP)"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (review.decisionQualityTen >= 8.0) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Trade Number, Classification Badge, Copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Trade ${review.tradeId}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Audit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Prediction & Outcome Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Prediction
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "PREDICTION",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${review.side} ${review.symbol}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = if (review.side == "BUY") Color(0xFF22C55E) else Color(0xFFEF4444)
                        )
                    }
                }

                // Outcome
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "OUTCOME",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${if (isWin) "WIN" else "LOSS"} (${if (isWin) "+" else ""}${String.format(Locale.US, "%.2f", review.pnlR)}R)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = pnlColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Decision Quality Score (0 - 10)
            Surface(
                color = when {
                    review.decisionQualityTen >= 8.0 -> Color(0xFF22C55E).copy(alpha = 0.12f)
                    review.decisionQualityTen >= 6.0 -> Color(0xFF3B82F6).copy(alpha = 0.12f)
                    else -> Color(0xFFEF4444).copy(alpha = 0.12f)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DECISION QUALITY:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", review.decisionQualityTen)}/10",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = when {
                            review.decisionQualityTen >= 8.0 -> Color(0xFF16A34A)
                            review.decisionQualityTen >= 6.0 -> Color(0xFF2563EB)
                            else -> Color(0xFFDC2626)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reasons Checklist (Exact User Format)
            Text(
                text = "Reason:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val lines = review.auditReasonsList.lines().filter { it.isNotBlank() }
                    lines.forEach { line ->
                        val isCheck = line.startsWith("✓")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isCheck) "✓" else "✗",
                                color = if (isCheck) Color(0xFF22C55E) else Color(0xFFEF4444),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                modifier = Modifier.width(18.dp)
                            )
                            Text(
                                text = line.removePrefix("✓").removePrefix("✗").trim(),
                                fontSize = 11.5.sp,
                                fontWeight = if (isCheck) FontWeight.SemiBold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Root Cause Tag
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Root Cause: ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = review.rootCause,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Feedback Loop Decoupling Box (Decision-Quality Learning vs Current Learning)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Decision-Quality Learning:",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "\"${review.decisionQualityLearning}\"",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Standard PnL learning would say: \"${review.currentPnlLearning}\"",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "SELF-VERIFIED",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Expanded Details (Strategy, Timing, Regime, Sizing)
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
                        text = "Regime: ${review.regimeName}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Continuous Policy: ${review.actionablePolicy}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
