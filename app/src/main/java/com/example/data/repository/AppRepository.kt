package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.Content as ApiContent
import com.example.data.api.GenerateContentRequest
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AppRepository(private val db: AppDatabase) {

    // DAOs
    private val portfolioDao = db.portfolioDao()
    private val contentDao = db.contentDao()
    private val gigDao = db.gigDao()
    private val transactionDao = db.transactionDao()
    private val accountLinkingDao = db.accountLinkingDao()
    private val chatDao = db.chatDao()
    private val channelDao = db.channelDao()
    private val azaGoalDao = db.azaGoalDao()
    private val executionRecordDao = db.executionRecordDao()
    private val azaLearningRecordDao = db.azaLearningRecordDao()
    val strategyDao = db.strategyDao()
    val experimentDao = db.experimentDao()
    val knowledgeDao = db.knowledgeDao()
    private val taskQueueDao = db.taskQueueDao()
    private val azaMissionDao = db.azaMissionDao()
    private val executiveDecisionDao = db.executiveDecisionDao()
    private val capabilityDao = db.capabilityDao()
    private val laptopDao = db.laptopDao()
    private val binanceDao = db.binanceDao()
    val marketIntelligenceDao = db.marketIntelligenceDao()
    val portfolioPositionDao = db.portfolioPositionDao()
 
    // Observables (Flows)
    val allPositions: Flow<List<BinancePositionEntity>> = portfolioPositionDao.getAllPositionsFlow()
    val allReviews: Flow<List<PositionReviewEntity>> = portfolioPositionDao.getAllReviewsFlow()
    val allAlerts: Flow<List<PositionAlertEntity>> = portfolioPositionDao.getAllAlertsFlow()
    val marketOpportunities: Flow<List<com.example.data.database.OpportunityEntity>> = marketIntelligenceDao.getAllOpportunitiesFlow()
    val tradeProposals: Flow<List<com.example.data.database.TradeProposalEntity>> = marketIntelligenceDao.getAllProposalsFlow()
    val portfolioAssets: Flow<List<PortfolioAsset>> = portfolioDao.getAllAssets()
    val contentItems: Flow<List<ContentItem>> = contentDao.getAllContent()
    val freelanceGigs: Flow<List<FreelanceGig>> = gigDao.getAllGigs()
    val transactions: Flow<List<TransactionRecord>> = transactionDao.getAllTransactions()
    val accountInfo: Flow<AccountLinking?> = accountLinkingDao.getAccountInfoFlow()
    val chatMessages: Flow<List<ChatMessage>> = chatDao.getAllMessagesFlow()
    val azaChannels: Flow<List<AzaChannel>> = channelDao.getAllChannelsFlow()
    val activeObjective: Flow<AzaObjective?> = azaGoalDao.getActiveObjectiveFlow()
    val pendingApprovalTasks: Flow<List<AzaAgentTask>> = azaGoalDao.getPendingApprovalTasksFlow()
    val executionRecords: Flow<List<ExecutionRecord>> = executionRecordDao.getAllExecutionRecordsFlow()
    val learningRecords: Flow<List<AzaLearningRecord>> = azaLearningRecordDao.getAllLearningRecordsFlow()
    val strategies: Flow<List<StrategyEntity>> = strategyDao.getAllStrategiesFlow()
    val backtestReports: Flow<List<StrategyBacktestReport>> = strategyDao.getAllBacktestReportsFlow()
    val optimizationRecords: Flow<List<StrategyOptimizationRecord>> = strategyDao.getAllOptimizationRecordsFlow()
    val tradeAuditLogs: Flow<List<TradeAuditLog>> = strategyDao.getAllTradeAuditLogsFlow()
    val exportedReports: Flow<List<ExportedReportHistory>> = strategyDao.getAllExportedReportsFlow()
    val experiments: Flow<List<Experiment>> = experimentDao.getAllExperimentsFlow()
    val allExperimentTrades: Flow<List<ExperimentTrade>> = experimentDao.getAllExperimentTradesFlow()
    val knowledgeEntries: Flow<List<KnowledgeEntry>> = knowledgeDao.getAllKnowledgeFlow()
    val skillLibrary: Flow<List<SkillEntry>> = knowledgeDao.getAllSkillsFlow()
    val allQueueTasks: Flow<List<TaskEntity>> = taskQueueDao.getAllTasksFlow()
    val allMissions: Flow<List<AzaMission>> = azaMissionDao.getAllMissionsFlow()
    val executiveDecisions: Flow<List<com.example.data.database.ExecutiveDecisionRecord>> = executiveDecisionDao.getAllDecisionsFlow()
    val capabilityStates: Flow<List<CapabilityStateEntity>> = capabilityDao.getAllCapabilityStatesFlow()
    val capabilityAuditLogs: Flow<List<CapabilityAuditLog>> = capabilityDao.getAllAuditLogsFlow()
    val laptopDocuments: Flow<List<LaptopDocumentEntity>> = laptopDao.getAllDocumentsFlow()
    val laptopExchangeHistory: Flow<List<LaptopExchangeRecord>> = laptopDao.getAllExchangeRecordsFlow()
    val binanceOrders: Flow<List<BinanceOrderEntity>> = binanceDao.getAllBinanceOrdersFlow()
    val binanceTrades: Flow<List<BinanceTradeEntity>> = binanceDao.getAllBinanceTradesFlow()
    
    fun getTasksForObjective(objectiveId: Int): Flow<List<AzaAgentTask>> = azaGoalDao.getTasksForObjectiveFlow(objectiveId)

    suspend fun getCapabilityStateByNameDirect(name: String): CapabilityStateEntity? = withContext(Dispatchers.IO) {
        capabilityDao.getCapabilityStateByName(name)
    }

    suspend fun getAllCapabilityStatesDirect(): List<CapabilityStateEntity> = withContext(Dispatchers.IO) {
        capabilityDao.getAllCapabilityStates()
    }

    suspend fun insertOrUpdateCapabilityState(state: CapabilityStateEntity) = withContext(Dispatchers.IO) {
        capabilityDao.insertOrUpdateCapabilityState(state)
    }

    suspend fun updateCapabilityState(state: CapabilityStateEntity) = withContext(Dispatchers.IO) {
        capabilityDao.updateCapabilityState(state)
    }

    suspend fun insertCapabilityAuditLog(log: CapabilityAuditLog): Long = withContext(Dispatchers.IO) {
        capabilityDao.insertAuditLog(log)
    }

    suspend fun clearCapabilityAuditLogs() = withContext(Dispatchers.IO) {
        capabilityDao.clearAllAuditLogs()
    }

    // Binance operations
    suspend fun getAllBinanceOrdersDirect(): List<BinanceOrderEntity> = withContext(Dispatchers.IO) {
        binanceDao.getAllBinanceOrdersDirect()
    }

    suspend fun insertBinanceOrders(orders: List<BinanceOrderEntity>) = withContext(Dispatchers.IO) {
        binanceDao.insertBinanceOrders(orders)
    }

    suspend fun clearAllBinanceOrders() = withContext(Dispatchers.IO) {
        binanceDao.clearAllBinanceOrders()
    }

    suspend fun getAllBinanceTradesDirect(): List<BinanceTradeEntity> = withContext(Dispatchers.IO) {
        binanceDao.getAllBinanceTradesDirect()
    }

    suspend fun insertBinanceTrades(trades: List<BinanceTradeEntity>) = withContext(Dispatchers.IO) {
        binanceDao.insertBinanceTrades(trades)
    }

    suspend fun clearAllBinanceTrades() = withContext(Dispatchers.IO) {
        binanceDao.clearAllBinanceTrades()
    }

    suspend fun getAllLaptopDocumentsDirect(): List<LaptopDocumentEntity> = withContext(Dispatchers.IO) {
        laptopDao.getAllDocumentsDirect()
    }

    suspend fun insertLaptopDocuments(documents: List<LaptopDocumentEntity>) = withContext(Dispatchers.IO) {
        laptopDao.insertDocuments(documents)
    }

    suspend fun clearAllLaptopDocuments() = withContext(Dispatchers.IO) {
        laptopDao.clearAllDocuments()
    }

    suspend fun getAllLaptopExchangeRecordsDirect(): List<LaptopExchangeRecord> = withContext(Dispatchers.IO) {
        laptopDao.getAllExchangeRecordsDirect()
    }

    suspend fun insertLaptopExchangeRecord(record: LaptopExchangeRecord): Long = withContext(Dispatchers.IO) {
        laptopDao.insertExchangeRecord(record)
    }

    suspend fun clearAllLaptopExchangeHistory() = withContext(Dispatchers.IO) {
        laptopDao.clearAllExchangeHistory()
    }
    fun getTradesForExperiment(experimentId: Int): Flow<List<ExperimentTrade>> = experimentDao.getTradesForExperimentFlow(experimentId)

    suspend fun insertExecutionRecord(record: ExecutionRecord) = withContext(Dispatchers.IO) {
        executionRecordDao.insertExecutionRecord(record)
    }

    suspend fun clearExecutionHistory() = withContext(Dispatchers.IO) {
        executionRecordDao.clearAllExecutionRecords()
    }

    suspend fun insertLearningRecord(record: AzaLearningRecord) = withContext(Dispatchers.IO) {
        azaLearningRecordDao.insertLearningRecord(record)
    }

    suspend fun clearLearningRecords() = withContext(Dispatchers.IO) {
        azaLearningRecordDao.clearAllLearningRecords()
    }

    suspend fun getActiveStrategyDirect(): StrategyEntity? = withContext(Dispatchers.IO) {
        strategyDao.getActiveStrategyDirect()
    }

    suspend fun getAllStrategiesDirect(): List<StrategyEntity> = withContext(Dispatchers.IO) {
        strategyDao.getAllStrategiesDirect()
    }

    suspend fun getStrategyByNameDirect(name: String): StrategyEntity? = withContext(Dispatchers.IO) {
        strategyDao.getStrategyByNameDirect(name)
    }

    suspend fun insertStrategy(strategy: StrategyEntity) = withContext(Dispatchers.IO) {
        strategyDao.insertStrategy(strategy)
    }

    suspend fun insertStrategies(strategies: List<StrategyEntity>) = withContext(Dispatchers.IO) {
        strategyDao.insertStrategies(strategies)
    }

    suspend fun updateStrategy(strategy: StrategyEntity) = withContext(Dispatchers.IO) {
        strategyDao.updateStrategy(strategy)
    }

    suspend fun setActiveStrategy(strategyName: String) = withContext(Dispatchers.IO) {
        strategyDao.setActiveStrategy(strategyName)
    }

    suspend fun insertBacktestReport(report: StrategyBacktestReport): Long = withContext(Dispatchers.IO) {
        strategyDao.insertBacktestReport(report)
    }

    suspend fun clearAllBacktests() = withContext(Dispatchers.IO) {
        strategyDao.clearAllBacktests()
    }

    suspend fun insertOptimizationRecord(record: StrategyOptimizationRecord) = withContext(Dispatchers.IO) {
        strategyDao.insertOptimizationRecord(record)
    }

    suspend fun clearAllOptimizationRecords() = withContext(Dispatchers.IO) {
        strategyDao.clearAllOptimizationRecords()
    }

    suspend fun insertTradeAuditLog(log: TradeAuditLog) = withContext(Dispatchers.IO) {
        strategyDao.insertTradeAuditLog(log)
    }

    suspend fun clearAllTradeAuditLogs() = withContext(Dispatchers.IO) {
        strategyDao.clearAllTradeAuditLogs()
    }

    suspend fun insertExportedReport(report: ExportedReportHistory) = withContext(Dispatchers.IO) {
        strategyDao.insertExportedReport(report)
    }

    suspend fun deleteExportedReportById(id: Int) = withContext(Dispatchers.IO) {
        strategyDao.deleteExportedReportById(id)
    }

    suspend fun clearAllExportedReports() = withContext(Dispatchers.IO) {
        strategyDao.clearAllExportedReports()
    }

    suspend fun getTradesForExperimentDirect(experimentId: Int): List<ExperimentTrade> = withContext(Dispatchers.IO) {
        experimentDao.getTradesForExperimentDirect(experimentId)
    }

    suspend fun getAllExperimentTradesDirect(): List<ExperimentTrade> = withContext(Dispatchers.IO) {
        experimentDao.getAllExperimentTradesDirect()
    }

    suspend fun insertExperiment(experiment: Experiment): Long = withContext(Dispatchers.IO) {
        experimentDao.insertExperiment(experiment)
    }

    suspend fun updateExperiment(experiment: Experiment) = withContext(Dispatchers.IO) {
        experimentDao.updateExperiment(experiment)
    }

    suspend fun getExperimentByIdDirect(id: Int): Experiment? = withContext(Dispatchers.IO) {
        experimentDao.getExperimentByIdDirect(id)
    }

    suspend fun insertExperimentTrade(trade: ExperimentTrade): Long = withContext(Dispatchers.IO) {
        experimentDao.insertExperimentTrade(trade)
    }

    suspend fun clearAllExperiments() = withContext(Dispatchers.IO) {
        experimentDao.clearAllExperiments()
    }

    suspend fun clearAllExperimentTrades() = withContext(Dispatchers.IO) {
        experimentDao.clearAllExperimentTrades()
    }

    // SQLite writes
    suspend fun updateChannel(channel: AzaChannel) = withContext(Dispatchers.IO) {
        channelDao.insertChannel(channel)
    }

    suspend fun getChannelByPlatform(platform: String): AzaChannel? = withContext(Dispatchers.IO) {
        channelDao.getChannelByPlatform(platform)
    }

    suspend fun getAllChannelsDirect(): List<AzaChannel> = withContext(Dispatchers.IO) {
        channelDao.getAllChannelsDirect()
    }

    suspend fun insertChatMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun clearChatMessages() = withContext(Dispatchers.IO) {
        chatDao.deleteAllMessages()
    }

    suspend fun getAssetBySymbol(symbol: String): PortfolioAsset? = withContext(Dispatchers.IO) {
        portfolioDao.getAssetBySymbol(symbol)
    }

    suspend fun insertAsset(asset: PortfolioAsset) = withContext(Dispatchers.IO) {
        portfolioDao.insertAsset(asset)
    }

    suspend fun updateAsset(asset: PortfolioAsset) = withContext(Dispatchers.IO) {
        portfolioDao.updateAsset(asset)
    }

    suspend fun removeAsset(asset: PortfolioAsset) = withContext(Dispatchers.IO) {
        portfolioDao.deleteAsset(asset)
    }

    suspend fun insertContent(item: ContentItem) = withContext(Dispatchers.IO) {
        contentDao.insertContent(item)
    }

    suspend fun updateContent(item: ContentItem) = withContext(Dispatchers.IO) {
        contentDao.updateContent(item)
    }

    suspend fun deleteContentById(id: Int) = withContext(Dispatchers.IO) {
        contentDao.deleteContentById(id)
    }

    suspend fun updateGig(gig: FreelanceGig) = withContext(Dispatchers.IO) {
        gigDao.updateGig(gig)
    }

    suspend fun insertGig(gig: FreelanceGig) = withContext(Dispatchers.IO) {
        gigDao.insertGig(gig)
    }

    suspend fun insertTransaction(record: TransactionRecord) = withContext(Dispatchers.IO) {
        transactionDao.insertTransaction(record)
    }

    suspend fun updateAccountLinking(info: AccountLinking) = withContext(Dispatchers.IO) {
        accountLinkingDao.insertAccountInfo(info)
    }

    suspend fun getAccountInfoDirect(): AccountLinking? = withContext(Dispatchers.IO) {
        accountLinkingDao.getAccountInfoDirect()
    }

    // --- AI/Gemini Methods ---

    private suspend fun generateContentWithFallback(
        apiKey: String,
        request: GenerateContentRequest
    ): String {
        val models = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-flash-latest",
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview",
            "gemini-3.1-flash-lite-preview"
        )
        var lastException: Exception? = null
        
        for (model in models) {
            var currentDelay = 1000L
            for (attempt in 1..2) {
                try {
                    val response = RetrofitClient.service.generateContent(model, apiKey, request)
                    val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (text != null) {
                        return text
                    }
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < 2) {
                        kotlinx.coroutines.delay(currentDelay)
                        currentDelay *= 2
                    }
                }
            }
        }
        throw lastException ?: Exception("All Gemini models failed to generate content.")
    }

    suspend fun getAiMarketAnalysis(symbol: String, type: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("placeholder", ignoreCase = true)) {
            return@withContext getSimulatedMarketAnalysis(symbol, type)
        }

        val prompt = """
            Perform a professional market analysis for $symbol ($type).
            Provide a current market trend (bullish, bearish, or sideways), short-term momentum, 
            support & resistance key numbers, and a clear tactical buy/sell/hold recommended action step.
            Format it with clear markdown headings, bullet points, and highly professional advice. Keep it concise.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(ApiContent(parts = listOf(Part(text = prompt))))
        )

        try {
            generateContentWithFallback(apiKey, request)
        } catch (e: Exception) {
            getSimulatedMarketAnalysis(symbol, type)
        }
    }

    suspend fun getAiContentScript(title: String, platform: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("placeholder", ignoreCase = true)) {
            return@withContext getSimulatedContentScript(title, platform)
        }

        val prompt = """
            Create an ultra-engaging, viral social media video production script/concept outline.
            - Title: $title
            - Target Media Platform: $platform (YouTube, Instagram, or TikTok)
            Include:
            1. An attention-grabbing 3-second hook configuration.
            2. High-retention pacing tips, audio suggestions, and rapid-cut visual overlays.
            3. A complete structured outline (Intro, Main discussion points, CTA Outro).
            Format with bold bullet lists and sleek sectional dividers. Keep it highly practical.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(ApiContent(parts = listOf(Part(text = prompt))))
        )

        try {
            generateContentWithFallback(apiKey, request)
        } catch (e: Exception) {
            getSimulatedContentScript(title, platform)
        }
    }

    suspend fun getAiProposalForGig(gigTitle: String, gigDescription: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("placeholder", ignoreCase = true)) {
            return@withContext getSimulatedGigProposal(gigTitle)
        }

        val prompt = """
            You are an elite, highly persuasive freelance consultant applying for this job gig:
            - Gig Title: $gigTitle
            - Client Job Description: $gigDescription
            Write a customized, compelling, high-converting freelance application introduction and technical proposal.
            Highlight precise problem diagnosis, a tailored implementation phase timeline, relevant credentials, and collaborative communication style. 
            Keep it strictly professional but charismatic, under 250 words.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(ApiContent(parts = listOf(Part(text = prompt))))
        )

        try {
            generateContentWithFallback(apiKey, request)
        } catch (e: Exception) {
            getSimulatedGigProposal(gigTitle)
        }
    }

    // --- Elegant Local Simulation Engines ---

    private fun getSimulatedMarketAnalysis(symbol: String, type: String): String {
        return """
            ### 📈 AI MARKET REPORT: $symbol
            
            *Analyzing active technical metrics via AI-Sentry offline simulation model...*
            
            #### 1. Technical Indicators
            - **Trend Status:** **BULLISH DETECTED** (Consistent 20-day moving average separation)
            - **Relative Strength Index (RSI):** **64.2** (Strong upward support, healthy market volume)
            - **MACD Divergence:** Positive crossover on 4H horizon indicating high buy momentum.
            
            #### 2. Key Target Keyrates (USD)
            - **Support Levels:** ${if (type == "CRYPTO") "92% current price support floor." else "95.8% current resistance rebound."}
            - **Target Ceiling:** Estimated +8.4% growth on breakthrough resistances.
            
            #### 3. Strategic Action Plan
            > **RECOMMENDATION: HOLD & DCA (Dollar-Cost Average)**
            > Market dynamics indicate persistent technical support. Refrain from immediate peak selling. Gradually accumulate during minor pullback zones.
        """.trimIndent()
    }

    private fun getSimulatedContentScript(title: String, platform: String): String {
        return """
            ### 🎬 AI VIRAL PLANNER: $platform
            
            **Concept:** *"$title"*
            
            #### ⚡ Phase 1: 3-Second Hook Selection
            - **Audio:** Quick synth rise with a bass drop.
            - **On-Screen Visual:** Zoom of a smartphone screen showing green candlesticks, overlay text: *"Stop scrolling if you want to understand how this code works."*
            - **Spoken Text:** *"Here is the absolute simplest way to connect Gemini AI into your apps..."*
            
            #### 📦 Phase 2: Core Body Segments (Retention Target: >60%)
            1. **The Core Secret (0:15):** Illustrate how direct REST endpoints require no complex Gradle configuration. Let the visual overlay list the Retrofit builder pattern.
            2. **Common Trap (0:30):** Show a dramatic red 'X' graphic. Highlight that hardcoding API keys leads to instant GitHub leakage warnings.
            3. **The Practical Fix (0:45):** Reveal the secure `.env` variables reading from local systems.
            
            #### 📣 Phase 3: High-Converting CTA (Call to Action)
            - **Action:** Point downward. Show a stylized subscribe button emerging on screen.
            - **Spoken Text:** *"I dropped the entire modular codebase template in my link in bio. Go clone it, save this reel, and double tap!"*
        """.trimIndent()
    }

    private fun getSimulatedGigProposal(gigTitle: String): String {
        return """
            Hi there,
            
            I reviewed your listing for **"$gigTitle"**, and I am confident that I can deliver an exceptional solution tailored specifically to your project requirements.
            
            #### 🛠 Proposed Architecture & Solution
            1. **Modular Scope Integration:** I will set up local Room persistent models to cache operations and keep response times under 200ms.
            2. **Security-First Secret Management:** I'll leverage properties-injected build configurations to make sure credential linking remains safe from reverse compilation.
            3. **Modern Jetpack UI:** The views will feature fully dynamic Material 3 layouts that scale flawlessly from phones to tablets.
            
            #### ⏱ Projected Timeline
            - **Phase 1 (Requirements & Architecture):** 24-48 Hours.
            - **Phase 2 (Core logic & AI connection):** 3 Business Days.
            - **Phase 3 (Testing & Handoff):** 24 Hours.
            
            I would love to establish a quick call or chat to discuss technical parameters further. Thank you for your consideration!
            
            Best regards,
            Your Elite AI Consultant
        """.trimIndent()
    }

    suspend fun getAzaResponse(systemInstructions: String, prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("placeholder", ignoreCase = true)) {
            throw Exception("Local simulator fallback")
        }

        val fullPrompt = "$systemInstructions\n\nUser Message: \"$prompt\"\n\nAza response:"
        val req = GenerateContentRequest(
            contents = listOf(ApiContent(parts = listOf(Part(text = fullPrompt))))
        )

        generateContentWithFallback(apiKey, req)
    }

    // --- Goal-Driven Autonomous Architecture Persistence & Decomposition ---

    suspend fun getActiveObjectiveDirect(): AzaObjective? = withContext(Dispatchers.IO) {
        azaGoalDao.getActiveObjectiveDirect()
    }

    suspend fun getObjectiveByIdDirect(objectiveId: Int): AzaObjective? = withContext(Dispatchers.IO) {
        azaGoalDao.getObjectiveByIdDirect(objectiveId)
    }

    suspend fun getObjectivesForMissionDirect(missionId: Int): List<AzaObjective> = withContext(Dispatchers.IO) {
        azaGoalDao.getObjectivesForMissionDirect(missionId)
    }
    
    suspend fun getTasksForObjectiveDirect(objectiveId: Int): List<AzaAgentTask> = withContext(Dispatchers.IO) {
        azaGoalDao.getTasksForObjectiveDirect(objectiveId)
    }

    suspend fun insertObjective(objective: AzaObjective): Long = withContext(Dispatchers.IO) {
        azaGoalDao.insertObjective(objective)
    }

    suspend fun updateObjective(objective: AzaObjective) = withContext(Dispatchers.IO) {
        azaGoalDao.updateObjective(objective)
    }

    suspend fun updateTask(task: AzaAgentTask) = withContext(Dispatchers.IO) {
        azaGoalDao.updateTask(task)
    }

    suspend fun insertTasks(tasks: List<AzaAgentTask>) = withContext(Dispatchers.IO) {
        azaGoalDao.insertTasks(tasks)
    }

    suspend fun clearGoalSystem() = withContext(Dispatchers.IO) {
        azaGoalDao.clearAllObjectives()
        azaGoalDao.clearAllTasks()
        azaMissionDao.clearAllMissions()
        taskQueueDao.clearAllTasks()
    }

    suspend fun decomposeObjectiveToTasks(objective: AzaObjective): List<AzaAgentTask> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val title = objective.title
        
        var generatedText = ""
        val hasRealKey = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY" && !apiKey.contains("placeholder", ignoreCase = true)
        
        if (hasRealKey) {
            val prompt = """
                You are Aza, an advanced autonomous task planning system. Decompose the user's high-level objective into exactly 3 or 4 practical specialized agent tasks.
                Your objective is: "$title"
                
                For each task, define which specialized agent is best suited to execute it (e.g., "Crypto Trading Agent", "Social Content Director", "Freelance Proposal Writer", or "Compliance & Yield Auditor").
                
                You MUST return exactly the tasks as lines. Do not return any other text, greetings, codeblocks, or markdowns.
                Format each task line exactly like this with double pipe delimiters:
                Agent Name || Task Title || Task Long Description || Priority (HIGH, MEDIUM, or LOW) || Estimated Budget ($)
                
                Example lines:
                Crypto Trading Agent || Secure Spot Accumulation || Research EMA indicators for BTC and purchase safe quantity under user-approval guidelines || HIGH || 250.0
                Social Content Director || Write Viral TikTok Script || Generate a high-retention vertical marketing concept about Jetpack Compose code || MEDIUM || 0.0
                Compliance & Yield Auditor || Audit Multi-Asset Risks || Perform secondary portfolio balance verification and logging || LOW || 0.0
            """.trimIndent()
            
            val request = GenerateContentRequest(
                contents = listOf(ApiContent(parts = listOf(Part(text = prompt))))
            )
            
            try {
                generatedText = generateContentWithFallback(apiKey, request)
            } catch (e: Exception) {
                // fall back to empty/simulated
            }
        }
        
        val tasks = mutableListOf<AzaAgentTask>()
        if (generatedText.isNotBlank()) {
            val lines = generatedText.split("\n")
            for (line in lines) {
                if (line.isBlank() || !line.contains("||")) continue
                try {
                    val parts = line.split("||")
                    if (parts.size >= 4) {
                        val agent = parts[0].trim()
                        val taskTitle = parts[1].trim()
                        val taskDesc = parts[2].trim()
                        val priorityVal = parts[3].trim().uppercase()
                        val budgetVal = if (parts.size >= 5) parts[4].trim().replace("$", "").toDoubleOrNull() ?: 0.0 else 0.0
                        
                        tasks.add(
                            AzaAgentTask(
                                objectiveId = objective.id,
                                assignedAgent = agent,
                                title = taskTitle,
                                description = taskDesc,
                                priority = if (priorityVal == "HIGH" || priorityVal == "MEDIUM" || priorityVal == "LOW") priorityVal else "MEDIUM",
                                status = "PENDING_APPROVAL",
                                estimatedBudget = budgetVal
                            )
                        )
                    }
                } catch (e: Exception) {
                    // skip malformed lines
                }
            }
        }
        
        if (tasks.isEmpty()) {
            val lowerObj = title.lowercase()
            
            val containsCrypto = lowerObj.contains("crypto") || lowerObj.contains("btc") || lowerObj.contains("eth") || lowerObj.contains("trading") || lowerObj.contains("market") || lowerObj.contains("portfolio") || lowerObj.contains("withdraw") || lowerObj.contains("depos")
            val containsContent = lowerObj.contains("video") || lowerObj.contains("script") || lowerObj.contains("youtube") || lowerObj.contains("tiktok") || lowerObj.contains("instagram") || lowerObj.contains("social") || lowerObj.contains("reel") || lowerObj.contains("concept") || lowerObj.contains("outline")
            val containsGig = lowerObj.contains("gig") || lowerObj.contains("proposal") || lowerObj.contains("work") || lowerObj.contains("client") || lowerObj.contains("freelance") || lowerObj.contains("hire") || lowerObj.contains("contract")
            
            if (containsCrypto || (!containsContent && !containsGig)) {
                tasks.add(
                    AzaAgentTask(
                        objectiveId = objective.id,
                        assignedAgent = "Crypto Trading Agent",
                        title = "Verify Market Technical Indicators",
                        description = "Analyze Binance/Coinbase technical charts and EMA crossovers to lock-in strategic buy-side entries.",
                        priority = "HIGH",
                        status = "PENDING_APPROVAL",
                        estimatedBudget = 150.0
                    )
                )
            }
            if (containsContent || (!containsCrypto && !containsGig)) {
                tasks.add(
                    AzaAgentTask(
                        objectiveId = objective.id,
                        assignedAgent = "Social Content Director",
                        title = "Generate High-Viral Visual Outline",
                        description = "Scrape trending topics on Jetpack Compose aesthetics and construct a structured TikTok audio-visual script blueprint.",
                        priority = "MEDIUM",
                        status = "PENDING_APPROVAL",
                        estimatedBudget = 0.0
                    )
                )
            }
            if (containsGig) {
                tasks.add(
                    AzaAgentTask(
                        objectiveId = objective.id,
                        assignedAgent = "Freelance Proposal Writer",
                        title = "Formulate Elite Technical Pitch",
                        description = "Match account skillset models and synthesize a highly persuasive pitch structure targeting the available client gig.",
                        priority = "MEDIUM",
                        status = "PENDING_APPROVAL",
                        estimatedBudget = 0.0
                    )
                )
            }
            
            tasks.add(
                AzaAgentTask(
                    objectiveId = objective.id,
                    assignedAgent = "Compliance & Yield Auditor",
                    title = "Safeguard & Audit Outflow Yields",
                    description = "Verify total transaction record histories against cash wallet escrows to optimize final yields and secure balance limits.",
                    priority = "LOW",
                    status = "PENDING_APPROVAL",
                    estimatedBudget = 0.0
                )
            )
        }
        tasks
    }

    // Knowledge Base & Skill Library API
    suspend fun getAllKnowledgeDirect(): List<KnowledgeEntry> = withContext(Dispatchers.IO) {
        knowledgeDao.getAllKnowledgeDirect()
    }

    suspend fun getKnowledgeById(id: Int): KnowledgeEntry? = withContext(Dispatchers.IO) {
        knowledgeDao.getKnowledgeById(id)
    }

    suspend fun insertKnowledge(entry: KnowledgeEntry): Long = withContext(Dispatchers.IO) {
        knowledgeDao.insertKnowledge(entry)
    }

    suspend fun updateKnowledge(entry: KnowledgeEntry) = withContext(Dispatchers.IO) {
        knowledgeDao.updateKnowledge(entry)
    }

    suspend fun clearAllKnowledge() = withContext(Dispatchers.IO) {
        knowledgeDao.clearAllKnowledge()
    }

    suspend fun getAllSkillsDirect(): List<SkillEntry> = withContext(Dispatchers.IO) {
        knowledgeDao.getAllSkillsDirect()
    }

    suspend fun insertSkill(skill: SkillEntry): Long = withContext(Dispatchers.IO) {
        knowledgeDao.insertSkill(skill)
    }

    suspend fun updateSkill(skill: SkillEntry) = withContext(Dispatchers.IO) {
        knowledgeDao.updateSkill(skill)
    }

    suspend fun clearAllSkills() = withContext(Dispatchers.IO) {
        knowledgeDao.clearAllSkills()
    }

    // Task Queue methods
    suspend fun getQueueTasksByStatus(status: String): List<TaskEntity> = withContext(Dispatchers.IO) {
        taskQueueDao.getTasksByStatusDirect(status)
    }

    suspend fun getQueueTaskById(id: Int): TaskEntity? = withContext(Dispatchers.IO) {
        taskQueueDao.getTaskById(id)
    }

    suspend fun insertQueueTask(task: TaskEntity): Long = withContext(Dispatchers.IO) {
        taskQueueDao.insertTask(task)
    }

    suspend fun insertQueueTasks(tasks: List<TaskEntity>) = withContext(Dispatchers.IO) {
        taskQueueDao.insertTasks(tasks)
    }

    suspend fun updateQueueTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        taskQueueDao.updateTask(task)
    }

    suspend fun getQueueTasksForObjectiveDirect(objectiveId: Int): List<TaskEntity> = withContext(Dispatchers.IO) {
        taskQueueDao.getTasksByObjectiveDirect(objectiveId)
    }

    suspend fun updateMissionProgressAndStatus(missionId: Int) = withContext(Dispatchers.IO) {
        val objectives = azaGoalDao.getObjectivesForMissionDirect(missionId)
        if (objectives.isEmpty()) return@withContext

        var totalTasks = 0
        var completedTasks = 0
        var latestTaskName = "None"
        var hasFailed = false
        var hasRunning = false

        for (obj in objectives) {
            val tasks = taskQueueDao.getTasksByObjectiveDirect(obj.id)
            totalTasks += tasks.size
            completedTasks += tasks.count { it.status == "COMPLETED" || it.status == "REJECTED" }
            if (tasks.any { it.status == "FAILED" }) {
                hasFailed = true
            }
            if (tasks.any { it.status == "RUNNING" }) {
                hasRunning = true
            }
            val lastActiveTask = tasks.lastOrNull { it.status == "RUNNING" || it.status == "COMPLETED" }
            if (lastActiveTask != null) {
                latestTaskName = lastActiveTask.title
            }
        }

        val computedProgress = if (totalTasks > 0) {
            (completedTasks.toFloat() / totalTasks.toFloat()) * 100f
        } else {
            0f
        }

        val mission = azaMissionDao.getMissionById(missionId)
        if (mission != null) {
            val nextStatus = when {
                computedProgress >= 100f -> "Completed"
                hasFailed -> "Failed"
                hasRunning -> "Active"
                else -> mission.status
            }

            val updatedMission = mission.copy(
                progress = computedProgress,
                status = nextStatus,
                currentTask = if (computedProgress >= 100f) "Completed" else latestTaskName,
                lastActivity = System.currentTimeMillis()
            )

            val isFinalState = nextStatus == "Completed" || nextStatus == "Failed"
            val shouldPerformReview = isFinalState && !mission.hasReview
            
            val reviewedMission = if (shouldPerformReview) {
                val delayingTasksList = mutableListOf<String>()
                val agentSuccessCounts = mutableMapOf<String, Int>()
                val mistakesList = mutableListOf<String>()
                
                for (obj in objectives) {
                    val tasks = taskQueueDao.getTasksByObjectiveDirect(obj.id)
                    for (t in tasks) {
                        if (t.retryCount > 0) {
                            delayingTasksList.add("${t.title} (${t.retryCount} retries)")
                        }
                        if (t.status == "COMPLETED") {
                            agentSuccessCounts[t.assignedAgent] = (agentSuccessCounts[t.assignedAgent] ?: 0) + 1
                        }
                        if (t.status == "FAILED") {
                            mistakesList.add("Task '${t.title}' failed: ${t.executionLog}")
                        } else if (t.retryCount > 0) {
                            mistakesList.add("Task '${t.title}' required ${t.retryCount} retries.")
                        }
                    }
                }
                
                val bestAgentStr = agentSuccessCounts.maxByOrNull { it.value }?.key ?: "None"
                val delayingTasksStr = if (delayingTasksList.isNotEmpty()) delayingTasksList.joinToString(", ") else "None"
                val mistakesStr = if (mistakesList.isNotEmpty()) mistakesList.joinToString("; ") else "No mistakes detected during this mission execution cycle."
                val lessonsLearnedStr = when {
                    nextStatus == "Failed" -> "Prioritize risk validation constraints early, verify API dependencies and network routes before initiating live asset pipelines."
                    delayingTasksList.isNotEmpty() -> "Calibrate dynamic retry latency backoffs. Pre-fetch trend metrics prior to dispatching active trader execution runs."
                    else -> "Maintain current optimized decentralized planning route. Consider elevating portfolio trade exposure or increasing social publishing frequency."
                }
                
                // Save Knowledge Entry
                val kEntry = KnowledgeEntry(
                    title = "Mission Experience: ${mission.title}",
                    category = "Mission Review",
                    description = "Successful: ${nextStatus == "Completed"}. Best Agent: $bestAgentStr. Lessons: $lessonsLearnedStr",
                    evidence = "Mission review generated from execution logs of ${mission.title}. Mistakes: $mistakesStr",
                    confidenceScore = 0.90
                )
                knowledgeDao.insertKnowledge(kEntry)
                
                updatedMission.copy(
                    reviewWasSuccessful = nextStatus == "Completed",
                    reviewDelayingTasks = delayingTasksStr,
                    reviewBestAgent = bestAgentStr,
                    reviewMistakes = mistakesStr,
                    reviewLessonsLearned = lessonsLearnedStr,
                    hasReview = true
                )
            } else {
                updatedMission
            }
            azaMissionDao.updateMission(reviewedMission)
        }
    }

    suspend fun deleteQueueTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        taskQueueDao.deleteTask(task)
    }

    suspend fun clearQueueTasks() = withContext(Dispatchers.IO) {
        taskQueueDao.clearAllTasks()
    }

    fun getExecutionsForTaskFlow(taskId: Int): Flow<List<TaskExecution>> {
        return taskQueueDao.getExecutionsForTaskFlow(taskId)
    }

    suspend fun insertTaskExecution(execution: TaskExecution): Long = withContext(Dispatchers.IO) {
        taskQueueDao.insertExecution(execution)
    }

    suspend fun updateTaskExecution(execution: TaskExecution) = withContext(Dispatchers.IO) {
        taskQueueDao.updateExecution(execution)
    }

    suspend fun clearAllExecutions() = withContext(Dispatchers.IO) {
        taskQueueDao.clearAllExecutions()
    }

    suspend fun insertTaskDependency(dep: TaskDependency) = withContext(Dispatchers.IO) {
        taskQueueDao.insertDependency(dep)
    }

    suspend fun getDependenciesForTask(taskId: Int): List<Int> = withContext(Dispatchers.IO) {
        taskQueueDao.getDependenciesForTask(taskId)
    }

    suspend fun getDependentTasks(dependsOnTaskId: Int): List<Int> = withContext(Dispatchers.IO) {
        taskQueueDao.getDependentTasks(dependsOnTaskId)
    }

    suspend fun clearAllDependencies() = withContext(Dispatchers.IO) {
        taskQueueDao.clearAllDependencies()
    }

    // Aza Mission Operations
    suspend fun getMissionById(id: Int): AzaMission? = withContext(Dispatchers.IO) {
        azaMissionDao.getMissionById(id)
    }

    suspend fun getActiveMissionDirect(): AzaMission? = withContext(Dispatchers.IO) {
        azaMissionDao.getActiveMissionDirect()
    }

    suspend fun insertMission(mission: AzaMission): Long = withContext(Dispatchers.IO) {
        azaMissionDao.insertMission(mission)
    }

    suspend fun updateMission(mission: AzaMission) = withContext(Dispatchers.IO) {
        azaMissionDao.updateMission(mission)
    }

    suspend fun deleteMission(mission: AzaMission) = withContext(Dispatchers.IO) {
        azaMissionDao.deleteMission(mission)
    }

    suspend fun getPausedMissionIds(): List<Int> = withContext(Dispatchers.IO) {
        azaMissionDao.getPausedMissionIds()
    }

    suspend fun clearAllMissions() = withContext(Dispatchers.IO) {
        azaMissionDao.clearAllMissions()
    }

    suspend fun insertExecutiveDecision(decision: com.example.data.database.ExecutiveDecisionRecord): Long = withContext(Dispatchers.IO) {
        executiveDecisionDao.insertDecision(decision)
    }

    suspend fun clearAllExecutiveDecisions() = withContext(Dispatchers.IO) {
        executiveDecisionDao.clearAllDecisions()
    }

    // Market Intelligence Helpers
    suspend fun getAllOpportunitiesDirect(): List<com.example.data.database.OpportunityEntity> = withContext(Dispatchers.IO) {
        marketIntelligenceDao.getAllOpportunitiesDirect()
    }

    suspend fun getOpportunityById(id: Int): com.example.data.database.OpportunityEntity? = withContext(Dispatchers.IO) {
        marketIntelligenceDao.getOpportunityById(id)
    }

    suspend fun insertOpportunity(opportunity: com.example.data.database.OpportunityEntity): Long = withContext(Dispatchers.IO) {
        marketIntelligenceDao.insertOpportunity(opportunity)
    }

    suspend fun updateOpportunity(opportunity: com.example.data.database.OpportunityEntity) = withContext(Dispatchers.IO) {
        marketIntelligenceDao.updateOpportunity(opportunity)
    }

    suspend fun clearAllOpportunities() = withContext(Dispatchers.IO) {
        marketIntelligenceDao.clearAllOpportunities()
    }

    suspend fun getAllProposalsDirect(): List<com.example.data.database.TradeProposalEntity> = withContext(Dispatchers.IO) {
        marketIntelligenceDao.getAllProposalsDirect()
    }

    suspend fun getProposalById(id: Int): com.example.data.database.TradeProposalEntity? = withContext(Dispatchers.IO) {
        marketIntelligenceDao.getProposalById(id)
    }

    suspend fun insertProposal(proposal: com.example.data.database.TradeProposalEntity): Long = withContext(Dispatchers.IO) {
        marketIntelligenceDao.insertProposal(proposal)
    }

    suspend fun updateProposal(proposal: com.example.data.database.TradeProposalEntity) = withContext(Dispatchers.IO) {
        marketIntelligenceDao.updateProposal(proposal)
    }

    suspend fun clearAllProposals() = withContext(Dispatchers.IO) {
        marketIntelligenceDao.clearAllProposals()
    }

    // Phase 17: Portfolio Position Helpers
    suspend fun getAllPositionsDirect(): List<BinancePositionEntity> = withContext(Dispatchers.IO) {
        portfolioPositionDao.getAllPositionsDirect()
    }

    suspend fun getPositionBySymbol(symbol: String): BinancePositionEntity? = withContext(Dispatchers.IO) {
        portfolioPositionDao.getPositionBySymbol(symbol)
    }

    suspend fun insertPosition(position: BinancePositionEntity) = withContext(Dispatchers.IO) {
        portfolioPositionDao.insertPosition(position)
    }

    suspend fun updatePosition(position: BinancePositionEntity) = withContext(Dispatchers.IO) {
        portfolioPositionDao.updatePosition(position)
    }

    suspend fun deletePosition(symbol: String) = withContext(Dispatchers.IO) {
        portfolioPositionDao.deletePosition(symbol)
    }

    suspend fun clearAllPositions() = withContext(Dispatchers.IO) {
        portfolioPositionDao.clearAllPositions()
    }

    suspend fun getAllReviewsDirect(): List<PositionReviewEntity> = withContext(Dispatchers.IO) {
        portfolioPositionDao.getAllReviewsDirect()
    }

    suspend fun insertReview(review: PositionReviewEntity): Long = withContext(Dispatchers.IO) {
        portfolioPositionDao.insertReview(review)
    }

    suspend fun clearAllReviews() = withContext(Dispatchers.IO) {
        portfolioPositionDao.clearAllReviews()
    }

    suspend fun getAllAlertsDirect(): List<PositionAlertEntity> = withContext(Dispatchers.IO) {
        portfolioPositionDao.getAllAlertsDirect()
    }

    suspend fun insertAlert(alert: PositionAlertEntity): Long = withContext(Dispatchers.IO) {
        portfolioPositionDao.insertAlert(alert)
    }

    suspend fun markAlertAsRead(id: Int) = withContext(Dispatchers.IO) {
        portfolioPositionDao.markAlertAsRead(id)
    }

    suspend fun clearAllAlerts() = withContext(Dispatchers.IO) {
        portfolioPositionDao.clearAllAlerts()
    }
}
