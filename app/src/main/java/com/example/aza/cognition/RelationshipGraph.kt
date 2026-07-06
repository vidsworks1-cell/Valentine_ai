package com.example.aza.cognition

import com.example.data.database.GraphEdgeEntity
import com.example.data.database.GraphNodeEntity
import com.example.data.repository.AppRepository

class RelationshipGraph(private val repository: AppRepository) {

    suspend fun addNode(type: String, label: String, value: String): Long {
        val existing = repository.getGraphNodeByLabelAndTypeDirect(label, type)
        if (existing != null) return existing.id
        val node = GraphNodeEntity(type = type, label = label, value = value)
        return repository.insertGraphNode(node)
    }

    suspend fun addEdge(sourceId: Long, targetId: Long, relationshipType: String, weight: Double = 1.0, details: String = ""): Long {
        val edge = GraphEdgeEntity(sourceId = sourceId, targetId = targetId, relationshipType = relationshipType, weight = weight, details = details)
        return repository.insertGraphEdge(edge)
    }

    suspend fun getConnectedNodes(nodeId: Long): List<GraphNodeEntity> {
        val edges = repository.getAllGraphEdgesDirect()
        val targetIds = edges.filter { it.sourceId == nodeId }.map { it.targetId }
        val sourceIds = edges.filter { it.targetId == nodeId }.map { it.sourceId }
        val allIds = (targetIds + sourceIds).distinct()
        
        return repository.getAllGraphNodesDirect().filter { allIds.contains(it.id) }
    }

    suspend fun getFullTrace(): List<String> {
        val nodes = repository.getAllGraphNodesDirect()
        val edges = repository.getAllGraphEdgesDirect()
        val nodeMap = nodes.associateBy { it.id }
        
        return edges.mapNotNull { edge ->
            val src = nodeMap[edge.sourceId]
            val tgt = nodeMap[edge.targetId]
            if (src != null && tgt != null) {
                "[${src.type}] '${src.label}' --(${edge.relationshipType})--> [${tgt.type}] '${tgt.label}'"
            } else null
        }
    }
}
