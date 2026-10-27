package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Samples of a mask gradient, as in `MASK_SAMPLES` of `SkiaProgressiveBlur.tsx`. */
private const val MASK_SAMPLES = 65

/** The three blur profiles (`INTENSITIES` 12, 32, 64) and their number. */
internal const val EMBER_BLUR_BANDS = 3

private fun smoothstep(value: Float, start: Float, end: Float): Float {
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * How much of band [band] (0 = lightest, 2 = strongest blur) shows at [depth] (0 on the inner side of the edge strip,
 * 1 at the viewport's edge). These are the weights `maskAlpha` partitions coverage into before it turns them into
 * source-over alphas: here the bands are added instead, so the weights themselves are the alphas and they sum to the
 * strip's coverage.
 */
internal fun emberBlurWeight(depth: Float, band: Int): Float {
    val coverage = smoothstep(depth, 0f, 0.38f)
    val medium = smoothstep(depth, 0.18f, 0.65f)
    val strong = smoothstep(depth, 0.52f, 1f)
    return when (band) {
        0 -> coverage * (1f - medium)
        1 -> coverage * medium * (1f - strong)
        else -> coverage * medium * strong
    }
}

/** The total blur coverage at [depth]: 0 leaves the sharp content, 1 replaces it entirely. */
internal fun emberBlurCoverage(depth: Float): Float = smoothstep(depth, 0f, 0.38f)

/**
 * The gradient masks of the three blur bands and of the sharp copy for one viewport size, rebuilt only when the
 * strip or the layer height changes (65 stops each, like the PNG masks of the React Native helper).
 * Lengths are in pixels. A band layer is [layerHeight] high and holds the [strip] at its outer end; the rest of the
 * layer (the blur's lead-in) is fully masked out.
 */
internal class EmberDayReviewBlurMasks {
    private var strip = -1f
    private var layerHeight = -1f
    private var bands: Array<Brush> = emptyArray()
    private var sharp: Array<Brush> = emptyArray()
    private var fades: Array<Brush> = emptyArray()

    /** The mask that multiplies a band layer: index = edge * 3 + band, edge 0 = top, 1 = bottom. */
    fun band(edge: Int, band: Int, strip: Float, layerHeight: Float): Brush {
        ensure(strip, layerHeight)
        return bands[edge * EMBER_BLUR_BANDS + band]
    }

    /** The mask that fades the sharp copy inside the strip (height [strip]): alpha = 1 - coverage. */
    fun sharp(edge: Int, strip: Float, layerHeight: Float): Brush {
        ensure(strip, layerHeight)
        return sharp[edge]
    }

    /** The plain white fade used below API 31, where there is no blur: alpha = coverage. */
    fun fade(edge: Int, strip: Float, layerHeight: Float): Brush {
        ensure(strip, layerHeight)
        return fades[edge]
    }

    private fun ensure(strip: Float, layerHeight: Float) {
        if (strip == this.strip && layerHeight == this.layerHeight) {
            return
        }
        this.strip = strip
        this.layerHeight = layerHeight
        bands = Array(2 * EMBER_BLUR_BANDS) { index ->
            val edge = index / EMBER_BLUR_BANDS
            val band = index % EMBER_BLUR_BANDS
            stripGradient(edge, strip, layerHeight) { depth -> emberBlurWeight(depth, band) }
        }
        sharp = Array(2) { edge ->
            stripGradient(edge, strip, strip) { depth -> 1f - emberBlurCoverage(depth) }
        }
        fades = Array(2) { edge ->
            stripGradient(edge, strip, strip) { depth -> emberBlurCoverage(depth) }
        }
    }

    /**
     * A vertical gradient over a layer of [height] whose last (bottom edge) or first (top edge) [strip] pixels carry
     * `alphaAt(depth)` and the rest 0. For the top edge depth is 1 at y = 0 and falls to 0 at y = strip; for the
     * bottom edge it is 0 at y = height - strip and 1 at the bottom.
     */
    private fun stripGradient(edge: Int, strip: Float, height: Float, alphaAt: (Float) -> Float): Brush {
        val stops = ArrayList<Pair<Float, Color>>(MASK_SAMPLES + 2)
        if (edge == 1 && height > strip) {
            stops.add(0f to Color.White.copy(alpha = 0f))
        }
        for (k in 0 until MASK_SAMPLES) {
            val fraction = k / (MASK_SAMPLES - 1).toFloat()
            val y = if (edge == 0) strip * fraction else (height - strip) + strip * fraction
            val depth = if (edge == 0) 1f - fraction else fraction
            val position = if (height > 0f) (y / height).coerceIn(0f, 1f) else 0f
            stops.add(position to Color.White.copy(alpha = alphaAt(depth).coerceIn(0f, 1f)))
        }
        if (edge == 0 && height > strip) {
            stops.add(1f to Color.White.copy(alpha = 0f))
        }
        return Brush.verticalGradient(*stops.toTypedArray(), startY = 0f, endY = height)
    }
}
