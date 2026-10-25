package com.ngocdevv.nativecook.blocks.sheets.rollerblindsheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/*
 Line icons for the room's controls and the scenes printed on the blind, drawn on a 24-dp grid
 with round caps (no icon font, no emoji): the same path data as RollerBlindIcons.tsx.
 */

internal enum class RollerBlindGlyph(val d: String, val width: Float = 1.6f, val circle: Boolean = false) {
    Bulb("M9 17.5h6M10 20.5h4M8.2 14.2A6 6 0 1 1 15.8 14.2c-.8.7-1.3 1.6-1.3 2.6V17h-5v-.2c0-1-.5-1.9-1.3-2.6Z"),
    Lamp("M8 3.5h8l3 8H5l3-8ZM12 11.5v8M8 20.5h8"),
    Thermo("M10 4.5a2 2 0 1 1 4 0v9.3a4 4 0 1 1-4 0V4.5ZM12 10v6"),
    Fan(
        "M12 10.4C11 7 12 3.5 14.6 4.2c2.2.6 1 4.6-2.6 6.2ZM13.6 12c3.4-1 6.9 0 6.2 2.6-.6 2.2-4.6 1-6.2-2.6ZM12 13.6c1 3.4 0 6.9-2.6 6.2-2.2-.6-1-4.6 2.6-6.2ZM10.4 12C7 13 3.5 12 4.2 9.4c.6-2.2 4.6-1 6.2 2.6Z",
        width = 1.4f,
        circle = true,
    ),
    Moon("M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10Z"),
    Book("M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5ZM12 6.5v13"),
    Sleep("M5 8h5l-5 6h5M13 5h6l-6 7h6M14 17h4l-4 4h4"),
    Sunrise("M3.5 18h17M7 18a5 5 0 0 1 10 0M12 4.5v4M5.6 9.6l1.8 1.8M18.4 9.6l-1.8 1.8M7 21h10"),
}

@Composable
internal fun RollerBlindIcon(glyph: RollerBlindGlyph, color: Color, size: Float = 22f, modifier: Modifier = Modifier) {
    val path: Path = remember(glyph) { PathParser().parsePathString(glyph.d).toPath() }
    Canvas(modifier.size(size.dp)) {
        val k = this.size.width / 24f
        scale(k, k, pivot = Offset.Zero) {
            if (glyph.circle) drawCircle(color, 1.6f, Offset(12f, 12f), style = Stroke(width = 1.6f))
            drawPath(path, color, style = Stroke(width = glyph.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
