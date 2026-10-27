package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * The Yesterday page: the hero, then the date row, the summary card and the timeline in one scrolling column
 * (bottom padding = safe area + 96 dp), behind the progressive blur edges. The page slides left by the page progress
 * (cross-fades under Reduce Motion). The content under the hero is pulled up by the hero's closed height while the
 * hero is shut, so the page opens like a drawer (`followingStyle`).
 */
@Composable
internal fun EmberDayReviewYesterdayPanel(
    controller: EmberDayReviewController,
    metrics: EmberDayReviewMetrics,
    star: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val visible = controller.visibleDay == EmberDayReviewDay.Yesterday
    val interactive = visible && !controller.reviewVisible
    val scroll = rememberScrollState()
    val tracking = rememberEmberDayReviewTouchTracking(scroll, interactive, controller)
    val widthPx = with(LocalDensity.current) { metrics.width.toPx() }
    val heroPx = with(LocalDensity.current) { metrics.heroHeight.toPx() }
    val reduced = metrics.reduced
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val page = controller.pageProgress()
                if (reduced) {
                    alpha = 1f - page
                } else {
                    translationX = -page * widthPx
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
                    .padding(bottom = metrics.insetBottom + 96.dp),
            ) {
                EmberDayReviewHero(controller, metrics, star)
                Column(
                    Modifier.graphicsLayer {
                        if (!reduced) {
                            translationY = heroPx * (emberHeroProgress(controller.time.floatValue) - 1f)
                        }
                    },
                ) {
                    EmberDayReviewYesterdayDates(controller, metrics, interactive)
                    EmberDayReviewSummary(controller, metrics, interactive)
                    Column(
                        Modifier.padding(start = 28.dp, end = 28.dp, top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        EmberDayReviewHistory.forEachIndexed { index, (stamp, title) ->
                            EmberDayReviewHistoryEntry(
                                stamp = stamp,
                                title = title,
                                index = index,
                                isLast = index == EmberDayReviewHistory.size - 1,
                                controller = controller,
                                reduced = reduced,
                            )
                        }
                    }
                }
            }
        }
    }
}
