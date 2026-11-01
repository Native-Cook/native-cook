package com.ngocdevv.nativecook.blocks.sheets.dronedroptracker

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

/** Full-screen order tracking map: the drone follows the RN route and the sheet is directly draggable. */
@Composable
fun DroneDropTracker(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reducedMotion = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
    val art = remember(context) { DroneDropTrackerArt(context) }
    val driver = remember(autoPlay, reducedMotion, previewTime) { DroneDropTrackerDriver() }
    val initialFrame = remember(driver, previewTime, reducedMotion) {
        if (previewTime != null) {
            val script = DroneDropTrackerMotion.scriptAt(previewTime)
            DroneDropTrackerMotion.Frame(script.f, script.drop, script.carried, script.back, script.sheet, previewTime, false)
        } else if (reducedMotion) {
            val script = DroneDropTrackerMotion.scriptAt(0.0)
            DroneDropTrackerMotion.Frame(script.f, script.drop, script.carried, script.back, script.sheet, 0.0, false)
        } else {
            driver.frame(t = 0.0)
        }
    }
    var frame by remember(driver) { mutableStateOf(initialFrame) }
    var drawRevision by remember(driver) { mutableLongStateOf(0L) }
    val statusInsetPx = WindowInsets.statusBars.getTop(density).toFloat()
    val bottomInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()

    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (oldStatus != null) controller?.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) controller?.isAppearanceLightNavigationBars = oldNavigation
        }
    }

    LaunchedEffect(driver, lifecycle, reducedMotion, previewTime) {
        if (previewTime != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (isActive) {
                withFrameNanos { nanos ->
                    val rawWall = if (previous == 0L) 0.016 else (nanos - previous) / 1e9
                    previous = nanos
                    frame = driver.tick(rawWall, autoPlay && !reducedMotion)
                    if (driver.justDelivered) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        } else {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                    drawRevision++
                }
            }
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Delivery map. Drag sideways to replay the flight, or drag the order details up to open it."
                onClick(label = "Open order details") { true }
            }
            .pointerInput(driver, reducedMotion, previewTime, density.density) {
                if (previewTime != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toDouble() / density.density
                    val height = size.height.toDouble() / density.density
                    val topInset = statusInsetPx / density.density
                    val bottomInset = bottomInsetPx / density.density
                    val sheetMin = 150.0 + bottomInset
                    val sheetMax = height * 0.55
                    val sheetRange = (sheetMax - sheetMin).coerceAtLeast(1.0)
                    val mapTop = topInset + 56.0
                    val mapHeight = height - topInset - 56.0 - sheetMin + 30.0
                    val startX = down.position.x.toDouble() / density.density
                    val startY = down.position.y.toDouble() / density.density
                    val sheetTop = height - sheetMax + (1.0 - driver.sheet) * sheetRange
                    // RN's Pan gesture wraps only sheetTop (grabber, summary row, and progress bar),
                    // not the detail rows or action buttons below it.
                    val isSheet = startY >= sheetTop && startY <= sheetTop + 105.0
                    val isMap = !isSheet && startY >= mapTop && startY < mapTop + mapHeight - 40.0
                    if (isSheet) driver.beginSheetDrag()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var ended = false
                    down.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val x = change.position.x.toDouble() / density.density
                            val y = change.position.y.toDouble() / density.density
                            if (isSheet && driver.draggingSheet) {
                                driver.updateSheetDrag(y - startY, sheetRange)
                            } else if (isMap) {
                                // Match Gesture.Pan.activeOffsetX([-8, 8]): vertical or tiny
                                // horizontal touches never enter scrub mode.
                                if (!driver.scrubbingMap && kotlin.math.abs(x - startX) > 8.0) {
                                    driver.beginMapScrub()
                                }
                                if (driver.scrubbingMap) driver.updateMapScrub(x - startX, width)
                            }
                            if (!change.pressed) {
                                val velocity = tracker.calculateVelocity()
                                if (isSheet && driver.draggingSheet) {
                                    driver.finishSheetDrag(velocity.y.toDouble() / density.density, sheetRange)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                } else if (isMap && driver.scrubbingMap) {
                                    driver.finishMapScrub()
                                }
                                ended = true
                                change.consume()
                                break
                            }
                            change.consume()
                        }
                    } finally {
                        if (!ended) {
                            if (isSheet && driver.draggingSheet) driver.finishSheetDrag(0.0, sheetRange)
                            if (isMap && driver.scrubbingMap) driver.finishMapScrub()
                        }
                    }
                }
            },
    ) {
        if (drawRevision >= 0L) {
            drawIntoCanvas {
                art.drawScene(
                    canvas = it.nativeCanvas,
                    widthPx = size.width,
                    heightPx = size.height,
                    density = density.density,
                    statusInsetPx = statusInsetPx,
                    bottomInsetPx = bottomInsetPx,
                    frame = frame,
                    reducedMotion = reducedMotion,
                )
            }
        }
    }
}
