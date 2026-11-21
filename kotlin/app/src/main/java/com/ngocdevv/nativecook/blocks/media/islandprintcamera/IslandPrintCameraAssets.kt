package com.ngocdevv.nativecook.blocks.media.islandprintcamera

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface

internal class IslandPrintCameraAssets(assets: AssetManager) {
    val plate: Bitmap = assets.open("${PREFIX}plate_back.jpg").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    val wordmark: Bitmap = assets.open("${PREFIX}pomo_wordmark.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    val inter: Typeface = Typeface.createFromAsset(assets, "${PREFIX}Inter-Regular.ttf")

    private companion object {
        const val PREFIX = "islandprintcamera/islandprintcamera_"
    }
}
