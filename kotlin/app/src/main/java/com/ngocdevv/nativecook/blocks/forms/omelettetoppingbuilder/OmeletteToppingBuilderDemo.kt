package com.ngocdevv.nativecook.blocks.forms.omelettetoppingbuilder

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Step 2 of a breakfast order on the whole screen, folding three fillings into the omelette by itself. */
@Composable
fun OmeletteToppingBuilderDemo() {
    OmeletteToppingBuilder(autoPlay = true)
}

@Preview
@Composable
private fun OmeletteToppingBuilderDemoPreview() {
    OmeletteToppingBuilderDemo()
}
