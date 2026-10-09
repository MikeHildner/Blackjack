package com.mikehildner.blackjack.engine

import java.util.Locale

/**
 * Strategy for Free Bet Blackjack, computed rather than copied.
 *
 * There is no single published chart that covers every rule combination, and
 * the right play depends on whether the hand is riding on real money or on a
 * free bet (where a push is worth nothing). So every decision is derived by
 * asking [Odds] for the expected value of each legal action against a fresh
 * shoe and taking the best. Results are memoised, so the full chart costs a
 * few hundred milliseconds once and is free afterwards.
 */
object FreeBetStrategy {

    /** The verdict for one situation plus the numbers behind it. */
    data class Decision(val code: ChartCode, val values: List<ActionValue>) {
        fun ev(action: Action): Double? = values.firstOrNull { it.action == action }?.ev
    }

    private val cache = HashMap<Pair<Rules, Situation>, Decision>()
    private val oddsCache = HashMap<Rules, Odds>()

    /** A representative wager; only the ratio of real to free money matters. */
    private const val UNIT = 100

    fun decide(s: Situation, rules: Rules): Decision = synchronized(cache) {
        cache.getOrPut(rules to s) { compute(s, rules) }
    }

    fun recommend(hand: Hand, dealerUp: Card, rules: Rules, available: Set<Action>): Recommendation {
        val situation = Situation.of(hand, dealerUp)
        val decision = decide(situation, rules)
        val cell = decision.code
        val action = when {
            cell.primary in available -> cell.primary
            cell.fallback in available -> cell.fallback
            else -> if (Action.STAND in available && situation.total >= 17) Action.STAND else Action.HIT
        }
        return Recommendation(action, cell, explain(situation, decision, action, rules))
    }

    // ------------------------------------------------------------ compute

    private fun compute(s: Situation, rules: Rules): Decision {
        val hand = representativeHand(s)
        val up = Card(rankOf(s.dealerUp), Suit.SPADES)

        val available = mutableSetOf(Action.HIT, Action.STAND)
        val freeDouble = rules.isFreeDouble(hand)
        val freeSplit = rules.isFreeSplit(hand)
        val dasOk = !s.fromSplit || rules.doubleAfterSplit
        if (s.twoCards && dasOk && (freeDouble || rules.doubleRestriction.allows(hand))) available += Action.DOUBLE
        if (s.twoCards && s.pairOf != null) available += Action.SPLIT
        if (s.twoCards && !s.fromSplit && rules.lateSurrender) available += Action.SURRENDER

        val odds = synchronized(oddsCache) { oddsCache.getOrPut(rules) { Odds(Composition.fresh(rules.decks), rules) } }
        val report = odds.analyze(hand, up, available, freeDouble, freeSplit)
        val best = report.best
        val standBeatsHit = (report.ev(Action.STAND) ?: 0.0) >= (report.ev(Action.HIT) ?: 0.0)

        val code = when (best.action) {
            Action.HIT -> ChartCode.HIT
            Action.STAND -> ChartCode.STAND
            Action.DOUBLE -> if (best.free) ChartCode.FREE_DOUBLE else if (standBeatsHit) ChartCode.DOUBLE_STAND else ChartCode.DOUBLE_HIT
            Action.SPLIT -> if (best.free) ChartCode.FREE_SPLIT else ChartCode.SPLIT
            Action.SURRENDER -> if (standBeatsHit) ChartCode.SURRENDER_STAND else ChartCode.SURRENDER_HIT
        }
        return Decision(code, report.values)
    }

    /**
     * Build a hand with the right totals for the situation. Only hard total,
     * softness, pair-ness and card count affect the maths, so any cards that
     * produce them will do.
     */
    internal fun representativeHand(s: Situation): Hand {
        val bet = if (s.hasFreeBet) 0 else UNIT
        val free = if (s.hasFreeBet) UNIT else 0
        val values: List<Int> = when {
            s.pairOf != null && s.twoCards -> listOf(s.pairOf, s.pairOf)
            s.soft -> listOf(11) + hardCards(s.total - 11, if (s.twoCards) 1 else 2)
            else -> hardCards(s.total, if (s.twoCards) 2 else 3)
        }
        val cards = values.mapIndexed { i, v -> Card(rankOf(v), Suit.entries[i % 4]) }
        return Hand(cards, bet, isFromSplit = s.fromSplit, freeBet = free)
    }

    /**
     * Card values whose hard total is [total], using at least [minCards] cards.
     * A value of 11 is an ace, which [Hand] counts as 1 in the hard total.
     */
    private fun hardCards(total: Int, minCards: Int): List<Int> {
        if (minCards <= 1) return listOf(if (total <= 1) 11 else total.coerceAtMost(10))
        if (minCards == 2) {
            if (total <= 3) return if (total <= 2) listOf(11, 11) else listOf(11, 2)
            val a = (total - 2).coerceAtMost(10)
            return listOf(a, total - a)
        }
        // Three cards: peel off the biggest card that leaves at least 4 for the other two.
        val a = (total - 4).coerceIn(2, 10)
        val rem = total - a
        val b = (rem - 2).coerceIn(2, 10)
        return listOf(a, b, rem - b)
    }

    private fun rankOf(value: Int): Rank = when (value) {
        11 -> Rank.ACE
        10 -> Rank.TEN
        else -> Rank.entries.first { it.value == value }
    }

    // ------------------------------------------------------------ explain

    private fun fmt(v: Double?) = if (v == null) "?" else String.format(Locale.US, "%+.2f", v)

    private fun upName(up: Int) = if (up == 11) "an Ace" else "a $up"

    private fun explain(s: Situation, d: Decision, action: Action, rules: Rules): String {
        val cell = d.code
        val best = d.ev(action)
        val runnerUp = d.values.filter { it.action != action }.maxByOrNull { it.ev }

        if (cell == ChartCode.FREE_DOUBLE && action == Action.DOUBLE) {
            return "Free double. The house puts up the second bet for you: lose and you lose only your original wager, " +
                "win and you collect on both. There is no situation where refusing a free double is right (EV ${fmt(best)} vs ${fmt(runnerUp?.ev)} for ${runnerUp?.action?.label?.lowercase()})."
        }
        if (cell == ChartCode.FREE_SPLIT && action == Action.SPLIT) {
            return "Free split. The second hand costs you nothing: if it loses you lose nothing, if it wins it pays as though you had bet. " +
                "Take every free split (only tens are excluded, and you would never split those anyway)."
        }

        val classic = BasicStrategy.decide(s.copy(hasFreeBet = false), rules.copy(variant = Variant.CLASSIC), canSplit = s.pairOf != null)
        val classicAction = if (classic.primary == Action.SURRENDER && !rules.lateSurrender) classic.fallback else classic.primary
        val evNote = " (EV ${fmt(best)} vs ${fmt(runnerUp?.ev)} for ${runnerUp?.action?.label?.lowercase()})."

        if (s.hasFreeBet) {
            return "This hand is riding on a free bet, so a push wins nothing: it is as bad as a loss. That makes the right play more aggressive " +
                "than on a real-money hand. Here that means ${action.label.lowercase()}" + evNote
        }
        if (action != classicAction && action == Action.HIT && s.total in 12..16 && s.dealerUp in 2..6) {
            return "In classic blackjack you would stand on ${s.total} against ${upName(s.dealerUp)} and let the dealer bust. In Free Bet a dealer 22 is only a push, " +
                "so a dealer bust is worth less to you, and taking a card edges ahead" + evNote
        }
        if (action != classicAction) {
            return "Free Bet rules change this one: the classic play is ${classicAction.label.lowercase()}, but with push-22 and free bets in the mix, " +
                "${action.label.lowercase()} has the higher expected value" + evNote
        }
        // Same answer as classic blackjack: reuse its explanation.
        return BasicStrategy.explain(s, classic, action, rules) + " Push-22 does not change this one."
    }
}
