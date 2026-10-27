package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The iOS-style Review page that waits behind the day (`styles.reviewScreen`): a "Done" button at the right (12 dp
 * past the padding), the "Review" title and an empty state centred in the rest. It is only reachable, and only read
 * by accessibility, while the day is dropped.
 */
@Composable
internal fun EmberDayReviewReviewPage(
    controller: EmberDayReviewController,
    metrics: EmberDayReviewMetrics,
    modifier: Modifier = Modifier,
) {
    val visible = controller.reviewVisible
    Column(
        modifier
            .fillMaxSize()
            .background(EmberDayReviewPalette.ReviewScreen)
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
            .padding(start = 24.dp, end = 24.dp, top = metrics.insetTop + 4.dp, bottom = metrics.insetBottom + 210.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            EmberDayReviewTap(
                enabled = visible,
                onClick = { controller.showReview(false) },
                modifier = Modifier.offset(x = 12.dp).defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmberDayReviewText(
                    text = "Done",
                    size = 14f,
                    color = EmberDayReviewPalette.PrimaryText,
                    weight = FontWeight.SemiBold,
                )
            }
        }
        EmberDayReviewText(
            text = "Review",
            size = 30f,
            color = EmberDayReviewPalette.Ink,
            modifier = Modifier.semantics { heading() },
            weight = FontWeight.Bold,
            line = 36f,
            cap = 1.6f,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmberDayReviewText(
                text = "No unfinished actions\nfor selected day.",
                size = 25f,
                color = EmberDayReviewPalette.ReviewEmpty,
                line = 38f,
                cap = 1.6f,
                align = TextAlign.Center,
            )
        }
    }
}
