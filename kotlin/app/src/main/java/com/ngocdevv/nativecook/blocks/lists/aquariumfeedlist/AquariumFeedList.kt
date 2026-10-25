package com.ngocdevv.nativecook.blocks.lists.aquariumfeedlist

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/*
 Aquarium Feed List — "Reef Log · Tank 2", a reef keeper's log whose entries float inside the
 tank: glass cards behind which six fish cruise, tails waving. Scrolling stirs the water: the fish
 scatter away from the scroll and bubbles stream up; caustic light plays over everything; the list
 fades into water at both ends. Tap an entry and the fish come to it.

 autoPlay (LOOP = 9 s): rest 0–1.4 (fish cruising) · scripted scroll down 1.4–4.4 (scatter,
 bubbles) · settle 4.4–5.6 (fish regroup) · tap the fifth card 5.8 (fish gather round it) ·
 scroll back 6.6–8.2 · rest. A touch (drag or tap) takes over at once; 2 s after the last one the
 list scrolls home and the fish swim back to their places (0.9 s), and the loop restarts.
 Reduced motion: no loop, fish hold still, no bubbles; the list scrolls as usual.

 Drawing: the water is a gradient; on it a Canvas with the fish (paths rebuilt each frame into
 reused Paths) and the bubbles; over that the LazyColumn of glass cards, so the fish pass behind the
 glass and never over the text; and on top a second Canvas (no touch) with the light — an AGSL
 shader whose alpha is its brightness, transparent where none falls (drifting rays from a baked
 bitmap below Android 13) — and the edge fades.
*/

private const val MODE_AUTO = 0
private const val MODE_USER = 1
private const val MODE_RETURN = 2
private const val IDLE = 2.0
private const val RETURN = 0.9
private const val HEADER = 150f

private val Water = arrayOf(Color(0xFF2C97A8), Color(0xFF177587), Color(0xFF0D5367), Color(0xFF083A4B))
/** Draw the far fish first. */
private val ORDER = AqFishes.indices.sortedBy { AqFishes[it].near }.toIntArray()

/** Which card the fish are feeding at, how strongly (0–1), and its press pulse (0–1); read in draw lambdas only. */
internal class AqFocus {
    val card = mutableIntStateOf(-1)
    val amt = mutableFloatStateOf(0f)
    val press = mutableFloatStateOf(0f)
}

private class AqState(private val reduced: Boolean) {
    var mode = MODE_AUTO
    var clock = 0.0
    var now = 0.0
    var lastTouch = 0.0
    var lastScroll = 0.0
    var dragging = false
    var tau = 0.0
    var stir = 0.0
    var dir = 1.0
    var feedAt = -10.0
    var feedCard = -1
    var returnAt = 0.0
    var returnScroll = 0.0
    val focus = AqFocus()
    val frame = mutableIntStateOf(0)

    // What is drawn.
    val fx = FloatArray(AqFishes.size)
    val fy = FloatArray(AqFishes.size)
    val frot = FloatArray(AqFishes.size)
    val fsx = FloatArray(AqFishes.size)
    val bodies = Array(AqFishes.size) { Path() }
    val bands = Array(AqFishes.size) { Path() }
    val rings = Path()
    val glints = Path()
    var ph = 0f
    var shownStir = 0f
    var scroll = 0f
    private val scratch = AqBodyScratch()
    private val a = DoubleArray(2)
    private val b = DoubleArray(2)
    private val r = DoubleArray(2)

    fun touched() {
        lastTouch = now
        if (mode == MODE_AUTO) {
            // Keep the fish where the loop had them: same fish-time, same stir and feeding.
            val t = clock
            tau = t
            stir = aqAutoStir(t)
            dir = aqAutoDir(t)
            if (t > AQ_TAP_AT - 0.2 && t < 7.6) {
                feedAt = now - (t - AQ_TAP_AT)
                feedCard = AQ_TAP_CARD
            } else {
                feedCard = -1
            }
        }
        mode = MODE_USER
    }

    fun tapCard(i: Int) {
        touched()
        feedAt = now
        feedCard = i
    }

    /** User feeding: up over 0.8 s from the tap, held, gone 3.9 s after it. */
    private fun userFeed(at: Double): Double {
        if (feedCard < 0) return 0.0
        val k = at - feedAt
        return aqEaseInOutCubic(k / 0.8) * (1 - aqEaseInOutCubic((k - 3) / 0.9))
    }

    /**
     * One frame at scroll [sy] (dp). Returns the scroll the list must be moved to (dp), or NaN to
     * leave it where the finger put it.
     */
    fun step(dt: Double, auto: Boolean, sy: Double, scrolling: Boolean, w: Float, listTop: Float, bottomY: Float, area: AqArea): Double {
        val at = now + dt
        now = at
        val v = (sy - lastScroll) / max(dt, 0.001)
        if (dragging && abs(sy - lastScroll) > 0.01) lastTouch = at
        lastScroll = sy
        if (dragging && !scrolling) {
            // The fling has ended.
            dragging = false
            lastTouch = at
        }

        val ft: Double
        val st: Double
        val dr: Double
        val fd: Double
        val card: Int
        val press: Double
        var blend = 0.0
        var sc = sy
        var moveTo = Double.NaN
        if (mode == MODE_AUTO) {
            val t = if (auto) (clock + dt) % LOOP else 0.0
            clock = t
            ft = t
            st = aqAutoStir(t)
            dr = aqAutoDir(t)
            fd = aqAutoFeed(t)
            card = AQ_TAP_CARD
            press = sin(PI * min(1.0, max(0.0, (t - AQ_TAP_AT) / 0.18)))
            sc = if (auto) aqAutoScroll(t) else 0.0
            if (auto) moveTo = sc
        } else {
            // Under touch the fish keep their own clock and feel the real scroll.
            ft = (tau + (if (reduced) 0.0 else dt)) % LOOP
            tau = ft
            val target = if (reduced) 0.0 else min(1.0, abs(v) / 1400)
            stir += (target - stir) * min(1.0, (if (target > stir) 6.0 else 1.4) * dt)
            st = stir
            if (abs(v) > 60) dir = if (v > 0) 1.0 else -1.0
            dr = dir
            fd = userFeed(at)
            card = feedCard
            press = if (card >= 0) sin(PI * min(1.0, max(0.0, (at - feedAt) / 0.18))) else 0.0
            if (mode == MODE_USER && auto && !dragging && at - lastTouch > IDLE) {
                returnAt = at
                returnScroll = sy
                mode = MODE_RETURN
            }
            if (mode == MODE_RETURN) {
                blend = aqEaseInOutCubic((at - returnAt) / RETURN)
                sc = returnScroll * (1 - blend)
                moveTo = sc
                if (blend >= 1) {
                    mode = MODE_AUTO
                    clock = 0.0
                    stir = 0.0
                    feedCard = -1
                }
            }
        }
        if (focus.card.intValue != card) focus.card.intValue = card
        focus.amt.floatValue = fd.toFloat()
        focus.press.floatValue = press.toFloat()

        // The fed card's centre on screen (fish circle it, and follow it if the list moves).
        val fc = if (card >= 0) card else AQ_TAP_CARD
        val tx = w / 2.0
        val ty = listTop + 16 + fc * AQ_ROW + AQ_CARD_H / 2 - sc
        val h = 1 / 60.0
        for (i in AqFishes.indices) {
            val f = AqFishes[i]
            aqFishAt(i, ft, area, st, dr, fd, tx, ty, a)
            aqFishAt(i, ft - h, area, st, dr, fd, tx, ty, b)
            var x = a[0]
            var y = a[1]
            var vx = (a[0] - b[0]) / h
            var vy = (a[1] - b[1]) / h
            if (blend > 0) {
                aqFishAt(i, 0.0, area, 0.0, 1.0, 0.0, tx, ty, r)
                x += (r[0] - x) * blend
                y += (r[1] - y) * blend
                aqFishAt(i, -h, area, 0.0, 1.0, 0.0, tx, ty, b)
                vx += ((r[0] - b[0]) / h - vx) * blend
                vy += ((r[1] - b[1]) / h - vy) * blend
            }
            // Facing: the body narrows to edge-on as the fish turns round (tanh of its sideways speed).
            val sx = tanh(vx / 22)
            val rot = max(-0.45, min(0.45, atan2(vy, abs(vx) + 12) * 0.7)) * (if (vx >= 0) 1 else -1)
            val phase = (2 * PI * f.beats * ft / LOOP).toFloat()
            val amp = if (reduced) 0f else (0.16 + 0.24 * st + 0.1 * fd).toFloat()
            fx[i] = x.toFloat()
            fy[i] = y.toFloat()
            frot[i] = rot.toFloat()
            fsx[i] = (if (abs(sx) < 0.08) (if (sx < 0) -0.08 else 0.08) else sx).toFloat()
            aqFishBody(f, phase, amp, bodies[i], scratch)
            aqFishBands(f, phase, amp, bands[i])
        }
        aqBubbles(ft, if (reduced) 0.0 else st, w, listTop, bottomY - 10, rings, glints)
        ph = (2 * PI * ft / LOOP).toFloat()
        shownStir = st.toFloat()
        scroll = sc.toFloat()
        frame.intValue += 1
        return moveTo
    }

    /** Whether anything still moves without a touch (the loop off, reduced motion or no autoPlay). */
    fun busy(): Boolean =
        (mode != MODE_AUTO && !reduced) || stir > 0.001 || focus.amt.floatValue > 0.001f || focus.press.floatValue > 0.001f || dragging
}

/** Paints for the fish and bubbles, made once. */
private class AqPaints {
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb((0.28f * 255).roundToInt(), 2, 18, 26)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter((6f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
    }
    val bodies = AqFishes.map { f ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, -f.dep / 2, 0f, f.dep / 2, f.colors, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
    }
    val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((0.92f * 255).roundToInt(), 255, 250, 240) }
    val eyeWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4F8F2.toInt() }
    val eyeDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B1A20.toInt() }
    val ringStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = android.graphics.Color.argb((0.6f * 255).roundToInt(), 214, 246, 255)
    }
    val ringFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((0.1f * 255).roundToInt(), 190, 236, 250) }
    val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((0.75f * 255).roundToInt(), 255, 255, 255) }
    val oval = RectF()
    val bounds = RectF()
}

/** The absolute scroll of a list of fixed rows, in px. */
private fun LazyListState.absolute(rowPx: Int) = firstVisibleItemIndex * rowPx + firstVisibleItemScrollOffset

/**
 * "Reef Log · Tank 2": a reef keeper's log floating in the tank, fish swimming behind the glass
 * cards. Scroll to stir the water; tap an entry and the fish gather round it.
 *
 * @param autoPlay scrolls the log, stirs the fish, taps an entry so they gather, scrolls back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AquariumFeedList(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val density = LocalDensity.current
    val top = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.getTop(density),
        WindowInsets.displayCutout.getTop(density),
    ) / density.density
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density) / density.density
    val state = remember(reduced) { AqState(reduced) }
    val list = rememberLazyListState()
    val running = remember { mutableStateOf(true) }
    val wake = { running.value = true }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to Water[0], 0.35f to Water[1], 0.7f to Water[2], 1f to Water[3])),
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val listTop = top + HEADER
        val area = remember(w, h, listTop, bottom) { AqArea(22.0, listTop + 8.0, w - 44.0, (h - bottom - 36 - (listTop + 8)).toDouble()) }
        val bottomY = h - bottom
        val cardPx = with(density) { AQ_CARD_H.dp.roundToPx() }
        val rowPx = cardPx + with(density) { AQ_CARD_GAP.dp.roundToPx() }

        // A drag on the list takes over from the loop; the fling's end is seen by the frame loop.
        LaunchedEffect(list, state) {
            list.interactionSource.interactions.collect { i ->
                when (i) {
                    is DragInteraction.Start -> {
                        state.touched()
                        state.dragging = true
                        wake()
                    }
                    is DragInteraction.Stop, is DragInteraction.Cancel -> state.lastTouch = state.now
                }
            }
        }
        // Scrolling wakes a stopped frame loop.
        LaunchedEffect(list) {
            snapshotFlow { list.absolute(rowPx) }.collect { wake() }
        }

        val isRunning by running
        LaunchedEffect(state, auto, isRunning, area, bottomY, rowPx) {
            if (!isRunning) return@LaunchedEffect
            var last = -1L
            while (true) {
                val keep = withFrameNanos { t ->
                    val dt = if (last < 0) 1.0 / 60 else min((t - last) / 1e9, 0.05)
                    last = t
                    val d = density.density
                    val sy = list.absolute(rowPx) / d.toDouble()
                    val moveTo = state.step(dt, auto, sy, list.isScrollInProgress, w, listTop, bottomY, area)
                    if (!moveTo.isNaN()) {
                        val delta = (moveTo * d).toFloat() - list.absolute(rowPx)
                        if (abs(delta) > 0.5f) list.dispatchRawDelta(delta)
                    }
                    auto || state.busy() || list.isScrollInProgress
                }
                if (!keep) {
                    running.value = false
                    break
                }
            }
        }

        // Behind the cards' glass: the fish and the bubbles.
        val paints = remember { AqPaints() }
        Canvas(Modifier.fillMaxSize()) {
            state.frame.intValue
            val d = density.density
            scale(d, d, pivot = Offset.Zero) {
                drawIntoCanvas { canvas -> drawFish(canvas.nativeCanvas, state, paints) }
            }
        }

        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = (listTop + 16).dp, bottom = (bottom + 48).dp),
            verticalArrangement = Arrangement.spacedBy(AQ_CARD_GAP.dp),
        ) {
            items(AqEntries.size, key = { it }, contentType = { "log" }) { i ->
                AquariumCard(i, AqEntries[i], state.focus) { index ->
                    state.tapCard(index)
                    wake()
                }
            }
        }

        // Over everything: the light (transparent where none falls) and the fades into water.
        AqLight(state, w, h, listTop, bottomY, density)

        // Header.
        Column(
            Modifier
                .fillMaxWidth()
                .height((listTop - 18).dp)
                .background(Brush.verticalGradient(0f to Color(0xFF2C97A8), 0.75f to Color(0xFF1F8497), 1f to Color(0x001E8295)))
                .padding(start = 22.dp, end = 22.dp, top = (top + 14).dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            BasicText("REEF LOG", style = TextStyle(color = Color(0xFF8FE3F0), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp))
            BasicText("Tank 2", style = TextStyle(color = Color(0xFFEAF9FB), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp))
            BasicText("220 L mixed reef · 6 fish · 14 corals", style = TextStyle(color = Color(0xA8DEF6FA), fontSize = 15.sp, fontWeight = FontWeight.Medium))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in listOf("25.6 °C", "pH 8.2", "SG 1.025", "NO₃ 5 ppm")) {
                    BasicText(
                        s,
                        modifier = Modifier
                            .background(Color(0x1FBEF0FA), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        style = TextStyle(color = Color(0xFFDDF6FA), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
    }
}

private fun drawFish(c: android.graphics.Canvas, s: AqState, p: AqPaints) {
    for (i in ORDER) {
        val f = AqFishes[i]
        c.save()
        c.translate(s.fx[i], s.fy[i])
        c.rotate(s.frot[i] * 180f / PI.toFloat())
        c.scale(s.fsx[i] * f.near, f.near)
        p.bounds.set(-f.len - 20, -f.dep * 1.3f - 10, f.len * 0.7f + 20, f.dep * 1.5f + 24)
        c.saveLayerAlpha(p.bounds, ((0.6f + 0.4f * f.near) * 255).roundToInt())
        p.oval.set(-f.len * 0.42f, f.dep * 0.9f, f.len * 0.48f, f.dep * 1.4f)
        c.drawOval(p.oval, p.shadow)
        c.drawPath(s.bodies[i], p.bodies[i])
        if (f.bands.isNotEmpty()) c.drawPath(s.bands[i], p.band)
        c.drawCircle(f.len * 0.31f, -f.dep * 0.06f, f.dep * 0.11f, p.eyeWhite)
        c.drawCircle(f.len * 0.32f, -f.dep * 0.06f, f.dep * 0.07f, p.eyeDark)
        c.restore()
        c.restore()
    }
    c.drawPath(s.rings, p.ringStroke)
    c.drawPath(s.rings, p.ringFill)
    c.drawPath(s.glints, p.glint)
}

/** The light over the screen and the edge fades; the shader below Android 13 is replaced by baked rays. */
@Composable
private fun AqLight(state: AqState, w: Float, h: Float, listTop: Float, bottomY: Float, density: Density) {
    val shader = remember(w, h, listTop) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                RuntimeShader(AQUARIUM_LIGHT_AGSL).apply {
                    setFloatUniform("size", w, h)
                    setFloatUniform("listTop", listTop)
                    setFloatUniform("row", AQ_ROW)
                    setFloatUniform("cardH", AQ_CARD_H)
                    setFloatUniform("cards", AqEntries.size.toFloat())
                }
            }.getOrNull()
        } else {
            null
        }
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val fallback = remember(w, h) { if (shader == null) AquariumFeedListRays(w, h) else null }
    val topFade = remember(listTop) {
        Brush.verticalGradient(0f to Color(0xFF1E8295), 1f to Color(0x001E8295), startY = (listTop - 30) * density.density, endY = (listTop + 40) * density.density)
    }
    val bottomFade = remember(bottomY, h) {
        Brush.verticalGradient(
            0f to Color(0x00083A4B), 0.7f to Color(0xE6083A4B), 1f to Color(0xFF083A4B),
            startY = (bottomY - 90) * density.density, endY = h * density.density,
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        state.frame.intValue
        val d = density.density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
            shader.setFloatUniform("ph", state.ph)
            shader.setFloatUniform("stir", state.shownStir)
            shader.setFloatUniform("scroll", state.scroll)
            scale(d, d, pivot = Offset.Zero) { drawRect(brush, Offset.Zero, Size(w, h)) }
        } else if (fallback != null) {
            scale(d, d, pivot = Offset.Zero) {
                drawIntoCanvas { fallback.draw(it.nativeCanvas, state.ph) }
            }
        }
        drawRect(topFade, Offset(0f, (listTop - 30) * d), Size(size.width, 70 * d))
        drawRect(bottomFade, Offset(0f, (bottomY - 90) * d), Size(size.width, (h - bottomY + 90) * d))
    }
}

/**
 * Below Android 13: the shader's god rays (slanted soft bands from the surface, fading with depth)
 * baked once at a quarter of the size and drifted sideways with the loop. No caustic net.
 */
private class AquariumFeedListRays(private val w: Float, private val h: Float) {
    private val q = 4f
    private val bitmap: android.graphics.Bitmap
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    init {
        val bw = ((w + 120) / q).toInt().coerceAtLeast(1)
        val bh = (h / q).toInt().coerceAtLeast(1)
        val px = IntArray(bw * bh)
        for (yy in 0 until bh) {
            val y = yy * q
            val depth = (y / h).coerceIn(0f, 1f)
            val fade = kotlin.math.exp(-depth * 2.4f) * 0.16f
            for (xx in 0 until bw) {
                val x = xx * q - 60 + y * 0.32f
                var rays = 0.5f + 0.5f * sin(x * 0.021f) * sin(x * 0.047f + 1.3f)
                rays = rays * rays * rays * fade
                val r = (0.7f * rays * 255).roundToInt().coerceIn(0, 255)
                val g = (0.95f * rays * 255).roundToInt().coerceIn(0, 255)
                val b = (rays * 255).roundToInt().coerceIn(0, 255)
                val a = max(r, max(g, b))
                // Premultiplied light: alpha equals its brightest channel.
                px[yy * bw + xx] = android.graphics.Color.argb(a, if (a == 0) 0 else r * 255 / a, if (a == 0) 0 else g * 255 / a, if (a == 0) 0 else b * 255 / a)
            }
        }
        bitmap = android.graphics.Bitmap.createBitmap(px, bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
    }

    fun draw(c: android.graphics.Canvas, ph: Float) {
        val sway = 30f * sin(ph)
        dst.set(-60 + sway, 0f, w + 60 + sway, h)
        c.drawBitmap(bitmap, null, dst, paint)
    }
}
