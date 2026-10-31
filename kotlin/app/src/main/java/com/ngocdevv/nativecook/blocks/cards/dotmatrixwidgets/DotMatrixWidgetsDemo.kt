package com.ngocdevv.nativecook.blocks.cards.dotmatrixwidgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DotMatrixWidgetsDemo() {
    DotMatrixWidgets(autoPlay = true)
}

@Preview
@Composable
private fun DotMatrixWidgetsDemoPreview() {
    DotMatrixWidgetsDemo()
}
