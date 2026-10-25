package com.ngocdevv.nativecook.blocks.charts.ripplepondheatmap

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Ripple Pond Heatmap — "Stillpond", a meditation habit tracker's month as lily pads on a pond.

 A pond seen from above (sand, pebbles, drifting caustics), October as a 7 × 5 grid of lily pads
 whose green deepens with the minutes practised; days over an hour carry a water lily, days after
 today are faint rings. Tap a day and a ripple spreads from it: the floor refracts, light focuses
 into rings, the pads around bob as the wave passes, a ring lights the pad and the card below
 rolls to that day. Drag across the water and a wake follows. One full-screen AGSL shader
 (RipplePondHeatmapShader.kt; a drawn stand-in below Android 13) over pads baked once, off the
 main thread (RipplePondHeatmapArt.kt).

 autoPlay: LOOP = 9 s (RipplePondHeatmapMotion.kt). 2 s after the last touch the water calms
 (0.9 s), the card rolls back to the month and the loop restarts. Reduced motion: still water,
 the month card, no loop.

 Per frame: the simulation steps once and bumps `tick`, read only in the draw lambda; the card
 changes a few times a loop.
*/

internal val PondBackground = Color(0xFF0B2A2E)
private val Ink = Color(0xFFEAF3EC)
private val Soft = rgba(234, 243, 236, 0.66f)
private val LEVELS = listOf(Color(0xFF9DB3A2), Color(0xFFA9CF8E), Color(0xFF6FB06A), Color(0xFF3E8C4E), Color(0xFF1F6A3C))
private val WEEK = listOf("M", "T", "W", "T", "F", "S", "S")
private const val ROLL = 52f
private val Ease = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private const val LEAF = "M8 14.5 C3 12 2 7 3.5 2.5 C8 3 12.5 6 8 14.5 Z"
private const val LEAF_VEIN = "M8 14 C6.5 10 5.2 7 3.8 3"

/** Whether the frame loop is running, so a touch knows to start it again. */
private class PondLoop {
    var running = false
}

/**
 * A meditation habit tracker's month as lily pads on a pond: tap a day to send a ripple and read
 * it, drag to leave a wake.
 *
 * @param autoPlay plays the 9 s tap-tap-wake loop until touched.
 */
@Composable
fun RipplePondHeatmap(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    // A shader the driver cannot compile throws: fall back to the drawn scene instead of crashing.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(RIPPLE_POND_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val fallback = remember { PondFallback() }
    val sim = remember(auto) { PondSim() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val detail = remember { mutableIntStateOf(-1) }
    val loop = remember { PondLoop() }

    BoxWithConstraints(modifier.fillMaxSize().background(PondBackground).clipToBounds()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val geo = remember(w, h, safeTop, safeBottom) { makePondGeo(w, h, safeTop, safeBottom) }
        var pads by remember { mutableStateOf<PondPads?>(null) }
        LaunchedEffect(geo) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to settle
            // (a cancellable delay) so the bake does not restart on every inset frame.
            delay(120)
            pads = withContext(Dispatchers.Default) { bakePondPads(geo.w, geo.grid) }
        }
        val ready = pads
        // The shader samples the pads; until they are baked it reads an empty pixel.
        remember(shader, ready) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                val bitmap = ready?.bitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                shader.setInputShader(
                    "pads",
                    BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(
                            Matrix().apply {
                                setScale(1f / POND_ART_SCALE, 1f / POND_ART_SCALE)
                                postTranslate(0f, ready?.bandTop ?: 0f)
                            },
                        )
                    },
                )
            }
            ready
        }

        // The loop starts at t = 0 on the first frame that has the pads.
        LaunchedEffect(sim, geo, auto, reduced, ready != null, wake.intValue) {
            fun after() {
                tick.intValue += 1
                // The card follows the selection (a few times a loop, never per frame).
                if (sim.frame.detail != detail.intValue) detail.intValue = sim.frame.detail
            }
            sim.step(0f, geo, auto, reduced)
            after()
            if (ready == null || reduced) return@LaunchedEffect
            loop.running = true
            try {
                var last = -1L
                while (true) {
                    val idle = withFrameNanos { now ->
                        val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 1f / 20f)
                        last = now
                        sim.step(dt, geo, auto, false)
                        after()
                        sim.idle(auto)
                    }
                    if (idle) break
                }
            } finally {
                loop.running = false
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "October as lily pads on a pond. Tap a day to see it; drag to make a wake." }
                .pointerInput(sim, geo, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        sim.down(down.position.x / d, down.position.y / d)
                        if (!loop.running) wake.intValue += 1
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var pos = down.position
                        var lifted = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(c.uptimeMillis, c.position)
                                pos = c.position
                                if (!c.pressed) {
                                    lifted = true
                                    break
                                }
                                val tx = (pos.x - down.position.x) / d
                                val ty = (pos.y - down.position.y) / d
                                if (hypot(tx, ty) >= 10f) {
                                    val v = tracker.calculateVelocity()
                                    sim.drag(pos.x / d, pos.y / d, hypot(v.x, v.y) / d)
                                }
                            }
                            if (lifted) {
                                val tx = (pos.x - down.position.x) / d
                                val ty = (pos.y - down.position.y) / d
                                if (hypot(tx, ty) < 10f) {
                                    if (sim.tap(geo, pos.x / d, pos.y / d) > 0) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                } else {
                                    sim.dragEnd(geo, pos.y / d)
                                }
                            }
                        } finally {
                            sim.dragging = false
                        }
                    }
                },
        ) {
            tick.intValue
            val d = density
            val f = sim.frame
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    val s = f.src
                    shader.setFloatUniform("size", max(1f, geo.w), max(1f, geo.h))
                    shader.setFloatUniform("grid", geo.grid)
                    for (i in 0 until POND_SOURCES) shader.setFloatUniform("s$i", s[i * 4], s[i * 4 + 1], s[i * 4 + 2], s[i * 4 + 3])
                    shader.setFloatUniform("sel", f.sel[0], f.sel[1], f.sel[2], 0f)
                    shader.setFloatUniform("phase", f.phase - floor(f.phase))
                    drawRect(brush, Offset.Zero, Size(geo.w, geo.h))
                } else {
                    with(fallback) { draw(geo, f, ready) }
                }
            }
        }

        PondHeader(safeTop)
        Row(Modifier.offset(geo.grid[0].dp, (geo.grid[1] - 26f).dp)) {
            WEEK.forEach { day ->
                BasicText(
                    day,
                    modifier = Modifier.width(geo.grid[2].dp),
                    style = TextStyle(color = rgba(234, 243, 236, 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
            }
        }
        PondLegend(geo)
        PondCard(geo, detail.intValue)
        Row(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = geo.chipsTop.dp)
                .fillMaxWidth()
                .height(POND_CHIPS_H.dp)
                .pointerInput(Unit) {},
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PondChip("Best streak $POND_BEST_STREAK days")
            PondChip("Reminder 7:00")
            PondChip("Export")
        }
    }
}

@Composable
private fun PondHeader(safeTop: Float) {
    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = (safeTop + 10f).dp)
            .fillMaxWidth()
            .height(POND_HEADER_H.dp),
    ) {
        Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(16.dp)
                    .drawWithCache {
                        val leaf = PathParser().parsePathString(LEAF).toPath()
                        val vein = PathParser().parsePathString(LEAF_VEIN).toPath()
                        val stroke = Stroke(1f, cap = StrokeCap.Round)
                        onDrawBehind {
                            scale(density, density, pivot = Offset.Zero) {
                                drawPath(leaf, Color(0xFF8FD19A))
                                drawPath(vein, Color(0xFF2F6B3D), style = stroke)
                            }
                        }
                    },
            )
            BasicText("STILLPOND", style = TextStyle(color = Color(0xFF9FD8A9), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(
                "October",
                modifier = Modifier.alignByBaseline(),
                style = TextStyle(color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            )
            BasicText(
                "2026",
                modifier = Modifier.alignByBaseline(),
                style = TextStyle(color = Soft, fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            )
        }
        BasicText(
            POND_SUMMARY,
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(color = Soft, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
        )
    }
}

@Composable
private fun PondLegend(geo: PondGeo) {
    val text = TextStyle(color = rgba(234, 243, 236, 0.55f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = geo.legendTop.dp)
            .fillMaxWidth()
            .height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText("Less", style = text)
        Box(
            Modifier
                .size(92.dp, 14.dp)
                .drawWithCache {
                    onDrawBehind {
                        scale(density, density, pivot = Offset.Zero) {
                            LEVELS.forEachIndexed { i, c ->
                                drawCircle(c, radius = 4f + i * 0.7f, center = Offset(7f + i * 19.5f, 7f), alpha = if (i == 0) 0.7f else 1f)
                            }
                        }
                    }
                },
        )
        BasicText("More", style = text)
        Spacer(Modifier.weight(1f))
        BasicText("Goal 20 min a day", style = text)
    }
}

@Composable
private fun PondCard(geo: PondGeo, id: Int) {
    val hairline = (1f / LocalDensity.current.density).dp
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = geo.cardTop.dp)
            .fillMaxWidth()
            .height(POND_CARD_H.dp)
            // The card is not water: touches on it do not reach the pond, as in React Native.
            .pointerInput(Unit) {}
            .cssBoxShadow(rgba(0, 10, 12, 0.35f), 2.dp, 4.dp, 24.dp)
            .cssBoxShadow(rgba(0, 10, 12, 0.35f), 20.dp, 40.dp, 24.dp)
            .clip(shape)
            .background(rgba(8, 30, 32, 0.66f))
            .pondInsetTop(rgba(255, 255, 255, 0.08f), 24.dp)
            .border(hairline, rgba(200, 240, 215, 0.2f), shape)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        PondRollingDetail(id)
    }
}

/**
 The card's two lines, rolled: two slots take turns. A new detail is written into the slot that is
 hidden (transparent, clipped), which then rolls up from below while the shown one rolls up and
 out, so old and new lines never sit on each other.
*/
@Composable
private fun PondRollingDetail(id: Int) {
    val texts = remember { mutableStateListOf(id, id) }
    var front by remember { mutableIntStateOf(0) }
    val y = remember { listOf(Animatable(0f), Animatable(1f)) }
    LaunchedEffect(id) {
        if (id == texts[front]) return@LaunchedEffect
        val back = 1 - front
        texts[back] = id
        front = back
        val incoming = y[back]
        val outgoing = y[1 - back]
        // The incoming slot starts below the window (it was invisible above it), then both roll up.
        incoming.snapTo(1f)
        launch { incoming.animateTo(0f, tween(380, easing = Ease)) }
        outgoing.animateTo(-1f, tween(380, easing = Ease))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(ROLL.dp)
            .clipToBounds(),
    ) {
        for (slot in 0 until 2) {
            val d = pondDetailFor(texts[slot])
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val v = y[slot].value
                        alpha = 1f - min(1f, abs(v) * 1.4f)
                        translationY = v * ROLL.dp.toPx()
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicText(
                    d.title,
                    style = TextStyle(color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 20.sp, stepSize = 0.5.sp),
                )
                BasicText(d.sub, style = TextStyle(color = Soft, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PondChip(label: String) {
    val hairline = (1f / LocalDensity.current.density).dp
    val shape = RoundedCornerShape((POND_CHIPS_H / 2f).dp)
    Box(
        Modifier
            .height(POND_CHIPS_H.dp)
            .clip(shape)
            .background(rgba(200, 240, 215, 0.1f))
            .border(hairline, rgba(200, 240, 215, 0.2f), shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

/** CSS `inset 0 1px 0 color`: a one-dp crescent of light along the top inside edge. */
private fun Modifier.pondInsetTop(color: Color, radius: Dp) = drawWithCache {
    val r = radius.toPx()
    val dy = 1.dp.toPx()
    val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    val inner = Path().apply { addRoundRect(RoundRect(0f, dy, size.width, size.height + dy, CornerRadius(r))) }
    onDrawBehind {
        clipPath(outer) { clipPath(inner, ClipOp.Difference) { drawRect(color) } }
    }
}
