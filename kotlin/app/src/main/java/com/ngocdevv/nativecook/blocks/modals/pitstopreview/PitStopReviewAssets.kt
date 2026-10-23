package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal class PitStopReviewAssets(private val context: Context) {
    val carFrames: List<ImageBitmap> = listOf(
        "pitstopreview_car_pitch_m150.webp",
        "pitstopreview_car_pitch_m075.webp",
        "pitstopreview_car_pitch_000.webp",
        "pitstopreview_car_pitch_p075.webp",
        "pitstopreview_car_pitch_p150.webp",
        "pitstopreview_car_pitch_p225.webp",
        "pitstopreview_car_pitch_p300.webp",
    ).map(::load)
    val carShadow: ImageBitmap = load("pitstopreview_car_shadow.webp")
    val profile: ImageBitmap = load("pitstopreview_profile.jpg")

    private fun load(name: String): ImageBitmap {
        return context.assets.open("pitstopreview/$name").use { stream ->
            BitmapFactory.decodeStream(stream).asImageBitmap()
        }
    }
}
