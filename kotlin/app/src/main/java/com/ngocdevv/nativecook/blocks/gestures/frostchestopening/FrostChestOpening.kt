package com.ngocdevv.nativecook.blocks.gestures.frostchestopening

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.WindowCompat
import kotlin.math.min
import com.ngocdevv.nativecook.blocks.gestures.frostchestopening.FrostChestOpeningMotion.T

/** RN's five-state touch driver. All clocks are seconds and use Double, like JavaScript. */
internal class FrostChestOpeningDriver {
    var mode = 0
    var t = 0.0
    var stop = 9.6
    var last = -100.0
    fun stepDriver(dt: Double, now: Double, auto: Boolean): Double {
        val step = min(dt, 0.05)
        when (mode) {
            0 -> t = if (auto) (t + step) % 9.6 else 0.0
            1 -> {
                t = maxOf(t, T.press) + step
                if (t >= T.fillEnd + 0.08) { mode = 3; stop = T.settled }
            }
            2 -> {
                t -= step * FrostChestOpeningMotion.DRAIN
                if (t <= T.press - 0.01) { t = 0.0; mode = 4; last = now }
            }
            3 -> {
                val next = t + step
                if (stop >= 9.6 && next >= 9.6) { t = 0.0; mode = 4; last = now }
                else if (stop < 9.6 && next >= stop) { t = stop; mode = 4; last = now }
                else t = next
            }
            4 -> if (auto && now - last > FrostChestOpeningMotion.RESUME) mode = 0
        }
        return t
    }
    fun holdBegin(now: Double): Boolean {
        if (t < T.flyStart && (mode == 0 || mode == 2 || mode == 4)) {
            if (t > T.fillEnd) return false
            if (!(mode == 0 && t > T.press) && t < T.press) t = T.press - 0.02
            mode = 1; last = now
            return true
        }
        return false
    }
    fun holdEnd(now: Double) { if (mode == 1) { mode = 2; last = now } }
    fun canClaim() = t >= T.claim + 0.25 && t < T.claimPress[0]
    fun claimTap(now: Double): Boolean {
        if (!canClaim()) return false
        t = T.claimPress[0]; mode = 3; stop = 9.6; last = now
        return true
    }
    fun touched(now: Double) {
        last = now
        if (mode == 0) {
            if (t >= T.flyStart && t < T.settled) { mode = 3; stop = T.settled }
            else if (t >= T.settled && t < T.claimPress[0]) mode = 4
        }
    }
}

/** previewTime only pins debug builds; the demo passes null. The loop is exactly 9.6 seconds. */
@Composable
fun FrostChestOpening(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val top = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pin = if (debug) previewTime?.toDouble()?.let { ((it % 9.6) + 9.6) % 9.6 } else null
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val resources = remember(context) { FrostChestOpeningArt(context) }
    val driver = remember(autoPlay, reduced, pin) { FrostChestOpeningDriver() }
    val clock = remember(driver) { doubleArrayOf(pin ?: 0.0) }
    val frame = remember(driver) {
        val initial = pin ?: if (reduced) T.settled else 0.0
        mutableStateOf(FrostChestOpeningMotion.frameAt(initial, initial % 9.6))
    }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    fun buzz(success: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 29 && success) vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(12, 45))
    }
    LaunchedEffect(driver, lifecycle) {
        if (reduced || pin != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.016 else (nanos - previous) / 1e9
                    previous = nanos
                    clock[0] += min(dt, 0.05)
                    val before = driver.t
                    val t = driver.stepDriver(dt, clock[0], autoPlay)
                    if (driver.mode != 0) {
                        if (before < T.press + 0.02 && t >= T.press + 0.02 && driver.mode == 1) buzz()
                        for (q in doubleArrayOf(0.25, 0.5, 0.75)) {
                            val at = T.fillKnee + (T.fillEnd - T.fillKnee) * q
                            if (before < at && t >= at && driver.mode == 1) buzz()
                        }
                        if (before < T.impact && t >= T.impact) buzz(true)
                        if (before < 3.68 && t >= 3.68) buzz()
                    }
                    // State is read only in drawing/layer phases: no per-frame composition.
                    frame.value = FrostChestOpeningMotion.frameAt(t, (if (autoPlay) clock[0] else t) % 9.6)
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()
        .semantics {
            role = Role.Button
            contentDescription = "A big surprise awaits. Rare chest with one rare drop. Hold to open the chest. Then claim the Winter Special Costume and other rewards."
            onClick(label = "Open chest or claim rewards") {
                if (!reduced && pin == null) {
                    if (!driver.claimTap(clock[0])) { driver.t = T.fillEnd; driver.mode = 3; driver.stop = T.settled; driver.last = clock[0] }
                }
                true
            }
        }
        .pointerInput(driver, reduced, pin) {
            if (reduced || pin != null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val sx = size.width / 402f; val sy = size.height / 874f
                val pos = down.position
                driver.touched(clock[0])
                val holding = pos.x >= 92 * sx - 16 * density && pos.x <= 311 * sx + 16 * density && pos.y >= 583.7 * sy - 30 * density && pos.y <= 774.5 * sy + 16 * density && driver.holdBegin(clock[0])
                val claiming = pos.x >= 13 * sx && pos.x <= 389 * sx && pos.y >= 759 * sy - 6 * density && pos.y <= 759 * sy + 69 * sy + 6 * density && driver.canClaim()
                down.consume()
                var end = pos
                var released = false
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) { end = change.position; released = !change.pressed; change.consume() }
                    } while (!released && event.changes.any { it.pressed })
                } finally {
                    if (holding) driver.holdEnd(clock[0])
                    if (released && claiming && (end - pos).getDistance() < 12 * density && driver.claimTap(clock[0])) buzz()
                    driver.last = clock[0]
                }
            }
        }
        .graphicsLayer {
            val f = frame.value
            translationX = (f.shakeX * size.width / 402).toFloat()
            translationY = (f.shakeY * size.width / 402).toFloat()
            scaleX = f.punch.toFloat(); scaleY = f.punch.toFloat()
            transformOrigin = TransformOrigin(200f / 402f, 430f / 874f)
            clip = false
        }
    ) {
        drawIntoCanvas { resources.drawScene(it.nativeCanvas, size.width, size.height, top, frame.value) }
    }
}
