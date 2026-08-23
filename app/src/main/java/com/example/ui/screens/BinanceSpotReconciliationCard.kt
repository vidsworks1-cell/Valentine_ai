package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aza.strategy.ExecutionReconciliationEngine
import com.example.data.api.StaleTradeMismatch
import com.example.viewmodel.AppViewModel
import java.util.Locale

@Composable
fun BinanceSpotReconciliationCard(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val reconciliation by viewModel.executionReconciliation.collectAsState()
    val binanceBalances by viewModel.binanceBalances.collectAsState()
    val realHoldings by viewModel.realBinanceHoldings.collectAsState()
    val syncResult by viewModel.lastPositionSyncResult.collectAsState()
    val isSyncRunning by viewModel.isPositionSyncRunning.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    var selectedMismatchForClose by remember { mutableStateOf<StaleTradeMismatch?>(null) }
    var showCloseAllDialog by remember { mutableStateOf(false) }

    val discrepancies = reconciliation?.discrepanciesFound ?: 0
    val staleCount = reconciliation?.staleTradesCount ?: 0
    val isClean = discrepancies == 0
    val statusColor = if (isClean) Color(0xFF388E3C) else Color(0xFFE53935)
    val statusBg = if (isClean) Color(0xFF388E3C).copy(alpha = 0.08f) else Color(0xFFE53935).copy(alpha = 0.08f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isClean) Color(0xFF4CAF50).copy(alpha = 0.4f) else Color(0xFFE53935).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
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
                        imageVector = if (isClean) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "SPOT BALANCES & RECONCILIATION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        Text(
                            text = "Audit vs Trades where exit_time IS NULL",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Badge(containerColor = statusBg) {
                    Text(
                        text = if (isClean) "ALL RECONCILED" else "$discrepancies MISMATCH${if (discrepancies > 1) "ES" else ""}",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Summary Grid
            reconciliation?.let { rec ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Monitored Trades", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${rec.activeLocalPositionsCount} Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Spot Assets", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${rec.spotBalancesChecked} Checked", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Stale / Orphan", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$staleCount Stale",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (staleCount > 0) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column {
                        Text("Time Drift", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${rec.exchangeTimeDriftMs} ms", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = rec.reconciliationSummary,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Stale / Discrepancy Items List
            val mismatches = reconciliation?.mismatches ?: emptyList()
            if (mismatches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "FLAGGED DISCREPANCIES (${mismatches.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53935)
                )
                Spacer(modifier = Modifier.height(6.dp))

                mismatches.take(if (isExpanded) mismatches.size else 3).forEach { mismatch ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.3f))
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
                                        containerColor = if (mismatch.discrepancyType == "ORPHAN_DATABASE_POSITION")
                                            Color(0xFFE53935)
                                        else
                                            Color(0xFFFB8C00)
                                    ) {
                                        Text(
                                            mismatch.discrepancyType.replace("_", " "),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        mismatch.symbol,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (mismatch.isStale) {
                                    Button(
                                        onClick = { selectedMismatchForClose = mismatch },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Safe Close", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                mismatch.description,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Tracked in DB: ${String.format(Locale.US, "%.4f", mismatch.trackedSize)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Spot Free: ${String.format(Locale.US, "%.4f", mismatch.spotFree)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (mismatch.entryPrice > 0.0) {
                                    Text(
                                        "Entry: $${String.format(Locale.US, "%,.2f", mismatch.entryPrice)}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (mismatches.size > 3) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (isExpanded) "Show Less" else "Show All ${mismatches.size} Flagged Trades", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Autonomous Position Synchronization Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "AUTONOMOUS POSITION SYNCHRONIZER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Badge(containerColor = Color(0xFF2E7D32).copy(alpha = 0.15f)) {
                            Text("ACTIVE SYNC", color = Color(0xFF2E7D32), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Compares internal positions with Binance Spot holdings. Auto-prunes ghost positions (0.00 balance), stops retry loops on impossible orders, and ensures Lumina manages real Binance holdings only.",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    syncResult?.let { sync ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Ghost Positions Pruned: ${sync.prunedGhostPositions.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sync.prunedGhostPositions.isNotEmpty()) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Verified Real Positions: ${sync.verifiedRealPositions.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                "Real Holdings: ${sync.realBinanceHoldings.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Real Binance Holdings List
            if (realHoldings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "VERIFIED BINANCE HOLDINGS (${realHoldings.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    realHoldings.take(4).forEach { holding ->
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text(holding.baseAsset, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("${String.format(Locale.US, "%.4f", holding.free)}", fontSize = 10.sp)
                                if (holding.estimatedValueUsd > 0.0) {
                                    Text("≈ $${String.format(Locale.US, "%,.1f", holding.estimatedValueUsd)}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Auto-Sync Positions with Binance Button
                Button(
                    onClick = {
                        viewModel.autoSynchronizePositionsWithBinance("MANUAL_UI")
                        Toast.makeText(context, "Autonomous Position Sync initiated with Binance", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isSyncRunning
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSyncRunning) "Syncing..." else "Auto-Sync with Binance", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Copy Audit Report
                OutlinedButton(
                    onClick = {
                        reconciliation?.let { rec ->
                            val report = ExecutionReconciliationEngine.formatReconciliationAudit(rec)
                            clipboardManager.setText(AnnotatedString(report))
                            Toast.makeText(context, "Reconciliation audit copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Audit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                // Safe Close All Stale
                if (staleCount > 0) {
                    Button(
                        onClick = { showCloseAllDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Close ($staleCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Dialog: Safe Close Single Trade
    selectedMismatchForClose?.let { mismatch ->
        AlertDialog(
            onDismissRequest = { selectedMismatchForClose = null },
            title = { Text("Safe Close Stale Trade: ${mismatch.symbol}") },
            text = {
                Column {
                    Text(
                        "This position is currently marked open (exit_time IS NULL) in local records, but Binance Spot reports 0.00 balance.",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Closing this position will:\n• Mark exit_time with timestamp\n• Archive post-trade review\n• Rebalance local accounting safely without triggering duplicate sell orders",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.safeCloseSingleStaleTrade(mismatch)
                        selectedMismatchForClose = null
                        Toast.makeText(context, "Stale trade ${mismatch.symbol} closed safely.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Confirm Safe Close")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedMismatchForClose = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Safe Close All Stale Trades
    if (showCloseAllDialog) {
        AlertDialog(
            onDismissRequest = { showCloseAllDialog = false },
            title = { Text("Safe Close All Stale Positions ($staleCount)") },
            text = {
                Text(
                    "Are you sure you want to safe-close and archive all $staleCount stale/orphan trades?\n\nThis will synchronize local trade records with verified Binance Spot balances.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.safeCloseStaleTrades()
                        showCloseAllDialog = false
                        Toast.makeText(context, "All $staleCount stale trades closed safely.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Safe Close All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCloseAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
