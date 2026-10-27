package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private val MoonPath = PathParser()
    .parsePathString("M23 5C13 5 7 12 7 20s6 15 16 15c4 0 7-1 10-4-12 3-20-7-15-17 1-3 3-6 5-9Z")
    .toPath()
private val StarPath = PathParser()
    .parsePathString("m27 11 2 5 5 2-5 2-2 5-2-5-5-2 5-2Z")
    .toPath()

/** `NocturneMark`: the crescent and the four-point star of the invented app, drawn in a 40 by 40 view box at [size] dp. */
@Composable
internal fun NocturneProgramCarouselMark(size: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size.dp)) {
        val factor = this.size.width / 40f
        scale(factor, factor, pivot = Offset.Zero) {
            drawPath(MoonPath, Color(0xFF6FE8D6))
            drawPath(StarPath, Color(0xFF6493FF))
        }
    }
}
