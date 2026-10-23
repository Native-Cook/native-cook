package com.ngocdevv.nativecook.blocks.forms.loanstepquestions

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.ceil
import kotlin.math.max

const val TICK_STEP = 10f
private const val TALL_EVERY = 5
private const val TICK = 14f
private const val TICK_TALL = 22f
private const val TICK_WIDTH = 1.2f
private const val MARKER_WIDTH = 5f
private const val MARKER_HEIGHT = 40f
private const val FADE = 44f

/** A repeating strip needs only its remainder; the marker stays fixed over it. */
class RulerArt(left: Float, right: Float, centre: Float) {
    private val strip = Path().apply {
        val count = ceil(max(centre - left, right - centre) / TICK_STEP).toInt() + TALL_EVERY
        for (index in -count..count) {
            val half = (if (index % TALL_EVERY == 0) TICK_TALL else TICK) / 2f
            moveTo(index * TICK_STEP, -half)
            lineTo(index * TICK_STEP, half)
        }
    }

    fun draw(scope: DrawScope, offset: Float, left: Float, right: Float, centre: Float, y: Float, tickColor: Color, markerColor: Color, background: Color) = with(scope) {
        val period = TICK_STEP * TALL_EVERY
        val pulled = ((offset % period) + period) % period
        clipRect(left, y - TICK_TALL, right, y + TICK_TALL) {
            translate(centre - pulled, y) {
                drawPath(strip, tickColor, style = Stroke(TICK_WIDTH, cap = StrokeCap.Round))
            }
        }
        val clear = background.copy(alpha = 0f)
        drawRect(Brush.horizontalGradient(listOf(background, clear), left, left + FADE), Offset(left, y - TICK_TALL), Size(FADE, TICK_TALL * 2f))
        drawRect(Brush.horizontalGradient(listOf(clear, background), right - FADE, right), Offset(right - FADE, y - TICK_TALL), Size(FADE, TICK_TALL * 2f))
        drawRoundRect(markerColor, Offset(centre - MARKER_WIDTH / 2f, y - MARKER_HEIGHT / 2f), Size(MARKER_WIDTH, MARKER_HEIGHT), CornerRadius(MARKER_WIDTH / 2f))
    }
}

@Composable
fun Ruler(offset: State<Float>, left: Float, right: Float, centre: Float, y: Float, tickColor: Color, markerColor: Color, background: Color, modifier: Modifier = Modifier) {
    val art = remember(left, right, centre) { RulerArt(left, right, centre) }
    Canvas(modifier) {
        surface(0.dp) { _, _, _, _ ->
            art.draw(this, offset.value, left, right, centre, y, tickColor, markerColor, background)
        }
    }
}
