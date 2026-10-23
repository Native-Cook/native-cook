// Vendored from expo-content-transition 0.1.7 (MIT, rit3zh / 650 Industries). See THIRD_PARTY.md.
// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget.contenttransition.records

import androidx.compose.ui.text.TextLayoutResult

internal class GlyphPlacement(
  val unit: String,
  val slot: GlyphSlot,
  val x: Float,
  val advance: Float,
  val layout: TextLayoutResult
)

internal class TypesetLine(
  val text: String,
  val units: List<String>,
  val placements: List<GlyphPlacement>,
  val width: Float,
  val height: Float,
  val baseline: Float
)
