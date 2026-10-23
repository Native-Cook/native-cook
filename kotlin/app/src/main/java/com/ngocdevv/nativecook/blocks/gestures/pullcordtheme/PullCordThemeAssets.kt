package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.ReplicaArt

/** The block's seven images and the two blurred copies drawn from them. Nothing loads from the network. */
internal class PullCordThemeAssets(
    val wall: Bitmap,
    val wallLight: Bitmap,
    val lit: Bitmap,
    val dim: Bitmap,
    val warm: Bitmap,
    val lightAdd: Bitmap,
    val lightMul: Bitmap,
    /** The lit sprite's bright pass, blurred (the filament's bloom). */
    val bloom: PullCordThemeSoftCopy,
    /** The warm sprite as a black silhouette, blurred (the bulb's cast shadow on the light wall). */
    val shadow: PullCordThemeSoftCopy,
) {
    companion object {
        /** Any sprite to a black silhouette keeping its alpha. */
        private val SHADOW = floatArrayOf(
            0f, 0f, 0f, 0f, 0.12f,
            0f, 0f, 0f, 0f, 0.08f,
            0f, 0f, 0f, 0f, 0.04f,
            0f, 0f, 0f, 1f, 0f,
        )

        /** Decodes and pre-renders everything; call it off the main thread. */
        fun load(context: Context, g: PullCordThemeGeometry, density: Float): PullCordThemeAssets {
            val warm = decode(context, R.drawable.pullcordtheme_bulb_warm)
            val lit = decode(context, R.drawable.pullcordtheme_bulb_lit)
            val spriteWidth = ReplicaArt.spriteW * ReplicaArt.spriteScale * g.s
            val spriteHeight = ReplicaArt.spriteH * ReplicaArt.spriteScale * g.s
            return PullCordThemeAssets(
                wall = decode(context, R.drawable.pullcordtheme_wall),
                wallLight = decode(context, R.drawable.pullcordtheme_wall_light),
                lit = lit,
                dim = decode(context, R.drawable.pullcordtheme_bulb_dim),
                warm = warm,
                lightAdd = decode(context, R.drawable.pullcordtheme_light_add),
                lightMul = decode(context, R.drawable.pullcordtheme_light_mul),
                bloom = PullCordThemeBlur.softCopy(
                    lit, ReplicaArt.brightPass, spriteWidth, spriteHeight, ReplicaArt.bloom, density,
                ),
                shadow = PullCordThemeBlur.softCopy(
                    warm, SHADOW, spriteWidth, spriteHeight, PullCordThemeMotion.Hero.shadowBlur * g.s, density,
                ),
            )
        }

        private fun decode(context: Context, id: Int): Bitmap {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeResource(context.resources, id, options)
            check(bitmap != null) { "PullCordTheme image $id did not decode" }
            return bitmap
        }
    }
}
