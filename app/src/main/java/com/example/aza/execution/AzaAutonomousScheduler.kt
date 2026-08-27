package com.example.aza.execution

import com.example.aza.agent.AgentRegistry
import com.example.aza.agent.AgentResult
import com.example.aza.context.TaskContext
import com.example.aza.planning.AzaPlanningEngine
import com.example.data.database.AppDatabase
import com.example.data.database.TaskEntity
import com.example.data.database.TaskExecution
import com.example.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.*

class AzaAutonomousScheduler(
    private val repository: AppRepository,
    private val planningEngine: AzaPlanningEngine,
    private val scope: CoroutineScope,
    val capabilityRegistry: com.example.aza.capability.CapabilityRegistry
) {
    val resourceManager = com.example.aza.executive.ResourceManager(repository)
    val consensusEngine = com.example.aza.executive.ConfidenceConsensusEngine(repository, planningEngine.coordinator.learningEngine, planningEngine.registry)
    val executivePlanner = com.example.aza.executive.ExecutivePlanner(repository, planningEngine.coordinator.learningEngine)
    val executiveBrain = com.example.aza.executive.ExecutiveBrain(repository)
    var dynamicExponentialEstimator: com.example.aza.executive.DynamicExponentialEstimator? = null
    data class SchedulerDiagnostics(
        val isHealthy: Boolean,
        val networkStatus: String,
        val batteryPct: Int,
        val apiAvailable: Boolean,
        val tradingHours: Boolean,
        val rateLimitPassed: Boolean,
        val pendingCount: Int,
        val lastRunTimestamp: Long
    )

    private val _diagnostics = MutableStateFlow(
        SchedulerDiagnostics(
            isHealthy = true,
            networkStatus = "CONNECTED",
            batteryPct = 95,
            apiAvailable = true,
            tradingHours = true,
            rateLimitPassed = true,
            pendingCount = 0,
            lastRunTimestamp = System.currentTimeMillis()
        )
    )
    val diagnostics = _diagnostics.asStateFlow()

    private val _isSchedulerRunning = MutableStateFlow(false)
    val isSchedulerRunning = _isSchedulerRunning.asStateFlow()

    // Interactive simulator overrides for robustness testing
    var simulatedNetworkStatus = "CONNECTED" // CONNECTED, DISCONNECTED
    var forceNextTaskFailure = false

    private var recentCallsCount = 0
    private var lastCallResetTime = System.currentTimeMillis()

    init {
        // Start periodic diagnostic cycles (checks every 20 seconds)
        scope.launch(Dispatchers.IO) {
            while (true) {
                runDiagnosticCheck()
                delay(20000)
            }
        }
    }

    /**
     * Updates recent calls count to simulate rate limiters.
     */
    private fun checkRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCallResetTime > 60000) {
            recentCallsCount = 0
            lastCallResetTime = now
        }
        return recentCallsCount < 5 // max 5 actions per minute allowed
    }

    suspend fun runDiagnosticCheck(): SchedulerDiagnostics {
        val tasks = repository.getQueueTasksByStatus("QUEUED") + repository.getQueueTasksByStatus("RETRY")
        val random = Random()
        
        // Simulating physical and environment states safely
        val network = simulatedNetworkStatus
        val battery = 60 + random.nextInt(40)
        val apiAvailable = random.nextDouble() > 0.02
        
        // Simulating stock market trading hours
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val isTradingHours = day != Calendar.SATURDAY && day != Calendar.SUNDAY && hour >= 9 && hour < 17

        val isHealthy = network == "CONNECTED" && battery > 15 && apiAvailable && checkRateLimit()

        val diag = SchedulerDiagnostics(
            isHealthy = isHealthy,
            networkStatus = network,
            batteryPct = battery,
            apiAvailable = apiAvailable,
            tradingHours = isTradingHours,
            rateLimitPassed = checkRateLimit(),
            pendingCount = tasks.size,
            lastRunTimestamp = System.currentTimeMillis()
        )
        _diagnostics.value = diag
        return diag
    }

    private fun calculateDynamicPriority(task: TaskEntity, batteryPct: Int, networkStatus: String): Int {
        var baseScore = when (task.priority.uppercase()) {
            "HIGH" -> 30
            "MEDIUM" -> 20
            "LOW" -> 10
            else -> 20
        }
        
        val lowerSkill = task.requiredSkill.lowercase()
        // 1. Market opportunities boost: if task is a Trading/Crypto task, boost +10!
        if (lowerSkill.contains("trading") || lowerSkill.contains("crypto")) {
            baseScore += 10
        }
        
        // 2. Resource preservation: if battery is low (< 30%), downgrade low priority content tasks by -10 to save energy
        if (batteryPct < 30) {
            if (lowerSkill.contains("content") || lowerSkill.contains("social") || lowerSkill.contains("web")) {
                baseScore -= 10
            }
        }
        
        // 3. Time elapsed / Deadline pressure: tasks that have been queued for more than 1 minute get a pressure boost
        val timeInQueueMs = System.currentTimeMillis() - task.updatedAt
        if (timeInQueueMs > 60000) {
            val additionalPoints = (timeInQueueMs / 30000).toInt().coerceAtMost(10)
            baseScore += additionalPoints
        }
        
        return baseScore
    }

    fun startPollingScheduler(onLog: (String) -> Unit) {
        if (_isSchedulerRunning.value) return
        _isSchedulerRunning.value = true
        onLog("⏰ AUTONOMOUS SCHEDULER: Background Scheduler Thread Initialized.")

        scope.launch(Dispatchers.IO) {
            while (_isSchedulerRunning.value) {
                try {
                    val currentDiag = runDiagnosticCheck()
                    onLog("🔎 SCHEDULER CYCLE: Checking conditions... [Network: ${currentDiag.networkStatus}, Battery: ${currentDiag.batteryPct}%, API: ${currentDiag.apiAvailable}, RateLimitPassed: ${currentDiag.rateLimitPassed}]")
                    
                    if (!currentDiag.isHealthy) {
                        onLog("⚠️ SCHEDULER PAUSED: Diagnostic constraints unmet. Halted autonomous execution loop.")
                    } else {
                        // Phase 12 Executive Tasks
                        resourceManager.autoResumeSuspendedTasks(onLog)
                        executivePlanner.superviseAndOptimize(onLog)

                        // Phase 19 Autonomous Executive Brain & Continuous Operations
                        val activeMissions = repository.allMissions.firstOrNull() ?: emptyList()
                        val knowledgeList = repository.getAllKnowledgeDirect()
                        val contentList = repository.contentItems.firstOrNull() ?: emptyList()
                        val freelanceList = repository.freelanceGigs.firstOrNull() ?: emptyList()
                        val metrics = resourceManager.currentMetrics.value
                        executiveBrain.runContinuousDecisionCycle(
                            schedulerDiagnostics = currentDiag,
                            resourceMetrics = metrics,
                            activeMissions = activeMissions,
                            knowledgeList = knowledgeList,
                            contentList = contentList,
                            freelanceList = freelanceList,
                            onLog = onLog
                        )

                        val runnableTasks = repository.getQueueTasksByStatus("QUEUED") + repository.getQueueTasksByStatus("RETRY")
                        if (runnableTasks.isNotEmpty()) {
                            onLog("⚡ SCHEDULER QUEUE: Detected ${runnableTasks.size} tasks in queue. Searching for ready candidates...")
                            
                            // Find candidate with completed dependencies and NOT in a paused mission
                            val completedTasks = repository.getQueueTasksByStatus("COMPLETED").map { it.title }.toSet()
                            val pausedMissionIds = repository.getPausedMissionIds()
                            
                            // Dynamic Priority Engine: Calculate score matching real-time context
                            val scoredTasks = runnableTasks.map { task ->
                                val score = calculateDynamicPriority(task, currentDiag.batteryPct, currentDiag.networkStatus)
                                task to score
                            }
                            
                            val sortedTasks = scoredTasks.sortedWith(
                                compareByDescending<Pair<TaskEntity, Int>> { it.second }
                                    .thenBy { it.first.id }
                            ).map { it.first }
                            
                            val topScored = scoredTasks.maxByOrNull { it.second }
                            if (topScored != null) {
                                onLog("⚖️ DYNAMIC PRIORITY ENGINE: Re-calibrated task '${topScored.first.title}' urgency context score: ${topScored.second} (Base priority: ${topScored.first.priority})")
                            }

                            var nextTask: TaskEntity? = null
                            for (task in sortedTasks) {
                                // 1. Check parent objective & parent mission status
                                val objective = repository.getObjectiveByIdDirect(task.objectiveId)
                                val isMissionPaused = if (objective?.missionId != null) {
                                    pausedMissionIds.contains(objective.missionId)
                                } else {
                                    false
                                }

                                if (isMissionPaused) {
                                    continue // Skip paused mission tasks
                                }

                                // 2. Check dependencies
                                val deps = task.executionLog.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                
                                val allDepsMet = deps.all { completedTasks.contains(it) }
                                if (allDepsMet) {
                                    nextTask = task
                                    break
                                }
                            }

                            if (nextTask != null) {
                                executeQueuedTask(nextTask, onLog)
                            } else {
                                onLog("⏳ SCHEDULER BLOCKED: All pending tasks are waiting for unresolved parent dependencies or paused missions.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    onLog("🚨 SCHEDULER EXCEPTION: Encountered error in task dispatch cycle: ${e.localizedMessage}")
                }
                delay(15000) // check every 15 seconds
            }
        }
    }

    fun stopPollingScheduler() {
        _isSchedulerRunning.value = false
    }

    private suspend fun executeQueuedTask(task: TaskEntity, onLog: (String) -> Unit) {
        // Phase 12 Resource Throttling Check
        if (resourceManager.shouldThrottleTask(task)) {
            val metrics = resourceManager.currentMetrics.value
            onLog("⚡ RESOURCE WARNING: Throttling task '${task.title}' due to resource limitations (Battery: ${metrics.batteryLevel}%, CPU: ${metrics.cpuUsage}%, API: ${metrics.apiUsageCount}). Status set to BLOCKED.")
            val blockedTask = task.copy(
                status = "BLOCKED",
                updatedAt = System.currentTimeMillis(),
                executionLog = "Throttled by Resource Manager. Battery: ${metrics.batteryLevel}%, CPU: ${metrics.cpuUsage}%"
            )
            repository.updateQueueTask(blockedTask)
            return
        }

        // Phase 12 Confidence Consensus Check
        val consensusResult = consensusEngine.conductConsensusCheck(task)
        onLog("⚖️ CONSENSUS CHECK: ${consensusResult.reasoningSummary}")
        if (!consensusResult.consensusPassed) {
            onLog("⚠️ CONSENSUS FAILURE: Insufficient combined score (${String.format(java.util.Locale.US, "%.1f", consensusResult.combinedScore * 100.0)}% < 75%). Action postponed. Escalating task to BLOCKED status.")
            val blockedTask = task.copy(
                status = "BLOCKED",
                updatedAt = System.currentTimeMillis(),
                executionLog = "Postponed: Weak agent consensus. Score: ${String.format(java.util.Locale.US, "%.2f", consensusResult.combinedScore)}"
            )
            repository.updateQueueTask(blockedTask)
            return
        }

        // Phase 18: Policy-Based Autonomy Interception
        val lowerSkill = task.requiredSkill.lowercase(Locale.US)
        val capabilityName = when {
            lowerSkill.contains("trading") || lowerSkill.contains("crypto") || lowerSkill.contains("binance") -> "Binance Capability"
            lowerSkill.contains("content") || lowerSkill.contains("social") || lowerSkill.contains("write") || lowerSkill.contains("draft") || lowerSkill.contains("script") -> "Content Capability"
            lowerSkill.contains("youtube") || lowerSkill.contains("video") -> "YouTube Capability"
            lowerSkill.contains("freelance") || lowerSkill.contains("proposal") || lowerSkill.contains("gig") -> "Freelance Capability"
            else -> null
        }

        if (capabilityName != null) {
            val state = repository.getCapabilityStateByNameDirect(capabilityName)
            if (state != null) {
                val policy = state.authPolicy
                val needsApproval = when {
                    policy == "APPROVAL_REQUIRED" -> true
                    capabilityName == "Binance Capability" && policy == "RISK_LIMIT" -> {
                        val estimator = dynamicExponentialEstimator
                        val volatility = estimator?.currentVolatility ?: 0.015
                        val baseLimit = 1000.0
                        val adjustedLimit = when {
                            volatility > 0.05 -> baseLimit * 0.25
                            volatility > 0.03 -> baseLimit * 0.50
                            else -> baseLimit
                        }
                        if (task.estimatedBudget > adjustedLimit) {
                            onLog("⚠️ POLICY ENFORCEMENT (VOLATILITY-ADAPTIVE): Budget $${task.estimatedBudget} exceeds volatility-adjusted risk limit of $${adjustedLimit} (Current Volatility: ${String.format(java.util.Locale.US, "%.4f", volatility)} under regime '${estimator?.currentRegime ?: "RANGE"}').")
                            true
                        } else {
                            false
                        }
                    }
                    capabilityName == "YouTube Capability" && policy == "DRAFT_ONLY" && task.title.lowercase(Locale.US).contains("publish") -> {
                        onLog("⚠️ POLICY ENFORCEMENT: 'Draft Only' policy prevents publishing action for YouTube Capability.")
                        true
                    }
                    capabilityName == "YouTube Capability" && policy == "APPROVAL_BEFORE_PUBLISH" && task.title.lowercase(Locale.US).contains("publish") -> {
                        onLog("⚠️ POLICY ENFORCEMENT: 'Approval Before Publishing' is required for YouTube Capability.")
                        true
                    }
                    capabilityName == "Freelance Capability" && policy == "DRAFT_ONLY" && task.title.lowercase(Locale.US).contains("submit") -> {
                        onLog("⚠️ POLICY ENFORCEMENT: 'Draft Only' policy prevents proposal submission for Freelance Capability.")
                        true
                    }
                    capabilityName == "Freelance Capability" && policy == "APPROVAL_BEFORE_SUBMIT" && task.title.lowercase(Locale.US).contains("submit") -> {
                        onLog("⚠️ POLICY ENFORCEMENT: 'Approval Before Submission' is required for Freelance Capability.")
                        true
                    }
                    else -> false
                }

                if (needsApproval) {
                    onLog("🔒 POLICY SECURITY LOCK: Task '${task.title}' requires Human Approval under configured '$policy' policy. Moving task to BLOCKED status.")
                    val blockedTask = task.copy(
                        status = "BLOCKED",
                        updatedAt = System.currentTimeMillis(),
                        executionLog = "Blocked: Requires human authorization under policy '$policy'."
                    )
                    repository.updateQueueTask(blockedTask)
                    repository.insertChatMessage(
                        com.example.data.database.ChatMessage(
                            sender = "SECURITY WATCHDOG",
                            text = "🔒 **AUTHORIZATION REQUIRED:** Task **${task.title}** on **$capabilityName** was intercepted under policy **$policy**. Please review and authorize this action."
                        )
                    )
                    return
                }
            }
        }

        // Dynamic resource consumption during execution
        resourceManager.incrementApiUsage()
        val currentBattery = resourceManager.currentMetrics.value.batteryLevel
        if (currentBattery > 1) {
            resourceManager.updateBatteryLevel(currentBattery - 1)
        }

        onLog("🚀 DISPATCHER: Transitioning task '${task.title}' [QUEUED -> RUNNING]")
        
        // 1. Transition state to RUNNING
        val runningTask = task.copy(
            status = "RUNNING",
            updatedAt = System.currentTimeMillis()
        )
        repository.updateQueueTask(runningTask)
        recentCallsCount++

        // Insert initial TaskExecution record
        val executionId = repository.insertTaskExecution(
            TaskExecution(
                taskId = task.id,
                status = "RUNNING",
                systemDiagnostics = "Battery: ${_diagnostics.value.batteryPct}%, Net: ${_diagnostics.value.networkStatus}"
            )
        ).toInt()

        onLog("🤖 SCHEDULER: Delegating task to Agent Registry -> '${task.assignedAgent}' for competency '${task.requiredSkill}'")

        // 2. Map task and execute
        // Map TaskEntity to legacy AzaAgentTask for compatibility with planning engine
        val agentTask = com.example.data.database.AzaAgentTask(
            objectiveId = task.objectiveId,
            assignedAgent = task.assignedAgent,
            title = task.title,
            description = task.description,
            priority = task.priority,
            status = "RUNNING",
            requiredSkill = task.requiredSkill,
            estimatedBudget = task.estimatedBudget
        )

        val result = if (forceNextTaskFailure) {
            forceNextTaskFailure = false
            onLog("⚠️ SIMULATED PIPELINE FAULT: Forcing next task to fail for retry verification.")
            AgentResult(
                success = false,
                errorMessage = "Forced failure for verification of robust architectural retry handler.",
                executionLog = "Forced failure for verification of robust architectural retry handler.",
                performanceImpact = "Aborted."
            )
        } else {
            // Check if we can route this through the Capability Framework autonomously!
            val lowerSkill = task.requiredSkill.lowercase(Locale.US)
            val lowerDesc = task.description.lowercase(Locale.US)
            val lowerTitle = task.title.lowercase(Locale.US)

            val capabilityName = when {
                lowerSkill.contains("trading") || lowerSkill.contains("crypto") || lowerSkill.contains("binance") -> "Binance Capability"
                lowerSkill.contains("content") || lowerSkill.contains("social") || lowerSkill.contains("write") || lowerSkill.contains("draft") || lowerSkill.contains("script") -> "Content Capability"
                lowerSkill.contains("youtube") || lowerSkill.contains("video") -> "YouTube Capability"
                lowerSkill.contains("freelance") || lowerSkill.contains("proposal") || lowerSkill.contains("gig") -> "Freelance Capability"
                lowerSkill.contains("notification") || lowerSkill.contains("alert") || lowerSkill.contains("warning") || lowerSkill.contains("message") -> "Notification Capability"
                lowerSkill.contains("laptop") || lowerSkill.contains("sync") || lowerSkill.contains("documents") || lowerSkill.contains("pull") || lowerSkill.contains("push") -> "Laptop Agent Capability"
                else -> null
            }

            if (capabilityName != null) {
                // Determine action dynamically based on description and title analysis
                val action = when (capabilityName) {
                    "Binance Capability" -> when {
                        lowerDesc.contains("balance") || lowerTitle.contains("balance") -> "READ_BALANCE"
                        lowerDesc.contains("position") || lowerTitle.contains("position") -> "READ_POSITIONS"
                        lowerDesc.contains("order") || lowerTitle.contains("order") -> "READ_ORDERS"
                        lowerDesc.contains("market") || lowerDesc.contains("price") || lowerTitle.contains("market") -> "MARKET_INFO"
                        else -> "EXECUTE_PAPER_TRADE"
                    }
                    "Content Capability" -> when {
                        lowerDesc.contains("idea") || lowerTitle.contains("idea") -> "GENERATE_IDEAS"
                        lowerDesc.contains("draft") || lowerTitle.contains("draft") -> "CREATE_DRAFTS"
                        lowerDesc.contains("script") || lowerTitle.contains("script") -> "GENERATE_SCRIPTS"
                        lowerDesc.contains("caption") || lowerTitle.contains("caption") -> "PRODUCE_CAPTIONS"
                        lowerDesc.contains("hashtag") || lowerTitle.contains("hashtag") -> "GENERATE_HASHTAGS"
                        lowerDesc.contains("schedule") || lowerTitle.contains("schedule") -> "PUBLISHING_SCHEDULES"
                        else -> "BUILD_PIPELINE"
                    }
                    "YouTube Capability" -> when {
                        lowerDesc.contains("analytics") || lowerDesc.contains("metric") || lowerTitle.contains("analytics") -> "CHANNEL_ANALYTICS"
                        lowerDesc.contains("upload") || lowerDesc.contains("prepare") || lowerTitle.contains("upload") -> "UPLOAD_PREP"
                        lowerDesc.contains("metadata") || lowerDesc.contains("tag") || lowerTitle.contains("metadata") -> "METADATA_GEN"
                        lowerDesc.contains("thumbnail") || lowerTitle.contains("thumbnail") -> "THUMBNAIL_WORKFLOW"
                        lowerDesc.contains("playlist") || lowerTitle.contains("playlist") -> "PLAYLIST_ORG"
                        lowerDesc.contains("queue") || lowerTitle.contains("queue") -> "PUBLISH_QUEUE"
                        else -> "TRACK_PERFORMANCE"
                    }
                    "Freelance Capability" -> when {
                        lowerDesc.contains("monitor") || lowerDesc.contains("scan") || lowerTitle.contains("monitor") -> "MONITOR_OPPORTUNITIES"
                        lowerDesc.contains("match") || lowerTitle.contains("match") -> "MATCH_SKILLS"
                        lowerDesc.contains("rank") || lowerDesc.contains("score") || lowerTitle.contains("rank") -> "RANK_OPPORTUNITIES"
                        lowerDesc.contains("draft") || lowerDesc.contains("proposal") || lowerTitle.contains("draft") -> "DRAFT_PROPOSAL"
                        lowerDesc.contains("package") || lowerDesc.contains("application") || lowerTitle.contains("package") -> "BUILD_APPLICATION_PACKAGE"
                        lowerDesc.contains("track") || lowerTitle.contains("track") -> "TRACK_OPPORTUNITIES"
                        else -> "LEARN_OUTCOMES"
                    }
                    "Notification Capability" -> when {
                        lowerDesc.contains("follow") || lowerTitle.contains("follow") || lowerDesc.contains("check back") -> "FOLLOW_UP"
                        lowerDesc.contains("market") || lowerTitle.contains("market") || lowerDesc.contains("price") -> "MARKET_UPDATE"
                        lowerDesc.contains("trade") || lowerTitle.contains("trade") -> "TRADE_ALERT"
                        lowerDesc.contains("complete") || lowerTitle.contains("complete") -> "MISSION_COMPLETED"
                        lowerDesc.contains("decision") || lowerTitle.contains("decision") -> "EXEC_DECISION"
                        lowerDesc.contains("safety") || lowerDesc.contains("critical") || lowerTitle.contains("safety") -> "SAFETY_WARNING"
                        lowerDesc.contains("approve") || lowerDesc.contains("request") || lowerTitle.contains("approve") -> "APPROVAL_REQUEST"
                        lowerDesc.contains("fail") || lowerTitle.contains("fail") -> "MISSION_FAILURE"
                        lowerDesc.contains("resource") || lowerTitle.contains("resource") -> "RESOURCE_WARNING"
                        else -> "FOLLOW_UP"
                    }
                    "Laptop Agent Capability" -> when {
                        lowerDesc.contains("ping") || lowerTitle.contains("ping") -> "PING_HOST"
                        lowerDesc.contains("pull") || lowerDesc.contains("retrieve") || lowerTitle.contains("pull") || lowerTitle.contains("retrieve") -> "RETRIEVE_DOCUMENTS"
                        lowerDesc.contains("push") || lowerDesc.contains("exchange") || lowerTitle.contains("push") || lowerTitle.contains("exchange") -> "EXCHANGE_DATA"
                        lowerDesc.contains("status") || lowerTitle.contains("status") -> "GET_SYNC_STATUS"
                        lowerDesc.contains("download") || lowerTitle.contains("download") -> "DOWNLOAD_DOCUMENT"
                        else -> "RETRIEVE_DOCUMENTS"
                    }
                    else -> "EXECUTE"
                }

                val onThoughtLog: suspend (String) -> Unit = { thought -> onLog(thought) }
                onThoughtLog("🤖 AUTONOMOUS CAPABILITY SELECTION: Executive Planner parsed action requirement.")
                onThoughtLog("🔍 SEARCH REGISTRY: Matching skill '${task.requiredSkill}' -> Resolved capability: '$capabilityName', Action: '$action'")

                // Resolve target mission ID
                val objectiveObj = repository.getObjectiveByIdDirect(task.objectiveId)
                val missionId = objectiveObj?.missionId

                // Execute securely
                val capResult = capabilityRegistry.executeActionSecurely(
                    capabilityName = capabilityName,
                    action = action,
                    parameters = mapOf(
                        "topic" to task.title,
                        "gig_title" to task.title,
                        "symbol" to "BTC",
                        "side" to "BUY",
                        "amount" to 0.02,
                        "message" to task.description
                    ),
                    missionId = missionId,
                    onThoughtLog = onThoughtLog
                )

                if (capResult.success && capabilityName == "Laptop Agent Capability" && action == "RETRIEVE_DOCUMENTS") {
                    onThoughtLog("🧠 LEARNING ENGINE: Initiating learning and habit calibration from new synchronized documents...")
                    val docs = repository.getAllLaptopDocumentsDirect()
                    planningEngine.coordinator.learningEngine.learnFromLaptopHabits(docs)
                }

                AgentResult(
                    success = capResult.success,
                    errorMessage = capResult.errorMessage,
                    executionLog = if (capResult.success) {
                        "Successfully executed Action '$action' on Capability '$capabilityName'. Result data: ${capResult.outputData}"
                    } else {
                        "Capability execution failed: ${capResult.errorMessage}"
                    },
                    performanceImpact = if (capResult.success) "Optimized execution completed in ${capResult.durationMs}ms." else "Fault state encountered."
                )
            } else {
                try {
                    planningEngine.executeSkill(task.requiredSkill, agentTask) { thought ->
                        onLog(thought)
                    }
                } catch (e: Exception) {
                    AgentResult(
                        success = false,
                        executionLog = "Internal dispatcher failure: ${e.localizedMessage}",
                        performanceImpact = "Aborted."
                    )
                }
            }
        }

        // 3. Finalize state
        val finalStatus = if (result.success) {
            "COMPLETED"
        } else {
            if (task.retryCount + 1 < task.maxRetries) "RETRY" else "FAILED"
        }

        val completedTask = runningTask.copy(
            status = finalStatus,
            executionLog = result.executionLog,
            outputSummary = result.performanceImpact,
            retryCount = if (!result.success) task.retryCount + 1 else task.retryCount,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateQueueTask(completedTask)

        // Update TaskExecution record
        repository.updateTaskExecution(
            TaskExecution(
                id = executionId,
                taskId = task.id,
                finishedAt = System.currentTimeMillis(),
                status = if (result.success) "COMPLETED" else "FAILED",
                errorMessage = result.errorMessage,
                systemDiagnostics = "Battery: ${_diagnostics.value.batteryPct}%, Net: ${_diagnostics.value.networkStatus}",
                outcomeSummary = result.performanceImpact
            )
        )

        // 4. Update parent objective and mission progress
        val objective = repository.getObjectiveByIdDirect(task.objectiveId)
        if (objective != null) {
            val freshQueueTasks = repository.getQueueTasksForObjectiveDirect(objective.id)
            if (freshQueueTasks.isNotEmpty()) {
                val compCount = freshQueueTasks.count { it.status == "COMPLETED" || it.status == "REJECTED" }
                val totCount = freshQueueTasks.size
                val computedProgress = compCount.toFloat() / totCount.toFloat()
                
                val allDone = freshQueueTasks.all { it.status == "COMPLETED" || it.status == "REJECTED" || it.status == "FAILED" }
                val nextStatus = if (allDone) {
                    val anyFailed = freshQueueTasks.any { it.status == "FAILED" }
                    if (anyFailed) "FAILED" else "COMPLETED"
                } else {
                    "RUNNING"
                }
                
                repository.updateObjective(
                    objective.copy(
                        progress = computedProgress,
                        status = nextStatus,
                        currentTask = if (allDone) "Completed" else task.title,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            if (objective.missionId != null) {
                repository.updateMissionProgressAndStatus(objective.missionId)
            }
        }

        // 5. Broadcast notification to user tray and chat for completed follow-ups and high priority background tasks
        if (task.requiredSkill.contains("Notification", ignoreCase = true) || task.title.contains("follow", ignoreCase = true) || task.priority.equals("HIGH", ignoreCase = true)) {
            val title = if (task.title.contains("follow", ignoreCase = true)) "Lumina Follow-Up Completed" else "Task Finalized: ${task.title}"
            val message = if (result.success) {
                result.executionLog.ifBlank { result.performanceImpact.ifBlank { "Task '${task.title}' was completed successfully." } }
            } else {
                "Task '${task.title}' failed: ${result.errorMessage ?: "Unexpected error"}"
            }
            try {
                SystemNotificationHelper.showNotification(title, message)
            } catch (e: Exception) {
                onLog("⚠️ Notification Helper Warning: ${e.localizedMessage}")
            }
        }

        onLog("🏁 DISPATCHER: Task '${task.title}' finalized with status: $finalStatus [Output: ${result.performanceImpact}]")
    }
}
