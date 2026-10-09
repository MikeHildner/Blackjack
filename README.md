# Blackjack

A free, open source, ad-free blackjack app for Android whose main purpose is to **teach**:
how the game works, why basic strategy is what it is, what the odds really are on
every decision, and how card counting works.

Play money only. There is nothing to buy and nothing phones home.

## What it does

| Tab | What you get |
|-----|--------------|
| **Table** | A full game: bet, hit, stand, double, split, surrender, insurance. Configurable casino rules. |
| **Coach** (on the table) | Grades every decision against basic strategy and explains *why* in plain English. Or shows the right play before you act. |
| **Live odds** (on the table) | Your bust chance, the dealer bust chance, and the expected value of every available action, computed from the cards actually left in the shoe. |
| **Chart** | The complete basic strategy chart for the rules you chose. The cell for your current hand is highlighted while you play. |
| **Count** | Hi-Lo card counting: the live running and true count at your table, a betting suggestion, and a flash-card drill. |
| **Learn** | Rules, soft vs hard hands, payouts, insurance, expected value, counting, a glossary, and a live breakdown of what each house rule costs you. |
| **Settings** | Presets (Vegas Strip, Hard Rock Tulsa, Single deck) or custom: decks, S17/H17, 3:2 or 6:5, double after split, surrender, doubling limits, resplitting, peeking, penetration, continuous shuffler, per-hand ante, bankroll. |

The **per-hand ante** deserves a mention. Oklahoma tribal casinos charge a fee on every hand
(50 cents, $1 on bigger bets). The app deducts it, shows the effective house edge at your bet
size, and explains why it matters more than any rule on the felt. Money in the engine is in
cents for this reason.

## Project layout

```
Blackjack/
├── engine/   Pure Kotlin. Zero Android dependencies. Fully unit tested.
│   └── src/main/kotlin/com/mikehildner/blackjack/engine/
│       ├── Card.kt            Suit, Rank, Card
│       ├── Shoe.kt            Multi-deck shoe, cut card, remaining-card composition
│       ├── Hand.kt            Soft/hard totals, blackjack, pairs
│       ├── Rules.kt           Every house rule, plus a house-edge estimate
│       ├── Game.kt            The round state machine: bet → insurance → play → dealer → settle
│       ├── BasicStrategy.kt   The strategy tables, rule-aware, with explanations
│       ├── Odds.kt            Dealer distribution and expected value of each action
│       └── Counting.kt        Hi-Lo running count, true count, bet ramp
└── app/      Jetpack Compose UI. Renders engine state and forwards taps.
    └── src/main/java/com/mikehildner/blackjack/
        ├── BlackjackViewModel.kt   Owns one Game, exposes Compose state, records stats
        ├── Prefs.kt                Settings and bankroll persistence
        └── ui/                     One file per screen
```

The split matters: everything that is *blackjack* lives in `engine` and can be
tested in milliseconds on your laptop without an emulator. Everything that is
*Android* lives in `app` and contains no game rules at all.

## Building

Requirements: Android Studio (2026.1 or newer), or the Android SDK plus any JDK to launch Gradle with. `gradle/gradle-daemon-jvm.properties` pins the build to JDK 25 and Gradle downloads it automatically if it is missing.

```bash
./gradlew :engine:test          # run the engine unit tests
./gradlew :app:assembleDebug    # build app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug     # install on a connected phone with USB debugging on
```

Or open the folder in Android Studio and press Run.

## Where to start reading

1. `engine/src/main/kotlin/.../Hand.kt`: the soft/hard total logic, ten lines that the whole game rests on.
2. `Game.kt`: the round as a state machine. Follow `placeBet`, `act`, `dealerPlay`, `settle`.
3. `BasicStrategy.kt`: the chart as code, and the `explain` function that turns a cell into a sentence.
4. `Odds.kt`: how expected values are computed. The dealer distribution recursion is about 30 lines.
5. `engine/src/test/.../GameTest.kt`: scripted rounds using a rigged shoe, the easiest way to see the rules in action.

See the `docs/` folder for the maths behind the strategy and the house-edge numbers.

## Licence

MIT. See [LICENSE](LICENSE).
