package com.ngocdevv.nativecook.blocks.modals.stackingtoastfeed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Line = Color(0xFFDEDFE3)
private const val RINGS = 7
private const val STEPS = 72

private class Hill(val x: Float, val y: Float, val reach: Float, val phase: Float)

// Where the contour maps sit, as fractions of the screen, and how wide their outer ring is.
private val Hills = listOf(
    Hill(0.05f, 0.04f, 0.34f, 0.4f),
    Hill(0.98f, 0.62f, 0.4f, 2.1f),
    Hill(0.1f, 0.97f, 0.3f, 4.2f),
)

/** A ring around (cx, cy) whose radius wobbles with a few fixed waves, as a contour line does. */
private fun ring(cx: Float, cy: Float, radius: Float, phase: Float) = Path().apply {
    for (i in 0..STEPS) {
        val angle = i.toFloat() / STEPS * 2f * PI.toFloat()
        val wobble = 1f + 0.12f * sin(angle * 3 + phase) + 0.07f * sin(angle * 5 + phase * 2) + 0.04f * sin(angle * 8 + phase * 3)
        val x = cx + cos(angle) * radius * wobble
        val y = cy + sin(angle) * radius * wobble
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The faint topographic lines behind the page. */
@Composable
internal fun Contours(width: Float, height: Float) {
    val paths = remember(width, height) {
        Hills.flatMap { hill ->
            List(RINGS) { r ->
                ring(hill.x * width, hill.y * height, (r + 1f) / RINGS * hill.reach * width, hill.phase + r * 0.15f)
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        scale(density, density, pivot = Offset.Zero) {
            for (path in paths) drawPath(path, Line, style = Stroke(1f))
        }
    }
}
