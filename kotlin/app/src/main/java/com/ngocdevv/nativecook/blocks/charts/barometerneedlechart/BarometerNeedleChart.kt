package com.ngocdevv.nativecook.blocks.charts.barometerneedlechart

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.charts.barometerneedlechart.BarometerMotion.NOW_HPA
import com.ngocdevv.nativecook.blocks.charts.barometerneedlechart.BarometerMotion.SPAN
import com.ngocdevv.nativecook.blocks.charts.barometerneedlechart.BarometerMotion.angleOf
import com.ngocdevv.nativecook.blocks.charts.barometerneedlechart.BarometerMotion.clamp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Barometer Needle Chart — a sailing weather screen ("Saltmarsh Harbour") whose hero is a brass
 aneroid barometer over a 48-hour pressure chart. Scrubbing the chart swings the blued needle on a
 spring to the reading at that hour; the brass set hand stays at now (1012 hPa), the readout rolls
 like an odometer and the forecast word and trend roll as the needle crosses their bands. Tap the
 glass and the needle twitches, as people tap real barometers.

 One Canvas: a baked bitmap of everything static (background, dial, chart card), the needle, a
 baked bitmap of the glass (reflections, set hand, knob), then the readout and chart cursor. The
 frame loop only moves numbers; the drawing reads them, so nothing recomposes per frame.

 Touch: drag on the chart to scrub (rubber-band past ±24 h); a tap on the dial kicks the needle
 (22°/s). 2 s after the last touch the cursor eases back to now (0.9 s) and the loop restarts.
 Reduced motion: no loop, no spring or jitter; the needle points straight at the reading.
 */

/** Seconds after the last touch before the screen eases back to rest, and how long that takes. */
private const val IDLE = 2.0
private const val RETURN = 0.9
/** Velocity kick of a tap on the glass, degrees per second. */
private const val TAP_KICK = 22.0

internal val BarometerBackground = Color(0xFF0F1C24)

private class BarometerImages(val base: Bitmap, val glass: Bitmap)

/** Everything the frame loop and the gesture share. mode: 0 loop / rest, 1 a finger, 2 easing back. */
private class BarometerState {
    var mode = 0
    var autoStart = -1.0
    var now = -1.0
    var lastTouch = -1.0
    var touching = false
    var scrubbing = false
    var userHour = 0.0
    val needle = doubleArrayOf(angleOf(BarometerMotion.pressureAt(0.0)), 0.0)
    var returnHour = 0.0
    var returnAt = 0.0
    var downX = 0f
    var downY = 0f
    var hour = 0.0
    var angle = angleOf(BarometerMotion.pressureAt(0.0))
    var word = -1
    /** Bumped once per frame; only the Canvas reads it. */
    val frame = mutableIntStateOf(0)
}

/**
 * A brass aneroid barometer over a 48-hour pressure chart: drag on the chart to swing the needle to
 * that hour's reading, tap the dial to tap the glass.
 *
 * @param autoPlay plays the 9 s scrub through the storm and the forecast on a loop.
 */
@Composable
fun BarometerNeedleChart(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val state = remember { BarometerState() }
    val table = remember { BarometerMotion.buildNeedleTable() }
    val kit = remember { BarometerKit() }
    val fonts = remember { BarometerFonts() }
    var wake by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(BarometerBackground)
            .semantics {
                contentDescription =
                    "Barometer at 1012 hectopascals, change. Drag the chart to see the pressure at another hour; tap the dial to tap the glass."
            },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val geo = remember(w, h, safeTop, safeBottom) { BarometerGeo(w, h, safeTop, safeBottom) }
        val needlePath = remember(geo) { barometerNeedlePath(geo) }
        var images by remember { mutableStateOf<BarometerImages?>(null) }
        val scale = min(screen.density, 2f)

        // Static art, baked once per size off the main thread, with its own fonts and paints. The demo
        // hides the status bar, so the insets animate for a moment: the bake waits for them to settle
        // (a cancellable delay) instead of restarting on every inset frame.
        LaunchedEffect(geo, scale) {
            delay(120)
            images = withContext(Dispatchers.Default) {
                val k = BarometerKit()
                val ft = BarometerFonts()
                val base = barometerBake(geo.w, geo.h, scale) { barometerDrawBase(it, k, geo, ft) }
                k.begin()
                val glass = barometerBake(geo.rb * 2, geo.rb * 2, scale) { barometerDrawGlass(it, k, geo, angleOf(NOW_HPA)) }
                BarometerImages(base, glass)
            }
        }

        val ready = images != null
        LaunchedEffect(geo, auto, reduced, ready, wake) {
            if (!ready) return@LaunchedEffect
            val s = state
            s.now = -1.0
            if (auto && s.mode == 0) s.autoStart = -1.0
            while (true) {
                val keepGoing = withFrameNanos { nanos -> barometerStep(s, nanos / 1e9, auto, reduced, table, geo, view) }
                s.frame.intValue += 1
                if (!keepGoing) break
            }
        }

        val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
        val baseRect = remember(geo) { RectF(0f, 0f, geo.w, geo.h) }
        val glassRect = remember(geo) { RectF(geo.cx - geo.rb, geo.cy - geo.rb, geo.cx + geo.rb, geo.cy + geo.rb) }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(geo) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = screen.density
                        val s = state
                        // A finger takes over from the loop.
                        s.mode = 1
                        s.autoStart = -1.0
                        s.touching = true
                        s.lastTouch = s.now
                        s.downX = down.position.x / d
                        s.downY = down.position.y / d
                        val inCard = s.downY >= geo.card.y && s.downY <= geo.card.y + geo.card.h
                        s.scrubbing = inCard
                        if (inCard) s.userHour = barometerHourFromX(geo, s.downX)
                        wake++
                        var lastX = s.downX
                        var lastY = s.downY
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastX = change.position.x / d
                            lastY = change.position.y / d
                            s.lastTouch = s.now
                            if (s.scrubbing) s.userHour = barometerHourFromX(geo, lastX)
                            if (!change.pressed) break
                        }
                        s.touching = false
                        s.lastTouch = s.now
                        if (!s.scrubbing) {
                            val moved = hypot(lastX - s.downX, lastY - s.downY)
                            val onDial = hypot(s.downX - geo.cx, s.downY - geo.cy) < geo.rb
                            if (moved < 12 && onDial) {
                                s.needle[1] += if (s.needle[0] > angleOf(NOW_HPA)) -TAP_KICK else TAP_KICK
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        }
                        s.scrubbing = false
                    }
                },
        ) {
            state.frame.intValue
            val img = images ?: return@Canvas
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val d = density
                nc.save()
                nc.scale(d, d)
                kit.begin()
                nc.drawBitmap(img.base, null, baseRect, bitmapPaint)
                barometerDrawNeedle(nc, kit, geo, needlePath, state.angle)
                nc.drawBitmap(img.glass, null, glassRect, bitmapPaint)
                barometerDrawOverlay(nc, kit, geo, fonts, state.hour, BarometerMotion.hpaOf(state.angle))
                nc.restore()
            }
        }

        if (ready) {
            BarometerStats(Modifier.offset(y = geo.statsTop.dp))
        }
    }
}

/** The chart hour under x, rubber-banded past either end. */
private fun barometerHourFromX(g: BarometerGeo, x: Float): Double {
    val h = g.hourAtX(x).toDouble()
    if (h > SPAN) return SPAN + (h - SPAN) / (1 + (h - SPAN) / 6)
    if (h < -SPAN) return -SPAN - (-SPAN - h) / (1 + (-SPAN - h) / 6)
    return h
}

/** One frame of the clock, the touch modes and the needle spring. Returns whether to keep running. */
private fun barometerStep(
    s: BarometerState,
    t1: Double,
    auto: Boolean,
    reduced: Boolean,
    table: DoubleArray,
    g: BarometerGeo,
    view: android.view.View,
): Boolean {
    val first = s.now < 0
    val dt = if (first) 0.0 else min(t1 - s.now, 0.05)
    s.now = t1
    if (s.lastTouch < 0 && s.mode == 1) s.lastTouch = t1
    if (first && auto && s.mode == 0) s.autoStart = t1
    var hour = s.userHour
    val angle: Double
    if (s.mode == 0 && s.autoStart >= 0) {
        val t = (t1 - s.autoStart) % LOOP
        hour = BarometerMotion.cursorAt(t)
        angle = BarometerMotion.sampleTable(table, t) + BarometerMotion.jitterAt(t)
        val ahead = BarometerMotion.sampleTable(table, t + 1.0 / 120)
        s.needle[0] = angle
        s.needle[1] = (ahead - BarometerMotion.sampleTable(table, t)) * 120
        s.userHour = hour
    } else {
        if (s.mode == 2) {
            val k = BarometerMotion.easeInOut(BarometerMotion.seg(t1, s.returnAt, s.returnAt + RETURN))
            hour = BarometerMotion.mix(s.returnHour, 0.0, k)
            s.userHour = hour
            if (t1 > s.returnAt + RETURN + 0.5) {
                s.mode = 0
                s.autoStart = t1
            }
        } else if (s.mode == 1 && !s.touching) {
            // Let go past an end: the cursor springs back inside.
            val inside = clamp(hour, -SPAN, SPAN)
            if (inside != hour) {
                hour += (inside - hour) * (1 - exp(-dt * 14))
                if (abs(inside - hour) < 1e-3) hour = inside
                s.userHour = hour
            }
            if (auto && t1 - s.lastTouch > IDLE) {
                s.mode = 2
                s.returnHour = hour
                s.returnAt = t1
            }
        }
        val target = angleOf(BarometerMotion.pressureAt(clamp(hour, -SPAN, SPAN)))
        if (reduced) {
            s.needle[0] = target
            s.needle[1] = 0.0
        } else {
            BarometerMotion.stepNeedle(s.needle, target, dt)
        }
        angle = s.needle[0]
    }
    s.hour = hour
    s.angle = angle
    // A selection tick each time a finger drags the needle into another forecast word.
    val word = BarometerMotion.wordIndex(BarometerMotion.hpaOf(angle)).roundToInt()
    if (s.word >= 0 && word != s.word && s.mode == 1) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    s.word = word
    if (auto) return true
    // Without the loop the frames stop once the finger is gone and the needle has settled.
    val target = angleOf(BarometerMotion.pressureAt(clamp(s.userHour, -SPAN, SPAN)))
    val settled = !s.touching && abs(s.needle[1]) < 0.01 && abs(s.needle[0] - target) < 0.005 && abs(clamp(s.userHour, -SPAN, SPAN) - s.userHour) < 1e-3
    return !settled
}

private val StatLabel = Color(0x80E9E0CC)
private val StatValue = Color(0xFFF1EAD9)
private val StatFill = Color(0x0FF1EAD9)
private val StatBorder = Color(0x1FF1EAD9)

/** Wind, swell and tide under the chart. */
@Composable
private fun BarometerStats(modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((label, value) in listOf("Wind" to "SW 14 kn", "Swell" to "1.2 m · 9 s", "High tide" to "16:48")) {
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(StatFill, shape)
                    .border(Dp.Hairline, StatBorder, shape)
                    .padding(horizontal = 12.dp),
            ) {
                Column(Modifier.align(androidx.compose.ui.Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(label, style = TextStyle(color = StatLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                    BasicText(
                        value,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        style = TextStyle(color = StatValue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
    }
}
