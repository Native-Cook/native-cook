package com.ngocdevv.nativecook.blocks.modals.emojiratingpopup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val WAVE_HEIGHT = 108f

// The header's waves, in a box 320 wide: the colour field, a lighter wave over it and a pale crest.
private val Base = PathParser().parsePathString("M0 0H320V80C284 104 248 72 204 86C160 100 124 72 84 86C50 98 22 92 0 82Z").toPath()
private val Light = PathParser().parsePathString("M0 0H320V60C288 50 256 88 212 76C170 64 140 94 96 78C60 66 28 72 0 62Z").toPath()
private val Crest = PathParser().parsePathString("M0 0H320V36C278 48 244 22 198 34C152 46 112 24 66 38C38 46 16 42 0 36Z").toPath()
private val CrestFill = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.12f)), startY = 0f, endY = 48f)

/** A straight mix in RGB between the two stops the mood sits between. */
private fun mix(stops: List<Color>, mood: Float): Color {
    val m = mood.coerceIn(0f, (stops.size - 1).toFloat())
    val i = minOf(m.toInt(), stops.size - 2)
    val t = m - i
    val a = stops[i]
    val b = stops[i + 1]
    return Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)
}

/**
 * The wavy header of the dialog; its colour follows the rating.
 *
 * @param mood Where the dialog's mood is: 0 for none yet, then 1 to 5 stars.
 * @param field The colour of the field at each mood, and of the wave over it.
 */
@Composable
internal fun Wave(width: Dp, mood: () -> Float, field: List<Color>, wave: List<Color>) {
    Canvas(Modifier.width(width).height(WAVE_HEIGHT.dp)) {
        val stretch = size.width / 320f / density
        scale(density * stretch, density, pivot = Offset.Zero) {
            drawPath(Base, mix(field, mood()))
            drawPath(Light, mix(wave, mood()))
            drawPath(Crest, CrestFill)
        }
    }
}
