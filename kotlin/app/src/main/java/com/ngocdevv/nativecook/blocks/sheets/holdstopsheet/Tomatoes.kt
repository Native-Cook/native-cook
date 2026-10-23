package com.ngocdevv.nativecook.blocks.sheets.holdstopsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The pile's height above the bottom edge, in dp. */
internal const val PILE_HEIGHT = 380f
// Rows of the pile, from the bottom up, and the gap between them.
private const val ROWS = 13
private const val ROW_STEP = 28f
// How far a tomato falls out of sight below the screen, and how much of the motion is spread over the tomatoes.
private const val FALL = 640f
private const val SPREAD = 0.5f

private enum class Kind { Red, Yellow, Orange }
private enum class Face { None, Dots, Smile, Happy, Oh }

// Colours of a piece: light where the light falls, base, and the shaded edge.
private fun palette(kind: Kind) = when (kind) {
    Kind.Red -> listOf(Color(0xFFFF9A62), Color(0xFFF2622D), Color(0xFFCF401B))
    Kind.Yellow -> listOf(Color(0xFFFFE68A), Color(0xFFFBC63A), Color(0xFFE39D12))
    Kind.Orange -> listOf(Color(0xFFFFBE6A), Color(0xFFF88F1F), Color(0xFFD9690B))
}
private val Leaf = Color(0xFF4FB03C)
private val LeafDark = Color(0xFF2F8A2A)
private val FaceInk = Color(0xFF3A2019)
private val Blush = Color(0xFFFF7F6E)

/** A fixed pseudo-random number in [0, 1) for a seed, so every pile is the same pile. */
private fun seeded(seed: Double): Double {
    val x = sin(seed * 127.1 + 311.7) * 43758.5453
    return x - floor(x)
}

/** What `Easing.bounce` of Reanimated gives: a ball that lands and bounces three times. */
internal fun bounce(t: Float): Float = when {
    t < 1f / 2.75f -> 7.5625f * t * t
    t < 2f / 2.75f -> (t - 1.5f / 2.75f).let { 7.5625f * it * it + 0.75f }
    t < 2.5f / 2.75f -> (t - 2.25f / 2.75f).let { 7.5625f * it * it + 0.9375f }
    else -> (t - 2.625f / 2.75f).let { 7.5625f * it * it + 0.984375f }
}

private class Tomato(
    val x: Float,
    val y: Float,
    val size: Float,
    val kind: Kind,
    val face: Face,
    val delay: Float,
    val tilt: Float,
    val calyx: Float,
)

/** How high the pile stands at a point [u] of the width (0 left, 1 right), in dp. */
private fun surface(u: Double) = 262 + 58 * sin(u * 5.6 + 0.9) + 26 * sin(u * 13.1 + 2.2)

/** The tomatoes of a pile [width] dp wide, back ones first. */
private fun pile(width: Float): List<Tomato> {
    val list = mutableListOf<Tomato>()
    var i = 0.0
    for (row in 0 until ROWS) {
        val rowY = PILE_HEIGHT - 20 - row * ROW_STEP
        var x = -14 + seeded(row * 13.0 + 1) * 22
        while (x < width + 8) {
            i += 1
            val big = seeded(i * 11 + 9) > 0.95 && row > 1 && row < 7
            val size = if (big) 64 + seeded(i * 11 + 3) * 12 else 36 + seeded(i * 11 + 3) * 18
            val cx = x + size * 0.45
            val cy = rowY + (seeded(i * 11 + 4) - 0.5) * 16
            if (PILE_HEIGHT - cy < surface(cx / width)) {
                val pick = seeded(i * 11 + 6)
                val kind = if (pick < 0.76) Kind.Red else if (pick < 0.93) Kind.Yellow else Kind.Orange
                val look = seeded(i * 11 + 7)
                val face = when {
                    size < 40 && look < 0.5 -> Face.None
                    look < 0.35 -> Face.Dots
                    look < 0.6 -> Face.Smile
                    look < 0.82 -> Face.Happy
                    else -> Face.Oh
                }
                list += Tomato(
                    x = (cx - size / 2).toFloat(),
                    y = (cy - size / 2).toFloat(),
                    size = size.toFloat(),
                    kind = kind,
                    face = face,
                    delay = (seeded(i * 11 + 5) * SPREAD).toFloat(),
                    tilt = ((seeded(i * 11 + 8) - 0.5) * 50).toFloat(),
                    calyx = (seeded(i * 11 + 10) * 72).toFloat(),
                )
            }
            x += size * 0.74 + seeded(i * 11 + 2) * 8
        }
    }
    // Lower and bigger pieces sit in front.
    return list.sortedBy { it.y + it.size * 0.6f }
}

/** A five-leaf calyx around (50, 20) in the 100 x 100 box, turned by [turn] degrees. */
private fun calyxPath(turn: Float) = Path().apply {
    for (k in 0 until 5) {
        val a = (-90 + turn + k * 72) * PI.toFloat() / 180f
        val l = a - 0.5f
        val r = a + 0.5f
        moveTo(50f, 20f)
        quadraticTo(50 + cos(l) * 13, 20 + sin(l) * 9, 50 + cos(a) * 24, 20 + sin(a) * 15)
        quadraticTo(50 + cos(r) * 13, 20 + sin(r) * 9, 50f, 20f)
        close()
    }
}

private fun curve(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float) = Path().apply {
    moveTo(x1, y1)
    quadraticTo(cx, cy, x2, y2)
}

private val Blade = Path().apply {
    moveTo(52f, 15f)
    quadraticTo(66f, 2f, 80f, 10f)
    quadraticTo(70f, 22f, 52f, 15f)
    close()
}

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) =
    drawOval(color, Offset(cx - rx, cy - ry), Size(2 * rx, 2 * ry))

/** One tomato in its 100 x 100 box: a lit body, its leaves, a highlight and maybe a face. */
private fun DrawScope.art(tomato: Tomato, body: Brush, calyx: Path) {
    val edge = palette(tomato.kind)[2]
    drawOval(body, Offset(4f, 14f), Size(92f, 84f))
    if (tomato.kind == Kind.Orange) {
        for ((x, y) in listOf(30f to 50f, 62f to 42f, 44f to 74f, 70f to 66f, 52f to 58f)) oval(x, y, 1.6f, 1.6f, edge.copy(alpha = 0.35f))
        drawLine(LeafDark, Offset(50f, 18f), Offset(51f, 9f), 4f, StrokeCap.Round)
        drawPath(Blade, Leaf)
        drawPath(Blade, LeafDark, style = Stroke(1.5f, join = StrokeJoin.Round))
    } else {
        drawPath(calyx, Leaf)
        drawPath(calyx, LeafDark, style = Stroke(1.6f, join = StrokeJoin.Round))
        drawLine(LeafDark, Offset(50f, 20f), Offset(52f, 9f), 4.5f, StrokeCap.Round)
    }
    rotate(-38f, Offset(30f, 40f)) { oval(30f, 40f, 10f, 5.5f, Color.White.copy(alpha = 0.45f)) }

    if (tomato.face == Face.None) return
    oval(34f, 66f, 6f, 3.4f, Blush.copy(alpha = 0.5f))
    oval(66f, 66f, 6f, 3.4f, Blush.copy(alpha = 0.5f))
    if (tomato.face == Face.Happy) {
        drawPath(curve(37f, 59f, 41f, 54f, 45f, 59f), FaceInk, style = Stroke(2.6f, cap = StrokeCap.Round))
        drawPath(curve(55f, 59f, 59f, 54f, 63f, 59f), FaceInk, style = Stroke(2.6f, cap = StrokeCap.Round))
    } else {
        oval(41f, 58f, 3.4f, 3.4f, FaceInk)
        oval(59f, 58f, 3.4f, 3.4f, FaceInk)
    }
    if (tomato.face == Face.Smile || tomato.face == Face.Happy) {
        drawPath(curve(45f, 66f, 50f, 72f, 55f, 66f), FaceInk, style = Stroke(2.4f, cap = StrokeCap.Round))
    }
    if (tomato.face == Face.Oh) oval(50f, 68f, 2.6f, 3.2f, FaceInk)
}

/**
 * A pile of tomatoes along the bottom of the screen, [PILE_HEIGHT] dp high; [drop] is how far
 * above it they rain in from. [fall] goes 0 to 1 as the pile drops away below the screen, [rise]
 * 0 to 1 as it rains back in from above and lands with bounces. Each tomato is a little behind the
 * one before it, which is what makes a pile and not a sheet. Every tomato is its own layer, drawn
 * once, so a frame of the motion only moves layers.
 */
@Composable
internal fun Tomatoes(
    width: Float,
    drop: Float,
    fall: Animatable<Float, AnimationVector1D>,
    rise: Animatable<Float, AnimationVector1D>,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val pieces = remember(width) { pile(width) }
    Box(modifier) {
        for (tomato in pieces) {
            Box(
                Modifier
                    .offset(tomato.x.dp, tomato.y.dp)
                    .size(tomato.size.dp)
                    .graphicsLayer {
                        val down = min(1f, max(0f, fall.value * (1f + SPREAD) - tomato.delay))
                        val up = min(1f, max(0f, rise.value * (1f + SPREAD) - tomato.delay))
                        rotationZ = tomato.tilt
                        when {
                            reduced -> alpha = 1f - down * (1f - up)
                            rise.value > 0f -> translationY = -drop * (1f - bounce(up)) * density
                            else -> translationY = FALL * down * down * density
                        }
                    }
                    .drawWithCache {
                        val colors = palette(tomato.kind)
                        val body = Brush.radialGradient(0f to colors[0], 0.5f to colors[1], 1f to colors[2], center = Offset(36f, 36f), radius = 70f)
                        val calyx = calyxPath(tomato.calyx)
                        onDrawBehind {
                            scale(size.width / 100f, size.height / 100f, pivot = Offset.Zero) { art(tomato, body, calyx) }
                        }
                    },
            )
        }
    }
}
