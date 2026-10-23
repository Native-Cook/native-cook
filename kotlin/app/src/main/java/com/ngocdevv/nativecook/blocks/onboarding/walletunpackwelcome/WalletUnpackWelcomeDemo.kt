package com.ngocdevv.nativecook.blocks.onboarding.walletunpackwelcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. Its screen
 * is light, so the block itself turns the bars' icons dark while it is open.
 */
@Composable
fun WalletUnpackWelcomeDemo() {
    WalletUnpackWelcome(autoPlay = true)
}

@Preview
@Composable
private fun WalletUnpackWelcomeDemoPreview() {
    WalletUnpackWelcomeDemo()
}
