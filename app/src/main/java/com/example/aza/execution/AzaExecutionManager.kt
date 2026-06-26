package com.example.aza.execution

import com.example.aza.agent.AgentResult
import com.example.aza.agent.AutonomousAgent
import com.example.aza.context.AzaEvent
import com.example.aza.context.AzaEventBus
import com.example.aza.context.TaskContext
import com.example.data.database.AzaAgentTask
import com.example.data.repository.AppRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * Governing execution coordinator containing execution limits (timeouts, retries) and failure recovery.
 */
class AzaExecutionManager(
    private val repository: AppRepository,
    private val eventBus: AzaEventBus
) {
    // Thread-safe map storing currently running execution contexts
    private val activeContexts = ConcurrentHashMap<Int, TaskContext>()

    /**
     * Returns or instantiates a safe Context sandbox for a target objective thread.
     */
    fun getOrCreateContext(objectiveId: Int): TaskContext {
        return activeContexts.getOrPut(objectiveId) {
            TaskContext(objectiveId = objectiveId, status = "RUNNING")
        }
    }

    /**
     * Coordinates the safe execution of a task with error isolation, retries, and timeouts.
     */
    suspend fun executeTask(
        task: AzaAgentTask,
        agent: AutonomousAgent,
        onThoughtLog: suspend (String) -> Unit
    ): AgentResult {
        val startedAt = System.currentTimeMillis()
        val context = getOrCreateContext(task.objectiveId)
        
        // Cancellation safeguard
        if (context.isCancelled) {
            onThoughtLog("⚠️ ABORTED: Execution context canceled prior to launch of '${task.title}'")
            val record = com.example.data.database.ExecutionRecord(
                objectiveId = task.objectiveId,
                taskId = task.id,
                agent = agent.displayName,
                skill = task.requiredSkill,
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis(),
                durationMs = 0,
                retries = 0,
                success = false,
                error = "Canceled",
                outputSummary = "Aborted prior to execution",
                input = task.input,
                output = "Aborted prior to execution"
            )
            repository.insertExecutionRecord(record)
            return AgentResult(
                success = false,
                executionLog = "Execution thread cancelled.",
                performanceImpact = "Aborted."
            )
        }

        // Broadcast task began execution
        eventBus.emit(AzaEvent.TaskStarted(task.id, agent.id, task.title))

        val maxRetries = 2
        var attempt = 0
        var lastResult: AgentResult? = null

        // Derive dynamic timeout matching agent's capability specification
        val capability = agent.capabilities().firstOrNull { it.skill == agent.id }
        val timeoutLimitMs = (capability?.estimatedDurationMs ?: 5000L) + 5000L // Padding for network/DB IO

        while (attempt <= maxRetries) {
            attempt++
            try {
                if (attempt > 1) {
                    onThoughtLog("🔄 RETRYING: Attempt $attempt/$maxRetries on '${task.title}' under ${agent.displayName}")
                }

                // Protect against hung operations using modern withTimeout block
                val result = withTimeout(timeoutLimitMs) {
                    agent.execute(task, context, repository)
                }

                lastResult = result

                if (result.success) {
                    val finishedAt = System.currentTimeMillis()
                    // Update cooperative TaskContext with execution parameters
                    context.outputs[task.id.toString()] = result.executionLog
                    context.metrics[task.id.toString()] = 1.0
                    
                    // Drain and emit any pending events buffered in TaskContext during execution
                    var queuedEvent = context.pendingEvents.poll()
                    while (queuedEvent != null) {
                        eventBus.emit(queuedEvent)
                        queuedEvent = context.pendingEvents.poll()
                    }

                    // Save immutable execution record
                    val record = com.example.data.database.ExecutionRecord(
                        objectiveId = task.objectiveId,
                        taskId = task.id,
                        agent = agent.displayName,
                        skill = task.requiredSkill,
                        startedAt = startedAt,
                        finishedAt = finishedAt,
                        durationMs = finishedAt - startedAt,
                        retries = attempt - 1,
                        success = true,
                        error = null,
                        outputSummary = result.performanceImpact,
                        input = task.input,
                        output = result.executionLog
                    )
                    repository.insertExecutionRecord(record)

                    // Announce successful deployment
                    eventBus.emit(AzaEvent.TaskCompleted(task.id, agent.id, result.performanceImpact))
                    return result.copy(retryCount = attempt - 1)
                } else {
                    onThoughtLog("⚠️ ATTEMPT $attempt FAILED for '${agent.displayName}': ${result.errorMessage ?: "Failed trade/proposal logic"}")
                }
            } catch (e: TimeoutCancellationException) {
                onThoughtLog("⏳ TIMEOUT: '${agent.displayName}' canceled (exceeded safe timeout boundary of ${timeoutLimitMs}ms)")
                lastResult = AgentResult(
                    success = false,
                    executionLog = "Operation timed out.",
                    performanceImpact = "Killed safely by manager.",
                    errorMessage = "TimeoutCancellationException",
                    retryCount = attempt - 1
                )
            } catch (e: Exception) {
                onThoughtLog("🚨 RUNTIME CRITICAL ERROR: ${e.localizedMessage}")
                lastResult = AgentResult(
                    success = false,
                    executionLog = "Crashed: ${e.localizedMessage}",
                    performanceImpact = "Recovered safely from exception boundary.",
                    errorMessage = e.localizedMessage,
                    retryCount = attempt - 1
                )
            }

            if (attempt <= maxRetries && !context.isCancelled) {
                delay(1000) // Delay before retrying
            }
        }

        // Exhausted limit
        val finalResult = lastResult ?: AgentResult(
            success = false,
            executionLog = "Execution unsuccessful after retry limits exhausted.",
            performanceImpact = "Halted.",
            retryCount = attempt - 1
        )

        val finishedAt = System.currentTimeMillis()
        val record = com.example.data.database.ExecutionRecord(
            objectiveId = task.objectiveId,
            taskId = task.id,
            agent = agent.displayName,
            skill = task.requiredSkill,
            startedAt = startedAt,
            finishedAt = finishedAt,
            durationMs = finishedAt - startedAt,
            retries = attempt - 1,
            success = false,
            error = finalResult.errorMessage ?: "Max retries reached",
            outputSummary = finalResult.performanceImpact,
            input = task.input,
            output = finalResult.executionLog
        )
        repository.insertExecutionRecord(record)

        eventBus.emit(AzaEvent.TaskFailed(task.id, agent.id, finalResult.errorMessage ?: "Max retries reached"))
        return finalResult.copy(retryCount = attempt - 1)
    }

    /**
     * Activates cancellation flags for an objective execution thread.
     */
    fun cancelObjectiveThread(objectiveId: Int) {
        activeContexts[objectiveId]?.apply {
            isCancelled = true
            status = "CANCELLED"
        }
    }

    /**
     * Clears cached contexts to protect memory limits after objective completes.
     */
    fun removeContext(objectiveId: Int) {
        activeContexts.remove(objectiveId)
    }
}
