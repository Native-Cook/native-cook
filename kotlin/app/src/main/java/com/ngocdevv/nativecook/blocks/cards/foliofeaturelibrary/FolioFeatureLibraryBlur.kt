package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** What the frosted pill under the Explore list blurs: the list's recorded content and where that list sits in the window. */
internal class FolioBlurSource(val layer: GraphicsLayer) {
    var viewportInWindow: Offset = Offset.Zero
}

/**
 * `ProgressiveBlurViewport` with the settings this block gives it (light tint, 28 dp edges, top and
 * bottom): while [enabled] and the content overflows ([overflowing], read while drawing: taller than the
 * viewport by more than 4 dp), the top and bottom edges of the scrolling content blur progressively,
 * strongest at the edge, so text scrolling away dissolves instead of being cut.
 *
 * Three blurred copies of the content (Gaussian radius 3, 8 and 16 px, the expo-blur intensities 12, 32
 * and 64 divided by its reduction factor of 4) are each masked by the alpha ramp of the React Native file
 * (`maskAlpha`), tinted white at 9%, 25% and 50% (expo-blur's `light` tint, intensity / 128), and laid over
 * each other. Below API 31, which cannot blur a layer, the edges fade to [backgroundColor] instead.
 * With [alwaysRecord] the content is recorded into [contentLayer] even when no edge is blurred, for the
 * frosted pill that reads it.
 */
@Composable
internal fun FolioProgressiveBlurViewport(
    enabled: Boolean,
    overflowing: () -> Boolean,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    contentLayer: GraphicsLayer = rememberGraphicsLayer(),
    alwaysRecord: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shortBlur = rememberGraphicsLayer()
    val mediumBlur = rememberGraphicsLayer()
    val strongBlur = rememberGraphicsLayer()
    val blurLayers = remember(shortBlur, mediumBlur, strongBlur) { listOf(shortBlur, mediumBlur, strongBlur) }
    val ramps = remember { List(BANDS) { band -> rampStops(band) } }
    val maskPaint = remember { Paint() }

    Box(
        modifier = modifier.drawWithContent {
            // The React Native edge: 28 dp, kept within 0 to 56 and half the viewport's height.
            val edge = min(EDGE_DP.dp.toPx(), size.height / 2f)
            val blurred = enabled && edge > 0f && size.width > 0f && overflowing()
            if (!blurred && !alwaysRecord) {
                drawContent()
                return@drawWithContent
            }
            contentLayer.record { this@drawWithContent.drawContent() }
            drawLayer(contentLayer)
            if (!blurred) return@drawWithContent
            val top = Rect(0f, 0f, size.width, edge)
            val bottom = Rect(0f, size.height - edge, size.width, size.height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                drawBlurredEdges(contentLayer, blurLayers, ramps, maskPaint, top, bottom)
            } else {
                drawFadedEdges(backgroundColor, top, bottom)
            }
        },
        content = content,
    )
}

private fun ContentDrawScope.drawBlurredEdges(
    contentLayer: GraphicsLayer,
    blurLayers: List<GraphicsLayer>,
    ramps: List<Array<Pair<Float, Color>>>,
    maskPaint: Paint,
    top: Rect,
    bottom: Rect,
) {
    for (band in 0 until BANDS) {
        val layer = blurLayers[band]
        layer.renderEffect = BlurEffect(BLUR_PX[band], BLUR_PX[band], TileMode.Mirror)
        layer.record { drawLayer(contentLayer) }
        val tint = Color(249, 249, 249, TINT_ALPHA[band])
        for (edge in listOf(top, bottom)) {
            // Depth is 0 on the inner side of the strip and 1 at the viewport's edge.
            val innerY = if (edge === top) edge.bottom else edge.top
            val outerY = if (edge === top) edge.top else edge.bottom
            val mask = Brush.verticalGradient(*ramps[band], startY = innerY, endY = outerY)
            drawIntoCanvas { canvas ->
                canvas.saveLayer(edge, maskPaint)
                clipRect(edge.left, edge.top, edge.right, edge.bottom) { drawLayer(layer) }
                drawRect(color = tint, topLeft = edge.topLeft, size = edge.size)
                drawRect(brush = mask, topLeft = edge.topLeft, size = edge.size, blendMode = BlendMode.DstIn)
                canvas.restore()
            }
        }
    }
}

private fun ContentDrawScope.drawFadedEdges(backgroundColor: Color, top: Rect, bottom: Rect) {
    val clear = backgroundColor.copy(alpha = 0f)
    drawRect(
        brush = Brush.verticalGradient(listOf(backgroundColor, clear), startY = top.top, endY = top.bottom),
        topLeft = top.topLeft,
        size = top.size,
    )
    drawRect(
        brush = Brush.verticalGradient(listOf(clear, backgroundColor), startY = bottom.top, endY = bottom.bottom),
        topLeft = bottom.topLeft,
        size = bottom.size,
    )
}

/** The mask of one blur band as 65 evenly spaced white stops whose alpha is [maskAlpha] (`MASK_SAMPLES`). */
private fun rampStops(band: Int): Array<Pair<Float, Color>> = Array(RAMP_SAMPLES) { index ->
    val depth = index / (RAMP_SAMPLES - 1).toDouble()
    depth.toFloat() to Color.White.copy(alpha = maskAlpha(depth, band).toFloat())
}

private fun smoothstep(value: Double, start: Double, end: Double): Double {
    val t = ((value - start) / (end - start)).coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

/**
 * `maskAlpha`: the source-over alpha of band [band] at [depth], so the three bands add up to a smooth
 * blur strength without dark gaps where neighbouring profiles cross-fade.
 */
private fun maskAlpha(depth: Double, band: Int): Double {
    val coverage = smoothstep(depth, 0.0, 0.38)
    val medium = smoothstep(depth, 0.18, 0.65)
    val strong = smoothstep(depth, 0.52, 1.0)
    val weights = doubleArrayOf(coverage * (1 - medium), coverage * medium * (1 - strong), coverage * medium * strong)
    val above = when (band) {
        0 -> weights[1] + weights[2]
        1 -> weights[2]
        else -> 0.0
    }
    return if (1 - above > 0.00001) min(1.0, weights[band] / (1 - above)) else 0.0
}

private const val BANDS = 3
private const val RAMP_SAMPLES = 65
private const val EDGE_DP = 28f
private val BLUR_PX = floatArrayOf(3f, 8f, 16f)
private val TINT_ALPHA = intArrayOf(23, 63, 127)
