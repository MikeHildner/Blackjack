package com.mikehildner.blackjack.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandTest {

    @Test
    fun `hard total adds face values`() {
        val hand = Hand.of(10, "9h", "7c")
        assertEquals(16, hand.total)
        assertFalse(hand.isSoft)
        assertEquals("Hard 16", hand.describe())
    }

    @Test
    fun `ace counts as 11 when it fits`() {
        val hand = Hand.of(10, "Ah", "6c")
        assertEquals(17, hand.total)
        assertTrue(hand.isSoft)
        assertEquals("Soft 17", hand.describe())
    }

    @Test
    fun `soft hand becomes hard when the ace would bust`() {
        val hand = Hand.of(10, "Ah", "6c", "Kd")
        assertEquals(17, hand.total)
        assertFalse(hand.isSoft)
        assertFalse(hand.isBust)
    }

    @Test
    fun `two aces are soft 12`() {
        val hand = Hand.of(10, "Ah", "Ad")
        assertEquals(12, hand.total)
        assertTrue(hand.isSoft)
        assertTrue(hand.isPair)
    }

    @Test
    fun `ace and ten is blackjack`() {
        assertTrue(Hand.of(10, "Ah", "Kd").isBlackjack)
        assertTrue(Hand.of(10, "10s", "Ac").isBlackjack)
        assertFalse(Hand.of(10, "7s", "7c", "7d").isBlackjack, "three-card 21 is not a natural")
    }

    @Test
    fun `21 on a split hand is not blackjack`() {
        val hand = Hand(listOf(Card.of("Ah"), Card.of("Kd")), bet = 10, isFromSplit = true)
        assertEquals(21, hand.total)
        assertFalse(hand.isBlackjack)
    }

    @Test
    fun `any two ten-value cards are a pair`() {
        assertTrue(Hand.of(10, "Kh", "Qd").isPair)
        assertTrue(Hand.of(10, "10h", "Jd").isPair)
        assertFalse(Hand.of(10, "9h", "10d").isPair)
    }

    @Test
    fun `bust is over 21`() {
        val hand = Hand.of(10, "Kh", "Qd", "5c")
        assertTrue(hand.isBust)
        assertEquals("Bust (25)", hand.describe())
        assertTrue(hand.isResolved)
    }

    @Test
    fun `card parser handles ten and face cards`() {
        assertEquals(Card(Rank.TEN, Suit.SPADES), Card.of("10s"))
        assertEquals(Card(Rank.TEN, Suit.SPADES), Card.of("Ts"))
        assertEquals(Card(Rank.QUEEN, Suit.HEARTS), Card.of("Qh"))
        assertEquals("Q♥", Card.of("Qh").toString())
    }
}
