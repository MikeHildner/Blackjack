package com.mikehildner.blackjack.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeakSpotsTest {

    private val rules = Rules()
    private val hard16v10 = CellKey(Variant.CLASSIC, BasicStrategy.Section.HARD, 16, 10)
    private val soft18v3 = CellKey(Variant.CLASSIC, BasicStrategy.Section.SOFT, 18, 3)
    private val hard6v5 = CellKey(Variant.CLASSIC, BasicStrategy.Section.HARD, 6, 5)

    @Test
    fun `records attempts, misses and accuracy`() {
        val w = WeakSpots()
        assertNull(w.accuracy(hard16v10))
        w.record(hard16v10, correct = true, nowMillis = 1)
        w.record(hard16v10, correct = false, nowMillis = 2)
        w.record(hard16v10, correct = true, nowMillis = 3)
        val r = w.recordOf(hard16v10)!!
        assertEquals(3, r.attempts)
        assertEquals(1, r.misses)
        assertEquals(3L, r.lastSeenMillis)
        assertEquals(2.0 / 3.0, w.accuracy(hard16v10)!!, 1e-9)
        assertEquals(3, w.totalAttempts)
    }

    @Test
    fun `recent misses decay`() {
        val w = WeakSpots()
        w.record(hard16v10, correct = false, nowMillis = 1)
        val afterMiss = w.recordOf(hard16v10)!!.recentMisses
        assertEquals(1.0, afterMiss, 1e-9)
        repeat(5) { w.record(hard16v10, correct = true, nowMillis = 2) }
        val afterCorrects = w.recordOf(hard16v10)!!.recentMisses
        assertTrue(afterCorrects < 0.4, "was $afterCorrects")
        assertEquals(1, w.recordOf(hard16v10)!!.misses, "the plain miss count never decays")
    }

    @Test
    fun `serialises and parses, ignoring junk`() {
        val w = WeakSpots()
        w.record(hard16v10, false, 10)
        w.record(soft18v3, true, 20)
        val fb = CellKey(Variant.FREE_BET, BasicStrategy.Section.HARD, 17, 9, freeBetHand = true)
        w.record(fb, true, 30)

        val text = w.serialize()
        val back = WeakSpots.parse(text + "\n\ngarbage line\nCLASSIC|HARD|x|10|0=1,2,3,4\n")
        assertEquals(w.all(), back.all())
        assertEquals(0, WeakSpots.parse("").totalAttempts)
    }

    @Test
    fun `weights rank missed above unseen above mastered, and autopilot rows low`() {
        val w = WeakSpots()
        w.record(hard16v10, false, 1)
        w.record(hard16v10, false, 2)
        val missed = w.weight(hard16v10, rules)
        val unseen = w.weight(soft18v3, rules)
        repeat(20) { w.record(soft18v3, true, 3) }
        val mastered = w.weight(soft18v3, rules)
        assertTrue(missed > unseen, "missed $missed vs unseen $unseen")
        assertTrue(unseen > mastered, "unseen $unseen vs mastered $mastered")

        val trivial = w.weight(hard6v5, rules) // unseen, but an autopilot row
        val twelveVsThree = w.weight(CellKey(Variant.CLASSIC, BasicStrategy.Section.HARD, 12, 3), rules) // unseen, famous close call
        assertTrue(twelveVsThree > trivial, "12 vs 3 $twelveVsThree should outweigh hard 6 vs 5 $trivial")
        assertTrue(w.difficulty(CellKey(Variant.CLASSIC, BasicStrategy.Section.HARD, 12, 3), rules) >= 2.0)
        assertTrue(w.difficulty(hard6v5, rules) < 1.0)
    }

    @Test
    fun `pool excludes free bet hands and unbuildable rows`() {
        val classic = WeakSpots().pool(rules)
        assertTrue(classic.none { it.freeBetHand })
        assertTrue(classic.none { it.section == BasicStrategy.Section.HARD && (it.row == 4 || it.row == 20) })
        assertEquals((13 + 8 + 10) * 10, classic.size)

        val freeBet = WeakSpots().pool(Rules(variant = Variant.FREE_BET))
        assertTrue(freeBet.all { it.variant == Variant.FREE_BET })
        assertTrue(freeBet.any { it.section == BasicStrategy.Section.HARD && it.row == 19 })
        assertFalse(freeBet.any { it.section == BasicStrategy.Section.HARD && it.row == 20 })
    }

    @Test
    fun `picks lean heavily toward recent misses`() {
        val w = WeakSpots()
        // Mark everything as seen and right once, so the unseen bonus does not dominate.
        w.pool(rules).forEach { w.record(it, true, 1) }
        repeat(3) { w.record(hard16v10, false, 2) }
        repeat(10) { w.record(hard6v5, true, 3) }

        val random = Random(11)
        var missedHits = 0
        var trivialHits = 0
        repeat(2_000) {
            when (w.pick(rules, random)) {
                hard16v10 -> missedHits++
                hard6v5 -> trivialHits++
            }
        }
        assertTrue(missedHits >= 5 * maxOf(trivialHits, 1), "missed $missedHits vs trivial $trivialHits")
        assertTrue(missedHits > 20, "the missed cell should come up often: $missedHits of 2000")
    }

    @Test
    fun `cell labels read naturally`() {
        assertEquals("Hard 16 vs 10", hard16v10.label())
        assertEquals("A,7 vs 3", soft18v3.label())
        assertEquals("8,8 vs A", CellKey(Variant.CLASSIC, BasicStrategy.Section.PAIRS, 8, 11).label())
        assertEquals("A,A vs 6 (free bet)", CellKey(Variant.FREE_BET, BasicStrategy.Section.PAIRS, 11, 6, true).label())
    }
}
