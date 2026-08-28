package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aza.strategy.MicroCompoundingMode
import com.example.viewmodel.AppViewModel
import java.util.Locale

@Composable
fun MicroCompoundingStudyCard(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val compoundingState by viewModel.microCompoundingState.collectAsState()
    val tradeStudy by viewModel.tradeStudySummary.collectAsState()
    val preBuyReports by viewModel.preBuyViabilityReports.collectAsState()
    val allocationPlan by viewModel.capitalAllocationPlan.collectAsState()

    var showPreBuyAuditor by remember { mutableStateOf(true) }
    var showTradeStudyDetails by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Micro-Compounding Engine Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "MICRO-COMPOUNDING & TRADE STUDY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Even a 1-cent win is a good trade • Compounding small accounts",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Badge(
                    containerColor = Color(0xFF2E7D32).copy(alpha = 0.15f),
                    contentColor = Color(0xFF2E7D32)
                ) {
                    Text(
                        text = "STREAK: ${compoundingState.currentWinningStreak} WINS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compounding Mode Switcher Chips
            Text(
                text = "EXECUTION MODE (VELOCITY VS PROTECTION)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MicroCompoundingMode.values().forEach { mode ->
                    val isSelected = compoundingState.mode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setMicroCompoundingMode(mode) },
                        label = {
                            Text(
                                text = when (mode) {
                                    MicroCompoundingMode.AGGRESSIVE_MICRO_HARVEST -> "⚡ 1¢-3¢ Rapid"
                                    MicroCompoundingMode.BALANCED_COMPOUNDER -> "⚖️ 3¢-8¢ Balanced"
                                    MicroCompoundingMode.CONSERVATIVE_RUNNER -> "🛡️ Steady Growth"
                                },
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Compounding Pennies & Stats Dashboard
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Compounded", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "+$${String.format(Locale.US, "%.3f", tradeStudy.totalNetPenniesEarnedUsd)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
                Column {
                    Text("Win Rate", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${String.format(Locale.US, "%.1f", tradeStudy.winRatePct)}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text("Defense Stop", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${String.format(Locale.US, "%.2f", tradeStudy.recommendedStopMultiplier)}x ATR",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                }
                Column {
                    Text("Loss Shield", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${String.format(Locale.US, "%.1f", tradeStudy.lossMitigationEffectivenessScore)}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 1: Pre-Buy Exit Viability Auditor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPreBuyAuditor = !showPreBuyAuditor },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCartCheckout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "PRE-BUY EXIT VIABILITY AUDITOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (showPreBuyAuditor) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showPreBuyAuditor) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Rule: Before buying, verify exit order compliance, fees coverage, and 1¢ net win hurdles.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (preBuyReports.isNotEmpty()) {
                        preBuyReports.values.take(3).forEach { report ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (report.isSellableApproved)
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    else Color(0xFFFFEBEE)
                                ),
                                border = BorderStroke(
                                    0.75.dp,
                                    if (report.isSellableApproved) Color(0xFF2E7D32).copy(alpha = 0.4f) else Color(0xFFC62828).copy(alpha = 0.4f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = report.symbol,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = if (report.isSellableApproved) "✓ SELLABLE APPROVED" else "❌ EXIT BLOCKED",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                            color = if (report.isSellableApproved) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Breakeven: $${String.format(Locale.US, "%.2f", report.breakevenHurdlePrice)}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "+1¢ Net Win: $${String.format(Locale.US, "%.2f", report.oneCentNetWinPrice)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = "+5¢ Win: $${String.format(Locale.US, "%.2f", report.fiveCentNetWinPrice)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = report.executionGuidance,
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Awaiting trade candidates. Tap Refresh to scan Binance Spot markets.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Section 2: Lumina Dynamic Trade Study & Adaptive Self-Tuning
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTradeStudyDetails = !showTradeStudyDetails },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "DYNAMIC TRADE STUDY & ADAPTATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (showTradeStudyDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showTradeStudyDetails) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Lumina studies past trade excursions (MFE/MAE) and dynamically adapts ATR stop tightness to mitigate large losses while compounding micro returns.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    tradeStudy.activeLearnedRules.forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("• ", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(rule, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Recent Studied Trades List
                    if (tradeStudy.recentStudies.isNotEmpty()) {
                        Text(
                            text = "RECENT STUDIED TRADES (MFE & MAE CAPTURE)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        tradeStudy.recentStudies.take(3).forEach { study ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${study.symbol} (${study.strategyName})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    )
                                    Text(
                                        text = "${study.exitType} • Held ${String.format(Locale.US, "%.1f", study.holdingDurationMinutes)}m",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${if (study.netPnL >= 0) "+" else ""}$${String.format(Locale.US, "%.3f", study.netPnL)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (study.netPnL >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                    Text(
                                        text = "Capture: ${String.format(Locale.US, "%.0f", study.profitCaptureEfficiencyPct)}%",
                                        fontSize = 8.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action: Trigger Post-Trade Study & Recompute
            OutlinedButton(
                onClick = { viewModel.triggerDeepTradeStudy() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Run Deep Trade Study & Re-calibrate", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
