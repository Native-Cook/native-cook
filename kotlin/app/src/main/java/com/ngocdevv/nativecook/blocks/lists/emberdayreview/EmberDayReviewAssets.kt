package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

private const val STAR_ASSET = "emberdayreview/emberdayreview_four-point-star.webp"

/** The glossy four-point star (`assets/four-point-star.webp` in the React Native block), decoded once. */
@Composable
internal fun rememberEmberDayReviewStar(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.assets.open(STAR_ASSET).use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                // Mip-maps keep the small stars smooth when the 384 px artwork is drawn at a third of its size.
                bitmap.setHasMipMap(true)
                bitmap.asImageBitmap()
            }
        }.getOrNull()
    }
}
