package com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Offline raster of the original shader at rest, kept as a PNG asset for lint/tooling. */
internal object ZeroFallbackBackdropAsset {
    fun decode(assets: AssetManager): Bitmap =
        assets.open("chromezeronumeral/zero-fallback-backdrop.png").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }
}
