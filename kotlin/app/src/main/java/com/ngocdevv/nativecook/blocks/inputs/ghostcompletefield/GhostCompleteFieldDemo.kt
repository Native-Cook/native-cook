package com.ngocdevv.nativecook.blocks.inputs.ghostcompletefield

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 Ghost Complete Field: a mail reply whose body offers the rest of the sentence in grey; a swipe
 right or the keycap chip fills it with ink. The Cc field offers mail domains as chips.
 Component: GhostCompleteField.kt (screen), GhostField.kt (the reusable field).
 */
@Composable
fun GhostCompleteFieldDemo() {
    GhostCompleteField(autoPlay = true)
}

@Preview
@Composable
private fun GhostCompleteFieldDemoPreview() {
    GhostCompleteFieldDemo()
}
