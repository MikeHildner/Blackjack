package com.mikehildner.blackjack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikehildner.blackjack.BlackjackViewModel
import com.mikehildner.blackjack.engine.BasicStrategy
import com.mikehildner.blackjack.engine.ChartCode
import com.mikehildner.blackjack.engine.Rules
import com.mikehildner.blackjack.engine.Situation
import com.mikehildner.blackjack.ui.theme.Gold
import java.util.Locale

private val HitColor = Color(0xFF2E7D32)
private val StandColor = Color(0xFFC62828)
private val DoubleColor = Color(0xFFF9A825)
private val SplitColor = Color(0xFF1565C0)
private val SurrenderColor = Color(0xFFBDBDBD)

private fun ChartCode.color(): Color = when (this) {
    ChartCode.HIT -> HitColor
    ChartCode.STAND -> StandColor
    ChartCode.DOUBLE_HIT, ChartCode.DOUBLE_STAND -> DoubleColor
    ChartCode.SPLIT -> SplitColor
    ChartCode.SURRENDER_HIT, ChartCode.SURRENDER_STAND, ChartCode.SURRENDER_SPLIT -> SurrenderColor
}

private fun ChartCode.textColor(): Color = when (this) {
    ChartCode.DOUBLE_HIT, ChartCode.DOUBLE_STAND,
    ChartCode.SURRENDER_HIT, ChartCode.SURRENDER_STAND, ChartCode.SURRENDER_SPLIT -> Color.Black
    else -> Color.White
}

/**
 * The full basic strategy chart for the table rules currently in play.
 * If a hand is in progress, the matching cell is outlined in gold.
 */
@Composable
fun StrategyChartScreen(vm: BlackjackViewModel, modifier: Modifier = Modifier) {
    val rules = vm.rules
    val current = vm.round.currentHand?.let { hand -> vm.round.dealerUpCard?.let { Situation.of(hand, it) } }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Basic strategy", style = MaterialTheme.typography.headlineSmall)
        Text(
            "For ${rules.summary()}. House edge about ${String.format(Locale.US, "%.2f", rules.houseEdgePercent)}% with perfect play. " +
                "Rows are your hand, columns are the dealer up card. The chart changes when you change the table rules.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Legend()
        BasicStrategy.Section.entries.forEach { section ->
            ChartTable(section, rules, highlight = current?.let { chartPosition(it) })
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Which (section, row) a live situation lands on. */
private fun chartPosition(s: Situation): Triple<BasicStrategy.Section, Int, Int> = when {
    s.pairOf != null && s.twoCards -> Triple(BasicStrategy.Section.PAIRS, s.pairOf!!, s.dealerUp)
    s.soft -> Triple(BasicStrategy.Section.SOFT, s.total.coerceIn(13, 20), s.dealerUp)
    else -> Triple(BasicStrategy.Section.HARD, s.total.coerceIn(5, 17), s.dealerUp)
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        LegendItem("H Hit", HitColor)
        LegendItem("S Stand", StandColor)
        LegendItem("D Double", DoubleColor)
        LegendItem("P Split", SplitColor)
        LegendItem("R Surrender", SurrenderColor)
    }
    Text(
        "D = double, else hit. Ds = double, else stand. R = surrender, else hit. Rs = surrender, else stand. Rp = surrender, else split.",
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
private fun ChartTable(section: BasicStrategy.Section, rules: Rules, highlight: Triple<BasicStrategy.Section, Int, Int>?) {
    val labelWidth = 40.dp
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.width(labelWidth))
            BasicStrategy.dealerColumns.forEach { up ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(BasicStrategy.columnLabel(up), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        BasicStrategy.rows(section).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(labelWidth), contentAlignment = Alignment.Center) {
                    Text(BasicStrategy.rowLabel(section, row), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                BasicStrategy.dealerColumns.forEach { up ->
                    val code = BasicStrategy.chartCell(section, row, up, rules)
                    val isHighlight = highlight != null && highlight.first == section && highlight.second == row && highlight.third == up
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(code.color())
                            .then(if (isHighlight) Modifier.border(3.dp, Gold, RoundedCornerShape(3.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(code.code, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = code.textColor())
                    }
                }
            }
        }
    }
}
