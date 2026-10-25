package com.ngocdevv.nativecook.blocks.inputs.chalkboardsearch

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Chalkboard Search — a café app's menu search on a chalkboard (a port of ChalkboardSearch.tsx).
 The query is chalked onto the board as you type: each glyph's outline (nudged and tilted a little)
 filled with chalk, revealed by the pen moving through it, dust falling from under the stroke. The
 menu lines that match are chalked in below and rewritten when the query changes. A swipe across
 the board is a felt eraser: it wipes the chalk where it goes and leaves a haze that clears.

 One Canvas: the baked board bitmap, then the live chalk (one bitmap per set of lines, rendered
 when the text changes) in a layer the eraser cuts out of (DST_OUT), the dust, the haze and the
 eraser. A hidden BasicTextField over the search line takes the keyboard.
*/

private const val IDLE = 2f
private const val RETURN_LEN = 0.65f
private const val ERASER_W = 60f
private const val PH_TEXT = "search the menu…"

/** A value that tweens, stepped by the frame loop. */
private class ChalkAnim(var v: Float = 0f) {
    private var moving = false
    private var from = 0f
    private var to = 0f
    private var start = 0f
    private var dur = 0f
    private var easing: Easing = LinearEasing
    val active get() = moving
    fun snap(x: Float) { v = x; moving = false }
    fun tween(target: Float, seconds: Float, now: Float, ease: Easing = LinearEasing) {
        if (seconds <= 0f) { snap(target); return }
        from = v; to = target; start = now; dur = seconds; easing = ease; moving = true
    }
    fun step(now: Float) {
        if (!moving) return
        val u = ((now - start) / dur).coerceIn(0f, 1f)
        v = from + (to - from) * easing.transform(u)
        if (u >= 1f) { v = to; moving = false }
    }
}

private val EASE_RN = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** The query line and the three menu lines, with the pen data the frames read. */
private class ChalkLines(val query: ChalkLine, val sugs: List<ChalkLine>, val spaces: BooleanArray)

private class ChalkState(val reduced: Boolean, val auto: Boolean) {
    val frame = mutableFloatStateOf(0f)
    val mode = mutableIntStateOf(0)
    val rev = mutableIntStateOf(0)
    var clock = 0f
    var now = 0f
    var lastTouch = 0f
    var focused = false
    var returnAt = 0f
    var births = FloatArray(0)
    val sugAt = floatArrayOf(-9f, -9f, -9f)
    val uPen = ChalkAnim()
    val uPh = ChalkAnim(1f)
    val uHaze = ChalkAnim()
    val uEraserOn = ChalkAnim()
    val fade = ChalkAnim(1f)
    val anims = arrayOf(uPen, uPh, uHaze, uEraserOn, fade)
    var eraser = floatArrayOf(0f, 0f, 0f)
    /** The finger's eraser stroke as a polyline, and whether a finger is wiping. */
    val erase = android.graphics.Path()
    var erasePoints = 0
    var wiping = false
    var sugKeySeen = ""
    var lines: ChalkLines? = null

    var query by mutableStateOf("")
    var userMode by mutableStateOf(false)

    private val board = ChalkBoard()
    private var cachedAt = Float.NaN
    private var cachedMode = -1
    private var cachedRev = -1

    val tNow get() = if (mode.intValue == 0) clock else now

    fun board(qx: Float): ChalkBoard {
        val f = frame.floatValue
        val m = mode.intValue
        val r = rev.intValue
        if (f == cachedAt && m == cachedMode && r == cachedRev) return board
        cachedAt = f; cachedMode = m; cachedRev = r
        val l = lines
        if (m == 0) {
            if (auto && l != null) return chalkAutoBoard(clock, l.query.lefts, l.query.rights, board)
            board.pen = qx; board.sug.fill(0f); board.trail = 0f; board.eraserOn = 0f; board.haze = 0f; board.placeholder = 1f
            return board
        }
        board.pen = uPen.v
        for (j in 0..2) board.sug[j] = if (reduced) 1f else chalkEaseInOutSine(chalkSeg(now, sugAt[j], sugAt[j] + CHALK_SUG_LEN))
        board.trail = 1f
        board.eraserOn = uEraserOn.v
        board.haze = uHaze.v
        board.placeholder = uPh.v
        return board
    }

    fun moving(n: Float): Boolean {
        if (mode.intValue == 2 || wiping || anims.any { it.active }) return true
        if (sugAt.any { n - it in -0.5f..CHALK_SUG_LEN }) return true
        return births.any { n - it in -0.5f..(CHALK_DUST_LIFE + CHALK_Q_STEP) }
    }
}

/**
 * A café app's menu search on a chalkboard: the query is chalked in as you type, matching menu
 * lines are chalked below, and a swipe across the board wipes it with a felt eraser.
 *
 * @param autoPlay writes "lemon tart", chalks three menu lines and wipes the board, every LOOP seconds.
 */
@Composable
fun ChalkboardSearch(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val state = remember(reduced, auto) { ChalkState(reduced, auto) }
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val insets = WindowInsets.safeDrawing
    val insetTop = insets.getTop(density) / density.density
    val insetBottom = insets.getBottom(density) / density.density
    val d = density.density

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFECE3D3))) {
        val W = maxWidth.value
        val H = maxHeight.value
        val g = remember(W, H, insetTop) { chalkMakeGeo(W, H, insetTop) }
        val boardBottom = g.boardY + g.boardH + 24f
        val px = min(3f, d)

        val tex by produceState<ChalkTexture?>(null) { value = withContext(Dispatchers.Default) { chalkMakeTexture() } }
        // The board is baked off the main thread. The demo hides the status bar, so the insets (and
        // with them `g`) animate for a moment: wait for them to settle, and let a superseded bake
        // stop (it checks for cancellation per row) instead of piling CPU-bound bakes onto the
        // worker threads, which kept the board from ever arriving.
        val board by produceState<android.graphics.Bitmap?>(null, g, tex) {
            val t = tex ?: return@produceState
            delay(120)
            value = withContext(Dispatchers.Default) {
                val job = coroutineContext.job
                chalkBakeBoard(g, t, px) { job.ensureActive() }
            }
        }
        // Until the baked board arrives, a plain one is drawn at once; the baked one fades in over it.
        val plainBoard = remember(g) { ChalkPlainBoard(g) }
        val boardIn = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(board != null) {
            if (board != null && boardIn.value < 1f) boardIn.animateTo(1f, androidx.compose.animation.core.tween(if (reduced) 0 else 250))
        }
        val fonts = remember { arrayOf(chalkFont(30f), chalkFont(21f), chalkFont(22f)) }

        fun makeLines(q: String, sugs: List<String>) = ChalkLines(
            query = chalkLine(q, fonts[0], g.qx, g.qy, 21),
            sugs = sugs.mapIndexed { j, s ->
                val l = chalkLine(s, fonts[1], g.sx + 16f, g.sy[j], 30 + j * 7)
                l.path.addCircle(g.sx + 4f, g.sy[j] - 7f, 2.6f, android.graphics.Path.Direction.CW)
                l
            },
            spaces = BooleanArray(q.length) { q[it] == ' ' },
        )

        val autoLines = remember(g) { makeLines(CHALK_QUERY, ChalkAutoSuggestions) }
        val userSugs = remember(state.query) { chalkSuggest(state.query) }
        val userLines = remember(g, state.query, userSugs) { makeLines(state.query, userSugs) }
        val lines = if (state.userMode) userLines else autoLines
        state.lines = lines
        val placeholder = remember(g) { chalkLine(PH_TEXT, fonts[2], g.qx, g.qy - 2f, 41) }

        val chalkLines by produceState<ChalkSprite?>(null, lines, tex) {
            val t = tex ?: return@produceState
            val list = buildList {
                add(lines.query.path to 21f)
                lines.sugs.forEachIndexed { j, s -> add(s.path to (30f + j * 7)) }
            }
            value = withContext(Dispatchers.Default) { chalkRenderLines(g, t, px, list) }
        }
        val phSprite by produceState<ChalkSprite?>(null, placeholder, tex) {
            val t = tex ?: return@produceState
            value = withContext(Dispatchers.Default) { chalkRenderLines(g, t, px, listOf(placeholder.path to 41f)) }
        }
        val eraserSprite by produceState<ChalkSprite?>(null) { value = withContext(Dispatchers.Default) { chalkMakeEraser(px) } }

        // The eraser's path in the loop: four passes over the query and the three menu lines.
        val trail = remember(g) {
            val b = android.graphics.Path()
            val l = g.slateX + 22f
            val r = g.slateX + g.slateW - 22f
            val ys = floatArrayOf(g.qy - 12f, g.sy[0] - 8f, g.sy[1] - 8f, g.sy[2] - 8f)
            b.moveTo(l, ys[0])
            for (k in ys.indices) {
                val from = if (k % 2 == 0) l else r
                val to = if (k % 2 == 0) r else l
                val bend = if (k % 2 == 0) -30f else 30f
                if (k > 0) b.cubicTo(from + bend, ys[k - 1] + 6f, from + bend, ys[k] - 6f, from, ys[k])
                b.lineTo(to, ys[k] + (chalkHash(k.toFloat()) - 0.5f) * 6f)
            }
            val m = PathMeasure(b, false)
            val pos = FloatArray(2)
            val tan = FloatArray(2)
            val samples = FloatArray(121 * 3)
            for (i in 0..120) {
                m.getPosTan(m.length * i / 120f, pos, tan)
                samples[i * 3] = pos[0]; samples[i * 3 + 1] = pos[1]; samples[i * 3 + 2] = atan2(tan[1], tan[0])
            }
            Triple(m, m.length, samples)
        }

        LaunchedEffect(state) {
            var last = -1L
            while (true) {
                if (state.mode.intValue == 0 && !state.auto) {
                    snapshotFlow { state.mode.intValue }.first { it != 0 }
                    last = -1L
                }
                withFrameNanos { t ->
                    val dt = if (last < 0) 1f / 60f else min((t - last) / 1e9f, 0.064f)
                    last = t
                    val n = state.now + dt
                    state.now = n
                    when (state.mode.intValue) {
                        0 -> {
                            state.clock = (state.clock + dt) % LOOP
                            state.frame.floatValue = state.clock
                        }
                        1 -> {
                            for (a in state.anims) a.step(n)
                            if (!state.focused && state.uEraserOn.v == 0f && !state.wiping && n - state.lastTouch > IDLE) {
                                state.mode.intValue = 2
                                state.returnAt = n
                                state.fade.tween(0f, 0.6f, n)
                            }
                            if (state.moving(n)) state.frame.floatValue = n
                        }
                        else -> {
                            for (a in state.anims) a.step(n)
                            state.frame.floatValue = n
                            if (n - state.returnAt > RETURN_LEN) {
                                state.uHaze.snap(0f)
                                state.erase.rewind()
                                state.erasePoints = 0
                                state.births = FloatArray(0)
                                state.clock = 0f
                                state.fade.snap(1f)
                                state.mode.intValue = 0
                                state.frame.floatValue = 0f
                                focus.clearFocus()
                                state.userMode = false
                                state.query = ""
                            }
                        }
                    }
                }
            }
        }

        // When the user's lines change, move the pen and chalk the new menu lines in.
        LaunchedEffect(state.userMode, userLines) {
            if (!state.userMode) return@LaunchedEffect
            val end = if (userLines.query.lefts.isNotEmpty()) userLines.query.end + 4f else g.qx
            state.uPen.tween(end, if (state.reduced) 0f else 0.18f, state.now, EASE_RN)
            val key = userSugs.joinToString("|")
            if (state.sugKeySeen != key) {
                state.sugKeySeen = key
                val n = state.now
                state.sugAt[0] = n + 0.05f; state.sugAt[1] = n + 0.2f; state.sugAt[2] = n + 0.35f
            }
            state.rev.intValue++
        }

        fun enterUser(fromSwipe: Boolean) {
            state.lastTouch = state.now
            if (state.mode.intValue == 1) return
            var q = ""
            var shown = false
            if (state.mode.intValue == 0 && state.auto) {
                val t = state.clock
                if (t < CHALK_ERASE_END) {
                    var n = 0
                    for (i in CHALK_QUERY.indices) if (chalkGlyphAt(i) + CHALK_Q_STEP <= t) n = i + 1
                    q = CHALK_QUERY.take(n).trimEnd()
                    shown = t > 4.5f
                }
            }
            val line = chalkLine(q, fonts[0], g.qx, g.qy, 21)
            state.births = FloatArray(q.length) { -9f }
            state.uPen.snap(if (q.isNotEmpty()) line.end + 4f else g.qx)
            val n = state.now
            if (shown) state.sugAt.fill(-9f) else { state.sugAt[0] = n; state.sugAt[1] = n + 0.15f; state.sugAt[2] = n + 0.3f }
            state.uPh.snap(if (q.isNotEmpty()) 0f else 1f)
            if (!fromSwipe) { state.erase.rewind(); state.erasePoints = 0 }
            state.sugKeySeen = chalkSuggest(q).joinToString("|")
            state.fade.snap(1f)
            state.query = q
            state.userMode = true
            state.mode.intValue = 1
            state.rev.intValue++
        }

        fun onChange(v0: String) {
            val v = v0.take(22)
            val old = state.query
            val n = state.now
            val prev = state.births
            var fresh = 0
            state.births = FloatArray(v.length) { i ->
                if (i < old.length && old[i] == v[i] && i < prev.size) prev[i]
                else (n + fresh++ * 0.06f)
            }
            state.erase.rewind()
            state.erasePoints = 0
            state.uPh.tween(if (v.isNotEmpty()) 0f else 1f, 0.2f, n)
            state.lastTouch = n
            state.query = v
            state.rev.intValue++
        }

        val hazePaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = ERASER_W + 6f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = 0x29DEE4DC
                maskFilter = BlurMaskFilter(16.5f, BlurMaskFilter.Blur.NORMAL)
            }
        }
        val erasePaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = ERASER_W
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = 0xFF000000.toInt()
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
        }
        val bmpPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
        val dustPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x9EECEEE6.toInt() } }
        val trimmed = remember { android.graphics.Path() }
        val dust = remember { android.graphics.Path() }
        val boardRect = remember(g) { android.graphics.RectF(0f, 0f, g.W, boardBottom) }

        Box(
            Modifier
                .fillMaxWidth()
                .height(boardBottom.dp)
                .semantics { contentDescription = "Café chalkboard. Tap the search line to type; swipe across the board to wipe it." }
                .pointerInput(state, g) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var active = false
                        val tracker = VelocityTracker()
                        try {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                                val x = ch.position.x / d
                                val y = ch.position.y / d
                                if (!active) {
                                    if (abs(ch.position.x - down.position.x) / d > 12f) {
                                        active = true
                                        enterUser(true)
                                        state.erase.rewind()
                                        state.erase.moveTo(x, y)
                                        state.erase.lineTo(x + 0.1f, y)
                                        state.erasePoints = 1
                                        state.eraser = floatArrayOf(x, y, 0f)
                                        state.uEraserOn.tween(1f, 0.12f, state.now)
                                        state.uHaze.snap(1f)
                                        state.wiping = true
                                        state.rev.intValue++
                                    }
                                } else {
                                    ch.consume()
                                    state.erase.lineTo(x, y)
                                    state.erasePoints++
                                    val vx = tracker.calculateVelocity().x / d
                                    state.eraser = floatArrayOf(x, y, (vx / 4000f).coerceIn(-0.25f, 0.25f))
                                    state.lastTouch = state.now
                                }
                            }
                        } finally {
                            if (active) {
                                state.wiping = false
                                state.query = ""
                                state.births = FloatArray(0)
                                state.uPh.tween(1f, 0.5f, state.now)
                                state.uEraserOn.tween(0f, 0.2f, state.now)
                                state.uHaze.tween(0f, if (state.reduced) 0.3f else 1.8f, state.now)
                                state.lastTouch = state.now
                                state.rev.intValue++
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val b = state.board(g.qx)
                val user = state.mode.intValue != 0
                val t = state.tNow
                drawIntoCanvas { cv ->
                    val nc = cv.nativeCanvas
                    nc.save()
                    nc.scale(d, d)
                    val bIn = if (board != null) boardIn.value else 0f
                    if (bIn < 1f) plainBoard.draw(nc, 1f)
                    board?.let {
                        bmpPaint.alpha = (bIn * 255).roundToInt()
                        nc.drawBitmap(it, null, boardRect, bmpPaint)
                        bmpPaint.alpha = 255
                    }

                    // The eraser's path so far (the loop's trail, or the finger's stroke).
                    val erasePath = if (user) state.erase else {
                        trimmed.rewind()
                        if (b.trail > 0f) trail.first.getSegment(0f, trail.second * b.trail, trimmed, true)
                        trimmed
                    }
                    val hasErase = if (user) state.erasePoints > 0 else b.trail > 0f
                    if (hasErase && b.haze > 0.002f) {
                        hazePaint.alpha = (0x29 * b.haze).roundToInt()
                        nc.drawPath(erasePath, hazePaint)
                    }

                    phSprite?.let { sp ->
                        val o = b.placeholder * 0.34f
                        if (o > 0.002f) {
                            bmpPaint.alpha = (o * 255).roundToInt()
                            nc.drawBitmap(sp.bitmap, null, sp.rect, bmpPaint)
                            bmpPaint.alpha = 255
                        }
                    }

                    val layer = if (user) state.fade.v else 1f
                    val cl = chalkLines
                    if (cl != null && layer > 0.002f) {
                        nc.saveLayerAlpha(cl.rect, (layer * 255).roundToInt())
                        // The query, revealed up to the pen.
                        nc.save()
                        nc.clipRect(g.qx - 30f, g.qy - 46f, g.qx - 30f + max(0f, b.pen - g.qx + 30f), g.qy + 24f)
                        nc.drawBitmap(cl.bitmap, null, cl.rect, bmpPaint)
                        nc.restore()
                        lines.sugs.forEachIndexed { j, s ->
                            val wj = (s.end - g.sx + 20f) * b.sug[j]
                            if (wj > 0.1f) {
                                nc.save()
                                nc.clipRect(g.sx - 10f, g.sy[j] - 30f, g.sx - 10f + wj, g.sy[j] + 14f)
                                nc.drawBitmap(cl.bitmap, null, cl.rect, bmpPaint)
                                nc.restore()
                            }
                        }
                        if (hasErase) nc.drawPath(erasePath, erasePaint)
                        nc.restore()
                    }

                    // Chalk dust: grains from under each glyph while it is written.
                    if (!state.reduced) {
                        dust.rewind()
                        var any = false
                        val q = lines.query
                        for (i in q.lefts.indices) {
                            if (i < lines.spaces.size && lines.spaces[i]) continue
                            val birth = if (!user) chalkGlyphAt(i) else state.births.getOrElse(i) { -9f }
                            for (k in 0 until CHALK_DUST_PER_GLYPH) {
                                val h1 = chalkHash(i * 17 + k * 3.7f)
                                val h2 = chalkHash(i * 29 + k * 5.3f + 1f)
                                val tau = t - (birth + (CHALK_Q_STEP * k) / CHALK_DUST_PER_GLYPH)
                                if (tau < 0f || tau > CHALK_DUST_LIFE) continue
                                val x = q.lefts[i] + (q.rights[i] - q.lefts[i]) * h1 + (h2 - 0.5f) * 22f * tau
                                val y = g.qy + 3f + 160f * (0.5f + h2) * tau * tau
                                val r = (0.6f + 0.9f * h2) * (1 - tau / CHALK_DUST_LIFE)
                                dust.addCircle(x, y, r, android.graphics.Path.Direction.CW)
                                any = true
                            }
                        }
                        if (any) nc.drawPath(dust, dustPaint)
                    }

                    // The felt eraser.
                    eraserSprite?.let { sp ->
                        if (b.eraserOn > 0.002f) {
                            val e = if (user) state.eraser else {
                                val s = trail.third
                                val k = min(120, (b.trail * 120).roundToInt())
                                floatArrayOf(s[k * 3], s[k * 3 + 1], 0.12f * sin(s[k * 3 + 2]))
                            }
                            nc.save()
                            nc.translate(e[0], e[1])
                            nc.rotate(e[2] * 180f / Math.PI.toFloat())
                            bmpPaint.alpha = (b.eraserOn * 255).roundToInt()
                            nc.drawBitmap(sp.bitmap, null, sp.rect, bmpPaint)
                            bmpPaint.alpha = 255
                            nc.restore()
                        }
                    }
                    nc.restore()
                }
            }

            // The hidden field over the search line takes the keyboard.
            BasicTextField(
                value = if (state.userMode) state.query else "",
                onValueChange = { v -> if (state.userMode) onChange(v) else { enterUser(false); onChange(v) } },
                singleLine = true,
                textStyle = TextStyle(color = Color.Transparent, fontSize = 22.sp),
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier
                    .offset(x = (g.slateX + 16f).dp, y = (g.qy - 40f).dp)
                    .width((g.slateW - 32f).dp)
                    .height(52.dp)
                    .onFocusChanged { fs ->
                        if (fs.isFocused && !state.focused) {
                            enterUser(false)
                            state.focused = true
                        } else if (!fs.isFocused && state.focused) {
                            state.focused = false
                            state.lastTouch = state.now
                        }
                    }
                    .semantics { contentDescription = "Search the menu" },
            )
        }

        ChalkCafeInfo(top = g.boardY + g.boardH + 28f, H = H, insetBottom = insetBottom)
    }
}

private class ChalkSpecial(val name: String, val price: String, val icon: Int)

private val SPECIALS = listOf(
    ChalkSpecial("Cardamom bun", "\$5 · 6 left", 0),
    ChalkSpecial("Flat white", "\$4 · oat +\$0.5", 1),
    ChalkSpecial("Fresh mint tea", "\$3 · pot for two", 2),
)

/** The café details below the board; the specials row shows when the screen is tall enough. */
@Composable
private fun ChalkCafeInfo(top: Float, H: Float, insetBottom: Float) {
    val showSpecials = top + 98f + 116f <= H - insetBottom - 8f
    Column(
        Modifier
            .fillMaxWidth()
            .offset(y = top.dp)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            BasicText("Open today · 7:00 – 18:00", style = TextStyle(color = Color(0xFF33261B), fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            BasicText("0.4 km", modifier = Modifier.padding(bottom = 2.dp), style = TextStyle(color = Color(0xFF7A6A58), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
        }
        BasicText("14 Alder Lane, by the canal", style = TextStyle(color = Color(0xFF7A6A58), fontSize = 14.sp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Pastry", "Coffee", "Brunch", "Tea").forEachIndexed { i, c ->
                val on = i == 0
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) Color(0xFF2C3A32) else Color(0xFFE2D6C2))
                        .drawWithContent {
                            drawContent()
                            if (!on) drawRoundRect(Color(0x99FFFFFF), Offset(16.dp.toPx(), 0f), Size(size.width - 32.dp.toPx(), 1.dp.toPx()))
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(c, style = TextStyle(color = if (on) Color(0xFFEEF0E8) else Color(0xFF4A3A2A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
        if (showSpecials) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText("AT THE COUNTER NOW", style = TextStyle(color = Color(0xFF7A6A58), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (sp in SPECIALS) {
                        Column(
                            Modifier
                                .weight(1f)
                                .cssBoxShadow(Color(0x1F3C2814), 1.dp, 2.dp, 16.dp)
                                .cssBoxShadow(Color(0x1A3C2814), 8.dp, 18.dp, 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF6EFE3))
                                .drawWithContent {
                                    drawContent()
                                    drawRoundRect(Color(0xCCFFFFFF), Offset(14.dp.toPx(), 0f), Size(size.width - 28.dp.toPx(), 1.dp.toPx()), CornerRadius(0.5f))
                                }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Box(
                                Modifier.padding(bottom = 4.dp).size(28.dp).clip(CircleShape).background(Color(0xFFE2D6C2)),
                                contentAlignment = Alignment.Center,
                            ) { ChalkIcon(sp.icon) }
                            BasicText(sp.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = Color(0xFF33261B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                            BasicText(sp.price, style = TextStyle(color = Color(0xFF7A6A58), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
                        }
                    }
                }
            }
        }
    }
    if (!showSpecials) {
        Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = (insetBottom + 16).dp), contentAlignment = Alignment.BottomCenter) {
            BasicText(
                "Tap the board to search · swipe across it to wipe",
                style = TextStyle(color = Color(0x994A3A2A), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
        }
    }
}

/** Small line icons for the specials: a bun, a cup on a saucer, a leaf (18 dp). */
@Composable
private fun ChalkIcon(kind: Int) {
    Canvas(Modifier.size(18.dp)) {
        val u = size.width / 18f
        val c = Color(0xFF2C3A32)
        val st = Stroke(width = 1.5f * u, cap = StrokeCap.Round)
        when (kind) {
            0 -> {
                drawRoundRect(c, Offset(3f * u, 9f * u), Size(12f * u, 6.5f * u), CornerRadius(1.5f * u), style = st)
                drawLine(c, Offset(3.5f * u, 12f * u), Offset(14.5f * u, 12f * u), 1.2f * u)
                drawLine(c, Offset(9f * u, 5f * u), Offset(9f * u, 8.5f * u), 1.5f * u, StrokeCap.Round)
                drawCircle(c, 1.1f * u, Offset(9f * u, 3.4f * u))
            }
            1 -> {
                val cup = Path().apply {
                    moveTo(3.5f * u, 5f * u); lineTo(12.5f * u, 5f * u); lineTo(12f * u, 10f * u)
                    cubicTo(11.8f * u, 12.5f * u, 4.2f * u, 12.5f * u, 4f * u, 10f * u); close()
                }
                drawPath(cup, c, style = st)
                val handle = Path().apply { moveTo(12.4f * u, 6.5f * u); cubicTo(16f * u, 6.5f * u, 16f * u, 10f * u, 12.1f * u, 9.6f * u) }
                drawPath(handle, c, style = st)
                drawLine(c, Offset(2f * u, 14.5f * u), Offset(16f * u, 14.5f * u), 1.5f * u, StrokeCap.Round)
            }
            else -> {
                val leaf = Path().apply {
                    moveTo(3.5f * u, 14.5f * u)
                    cubicTo(3f * u, 7f * u, 8f * u, 3.5f * u, 15f * u, 3.5f * u)
                    cubicTo(15f * u, 10.5f * u, 11f * u, 15f * u, 3.5f * u, 14.5f * u)
                    close()
                }
                drawPath(leaf, c, style = st)
                drawLine(c, Offset(3.5f * u, 14.5f * u), Offset(10.5f * u, 7.5f * u), 1.3f * u, StrokeCap.Round)
            }
        }
    }
}
