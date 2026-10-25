package com.ngocdevv.nativecook.blocks.pickers.sundialtimepicker

import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/*
 Sundial Time Picker — a calendar's time picker ("Meridian — Pick a time") that is a stone sundial
 seen from above. Drag the sun along the sky arc, or drag the shadow round the dial: the gnomon's
 shadow swings to that hour, shortens towards noon and stretches in the morning and late
 afternoon, the light warms when the sun is low. While dragging, every quarter hour ticks and the
 readout rolls; on release the time springs to the nearest quarter, carrying the drag's speed.
 Two seconds after the last touch it eases back to 10:00 (0.9 s) and the loop starts again.

 One Canvas: the AGSL pass (SundialTimeShader.kt; drawn stand-in below Android 13) plus the carved
 numerals, recorded once into a Picture. The readout and the event card are Compose; the readout
 rolls in draw lambdas. Reduced motion: 10:00, still; dragging still works.
 */

internal val SundialBackground = Color(0xFFEEE8DF)
private val Ink = Color(0xFF2C2722)
private val Mute = Color(0x942C2722)
private val Accent = Color(0xFFC77A35)
private val Numerals = listOf("VII", "VIII", "IX", "X", "XI", "XII", "I", "II", "III", "IV", "V")
private val Quarters = listOf("00", "15", "30", "45")
private const val IDLE = 2.0
private const val RETURN = 0.9
private const val MODE_AUTO = 0
private const val MODE_HAND = 1
private const val MODE_RETURN = 2
private const val K_SNAP = 195.0
private const val C_SNAP = 19.5

private class SundialState {
    var now = -1.0
    var mode = MODE_AUTO
    var autoStart = 0.0
    var lastTouch = 0.0
    var touching = false
    var tau = SundialRest
    var vel = 0.0
    var want = SundialRest
    var onDial = false
    var returnFrom = SundialRest
    var returnStart = 0.0
    var lastQuarter = -1.0
    // the frame on screen
    var shown = SundialRest
    var finger = 0.0
    var ready = false
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)

    fun settled() = mode == MODE_HAND && !touching && abs(vel) < 0.001 && abs(tau - want) < 0.0005
}

private fun SundialState.step(now: Double, auto: Boolean, autoPlay: Boolean) {
    val prev = this.now
    val dt = if (prev < 0) 0.0 else min(0.033, now - prev)
    if (prev < 0) autoStart = now
    this.now = now
    if (mode == MODE_HAND) {
        if (touching) {
            val next = tau + (want - tau) * min(1.0, dt * 30)
            if (dt > 0) vel = vel * 0.6 + 0.4 * (next - tau) / dt
            tau = next
        } else {
            vel += (-K_SNAP * (tau - want) - C_SNAP * vel) * dt
            tau += vel * dt
            if (now - lastTouch > IDLE && abs(vel) < 0.01 && autoPlay) {
                mode = MODE_RETURN
                returnStart = now
                returnFrom = tau
            }
        }
    } else if (mode == MODE_RETURN && now - returnStart >= RETURN) {
        mode = MODE_AUTO
        autoStart = now
    }
    when {
        mode == MODE_HAND -> {
            shown = tau
            finger = if (touching) 1.0 else 0.0
        }
        mode == MODE_RETURN -> {
            val k = sundialEaseInOut(min(1.0, (now - returnStart) / RETURN))
            shown = returnFrom + (SundialRest - returnFrom) * k
            finger = 0.0
        }
        !auto -> {
            shown = SundialRest
            finger = 0.0
        }
        else -> {
            val t = ((now - autoStart) % LOOP + LOOP) % LOOP
            shown = sundialTimeAt(t)
            finger = sundialFingerAt(t)
        }
    }
    ready = true
}

/** The carved numerals and the motto, recorded once in dp: a dark cut with a light lower lip. */
private fun sundialCarve(g: DialGeo): Picture {
    val pic = Picture()
    val canvas = pic.beginRecording(g.w.roundToInt().coerceAtLeast(1), g.h.roundToInt().coerceAtLeast(1))
    val face = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SERIF, 600, false)
    } else {
        Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    fun paint(color: Color, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        textAlign = Paint.Align.CENTER
        this.color = color.toArgb()
    }
    val big = (15 * g.unit).roundToInt().toFloat()
    val small = (10 * g.unit).roundToInt().toFloat()
    val dark = Color(58, 48, 38, 199)
    val lip = Color(255, 252, 244, 140)
    val bigDark = paint(dark, big)
    val bigLip = paint(lip, big)
    val smallDark = paint(dark, small)
    val smallLip = paint(lip, small)
    fun put(text: String, d: Paint, l: Paint, x: Double, y: Double) {
        canvas.drawText(text, (x + 0.6).toFloat(), (y + 0.8).toFloat(), l)
        canvas.drawText(text, x.toFloat(), y.toFloat(), d)
    }
    val r = g.r * 0.87
    for (i in Numerals.indices) {
        val h = (i + 7 - 12) * 15 * PI / 180
        val a = atan(SundialSinLat * tan(h))
        put(Numerals[i], bigDark, bigLip, g.cx + sin(a) * r, g.cy - cos(a) * r + 5 * g.unit)
    }
    put("MERIDIAN", smallDark, smallLip, g.cx, g.cy + g.r * 0.5)
    put("LAT 40 N  -  EST 1894", smallDark, smallLip, g.cx, g.cy + g.r * 0.62)
    pic.endRecording()
    return pic
}

/**
 * A time picker that is a stone sundial. Drag the sun or the shadow to choose a time.
 *
 * @param autoPlay drags the sun from 10:00 to 15:30 and back, on a 9 s loop.
 */
@Composable
fun SundialTimePicker(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val state = remember(reduced) { SundialState() }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(SUNDIAL_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val fallback = remember { SundialFallback() }
    val sunScratch = remember { DoubleArray(3) }

    BoxWithConstraints(modifier.fillMaxSize().background(SundialBackground)) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val g = remember(w, h, safeTop) { DialGeo(w, h, safeTop.toDouble()) }
        val numerals = remember(g) { sundialCarve(g) }

        LaunchedEffect(state, auto) {
            while (true) {
                withFrameNanos { n ->
                    state.step(n / 1e9, auto, autoPlay)
                    if (state.mode == MODE_HAND && state.touching) {
                        val q = sundialQuarterOf(state.shown)
                        if (state.lastQuarter >= 0 && q != state.lastQuarter) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        state.lastQuarter = q
                    } else {
                        state.lastQuarter = -1.0
                    }
                    state.tick.intValue += 1
                }
                if ((state.mode == MODE_AUTO && !auto) || (!autoPlay && state.settled())) {
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Sundial time picker. Drag the sun or the shadow to choose a time." }
                .pointerInput(state, g) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val x = (down.position.x / d).toDouble()
                        val y = (down.position.y / d).toDouble()
                        val onDial = hypot(x - g.cx, y - g.cy) < g.r
                        state.onDial = onDial
                        state.tau = state.shown
                        state.vel = 0.0
                        state.want = sundialRubberTime(sundialTimeFromFinger(g, x, y, onDial))
                        state.mode = MODE_HAND
                        state.touching = true
                        state.lastTouch = state.now
                        state.wake.intValue += 1
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                c.consume()
                                state.want = sundialRubberTime(
                                    sundialTimeFromFinger(g, (c.position.x / d).toDouble(), (c.position.y / d).toDouble(), state.onDial),
                                )
                                state.lastTouch = state.now
                            }
                        } finally {
                            // the release aims at the quarter the drag's momentum carries it to
                            if (state.touching) state.want = sundialQuarterOf(state.tau + state.vel * 0.12)
                            state.touching = false
                            state.lastTouch = state.now
                        }
                    }
                },
        ) {
            state.tick.intValue
            if (!state.ready) return@Canvas
            val d = density
            val tau = state.shown
            sundialSunDir(tau, sunScratch)
            val sx = sundialSunX(g, tau).toFloat()
            val sy = sundialSunY(g, tau).toFloat()
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("size", w.toFloat(), h.toFloat())
                    shader.setFloatUniform("dial", g.cx.toFloat(), g.cy.toFloat(), g.r.toFloat(), g.unit.toFloat())
                    shader.setFloatUniform("sun", sunScratch[0].toFloat(), sunScratch[1].toFloat(), sunScratch[2].toFloat(), tau.toFloat())
                    shader.setFloatUniform("handle", sx, sy, g.gnomon.toFloat(), 0f)
                    shader.setFloatUniform("finger", sx, sy, state.finger.toFloat(), 0f)
                    drawRect(brush, Offset.Zero, Size(w.toFloat(), h.toFloat()))
                } else {
                    with(fallback) { draw(g, sunScratch, sx, sy, state.finger.toFloat()) }
                }
                drawIntoCanvas { it.nativeCanvas.drawPicture(numerals) }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 16).dp),
        ) {
            BasicText("MERIDIAN · NEW EVENT", style = TextStyle(color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
            BasicText(
                "Pick a time",
                modifier = Modifier.padding(top = 8.dp),
                style = TextStyle(color = Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
            )
            BasicText(
                "Tea with Ines · Thursday, 24 October",
                modifier = Modifier.padding(top = 4.dp),
                style = TextStyle(color = Mute, fontSize = 15.sp),
            )
        }

        // the readout under the dial
        val timeStyle = TextStyle(color = Ink, fontSize = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, fontFeatureSettings = "tnum")
        val metaStyle = TextStyle(color = Mute, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontFeatureSettings = "tnum")
        fun shownQ() = sundialQuarterOf(state.shown)
        fun quarterIndex(v: Double): Int = ((v - floor(v + 1e-6)) * 4).roundToInt() % 4
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = (g.cy + g.r + 22 * g.unit).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SundialTimeRollNumber(
                    value = { state.tick.intValue; floor(shownQ() + 1e-6).toFloat() },
                    digits = 2, line = 60f, style = timeStyle, pad = true, still = reduced,
                )
                BasicText(":", modifier = Modifier.padding(horizontal = 2.dp).padding(bottom = 8.dp), style = timeStyle)
                SundialTimeRollLabel(
                    labels = Quarters,
                    index = { state.tick.intValue; quarterIndex(shownQ()) },
                    line = 60f, style = timeStyle, still = reduced,
                )
            }
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("SUN ", style = metaStyle)
                SundialTimeRollNumber(
                    value = {
                        state.tick.intValue
                        sundialSunDir(state.shown, sunScratch)
                        (asin(max(0.0, sunScratch[2])) * 180 / PI).toFloat()
                    },
                    digits = 2, line = 16f, style = metaStyle, suffix = "° HIGH  ·  UNTIL ", still = reduced,
                )
                SundialTimeRollNumber(
                    value = { state.tick.intValue; floor(shownQ() + 0.75 + 1e-6).toFloat() },
                    digits = 2, line = 16f, style = metaStyle, pad = true, still = reduced,
                )
                BasicText(":", style = metaStyle)
                SundialTimeRollLabel(
                    labels = Quarters,
                    index = { state.tick.intValue; quarterIndex(shownQ() + 0.75) },
                    line = 16f, style = metaStyle, still = reduced,
                )
            }
        }

        val hair = (1f / screen.density).dp
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = (safeBottom + 16).dp)
                .fillMaxWidth()
                .height(72.dp)
                .cssBoxShadow(Color(0x1F46321E), 1.dp, 3.dp, 22.dp)
                .cssBoxShadow(Color(0x1F46321E), 14.dp, 30.dp, 22.dp)
                .background(Color(0xFFFBF8F3), RoundedCornerShape(22.dp))
                .border(hair, Color(0x1A2C2722), RoundedCornerShape(22.dp))
                .drawBehind {
                    val r = 22.dp.toPx()
                    drawLine(Color(0xE6FFFFFF), Offset(r, 1.dp.toPx()), Offset(size.width - r, 1.dp.toPx()), 1.dp.toPx())
                }
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp, 36.dp).background(Accent, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText("Tea with Ines", style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                BasicText(
                    "Garden room · 45 min · Reminder 10 min before",
                    modifier = Modifier.padding(top = 2.dp),
                    style = TextStyle(color = Mute, fontSize = 12.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .height(44.dp)
                    .cssBoxShadow(Color(0x33000000), 1.dp, 2.dp, 22.dp)
                    .background(Color(0xFF2C2722), RoundedCornerShape(22.dp))
                    .drawBehind {
                        val r = 22.dp.toPx()
                        drawLine(Color(0x1FFFFFFF), Offset(r, 1.dp.toPx()), Offset(size.width - r, 1.dp.toPx()), 1.dp.toPx())
                    }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Confirm", style = TextStyle(color = Color(0xFFFBF5EC), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}
