package com.example.aza.strategy

import com.example.data.api.BinanceBalance
import com.example.data.api.BinanceOpenOrder
import com.example.data.database.BinancePositionEntity
import java.util.Locale

/**
 * Execution Profile adapting automatically based on account equity size.
 */
enum class AccountRiskProfileType {
    MICRO_ACCOUNT_MODE,     // Total Balance <= $30 USDT (e.g. ~13 USDT)
    SMALL_ACCOUNT_MODE,     // $30 - $250 USDT
    STANDARD_PORTFOLIO_MODE // > $250 USDT
}

data class MicroAccountRiskConfig(
    val profileType: AccountRiskProfileType = AccountRiskProfileType.MICRO_ACCOUNT_MODE,
    val maxActivePositions: Int = 1,
    val feeReserveUsdt: Double = 0.75, // Guarantee at least 0.50 - 1.00 USDT reserved for fees/slippage
    val maxCapitalLockupRatio: Double = 0.85,
    val minConfidenceThreshold: Double = 0.80, // Require >= 80% conviction for micro capital
    val minExpectedRR: Double = 2.0,
    val allowCompetingLimitOrders: Boolean = false,
    val description: String = "Micro Capital Profile (< $30 USDT): Single active position, 0.75 USDT fee reserve, high-conviction only."
)

data class CapitalAllocationOpportunity(
    val symbol: String,
    val strategyName: String,
    val confidence: Double,
    val expectedRR: Double,
    val entryPrice: Double,
    val stopLossPrice: Double,
    val takeProfitPrice: Double,
    val winProbability: Double,
    val rationale: String,
    val score: Double // Ranking metric: EV = winProb * RR - (1 - winProb)
)

data class CapitalAllocationPlan(
    val totalAccountBalanceUsdt: Double,
    val availableFreeUsdt: Double,
    val reservedFeeBufferUsdt: Double,
    val allocatableCapitalUsdt: Double,
    val activePositionsCount: Int,
    val pendingOrdersCount: Int,
    val isMicroAccountMode: Boolean,
    val profileType: AccountRiskProfileType,
    val selectedOpportunity: CapitalAllocationOpportunity?,
    val allocatedUsdt: Double,
    val calculatedQuantity: Double,
    val isExecutionApproved: Boolean,
    val decisionReason: String,
    val recommendationSummary: String
)

object MicroAccountRiskEngine {

    const val MICRO_ACCOUNT_THRESHOLD_USDT = 30.0
    const val SMALL_ACCOUNT_THRESHOLD_USDT = 250.0

    /**
     * Determines the active risk profile given total wallet equity.
     */
    fun determineRiskProfile(totalBalanceUsdt: Double): MicroAccountRiskConfig {
        return when {
            totalBalanceUsdt <= MICRO_ACCOUNT_THRESHOLD_USDT -> {
                MicroAccountRiskConfig(
                    profileType = AccountRiskProfileType.MICRO_ACCOUNT_MODE,
                    maxActivePositions = 1,
                    feeReserveUsdt = 0.75.coerceAtMost(totalBalanceUsdt * 0.10).coerceAtLeast(0.50),
                    maxCapitalLockupRatio = 0.85,
                    minConfidenceThreshold = 0.80,
                    minExpectedRR = 2.0,
                    allowCompetingLimitOrders = false,
                    description = "Micro Capital Profile (Equity: $${String.format(Locale.US, "%.2f", totalBalanceUsdt)}): 1 position max, $0.75 USDT fee reserve, single-ticket allocation."
                )
            }
            totalBalanceUsdt <= SMALL_ACCOUNT_THRESHOLD_USDT -> {
                MicroAccountRiskConfig(
                    profileType = AccountRiskProfileType.SMALL_ACCOUNT_MODE,
                    maxActivePositions = 2,
                    feeReserveUsdt = 1.50,
                    maxCapitalLockupRatio = 0.75,
                    minConfidenceThreshold = 0.75,
                    minExpectedRR = 1.8,
                    allowCompetingLimitOrders = true,
                    description = "Small Account Profile (Equity: $${String.format(Locale.US, "%.2f", totalBalanceUsdt)}): 2 positions max, $1.50 USDT fee reserve."
                )
            }
            else -> {
                MicroAccountRiskConfig(
                    profileType = AccountRiskProfileType.STANDARD_PORTFOLIO_MODE,
                    maxActivePositions = 5,
                    feeReserveUsdt = 5.00,
                    maxCapitalLockupRatio = 0.60,
                    minConfidenceThreshold = 0.70,
                    minExpectedRR = 1.5,
                    allowCompetingLimitOrders = true,
                    description = "Standard Portfolio Profile: Multi-position diversification, dynamic risk parity."
                )
            }
        }
    }

    /**
     * Evaluates whether a new candidate trade signal can be safely submitted
     * under Micro Account Risk Mode guidelines.
     */
    fun validateMicroAccountTradeGate(
        candidateSymbol: String,
        confidence: Double,
        expectedRR: Double,
        targetUsdtSize: Double,
        totalBalanceUsdt: Double,
        availableFreeUsdt: Double,
        activePositions: List<BinancePositionEntity>,
        openOrders: List<BinanceOpenOrder>,
        isSimpleEarnOnly: Boolean = false
    ): Pair<Boolean, String> {
        if (isSimpleEarnOnly) {
            return Pair(false, "Funds are in Binance Simple Earn. Redeem to Spot before trading.")
        }
        val config = determineRiskProfile(totalBalanceUsdt)
        val openPositionsCount = activePositions.count { it.status == "OPEN" && it.closedAt == null }

        // 1. Max active positions check (Micro Account: 1 single position max)
        if (openPositionsCount >= config.maxActivePositions) {
            val existingSym = activePositions.firstOrNull { it.status == "OPEN" }?.symbol ?: "Unknown"
            return Pair(
                false,
                "Micro Account Mode: Maximum ${config.maxActivePositions} active position allowed (currently holding $existingSym). Staging additional positions is blocked to protect micro capital (~$${String.format(Locale.US, "%.2f", totalBalanceUsdt)})."
            )
        }

        // 2. Competing open limit orders check
        if (!config.allowCompetingLimitOrders && openOrders.isNotEmpty()) {
            val activeOrder = openOrders.first()
            return Pair(
                false,
                "Micro Account Mode: Pending exchange order #${activeOrder.orderId} (${activeOrder.symbol} ${activeOrder.side}) is currently active. Competing orders are blocked to avoid locking collateral."
            )
        }

        // 3. High conviction filter (Micro accounts cannot afford low-conviction exploration)
        if (confidence < config.minConfidenceThreshold) {
            return Pair(
                false,
                "Micro Account Mode requires high conviction (Confidence ${String.format(Locale.US, "%.0f%%", confidence * 100)} < ${String.format(Locale.US, "%.0f%%", config.minConfidenceThreshold * 100)} minimum). Signal skipped."
            )
        }

        if (expectedRR < config.minExpectedRR) {
            return Pair(
                false,
                "Micro Account Mode requires minimum ${config.minExpectedRR} R:R (Proposed R:R: ${String.format(Locale.US, "%.1f", expectedRR)}). Signal skipped."
            )
        }

        // 4. USDT Fee Reserve check
        val netUsableUsdt = (availableFreeUsdt - config.feeReserveUsdt).coerceAtLeast(0.0)
        if (netUsableUsdt < 5.00) {
            return Pair(
                false,
                "Insufficient free USDT after reserving $${String.format(Locale.US, "%.2f", config.feeReserveUsdt)} for fees (Available: $${String.format(Locale.US, "%.2f", availableFreeUsdt)}, Need >= $5.00 for Binance MIN_NOTIONAL)."
            )
        }

        return Pair(true, "Micro Account safety constraints verified. Single-ticket high-conviction order authorized.")
    }

    /**
     * Capital Allocation Engine:
     * "Given my available capital, what is the best single mathematical use of it right now?"
     */
    fun computeOptimalCapitalAllocation(
        totalBalanceUsdt: Double,
        availableFreeUsdt: Double,
        activePositions: List<BinancePositionEntity>,
        openOrders: List<BinanceOpenOrder>,
        candidateOpportunities: List<CapitalAllocationOpportunity>
    ): CapitalAllocationPlan {
        val config = determineRiskProfile(totalBalanceUsdt)
        val isMicro = config.profileType == AccountRiskProfileType.MICRO_ACCOUNT_MODE
        val openPositionsCount = activePositions.count { it.status == "OPEN" && it.closedAt == null }
        val reservedBuffer = config.feeReserveUsdt
        val usableCapital = (availableFreeUsdt - reservedBuffer).coerceAtLeast(0.0)

        // If already at max positions or pending orders active
        if (openPositionsCount >= config.maxActivePositions) {
            val existing = activePositions.firstOrNull { it.status == "OPEN" }
            return CapitalAllocationPlan(
                totalAccountBalanceUsdt = totalBalanceUsdt,
                availableFreeUsdt = availableFreeUsdt,
                reservedFeeBufferUsdt = reservedBuffer,
                allocatableCapitalUsdt = 0.0,
                activePositionsCount = openPositionsCount,
                pendingOrdersCount = openOrders.size,
                isMicroAccountMode = isMicro,
                profileType = config.profileType,
                selectedOpportunity = null,
                allocatedUsdt = 0.0,
                calculatedQuantity = 0.0,
                isExecutionApproved = false,
                decisionReason = "Capacity full: Holding active position ${existing?.symbol ?: ""}. Managing active position before allocating capital.",
                recommendationSummary = "Hold active position and manage trailing stops."
            )
        }

        if (!config.allowCompetingLimitOrders && openOrders.isNotEmpty()) {
            val order = openOrders.first()
            return CapitalAllocationPlan(
                totalAccountBalanceUsdt = totalBalanceUsdt,
                availableFreeUsdt = availableFreeUsdt,
                reservedFeeBufferUsdt = reservedBuffer,
                allocatableCapitalUsdt = 0.0,
                activePositionsCount = openPositionsCount,
                pendingOrdersCount = openOrders.size,
                isMicroAccountMode = isMicro,
                profileType = config.profileType,
                selectedOpportunity = null,
                allocatedUsdt = 0.0,
                calculatedQuantity = 0.0,
                isExecutionApproved = false,
                decisionReason = "Pending order #${order.orderId} (${order.symbol}) locks collateral. No competing orders in Micro Mode.",
                recommendationSummary = "Awaiting fill or manual cancellation of order #${order.orderId}."
            )
        }

        // Rank candidates by Expected Value = (WinProb * RR) - (1 - WinProb)
        val eligibleCandidates = candidateOpportunities
            .filter { it.confidence >= config.minConfidenceThreshold && it.expectedRR >= config.minExpectedRR }
            .sortedByDescending { it.score }

        val bestOpportunity = eligibleCandidates.firstOrNull()

        if (bestOpportunity == null) {
            return CapitalAllocationPlan(
                totalAccountBalanceUsdt = totalBalanceUsdt,
                availableFreeUsdt = availableFreeUsdt,
                reservedFeeBufferUsdt = reservedBuffer,
                allocatableCapitalUsdt = usableCapital,
                activePositionsCount = openPositionsCount,
                pendingOrdersCount = openOrders.size,
                isMicroAccountMode = isMicro,
                profileType = config.profileType,
                selectedOpportunity = null,
                allocatedUsdt = 0.0,
                calculatedQuantity = 0.0,
                isExecutionApproved = false,
                decisionReason = "No opportunities currently satisfy Micro Account conviction criteria (>= 80% confidence, >= 2.0 R:R). Capital safely preserved in USDT.",
                recommendationSummary = "Preserve $${String.format(Locale.US, "%.2f", availableFreeUsdt)} USDT cash until high-conviction setup emerges."
            )
        }

        // Allocate whole usable ticket in micro mode (e.g. 10.50 - 11.50 USDT out of 13 USDT)
        // Ensure allocatedUsdt >= 5.00 MIN_NOTIONAL
        val allocatedUsdt = if (isMicro) {
            usableCapital.coerceAtLeast(5.00).coerceAtMost(availableFreeUsdt - 0.50)
        } else {
            (usableCapital * 0.40).coerceAtLeast(10.00).coerceAtMost(usableCapital)
        }

        val filter = BinanceExchangeFilterValidator.getFilterForSymbol(bestOpportunity.symbol)
        val rawQty = if (bestOpportunity.entryPrice > 0.0) allocatedUsdt / bestOpportunity.entryPrice else 0.0
        val finalExecutableQty = BinanceExchangeFilterValidator.roundToStepSize(rawQty, filter.stepSize)
        val finalNotional = finalExecutableQty * bestOpportunity.entryPrice

        val isApproved = finalNotional >= filter.minNotional && finalNotional <= availableFreeUsdt

        val summary = if (isApproved) {
            "Deploy $${String.format(Locale.US, "%.2f", finalNotional)} USDT to best opportunity ${bestOpportunity.symbol} (${String.format(Locale.US, "%.0f%%", bestOpportunity.confidence * 100)} Conviction, ${String.format(Locale.US, "%.1f", bestOpportunity.expectedRR)} R:R), reserving $${String.format(Locale.US, "%.2f", reservedBuffer)} USDT."
        } else {
            "Allocation value ($${String.format(Locale.US, "%.2f", finalNotional)}) below minimum Binance order threshold ($${filter.minNotional})."
        }

        return CapitalAllocationPlan(
            totalAccountBalanceUsdt = totalBalanceUsdt,
            availableFreeUsdt = availableFreeUsdt,
            reservedFeeBufferUsdt = reservedBuffer,
            allocatableCapitalUsdt = usableCapital,
            activePositionsCount = openPositionsCount,
            pendingOrdersCount = openOrders.size,
            isMicroAccountMode = isMicro,
            profileType = config.profileType,
            selectedOpportunity = bestOpportunity,
            allocatedUsdt = finalNotional,
            calculatedQuantity = finalExecutableQty,
            isExecutionApproved = isApproved,
            decisionReason = "Single best capital deployment selected for ${bestOpportunity.symbol} based on high conviction and positive mathematical expectation.",
            recommendationSummary = summary
        )
    }
}
