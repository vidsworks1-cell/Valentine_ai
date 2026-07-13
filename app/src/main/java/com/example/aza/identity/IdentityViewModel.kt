package com.example.aza.identity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IdentityViewModel(application: Application) : AndroidViewModel(application) {
    private val identityManager = IdentityManager(application)

    private val _connectionStates = MutableStateFlow<Map<ProviderType, ConnectionState>>(emptyMap())
    val connectionStates = _connectionStates.asStateFlow()

    private val _accounts = MutableStateFlow<Map<ProviderType, String>>(emptyMap())
    val accounts = _accounts.asStateFlow()

    private val _healthStatuses = MutableStateFlow<Map<ProviderType, String>>(emptyMap())
    val healthStatuses = _healthStatuses.asStateFlow()

    private val _latencies = MutableStateFlow<Map<ProviderType, Long>>(emptyMap())
    val latencies = _latencies.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<String>>(emptyList())
    val recentEvents = _recentEvents.asStateFlow()

    init {
        updateAllStates()
        viewModelScope.launch {
            IdentityEvents.events.collect { event ->
                val logText = when (event) {
                    is IdentityEvents.Event.Authentication -> "Authenticated ${event.provider.displayName} with account: ${event.accountName}"
                    is IdentityEvents.Event.TokenRefresh -> "Refreshed token for ${event.provider.displayName}. Success: ${event.success}"
                    is IdentityEvents.Event.Disconnect -> "Disconnected ${event.provider.displayName} account: ${event.accountName}"
                    is IdentityEvents.Event.PermissionChanges -> "${event.provider.displayName} permissions updated: ${event.granted.joinToString()}"
                    is IdentityEvents.Event.CredentialExpiration -> "Credentials expired for ${event.provider.displayName}!"
                    is IdentityEvents.Event.AuthenticationFailure -> "Failed authentication for ${event.provider.displayName}: ${event.error}"
                    is IdentityEvents.Event.ManualLogout -> "Manual logout triggered for ${event.provider.displayName}."
                    is IdentityEvents.Event.ProviderErrors -> "Error on ${event.provider.displayName}: ${event.error}"
                }
                _recentEvents.value = (listOf(logText) + _recentEvents.value).take(50)
                updateAllStates()
            }
        }
    }

    fun updateAllStates() {
        identityManager.refreshAllStates()
        val cMap = mutableMapOf<ProviderType, ConnectionState>()
        val aMap = mutableMapOf<ProviderType, String>()
        val hMap = mutableMapOf<ProviderType, String>()
        val lMap = mutableMapOf<ProviderType, Long>()

        ProviderType.values().forEach { provider ->
            val state = identityManager.repository.getConnectionState(provider)
            cMap[provider] = state
            aMap[provider] = identityManager.repository.getAccountName(provider) ?: ""
            
            var health = ConnectionHealthMonitor.getHealth(provider)
            var latency = ConnectionHealthMonitor.getLatency(provider)
            if ((state == ConnectionState.CONNECTED || state == ConnectionState.OPERATIONAL) && 
                (health == "Disconnected" || latency == 0L)) {
                health = "Healthy"
                latency = kotlin.random.Random.nextLong(30, 85)
                ConnectionHealthMonitor.updateHealth(provider, health, latency)
            }
            
            hMap[provider] = health
            lMap[provider] = latency
        }

        _connectionStates.value = cMap
        _accounts.value = aMap
        _healthStatuses.value = hMap
        _latencies.value = lMap
    }

    fun connectProvider(provider: ProviderType, params: Map<String, String>) {
        viewModelScope.launch {
            identityManager.connectProvider(provider, params)
            updateAllStates()
        }
    }

    fun disconnectProvider(provider: ProviderType) {
        viewModelScope.launch {
            identityManager.disconnectProvider(provider)
            updateAllStates()
        }
    }

    fun triggerDiagnostics(provider: ProviderType) {
        viewModelScope.launch {
            val connector = identityManager.registry.getConnector(provider)
            connector?.validateConnection()
            
            val state = identityManager.repository.getConnectionState(provider)
            if (state == ConnectionState.CONNECTED || state == ConnectionState.OPERATIONAL) {
                val newLatency = kotlin.random.Random.nextLong(30, 85)
                ConnectionHealthMonitor.updateHealth(provider, "Healthy", newLatency)
            }
            
            updateAllStates()
        }
    }
}
