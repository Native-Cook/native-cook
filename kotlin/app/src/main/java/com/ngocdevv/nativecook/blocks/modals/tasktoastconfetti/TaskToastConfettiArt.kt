package com.ngocdevv.nativecook.blocks.modals.tasktoastconfetti

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val ArtBlue = Color(0xFF1A5CFF)
internal val Scribble = Color(0xFFB9BDC9)

private fun svg(data: String): Path = PathParser().parsePathString(data).toPath()

private val LeafLeft = svg("M32 13 C26 4 18 6 17 9 C22 9 27 11 32 15 Z")
private val LeafRight = svg("M32 13 C37 3 46 5 47 8 C42 8 37 11 32 15 Z")
private val Stem = svg("M31 15 C30 9 31 5 33 2")
private val BrowLeft = svg("M20 24.5 L30 27")
private val BrowRight = svg("M48 24.5 L38 27")
private val MascotMouth = svg("M30 45 Q35 49 40 45")
private val AvatarMouth = svg("M18 31 Q22 34 26 31")
private val Drop = svg("M20 4 C27 14 36 21 36 30 C36 39 29 45 20 45 C11 45 4 39 4 30 C4 21 13 14 20 4 Z")
private val ArrowShaft = svg("M13 3 C10 15 17 27 14 47")
private val ArrowHead = svg("M5 37 L14 48 L23 38")

private val RoundStroke = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.ellipse(color: Color, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float = 1f) =
    drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), alpha = alpha)

/** A canvas in the coordinates of the React Native SVG's viewBox, one unit to the dp. */
@Composable
private fun ViewBox(width: Int, height: Int, draw: DrawScope.() -> Unit) {
    Canvas(Modifier.size(width.dp, height.dp)) { scale(density, density, pivot = Offset.Zero) { draw() } }
}

/** A round tomato-red creature with a sprout, looking right at the bubble. */
@Composable
internal fun Mascot() = ViewBox(64, 64) {
    ellipse(Color.Black, 32f, 60f, 17f, 3f, alpha = 0.12f)
    // The SVG's gradient box is the circle's bounds: centre at 38 % / 34 %, radius 70 %.
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFF7B6E), 0.55f to Color(0xFFEF3340), 1f to Color(0xFFB8172A),
            center = Offset(8f + 0.38f * 48f, 11f + 0.34f * 48f), radius = 0.7f * 48f,
        ),
        radius = 24f, center = Offset(32f, 35f),
    )
    rotate(-30f, Offset(22f, 23f)) { ellipse(Color.White, 22f, 23f, 7f, 4.5f, alpha = 0.35f) }
    drawPath(LeafLeft, Color(0xFF2EA95A))
    drawPath(LeafRight, Color(0xFF3CC46A))
    drawPath(Stem, Color(0xFF24874A), style = RoundStroke)
    ellipse(Color.White, 27f, 33f, 6.2f, 7.4f)
    ellipse(Color.White, 41f, 33f, 6.2f, 7.4f)
    drawCircle(Color(0xFF15131C), 3.4f, Offset(29.5f, 34f))
    drawCircle(Color(0xFF15131C), 3.4f, Offset(43.5f, 34f))
    drawCircle(Color.White, 1.1f, Offset(30.6f, 32.6f))
    drawCircle(Color.White, 1.1f, Offset(44.6f, 32.6f))
    val dark = Color(0xFF7A0F1D)
    val brow = Stroke(width = 2.6f, cap = StrokeCap.Round)
    drawPath(BrowLeft, dark, style = brow)
    drawPath(BrowRight, dark, style = brow)
    drawPath(MascotMouth, dark, style = RoundStroke)
    ellipse(Color(0xFFFF9AA2), 20f, 43f, 3.4f, 2f, alpha = 0.7f)
    ellipse(Color(0xFFFF9AA2), 47f, 43f, 3.4f, 2f, alpha = 0.7f)
}

/** The profile picture: a fuzzy blue face with big eyes. */
@Composable
internal fun Avatar() = ViewBox(44, 44) {
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFF3F7BFF), Color(0xFF0A2A8F)),
            center = Offset(1f + 0.4f * 42f, 1f + 0.35f * 42f), radius = 0.7f * 42f,
        ),
        radius = 21f, center = Offset(22f, 22f),
    )
    drawCircle(Color(0xFF5D8FFF), 21f, Offset(22f, 22f), style = Stroke(1.5f))
    ellipse(Color.White, 16f, 21f, 6f, 7f)
    ellipse(Color.White, 28f, 20f, 6f, 7f)
    val ink = Color(0xFF0D0F1A)
    drawCircle(ink, 3f, Offset(17.5f, 22f))
    drawCircle(ink, 3f, Offset(26.5f, 21.5f))
    drawPath(AvatarMouth, ink, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
}

/** The app's mark: a white drop with a dot. */
@Composable
internal fun Logo() = ViewBox(40, 46) {
    drawPath(Drop, Color.White)
    drawCircle(Color.White, 4f, Offset(27f, 5f))
    drawLine(ArtBlue, Offset(20f, 26f), Offset(20f, 36f), strokeWidth = 5f, cap = StrokeCap.Round)
    drawCircle(ArtBlue, 3f, Offset(20f, 20f))
}

/** The scribbled arrow that points from the inspiration note to the tab bar. */
@Composable
internal fun ScribbleArrow() = ViewBox(30, 52) {
    drawPath(ArrowShaft, Scribble, style = RoundStroke)
    drawPath(ArrowHead, Scribble, style = RoundStroke)
}
