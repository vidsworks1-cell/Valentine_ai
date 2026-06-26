package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.aza.strategy.*
import com.example.data.database.AppDatabase
import com.example.data.database.StrategyEntity
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
class StrategyPhase5Test {

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
    fun `test Market Regime Detection`() {
        println("--- TEST: Market Regime Detection ---")
        
        // Trending Upward series
        val trendingPrices = List(30) { index -> 100.0 + (index * 2.5) }
        val trendingRegime = StrategyEvaluationEngine.detectMarketRegime(trendingPrices)
        println("Trending Prices detected as: $trendingRegime")
        assertEquals("Trending", trendingRegime)

        // Ranging (Flat oscillating) series
        val rangingPrices = listOf(
            100.0, 101.0, 99.0, 100.0, 101.0, 99.0, 100.0, 101.0, 99.0, 100.0,
            101.0, 99.0, 100.0, 101.0, 99.0, 100.0, 101.0, 99.0, 100.0, 101.0,
            99.0, 100.0, 101.0, 99.0, 100.0, 101.0, 99.0, 100.0, 101.0, 99.0
        )
        val rangingRegime = StrategyEvaluationEngine.detectMarketRegime(rangingPrices)
        println("Ranging Prices detected as: $rangingRegime")
        assertEquals("Ranging", rangingRegime)
    }

    @Test
    fun `test Strategy Ranking Score`() {
        println("--- TEST: Strategy Ranking Score ---")
        
        // High winrate, high profit factor, high Sharpe, low drawdown
        val scoreHigh = StrategyEvaluationEngine.calculateRankingScore(
            winRate = 75.0,
            profitFactor = 2.5,
            sharpeRatio = 2.1,
            maxDrawdown = 4.0
        )

        // Low winrate, low profit factor, low Sharpe, high drawdown
        val scoreLow = StrategyEvaluationEngine.calculateRankingScore(
            winRate = 40.0,
            profitFactor = 0.8,
            sharpeRatio = -0.5,
            maxDrawdown = 22.0
        )

        println("High performance rank score: $scoreHigh, Low performance rank score: $scoreLow")
        assertTrue(scoreHigh > scoreLow)
    }

    @Test
    fun `test Confidence Calibration`() {
        println("--- TEST: Confidence Calibration ---")
        
        // Current confidence high (0.9), but actual win rate low (40.0) -> should decrease
        val calibratedDown = StrategyEvaluationEngine.calibrateConfidence(
            declaredConfidence = 0.9,
            actualWinRatePercent = 40.0,
            currentConfidenceScore = 0.9
        )
        println("Calibrated down: $calibratedDown")
        assertTrue(calibratedDown < 0.9)

        // Current confidence low (0.3), but actual win rate high (80.0) -> should increase
        val calibratedUp = StrategyEvaluationEngine.calibrateConfidence(
            declaredConfidence = 0.3,
            actualWinRatePercent = 80.0,
            currentConfidenceScore = 0.3
        )
        println("Calibrated up: $calibratedUp")
        assertTrue(calibratedUp > 0.3)
    }

    @Test
    fun `test Parameter Optimization`() = runBlocking {
        println("--- TEST: Parameter Optimization ---")
        
        val strategy = TrendFollowingStrategy()
        // Generate mock candles
        val candles = BacktestEngine.generateMockCandles("BTC", 50000.0, 30, "UPWARD_TREND")
        
        val optimizedParams = StrategyEvaluationEngine.optimizeStrategy(
            strategy = strategy,
            market = "BTC",
            candles = candles
        )
        
        println("Optimized parameters found: $optimizedParams")
        assertNotNull(optimizedParams)
        assertTrue(optimizedParams.containsKey("emaShortPeriod") || optimizedParams.containsKey("emaLongPeriod"))
    }

    @Test
    fun `test Automatic Strategy Switching and Persistent Storage`() = runBlocking {
        println("--- TEST: Automatic Strategy Switching & Persistence ---")
        
        // 1. Pre-populate strategies in DB with correct registry names
        val tfEntity = StrategyEntity(
            strategyName = "Trend Following",
            version = "1.0",
            description = "Trend following breakout strategy",
            supportedMarkets = "BTC,ETH",
            timeframe = "1h",
            requiredIndicators = "EMA,MACD",
            isActive = false,
            riskProfile = "Medium",
            totalPaperTrades = 10,
            paperWinRate = 60.0,
            paperNetProfit = 500.0,
            winRate = 55.0,
            profitFactor = 1.2,
            sharpeRatio = 1.0,
            maxDrawdown = 8.0,
            currentConfidenceScore = 0.5,
            ranking = 1,
            bestConfigParams = "",
            designedRegimes = "Trending"
        )
        val mrEntity = StrategyEntity(
            strategyName = "Mean Reversion",
            version = "1.0",
            description = "Mean reversion RSI bounce strategy",
            supportedMarkets = "BTC,ETH",
            timeframe = "1h",
            requiredIndicators = "RSI",
            isActive = true, // start active
            riskProfile = "Low",
            totalPaperTrades = 15,
            paperWinRate = 75.0,
            paperNetProfit = 1200.0,
            winRate = 70.0,
            profitFactor = 2.1,
            sharpeRatio = 1.9,
            maxDrawdown = 4.0,
            currentConfidenceScore = 0.85,
            ranking = 2,
            bestConfigParams = "",
            designedRegimes = "Ranging"
        )
        val boEntity = StrategyEntity(
            strategyName = "Breakout",
            version = "1.0",
            description = "Vol breakout strategy",
            supportedMarkets = "BTC,ETH",
            timeframe = "1h",
            requiredIndicators = "ATR",
            isActive = false,
            riskProfile = "High",
            totalPaperTrades = 5,
            paperWinRate = 45.0,
            paperNetProfit = 200.0,
            winRate = 50.0,
            profitFactor = 1.1,
            sharpeRatio = 0.8,
            maxDrawdown = 12.0,
            currentConfidenceScore = 0.45,
            ranking = 0,
            bestConfigParams = "",
            designedRegimes = "Trending"
        )
        
        repository.insertStrategy(tfEntity)
        repository.insertStrategy(mrEntity)
        repository.insertStrategy(boEntity)
        
        // 2. Mock prices that represent a strong TRENDING market regime
        val prices = List(30) { index -> 100.0 + (index * 4.0) } // Strong upward trend
        
        // Execute automatic selection and activation
        val selectedStrategy = StrategyEvaluationEngine.autoSelectAndActivateStrategy(
            repository = repository,
            market = "BTC",
            prices = prices,
            addLog = { println(" [Log] $it") }
        )
        
        assertNotNull(selectedStrategy)
        println("Auto-selected optimal strategy: ${selectedStrategy?.name}")
        
        // Since the market regime is "Trending", either Trend Following or Breakout
        // (which declare "Trending" in designedRegimes) should be preferred and activated.
        // Let's retrieve from database to verify active strategy got switched.
        val activeStrategyDb = repository.getActiveStrategyDirect()
        assertNotNull(activeStrategyDb)
        println("Database active strategy is now: ${activeStrategyDb?.strategyName}")
        
        // Assert that we switched away from Mean Reversion (designed for Ranging) 
        // to a strategy matching "Trending"
        assertNotEquals("Mean Reversion", activeStrategyDb?.strategyName)
        assertTrue(activeStrategyDb?.strategyName == "Trend Following" || activeStrategyDb?.strategyName == "Breakout")
        
        // 3. Test evaluateAndOptimizeAll pipeline
        println("Running full evaluateAndOptimizeAll orchestrator...")
        StrategyEvaluationEngine.evaluateAndOptimizeAll(
            repository = repository,
            market = "BTC",
            currentPrices = prices,
            addLog = { println(" [Log] $it") }
        )
        
        // Verify database records are updated
        val updatedStrategies = repository.strategies.first()
        for (strat in updatedStrategies) {
            println("Strat DB Row: ${strat.strategyName} | Rank: ${strat.ranking} | WinRate: ${strat.winRate}% | Conf: ${strat.currentConfidenceScore}")
            assertTrue(strat.totalTrades >= 0)
            assertTrue(strat.currentConfidenceScore in 0.0..1.0)
        }
        
        // Verify that some optimization records were written to SQLite
        val optHistory = repository.optimizationRecords.first()
        println("Total optimization records stored: ${optHistory.size}")
        assertTrue(optHistory.isNotEmpty())
        for (record in optHistory) {
            println(" -> Stored Opt Record: Strategy: ${record.strategyName}, Profit: $${record.inSampleProfit}, Config: ${record.paramConfig}")
        }
    }
}
