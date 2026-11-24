package com.ngocdevv.nativecook.blocks.inputs.dualrangehistogram

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// The filter sheet of an invented rental search app, Nook. A price histogram sits on a two-thumb range: bars inside
// take the colour, bars outside drain and sink, the labels ride the thumbs and roll their digits, and the thumbs
// squash when held and refuse to cross with a rubber band. Drag a thumb or tap a bar to take over; the film returns
// 2 s after the last touch.
@Composable
fun DualRangeHistogramDemo() {
    NookFilterScreen(autoPlay = true)
}

@Preview
@Composable
private fun DualRangeHistogramDemoPreview() {
    DualRangeHistogramDemo()
}
