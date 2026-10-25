package com.ngocdevv.nativecook.blocks.charts.tidegaugechart

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Tide Gauge Chart — "Harbor Point · Tides": a 24-hour tide chart whose area is real water.
 The loop and its phases are in TideGaugeChartMotion.kt (LOOP = 9 s).

 Touch: the scrubber follows the finger along the day (a fast critically damped follow, ω 28),
 the buoy rides the surface, and the speed of the scrub drives the slosh oscillator (ω 9 rad/s,
 damping ratio 0.3): the surface leans against the move and rings when the finger stops. Past
 either end of the day the scrubber rubber-bands and springs back on release. A tick when the
 scrubber passes a high or low tide. 2 s after the last touch the scrubber eases back to now in
 0.8 s and the loop restarts. Reduced motion: no loop, no ripples, no slosh; the finger still scrubs.
*/

private const val PAD = 20f
private const val DIGIT_H = 60f
private val Ink = Color(0xFFE6F0F3)
private val InkSoft = Color(200 / 255f, 222 / 255f, 230 / 255f, 0.62f)
private val Cyan = Color(0xFF7FC3D6)
private val Good = Color(0xFF7FE0B4)
internal val TideBackground = Color(0xFF081822)

/** Everything the frame loop and the finger share; [frame] is the state the drawing reads. */
private class TideState {
    var clock = 0f
    var hour = TIDE_NOW
    var tilt = 0f
    var chop = 0f
    var glow = 0f
    var phase = 0f
    var mode = 0 // 0 loop, 1 finger down, 2 released (waiting), 3 easing back to rest
    var fingerHour = TIDE_NOW
    var hourV = 0f
    var tiltV = 0f
    var idle = 0f
    var fromHour = TIDE_NOW
    var ret = 0f
    val slosh = FloatArray(2)
    val frame = mutableFloatStateOf(0f)
    val wake = mutableIntStateOf(0)
}

private fun step(s: TideState, dt: Float, auto: Boolean, reduced: Boolean, plot: TidePlot, tick: () -> Unit) {
    val m = s.mode
    if (m == 0) {
        if (!auto) {
            s.hour = TIDE_NOW
            s.tilt = 0f
            s.chop = 0f
            s.glow = 0f
            if (!reduced) s.phase = (s.phase + dt / LOOP) % 1f
            return
        }
        val t = (s.clock + dt) % LOOP
        s.clock = t
        tideScriptSlosh(t, plot.w, s.slosh)
        s.hour = tideScriptHour(t)
        s.tilt = s.slosh[0]
        s.chop = s.slosh[1]
        s.glow = tideScriptGlow(t)
        s.phase = t / LOOP
        return
    }
    // Ripples keep travelling while the finger is in charge (not under reduced motion).
    if (!reduced) s.phase = (s.phase + dt / LOOP) % 1f
    val before = s.hour
    if (m == 3) {
        val r = min(1f, s.ret + dt / TIDE_RETURN_FOR)
        s.ret = r
        s.hour = s.fromHour + (TIDE_NOW - s.fromHour) * tEaseInOut(r)
        if (r >= 1f) {
            s.mode = 0
            s.clock = 0f
            s.phase = 0f
        }
    } else {
        // The scrubber follows the finger (or, released, springs back inside the day).
        val target = if (m == 1) s.fingerHour else tClamp(s.hour, 0f, TIDE_DAY)
        val w = 28f
        val a = -w * w * (s.hour - target) - 2 * w * s.hourV
        s.hourV += a * dt
        s.hour += s.hourV * dt
        if (m == 2) {
            s.idle += dt
            if (s.idle > TIDE_IDLE_BEFORE_RETURN) {
                s.mode = 3
                s.ret = 0f
                s.fromHour = s.hour
            }
        }
    }
    val h = s.hour
    val speed = (h - before) / max(dt, 1e-3f) * (plot.w / TIDE_DAY)
    // Slosh: the oscillator, driven by the scrub speed (critically damped under reduced motion).
    val zeta = if (reduced) 1f else SLOSH_ZETA
    val drive = if (reduced) 0f else tClamp(-speed * SLOSH_GAIN, -SLOSH_MAX, SLOSH_MAX)
    val acc = -SLOSH_OMEGA * SLOSH_OMEGA * (s.tilt - drive) - 2 * zeta * SLOSH_OMEGA * s.tiltV
    s.tiltV += acc * dt
    s.tilt += s.tiltV * dt
    // While easing back to rest the slosh is let go, so the loop takes over from a calm surface.
    if (m == 3) s.tilt *= 1 - min(1f, s.ret) * 0.25f
    s.chop = if (reduced) 0f else s.chop + (tClamp(abs(speed) / 700, 0f, 1f) - s.chop) * min(1f, dt * 6)
    s.glow *= exp(-dt * 4)
    // A tick when the scrubber passes a high or low tide (touch only; the loop is silent).
    if (m != 3) {
        for (e in TIDE_EXTREMES) if ((before - e) * (h - e) < 0f) tick()
    }
}

/**
 * A 24-hour tide chart for a harbour whose area under the curve is real water: scrub the day and
 * the buoy rides the surface while the water sloshes against the move.
 *
 * @param autoPlay plays the 9 s scrub demo: back to the morning, across the high tide, and home to now.
 */
@Composable
fun TideGaugeChart(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val state = remember { TideState() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        // Layout, computed rather than measured so the canvas and the views agree exactly.
        val top = safeTop + 16
        val cardX = PAD - 4
        val cardY = top + 164
        val cardW = width - cardX * 2
        val cardH = tClamp(height - cardY - 214 - safeBottom, 220f, 340f)
        val plot = remember(cardW, cardH) { TidePlot(14f, 40f, cardW - 28, cardH - 40 - 48) }
        val tilesY = cardY + cardH + 16

        val buoy = remember(screen.density) { buildTideBuoy(min(3f, screen.density)) }
        val shader = remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(TIDE_AGSL) }.getOrNull() else null
        }
        val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

        LaunchedEffect(auto) {
            state.clock = 0f
            state.mode = 0
            state.hour = TIDE_NOW
        }

        val wake = state.wake.intValue
        LaunchedEffect(auto, reduced, wake, plot) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 1f / 60 else min((now - last) / 1e9f, 1f / 20)
                    last = now
                    step(state, dt, auto, reduced, plot) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                    state.frame.floatValue += dt
                }
                // Under reduced motion nothing moves at rest, so the loop sleeps until a touch.
                if (reduced && state.mode == 0) break
            }
        }

        TideBackdrop(Modifier.fillMaxSize())

        // The card and the plot's static backdrop: drawn once, in their own layer.
        Box(
            Modifier
                .offset(x = cardX.dp, y = cardY.dp)
                .size(cardW.dp, cardH.dp)
                .drawWithCache {
                    onDrawBehind {
                        scale(density, density, pivot = Offset.Zero) {
                            drawIntoCanvas { drawTideCard(it.nativeCanvas, cardW, cardH, plot) }
                        }
                    }
                },
        )

        // The water, the scrubber and the buoy: the only layer redrawn every frame.
        val water = remember { WaterPainter(buoy) }
        Canvas(
            Modifier
                .offset(x = cardX.dp, y = cardY.dp)
                .size(cardW.dp, cardH.dp)
                .graphicsLayer(),
        ) {
            state.frame.floatValue
            scale(density, density, pivot = Offset.Zero) {
                val scrubX = hourToX(state.hour, plot)
                val ph = state.phase
                val tilt = state.tilt
                val chop = state.chop
                val surf = surfaceY(scrubX, plot, ph, scrubX, tilt, chop)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("plot", plot.x, plot.y, plot.w, plot.h)
                    shader.setFloatUniform("scrub", scrubX, tilt, chop, state.glow)
                    shader.setFloatUniform("phase", ph)
                    drawRoundRect(brush, Offset.Zero, Size(cardW, cardH), CornerRadius(TIDE_CARD_RADIUS))
                } else {
                    with(water) { fallbackWater(plot, ph, scrubX, tilt, chop, state.glow) }
                }
                drawLine(
                    Color(230 / 255f, 240 / 255f, 243 / 255f, 0.55f),
                    Offset(scrubX, plot.y - 4),
                    Offset(scrubX, surf - 30),
                    strokeWidth = 1f,
                    pathEffect = water.dash,
                )
                drawCircle(Ink, 3.5f, Offset(scrubX, plot.y - 4))
                // The buoy rides the surface, leaning with its slope and the slosh.
                val left = surfaceY(scrubX - 9, plot, ph, scrubX, tilt, chop)
                val right = surfaceY(scrubX + 9, plot, ph, scrubX, tilt, chop)
                val bob = if (reduced) 0f else 1.4f * sin(2 * PI.toFloat() * 3 * ph)
                val lean = atan2(right - left, 18f) * 0.8f + tilt * 0.012f
                val s2 = sin(2 * PI.toFloat() * 2 * ph)
                val lamp = 0.35f + 0.25f * s2 * s2 + state.glow * 0.4f
                drawIntoCanvas { water.drawBuoy(it.nativeCanvas, scrubX, surf + 2 + bob, lean, lamp) }
            }
        }

        Column(
            Modifier.offset(x = PAD.dp, y = top.dp).width((width - PAD * 2).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("TIDES · STATION 4127", style = TextStyle(color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
            BasicText("Harbor Point", style = TextStyle(color = Ink, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp))
            BasicText("Sat 25 Oct · Spring tide · Swell 1.2 m", style = TextStyle(color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        // The reading: odometer digits on the left, the trend and time on the right.
        val level by remember { derivedStateOf { state.frame.floatValue; tide(tClamp(state.hour, 0f, TIDE_DAY)) } }
        val status by remember {
            derivedStateOf {
                state.frame.floatValue
                val h = tClamp(state.hour, 0f, TIDE_DAY)
                "${if (tideTrend(h) > 0) "Rising" else "Falling"} · ${clockText(h)}"
            }
        }
        Row(
            Modifier.offset(x = PAD.dp, y = (top + 96).dp).width((width - PAD * 2).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box {
                    Box(
                        Modifier
                            .matchParentSize()
                            .layout { measurable, constraints ->
                                // left/right −6, top 10, bottom 6, around the digits.
                                val w = constraints.maxWidth + 12.dp.roundToPx()
                                val h = max(0, constraints.maxHeight - 16.dp.roundToPx())
                                val p = measurable.measure(Constraints.fixed(w, h))
                                layout(constraints.maxWidth, constraints.maxHeight) { p.place(-6.dp.roundToPx(), 10.dp.roundToPx()) }
                            }
                            .graphicsLayer {
                                state.frame.floatValue
                                alpha = state.glow
                            }
                            .cssBoxShadow(Color(143 / 255f, 214 / 255f, 232 / 255f, 0.55f), 0.dp, 28.dp, 30.dp)
                            .background(Color(143 / 255f, 214 / 255f, 232 / 255f, 0.10f), RoundedCornerShape(30.dp)),
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        TideDigit({ level }, 0, reduced)
                        BasicText(".", style = DigitStyle, modifier = Modifier.height(DIGIT_H.dp))
                        TideDigit({ level }, 1, reduced)
                        TideDigit({ level }, 2, reduced)
                    }
                }
                BasicText(
                    "m",
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                    style = TextStyle(color = InkSoft, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            Column(Modifier.padding(bottom = 8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    status,
                    modifier = Modifier.widthIn(min = 140.dp),
                    style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", textAlign = TextAlign.End),
                )
                BasicText("Water 17 °C · Wind 9 kn SW", style = TextStyle(color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
        }

        // The finger scrubs the day anywhere on the card.
        Box(
            Modifier
                .offset(x = cardX.dp, y = cardY.dp)
                .size(cardW.dp, cardH.dp)
                .semantics { contentDescription = "Tide chart for today. Drag to scrub through the day." }
                .pointerInput(state, plot) {
                    fun hourAt(px: Float): Float {
                        val raw = (px / density - plot.x) / plot.w * TIDE_DAY
                        // Rubber band past either end of the day.
                        return when {
                            raw < 0f -> raw / (1 - raw / 3)
                            raw > TIDE_DAY -> TIDE_DAY + (raw - TIDE_DAY) / (1 + (raw - TIDE_DAY) / 3)
                            else -> raw
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (state.mode == 0) {
                            state.hourV = 0f
                            state.tiltV = 0f
                        }
                        state.mode = 1
                        state.fingerHour = hourAt(down.position.x)
                        state.wake.intValue += 1
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                state.fingerHour = hourAt(change.position.x)
                                change.consume()
                            }
                        } finally {
                            state.mode = 2
                            state.idle = 0f
                        }
                    }
                },
        )

        Row(
            Modifier.offset(x = (PAD - 4).dp, y = tilesY.dp).width((width - (PAD - 4) * 2).dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TideTile(Modifier.weight(1f), "HIGH TIDE", "2.4 m at 14:12", "Next 02:26 · 2.3 m")
            TideTile(Modifier.weight(1f), "LOW TIDE", "0.3 m at 08:05", "Next 20:19 · 0.4 m")
        }

        Row(
            Modifier
                .offset(x = (PAD - 4).dp, y = (tilesY + 96).dp)
                .width((width - (PAD - 4) * 2).dp)
                .height(48.dp)
                .background(Color(18 / 255f, 48 / 255f, 66 / 255f, 0.5f), RoundedCornerShape(24.dp))
                .border(0.5.dp, Color(190 / 255f, 230 / 255f, 245 / 255f, 0.12f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(8.dp).background(Good, RoundedCornerShape(4.dp)))
            BasicText(
                "Surf window 12:30 – 15:40",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            )
            BasicText("Good", style = TextStyle(color = Good, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }

        BasicText(
            "Predicted levels above chart datum · Not for navigation",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = PAD.dp, end = PAD.dp, bottom = (safeBottom + 14).dp),
            style = TextStyle(color = Color(200 / 255f, 222 / 255f, 230 / 255f, 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}

private val DigitStyle = TextStyle(
    color = Ink,
    fontSize = 52.sp,
    lineHeight = DIGIT_H.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
    letterSpacing = (-1).sp,
)

/**
 * One odometer column: 0–9 and a second 0 so the wrap from 9 rolls on. The wheel springs to the
 * column's whole count (critically damped, about 0.26 s), so it only shows a partial digit while
 * the value changes and always comes to rest square on a digit.
 */
@Composable
private fun TideDigit(value: () -> Float, place: Int, reduced: Boolean) {
    val div = when (place) { 2 -> 1; 1 -> 10; else -> 100 }
    val count by remember { derivedStateOf { floor((max(0f, value()) * 100).roundToInt().toFloat() / div).toInt() } }
    val wheel = remember { Animatable(count.toFloat()) }
    LaunchedEffect(count) {
        if (reduced) wheel.snapTo(count.toFloat()) else wheel.animateTo(count.toFloat(), spring(dampingRatio = 1f, stiffness = 600f))
    }
    Box(Modifier.height(DIGIT_H.dp).clipToBounds()) {
        // The strip is eleven digits tall: measure it unbounded (top-aligned) so the 60 dp window
        // does not squeeze every digit after the first to zero height.
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    val pos = ((wheel.value % 10) + 10) % 10
                    translationY = -pos * DIGIT_H.dp.toPx()
                },
        ) {
            for (d in intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0)) {
                BasicText("$d", style = DigitStyle, modifier = Modifier.height(DIGIT_H.dp))
            }
        }
    }
}

@Composable
private fun TideTile(modifier: Modifier, label: String, value: String, sub: String) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .height(84.dp)
            .cssBoxShadow(Color(0f, 8 / 255f, 14 / 255f, 0.35f), 1.dp, 2.dp, 18.dp)
            .cssBoxShadow(Color(0f, 8 / 255f, 14 / 255f, 0.25f), 10.dp, 24.dp, 18.dp)
            .background(Color(18 / 255f, 48 / 255f, 66 / 255f, 0.72f), shape)
            .border(0.5.dp, Color(190 / 255f, 230 / 255f, 245 / 255f, 0.14f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BasicText(label, style = TextStyle(color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
        BasicText(value, style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
        BasicText(sub, style = TextStyle(color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
    }
}

/** The buoy, its lamp glow and, below Android 13, the drawn water. */
private class WaterPainter(private val buoy: android.graphics.Bitmap) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
    private val buoyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val buoyRect = RectF(TIDE_BUOY_LEFT, TIDE_BUOY_TOP, TIDE_BUOY_LEFT + TIDE_BUOY_W, TIDE_BUOY_TOP + TIDE_BUOY_H)
    private val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    private val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (blurOk) maskFilter = BlurMaskFilter(sigmaToRadius(6f), BlurMaskFilter.Blur.NORMAL)
    }
    private val surface = Path()
    private val foam = Path()

    fun drawBuoy(c: android.graphics.Canvas, x: Float, y: Float, lean: Float, lamp: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(lean * 180f / PI.toFloat())
        val a = (tClamp(lamp, 0f, 1f) * 255).roundToInt()
        if (blurOk) {
            lampPaint.shader = null
            lampPaint.color = android.graphics.Color.argb(a, 0xFF, 0xC9, 0x67)
            c.drawCircle(0f, -30.5f, 9f, lampPaint)
        } else {
            lampPaint.color = android.graphics.Color.BLACK
            lampPaint.shader = android.graphics.RadialGradient(
                0f, -30.5f, 18f,
                intArrayOf(android.graphics.Color.argb(a, 0xFF, 0xC9, 0x67), android.graphics.Color.argb(0, 0xFF, 0xC9, 0x67)),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
            c.drawCircle(0f, -30.5f, 18f, lampPaint)
        }
        c.drawBitmap(buoy, null, buoyRect, buoyPaint)
        c.restore()
    }

    /** Below Android 13: the water as a filled surface with a depth gradient, a foam line and the scrubber's lighter column. */
    fun androidx.compose.ui.graphics.drawscope.DrawScope.fallbackWater(plot: TidePlot, phase: Float, scrubX: Float, tilt: Float, chop: Float, glow: Float) {
        surface.reset()
        foam.reset()
        val bottom = plot.y + plot.h
        var top = bottom
        var x = plot.x
        while (true) {
            val y = surfaceY(x, plot, phase, scrubX, tilt, chop)
            top = min(top, y)
            if (x == plot.x) {
                surface.moveTo(x, y)
                foam.moveTo(x, y + 1)
            } else {
                surface.lineTo(x, y)
                foam.lineTo(x, y + 1)
            }
            if (x >= plot.x + plot.w) break
            x = min(plot.x + plot.w, x + 2f)
        }
        surface.lineTo(plot.x + plot.w, bottom)
        surface.lineTo(plot.x, bottom)
        surface.close()
        drawPath(
            surface,
            Brush.verticalGradient(
                0f to Color(0.561f, 0.839f, 0.910f), 0.32f to Color(0.110f, 0.498f, 0.682f), 1f to Color(0.039f, 0.227f, 0.361f),
                startY = top, endY = bottom,
            ),
            alpha = 0.96f,
        )
        clipPath(surface) {
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color(0.45f, 0.80f, 0.90f, 0.05f + 0.16f * glow),
                    1f to Color.Transparent,
                    startX = scrubX - 32, endX = scrubX + 32,
                ),
                Offset(scrubX - 32, top),
                Size(64f, bottom - top),
            )
        }
        drawPath(foam, Color(0.93f, 0.97f, 0.98f, 0.8f), style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The harbour screen behind everything: the AGSL backdrop from API 33, gradients below it. */
@Composable
private fun TideBackdrop(modifier: Modifier) {
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(TIDE_BACKDROP_AGSL) }.getOrNull() else null
    }
    Box(
        modifier.drawWithCache {
            val w = size.width / density
            val h = size.height / density
            val brush = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                shader.setFloatUniform("size", w, h)
                ShaderBrush(shader)
            } else {
                null
            }
            val base = Brush.verticalGradient(
                0f to Color(0.051f, 0.137f, 0.188f), 0.45f to Color(0.039f, 0.122f, 0.161f), 1f to Color(0.024f, 0.071f, 0.098f),
                startY = 0f, endY = h,
            )
            val glowBrush = Brush.radialGradient(listOf(Color(0.02f, 0.07f, 0.09f), Color.Transparent), Offset(w * 0.5f, h * 0.42f), w * 0.55f)
            val vignette = Brush.radialGradient(
                0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.38f),
                center = Offset(w / 2, h / 2), radius = max(w, h) * 0.62f,
            )
            onDrawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    if (brush != null) {
                        drawRect(brush, Offset.Zero, Size(w, h))
                    } else {
                        drawRect(base, Offset.Zero, Size(w, h))
                        drawRect(glowBrush, Offset.Zero, Size(w, h), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
                        drawRect(vignette, Offset.Zero, Size(w, h))
                    }
                }
            }
        },
    )
}
