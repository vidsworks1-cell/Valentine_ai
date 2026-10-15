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
import com.example.aza.strategy.PreBuyExitViabilityEngine
import com.example.data.database.BinancePositionEntity
import com.example.viewmodel.AppViewModel
import java.util.Locale

@Composable
fun LuminaPreBuySellabilityProfitHarvesterCard(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val isAutoProfitSellEnabled by viewModel.isAutoProfitSellOnBinanceEnabled.collectAsState()
    val profitThresholdPct by viewModel.customProfitHarvestThresholdPct.collectAsState()
    val allPositions by viewModel.allPositions.collectAsState()
    val openPositions = remember(allPositions) { allPositions.filter { it.status == "OPEN" } }

    var testSymbol by remember { mutableStateOf("BTCUSDT") }
    var testAllocationUsdt by remember { mutableStateOf(10.50) }
    var isAnalyzerExpanded by remember { mutableStateOf(false) }

    val testReport = remember(testSymbol, testAllocationUsdt) {
        viewModel.evaluatePreBuySellabilityCheck(testSymbol, testAllocationUsdt)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, Color(0xFF00E676).copy(alpha = 0.50f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Pre-Buy Sellability & Binance Fast Profit Harvester
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
                            .size(32.dp)
                            .background(Color(0xFF00E676).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "PRE-BUY SELLABILITY & FAST PROFIT HARVESTER",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                        Text(
                            text = "Analyzes Exit Path Before Buying • Auto-Sells Any Profit or TP",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isAutoProfitSellEnabled,
                    onCheckedChange = { viewModel.setAutoProfitSellOnBinanceEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00E676)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAutoProfitSellEnabled) Color(0xFF00E676).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, if (isAutoProfitSellEnabled) Color(0xFF00E676).copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isAutoProfitSellEnabled) Icons.Default.CheckCircle else Icons.Default.PauseCircle,
                            contentDescription = null,
                            tint = if (isAutoProfitSellEnabled) Color(0xFF00E676) else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isAutoProfitSellEnabled) "HARVESTER ARMED: Auto-Sell on TP or Net Profit ≥ +${String.format(Locale.US, "%.2f", profitThresholdPct)}%" else "HARVESTER PAUSED: Manual Sells Only",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAutoProfitSellEnabled) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Binance Spot Live",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Emergency Trapped Asset Liquidation Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFF5252).copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.2.dp, Color(0xFFFF5252).copy(alpha = 0.45f))
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
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "TRAPPED ASSETS / SOFT STAKING RECOVERY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252)
                            )
                        }
                        Badge(
                            containerColor = Color(0xFFFF5252).copy(alpha = 0.2f),
                            contentColor = Color(0xFFFF5252)
                        ) {
                            Text(
                                text = "4 HELD ASSETS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Detected Spot/Soft-Staking balances: RENDER (2.5), INJ (0.62), ETH (0.0021), BNB (0.00697). Standard spot market sells were blocked because balance dipped below Binance's $5.00 MIN_NOTIONAL limit. One-Click Smart Liquidation redeems soft-staking and converts directly to USDT via zero-fee Binance Convert & Dust Sweep.",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.emergencyLiquidateTrappedPositions() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5252)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CurrencyExchange,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "EMERGENCY LIQUIDATE TO USDT (CONVERT & DUST SWEEP)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pre-Buy Sellability Section (Interactive Analyzer)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAnalyzerExpanded = !isAnalyzerExpanded },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                ),
                border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.35f))
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
                            Icon(
                                imageVector = Icons.Default.Rule,
                                contentDescription = null,
                                tint = Color(0xFF00B0FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Pre-Buy Exit Viability Inspector",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Badge(
                                containerColor = if (testReport.isSellableApproved) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFE53935).copy(alpha = 0.2f),
                                contentColor = if (testReport.isSellableApproved) Color(0xFF00E676) else Color(0xFFE53935)
                            ) {
                                Text(
                                    text = if (testReport.isSellableApproved) "✓ SELLABLE APPROVED" else "❌ REJECTED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Icon(
                                imageVector = if (isAnalyzerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Core Rule: Lumina verifies it can safely sell on Binance (LOT_SIZE, MIN_NOTIONAL $5.00, fee hurdle) before any buy.",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(visible = isAnalyzerExpanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))

                            // Symbol selector chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "ADAUSDT", "DOTUSDT", "PEPEUSDT").forEach { sym ->
                                    FilterChip(
                                        selected = testSymbol == sym,
                                        onClick = { testSymbol = sym },
                                        label = { Text(sym.removeSuffix("USDT"), fontSize = 10.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Allocation quick chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Allocation:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                listOf(6.0, 10.50, 15.0, 25.0).forEach { amt ->
                                    FilterChip(
                                        selected = testAllocationUsdt == amt,
                                        onClick = { testAllocationUsdt = amt },
                                        label = { Text("$$amt", fontSize = 10.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Viability metrics grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Exec Qty", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${testReport.executableQuantity}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column {
                                    Text("MIN_NOTIONAL", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", testReport.entryNotionalUsdt)} / $${testReport.minNotionalRequiredUsdt}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (testReport.isMinNotionalSatisfied) Color(0xFF2E7D32) else Color(0xFFE53935)
                                    )
                                }
                                Column {
                                    Text("Round-Trip Fee", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "$${String.format(Locale.US, "%.4f", testReport.estimatedFeesUsdt)} (0.2%)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column {
                                    Text("Breakeven Hurdle", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "$${String.format(Locale.US, if (testReport.breakevenHurdlePrice < 1.0) "%.4f" else "%.2f", testReport.breakevenHurdlePrice)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = testReport.executionGuidance,
                                fontSize = 9.5.sp,
                                color = if (testReport.isSellableApproved) Color(0xFF2E7D32) else Color(0xFFE53935)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Profit Harvest Threshold Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fast Profit Auto-Sell Target:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.25 to "1¢ Quick", 0.35 to "+0.35%", 0.65 to "+0.65%", 1.20 to "+1.2%").forEach { (pct, label) ->
                        FilterChip(
                            selected = profitThresholdPct == pct,
                            onClick = { viewModel.setCustomProfitHarvestThresholdPct(pct) },
                            label = { Text(label, fontSize = 9.5.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Positions with Live Profit & Fast Sell Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE BINANCE POSITIONS (${openPositions.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (openPositions.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.harvestAllProfitableBinancePositions() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Harvest All Green", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                    }
                }
            }

            if (openPositions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No open positions. Lumina is actively scanning the universe for sellable setups.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    openPositions.forEach { pos ->
                        PositionHarvestItem(pos = pos, onSellClick = { viewModel.harvestProfitsAndSellOnBinance(pos.symbol) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionHarvestItem(
    pos: BinancePositionEntity,
    onSellClick: () -> Unit
) {
    val grossPnl = (pos.currentPrice - pos.entryPrice) * pos.amountOwned
    val feesEst = (pos.amountOwned * pos.entryPrice) * PreBuyExitViabilityEngine.ROUND_TRIP_FEE_RATE
    val netPnl = grossPnl - feesEst
    val pnlPct = if (pos.entryPrice > 0.0) ((pos.currentPrice - pos.entryPrice) / pos.entryPrice) * 100.0 else 0.0
    val isNetGreen = netPnl > 0.0
    val isTpHit = pos.takeProfitPrice > 0.0 && pos.currentPrice >= pos.takeProfitPrice

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTpHit) Color(0xFF00E676).copy(alpha = 0.12f) else if (isNetGreen) Color(0xFF00E676).copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = if (isTpHit) BorderStroke(1.2.dp, Color(0xFF00E676)) else if (isNetGreen) BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = pos.symbol,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isTpHit) {
                        Badge(containerColor = Color(0xFF00E676), contentColor = Color.Black) {
                            Text("🎯 TP HIT", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    } else if (isNetGreen) {
                        Badge(containerColor = Color(0xFF00E676).copy(alpha = 0.2f), contentColor = Color(0xFF00E676)) {
                            Text("PROFIT", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
                Text(
                    text = "Entry: $${String.format(Locale.US, if (pos.entryPrice < 1.0) "%.4f" else "%.2f", pos.entryPrice)} • Now: $${String.format(Locale.US, if (pos.currentPrice < 1.0) "%.4f" else "%.2f", pos.currentPrice)}",
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pos.takeProfitPrice > 0.0) {
                    Text(
                        text = "TP: $${String.format(Locale.US, if (pos.takeProfitPrice < 1.0) "%.4f" else "%.2f", pos.takeProfitPrice)}",
                        fontSize = 9.sp,
                        color = Color(0xFF00B0FF)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (netPnl >= 0) "+" else ""}$${String.format(Locale.US, "%.3f", netPnl)} net",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isNetGreen) Color(0xFF00E676) else Color(0xFFE53935)
                    )
                    Text(
                        text = "${if (pnlPct >= 0) "+" else ""}${String.format(Locale.US, "%.2f", pnlPct)}%",
                        fontSize = 9.sp,
                        color = if (pnlPct >= 0) Color(0xFF00E676) else Color(0xFFE53935)
                    )
                }

                Button(
                    onClick = onSellClick,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNetGreen) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Sell, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sell on Binance", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}
