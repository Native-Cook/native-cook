package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The foot of the day surface (`styles.footer` with its `LinearGradient`): a fade from clear to white (to peach on
 * Today) behind the primary button, and on Today the Add Action pill above it. The gradient takes no touches, only the
 * controls on it do. The button reads "Today" with a day badge and an arrow on Yesterday, and "Commit" / "Committed"
 * with a lock on Today; its label swaps in place ([EmberDayReviewSwapText]).
 */
@Composable
internal fun EmberDayReviewFooter(
    controller: EmberDayReviewController,
    metrics: EmberDayReviewMetrics,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val today = controller.visibleDay == EmberDayReviewDay.Today
    val colors = if (today) {
        listOf(EmberDayReviewPalette.FooterClear, EmberDayReviewPalette.FooterTodayMid, EmberDayReviewPalette.FooterTodayEnd)
    } else {
        listOf(EmberDayReviewPalette.FooterClear, Color.White, Color.White)
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors))
            .padding(top = if (today) 44.dp else 28.dp, bottom = metrics.insetBottom + 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (today) {
            EmberDayReviewAddAction(controller, enabled)
        }
        PrimaryButton(controller, metrics, today, enabled)
    }
}

@Composable
private fun PrimaryButton(controller: EmberDayReviewController, metrics: EmberDayReviewMetrics, today: Boolean, enabled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val committed = controller.committed
    val label = if (!today) "Today" else if (committed) "Committed" else "Commit"
    val description = if (!today) "Today" else if (committed) "Committed" else "Commit today"
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = { controller.primaryPress() },
            )
            .semantics { contentDescription = description }
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!today) {
            Box(
                Modifier.size(18.dp).background(EmberDayReviewPalette.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                EmberDayReviewText(
                    text = "25",
                    size = 9f,
                    color = Color.White,
                    weight = FontWeight.Bold,
                    tabular = true,
                )
            }
        } else {
            EmberDayReviewIcon(
                if (committed) EmberDayReviewGlyph.LockClosed else EmberDayReviewGlyph.LockOpen,
                17.dp,
                EmberDayReviewPalette.Accent,
            )
        }
        EmberDayReviewSwapText(
            text = label,
            size = 14f,
            color = EmberDayReviewPalette.PrimaryText,
            weight = FontWeight.SemiBold,
            cap = 1.6f,
            reduced = metrics.reduced,
        )
        if (!today) {
            EmberDayReviewIcon(EmberDayReviewGlyph.ArrowForward, 17.dp, EmberDayReviewPalette.AccentArrow)
        }
    }
}
