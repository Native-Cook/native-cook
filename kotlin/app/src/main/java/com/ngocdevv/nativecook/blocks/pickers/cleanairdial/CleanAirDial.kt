package com.ngocdevv.nativecook.blocks.pickers.cleanairdial

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialTheme as Theme
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion as Motion

/** The 9-second RN reference, rendered at width / 402 without altering pin geometry. */
@Composable
fun CleanAirDial(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view) {
        val activity = generateSequence(view.context) {
            (it as? ContextWrapper)?.baseContext
        }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, view)
        }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pin = if (debug) previewTime?.toDouble() else null
    val auto = autoPlay && !reduced
    val art = remember(context) {
        CleanAirDialArt(context)
    }
    val feedback = remember(context, reduced) {
        CleanAirDialFeedback(context, !reduced)
    }
    DisposableEffect(feedback) {
        onDispose {
            feedback.close()
        }
    }
    val driver = remember(auto, reduced, pin) {
        CleanAirDialDriver(pin ?: 0.0)
    }
    val clock = remember(driver) {
        doubleArrayOf(0.0)
    }
    val frame = remember(driver) {
        mutableStateOf(Motion.frameAt(pin ?: 0.0))
    }
    LaunchedEffect(driver, lifecycle, feedback) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            var quality = 1
            while (true) withFrameNanos { nanos ->
                try {
                    val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    clock[0] += dt
                    val now = clock[0]
                    if (pin != null) {
                        driver.mode = 0
                        driver.t = pin
                        driver.amb = pin
                    } else {
                        driver.step(dt, now, auto)
                        if (reduced && driver.mode == 2 && driver.dragging == 0) {
                            driver.g = if (driver.power) Theme.ticks[driver.level] else 0.0
                            driver.gv = 0.0
                            driver.f = if (driver.power) Theme.target[driver.level] else -0.5
                        }
                    }
                    var next = driver.frame(now)
                    if (reduced) next = next.copy(amb = 0.0, phi = 0.0)
                    val pm = Motion.pmOf(next.f)
                    val q = if (pm < 6.5) 0 else if (pm < 15.5) 1 else 2
                    if (q != quality) {
                        if (q == 0 && driver.mode == 2) {
                            feedback.play("chime")
                            feedback.buzz(3)
                        }
                        quality = q
                    }
                    // Only the drawing phase observes this value, avoiding per-frame recomposition.
                    frame.value = next
                } catch (error: Exception) {
                    if (debug) android.util.Log.e("CleanAirDial", "Frame callback failed", error)
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        role = Role.Button
        contentDescription = "Air purifier. PM 2.5: 12, moderate. Speed: medium."
        onClick(label = "Change the fan speed") {
            driver.takeOver(clock[0])
            driver.level = (driver.level + 1) % 4
            true
        }
    }.pointerInput(driver, pin, feedback) {
        if (pin != null) return@pointerInput
        fun toPin(point: Offset): Pair<Double, Double> {
            val scale = size.width / Theme.REF_W
            return Pair(point.x / scale / Theme.S + Theme.X0, (point.y / scale - Theme.Y0) / Theme.S)
        }
        fun ringTick(p: Pair<Double, Double>): Double {
            var a = atan2(p.first - Theme.UI.dialX, -(p.second - Theme.UI.dialY)) * 180 / PI
            if (a < 0) a += 360
            return (a / 6 + 0.5).coerceIn(1.0, 36.0)
        }
        fun buttonAt(p: Pair<Double, Double>): Int {
            for (i in 0..2) {
                if (abs(p.first - Theme.UI.chipX[i]) < Theme.UI.chipW/2+6 && abs(p.second - Theme.UI.chipY) < Theme.UI.chipH/2+6) return i
                if (abs(p.first - Theme.UI.chipX[i]) < Theme.UI.chipW/2+6 && abs(p.second - Theme.UI.bottomY) < Theme.UI.chipH/2+6) return 3+i
            }
            return -1
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            driver.takeOver(clock[0])
            val start = toPin(down.position)
            val dist = hypot(start.first - Theme.UI.dialX, start.second - Theme.UI.dialY)
            val ring = dist > Theme.UI.disk - 30 && dist < Theme.UI.r1 + 60 && driver.power
            var lastTick = 0
            if (ring) {
                driver.dragging = 1
                driver.dragG = ringTick(start)
                lastTick = floor(driver.dragG+0.5).toInt()
                feedback.buzz(2)
                feedback.play("tick")
            } else {
                val button = buttonAt(start)
                if (button >= 0) {
                    driver.press = button
                    driver.pressLast = button
                    driver.pressAt = clock[0]
                }
            }
            down.consume()
            var end = start
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull {
                        it.id == down.id
                    }
                    ?: break
                    end = toPin(change.position)
                    if (change.pressed && ring) {
                        driver.last = clock[0]
                        driver.dragG = ringTick(end)
                        val tick = floor(driver.dragG+0.5).toInt()
                        if (tick != lastTick) {
                            lastTick = tick
                            feedback.buzz(2)
                            feedback.play("tick")
                        }
                    }
                    change.consume()
                }
                while (change.pressed)
            } finally {
                val now = clock[0]
                driver.last = now
                if (ring) {
                    val before = driver.level
                    driver.level = Motion.levelAt(driver.dragG)
                    driver.g = driver.dragG
                    driver.dragging = 0
                    driver.auto = false
                    if (driver.level > before) feedback.play("whoosh")
                    feedback.buzz(0)
                } else {
                    val button = driver.press
                    if (button >= 0) {
                        driver.press = -1
                        driver.releaseAt = now
                    }
                    if (button >= 0 && hypot(end.first-start.first, end.second-start.second) <= 24 && buttonAt(end) == button) {
                        feedback.play("tap")
                        when (button) {
                            0 -> if (driver.power) {
                                val arrows = end.first > Theme.UI.chipX[0] + 14
                                val up = !arrows || end.second < Theme.UI.chipY
                                val next = if (up) {
                                    if (arrows) min(3, driver.level+1) else (driver.level+1)%4
                                } else max(0, driver.level-1)
                                if (next > driver.level) feedback.play("whoosh")
                                driver.level = next
                                driver.auto = false
                                driver.autoAt = now-1
                                feedback.buzz(2)
                            }
                            1 -> {
                                driver.timer = !driver.timer
                                driver.timerAt = now
                                feedback.buzz(0)
                            }
                            2 -> {
                                driver.scenes = !driver.scenes
                                driver.scenesAt = now
                                feedback.buzz(0)
                            }
                            3 -> {
                                driver.auto = !driver.auto
                                driver.autoAt = now
                                feedback.buzz(0)
                            }
                            4 -> {
                                driver.power = !driver.power
                                driver.powerAt = now
                                feedback.buzz(1)
                                if (driver.power) feedback.play("whoosh")
                            }
                            else -> feedback.buzz(0)
                        }
                    }
                }
            }
        }
    }) {
        drawIntoCanvas {
            art.drawScene(it.nativeCanvas, size.width, frame.value)
        }
    }
}
