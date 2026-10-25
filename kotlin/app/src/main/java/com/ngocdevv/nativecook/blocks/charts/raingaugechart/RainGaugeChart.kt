package com.ngocdevv.nativecook.blocks.charts.raingaugechart

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Rain Gauge Chart — "Plot 7", a garden app's week of rainfall as seven glass test-tube gauges in a
 wooden rack, one per day (Mon 3.2 … Sun 6.6 mm, 38.6 mm in all; a tube holds 15 mm), ported from
 RainGaugeChart.tsx. Everything that moves is android.graphics drawing in one layer
 (RainGaugeChartArt.kt): glass with a stripe and ticks, water with a meniscus that tilts as it
 sloshes, streaked rain with splashes and ripples. Text is Compose text.

 autoPlay (LOOP = 9 s, RainGaugeChartMotion.kt):
   0.0 – 1.4   rest: every tube holds its day's rain.
   1.4 – 2.4   the tubes drain; the total rolls down.
   2.4 – 6.0   rain sweeps across the rack from Monday to Sunday and each tube fills in turn.
   6.0 – 7.4   Thursday lifts out of the rack (40 dp, scale 1.1) with a slosh, its card rises.
   7.4 – 9.0   rest.

 Touch: tap a tube to lift it (spring 420 ms, damping 0.7, a light haptic); tap it again to put it
 back. Drag in the sky above the rack to rain on the tubes under the finger (1.1 mm/s, up to
 15 mm). 2 s after the last touch everything eases back to rest (800 ms) and the loop restarts.
 Reduced motion: the rest frame, no rain; a tap still shows a day.
 */

private val Ink = Color(0xFFEAF2F0)
private val Muted = Color(0xFFEAF2F0).copy(alpha = 0.58f)
private val Accent = Color(0xFF8FD3E8)
internal val RainBackground = Color(0xFF1D2D32)
private const val RETURN_MS = 800
private const val IDLE_MS = 2000L
private const val CARD_W = 176f

/** Reanimated's duration-based spring as a Compose spring: stiffness from the period. */
private fun rainSpring(ms: Int, damping: Float): AnimationSpec<Float> {
    val w = 2.0 * PI / (ms / 1000.0)
    return spring(dampingRatio = damping, stiffness = (w * w).toFloat())
}

/** The loop clock, the finger's state and the pose the scene reads. */
private class RainState(auto: Boolean) {
    val t = mutableFloatStateOf(0f)
    var manual by mutableStateOf(!auto)
    /** Bumped every frame while the finger's simulation runs, so the scene redraws. */
    val frame = mutableIntStateOf(0)
    var clock = 0f
    val levels = RainMm.copyOf()
    val kicks = FloatArray(TUBES) { -10f }
    var liftIdx by mutableIntStateOf(-1)
    val lift = Animatable(0f)
    var cursor by mutableFloatStateOf(0f)
    val rain = Animatable(0f)
    var rainT = 0f
    val back = Animatable(0f)
    var touchMode = 0
    var callout by mutableIntStateOf(LOOP_LIFT)
    var resume: Job? = null
    var wake by mutableIntStateOf(0)
    private val pose = RainPose()

    /** Reads state: call only inside draw / graphicsLayer lambdas. */
    fun pose(): RainPose {
        if (!manual) return rainLoopPose(t.floatValue, pose)
        frame.intValue
        val now = clock
        val r = rain.value
        val b = back.value
        for (i in 0 until TUBES) {
            val near = exp(-((i - cursor) / 0.9f).pow(2)) * r
            val slosh = rainKick(now - kicks[i], 3.2f) + 1.6f * near * sin(now * 2f * PI.toFloat() * 1.25f + i * 1.3f)
            pose.levels[i] = levels[i] + (RainMm[i] - levels[i]) * b
            pose.slosh[i] = slosh * (1f - b)
        }
        pose.liftIdx = liftIdx
        pose.lift = lift.value * (1f - b)
        pose.cursor = cursor
        pose.rain = r * (1f - b)
        pose.rainT = rainT
        return pose
    }

    /** True while the finger's scene still moves (outside autoPlay the frame loop then stops). */
    fun moving() = rain.value > 0.01f || lift.isRunning || back.isRunning || kicks.any { clock - it < 4f }
}

private fun rainGrainTile(): ImageBitmap {
    val n = 96
    val px = IntArray(n * n)
    for (y in 0 until n) {
        for (x in 0 until n) {
            var h = x * 374761393 + y * 668265263 + 977
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            val v = (h and 0xFFFF) / 65535f
            val a = (abs(v - 0.5f) * 2f * 255f).toInt().coerceIn(0, 255)
            px[y * n + x] = (a shl 24) or (if (v > 0.5f) 0xFFFFFF else 0)
        }
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
fun RainGaugeChart(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state = remember(auto) { RainState(auto) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    val grain = remember { rainGrainTile() }

    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var geo by remember { mutableStateOf<RainGeo?>(null) }

    LaunchedEffect(state, auto, state.wake) {
        var last = -1L
        while (true) {
            val keep = withFrameNanos { now ->
                val dt = if (last < 0) 0f else min((now - last) / 1e9f, 0.05f)
                last = now
                state.clock += dt
                if (!state.manual) {
                    state.t.floatValue = (state.t.floatValue + dt) % LOOP
                    return@withFrameNanos auto
                }
                state.rainT = (state.rainT + dt) % LOOP
                val r = state.rain.value
                if (r > 0.01f && state.back.value == 0f) {
                    for (i in 0 until TUBES) {
                        val near = exp(-((i - state.cursor) / 0.7f).pow(2))
                        state.levels[i] = min(CAPACITY, state.levels[i] + dt * 1.1f * r * near)
                    }
                }
                state.frame.intValue += 1
                auto || state.moving()
            }
            if (!keep) break
        }
    }

    fun backToRest() {
        scope.launch {
            val r = state.back.animateTo(1f, tween(RETURN_MS))
            if (r.endReason == AnimationEndReason.Finished && auto) {
                state.t.floatValue = 0f
                state.lift.snapTo(0f)
                state.rain.snapTo(0f)
                state.liftIdx = -1
                RainMm.copyInto(state.levels)
                state.callout = LOOP_LIFT
                state.manual = false
                state.back.snapTo(0f)
            }
        }
    }

    fun takeOver() {
        state.resume?.cancel()
        state.resume = null
        scope.launch { state.back.snapTo(0f) }
        if (!state.manual) {
            val p = rainLoopPose(state.t.floatValue, RainPose())
            p.levels.copyInto(state.levels)
            state.liftIdx = if (p.lift > 0.001f) p.liftIdx else -1
            state.cursor = p.cursor
            state.rainT = p.rainT
            val lift = p.lift
            val rain = p.rain
            scope.launch { state.lift.snapTo(lift) }
            scope.launch { state.rain.snapTo(rain) }
            state.manual = true
        }
        state.wake += 1
    }

    fun tapTube(i: Int) {
        state.kicks[i] = state.clock
        if (state.liftIdx == i && state.lift.value > 0.5f) {
            scope.launch { state.lift.animateTo(0f, if (reduced) tween(200) else rainSpring(420, 0.85f)) }
        } else {
            val prev = state.liftIdx
            if (prev >= 0 && prev != i) state.kicks[prev] = state.clock
            state.liftIdx = i
            scope.launch {
                state.lift.snapTo(0f)
                state.lift.animateTo(1f, if (reduced) tween(200) else rainSpring(420, 0.7f))
            }
            state.callout = i
        }
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(RainBackground)
            .onGloballyPositioned { root = it },
    ) {
        val screenW = maxWidth.value
        val screenH = maxHeight.value
        val g = geo
        val front = remember(g, screenW, screenH) { g?.let { rainRecordRackFront(it, screenW, screenH) } }
        val painter = remember(g, front) { if (g != null && front != null) RainPainter(g, front) else null }

        // The screen's gradient, its grain, the hedge, the bed and the vignette: static.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val gradient = Brush.verticalGradient(0f to Color(0xFF26393F), 0.5f to Color(0xFF1D2D32), 1f to Color(0xFF152124))
                    val grainBrush = ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
                    val d = density.density
                    val backdrop = geo?.let { rainRecordBackdrop(it, size.width / d, size.height / d) }
                    onDrawBehind {
                        drawRect(gradient)
                        if (backdrop != null) {
                            scale(d, d, pivot = Offset.Zero) { drawIntoCanvas { it.nativeCanvas.drawPicture(backdrop) } }
                        }
                        drawRect(grainBrush, alpha = 0.07f)
                    }
                },
        )

        // Tubes, rack, the lifted tube and the rain: the one layer that redraws per frame.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind {
                    val p = painter ?: return@drawBehind
                    val pose = state.pose()
                    val d = density.density
                    scale(d, d, pivot = Offset.Zero) { drawIntoCanvas { p.draw(it.nativeCanvas, pose) } }
                },
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 10).dp, bottom = (safeBottom + 12).dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("PLOT 7 · ALDER LANE", style = TextStyle(color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp))
                    Box(
                        Modifier
                            .height(28.dp)
                            .background(Color(0xFFE2F4F8).copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                            .border(Dp.Hairline, Color(0xFFE2F4F8).copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText("Week 43 · Oct 20 – 26", style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
                    }
                }
                BasicText(
                    "Rainfall this week",
                    Modifier.padding(top = 4.dp),
                    style = TextStyle(color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RainOdometer(state, digits = 3, suffix = " mm", height = 26.dp, style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                    BasicText("+12% on last week", style = TextStyle(color = Muted, fontSize = 14.sp))
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 360.dp)
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val b = 24.dp.roundToPx()
                        val pl = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * b, maxWidth = constraints.maxWidth + 2 * b))
                        layout(constraints.maxWidth, pl.height) { pl.place(-b, 0) }
                    }
                    .onGloballyPositioned { c ->
                        val r = root ?: return@onGloballyPositioned
                        if (!r.isAttached || !c.isAttached) return@onGloballyPositioned
                        val o = r.localPositionOf(c, Offset.Zero)
                        val d = density.density
                        val ox = o.x / d
                        val oy = o.y / d
                        val w = c.size.width / d
                        val h = c.size.height / d
                        val old = geo
                        if (old == null || old.ox != ox || old.oy != oy || old.w != w || old.h != h) geo = RainGeo(ox, oy, w, h)
                    }
                    .semantics { contentDescription = "Seven rain gauges, Monday to Sunday. Tap a gauge for its day, drag above the rack to make it rain." }
                    .pointerInput(auto, reduced) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val gg = geo ?: return@awaitEachGesture
                            val d = density.density
                            takeOver()
                            val sx = down.position.x / d
                            val sy = down.position.y / d
                            var ex = sx
                            var ey = sy
                            fun cursorAt(x: Float) = max(-0.5f, min(TUBES - 0.5f, (x + gg.ox - gg.cx[0]) / gg.pitch))
                            if (sy + gg.oy > gg.board - 24f || reduced) {
                                state.touchMode = 1
                            } else {
                                state.touchMode = 2
                                state.cursor = cursorAt(sx)
                                scope.launch { state.rain.animateTo(1f, tween(250)) }
                            }
                            try {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    ex = ch.position.x / d
                                    ey = ch.position.y / d
                                    if (!ch.pressed) break
                                    if (state.touchMode == 2) state.cursor = cursorAt(ex)
                                    ch.consume()
                                }
                            } finally {
                                if (state.touchMode == 1 && hypot(ex - sx, ey - sy) < 14f) {
                                    val x = ex + gg.ox
                                    val i = (0 until TUBES).firstOrNull { abs(x - gg.cx[it]) < gg.pitch / 2f } ?: -1
                                    if (i >= 0) tapTube(i)
                                }
                                if (state.touchMode == 2) scope.launch { state.rain.animateTo(0f, tween(600)) }
                                state.touchMode = 0
                                state.wake += 1
                                if (auto) {
                                    state.resume?.cancel()
                                    state.resume = scope.launch {
                                        delay(IDLE_MS)
                                        state.resume = null
                                        backToRest()
                                    }
                                }
                            }
                        }
                    },
            )

            Row(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .cssBoxShadow(Color.Black.copy(alpha = 0.3f), offsetY = 1.dp, blur = 2.dp, radius = 20.dp)
                    .cssBoxShadow(Color.Black.copy(alpha = 0.25f), offsetY = 12.dp, blur = 32.dp, radius = 20.dp)
                    .background(Color(0xFFE2F4F8).copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                    .border(Dp.Hairline, Color(0xFFE2F4F8).copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .padding(vertical = 14.dp, horizontal = 16.dp),
            ) {
                RainStat("Wettest", "Thu · 11.6 mm")
                Box(Modifier.padding(horizontal = 12.dp).width(Dp.Hairline).fillMaxHeight().background(Color(0xFFE2F4F8).copy(alpha = 0.14f)))
                RainStat("Dry spells", "1 day, Tue")
                Box(Modifier.padding(horizontal = 12.dp).width(Dp.Hairline).fillMaxHeight().background(Color(0xFFE2F4F8).copy(alpha = 0.14f)))
                RainStat("Watering", "Skip till Sat")
            }
        }

        // Labels laid over the scene at the geometry's positions.
        if (g != null) {
            val axis = TextStyle(color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
            BasicText("mm", Modifier.offset { dpOffset(g.ox + 10f, g.levelY(CAPACITY) - 30f) }, style = axis)
            for (mm in intArrayOf(15, 10, 5, 0)) {
                BasicText(
                    "$mm",
                    Modifier.offset { dpOffset(g.ox + 6f, g.levelY(mm.toFloat()) - 8f) }.width(22.dp),
                    style = axis.copy(textAlign = TextAlign.End),
                )
            }
            for (i in 0 until TUBES) RainDayLabel(state, g, i)

            val c = state.callout
            val cardTop = g.y0 - LIFT_PT - (g.y1 - g.y0) * LIFT_SCALE - 84f
            val cardLeft = max(16f, min(screenW - CARD_W - 16f, g.cx[c] - CARD_W / 2f))
            Column(
                Modifier
                    .offset { dpOffset(cardLeft, cardTop) }
                    .width(CARD_W.dp)
                    .graphicsLayer {
                        val l = state.pose().lift
                        alpha = l
                        translationY = 10.dp.toPx() * (1f - l)
                        val s = 0.96f + 0.04f * l
                        scaleX = s
                        scaleY = s
                    }
                    .cssBoxShadow(Color.Black.copy(alpha = 0.35f), offsetY = 1.dp, blur = 2.dp, radius = 16.dp)
                    .cssBoxShadow(Color.Black.copy(alpha = 0.3f), offsetY = 14.dp, blur = 30.dp, radius = 16.dp)
                    .background(Color(30, 50, 56).copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                    .border(Dp.Hairline, Color(170, 225, 240).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicText(RainFullDays[c], style = TextStyle(color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp))
                BasicText(String.format(Locale.US, "%.1f mm", RainMm[c]), style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
                BasicText(RainNotes[c], style = TextStyle(color = Muted, fontSize = 12.sp), maxLines = 1)
            }
        }
    }
}

private fun androidx.compose.ui.unit.Density.dpOffset(x: Float, y: Float) = IntOffset(x.dp.roundToPx(), y.dp.roundToPx())

@Composable
private fun RainDayLabel(state: RainState, g: RainGeo, i: Int) {
    val x = g.cx[i] - g.pitch / 2f
    val name = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val value = TextStyle(color = Color(0xFFEAF2F0).copy(alpha = 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val mm = String.format(Locale.US, "%.1f", RainMm[i])
    Box(Modifier.offset { dpOffset(x, g.ground + 6f) }.width(g.pitch.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(RainDays[i], style = name)
            BasicText(mm, style = value)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = state.pose()
                    alpha = if (p.liftIdx == i) p.lift.coerceIn(0f, 1f) else 0f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            BasicText(RainDays[i], style = name.copy(color = Accent))
            BasicText(mm, style = value.copy(color = Ink))
        }
    }
}

@Composable
private fun RowScope.RainStat(label: String, value: String) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(label, style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp))
        BasicText(value, style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

/** An odometer for a value with one decimal (38.6): `digits` columns in all, the last after the point. */
@Composable
private fun RainOdometer(state: RainState, digits: Int, suffix: String, height: Dp, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (k in digits - 1 downTo 0) {
            Box(Modifier.height(height).clipToBounds()) {
                Column(
                    Modifier
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .graphicsLayer {
                            // Tenths of a millimetre; each column turns with the lower digits' carry.
                            val v = max(0f, (rainTotalOf(state.pose().levels) * 10f * 1000f).roundToInt() / 1000f)
                            val scaledV = v / 10f.pow(k)
                            val whole = floor(scaledV) % 10f
                            val frac = scaledV - floor(scaledV)
                            val lower = if (k == 0) frac else max(0f, frac * 10f - 9f)
                            translationY = -(whole + lower) * height.toPx()
                        },
                ) {
                    for (dgt in 0..10) RainOdoCell("${dgt % 10}", height, style)
                }
            }
            if (k == 1) RainOdoCell(".", height, style)
        }
        RainOdoCell(suffix, height, style)
    }
}

@Composable
private fun RainOdoCell(text: String, height: Dp, style: TextStyle) {
    Box(Modifier.height(height), contentAlignment = Alignment.Center) { BasicText(text, style = style, maxLines = 1) }
}
