package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** `edgeSize={28}` of the viewport in BrightLessonRecap.tsx:127; the component clamps it to 0-56. */
private val EdgeSize = 28.dp

/** Blur radii of the three bands, in pixels: expo-blur's intensities 12, 32 and 64 divided by its reduction factor 4. */
private val BandRadiusPx = floatArrayOf(3f, 8f, 16f)

/** The light tint expo-blur lays over each band: 255 x intensity/100 x 0.78 alpha, in (249, 249, 249). */
private val BandTintAlpha = intArrayOf(23, 63, 127)

private const val MASK_SAMPLES = 65

private fun smoothstep(value: Float, start: Float, end: Float): Float {
    val t = max(0f, min(1f, (value - start) / (end - start)))
    return t * t * (3 - 2 * t)
}

/** `maskAlpha` of SkiaProgressiveBlur.tsx: the source-over alpha of [band] at [depth] (0 inside, 1 at the screen edge). */
private fun maskAlpha(depth: Float, band: Int): Float {
    val coverage = smoothstep(depth, 0f, 0.38f)
    val medium = smoothstep(depth, 0.18f, 0.65f)
    val strong = smoothstep(depth, 0.52f, 1f)
    val weights = floatArrayOf(coverage * (1 - medium), coverage * medium * (1 - strong), coverage * medium * strong)
    val above = when (band) {
        0 -> weights[1] + weights[2]
        1 -> weights[2]
        else -> 0f
    }
    return if (1 - above > 0.00001f) min(1f, weights[band] / (1 - above)) else 0f
}

/**
 * The 65-stop alpha ramp of a band along a strip's height. Depth 0 is the inner side, 1 the screen
 * edge, which is the top of the top strip and the bottom of the bottom one.
 */
private fun maskStops(band: Int, atTop: Boolean): Array<Pair<Float, Color>> = Array(MASK_SAMPLES) { k ->
    val i = if (atTop) MASK_SAMPLES - 1 - k else k
    val depth = i / (MASK_SAMPLES - 1).toFloat()
    val position = if (atTop) 1f - depth else depth
    position to Color.White.copy(alpha = maskAlpha(depth, band))
}

/**
 * `ProgressiveBlurViewport` (react-native/src/shared/SkiaProgressiveBlur.tsx) with `tint="light"`
 * and `edgeSize={28}` on the top and bottom edges. The content is recorded once into a graphics
 * layer; when it overflows the viewport ([overflowing]) three blurred copies of it, in three
 * strengths and each masked to its own part of a 28 dp strip, are drawn over each edge.
 * Below API 31, where there is no blur effect, the edge fades into the white background instead.
 */
@Composable
internal fun BrightLessonRecapBlurViewport(
    overflowing: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val feedLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val strip = min(EdgeSize.value, maxHeight.value / 2).dp
        val viewportPx = with(density) { maxHeight.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (overflowing) {
                        Modifier.drawWithContent {
                            feedLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(feedLayer)
                        }
                    } else {
                        Modifier
                    },
                ),
        ) { content() }
        if (overflowing) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                for (atTop in booleanArrayOf(true, false)) {
                    for (band in 0..2) {
                        BlurBand(atTop = atTop, band = band, strip = strip, viewportPx = viewportPx, feedLayer = feedLayer)
                    }
                }
            } else {
                FadeEdge(atTop = true, strip = strip)
                FadeEdge(atTop = false, strip = strip)
            }
        }
    }
}

/** One band of one edge: a blurred copy of the content, tinted, and cut by the band's alpha ramp. */
@Composable
private fun BoxScope.BlurBand(atTop: Boolean, band: Int, strip: Dp, viewportPx: Float, feedLayer: GraphicsLayer) {
    val stops = remember(band, atTop) { maskStops(band, atTop) }
    Box(
        Modifier
            .align(if (atTop) Alignment.TopStart else Alignment.BottomStart)
            .fillMaxWidth()
            .height(strip)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(colorStops = stops, startY = 0f, endY = size.height), blendMode = BlendMode.DstIn)
            },
    ) {
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                renderEffect = BlurEffect(BandRadiusPx[band], BandRadiusPx[band], TileMode.Clamp)
            },
        ) {
            drawRect(Color.White)
            translate(top = if (atTop) 0f else -(viewportPx - size.height)) { drawLayer(feedLayer) }
        }
        Box(Modifier.matchParentSize().background(Color(249, 249, 249, BandTintAlpha[band])))
    }
}

/** The fallback below API 31: the background colour, solid at the screen edge and clear inside. */
@Composable
private fun BoxScope.FadeEdge(atTop: Boolean, strip: Dp) {
    val colors = if (atTop) listOf(Color.White, Color.Transparent) else listOf(Color.Transparent, Color.White)
    Box(
        Modifier
            .align(if (atTop) Alignment.TopStart else Alignment.BottomStart)
            .fillMaxWidth()
            .height(strip)
            .background(Brush.verticalGradient(colors)),
    )
}
