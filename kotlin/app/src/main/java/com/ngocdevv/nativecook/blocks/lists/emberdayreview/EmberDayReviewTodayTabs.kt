package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** The tabs of the Today page are 34 sp, bold, tightened by 0.8: Today in ink, the others grey. */
@Composable
private fun TabText(text: String, active: Boolean, modifier: Modifier = Modifier) {
    EmberDayReviewText(
        text = text,
        size = 34f,
        color = if (active) EmberDayReviewPalette.Ink else EmberDayReviewPalette.TabMuted,
        modifier = modifier.semantics { if (active) selected = true },
        weight = FontWeight.Bold,
        spacing = -0.8f,
        cap = 1.5f,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * The big day tabs at the top of the Today list (`styles.todayTabs`): "Today" in ink, "Tomorrow" and "Wednesday" in
 * grey after it 16 dp apart (the row does not wrap, so Wednesday runs off the edge), and "Yesterday" waiting just left
 * of the screen, its right edge 14 dp before the row, so it slides in as the page returns. The row is pulled up 8 dp.
 * From a text scale of 1.4 the tabs wrap onto lines and "Yesterday" joins the flow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmberDayReviewTodayTabs(controller: EmberDayReviewController, metrics: EmberDayReviewMetrics, enabled: Boolean) {
    val pull = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val up = 8.dp.roundToPx()
        layout(placeable.width, placeable.height - up) { placeable.place(0, -up) }
    }
    if (metrics.fontScale > 1.4f) {
        FlowRow(
            pull.fillMaxWidth().heightIn(min = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            YesterdayTab(controller, enabled, Modifier)
            TabText("Today", active = true)
            TabText("Tomorrow", active = false)
            TabText("Wednesday", active = false)
        }
        return
    }
    Box(pull.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(
            Modifier.align(Alignment.CenterStart).wrapContentWidth(Alignment.Start, unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabText("Today", active = true)
            TabText("Tomorrow", active = false)
            TabText("Wednesday", active = false)
        }
        YesterdayTab(
            controller,
            enabled,
            Modifier
                .align(Alignment.CenterStart)
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints())
                    layout(placeable.width, placeable.height) {
                        placeable.place(-placeable.width - 14.dp.roundToPx(), 0)
                    }
                },
        )
    }
}

@Composable
private fun YesterdayTab(controller: EmberDayReviewController, enabled: Boolean, modifier: Modifier) {
    EmberDayReviewTap(
        enabled = enabled,
        onClick = { controller.select(EmberDayReviewDay.Yesterday) },
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = "Yesterday" },
        role = Role.Tab,
    ) {
        TabText("Yesterday", active = false)
    }
}
