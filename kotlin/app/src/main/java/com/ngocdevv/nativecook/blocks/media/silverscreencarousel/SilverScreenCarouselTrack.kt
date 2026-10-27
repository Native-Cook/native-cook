package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * The row of cards (`track`, SilverScreenCarousel.tsx:125): a row with `alignItems: 'stretch'`,
 * `paddingLeft` [startPadding] and a [gap] between the cards. Every card is [cardWidth] wide and as tall
 * as the tallest one, and the row is as wide as the screen however far the cards reach past it
 * (they are clipped by the carousel). The caller moves the row with a `graphicsLayer`.
 */
@Composable
internal fun SilverScreenCarouselTrack(
    cardWidth: Dp,
    gap: Dp,
    startPadding: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val cardPx = cardWidth.roundToPx()
        val gapPx = gap.roundToPx()
        val startPx = startPadding.roundToPx()
        val height = measurables.maxOfOrNull { it.maxIntrinsicHeight(cardPx) } ?: 0
        val placeables = measurables.map { it.measure(Constraints.fixed(cardPx, height)) }
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(startPx + index * (cardPx + gapPx), 0)
            }
        }
    }
}
