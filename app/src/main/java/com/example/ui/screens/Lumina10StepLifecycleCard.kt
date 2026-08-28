package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.aza.strategy.LifecycleStepStatus
import com.example.aza.strategy.Trade10StepAudit
import com.example.aza.strategy.TradeLifecycleStep
import com.example.viewmodel.AppViewModel
import java.util.Locale

@Composable
fun Lumina10StepLifecycleCard(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val activeAudits by viewModel.active10StepAudits.collectAsState()
    val isRunning by viewModel.is10StepCycleRunning.collectAsState()

    var selectedSymbol by remember { mutableStateOf("BTCUSDT") }
    var selectedAmountUsdt by remember { mutableStateOf(10.50) }
    var isExpanded by remember { mutableStateOf(true) }

    val currentAudit = activeAudits.firstOrNull()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.60f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
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
                            .size(36.dp)
                            .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "LUMINA 10-STEP TRADE LIFECYCLE ENGINE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                        Text(
                            text = "End-to-End Binance Spot Execution & Verification Pipeline",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Controls: Symbol & Allocation Selector + Run Verification Button
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "VERIFICATION & TEST RUNNER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Symbol Selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "ADAUSDT", "PEPEUSDT").forEach { sym ->
                                    FilterChip(
                                        selected = selectedSymbol == sym,
                                        onClick = { selectedSymbol = sym },
                                        label = { Text(sym.removeSuffix("USDT"), fontSize = 10.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Amount Selection & Action Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(6.0, 10.50, 15.0, 25.0).forEach { amt ->
                                        FilterChip(
                                            selected = selectedAmountUsdt == amt,
                                            onClick = { selectedAmountUsdt = amt },
                                            label = { Text("$$amt", fontSize = 9.5.sp) }
                                        )
                                    }
                                }

                                Button(
                                    onClick = { viewModel.runComplete10StepLifecycleVerification(selectedSymbol, selectedAmountUsdt) },
                                    enabled = !isRunning,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    if (isRunning) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Executing...", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Run 10 Steps", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 10 Steps Pipeline Display
                    Text(
                        text = "10-STEP EXECUTION & VERIFICATION AUDIT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val stepsToDisplay = currentAudit?.steps ?: TradeLifecycleStep.values().map { 
                        com.example.aza.strategy.LifecycleStepRecord(it, LifecycleStepStatus.PENDING, "Waiting to run")
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        stepsToDisplay.forEach { record ->
                            StepItemView(record = record)
                        }
                    }

                    if (currentAudit != null && currentAudit.isCompleted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF00E676).copy(alpha = 0.12f)),
                            border = BorderStroke(1.2.dp, Color(0xFF00E676))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
                                        Text("10/10 STEPS VERIFIED COMPLETE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                                    }
                                    Text(
                                        text = "+$${String.format(Locale.US, "%.3f", currentAudit.netPnl)} net PnL",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E676)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Buy Order: #${currentAudit.buyOrderId ?: "SPOT-BUY"} • Sell Order: #${currentAudit.sellOrderId ?: "SPOT-SELL"} • Room DB Review Archived",
                                    fontSize = 9.5.sp,
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

@Composable
private fun StepItemView(record: com.example.aza.strategy.LifecycleStepRecord) {
    val isSuccess = record.status == LifecycleStepStatus.VERIFIED_SUCCESS
    val isInProgress = record.status == LifecycleStepStatus.IN_PROGRESS
    val isFailed = record.status == LifecycleStepStatus.FAILED

    var showDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetails = !showDetails },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSuccess -> Color(0xFF00E676).copy(alpha = 0.08f)
                isInProgress -> Color(0xFF00E5FF).copy(alpha = 0.12f)
                isFailed -> Color(0xFFE53935).copy(alpha = 0.10f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }
        ),
        border = when {
            isSuccess -> BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.40f))
            isInProgress -> BorderStroke(1.2.dp, Color(0xFF00E5FF))
            isFailed -> BorderStroke(1.dp, Color(0xFFE53935))
            else -> BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(
                                color = when {
                                    isSuccess -> Color(0xFF00E676)
                                    isInProgress -> Color(0xFF00E5FF)
                                    isFailed -> Color(0xFFE53935)
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.Black, strokeWidth = 1.5.dp)
                        } else {
                            Text(
                                text = "${record.step.stepNumber}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSuccess || isInProgress || isFailed) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column {
                        Text(
                            text = record.step.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                isSuccess -> Color(0xFF00E676)
                                isInProgress -> Color(0xFF00E5FF)
                                isFailed -> Color(0xFFE53935)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = if (record.details.isNotBlank()) record.details else record.step.shortDesc,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (showDetails) Int.MAX_VALUE else 1
                        )
                    }
                }

                if (isSuccess) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                } else if (isInProgress) {
                    Badge(containerColor = Color(0xFF00E5FF), contentColor = Color.Black) {
                        Text("RUNNING", fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }

            if (showDetails && record.dataPayload.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(4.dp))
                record.dataPayload.forEach { (k, v) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(k, fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, fontSize = 8.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
