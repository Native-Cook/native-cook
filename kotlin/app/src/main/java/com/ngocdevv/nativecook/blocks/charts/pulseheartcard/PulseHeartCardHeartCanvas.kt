package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.charts.pulseheartcard.PulseHeartCardTheme.Colors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The pin's heart outline as a polar cosine series c_0...c_13 around the centre, in ref pt (heart-shader.ts). */
private val HeartSeries = doubleArrayOf(
    114.202, 15.914, -2.614, -14.183, -4.316, -4.079, -0.688, -1.807, -0.379, -1.124, -0.233, -0.716, -0.107, -0.477,
)
private const val CENTRE_X = 201.1667f
private const val CENTRE_Y = 290f

/**
 * The heart, its halo and the ECG trace: one AGSL RuntimeShader over the canvas (API 33+), redrawn
 * only when the engine's [tick] changes. Below API 33 a static heart is drawn instead.
 */
@Composable
internal fun PulseHeartCardHeartCanvas(
    engine: PulseHeartCardEngine,
    tick: State<Long>,
    scale: Float,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val canvasHeight = ((PulseHeartCardTheme.Layout.HEART_CANVAS_BOTTOM - PulseHeartCardTheme.Layout.HEART_CANVAS_TOP) * scale).dp
    val shader = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PulseHeartCardShader() else null }
    Spacer(
        modifier
            .size(width, canvasHeight)
            .drawBehind {
                val tickValue = tick.value
                val frame = engine.frame
                check(tickValue >= 0)
                val pixelScale = scale * density
                if (shader != null) {
                    shader.update(frame, pixelScale)
                    drawRect(ShaderBrush(shader.shader))
                } else {
                    drawStaticHeart(frame, pixelScale)
                }
            },
    )
}

/**
 * Fallback for API 26-32 (no AGSL): the rest heart from the same outline series with a vertical
 * gradient taken from the colour fit, a flat pink halo, and the beat's body scale and hop.
 * No gloss, ripples or ECG trace.
 */
private fun DrawScope.drawStaticHeart(frame: PulseHeartCardFrame, u: Float) {
    val body = 1f + frame.beat.body.toFloat()
    val top = PulseHeartCardTheme.Layout.HEART_CANVAS_TOP
    val outline = Path()
    for (step in 0..180) {
        val theta = 2 * PI * step / 180
        var r = 0.0
        for (k in HeartSeries.indices) r += HeartSeries[k] * cos(k * theta)
        val x = CENTRE_X + (r * sin(theta)).toFloat()
        val y = CENTRE_Y - (r * cos(theta)).toFloat() - top
        if (step == 0) outline.moveTo(x * u, y * u) else outline.lineTo(x * u, y * u)
    }
    outline.close()
    withTransform({
        translate(left = 0f, top = frame.beat.lift.toFloat() * u)
        scale(body, body, pivot = Offset(CENTRE_X * u, (CENTRE_Y - top) * u))
    }) {
        // Halo: the heart's red at a low alpha, a plateau about 40 pt out.
        drawPath(outline, Colors.heart.copy(alpha = 0.045f), style = Stroke(width = 80f * u, join = StrokeJoin.Round))
        drawPath(
            outline,
            Brush.verticalGradient(
                0f to Color(254, 125, 146),
                1f to Color(253, 97, 119),
                startY = (172f - top) * u,
                endY = (404f - top) * u,
            ),
        )
    }
}
