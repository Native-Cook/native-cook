/** Gallery entry and preview for the Crumple to Delete autoplay loop. */
package com.ngocdevv.nativecook.blocks.gestures.crumpletodelete

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
/** Starts the complete nine-second delete and Undo demonstration. */
fun CrumpleToDeleteDemo() {
    CrumpleToDelete(autoPlay = true)
}

@Preview
@Composable
private fun CrumpleToDeleteDemoPreview() {
    CrumpleToDeleteDemo()
}
