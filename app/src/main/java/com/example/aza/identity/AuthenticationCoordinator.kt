package com.example.aza.identity

import android.content.Context
import android.util.Log

class AuthenticationCoordinator(private val context: Context, private val registry: AccountRegistry) {

    suspend fun startAuthenticationFlow(provider: ProviderType, params: Map<String, String>): AuthenticationResult {
        Log.d("AuthenticationCoordinator", "Starting authentication flow for $provider...")
        val connector = registry.getConnector(provider) ?: return AuthenticationResult.Failure("No connector found for $provider.")
        
        // Execute integration flow based on provider type
        return try {
            val result = connector.connect(params)
            when (result) {
                is AuthenticationResult.Success -> {
                    IdentityEvents.emit(IdentityEvents.Event.Authentication(provider, result.accountName))
                }
                is AuthenticationResult.Failure -> {
                    IdentityEvents.emit(IdentityEvents.Event.AuthenticationFailure(provider, result.error))
                }
            }
            result
        } catch (e: Exception) {
            val err = e.message ?: "Unknown authentication error"
            IdentityEvents.emit(IdentityEvents.Event.AuthenticationFailure(provider, err))
            AuthenticationResult.Failure(err)
        }
    }

    suspend fun disconnectProvider(provider: ProviderType) {
        val connector = registry.getConnector(provider) ?: return
        connector.disconnect()
    }
}
