package com.ngocdevv.nativecook.blocks.forms.priceglyphscatterslider

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The budget card on the whole screen, dragging its own thumb up and down. */
@Composable
fun PriceGlyphScatterSliderDemo() {
    PriceGlyphScatterSlider(autoPlay = true)
}

@Preview
@Composable
private fun PriceGlyphScatterSliderDemoPreview() {
    PriceGlyphScatterSliderDemo()
}
