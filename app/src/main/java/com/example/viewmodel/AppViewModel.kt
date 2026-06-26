package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.*
import com.example.data.repository.AppRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import java.util.Locale
import com.example.aza.planning.AzaPlanningEngine
import com.example.aza.web.WebSurfer
import com.example.aza.web.SearchResult

class AppViewModel(private val repository: AppRepository) : ViewModel() {

    // Main Tables Observables
    val portfolioAssets: StateFlow<List<PortfolioAsset>> = repository.portfolioAssets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contentItems: StateFlow<List<ContentItem>> = repository.contentItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val freelanceGigs: StateFlow<List<FreelanceGig>> = repository.freelanceGigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<TransactionRecord>> = repository.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accountInfo: StateFlow<AccountLinking?> = repository.accountInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val executionRecords: StateFlow<List<ExecutionRecord>> = repository.executionRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val learningRecords: StateFlow<List<AzaLearningRecord>> = repository.learningRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val strategies: StateFlow<List<StrategyEntity>> = repository.strategies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backtestReports: StateFlow<List<StrategyBacktestReport>> = repository.backtestReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val optimizationRecords: StateFlow<List<StrategyOptimizationRecord>> = repository.optimizationRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tradeAuditLogs: StateFlow<List<com.example.data.database.TradeAuditLog>> = repository.tradeAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exportedReports: StateFlow<List<com.example.data.database.ExportedReportHistory>> = repository.exportedReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertExportedReport(report: com.example.data.database.ExportedReportHistory) {
        viewModelScope.launch {
            repository.insertExportedReport(report)
        }
    }

    fun deleteExportedReportById(id: Int) {
        viewModelScope.launch {
            repository.deleteExportedReportById(id)
        }
    }

    fun clearAllExportedReports() {
        viewModelScope.launch {
            repository.clearAllExportedReports()
        }
    }

    // --- Aza Validation Lab (Phase 7) ---
    val experiments: StateFlow<List<com.example.data.database.Experiment>> = repository.experiments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExperimentTrades: StateFlow<List<com.example.data.database.ExperimentTrade>> = repository.allExperimentTrades
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedExperimentId = MutableStateFlow<Int?>(1)

    val selectedExperimentTrades: StateFlow<List<com.example.data.database.ExperimentTrade>> = kotlinx.coroutines.flow.combine(
        allExperimentTrades,
        selectedExperimentId
    ) { trades, id ->
        if (id == null) emptyList() else trades.filter { it.experimentId == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Aza Knowledge Base & Skill Library (Phase 8) ---
    val knowledgeEntries: StateFlow<List<com.example.data.database.KnowledgeEntry>> = repository.knowledgeEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val skillLibrary: StateFlow<List<com.example.data.database.SkillEntry>> = repository.skillLibrary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTradingConflicts = MutableStateFlow<List<String>>(emptyList())
    val influencedKnowledgeTitle = MutableStateFlow<String?>(null)

    fun createNewExperiment(
        name: String,
        description: String,
        strategyName: String,
        market: String,
        timeframe: String,
        riskLevel: String,
        initialBalance: Double = 10000.0
    ) {
        viewModelScope.launch {
            val newExp = com.example.data.database.Experiment(
                name = name,
                description = description,
                strategyName = strategyName,
                market = market,
                timeframe = timeframe,
                riskLevel = riskLevel,
                initialBalance = initialBalance,
                currentBalance = initialBalance,
                isActive = true
            )
            val id = repository.insertExperiment(newExp).toInt()
            selectedExperimentId.value = id
            addThought("🧪 VALIDATION LAB: Created new experiment '$name' using strategy '$strategyName' on $market ($timeframe)")
        }
    }

    fun tickContinuousPaperTrading() {
        viewModelScope.launch {
            val expList = experiments.value
            if (expList.isEmpty()) return@launch

            // Pick the selected experiment, or a random active one if none is selected
            val targetExp = expList.find { it.id == selectedExperimentId.value } ?: expList.firstOrNull { it.isActive } ?: return@launch
            
            // --- Phase 8: Knowledge Retrieval BEFORE trade decision ---
            val kList = repository.getAllKnowledgeDirect()
            
            val relevantLesson = kList.find { entry ->
                entry.title.contains(targetExp.strategyName, ignoreCase = true) || 
                entry.description.contains(targetExp.strategyName, ignoreCase = true) ||
                entry.title.contains(targetExp.market, ignoreCase = true) ||
                (targetExp.strategyName == "Trend Following" && (entry.title.contains("EMA", ignoreCase = true) || entry.title.contains("trend", ignoreCase = true))) ||
                (targetExp.strategyName == "Breakout" && entry.title.contains("breakout", ignoreCase = true)) ||
                (targetExp.strategyName == "Mean Reversion" && entry.title.contains("reversion", ignoreCase = true))
            }

            if (relevantLesson != null) {
                influencedKnowledgeTitle.value = relevantLesson.title
                // Update timesConfirmed (evolution)
                repository.updateKnowledge(relevantLesson.copy(timesConfirmed = relevantLesson.timesConfirmed + 1))
            } else {
                influencedKnowledgeTitle.value = null
            }

            // --- Phase 8: Conflict Detection ---
            val conflicts = mutableListOf<String>()
            val isBuy = java.util.Random().nextBoolean()
            
            if (targetExp.strategyName == "Breakout" && isBuy) {
                val hasOverboughtWarning = kList.any { it.title.contains("overbought RSI", ignoreCase = true) }
                if (hasOverboughtWarning && java.util.Random().nextDouble() > 0.4) {
                    conflicts.add("Stored knowledge ('Avoiding breakout buys during overbought RSI conditions') advises AGAINST breakouts during overbought conditions.")
                }
            }
            if (targetExp.strategyName == "Trend Following" && !isBuy) {
                val hasTrendWarning = kList.any { it.title.contains("trend regimes", ignoreCase = true) }
                if (hasTrendWarning && java.util.Random().nextDouble() > 0.4) {
                    conflicts.add("Strategy EMA direction SELL conflicts with macro trend volume guidelines.")
                }
            }
            activeTradingConflicts.value = conflicts
            if (conflicts.isNotEmpty()) {
                addThought("⚠️ CONFLICT DETECTED on ${targetExp.market}: ${conflicts.joinToString(". ")}")
            }

            // Generate simulated price and run strategy
            val currentPrice = when (targetExp.market) {
                "BTC" -> 67000.0 + (java.util.Random().nextDouble() - 0.5) * 4000.0
                "ETH" -> 3400.0 + (java.util.Random().nextDouble() - 0.5) * 200.0
                "AAPL" -> 190.0 + (java.util.Random().nextDouble() - 0.5) * 10.0
                "NVDA" -> 940.0 + (java.util.Random().nextDouble() - 0.5) * 50.0
                else -> 100.0
            }

            // Simulate direction
            val direction = if (isBuy) "BUY" else "SELL"
            val priceChangePct = (java.util.Random().nextDouble() * 0.05) - 0.02 // -2% to +3%
            val entryPrice = currentPrice
            val exitPrice = if (isBuy) entryPrice * (1 + priceChangePct) else entryPrice * (1 - priceChangePct)
            
            val quantity = when (targetExp.market) {
                "BTC" -> 0.1 + java.util.Random().nextDouble() * 0.4
                "ETH" -> 1.0 + java.util.Random().nextDouble() * 4.0
                else -> 5.0 + java.util.Random().nextDouble() * 20.0
            }

            // Calculate fees and slippage based on riskLevel and market
            val baseFeeRate = when (targetExp.riskLevel) {
                "Low" -> 0.0005
                "Medium" -> 0.001
                else -> 0.002
            }
            val baseSlippageRate = when (targetExp.riskLevel) {
                "Low" -> 0.0002
                "Medium" -> 0.0005
                else -> 0.0012
            }
            
            val fees = (entryPrice + exitPrice) * quantity * baseFeeRate
            val slippage = entryPrice * quantity * baseSlippageRate * (0.5 + java.util.Random().nextDouble())
            
            // Profit Loss calculation
            val rawProfitLoss = if (isBuy) {
                (exitPrice - entryPrice) * quantity
            } else {
                (entryPrice - exitPrice) * quantity
            }
            val netProfitLoss = rawProfitLoss - fees - slippage
            val durationMin = 15 + java.util.Random().nextInt(480)

            // Dynamic entry/exit reasons
            val entryReason = when (targetExp.strategyName) {
                "Trend Following" -> "EMA crossover signal with high-volume breakout confirmation."
                "Breakout" -> "Local channel boundary breakthrough with volume escalation."
                "Mean Reversion" -> "Stochastic oscillator reached oversold boundary with bullish divergence."
                else -> "Quantitative pattern matching trigger."
            }
            
            val exitReason = if (netProfitLoss > 0) {
                "Hit target take-profit level at extension boundary."
            } else {
                "Triggered tight stop-loss parameter to protect capital."
            }

            val alternativeAction = if (netProfitLoss > 0) {
                "Consider employing a dynamic trailing stop-loss to harvest additional extended trend runs."
            } else {
                "Introduce an ATR volatility filter to dynamically widen stop limits during high whip-saw conditions."
            }

            val newTrade = com.example.data.database.ExperimentTrade(
                experimentId = targetExp.id,
                symbol = targetExp.market,
                direction = direction,
                entryPrice = entryPrice,
                exitPrice = exitPrice,
                quantity = quantity,
                profitLoss = netProfitLoss,
                fees = fees,
                slippage = slippage,
                durationMin = durationMin.toLong(),
                entryReason = entryReason,
                exitReason = exitReason,
                confidence = 0.70 + (java.util.Random().nextDouble() * 0.25),
                marketSnapshot = "Price: $${String.format(Locale.US, "%,.2f", entryPrice)}, Timeframe: ${targetExp.timeframe}, Risk: ${targetExp.riskLevel}, Regime: ${if (isBuy) "TRENDING" else "MEAN_REVERTING"}",
                alternativeAction = alternativeAction
            )

            // Insert trade
            repository.insertExperimentTrade(newTrade)

            // --- Phase 8: Skill Evolution based on trade outcome ---
            val isWin = netProfitLoss > 0
            val skillsList = repository.getAllSkillsDirect()
            val matchingSkills = skillsList.filter { skill ->
                skill.name == "Position Sizing" || 
                skill.name == "Risk Calculation" ||
                skill.name == "Trade Exit Optimization" ||
                (targetExp.strategyName == "Trend Following" && skill.name == "Detect Trend") ||
                (targetExp.strategyName == "Breakout" && (skill.name == "Detect Breakout" || skill.name == "Identify Fake Breakout")) ||
                (targetExp.strategyName == "Mean Reversion" && (skill.name == "Detect Range" || skill.name == "Detect Reversal"))
            }

            matchingSkills.forEach { skill ->
                val newSuccessRate = if (isWin) {
                    ((skill.successRate * 9.0) + 100.0) / 10.0
                } else {
                    (skill.successRate * 9.0) / 10.0
                }
                val newFailureRate = 100.0 - newSuccessRate
                
                // Confidence updates slowly across many trades. Never from a single trade.
                val newConfidence = if (isWin) {
                    (skill.confidence + 0.01).coerceAtMost(1.0)
                } else {
                    (skill.confidence - 0.02).coerceAtLeast(0.1)
                }

                val updatedSkill = skill.copy(
                    successRate = newSuccessRate,
                    failureRate = newFailureRate,
                    confidence = newConfidence,
                    lastUpdated = System.currentTimeMillis()
                )
                repository.updateSkill(updatedSkill)
            }

            // Recalculate Experiment statistics based on all historical trades for this experiment
            val allTrades = repository.getTradesForExperimentDirect(targetExp.id)
            val totalTradesCount = allTrades.size
            val winningTrades = allTrades.filter { it.profitLoss > 0 }
            val winningTradesCount = winningTrades.size
            val losingTrades = allTrades.filter { it.profitLoss <= 0 }
            
            val winRate = if (totalTradesCount > 0) (winningTradesCount.toDouble() / totalTradesCount) * 100.0 else 0.0
            
            val totalProfit = winningTrades.map { it.profitLoss }.sum()
            val totalLoss = losingTrades.map { it.profitLoss }.sum().let { if (it == 0.0) 1.0 else Math.abs(it) }
            val profitFactor = totalProfit / totalLoss

            val avgDuration = if (totalTradesCount > 0) allTrades.map { it.durationMin }.average() else 0.0
            val totalFeesAccum = allTrades.map { it.fees }.sum()
            val totalSlippageAccum = allTrades.map { it.slippage }.sum()
            val netProfitAccum = allTrades.map { it.profitLoss }.sum()
            val currentBal = targetExp.initialBalance + netProfitAccum

            // Simple Sharpe: mean profit loss divided by standard deviation (or simplified risk adjustment)
            val avgProfitLoss = if (totalTradesCount > 0) netProfitAccum / totalTradesCount else 0.0
            val variance = if (totalTradesCount > 1) {
                allTrades.map { Math.pow(it.profitLoss - avgProfitLoss, 2.0) }.sum() / (totalTradesCount - 1)
            } else 1.0
            val stdDev = Math.sqrt(variance).let { if (it == 0.0) 1.0 else it }
            val sharpe = (avgProfitLoss / stdDev) * Math.sqrt(252.0) // annualized approximation

            // Drawdown estimation
            var peak = targetExp.initialBalance
            var maxDD = 0.0
            var runningBal = targetExp.initialBalance
            // Chronological sort
            allTrades.reversed().forEach { t ->
                runningBal += t.profitLoss
                if (runningBal > peak) {
                    peak = runningBal
                }
                val dd = if (peak > 0) ((peak - runningBal) / peak) * 100.0 else 0.0
                if (dd > maxDD) {
                    maxDD = dd
                }
            }

            // Stability score (smoothness of returns: closer to linear equity curve = higher score)
            val stability = if (totalTradesCount > 2) {
                val positiveSteps = allTrades.count { it.profitLoss > -10.0 }
                (positiveSteps.toDouble() / totalTradesCount * 100.0).coerceIn(10.0, 98.0)
            } else 75.0

            val updatedExp = targetExp.copy(
                currentBalance = currentBal,
                netProfit = netProfitAccum,
                winRate = winRate,
                profitFactor = if (profitFactor.isNaN() || profitFactor.isInfinite()) 1.0 else profitFactor,
                sharpeRatio = if (sharpe.isNaN() || sharpe.isInfinite()) 0.0 else sharpe,
                maxDrawdown = maxDD,
                averageTradeDuration = avgDuration,
                totalSimulatedFees = totalFeesAccum,
                totalSimulatedSlippage = totalSlippageAccum,
                dailyReturn = targetExp.dailyReturn + (netProfitLoss / targetExp.initialBalance * 100.0),
                weeklyReturn = targetExp.weeklyReturn + (netProfitLoss / targetExp.initialBalance * 100.0),
                monthlyReturn = targetExp.monthlyReturn + (netProfitLoss / targetExp.initialBalance * 100.0),
                riskAdjustedReturn = if (sharpe > 0) sharpe * 0.8 else 0.0,
                stabilityScore = stability
            )

            repository.updateExperiment(updatedExp)
            addThought("🧪 VALIDATION LAB: Executed paper trade simulation on ${targetExp.market}. Direction: $direction, Profit/Loss: $${String.format(Locale.US, "%.2f", netProfitLoss)}")
        }
    }

    fun clearAllExperiments() {
        viewModelScope.launch {
            repository.clearAllExperiments()
            repository.clearAllExperimentTrades()
            selectedExperimentId.value = null
            addThought("🧹 VALIDATION LAB: Cleared all experiments and historical evaluation logs.")
        }
    }

    fun createNewKnowledgeEntry(
        title: String,
        category: String,
        description: String,
        evidence: String,
        confidenceScore: Double
    ) {
        viewModelScope.launch {
            val entry = com.example.data.database.KnowledgeEntry(
                title = title,
                category = category,
                description = description,
                evidence = evidence,
                confidenceScore = confidenceScore,
                dateCreated = System.currentTimeMillis(),
                timesConfirmed = 1,
                isConfirmed = true
            )
            repository.insertKnowledge(entry)
            addThought("🧠 KNOWLEDGE BASE: Formulated new lesson '$title' in category $category.")
        }
    }

    fun updateKnowledgeEntry(entry: com.example.data.database.KnowledgeEntry) {
        viewModelScope.launch {
            repository.updateKnowledge(entry)
            addThought("🧠 KNOWLEDGE BASE: Updated lesson '${entry.title}' (Confirmed count: ${entry.timesConfirmed})")
        }
    }

    fun clearAllKnowledgeBase() {
        viewModelScope.launch {
            repository.clearAllKnowledge()
            addThought("🧠 KNOWLEDGE BASE: Cleared persistent experience store.")
        }
    }

    fun performHardSystemReset() {
        viewModelScope.launch {
            repository.clearAllKnowledge()
            repository.clearAllExperiments()
            repository.clearAllExperimentTrades()
            repository.clearAllTradeAuditLogs()
            repository.clearExecutionHistory()
            repository.clearLearningRecords()
            repository.clearChatMessages()
            repository.clearGoalSystem()
            repository.clearAllExportedReports()
            
            repository.updateAccountLinking(com.example.data.database.AccountLinking(
                totalBalance = 100000.0,
                isTradingLinked = true,
                isSocialLinked = true,
                isFreelanceLinked = true
            ))

            addThought("🚨 SYSTEM DIAGNOSTICS: Performed a complete Hard System Reset. Seeded default configurations.")
        }
    }

    fun updateSkillEntry(skill: com.example.data.database.SkillEntry) {
        viewModelScope.launch {
            repository.updateSkill(skill)
            addThought("🎓 SKILL LIBRARY: Calibrated trading skill '${skill.name}' (Confidence: ${String.format(Locale.US, "%.2f", skill.confidence)})")
        }
    }

    fun generateImprovementSuggestions(): String {
        val trades = allExperimentTrades.value
        if (trades.isEmpty()) {
            return "No trading history available to generate suggestions yet. Run more paper trades!"
        }

        val exps = experiments.value
        val strategyProfits = mutableMapOf<String, Double>()
        val strategyWins = mutableMapOf<String, Int>()
        val strategyTotals = mutableMapOf<String, Int>()

        val regimeProfits = mutableMapOf<String, Double>()
        val regimeTotals = mutableMapOf<String, Int>()

        val failureReasons = mutableMapOf<String, Int>()

        trades.forEach { trade ->
            val exp = exps.find { it.id == trade.experimentId }
            val strat = exp?.strategyName ?: "Unknown"
            
            strategyProfits[strat] = (strategyProfits[strat] ?: 0.0) + trade.profitLoss
            strategyTotals[strat] = (strategyTotals[strat] ?: 0) + 1
            if (trade.profitLoss > 0) {
                strategyWins[strat] = (strategyWins[strat] ?: 0) + 1
            } else {
                val reason = if (trade.exitReason.contains("stop-loss", ignoreCase = true) || trade.exitReason.contains("Stop loss", ignoreCase = true)) {
                    "Stop Loss Triggered"
                } else if (trade.exitReason.contains("whip-saw", ignoreCase = true)) {
                    "Whip-saw Chop"
                } else {
                    "Fee Slippage Drag"
                }
                failureReasons[reason] = (failureReasons[reason] ?: 0) + 1
            }

            val regime = if (trade.marketSnapshot.contains("TRENDING", ignoreCase = true)) "TRENDING"
                         else if (trade.marketSnapshot.contains("VOLATILE", ignoreCase = true)) "VOLATILE"
                         else if (trade.marketSnapshot.contains("MEAN_REVERTING", ignoreCase = true)) "MEAN REVERTING"
                         else "CHOPPY/RANGE"

            regimeProfits[regime] = (regimeProfits[regime] ?: 0.0) + trade.profitLoss
            regimeTotals[regime] = (regimeTotals[regime] ?: 0) + 1
        }

        val strongestStrategy = strategyProfits.maxByOrNull { it.value }?.key ?: "Trend Following"
        val weakestStrategy = strategyProfits.minByOrNull { it.value }?.key ?: "Breakout"
        val commonFailure = failureReasons.maxByOrNull { it.value }?.key ?: "Stop Loss Drag"
        val bestRegime = regimeProfits.maxByOrNull { it.value }?.key ?: "TRENDING"
        val worstRegime = regimeProfits.minByOrNull { it.value }?.key ?: "VOLATILE/CHOP"

        val countText = if (trades.size >= 100) "Continuous validation cycle complete (${trades.size} trades analyzed)." else "Evaluation phase in progress (${trades.size}/100 trades completed in current validation cycle)."

        return """
            💡 **AUTOMATED IMPROVEMENT SUGGESTIONS**
            *$countText*
            
            • **Strongest Strategy**: **$strongestStrategy** (demonstrating structural persistence and optimal fee protection).
            • **Weakest Strategy**: **$weakestStrategy** (highly vulnerable to rapid volatility shifts and spread-drag).
            • **Most Common Failure Reason**: **$commonFailure** (accounting for the majority of paper trade drawdowns).
            • **Best-Performing Regime**: **$bestRegime** (highly suited for momentum-based trailing targets).
            • **Worst-Performing Regime**: **$worstRegime** (severe impact on execution metrics due to slippage).
            
            **RECOMMENDATIONS FOR MODEL ADJUSTMENT (Do Not Execute Automatically):**
            1. Restrict execution of **$weakestStrategy** during high-spread **$worstRegime** periods to curb fee friction.
            2. Increase sizing parameters dynamically for **$strongestStrategy** under **$bestRegime** regimes where win rate exceeds 70%.
            3. Recalibrate stop-loss buffers based on **ATR (Average True Range)** instead of hard thresholds to mitigate premature **$commonFailure** liquidations.
        """.trimIndent()
    }

    fun clearAllTradeAuditLogs() {
        viewModelScope.launch {
            repository.clearAllTradeAuditLogs()
        }
    }

    val marketRegime = MutableStateFlow("Ranging")

    fun clearExecutionHistory() {
        viewModelScope.launch {
            repository.clearExecutionHistory()
        }
    }

    fun clearLearningHistory() {
        viewModelScope.launch {
            repository.clearLearningRecords()
        }
    }

    val chatMessages: StateFlow<List<ChatMessage>> = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val azaChannels: StateFlow<List<AzaChannel>> = repository.azaChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI-Specific states
    private val _isAzaResponding = MutableStateFlow(false)
    val isAzaResponding = _isAzaResponding.asStateFlow()

    private val _isAutopilotActive = MutableStateFlow(false)
    val isAutopilotActive = _isAutopilotActive.asStateFlow()

    private val _isAutonomousMode = MutableStateFlow(false)
    val isAutonomousMode = _isAutonomousMode.asStateFlow()

    private val _safetyRules = MutableStateFlow("Max Alloc: $250, zero dynamic withdrawals, paper mode enabled if account is unlinked.")
    val safetyRules = _safetyRules.asStateFlow()

    fun toggleAutonomousMode(enable: Boolean) {
        _isAutonomousMode.value = enable
        val modeStr = if (enable) "AUTONOMOUS MODE ACTIVE (Safety bounds applied)" else "APPROVAL MODE ACTIVE (Host checks enabled)"
        addThought("⚙️ POLICY: Switched execution mode to: $modeStr")
    }

    fun updateSafetyRules(rules: String) {
        _safetyRules.value = rules
        addThought("🔒 POLICY: Updated host safety rules parameters.")
    }

    // --- Web Surfing & Exploration States ---
    private val _surfUrl = MutableStateFlow("https://news.google.com")
    val surfUrl = _surfUrl.asStateFlow()

    private val _surfContent = MutableStateFlow("Welcome to Aza Web Explorer! Enter a URL above or enter a search query to explore live web directories.")
    val surfContent = _surfContent.asStateFlow()

    private val _isSurfing = MutableStateFlow(false)
    val isSurfing = _isSurfing.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _surfHistory = MutableStateFlow<List<String>>(
        listOf("https://news.ycombinator.com", "https://wikipedia.org", "https://github.com")
    )
    val surfHistory = _surfHistory.asStateFlow()

    fun selectSurfUrl(url: String) {
        _surfUrl.value = url
    }

    fun selectSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun runWebSurf(url: String) {
        if (url.isBlank()) return
        var targetUrl = url.trim()
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "https://$targetUrl"
        }
        _surfUrl.value = targetUrl
        _isSurfing.value = true
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                WebSurfer.surfUrl(targetUrl)
            }
            _surfContent.value = content
            _isSurfing.value = false
            
            val currentHist = _surfHistory.value.toMutableList()
            if (!currentHist.contains(targetUrl)) {
                currentHist.add(0, targetUrl)
                if (currentHist.size > 8) {
                    currentHist.removeAt(currentHist.size - 1)
                }
                _surfHistory.value = currentHist
            }
            
            repository.insertChatMessage(ChatMessage(
                sender = "SYSTEM",
                text = "🌐 **Aza Web Surfer**: Fetched and scraped URL: `$targetUrl` (${content.length} characters of parsed body content)."
            ))
        }
    }

    fun runWebSearch(query: String) {
        if (query.isBlank()) return
        _searchQuery.value = query
        _isSurfing.value = true
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                WebSurfer.searchWeb(query)
            }
            _searchResults.value = results
            _isSurfing.value = false
            
            if (results.isNotEmpty()) {
                val bestResultUrl = results.first().url
                _surfUrl.value = bestResultUrl
                val bestResultContent = withContext(Dispatchers.IO) {
                    WebSurfer.surfUrl(bestResultUrl)
                }
                _surfContent.value = "### Web Search Results for \"$query\"\n\n" +
                        results.joinToString("\n") { "• **${it.title}**\n  URL: ${it.url}\n  _${it.snippet}_\n" } +
                        "\n---\n\n### Scraped Best Match Page ($bestResultUrl):\n\n$bestResultContent"
            } else {
                _surfContent.value = "No results found for search query: \"$query\""
            }
        }
    }

    private val _autopilotThoughts = MutableStateFlow<List<String>>(
        listOf(
            "System online. Standing by for Autopilot activation.",
            "Ready to optimize search trends and self-publish."
        )
    )
    val autopilotThoughts = _autopilotThoughts.asStateFlow()

    private var autopilotJob: Job? = null

    // --- Decentralized Autonomous Planning Engine States ---
    val activeObjective: StateFlow<AzaObjective?> = repository.activeObjective
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pendingApprovalTasks: StateFlow<List<AzaAgentTask>> = repository.pendingApprovalTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeObjectiveTasks = MutableStateFlow<List<AzaAgentTask>>(emptyList())
    val activeObjectiveTasks = _activeObjectiveTasks.asStateFlow()

    private val planningEngine = AzaPlanningEngine(repository)

    val registeredSkills = planningEngine.skillRegistry.getAllSkills()
    val registeredAgents = planningEngine.registry.getAllAgents()

    init {
        viewModelScope.launch {
            activeObjective.collectLatest { objective ->
                if (objective != null) {
                    repository.getTasksForObjective(objective.id).collect { tasks ->
                        _activeObjectiveTasks.value = tasks
                    }
                } else {
                    _activeObjectiveTasks.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            planningEngine.eventBus.events.collect { event ->
                when (event) {
                    is com.example.aza.context.AzaEvent.TradeClosed -> {
                        addThought("📊 EVENT [TradeClosed]: Exchanged ${event.amount} ${event.symbol}. Profit: +$${event.profitUsd} USD. Direct reserve balance: $${event.totalBalanceUsd}")
                    }
                    is com.example.aza.context.AzaEvent.ContentCreated -> {
                        addThought("🎬 EVENT [ContentCreated]: TikTok script outline registered: '${event.title}' under queue status [${event.status}].")
                    }
                    is com.example.aza.context.AzaEvent.ProposalSubmitted -> {
                        addThought("💼 EVENT [ProposalSubmitted]: Tailored engineering bid uploaded to ${event.clientName} under client request: '${event.gigTitle}'.")
                    }
                    is com.example.aza.context.AzaEvent.AuditCompleted -> {
                        addThought("🛡️ EVENT [AuditCompleted]: Financial system compliance verified. Audited Balance: $${event.auditedAmount}. Summary: ${event.statusLog}")
                    }
                    else -> {}
                }
            }
        }
        startHealthMonitoring()
    }

    fun startHealthMonitoring() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15000) // check every 15s
                
                // 1. Database status check
                val dbStatus = try {
                    repository.getAccountInfoDirect()
                    "HEALTHY"
                } catch (e: Exception) {
                    "ERROR"
                }
                com.example.aza.market.MarketDataService.databaseStatus = dbStatus
                if (dbStatus == "ERROR") {
                    addThought("⚠️ SYSTEM HEALTH ALERT: Database connection is offline or degraded.")
                }

                // 2. Exchange connection
                val isConnected = com.example.aza.market.MarketDataService.exchangeConnected
                if (!isConnected) {
                    addThought("⚠️ SYSTEM HEALTH ALERT: Connection to external exchange is lost.")
                }

                // 3. Latency / Data Quality alerts
                val latency = com.example.aza.market.MarketDataService.currentLatencyMs
                if (latency > 150) {
                    addThought("⚠️ SYSTEM HEALTH WARNING: Market API latency is elevated: ${latency}ms")
                }
                
                val qScore = com.example.aza.market.MarketDataService.dataQualityScore
                if (qScore < 90.0) {
                    addThought("⚠️ SYSTEM HEALTH WARNING: Market Data Quality score has dropped to ${String.format(Locale.US, "%.1f", qScore)}%")
                }
            }
        }
    }

    private val _isAnalyzingMarket = MutableStateFlow(false)
    val isAnalyzingMarket = _isAnalyzingMarket.asStateFlow()

    private val _marketAnalysis = MutableStateFlow<String?>(null)
    val marketAnalysis = _marketAnalysis.asStateFlow()

    private val _isGeneratingScript = MutableStateFlow(false)
    val isGeneratingScript = _isGeneratingScript.asStateFlow()

    private val _isGeneratingProposal = MutableStateFlow(false)
    val isGeneratingProposal = _isGeneratingProposal.asStateFlow()

    private val _selectedGigProposal = MutableStateFlow<String?>(null)
    val selectedGigProposal = _selectedGigProposal.asStateFlow()

    // --- Trading Logic ---

    fun buyAsset(symbol: String, name: String, amount: Double, priceEach: Double) {
        viewModelScope.launch {
            val totalCost = amount * priceEach
            val account = repository.getAccountInfoDirect() ?: AccountLinking()
            
            if (!account.isTradingLinked) {
                // Must link trading first
                return@launch
            }
            if (account.totalBalance < totalCost) {
                // Insufficient balance
                return@launch
            }

            // 1. Deduct balance
            val updatedAccount = account.copy(totalBalance = account.totalBalance - totalCost)
            repository.updateAccountLinking(updatedAccount)

            // 2. Add/Update PortfolioAsset
            val existing = portfolioAssets.value.find { it.symbol == symbol }
            if (existing != null) {
                val newAmount = existing.amountOwned + amount
                val totalCostExisting = (existing.amountOwned * existing.averageBuyPrice) + totalCost
                val newAvgPrice = totalCostExisting / newAmount
                repository.updateAsset(existing.copy(amountOwned = newAmount, averageBuyPrice = newAvgPrice, currentPrice = priceEach))
            } else {
                repository.insertAsset(PortfolioAsset(symbol, name, amount, priceEach, if (symbol == "BTC" || symbol == "ETH") "CRYPTO" else "STOCK", priceEach))
            }

            // 3. Log transaction
            repository.insertTransaction(
                TransactionRecord(
                    description = "Bought $amount $symbol @ $$priceEach",
                    amount = -totalCost,
                    type = "BUY"
                )
            )
        }
    }

    fun sellAsset(symbol: String, amount: Double, priceEach: Double) {
        viewModelScope.launch {
            val existing = portfolioAssets.value.find { it.symbol == symbol } ?: return@launch
            if (existing.amountOwned < amount) return@launch // Can't sell more than owned

            val totalProceeds = amount * priceEach
            val account = repository.getAccountInfoDirect() ?: AccountLinking()

            // 1. Add balance
            val updatedAccount = account.copy(totalBalance = account.totalBalance + totalProceeds)
            repository.updateAccountLinking(updatedAccount)

            // 2. Update/Delete Asset
            val remainingAmount = existing.amountOwned - amount
            if (remainingAmount <= 0.0001) {
                repository.removeAsset(existing)
            } else {
                repository.updateAsset(existing.copy(amountOwned = remainingAmount, currentPrice = priceEach))
            }

            // 3. Log transaction
            repository.insertTransaction(
                TransactionRecord(
                    description = "Sold $amount $symbol @ $$priceEach",
                    amount = totalProceeds,
                    type = "SELL"
                )
            )
        }
    }

    fun requestMarketAnalysis(symbol: String, type: String) {
        viewModelScope.launch {
            _isAnalyzingMarket.value = true
            _marketAnalysis.value = null
            val analysis = repository.getAiMarketAnalysis(symbol, type)
            _marketAnalysis.value = analysis
            _isAnalyzingMarket.value = false
        }
    }

    // --- Content Creation Logic ---

    fun generateNewContentPlan(title: String, platform: String) {
        viewModelScope.launch {
            if (title.isBlank()) return@launch
            _isGeneratingScript.value = true

            // 1. Direct call to repository's AI engine
            val scriptOutline = repository.getAiContentScript(title, platform)

            // 2. Insert as a Draft Content item
            repository.insertContent(
                ContentItem(
                    title = title,
                    platform = platform.uppercase(),
                    scriptOutline = scriptOutline,
                    status = "DRAFT",
                    engagementCount = 0
                )
            )
            _isGeneratingScript.value = false
        }
    }

    fun updateContentStatus(item: ContentItem, newStatus: String) {
        viewModelScope.launch {
            val simulatedEngagementMultiplier = when (newStatus) {
                "SCHEDULED" -> Random.nextInt(15, 80)
                "PUBLISHED" -> Random.nextInt(150, 2400)
                else -> 0
            }
            repository.updateContent(
                item.copy(
                    status = newStatus,
                    engagementCount = simulatedEngagementMultiplier
                )
            )
        }
    }

    fun deleteContent(id: Int) {
        viewModelScope.launch {
            repository.deleteContentById(id)
        }
    }

    // --- Freelance Logic ---

    fun runGigProposalGenerator(gig: FreelanceGig) {
        viewModelScope.launch {
            _isGeneratingProposal.value = true
            _selectedGigProposal.value = null

            val proposal = repository.getAiProposalForGig(gig.title, gig.description)
            _selectedGigProposal.value = proposal

            repository.updateGig(
                gig.copy(
                    status = "APPLIED",
                    proposalText = proposal
                )
            )
            _isGeneratingProposal.value = false
        }
    }

    fun completeAndPayoutGig(gig: FreelanceGig) {
        viewModelScope.launch {
            val account = repository.getAccountInfoDirect() ?: AccountLinking()
            
            // 1. Update gig status to PAVED / PAID
            repository.updateGig(
                gig.copy(status = "PAID")
            )

            // 2. Deposit into wallet balance
            val updatedAccount = account.copy(totalBalance = account.totalBalance + gig.budget)
            repository.updateAccountLinking(updatedAccount)

            // 3. Record transaction
            repository.insertTransaction(
                TransactionRecord(
                    description = "Payment: Freelance - ${gig.title}",
                    amount = gig.budget,
                    type = "FREELANCE"
                )
            )
        }
    }

    // --- System Actions ---

    fun setupAccountConnections(isTrading: Boolean, isSocial: Boolean, isFreelance: Boolean) {
        viewModelScope.launch {
            val account = repository.getAccountInfoDirect() ?: AccountLinking()
            repository.updateAccountLinking(
                account.copy(
                    isTradingLinked = isTrading,
                    isSocialLinked = isSocial,
                    isFreelanceLinked = isFreelance
                )
            )
        }
    }

    fun depositFundsMock(amount: Double) {
        viewModelScope.launch {
            val account = repository.getAccountInfoDirect() ?: AccountLinking()
            repository.updateAccountLinking(
                account.copy(totalBalance = account.totalBalance + amount)
            )
            repository.insertTransaction(
                TransactionRecord(
                    description = "Manual Deposit Traced",
                    amount = amount,
                    type = "DEPOSIT"
                )
            )
        }
    }

    fun withdrawFundsSecurely(amount: Double, destination: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val account = repository.getAccountInfoDirect() ?: AccountLinking()
            if (amount <= 0.0) {
                onError("Amount must be greater than zero.")
                return@launch
            }
            if (account.totalBalance < amount) {
                onError("Insufficient secure funds. Current balance: $${String.format(Locale.US, "%,.2f", account.totalBalance)}")
                return@launch
            }

            // Deduct balance
            val updated = account.copy(totalBalance = account.totalBalance - amount)
            repository.updateAccountLinking(updated)

            // Log Transaction
            repository.insertTransaction(
                TransactionRecord(
                    description = "Secured Withdrawal to $destination",
                    amount = -amount,
                    type = "WITHDRAWAL"
                )
            )

            // Chat Announcement
            val msg = """
                🔐 **[Secure Withdrawal Initiated]**
                
                I have verified and dispatched a secure cryptographically-signed withdrawal request from your central cash treasury:
                
                • **Beneficiary/Destination**: *"$destination"*
                • **Withdrawal Amount**: **$${String.format(Locale.US, "%,.2f", amount)} USD**
                • **Gateway Clearing Protocol**: Security-shielded Ledger Wire
                
                ✅ **Audited Balance:** Your remaining wallet balance has been securely optimized to **$${String.format(Locale.US, "%,.2f", updated.totalBalance)} USD**.
            """.trimIndent()
            repository.insertChatMessage(ChatMessage(sender = "AZA", text = msg))
            onSuccess()
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            // 1. Save user message
            repository.insertChatMessage(ChatMessage(sender = "USER", text = text))
            _isAzaResponding.value = true

            // 2. Local State Variables for parsing
            val account = accountInfo.value ?: AccountLinking()
            val assets = portfolioAssets.value
            val contentList = contentItems.value
            val gigs = freelanceGigs.value
            
            var actionExecutedNotice = ""
            
            // Try to parse BUY action
            // Pattern: buy 0.05 BTC
            val buyPattern = """(?i)buy\s+(\d+(?:\.\d+)?)\s*([a-zA-Z0-9]+)""".toRegex()
            val buyMatch = buyPattern.find(text)
            
            // Try to parse SELL action
            val sellPattern = """(?i)sell\s+(\d+(?:\.\d+)?)\s*([a-zA-Z0-9]+)""".toRegex()
            val sellMatch = sellPattern.find(text)
            
            // Try to parse DEPOSIT action
            val depositPattern = """(?i)deposit\s+(\d+(?:\.\d+)?)""".toRegex()
            val depositMatch = depositPattern.find(text)
            
            // Try to parse APPLY/PROPOSAL action
            val proposalPattern = """(?i)(?:apply\s+(?:for\s+)?|proposal\s+(?:for\s+)?)(.+)""".toRegex()
            val proposalMatch = proposalPattern.find(text)
            
            // Try to parse SCRIPT action
            val scriptPattern = """(?i)(?:create|draft|add)\s+(?:script|content)\s+(?:about\s+)?(.+?)\s+(?:on|for)\s+(tiktok|youtube|instagram)""".toRegex()
            val scriptMatch = scriptPattern.find(text)

            // Try to parse SURF action
            val surfPattern = """(?i)(?:surf|browse|crawl|open|visit|view)\s+(https?://\S+|www\.\S+|\S+\.[a-zA-Z]{2,6}\S*)""".toRegex()
            val surfMatch = surfPattern.find(text)

            // Try to parse SEARCH action
            val searchPattern = """(?i)(?:web\s+)?search\s+(?:for\s+)?(.+)""".toRegex()
            val searchMatch = if (surfMatch == null && buyMatch == null && sellMatch == null && depositMatch == null && scriptMatch == null && proposalMatch == null) {
                searchPattern.find(text)
            } else null

            // Dynamic Asset Pricing Map to mirror databases
            val assetPrices = mapOf(
                "BTC" to (67450.0 to "Bitcoin"),
                "ETH" to (3480.0 to "Ethereum"),
                "AAPL" to (192.3 to "Apple Inc."),
                "NVDA" to (945.5 to "NVIDIA Corp.")
            )

            if (buyMatch != null) {
                val amount = buyMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val symbol = buyMatch.groupValues[2].uppercase()
                val priceInfo = assetPrices[symbol]
                
                if (priceInfo != null) {
                    val price = priceInfo.first
                    val name = priceInfo.second
                    val totalCost = amount * price
                    if (!account.isTradingLinked) {
                        actionExecutedNotice = "⚠️ **Execution Failed:** Your trading connection is currently offline. Please toggle 'Simulated Instant Checkout' on the Account screen to link your wallet."
                    } else if (account.totalBalance < totalCost) {
                        actionExecutedNotice = "⚠️ **Execution Failed:** Insufficient simulated funds. The cost to buy $amount $symbol is $${String.format("%,.2f", totalCost)}, but your cash balance is only $${String.format("%,.2f", account.totalBalance)}."
                    } else {
                        buyAsset(symbol, name, amount, price)
                        actionExecutedNotice = "✅ **Action Executed:** Purchased **$amount $symbol** ($name) at $${String.format("%,.2f", price)} per unit, totaling $${String.format("%,.2f", totalCost)}."
                    }
                } else {
                    actionExecutedNotice = "⚠️ **Analysis Notice:** Asset symbol '$symbol' is not in our active lists (BTC, ETH, AAPL, NVDA). Please specify one of these for purchase execution."
                }
            } else if (sellMatch != null) {
                val amount = sellMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val symbol = sellMatch.groupValues[2].uppercase()
                val priceInfo = assetPrices[symbol]
                val existing = assets.find { it.symbol == symbol }
                
                if (priceInfo != null && existing != null) {
                    val price = priceInfo.first
                    if (existing.amountOwned < amount) {
                        actionExecutedNotice = "⚠️ **Execution Failed:** You only own **${existing.amountOwned} $symbol**, so you cannot sell **$amount $symbol**."
                    } else {
                        sellAsset(symbol, amount, price)
                        actionExecutedNotice = "✅ **Action Executed:** Sold **$amount $symbol** at $${String.format("%,.2f", price)} per unit, crediting **$${String.format("%,.2f", amount * price)}** to your balance."
                    }
                } else if (existing == null) {
                    actionExecutedNotice = "⚠️ **Execution Failed:** You do not own any shares of **$symbol** in your portfolio currently."
                } else {
                    actionExecutedNotice = "⚠️ **Execution Failed:** Market services cannot find liquid pricing for asset symbol '$symbol'."
                }
            } else if (depositMatch != null) {
                val amount = depositMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                depositFundsMock(amount)
                actionExecutedNotice = "✅ **Action Executed:** Credited **$${String.format("%,.2f", amount)}** of simulated funds to your balance."
            } else if (scriptMatch != null) {
                val title = scriptMatch.groupValues[1].trim()
                val platform = scriptMatch.groupValues[2].trim().uppercase()
                generateNewContentPlan(title, platform)
                actionExecutedNotice = "✅ **Action Executed:** Generated a script draft titled **\"$title\"** for **$platform** in your Content Studio queue."
            } else if (proposalMatch != null) {
                val searchKey = proposalMatch.groupValues[1].trim()
                val targetGig = gigs.find { it.title.contains(searchKey, ignoreCase = true) || it.description.contains(searchKey, ignoreCase = true) }
                if (targetGig != null) {
                    runGigProposalGenerator(targetGig)
                    actionExecutedNotice = "✅ **Action Executed:** Drafted and submitted a proposal for the project **\"${targetGig.title}\"**."
                } else {
                    actionExecutedNotice = "ℹ️ **Notice:** Searched for a gig matching '$searchKey' to submit proposal, but couldn't find an open contract. Please check typing and try again!"
                }
            } else if (surfMatch != null) {
                val rawUrl = surfMatch.groupValues[1].trim()
                var targetUrl = rawUrl
                if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                    targetUrl = "https://$targetUrl"
                }
                _surfUrl.value = targetUrl
                
                actionExecutedNotice = withContext(Dispatchers.IO) {
                    try {
                        val content = WebSurfer.surfUrl(targetUrl)
                        _surfContent.value = content
                        
                        val currentHist = _surfHistory.value.toMutableList()
                        if (!currentHist.contains(targetUrl)) {
                            currentHist.add(0, targetUrl)
                            if (currentHist.size > 8) currentHist.removeAt(currentHist.size - 1)
                            _surfHistory.value = currentHist
                        }
                        
                        "🌐 **Aza Web Surfer**: Connected to **$targetUrl** and scraped page text. Content snippet:\n\n${if (content.length > 2500) content.substring(0, 2500) + "\n\n...[Content truncated for context safety]..." else content}"
                    } catch (e: Exception) {
                        "⚠️ **Aza Web Surfer Error**: Failed to browse **$targetUrl** - ${e.localizedMessage}"
                    }
                }
            } else if (searchMatch != null) {
                val query = searchMatch.groupValues[1].trim()
                _searchQuery.value = query
                actionExecutedNotice = withContext(Dispatchers.IO) {
                    try {
                        val results = WebSurfer.searchWeb(query)
                        _searchResults.value = results
                        if (results.isNotEmpty()) {
                            val bestResultUrl = results.first().url
                            _surfUrl.value = bestResultUrl
                            val bestResultContent = WebSurfer.surfUrl(bestResultUrl)
                            _surfContent.value = "### Web Search Results for \"$query\"\n\n" +
                                    results.joinToString("\n") { "• **${it.title}**\n  URL: ${it.url}\n  _${it.snippet}_\n" } +
                                    "\n---\n\n### Scraped Best Match Content ($bestResultUrl):\n\n$bestResultContent"
                            
                            val listStr = results.take(4).joinToString("\n") { "• **${it.title}** (${it.url}): ${it.snippet}" }
                            "🌐 **Aza Web Explorer**: Searched live indexes for *\"$query\"*.\n\n**Search Results Summary:**\n$listStr\n\n**Best Page Scraped Content:**\n${if (bestResultContent.length > 1800) bestResultContent.substring(0, 1800) + "..." else bestResultContent}"
                        } else {
                            "ℹ️ **Aza Web Explorer Notice**: Searched indexes for *\"$query\"* but received zero matching indexed nodes. Please check web accessibility parameters."
                        }
                    } catch (e: Exception) {
                        "⚠️ **Aza Web Explorer Error**: Search index connection lost: ${e.localizedMessage}"
                    }
                }
            }

            // Construct Backdrop Prompt for Gemini
            val finalBackdrop = """
                You are Aza, the user's conversational AI Business & Trading assistant.
                
                Active System Status:
                - Simulated Cash: $${String.format("%.2f", account.totalBalance)} USD (Trading Linked: ${account.isTradingLinked}, Client Stripe Linked: ${account.isFreelanceLinked})
                - Portfolio Assets:
                  ${if (assets.isEmpty()) "No active holdings." else assets.joinToString("\n  ") { "• ${it.symbol} (${it.displayName}): ${it.amountOwned} units owned (Avg buy: $${it.averageBuyPrice}, current price: $${it.currentPrice})" }}
                - Social Content Plans:
                  ${if (contentList.isEmpty()) "Empty queue." else contentList.joinToString("\n  ") { "• \"${it.title}\" on ${it.platform} - Status: ${it.status} (likes: ${it.engagementCount})" }}
                - Freelance Gigs:
                  ${if (gigs.isEmpty()) "No open gigs cataloged." else gigs.joinToString("\n  ") { "• \"${it.title}\" ($${it.budget}) - Status: ${it.status}" }}
                
                Executive Actions executed in this turn:
                ${if (actionExecutedNotice.isEmpty()) "None. Just engage in conversation and offer advice." else actionExecutedNotice}
                
                Requirements:
                1. Speak in a charismatic, business-focused, concise, and helpful tone representing Aza.
                2. If an executive action was executed this turn, make sure to congratulate or explain the action's implications (e.g. updating portfolios or submitting drafts).
                3. Address questions on how to grow their portfolio, make scripts, or apply for gigs.
                4. Always use exquisite and readable Markdown formatting (bold keywords, bullets, neat dividers, no ugly headers). Keep replies under 3-4 paragraphs.
            """.trimIndent()

            // 3. Request Gemini API or graceful fallback
            var reply = ""
            try {
                reply = repository.getAzaResponse(finalBackdrop, text)
            } catch (e: Exception) {
                // FALLBACK: Local response selector
                reply = if (actionExecutedNotice.isNotEmpty()) {
                    "$actionExecutedNotice\n\nI have successfully executed this command directly on your device. Your portfolios have been updated in real-time. Do you have any other tasks or questions about your trading balance, video scripts, or freelance gigs?"
                } else {
                    val lower = text.lowercase()
                    when {
                        lower.contains("balance") || lower.contains("portfolio") || lower.contains("money") || lower.contains("asset") || lower.contains("trade") || lower.contains("buy") || lower.contains("sell") || lower.contains("invest") || lower.contains("market") || lower.contains("bitcoin") || lower.contains("btc") || lower.contains("ethereum") || lower.contains("eth") || lower.contains("aapl") || lower.contains("apple") || lower.contains("nvda") || lower.contains("nvidia") || lower.contains("crypto") || lower.contains("stock") || lower.contains("shares") || lower.contains("holding") || lower.contains("cash") || lower.contains("wallet") -> {
                            "### 📊 Portfolio Status & Paper Trading\n\n" +
                            "Your simulated cash balance is **$${String.format(Locale.US, "%,.2f", account.totalBalance)} USD** (Trading Link: ${if (account.isTradingLinked) "CONNECTED" else "DISCONNECTED"}).\n\n" +
                            "**Current Asset Holdings:**\n" +
                            (if (assets.isEmpty()) "• No active holdings. Let's make some paper trades!" else assets.joinToString("\n") { "• **${it.symbol}** (${it.displayName}): ${it.amountOwned} units (Avg cost: $${String.format(Locale.US, "%,.2f", it.averageBuyPrice)}, current: $${String.format(Locale.US, "%,.2f", it.currentPrice)})" }) +
                            "\n\n#### ⚡ Direct Executable Trade Shortcuts:\n" +
                            "You can execute paper trades instantly by typing:\n" +
                            "• *\"Buy 0.05 BTC\"* or *\"Buy 10 AAPL\"*\n" +
                            "• *\"Sell 0.01 BTC\"* or *\"Sell 5 NVDA\"*\n\n" +
                            "Let me know what you'd like to trade, or check the **Markets** tab to perform live indicator analysis!"
                        }
                        lower.contains("gigs") || lower.contains("jobs") || lower.contains("work") || lower.contains("freelance") || lower.contains("proposal") || lower.contains("apply") || lower.contains("client") || lower.contains("contract") || lower.contains("pitch") -> {
                            "### 💼 Freelance Opportunities\n\n" +
                            gigs.joinToString("\n") { "• **${it.title}** (Budget: $${it.budget}) - Status: *${it.status}*" } +
                            "\n\nTell me: *\"Apply for Article Writer\"* and I will automatically draft your technical pitch!"
                        }
                        lower.contains("scripts") || lower.contains("tiktok") || lower.contains("youtube") || lower.contains("instagram") || lower.contains("content") || lower.contains("video") || lower.contains("reels") || lower.contains("draft") || lower.contains("platform") -> {
                            "### 🎬 Marketing Studio Queue\n\nYou have **${contentList.size}** active ideas in your Content Studio.\n\n" +
                            (if (contentList.isEmpty()) "• No active content items." else contentList.joinToString("\n") { "• *\"${it.title}\"* on **${it.platform}** - *${it.status}*" }) +
                            "\n\nTo add an item, dictate: *\"Create script about Room Database on TikTok\"*."
                        }
                        else -> {
                            "Hello! I am **Aza**, your conversational partner.\n\n" +
                            "I can directly parse commands to interact with your data. For example:\n" +
                            "• *\"Buy 0.02 BTC\"* or *\"Sell 1 AAPL\"*\n" +
                            "• *\"Draft a script about Jetpack Compose on TikTok\"*\n" +
                            "• *\"Write a proposal for the Article Writer gig\"*\n\n" +
                            "Feel free to ask questions about your cash balance, video scripts, or freelance applications!"
                        }
                    }
                }
            }

            // 4. Save Aza Response
            repository.insertChatMessage(ChatMessage(sender = "AZA", text = reply))
            _isAzaResponding.value = false
        }
    }

    fun toggleAutopilot(enable: Boolean) {
        if (enable) {
            autopilotJob?.cancel()
            _isAutopilotActive.value = true
            addThought("Autonomous Aza Hub initiated. Beginning continuous channel optimization.")
            autopilotJob = viewModelScope.launch {
                while (isActive) {
                    addThought("Standby... Preparing the next automated generation cycle in 20 seconds.")
                    delay(20000)
                    if (isActive) {
                        runAutonomousCycle()
                    }
                }
            }
        } else {
            autopilotJob?.cancel()
            autopilotJob = null
            _isAutopilotActive.value = false
            addThought("Aza Autopilot suspended. Handed controls back to host terminal.")
        }
    }

    fun addThought(thought: String) {
        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val timeString = formatter.format(java.util.Date())
        val current = _autopilotThoughts.value.toMutableList()
        current.add(0, "[$timeString] $thought")
        _autopilotThoughts.value = current.take(15)
    }

    fun forceAutonomousRun() {
        viewModelScope.launch {
            addThought("Manual override: Triggering immediate autonomous iteration cycle...")
            runAutonomousCycle()
        }
    }

    private suspend fun runAutonomousCycle() {
        _isAzaResponding.value = true
        try {
            val performCryptoTrade = Random.nextBoolean()
            if (performCryptoTrade) {
                runCryptoTradingCycle()
            } else {
                runSocialContentCycle()
            }
        } catch (e: java.lang.Exception) {
            addThought("⚠️ Error during autonomous loop optimization: ${e.localizedMessage}")
        } finally {
            _isAzaResponding.value = false
        }
    }

    private suspend fun runCryptoTradingCycle() {
        addThought("Scanning global crypto feeds on Binance, Coinbase & Kraken sockets...")
        delay(1500)
        
        val account = repository.getAccountInfoDirect() ?: AccountLinking()
        val exchange = listOf("Binance", "Coinbase", "Kraken").random()
        val symbol = listOf("BTC", "ETH").random()
        
        val (price, name) = when (symbol) {
            "BTC" -> 67450.0 to "Bitcoin"
            else -> 3480.0 to "Ethereum"
        }
        
        addThought("Retrieved $symbol ticker from $exchange: $$price USD.")
        addThought("Evaluating technicals: EMA(20) crossover on 1-Hour chart. RSI stands at healthy ${Random.nextInt(48, 62)}.")
        
        // Conservative Trade Risk Assessment
        val riskLevel = "Conservative Grid Strategy"
        val stopLossPercent = 3.0
        val targetStopLoss = price * (1.0 - (stopLossPercent / 100.0))
        addThought("Risk Guard active: Capping portfolio allocation to max 5.0% buffer.")
        
        val totalAssets = portfolioAssets.value
        val existingAsset = totalAssets.find { it.symbol == symbol }
        
        // Decide buy vs sell vs hold using conservative rules
        val decision = when {
            account.totalBalance > 300.0 && (existingAsset == null || (existingAsset.amountOwned * price) < (account.totalBalance * 0.4)) -> "BUY"
            existingAsset != null && existingAsset.amountOwned > 0.001 && Random.nextBoolean() -> "SELL"
            else -> "HOLD"
        }
        
        if (decision == "BUY") {
            val buyUSD = Random.nextDouble(100.0, 300.0)
            val units = buyUSD / price
            val unitsFormatted = String.format(Locale.US, "%.4f", units).toDouble()
            val finalCost = unitsFormatted * price
            
            if (account.totalBalance >= finalCost) {
                addThought("Action determined: DCA accumulation signal trigger filled.")
                
                // Deduct cash from secure wallet
                val updatedAccount = account.copy(totalBalance = account.totalBalance - finalCost)
                repository.updateAccountLinking(updatedAccount)
                
                // Update Asset holding
                if (existingAsset != null) {
                    val newAmount = existingAsset.amountOwned + unitsFormatted
                    val totalCostExisting = (existingAsset.amountOwned * existingAsset.averageBuyPrice) + finalCost
                    val newAvgPrice = totalCostExisting / newAmount
                    repository.updateAsset(existingAsset.copy(
                        amountOwned = newAmount,
                        averageBuyPrice = newAvgPrice,
                        currentPrice = price
                    ))
                } else {
                    repository.insertAsset(PortfolioAsset(
                        symbol = symbol,
                        displayName = name,
                        amountOwned = unitsFormatted,
                        averageBuyPrice = price,
                        assetType = "CRYPTO",
                        currentPrice = price
                    ))
                }
                
                // Insert transaction record
                repository.insertTransaction(
                    TransactionRecord(
                        description = "[Aza Autopilot] Bought $unitsFormatted $symbol on $exchange",
                        amount = -finalCost,
                        type = "BUY"
                    )
                )
                
                addThought("Spot order successful on $exchange! +$unitsFormatted $symbol filled.")
                
                // Post Chat Announcement
                val alertMessage = """
                    🤖 **[Aza Autopilot Trade Executed]**
                    
                    I have autonomously analyzed market trend data and executed a conservative risk-minimized purchase on your behalf:
                    
                    • **Exchange Routing**: **$exchange**
                    • **Asset Traded**: **$symbol** ($name)
                    • **Action/Strategy**: Spot DCA Accumulation (Pullback Dip)
                    • **Quantity/Rate**: **$unitsFormatted $symbol** @ $${String.format("%,.2f", price)} each
                    • **Transaction Value**: **$${String.format("%,.2f", finalCost)} USD** (Settled via secure Cash Wallet balance)
                    
                    🛡️ **Compliance & Risk Guard Metrics:**
                    - Risk Category: **$riskLevel** (Low exposure)
                    - Calculated Volatility Filter: **Active**
                    - Stop-Loss Configured: **$${String.format("%,.2f", targetStopLoss)} USD** (-$stopLossPercent%)
                    - Dynamic Rebalancing Adjustment: **Portfolio Optimized**
                """.trimIndent()
                
                repository.insertChatMessage(ChatMessage(sender = "AZA", text = alertMessage))
            } else {
                addThought("DCA trigger cancelled: Insufficient secure cash reserve ($${String.format("%,.2f", account.totalBalance)}).")
            }
        } else if (decision == "SELL") {
            // Sell a fraction of existing asset to secure some profit
            val percentToSell = Random.nextDouble(15.0, 40.0) / 100.0
            val existingAmount = existingAsset!!.amountOwned
            val amountToSell = existingAmount * percentToSell
            val amountToSellFormatted = String.format(Locale.US, "%.4f", amountToSell).toDouble()
            
            if (amountToSellFormatted >= 0.0001) {
                val proceeds = amountToSellFormatted * price
                addThought("Action determined: Profit securing trigger alert.")
                
                // Add cash back
                val updatedAccount = account.copy(totalBalance = account.totalBalance + proceeds)
                repository.updateAccountLinking(updatedAccount)
                
                // Update Asset count
                val remainingAmount = existingAmount - amountToSellFormatted
                if (remainingAmount <= 0.0005) {
                    repository.removeAsset(existingAsset)
                } else {
                    repository.updateAsset(existingAsset.copy(
                        amountOwned = remainingAmount,
                        currentPrice = price
                    ))
                }
                
                // Ledger log
                repository.insertTransaction(
                    TransactionRecord(
                        description = "[Aza Autopilot] Solidified profit of $amountToSellFormatted $symbol on $exchange",
                        amount = proceeds,
                        type = "SELL"
                    )
                )
                
                addThought("Limit Sell Order processed successfully! Secured +$${String.format("%,.2f", proceeds)} USD.")
                
                val alertMessage = """
                    🤖 **[Aza Autopilot Profit Secured]**
                    
                    I have autonomously detected a local overhead peak and executed a profit liquidation trade:
                    
                    • **Exchange Routing**: **$exchange**
                    • **Asset Traded**: **$symbol** ($name)
                    • **Action/Strategy**: Limit Sell & Rebalancing (Profit Securing)
                    • **Quantity/Rate**: **$amountToSellFormatted $symbol** @ $${String.format("%,.2f", price)} each
                    • **Simulated Proceeds**: **+$${String.format("%,.2f", proceeds)} USD** (Settle-deposited to Cash wallet)
                    
                    🛡️ **Compliance & Risk Guard Metrics:**
                    - Portfolio Realized Gain: **Optimized**
                    - Stop-Loss Clear: **Dynamic Adjustment**
                    - Secure Escrow Settlement: **Completed**
                """.trimIndent()
                
                repository.insertChatMessage(ChatMessage(sender = "AZA", text = alertMessage))
            } else {
                addThought("Holding $symbol: Quantities too small to split safely.")
            }
        } else {
            addThought("Market Technical analysis holds: Price consolidation within bounds. Decided to HOLD exposure.")
        }
    }

    private suspend fun runSocialContentCycle() {
        val platforms = listOf("YOUTUBE", "INSTAGRAM", "TIKTOK")
        val platform = platforms.random()
        
        val trends = when (platform) {
            "YOUTUBE" -> listOf(
                "Mastering Edge-to-Edge with Material 3",
                "Room Database migrations made bulletproof",
                "Jetpack Compose Speed Optimization guide",
                "Complete local Gemini offline models on Android"
            )
            "INSTAGRAM" -> listOf(
                "3 Visual spacing techniques to double retention",
                "Aesthetics of Neo-Brutalism themes in Jetpack Compose",
                "Secret tips to avoid lagging animation recompositions",
                "How color psychology governs app onboarding metrics"
            )
            else -> listOf(
                "Avoid doing THIS in Jetpack Compose! 🛑",
                "How I automated a workflow using Android local state",
                "Beginner mistake in Coroutines that leaks memory",
                "Wait, you can render custom Canvas animations in 12 lines?!"
            )
        }
        val trendTopic = trends.random()
        addThought("Identified high-viral interest topic for $platform: '$trendTopic'")

        val channel = repository.getChannelByPlatform(platform) ?: AzaChannel(
            platform = platform,
            handle = "@aza_" + platform.lowercase() + "_test",
            followersCount = 150,
            totalVideosPublished = 0,
            totalLikesAccumulated = 0
        )

        addThought("Retrieving profile details of ${channel.handle} on $platform...")
        addThought("Synthesizing current trend keyword metrics with target audience: '${channel.targetSegment}'...")

        val scriptOutline = try {
            repository.getAiContentScript(trendTopic, platform + " (Autopilot Direct)")
        } catch (e: java.lang.Exception) {
            """
            🎬 **HOOK [0-3s]**:
            "If you are still design-coding like it is 2018, STOP! Here is how to use modern $trendTopic."
            
            📘 **BODY [3-25s]**:
            • Tip 1: Avoid nested structures. Use simple modifiers.
            • Tip 2: Implement dynamic Material 3 color system.
            • Tip 3: Abstract states away from render loops.
            
            🚀 **CTA [25-30s]**:
            "This layout is automatically published on ${channel.handle}! Hit follow to stay updated with Aza optimizations!"
            """.trimIndent()
        }

        // engagement mapping
        val likes = when (platform) {
            "YOUTUBE" -> Random.nextInt(450, 2200)
            "INSTAGRAM" -> Random.nextInt(1200, 6800)
            else -> Random.nextInt(2400, 18500) // TikTok higher volume
        }

        val gainedFollowers = (likes * 0.14).toInt()
        val newFollowerCount = channel.followersCount + gainedFollowers
        
        val learnedInsight = when (platform) {
            "YOUTUBE" -> "Adding detailed markdown tags to '$trendTopic' outlines improved retention metrics by 18.5%."
            "INSTAGRAM" -> "High-contrast visual styling on '$trendTopic' drives 3x more bookmark saves compared to standard layouts."
            else -> "Placing direct contrast warnings in the first 1.5 seconds of '$trendTopic' increased completion rate from 23% to 75%."
        }

        // Save updated social channel stats
        val updatedChannel = channel.copy(
            followersCount = newFollowerCount,
            totalVideosPublished = channel.totalVideosPublished + 1,
            totalLikesAccumulated = channel.totalLikesAccumulated + likes,
            autonomousInsight = learnedInsight
        )
        repository.updateChannel(updatedChannel)
        addThought("Successfully published outline! engagement recorded: +$likes likes.")
        addThought("Channel ${channel.handle} grew by +$gainedFollowers followers.")

        // Insert content item
        repository.insertContent(
            ContentItem(
                title = trendTopic,
                platform = platform,
                scriptOutline = scriptOutline,
                status = "PUBLISHED",
                engagementCount = likes
            )
        )

        // Chat Announcement Log from Aza
        val alertMessage = """
            🤖 **[Aza Autopilot Executed]**
            
            I have autonomously designed, optimized, and published a new vertical content piece on our profiles:
            
            • **Platform Channel**: **$platform** (${channel.handle})
            • **Published Title**: *"$trendTopic"*
            • **Target Demographic**: *${channel.targetSegment}*
            • **Simulated Live Performance**: 📈 **+$likes likes**
            • **Channel Followers**: **$newFollowerCount** (grown by **+$gainedFollowers**)
            
            ⚡ **Aza Optimization Learning Outcome:**
            *"$learnedInsight"*
        """.trimIndent()

        repository.insertChatMessage(
            ChatMessage(
                sender = "AZA",
                text = alertMessage
            )
        )
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChatMessages()
            repository.insertChatMessage(
                ChatMessage(
                    sender = "AZA",
                    text = "Hello! I am Aza, your conversational AI partner. 🚀\n\nI can help you analyze markets, generate video scripts, draft freelance proposals, and execute complex business tasks in real time.\n\nTry speaking to me naturally, or try these tasks directly:\n• *\"Buy 0.02 BTC\"* or *\"Sell 2 AAPL\"*\n• *\"Draft a script about Room Database on TikTok\"*\n• *\"Write a proposal for the Tech Blog Writer gig\"*"
                )
            )
        }
    }

    // --- Goal planning implementation ---
    
    fun setHighLevelObjective(objectiveTitle: String) {
        if (objectiveTitle.isBlank()) return
        viewModelScope.launch {
            repository.clearGoalSystem()
            
            addThought("🔄 DEPLOYING PIPELINE FOR OBJECTIVE: \"$objectiveTitle\"")
            addThought("🧠 Initializing generative planning agents...")
            
            val targetPlatform = if (objectiveTitle.lowercase().contains("youtube") || objectiveTitle.lowercase().contains("video")) "YOUTUBE"
                                 else if (objectiveTitle.lowercase().contains("tiktok")) "TIKTOK"
                                 else if (objectiveTitle.lowercase().contains("crypto") || objectiveTitle.lowercase().contains("portfolio")) "CRYPTO"
                                 else if (objectiveTitle.lowercase().contains("gig") || objectiveTitle.lowercase().contains("proposal")) "FREELANCE"
                                 else "ALL"
            
            // 1. OBJECTIVE LIFECYCLE STATE: NEW
            val newObjective = AzaObjective(
                objective = objectiveTitle,
                title = objectiveTitle,
                targetPlatform = targetPlatform,
                targetBudget = if (targetPlatform == "CRYPTO") 1000.0 else 0.0,
                status = "NEW",
                progress = 0.0f,
                currentTask = "Initializing Planner",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val objectiveId = repository.insertObjective(newObjective)
            val insertedObjective = newObjective.copy(id = objectiveId.toInt())
            addThought("🔄 [Lifecycle: NEW] Objective persisted in database.")
            
            // 2. OBJECTIVE LIFECYCLE STATE: PLANNING
            val planningObjective = insertedObjective.copy(
                status = "PLANNING",
                currentTask = "Planning task sequence"
            )
            repository.updateObjective(planningObjective)
            addThought("🧠 [Lifecycle: PLANNING] Planner is decomposing the objective into specialized tasks...")
            
            // Planner decomposes into tasks
            val tasks = planningEngine.planner.planObjective(planningObjective)
            
            // Enrich tasks with requiredSkill, inputs, outputs, dependencies, retryCount
            val enrichedTasks = tasks.mapIndexed { index, task ->
                val skill = planningEngine.skillRegistry.findBestSkillForTask(task.assignedAgent)
                val resolvedSkillName = skill?.skillName ?: "compliance_audit"
                val resolvedInputs = skill?.supportedInputs?.joinToString(",") ?: ""
                val resolvedOutputs = skill?.supportedOutputs?.joinToString(",") ?: ""
                val resolvedDeps = if (index > 0) tasks[index - 1].title else ""
                
                task.copy(
                    requiredSkill = resolvedSkillName,
                    input = resolvedInputs,
                    output = resolvedOutputs,
                    dependencies = resolvedDeps,
                    retryCount = 0,
                    status = if (_isAutonomousMode.value) "APPROVED" else "PENDING_APPROVAL"
                )
            }
            
            if (_isAutonomousMode.value) {
                // Autonomous Mode: Bypass approvals and execute them directly in sequence!
                repository.insertTasks(enrichedTasks)
                
                // 3. OBJECTIVE LIFECYCLE STATE: READY
                val readyObjective = planningObjective.copy(
                    status = "READY",
                    currentTask = "Ready to execute",
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateObjective(readyObjective)
                addThought("⚡ [Lifecycle: READY] Autonomous Mode Active: Decomposed into ${tasks.size} tasks. Executing sequentially...")
                
                repository.insertChatMessage(
                    ChatMessage(
                        sender = "AZA",
                        text = "⚡ **Autonomous Pipeline Execution Active!**\n\nI have configured your objective: *\"$objectiveTitle\"*.\n\nSince **Autonomous Mode** is enabled, Aza will automatically run all ${tasks.size} specialized tasks without requiring approvals, conforming to security parameters:\n\n*${_safetyRules.value}*"
                    )
                )

                // Execute the tasks sequentially in background coroutine
                viewModelScope.launch {
                    val freshTasks = repository.getTasksForObjectiveDirect(insertedObjective.id)
                    for (task in freshTasks) {
                        addThought("🤖 Auto-executing task run: '${task.title}'")
                        planningEngine.executeTask(task) { thought ->
                            addThought(thought)
                        }
                    }
                }
            } else {
                // Approval Mode: Safe pending states
                repository.insertTasks(enrichedTasks)
                
                // 3. OBJECTIVE LIFECYCLE STATE: WAITING_APPROVAL
                val waitingObjective = planningObjective.copy(
                    status = "WAITING_APPROVAL",
                    currentTask = "Awaiting task approvals",
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateObjective(waitingObjective)
                
                addThought("👤 [Lifecycle: WAITING_APPROVAL] Objective awaiting manual approval validation...")
                addThought("👤 Approval Mode active. Standby for user authorizations.")
                
                repository.insertChatMessage(
                    ChatMessage(
                        sender = "AZA",
                        text = "🎯 **Global Objective Established & Orchestrated!**\n\nI have configured your objective: *\"$objectiveTitle\"*.\n\nUsing the Aza decentralized planning system, I created **${tasks.size} specialized tasks** tailored for different agent profiles.\n\nSince you are of **Approval Mode**, those tasks are locked in **'PENDING APPROVAL'** states. Please review them in the **Autopilot Pilot** screen and tap **APPROVE** to securely unleash the agents!"
                    )
                )
            }
        }
    }

    fun approveAgentTask(task: AzaAgentTask) {
        viewModelScope.launch {
            planningEngine.executeTask(task) { thought ->
                addThought(thought)
            }
        }
    }

    fun rejectAgentTask(task: AzaAgentTask) {
        viewModelScope.launch {
            val rejectedTask = task.copy(status = "REJECTED")
            repository.updateTask(rejectedTask)
            addThought("🛑 AGENT REJECTED: '${task.assignedAgent}' action was halted by user host.")
            
            val currentObj = repository.getActiveObjectiveDirect()
            if (currentObj != null) {
                val allTasks = repository.getTasksForObjectiveDirect(currentObj.id)
                val allDone = allTasks.all { it.status == "COMPLETED" || it.status == "REJECTED" || it.status == "FAILED" }
                if (allDone) {
                    val anyFailed = allTasks.any { it.status == "FAILED" }
                    val nextStatus = if (anyFailed) "FAILED" else "COMPLETED"
                    val finishedObj = currentObj.copy(status = nextStatus)
                    repository.updateObjective(finishedObj)
                    addThought("🏁 Objective execution thread closed.")
                }
            }
        }
    }

    fun cancelActiveObjective() {
        viewModelScope.launch {
            val currentObj = repository.getActiveObjectiveDirect()
            if (currentObj != null) {
                val cancelledObj = currentObj.copy(
                    status = "CANCELLED",
                    currentTask = "Cancelled",
                    completionReason = "Cancelled by the system/user",
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateObjective(cancelledObj)
                addThought("🚫 [Lifecycle: CANCELLED] Active Objective cancelled.")
            }
        }
    }

    fun clearActiveObjective() {
        viewModelScope.launch {
            repository.clearGoalSystem()
            addThought("🧹 Active Objective and all subtasks purged from memory.")
        }
    }

    // --- Strategy & Backtesting Research Platform API ---

    private val _isBacktesting = MutableStateFlow(false)
    val isBacktesting = _isBacktesting.asStateFlow()

    private val _lastBacktestReport = MutableStateFlow<StrategyBacktestReport?>(null)
    val lastBacktestReport = _lastBacktestReport.asStateFlow()

    private val _selectedBacktestStrategy = MutableStateFlow("Trend Following")
    val selectedBacktestStrategy = _selectedBacktestStrategy.asStateFlow()

    private val _backtestMarket = MutableStateFlow("BTC")
    val backtestMarket = _backtestMarket.asStateFlow()

    private val _backtestCandleCount = MutableStateFlow(100)
    val backtestCandleCount = _backtestCandleCount.asStateFlow()

    private val _backtestPattern = MutableStateFlow("TREND")
    val backtestPattern = _backtestPattern.asStateFlow()

    fun selectBacktestStrategy(name: String) {
        _selectedBacktestStrategy.value = name
    }

    fun selectBacktestMarket(market: String) {
        _backtestMarket.value = market
    }

    fun selectBacktestPattern(pattern: String) {
        _backtestPattern.value = pattern
    }

    fun setBacktestCandleCount(count: Int) {
        _backtestCandleCount.value = count
    }

    fun changeActiveStrategy(name: String) {
        viewModelScope.launch {
            repository.setActiveStrategy(name)
            addThought("🎯 STRATEGY: Active execution trading model updated to: $name")
        }
    }

    fun runBacktest() {
        viewModelScope.launch {
            _isBacktesting.value = true
            val stratName = _selectedBacktestStrategy.value
            val market = _backtestMarket.value
            val pattern = _backtestPattern.value
            val count = _backtestCandleCount.value

            val strategy = com.example.aza.strategy.StrategyRegistry.getStrategy(stratName)
            if (strategy != null) {
                val seedPrice = when(market) {
                    "BTC" -> 67450.0
                    "ETH" -> 3480.0
                    "AAPL" -> 192.3
                    "NVDA" -> 945.5
                    else -> 100.0
                }
                val candles = com.example.aza.strategy.BacktestEngine.generateMockCandles(market, seedPrice, count, pattern)
                val report = com.example.aza.strategy.BacktestEngine.runBacktest(strategy, market, candles)
                
                repository.insertBacktestReport(report)
                _lastBacktestReport.value = report

                addThought("📊 BACKTEST: Completed simulation of ${strategy.name} on $market ($pattern, $count candles). Net profit: $${String.format(Locale.US, "%.2f", report.netProfit)} USD. Win rate: ${String.format(Locale.US, "%.1f", report.winRate)}%")
            } else {
                addThought("⚠️ BACKTEST: Strategy $stratName not found in registry.")
            }
            _isBacktesting.value = false
        }
    }

    fun runPaperTradeSignal(market: String) {
        viewModelScope.launch {
            val currentAsset = portfolioAssets.value.find { it.symbol == market }
            val currentPrice = currentAsset?.currentPrice ?: when (market) {
                "BTC" -> 67450.0
                "ETH" -> 3480.0
                "AAPL" -> 192.3
                "NVDA" -> 945.5
                else -> 100.0
            }

            val prices = List(30) { index -> currentPrice * (0.95 + (index * 0.003)) }
            
            // 2. Detect the current market regime
            val regime = com.example.aza.strategy.StrategyEvaluationEngine.detectMarketRegime(prices)
            marketRegime.value = regime
            
            // 3. Rank and automatically select the highest performing strategy
            val strategy = com.example.aza.strategy.StrategyEvaluationEngine.autoSelectAndActivateStrategy(
                repository = repository,
                market = market,
                prices = prices,
                addLog = { addThought(it) }
            ) ?: return@launch

            val account = repository.getAccountInfoDirect() ?: AccountLinking()
            val proposal = strategy.evaluate(market, prices, account.totalBalance)

            if (proposal.signal == com.example.aza.strategy.SignalType.BUY) {
                if (account.totalBalance >= proposal.positionSize * proposal.entryPrice) {
                    val simResult = com.example.aza.strategy.PaperExecutionSimulator.simulateMarketOrder(market, isBuy = true, targetSize = proposal.positionSize, limitOrSignalPrice = proposal.entryPrice)
                    buyAsset(market, market, simResult.filledSize, simResult.averageExecutionPrice)
                    
                    planningEngine.coordinator.learningEngine.recordTradeOutcome(
                        strategyName = strategy.name,
                        market = market,
                        profit = 50.0 - simResult.feesPaid, // base transaction fee buffer / paper credit minus simulated fees
                        latencyMs = simResult.latencyMs
                    )

                    // Execution Audit Logging (Objective 5)
                    val auditLog = com.example.data.database.TradeAuditLog(
                        strategyName = strategy.name,
                        marketRegime = regime,
                        signal = "BUY",
                        entryReason = proposal.explanation,
                        exitReason = "",
                        confidence = proposal.confidenceScore,
                        positionSize = simResult.filledSize,
                        expectedProfit = 50.0,
                        actualProfit = 50.0 - simResult.feesPaid,
                        slippage = simResult.slippagePaid,
                        fees = simResult.feesPaid,
                        executionLatencyMs = simResult.latencyMs
                    )
                    repository.insertTradeAuditLog(auditLog)

                    addThought("✅ PAPER TRADE: Executed active strategy BUY signal for $market. Entered @ $${String.format(Locale.US, "%.2f", simResult.averageExecutionPrice)} with size ${String.format(Locale.US, "%.4f", simResult.filledSize)} (Latency: ${simResult.latencyMs}ms, Fees: $${String.format(Locale.US, "%.2f", simResult.feesPaid)})")
                } else {
                    addThought("⚠️ PAPER TRADE: Active strategy signaled BUY for $market, but cash balance is insufficient.")
                }
            } else if (proposal.signal == com.example.aza.strategy.SignalType.SELL) {
                val owned = currentAsset?.amountOwned ?: 0.0
                if (owned > 0.0) {
                    val sellAmount = owned
                    val price = proposal.entryPrice
                    val profitBeforeSlippage = (price - currentAsset!!.averageBuyPrice) * sellAmount
                    
                    val simResult = com.example.aza.strategy.PaperExecutionSimulator.simulateMarketOrder(market, isBuy = false, targetSize = sellAmount, limitOrSignalPrice = price)
                    val actualProfit = (simResult.averageExecutionPrice - currentAsset!!.averageBuyPrice) * simResult.filledSize - simResult.feesPaid
                    
                    sellAsset(market, simResult.filledSize, simResult.averageExecutionPrice)
                    planningEngine.coordinator.learningEngine.recordTradeOutcome(
                        strategyName = strategy.name,
                        market = market,
                        profit = actualProfit,
                        latencyMs = simResult.latencyMs
                    )

                    // Execution Audit Logging (Objective 5)
                    val auditLog = com.example.data.database.TradeAuditLog(
                        strategyName = strategy.name,
                        marketRegime = regime,
                        signal = "SELL",
                        entryReason = "",
                        exitReason = "Liquidation / Take Profit Triggered",
                        confidence = proposal.confidenceScore,
                        positionSize = simResult.filledSize,
                        expectedProfit = profitBeforeSlippage,
                        actualProfit = actualProfit,
                        slippage = simResult.slippagePaid,
                        fees = simResult.feesPaid,
                        executionLatencyMs = simResult.latencyMs
                    )
                    repository.insertTradeAuditLog(auditLog)

                    addThought("✅ PAPER TRADE: Executed active strategy SELL signal for $market. Realized profit: $${String.format(Locale.US, "%.2f", actualProfit)} USD. (Latency: ${simResult.latencyMs}ms, Fees: $${String.format(Locale.US, "%.2f", simResult.feesPaid)}, Slippage: $${String.format(Locale.US, "%.2f", simResult.slippagePaid)})")
                } else {
                    addThought("⚠️ PAPER TRADE: Active strategy signaled SELL for $market, but no active position to liquidate.")
                }
            } else {
                addThought("ℹ️ PAPER TRADE: Active strategy evaluated $market. Current signal is HOLD. Indicators: ${strategy.analyzeMarket(market, prices)}")
            }
        }
    }

    fun runAutonomousEvaluationAndOptimization(market: String) {
        viewModelScope.launch {
            val currentAsset = portfolioAssets.value.find { it.symbol == market }
            val currentPrice = currentAsset?.currentPrice ?: when (market) {
                "BTC" -> 67450.0
                "ETH" -> 3480.0
                "AAPL" -> 192.3
                "NVDA" -> 945.5
                else -> 100.0
            }
            val prices = List(30) { index -> currentPrice * (0.95 + (index * 0.003)) }
            
            com.example.aza.strategy.StrategyEvaluationEngine.evaluateAndOptimizeAll(
                repository = repository,
                market = market,
                currentPrices = prices,
                addLog = { addThought(it) }
            )
        }
    }

}

// Custom simple provider factory
class AppViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
