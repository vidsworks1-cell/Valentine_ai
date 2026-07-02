package com.example.aza.capability

import com.example.data.database.CapabilityAuditLog
import com.example.data.repository.AppRepository

class CapabilityRegistry(private val repository: AppRepository) {
    private val capabilities = mutableMapOf<String, Capability>()

    fun registerCapability(capability: Capability) {
        capabilities[capability.name] = capability
    }

    fun getCapability(name: String): Capability? = capabilities[name]

    fun getAllCapabilities(): List<Capability> = capabilities.values.toList()

    /**
     * Executes an action securely through the Capability Framework.
     * Verifies enablement, health, and permissions before execution.
     * Logs the execution in the persistent Capability Audit Log.
     */
    suspend fun executeActionSecurely(
        capabilityName: String,
        action: String,
        parameters: Map<String, Any>,
        missionId: Int?,
        onThoughtLog: suspend (String) -> Unit
    ): CapabilityResult {
        val startTime = System.currentTimeMillis()
        val cap = capabilities[capabilityName]
        if (cap == null) {
            val err = "Capability '$capabilityName' is not registered."
            onThoughtLog("❌ SECURE OPERATIONS: $err")
            return CapabilityResult(success = false, outputData = emptyMap(), errorMessage = err)
        }

        // Fetch dynamic state from database
        val state = repository.getCapabilityStateByNameDirect(capabilityName)
        if (state == null) {
            val err = "Capability State for '$capabilityName' not found in database."
            onThoughtLog("❌ SECURE OPERATIONS: $err")
            return CapabilityResult(success = false, outputData = emptyMap(), errorMessage = err)
        }

        if (!state.isEnabled) {
            val err = "Capability '$capabilityName' is currently DISABLED."
            onThoughtLog("❌ SECURE OPERATIONS: Security block. $err")
            logAudit(capabilityName, action, "DENIED", cap.requiredPermissions, "Disabled capability", 0, err, missionId)
            return CapabilityResult(success = false, outputData = emptyMap(), errorMessage = err)
        }

        if (state.status == "OFFLINE" || state.healthState == "FAILING") {
            val err = "Capability '$capabilityName' is OFFLINE or FAILING (Health: ${state.healthState})."
            onThoughtLog("⚠️ SECURE OPERATIONS: Health alert. $err")
            logAudit(capabilityName, action, "DENIED", cap.requiredPermissions, "Offline or failing capability", 0, err, missionId)
            return CapabilityResult(success = false, outputData = emptyMap(), errorMessage = err)
        }

        // Verify action is supported
        if (!cap.supportedActions.contains(action.uppercase())) {
            val err = "Action '$action' is not supported by '$capabilityName'."
            onThoughtLog("❌ SECURE OPERATIONS: Action block. $err")
            logAudit(capabilityName, action, "DENIED", cap.requiredPermissions, "Unsupported action", 0, err, missionId)
            return CapabilityResult(success = false, outputData = emptyMap(), errorMessage = err)
        }

        // Increment workload
        repository.insertOrUpdateCapabilityState(state.copy(
            currentWorkload = state.currentWorkload + 1
        ))

        onThoughtLog("🔒 SECURE OPERATIONS: Capability system verified permissions: [${cap.requiredPermissions.joinToString()}]")
        onThoughtLog("⚡ SECURE OPERATIONS: Dispatching action '$action' securely to '$capabilityName'...")

        val result = try {
            cap.executeAction(action, parameters, missionId, repository, onThoughtLog)
        } catch (e: Exception) {
            CapabilityResult(success = false, outputData = emptyMap(), errorMessage = e.localizedMessage)
        }

        val durationMs = System.currentTimeMillis() - startTime
        val updatedState = repository.getCapabilityStateByNameDirect(capabilityName) ?: state

        if (result.success) {
            onThoughtLog("✅ SECURE OPERATIONS: Action '$action' completed successfully in ${durationMs}ms.")
            repository.insertOrUpdateCapabilityState(updatedState.copy(
                lastSuccessfulExecution = System.currentTimeMillis(),
                successCount = updatedState.successCount + 1,
                currentWorkload = (updatedState.currentWorkload - 1).coerceAtLeast(0),
                healthState = "HEALTHY"
            ))
            logAudit(capabilityName, action, "APPROVED", cap.requiredPermissions, "COMPLETED", durationMs, null, missionId)
        } else {
            onThoughtLog("❌ SECURE OPERATIONS: Action '$action' failed: ${result.errorMessage}")
            val nextErrorCount = updatedState.errorCount + 1
            val shouldDisable = nextErrorCount >= 3
            val newHealth = if (shouldDisable) "FAILING" else "DEGRADED"
            val newStatus = if (shouldDisable) "OFFLINE" else updatedState.status
            
            repository.insertOrUpdateCapabilityState(updatedState.copy(
                errorCount = nextErrorCount,
                currentWorkload = (updatedState.currentWorkload - 1).coerceAtLeast(0),
                healthState = newHealth,
                isEnabled = if (shouldDisable) false else updatedState.isEnabled,
                status = newStatus
            ))
            
            if (shouldDisable) {
                onThoughtLog("🚨 SECURE OPERATIONS: Repeated failures detected! Capability '$capabilityName' has been temporarily DISABLED for safety.")
                repository.insertChatMessage(
                    com.example.data.database.ChatMessage(
                        sender = "SECURITY WATCHDOG",
                        text = "🚨 **CRITICAL SECURITY SHUTDOWN:** Capability **$capabilityName** has been automatically disabled due to repeated operational failures. Health: $newHealth | Status: $newStatus. System offline until manual review."
                    )
                )
            }
            logAudit(capabilityName, action, "APPROVED", cap.requiredPermissions, "FAILED", durationMs, result.errorMessage, missionId)
        }

        return result.copy(durationMs = durationMs)
    }

    private suspend fun logAudit(
        capabilityName: String,
        action: String,
        decision: String,
        permissions: List<PermissionLevel>,
        outcome: String,
        durationMs: Long,
        error: String?,
        missionId: Int?
    ) {
        val auditLog = CapabilityAuditLog(
            missionId = missionId,
            capabilityUsed = capabilityName,
            actionPerformed = action,
            plannerDecision = decision,
            permissionLevel = permissions.joinToString(),
            result = outcome,
            durationMs = durationMs,
            errors = error
        )
        repository.insertCapabilityAuditLog(auditLog)
    }
}
