package com.ngocdevv.nativecook.blocks.forms.papertapequantity

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The block fills the screen and keeps the status bar, so it is shown without a stage. */
@Composable
fun PaperTapeQuantityDemo() {
    PaperTapeQuantity(autoPlay = true)
}

@Preview
@Composable
private fun PaperTapeQuantityDemoPreview() {
    PaperTapeQuantityDemo()
}
