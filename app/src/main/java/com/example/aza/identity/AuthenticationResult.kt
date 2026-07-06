package com.example.aza.identity

sealed class AuthenticationResult {
    data class Success(val accountName: String, val details: Map<String, String>) : AuthenticationResult()
    data class Failure(val error: String) : AuthenticationResult()
}
