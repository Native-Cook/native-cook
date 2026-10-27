package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlin.math.max

/** One layer of a CSS `box-shadow`: `0 offsetY blur spread color`, all in dp. */
internal class FolioShadow(val offsetY: Float, val blur: Float, val spread: Float, val color: Color)

private fun black(alpha: Float) = Color(0f, 0f, 0f, alpha)

/** `SHADOW`: `0px 1px 2px rgba(0,0,0,0.04), 0px 10px 28px -8px rgba(0,0,0,0.12)`. */
internal val FolioCardShadow = listOf(
    FolioShadow(1f, 2f, 0f, black(0.04f)),
    FolioShadow(10f, 28f, -8f, black(0.12f)),
)
internal val FolioShadowChevron = listOf(FolioShadow(2f, 8f, 0f, black(0.08f)))
internal val FolioShadowCategory = listOf(FolioShadow(1f, 3f, 0f, black(0.06f)))
internal val FolioShadowNavPill = listOf(FolioShadow(10f, 30f, -8f, black(0.22f)))
internal val FolioShadowNavSelected = listOf(FolioShadow(2f, 8f, 0f, black(0.10f)))
internal val FolioShadowPlus = listOf(FolioShadow(10f, 22f, -8f, black(0.5f)))
internal val FolioShadowCircle = listOf(FolioShadow(2f, 10f, 0f, black(0.08f)))
internal val FolioShadowAllBooks = listOf(FolioShadow(1f, 4f, 0f, black(0.06f)))
internal val FolioShadowCoverDetail = listOf(FolioShadow(14f, 26f, -10f, black(0.45f)))
internal val FolioShadowSection = listOf(FolioShadow(1f, 3f, 0f, black(0.04f)))
internal val FolioShadowButton = listOf(FolioShadow(10f, 22f, -12f, Color(16f / 255f, 90f / 255f, 70f / 255f, 0.9f)))
internal val FolioShadowButtonSecondary = listOf(FolioShadow(1f, 3f, 0f, black(0.05f)))
internal val FolioShadowTopic = listOf(FolioShadow(2f, 6f, 0f, black(0.08f)))
internal val FolioShadowTopicSelected = listOf(FolioShadow(4f, 12f, -4f, Color(30f / 255f, 111f / 255f, 239f / 255f, 0.7f)))

/**
 * React Native's `boxShadow` under a rounded box: each layer is drawn outside the box only, with the CSS
 * blur radius (twice the sigma) and spread. [radius] is read while drawing (dp), so an animated corner
 * radius keeps its shadow. From API 28; older devices draw no shadow (as the shared helper does).
 */
internal fun Modifier.folioShadow(layers: List<FolioShadow>, radius: () -> Float): Modifier = composed {
    val shadows = remember(layers) { layers.map { SoftRoundRect(it.color, sigma = it.blur / 2f) } }
    drawBehind {
        val r = radius()
        val widthDp = size.width / density
        val heightDp = size.height / density
        val box = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r * density)))
        }
        clipPath(box, ClipOp.Difference) {
            scale(density, density, pivot = Offset.Zero) {
                drawIntoCanvas { canvas ->
                    for (index in layers.indices) {
                        val layer = layers[index]
                        val s = layer.spread
                        shadows[index].draw(
                            canvas.nativeCanvas,
                            -s,
                            layer.offsetY - s,
                            widthDp + 2f * s,
                            heightDp + 2f * s,
                            max(0f, r + s),
                        )
                    }
                }
            }
        }
    }
}

/**
 * An `inset` box shadow without blur: the band between the box and the box moved by [offsetY] and shrunk
 * by [spread], clipped to the box. `0px 1px 0px rgba(255,255,255,0.16) inset` is (offsetY 1, spread 0);
 * `0px 0px 0px 1px rgba(255,255,255,0.9) inset` is (offsetY 0, spread 1).
 */
internal fun Modifier.folioInset(color: Color, offsetY: Float, spread: Float, radius: Float): Modifier = drawWithContent {
    drawContent()
    val r = radius * density
    val s = spread * density
    val shift = offsetY * density
    val outer = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
    }
    val hole = Path().apply {
        addRoundRect(
            RoundRect(
                s,
                s + shift,
                size.width - s,
                size.height - s + shift,
                CornerRadius(max(0f, r - s)),
            ),
        )
    }
    val band = Path.combine(PathOperation.Difference, outer, hole)
    clipPath(outer) {
        drawPath(band, color)
    }
}
