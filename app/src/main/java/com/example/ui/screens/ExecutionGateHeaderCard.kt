package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aza.strategy.LuminaOperatingMode
import com.example.aza.strategy.PendingApprovalTrade
import com.example.aza.strategy.PendingTradeStatus
import com.example.viewmodel.AppViewModel
import java.util.Locale

@Composable
fun ExecutionGateHeaderCard(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val operatingMode by viewModel.operatingMode.collectAsState()
    val isExecutionUnlocked by viewModel.isExecutionUnlocked.collectAsState()
    val pendingQueue by viewModel.pendingApprovalQueue.collectAsState()

    var showUnlockConfirmDialog by remember { mutableStateOf(false) }
    var showModeSelectorDialog by remember { mutableStateOf(false) }

    val waitingTrades = pendingQueue.filter { it.status == PendingTradeStatus.WAITING_FOR_OPERATOR_APPROVAL && !it.isExpired }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            when {
                !isExecutionUnlocked -> Color(0xFFFB8C00).copy(alpha = 0.5f)
                operatingMode == LuminaOperatingMode.ANALYSIS_ONLY -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                operatingMode == LuminaOperatingMode.LIVE_APPROVAL -> Color(0xFF0288D1).copy(alpha = 0.5f)
                else -> Color(0xFFE53935).copy(alpha = 0.5f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Mode Badge & Master Execution Lock Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tier 1: Operating Mode Selector Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (operatingMode) {
                        LuminaOperatingMode.ANALYSIS_ONLY -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        LuminaOperatingMode.LIVE_APPROVAL -> Color(0xFF0288D1).copy(alpha = 0.15f)
                        LuminaOperatingMode.FULL_AUTONOMOUS_LIVE -> Color(0xFFE53935).copy(alpha = 0.15f)
                    },
                    modifier = Modifier.clickable { showModeSelectorDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when (operatingMode) {
                                LuminaOperatingMode.ANALYSIS_ONLY -> Icons.Default.Analytics
                                LuminaOperatingMode.LIVE_APPROVAL -> Icons.Default.HowToVote
                                LuminaOperatingMode.FULL_AUTONOMOUS_LIVE -> Icons.Default.Bolt
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when (operatingMode) {
                                LuminaOperatingMode.ANALYSIS_ONLY -> MaterialTheme.colorScheme.primary
                                LuminaOperatingMode.LIVE_APPROVAL -> Color(0xFF0288D1)
                                LuminaOperatingMode.FULL_AUTONOMOUS_LIVE -> Color(0xFFE53935)
                            }
                        )
                        Column {
                            Text(
                                "OPERATING MODE",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                operatingMode.displayName.substringBefore(" ("),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }

                // Tier 2: Master Execution Lock Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "EXECUTION LOCK",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExecutionUnlocked) Color(0xFFE53935) else Color(0xFF388E3C)
                        )
                        Text(
                            if (isExecutionUnlocked) "UNLOCKED" else "LOCKED (SAFE)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExecutionUnlocked) Color(0xFFE53935) else Color(0xFF388E3C)
                        )
                    }

                    Switch(
                        checked = isExecutionUnlocked,
                        onCheckedChange = { willUnlock ->
                            if (willUnlock) {
                                showUnlockConfirmDialog = true
                            } else {
                                viewModel.setExecutionUnlocked(false)
                                Toast.makeText(context, "Execution Lock Engaged. Live orders blocked.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFE53935),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF388E3C)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Safety Status Summary Text
            val statusSummary = when {
                !isExecutionUnlocked -> "🔒 Master Execution Lock is ENGAGED. Zero Binance orders will be sent regardless of market signals."
                operatingMode == LuminaOperatingMode.ANALYSIS_ONLY -> "🛡️ ANALYSIS_ONLY: AI market scanner & risk engine active. All order submissions blocked."
                operatingMode == LuminaOperatingMode.LIVE_APPROVAL -> "⏳ LIVE APPROVAL (Human-In-The-Loop): AI stages trades into the approval queue. Orders require explicit click."
                else -> "⚡ FULL AUTONOMOUS LIVE: Validated AI setups will execute directly on Binance Spot."
            }

            Text(
                statusSummary,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )

            // PENDING APPROVAL QUEUE (When trades are waiting)
            if (waitingTrades.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PENDING APPROVAL QUEUE (${waitingTrades.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0288D1)
                    )
                    Badge(containerColor = Color(0xFF0288D1).copy(alpha = 0.15f)) {
                        Text("2-MIN TIMEOUT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0288D1))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                waitingTrades.forEach { trade ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF0288D1).copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Badge(
                                        containerColor = if (trade.side == "BUY") Color(0xFF2E7D32) else Color(0xFFC62828)
                                    ) {
                                        Text(trade.side, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.5.sp)
                                    }
                                    Text(
                                        trade.symbol,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "#${trade.proposalNumber}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFFB8C00))
                                    Text(
                                        "${trade.remainingSeconds}s left",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFB8C00)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Entry: $${String.format(Locale.US, "%,.2f", trade.entryPrice)}", fontSize = 10.5.sp)
                                Text("Size: $${String.format(Locale.US, "%,.0f", trade.targetPositionSizeUsd)}", fontSize = 10.5.sp)
                                Text("Conf: ${String.format(Locale.US, "%.0f%%", trade.confidenceScore * 100)}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action buttons: Approve vs Dismiss
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.rejectPendingTrade(trade.id, "Dismissed by operator") },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Dismiss", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        viewModel.approveAndExecutePendingTrade(trade.id)
                                        Toast.makeText(context, "Approving & Validating trade #${trade.proposalNumber}...", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1.4f).height(32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (trade.side == "BUY") Color(0xFF2E7D32) else Color(0xFFC62828)
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Approve & Execute", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Confirm Master Execution Unlock
    if (showUnlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockConfirmDialog = false },
            title = { Text("⚠️ Unlock Master Execution Gate?") },
            text = {
                Text(
                    "You are about to enable real order execution authority (execution_enabled = true).\n\n" +
                    "• In Live Approval Mode, you will be prompted before every Binance order.\n" +
                    "• Lumina automatically enforces pre-execution slippage tolerance (max 0.8%) and 2-minute timeouts.",
                    fontSize = 12.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setExecutionUnlocked(true)
                        showUnlockConfirmDialog = false
                        Toast.makeText(context, "Master Execution UNLOCKED", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Confirm Unlock")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUnlockConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Select Operating Mode
    if (showModeSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showModeSelectorDialog = false },
            title = { Text("Select Lumina Operating Mode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LuminaOperatingMode.values().forEach { mode ->
                        val isSelected = mode == operatingMode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setOperatingMode(mode)
                                    showModeSelectorDialog = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mode.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(mode.description, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeSelectorDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
