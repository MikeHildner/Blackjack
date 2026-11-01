package com.mikehildner.blackjack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mikehildner.blackjack.BlackjackViewModel
import com.mikehildner.blackjack.engine.Action
import com.mikehildner.blackjack.engine.BasicStrategy
import com.mikehildner.blackjack.engine.Card
import com.mikehildner.blackjack.engine.CellKey
import com.mikehildner.blackjack.engine.CellRecord
import com.mikehildner.blackjack.engine.ChartCode
import com.mikehildner.blackjack.engine.FreeBetStrategy
import com.mikehildner.blackjack.engine.Rank
import com.mikehildner.blackjack.engine.Rules
import com.mikehildner.blackjack.engine.Situation
import com.mikehildner.blackjack.engine.Suit
import com.mikehildner.blackjack.engine.Variant
import com.mikehildner.blackjack.ui.theme.Gold
import java.util.Locale

private val HitColor = Color(0xFF2E7D32)
private val StandColor = Color(0xFFC62828)
private val DoubleColor = Color(0xFFF9A825)
private val SplitColor = Color(0xFF1565C0)
private val SurrenderColor = Color(0xFFBDBDBD)

private val FreeDoubleColor = Color(0xFFE65100)
private val FreeSplitColor = Color(0xFF6A1B9A)

private val UntestedColor = Color(0xFF37474F)
private val AccuracyLow = Color(0xFFC62828)
private val AccuracyMid = Color(0xFFF9A825)
private val AccuracyHigh = Color(0xFF2E7D32)

private fun ChartCode.color(): Color = when (this) {
    ChartCode.HIT -> HitColor
    ChartCode.STAND -> StandColor
    ChartCode.DOUBLE_HIT, ChartCode.DOUBLE_STAND -> DoubleColor
    ChartCode.SPLIT -> SplitColor
    ChartCode.SURRENDER_HIT, ChartCode.SURRENDER_STAND, ChartCode.SURRENDER_SPLIT -> SurrenderColor
    ChartCode.FREE_DOUBLE -> FreeDoubleColor
    ChartCode.FREE_SPLIT -> FreeSplitColor
}

private fun ChartCode.textColor(): Color = when (this) {
    ChartCode.DOUBLE_HIT, ChartCode.DOUBLE_STAND,
    ChartCode.SURRENDER_HIT, ChartCode.SURRENDER_STAND, ChartCode.SURRENDER_SPLIT -> Color.Black
    else -> Color.White
}

/** Red at 60% or below, amber at 80%, green at 100%. */
private fun accuracyColor(accuracy: Double): Color = when {
    accuracy <= 0.6 -> AccuracyLow
    accuracy <= 0.8 -> lerp(AccuracyLow, AccuracyMid, ((accuracy - 0.6) / 0.2).toFloat())
    else -> lerp(AccuracyMid, AccuracyHigh, ((accuracy - 0.8) / 0.2).toFloat())
}

/**
 * The full basic strategy chart for the table rules currently in play, or a
 * heatmap of your own accuracy on the same grid. If a hand is in progress,
 * the matching cell is enlarged and ringed in white, its row and column are
 * dimmed as a crosshair, and the chart scrolls to it. Tap any cell for its
 * explanation and record, and to practise it.
 */
@Composable
fun StrategyChartScreen(vm: BlackjackViewModel, modifier: Modifier = Modifier) {
    val rules = vm.rules
    val current = vm.round.currentHand?.let { hand -> vm.round.dealerUpCard?.let { Situation.of(hand, it) } }
    val freeBetGame = rules.variant == Variant.FREE_BET
    // Free Bet: a hand riding on house money plays differently, so the chart has two faces.
    var freeBetHand by rememberSaveable { mutableStateOf(false) }
    val showFreeBetHand = freeBetGame && (if (current != null) current.hasFreeBet else freeBetHand)
    var heatmap by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CellKey?>(null) }
    vm.weakSpotsVersion // recompose the heatmap when the record changes

    // Free Bet cells are computed from EV; cache the whole grid per rules so scrolling stays smooth.
    val cells = remember(rules, showFreeBetHand) {
        BasicStrategy.Section.entries.associateWith { section ->
            BasicStrategy.rows(section, rules.variant).associateWith { row ->
                BasicStrategy.dealerColumns.associateWith { up -> BasicStrategy.chartCell(section, row, up, rules, showFreeBetHand) }
            }
        }
    }

    // When a hand is in play, scroll the chart so its cell sits mid-screen. The cell reports its
    // position within the scrolled content; the effect fires once per new highlight.
    val scrollState = rememberScrollState()
    val highlight = current?.let { BasicStrategy.cellFor(it, rules.variant) }
    var viewportTop by remember { mutableStateOf(0f) }
    var viewportHeight by remember { mutableStateOf(0) }
    var highlightContentY by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(highlight, highlightContentY) {
        val y = highlightContentY ?: return@LaunchedEffect
        if (highlight != null) scrollState.animateScrollTo((y - viewportHeight / 2f).toInt().coerceAtLeast(0))
    }

    Column(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                viewportTop = it.positionInRoot().y
                viewportHeight = it.size.height
            }
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (freeBetGame) "Free Bet strategy" else "Basic strategy", style = MaterialTheme.typography.headlineSmall)
        Text(
            "For ${rules.summary()}. House edge about ${String.format(Locale.US, "%.2f", rules.houseEdgePercent)}% with perfect play. " +
                "Rows are your hand, columns are the dealer up card. The chart changes when you change the table rules." +
                if (freeBetGame) " Every Free Bet cell is computed from expected value for these exact rules." else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !heatmap, onClick = { heatmap = false }, label = { Text("Strategy") })
            FilterChip(selected = heatmap, onClick = { heatmap = true }, label = { Text("My accuracy") })
        }
        if (freeBetGame) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !showFreeBetHand, onClick = { freeBetHand = false }, label = { Text("Real-money hand") }, enabled = current == null)
                FilterChip(selected = showFreeBetHand, onClick = { freeBetHand = true }, label = { Text("Hand on a free bet") }, enabled = current == null)
            }
            Text(
                if (showFreeBetHand) "A free-bet hand (the second hand of a free split) has no money at risk, so a push wins nothing. It plays harder."
                else "Your original hand, or the first hand of a split: real money at risk.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (heatmap) HeatmapLegend(vm.weakSpots.totalAttempts) else Legend(freeBetGame)
        Text(
            if (highlight != null) "Your hand is outlined in white below. Tap any cell for details." else "Tap any cell for its explanation, your record, and a practice button.",
            fontSize = 12.sp,
            color = if (highlight != null) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BasicStrategy.Section.entries.forEach { section ->
            ChartTable(
                section, cells.getValue(section), rules.variant, showFreeBetHand, highlight, heatmap,
                recordOf = { vm.weakSpots.recordOf(it) },
                onTap = { selected = it },
                onHighlightPositioned = { rootY -> highlightContentY = rootY - viewportTop + scrollState.value },
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    selected?.let { key ->
        CellDialog(
            key = key,
            code = cells[key.section]?.get(key.row)?.get(key.dealerUp) ?: BasicStrategy.chartCell(key, rules),
            record = vm.weakSpots.recordOf(key),
            rules = rules,
            canPractice = !key.freeBetHand && !(key.section == BasicStrategy.Section.HARD && key.row == 20),
            onPractice = { vm.practiceCell(key); selected = null },
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun Legend(freeBet: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        LegendItem("H Hit", HitColor)
        LegendItem("S Stand", StandColor)
        LegendItem("D Double", DoubleColor)
        LegendItem("P Split", SplitColor)
        LegendItem("R Surrender", SurrenderColor)
    }
    if (freeBet) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LegendItem("FD Free double", FreeDoubleColor)
            LegendItem("FP Free split", FreeSplitColor)
        }
    }
    Text(
        "D = double, else hit. Ds = double, else stand. R = surrender, else hit. Rs = surrender, else stand. Rp = surrender, else split.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HeatmapLegend(totalAttempts: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        LegendItem("100%", AccuracyHigh)
        LegendItem("80%", AccuracyMid)
        LegendItem("60% or less", AccuracyLow)
        LegendItem("Untested", UntestedColor)
    }
    Text(
        if (totalAttempts == 0) "Nothing recorded yet. Every decision the coach grades, at the table or in practice, colours a cell here."
        else "$totalAttempts graded decisions so far. The small number in a cell is how many times you have faced it; recent misses weigh most in practice.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LegendItem(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(text, fontSize = 11.sp)
    }
}

@Composable
private fun ChartTable(
    section: BasicStrategy.Section,
    cells: Map<Int, Map<Int, ChartCode>>,
    variant: Variant,
    freeBetHand: Boolean,
    highlight: CellKey?,
    heatmap: Boolean,
    recordOf: (CellKey) -> CellRecord?,
    onTap: (CellKey) -> Unit,
    onHighlightPositioned: (Float) -> Unit,
) {
    val labelWidth = 40.dp
    val inSection = highlight?.section == section
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.width(labelWidth))
            BasicStrategy.dealerColumns.forEach { up ->
                val hit = inSection && highlight?.dealerUp == up
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(BasicStrategy.columnLabel(up), fontSize = if (hit) 14.sp else 12.sp, fontWeight = FontWeight.Bold, color = if (hit) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        BasicStrategy.rows(section, variant).forEach { row ->
            val rowHit = inSection && highlight?.row == row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(labelWidth), contentAlignment = Alignment.Center) {
                    Text(BasicStrategy.rowLabel(section, row, variant), fontSize = if (rowHit) 14.sp else 12.sp, fontWeight = FontWeight.Bold, color = if (rowHit) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicStrategy.dealerColumns.forEach { up ->
                    val key = CellKey(variant, section, row, up, freeBetHand)
                    val code = cells.getValue(row).getValue(up)
                    val record = recordOf(key)
                    val isHighlight = rowHit && highlight?.dealerUp == up
                    // Cells in the highlighted row and column are dimmed slightly so the crosshair reads at a glance.
                    val inCrosshair = inSection && !isHighlight && (rowHit || highlight?.dealerUp == up)
                    val fill = if (heatmap) (record?.let { accuracyColor(it.accuracy) } ?: UntestedColor) else code.color()
                    val ink = if (heatmap) Color.White else code.textColor()
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .then(
                                if (isHighlight) Modifier
                                    .zIndex(1f)
                                    .scale(1.25f)
                                    .shadow(8.dp, RoundedCornerShape(3.dp))
                                    .onGloballyPositioned { onHighlightPositioned(it.positionInRoot().y + it.size.height / 2f) }
                                else Modifier,
                            )
                            .clip(RoundedCornerShape(3.dp))
                            .background(fill)
                            .clickable { onTap(key) }
                            .then(
                                when {
                                    // White ring with a dark inner edge: visible on every cell colour, including amber.
                                    isHighlight -> Modifier.border(3.dp, Color.White, RoundedCornerShape(3.dp)).padding(3.dp).border(1.dp, Color.Black, RoundedCornerShape(1.dp))
                                    inCrosshair -> Modifier.background(Color(0x33000000))
                                    else -> Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(code.code, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ink)
                        if (heatmap && record != null) {
                            Text(
                                record.attempts.toString(),
                                fontSize = 7.sp,
                                lineHeight = 7.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 2.dp, top = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Everything about one cell: the play, why, your record, and a way to drill it. */
@Composable
private fun CellDialog(
    key: CellKey,
    code: ChartCode,
    record: CellRecord?,
    rules: Rules,
    canPractice: Boolean,
    onPractice: () -> Unit,
    onDismiss: () -> Unit,
) {
    val explanation = remember(key, rules) {
        val s = key.situation()
        val hand = FreeBetStrategy.representativeHand(s)
        val up = Card(if (key.dealerUp == 11) Rank.ACE else if (key.dealerUp == 10) Rank.TEN else Rank.entries.first { it.value == key.dealerUp }, Suit.SPADES)
        val available = buildSet {
            add(Action.HIT); add(Action.STAND); add(Action.DOUBLE)
            if (s.pairOf != null) add(Action.SPLIT)
            if (rules.lateSurrender && !s.fromSplit) add(Action.SURRENDER)
        }
        BasicStrategy.recommend(hand, up, rules, available)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(key.label()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chart: ${code.code} = ${explanation.action.label}${if (code.isFree) " (house money)" else ""}", fontWeight = FontWeight.Bold)
                Text(explanation.reason, fontSize = 14.sp)
                Text(
                    when {
                        record == null -> "You have not been graded on this cell yet."
                        else -> "Your record: ${record.attempts - record.misses} right out of ${record.attempts} (${(record.accuracy * 100).toInt()}%)."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (canPractice) TextButton(onClick = onPractice) { Text("Practise this cell") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
