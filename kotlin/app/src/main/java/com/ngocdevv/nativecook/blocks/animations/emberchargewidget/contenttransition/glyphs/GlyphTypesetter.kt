// Vendored from expo-content-transition 0.1.7 (MIT, rit3zh / 650 Industries). See THIRD_PARTY.md.
// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget.contenttransition.glyphs

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.contenttransition.records.GlyphPlacement
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.contenttransition.records.TypesetLine

internal class GlyphTypesetter(
  private val measurer: TextMeasurer,
  private val style: TextStyle,
  private val density: Density,
  private val layoutDirection: LayoutDirection
) {
  private val glyphCache = HashMap<String, TextLayoutResult>()

  fun glyphLayout(unit: String): TextLayoutResult = glyphCache.getOrPut(unit) { measure(unit) }

  fun typeset(text: String, decimalSeparator: Char, leftAnchored: Boolean): TypesetLine {
    val units = GlyphSlots.splitUnits(text)
    if (units.isEmpty()) {
      val probe = measure("0")
      return TypesetLine(
        text = text,
        units = units,
        placements = emptyList(),
        width = 0f,
        height = probe.size.height.toFloat(),
        baseline = probe.firstBaseline
      )
    }

    val slots = GlyphSlots.compute(units, decimalSeparator, leftAnchored)
    val line = measure(text)

    val placements = ArrayList<GlyphPlacement>(units.size)
    var offset = 0
    for (index in units.indices) {
      val unit = units[index]
      val next = offset + unit.length
      val x = line.getHorizontalPosition(offset, usePrimaryDirection = true)
      val nextX = line.getHorizontalPosition(next, usePrimaryDirection = true)
      val advance = (nextX - x).takeIf { it > 0f } ?: (line.size.width - x).coerceAtLeast(0f)
      placements.add(
        GlyphPlacement(
          unit = unit,
          slot = slots[index],
          x = x,
          advance = advance,
          layout = glyphLayout(unit)
        )
      )
      offset = next
    }

    return TypesetLine(
      text = text,
      units = units,
      placements = placements,
      width = line.size.width.toFloat(),
      height = line.size.height.toFloat(),
      baseline = line.firstBaseline
    )
  }

  private fun measure(text: String): TextLayoutResult = measurer.measure(
    text = AnnotatedString(text),
    style = style,
    softWrap = false,
    maxLines = 1,
    layoutDirection = layoutDirection,
    density = density
  )
}
