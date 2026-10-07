package com.mikehildner.blackjack.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CountingTest {

    @Test
    fun `hi-lo values`() {
        assertEquals(1, HiLo.value(Card.of("2h")))
        assertEquals(1, HiLo.value(Card.of("6h")))
        assertEquals(0, HiLo.value(Card.of("7h")))
        assertEquals(0, HiLo.value(Card.of("9h")))
        assertEquals(-1, HiLo.value(Card.of("10h")))
        assertEquals(-1, HiLo.value(Card.of("Kh")))
        assertEquals(-1, HiLo.value(Card.of("Ah")))
    }

    @Test
    fun `a full deck is balanced`() {
        assertEquals(0, Card.fullDeck.sumOf { HiLo.value(it) })
        val tracker = CountTracker()
        Card.fullDeck.forEach(tracker::see)
        assertEquals(0, tracker.runningCount)
        assertEquals(52, tracker.cardsSeen)
    }

    @Test
    fun `true count divides by decks remaining`() {
        val tracker = CountTracker()
        listOf("2h", "3h", "4h", "5h", "6h", "7h").map(Card::of).forEach(tracker::see)
        assertEquals(5, tracker.runningCount)
        assertEquals(2.5, tracker.trueCount(2.0))
        assertEquals(5.0, tracker.trueCount(1.0))
        assertEquals(10.0, tracker.trueCount(0.25), "decks remaining is floored at half a deck")
    }

    @Test
    fun `bet ramp grows with the true count`() {
        val tracker = CountTracker()
        assertEquals(1, tracker.suggestedUnits(4.0))
        repeat(8) { tracker.see(Card.of("2h")) } // running +8
        assertEquals(1, tracker.suggestedUnits(4.0), "TC +2 -> 1 unit")
        assertEquals(3, tracker.suggestedUnits(2.0), "TC +4 -> 3 units")
        assertEquals(7, tracker.suggestedUnits(1.0), "TC +8 -> 7 units")
        assertEquals(8, tracker.suggestedUnits(0.5), "capped at 8")
    }

    @Test
    fun `player edge improves half a percent per true count`() {
        val tracker = CountTracker()
        repeat(4) { tracker.see(Card.of("5h")) }
        assertEquals(-0.5 + 2.0, tracker.playerEdgePercent(1.0, 0.5))
        assertEquals(0.0, tracker.playerEdgePercent(4.0, 0.5), "true count +1 roughly cancels a 0.5% house edge")
        assertTrue(tracker.playerEdgePercent(8.0, 0.5) < 0)
    }

    @Test
    fun `format shows a sign`() {
        assertEquals("+2.5", CountTracker.format(2.5))
        assertEquals("-1.0", CountTracker.format(-1.04))
        assertEquals("0.0", CountTracker.format(0.0))
    }
}
