package com.example.aza.planning

import com.example.data.database.AzaAgentTask
import com.example.data.database.AzaObjective

/**
 * High-level goal planning engine that decomposes raw objective goals into task lists,
 * structures them as a DAG, and ensures they have correct parameters.
 */
object GoalPlanner {

    /**
     * Converts a high level objective into ordered tasks.
     */
    fun planGoal(objective: AzaObjective): List<AzaAgentTask> {
        val blueprints = PlanningRules.getBlueprintsForGoal(objective.title)
        
        val tasks = blueprints.map { blueprint ->
            AzaAgentTask(
                objectiveId = objective.id,
                assignedAgent = blueprint.assignedAgent,
                title = blueprint.title,
                description = blueprint.description,
                priority = blueprint.priority,
                status = "QUEUED", // Initialize in QUEUED state for the Task Queue
                estimatedBudget = blueprint.estimatedBudget,
                requiredSkill = blueprint.requiredSkill,
                dependencies = blueprint.dependencies
            )
        }

        // Validate and sort topological order
        val graph = TaskGraph(tasks)
        val sortedTasks = graph.topologicalSort()
        
        // If sort fails (cycle detected), fallback gracefully to original order
        return sortedTasks ?: tasks
    }
}
