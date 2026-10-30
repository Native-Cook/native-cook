package com.ngocdevv.nativecook.blocks.charts.isometricscoregauge

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeMotion.Frame
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeTheme.Gauge
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeTheme.UI

/** Scales the same 402 × 874 reference canvas, including its own safe-area spacing. */
@Composable
fun IsometricScoreGauge(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pin = if (debug) previewTime?.toDouble() else null
    val resources = remember(context) { IsometricScoreGaugeArt(context) }
    val driver = remember(autoPlay, reduced, pin) { IsometricScoreGaugeDriver(pin ?: 0.0) }
    val clock = remember(driver) { doubleArrayOf(0.0) }
    val frame = remember(driver) { mutableStateOf(if (pin != null) IsometricScoreGaugeMotion.frameAt(pin) else Frame()) }
    val ambient = remember(driver) { mutableDoubleStateOf(pin ?: 0.0) }
    val pick = remember { mutableIntStateOf(-1) }
    val pickFade = remember { Animatable(0f) }
    val audio = remember(context, reduced) { if (reduced) null else IsometricScoreGaugeAudio(context) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    LaunchedEffect(driver, lifecycle, reduced, pin) {
        if (reduced || pin != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    clock[0] += dt
                    driver.stepDriver(dt, clock[0], autoPlay)
                    // Read these values only from the draw lambda, avoiding frame-driven composition.
                    frame.value = driver.frame()
                    ambient.doubleValue = driver.amb
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()
        .semantics {
            role = Role.Button
            contentDescription = "Credit score 765 out of 900. Make a choice, see where it takes you."
            onClick(label = "Previews paying off your credit card") {
                driver.takeOver(clock[0])
                driver.choose(if (driver.sel >= 0) driver.sel else 0)
                true
            }
        }
        .pointerInput(driver, reduced, pin) {
            if (pin != null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val scale = size.width / IsometricScoreGaugeTheme.REF_W
                val dy = (size.height - IsometricScoreGaugeTheme.REF_H * scale) / 2
                val start = down.position
                val startX = (start.x / scale).toDouble()
                val startY = ((start.y - dy) / scale).toDouble()
                val startScroll = driver.scroll
                val inGrid = startY > UI.rows[0] - 20
                var moved = false
                val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, down.position)
                if (!reduced) driver.takeOver(clock[0])
                down.consume()
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val translation = change.position.x - start.x
                        if (!reduced && inGrid) {
                            driver.last = clock[0]
                            // RN's threshold is in screen points; Android pointers are physical pixels.
                            if (abs(translation) / density > 8) moved = true
                            if (moved) driver.dragTo(startScroll, (translation / scale).toDouble())
                        }
                        change.consume()
                        if (!change.pressed) break
                    } while (event.changes.any { it.pressed })
                } finally {
                    driver.last = clock[0]
                    if (reduced) {
                        val index = IsometricScoreGaugeMotion.cardAt(startX, startY, 0.0)
                        if (index >= 0) {
                            val same = pick.intValue == index && pickFade.value > 0.5f
                            if (!same) pick.intValue = index
                            scope.launch(object : MotionDurationScale { override val scaleFactor = 1f }) {
                                // RN withTiming's default quadratic ease-in-out, 200 ms even when
                                // the system animator scale is zero (ReduceMotion.Never).
                                pickFade.animateTo(if (same) 0f else 1f, tween(200, easing = Easing { fraction -> if (fraction < 0.5f) 2 * fraction * fraction else 1 - 2 * (1 - fraction) * (1 - fraction) }))
                            }
                        }
                    } else if (moved) {
                        driver.release((velocity.calculateVelocity().x / scale).toDouble())
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else {
                        val index = IsometricScoreGaugeMotion.cardAt(startX, startY, driver.scroll)
                        if (index >= 0) {
                            val delta = driver.choose(index)
                            audio?.play(if (delta > 0) "up" else "pick")
                            audio?.play("roll")
                            haptics.performHapticFeedback(if (delta != 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            }
        }
    ) {
        val index = pick.intValue
        val selected = if (reduced && index >= 0) Frame(sel = index, selW = 1.0, score = Gauge.score + IsometricScoreGaugeTheme.columns[index / 2][index % 2].delta) else null
        drawIntoCanvas {
            resources.drawScene(it.nativeCanvas, size.width, size.height, if (reduced) Frame() else frame.value,
                if (reduced) 0.0 else ambient.doubleValue, selected, if (reduced) pickFade.value else 0f)
        }
    }
}
