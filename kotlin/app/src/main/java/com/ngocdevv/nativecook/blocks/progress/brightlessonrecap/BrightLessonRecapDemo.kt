package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The Bright Lesson Recap screen on the whole display, playing its 6.5 s loop by itself. */
@Composable
fun BrightLessonRecapDemo() {
    BrightLessonRecap(autoPlay = true)
}

@Preview
@Composable
private fun BrightLessonRecapDemoPreview() {
    BrightLessonRecapDemo()
}
