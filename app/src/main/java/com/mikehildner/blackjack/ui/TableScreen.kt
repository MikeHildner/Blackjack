package com.mikehildner.blackjack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikehildner.blackjack.BlackjackViewModel
import com.mikehildner.blackjack.CoachMode
import com.mikehildner.blackjack.engine.Action
import com.mikehildner.blackjack.engine.ActionValue
import com.mikehildner.blackjack.engine.CountTracker
import com.mikehildner.blackjack.engine.Hand
import com.mikehildner.blackjack.engine.HandResult
import com.mikehildner.blackjack.engine.Outcome
import com.mikehildner.blackjack.engine.Phase
import com.mikehildner.blackjack.engine.RoundState
import com.mikehildner.blackjack.ui.theme.Bad
import com.mikehildner.blackjack.ui.theme.FeltDark
import com.mikehildner.blackjack.ui.theme.FeltLight
import com.mikehildner.blackjack.ui.theme.Gold
import com.mikehildner.blackjack.ui.theme.Good
import java.util.Locale
import kotlin.math.roundToInt

/** Cents to "$1,000", or "$9.50" when there are cents to show. */
private fun dollars(cents: Int): String {
    val abs = kotlin.math.abs(cents)
    return if (abs % 100 == 0) String.format(Locale.US, "$%,d", abs / 100)
    else String.format(Locale.US, "$%,d.%02d", abs / 100, abs % 100)
}

fun money(cents: Int): String = (if (cents < 0) "-" else "") + dollars(cents)
fun signedMoney(cents: Int): String = (if (cents >= 0) "+" else "-") + dollars(cents)
fun pct(p: Double): String = "${(p * 100).roundToInt()}%"
fun pct1(percent: Double): String = String.format(Locale.US, "%.1f%%", percent)
fun ev(v: Double): String = String.format(Locale.US, "%+.2f", v)

/** The game screen: dealer, hands, coaching panels and the controls. */
@Composable
fun TableScreen(vm: BlackjackViewModel, modifier: Modifier = Modifier) {
    val round = vm.round
    Column(modifier.fillMaxSize()) {
        StatusRow(vm)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            DealerArea(round)
            Spacer(Modifier.height(16.dp))
            PlayerArea(round)
            Spacer(Modifier.height(12.dp))
            if (round.phase == Phase.SETTLED) ResultBanner(round)
            if (vm.justShuffled && round.phase == Phase.BETTING && !vm.rules.continuousShuffle) {
                Notice("Fresh shoe: ${vm.rules.decks} deck${if (vm.rules.decks > 1) "s" else ""} shuffled, count reset to 0.")
            }
            vm.pendingRules?.let { Notice("New table rules take effect on the next hand.") }
            CoachPanel(vm)
            if (vm.settings.showOdds) OddsPanel(vm)
            if (vm.settings.showCount) CountPanel(vm)
            Spacer(Modifier.height(8.dp))
        }
        Controls(vm)
    }
}

// ------------------------------------------------------------------ status

@Composable
private fun StatusRow(vm: BlackjackViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(FeltDark)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatusItem("Bankroll", money(vm.bankroll), if (vm.bankroll >= vm.settings.startingBankroll) Good else Bad)
        if (vm.rules.continuousShuffle) StatusItem("Shoe", "CSM ${vm.rules.decks}D")
        else StatusItem("Shoe", String.format(Locale.US, "%.1f decks", vm.decksRemaining))
        StatusItem("Strategy", "${vm.stats.accuracyPercent}%", if (vm.stats.accuracyPercent >= 90) Good else Gold)
        StatusItem("Hands", vm.stats.hands.toString())
    }
}

@Composable
private fun StatusItem(label: String, value: String, valueColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// ------------------------------------------------------------------ table

@Composable
private fun DealerArea(round: RoundState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dealer", style = MaterialTheme.typography.titleMedium)
            if (round.dealerCards.isNotEmpty()) {
                val visible = round.visibleDealerHand
                val label = if (round.holeCardHidden) "Showing ${visible.total}" else visible.describe()
                Pill(label, FeltLight)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (round.dealerCards.isEmpty()) {
            Box(Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                Text("Place your bet to deal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            CardFan(round.dealerCards, hiddenIndex = if (round.holeCardHidden) 1 else -1, fromTop = true)
        }
    }
}

@Composable
private fun PlayerArea(round: RoundState) {
    if (round.hands.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        round.hands.forEachIndexed { i, hand ->
            val active = round.phase == Phase.PLAYER_TURN && i == round.activeHand
            HandRow(hand, active, round.results.getOrNull(i), index = if (round.hands.size > 1) i + 1 else null)
        }
    }
}

@Composable
private fun HandRow(hand: Hand, active: Boolean, result: HandResult?, index: Int?) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (active) Modifier.border(2.dp, Gold, shape) else Modifier)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardFan(hand.cards, width = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (index != null) Text("Hand $index", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Pill(hand.describe(), if (hand.isBust) Bad else FeltLight)
            }
            Text(
                buildString {
                    append("Bet ").append(money(hand.bet))
                    if (hand.isDoubled) append(" (doubled)")
                    if (hand.isSurrendered) append(" (surrendered)")
                },
                fontSize = 13.sp,
            )
            if (result != null) {
                val color = when (result.outcome) {
                    Outcome.WIN, Outcome.BLACKJACK -> Good
                    Outcome.PUSH -> Gold
                    else -> Bad
                }
                Pill("${result.outcome.label}  ${signedMoney(result.net)}", color, textColor = Color.Black)
            }
        }
    }
}

@Composable
private fun ResultBanner(round: RoundState) {
    // The headline is about the cards; the ante gets its own line so it is never hidden inside a "win".
    val net = round.handsNet
    val text = when {
        round.results.size == 1 && round.results[0].outcome == Outcome.BLACKJACK && round.insuranceNet == 0 -> "Blackjack! You win ${money(net)}"
        net > 0 -> "You win ${money(net)}"
        net < 0 -> "You lose ${money(-net)}"
        else -> "Push: your bet comes back"
    }
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = if (net > 0) Good else if (net < 0) Bad else Gold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
    if (round.insuranceNet != 0) {
        Text(
            if (round.insuranceNet > 0) "Insurance paid ${money(round.insuranceNet)}" else "Insurance lost ${money(-round.insuranceNet)}",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (round.ante > 0) {
        Text(
            "Ante ${money(round.ante)} kept by the house (net ${signedMoney(round.netResult)})",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            color = Bad,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

// ------------------------------------------------------------------ coaching

@Composable
private fun Panel(title: String, accent: Color = FeltLight, content: @Composable () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, accent),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = accent)
            content()
        }
    }
}

@Composable
private fun CoachPanel(vm: BlackjackViewModel) {
    if (vm.settings.coachMode == CoachMode.OFF) return
    val round = vm.round

    when (round.phase) {
        Phase.INSURANCE -> {
            val e = vm.insuranceEv()
            Panel("Coach", Gold) {
                Text(
                    if (e > 0) "Unusually, insurance is worth it right now: the shoe is rich in tens."
                    else "Decline insurance. It is a side bet that the hole card is a ten, paid 2:1, but tens are less than a third of the shoe. Expected loss: ${pct(-e)} of the insurance bet.",
                )
            }
        }
        Phase.PLAYER_TURN -> {
            val hint = vm.hint
            if (hint != null) {
                Panel("Basic strategy says: ${hint.action.label}", Gold) { Text(hint.reason) }
            } else {
                vm.feedback?.let { FeedbackContent(it) }
            }
        }
        Phase.SETTLED -> vm.feedback?.let { FeedbackContent(it) }
        Phase.BETTING -> Unit
    }
}

@Composable
private fun FeedbackContent(fb: com.mikehildner.blackjack.Feedback) {
    val title = if (fb.correct) "Correct: ${fb.chosen.label} on ${fb.hand.describe()} vs ${fb.dealerUp.rank.symbol}"
    else "You chose ${fb.chosen.label}; basic strategy says ${fb.recommended.action.label} on ${fb.hand.describe()} vs ${fb.dealerUp.rank.symbol}"
    Panel(title, if (fb.correct) Good else Bad) {
        Text(fb.recommended.reason)
    }
}

@Composable
private fun OddsPanel(vm: BlackjackViewModel) {
    val round = vm.round
    when (round.phase) {
        Phase.PLAYER_TURN -> {
            val report = vm.odds() ?: return
            Panel("Live odds") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Bust if you hit: ${pct(report.bustIfHit)}")
                    Text("Dealer busts: ${pct(report.dealer.bust)}")
                }
                Text("Expected value per $1 bet:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                report.values.sortedByDescending { it.ev }.forEach { EvRow(it, it.action == report.best.action) }
            }
        }
        Phase.INSURANCE -> {
            Panel("Live odds") {
                Text("Insurance EV: ${ev(vm.insuranceEv())} per $1 insured")
            }
        }
        else -> Unit
    }
}

@Composable
private fun EvRow(value: ActionValue, best: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            value.action.label + if (value.approximate) " (approx.)" else "",
            fontWeight = if (best) FontWeight.Bold else FontWeight.Normal,
            color = if (best) Good else Color.White,
        )
        Text(ev(value.ev), fontWeight = if (best) FontWeight.Bold else FontWeight.Normal, color = if (value.ev >= 0) Good else Bad)
    }
}

@Composable
private fun CountPanel(vm: BlackjackViewModel) {
    Panel("Hi-Lo count") {
        val tc = vm.trueCount
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Running ${if (vm.runningCount > 0) "+" else ""}${vm.runningCount}", fontWeight = FontWeight.Bold)
            Text("True ${CountTracker.format(tc)}", fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.1f decks left", vm.decksRemaining))
        }
        if (vm.rules.continuousShuffle) {
            Text(
                "Continuous shuffler: every card goes straight back in, so the count resets each hand and cannot help you. " +
                    "Counters avoid these tables.",
                fontSize = 13.sp,
                color = Bad,
            )
        } else {
            val units = (kotlin.math.floor(tc).toInt() - 1).coerceIn(1, 8)
            Text(
                "Suggested bet: $units unit${if (units > 1) "s" else ""} (${money(units * vm.rules.minBet)}). " +
                    if (tc >= 2) "The shoe favours you: bet more." else "No edge yet: bet the minimum.",
                fontSize = 13.sp,
            )
            if (vm.rules.ante > 0) {
                Text(
                    "With a ${money(vm.rules.ante)} ante the fee is largest exactly when you want to bet small, which eats most of a counter's edge.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ controls

@Composable
private fun Controls(vm: BlackjackViewModel) {
    Surface(color = FeltDark, tonalElevation = 2.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (vm.round.phase) {
                Phase.BETTING -> BettingControls(vm)
                Phase.INSURANCE -> InsuranceControls(vm)
                Phase.PLAYER_TURN -> ActionControls(vm)
                Phase.SETTLED -> SettledControls(vm)
            }
        }
    }
}

@Composable
private fun BettingControls(vm: BlackjackViewModel) {
    val rules = vm.rules
    if (vm.bankroll < rules.minBet) {
        Text("Out of chips. It is only play money.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Button(onClick = vm::addChips, modifier = Modifier.fillMaxWidth()) { Text("Add ${money(vm.settings.startingBankroll)} in chips") }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Bet ${money(vm.bet)}", style = MaterialTheme.typography.titleLarge, color = Gold)
        Text("Table ${money(rules.minBet)} to ${money(rules.maxBet)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(500, 2_500, 10_000).forEach { chip ->
            OutlinedButton(onClick = { vm.changeBet(vm.bet + chip) }, modifier = Modifier.weight(1f)) { Text("+${chip / 100}") }
        }
        OutlinedButton(onClick = { vm.changeBet(rules.minBet) }, modifier = Modifier.weight(1f)) { Text("Min") }
    }
    val ante = rules.anteFor(vm.bet)
    if (ante > 0) {
        val edge = rules.effectiveHouseEdgePercent(vm.bet)
        Text(
            "Ante ${money(ante)} per hand, win or lose. House edge at this bet: ${pct1(edge)} " +
                "(${pct1(rules.houseEdgePercent)} from the rules, the rest is the ante).",
            fontSize = 12.sp,
            color = if (edge > 2.0) Bad else Gold,
        )
    }
    Button(onClick = vm::deal, modifier = Modifier.fillMaxWidth(), enabled = vm.canDeal) {
        Text("Deal", fontSize = 18.sp)
    }
}

@Composable
private fun InsuranceControls(vm: BlackjackViewModel) {
    val cost = vm.round.hands.firstOrNull()?.bet?.div(2) ?: 0
    Text(
        "Dealer shows an Ace. Insurance costs ${money(cost)} and pays 2:1 if the dealer has blackjack.",
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.insurance(false) }, modifier = Modifier.weight(1f)) { Text("No insurance") }
        OutlinedButton(onClick = { vm.insurance(true) }, modifier = Modifier.weight(1f), enabled = cost <= vm.bankroll) { Text("Insure ${money(cost)}") }
    }
}

@Composable
private fun ActionControls(vm: BlackjackViewModel) {
    val hint = vm.hint?.action
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Action.HIT, Action.STAND, Action.DOUBLE).forEach { ActionButton(vm, it, hint, Modifier.weight(1f)) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Action.SPLIT, Action.SURRENDER).forEach { ActionButton(vm, it, hint, Modifier.weight(1f)) }
    }
}

@Composable
private fun ActionButton(vm: BlackjackViewModel, action: Action, hint: Action?, modifier: Modifier) {
    val enabled = action in vm.actions
    val highlighted = hint == action
    Button(
        onClick = { vm.act(action) },
        enabled = enabled,
        modifier = modifier,
        border = if (highlighted) BorderStroke(3.dp, Gold) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (highlighted) Gold else FeltLight,
            contentColor = if (highlighted) Color.Black else Color.White,
            disabledContainerColor = Color(0x22FFFFFF),
            disabledContentColor = Color(0x66FFFFFF),
        ),
    ) {
        Text(action.label)
    }
}

@Composable
private fun SettledControls(vm: BlackjackViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = vm::next, modifier = Modifier.weight(1f)) { Text("Change bet") }
        Button(
            onClick = vm::nextAndDeal,
            modifier = Modifier.weight(1f),
            enabled = vm.bet <= vm.bankroll && vm.bet >= vm.rules.minBet,
        ) { Text("Deal ${money(vm.bet)}") }
    }
    if (vm.bankroll < vm.rules.minBet) {
        Button(onClick = { vm.next(); vm.addChips() }, modifier = Modifier.fillMaxWidth()) { Text("Out of chips: add ${money(vm.settings.startingBankroll)}") }
    }
}
