package com.example

import com.example.aza.strategy.EngineValidationSuite
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BreakoutGridHybridWalkForwardTest {

    @Test
    fun testHybridBreakoutGridWalkForwardMultiRegimeSimulation() {
        val result = EngineValidationSuite.runHybridBreakoutGridWalkForwardSimulation("BTCUSDT")

        assertNotNull(result)
        assertEquals("BTCUSDT", result["symbol"])
        assertEquals(400, result["totalCandlesTested"])

        @Suppress("UNCHECKED_CAST")
        val breakout = result["breakoutStandalone"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val grid = result["gridStandalone"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val hybrid = result["hybridSystem"] as Map<String, Any>

        println("=== WALK-FORWARD MULTI-REGIME SIMULATION RESULTS ===")
        println("1. Breakout Standalone: Net Profit = $${breakout["netProfitUsd"]}, Win Rate = ${breakout["winRatePct"]}%, WFE = ${breakout["wfePct"]}%, Rating = ${breakout["robustness"]}")
        println("2. Mean Reversion Grid Standalone: Net Profit = $${grid["netProfitUsd"]}, Win Rate = ${grid["winRatePct"]}%, WFE = ${grid["wfePct"]}%, Rating = ${grid["robustness"]}")
        println("3. Hybrid System (Breakout + Grid): Net Profit = $${hybrid["netProfitUsd"]}, Win Rate = ${hybrid["winRatePct"]}%, WFE = ${hybrid["wfePct"]}%, Rating = ${hybrid["robustness"]}")

        val hybridNetProfit = (hybrid["netProfitUsd"] as Number).toDouble()
        val hybridWfe = (hybrid["wfePct"] as Number).toDouble()
        val hybridMaxDd = (hybrid["maxDrawdownPct"] as Number).toDouble()

        assertTrue("Hybrid system should maintain positive net profit", hybridNetProfit > 0.0)
        assertTrue("Hybrid system should achieve solid WFE ratio", hybridWfe >= 50.0)
        assertTrue("Hybrid system should keep drawdown controlled", hybridMaxDd < 15.0)
    }
}
