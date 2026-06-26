package com.example.aza.planning

import com.example.data.database.AzaAgentTask
import com.example.data.database.AzaObjective
import com.example.data.repository.AppRepository

/**
 * Strict Planning module of the Autonomous Objective Loop.
 * Its sole responsibility is strategic planning (decomposition).
 * It is explicitly forbidden from executing tasks.
 */
class AzaPlanner(private val repository: AppRepository) {

    /**
     * Strategic decomposition pipeline.
     * Takes a persistent objective and expands it into atomic, sequential task nodes.
     */
    suspend fun planObjective(objective: AzaObjective): List<AzaAgentTask> {
        // Delegate decomposition strictly via repository/API, or use tailored blueprints
        return repository.decomposeObjectiveToTasks(objective)
    }
}
