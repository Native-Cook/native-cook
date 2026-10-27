package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * `Plane`: one half of the plane the card and its token matrix share. The plane is 210 by 135 dp,
 * scaled by `width / 402` about its top-left corner and moved so its left edge follows the measured
 * knots of [CipherSeamTimeline.planeSourceX]; a clip box as wide as half the hero lets only one
 * half of it show, the left half (the card) on the left of the seam, the right half (the token
 * matrix) on the right. The `previous` plane is the token matrix of the loop before, which
 * leaves to the right at the start of the loop and fades back in at its end.
 *
 * Everything that changes with time lives in the `graphicsLayer` block, so a frame never recomposes
 * this function. Under Reduce Motion ([reduced]) the plane stands across the seam, centred, and
 * the previous plane is gone.
 *
 * @param width the hero's width in dp (`heroWidth`)
 * @param cardHeight the scaled card height in dp
 * @param right whether this is the right half (token matrix) rather than the left half (card)
 */
@Composable
internal fun CipherSeamPlane(
    clock: CipherSeamClock,
    width: Float,
    cardHeight: Float,
    right: Boolean,
    reduced: Boolean,
    previous: Boolean = false,
) {
    val scale = width / CipherSeamTimeline.DESIGN_WIDTH
    val clipLeft = if (right) width / 2f else 0f
    Box(
        Modifier
            .offset(x = clipLeft.dp)
            .size((width / 2f).dp, cardHeight.dp)
            .clipToBounds(),
    ) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(CipherSeamTimeline.CARD_WIDTH.dp, CipherSeamTimeline.CARD_HEIGHT.dp)
                .graphicsLayer {
                    val t = clock.time
                    val sourceX = CipherSeamTimeline.planeSourceX(t, previous)
                    val position = if (reduced) {
                        width / 2f - CipherSeamTimeline.CARD_WIDTH * scale / 2f
                    } else {
                        (sourceX / CipherSeamTimeline.SOURCE_VIEWPORT * width).toFloat()
                    }
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    translationX = (position - clipLeft) * density
                    alpha = when {
                        !previous -> 1f
                        reduced -> 0f
                        else -> CipherSeamTimeline.previousOpacity(t).toFloat()
                    }
                },
        ) {
            if (right) CipherSeamTokenField(clock, reduced) else CipherSeamCardFace()
        }
    }
}
