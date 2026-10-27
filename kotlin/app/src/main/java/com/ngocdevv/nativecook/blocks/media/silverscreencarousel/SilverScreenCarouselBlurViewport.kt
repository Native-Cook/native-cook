package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The three blur strengths of `ProgressiveBlurViewport` (SkiaProgressiveBlur.tsx:21). */
private val Intensities = intArrayOf(12, 32, 64)

/** Mask resolution: 65 samples along the edge (SkiaProgressiveBlur.tsx:22). */
private const val MaskSamples = 65

/** `edgeSize={32}` of the block's viewport (SilverScreenCarousel.tsx:103), kept within 0 to 56 dp. */
private const val EdgeDp = 32f

private fun smoothstep(value: Float, start: Float, end: Float): Float {
    val t = min(1f, max(0f, (value - start) / (end - start)))
    return t * t * (3f - 2f * t)
}

/** Alpha of one blur band at [depth] (0 inside, 1 at the screen edge): `maskAlpha`, SkiaProgressiveBlur.tsx:32. */
private fun maskAlpha(depth: Float, band: Int): Float {
    val coverage = smoothstep(depth, 0f, 0.38f)
    val medium = smoothstep(depth, 0.18f, 0.65f)
    val strong = smoothstep(depth, 0.52f, 1f)
    val weights = floatArrayOf(coverage * (1f - medium), coverage * medium * (1f - strong), coverage * medium * strong)
    // Convert a smooth partition into source-over alpha, ordered low to high.
    val above = when (band) {
        0 -> weights[1] + weights[2]
        1 -> weights[2]
        else -> 0f
    }
    return if (1f - above > 0.00001f) min(1f, weights[band] / (1f - above)) else 0f
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

/**
 * expo-blur's tint on Android: the tint colour at 78% of the intensity (TintStyle.kt). The dark tint
 * is taken as black; the light one, used by the sibling ports, is rgb(249, 249, 249).
 */
private fun darkTint(intensity: Int): Color = Color(0, 0, 0, (255 * (intensity / 100f) * 0.78f).toInt())

/**
 * `ProgressiveBlurViewport tint="dark" edgeSize={32}` (shared/SkiaProgressiveBlur.tsx), around the page's
 * scroll view: while the content overflows ([overflowing], read while drawing: the content is taller
 * than the viewport by more than 4 dp) the top and bottom edges blur progressively, strongest at the
 * screen's edge, so text scrolling away dissolves instead of being cut. Three blurred copies of the
 * content (radius 3, 8 and 16 px: the expo-blur intensities 12, 32 and 64 divided by its reduction
 * factor of 4) are each masked by the alpha ramp of the React Native file and tinted, and laid over each
 * other. Below API 31, which cannot blur a layer, the edges fade to [fade], the backdrop colour.
 */
@Composable
internal fun SilverScreenCarouselBlurViewport(
    overflowing: () -> Boolean,
    fade: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val contentLayer = rememberGraphicsLayer()
    // One copy per band and edge: the three of the top edge, then the three of the bottom edge.
    val bands = listOf(
        rememberGraphicsLayer(), rememberGraphicsLayer(), rememberGraphicsLayer(),
        rememberGraphicsLayer(), rememberGraphicsLayer(), rememberGraphicsLayer(),
    )
    val masks = remember { listOf(true, false).flatMap { top -> List(3) { band -> maskBrush(top, band) } } }
    Box(
        modifier = modifier
            .clipToBounds()
            .drawWithContent {
                val edge = min(EdgeDp.coerceIn(0f, 56f).dp.toPx(), size.height / 2f)
                if (edge <= 0f || size.width <= 0f || !overflowing()) {
                    drawContent()
                    return@drawWithContent
                }
                val full = IntSize(size.width.roundToInt(), size.height.roundToInt())
                contentLayer.record(this, layoutDirection, full) { this@drawWithContent.drawContent() }
                drawLayer(contentLayer)
                if (canBlur) {
                    drawBlurredEdge(contentLayer, bands, masks, top = true, edge = edge)
                    drawBlurredEdge(contentLayer, bands, masks, top = false, edge = edge)
                } else {
                    drawFade(fade, top = true, edge = edge)
                    drawFade(fade, top = false, edge = edge)
                }
            },
        content = content,
    )
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
        val padded = radius * 3f
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
                translate(0f, stripTop) { drawRect(darkTint(Intensities[band]), size = Size(size.width, edge)) }
            }
            translate(0f, stripTop) { drawRect(masks[index], size = Size(size.width, edge), blendMode = BlendMode.DstIn) }
            canvas.restore()
        }
    }
}

/** The pre-Android 12 stand-in: a gradient of the backdrop colour over the strip. */
private fun DrawScope.drawFade(color: Color, top: Boolean, edge: Float) {
    val stripTop = if (top) 0f else size.height - edge
    val clear = color.copy(alpha = 0f)
    val brush = if (top) Brush.verticalGradient(listOf(color, clear)) else Brush.verticalGradient(listOf(clear, color))
    translate(0f, stripTop) { drawRect(brush, size = Size(size.width, edge)) }
}
