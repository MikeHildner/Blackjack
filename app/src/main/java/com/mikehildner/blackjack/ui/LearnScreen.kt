package com.mikehildner.blackjack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikehildner.blackjack.BlackjackViewModel
import com.mikehildner.blackjack.engine.DoubleRestriction
import com.mikehildner.blackjack.engine.Payout
import com.mikehildner.blackjack.engine.Rules
import com.mikehildner.blackjack.ui.theme.Bad
import com.mikehildner.blackjack.ui.theme.Good
import java.util.Locale

private data class Topic(val title: String, val body: String)

private val Topics = listOf(
    Topic(
        "How a round works",
        """
        You bet, then everyone gets two cards. Your cards are face up; the dealer shows one card and keeps one face down (the hole card).

        Your goal is not "get close to 21". It is "beat the dealer": finish with a higher total than the dealer without going over 21, or still be standing when the dealer busts.

        You act first. Hit to take a card, Stand to stop, Double to double your bet for exactly one more card, Split a pair into two hands, or Surrender to give up half your bet. If you go over 21 you bust and lose immediately, even if the dealer later busts too. That ordering is the entire source of the house edge.

        Once you are done, the dealer turns over the hole card and follows a fixed rule: draw to 16, stand on 17 (some tables make the dealer hit a soft 17). No judgement, no choices.
        """.trimIndent(),
    ),
    Topic(
        "Card values, soft and hard",
        """
        Number cards count their face value. Jack, Queen and King count 10. An Ace counts 11 unless that would bust you, in which case it counts 1.

        A hand with an Ace counted as 11 is soft: Ace-6 is soft 17. You cannot bust a soft hand with one card, because the Ace simply drops to 1. Ace-6 plus a 9 becomes hard 16.

        This is why strategy has separate tables for soft hands: they can be played aggressively, hitting and doubling where a hard hand of the same total would stand.
        """.trimIndent(),
    ),
    Topic(
        "Blackjack and payouts",
        """
        An Ace plus a ten-value card as your first two cards is a blackjack, or natural. It beats any dealer 21 made with three or more cards and traditionally pays 3:2 (bet $10, win $15). If the dealer also has blackjack, it is a push.

        Many casinos now pay only 6:5 on blackjack. That one change costs you about 1.4% of every dollar bet, roughly quadrupling the house edge of a decent game. Never play 6:5 if a 3:2 table exists.

        A 21 made after splitting is not a blackjack and pays 1:1.
        """.trimIndent(),
    ),
    Topic(
        "Insurance and even money",
        """
        When the dealer shows an Ace you are offered insurance: a side bet of half your stake, paying 2:1 if the hole card is a ten. It has nothing to do with protecting your hand.

        Roughly 4 of every 13 cards are ten-value, so the hole card is a ten about 31% of the time. You need 33.3% to break even on a 2:1 payout. In a fresh shoe insurance loses about 7.7% of the money you put on it.

        "Even money" on your own blackjack is the same bet in disguise. Decline both unless you are counting cards and the true count is about +3 or higher.
        """.trimIndent(),
    ),
    Topic(
        "Why basic strategy works",
        """
        Basic strategy is not a hunch. For every combination of your hand and the dealer up card, someone has computed the expected value of each action over every possible way the cards could fall, and picked the best one. Those choices are the chart.

        Two ideas explain most of it. First, dealer up cards 2 to 6 are weak: the dealer must draw and will bust 35% to 42% of the time, so you stand on stiff totals (12 to 16) and let them. Second, up cards 7 to Ace are strong: the dealer usually makes 17 or better, so you have to take risks to catch up.

        Doubling and splitting are about getting more money on the table when you are the favourite: 11 against a 6, or a pair of 8s instead of a hopeless 16.

        Perfect basic strategy at a good table cuts the house edge to about half a percent. Guessing costs most players 2% or more. Turn on the coach and the live odds and you can watch the numbers behind every cell.
        """.trimIndent(),
    ),
    Topic(
        "Expected value, in plain words",
        """
        Expected value (EV) is what an action is worth on average per dollar bet, if you could play the same situation millions of times. An EV of -0.54 means you lose 54 cents per dollar in the long run.

        Hard 16 against a 10 has an EV of about -0.54 whether you hit or stand. Surrendering is exactly -0.50. That is why surrender is correct: losing half for certain beats losing more than half on average.

        The live odds panel computes EV from the cards actually left in the shoe, so the numbers shift as cards come out. That shift is what card counters exploit.
        """.trimIndent(),
    ),
    Topic(
        "The per-hand ante (Oklahoma)",
        """
        Oklahoma tribal casinos charge a fee on every blackjack hand, typically 50 cents, rising to a dollar on larger bets. You pay it when you bet and never see it again, whether you win, lose, push or draw a blackjack. Hard Rock Tulsa charges it; some casinos in the north-east corner of the state waive it if you use a players card.

        It sounds small. It is not. The house edge of the game itself is about half a percent of your bet. A 50 cent ante on a $5 bet is 10% of your bet. On $10 it is 5%, on $25 it is 2%, and only at $50 does it fall to 1%, where the ante doubles. At a $10 table you lose about 55 cents a hand on average, 50 of them to the ante. Over 70 hands an hour that is roughly $35 an hour before the cards do anything.

        The same arithmetic ruins card counting. A counter wants to bet the minimum while the count is bad and only bet big when it is good. The ante punishes exactly those small waiting bets, so most of the edge the count would have given you goes to the fee.

        If you must play an ante table, bet more per hand and fewer hands, and prefer a casino that waives the fee for rated play. Set the ante in Settings and the table will show the true cost at your bet size.
        """.trimIndent(),
    ),
    Topic(
        "Card counting is legal and mostly arithmetic",
        """
        Counting cards means tracking whether the remaining shoe is rich or poor in high cards. It is not illegal anywhere; casinos can simply ask you to leave.

        Hi-Lo assigns +1 to 2-6, 0 to 7-9 and -1 to tens and Aces. Keep a running total as cards appear. Divide by the decks still to be dealt to get the true count. Each true count point is worth about half a percent to you, so at a true count of +2 a half-percent house edge has flipped to a half-percent player edge.

        The money comes from betting more when the count is high and the minimum when it is not. Playing deviations (like taking insurance at +3) add a little more.

        Two things kill counting outright. A continuous shuffling machine feeds every played card straight back into the shoe, so the composition never drifts and the count never means anything; low-limit tables often use one. And a per-hand ante taxes the small bets you make while waiting for a good count. Look for a shoe with a cut card, deep penetration and no fee.

        Realistically, the edge is small (around 1%), the variance is large, and you need a bankroll of hundreds of bets to survive the swings. The Count tab lets you practise the skill without any of the risk.
        """.trimIndent(),
    ),
    Topic(
        "Glossary",
        """
        Bust: going over 21. Lose immediately.
        Stiff: a hard 12 to 16, the hands most likely to bust.
        Pat hand: 17 or more. You stand.
        Push: a tie. Your bet is returned.
        Natural: a two-card 21, blackjack.
        Hole card: the dealer face-down card.
        Up card: the dealer face-up card.
        Shoe: the box holding several shuffled decks.
        Cut card: a plastic card marking where the shoe will be reshuffled.
        Penetration: how deep into the shoe the cut card sits. Deeper is better for counters.
        CSM: continuous shuffling machine. Played cards go straight back in; the count never develops.
        Ante: a per-hand fee charged by Oklahoma casinos, paid whether you win or lose.
        S17 / H17: the dealer stands / hits on soft 17.
        DAS: doubling after a split is allowed.
        Late surrender: give up half your bet after the dealer checks for blackjack.
        Peek: the dealer checks the hole card for blackjack before you act, so you cannot lose doubles and splits to a dealer natural.
        House edge: the casino long-run profit as a percentage of each bet.
        Unit: your base bet, used to describe bet sizes when counting.
        """.trimIndent(),
    ),
)

/** Reference material plus a live breakdown of the house edge at the current table. */
@Composable
fun LearnScreen(vm: BlackjackViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Learn", style = MaterialTheme.typography.headlineSmall)
        HouseEdgeCard(vm.settings.rules)
        Topics.forEachIndexed { i, topic -> TopicCard(topic, initiallyExpanded = i == 0) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TopicCard(topic: Topic, initiallyExpanded: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(topic.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(expanded) {
                Text(topic.body, modifier = Modifier.padding(top = 10.dp), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

/** Shows what each rule at the current table costs or saves, using the engine estimate. */
@Composable
private fun HouseEdgeCard(rules: Rules) {
    val base = rules.houseEdgePercent
    fun delta(alt: Rules) = alt.houseEdgePercent - base

    val comparisons = buildList {
        add("Dealer ${if (rules.dealerHitsSoft17) "stands" else "hits"} soft 17" to delta(rules.copy(dealerHitsSoft17 = !rules.dealerHitsSoft17)))
        add("Blackjack pays ${if (rules.blackjackPayout == Payout.THREE_TO_TWO) "6:5" else "3:2"}" to delta(rules.copy(blackjackPayout = if (rules.blackjackPayout == Payout.THREE_TO_TWO) Payout.SIX_TO_FIVE else Payout.THREE_TO_TWO)))
        add("${if (rules.doubleAfterSplit) "No d" else "D"}ouble after split" to delta(rules.copy(doubleAfterSplit = !rules.doubleAfterSplit)))
        add("${if (rules.lateSurrender) "No s" else "S"}urrender" to delta(rules.copy(lateSurrender = !rules.lateSurrender)))
        if (rules.decks > 1) add("Single deck instead of ${rules.decks}" to delta(rules.copy(decks = 1)))
        if (rules.decks < 8) add("Eight decks instead of ${rules.decks}" to delta(rules.copy(decks = 8)))
        if (rules.doubleRestriction == DoubleRestriction.ANY_TWO) add("Double on 10-11 only" to delta(rules.copy(doubleRestriction = DoubleRestriction.TEN_TO_ELEVEN)))
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("House edge at your table", style = MaterialTheme.typography.titleMedium)
            Text(
                "${rules.summary()}: about ${String.format(Locale.US, "%.2f", base)}% with perfect basic strategy. " +
                    "That is what the casino keeps of every dollar you bet, on average. Here is how changing one rule would move it:",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            comparisons.forEach { (label, d) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, fontSize = 14.sp)
                    Text(
                        String.format(Locale.US, "%+.2f%%", d),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (d > 0) Bad else Good,
                    )
                }
            }
            if (rules.ante > 0) {
                Text("What the ${money(rules.ante)} ante costs on top, by bet size:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                listOf(500, 1_000, 2_500, 5_000, 10_000).forEach { bet ->
                    val total = rules.effectiveHouseEdgePercent(bet)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${money(bet)} bet (ante ${money(rules.anteFor(bet))})", fontSize = 14.sp)
                        Text(
                            String.format(Locale.US, "%.1f%% total", total),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (total > 2.0) Bad else Good,
                        )
                    }
                }
            } else {
                Text("No ante at this table. Oklahoma casinos add a per-hand fee that dwarfs every line above; see the ante topic below.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Estimates from the standard rule-adjustment tables; exact figures vary slightly with the full rule set.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
