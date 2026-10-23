package com.ngocdevv.nativecook.blocks.lists.menuproductcarousel

import android.app.Activity
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MenuProductCarouselDemo() {
    val context = LocalContext.current
    val freezeAt = remember(context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            (context as? Activity)?.intent?.getStringExtra("freezeAt")
                ?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
        } else {
            null
        }
    }
    MenuProductCarousel(autoPlay = freezeAt == null, freezeAt = freezeAt)
}

@Preview
@Composable
private fun MenuProductCarouselDemoPreview() {
    MenuProductCarouselDemo()
}
