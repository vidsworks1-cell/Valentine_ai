package com.example.aza.agent

import com.example.aza.context.AzaEvent
import com.example.aza.context.TaskContext
import com.example.aza.web.WebSurfer
import com.example.data.database.AzaAgentTask
import com.example.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebSurferAgent : AutonomousAgent {
    override val id: String = "web_surfer"
    override val displayName: String = "Aza Web Explorer & Analyst"

    override fun capabilities(): List<AgentCapability> {
        return listOf(
            AgentCapability(
                skill = "web_surfing",
                confidence = 0.95f,
                priority = 9,
                estimatedDurationMs = 1500L
            )
        )
    }

    override suspend fun execute(
        task: AzaAgentTask,
        context: TaskContext,
        repository: AppRepository
    ): AgentResult = withContext(Dispatchers.IO) {
        try {
            val queryOrUrl = task.description
            val isUrl = queryOrUrl.contains("http://") || queryOrUrl.contains("https://") || queryOrUrl.contains(".com") || queryOrUrl.contains(".org") || queryOrUrl.contains(".net")
            
            val logDetails: String
            val metrics: String
            
            if (isUrl) {
                val urlPattern = """(https?://\S+|www\.\S+|\S+\.[a-zA-Z]{2,6}\S*)""".toRegex()
                val match = urlPattern.find(queryOrUrl)
                val targetUrl = match?.value ?: queryOrUrl
                
                val content = WebSurfer.surfUrl(targetUrl)
                val summary = if (content.length > 250) content.substring(0, 250) + "..." else content
                
                logDetails = "Surfed URL successfully: $targetUrl. Scraped ${content.length} characters of clean text content."
                metrics = "Web scrap successful: $summary"
                
                context.sharedFacts["lastSurfedUrl"] = targetUrl
                context.sharedFacts["lastScrapedContent"] = content
            } else {
                val results = WebSurfer.searchWeb(queryOrUrl)
                if (results.isNotEmpty()) {
                    val summaryList = results.joinToString("\n") { "• ${it.title}: ${it.url}\n  ${it.snippet}" }
                    logDetails = "Searched web for: '$queryOrUrl'. Retrieved ${results.size} high-relevance pages."
                    metrics = "Found results:\n$summaryList"
                    
                    context.sharedFacts["lastSearchQuery"] = queryOrUrl
                    context.sharedFacts["lastSearchResults"] = results.map { it.title to it.url }
                } else {
                    logDetails = "Web search initiated for: '$queryOrUrl'. Index returned no matches."
                    metrics = "No live indexing matches found."
                }
            }

            AgentResult(
                success = true,
                executionLog = logDetails,
                performanceImpact = metrics
            )
        } catch (e: Exception) {
            AgentResult(
                success = false,
                executionLog = "Failed to execute Web Surf & Analyze: ${e.localizedMessage}",
                performanceImpact = "Web connection error.",
                errorMessage = e.localizedMessage
            )
        }
    }
}
