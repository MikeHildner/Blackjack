package com.mikehildner.blackjack

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.mikehildner.blackjack.engine.Action
import com.mikehildner.blackjack.engine.BasicStrategy
import com.mikehildner.blackjack.engine.Card
import com.mikehildner.blackjack.engine.Composition
import com.mikehildner.blackjack.engine.CountTracker
import com.mikehildner.blackjack.engine.Game
import com.mikehildner.blackjack.engine.Hand
import com.mikehildner.blackjack.engine.Odds
import com.mikehildner.blackjack.engine.OddsReport
import com.mikehildner.blackjack.engine.Phase
import com.mikehildner.blackjack.engine.Recommendation
import com.mikehildner.blackjack.engine.RoundState
import com.mikehildner.blackjack.engine.Rules
import com.mikehildner.blackjack.engine.Shoe
import kotlin.math.min

/** What the coach has to say about the decision you just made. */
data class Feedback(
    val hand: Hand,
    val dealerUp: Card,
    val chosen: Action,
    val recommended: Recommendation,
) {
    val correct: Boolean get() = chosen == recommended.action
}

/**
 * Glue between the Compose UI and the engine.
 *
 * All game logic lives in the engine. This class owns one [Game], exposes its
 * state as Compose state so the UI redraws automatically, records coaching
 * statistics, and persists settings and the bankroll.
 */
class BlackjackViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val counter = CountTracker()

    var settings: AppSettings by mutableStateOf(prefs.loadSettings())
        private set
    var stats: Stats by mutableStateOf(prefs.loadStats())
        private set

    private var game: Game = createGame(settings.rules, prefs.bankroll ?: settings.startingBankroll)

    var round: RoundState by mutableStateOf(game.state)
        private set
    var bankroll: Int by mutableStateOf(game.bankroll)
        private set
    var actions: Set<Action> by mutableStateOf(emptySet())
        private set
    var bet: Int by mutableStateOf(prefs.lastBet)
        private set
    var feedback: Feedback? by mutableStateOf(null)
        private set
    var runningCount: Int by mutableStateOf(0)
        private set
    var cardsSeen: Int by mutableStateOf(0)
        private set
    var decksRemaining: Double by mutableStateOf(game.shoe.decksRemaining)
        private set
    var justShuffled: Boolean by mutableStateOf(true)
        private set
    /** Rules changed mid-round; they apply once this round is over. */
    var pendingRules: Rules? by mutableStateOf(null)
        private set

    /** Rules of the game actually being dealt (may lag [settings] by one round). */
    val rules: Rules get() = game.rules

    val trueCount: Double get() = counter.trueCount(decksRemaining)

    init {
        refresh()
    }

    // --------------------------------------------------------------- queries

    /** Basic strategy for the current hand, or null when there is no decision to make. */
    fun recommendation(): Recommendation? {
        val hand = round.currentHand ?: return null
        val up = round.dealerUpCard ?: return null
        return BasicStrategy.recommend(hand, up, rules, actions)
    }

    /** The hint shown before acting, only in HINT mode. */
    val hint: Recommendation? get() = if (settings.coachMode == CoachMode.HINT) recommendation() else null

    fun odds(): OddsReport? {
        val hand = round.currentHand ?: return null
        val up = round.dealerUpCard ?: return null
        return Odds(composition(), rules).analyze(hand, up, actions)
    }

    fun insuranceEv(): Double = Odds(composition(), rules).evInsurance()

    /**
     * What the player knows about the shoe: everything dealt except the dealer
     * hole card, which is face down and therefore still "in the unknown pile".
     */
    private fun composition(): Composition {
        var c = game.shoe.composition()
        if (round.holeCardHidden && round.dealerCards.size > 1) c = c.with(round.dealerCards[1].value)
        return c
    }

    // --------------------------------------------------------------- actions

    fun changeBet(amount: Int) {
        val cap = min(rules.maxBet, bankroll)
        bet = amount.coerceIn(min(rules.minBet, cap), cap).coerceAtLeast(0)
        prefs.lastBet = bet
    }

    fun deal() {
        if (round.phase != Phase.BETTING) return
        if (!game.canBet(bet)) {
            changeBet(bet)
            if (!game.canBet(bet)) return
        }
        feedback = null
        justShuffled = false
        game.placeBet(bet)
        refresh()
        if (round.phase == Phase.SETTLED) recordHand()
    }

    fun insurance(take: Boolean) {
        if (round.phase != Phase.INSURANCE) return
        if (take && !game.canAffordInsurance()) return
        game.insurance(take)
        refresh()
        if (round.phase == Phase.SETTLED) recordHand()
    }

    fun act(action: Action) {
        val hand = round.currentHand ?: return
        val up = round.dealerUpCard ?: return
        if (action !in actions) return

        val rec = BasicStrategy.recommend(hand, up, rules, actions)
        val fb = Feedback(hand, up, action, rec)
        if (settings.coachMode != CoachMode.OFF) feedback = fb
        stats = stats.copy(decisions = stats.decisions + 1, correct = stats.correct + if (fb.correct) 1 else 0)

        game.act(action)
        refresh()
        if (round.phase == Phase.SETTLED) recordHand()
    }

    fun next() {
        if (round.phase != Phase.SETTLED) return
        val shuffled = game.nextRound()
        feedback = null
        pendingRules?.let { applyRules(it) }
        if (shuffled) onShuffle()
        refresh()
    }

    /** Convenience for fast play: clear the table and deal the same bet again. */
    fun nextAndDeal() {
        next()
        deal()
    }

    fun updateSettings(new: AppSettings) {
        val rulesChanged = new.rules != settings.rules
        settings = new
        prefs.saveSettings(new)
        if (rulesChanged) {
            if (round.phase == Phase.BETTING) applyRules(new.rules) else pendingRules = new.rules
        }
    }

    /** Start over with a fresh bankroll and zeroed statistics. Only between hands. */
    fun resetBankroll() {
        if (round.phase != Phase.BETTING && round.phase != Phase.SETTLED) return
        game = createGame(rules, settings.startingBankroll)
        stats = Stats()
        prefs.saveStats(stats)
        onShuffle()
        refresh()
    }

    /** Play money: when you bust out, refill. */
    fun addChips() {
        game.addFunds(settings.startingBankroll)
        refresh()
    }

    // --------------------------------------------------------------- internals

    private fun createGame(rules: Rules, bankroll: Int): Game {
        val g = Game(rules, Shoe(rules.decks, penetration = rules.penetration), bankroll)
        g.addCardListener { card ->
            counter.see(card)
            runningCount = counter.runningCount
            cardsSeen = counter.cardsSeen
        }
        return g
    }

    private fun applyRules(r: Rules) {
        game = createGame(r, game.bankroll)
        pendingRules = null
        onShuffle()
    }

    private fun onShuffle() {
        counter.reset()
        runningCount = 0
        cardsSeen = 0
        justShuffled = true
    }

    private fun recordHand() {
        stats = stats.copy(hands = stats.hands + 1, net = stats.net + round.netResult)
        prefs.saveStats(stats)
    }

    private fun refresh() {
        round = game.state
        bankroll = game.bankroll
        actions = game.availableActions()
        decksRemaining = game.shoe.decksRemaining
        prefs.bankroll = bankroll
    }
}
