package com.ngocdevv.nativecook.blocks.inputs.oracleglassgrid

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Sibyl's "ask the cards" screen on the whole screen, opening a card into the question field by itself. */
@Composable
fun OracleGlassGridDemo() {
    OracleGlassGrid(autoPlay = true)
}

@Preview
@Composable
private fun OracleGlassGridDemoPreview() {
    OracleGlassGridDemo()
}
