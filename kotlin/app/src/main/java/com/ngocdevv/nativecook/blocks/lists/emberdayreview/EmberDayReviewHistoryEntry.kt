package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * One row of yesterday's timeline (`HistoryEntry`): a dot, the time stamp, the title, and, except on the last row, the
 * thin rail that runs 9 dp below the dot to 27 dp past the row to meet the next dot. The row rises 10 dp into place
 * and fades in as the loop reaches it, 180 ms after the previous one, and leaves with the end fade.
 */
@Composable
internal fun EmberDayReviewHistoryEntry(
    stamp: String,
    title: String,
    index: Int,
    isLast: Boolean,
    controller: EmberDayReviewController,
    reduced: Boolean,
) {
    Column(
        Modifier
            .graphicsLayer {
                if (!reduced) {
                    val t = controller.time.floatValue
                    alpha = emberHistoryOpacity(t, index)
                    translationY = emberHistoryRise(t, index) * density
                }
            }
            .drawBehind {
                if (!isLast) {
                    val top = 9.dp.toPx()
                    drawRect(
                        color = EmberDayReviewPalette.Rail,
                        topLeft = Offset(2.dp.toPx(), top),
                        size = Size(1.dp.toPx(), size.height - top + 27.dp.toPx()),
                    )
                }
            },
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                Modifier.size(5.dp).background(EmberDayReviewPalette.Dot, CircleShape),
            )
            EmberDayReviewText(
                text = stamp,
                size = 11f,
                color = EmberDayReviewPalette.Stamp,
                modifier = Modifier.padding(start = 5.dp),
                spacing = 0.5f,
                tabular = true,
            )
        }
        EmberDayReviewText(
            text = title,
            size = 16f,
            color = EmberDayReviewPalette.HistoryTitle,
            modifier = Modifier.padding(start = 10.dp),
            line = 22f,
            cap = 1.8f,
        )
    }
}
