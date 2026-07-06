package com.example.aza.identity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

object ConnectionHealthMonitor {
    private val healthStates = mutableMapOf<ProviderType, String>()
    private val latencies = mutableMapOf<ProviderType, Long>()

    init {
        ProviderType.values().forEach {
            healthStates[it] = "Disconnected"
            latencies[it] = 0L
        }
    }

    fun updateHealth(provider: ProviderType, status: String, latencyMs: Long) {
        healthStates[provider] = status
        latencies[provider] = latencyMs
    }

    fun getHealth(provider: ProviderType): String {
        return healthStates[provider] ?: "Disconnected"
    }

    fun getLatency(provider: ProviderType): Long {
        return latencies[provider] ?: 0L
    }

    fun simulateActivePings(): Map<ProviderType, Long> {
        ProviderType.values().forEach {
            if (healthStates[it] != "Disconnected") {
                latencies[it] = Random.nextLong(15, 120)
            }
        }
        return latencies
    }
}
