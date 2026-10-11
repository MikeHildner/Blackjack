package com.mikehildner.blackjack.engine

import com.mikehildner.blackjack.engine.DealSequence.CARD_PAUSE
import com.mikehildner.blackjack.engine.DealSequence.DRAW_PAUSE
import com.mikehildner.blackjack.engine.DealSequence.FLIP_PAUSE
import com.mikehildner.blackjack.engine.DealSequence.RESULT_PAUSE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Transitions are produced by a real [Game] with a rigged shoe, so the
 * sequences are checked against exactly what the engine emits.
 */
class DealSequenceTest {

    private val BET = 1_000

    private fun game(vararg cards: String, rules: Rules = Rules()) = Game(rules, Shoe.rigged(*cards), 10_000)

    private fun cardsOf(s: RoundState) = s.hands.map { h -> h.cards.map { it.toString() } } to s.dealerCards.map { it.toString() }

    @Test
    fun `opening deal arrives player, dealer, player, dealer`() {
        val g = game("10h", "7c", "9d", "9s", "5c")
        val from = RoundState()
        g.placeBet(BET)
        val steps = DealSequence.steps(from, g.state)

        assertEquals(5, steps.size)
        assertEquals(listOf(listOf("10♥")) to emptyList<String>(), cardsOf(steps[0].state))
        assertEquals(listOf(listOf("10♥")) to listOf("7♣"), cardsOf(steps[1].state))
        assertEquals(listOf(listOf("10♥", "9♦")) to listOf("7♣"), cardsOf(steps[2].state))
        assertEquals(listOf(listOf("10♥", "9♦")) to listOf("7♣", "9♠"), cardsOf(steps[3].state))
        assertEquals(g.state, steps[4].state)

        steps.dropLast(1).forEach {
            assertTrue(it.state.holeCardHidden, "hole card stays down during the deal")
            assertEquals(-1, it.state.activeHand, "no hand highlighted while dealing")
            assertEquals(Phase.PLAYER_TURN, it.state.phase)
            assertEquals(CARD_PAUSE, it.pauseMillis)
        }
        assertEquals(0, steps.last().state.activeHand)
    }

    @Test
    fun `immediate blackjack flips the hole card before settling`() {
        val g = game("Ah", "5c", "Kd", "9s")
        g.placeBet(BET)
        val steps = DealSequence.steps(RoundState(), g.state)
        assertEquals(Phase.SETTLED, g.state.phase)
        assertEquals(6, steps.size)
        assertTrue(steps[3].state.holeCardHidden)
        assertTrue(!steps[4].state.holeCardHidden, "reveal step")
        assertTrue(steps[4].state.results.isEmpty(), "results only on the final step")
        assertEquals(maxOf(FLIP_PAUSE, RESULT_PAUSE), steps[4].pauseMillis)
        assertEquals(g.state, steps.last().state)
    }

    @Test
    fun `a hit is one card then the final state`() {
        val g = game("2h", "6c", "3d", "10s", "4c")
        g.placeBet(BET)
        val from = g.state
        g.act(Action.HIT)
        val steps = DealSequence.steps(from, g.state)
        assertEquals(2, steps.size)
        assertEquals(3, steps[0].state.hands[0].cards.size)
        assertEquals(CARD_PAUSE, steps[0].pauseMillis)
        assertEquals(g.state, steps[1].state)
    }

    @Test
    fun `standing plays the dealer out one draw at a time`() {
        // Player 10,9 vs dealer 2 (hole 4): dealer 6 draws 5 -> 11, draws K -> 21.
        val g = game("10h", "2c", "9d", "4s", "5c", "Kd")
        g.placeBet(BET)
        val from = g.state
        g.act(Action.STAND)
        val steps = DealSequence.steps(from, g.state)

        assertEquals(4, steps.size)
        assertTrue(!steps[0].state.holeCardHidden && steps[0].state.dealerCards.size == 2, "flip first")
        assertEquals(FLIP_PAUSE, steps[0].pauseMillis)
        assertEquals(3, steps[1].state.dealerCards.size)
        assertEquals(DRAW_PAUSE, steps[1].pauseMillis)
        assertEquals(4, steps[2].state.dealerCards.size)
        assertEquals(maxOf(DRAW_PAUSE, RESULT_PAUSE), steps[2].pauseMillis, "pause before results")
        steps.dropLast(1).forEach {
            assertEquals(Phase.PLAYER_TURN, it.state.phase)
            assertTrue(it.state.results.isEmpty())
        }
        assertEquals(Phase.SETTLED, steps.last().state.phase)
        assertEquals(g.state, steps.last().state)
    }

    @Test
    fun `busting shows the card, then the hole card, then the result`() {
        val g = game("10h", "7c", "6d", "9s", "Kc")
        g.placeBet(BET)
        val from = g.state
        g.act(Action.HIT)
        val steps = DealSequence.steps(from, g.state)
        assertEquals(3, steps.size)
        assertEquals(3, steps[0].state.hands[0].cards.size)
        assertTrue(steps[0].state.holeCardHidden)
        assertTrue(!steps[1].state.holeCardHidden)
        assertEquals(Phase.SETTLED, steps[2].state.phase)
    }

    @Test
    fun `a split shows both halves, then deals each in turn`() {
        val g = game("8h", "6c", "8d", "10s", "3c", "10d", "9h", "9c")
        g.placeBet(BET)
        val from = g.state
        g.act(Action.SPLIT)
        val steps = DealSequence.steps(from, g.state)

        assertEquals(4, steps.size)
        assertEquals(listOf(listOf("8♥"), listOf("8♦")), steps[0].state.hands.map { h -> h.cards.map { it.toString() } })
        assertEquals(listOf(listOf("8♥", "3♣"), listOf("8♦")), steps[1].state.hands.map { h -> h.cards.map { it.toString() } })
        assertEquals(listOf(listOf("8♥", "3♣"), listOf("8♦", "10♦")), steps[2].state.hands.map { h -> h.cards.map { it.toString() } })
        assertEquals(g.state, steps[3].state)
        steps.dropLast(1).forEach { assertTrue(it.state.holeCardHidden) }
    }

    @Test
    fun `second hand of a split draws then the dealer plays`() {
        val g = game("8h", "6c", "8d", "10s", "3c", "10d", "9h", "9c")
        g.placeBet(BET)
        g.act(Action.SPLIT)
        g.act(Action.DOUBLE) // hand one: 11 + 9 = 20
        val from = g.state
        g.act(Action.STAND) // hand two stands; dealer 16 draws 9 and busts
        val steps = DealSequence.steps(from, g.state)
        // flip, draw, final
        assertEquals(3, steps.size)
        assertTrue(!steps[0].state.holeCardHidden)
        assertEquals(3, steps[1].state.dealerCards.size)
        assertEquals(g.state, steps.last().state)
    }

    @Test
    fun `declining insurance with no change in cards is a single step`() {
        val g = game("5h", "Ah", "9d", "7c", "3s")
        g.placeBet(BET)
        val from = g.state
        assertEquals(Phase.INSURANCE, from.phase)
        g.insurance(false)
        val steps = DealSequence.steps(from, g.state)
        assertEquals(1, steps.size)
        assertEquals(g.state, steps[0].state)
    }

    @Test
    fun `back to betting and identical states are single steps`() {
        val g = game("10h", "7c", "9d", "9s", "5c")
        g.placeBet(BET)
        g.act(Action.STAND)
        val settled = g.state
        g.nextRound()
        assertEquals(1, DealSequence.steps(settled, g.state).size)
        assertEquals(1, DealSequence.steps(settled, settled).size)
    }

    @Test
    fun `every step shows something new`() {
        val rules = Rules()
        val g = Game(rules, Shoe(rules.decks, kotlin.random.Random(3)), 1_000_000)
        repeat(300) {
            var shown = g.state
            g.placeBet(BET)
            check(DealSequence.steps(shown, g.state)); shown = g.state
            if (g.state.phase == Phase.INSURANCE) { g.insurance(false); check(DealSequence.steps(shown, g.state)); shown = g.state }
            while (g.state.phase == Phase.PLAYER_TURN) {
                val rec = BasicStrategy.recommend(g.state.currentHand!!, g.state.dealerUpCard!!, rules, g.availableActions())
                g.act(rec.action)
                check(DealSequence.steps(shown, g.state)); shown = g.state
            }
            g.nextRound()
        }
    }

    private fun check(steps: List<DealSequence.Step>) {
        assertTrue(steps.isNotEmpty())
        for (i in 1 until steps.size - 1) {
            val a = steps[i - 1].state
            val b = steps[i].state
            assertTrue(a.hands.map { it.cards } != b.hands.map { it.cards } || a.dealerCards != b.dealerCards || a.holeCardHidden != b.holeCardHidden, "step $i repeats the previous picture")
        }
    }
}
