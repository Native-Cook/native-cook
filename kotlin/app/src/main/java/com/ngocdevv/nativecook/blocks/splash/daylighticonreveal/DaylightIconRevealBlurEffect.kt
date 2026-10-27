package com.ngocdevv.nativecook.blocks.splash.daylighticonreveal

import android.os.Build
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode

/**
 * A Gaussian blur of [sigmaPx] device pixels for a `graphicsLayer`, or null when there is nothing
 * to blur or the device cannot (render effects need API 31). React Native's `filter: blur(Npx)`
 * on Android is the same call: N dp becomes the standard deviation in pixels, edges decal.
 */
internal fun daylightIconRevealBlur(sigmaPx: Float): RenderEffect? {
    if (sigmaPx < 0.005f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return BlurEffect(sigmaPx, sigmaPx, TileMode.Decal)
}
