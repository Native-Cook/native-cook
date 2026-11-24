package com.ngocdevv.nativecook.blocks.modals.morphingcarddialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MorphingCardDialogDemo() {
    MorphingCardDialog(autoPlay = true)
}

@Preview
@Composable
private fun MorphingCardDialogDemoPreview() {
    MorphingCardDialogDemo()
}
