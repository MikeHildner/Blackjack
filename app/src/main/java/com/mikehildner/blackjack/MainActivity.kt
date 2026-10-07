package com.mikehildner.blackjack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mikehildner.blackjack.ui.BlackjackApp
import com.mikehildner.blackjack.ui.theme.BlackjackTheme

/**
 * The only Activity. Everything on screen is Jetpack Compose, driven by
 * [BlackjackViewModel], which in turn only talks to the pure-Kotlin engine.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlackjackTheme {
                BlackjackApp()
            }
        }
    }
}
