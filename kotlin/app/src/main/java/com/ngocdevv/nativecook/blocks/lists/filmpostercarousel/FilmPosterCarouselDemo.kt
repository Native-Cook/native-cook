package com.ngocdevv.nativecook.blocks.lists.filmpostercarousel

import android.content.pm.ApplicationInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun FilmPosterCarouselDemo() {
    val freezeAt = LocalActivity.current
        ?.takeIf { it.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
        ?.intent?.getStringExtra("freezeAt")?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0 }
    FilmPosterCarousel(autoPlay = true, freezeAt = freezeAt)
}

@Preview
@Composable
private fun FilmPosterCarouselDemoPreview() {
    FilmPosterCarouselDemo()
}
