package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.aza.capability.CapabilityRegistry
import com.example.aza.capability.LaptopAgentCapability
import com.example.aza.execution.AzaLearningEngine
import com.example.data.database.AppDatabase
import com.example.data.database.CapabilityStateEntity
import com.example.data.database.LaptopDocumentEntity
import com.example.data.database.SkillEntry
import com.example.data.repository.AppRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaptopAgentLearningTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository
    private lateinit var registry: CapabilityRegistry
    private lateinit var learningEngine: AzaLearningEngine
    private val laptopCap = LaptopAgentCapability()

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            repository = AppRepository(database)
            registry = CapabilityRegistry(repository)
            learningEngine = AzaLearningEngine(repository)

            // Register the Laptop Agent capability
            registry.registerCapability(laptopCap)

            // Pre-populate standard Capability state
            repository.insertOrUpdateCapabilityState(
                CapabilityStateEntity(
                    name = laptopCap.name,
                    description = laptopCap.description,
                    supportedActions = laptopCap.supportedActions.joinToString(","),
                    requiredPermissions = laptopCap.requiredPermissions.joinToString(","),
                    isEnabled = true,
                    status = "ONLINE",
                    healthState = "HEALTHY"
                )
            )

            // Insert mock skills so we can verify learning weight adaptation
            repository.insertSkill(
                SkillEntry(
                    name = "Detect Range",
                    description = "Detect sideways market ranges.",
                    confidence = 0.50,
                    successRate = 60.0,
                    lastUpdated = System.currentTimeMillis(),
                    associatedRegimes = "SIDEWAYS, RANGE"
                )
            )
            repository.insertSkill(
                SkillEntry(
                    name = "Detect Reversal",
                    description = "Detect trend reversals.",
                    confidence = 0.40,
                    successRate = 50.0,
                    lastUpdated = System.currentTimeMillis(),
                    associatedRegimes = "TRENDING, VOLATILE"
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testLaptopCapability_MockActions() = runBlocking {
        println("--- SCENARIO 1: Basic Laptop Agent Action Verification ---")

        // 1. PING_HOST
        val pingResult = registry.executeActionSecurely(
            laptopCap.name, "PING_HOST", emptyMap(), null
        ) { println("LOG: $it") }
        assertTrue("Ping must succeed in Mock Mode", pingResult.success)
        assertEquals("CONNECTED", pingResult.outputData["status"])

        // 2. RETRIEVE_DOCUMENTS
        val retrieveResult = registry.executeActionSecurely(
            laptopCap.name, "RETRIEVE_DOCUMENTS", emptyMap(), null
        ) { println("LOG: $it") }
        assertTrue("Retrieve documents must succeed in Mock Mode", retrieveResult.success)
        assertEquals(3, retrieveResult.outputData["retrieved_count"])

        // Verify documents are inserted in database
        val docs = repository.getAllLaptopDocumentsDirect()
        assertEquals(3, docs.size)
        assertTrue(docs.any { it.title.contains("Quantitative Trading") })
        assertTrue(docs.any { it.title.contains("YouTube") })
        assertTrue(docs.any { it.title.contains("Freelance") })

        // 3. GET_SYNC_STATUS
        val statusResult = registry.executeActionSecurely(
            laptopCap.name, "GET_SYNC_STATUS", emptyMap(), null
        ) { println("LOG: $it") }
        assertTrue("Get Sync Status must succeed", statusResult.success)
        assertEquals("SUCCESS", statusResult.outputData["status"])
    }

    @Test
    fun testLearningEngine_QuantPreferenceLearning() = runBlocking {
        println("--- SCENARIO 2: Learning from Quantitative Research Document ---")

        val doc = LaptopDocumentEntity(
            id = "q1",
            title = "Alpha Research - Quant Mean-Reversion.pdf",
            content = "This document investigates using GARCH volatilities to calibrate mean-reversion trading bounds. It shows superior Sharpe ratios in sideways ranges.",
            category = "RESEARCH",
            sizeBytes = 1000,
            lastModified = System.currentTimeMillis(),
            retrievedAt = System.currentTimeMillis()
        )

        // Capture skill levels prior to learning
        val rangeSkillBefore = repository.getAllSkillsDirect().find { it.name == "Detect Range" }
        assertNotNull(rangeSkillBefore)
        val initialConfidence = rangeSkillBefore!!.confidence

        // Execute learning
        learningEngine.learnFromLaptopHabits(listOf(doc))

        // Verify weight adjustment
        val rangeSkillAfter = repository.getAllSkillsDirect().find { it.name == "Detect Range" }
        assertNotNull(rangeSkillAfter)
        assertTrue(
            "Confidence weight should be elevated post-quant learning",
            rangeSkillAfter!!.confidence > initialConfidence
        )
        assertEquals(initialConfidence + 0.15, rangeSkillAfter.confidence, 0.001)

        // Verify Knowledge Base Adaptation
        val knowledge = repository.getAllKnowledgeDirect()
        val adaptKnowledge = knowledge.find { it.title == "Adaptation: Quantitative Mean Reversion Bias" }
        assertNotNull("Knowledge base should incorporate the GARCH preference adaptation", adaptKnowledge)
        assertEquals("Laptop Agent", adaptKnowledge!!.category)
        assertTrue(adaptKnowledge.evidence.contains("GARCH"))
    }

    @Test
    fun testLearningEngine_YouTubeScheduleLearning() = runBlocking {
        println("--- SCENARIO 3: Learning from YouTube Content Schedule Document ---")

        val doc = LaptopDocumentEntity(
            id = "yt1",
            title = "Video Content Blueprint.docx",
            content = "This is our blueprint for upcoming youtube releases. We schedule video creation for peak hours of user availability.",
            category = "CONTENT",
            sizeBytes = 1500,
            lastModified = System.currentTimeMillis(),
            retrievedAt = System.currentTimeMillis()
        )

        learningEngine.learnFromLaptopHabits(listOf(doc))

        // Verify Knowledge Base Adaptation
        val knowledge = repository.getAllKnowledgeDirect()
        val ytAdapt = knowledge.find { it.title == "Adaptation: Video Production & Content Scheduling" }
        assertNotNull("Knowledge base should adapt to YouTube content workflows", ytAdapt)
        assertTrue(ytAdapt!!.description.contains("content drafting priority"))
    }

    @Test
    fun testLearningEngine_ApiPerformanceLearning() = runBlocking {
        println("--- SCENARIO 4: Learning from Freelance API Gateway Spec ---")

        val doc = LaptopDocumentEntity(
            id = "api1",
            title = "Freelance Project Spec.md",
            content = "System specs require building a high performance Ktor gateway with JWT protection and strict rate limits.",
            category = "PROJECTS",
            sizeBytes = 800,
            lastModified = System.currentTimeMillis(),
            retrievedAt = System.currentTimeMillis()
        )

        learningEngine.learnFromLaptopHabits(listOf(doc))

        // Verify Knowledge Base Adaptation
        val knowledge = repository.getAllKnowledgeDirect()
        val apiAdapt = knowledge.find { it.title == "Adaptation: API Performance & Throughput Constraints" }
        assertNotNull("Knowledge base should adapt to freelance API specifications", apiAdapt)
        assertTrue(apiAdapt!!.evidence.contains("Ktor"))
    }

    @Test
    fun testBidirectionalPush_ExchangeData() = runBlocking {
        println("--- SCENARIO 5: Bidirectional Data Push / Exchange Data ---")

        // Populate a chat message and portfolio assets to be telemetry-synced
        // (these are grabbed during EXCHANGE_DATA)
        val pushResult = registry.executeActionSecurely(
            laptopCap.name, "EXCHANGE_DATA", emptyMap(), null
        ) { println("LOG: $it") }

        assertTrue("Exchange Data action should succeed", pushResult.success)
        assertEquals("Laptop-Mock-Host", pushResult.outputData["deviceName"])

        // Check audit trail
        val exchangeLogs = repository.getAllLaptopExchangeRecordsDirect()
        assertTrue("There must be a PUSH exchange record in the audit logs", exchangeLogs.any { it.direction == "PUSH" })
        val lastPush = exchangeLogs.find { it.direction == "PUSH" }
        assertNotNull(lastPush)
        assertEquals("SUCCESS", lastPush!!.status)
        assertTrue(lastPush.message.contains("Pushed telemetry"))
    }
}
