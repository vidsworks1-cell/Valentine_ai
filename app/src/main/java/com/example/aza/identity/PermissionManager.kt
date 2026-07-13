package com.example.aza.identity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PermissionManager {
    private val grantedPermissions = mutableMapOf<ProviderType, List<String>>()

    fun grantPermissions(provider: ProviderType, permissions: List<String>) {
        grantedPermissions[provider] = permissions
        IdentityEvents.emit(IdentityEvents.Event.PermissionChanges(provider, permissions))
    }

    fun getPermissions(provider: ProviderType): List<String> {
        return grantedPermissions[provider] ?: when (provider) {
            ProviderType.GOOGLE -> listOf("https://www.googleapis.com/auth/userinfo.profile", "https://www.googleapis.com/auth/userinfo.email")
            ProviderType.YOUTUBE -> listOf("https://www.googleapis.com/auth/youtube.readonly", "https://www.googleapis.com/auth/youtube.upload", "https://www.googleapis.com/auth/youtube.force-ssl")
            ProviderType.BINANCE -> listOf("Read", "TradingEnabled")
            ProviderType.GITHUB -> listOf("repo", "user", "workflow")
            ProviderType.GMAIL -> listOf("https://www.googleapis.com/auth/gmail.readonly", "https://www.googleapis.com/auth/gmail.send")
            ProviderType.GOOGLE_DRIVE -> listOf("https://www.googleapis.com/auth/drive.readonly", "https://www.googleapis.com/auth/drive.file")
            ProviderType.GOOGLE_CALENDAR -> listOf("https://www.googleapis.com/auth/calendar.readonly", "https://www.googleapis.com/auth/calendar.events")
            ProviderType.CCP -> listOf("CHECK_BALANCE", "INITIATE_DEPOSIT", "WIRE_SETTLEMENT")
        }
    }

    fun isPermissionGranted(provider: ProviderType, permission: String): Boolean {
        return getPermissions(provider).contains(permission)
    }

    fun revokePermissions(provider: ProviderType) {
        grantedPermissions.remove(provider)
        IdentityEvents.emit(IdentityEvents.Event.PermissionChanges(provider, emptyList()))
    }
}
