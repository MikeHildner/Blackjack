package com.mikehildner.blackjack.engine

/**
 * One blackjack hand and the bet riding on it.
 *
 * Totals: every card counts its face value, aces count 1, and if the hand
 * holds an ace that can be promoted to 11 without busting, the total is "soft".
 * A soft hand cannot bust on the next card, which is why strategy treats
 * soft and hard totals differently.
 */
data class Hand(
    val cards: List<Card>,
    val bet: Int,
    val isDoubled: Boolean = false,
    val isFromSplit: Boolean = false,
    val isFromSplitAces: Boolean = false,
    val isSurrendered: Boolean = false,
    val isStood: Boolean = false,
) {
    /** Total counting every ace as 1. */
    val hardTotal: Int = cards.sumOf { if (it.isAce) 1 else it.value }

    /** True if an ace is currently being counted as 11. */
    val isSoft: Boolean = cards.any { it.isAce } && hardTotal + 10 <= 21

    /** Best total: soft when possible, hard otherwise. */
    val total: Int = if (isSoft) hardTotal + 10 else hardTotal

    val isBust: Boolean get() = total > 21

    /** A natural: ace + ten-value as the first two cards of an unsplit hand. */
    val isBlackjack: Boolean get() = cards.size == 2 && total == 21 && !isFromSplit

    /** Two cards of equal value (any two ten-value cards count as a pair). */
    val isPair: Boolean get() = cards.size == 2 && cards[0].value == cards[1].value

    /** Nothing more can happen to this hand. */
    val isResolved: Boolean get() = isBust || isStood || isSurrendered || isDoubled || isBlackjack

    operator fun plus(card: Card): Hand = copy(cards = cards + card)

    /** "Soft 18", "Hard 16", "Blackjack", "Bust (23)". */
    fun describe(): String = when {
        isBlackjack -> "Blackjack"
        isBust -> "Bust ($total)"
        isSoft -> "Soft $total"
        else -> "Hard $total"
    }

    companion object {
        fun of(bet: Int, vararg cards: String): Hand = Hand(cards.map(Card::of), bet)
    }
}
