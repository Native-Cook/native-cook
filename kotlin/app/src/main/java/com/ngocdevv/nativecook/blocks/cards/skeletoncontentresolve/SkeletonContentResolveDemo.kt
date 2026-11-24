package com.ngocdevv.nativecook.blocks.cards.skeletoncontentresolve

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// The home of an invented podcast app, Hearth. Its loading state turns into the content: bars with
// one band of light stretch to the exact width of their lines, the text fades in, the covers open
// through a soft radial wipe. Pull the list down to refresh (the film returns 2 s after the last touch).
@Composable
fun SkeletonContentResolveDemo() {
    SkeletonContentResolve(autoPlay = true)
}

@Preview
@Composable
private fun SkeletonContentResolveDemoPreview() {
    SkeletonContentResolveDemo()
}
