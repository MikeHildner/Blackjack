package com.mikehildner.blackjack.engine

/** The four suits. Suits never matter in blackjack scoring, only for display. */
enum class Suit(val symbol: String) {
    CLUBS("♣"), DIAMONDS("♦"), HEARTS("♥"), SPADES("♠");

    val isRed: Boolean get() = this == DIAMONDS || this == HEARTS
}

/**
 * Card ranks and their blackjack value.
 *
 * Face cards are worth 10. An ace is listed here as 11; [Hand] takes care of
 * counting it as 1 when 11 would bust the hand (a "soft" total becoming "hard").
 */
enum class Rank(val symbol: String, val value: Int) {
    TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5), SIX("6", 6),
    SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9), TEN("10", 10),
    JACK("J", 10), QUEEN("Q", 10), KING("K", 10), ACE("A", 11);

    val isAce: Boolean get() = this == ACE
    val isTenValue: Boolean get() = value == 10
}

data class Card(val rank: Rank, val suit: Suit) {
    val value: Int get() = rank.value
    val isAce: Boolean get() = rank.isAce

    override fun toString(): String = rank.symbol + suit.symbol

    companion object {
        /** All 52 cards of a single deck, in a fixed order. */
        val fullDeck: List<Card> = Suit.entries.flatMap { suit -> Rank.entries.map { Card(it, suit) } }

        /**
         * Convenience parser for tests and docs, e.g. `Card.of("Ah")`, `Card.of("10s")`.
         * Suit letters: c, d, h, s.
         */
        fun of(text: String): Card {
            val suitChar = text.last().lowercaseChar()
            val rankText = text.dropLast(1).uppercase()
            val rank = Rank.entries.first { it.symbol == rankText || (rankText == "T" && it == Rank.TEN) }
            val suit = when (suitChar) {
                'c' -> Suit.CLUBS
                'd' -> Suit.DIAMONDS
                'h' -> Suit.HEARTS
                's' -> Suit.SPADES
                else -> error("Unknown suit '$suitChar' in '$text'")
            }
            return Card(rank, suit)
        }
    }
}
