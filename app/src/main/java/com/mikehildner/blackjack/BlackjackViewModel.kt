package com.mikehildner.blackjack

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikehildner.blackjack.engine.Action
import com.mikehildner.blackjack.engine.BasicStrategy
import com.mikehildner.blackjack.engine.Card
import com.mikehildner.blackjack.engine.CellKey
import com.mikehildner.blackjack.engine.CellRecord
import com.mikehildner.blackjack.engine.Composition
import com.mikehildner.blackjack.engine.CountTracker
import com.mikehildner.blackjack.engine.DealSequence
import com.mikehildner.blackjack.engine.Game
import com.mikehildner.blackjack.engine.Hand
import com.mikehildner.blackjack.engine.HiLo
import com.mikehildner.blackjack.engine.Odds
import com.mikehildner.blackjack.engine.OddsReport
import com.mikehildner.blackjack.engine.Phase
import com.mikehildner.blackjack.engine.PracticeDealer
import com.mikehildner.blackjack.engine.Recommendation
import com.mikehildner.blackjack.engine.RoundState
import com.mikehildner.blackjack.engine.Rules
import com.mikehildner.blackjack.engine.Shoe
import com.mikehildner.blackjack.engine.Situation
import com.mikehildner.blackjack.engine.WeakSpots
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random

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
 *
 * The engine resolves each action instantly. What the UI sees ([round]) lags
 * behind on purpose: [DealSequence] breaks every transition into one-card
 * steps, and [present] plays them back with pauses, so a deal looks like a
 * deal. While that is happening [isAnimating] is true and nothing is tappable.
 *
 * Practice mode swaps in a throwaway [Game] per situation, dealt from a shoe
 * stacked to open on a chart cell you need work on ([WeakSpots] chooses it).
 * The real table game is parked meanwhile and comes back untouched.
 */
class BlackjackViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val counter = CountTracker()

    var settings: AppSettings by mutableStateOf(prefs.loadSettings())
        private set
    var stats: Stats by mutableStateOf(prefs.loadStats())
        private set

    private var game: Game = createGame(settings.rules, prefs.bankroll ?: settings.startingBankroll)

    /** What is on the table right now, as the player sees it. Lags [Game.state] during playback. */
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
    /** Cards are still being dealt on screen; inputs are ignored until they land. */
    var isAnimating: Boolean by mutableStateOf(false)
        private set
    /** Rules changed mid-round; they apply once this round is over. */
    var pendingRules: Rules? by mutableStateOf(null)
        private set

    // ---- practice mode
    /** Your per-cell record against the chart. Read [weakSpotsVersion] in composables that show it. */
    val weakSpots: WeakSpots = WeakSpots.parse(prefs.weakSpots)
    var weakSpotsVersion: Int by mutableIntStateOf(0)
        private set
    var practice: Boolean by mutableStateOf(false)
        private set
    var playOut: Boolean by mutableStateOf(prefs.playOut)
        private set
    var practiceCorrect: Int by mutableIntStateOf(0)
        private set
    var practiceTotal: Int by mutableIntStateOf(0)
        private set
    /** Drill mode: the decision has been graded and the table waits for Next. */
    var practiceAwaitingNext: Boolean by mutableStateOf(false)
        private set
    var practiceKey: CellKey? by mutableStateOf(null)
        private set
    /** Bumped when something asks the UI to show the table (e.g. "practice this cell" from the chart). */
    var showTableRequests: Int by mutableIntStateOf(0)
        private set

    private var parkedTableGame: Game? = null
    private var forcedNextCell: CellKey? = null
    private val practiceRandom = Random.Default

    private var playback: Job? = null
    private var handRecorded = false

    // The count the player should see follows the cards on the table, not the engine.
    private var countAtRoundStart = 0
    private var seenAtRoundStart = 0

    /** Rules of the game actually being dealt (may lag [settings] by one round). */
    val rules: Rules get() = game.rules

    val trueCount: Double get() = counter.trueCount(decksRemaining)

    /** Free Bet Blackjack: is this action paid for by the house right now? Read after [actions] so it recomposes. */
    fun isFree(action: Action): Boolean = action in actions && game.isFree(action)

    /** Bet within limits and bankroll covering both the bet and the ante. */
    val canDeal: Boolean
        get() = !isAnimating && round.phase == Phase.BETTING && bet in rules.minBet..rules.maxBet && bet + rules.anteFor(bet) <= bankroll

    /** The record for the cell being practised, refreshed after each grading. */
    val practiceCellRecord: CellRecord?
        get() {
            weakSpotsVersion // dependency for recomposition
            return practiceKey?.let { weakSpots.recordOf(it) }
        }

    init {
        finish()
    }

    // --------------------------------------------------------------- queries

    /** Basic strategy for the current hand, or null when there is no decision to make. */
    fun recommendation(): Recommendation? {
        if (isAnimating) return null
        val hand = round.currentHand ?: return null
        val up = round.dealerUpCard ?: return null
        return BasicStrategy.recommend(hand, up, rules, actions)
    }

    /** The hint shown before acting, only in HINT mode and never in practice (it would be the answer). */
    val hint: Recommendation? get() = if (settings.coachMode == CoachMode.HINT && !practice) recommendation() else null

    /** Live odds. In practice they only appear once the decision has been graded, for the same reason. */
    fun odds(): OddsReport? {
        if (isAnimating) return null
        if (practice && !practiceAwaitingNext && round.phase == Phase.PLAYER_TURN) return null
        val hand = (if (practiceAwaitingNext) round.hands.getOrNull(round.activeHand) else round.currentHand) ?: return null
        val up = round.dealerUpCard ?: return null
        val available = if (practiceAwaitingNext) game.availableActions() else actions
        return Odds(composition(), rules).analyze(hand, up, available)
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
        // The ante has to be paid alongside the bet, so it shrinks what you can put down.
        val affordable = bankroll - rules.anteFor(amount)
        val cap = min(rules.maxBet, affordable)
        bet = amount.coerceIn(min(rules.minBet, cap), cap).coerceAtLeast(0)
        prefs.lastBet = bet
    }

    fun deal() {
        if (practice) { practiceNext(); return }
        if (isAnimating || round.phase != Phase.BETTING) return
        if (!game.canBet(bet)) {
            changeBet(bet)
            if (!game.canBet(bet)) return
        }
        feedback = null
        justShuffled = false
        handRecorded = false
        countAtRoundStart = counter.runningCount
        seenAtRoundStart = counter.cardsSeen
        game.placeBet(bet)
        bankroll = game.bankroll // the stake and ante leave immediately; winnings arrive with the result
        present()
    }

    fun insurance(take: Boolean) {
        if (isAnimating || round.phase != Phase.INSURANCE) return
        if (take && !game.canAffordInsurance()) return
        game.insurance(take)
        present()
    }

    fun act(action: Action) {
        if (isAnimating || practiceAwaitingNext) return
        val hand = round.currentHand ?: return
        val up = round.dealerUpCard ?: return
        if (action !in actions) return

        val rec = BasicStrategy.recommend(hand, up, rules, actions)
        val fb = Feedback(hand, up, action, rec)
        if (settings.coachMode != CoachMode.OFF || practice) feedback = fb
        grade(hand, up, rec, fb.correct)

        if (practice && !playOut) {
            // Drill: the decision is the whole exercise. Freeze the table and explain.
            practiceAwaitingNext = true
            actions = emptySet()
            return
        }
        game.act(action)
        present()
    }

    /** Lifetime statistics (table only) and the per-cell record (table and practice). */
    private fun grade(hand: Hand, up: Card, rec: Recommendation, correct: Boolean) {
        if (practice) {
            practiceTotal++
            if (correct) practiceCorrect++
        } else {
            stats = stats.copy(decisions = stats.decisions + 1, correct = stats.correct + if (correct) 1 else 0)
        }
        // Only count it against the cell when the chart's own action was on the table; a forced
        // fallback (three cards, cannot double) says nothing about whether you know the cell.
        if (rec.action == rec.chart.primary) {
            weakSpots.record(BasicStrategy.cellFor(Situation.of(hand, up), rules.variant), correct, System.currentTimeMillis())
            prefs.weakSpots = weakSpots.serialize()
            weakSpotsVersion++
        }
    }

    fun next() {
        if (practice) { practiceNext(); return }
        if (isAnimating || round.phase != Phase.SETTLED) return
        val shuffled = game.nextRound()
        feedback = null
        pendingRules?.let { applyRules(it) }
        if (shuffled) onShuffle()
        present()
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
            if (round.phase == Phase.BETTING && !isAnimating && !practice) applyRules(new.rules) else pendingRules = new.rules
        }
    }

    /** Start over with a fresh bankroll and zeroed statistics. Only between hands. */
    fun resetBankroll() {
        if (isAnimating || practice || (round.phase != Phase.BETTING && round.phase != Phase.SETTLED)) return
        game = createGame(rules, settings.startingBankroll)
        stats = Stats()
        prefs.saveStats(stats)
        onShuffle()
        finish()
    }

    /** Play money: when you bust out, refill. */
    fun addChips() {
        if (practice) return
        game.addFunds(settings.startingBankroll)
        bankroll = game.bankroll
        prefs.bankroll = bankroll
    }

    // --------------------------------------------------------------- practice

    fun enterPractice(on: Boolean) {
        if (on == practice || isAnimating) return
        if (on) {
            parkedTableGame = game
            practice = true
            practiceCorrect = 0
            practiceTotal = 0
            practiceNext()
        } else {
            playback?.cancel()
            practice = false
            practiceAwaitingNext = false
            practiceKey = null
            feedback = null
            game = parkedTableGame ?: game
            parkedTableGame = null
            // Rules changed while practising apply now if the table is between hands.
            pendingRules?.let { if (game.state.phase == Phase.BETTING) applyRules(it) }
            finish()
        }
    }

    fun togglePlayOut(on: Boolean) {
        playOut = on
        prefs.playOut = on
    }

    /** Deal the next practice situation: a forced cell if one was requested, else a weighted pick. */
    fun practiceNext() {
        if (!practice || isAnimating) return
        val tableRules = parkedTableGame?.rules ?: rules
        val practiceRules = pendingRules ?: tableRules
        val key = forcedNextCell?.takeIf { it.variant == practiceRules.variant } ?: weakSpots.pick(practiceRules, practiceRandom)
        forcedNextCell = null
        practiceKey = key
        practiceAwaitingNext = false
        feedback = null
        handRecorded = true // practice hands never touch lifetime stats

        game = Game(practiceRules, PracticeDealer.shoeFor(key, practiceRules, practiceRandom), PRACTICE_BANKROLL)
        round = RoundState() // clear the felt so the new situation is dealt from nothing
        game.placeBet(bet.coerceIn(practiceRules.minBet, practiceRules.maxBet))
        if (game.state.phase == Phase.INSURANCE) game.insurance(false)
        present()
    }

    /** From the chart: switch to practice and deal this cell next. */
    fun practiceCell(key: CellKey) {
        if (isAnimating) return
        forcedNextCell = key
        showTableRequests++
        if (practice) practiceNext() else enterPractice(true)
    }

    fun clearWeakSpots() {
        weakSpots.clear()
        prefs.weakSpots = ""
        weakSpotsVersion++
    }

    // --------------------------------------------------------------- internals

    private fun createGame(rules: Rules, bankroll: Int): Game {
        val g = Game(rules, Shoe(rules.decks, penetration = rules.penetration), bankroll)
        g.addCardListener { counter.see(it) }
        return g
    }

    private fun applyRules(r: Rules) {
        playback?.cancel()
        game = createGame(r, game.bankroll)
        pendingRules = null
        onShuffle()
        finish()
    }

    private fun onShuffle() {
        counter.reset()
        countAtRoundStart = 0
        seenAtRoundStart = 0
        justShuffled = true
    }

    private fun recordHand() {
        stats = stats.copy(hands = stats.hands + 1, net = stats.net + round.netResult)
        prefs.saveStats(stats)
    }

    /**
     * Show the engine's new state: all at once when animations are off or
     * nothing moved, otherwise one card at a time with the pauses from
     * [DealSequence].
     */
    private fun present() {
        playback?.cancel()
        val steps = if (settings.animations) DealSequence.steps(round, game.state) else emptyList()
        if (steps.size <= 1) {
            finish()
            return
        }
        isAnimating = true
        actions = emptySet()
        playback = viewModelScope.launch {
            for (step in steps.dropLast(1)) {
                round = step.state
                updateShownCount()
                delay(step.pauseMillis)
            }
            finish()
        }
    }

    /** The table now shows exactly what the engine has. */
    private fun finish() {
        playback = null
        round = game.state
        actions = game.availableActions()
        bankroll = game.bankroll
        decksRemaining = game.shoe.decksRemaining
        if (!practice) prefs.bankroll = bankroll
        isAnimating = false
        updateShownCount()
        if (round.phase == Phase.SETTLED && !handRecorded) {
            handRecorded = true
            recordHand()
        }
    }

    /** Running count and cards seen for the cards currently visible on the table. */
    private fun updateShownCount() {
        if (practice) return // the practice shoe is not the table shoe
        val dealerVisible = if (round.holeCardHidden) round.dealerCards.take(1) else round.dealerCards
        val visible = round.hands.flatMap { it.cards } + dealerVisible
        runningCount = countAtRoundStart + visible.sumOf { HiLo.value(it) }
        cardsSeen = seenAtRoundStart + visible.size
    }

    private companion object {
        const val PRACTICE_BANKROLL = 1_000_000
    }
}
