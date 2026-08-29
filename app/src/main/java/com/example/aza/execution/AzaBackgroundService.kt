package com.example.aza.execution

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.aza.capability.CapabilityRegistry
import com.example.aza.planning.AzaPlanningEngine
import com.example.data.database.AppDatabase
import com.example.data.repository.AppRepository
import kotlinx.coroutines.*

class AzaBackgroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private var scheduler: AzaAutonomousScheduler? = null
    private var isRunning = false

    companion object {
        private const val TAG = "AzaBackgroundService"
        var isServiceRunning = false
            private set

        fun startService(context: Context) {
            try {
                val intent = Intent(context, AzaBackgroundService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start AzaBackgroundService", e)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, AzaBackgroundService::class.java)
                context.stopService(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to stop AzaBackgroundService", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Background Service Initializing")
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Starting background daemon")
        
        // Initialize Notification Helper and secure credential store
        try {
            SystemNotificationHelper.init(applicationContext)
            com.example.aza.security.SecureCredentialStore.init(applicationContext)
            val savedMode = com.example.aza.security.SecureCredentialStore.getBinanceTradingMode()
            com.example.aza.connector.BinanceConnector.setOrderPlacementEnabled(savedMode == "Autonomous Trading")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SecureCredentialStore or sync trading mode", e)
        }
        
        // Promote to Foreground Service to prevent background execution limits
        try {
            val notification = SystemNotificationHelper.getForegroundNotification(
                "Lumina continuous executive scheduler active in background.",
                applicationContext
            )
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    9922,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    9922,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                )
            } else {
                startForeground(9922, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to startForeground on background service", e)
        }

        if (!isRunning) {
            isRunning = true
            startBackgroundScheduler()
        }

        return START_STICKY
    }

    private val lastKnownExecutionStates = mutableMapOf<String, String>()
    private var lastEmergencyState: Boolean? = null
    private var lastCircuitBreakerState: Boolean? = null

    private fun startBackgroundScheduler() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Initializing Room database and scheduler components inside Background Service")
                val database = AppDatabase.getDatabase(applicationContext, serviceScope)
                val repository = AppRepository(database)
                val planningEngine = AzaPlanningEngine(repository)
                val capabilityRegistry = CapabilityRegistry(repository).apply {
                    registerCapability(com.example.aza.capability.BinanceCapability())
                    registerCapability(com.example.aza.capability.ContentCapability())
                    registerCapability(com.example.aza.capability.YouTubeCapability())
                    registerCapability(com.example.aza.capability.FreelanceCapability())
                    registerCapability(com.example.aza.capability.NotificationCapability())
                    registerCapability(com.example.aza.capability.LaptopAgentCapability())
                }

                scheduler = AzaAutonomousScheduler(repository, planningEngine, serviceScope, capabilityRegistry)
                
                scheduler?.startPollingScheduler { log ->
                    Log.d(TAG, "DAEMON: $log")
                }

                // Launch backend continuous trade lifecycle & event surveillance loop
                startBackendSurveillanceLoop(repository)
                
                SystemNotificationHelper.showNotification(
                    "Lumina Autopilot Active",
                    "Lumina Background Service is actively monitoring markets, tasks, and follow-ups while the app is closed."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start background scheduler", e)
                SystemNotificationHelper.showNotification(
                    "Lumina Autopilot Error",
                    "Lumina Background Service failed to start: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun startBackendSurveillanceLoop(repository: AppRepository) {
        serviceScope.launch {
            while (isRunning && isActive) {
                try {
                    // 1. Poll backend execution state lifecycle
                    val executionStatesResponse = try {
                        repository.getExecutionStates()
                    } catch (e: Exception) {
                        null
                    }

                    if (executionStatesResponse != null) {
                        executionStatesResponse.currentStates.forEach { (symbol, record) ->
                            val previousState = lastKnownExecutionStates[symbol.uppercase()]
                            val currentState = record.state

                            if (previousState != null && previousState != currentState) {
                                // State transition detected!
                                val stateEnum = com.example.aza.strategy.ExecutionState.fromString(currentState)
                                val displayLabel = stateEnum?.uiDisplayLabel ?: currentState
                                val title = "Trade Update: $symbol ($displayLabel)"
                                val message = buildString {
                                    append("${record.side} $symbol: $displayLabel.")
                                    if (record.price != null) append(" Price: $${record.price}.")
                                    if (record.userMessage.isNotBlank()) append(" ${record.userMessage}")
                                }

                                SystemNotificationHelper.showNotification(
                                    title = title,
                                    message = message
                                )

                                repository.insertChatMessage(
                                    com.example.data.database.ChatMessage(
                                        sender = "AZA OPERATOR",
                                        text = "📡 **BACKGROUND TRADE UPDATE [${symbol.uppercase()}]:** State transitioned from `$previousState` to **$displayLabel**.\n${record.userMessage}"
                                    )
                                )
                            }
                            lastKnownExecutionStates[symbol.uppercase()] = currentState
                        }
                    }

                    // 2. Poll Institutional Risk & Trading Halt status
                    try {
                        val riskStatus = repository.getInstitutionalRiskStatus()
                        val isHalted = riskStatus.tradingHalted
                        if (lastEmergencyState != null && lastEmergencyState != isHalted) {
                            if (isHalted) {
                                SystemNotificationHelper.showNotification(
                                    "🚨 RISK HALT ENGAGED",
                                    "Trading halted on Contabo backend: ${riskStatus.haltReason ?: "Risk limit threshold reached"}."
                                )
                            }
                        }
                        lastEmergencyState = isHalted

                        val health = repository.getProductionMonitoringHealth()
                        val isHealthy = health.status.equals("HEALTHY", ignoreCase = true)
                        if (lastCircuitBreakerState != null && lastCircuitBreakerState != isHealthy) {
                            if (!isHealthy) {
                                SystemNotificationHelper.showNotification(
                                    "⚠️ VPS HEALTH ALERT",
                                    "Contabo production monitoring reported status: ${health.status}."
                                )
                            }
                        }
                        lastCircuitBreakerState = isHealthy
                    } catch (e: Exception) {
                        // ignore transient backend network errors
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Backend surveillance loop cycle error", e)
                }

                delay(20000) // check every 20 seconds
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Shutting down Background Service")
        scheduler?.stopPollingScheduler()
        serviceJob.cancel()
        isRunning = false
        isServiceRunning = false
        
        SystemNotificationHelper.showNotification(
            "Aza Autopilot Paused",
            "Aza Background Service has been deactivated. Portfolios will only synchronize when app is open."
        )
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
