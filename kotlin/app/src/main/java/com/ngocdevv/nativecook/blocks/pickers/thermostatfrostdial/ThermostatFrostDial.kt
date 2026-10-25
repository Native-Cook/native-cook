package com.ngocdevv.nativecook.blocks.pickers.thermostatfrostdial

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/*
 Thermostat Frost Dial — "Hearthline", the living room's thermostat in a smart-home app.

 A slatted oak wall with a round thermostat on it: a matte ceramic ring with fine grip grooves and
 a set-point notch, around a dark face with 41 ticks (10° – 30°, every 0.5°) and the set point in
 big light digits. Colder grows frost over the ring and cools the wall to steel blue; warmer melts
 the frost into beads, the wall glows amber and the air above the dial shimmers. One full-screen
 AGSL shader (ThermostatFrostDialShader.kt; a drawn stand-in below Android 13), composables for the
 text and controls.

 autoPlay: LOOP = 9 s (21° → 16° → 25° → 21°, ThermostatFrostDialMotion.kt). Touch: drag around
 the dial to turn it (13.5° of turn per degree, rubber-band past 10° and 30°); on release a spring
 carries the finger's speed and settles on the nearest 0.5°; a tick haptic at every 0.5° step.
 2 s after the last touch the dial eases back to 21° (0.9 s) and the loop restarts. Reduced
 motion: 21°, still; the dial still turns under a finger.

 Per frame: the simulation steps once and bumps `tick`, read only in draw and graphicsLayer lambdas.
*/

internal val DialBackground = Color(0xFF3A3530)
private val Ink = Color(0xFFF2EDE6)
private val Soft = rgba(242, 237, 230, 0.62f)
private const val DIGIT_H = 76f
private val DIGITS = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val HALVES = listOf("0", "5", "0")
private val MODES = listOf("Heat", "Cool", "Auto", "Eco")
private val Ease = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private const val HOUSE = "M2.5 7.4 L8 2.8 L13.5 7.4 V13.2 H2.5 Z"
private const val DOOR = "M6.4 13.2 V9.6 H9.6 V13.2"
private const val BOLT = "M9 1.8 L3.6 9 H7.6 L6.8 14.2 L12.4 6.8 H8.4 Z"

private val BigStyle = TextStyle(
    color = Ink,
    fontSize = 66.sp,
    lineHeight = DIGIT_H.sp,
    fontWeight = FontWeight.ExtraLight,
    fontFeatureSettings = "tnum",
    letterSpacing = (-1).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Whether the frame loop is running, so a touch knows to start it again. */
private class DialLoop {
    var running = false
}

/**
 * A smart-home thermostat: a ceramic ring turned around a dial face, frosting over when colder and
 * glowing amber when warmer.
 *
 * @param autoPlay plays the 9 s cold-warm-rest loop until touched.
 */
@Composable
fun ThermostatFrostDial(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    // A shader the driver cannot compile throws: fall back to the drawn scene instead of crashing.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(THERMOSTAT_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val sim = remember(auto) { DialSim() }
    val uniforms = remember { DialUniforms() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val loop = remember { DialLoop() }
    // "Heating to" / "Cooling to" rolls when the set point crosses the room's temperature.
    val heating = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier.fillMaxSize().background(DialBackground).clipToBounds()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val geo = remember(w, h, safeTop, safeBottom) { makeDialGeo(w, h, safeTop, safeBottom) }

        LaunchedEffect(sim, auto, reduced, wake.intValue) {
            var heatNow = if (sim.frame.temp >= DIAL_ROOM) 1 else 0
            var detent = -1
            fun after() {
                tick.intValue += 1
                val f = sim.frame
                val heat = if (f.temp >= DIAL_ROOM) 1 else 0
                if (heat != heatNow) {
                    heatNow = heat
                    scope.launch { heating.animateTo(heat.toFloat(), tween(320, easing = Ease)) }
                }
                // A detent every 0.5° while a finger is on the dial (or its release spring is settling).
                val step = if (sim.mode == DIAL_MODE_LIVE) round(f.temp * 2f).toInt() else -1
                if (step >= 0 && detent >= 0 && step != detent) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                detent = step
            }
            sim.step(0f, auto, reduced)
            after()
            loop.running = true
            try {
                var last = -1L
                while (true) {
                    val idle = withFrameNanos { now ->
                        val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 1f / 20f)
                        last = now
                        sim.step(dt, auto, reduced)
                        after()
                        sim.idle(auto)
                    }
                    if (idle) break
                }
            } finally {
                loop.running = false
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            val d = density
            uniforms.set(sim.frame)
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("size", max(1f, geo.w), max(1f, geo.h))
                    shader.setFloatUniform("dial", geo.cx, geo.cy, geo.ro, geo.ri)
                    shader.setFloatUniform("ring", uniforms.ring)
                    shader.setFloatUniform("env", uniforms.env)
                    shader.setFloatUniform("lit", uniforms.lit)
                    drawRect(brush, Offset.Zero, Size(geo.w, geo.h))
                } else {
                    drawDialFallback(geo, uniforms)
                }
            }
        }

        DialHeader(safeTop)

        // The dial's touch area: the ring and a 16 dp margin around it.
        val hit = geo.ro * 2f + 32f
        Box(
            Modifier
                .offset((geo.cx - geo.ro - 16f).dp, (geo.cy - geo.ro - 16f).dp)
                .size(hit.dp)
                .semantics { contentDescription = "Set temperature. Turn the dial." }
                .pointerInput(sim) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val c = size.width / 2f
                        fun angle(p: Offset) = atan2(p.y - c, p.x - c)
                        sim.grab(angle(down.position))
                        if (!loop.running) wake.intValue += 1
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                if (ch.positionChanged()) {
                                    sim.turn(angle(ch.position))
                                    ch.consume()
                                }
                            }
                        } finally {
                            sim.release()
                        }
                    }
                },
        )

        DialReadout(geo, sim, tick, heating)
        DialRangeLabel("10°", geo.cx - geo.ri * 0.5f - 14f, geo.cy + geo.ri * 0.5f - 7f)
        DialRangeLabel("30°", geo.cx + geo.ri * 0.5f - 14f, geo.cy + geo.ri * 0.5f - 7f)
        DialBottom(safeBottom)
    }
}

@Composable
private fun DialHeader(safeTop: Float) {
    val hairline = (1f / LocalDensity.current.density).dp
    Column(
        Modifier
            .padding(start = 24.dp, end = 24.dp, top = (safeTop + 10f).dp)
            .fillMaxWidth()
            .height(DIAL_HEADER_H.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .drawWithCache {
                        val house = PathParser().parsePathString(HOUSE).toPath()
                        val door = PathParser().parsePathString(DOOR).toPath()
                        val stroke = Stroke(1.5f, join = StrokeJoin.Round)
                        onDrawBehind {
                            scale(density, density, pivot = Offset.Zero) {
                                drawPath(house, Ink, style = stroke)
                                drawPath(door, Ink, style = stroke)
                            }
                        }
                    },
            )
            BasicText("HEARTHLINE", style = TextStyle(color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(rgba(242, 237, 230, 0.14f))
                    .border(hairline, rgba(242, 237, 230, 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("MR", style = TextStyle(color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
        }
        BasicText(
            "Living room",
            modifier = Modifier.padding(top = 10.dp),
            style = TextStyle(color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            maxLines = 1,
        )
        BasicText(
            "Indoor 20.5° · Humidity 41% · Eco on",
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(color = Soft, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

@Composable
private fun DialReadout(geo: DialGeo, sim: DialSim, tick: MutableIntState, heating: Animatable<Float, *>) {
    Column(
        Modifier
            .offset((geo.cx - 120f).dp, (geo.cy - 70f).dp)
            .size(240.dp, 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(160.dp, 18.dp)
                .clipToBounds(),
        ) {
            val mode = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, textAlign = TextAlign.Center)
            BasicText(
                "HEATING TO",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val k = heating.value
                        alpha = k
                        translationY = (1f - k) * -14.dp.toPx()
                    },
                style = mode.copy(color = Color(0xFFF5B46A)),
            )
            BasicText(
                "COOLING TO",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val k = heating.value
                        alpha = 1f - k
                        translationY = k * 14.dp.toPx()
                    },
                style = mode.copy(color = Color(0xFF9CCBF2)),
            )
        }
        Row(
            Modifier
                .padding(top = 4.dp)
                .height(DIGIT_H.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DialWheel(sim, tick, 0, DIGITS)
            DialWheel(sim, tick, 1, DIGITS)
            DialBig(".")
            DialWheel(sim, tick, 2, HALVES)
            Box(
                Modifier
                    .padding(start = 2.dp)
                    .height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("°", style = BigStyle.copy(fontSize = 44.sp, lineHeight = 64.sp))
            }
        }
        BasicText(
            "Indoor 20.5°",
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(color = Soft, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun DialBig(text: String) {
    Box(Modifier.height(DIGIT_H.dp), contentAlignment = Alignment.Center) {
        BasicText(text, style = BigStyle)
    }
}

/** One column of the big number: a strip of digits behind a one-digit window. */
@Composable
private fun DialWheel(sim: DialSim, tick: MutableIntState, column: Int, strip: List<String>) {
    Box(
        Modifier
            .height(DIGIT_H.dp)
            .clipToBounds(),
    ) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer {
                    tick.intValue
                    translationY = -dialDigitWheel(sim.frame.temp, column) * DIGIT_H.dp.toPx()
                },
        ) {
            strip.forEach { DialBig(it) }
        }
    }
}

@Composable
private fun DialRangeLabel(text: String, x: Float, y: Float) {
    BasicText(
        text,
        modifier = Modifier
            .offset(x.dp, y.dp)
            .width(28.dp),
        style = TextStyle(color = rgba(242, 237, 230, 0.45f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
    )
}

@Composable
private fun DialBottom(safeBottom: Float) {
    val hairline = (1f / LocalDensity.current.density).dp
    val line = rgba(242, 237, 230, 0.14f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .padding(start = 20.dp, end = 20.dp, bottom = max(safeBottom, 12f).dp)
                .fillMaxWidth()
                .height(DIAL_BOTTOM_H.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(rgba(20, 18, 16, 0.42f))
                    .border(hairline, line, RoundedCornerShape(22.dp))
                    .padding(4.dp),
            ) {
                MODES.forEach { m ->
                    val active = m == "Auto"
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (active) {
                                    Modifier
                                        .cssBoxShadow(rgba(0, 0, 0, 0.3f), 1.dp, 2.dp, 18.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(rgba(242, 237, 230, 0.92f))
                                        .dialInsetTop(rgba(255, 255, 255, 0.9f), 18.dp)
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            m,
                            style = TextStyle(color = if (active) Color(0xFF2A2622) else Soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .cssBoxShadow(rgba(0, 0, 0, 0.25f), 2.dp, 4.dp, 24.dp)
                    .cssBoxShadow(rgba(0, 0, 0, 0.3f), 18.dp, 40.dp, 24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(rgba(24, 22, 20, 0.55f))
                    .dialInsetTop(rgba(255, 255, 255, 0.08f), 24.dp)
                    .border(hairline, line, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(
                    "TODAY",
                    style = TextStyle(color = rgba(242, 237, 230, 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
                )
                DialScheduleRow("18:00", "Evening", "21°", active = true)
                DialScheduleRow("22:30", "Night", "18°", active = false)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind { drawRect(line, size = Size(size.width, hairline.toPx().coerceAtLeast(1f))) }
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .drawWithCache {
                                val bolt = PathParser().parsePathString(BOLT).toPath()
                                onDrawBehind { scale(density, density, pivot = Offset.Zero) { drawPath(bolt, Color(0xFFF2B45C)) } }
                            },
                    )
                    BasicText(
                        "3.2 kWh today · 12% under your usual",
                        style = TextStyle(color = Soft, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DialScheduleRow(time: String, name: String, temp: String, active: Boolean) {
    val row = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .drawBehind {
                    drawCircle(if (active) Color(0xFFF2B45C) else rgba(242, 237, 230, 0.25f), radius = 4.dp.toPx())
                },
        )
        BasicText(time, modifier = Modifier.width(48.dp), style = row)
        BasicText(name, modifier = Modifier.weight(1f), style = TextStyle(color = Soft, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        BasicText(temp, style = row)
    }
}

/** CSS `inset 0 1px 0 color`: a one-dp crescent of light along the top inside edge. */
private fun Modifier.dialInsetTop(color: Color, radius: Dp) = drawWithCache {
    val r = radius.toPx()
    val dy = 1.dp.toPx()
    val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    val inner = Path().apply { addRoundRect(RoundRect(0f, dy, size.width, size.height + dy, CornerRadius(r))) }
    onDrawBehind {
        clipPath(outer) { clipPath(inner, ClipOp.Difference) { drawRect(color) } }
    }
}
