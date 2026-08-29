package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.example.aza.strategy.ScalpingCyclePhase
import com.example.aza.strategy.SmallAccountScalpConfig
import com.example.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LuminaSmallAccountScalperCard(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val isLoopActive by viewModel.isScalpLoopActive.collectAsState()
    val currentPhase by viewModel.scalpCurrentPhase.collectAsState()
    val activeSymbol by viewModel.scalpCurrentActiveSymbol.collectAsState()
    val config by viewModel.smallAccountScalpConfig.collectAsState()
    val summary by viewModel.scalpPerformanceSummary.collectAsState()
    val completedCycles by viewModel.scalpCompletedCycles.collectAsState()
    val balances by viewModel.binanceBalances.collectAsState()

    var isExpanded by remember { mutableStateOf(true) }
    var selectedSingleSymbol by remember { mutableStateOf("BTCUSDT") }

    val freeUsdt = balances.find { it.asset.equals("USDT", ignoreCase = true) }?.free ?: 13.0

    val activeColor by animateColorAsState(
        targetValue = if (isLoopActive) Color(0xFF00E676) else Color(0xFFFFD600),
        label = "activeColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, Color(0xFFFF9100).copy(alpha = 0.70f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Continuous Loop Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFFF9100).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = Color(0xFFFF9100),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "LUMINA HIGH-FREQUENCY SCALPER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9100)
                            )
                            Badge(
                                containerColor = if (isLoopActive) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFFD600).copy(alpha = 0.2f),
                                contentColor = if (isLoopActive) Color(0xFF00E676) else Color(0xFFFFD600)
                            ) {
                                Text(if (isLoopActive) "LOOP ACTIVE" else "STANDBY", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = "Small Account Strategy: Buy → Quick Profit → Sell → Repeat",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Vision & Compounding Edge Banner
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9100).copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, Color(0xFFFF9100).copy(alpha = 0.30f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFFFF9100), modifier = Modifier.size(20.dp))
                            Text(
                                text = "Small Account Growth: Rather than holding through volatile swings, Lumina captures dozens of quick micro-wins (+0.20% to +0.50% net) and compounds equity rapidly with zero overnight exposure.",
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Scalping HUD / Phase Tracker
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(activeColor, CircleShape)
                                    )
                                    Text(
                                        text = "CURRENT PHASE: ${currentPhase.label.uppercase()}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeColor
                                    )
                                }
                                Text(
                                    text = if (activeSymbol != null) "Active: $activeSymbol" else "Available: $${String.format(Locale.US, "%.2f", freeUsdt)} USDT",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentPhase.description,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // 5-Step Pipeline Indicators
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val phases = listOf(
                                    Pair("1. Scan", currentPhase == ScalpingCyclePhase.SCANNING),
                                    Pair("2. Buy", currentPhase == ScalpingCyclePhase.EXECUTING_BUY),
                                    Pair("3. Profit Tick", currentPhase == ScalpingCyclePhase.MONITORING_PROFIT),
                                    Pair("4. Sell", currentPhase == ScalpingCyclePhase.EXECUTING_SELL),
                                    Pair("5. Repeat", currentPhase == ScalpingCyclePhase.SETTLING_AND_REINVESTING || isLoopActive)
                                )
                                phases.forEach { (label, isActive) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 2.dp)
                                            .background(
                                                color = if (isActive) Color(0xFFFF9100) else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 8.5.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isActive) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Performance & Compounding Stats Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatTile(
                            modifier = Modifier.weight(1f),
                            title = "SCALPS",
                            value = "${summary.totalCyclesCompleted}",
                            subtitle = "${summary.winningCycles}W / ${summary.losingCycles}L (${String.format(Locale.US, "%.0f", summary.winRatePct)}%)",
                            valueColor = Color(0xFF00E676)
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            title = "NET COMPOUNDED",
                            value = "+$${String.format(Locale.US, "%.3f", summary.totalNetProfitUsdt)}",
                            subtitle = "Streak: ${summary.currentConsecutiveWins} wins",
                            valueColor = Color(0xFF00E5FF)
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            title = "AVG VELOCITY",
                            value = "${summary.averageHoldingDurationSeconds.coerceAtLeast(18L)}s",
                            subtitle = "Fast exit turnover",
                            valueColor = Color(0xFFFFD600)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Scalping Configuration Controls (Profit Target, Stop Loss, Duration)
                    Text(
                        text = "DYNAMIC SCALPING & RISK CONTROLS",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Profit Target Selector
                    Text("Take-Profit Micro Target:", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.20, 0.35, 0.50, 0.75).forEach { tp ->
                            FilterChip(
                                selected = config.targetProfitPct == tp,
                                onClick = { viewModel.updateSmallAccountScalpConfig(config.copy(targetProfitPct = tp)) },
                                label = { Text("+${tp}% (${if (tp <= 0.35) "1¢-4¢" else "5¢-10¢"})", fontSize = 9.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Tight Stop Loss Selector
                    Text("Dynamic Protective Stop Loss:", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.50, 0.75, 1.00, 1.50).forEach { sl ->
                            FilterChip(
                                selected = config.tightStopLossPct == sl,
                                onClick = { viewModel.updateSmallAccountScalpConfig(config.copy(tightStopLossPct = sl)) },
                                label = { Text("-${sl}% tight", fontSize = 9.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Max Holding Timeout Selector
                    Text("Max Holding Duration (Fast Rotation):", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3.0, 5.0, 8.0, 15.0).forEach { mins ->
                            FilterChip(
                                selected = config.maxHoldingMinutes == mins,
                                onClick = { viewModel.updateSmallAccountScalpConfig(config.copy(maxHoldingMinutes = mins)) },
                                label = { Text("${mins.toInt()} mins", fontSize = 9.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons: Continuous Loop Runner & Single Scalp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isLoopActive) {
                                    viewModel.stopContinuousSmallAccountScalpLoop()
                                } else {
                                    viewModel.startContinuousSmallAccountScalpLoop()
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLoopActive) Color(0xFFE53935) else Color(0xFFFF9100)
                            )
                        ) {
                            Icon(
                                imageVector = if (isLoopActive) Icons.Default.Stop else Icons.Default.AllInclusive,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLoopActive) "Stop Scalping Loop" else "Start Scalp & Repeat",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.executeSingleScalpCycle(selectedSingleSymbol, 10.50) },
                            enabled = !isLoopActive,
                            modifier = Modifier.weight(0.9f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF9100))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF9100), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1-Scalp Test", fontSize = 10.sp, color = Color(0xFFFF9100), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Recent Scalp History Log
                    if (completedCycles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "RECENT COMPLETED SCALPS",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            completedCycles.take(4).forEach { cycle ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)),
                                    border = BorderStroke(0.8.dp, Color(0xFF00E676).copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(cycle.symbol, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Text("• ${cycle.holdingDurationSeconds}s", fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(
                                                text = "In: \$${cycle.entryPrice} → Out: \$${cycle.exitPrice}",
                                                fontSize = 8.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "+\$${String.format(Locale.US, "%.3f", cycle.netPnlUsdt)} USDT",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00E676)
                                            )
                                            Text(
                                                text = "+${String.format(Locale.US, "%.2f", cycle.returnPct)}% net",
                                                fontSize = 8.5.sp,
                                                color = Color(0xFF00E676)
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
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 7.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
