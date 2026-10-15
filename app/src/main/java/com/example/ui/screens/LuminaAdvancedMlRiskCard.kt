package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.example.aza.strategy.MarketRegimeType
import com.example.viewmodel.AppViewModel
import java.util.Locale

@Composable
fun LuminaAdvancedMlRiskCard(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val prediction by viewModel.deepMlPrediction.collectAsState()
    val activeRegime by viewModel.activeMarketRegime.collectAsState()
    val riskReport by viewModel.adaptiveRiskReport.collectAsState()

    var selectedSymbol by remember { mutableStateOf("BTCUSDT") }
    val symbols = com.example.aza.strategy.LuminaMultiSymbolUniverseEngine.UNIVERSE_SPECIFICATIONS.map { it.first }

    var showXaiDetails by remember { mutableStateOf(true) }
    var showRiskDetails by remember { mutableStateOf(true) }
    var showRegimeTransitions by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF6200EA).copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Deep ML & Adaptive Risk Management
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
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "LUMINA DEEP ENSEMBLE ML",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C4DFF)
                        )
                        Text(
                            text = "GBDT • Temporal Attention • Bayesian SGD • Adaptive Risk",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Badge(
                    containerColor = Color(0xFF7C4DFF).copy(alpha = 0.15f),
                    contentColor = Color(0xFF7C4DFF)
                ) {
                    Text(
                        text = "32-FEATURE VECTOR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Market Symbol Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                symbols.forEach { sym ->
                    val isSelected = selectedSymbol == sym
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedSymbol = sym
                            viewModel.runDeepMlMarketAnalysis(sym)
                        },
                        label = {
                            Text(
                                text = sym.replace("USDT", ""),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF7C4DFF).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF7C4DFF)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Prediction Outcome Card
            if (prediction != null) {
                val pred = prediction!!
                val dirColor = when (pred.predictedDirection) {
                    "STRONG_BULLISH", "MODERATE_BULLISH" -> Color(0xFF2E7D32)
                    "STRONG_BEARISH", "MODERATE_BEARISH" -> Color(0xFFC62828)
                    else -> Color(0xFFE65100)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = dirColor.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, dirColor.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = pred.predictedDirection.replace('_', ' '),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dirColor
                                )
                                Text(
                                    text = "Confidence: ${String.format(Locale.US, "%.1f", pred.ensembleConfidencePct)}% • Exp Alpha: +${String.format(Locale.US, "%.0f", pred.expectedReturnBps)} bps",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Badge(
                                containerColor = dirColor.copy(alpha = 0.2f),
                                contentColor = dirColor
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.0f", pred.bullishProbabilityPct)}% BULL",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Multi-Class Probability Split Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight((pred.bullishProbabilityPct / 100.0).toFloat().coerceIn(0.05f, 0.90f))
                                    .fillMaxHeight()
                                    .background(Color(0xFF2E7D32), RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .weight((pred.neutralProbabilityPct / 100.0).toFloat().coerceIn(0.05f, 0.90f))
                                    .fillMaxHeight()
                                    .background(Color(0xFFB0BEC5))
                            )
                            Box(
                                modifier = Modifier
                                    .weight((pred.bearishProbabilityPct / 100.0).toFloat().coerceIn(0.05f, 0.90f))
                                    .fillMaxHeight()
                                    .background(Color(0xFFC62828), RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Uncertainty Bounds: Epistemic vs Aleatoric vs OOD
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Model Disagreement (Epistemic): ${String.format(Locale.US, "%.2f", pred.epistemicUncertainty)}σ",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "OOD Novelty Risk: ${String.format(Locale.US, "%.0f", pred.outOfDistributionRiskPct)}%",
                                fontSize = 9.sp,
                                color = if (pred.outOfDistributionRiskPct > 40.0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Market Regime Selection & Dynamic Adaptation
            Text(
                text = "DYNAMIC MARKET REGIME CALIBRATION",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Regime Switcher Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MarketRegimeType.values().forEach { reg ->
                    val isSelected = activeRegime == reg
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setActiveMarketRegime(reg) },
                        label = {
                            Text(
                                text = when (reg) {
                                    MarketRegimeType.BULL_EXPANSION -> "🚀 Bullish"
                                    MarketRegimeType.BEAR_BREAKDOWN -> "📉 Bearish"
                                    MarketRegimeType.CHOPPY_RANGE -> "↔️ Range"
                                    MarketRegimeType.HIGH_VOLATILITY_SHOCK -> "⚡ Vol Shock"
                                    MarketRegimeType.LOW_VOLATILITY_SQUEEZE -> "🗜️ Squeeze"
                                },
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Section: Explainable AI (XAI) Feature Importance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showXaiDetails = !showXaiDetails },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "EXPLAINABLE AI (TOP PREDICTIVE DRIVERS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (showXaiDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showXaiDetails) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    prediction?.topContributingFeatures?.forEach { (feature, weight) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(feature, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(70.dp)
                                        .height(4.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((weight * 2.5).toFloat().coerceIn(0.1f, 1.0f))
                                            .fillMaxHeight()
                                            .background(Color(0xFF7C4DFF), RoundedCornerShape(2.dp))
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "%.2f", weight),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF7C4DFF)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Section: Adaptive Multi-Regime Risk Assessment & VaR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRiskDetails = !showRiskDetails },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ADAPTIVE REGIME RISK & VaR SHIELD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (showRiskDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showRiskDetails) {
                if (riskReport != null) {
                    val risk = riskReport!!
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Risk Sizing", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", risk.regimeRiskMultiplier)}x Risk",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column {
                                Text("1-Day VaR (95%)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "$${String.format(Locale.US, "%.3f", risk.parametricVar95PctUsdt)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                            }
                            Column {
                                Text("Tail CVaR (99%)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "$${String.format(Locale.US, "%.3f", risk.conditionalVar99PctUsdt)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828)
                                )
                            }
                            Column {
                                Text("Max SL Capital", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "$${String.format(Locale.US, "%.3f", risk.maxStopLossRiskCapitalUsdt)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        risk.riskMitigationNotes.forEach { note ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.5.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("• ", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                Text(note, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action: Trigger Deep ML Model Inference
            OutlinedButton(
                onClick = { viewModel.runDeepMlMarketAnalysis(selectedSymbol) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF7C4DFF))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Run Deep ML Inference ($selectedSymbol)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7C4DFF))
            }
        }
    }
}
