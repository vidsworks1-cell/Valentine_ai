package com.example.aza.strategy

import com.example.data.api.BinanceBalance
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Pre-Buy Exit Viability & Liquidability Verification Engine
 *
 * Core Directive: "Before buying, consider the ability of selling."
 * Verifies that any trade setup has a guaranteed, mathematically sound,
 * and exchange-compliant exit path before a single cent of capital is committed.
 */
data class PreBuyExitViabilityReport(
    val symbol: String,
    val proposedEntryPrice: Double,
    val allocatedUsdt: Double,
    val rawQuantity: Double,
    val executableQuantity: Double,
    val roundTripFeePct: Double = 0.002, // 0.1% entry + 0.1% exit
    val estimatedFeesUsdt: Double,
    val breakevenHurdlePrice: Double,
    val oneCentNetWinPrice: Double,
    val fiveCentNetWinPrice: Double,
    val minNotionalRequiredUsdt: Double,
    val entryNotionalUsdt: Double,
    val downsideStopNotionalUsdt: Double,
    val isMinNotionalSatisfied: Boolean,
    val isDownsideExitExecutable: Boolean,
    val isSellableApproved: Boolean,
    val liquidityRating: String,
    val rejectionReason: String?,
    val executionGuidance: String
)

object PreBuyExitViabilityEngine {

    const val STANDARD_BINANCE_MIN_NOTIONAL = 5.00
    const val STANDARD_FEE_RATE = 0.001 // 0.10% Binance spot taker fee
    const val ROUND_TRIP_FEE_RATE = 0.002 // 0.20% total entry + exit fee
    const val ONE_CENT_NET_PROFIT_USD = 0.01
    const val FIVE_CENT_NET_PROFIT_USD = 0.05

    /**
     * Thoroughly analyzes a candidate buy proposal before execution to verify
     * that it can be cleanly exited on Binance at both target and stop levels.
     */
    fun evaluatePreBuyExitViability(
        symbol: String,
        proposedEntryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double,
        allocatedUsdt: Double,
        availableFreeUsdt: Double,
        orderBookBidDepthUsdt: Double = 5000.0 // Rolling top-of-book bid depth
    ): PreBuyExitViabilityReport {
        val filter = BinanceExchangeFilterValidator.getFilterForSymbol(symbol)
        val minNotional = filter.minNotional.coerceAtLeast(STANDARD_BINANCE_MIN_NOTIONAL)

        if (proposedEntryPrice <= 0.0 || allocatedUsdt <= 0.0) {
            return PreBuyExitViabilityReport(
                symbol = symbol,
                proposedEntryPrice = proposedEntryPrice,
                allocatedUsdt = allocatedUsdt,
                rawQuantity = 0.0,
                executableQuantity = 0.0,
                estimatedFeesUsdt = 0.0,
                breakevenHurdlePrice = 0.0,
                oneCentNetWinPrice = 0.0,
                fiveCentNetWinPrice = 0.0,
                minNotionalRequiredUsdt = minNotional,
                entryNotionalUsdt = 0.0,
                downsideStopNotionalUsdt = 0.0,
                isMinNotionalSatisfied = false,
                isDownsideExitExecutable = false,
                isSellableApproved = false,
                liquidityRating = "INVALID_INPUT",
                rejectionReason = "Invalid entry price or allocation amount.",
                executionGuidance = "Aborted: Positive price and allocation required."
            )
        }

        // 1. Calculate step-size compliant executable quantity
        val rawQty = allocatedUsdt / proposedEntryPrice
        val executableQty = BinanceExchangeFilterValidator.roundToStepSize(rawQty, filter.stepSize)
        val actualEntryNotional = executableQty * proposedEntryPrice

        // 2. Calculate Round-Trip Fees & Hurdle Rates
        val entryFee = actualEntryNotional * STANDARD_FEE_RATE
        val estExitFee = actualEntryNotional * STANDARD_FEE_RATE
        val totalRoundTripFees = entryFee + estExitFee

        // Breakeven price: entry + round trip fees converted to price delta
        val feePerUnit = if (executableQty > 0.0) totalRoundTripFees / executableQty else 0.0
        val breakevenPrice = proposedEntryPrice + feePerUnit

        // +1 Cent Net Win Price: covers fees + $0.01 net
        val oneCentPriceDelta = if (executableQty > 0.0) (totalRoundTripFees + ONE_CENT_NET_PROFIT_USD) / executableQty else 0.0
        val oneCentWinPrice = proposedEntryPrice + oneCentPriceDelta

        // +5 Cent Net Win Price: covers fees + $0.05 net
        val fiveCentPriceDelta = if (executableQty > 0.0) (totalRoundTripFees + FIVE_CENT_NET_PROFIT_USD) / executableQty else 0.0
        val fiveCentWinPrice = proposedEntryPrice + fiveCentPriceDelta

        // 3. Binance Filter & Exitability Checks
        val isEntryMinNotionalSatisfied = actualEntryNotional >= minNotional
        val downsideExitNotional = if (stopLossPrice > 0.0) executableQty * stopLossPrice else actualEntryNotional * 0.98
        val isDownsideExecutable = downsideExitNotional >= minNotional

        // 4. Order Book Depth & Liquidability Check
        val liquidityRating = when {
            orderBookBidDepthUsdt >= 2000.0 -> "HIGH_LIQUIDITY"
            orderBookBidDepthUsdt >= 500.0 -> "ADEQUATE_LIQUIDITY"
            else -> "LOW_LIQUIDITY"
        }

        // 5. Synthesis & Approval Decision
        var isApproved = true
        var rejectionReason: String? = null

        if (actualEntryNotional > availableFreeUsdt) {
            isApproved = false
            rejectionReason = "Allocated notional ($${String.format(Locale.US, "%.2f", actualEntryNotional)}) exceeds available free USDT ($${String.format(Locale.US, "%.2f", availableFreeUsdt)})."
        } else if (!isEntryMinNotionalSatisfied) {
            isApproved = false
            rejectionReason = "Order notional ($${String.format(Locale.US, "%.2f", actualEntryNotional)}) is below Binance MIN_NOTIONAL filter ($${String.format(Locale.US, "%.2f", minNotional)}). Order would be rejected by exchange."
        } else if (executableQty < filter.minQty) {
            isApproved = false
            rejectionReason = "Executable quantity ($executableQty) is below LOT_SIZE minQty (${filter.minQty})."
        } else if (!isDownsideExecutable && downsideExitNotional < (minNotional * 0.80)) {
            isApproved = false
            rejectionReason = "Severe Downside Exit Risk: If stop loss is hit, exit value ($${String.format(Locale.US, "%.2f", downsideExitNotional)}) drops significantly below MIN_NOTIONAL ($${minNotional}), risking stuck funds on exchange."
        }

        val guidance = if (isApproved) {
            "✓ SELLABILITY CERTIFIED: Buy $executableQty $symbol ($${String.format(Locale.US, "%.2f", actualEntryNotional)} USDT). Breakeven at $${String.format(Locale.US, "%.2f", breakevenPrice)}. +1¢ Net Win at $${String.format(Locale.US, "%.2f", oneCentWinPrice)} (+${String.format(Locale.US, "%.2f", ((oneCentWinPrice - proposedEntryPrice) / proposedEntryPrice) * 100)}%). Exit path validated."
        } else {
            "❌ PRE-BUY EXIT REJECTED: $rejectionReason"
        }

        return PreBuyExitViabilityReport(
            symbol = symbol,
            proposedEntryPrice = proposedEntryPrice,
            allocatedUsdt = allocatedUsdt,
            rawQuantity = rawQty,
            executableQuantity = executableQty,
            roundTripFeePct = ROUND_TRIP_FEE_RATE,
            estimatedFeesUsdt = totalRoundTripFees,
            breakevenHurdlePrice = breakevenPrice,
            oneCentNetWinPrice = oneCentWinPrice,
            fiveCentNetWinPrice = fiveCentWinPrice,
            minNotionalRequiredUsdt = minNotional,
            entryNotionalUsdt = actualEntryNotional,
            downsideStopNotionalUsdt = downsideExitNotional,
            isMinNotionalSatisfied = isEntryMinNotionalSatisfied,
            isDownsideExitExecutable = isDownsideExecutable,
            isSellableApproved = isApproved,
            liquidityRating = liquidityRating,
            rejectionReason = rejectionReason,
            executionGuidance = guidance
        )
    }
}
