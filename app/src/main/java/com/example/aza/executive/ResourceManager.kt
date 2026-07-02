package com.example.aza.executive

import com.example.data.database.TaskEntity
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class ResourceManager(private val repository: AppRepository) {
    data class ResourceMetrics(
        val batteryLevel: Int = 85,       // %
        val cpuUsage: Double = 22.4,      // %
        val memoryUsageMb: Double = 145.0, // MB
        val networkAvailable: Boolean = true,
        val apiUsageCount: Int = 12,       // Current rate-limit count
        val isPowerSavingMode: Boolean = false
    )

    private val _currentMetrics = MutableStateFlow(ResourceMetrics())
    val currentMetrics = _currentMetrics.asStateFlow()

    // Interactive adjustment methods for demo / dashboard
    fun updateBatteryLevel(level: Int) {
        val capped = level.coerceIn(0, 100)
        val powerSaving = capped < 30
        _currentMetrics.value = _currentMetrics.value.copy(
            batteryLevel = capped,
            isPowerSavingMode = powerSaving
        )
    }

    fun updateCpuUsage(usage: Double) {
        _currentMetrics.value = _currentMetrics.value.copy(cpuUsage = usage.coerceIn(0.0, 100.0))
    }

    fun updateMemoryUsage(mb: Double) {
        _currentMetrics.value = _currentMetrics.value.copy(memoryUsageMb = mb.coerceAtLeast(10.0))
    }

    fun updateNetwork(available: Boolean) {
        _currentMetrics.value = _currentMetrics.value.copy(networkAvailable = available)
    }

    fun incrementApiUsage() {
        _currentMetrics.value = _currentMetrics.value.copy(apiUsageCount = _currentMetrics.value.apiUsageCount + 1)
    }

    fun resetApiUsage() {
        _currentMetrics.value = _currentMetrics.value.copy(apiUsageCount = 0)
    }

    /**
     * Determines whether background tasks should be throttled or paused based on active resource levels.
     */
    fun shouldThrottleTask(task: TaskEntity): Boolean {
        val metrics = _currentMetrics.value
        
        // 1. If battery is extremely low (< 15%), halt ALL task execution
        if (metrics.batteryLevel < 15) {
            return true
        }

        // 2. If battery is low (< 30%), throttle non-critical tasks (priority LOW or MEDIUM)
        if (metrics.batteryLevel < 30) {
            val taskName = task.title.lowercase(Locale.US)
            val isCritical = task.priority == "HIGH" || taskName.contains("risk") || taskName.contains("trade")
            if (!isCritical) {
                return true // Throttle/pause low-essential work
            }
        }

        // 3. If CPU is abnormally high (> 85%), pause non-essential work
        if (metrics.cpuUsage > 85.0) {
            if (task.priority != "HIGH") {
                return true
            }
        }

        // 4. Rate limits or network disconnection halts everything
        if (!metrics.networkAvailable || metrics.apiUsageCount >= 30) {
            return true
        }

        return false
    }

    /**
     * Periodically reviews paused or throttled tasks, and automatically resumes them if resources have recovered.
     */
    suspend fun autoResumeSuspendedTasks(onLog: (String) -> Unit) {
        val metrics = _currentMetrics.value
        if (metrics.batteryLevel >= 30 && metrics.cpuUsage <= 80.0 && metrics.networkAvailable) {
            val throttledTasks = repository.getQueueTasksByStatus("BLOCKED")
            if (throttledTasks.isNotEmpty()) {
                onLog("⚡ RESOURCE MANAGER: Resource levels recovered (Battery: ${metrics.batteryLevel}%, CPU: ${String.format(Locale.US, "%.1f", metrics.cpuUsage)}%). Automatically resuming ${throttledTasks.size} throttled/blocked tasks.")
                for (task in throttledTasks) {
                    repository.updateQueueTask(task.copy(status = "QUEUED", updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }
}
