package com.ngocdevv.nativecook.blocks.lists.planetexplorercarousel

import android.app.Activity
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun PlanetExplorerCarouselDemo() {
    val context = LocalContext.current
    val freezeAt = remember(context) {
        val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debug) (context as? Activity)?.intent?.getStringExtra("freezeAt")?.toFloatOrNull() else null
    }
    PlanetExplorerCarousel(autoPlay = freezeAt == null, freezeAt = freezeAt)
}

@Preview
@Composable
private fun PlanetExplorerCarouselDemoPreview() {
    PlanetExplorerCarouselDemo()
}
