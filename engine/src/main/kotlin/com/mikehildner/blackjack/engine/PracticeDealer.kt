package com.mikehildner.blackjack.engine

import kotlin.random.Random

/**
 * Builds a shoe that opens with a chosen chart cell, so Practice mode can go
 * through the ordinary [Game] with nothing special-cased: the same dealing,
 * coaching and odds code runs, it just starts from a hand you need to work on.
 */
object PracticeDealer {

    /** Card values for the player's two cards. Hard rows avoid pairs so they are not mistaken for one. */
    fun playerValues(key: CellKey): Pair<Int, Int> = when (key.section) {
        BasicStrategy.Section.PAIRS -> key.row to key.row
        BasicStrategy.Section.SOFT -> 11 to key.row - 11
        BasicStrategy.Section.HARD -> when {
            key.row <= 11 -> 2 to key.row - 2
            else -> 10 to key.row - 10
        }
    }

    /**
     * A full shuffled shoe for [rules] rearranged so the first four cards are
     * player, dealer up, player, hole, with the hole card chosen so the dealer
     * cannot have a blackjack (the decision must actually arrive).
     */
    fun shoeFor(key: CellKey, rules: Rules, random: Random): Shoe {
        val (p1, p2) = playerValues(key)
        require(p1 != p2 || key.section == BasicStrategy.Section.PAIRS) { "${key.label()} cannot be dealt without a pair" }

        val deck = ArrayList<Card>()
        repeat(rules.decks) { deck.addAll(Card.fullDeck) }
        deck.shuffle(random)

        fun take(value: Int): Card {
            val i = deck.indexOfFirst { it.value == value }
            check(i >= 0) { "no card of value $value left" }
            return deck.removeAt(i)
        }
        val first = take(p1)
        val up = take(key.dealerUp)
        val second = take(p2)
        val forbiddenHole = when (key.dealerUp) {
            11 -> 10
            10 -> 11
            else -> -1
        }
        val hole = if (forbiddenHole == -1) deck.removeAt(0) else deck.removeAt(deck.indexOfFirst { it.value != forbiddenHole })

        return Shoe.rigged(listOf(first, up, second, hole) + deck, penetration = rules.penetration)
    }
}
