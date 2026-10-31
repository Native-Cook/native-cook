package com.ngocdevv.nativecook.blocks.onboarding.nightwindowsleep

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun NightWindowSleepDemo() {
    NightWindowSleep(autoPlay = true)
}

@Preview
@Composable
private fun NightWindowSleepDemoPreview() {
    NightWindowSleepDemo()
}
