package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.KnowledgeEntry
import com.example.data.repository.AppRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LuminaLifelongLearningTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testChatRegex_MatchesBecomeObjectivePatterns() {
        val pattern = """(?i)(?:become\s+a\s+|become\s+an\s+|become\s+|be\s+a\s+|be\s+an\s+|be\s+|improve\s+your\s+|learn\s+to\s+be\s+a\s+|learn\s+to\s+be\s+an\s+|learn\s+to\s+be\s+|learn\s+to\s+|evolve\s+into\s+a\s+|evolve\s+into\s+an\s+|evolve\s+into\s+|evolve\s+|autonomous\s+evolution\s+|start\s+evolution\s+|want\s+you\s+(?:from\s+now\s+on\s+)?(?:to\s+)?(?:be\s+a\s+|be\s+an\s+|be\s+|become\s+a\s+|become\s+an\s+|become\s+))(.+)""".toRegex()

        val queries = listOf(
            "become a master trader" to "master trader",
            "become an expert strategist" to "expert strategist",
            "become machine learning engineer" to "machine learning engineer",
            "I want you from now on to be a master trader" to "master trader",
            "I want you to become a Quant Researcher" to "Quant Researcher",
            "evolve into an autonomous risk manager." to "autonomous risk manager.",
            "learn to be exceptional quantitative analyst" to "exceptional quantitative analyst",
            "be a data scientist" to "data scientist"
        )

        for ((input, expected) in queries) {
            val match = pattern.find(input)
            assertNotNull("Pattern should match: '$input'", match)
            val captured = match!!.groupValues[1].trim().replace(Regex("[.?]+$"), "")
            assertEquals("Captured objective should match expected for input: '$input'", expected.replace(Regex("[.?]+$"), ""), captured)
        }
    }

    @Test
    fun testKnowledgeCompetencies_PersistenceAndDaoLifecycle() = runBlocking {
        val objective = "master trader"
        val skillName = "Advanced Portfolio Sizing"
        val category = "Portfolio Management"
        val score = 85
        val weight = 0.95f

        // Construct standard Lumina Competency entry exactly as formatted in the VM
        val kbEntry = KnowledgeEntry(
            id = 0,
            title = "skill:${objective.lowercase()}:${skillName.lowercase()}",
            category = "Lumina Competency",
            description = category,
            evidence = "$score|$weight",
            confidenceScore = weight.toDouble(),
            dateCreated = System.currentTimeMillis(),
            isConfirmed = true,
            timesConfirmed = 1
        )

        // Insert entry
        database.knowledgeDao().insertKnowledge(kbEntry)

        // Retrieve entries
        val allKnowledge = database.knowledgeDao().getAllKnowledgeDirect()
        val filtered = allKnowledge.filter {
            it.category == "Lumina Competency" &&
            it.title.startsWith("skill:${objective.lowercase()}:")
        }

        assertEquals(1, filtered.size)
        val retrieved = filtered.first()

        assertEquals("skill:master trader:advanced portfolio sizing", retrieved.title)
        assertEquals("Portfolio Management", retrieved.description)

        // Parse evidence back exactly as VM does
        val parts = retrieved.evidence.split("|")
        val parsedScore = parts.getOrNull(0)?.toIntOrNull() ?: 50
        val parsedWeight = parts.getOrNull(1)?.toFloatOrNull() ?: 1.0f

        assertEquals(85, parsedScore)
        assertEquals(0.95f, parsedWeight, 0.001f)
    }

    @Test
    fun testUserFeedbackCalibration_SavesCorrectFeedbackToDatabase() = runBlocking {
        // Construct User Feedback Entry exactly as formatted in the VM
        val feedbackEntry = KnowledgeEntry(
            id = 0,
            title = "Lumina Skill Calibration: Advanced Portfolio Sizing",
            category = "User Feedback",
            description = "User manually adjusted score from 60% to 95%. Comment: Recalibrated for aggressive risk management.",
            evidence = "Objective: master trader",
            confidenceScore = 1.0,
            dateCreated = System.currentTimeMillis(),
            isConfirmed = true,
            timesConfirmed = 1
        )

        // Insert entry
        database.knowledgeDao().insertKnowledge(feedbackEntry)

        val allKnowledge = database.knowledgeDao().getAllKnowledgeDirect()
        val retrieved = allKnowledge.find { it.title.startsWith("Lumina Skill Calibration:") }

        assertNotNull(retrieved)
        assertEquals("User Feedback", retrieved!!.category)
        assertTrue(retrieved.description.contains("manually adjusted"))
        assertTrue(retrieved.description.contains("Recalibrated for aggressive risk management"))
    }
}
