package com.example.aza.authentication

enum class OAuthState {
    NOT_CONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    TOKEN_REFRESHING,
    ERROR,
    REVOKED
}
