package com.example.aza.cognition

import com.example.data.database.KnowledgeEntry
import com.example.data.repository.AppRepository

class MemoryRetriever(private val repository: AppRepository) {

    suspend fun retrieveRelevantMemories(text: String): List<String> {
        val lower = text.lowercase()
        val memories = mutableListOf<String>()
        
        val knowledge = repository.getAllKnowledgeDirect()
        val matchedKnowledge = knowledge.filter { entry ->
            entry.title.lowercase().contains(lower) || entry.description.lowercase().contains(lower)
        }
        
        matchedKnowledge.forEach {
            memories.add("Knowledge: ${it.title} - ${it.description}")
        }
        
        return memories.take(3)
    }
}
