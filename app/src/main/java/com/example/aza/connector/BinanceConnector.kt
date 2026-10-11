package com.example.aza.connector

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

open class BinanceApiException(
    val errorCode: Int,
    override val message: String,
    val isRetryable: Boolean = false
) : IOException(message)

class ImpossibleCloseOrderException(
    val symbol: String,
    errorCode: Int,
    message: String
) : BinanceApiException(errorCode, message, false)

class ExecutionGateBlockedException(
    val reason: String,
    val suggestedAction: String,
    override val message: String = "Execution Gate Blocked: $reason"
) : IOException(message)

object BinanceConnector {
    private const val TAG = "BinanceConnector"
    private const val BASE_URL = "https://api.binance.com"

    // Configuration / Credentials
    private var apiKey: String = ""
    private var apiSecret: String = ""

    // Thread-safe OkHttpClient with SSL Pinning
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .certificatePinner(com.example.aza.security.SslPinningUtility.certificatePinner)
        .build()

    // Operating Mode & Execution Lock (Disengaged manual approval: Unlocked and set to FULL_AUTONOMOUS_LIVE by default)
    private val _operatingMode = MutableStateFlow(com.example.aza.strategy.LuminaOperatingMode.FULL_AUTONOMOUS_LIVE)
    val operatingMode: StateFlow<com.example.aza.strategy.LuminaOperatingMode> = _operatingMode.asStateFlow()

    private val _isExecutionUnlocked = MutableStateFlow(true)
    val isExecutionUnlocked: StateFlow<Boolean> = _isExecutionUnlocked.asStateFlow()

    fun setOperatingMode(mode: com.example.aza.strategy.LuminaOperatingMode) {
        _operatingMode.value = mode
    }

    fun setExecutionUnlocked(unlocked: Boolean) {
        _isExecutionUnlocked.value = unlocked
    }

    // Connection Health Telemetry & Status StateFlows
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated = _isAuthenticated.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow(0L)
    val lastLatencyMs = _lastLatencyMs.asStateFlow()

    private val _lastSuccessfulSync = MutableStateFlow(0L)
    val lastSuccessfulSync = _lastSuccessfulSync.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage = _lastErrorMessage.asStateFlow()

    private val _healthState = MutableStateFlow("HEALTHY") // HEALTHY, DEGRADED, FAILING
    val healthState = _healthState.asStateFlow()

    private val _rateLimitStatus = MutableStateFlow("1200/1200")
    val rateLimitStatus = _rateLimitStatus.asStateFlow()

    private val _cooldownExpirationTime = MutableStateFlow(0L)
    val cooldownExpirationTime = _cooldownExpirationTime.asStateFlow()

    fun isCooldownActive(): Boolean {
        return System.currentTimeMillis() < _cooldownExpirationTime.value
    }

    fun getCooldownRemainingSeconds(): Long {
        val remaining = _cooldownExpirationTime.value - System.currentTimeMillis()
        return if (remaining > 0) remaining / 1000 else 0L
    }

    fun triggerManualCooldown(seconds: Long) {
        _cooldownExpirationTime.value = System.currentTimeMillis() + (seconds * 1000)
    }

    private val _isOrderPlacementEnabled = MutableStateFlow(false)
    val isOrderPlacementEnabled = _isOrderPlacementEnabled.asStateFlow()

    /**
     * Update credentials dynamically from capability state.
     */
    fun updateCredentials(key: String, secret: String) {
        apiKey = key
        apiSecret = secret
        _isAuthenticated.value = key.isNotBlank() && secret.isNotBlank()
        if (key.isBlank() || secret.isBlank()) {
            _healthState.value = "FAILING"
            _lastErrorMessage.value = "Credentials missing"
        } else {
            _lastErrorMessage.value = null
        }
    }

    /**
     * Enable or disable order execution. Disabled by default for Phase 14.1.
     */
    fun setOrderPlacementEnabled(enabled: Boolean) {
        _isOrderPlacementEnabled.value = enabled
    }

    private var timeOffsetMs: Long = 0L

    /**
     * Synchronize server time with Binance to prevent "timestamp out of sync" errors.
     */
    suspend fun syncServerTime(): Result<Long> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("$BASE_URL/api/v3/time")
                .addHeader("Accept", "application/json")
                .build()
            val startTime = System.currentTimeMillis()
            val jsonStr = executeCall(request, maxRetries = 2)
            val serverTime = JSONObject(jsonStr).getLong("serverTime")
            val localTime = System.currentTimeMillis()
            timeOffsetMs = serverTime - ((startTime + localTime) / 2) // Midpoint calculation for high precision latency compensation
            Log.i(TAG, "Successfully synchronized with Binance server time. Calculated offset = ${timeOffsetMs}ms.")
            Result.success(serverTime)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to synchronize server time: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Generate HMAC-SHA256 signature for signed Binance endpoints.
     */
    private fun hmacSha256(data: String, secret: String): String {
        return try {
            val sha256HMAC = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
            sha256HMAC.init(secretKey)
            val hash = sha256HMAC.doFinal(data.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { String.format("%02x", it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating HMAC-SHA256 signature", e)
            ""
        }
    }

    /**
     * Build signed request URL and request builder.
     */
    private fun buildSignedRequest(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        method: String = "GET"
    ): Request {
        val timestamp = System.currentTimeMillis() + timeOffsetMs
        val paramsWithTime = queryParams.toMutableMap()
        paramsWithTime["timestamp"] = timestamp.toString()
        paramsWithTime["recvWindow"] = "10000" // generous window to prevent timestamp out-of-sync issues

        val queryString = paramsWithTime.map { "${it.key}=${it.value}" }.joinToString("&")
        val signature = hmacSha256(queryString, apiSecret)
        val url = "$BASE_URL$path?$queryString&signature=$signature"

        val builder = Request.Builder()
            .url(url)
            .addHeader("X-MBX-APIKEY", apiKey)
            .addHeader("Accept", "application/json")

        if (method == "POST") {
            builder.post(okhttp3.RequestBody.create(null, ByteArray(0)))
        } else if (method == "DELETE") {
            builder.delete()
        }

        return builder.build()
    }

    /**
     * Execute a network call with built-in retries, automatic reconnection,
     * latency tracking, rate limit monitoring, and robust error handling.
     */
    private suspend fun executeCall(request: Request, maxRetries: Int = 3): String = withContext(Dispatchers.IO) {
        if (isCooldownActive()) {
            throw IOException("Rate limit cooldown active. Remaining: ${getCooldownRemainingSeconds()} seconds.")
        }

        var attempt = 0
        var lastException: Exception? = null
        var delayMs = 500L

        while (attempt < maxRetries) {
            attempt++
            val startTime = System.currentTimeMillis()
            try {
                Log.d(TAG, "Executing call to: ${request.url.encodedPath} (Attempt $attempt/$maxRetries)")
                client.newCall(request).execute().use { response ->
                    val duration = System.currentTimeMillis() - startTime
                    _lastLatencyMs.value = duration

                    // Parse rate limit headers
                    val usedWeight = response.header("X-MBX-USED-WEIGHT-1M")
                    if (usedWeight != null) {
                        val remaining = (1200 - usedWeight.toIntOrNull().orZero()).coerceAtLeast(0)
                        _rateLimitStatus.value = "$remaining/1200"
                    }

                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        _lastSuccessfulSync.value = System.currentTimeMillis()
                        _healthState.value = "HEALTHY"
                        _lastErrorMessage.value = null
                        return@withContext body
                    } else {
                        val errBody = response.body?.string() ?: ""
                        var apiErrorCode = 0
                        var apiErrorMsg = ""
                        try {
                            val json = JSONObject(errBody)
                            apiErrorCode = json.optInt("code", 0)
                            apiErrorMsg = json.optString("msg", response.message)
                        } catch (e: Exception) {
                            apiErrorMsg = response.message
                        }

                        val formattedErrMsg = if (apiErrorCode != 0) {
                            "API Error [Code: $apiErrorCode]: $apiErrorMsg"
                        } else {
                            "HTTP Error ${response.code}: $apiErrorMsg"
                        }

                        _lastErrorMessage.value = formattedErrMsg
                        _healthState.value = "DEGRADED"

                        // Check for rate limit or IP ban codes (429 or 418)
                        if (response.code == 429 || response.code == 418) {
                            val retryAfterHeader = response.header("Retry-After")
                            val backoffSec = retryAfterHeader?.toLongOrNull() ?: 60L
                            _cooldownExpirationTime.value = System.currentTimeMillis() + (backoffSec * 1000)
                            Log.e(TAG, "Rate limit hit (HTTP ${response.code}). Putting connector on cooldown for ${backoffSec}s.")
                            _lastErrorMessage.value = "Rate Limit Hit. Backing off for ${backoffSec}s."
                            throw BinanceApiException(response.code, "Rate limit exceeded. Backoff ${backoffSec}s.", isRetryable = false)
                        }

                        // Non-retryable business logic / balance errors (e.g. -2010 Insufficient Balance, -2011 Unknown Order, -1013 Filter Min Notional)
                        val nonRetryableCodes = setOf(-2010, -2011, -2013, -1013, -1121, -1100, -1111)
                        if (nonRetryableCodes.contains(apiErrorCode) || (response.code in 400..404 && apiErrorCode != 0)) {
                            Log.w(TAG, "Non-retryable exchange error detected ($apiErrorCode): $apiErrorMsg. Aborting retries.")
                            throw BinanceApiException(apiErrorCode, formattedErrMsg, isRetryable = false)
                        }

                        throw IOException(formattedErrMsg)
                    }
                }
            } catch (e: BinanceApiException) {
                if (!e.isRetryable) {
                    // Stop retrying immediately for non-retryable account/order errors
                    lastException = e
                    break
                }
                lastException = e
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt $attempt failed: ${e.message}")
                if (e is UnknownHostException || e is SocketTimeoutException || e is IOException) {
                    if (attempt < maxRetries) {
                        kotlinx.coroutines.delay(delayMs)
                        delayMs *= 2
                    }
                } else {
                    break
                }
            }
        }

        _healthState.value = "FAILING"
        val finalErrMsg = lastException?.message ?: "Unknown connection failure"
        _lastErrorMessage.value = "Connection failed: $finalErrMsg"
        if (lastException is BinanceApiException) {
            throw lastException
        }
        throw IOException("Failed after $maxRetries attempts: $finalErrMsg", lastException)
    }

    /**
     * Fetch user's spot account balance information directly from Binance GET /api/v3/account.
     */
    suspend fun fetchAccountBalances(): Result<List<BinanceAssetBalance>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            val key = try { com.example.aza.security.SecureCredentialStore.getBinanceApiKey() } catch(e: Exception) { "" }
            val secret = try { com.example.aza.security.SecureCredentialStore.getBinanceApiSecret() } catch(e: Exception) { "" }
            if (key.isNotBlank() && secret.isNotBlank()) {
                updateCredentials(key, secret)
            }
        }
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Binance API Keys not configured."))
        }
        return@withContext try {
            // Synchronize server time first to prevent timestamp errors
            if (timeOffsetMs == 0L) {
                syncServerTime()
            }
            val request = buildSignedRequest("/api/v3/account")
            val jsonStr = executeCall(request)
            val json = JSONObject(jsonStr)
            val balancesArr = json.getJSONArray("balances")
            val resultList = mutableListOf<BinanceAssetBalance>()

            for (i in 0 until balancesArr.length()) {
                val item = balancesArr.getJSONObject(i)
                val free = item.getString("free").toDoubleOrNull() ?: 0.0
                val locked = item.getString("locked").toDoubleOrNull() ?: 0.0
                if (free > 0.0 || locked > 0.0) {
                    resultList.add(
                        BinanceAssetBalance(
                            asset = item.getString("asset"),
                            free = free,
                            locked = locked
                        )
                    )
                }
            }
            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Queries /api/v3/account and verifies live Spot USDT equity.
     * Detects Binance Simple Earn (LDUSDT) and prevents positioning on zero spot equity.
     */
    suspend fun fetchSpotEquityAndEarnStatus(): Result<SpotEquityStatus> = withContext(Dispatchers.IO) {
        val balancesRes = fetchAccountBalances()
        if (balancesRes.isFailure) {
            return@withContext Result.failure(balancesRes.exceptionOrNull() ?: Exception("Failed to query /api/v3/account"))
        }
        val balances = balancesRes.getOrNull() ?: emptyList()
        var freeUsdt = 0.0
        var lockedUsdt = 0.0
        var ldFree = 0.0
        var ldLocked = 0.0

        for (b in balances) {
            val sym = b.asset.uppercase(Locale.US)
            if (sym == "USDT") {
                freeUsdt = b.free
                lockedUsdt = b.locked
            } else if (sym == "LDUSDT" || sym == "LENDING_USDT") {
                ldFree += b.free
                ldLocked += b.locked
            }
        }

        val totalSpot = freeUsdt + lockedUsdt
        val totalLd = ldFree + ldLocked
        val isEarnOnly = (freeUsdt <= 0.0 && totalLd > 0.0)
        val hasSufficient = freeUsdt >= 5.0

        val msg = when {
            isEarnOnly -> "Funds are in Binance Simple Earn. Redeem to Spot before trading."
            !hasSufficient -> "Insufficient free Spot USDT balance ($${String.format(Locale.US, "%.2f", freeUsdt)} USDT). Minimum Binance Spot order requires at least $5.00 USDT."
            else -> "Verified live Binance Spot equity: $${String.format(Locale.US, "%.2f", freeUsdt)} USDT."
        }

        Result.success(
            SpotEquityStatus(
                freeSpotUsdt = freeUsdt,
                lockedSpotUsdt = lockedUsdt,
                totalSpotUsdt = totalSpot,
                ldUsdtFree = ldFree,
                ldUsdtLocked = ldLocked,
                totalLdUsdt = totalLd,
                isEarnOnly = isEarnOnly,
                hasSufficientSpotBalance = hasSufficient,
                statusMessage = msg
            )
        )
    }

    /**
     * Fetch user's active open orders on Binance.
     */
    suspend fun fetchOpenOrders(symbol: String? = null): Result<List<BinanceOpenOrder>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Binance API Keys not configured."))
        }
        return@withContext try {
            if (timeOffsetMs == 0L) {
                syncServerTime()
            }
            val params = if (symbol != null) mapOf("symbol" to symbol) else emptyMap()
            val request = buildSignedRequest("/api/v3/openOrders", params)
            val jsonStr = executeCall(request)
            val jsonArr = JSONArray(jsonStr)
            val resultList = mutableListOf<BinanceOpenOrder>()

            for (i in 0 until jsonArr.length()) {
                val item = jsonArr.getJSONObject(i)
                resultList.add(
                    BinanceOpenOrder(
                        orderId = item.getLong("orderId").toString(),
                        symbol = item.getString("symbol"),
                        side = item.getString("side"),
                        type = item.getString("type"),
                        price = item.getString("price").toDoubleOrNull() ?: 0.0,
                        origQty = item.getString("origQty").toDoubleOrNull() ?: 0.0,
                        executedQty = item.getString("executedQty").toDoubleOrNull() ?: 0.0,
                        status = item.getString("status"),
                        time = item.getLong("time")
                    )
                )
            }
            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch user's completed/canceled order history on Binance.
     */
    suspend fun fetchOrderHistory(symbol: String): Result<List<BinanceOpenOrder>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Binance API Keys not configured."))
        }
        return@withContext try {
            if (timeOffsetMs == 0L) {
                syncServerTime()
            }
            val request = buildSignedRequest("/api/v3/allOrders", mapOf("symbol" to symbol, "limit" to "100"))
            val jsonStr = executeCall(request)
            val jsonArr = JSONArray(jsonStr)
            val resultList = mutableListOf<BinanceOpenOrder>()

            for (i in 0 until jsonArr.length()) {
                val item = jsonArr.getJSONObject(i)
                resultList.add(
                    BinanceOpenOrder(
                        orderId = item.getLong("orderId").toString(),
                        symbol = item.getString("symbol"),
                        side = item.getString("side"),
                        type = item.getString("type"),
                        price = item.getString("price").toDoubleOrNull() ?: 0.0,
                        origQty = item.getString("origQty").toDoubleOrNull() ?: 0.0,
                        executedQty = item.getString("executedQty").toDoubleOrNull() ?: 0.0,
                        status = item.getString("status"),
                        time = item.getLong("time")
                    )
                )
            }
            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch user's transaction/trade history on Binance.
     */
    suspend fun fetchTradeHistory(symbol: String): Result<List<BinanceTrade>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Binance API Keys not configured."))
        }
        return@withContext try {
            if (timeOffsetMs == 0L) {
                syncServerTime()
            }
            val request = buildSignedRequest("/api/v3/myTrades", mapOf("symbol" to symbol, "limit" to "100"))
            val jsonStr = executeCall(request)
            val jsonArr = JSONArray(jsonStr)
            val resultList = mutableListOf<BinanceTrade>()

            for (i in 0 until jsonArr.length()) {
                val item = jsonArr.getJSONObject(i)
                resultList.add(
                    BinanceTrade(
                        id = item.getLong("id").toString(),
                        orderId = item.getLong("orderId").toString(),
                        symbol = item.getString("symbol"),
                        price = item.getString("price").toDoubleOrNull() ?: 0.0,
                        qty = item.getString("qty").toDoubleOrNull() ?: 0.0,
                        commission = item.getString("commission").toDoubleOrNull() ?: 0.0,
                        commissionAsset = item.getString("commissionAsset"),
                        time = item.getLong("time"),
                        isBuyer = item.getBoolean("isBuyer")
                    )
                )
            }
            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch Exchange Information containing standard rules and symbols filters.
     */
    suspend fun fetchExchangeInfo(): Result<JSONObject> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("$BASE_URL/api/v3/exchangeInfo")
                .addHeader("Accept", "application/json")
                .build()
            val jsonStr = executeCall(request)
            Result.success(JSONObject(jsonStr))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch live price of a specific symbol from the spot market.
     */
    suspend fun fetchMarketPrice(symbol: String): Result<Double> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("$BASE_URL/api/v3/ticker/price?symbol=$symbol")
                .addHeader("Accept", "application/json")
                .build()

            val jsonStr = executeCall(request)
            val json = JSONObject(jsonStr)
            val price = json.getString("price").toDoubleOrNull() ?: 0.0
            Result.success(price)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch live prices of all symbols from the spot market.
     */
    suspend fun fetchAllMarketPrices(): Result<Map<String, Double>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("$BASE_URL/api/v3/ticker/price")
                .addHeader("Accept", "application/json")
                .build()

            val jsonStr = executeCall(request)
            val jsonArray = JSONArray(jsonStr)
            val priceMap = mutableMapOf<String, Double>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val symbol = obj.getString("symbol")
                val price = obj.getString("price").toDoubleOrNull() ?: 0.0
                priceMap[symbol] = price
            }
            Result.success(priceMap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Place an order on Binance Spot.
     * Support Market Buy/Sell, Limit Buy/Sell, and Post-Only Maker Limit (GTX).
     */
    private val symbolPrecisionCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun getSymbolPrecision(symbol: String): Int {
        val sym = symbol.uppercase()
        val cached = symbolPrecisionCache[sym]
        if (cached != null) return cached

        val precision = when (sym) {
            "BTCUSDT" -> 5
            "ETHUSDT" -> 4
            "BNBUSDT" -> 3
            "SOLUSDT" -> 2
            "SUIUSDT" -> 1
            "INJUSDT" -> 2
            "RENDERUSDT" -> 1
            "LINKUSDT" -> 2
            "AVAXUSDT" -> 2
            "TIAUSDT" -> 1
            "FETUSDT" -> 1
            "NEARUSDT" -> 1
            "DOGEUSDT" -> 0
            "PEPEUSDT" -> 0
            "ADAUSDT" -> 1
            "DOTUSDT" -> 2
            "MATICUSDT" -> 1
            "LTCUSDT" -> 3
            else -> 2
        }
        symbolPrecisionCache[sym] = precision
        return precision
    }

    suspend fun placeOrder(
        symbol: String,
        side: String,
        type: String,
        quantity: Double,
        price: Double? = null,
        timeInForce: String = "GTC",
        clientOrderId: String? = null
    ): Result<BinanceOpenOrder> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Binance API Keys not configured."))
        }
        if (_operatingMode.value == com.example.aza.strategy.LuminaOperatingMode.ANALYSIS_ONLY) {
            val gateEx = ExecutionGateBlockedException(
                reason = "Lumina is running in ANALYSIS_ONLY mode.",
                suggestedAction = "Switch to Live Approval Mode to stage actionable trades."
            )
            return@withContext Result.failure(gateEx)
        }
        if (!_isExecutionUnlocked.value) {
            val gateEx = ExecutionGateBlockedException(
                reason = "Master Execution Lock is ENGAGED (execution_enabled = false).",
                suggestedAction = "Unlock the Master Execution Switch in the Trading Screen header."
            )
            return@withContext Result.failure(gateEx)
        }
        if (!_isOrderPlacementEnabled.value) {
            return@withContext Result.failure(IllegalStateException("Order placement is disabled. Safety lock is active."))
        }
        return@withContext try {
            if (timeOffsetMs == 0L) {
                syncServerTime()
            }
            val stepDecimals = getSymbolPrecision(symbol)
            val params = mutableMapOf(
                "symbol" to symbol,
                "side" to side,
                "type" to type,
                "quantity" to String.format(java.util.Locale.US, "%.${stepDecimals}f", quantity)
            )
            if (type == "LIMIT") {
                if (price == null) {
                    throw IllegalArgumentException("Price is required for LIMIT orders.")
                }
                params["price"] = String.format(java.util.Locale.US, "%.2f", price)
                // GTX = Good-Til-Crossing (Post-Only). Guarantees Maker order on Binance.
                params["timeInForce"] = timeInForce
            }
            if (clientOrderId != null) {
                params["newClientOrderId"] = clientOrderId
            }

            val request = buildSignedRequest("/api/v3/order", params, method = "POST")
            val startTime = System.currentTimeMillis()
            val jsonStr = executeCall(request)
            val latency = System.currentTimeMillis() - startTime
            _lastLatencyMs.value = latency
            _lastSuccessfulSync.value = System.currentTimeMillis()

            val item = JSONObject(jsonStr)

            val orderId = item.optLong("orderId").toString()
            val finalPrice = item.optString("price", "0.0").toDoubleOrNull() ?: (price ?: 0.0)
            val origQty = item.optString("origQty", "0.0").toDoubleOrNull() ?: quantity
            val executedQty = item.optString("executedQty", "0.0").toDoubleOrNull() ?: 0.0
            val status = item.optString("status", "FILLED")
            val transactTime = item.optLong("transactTime", System.currentTimeMillis())

            Result.success(
                BinanceOpenOrder(
                    orderId = orderId,
                    symbol = symbol,
                    side = side,
                    type = type,
                    price = finalPrice,
                    origQty = origQty,
                    executedQty = executedQty,
                    status = status,
                    time = transactTime
                )
            )
        } catch (e: BinanceApiException) {
            if (side.equals("SELL", ignoreCase = true) && e.errorCode == -2010) {
                val impossibleEx = ImpossibleCloseOrderException(
                    symbol = symbol,
                    errorCode = -2010,
                    message = "Impossible close order for $symbol: Account has 0.00 base balance on Binance Spot. Position must be auto-pruned."
                )
                Log.w(TAG, impossibleEx.message)
                _lastErrorMessage.value = impossibleEx.message
                Result.failure(impossibleEx)
            } else if (e.errorCode == -5022 || (e.message?.contains("Post-only", ignoreCase = true) == true)) {
                // Post-only order would immediately match and trade, rejected by exchange to protect Maker status
                val postOnlyMsg = "🛡️ POST-ONLY SHIELD: Order on $symbol would take liquidity as taker. Canceled by Binance GTX filter to preserve 0% slippage & maker fee."
                Log.i(TAG, postOnlyMsg)
                _lastErrorMessage.value = postOnlyMsg
                Result.failure(e)
            } else {
                Log.e(TAG, "Order placement failed (${e.errorCode}): ${e.message}", e)
                _lastErrorMessage.value = e.message
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Order placement failed: ${e.message}", e)
            _lastErrorMessage.value = e.message
            Result.failure(e)
        }
    }

    /**
     * Convenience method to place a guaranteed Post-Only Limit Order (GTX) on Binance.
     * Guarantees 0% slippage and maker-tier fee (0.02% to 0.075%, or 0% on promo pairs).
     */
    suspend fun placePostOnlyLimitOrder(
        symbol: String,
        side: String,
        quantity: Double,
        price: Double,
        clientOrderId: String? = null
    ): Result<BinanceOpenOrder> {
        return placeOrder(
            symbol = symbol,
            side = side,
            type = "LIMIT",
            quantity = quantity,
            price = price,
            timeInForce = "GTX",
            clientOrderId = clientOrderId
        )
    }

    /**
     * Cancel an open order on Binance Spot.
     */
    suspend fun cancelOrder(symbol: String, orderId: String): Result<BinanceOpenOrder> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Binance API Keys not configured."))
        }
        return@withContext try {
            if (timeOffsetMs == 0L) {
                syncServerTime()
            }
            val params = mapOf(
                "symbol" to symbol,
                "orderId" to orderId
            )
            val request = buildSignedRequest("/api/v3/order", params, method = "DELETE")
            val startTime = System.currentTimeMillis()
            val jsonStr = executeCall(request)
            val latency = System.currentTimeMillis() - startTime
            _lastLatencyMs.value = latency
            _lastSuccessfulSync.value = System.currentTimeMillis()

            val item = JSONObject(jsonStr)

            Result.success(
                BinanceOpenOrder(
                    orderId = item.optLong("orderId").toString(),
                    symbol = item.optString("symbol", symbol),
                    side = item.optString("side", "BUY"),
                    type = item.optString("type", "LIMIT"),
                    price = item.optString("price", "0.0").toDoubleOrNull() ?: 0.0,
                    origQty = item.optString("origQty", "0.0").toDoubleOrNull() ?: 0.0,
                    executedQty = item.optString("executedQty", "0.0").toDoubleOrNull() ?: 0.0,
                    status = item.optString("status", "CANCELED"),
                    time = item.optLong("transactTime", System.currentTimeMillis())
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Order cancellation failed: ${e.message}", e)
            _lastErrorMessage.value = e.message
            Result.failure(e)
        }
    }

    /**
     * Fetch candles (Klines) for a specific symbol, interval, and limit.
     */
    suspend fun fetchCandles(symbol: String, interval: String = "1h", limit: Int = 100): Result<List<BinanceCandle>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("$BASE_URL/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit")
                .addHeader("Accept", "application/json")
                .build()

            val jsonStr = executeCall(request)
            val jsonArr = JSONArray(jsonStr)
            val resultList = mutableListOf<BinanceCandle>()

            for (i in 0 until jsonArr.length()) {
                val item = jsonArr.getJSONArray(i)
                resultList.add(
                    BinanceCandle(
                        openTime = item.getLong(0),
                        open = item.getString(1).toDoubleOrNull() ?: 0.0,
                        high = item.getString(2).toDoubleOrNull() ?: 0.0,
                        low = item.getString(3).toDoubleOrNull() ?: 0.0,
                        close = item.getString(4).toDoubleOrNull() ?: 0.0,
                        volume = item.getString(5).toDoubleOrNull() ?: 0.0,
                        closeTime = item.getLong(6)
                    )
                )
            }
            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Helper extension to avoid null values.
     */
    private fun Int?.orZero(): Int = this ?: 0
}

data class BinanceCandle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long
)

data class BinanceAssetBalance(
    val asset: String,
    val free: Double,
    val locked: Double
) {
    val total: Double get() = free + locked
}

data class SpotEquityStatus(
    val freeSpotUsdt: Double,
    val lockedSpotUsdt: Double,
    val totalSpotUsdt: Double,
    val ldUsdtFree: Double,
    val ldUsdtLocked: Double,
    val totalLdUsdt: Double,
    val isEarnOnly: Boolean,
    val hasSufficientSpotBalance: Boolean,
    val statusMessage: String
)

data class BinanceOpenOrder(
    val orderId: String,
    val symbol: String,
    val side: String,
    val type: String,
    val price: Double,
    val origQty: Double,
    val executedQty: Double,
    val status: String,
    val time: Long
)

data class BinanceTrade(
    val id: String,
    val orderId: String,
    val symbol: String,
    val price: Double,
    val qty: Double,
    val commission: Double,
    val commissionAsset: String,
    val time: Long,
    val isBuyer: Boolean
)
