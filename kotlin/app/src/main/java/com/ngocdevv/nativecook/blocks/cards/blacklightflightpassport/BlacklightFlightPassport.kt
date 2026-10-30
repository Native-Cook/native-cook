package com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportMotion.frameAt
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportMotion.isUv
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportTheme.UI

/** Button and daylight torch share RN's three-mode driver. */
@Composable
fun BlacklightFlightPassport(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pin = if (debug) previewTime?.toDouble() else null
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val art = remember(context) { BlacklightFlightPassportArt(context) }
    val gyro = remember(context) { BlacklightFlightPassportGyro(context) }
    DisposableEffect(gyro) { onDispose { gyro.stop() } }
    val driver = remember(autoPlay, reduced, pin) { BlacklightFlightPassportDriver(pin ?: 0.0) }
    val clock = remember(driver) { doubleArrayOf(0.0) }
    // Read only in drawing, so the frame clock never recomposes the whole screen.
    val pulse = remember(driver) { mutableIntStateOf(0) }
    val reducedFade = remember { mutableDoubleStateOf(0.0) }
    val scope = rememberCoroutineScope()
    val fadeJob = remember { arrayOfNulls<Job>(1) }
    val audio = remember(context, reduced) { if (reduced) null else BlacklightFlightPassportAudio(context) }
    DisposableEffect(audio) { onDispose { audio?.release() } }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val controller = remember(view) {
        generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()?.let { WindowCompat.getInsetsController(it.window, view) }
    }
    DisposableEffect(controller) {
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    fun toggleReduced() {
        fadeJob[0]?.cancel()
        val start = reducedFade.doubleValue
        val end = if (start > 0.5) 0.0 else 1.0
        // RN explicitly uses ReduceMotion.Never for this 220 ms quadratic cross-fade.
        fadeJob[0] = scope.launch {
            val began = withFrameNanos { it }
            var progress: Double
            do {
                progress = withFrameNanos { ((it - began) / 220_000_000.0).coerceIn(0.0, 1.0) }
                val eased = if (progress < 0.5) 2 * progress * progress else 1 - (-2 * progress + 2) * (-2 * progress + 2) / 2
                reducedFade.doubleValue = start + (end - start) * eased
            } while (progress < 1)
        }
    }
    LaunchedEffect(driver, lifecycle) {
        if (reduced || pin != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            gyro.start()
            try {
            var previous = 0L
            var dark = false
            while (true) withFrameNanos { nanos ->
                val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                previous = nanos
                clock[0] += dt
                gyro.step(dt)
                driver.tiltX = gyro.x; driver.tiltY = gyro.y
                driver.step(dt, clock[0], autoPlay)
                val nextDark = frameAt(driver.t).uv > 0.5
                if (nextDark != dark) {
                    dark = nextDark
                    controller?.isAppearanceLightStatusBars = !dark
                }
                pulse.intValue++
            }
            } finally { gyro.stop() }
        }
    }
    LaunchedEffect(pin) {
        if (pin != null && !reduced) controller?.isAppearanceLightStatusBars = frameAt(pin).uv <= 0.5
    }
    Canvas(Modifier.fillMaxSize()
        .semantics {
            role = Role.Button
            contentDescription = "Your all-time passport: 27 flights, 128,714 kilometres, 7 days 1 hour in the air, 21 airports, 14 airlines."
            onClick(label = "Switches the blacklight on or off.") {
                if (reduced) toggleReduced()
                else if (pin == null) { driver.touched(clock[0]); driver.toggleUv() }
                true
            }
        }
        .pointerInput(driver, reduced, pin) {
            if (pin != null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val scale = size.width / 402.0
                val dy = (size.height - 874 * scale) / 2
                val x = down.position.x / scale
                val y = (down.position.y - dy) / scale
                val target = when {
                    x > UI.BUTTON_X - 10 && x < UI.BUTTON_X + UI.BUTTON_W + 10 && y > UI.BUTTON_Y - 10 && y < UI.BUTTON_Y + UI.BUTTON_H + 10 -> 1
                    x > UI.CARD_X - 10 && x < UI.CARD_X + UI.CARD_W + 10 && y > UI.CARD_Y - 10 && y < UI.CARD_Y + UI.CARD_H + 10 -> 2
                    else -> 0
                }
                var moved = false
                if (!reduced) {
                    driver.touched(clock[0])
                    if (target == 1) driver.pressAim = 1.0
                    if (target == 2 && !isUv(driver.t)) {
                        driver.torchAim = 1.0
                        driver.tx = x
                        driver.ty = y - 40
                    }
                }
                down.consume()
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            if (!reduced) {
                                driver.last = clock[0]
                                // Translation threshold is in logical screen points, before ref scaling.
                                if ((change.position - down.position).getDistance() / density > 10) moved = true
                                if (target == 2 && driver.torchAim > 0) {
                                    driver.tx = change.position.x / scale
                                    driver.ty = (change.position.y - dy) / scale - 40
                                }
                            }
                            change.consume()
                        }
                    } while (change?.pressed == true && event.changes.any { it.pressed })
                } finally {
                    driver.pressAim = 0.0
                    driver.torchAim = 0.0
                    driver.last = clock[0]
                    if (target == 1 && !moved) {
                        if (reduced) toggleReduced()
                        else {
                            val on = driver.toggleUv()
                            audio?.play(if (on) "on" else "off")
                            vibrator?.vibrate(VibrationEffect.createOneShot(if (on) 20L else 12L, if (on) 100 else 45))
                        }
                    }
                }
            }
        }
    ) {
        pulse.intValue
        drawIntoCanvas { art.drawScene(it.nativeCanvas, size.width, size.height, frameAt(driver.t), driver, if (reduced) reducedFade.doubleValue else null) }
    }
}
