package com.mikehildner.blackjack.engine

import kotlin.random.Random

/**
 * One cell of a strategy chart, independent of the exact cards: the game
 * variant, the section (hard / soft / pairs), the row and the dealer up card.
 * Under Free Bet a hand riding on house money is a separate cell.
 */
data class CellKey(
    val variant: Variant,
    val section: BasicStrategy.Section,
    val row: Int,
    val dealerUp: Int,
    val freeBetHand: Boolean = false,
) {
    /** The abstract situation this cell stands for (first decision, two cards). */
    fun situation(): Situation = when (section) {
        BasicStrategy.Section.HARD -> Situation(total = row, soft = false, pairOf = null, dealerUp = dealerUp, twoCards = true)
        BasicStrategy.Section.SOFT -> Situation(total = row, soft = true, pairOf = null, dealerUp = dealerUp, twoCards = true)
        BasicStrategy.Section.PAIRS -> Situation(total = if (row == 11) 12 else row * 2, soft = row == 11, pairOf = row, dealerUp = dealerUp, twoCards = true)
    }.copy(fromSplit = freeBetHand, hasFreeBet = freeBetHand)

    /** "Hard 16 vs 10", "A,7 vs 3", "8,8 vs A". */
    fun label(): String {
        val hand = when (section) {
            BasicStrategy.Section.HARD -> "Hard $row"
            BasicStrategy.Section.SOFT -> "A,${row - 11}"
            BasicStrategy.Section.PAIRS -> if (row == 11) "A,A" else "$row,$row"
        }
        return "$hand vs ${BasicStrategy.columnLabel(dealerUp)}" + if (freeBetHand) " (free bet)" else ""
    }
}

/** How you have done on one cell. [recentMisses] decays so old mistakes fade. */
data class CellRecord(
    val attempts: Int = 0,
    val misses: Int = 0,
    val recentMisses: Double = 0.0,
    val lastSeenMillis: Long = 0,
) {
    val accuracy: Double get() = if (attempts == 0) 1.0 else (attempts - misses).toDouble() / attempts
}

/**
 * Your personal record against the strategy chart, and the sampler that turns
 * it into practice situations.
 *
 * Each cell's sampling weight rewards three things: recent misses (the point
 * of the exercise), never having been tested, and being a genuinely close call
 * according to the odds engine. Rows nobody gets wrong (hard 5-8, 17+) are
 * kept in the pool at a fraction of the weight so you cannot autopilot.
 */
class WeakSpots(initial: Map<CellKey, CellRecord> = emptyMap()) {

    private val records = HashMap(initial)
    private val difficultyCache = HashMap<Pair<Rules, CellKey>, Double>()
    private val oddsCache = HashMap<Rules, Odds>()

    fun record(key: CellKey, correct: Boolean, nowMillis: Long) {
        val r = records[key] ?: CellRecord()
        records[key] = r.copy(
            attempts = r.attempts + 1,
            misses = r.misses + if (correct) 0 else 1,
            recentMisses = r.recentMisses * DECAY + if (correct) 0.0 else 1.0,
            lastSeenMillis = nowMillis,
        )
    }

    fun recordOf(key: CellKey): CellRecord? = records[key]

    /** Null until the cell has been attempted at least once. */
    fun accuracy(key: CellKey): Double? = records[key]?.accuracy

    fun all(): Map<CellKey, CellRecord> = records.toMap()

    val totalAttempts: Int get() = records.values.sumOf { it.attempts }

    fun clear() = records.clear()

    // ------------------------------------------------------------- sampling

    /**
     * Every cell that can be dealt as an opening hand under these rules. Free
     * bet hands need a split in progress, and hard 20 is always a pair of tens,
     * so those are recorded but never drilled.
     */
    fun pool(rules: Rules): List<CellKey> = buildList {
        for (section in BasicStrategy.Section.entries) {
            for (row in BasicStrategy.rows(section, rules.variant)) {
                if (section == BasicStrategy.Section.HARD && (row == 4 || row == 20)) continue
                for (up in BasicStrategy.dealerColumns) add(CellKey(rules.variant, section, row, up))
            }
        }
    }

    fun weight(key: CellKey, rules: Rules): Double {
        val r = records[key]
        val rarity = when (key.section) {
            BasicStrategy.Section.PAIRS -> 1.0
            BasicStrategy.Section.SOFT -> 0.5
            BasicStrategy.Section.HARD -> 0.0
        }
        return difficulty(key, rules) + MISS_WEIGHT * (r?.recentMisses ?: 0.0) + (if (r == null) UNSEEN_BONUS else 0.0) + rarity
    }

    /** Weighted random pick from the pool. */
    fun pick(rules: Rules, random: Random): CellKey {
        val pool = pool(rules)
        val weights = pool.map { weight(it, rules) }
        var roll = random.nextDouble() * weights.sum()
        for (i in pool.indices) {
            roll -= weights[i]
            if (roll <= 0) return pool[i]
        }
        return pool.last()
    }

    /**
     * How close a call the cell is: 3 for a coin flip, 2 for close, 1 for
     * clear, scaled down for the rows everyone knows. Uses the EV gap between
     * the best and second-best action on a fresh shoe.
     */
    fun difficulty(key: CellKey, rules: Rules): Double = difficultyCache.getOrPut(rules to key) {
        val s = key.situation()
        val hand = FreeBetStrategy.representativeHand(s)
        val available = mutableSetOf(Action.HIT, Action.STAND)
        if (rules.isFreeDouble(hand) || rules.doubleRestriction.allows(hand)) available += Action.DOUBLE
        if (s.pairOf != null) available += Action.SPLIT
        if (rules.lateSurrender && !s.fromSplit) available += Action.SURRENDER
        val odds = oddsCache.getOrPut(rules) { Odds(Composition.fresh(rules.decks), rules) }
        val evs = odds.analyze(hand, Card(rankOf(key.dealerUp), Suit.SPADES), available).values.map { it.ev }.sortedDescending()
        val gap = if (evs.size >= 2) evs[0] - evs[1] else 1.0
        val base = when {
            gap < 0.03 -> 3.0
            gap < 0.10 -> 2.0
            else -> 1.0
        }
        val autopilot = when (key.section) {
            BasicStrategy.Section.HARD -> key.row <= 8 || (key.row >= 17 && key.variant == Variant.CLASSIC)
            BasicStrategy.Section.SOFT -> key.row >= 20
            BasicStrategy.Section.PAIRS -> false
        }
        if (autopilot) base * AUTOPILOT_FACTOR else base
    }

    // -------------------------------------------------------- persistence

    /** One line per cell: `VARIANT|SECTION|row|up|fb=attempts,misses,recentMisses,lastSeen`. */
    fun serialize(): String = records.entries.joinToString("\n") { (k, r) ->
        "${k.variant.name}|${k.section.name}|${k.row}|${k.dealerUp}|${if (k.freeBetHand) 1 else 0}=${r.attempts},${r.misses},${r.recentMisses},${r.lastSeenMillis}"
    }

    companion object {
        const val DECAY = 0.8
        const val MISS_WEIGHT = 4.0
        const val UNSEEN_BONUS = 1.5
        const val AUTOPILOT_FACTOR = 0.3

        fun parse(text: String): WeakSpots {
            val map = HashMap<CellKey, CellRecord>()
            for (line in text.lineSequence()) {
                val (keyPart, valuePart) = line.split("=", limit = 2).takeIf { it.size == 2 } ?: continue
                val k = keyPart.split("|")
                val v = valuePart.split(",")
                if (k.size != 5 || v.size != 4) continue
                try {
                    val key = CellKey(
                        variant = Variant.valueOf(k[0]),
                        section = BasicStrategy.Section.valueOf(k[1]),
                        row = k[2].toInt(),
                        dealerUp = k[3].toInt(),
                        freeBetHand = k[4] == "1",
                    )
                    map[key] = CellRecord(v[0].toInt(), v[1].toInt(), v[2].toDouble(), v[3].toLong())
                } catch (_: IllegalArgumentException) {
                    // A line from a future or corrupted version: skip it.
                }
            }
            return WeakSpots(map)
        }

        private fun rankOf(value: Int): Rank = when (value) {
            11 -> Rank.ACE
            10 -> Rank.TEN
            else -> Rank.entries.first { it.value == value }
        }
    }
}
