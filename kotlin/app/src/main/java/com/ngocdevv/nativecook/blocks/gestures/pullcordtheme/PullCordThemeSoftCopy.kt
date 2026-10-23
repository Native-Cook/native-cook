package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import android.graphics.Bitmap

/**
 * A sprite pre-rendered with its colour matrix and a Gaussian blur, padded so the blur can spill past
 * the sprite's edge. [scale] is the copy's pixels per dp; the copy covers the sprite plus [padDp] on every side.
 */
internal class PullCordThemeSoftCopy(
    val bitmap: Bitmap,
    val scale: Float,
    val padDp: Float,
) {
    val widthDp: Float get() = bitmap.width / scale
    val heightDp: Float get() = bitmap.height / scale
}
