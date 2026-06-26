package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.data.database.PortfolioAsset
import com.example.viewmodel.AppViewModel
import java.util.Locale

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 8.dp
        ),
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
