package com.ngocdevv.nativecook.blocks.inputs.modelpickerprompt

import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A filled path with a soft edge, for the glow under the mark. Drawn in the current (dp) units. */
private class SoftPath(color: Color, sigma: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    fun draw(canvas: android.graphics.Canvas, path: Path) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        canvas.drawPath(path.asAndroidPath(), paint)
    }
}

/**
 * One four-pointed star of the mark, around the centre of a [box]-wide square. [pinch] pulls the
 * sides towards the centre: 0 is a needle-thin star, 0.5 a diamond.
 */
private fun Path.star(box: Float, radius: Float, turn: Float, pinch: Float) {
    val mid = box / 2f
    val r = radius * box
    val quarter = (PI / 2).toFloat()
    moveTo(mid + cos(turn) * r, mid + sin(turn) * r)
    for (step in 1..4) {
        val angle = turn + step * quarter
        val bend = angle - quarter / 2f
        val pull = r * pinch * sqrt(2f)
        quadraticTo(mid + cos(bend) * pull, mid + sin(bend) * pull, mid + cos(angle) * r, mid + sin(angle) * r)
    }
    close()
}

/**
 * The assistant's mark: a four-pointed star with a smaller one turned 45° behind it, like a compass
 * rose, over a soft [glow] of its own shape.
 */
@Composable
internal fun MarkIcon(size: Float, color: Color, glow: Color, glowSigma: Float) {
    val path = remember(size) {
        Path().apply {
            star(size, radius = 0.5f, turn = 0f, pinch = 0.1f)
            star(size, radius = 0.3f, turn = (PI / 4).toFloat(), pinch = 0.16f)
        }
    }
    val halo = remember(glow, glowSigma) { SoftPath(glow, glowSigma) }
    Canvas(Modifier.size(size.dp)) {
        surface(0.dp) { _, _, _, _ ->
            drawIntoCanvas { halo.draw(it.nativeCanvas, path) }
            drawPath(path, color)
        }
    }
}

/** Five bars of a voice waveform in an 18 × 18 box. */
@Composable
internal fun WaveformIcon(color: Color) {
    val path = remember {
        Path().apply {
            floatArrayOf(6f, 12f, 16f, 9f, 5f).forEachIndexed { index, height ->
                val left = 1.1f + index * 3.5f
                val top = 9f - height / 2f
                addRoundRect(RoundRect(Rect(left, top, left + 1.8f, top + height), CornerRadius(0.9f)))
            }
        }
    }
    Canvas(Modifier.size(18.dp)) {
        surface(0.dp) { _, _, _, _ -> drawPath(path, color) }
    }
}

/** Up arrow in the 30 × 30 send button. */
@Composable
internal fun ArrowIcon(color: Color, modifier: Modifier = Modifier) {
    val path = remember {
        Path().apply {
            moveTo(15f, 21f)
            lineTo(15f, 9.5f)
            moveTo(10f, 14.25f)
            lineTo(15f, 9.25f)
            lineTo(20f, 14.25f)
        }
    }
    Canvas(modifier.size(30.dp)) {
        surface(0.dp) { _, _, _, _ ->
            drawPath(path, color, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Chevron pointing down in an 8 × 5 box. [color] is read while drawing, so it can be animated. */
@Composable
internal fun ChevronIcon(color: () -> Color, modifier: Modifier = Modifier) {
    val path = remember {
        Path().apply {
            moveTo(0.5f, 0.75f)
            lineTo(4f, 4.25f)
            lineTo(7.5f, 0.75f)
        }
    }
    Canvas(modifier.size(8.dp, 5.dp)) {
        surface(0.dp) { _, _, _, _ ->
            drawPath(path, color(), style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Check mark in a 12 × 10 box. */
@Composable
internal fun CheckIcon(color: Color, modifier: Modifier = Modifier) {
    val path = remember {
        Path().apply {
            moveTo(1f, 5.4f)
            lineTo(4.4f, 8.8f)
            lineTo(11f, 1.2f)
        }
    }
    Canvas(modifier.size(12.dp, 10.dp)) {
        surface(0.dp) { _, _, _, _ ->
            drawPath(path, color, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The chips' outline icons in a 13 × 13 box: a sparkle, a pencil, an open book and code brackets. */
@Composable
internal fun ChipIcon(kind: Int, color: Color) {
    val path = remember(kind) {
        Path().apply {
            when (kind % 4) {
                0 -> {
                    moveTo(6.5f, 1f)
                    quadraticTo(7.2f, 5.8f, 12f, 6.5f)
                    quadraticTo(7.2f, 7.2f, 6.5f, 12f)
                    quadraticTo(5.8f, 7.2f, 1f, 6.5f)
                    quadraticTo(5.8f, 5.8f, 6.5f, 1f)
                    close()
                }
                1 -> {
                    moveTo(1.8f, 11.2f)
                    lineTo(2.5f, 8.3f)
                    lineTo(8.9f, 1.9f)
                    lineTo(11.1f, 4.1f)
                    lineTo(4.7f, 10.5f)
                    close()
                    moveTo(7.4f, 3.4f)
                    lineTo(9.6f, 5.6f)
                }
                2 -> {
                    moveTo(6.5f, 3.4f)
                    quadraticTo(4.4f, 2.1f, 1.5f, 2.6f)
                    lineTo(1.5f, 10.2f)
                    quadraticTo(4.4f, 9.7f, 6.5f, 11f)
                    quadraticTo(8.6f, 9.7f, 11.5f, 10.2f)
                    lineTo(11.5f, 2.6f)
                    quadraticTo(8.6f, 2.1f, 6.5f, 3.4f)
                    lineTo(6.5f, 11f)
                }
                else -> {
                    moveTo(4.4f, 3.2f)
                    lineTo(1.2f, 6.5f)
                    lineTo(4.4f, 9.8f)
                    moveTo(8.6f, 3.2f)
                    lineTo(11.8f, 6.5f)
                    lineTo(8.6f, 9.8f)
                }
            }
        }
    }
    Canvas(Modifier.size(13.dp)) {
        surface(0.dp) { _, _, _, _ ->
            drawPath(path, color, style = Stroke(1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
