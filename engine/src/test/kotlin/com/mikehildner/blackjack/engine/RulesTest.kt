package com.mikehildner.blackjack.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {

    private val tulsa = Rules.HARD_ROCK_TULSA

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.05) =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected but was $actual")

    @Test
    fun `ante steps up above the threshold`() {
        assertEquals(50, tulsa.anteFor(500))
        assertEquals(50, tulsa.anteFor(5_000), "exactly at the threshold still pays the small ante")
        assertEquals(100, tulsa.anteFor(5_001))
        assertEquals(100, tulsa.anteFor(50_000))
        assertEquals(0, Rules().anteFor(500))
    }

    @Test
    fun `effective house edge shrinks as the bet grows`() {
        val base = tulsa.houseEdgePercent
        assertClose(base + 10.0, tulsa.effectiveHouseEdgePercent(500))   // $5 bet, 50c ante
        assertClose(base + 5.0, tulsa.effectiveHouseEdgePercent(1_000))  // $10
        assertClose(base + 1.0, tulsa.effectiveHouseEdgePercent(5_000))  // $50
        assertClose(base + 1.0, tulsa.effectiveHouseEdgePercent(10_000)) // $100, $1 ante
        assertEquals(Rules().houseEdgePercent, Rules().effectiveHouseEdgePercent(500), "no ante, no change")
        assertEquals(tulsa.houseEdgePercent, tulsa.effectiveHouseEdgePercent(0), "guard against division by zero")
    }

    @Test
    fun `base edge of the Tulsa rules is in the right ballpark`() {
        // 6D H17 DAS no surrender, 3:2: published figures are about 0.6%.
        assertClose(0.63, tulsa.houseEdgePercent, 0.05)
        assertClose(0.33, Rules().houseEdgePercent, 0.05)
    }

    @Test
    fun `summary shows the ante and shuffler markers`() {
        assertEquals("6D S17 DAS LS 3:2", Rules().summary())
        assertEquals("6D H17 DAS 3:2 +ante CSM", tulsa.summary())
        assertEquals("1D H17 NDAS 3:2", Rules.SINGLE_DECK.summary())
    }

    @Test
    fun `presets are recognised and edits fall back to custom`() {
        assertEquals(Preset.VEGAS_STRIP, Preset.matching(Rules()))
        assertEquals(Preset.HARD_ROCK_TULSA, Preset.matching(Rules.HARD_ROCK_TULSA))
        assertEquals(Preset.SINGLE_DECK, Preset.matching(Rules.SINGLE_DECK))
        assertNull(Preset.matching(Rules(decks = 2)))
        assertNull(Preset.matching(Rules.HARD_ROCK_TULSA.copy(ante = 0)))
        for (preset in Preset.entries) assertTrue(preset.description.isNotBlank())
    }

    @Test
    fun `default limits are in cents`() {
        assertEquals(500, Rules().minBet)
        assertEquals(50_000, Rules().maxBet)
    }
}
