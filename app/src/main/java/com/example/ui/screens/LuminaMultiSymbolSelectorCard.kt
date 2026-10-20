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
import com.example.aza.strategy.SymbolAssessmentRecord
import com.example.aza.strategy.SymbolCategory
import com.example.viewmodel.AppViewModel
import java.util.Locale

@Composable
fun LuminaMultiSymbolSelectorCard(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scanSummary by viewModel.universeScanSummary.collectAsState()
    val activeFilter by viewModel.selectedUniverseCategoryFilter.collectAsState()

    var expandedSymbol by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Multi-Universe Scanner & Moonshot Selector
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
                        imageVector = Icons.Default.TravelExplore,
                        contentDescription = null,
                        tint = Color(0xFF00B0FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "MULTI-SYMBOL UNIVERSE SCANNER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00B0FF)
                        )
                        Text(
                            text = "Full Exchange Perpetual Universe • 400+ Contracts",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Badge(
                    containerColor = Color(0xFF00B0FF).copy(alpha = 0.15f),
                    contentColor = Color(0xFF00B0FF)
                ) {
                    Text(
                        text = "${scanSummary?.totalAssessedSymbols ?: 31} MONITORED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Champion Selected Trade Hero Banner
            val topPick = scanSummary?.topSelectedCandidate
            if (topPick != null) {
                val categoryColor = when (topPick.category) {
                    SymbolCategory.BLUE_CHIP -> Color(0xFF1976D2)
                    SymbolCategory.MAJOR_ALT -> Color(0xFF7B1FA2)
                    SymbolCategory.MOONSHOT_GROWTH -> Color(0xFFE65100)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = categoryColor.copy(alpha = 0.08f)),
                    border = BorderStroke(1.2.dp, categoryColor.copy(alpha = 0.40f))
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
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "CHOSEN LUMINA TRADE: ${topPick.symbol}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = categoryColor
                                )
                            }

                            Badge(
                                containerColor = categoryColor.copy(alpha = 0.2f),
                                contentColor = categoryColor
                            ) {
                                Text(
                                    text = topPick.category.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Key Metrics Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Score", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = String.format(Locale.US, "%.1f", topPick.compositeRankScore),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = categoryColor
                                )
                            }
                            Column {
                                Text("Win Prob", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${String.format(Locale.US, "%.0f", topPick.winProbabilityPct)}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                            Column {
                                Text("Exp R/R", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", topPick.expectedRewardRisk)}x",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column {
                                Text("Growth Upside", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", topPick.moonshotGrowthMultiplier)}x",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                            }
                            Column {
                                Text("Safety", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${String.format(Locale.US, "%.0f", topPick.safetyMarginScore)}/100",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = topPick.algorithmicRationale,
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = activeFilter == null,
                    onClick = { viewModel.setUniverseCategoryFilter(null) },
                    label = { Text("All (${scanSummary?.allRankedAssessments?.size ?: 18})", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = activeFilter == SymbolCategory.BLUE_CHIP,
                    onClick = { viewModel.setUniverseCategoryFilter(SymbolCategory.BLUE_CHIP) },
                    label = { Text("💎 Blue-Chips", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = activeFilter == SymbolCategory.MAJOR_ALT,
                    onClick = { viewModel.setUniverseCategoryFilter(SymbolCategory.MAJOR_ALT) },
                    label = { Text("🚀 Major Alts", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = activeFilter == SymbolCategory.MOONSHOT_GROWTH,
                    onClick = { viewModel.setUniverseCategoryFilter(SymbolCategory.MOONSHOT_GROWTH) },
                    label = { Text("🌟 Moonshots", fontSize = 10.sp) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Ranked List of Assessed Symbols
            val filteredAssessments = scanSummary?.allRankedAssessments?.filter {
                activeFilter == null || it.category == activeFilter
            } ?: emptyList()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filteredAssessments.forEach { record ->
                    val isExpanded = expandedSymbol == record.symbol
                    val badgeColor = when (record.category) {
                        SymbolCategory.BLUE_CHIP -> Color(0xFF1976D2)
                        SymbolCategory.MAJOR_ALT -> Color(0xFF7B1FA2)
                        SymbolCategory.MOONSHOT_GROWTH -> Color(0xFFE65100)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedSymbol = if (isExpanded) null else record.symbol },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = if (record.rankPosition == 1) BorderStroke(1.dp, Color(0xFFFFB300)) else null
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "#${record.rankPosition}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (record.rankPosition <= 3) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = record.symbol,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = when (record.category) {
                                                        SymbolCategory.BLUE_CHIP -> "Core"
                                                        SymbolCategory.MAJOR_ALT -> "Major"
                                                        SymbolCategory.MOONSHOT_GROWTH -> "Moonshot"
                                                    },
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = badgeColor
                                                )
                                            }
                                        }
                                        Text(
                                            text = "$${String.format(Locale.US, if (record.currentPrice < 1.0) "%.4f" else "%.2f", record.currentPrice)} • Vol: $${String.format(Locale.US, "%.0f", record.volume24hUsd / 1_000_000.0)}M",
                                            fontSize = 9.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Score ${String.format(Locale.US, "%.1f", record.compositeRankScore)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Win: ${String.format(Locale.US, "%.0f", record.winProbabilityPct)}% • ${String.format(Locale.US, "%.1f", record.expectedRewardRisk)}x RR",
                                            fontSize = 9.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.selectUniverseSymbolForTargeting(record.symbol) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GpsFixed,
                                            contentDescription = "Target Symbol",
                                            tint = Color(0xFF00B0FF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Market Cap: $${String.format(Locale.US, "%.1f", record.estimatedMarketCapUsd / 1_000_000_000.0)}B", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("ATR Volatility: ${String.format(Locale.US, "%.1f", record.atr24hPct)}%", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Growth Multiple: ${String.format(Locale.US, "%.1f", record.moonshotGrowthMultiplier)}x", fontSize = 9.sp, color = Color(0xFFE65100))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = record.algorithmicRationale,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.selectUniverseSymbolForTargeting(record.symbol) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(30.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00B0FF))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Run Deep ML Inference & Target ${record.symbol}", fontSize = 10.5.sp, color = Color(0xFF00B0FF))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Action: Run Full Universe Re-Scan
            Button(
                onClick = { viewModel.scanAndAssessMultiSymbolUniverse() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan & Re-Rank Multi-Symbol Universe", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
