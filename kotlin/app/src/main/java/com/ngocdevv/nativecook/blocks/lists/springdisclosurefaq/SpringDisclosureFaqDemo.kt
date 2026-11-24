package com.ngocdevv.nativecook.blocks.lists.springdisclosurefaq

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// The block has the whole screen: the help centre of an invented bank app, Alder. One question is
// open at a time; in the 9 s film a ghost finger opens the third, the fifth and the first again.
// Tap any question to take over (the film returns 2 s after the last touch).
@Composable
fun SpringDisclosureFaqDemo() {
    SpringDisclosureFaq(autoPlay = true)
}

@Preview
@Composable
private fun SpringDisclosureFaqDemoPreview() {
    SpringDisclosureFaqDemo()
}
