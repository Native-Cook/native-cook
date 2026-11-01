package com.ngocdevv.nativecook.blocks.onboarding.droneunfoldconnect

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val DucOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val DucInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private val DucDefault = Easing { x -> if (x < 0.5f) 2 * x * x else 1 - (-2 * x + 2) * (-2 * x + 2) / 2 }

private class DucTween(var value: Float = 0f) {
    var from = value; var to = value; var start = 0f; var duration = 0f
    var curve = DucDefault
    fun set(v: Float) { value = v; from = v; to = v; duration = 0f }
    fun animate(v: Float, now: Float, seconds: Float, easing: Easing = DucDefault, delay: Float = 0f) {
        from = value; to = v; start = now + delay; duration = seconds; curve = easing
    }
    fun update(now: Float) {
        if (duration > 0 && now >= start) value = from + (to - from) * curve.transform(((now - start) / duration).coerceIn(0f, 1f))
    }
    fun active(now: Float) = duration > 0 && now < start + duration
}

/** A draw-only clock; Compose observes only stage changes and the Canvas invalidation counter. */
internal class DroneUnfoldConnectState(val reduced: Boolean, val auto: Boolean, val buzz: (Boolean) -> Unit) {
    var frame = DroneUnfoldConnectMotion.frameAt(0f)
    val stage = mutableIntStateOf(0)
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
    var clock = 0f; var autoStart = 0f; var modeAuto = auto
    var lastTouch = -100f
    private val unfold = DucTween(); private val spin = DucTween(); private val lift = DucTween()
    private val light = DucTween(); private val connected = DucTween(); private val finger = DucTween(); private val press = DucTween()
    private val channels = listOf(unfold, spin, lift, light, connected, finger, press)
    private var phaseEnd = -1f; private var lightBuzzAt = -1f
    private var completionBuzz = true
    private var pitch = 0f; private var roll = 0f; private var drift = 0f
    private var vp = 0f; private var vr = 0f; private var vd = 0f
    private var sx = 0f; private var sy = 0f
    private var stickDown = false; private var returnAt = 0f; private var returnDuration = 0.35f; private var returnRatio = 0.9f
    private var returnX = 0f; private var returnY = 0f
    fun status() = when (stage.intValue) { 0 -> "Folded · 92%"; 1 -> "Connecting…"; 2 -> "Connected · flying"; else -> "Landing…" }
    fun takeOver() {
        lastTouch = clock; wake.intValue++
        if (!modeAuto) return
        modeAuto = false
        unfold.set(frame.unfold); spin.set(frame.spin); lift.set(frame.lift); light.set(frame.light); connected.set(frame.connected)
        finger.set(frame.finger); finger.animate(0f, clock, 0.15f)
        press.set(frame.pressConnect); press.animate(0f, clock, 0.15f)
        pitch = frame.pitch; roll = frame.roll; drift = frame.drift; vp = 0f; vr = 0f; vd = 0f
        sx = frame.stickX; sy = frame.stickY; releaseStick(0.3f, 1f)
        if (stage.intValue == 1 && frame.connected < 0.5f && frame.unfold > 0.001f) {
            unfold.animate(1f, clock, 0.4f); spin.animate(1f, clock, 0.4f); light.animate(1f, clock, 0.2f)
            lift.animate(0.9f, clock, 0.5f); phaseEnd = clock + 0.5f; completionBuzz = false
        }
        // An autoplay landing in progress is completed before resuming the loop.
        if (stage.intValue == 3) beginLanding()
    }
    fun connect() {
        takeOver(); if (stage.intValue != 0) return
        stage.intValue = 1; completionBuzz = true
        val k = if (reduced) 0.25f else 1f
        unfold.animate(1f, clock, 1.2f * k, DucInOut)
        light.animate(1f, clock, 0.2f, delay = 1.2f * k)
        spin.animate(1f, clock, 0.7f * k, DucOut, 1.2f * k)
        lift.animate(0.9f, clock, 0.9f * k, DucOut, 1.9f * k)
        lightBuzzAt = clock + 1.2f * k; phaseEnd = clock + 2.8f * k
    }
    fun land() { takeOver(); if (stage.intValue == 2) beginLanding() }
    private fun beginLanding() {
        stage.intValue = 3
        val k = if (reduced) 0.25f else 1f
        connected.animate(0f, clock, 0.25f); releaseStick(0.3f, 1f)
        lift.animate(0f, clock, k, DucInOut)
        spin.animate(0f, clock, 0.6f * k, DucOut, k)
        unfold.animate(0f, clock, k, DucInOut, 1.6f * k)
        light.animate(0f, clock, 0.2f, delay = 2.6f * k)
        phaseEnd = clock + 2.6f * k
    }
    fun moveStick(x: Float, y: Float) {
        if (stage.intValue != 2) return
        lastTouch = clock; stickDown = true
        val mag = max(1f, hypot(x, y)); sx = x / mag; sy = y / mag
    }
    fun releaseStick(duration: Float = 0.35f, ratio: Float = 0.9f) {
        lastTouch = clock; stickDown = false; returnAt = clock; returnDuration = duration; returnRatio = ratio
        if (reduced) { sx = 0f; sy = 0f }
        returnX = sx; returnY = sy
    }
    fun step(dt: Float): Boolean {
        clock += dt
        if (modeAuto) {
            val t = (clock - autoStart) % DroneUnfoldConnectMotion.LOOP
            frame = DroneUnfoldConnectMotion.frameAt(t)
            stage.intValue = if (frame.connected > 0.5f) 2 else if (t > 7.4f) 3 else if (frame.unfold > 0.001f) 1 else 0
        } else {
            channels.forEach { it.update(clock) }
            if (lightBuzzAt >= 0 && clock >= lightBuzzAt) { lightBuzzAt = -1f; buzz(false) }
            if (phaseEnd >= 0 && clock >= phaseEnd) {
                phaseEnd = -1f
                if (stage.intValue == 1) { stage.intValue = 2; connected.animate(1f, clock, 0.3f); if (completionBuzz) buzz(true) }
                else if (stage.intValue == 3) stage.intValue = 0
            }
            if (!stickDown) {
                val elapsed = (clock - returnAt).coerceAtLeast(0f)
                val factor = DroneUnfoldConnectMotion.stickSpring(elapsed, returnRatio >= 1f)
                sx = returnX * factor; sy = returnY * factor
            }
            val k = if (reduced) 0.5f else 1f
            vp += ((-sy * 0.35f * k - pitch) * 60 - vp * 11) * dt; pitch += vp * dt
            vr += ((-sx * 0.35f * k - roll) * 60 - vr * 11) * dt; roll += vr * dt
            vd += ((sx * 0.35f - drift) * 60 - vd * 11) * dt; drift += vd * dt
            val bob = if (reduced) 0f else 0.05f * sin(clock * 1.9f) * lift.value / 0.9f
            frame = DroneUnfoldConnectMotion.Frame(unfold.value, spin.value, lift.value + bob, pitch, roll, drift, light.value, connected.value, sx, sy, press.value, finger.value, frame.fingerAt)
            if (stage.intValue == 1 || stage.intValue == 3 || abs(sx) + abs(sy) > 0.01f) lastTouch = clock
            if (auto && clock - lastTouch >= 2) {
                if (stage.intValue == 2) beginLanding()
                else if (stage.intValue == 0 && unfold.value < 0.001f) { autoStart = clock; modeAuto = true }
            }
        }
        tick.intValue++
        return auto || stage.intValue != 0 || channels.any { it.active(clock) } || abs(pitch) + abs(roll) + abs(drift) > 0.0001f
    }
}

@Composable
fun DroneUnfoldConnect(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val d = density.density
    val top = WindowInsets.safeDrawing.getTop(density) / d
    val bottom = WindowInsets.safeDrawing.getBottom(density) / d
    val state = remember(reduced, autoPlay) { DroneUnfoldConnectState(reduced, autoPlay && !reduced) { done ->
        view.performHapticFeedback(if (done) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
    } }
    val art = remember { DroneUnfoldConnectArt(context.assets) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(DRONE_UNFOLD_CONNECT_AGSL) }.getOrNull() else null
    }
    val shaderPaint = remember(shader) { Paint().also { if (Build.VERSION.SDK_INT >= 33) it.shader = shader } }
    LaunchedEffect(state) {
        var last = 0L
        while (true) {
            val keep = withFrameNanos { n ->
                val delta = if (last == 0L) 0f else min(0.05f, (n - last) / 1e9f)
                last = n
                if (view.isShown && view.hasWindowFocus()) state.step(delta) else true
            }
            if (!keep) {
                val seen = state.wake.intValue
                snapshotFlow { state.wake.intValue }.first { it != seen }
                last = 0L
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value; val h = maxHeight.value
        val ppu = (w * 0.3f).roundToInt().toFloat()
        val floorY = top + ((h - top - bottom) * 0.52f).roundToInt()
        val cardTop = floorY + 86
        val bottomTop = h - max(bottom, 16f) - 8 - 120
        Canvas(Modifier.fillMaxSize().pointerInput(state, w, h, top, bottomTop) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val point = down.position / d
                state.takeOver()
                val stick = state.stage.intValue == 2 && hypot(point.x - w / 2, point.y - bottomTop - 60) <= 60
                var moved = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = (change.position - down.position) / d
                    if (hypot(delta.x, delta.y) > 8) moved = true
                    if (stick) { state.moveStick(delta.x / 44, delta.y / 44); change.consume() }
                    if (!change.pressed) {
                        state.lastTouch = state.clock
                        if (stick) state.releaseStick()
                        else if (!moved) {
                            if (point.y in bottomTop + 32..bottomTop + 88 && point.x in 20f..w - 20) state.connect()
                            else if (point.x >= w - 68 && point.y in top + 8..top + 52 && state.stage.intValue == 2) state.land()
                        }
                        break
                    }
                } while (true)
                if (stick) state.releaseStick()
            }
        }) {
            state.tick.intValue
            drawIntoCanvas { canvas ->
                val cv = canvas.nativeCanvas
                cv.drawColor(DroneUnfoldConnectTheme.page)
                cv.save(); cv.scale(d, d)
                val f = state.frame
                if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                    shader.setFloatUniform("uView", w, h, ppu, d)
                    shader.setFloatUniform("uDrone", f.drift, f.lift, f.pitch, f.roll)
                    shader.setFloatUniform("uState", f.unfold, f.spin, 0.5f + 0.25f * f.roll, state.clock * 0.7f)
                    shader.setFloatUniform("uFloor", floorY, 0.38f, f.light, 0f)
                    cv.drawRect(0f, 0f, w, h, shaderPaint)
                } else art.fallback(cv, w, floorY, ppu, state)
                art.ui(cv, w, top, cardTop, bottomTop, state)
                cv.restore()
            }
        }
        Box(Modifier.offset((w / 2 - 72).dp, (top + 8).dp).size(120.dp, 44.dp).semantics {
            contentDescription = "Add drone"; heading()
        })
        Box(Modifier.offset(0.dp, cardTop.dp).size(w.dp, 60.dp).semantics(mergeDescendants = true) {
            contentDescription = "Skyline Mini, ${state.status()}"; liveRegion = LiveRegionMode.Polite
        })
        if (state.stage.intValue == 0) Box(Modifier.offset(20.dp, (bottomTop + 32).dp).size((w - 40).dp, 56.dp).semantics {
            contentDescription = "Connect"; role = Role.Button; onClick { state.connect(); true }
        })
        if (state.stage.intValue == 2) {
            Box(Modifier.offset((w - 68).dp, (top + 8).dp).size(60.dp, 44.dp).semantics {
                contentDescription = "Land"; role = Role.Button; onClick { state.land(); true }
            })
            Box(Modifier.offset((w / 2 - 60).dp, bottomTop.dp).size(120.dp).semantics {
                contentDescription = "Flight stick"; stateDescription = "Connected · flying"
                customActions = listOf(
                    CustomAccessibilityAction("Fly right") { state.takeOver(); state.moveStick(1f, 0f); true },
                    CustomAccessibilityAction("Fly left") { state.takeOver(); state.moveStick(-1f, 0f); true },
                    CustomAccessibilityAction("Fly forward") { state.takeOver(); state.moveStick(0f, -1f); true },
                    CustomAccessibilityAction("Center stick") { state.takeOver(); state.releaseStick(); true },
                )
            })
        }
        Box(Modifier.offset(20.dp, (top + 8).dp).size(12.dp, 44.dp).semantics { contentDescription = "Back"; role = Role.Button })
    }
}
