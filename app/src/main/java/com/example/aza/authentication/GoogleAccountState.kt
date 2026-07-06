package com.example.aza.authentication

data class GoogleAccountState(
    val email: String = "",
    val displayName: String = "",
    val profilePictureUrl: String = "",
    val grantedScopes: List<String> = emptyList(),
    val lastSyncTime: Long = 0L
)
