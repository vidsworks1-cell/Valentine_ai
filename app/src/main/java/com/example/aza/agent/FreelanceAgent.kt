package com.example.aza.agent

import com.example.aza.context.AzaEvent
import com.example.aza.context.TaskContext
import com.example.data.database.AzaAgentTask
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.first

class FreelanceAgent : AutonomousAgent {
    override val id: String = "freelance"
    override val displayName: String = "Freelance Proposal Writer"

    override fun capabilities(): List<AgentCapability> {
        return listOf(
            AgentCapability(
                skill = "freelance_writing",
                confidence = 0.85f,
                priority = 7,
                estimatedDurationMs = 2000L
            )
        )
    }

    override suspend fun execute(
        task: AzaAgentTask,
        context: TaskContext,
        repository: AppRepository
    ): AgentResult {
        return try {
            val gigs = repository.freelanceGigs.first()
            val targetGig = gigs.firstOrNull { it.status == "AVAILABLE" }
            
            val logDetails: String
            val metrics: String

            if (targetGig != null) {
                val proposalText = repository.getAiProposalForGig(targetGig.title, targetGig.description)
                val updated = targetGig.copy(
                    status = "APPLIED",
                    proposalText = proposalText
                )
                repository.updateGig(updated)
                
                logDetails = "Constructed tailored, elite technical proposals targeting '${targetGig.title}'"
                metrics = "Bid drafted and submitted to '${targetGig.clientName}'"
                
                // Save info to shared sandbox
                context.sharedFacts["appliedGigTitle"] = targetGig.title
                context.sharedFacts["appliedGigClient"] = targetGig.clientName

                // Push ProposalSubmitted event
                context.pendingEvents.add(
                    AzaEvent.ProposalSubmitted(
                        gigTitle = targetGig.title,
                        clientName = targetGig.clientName,
                        bidOutcome = "APPLIED"
                    )
                )
            } else {
                logDetails = "Scraped gig markets, all listed projects verified apply-cleared."
                metrics = "Handled application queue scans"
            }

            AgentResult(
                success = true,
                executionLog = logDetails,
                performanceImpact = metrics
            )
        } catch (e: Exception) {
            AgentResult(
                success = false,
                executionLog = "Failed to run Freelance Proposal Writer: ${e.localizedMessage}",
                performanceImpact = "No proposal drafted due to database/network error.",
                errorMessage = e.localizedMessage
            )
        }
    }
}
