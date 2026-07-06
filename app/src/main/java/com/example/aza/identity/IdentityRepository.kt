package com.example.aza.identity

import android.content.Context

class IdentityRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("aza_identity_meta", Context.MODE_PRIVATE)

    fun saveConnectionState(provider: ProviderType, state: ConnectionState) {
        prefs.edit().putString("${provider.name}_state", state.name).apply()
    }

    fun getConnectionState(provider: ProviderType): ConnectionState {
        val name = prefs.getString("${provider.name}_state", null) ?: return ConnectionState.NOT_CONFIGURED
        return try {
            ConnectionState.valueOf(name)
        } catch (e: Exception) {
            ConnectionState.NOT_CONFIGURED
        }
    }

    fun saveAccountName(provider: ProviderType, name: String) {
        prefs.edit().putString("${provider.name}_account", name).apply()
    }

    fun getAccountName(provider: ProviderType): String? {
        return prefs.getString("${provider.name}_account", null)
    }

    fun saveLastSync(provider: ProviderType, timestamp: Long) {
        prefs.edit().putLong("${provider.name}_last_sync", timestamp).apply()
    }

    fun getLastSync(provider: ProviderType): Long {
        return prefs.getLong("${provider.name}_last_sync", 0L)
    }

    fun clear(provider: ProviderType) {
        prefs.edit()
            .remove("${provider.name}_state")
            .remove("${provider.name}_account")
            .remove("${provider.name}_last_sync")
            .apply()
    }
}
