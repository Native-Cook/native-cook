package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The paywall story on the whole screen, playing by itself, with the status bar on show (as in React Native). */
@Composable
fun ConfettiGiftOfferDemo() {
    ConfettiGiftOffer(autoPlay = true)
}

@Preview
@Composable
private fun ConfettiGiftOfferDemoPreview() {
    ConfettiGiftOfferDemo()
}
