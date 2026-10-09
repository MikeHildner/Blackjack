package com.mikehildner.blackjack.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OddsTest {

    private val rules = Rules()
    private val fresh = Composition.fresh(6)
    private val odds = Odds(fresh, rules)

    private fun assertClose(expected: Double, actual: Double, tolerance: Double, message: String = "") =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "$message expected $expected but was $actual")

    @Test
    fun `dealer distribution sums to one`() {
        for (up in 2..11) {
            val d = odds.dealerDistribution(up)
            val sum = (17..21).sumOf { d[it] } + d.bust
            assertClose(1.0, sum, 1e-9, "up card $up")
        }
    }

    @Test
    fun `dealer bust probabilities match the published figures`() {
        // Standard multi-deck S17 figures: 6 is the biggest bust card, aces the smallest.
        assertClose(0.42, odds.dealerDistribution(6).bust, 0.015, "6")
        assertClose(0.35, odds.dealerDistribution(2).bust, 0.015, "2")
        assertClose(0.26, odds.dealerDistribution(7).bust, 0.015, "7")
        // 10 and Ace are conditioned on "dealer peeked and has no blackjack", which raises the bust
        // figures from the unconditional 21% / 12% to about 23% / 17%.
        assertClose(0.23, odds.dealerDistribution(10).bust, 0.02, "10")
        assertClose(0.17, odds.dealerDistribution(11).bust, 0.02, "A")
        assertClose(0.12, Odds(fresh, Rules(dealerPeeks = false)).dealerDistribution(11).bust, 0.02, "A, no peek")
        assertTrue(odds.dealerDistribution(5).bust > odds.dealerDistribution(9).bust)
    }

    @Test
    fun `H17 makes the dealer bust more often with an ace`() {
        val s17 = Odds(fresh, Rules(dealerHitsSoft17 = false)).dealerDistribution(11)
        val h17 = Odds(fresh, Rules(dealerHitsSoft17 = true)).dealerDistribution(11)
        assertTrue(h17.bust > s17.bust)
        assertTrue(h17[17] < s17[17], "H17 turns some 17s into other totals")
    }

    @Test
    fun `bust probability for hard 16 is about 62 percent`() {
        assertClose(32.0 / 52.0, odds.bustProbability(Hand.of(10, "10h", "6c")), 1e-9)
        assertEquals(0.0, odds.bustProbability(Hand.of(10, "Ah", "5c")), "soft hands cannot bust")
        assertEquals(0.0, odds.bustProbability(Hand.of(10, "5h", "6c")))
    }

    @Test
    fun `expected values rank the actions the way basic strategy does`() {
        val noPair = setOf(Action.HIT, Action.STAND, Action.DOUBLE, Action.SURRENDER)
        val all = Action.entries.toSet()
        val up6 = Card.of("6s")
        val up10 = Card.of("10s")

        assertEquals(Action.DOUBLE, odds.analyze(Hand.of(10, "6h", "5c"), up6, noPair).best.action, "11 vs 6 doubles")
        assertEquals(Action.SURRENDER, odds.analyze(Hand.of(10, "10h", "6c"), up10, noPair).best.action, "16 vs 10 surrenders")
        assertEquals(Action.STAND, odds.analyze(Hand.of(10, "10h", "6c"), up6, noPair).best.action, "16 vs 6 stands")
        assertEquals(Action.STAND, odds.analyze(Hand.of(10, "10h", "Kc"), up10, all).best.action, "20 stands, never split tens")
        assertEquals(Action.SPLIT, odds.analyze(Hand.of(10, "Ah", "Ac"), up6, all).best.action, "aces split")
        assertEquals(Action.SPLIT, odds.analyze(Hand.of(10, "8h", "8c"), up10, all).best.action, "eights split even against a 10")
        assertEquals(Action.HIT, odds.analyze(Hand.of(10, "10h", "2c"), Card.of("2s"), noPair).best.action, "12 vs 2 hits")
        assertEquals(Action.STAND, odds.analyze(Hand.of(10, "10h", "2c"), Card.of("4s"), noPair).best.action, "12 vs 4 stands")
        assertTrue(odds.analyze(Hand.of(10, "10h", "6c"), up10, all).values.none { it.action == Action.SPLIT }, "analyze ignores split for non-pairs")
    }

    @Test
    fun `16 versus 10 loses about 54 percent of a bet whether you hit or stand`() {
        val report = odds.analyze(Hand.of(10, "10h", "6c"), Card.of("10s"), setOf(Action.HIT, Action.STAND))
        assertClose(-0.54, report.ev(Action.STAND)!!, 0.03)
        assertClose(-0.54, report.ev(Action.HIT)!!, 0.03)
    }

    @Test
    fun `insurance is a losing bet in a fresh shoe`() {
        assertClose(3 * 16.0 / 52.0 - 1, odds.evInsurance(), 1e-9)
        assertTrue(odds.evInsurance() < 0)
    }

    @Test
    fun `insurance becomes profitable when the shoe is rich in tens`() {
        // Remove a lot of small cards: a count-heavy shoe.
        var comp = Composition.fresh(1)
        repeat(12) { comp = comp.without(2 + it % 5) }
        val rich = Odds(comp, rules)
        assertTrue(rich.evInsurance() > 0, "EV was ${rich.evInsurance()}")
    }

    @Test
    fun `composition add and remove are inverses`() {
        val c = Composition.fresh(1)
        assertEquals(16, c[10])
        assertEquals(15, c.without(10)[10])
        assertEquals(16, c.without(10).with(10)[10])
        assertEquals(52, c.total)
    }

    // ------------------------------------------------------- Free Bet Blackjack

    private val freeBetRules = Rules(variant = Variant.FREE_BET, dealerHitsSoft17 = true, lateSurrender = false, resplitAces = true)
    private val freeBet = Odds(fresh, freeBetRules)

    @Test
    fun `push 22 moves the dealer 22 out of the bust figure`() {
        val classic = Odds(fresh, Rules(dealerHitsSoft17 = true)).dealerDistribution(6)
        val fb = freeBet.dealerDistribution(6)
        assertClose(1.0, (17..26).sumOf { fb[it] }, 1e-9)
        assertClose(classic.bust, fb.bust + fb.p22, 1e-9, "same cards, 22 just reclassified")
        assertTrue(fb.p22 > 0.05, "a dealer 6 busts with exactly 22 fairly often")
        assertTrue(fb.evStand(20) < classic.evStand(20), "standing is worth less when 22 only pushes")
    }

    @Test
    fun `a free double beats a real double and hitting on 11`() {
        val hand = Hand.of(100, "6h", "5c")
        val dealer = freeBet.dealerDistribution(6)
        val free = freeBet.evDouble(hand, dealer, free = true)
        val real = freeBet.evDouble(hand, dealer, free = false)
        assertTrue(free > real, "free $free vs real $real")
        assertTrue(free > freeBet.evHit(hand, dealer))
        // Win pays 2 units, lose costs 1, push (including dealer 22) 0: the EV sits a little under +1.
        assertTrue(free > 0.6 && free < 1.4, "free double EV was $free")
    }

    @Test
    fun `a hand riding on a free bet only values wins`() {
        val freeHand = Hand(listOf(Card.of("Kh"), Card.of("Qc")), bet = 0, isFromSplit = true, freeBet = 100)
        val dealer = freeBet.dealerDistribution(6)
        assertClose(dealer.pPlayerWins(20), freeBet.evStand(freeHand, dealer), 1e-9, "a loss or push costs nothing, a win pays one unit")
        assertTrue(freeBet.evStand(freeHand, dealer) > 0)
    }

    @Test
    fun `classic numbers are unchanged by the payoff refactor`() {
        val report = odds.analyze(Hand.of(10, "10h", "6c"), Card.of("10s"), setOf(Action.HIT, Action.STAND, Action.SURRENDER))
        assertClose(-0.5, report.ev(Action.SURRENDER)!!, 1e-9)
        assertClose(odds.dealerDistribution(10).evStand(16), report.ev(Action.STAND)!!, 1e-9)
    }

    @Test
    fun `basic strategy chart agrees with brute-force EV on hard totals`() {
        // Every hard-total cell of the chart should match the EV-maximising action within a tiny margin.
        var disagreements = 0
        for (total in 5..17) {
            for (up in 2..11) {
                val hand = handWithHardTotal(total)
                val upCard = Card(Rank.entries.first { it.value == up }, Suit.SPADES)
                val available = setOf(Action.HIT, Action.STAND, Action.DOUBLE, Action.SURRENDER)
                val chart = BasicStrategy.recommend(hand, upCard, rules, available).action
                val report = odds.analyze(hand, upCard, available)
                val chartEv = report.ev(chart)!!
                if (report.best.ev - chartEv > 0.01) disagreements++
            }
        }
        assertEquals(0, disagreements)
    }

    private fun handWithHardTotal(total: Int): Hand {
        val first = if (total <= 11) 2 else 10
        val second = total - first
        val r1 = Rank.entries.first { it.value == first }
        val r2 = Rank.entries.first { it.value == second }
        return Hand(listOf(Card(r1, Suit.HEARTS), Card(r2, Suit.CLUBS)), 10)
    }
}
