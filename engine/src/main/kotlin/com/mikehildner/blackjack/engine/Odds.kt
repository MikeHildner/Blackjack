package com.mikehildner.blackjack.engine

import kotlin.math.max

/**
 * Probability that the dealer finishes on each total. Index 17..21 are the
 * standing totals, [BUST] is the bust probability.
 */
class DealerDistribution(private val p: DoubleArray) {
    operator fun get(total: Int): Double = p[total]
    val bust: Double get() = p[BUST]

    /** Expected value of standing with [playerTotal] (already assumed <= 21), per unit bet. */
    fun evStand(playerTotal: Int): Double {
        var ev = bust
        for (t in 17..21) {
            if (t < playerTotal) ev += p[t] else if (t > playerTotal) ev -= p[t]
        }
        return ev
    }

    companion object {
        const val BUST = 22
    }
}

/** Expected value of one action, in units of the original bet. */
data class ActionValue(val action: Action, val ev: Double, val approximate: Boolean = false)

/** Everything the odds panel shows for the current decision. */
data class OddsReport(
    /** Probability the next card busts the player if they hit. */
    val bustIfHit: Double,
    val dealer: DealerDistribution,
    val values: List<ActionValue>,
) {
    val best: ActionValue get() = values.maxBy { it.ev }
    fun ev(action: Action): Double? = values.firstOrNull { it.action == action }?.ev
}

/**
 * Exact-ish expected values computed from the remaining shoe composition.
 *
 * "Exact-ish" because of one standard simplification: as we imagine drawing
 * cards, the composition is not depleted. For a six-deck shoe the error is a
 * tiny fraction of a percent; for a single deck it is noticeable but still
 * close. This is the same approach most strategy calculators use.
 *
 * Everything here is pure arithmetic over about 30 hand states, so it runs
 * in well under a millisecond and can be recomputed on every card.
 */
class Odds(private val shoe: Composition, private val rules: Rules) {

    private val dealerCache = HashMap<Int, DealerDistribution>()

    // ------------------------------------------------------------- dealer

    /**
     * Dealer final-total distribution given the up card.
     *
     * When the dealer has peeked and not turned over a blackjack, we know the
     * hole card is not the one card that would have completed it, and the
     * probabilities are conditioned on that.
     */
    fun dealerDistribution(upValue: Int, peekedNoBlackjack: Boolean = rules.dealerPeeks): DealerDistribution {
        val key = upValue * 2 + (if (peekedNoBlackjack) 1 else 0)
        return dealerCache.getOrPut(key) {
            val result = DoubleArray(23)
            val excluded = when {
                !peekedNoBlackjack -> -1
                upValue == 11 -> 10
                upValue == 10 -> 11
                else -> -1
            }
            val hard = if (upValue == 11) 1 else upValue
            val hasAce = upValue == 11
            // First draw (the hole card) is conditioned on "not a blackjack".
            val denominator = if (excluded == -1) 1.0 else 1.0 - shoe.probability(excluded)
            for (v in Composition.VALUES) {
                if (v == excluded) continue
                val p = shoe.probability(v) / denominator
                if (p == 0.0) continue
                val sub = dealerFrom(hard + hardValue(v), hasAce || v == 11)
                for (i in 17..22) result[i] += p * sub[i]
            }
            DealerDistribution(result)
        }
    }

    private val dealerMemo = HashMap<Int, DoubleArray>()

    /** Distribution of final totals for a dealer hand with this hard total and ace flag, drawing freely. */
    private fun dealerFrom(hard: Int, hasAce: Boolean): DoubleArray {
        val total = bestTotal(hard, hasAce)
        val soft = hasAce && hard + 10 <= 21
        val result = DoubleArray(23)
        if (total > 21) { result[DealerDistribution.BUST] = 1.0; return result }
        val mustHit = total < 17 || (total == 17 && soft && rules.dealerHitsSoft17)
        if (!mustHit) { result[total] = 1.0; return result }

        return dealerMemo.getOrPut(hard * 2 + (if (hasAce) 1 else 0)) {
            val acc = DoubleArray(23)
            for (v in Composition.VALUES) {
                val p = shoe.probability(v)
                if (p == 0.0) continue
                val sub = dealerFrom(hard + hardValue(v), hasAce || v == 11)
                for (i in 17..22) acc[i] += p * sub[i]
            }
            acc
        }
    }

    // ------------------------------------------------------------- player

    /** Probability that one more card busts this hand. */
    fun bustProbability(hand: Hand): Double {
        if (hand.isSoft) return 0.0
        var p = 0.0
        for (v in Composition.VALUES) {
            if (hand.hardTotal + hardValue(v) > 21) p += shoe.probability(v)
        }
        return p
    }

    fun evStand(hand: Hand, dealer: DealerDistribution): Double =
        if (hand.isBust) -1.0 else dealer.evStand(hand.total)

    /** EV of hitting once and then playing on optimally (hit or stand only). */
    fun evHit(hand: Hand, dealer: DealerDistribution): Double {
        val memo = HashMap<Int, Double>()
        return hitFrom(hand.hardTotal, hand.cards.any { it.isAce }, dealer, memo)
    }

    private fun hitFrom(hard: Int, hasAce: Boolean, dealer: DealerDistribution, memo: HashMap<Int, Double>): Double =
        memo.getOrPut(hard * 2 + (if (hasAce) 1 else 0)) {
            var ev = 0.0
            for (v in Composition.VALUES) {
                val p = shoe.probability(v)
                if (p == 0.0) continue
                val nh = hard + hardValue(v)
                val na = hasAce || v == 11
                val total = bestTotal(nh, na)
                ev += p * when {
                    total > 21 -> -1.0
                    total == 21 -> dealer.evStand(21)
                    else -> max(dealer.evStand(total), hitFrom(nh, na, dealer, memo))
                }
            }
            ev
        }

    /** EV of doubling: exactly one more card, bet doubled. */
    fun evDouble(hand: Hand, dealer: DealerDistribution): Double {
        var ev = 0.0
        for (v in Composition.VALUES) {
            val p = shoe.probability(v)
            if (p == 0.0) continue
            val total = bestTotal(hand.hardTotal + hardValue(v), hand.cards.any { it.isAce } || v == 11)
            ev += p * if (total > 21) -2.0 else 2.0 * dealer.evStand(total)
        }
        return ev
    }

    /**
     * Approximate EV of splitting: each new hand gets one card, is then played
     * optimally without further splitting. Ignores resplits, so it is a touch
     * pessimistic for small pairs.
     */
    fun evSplit(hand: Hand, dealer: DealerDistribution): Double {
        val cardValue = hand.cards[0].value
        val aces = cardValue == 11
        val hard = if (aces) 1 else cardValue
        var perHand = 0.0
        for (v in Composition.VALUES) {
            val p = shoe.probability(v)
            if (p == 0.0) continue
            val nh = hard + hardValue(v)
            val na = aces || v == 11
            val total = bestTotal(nh, na)
            perHand += p * if (aces && !rules.hitSplitAces) {
                dealer.evStand(total)
            } else {
                val two = Hand(listOf(hand.cards[0], Card(valueToRank(v), Suit.SPADES)), hand.bet, isFromSplit = true)
                var best = max(dealer.evStand(total), hitFrom(nh, na, dealer, HashMap()))
                if (rules.doubleAfterSplit && rules.doubleRestriction.allows(two)) best = max(best, evDouble(two, dealer))
                best
            }
        }
        return 2 * perHand
    }

    /** Insurance pays 2:1 if the hole card is a ten. EV per unit of insurance bet. */
    fun evInsurance(): Double = 3 * shoe.probability(10) - 1

    /** Full report for the current decision. */
    fun analyze(hand: Hand, dealerUp: Card, available: Set<Action>): OddsReport {
        val dealer = dealerDistribution(dealerUp.value)
        val values = buildList {
            add(ActionValue(Action.STAND, evStand(hand, dealer)))
            add(ActionValue(Action.HIT, evHit(hand, dealer)))
            if (Action.DOUBLE in available) add(ActionValue(Action.DOUBLE, evDouble(hand, dealer)))
            if (Action.SPLIT in available && hand.isPair) add(ActionValue(Action.SPLIT, evSplit(hand, dealer), approximate = true))
            if (Action.SURRENDER in available) add(ActionValue(Action.SURRENDER, -0.5))
        }
        return OddsReport(bustProbability(hand), dealer, values.filter { it.action in available || it.action == Action.STAND || it.action == Action.HIT })
    }

    // ------------------------------------------------------------ helpers

    private fun hardValue(v: Int) = if (v == 11) 1 else v

    private fun bestTotal(hard: Int, hasAce: Boolean) = if (hasAce && hard + 10 <= 21) hard + 10 else hard

    private fun valueToRank(v: Int): Rank = when (v) {
        11 -> Rank.ACE
        10 -> Rank.TEN
        else -> Rank.entries.first { it.value == v }
    }
}
