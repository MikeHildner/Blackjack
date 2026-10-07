package com.mikehildner.blackjack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Table colours. The app is always "dark" because a casino table is green felt.
val Felt = Color(0xFF0B5D2E)
val FeltDark = Color(0xFF07401F)
val FeltLight = Color(0xFF14773D)
val Gold = Color(0xFFE2B714)
val CardWhite = Color(0xFFFDFBF4)
val CardRed = Color(0xFFC62828)
val CardBlack = Color(0xFF1B1B1B)
val CardBack = Color(0xFF1E3A8A)
val Good = Color(0xFF66BB6A)
val Bad = Color(0xFFEF5350)

private val Scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF8A6D00),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF80CBC4),
    onSecondary = Color.Black,
    secondaryContainer = FeltLight,
    onSecondaryContainer = Color.White,
    background = FeltDark,
    onBackground = Color.White,
    surface = Felt,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF0E6B36),
    onSurfaceVariant = Color(0xFFDDEEDD),
    surfaceContainer = Color(0xFF0A5229),
    surfaceContainerHigh = Color(0xFF0E6B36),
    surfaceContainerHighest = FeltLight,
    outline = Color(0xFF7FB591),
    error = Bad,
    onError = Color.Black,
)

@Composable
fun BlackjackTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
