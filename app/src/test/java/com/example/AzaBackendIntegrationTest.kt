package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.aza.agent.*
import com.example.aza.execution.AzaCoordinator
import com.example.aza.execution.AzaLearningEngine
import com.example.aza.planning.AzaPlanningEngine
import com.example.data.database.*
import com.example.data.repository.AppRepository
import com.example.viewmodel.AppViewModel
import kotlinx.coroutines.flow.first
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
class AzaBackendIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository
    private lateinit var planningEngine: AzaPlanningEngine
    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(database)
        planningEngine = AzaPlanningEngine(repository)
        viewModel = AppViewModel(repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `test Skill Registry automatic dynamic registration`() {
        println("--- CHECKPOINT: Skill Registry Discovery ---")
        val skillRegistry = planningEngine.skillRegistry
        val allSkills = skillRegistry.getAllSkills()
        
        // Ensure all primary skills from agents are registered automatically on start-up
        assertFalse(allSkills.isEmpty())
        val skillNames = allSkills.map { it.skillName }
        println("Registered Skills: $skillNames")
        
        assertTrue(skillNames.contains("crypto_trading"))
        assertTrue(skillNames.contains("market_analysis"))
        assertTrue(skillNames.contains("content_creation"))
        assertTrue(skillNames.contains("freelance_writing"))
        assertTrue(skillNames.contains("compliance_audit"))
        
        // Verify runtime discovery and resolving without hardcoded references
        val skill = skillRegistry.getSkill("market_analysis")
        assertNotNull(skill)
        val agentId = skill?.implementingAgentId ?: ""
        assertTrue(agentId == "crypto_trading" || agentId == "trading_agent")
        println("Dynamic resolution: 'market_analysis' skill maps to implementer agent '$agentId' successfully!")
    }

    @Test
    fun `test Objective Lifecycle Transitions and task enrichment`() = runBlocking {
        println("--- CHECKPOINT: Objective Lifecycle Transitions ---")
        
        // 1. NEW status
        val title = "Launch decentralized crypto asset accumulation and audit risks"
        val initialObjective = AzaObjective(
            objective = title,
            title = title,
            status = "NEW",
            progress = 0.0f,
            currentTask = "Initializing Planner",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val objId = repository.insertObjective(initialObjective)
        assertTrue(objId > 0)
        
        var dbObj = repository.getActiveObjectiveDirect()
        assertNotNull(dbObj)
        assertEquals("NEW", dbObj?.status)
        println("[Transition check] Objective created successfully with status: ${dbObj?.status}")

        // 2. PLANNING status
        val planningObj = dbObj!!.copy(status = "PLANNING")
        repository.updateObjective(planningObj)
        dbObj = repository.getActiveObjectiveDirect()
        assertEquals("PLANNING", dbObj?.status)
        println("[Transition check] Objective transitioned to: ${dbObj?.status}")

        // Planner Decomposes Objective into tasks
        val tasks = planningEngine.planner.planObjective(planningObj)
        assertTrue(tasks.isNotEmpty())
        println("Planner decomposed objective into ${tasks.size} tasks:")

        // Enrich tasks with requiredSkill, input, output, dependencies, retryCount
        val enrichedTasks = tasks.mapIndexed { index, task ->
            val skill = planningEngine.skillRegistry.findBestSkillForTask(task.assignedAgent)
            task.copy(
                requiredSkill = skill?.skillName ?: "compliance_audit",
                input = skill?.supportedInputs?.joinToString(",") ?: "",
                output = skill?.supportedOutputs?.joinToString(",") ?: "",
                dependencies = if (index > 0) tasks[index - 1].title else "",
                retryCount = 0,
                status = "PENDING_APPROVAL"
            )
        }
        repository.insertTasks(enrichedTasks)

        val insertedTasks = repository.getTasksForObjectiveDirect(objId.toInt())
        assertEquals(enrichedTasks.size, insertedTasks.size)
        
        // Verify enrichment fields
        for (task in insertedTasks) {
            assertNotNull(task.requiredSkill)
            assertNotNull(task.input)
            assertNotNull(task.output)
            assertEquals(0, task.retryCount)
            println(" -> Enriched Task: '${task.title}' | Required Skill: '${task.requiredSkill}' | Inputs: '${task.input}' | Dependencies: '${task.dependencies}'")
        }

        // 3. READY status transition
        val readyObj = dbObj!!.copy(status = "READY")
        repository.updateObjective(readyObj)
        dbObj = repository.getActiveObjectiveDirect()
        assertEquals("READY", dbObj?.status)
        println("[Transition check] Objective transitioned to: ${dbObj?.status}")
    }

    @Test
    fun `test Coordinator execution and telemetry recording`() = runBlocking {
        println("--- CHECKPOINT: Coordinator Routing and Telemetry Audit ---")
        
        val title = "Execute crypto DCA"
        val objective = AzaObjective(
            objective = title,
            title = title,
            status = "NEW",
            progress = 0.0f,
            currentTask = "Running indicator analysis",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val objId = repository.insertObjective(objective).toInt()
        
        val task = AzaAgentTask(
            objectiveId = objId,
            assignedAgent = "Crypto Trading Agent",
            title = "Verify Market Technical Indicators",
            description = "Run indicators check",
            priority = "HIGH",
            status = "PENDING_APPROVAL",
            requiredSkill = "market_analysis",
            input = "market_trend,balance_usd",
            output = "buy_signal"
        )
        repository.insertTasks(listOf(task))
        val dbTask = repository.getTasksForObjectiveDirect(objId).first()

        // Execute task via Skill Registry resolution
        println("Routing task using Coordinator executeSkill for 'market_analysis'...")
        val result = planningEngine.executeSkill("market_analysis", dbTask) { log ->
            println("    [Log] $log")
        }

        // Verify task output and status updates
        assertTrue(result.success)
        val updatedTask = repository.getTasksForObjectiveDirect(objId).first()
        assertEquals("COMPLETED", updatedTask.status)
        assertEquals(0, updatedTask.retryCount)
        assertFalse(updatedTask.output.isEmpty())
        println("[Verification success] Task status is now COMPLETED, retry count: ${updatedTask.retryCount}, Output size: ${updatedTask.output.length}")

        // Verify parental objective transitioned to COMPLETED/RUNNING
        val finalObj = repository.getObjectiveByIdDirect(objId)
        assertEquals("COMPLETED", finalObj?.status)
        println("[Verification success] Parent Objective status updated to: ${finalObj?.status}")

        // 4. TELEMETRY AND AUDIT TRAIL CHECK
        val executionRecords = database.executionRecordDao().getExecutionRecordsDirect(objId)
        assertFalse(executionRecords.isEmpty())
        val auditRecord = executionRecords.first()
        assertEquals(objId, auditRecord.objectiveId)
        assertEquals(dbTask.id, auditRecord.taskId)
        assertEquals("market_analysis", auditRecord.skill)
        assertTrue(auditRecord.success)
        assertFalse(auditRecord.input.isEmpty())
        assertFalse(auditRecord.output.isEmpty())
        println("[Telemetry Audited] Saved record ID: ${auditRecord.id}, Task ID: ${auditRecord.taskId}, Skill: ${auditRecord.skill}, Input: ${auditRecord.input}, Output: ${auditRecord.output}")

        // 5. LEARNING RECORD CHECK
        val learningRecords = database.azaLearningRecordDao().getRecordsForSkillDirect("market_analysis")
        assertFalse(learningRecords.isEmpty())
        val learning = learningRecords.first()
        assertEquals("market_analysis", learning.skillName)
        assertTrue(learning.success)
        assertEquals(0, learning.retryCount)
        println("[Learning Engine Updated] Recorded execution parameters: Skill='${learning.skillName}', Success=${learning.success}, Latency=${learning.latencyMs}ms")
    }

    private suspend fun yieldAndIdle(times: Int = 10) {
        repeat(times) {
            kotlinx.coroutines.delay(100)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `test ViewModel autonomous pipeline integration`() = runBlocking {
        println("--- CHECKPOINT: ViewModel Autonomous Loop Integration ---")
        
        viewModel.toggleAutonomousMode(true)
        assertTrue(viewModel.isAutonomousMode.value)
        
        viewModel.setHighLevelObjective("Verify Market Technical Indicators and crypto portfolio analysis")
        yieldAndIdle()
        
        // Wait briefly for planning execution coroutines to process
        val dbObj = repository.getActiveObjectiveDirect()
        assertNotNull(dbObj)
        println("Autonomous execution started! Initial Objective status: ${dbObj?.status}")
    }

    @Test
    fun `test ViewModel manual approval and cancellation transitions`() = runBlocking {
        println("--- CHECKPOINT: ViewModel Manual Approval and Cancellation ---")
        
        viewModel.toggleAutonomousMode(false)
        assertFalse(viewModel.isAutonomousMode.value)
        
        viewModel.setHighLevelObjective("Verify Market Technical Indicators and crypto portfolio analysis")
        yieldAndIdle()
        
        val allObjs = database.azaGoalDao().getAllObjectivesFlow().first()
        println("DEBUG: All objectives in DB: ${allObjs.map { it.title + " | Status: " + it.status }}")
        
        var dbObj = repository.getActiveObjectiveDirect()
        assertNotNull(dbObj)
        assertEquals("WAITING_APPROVAL", dbObj?.status)
        println("Approval Mode Active! Objective status successfully holding in: ${dbObj?.status}")

        val tasks = repository.getTasksForObjectiveDirect(dbObj!!.id)
        assertTrue(tasks.isNotEmpty())
        for (task in tasks) {
            assertEquals("PENDING_APPROVAL", task.status)
        }
        
        // Trigger manual cancellation
        viewModel.cancelActiveObjective()
        yieldAndIdle()
        
        val cancelledObj = repository.getObjectiveByIdDirect(dbObj.id)
        assertEquals("CANCELLED", cancelledObj?.status)
        println("Objective cancelled! Status successfully moved to: ${cancelledObj?.status}")
    }
}
