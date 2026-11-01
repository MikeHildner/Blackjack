package com.mikehildner.blackjack.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeDealerTest {

    @Test
    fun `every pool cell can be dealt and lands on its own cell`() {
        for (rules in listOf(Rules(), Rules(variant = Variant.FREE_BET, dealerHitsSoft17 = true, lateSurrender = false, resplitAces = true))) {
            val random = Random(5)
            for (key in WeakSpots().pool(rules)) {
                val g = Game(rules, PracticeDealer.shoeFor(key, rules, random), 1_000_000)
                g.placeBet(1_000)
                assertTrue(g.state.phase == Phase.PLAYER_TURN || g.state.phase == Phase.INSURANCE, "${key.label()} ended in ${g.state.phase}")
                assertFalse(g.state.hands[0].isBlackjack, "${key.label()} dealt a natural")
                assertFalse(g.state.dealerHand.isBlackjack, "${key.label()} dealt a dealer natural")
                if (g.state.phase == Phase.INSURANCE) g.insurance(false)
                assertEquals(Phase.PLAYER_TURN, g.state.phase, key.label())
                val dealt = BasicStrategy.cellFor(Situation.of(g.state.hands[0], g.state.dealerUpCard!!), rules.variant)
                assertEquals(key, dealt)
            }
        }
    }

    @Test
    fun `hard rows are never dealt as pairs`() {
        val key = CellKey(Variant.CLASSIC, BasicStrategy.Section.HARD, 12, 4)
        repeat(20) { seed ->
            val g = Game(Rules(), PracticeDealer.shoeFor(key, Rules(), Random(seed)), 1_000_000)
            g.placeBet(1_000)
            assertFalse(g.state.hands[0].isPair)
            assertEquals(12, g.state.hands[0].total)
        }
    }

    @Test
    fun `the rest of the shoe is a real shuffled shoe`() {
        val rules = Rules()
        val shoe = PracticeDealer.shoeFor(CellKey(Variant.CLASSIC, BasicStrategy.Section.SOFT, 18, 3), rules, Random(1))
        assertEquals(rules.decks * 52, shoe.remaining)
        assertEquals(rules.decks * 4, shoe.composition()[11], "all aces accounted for before dealing")
    }
}
