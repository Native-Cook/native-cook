package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion

/**
 * A full-screen refresh target whose lever remains at its 301 dp source-artboard
 * position. Only the artboard's vertical origin follows the available height.
 */
@Composable
fun PullLeverRefresh(
    autoPlay: Boolean = false,
    onRefresh: (suspend () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current.density
    val state = rememberPullLeverRefreshState(
        autoPlay = autoPlay,
        reduceMotion = reducedMotion,
        onRefresh = onRefresh,
    )
    val description = when (state.label) {
        PullLeverRefreshLabel.REFRESHING -> "Refreshing"
        PullLeverRefreshLabel.UPDATED -> "Updated just now"
        PullLeverRefreshLabel.NONE -> "Ready"
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PullLeverRefreshTheme.Color.background)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Refresh"
                stateDescription = description
                liveRegion = LiveRegionMode.Polite
                onClick("Pull the lever down, or double tap, to refresh") {
                    state.refreshNow()
                    true
                }
            }
            .pointerInput(state, density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocity = VelocityTracker()
                    velocity.addPosition(down.uptimeMillis, down.position)
                    state.onTouchDown()
                    var dragStarted = false
                    var totalY = 0f
                    var ended = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) break
                            velocity.addPosition(change.uptimeMillis, change.position)
                            val deltaY = change.positionChange().y / density
                            totalY += deltaY
                            if (!dragStarted && totalY >= PullLeverRefreshTokens.Gesture.activationDistance) {
                                dragStarted = true
                                state.onDragStart()
                                state.onDrag(totalY)
                            } else if (dragStarted && deltaY != 0f) {
                                state.onDrag(deltaY)
                            }
                            if (!change.pressed) {
                                if (dragStarted) {
                                    state.onDragEnd(
                                        velocityY = velocity.calculateVelocity().y / density,
                                        cancelled = false,
                                    )
                                } else {
                                    state.onTouchUp()
                                }
                                ended = true
                                change.consume()
                                break
                            }
                            change.consume()
                        }
                    } finally {
                        if (!ended) {
                            if (dragStarted) state.onDragEnd(cancelled = true) else state.onTouchUp()
                        }
                    }
                }
            },
    ) {
        val artboardTop = (maxHeight - PullLeverRefreshTheme.Artboard.height.dp) / 2f
        val leverCentre = artboardTop + PullLeverRefreshTheme.Artboard.leverCentreY.dp
        PullLeverRefreshDrawing(
            state = state,
            modifier = Modifier.offset(
                x = (PullLeverRefreshTheme.Artboard.leverLeft - 80f).dp,
                y = leverCentre - 120.dp,
            ),
        )
        Box(
            modifier = Modifier
                .offset(y = leverCentre - PullLeverRefreshTheme.Type.caption.lineHeight.dp / 2f)
                .width((PullLeverRefreshTheme.Artboard.leverLeft - PullLeverRefreshTheme.Layout.captionGap).dp)
                .height(PullLeverRefreshTheme.Type.caption.lineHeight.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (!PullLeverRefreshMotion.REPLICA_ONLY) {
                PullLeverRefreshCaption(
                    text = state.label.text,
                    reducedMotion = reducedMotion,
                    color = PullLeverRefreshTheme.Color.mute,
                    textStyle = TextStyle(
                        fontSize = PullLeverRefreshTheme.Type.caption.fontSize.sp,
                        lineHeight = PullLeverRefreshTheme.Type.caption.lineHeight.sp,
                        fontWeight = PullLeverRefreshTheme.Type.caption.fontWeight,
                        letterSpacing = PullLeverRefreshTheme.Type.caption.letterSpacing.sp,
                    ),
                )
            }
        }
    }
}
