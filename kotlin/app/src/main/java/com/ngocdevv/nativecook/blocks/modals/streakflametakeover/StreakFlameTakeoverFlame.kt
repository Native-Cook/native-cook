package com.ngocdevv.nativecook.blocks.modals.streakflametakeover

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

internal const val FLAME_SIZE = 180f

/** Everything is drawn in a 200-point box and scaled to the flame's size. */
private const val BOX = 200f
private val FrameColor = Color(0xFF4A1A10)
private const val GLINT_WIDTH = 9f

private fun svg(data: String): Path = PathParser().parsePathString(data).toPath()

// The gold flame: a round body with three tips swept up and to the right.
private val Outer = svg(
    "M100 190C58 190 28 160 28 120C28 92 40 70 58 54C62 70 70 76 78 74C76 54 84 34 104 18C104 38 112 50 122 52C124 36 134 22 152 12C146 32 148 48 156 58C162 50 170 44 182 42C172 58 168 72 170 88C174 100 172 110 172 120C172 160 142 190 100 190Z",
)

// The hot little character inside it, with two ears, a waving arm and a foot.
private val Body = svg(
    "M62 150C60 120 63 96 72 82C76 70 90 66 98 78C100 80 104 80 106 78C114 66 128 70 132 82C142 98 144 124 140 150C136 172 120 182 100 182C80 182 64 172 62 150Z",
)
private val Arm = svg("M138 124C150 118 158 108 160 98C166 102 166 116 156 126C151 131 144 134 139 134Z")
private val Lenses = svg(
    "M66 110L100 110L98 124C96 132 91 135 84 135L77 135C70 135 66 130 66 123Z" + "M106 110L146 108L144 122C142 131 136 135 128 135L117 135C110 135 106 130 106 123Z",
)
private val Brow = svg("M60 104L152 101L152 110L60 113Z")
private val Bridge = svg("M98 112H108V117H98Z")
private val Stripes = svg("M80 106L90 106L76 138L66 138ZM124 104L136 104L120 138L108 138Z")
private val SparklePath = svg("M158 86Q160 96 170 98Q160 100 158 110Q156 100 146 98Q156 96 158 86Z")

/** The glint leans forward: x moves left by half of y. */
private val Lean = Matrix().apply { values[Matrix.SkewX] = -0.5f }

/**
 * The flame mascot: a gold flame round a hot little character in shades, with a glint that sweeps
 * the lenses ([glint], 0 to 1) and a sparkle by them ([sparkle] is its opacity).
 */
@Composable
internal fun Flame(glint: () -> Float, sparkle: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.width / BOX
        scale(unit, unit, pivot = Offset.Zero) {
            drawOval(Color(255, 200, 0).copy(alpha = 0.22f), Offset(52f, 180f), Size(96f, 18f))
            drawPath(
                Outer,
                Brush.radialGradient(
                    0f to Color(0xFFFFF6B0), 0.38f to Color(0xFFFFDB1F), 0.75f to Color(0xFFFFBD00), 1f to Color(0xFFF59E00),
                    center = Offset(90f, 112f), radius = 104f,
                ),
            )
            drawPath(Outer, Color(255, 250, 210).copy(alpha = 0.5f), style = Stroke(2f))
            drawOval(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)), center = Offset(100f, 78f), radius = 44f),
                Offset(58f, 46f), Size(84f, 64f), alpha = 0.55f,
            )

            drawPath(Arm, Brush.linearGradient(listOf(Color(0xFFFF8A34), Color(0xFFE8471A)), start = Offset(140f, 98f), end = Offset(160f, 134f)))
            drawOval(Color(0xFFD93D14), Offset(110f, 176f), Size(22f, 11f))
            drawPath(Body, Brush.verticalGradient(0f to Color(0xFFFFA040), 0.5f to Color(0xFFF45A1C), 1f to Color(0xFFD63A12), startY = 70f, endY = 182f))
            rotate(-0.5f * 180f / Math.PI.toFloat(), Offset(83f, 92f)) {
                drawOval(Color.White.copy(alpha = 0.32f), Offset(72f, 86f), Size(22f, 12f))
            }
            drawOval(Color(150, 30, 0).copy(alpha = 0.18f), Offset(68f, 146f), Size(64f, 30f))

            // The shades: a heavy brow bar, two cream lenses with a dark band, and a moving glint.
            drawPath(Lenses, Brush.verticalGradient(listOf(Color(0xFFFFF6E4), Color(0xFFF2CF98)), startY = 110f, endY = 135f))
            clipPath(Lenses) {
                drawPath(Stripes, FrameColor, alpha = 0.88f)
                withTransform({
                    translate(56f + glint() * 100f, 0f)
                    transform(Lean)
                }) {
                    drawRect(Color.White.copy(alpha = 0.9f), Offset(0f, 104f), Size(GLINT_WIDTH, 36f))
                }
            }
            drawPath(Lenses, FrameColor, style = Stroke(4.5f, join = StrokeJoin.Round))
            drawPath(Brow, FrameColor)
            drawPath(Bridge, FrameColor)
            val shine = sparkle()
            if (shine > 0f) drawPath(SparklePath, Color.White, alpha = shine)
        }
    }
}

internal const val WINGS_WIDTH = 300f
internal const val WINGS_HEIGHT = 140f

// One feathered wing; the other is the same, mirrored.
private val Wing = svg("M148 84C122 50 82 30 18 34C34 44 40 50 46 58C26 60 12 68 4 82C24 82 38 86 46 90C30 96 20 106 16 118C52 116 94 108 148 98Z")
private val Feathers = svg("M46 58C70 62 100 70 128 82M46 90C74 90 104 90 132 92")
private val WingFill = Brush.linearGradient(
    0f to Color(0xFFFFF08A), 0.5f to Color(0xFFFFC800), 1f to Color(0xFFF2A100),
    start = Offset(10f, 40f), end = Offset(150f, 100f),
)

/** A pair of gold wings that flap once behind the flame as it pops. */
@Composable
internal fun Wings(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(density, density, pivot = Offset.Zero) {
            for (side in listOf(1f, -1f)) {
                scale(side, 1f, pivot = Offset(WINGS_WIDTH / 2f, 0f)) {
                    drawPath(Wing, WingFill)
                    drawPath(Feathers, Color(214, 130, 0).copy(alpha = 0.55f), style = Stroke(2.5f, cap = StrokeCap.Round))
                }
            }
        }
    }
}
