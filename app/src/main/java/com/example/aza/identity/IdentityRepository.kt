package com.example.aza.identity

import android.content.Context

class IdentityRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("aza_identity_meta", Context.MODE_PRIVATE)

    fun saveConnectionState(provider: ProviderType, state: ConnectionState) {
        prefs.edit().putString("${provider.name}_state", state.name).apply()
    }

    fun getConnectionState(provider: ProviderType): ConnectionState {
        val name = prefs.getString("${provider.name}_state", null)
        val state = try {
            if (name != null) ConnectionState.valueOf(name) else ConnectionState.NOT_CONFIGURED
        } catch (e: Exception) {
            ConnectionState.NOT_CONFIGURED
        }

        if (state == ConnectionState.CONNECTED || state == ConnectionState.OPERATIONAL) {
            return state
        }

        // Self-heal/validate if keys are present in Keystore but SharedPreferences says NOT_CONFIGURED
        val hasKeys = when (provider) {
            ProviderType.BINANCE -> {
                com.example.aza.security.SecureCredentialStore.getBinanceApiKey().isNotEmpty() &&
                com.example.aza.security.SecureCredentialStore.getBinanceApiSecret().isNotEmpty()
            }
            ProviderType.GITHUB -> {
                com.example.aza.security.SecureCredentialStore.getGitHubOAuthToken().isNotEmpty()
            }
            ProviderType.YOUTUBE -> {
                com.example.aza.security.SecureCredentialStore.getYouTubeOAuthToken().isNotEmpty()
            }
            ProviderType.GMAIL, ProviderType.GOOGLE_DRIVE, ProviderType.GOOGLE_CALENDAR -> {
                com.example.aza.identity.CredentialVault.getToken(provider).isNotEmpty()
            }
            ProviderType.CCP -> {
                com.example.aza.security.SecureCredentialStore.getCcpAccountNumber().isNotEmpty()
            }
            else -> false
        }

        return if (hasKeys) {
            saveConnectionState(provider, ConnectionState.CONNECTED)
            ConnectionState.CONNECTED
        } else {
            state
        }
    }

    fun saveAccountName(provider: ProviderType, name: String) {
        prefs.edit().putString("${provider.name}_account", name).apply()
    }

    fun getAccountName(provider: ProviderType): String? {
        val name = prefs.getString("${provider.name}_account", null)
        if (!name.isNullOrBlank()) return name

        // Self-heal account name if authenticated but missing from prefs
        return when (provider) {
            ProviderType.BINANCE -> {
                val apiKey = com.example.aza.security.SecureCredentialStore.getBinanceApiKey()
                val apiSecret = com.example.aza.security.SecureCredentialStore.getBinanceApiSecret()
                if (apiKey.isNotEmpty() && apiSecret.isNotEmpty()) {
                    val env = com.example.aza.security.SecureCredentialStore.getBinanceEnv()
                    val result = "Binance Account ($env)"
                    saveAccountName(provider, result)
                    result
                } else null
            }
            ProviderType.GITHUB -> {
                val token = com.example.aza.security.SecureCredentialStore.getGitHubOAuthToken()
                if (token.isNotEmpty()) {
                    val username = com.example.aza.security.SecureCredentialStore.getGitHubUsername().ifBlank { "GitHub Dev" }
                    saveAccountName(provider, username)
                    username
                } else null
            }
            ProviderType.YOUTUBE -> {
                val name = com.example.aza.security.SecureCredentialStore.getYouTubeChannelName()
                if (name.isNotEmpty()) {
                    saveAccountName(provider, name)
                    name
                } else null
            }
            ProviderType.GMAIL -> {
                val token = com.example.aza.identity.CredentialVault.getToken(provider)
                if (token.isNotEmpty()) {
                    val result = "Gmail Account"
                    saveAccountName(provider, result)
                    result
                } else null
            }
            ProviderType.GOOGLE_DRIVE -> {
                val token = com.example.aza.identity.CredentialVault.getToken(provider)
                if (token.isNotEmpty()) {
                    val result = "Google Drive"
                    saveAccountName(provider, result)
                    result
                } else null
            }
            ProviderType.GOOGLE_CALENDAR -> {
                val token = com.example.aza.identity.CredentialVault.getToken(provider)
                if (token.isNotEmpty()) {
                    val result = "Google Calendar"
                    saveAccountName(provider, result)
                    result
                } else null
            }
            ProviderType.CCP -> {
                val acc = com.example.aza.security.SecureCredentialStore.getCcpAccountNumber()
                if (acc.isNotEmpty()) {
                    val result = "CCP Compte No: $acc"
                    saveAccountName(provider, result)
                    result
                } else null
            }
            else -> null
        }
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
