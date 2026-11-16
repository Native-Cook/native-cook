package com.ngocdevv.nativecook.blocks.progress.breathvolumelungs

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The loop opens at the lungs' rest, exhales to empty, shows "Well done" at 5.5 L, then refills (8.5 s). */
@Composable
fun BreathVolumeLungsDemo() {
    BreathVolumeLungs(autoPlay = true)
}

@Preview
@Composable
private fun BreathVolumeLungsDemoPreview() {
    BreathVolumeLungsDemo()
}
