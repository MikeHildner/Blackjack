package com.mikehildner.blackjack.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FreeBetStrategyTest {

    private val rules = Rules(variant = Variant.FREE_BET, dealerHitsSoft17 = true, lateSurrender = false, resplitAces = true)

    private fun cell(section: BasicStrategy.Section, key: Int, up: Int, freeBetHand: Boolean = false) =
        BasicStrategy.chartCell(section, key, up, rules, freeBetHand)

    @Test
    fun `take every free double`() {
        for (total in 9..11) for (up in 2..11) {
            assertEquals(ChartCode.FREE_DOUBLE, cell(BasicStrategy.Section.HARD, total, up), "hard $total vs $up")
            assertEquals(ChartCode.FREE_DOUBLE, cell(BasicStrategy.Section.HARD, total, up, freeBetHand = true), "free-bet hard $total vs $up")
        }
    }

    @Test
    fun `take every free split except tens`() {
        for (pair in listOf(2, 3, 4, 6, 7, 8, 9, 11)) for (up in 2..11) {
            assertEquals(ChartCode.FREE_SPLIT, cell(BasicStrategy.Section.PAIRS, pair, up), "$pair,$pair vs $up")
        }
        for (up in 2..11) assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.PAIRS, 10, up), "never split tens")
        for (up in 2..11) {
            val fives = cell(BasicStrategy.Section.PAIRS, 5, up)
            assertTrue(fives == ChartCode.FREE_DOUBLE || fives == ChartCode.FREE_SPLIT, "5,5 vs $up was $fives")
        }
    }

    @Test
    fun `the obvious cells are still obvious`() {
        for (up in 2..11) {
            assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.HARD, 17, up))
            assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.SOFT, 20, up))
            assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 8, up))
        }
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 16, 10))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 12, 2))
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.HARD, 16, 6))
    }

    @Test
    fun `a hand riding on a free bet plays stiffs more aggressively`() {
        fun hits(freeBetHand: Boolean) = (12..16).sumOf { total ->
            (2..6).count { up -> cell(BasicStrategy.Section.HARD, total, up, freeBetHand) == ChartCode.HIT }
        }
        val real = hits(false)
        val free = hits(true)
        assertTrue(free > real, "free-bet hand hit $free stiff cells vs $real for a real-money hand")
    }

    @Test
    fun `push 22 makes standing on stiffs less attractive than in classic`() {
        val classic = rules.copy(variant = Variant.CLASSIC)
        var standsClassic = 0
        var standsFreeBet = 0
        for (total in 12..16) for (up in 2..6) {
            if (BasicStrategy.chartCell(BasicStrategy.Section.HARD, total, up, classic) == ChartCode.STAND) standsClassic++
            if (cell(BasicStrategy.Section.HARD, total, up) == ChartCode.STAND) standsFreeBet++
        }
        assertTrue(standsFreeBet <= standsClassic, "classic stands $standsClassic, free bet $standsFreeBet")
    }

    @Test
    fun `recommendations respect availability and explain themselves`() {
        val eleven = Hand.of(1_000, "6h", "5c")
        val up = Card.of("6s")
        val full = FreeBetStrategy.recommend(eleven, up, rules, setOf(Action.HIT, Action.STAND, Action.DOUBLE))
        assertEquals(Action.DOUBLE, full.action)
        assertEquals(ChartCode.FREE_DOUBLE, full.chart)
        assertTrue(full.reason.startsWith("Free double"))

        val noDouble = FreeBetStrategy.recommend(eleven, up, rules, setOf(Action.HIT, Action.STAND))
        assertEquals(Action.HIT, noDouble.action)

        val eights = Hand.of(1_000, "8h", "8c")
        val split = FreeBetStrategy.recommend(eights, Card.of("10s"), rules, Action.entries.toSet())
        assertEquals(Action.SPLIT, split.action)
        assertTrue(split.reason.startsWith("Free split"))

        val freeHand = Hand(listOf(Card.of("9h"), Card.of("4c")), bet = 0, isFromSplit = true, freeBet = 1_000)
        val rec = FreeBetStrategy.recommend(freeHand, Card.of("4s"), rules, setOf(Action.HIT, Action.STAND, Action.DOUBLE))
        assertTrue(rec.reason.contains("free bet"), rec.reason)

        val twenty = Hand.of(1_000, "Kh", "Qc")
        val stand = FreeBetStrategy.recommend(twenty, Card.of("6s"), rules, setOf(Action.HIT, Action.STAND))
        assertEquals(Action.STAND, stand.action)
        assertTrue(stand.reason.isNotBlank())
    }

    @Test
    fun `the whole chart computes quickly`() {
        val fresh = Rules(variant = Variant.FREE_BET, decks = 8) // not cached by earlier tests
        val start = System.nanoTime()
        for (free in listOf(false, true)) {
            for (section in BasicStrategy.Section.entries) {
                for (row in BasicStrategy.rows(section)) {
                    for (up in BasicStrategy.dealerColumns) BasicStrategy.chartCell(section, row, up, fresh, free)
                }
            }
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(ms < 2_000, "chart took $ms ms")
    }
}
