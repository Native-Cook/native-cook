package com.ngocdevv.nativecook.blocks.forms.unfoldingsignaturepad

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The block fills the screen and keeps the status bar, so it is shown without a stage. */
@Composable
fun UnfoldingSignaturePadDemo() {
    UnfoldingSignaturePad(autoPlay = true)
}

@Preview
@Composable
private fun UnfoldingSignaturePadDemoPreview() {
    UnfoldingSignaturePadDemo()
}
