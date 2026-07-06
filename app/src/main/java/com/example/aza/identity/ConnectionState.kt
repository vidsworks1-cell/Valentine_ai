package com.example.aza.identity

enum class ConnectionState {
    NOT_CONFIGURED,
    CONFIGURED,
    AWAITING_AUTHENTICATION,
    AUTHENTICATED,
    CONNECTED,
    SYNCHRONIZING,
    OPERATIONAL,
    ERROR,
    REVOKED
}
