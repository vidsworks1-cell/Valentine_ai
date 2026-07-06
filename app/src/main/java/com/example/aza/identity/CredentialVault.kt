package com.example.aza.identity

import com.example.aza.security.SecureCredentialStore

object CredentialVault {
    
    fun saveToken(provider: ProviderType, token: String, refreshToken: String = "") {
        when (provider) {
            ProviderType.GOOGLE, ProviderType.YOUTUBE, ProviderType.GMAIL, ProviderType.GOOGLE_DRIVE, ProviderType.GOOGLE_CALENDAR -> {
                SecureCredentialStore.encrypt("${provider.name.lowercase()}_oauth_token", token)
                if (refreshToken.isNotEmpty()) {
                    SecureCredentialStore.encrypt("${provider.name.lowercase()}_refresh_token", refreshToken)
                }
            }
            ProviderType.BINANCE -> {
                SecureCredentialStore.encrypt("binance_api_key", token)
            }
            ProviderType.GITHUB -> {
                SecureCredentialStore.encrypt("github_oauth_token", token)
            }
        }
    }

    fun getToken(provider: ProviderType): String {
        return when (provider) {
            ProviderType.GOOGLE, ProviderType.YOUTUBE, ProviderType.GMAIL, ProviderType.GOOGLE_DRIVE, ProviderType.GOOGLE_CALENDAR -> {
                SecureCredentialStore.decrypt("${provider.name.lowercase()}_oauth_token") ?: ""
            }
            ProviderType.BINANCE -> {
                SecureCredentialStore.decrypt("binance_api_key") ?: ""
            }
            ProviderType.GITHUB -> {
                SecureCredentialStore.decrypt("github_oauth_token") ?: ""
            }
        }
    }

    fun getRefreshToken(provider: ProviderType): String {
        return when (provider) {
            ProviderType.GOOGLE, ProviderType.YOUTUBE, ProviderType.GMAIL, ProviderType.GOOGLE_DRIVE, ProviderType.GOOGLE_CALENDAR -> {
                SecureCredentialStore.decrypt("${provider.name.lowercase()}_refresh_token") ?: ""
            }
            else -> ""
        }
    }

    fun saveSecret(provider: ProviderType, secret: String) {
        when (provider) {
            ProviderType.BINANCE -> {
                SecureCredentialStore.encrypt("binance_api_secret", secret)
            }
            else -> {
                SecureCredentialStore.encrypt("${provider.name.lowercase()}_api_secret", secret)
            }
        }
    }

    fun getSecret(provider: ProviderType): String {
        return when (provider) {
            ProviderType.BINANCE -> {
                SecureCredentialStore.decrypt("binance_api_secret") ?: ""
            }
            else -> {
                SecureCredentialStore.decrypt("${provider.name.lowercase()}_api_secret") ?: ""
            }
        }
    }

    fun clearCredentials(provider: ProviderType) {
        when (provider) {
            ProviderType.GOOGLE, ProviderType.YOUTUBE, ProviderType.GMAIL, ProviderType.GOOGLE_DRIVE, ProviderType.GOOGLE_CALENDAR -> {
                SecureCredentialStore.delete("${provider.name.lowercase()}_oauth_token")
                SecureCredentialStore.delete("${provider.name.lowercase()}_refresh_token")
                SecureCredentialStore.delete("${provider.name.lowercase()}_channel_id")
                SecureCredentialStore.delete("${provider.name.lowercase()}_channel_name")
            }
            ProviderType.BINANCE -> {
                SecureCredentialStore.deleteBinanceCredentials()
            }
            ProviderType.GITHUB -> {
                SecureCredentialStore.deleteGitHubCredentials()
            }
        }
    }

    fun clearAll() {
        ProviderType.values().forEach { clearCredentials(it) }
    }
}
