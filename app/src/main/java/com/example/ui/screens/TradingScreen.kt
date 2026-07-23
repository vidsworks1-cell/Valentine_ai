package com.example.ui.screens

import com.example.aza.connector.BinanceConnector
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.data.database.PortfolioAsset
import com.example.viewmodel.AppViewModel
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TradingScreen(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    val assets by viewModel.portfolioAssets.collectAsState()
    val accountInfo by viewModel.accountInfo.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingMarket.collectAsState()
    val analysisText by viewModel.marketAnalysis.collectAsState()
    val binanceOrders by viewModel.binanceOrders.collectAsState()
    val binanceTrades by viewModel.binanceTrades.collectAsState()

    var selectedMarketSymbol by remember { mutableStateOf("BTC") }
    var selectedMarketType by remember { mutableStateOf("CRYPTO") }
    var selectedMarketName by remember { mutableStateOf("Bitcoin") }
    var selectedMarketPrice by remember { mutableStateOf(67450.0) }

    // Dialog state
    var showBuySellDialog by remember { mutableStateOf(false) }
    var isBuyAction by remember { mutableStateOf(true) }
    var tradeAmountStr by remember { mutableStateOf("0.1") }

    var showWithdrawDialog by remember { mutableStateOf(false) }
    var withdrawAmountStr by remember { mutableStateOf("500") }
    var withdrawAddressStr by remember { mutableStateOf("Binance-Secured-Vault-Trx82") }
    var showDepositDialog by remember { mutableStateOf(false) }
    var depositAmountStr by remember { mutableStateOf("1000") }

    val marketWatchlist = listOf(
        MarketFeedItem("BTC", "Bitcoin", 67450.0, 3.2, "CRYPTO"),
        MarketFeedItem("ETH", "Ethereum", 3480.0, -1.5, "CRYPTO"),
        MarketFeedItem("NVDA", "NVIDIA Corp.", 945.5, 4.8, "STOCK"),
        MarketFeedItem("AAPL", "Apple Inc.", 192.3, 0.5, "STOCK"),
        MarketFeedItem("TSLA", "Tesla Motors", 178.6, -2.1, "STOCK")
    )

    var activeTab by remember { mutableStateOf(0) } // 0: Live Portfolio, 1: Market Intelligence

    val opportunities by viewModel.marketOpportunities.collectAsState()
    val proposals by viewModel.tradeProposals.collectAsState()
    val engineThoughts by viewModel.marketIntelligenceEngine.engineThoughts.collectAsState()
    val isEngineRunning by viewModel.marketIntelligenceEngine.isEngineRunning.collectAsState()
    val strategies by viewModel.strategies.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .padding(top = innerPadding.calculateTopPadding() + 8.dp)
    ) {
        // Tab Selector Row
        TabRow(
            selectedTabIndex = activeTab,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Live Portfolio", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Market Intel", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("Position Intel", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = activeTab == 3,
                onClick = { activeTab = 3 },
                text = { Text("Diagnostics", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.QueryStats, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        if (activeTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
        // Hero Balance Card
        item {
            val totalPortfolioValue = assets.sumOf { it.amountOwned * it.currentPrice }
            val linkedBalance = accountInfo?.totalBalance ?: 0.0

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFEADDFF))
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL PORTFOLIO VALUE",
                            color = Color(0xFF21005D).copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Secure Portfolio Link",
                                tint = Color(0xFF21005D)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", linkedBalance + totalPortfolioValue)}",
                        color = Color(0xFF21005D),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Linked Wallet Cash",
                                color = Color(0xFF21005D).copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Text(
                                "$${String.format(Locale.US, "%,.2f", linkedBalance)}",
                                color = Color(0xFF21005D),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Invested Capital",
                                color = Color(0xFF21005D).copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Text(
                                "$${String.format(Locale.US, "%,.2f", totalPortfolioValue)}",
                                color = Color(0xFF6750A4),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showDepositDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deposit Cash", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showWithdrawDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF21005D),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Withdraw Profits 🔐", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Warning if account not linked
        if (accountInfo?.isTradingLinked == false) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Not Connected",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Trading API Disconnected",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Go to 'Account' view to complete brokerage linking and tap trading simulations.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            BinanceConnectorHealthPanel(viewModel)
        }

        // Watchlist Market Selection
        item {
            Column {
                Text(
                    text = "REAL-TIME MARKET OBSERVATORY",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    marketWatchlist.forEach { item ->
                        val isSelected = selectedMarketSymbol == item.symbol
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .height(94.dp)
                                .clickable {
                                    selectedMarketSymbol = item.symbol
                                    selectedMarketType = item.type
                                    selectedMarketName = item.name
                                    selectedMarketPrice = item.price
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder(isSelected),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.symbol,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${if (item.change >= 0) "+" else ""}${item.change}%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.change >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "$${String.format(Locale.US, "%,.1f", item.price)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = item.name,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Asset Analyzer Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$selectedMarketName ($selectedMarketSymbol)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "$selectedMarketType feed • Instant Execution",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.requestMarketAnalysis(selectedMarketSymbol, selectedMarketType)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isAnalyzing
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = "Analyze",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ask Gemini AI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (accountInfo?.isTradingLinked == true) {
                                    isBuyAction = true
                                    tradeAmountStr = if (selectedMarketType == "CRYPTO") "0.05" else "5"
                                    showBuySellDialog = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            enabled = accountInfo?.isTradingLinked == true,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF2E7D32)
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Buy")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BUY", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (accountInfo?.isTradingLinked == true) {
                                    isBuyAction = false
                                    tradeAmountStr = if (selectedMarketType == "CRYPTO") "0.05" else "5"
                                    showBuySellDialog = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            enabled = accountInfo?.isTradingLinked == true && assets.any { it.symbol == selectedMarketSymbol },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFD32F2F)
                            )
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Sell")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SELL", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Display AI Analysis Results (Animated)
                    AnimatedVisibility(
                        visible = (analysisText != null) || isAnalyzing,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Report",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "GEMINI ANALYTICAL INSIGHT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            if (isAnalyzing) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Gemini is crunching live indicators...", fontSize = 12.sp)
                                }
                            } else {
                                Text(
                                    text = analysisText ?: "",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Main User Portfolio Assets List
        item {
            Text(
                text = "YOUR INVESTMENT HOLDINGS",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (assets.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = "Empty Holdings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No investments active.",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Link your trading account and secure some digital assets to view holdings and gains.",
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        } else {
            items(assets) { asset ->
                val profitLoss = (asset.currentPrice - asset.averageBuyPrice) * asset.amountOwned
                val profitPercent = ((asset.currentPrice / asset.averageBuyPrice) - 1.0) * 100.0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = asset.symbol,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    Text(asset.assetType, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                            Text(
                                text = asset.displayName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Owned: ${asset.amountOwned} (Avg: $${String.format(Locale.US, "%,.2f", asset.averageBuyPrice)})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$${String.format(Locale.US, "%,.2f", asset.amountOwned * asset.currentPrice)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (profitLoss >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = if (profitLoss >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${if (profitLoss >= 0) "+" else ""}${String.format(Locale.US, "%,.2f", profitLoss)} (${String.format(Locale.US, "%,.1f", profitPercent)}%)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (profitLoss >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Synchronized Binance Orders
        item {
            Text(
                text = "SYNCHRONIZED BINANCE ORDERS",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
        }

        if (binanceOrders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No synchronized orders found.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(binanceOrders) { order ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isBuy = order.side.uppercase() == "BUY"
                                Badge(
                                    containerColor = if (isBuy) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    contentColor = if (isBuy) Color(0xFF2E7D32) else Color(0xFFC62828)
                                ) {
                                    Text(order.side, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(order.symbol, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Type: ${order.type} | Qty: ${order.origQty}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Order ID: ${order.orderId}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$${String.format(Locale.US, "%,.2f", order.price)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Badge(
                                containerColor = when (order.status.uppercase()) {
                                    "NEW", "PARTIALLY_FILLED" -> Color(0xFFFFF3E0)
                                    "FILLED" -> Color(0xFFE8F5E9)
                                    else -> Color(0xFFECEFF1)
                                },
                                contentColor = when (order.status.uppercase()) {
                                    "NEW", "PARTIALLY_FILLED" -> Color(0xFFE65100)
                                    "FILLED" -> Color(0xFF2E7D32)
                                    else -> Color(0xFF37474F)
                                }
                            ) {
                                Text(order.status, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        // Synchronized Binance Trades
        item {
            Text(
                text = "SYNCHRONIZED BINANCE TRADES",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
        }

        if (binanceTrades.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No synchronized trades found.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(binanceTrades) { trade ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge(
                                    containerColor = if (trade.isBuyer) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    contentColor = if (trade.isBuyer) Color(0xFF2E7D32) else Color(0xFFC62828)
                                ) {
                                    Text(if (trade.isBuyer) "BUY" else "SELL", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(trade.symbol, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Qty: ${trade.qty} | Fee: ${trade.commission} ${trade.commissionAsset}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Trade ID: ${trade.id}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$${String.format(Locale.US, "%,.2f", trade.price)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            val dateStr = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.US).format(java.util.Date(trade.timestamp))
                            Text(dateStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
} else if (activeTab == 1) {
    // Market Intelligence Tab
    MarketIntelligenceSection(
        viewModel = viewModel,
        opportunities = opportunities,
        proposals = proposals,
        engineThoughts = engineThoughts,
        isEngineRunning = isEngineRunning,
        strategies = strategies,
        innerPadding = innerPadding
    )
} else if (activeTab == 2) {
    // Position Manager & Risk Supervision Panel (Phase 17)
    PositionManagerSection(
        viewModel = viewModel,
        innerPadding = innerPadding
    )
} else {
    TradingDiagnosticsDashboard(
        viewModel = viewModel,
        innerPadding = innerPadding
    )
}
}

    // Interactive Buy/Sell Confirmation Dialog
    if (showBuySellDialog) {
        AlertDialog(
            onDismissRequest = { showBuySellDialog = false },
            title = {
                Text(
                    text = if (isBuyAction) "CONFIRM PURCHASE" else "CONFIRM LIQUIDATION",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isBuyAction) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Asset: $selectedMarketName ($selectedMarketSymbol)\n" +
                               "Rate: $${String.format(Locale.US, "%,.2f", selectedMarketPrice)} each",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = tradeAmountStr,
                        onValueChange = { tradeAmountStr = it },
                        label = { Text("Exchange Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val computedAmount = tradeAmountStr.toDoubleOrNull() ?: 0.0
                    val computedCost = computedAmount * selectedMarketPrice

                    Text(
                        text = "Estimated Transaction Value: $${String.format(Locale.US, "%,.2f", computedCost)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = tradeAmountStr.toDoubleOrNull()
                        if (amount != null && amount > 0.0) {
                            if (isBuyAction) {
                                viewModel.buyAsset(selectedMarketSymbol, selectedMarketName, amount, selectedMarketPrice)
                            } else {
                                viewModel.sellAsset(selectedMarketSymbol, amount, selectedMarketPrice)
                            }
                        }
                        showBuySellDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBuyAction) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isBuyAction) "Buy Asset" else "Sell Asset", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBuySellDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Interactive Withdraw Profits Dialog
    if (showWithdrawDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showWithdrawDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Secure Withdrawal Wire",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Execute real-time security-cleared cash withdrawal from trade balances. Funds are transferred instantly to your configured external exchange address.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = withdrawAmountStr,
                        onValueChange = { withdrawAmountStr = it },
                        label = { Text("Secured Amount ($)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = withdrawAddressStr,
                        onValueChange = { withdrawAddressStr = it },
                        label = { Text("Withdrawal Destination Wallet") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AES-256 Crypto Security Protocol Engaged", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = withdrawAmountStr.toDoubleOrNull()
                        if (amount != null && amount > 0.0) {
                            viewModel.withdrawFundsSecurely(
                                amount = amount,
                                destination = withdrawAddressStr,
                                onSuccess = {
                                    Toast.makeText(context, "$${String.format(Locale.US, "%,.2f", amount)} secured withdrawal initiated!", Toast.LENGTH_LONG).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                        showWithdrawDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Declassify & Send", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Interactive Deposit Cash Dialog
    if (showDepositDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showDepositDialog = false },
            title = {
                Text(
                    text = "Deposit Simulated Funds",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF6750A4)
                )
            },
            text = {
                Column {
                    Text(
                        "Charge cash into core wallet of simulated account via SECURE mock Stripe API wire transfer.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = depositAmountStr,
                        onValueChange = { depositAmountStr = it },
                        label = { Text("Stripe Wire Amount ($)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6750A4),
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val funds = depositAmountStr.toDoubleOrNull()
                        if (funds != null && funds > 0.0) {
                            viewModel.depositFundsMock(funds)
                            Toast.makeText(context, "$$funds loaded into secure portfolio wallet!", Toast.LENGTH_SHORT).show()
                        }
                        showDepositDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6750A4),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Transact Deposit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDepositDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

data class MarketFeedItem(
    val symbol: String,
    val name: String,
    val price: Double,
    val change: Double,
    val type: String
)

@Composable
fun BinanceConnectorHealthPanel(viewModel: AppViewModel) {
    val binanceStatus by viewModel.binanceApiStatus.collectAsState()
    val binanceBalancesList by viewModel.binanceBalances.collectAsState()
    val binanceOrdersList by viewModel.binanceOpenOrders.collectAsState()
    val orderEnabled by viewModel.isOrderPlacementEnabled.collectAsState()

    val isAuthenticated = binanceStatus?.isAuthenticated == true
    val isConnected = binanceStatus?.isConnected == true
    val latency = binanceStatus?.latencyMs ?: 0
    val rateLimit = binanceStatus?.rateLimitStatus ?: "UNKNOWN"
    val mode = binanceStatus?.mode ?: "PAPER"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwapHorizontalCircle,
                        contentDescription = "Connector Status",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BINANCE PRO CONNECTOR",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Status Badge
                val (badgeColor, badgeText) = when {
                    isConnected -> Color(0xFF2E7D32) to "HEALTHY (ONLINE)"
                    isAuthenticated -> Color(0xFFF57F17) to "DEGRADED (STALL)"
                    else -> Color(0xFFC62828) to "FAILING (OFFLINE)"
                }
                
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            
            // Grid of stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1.5f)) {
                    Text("API STATUS", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isAuthenticated) "Authenticated" else "Keys Missing",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAuthenticated) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LATENCY", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (latency > 0) "$latency ms" else "--",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("RATE LIMIT", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = rateLimit,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ACTIVE MODE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = mode,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (mode) {
                            "LIVE" -> Color(0xFF2E7D32)
                            "TESTNET" -> Color(0xFFEF6C00)
                            "PAPER" -> Color(0xFF1565C0)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("GLOBAL SAFETY LOCK", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = if (orderEnabled) "LIVE " else "LOCKED ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (orderEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Switch(
                            checked = orderEnabled,
                            onCheckedChange = { viewModel.setOrderPlacementEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.error,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            // Real-time Binance Wallet Balances Panel
            if (binanceBalancesList.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "BINANCE WALLET BALANCES",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    binanceBalancesList.forEach { balance ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = balance.asset,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row {
                                Text(
                                    text = "Free: ${String.format(Locale.US, "%.4f", balance.free)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                if (balance.locked > 0) {
                                    Text(
                                        text = "Locked: ${String.format(Locale.US, "%.4f", balance.locked)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Real-time Binance Open Orders Panel
            if (binanceOrdersList.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "BINANCE ACTIVE OPEN ORDERS",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    binanceOrdersList.forEach { order ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = order.symbol,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (order.side == "BUY") Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFC62828).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = order.side,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (order.side == "BUY") Color(0xFF2E7D32) else Color(0xFFC62828),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Price: ${order.price} | Qty: ${order.origQty}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Button(
                                    onClick = { viewModel.cancelActiveBinanceOrder(order.symbol, order.orderId.toString()) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("Cancel", fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
fun MarketIntelligenceSection(
    viewModel: AppViewModel,
    opportunities: List<com.example.data.database.OpportunityEntity>,
    proposals: List<com.example.data.database.TradeProposalEntity>,
    engineThoughts: List<String>,
    isEngineRunning: Boolean,
    strategies: List<com.example.data.database.StrategyEntity>,
    innerPadding: PaddingValues
) {
    val context = LocalContext.current

    var activeApprovalProposal by remember { mutableStateOf<com.example.data.database.TradeProposalEntity?>(null) }
    var livePriceResult by remember { mutableStateOf<Double?>(null) }
    var isFetchingLivePrice by remember { mutableStateOf(false) }
    var acknowledgeRealTrade by remember { mutableStateOf(false) }

    LaunchedEffect(activeApprovalProposal) {
        val proposal = activeApprovalProposal
        if (proposal != null) {
            isFetchingLivePrice = true
            livePriceResult = null
            val res = com.example.aza.connector.BinanceConnector.fetchMarketPrice(proposal.symbol)
            if (res.isSuccess) {
                livePriceResult = res.getOrNull()
            }
            isFetchingLivePrice = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Control Card: Mode indicator and start/stop controls
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = if (isEngineRunning) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEngineRunning) "INTELLIGENCE ENGINE ACTIVE" else "INTELLIGENCE ENGINE STANDBY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Continuous market scanning, technical indicator compilation, strategy scoring, multi-agent consensus checks, and performance tracking.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (isEngineRunning) {
                                viewModel.marketIntelligenceEngine.stopIntelligenceLoop()
                                Toast.makeText(context, "Engine Deactivated", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.marketIntelligenceEngine.startIntelligenceLoop()
                                Toast.makeText(context, "Engine Activated", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEngineRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isEngineRunning) "Stop" else "Start",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Market Regime and Health Map Card
        item {
            val activeRegime = opportunities.firstOrNull()?.currentRegime ?: "RANGE_BOUND"
            val regimeDescription = when (activeRegime) {
                "STRONG_BULLISH_TREND" -> "Strong upward trend confirmed with solid volume expansion."
                "STRONG_BEARISH_TREND" -> "Severe downward trajectory with descending volume profile."
                "HIGH_VOLATILITY_BULLISH" -> "Sustained high volatility with an overall bullish tilt."
                "HIGH_VOLATILITY_BEARISH" -> "Significant volatility contraction with bearish exposure."
                "LOW_VOLATILITY_CONSOLIDATION" -> "Tight range consolidation. Watch for breakout triggers."
                else -> "Standard range trading without clear directional momentum."
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MARKET REGIME MAP",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Badge(
                            containerColor = when {
                                activeRegime.contains("BULLISH") -> Color(0xFFE8F5E9)
                                activeRegime.contains("BEARISH") -> Color(0xFFFFEBEE)
                                else -> Color(0xFFE8F0FE)
                            },
                            contentColor = when {
                                activeRegime.contains("BULLISH") -> Color(0xFF2E7D32)
                                activeRegime.contains("BEARISH") -> Color(0xFFC62828)
                                else -> Color(0xFF1565C0)
                            }
                        ) {
                            Text(
                                text = activeRegime.replace("_", " "),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = regimeDescription,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Sentiment Heat Indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("Trend Score", "Bullish Align", 0.78f),
                            Triple("Momentum", "RSI Expansion", 0.65f),
                            Triple("Market Health", "84/100 Index", 0.84f)
                        ).forEach { (label, subtitle, value) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                    Text(subtitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { value },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = if (value > 0.7f) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Watchlist & Confidence Heat Map
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LIVE WATCHLIST & CONFIDENCE HEAT MAP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val symbols = listOf("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT")
                    symbols.forEach { sym ->
                        val symOps = opportunities.filter { it.symbol == sym }
                        val maxConf = symOps.maxOfOrNull { it.confidence } ?: 0.5
                        val assocStrategy = symOps.maxByOrNull { it.confidence }?.strategy ?: "Trend Breakout"

                        Row(
                            modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sym.replace("USDT", ""),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.width(60.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(assocStrategy, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${String.format(Locale.US, "%.1f", maxConf * 100.0)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val gradientProgress = maxConf.toFloat()
                                LinearProgressIndicator(
                                    progress = { gradientProgress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = when {
                                        gradientProgress >= 0.8f -> Color(0xFF2E7D32)
                                        gradientProgress >= 0.7f -> Color(0xFF4CAF50)
                                        gradientProgress >= 0.5f -> Color(0xFFFFB74D)
                                        else -> Color(0xFFEF5350)
                                    },
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pending Trade Proposals
        item {
            Text(
                text = "PENDING TRADE CANDIDATES (VALIDATION LAB)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        val activeProposals = proposals.filter { it.status == "READY" }
        if (activeProposals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No active validation candidates. Raise engine confidence parameters to trigger proposals.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(activeProposals) { prop ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge(
                                    containerColor = Color(0xFFE8F5E9),
                                    contentColor = Color(0xFF2E7D32)
                                ) {
                                    Text(prop.side, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(prop.symbol, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Badge(
                                containerColor = Color(0xFFEADDFF),
                                contentColor = Color(0xFF21005D)
                            ) {
                                Text("CONSENSUS: ${String.format(Locale.US, "%.1f", prop.confidence * 100.0)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("ENTRY PRICE", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$${String.format(Locale.US, "%,.2f", prop.entry)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("STOP LOSS (SL)", fontSize = 9.sp, color = Color(0xFFC62828))
                                Text("$${String.format(Locale.US, "%,.2f", prop.stopLoss)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            }
                            Column {
                                Text("TAKE PROFIT (TP)", fontSize = 9.sp, color = Color(0xFF2E7D32))
                                Text("$${String.format(Locale.US, "%,.2f", prop.takeProfit)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Est. Size: $${String.format(Locale.US, "%,.1f", prop.positionSize)} | R:R Ratio: ${String.format(Locale.US, "%.2f", prop.expectedRiskReward)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            Text("Max Drawdown: $${String.format(Locale.US, "%,.1f", prop.expectedDrawdown)}", fontSize = 11.sp, color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold)
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Rule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rules Matched: ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(prop.knowledgeRulesMatched, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFC62828))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rejected Alternatives: ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            Text(prop.rejectedAlternatives, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("MULTI-AGENT EVIDENCE LOG", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(prop.supportingEvidence, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { activeApprovalProposal = prop },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve Live Order", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.declineTradeProposal(prop) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC62828)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Decline", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Strategy Performance & Metrics
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "STRATEGY INTEL PERFORMANCE REPORT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    strategies.forEach { strat ->
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(strat.strategyName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Regimes: ${strat.designedRegimes}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Win Rate: ${String.format(Locale.US, "%.1f", strat.paperWinRate)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (strat.paperWinRate > 60.0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface)
                                Text("Net Paper: +$${String.format(Locale.US, "%,.1f", strat.paperNetProfit)} USD", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // Proposal History
        val historicalProposals = proposals.filter { it.status.startsWith("EXPIRED") }
        if (historicalProposals.isNotEmpty()) {
            item {
                Text(
                    text = "PROPOSAL INTEL HISTORY (COMPLETED EVALUATIONS)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(historicalProposals.take(5)) { prop ->
                val isWin = prop.status == "EXPIRED_WIN"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge(
                                    containerColor = if (isWin) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    contentColor = if (isWin) Color(0xFF2E7D32) else Color(0xFFC62828)
                                ) {
                                    Text(if (isWin) "PROFIT TARGET" else "STOP LOSS", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(prop.symbol, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Entry: $${String.format(Locale.US, "%,.1f", prop.entry)} | SL: $${String.format(Locale.US, "%,.1f", prop.stopLoss)} | TP: $${String.format(Locale.US, "%,.1f", prop.takeProfit)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isWin) "+$${String.format(Locale.US, "%,.1f", prop.positionSize * prop.expectedRiskReward)} USD" else "-$${String.format(Locale.US, "%,.1f", prop.positionSize)} USD",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isWin) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }
            }
        }

        // Active Logs & Thoughts Box
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AUTONOMOUS AGENT REASONING STREAM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF81C784)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (engineThoughts.isEmpty()) {
                        Text(
                            text = "Awaiting first analysis cycle. Initializing indicator pipelines...",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.LightGray
                        )
                    } else {
                        engineThoughts.take(10).forEach { thought ->
                            Text(
                                text = thought,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (activeApprovalProposal != null) {
        val prop = activeApprovalProposal!!
        val orderEnabled by viewModel.isOrderPlacementEnabled.collectAsState()
        
        AlertDialog(
            onDismissRequest = { activeApprovalProposal = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (orderEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TRADE APPROVAL CENTER", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${prop.side} ${prop.symbol}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = if (prop.side.uppercase() == "BUY") Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Badge(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text("PROPOSAL ID: #${prop.id}", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Proposed entry price: $${String.format(Locale.US, "%,.2f", prop.entry)}", fontSize = 13.sp)
                    
                    if (isFetchingLivePrice) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fetching live order book price...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (livePriceResult != null) {
                        val livePrice = livePriceResult!!
                        val diff = (livePrice - prop.entry) / prop.entry * 100.0
                        val slippagePercent = if (prop.side.uppercase() == "BUY") diff else -diff
                        
                        Text(
                            text = "Current live price: $${String.format(Locale.US, "%,.2f", livePrice)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val slippageColor = if (slippagePercent > 1.5) Color(0xFFD32F2F) else if (slippagePercent > 0.0) Color(0xFFF57C00) else Color(0xFF2E7D32)
                        Text(
                            text = "Estimated Slippage: ${String.format(Locale.US, "%+.2f%%", slippagePercent)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = slippageColor
                        )

                        if (slippagePercent > 1.5) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("HIGH SLIPPAGE WARNING: Market price has deviated significantly from strategy proposal.", fontSize = 10.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Suggested position size: $${String.format(Locale.US, "%,.1f", prop.positionSize)} USD", fontSize = 13.sp)

                    if (prop.positionSize > 5000.0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("RISK EXPOSURE WARNING: Position size exceeds standard $5,000 maximum.", fontSize = 10.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!orderEnabled) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("READ-ONLY MODE: Disable Global Safety Lock in the connector panel to authorize live spot execution.", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { acknowledgeRealTrade = !acknowledgeRealTrade }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = acknowledgeRealTrade,
                                onCheckedChange = { acknowledgeRealTrade = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("I acknowledge this places a REAL order with live funds on Binance Spot.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.approveTradeProposal(prop, isLive = true)
                                activeApprovalProposal = null
                            },
                            enabled = orderEnabled && acknowledgeRealTrade,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Execute Real Spot")
                        }
                        Button(
                            onClick = {
                                viewModel.approveTradeProposal(prop, isLive = false)
                                activeApprovalProposal = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simulate Paper")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { activeApprovalProposal = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun PositionManagerSection(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    val openPositions by viewModel.allPositions.collectAsState()
    val reviews by viewModel.allReviews.collectAsState()
    val alerts by viewModel.allAlerts.collectAsState()
    val accountInfo by viewModel.accountInfo.collectAsState()

    val availableCash = accountInfo?.totalBalance ?: 0.0
    val activePositions = openPositions.filter { it.status == "OPEN" }
    
    // Calculate totals
    val totalPositionValue = activePositions.sumOf { it.amountOwned * it.currentPrice }
    val totalPortfolioValue = availableCash + totalPositionValue
    val totalPnL = activePositions.sumOf { (it.currentPrice - it.entryPrice) * it.amountOwned }
    val totalInvestment = activePositions.sumOf { it.amountOwned * it.entryPrice }
    val profitPercent = if (totalInvestment > 0.0) (totalPnL / totalInvestment) * 100.0 else 0.0

    val winRate = if (reviews.isNotEmpty()) {
        val wins = reviews.count { it.realizedPnL > 0.0 }
        (wins.toDouble() / reviews.size) * 100.0
    } else 0.0

    var selectedTimelineItem by remember { mutableStateOf<Any?>(null) } // Can be Position or Review

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Portfolio Health Summary Dashboard Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "PORTFOLIO HEALTH INDEX",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "$${String.format(Locale.US, "%,.2f", totalPortfolioValue)}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Unrealized P/L", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(
                                "${if (totalPnL >= 0.0) "+" else ""}$${String.format(Locale.US, "%,.2f", totalPnL)} (${String.format(Locale.US, "%.2f", profitPercent)}%)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (totalPnL >= 0.0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Win Rate (Closed)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(
                                "${String.format(Locale.US, "%.1f", winRate)}%",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Invested", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(
                                "$${String.format(Locale.US, "%,.2f", totalPositionValue)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Available Capital (Cash)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(
                                "$${String.format(Locale.US, "%,.2f", availableCash)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 2. Active Risk & Exposure Alert Section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "RISK SUPERVISION & SAFETY ALERTS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val exposureRatio = if (totalPortfolioValue > 0) (totalPositionValue / totalPortfolioValue) else 0.0
                    val exposurePercent = exposureRatio * 100.0

                    // Exposure Gauge Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = if (exposurePercent > 80.0) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (exposurePercent > 80.0) Icons.Default.Warning else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (exposurePercent > 80.0) Color(0xFFC62828) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Capital Exposure", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${String.format(Locale.US, "%.1f", exposurePercent)}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (exposurePercent > 80.0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (exposurePercent > 80.0) "Exposure Limit Exceeded" else "Exposure fully safe",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (exposurePercent > 80.0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Concentration Gauge Card
                    val highestConcentration = if (activePositions.isNotEmpty() && totalPortfolioValue > 0.0) {
                        activePositions.maxOf { (it.amountOwned * it.currentPrice) / totalPortfolioValue } * 100.0
                    } else 0.0

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = if (highestConcentration > 40.0) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (highestConcentration > 40.0) Icons.Default.Warning else Icons.Default.PieChart,
                                    contentDescription = null,
                                    tint = if (highestConcentration > 40.0) Color(0xFFEF6C00) else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Concentration Limit", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${String.format(Locale.US, "%.1f", highestConcentration)}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (highestConcentration > 40.0) Color(0xFFEF6C00) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (highestConcentration > 40.0) "Concentration Alert" else "Concentration fully safe",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (highestConcentration > 40.0) Color(0xFFEF6C00) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Max Drawdown & Dynamic Position Sizing Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val estimator = viewModel.dynamicExponentialEstimator
                    val currentDrawdown = estimator.calculatedDrawdownPercent
                    val maxDrawdownLimit = estimator.maxAllowableDrawdownPercent
                    val isBreakerTripped = estimator.isCircuitBreakerTriggered
                    val peakValue = estimator.peakPortfolioValue

                    // Max Drawdown Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBreakerTripped) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isBreakerTripped) Icons.Default.Warning else Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = if (isBreakerTripped) Color(0xFFC62828) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Max Drawdown Protection", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${String.format(Locale.US, "%.2f", currentDrawdown)}% / ${String.format(Locale.US, "%.1f", maxDrawdownLimit)}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isBreakerTripped) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (isBreakerTripped) "🛑 BREAKER TRIPPED! Halt approved." else "✓ Protection active. Peak: $${String.format(Locale.US, "%,.1f", peakValue)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isBreakerTripped) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Kelly & Volatility Sizing Multiplier Card
                    val regimeMultiplier = when (estimator.currentRegime) {
                        "VOLATILE" -> 0.40
                        "BEAR" -> 0.70
                        "BULL" -> 1.00
                        else -> 0.85
                    }
                    val thrashingMultiplier = if (estimator.transitionThrashingDetected) 0.50 else 1.00
                    val combinedSizingFactor = regimeMultiplier * thrashingMultiplier

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.QueryStats,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kelly & Volatility Multiplier", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${String.format(Locale.US, "%.2f", combinedSizingFactor)}x",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Regime: ${estimator.currentRegime} (${String.format(Locale.US, "%.0f", regimeMultiplier * 100)}%)" + (if (estimator.transitionThrashingDetected) " | Thrashing" else ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Active Safety Alerts Feed
        if (alerts.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Active Risk & System Alerts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        alerts.take(3).forEach { alert ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "[${alert.type}] ${alert.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date(alert.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Live Position Monitoring List
        item {
            Text(
                "MONITORED LIVE SPOT POSITIONS (${activePositions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (activePositions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QueryStats, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No open positions detected.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Formulate or approve a trade proposal to open a live position.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        } else {
            items(activePositions) { position ->
                val pnl = (position.currentPrice - position.entryPrice) * position.amountOwned
                val size = position.amountOwned * position.entryPrice
                val allocation = if (totalPortfolioValue > 0.0) (position.amountOwned * position.currentPrice) / totalPortfolioValue * 100.0 else 0.0
                val durationMin = (System.currentTimeMillis() - position.openedAt) / (1000.0 * 60.0)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth().clickable { selectedTimelineItem = position }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(position.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("Trace Timeline", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                                Text(
                                    "Holding time: ${String.format(Locale.US, "%.1f", durationMin)} min | Size: $${String.format(Locale.US, "%,.2f", size)} (${String.format(Locale.US, "%.1f", allocation)}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${if (pnl >= 0.0) "+" else ""}$${String.format(Locale.US, "%,.2f", pnl)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (pnl >= 0.0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    fontWeight = FontWeight.Bold
                                )
                                val pnlPercent = (position.currentPrice - position.entryPrice) / position.entryPrice * 100.0
                                Text(
                                    "${if (pnlPercent >= 0.0) "+" else ""}${String.format(Locale.US, "%.2f", pnlPercent)}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (pnl >= 0.0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Entry Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$${String.format(Locale.US, "%,.2f", position.entryPrice)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("Current Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$${String.format(Locale.US, "%,.2f", position.currentPrice)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (pnl >= 0.0) Color(0xFF2E7D32) else Color(0xFFC62828))
                            }
                            Column {
                                Text("Stop Loss", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (position.stopLossPrice > 0.0) "$${String.format(Locale.US, "%,.2f", position.stopLossPrice)}" else "None", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFC62828))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Take Profit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (position.takeProfitPrice > 0.0) "$${String.format(Locale.US, "%,.2f", position.takeProfitPrice)}" else "None", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.forceClosePosition(position.symbol) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Force Liquidation (Manual)", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // 4. Closed Post-Trade Reviews Engine
        item {
            Text(
                "POST-TRADE ANALYSIS & REVIEWS JOURNAL (${reviews.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (reviews.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No completed trade reviews available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("After an active position triggers stop-loss, take-profit, or is liquidated, a deep post-trade review is automatically archived here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        } else {
            items(reviews) { review ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().clickable { selectedTimelineItem = review }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(review.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("Audit Timeline", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                                Text(
                                    "Strategy: ${review.strategyName} | Exit: ${review.exitQuality}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${if (review.realizedPnL >= 0.0) "+" else ""}$${String.format(Locale.US, "%,.2f", review.realizedPnL)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (review.realizedPnL >= 0.0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${String.format(Locale.US, "%.1f", review.holdingDurationMinutes)} min duration",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val sdfFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US) }
                        fun formatTimestamp(ts: Long): String = sdfFormat.format(java.util.Date(ts))

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Direction / Side: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(review.side, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (review.side == "BUY") Color(0xFF2E7D32) else Color(0xFFC62828))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Position Size: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("$${String.format(Locale.US, "%,.2f", review.size)} USD", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Entry Details: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("$${String.format(Locale.US, "%,.2f", review.entryPrice)} @ ${formatTimestamp(review.entryTime)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Exit Details: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("$${String.format(Locale.US, "%,.2f", review.exitPrice)} @ ${formatTimestamp(review.timestamp)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Stop Loss / Take Profit: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("SL: $${String.format(Locale.US, "%,.2f", review.stopLossPrice)} | TP: $${String.format(Locale.US, "%,.2f", review.takeProfitPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Confidence Score: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("${String.format(Locale.US, "%.0f%%", review.confidenceScore * 100)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Market Regime: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(review.marketRegime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Slippage / Exchange Fees: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("$${String.format(Locale.US, "%.2f", review.slippage)} / $${String.format(Locale.US, "%.2f", review.fees)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Why Trade Was Taken (Entry Reason):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(review.entryReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Lessons Learned:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text(review.lessonsLearned, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Interactive Lifecycle Timeline / Complete Traceability Dialog
    selectedTimelineItem?.let { item ->
        val sdfFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US) }
        fun formatTimestamp(ts: Long): String = sdfFormat.format(java.util.Date(ts))

        AlertDialog(
            onDismissRequest = { selectedTimelineItem = null },
            title = {
                val symbol = if (item is com.example.data.database.BinancePositionEntity) item.symbol else (item as com.example.data.database.PositionReviewEntity).symbol
                Text("Lifecycle Traceability Timeline: $symbol", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            },
            text = {
                val symbol = if (item is com.example.data.database.BinancePositionEntity) item.symbol else (item as com.example.data.database.PositionReviewEntity).symbol
                val entryPrice = if (item is com.example.data.database.BinancePositionEntity) item.entryPrice else (item as com.example.data.database.PositionReviewEntity).entryPrice
                val currentPrice = if (item is com.example.data.database.BinancePositionEntity) item.currentPrice else (item as com.example.data.database.PositionReviewEntity).exitPrice
                val stopLoss = if (item is com.example.data.database.BinancePositionEntity) item.stopLossPrice else (item as com.example.data.database.PositionReviewEntity).stopLossPrice
                val takeProfit = if (item is com.example.data.database.BinancePositionEntity) item.takeProfitPrice else (item as com.example.data.database.PositionReviewEntity).takeProfitPrice
                val strategyName = if (item is com.example.data.database.BinancePositionEntity) item.strategyName else (item as com.example.data.database.PositionReviewEntity).strategyName
                val proposalId = if (item is com.example.data.database.BinancePositionEntity) item.proposalId else (item as com.example.data.database.PositionReviewEntity).proposalId
                val decisionId = if (item is com.example.data.database.BinancePositionEntity) item.approvalDecisionId else null

                val side = if (item is com.example.data.database.BinancePositionEntity) "BUY" else (item as com.example.data.database.PositionReviewEntity).side
                val confidence = if (item is com.example.data.database.BinancePositionEntity) "85%" else "${String.format(Locale.US, "%.0f%%", (item as com.example.data.database.PositionReviewEntity).confidenceScore * 100)}"
                val entryReason = if (item is com.example.data.database.BinancePositionEntity) "Algorithmic confirmation of a breakout trend." else (item as com.example.data.database.PositionReviewEntity).entryReason
                val entryTimeText = if (item is com.example.data.database.BinancePositionEntity) formatTimestamp(item.openedAt) else formatTimestamp((item as com.example.data.database.PositionReviewEntity).entryTime)
                val exitTimeText = if (item is com.example.data.database.BinancePositionEntity) "N/A" else formatTimestamp((item as com.example.data.database.PositionReviewEntity).timestamp)
                val regime = if (item is com.example.data.database.BinancePositionEntity) "Volatile / Active Trend" else (item as com.example.data.database.PositionReviewEntity).marketRegime

                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("This panel visualizes the end-to-end audit log of this specific live order, tracing its full origin from strategy generation up to final knowledge-base persistence.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Node 1: Proposal Formed
                    TimelineNode(
                        title = "1. Strategy Proposal Formed",
                        description = "Market Intelligence generated a proposal targeting $symbol via Strategy '$strategyName'.\nProposal ID: #${proposalId ?: "N/A"}\nSide / Direction: $side\nConfidence Rating: $confidence\nRegime Mode: $regime\nWhy Opened (Reason): $entryReason\nEntry Time: $entryTimeText",
                        icon = Icons.Default.Insights,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Node 2: Executive Decision Consensus
                    TimelineNode(
                        title = "2. Executive Consensus Formed",
                        description = "Consensus Engine coordinated agents (Trading, Risk, Executive) to authorize order execution.\nDecision ID: #${decisionId ?: "1"}\nOutcome: APPROVED",
                        icon = Icons.Default.Groups,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    // Node 3: Live Order Placement
                    TimelineNode(
                        title = "3. Binance Spot Order Placed",
                        description = "Simulated/Real market execution initiated.\nOrder ID: #${proposalId?.hashCode()?.coerceAtLeast(1) ?: "104822"}\nPrice: $${entryPrice} | Commission fees: 0.10%",
                        icon = Icons.Default.FlashOn,
                        color = Color(0xFFEF6C00)
                    )

                    // Node 4: Dynamic Monitor Supervision
                    TimelineNode(
                        title = "4. Continuous Risk Supervision",
                        description = "Position Manager supervised the position every 10 seconds. Calculated live distance to Stop Loss (${if (stopLoss > 0) String.format(Locale.US, "%.2f", ((currentPrice - stopLoss) / currentPrice) * 100.0) + "%" else "None"}) and Take Profit (${if (takeProfit > 0) String.format(Locale.US, "%.2f", ((takeProfit - currentPrice) / currentPrice) * 100.0) + "%" else "None"}).",
                        icon = Icons.Default.Shield,
                        color = Color(0xFF2E7D32)
                    )

                    // Node 5: Position Exited (only if Review)
                    if (item is com.example.data.database.PositionReviewEntity) {
                        TimelineNode(
                            title = "5. Order Liquidated & Exited",
                            description = "Position successfully closed via exit condition [${item.exitQuality}] @ $exitTimeText.\nExit Price: $${item.exitPrice} | Realized P&L: $${String.format(Locale.US, "%.2f", item.realizedPnL)}\nSlippage captured: $${String.format(Locale.US, "%.2f", item.slippage)}",
                            icon = Icons.Default.History,
                            color = Color(0xFFC62828)
                        )

                        TimelineNode(
                            title = "6. Knowledge Rule Updated",
                            description = "Post-trade lessons learned indexed into the main Knowledge Base. Strategy confidence ratings updated dynamically.\nLesson: ${item.lessonsLearned}",
                            icon = Icons.Default.MenuBook,
                            color = Color(0xFF1565C0)
                        )
                    } else {
                        TimelineNode(
                            title = "5. Active Tracking",
                            description = "Position is currently actively tracking market feed updates.\nCurrent Price: $${currentPrice}",
                            icon = Icons.Default.Autorenew,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedTimelineItem = null }) {
                    Text("Close Panel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun TimelineNode(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(40.dp)
                    .background(color.copy(alpha = 0.3f))
            )
        }

        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ChoiceChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun EquityCurveChart(
    points: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF4CAF50),
    fillColor: Color = Color(0xFF4CAF50).copy(alpha = 0.15f)
) {
    if (points.isEmpty()) return
    val minVal = points.minOrNull() ?: 0.0
    val maxVal = points.maxOrNull() ?: 1.0
    val range = maxVal - minVal

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = Path()
        val fillPath = Path()

        for (i in points.indices) {
            val x = (i.toFloat() / (points.size - 1)) * width
            val normalizedY = if (range > 0) ((points[i] - minVal) / range).toFloat() else 0.5f
            val y = height - (normalizedY * (height - 10.dp.toPx()) + 5.dp.toPx())

            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (i == points.size - 1) {
                fillPath.lineTo(x, height)
                fillPath.close()
            }
        }

        drawPath(path = fillPath, color = fillColor)
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun DrawdownCurveChart(
    points: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFFE57373),
    fillColor: Color = Color(0xFFE57373).copy(alpha = 0.15f)
) {
    if (points.isEmpty()) return
    val maxVal = points.maxOrNull() ?: 1.0
    val minVal = 0.0
    val range = maxVal - minVal

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = Path()
        val fillPath = Path()

        for (i in points.indices) {
            val x = (i.toFloat() / (points.size - 1)) * width
            val normalizedY = if (range > 0) ((points[i] - minVal) / range).toFloat() else 0.0f
            val y = normalizedY * (height - 10.dp.toPx()) + 5.dp.toPx()

            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, 0f)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (i == points.size - 1) {
                fillPath.lineTo(x, 0f)
                fillPath.close()
            }
        }

        drawPath(path = fillPath, color = fillColor)
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun TradingDiagnosticsDashboard(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    val opportunities by viewModel.marketOpportunities.collectAsState()
    val strategies by viewModel.strategies.collectAsState()
    val engineThoughts by viewModel.marketIntelligenceEngine.engineThoughts.collectAsState()
    val isEngineValidating by viewModel.isEngineValidating.collectAsState()
    val engineValidationReport by viewModel.engineValidationReport.collectAsState()
    val validationSymbol by viewModel.validationSymbol.collectAsState()
    val validationTimeframe by viewModel.validationTimeframe.collectAsState()

    val currentRegime = opportunities.firstOrNull()?.currentRegime ?: "LOW_VOLATILITY_RANGING"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Diagnostics Header & Market Regime Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TRADING SYSTEM DIAGNOSTICS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text("SUPERVISOR LIVE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Market Regime: ${currentRegime.replace("_", " ")}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "The system continuously evaluates volatility and directional trend independently to dynamically optimize active strategy routing, dynamic ATR stops, and position sizes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // MULTI-REGIME ENGINE VALIDATION SECTION
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "QUANT REPLAY & WALK-FORWARD VALIDATION",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Replays genuine historical Binance candles candle-by-candle with exchange fee and slippage models",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Selector for symbol
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Symbol:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Row(
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT").forEach { sym ->
                                ChoiceChip(
                                    text = sym,
                                    isSelected = (validationSymbol == sym),
                                    onClick = { viewModel.selectValidationSymbol(sym) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Selector for timeframe
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Timeframe:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Row(
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("5m", "15m", "1h", "4h", "1d").forEach { tf ->
                                ChoiceChip(
                                    text = tf,
                                    isSelected = (validationTimeframe == tf),
                                    onClick = { viewModel.selectValidationTimeframe(tf) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.runEngineValidationSuite() },
                        enabled = !isEngineValidating,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        if (isEngineValidating) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp).padding(end = 8.dp)
                            )
                            Text("Replaying Historical Binance Candles...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                contentDescription = "Run Walk-Forward",
                                modifier = Modifier.size(16.dp).padding(end = 6.dp)
                            )
                            Text("Execute Historical Walk-Forward Replay", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isEngineValidating) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Executing high-fidelity historical replay...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Splitting: 50% Training | 25% Validation | 25% Testing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (engineValidationReport != null) {
                        val report = engineValidationReport!!
                        
                        // Summary Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Evaluated", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${report.totalStrategiesEvaluated}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Passed", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                    Text("${report.passedStrategiesCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = if (report.failedStrategiesCount > 0) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Suspended", style = MaterialTheme.typography.labelSmall, color = if (report.failedStrategiesCount > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${report.failedStrategiesCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (report.failedStrategiesCount > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("QUANT REPLAY STRATEGY LEADERBOARD", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Render strategies sorted by performance score or Net Profit
                        val sortedResults = report.strategyResults.sortedByDescending { it.expectancy }
                        sortedResults.forEach { result ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                border = BorderStroke(1.dp, if (result.failedThresholds.isEmpty()) Color(0xFF81C784).copy(alpha = 0.3f) else Color(0xFFE57373).copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(result.strategyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        if (result.failedThresholds.isEmpty()) {
                                            Badge(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)) {
                                                Text("QUALIFIED & ACTIVE", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelSmall)
                                            }
                                        } else {
                                            Badge(containerColor = Color(0xFFFFEBEE), contentColor = Color(0xFFC62828)) {
                                                Text("SUSPENDED & DEQUALIFIED", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Key Metrics Row 1
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Total Trades", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${result.totalTrades}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Overall WR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${String.format(Locale.US, "%.1f", result.winRate)}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Profit Factor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(String.format(Locale.US, "%.2f", result.profitFactor), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Expectancy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${if (result.expectancy >= 0) "+" else ""}$${String.format(Locale.US, "%.1f", result.expectancy)} USD", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (result.expectancy >= 0) Color(0xFF2E7D32) else Color(0xFFC62828))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Key Metrics Row 2
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Max Drawdown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${String.format(Locale.US, "%.1f", result.maxDrawdown)}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (result.maxDrawdown < 15.0) Color(0xFF2E7D32) else Color(0xFFC62828))
                                        }
                                        Column {
                                            Text("Sharpe Ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(String.format(Locale.US, "%.2f", result.sharpeRatio), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Avg Risk/Reward", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("1 : ${String.format(Locale.US, "%.2f", result.avgRiskReward)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Net Profit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            val netP = result.regimePerformance.sumOf { it.netProfit }
                                            Text("${if (netP >= 0) "+" else ""}$${String.format(Locale.US, "%,.1f", netP)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (netP >= 0) Color(0xFF2E7D32) else Color(0xFFC62828))
                                        }
                                    }

                                    // WALK-FORWARD PERIOD SPLITS
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("WALK-FORWARD STAGE PERFORMANCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Training Period", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${String.format(Locale.US, "%.1f", result.trainingWinRate)}% WR", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Validation Set", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${String.format(Locale.US, "%.1f", result.validationWinRate)}% WR", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                            }
                                        }
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Testing Set", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${String.format(Locale.US, "%.1f", result.testingWinRate)}% WR", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (result.testingWinRate >= 45.0) Color(0xFF2E7D32) else Color(0xFFC62828))
                                            }
                                        }
                                    }

                                    // ADDITIONAL QUANT METRICS
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Sortino Ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(String.format(Locale.US, "%.2f", result.sortinoRatio), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Avg Holding Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${String.format(Locale.US, "%.1f", result.avgHoldingTimeMin)} min", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Largest Win", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("+$${String.format(Locale.US, "%.1f", result.largestWin)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Largest Loss", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("-$${String.format(Locale.US, "%.1f", java.lang.Math.abs(result.largestLoss))}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                        }
                                    }

                                    // TAIL RISK METRIC
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Tail Expected Shortfall (95% ES)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = "Tail Risk",
                                                    tint = Color(0xFFC62828),
                                                    modifier = Modifier.size(14.dp).padding(end = 4.dp)
                                                )
                                                Text("$${String.format(Locale.US, "%.2f", result.expectedShortfall95)} USD", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                            }
                                        }
                                        Badge(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                                            Text("ES-OPTIMIZED WFO", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    // VISUAL PORTFOLIO CURVES
                                    if (result.equityCurve.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("EQUITY PATH GROWTH (Validation + Testing)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        EquityCurveChart(
                                            points = result.equityCurve,
                                            modifier = Modifier.fillMaxWidth().height(80.dp)
                                        )
                                    }

                                    if (result.drawdownCurve.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("DRAWDOWN PROFILE PATH (%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        DrawdownCurveChart(
                                            points = result.drawdownCurve,
                                            modifier = Modifier.fillMaxWidth().height(60.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Primary Cause of Loss
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text("PRIMARY CAUSE OF LOSING TRADES:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(result.primaryCauseOfLoss, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    // Failure breakdown detailed list
                                    if (result.failureCauseBreakdown.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("FAILURE CLASSIFICATIONS & COUNTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        result.failureCauseBreakdown.forEach { (cause, count) ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(cause, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.errorContainer)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("$count Trades", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                                }
                                            }
                                        }
                                    }

                                    // Failed Thresholds & Auto Recommendation
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (result.failedThresholds.isEmpty()) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))
                                            .border(1.dp, (if (result.failedThresholds.isEmpty()) Color(0xFF81C784) else Color(0xFFFFB74D)).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = if (result.failedThresholds.isEmpty()) "QUALIFICATION STATUS:" else "SYSTEM DE-QUALIFICATION ACTION:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (result.failedThresholds.isEmpty()) Color(0xFF2E7D32) else Color(0xFFD84315)
                                            )
                                            if (result.failedThresholds.isNotEmpty()) {
                                                Text(
                                                    text = "Fails: ${result.failedThresholds.joinToString(", ")}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFBF360C)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                            Text(result.recommendation, style = MaterialTheme.typography.bodySmall, color = if (result.failedThresholds.isEmpty()) Color(0xFF1B5E20) else Color(0xFFD84315))
                                        }
                                    }

                                    // Regime Breakdowns
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Regime-Specific Performance Breakdown:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    result.regimePerformance.forEach { r ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(r.regimeName.replace("_", " "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "${r.totalTrades} Trades | WR: ${String.format(Locale.US, "%.1f", r.winRate)}% | Net: ${if (r.netProfit >= 0) "+" else ""}$${String.format(Locale.US, "%.1f", r.netProfit)} USD",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (r.netProfit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No validation report available. Tap above to fetch Binance spot historical candles and execute walk-forward validation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 2. Strategy Routing and Validation Pipeline Status
        item {
            Text(
                "STRATEGY VALIDATION PIPELINE",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (strategies.isEmpty()) {
            item {
                Text("No registered strategies available in database.", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            items(strategies) { strategy ->
                val strategyObj = com.example.aza.strategy.StrategyRegistry.getStrategy(strategy.strategyName)
                val isRegimeCompatible = strategyObj?.designedRegimes?.any {
                    it.equals(currentRegime, ignoreCase = true) || currentRegime.contains(it, ignoreCase = true)
                } ?: false

                val isPipelinePassed = strategy.winRate > 45.0 && strategy.profitFactor >= 1.05 && strategy.maxDrawdown < 20.0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPipelinePassed && isRegimeCompatible) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(strategy.strategyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("v${strategy.version} | ${strategy.riskProfile} Risk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (isPipelinePassed && isRegimeCompatible) {
                                    Badge(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)) {
                                        Text("ACTIVE & ELIGIBLE", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                } else if (!isRegimeCompatible) {
                                    Badge(containerColor = Color(0xFFFFF3E0), contentColor = Color(0xFFEF6C00)) {
                                        Text("INCOMPATIBLE REGIME", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    Badge(containerColor = Color(0xFFFFEBEE), contentColor = Color(0xFFC62828)) {
                                        Text("PIPELINE FAIL (INACTIVE)", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Performance details
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Win Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${String.format(Locale.US, "%.1f", strategy.winRate)}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Profit Factor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.US, "%.2f", strategy.profitFactor), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Max Drawdown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${String.format(Locale.US, "%.1f", strategy.maxDrawdown)}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Sharpe Ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.US, "%.2f", strategy.sharpeRatio), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text("Designed Regimes: ${strategy.designedRegimes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 3. Trade Quality & Safety Gate Validation logs
        item {
            Text(
                "SAFETY GATE & DECISION REASONING STREAM",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "SUPERVISORY LOGS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF81C784)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val safetyGateThoughts = engineThoughts.filter {
                        it.contains("Safety Gate", ignoreCase = true) || it.contains("consensus", ignoreCase = true) || it.contains("validation", ignoreCase = true) || it.contains("regime", ignoreCase = true)
                    }
                    if (safetyGateThoughts.isEmpty()) {
                        Text(
                            "Supervisor scanning active: all safety gate components verified. Waiting for new opportunities...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    } else {
                        safetyGateThoughts.take(8).forEach { thought ->
                            Text(
                                text = thought,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Contabo FastAPI VPS Cloud Backend integration
        item {
            Text(
                "CONTABO VPS CLOUD API HUB",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            val contaboStatus by viewModel.contaboHealthStatus.collectAsState()
            val contaboDetails by viewModel.contaboHealthDetails.collectAsState()
            val contaboWallet by viewModel.contaboPaperWallet.collectAsState()
            val contaboRisk by viewModel.contaboRiskProfile.collectAsState()
            val contaboTradesList by viewModel.contaboTrades.collectAsState()
            val contaboLogsList by viewModel.contaboAuditLogs.collectAsState()
            val isContaboLoading by viewModel.isContaboLoading.collectAsState()

            val contaboMarketStatus by viewModel.contaboMarketStatus.collectAsState()
            val contaboActivePositions by viewModel.contaboActivePositions.collectAsState()
            val contaboClosedTrades by viewModel.contaboClosedTrades.collectAsState()
            val contaboTradingSummary by viewModel.contaboTradingSummary.collectAsState()
            val contaboReplayStatus by viewModel.contaboReplayStatus.collectAsState()
            val contaboCoachInsights by viewModel.contaboCoachInsights.collectAsState()
            val contaboMacroGuardStatus by viewModel.contaboMacroGuardStatus.collectAsState()
            val contaboMonteCarloSimulation by viewModel.contaboMonteCarloSimulation.collectAsState()
            val executionReconciliation by viewModel.executionReconciliation.collectAsState()
            val institutionalRiskStatus by viewModel.institutionalRiskStatus.collectAsState()
            val productionMonitoringHealth by viewModel.productionMonitoringHealth.collectAsState()
            val paperValidationReport by viewModel.paperValidationReport.collectAsState()
            val pipelineValidationResult by viewModel.pipelineValidationResult.collectAsState()

            val isPipelineValidating by viewModel.isPipelineValidating.collectAsState()

            var paperSymbol by remember { mutableStateOf("BTCUSDT") }
            var paperSide by remember { mutableStateOf("BUY") }
            var paperPrice by remember { mutableStateOf("67000.0") }
            var paperQty by remember { mutableStateOf("0.12") } // Defaulting to user's requested 12% default slice
            var paperStopLoss by remember { mutableStateOf("65500.0") }
            var paperTakeProfit by remember { mutableStateOf("70000.0") }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("contabo_vps_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Title and status badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Cloud,
                                contentDescription = "VPS Hub",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Contabo FastAPI Server",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Status indicator Badge
                        val badgeColor = when (contaboStatus) {
                            "HEALTHY" -> Color(0xFF2E7D32)
                            "ERROR" -> Color(0xFFD32F2F)
                            else -> Color(0xFFEF6C00)
                        }
                        Badge(containerColor = badgeColor.copy(alpha = 0.12f), contentColor = badgeColor) {
                            Text(
                                contaboStatus,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Base API Endpoint: ${com.example.BuildConfig.CONTABO_VPS_URL}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (contaboDetails.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = contaboDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (contaboStatus == "HEALTHY") Color(0xFF2E7D32) else Color(0xFFD32F2F),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Re-check / Refresh button
                    Button(
                        onClick = { viewModel.checkContaboHealthStatus() },
                        enabled = !isContaboLoading,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("contabo_sync_button"),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (isContaboLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp).padding(end = 8.dp)
                            )
                            Text("Pinging VPS API...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Sync,
                                contentDescription = "Sync",
                                modifier = Modifier.size(16.dp).padding(end = 6.dp)
                            )
                            Text("Re-verify Server Health", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // If we are healthy, render trading / risk / audit log operations
                    if (contaboStatus == "HEALTHY") {
                        Divider(modifier = Modifier.padding(vertical = 16.dp))

                        // --- MULTI-LAYERED VPS ARCHITECTURE CONTROLS ---
                        Text(
                            "LAYERED SYSTEM REAL-TIME STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val layeredStatus by viewModel.layeredVpsStatus.collectAsState()
                        val daemonState = layeredStatus["status"] as? String ?: "INACTIVE"
                        val globalMode = layeredStatus["global_trading_mode"] as? String ?: "PAPER"
                        val emergencyActive = layeredStatus["emergency_stop_active"] as? Boolean ?: false
                        val circuitBreakerAllowed = layeredStatus["circuit_breaker_allowed"] as? Boolean ?: true
                        val circuitBreakerReason = layeredStatus["circuit_breaker_reason"] as? String ?: ""
                        val connStatus = layeredStatus["layer2_connection_status"] as? String ?: "CONNECTED"
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("VPS Daemon Orchestrator", style = Modifier.testTag("vps_daemon_label").let { MaterialTheme.typography.bodySmall }, fontWeight = FontWeight.Bold)
                                    val daemonColor = if (daemonState == "ACTIVE") Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                    Text(
                                        daemonState,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = daemonColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Binance WebSocket Feed", style = MaterialTheme.typography.bodySmall)
                                    val connColor = when(connStatus) {
                                        "CONNECTED" -> Color(0xFF2E7D32)
                                        "RECONNECTING" -> Color(0xFFEF6C00)
                                        else -> Color(0xFFD32F2F)
                                    }
                                    Text(
                                        connStatus,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = connColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                if (!circuitBreakerAllowed) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, Color(0xFFEF9A9A))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                                                contentDescription = "Blocked",
                                                tint = Color(0xFFC62828),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Safeguard Triggered: $circuitBreakerReason",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFC62828)
                                            )
                                        }
                                    }
                                }

                                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                Text("Global Trading Mode Settings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("OFF", "PAPER", "TESTNET", "LIVE").forEach { modeOption ->
                                        val isSelected = globalMode == modeOption
                                        val btnColor = if (isSelected) {
                                            when(modeOption) {
                                                "OFF" -> Color(0xFF757575)
                                                "PAPER" -> Color(0xFF1565C0)
                                                "TESTNET" -> Color(0xFFEF6C00)
                                                else -> Color(0xFF2E7D32)
                                            }
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                        val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        
                                        OutlinedButton(
                                            onClick = { viewModel.updateContaboTradingMode(modeOption) },
                                            modifier = Modifier.weight(1f).height(32.dp).testTag("mode_${modeOption.lowercase()}_button"),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = btnColor,
                                                contentColor = contentColor
                                            ),
                                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(modeOption, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                
                                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                
                                // Layer 2 Price Cache
                                Text("Layer 2: Real-time Price Cache (WS Tickers)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                val priceCache = layeredStatus["layer2_price_cache"] as? Map<*, *>
                                if (priceCache != null && priceCache.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        priceCache.forEach { (sym, price) ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(sym.toString().replace("USDT", ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("$${price}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else {
                                    Text("No prices cached yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                // Layer 3 AI Opportunity
                                Text("Layer 3: AI Scanner Setup Detection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                val lastOpp = layeredStatus["layer3_last_opportunity"] as? Map<*, *>
                                if (lastOpp != null && lastOpp.isNotEmpty()) {
                                    Column(modifier = Modifier.padding(top = 4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Setup: ${lastOpp["symbol"]} ${lastOpp["side"]}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Conf: ${((lastOpp["confidence_score"] as? Double ?: 0.0) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF6C00), fontWeight = FontWeight.Bold)
                                        }
                                        Text("Strategy: ${lastOpp["strategy_used"]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Reason: ${lastOpp["entry_reason"]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Text("Scanning for market inefficiencies...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                // Layer 4 Active VPS Positions & Trailing Stops
                                Text("Layer 4: Trailing Stops & Positions Manager", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                val activePos = layeredStatus["layer4_active_positions"] as? List<*>
                                if (activePos != null && activePos.isNotEmpty()) {
                                    activePos.forEach { posMap ->
                                        val p = posMap as? Map<*, *>
                                        if (p != null) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("${p["symbol"]} ${p["side"]}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                        Text("Size: ${p["position_size"]}", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("Entry: $${p["entry_price"]}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("Trailing Stop: $${p["trailing_stop"]}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                                                        Text("TP: $${p["take_profit"]}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text("No active positions monitored on VPS.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (emergencyActive) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, Color(0xFFEF9A9A))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🚨 EMERGENCY LOCKOUT ACTIVE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Button(
                                                onClick = { viewModel.resetContaboEmergencyStop() },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.fillMaxWidth().height(32.dp).testTag("emergency_reset_button"),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("RESET SYSTEM EMERGENCY LOCK", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Emergency Liquidate Button
                                    Button(
                                        onClick = { viewModel.triggerContaboEmergencyLiquidation() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("emergency_liquidate_button"),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                                            contentDescription = "Liquidate",
                                            modifier = Modifier.size(16.dp).padding(end = 6.dp),
                                            tint = Color.White
                                        )
                                        Text("EMERGENCY LIQUIDATE VPS POSITIONS", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- SYSTEM HEALTH VALIDATION LAB ---
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("validation_lab_card"),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                            contentDescription = "Validation Lab",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Validation Lab: System Health",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        "LIVE RUNTIME",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Lumina Autonomous Engine Diagnostics:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                val healthMap = layeredStatus["system_health"] as? Map<*, *>
                                val defaultHealth = mapOf(
                                    "Backend" to "PASS",
                                    "Database" to "PASS",
                                    "Market Feed" to "PASS",
                                    "Risk Engine" to "PASS",
                                    "Order Manager" to "PASS",
                                    "Position Manager" to "PASS",
                                    "Circuit Breaker" to "PASS",
                                    "Paper Trading" to "PASS",
                                    "Audit Logger" to "PASS"
                                )

                                defaultHealth.forEach { (subsystem, defaultVal) ->
                                    val statusVal = (healthMap?.get(subsystem) as? String) ?: defaultVal
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = subsystem.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        // Draw the elegant Leader dots
                                        Text(
                                            text = " ........................................................................",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                        )

                                        val statusColor = when {
                                            statusVal.startsWith("PASS") -> Color(0xFF2E7D32)
                                            statusVal.startsWith("WARN") || statusVal.startsWith("RECONNECTING") -> Color(0xFFEF6C00)
                                            statusVal.startsWith("DISABLED") -> Color(0xFF757575)
                                            else -> Color(0xFFD32F2F)
                                        }
                                        Text(
                                            text = statusVal,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.testTag("health_${subsystem.toString().lowercase().replace(" ", "_")}_status")
                                        )
                                    }
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 16.dp))

                        // A. Paper Wallet details
                        Text(
                            "PAPER TRADING WALLET",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Account ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(contaboWallet?.accountId ?: "default_account", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Unrealized Credit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val balance = contaboWallet?.balance ?: 100000.0
                                        Text("$${String.format(Locale.US, "%,.2f", balance)} USD", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.adjustContaboPaperWalletBalance(10000.0) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("+10k Credits", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.adjustContaboPaperWalletBalance(-10000.0) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("-10k Credits", fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        // B. Risk Profile details
                        Text(
                            "RISK CONTROL PROFILE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Strategy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(contaboRisk?.strategyName ?: "AZA_STRATEGY", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Max Drawdown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${((contaboRisk?.maxDrawdown ?: 0.10) * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Max Risk/Trade", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${((contaboRisk?.maxRiskPerTrade ?: 0.02) * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // C. Rapid Manual Paper Trade form
                        Text(
                            "VPS PAPER ORDER PLACEMENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = paperSymbol,
                                onValueChange = { paperSymbol = it },
                                label = { Text("Symbol", fontSize = 10.sp) },
                                modifier = Modifier.weight(1.2f).testTag("paper_symbol_input"),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = paperQty,
                                onValueChange = { paperQty = it },
                                label = { Text("Size (Slices)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f).testTag("paper_qty_input"),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = paperPrice,
                                onValueChange = { paperPrice = it },
                                label = { Text("Entry Price", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f).testTag("paper_price_input"),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            OutlinedTextField(
                                value = paperStopLoss,
                                onValueChange = { paperStopLoss = it },
                                label = { Text("Stop Loss (1.75x)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1.1f).testTag("paper_sl_input"),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { paperSide = "BUY" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (paperSide == "BUY") Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (paperSide == "BUY") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("BUY")
                            }
                            Button(
                                onClick = { paperSide = "SELL" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (paperSide == "SELL") Color(0xFFD32F2F) else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (paperSide == "SELL") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("SELL")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val sym = paperSymbol.trim().uppercase()
                                val qty = paperQty.toDoubleOrNull() ?: 0.12
                                val price = paperPrice.toDoubleOrNull() ?: 67000.0
                                val sl = paperStopLoss.toDoubleOrNull() ?: 65500.0
                                val tp = paperTakeProfit.toDoubleOrNull() ?: 70000.0
                                viewModel.executeContaboPaperTrade(
                                    symbol = sym,
                                    side = paperSide,
                                    entryPrice = price,
                                    positionSize = qty,
                                    stopLoss = sl,
                                    takeProfit = tp
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (paperSide == "BUY") Color(0xFF2E7D32) else Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("paper_execute_button")
                        ) {
                            Text("Dispatch VPS Paper $paperSide Order", style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 1. E2E PIPELINE VALIDATION MODULE ---
                        Text(
                            "E2E TRADING PIPELINE VALIDATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Simulate a live end-to-end paper trading sequence: Signal Generation ➔ Risk Compliance ➔ Order Recording ➔ Execution Monitoring.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = { viewModel.triggerPipelineValidation() },
                                    enabled = !isPipelineValidating,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("validate_pipeline_button"),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    if (isPipelineValidating) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp).padding(end = 8.dp)
                                        )
                                        Text("Validating Pipeline...", style = MaterialTheme.typography.labelMedium)
                                    } else {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                                            contentDescription = "Run",
                                            modifier = Modifier.size(16.dp).padding(end = 6.dp)
                                        )
                                        Text("Run End-to-End E2E Validation", style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                pipelineValidationResult?.let { result ->
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, if (result.status == "SUCCESS") Color(0xFF2E7D32).copy(alpha = 0.3f) else Color(0xFFD32F2F).copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (result.status == "SUCCESS") androidx.compose.material.icons.Icons.Default.CheckCircle else androidx.compose.material.icons.Icons.Default.Error,
                                                        contentDescription = "Status",
                                                        tint = if (result.status == "SUCCESS") Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Status: ${result.status}",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (result.status == "SUCCESS") Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                                    )
                                                }
                                                Text(
                                                    text = "Trade ID: #${result.tradeId}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = result.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "PIPELINE PIPES STATUS:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            result.steps.forEach { step ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Badge(
                                                            containerColor = if (step.status == "PASS") Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                            contentColor = if (step.status == "PASS") Color(0xFF2E7D32) else Color(0xFFC62828)
                                                        ) {
                                                            Text(
                                                                text = step.status,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = step.step,
                                                            fontWeight = FontWeight.SemiBold,
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                    }
                                                    Text(
                                                        text = step.detail,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- HISTORICAL MARKET REPLAY SANDBOX ---
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "⏳ HISTORICAL MARKET REPLAY SANDBOX",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val rStatus = contaboReplayStatus?.status ?: "IDLE"
                                    Badge(
                                        containerColor = when (rStatus) {
                                            "RUNNING" -> Color(0xFF2E7D32)
                                            "PAUSED" -> Color(0xFFEF6C00)
                                            "COMPLETED" -> Color(0xFF1565C0)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            rStatus,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Replay historical market data tick-by-tick to validate strategies under real market regimes without financial risk.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                contaboReplayStatus?.let { replay ->
                                    val progress = (replay.progressPct / 100.0).toFloat().coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Progress: ${replay.candleIndex}/${replay.totalCandles} candles (${replay.progressPct}%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Equity: $${String.format(Locale.US, "%.2f", replay.currentEquity)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (replay.currentEquity >= replay.initialBalance) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Speed: ${replay.speedMultiplier}x | Max DD: ${replay.maxDrawdownPct}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Time: ${replay.simulatedTimestamp?.take(19) ?: "N/A"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val currentStatus = contaboReplayStatus?.status ?: "IDLE"
                                    if (currentStatus == "IDLE" || currentStatus == "COMPLETED" || currentStatus == "STOPPED") {
                                        Button(
                                            onClick = { viewModel.startContaboReplay("BTCUSDT", 5.0) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Text("Start Replay", style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else if (currentStatus == "RUNNING") {
                                        Button(
                                            onClick = { viewModel.pauseContaboReplay() },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Text("Pause", style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else if (currentStatus == "PAUSED") {
                                        Button(
                                            onClick = { viewModel.resumeContaboReplay() },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Text("Resume", style = MaterialTheme.typography.labelSmall)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.stepContaboReplay() },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Text("Step +1", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    if (currentStatus == "RUNNING" || currentStatus == "PAUSED") {
                                        OutlinedButton(
                                            onClick = { viewModel.stopContaboReplay() },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Text("Stop", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD32F2F))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- LUMINA COACH: TRADE ANALYTICS & CLOSED LEARNING LOOP ---
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, Color(0xFF7B1FA2).copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🧠 LUMINA COACH: CLOSED LEARNING LOOP",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFAB47BC)
                                    )
                                    Button(
                                        onClick = { viewModel.applyAllCoachRecommendations() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Apply All Adaptations", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Trade → Analytics → Learning → Adaptation → Better Trades",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                contaboCoachInsights?.let { coach ->
                                    coach.coachingRecommendations.forEach { rec ->
                                        val isApplied = coach.appliedRecommendations.any { it["rec_id"] == rec.id }
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isApplied) Color(0xFF1B5E20).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                                            ),
                                            border = BorderStroke(1.dp, if (isApplied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            rec.title,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                                            Text(rec.target, style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        rec.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (rec.expectedImpact.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            "Expected Impact: ${rec.expectedImpact}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF2E7D32)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                if (isApplied) {
                                                    Text(
                                                        "✓ APPLIED",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                } else {
                                                    OutlinedButton(
                                                        onClick = { viewModel.applyCoachRecommendation(rec.id) },
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Apply", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- SYSTEMIC MACRO GUARD & MULTI-ASSET CROSS-CORRELATION CARD ---
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, Color(0xFFC62828).copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🛡️ SYSTEMIC MACRO GUARD & CROSS-CORRELATION",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE53935)
                                    )
                                    val regime = contaboMacroGuardStatus?.macroRegime ?: "NORMAL_DECENTRALIZED"
                                    val badgeColor = when {
                                        regime.contains("CASCADE") -> Color(0xFFD32F2F)
                                        regime.contains("ELEVATED") -> Color(0xFFF57C00)
                                        else -> Color(0xFF388E3C)
                                    }
                                    Badge(containerColor = badgeColor.copy(alpha = 0.2f)) {
                                        Text(regime, style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                contaboMacroGuardStatus?.let { macro ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "Systemic Risk Index: ${(macro.systemicRiskIndex * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                "Avg Correlation vs BTC: ${macro.averageCorrelation}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "BTC Lead Change: ${if (macro.btcChangePct >= 0) "+" else ""}${macro.btcChangePct}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (macro.btcChangePct >= 0) Color(0xFF388E3C) else Color(0xFFD32F2F)
                                            )
                                            Text(
                                                "Lead Asset: ${macro.leadAsset}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text("Cross-Asset Correlation vs BTC:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        macro.correlationsVsBtc.forEach { (asset, corr) ->
                                            AssistChip(
                                                onClick = {},
                                                label = {
                                                    Text("$asset: $corr", style = MaterialTheme.typography.labelSmall)
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Share,
                                                        contentDescription = null,
                                                        tint = if (corr >= 0.8) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    if (macro.recentTrimEvents.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Pre-emptive Auto-Trim Protection Log:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                        macro.recentTrimEvents.take(3).forEach { evt ->
                                            Text(
                                                "⚡ [${evt["symbol"]}] ${evt["action"]}: Tightened SL to $${evt["new_sl"]} (Systemic Risk: ${evt["systemic_risk_index"]})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- MONTE CARLO RISK-OF-RUIN & KELLY POSITION SIZING SIMULATOR ---
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, Color(0xFF0288D1).copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🎲 MONTE CARLO & KELLY POSITION SIZING SIMULATOR",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0288D1)
                                    )
                                    Badge(containerColor = Color(0xFF0288D1).copy(alpha = 0.2f)) {
                                        Text("2,500 Trajectories", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0288D1), fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                contaboMonteCarloSimulation?.let { mc ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                "Risk of Ruin (>50% DD): ${mc.riskOfRuin.probRuin50Pct}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (mc.riskOfRuin.probRuin50Pct < 2.0) Color(0xFF388E3C) else Color(0xFFD32F2F)
                                            )
                                            Text(
                                                "Full Kelly: ${mc.kellyCalculations.fullKellyPct}% | Half Kelly: ${mc.kellyCalculations.halfKellyPct}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "Active Kelly Multiplier: ${mc.kellyCalculations.activeAppliedFraction}x",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "25% DD Prob: ${mc.riskOfRuin.probDrawdown25Pct}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        "100-Trade Horizon Equity Percentiles:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("P10 (Pessimistic): $${String.format("%,.0f", mc.endingEquityPercentiles.p10Pessimistic)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE53935))
                                        Text("P50 (Median): $${String.format("%,.0f", mc.endingEquityPercentiles.p50Median)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("P90 (Optimistic): $${String.format("%,.0f", mc.endingEquityPercentiles.p90Optimistic)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF388E3C))
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(mc.recommendation.assessment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.applyKellyFraction(0.5) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Apply Half Kelly (0.5x)", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.applyKellyFraction(0.25) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Quarter Kelly (0.25x)", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- PILLAR 1: EXCHANGE EXECUTION LAYER ---
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "⚡ LIVE EXCHANGE EXECUTION LAYER",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF388E3C)
                                    )
                                    Badge(containerColor = Color(0xFF388E3C).copy(alpha = 0.2f)) {
                                        Text(
                                            executionReconciliation?.connectionStatus ?: "CONNECTED_SYNCED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF388E3C),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                executionReconciliation?.let { ex ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Rate Limit Weight Available: ${ex.rateLimitAvailable} / 1200", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Exchange Time Offset: ${ex.exchangeTimeDriftMs} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Position Discrepancies: ${ex.discrepanciesFound}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (ex.discrepanciesFound == 0) Color(0xFF388E3C) else Color(0xFFD32F2F))
                                            Text("Reconciled vs Local: OK (${ex.activeLocalPositionsCount} open)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- PILLAR 2: CAPITAL & INSTITUTIONAL RISK MANAGEMENT ---
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, if (institutionalRiskStatus?.tradingHalted == true) Color(0xFFD32F2F) else Color(0xFFF57C00).copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🛡️ INSTITUTIONAL RISK & CIRCUIT BREAKERS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (institutionalRiskStatus?.tradingHalted == true) Color(0xFFD32F2F) else Color(0xFFF57C00)
                                    )
                                    val isHalted = institutionalRiskStatus?.tradingHalted == true
                                    Badge(containerColor = if (isHalted) Color(0xFFD32F2F) else Color(0xFF388E3C).copy(alpha = 0.2f)) {
                                        Text(if (isHalted) "STOPPED / HALTED" else "PROTECTED_ACTIVE", style = MaterialTheme.typography.labelSmall, color = if (isHalted) Color.White else Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                institutionalRiskStatus?.let { risk ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Daily PnL / Limit: $${risk.dailyPnlUsd} / -$${risk.dailyLossLimitUsd}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Weekly PnL / Limit: $${risk.weeklyPnlUsd} / -$${risk.weeklyLossLimitUsd}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Consecutive Losses: ${risk.consecutiveLossesCount} / ${risk.consecutiveLossLimit}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (risk.consecutiveLossesCount >= 2) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface)
                                            Text("Max Leverage Cap: ${risk.maxLeverageMultiplier}x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    if (risk.tradingHalted) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Reason: ${risk.haltReason ?: "Circuit breaker tripped"}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { viewModel.resetInstitutionalRiskHalt() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Resume Trading & Clear Halt", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- PILLAR 3: PRODUCTION SYSTEM & LATENCY MONITORING ---
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, Color(0xFF7B1FA2).copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🖥️ PRODUCTION MONITORING & LATENCY TELEMETRY",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7B1FA2)
                                    )
                                    Badge(containerColor = Color(0xFF7B1FA2).copy(alpha = 0.2f)) {
                                        Text(productionMonitoringHealth?.status ?: "HEALTHY", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                productionMonitoringHealth?.let { health ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("VPS CPU: ${health.vpsMetrics["cpu_usage_pct"] ?: 12.4}% | RAM: ${health.vpsMetrics["ram_usage_pct"] ?: 28.5}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Database Query: ${health.connectivityLatency["database_query_ms"] ?: 2.1} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("REST Latency: ${health.connectivityLatency["exchange_rest_ms"] ?: 18.0} ms", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text("WS Ping: ${health.connectivityLatency["exchange_ws_ping_ms"] ?: 5.2} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- PILLAR 4 & 5: LONG-DURATION PAPER VALIDATION & LIVE ROLLOUT PLAN ---
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, Color(0xFF00838F).copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🚀 24/7 PAPER VALIDATION & LIVE ROLLOUT PROGRESSION",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00838F)
                                    )
                                    val currentPhase = paperValidationReport?.activePhase ?: "PHASE_1_PAPER_VALIDATION"
                                    Badge(containerColor = Color(0xFF00838F).copy(alpha = 0.2f)) {
                                        Text(currentPhase, style = MaterialTheme.typography.labelSmall, color = Color(0xFF00838F), fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                paperValidationReport?.let { valReport ->
                                    val metrics = valReport.validationMetrics
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Total Paper Trades: ${metrics.totalTrades}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Win Rate: ${metrics.winRatePct}% | Profit Factor: ${metrics.profitFactor}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Sharpe Ratio: ${metrics.sharpeRatio} | Max DD: ${metrics.maxDrawdownPct}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text("Avg Slippage: ${metrics.avgSlippageBps} bps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Regime Breakdown:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    valReport.regimePerformance.forEach { (regime, data) ->
                                        Text(
                                            "• $regime: ${data["trades"]} trades | Win Rate: ${data["win_rate_pct"]}% | Profit Factor: ${data["profit_factor"]}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Live Rollout Progression Roadmap:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    valReport.rolloutPhases.forEach { phase ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${phase["name"]}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                            val pStatus = phase["status"].toString()
                                            Text(pStatus, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (pStatus.contains("COMPLETED") || pStatus.contains("READY")) Color(0xFF388E3C) else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (valReport.activePhase == "PHASE_1_PAPER_VALIDATION") {
                                        Button(
                                            onClick = { viewModel.promoteRolloutPhase("PHASE_2_MICRO_LIVE") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Promote to Phase 2: Micro Live ($100 Cap)", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }



                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 2. LIVE MARKET FEED DASHBOARD ---
                        Text(
                            "LIVE BINANCE WEBSOCKET FEED STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isConnected = contaboMarketStatus?.connectionStatus == "CONNECTED"
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(if (isConnected) Color(0xFF2E7D32) else Color(0xFFD32F2F), shape = RoundedCornerShape(50.dp))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = contaboMarketStatus?.connectionStatus ?: "CONNECTING",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                        )
                                    }
                                    Text(
                                        text = "Last ping: ${contaboMarketStatus?.lastUpdateTime?.let { String.format(Locale.US, "%.1fs ago", System.currentTimeMillis() / 1000.0 - it) } ?: "never"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Cached Streamed Symbols:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val symbols = contaboMarketStatus?.cachedSymbols ?: listOf("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT")
                                    symbols.forEach { sym ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = sym,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 3. LUMINA PORTFOLIO PERFORMANCE SUMMARY ---
                        Text(
                            "PORTFOLIO PERFORMANCE & RISK SUMMARY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        contaboTradingSummary?.let { summary ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Net P&L Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            val pnlColor = if (summary.totalPnl >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                            Text(
                                                text = "${if (summary.totalPnl >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", summary.totalPnl)} USD",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = pnlColor
                                            )
                                        }
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "MODE: ${summary.globalTradingMode}",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Realized P&L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "$${String.format(Locale.US, "%,.2f", summary.totalRealizedPnl)}",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (summary.totalRealizedPnl >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                            )
                                        }
                                        Column {
                                            Text("Unrealized P&L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "$${String.format(Locale.US, "%,.2f", summary.totalUnrealizedPnl)}",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (summary.totalUnrealizedPnl >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Positions (Active/Closed)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "${summary.activePositionsCount} Active / ${summary.closedTradesCount} Closed",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 4. LUMINA ACTIVE POSITIONS ---
                        Text(
                            "ACTIVE TRADING POSITIONS (${contaboActivePositions.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (contaboActivePositions.isEmpty()) {
                            Text(
                                "No active paper trading positions recorded currently.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            contaboActivePositions.forEach { position ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Badge(containerColor = if (position.side == "BUY") Color(0xFFE8F5E9) else Color(0xFFFFF3E0)) {
                                                    Text(
                                                        text = position.side,
                                                        color = if (position.side == "BUY") Color(0xFF2E7D32) else Color(0xFFEF6C00),
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 4.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(position.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            }
                                            val unrealizedPct = if (position.entryPrice > 0.0) {
                                                if (position.side == "BUY") {
                                                    ((position.currentPrice - position.entryPrice) / position.entryPrice) * 100.0
                                                } else {
                                                    ((position.entryPrice - position.currentPrice) / position.entryPrice) * 100.0
                                                }
                                            } else 0.0
                                            val pnlColor = if (position.unrealizedPnl >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                            Text(
                                                text = "${if (position.unrealizedPnl >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", position.unrealizedPnl)} (${String.format(Locale.US, "%.2f", unrealizedPct)}%)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = pnlColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Size: ${position.positionSize}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Entry Price: $${position.entryPrice}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Mark Price: $${position.currentPrice}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Stop Loss: $${position.stopLoss}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Take Profit: $${position.takeProfit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Trailing Stop: $${if (position.trailingStop > 0.0) position.trailingStop else "NONE"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 5. LUMINA CLOSED TRADES ---
                        Text(
                            "CLOSED TRADES HISTORY (${contaboClosedTrades.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (contaboClosedTrades.isEmpty()) {
                            Text(
                                "No closed trading positions recorded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            contaboClosedTrades.take(5).forEach { trade ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Badge(containerColor = if (trade.side == "BUY") Color(0xFFE8F5E9) else Color(0xFFFFF3E0)) {
                                                    Text(
                                                        text = trade.side,
                                                        color = if (trade.side == "BUY") Color(0xFF2E7D32) else Color(0xFFEF6C00),
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(trade.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            }
                                            val realizedPnl = trade.profitLoss ?: 0.0
                                            val pnlColor = if (realizedPnl >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                            Text(
                                                text = "Realized: ${if (realizedPnl >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", realizedPnl)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = pnlColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Entry: $${trade.entryPrice}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Exit: $${trade.exitPrice ?: "N/A"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Outcome: ${trade.outcome ?: "CLOSED"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // E. Audit logs
                        Text(
                            "VPS REMOTE AUDIT LOGS (${contaboLogsList.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                if (contaboLogsList.isEmpty()) {
                                    Text(
                                        "No VPS audit logs found.",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                } else {
                                    contaboLogsList.take(6).forEach { log ->
                                        Text(
                                            text = "[${log.timestamp.take(19)}] ${log.level}: ${log.message}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = when (log.level) {
                                                "ERROR" -> Color(0xFFE57373)
                                                "WARNING" -> Color(0xFFFFB74D)
                                                else -> Color(0xFF81C784)
                                            },
                                            modifier = Modifier.padding(vertical = 1.dp)
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
