package com.ngocdevv.nativecook.blocks.lists.snackstopvending

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SnackStopVendingDemo() {
    SnackStopVending(autoPlay = true)
}

@Preview
@Composable
private fun SnackStopVendingDemoPreview() {
    SnackStopVendingDemo()
}
