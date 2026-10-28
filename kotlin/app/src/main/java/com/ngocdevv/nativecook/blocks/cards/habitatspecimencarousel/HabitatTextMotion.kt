package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.min

// BlurEdgeTypeIn's numbers (blurEdgeMotion.ts), in ems of the text size.
private const val BLUR_EDGE_MAX_BLUR = 0.16f
private const val BLUR_EDGE_MAX_SHIFT = 0.3f

internal class Crossing(val from: Int, val to: Int, val erase: Float, val type: Float, val settled: Boolean)

/**
 * Erase-then-type timing over one crossing, sampled from the carousel position so a drag scrubs
 * it, a reversal is exact and the 2 -> 0 wrap is seamless. Blur and drift use BlurEdgeTypeIn's numbers.
 */
internal fun crossingFronts(position: Float, count: Int): Crossing {
    val wrapped = ((position % count) + count) % count
    val from = floor(wrapped).toInt() % count
    val f = wrapped - floor(wrapped)
    val erase = 1 - clamp(f / 0.55f)
    val type = clamp((f - 0.38f) / 0.62f)
    return Crossing(from, (from + 1) % count, erase, type, f < 0.0005f)
}

/**
 * A Gaussian blur of `sigma` pixels, as CSS `blur()` gives the reference's text. The platform takes a
 * radius and blurs by 0.57735 * radius + 0.5, so it cannot blur by less than half a pixel: from a
 * quarter to a half the smallest blur is the nearest there is, and below a quarter no blur is.
 */
internal fun sigmaBlur(sigma: Float): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || sigma < 0.25f) return null
    val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.001f)
    return BlurEffect(radius, radius, TileMode.Decal)
}

/** One value of a specimen, blurring out drifting left while the next sharpens in from the right. */
@Composable
private fun SwapLayer(index: Int, count: Int, height: Float, motion: HabitatMotion, content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .graphicsLayer {
                motion.observe()
                clip = false
                if (motion.reduced) {
                    val shown = jsRound(wrapPositionBy(motion.position, count)).toInt() % count == index
                    alpha = if (shown) 1f else 0f
                    translationX = 0f
                    renderEffect = null
                    return@graphicsLayer
                }
                val fronts = crossingFronts(motion.position, count)
                var u = 0f
                var direction = 0
                if (index == fronts.from) { u = fronts.erase; direction = -1 }
                else if (index == fronts.to && !fronts.settled) { u = fronts.type; direction = 1 }
                val eased = u * u * (3 - 2 * u)
                val k = (1 - eased) * (1 - eased)
                val em = min(height * 0.8f, 18f)
                alpha = eased
                renderEffect = sigmaBlur(BLUR_EDGE_MAX_BLUR * em * k * density)
                translationX = direction * BLUR_EDGE_MAX_SHIFT * em * k * density
            },
    ) { content() }
}

private fun wrapPositionBy(value: Float, count: Int): Float = ((value % count) + count) % count

/** Every other value that belongs to a specimen swaps with the same letter motion, as one block. */
@Composable
internal fun TextSwap(motion: HabitatMotion, height: Float, modifier: Modifier = Modifier, items: List<@Composable () -> Unit>) {
    Box(modifier.height(height.dp)) {
        items.forEachIndexed { index, item -> SwapLayer(index, items.size, height, motion, item) }
    }
}

/** The reference's `Text`: sizes are points (scaled by the system font size, optionally capped), letter spacing in points. */
@Composable
internal fun HText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f,
    lineHeight: Float? = null,
    align: TextAlign = TextAlign.Start,
    tabular: Boolean = false,
    maxFontScale: Float = Float.MAX_VALUE,
    maxLines: Int = Int.MAX_VALUE,
) {
    val fontScale = LocalDensity.current.fontScale
    val capped = min(fontScale, maxFontScale)
    // sp multiplies by the system font scale; the cap divides it back out.
    val factor = capped / fontScale
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        softWrap = maxLines == Int.MAX_VALUE,
        overflow = TextOverflow.Clip,
        style = TextStyle(
            color = color,
            fontSize = (size * factor).sp,
            fontWeight = weight,
            letterSpacing = (letterSpacing * factor).sp,
            lineHeight = if (lineHeight != null) (lineHeight * factor).sp else androidx.compose.ui.unit.TextUnit.Unspecified,
            textAlign = align,
            fontFeatureSettings = if (tabular) "tnum" else null,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
    )
}
