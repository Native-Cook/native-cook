package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Tone
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Native text siblings over the rail; its one pan exclusively owns the action hit region. */
@Composable
internal fun ReferralCapsule(
    state: ReferralState,
    width: Float,
    effects: Array<RenderEffect?>?,
) {
    val density = LocalDensity.current.density
    val name = ReferralFriends.names[state.capsuleIndex]
    var wasOpen by remember { mutableStateOf(false) }
    var joined by remember { mutableStateOf(false) }
    if (state.open && !wasOpen) joined = state.joined(state.capsuleIndex)
    if (wasOpen != state.open) wasOpen = state.open
    val status =
        stringResource(
            if (joined) {
                R.string.referralrewardcard_joined
            } else if (width < 320) {
                R.string.referralrewardcard_pending_short
            } else {
                R.string.referralrewardcard_pending
            },
        )
    val action = stringResource(R.string.referralrewardcard_invite)
    val actionLabel = stringResource(R.string.referralrewardcard_invite_name, name)
    val labelStyle = referralText(14, 20, FontWeight.SemiBold)
    val measure = rememberTextMeasurer().measure(action, labelStyle)
    val actionWidth = max(44f, 2 * ceil((measure.size.width / density + 24) / 2))
    SideEffect { state.actionWidth = actionWidth.toDouble() }
    val copyWidth =
        max(width - Size.cardPad * 2 - 90 - 20 - (if (joined) 0f else actionWidth + 8), 0f)
    ReferralCapsuleCopy(state, width, density, copyWidth, name, status, effects)
    if (!joined) ReferralCapsuleAction(state, width, density, actionWidth, action, actionLabel, labelStyle, effects)
}

@Composable
private fun ReferralCapsuleCopy(
    state: ReferralState,
    width: Float,
    density: Float,
    copyWidth: Float,
    name: String,
    status: String,
    effects: Array<RenderEffect?>?,
) {
    var copyHeight by remember { mutableFloatStateOf(46f) }
    Column(
        Modifier
            .offset(x = 114.dp, y = (Size.cy - copyHeight / 2).dp)
            .width(copyWidth.dp)
            .graphicsLayer {
                translationX =
                    if (state.reduced) {
                        0f
                    } else {
                        ((width / 2 - 66) * (1 - state.peek.value) * density).toFloat()
                    }
            }.drawWithContent {
                val shift =
                    if (state.reduced) {
                        0f
                    } else {
                        ((width / 2 - 66) * (1 - state.peek.value) * density).toFloat()
                    }
                clipRect(right = max(size.width - 2 * shift, 0f)) {
                    this@drawWithContent.drawContent()
                }
            }.onSizeChanged { copyHeight = 2 * ceil(it.height / density / 2) }
            .clearAndSetSemantics { if (state.open) contentDescription = "$name. $status." },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(
            name,
            Modifier.graphicsLayer {
                val presence = ((state.peek.value - 0.35) / 0.4).coerceIn(0.0, 1.0)
                alpha = presence.toFloat()
                translationX = if (state.reduced) 0f else (10 * (1 - presence) * density).toFloat()
                renderEffect =
                    if (state.reduced) {
                        null
                    } else {
                        effects?.get(((1 - presence) * 64).roundToInt().coerceIn(0, 64))
                    }
            },
            style = referralText(17, 22, FontWeight.SemiBold),
            maxLines = 1,
        )
        BasicText(
            status,
            Modifier.graphicsLayer {
                val presence = ((state.peek.value - 0.42) / 0.4).coerceIn(0.0, 1.0)
                alpha = presence.toFloat()
                translationX = if (state.reduced) 0f else (10 * (1 - presence) * density).toFloat()
                renderEffect =
                    if (state.reduced) {
                        null
                    } else {
                        effects?.get(((1 - presence) * 64).roundToInt().coerceIn(0, 64))
                    }
            },
            style = referralText(13, 20).copy(color = Color.White.copy(alpha = Tone.subAlpha)),
            maxLines = 2,
        )
    }
}
