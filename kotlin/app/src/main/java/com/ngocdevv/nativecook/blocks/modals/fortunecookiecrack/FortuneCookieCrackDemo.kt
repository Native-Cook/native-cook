package com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and draws under the system bars, keeping its content clear of them
 * itself. The gallery's activity is edge to edge with light icons, which the dark screen needs.
 */
@Composable
fun FortuneCookieCrackDemo() {
    FortuneCookieCrack(autoPlay = true)
}

@Preview
@Composable
private fun FortuneCookieCrackDemoPreview() {
    FortuneCookieCrackDemo()
}
