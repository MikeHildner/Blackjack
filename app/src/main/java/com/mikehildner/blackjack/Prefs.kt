package com.mikehildner.blackjack

import android.content.Context
import androidx.core.content.edit
import com.mikehildner.blackjack.engine.DoubleRestriction
import com.mikehildner.blackjack.engine.Payout
import com.mikehildner.blackjack.engine.Rules

/** How much help the coach gives while you play. */
enum class CoachMode(val label: String, val description: String) {
    OFF("Off", "Just play. No hints, no grading."),
    FEEDBACK("Grade my decisions", "After each decision, see whether it matched basic strategy and why."),
    HINT("Show me the right play", "The correct action is highlighted before you act, with the reasoning."),
}

/** Everything the user can configure. Persisted in SharedPreferences by [Prefs]. */
data class AppSettings(
    val rules: Rules = Rules(),
    val coachMode: CoachMode = CoachMode.FEEDBACK,
    val showOdds: Boolean = true,
    val showCount: Boolean = false,
    val startingBankroll: Int = 1000,
    /** Card deal and flip animations. Off makes everything instant. */
    val animations: Boolean = true,
)

/** Lifetime statistics, also persisted. */
data class Stats(
    val hands: Int = 0,
    val decisions: Int = 0,
    val correct: Int = 0,
    val net: Int = 0,
) {
    val accuracyPercent: Int get() = if (decisions == 0) 100 else correct * 100 / decisions
}

/**
 * Thin wrapper over SharedPreferences. Deliberately boring: one key per
 * setting, so the stored data is easy to inspect and impossible to corrupt.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("blackjack", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        val d = Rules()
        val rules = Rules(
            decks = sp.getInt("decks", d.decks),
            dealerHitsSoft17 = sp.getBoolean("h17", d.dealerHitsSoft17),
            blackjackPayout = enumOr("payout", d.blackjackPayout),
            doubleAfterSplit = sp.getBoolean("das", d.doubleAfterSplit),
            lateSurrender = sp.getBoolean("ls", d.lateSurrender),
            doubleRestriction = enumOr("doubleOn", d.doubleRestriction),
            maxSplitHands = sp.getInt("maxSplitHands", d.maxSplitHands),
            resplitAces = sp.getBoolean("rsa", d.resplitAces),
            hitSplitAces = sp.getBoolean("hsa", d.hitSplitAces),
            dealerPeeks = sp.getBoolean("peek", d.dealerPeeks),
            penetration = sp.getFloat("penetration", d.penetration.toFloat()).toDouble(),
        )
        return AppSettings(
            rules = rules,
            coachMode = enumOr("coachMode", CoachMode.FEEDBACK),
            showOdds = sp.getBoolean("showOdds", true),
            showCount = sp.getBoolean("showCount", false),
            startingBankroll = sp.getInt("startingBankroll", 1000),
            animations = sp.getBoolean("animations", true),
        )
    }

    fun saveSettings(s: AppSettings) = sp.edit {
        val r = s.rules
        putInt("decks", r.decks)
        putBoolean("h17", r.dealerHitsSoft17)
        putString("payout", r.blackjackPayout.name)
        putBoolean("das", r.doubleAfterSplit)
        putBoolean("ls", r.lateSurrender)
        putString("doubleOn", r.doubleRestriction.name)
        putInt("maxSplitHands", r.maxSplitHands)
        putBoolean("rsa", r.resplitAces)
        putBoolean("hsa", r.hitSplitAces)
        putBoolean("peek", r.dealerPeeks)
        putFloat("penetration", r.penetration.toFloat())
        putString("coachMode", s.coachMode.name)
        putBoolean("showOdds", s.showOdds)
        putBoolean("showCount", s.showCount)
        putInt("startingBankroll", s.startingBankroll)
        putBoolean("animations", s.animations)
    }

    /** Null until the first hand has been played. */
    var bankroll: Int?
        get() = if (sp.contains("bankroll")) sp.getInt("bankroll", 0) else null
        set(value) = sp.edit { if (value == null) remove("bankroll") else putInt("bankroll", value) }

    var lastBet: Int
        get() = sp.getInt("lastBet", 10)
        set(value) = sp.edit { putInt("lastBet", value) }

    fun loadStats() = Stats(
        hands = sp.getInt("stats.hands", 0),
        decisions = sp.getInt("stats.decisions", 0),
        correct = sp.getInt("stats.correct", 0),
        net = sp.getInt("stats.net", 0),
    )

    fun saveStats(s: Stats) = sp.edit {
        putInt("stats.hands", s.hands)
        putInt("stats.decisions", s.decisions)
        putInt("stats.correct", s.correct)
        putInt("stats.net", s.net)
    }

    private inline fun <reified E : Enum<E>> enumOr(key: String, default: E): E {
        val name = sp.getString(key, null) ?: return default
        return enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
