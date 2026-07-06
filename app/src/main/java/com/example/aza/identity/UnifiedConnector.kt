package com.example.aza.identity

interface UnifiedConnector {
    val provider: ProviderType
    suspend fun connect(params: Map<String, String>): AuthenticationResult
    suspend fun disconnect()
    suspend fun refreshAuthentication(): Boolean
    suspend fun isAuthenticated(): Boolean
    suspend fun isOperational(): Boolean
    suspend fun currentAccount(): String?
    suspend fun grantedPermissions(): List<String>
    suspend fun connectionHealth(): String
    suspend fun lastSynchronization(): Long
    suspend fun validateConnection(): ConnectionState
}
