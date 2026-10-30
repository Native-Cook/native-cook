package com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.Event
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.FLY
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.MAX_SCROLL
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.frameAt
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.panelK
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.replay
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveTheme.UI
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveTheme.cats

/** Same event-driven picker, scroll momentum and two-second homing as the RN reference. */
@Composable
fun CashbackPickFive(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pin = if (debug) previewTime?.toDouble() else null
    val auto = autoPlay && !reduced
    val art = remember(context) {
        CashbackPickFiveArt(context)
    }
    val audio = remember(context, reduced) {
        if (reduced) null else CashbackPickFiveAudio(context)
    }
    DisposableEffect(audio) {
        onDispose {
            audio?.release()
        }
    }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) {
            (it as? ContextWrapper)?.baseContext
        }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, view)
        }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
    val driver = remember(auto, reduced, pin) {
        CashbackPickFiveDriver(pin ?: 0.0)
    }
    val clock = remember(driver) {
        doubleArrayOf(0.0)
    }
    val frame = remember(driver) {
        mutableStateOf(frameAt(pin ?: 0.0))
    }
    val vibrator = remember(context) {
        context.getSystemService(Vibrator::class.java)
    }
    val scope = rememberCoroutineScope()

    fun buzz(kind: Int) {
        val timings = when (kind) {
            3 -> longArrayOf(0, 15, 55, 15)
            else -> longArrayOf(0, if (kind == 2) 8 else 12)
        }
        val amplitudes = when (kind) {
            3 -> intArrayOf(0, 70, 0, 70)
            else -> intArrayOf(0, if (kind == 1) 90 else 45)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    fun cue(name: String) {
        audio?.play(name)
    }

    fun tap(x: Double, y: Double) {
        val now = clock[0]
        val f = driver.frame(now)
        val k = panelK(f)
        val floor = if (k > 0.5) UI.panelY else UI.disabledY - 20
        if (y >= floor || y < 222) {
            return
        }
        val py = y + driver.scroll
        if (py > UI.bannerY && py < UI.bannerY + UI.bannerH && x > UI.bannerX && x < UI.bannerX + UI.bannerW) {
            driver.sparkleAt = now
            cue("sparkle")
            buzz(2)
            return
        }
        for (i in cats.indices) {
            val cy = UI.listTop + i * UI.pitch
            if (abs(py - cy) < UI.pitch / 2 && x > UI.listX && x < UI.listX + UI.listW) {
                val s = replay(driver.events, now)
                val picked = s.slotOf[i] >= 0
                val at = if (reduced) now - FLY - 0.4 else now
                driver.push(Event(at, if (picked) 1 else 0, i))
                when {
                    picked -> {
                        cue("back")
                        buzz(2)
                    }
                    s.picks.size >= 5 -> {
                        cue("nope")
                        buzz(3)
                    }
                    else -> {
                        cue("pop")
                        scope.launch {
                            delay((FLY * 1000).toLong())
                            cue("drop")
                            buzz(0)
                        }
                    }
                }
                return
            }
        }
    }
    // Draw phase observes frame state. No per-frame whole-screen composition.
    LaunchedEffect(driver, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos {
                nanos ->
                val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                previous = nanos
                clock[0] += dt
                if (pin == null) {
                    driver.step(dt, clock[0], auto)
                }
                else {
                    driver.mode = 0
                    driver.t = pin
                    driver.amb = pin
                }
                frame.value = driver.frame(clock[0])
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
            role = Role.Button
            contentDescription = "Choose 5 of 9 cashback categories for June. 2% all purchases, 10% cafés, bars and restaurants, 3% supermarkets, 10% clothes and shoes, 10% taxi. Picks the next category."
            onClick(label = "Pick the next category") {
                driver.takeOver(clock[0])
                val s = replay(driver.events, clock[0])
                val next = cats.indices.firstOrNull {
                    s.slotOf[it] < 0
                }
                if (next != null) {
                    driver.push(Event(clock[0], 0, next))
                }
                true
            }
        }.pointerInput(driver, reduced, pin) {
            if (pin != null) {
                return@pointerInput
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val now = clock[0]
                driver.takeOver(now)
                driver.v = 0.0
                if (!driver.scroll.isFinite()) {
                    driver.scroll = 0.0
                }
                val startScroll = driver.scroll
                val startY = down.position.y
                val scale = size.width / 736.0
                val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, down.position)
                down.consume()
                var end = down.position
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull {
                            it.id == down.id
                        } ?: break
                        end = change.position
                        velocity.addPosition(change.uptimeMillis, end)
                        if (change.pressed) {
                            driver.last = clock[0]
                            val dy = (end.y - startY) / scale
                            if (driver.dragging == 0 && abs(dy) > 10) {
                                driver.dragging = 1
                            }
                            val next = startScroll - dy
                            if (driver.dragging == 1 && next.isFinite()) {
                                driver.scroll = next.coerceIn(0.0, MAX_SCROLL)
                            }
                        }
                        change.consume()
                        if (!change.pressed) {
                            break
                        }
                    }
                    while (event.changes.any {
                            it.pressed
                        })
                } finally {
                    driver.last = clock[0]
                    if (driver.dragging == 1) {
                        driver.dragging = 0
                        val v = -velocity.calculateVelocity().y / scale
                        driver.v = if (v.isFinite()) v else 0.0
                    } else {
                        tap(end.x / scale, end.y / scale)
                    }
                }
            }
        }) {
        drawIntoCanvas {
            art.drawScene(it.nativeCanvas, size.width, size.height, frame.value)
        }
    }
}
