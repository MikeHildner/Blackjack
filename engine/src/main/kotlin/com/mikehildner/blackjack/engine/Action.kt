package com.mikehildner.blackjack.engine

/** Everything a player can do with a hand. */
enum class Action(val label: String, val shortCode: String) {
    HIT("Hit", "H"),
    STAND("Stand", "S"),
    DOUBLE("Double", "D"),
    SPLIT("Split", "P"),
    SURRENDER("Surrender", "R"),
}
