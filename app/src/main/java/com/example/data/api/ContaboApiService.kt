package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// --- Models for Contabo VPS Backend ---

data class ContaboHealthResponse(
    val status: String,
    @Json(name = "app_name") val appName: String? = null,
    val name: String? = null,
    val version: String? = null
) {
    val displayName: String get() = appName ?: name ?: "Contabo VPS"
}

data class ContaboTrade(
    val id: Int,
    val symbol: String,
    val side: String,
    @Json(name = "entry_price") val entryPrice: Double,
    @Json(name = "position_size") val positionSize: Double,
    @Json(name = "stop_loss") val stopLoss: Double,
    @Json(name = "take_profit") val takeProfit: Double,
    @Json(name = "strategy_used") val strategyUsed: String,
    @Json(name = "market_regime") val marketRegime: String,
    @Json(name = "confidence_score") val confidenceScore: Double,
    @Json(name = "entry_reason") val entryReason: String,
    val indicators: String,
    val atr: Double,
    @Json(name = "risk_amount") val riskAmount: Double,
    @Json(name = "risk_pct") val riskPct: Double,
    @Json(name = "exit_price") val exitPrice: Double? = null,
    @Json(name = "profit_loss") val profitLoss: Double? = null,
    @Json(name = "entry_time") val entryTime: String,
    @Json(name = "exit_time") val exitTime: String? = null,
    val outcome: String? = null
)

data class ContaboTradeCreate(
    val symbol: String,
    val side: String,
    @Json(name = "entry_price") val entryPrice: Double,
    @Json(name = "position_size") val positionSize: Double,
    @Json(name = "stop_loss") val stopLoss: Double,
    @Json(name = "take_profit") val takeProfit: Double,
    @Json(name = "strategy_used") val strategyUsed: String = "AZA_STRATEGY",
    @Json(name = "market_regime") val marketRegime: String = "TREND",
    @Json(name = "confidence_score") val confidenceScore: Double = 1.0,
    @Json(name = "entry_reason") val entryReason: String = "",
    val indicators: String = "{}",
    val atr: Double = 0.0,
    @Json(name = "risk_amount") val riskAmount: Double = 0.0,
    @Json(name = "risk_pct") val riskPct: Double = 0.0
)

data class ContaboTradeUpdate(
    @Json(name = "exit_price") val exitPrice: Double? = null,
    @Json(name = "profit_loss") val profitLoss: Double? = null,
    @Json(name = "exit_time") val exitTime: String? = null,
    val outcome: String? = null
)

data class ContaboPaperWallet(
    val id: Int,
    @Json(name = "account_id") val accountId: String,
    val balance: Double,
    @Json(name = "updated_at") val updatedAt: String
)

data class ContaboRiskProfile(
    val id: Int,
    @Json(name = "strategy_name") val strategyName: String,
    @Json(name = "max_drawdown") val maxDrawdown: Double,
    @Json(name = "max_risk_per_trade") val maxRiskPerTrade: Double,
    @Json(name = "updated_at") val updatedAt: String
)

data class ContaboRiskProfileCreate(
    @Json(name = "strategy_name") val strategyName: String,
    @Json(name = "max_drawdown") val maxDrawdown: Double = 0.10,
    @Json(name = "max_risk_per_trade") val maxRiskPerTrade: Double = 0.02
)

data class ContaboAuditLog(
    val id: Int,
    val level: String,
    val category: String,
    val message: String,
    val details: String,
    @Json(name = "trade_id") val tradeId: Int?,
    val timestamp: String
)

data class ContaboAuditLogCreate(
    val level: String = "INFO",
    val category: String = "SYSTEM",
    val message: String,
    val details: String = "{}",
    @Json(name = "trade_id") val tradeId: Int? = null
)

data class BinanceStatusResponse(
    @Json(name = "is_authenticated") val isAuthenticated: Boolean,
    @Json(name = "is_connected") val isConnected: Boolean,
    @Json(name = "latency_ms") val latencyMs: Int,
    val mode: String,
    @Json(name = "rate_limit_status") val rateLimitStatus: String
)

data class BinanceBalance(
    val asset: String,
    val free: Double,
    val locked: Double
)

data class BinanceOpenOrder(
    val symbol: String,
    val orderId: Long,
    val price: String,
    val origQty: String,
    val status: String,
    val side: String,
    val type: String,
    val time: Long
)

data class ContaboActivePosition(
    val id: Int,
    val symbol: String,
    val side: String,
    @Json(name = "entry_price") val entryPrice: Double,
    @Json(name = "position_size") val positionSize: Double,
    @Json(name = "stop_loss") val stopLoss: Double,
    @Json(name = "take_profit") val takeProfit: Double,
    @Json(name = "trailing_stop") val trailingStop: Double,
    @Json(name = "current_price") val currentPrice: Double,
    @Json(name = "unrealized_pnl") val unrealizedPnl: Double,
    @Json(name = "tp_stages") val tpStages: List<Map<String, Any>> = emptyList()
)

data class ContaboPortfolioSummary(
    @Json(name = "wallet_balance") val walletBalance: Double,
    @Json(name = "total_realized_pnl") val totalRealizedPnl: Double,
    @Json(name = "total_unrealized_pnl") val totalUnrealizedPnl: Double,
    @Json(name = "total_pnl") val totalPnl: Double,
    @Json(name = "active_positions_count") val activePositionsCount: Int,
    @Json(name = "closed_trades_count") val closedTradesCount: Int,
    @Json(name = "global_trading_mode") val globalTradingMode: String
)

data class PipelineValidationStep(
    val step: String,
    val status: String,
    val detail: String
)

data class PipelineValidationResponse(
    val status: String,
    val message: String,
    @Json(name = "trade_id") val tradeId: Int,
    val steps: List<PipelineValidationStep>
)

// --- API Service Definition ---

interface ContaboApiService {

    @GET("health")
    suspend fun checkHealth(): ContaboHealthResponse

    @GET("trading/layers/status")
    suspend fun getLayeredStatus(): Map<String, Any>

    @GET("trading/binance/status")
    suspend fun getBinanceStatus(): BinanceStatusResponse

    @GET("trading/binance/balances")
    suspend fun getBinanceBalances(): List<BinanceBalance>

    @GET("trading/binance/orders")
    suspend fun getBinanceOrders(
        @Query("symbol") symbol: String? = null
    ): List<BinanceOpenOrder>

    @DELETE("trading/binance/order/{symbol}/{order_id}")
    suspend fun cancelBinanceOrder(
        @Path("symbol") symbol: String,
        @Path("order_id") orderId: String
    ): Map<String, Any>

    @POST("trading/mode")
    suspend fun updateGlobalTradingMode(
        @Query("mode") mode: String
    ): Map<String, Any>

    @POST("trading/emergency/reset")
    suspend fun resetEmergencyStop(): Map<String, Any>

    @POST("trading/emergency/liquidate")
    suspend fun triggerEmergencyLiquidation(): Map<String, Any>

    @GET("trading")
    suspend fun getTrades(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100
    ): List<ContaboTrade>

    @GET("trading/{trade_id}")
    suspend fun getTradeById(
        @Path("trade_id") tradeId: Int
    ): ContaboTrade

    @POST("trading")
    suspend fun createTrade(
        @Body trade: ContaboTradeCreate
    ): ContaboTrade

    @PATCH("trading/{trade_id}")
    suspend fun updateTrade(
        @Path("trade_id") tradeId: Int,
        @Body tradeUpdate: ContaboTradeUpdate
    ): ContaboTrade

    @GET("paper-trading/{account_id}")
    suspend fun getPaperWallet(
        @Path("account_id") accountId: String
    ): ContaboPaperWallet

    @POST("paper-trading/{account_id}")
    suspend fun createPaperWallet(
        @Path("account_id") accountId: String,
        @Query("initial_balance") initialBalance: Double = 100000.0
    ): ContaboPaperWallet

    @POST("paper-trading/{account_id}/adjust")
    suspend fun adjustPaperWallet(
        @Path("account_id") accountId: String,
        @Query("amount_change") amountChange: Double
    ): ContaboPaperWallet

    @GET("risk/{strategy_name}")
    suspend fun getRiskProfile(
        @Path("strategy_name") strategyName: String
    ): ContaboRiskProfile

    @POST("risk")
    suspend fun createOrUpdateRiskProfile(
        @Body profile: ContaboRiskProfileCreate
    ): ContaboRiskProfile

    @GET("audit")
    suspend fun getAuditLogs(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100
    ): List<ContaboAuditLog>

    @POST("audit")
    suspend fun createAuditLog(
        @Body log: ContaboAuditLogCreate
    ): ContaboAuditLog

    @GET("trading/active-positions")
    suspend fun getActivePositions(): List<ContaboActivePosition>

    @GET("trading/closed-trades")
    suspend fun getClosedTrades(): List<ContaboTrade>

    @GET("trading/summary")
    suspend fun getTradingSummary(): ContaboPortfolioSummary

    @POST("trading/validate-pipeline")
    suspend fun validateTradingPipeline(): PipelineValidationResponse
}

// --- Contabo Retrofit Client ---

object ContaboRetrofitClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun buildService(rawBaseUrl: String): ContaboApiService {
        val formattedUrl = when {
            rawBaseUrl.startsWith("http://") || rawBaseUrl.startsWith("https://") -> rawBaseUrl
            else -> "http://$rawBaseUrl"
        }.let { if (it.endsWith("/")) it else "$it/" }

        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ContaboApiService::class.java)
    }
}
