package com.mikehildner.blackjack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

/** Set to false (Settings > Reduce animations) to make every card appear instantly. */
val LocalAnimationsEnabled = compositionLocalOf { true }

/**
 * A card that can be face down and flips over when [faceUp] becomes true.
 *
 * The flip is a rotation around the Y axis. For the first half of the turn we
 * draw the back; past 90 degrees we draw the face, itself rotated 180 degrees
 * so that it is not mirrored.
 */
@Composable
fun FlipCard(card: Card, faceUp: Boolean, modifier: Modifier = Modifier, width: Dp = 56.dp) {
    val animate = LocalAnimationsEnabled.current
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 180f else 0f,
        animationSpec = if (animate) tween(durationMillis = 450) else snap(),
        label = "flip",
    )
    val showingFace = rotation > 90f
    Box(
        modifier.graphicsLayer {
            rotationY = rotation
            cameraDistance = 12f * density
        },
    ) {
        if (showingFace) {
            PlayingCard(card, Modifier.graphicsLayer { rotationY = 180f }, width)
        } else {
            PlayingCard(null, width = width)
        }
    }
}

/**
 * A fan of overlapping cards. [hiddenIndex] marks the one card to show face
 * down (the dealer hole card); it flips when the index changes to -1.
 *
 * New cards fade and slide in from [fromTop] (dealer) or the bottom (player).
 * Cards already on the table keep their state and do not re-animate, which
 * is what the `key(i, card)` and the remembered transition state are for.
 */
@Composable
fun CardFan(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    hiddenIndex: Int = -1,
    overlap: Dp = width * 0.52f,
    fromTop: Boolean = false,
) {
    val animate = LocalAnimationsEnabled.current
    val total = if (cards.isEmpty()) width else width + overlap * (cards.size - 1)
    Box(modifier.width(total).height(width / 0.7f)) {
        cards.forEachIndexed { i, card ->
            key(i, card) {
                // Starts invisible on first composition and immediately targets visible,
                // so the enter transition plays exactly once per card.
                val visible = remember { MutableTransitionState(!animate) }.apply { targetState = true }
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(320)) { height -> if (fromTop) -height else height },
                    modifier = Modifier.offset(x = overlap * i),
                ) {
                    FlipCard(card, faceUp = i != hiddenIndex, width = width)
                }
            }
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
