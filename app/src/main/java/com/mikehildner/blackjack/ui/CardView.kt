package com.mikehildner.blackjack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikehildner.blackjack.engine.Card
import com.mikehildner.blackjack.ui.theme.CardBack
import com.mikehildner.blackjack.ui.theme.CardBlack
import com.mikehildner.blackjack.ui.theme.CardRed
import com.mikehildner.blackjack.ui.theme.CardWhite

/** A single playing card. Pass null for a face-down card. */
@Composable
fun PlayingCard(card: Card?, modifier: Modifier = Modifier, width: Dp = 56.dp) {
    val shape = RoundedCornerShape(width / 9)
    Surface(
        modifier = modifier
            .width(width)
            .aspectRatio(0.7f)
            .clip(shape),
        shape = shape,
        color = if (card == null) CardBack else CardWhite,
        border = BorderStroke(1.dp, Color(0x33000000)),
        shadowElevation = 3.dp,
    ) {
        if (card == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(width / 10)
                    .border(2.dp, Color(0x66FFFFFF), RoundedCornerShape(width / 14)),
            )
        } else {
            val color = if (card.suit.isRed) CardRed else CardBlack
            val rankSize = if (card.rank == com.mikehildner.blackjack.engine.Rank.TEN) width.value * 0.3f else width.value * 0.36f
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.padding(start = width / 10, top = width / 16)) {
                    Text(
                        card.rank.symbol,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = rankSize.sp,
                        lineHeight = rankSize.sp,
                    )
                    Text(
                        card.suit.symbol,
                        color = color,
                        fontSize = (width.value * 0.3f).sp,
                        lineHeight = (width.value * 0.3f).sp,
                        modifier = Modifier.offset(y = (-width.value * 0.05f).dp),
                    )
                }
                Text(
                    card.suit.symbol,
                    color = color,
                    fontSize = (width.value * 0.55f).sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = width / 10, bottom = width / 12),
                )
            }
        }
    }
}

/**
 * A fan of overlapping cards. [hidden] marks indexes to show face down
 * (the dealer hole card).
 */
@Composable
fun CardFan(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    hiddenIndex: Int = -1,
    overlap: Dp = width * 0.52f,
) {
    val total = if (cards.isEmpty()) width else width + overlap * (cards.size - 1)
    Box(modifier.width(total).height(width / 0.7f)) {
        cards.forEachIndexed { i, card ->
            PlayingCard(
                card = if (i == hiddenIndex) null else card,
                width = width,
                modifier = Modifier.offset(x = overlap * i),
            )
        }
    }
}

/** A small coloured pill used for totals, bets and results. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.White) {
    Text(
        text,
        color = textColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
