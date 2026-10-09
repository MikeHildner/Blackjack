package com.mikehildner.blackjack.engine

/**
 * One cell of a strategy chart: what to do, and what to do instead when the
 * first choice is not allowed (e.g. doubling with three cards, or surrender
 * at a table that does not offer it).
 */
enum class ChartCode(val code: String, val primary: Action, val fallback: Action) {
    HIT("H", Action.HIT, Action.HIT),
    STAND("S", Action.STAND, Action.STAND),
    /** Double if allowed, otherwise hit. */
    DOUBLE_HIT("D", Action.DOUBLE, Action.HIT),
    /** Double if allowed, otherwise stand. */
    DOUBLE_STAND("Ds", Action.DOUBLE, Action.STAND),
    SPLIT("P", Action.SPLIT, Action.HIT),
    /** Surrender if allowed, otherwise hit. */
    SURRENDER_HIT("R", Action.SURRENDER, Action.HIT),
    /** Surrender if allowed, otherwise stand. */
    SURRENDER_STAND("Rs", Action.SURRENDER, Action.STAND),
    /** Surrender if allowed, otherwise split. */
    SURRENDER_SPLIT("Rp", Action.SURRENDER, Action.SPLIT),
    /** Free Bet Blackjack: double with house money (hard 9, 10, 11). */
    FREE_DOUBLE("FD", Action.DOUBLE, Action.HIT),
    /** Free Bet Blackjack: split with house money (any pair but tens). */
    FREE_SPLIT("FP", Action.SPLIT, Action.HIT);

    val isFree: Boolean get() = this == FREE_DOUBLE || this == FREE_SPLIT
}

/** The strategy verdict for a concrete situation, with a plain-English explanation. */
data class Recommendation(
    val action: Action,
    /** The chart cell this came from, before availability fallbacks were applied. */
    val chart: ChartCode,
    val reason: String,
)

/**
 * Everything needed to look up a strategy decision, without reference to
 * actual [Card] objects so the same code can also draw the charts.
 */
data class Situation(
    val total: Int,
    val soft: Boolean,
    /** Card value of the pair (2..11) when the hand is exactly two equal cards, else null. */
    val pairOf: Int?,
    /** Dealer up card value 2..11 (11 = ace). */
    val dealerUp: Int,
    /** True on the first decision for a hand (only then may you double, split or surrender). */
    val twoCards: Boolean,
    val fromSplit: Boolean = false,
    /** Free Bet Blackjack: the hand is riding on house money only, so a push wins nothing. */
    val hasFreeBet: Boolean = false,
) {
    companion object {
        fun of(hand: Hand, dealerUp: Card) = Situation(
            total = hand.total,
            soft = hand.isSoft,
            pairOf = if (hand.isPair) hand.cards[0].value else null,
            dealerUp = dealerUp.value,
            twoCards = hand.cards.size == 2,
            fromSplit = hand.isFromSplit,
            hasFreeBet = hand.bet == 0 && hand.freeBet > 0,
        )
    }
}

/**
 * Basic strategy: the mathematically best decision for every hand against
 * every dealer up card, assuming you know nothing about the remaining cards.
 *
 * The tables below follow the standard multi-deck charts (Wizard of Odds /
 * Schlesinger) with the known adjustments for H17, DAS, surrender and
 * doubling restrictions. [Odds] can confirm any cell by brute force.
 */
object BasicStrategy {

    /**
     * Resolve the chart against what is actually permitted right now.
     * Free Bet Blackjack has no fixed chart; its decisions are computed by [FreeBetStrategy].
     */
    fun recommend(hand: Hand, dealerUp: Card, rules: Rules, available: Set<Action>): Recommendation {
        if (rules.variant == Variant.FREE_BET) return FreeBetStrategy.recommend(hand, dealerUp, rules, available)
        return classicRecommend(hand, dealerUp, rules, available)
    }

    internal fun classicRecommend(hand: Hand, dealerUp: Card, rules: Rules, available: Set<Action>): Recommendation {
        val situation = Situation.of(hand, dealerUp)
        val cell = decide(situation, rules, canSplit = Action.SPLIT in available)
        val action = when {
            cell.primary in available -> cell.primary
            cell.fallback in available -> cell.fallback
            // Fallback itself unavailable (e.g. 8-8 vs A with no surrender and no split): play the total.
            else -> decide(situation.copy(pairOf = null), rules, canSplit = false).let {
                if (it.primary in available) it.primary else it.fallback
            }
        }
        return Recommendation(action, cell, explain(situation, cell, action, rules))
    }

    /** The chart cell for a situation. [canSplit] lets callers hide split when it is not legal. */
    fun decide(s: Situation, rules: Rules, canSplit: Boolean = true): ChartCode {
        if (s.pairOf != null && s.twoCards && canSplit) {
            pairs(s.pairOf, s.dealerUp, rules)?.let { return it }
        }
        return if (s.soft) soft(s, rules) else hard(s, rules)
    }

    // --------------------------------------------------------------- pairs

    /** Returns null when the pair should be played as a plain total (5-5, 10-10, and DAS-dependent cases). */
    private fun pairs(pair: Int, up: Int, rules: Rules): ChartCode? {
        val das = rules.doubleAfterSplit
        return when (pair) {
            11 -> ChartCode.SPLIT
            10 -> null
            9 -> if (up in 2..6 || up in 8..9) ChartCode.SPLIT else null
            8 -> if (up == 11 && rules.dealerHitsSoft17 && rules.lateSurrender) ChartCode.SURRENDER_SPLIT else ChartCode.SPLIT
            7 -> if (up in 2..7) ChartCode.SPLIT else null
            6 -> if (up in 3..6 || (up == 2 && das)) ChartCode.SPLIT else null
            5 -> null
            4 -> if (das && up in 5..6) ChartCode.SPLIT else null
            3, 2 -> if (up in 4..7 || (up in 2..3 && das)) ChartCode.SPLIT else null
            else -> null
        }
    }

    // ---------------------------------------------------------------- soft

    private fun soft(s: Situation, rules: Rules): ChartCode {
        val up = s.dealerUp
        val h17 = rules.dealerHitsSoft17
        val canDouble = s.twoCards && (!s.fromSplit || rules.doubleAfterSplit)
        fun dbl(orElse: ChartCode) = if (canDouble) (if (orElse == ChartCode.STAND) ChartCode.DOUBLE_STAND else ChartCode.DOUBLE_HIT) else orElse

        return when (s.total) {
            in 13..14 -> if (up in 5..6) dbl(ChartCode.HIT) else ChartCode.HIT
            in 15..16 -> if (up in 4..6) dbl(ChartCode.HIT) else ChartCode.HIT
            17 -> if (up in 3..6) dbl(ChartCode.HIT) else ChartCode.HIT
            18 -> when {
                up in 3..6 -> dbl(ChartCode.STAND)
                up == 2 -> if (h17) dbl(ChartCode.STAND) else ChartCode.STAND
                up in 7..8 -> ChartCode.STAND
                else -> ChartCode.HIT // 9, 10, A
            }
            19 -> if (h17 && up == 6) dbl(ChartCode.STAND) else ChartCode.STAND
            else -> ChartCode.STAND // soft 20, 21
        }
    }

    // ---------------------------------------------------------------- hard

    private fun hard(s: Situation, rules: Rules): ChartCode {
        val up = s.dealerUp
        val h17 = rules.dealerHitsSoft17
        val canDouble = s.twoCards && (!s.fromSplit || rules.doubleAfterSplit) && allowsTotal(rules, s.total)
        val canSurrender = s.twoCards && !s.fromSplit && rules.lateSurrender
        fun dbl() = if (canDouble) ChartCode.DOUBLE_HIT else ChartCode.HIT
        fun sur(orElse: ChartCode) = if (canSurrender) (if (orElse == ChartCode.STAND) ChartCode.SURRENDER_STAND else ChartCode.SURRENDER_HIT) else orElse

        return when {
            s.total >= 18 -> ChartCode.STAND
            s.total == 17 -> if (h17 && up == 11) sur(ChartCode.STAND) else ChartCode.STAND
            s.total == 16 -> when {
                up in 2..6 -> ChartCode.STAND
                up in 9..11 -> sur(ChartCode.HIT)
                else -> ChartCode.HIT
            }
            s.total == 15 -> when {
                up in 2..6 -> ChartCode.STAND
                up == 10 -> sur(ChartCode.HIT)
                up == 11 && h17 -> sur(ChartCode.HIT)
                else -> ChartCode.HIT
            }
            s.total in 13..14 -> if (up in 2..6) ChartCode.STAND else ChartCode.HIT
            s.total == 12 -> if (up in 4..6) ChartCode.STAND else ChartCode.HIT
            s.total == 11 -> if (up <= 10 || h17) dbl() else ChartCode.HIT
            s.total == 10 -> if (up <= 9) dbl() else ChartCode.HIT
            s.total == 9 -> if (up in 3..6) dbl() else ChartCode.HIT
            else -> ChartCode.HIT // 8 or less
        }
    }

    private fun allowsTotal(rules: Rules, total: Int): Boolean = when (rules.doubleRestriction) {
        DoubleRestriction.ANY_TWO -> true
        DoubleRestriction.NINE_TO_ELEVEN -> total in 9..11
        DoubleRestriction.TEN_TO_ELEVEN -> total in 10..11
    }

    // ------------------------------------------------------------ explain

    private fun upName(up: Int) = if (up == 11) "an Ace" else "a $up"

    internal fun explain(s: Situation, cell: ChartCode, action: Action, rules: Rules): String {
        val up = s.dealerUp
        val weak = up in 2..6
        val core = when {
            // Pairs
            s.pairOf != null && cell == ChartCode.SPLIT -> when (s.pairOf) {
                11 -> "Always split Aces. Two hands that each start with 11 are far stronger than one soft 12, and each has a good shot at 21."
                8 -> "Always split 8s. Hard 16 is the worst total in the game; two hands starting at 8 are each in reasonable shape."
                9 -> "Split 9s against ${upName(up)}. Two hands starting at 9 beat standing on 18 here, except against a 7 (18 beats the likely 17), or a 10/Ace (too strong to put more money out)."
                7 -> "Split 7s against ${upName(up)}. Hard 14 is a poor total, and against a weak-to-middling dealer card each 7 can become a decent hand."
                6 -> "Split 6s against ${upName(up)}. Hard 12 is awkward, and the dealer is likely to bust, so two hands are worth more than one."
                4 -> "Split 4s only against a 5 or 6 when doubling after a split is allowed: the dealer is most likely to bust, and you may get to double on 9, 10 or 11."
                else -> "Split ${s.pairOf}s against ${upName(up)}. The dealer is weak enough that two separate hands each earn more than one hard ${s.total}."
            }
            s.pairOf == 10 && s.twoCards -> "Never split 10s. A 20 wins about 85% of the time; breaking it up throws away a near-certain win."
            s.pairOf == 5 && s.twoCards -> "Never split 5s. Play the hand as hard 10, which is a great doubling hand."
            s.pairOf == 9 && up == 7 -> "Stand with 9-9 against a 7. Your 18 beats the dealer's most likely total of 17, so leave it alone."
            s.pairOf != null && s.twoCards && (up >= 7) && s.total < 17 -> "Against a strong dealer card, splitting ${s.pairOf}s would just put more money at risk. Play it as a hard ${s.total} instead."
            cell == ChartCode.SURRENDER_SPLIT -> "8-8 against an Ace when the dealer hits soft 17 is the one pair you give up: half the bet back beats splitting."

            // Surrender
            action == Action.SURRENDER || cell.primary == Action.SURRENDER ->
                "Hard ${s.total} against ${upName(up)} is one of the worst spots in blackjack: you lose roughly three times in four whatever you do. Giving up half the bet (-0.50) beats the expected loss of playing on (about -0.54 or worse)."

            // Soft hands
            s.soft && cell.primary == Action.DOUBLE ->
                "Soft ${s.total} cannot bust with one more card, and the dealer shows ${upName(up)}, a card that busts often. That is the moment to double: more money on the table while the odds favour you."
            s.soft && s.total == 18 && action == Action.HIT ->
                "Soft 18 feels like a good hand, but against ${upName(up)} an 18 loses more often than it wins. You cannot bust, so take a card and try to improve."
            s.soft && s.total == 18 -> "Soft 18 against ${upName(up)}: 18 beats a dealer 17 and there is no cheap way to improve, so stand."
            s.soft && s.total >= 19 -> "Soft ${s.total} is already a strong hand. Stand."
            s.soft -> "Soft ${s.total}: you cannot bust, and against ${upName(up)} doubling is not quite worth it, so just take a card for free."

            // Hard hands
            s.total >= 17 -> "Hard ${s.total}: always stand. Only a few cards could help, most would bust you, and the dealer busts often enough to make standing right."
            s.total == 11 && action == Action.DOUBLE -> "11 is the best doubling hand there is: any ten-value card makes 21 and nothing can bust you. Put more money down while you are the favourite."
            s.total == 10 && action == Action.DOUBLE -> "Hard 10 against ${upName(up)}: you are a big favourite to end on 18-21 with one card, and the dealer is weaker than you. Double."
            s.total == 9 && action == Action.DOUBLE -> "Hard 9 against ${upName(up)}: the dealer is likely to bust, and one card gives you a strong total often enough to justify doubling."
            s.total in 9..11 -> "Hard ${s.total} against ${upName(up)}: doubling is not worth the extra money here, but you can never bust, so hit."
            s.total <= 8 -> "A total of ${s.total} cannot bust. Always take a card."
            s.total == 12 && weak && action == Action.HIT -> "12 against a 2 or 3 is the famous exception: the dealer busts only about a third of the time, and only the four ten-values bust you, so hitting is slightly better."
            s.total in 12..16 && weak -> "You hold a stiff ${s.total}, but the dealer shows ${upName(up)} and will bust roughly 40% of the time. Do not take that risk yourself: stand and make the dealer draw."
            s.total in 12..16 -> "Hard ${s.total} against ${upName(up)}: the dealer will usually finish with 17 or better, so standing loses most of the time. You have to take a card, bust risk and all."
            else -> "Chart says ${cell.code}."
        }
        val fallbackNote = when {
            action != cell.primary && cell.primary == Action.DOUBLE -> " Doubling is not allowed here, so ${action.label.lowercase()} instead."
            action != cell.primary && cell.primary == Action.SURRENDER -> " Surrender is not available, so ${action.label.lowercase()} instead."
            action != cell.primary && cell.primary == Action.SPLIT -> " Splitting is not available, so play the total and ${action.label.lowercase()}."
            else -> ""
        }
        return core + fallbackNote
    }

    // -------------------------------------------------------------- charts

    enum class Section(val title: String) { HARD("Hard totals"), SOFT("Soft totals"), PAIRS("Pairs") }

    /** Row keys for each chart section: hard 5..17, soft 13..20 (A2..A9), pairs 2..11. */
    fun rows(section: Section): List<Int> = when (section) {
        Section.HARD -> (5..17).toList()
        Section.SOFT -> (13..20).toList()
        Section.PAIRS -> (2..11).toList()
    }

    fun rowLabel(section: Section, key: Int): String = when (section) {
        Section.HARD -> if (key == 17) "17+" else key.toString()
        Section.SOFT -> "A," + (key - 11)
        Section.PAIRS -> if (key == 11) "A,A" else "$key,$key"
    }

    val dealerColumns: List<Int> = (2..11).toList()

    fun columnLabel(up: Int): String = if (up == 11) "A" else up.toString()

    /**
     * One chart cell. [hasFreeBet] only matters for Free Bet Blackjack, where a
     * hand riding on house money (always a split hand) plays differently.
     */
    fun chartCell(section: Section, key: Int, dealerUp: Int, rules: Rules, hasFreeBet: Boolean = false): ChartCode {
        val situation = when (section) {
            Section.HARD -> Situation(total = key, soft = false, pairOf = null, dealerUp = dealerUp, twoCards = true)
            Section.SOFT -> Situation(total = key, soft = true, pairOf = null, dealerUp = dealerUp, twoCards = true)
            Section.PAIRS -> Situation(total = if (key == 11) 12 else key * 2, soft = key == 11, pairOf = key, dealerUp = dealerUp, twoCards = true)
        }.copy(hasFreeBet = hasFreeBet, fromSplit = hasFreeBet)
        return if (rules.variant == Variant.FREE_BET) FreeBetStrategy.decide(situation, rules).code else decide(situation, rules)
    }
}
