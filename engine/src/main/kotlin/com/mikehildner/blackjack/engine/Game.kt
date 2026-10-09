package com.mikehildner.blackjack.engine

/** Where we are in a round. */
enum class Phase {
    /** Waiting for the player to place a bet. */
    BETTING,
    /** Dealer shows an ace; player may buy insurance before anything else happens. */
    INSURANCE,
    /** Player is acting on one of their hands. */
    PLAYER_TURN,
    /** Dealer has finished drawing and every bet has been paid or collected. */
    SETTLED,
}

/** How one hand finished. */
enum class Outcome(val label: String) {
    BLACKJACK("Blackjack!"),
    WIN("Win"),
    PUSH("Push"),
    LOSE("Lose"),
    BUST("Bust"),
    SURRENDER("Surrendered"),
    DEALER_BLACKJACK("Dealer blackjack"),
    /** Free Bet Blackjack: the dealer busted with exactly 22, which only pushes. */
    PUSH_22("Dealer 22: push"),
}

/** Result for a single hand: the outcome and the net change to the bankroll (profit, loss, or 0). */
data class HandResult(val outcome: Outcome, val net: Int)

/**
 * Immutable snapshot of a round. The UI renders this; nothing else.
 */
data class RoundState(
    val phase: Phase = Phase.BETTING,
    val hands: List<Hand> = emptyList(),
    val activeHand: Int = 0,
    val dealerCards: List<Card> = emptyList(),
    val holeCardHidden: Boolean = true,
    val insuranceBet: Int = 0,
    val results: List<HandResult> = emptyList(),
    /** Net result of the insurance side bet, if any. */
    val insuranceNet: Int = 0,
    /** Per-hand fee paid when the bet was placed. Never comes back. */
    val ante: Int = 0,
) {
    val dealerUpCard: Card? get() = dealerCards.firstOrNull()

    /** The dealer hand as the player can currently see it. */
    val visibleDealerHand: Hand
        get() = Hand(if (holeCardHidden) dealerCards.take(1) else dealerCards, bet = 0)

    val dealerHand: Hand get() = Hand(dealerCards, bet = 0)

    val currentHand: Hand? get() = hands.getOrNull(activeHand).takeIf { phase == Phase.PLAYER_TURN }

    /** Net result of the hands and insurance, before the ante. */
    val handsNet: Int get() = results.sumOf { it.net } + insuranceNet

    /** Total net change to the bankroll from this round, once settled, ante included. */
    val netResult: Int get() = handsNet - ante
}

/**
 * The blackjack state machine. One instance lives for a whole session and
 * deals round after round from its [shoe].
 *
 * Call sequence per round:
 *   placeBet -> (insurance?) -> act... -> [SETTLED] -> nextRound
 */
class Game(
    val rules: Rules,
    val shoe: Shoe,
    bankroll: Int,
) {
    var bankroll: Int = bankroll
        private set

    var state: RoundState = RoundState()
        private set

    private val cardListeners = mutableListOf<(Card) -> Unit>()

    /** Called for every card that becomes visible to the player (needed for card counting). */
    fun addCardListener(listener: (Card) -> Unit) { cardListeners += listener }

    private fun reveal(card: Card) = cardListeners.forEach { it(card) }

    private fun drawVisible(): Card = shoe.draw().also(::reveal)

    // ---------------------------------------------------------------- betting

    /** The bet plus the ante must both be covered. */
    fun canBet(amount: Int): Boolean =
        state.phase == Phase.BETTING && amount in rules.minBet..rules.maxBet && amount + rules.anteFor(amount) <= bankroll

    fun placeBet(amount: Int) {
        require(canBet(amount)) { "Cannot bet $amount" }
        val ante = rules.anteFor(amount)
        bankroll -= amount + ante

        val first = drawVisible()
        val up = drawVisible()
        val second = drawVisible()
        val hole = shoe.draw() // face down: NOT revealed to counters yet

        val player = Hand(listOf(first, second), bet = amount)
        state = RoundState(
            phase = Phase.PLAYER_TURN,
            hands = listOf(player),
            dealerCards = listOf(up, hole),
            holeCardHidden = true,
            ante = ante,
        )

        if (up.isAce) {
            state = state.copy(phase = Phase.INSURANCE)
        } else {
            afterInsuranceDecision()
        }
    }

    // -------------------------------------------------------------- insurance

    val insuranceCost: Int get() = state.hands.firstOrNull()?.bet?.div(2) ?: 0

    fun canAffordInsurance(): Boolean = state.phase == Phase.INSURANCE && insuranceCost <= bankroll && insuranceCost > 0

    fun insurance(take: Boolean) {
        check(state.phase == Phase.INSURANCE)
        if (take) {
            require(canAffordInsurance())
            bankroll -= insuranceCost
            state = state.copy(insuranceBet = insuranceCost)
        }
        afterInsuranceDecision()
    }

    private fun afterInsuranceDecision() {
        val dealer = state.dealerHand
        val player = state.hands[0]

        if (rules.dealerPeeks && dealer.isBlackjack) {
            revealHole()
            settle()
            return
        }
        if (player.isBlackjack) {
            // Player natural, dealer has none (or cannot be checked): pay out now.
            revealHole()
            settle()
            return
        }
        state = state.copy(phase = Phase.PLAYER_TURN, activeHand = 0)
    }

    // ------------------------------------------------------------ player turn

    /**
     * Free Bet Blackjack: would this action be paid for by the house?
     * Free double: two-card hard 9, 10 or 11. Free split: any pair but tens.
     */
    fun isFree(action: Action): Boolean = state.currentHand?.let { isFree(action, it) } ?: false

    private fun isFree(action: Action, hand: Hand): Boolean = when (action) {
        Action.DOUBLE -> rules.isFreeDouble(hand)
        Action.SPLIT -> rules.isFreeSplit(hand)
        else -> false
    }

    /** Legal actions for the current hand, considering rules and bankroll. */
    fun availableActions(): Set<Action> {
        val hand = state.currentHand ?: return emptySet()
        if (hand.isResolved) return emptySet()

        val actions = mutableSetOf(Action.HIT, Action.STAND)
        val firstDecision = hand.cards.size == 2
        val canAffordAnother = bankroll >= hand.wager

        if (firstDecision && !hand.isFromSplitAces) {
            val dasOk = !hand.isFromSplit || rules.doubleAfterSplit
            val funded = canAffordAnother || isFree(Action.DOUBLE, hand)
            if (dasOk && funded && rules.doubleRestriction.allows(hand)) actions += Action.DOUBLE
        }
        if (firstDecision && hand.isPair && state.hands.size < rules.maxSplitHands) {
            val acesOk = !hand.cards[0].isAce || !hand.isFromSplit || rules.resplitAces
            val funded = canAffordAnother || isFree(Action.SPLIT, hand)
            if (acesOk && funded) actions += Action.SPLIT
        }
        if (firstDecision && !hand.isFromSplit && rules.lateSurrender && state.hands.size == 1) {
            actions += Action.SURRENDER
        }
        return actions
    }

    fun act(action: Action) {
        require(action in availableActions()) { "$action is not available" }
        val idx = state.activeHand
        val hand = state.hands[idx]
        val hands = state.hands.toMutableList()

        when (action) {
            Action.HIT -> {
                val next = hand + drawVisible()
                hands[idx] = if (next.total == 21) next.copy(isStood = true) else next
            }
            Action.STAND -> hands[idx] = hand.copy(isStood = true)
            Action.DOUBLE -> {
                val extra = hand.wager
                val doubled = if (isFree(Action.DOUBLE, hand)) {
                    hand.copy(freeBet = hand.freeBet + extra)       // the house puts up the lammer
                } else {
                    bankroll -= extra
                    hand.copy(bet = hand.bet + extra)
                }
                hands[idx] = (doubled + drawVisible()).copy(isDoubled = true)
            }
            Action.SURRENDER -> hands[idx] = hand.copy(isSurrendered = true)
            Action.SPLIT -> {
                val aces = hand.cards[0].isAce
                // Hand one keeps whatever was riding on the original hand.
                val one = Hand(listOf(hand.cards[0]), hand.bet, isFromSplit = true, isFromSplitAces = aces, freeBet = hand.freeBet)
                // Hand two is funded either by the house (free split) or by the player.
                val two = if (isFree(Action.SPLIT, hand)) {
                    Hand(listOf(hand.cards[1]), bet = 0, isFromSplit = true, isFromSplitAces = aces, freeBet = hand.wager)
                } else {
                    bankroll -= hand.wager
                    Hand(listOf(hand.cards[1]), bet = hand.wager, isFromSplit = true, isFromSplitAces = aces)
                }
                hands[idx] = finishSplitHand(one + drawVisible())
                hands.add(idx + 1, finishSplitHand(two + drawVisible()))
            }
        }
        state = state.copy(hands = hands)
        advance()
    }

    /** Split aces normally get one card and stop; any 21 stands automatically. */
    private fun finishSplitHand(hand: Hand): Hand = when {
        hand.isFromSplitAces && !rules.hitSplitAces -> hand.copy(isStood = true)
        hand.total == 21 -> hand.copy(isStood = true)
        else -> hand
    }

    private fun advance() {
        val next = state.hands.indexOfFirst { !it.isResolved }
        if (next >= 0) {
            state = state.copy(activeHand = next, phase = Phase.PLAYER_TURN)
        } else {
            dealerPlay()
        }
    }

    // ------------------------------------------------------------ dealer turn

    private fun revealHole() {
        if (state.holeCardHidden) {
            reveal(state.dealerCards[1])
            state = state.copy(holeCardHidden = false)
        }
    }

    private fun dealerPlay() {
        revealHole()
        val anyLive = state.hands.any { !it.isBust && !it.isSurrendered }
        if (anyLive) {
            var dealer = state.dealerHand
            while (dealerMustHit(dealer)) dealer += drawVisible()
            state = state.copy(dealerCards = dealer.cards)
        }
        settle()
    }

    private fun dealerMustHit(hand: Hand): Boolean =
        hand.total < 17 || (hand.total == 17 && hand.isSoft && rules.dealerHitsSoft17)

    // ------------------------------------------------------------- settlement

    private fun settle() {
        val dealer = state.dealerHand
        val dealerBj = dealer.isBlackjack
        val results = state.hands.map { hand -> settleHand(hand, dealer, dealerBj) }

        val insuranceNet = when {
            state.insuranceBet == 0 -> 0
            dealerBj -> state.insuranceBet * 2
            else -> -state.insuranceBet
        }

        // Return every stake plus winnings. The net figures exclude the original stake.
        bankroll += state.hands.sumOf { it.bet } + results.sumOf { it.net }
        if (state.insuranceBet > 0) bankroll += state.insuranceBet + insuranceNet

        state = state.copy(phase = Phase.SETTLED, results = results, insuranceNet = insuranceNet, holeCardHidden = false)
    }

    /**
     * A win pays the real bet plus any free bet; a loss costs only the real bet;
     * a push returns nothing extra (the free bet is simply taken back).
     */
    private fun settleHand(hand: Hand, dealer: Hand, dealerBj: Boolean): HandResult {
        val win = hand.bet + hand.freeBet
        return when {
            hand.isSurrendered -> HandResult(Outcome.SURRENDER, -hand.bet / 2)
            hand.isBust -> HandResult(Outcome.BUST, -hand.bet)
            dealerBj && hand.isBlackjack -> HandResult(Outcome.PUSH, 0)
            dealerBj -> HandResult(Outcome.DEALER_BLACKJACK, -hand.bet)
            hand.isBlackjack -> HandResult(Outcome.BLACKJACK, (hand.bet * rules.blackjackPayout.multiplier).toInt())
            dealer.total == 22 && rules.pushOn22 -> HandResult(Outcome.PUSH_22, 0)
            dealer.isBust -> HandResult(Outcome.WIN, win)
            hand.total > dealer.total -> HandResult(Outcome.WIN, win)
            hand.total < dealer.total -> HandResult(Outcome.LOSE, -hand.bet)
            else -> HandResult(Outcome.PUSH, 0)
        }
    }

    // ------------------------------------------------------------- next round

    /**
     * Clears the table. Returns true if the shoe was reshuffled, which happens
     * past the cut card, or after every round with a continuous shuffler.
     */
    fun nextRound(): Boolean {
        check(state.phase == Phase.SETTLED)
        state = RoundState()
        if (rules.continuousShuffle || shoe.cutCardReached) {
            shoe.shuffle()
            return true
        }
        return false
    }

    /** Top up the bankroll (play money, after all). */
    fun addFunds(amount: Int) { bankroll += amount }
}
