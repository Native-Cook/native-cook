package com.ngocdevv.nativecook.blocks.progress.rocketlaunchprogress

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Rocket Launch Progress — "Shipwright", a deploy screen where the progress is fuel. A small rocket
 waits on its pad at dusk; the tank beside it fills as Build, Test and Upload tick; at 100 % the
 sky counts 3-2-1, the engine lights (flame, smoke thrown sideways along the pad, the camera
 shaking), the rocket lifts off and arcs away on a contrail, "v4.2.0 is live" — and the next
 rocket rolls onto the pad. Hold the pad to fuel it yourself; full tanks launch.

 autoPlay (LOOP = 10 s, RocketLaunchProgressMotion.kt): rest 0–1.4 · fuelling 1.4–4.4 ·
 countdown 4.4–5.6 · ignition 5.6–6.2 · lift-off 6.2–7.6 · deployed 7.6–8.4 · new rocket 8.4–9.4 ·
 rest. A touch takes over: holding fuels (2.4 s to full; letting go holds the gauge on a whole
 number); a full tank hands the rest of the launch back to the loop. 2 s after letting go of a
 part-full tank it drains (0.9 s) and the loop restarts. Reduced motion: no loop, no shake or
 flight; the gauge fills under the finger and the screen shows the deployed state when full.

 Drawing: one Canvas — the baked scene, the contrail (two blurred paths), the fuel (a clipped
 level with a sloshing top), the rocket (static paths under a moving transform), and one AGSL
 shader for flame, smoke and glow (gradient discs below Android 13). The text reads the same
 values in graphicsLayer lambdas.
*/

private const val MODE_AUTO = 0
private const val MODE_USER = 1
private const val MODE_RETURN = 2
private const val IDLE = 2.0
private const val RETURN = 0.9
/** Holding fills the tank in this long. */
private const val FILL_FOR = 2.4

private const val FEEL_TICK = 1
private const val FEEL_FULL = 2

private class RocketState(private val l: RocketLayout, private val reduced: Boolean) {
    var mode = MODE_AUTO
    var clockT = 0.0
    var now = 0.0
    var lastTouch = 0.0
    var holding = false
    var oneShot = false
    var userFuel = 0.0
    var returnAt = 0.0
    var returnFuel = 0.0
    var feel = 0
    val clock = RocketClock()
    val frame = mutableIntStateOf(0)
    private val baseY = l.groundY - 14
    private val pose = RocketPose()

    // What is drawn.
    var fuel = 0.0
    var rx = l.padX
    var ry = baseY
    var rot = 0f
    var on = true
    var sx = 0f
    var sy = 0f
    var thr = 0f
    var lift = 0f
    var age = 0f
    var fade = 0f
    var trailA = 0f
    val trail = Path()
    val slosh = Path()
    private val left = FloatArray(58)
    private val right = FloatArray(58)

    fun touched() {
        lastTouch = now
        if (mode == MODE_USER) return
        if (mode == MODE_AUTO) {
            // Mid-launch the loop keeps the stage; on the pad the finger takes the tank as it is.
            val t = clockT
            if (t >= ROCKET_COUNT_AT && t < ROCKET_ROLL_AT + 0.6) return
            userFuel = if (t > ROCKET_ROLL_AT) 0.0 else Math.round(rocketAutoFuel(t)).toDouble()
        }
        mode = MODE_USER
    }

    fun press() {
        touched()
        if (mode == MODE_USER) holding = true
    }

    fun release() {
        if (!holding) return
        holding = false
        lastTouch = now
        // The gauge comes to rest on a whole number.
        userFuel = Math.round(userFuel).toDouble()
    }

    /** The contrail: the nozzle's path since lift-off, a ribbon that widens with age. */
    private fun trailPath(t: Double) {
        trail.rewind()
        val k = min(t, ROCKET_ROLL_AT) - ROCKET_LIFT_AT
        if (k <= 0.02) return
        val n = 28
        val p = RocketPose()
        for (j in 0..n) {
            val kk = k * j / n
            rocketPose(ROCKET_LIFT_AT + kk, p)
            val a = k - kk
            val w = ((3 + 26 * a + 30 * max(0.0, t - ROCKET_DONE_AT)) * l.k).toFloat()
            val nx = cos(p.rot).toFloat()
            val ny = sin(p.rot).toFloat()
            val x = l.padX + p.x.toFloat()
            val y = baseY + p.y.toFloat() + 10 * l.k * min(1.0, a * 4).toFloat()
            left[j * 2] = x - nx * w; left[j * 2 + 1] = y - ny * w
            right[j * 2] = x + nx * w; right[j * 2 + 1] = y + ny * w
        }
        trail.moveTo(left[0], left[1])
        for (j in 1..n) trail.lineTo(left[j * 2], left[j * 2 + 1])
        for (j in n downTo 0) trail.lineTo(right[j * 2], right[j * 2 + 1])
        trail.close()
    }

    /** The fuel's surface: a level with a small wave that grows while it moves. */
    private fun sloshPath(level: Double, t: Double, moving: Double) {
        val tank = l.tank
        slosh.rewind()
        val y0 = tank.y + tank.h - (level * (tank.h - 4)).toFloat()
        val amp = 0.8 + 2.2 * moving
        slosh.moveTo(tank.x, tank.y + tank.h)
        for (j in 0..10) {
            val x = tank.x + tank.w * j / 10
            slosh.lineTo(x, (y0 + amp * sin(j / 10.0 * PI * 2 + 2 * PI * 7 * t / LOOP)).toFloat())
        }
        slosh.lineTo(tank.x + tank.w, tank.y + tank.h)
        slosh.close()
    }

    /** One frame; returns whether the frame loop must keep running. */
    fun step(dt: Double, auto: Boolean): Boolean {
        val at = now + dt
        now = at
        var t = 0.0
        val f: Double
        var moving = 0.0
        if (mode == MODE_AUTO) {
            if (reduced) {
                // Still: the pad, or once fuelled by hand, the deployed screen.
                t = if (userFuel >= 100) ROCKET_DONE_AT + 0.3 else 0.0
                f = if (t > 0) 100.0 else 0.0
                oneShot = false
            } else if (auto || oneShot) {
                val next = clockT + dt
                // Without autoPlay a hand-fuelled launch plays once and stops back on the pad.
                if (!auto && next >= LOOP) oneShot = false
                t = next % LOOP
                f = rocketAutoFuel(t)
            } else {
                f = 0.0
            }
            clockT = t
            moving = if (t > 1.4 && t < 4.6) 1.0 else 0.0
            clock.spin.floatValue = (2 * PI * 6 * t / LOOP).toFloat()
        } else {
            t = 0.5
            var u = userFuel
            if (mode == MODE_USER && holding) {
                val before = u
                u = min(100.0, u + 100 / FILL_FOR * dt)
                userFuel = u
                moving = 1.0
                for (s in intArrayOf(25, 55, 85)) if (before < s && u >= s) feel = feel or FEEL_TICK
                if (u >= 100) {
                    // Full: the loop carries the launch on from the countdown.
                    holding = false
                    feel = feel or FEEL_FULL
                    mode = MODE_AUTO
                    clockT = ROCKET_COUNT_AT
                    oneShot = true
                }
            }
            if (mode == MODE_USER && auto && !holding && at - lastTouch > IDLE) {
                returnAt = at
                returnFuel = u
                mode = MODE_RETURN
            }
            if (mode == MODE_RETURN) {
                val p = rocketEaseOutCubic((at - returnAt) / RETURN)
                u = returnFuel * (1 - p)
                userFuel = u
                moving = 1.0
                if (p >= 1) {
                    mode = MODE_AUTO
                    clockT = 0.0
                    userFuel = 0.0
                }
            }
            f = u
            clock.spin.floatValue = (at * 5).toFloat()
        }
        clock.t.floatValue = t.toFloat()
        clock.fuel.floatValue = f.toFloat()
        fuel = f

        pose.x = 0.0; pose.y = 0.0; pose.rot = 0.0; pose.on = true
        if (!reduced) rocketPose(t, pose)
        val a = if (reduced) 0.0 else rocketShake(t)
        val w = 2 * PI * t / LOOP
        rx = l.padX + pose.x.toFloat()
        ry = baseY + pose.y.toFloat()
        rot = pose.rot.toFloat()
        on = if (reduced && t > ROCKET_LIFT_AT) false else pose.on
        sx = (a * 2.6 * (sin(37 * w) + 0.5 * sin(71 * w + 1))).toFloat()
        sy = (a * 2.2 * (sin(43 * w + 2) + 0.5 * sin(89 * w))).toFloat()
        thr = if (reduced) 0f else rocketThrottle(t).toFloat()
        lift = rocketClamp01((t - ROCKET_LIFT_AT) / 1.2).toFloat()
        age = max(0.0, t - ROCKET_IGNITE_AT).toFloat()
        fade = (1 - rocketEaseOutCubic((t - (ROCKET_DONE_AT - 0.2)) / 1.2)).toFloat()
        trailPath(t)
        trailA = if (reduced) 0f else (rocketClamp01((t - ROCKET_LIFT_AT) / 0.15) * (1 - rocketEaseOutCubic((t - ROCKET_DONE_AT) / 0.8))).toFloat()
        sloshPath(min(1.0, f / 100), t, moving)
        frame.intValue += 1
        return auto || oneShot || holding || mode == MODE_RETURN || (mode == MODE_USER && auto)
    }
}

/** Paints for what is drawn each frame, made once. */
private class RocketPaints(l: RocketLayout) {
    val bitmap = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hwBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val trailWide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(89, 232, 224, 240)
        if (hwBlur) maskFilter = BlurMaskFilter((9f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
    }
    val trailCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(140, 255, 250, 246)
        if (hwBlur) maskFilter = BlurMaskFilter((2.5f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
    }
    val fuel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            l.tank.x, 0f, l.tank.x + l.tank.w, 0f,
            intArrayOf(0xFFC2561C.toInt(), 0xFFFFB04F.toInt(), 0xFFFFD58A.toInt(), 0xFFD86A22.toInt()),
            floatArrayOf(0f, 0.35f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    val tankRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = android.graphics.Color.argb(46, 255, 255, 255)
    }
    val tankShine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(41, 255, 255, 255) }
    val tankRect = RectF(l.tank.x, l.tank.y, l.tank.x + l.tank.w, l.tank.y + l.tank.h)
    val tankClip = Path().apply { addRoundRect(tankRect, l.tank.w / 2, l.tank.w / 2, Path.Direction.CW) }
    val shineRect = RectF(l.tank.x + 5, l.tank.y + 8, l.tank.x + 9, l.tank.y + l.tank.h - 8)
    val dst = RectF(0f, 0f, l.w, l.h)
}

/**
 * "Shipwright": a deploy screen where the progress is the fuel of a rocket on its pad; at 100 % it
 * counts down, lights, lifts off and the next rocket rolls in. Hold the pad to fuel it.
 *
 * @param autoPlay fuels, counts down, launches, deploys, rolls out the next rocket.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RocketLaunchProgress(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val density = LocalDensity.current
    val view = LocalView.current
    val top = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.getTop(density),
        WindowInsets.displayCutout.getTop(density),
    ) / density.density
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density) / density.density

    BoxWithConstraints(modifier.fillMaxSize().background(RocketNight)) {
        val w = maxWidth.value
        val h = maxHeight.value
        val l = remember(w, h, top, bottom) { rocketLayout(w, h, top, bottom) }
        val back by produceState<android.graphics.Bitmap?>(null, l) {
            value = withContext(Dispatchers.Default) { rocketBakeBack(l, min(3f, density.density)) }
        }
        val backBitmap = back ?: return@BoxWithConstraints
        val state = remember(l, reduced) { RocketState(l, reduced) }
        val running = remember(state) { mutableStateOf(true) }
        val isRunning by running

        LaunchedEffect(state, auto, isRunning) {
            if (!isRunning) return@LaunchedEffect
            var last = -1L
            while (true) {
                val keep = withFrameNanos { t ->
                    val dt = if (last < 0) 1.0 / 60 else min((t - last) / 1e9, 0.05)
                    last = t
                    val keep = state.step(dt, auto)
                    if (state.feel != 0) {
                        if (state.feel and FEEL_FULL != 0) {
                            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
                        } else {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        state.feel = 0
                    }
                    keep
                }
                if (!keep) {
                    running.value = false
                    break
                }
            }
        }

        val shader = remember(l) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching {
                    RuntimeShader(ROCKET_EXHAUST_AGSL).apply {
                        setFloatUniform("pad", l.padX, l.groundY - 8)
                        setFloatUniform("k", l.k)
                    }
                }.getOrNull()
            } else {
                null
            }
        }
        val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
        val fallback = remember { RocketExhaustFallback() }
        val paints = remember(l) { RocketPaints(l) }
        val parts = remember { RocketParts() }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Launch pad. Hold to fuel the rocket." }
                .pointerInput(state) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        state.press()
                        running.value = true
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                            }
                        } finally {
                            state.release()
                            running.value = true
                        }
                    }
                },
        ) {
            state.frame.intValue
            val d = density.density
            scale(d, d, pivot = Offset.Zero) {
                drawIntoCanvas { c -> c.nativeCanvas.drawBitmap(backBitmap, null, paints.dst, paints.bitmap) }
                // The engine shakes what stands on the pad; the sky and the ground stay put.
                translate(state.sx, state.sy) {
                    drawIntoCanvas { canvas ->
                        val c = canvas.nativeCanvas
                        if (state.trailA > 0.001f) {
                            paints.trailWide.alpha = (89 * state.trailA).roundToInt()
                            paints.trailCore.alpha = (140 * state.trailA).roundToInt()
                            c.drawPath(state.trail, paints.trailWide)
                            c.drawPath(state.trail, paints.trailCore)
                        }
                        c.save()
                        c.clipPath(paints.tankClip)
                        c.drawPath(state.slosh, paints.fuel)
                        c.restore()
                        c.drawRoundRect(paints.tankRect, l.tank.w / 2, l.tank.w / 2, paints.tankRim)
                        c.drawRoundRect(paints.shineRect, 2f, 2f, paints.tankShine)
                        if (state.on) {
                            c.save()
                            c.translate(state.rx, state.ry)
                            c.rotate(state.rot * 180f / PI.toFloat())
                            c.scale(l.k, l.k)
                            parts.draw(c)
                            c.restore()
                        }
                        if (shader == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            fallback.draw(
                                c, state.rx, state.ry, sin(state.rot), -cos(state.rot), state.thr, state.lift,
                                state.age, state.fade, l.padX, l.groundY - 8, l.k,
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                        shader.setFloatUniform("nozzle", state.rx, state.ry)
                        shader.setFloatUniform("dir", sin(state.rot), -cos(state.rot))
                        shader.setFloatUniform("throttle", state.thr)
                        shader.setFloatUniform("lift", state.lift)
                        shader.setFloatUniform("age", state.age)
                        shader.setFloatUniform("fade", state.fade)
                        drawRect(brush, Offset(-20f, -20f), Size(l.w + 40, l.h + 40))
                    }
                }
            }
        }

        val baseY = l.groundY - 14
        val skyMid = top + 108 + (baseY - l.rocketH - top - 108) * 0.42f
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = (top + 14).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("SHIPWRIGHT", style = TextStyle(color = RocketAmber, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp))
            BasicText("Deploying v4.2.0", style = TextStyle(color = RocketInk, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp))
            RocketStatusLine(state.clock)
        }
        RocketOdometer(state.clock, Modifier.offset(x = 22.dp, y = (top + 118).dp))
        RocketCountdown(state.clock, Modifier.offset(y = (skyMid - 60).dp))
        RocketBadge(state.clock, Modifier.offset(y = (skyMid - 22).dp))
        RocketStepsCard(state.clock, l.cardTop, l.cardH, l.rowH)
    }
}
