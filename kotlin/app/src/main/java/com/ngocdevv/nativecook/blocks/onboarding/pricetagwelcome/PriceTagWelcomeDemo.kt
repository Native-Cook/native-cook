package com.ngocdevv.nativecook.blocks.onboarding.pricetagwelcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun PriceTagWelcomeDemo() {
    PriceTagWelcome(autoPlay = true)
}

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun PriceTagWelcomeDemoPreview() {
    PriceTagWelcomeDemo()
}
