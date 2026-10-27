package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What React Native reads from `useWindowDimensions()` and `useSafeAreaInsets()`, plus the reduced-motion flag.
 * `compact` and the hero height are the same two formulas as in `EmberDayReview.tsx` (`height < 740 || fontScale > 1.35`,
 * `compact ? 200 : 262`).
 */
@Immutable
internal data class EmberDayReviewMetrics(
    val width: Dp,
    val height: Dp,
    val insetTop: Dp,
    val insetBottom: Dp,
    val keyboardLift: Dp,
    val fontScale: Float,
    val reduced: Boolean,
) {
    val compact: Boolean get() = height.value < 740f || fontScale > 1.35f

    val heroHeight: Dp get() = if (compact) 200.dp else 262.dp
}
