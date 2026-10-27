package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The top bar of the day surface: an empty 48 dp slot, the handle-like badge in the middle (a 36 x 6 grey pill; on
 * Today a "3 in Review" chip) and the round "..." button that opens the Review page, all inside the safe area minus 4 dp.
 * While the day drops, the content fades out 0.65 to 0.9 and the bar turns white 0.65 to 1 (`headerContentStyle`,
 * `topTintStyle`).
 */
@Composable
internal fun EmberDayReviewTopBar(
    controller: EmberDayReviewController,
    metrics: EmberDayReviewMetrics,
    enabled: Boolean,
) {
    val today = controller.visibleDay == EmberDayReviewDay.Today
    val topInset: Dp = (metrics.insetTop - 4.dp).coerceAtLeast(0.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val tint = ((controller.reviewProgress() - 0.65f) / 0.35f).coerceIn(0f, 1f)
                if (tint > 0f) {
                    drawRect(Color.White.copy(alpha = tint))
                }
            }
            .padding(start = 12.dp, end = 12.dp, top = topInset),
    ) {
        Row(
            Modifier
                .weight(1f)
                .graphicsLayer {
                    val fade = ((controller.reviewProgress() - 0.65f) / 0.25f).coerceIn(0f, 1f)
                    alpha = 1f - fade
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp))
            if (today) {
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 36.dp, minHeight = 20.dp)
                        .background(EmberDayReviewPalette.BadgeToday, RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmberDayReviewText(
                        text = "3 in Review",
                        size = 10f,
                        color = EmberDayReviewPalette.Accent,
                        weight = FontWeight.SemiBold,
                        cap = 1.4f,
                    )
                }
            } else {
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 36.dp)
                        .height(6.dp)
                        .background(EmberDayReviewPalette.BadgeIdle, RoundedCornerShape(3.dp)),
                )
            }
            EmberDayReviewTap(
                enabled = enabled,
                onClick = { controller.showReview(true) },
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Review unfinished actions" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(28.dp).background(EmberDayReviewPalette.MoreDisc, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    EmberDayReviewIcon(EmberDayReviewGlyph.EllipsisHorizontal, 15.dp, EmberDayReviewPalette.Accent)
                }
            }
        }
    }
}
