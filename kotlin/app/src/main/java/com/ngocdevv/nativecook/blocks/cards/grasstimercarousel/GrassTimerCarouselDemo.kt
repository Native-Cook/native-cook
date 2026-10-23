package com.ngocdevv.nativecook.blocks.cards.grasstimercarousel

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun GrassTimerCarouselDemo() {
    val context = LocalContext.current
    val manual = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
        context.findGrassTimerActivity()?.intent?.getBooleanExtra("grass-manual", false) == true
    GrassTimerCarousel(autoPlay = !manual)
}

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun PreviewGrassTimerCarousel() {
    GrassTimerCarouselDemo()
}
