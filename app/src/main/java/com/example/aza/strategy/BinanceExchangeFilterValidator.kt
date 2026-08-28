package com.example.aza.strategy

import com.example.data.api.BinanceBalance
import java.util.Locale
import kotlin.math.floor
import kotlin.math.round

/**
 * Standard symbol filter parameters for Binance Spot markets.
 */
data class BinanceSymbolFilter(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String = "USDT",
    val minQty: Double = 0.00001,
    val maxQty: Double = 9000.0,
    val stepSize: Double = 0.00001,
    val minNotional: Double = 5.00, // Standard Binance Spot minimum order value ($5 USDT)
    val pricePrecision: Int = 2,
    val qtyPrecision: Int = 5
)

enum class FilterValidationStatus {
    VALID_FOR_EXECUTION,
    DUST_POSITION,
    INSUFFICIENT_FREE_BALANCE,
    LOCKED_IN_OPEN_ORDERS,
    ZERO_EXCHANGE_HOLDING,
    BELOW_MIN_QTY
}

data class FilterValidationResult(
    val status: FilterValidationStatus,
    val isValid: Boolean,
    val symbol: String,
    val requestedQty: Double,
    val executableQty: Double,
    val currentPrice: Double,
    val notionalValueUsd: Double,
    val minNotionalRequired: Double,
    val availableFreeSpot: Double,
    val lockedSpot: Double,
    val diagnosticMessage: String,
    val suggestedAction: String
)

object BinanceExchangeFilterValidator {

    // Known symbol filter specifications for common pairs on Binance Spot
    private val defaultFilters = mapOf(
        "BTCUSDT" to BinanceSymbolFilter("BTCUSDT", "BTC", "USDT", minQty = 0.00001, stepSize = 0.00001, minNotional = 5.0, pricePrecision = 2, qtyPrecision = 5),
        "ETHUSDT" to BinanceSymbolFilter("ETHUSDT", "ETH", "USDT", minQty = 0.0001, stepSize = 0.0001, minNotional = 5.0, pricePrecision = 2, qtyPrecision = 4),
        "SOLUSDT" to BinanceSymbolFilter("SOLUSDT", "SOL", "USDT", minQty = 0.01, stepSize = 0.01, minNotional = 5.0, pricePrecision = 2, qtyPrecision = 2),
        "BNBUSDT" to BinanceSymbolFilter("BNBUSDT", "BNB", "USDT", minQty = 0.001, stepSize = 0.001, minNotional = 5.0, pricePrecision = 2, qtyPrecision = 3),
        "ADAUSDT" to BinanceSymbolFilter("ADAUSDT", "ADA", "USDT", minQty = 0.1, stepSize = 0.1, minNotional = 5.0, pricePrecision = 4, qtyPrecision = 1),
        "XRPUSDT" to BinanceSymbolFilter("XRPUSDT", "XRP", "USDT", minQty = 0.1, stepSize = 0.1, minNotional = 5.0, pricePrecision = 4, qtyPrecision = 1),
        "DOGEUSDT" to BinanceSymbolFilter("DOGEUSDT", "DOGE", "USDT", minQty = 1.0, stepSize = 1.0, minNotional = 5.0, pricePrecision = 5, qtyPrecision = 0),
        "AVAXUSDT" to BinanceSymbolFilter("AVAXUSDT", "AVAX", "USDT", minQty = 0.01, stepSize = 0.01, minNotional = 5.0, pricePrecision = 2, qtyPrecision = 2),
        "LINKUSDT" to BinanceSymbolFilter("LINKUSDT", "LINK", "USDT", minQty = 0.01, stepSize = 0.01, minNotional = 5.0, pricePrecision = 3, qtyPrecision = 2),
        "DOTUSDT" to BinanceSymbolFilter("DOTUSDT", "DOT", "USDT", minQty = 0.01, stepSize = 0.01, minNotional = 5.0, pricePrecision = 3, qtyPrecision = 2),
        "NEARUSDT" to BinanceSymbolFilter("NEARUSDT", "NEAR", "USDT", minQty = 0.1, stepSize = 0.1, minNotional = 5.0, pricePrecision = 3, qtyPrecision = 1),
        "SUIUSDT" to BinanceSymbolFilter("SUIUSDT", "SUI", "USDT", minQty = 0.1, stepSize = 0.1, minNotional = 5.0, pricePrecision = 4, qtyPrecision = 1)
    )

    fun getFilterForSymbol(symbol: String): BinanceSymbolFilter {
        val cleanSym = symbol.uppercase().trim()
        val existing = defaultFilters[cleanSym]
        if (existing != null) return existing

        val base = cleanSym
            .removeSuffix("USDT")
            .removeSuffix("USDC")
            .removeSuffix("FDUSD")
            .removeSuffix("BUSD")
            .ifBlank { "UNKNOWN" }

        return BinanceSymbolFilter(
            symbol = cleanSym,
            baseAsset = base,
            quoteAsset = "USDT",
            minQty = 0.0001,
            stepSize = 0.0001,
            minNotional = 5.00,
            pricePrecision = 2,
            qtyPrecision = 4
        )
    }

    /**
     * Strictly rounds a quantity down to the allowed stepSize for the given symbol.
     */
    fun roundToStepSize(qty: Double, stepSize: Double): Double {
        if (stepSize <= 0.0) return qty
        val steps = floor(qty / stepSize)
        val rounded = steps * stepSize
        val decimalPlaces = getDecimalPlaces(stepSize)
        val factor = Math.pow(10.0, decimalPlaces.toDouble())
        return floor(rounded * factor) / factor
    }

    private fun getDecimalPlaces(value: Double): Int {
        val str = String.format(Locale.US, "%.8f", value).trimEnd('0')
        val dotIdx = str.indexOf('.')
        return if (dotIdx >= 0) (str.length - dotIdx - 1).coerceAtLeast(0) else 0
    }

    /**
     * Comprehensive pre-sell / exit validation.
     * Validates:
     * 1. Exchange Spot Balances (Free vs Locked)
     * 2. LOT_SIZE (stepSize & minQty rounding)
     * 3. MIN_NOTIONAL (order value >= $5.00 USDT)
     * 4. Dust Position Detection
     */
    fun validateSellOrder(
        symbol: String,
        requestedQty: Double,
        currentPrice: Double,
        spotBalances: List<BinanceBalance>
    ): FilterValidationResult {
        val filter = getFilterForSymbol(symbol)
        val baseAsset = filter.baseAsset
        val balance = spotBalances.find { it.asset.equals(baseAsset, ignoreCase = true) }
        val free = balance?.free ?: 0.0
        val locked = balance?.locked ?: 0.0
        val totalHeld = free + locked

        // 1. Check for absolute 0 holding
        if (totalHeld <= 0.00000001) {
            return FilterValidationResult(
                status = FilterValidationStatus.ZERO_EXCHANGE_HOLDING,
                isValid = false,
                symbol = symbol,
                requestedQty = requestedQty,
                executableQty = 0.0,
                currentPrice = currentPrice,
                notionalValueUsd = 0.0,
                minNotionalRequired = filter.minNotional,
                availableFreeSpot = 0.0,
                lockedSpot = 0.0,
                diagnosticMessage = "Zero $baseAsset holding on Binance Spot (0.00 held). Local position is a ghost record.",
                suggestedAction = "Auto-archive local position without dispatching impossible sell order to Binance."
            )
        }

        // 2. Check if held entirely in locked orders
        if (free <= 0.00000001 && locked > 0.00000001) {
            return FilterValidationResult(
                status = FilterValidationStatus.LOCKED_IN_OPEN_ORDERS,
                isValid = false,
                symbol = symbol,
                requestedQty = requestedQty,
                executableQty = 0.0,
                currentPrice = currentPrice,
                notionalValueUsd = locked * currentPrice,
                minNotionalRequired = filter.minNotional,
                availableFreeSpot = 0.0,
                lockedSpot = locked,
                diagnosticMessage = "All $baseAsset is locked in active Binance limit orders ($locked $baseAsset locked).",
                suggestedAction = "Cancel open limit orders on Binance before submitting market exit order."
            )
        }

        // 3. Determine usable quantity capped to available free balance
        val rawUsableQty = if (requestedQty > 0.0) requestedQty.coerceAtMost(free) else free
        val stepAdjustedQty = roundToStepSize(rawUsableQty, filter.stepSize)
        val notionalValue = stepAdjustedQty * currentPrice

        // 4. Check MIN_QTY
        if (stepAdjustedQty < filter.minQty) {
            return FilterValidationResult(
                status = FilterValidationStatus.BELOW_MIN_QTY,
                isValid = false,
                symbol = symbol,
                requestedQty = requestedQty,
                executableQty = stepAdjustedQty,
                currentPrice = currentPrice,
                notionalValueUsd = notionalValue,
                minNotionalRequired = filter.minNotional,
                availableFreeSpot = free,
                lockedSpot = locked,
                diagnosticMessage = "Adjusted quantity ($stepAdjustedQty $baseAsset) is below Binance LOT_SIZE minimum (${filter.minQty}).",
                suggestedAction = "Hold or convert dust holding via Binance mobile app."
            )
        }

        // 5. Check MIN_NOTIONAL (Binance requires value >= 5.00 USDT)
        if (notionalValue < filter.minNotional) {
            return FilterValidationResult(
                status = FilterValidationStatus.DUST_POSITION,
                isValid = false,
                symbol = symbol,
                requestedQty = requestedQty,
                executableQty = stepAdjustedQty,
                currentPrice = currentPrice,
                notionalValueUsd = notionalValue,
                minNotionalRequired = filter.minNotional,
                availableFreeSpot = free,
                lockedSpot = locked,
                diagnosticMessage = "Order value ($${String.format(Locale.US, "%.2f", notionalValue)}) is below Binance MIN_NOTIONAL ($${filter.minNotional} USDT). Exchange will reject standard market sell.",
                suggestedAction = "Flagged as DUST POSITION. Archive internally or convert to BNB via Binance app."
            )
        }

        // 6. Check if requested quantity exceeds free balance
        if (requestedQty > free + 0.00000001) {
            return FilterValidationResult(
                status = FilterValidationStatus.INSUFFICIENT_FREE_BALANCE,
                isValid = false,
                symbol = symbol,
                requestedQty = requestedQty,
                executableQty = stepAdjustedQty,
                currentPrice = currentPrice,
                notionalValueUsd = notionalValue,
                minNotionalRequired = filter.minNotional,
                availableFreeSpot = free,
                lockedSpot = locked,
                diagnosticMessage = "Requested size ($requestedQty) exceeds free Spot balance ($free $baseAsset).",
                suggestedAction = "Execute partial sell with available free quantity ($stepAdjustedQty $baseAsset)."
            )
        }

        // 7. All filters verified!
        return FilterValidationResult(
            status = FilterValidationStatus.VALID_FOR_EXECUTION,
            isValid = true,
            symbol = symbol,
            requestedQty = requestedQty,
            executableQty = stepAdjustedQty,
            currentPrice = currentPrice,
            notionalValueUsd = notionalValue,
            minNotionalRequired = filter.minNotional,
            availableFreeSpot = free,
            lockedSpot = locked,
            diagnosticMessage = "Verified LOT_SIZE, MIN_NOTIONAL ($${String.format(Locale.US, "%.2f", notionalValue)} >= $${filter.minNotional}), and available Spot balance ($free $baseAsset).",
            suggestedAction = "Authorized for Binance market/limit sell."
        )
    }
}
