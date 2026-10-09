package com.mikehildner.blackjack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikehildner.blackjack.BlackjackViewModel
import com.mikehildner.blackjack.engine.Card
import com.mikehildner.blackjack.engine.CountTracker
import com.mikehildner.blackjack.engine.HiLo
import com.mikehildner.blackjack.engine.Shoe
import com.mikehildner.blackjack.ui.theme.Bad
import com.mikehildner.blackjack.ui.theme.Gold
import com.mikehildner.blackjack.ui.theme.Good
import kotlinx.coroutines.delay

private val Speeds = listOf("Slow" to 2000L, "Medium" to 1200L, "Fast" to 700L, "Pro" to 400L, "Tap" to 0L)
private val Sizes = listOf(13, 26, 52)

private data class Drill(val cards: List<Card>, val index: Int = 0) {
    val finished: Boolean get() = index >= cards.size
    val current: Card? get() = cards.getOrNull(index)
    val answer: Int get() = cards.sumOf { HiLo.value(it) }
}

/**
 * Card counting trainer: a quick Hi-Lo primer, the live count at your table,
 * and a drill where cards flash past and you keep the running count.
 */
@Composable
fun CountTrainerScreen(vm: BlackjackViewModel, modifier: Modifier = Modifier) {
    var speedIndex by rememberSaveable { mutableIntStateOf(1) }
    var size by rememberSaveable { mutableIntStateOf(26) }
    var showValues by rememberSaveable { mutableStateOf(true) }
    var drill by remember { mutableStateOf<Drill?>(null) }
    var answer by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<Pair<Int, Int>?>(null) } // your answer to the real count

    val speedMs = Speeds[speedIndex].second
    LaunchedEffect(drill, speedMs) {
        val d = drill ?: return@LaunchedEffect
        if (!d.finished && speedMs > 0) {
            delay(speedMs)
            drill = d.copy(index = d.index + 1)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Card counting", style = MaterialTheme.typography.headlineSmall)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Hi-Lo in one breath", style = MaterialTheme.typography.titleMedium)
                Text("2, 3, 4, 5, 6 leave the shoe: count +1.  7, 8, 9: count 0.  10, J, Q, K, A: count -1.")
                Text(
                    "A high running count means the cards left are rich in tens and aces, which favours you: more blackjacks at 3:2, " +
                        "better doubles, and a dealer who busts more often. Divide by the decks remaining to get the true count, and bet more when it is high.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("At your table right now", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Running ${if (vm.runningCount > 0) "+" else ""}${vm.runningCount}", fontWeight = FontWeight.Bold)
                    Text("True ${CountTracker.format(vm.trueCount)}", fontWeight = FontWeight.Bold)
                    Text("${vm.cardsSeen} cards seen")
                }
                if (vm.rules.continuousShuffle) {
                    Text(
                        "Your table uses a continuous shuffler, so this count resets every hand. Practise here, but it will not pay at that table.",
                        fontSize = 13.sp,
                        color = Bad,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Show the count on the table screen")
                    Switch(checked = vm.settings.showCount, onCheckedChange = { vm.updateSettings(vm.settings.copy(showCount = it)) })
                }
            }
        }

        Text("Drill", style = MaterialTheme.typography.titleMedium)
        Text("Cards appear one at a time. Keep the running count in your head, then enter it at the end. A full deck always ends at 0.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Speeds.forEachIndexed { i, (label, _) ->
                FilterChip(selected = speedIndex == i, onClick = { speedIndex = i }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Sizes.forEach { n ->
                FilterChip(selected = size == n, onClick = { size = n }, label = { Text(if (n == 52) "Full deck" else "$n cards") })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Show each card's Hi-Lo value")
            Switch(checked = showValues, onCheckedChange = { showValues = it })
        }

        val d = drill
        when {
            d == null -> Button(
                onClick = {
                    val shoe = Shoe(1)
                    drill = Drill(List(size) { shoe.draw() })
                    answer = ""
                    result = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start drill") }

            !d.finished -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Card ${d.index + 1} of ${d.cards.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PlayingCard(d.current, width = 120.dp)
                    if (showValues) {
                        val v = HiLo.value(d.current!!)
                        Text(
                            if (v > 0) "+1" else if (v < 0) "-1" else "0",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (v > 0) Good else if (v < 0) Bad else Gold,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (speedMs == 0L) Button(onClick = { drill = d.copy(index = d.index + 1) }) { Text("Next card") }
                        OutlinedButton(onClick = { drill = null }) { Text("Stop") }
                    }
                }
            }

            else -> {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What is the running count?", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it.filter { c -> c.isDigit() || c == '-' || c == '+' } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = result == null,
                    )
                    val r = result
                    if (r == null) {
                        Button(
                            onClick = { answer.removePrefix("+").toIntOrNull()?.let { result = it to d.answer } },
                            enabled = answer.removePrefix("+").toIntOrNull() != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Check") }
                    } else {
                        val correct = r.first == r.second
                        Text(
                            if (correct) "Correct! The running count was ${signed(r.second)}."
                            else "Not quite. You said ${signed(r.first)}; the running count was ${signed(r.second)}.",
                            color = if (correct) Good else Bad,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(d.cards.joinToString(" ") { it.toString() }, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                        Button(
                            onClick = {
                                val shoe = Shoe(1)
                                drill = Drill(List(size) { shoe.draw() })
                                answer = ""
                                result = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Again") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun signed(n: Int) = if (n > 0) "+$n" else n.toString()
