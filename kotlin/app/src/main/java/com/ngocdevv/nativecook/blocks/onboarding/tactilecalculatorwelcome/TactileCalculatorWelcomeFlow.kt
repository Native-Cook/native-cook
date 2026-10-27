package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * The content column of the React Native `ScrollView` (`styles.content` plus `contentContainerStyle`):
 * three blocks in a column, at least [minHeight] tall and with [bottomPadding] under the last one.
 *
 * - [hero], [heroHeight] tall and as wide as the column: the keys.
 * - [copy], as wide as the column, at least 125 dp tall. With [growCopy] it is `flex: 1`: it takes
 *   whatever height is left once the others and the padding have their share, and its own content
 *   if that is taller. Without it (large text) it is only as tall as its content.
 * - [cta]: the Continue pill, stretched to the column's width less 30 dp each side, 10 dp under the copy.
 *
 * The column may grow past [minHeight] when the copy is tall, which is what makes the screen scroll.
 * Each slot must be exactly one composable that emits one layout node.
 */
@Composable
internal fun TactileCalculatorWelcomeFlow(
    minHeight: Dp,
    heroHeight: Dp,
    bottomPadding: Dp,
    growCopy: Boolean,
    modifier: Modifier = Modifier,
    hero: @Composable () -> Unit,
    copy: @Composable () -> Unit,
    cta: @Composable () -> Unit,
) {
    Layout(
        content = {
            hero()
            copy()
            cta()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val minTotal = minHeight.roundToPx()
        val heroPx = heroHeight.roundToPx()
        val ctaGap = CTA_MARGIN_TOP.dp.roundToPx()
        val ctaInset = CTA_MARGIN_HORIZONTAL.dp.roundToPx()
        val bottomPx = bottomPadding.roundToPx()

        val heroPlaceable = measurables[0].measure(Constraints.fixed(width, heroPx))
        val ctaWidth = max(0, width - 2 * ctaInset)
        val ctaPlaceable = measurables[2].measure(Constraints(minWidth = ctaWidth, maxWidth = ctaWidth))

        // `flex: 1` takes the free height; `minHeight: 125` holds either way.
        val others = heroPx + ctaGap + ctaPlaceable.height + bottomPx
        val free = if (growCopy) minTotal - others else 0
        val copyMin = max(COPY_MIN_HEIGHT.dp.roundToPx(), free)
        val copyPlaceable = measurables[1].measure(Constraints(minWidth = width, maxWidth = width, minHeight = copyMin))

        val total = max(minTotal, others + copyPlaceable.height)
        layout(width, total) {
            heroPlaceable.placeRelative(0, 0)
            copyPlaceable.placeRelative(0, heroPx)
            ctaPlaceable.placeRelative(ctaInset, heroPx + copyPlaceable.height + ctaGap)
        }
    }
}

/** `styles.copy.minHeight`. */
private const val COPY_MIN_HEIGHT = 125f

/** `styles.cta.marginTop`. */
private const val CTA_MARGIN_TOP = 10f

/** `styles.cta.marginHorizontal`. */
private const val CTA_MARGIN_HORIZONTAL = 30f
