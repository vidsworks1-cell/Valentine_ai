package com.example.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppViewModel
import java.util.Locale

@Composable
fun MicroAccountCapitalCard(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val microConfig by viewModel.microAccountRiskConfig.collectAsState()
    val allocationPlan by viewModel.capitalAllocationPlan.collectAsState()
    val binanceBalances by viewModel.binanceBalances.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.recomputeCapitalAllocation()
    }

    val totalUsdt = binanceBalances.find { it.asset.equals("USDT", ignoreCase = true) }?.let { it.free + it.locked } ?: 13.0
    val freeUsdt = binanceBalances.find { it.asset.equals("USDT", ignoreCase = true) }?.free ?: 11.50

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
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
                        imageVector = Icons.Default.Savings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "MICRO ACCOUNT RISK MODE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Capital-Optimized Execution (~13 USDT)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = "1 POSITION MAX",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Micro Risk Rules Summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Equity", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${String.format(Locale.US, "%.2f", totalUsdt)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Usable Free", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${String.format(Locale.US, "%.2f", freeUsdt)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
                Column {
                    Text("Fee Reserve", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${String.format(Locale.US, "%.2f", microConfig.feeReserveUsdt)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text("Min Conviction", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(">= 80%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Capital Allocation Engine Recommendation
            Text(
                text = "CAPITAL ALLOCATION ENGINE (BEST-USE DEPLOYMENT)",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            allocationPlan?.let { plan ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val targetSym = plan.selectedOpportunity?.symbol ?: "CASH PRESERVATION"
                            Text(
                                text = "🎯 Target: $targetSym",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (plan.selectedOpportunity != null) MaterialTheme.colorScheme.primary else Color(0xFFE65100)
                            )
                            if (plan.selectedOpportunity != null) {
                                Text(
                                    text = "Allocation: $${String.format(Locale.US, "%.2f", plan.allocatedUsdt)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = plan.recommendationSummary,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("No Competing Orders", fontSize = 9.5.sp) },
                                modifier = Modifier.height(24.dp)
                            )
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Min Notional Checked", fontSize = 9.5.sp) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { viewModel.recomputeCapitalAllocation() },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Recompute Optimal Allocation", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
