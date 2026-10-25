package com.ngocdevv.nativecook.blocks.text.windscatterletters

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PathMeasure
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Wind Scatter Letters — the hero of an autumn weather edition (a port of WindScatterLetters.tsx):
 the headline "Let it drift" blows apart like leaves in a gust and settles back. Each glyph is
 measured at runtime and pre-rendered once (off the main thread) into its own bitmap, then placed
 per frame with its position, rotation and scale; every third glyph sits further back, smaller and
 softened. Leaves drawn in code lie on the ridge and cross the sky with the gust. A swipe is a gust
 in that direction at that speed: glyphs and leaves near the finger scatter most, and soft springs
 bring them home.

 One Canvas: the sky bitmap, wind streaks, two motion trails, the far and near glyphs and the
 leaves in three colour batches. Copy and the button are composables. One frame float drives it.
*/

private const val IDLE = 2f
private val TRAIL_LAG = floatArrayOf(0.045f, 0.09f)

private class WindState(val reduced: Boolean, val auto: Boolean) {
    val frame = mutableFloatStateOf(0f)
    val mode = mutableIntStateOf(0)
    var clock = 0f
    var steps = 0
    var idleFor = 0f
    var sim: WindSim? = null
    val wind = WindWind()
    var ticked = false
}

/**
 * The headline of an autumn weather edition that blows apart like leaves in a gust and settles
 * back; a swipe across it is a gust of its own.
 *
 * @param autoPlay plays the gust (build, scatter, return) every LOOP seconds.
 */
@Composable
fun WindScatterLetters(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val state = remember(reduced, auto) { WindState(reduced, auto) }
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val insetTop = insets.getTop(density) / density.density
    val insetBottom = insets.getBottom(density) / density.density
    val d = density.density

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF25202A))) {
        val W = maxWidth.value
        val H = maxHeight.value
        // Measuring ten glyphs is cheap; everything drawn is rendered off the main thread.
        val L = remember(W, H, insetTop) { windMakeLayout(W, H, insetTop) }
        val bodies = L.homes.size
        val background by produceState<android.graphics.Bitmap?>(null, W, H, d) { value = withContext(Dispatchers.Default) { windMakeBackground(W, H, d) } }
        val glyphs by produceState<List<WindGlyph>?>(null, L, d) { value = withContext(Dispatchers.Default) { windMakeGlyphs(L, d) } }
        val leaf = remember { windMakeLeaf() }
        val streaks = remember(W, H) { windMakeStreaks(W, H).map { PathMeasure(it, false) } }

        LaunchedEffect(state, L) {
            var last = -1L
            while (true) {
                if (state.mode.intValue == 0 && !state.auto) {
                    snapshotFlow { state.mode.intValue }.first { it != 0 }
                    last = -1L
                }
                withFrameNanos { t ->
                    val dt = if (last < 0) 1f / 60f else min((t - last) / 1e9f, 0.05f)
                    last = t
                    if (state.mode.intValue == 0) {
                        state.clock = (state.clock + dt) % LOOP
                        state.frame.floatValue = state.clock
                        return@withFrameNanos
                    }
                    val s = state.sim ?: return@withFrameNanos
                    if (!state.wind.on) state.idleFor += dt
                    val settle = state.idleFor > IDLE
                    val n = if (dt > 1f / 90f) 2 else 1
                    for (k in 0 until n) windStepSim(s, L.homes, state.wind, dt / n, settle)
                    state.steps++
                    state.frame.floatValue = state.steps.toFloat()
                    if (settle && windSimEnergy(s) < 0.4f) {
                        state.clock = 0f
                        state.mode.intValue = 0
                        state.frame.floatValue = 0f
                    }
                }
            }
        }

        val poses = remember(bodies) { Array(3) { FloatArray(bodies * 4) } }
        val leafItem = remember { FloatArray(5) }
        val leafPaths = remember { Array(3) { android.graphics.Path() } }
        val ribs = remember { android.graphics.Path() }
        val m = remember { Matrix() }
        val seg = remember { android.graphics.Path() }
        val bmp = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
        val leafPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
        val ribPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = 0x8C3C180C.toInt() } }
        val streakPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; strokeCap = Paint.Cap.ROUND; color = 0xFFFFE4C4.toInt() } }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    heading()
                    contentDescription = "Let it drift. Swipe across the headline to blow the letters away."
                }
                .pointerInput(state, L) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Take over from the loop on the frame on screen.
                        if (state.mode.intValue == 0) {
                            val s = WindSim(bodies)
                            val t = state.clock
                            val o = FloatArray(4)
                            for (i in 0 until bodies) {
                                if (state.auto) windAutoOffset(t, i, L.homes[i], W, H, o, 0) else { o[0] = 0f; o[1] = 0f; o[2] = 0f; o[3] = 1f }
                                s.x[i] = o[0]; s.y[i] = o[1]; s.r[i] = o[2]
                                s.px0[i] = o[0]; s.py0[i] = o[1]; s.px1[i] = o[0]; s.py1[i] = o[1]
                            }
                            state.sim = s
                            state.mode.intValue = 1
                        }
                        state.idleFor = 0f
                        val w = state.wind
                        w.fx = down.position.x / d; w.fy = down.position.y / d; w.fvx = 0f; w.fvy = 0f; w.on = true
                        state.ticked = false
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        try {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                                val v = tracker.calculateVelocity()
                                w.fx = ch.position.x / d; w.fy = ch.position.y / d
                                w.fvx = v.x / d; w.fvy = v.y / d
                                state.idleFor = 0f
                                if (!state.ticked && hypot(w.fvx, w.fvy) > 1400f) {
                                    state.ticked = true
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        } finally {
                            w.on = false; w.fvx = 0f; w.fvy = 0f
                            state.idleFor = 0f
                        }
                    }
                },
        ) {
            state.frame.floatValue
            val user = state.mode.intValue != 0
            val t = state.clock
            val s = state.sim
            // Every body's [dx, dy, rotation, scale] now, and at two earlier moments for the trails.
            for (k in 0 until 3) {
                val p = poses[k]
                for (i in 0 until bodies) {
                    val o = i * 4
                    if (!user) {
                        if (state.auto) windAutoOffset(max(0f, if (k == 0) t else t - TRAIL_LAG[k - 1]), i, L.homes[i], W, H, p, o)
                        else { p[o] = 0f; p[o + 1] = 0f; p[o + 2] = 0f; p[o + 3] = 1f }
                    } else if (s != null) {
                        val x = when (k) { 0 -> s.x[i]; 1 -> s.px0[i]; else -> s.px1[i] }
                        val y = when (k) { 0 -> s.y[i]; 1 -> s.py0[i]; else -> s.py1[i] }
                        p[o] = x; p[o + 1] = y; p[o + 2] = s.r[i]
                        p[o + 3] = 1f + windDepthOf(i) * min(1f, hypot(x, y) / 220f)
                    }
                }
            }
            // How much the air is moving now (0…1): streaks, trails and the far glyphs' softness follow it.
            val air = if (!user) (if (state.auto) windGustAt(t) else 0f) else {
                var v = 0f
                if (s != null) for (i in 0 until L.glyphs) v = max(v, hypot(s.vx[i], s.vy[i]))
                min(1f, v / 600f)
            }

            drawIntoCanvas { cv ->
                val nc = cv.nativeCanvas
                nc.save()
                nc.scale(d, d)
                background?.let { nc.drawBitmap(it, null, android.graphics.RectF(0f, 0f, W, H), bmp) }

                // Wind streaks: a segment 0.32 of the path long runs along it, twice.
                if (!user && state.auto && air > 0.002f) {
                    streaks.forEachIndexed { i, pm ->
                        val a = 1.6f + i * 0.13f
                        val p1 = windSeg(t, a, a + 1.1f)
                        val pp = if (p1 < 1f) p1 else windSeg(t, a + 1.4f, a + 2.5f)
                        val travel = windEaseInOutCubic(pp) * 1.32f
                        val head = min(1f, travel)
                        val start = min(1f, max(0f, travel - 0.32f))
                        if (head > start) {
                            seg.rewind()
                            pm.getSegment(start * pm.length, head * pm.length, seg, true)
                            streakPaint.alpha = (0.32f * air * (0.5f + 0.5f * windHash(i + 3f)) * 255).roundToInt()
                            nc.drawPath(seg, streakPaint)
                        }
                    }
                }

                val gl = glyphs
                if (gl != null) {
                    fun place(i: Int, p: FloatArray, draw: () -> Unit) {
                        val o = i * 4
                        nc.save()
                        nc.translate(L.homes[i].x + p[o], L.homes[i].y + p[o + 1])
                        nc.rotate(p[o + 2] * 180f / PI.toFloat())
                        nc.scale(p[o + 3], p[o + 3])
                        draw()
                        nc.restore()
                    }
                    // Two motion trails, then the far glyphs (softened as the air moves), then the near ones.
                    if (air > 0.002f) {
                        for (k in intArrayOf(2, 1)) {
                            bmp.alpha = ((if (k == 1) 0.2f else 0.09f) * air * 255).roundToInt()
                            for (i in 0 until L.glyphs) place(i, poses[k]) { nc.drawBitmap(gl[i].crisp, null, gl[i].rect, bmp) }
                        }
                    }
                    val k = air
                    for (i in 0 until L.glyphs) {
                        val g = gl[i]
                        if (g.soft == null || g.blur == null) continue
                        place(i, poses[0]) {
                            if (k < 0.999f) { bmp.alpha = ((1f - k) * 255).roundToInt(); nc.drawBitmap(g.soft, null, g.rect, bmp) }
                            if (k > 0.001f) { bmp.alpha = (k * 255).roundToInt(); nc.drawBitmap(g.blur, null, g.rect, bmp) }
                        }
                    }
                    bmp.alpha = 255
                    for (i in 0 until L.glyphs) {
                        if (gl[i].soft != null) continue
                        place(i, poses[0]) { nc.drawBitmap(gl[i].crisp, null, gl[i].rect, bmp) }
                    }
                }

                // Leaves: six on the ridge (bodies after the glyphs) and eight crossing with the gust.
                for (p in leafPaths) p.rewind()
                ribs.rewind()
                val p0 = poses[0]
                for (j in 0 until WIND_GROUND_LEAVES + WIND_FLYING_LEAVES) {
                    if (j < WIND_GROUND_LEAVES) {
                        val i = L.glyphs + j
                        val h = L.homes[i]
                        val o = i * 4
                        val rest = (windHash(j * 2.3f + 40f) - 0.5f) * 1.2f + (if (j % 2 == 1) PI.toFloat() else 0f)
                        leafItem[0] = h.x + p0[o]; leafItem[1] = h.y + p0[o + 1]
                        leafItem[2] = rest + p0[o + 2] * 1.6f; leafItem[3] = cos(p0[o + 2] * 2.2f)
                        leafItem[4] = 0.9f + 0.3f * windHash(j + 50f)
                    } else if (!user && state.auto) {
                        windFlyingLeaf(t, j - WIND_GROUND_LEAVES, W, H, leafItem)
                    } else continue
                    if (leafItem[0] < -100f) continue
                    m.setScale(leafItem[3] * leafItem[4], leafItem[4])
                    m.postRotate(leafItem[2] * 180f / PI.toFloat())
                    m.postTranslate(leafItem[0], leafItem[1])
                    leafPaths[j % 3].addPath(leaf.blade, m)
                    ribs.addPath(leaf.rib, m)
                }
                for (c in 0..2) {
                    leafPaint.color = WindPalette.leaves[c]
                    nc.drawPath(leafPaths[c], leafPaint)
                }
                nc.drawPath(ribs, ribPaint)
                nc.restore()
            }
        }

        // Masthead.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (insetTop + 24).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("AUTUMN EDITION", style = TextStyle(color = Color(WindPalette.EMBER), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("No. 14 · Sat 25 Oct", style = TextStyle(color = Color(0x8CF4E7D4), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
        }

        // The deck under the headline.
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (L.b2 + 34f).dp)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BasicText(
                "Gusts up to 48 km/h from the west. Leaves down by noon, clear skies by four.",
                style = TextStyle(color = Color(0xB8F4E7D4), fontSize = 17.sp, lineHeight = 24.sp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WindStat("Wind", "32", "km/h W")
                WindStat("Gusts", "48", "km/h")
                WindStat("Feels", "9°", "at 15:00")
            }
            if (L.b2 + 34f + 292f <= H * 0.78f) WindHourly()
        }

        // The button.
        Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = (insetBottom + 20).dp), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .cssBoxShadow(Color(0x66000000), 1.dp, 2.dp, 26.dp)
                    .cssBoxShadow(Color(0x59000000), 12.dp, 30.dp, 26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(0f to Color(0xFFF7EAD6), 0.5f to Color(0xFFEFD8B8), 1f to Color(0xFFE2C196)))
                    .drawWithContent {
                        val r = CornerRadius(size.height / 2)
                        val pill = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r)) }
                        val lower = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 1.dp.toPx(), size.width, size.height + 1.dp.toPx(), r)) }
                        clipPath(pill) { drawPath(Path.combine(PathOperation.Difference, pill, lower), Color(0xB3FFFFFF)) }
                        drawContent()
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WindIcon()
                BasicText("Read the forecast", style = TextStyle(color = Color(0xFF3A2418), fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/** Three gusting strokes (the "wind" symbol), 17 dp. */
@Composable
private fun WindIcon() {
    Canvas(Modifier.size(17.dp)) {
        val u = size.width / 17f
        val c = Color(0xFF3A2418)
        val st = Stroke(width = 1.6f * u, cap = StrokeCap.Round)
        val a = Path().apply { moveTo(1.5f * u, 6f * u); lineTo(10.5f * u, 6f * u); cubicTo(14f * u, 6f * u, 14f * u, 1.5f * u, 10.8f * u, 1.8f * u) }
        val b = Path().apply { moveTo(1.5f * u, 9.5f * u); lineTo(13f * u, 9.5f * u); cubicTo(16.5f * u, 9.5f * u, 16.5f * u, 14.5f * u, 13.2f * u, 14.2f * u) }
        drawPath(a, c, style = st)
        drawPath(b, c, style = st)
        drawLine(c, Offset(1.5f * u, 13f * u), Offset(7.5f * u, 13f * u), 1.6f * u, StrokeCap.Round)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.WindStat(label: String, value: String, unit: String) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(Color(0x0FFFECD6))
            .border(0.5.dp, Color(0x29FFECD6), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(label.uppercase(), style = TextStyle(color = Color(0x80F4E7D4), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
        BasicText(value, style = TextStyle(color = Color(WindPalette.CREAM), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
        BasicText(unit, style = TextStyle(color = Color(0x8CF4E7D4), fontSize = 12.sp, fontWeight = FontWeight.Medium))
    }
}

private val HOURS = listOf("12:00" to 24, "13:00" to 31, "14:00" to 40, "15:00" to 48, "16:00" to 37, "17:00" to 22)

/** Gusts over the afternoon: one bar per hour, the peak in ember. */
@Composable
private fun WindHourly() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("GUSTS THIS AFTERNOON", style = TextStyle(color = Color(0x80F4E7D4), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            for ((at, kmh) in HOURS) {
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText("$kmh", style = TextStyle(color = Color(0xCCF4E7D4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                    Box(
                        Modifier
                            .width(6.dp)
                            .height((6f + (kmh / 48f) * 26f).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (kmh == 48) Color(WindPalette.EMBER) else Color(0x52F4E7D4)),
                    )
                    BasicText(at, style = TextStyle(color = Color(0x73F4E7D4), fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}
