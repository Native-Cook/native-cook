package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

/**
 * The "Are you sure to skip -70% offer?" sheet (`ExitSheet`, ConfettiGiftOffer.tsx:102).
 * On the loop it rises on measured positions (3.17 to 3.43 s) over a light scrim; once a touch
 * owns it, its drag handle pulls it down (a spring of 400 ms, damping ratio 0.8) and a pull past
 * 90 dp or faster than 700 dp/s skips the offer. The React Native `Modal` becomes an overlay at
 * the top of the stack that the Back button closes the same way.
 */
@Composable
internal fun ConfettiGiftOfferExitSheet(
    controller: ConfettiGiftOfferController,
    metrics: ConfettiGiftOfferMetrics,
    assets: ConfettiGiftOfferAssets,
    visible: Boolean,
    foreground: Boolean,
    reduced: Boolean,
    ownership: ConfettiGiftOfferPressOwnership,
) {
    val density = LocalDensity.current
    val scope = controller.scope
    val maxHeight = max(240f, metrics.height - metrics.insetTop - 20f)
    val travel = maxHeight + 32f
    val offset = remember { ConfettiGiftOfferValue(travel) }
    var panelHeight by remember { mutableFloatStateOf(metrics.height * .598f) }
    val scroll = rememberScrollState()

    DisposableEffect(visible, foreground, reduced, travel) {
        if (!visible || !foreground) {
            offset.stop()
            if (!visible) offset.set(travel)
        } else if (reduced) {
            offset.set(0f)
        } else {
            offset.spring(scope, 0f, 0.4, 0.8f)
        }
        onDispose { if (visible && foreground) offset.stop() }
    }

    /** `close` (ConfettiGiftOffer.tsx:107): scrim tap, Back button and the accessibility action. */
    fun close() {
        if (!controller.manual) offset.set(panelHeight * (1 - confettiGiftOfferSourceSheet(controller.time).toFloat()))
        controller.ownSheet()
        if (reduced) {
            controller.skip()
        } else {
            offset.spring(scope, travel, 0.3, 1f, overshootClamping = true) { controller.skip() }
        }
    }

    /** `beginReading` (ConfettiGiftOffer.tsx:112): a finger on the sheet's text takes the story over. */
    fun beginReading() {
        if (!controller.manual) offset.spring(scope, 0f, 0.4, 0.8f)
        controller.ownSheet()
    }

    BackHandler(enabled = visible) { close() }
    if (!visible) return

    ConfettiGiftOfferScrollWatcher(
        state = scroll,
        enabled = controller.touchEnabled,
        onBeginDrag = ::beginReading,
        onEndDrag = {},
        onMomentumBegin = ::beginReading,
        onMomentumEnd = {},
    )

    val titleStyle = density.confettiGiftOfferText(Color(0xFF1C1C1E), 30f, 37f, 800, -0.5f, cap = 1.5f)
    val title = remember {
        buildAnnotatedString {
            append("Are you sure to skip\n")
            withStyle(SpanStyle(color = Color(0xFFF0605A))) { append("-70%") }
            append(" offer?")
        }
    }
    val minHeight = if (metrics.fontScale <= 1.3f) metrics.height * 516f / 874f else 0f

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // `outgoing`: the sheet leaves with the offer when the account screen pushes in.
                translationX = if (reduced) 0f else -controller.pushProgress() * metrics.width * density.density
            },
    ) {
        // The scrim; a press on it closes the sheet.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (!controller.manual) {
                        confettiGiftOfferPhase(controller.time, 3.233, 3.4).toFloat()
                    } else {
                        max(0f, 1f - offset.value / travel)
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .19f))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Continue without the sample offer"
                        onClick { close(); true }
                    }
                    .confettiGiftOfferPress(controller.touchEnabled, ownership.onPressIn, ownership.onPressOut, ::close),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = if (reduced) {
                        0f
                    } else if (!controller.manual) {
                        panelHeight * (1 - confettiGiftOfferSourceSheet(controller.time).toFloat()) * density.density
                    } else {
                        offset.value * density.density
                    }
                }
                .heightIn(min = minHeight.dp, max = maxHeight.dp)
                .onSizeChanged { panelHeight = it.height / density.density }
                .background(Color.White, RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                // The sheet is solid to touch: a press on its empty part must not reach the scrim.
                .pointerInput(Unit) { }
                .padding(horizontal = 33.dp),
        ) {
            ConfettiGiftOfferDragZone(
                enabled = controller.touchEnabled,
                reduced = reduced,
                travel = travel,
                offset = offset,
                controller = controller,
                panelHeight = { panelHeight },
                close = ::close,
            )
            ConfettiGiftOfferBlurViewport(
                scrollState = scroll,
                enabled = visible,
                fade = Color.White,
                modifier = Modifier.heightIn(max = (maxHeight - 48f).dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (controller.touchEnabled) Modifier.verticalScroll(scroll) else Modifier)
                        .padding(bottom = (metrics.insetBottom + 18f).dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(178.dp), contentAlignment = Alignment.Center) {
                        ConfettiGiftOfferMascot(assets.worried, 190.dp, Modifier.requiredSize(190.dp))
                    }
                    BasicText(
                        text = title,
                        style = titleStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp),
                    )
                    Column(Modifier.padding(top = 34.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        ConfettiGiftOfferButton(
                            label = "Back to offer",
                            onPress = controller::stay,
                            ownership = ownership,
                            reduced = reduced,
                            enabled = controller.touchEnabled,
                        )
                        ConfettiGiftOfferButton(
                            label = "Skip this offer forever",
                            onPress = controller::skip,
                            ownership = ownership,
                            reduced = reduced,
                            enabled = controller.touchEnabled,
                            style = ConfettiGiftOfferButtonStyle.Danger,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The 30 dp strip with the grab handle (ConfettiGiftOffer.tsx:108 and 116): a one-finger pan that
 * starts after 8 dp of vertical travel, follows the finger (stretching with resistance above the
 * rest position) and decides on release.
 */
@Composable
private fun ConfettiGiftOfferDragZone(
    enabled: Boolean,
    reduced: Boolean,
    travel: Float,
    offset: ConfettiGiftOfferValue,
    controller: ConfettiGiftOfferController,
    panelHeight: () -> Float,
    close: () -> Unit,
) {
    val currentReduced = rememberUpdatedState(reduced)
    val currentPanel = rememberUpdatedState(panelHeight)
    val currentClose = rememberUpdatedState(close)
    Box(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Drag down to skip the offer"
                customActions = listOf(CustomAccessibilityAction("Skip the offer") { currentClose.value(); true })
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(travel) {
                        confettiGiftOfferPan(controller, offset, travel, { currentReduced.value }, { currentPanel.value() })
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(22.dp, 4.dp).background(Color(0xFFC7C7CC), RoundedCornerShape(2.dp)))
    }
}

/** The pan gesture: `Gesture.Pan().maxPointers(1).activeOffsetY([-8, 8])` (ConfettiGiftOffer.tsx:108). */
private suspend fun PointerInputScope.confettiGiftOfferPan(
    controller: ConfettiGiftOfferController,
    offset: ConfettiGiftOfferValue,
    travel: Float,
    reduced: () -> Boolean,
    panelHeight: () -> Float,
) {
    val slop = 8.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPointerInputChange(down)
        var activated = false
        var finished = false
        var initial = 0f
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPointerInputChange(change)
                val travelled = change.position.y - down.position.y
                if (!activated && abs(travelled) >= slop) {
                    activated = true
                    // onStart: take the sheet from wherever the loop had it.
                    offset.stop()
                    if (!controller.manual) {
                        offset.set(panelHeight() * (1 - confettiGiftOfferSourceSheet(controller.time).toFloat()))
                    }
                    initial = offset.value
                    controller.ownSheet()
                }
                if (activated && change.pressed) {
                    // onUpdate: above the rest position the sheet stretches with resistance.
                    val next = initial + travelled / density
                    offset.set(if (next >= 0f) next else next / (1 + abs(next) / 80f) * 0.35f)
                    change.consume()
                }
                if (!change.pressed) {
                    finished = true
                    if (activated) endPan(controller, offset, travel, reduced(), tracker.calculateVelocity().y / density, success = true)
                    break
                }
            }
        } finally {
            if (activated && !finished) endPan(controller, offset, travel, reduced(), 0f, success = false)
        }
    }
}

/** `onEnd` of the pan (ConfettiGiftOffer.tsx:108): skip, or spring back to rest. */
private fun endPan(
    controller: ConfettiGiftOfferController,
    offset: ConfettiGiftOfferValue,
    travel: Float,
    reduced: Boolean,
    velocityY: Float,
    success: Boolean,
) {
    val scope = controller.scope
    if (!success) {
        if (reduced) offset.set(0f) else offset.spring(scope, 0f, 0.4, 0.8f)
        return
    }
    if (offset.value > 90f || velocityY > 700f) {
        if (reduced) {
            controller.skip()
        } else {
            offset.spring(scope, travel, 0.4, 1f, velocity = velocityY, overshootClamping = true) { controller.skip() }
        }
    } else if (reduced) {
        offset.set(0f)
    } else {
        offset.spring(scope, 0f, 0.4, 0.8f, velocity = velocityY)
    }
}
