package com.mikehildner.blackjack.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikehildner.blackjack.BlackjackViewModel

enum class Screen(val label: String, val icon: ImageVector) {
    TABLE("Table", Icons.Default.Style),
    CHART("Chart", Icons.Default.GridOn),
    COUNT("Count", Icons.Default.Calculate),
    LEARN("Learn", Icons.AutoMirrored.Filled.MenuBook),
    SETTINGS("Settings", Icons.Default.Settings),
}

/**
 * Root of the UI: a bottom navigation bar and one screen at a time.
 * Navigation is a plain enum in state; no navigation library needed for five tabs.
 */
@Composable
fun BlackjackApp(vm: BlackjackViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.TABLE) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = screen == s,
                        onClick = { screen = s },
                        icon = { Icon(s.icon, contentDescription = s.label) },
                        label = { Text(s.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (screen) {
            Screen.TABLE -> TableScreen(vm, modifier)
            Screen.CHART -> StrategyChartScreen(vm, modifier)
            Screen.COUNT -> CountTrainerScreen(vm, modifier)
            Screen.LEARN -> LearnScreen(vm, modifier)
            Screen.SETTINGS -> SettingsScreen(vm, modifier)
        }
    }
}
