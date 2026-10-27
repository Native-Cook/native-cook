package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The Today page: the day tabs and the checklist in one scrolling column (24 dp at the sides, 170 dp + safe area at
 * the foot, so the last action clears the Add Action field), behind the progressive blur edges. The page slides in
 * from the right by `(1 - pageProgress)` of the width (cross-fades under Reduce Motion). Actions typed after the
 * first seven enter with a rise; "Committed. Small steps count." closes the list once Commit is pressed.
 */
@Composable
internal fun EmberDayReviewTodayPanel(
    controller: EmberDayReviewController,
    metrics: EmberDayReviewMetrics,
    modifier: Modifier = Modifier,
) {
    val visible = controller.visibleDay == EmberDayReviewDay.Today
    val interactive = visible && !controller.reviewVisible
    val scroll = rememberScrollState()
    val tracking = rememberEmberDayReviewTouchTracking(scroll, interactive, controller)
    val widthPx = with(LocalDensity.current) { metrics.width.toPx() }
    val reduced = metrics.reduced
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val page = controller.pageProgress()
                if (reduced) {
                    alpha = page
                } else {
                    translationX = (1f - page) * widthPx
                }
            }
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        EmberDayReviewProgressiveBlur(enabled = interactive, scroll = scroll, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(tracking)
                    .verticalScroll(scroll, enabled = interactive)
                    .padding(start = 24.dp, end = 24.dp, bottom = 170.dp + metrics.insetBottom),
            ) {
                EmberDayReviewTodayTabs(controller, metrics, interactive)
                controller.tasks.forEachIndexed { index, title ->
                    key(title) {
                        EmberDayReviewTaskRow(
                            title = title,
                            checked = controller.completed.contains(index),
                            enabled = interactive,
                            animateIn = index >= EmberDayReviewTodayTasks.size && !reduced,
                            reduced = reduced,
                            onToggle = { controller.toggleTask(index) },
                        )
                    }
                }
                if (controller.committed) {
                    EmberDayReviewText(
                        text = "Committed. Small steps count.",
                        size = 14f,
                        color = EmberDayReviewPalette.Saved,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        line = 21f,
                    )
                }
            }
        }
    }
}
