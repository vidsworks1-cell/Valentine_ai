package com.example

import com.example.aza.strategy.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LuminaMtfInstitutionalRefinementTest {

    @Test
    fun testThreeOfThreeAgreement() {
        val states = listOf(
            TimeframeState("4H", "BULLISH", 0.85),
            TimeframeState("1H", "BULLISH", 0.85),
            TimeframeState("15m", "BULLISH", 0.80)
        )
        val diag = MtfEvaluator.evaluateMtfDiagnostics("BUY", states, 66.0)

        assertEquals(100.0, diag.mtfRawScore, 0.1)
        assertEquals(100.0, diag.mtfWeightedScore, 0.1)
        assertEquals(MtfAlignmentStatus.STRONG_ALIGNMENT, diag.mtfStatus)
        assertEquals("ALIGNED", diag.htfDirectionalAlignment)
        assertFalse(diag.isHtfConflict)
    }

    @Test
    fun testTwoOfThreeAgreement() {
        val states = listOf(
            TimeframeState("4H", "BULLISH", 0.80),
            TimeframeState("1H", "BULLISH", 0.80),
            TimeframeState("15m", "NEUTRAL", 0.60)
        )
        val diag = MtfEvaluator.evaluateMtfDiagnostics("BUY", states, 66.0)

        assertEquals(66.66, diag.mtfRawScore, 1.0)
        assertTrue("Weighted score should reflect high HTF confidence", diag.mtfWeightedScore >= 80.0)
        assertTrue(diag.mtfStatus == MtfAlignmentStatus.STRONG_ALIGNMENT || diag.mtfStatus == MtfAlignmentStatus.ACCEPTABLE_ALIGNMENT)
    }

    @Test
    fun testOneOfThreeAgreement() {
        val states = listOf(
            TimeframeState("4H", "BEARISH", 0.85),
            TimeframeState("1H", "BEARISH", 0.75),
            TimeframeState("15m", "BULLISH", 0.70)
        )
        val diag = MtfEvaluator.evaluateMtfDiagnostics("BUY", states, 66.0)

        assertEquals(33.33, diag.mtfRawScore, 1.0)
        assertTrue("Weighted score should be low", diag.mtfWeightedScore < 40.0)
        assertEquals(MtfAlignmentStatus.CONFLICT, diag.mtfStatus)
        assertTrue(diag.isHtfConflict)
    }

    @Test
    fun testWeightedFailDespiteRawPass() {
        // Raw agreement is 2 out of 3 (66.7%), but HTF (4H) with 0.50 weight opposes the signal (BEARISH vs BUY).
        val states = listOf(
            TimeframeState("4H", "BEARISH", 0.85),
            TimeframeState("1H", "BULLISH", 0.80),
            TimeframeState("15m", "BULLISH", 0.80)
        )
        val diag = MtfEvaluator.evaluateMtfDiagnostics("BUY", states, 66.0)

        assertEquals("Raw score is 2/3 = 66.7%", 66.66, diag.mtfRawScore, 1.0)
        assertTrue("Weighted score must fail threshold because HTF disagrees", diag.mtfWeightedScore < 66.0)
        assertEquals(MtfAlignmentStatus.CONFLICT, diag.mtfStatus)
        assertTrue(diag.isHtfConflict)
        assertEquals("OPPOSING", diag.htfDirectionalAlignment)
    }

    @Test
    fun testWeightedPassDespiteRawWeakness() {
        // HTF (4H weight 0.50) and MTF (1H weight 0.30) agree with BUY signal, while LTF (15m weight 0.20) opposes.
        val states = listOf(
            TimeframeState("4H", "BULLISH", 0.90),
            TimeframeState("1H", "BULLISH", 0.85),
            TimeframeState("15m", "BEARISH", 0.60)
        )
        val diag = MtfEvaluator.evaluateMtfDiagnostics("BUY", states, 66.0)

        assertEquals(66.66, diag.mtfRawScore, 1.0)
        assertTrue("Weighted score must pass because heavy HTF+MTF agree", diag.mtfWeightedScore >= 75.0)
        assertEquals(MtfAlignmentStatus.STRONG_ALIGNMENT, diag.mtfStatus)
        assertEquals("ALIGNED", diag.htfDirectionalAlignment)
    }

    @Test
    fun testMissingTimeframe() {
        val states = listOf(
            TimeframeState("4H", "BULLISH", 0.80, isMissing = true),
            TimeframeState("1H", "BULLISH", 0.80),
            TimeframeState("15m", "BULLISH", 0.80)
        )
        val diag = MtfEvaluator.evaluateMtfDiagnostics("BUY", states, 66.0)

        assertEquals(MtfAlignmentStatus.INSUFFICIENT_DATA, diag.mtfStatus)
        assertTrue(diag.rationale.contains("INSUFFICIENT DATA"))
    }

    @Test
    fun testStaleTimeframe() {
        val states = listOf(
            TimeframeState("4H", "BULLISH", 0.80, isStale = true),
            TimeframeState("1H", "BULLISH", 0.80),
            TimeframeState("15m", "BULLISH", 0.80)
        )
        val diag = MtfEvaluator.evaluateMtfDiagnostics("BUY", states, 66.0)

        assertEquals(MtfAlignmentStatus.INSUFFICIENT_DATA, diag.mtfStatus)
        assertTrue(diag.rationale.contains("stale"))
    }

    @Test
    fun testHigherTimeframeConflict() {
        val states = listOf(
            TimeframeState("1D", "BEARISH", 0.90),
            TimeframeState("4H", "BEARISH", 0.85),
            TimeframeState("1H", "BULLISH", 0.80),
            TimeframeState("15m", "BULLISH", 0.85)
        )
        val diag = MtfEvaluator.evaluateMtfDiagnostics("BUY", states, 66.0)

        assertTrue("Must flag HTF conflict when 1D/4H oppose signal", diag.isHtfConflict)
        assertEquals("OPPOSING", diag.htfDirectionalAlignment)
        assertEquals(MtfAlignmentStatus.CONFLICT, diag.mtfStatus)
        assertTrue("Conflict level must be elevated", diag.mtfConflictLevel >= 75.0)
        assertTrue("Rationale must mention MACRO CONFLICT", diag.rationale.contains("MACRO CONFLICT"))
    }
}
