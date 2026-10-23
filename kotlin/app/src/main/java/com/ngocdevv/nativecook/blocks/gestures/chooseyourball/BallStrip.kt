package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** How many slots either side of the centre are drawn at all. */
private const val REACH = 3f
/** Distance between two thumbnails, in dp. */
private const val STRIDE = 40f
/** Diameter of a thumbnail, and of the selected one, in dp. */
private const val THUMB = 26f
private const val SELECTED = 36f
/** Extra touch area around a thumbnail, in dp. */
private const val HIT_SLOP = 10f

/**
 * The rest of the belt, reduced to an index (BallStrip.tsx): the selected ball and two either
 * side of it, small and low in contrast. A tap on one goes to it.
 */
@Composable
internal fun BallStrip(
    order: List<BallId>,
    offset: () -> Float,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The thumbnails' touch areas are taller than the strip, so they are laid out unconstrained.
    Box(modifier.fillMaxWidth().height(SELECTED.dp).wrapContentHeight(unbounded = true), contentAlignment = Alignment.Center) {
        order.forEachIndexed { index, ball ->
            key(ball) { Thumb(ball, index, order.size, offset, onPick) }
        }
    }
}

@Composable
private fun Thumb(ball: BallId, index: Int, total: Int, offset: () -> Float, onPick: (Int) -> Unit) {
    // A thumbnail three or more slots away is fully transparent: it is neither drawn nor tappable.
    val inReach by remember(index, total, offset) {
        derivedStateOf { abs(cyclicDelta(index.toFloat(), offset(), total)) < REACH }
    }
    if (!inReach) return

    Box(
        Modifier
            // The touch target is the thumbnail and 10 dp around it; the layer below moves both.
            .size((THUMB + 2 * HIT_SLOP).dp)
            // Everything about a thumbnail is its distance from the centre, so each places itself.
            .graphicsLayer {
                val slot = cyclicDelta(index.toFloat(), offset(), total)
                val distance = abs(slot)
                translationX = slot * STRIDE.dp.toPx()
                val scale = lerp(SELECTED / THUMB, 1f, distance.coerceIn(0f, 1f))
                scaleX = scale
                scaleY = scale
                alpha = if (distance <= 1f) lerp(1f, 0.42f, distance) else lerp(0.42f, 0f, ((distance - 1f) / (REACH - 1f)).coerceIn(0f, 1f))
            }
            .clickable(interactionSource = null, indication = null, role = Role.Button) { onPick(index) }
            .semantics { contentDescription = ball.label }
            .padding(HIT_SLOP.dp)
            .drawWithCache { pokeBallGraphic(ball, showButton = false) },
    )
}

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction
