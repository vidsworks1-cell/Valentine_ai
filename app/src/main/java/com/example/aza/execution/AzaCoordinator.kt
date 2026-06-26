package com.example.aza.execution

import com.example.aza.agent.AgentRegistry
import com.example.aza.agent.AgentResult
import com.example.aza.agent.SkillMetadata
import com.example.aza.agent.SkillRegistry
import com.example.aza.context.AzaEvent
import com.example.aza.context.AzaEventBus
import com.example.data.database.AzaAgentTask
import com.example.data.database.AzaObjective
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.roundToInt

class AzaCoordinator(
    private val repository: AppRepository,
    private val skillRegistry: SkillRegistry = SkillRegistry(),
    private val agentRegistry: AgentRegistry = AgentRegistry(),
    private val eventBus: AzaEventBus = AzaEventBus(),
    private val executionManager: AzaExecutionManager = AzaExecutionManager(repository, eventBus),
    val learningEngine: AzaLearningEngine = AzaLearningEngine(repository)
) {

    /**
     * Executes a skill dynamically by its registered name without direct coupling to agents.
     */
    suspend fun executeSkill(
        skillName: String,
        task: AzaAgentTask,
        onThoughtLog: suspend (String) -> Unit
    ): AgentResult {
        val startedAt = System.currentTimeMillis()
        onThoughtLog("📡 COORDINATOR [executeSkill]: Dynamic request for skill '$skillName' initiated.")

        val selectedSkill = skillRegistry.getSkill(skillName)
        if (selectedSkill == null) {
            val errLog = "Skill '$skillName' not found in registry."
            onThoughtLog("❌ PIPELINE COMPLIANCE BLOCK: $errLog")
            return AgentResult(success = false, executionLog = errLog, performanceImpact = "Core Aborted.")
        }

        val multiplier = learningEngine.calculateReliabilityMultiplier(skillName)
        val adjustedConfidence = (selectedSkill.confidence * multiplier).coerceIn(0f, 1f)

        val agent = agentRegistry.getAgentById(selectedSkill.implementingAgentId)
        if (agent == null) {
            val errLog = "No implementation agent mapped for skill '$skillName' [implementer: ${selectedSkill.implementingAgentId}]."
            onThoughtLog("❌ PIPELINE FAULT: $errLog")
            return AgentResult(success = false, executionLog = errLog, performanceImpact = "No agent mapped.")
        }

        onThoughtLog("🤖 SKILL REGISTRY: Resolved implementing agent '${agent.displayName}' [${agent.id}] dynamically for skill '$skillName'.")
        
        // Progress update to objective: Set current running task
        updateObjectiveTaskProgress(task.objectiveId, runningTaskName = task.title)

        val runningTask = task.copy(status = "RUNNING", requiredSkill = skillName)
        val result = executionManager.executeTask(runningTask, agent, onThoughtLog)
        val durationMs = System.currentTimeMillis() - startedAt

        // Record outcome in Learning Engine
        learningEngine.recordOutcome(
            skillName = skillName,
            success = result.success,
            latencyMs = durationMs,
            confidence = adjustedConfidence,
            retryCount = result.retryCount,
            userApproval = true,
            outcomeQuality = if (result.success) 1.0 else 0.0
        )

        // Update progress of parent objective
        updateObjectiveProgressPercentage(task.objectiveId, onThoughtLog)

        return result
    }

    /**
     * Executes a task using the full Autonomous Objective Loop Pipeline:
     * Choosing skill -> Invoking agent -> Monitoring -> Retrying -> History -> Learning -> Progress Update
     */
    suspend fun executeTask(
        task: AzaAgentTask,
        onThoughtLog: suspend (String) -> Unit
    ): AgentResult {
        val startedAt = System.currentTimeMillis()
        onThoughtLog("📡 COORDINATOR: Initiating pipeline execution of task node [${task.id}] - '${task.title}'")

        // 1. CHOOSE SKILL (Requests Skill Registry - NOT specific agents)
        val selectedSkill = skillRegistry.findBestSkillForTask(task.assignedAgent)
        if (selectedSkill == null) {
            val errLog = "No suitable skill matches for criteria: ${task.assignedAgent}"
            onThoughtLog("❌ PIPELINE COMPLIANCE BLOCK: $errLog")
            updateTaskFault(task, errLog)
            return AgentResult(success = false, executionLog = errLog, performanceImpact = "Core Aborted.")
        }

        // Dynamically incorporate Learning Engine reliability multiplier to check route rating
        val multiplier = learningEngine.calculateReliabilityMultiplier(selectedSkill.skillName)
        val adjustedConfidence = (selectedSkill.confidence * multiplier).coerceIn(0f, 1f)
        onThoughtLog("🎯 SKILL REGISTRY: Selected dynamic skill [${selectedSkill.skillName}] (Base Conf: ${selectedSkill.confidence}, Adjusted: ${String.format("%.2f", adjustedConfidence)})")

        // 2. INVOKE AGENT (Registry matches the implementation agent for that skill)
        val agent = agentRegistry.getAgentById(selectedSkill.implementingAgentId)
        if (agent == null) {
            val errLog = "No implementation agent found for skill node: ${selectedSkill.implementingAgentId}"
            onThoughtLog("❌ PIPELINE FAULT: $errLog")
            updateTaskFault(task, errLog)
            return AgentResult(success = false, executionLog = errLog, performanceImpact = "No agent mapped.")
        }

        onThoughtLog("🤖 AGENT INVOKED: Routing task payload directly to '${agent.displayName}' [${agent.id}]")

        // Progress update to objective: Set current running task
        updateObjectiveTaskProgress(task.objectiveId, runningTaskName = task.title)

        // 3. MONITOR & RETRY PIPELINE DEPLOYMENT
        val result = executionManager.executeTask(task, agent, onThoughtLog)
        val durationMs = System.currentTimeMillis() - startedAt

        // 4. LEARNING ENGINE REINFORCEMENT UPDATE
        val outcomeQuality = if (result.success) 1.0 else 0.0
        // We track if user approved by looking at the default execution parameters (whether standard confirm logic was bypassed)
        val userApprovalState = task.status == "APPROVED" || task.status == "RUNNING"
        
        learningEngine.recordOutcome(
            skillName = selectedSkill.skillName,
            success = result.success,
            latencyMs = durationMs,
            confidence = adjustedConfidence,
            retryCount = 1, // baseline single run dispatch retry count
            userApproval = userApprovalState,
            outcomeQuality = outcomeQuality
        )
        onThoughtLog("📊 LEARNING SEED: Outcome telemetry stored. Recalculated dynamic health score index.")

        // 5. UPDATE PARENT OBJECTIVE PROGRESS
        updateObjectiveProgressPercentage(task.objectiveId, onThoughtLog)

        return result
    }

    private suspend fun updateTaskFault(task: AzaAgentTask, error: String) {
        val failedTask = task.copy(status = "FAILED", executionLog = error, performanceImpact = "Terminated.")
        repository.updateTask(failedTask)
    }

    /**
     * Updates AzaObjective with the name of the currently active/executing task.
     */
    private suspend fun updateObjectiveTaskProgress(objectiveId: Int, runningTaskName: String) {
        val obj = repository.getActiveObjectiveDirect() ?: return
        if (obj.id == objectiveId) {
            val updated = obj.copy(
                status = "RUNNING",
                currentTask = runningTaskName,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateObjective(updated)
        }
    }

    /**
     * Recalculates exact completed percentage weight across the task queue to update overall objective status.
     */
    private suspend fun updateObjectiveProgressPercentage(
        objectiveId: Int,
        onThoughtLog: suspend (String) -> Unit
    ) {
        val obj = repository.getActiveObjectiveDirect() ?: return
        if (obj.id == objectiveId) {
            val allTasks = repository.getTasksForObjectiveDirect(objectiveId)
            if (allTasks.isEmpty()) return

            val completed = allTasks.count { it.status == "COMPLETED" || it.status == "REJECTED" }
            val total = allTasks.size
            val computedProgress = completed.toFloat() / total.toFloat()

            val allDone = allTasks.all { it.status == "COMPLETED" || it.status == "REJECTED" || it.status == "FAILED" }
            var nextStatus = obj.status
            var reason = obj.completionReason

            if (allDone) {
                val anyFailed = allTasks.any { it.status == "FAILED" }
                nextStatus = if (anyFailed) "FAILED" else "COMPLETED"
                reason = if (anyFailed) "Objective failed due to critical task execution failure." else "All pipeline steps executed successfully under automated compliance rules."
                onThoughtLog("🏆 OBJECTIVE ACCOMPLISHED: Finalized loop checklist with status $nextStatus for ${obj.objective}")
            } else {
                nextStatus = "RUNNING"
            }

            val updatedObj = obj.copy(
                progress = computedProgress,
                status = nextStatus,
                currentTask = if (allDone) "Completed" else obj.currentTask,
                completionReason = reason,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateObjective(updatedObj)
        }
    }
}
