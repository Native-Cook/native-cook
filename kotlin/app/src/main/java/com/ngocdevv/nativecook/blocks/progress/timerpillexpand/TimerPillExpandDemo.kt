package com.ngocdevv.nativecook.blocks.progress.timerpillexpand

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The timer draws its own full-screen cooking step, including the safe areas. */
@Composable
fun TimerPillExpandDemo() {
    TimerPillExpand(autoPlay = true)
}

@Preview
@Composable
private fun TimerPillExpandDemoPreview() {
    TimerPillExpandDemo()
}
