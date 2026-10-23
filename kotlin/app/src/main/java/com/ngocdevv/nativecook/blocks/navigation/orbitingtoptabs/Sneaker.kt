package com.ngocdevv.nativecook.blocks.navigation.orbitingtoptabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** A sneaker's colours; [shade] is the toe cap and the lace panel, [accent] the heel and the stripes. */
internal class SneakerColors(val upper: Color, val shade: Color, val accent: Color, val midsole: Color, val outsole: Color)

// A sneaker seen from the side, toe to the left, drawn in a 300 × 160 box.
internal const val SNEAKER_RATIO = 160f / 300f

private fun outline(data: String) = PathParser().parsePathString(data).toPath()

private val Outsole = outline("M22 131 Q13 140 25 147 L268 147 Q283 146 281 131 Z")
private val Midsole = outline("M20 118 Q9 126 22 134 L281 134 Q287 120 280 103 L196 112 Q120 122 60 118 Z")
private val Upper = outline(
    "M20 118 Q22 96 60 88 Q118 76 148 48 L160 34 Q168 30 176 40 Q200 66 236 54 L258 36 Q268 34 270 46 Q278 74 280 103 L196 112 Q120 122 60 118 Z",
)
private val Toe = outline("M20 118 Q22 96 60 88 Q78 84 92 82 Q84 102 96 119 Q76 119 60 118 Z")
private val Heel = outline("M236 54 L258 36 Q268 34 270 46 Q278 74 280 103 L232 108 Q244 80 236 54 Z")
private val Panel = outline("M100 80 Q130 70 150 46 L160 34 Q168 30 176 40 L184 50 Q160 80 122 96 Z")
private val Stripes = outline("M150 113 L186 66 L198 70 L164 113 Z M178 112 L208 72 L218 76 L190 111 Z")
private val Collar = outline("M176 40 Q200 66 236 54 Q206 78 180 52 Z")
private val Laces = outline("M112 80 L126 90 M124 72 L140 82 M136 62 L153 72 M147 52 L165 61")
private val Inside = Color(0xFF16161A)
private val Lace = Color(0xFFF6F4EE)

/** A sneaker drawn with paths, so that it sits on the white page without a photo's rectangle. */
@Composable
internal fun Sneaker(width: Float, colors: SneakerColors, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width.dp, (width * SNEAKER_RATIO).dp)) {
        scale(size.width / 300f, pivot = Offset.Zero) {
            drawOval(Color.Black, topLeft = Offset(14f, 143f), size = Size(276f, 14f), alpha = 0.07f)
            drawPath(Outsole, colors.outsole)
            drawPath(Midsole, colors.midsole)
            drawPath(Upper, colors.upper)
            drawPath(Toe, colors.shade)
            drawPath(Panel, colors.shade)
            drawPath(Heel, colors.accent)
            drawPath(Stripes, colors.accent)
            drawPath(Collar, Inside)
            drawPath(Laces, Lace, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }
    }
}
