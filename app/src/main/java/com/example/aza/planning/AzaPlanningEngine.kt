package com.example.aza.planning

import com.example.aza.agent.AgentRegistry
import com.example.aza.agent.AgentResult
import com.example.aza.agent.SkillRegistry
import com.example.aza.context.AzaEventBus
import com.example.aza.execution.AzaCoordinator
import com.example.data.database.AzaAgentTask
import com.example.data.database.ChatMessage
import com.example.data.repository.AppRepository

/**
 * Core strategic planner acting as the main entry point for delegating global goals,
 * adhering strictly to the Autonomous Objective Loop Pipeline.
 */
class AzaPlanningEngine(
    private val repository: AppRepository,
    val eventBus: AzaEventBus = AzaEventBus(),
    val registry: AgentRegistry = AgentRegistry()
) {
    val planner = AzaPlanner(repository)
    val skillRegistry = SkillRegistry()
    val coordinator = AzaCoordinator(repository, skillRegistry, registry, eventBus)

    /**
     * Executes a skill dynamically by its registered name without direct agent coupling.
     */
    suspend fun executeSkill(
        skillName: String,
        task: AzaAgentTask,
        onThoughtLog: suspend (String) -> Unit
    ): AgentResult {
        val result = coordinator.executeSkill(skillName, task, onThoughtLog)
        
        // Finalize task completion parameters
        val finalStatus = if (result.success) "COMPLETED" else "FAILED"
        val completedTask = task.copy(
            status = finalStatus,
            executionLog = result.executionLog,
            performanceImpact = result.performanceImpact,
            retryCount = result.retryCount,
            output = result.performanceImpact
        )
        repository.updateTask(completedTask)

        // Check overall completion of objective
        checkAndUpdateObjectiveCompletion(task.objectiveId, onThoughtLog)

        return result
    }

    /**
     * Executes a planned task through the strict pipelines of the AzaCoordinator.
     */
    suspend fun executeTask(
        task: AzaAgentTask,
        onThoughtLog: suspend (String) -> Unit
    ): AgentResult {
        // 1. Transition status in Database to RUNNING
        val runningTask = task.copy(status = "RUNNING")
        repository.updateTask(runningTask)

        // 2. Delegate execution strictly through the Coordinator (which executes matching skill agent + records telemetry)
        val result = coordinator.executeTask(runningTask, onThoughtLog)

        // 3. Finalize task completion parameters
        val finalStatus = if (result.success) "COMPLETED" else "FAILED"
        val completedTask = runningTask.copy(
            status = finalStatus,
            executionLog = result.executionLog,
            performanceImpact = result.performanceImpact,
            retryCount = result.retryCount,
            output = result.performanceImpact
        )
        repository.updateTask(completedTask)

        if (result.success) {
            onThoughtLog("✅ PIPELINE SUCCESS: Task computed cleanly. Summary: ${result.performanceImpact}")
        } else {
            onThoughtLog("❌ PIPELINE FAULT: Execution failure detected: ${result.errorMessage ?: "Timeout / Validation failure"}")
        }

        // 4. Update parent objective status and verify completion of subsequent loop steps
        checkAndUpdateObjectiveCompletion(task.objectiveId, onThoughtLog)

        return result
    }

    /**
     * Checks if all tasks for the objective are finished and completes the global loop.
     */
    private suspend fun checkAndUpdateObjectiveCompletion(
        objectiveId: Int,
        onThoughtLog: suspend (String) -> Unit
    ) {
        val currentObj = repository.getActiveObjectiveDirect()
        if (currentObj != null && currentObj.id == objectiveId) {
            val allTasks = repository.getTasksForObjectiveDirect(currentObj.id)
            val allDone = allTasks.all { 
                it.status == "COMPLETED" || it.status == "REJECTED" || it.status == "FAILED" 
            }
            
            if (allDone) {
                // High progress completion
                val finishedObj = currentObj.copy(
                    status = "COMPLETED",
                    progress = 1.0f,
                    currentTask = "Completed",
                    completionReason = "All decentralized tasks completed.",
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateObjective(finishedObj)

                onThoughtLog("🏆 OPERATING SYSTEM LOOP COMPLETED: Objective \"${currentObj.objective}\" finished!")
                
                repository.insertChatMessage(
                    ChatMessage(
                        sender = "AZA",
                        text = "🏆 **Global Objective Loop Completed!** 🎉\n\nObjective: *\"${currentObj.objective}\"*\n\nAll specialized agent tasks have successfully executed under Coordinator supervision and registered learning record benchmarks. Review the Mission Control dashboards for real-time telemetry!"
                    )
                )
            }
        }
    }
}

