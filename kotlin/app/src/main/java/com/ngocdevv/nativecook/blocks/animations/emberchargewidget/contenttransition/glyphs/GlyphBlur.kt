// Vendored from expo-content-transition 0.1.7 (MIT, rit3zh / 650 Industries). See THIRD_PARTY.md.
// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget.contenttransition.glyphs

import android.os.Build
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import kotlin.math.roundToInt

internal object GlyphBlur {
  // Whole-pixel buckets make a decaying blur step down in visible notches, so the glyph reads as
  // vibrating while it settles. Eighth-pixel buckets sit below that threshold and keep the cache small.
  private const val STEPS_PER_PX = 8f
  private const val MIN_RADIUS = 0.375f
  private const val MAX_CACHED = 512

  private val cache = HashMap<Int, RenderEffect>()

  fun effect(radius: Float): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius < MIN_RADIUS) {
      return null
    }

    val bucket = (radius * STEPS_PER_PX).roundToInt()
    cache[bucket]?.let { return it }

    if (cache.size >= MAX_CACHED) {
      cache.clear()
    }

    val quantized = bucket / STEPS_PER_PX
    return BlurEffect(quantized, quantized, TileMode.Decal).also { cache[bucket] = it }
  }
}
