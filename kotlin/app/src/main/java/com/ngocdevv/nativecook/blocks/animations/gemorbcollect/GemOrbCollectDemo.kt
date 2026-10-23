package com.ngocdevv.nativecook.blocks.animations.gemorbcollect

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun GemOrbCollectDemo() {
    AndroidView(
        modifier = Modifier,
        factory = { context ->
            GemOrbCollectView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                autoPlay = true
            }
        },
    )
}
