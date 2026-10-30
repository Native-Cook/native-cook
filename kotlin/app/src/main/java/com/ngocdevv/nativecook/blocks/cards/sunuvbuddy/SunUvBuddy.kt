package com.ngocdevv.nativecook.blocks.cards.sunuvbuddy

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.Live
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.sizeFor
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.uvAt
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyTheme.UI

/** Day chips, hour detents and jelly poke interrupt the same nine-second loop. */
@Composable
fun SunUvBuddy(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pin = if (debug) previewTime?.toDouble() else null
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val auto = autoPlay && !reduced
    val resources = remember(context) { SunUvBuddyArt(context) }
    val audio = remember(context, reduced) { if (reduced) null else SunUvBuddyAudio(context) }
    DisposableEffect(audio) { onDispose { audio?.release() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    val driver = remember(auto, reduced, pin) { SunUvBuddyDriver(pin ?: 0.0) }
    val clock = remember(driver) { doubleArrayOf(0.0) }
    val chipAt = remember(driver) { doubleArrayOf(-10.0) }
    val shadesOn = remember(driver) { intArrayOf(0) }
    val frame = remember(driver) { mutableStateOf(Live()) }
    fun buzz(kind: Int) {
        val feedback = when (kind) {
            2 -> HapticFeedbackConstants.CLOCK_TICK
            1 -> HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(feedback)
    }
    LaunchedEffect(driver, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { nanos ->
                val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                previous = nanos
                clock[0] += dt
                val now = clock[0]
                val base = if (pin != null) {
                    driver.mode = 0; driver.t = pin; driver.amb = pin
                    driver.step(0.0, now, false)
                } else driver.step(dt, now, auto)
                val tau = base - driver.at
                if (pin != null || reduced) {
                    driver.size = sizeFor(uvAt(driver.hour, driver.from, driver.to, if (reduced) 10.0 else tau))
                    driver.sizeV = 0.0
                }
                val strong = if (uvAt(driver.hour, driver.from, driver.to, tau) > 4.2) 1 else 0
                if (strong != shadesOn[0]) {
                    shadesOn[0] = strong
                    if (strong == 1 && driver.mode == 2) audio?.play("shades")
                }
                // Read only by the draw lambda: no whole-screen composition per frame.
                frame.value = Live(
                    amb = if (reduced) 0.0 else driver.amb,
                    hour = driver.hour, hourV = if (reduced) 0.0 else driver.hourV,
                    from = driver.from, to = driver.to, tau = if (reduced) 10.0 else tau,
                    size = driver.size,
                    squish = if (driver.squishAt < 0 || reduced) -1.0 else now - driver.squishAt,
                    dayPress = max(0.0, 1 - (now - chipAt[0]) / 0.25),
                )
            }
        }
    }
    Canvas(Modifier.fillMaxSize()
        .semantics {
            role = Role.Button
            contentDescription = "UV index, Thursday. From 12:00 it will be moderate, 3. Sunscreen and a cap won't hurt."
            onClick(label = "Double tap for the next day") {
                driver.takeOver(clock[0]); driver.pickDay((driver.to + 1) % SunUvBuddyTheme.days.size, clock[0]); true
            }
        }
        .pointerInput(driver, reduced, pin, audio) {
            if (pin != null) return@pointerInput
            fun hourAt(x: Double) = ((x - UI.barsX - UI.barsW / 2) / (UI.barsW + UI.barsGap)).coerceIn(0.0, 10.0)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val scale = size.width / SunUvBuddyTheme.REF_W
                val startX = down.position.x / scale
                val startY = down.position.y / scale
                var endX = startX
                var lastT = clock[0]
                var lastHour = 0
                driver.takeOver(clock[0])
                val zone = when {
                    startY > UI.sheetBottom - 8 && startY < UI.barsLabelY + 20 -> 1
                    abs(startY - UI.chipsY) < 24 -> 2
                    hypot(startX - UI.sunX, startY - UI.sunY) < UI.sunR * 1.15 -> 3
                    else -> 0
                }
                if (zone == 1) {
                    driver.dragging = 1; driver.hourV = 0.0; driver.hour = hourAt(startX)
                    lastHour = floor(driver.hour + 0.5).toInt()
                    audio?.play("tick"); buzz(2)
                }
                down.consume()
                var released = false
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            endX = change.position.x / scale
                            released = !change.pressed
                            if (zone == 1 && change.pressed && change.position != change.previousPosition) {
                                val now = clock[0]; driver.last = now
                                val dtm = max(1.0 / 240, now - lastT)
                                val next = hourAt(endX)
                                driver.hourV = driver.hourV * 0.6 + ((next - driver.hour) / dtm) * 0.4
                                driver.hour = next; lastT = now
                                val step = floor(next + 0.5).toInt()
                                if (step != lastHour) { lastHour = step; audio?.play("tick"); buzz(2) }
                            }
                            change.consume()
                        }
                    } while (!released && event.changes.any { it.pressed })
                } finally {
                    val now = clock[0]; driver.last = now
                    val tap = abs(endX - startX) < 10
                    // RN finalizes on cancellation too, preserving release momentum and detents.
                    if (zone == 1) {
                        driver.dragging = 0
                        driver.hourAim = floor(driver.hour + driver.hourV * 0.08 + 0.5).coerceIn(0.0, 10.0)
                        if (reduced) { driver.hour = driver.hourAim; driver.hourV = 0.0 }
                    } else if (tap && zone == 2) {
                        for (i in 0 until 5) {
                            if (endX > UI.chipsX[i] - 4 && endX < UI.chipsX[i] + UI.chipsW[i] + 4) {
                                if (driver.pickDay(i, now)) { chipAt[0] = now; buzz(0) }
                                break
                            }
                        }
                    } else if (tap && zone == 3 && !reduced) {
                        driver.squishAt = now; driver.sizeV -= 1.6
                        audio?.play("boing"); buzz(1)
                    }
                }
            }
        }
    ) { drawIntoCanvas { resources.drawScene(it.nativeCanvas, size.width, frame.value) } }
}
