package com.example.aza.identity

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class IdentityManager(private val context: Context) {
    val repository = IdentityRepository(context)
    val registry = AccountRegistry(context, repository)
    val coordinator = AuthenticationCoordinator(context, registry)

    private val _states = MutableStateFlow<Map<ProviderType, ConnectionState>>(emptyMap())
    val states = _states.asStateFlow()

    init {
        refreshAllStates()
    }

    fun refreshAllStates() {
        val newStates = ProviderType.values().associateWith {
            repository.getConnectionState(it)
        }
        _states.value = newStates
    }

    suspend fun connectProvider(provider: ProviderType, params: Map<String, String>): AuthenticationResult {
        val res = coordinator.startAuthenticationFlow(provider, params)
        refreshAllStates()
        return res
    }

    suspend fun disconnectProvider(provider: ProviderType) {
        coordinator.disconnectProvider(provider)
        refreshAllStates()
    }

    fun isProviderConnected(provider: ProviderType): Boolean {
        return repository.getConnectionState(provider) == ConnectionState.CONNECTED
    }

    fun getConnectedAccount(provider: ProviderType): String? {
        return repository.getAccountName(provider)
    }

    fun getHealthStatus(provider: ProviderType): String {
        return ConnectionHealthMonitor.getHealth(provider)
    }

    fun getLatency(provider: ProviderType): Long {
        return ConnectionHealthMonitor.getLatency(provider)
    }
}
