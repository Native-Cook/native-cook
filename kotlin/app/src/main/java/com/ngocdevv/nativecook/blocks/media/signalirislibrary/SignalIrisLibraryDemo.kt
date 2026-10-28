package com.ngocdevv.nativecook.blocks.media.signalirislibrary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SignalIrisLibraryDemo() {
    SignalIrisLibrary(modifier = Modifier, autoPlay = true)
}

@Preview
@Composable
private fun SignalIrisLibraryDemoPreview() {
    SignalIrisLibraryDemo()
}
