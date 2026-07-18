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
    val name: String,
    val version: String
)

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

// --- API Service Definition ---

interface ContaboApiService {

    @GET("health")
    suspend fun checkHealth(): ContaboHealthResponse

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
