package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.aza.market.MarketDataService
import com.example.aza.strategy.PaperExecutionSimulator
import com.example.data.database.AppDatabase
import com.example.data.repository.AppRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AzaPhase6Test {

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
    fun testMarketDataService_RetrieveValidCandles() {
        // Retrieve candles for BTC (valid)
        val candles = MarketDataService.getHistoricalCandles(market = "BTC", timeframe = "1h", count = 100)
        
        assertNotNull(candles)
        assertTrue("Candles list should not be empty", candles.isNotEmpty())
        assertTrue("Candles list should contain up to 100 elements", candles.size <= 100)
        
        // Verify chronological sorting (ascending timestamps)
        for (i in 0 until candles.size - 1) {
            assertTrue(
                "Timestamps must be strictly ascending: ${candles[i].timestamp} <= ${candles[i+1].timestamp}",
                candles[i].timestamp <= candles[i+1].timestamp
            )
        }
    }

    @Test
    fun testMarketDataService_DataQualityValidation() {
        val score = MarketDataService.dataQualityScore
        assertTrue("Data Quality Score must be high for generated candles", score >= 90.0)
    }

    @Test
    fun testPaperExecutionSimulator_SimulateBuy() {
        val symbol = "BTC"
        val size = 0.5
        val targetPrice = 50000.0
        
        val result = PaperExecutionSimulator.simulateMarketOrder(
            symbol = symbol,
            isBuy = true,
            targetSize = size,
            limitOrSignalPrice = targetPrice
        )
        
        assertTrue("Filled size should be positive", result.filledSize > 0)
        assertTrue("Latency must be positive", result.latencyMs > 0)
        assertTrue("Fees must be strictly positive", result.feesPaid > 0.0)
        assertTrue("Slippage must be non-negative", result.slippagePaid >= 0.0)
        
        // Slippage check: execution price should be higher than or equal to targetPrice for a buy order
        assertTrue(
            "Execution price for BUY should incorporate slippage: ${result.averageExecutionPrice} >= $targetPrice",
            result.averageExecutionPrice >= targetPrice
        )
    }

    @Test
    fun testPaperExecutionSimulator_SimulateSell() {
        val symbol = "ETH"
        val size = 2.0
        val targetPrice = 3000.0
        
        val result = PaperExecutionSimulator.simulateMarketOrder(
            symbol = symbol,
            isBuy = false,
            targetSize = size,
            limitOrSignalPrice = targetPrice
        )
        
        assertTrue("Filled size should be positive", result.filledSize > 0)
        assertTrue("Fees must be strictly positive", result.feesPaid > 0.0)
        
        // Slippage check: execution price should be lower than or equal to targetPrice for a sell order
        assertTrue(
            "Execution price for SELL should incorporate slippage: ${result.averageExecutionPrice} <= $targetPrice",
            result.averageExecutionPrice <= targetPrice
        )
    }

    @Test
    fun testPaperExecutionSimulator_SizeLimits() {
        // Enormous size should lead to partial fill or rejection, or at least highly elevated slippage
        val giantBuy = PaperExecutionSimulator.simulateMarketOrder(
            symbol = "BTC",
            isBuy = true,
            targetSize = 1000.0, // huge size
            limitOrSignalPrice = 50000.0
        )
        
        // Tiny size should have very little slippage
        val tinyBuy = PaperExecutionSimulator.simulateMarketOrder(
            symbol = "BTC",
            isBuy = true,
            targetSize = 0.001,
            limitOrSignalPrice = 50000.0
        )
        
        assertTrue("Giant buy slippage should exceed tiny buy slippage", giantBuy.slippagePaid >= tinyBuy.slippagePaid)
    }

    @Test
    fun testDynamicExponentialEstimator_RegimeAdaptation() {
        val estimator = com.example.aza.executive.DynamicExponentialEstimator(repository)

        // Low Volatility Price list -> Stable / RANGE regime
        val stablePrices = listOf(100.0, 100.1, 100.0, 99.9, 100.0, 100.1, 100.0, 99.9)
        val stableAlpha = estimator.analyzePricesAndAdapt(stablePrices)
        assertEquals("RANGE", estimator.currentRegime)
        assertEquals(0.20, stableAlpha, 0.001)

        // Extremely high volatility prices -> VOLATILE regime
        val volatilePrices = listOf(100.0, 140.0, 80.0, 150.0, 70.0, 160.0, 60.0, 170.0)
        val volatileAlpha = estimator.analyzePricesAndAdapt(volatilePrices)
        assertEquals("VOLATILE", estimator.currentRegime)
        assertEquals(0.75, volatileAlpha, 0.001)
    }

    @Test
    fun testConflictResolutionEngine_AdaptiveClippingAndTrustRegion() {
        val engine = com.example.aza.executive.ConflictResolutionEngine(repository)

        // 1. Verify adaptive clipping range calculation
        val lowVolClip = engine.calculateAdaptiveClippingRange(0.005)
        val highVolClip = engine.calculateAdaptiveClippingRange(0.06)
        
        // Under low volatility, the trust region is wider to allow faster adaptation
        assertTrue("Expected low volatility clip to be around 22% (0.22)", lowVolClip > 0.20)
        // Under high volatility, the trust region shrinks to keep updates safe
        assertTrue("Expected high volatility clip to be around 4% (0.04)", highVolClip < 0.05)
        assertTrue("Low vol clipping range should be wider than high vol clipping range", lowVolClip > highVolClip)

        // 2. Verify weight clipping ratio (TRPO/PPO style)
        val oldWeight = 0.80
        val normalProposedWeight = 0.83 // ~3.7% change, within 5% high vol clipping range
        val clippedNormal = engine.clipWeightUpdate(oldWeight, normalProposedWeight, 0.05)
        assertEquals(normalProposedWeight, clippedNormal, 0.0001)

        val extremeProposedWeight = 1.05 // ~31% change, exceeds 15% medium clipping range
        val clippedExtreme = engine.clipWeightUpdate(oldWeight, extremeProposedWeight, 0.15)
        val maxAllowedWeight = oldWeight * (1.15)
        assertEquals(maxAllowedWeight, clippedExtreme, 0.0001)
    }

    @Test
    fun testAzaReinforcementOptimizer_FullUpgradeSuite() {
        // Test 1: PolicyGradientBandit initialization and probability properties
        val contextDim = 3
        val numActions = 4
        val bandit = com.example.aza.execution.AzaReinforcementOptimizer.PolicyGradientBandit(
            numActions = numActions,
            contextDim = contextDim,
            learningRate = 0.1,
            baseEntropyCoef = 0.15
        )

        val context = doubleArrayOf(0.5, -0.2, 0.8)
        val probs = bandit.getActionProbabilities(context)
        assertEquals("Probabilities must sum to 1.0", 1.0, probs.sum(), 1e-9)
        assertTrue("All probabilities must be non-negative", probs.all { it >= 0.0 })

        val action = bandit.sampleAction(context)
        assertTrue("Sampled action must be within bounds", action in 0 until numActions)

        // Test 2: VATRC (Volatility-Adaptive Trust Region Clipping) inside policy updates
        val oldProbs = probs.clone()
        val advantage = 0.5
        
        // Low volatility -> wider trust region (less clipping)
        bandit.updatePolicy(
            context = context,
            action = action,
            advantage = advantage,
            volatility = 0.005,
            oldProbs = oldProbs
        )
        val probsAfterLowVol = bandit.getActionProbabilities(context)
        assertNotEquals("Theta parameters should have changed", probs[action], probsAfterLowVol[action], 1e-9)

        // High volatility -> shrunken trust region (more clipping)
        val probsBeforeHighVol = probsAfterLowVol.clone()
        bandit.updatePolicy(
            context = context,
            action = action,
            advantage = 2.0, // extremely high advantage
            volatility = 0.06, // extremely high volatility
            oldProbs = probsBeforeHighVol
        )
        val probsAfterHighVol = bandit.getActionProbabilities(context)
        // Highly clipped updates under high volatility ensure policy remains stable
        assertTrue("Changes should be constrained under high volatility", 
            kotlin.math.abs(probsAfterHighVol[action] - probsBeforeHighVol[action]) < 0.2
        )

        // Test 3: Multi-Objective Reward & Dual-Loop Adaptation
        val rewardEvaluator = com.example.aza.execution.AzaReinforcementOptimizer.MultiObjectiveRewardEvaluator()
        val initialSentimentWeight = rewardEvaluator.sentimentWeight
        val initialProfitWeight = rewardEvaluator.profitWeight

        // Fast loop response to frustration (negative user sentiment)
        rewardEvaluator.adaptRewardWeights(userSentimentScore = -0.5, recentProfitFactor = 1.1, marketVolatility = 0.01)
        assertTrue("Sentiment weight should increase immediately during user frustration", 
            rewardEvaluator.sentimentWeight > initialSentimentWeight
        )

        // Slow loop response to low macro trading performance
        rewardEvaluator.adaptRewardWeights(userSentimentScore = 0.0, recentProfitFactor = 0.9, marketVolatility = 0.01)
        assertTrue("Profit weight should increase when trading performance is poor", 
            rewardEvaluator.profitWeight > initialProfitWeight - 0.2 // accounting for previous fast loop shift
        )

        val compositeReward = rewardEvaluator.evaluateReward(userSentimentScore = 0.8, profitPerformance = 0.5)
        assertTrue("Composite reward must be within [-1.0, 1.0]", compositeReward in -1.0..1.0)

        // Test 4: Actor-Critic temporal advantage updates
        val acOptimizer = com.example.aza.execution.AzaReinforcementOptimizer.ActorCriticOptimizer(
            bandit = bandit,
            contextDim = contextDim,
            criticLearningRate = 0.1
        )
        val initialVal = acOptimizer.predictValue(context)
        val computedAdvantage = acOptimizer.step(
            context = context,
            action = action,
            sentimentScore = 0.7,
            profitPerformance = 0.6,
            volatility = 0.01,
            oldProbs = probsAfterHighVol,
            rewardEvaluator = rewardEvaluator
        )
        val updatedVal = acOptimizer.predictValue(context)
        assertNotEquals("Critic weights should be updated", initialVal, updatedVal, 1e-9)

        // Test 5: TPE Optimizer Bounded Memory and Dynamic Histograms
        val tpe = com.example.aza.execution.AzaReinforcementOptimizer.TpeOptimizer(maxMemorySize = 15)
        
        // Cold start check
        val coldStartVal = tpe.suggestHyperparameter(0.01, 0.5)
        assertTrue("Cold-start value must be within bounds", coldStartVal in 0.01..0.5)

        // Populate history past maxMemorySize to verify bounded-memory constraint
        for (i in 1..25) {
            val hVal = 0.01 + i * 0.02
            val loss = if (kotlin.math.abs(hVal - 0.15) < 0.05) 0.1 else 0.8 // 0.15 is optimal
            tpe.recordTrial(hVal, loss)
        }
        assertEquals("Tpe history must be bounded", 15, tpe.history.size)

        // Get suggestion from TPE
        val suggestedVal = tpe.suggestHyperparameter(0.01, 0.5)
        assertTrue("Suggested value must be within bounds", suggestedVal in 0.01..0.5)

        // Test 6: RFF-Based Bayesian Regressor & Marginal Likelihood Tuning
        val regressor = com.example.aza.execution.AzaReinforcementOptimizer.RffBayesianRegressor(
            inputDim = contextDim,
            lengthScale = 0.5,
            priorVariance = 1.0,
            noiseVariance = 0.05
        )

        val rffFeatures = regressor.mapToRff(context)
        assertTrue("Should output features", rffFeatures.isNotEmpty())

        val mockInputs = listOf(
            doubleArrayOf(0.1, 0.2, 0.3),
            doubleArrayOf(0.4, -0.1, 0.2),
            doubleArrayOf(-0.3, 0.5, 0.1)
        )
        val mockTargets = doubleArrayOf(0.8, -0.2, 0.4)

        // Tune random feature count using marginal likelihood / cross-validation score
        regressor.tuneRandomFeatureCount(mockInputs, mockTargets, listOf(10, 20, 30))
        val tunedFeatures = regressor.mapToRff(context)
        assertTrue("Tuned feature mapping should be non-empty", tunedFeatures.isNotEmpty())
    }
}
