package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.cssBoxShadow

/**
 * The white summary card (`styles.summary`): "42 min well spent", the laurels around "3 completed", and the "0 open"
 * button that opens the Review page. It resolves 0.85 to 1.4 s, rising 10 dp, and leaves with the end fade. From a
 * text scale of 1.4 the three parts stack in a column 12 dp apart.
 */
@Composable
internal fun EmberDayReviewSummary(
    controller: EmberDayReviewController,
    metrics: EmberDayReviewMetrics,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduced = metrics.reduced
    val stacked = metrics.fontScale > 1.4f
    val shape = RoundedCornerShape(20.dp)
    val frame = modifier
        .padding(horizontal = 16.dp)
        .fillMaxWidth()
        .graphicsLayer {
            if (!reduced) {
                val t = controller.time.floatValue
                alpha = emberSummaryOpacity(t)
                translationY = emberSummaryRise(t) * density
            }
        }
        .cssBoxShadow(EmberDayReviewPalette.SummaryShadow, offsetY = 4.dp, blur = 20.dp, radius = 20.dp)
        .background(EmberDayReviewPalette.Screen, shape)
        .padding(horizontal = 8.dp, vertical = 26.dp)
    if (stacked) {
        Column(frame, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SideValue("42 min", "well spent", Modifier.heightIn(min = 48.dp))
            Middle()
            OpenButton(enabled, Modifier.heightIn(min = 48.dp)) { controller.showReview(true) }
        }
    } else {
        Row(frame, verticalAlignment = Alignment.CenterVertically) {
            SideValue("42 min", "well spent", Modifier.weight(1f).heightIn(min = 48.dp))
            Middle(Modifier.weight(1.3f))
            OpenButton(enabled, Modifier.weight(1f).heightIn(min = 48.dp)) { controller.showReview(true) }
        }
    }
}

@Composable
private fun SideValue(value: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        SummaryValue(value)
        SummaryLabel(label)
    }
}

@Composable
private fun OpenButton(enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    EmberDayReviewTap(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "Review zero unfinished actions" },
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            SummaryValue("0 open")
            SummaryLabel("to review ›")
        }
    }
}

@Composable
private fun Middle(modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmberDayReviewLaurel(flip = false)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmberDayReviewText(
                text = "✓ 3",
                size = 24f,
                color = EmberDayReviewPalette.Total,
                weight = FontWeight.SemiBold,
                cap = 1.6f,
                align = TextAlign.Center,
            )
            SummaryLabel("completed")
        }
        EmberDayReviewLaurel(flip = true)
    }
}

@Composable
private fun SummaryValue(text: String) {
    EmberDayReviewText(text = text, size = 12f, color = EmberDayReviewPalette.SmallValue, weight = FontWeight.Medium)
}

@Composable
private fun SummaryLabel(text: String) {
    EmberDayReviewText(
        text = text,
        size = 10f,
        color = EmberDayReviewPalette.SmallLabel,
        modifier = Modifier.padding(top = 4.dp),
        align = TextAlign.Center,
    )
}
