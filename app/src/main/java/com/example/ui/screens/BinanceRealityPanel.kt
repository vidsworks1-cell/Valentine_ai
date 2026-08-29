package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.BinanceBalance
import com.example.data.api.BinanceOpenOrder
import com.example.data.api.ContaboActivePosition
import com.example.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BinanceRealityTab(val label: String) {
    ALL("Overview"),
    OPEN_ORDERS("Open Orders"),
    BALANCES("Balances"),
    ACTIVE_POSITIONS("Positions")
}

@Composable
fun BinanceRealityPanel(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val binanceOrders by viewModel.binanceOpenOrders.collectAsStateWithLifecycle()
    val binanceBalances by viewModel.binanceBalances.collectAsStateWithLifecycle()
    val activePositions by viewModel.contaboActivePositions.collectAsStateWithLifecycle()
    val apiStatus by viewModel.binanceApiStatus.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isBinanceRealityRefreshing.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastBinanceRealitySyncTime.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(BinanceRealityTab.ALL) }
    var hideZeroBalances by remember { mutableStateOf(true) }
    var orderToCancel by remember { mutableStateOf<BinanceOpenOrder?>(null) }

    val nonZeroBalances = remember(binanceBalances) {
        binanceBalances.filter { it.free > 0.00000001 || it.locked > 0.00000001 }
    }

    val displayBalances = if (hideZeroBalances) nonZeroBalances else binanceBalances

    val timeFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("binance_reality_panel"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title, Ground Truth Badge, Refresh Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (apiStatus?.isConnected == true) Color(0xFF10B981)
                                    else Color(0xFFF59E0B)
                                )
                        )
                        Text(
                            text = "BINANCE REALITY PANEL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = "Verified Exchange Truth • Zero AI Assumptions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Refresh Button with Spinner
                FilledTonalIconButton(
                    onClick = { viewModel.refreshBinanceRealityData() },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("refresh_binance_reality_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Binance Reality Data",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Status & Verification Telemetry Row
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Verified Source",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Endpoints: /openOrders • /account • PositionManager",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "Synced: ${timeFormatter.format(Date(lastSyncTime))}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tab Row
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                BinanceRealityTab.values().forEachIndexed { index, tab ->
                    val count = when (tab) {
                        BinanceRealityTab.OPEN_ORDERS -> binanceOrders.size
                        BinanceRealityTab.BALANCES -> nonZeroBalances.size
                        BinanceRealityTab.ACTIVE_POSITIONS -> activePositions.size
                        BinanceRealityTab.ALL -> null
                    }
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = BinanceRealityTab.values().size),
                        modifier = Modifier.testTag("reality_tab_${tab.name.lowercase()}")
                    ) {
                        Text(
                            text = if (count != null) "${tab.label} ($count)" else tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }

            // Content Sections based on selected Tab
            when (selectedTab) {
                BinanceRealityTab.ALL -> {
                    RealityOverviewSection(
                        orders = binanceOrders,
                        balances = displayBalances,
                        positions = activePositions,
                        timeFormatter = timeFormatter,
                        onCancelOrder = { orderToCancel = it },
                        hideZeroBalances = hideZeroBalances,
                        onToggleHideZero = { hideZeroBalances = !hideZeroBalances }
                    )
                }
                BinanceRealityTab.OPEN_ORDERS -> {
                    RealityOpenOrdersSection(
                        orders = binanceOrders,
                        timeFormatter = timeFormatter,
                        onCancelOrder = { orderToCancel = it }
                    )
                }
                BinanceRealityTab.BALANCES -> {
                    RealityBalancesSection(
                        balances = displayBalances,
                        totalNonZeroCount = nonZeroBalances.size,
                        hideZeroBalances = hideZeroBalances,
                        onToggleHideZero = { hideZeroBalances = !hideZeroBalances }
                    )
                }
                BinanceRealityTab.ACTIVE_POSITIONS -> {
                    RealityPositionsSection(
                        positions = activePositions
                    )
                }
            }

            // Anti-Hallucination Disclaimer Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Raw backend JSON only. Never contains AI predictions, simulated fills, or hypothetical orders.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Cancellation Confirmation Dialog
    if (orderToCancel != null) {
        val order = orderToCancel ?: return@BinanceRealityPanel
        AlertDialog(
            onDismissRequest = { orderToCancel = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Cancel Binance Open Order") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Are you sure you want to cancel open order on exchange?")
                    Text("Order ID: #${order.orderId}", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("Symbol: ${order.symbol} (${order.side} ${order.origQty} @ $${order.price})")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelActiveBinanceOrder(order.symbol, order.orderId.toString())
                        orderToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_cancel_order_button")
                ) {
                    Text("Confirm Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToCancel = null }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
private fun RealityOverviewSection(
    orders: List<BinanceOpenOrder>,
    balances: List<BinanceBalance>,
    positions: List<ContaboActivePosition>,
    timeFormatter: SimpleDateFormat,
    onCancelOrder: (BinanceOpenOrder) -> Unit,
    hideZeroBalances: Boolean,
    onToggleHideZero: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Open Orders Mini Summary
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1. VERIFIED OPEN ORDERS (/openOrders)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = CircleShape,
                    color = if (orders.isNotEmpty()) Color(0xFF3B82F6).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "${orders.size} ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (orders.isNotEmpty()) Color(0xFF3B82F6) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (orders.isEmpty()) {
                EmptyRealityCard(
                    icon = Icons.Default.CheckCircleOutline,
                    title = "No Open Orders on Binance",
                    description = "GET /openOrders returned 0 open orders. All orders filled or cancelled."
                )
            } else {
                orders.take(3).forEach { order ->
                    BinanceOrderItemCard(order = order, timeFormatter = timeFormatter, onCancel = { onCancelOrder(order) })
                }
            }
        }

        // 2. Active Positions Mini Summary
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. ACTIVE POSITIONS (PositionManager)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = CircleShape,
                    color = if (positions.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "${positions.size} RUNNING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (positions.isNotEmpty()) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (positions.isEmpty()) {
                EmptyRealityCard(
                    icon = Icons.Default.TrendingFlat,
                    title = "No Open Positions in PositionManager",
                    description = "PositionManager backend reports 0 active exposure. Wallet is in cash/safe state."
                )
            } else {
                positions.take(2).forEach { pos ->
                    PositionItemCard(position = pos)
                }
            }
        }

        // 3. Spot Balances Mini Summary
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3. SPOT BALANCES (/account)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = onToggleHideZero,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (hideZeroBalances) "Show All" else "Hide Zero",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (balances.isEmpty()) {
                EmptyRealityCard(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "No Wallet Balances",
                    description = "No assets found or API returned empty balance payload."
                )
            } else {
                balances.take(4).forEach { balance ->
                    BalanceItemCard(balance = balance)
                }
            }
        }
    }
}

@Composable
private fun RealityOpenOrdersSection(
    orders: List<BinanceOpenOrder>,
    timeFormatter: SimpleDateFormat,
    onCancelOrder: (BinanceOpenOrder) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Open Orders from Binance Exchange",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${orders.size} Total Orders",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (orders.isEmpty()) {
            EmptyRealityCard(
                icon = Icons.Default.CheckCircle,
                title = "No Open Orders on Exchange",
                description = "Verified directly against Binance /openOrders. There are currently no pending limit or market orders on the book."
            )
        } else {
            orders.forEach { order ->
                BinanceOrderItemCard(
                    order = order,
                    timeFormatter = timeFormatter,
                    onCancel = { onCancelOrder(order) }
                )
            }
        }
    }
}

@Composable
private fun BinanceOrderItemCard(
    order: BinanceOpenOrder,
    timeFormatter: SimpleDateFormat,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("binance_order_${order.orderId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Symbol, Side badge, Status badge, Cancel button
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
                        text = order.symbol.ifBlank { "UNKNOWN" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val isBuy = order.side.equals("BUY", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isBuy) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = order.side.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isBuy) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = order.type.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Required Order Fields Grid: exchange orderId, status, executedQty, price, timestamp
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Exchange Order ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("#${order.orderId}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(order.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${order.price}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Executed Qty / Orig", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${order.executedQty} / ${order.origQty}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Exchange Timestamp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (order.time > 0) timeFormatter.format(Date(order.time)) else "N/A",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun RealityBalancesSection(
    balances: List<BinanceBalance>,
    totalNonZeroCount: Int,
    hideZeroBalances: Boolean,
    onToggleHideZero: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Spot Account Balances",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Verified from Binance /account",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilterChip(
                selected = hideZeroBalances,
                onClick = onToggleHideZero,
                label = {
                    Text(if (hideZeroBalances) "Non-Zero ($totalNonZeroCount)" else "Showing All (${balances.size})", fontSize = 11.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (hideZeroBalances) Icons.Default.FilterList else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }

        if (balances.isEmpty()) {
            EmptyRealityCard(
                icon = Icons.Default.AccountBalanceWallet,
                title = "No Balances Found",
                description = "Binance /account returned empty or no assets matched the active filter."
            )
        } else {
            balances.forEach { balance ->
                BalanceItemCard(balance = balance)
            }
        }
    }
}

@Composable
private fun BalanceItemCard(balance: BinanceBalance) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("binance_balance_${balance.asset}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = balance.asset.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column {
                    Text(
                        text = balance.asset,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Total: ${String.format(Locale.US, "%.6f", balance.free + balance.locked)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Free: ${String.format(Locale.US, "%.6f", balance.free)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
                if (balance.locked > 0.0) {
                    Text(
                        text = "Locked: ${String.format(Locale.US, "%.6f", balance.locked)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}

@Composable
private fun RealityPositionsSection(
    positions: List<ContaboActivePosition>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Active Positions (PositionManager)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Single Source of Truth on Backend",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = if (positions.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = "${positions.size} ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (positions.isNotEmpty()) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        if (positions.isEmpty()) {
            EmptyRealityCard(
                icon = Icons.Default.TrendingFlat,
                title = "No Active Positions",
                description = "PositionManager reports 0 registered active positions. No live open market risk."
            )
        } else {
            positions.forEach { pos ->
                PositionItemCard(position = pos)
            }
        }
    }
}

@Composable
private fun PositionItemCard(position: ContaboActivePosition) {
    val isProfit = position.unrealizedPnl >= 0.0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("binance_position_${position.symbol}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                        text = position.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (position.side.equals("BUY", true) || position.side.equals("LONG", true))
                            Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = position.side.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (position.side.equals("BUY", true) || position.side.equals("LONG", true))
                                Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "${if (isProfit) "+" else ""}$${String.format(Locale.US, "%.2f", position.unrealizedPnl)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = if (isProfit) Color(0xFF10B981) else Color(0xFFEF4444)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Entry Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${String.format(Locale.US, "%.2f", position.entryPrice)}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Current Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${String.format(Locale.US, "%.2f", position.currentPrice)}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Position Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${position.positionSize}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("SL: $${String.format(Locale.US, "%.2f", position.stopLoss)}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = Color(0xFFEF4444))
                Text("TP: $${String.format(Locale.US, "%.2f", position.takeProfit)}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = Color(0xFF10B981))
                Text("Trail: $${String.format(Locale.US, "%.2f", position.trailingStop)}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyRealityCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
