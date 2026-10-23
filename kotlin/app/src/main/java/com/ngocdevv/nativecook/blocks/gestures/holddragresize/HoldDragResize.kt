package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.min
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HoldDragResize(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val activity = LocalActivity.current
    val safeInsets = WindowInsets.safeDrawing
    val safeTop = safeInsets.getTop(density) / density.density
    val safeBottom = safeInsets.getBottom(density) / density.density
    val assets = rememberHoldDragResizeAssets()
    val renderer = remember(assets) { HoldDragResizeRenderer(assets) }
    val simulation = remember(autoPlay) { HoldDragResizeSimulation(autoPlay, reducedMotion) }
    var widthDp by remember { mutableDoubleStateOf(Tokens.REFERENCE_WIDTH) }
    var heightDp by remember { mutableDoubleStateOf(Tokens.REFERENCE_HEIGHT) }
    var redraw by remember { mutableLongStateOf(0L) }
    var accessibilitySizeIndex by remember { mutableIntStateOf(0) }
    var accessibilityPlaying by remember { mutableStateOf(true) }

    fun flushHaptics() {
        simulation.events.forEach { event ->
            val feedback = when (event) {
                HoldDragResizeSimulation.Haptic.Lift -> HapticFeedbackConstants.LONG_PRESS
                HoldDragResizeSimulation.Haptic.Detent -> HapticFeedbackConstants.CLOCK_TICK
                HoldDragResizeSimulation.Haptic.Tap -> HapticFeedbackConstants.KEYBOARD_TAP
                HoldDragResizeSimulation.Haptic.Land -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.CONTEXT_CLICK
                }
            }
            view.performHapticFeedback(feedback)
        }
        simulation.events.clear()
    }

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousSystemUi = view.systemUiVisibility
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            view.systemUiVisibility = previousSystemUi
        }
    }

    LaunchedEffect(simulation, widthDp, reducedMotion) {
        var previousNanos = withFrameNanos { it }
        while (isActive) {
            val nowNanos = withFrameNanos { it }
            val delta = min((nowNanos - previousNanos) / 1e9, Tokens.Timing.frameDeltaMax)
            previousNanos = nowNanos
            simulation.reducedMotion = reducedMotion
            simulation.step(
                delta = delta,
                width = widthDp,
                previewCentreY = Tokens.frameTops[0] + Tokens.sizes[0] / 2.0,
            )
            if (accessibilitySizeIndex != simulation.sizeIndex) {
                accessibilitySizeIndex = simulation.sizeIndex
            }
            if (accessibilityPlaying != simulation.playing) {
                accessibilityPlaying = simulation.playing
            }
            flushHaptics()
            redraw = nowNanos
        }
    }

    val background = remember { Color(android.graphics.Color.parseColor(Tokens.Color.background)) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .onSizeChanged { size ->
                widthDp = size.width / density.density.toDouble()
                heightDp = size.height / density.density.toDouble()
            }
            .semantics {
                contentDescription = "Video preview"
                stateDescription = Tokens.Copy.accessibilitySizeNames[accessibilitySizeIndex]
                customActions = listOf(
                    CustomAccessibilityAction("Increase preview size") {
                        simulation.stepSize(1)
                        true
                    },
                    CustomAccessibilityAction("Decrease preview size") {
                        simulation.stepSize(-1)
                        true
                    },
                    CustomAccessibilityAction(if (accessibilityPlaying) "Pause video" else "Play video") {
                        simulation.tapPreview()
                        flushHaptics()
                        true
                    },
                    CustomAccessibilityAction("Dismiss coach mark") {
                        simulation.dismissCoach()
                        true
                    },
                )
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(simulation, density, widthDp, heightDp, safeTop, safeBottom) {
                    val velocityTracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val scale = density.density.toDouble()
                        val downX = down.position.x / scale
                        val downY = down.position.y / scale
                        simulation.touchStart()
                        var released = false
                        try {
                            if (simulation.coachOn) {
                                val insideButton = downX in Tokens.Coach.buttonX..(Tokens.Coach.buttonX + Tokens.Coach.buttonWidth) &&
                                    downY in Tokens.Coach.buttonY..(Tokens.Coach.buttonY + Tokens.Coach.buttonHeight)
                                if (insideButton) {
                                    simulation.controlDown(Tokens.Control.gotIt)
                                }
                                val up = waitForRelease(down)
                                simulation.controlUp()
                                released = true
                                if (insideButton && up != null) {
                                    simulation.dismissCoach()
                                }
                                return@awaitEachGesture
                            }
                            val frame = HoldDragResizeGeometry.preview(simulation.shown, widthDp)
                            val insidePreview = downX in frame.left..(frame.left + frame.width) &&
                                downY in frame.top..(frame.top + frame.height)
                            if (insidePreview) {
                                simulation.pressPreview()
                                velocityTracker.resetTracking()
                                velocityTracker.addPointerInputChange(down)
                                var latest = down
                                val releasedBeforeHold = withTimeoutOrNull(Tokens.Resize.holdMs) {
                                    while (latest.pressed) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                        latest = change
                                        velocityTracker.addPointerInputChange(change)
                                        change.consume()
                                    }
                                    true
                                }
                                if (releasedBeforeHold == true) {
                                    simulation.cancelPreviewPress()
                                    simulation.tapPreview()
                                    released = true
                                    return@awaitEachGesture
                                }
                                simulation.beginHold()
                                simulation.moveHold((latest.position.y - down.position.y) / scale)
                                while (latest.pressed) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    latest = change
                                    velocityTracker.addPointerInputChange(change)
                                    simulation.moveHold((change.position.y - down.position.y) / scale)
                                    change.consume()
                                }
                                val velocity = velocityTracker.calculateVelocity()
                                simulation.endHold(velocity.y / scale)
                                released = true
                                return@awaitEachGesture
                            }
                            val barY = safeTop + Tokens.Layout.barY
                            if (insideCircle(downX, downY, Tokens.Layout.gutter + Tokens.Layout.barButton / 2.0, barY, Tokens.Layout.barButton / 2.0)) {
                                simulation.controlDown(Tokens.Control.topLeft)
                                val up = waitForRelease(down)
                                simulation.controlUp()
                                released = true
                                if (up != null) {
                                    if (simulation.sizeIndex == Tokens.sizes.lastIndex) {
                                        simulation.stepSize(-1)
                                    } else {
                                        simulation.collapse()
                                    }
                                }
                                return@awaitEachGesture
                            }
                            val topRightX = widthDp - Tokens.Layout.gutter - Tokens.Layout.barButton / 2.0
                            if (insideCircle(downX, downY, topRightX, barY, Tokens.Layout.barButton / 2.0)) {
                                simulation.controlDown(Tokens.Control.topRight)
                                waitForRelease(down)
                                simulation.controlUp()
                                released = true
                                return@awaitEachGesture
                            }
                            val groupTop = Tokens.frameTops[0] + Tokens.sizes[0] + HoldDragResizeGeometry.belowShift(simulation.lag)
                            val controlY = groupTop + Tokens.Layout.controlsGap
                            val playX = Tokens.Layout.gutter + Tokens.Layout.control / 2.0
                            if (insideCircle(downX, downY, playX, controlY, Tokens.Layout.control / 2.0)) {
                                simulation.controlDown(Tokens.Control.timelinePlay)
                                val up = waitForRelease(down)
                                simulation.controlUp()
                                released = true
                                if (up != null) {
                                    simulation.tapPreview()
                                }
                                return@awaitEachGesture
                            }
                            if (simulation.sizeIndex == Tokens.sizes.lastIndex) {
                                val pillTop = heightDp - safeBottom - Tokens.Layout.pillsBottom - Tokens.Layout.pillHeight
                                val edit = downX in Tokens.Layout.gutter..(Tokens.Layout.gutter + 112.0) && downY in pillTop..(pillTop + Tokens.Layout.pillHeight)
                                val nextLeft = widthDp - Tokens.Layout.gutter - 91.0
                                val next = downX in nextLeft..(nextLeft + 91.0) && downY in pillTop..(pillTop + Tokens.Layout.pillHeight)
                                if (edit) {
                                    simulation.controlDown(Tokens.Control.editVideo)
                                    val up = waitForRelease(down)
                                    simulation.controlUp()
                                    released = true
                                    if (up != null) {
                                        simulation.stepSize(-1)
                                    }
                                    return@awaitEachGesture
                                }
                                if (next) {
                                    simulation.controlDown(Tokens.Control.next)
                                    waitForRelease(down)
                                    simulation.controlUp()
                                    released = true
                                    return@awaitEachGesture
                                }
                            } else {
                                val toolTop = heightDp - safeBottom - Tokens.Layout.tools + Tokens.Squeeze.tools * HoldDragResizeGeometry.squeeze(simulation.shown) + Tokens.Layout.toolTop
                                if (downY in toolTop..(toolTop + Tokens.Layout.toolH)) {
                                    val toolIndex = ((downX - Tokens.Layout.toolInset) / (Tokens.Layout.tool + Tokens.Layout.toolGap)).toInt()
                                    if (toolIndex in Tokens.Copy.tools.indices) {
                                        val toolLeft = Tokens.Layout.toolInset + toolIndex * (Tokens.Layout.tool + Tokens.Layout.toolGap)
                                        if (downX in toolLeft..(toolLeft + Tokens.Layout.tool)) {
                                            simulation.controlDown(Tokens.Control.firstTool + toolIndex)
                                            waitForRelease(down)
                                            simulation.controlUp()
                                            released = true
                                            return@awaitEachGesture
                                        }
                                    }
                                }
                            }
                            waitForRelease(down)
                            released = true
                        } finally {
                            simulation.cancelPreviewPress()
                            simulation.controlUp()
                            if (!released) {
                                simulation.endHold(0.0)
                            }
                            simulation.touchEnd()
                            flushHaptics()
                        }
                    }
                },
        ) {
            @Suppress("UNUSED_VARIABLE")
            val frameInvalidation = redraw
            drawIntoCanvas { composeCanvas ->
                composeCanvas.nativeCanvas.save()
                composeCanvas.nativeCanvas.scale(density.density, density.density)
                renderer.draw(
                    canvas = composeCanvas.nativeCanvas,
                    simulation = simulation,
                    screenWidth = widthDp.toDouble(),
                    screenHeight = heightDp.toDouble(),
                    safeTop = safeTop.toDouble(),
                    safeBottom = safeBottom.toDouble(),
                    pixelScale = density.density,
                )
                composeCanvas.nativeCanvas.restore()
            }
        }
    }
}

private fun insideCircle(
    x: Double,
    y: Double,
    centreX: Double,
    centreY: Double,
    radius: Double,
): Boolean {
    val dx = x - centreX
    val dy = y - centreY
    return dx * dx + dy * dy <= radius * radius
}
