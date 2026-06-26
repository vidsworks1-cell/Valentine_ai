package com.example

import com.example.aza.market.MarketDataService
import com.example.aza.strategy.PaperExecutionSimulator
import org.junit.Assert.*
import org.junit.Test

class AzaPhase6Test {

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
}
