package com.ngocdevv.nativecook.blocks.inputs.scrubnumberfield

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// The block has the whole screen: the Adjust page of an invented camera app, Parlour. Three number
// fields are scrubbed like in a design tool (press and drag sideways) and edit the photo live through
// one shader. In the 9 s film a ghost finger scrubs Exposure slowly, flicks Warmth, scrubs Vignette
// and double taps the photo to roll everything back. Touch a field to take over (the film returns
// 2 s after the last touch).
@Composable
fun ScrubNumberFieldDemo() {
    ScrubNumberField(autoPlay = true)
}

@Preview
@Composable
private fun ScrubNumberFieldDemoPreview() {
    ScrubNumberFieldDemo()
}
