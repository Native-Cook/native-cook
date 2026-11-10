package com.ngocdevv.nativecook.blocks.onboarding.pindroplocation

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun PinDropLocation(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val densityValue = density.density
    val top = (WindowInsets.statusBars.getTop(density).toDouble() / densityValue).coerceAtLeast(44.0)
    val bottom = (WindowInsets.navigationBars.getBottom(density).toDouble() / densityValue).coerceAtLeast(20.0)
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val art = remember { PinDropLocationArt() }
    val driver = remember(autoPlay, reduced) {
        PinDropLocationDriver(autoPlay && !reduced, reduced) { kind ->
            val feedback = when (kind) {
                1 -> HapticFeedbackConstants.CLOCK_TICK
                2 -> HapticFeedbackConstants.CONFIRM
                else -> HapticFeedbackConstants.KEYBOARD_TAP
            }
            view.performHapticFeedback(feedback)
        }
    }
    val initialFrame = remember(driver, previewTime) {
        if (previewTime != null) PinDropLocationMotion.frameAt(previewTime) else PinDropLocationMotion.frameAt(0.0)
    }
    var frame by remember(driver, previewTime) { mutableStateOf(initialFrame) }
    var allowed by remember(driver) { mutableStateOf(driver.allowed) }
    var wake by remember(driver) { mutableIntStateOf(0) }

    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }

    LaunchedEffect(driver, lifecycle, wake, previewTime) {
        if (previewTime != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (driver.moving) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.016 else ((nanos - previous) / 1e9).coerceIn(0.0, 0.5)
                    previous = nanos
                    frame = driver.step(dt)
                    if (allowed != driver.allowed) allowed = driver.allowed
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = remember(maxWidth.value, maxHeight.value, top, bottom) {
            PinDropLocationLayout(maxWidth.value.toDouble(), maxHeight.value.toDouble(), top, bottom)
        }
        val mapLeft = layout.discX - layout.discR - 20.0
        val mapTop = layout.discY - layout.discR - 20.0
        val mapWidth = layout.discR * 2.0 + 40.0
        val mapHeight = mapWidth

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(driver, layout, previewTime, reduced) {
                    if (previewTime != null) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x.toDouble() / densityValue
                        val startY = down.position.y.toDouble() / densityValue
                        val mapTarget = startX >= mapLeft && startX <= mapLeft + mapWidth && startY >= mapTop && startY <= mapTop + mapHeight
                        val allowTarget = startX >= layout.allowX && startX <= layout.allowX + layout.allowW && startY >= layout.allowY && startY <= layout.allowY + layout.allowH
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var end = down.position
                        var panning = false
                        var cancelled = false
                        if (mapTarget || allowTarget) down.consume()
                        if (mapTarget) {
                            driver.beginMap()
                            wake++
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                end = change.position
                                tracker.addPosition(change.uptimeMillis, end)
                                val x = end.x.toDouble() / densityValue
                                val y = end.y.toDouble() / densityValue
                                val dx = x - startX
                                val dy = y - startY
                                if (mapTarget && !panning && hypot(dx, dy) >= 6.0) {
                                    panning = true
                                    driver.startPan()
                                    wake++
                                }
                                if (panning) driver.updatePan(dx, dy, layout.discR)
                                if (mapTarget || allowTarget) change.consume()
                                if (!change.pressed) break
                            }
                        } catch (_: Throwable) {
                            cancelled = true
                        } finally {
                            val velocity = tracker.calculateVelocity()
                            val endX = end.x.toDouble() / densityValue
                            val endY = end.y.toDouble() / densityValue
                            val isTap = hypot(endX - startX, endY - startY) < 8.0
                            if (panning) {
                                driver.finishPan(velocity.x.toDouble() / densityValue, layout.discR, cancelled)
                            } else if (!cancelled && mapTarget && isTap) {
                                driver.tapMap()
                            } else if (!cancelled && allowTarget && isTap) {
                                driver.allow()
                            }
                            frame = driver.frame()
                            allowed = driver.allowed
                            wake++
                        }
                    }
                }
        ) {
            drawIntoCanvas { canvas ->
                art.draw(canvas.nativeCanvas, layout, frame, allowed, reduced, densityValue)
            }
        }

        Box(
            Modifier
                .offsetDp(mapLeft, mapTop, densityValue)
                .sizeDp(mapWidth, mapHeight)
                .semantics {
                    role = Role.Image
                    contentDescription = if (allowed) "Map zoomed in on your pin, with three places nearby" else "Map with your pin"
                    stateDescription = if (allowed) "Location on" else "Location off"
                    customActions = listOf(
                        CustomAccessibilityAction("Increment") {
                            driver.accessibilityShift(1)
                            frame = driver.frame()
                            wake++
                            true
                        },
                        CustomAccessibilityAction("Decrement") {
                            driver.accessibilityShift(-1)
                            frame = driver.frame()
                            wake++
                            true
                        },
                    )
                    onClick {
                        driver.tapMap()
                        frame = driver.frame()
                        wake++
                        true
                    }
                },
        )
        Box(
            Modifier
                .offsetDp(layout.skipX, layout.skipY, densityValue)
                .sizeDp(layout.skipW, layout.skipH)
                .semantics {
                    role = Role.Button
                    contentDescription = PinDropLocationArt.SKIP
                    onClick { true }
                },
        )
        Box(
            Modifier
                .offsetDp(0.0, layout.titleY - 11.0, densityValue)
                .sizeDp(layout.w, 32.0)
                .semantics {
                    heading()
                    contentDescription = PinDropLocationArt.TITLE
                },
        )
        Box(
            Modifier
                .offsetDp((layout.w - layout.bodyW) * 0.5, layout.bodyY - 14.0, densityValue)
                .sizeDp(layout.bodyW, 88.0)
                .semantics { contentDescription = PinDropLocationArt.BODY.replace('\n', ' ') },
        )
        Box(
            Modifier
                .offsetDp(layout.allowX, layout.allowY, densityValue)
                .sizeDp(layout.allowW, layout.allowH)
                .semantics {
                    role = Role.Button
                    contentDescription = if (allowed) PinDropLocationArt.DONE else PinDropLocationArt.ALLOW
                    if (allowed) disabled()
                    onClick {
                        if (!allowed) {
                            driver.allow()
                            frame = driver.frame()
                            allowed = driver.allowed
                            wake++
                        }
                        true
                    }
                },
        )
        if (frame.places > 0.0) {
            for (index in 0..2) {
                val opacity = (frame.places * 1.6 - index * 0.25).coerceIn(0.0, 1.0)
                if (opacity <= 0.0) continue
                val x = layout.discX + PinDropLocationArt.PLACE_X[index] * layout.discR
                val y = layout.discY + PinDropLocationArt.PLACE_Y[index] * layout.discR
                val left = if (PinDropLocationArt.PLACE_SIDE[index] < 0) x - 190.0 else x + 10.0
                Box(
                    Modifier
                        .offsetDp(left, y - 13.0, densityValue)
                        .sizeDp(180.0, 26.0)
                        .semantics { contentDescription = PinDropLocationArt.PLACE_NAMES[index] },
                )
            }
        }
    }
}

private fun Modifier.offsetDp(x: Double, y: Double, density: Float): Modifier =
    offset { IntOffset((x * density).roundToInt(), (y * density).roundToInt()) }

private fun Modifier.sizeDp(width: Double, height: Double): Modifier =
    this.then(Modifier.size(width.dp, height.dp))
