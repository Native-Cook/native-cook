package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val DroppedShape = RoundedCornerShape(30.dp)

/**
 * The day: everything that is not the Review page, as one white surface (`daySurfaceStyle`). Lowering it for the
 * Review page translates it down by 75.2% of the height, shrinks it to 92% about its top centre and rounds its
 * corners to 30 dp (it fades instead under Reduce Motion). Inside: the glow, the top bar, the two pages, the card
 * label that appears when it is dropped, and the footer.
 */
@Composable
internal fun EmberDayReviewDaySurface(
    controller: EmberDayReviewController,
    metrics: EmberDayReviewMetrics,
    star: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val reviewVisible = controller.reviewVisible
    val interactive = !reviewVisible
    val reduced = metrics.reduced
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val review = controller.reviewProgress()
                shape = if (review > 0f) DroppedShape else RectangleShape
                clip = true
                transformOrigin = TransformOrigin(0.5f, 0f)
                if (reduced) {
                    alpha = 1f - review
                } else {
                    translationY = review * size.height * 0.752f
                    scaleX = 1f - 0.08f * review
                    scaleY = 1f - 0.08f * review
                }
            }
            .background(EmberDayReviewPalette.Screen)
            .then(if (reviewVisible) Modifier.clearAndSetSemantics {} else Modifier)
            .padding(bottom = metrics.keyboardLift),
    ) {
        EmberDayReviewGlow(controller, metrics)
        Column(Modifier.fillMaxSize()) {
            EmberDayReviewTopBar(controller, metrics, interactive)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        if (!reduced) {
                            alpha = emberInterpolate(
                                controller.reviewProgress(),
                                floatArrayOf(0f, 0.4f, 0.85f, 1f),
                                floatArrayOf(1f, 1f, 0.15f, 0f),
                            )
                        }
                    },
            ) {
                EmberDayReviewYesterdayPanel(controller, metrics, star)
                EmberDayReviewTodayPanel(controller, metrics)
            }
        }
        CollapsedLabel(controller, metrics)
        EmberDayReviewFooter(controller, metrics, interactive, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * What the dropped day card says: a 32 x 3 handle 12 dp from the top and, 30 dp from the top, "Showing unfinished
 * for selected day" over "Yesterday >", both fading in 0.75 to 1 of the drop (`compactDayStyle`).
 */
@Composable
private fun CollapsedLabel(controller: EmberDayReviewController, metrics: EmberDayReviewMetrics) {
    val reduced = metrics.reduced
    val fade = Modifier.graphicsLayer {
        alpha = if (reduced) 0f else ((controller.reviewProgress() - 0.75f) / 0.25f).coerceIn(0f, 1f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .then(fade),
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 12.dp)
                .size(width = 32.dp, height = 3.dp)
                .background(EmberDayReviewPalette.Handle, RoundedCornerShape(2.dp)),
        )
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 30.dp)
                .wrapContentSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmberDayReviewText(
                text = "Showing unfinished for selected day",
                size = 16f,
                color = EmberDayReviewPalette.ReviewNotice,
                line = 18f,
                align = TextAlign.Center,
            )
            EmberDayReviewText(
                text = "Yesterday ›",
                size = 15f,
                color = EmberDayReviewPalette.Accent,
                weight = FontWeight.SemiBold,
            )
        }
    }
}
