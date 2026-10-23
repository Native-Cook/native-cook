package com.ngocdevv.nativecook.blocks.cards.cityeventhero

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun CityEventHeroDemo() {
    CityEventHero(autoPlay = true)
}

@Preview
@Composable
private fun CityEventHeroDemoPreview() {
    CityEventHeroDemo()
}
