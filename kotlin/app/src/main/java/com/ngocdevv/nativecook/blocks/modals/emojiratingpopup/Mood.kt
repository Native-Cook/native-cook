package com.ngocdevv.nativecook.blocks.modals.emojiratingpopup

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val FACE_SIZE = 84f
private const val CENTER = FACE_SIZE / 2
private const val RING = 39f
private const val DISC = 32f

// The features are drawn for a face 50 across and scaled to the disc.
private const val FEATURE_SCALE = DISC / 25f

private const val EYE_X = 8f
private const val EYE_Y = -4f
private val Ink = Color(0xFF3D2300)
private val HeartTop = Color(0xFFFF6B81)
private val HeartBottom = Color(0xFFE0193A)
private val MouthTop = Color(0xFF5A1020)
private val MouthBottom = Color(0xFF8E2034)
private val Tongue = Color(0xFFFF7D8F)
private val Blush = rgba(255, 110, 90, 0.38f)
private const val FACES = 5

private fun svg(data: String) = PathParser().parsePathString(data).toPath()

// Faces by place in the row, from the saddest to the happiest. The shapes sit around (0, 0).
private val Frown = svg("M-8 12Q0 4 8 12")
private val BrowLeft = svg("M-12 -12Q-8 -14 -4 -11")
private val BrowRight = svg("M12 -12Q8 -14 4 -11")
private val Flat = svg("M-7 9L7 9")
private val Smile = svg("M-9 6Q0 15 9 6")
private val HappyEye = svg("M-4 2Q0 -5 4 2")
private val OpenMouth = svg("M-12 2Q0 4 12 2Q11 18 0 18Q-11 18 -12 2Z")
private val GrinTeeth = svg("M-11.4 2.6Q0 4.6 11.4 2.6L11 6Q0 7.6 -11 6Z")
private val SmallOpenMouth = svg("M-9 5Q0 7 9 5Q8 16 0 16Q-8 16 -9 5Z")
private val SmallTeeth = svg("M-8.6 5.4Q0 7.4 8.6 5.4L8.3 8.2Q0 9.8 -8.3 8.2Z")
private val Heart = svg("M0 6.5C-10 0 -8 -9 0 -4.5C8 -9 10 0 0 6.5Z")

private val Line = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val BrowLine = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val MouthFill = Brush.verticalGradient(listOf(MouthTop, MouthBottom), startY = 2f, endY = 18f)
private val HeartFill = Brush.verticalGradient(listOf(HeartTop, HeartBottom), startY = -8f, endY = 7f)
private val DiscFill = Brush.radialGradient(
    0f to Color(0xFFFFE680),
    0.45f to Color(0xFFFFC53A),
    0.8f to Color(0xFFFF9A1F),
    1f to Color(0xFFF2731A),
    center = Offset(CENTER, CENTER - DISC * 0.35f),
    radius = DISC * 1.45f,
)

/** A soft-edged oval (a blur mask), drawn in the canvas's current units; plain below Android 9. */
private class SoftOval(color: Color, sigma: Float) {
    private val baseAlpha = color.alpha
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.copy(alpha = 1f).toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    fun DrawScope.draw(rect: Rect, alpha: Float = 1f) {
        paint.alpha = (baseAlpha * alpha * 255f).roundToInt().coerceIn(0, 255)
        drawIntoCanvas { it.nativeCanvas.drawOval(rect.left, rect.top, rect.right, rect.bottom, paint) }
    }
}

private val Gloss = SoftOval(Color.White.copy(alpha = 0.45f), 3f)
private val Cheek = SoftOval(Blush, 1.6f)

// The ring's shadow, as a soft disc under it.
private val RingShadow = SoftOval(rgba(120, 80, 0, 0.18f), 5f)

/**
 * The emoji in the dialog's header: a glossy yellow disc in a thin ring, with five expressions drawn
 * on top of each other. `face` runs from 0 (frown) to 4 (heart eyes); between two whole numbers the
 * neighbouring expressions cross-fade.
 */
@Composable
internal fun Mood(face: () -> Float, modifier: Modifier = Modifier) {
    // Clipped to its box like the Skia canvas, which cuts the ring's shadow at the edge.
    Canvas(modifier.size(FACE_SIZE.dp).clipToBounds()) {
        scale(density, density, pivot = Offset.Zero) {
            val c = Offset(CENTER, CENTER)
            with(RingShadow) { draw(Rect(Offset(CENTER, CENTER + 3f), RING)) }
            drawCircle(Color.White, RING, c)
            drawCircle(Color(0xFFF6D66A), RING - 1.5f, c, style = Stroke(2f))
            // The disc: warm yellow at the top to orange at the chin, a darker rim, a soft gloss.
            drawCircle(DiscFill, DISC, c)
            drawCircle(rgba(214, 110, 0, 0.35f), DISC - 0.5f, c, style = Stroke(1f))
            with(Gloss) {
                draw(Rect(Offset(CENTER - DISC * 0.55f, CENTER - DISC * 0.88f), Size(DISC * 1.1f, DISC * 0.5f)))
            }
            val now = face()
            for (place in 0 until FACES) {
                val opacity = (1f - abs(now - place)).coerceIn(0f, 1f)
                if (opacity <= 0f) continue
                translate(CENTER, CENTER) {
                    scale(FEATURE_SCALE, FEATURE_SCALE, pivot = Offset.Zero) { expression(place, opacity) }
                }
            }
        }
    }
}

private fun DrawScope.eyes(alpha: Float) {
    for (x in floatArrayOf(-EYE_X, EYE_X)) {
        drawOval(Ink, Offset(x - 2.6f, EYE_Y - 3.8f), Size(5.2f, 7.6f), alpha)
        drawCircle(Color.White.copy(alpha = 0.85f), 1.1f, Offset(x + 0.8f, EYE_Y - 1.8f), alpha)
    }
}

private fun DrawScope.stroke(path: Path, alpha: Float, dx: Float = 0f, dy: Float = 0f, line: Stroke = Line) =
    translate(dx, dy) { drawPath(path, Ink, alpha, line) }

private fun DrawScope.cheeks(alpha: Float) {
    with(Cheek) {
        draw(Rect(Offset(-17f, 3f), Size(8f, 5f)), alpha)
        draw(Rect(Offset(9f, 3f), Size(8f, 5f)), alpha)
    }
}

/** An open mouth with a dark gradient, a row of teeth and a tongue. */
private fun DrawScope.mouth(shape: Path, teeth: Path, tongueY: Float, tongueR: Float, alpha: Float) {
    clipPath(shape) {
        drawPath(shape, MouthFill, alpha)
        drawPath(teeth, Color.White, alpha)
        drawCircle(Tongue, tongueR, Offset(0f, tongueY), alpha)
    }
}

/** One expression, shown at full strength when `face` equals its place and faded out a step away. */
private fun DrawScope.expression(place: Int, alpha: Float) {
    when (place) {
        0 -> {
            stroke(BrowLeft, alpha, line = BrowLine)
            stroke(BrowRight, alpha, line = BrowLine)
            eyes(alpha)
            stroke(Frown, alpha)
        }
        1 -> {
            eyes(alpha)
            stroke(Flat, alpha)
        }
        2 -> {
            cheeks(alpha)
            eyes(alpha)
            stroke(Smile, alpha)
        }
        3 -> {
            cheeks(alpha)
            stroke(HappyEye, alpha, -EYE_X, EYE_Y - 1f)
            stroke(HappyEye, alpha, EYE_X, EYE_Y - 1f)
            mouth(OpenMouth, GrinTeeth, 19f, 6.5f, alpha)
        }
        else -> {
            cheeks(alpha)
            for (x in floatArrayOf(-EYE_X - 1f, EYE_X + 1f)) {
                translate(x, EYE_Y - 1f) {
                    drawPath(Heart, HeartFill, alpha)
                    drawOval(Color.White.copy(alpha = 0.7f), Offset(-5.5f, -5f), Size(3f, 2f), alpha)
                }
            }
            mouth(SmallOpenMouth, SmallTeeth, 17f, 5f, alpha)
        }
    }
}
