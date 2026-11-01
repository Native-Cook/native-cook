package com.ngocdevv.nativecook.blocks.pickers.islandsleepdial

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable
fun IslandSleepDial(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val art = remember(context) { IslandSleepDialArt(context) }
    val driver = remember {
        IslandSleepDialDriver { success ->
            val feedback = if (!success) HapticFeedbackConstants.CLOCK_TICK
                else if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY
            view.performHapticFeedback(feedback)
        }
    }
    val frame = remember { mutableStateOf(IslandSleepDialMotion.Frame()) }
    val pressed = remember { mutableStateOf(false) }
    val insets = WindowInsets.systemBars.asPaddingValues()
    val top = insets.calculateTopPadding().value.toDouble()
    val bottom = insets.calculateBottomPadding().value.toDouble()
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val old = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (old != null) controller.isAppearanceLightStatusBars = old }
    }
    LaunchedEffect(driver, autoPlay, reduced, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { nanos ->
                val wall = (if (previous == 0L) 0.016 else (nanos - previous) / 1e9).coerceIn(0.0, 0.5)
                previous = nanos
                frame.value = driver.step(wall, autoPlay && !reduced)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val cx = w / 2
        val cy = round(h * .44)
        val r = round(w * .4)
        Canvas(Modifier.fillMaxSize().pointerInput(driver, w, h) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                val x = start.x / density
                val y = start.y / density
                val onButton = x >= 20 && x <= w - 20 && y >= h - bottom - 74 && y <= h - bottom - 18
                val onDial = y >= cy - r - 40 && y <= cy + r + 40
                var dragging = false
                var end = start
                if (onButton) pressed.value = true
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        end = change.position
                        if (onDial && change.pressed) {
                            if (!dragging && (end - start).getDistance() / density >= 2) {
                                dragging = true
                                driver.start(end.x / density.toDouble(), end.y / density.toDouble(), cx, cy)
                            } else if (dragging) driver.move(end.x / density.toDouble(), end.y / density.toDouble(), cx, cy)
                        }
                        if (onDial || onButton) change.consume()
                    } while (change.pressed)
                } finally {
                    if (dragging) driver.end()
                    if (onButton && (end - start).getDistance() / density < 12) driver.setAlarm()
                    pressed.value = false
                }
            }
        }) {
            // Read animation state here so only drawing invalidates on each frame.
            val f = frame.value
            val isPressed = pressed.value
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                val save = c.save(); c.scale(density, density)
                art.draw(c, size.width / density.toDouble(), size.height / density.toDouble(), top, bottom, f, reduced, isPressed)
                c.restoreToCount(save)
            }
        }
        Box(Modifier.offset(24.dp, (top + 12).dp).size((w - 48).dp, 87.dp).semantics {
            contentDescription = "Sleep length"
            stateDescription = IslandSleepDialMotion.duration(frame.value.h)
        })
        Box(Modifier.offset(y = (cy - r - 40).dp).size(w.dp, (r * 2 + 80).dp).semantics {
            contentDescription = "Sleep length dial. Drag the moon around the island"
            stateDescription = IslandSleepDialMotion.duration(frame.value.h)
            progressBarRangeInfo = ProgressBarRangeInfo(frame.value.h.toFloat(), 4f..11f, 27)
            setProgress { target -> driver.adjust(round(target * 4) / 4.0 - driver.h); true }
        })
        Box(Modifier.offset(20.dp, (h - bottom - 74).dp).size((w - 40).dp, 56.dp).semantics {
            role = Role.Button
            contentDescription = if (frame.value.cloud > .5) "Alarm set" else "Set alarm"
            onClick { driver.setAlarm(); true }
        })
    }
}
