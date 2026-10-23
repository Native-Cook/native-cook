package com.ngocdevv.nativecook.blocks.animations.particledispersion

import android.graphics.RectF
import java.io.File
import kotlin.math.max

internal data class EffectScene(
  val frame: File,
  val mask: File,
  val imageWidth: Int,
  val imageHeight: Int,
  val bounds: RectF,
  val ownsFiles: Boolean
) {
  fun displayBounds(width: Float, height: Float): FloatArray {
    val scale = max(width / imageWidth, height / imageHeight)
    val w = imageWidth * scale
    val h = imageHeight * scale
    return floatArrayOf(((width - w) / 2 + bounds.left * w) / width,
      ((height - h) / 2 + bounds.top * h) / height,
      bounds.width() * w / width, bounds.height() * h / height)
  }

  fun release() {
    if (ownsFiles) { frame.delete(); mask.delete() }
  }

}
