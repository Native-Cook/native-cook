package com.ngocdevv.nativecook.blocks.forms.timetargetdial

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.TimeTargetScreen
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.TimeTargetTheme

/**
 * The original app's root content: `TimeTargetTheme { TimeTargetScreen() }`.
 *
 * The original activity was edge to edge with `SystemBarStyle.dark` on both bars (light icons),
 * which is what the gallery's activity already uses, so the block changes nothing on the window.
 */
@Composable
fun TimeTargetDialDemo() {
    TimeTargetTheme {
        TimeTargetScreen()
    }
}

@Preview
@Composable
private fun TimeTargetDialDemoPreview() {
    TimeTargetDialDemo()
}
