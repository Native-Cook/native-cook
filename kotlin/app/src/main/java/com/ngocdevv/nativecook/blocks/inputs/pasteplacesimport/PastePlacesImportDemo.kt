package com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background

@Composable
fun PastePlacesImportDemo() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF6F6F6)),
    ) {
        PastePlacesImport(autoPlay = true)
    }
}

@Preview
@Composable
private fun PastePlacesImportDemoPreview() {
    PastePlacesImportDemo()
}
