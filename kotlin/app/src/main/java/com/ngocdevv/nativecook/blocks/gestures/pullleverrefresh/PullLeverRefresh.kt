package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.ngocdevv.nativecook.shared.rememberReducedMotion

@Composable
fun PullLeverRefresh(
    autoPlay: Boolean = false,
    onRefresh: (suspend () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current.density
    val state = rememberPullLeverRefreshState(autoPlay, reducedMotion, onRefresh)
    val safePadding = WindowInsets.safeDrawing.asPaddingValues()
    val insetTop = safePadding.calculateTopPadding().value
    val insetBottom = safePadding.calculateBottomPadding().value
    val readout = state.readout

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Espresso lever"
                stateDescription = when (state.stage) {
                    PullLeverRefreshStage.BREWING -> "Brewing espresso"
                    PullLeverRefreshStage.DONE -> "Espresso ready"
                    PullLeverRefreshStage.FULL -> "Nine bar, release to brew"
                    PullLeverRefreshStage.PUMPING -> "Building pressure"
                    PullLeverRefreshStage.REST -> "Ready to pump"
                }
                onClick("Pump a full espresso shot") {
                    state.activate()
                    true
                }
            },
    ) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val layout = remember(widthDp, heightDp, insetTop, insetBottom) {
            pullLeverRefreshLayout(widthDp, heightDp, insetTop, insetBottom)
        }
        state.updateLayout(layout)
        val background = remember(widthDp, heightDp, density, layout) {
            PullLeverRefreshRenderer.renderStaticBackground(widthDp, heightDp, density, layout).asImageBitmap()
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state, density, layout) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        state.beginTouch(down.position.x / density, down.position.y / density)
                        var ended = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    state.dragTo(
                                        x = change.position.x / density,
                                        y = change.position.y / density,
                                        deltaY = delta.y / density,
                                    )
                                    change.consume()
                                }
                                if (!change.pressed) {
                                    state.endTouch(tracker.calculateVelocity().y / density, cancelled = false)
                                    ended = true
                                    break
                                }
                            }
                        } finally {
                            if (!ended) state.endTouch(0f, cancelled = true)
                        }
                    }
                },
        ) {
            drawImage(background, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            drawIntoCanvas { composeCanvas ->
                val native = composeCanvas.nativeCanvas
                native.save()
                native.scale(density, density)
                PullLeverRefreshRenderer.drawDynamic(native, layout, state)
                native.restore()
            }
        }

        Column(
            modifier = Modifier
                .offset(x = PullLeverRefreshTokens.Layout.Gutter.dp, y = (insetTop + PullLeverRefreshTokens.Layout.CopyTop).dp)
                .widthIn(max = 345.dp),
        ) {
            Text(
                text = readout.eyebrow,
                color = androidx.compose.ui.graphics.Color(PullLeverRefreshTokens.Color.Eyebrow),
                style = TextStyle(
                    fontSize = PullLeverRefreshTokens.Type.Eyebrow.sp,
                    lineHeight = PullLeverRefreshTokens.Type.EyebrowLine.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = PullLeverRefreshTokens.Type.EyebrowTracking.sp,
                ),
            )
            Box(Modifier.padding(top = PullLeverRefreshTokens.Layout.CopyGap.dp)) {
                RollingLine(
                    value = readout.title,
                    reducedMotion = reducedMotion,
                    style = TextStyle(
                        color = androidx.compose.ui.graphics.Color(PullLeverRefreshTokens.Color.Ink),
                        fontSize = PullLeverRefreshTokens.Type.Title.sp,
                        lineHeight = PullLeverRefreshTokens.Type.TitleLine.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = PullLeverRefreshTokens.Type.TitleTracking.sp,
                    ),
                )
            }
            Box(Modifier.padding(top = PullLeverRefreshTokens.Layout.SubGap.dp)) {
                RollingLine(
                    value = readout.subtitle,
                    reducedMotion = reducedMotion,
                    style = TextStyle(
                        color = androidx.compose.ui.graphics.Color(PullLeverRefreshTokens.Color.Mute),
                        fontSize = PullLeverRefreshTokens.Type.Sub.sp,
                        lineHeight = PullLeverRefreshTokens.Type.SubLine.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = PullLeverRefreshTokens.Type.SubTracking.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RollingLine(value: String, reducedMotion: Boolean, style: TextStyle) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(PullLeverRefreshTokens.Timing.Reduced.toInt())) togetherWith
                    fadeOut(tween(PullLeverRefreshTokens.Timing.Reduced.toInt()))
            } else {
                (slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(PullLeverRefreshTokens.Timing.SubRoll.toInt())) +
                    fadeIn(tween(PullLeverRefreshTokens.Timing.SubRoll.toInt()))) togetherWith
                    (slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(PullLeverRefreshTokens.Timing.SubRoll.toInt())) +
                        fadeOut(tween(PullLeverRefreshTokens.Timing.SubRoll.toInt())))
            }
        },
        label = "espresso copy roll",
    ) { text ->
        Text(text = text, style = style, maxLines = 1)
    }
}
