package com.mikehildner.blackjack.engine

import kotlin.math.max

/**
 * Probability that the dealer finishes on each total 17..26. Totals above 21
 * are busts, except that in Free Bet Blackjack a 22 is a push.
 */
class DealerDistribution(private val p: DoubleArray, val pushOn22: Boolean) {
    operator fun get(total: Int): Double = p[total]

    /** Probability the dealer busts with exactly 22. */
    val p22: Double get() = p[22]

    /** Probability the dealer busts in a way that pays the player. */
    val bust: Double get() = (if (pushOn22) 0.0 else p[22]) + p[23] + p[24] + p[25] + p[26]

    /** Probability the dealer ends below this standing total, or busts. */
    fun pPlayerWins(playerTotal: Int): Double {
        var p = bust
        for (t in 17..21) if (t < playerTotal) p += this[t]
        return p
    }

    /** Probability the dealer ends above this standing total (without busting). */
    fun pPlayerLoses(playerTotal: Int): Double {
        var p = 0.0
        for (t in 17..21) if (t > playerTotal) p += this[t]
        return p
    }

    /** Expected value of standing with [playerTotal] (<= 21), per unit bet, real money only. */
    fun evStand(playerTotal: Int): Double = pPlayerWins(playerTotal) - pPlayerLoses(playerTotal)

    companion object {
        const val MAX_TOTAL = 26
    }
}

/** Expected value of one action, in units of the hand's current wager. */
data class ActionValue(val action: Action, val ev: Double, val approximate: Boolean = false, val free: Boolean = false)

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
 * Every hand has a payoff profile: [Hand.bet] is real money (lost on a loss,
 * paid on a win) and [Hand.freeBet] is house money (paid on a win, nothing
 * otherwise). All EVs are reported per unit of the hand's current wager so the
 * actions stay comparable. For a classic hand the formulas collapse to the
 * familiar P(win) - P(lose).
 *
 * Everything here is pure arithmetic over about 30 hand states, so it runs
 * in well under a millisecond and can be recomputed on every card.
 */
class Odds(private val shoe: Composition, private val rules: Rules) {

    private val dealerCache = HashMap<Int, DealerDistribution>()
    private val dealerMemo = HashMap<Int, DoubleArray>()

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
            val result = DoubleArray(DealerDistribution.MAX_TOTAL + 1)
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
                for (i in 17..DealerDistribution.MAX_TOTAL) result[i] += p * sub[i]
            }
            DealerDistribution(result, rules.pushOn22)
        }
    }

    /** Distribution of final totals for a dealer hand with this hard total and ace flag, drawing freely. */
    private fun dealerFrom(hard: Int, hasAce: Boolean): DoubleArray {
        val total = bestTotal(hard, hasAce)
        val soft = hasAce && hard + 10 <= 21
        val result = DoubleArray(DealerDistribution.MAX_TOTAL + 1)
        if (total > 21) { result[total] = 1.0; return result }
        val mustHit = total < 17 || (total == 17 && soft && rules.dealerHitsSoft17)
        if (!mustHit) { result[total] = 1.0; return result }

        return dealerMemo.getOrPut(hard * 2 + (if (hasAce) 1 else 0)) {
            val acc = DoubleArray(DealerDistribution.MAX_TOTAL + 1)
            for (v in Composition.VALUES) {
                val p = shoe.probability(v)
                if (p == 0.0) continue
                val sub = dealerFrom(hard + hardValue(v), hasAce || v == 11)
                for (i in 17..DealerDistribution.MAX_TOTAL) acc[i] += p * sub[i]
            }
            acc
        }
    }

    // ------------------------------------------------------------- payoffs

    /** Money won or lost (absolute) by standing on [total] with this profile. */
    private fun standValue(total: Int, bet: Double, free: Double, dealer: DealerDistribution): Double =
        if (total > 21) -bet else dealer.pPlayerWins(total) * (bet + free) - dealer.pPlayerLoses(total) * bet

    /** Best of standing or hitting from this state, absolute money, hit-or-stand only from here on. */
    private fun bestFrom(hard: Int, hasAce: Boolean, bet: Double, free: Double, dealer: DealerDistribution, memo: HashMap<Int, Double>): Double {
        val total = bestTotal(hard, hasAce)
        if (total > 21) return -bet
        if (total == 21) return standValue(21, bet, free, dealer)
        return max(standValue(total, bet, free, dealer), hitFrom(hard, hasAce, bet, free, dealer, memo))
    }

    private fun hitFrom(hard: Int, hasAce: Boolean, bet: Double, free: Double, dealer: DealerDistribution, memo: HashMap<Int, Double>): Double =
        memo.getOrPut(hard * 2 + (if (hasAce) 1 else 0)) {
            var ev = 0.0
            for (v in Composition.VALUES) {
                val p = shoe.probability(v)
                if (p == 0.0) continue
                ev += p * bestFrom(hard + hardValue(v), hasAce || v == 11, bet, free, dealer, memo)
            }
            ev
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
        standValue(hand.total, hand.bet.toDouble(), hand.freeBet.toDouble(), dealer) / unit(hand)

    /** EV of hitting once and then playing on optimally (hit or stand only). */
    fun evHit(hand: Hand, dealer: DealerDistribution): Double =
        hitFrom(hand.hardTotal, hand.cards.any { it.isAce }, hand.bet.toDouble(), hand.freeBet.toDouble(), dealer, HashMap()) / unit(hand)

    /**
     * EV of doubling: exactly one more card. A real double risks another wager;
     * a free double (Free Bet Blackjack) adds a wager the house pays for.
     */
    fun evDouble(hand: Hand, dealer: DealerDistribution, free: Boolean = false): Double {
        val extra = hand.wager.toDouble()
        val bet = hand.bet + if (free) 0.0 else extra
        val freeBet = hand.freeBet + if (free) extra else 0.0
        var ev = 0.0
        for (v in Composition.VALUES) {
            val p = shoe.probability(v)
            if (p == 0.0) continue
            val total = bestTotal(hand.hardTotal + hardValue(v), hand.cards.any { it.isAce } || v == 11)
            ev += p * standValue(total, bet, freeBet, dealer)
        }
        return ev / unit(hand)
    }

    /**
     * Approximate EV of splitting: each new hand gets one card, is then played
     * optimally without further splitting. Ignores resplits, so it is a touch
     * pessimistic for small pairs. Hand one keeps the current profile; hand two
     * is funded by the player (real split) or the house (free split).
     */
    fun evSplit(hand: Hand, dealer: DealerDistribution, free: Boolean = false): Double {
        val cardValue = hand.cards[0].value
        val extra = hand.wager.toDouble()
        val one = splitHandValue(cardValue, hand.bet.toDouble(), hand.freeBet.toDouble(), dealer)
        val two = if (free) splitHandValue(cardValue, 0.0, extra, dealer) else splitHandValue(cardValue, extra, 0.0, dealer)
        return (one + two) / unit(hand)
    }

    /** Absolute value of one split hand starting with [cardValue] and this profile. */
    private fun splitHandValue(cardValue: Int, bet: Double, free: Double, dealer: DealerDistribution): Double {
        val aces = cardValue == 11
        val hard = if (aces) 1 else cardValue
        var value = 0.0
        for (v in Composition.VALUES) {
            val p = shoe.probability(v)
            if (p == 0.0) continue
            val nh = hard + hardValue(v)
            val na = aces || v == 11
            val total = bestTotal(nh, na)
            value += p * if (aces && !rules.hitSplitAces) {
                standValue(total, bet, free, dealer)
            } else {
                val two = Hand(listOf(Card(valueToRank(cardValue), Suit.SPADES), Card(valueToRank(v), Suit.HEARTS)), bet.toInt(), isFromSplit = true, freeBet = free.toInt())
                var best = bestFrom(nh, na, bet, free, dealer, HashMap())
                if (rules.doubleAfterSplit && rules.doubleRestriction.allows(two)) {
                    val freeDouble = rules.isFreeDouble(two)
                    val extra = bet + free
                    val dBet = bet + if (freeDouble) 0.0 else extra
                    val dFree = free + if (freeDouble) extra else 0.0
                    var dbl = 0.0
                    for (w in Composition.VALUES) {
                        val q = shoe.probability(w)
                        if (q == 0.0) continue
                        dbl += q * standValue(bestTotal(nh + hardValue(w), na || w == 11), dBet, dFree, dealer)
                    }
                    best = max(best, dbl)
                }
                best
            }
        }
        return value
    }

    /** Insurance pays 2:1 if the hole card is a ten. EV per unit of insurance bet. */
    fun evInsurance(): Double = 3 * shoe.probability(10) - 1

    /**
     * Full report for the current decision. [freeDouble] and [freeSplit] say
     * whether those actions would be house-funded (see [Rules.isFreeDouble]).
     */
    fun analyze(
        hand: Hand,
        dealerUp: Card,
        available: Set<Action>,
        freeDouble: Boolean = rules.isFreeDouble(hand),
        freeSplit: Boolean = rules.isFreeSplit(hand),
    ): OddsReport {
        val dealer = dealerDistribution(dealerUp.value)
        val values = buildList {
            add(ActionValue(Action.STAND, evStand(hand, dealer)))
            add(ActionValue(Action.HIT, evHit(hand, dealer)))
            if (Action.DOUBLE in available) add(ActionValue(Action.DOUBLE, evDouble(hand, dealer, freeDouble), free = freeDouble))
            if (Action.SPLIT in available && hand.isPair) add(ActionValue(Action.SPLIT, evSplit(hand, dealer, freeSplit), approximate = true, free = freeSplit))
            if (Action.SURRENDER in available) add(ActionValue(Action.SURRENDER, -0.5 * hand.bet / unit(hand)))
        }
        return OddsReport(bustProbability(hand), dealer, values)
    }

    // ------------------------------------------------------------ helpers

    /** The normaliser: EVs are per unit of what the hand is currently playing for. */
    private fun unit(hand: Hand): Double = max(hand.wager, 1).toDouble()

    private fun hardValue(v: Int) = if (v == 11) 1 else v

    private fun bestTotal(hard: Int, hasAce: Boolean) = if (hasAce && hard + 10 <= 21) hard + 10 else hard

    private fun valueToRank(v: Int): Rank = when (v) {
        11 -> Rank.ACE
        10 -> Rank.TEN
        else -> Rank.entries.first { it.value == v }
    }
}
