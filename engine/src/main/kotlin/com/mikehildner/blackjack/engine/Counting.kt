package com.mikehildner.blackjack.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Hi-Lo count, the most widely taught card counting system.
 *
 *  - 2 through 6 are low cards: +1 when they leave the shoe (good for the player).
 *  - 7, 8, 9 are neutral: 0.
 *  - 10, J, Q, K, A are high cards: -1 when they leave (bad for the player).
 *
 * The sum of all 52 values in a deck is 0, which makes Hi-Lo a *balanced*
 * count: a complete shoe always returns to zero.
 *
 * Why it works: a shoe rich in tens and aces produces more player blackjacks
 * (paid 3:2, while the dealer's only pay 1:1), more successful doubles, and
 * more dealer busts on stiff hands. The running count measures that richness.
 */
object HiLo {
    fun value(card: Card): Int = value(card.value)

    fun value(cardValue: Int): Int = when (cardValue) {
        in 2..6 -> 1
        in 7..9 -> 0
        else -> -1 // 10-value cards and aces
    }
}

/**
 * Tracks the running count across a shoe and turns it into a true count and
 * a bet suggestion.
 */
class CountTracker {
    var runningCount: Int = 0
        private set
    var cardsSeen: Int = 0
        private set

    fun see(card: Card) {
        runningCount += HiLo.value(card)
        cardsSeen++
    }

    fun reset() {
        runningCount = 0
        cardsSeen = 0
    }

    /**
     * True count = running count / decks remaining.
     *
     * A running count of +6 means little with five decks left but a lot with
     * one deck left. Dividing by the decks remaining normalises it; each
     * point of true count is worth roughly +0.5% to the player.
     */
    fun trueCount(decksRemaining: Double): Double = runningCount / max(decksRemaining, 0.5)

    /**
     * A simple, conservative betting ramp: 1 unit at true count 1 or less,
     * then (true count - 1) units, capped at 8 units.
     */
    fun suggestedUnits(decksRemaining: Double): Int =
        (floor(trueCount(decksRemaining)).toInt() - 1).coerceIn(1, 8)

    /**
     * Rough player advantage in percent, given the table's base house edge.
     * The commonly quoted rule of thumb is +0.5% per true count point.
     */
    fun playerEdgePercent(decksRemaining: Double, houseEdgePercent: Double): Double =
        -houseEdgePercent + 0.5 * trueCount(decksRemaining)

    companion object {
        /** Format a true count to one decimal, with a sign. */
        fun format(trueCount: Double): String {
            val rounded = (trueCount * 10).roundToInt() / 10.0
            return (if (rounded > 0) "+" else "") + rounded
        }
    }
}
