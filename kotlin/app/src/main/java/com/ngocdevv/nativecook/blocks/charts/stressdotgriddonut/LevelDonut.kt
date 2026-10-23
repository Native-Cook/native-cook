package com.ngocdevv.nativecook.blocks.charts.stressdotgriddonut

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.max

private val DonutInk = Color.White
private const val STROKE = 22f

// The space between two segments, measured between their rounded ends.
private const val GAP = 6f
private const val GLOW_BLUR = 9f
private const val GLOW_ALPHA = 0.5f

/**
 * A ring split into rounded segments, one per level, whose lengths morph between two sets of shares.
 *
 * @param from Each segment's share in the first view.
 * @param to Its share in the second; the donut moves between them as [progress] goes 0 → 1.
 */
@Composable
internal fun LevelDonut(size: Float, colors: List<Color>, from: List<Float>, to: List<Float>, progress: () -> Float, label: String) {
    val radius = (size - STROKE) / 2 - GLOW_BLUR / 2
    val centre = size / 2
    // Round ends reach half a stroke past each segment, so the gap in degrees counts them in.
    val gapDegrees = ((GAP + STROKE) / radius * 180f / PI).toFloat()
    val oval = remember(size) { RectF(centre - radius, centre - radius, centre + radius, centre + radius) }
    val stroke = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = STROKE; strokeCap = Paint.Cap.ROUND } }
    // Hardware canvases honour mask filters from API 28; below it the ring has no glow.
    val glow = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = STROKE
                strokeCap = Paint.Cap.ROUND
                maskFilter = BlurMaskFilter(sigmaToRadius(GLOW_BLUR), BlurMaskFilter.Blur.NORMAL)
            }
        } else {
            null
        }
    }

    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        // Skia draws into the view's own surface, so the glow stops at its edges.
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            val p = progress()
            val shares = FloatArray(from.size) { max(0f, from[it] + (to[it] - from[it]) * p) }
            val total = shares.sum()
            val free = 360f - gapDegrees * from.size
            var start = -90f + gapDegrees / 2
            scale(density, density, pivot = Offset.Zero) {
                drawIntoCanvas { canvas ->
                    for (i in shares.indices) {
                        // A segment with almost nothing left is still a dot, as the round ends draw it.
                        val sweep = max(0.01f, if (total > 0) shares[i] / total * free else 0f)
                        val color = colors[i]
                        glow?.let {
                            it.color = color.copy(alpha = GLOW_ALPHA).toArgb()
                            canvas.nativeCanvas.drawArc(oval, start, sweep, false, it)
                        }
                        stroke.color = color.toArgb()
                        canvas.nativeCanvas.drawArc(oval, start, sweep, false, stroke)
                        start += sweep + gapDegrees
                    }
                }
            }
        }
        Text(label, style = stressText(15f, 20f, FontWeight.SemiBold, DonutInk))
    }
}
