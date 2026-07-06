package com.example.aza.cognition

import com.example.data.database.AzaMission
import com.example.data.database.PortfolioAsset

enum class CapabilityStatus {
    NOT_IMPLEMENTED,
    IMPLEMENTED,
    CONFIGURED,
    AUTHENTICATED,
    CONNECTED,
    OPERATIONAL,
    SIMULATION,
    DISABLED,
    UNKNOWN
}

data class CapabilityState(
    val name: String,
    val status: CapabilityStatus,
    val supportedActions: List<String>
)

data class WorldModel(
    val portfolioCash: Double = 0.0,
    val holdings: List<PortfolioAsset> = emptyList(),
    val activeMissions: List<AzaMission> = emptyList(),
    val contentQueueCount: Int = 0,
    val openGigsCount: Int = 0,
    val capabilityStates: Map<String, CapabilityState> = emptyMap()
)
