package com.example.ui.screens
import java.util.Locale

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppViewModel

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title.uppercase(Locale.US),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PositionScalingDrawdownChart(
    currentDrawdownPct: Double,
    maxDrawdownLimitPct: Double,
    adaptivePositionScaleFactor: Double,
    onDrawdownChanged: (Double) -> Unit
) {
    Surface(
        color = Color(0xFF141A24),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2E3B52)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📈 Position Scale vs Drawdown Correlation",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "Dynamic position sizing response function to drawdown risk",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Surface(
                    color = if (adaptivePositionScaleFactor > 0.7) Color(0xFF15803D).copy(alpha = 0.2f)
                            else if (adaptivePositionScaleFactor > 0.3) Color(0xFFB45309).copy(alpha = 0.2f)
                            else Color(0xFFB91C1C).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Scale: ${(adaptivePositionScaleFactor * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (adaptivePositionScaleFactor > 0.7) Color(0xFF4ADE80)
                                else if (adaptivePositionScaleFactor > 0.3) Color(0xFFFBBF24)
                                else Color(0xFFF87171)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF0F172A), shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val paddingLeft = 35f
                    val paddingBottom = 20f
                    val chartWidth = width - paddingLeft
                    val chartHeight = height - paddingBottom

                    // Draw grid lines
                    for (i in 0..4) {
                        val y = chartHeight * (1f - i / 4f)
                        drawLine(
                            color = Color(0xFF334155),
                            start = Offset(paddingLeft, y),
                            end = Offset(width, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    }

                    // Max Drawdown threshold line
                    val limitX = paddingLeft + chartWidth * (maxDrawdownLimitPct / 15.0).coerceIn(0.0, 1.0).toFloat()
                    drawLine(
                        color = Color(0xFFEF4444),
                        start = Offset(limitX, 0f),
                        end = Offset(limitX, chartHeight),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                    )

                    // Draw curve
                    val curvePath = Path()
                    val steps = 50
                    for (i in 0..steps) {
                        val dd = (i.toDouble() / steps) * 15.0
                        val scale = (1.0 - Math.pow(dd / maxDrawdownLimitPct, 1.5)).coerceIn(0.0, 1.0)
                        val x = paddingLeft + (chartWidth * (i.toFloat() / steps))
                        val y = chartHeight * (1f - scale.toFloat())

                        if (i == 0) {
                            curvePath.moveTo(x, y)
                        } else {
                            curvePath.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = curvePath,
                        color = Color(0xFF38BDF8),
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )

                    // Active operating point
                    val activeDDX = paddingLeft + chartWidth * (currentDrawdownPct / 15.0).coerceIn(0.0, 1.0).toFloat()
                    val activeScaleY = chartHeight * (1f - adaptivePositionScaleFactor.coerceIn(0.0, 1.0).toFloat())

                    drawLine(
                        color = Color(0xFFF59E0B),
                        start = Offset(activeDDX, activeScaleY),
                        end = Offset(activeDDX, chartHeight),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    )

                    drawCircle(
                        color = Color(0xFFF59E0B),
                        radius = 7f,
                        center = Offset(activeDDX, activeScaleY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = Offset(activeDDX, activeScaleY)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF38BDF8), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scaling Curve", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFEF4444), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Max Limit (${maxDrawdownLimitPct.toInt()}%)", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFF59E0B), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Active DD (${currentDrawdownPct}%)", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}

@Composable
fun DrawdownAlertNotificationModule(
    prot: com.example.aza.strategy.MaxDrawdownProtectionState,
    onSimulateDrawdown: (Double) -> Unit
) {
    var logs by remember { mutableStateOf(prot.alertActionLogs) }
    var actionBannerMessage by remember { mutableStateOf<String?>(null) }
    var showPredictiveDetails by remember { mutableStateOf(true) }

    val isBreached = prot.currentDrawdownPct >= prot.maxDrawdownLimitPct
    val isWarning = prot.currentDrawdownPct >= (prot.maxDrawdownLimitPct * 0.75)
    val breachMagnitude = if (isBreached) prot.currentDrawdownPct - prot.maxDrawdownLimitPct else 0.0

    // Dynamic Predictive Forecast computation based on current drawdown
    val forecast = prot.predictiveForecast
    val dynamicPredictedDD = (prot.currentDrawdownPct * 1.18).coerceAtMost(19.5)
    val dynamicBreachProb = when {
        isBreached -> 99.8
        isWarning -> 82.5
        prot.currentDrawdownPct > 5.0 -> 45.0
        else -> 12.0
    }
    val dynamicMinutesToBreach = when {
        isBreached -> 0
        isWarning -> 45
        else -> 320
    }

    val severityLabel = when {
        isBreached -> "CRITICAL BREACH"
        isWarning -> "HIGH RISK WARNING"
        else -> "GUARDED STABLE"
    }

    val severityBgColor = when {
        isBreached -> Color(0xFF7F1D1D)
        isWarning -> Color(0xFF78350F)
        else -> Color(0xFF064E3B)
    }

    val severityBorderColor = when {
        isBreached -> Color(0xFFEF4444)
        isWarning -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val severityTextColor = when {
        isBreached -> Color(0xFFF87171)
        isWarning -> Color(0xFFFBBF24)
        else -> Color(0xFF34D399)
    }

    Surface(
        color = severityBgColor.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, severityBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isBreached) Icons.Default.Warning else Icons.Default.Shield,
                        contentDescription = null,
                        tint = severityTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MAX DRAWDOWN ALERT & PREDICTIVE ENGINE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    color = severityBorderColor.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, severityBorderColor)
                ) {
                    Text(
                        text = severityLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = severityTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Real-Time Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Drawdown vs Limit",
                    value = "${prot.currentDrawdownPct}% / ${prot.maxDrawdownLimitPct}%",
                    subtitle = if (isBreached) "+${String.format(Locale.US, "%.1f", breachMagnitude)}% Breach" else "Safe Room: ${String.format(Locale.US, "%.1f", prot.maxDrawdownLimitPct - prot.currentDrawdownPct)}%",
                    valueColor = severityTextColor,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Pos Sizing Scale",
                    value = "${(prot.adaptivePositionScaleFactor * 100).toInt()}%",
                    subtitle = "Adaptive Guard",
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Cap at Risk",
                    value = "$${prot.maxAllowedContractsUSD.toInt()}",
                    subtitle = "Margin Shield",
                    valueColor = Color(0xFF4ADE80),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Alert Status Summary Message
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = when {
                            isBreached -> "🚨 CRITICAL THRESHOLD BREACH: Current portfolio drawdown of ${prot.currentDrawdownPct}% has breached the ${prot.maxDrawdownLimitPct}% maximum limit by +${String.format(Locale.US, "%.1f", breachMagnitude)}%. Automatic position scale reduced to ${(prot.adaptivePositionScaleFactor * 100).toInt()}%."
                            isWarning -> "⚠️ HIGH RISK WARNING: Current drawdown of ${prot.currentDrawdownPct}% is approaching maximum threshold (${prot.maxDrawdownLimitPct}%). Position sizing proactively scaled down."
                            else -> "🛡️ GUARDED NORMAL: Portfolio drawdown (${prot.currentDrawdownPct}%) is well within safe operating limits (< ${prot.maxDrawdownLimitPct}%)."
                        },
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PREDICTIVE ANALYTICS ENGINE MODULE
            Surface(
                color = Color(0xFF1E1B4B).copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF6366F1))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔮 PREDICTIVE BREACH ANALYTICS (ML)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFA5B4FC)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF4338CA),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "LSTM-Ensemble v4",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE0E7FF),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showPredictiveDetails = !showPredictiveDetails },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (showPredictiveDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle predictive details",
                                tint = Color(0xFFA5B4FC)
                            )
                        }
                    }

                    if (showPredictiveDetails) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Projected 24h Drawdown", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        "${String.format(Locale.US, "%.1f", dynamicPredictedDD)}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dynamicPredictedDD >= prot.maxDrawdownLimitPct) Color(0xFFF87171) else Color(0xFFFBBF24)
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Breach Probability", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        "${String.format(Locale.US, "%.1f", dynamicBreachProb)}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dynamicBreachProb > 70) Color(0xFFEF4444) else Color(0xFF38BDF8)
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Est. Time to Breach", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        if (dynamicMinutesToBreach == 0) "BREACHED NOW" else "~${dynamicMinutesToBreach}m",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // MARKET SENTIMENT & NLP NEWS FEED INTEGRATION CARD
                        val sentiment = forecast.sentimentImpact
                        var simulatedSentimentBias by remember { mutableStateOf(-0.68) }

                        val dynamicSentimentScore = when {
                            isBreached -> -0.88
                            isWarning -> -0.68
                            else -> simulatedSentimentBias
                        }

                        val sentimentLabel = when {
                            dynamicSentimentScore <= -0.6 -> "EXTREME FEAR / BEARISH (-${(Math.abs(dynamicSentimentScore) * 100).toInt()}%)"
                            dynamicSentimentScore < -0.2 -> "MODERATE BEARISH (-${(Math.abs(dynamicSentimentScore) * 100).toInt()}%)"
                            dynamicSentimentScore <= 0.2 -> "NEUTRAL MARKET SENTIMENT"
                            else -> "BULLISH / GREED (+${(dynamicSentimentScore * 100).toInt()}%)"
                        }

                        val sentimentColor = when {
                            dynamicSentimentScore <= -0.5 -> Color(0xFFEF4444)
                            dynamicSentimentScore < 0.0 -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "📰 FINANCIAL NEWS & SOCIAL SENTIMENT NLP",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                    Text(
                                        text = sentimentLabel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = sentimentColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Sentiment Meter Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                                ) {
                                    val normalizedPos = ((dynamicSentimentScore + 1.0) / 2.0).coerceIn(0.0, 1.0).toFloat()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = normalizedPos)
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF10B981))
                                                ),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("News Sentiment Index", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${String.format(Locale.US, "%.2f", sentiment.newsSentimentIndex)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Social Media Index", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${String.format(Locale.US, "%.2f", sentiment.socialMediaSentimentIndex)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Risk Multiplier", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${sentiment.sentimentRiskMultiplier}x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC7D2FE))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "📰 Market-Moving News Headlines:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                sentiment.newsHeadlines.forEach { headline ->
                                    Text("• $headline", fontSize = 8.5.sp, color = Color(0xFFCBD5E1), modifier = Modifier.padding(vertical = 1.dp))
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "🔥 Live Social Media Signals (X / Reddit / FinTwit):",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                sentiment.socialTrends.forEach { trend ->
                                    Text("• $trend", fontSize = 8.5.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(vertical = 1.dp))
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Simulate News Feed Bias:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = simulatedSentimentBias == -0.85,
                                        onClick = { simulatedSentimentBias = -0.85 },
                                        label = { Text("Crisis (-0.85)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = simulatedSentimentBias == 0.0,
                                        onClick = { simulatedSentimentBias = 0.0 },
                                        label = { Text("Neutral (0.0)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = simulatedSentimentBias == 0.65,
                                        onClick = { simulatedSentimentBias = 0.65 },
                                        label = { Text("Bullish (+0.65)", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // TECHNICAL INDICATORS & CANDLESTICK PATTERN RECOGNITION CARD
                        val tech = forecast.technicalIndicators
                        var selectedTechCondition by remember { mutableStateOf("OVERBOUGHT") }

                        val currentBBUpper = if (selectedTechCondition == "OVERSOLD") 3250.00 else 3450.50
                        val currentBBMiddle = 3380.00
                        val currentBBLower = if (selectedTechCondition == "OVERSOLD") 3100.00 else 3309.50
                        val currentPercentB = if (selectedTechCondition == "OVERSOLD") 0.12 else 0.88
                        val currentStochK = if (selectedTechCondition == "OVERSOLD") 14.2 else 84.6
                        val currentStochD = if (selectedTechCondition == "OVERSOLD") 18.5 else 79.2

                        val activeCandlestickPatterns = if (selectedTechCondition == "OVERSOLD") {
                            listOf(
                                com.example.aza.strategy.CandlestickPatternMatch(
                                    patternName = "Bullish Morning Star",
                                    patternType = "BULLISH_REVERSAL",
                                    confidencePct = 91.2,
                                    description = "3-candle bullish reversal formation at Lower Bollinger Band support.",
                                    candleCount = 3
                                ),
                                com.example.aza.strategy.CandlestickPatternMatch(
                                    patternName = "Hammer",
                                    patternType = "BULLISH_REVERSAL",
                                    confidencePct = 82.0,
                                    description = "Long lower shadow > 2x body length at oversold extreme.",
                                    candleCount = 1
                                )
                            )
                        } else {
                            tech.detectedCandlestickPatterns
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "📈 TECHNICAL INDICATORS & CANDLESTICK PATTERNS",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                    Text(
                                        text = if (selectedTechCondition == "OVERSOLD") "OVERSOLD BOUNCE ZONE" else "OVERBOUGHT REVERSAL WATCH",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTechCondition == "OVERSOLD") Color(0xFF10B981) else Color(0xFFEF4444)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Indicator Grid Row 1: Bollinger Bands & Stochastic Oscillator
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("📊 Bollinger Bands (20, 2.0)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Upper Band: $${String.format(Locale.US, "%.2f", currentBBUpper)}", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("Middle Band: $${String.format(Locale.US, "%.2f", currentBBMiddle)}", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("Lower Band: $${String.format(Locale.US, "%.2f", currentBBLower)}", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("%B Ratio: ${String.format(Locale.US, "%.2f", currentPercentB)} (${if (currentPercentB > 0.8) "Upper Touch" else "Lower Touch"})", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (currentPercentB > 0.8) Color(0xFFF87171) else Color(0xFF4ADE80))
                                        }
                                    }

                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("⚡ Stochastic Oscillator (14, 3)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("%K Line: ${String.format(Locale.US, "%.1f", currentStochK)}", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (currentStochK > 80) Color(0xFFEF4444) else if (currentStochK < 20) Color(0xFF10B981) else Color(0xFFE2E8F0))
                                            Text("%D Line: ${String.format(Locale.US, "%.1f", currentStochD)}", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                            Text("RSI (14): ${tech.rsi14Value}", fontSize = 8.sp, color = Color(0xFFCBD5E1))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("State: ${if (currentStochK > 80) "Overbought (>80)" else "Oversold (<20)"}", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (currentStochK > 80) Color(0xFFFBBF24) else Color(0xFF34D399))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Candlestick Pattern Recognition List
                                Text(
                                    text = "🕯️ Automated Candlestick Pattern Detection:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                activeCandlestickPatterns.forEach { pattern ->
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(pattern.patternName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (pattern.patternType.contains("BEARISH")) Color(0xFFF87171) else Color(0xFF4ADE80))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(color = Color(0xFF334155), shape = RoundedCornerShape(3.dp)) {
                                                        Text(pattern.patternType, fontSize = 7.5.sp, color = Color(0xFFCBD5E1), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                    }
                                                }
                                                Text(pattern.description, fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            }
                                            Text("${pattern.confidencePct}% Conf", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Interactive Simulator Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Simulate Pattern:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = selectedTechCondition == "OVERBOUGHT",
                                        onClick = { selectedTechCondition = "OVERBOUGHT" },
                                        label = { Text("Overbought Reversal", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedTechCondition == "OVERSOLD",
                                        onClick = { selectedTechCondition = "OVERSOLD" },
                                        label = { Text("Oversold Bounce", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // FUNDAMENTAL ANALYSIS DATA CARD (EARNINGS, MACRO, & INTRINSIC VALUATION)
                        val fundamental = forecast.fundamentalAnalysis
                        var macroMode by remember { mutableStateOf("BEARISH_RATE_PRESSURE") }
                        var selectedApiSource by remember { mutableStateOf("Alpha Vantage") }

                        val activeMacroBias = if (macroMode == "BULLISH_EXPANSION") "BULLISH_EXPANSION" else fundamental.macroRiskBias
                        val activeMacroSummary = if (macroMode == "BULLISH_EXPANSION") {
                            "🏛️ Fundamental Macro tailwinds ($selectedApiSource API): Rate cuts anticipated & Tech Q2 EPS beats align market price closer to Intrinsic Value."
                        } else {
                            fundamental.macroSummary
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🏛️ FUNDAMENTAL ANALYSIS & INTRINSIC VALUATION",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF34D399)
                                        )
                                    }
                                    Surface(
                                        color = if (activeMacroBias.contains("BEARISH")) Color(0xFF7F1D1D) else Color(0xFF065F46),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "$selectedApiSource | $activeMacroBias",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Valuation Sub-card: DCF Intrinsic Value vs Market Price
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("DCF Intrinsic Fair Value", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("$${String.format(Locale.US, "%.2f", fundamental.intrinsicFairValueDcf)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34D399))
                                        }
                                        Column {
                                            Text("Current Market Price", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("$${String.format(Locale.US, "%.2f", fundamental.currentMarketPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                        }
                                        Column {
                                            Text("Valuation Premium", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("+7.3% (Overvalued)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                        }
                                        Column {
                                            Text("Dividend Yield", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("${fundamental.dividendYieldPct}% Annual", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("US Core PCE", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${fundamental.cpiInflationRatePct}%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Fed Policy Rate", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${fundamental.centralBankPolicyRatePct}%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Earnings Surprise", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text("${fundamental.earningsSurpriseIndex}%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (fundamental.earningsSurpriseIndex < 0) Color(0xFFEF4444) else Color(0xFF10B981))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "📊 Live Financial API Statements & Corporate Events ($selectedApiSource):",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                fundamental.fundamentalMetrics.forEach { metric ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(metric.name, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(3.dp)) {
                                                    Text(metric.category, fontSize = 7.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                                }
                                            }
                                            Text("Actual: ${metric.currentValue} (Exp: ${metric.expectedValue}) | Released: ${metric.releaseDate}", fontSize = 8.sp, color = Color(0xFF64748B))
                                        }
                                        Surface(
                                            color = if (metric.impactRating.contains("BEARISH")) Color(0xFF991B1B) else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(3.dp)
                                        ) {
                                            Text(metric.impactRating, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFECACA), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(activeMacroSummary, fontSize = 8.5.sp, color = Color(0xFFA7F3D0))

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("API Provider Source:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = selectedApiSource == "Alpha Vantage",
                                        onClick = { selectedApiSource = "Alpha Vantage" },
                                        label = { Text("Alpha Vantage", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedApiSource == "Quandl / Nasdaq Data",
                                        onClick = { selectedApiSource = "Quandl / Nasdaq Data" },
                                        label = { Text("Quandl Data", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // DCF MODEL COMPARISON LAYER & PREDICTIVE BREACH ANALYTICS INTEGRATION CARD
                        val dcfState = forecast.dcfModelComparison
                        var waccSensitivityMode by remember { mutableStateOf("BASE_8_5") }

                        val activeWaccMultiplier = when (waccSensitivityMode) {
                            "HAWKISH_9_0" -> 1.05
                            "DOVISH_8_0" -> 0.95
                            else -> 1.0
                        }

                        val activeConsensusFairValue = dcfState.consensusIntrinsicFairValue / activeWaccMultiplier
                        val activeValuationGapPct = ((dcfState.benchmarkMarketPrice - activeConsensusFairValue) / activeConsensusFairValue) * 100.0
                        val dcfBreachProbabilityBoostPct = if (activeValuationGapPct > 0) activeValuationGapPct * 1.35 else 0.0
                        val activeBreachProbabilityPct = (forecast.probabilityOfBreachPct + (dcfBreachProbabilityBoostPct - 11.2)).coerceIn(50.0, 99.9)

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "📊 DCF MODEL COMPARISON & BREACH ANALYTICS",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                    Surface(
                                        color = if (activeValuationGapPct > 5.0) Color(0xFF991B1B) else Color(0xFF065F46),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Breach Risk: ${String.format(Locale.US, "%.1f", activeBreachProbabilityPct)}% (+${String.format(Locale.US, "%.1f", dcfBreachProbabilityBoostPct)}% DCF Impact)",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Consensus DCF Target", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("$${String.format(Locale.US, "%.2f", activeConsensusFairValue)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF38BDF8))
                                        }
                                        Column {
                                            Text("Market Price", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text("$${String.format(Locale.US, "%.2f", dcfState.benchmarkMarketPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                        }
                                        Column {
                                            Text("Valuation Premium", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                            Text(
                                                text = "${if (activeValuationGapPct > 0) "+" else ""}${String.format(Locale.US, "%.1f", activeValuationGapPct)}%",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (activeValuationGapPct > 0) Color(0xFFF87171) else Color(0xFF34D399)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "📑 Multi-Model DCF Valuation Comparison Matrix:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    dcfState.dcfModels.forEach { model ->
                                        val modelFairVal = model.intrinsicValue / activeWaccMultiplier
                                        val modelGap = ((dcfState.benchmarkMarketPrice - modelFairVal) / modelFairVal) * 100.0

                                        Surface(
                                            color = Color(0xFF1E293B),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(model.modelName, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                                    Text(
                                                        "WACC: ${String.format(Locale.US, "%.1f", model.discountRateWaccPct * activeWaccMultiplier)}% | Term Growth: ${model.terminalGrowthRatePct}% | Target: $${String.format(Locale.US, "%.2f", modelFairVal)}",
                                                        fontSize = 7.5.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Surface(
                                                        color = if (modelGap > 10.0) Color(0xFF991B1B) else if (modelGap > 0) Color(0xFF854D0E) else Color(0xFF065F46),
                                                        shape = RoundedCornerShape(3.dp)
                                                    ) {
                                                        Text(
                                                            text = "${if (modelGap > 0) "+" else ""}${String.format(Locale.US, "%.1f", modelGap)}%",
                                                            fontSize = 7.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                    Text("Breach Shift: +${String.format(Locale.US, "%.1f", model.impliedBreachProbabilityShiftPct)}%", fontSize = 7.5.sp, color = Color(0xFFBAE6FD))
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(dcfState.dcfBreachAnalyticsIntegrationSummary, fontSize = 8.5.sp, color = Color(0xFFBAE6FD))

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("WACC Stress Sensitivity:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = waccSensitivityMode == "BASE_8_5",
                                        onClick = { waccSensitivityMode = "BASE_8_5" },
                                        label = { Text("Base WACC (8.5%)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = waccSensitivityMode == "HAWKISH_9_0",
                                        onClick = { waccSensitivityMode = "HAWKISH_9_0" },
                                        label = { Text("Hawkish WACC (9.0%)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = waccSensitivityMode == "DOVISH_8_0",
                                        onClick = { waccSensitivityMode = "DOVISH_8_0" },
                                        label = { Text("Dovish WACC (8.0%)", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // NLP NEWS & SOCIAL MEDIA SENTIMENT ANALYSIS CARD (GEMINI 3.5 FLASH ENGINE)
                        val nlp = forecast.nlpSentiment
                        var nlpSimMode by remember { mutableStateOf("HAWKISH_BEARISH") }
                        var streamFilter by remember { mutableStateOf("ALL_STREAMS") }

                        val activeNlpScore = when (nlpSimMode) {
                            "DOVISH_BULLISH" -> 0.58
                            "NEUTRAL_BALANCED" -> 0.05
                            else -> nlp.aggregateSentimentIndex // -0.64
                        }

                        val activeNlpLabel = when (nlpSimMode) {
                            "DOVISH_BULLISH" -> "BULLISH_MACRO_TAILWINDS (+0.58)"
                            "NEUTRAL_BALANCED" -> "BALANCED_MACRO_SENTIMENT (+0.05)"
                            else -> "BEARISH_MACRO_HEADWINDS (-0.64)"
                        }

                        val activeArticles = when (nlpSimMode) {
                            "DOVISH_BULLISH" -> listOf(
                                com.example.aza.strategy.NlpNewsSentimentArticle(
                                    headline = "Central Bank Hints at Early Rate Cuts Following Cooling Inflation Data",
                                    source = "Financial Times",
                                    channelType = "NEWS_FEED",
                                    publishedAgo = "10m ago",
                                    sentimentScore = 0.78,
                                    sentimentLabel = "BULLISH",
                                    keyEntity = "Central Bank Policy",
                                    nlpConfidencePct = 96.2,
                                    summarySnippet = "Dovish monetary tone provides strong tailwind for risk assets and growth stocks."
                                ),
                                com.example.aza.strategy.NlpNewsSentimentArticle(
                                    headline = "#FedRateCut & #MarketRally trending as institutional buyers re-enter",
                                    source = "X / FinTwit Feed",
                                    channelType = "SOCIAL_MEDIA_STREAM",
                                    publishedAgo = "5m ago",
                                    sentimentScore = 0.81,
                                    sentimentLabel = "BULLISH",
                                    keyEntity = "Social Sentiment Surge",
                                    nlpConfidencePct = 93.8,
                                    summarySnippet = "Retail & institutional sentiment flipped bullish following dovish Fed comments."
                                )
                            )
                            else -> nlp.articles
                        }.filter {
                            when (streamFilter) {
                                "NEWS_ONLY" -> it.channelType == "NEWS_FEED"
                                "SOCIAL_ONLY" -> it.channelType != "NEWS_FEED"
                                else -> true
                            }
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🧠 GEMINI NLP NEWS & SOCIAL SENTIMENT EARLY WARNING",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFE879F9)
                                        )
                                    }
                                    Surface(
                                        color = if (activeNlpScore < -0.2) Color(0xFF991B1B) else if (activeNlpScore > 0.2) Color(0xFF065F46) else Color(0xFF334155),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = activeNlpLabel,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Early Warning Alert Banner
                                Surface(
                                    color = if (activeNlpScore < -0.2) Color(0xFF450A0A) else Color(0xFF064E3B),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (activeNlpScore < -0.2) Color(0xFFDC2626) else Color(0xFF059669)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (nlpSimMode == "DOVISH_BULLISH") "✅ EARLY WARNING SYSTEM: Positive sentiment momentum (+0.58) suppresses immediate breach risk." else nlp.earlyWarningBreachAlert,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeNlpScore < -0.2) Color(0xFFFCA5A5) else Color(0xFFA7F3D0),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // News vs Social Breakdown
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("News Feed Score: ${nlp.newsSentimentScore}", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text("Social Media Score: ${nlp.socialMediaSentimentScore}", fontSize = 8.sp, color = Color(0xFF38BDF8))
                                    Text("Social Vol Surge: ${nlp.socialMediaVolumeSpikePct}%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Sentiment Score Bar (-1.0 to +1.0)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Aggregate NLP Sentiment Index", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = "${if (activeNlpScore > 0) "+" else ""}${String.format(Locale.US, "%.2f", activeNlpScore)} / 1.00",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeNlpScore < 0) Color(0xFFF87171) else Color(0xFF34D399)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                                ) {
                                    val normalFraction = ((activeNlpScore + 1.0) / 2.0).coerceIn(0.0, 1.0).toFloat()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = normalFraction)
                                            .background(
                                                color = if (activeNlpScore < 0) Color(0xFFEF4444) else Color(0xFF10B981),
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📰 News & FinTwit Stream (${nlp.processedArticleCount} News / ${nlp.processedSocialPostsCount} Posts):",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))

                                activeArticles.forEach { article ->
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(article.source, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = if (article.channelType == "NEWS_FEED") Color(0xFF38BDF8) else Color(0xFFE879F9))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("• ${article.publishedAgo}", fontSize = 7.5.sp, color = Color(0xFF64748B))
                                                }
                                                Surface(
                                                    color = if (article.sentimentScore < 0) Color(0xFF7F1D1D) else Color(0xFF065F46),
                                                    shape = RoundedCornerShape(3.dp)
                                                ) {
                                                    Text(
                                                        text = "${article.sentimentLabel} (${article.sentimentScore})",
                                                        fontSize = 7.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(article.headline, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                                            Text(article.summarySnippet, fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Channel:", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = streamFilter == "ALL_STREAMS",
                                        onClick = { streamFilter = "ALL_STREAMS" },
                                        label = { Text("All Streams", fontSize = 7.5.sp) }
                                    )
                                    FilterChip(
                                        selected = streamFilter == "NEWS_ONLY",
                                        onClick = { streamFilter = "NEWS_ONLY" },
                                        label = { Text("News Only", fontSize = 7.5.sp) }
                                    )
                                    FilterChip(
                                        selected = streamFilter == "SOCIAL_ONLY",
                                        onClick = { streamFilter = "SOCIAL_ONLY" },
                                        label = { Text("Social Only", fontSize = 7.5.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // GRAPH NEURAL NETWORK (GNN) KNOWLEDGE GRAPH CARD
                        val gnn = forecast.gnnNetworkState
                        var gnnSimMode by remember { mutableStateOf("MACRO_SHOCK") }

                        val activeGnnRiskScore = if (gnnSimMode == "SUPPLY_RECOVERY") 0.32 else gnn.propagationRiskScore
                        val activeGnnSummary = if (gnnSimMode == "SUPPLY_RECOVERY") {
                            "🕸️ GNN GRAPH RIPPLE PATTERN: Supply chain bottleneck easing (+0.82) mitigates systemic contagion risk across semiconductor nodes."
                        } else {
                            gnn.hiddenPatternSummary
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🕸️ GRAPH NEURAL NETWORK (GNN) RISK PROPAGATION",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFF43F5E)
                                        )
                                    }
                                    Surface(
                                        color = if (activeGnnRiskScore > 0.6) Color(0xFF991B1B) else Color(0xFF065F46),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "GNN Risk Score: ${String.format(Locale.US, "%.2f", activeGnnRiskScore)}",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Graph Topology: ${gnn.nodeCount} Nodes, ${gnn.edgeCount} Edges", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text("Engine: ${gnn.modelEngine}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "🔗 Key Graph Nodes & Cross-Asset Centrality:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    gnn.nodes.take(4).forEach { node ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 1.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Text("• ${node.name}", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(3.dp)) {
                                                    Text(node.type, fontSize = 7.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                                }
                                            }
                                            Text("${node.currentMetric} (Centrality ${node.centralityScore})", fontSize = 8.sp, color = Color(0xFFF43F5E))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "⚡ GNN Directed Edges & Correlation Propagation Paths:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                gnn.edges.forEach { edge ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${edge.sourceNodeId.removePrefix("NODE_")} ➔ ${edge.targetNodeId.removePrefix("NODE_")} (${edge.relationshipType})", fontSize = 8.sp, color = Color(0xFFCBD5E1))
                                        Text(
                                            text = "${if (edge.weight > 0) "+" else ""}${edge.weight} Weight",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (edge.weight < 0) Color(0xFFF87171) else Color(0xFF34D399)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(activeGnnSummary, fontSize = 8.5.sp, color = Color(0xFFFECDD3))

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("GNN Network Simulation:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = gnnSimMode == "MACRO_SHOCK",
                                        onClick = { gnnSimMode = "MACRO_SHOCK" },
                                        label = { Text("Macro Propagation (0.79 Risk)", fontSize = 8.sp) }
                                    )
                                     FilterChip(
                                        selected = gnnSimMode == "SUPPLY_RECOVERY",
                                        onClick = { gnnSimMode = "SUPPLY_RECOVERY" },
                                        label = { Text("Supply Chain Easing (0.32 Risk)", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // KNOWLEDGE GRAPH REASONING ENGINE CARD
                        val kg = forecast.knowledgeGraphReasoning
                        var kgFilterDomain by remember { mutableStateOf("ALL_DOMAINS") }

                        val activeTriples = if (kgFilterDomain == "PORTFOLIO_ONLY") {
                            kg.activeInferenceChain.filter { it.insightDomain.contains("PORTFOLIO") || it.insightDomain.contains("EQUITY") }
                        } else {
                            kg.activeInferenceChain
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "💡 KNOWLEDGE GRAPH REASONING ENGINE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFFACC15)
                                        )
                                    }
                                    Surface(color = Color(0xFF854D0E), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "${kg.totalEntitiesMapped} Entities | ${kg.totalTriplesInferred} Triples",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFEF08A),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Ontology: ${kg.kgOntologyVersion}", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text("Multi-Hop Reasoning Active", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFDE047))
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "🔗 Semantic Knowledge Triples & Inferred Relations:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    activeTriples.forEach { triple ->
                                        Surface(
                                            color = Color(0xFF1E293B),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(triple.subjectEntity, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Surface(color = Color(0xFF334155), shape = RoundedCornerShape(3.dp)) {
                                                            Text(triple.predicateRelation, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFDE047), modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                                        }
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(triple.objectEntity, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                                                    }
                                                    Text("Domain: ${triple.insightDomain}", fontSize = 7.5.sp, color = Color(0xFF64748B))
                                                }
                                                Text("${triple.reasoningConfidencePct}% Conf", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(kg.reasoningInsightSummary, fontSize = 8.5.sp, color = Color(0xFFFEF08A))

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("KG Subgraph Filter:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = kgFilterDomain == "ALL_DOMAINS",
                                        onClick = { kgFilterDomain = "ALL_DOMAINS" },
                                        label = { Text("Full Macro Chain (4 Triples)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = kgFilterDomain == "PORTFOLIO_ONLY",
                                        onClick = { kgFilterDomain = "PORTFOLIO_ONLY" },
                                        label = { Text("Equity & Portfolio Triples", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // MULTI-TIMEFRAME CONFIRMATION (MTFC) VALIDATION ENGINE CARD
                        val mtfc = forecast.mtfcConfirmation
                        var mtfcSimMode by remember { mutableStateOf("HIGH_CONFLUENCE") }

                        val alignmentPct = if (mtfcSimMode == "HIGH_CONFLUENCE") 87.5 else 42.0
                        val isMtfcPassed = alignmentPct >= mtfc.minRequiredAlignmentPct
                        val mtfcStatusText = if (isMtfcPassed) "PASSED (${alignmentPct}% CONFLUENCE)" else "REJECTED (${alignmentPct}% - WEAK SIGNAL)"
                        val mtfcStatusColor = if (isMtfcPassed) Color(0xFF10B981) else Color(0xFFEF4444)

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🎯 MULTI-TIMEFRAME CONFIRMATION (MTFC)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF818CF8)
                                        )
                                    }
                                    Text(
                                        text = mtfcStatusText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = mtfcStatusColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // MTFC Confluence Progress Meter
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = (alignmentPct / 100.0).toFloat())
                                            .background(
                                                color = if (isMtfcPassed) Color(0xFF818CF8) else Color(0xFFEF4444),
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Timeframe Detail Matrix
                                mtfc.timeframeDetails.forEach { (tf, desc) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("• $tf", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                        Text(desc, fontSize = 8.sp, color = if (isMtfcPassed) Color(0xFF94A3B8) else Color(0xFFF87171))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (isMtfcPassed)
                                        "✅ MTFC VALIDATED: Signal aligned across 1D macro, 4H intermediate, 1H short-term, and 15m entry timeframes."
                                    else
                                        "⚠️ MTFC FILTERED: Multi-timeframe conflict detected (1D macro trend contradicts 15m signal). Weak signal quarantined.",
                                    fontSize = 8.5.sp,
                                    color = Color(0xFFC7D2FE)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Simulate Signal Confluence:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                                    FilterChip(
                                        selected = mtfcSimMode == "HIGH_CONFLUENCE",
                                        onClick = { mtfcSimMode = "HIGH_CONFLUENCE" },
                                        label = { Text("87.5% Aligned (Pass)", fontSize = 8.sp) }
                                    )
                                    FilterChip(
                                        selected = mtfcSimMode == "WEAK_SIGNAL",
                                        onClick = { mtfcSimMode = "WEAK_SIGNAL" },
                                        label = { Text("42% Conflict (Filter)", fontSize = 8.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // PORTFOLIO VARIANT REBALANCING CARD (60-20-20 SPLIT)
                        val portfolio = forecast.portfolioRebalancing
                        var activeSplitMode by remember { mutableStateOf("60_20_20") }

                        val currentA2Weight = if (activeSplitMode == "60_20_20") 60.0 else 50.0
                        val currentAWeight = if (activeSplitMode == "60_20_20") 20.0 else 30.0
                        val currentCWeight = 20.0

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "⚖️ PORTFOLIO REBALANCING (60-20-20 SPLIT)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                    Surface(color = Color(0xFF0284C7), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "Sharpe 2.08 | Max DD 9.8%",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Variant Weight Distributions
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Variant A2 (60%)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {

Text("Variant A2 (Champion)", fontSize = 8.5.sp, color = Color.White)
                                        Text("" + currentA2Weight.toInt() + "%", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                    }
                                    // Variant A (20%)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Variant A (Breakout)", fontSize = 8.5.sp, color = Color.White)
                                        Text("" + currentAWeight.toInt() + "%", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                    }
                                    // Variant C (20%)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Variant C (Scalper)", fontSize = 8.5.sp, color = Color.White)
                                        Text("" + currentCWeight.toInt() + "%", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
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
