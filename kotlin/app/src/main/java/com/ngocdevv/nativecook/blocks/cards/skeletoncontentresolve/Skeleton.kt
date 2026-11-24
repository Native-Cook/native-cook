package com.ngocdevv.nativecook.blocks.cards.skeletoncontentresolve

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.max

/** What every skeleton of one screen shares: the two clocks and the view the shimmer is measured against. */
@Stable
internal class SkeletonGroup(
    val sinceHide: FloatState,
    val sinceShow: FloatState,
    val span: Float,
    val bar: Color,
    val shine: Color,
    val block: Color,
) {
    var container: LayoutCoordinates? = null
    val shineClear = shine.copy(alpha = 0f)
    val blockClear = block.copy(alpha = 0f)

    fun k(order: Float) = kAt(order, sinceHide.floatValue, sinceShow.floatValue)

    /** Where a placed element sits in the group, px. Layout only, so the list's pull does not affect it. */
    fun place(self: LayoutCoordinates?): Offset {
        val root = container ?: return Offset.Zero
        if (self == null || !self.isAttached || !root.isAttached) return Offset.Zero
        return root.localPositionOf(self, Offset.Zero)
    }
}

private class Placed {
    var coords: LayoutCoordinates? = null
    val path = Path()
}

/**
 * Wraps one line of content and measures it. While loading it shows a bar of [placeholder] width with
 * the group's shimmer running through it; to resolve, the bar settles to the child's exact width,
 * the child fades in inside it, and the bar dissolves.
 */
@Composable
internal fun Skeleton(
    group: SkeletonGroup,
    order: Float,
    placeholder: Float,
    barHeight: Float,
    modifier: Modifier = Modifier,
    radius: Float = barHeight / 2f,
    alignEnd: Boolean = false,
    content: @Composable () -> Unit,
) {
    val placed = remember { Placed() }
    Box(
        modifier
            .onPlaced { placed.coords = it }
            .graphicsLayer { }
            .drawBehind { drawBar(group, placed, order, placeholder, barHeight, radius, alignEnd) },
    ) {
        Box(Modifier.graphicsLayer { alpha = contentAlpha(group.k(order)) }) { content() }
    }
}

private fun DrawScope.drawBar(
    group: SkeletonGroup,
    placed: Placed,
    order: Float,
    placeholder: Float,
    barHeight: Float,
    radius: Float,
    alignEnd: Boolean,
) {
    val hidden = group.sinceHide.floatValue
    val k = kAt(order, hidden, group.sinceShow.floatValue)
    val alpha = barAlpha(k)
    if (alpha <= 0f) return
    val d = density
    val contentW = size.width / d
    val widthDp = max(barHeight, placeholder + (contentW - placeholder) * barMorph(k))
    val w = widthDp * d
    val h = barHeight * d
    val left = if (alignEnd) size.width - w else 0f
    val top = (size.height - h) / 2f
    val r = CornerRadius(radius * d)
    val bloom = barBloom(k)
    withTransform({ scale(1f, bloom, Offset(left + w / 2f, size.height / 2f)) }) {
        drawRoundRect(group.bar, Offset(left, top), Size(w, h), r, alpha = alpha)
        val sheen = shimmerAlpha(k, hidden) * alpha
        if (sheen > 0f) {
            val at = group.place(placed.coords)
            val centreY = (at.y + size.height / 2f) / d
            val barLeft = (at.x + left) / d
            val c = (shimmerX(hidden, group.span) - SLANT * centreY - barLeft) * d + left
            val half = BAND * d / 2f
            placed.path.rewind()
            placed.path.addRoundRect(RoundRect(left, top, left + w, top + h, r))
            clipPath(placed.path) {
                drawRect(
                    Brush.horizontalGradient(listOf(group.shineClear, group.shine, group.shineClear), c - half, c + half),
                    Offset(c - half, top),
                    Size(half * 2f, h),
                    alpha = sheen,
                )
            }
        }
    }
}


internal fun renderCover(spec: CoverSpec, px: IntSize, density: Density, layoutDirection: androidx.compose.ui.unit.LayoutDirection): ImageBitmap {
    val bitmap = ImageBitmap(px.width, px.height)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.width.toFloat(), px.height.toFloat())) {
        drawCover(spec, px.width.toFloat())
    }
    return bitmap
}

/**
 * A square of drawn artwork under a placeholder. The placeholder carries the same shimmer band as
 * the bars and opens with a soft radial wipe from the centre.
 */
@Composable
internal fun SkeletonCover(group: SkeletonGroup, order: Float, size: Dp, radius: Dp, spec: CoverSpec) {
    val placed = remember { Placed() }
    Box(
        Modifier
            .onPlaced { placed.coords = it }
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(group.block)
            .drawWithCache {
                val px = ceil(this.size.width).toInt().coerceAtLeast(1)
                val art = renderCover(spec, IntSize(px, px), Density(density, fontScale), layoutDirection)
                onDrawBehind {
                    drawImage(art)
                    drawCoverVeil(group, placed, order)
                }
            },
    )
}

private fun DrawScope.drawCoverVeil(group: SkeletonGroup, placed: Placed, order: Float) {
    val hidden = group.sinceHide.floatValue
    val k = kAt(order, hidden, group.sinceShow.floatValue)
    if (k >= 1f) return
    val s = size.width
    val p = wipe(k) * (1f + WIPE_SOFT)
    drawRect(
        Brush.radialGradient(
            0f to group.blockClear,
            clamp01(p - WIPE_SOFT) to group.blockClear,
            clamp01(p) to group.block,
            1f to group.block,
            center = Offset(s / 2f, s / 2f),
            radius = s * 0.75f,
        ),
    )
    val sheen = shimmerAlpha(k, hidden)
    if (sheen <= 0f) return
    val d = density
    val at = group.place(placed.coords)
    val tx = (shimmerX(hidden, group.span) - SLANT * (at.y / d) - at.x / d) * d
    val half = BAND * d / 2f
    val shear = Matrix().apply {
        values[Matrix.SkewX] = -SLANT
        values[Matrix.TranslateX] = tx
    }
    withTransform({ transform(shear) }) {
        drawRect(
            Brush.horizontalGradient(listOf(group.shineClear, group.shine, group.shineClear), -half, half),
            Offset(-half, 0f),
            Size(half * 2f, s),
            alpha = sheen,
        )
    }
}
