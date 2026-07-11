package com.example.aza.identity

import android.content.Context
import android.util.Log
import com.example.aza.security.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AccountRegistry(private val context: Context, private val repository: IdentityRepository) {

    private val connectors = mutableMapOf<ProviderType, UnifiedConnector>()

    init {
        // Register all initial connectors
        registerConnector(createGoogleConnector())
        registerConnector(createYouTubeConnector())
        registerConnector(createBinanceConnector())
        registerConnector(createGitHubConnector())
        registerConnector(createGmailConnector())
        registerConnector(createGoogleDriveConnector())
        registerConnector(createGoogleCalendarConnector())
    }

    fun registerConnector(connector: UnifiedConnector) {
        connectors[connector.provider] = connector
    }

    fun getConnector(provider: ProviderType): UnifiedConnector? {
        return connectors[provider]
    }

    fun getAllConnectors(): List<UnifiedConnector> {
        return connectors.values.toList()
    }

    private fun createGoogleConnector() = object : UnifiedConnector {
        override val provider: ProviderType = ProviderType.GOOGLE
        private val tokenManager = com.example.aza.authentication.GoogleTokenManager(context)

        override suspend fun connect(params: Map<String, String>): AuthenticationResult = withContext(Dispatchers.IO) {
            val email = params["email"] ?: tokenManager.getEmail()
            if (email.isBlank()) {
                return@withContext AuthenticationResult.Failure("Google Account not connected via official flow.")
            }
            AuthenticationResult.Success(email, mapOf("scopes" to tokenManager.getGrantedScopes().joinToString(",")))
        }

        override suspend fun disconnect() {
            val account = currentAccount() ?: ""
            tokenManager.clear()
            repository.clear(provider)
            ConnectionHealthMonitor.updateHealth(provider, "Disconnected", 0)
            IdentityEvents.emit(IdentityEvents.Event.Disconnect(provider, account))
        }

        override suspend fun refreshAuthentication(): Boolean {
            return tokenManager.hasCredentials()
        }

        override suspend fun isAuthenticated(): Boolean {
            return tokenManager.hasCredentials()
        }

        override suspend fun isOperational(): Boolean {
            return isAuthenticated() && !tokenManager.isTokenExpired()
        }

        override suspend fun currentAccount(): String? {
            val email = tokenManager.getEmail()
            return if (email.isBlank()) null else email
        }

        override suspend fun grantedPermissions(): List<String> {
            return tokenManager.getGrantedScopes()
        }

        override suspend fun connectionHealth(): String {
            return if (isOperational()) "Healthy" else "Disconnected"
        }

        override suspend fun lastSynchronization(): Long {
            return tokenManager.getLastSyncTime()
        }

        override suspend fun validateConnection(): ConnectionState {
            if (!isAuthenticated()) {
                repository.saveConnectionState(provider, ConnectionState.NOT_CONFIGURED)
                return ConnectionState.NOT_CONFIGURED
            }
            if (tokenManager.isTokenExpired()) {
                repository.saveConnectionState(provider, ConnectionState.REVOKED)
                return ConnectionState.REVOKED
            }
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            return ConnectionState.CONNECTED
        }
    }

    private fun createYouTubeConnector() = object : UnifiedConnector {
        override val provider: ProviderType = ProviderType.YOUTUBE

        override suspend fun connect(params: Map<String, String>): AuthenticationResult = withContext(Dispatchers.IO) {
            val channelName = params["channel_name"] ?: params["email"] ?: "My YouTube Channel"
            val channelId = params["channel_id"] ?: "UC_DUMMY_ID"
            val token = params["oauth_token"] ?: params["token"] ?: "dummy_yt_token"
            
            CredentialVault.saveToken(provider, token)
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            repository.saveAccountName(provider, channelName)
            repository.saveLastSync(provider, System.currentTimeMillis())
            ConnectionHealthMonitor.updateHealth(provider, "Healthy", 60)
            
            // Save detailed channel metadata inside Secure Store
            SecureCredentialStore.saveYouTubeCredentials(
                token = token,
                refreshToken = "dummy_ref",
                channelId = channelId,
                channelName = channelName,
                subscribers = params["subscribers"]?.toIntOrNull() ?: 1240,
                views = params["views"]?.toIntOrNull() ?: 45000,
                videos = params["videos"]?.toIntOrNull() ?: 48,
                playlists = params["playlists"]?.toIntOrNull() ?: 5
            )

            IdentityEvents.emit(IdentityEvents.Event.Authentication(provider, channelName))
            AuthenticationResult.Success(channelName, mapOf("channel_id" to channelId))
        }

        override suspend fun disconnect() {
            val account = currentAccount() ?: ""
            CredentialVault.clearCredentials(provider)
            repository.clear(provider)
            ConnectionHealthMonitor.updateHealth(provider, "Disconnected", 0)
            IdentityEvents.emit(IdentityEvents.Event.Disconnect(provider, account))
        }

        override suspend fun refreshAuthentication(): Boolean {
            return TokenManager.refreshIfNeeded(provider)
        }

        override suspend fun isAuthenticated(): Boolean {
            return CredentialVault.getToken(provider).isNotEmpty()
        }

        override suspend fun isOperational(): Boolean {
            return isAuthenticated() && repository.getConnectionState(provider) == ConnectionState.CONNECTED
        }

        override suspend fun currentAccount(): String? {
            return repository.getAccountName(provider)
        }

        override suspend fun grantedPermissions(): List<String> {
            return PermissionManager.getPermissions(provider)
        }

        override suspend fun connectionHealth(): String {
            return if (isOperational()) "Healthy" else "Disconnected"
        }

        override suspend fun lastSynchronization(): Long {
            return repository.getLastSync(provider)
        }

        override suspend fun validateConnection(): ConnectionState {
            if (!isAuthenticated()) {
                repository.saveConnectionState(provider, ConnectionState.NOT_CONFIGURED)
                return ConnectionState.NOT_CONFIGURED
            }
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            return ConnectionState.CONNECTED
        }
    }

    private fun createBinanceConnector() = object : UnifiedConnector {
        override val provider: ProviderType = ProviderType.BINANCE

        override suspend fun connect(params: Map<String, String>): AuthenticationResult = withContext(Dispatchers.IO) {
            val apiKey = params["api_key"] ?: params["apiKey"] ?: return@withContext AuthenticationResult.Failure("API key required.")
            val secretKey = params["secret_key"] ?: params["secret"] ?: params["secretKey"] ?: return@withContext AuthenticationResult.Failure("Secret key required.")
            val env = params["env"] ?: params["selectedEnv"] ?: "Sandbox"
            val mode = params["trading_mode"] ?: params["tradingMode"] ?: params["selectedMode"] ?: "Read-Only"

            CredentialVault.saveToken(provider, apiKey)
            CredentialVault.saveSecret(provider, secretKey)
            
            // Validate connection ping
            var isHealthy = false
            var latency = 999L
            try {
                val url = if (env.equals("Production", ignoreCase = true)) {
                    URL("https://api.binance.com/api/v3/ping")
                } else {
                    URL("https://testnet.binance.vision/api/v3/ping")
                }
                val start = System.currentTimeMillis()
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                val code = connection.responseCode
                latency = System.currentTimeMillis() - start
                if (code == 200) {
                    isHealthy = true
                }
            } catch (e: Exception) {
                Log.e("AccountRegistry", "Binance ping failed: ${e.message}")
            }

            SecureCredentialStore.saveBinanceCredentials(apiKey, secretKey, env, mode)
            repository.saveConnectionState(provider, if (isHealthy) ConnectionState.CONNECTED else ConnectionState.ERROR)
            repository.saveAccountName(provider, "Binance Account (${env})")
            repository.saveLastSync(provider, System.currentTimeMillis())
            ConnectionHealthMonitor.updateHealth(provider, if (isHealthy) "Healthy" else "Unavailable", latency)

            if (isHealthy) {
                IdentityEvents.emit(IdentityEvents.Event.Authentication(provider, "Binance Account"))
                AuthenticationResult.Success("Binance Account (${env})", mapOf("latency" to "$latency ms", "trading_mode" to mode))
            } else {
                IdentityEvents.emit(IdentityEvents.Event.ProviderErrors(provider, "Binance API unreachable or ping failed."))
                AuthenticationResult.Success("Binance Account (${env}) - Configured with Ping Timeout", mapOf("latency" to "Timeout", "trading_mode" to mode))
            }
        }

        override suspend fun disconnect() {
            val account = currentAccount() ?: ""
            CredentialVault.clearCredentials(provider)
            repository.clear(provider)
            ConnectionHealthMonitor.updateHealth(provider, "Disconnected", 0)
            IdentityEvents.emit(IdentityEvents.Event.Disconnect(provider, account))
        }

        override suspend fun refreshAuthentication(): Boolean = true

        override suspend fun isAuthenticated(): Boolean {
            return CredentialVault.getToken(provider).isNotEmpty() && CredentialVault.getSecret(provider).isNotEmpty()
        }

        override suspend fun isOperational(): Boolean {
            return isAuthenticated() && repository.getConnectionState(provider) == ConnectionState.CONNECTED
        }

        override suspend fun currentAccount(): String? {
            return repository.getAccountName(provider)
        }

        override suspend fun grantedPermissions(): List<String> {
            return PermissionManager.getPermissions(provider)
        }

        override suspend fun connectionHealth(): String {
            return if (isOperational()) "Healthy" else "Disconnected"
        }

        override suspend fun lastSynchronization(): Long {
            return repository.getLastSync(provider)
        }

        override suspend fun validateConnection(): ConnectionState {
            if (!isAuthenticated()) {
                repository.saveConnectionState(provider, ConnectionState.NOT_CONFIGURED)
                return ConnectionState.NOT_CONFIGURED
            }
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            return ConnectionState.CONNECTED
        }
    }

    private fun createGitHubConnector() = object : UnifiedConnector {
        override val provider: ProviderType = ProviderType.GITHUB

        override suspend fun connect(params: Map<String, String>): AuthenticationResult = withContext(Dispatchers.IO) {
            val token = params["token"] ?: return@withContext AuthenticationResult.Failure("Token required.")
            val username = params["username"] ?: "GitHub Dev"
            val repo = params["repo"] ?: "MyRepo"
            val branch = params["branch"] ?: "main"

            CredentialVault.saveToken(provider, token)
            SecureCredentialStore.saveGitHubCredentials(token, username, repo, branch)
            
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            repository.saveAccountName(provider, username)
            repository.saveLastSync(provider, System.currentTimeMillis())
            ConnectionHealthMonitor.updateHealth(provider, "Healthy", 95)

            IdentityEvents.emit(IdentityEvents.Event.Authentication(provider, username))
            AuthenticationResult.Success(username, mapOf("repo" to repo, "branch" to branch))
        }

        override suspend fun disconnect() {
            val account = currentAccount() ?: ""
            CredentialVault.clearCredentials(provider)
            repository.clear(provider)
            ConnectionHealthMonitor.updateHealth(provider, "Disconnected", 0)
            IdentityEvents.emit(IdentityEvents.Event.Disconnect(provider, account))
        }

        override suspend fun refreshAuthentication(): Boolean = true

        override suspend fun isAuthenticated(): Boolean {
            return CredentialVault.getToken(provider).isNotEmpty()
        }

        override suspend fun isOperational(): Boolean {
            return isAuthenticated() && repository.getConnectionState(provider) == ConnectionState.CONNECTED
        }

        override suspend fun currentAccount(): String? {
            return repository.getAccountName(provider)
        }

        override suspend fun grantedPermissions(): List<String> {
            return PermissionManager.getPermissions(provider)
        }

        override suspend fun connectionHealth(): String {
            return if (isOperational()) "Healthy" else "Disconnected"
        }

        override suspend fun lastSynchronization(): Long {
            return repository.getLastSync(provider)
        }

        override suspend fun validateConnection(): ConnectionState {
            if (!isAuthenticated()) {
                repository.saveConnectionState(provider, ConnectionState.NOT_CONFIGURED)
                return ConnectionState.NOT_CONFIGURED
            }
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            return ConnectionState.CONNECTED
        }
    }

    private fun createGmailConnector() = object : UnifiedConnector {
        override val provider: ProviderType = ProviderType.GMAIL

        override suspend fun connect(params: Map<String, String>): AuthenticationResult = withContext(Dispatchers.IO) {
            val email = params["email"] ?: "user@gmail.com"
            val token = params["oauth_token"] ?: params["token"] ?: "dummy_gmail_token"
            
            CredentialVault.saveToken(provider, token)
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            repository.saveAccountName(provider, email)
            repository.saveLastSync(provider, System.currentTimeMillis())
            ConnectionHealthMonitor.updateHealth(provider, "Healthy", 50)
            
            IdentityEvents.emit(IdentityEvents.Event.Authentication(provider, email))
            AuthenticationResult.Success(email, mapOf("scopes" to "gmail.readonly"))
        }

        override suspend fun disconnect() {
            val account = currentAccount() ?: ""
            CredentialVault.clearCredentials(provider)
            repository.clear(provider)
            ConnectionHealthMonitor.updateHealth(provider, "Disconnected", 0)
            IdentityEvents.emit(IdentityEvents.Event.Disconnect(provider, account))
        }

        override suspend fun refreshAuthentication(): Boolean {
            return TokenManager.refreshIfNeeded(provider)
        }

        override suspend fun isAuthenticated(): Boolean {
            return CredentialVault.getToken(provider).isNotEmpty()
        }

        override suspend fun isOperational(): Boolean {
            return isAuthenticated() && repository.getConnectionState(provider) == ConnectionState.CONNECTED
        }

        override suspend fun currentAccount(): String? {
            return repository.getAccountName(provider)
        }

        override suspend fun grantedPermissions(): List<String> {
            return PermissionManager.getPermissions(provider)
        }

        override suspend fun connectionHealth(): String {
            return if (isOperational()) "Healthy" else "Disconnected"
        }

        override suspend fun lastSynchronization(): Long {
            return repository.getLastSync(provider)
        }

        override suspend fun validateConnection(): ConnectionState {
            if (!isAuthenticated()) {
                repository.saveConnectionState(provider, ConnectionState.NOT_CONFIGURED)
                return ConnectionState.NOT_CONFIGURED
            }
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            return ConnectionState.CONNECTED
        }
    }

    private fun createGoogleDriveConnector() = object : UnifiedConnector {
        override val provider: ProviderType = ProviderType.GOOGLE_DRIVE

        override suspend fun connect(params: Map<String, String>): AuthenticationResult = withContext(Dispatchers.IO) {
            val email = params["email"] ?: "user@gmail.com"
            val token = params["oauth_token"] ?: params["token"] ?: "dummy_drive_token"
            
            CredentialVault.saveToken(provider, token)
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            repository.saveAccountName(provider, email)
            repository.saveLastSync(provider, System.currentTimeMillis())
            ConnectionHealthMonitor.updateHealth(provider, "Healthy", 55)
            
            IdentityEvents.emit(IdentityEvents.Event.Authentication(provider, email))
            AuthenticationResult.Success(email, mapOf("scopes" to "drive.readonly"))
        }

        override suspend fun disconnect() {
            val account = currentAccount() ?: ""
            CredentialVault.clearCredentials(provider)
            repository.clear(provider)
            ConnectionHealthMonitor.updateHealth(provider, "Disconnected", 0)
            IdentityEvents.emit(IdentityEvents.Event.Disconnect(provider, account))
        }

        override suspend fun refreshAuthentication(): Boolean {
            return TokenManager.refreshIfNeeded(provider)
        }

        override suspend fun isAuthenticated(): Boolean {
            return CredentialVault.getToken(provider).isNotEmpty()
        }

        override suspend fun isOperational(): Boolean {
            return isAuthenticated() && repository.getConnectionState(provider) == ConnectionState.CONNECTED
        }

        override suspend fun currentAccount(): String? {
            return repository.getAccountName(provider)
        }

        override suspend fun grantedPermissions(): List<String> {
            return PermissionManager.getPermissions(provider)
        }

        override suspend fun connectionHealth(): String {
            return if (isOperational()) "Healthy" else "Disconnected"
        }

        override suspend fun lastSynchronization(): Long {
            return repository.getLastSync(provider)
        }

        override suspend fun validateConnection(): ConnectionState {
            if (!isAuthenticated()) {
                repository.saveConnectionState(provider, ConnectionState.NOT_CONFIGURED)
                return ConnectionState.NOT_CONFIGURED
            }
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            return ConnectionState.CONNECTED
        }
    }

    private fun createGoogleCalendarConnector() = object : UnifiedConnector {
        override val provider: ProviderType = ProviderType.GOOGLE_CALENDAR

        override suspend fun connect(params: Map<String, String>): AuthenticationResult = withContext(Dispatchers.IO) {
            val email = params["email"] ?: "user@gmail.com"
            val token = params["oauth_token"] ?: params["token"] ?: "dummy_calendar_token"
            
            CredentialVault.saveToken(provider, token)
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            repository.saveAccountName(provider, email)
            repository.saveLastSync(provider, System.currentTimeMillis())
            ConnectionHealthMonitor.updateHealth(provider, "Healthy", 58)
            
            IdentityEvents.emit(IdentityEvents.Event.Authentication(provider, email))
            AuthenticationResult.Success(email, mapOf("scopes" to "calendar.readonly"))
        }

        override suspend fun disconnect() {
            val account = currentAccount() ?: ""
            CredentialVault.clearCredentials(provider)
            repository.clear(provider)
            ConnectionHealthMonitor.updateHealth(provider, "Disconnected", 0)
            IdentityEvents.emit(IdentityEvents.Event.Disconnect(provider, account))
        }

        override suspend fun refreshAuthentication(): Boolean {
            return TokenManager.refreshIfNeeded(provider)
        }

        override suspend fun isAuthenticated(): Boolean {
            return CredentialVault.getToken(provider).isNotEmpty()
        }

        override suspend fun isOperational(): Boolean {
            return isAuthenticated() && repository.getConnectionState(provider) == ConnectionState.CONNECTED
        }

        override suspend fun currentAccount(): String? {
            return repository.getAccountName(provider)
        }

        override suspend fun grantedPermissions(): List<String> {
            return PermissionManager.getPermissions(provider)
        }

        override suspend fun connectionHealth(): String {
            return if (isOperational()) "Healthy" else "Disconnected"
        }

        override suspend fun lastSynchronization(): Long {
            return repository.getLastSync(provider)
        }

        override suspend fun validateConnection(): ConnectionState {
            if (!isAuthenticated()) {
                repository.saveConnectionState(provider, ConnectionState.NOT_CONFIGURED)
                return ConnectionState.NOT_CONFIGURED
            }
            repository.saveConnectionState(provider, ConnectionState.CONNECTED)
            return ConnectionState.CONNECTED
        }
    }
}
