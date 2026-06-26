package com.example.aza.context

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event taxonomy representing real actions occurring in Aza's autonomous container.
 */
sealed interface AzaEvent {
    data class TaskStarted(val taskId: Int, val agentId: String, val title: String) : AzaEvent
    data class TaskCompleted(val taskId: Int, val agentId: String, val performanceImpact: String) : AzaEvent
    data class TaskFailed(val taskId: Int, val agentId: String, val error: String) : AzaEvent
    data class TradeClosed(val symbol: String, val amount: Double, val profitUsd: Double, val totalBalanceUsd: Double) : AzaEvent
    data class ContentCreated(val title: String, val platform: String, val status: String) : AzaEvent
    data class ProposalSubmitted(val gigTitle: String, val clientName: String, val bidOutcome: String) : AzaEvent
    data class AuditCompleted(val auditedAmount: Double, val statusLog: String) : AzaEvent
}

/**
 * Central event broker allowing agents to announce completion, yield signals, and update metrics asynchronously.
 */
class AzaEventBus {
    private val _events = MutableSharedFlow<AzaEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<AzaEvent> = _events.asSharedFlow()

    suspend fun emit(event: AzaEvent) {
        _events.emit(event)
    }
}
