package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The three blur strengths of `ProgressiveBlurViewport` (SkiaProgressiveBlur.tsx:21). */
private val Intensities = intArrayOf(12, 32, 64)

/** Mask resolution: 65 samples along the edge (SkiaProgressiveBlur.tsx:22). */
private const val MaskSamples = 65

private fun smoothstep(value: Float, start: Float, end: Float): Float {
    val t = min(1f, max(0f, (value - start) / (end - start)))
    return t * t * (3 - 2 * t)
}

/** Alpha of one blur band at [depth] (0 inside, 1 at the screen edge): SkiaProgressiveBlur.tsx:32. */
private fun maskAlpha(depth: Float, band: Int): Float {
    val coverage = smoothstep(depth, 0f, 0.38f)
    val medium = smoothstep(depth, 0.18f, 0.65f)
    val strong = smoothstep(depth, 0.52f, 1f)
    val weights = floatArrayOf(coverage * (1 - medium), coverage * medium * (1 - strong), coverage * medium * strong)
    // Convert a smooth partition into source-over alpha, ordered low to high.
    val above = if (band == 0) weights[1] + weights[2] else if (band == 1) weights[2] else 0f
    return if (1 - above > 0.00001f) min(1f, weights[band] / (1 - above)) else 0f
}

/** The white-with-alpha mask of a band; the strip's top is the outer edge when [top] is true. */
private fun maskBrush(top: Boolean, band: Int): Brush {
    val stops = Array(MaskSamples) { index ->
        val position = index / (MaskSamples - 1).toFloat()
        val depth = if (top) 1f - position else position
        position to Color(1f, 1f, 1f, maskAlpha(depth, band))
    }
    return Brush.verticalGradient(colorStops = stops)
}

/** expo-blur's "light" tint on Android: rgb(249, 249, 249) at 78% of the intensity (TintStyle.kt). */
private fun lightTint(intensity: Int): Color = Color(249, 249, 249, (255 * (intensity / 100f) * 0.78f).toInt())

/**
 * `ProgressiveBlurViewport` (shared/SkiaProgressiveBlur.tsx): a scroll view whose top and bottom
 * [edgeSize] fade into a blur, drawn only while the content overflows (more than 4 dp). Three
 * blurred copies of the content (radius 3, 8 and 16 px, tinted light) are masked by gradients and
 * stacked. Needs Android 12 for the blur; below it a plain fade of [fade] (the backdrop colour)
 * takes its place.
 */
@Composable
internal fun ConfettiGiftOfferBlurViewport(
    scrollState: ScrollState,
    enabled: Boolean,
    fade: Color,
    modifier: Modifier = Modifier,
    edgeSize: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val overflows by remember(scrollState, density) {
        derivedStateOf { scrollState.maxValue != Int.MAX_VALUE && scrollState.maxValue > 4f * density.density }
    }
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val contentLayer = rememberGraphicsLayer()
    val bands = listOf(
        rememberGraphicsLayer(), rememberGraphicsLayer(), rememberGraphicsLayer(),
        rememberGraphicsLayer(), rememberGraphicsLayer(), rememberGraphicsLayer(),
    )
    val masks = remember { listOf(true, false).flatMap { top -> List(3) { band -> maskBrush(top, band) } } }
    val bounded = edgeSize.coerceIn(0.dp, 56.dp)
    Box(
        modifier
            .clipToBounds()
            .drawWithContent {
                if (!enabled || !overflows || bounded <= 0.dp) {
                    drawContent()
                    return@drawWithContent
                }
                val full = IntSize(size.width.roundToInt(), size.height.roundToInt())
                contentLayer.record(this, layoutDirection, full) { this@drawWithContent.drawContent() }
                drawLayer(contentLayer)
                val edge = min(bounded.toPx(), size.height / 2f)
                if (canBlur) {
                    drawBlurredEdge(contentLayer, bands, masks, top = true, edge = edge)
                    drawBlurredEdge(contentLayer, bands, masks, top = false, edge = edge)
                } else {
                    drawFade(fade, top = true, edge = edge)
                    drawFade(fade, top = false, edge = edge)
                }
            },
    ) { content() }
}

/** Draws the three masked, blurred, tinted copies of the content over one edge strip. */
private fun DrawScope.drawBlurredEdge(
    content: GraphicsLayer,
    bands: List<GraphicsLayer>,
    masks: List<Brush>,
    top: Boolean,
    edge: Float,
) {
    val stripTop = if (top) 0f else size.height - edge
    for (band in 0 until 3) {
        val index = (if (top) 0 else 3) + band
        val layer = bands[index]
        val radius = Intensities[band] / 4f
        // Pad the copy so the blur samples what lies beyond the strip, as a backdrop blur does.
        val padded = ceilPad(radius)
        val copyTop = max(0f, stripTop - padded)
        val copyBottom = min(size.height, stripTop + edge + padded)
        layer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
        layer.record(this, layoutDirection, IntSize(size.width.roundToInt(), (copyBottom - copyTop).roundToInt())) {
            translate(0f, -copyTop) { drawLayer(content) }
        }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, stripTop, size.width, stripTop + edge), Paint())
            clipRect(0f, stripTop, size.width, stripTop + edge) {
                translate(0f, copyTop) { drawLayer(layer) }
                translate(0f, stripTop) { drawRect(lightTint(Intensities[band]), size = Size(size.width, edge)) }
            }
            translate(0f, stripTop) { drawRect(masks[index], size = Size(size.width, edge), blendMode = BlendMode.DstIn) }
            canvas.restore()
        }
    }
}

private fun ceilPad(radius: Float): Float = radius * 3f

/** The pre-Android 12 stand-in: a gradient of the backdrop colour over the strip. */
private fun DrawScope.drawFade(color: Color, top: Boolean, edge: Float) {
    val stripTop = if (top) 0f else size.height - edge
    val from = color.copy(alpha = 0.92f)
    val to = color.copy(alpha = 0f)
    val brush = if (top) Brush.verticalGradient(listOf(from, to)) else Brush.verticalGradient(listOf(to, from))
    translate(0f, stripTop) { drawRect(brush, size = Size(size.width, edge)) }
}
