package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PortfolioAsset::class,
        ContentItem::class,
        FreelanceGig::class,
        TransactionRecord::class,
        AccountLinking::class,
        ChatMessage::class,
        AzaChannel::class,
        AzaObjective::class,
        AzaAgentTask::class,
        ExecutionRecord::class,
        AzaLearningRecord::class,
        StrategyEntity::class,
        StrategyBacktestReport::class,
        StrategyOptimizationRecord::class,
        TradeAuditLog::class,
        ExportedReportHistory::class,
        Experiment::class,
        ExperimentTrade::class,
        KnowledgeEntry::class,
        SkillEntry::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao
    abstract fun contentDao(): ContentDao
    abstract fun gigDao(): GigDao
    abstract fun transactionDao(): TransactionDao
    abstract fun accountLinkingDao(): AccountLinkingDao
    abstract fun chatDao(): ChatDao
    abstract fun channelDao(): ChannelDao
    abstract fun azaGoalDao(): AzaGoalDao
    abstract fun executionRecordDao(): ExecutionRecordDao
    abstract fun azaLearningRecordDao(): AzaLearningRecordDao
    abstract fun strategyDao(): StrategyDao
    abstract fun experimentDao(): ExperimentDao
    abstract fun knowledgeDao(): KnowledgeDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nexus_hub_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabasePrepopulateCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabasePrepopulateCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database)
                }
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            // Prepopulate Account Info
            db.accountLinkingDao().insertAccountInfo(
                AccountLinking(
                    id = 1,
                    totalBalance = 4250.75,
                    isTradingLinked = true,
                    isSocialLinked = false,
                    isFreelanceLinked = false
                )
            )

            // Prepopulate Portfolio Assets
            db.portfolioDao().insertAsset(
                PortfolioAsset("BTC", "Bitcoin", 0.082, 64200.0, "CRYPTO", 67450.0)
            )
            db.portfolioDao().insertAsset(
                PortfolioAsset("ETH", "Ethereum", 1.25, 3100.0, "CRYPTO", 3480.0)
            )
            db.portfolioDao().insertAsset(
                PortfolioAsset("AAPL", "Apple Inc.", 8.0, 175.5, "STOCK", 192.3)
            )
            db.portfolioDao().insertAsset(
                PortfolioAsset("NVDA", "NVIDIA Corp.", 12.0, 820.0, "STOCK", 945.5)
            )

            // Prepopulate Freelance Gigs
            db.gigDao().insertGig(
                FreelanceGig(
                    title = "Tech Blog AI Article Writer",
                    description = "Write a comprehensive 1,500-word blog post reviewing the latest advancements in local TPU processors and Google Gemini Nano. Requires a deeply technical background.",
                    budget = 350.00,
                    clientName = "FutureTech Media",
                    status = "AVAILABLE"
                )
            )
            db.gigDao().insertGig(
                FreelanceGig(
                    title = "YouTube Video Script: Trading Bot Setup",
                    description = "Develop an engaging, high-retention 10-minute video script explaining how to connect financial feeds via API to make a mock trading app. Include visual suggestions.",
                    budget = 180.00,
                    clientName = "Alex TechVlog",
                    status = "AVAILABLE"
                )
            )
            db.gigDao().insertGig(
                FreelanceGig(
                    title = "Social Reel Campaign: Finance App Launch",
                    description = "Need a creator to design and record 3 engaging short video reels (TikTok, YT Shorts, Instagram) highlighting security features of modern digital wallets.",
                    budget = 500.00,
                    clientName = "StripeVentures Ltd",
                    status = "AVAILABLE"
                )
            )
            db.gigDao().insertGig(
                FreelanceGig(
                    title = "Jetpack Compose Widget Customization",
                    description = "Fix custom canvas performance of stock price indicators. We currently see frame drops during scrolling inside LazyList components.",
                    budget = 750.00,
                    clientName = "SaaS Analytics",
                    status = "AVAILABLE"
                )
            )

            // Prepopulate Content Ideas
            db.contentDao().insertContent(
                ContentItem(
                    title = "Mastering AI-Powered Market Analysis",
                    platform = "YOUTUBE",
                    scriptOutline = "Intro (0:00-1:00) - Hook about generative AI in modern trading.\nKey Concept (1:00-5:00) - Real-time market analytics using Google Gemini.\nDemo (5:00-8:00) - Portfolio balancing dashboards.\nOutro (8:00-10:00) - Call to subscribe and link.",
                    status = "DRAFT",
                    engagementCount = 0
                )
            )

            // Prepopulate transaction history
            db.transactionDao().insertTransaction(
                TransactionRecord(
                    description = "Linked Initial Trading Account Balance",
                    amount = 4000.00,
                    type = "DEPOSIT"
                )
            )
            db.transactionDao().insertTransaction(
                TransactionRecord(
                    description = "Payout: Freelance Video Promo Outline",
                    amount = 250.75,
                    type = "FREELANCE"
                )
            )

            // Prepopulate initial chat dialogue from Aza
            db.chatDao().insertMessage(
                ChatMessage(
                    sender = "AZA",
                    text = "Hello! I am Aza, your conversational AI partner. 🚀\n\nI can help you analyze markets, generate video scripts, draft freelance proposals, and execute complex business tasks in real time.\n\nTry speaking to me naturally, or try these tasks directly:\n• *\"Buy 0.02 BTC\"* or *\"Sell 2 AAPL\"*\n• *\"Draft a script about Room Database on TikTok\"*\n• *\"Write a proposal for the Tech Blog Writer gig\"*"
                )
            )

            // Prepopulate autonomous channels
            db.channelDao().insertChannels(
                listOf(
                    AzaChannel(
                        platform = "YOUTUBE",
                        handle = "@aza_growth_hq",
                        followersCount = 2450,
                        totalVideosPublished = 12,
                        totalLikesAccumulated = 4800,
                        targetSegment = "Developers & Technical Leads",
                        autonomousInsight = "YouTube audiences respond 2.3x better to detailed coding breakdown scripts (over 1000 words) compared to superficial generic topics."
                    ),
                    AzaChannel(
                        platform = "INSTAGRAM",
                        handle = "@aza_design_vibe",
                        followersCount = 5900,
                        totalVideosPublished = 18,
                        totalLikesAccumulated = 12400,
                        targetSegment = "Design Enthusiasts & Bloggers",
                        autonomousInsight = "Instagram reels perform best when styled with premium, high-contrast dark visual screenshots and a catchy 3-second visual teaser."
                    ),
                    AzaChannel(
                        platform = "TIKTOK",
                        handle = "@aza_marketing_fast",
                        followersCount = 11800,
                        totalVideosPublished = 25,
                        totalLikesAccumulated = 39000,
                        targetSegment = "Gen-Z Micro-Entrepreneurs",
                        autonomousInsight = "TikTok's algorithm favors ultra-short audio-visual templates (under 40s) emphasizing punchy hooks like 'AVOID this mistake...' in the first 2 seconds."
                    )
                )
            )

            // Prepopulate Strategy Metadata
            db.strategyDao().insertStrategies(
                listOf(
                    StrategyEntity(
                        strategyName = "Trend Following",
                        version = "1.0.0",
                        description = "Follows strong upward market momentum using exponential moving average (EMA) crossovers.",
                        supportedMarkets = "BTC, ETH, SOL, AAPL, NVDA",
                        riskProfile = "Conservative",
                        timeframe = "4H",
                        requiredIndicators = "EMA (12, 26, 200), RSI (14)",
                        isActive = true,
                        totalPaperTrades = 5,
                        winningPaperTrades = 4,
                        paperWinRate = 80.0,
                        paperNetProfit = 245.50,
                        avgLatencyMs = 120,
                        confidence = 0.85,
                        ranking = 1,
                        currentSignal = "BUY - BTC @ $67,450"
                    ),
                    StrategyEntity(
                        strategyName = "Breakout",
                        version = "1.1.0",
                        description = "Captures sharp price movements exceeding local resistance/support boundaries.",
                        supportedMarkets = "BTC, ETH, high-volatility assets",
                        riskProfile = "Aggressive",
                        timeframe = "1H",
                        requiredIndicators = "Bollinger Bands, Average True Range (ATR)",
                        isActive = false,
                        totalPaperTrades = 8,
                        winningPaperTrades = 5,
                        paperWinRate = 62.5,
                        paperNetProfit = 312.10,
                        avgLatencyMs = 145,
                        confidence = 0.75,
                        ranking = 2,
                        currentSignal = "HOLD"
                    ),
                    StrategyEntity(
                        strategyName = "Mean Reversion",
                        version = "1.0.2",
                        description = "Exploits temporary overbought/oversold price extensions to return to structural equilibrium.",
                        supportedMarkets = "AAPL, stable coin pairings",
                        riskProfile = "Moderate",
                        timeframe = "30M",
                        requiredIndicators = "RSI, Stochastic Oscillator, Pivot Points",
                        isActive = false,
                        totalPaperTrades = 3,
                        winningPaperTrades = 1,
                        paperWinRate = 33.3,
                        paperNetProfit = -45.00,
                        avgLatencyMs = 95,
                        confidence = 0.60,
                        ranking = 3,
                        currentSignal = "HOLD"
                    )
                )
            )

            // Prepopulate Validation Lab Experiments
            val exp1Id = db.experimentDao().insertExperiment(
                Experiment(
                    name = "Trend Following vs Mean Reversion",
                    description = "Rigorous comparison of EMA crossover strategy against stochastic mean reversion in volatile BTC markets.",
                    strategyName = "Trend Following",
                    market = "BTC",
                    timeframe = "1h",
                    riskLevel = "Low",
                    isActive = true,
                    initialBalance = 10000.0,
                    currentBalance = 10380.0,
                    dailyReturn = 0.85,
                    weeklyReturn = 3.80,
                    monthlyReturn = 12.45,
                    winRate = 75.0,
                    profitFactor = 2.45,
                    sharpeRatio = 1.82,
                    maxDrawdown = 1.2,
                    averageTradeDuration = 105.0,
                    totalSimulatedFees = 32.50,
                    totalSimulatedSlippage = 18.20,
                    netProfit = 380.0,
                    riskAdjustedReturn = 1.52,
                    stabilityScore = 88.0
                )
            ).toInt()

            val exp2Id = db.experimentDao().insertExperiment(
                Experiment(
                    name = "BTC vs ETH",
                    description = "Continuous multi-asset breakout evaluations measuring fee slippage drag and liquidity variations.",
                    strategyName = "Breakout",
                    market = "ETH",
                    timeframe = "15m",
                    riskLevel = "Medium",
                    isActive = true,
                    initialBalance = 10000.0,
                    currentBalance = 9910.0,
                    dailyReturn = -0.45,
                    weeklyReturn = -0.90,
                    monthlyReturn = -2.10,
                    winRate = 33.3,
                    profitFactor = 0.65,
                    sharpeRatio = -0.42,
                    maxDrawdown = 3.5,
                    averageTradeDuration = 42.0,
                    totalSimulatedFees = 48.00,
                    totalSimulatedSlippage = 24.50,
                    netProfit = -90.0,
                    riskAdjustedReturn = -0.32,
                    stabilityScore = 45.0
                )
            ).toInt()

            val exp3Id = db.experimentDao().insertExperiment(
                Experiment(
                    name = "15m vs 1h",
                    description = "Analyzing fractal resolution performance using identical trend configurations.",
                    strategyName = "Trend Following",
                    market = "BTC",
                    timeframe = "1h",
                    riskLevel = "Medium",
                    isActive = true,
                    initialBalance = 10000.0,
                    currentBalance = 10550.0,
                    dailyReturn = 1.20,
                    weeklyReturn = 5.50,
                    monthlyReturn = 18.20,
                    winRate = 80.0,
                    profitFactor = 3.20,
                    sharpeRatio = 2.45,
                    maxDrawdown = 1.8,
                    averageTradeDuration = 180.0,
                    totalSimulatedFees = 42.00,
                    totalSimulatedSlippage = 12.80,
                    netProfit = 550.0,
                    riskAdjustedReturn = 2.15,
                    stabilityScore = 92.0
                )
            ).toInt()

            val exp4Id = db.experimentDao().insertExperiment(
                Experiment(
                    name = "Low Risk vs Medium Risk",
                    description = "Quantifying risk-reward tradeoffs by comparing conservative stop thresholds against wide targets.",
                    strategyName = "Mean Reversion",
                    market = "BTC",
                    timeframe = "1D",
                    riskLevel = "Low",
                    isActive = true,
                    initialBalance = 10000.0,
                    currentBalance = 10120.0,
                    dailyReturn = 0.30,
                    weeklyReturn = 1.20,
                    monthlyReturn = 4.50,
                    winRate = 60.0,
                    profitFactor = 1.80,
                    sharpeRatio = 1.15,
                    maxDrawdown = 0.8,
                    averageTradeDuration = 240.0,
                    totalSimulatedFees = 15.00,
                    totalSimulatedSlippage = 6.20,
                    netProfit = 120.0,
                    riskAdjustedReturn = 0.95,
                    stabilityScore = 79.0
                )
            ).toInt()

            // Prepopulate Trades for Experiment 1
            db.experimentDao().insertExperimentTrade(
                ExperimentTrade(
                    experimentId = exp1Id,
                    symbol = "BTC",
                    direction = "BUY",
                    entryPrice = 67200.0,
                    exitPrice = 68400.0,
                    quantity = 0.25,
                    profitLoss = 300.0,
                    fees = 12.0,
                    slippage = 6.5,
                    durationMin = 120,
                    entryReason = "Bulish EMA Cross Over confirmed with RSI at 55",
                    exitReason = "Hit take profit target level",
                    confidence = 0.85,
                    marketSnapshot = "Regime: TRENDING, Volume: HIGH, RSI: 55",
                    alternativeAction = "Exit logic operated correctly. Standard trailing stop could have captured 3% additional upside but would introduce risk."
                )
            )
            db.experimentDao().insertExperimentTrade(
                ExperimentTrade(
                    experimentId = exp1Id,
                    symbol = "BTC",
                    direction = "BUY",
                    entryPrice = 68100.0,
                    exitPrice = 67800.0,
                    quantity = 0.25,
                    profitLoss = -75.0,
                    fees = 11.5,
                    slippage = 5.2,
                    durationMin = 45,
                    entryReason = "Breakout of local resistance cluster",
                    exitReason = "Stop loss triggered on minor pullback",
                    confidence = 0.78,
                    marketSnapshot = "Regime: HIGH_VOLATILITY, Volume: MEDIUM, RSI: 64",
                    alternativeAction = "The entry was premature as local volume was fading. Setting stop loss based on ATR instead of static dollar amounts would have prevented premature liquidation."
                )
            )
            db.experimentDao().insertExperimentTrade(
                ExperimentTrade(
                    experimentId = exp1Id,
                    symbol = "BTC",
                    direction = "BUY",
                    entryPrice = 67500.0,
                    exitPrice = 68120.0,
                    quantity = 0.25,
                    profitLoss = 155.0,
                    fees = 9.0,
                    slippage = 6.5,
                    durationMin = 150,
                    entryReason = "Support bounce on 200 EMA line",
                    exitReason = "EMA trailing reversal exit signal",
                    confidence = 0.82,
                    marketSnapshot = "Regime: RANGE, Volume: LOW, RSI: 48",
                    alternativeAction = "None. High execution quality. Slippage was slightly elevated due to low liquidity at the session transition."
                )
            )

            // Prepopulate Trades for Experiment 2 (Underperforming)
            db.experimentDao().insertExperimentTrade(
                ExperimentTrade(
                    experimentId = exp2Id,
                    symbol = "ETH",
                    direction = "BUY",
                    entryPrice = 3480.0,
                    exitPrice = 3410.0,
                    quantity = 1.5,
                    profitLoss = -105.0,
                    fees = 16.0,
                    slippage = 8.5,
                    durationMin = 30,
                    entryReason = "Breakout on high volume",
                    exitReason = "Stop loss hit on aggressive correction",
                    confidence = 0.70,
                    marketSnapshot = "Regime: VOLATILE_RANGE, Volume: HIGH, RSI: 71",
                    alternativeAction = "Entering a breakout trade on overbought RSI (71) is highly risky. Introduce a volume filter to ensure breakout is supported by systemic flows."
                )
            )
            db.experimentDao().insertExperimentTrade(
                ExperimentTrade(
                    experimentId = exp2Id,
                    symbol = "ETH",
                    direction = "SELL",
                    entryPrice = 3400.0,
                    exitPrice = 3410.0,
                    quantity = 1.5,
                    profitLoss = -15.0,
                    fees = 16.0,
                    slippage = 8.0,
                    durationMin = 15,
                    entryReason = "Short breakdown below support",
                    exitReason = "Rebound stop loss triggered",
                    confidence = 0.65,
                    marketSnapshot = "Regime: CHOPPY, Volume: MEDIUM, RSI: 42",
                    alternativeAction = "Shorting in a low-spread regime causes rapid whip-saws. Avoid trading when spreads are tight and volatility is mean-reverting."
                )
            )
            db.experimentDao().insertExperimentTrade(
                ExperimentTrade(
                    experimentId = exp2Id,
                    symbol = "ETH",
                    direction = "BUY",
                    entryPrice = 3420.0,
                    exitPrice = 3440.0,
                    quantity = 1.5,
                    profitLoss = 30.0,
                    fees = 16.0,
                    slippage = 8.0,
                    durationMin = 81,
                    entryReason = "Stochastic oversold reversal",
                    exitReason = "Dynamic profit target reached",
                    confidence = 0.74,
                    marketSnapshot = "Regime: MEAN_REVERTING, Volume: LOW, RSI: 31",
                    alternativeAction = "Aza reversion model succeeded, but fees devoured 50% of nominal profits. Lower the trade frequency on high-fee instruments."
                )
            )

            // Prepopulate Knowledge Base Lessons
            db.knowledgeDao().insertKnowledge(
                KnowledgeEntry(
                    title = "EMA Crossovers under high-liquidity trend regimes",
                    category = "Strategy",
                    description = "Long-term trend following works highly effectively on BTC when average 24h trading volume is above standard baseline.",
                    evidence = "75% win-rate on Trend Following vs Mean Reversion experiment.",
                    confidenceScore = 0.85,
                    timesConfirmed = 4,
                    isConfirmed = true
                )
            )
            db.knowledgeDao().insertKnowledge(
                KnowledgeEntry(
                    title = "Avoiding breakout buys during overbought RSI conditions",
                    category = "Risk",
                    description = "Buying breakouts when RSI exceeds 70 on short-term scales results in high stop-out frequency.",
                    evidence = "Experiment 2 (ETH Breakout) had multiple stop-out failures due to entries during high-volatility range tops.",
                    confidenceScore = 0.72,
                    timesConfirmed = 2,
                    isConfirmed = true
                )
            )
            db.knowledgeDao().insertKnowledge(
                KnowledgeEntry(
                    title = "Stochastic mean reversion works poorly on high momentum assets",
                    category = "Regime",
                    description = "Entering counter-trend positions during massive macro runs triggers rapid drawdowns.",
                    evidence = "Backtesting sessions indicate -12.4% drawdown when shorting overextended upward BTC runs.",
                    confidenceScore = 0.78,
                    timesConfirmed = 3,
                    isConfirmed = true
                )
            )

            // Prepopulate Skill Library
            val skills = listOf(
                SkillEntry(
                    name = "Detect Trend",
                    description = "Identify general market direction using EMAs and moving averages.",
                    successRate = 75.0,
                    failureRate = 25.0,
                    confidence = 0.8,
                    associatedRegimes = "TRENDING"
                ),
                SkillEntry(
                    name = "Detect Range",
                    description = "Identify support/resistance bounds in steady, non-trending markets.",
                    successRate = 60.0,
                    failureRate = 40.0,
                    confidence = 0.65,
                    associatedRegimes = "RANGE, MEAN_REVERTING"
                ),
                SkillEntry(
                    name = "Detect Breakout",
                    description = "Identify momentum expansion exceeding historical limits.",
                    successRate = 55.0,
                    failureRate = 45.0,
                    confidence = 0.58,
                    associatedRegimes = "VOLATILE, BREAKOUT"
                ),
                SkillEntry(
                    name = "Detect Reversal",
                    description = "Pinpoint inflection points of overextended swings.",
                    successRate = 62.0,
                    failureRate = 38.0,
                    confidence = 0.64,
                    associatedRegimes = "MEAN_REVERTING"
                ),
                SkillEntry(
                    name = "Identify Fake Breakout",
                    description = "Differentiate between real momentum expansion and short-term liquidity sweeps.",
                    successRate = 50.0,
                    failureRate = 50.0,
                    confidence = 0.50,
                    associatedRegimes = "VOLATILE"
                ),
                SkillEntry(
                    name = "Position Sizing",
                    description = "Calculate optimal asset allocation dynamically using ATR risk parameters.",
                    successRate = 85.0,
                    failureRate = 15.0,
                    confidence = 0.88,
                    associatedRegimes = "ANY"
                ),
                SkillEntry(
                    name = "Risk Calculation",
                    description = "Set strict stop losses and take profit bounds based on historical volatility metrics.",
                    successRate = 88.0,
                    failureRate = 12.0,
                    confidence = 0.90,
                    associatedRegimes = "ANY"
                ),
                SkillEntry(
                    name = "Trade Exit Optimization",
                    description = "Optimize exit points using trailing stops and profit scaling.",
                    successRate = 70.0,
                    failureRate = 30.0,
                    confidence = 0.75,
                    associatedRegimes = "TRENDING, VOLATILE"
                )
            )
            for (skill in skills) {
                db.knowledgeDao().insertSkill(skill)
            }
        }
    }
}
