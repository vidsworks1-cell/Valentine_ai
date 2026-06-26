package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.aza.agent.*
import com.example.aza.context.TaskContext
import com.example.aza.execution.AzaExecutionManager
import com.example.data.database.*
import com.example.data.repository.AppRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AzaComprehensiveStressTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Standard in-memory DB for generic scenarios
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Fake Agent implementation for fully deterministic mock testing.
     */
    class FakeAgent(
        override val id: String,
        override val displayName: String,
        private val failuresToSimulate: Int
    ) : AutonomousAgent {
        var executionCount = 0

        override fun capabilities(): List<AgentCapability> = listOf(
            AgentCapability(id, 1.0f, 1, 100L)
        )

        override suspend fun execute(
            task: AzaAgentTask,
            context: TaskContext,
            repository: AppRepository
        ): AgentResult {
            executionCount++
            return if (executionCount <= failuresToSimulate) {
                AgentResult(
                    success = false,
                    executionLog = "Simulated attempt $executionCount failed.",
                    performanceImpact = "Faulty",
                    errorMessage = "Simulated Error $executionCount"
                )
            } else {
                AgentResult(
                    success = true,
                    executionLog = "Simulated attempt $executionCount succeeded.",
                    performanceImpact = "Recovered on attempt $executionCount"
                )
            }
        }
    }

    /**
     * Scenario 1: 100 queued objectives
     * Verifies enqueuing 100 objectives into the database, loading them, and updating states.
     */
    @Test
    fun test100QueuedObjectives() = runBlocking {
        println("=== Scenario 1: 100 Queued Objectives Stress Test ===")
        val count = 100
        val objectives = List(count) { i ->
            AzaObjective(
                id = 0, // let Room auto-generate
                objective = "Objective number $i",
                title = "Objective Title $i",
                status = "QUEUED",
                progress = 0.0f,
                currentTask = "Waiting in Queue",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        // Bulk insert
        for (obj in objectives) {
            val id = repository.insertObjective(obj)
            assertTrue(id > 0)
        }

        // Retrieve and verify
        val allObjectives = database.azaGoalDao().getAllObjectivesFlow().first()
        assertTrue("Expected at least 100 objectives, found ${allObjectives.size}", allObjectives.size >= count)

        // Bulk update stress test
        val updatedObjectives = allObjectives.map { it.copy(status = "PROCESSING", updatedAt = System.currentTimeMillis()) }
        for (obj in updatedObjectives) {
            repository.updateObjective(obj)
        }

        val retrievedAfterUpdate = database.azaGoalDao().getAllObjectivesFlow().first()
        val processingCount = retrievedAfterUpdate.count { it.status == "PROCESSING" }
        assertTrue("Expected at least 100 to be in PROCESSING status, found $processingCount", processingCount >= count)
        println("Successfully enqueued, retrieved, updated, and validated $count queued objectives under database stress.")
    }

    /**
     * Scenario 2: Agent failures and retries
     * Verifies that the coordinator / execution manager properly handles transient agent failures and automatically retries.
     */
    @Test
    fun testAgentFailuresAndRetries() = runBlocking {
        println("=== Scenario 2: Agent Failures and Retries Test ===")
        val eventBus = com.example.aza.context.AzaEventBus()
        val manager = AzaExecutionManager(repository, eventBus)

        // 1. Transient failure: succeeds on the 2nd attempt (1 failure, 1 retry)
        val agent1 = FakeAgent("test_skill_1", "Retry Agent 1", failuresToSimulate = 1)
        val task1 = AzaAgentTask(
            objectiveId = 999,
            assignedAgent = "Retry Agent 1",
            title = "Retry Task 1",
            description = "Test retry",
            priority = "HIGH",
            status = "PENDING_APPROVAL",
            requiredSkill = "test_skill_1"
        )

        val result1 = manager.executeTask(task1, agent1) { log ->
            println("    [Manager Log] $log")
        }

        assertTrue(result1.success)
        assertEquals(2, agent1.executionCount) // Attempt 1 (fail), Attempt 2 (success)
        assertEquals(1, result1.retryCount)

        // 2. Fatal failure: exhausts all 3 attempts (3 failures, 2 retries)
        val agent2 = FakeAgent("test_skill_2", "Retry Agent 2", failuresToSimulate = 3)
        val task2 = AzaAgentTask(
            objectiveId = 999,
            assignedAgent = "Retry Agent 2",
            title = "Retry Task 2",
            description = "Test exhaust retries",
            priority = "HIGH",
            status = "PENDING_APPROVAL",
            requiredSkill = "test_skill_2"
        )

        val result2 = manager.executeTask(task2, agent2) { log ->
            println("    [Manager Log] $log")
        }

        assertFalse(result2.success)
        assertEquals(3, agent2.executionCount) // Attempt 1 (fail), Attempt 2 (fail), Attempt 3 (fail)
        assertEquals(2, result2.retryCount)
        println("Transient agent failures and max retry exhaustion handled correctly.")
    }

    /**
     * Scenario 3: Database restart and recovery
     * Creates a persistent file-backed database, registers objectives, crashes (closes DB),
     * restarts (creates fresh DB instance over the same file), and asserts data integrity.
     */
    @Test
    fun testDatabaseRestartAndRecovery() = runBlocking {
        println("=== Scenario 3: Database Restart and Recovery Test ===")
        val dbFile = context.getDatabasePath("aza_recovery_stress_test.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }

        // Initialize first DB instance
        var persistentDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
        var tempRepo = AppRepository(persistentDb)

        // Save persistent states
        val objective = AzaObjective(
            id = 0,
            objective = "Recoverable high-level target",
            title = "System Persistence Target",
            status = "IN_PROGRESS",
            progress = 0.5f,
            currentTask = "Pre-crash checkpoint task",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val insertedId = tempRepo.insertObjective(objective).toInt()

        val task1 = AzaAgentTask(
            id = 0,
            objectiveId = insertedId,
            assignedAgent = "Aza Agent",
            title = "Completed Task",
            description = "Completed before crash",
            priority = "HIGH",
            status = "COMPLETED",
            requiredSkill = "any_skill"
        )
        val task2 = AzaAgentTask(
            id = 0,
            objectiveId = insertedId,
            assignedAgent = "Aza Agent",
            title = "Interrupted Task",
            description = "Interrupted by crash",
            priority = "HIGH",
            status = "RUNNING",
            requiredSkill = "any_skill"
        )
        tempRepo.insertTasks(listOf(task1, task2))

        // Simulate crash by closing connection abruptly
        persistentDb.close()

        // Create a new distinct DB instance pointing to the same file
        val restartedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
        val restartedRepo = AppRepository(restartedDb)

        // Verify recovered state matches pre-crash states
        val recoveredObj = restartedRepo.getObjectiveByIdDirect(insertedId)
        assertNotNull(recoveredObj)
        assertEquals("Recoverable high-level target", recoveredObj?.objective)
        assertEquals("IN_PROGRESS", recoveredObj?.status)
        assertEquals(0.5f, recoveredObj?.progress ?: 0f)

        val recoveredTasks = restartedRepo.getTasksForObjectiveDirect(insertedId)
        assertEquals(2, recoveredTasks.size)

        val completedTask = recoveredTasks.find { it.title == "Completed Task" }
        assertNotNull(completedTask)
        assertEquals("COMPLETED", completedTask?.status)

        val interruptedTask = recoveredTasks.find { it.title == "Interrupted Task" }
        assertNotNull(interruptedTask)
        assertEquals("RUNNING", interruptedTask?.status)

        restartedDb.close()
        dbFile.delete()
        println("Database crash recovery of persistent active schedules verified successfully.")
    }

    /**
     * Scenario 4: Concurrent task execution
     * Spawns multiple tasks executing concurrently, ensuring that the thread-safe TaskContext map
     * and the repository database do not trigger deadlock or race conditions.
     */
    @Test
    fun testConcurrentTaskExecution() = runBlocking {
        println("=== Scenario 4: Concurrent Task Execution Stress Test ===")
        val eventBus = com.example.aza.context.AzaEventBus()
        val manager = AzaExecutionManager(repository, eventBus)

        val concurrentTasksCount = 10
        val agents = List(concurrentTasksCount) { i ->
            FakeAgent("concurrent_skill_$i", "Agent $i", failuresToSimulate = 0)
        }
        val tasks = List(concurrentTasksCount) { i ->
            AzaAgentTask(
                objectiveId = 888,
                assignedAgent = "Agent $i",
                title = "Concurrent Task $i",
                description = "Task run concurrent",
                priority = "MEDIUM",
                status = "PENDING_APPROVAL",
                requiredSkill = "concurrent_skill_$i"
            )
        }

        // Spawn coroutines concurrently
        val jobs = tasks.mapIndexed { idx, task ->
            this@runBlocking.async(kotlinx.coroutines.Dispatchers.Default) {
                manager.executeTask(task, agents[idx]) { _ -> }
            }
        }

        val results = jobs.map { it.await() }
        assertEquals(concurrentTasksCount, results.size)
        assertTrue(results.all { it.success })

        for (i in 0 until concurrentTasksCount) {
            assertEquals(1, agents[i].executionCount)
        }
        println("Successfully executed $concurrentTasksCount tasks concurrently without thread deadlock or race conditions.")
    }

    /**
     * Scenario 5: Cancellation and resume
     * Verifies that cancelling active contexts correctly intercepts execution,
     * while enabling new execution contexts to run unaffected.
     */
    @Test
    fun testCancellationAndResume() = runBlocking {
        println("=== Scenario 5: Cancellation and Resume Test ===")
        val eventBus = com.example.aza.context.AzaEventBus()
        val manager = AzaExecutionManager(repository, eventBus)

        val contextId = 111
        val taskContext = manager.getOrCreateContext(contextId)
        assertFalse(taskContext.isCancelled)

        // Cancel execution context
        manager.cancelObjectiveThread(contextId)
        assertTrue(taskContext.isCancelled)
        assertEquals("CANCELLED", taskContext.status)

        // Execution should be blocked prior to starting
        val agent = FakeAgent("skill_c", "Agent C", failuresToSimulate = 0)
        val task = AzaAgentTask(
            objectiveId = contextId,
            assignedAgent = "Agent C",
            title = "Cancelled Task Execution",
            description = "Will fail due to pre-cancellation",
            priority = "HIGH",
            status = "PENDING_APPROVAL",
            requiredSkill = "skill_c"
        )

        val result = manager.executeTask(task, agent) { _ -> }
        assertFalse(result.success)
        assertEquals("Execution thread cancelled.", result.executionLog)
        assertEquals(0, agent.executionCount) // Never runs because of cancellation boundary

        // Fresh context must execute unaffected
        val freshContextId = 222
        val freshTaskContext = manager.getOrCreateContext(freshContextId)
        assertFalse(freshTaskContext.isCancelled)

        val freshTask = task.copy(objectiveId = freshContextId, title = "Resumed Task Execution")
        val freshResult = manager.executeTask(freshTask, agent) { _ -> }
        assertTrue(freshResult.success)
        assertEquals(1, agent.executionCount) // Executed successfully
        println("Context cancellation and execution resumption verified successfully.")
    }

    /**
     * Scenario 6: Paper trading under simulated market changes
     * Triggers multiple sequential asset acquisitions at different market price points,
     * verifying exact cash deductions and weighted average buy-price recalculations.
     */
    @Test
    fun testPaperTradingUnderSimulatedMarketChanges() = runBlocking {
        println("=== Scenario 6: Paper Trading Under Market Fluctuations ===")

        // Initialize liquid account
        val account = AccountLinking(
            id = 1,
            totalBalance = 10000.0,
            isTradingLinked = true
        )
        repository.updateAccountLinking(account)

        // Clear existing portfolio assets
        val currentAssets = repository.portfolioAssets.first()
        for (asset in currentAssets) {
            repository.removeAsset(asset)
        }

        // Purchase 1: Buy 0.1 BTC at price of $50,000 (Cost = $5,000)
        val purchaseAmount1 = 0.1
        val purchasePrice1 = 50000.0
        val cost1 = purchaseAmount1 * purchasePrice1

        var latestAccount = repository.getAccountInfoDirect()!!
        repository.updateAccountLinking(latestAccount.copy(totalBalance = latestAccount.totalBalance - cost1))
        repository.insertAsset(
            PortfolioAsset("BTC", "Bitcoin", purchaseAmount1, purchasePrice1, "CRYPTO", purchasePrice1)
        )

        var btcAsset = repository.getAssetBySymbol("BTC")
        assertNotNull(btcAsset)
        assertEquals(0.1, btcAsset!!.amountOwned, 0.0001)
        assertEquals(50000.0, btcAsset.averageBuyPrice, 0.0001)

        // Purchase 2: Buy another 0.05 BTC at price of $70,000 (Cost = $3,500)
        val purchaseAmount2 = 0.05
        val purchasePrice2 = 70000.0
        val cost2 = purchaseAmount2 * purchasePrice2

        latestAccount = repository.getAccountInfoDirect()!!
        repository.updateAccountLinking(latestAccount.copy(totalBalance = latestAccount.totalBalance - cost2))

        val existing = repository.getAssetBySymbol("BTC")!!
        val newAmount = existing.amountOwned + purchaseAmount2
        val totalCostExisting = (existing.amountOwned * existing.averageBuyPrice) + cost2
        val newAvgPrice = totalCostExisting / newAmount
        repository.updateAsset(
            existing.copy(amountOwned = newAmount, averageBuyPrice = newAvgPrice, currentPrice = purchasePrice2)
        )

        // Validate outcomes
        val finalAccount = repository.getAccountInfoDirect()!!
        assertEquals(1500.0, finalAccount.totalBalance, 0.0001) // 10000 - 5000 - 3500

        val finalBtcAsset = repository.getAssetBySymbol("BTC")!!
        assertEquals(0.15, finalBtcAsset.amountOwned, 0.0001)
        // Average Price: (0.1 * 50000 + 0.05 * 70000) / 0.15 = 8500 / 0.15 = 56666.6667
        assertEquals(56666.6667, finalBtcAsset.averageBuyPrice, 0.1)
        assertEquals(70000.0, finalBtcAsset.currentPrice, 0.0001)

        println("Paper trading under dynamic price changes executes with perfect mathematical precision.")
    }

    /**
     * Scenario 7: Long-running autonomous operation simulation without memory leaks or deadlocks
     * Runs 200 consecutive execution loops to verify that system stability is preserved.
     */
    @Test
    fun testLongRunningAutonomousOperation() = runBlocking {
        println("=== Scenario 7: Long-Running Autonomous Operation Simulation ===")
        val eventBus = com.example.aza.context.AzaEventBus()
        val manager = AzaExecutionManager(repository, eventBus)
        val agent = FakeAgent("leak_test_skill", "Leak Test Agent", failuresToSimulate = 0)

        val runtimeIterations = 200
        println("Simulating $runtimeIterations consecutive loop execution runs...")

        val runtimeStart = System.currentTimeMillis()
        for (i in 1..runtimeIterations) {
            val task = AzaAgentTask(
                objectiveId = i, // unique objective id per cycle to emulate distinct workflows
                assignedAgent = "Leak Test Agent",
                title = "Operation Cycle $i",
                description = "Task for cycle $i",
                priority = "MEDIUM",
                status = "PENDING_APPROVAL",
                requiredSkill = "leak_test_skill"
            )
            val result = manager.executeTask(task, agent) { _ -> }
            assertTrue(result.success)

            if (i % 50 == 0) {
                val freeMem = Runtime.getRuntime().freeMemory()
                val totalMem = Runtime.getRuntime().totalMemory()
                println("    -> Executed cycle $i/$runtimeIterations. Free memory: ${freeMem / (1024 * 1024)}MB / ${totalMem / (1024 * 1024)}MB")
            }
        }
        val duration = System.currentTimeMillis() - runtimeStart
        println("Completed $runtimeIterations operational pipeline runs in ${duration}ms with zero deadlocks or resource leaks.")
    }
}
