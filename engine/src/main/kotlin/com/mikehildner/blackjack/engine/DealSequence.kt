package com.mikehildner.blackjack.engine

/**
 * Turns one engine transition into the sequence of pictures a dealer would
 * show you: one card at a time, in dealing order, hole card flipped before
 * the dealer draws, results last.
 *
 * The engine resolves a whole action instantly. The UI shows these steps
 * instead, pausing after each. Every step is an ordinary [RoundState], so
 * nothing downstream needs to know it is looking at a half-dealt round.
 *
 * Intermediate states carry `results = empty`, `activeHand = -1` (no hand is
 * highlighted while cards are moving) and `phase = PLAYER_TURN` (so the
 * action buttons are drawn, disabled, rather than the result controls).
 */
object DealSequence {
    const val CARD_PAUSE = 220L
    const val FLIP_PAUSE = 500L
    const val DRAW_PAUSE = 550L
    const val RESULT_PAUSE = 350L

    data class Step(val state: RoundState, val pauseMillis: Long)

    fun steps(from: RoundState, to: RoundState): List<Step> {
        if (to.phase == Phase.BETTING || to == from) return listOf(Step(to, 0))

        val out = ArrayList<Step>()
        fun add(state: RoundState, pause: Long) {
            val previous = out.lastOrNull()?.state ?: from
            if (sameCards(state, previous)) return
            out += Step(state, pause)
        }

        val interim = to.copy(
            results = emptyList(),
            insuranceNet = 0,
            activeHand = -1,
            phase = if (to.phase == Phase.SETTLED || to.phase == Phase.INSURANCE) Phase.PLAYER_TURN else to.phase,
        )

        var dealerShown: List<Card>
        var holeHidden: Boolean

        if (from.hands.isEmpty()) {
            // Opening deal: player, dealer, player, dealer (face down).
            val player = to.hands[0]
            val up = to.dealerCards.take(1)
            val one = player.copy(cards = player.cards.take(1))
            val two = player.copy(cards = player.cards.take(2))
            add(interim.copy(hands = listOf(one), dealerCards = emptyList(), holeCardHidden = true), CARD_PAUSE)
            add(interim.copy(hands = listOf(one), dealerCards = up, holeCardHidden = true), CARD_PAUSE)
            add(interim.copy(hands = listOf(two), dealerCards = up, holeCardHidden = true), CARD_PAUSE)
            add(interim.copy(hands = listOf(two), dealerCards = to.dealerCards.take(2), holeCardHidden = true), CARD_PAUSE)
            dealerShown = to.dealerCards.take(2)
            holeHidden = true
        } else {
            dealerShown = from.dealerCards
            holeHidden = from.holeCardHidden
            val k = actedHand(from, to)

            if (to.hands.size > from.hands.size) {
                // A split: both halves appear with one card, then each takes its second.
                val first = to.hands[k]
                val second = to.hands[k + 1]
                val bare = to.hands.toMutableList().also {
                    it[k] = first.copy(cards = first.cards.take(1))
                    it[k + 1] = second.copy(cards = second.cards.take(1))
                }
                add(interim.copy(hands = bare, dealerCards = dealerShown, holeCardHidden = holeHidden), CARD_PAUSE)
                val firstDealt = bare.toMutableList().also { it[k] = first }
                add(interim.copy(hands = firstDealt, dealerCards = dealerShown, holeCardHidden = holeHidden), CARD_PAUSE)
                add(interim.copy(hands = to.hands, dealerCards = dealerShown, holeCardHidden = holeHidden), CARD_PAUSE)
            } else if (k in from.hands.indices && k in to.hands.indices) {
                // Hit or double: one step per card drawn to the acted hand.
                val before = from.hands[k].cards.size
                val target = to.hands[k]
                for (n in before + 1..target.cards.size) {
                    val hands = to.hands.toMutableList().also { it[k] = target.copy(cards = target.cards.take(n)) }
                    add(interim.copy(hands = hands, dealerCards = dealerShown, holeCardHidden = holeHidden), CARD_PAUSE)
                }
            }
        }

        // Dealer: flip the hole card, then one draw at a time.
        if (holeHidden && !to.holeCardHidden) {
            holeHidden = false
            add(interim.copy(hands = to.hands, dealerCards = to.dealerCards.take(maxOf(dealerShown.size, 2)), holeCardHidden = false), FLIP_PAUSE)
            dealerShown = to.dealerCards.take(maxOf(dealerShown.size, 2))
        }
        for (n in dealerShown.size + 1..to.dealerCards.size) {
            add(interim.copy(hands = to.hands, dealerCards = to.dealerCards.take(n), holeCardHidden = holeHidden), DRAW_PAUSE)
        }

        // Let the last card land before the results appear.
        if (to.phase == Phase.SETTLED && out.isNotEmpty()) {
            val last = out.removeAt(out.size - 1)
            out += last.copy(pauseMillis = maxOf(last.pauseMillis, RESULT_PAUSE))
        }
        out += Step(to, 0)
        return out
    }

    /** The hand the player just acted on. Falls back to the first hand whose cards changed. */
    private fun actedHand(from: RoundState, to: RoundState): Int {
        if (from.activeHand in from.hands.indices) return from.activeHand
        val changed = from.hands.indices.firstOrNull { i -> to.hands.getOrNull(i)?.cards != from.hands[i].cards }
        return changed ?: 0
    }

    /** True when two states would draw the same cards (ignoring phase, results and highlights). */
    private fun sameCards(a: RoundState, b: RoundState): Boolean =
        a.hands.map { it.cards } == b.hands.map { it.cards } &&
            a.dealerCards == b.dealerCards &&
            a.holeCardHidden == b.holeCardHidden &&
            a.hands.size == b.hands.size
}
