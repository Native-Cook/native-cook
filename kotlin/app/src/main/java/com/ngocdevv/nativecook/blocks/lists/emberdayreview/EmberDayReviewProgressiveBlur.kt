package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** `edgeSize={28}`: the height of each blurred edge strip, in dp. */
private const val EDGE_DP = 28f

/** A blur samples beyond the strip; the layers carry this much real content past the strip's inner side so the strip is not smeared by an edge clamp. */
private const val LEAD_IN_DP = 24f

/** `contentSize.height > size.height + 4`: the edges show only when the content overflows by more than 4 dp. */
private const val OVERFLOW_SLACK_DP = 4f

/** Blur sigma per band in dp, standing for the `INTENSITIES` 12, 32 and 64 of the React Native helper (about intensity / 5). */
private val BandSigmaDp = floatArrayOf(2.5f, 6f, 12f)

/** The white veil of `tint="light"` per band, laid over blurred content only. */
private val BandTint = floatArrayOf(0.07f, 0.18f, 0.35f)

/**
 * `ProgressiveBlurViewport` (tint light, edges top and bottom, `edgeSize` 28, `onlyWhenOverflow`) around a scrolling [content].
 * While [enabled] and the content overflows, the content is drawn through a stack of layers: a copy that fades out
 * inside each 28 dp edge strip, plus three blurred copies (three profiles, as in React Native) whose masks are the
 * same smoothstep partition of the strip, added together. On API 31+ the blur is a render effect on a recorded layer
 * of the content; below it the strips get a plain white fade instead. Nothing here recomposes while the content moves.
 */
@Composable
internal fun EmberDayReviewProgressiveBlur(
    enabled: Boolean,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val contentLayer = rememberGraphicsLayer()
    val sharpLayer = rememberGraphicsLayer()
    val compositeLayer = rememberGraphicsLayer()
    val blurLayers = List(2 * EMBER_BLUR_BANDS) { rememberGraphicsLayer() }
    val maskLayers = List(2 * EMBER_BLUR_BANDS) { rememberGraphicsLayer() }
    val masks = remember { EmberDayReviewBlurMasks() }
    Box(
        modifier.drawWithContent {
            if (!enabled || scroll.maxValue.toFloat() <= OVERFLOW_SLACK_DP * density) {
                drawContent()
                return@drawWithContent
            }
            val width = size.width
            val height = size.height
            val strip = min(EDGE_DP * density, height / 2f)
            val layerHeight = ceil(strip + min(LEAD_IN_DP * density, height - strip))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                drawContent()
                translate(top = 0f) {
                    drawRect(masks.fade(0, strip, strip), size = Size(width, strip))
                }
                translate(top = height - strip) {
                    drawRect(masks.fade(1, strip, strip), size = Size(width, strip))
                }
                return@drawWithContent
            }
            val layerSize = IntSize(width.roundToInt(), layerHeight.roundToInt())
            contentLayer.record { this@drawWithContent.drawContent() }
            for (edge in 0..1) {
                val regionTop = if (edge == 0) 0f else height - layerHeight
                for (band in 0 until EMBER_BLUR_BANDS) {
                    val index = edge * EMBER_BLUR_BANDS + band
                    val sigma = BandSigmaDp[band] * density
                    val blur = blurLayers[index]
                    blur.renderEffect = BlurEffect(sigma, sigma, TileMode.Clamp)
                    blur.clip = true
                    blur.record(size = layerSize) {
                        translate(top = -regionTop) { drawLayer(contentLayer) }
                    }
                    val mask = maskLayers[index]
                    mask.compositingStrategy = CompositingStrategy.Offscreen
                    mask.blendMode = BlendMode.Plus
                    mask.record(size = layerSize) {
                        drawLayer(blur)
                        drawRect(Color.White.copy(alpha = BandTint[band]), blendMode = BlendMode.SrcAtop)
                        drawRect(masks.band(edge, band, strip, layerHeight), blendMode = BlendMode.DstIn)
                    }
                }
            }
            sharpLayer.compositingStrategy = CompositingStrategy.Offscreen
            sharpLayer.record {
                drawLayer(contentLayer)
                translate(top = 0f) {
                    drawRect(masks.sharp(0, strip, layerHeight), size = Size(width, strip), blendMode = BlendMode.DstIn)
                }
                translate(top = height - strip) {
                    drawRect(masks.sharp(1, strip, layerHeight), size = Size(width, strip), blendMode = BlendMode.DstIn)
                }
            }
            compositeLayer.compositingStrategy = CompositingStrategy.Offscreen
            compositeLayer.record {
                drawLayer(sharpLayer)
                for (edge in 0..1) {
                    val regionTop = if (edge == 0) 0f else height - layerHeight
                    for (band in 0 until EMBER_BLUR_BANDS) {
                        translate(top = regionTop) { drawLayer(maskLayers[edge * EMBER_BLUR_BANDS + band]) }
                    }
                }
            }
            drawLayer(compositeLayer)
        },
        content = content,
    )
}
