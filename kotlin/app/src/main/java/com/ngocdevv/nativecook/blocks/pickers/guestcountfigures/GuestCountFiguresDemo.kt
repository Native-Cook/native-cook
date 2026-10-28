package com.ngocdevv.nativecook.blocks.pickers.guestcountfigures

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The guests step on the whole screen, adding and removing its little people by itself. */
@Composable
fun GuestCountFiguresDemo() {
    GuestCountFigures(autoPlay = true)
}

@Preview
@Composable
private fun GuestCountFiguresDemoPreview() {
    GuestCountFiguresDemo()
}
