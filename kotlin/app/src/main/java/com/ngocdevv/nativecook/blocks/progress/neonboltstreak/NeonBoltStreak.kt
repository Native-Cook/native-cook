package com.ngocdevv.nativecook.blocks.progress.neonboltstreak

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.hypot

@Composable
fun NeonBoltStreak(modifier: Modifier = Modifier, autoPlay: Boolean = true) {
    val reducedMotion = rememberLiveReducedMotion()
    val context = LocalContext.current
    // Debug-only deterministic frame for matching the RN acceptance captures, including the 9.5 s seam.
    val debugIntent = remember(context) {
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) null
        else generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()?.intent
    }
    val pinnedTime = debugIntent?.getStringExtra("neon-bolt-streak-time")
        ?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    val playsLoop = autoPlay && debugIntent?.getBooleanExtra("neon-bolt-streak-manual", false) != true
    val art = remember { NeonBoltStreakArt() }
    val clock = remember { NeonBoltStreakClock() }
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeInset = maxOf(insets.getTop(density), insets.getBottom(density)).toFloat()
    SideEffect { clock.safeInset = safeInset }
    val view = LocalView.current
    var now by remember { mutableDoubleStateOf(SystemClock.elapsedRealtimeNanos() / 1e9) }
    var wake by remember { mutableIntStateOf(0) }
    clock.onReturn = { wake++ }
    DisposableEffect(clock) { onDispose { clock.dispose() } }

    LaunchedEffect(playsLoop, reducedMotion, pinnedTime, wake) {
        while (pinnedTime == null && !reducedMotion && (playsLoop || clock.touch != null)) {
            androidx.compose.runtime.withFrameNanos { now = it / 1e9 }
        }
    }

    val frame by remember(pinnedTime, reducedMotion, playsLoop, wake) {
        derivedStateOf {
            when {
                pinnedTime != null -> NeonBoltStreakMotion.autoplay(pinnedTime)
                reducedMotion -> NeonBoltStreakMotion.manual(1.4, 6, 1f, celebration = true, flicker = false)
                clock.touch != null -> clock.frame(now)
                playsLoop -> NeonBoltStreakMotion.autoplay(now - clock.loopOrigin)
                else -> NeonBoltStreakMotion.autoplay(0.0)
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF26272B))
            .onSizeChanged { clock.setGeometry(it.width.toFloat(), it.height.toFloat()) }
            .semantics {
                contentDescription = "Neon bolt streak"
                stateDescription = "${frame.count.toInt()} day streak. Tap to strike Thursday; drag across weekdays to scrub days."
                role = Role.Button
                if (!reducedMotion && pinnedTime == null) onClick(label = "Strike Thursday") {
                    clock.accessibilityStrike(SystemClock.elapsedRealtimeNanos() / 1e9, view).also { if (it) wake++ }
                }
            }
            .pointerInput(reducedMotion, pinnedTime) {
                if (reducedMotion || pinnedTime != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = down.position
                    val started = SystemClock.elapsedRealtimeNanos() / 1e9
                    if (!clock.begin(start.x, start.y, started)) return@awaitEachGesture
                    wake++
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val elapsed = SystemClock.elapsedRealtimeNanos() / 1e9 - started
                            clock.update(change.position.x, change.position.y, elapsed)
                            if (change.changedToUp()) {
                                clock.end(elapsed, view)
                                released = true
                                wake++
                                break
                            }
                        }
                    } finally {
                        if (!released) clock.cancelGesture()
                    }
                }
            },
    ) { art.draw(drawContext.canvas.nativeCanvas, size.width, size.height, safeInset, frame) }
}

internal fun neonBoltStreakScale(width: Float, height: Float, safeInset: Float): Float =
    minOf(width / 402f, (height / 2 - safeInset) / 291f).coerceAtLeast(.01f)

private class NeonBoltStreakClock {
    var loopOrigin = SystemClock.elapsedRealtimeNanos() / 1e9
    var touch: NeonBoltStreakTouch? = null
    var onReturn: (() -> Unit)? = null
    private var width = 402f
    private var height = 874f
    var safeInset = 0f

    fun setGeometry(width: Float, height: Float) { this.width = width; this.height = height }

    fun begin(rawX: Float, rawY: Float, now: Double): Boolean {
        val (x, y) = point(rawX, rawY)
        val count = x in 100f..300f && y in 330f..520f
        val panel = y in 560f..728f && x in 16f..386f
        if (!count && !panel) return false
        val day = dayAt(x)
        if (!count && day > NeonBoltStreakMotion.TODAY) return false
        returnRunnable?.let(handler::removeCallbacks)
        touch = NeonBoltStreakTouch(started = now, day = if (count) 6 else day, countGesture = count, startX = x, startY = y, mode = if (!count && day < NeonBoltStreakMotion.TODAY) NeonBoltStreakTouch.Mode.Flickering else NeonBoltStreakTouch.Mode.Pressing, beganCharging = !count && day == NeonBoltStreakMotion.TODAY)
        return true
    }

    fun update(rawX: Float, rawY: Float, elapsed: Double) {
        val active = touch ?: return
        val (x, y) = point(rawX, rawY)
        val moved = hypot(x - active.startX, y - active.startY)
        if (moved > 30f && active.beganCharging) active.cancelled = true
        if ((active.mode == NeonBoltStreakTouch.Mode.Pressing || active.mode == NeonBoltStreakTouch.Mode.Flickering || active.mode == NeonBoltStreakTouch.Mode.Igniting) && !active.countGesture && y in 560f..728f && x in 16f..386f) {
            val entered = dayAt(x)
            if (moved > 30f && entered != active.lastDay) {
                val step = if (entered > active.lastDay) 1 else -1
                val crossed = if (step > 0) (active.lastDay + step)..entered else (active.lastDay + step downTo entered)
                for (day in crossed) {
                    if (day > NeonBoltStreakMotion.TODAY) continue
                    if (day < NeonBoltStreakMotion.TODAY) active.flickers += day to now()
                    else if (!active.cancelled) { active.day = day; active.mode = NeonBoltStreakTouch.Mode.Igniting; active.started = now() }
                }
                active.lastDay = entered
            }
            if (entered == NeonBoltStreakMotion.TODAY && !active.cancelled) active.beganCharging = true
            active.charge = ((elapsed - .18) / .72).toFloat().coerceIn(0f, 1f)
        }
    }

    fun end(elapsed: Double, view: android.view.View) {
        val active = touch ?: return
        if (active.cancelled) { returnHome(); return }
        if (active.mode == NeonBoltStreakTouch.Mode.Pressing) {
            if (active.countGesture && elapsed > .62) {
                active.mode = NeonBoltStreakTouch.Mode.Celebrating; active.started = now(); view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); returnHome(); return
            }
            active.charge = maxOf(active.charge, ((elapsed - .18) / .72).toFloat().coerceIn(0f, 1f))
            active.mode = NeonBoltStreakTouch.Mode.Igniting; active.started = now(); view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); returnHome()
        } else returnHome()
    }

    fun accessibilityStrike(now: Double, view: android.view.View): Boolean {
        if (touch != null) return false
        returnRunnable?.let(handler::removeCallbacks)
        touch = NeonBoltStreakTouch(started = now, day = NeonBoltStreakMotion.TODAY, countGesture = false, startX = 0f, startY = 0f, mode = NeonBoltStreakTouch.Mode.Igniting, beganCharging = true)
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        returnHome()
        return true
    }

    fun cancelGesture() {
        if (touch?.mode == NeonBoltStreakTouch.Mode.Pressing) {
            touch = null
            loopOrigin = now()
            onReturn?.invoke()
        } else if (touch != null) returnHome()
    }

    fun frame(now: Double): NeonBoltStreakFrame {
        val active = touch ?: return NeonBoltStreakMotion.autoplay(now - loopOrigin)
        val elapsed = now - active.started
        return when (active.mode) {
            NeonBoltStreakTouch.Mode.Pressing, NeonBoltStreakTouch.Mode.Igniting -> {
                val charge = if (active.mode == NeonBoltStreakTouch.Mode.Pressing && !active.countGesture) maxOf(active.charge, ((elapsed - .18) / .72).toFloat().coerceIn(0f, 1f)) else active.charge
                val frame = NeonBoltStreakMotion.manual(elapsed, active.day, charge, celebration = false, flicker = false)
                active.flickers.filter { now - it.second < .5 }.forEach { (day, started) -> frame.lit[day] = NeonBoltStreakMotion.flicker(now - started); frame.fill[day] = 1f }
                frame
            }
            NeonBoltStreakTouch.Mode.Flickering -> {
                val frame = NeonBoltStreakMotion.manual(elapsed, active.day, 0f, celebration = false, flicker = true)
                active.flickers.filter { now - it.second < .5 }.forEach { (day, started) -> frame.lit[day] = NeonBoltStreakMotion.flicker(now - started) }
                frame
            }
            NeonBoltStreakTouch.Mode.Celebrating -> NeonBoltStreakMotion.manual(elapsed, active.day, 1f, celebration = true, flicker = false)
        }
    }

    private fun returnHome() {
        returnRunnable?.let(handler::removeCallbacks)
        val task = Runnable {
            touch = null
            loopOrigin = now()
            onReturn?.invoke()
        }
        returnRunnable = task
        handler.postDelayed(task, 2_000)
    }
    private fun dayAt(x: Float) = ((x - 16f) / (370f / 7f)).toInt().coerceIn(0, 6)
    private fun point(rawX: Float, rawY: Float): Pair<Float, Float> {
        val scale = neonBoltStreakScale(width, height, safeInset)
        return (rawX - width / 2) / scale + 201f to (rawY - height / 2) / scale + 437f
    }
    private fun now() = SystemClock.elapsedRealtimeNanos() / 1e9

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var returnRunnable: Runnable? = null
    fun dispose() { returnRunnable?.let(handler::removeCallbacks); onReturn = null }
}
