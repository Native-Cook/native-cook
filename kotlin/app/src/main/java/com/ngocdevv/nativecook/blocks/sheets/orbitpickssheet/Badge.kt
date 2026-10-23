package com.ngocdevv.nativecook.blocks.sheets.orbitpickssheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

internal val INDIGO = Color(0xFF5B3FE0)
private const val POINTS = 16

// An eight-point rosette in a 64 × 64 box: the outer and inner radius alternate.
private val ROSETTE = Path().apply {
    for (i in 0 until POINTS) {
        val radius = if (i % 2 == 0) 31f else 26f
        val angle = i.toFloat() / POINTS * (Math.PI * 2).toFloat() - (Math.PI / 2).toFloat()
        val x = 32f + cos(angle) * radius
        val y = 32f + sin(angle) * radius
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The indigo rosette with a white bolt that stands for the picks. */
@Composable
internal fun Badge(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            scale(this.size.width / 64f, pivot = Offset.Zero) {
                drawPath(ROSETTE, INDIGO, style = Fill)
                drawPath(ROSETTE, INDIGO, style = Stroke(width = 2f, join = StrokeJoin.Round))
            }
        }
        Icon(Icons.Filled.FlashOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.42f))
    }
}
