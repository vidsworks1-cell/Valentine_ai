package com.example.aza.planning

import com.example.data.database.AzaAgentTask

/**
 * Validates, sorts, and structures task lists into a Directed Acyclic Graph (DAG).
 */
class TaskGraph(val tasks: List<AzaAgentTask>) {

    // Adjacency list: Task Title -> List of dependent task titles
    private val adjacencyList = mutableMapOf<String, MutableList<String>>()
    private val inDegree = mutableMapOf<String, Int>()

    init {
        // Initialize degrees
        for (task in tasks) {
            inDegree[task.title] = 0
            adjacencyList[task.title] = mutableListOf()
        }

        // Build edges based on dependencies
        for (task in tasks) {
            val deps = task.dependencies.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            for (dep in deps) {
                // If dependency exists in our graph
                if (adjacencyList.containsKey(dep)) {
                    adjacencyList[dep]?.add(task.title)
                    inDegree[task.title] = (inDegree[task.title] ?: 0) + 1
                }
            }
        }
    }

    /**
     * Checks if the task graph has any circular dependencies (is valid DAG).
     */
    fun hasCycles(): Boolean {
        return topologicalSort() == null
    }

    /**
     * Computes the topological order of tasks.
     * Returns null if a circular dependency is detected.
     */
    fun topologicalSort(): List<AzaAgentTask>? {
        val tempInDegree = inDegree.toMutableMap()
        val queue = ArrayDeque<String>()
        val sortedTitles = mutableListOf<String>()

        // Add all nodes with in-degree 0
        for ((title, deg) in tempInDegree) {
            if (deg == 0) {
                queue.add(title)
            }
        }

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            sortedTitles.add(curr)

            adjacencyList[curr]?.forEach { neighbor ->
                val currentDeg = tempInDegree[neighbor] ?: 0
                val nextDeg = currentDeg - 1
                tempInDegree[neighbor] = nextDeg
                if (nextDeg == 0) {
                    queue.add(neighbor)
                }
            }
        }

        // If sorted count matches original count, no cycles exist
        if (sortedTitles.size == tasks.size) {
            val titleToTaskMap = tasks.associateBy { it.title }
            return sortedTitles.mapNotNull { titleToTaskMap[it] }
        }

        return null // Cycle detected!
    }

    /**
     * Finds tasks that are ready to run (all of their dependencies are COMPLETED).
     */
    fun getReadyTasks(completedTaskTitles: Set<String>): List<AzaAgentTask> {
        return tasks.filter { task ->
            if (task.status != "QUEUED" && task.status != "WAITING" && task.status != "PENDING_APPROVAL") return@filter false
            
            val deps = task.dependencies.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            deps.all { completedTaskTitles.contains(it) }
        }
    }
}
