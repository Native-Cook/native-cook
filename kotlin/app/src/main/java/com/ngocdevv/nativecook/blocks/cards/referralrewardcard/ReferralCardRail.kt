package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import kotlin.math.roundToInt

// One gesture surface owns the row and the capsule action; decorative canvases stay silent.
@Composable
internal fun ReferralCardRail(
    state: ReferralState,
    rail: ReferralRailPainter,
    points: ReferralPoints,
    width: Float,
    density: Float,
    effects: Array<androidx.compose.ui.graphics.RenderEffect?>?,
    cardPosition: Offset,
    onRailTop: (Float) -> Unit,
) {
    val pointsLabel =
        stringResource(
            R.string.referralrewardcard_points,
            state.count * ReferralPalette.pointsPerFriend,
        )
    val friendsLabel = stringResource(R.string.referralrewardcard_friends)
    val friendValue =
        stringResource(
            if (state.joined(state.focus)) {
                R.string.referralrewardcard_value_joined
            } else {
                R.string.referralrewardcard_value_pending
            },
            ReferralFriends.names[state.focus],
            state.focus + 1,
        )
    val next = stringResource(R.string.referralrewardcard_next)
    val previous = stringResource(R.string.referralrewardcard_previous)
    val toggle = stringResource(R.string.referralrewardcard_toggle)
    Box(
        Modifier
            .fillMaxWidth()
            .height(Size.railHeight.dp)
            .onGloballyPositioned {
                onRailTop(
                    (it.localToRoot(Offset.Zero).y - cardPosition.y) /
                        density,
                )
            }.graphicsLayer {
                alpha = state.part(1).toFloat()
                translationY =
                    if (state.reduced) 0f else (8 * (1 - state.part(1)) * density).toFloat()
            }.referralRailGestures(state, density),
    ) {
        ReferralRail(state, rail, width, density)
        Box(
            Modifier.matchParentSize().semantics {
                contentDescription = friendsLabel
                stateDescription = friendValue
                progressBarRangeInfo = ProgressBarRangeInfo(state.focus.toFloat(), 0f..6f, 5)
                setProgress { target ->
                    state.step(target.roundToInt() - state.focus)
                    true
                }
                onClick(toggle) {
                    state.touch()
                    state.changePeek(!state.open)
                    true
                }
                customActions =
                    listOf(
                        CustomAccessibilityAction(next) {
                            state.step(1)
                            true
                        },
                        CustomAccessibilityAction(previous) {
                            state.step(-1)
                            true
                        },
                    )
            },
        )
        Canvas(
            Modifier.graphicsLayer().fillMaxWidth().height(74.dp).semantics {
                contentDescription = pointsLabel
            },
        ) {
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                points.draw(canvas, state, width)
                canvas.restore()
            }
        }
        ReferralCapsule(state, width, effects)
    }
}
