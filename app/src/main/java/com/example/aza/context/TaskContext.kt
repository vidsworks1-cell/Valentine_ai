package com.example.aza.context

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Lightweight, thread-safe execution state block for cooperative multi-agent execution threads.
 */
data class TaskContext(
    val objectiveId: Int,
    @Volatile var status: String = "INIT",
    val sharedFacts: ConcurrentHashMap<String, Any> = ConcurrentHashMap(),
    val outputs: ConcurrentHashMap<String, String> = ConcurrentHashMap(),
    val metrics: ConcurrentHashMap<String, Double> = ConcurrentHashMap(),
    val pendingEvents: ConcurrentLinkedQueue<AzaEvent> = ConcurrentLinkedQueue(),
    @Volatile var isCancelled: Boolean = false
)
