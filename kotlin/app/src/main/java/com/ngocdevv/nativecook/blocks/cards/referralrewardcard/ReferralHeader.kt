package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Tone
import kotlin.math.min

// Large text moves the reward cluster below the title while retaining the measured flight target.
@Composable
internal fun ReferralHeader(
    state: ReferralState,
    width: Float,
    density: Float,
    fontScale: Float,
    tiers: ReferralTiers,
    cardPosition: Offset,
    onMarkPosition: (Float, Float) -> Unit,
) {
    val stacked = width - Size.cardPad * 2 < 152 * fontScale + 12 + Size.tiersWidth
    val title = stringResource(R.string.referralrewardcard_title)
    val subtitle = stringResource(R.string.referralrewardcard_subtitle)
    val caption = stringResource(R.string.referralrewardcard_caption, state.shownTier)
    val rewardLabel = stringResource(R.string.referralrewardcard_rewards, state.shownTier)

    val headerModifier =
        Modifier.fillMaxWidth().padding(horizontal = Size.cardPad.dp).graphicsLayer {
            alpha = state.part(0).toFloat()
            translationY = if (state.reduced) 0f else (8 * (1 - state.part(0)) * density).toFloat()
        }
    if (stacked) {
        Column(headerModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReferralHeaderCopy(
                title,
                subtitle,
                fontScale,
                Modifier.width(min(width - Size.cardPad * 2, 180 * fontScale).dp),
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReferralHeaderMarks(state, tiers, rewardLabel, density, cardPosition, onMarkPosition)
                BasicText(
                    caption,
                    Modifier.weight(1f).clearAndSetSemantics {},
                    style =
                        referralText(12, 16)
                            .copy(
                                color = Color.White.copy(alpha = Tone.noteAlpha),
                                textAlign = TextAlign.End,
                                fontFeatureSettings = "tnum",
                            ),
                )
            }
        }
    } else {
        Row(headerModifier, horizontalArrangement = Arrangement.SpaceBetween) {
            ReferralHeaderCopy(
                title,
                subtitle,
                fontScale,
                Modifier.width(
                    min(
                        180 * fontScale,
                        width - Size.cardPad * 2 - 12 - Size.tiersWidth,
                    ).dp,
                ),
            )
            Column(
                Modifier.padding(top = 1.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReferralHeaderMarks(state, tiers, rewardLabel, density, cardPosition, onMarkPosition)
                BasicText(
                    caption,
                    Modifier.clearAndSetSemantics {},
                    style =
                        referralText(12, 16)
                            .copy(
                                color = Color.White.copy(alpha = Tone.noteAlpha),
                                fontFeatureSettings = "tnum",
                            ),
                )
            }
        }
    }
}

@Composable
private fun ReferralHeaderCopy(
    title: String,
    subtitle: String,
    fontScale: Float,
    modifier: Modifier,
) {
    Column(
        modifier.widthIn(max = (180 * fontScale).dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(
            title,
            Modifier.semantics { heading() },
            style = referralText(26, 32, FontWeight.Bold).copy(letterSpacing = (-0.5).sp),
        )
        BasicText(
            subtitle,
            style = referralText(16, 24).copy(color = Color.White.copy(alpha = Tone.subAlpha)),
        )
    }
}

@Composable
private fun ReferralHeaderMarks(
    state: ReferralState,
    tiers: ReferralTiers,
    rewardLabel: String,
    density: Float,
    cardPosition: Offset,
    onMarkPosition: (Float, Float) -> Unit,
) {
    Canvas(
        Modifier
            .graphicsLayer()
            .size(Size.tiersWidth.dp, Size.tierDiameter.dp)
            .semantics {
                contentDescription = rewardLabel
                role = Role.Image
            }.onGloballyPositioned {
                val position =
                    it.localToRoot(Offset.Zero) - cardPosition
                onMarkPosition(position.x / density, position.y / density)
            },
    ) {
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            canvas.save()
            canvas.scale(density, density)
            tiers.draw(canvas, state)
            canvas.restore()
        }
    }
}
