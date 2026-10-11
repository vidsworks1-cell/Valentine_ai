package com.example.aza.strategy

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Data model for a buffered trade opportunity waiting in the queue.
 */
data class QueuedTradeOpportunity(
    val id: String,
    val symbol: String,
    val side: String = "BUY",
    val strategy: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val confidence: Double,
    val rankScore: Double,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val ttlSeconds: Long = 600L, // 10 minutes time-to-live
    val rvol: Double = 1.5,
    val spreadBps: Double = 6.0
) {
    val isExpired: Boolean get() = (System.currentTimeMillis() - enqueuedAt) > (ttlSeconds * 1000L)
    val ageMinutes: Double get() = (System.currentTimeMillis() - enqueuedAt) / 60000.0
}

/**
 * Institutional Opportunity Queue & Capital Ranking Engine for Lumina Android.
 *
 * Prevents dropping high-conviction trades when portfolio limits or Spot USDT capacity are reached.
 * Automatically awakens when capital is freed to execute the highest-alpha candidate.
 */
object OpportunityQueueEngine {
    private const val TAG = "OpportunityQueueEngine"
    private const val MAX_CAPACITY = 12

    private val _queue = MutableStateFlow<List<QueuedTradeOpportunity>>(emptyList())
    val queue: StateFlow<List<QueuedTradeOpportunity>> = _queue.asStateFlow()

    private val _totalEnqueued = MutableStateFlow(0)
    val totalEnqueued: StateFlow<Int> = _totalEnqueued.asStateFlow()

    private val _totalPromoted = MutableStateFlow(0)
    val totalPromoted: StateFlow<Int> = _totalPromoted.asStateFlow()

    /**
     * Institutional Capital Ranking Score (0 to 100).
     */
    fun calculateRankScore(
        confidence: Double,
        entryPrice: Double,
        stopLoss: Double,
        takeProfit: Double,
        rvol: Double = 1.5,
        spreadBps: Double = 6.0
    ): Double {
        val risk = abs(entryPrice - stopLoss).coerceAtLeast(0.005 * entryPrice)
        val reward = abs(takeProfit - entryPrice)
        val rrr = reward / risk
        val rrrScore = min(1.0, max(0.0, (rrr - 1.0) / 2.5))
        val rvolScore = min(1.0, max(0.0, (rvol - 0.8) / 2.2))
        val spreadScore = max(0.0, 1.0 - (spreadBps / 25.0))

        val composite = (confidence * 40.0) +
                (rrrScore * 25.0) +
                (rvolScore * 20.0) +
                (spreadScore * 15.0)

        return Math.round(composite * 10.0) / 10.0
    }

    /**
     * Enqueue a trade that was throttled by portfolio capacity or open position limits.
     */
    fun enqueueOpportunity(
        symbol: String,
        side: String = "BUY",
        strategy: String,
        entryPrice: Double,
        stopLoss: Double,
        takeProfit: Double,
        confidence: Double,
        rvol: Double = 1.5,
        spreadBps: Double = 6.0,
        reason: String = "Portfolio Capacity Limit"
    ): QueuedTradeOpportunity {
        val rankScore = calculateRankScore(confidence, entryPrice, stopLoss, takeProfit, rvol, spreadBps)
        val id = "QUEUE_" + System.currentTimeMillis().toString().takeLast(6)
        val item = QueuedTradeOpportunity(
            id = id,
            symbol = symbol,
            side = side,
            strategy = strategy,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            confidence = confidence,
            rankScore = rankScore,
            rvol = rvol,
            spreadBps = spreadBps
        )

        val currentList = _queue.value.filter { it.symbol != symbol && !it.isExpired }.toMutableList()
        currentList.add(item)
        currentList.sortByDescending { it.rankScore }

        _queue.value = currentList.take(MAX_CAPACITY)
        _totalEnqueued.value = _totalEnqueued.value + 1

        Log.i(TAG, "📥 [OPPORTUNITY ENQUEUED] $symbol (Score: $rankScore, Reason: $reason, Queue Size: ${_queue.value.size})")
        return item
    }

    /**
     * Retrieves the top-ranked candidate and validates freshness.
     */
    fun popTopCandidate(): QueuedTradeOpportunity? {
        val currentList = _queue.value.filter { !it.isExpired }.toMutableList()
        if (currentList.isEmpty()) {
            _queue.value = emptyList()
            return null
        }
        val top = currentList.removeAt(0)
        _queue.value = currentList
        _totalPromoted.value = _totalPromoted.value + 1
        Log.i(TAG, "🚀 [OPPORTUNITY PROMOTED] Popped rank #1 candidate: ${top.symbol} (Score: ${top.rankScore})")
        return top
    }

    /**
     * Validates whether a candidate's market price has drifted beyond tolerance (+/-0.35%).
     */
    fun isCandidatePriceFresh(candidate: QueuedTradeOpportunity, livePrice: Double): Boolean {
        if (livePrice <= 0.0 || candidate.entryPrice <= 0.0) return true
        val driftPct = (abs(livePrice - candidate.entryPrice) / candidate.entryPrice) * 100.0
        return driftPct <= 0.35
    }

    fun clearQueue() {
        _queue.value = emptyList()
    }
}
