package com.ngocdevv.nativecook.blocks.gestures.potterywheelshape

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Pottery Wheel Shape — a ceramics-class screen ("Kiln Club — Throw a vase") with a lump of wet
 clay turning on a wheel. The whole studio is one AGSL shader (PotteryWheelShapeShader.kt; a drawn
 stand-in below Android 13, PotteryWheelShapeFallback.kt); slip drops are one Path per frame; the
 readouts are rolling digits drawn in draw lambdas.

 Touch: drag on either side of the clay. Pressing towards the middle squeezes the wall at that
 height (and pushes the clay up), dragging up along the wall pulls it taller, down squats it; slip
 flies off while the finger works and a tick marks every whole centimetre of height. The wheel
 speeds up under the hand. Two seconds after the last touch the clay eases back to the lump
 (0.9 s, ease-in-out) and the loop starts again from t = 0.

 Reduced motion: the finished vase, still, no spray; the clay can still be shaped by hand.
 */

internal val PotteryBackground = Color(0xFF130F0C)
private val Ink = Color(0xFFF3E7DA)
private val Mute = Color(0x99F3E7DA)
private val Accent = Color(0xFFE39A5B)
private val LineC = Color(0x1FFFECDA)
private val DropColor = Color(150, 98, 72, 230)

private const val IDLE = 2.0
private const val RETURN = 0.9
private const val STILL_T = 6.6

private const val MODE_AUTO = 0
private const val MODE_HAND = 1
private const val MODE_RETURN = 2

/** Everything the frame loop and the gestures share; draws read `tick` and then these fields. */
private class PotteryState {
    var now = -1.0
    var mode = MODE_AUTO
    var autoStart = 0.0
    var spinOffset = 0.0
    var lastTouch = 0.0
    var touching = false
    var hand = potteryRestPose()
    var handSpin = 0.0
    var handSpeed = PotterySlow
    val pad = doubleArrayOf(0.0, 0.0, 0.0)
    val spray = doubleArrayOf(0.0, 0.5, 0.0)
    var returnFrom = potteryRestPose()
    var returnStart = 0.0
    var lastCm = -1

    // the frame on screen
    var pose = potteryRestPose()
    var spin = 0.0
    var speed = PotterySlow
    var wob = 0.0
    val pads = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    var phase = 0
    var sprayClock = 0.0
    var sprayAuto = false
    var ready = false

    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
}

private fun PotteryState.autoFrame(g: PotteryGeo, t: Double, offset: Double) {
    pose = potteryPoseAt(t)
    spin = potterySpinAt(t) + offset
    wob = potteryWobbleX(pose, spin, g.unit)
    val f = potteryFingersAt(t, pose)
    potteryFingerOnScreen(g, pose, f, 0, wob, pads, 0)
    potteryFingerOnScreen(g, pose, f, 4, wob, pads, 4)
    speed = potterySpeedAt(t)
    phase = potteryPhaseAt(t)
}

/** Advances the clock to `now` and computes the frame on screen. */
private fun PotteryState.step(g: PotteryGeo, now: Double, auto: Boolean, reduced: Boolean) {
    val prev = this.now
    val dt = if (prev < 0) 0.0 else min(0.05, now - prev)
    if (prev < 0) autoStart = now
    this.now = now
    when (mode) {
        MODE_HAND -> {
            val target = if (touching) PotteryFast else handSpeed
            handSpeed += (target - handSpeed) * min(1.0, dt * 3)
            if (!touching) {
                hand = potterySettlePose(hand, min(1.0, dt * 8))
                pad[2] = max(0.0, pad[2] - dt * 4)
                spray[0] = max(0.0, spray[0] - dt * 3)
                if (now - lastTouch > IDLE) {
                    mode = MODE_RETURN
                    returnStart = now
                    returnFrom = hand
                }
            } else {
                spray[0] *= exp(-dt * 2.5)
            }
        }
        MODE_RETURN -> {
            handSpeed += (PotterySlow - handSpeed) * min(1.0, dt * 4)
            if (now - returnStart >= RETURN) {
                mode = MODE_AUTO
                autoStart = now
                spinOffset = handSpin % (PI * 2)
            }
        }
    }
    if (mode != MODE_AUTO) handSpin += handSpeed * PI * 2 * dt

    sprayAuto = false
    if (mode == MODE_AUTO) {
        when {
            reduced -> {
                autoFrame(g, STILL_T, 0.0)
                pose = potteryVasePose()
                spin = 0.0
                speed = 0.0
                for (i in 0 until 8) pads[i] = if (i == 3 || i == 7) 1.0 else 0.0
            }
            auto -> {
                val t = (now - autoStart) % LOOP
                autoFrame(g, t, spinOffset)
                sprayAuto = true
                sprayClock = t
            }
            else -> {
                autoFrame(g, 0.0, 0.0)
                spin = spinOffset + PotterySlow * PI * 2 * (now - autoStart)
                wob = potteryWobbleX(pose, spin, g.unit)
            }
        }
        spray[0] = 0.0
        spray[2] = 0.0
    } else {
        var p = hand
        var padAlpha = pad[2]
        phase = PotteryPhases.size - 1
        if (mode == MODE_RETURN) {
            val k = potteryEaseInOut(min(1.0, (now - returnStart) / RETURN))
            p = potteryMix(returnFrom, if (reduced) potteryVasePose() else potteryRestPose(), k)
            padAlpha = 0.0
            phase = 0
        }
        pose = p
        spin = handSpin
        speed = handSpeed
        wob = potteryWobbleX(p, spin, g.unit)
        pads[0] = pad[0]; pads[1] = pad[1]; pads[2] = padAlpha; pads[3] = 15 * g.unit
        pads[4] = 0.0; pads[5] = 0.0; pads[6] = 0.0; pads[7] = 1.0
        sprayClock = now
        if (reduced) spray[0] = 0.0
    }
    ready = true
}

/** Slip drops flung off the turning wall: pure functions of the clock and hash(i). */
private fun PotteryState.sprayPath(g: PotteryGeo, path: Path, scratch: DoubleArray) {
    path.reset()
    val u = g.unit
    val clock = sprayClock
    for (i in 0 until PotterySprayN) {
        val age = ((clock / PotterySprayPeriod + potteryHash(i)) % 1.0 + 1.0) % 1.0
        var strength = spray[0]
        var share = spray[1]
        if (sprayAuto) {
            // the drop was thrown at its own birth time, by the script's spray at that moment
            val born = (((clock - age * PotterySprayPeriod) % LOOP) + LOOP) % LOOP
            potterySprayAt(born, scratch)
            strength = scratch[0]
            share = scratch[1]
        }
        if (potteryHash(i + 71) > strength || age > 0.8) continue
        val side = if (spray[2] != 0.0) {
            if (potteryHash(i + 13) < 0.8) spray[2] else -spray[2]
        } else if (potteryHash(i + 13) < 0.5) -1.0 else 1.0
        val h0 = max(4.0, min(pose[P_H] - 2, pose[P_H] * share + (potteryHash(i + 29) - 0.5) * 26))
        val r = potteryRadiusAt(pose, h0) * u
        val vx = (70 + 120 * potteryHash(i + 3)) * u
        val vy = (30 + 90 * potteryHash(i + 5)) * u
        val a = age * PotterySprayPeriod
        val x = g.cx + wob + side * (r + vx * a)
        val y = g.baseY - h0 * u * PotteryCosE - vy * a + 0.5 * 900 * u * a * a
        val size = (1.2 + 2.2 * potteryHash(i + 41)) * (1 - age * 0.7) * u
        path.addOval(Rect(Offset(x.toFloat(), y.toFloat()), size.toFloat()))
        val tx = x - side * vx * 0.012
        val ty = y + (vy - 900 * u * a) * 0.012
        path.addOval(Rect(Offset(tx.toFloat(), ty.toFloat()), (size * 0.6).toFloat()))
    }
}

/**
 * Clay on a potter's wheel under a warm studio light. Drag the wall in, up or down to shape it.
 *
 * @param autoPlay throws a vase from the lump and lets it fall back, on a 9 s loop.
 */
@Composable
fun PotteryWheelShape(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val state = remember(reduced) { PotteryState() }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(POTTERY_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val fallback = remember { PotteryFallback() }
    val drops = remember { Path() }
    val scratch = remember { DoubleArray(2) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PotteryBackground),
    ) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val g = remember(w, h) { PotteryGeo(w, h) }

        LaunchedEffect(state, g, auto) {
            while (true) {
                withFrameNanos { n ->
                    state.step(g, n / 1e9, auto, reduced)
                    if (state.mode == MODE_HAND && state.touching) {
                        val cm = (state.pose[P_H] / PotteryPtPerCm).roundToInt()
                        if (state.lastCm >= 0 && cm != state.lastCm) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        state.lastCm = cm
                    } else {
                        state.lastCm = -1
                    }
                    state.tick.intValue += 1
                }
                // Under reduced motion the still vase needs no frames until a finger lands.
                if (reduced && state.mode == MODE_AUTO) {
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Clay on a potter's wheel. Drag the wall in, up or down to shape it." }
                .pointerInput(state, g) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        // take hold: the hand starts from whatever the clay looks like now
                        if (state.mode != MODE_HAND && state.ready) {
                            state.hand = state.pose.copyOf()
                            state.handSpin = state.spin
                            state.handSpeed = state.speed
                        }
                        state.mode = MODE_HAND
                        state.touching = true
                        state.lastTouch = state.now
                        state.pad[0] = (down.position.x / d).toDouble()
                        state.pad[1] = (down.position.y / d).toDouble()
                        state.pad[2] = 1.0
                        state.wake.intValue += 1
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                val delta = c.position - c.previousPosition
                                if (delta == Offset.Zero) continue
                                tracker.addPosition(c.uptimeMillis, c.position)
                                c.consume()
                                val x = (c.position.x / d).toDouble()
                                val y = (c.position.y / d).toDouble()
                                state.hand = potteryShapeByFinger(g, state.hand, x, y, (delta.x / d).toDouble(), (delta.y / d).toDouble())
                                state.pad[0] = x
                                state.pad[1] = y
                                state.pad[2] = 1.0
                                state.lastTouch = state.now
                                val hh = state.hand[P_H]
                                val share = min(1.0, max(0.0, (g.baseY - y) / (g.unit * PotteryCosE) / hh))
                                val v = tracker.calculateVelocity()
                                val s = potterySpeedOf((v.x / d).toDouble(), (v.y / d).toDouble())
                                state.spray[0] = max(state.spray[0], s)
                                state.spray[1] = share
                                state.spray[2] = if (x >= g.cx) 1.0 else -1.0
                            }
                        } finally {
                            state.touching = false
                            state.lastTouch = state.now
                        }
                    }
                },
        ) {
            state.tick.intValue
            if (!state.ready) return@Canvas
            val d = density
            scale(d, d, pivot = Offset.Zero) {
                val u = g.unit
                val p = state.pose
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("size", w.toFloat(), h.toFloat())
                    shader.setFloatUniform("base", g.cx.toFloat(), g.baseY.toFloat())
                    shader.setFloatUniform("unit", u.toFloat())
                    shader.setFloatUniform("profA", (p[P_R] * u).toFloat(), (p[P_R + 1] * u).toFloat(), (p[P_R + 2] * u).toFloat(), (p[P_R + 3] * u).toFloat())
                    shader.setFloatUniform("profB", (p[P_R + 4] * u).toFloat(), (p[P_R + 5] * u).toFloat(), (p[P_R + 6] * u).toFloat(), (p[P_R + 7] * u).toFloat())
                    shader.setFloatUniform("shape", (p[P_H] * u).toFloat(), p[P_OPEN].toFloat(), state.wob.toFloat(), state.speed.toFloat())
                    shader.setFloatUniform("spin", (state.spin % (PI * 2)).toFloat())
                    val f = state.pads
                    shader.setFloatUniform("fingerA", f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat())
                    shader.setFloatUniform("fingerB", f[4].toFloat(), f[5].toFloat(), f[6].toFloat(), f[7].toFloat())
                    drawRect(brush, Offset.Zero, Size(w.toFloat(), h.toFloat()))
                } else {
                    with(fallback) { draw(g, p, state.spin, state.wob, state.pads) }
                }
                if (!reduced) {
                    state.sprayPath(g, drops, scratch)
                    drawPath(drops, DropColor)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 16).dp),
        ) {
            BasicText("KILN CLUB · LESSON 4 OF 8", style = TextStyle(color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
            BasicText(
                "Throw a vase",
                modifier = Modifier.padding(top = 8.dp),
                style = TextStyle(color = Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
            )
            BasicText(
                "Keep your hands wet. Press the wall in, then pull it up slowly.",
                modifier = Modifier.padding(top = 6.dp).widthIn(max = 300.dp),
                style = TextStyle(color = Mute, fontSize = 15.sp, lineHeight = 21.sp),
            )
            Row(
                Modifier
                    .padding(top = 16.dp)
                    .height(32.dp)
                    .background(Color(0x0FFFECDA), RoundedCornerShape(16.dp))
                    .border((1f / screen.density).dp, LineC, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(listOf(Accent.copy(alpha = 0.8f), Accent.copy(alpha = 0f)), center, 6.dp.toPx()),
                                6.dp.toPx(),
                            )
                            drawCircle(Accent)
                        },
                )
                Spacer(Modifier.width(8.dp))
                PotteryWheelShapeRollLabel(
                    labels = PotteryPhases,
                    index = { state.tick.intValue; state.phase },
                    line = 20f,
                    style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    still = reduced,
                )
            }
        }

        val valueStyle = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val unitStyle = TextStyle(color = Mute, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = (safeBottom + 16).dp)
                .fillMaxWidth()
                .height(84.dp)
                .cssBoxShadow(Color(0x59000000), 1.dp, 3.dp, 24.dp)
                .cssBoxShadow(Color(0x59000000), 16.dp, 36.dp, 24.dp)
                .background(Color(0xFF211913), RoundedCornerShape(24.dp))
                .border((1f / screen.density).dp, Color(0x29FFECDA), RoundedCornerShape(24.dp))
                .drawBehind {
                    // inset 0 1px 0: a faint lit top edge
                    val r = 24.dp.toPx()
                    drawLine(Color(0x14FFECDA), Offset(r, 1.dp.toPx()), Offset(size.width - r, 1.dp.toPx()), 1.dp.toPx())
                }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PotteryStat("HEIGHT") {
                PotteryWheelShapeRollNumber(
                    value = { state.tick.intValue; (state.pose[P_H] / PotteryPtPerCm).toFloat() },
                    digits = 2, line = 28f, style = valueStyle, suffix = " cm", suffixStyle = unitStyle, still = reduced,
                )
            }
            PotteryRule()
            PotteryStat("WIDEST") {
                PotteryWheelShapeRollNumber(
                    value = { state.tick.intValue; (potteryWidest(state.pose) / PotteryPtPerCm).toFloat() },
                    digits = 2, line = 28f, style = valueStyle, prefix = "Ø ", suffix = " cm", suffixStyle = unitStyle, still = reduced,
                )
            }
            PotteryRule()
            PotteryStat("WHEEL") {
                PotteryWheelShapeRollNumber(
                    value = { state.tick.intValue; (state.speed * 60).toFloat() },
                    digits = 3, line = 28f, style = valueStyle, suffix = " rpm", suffixStyle = unitStyle, still = reduced,
                )
            }
        }
    }
}

@Composable
private fun RowScope.PotteryStat(label: String, value: @Composable () -> Unit) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(label, style = TextStyle(color = Mute, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp))
        value()
    }
}

@Composable
private fun PotteryRule() {
    val d = LocalDensity.current.density
    Box(
        Modifier
            .padding(horizontal = 12.dp)
            .width((1f / d).dp)
            .height(40.dp)
            .background(LineC),
    )
}
