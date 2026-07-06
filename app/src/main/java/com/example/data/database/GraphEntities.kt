package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "relationship_graph_nodes")
data class GraphNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // e.g., "USER", "GOAL", "PROJECT", "STRATEGY", "LESSON", "TRADE", "VIDEO", "CLIENT", "KNOWLEDGE", "CAPABILITY", "AGENT", "MISSION_OUTCOME"
    val label: String, // The name or short title
    val value: String, // Additional details, metrics, or description
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "relationship_graph_edges",
    indices = [
        Index("sourceId"),
        Index("targetId")
    ]
)
data class GraphEdgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val targetId: Long,
    val relationshipType: String, // e.g., "LEADS_TO", "DEPENDS_ON", "CONFLICTS_WITH", "SOLVED_BY", "INFLUENCED_BY", "ASSOCIATED_WITH", "PREVENTS"
    val weight: Double = 1.0,
    val details: String = ""
)

@Dao
interface GraphDao {
    @Query("SELECT * FROM relationship_graph_nodes")
    suspend fun getAllNodes(): List<GraphNodeEntity>

    @Query("SELECT * FROM relationship_graph_nodes")
    fun getAllNodesFlow(): Flow<List<GraphNodeEntity>>

    @Query("SELECT * FROM relationship_graph_nodes WHERE id = :id LIMIT 1")
    suspend fun getNodeById(id: Long): GraphNodeEntity?

    @Query("SELECT * FROM relationship_graph_nodes WHERE label = :label AND type = :type LIMIT 1")
    suspend fun getNodeByLabelAndType(label: String, type: String): GraphNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: GraphNodeEntity): Long

    @Query("SELECT * FROM relationship_graph_edges")
    suspend fun getAllEdges(): List<GraphEdgeEntity>

    @Query("SELECT * FROM relationship_graph_edges")
    fun getAllEdgesFlow(): Flow<List<GraphEdgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdge(edge: GraphEdgeEntity): Long

    @Query("DELETE FROM relationship_graph_nodes")
    suspend fun clearNodes()

    @Query("DELETE FROM relationship_graph_edges")
    suspend fun clearEdges()
}
