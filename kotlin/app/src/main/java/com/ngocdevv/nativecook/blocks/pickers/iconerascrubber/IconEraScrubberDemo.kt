package com.ngocdevv.nativecook.blocks.pickers.iconerascrubber

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Full-screen demo entry point; the recording uses the deterministic ten-second loop. */
@Composable
fun IconEraScrubberDemo() {
    Box(
        Modifier
            .fillMaxSize()
            .background(IconEraScrubberTheme.Background),
    ) {
        IconEraScrubber(autoPlay = true)
    }
}
