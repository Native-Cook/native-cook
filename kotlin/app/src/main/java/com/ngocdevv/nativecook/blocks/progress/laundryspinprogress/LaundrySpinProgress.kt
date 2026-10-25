package com.ngocdevv.nativecook.blocks.progress.laundryspinprogress

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Laundry Spin Progress — a laundromat app's machine screen ("Suds — Washer 4"): the progress of a
 wash cycle shown through the washer's door. The water level, the tumbling clothes, the suds and
 the spin (clothes pressed to the wall, smeared by motion blur) tell the phase; the phase name,
 the minutes left and a four-step bar roll and fill below.

 Touch: turn the drum by dragging round the door (it follows the finger's angle), flick it and it
 keeps turning and slows down; slow turns tumble the clothes, past ~3 rev/s they plaster to the
 wall. Two seconds after the last touch, once the drum has slowed, the scene eases back to its
 rest (0.9 s) and the loop restarts from t = 0.

 One Canvas, one AGSL shader (LaundrySpinShader.kt; drawn stand-in below Android 13); all motion is
 uniforms. Reduced motion: the washing phase, still; the drum still turns under a finger.
 */

internal val LaundryBackground = Color(0xFFECEDEC)
private val Ink = Color(0xFF1F2A33)
private val Mute = Color(0x941F2A33)
private val Accent = Color(0xFF2F8FB8)
private val Steps = listOf("Wash", "Rinse", "Spin", "Done")
private const val IDLE = 2.0
private const val RETURN = 0.9
private const val STILL_T = 3.0
private const val MODE_AUTO = 0
private const val MODE_HAND = 1
private const val MODE_RETURN = 2

internal class LaundryGeo(val w: Double, val h: Double) {
    val unit = max(0.74, min(1.15, min(w / 393, h / 852)))
    val cx = w / 2
    val cy = h * 0.45
    val rg = min(138 * unit, w * 0.35)
}

private class LaundryState {
    var now = -1.0
    var mode = MODE_AUTO
    var autoStart = 0.0
    var angleOffset = 0.0
    var lastTouch = 0.0
    var touching = false
    // hand: drum angle, speed rev/s, finger angle; the scene it started from; progress and phase held
    var handAngle = 0.0
    var handSpeed = 0.0
    var fingerAngle = 0.0
    var base = laundryRestScene()
    var heldProgress = 0.0
    var heldIndex = 0
    var returnFrom = laundryRestScene()
    var returnStart = 0.0
    var lastPlaster = false
    var lastQuarter = Int.MIN_VALUE
    // the frame on screen
    var scene = laundryRestScene()
    var phase = 0.0
    var progress = 0.0
    var index = 0
    val garments = DoubleArray(5 * LaundryGarments)
    var ready = false
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
}

private fun LaundryState.step(now: Double, auto: Boolean, autoPlay: Boolean, reduced: Boolean) {
    val prev = this.now
    val dt = if (prev < 0) 0.0 else min(0.033, now - prev)
    if (prev < 0) autoStart = now
    this.now = now
    if (mode != MODE_AUTO) {
        if (!touching) {
            // a flicked drum keeps turning and slows down
            handSpeed *= exp(-dt / 1.2)
            handAngle += handSpeed * PI * 2 * dt
        }
        if (mode == MODE_HAND && !touching && now - lastTouch > IDLE && abs(handSpeed) < 0.3 && autoPlay) {
            mode = MODE_RETURN
            returnStart = now
            returnFrom = laundryHandScene(base, handAngle, handSpeed)
        } else if (mode == MODE_RETURN && now - returnStart >= RETURN) {
            mode = MODE_AUTO
            autoStart = now
            angleOffset = handAngle % (PI * 2)
        }
    }
    when (mode) {
        MODE_HAND -> {
            scene = laundryHandScene(base, handAngle, handSpeed)
            phase = (now % LOOP) / LOOP
            progress = heldProgress
            index = heldIndex
        }
        MODE_RETURN -> {
            val k = laundryEaseInOut(min(1.0, (now - returnStart) / RETURN))
            val rest = if (reduced) laundrySceneAt(STILL_T) else laundryRestScene()
            val s = returnFrom.copyOf()
            for (i in 1 until s.size) s[i] = returnFrom[i] + (rest[i] - returnFrom[i]) * k
            s[0] = handAngle
            scene = s
            phase = (now % LOOP) / LOOP
            progress = heldProgress * (1 - k)
            index = if (k > 0.5) 0 else heldIndex
        }
        else -> {
            val t = if (reduced) STILL_T else if (auto) ((now - autoStart) % LOOP + LOOP) % LOOP else 0.0
            val s = laundrySceneAt(t)
            s[S_ANGLE] += angleOffset
            scene = s
            phase = t / LOOP
            progress = laundryProgressAt(t)
            index = laundryPhaseAt(t)
        }
    }
    for (i in 0 until LaundryGarments) laundryGarmentAt(i, scene, garments)
    ready = true
}

/**
 * Washer 4, seen through its door, telling the progress of a wash cycle. Drag round the door to
 * turn the drum.
 *
 * @param autoPlay runs a whole wash, rinse and spin, then fogs and clears, on a 10 s loop.
 */
@Composable
fun LaundrySpinProgress(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val state = remember(reduced) { LaundryState() }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(LAUNDRY_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val fallback = remember { LaundryFallback() }

    BoxWithConstraints(modifier.fillMaxSize().background(LaundryBackground)) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val g = remember(w, h) { LaundryGeo(w, h) }

        LaunchedEffect(state, auto) {
            while (true) {
                withFrameNanos { n ->
                    state.step(n / 1e9, auto, autoPlay, reduced)
                    val plastered = state.mode == MODE_HAND && state.scene[S_PLASTER] > 0.5
                    if (plastered && !state.lastPlaster) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    state.lastPlaster = plastered
                    if (state.mode == MODE_HAND && state.touching) {
                        val q = floor(state.handAngle / (PI / 2)).toInt()
                        if (state.lastQuarter != Int.MIN_VALUE && q != state.lastQuarter) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        state.lastQuarter = q
                    } else {
                        state.lastQuarter = Int.MIN_VALUE
                    }
                    state.tick.intValue += 1
                }
                // Still (no autoPlay, or reduced motion) until a finger lands; a flicked drum runs down first.
                val idle = (state.mode == MODE_AUTO && !auto) ||
                    (!autoPlay && state.mode == MODE_HAND && !state.touching && abs(state.handSpeed) < 0.005)
                if (idle) {
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Washer 4, seen through its door. Drag round the door to turn the drum." }
                .pointerInput(state, g) {
                    fun angleOf(x: Double, y: Double) = atan2(-(y - g.cy), x - g.cx)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val x0 = (down.position.x / d).toDouble()
                        val y0 = (down.position.y / d).toDouble()
                        if (state.mode != MODE_HAND && state.ready) {
                            state.base = state.scene.copyOf()
                            state.heldProgress = state.progress
                            state.heldIndex = state.index
                            state.handAngle = state.scene[S_ANGLE]
                            state.handSpeed = state.scene[S_SPEED]
                        }
                        state.fingerAngle = angleOf(x0, y0)
                        state.mode = MODE_HAND
                        state.touching = true
                        state.lastTouch = state.now
                        state.wake.intValue += 1
                        var lastMs = down.uptimeMillis
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                if (c.position == c.previousPosition) continue
                                c.consume()
                                val x = (c.position.x / d).toDouble()
                                val y = (c.position.y / d).toDouble()
                                val a = angleOf(x, y)
                                var da = a - state.fingerAngle
                                if (da > PI) da -= PI * 2
                                if (da < -PI) da += PI * 2
                                // only a drag far enough from the centre turns the drum (near it, angles jump)
                                val k = min(1.0, hypot(x - g.cx, y - g.cy) / (g.rg * 0.35))
                                val dt = max(1.0 / 120, (c.uptimeMillis - lastMs) / 1000.0)
                                lastMs = c.uptimeMillis
                                val v = da * k / (PI * 2) / dt
                                state.handAngle += da * k
                                state.handSpeed = state.handSpeed * 0.7 + v * 0.3
                                state.fingerAngle = a
                                state.lastTouch = state.now
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
            val s = state.scene
            val gm = state.garments
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("size", w.toFloat(), h.toFloat())
                    shader.setFloatUniform("door", g.cx.toFloat(), g.cy.toFloat(), g.rg.toFloat(), g.unit.toFloat())
                    shader.setFloatUniform("scene", (s[S_ANGLE] % (PI * 2)).toFloat(), s[S_SPEED].toFloat(), s[S_LEVEL].toFloat(), s[S_SUDS].toFloat())
                    shader.setFloatUniform("extra", s[S_FOG].toFloat(), s[S_SLOSH].toFloat(), state.phase.toFloat(), 0f)
                    for (i in 0 until LaundryGarments) {
                        val o = i * 5
                        shader.setFloatUniform("g$i", gm[o].toFloat(), gm[o + 1].toFloat(), gm[o + 2].toFloat(), gm[o + 3].toFloat())
                    }
                    shader.setFloatUniform("st", gm[4].toFloat(), gm[9].toFloat(), gm[14].toFloat(), gm[19].toFloat())
                    shader.setFloatUniform("st2", gm[24].toFloat(), gm[29].toFloat(), 0f, 0f)
                    drawRect(brush, Offset.Zero, Size(w.toFloat(), h.toFloat()))
                } else {
                    with(fallback) { draw(g, s, gm) }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 16).dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("SUDS · WASHER 4", style = TextStyle(color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
                Row(
                    Modifier
                        .height(24.dp)
                        .background(Color(0x1F2F8FB8), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(Accent, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    BasicText("Live", style = TextStyle(color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
            BasicText(
                "Cottons 40°",
                modifier = Modifier.padding(top = 8.dp),
                style = TextStyle(color = Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
            )
            BasicText(
                "Hale Street Laundromat · 1,200 rpm spin",
                modifier = Modifier.padding(top = 4.dp),
                style = TextStyle(color = Mute, fontSize = 15.sp),
            )
        }

        val hair = (1f / screen.density).dp
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = (safeBottom + 16).dp)
                .fillMaxWidth()
                .cssBoxShadow(Color(0x1F14283C), 1.dp, 3.dp, 26.dp)
                .cssBoxShadow(Color(0x1F14283C), 16.dp, 36.dp, 26.dp)
                .background(Color(0xFFFAFBFB), RoundedCornerShape(26.dp))
                .border(hair, Color(0x1A1F2A33), RoundedCornerShape(26.dp))
                .drawBehind {
                    val r = 26.dp.toPx()
                    drawLine(Color(0xF2FFFFFF), Offset(r, 1.dp.toPx()), Offset(size.width - r, 1.dp.toPx()), 1.dp.toPx())
                }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                LaundrySpinRollLabel(
                    labels = LaundryPhases,
                    index = { state.tick.intValue; state.index },
                    line = 34f,
                    style = TextStyle(color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
                    still = reduced,
                )
                LaundrySpinRollNumber(
                    value = { state.tick.intValue; laundryMinutesLeft(state.progress).toFloat() },
                    digits = 2,
                    line = 34f,
                    style = TextStyle(color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    suffix = " min",
                    suffixStyle = TextStyle(color = Mute, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    still = reduced,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in Steps.indices) LaundryStep(i, state)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("We will ping you when it is done.", style = TextStyle(color = Mute, fontSize = 13.sp))
                Box(
                    Modifier
                        .size(44.dp, 26.dp)
                        .background(Accent, RoundedCornerShape(13.dp))
                        .padding(2.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .cssBoxShadow(Color(0x40000000), 1.dp, 2.dp, 11.dp)
                            .background(Color(0xFFFBFCFC), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.LaundryStep(i: Int, state: LaundryState) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            state.tick.intValue
            val r = CornerRadius(3.dp.toPx())
            drawRoundRect(Color(0x171F2A33), cornerRadius = r)
            val k = (state.progress * 4 - i).toFloat().coerceIn(0f, 1f)
            if (k > 0f) {
                // the fill grows from the left, like the React Native scaleX
                drawRoundRect(Accent, size = Size(size.width * k, size.height), cornerRadius = r)
            }
        }
        BasicText(
            Steps[i],
            modifier = Modifier.graphicsLayer {
                state.tick.intValue
                alpha = if (state.progress * 4 >= i - 0.001) 1f else 0.45f
            },
            style = TextStyle(color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}
