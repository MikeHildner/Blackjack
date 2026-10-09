package com.mikehildner.blackjack.engine

/** How a natural blackjack pays. 3:2 is the traditional payout; 6:5 is a modern money-grab. */
enum class Payout(val label: String, val multiplier: Double) {
    THREE_TO_TWO("3:2", 1.5),
    SIX_TO_FIVE("6:5", 1.2),
    EVEN_MONEY("1:1", 1.0),
}

/** Which two-card totals the player is allowed to double down on. */
enum class DoubleRestriction(val label: String) {
    ANY_TWO("Any two cards"),
    NINE_TO_ELEVEN("9, 10 or 11 only"),
    TEN_TO_ELEVEN("10 or 11 only");

    fun allows(hand: Hand): Boolean = when (this) {
        ANY_TWO -> true
        NINE_TO_ELEVEN -> !hand.isSoft && hand.total in 9..11
        TEN_TO_ELEVEN -> !hand.isSoft && hand.total in 10..11
    }
}

/**
 * The house rules for a table. Every one of these changes the house edge,
 * and basic strategy has to adapt to several of them.
 *
 * All money is in cents: a $10 bet is 1000. Defaults are a common Las Vegas
 * Strip shoe game.
 */
data class Rules(
    val decks: Int = 6,
    /** H17: dealer hits soft 17. S17 (false) is better for the player. */
    val dealerHitsSoft17: Boolean = false,
    val blackjackPayout: Payout = Payout.THREE_TO_TWO,
    /** DAS: allowed to double after splitting a pair. */
    val doubleAfterSplit: Boolean = true,
    /** Late surrender: give up half the bet after the dealer checks for blackjack. */
    val lateSurrender: Boolean = true,
    val doubleRestriction: DoubleRestriction = DoubleRestriction.ANY_TWO,
    /** Maximum number of hands a player may end up with via splitting (2 = one split). */
    val maxSplitHands: Int = 4,
    val resplitAces: Boolean = false,
    /** Normally split aces receive exactly one card each. */
    val hitSplitAces: Boolean = false,
    /** Dealer checks the hole card for blackjack before players act (American rules). */
    val dealerPeeks: Boolean = true,
    /** Fraction of the shoe dealt before reshuffling. Ignored when [continuousShuffle] is on. */
    val penetration: Double = 0.75,
    val minBet: Int = 500,
    val maxBet: Int = 50_000,
    /**
     * Per-hand fee charged when you place a bet, win or lose. Oklahoma tribal
     * casinos charge one (typically 50 cents) because of their state compact.
     * It is by far the most expensive "rule" at a low-limit table.
     */
    val ante: Int = 0,
    /** The ante charged instead when the bet is above [anteThreshold]. */
    val anteAboveThreshold: Int = 0,
    val anteThreshold: Int = 5_000,
    /**
     * Continuous shuffling machine: every card goes back into the shuffler after
     * each round, so the shoe is effectively fresh every hand. Harmless for a
     * basic-strategy player, fatal for a card counter.
     */
    val continuousShuffle: Boolean = false,
) {
    init {
        require(decks in 1..8) { "decks must be 1..8" }
        require(maxSplitHands in 1..4)
        require(penetration in 0.1..1.0)
        require(minBet in 1..maxBet)
        require(ante >= 0 && anteAboveThreshold >= 0)
    }

    /** The fee for a round with this original bet. */
    fun anteFor(bet: Int): Int = if (bet > anteThreshold) anteAboveThreshold else ante

    /**
     * House edge including the ante, as a percentage of this bet size. A flat
     * fee is a huge percentage of a small bet and a small percentage of a big
     * one, which is why the ante matters more than any other rule at a $5 table.
     */
    fun effectiveHouseEdgePercent(bet: Int): Double =
        if (bet <= 0) houseEdgePercent else houseEdgePercent + 100.0 * anteFor(bet) / bet

    /**
     * A rough house-edge estimate for a basic-strategy player, in percent,
     * from the rules alone. See [effectiveHouseEdgePercent] for the ante.
     *
     * Built from the well-known rule adjustments (see docs/strategy-and-odds.md).
     * It is deliberately simple: an additive model that lands within a few
     * hundredths of a percent of published figures for common rule sets.
     * A continuous shuffler does not change it: basic strategy assumes a
     * fresh shoe anyway.
     */
    val houseEdgePercent: Double
        get() {
            // Baseline: 8 decks, S17, DAS, no surrender, 3:2, double any two, resplit to 4.
            var edge = 0.43
            edge += when (decks) {
                1 -> -0.48
                2 -> -0.19
                3 -> -0.10
                4 -> -0.06
                5 -> -0.03
                6 -> -0.02
                7 -> -0.01
                else -> 0.0
            }
            if (dealerHitsSoft17) edge += 0.22
            if (!doubleAfterSplit) edge += 0.14
            if (lateSurrender) edge -= 0.08
            edge += when (blackjackPayout) {
                Payout.THREE_TO_TWO -> 0.0
                Payout.SIX_TO_FIVE -> 1.39
                Payout.EVEN_MONEY -> 2.27
            }
            edge += when (doubleRestriction) {
                DoubleRestriction.ANY_TWO -> 0.0
                DoubleRestriction.NINE_TO_ELEVEN -> 0.09
                DoubleRestriction.TEN_TO_ELEVEN -> 0.18
            }
            if (resplitAces) edge -= 0.08
            if (hitSplitAces) edge -= 0.19
            if (maxSplitHands == 2) edge += 0.03
            if (maxSplitHands == 1) edge += 0.57
            if (!dealerPeeks) edge += 0.11
            return edge
        }

    /** One-line summary like "6D S17 DAS LS 3:2" or "6D H17 DAS 3:2 +ante CSM". */
    fun summary(): String = buildString {
        append(decks).append("D ")
        append(if (dealerHitsSoft17) "H17 " else "S17 ")
        append(if (doubleAfterSplit) "DAS " else "NDAS ")
        if (lateSurrender) append("LS ")
        append(blackjackPayout.label)
        if (ante > 0) append(" +ante")
        if (continuousShuffle) append(" CSM")
    }

    companion object {
        val VEGAS_STRIP = Rules()
        val SINGLE_DECK = Rules(decks = 1, dealerHitsSoft17 = true, doubleAfterSplit = false, lateSurrender = false)

        /**
         * Hard Rock Hotel & Casino Tulsa (Cherokee Nation). The ante is well
         * documented: 50 cents per hand up to $50, $1 above. The other rules are
         * the most common Oklahoma shoe game and should be confirmed at the table.
         */
        val HARD_ROCK_TULSA = Rules(
            decks = 6,
            dealerHitsSoft17 = true,
            lateSurrender = false,
            ante = 50,
            anteAboveThreshold = 100,
            anteThreshold = 5_000,
            continuousShuffle = true,
        )
    }
}

/** Named rule sets offered in Settings. Anything else is "Custom". */
enum class Preset(val label: String, val description: String, val rules: Rules) {
    VEGAS_STRIP(
        "Vegas Strip",
        "Six decks, dealer stands on soft 17, double after split, late surrender, 3:2. A good shoe game.",
        Rules.VEGAS_STRIP,
    ),
    HARD_ROCK_TULSA(
        "Hard Rock Tulsa",
        "Oklahoma: 50 cent ante per hand ($1 over $50), continuous shuffler, dealer hits soft 17, no surrender. " +
            "The ante is confirmed; check the felt for the rest.",
        Rules.HARD_ROCK_TULSA,
    ),
    SINGLE_DECK(
        "Single deck",
        "One deck, dealer hits soft 17, no double after split, no surrender. Classic, and easy to count.",
        Rules.SINGLE_DECK,
    );

    companion object {
        fun matching(rules: Rules): Preset? = entries.firstOrNull { it.rules == rules }
    }
}
