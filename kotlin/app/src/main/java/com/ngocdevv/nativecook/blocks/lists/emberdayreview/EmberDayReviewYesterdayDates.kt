package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The date row of the Yesterday page (`styles.dateRow`): a big "Yesterday" in the middle (35 sp), the neighbouring
 * "Oct 24" peeking in from the left edge (its right edge at 16.5% of the width) and "Today" starting at 83% of the
 * width, all clipped to the row. From a text scale of 1.4 the row becomes a plain column with the sizes of the style (24 and 21).
 */
@Composable
internal fun EmberDayReviewYesterdayDates(
    controller: EmberDayReviewController,
    metrics: EmberDayReviewMetrics,
    enabled: Boolean,
) {
    if (metrics.fontScale > 1.4f) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            DayTab(controller, enabled, EmberDayReviewDay.Yesterday, 24f, Modifier.fillMaxWidth())
            DayTab(controller, enabled, EmberDayReviewDay.Today, 21f, Modifier)
        }
        return
    }
    val widthPx = with(LocalDensity.current) { metrics.width.toPx() }
    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).clipToBounds()) {
        EmberDayReviewText(
            text = "Oct 24",
            size = 24f,
            color = EmberDayReviewPalette.DateHint,
            modifier = Modifier.align(Alignment.CenterStart).rightEdgeAt(widthPx * 0.165f),
            cap = 1.5f,
        )
        DayTab(controller, enabled, EmberDayReviewDay.Yesterday, 35f, Modifier.align(Alignment.Center))
        DayTab(
            controller,
            enabled,
            EmberDayReviewDay.Today,
            28f,
            Modifier.align(Alignment.CenterStart).offset(x = (metrics.width.value * 0.83f).dp),
        )
    }
}

/** One tab of the row: the active day in ink, bold; the other grey and semi-bold; 48 dp tall. */
@Composable
private fun DayTab(
    controller: EmberDayReviewController,
    enabled: Boolean,
    day: EmberDayReviewDay,
    size: Float,
    modifier: Modifier,
) {
    val active = day == EmberDayReviewDay.Yesterday
    EmberDayReviewTap(
        enabled = enabled,
        onClick = { controller.select(day) },
        modifier = modifier.heightIn(min = 48.dp).semantics { selected = active },
        role = Role.Tab,
    ) {
        EmberDayReviewText(
            text = if (active) "Yesterday" else "Today",
            size = size,
            color = if (active) EmberDayReviewPalette.DateActive else EmberDayReviewPalette.DateInactive,
            weight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            spacing = if (active) -0.8f else 0f,
            cap = 1.5f,
        )
    }
}

/** An absolutely placed child whose right edge sits at [xPx] from the parent's left edge, at its natural width. */
private fun Modifier.rightEdgeAt(xPx: Float) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(placeable.width, placeable.height) {
        placeable.place((xPx - placeable.width).roundToInt(), 0)
    }
}
