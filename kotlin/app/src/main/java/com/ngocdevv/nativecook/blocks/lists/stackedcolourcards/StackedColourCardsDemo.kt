package com.ngocdevv.nativecook.blocks.lists.stackedcolourcards

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun StackedColourCardsDemo() {
    StackedColourCards(autoPlay = true)
}

@Preview
@Composable
private fun StackedColourCardsDemoPreview() {
    StackedColourCardsDemo()
}
