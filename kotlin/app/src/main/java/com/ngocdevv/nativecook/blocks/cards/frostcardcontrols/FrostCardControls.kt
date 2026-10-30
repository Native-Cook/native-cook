package com.ngocdevv.nativecook.blocks.cards.frostcardcontrols

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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.blocks.cards.frostcardcontrols.FrostCardControlsMotion.Driver
import com.ngocdevv.nativecook.blocks.cards.frostcardcontrols.FrostCardControlsMotion.frameAt
import com.ngocdevv.nativecook.blocks.cards.frostcardcontrols.FrostCardControlsTheme.UI
import kotlin.math.min

/** Event-driven card controls. Per-frame state is read only by drawing. */
@Composable
fun FrostCardControls(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pin = if (debug) previewTime?.toDouble() else null
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val art = remember(context) {
        FrostCardControlsArt(context)
    }
    val driver = remember(autoPlay, reduced, pin) {
        Driver(pin ?: 0.0)
    }
    val clock = remember(driver) {
        doubleArrayOf(0.0)
    }
    val frame = remember(driver) {
        mutableStateOf(frameAt(pin ?: 0.0))
    }
    val live = remember(driver) {
        mutableStateOf(Triple((pin ?: 0.0) % FrostCardControlsMotion.LOOP, 0.0, 0.0))
    }
    val icedAim = remember {
        mutableDoubleStateOf(0.0)
    }
    val iced = remember {
        mutableDoubleStateOf(0.0)
    }
    val audio = remember(context) {
        FrostCardControlsAudio(context)
    }
    DisposableEffect(audio) {
        onDispose {
            audio.release()
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
    LaunchedEffect(icedAim.doubleValue) {
        val from = iced.doubleValue
        var start = 0L
        var done = false
        while (!done) withFrameNanos { nanos ->
            if (start == 0L) start = nanos
            val t = ((nanos - start) / 1e9 / 0.22).coerceIn(0.0, 1.0)
            // Reanimated's default quadratic in-out easing, even when system motion is reduced.
            val ease = if (t < 0.5) 2 * t * t else 1 - 2 * (1 - t) * (1 - t)
            iced.doubleValue = from + (icedAim.doubleValue - from) * ease
            done = t >= 1
        }
    }
    LaunchedEffect(driver, lifecycle) {
        if (reduced || pin != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { nanos ->
                val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                previous = nanos
                clock[0] += dt
                driver.stepDriver(dt, clock[0], autoPlay)
                frame.value = driver.liveFrame(clock[0])
                live.value = Triple(driver.amb, driver.tiltX, driver.tiltY)
            }
        }
    }
    fun accessibilityFreeze() {
        if (reduced) icedAim.doubleValue = if (iced.doubleValue > 0.5) 0.0 else 1.0
        else {
            driver.takeOver(clock[0])
            if (driver.frozen != 0) driver.meltNow(clock[0]) else driver.freezeAt(clock[0], 46.0, 186.0)
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        role = Role.Button
        contentDescription = "Physical card ending 5371. Balance minus 3,116 dollars 58. No spending limit. Freezes the card, or unfreezes it when it is frozen."
        onClick(label = "Freeze or unfreeze card") {
            accessibilityFreeze()
            true
        }
    }.pointerInput(driver, reduced, pin) {
        if (pin != null) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val scale = size.width / 402.0
            val start = down.position
            val x = start.x / scale
            val y = start.y / scale
            var target = 0
            if (x > UI.cardX && x < UI.cardX + UI.cardW && y > UI.cardY && y < UI.cardY + UI.cardH) target = 1
            else if (y > UI.tileY && y < UI.tileY + UI.tileH) for (i in 0..2) if (x > UI.tileXs[i] && x < UI.tileXs[i] + UI.tileW) target = i + 2
            var moved = false
            if (!reduced) {
                driver.takeOver(clock[0])
                if (target >= 2) driver.pressAim[target - 2] = 1.0
            }
            down.consume()
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull {
                        it.id == down.id
                    }
                    if (change != null) {
                        if (!reduced) {
                            driver.last = clock[0]
                            val delta = change.position - start
                            // RN translations use screen points, not scaled reference-card coordinates.
                            if (delta.getDistance() / density > 8) moved = true
                            if (target == 1 && moved) {
                                driver.aimX = (delta.x / density / 120.0).coerceIn(-1.0, 1.0)
                                driver.aimY = (delta.y / density / 120.0).coerceIn(-1.0, 1.0)
                            }
                        }
                        change.consume()
                    }
                } while (event.changes.any {
                    it.id == down.id && it.pressed
                })
            } finally {
                val now = clock[0]
                driver.pressAim = DoubleArray(3)
                driver.aimX = 0.0
                driver.aimY = 0.0
                driver.last = now
                if (reduced) {
                    if (!moved && (target == 1 || target == 2)) icedAim.doubleValue = if (iced.doubleValue > 0.5) 0.0 else 1.0
                } else if (!moved && now >= driver.busy) when (target) {
                    1, 2 -> if (driver.frozen != 0) {
                        driver.meltNow(now)
                        audio.play("melt", 0)
                    } else {
                        driver.freezeAt(now, if (target == 1) x - UI.cardX else 46.0, if (target == 1) y - UI.cardY else 186.0)
                        audio.play("freeze", 1)
                    }
                    3 -> {
                        driver.reissueNow(now)
                        audio.play("reissue", 0)
                    }
                    4 -> {
                        driver.shredNow(now)
                        audio.play("shred", 3)
                    }
                }
            }
        }
    }) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (reduced) {
                art.drawScene(native, size.width, frameAt(0.0), 0.0, 0.0, 0.0)
                val k = iced.doubleValue
                if (k > 0.001) {
                    native.saveLayerAlpha(0f, 0f, size.width, size.height, (k * 255).toInt())
                    art.drawScene(native, size.width, frameAt(3.0), 0.0, 0.0, 0.0)
                    native.restore()
                }
            } else {
                val l = live.value
                art.drawScene(native, size.width, frame.value, l.first, l.second, l.third)
            }
        }
    }
}

