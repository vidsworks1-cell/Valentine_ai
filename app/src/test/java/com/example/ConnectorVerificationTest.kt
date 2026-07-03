package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.aza.capability.*
import com.example.data.database.*
import com.example.data.repository.AppRepository
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
class ConnectorVerificationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository
    private lateinit var registry: CapabilityRegistry

    private val binanceCap = BinanceCapability()
    private val contentCap = ContentCapability()
    private val youtubeCap = YouTubeCapability()
    private val freelanceCap = FreelanceCapability()
    private val notificationCap = NotificationCapability()

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(database)
        registry = CapabilityRegistry(repository)

        // Register all capabilities under test
        registry.registerCapability(binanceCap)
        registry.registerCapability(contentCap)
        registry.registerCapability(youtubeCap)
        registry.registerCapability(freelanceCap)
        registry.registerCapability(notificationCap)

        // Pre-populate Capability States in the DB
        repository.insertOrUpdateCapabilityState(
            CapabilityStateEntity(
                name = binanceCap.name,
                description = binanceCap.description,
                supportedActions = binanceCap.supportedActions.joinToString(","),
                requiredPermissions = binanceCap.requiredPermissions.joinToString(","),
                isEnabled = true,
                status = "ONLINE",
                healthState = "HEALTHY"
            )
        )
        repository.insertOrUpdateCapabilityState(
            CapabilityStateEntity(
                name = contentCap.name,
                description = contentCap.description,
                supportedActions = contentCap.supportedActions.joinToString(","),
                requiredPermissions = contentCap.requiredPermissions.joinToString(","),
                isEnabled = true,
                status = "ONLINE",
                healthState = "HEALTHY"
            )
        )
        repository.insertOrUpdateCapabilityState(
            CapabilityStateEntity(
                name = youtubeCap.name,
                description = youtubeCap.description,
                supportedActions = youtubeCap.supportedActions.joinToString(","),
                requiredPermissions = youtubeCap.requiredPermissions.joinToString(","),
                isEnabled = true,
                status = "ONLINE",
                healthState = "HEALTHY"
            )
        )
        repository.insertOrUpdateCapabilityState(
            CapabilityStateEntity(
                name = freelanceCap.name,
                description = freelanceCap.description,
                supportedActions = freelanceCap.supportedActions.joinToString(","),
                requiredPermissions = freelanceCap.requiredPermissions.joinToString(","),
                isEnabled = true,
                status = "ONLINE",
                healthState = "HEALTHY"
            )
        )
        repository.insertOrUpdateCapabilityState(
            CapabilityStateEntity(
                name = notificationCap.name,
                description = notificationCap.description,
                supportedActions = notificationCap.supportedActions.joinToString(","),
                requiredPermissions = notificationCap.requiredPermissions.joinToString(","),
                isEnabled = true,
                status = "ONLINE",
                healthState = "HEALTHY"
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==========================================
    // 1. CORE CONNECTOR OUTPUT VERIFICATION TESTS
    // ==========================================

    @Test
    fun testBinanceConnector_OutputValidation() = runBlocking {
        println("--- TEST: Binance Connector Outputs ---")
        
        // Test Balance Check
        val balResult = registry.executeActionSecurely(binanceCap.name, "READ_BALANCE", emptyMap(), null) { println(it) }
        assertTrue(balResult.success)
        assertNotNull(balResult.outputData["balance_btc"])
        assertEquals("USDT", balResult.outputData["currency"])

        // Test Open Orders Check
        val orderResult = registry.executeActionSecurely(binanceCap.name, "READ_ORDERS", emptyMap(), null) { println(it) }
        assertTrue(orderResult.success)
        val orders = orderResult.outputData["open_orders"] as? List<*>
        assertNotNull(orders)
        assertTrue(orders!!.isNotEmpty())

        // Test Market Price Retrieval with custom parameter
        val marketResult = registry.executeActionSecurely(binanceCap.name, "MARKET_INFO", mapOf("symbol" to "ETHUSDT"), null) { println(it) }
        assertTrue(marketResult.success)
        assertEquals("ETHUSDT", marketResult.outputData["symbol"])
        assertNotNull(marketResult.outputData["lastPrice"])
    }

    @Test
    fun testContentConnector_OutputValidation() = runBlocking {
        println("--- TEST: Content Automation Connector Outputs ---")

        // Test Ideas Generation
        val ideasResult = registry.executeActionSecurely(contentCap.name, "GENERATE_IDEAS", mapOf("topic" to "Kotlin Multi-Agent"), null) { println(it) }
        assertTrue(ideasResult.success)
        val ideasList = ideasResult.outputData["ideas"] as? List<*>
        assertNotNull(ideasList)
        assertTrue(ideasList!!.isNotEmpty())

        // Test Script Generation inserting to Content DB
        val scriptResult = registry.executeActionSecurely(
            contentCap.name, 
            "GENERATE_SCRIPTS", 
            mapOf("topic" to "Jetpack Compose", "platform" to "TIKTOK"), 
            null
        ) { println(it) }
        assertTrue(scriptResult.success)
        assertEquals("INSERTED_DB", scriptResult.outputData["status"])
        
        // Confirm DB Persistence
        val contentsInDb = database.contentDao().getAllContent().first()
        assertTrue(contentsInDb.any { it.title.contains("Jetpack Compose") })
    }

    @Test
    fun testYouTubeConnector_OutputValidation() = runBlocking {
        println("--- TEST: YouTube Integration Connector Outputs ---")

        val analyticsResult = registry.executeActionSecurely(youtubeCap.name, "CHANNEL_ANALYTICS", emptyMap(), null) { println(it) }
        assertTrue(analyticsResult.success)
        assertEquals(2450, analyticsResult.outputData["subscribers"])
        assertEquals(42100, analyticsResult.outputData["views"])

        // Performance Tracking & Learning Record Generation
        val trackResult = registry.executeActionSecurely(youtubeCap.name, "TRACK_PERFORMANCE", emptyMap(), null) { println(it) }
        assertTrue(trackResult.success)
        assertEquals("SUCCESS", trackResult.outputData["feedback_status"])

        // Check if dynamic learning record and knowledge entry are created in sqlite
        val learningRecords = database.azaLearningRecordDao().getAllLearningRecordsFlow().first()
        assertTrue(learningRecords.any { it.skillName.contains("YouTube Capability") })

        val knowledgeEntries = database.knowledgeDao().getAllKnowledgeDirect()
        assertTrue(knowledgeEntries.any { it.title.contains("YouTube technical content view retention") })
    }

    @Test
    fun testFreelanceConnector_OutputValidation() = runBlocking {
        println("--- TEST: Freelance Connector Outputs ---")

        val monitorResult = registry.executeActionSecurely(freelanceCap.name, "MONITOR_OPPORTUNITIES", emptyMap(), null) { println(it) }
        assertTrue(monitorResult.success)
        val list = monitorResult.outputData["available_opportunities"] as? List<*>
        assertNotNull(list)
        assertTrue(list!!.isNotEmpty())

        val matchResult = registry.executeActionSecurely(freelanceCap.name, "MATCH_SKILLS", emptyMap(), null) { println(it) }
        assertTrue(matchResult.success)
        assertEquals(95.0, matchResult.outputData["match_percentage"])
    }

    @Test
    fun testNotificationConnector_MessageFormatting() = runBlocking {
        println("--- TEST: Notification Formatting & DB Injection ---")

        // Test Mission Completed Formatting
        val compResult = registry.executeActionSecurely(
            notificationCap.name, 
            "MISSION_COMPLETED", 
            mapOf("message" to "All indicators green."), 
            null
        ) { println(it) }
        assertTrue(compResult.success)
        assertTrue(compResult.outputData["formatted_message"].toString().contains("🏆 **MISSION COMPLETED:**"))

        // Test Safety Warning Formatting
        val warnResult = registry.executeActionSecurely(
            notificationCap.name, 
            "SAFETY_WARNING", 
            mapOf("message" to "High risk trade request."), 
            null
        ) { println(it) }
        assertTrue(warnResult.success)
        assertTrue(warnResult.outputData["formatted_message"].toString().contains("⚠️ **SAFETY SECURITY WARNING:**"))

        // Verify ChatMessage insertions in Database
        val chatMessages = database.chatDao().getAllMessagesFlow().first()
        assertTrue(chatMessages.any { it.text.contains("All indicators green.") })
        assertTrue(chatMessages.any { it.text.contains("High risk trade request.") })
    }

    // ==========================================
    // 2. CONDITION / MODE / POLICY TESTING
    // ==========================================

    @Test
    fun testBinanceDynamicPolicy_AutonomousVsApproval() = runBlocking {
        println("--- TEST: Binance Dynamic Policy Enforcement ---")

        // Enable real order placement temporarily to test live trade policies
        com.example.aza.connector.BinanceConnector.setOrderPlacementEnabled(true)

        // 1. Enable Live mode & approval policy
        val state = repository.getCapabilityStateByNameDirect(binanceCap.name)
        assertNotNull(state)
        repository.insertOrUpdateCapabilityState(
            state!!.copy(operatingMode = "LIVE", authPolicy = "APPROVAL_REQUIRED", apiKey = "test_key", apiSecret = "test_secret")
        )

        // 2. Try executing LIVE trade WITHOUT user approval parameter
        val blockedResult = registry.executeActionSecurely(binanceCap.name, "EXECUTE_REAL_TRADE", mapOf("symbol" to "BTC", "amount" to 0.05), null) { println(it) }
        assertFalse(blockedResult.success)
        assertTrue(blockedResult.errorMessage!!.contains("REAL_TRADE_DENIED_BY_POLICY"))

        // Confirm approval request was sent into Chat history
        val chatHistory = database.chatDao().getAllMessagesFlow().first()
        assertTrue(chatHistory.any { it.text.contains("🔒 **APPROVAL REQUESTED:**") && it.text.contains("Real Trade") })

        // 3. Execute LIVE trade WITH explicit user approval parameter
        val approvedResult = registry.executeActionSecurely(
            binanceCap.name, 
            "EXECUTE_REAL_TRADE", 
            mapOf("symbol" to "BTC", "amount" to 0.05, "approved" to true), 
            null
        ) { println(it) }
        assertTrue(approvedResult.success)
        assertEquals("FILLED", approvedResult.outputData["status"])
        assertEquals("LIVE_REAL", approvedResult.outputData["mode"])

        // Reset real order placement back to disabled
        com.example.aza.connector.BinanceConnector.setOrderPlacementEnabled(false)
    }

    @Test
    fun testFreelanceDynamicPolicy_AutonomousVsApproval() = runBlocking {
        println("--- TEST: Freelance Dynamic Policy Enforcement ---")

        val state = repository.getCapabilityStateByNameDirect(freelanceCap.name)
        assertNotNull(state)
        repository.insertOrUpdateCapabilityState(
            state!!.copy(operatingMode = "LIVE", authPolicy = "APPROVAL_REQUIRED", apiKey = "test_key")
        )

        // Try bidding WITHOUT explicit approval
        val blockedResult = registry.executeActionSecurely(
            freelanceCap.name, 
            "BUILD_APPLICATION_PACKAGE", 
            mapOf("gig_title" to "Kotlin Developer"), 
            null
        ) { println(it) }
        assertFalse(blockedResult.success)
        assertTrue(blockedResult.errorMessage!!.contains("APPLICATION_BLOCKED_BY_POLICY"))

        // Try bidding WITH explicit approval
        val approvedResult = registry.executeActionSecurely(
            freelanceCap.name, 
            "BUILD_APPLICATION_PACKAGE", 
            mapOf("gig_title" to "Kotlin Developer", "approved" to true), 
            null
        ) { println(it) }
        assertTrue(approvedResult.success)
        assertEquals("SUBMITTED", approvedResult.outputData["package_status"])

        // Check sqlite persistence
        val gigs = database.gigDao().getAllGigs().first()
        assertTrue(gigs.any { it.title == "Kotlin Developer" && it.status == "SUBMITTED" })
    }

    // ==========================================
    // 3. ERROR SIMULATION & SECURITY WATCHDOG
    // ==========================================

    @Test
    fun testLiveMode_CredentialRequirementFailure() = runBlocking {
        println("--- TEST: Missing Credentials Fail-Safe ---")

        val state = repository.getCapabilityStateByNameDirect(binanceCap.name)
        assertNotNull(state)
        repository.insertOrUpdateCapabilityState(
            state!!.copy(operatingMode = "LIVE", apiKey = "", apiSecret = "")
        )

        val result = registry.executeActionSecurely(binanceCap.name, "READ_BALANCE", emptyMap(), null) { println(it) }
        assertFalse(result.success)
        assertTrue(result.errorMessage!!.contains("API Credentials missing"))
    }

    @Test
    fun testSecurityWatchdog_RepeatedFailuresShutdown() = runBlocking {
        println("--- TEST: Security Watchdog Shutdown ---")

        val capabilityName = binanceCap.name
        
        // 1. Enable Live mode but with empty credentials, so supported actions fail!
        val stateBefore = repository.getCapabilityStateByNameDirect(capabilityName)
        assertNotNull(stateBefore)
        repository.insertOrUpdateCapabilityState(
            stateBefore!!.copy(operatingMode = "LIVE", apiKey = "", apiSecret = "", isEnabled = true, status = "ONLINE", errorCount = 0, healthState = "HEALTHY")
        )

        // Failure 1
        var result1 = registry.executeActionSecurely(capabilityName, "READ_BALANCE", emptyMap(), null) { println(it) }
        assertFalse(result1.success)

        val stateAfter1 = repository.getCapabilityStateByNameDirect(capabilityName)
        assertEquals(1, stateAfter1?.errorCount)
        assertEquals("DEGRADED", stateAfter1?.healthState)
        assertTrue(stateAfter1!!.isEnabled)

        // Failure 2
        var result2 = registry.executeActionSecurely(capabilityName, "READ_BALANCE", emptyMap(), null) { println(it) }
        assertFalse(result2.success)

        val stateAfter2 = repository.getCapabilityStateByNameDirect(capabilityName)
        assertEquals(2, stateAfter2?.errorCount)
        assertEquals("DEGRADED", stateAfter2?.healthState)
        assertTrue(stateAfter2!!.isEnabled)

        // Failure 3 - Watchdog should activate!
        var result3 = registry.executeActionSecurely(capabilityName, "READ_BALANCE", emptyMap(), null) { println(it) }
        assertFalse(result3.success)

        val stateAfter3 = repository.getCapabilityStateByNameDirect(capabilityName)
        assertEquals(3, stateAfter3?.errorCount)
        assertEquals("FAILING", stateAfter3?.healthState)
        assertEquals("OFFLINE", stateAfter3?.status)
        assertFalse(stateAfter3!!.isEnabled) // SHUT DOWN

        // Check if Watchdog Critical Notification was logged to DB Chat history
        val chatHistory = database.chatDao().getAllMessagesFlow().first()
        assertTrue(chatHistory.any { it.sender == "SECURITY WATCHDOG" && it.text.contains("CRITICAL SECURITY SHUTDOWN") })

        // Check if subsequent attempts are instantly blocked by Security Guard
        val blockedResult = registry.executeActionSecurely(capabilityName, "READ_BALANCE", emptyMap(), null) { println(it) }
        assertFalse(blockedResult.success)
        assertTrue(blockedResult.errorMessage!!.contains("DISABLED") || blockedResult.errorMessage!!.contains("OFFLINE"))
    }

    @Test
    fun testRegistryBlocks_UnsupportedAction() = runBlocking {
        println("--- TEST: Action Support Filter ---")
        val result = registry.executeActionSecurely(binanceCap.name, "HACK_SERVER_AND_STEAL_KEYS", emptyMap(), null) { println(it) }
        assertFalse(result.success)
        assertTrue(result.errorMessage!!.contains("is not supported"))
    }

    // ==========================================
    // 4. SPEED & LATENCY PERFORMANCE TESTS
    // ==========================================

    @Test
    fun testConnectors_SpeedAndLatencyBenchmark() = runBlocking {
        println("--- TEST: Connector Performance Benchmark ---")

        val benchmarkActions = listOf(
            Triple(binanceCap.name, "READ_BALANCE", emptyMap<String, Any>()),
            Triple(binanceCap.name, "READ_POSITIONS", emptyMap<String, Any>()),
            Triple(binanceCap.name, "MARKET_INFO", mapOf("symbol" to "BTCUSDT")),
            Triple(contentCap.name, "GENERATE_IDEAS", mapOf("topic" to "Agent architectures")),
            Triple(contentCap.name, "PRODUCE_CAPTIONS", emptyMap<String, Any>()),
            Triple(youtubeCap.name, "CHANNEL_ANALYTICS", emptyMap<String, Any>()),
            Triple(freelanceCap.name, "MONITOR_OPPORTUNITIES", emptyMap<String, Any>()),
            Triple(freelanceCap.name, "MATCH_SKILLS", emptyMap<String, Any>()),
            Triple(notificationCap.name, "EXEC_DECISION", mapOf("message" to "Performance Benchmark Initialized."))
        )

        val latencies = mutableListOf<Long>()
        val totalLoops = 3 // execute the suite multiple times for consistent average statistics
        var totalOpsCount = 0
        val startTimeBenchmark = System.currentTimeMillis()

        for (i in 1..totalLoops) {
            for (actionTriple in benchmarkActions) {
                val actionResult = registry.executeActionSecurely(
                    actionTriple.first,
                    actionTriple.second,
                    actionTriple.third,
                    null
                ) { }
                assertTrue(actionResult.success)
                assertNotNull(actionResult.durationMs)
                latencies.add(actionResult.durationMs)
                totalOpsCount++
            }
        }

        val totalTimeBenchmark = System.currentTimeMillis() - startTimeBenchmark
        val averageLatency = latencies.average()
        val maxLatency = latencies.maxOrNull() ?: 0L
        val minLatency = latencies.minOrNull() ?: 0L
        val opsPerSec = (totalOpsCount.toDouble() / totalTimeBenchmark.toDouble()) * 1000.0

        println("\n=======================================================")
        println("              CONNECTOR PERFORMANCE REPORT             ")
        println("=======================================================")
        println("Total Actions Executed : $totalOpsCount ops")
        println("Total Benchmark Time   : ${totalTimeBenchmark} ms")
        println("Throughput Rate        : ${String.format("%.2f", opsPerSec)} ops/sec")
        println("Average Action Latency : ${String.format("%.2f", averageLatency)} ms")
        println("Max Latency Spike      : ${maxLatency} ms")
        println("Min Latency            : ${minLatency} ms")
        println("=======================================================\n")

        // Assert performance is within expected constraints (local JVM execution shouldn't bottleneck)
        assertTrue("Average latency must be fast in mock execution mode", averageLatency < 100.0)
        assertTrue("Minimum latency should be ultra-low on in-memory db", minLatency >= 0L)
    }
}
