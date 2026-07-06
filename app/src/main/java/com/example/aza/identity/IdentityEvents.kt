package com.example.aza.identity

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object IdentityEvents {
    sealed class Event {
        data class Authentication(val provider: ProviderType, val accountName: String) : Event()
        data class TokenRefresh(val provider: ProviderType, val success: Boolean) : Event()
        data class Disconnect(val provider: ProviderType, val accountName: String) : Event()
        data class PermissionChanges(val provider: ProviderType, val granted: List<String>) : Event()
        data class CredentialExpiration(val provider: ProviderType) : Event()
        data class AuthenticationFailure(val provider: ProviderType, val error: String) : Event()
        data class ManualLogout(val provider: ProviderType) : Event()
        data class ProviderErrors(val provider: ProviderType, val error: String) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun emit(event: Event) {
        _events.tryEmit(event)
    }
}
