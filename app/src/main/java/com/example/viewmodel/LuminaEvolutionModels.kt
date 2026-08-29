package com.example.viewmodel

/**
 * Lumina Autonomous Evolution Data Models (Phase 10)
 */
data class LifelongLearnerAssessment(
    val question: String,
    val answer: String
)

data class LuminaSkillScore(
    val name: String,
    val category: String,
    val score: Int, // 0 - 100
    val weight: Float // importance (0.5 to 1.0)
)

data class LuminaMilestone(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val metric: String = ""
)

data class LuminaEvolutionPlan(
    val currentObjective: String = "",
    val activePhase: String = "IDLE", // IDLE, DISCOVERING, RESEARCHING, TESTING, REFLECTING, EVALUATING, SUCCESS
    val progress: Float = 0f,
    val requiredCapabilities: List<String> = emptyList(),
    val skillGaps: List<String> = emptyList(),
    val learningPlanSteps: List<String> = emptyList(),
    val milestones: List<LuminaMilestone> = emptyList(),
    val completedMilestones: List<String> = emptyList(),
    val knowledgeAcquired: List<String> = emptyList(),
    val performanceChanges: List<String> = emptyList(),
    val remainingWork: List<String> = emptyList(),
    val plannedImprovements: List<String> = emptyList(),
    val evolutionLog: List<String> = emptyList(),
    val predictionAccuracy: Float = 75.0f,
    val sharpeRatio: Float = 1.20f,
    val decisionQuality: Float = 70.0f,
    val consistency: String = "STABLE",
    val safetyApproved: Boolean = true,
    
    // --- Phase 10 Expanded Autonomous Identity & Research Fields ---
    val identityTitle: String = "",
    val identitySkills: List<LuminaSkillScore> = emptyList(),
    val researchQuestions: List<String> = emptyList(),
    val selectedSkillToStudy: String = "",
    val studyGuideContent: String = "",
    val practiceScore: Int = 0,
    val practiceFeedback: String = "",
    val reflectionLog: List<String> = emptyList(),
    val overallLevel: String = "NOVICE", // NOVICE, APPRENTICE, COMPETENT, PROFICIENT, EXPERT, MASTER
    val lifelongAssessment: List<LifelongLearnerAssessment> = emptyList()
)
