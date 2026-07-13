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
            val intent = Intent(context, AzaBackgroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AzaBackgroundService::class.java)
            context.stopService(intent)
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
        SystemNotificationHelper.init(applicationContext)
        try {
            com.example.aza.security.SecureCredentialStore.init(applicationContext)
            val savedMode = com.example.aza.security.SecureCredentialStore.getBinanceTradingMode()
            com.example.aza.connector.BinanceConnector.setOrderPlacementEnabled(savedMode == "Autonomous Trading")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SecureCredentialStore or sync trading mode", e)
        }
        
        // Promote to Foreground Service to prevent background execution limits
        val notification = SystemNotificationHelper.getForegroundNotification("Aza continuous executive scheduler active in background.")
        startForeground(9922, notification)

        if (!isRunning) {
            isRunning = true
            startBackgroundScheduler()
        }

        return START_STICKY
    }

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
                
                SystemNotificationHelper.showNotification(
                    "Aza Autopilot Enabled",
                    "Aza Background Service has successfully connected and started active market surveillance."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start background scheduler", e)
                SystemNotificationHelper.showNotification(
                    "Aza Autopilot Error",
                    "Aza Background Service failed to start: ${e.localizedMessage}"
                )
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
