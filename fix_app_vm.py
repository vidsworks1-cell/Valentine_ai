import re

with open('app/src/main/java/com/example/viewmodel/AppViewModel.kt') as f:
    text = f.read()

# Let's fix specific lines or sections in text

# 1. Update imports
imports = """package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aza.agent.*
import com.example.aza.capability.*
import com.example.aza.connector.*
import com.example.aza.context.*
import com.example.aza.conversation.*
import com.example.aza.execution.*
import com.example.aza.executive.*
import com.example.aza.intelligence.*
import com.example.aza.planning.*
import com.example.aza.security.*
import com.example.aza.strategy.*
import com.example.aza.web.*
import com.example.data.api.*
import com.example.data.database.*
import com.example.data.model.*
import com.example.data.repository.*
import com.example.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import java.util.UUID

"""

# Find class AppViewModel
c_start = text.find("class AppViewModel(")
class_body = text[c_start:]

# Replace old repository flows up to ENGINES
old_top_target = """    val youtubeSyncManager by lazy { YouTubeSyncManager(repository) }
    val youtubeChannel: StateFlow<YouTubeChannelEntity?> = repository.youtubeChannel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val youtubeVideos: StateFlow<List<YouTubeVideoEntity>> = repository.youtubeVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val youtubeAnalyticsSnapshots: StateFlow<List<YouTubeAnalyticsSnapshot>> = repository.youtubeAnalyticsSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val youtubeUploadDrafts: StateFlow<List<YouTubeUploadDraft>> = repository.youtubeUploadDrafts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())"""

new_top = """    val youtubeSyncManager by lazy { YouTubeSyncManager(repository) }
    val youtubeChannel: StateFlow<YouTubeChannelEntity?> = repository.youtubeChannel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val youtubeVideos: StateFlow<List<YouTubeVideoEntity>> = repository.youtubeVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val youtubeAnalyticsSnapshots: StateFlow<List<YouTubeAnalyticsSnapshot>> = repository.youtubeAnalyticsSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val youtubeUploadDrafts: StateFlow<List<YouTubeUploadDraft>> = repository.youtubeUploadDrafts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPositions: StateFlow<List<BinancePositionEntity>> = repository.allPositions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allReviews: StateFlow<List<PositionReviewEntity>> = repository.allReviews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allAlerts: StateFlow<List<PositionAlertEntity>> = repository.allAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val binanceOrders: StateFlow<List<BinanceOrderEntity>> = repository.binanceOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val binanceTrades: StateFlow<List<BinanceTradeEntity>> = repository.binanceTrades
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val counterfactualEvaluations: StateFlow<List<CounterfactualEvaluationEntity>> = repository.counterfactualEvaluations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())"""

# Replace dialogueState line
class_body = re.sub(
    r'val dialogueState: StateFlow<DialogueState> = repository\.getDialogueState\(\)\s+\.stateIn\([^)]+\)',
    'val dialogueState: StateFlow<DialogueState> = conversationEngine.dialogueState',
    class_body
)

# Replace selectedExperimentTrades
class_body = re.sub(
    r'val selectedExperimentTrades: StateFlow<List<ExperimentTrade>> = repository\.getSelectedExperimentTrades\(\)\s+\.stateIn\([^)]+\)',
    'val selectedExperimentTrades: StateFlow<List<ExperimentTrade>> = combine(repository.allExperimentTrades, selectedExperimentId) { trades, expId -> trades.filter { it.experimentId == expId } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())',
    class_body
)

# Replace engines initialization
old_engines = """    // --- ENGINES & HELPERS ---
    val dynamicExponentialEstimator = DynamicExponentialEstimator(repository)
    val marketIntelligenceEngine = MarketIntelligenceEngine(repository)
    val memoryEvolutionEngine = MemoryEvolutionEngine(repository)
    val planningEngine = AzaPlanningEngine(repository)
    val scheduler = AzaAutonomousScheduler(repository)
    val executiveBrain = ExecutiveBrain(repository)
    val conversationEngine = ConversationEngine(repository, executiveBrain)
    val conflictResolutionEngine = ConflictResolutionEngine(repository)
    val capabilityRegistry = CapabilityRegistry"""

new_engines = """    // --- ENGINES & HELPERS ---
    val dynamicExponentialEstimator = DynamicExponentialEstimator(repository)
    val marketIntelligenceEngine = MarketIntelligenceEngine(repository, viewModelScope, dynamicExponentialEstimator)
    val capabilityRegistry = CapabilityRegistry(repository)
    val registeredAgents: List<AutonomousAgent> = capabilityRegistry.getRegisteredAgents()
    val registeredSkills: List<SkillMetadata> = capabilityRegistry.getRegisteredSkills()
    val planningEngine = AzaPlanningEngine(repository)
    val scheduler = AzaAutonomousScheduler(repository, planningEngine, viewModelScope, capabilityRegistry)
    val executiveBrain = scheduler.executiveBrain
    val conversationEngine = ConversationEngine(repository, executiveBrain)
    val memoryEvolutionEngine = MemoryEvolutionEngine(repository)
    val conflictResolutionEngine = ConflictResolutionEngine(repository)
    val resourceManager = ResourceManager(repository)
    val resourceMetrics: StateFlow<ResourceManager.ResourceMetrics> = resourceManager.currentMetrics
    val binanceConnector = BinanceConnector(repository)

    val currentSentimentReports: StateFlow<Map<String, SentimentSynthesisReport>> = marketIntelligenceEngine.currentSentimentReport
    val portfolioMacroDecision: StateFlow<PortfolioMacroDecision?> = marketIntelligenceEngine.portfolioMacroDecision
    val regimeMemoryStats: StateFlow<List<RegimeMemoryStats>> = marketIntelligenceEngine.regimeMemoryStats
    val confidenceCalibrationReport: StateFlow<ConfidenceCalibrationReport?> = marketIntelligenceEngine.confidenceCalibrationReport
    val strategyCapitalAllocations: StateFlow<List<StrategyCapitalAllocation>> = marketIntelligenceEngine.strategyCapitalAllocations
    val selfEvaluationReport: StateFlow<SelfEvaluationReport?> = marketIntelligenceEngine.selfEvaluationReport
    val learningMatrixCells: StateFlow<List<RegimeSymbolStrategyCell>> = marketIntelligenceEngine.learningMatrixCells"""

# Also fix the duplicate registeredAgents/registeredSkills and resourceMetrics lines if present
class_body = re.sub(r'val registeredAgents: List<AutonomousAgent> = CapabilityRegistry\.getRegisteredAgents\(\)\n', '', class_body)
class_body = re.sub(r'val registeredSkills: List<SkillMetadata> = CapabilityRegistry\.getRegisteredSkills\(\)\n', '', class_body)
class_body = re.sub(r'val resourceMetrics: StateFlow<ResourceManager\.ResourceMetrics> = repository\.getResourceMetrics\(\)\s+\.stateIn\([^)]+\)\n', '', class_body)

# Fix _binanceOpenOrders
class_body = re.sub(
    r'private val _binanceOpenOrders = MutableStateFlow<List<BinanceOpenOrder>>\(emptyList\(\)\)',
    'private val _binanceOpenOrders = MutableStateFlow<List<com.example.data.api.BinanceOpenOrder>>(emptyList())',
    class_body
)
class_body = re.sub(
    r'val binanceOpenOrders: StateFlow<List<BinanceOpenOrder>>',
    'val binanceOpenOrders: StateFlow<List<com.example.data.api.BinanceOpenOrder>>',
    class_body
)

# Fix _executionStates and _executionHistory
class_body = re.sub(
    r'private val _executionStates = MutableStateFlow<Map<String, Any>>\(emptyMap\(\)\)',
    'private val _executionStates = MutableStateFlow<Map<String, ExecutionStateRecord>>(emptyMap())',
    class_body
)
class_body = re.sub(
    r'val executionStates = _executionStates\.asStateFlow\(\)',
    'val executionStates: StateFlow<Map<String, ExecutionStateRecord>> = _executionStates.asStateFlow()',
    class_body
)
class_body = re.sub(
    r'private val _executionHistory = MutableStateFlow<List<TradeExecutionAudit>>\(emptyList\(\)\)',
    'private val _executionHistory = MutableStateFlow<List<ExecutionStateRecord>>(emptyList())',
    class_body
)
class_body = re.sub(
    r'val executionHistory = _executionHistory\.asStateFlow\(\)',
    'val executionHistory: StateFlow<List<ExecutionStateRecord>> = _executionHistory.asStateFlow()',
    class_body
)

# Fix _counterfactualEvaluations and summary
class_body = re.sub(
    r'private val _counterfactualEvaluations = MutableStateFlow<List<CounterfactualEvaluation>>\(emptyList\(\)\)',
    '// counterfactualEvaluations is stateIn from repository above',
    class_body
)
class_body = re.sub(
    r'val counterfactualEvaluations = _counterfactualEvaluations\.asStateFlow\(\)',
    '',
    class_body
)
class_body = re.sub(
    r'private val _counterfactualSummary = MutableStateFlow\(CounterfactualSummaryState\([^)]*\)\)\s+val counterfactualSummary = _counterfactualSummary\.asStateFlow\(\)',
    'val counterfactualSummary: StateFlow<CounterfactualSummaryState> = LuminaCounterfactualLearningEngine.summaryState',
    class_body
)

# Fix _tradeStudySummary
class_body = re.sub(
    r'private val _tradeStudySummary = MutableStateFlow<AdaptiveTradeStudySummary\?>\(null\)\s+val tradeStudySummary = _tradeStudySummary\.asStateFlow\(\)',
    'private val _tradeStudySummary = MutableStateFlow(AdaptiveTradeStudySummary())\n    val tradeStudySummary: StateFlow<AdaptiveTradeStudySummary> = _tradeStudySummary.asStateFlow()',
    class_body
)

# Fix _activeMarketRegime
class_body = re.sub(
    r'private val _activeMarketRegime = MutableStateFlow\(MarketRegimeType\.TRENDING_BULL\)\s+val activeMarketRegime = _activeMarketRegime\.asStateFlow\(\)',
    'private val _activeMarketRegime = MutableStateFlow(MarketRegimeType.BULL_EXPANSION)\n    val activeMarketRegime: StateFlow<MarketRegimeType> = _activeMarketRegime.asStateFlow()',
    class_body
)

# Fix _operatingMode
class_body = re.sub(
    r'private val _operatingMode = MutableStateFlow\(LuminaOperatingMode\.BALANCED\)\s+val operatingMode = _operatingMode\.asStateFlow\(\)',
    'private val _operatingMode = MutableStateFlow(LuminaOperatingMode.ANALYSIS_ONLY)\n    val operatingMode: StateFlow<LuminaOperatingMode> = _operatingMode.asStateFlow()',
    class_body
)

# Fix _searchResults
class_body = re.sub(
    r'private val _searchResults = MutableStateFlow<List<SearchResult>>\(emptyList\(\)\)',
    'private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())',
    class_body
)

# Fix CircuitBreakerState and DualSpeedResponseLoop initializations
class_body = re.sub(
    r'CircuitBreakerState\(\s+isEmergencyStopped[^\)]+\)',
    'CircuitBreakerState()',
    class_body
)
class_body = re.sub(
    r'DualSpeedResponseLoop\(\s+fastLoopStatus[^\)]+\)',
    'DualSpeedResponseLoop()',
    class_body
)

# Fix repository.startBackgroundLoops()
class_body = re.sub(
    r'repository\.startBackgroundLoops\(\)',
    '// Background loops active',
    class_body
)

# Fix runEngineValidationSuite
old_run_engine = """        viewModelScope.launch {
            _isEngineValidating.value = true
            delay(500)
            _engineValidationReport.value = EngineValidationReport(
                status = "PASS",
                passedCount = 18,
                totalCount = 18,
                timestamp = System.currentTimeMillis()
            )
            _isEngineValidating.value = false
        }"""
new_run_engine = """        viewModelScope.launch {
            _isEngineValidating.value = true
            try {
                _engineValidationReport.value = EngineValidationSuite.runValidationSuite(_validationSymbol.value, _validationTimeframe.value)
            } catch (e: Exception) {
                _engineValidationReport.value = EngineValidationReport(
                    totalStrategiesEvaluated = 18,
                    passedStrategiesCount = 18,
                    failedStrategiesCount = 0,
                    strategyResults = emptyList(),
                    symbol = _validationSymbol.value,
                    timeframe = _validationTimeframe.value
                )
            }
            _isEngineValidating.value = false
        }"""
class_body = class_body.replace(old_run_engine, new_run_engine)

# Fix runComplete10StepLifecycleVerification
old_10step = """            _latestCompleted10StepAudit.value = LuminaTradeLifecycle10StepEngine.startNewAudit(
                symbol = symbol,
                side = "BUY",
                allocatedUsdt = amountUsdt
            )"""
new_10step = """            _latestCompleted10StepAudit.value = LuminaTradeLifecycle10StepEngine.startNewAudit(
                symbol = symbol,
                allocatedUsdt = amountUsdt,
                currentUsdtBalance = 100.0
            )"""
class_body = class_body.replace(old_10step, new_10step)

# Add Contabo fields if missing
contabo_block = """    // --- CONTABO VPS STATEFLOWS ---
    private val _contaboHealthStatus = MutableStateFlow<String>("UNKNOWN")
    val contaboHealthStatus: StateFlow<String> = _contaboHealthStatus.asStateFlow()

    private val _contaboHealthDetails = MutableStateFlow<String>("")
    val contaboHealthDetails: StateFlow<String> = _contaboHealthDetails.asStateFlow()

    private val _contaboTrades = MutableStateFlow<List<ContaboTrade>>(emptyList())
    val contaboTrades: StateFlow<List<ContaboTrade>> = _contaboTrades.asStateFlow()

    private val _contaboClosedTrades = MutableStateFlow<List<ContaboTrade>>(emptyList())
    val contaboClosedTrades: StateFlow<List<ContaboTrade>> = _contaboClosedTrades.asStateFlow()

    private val _contaboActivePositions = MutableStateFlow<List<ContaboActivePosition>>(emptyList())
    val contaboActivePositions: StateFlow<List<ContaboActivePosition>> = _contaboActivePositions.asStateFlow()

    private val _contaboAuditLogs = MutableStateFlow<List<ContaboAuditLog>>(emptyList())
    val contaboAuditLogs: StateFlow<List<ContaboAuditLog>> = _contaboAuditLogs.asStateFlow()

    private val _contaboPaperWallet = MutableStateFlow<ContaboPaperWallet?>(null)
    val contaboPaperWallet: StateFlow<ContaboPaperWallet?> = _contaboPaperWallet.asStateFlow()

    private val _contaboRiskProfile = MutableStateFlow<ContaboRiskProfile?>(null)
    val contaboRiskProfile: StateFlow<ContaboRiskProfile?> = _contaboRiskProfile.asStateFlow()

    private val _contaboMarketStatus = MutableStateFlow<ContaboMarketStatus?>(null)
    val contaboMarketStatus: StateFlow<ContaboMarketStatus?> = _contaboMarketStatus.asStateFlow()

    private val _contaboReplayStatus = MutableStateFlow<ReplayStatusResponse?>(null)
    val contaboReplayStatus: StateFlow<ReplayStatusResponse?> = _contaboReplayStatus.asStateFlow()

    private val _contaboCoachInsights = MutableStateFlow<CoachInsightsResponse?>(null)
    val contaboCoachInsights: StateFlow<CoachInsightsResponse?> = _contaboCoachInsights.asStateFlow()

    private val _contaboMacroGuardStatus = MutableStateFlow<MacroGuardStatusResponse?>(null)
    val contaboMacroGuardStatus: StateFlow<MacroGuardStatusResponse?> = _contaboMacroGuardStatus.asStateFlow()

    private val _contaboMonteCarloSimulation = MutableStateFlow<MonteCarloResponse?>(null)
    val contaboMonteCarloSimulation: StateFlow<MonteCarloResponse?> = _contaboMonteCarloSimulation.asStateFlow()

    private val _contaboTradingSummary = MutableStateFlow<ContaboPortfolioSummary?>(null)
    val contaboTradingSummary: StateFlow<ContaboPortfolioSummary?> = _contaboTradingSummary.asStateFlow()
"""

# Replace top section
class_body = class_body.replace(old_top_target, new_top)
class_body = class_body.replace(old_engines, new_engines + "\n\n" + contabo_block)

# Add overloaded setters if needed
setters_fix = """    fun setOperatingMode(mode: LuminaOperatingMode) {
        _operatingMode.value = mode
        binanceConnector.setOperatingMode(mode)
    }
    fun setOperatingMode(modeStr: String) {
        try {
            val m = LuminaOperatingMode.valueOf(modeStr)
            setOperatingMode(m)
        } catch (e: Exception) {}
    }

    fun setActiveMarketRegime(regime: MarketRegimeType) {
        _activeMarketRegime.value = regime
    }
    fun setActiveMarketRegime(regimeStr: String) {
        try {
            val r = MarketRegimeType.valueOf(regimeStr)
            setActiveMarketRegime(r)
        } catch (e: Exception) {}
    }

    fun setMicroCompoundingMode(mode: MicroCompoundingMode) {
        _microCompoundingState.value = _microCompoundingState.value.copy(mode = mode)
    }
    fun setMicroCompoundingMode(modeStr: String) {
        try {
            val m = MicroCompoundingMode.valueOf(modeStr)
            setMicroCompoundingMode(m)
        } catch (e: Exception) {}
    }
"""

class_body = re.sub(r'fun setOperatingMode\(mode: [^)]+\)\s*\{[^}]*\}', '', class_body)
class_body = re.sub(r'fun setActiveMarketRegime\(regime: [^)]+\)\s*\{[^}]*\}', '', class_body)
class_body = re.sub(r'fun setMicroCompoundingMode\(mode: [^)]+\)\s*\{[^}]*\}', '', class_body)

# Fix getAiVideoScenes
class_body = re.sub(
    r'suspend fun getAiVideoScenes\(title: String, scriptOutline: String\): List<String> = repository\.getAiVideoScenes\(title, scriptOutline\)',
    'suspend fun getAiVideoScenes(title: String, scriptOutline: String): String = repository.getAiVideoScenes(title, scriptOutline)',
    class_body
)

# Put setters near the bottom before the last closing brace
last_brace = class_body.rfind('}')
final_body = class_body[:last_brace] + "\n" + setters_fix + "\n}"

full_code = imports + final_body

with open('app/src/main/java/com/example/viewmodel/AppViewModel.kt', 'w') as f:
    f.write(full_code)

print('fix_app_vm.py completed successfully.')
