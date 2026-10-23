// Vendored from expo-content-transition 0.1.7 (MIT, rit3zh / 650 Industries). See THIRD_PARTY.md.
// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget.contenttransition.records

data class TransitionShape(
  val blurIntensity: Float = DEFAULT_BLUR_INTENSITY,

  val maxBlurRadius: Float = Float.MAX_VALUE,
  val enterScale: Float = DEFAULT_ENTER_SCALE,
  val travelRatio: Float = DEFAULT_TRAVEL_RATIO
) {
  companion object {
    const val DEFAULT_BLUR_INTENSITY = 1f
    const val DEFAULT_ENTER_SCALE = 0.4f
    const val DEFAULT_TRAVEL_RATIO = 1f / 3f
  }
}
