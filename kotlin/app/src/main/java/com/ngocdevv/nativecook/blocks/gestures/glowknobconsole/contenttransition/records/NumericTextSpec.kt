// Vendored from expo-content-transition 0.1.7 (MIT, rit3zh / 650 Industries). See THIRD_PARTY.md.
// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.contenttransition.records

import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.contenttransition.animation.TransitionSprings
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.contenttransition.enums.NumericTextAlignment
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.contenttransition.enums.TransitionDirection

internal data class NumericTextSpec(
  val text: String,
  val alignment: NumericTextAlignment,
  val direction: TransitionDirection,
  val decimalSeparator: Char,
  val blurEnabled: Boolean,
  val shape: TransitionShape,
  val clipEnabled: Boolean,
  val animationsEnabled: Boolean,
  val duration: Float,
  val bounce: Float
) {
  val blurActive: Boolean get() = blurEnabled && shape.blurIntensity > 0f

  val effectiveShape: TransitionShape
    get() = if (blurActive) shape else shape.copy(blurIntensity = 0f)

  companion object {
    const val REFERENCE_DURATION = TransitionSprings.REFERENCE_DURATION
  }
}
