package com.ngocdevv.nativecook.blocks.lists.giftcardpicker

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.pow

@Composable
fun GiftCardPicker(autoPlay: Boolean = false, freezeAt: Long? = null) {
    val reduceMotion = rememberReducedMotion()
    val state = remember(autoPlay, reduceMotion, freezeAt) {
        GiftCardPickerMotionState(reduceMotion).apply {
            configureAutoPlay(autoPlay && freezeAt == null)
            if (freezeAt != null) freezeAt(freezeAt)
        }
    }
    val density = LocalDensity.current
    val view = LocalView.current
    val activity = LocalActivity.current

    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (previousStatus != null) controller?.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) controller?.isAppearanceLightNavigationBars = previousNavigation
        }
    }

    LaunchedEffect(state, state.frameEpoch, autoPlay, reduceMotion) {
        if (freezeAt != null) return@LaunchedEffect
        val continuous = autoPlay && !reduceMotion
        while (isActive) {
            withFrameNanos(state::frame)
            if (!continuous && !state.needsFrames(SystemClock.uptimeMillis())) break
        }
    }

    LaunchedEffect(state, state.resumeGeneration, autoPlay, reduceMotion) {
        if (!autoPlay || reduceMotion || !state.resumePending) return@LaunchedEffect
        val generation = state.resumeGeneration
        delay(GiftCardPickerMotionSpec.ResumeAfterMs)
        if (state.resumePending && state.resumeGeneration == generation) {
            state.resume(SystemClock.uptimeMillis())
            state.wakeFrames()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(GiftCardPickerTheme.Page)
            .pointerInput(state, autoPlay, freezeAt) {
                if (freezeAt != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    state.touchStarted(down.uptimeMillis)
                    var change = down
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                    } while (change.pressed)
                    state.touchEnded(change.uptimeMillis.takeIf { it > 0L } ?: SystemClock.uptimeMillis())
                    state.wakeFrames()
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val sheetTop = topInset + GiftCardPickerLayout.Sheet.Top - 62f
        val sheetHeight = (height - sheetTop).coerceAtLeast(0f)
        val ky = sheetHeight / (GiftCardPickerLayout.ScreenHeight - GiftCardPickerLayout.Sheet.Top)
        val sx = width / GiftCardPickerLayout.ScreenWidth
        val dy = topInset - 62f
        val bottomDelta = bottomInset - 34f
        val layer = state.layer

        GiftCardPickerPortfolio(
            width = width,
            height = height,
            dy = dy,
            bottomDelta = bottomDelta,
            visible = layer == 0 && freezeAt == null,
            state = state,
            onGift = {
                giftCardPickerLightHaptic(view)
                state.present(SystemClock.uptimeMillis())
                state.wakeFrames()
            },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.sheet.value }
                .background(GiftCardPickerTheme.Dim)
                .clearAndSetSemantics { },
        )

        Box(
            modifier = Modifier
                .offset(y = sheetTop.dp)
                .fillMaxWidth()
                .height(sheetHeight.dp)
                .graphicsLayer {
                    alpha = if (reduceMotion) state.sheet.value else 1f
                    translationY = if (reduceMotion) 0f else {
                        (1f - state.sheet.value) * (sheetHeight + 20f) * density.density
                    }
                }
                .clip(RoundedCornerShape(topStart = GiftCardPickerLayout.Sheet.Radius.dp, topEnd = GiftCardPickerLayout.Sheet.Radius.dp))
                .background(GiftCardPickerTheme.SheetLight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = if (reduceMotion) 0f else {
                            -state.push.value * width * GiftCardPickerMotionSpec.UnderShift * density.density
                        }
                    }
                    .then(if (layer == 1) Modifier else Modifier.semantics { hideFromAccessibility() }),
            ) {
                GiftCardPickerDiscoverSheet(
                    state = state,
                    width = width,
                    height = sheetHeight,
                    sx = sx,
                    ky = ky,
                    reduceMotion = reduceMotion,
                    view = view,
                    enabled = layer == 1 && freezeAt == null,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val push = state.push.value
                        alpha = if (reduceMotion) push else if (push > 0.001f) 1f else 0f
                        translationX = if (reduceMotion) 0f else {
                            (1f - push) * width * density.density
                        }
                    }
                    .then(if (layer == 2) Modifier else Modifier.semantics { hideFromAccessibility() }),
            ) {
                GiftCardPickerStockSheet(
                    state = state,
                    width = width,
                    height = sheetHeight,
                    ky = ky,
                    view = view,
                    reduceMotion = reduceMotion,
                    enabled = layer == 2 && freezeAt == null,
                    onChoose = {
                        giftCardPickerLightHaptic(view)
                        state.commitSelection(SystemClock.uptimeMillis())
                    },
                )
            }

            GiftCardPickerCloseButton(
                state = state,
                enabled = freezeAt == null && (layer == 1 || layer == 2),
                onClick = {
                    giftCardPickerLightHaptic(view)
                    state.dismiss(SystemClock.uptimeMillis())
                    state.wakeFrames()
                },
            )
        }

        if (layer == 3 && state.commit.value > 0f) {
            GiftCardPickerSelectionFlight(
                state = state,
                width = width,
                sheetTop = sheetTop,
                sheetHeight = sheetHeight,
                reduceMotion = reduceMotion,
            )
        }
    }
}

@Composable
private fun GiftCardPickerCloseButton(
    state: GiftCardPickerMotionState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(
                x = GiftCardPickerLayout.Sheet.CloseLeft.dp,
                y = GiftCardPickerLayout.Sheet.CloseTop.dp,
            )
            .size(GiftCardPickerLayout.Sheet.CloseSize.dp)
            .graphicsLayer {
                val push = state.push.value
                alpha = 1f - (state.commit.value * if (state.commit.value > 0f) 15.875f else 0f).coerceIn(0f, 1f)
                shadowElevation = 10.dp.toPx() * (1f - push)
                val shadow = Color.Black.copy(alpha = 0.08f * (1f - push))
                ambientShadowColor = shadow
                spotShadowColor = shadow
                shape = CircleShape
                clip = false
            }
            .drawBehind {
                val push = state.push.value
                val reveal = state.reveal.value
                val closeBackground = if (reveal > 0f) {
                    giftCardPickerGammaColor(
                        from = Color(0xFF5A5A5E),
                        to = Color.White.copy(alpha = 0.06f),
                        progress = reveal.coerceIn(0f, 1f),
                    )
                } else {
                    giftCardPickerGammaColor(
                        from = Color.White,
                        to = Color(0xFF5A5A5E),
                        progress = push.coerceIn(0f, 1f),
                    )
                }
                drawCircle(closeBackground)
            }
            .semantics {
                role = Role.Button
                contentDescription = "Close"
            }
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled) Modifier else Modifier.semantics { hideFromAccessibility() }),
        contentAlignment = Alignment.Center,
    ) {
        GiftCardPickerIcon(
            kind = GiftCardPickerIconKind.Close,
            modifier = Modifier.size(18.dp).graphicsLayer {
                alpha = 1f - (state.push.value * 8f).coerceIn(0f, 1f)
            },
            color = GiftCardPickerTheme.Ink,
        )
        GiftCardPickerIcon(
            kind = GiftCardPickerIconKind.Close,
            modifier = Modifier.size(18.dp).graphicsLayer {
                alpha = ((state.push.value - 0.9f) * 10f).coerceIn(0f, 1f)
            },
            color = Color.White,
        )
    }
}

private fun giftCardPickerLightHaptic(view: android.view.View) {
    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

private fun giftCardPickerGammaColor(from: Color, to: Color, progress: Float): Color {
    fun channel(start: Float, end: Float): Float {
        val amount = progress.toDouble()
        val linear = start.toDouble().pow(2.2) * (1.0 - amount) + end.toDouble().pow(2.2) * amount
        return linear.pow(1.0 / 2.2).toFloat()
    }
    return Color(
        red = channel(from.red, to.red),
        green = channel(from.green, to.green),
        blue = channel(from.blue, to.blue),
        alpha = from.alpha + (to.alpha - from.alpha) * progress,
    )
}
