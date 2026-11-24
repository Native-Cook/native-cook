package com.ngocdevv.nativecook.blocks.navigation.louverthemeswitch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun LouverThemeSwitchDemo() {
    var prefs by remember { mutableStateOf(AlmanacPrefs(weekNumbers = true, declined = false, travel = true)) }
    val setPref: (AlmanacPref, Boolean) -> Unit = { key, value ->
        prefs = when (key) {
            AlmanacPref.WeekNumbers -> prefs.copy(weekNumbers = value)
            AlmanacPref.Declined -> prefs.copy(declined = value)
            AlmanacPref.Travel -> prefs.copy(travel = value)
        }
    }
    LouverThemeSwitch(
        autoPlay = true,
        contentKey = "${prefs.weekNumbers}-${prefs.declined}-${prefs.travel}",
    ) { screen ->
        AlmanacSettings(screen, prefs, setPref)
    }
}

@Preview
@Composable
private fun LouverThemeSwitchDemoPreview() {
    LouverThemeSwitchDemo()
}
