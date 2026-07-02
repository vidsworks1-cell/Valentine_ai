package com.example.aza.capability

import com.example.data.repository.AppRepository

enum class PermissionLevel {
    READ, WRITE, EXECUTE, FINANCIAL, PUBLISH, HIGH_RISK
}

interface Capability {
    val name: String
    val description: String
    val supportedActions: List<String>
    val requiredPermissions: List<PermissionLevel>
    
    suspend fun executeAction(
        action: String, 
        parameters: Map<String, Any>, 
        missionId: Int?, 
        repository: AppRepository,
        onThoughtLog: suspend (String) -> Unit
    ): CapabilityResult
}

data class CapabilityResult(
    val success: Boolean,
    val outputData: Map<String, Any>,
    val errorMessage: String? = null,
    val durationMs: Long = 0
)
