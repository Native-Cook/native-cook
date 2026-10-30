package com.ngocdevv.nativecook.blocks.navigation.greatwavemorning

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningTheme.UI
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningTheme.S
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningTheme.DY
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.MAX_SCROLL
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.PULL
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.rubber

/** A layered paper sea driven by the same scroll number as the task content. */
@Composable
fun GreatWaveMorning(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val auto = autoPlay && !reduced
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pin = if (debug) previewTime?.toDouble() else null
    val art = remember(context) {
        GreatWaveMorningArt(context)
    }
    val gyro = remember(context) {
        GreatWaveMorningGyro(context)
    }
    val driver = remember(auto, reduced, pin) {
        GreatWaveMorningDriver(pin ?: 0.0)
    }
    val clock = remember(driver) {
        doubleArrayOf(0.0)
    }
    val frame = remember(driver) {
        val initial = GreatWaveMorningMotion.frameAt(pin ?: 0.0)
        mutableStateOf(if (reduced) initial.copy(amb = 0.0, foam = -1.0) else initial)
    }
    val audio = remember(context, reduced) {
        GreatWaveMorningAudio(context, !reduced)
    }
    val vibrator = remember(context) {
        context.getSystemService(Vibrator::class.java)
    }
    DisposableEffect(audio, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) audio.stopAll()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(audio) {
        onDispose {
            audio.close()
        }
    }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) {
            (it as? ContextWrapper)?.baseContext
        }
        .filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, view)
        }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
    DisposableEffect(gyro, lifecycle, reduced, pin) {
        if (!reduced && pin == null) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> gyro.start()
                    Lifecycle.Event.ON_PAUSE -> gyro.stop()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) gyro.start()
            onDispose {
                lifecycle.removeObserver(observer)
                gyro.stop()
            }
        } else {
            onDispose { gyro.stop() }
        }
    }

    fun buzz(kind: Int) {
        if (Build.VERSION.SDK_INT >= 29) {
            val effect = if (kind == 1) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK
            vibrator?.vibrate(VibrationEffect.createPredefined(effect))
        } else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 1) 20 else 12, if (kind == 1) 90 else 45))
    }
    LaunchedEffect(driver, lifecycle) {
        if (pin != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            var baselinePitch = 0.0
            var baselineRoll = 0.0
            var tiltX = 0.0
            var tiltY = 0.0
            var gyroReady = false
            while (true) withFrameNanos {
                nanos ->
                val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                previous = nanos
                clock[0] += dt
                driver.step(dt, clock[0], auto)
                val current = driver.frame(clock[0])
                if (!reduced && pin == null) {
                    val pitch = gyro.pitch
                    val roll = gyro.roll
                    if (!gyroReady) {
                        gyroReady = true
                        baselinePitch = pitch
                        baselineRoll = roll
                    }
                    val baselineK = min(1.0, dt * 0.6)
                    baselinePitch += (pitch - baselinePitch) * baselineK
                    baselineRoll += (roll - baselineRoll) * baselineK
                    val response = 1 - exp(-dt * 8)
                    val targetX = ((roll - baselineRoll) / 0.35).coerceIn(-1.0, 1.0)
                    val targetY = ((pitch - baselinePitch) / 0.35).coerceIn(-1.0, 1.0)
                    tiltX += (targetX - tiltX) * response
                    tiltY += (targetY - tiltY) * response
                }
                // Only the draw phase reads this state; the screen never recomposes per frame.
                frame.value = if (reduced) {
                    current.copy(amb = 0.0, foam = -1.0, tiltX = 0.0, tiltY = 0.0)
                } else {
                    current.copy(tiltX = tiltX, tiltY = tiltY)
                }
            }
        }
    }
    Canvas(
        Modifier.fillMaxSize()
        .semantics {
            role = Role.Button
            contentDescription = "Good morning, Yuna. Sprint 8, 14 tasks due this week. 38 open, 6 due today, 94 done this sprint. Today's focus: Review new component specs with dev, 09:30, due now."
            onClick(label = "Marks the next task done.") {
                driver.takeOver(clock[0])
                val next = driver.done.indexOfFirst {
                    !it
                }
                if (next >= 0) {
                    driver.done[next] = true
                    driver.doneAt[next] = clock[0]
                }
                true
            }
        }
        .pointerInput(driver, reduced, pin) {
            if (pin != null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val scale = size.width / 402.0
                fun pinX(x: Float) = x / scale / S
                fun pinY(y: Float) = (y / scale - DY) / S
                val now = clock[0]
                driver.takeOver(now)
                driver.v = 0.0
                driver.raw = driver.s
                val startRaw = driver.s
                var surged = false
                val x = pinX(down.position.x)
                val y = pinY(down.position.y)
                val cy = y + driver.s
                driver.pressRow = -1
                if (y < UI.tab.y && x > UI.rows.x && x < UI.rows.x + UI.rows.w) {
                    for (i in 0..4) {
                        val rowY = UI.rows.y0 + i * UI.rows.pitch
                        if (cy > rowY && cy < rowY + UI.rows.h && y > (if (driver.s > 0) 170 else 520)) {
                            driver.pressRow = i
                            driver.pressLast = i
                            driver.pressAt = now
                        }
                    }
                }
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var end = down.position
                down.consume()
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull {
                            it.id == down.id
                        } ?: break
                        end = change.position
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (change.pressed) {
                            driver.last = clock[0]
                            val dy = (change.position.y - down.position.y) / scale / S
                            if (!driver.dragging && abs(dy) > 8) {
                                driver.dragging = true
                                if (driver.pressRow >= 0) {
                                    driver.pressRow = -1
                                    driver.releaseRowAt = clock[0]
                                }
                            }
                            if (driver.dragging) {
                                val raw = startRaw - dy
                                driver.raw = raw
                                driver.s = if (raw < 0) -rubber(-raw) else if (raw > MAX_SCROLL) MAX_SCROLL + rubber(raw - MAX_SCROLL) else raw
                                if (driver.s < -60 && !surged) {
                                    surged = true
                                    audio.play("surge")
                                    buzz(0)
                                }
                            }
                        }
                        change.consume()
                    } while (event.changes.any {
                        it.pressed
                    })
                } finally {
                    val release = clock[0]
                    driver.last = release
                    if (driver.dragging) {
                        driver.dragging = false
                        driver.v = (-tracker.calculateVelocity().y / scale / S).coerceIn(-4000.0, 4000.0)
                        if (driver.s < -60 && !reduced) {
                            driver.releaseAt = release
                            driver.foamPow = min(1.2, -driver.s / PULL)
                            audio.play("splash")
                            buzz(1)
                        }
                    } else if (driver.pressRow >= 0) {
                        val i = driver.pressRow
                        driver.pressRow = -1
                        driver.releaseRowAt = release
                        driver.done[i] = !driver.done[i]
                        driver.doneAt[i] = if (reduced) release - 1 else release
                        audio.play("tap")
                        buzz(if (driver.done[i]) 0 else 2)
                    } else if (pinY(end.y) > UI.tab.y) {
                        for (i in 0..4) if (abs(pinX(end.x) - UI.tab.x[i]) < 70 && driver.tab != i) {
                            driver.tab = i
                            if (reduced) driver.tabPos = i.toDouble()
                            audio.play("tap")
                            buzz(2)
                        }
                    }
                }
            }
        }
    ) {
        drawIntoCanvas {
            art.drawScene(it.nativeCanvas, size.width, size.height, density, frame.value)
        }
    }
}
