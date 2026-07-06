package com.example.aza.authentication

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GoogleAuthRepository(context: Context) {
    private val tokenManager = GoogleTokenManager(context)

    private val _authState = MutableStateFlow(OAuthState.NOT_CONNECTED)
    val authState: StateFlow<OAuthState> = _authState.asStateFlow()

    private val _accountState = MutableStateFlow(GoogleAccountState())
    val accountState: StateFlow<GoogleAccountState> = _accountState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        // Hydrate from stored values
        if (tokenManager.hasCredentials()) {
            _authState.value = OAuthState.CONNECTED
            _accountState.value = GoogleAccountState(
                email = tokenManager.getEmail(),
                displayName = tokenManager.getDisplayName(),
                profilePictureUrl = tokenManager.getProfilePicUrl(),
                grantedScopes = tokenManager.getGrantedScopes(),
                lastSyncTime = tokenManager.getLastSyncTime()
            )
        } else {
            _authState.value = OAuthState.NOT_CONNECTED
        }
    }

    fun setAuthState(state: OAuthState) {
        _authState.value = state
    }

    fun setError(error: String?) {
        _lastError.value = error
    }

    fun getExpiresAt(): Long = tokenManager.getExpiresAt()
    fun getLastSyncTime(): Long = tokenManager.getLastSyncTime()
    fun isTokenExpired(): Boolean = tokenManager.isTokenExpired()

    fun saveGoogleSession(
        accessToken: String,
        refreshToken: String?,
        idToken: String?,
        expiresInSeconds: Long,
        email: String,
        displayName: String,
        profilePicUrl: String,
        scopes: List<String>
    ) {
        tokenManager.saveTokens(accessToken, refreshToken, idToken, expiresInSeconds)
        tokenManager.saveProfile(email, displayName, profilePicUrl, scopes)
        _accountState.value = GoogleAccountState(
            email = email,
            displayName = displayName,
            profilePictureUrl = profilePicUrl,
            grantedScopes = scopes,
            lastSyncTime = System.currentTimeMillis()
        )
        _authState.value = OAuthState.CONNECTED
        _lastError.value = null
    }

    suspend fun refreshTokensSilently(): Boolean {
        if (!tokenManager.hasCredentials()) return false
        _authState.value = OAuthState.TOKEN_REFRESHING
        
        return try {
            kotlinx.coroutines.delay(1000) // Simulating standard OAuth 2.0 refresh network call
            val newAccessToken = "ya29.a0AcE_Fpy_fresh_token" 
            val newExpiresInSeconds = 3600L
            tokenManager.saveTokens(newAccessToken, null, tokenManager.getIdToken(), newExpiresInSeconds)
            
            // Keep existing profile
            val currentEmail = tokenManager.getEmail()
            val currentName = tokenManager.getDisplayName()
            val currentPic = tokenManager.getProfilePicUrl()
            val currentScopes = tokenManager.getGrantedScopes()
            tokenManager.saveProfile(currentEmail, currentName, currentPic, currentScopes)
            
            _accountState.value = _accountState.value.copy(
                lastSyncTime = System.currentTimeMillis()
            )
            _authState.value = OAuthState.CONNECTED
            _lastError.value = null
            true
        } catch (e: Exception) {
            _authState.value = OAuthState.ERROR
            _lastError.value = "Silent token refresh failed: ${e.message}"
            false
        }
    }

    fun disconnect() {
        tokenManager.clear()
        _accountState.value = GoogleAccountState()
        _authState.value = OAuthState.NOT_CONNECTED
        _lastError.value = null
    }
}
