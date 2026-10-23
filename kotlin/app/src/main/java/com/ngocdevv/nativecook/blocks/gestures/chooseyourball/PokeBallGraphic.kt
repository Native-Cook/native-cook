package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/** Height of the seam band, as a fraction of the ball's diameter. */
private const val BAND_RATIO = 0.115f
/** Diameters of the release button's housing and of its centre, as fractions of the ball's. */
private const val OUTER_BUTTON = 0.31f
private const val INNER_BUTTON = 0.16f
private val BAND_COLOR = Color.Black
private val LOWER_SHELL = listOf(Color(0xFFFFFFFF), Color(0xFFF7F7F7), Color(0xFFC8CBD0))

private fun opaque(rgb: Int): Color = Color(0xFF000000 or rgb.toLong())

/**
 * The ball as flat vector art (PokeBallGraphic.tsx), for the thumbnails of the strip: the pale
 * lower shell, the upper shell painted by the ball's `art` recipe, the seam band, the release
 * button (the strip draws it without), a rim and one specular sweep.
 *
 * Use it from `Modifier.drawWithCache`; the ball fills the square it is given.
 */
internal fun CacheDrawScope.pokeBallGraphic(ball: BallId, showButton: Boolean = true): DrawResult {
    val side = size.minDimension
    val center = side / 2f
    val radius = side / 2f
    val inset = 2.dp.toPx()
    val shell = opaque(ball.shell)
    val accent = opaque(ball.accent)
    val button = opaque(ball.button)

    val dome = Path().apply {
        moveTo(0f, center)
        arcTo(Rect(0f, 0f, side, side), 180f, 180f, false)
        lineTo(side, center)
        close()
    }
    val lowerBrush = Brush.linearGradient(LOWER_SHELL, Offset(side * 0.25f, side * 0.12f), Offset(side * 0.82f, side))
    val domeBrush = Brush.linearGradient(
        listOf(shell, opaque(ball.shellShade)),
        Offset(side * 0.22f, side * 0.08f),
        Offset(side * 0.82f, side * 0.58f),
    )
    val sweep = Path().apply {
        moveTo(side * 0.22f, side * 0.24f)
        cubicTo(side * 0.34f, side * 0.09f, side * 0.53f, side * 0.07f, side * 0.66f, side * 0.13f)
    }
    val markings = shellMarkings(ball.art, accent, side)

    return onDrawBehind {
        drawCircle(lowerBrush, radius - inset, Offset(center, center))
        clipPath(dome) {
            drawPath(dome, domeBrush)
            markings()
        }
        val bandHeight = side * BAND_RATIO
        drawRect(BAND_COLOR, Offset(0f, center - bandHeight / 2f), Size(side, bandHeight))
        if (showButton) {
            drawCircle(BAND_COLOR, side * OUTER_BUTTON / 2f, Offset(center, center))
            drawCircle(button, side * INNER_BUTTON / 2f, Offset(center, center))
        }
        drawCircle(Color.White.copy(alpha = 0.72f), radius - inset, Offset(center, center), style = Stroke(inset))
        drawPath(sweep, Color.White.copy(alpha = 0.30f), style = Stroke(side * 0.028f, cap = StrokeCap.Round))
    }
}

/** The marking of the upper shell; drawn clipped to the dome. */
private fun shellMarkings(art: BallArt, accent: Color, side: Float): DrawScope.() -> Unit {
    val center = side / 2f
    return when (art) {
        BallArt.SOLID -> {
            val draw: DrawScope.() -> Unit = {}
            draw
        }
        BallArt.STRIPES -> {
            val stripe = side * 0.11f
            val draw: DrawScope.() -> Unit = {
                drawRect(accent, Offset(side * 0.2f, 0f), Size(stripe, side))
                drawRect(accent, Offset(side * 0.69f - stripe / 2f, 0f), Size(stripe, side))
            }
            draw
        }
        BallArt.WEDGE -> {
            val left = Path().apply {
                moveTo(0f, side * 0.06f); lineTo(side * 0.36f, center); lineTo(0f, center); close()
            }
            val right = Path().apply {
                moveTo(side, side * 0.06f); lineTo(side * 0.64f, center); lineTo(side, center); close()
            }
            val draw: DrawScope.() -> Unit = {
                drawPath(left, accent)
                drawPath(right, accent)
            }
            draw
        }
        BallArt.BAND -> {
            val draw: DrawScope.() -> Unit = {
                drawRect(accent, Offset(0f, side * 0.26f - side * 0.0425f), Size(side, side * 0.085f))
            }
            draw
        }
        BallArt.EMBLEM -> {
            // The Master Ball's mark: an M with a dot on each side.
            val w = side * 0.24f
            val h = side * 0.13f
            val cy = side * 0.26f
            val m = Path().apply {
                moveTo(center - w / 2f, cy + h / 2f)
                lineTo(center - w / 2f, cy - h / 2f)
                lineTo(center, cy + h * 0.22f)
                lineTo(center + w / 2f, cy - h / 2f)
                lineTo(center + w / 2f, cy + h / 2f)
            }
            val draw: DrawScope.() -> Unit = {
                drawPath(m, accent, style = Stroke(side * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(accent, side * 0.034f, Offset(center - side * 0.21f, cy))
                drawCircle(accent, side * 0.034f, Offset(center + side * 0.21f, cy))
            }
            draw
        }
        BallArt.CREST -> {
            val glow = Brush.radialGradient(
                listOf(accent, accent.copy(alpha = 0f)),
                center = Offset(center, side * 0.22f),
                radius = side * 0.13f,
            )
            val draw: DrawScope.() -> Unit = {
                drawCircle(glow, side * 0.1f, Offset(center, side * 0.25f))
                drawCircle(accent, side * 0.075f, Offset(center, side * 0.25f), style = Stroke(side * 0.022f))
            }
            draw
        }
    }
}
