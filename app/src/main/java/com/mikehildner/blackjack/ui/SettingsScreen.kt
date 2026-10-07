package com.mikehildner.blackjack.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikehildner.blackjack.BlackjackViewModel
import com.mikehildner.blackjack.CoachMode
import com.mikehildner.blackjack.engine.DoubleRestriction
import com.mikehildner.blackjack.engine.Payout
import com.mikehildner.blackjack.engine.Phase
import com.mikehildner.blackjack.engine.Rules
import com.mikehildner.blackjack.ui.theme.Gold
import java.util.Locale

const val REPO_URL = "https://github.com/MikeHildner/Blackjack"

/** Teaching aids, table rules and bankroll. Every change is saved immediately. */
@Composable
fun SettingsScreen(vm: BlackjackViewModel, modifier: Modifier = Modifier) {
    val s = vm.settings
    val rules = s.rules
    fun update(r: Rules) = vm.updateSettings(s.copy(rules = r))

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        SectionHeader("Teaching")
        CoachMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.updateSettings(s.copy(coachMode = mode)) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s.coachMode == mode, onClick = { vm.updateSettings(s.copy(coachMode = mode)) })
                Column {
                    Text(mode.label)
                    Text(mode.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SwitchRow("Live odds", "Bust chance, dealer bust chance and the EV of every option while you decide.", s.showOdds) {
            vm.updateSettings(s.copy(showOdds = it))
        }
        SwitchRow("Card count", "Show the Hi-Lo running and true count on the table.", s.showCount) {
            vm.updateSettings(s.copy(showCount = it))
        }

        SectionHeader("Table rules")
        Text(
            "${rules.summary()}  ·  house edge about ${String.format(Locale.US, "%.2f", rules.houseEdgePercent)}%",
            color = Gold,
            fontSize = 13.sp,
        )
        if (vm.round.phase != Phase.BETTING) {
            Text("Rule changes take effect when the current hand is over.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ChipRow("Decks", listOf(1, 2, 4, 6, 8).map { it.toString() to it }, rules.decks) { update(rules.copy(decks = it)) }
        SwitchRow("Dealer hits soft 17 (H17)", "Worse for you by about 0.22%.", rules.dealerHitsSoft17) { update(rules.copy(dealerHitsSoft17 = it)) }
        ChipRow("Blackjack pays", Payout.entries.map { it.label to it }, rules.blackjackPayout) { update(rules.copy(blackjackPayout = it)) }
        SwitchRow("Double after split (DAS)", "Lets you double on hands made by splitting.", rules.doubleAfterSplit) { update(rules.copy(doubleAfterSplit = it)) }
        SwitchRow("Late surrender", "Give up half your bet on a hopeless first two cards.", rules.lateSurrender) { update(rules.copy(lateSurrender = it)) }
        ChipRow("Double on", DoubleRestriction.entries.map { it.label to it }, rules.doubleRestriction) { update(rules.copy(doubleRestriction = it)) }
        ChipRow("Max hands after splitting", listOf(2, 3, 4).map { it.toString() to it }, rules.maxSplitHands) { update(rules.copy(maxSplitHands = it)) }
        SwitchRow("Resplit aces", "Split again if you draw another ace to a split ace.", rules.resplitAces) { update(rules.copy(resplitAces = it)) }
        SwitchRow("Hit split aces", "Normally split aces receive exactly one card.", rules.hitSplitAces) { update(rules.copy(hitSplitAces = it)) }
        SwitchRow("Dealer peeks for blackjack", "American rule. Off means European no-hole-card style.", rules.dealerPeeks) { update(rules.copy(dealerPeeks = it)) }

        Text("Penetration: ${(rules.penetration * 100).toInt()}% of the shoe is dealt before a shuffle", modifier = Modifier.padding(top = 6.dp))
        Slider(
            value = rules.penetration.toFloat(),
            onValueChange = { update(rules.copy(penetration = (Math.round(it * 20) / 20.0).coerceIn(0.5, 0.95))) },
            valueRange = 0.5f..0.95f,
            steps = 8,
        )

        SectionHeader("Bankroll")
        ChipRow("Starting chips", listOf(500, 1000, 5000).map { money(it) to it }, s.startingBankroll) { vm.updateSettings(s.copy(startingBankroll = it)) }
        Text(
            "Lifetime: ${vm.stats.hands} hands, ${vm.stats.decisions} decisions, ${vm.stats.accuracyPercent}% matched basic strategy, net ${signedMoney(vm.stats.net)}.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var confirmReset by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { confirmReset = true }, enabled = vm.round.phase == Phase.BETTING || vm.round.phase == Phase.SETTLED) {
            Text("Reset bankroll and statistics")
        }
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("Start over?") },
                text = { Text("Your bankroll returns to ${money(s.startingBankroll)} and the statistics go back to zero.") },
                confirmButton = { TextButton(onClick = { vm.resetBankroll(); confirmReset = false }) { Text("Reset") } },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            )
        }

        SectionHeader("About")
        Text("Free, open source, no ads, no tracking. Built to teach the game, not to take your money.", fontSize = 13.sp)
        val uriHandler = LocalUriHandler.current
        TextButton(onClick = { uriHandler.openUri(REPO_URL) }) { Text("Source code and licence (MIT) on GitHub") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 6.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, color = Gold)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChipRow(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (label, value) ->
                FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}
