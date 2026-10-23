package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ngocdevv.nativecook.R

data class HoldDragResizeAssets(
    val clips: List<Bitmap>,
)

@Composable
fun rememberHoldDragResizeAssets(): HoldDragResizeAssets {
    val resources = LocalContext.current.resources
    return remember(resources) {
        HoldDragResizeAssets(
            clips = listOf(
                BitmapFactory.decodeResource(resources, R.drawable.holddragresize_clip_shelves),
                BitmapFactory.decodeResource(resources, R.drawable.holddragresize_clip_cocktail),
                BitmapFactory.decodeResource(resources, R.drawable.holddragresize_clip_lamps),
                BitmapFactory.decodeResource(resources, R.drawable.holddragresize_clip_lounge),
            ),
        )
    }
}
