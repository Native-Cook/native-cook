package com.ngocdevv.nativecook.blocks.lists.windchimelist

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/*
 Wind Chime List — a sound-bath app's sessions as a hanging wind chime (a port of WindChimeList.tsx):
 every row is a brass or bamboo tube on two strings, hanging from the one above, the first from an
 oak beam under the title. Scrolling swings them: each tube is a damped pendulum driven by the
 list's acceleration, 40 ms later per row and at its own pitch, neighbours in opposite directions;
 when two swing far enough apart they touch and a ring of light spreads from the point of contact.
 Tapping a tube strikes it: it swings hard and its waveform plays.

 A LazyColumn with fixed row heights; rows never recompose per frame: their swing, ring and glow
 are read from one frame float inside graphicsLayer / draw lambdas. Tube shadows are one blurred
 capsule bitmap drawn in three slices. The large title scrolls away and a compact bar fades in;
 both edges fade.
*/

private val BG = listOf(Color(0xFF13262A), Color(0xFF0F1D20), Color(0xFF0A1315))
private val COUNT = ChimeSessions.size
private const val IDLE = 2f
private const val RETURN_LEN = 0.8f
/** How strongly the list's acceleration (dp/s²) kicks a tube (rad/s² per dp/s²). */
private const val GAIN = 0.0018f
private const val MAX_SWING = 0.6f
private const val BARS = 16

private val BRASS = listOf(0xFF4F3512, 0xFFA87A35, 0xFFF1D690, 0xFFD8AD5C, 0xFF8A6025, 0xFF3A2709).map { Color(it) }
private val BAMBOO = listOf(0xFF3F4A1E, 0xFF7F8F45, 0xFFCFD68F, 0xFFA7B064, 0xFF5E6A2C, 0xFF2D3513).map { Color(it) }
private val STOPS = floatArrayOf(0f, 0.16f, 0.34f, 0.52f, 0.82f, 1f)

private fun soft(x: Float) = MAX_SWING * tanh(x / MAX_SWING)

/** The oscillators a real scroll drives. */
private class ChimeSim {
    val th = FloatArray(COUNT)
    val w = FloatArray(COUNT)
    val touchAt = FloatArray(COUNT) { -9f }
    val was = BooleanArray(COUNT)
    val side = FloatArray(COUNT) { 1f }
    val hist = ArrayDeque<Float>()
    val histT = ArrayDeque<Float>()
    var lastY = 0f
    var vs = 0f

    fun accelAgo(now: Float, delay: Float): Float {
        val want = now - delay
        for (k in histT.indices.reversed()) if (histT[k] <= want) return hist[k]
        return 0f
    }
}

private class ChimeState(val reduced: Boolean, val auto: Boolean) {
    val frame = mutableFloatStateOf(0f)
    val mode = mutableIntStateOf(0)
    /** The tube whose waveform plays (changes at most twice a loop). */
    val playingRow = mutableIntStateOf(-1)
    var clock = 0f
    var now = 0f
    var lastTouch = 0f
    var dragging = false
    var returnFrom = 0f
    var returnAt = 0f
    var sim: ChimeSim? = null
    /** Per row: swing angle (rad); per row i: [seconds since it touched row i + 1 (−1 none), side]. */
    val swing = FloatArray(COUNT)
    val rings = FloatArray(COUNT * 2) { -1f }
    var playRow = -1
    var playTau = 0f
    var strikeAt = -9f
    var strikeRow = -1
}

/** A capsule's two soft shadows (0 2 3 / 0 14 26 in CSS), blurred once and drawn in three slices. */
private class ChimeShadow(px: Float) {
    private val margin = 56f
    private val w0 = CHIME_TUBE_H * 2 + 4
    private val bmp: Bitmap
    private val scale = px
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        val bw = ceil((w0 + margin * 2) * px).toInt()
        val bh = ceil((CHIME_TUBE_H + margin * 2) * px).toInt()
        bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.scale(px, px)
        c.translate(margin, margin)
        val r = CHIME_TUBE_H / 2
        fun layer(dy: Float, sigma: Float, a: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (((a * 255).roundToInt()) shl 24)
                maskFilter = BlurMaskFilter(max(0.5f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
            }
            c.drawRoundRect(RectF(0f, dy, w0, dy + CHIME_TUBE_H), r, r, p)
        }
        layer(2f, 1.5f, 0.45f)
        layer(14f, 13f, 0.38f)
        bmp.prepareToDraw()
    }

    /** The shadow of a tube w dp wide whose top-left is at (x, y). */
    fun draw(c: android.graphics.Canvas, x: Float, y: Float, w: Float) {
        val half = ((margin + w0 / 2) * scale).toInt()
        val bw = bmp.width
        val bh = bmp.height
        val top = y - margin
        val bottom = y + CHIME_TUBE_H + margin
        val lw = half / scale
        c.drawBitmap(bmp, Rect(0, 0, half, bh), RectF(x - margin, top, x - margin + lw, bottom), paint)
        val midL = x - margin + lw
        val midR = x + w + margin - lw
        if (midR > midL) c.drawBitmap(bmp, Rect(half, 0, half + 1, bh), RectF(midL, top, midR, bottom), paint)
        c.drawBitmap(bmp, Rect(bw - half, 0, bw, bh), RectF(max(midL, midR), top, x + w + margin, bottom), paint)
    }
}

/** The list's scroll offset in dp, from fixed item heights. */
private fun absScroll(list: LazyListState, headerH: Float, d: Float): Float {
    val i = list.firstVisibleItemIndex
    val o = list.firstVisibleItemScrollOffset / d
    return if (i == 0) o else headerH + (i - 1) * CHIME_ROW_H + o
}

/**
 * A sound-bath app's sessions hanging as a wind chime: scrolling swings the tubes, neighbours
 * touch and ring, and tapping a tube strikes it and plays its waveform.
 *
 * @param autoPlay flings the list down, strikes a tube and scrolls back, every LOOP seconds.
 */
@Composable
fun WindChimeList(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val state = remember(reduced, auto) { ChimeState(reduced, auto) }
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val insetTop = insets.getTop(density) / density.density
    val insetBottom = insets.getBottom(density) / density.density
    val d = density.density
    val list = rememberLazyListState()
    val headerH = insetTop + 168f

    BoxWithConstraints(modifier.fillMaxSize().background(BG[1])) {
        val W = maxWidth.value
        val H = maxHeight.value
        val grain by produceState<Bitmap?>(null, W, H, d) {
            value = withContext(Dispatchers.Default) {
                val bmp = Bitmap.createBitmap(max(1, (W * d).toInt()), max(1, (H * d).toInt()), Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp)
                c.scale(d, d)
                c.drawRect(0f, 0f, W, H, Paint().apply {
                    shader = RadialGradient(W * 0.5f, H * 0.12f, W * 0.9f, intArrayOf(0x2478BEB4, 0x0878BEB4, 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                })
                val dot = Paint().apply { color = 0x08DCF0EB }
                for (i in 0 until 1500) c.drawCircle(chimeHash(i * 1.3f) * W, chimeHash(i * 2.9f + 4f) * H, 0.6f, dot)
                c.drawRect(0f, 0f, W, H, Paint().apply {
                    shader = RadialGradient(W / 2, H * 0.45f, H * 0.75f, intArrayOf(0, 0x33000000, 0x8C000000.toInt()), floatArrayOf(0.45f, 0.78f, 1f), Shader.TileMode.CLAMP)
                })
                bmp.prepareToDraw()
                bmp
            }
        }
        val shadow by produceState<ChimeShadow?>(null, d) { value = withContext(Dispatchers.Default) { ChimeShadow(d) } }

        fun takeOver() {
            state.lastTouch = state.now
            if (state.mode.intValue == 1) return
            val s = ChimeSim()
            val t = state.clock
            val fromLoop = state.mode.intValue == 0 && state.auto
            for (i in 0 until COUNT) {
                s.th[i] = if (fromLoop) chimeSwingAt(t, i) else state.swing[i]
                s.w[i] = if (fromLoop) (chimeSwingAt(t, i) - chimeSwingAt(max(0f, t - 1f / 120f), i)) * 120f else 0f
            }
            s.lastY = absScroll(list, headerH, d)
            state.sim = s
            if (fromLoop && state.strikeRow < 0 && t > CHIME_STRIKE_AT && t < CHIME_STRIKE_AT + CHIME_PLAY_LEN) {
                state.strikeRow = CHIME_STRIKE_ROW
                state.strikeAt = state.now - (t - CHIME_STRIKE_AT)
            }
            state.mode.intValue = 1
        }

        fun strike(i: Int) {
            takeOver()
            val s = state.sim ?: return
            if (!state.reduced) s.w[i] += chimeDir(i) * 0.32f * chimeOmega(i)
            state.strikeRow = i
            state.strikeAt = state.now
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        // A drag on the list takes over from the loop.
        LaunchedEffect(list, state) {
            list.interactionSource.interactions.collect { inter ->
                when (inter) {
                    is DragInteraction.Start -> { state.dragging = true; takeOver() }
                    is DragInteraction.Stop, is DragInteraction.Cancel -> { state.dragging = false; state.lastTouch = state.now }
                }
            }
        }

        LaunchedEffect(state, headerH) {
            var last = -1L
            while (true) {
                if (state.mode.intValue == 0 && !state.auto) {
                    snapshotFlow { state.mode.intValue }.first { it != 0 }
                    last = -1L
                }
                withFrameNanos { tn ->
                    val dt = if (last < 0) 1f / 60f else min((tn - last) / 1e9f, 0.05f)
                    last = tn
                    val n = state.now + dt
                    state.now = n
                    val m = state.mode.intValue
                    if (m == 0) {
                        val t = (state.clock + dt) % LOOP
                        state.clock = t
                        list.dispatchRawDelta((chimeScrollAt(t) - absScroll(list, headerH, d)) * d)
                        for (i in 0 until COUNT) state.swing[i] = chimeSwingAt(t, i)
                        for (i in 0 until COUNT - 1) chimeRingAt(t, i, state.rings, i * 2)
                        val playing = t >= CHIME_STRIKE_AT && t < CHIME_STRIKE_AT + CHIME_PLAY_LEN
                        state.playRow = if (playing) CHIME_STRIKE_ROW else -1
                        state.playTau = if (playing) t - CHIME_STRIKE_AT else 0f
                        if (state.playingRow.intValue != state.playRow) state.playingRow.intValue = state.playRow
                        state.frame.floatValue = t
                        return@withFrameNanos
                    }
                    val s = state.sim ?: return@withFrameNanos
                    if (m == 1 && list.isScrollInProgress) state.lastTouch = n
                    // The list's speed and acceleration, smoothed, with a short history for the per-row delay.
                    val y = absScroll(list, headerH, d)
                    val v = (y - s.lastY) / max(dt, 1e-3f)
                    s.lastY = y
                    val vs = s.vs + (v - s.vs) * 0.35f
                    val a = ((vs - s.vs) / max(dt, 1e-3f)).coerceIn(-9000f, 9000f)
                    s.vs = vs
                    s.hist.addLast(a)
                    s.histT.addLast(n)
                    while (s.histT.isNotEmpty() && s.histT.first() < n - COUNT * CHIME_ROW_DELAY - 0.1f) {
                        s.hist.removeFirst()
                        s.histT.removeFirst()
                    }
                    val zeta = if (m == 2) 0.7f else 0.22f
                    var energy = 0f
                    for (i in 0 until COUNT) {
                        val w0 = chimeOmega(i)
                        val z = if (i == state.strikeRow && n - state.strikeAt < CHIME_PLAY_LEN && m == 1) 0.12f else zeta
                        val drive = if (m == 2 || state.reduced) 0f else 1f
                        val acc = -w0 * w0 * s.th[i] - 2 * z * w0 * s.w[i] + GAIN * chimeDir(i) * s.accelAgo(n, i * CHIME_ROW_DELAY) * drive
                        s.w[i] += acc * dt
                        s.th[i] += s.w[i] * dt
                        state.swing[i] = soft(s.th[i])
                        energy = max(energy, abs(s.th[i]) + abs(s.w[i]) * 0.1f)
                    }
                    for (i in 0 until COUNT - 1) {
                        val dd = state.swing[i] - state.swing[i + 1]
                        val touching = abs(dd) > CHIME_TOUCH
                        if (touching && !s.was[i]) {
                            s.touchAt[i] = n
                            s.side[i] = if (dd > 0f) 1f else -1f
                        }
                        s.was[i] = touching
                        val age = n - s.touchAt[i]
                        state.rings[i * 2] = if (age <= CHIME_RING_LEN) age else -1f
                        state.rings[i * 2 + 1] = s.side[i]
                    }
                    val sr = state.strikeRow
                    val playing = sr >= 0 && n - state.strikeAt < CHIME_PLAY_LEN
                    state.playRow = if (playing) sr else -1
                    state.playTau = if (playing) n - state.strikeAt else 0f
                    if (state.playingRow.intValue != state.playRow) state.playingRow.intValue = state.playRow

                    if (m == 1 && !state.dragging && n - state.lastTouch > IDLE) {
                        state.mode.intValue = 2
                        state.returnAt = n
                        state.returnFrom = y
                        state.strikeRow = -1
                    }
                    if (m == 2) {
                        val u = min(1f, (n - state.returnAt) / RETURN_LEN)
                        list.dispatchRawDelta((state.returnFrom * (1 - chimeEaseInOutCubic(u)) - y) * d)
                        if (u >= 1f && energy < 0.01f) {
                            state.clock = 0f
                            state.mode.intValue = 0
                        }
                    }
                    state.frame.floatValue = n
                }
            }
        }

        // Background: gradient and the grain.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(BG)))
        Canvas(Modifier.fillMaxSize()) {
            grain?.let { g -> drawIntoCanvas { it.nativeCanvas.drawBitmap(g, null, RectF(0f, 0f, size.width, size.height), null) } }
        }

        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = (insetBottom + 72).dp),
        ) {
            item(key = "header", contentType = "header") {
                ChimeHeader(insetTop, W, list, d)
            }
            itemsIndexed(ChimeSessions, key = { _, s -> s.title }, contentType = { _, _ -> "tube" }) { i, s ->
                ChimeRow(s, i, W, state, shadow, state.playingRow.intValue == i) { strike(i) }
            }
        }

        // Edge fades and the compact bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height((90 + insetBottom).dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color(0x000A1315), Color(0xEB0A1315)))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height((insetTop + 48).dp)
                .graphicsLayer { alpha = chimeClamp01((absScroll(list, headerH, d) - 60f) / 50f) }
                .background(Color(0xE60F1D20))
                .drawBehind { drawRect(Color(0x26EEF3EE), Offset(0f, size.height - 0.5f * d), Size(size.width, 0.5f * d)) },
        ) {
            BasicText(
                "Sound baths",
                modifier = Modifier.fillMaxWidth().padding(top = insetTop.dp).height(48.dp).padding(top = 14.dp),
                style = TextStyle(color = Color(0xFFEEF3EE), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height((insetTop + 12).dp)
                .background(Brush.verticalGradient(listOf(Color(0xE60F1D20), Color(0x000F1D20)))),
        )
    }
}

/** Large title and the oak beam the chime hangs from; the title scrolls away with a little parallax. */
@Composable
private fun ChimeHeader(insetTop: Float, W: Float, list: LazyListState, d: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height((insetTop + 168).dp),
    ) {
        Column(
            Modifier
                .padding(start = 24.dp, end = 24.dp, top = (insetTop + 20).dp)
                .graphicsLayer {
                    val y = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset / d else 999f
                    alpha = 1f - chimeClamp01(y / 90f)
                    translationY = min(40f, y / 3f) * d
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("STILLWATER", style = TextStyle(color = Color(0xFFD9B56A), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("Sound baths", style = TextStyle(color = Color(0xFFEEF3EE), fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
            BasicText("14 sessions · tuned to 432 Hz · tap a tube to hear it", style = TextStyle(color = Color(0x99EEF3EE), fontSize = 15.sp))
        }
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp)
                .width((W - 32).dp)
                .height(18.dp)
                .cssBoxShadow(Color(0x73000000), 2.dp, 3.dp, 6.dp)
                .cssBoxShadow(Color(0x59000000), 12.dp, 24.dp, 6.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF3C2412), 0.2f to Color(0xFF8A5A31), 0.45f to Color(0xFFB98352), 0.75f to Color(0xFF7A4B26), 1f to Color(0xFF2E1A0B),
                    ),
                )
                .drawWithContent {
                    drawContent()
                    for (x in floatArrayOf(0.12f, 0.31f, 0.55f, 0.78f)) {
                        drawRect(Color(0x59281406), Offset(size.width * x, 3.dp.toPx()), Size(40.dp.toPx(), 1.dp.toPx()))
                    }
                    drawRect(Color(0x59FFDCAA), Offset(6.dp.toPx(), 0f), Size(size.width - 12.dp.toPx(), 1.dp.toPx()))
                },
        )
    }
}

/** One session: a tube on two strings, swinging about the strings' top, with its contact ring. */
@Composable
private fun ChimeRow(item: ChimeSession, index: Int, W: Float, state: ChimeState, shadow: ChimeShadow?, playing: Boolean, onStrike: () -> Unit) {
    val tubeW = W - 32f - item.shrink * 2
    val ink = if (item.brass) Color(0xFF2A1A07) else Color(0xFF1D2408)
    val bars = remember(item.seed) { FloatArray(BARS) { chimeBarHeight(item.seed, it) } }
    val tubeX = (W - tubeW) / 2
    Box(Modifier.fillMaxWidth().height(CHIME_ROW_H.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    state.frame.floatValue
                    val a = state.swing[index]
                    translationX = CHIME_SWAY * a * density
                    rotationZ = CHIME_VISUAL_TURN * a * 180f / Math.PI.toFloat()
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .drawBehind {
                    // Strings, then the tube's shadow (one blurred capsule, three slices).
                    val sw = 1.2f * density
                    for (f in floatArrayOf(0.24f, 0.76f)) {
                        drawRect(Color(0x8CE2CEA0), Offset((tubeX + tubeW * f) * density, -2f * density), Size(sw, (CHIME_STRING_H + 4) * density))
                    }
                    shadow?.let { sh ->
                        drawIntoCanvas { c ->
                            val nc = c.nativeCanvas
                            nc.save()
                            nc.scale(density, density)
                            sh.draw(nc, tubeX, CHIME_STRING_H, tubeW)
                            nc.restore()
                        }
                    }
                },
        ) {
            val shape = RoundedCornerShape(50)
            Row(
                Modifier
                    .offset(x = tubeX.dp, y = CHIME_STRING_H.dp)
                    .size(tubeW.dp, CHIME_TUBE_H.dp)
                    .clip(shape)
                    .drawWithContent {
                        val colors = if (item.brass) BRASS else BAMBOO
                        drawRect(Brush.verticalGradient(*Array(6) { STOPS[it] to colors[it] }))
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color(0x61000000), 0.07f to Color(0x00000000), 0.5f to Color(0x0FFFFFFF), 0.93f to Color(0x00000000), 1f to Color(0x61000000),
                            ),
                        )
                        if (!item.brass) {
                            for (f in floatArrayOf(0.36f, 0.7f)) {
                                val x = size.width * f
                                drawRect(Color(0x5928300C), Offset(x, 0f), Size(3.dp.toPx(), size.height))
                                drawRect(Color(0x33FFFFDC), Offset(x + 2.dp.toPx(), 0f), Size(1.dp.toPx(), size.height))
                            }
                        }
                        // inset 0 1px 0 rgba(255,250,225,0.5)
                        drawRect(Color(0x80FFFAE1), Offset(size.height / 2, 0f), Size(size.width - size.height, 1.dp.toPx()))
                        val glow = if (playing) { state.frame.floatValue; 0.55f * chimePlayEnvelope(state.playTau) } else 0f
                        if (glow > 0.002f) drawRect(Color(0x8CFFF0BE).copy(alpha = 0.55f * glow))
                        drawContent()
                    }
                    .pointerInput(index) { detectTapGestures { onStrike() } }
                    .semantics {
                        role = Role.Button
                        contentDescription = "${item.title}, ${item.minutes} minutes, ${item.meta}"
                    }
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                    BasicText(
                        "${item.minutes} min · ${item.meta}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = ink.copy(alpha = 0.72f), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                    )
                }
                // The waveform: still, or dancing on a few sines of the time since the strike.
                Canvas(Modifier.size((BARS * 2.5f + (BARS - 1) * 2.5f).dp, 40.dp)) {
                    val tau = if (playing) { state.frame.floatValue; state.playTau } else 0f
                    val env = if (playing) chimePlayEnvelope(tau) else 0f
                    val bw = 2.5f * density
                    for (k in 0 until BARS) {
                        val h = (4f + 22f * bars[k]) * density
                        val dance = 0.5f + 0.5f * abs(sin(tau * (7 + k * 0.9f) + k) * 0.7f + sin(tau * (13 + (k % 4) * 2) + k * 2.1f) * 0.3f)
                        val sy = 1f + env * (dance * 1.6f - 0.2f)
                        val hh = h * sy
                        drawRoundRect(ink.copy(alpha = 0.7f), Offset(k * 5f * density, size.height / 2 - hh / 2), Size(bw, hh), CornerRadius(1.25f * density))
                    }
                }
            }
        }
        // The ring where this tube touches the next one.
        Box(
            Modifier
                .offset(x = (W / 2 - 22).dp, y = (CHIME_ROW_H + CHIME_STRING_H / 2 - 22).dp)
                .size(44.dp)
                .graphicsLayer {
                    state.frame.floatValue
                    val age = state.rings[index * 2]
                    if (age < 0f) {
                        alpha = 0f
                    } else {
                        val u = age / CHIME_RING_LEN
                        alpha = 0.85f * (1 - u) * (1 - u)
                        translationX = state.rings[index * 2 + 1] * (tubeW / 2 - 26) * density
                        scaleX = 0.5f + 1.8f * u
                        scaleY = 0.5f + 1.8f * u
                    }
                }
                .drawBehind {
                    val r = size.width / 2
                    val c = Offset(r, r)
                    drawCircle(Brush.radialGradient(0.6f to Color(0x00FFD278), 0.82f to Color(0xCCFFD278), 1f to Color(0x00FFD278), center = c, radius = r + 14.dp.toPx()), r + 14.dp.toPx(), c)
                    drawCircle(Brush.radialGradient(0.55f to Color(0x00FFD278), 1f to Color(0x99FFD278), center = c, radius = r), r, c)
                    drawCircle(Color(0xF2FFE296), r - 0.75f * density, c, style = Stroke(1.5f * density))
                },
        )
    }
}
