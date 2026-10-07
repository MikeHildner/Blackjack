package com.mikehildner.blackjack.engine

import kotlin.random.Random

/**
 * A dealing shoe holding one or more shuffled decks.
 *
 * Real casinos put a plastic "cut card" part-way into the shoe. When it comes
 * out, the current round finishes and the shoe is reshuffled. The fraction of
 * the shoe dealt before that point is the *penetration*; deeper penetration
 * is better for card counters because the count becomes more informative.
 */
class Shoe private constructor(
    val decks: Int,
    private val random: Random,
    private val penetration: Double,
    initialCards: List<Card>?,
) {
    private val cards: MutableList<Card> = mutableListOf()
    private var position = 0

    /** Count of remaining cards per blackjack value (index 2..11; 10 covers T/J/Q/K, 11 is ace). */
    private val byValue = IntArray(12)

    /** Incremented on every shuffle so observers (e.g. a card counter) can reset. */
    var shuffleCount: Int = 0
        private set

    init {
        if (initialCards != null) {
            cards.addAll(initialCards)
            position = 0
            recount()
        } else {
            shuffle()
        }
    }

    constructor(decks: Int, random: Random = Random.Default, penetration: Double = 0.75) :
        this(decks, random, penetration, null)

    val totalCards: Int get() = cards.size
    val remaining: Int get() = cards.size - position
    val dealt: Int get() = position
    val decksRemaining: Double get() = remaining / 52.0

    /** True once the cut card has been passed: finish the round, then [shuffle]. */
    val cutCardReached: Boolean get() = position >= (cards.size * penetration).toInt()

    fun shuffle() {
        cards.clear()
        repeat(decks) { cards.addAll(Card.fullDeck) }
        cards.shuffle(random)
        position = 0
        shuffleCount++
        recount()
    }

    fun draw(): Card {
        check(position < cards.size) { "Shoe is empty" }
        val card = cards[position++]
        byValue[card.value]--
        return card
    }

    /** Snapshot of the remaining composition, used by [Odds] to compute probabilities. */
    fun composition(): Composition = Composition(byValue.copyOf())

    private fun recount() {
        byValue.fill(0)
        for (i in position until cards.size) byValue[cards[i].value]++
    }

    companion object {
        /**
         * A shoe that deals exactly these cards in order. Used by tests and by the
         * trainer to set up specific situations ("you have 16, dealer shows 10").
         */
        fun rigged(cards: List<Card>, penetration: Double = 1.0): Shoe =
            Shoe(decks = maxOf(1, cards.size / 52), random = Random.Default, penetration = penetration, initialCards = cards)

        fun rigged(vararg cards: String): Shoe = rigged(cards.map(Card::of))
    }
}

/**
 * How many cards of each value remain. Index is the blackjack value 2..11
 * (10 lumps together T, J, Q, K; 11 is the ace).
 */
class Composition(private val counts: IntArray) {
    init { require(counts.size == 12) }

    operator fun get(value: Int): Int = counts[value]
    val total: Int get() = counts.sum()

    /** Probability that the next card has this value. */
    fun probability(value: Int): Double = if (total == 0) 0.0 else counts[value].toDouble() / total

    /** Same composition with one card of [value] removed (for conditional probabilities). */
    fun without(value: Int): Composition {
        val c = counts.copyOf()
        if (c[value] > 0) c[value]--
        return Composition(c)
    }

    /**
     * Same composition with one card of [value] added back. The game draws the
     * dealer hole card from the shoe, but the player cannot see it, so for the
     * odds it must be treated as still unknown.
     */
    fun with(value: Int): Composition {
        val c = counts.copyOf()
        c[value]++
        return Composition(c)
    }

    companion object {
        val VALUES: IntRange = 2..11

        /** A fresh shoe of [decks] decks: 4 of each rank, so 16 ten-values. */
        fun fresh(decks: Int): Composition {
            val c = IntArray(12)
            for (v in 2..9) c[v] = 4 * decks
            c[10] = 16 * decks
            c[11] = 4 * decks
            return Composition(c)
        }
    }
}
