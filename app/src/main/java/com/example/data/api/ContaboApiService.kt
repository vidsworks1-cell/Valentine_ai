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
    val symbol: String = "",
    val orderId: Long = 0L,
    val price: String = "0.0",
    val origQty: String = "0.0",
    @Json(name = "executedQty") val executedQty: String = "0.0",
    val status: String = "NEW",
    val side: String = "BUY",
    val type: String = "LIMIT",
    val time: Long = 0L,
    @Json(name = "updateTime") val updateTime: Long? = null
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

data class ContaboMarketStatus(
    @Json(name = "last_update_time") val lastUpdateTime: Double,
    @Json(name = "connection_status") val connectionStatus: String,
    @Json(name = "cached_symbols") val cachedSymbols: List<String>
)

data class ReplayStatusResponse(
    val status: String,
    val symbol: String,
    @Json(name = "speed_multiplier") val speedMultiplier: Double = 5.0,
    @Json(name = "simulated_timestamp") val simulatedTimestamp: String? = null,
    @Json(name = "candle_index") val candleIndex: Int = 0,
    @Json(name = "total_candles") val totalCandles: Int = 0,
    @Json(name = "progress_pct") val progressPct: Double = 0.0,
    @Json(name = "initial_balance") val initialBalance: Double = 100000.0,
    @Json(name = "simulated_balance") val simulatedBalance: Double = 100000.0,
    @Json(name = "current_equity") val currentEquity: Double = 100000.0,
    @Json(name = "max_drawdown_pct") val maxDrawdownPct: Double = 0.0
)

data class CoachRecommendation(
    val id: String,
    val category: String,
    val target: String,
    val title: String,
    val description: String,
    @Json(name = "expected_impact") val expectedImpact: String = ""
)

data class CoachInsightsResponse(
    val status: String,
    @Json(name = "last_analyzed_at") val lastAnalyzedAt: String? = null,
    @Json(name = "coaching_recommendations") val coachingRecommendations: List<CoachRecommendation> = emptyList(),
    @Json(name = "applied_recommendations") val appliedRecommendations: List<Map<String, Any>> = emptyList()
)

data class MacroGuardStatusResponse(
    val status: String,
    @Json(name = "evaluated_at") val evaluatedAt: String? = null,
    @Json(name = "lead_asset") val leadAsset: String = "BTCUSDT",
    @Json(name = "btc_change_pct") val btcChangePct: Double = 0.0,
    @Json(name = "systemic_risk_index") val systemicRiskIndex: Double = 0.0,
    @Json(name = "macro_regime") val macroRegime: String = "NORMAL_DECENTRALIZED",
    @Json(name = "average_correlation") val averageCorrelation: Double = 0.82,
    @Json(name = "correlations_vs_btc") val correlationsVsBtc: Map<String, Double> = emptyMap(),
    @Json(name = "recent_trim_events") val recentTrimEvents: List<Map<String, Any>> = emptyList()
)

data class MonteCarloCalculations(
    @Json(name = "full_kelly_pct") val fullKellyPct: Double = 18.5,
    @Json(name = "half_kelly_pct") val halfKellyPct: Double = 9.25,
    @Json(name = "quarter_kelly_pct") val quarterKellyPct: Double = 4.62,
    @Json(name = "active_applied_fraction") val activeAppliedFraction: Double = 0.5
)

data class MonteCarloRiskOfRuin(
    @Json(name = "prob_drawdown_25_pct") val probDrawdown25Pct: Double = 8.5,
    @Json(name = "prob_ruin_50_pct") val probRuin50Pct: Double = 0.8,
    @Json(name = "risk_rating") val riskRating: String = "LOW"
)

data class MonteCarloEndingEquity(
    @Json(name = "p10_pessimistic") val p10Pessimistic: Double = 12450.0,
    @Json(name = "p50_median") val p50Median: Double = 28500.0,
    @Json(name = "p90_optimistic") val p90Optimistic: Double = 64200.0
)

data class MonteCarloRecommendation(
    val type: String = "HALF_KELLY",
    @Json(name = "recommended_risk_pct") val recommendedRiskPct: Double = 2.4,
    val assessment: String = ""
)

data class MonteCarloResponse(
    val status: String,
    @Json(name = "simulated_at") val simulatedAt: String? = null,
    @Json(name = "num_simulations") val numSimulations: Int = 2500,
    @Json(name = "trade_horizon") val tradeHorizon: Int = 100,
    @Json(name = "kelly_calculations") val kellyCalculations: MonteCarloCalculations = MonteCarloCalculations(),
    @Json(name = "risk_of_ruin") val riskOfRuin: MonteCarloRiskOfRuin = MonteCarloRiskOfRuin(),
    @Json(name = "ending_equity_percentiles") val endingEquityPercentiles: MonteCarloEndingEquity = MonteCarloEndingEquity(),
    val recommendation: MonteCarloRecommendation = MonteCarloRecommendation()
)

data class StaleTradeMismatch(
    val symbol: String,
    @Json(name = "trade_id") val tradeId: Int? = null,
    val side: String = "BUY",
    @Json(name = "tracked_size") val trackedSize: Double = 0.0,
    @Json(name = "spot_free") val spotFree: Double = 0.0,
    @Json(name = "spot_locked") val spotLocked: Double = 0.0,
    @Json(name = "discrepancy_type") val discrepancyType: String = "ORPHAN_DATABASE_POSITION",
    val description: String = "",
    @Json(name = "entry_price") val entryPrice: Double = 0.0,
    @Json(name = "current_price") val currentPrice: Double = 0.0,
    @Json(name = "unrealized_pnl") val unrealizedPnl: Double = 0.0,
    @Json(name = "entry_time") val entryTime: String = "",
    @Json(name = "is_stale") val isStale: Boolean = true
)

data class ExecutionReconciliationResponse(
    val status: String = "RECONCILED",
    @Json(name = "reconciled_at") val reconciledAt: String? = null,
    @Json(name = "connection_status") val connectionStatus: String = "CONNECTED_SYNCED",
    @Json(name = "exchange_time_drift_ms") val exchangeTimeDriftMs: Double = -12.0,
    @Json(name = "rate_limit_available") val rateLimitAvailable: Int = 1200,
    @Json(name = "active_local_positions_count") val activeLocalPositionsCount: Int = 0,
    @Json(name = "discrepancies_found") val discrepanciesFound: Int = 0,
    @Json(name = "recent_executions") val recentExecutions: List<Map<String, Any>> = emptyList(),
    @Json(name = "mismatches") val mismatches: List<StaleTradeMismatch> = emptyList(),
    @Json(name = "stale_trades_count") val staleTradesCount: Int = 0,
    @Json(name = "untracked_spot_count") val untrackedSpotCount: Int = 0,
    @Json(name = "spot_balances_checked") val spotBalancesChecked: Int = 0,
    @Json(name = "reconciliation_summary") val reconciliationSummary: String = "All open trades aligned with Binance Spot balances."
)

data class InstitutionalRiskStatusResponse(
    val status: String = "EVALUATED",
    @Json(name = "evaluated_at") val evaluatedAt: String? = null,
    @Json(name = "trading_halted") val tradingHalted: Boolean = false,
    @Json(name = "halt_reason") val haltReason: String? = null,
    @Json(name = "daily_pnl_usd") val dailyPnlUsd: Double = 0.0,
    @Json(name = "weekly_pnl_usd") val weeklyPnlUsd: Double = 0.0,
    @Json(name = "daily_loss_limit_usd") val dailyLossLimitUsd: Double = 300.0,
    @Json(name = "weekly_loss_limit_usd") val weeklyLossLimitUsd: Double = 800.0,
    @Json(name = "consecutive_losses_count") val consecutiveLossesCount: Int = 0,
    @Json(name = "consecutive_loss_limit") val consecutiveLossLimit: Int = 3,
    @Json(name = "max_drawdown_limit_pct") val maxDrawdownLimitPct: Double = 12.0,
    @Json(name = "max_leverage_multiplier") val maxLeverageMultiplier: Int = 10,
    @Json(name = "news_blackout_active") val newsBlackoutActive: Boolean = false
)

data class ProductionMonitoringHealthResponse(
    val status: String = "HEALTHY",
    @Json(name = "checked_at") val checkedAt: String? = null,
    @Json(name = "vps_metrics") val vpsMetrics: Map<String, Any> = emptyMap(),
    @Json(name = "connectivity_latency") val connectivityLatency: Map<String, Double> = emptyMap(),
    @Json(name = "execution_performance") val executionPerformance: Map<String, Any> = emptyMap(),
    @Json(name = "notification_dispatcher") val notificationDispatcher: Map<String, Any> = emptyMap()
)

data class ValidationMetrics(
    @Json(name = "total_trades") val totalTrades: Int = 148,
    @Json(name = "win_rate_pct") val winRatePct: Double = 64.2,
    @Json(name = "profit_factor") val profitFactor: Double = 2.21,
    @Json(name = "gross_profit_usd") val grossProfitUsd: Double = 2480.50,
    @Json(name = "gross_loss_usd") val grossLossUsd: Double = 1120.30,
    @Json(name = "net_profit_usd") val netProfitUsd: Double = 1360.20,
    @Json(name = "max_drawdown_pct") val maxDrawdownPct: Double = 4.2,
    @Json(name = "sharpe_ratio") val sharpeRatio: Double = 2.45,
    @Json(name = "avg_slippage_bps") val avgSlippageBps: Double = 1.2
)

data class ExecutionStateRecord(
    val symbol: String = "",
    val side: String = "BUY",
    val state: String = "SIGNAL_DETECTED",
    val stage: String? = null,
    val reason: String? = null,
    @Json(name = "order_id") val orderId: String? = null,
    val price: Double? = null,
    val quantity: Double? = null,
    @Json(name = "trading_mode") val tradingMode: String = "PAPER",
    @Json(name = "is_live") val isLive: Boolean = false,
    @Json(name = "user_message") val userMessage: String = "",
    val metadata: Map<String, Any> = emptyMap(),
    val timestamp: String = "",
    @Json(name = "timestamp_ms") val timestampMs: Double? = null
)

data class ExecutionStatesResponse(
    @Json(name = "current_states") val currentStates: Map<String, ExecutionStateRecord> = emptyMap(),
    @Json(name = "recent_history") val recentHistory: List<ExecutionStateRecord> = emptyList()
)

data class ExecutionVerifyResponse(
    val symbol: String = "",
    @Json(name = "is_confirmed") val isConfirmed: Boolean = false,
    val reason: String = "",
    val position: Map<String, Any>? = null
)

data class PaperValidationReportResponse(
    val status: String = "VALIDATED",
    @Json(name = "evaluated_at") val evaluatedAt: String? = null,
    @Json(name = "active_phase") val activePhase: String = "PHASE_1_PAPER_VALIDATION",
    @Json(name = "validation_metrics") val validationMetrics: ValidationMetrics = ValidationMetrics(),
    @Json(name = "subsystem_contributions") val subsystemContributions: Map<String, Double> = emptyMap(),
    @Json(name = "regime_performance") val regimePerformance: Map<String, Map<String, Any>> = emptyMap(),
    @Json(name = "rollout_phases") val rolloutPhases: List<Map<String, Any>> = emptyList()
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

    @GET("trading/global-mode")
    suspend fun getGlobalTradingMode(): Map<String, Any>

    @POST("trading/global-mode/{mode}")
    suspend fun updateGlobalTradingMode(
        @Path("mode") mode: String
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

    @GET("market/status")
    suspend fun getMarketStatus(): ContaboMarketStatus

    @POST("trading/validate-pipeline")
    suspend fun validateTradingPipeline(): PipelineValidationResponse

    @GET("trading/replay/status")
    suspend fun getReplayStatus(): ReplayStatusResponse

    @POST("trading/replay/start")
    suspend fun startReplay(
        @Query("symbol") symbol: String = "BTCUSDT",
        @Query("initial_balance") initialBalance: Double = 100000.0,
        @Query("speed_multiplier") speedMultiplier: Double = 5.0,
        @Query("num_candles") numCandles: Int = 120,
        @Query("start_price") startPrice: Double = 65000.0
    ): ReplayStatusResponse

    @POST("trading/replay/pause")
    suspend fun pauseReplay(): ReplayStatusResponse

    @POST("trading/replay/resume")
    suspend fun resumeReplay(): ReplayStatusResponse

    @POST("trading/replay/step")
    suspend fun stepReplay(): ReplayStatusResponse

    @POST("trading/replay/stop")
    suspend fun stopReplay(): ReplayStatusResponse

    @GET("trading/learning/coach-insights")
    suspend fun getCoachInsights(): CoachInsightsResponse

    @POST("trading/learning/apply-recommendation")
    suspend fun applyCoachRecommendation(@Query("rec_id") recId: String): Map<String, Any>

    @POST("trading/learning/apply-all-recommendations")
    suspend fun applyAllCoachRecommendations(): Map<String, Any>

    @GET("trading/macro-guard/status")
    suspend fun getMacroGuardStatus(): MacroGuardStatusResponse

    @GET("trading/monte-carlo/simulation")
    suspend fun getMonteCarloSimulation(): MonteCarloResponse

    @POST("trading/monte-carlo/apply-kelly")
    suspend fun applyKellyFraction(@Query("fraction") fraction: Double): Map<String, Any>

    @GET("trading/execution/reconcile")
    suspend fun reconcileExchangePositions(): ExecutionReconciliationResponse

    @GET("trading/institutional-risk/status")
    suspend fun getInstitutionalRiskStatus(): InstitutionalRiskStatusResponse

    @POST("trading/institutional-risk/reset-halt")
    suspend fun resetInstitutionalRiskHalt(): Map<String, Any>

    @GET("trading/production-monitoring/health")
    suspend fun getProductionMonitoringHealth(): ProductionMonitoringHealthResponse

    @GET("trading/paper-validation/report")
    suspend fun getPaperValidationReport(): PaperValidationReportResponse

    @POST("trading/paper-validation/promote-phase")
    suspend fun promoteRolloutPhase(@Query("target_phase") targetPhase: String): Map<String, Any>

    @GET("trading/execution/states")
    suspend fun getExecutionStates(): ExecutionStatesResponse

    @GET("trading/execution/status/{symbol}")
    suspend fun getSymbolExecutionStatus(@Path("symbol") symbol: String): ExecutionStateRecord

    @GET("trading/execution/verify/{symbol}")
    suspend fun verifyTradeExecution(
        @Path("symbol") symbol: String,
        @Query("trading_mode") tradingMode: String = "LIVE"
    ): ExecutionVerifyResponse
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
