package com.ngocdevv.nativecook.blocks.progress.kettleboilprogress

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 Kettle Boil Progress — "Steep", a tea app heating water for a sencha. A glass kettle on a
 charcoal base: the LED ring round the base fills with the temperature, micro bubbles gather on
 the floor, streams rise, the surface starts to roll, the kettle clicks off at 80 °C and steam
 pours from the spout. The temperature rolls up digit by digit.

 Drawing: one Canvas with one AGSL shader (KettleBoilProgressShader.kt): wall, counter, base and
 ring, glass, water, bubbles, steam; below Android 13, KettleBoilProgressFallback.kt. Its uniforms
 come from one state object written by the frame loop; the readout, the status line and the
 button read it in graphicsLayer lambdas, so nothing recomposes while it heats.

 Touch: a tap anywhere starts heating, a tap while it heats cancels (the bubbles settle and it
 cools); at 80 °C it clicks off by itself. 2 s after the last tap, once the element is off, it
 eases back to cold still water (0.9 s) and the loop starts again. Reduced motion: still water at
 21 °C, no loop; a tap still heats and cancels, the readout changing without rolling.

 LOOP = 9 s:
   0.0 – 1.4  rest: still water at 21 °C, the ring dark, "Ready".
   1.3 – 1.45 the switch is pressed down; the ring lights.
   1.4 – 5.2  heat: 21 → 80 °C (ease-in-out), the ring fills, bubbles, streams, a rolling surface.
   5.2        click: the switch springs back up, the ring flashes and dims (0.6 s).
   5.2 – 6.8  steam pours from the spout and the lid; the boil settles.
   6.8 – 8.4  cool reset: the steam fades, the temperature rolls back to 21, the ring empties.
   8.4 – 9.0  rest; the frame at 9 s is the frame at 0 s.
*/

private const val LOOP = 9f
private const val T_COLD = 21f
private const val T_TARGET = 80f
private const val MODE_AUTO = 0
private const val MODE_USER = 1
private const val MODE_RETURN = 2
private const val IDLE = 2f
private const val RETURN = 0.9f

private val Ink = Color(0xFFEEF3EF)
private val InkSoft = Color(0xA3EEF3EF)
private val InkFaint = Color(0x6BEEF3EF)
private val Mint = Color(0xFFA9E3BE)
private val MintInk = Color(0xFF123222)
private val Status = listOf("Ready · cold water", "Heating to 80 °C", "Ready to steep", "Cooling down")
private val Action = listOf("Heat to 80 °C", "Cancel", "Start 2 min timer", "Heat again")
private const val DIGIT_H = 72

/** The kettle's origin (where the body meets the base) and scale, and the counter's top, in dp. */
internal class KettleLayout(val ox: Float, val oy: Float, val k: Float, val counter: Float)

private fun kettleLayout(width: Float, height: Float, insetTop: Float, insetBottom: Float): KettleLayout {
    val counter = height - insetBottom - 196f
    val k = max(0.6f, minOf(1.12f, (width - 40f) / 330f, (counter - insetTop - 200f) / 300f))
    return KettleLayout(width / 2f + 8f * k, counter - 52f * k, k, counter)
}

private fun clamp01(x: Float) = min(1f, max(0f, x))
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeInOut(x: Float): Float {
    val c = clamp01(x)
    return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3) / 2f
}
private fun easeOut(x: Float): Float {
    val c = clamp01(x)
    return 1f - (1f - c).pow(3)
}

/** How hard the water works at a temperature while the element is on (0 still … 1 rolling boil). */
private fun activityAt(temp: Float) = clamp01((temp - 28f) / 50f).pow(1.15f)

internal class KettleState {
    var temp = T_COLD
    var heat = 0f
    var ringFill = 0f
    var ringOn = 0f
    var steam = 0f
    var lever = 0f
    /** 0 Ready, 1 Heating, 2 Ready to steep, 3 Cooling (4 reads like 0). */
    var status = 0

    fun reset() {
        temp = T_COLD; heat = 0f; ringFill = 0f; ringOn = 0f; steam = 0f; lever = 0f; status = 0
    }

    fun copyFrom(a: KettleState) {
        temp = a.temp; heat = a.heat; ringFill = a.ringFill; ringOn = a.ringOn; steam = a.steam; lever = a.lever; status = a.status
    }

    fun blend(a: KettleState, b: KettleState, e: Float) {
        temp = a.temp + (b.temp - a.temp) * e
        heat = a.heat + (b.heat - a.heat) * e
        ringFill = a.ringFill + (b.ringFill - a.ringFill) * e
        ringOn = a.ringOn + (b.ringOn - a.ringOn) * e
        steam = a.steam + (b.steam - a.steam) * e
        lever = a.lever + (b.lever - a.lever) * e
    }
}

/** The kettle at loop time t. */
private fun loopState(t: Float, s: KettleState) {
    val heating = easeInOut(seg(t, 1.4f, 5.2f))
    val cooling = easeInOut(seg(t, 6.8f, 8.4f))
    s.temp = T_COLD + (T_TARGET - T_COLD) * heating * (1f - cooling)
    s.ringFill = (s.temp - T_COLD) / (T_TARGET - T_COLD)
    // On from the press, a flash at the click, then dim.
    val on = easeOut(seg(t, 1.3f, 1.5f))
    val click = seg(t, 5.2f, 5.8f)
    s.ringOn = if (t < 5.2f) on else 1.35f * exp(-click * 4f) * (1f - click)
    val settle = if (t < 5.2f) 1f else 1f - easeOut(seg(t, 5.2f, 6.8f))
    s.heat = if (t < 5.2f) activityAt(s.temp) else activityAt(T_TARGET) * settle
    val steamIn = easeOut(seg(t, 4.6f, 5.6f))
    s.steam = steamIn * (0.4f + 0.6f * easeOut(seg(t, 5.2f, 5.6f))) * (1f - easeInOut(seg(t, 6.8f, 8.0f)))
    // The switch: pressed at 1.3 (0.15 s), back up at the click with a little overshoot.
    val up = seg(t, 5.2f, 5.5f)
    s.lever = if (t < 5.2f) easeOut(seg(t, 1.3f, 1.45f)) else max(0f, 1f - up) - 0.12f * kotlin.math.sin(Math.PI.toFloat() * up) * (if (up < 1f) 1f else 0f)
    s.status = if (t < 1.4f) 0 else if (t < 5.2f) 1 else if (t < 6.8f) 2 else if (t < 8.4f) 3 else 4
}

/** The temperature shown: whole degrees, each clicking in over 0.35 of a degree's change. */
private fun shownTemp(temp: Float): Float {
    val whole = floor(temp)
    return whole + easeOut((temp - whole) / 0.35f)
}

/** One step of the kettle under a hand: heats while on and clicks off at 80, cools when off. */
private fun stepUser(s: KettleState, on: Boolean, dt: Float) {
    if (on) s.temp = min(T_TARGET, s.temp + (12f + 4f * (1f - (s.temp - T_COLD) / (T_TARGET - T_COLD))) * dt)
    else s.temp += -(s.temp - T_COLD) * 0.07f * dt
    s.ringFill = clamp01((s.temp - T_COLD) / (T_TARGET - T_COLD))
    val act = if (on) activityAt(s.temp) else 0f
    s.heat += (act - s.heat) * min(1f, dt * (if (on) 2.5f else 0.9f))
    val steamTo = if (s.temp > 74f) clamp01((s.temp - 74f) / 6f) * (if (on) 0.4f else 1f) else 0f
    s.steam += (steamTo - s.steam) * min(1f, dt * 1.6f)
    s.ringOn += ((if (on) 1f else 0f) - s.ringOn) * min(1f, dt * (if (on) 8f else 3.5f))
    s.lever += ((if (on) 1f else 0f) - s.lever) * min(1f, dt * 18f)
}

/** The next status index at or after `from` whose state (index mod 4) is `state`: labels only roll forward. */
private fun nextStatus(from: Float, state: Int): Float {
    val base = floor(from + 1e-3f).toInt()
    for (i in 0 until 4) if (((base + i) % 4 + 4) % 4 == state) return (base + i).toFloat()
    return base.toFloat()
}

private class KettleScreen {
    var layout = KettleLayout(0f, 0f, 1f, 0f)
    var mode = MODE_AUTO
    var clock = 0f
    var now = 0f
    var lastTouch = -10f
    var offAt = -10f
    var returnAt = 0f
    var heater = false
    var phase = 0f
    val s = KettleState()
    val from = KettleState()
    val rest = KettleState()
    var temp = T_COLD
    var statusPos = 0f
    val frame = mutableIntStateOf(0)
    val awake = mutableStateOf(true)
}

/**
 * "Steep" heating water for a sencha in a glass kettle drawn by one AGSL shader: the LED ring
 * fills with the temperature, bubbles gather and rise, the kettle clicks off at 80 °C and steams,
 * and the readout rolls digit by digit. A tap heats or cancels.
 *
 * @param autoPlay plays the 9 s heat-click-steam-cool loop.
 */
@Composable
fun KettleBoilProgress(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val st = remember { KettleScreen() }
    val insets = WindowInsets.safeDrawing
    val insetTop = insets.getTop(screen) / screen.density
    val insetBottom = insets.getBottom(screen) / screen.density
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(KETTLE_BOIL_PROGRESS_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF141C1D))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val l = kettleLayout(width, height, insetTop, insetBottom)
        st.layout = l

        LaunchedEffect(st, auto, reduced, st.awake.value) {
            if (!st.awake.value) return@LaunchedEffect
            var last = -1L
            while (true) {
                val stop = withFrameNanos { nanos ->
                    val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                    last = nanos
                    kettleFrame(st, dt, auto, reduced) {
                        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    st.frame.intValue += 1
                    !auto && st.mode == MODE_AUTO && st.statusPos == nextStatus(st.statusPos, 0)
                }
                if (stop) {
                    st.awake.value = false
                    break
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    role = Role.Button
                    contentDescription = "Glass kettle. Tap to heat to 80 degrees, tap again to cancel."
                }
                .pointerInput(st) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if (up.uptimeMillis - down.uptimeMillis > 600) return@awaitEachGesture
                        kettleTap(st)
                        st.awake.value = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                },
        ) {
            st.frame.intValue
            val s = st.s
            val dd = this.density
            scale(dd, dd, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("size", width, height)
                    shader.setFloatUniform("kettle", l.ox, l.oy, l.k, 0f)
                    shader.setFloatUniform("heat", s.heat)
                    shader.setFloatUniform("ring", s.ringFill, s.ringOn)
                    shader.setFloatUniform("steam", s.steam)
                    shader.setFloatUniform("phase", st.phase)
                    shader.setFloatUniform("lever", s.lever)
                    drawRect(brush, Offset.Zero, Size(width, height))
                } else {
                    kettleFallback(width, height, l, s, st.phase)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (insetTop + 18f).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("STEEP", style = TextStyle(color = Mint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp))
            BasicText("Sencha, 80 °C", style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp), maxLines = 1)
            BasicText("Japanese green · 250 ml · 2 min", style = TextStyle(color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }

        // The temperature and the status line, on the right under the header.
        Box(Modifier.fillMaxWidth().padding(end = 24.dp).offset(y = (insetTop + 118f).dp), contentAlignment = Alignment.TopEnd) {
            Column(Modifier.width(200.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    KettleDigit(st, 10f)
                    KettleDigit(st, 1f)
                    BasicText("°C", modifier = Modifier.padding(top = 12.dp, start = 4.dp), style = TextStyle(color = InkSoft, fontSize = 20.sp, fontWeight = FontWeight.Medium))
                }
                KettleRoller(st, Status, 20f, TextStyle(color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End))
            }
        }

        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = (insetBottom + 16f).dp), contentAlignment = Alignment.BottomCenter) {
            val shape = RoundedCornerShape(26.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .cssBoxShadow(Color(0x4D000000), 1.dp, 2.dp, 26.dp)
                    .cssBoxShadow(Color(0x66000000), 18.dp, 40.dp, 26.dp)
                    .clip(shape)
                    .background(Color(0xE6161E1E))
                    .drawBehind {
                        drawLine(Color(0x12E6F5EE), Offset(26.dp.toPx(), 1.dp.toPx()), Offset(size.width - 26.dp.toPx(), 1.dp.toPx()), 1.dp.toPx())
                    }
                    .border(1.dp, Color(0x12DCF0E6), shape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KettleChip(Icons.Filled.WaterDrop, "250 ml", Modifier.weight(1f))
                    KettleChip(Icons.Filled.Thermostat, "80 °C", Modifier.weight(1f))
                    KettleChip(Icons.Filled.Timer, "2:00", Modifier.weight(1f))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .cssBoxShadow(Color(0x40000000), 1.dp, 1.dp, 26.dp)
                        .cssBoxShadow(Color(0x3850B478), 8.dp, 20.dp, 26.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Mint)
                        .drawBehind {
                            drawLine(Color(0x73FFFFFF), Offset(26.dp.toPx(), 1.dp.toPx()), Offset(size.width - 26.dp.toPx(), 1.dp.toPx()), 1.dp.toPx())
                        }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    KettleRoller(st, Action, 22f, TextStyle(color = MintInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                }
                BasicText(
                    "Tap the kettle to heat or cancel",
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(color = InkFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

private val CenteredLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** One digit wheel: the ones turn with the value, the tens as the ones pass 9. */
@Composable
private fun KettleDigit(st: KettleScreen, place: Float) {
    Box(Modifier.size(40.dp, DIGIT_H.dp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    st.frame.intValue
                    val v = max(0f, st.temp)
                    val m = v / place
                    val base = floor(m)
                    val pos = if (place == 1f) m else base + max(0f, (m - base) * 10f - 9f)
                    translationY = -(pos % 10f) * DIGIT_H.dp.toPx()
                },
        ) {
            for (i in 0..10) {
                Box(Modifier.size(40.dp, DIGIT_H.dp), contentAlignment = Alignment.Center) {
                    BasicText(
                        "${i % 10}",
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                        style = TextStyle(
                            color = Ink,
                            fontSize = 66.sp,
                            fontWeight = FontWeight.Light,
                            fontFeatureSettings = "tnum",
                            letterSpacing = (-2).sp,
                            textAlign = TextAlign.Center,
                            lineHeight = DIGIT_H.sp,
                            lineHeightStyle = CenteredLine,
                        ),
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** Labels in a clipped box: the old one rolls up and out, the new one in from below. */
@Composable
private fun KettleRoller(st: KettleScreen, lines: List<String>, h: Float, style: TextStyle) {
    Box(Modifier.fillMaxWidth().height(h.dp).clipToBounds()) {
        for (i in -1..4) {
            BasicText(
                lines[(i + 4) % 4],
                modifier = Modifier
                    .fillMaxWidth()
                    .height(h.dp)
                    .graphicsLayer {
                        st.frame.intValue
                        val d = i - st.statusPos
                        alpha = max(0f, 1f - abs(d) * 1.2f)
                        translationY = d * h.dp.toPx()
                    },
                style = style.copy(lineHeight = h.sp, lineHeightStyle = CenteredLine),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun KettleChip(icon: ImageVector, label: String, modifier: Modifier) {
    Row(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x14A9E3BE)),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Mint, modifier = Modifier.size(15.dp))
        BasicText(label, style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

/** One frame: the loop, the kettle under a hand, or the ease back to cold water. */
private inline fun kettleFrame(st: KettleScreen, dt: Float, auto: Boolean, reduced: Boolean, clicked: () -> Unit) {
    val at = st.now + dt
    st.now = at
    val s = st.s
    var stage: Int
    when (st.mode) {
        MODE_AUTO -> {
            val t = if (auto) (st.clock + dt) % LOOP else 0f
            st.clock = t
            loopState(t, s)
            st.phase = t / LOOP
            stage = s.status % 4
        }
        MODE_USER -> {
            st.phase = (st.phase + dt / LOOP) % 1f
            stepUser(s, st.heater, dt)
            if (st.heater && s.temp >= T_TARGET - 0.01f) {
                // The kettle clicks off by itself: a flash of the ring.
                st.heater = false
                st.offAt = at
                s.ringOn = 1.35f
                clicked()
            }
            stage = if (st.heater) 1 else if (s.temp > 74f) 2 else if (s.temp > 23f) 3 else 0
            if (!st.heater && at - max(st.lastTouch, st.offAt) > IDLE) {
                st.from.copyFrom(s)
                st.returnAt = at
                st.mode = MODE_RETURN
            }
        }
        else -> {
            st.phase = (st.phase + dt / LOOP) % 1f
            val e = easeInOut((at - st.returnAt) / RETURN)
            loopState(0f, st.rest)
            s.blend(st.from, st.rest, e)
            stage = if (e < 1f) 3 else 0
            if (e >= 1f) {
                st.mode = MODE_AUTO
                st.clock = 0f
            }
        }
    }
    st.temp = if (reduced) s.temp.roundToInt().toFloat() else shownTemp(s.temp)
    // The labels roll forward to the state's next index; index 4 reads like 0, so wrap there.
    var pos = st.statusPos
    var target = nextStatus(pos, stage)
    if (pos >= 3f && target >= 4f) {
        pos -= 4f
        target -= 4f
    }
    pos = if (reduced) target else pos + (target - pos) * min(1f, dt * 9f)
    if (abs(target - pos) < 0.002f) pos = target
    st.statusPos = pos
}

/** A tap: hands the loop's frame to the hand-driven kettle, then heats or cancels. */
private fun kettleTap(st: KettleScreen) {
    if (st.mode == MODE_AUTO) {
        val t = st.clock
        st.s.reset()
        loopState(t, st.s)
        st.heater = t >= 1.4f && t < 5.2f
    } else if (st.mode == MODE_RETURN) {
        st.heater = false
    }
    st.mode = MODE_USER
    st.lastTouch = st.now
    val on = if (st.heater) false else st.s.temp < T_TARGET - 0.5f
    st.heater = on
    if (!on) st.offAt = st.now
}
