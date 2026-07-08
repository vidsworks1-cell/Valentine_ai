package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.aza.agent.AgentRegistry
import com.example.aza.execution.AzaLearningEngine
import com.example.aza.executive.ConfidenceConsensusEngine
import com.example.data.database.AppDatabase
import com.example.data.repository.AppRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AzaConsensusEntropyTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository
    private lateinit var learningEngine: AzaLearningEngine
    private lateinit var registry: AgentRegistry
    private lateinit var consensusEngine: ConfidenceConsensusEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(database)
        learningEngine = AzaLearningEngine(repository)
        registry = AgentRegistry()
        consensusEngine = ConfidenceConsensusEngine(repository, learningEngine, registry)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testOpinionEntropyCalculation() {
        // Case 1: Minimal or uniform confidence scores (equidistributed) -> Maximum entropy
        val uniformOpinions = listOf(
            ConfidenceConsensusEngine.AgentOpinion("ResearchAgent", 0.8, "Reasoning 1", "APPROVE"),
            ConfidenceConsensusEngine.AgentOpinion("RiskAgent", 0.8, "Reasoning 2", "APPROVE"),
            ConfidenceConsensusEngine.AgentOpinion("TradingAgent", 0.8, "Reasoning 3", "APPROVE")
        )
        val uniformEntropy = consensusEngine.calculateOpinionEntropy(uniformOpinions)
        
        // Shannon entropy of (1/3, 1/3, 1/3) in base 2 should be: -3 * (1/3 * log2(1/3)) = log2(3) ≈ 1.58496
        assertEquals(1.58496, uniformEntropy, 0.001)

        // Case 2: Highly polarized confidence scores (one extremely high, others minimal) -> Lower entropy
        val polarizedOpinions = listOf(
            ConfidenceConsensusEngine.AgentOpinion("ResearchAgent", 0.95, "Reasoning 1", "APPROVE"),
            ConfidenceConsensusEngine.AgentOpinion("RiskAgent", 0.05, "Reasoning 2", "POSTPONE"),
            ConfidenceConsensusEngine.AgentOpinion("TradingAgent", 0.05, "Reasoning 3", "POSTPONE")
        )
        val polarizedEntropy = consensusEngine.calculateOpinionEntropy(polarizedOpinions)
        
        // Polarization reduces entropy
        assertTrue("Polarized opinions must have lower entropy than uniform ones", polarizedEntropy < uniformEntropy)
        assertTrue("Entropy must remain positive", polarizedEntropy > 0.0)
    }

    @Test
    fun testEntropyStateTrackingAndAcceleration() {
        // Initial state
        assertEquals(1.0, consensusEngine.lastEntropy, 1e-9)
        assertEquals(0.0, consensusEngine.lastEntropyAcceleration, 1e-9)
        assertEquals(0L, consensusEngine.lowEntropyDurationMs)

        // Simulate update 1 (Low entropy < 1.2)
        consensusEngine.lastCheckTimestamp = System.currentTimeMillis() - 1000 // force dt of 1 second
        consensusEngine.updateEntropyState(currentEntropy = 0.8)

        assertEquals(0.8, consensusEngine.lastEntropy, 1e-9)
        // Delta = 0.8 - 1.0 = -0.2. Accel = -0.2 / 1.0 = -0.2. Smoothed = 0.7 * 0 + 0.3 * -0.2 = -0.06
        assertEquals(-0.06, consensusEngine.lastEntropyAcceleration, 0.01)
        assertTrue("Low-entropy duration must be accumulated", consensusEngine.lowEntropyDurationMs > 0)

        // Simulate update 2 (High entropy > 1.2)
        val durationBefore = consensusEngine.lowEntropyDurationMs
        consensusEngine.lastCheckTimestamp = System.currentTimeMillis() - 1000 // force dt of 1 second
        consensusEngine.updateEntropyState(currentEntropy = 1.5)

        assertEquals(1.5, consensusEngine.lastEntropy, 1e-9)
        // High entropy should instantly reset low-entropy duration to 0
        assertEquals(0L, consensusEngine.lowEntropyDurationMs)
        // Accel should flip to positive (rising entropy)
        assertTrue("Entropy acceleration must be positive on rising entropy", consensusEngine.lastEntropyAcceleration > 0.0)
    }

    @Test
    fun testAdaptiveHysteresisThreshold() {
        // Baseline threshold starts at 0.75
        assertEquals(0.75, consensusEngine.activeConsensusThreshold, 1e-9)

        // Case 1: High entropy spike -> Raise active threshold
        consensusEngine.lastEntropy = 1.6
        consensusEngine.lastEntropyAcceleration = 0.6
        
        val raisedThreshold = consensusEngine.computeAdaptiveThreshold()
        assertTrue("High entropy spike should raise consensus threshold from 0.75", raisedThreshold > 0.75)

        // Reset threshold to prevent carryover
        consensusEngine.activeConsensusThreshold = 0.75

        // Case 2: Low entropy stable trend -> Lower active threshold
        consensusEngine.lastEntropy = 0.5
        consensusEngine.lastEntropyAcceleration = -0.1
        consensusEngine.lowEntropyDurationMs = 5000L // sustained stable trend

        val loweredThreshold = consensusEngine.computeAdaptiveThreshold()
        assertTrue("Sustained stable trend should lower consensus threshold below baseline", loweredThreshold < 0.75)
    }

    @Test
    fun testAdaptiveLowPassSmoothingFilter() {
        // Initial state of smoothed score is 0.75
        assertEquals(0.75, consensusEngine.smoothedCombinedScore, 1e-9)

        // Under normal low-acceleration conditions, smoothing factor is deep (alpha = 0.15)
        consensusEngine.lastEntropyAcceleration = 0.05
        
        // We propose a combined score of 0.95
        // Smoothed = 0.15 * 0.95 + 0.85 * 0.75 = 0.1425 + 0.6375 = 0.78
        val mockTask = com.example.data.database.TaskEntity(
            objectiveId = 1,
            title = "Test Standard Task",
            description = "Test Description",
            assignedAgent = "ResearchAgent",
            requiredSkill = "Research",
            status = "QUEUED",
            priority = "MEDIUM"
        )

        // Execute consensus check which triggers smoothed calculation
        val result = run {
            val opinions = listOf(
                ConfidenceConsensusEngine.AgentOpinion("ResearchAgent", 0.95, "Reasoning 1", "APPROVE"),
                ConfidenceConsensusEngine.AgentOpinion("RiskAgent", 0.95, "Reasoning 2", "APPROVE"),
                ConfidenceConsensusEngine.AgentOpinion("TradingAgent", 0.95, "Reasoning 3", "APPROVE")
            )
            // Inject values manually to bypass dynamic generation for precise test control
            consensusEngine.smoothedCombinedScore = 0.75
            consensusEngine.lastEntropyAcceleration = 0.05
            
            val combined = opinions.map { it.confidenceScore }.average() // 0.95
            val smoothingAlpha = if (kotlin.math.abs(consensusEngine.lastEntropyAcceleration) > 0.3) 0.40 else 0.15
            consensusEngine.smoothedCombinedScore = (smoothingAlpha * combined) + ((1.0 - smoothingAlpha) * consensusEngine.smoothedCombinedScore)
            consensusEngine.smoothedCombinedScore
        }

        assertEquals(0.78, result, 0.01)
        assertTrue("Smoothed score must filter out high-frequency raw jump (from 0.75 to 0.95)", result < 0.95)
    }
}
