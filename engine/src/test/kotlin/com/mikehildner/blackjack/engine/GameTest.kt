package com.mikehildner.blackjack.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rounds are scripted with [Shoe.rigged]. Deal order is always:
 * player card 1, dealer up card, player card 2, dealer hole card, then any draws.
 */
class GameTest {

    private fun game(vararg cards: String, rules: Rules = Rules(), bankroll: Int = 100) =
        Game(rules, Shoe.rigged(*cards), bankroll)

    @Test
    fun `player blackjack pays 3 to 2`() {
        val g = game("Ah", "5c", "Kd", "9s")
        g.placeBet(10)
        assertEquals(Phase.SETTLED, g.state.phase)
        assertEquals(Outcome.BLACKJACK, g.state.results[0].outcome)
        assertEquals(15, g.state.results[0].net)
        assertEquals(115, g.bankroll)
    }

    @Test
    fun `player blackjack pays 6 to 5 at a bad table`() {
        val g = game("Ah", "5c", "Kd", "9s", rules = Rules(blackjackPayout = Payout.SIX_TO_FIVE))
        g.placeBet(10)
        assertEquals(12, g.state.results[0].net)
    }

    @Test
    fun `dealer ace offers insurance and dealer blackjack takes the bet`() {
        val g = game("5h", "Ah", "9d", "Kc")
        g.placeBet(10)
        assertEquals(Phase.INSURANCE, g.state.phase)
        g.insurance(take = false)
        assertEquals(Phase.SETTLED, g.state.phase)
        assertEquals(Outcome.DEALER_BLACKJACK, g.state.results[0].outcome)
        assertEquals(90, g.bankroll)
    }

    @Test
    fun `insurance pays 2 to 1 when the dealer has blackjack`() {
        val g = game("5h", "Ah", "9d", "Kc")
        g.placeBet(10)
        g.insurance(take = true)
        assertEquals(10, g.state.insuranceNet)
        assertEquals(-10, g.state.results[0].net)
        assertEquals(100, g.bankroll, "insurance exactly covers the lost main bet")
    }

    @Test
    fun `insurance is lost when the dealer has no blackjack`() {
        val g = game("5h", "Ah", "9d", "7c", "3s")
        g.placeBet(10)
        g.insurance(take = true)
        assertEquals(Phase.PLAYER_TURN, g.state.phase)
        g.act(Action.STAND) // player 14 vs dealer A,7 = soft 18 -> stands
        assertEquals(-5, g.state.insuranceNet)
        assertEquals(Outcome.LOSE, g.state.results[0].outcome)
        assertEquals(85, g.bankroll)
    }

    @Test
    fun `blackjack versus dealer blackjack is a push`() {
        val g = game("Ah", "Ac", "Kd", "Qc")
        g.placeBet(10)
        g.insurance(take = false)
        assertEquals(Outcome.PUSH, g.state.results[0].outcome)
        assertEquals(100, g.bankroll)
    }

    @Test
    fun `hitting past 21 busts immediately`() {
        val g = game("10h", "7c", "6d", "9s", "Kc")
        g.placeBet(10)
        assertEquals(Phase.PLAYER_TURN, g.state.phase)
        g.act(Action.HIT)
        assertEquals(Phase.SETTLED, g.state.phase)
        assertEquals(Outcome.BUST, g.state.results[0].outcome)
        assertEquals(90, g.bankroll)
        assertEquals(2, g.state.dealerCards.size, "dealer does not draw when every hand is bust")
    }

    @Test
    fun `dealer draws to 17 and beats a standing player`() {
        val g = game("10h", "7c", "9d", "9s", "5c")
        g.placeBet(10)
        g.act(Action.STAND)
        assertEquals(21, g.state.dealerHand.total)
        assertEquals(Outcome.LOSE, g.state.results[0].outcome)
    }

    @Test
    fun `dealer stands on soft 17 under S17`() {
        val g = game("10h", "Ac", "Kd", "6s", "4c", rules = Rules(dealerHitsSoft17 = false))
        g.placeBet(10)
        g.insurance(false)
        g.act(Action.STAND)
        assertEquals(17, g.state.dealerHand.total)
        assertEquals(Outcome.WIN, g.state.results[0].outcome)
    }

    @Test
    fun `dealer hits soft 17 under H17`() {
        val g = game("10h", "Ac", "Kd", "6s", "4c", rules = Rules(dealerHitsSoft17 = true))
        g.placeBet(10)
        g.insurance(false)
        g.act(Action.STAND)
        assertEquals(21, g.state.dealerHand.total)
        assertEquals(Outcome.LOSE, g.state.results[0].outcome)
    }

    @Test
    fun `double takes one card and doubles the bet`() {
        val g = game("6h", "6c", "5d", "9s", "Kc", "2d")
        g.placeBet(10)
        assertTrue(Action.DOUBLE in g.availableActions())
        g.act(Action.DOUBLE)
        val hand = g.state.hands[0]
        assertEquals(21, hand.total)
        assertEquals(20, hand.bet)
        assertTrue(hand.isDoubled)
        assertEquals(17, g.state.dealerHand.total)
        assertEquals(20, g.state.results[0].net)
        assertEquals(120, g.bankroll)
    }

    @Test
    fun `surrender returns half the bet`() {
        val g = game("10h", "Kc", "6d", "9s")
        g.placeBet(10)
        assertTrue(Action.SURRENDER in g.availableActions())
        g.act(Action.SURRENDER)
        assertEquals(Outcome.SURRENDER, g.state.results[0].outcome)
        assertEquals(95, g.bankroll)
    }

    @Test
    fun `surrender is not offered when the rules forbid it`() {
        val g = game("10h", "Kc", "6d", "9s", rules = Rules(lateSurrender = false))
        g.placeBet(10)
        assertFalse(Action.SURRENDER in g.availableActions())
    }

    @Test
    fun `split creates two hands played in order`() {
        // Player 8,8 vs dealer 6 (hole 10). Split draws 3 then 10. Double first hand draws 9. Dealer draws 9 and busts.
        val g = game("8h", "6c", "8d", "10s", "3c", "10d", "9h", "9c")
        g.placeBet(10)
        assertTrue(Action.SPLIT in g.availableActions())
        g.act(Action.SPLIT)
        assertEquals(2, g.state.hands.size)
        assertEquals(0, g.state.activeHand)
        assertEquals(11, g.state.hands[0].total)
        assertEquals(18, g.state.hands[1].total)
        assertFalse(Action.SURRENDER in g.availableActions(), "no surrender on split hands")

        g.act(Action.DOUBLE)
        assertEquals(1, g.state.activeHand)
        g.act(Action.STAND)

        assertEquals(Phase.SETTLED, g.state.phase)
        assertTrue(g.state.dealerHand.isBust)
        assertEquals(listOf(20, 10), g.state.results.map { it.net })
        assertEquals(130, g.bankroll)
    }

    @Test
    fun `split aces get one card each and 21 is not a blackjack`() {
        val g = game("Ah", "9c", "Ad", "8s", "Kc", "Qd")
        g.placeBet(10)
        g.act(Action.SPLIT)
        assertEquals(Phase.SETTLED, g.state.phase, "split aces are resolved automatically")
        assertEquals(listOf(Outcome.WIN, Outcome.WIN), g.state.results.map { it.outcome })
        assertEquals(120, g.bankroll)
    }

    @Test
    fun `double is only available on the first two cards`() {
        val g = game("2h", "6c", "3d", "10s", "4c", "5d")
        g.placeBet(10)
        g.act(Action.HIT) // 9 with three cards
        assertFalse(Action.DOUBLE in g.availableActions())
        assertEquals(setOf(Action.HIT, Action.STAND), g.availableActions())
    }

    @Test
    fun `double restriction blocks soft and low totals`() {
        val g = game("Ah", "6c", "5d", "10s", rules = Rules(doubleRestriction = DoubleRestriction.TEN_TO_ELEVEN))
        g.placeBet(10)
        assertFalse(Action.DOUBLE in g.availableActions(), "soft 16 cannot be doubled at a 10-11 table")
    }

    @Test
    fun `cannot bet more than the bankroll or outside table limits`() {
        val g = game("2h", "6c", "3d", "10s", bankroll = 20)
        assertFalse(g.canBet(25))
        assertFalse(g.canBet(4))
        assertTrue(g.canBet(20))
    }

    @Test
    fun `card listener sees every card except the hole card until it is revealed`() {
        val g = game("10h", "7c", "9d", "9s", "5c")
        val seen = mutableListOf<Card>()
        g.addCardListener { seen += it }
        g.placeBet(10)
        assertEquals(listOf("10♥", "7♣", "9♦"), seen.map { it.toString() })
        g.act(Action.STAND)
        assertEquals(listOf("10♥", "7♣", "9♦", "9♠", "5♣"), seen.map { it.toString() })
    }

    @Test
    fun `shoe reshuffles after the cut card`() {
        val shoe = Shoe.rigged(listOf("10h", "7c", "9d", "9s", "5c", "2d", "2c", "2h").map(Card::of), penetration = 0.5)
        val g = Game(Rules(), shoe, 100)
        g.placeBet(10)
        g.act(Action.STAND)
        assertTrue(shoe.cutCardReached)
        assertTrue(g.nextRound())
        assertEquals(52, shoe.remaining)
    }

    @Test
    fun `a real shoe plays thousands of rounds without error`() {
        val rules = Rules()
        val g = Game(rules, Shoe(rules.decks, kotlin.random.Random(42)), 1_000_000)
        repeat(5_000) {
            g.placeBet(10)
            if (g.state.phase == Phase.INSURANCE) g.insurance(false)
            while (g.state.phase == Phase.PLAYER_TURN) {
                val hand = g.state.currentHand!!
                val rec = BasicStrategy.recommend(hand, g.state.dealerUpCard!!, rules, g.availableActions())
                g.act(rec.action)
            }
            g.nextRound()
        }
        // Basic strategy loses slowly: 5000 rounds at $10 should land well inside +/- $4000 of break-even.
        assertTrue(g.bankroll in 996_000..1_004_000, "bankroll was ${g.bankroll}")
    }
}
