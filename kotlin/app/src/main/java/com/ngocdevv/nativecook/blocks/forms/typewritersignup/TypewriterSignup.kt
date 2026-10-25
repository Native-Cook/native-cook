package com.ngocdevv.nativecook.blocks.forms.typewritersignup

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Typewriter Sign Up — a writing app's sign-up where the form is typed onto a sheet in a desk
 typewriter (a port of TypewriterSignup.tsx). Each keystroke swings a typebar up out of the basket
 to the platen (70 ms up, 110 ms back), the carriage steps one glyph left on a stiff spring, and the
 glyph lands with a little ink spread and its own baseline jitter and ink density. Moving to the
 next field is a carriage return with the margin bell; "Pull the sheet" (or dragging the sheet up)
 pulls it out of the carriage, and a blank one feeds back in.

 One Canvas draws the desk, header, sheet, carriage, basket and bell from bitmaps rendered once;
 the three fields and the CTA are composables over it. One frame float drives every draw.
*/

/** Seconds after the last touch before the screen eases back and the loop restarts. */
private const val IDLE = 2f
private const val RETURN_LEN = 0.85f
private const val MAX_COLS = 18
private val EASE = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private val EASE_OUT = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val PULL_EASE = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)

private class TwField(val label: String, val placeholder: String, val keyboard: KeyboardType, val caps: KeyboardCapitalization)

private val FIELDS = listOf(
    TwField("Name", "Your pen name", KeyboardType.Text, KeyboardCapitalization.Words),
    TwField("Email", "you@inkwell.co", KeyboardType.Email, KeyboardCapitalization.None),
    TwField("Password", "At least 8 characters", KeyboardType.Password, KeyboardCapitalization.None),
)

/** A value that tweens or springs, stepped by the frame loop. */
private class TwAnim(var v: Float = 0f) {
    private var kind = 0 // 0 still, 1 tween, 2 spring
    private var from = 0f
    private var to = 0f
    private var start = 0f
    private var dur = 0f
    private var easing: Easing = EASE
    private var vel = 0f
    private var k = 0f
    private var c = 0f
    private var m = 1f
    private var done: (() -> Unit)? = null
    val moving get() = kind != 0

    fun snap(x: Float) { v = x; kind = 0; done = null }
    fun tween(target: Float, seconds: Float, now: Float, ease: Easing, onDone: (() -> Unit)? = null) {
        if (seconds <= 0f) { snap(target); onDone?.invoke(); return }
        from = v; to = target; start = now; dur = seconds; easing = ease; kind = 1; done = onDone
    }
    fun spring(target: Float, damping: Float, stiffness: Float, mass: Float = 1f, velocity: Float = 0f) {
        to = target; c = damping; k = stiffness; m = mass; vel = velocity; kind = 2; done = null
    }
    fun step(now: Float, dt: Float) {
        when (kind) {
            1 -> {
                val u = ((now - start) / dur).coerceIn(0f, 1f)
                v = from + (to - from) * easing.transform(u)
                if (u >= 1f) finish()
            }
            2 -> {
                var left = dt
                while (left > 0f) {
                    val h = min(left, 1f / 240f)
                    val a = (-k * (v - to) - c * vel) / m
                    vel += a * h
                    v += vel * h
                    left -= h
                }
                if (abs(v - to) < 0.002f && abs(vel) < 0.01f) finish()
            }
        }
    }
    private fun finish() { v = to; kind = 0; val d = done; done = null; d?.invoke() }
}

private class TwState(val reduced: Boolean, val auto: Boolean, val filledAtRest: Boolean) {
    /** The one value every draw reads: the loop time in mode 0, the clock otherwise. */
    val frame = mutableFloatStateOf(0f)
    /** 0 = autoPlay (or rest), 1 = the user drives, 2 = easing back to rest. */
    val mode = mutableIntStateOf(0)
    /** Bumped on every change made outside the frame loop (keys, focus, presses). */
    val rev = mutableIntStateOf(0)
    var clock = 0f
    var now = 0f
    var lastTouch = 0f
    var returnAt = 0f
    var focused = -1
    var dragging = false
    var feedAt = -1f
    val values = mutableStateListOf("", "", "")
    val uLines = arrayOf("", "", "")
    val uN = intArrayOf(0, 0, 0)
    val uLine = TwAnim()
    val uCol = TwAnim()
    val uPull = TwAnim()
    val uFlex = TwAnim()
    val uDone = TwAnim()
    val uInk = TwAnim(1f)
    val uPress = TwAnim()
    var uStrikeAt = -9f
    var uBar = 4
    var uBellAt = -9f
    val anims = arrayOf(uLine, uCol, uPull, uFlex, uDone, uInk, uPress)

    private var cachedAt = Float.NaN
    private var cachedMode = -1
    private var cachedRev = -1
    private var cached = TypewriterPose()
    private val bell = FloatArray(2)

    /** The pose on screen; reads the frame state, so call it only from draw / layer lambdas. */
    fun pose(): TypewriterPose {
        val f = frame.floatValue
        val m = mode.intValue
        val r = rev.intValue
        if (f == cachedAt && m == cachedMode && r == cachedRev) return cached
        cachedAt = f
        cachedMode = m
        cachedRev = r
        cached = build(m)
        return cached
    }

    fun build(m: Int): TypewriterPose {
        if (m == 0) return if (auto) typewriterAutoPose(clock) else if (filledAtRest) TypewriterPose().fill() else TypewriterPose()
        val p = TypewriterPose()
        p.n0 = uN[0]; p.n1 = uN[1]; p.n2 = uN[2]
        p.line = uLine.v
        p.col = uCol.v
        val tau = now - uStrikeAt
        p.strike = if (reduced) 0f else twStrikeCurve(tau)
        p.inkAge = tau
        p.bar = uBar
        twBell(now - uBellAt, bell)
        p.bell = if (reduced) 0f else bell[0]
        p.bellGlow = bell[1]
        p.pull = uPull.v
        p.flex = uFlex.v
        p.done = uDone.v
        p.press = uPress.v
        return p
    }

    fun line(l: Int): String = if (mode.intValue == 0) TypewriterScript[l] else uLines[l]

    fun touch() { lastTouch = now }

    /** Freezes the current loop frame into the user state, so taking over never jumps. */
    fun enterUser() {
        if (mode.intValue == 1) return
        val p = build(mode.intValue).let { if (mode.intValue == 0) it else TypewriterPose() }
        val vals = arrayOf(TypewriterScript[0].take(p.n0), TypewriterScript[1].take(p.n1), "x".repeat(p.n2))
        if (p.pull >= TYPEWRITER_SHEET_AWAY - 1f) { vals[0] = ""; vals[1] = ""; vals[2] = "" }
        for (l in 0..2) {
            uLines[l] = if (l == 2) "•".repeat(vals[l].length) else vals[l]
            uN[l] = vals[l].length
            values[l] = vals[l]
        }
        uLine.snap(p.line); uCol.snap(p.col); uPull.snap(p.pull); uFlex.snap(0f); uDone.snap(p.done); uInk.snap(1f)
        uBellAt = -9f; uStrikeAt = -9f
        mode.intValue = 1
        lastTouch = now
        if (p.pull > 1f) uPull.tween(0f, if (reduced) 0f else 0.7f, now, EASE_OUT)
        rev.intValue++
    }

    fun resetForm() {
        for (l in 0..2) { values[l] = ""; uLines[l] = ""; uN[l] = 0 }
    }
}

/**
 * A writing app's sign-up typed onto a sheet in a desk typewriter. Typing in the fields strikes a
 * typebar, steps the carriage and stamps the glyph; changing field returns the carriage with the
 * bell; "Pull the sheet", or dragging the sheet up, pulls it out and feeds a blank one.
 *
 * @param autoPlay types the three fields, pulls the sheet and feeds a blank one, every LOOP seconds.
 */
@Composable
fun TypewriterSignup(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val state = remember(reduced, auto) { TwState(reduced, auto, reduced && autoPlay) }
    val view = LocalView.current
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val insets = WindowInsets.safeDrawing
    val insetTop = insets.getTop(density) / density.density
    val insetBottom = insets.getBottom(density) / density.density

    // The typed face (Menlo 15 medium on iOS; the platform monospace here), measured at runtime.
    val ink = remember {
        val tf = twTypeface(Typeface.MONOSPACE, 500)
        arrayOf(
            twTextPaint(tf, 500, 15f, 0xB82B2420.toInt()),
            twTextPaint(tf, 500, 15f, 0xDB2B2420.toInt()),
            twTextPaint(tf, 500, 15f, 0xF7261F1C.toInt()),
            twTextPaint(tf, 500, 15f, TwColors.INK).apply { maskFilter = BlurMaskFilter(2.2f, BlurMaskFilter.Blur.NORMAL) },
        )
    }
    val adv = remember { ink[0].measureText("M") }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF1C1613))) {
        val W = maxWidth.value
        val H = maxHeight.value
        val g = remember(W, H, insetTop, insetBottom) { twMakeGeo(W, H, insetTop, insetBottom) }
        val d = density.density
        // The static art is rendered off the main thread; until it lands only the desk colour shows.
        val art by produceState<TwArtSet?>(null, g, d) { value = withContext(Dispatchers.Default) { twMakeArt(g, d) } }

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
                    state.now += dt
                    val now = state.now
                    when (state.mode.intValue) {
                        0 -> {
                            state.clock = (state.clock + dt) % LOOP
                            state.frame.floatValue = state.clock
                        }
                        else -> {
                            var moving = false
                            for (a in state.anims) { a.step(now, dt); if (a.moving) moving = true }
                            if (state.feedAt in 0f..now) {
                                state.feedAt = -1f
                                feedBlank(state)
                                moving = true
                            }
                            if (now - max(state.uStrikeAt, state.uBellAt) < 1.4f || state.dragging) moving = true
                            if (moving) state.frame.floatValue = now
                            if (state.mode.intValue == 1) {
                                if (state.focused < 0 && !state.dragging && state.feedAt < 0f && now - state.lastTouch > IDLE) {
                                    state.mode.intValue = 2
                                    state.returnAt = now
                                    val dur = if (state.reduced) 0f else 0.8f
                                    state.uInk.tween(0f, if (state.reduced) 0f else 0.35f, now, EASE)
                                    state.uCol.tween(0f, dur, now, EASE)
                                    state.uLine.tween(0f, dur, now, EASE)
                                    state.uPull.tween(0f, dur, now, EASE)
                                    state.uDone.tween(0f, 0.4f, now, EASE)
                                }
                            } else if (now - state.returnAt > RETURN_LEN) {
                                state.resetForm()
                                state.uInk.snap(1f)
                                state.clock = 0f
                                state.mode.intValue = 0
                                state.frame.floatValue = 0f
                            }
                        }
                    }
                }
            }
        }

        val bar = remember { FloatArray(4) }
        val strokeBar = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND } }
        val fill = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
        val bmpPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
        val restBars = remember { android.graphics.Path() }
        val restHeads = remember { android.graphics.Path() }
        val dome = remember(g) {
            val x = g.W - 52f
            val y = g.deckY + 24f
            android.graphics.Path().apply {
                moveTo(x - 11f, y + 8f)
                cubicTo(x - 11f, y - 6f, x - 5f, y - 11f, x, y - 11f)
                cubicTo(x + 5f, y - 11f, x + 11f, y - 6f, x + 11f, y + 8f)
                close()
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A sheet in a typewriter. Your entries are typed onto it. Drag the sheet up to pull it out." }
                .pointerInput(state, g) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val p = state.pose()
                        val x = down.position.x / d / g.scale
                        val y = down.position.y / d / g.scale
                        val left = g.cx - TW_TEXT_X - p.col * adv - adv / 2
                        val top = g.sy - TW_TOP_PAD - p.line * TW_LINE_H - p.pull
                        val inside = x > left && x < left + TW_PAPER_W && y > top && y < g.platenY
                        if (!inside) return@awaitEachGesture
                        state.enterUser()
                        state.dragging = true
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val startY = down.position.y
                        var vy = 0f
                        var ty = 0f
                        try {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                                ty = (ch.position.y - startY) / d
                                vy = tracker.calculateVelocity().y / d
                                if (!ch.pressed) break
                                ch.consume()
                                state.lastTouch = state.now
                                val up = rubber(max(0f, -ty / g.scale), 70f)
                                val dn = min(0f, -ty / g.scale)
                                state.uPull.snap(up + dn / (1f + -dn / 12f))
                                state.uFlex.snap((-vy / 1600f).coerceIn(-1f, 1f))
                            }
                            if (-ty > 90f || vy < -700f) pullOut(state, vy, view)
                            else {
                                state.uPull.spring(0f, damping = 16f, stiffness = 260f, velocity = -vy / g.scale)
                                state.uFlex.spring(0f, damping = 12f, stiffness = 200f)
                            }
                        } finally {
                            state.dragging = false
                            state.lastTouch = state.now
                        }
                    }
                },
        ) {
            val set = art ?: return@Canvas
            val p = state.pose()
            drawIntoCanvas { cv ->
                val nc = cv.nativeCanvas
                nc.save()
                nc.scale(d, d)
                nc.drawBitmap(set.background.bitmap, null, set.background.rect, bmpPaint)
                nc.scale(g.scale, g.scale)

                val paperLeft = g.cx - TW_TEXT_X - p.col * adv - adv / 2
                val paperTop = g.sy - TW_TOP_PAD - p.line * TW_LINE_H - p.pull
                // The sheet, clipped where it goes into the carriage.
                nc.save()
                nc.clipRect(-200f, -3000f, g.W + 200f, g.platenY - 4f)
                val f = p.flex
                nc.translate(paperLeft + TW_PAPER_W / 2, paperTop + TW_PAPER_H)
                nc.rotate(-1.6f * f)
                nc.skew(-0.03f * f, 0f)
                nc.scale(1f - 0.025f * abs(f), 1f)
                nc.translate(-TW_PAPER_W / 2, -TW_PAPER_H)
                nc.drawBitmap(set.paper.bitmap, null, set.paper.rect, bmpPaint)
                val inkAlpha = if (state.mode.intValue == 0) 1f else state.uInk.v
                if (inkAlpha > 0.005f) {
                    if (inkAlpha < 0.999f) nc.saveLayerAlpha(-60f, -60f, TW_PAPER_W + 60f, TW_PAPER_H + 60f, (inkAlpha * 255).roundToInt())
                    for (l in 0..2) {
                        val s = state.line(l)
                        val c = min(min(p.n(l), MAX_COLS), s.length)
                        for (k in 0 until c) {
                            val gx = TW_TEXT_X + k * adv + (twHash((l * 97 + k * 3 + 1).toFloat()) - 0.5f) * 0.8f
                            val gy = TW_TOP_PAD + l * TW_LINE_H + (twHash((l * 53 + k * 7 + 2).toFloat()) - 0.5f) * 1.4f
                            val b = (twHash((l * 11 + k * 5 + 3).toFloat()) * 3).toInt().coerceIn(0, 2)
                            val ox = if (b == 2) 0.3f else 0f
                            val oy = if (b == 2) 0.2f else 0f
                            nc.drawText(s, k, k + 1, gx + ox, gy + oy, ink[b])
                        }
                    }
                    // The newest glyph, drawn again blurred while its ink spreads (0.3 s).
                    if (p.inkAge in 0f..0.3f) {
                        val l = p.line.roundToInt().coerceIn(0, 2)
                        val s = state.line(l)
                        val k = min(p.n(l), MAX_COLS) - 1
                        if (k >= 0 && k < s.length) {
                            ink[3].alpha = (0.55f * (1f - p.inkAge / 0.3f) * 255).roundToInt()
                            nc.drawText(s, k, k + 1, TW_TEXT_X + k * adv, TW_TOP_PAD + l * TW_LINE_H, ink[3])
                        }
                    }
                    if (inkAlpha < 0.999f) nc.restore()
                }
                nc.restore()

                // Carriage, body, bars, deck, active bar, bell.
                nc.save()
                nc.translate(paperLeft, 0f)
                nc.drawBitmap(set.carriage.bitmap, null, set.carriage.rect, bmpPaint)
                nc.restore()
                nc.drawBitmap(set.body.bitmap, null, set.body.rect, bmpPaint)

                val striking = p.strike > 0.001f
                restBars.rewind()
                restHeads.rewind()
                for (i in 0 until TYPEWRITER_BARS) {
                    if (i == p.bar && striking) continue
                    twBarTip(g, i, 0f, bar)
                    restBars.moveTo(bar[0], bar[1])
                    restBars.lineTo(bar[2], bar[3])
                    restHeads.addCircle(bar[2], bar[3], 2.6f, android.graphics.Path.Direction.CW)
                }
                strokeBar.strokeWidth = 2.4f
                strokeBar.color = 0xFF8E959C.toInt()
                nc.drawPath(restBars, strokeBar)
                fill.color = 0xFFC3C9CE.toInt()
                nc.drawPath(restHeads, fill)
                nc.drawBitmap(set.deck.bitmap, null, set.deck.rect, bmpPaint)
                if (striking) {
                    twBarTip(g, p.bar, p.strike, bar)
                    strokeBar.strokeWidth = 3f
                    strokeBar.color = 0xFFD5DADE.toInt()
                    nc.drawLine(bar[0], bar[1], bar[2], bar[3], strokeBar)
                    val s = 2.6f + p.strike * 1.6f
                    fill.color = 0xFF2F3336.toInt()
                    nc.drawRoundRect(RectF(bar[2] - s, bar[3] - s * 0.8f, bar[2] + s, bar[3] + s * 0.8f), 1f, 1f, fill)
                }

                val bx = g.W - 52f
                val by = g.deckY + 24f
                nc.save()
                nc.rotate(p.bell * 14f, bx, by + 8f)
                fill.color = 0xFF3D4247.toInt()
                nc.drawRoundRect(RectF(bx - 13f, by + 7f, bx + 13f, by + 11f), 2f, 2f, fill)
                fill.color = 0xFF9AA1A8.toInt()
                nc.drawPath(dome, fill)
                strokeBar.strokeWidth = 1f
                strokeBar.color = 0x59FFFFFF
                nc.drawPath(dome, strokeBar)
                fill.color = 0xB3FFFFFF.toInt()
                nc.drawRoundRect(RectF(bx - 6f, by - 8f, bx - 2f, by + 2f), 2f, 2f, fill)
                fill.color = 0xFFC3C9CE.toInt()
                nc.drawRoundRect(RectF(bx - 1.5f, by - 15f, bx + 1.5f, by - 10f), 1.5f, 1.5f, fill)
                nc.restore()
                if (p.bellGlow > 0.002f) {
                    strokeBar.strokeWidth = 1.5f
                    strokeBar.color = TwColors.BRASS
                    strokeBar.alpha = (p.bellGlow * 0.55f * 255).roundToInt()
                    nc.drawCircle(bx, by, 12f + (1f - p.bellGlow) * 22f, strokeBar)
                }
                nc.restore()
            }
        }

        // ---- Fields and the button ------------------------------------------------------------
        val scripted by remember(state) { derivedStateOf { if (state.mode.intValue == 0 && state.auto) twTypedCounts(state.frame.floatValue) else -1 } }
        val requesters = remember { List(3) { FocusRequester() } }
        val fieldH = if (g.compact) 52.dp else 56.dp
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = g.formTop.dp)
                .padding(start = 24.dp, end = 24.dp, bottom = (insetBottom + 16).dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.graphicsLayer { alpha = if (state.mode.intValue == 0) state.pose().fields else 1f },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FIELDS.forEachIndexed { l, f ->
                    val shown = when {
                        state.mode.intValue != 0 -> state.values[l]
                        scripted >= 0 -> {
                            val n = (scripted shr (8 * l)) and 0xFF
                            if (l == 2) "x".repeat(n) else TypewriterScript[l].take(n)
                        }
                        state.filledAtRest -> if (l == 2) "x".repeat(8) else TypewriterScript[l]
                        else -> ""
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(fieldH)
                            .drawBehind {
                                drawRect(Color(0x38F1E8D8), Offset(0f, size.height - 1f), androidx.compose.ui.geometry.Size(size.width, 1f))
                            }
                            .padding(bottom = 6.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(
                                f.label.uppercase(),
                                style = TextStyle(color = Color(0xE6CFAE6D), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
                            )
                            BasicTextField(
                                value = shown,
                                onValueChange = { v -> onType(state, l, v.take(if (l == 1) 40 else 24), view) },
                                singleLine = true,
                                textStyle = TextStyle(color = Color(TwColors.CREAM), fontSize = 17.sp, fontWeight = FontWeight.Medium),
                                cursorBrush = SolidColor(Color(TwColors.BRASS)),
                                visualTransformation = if (l == 2) PasswordVisualTransformation() else VisualTransformation.None,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = f.caps,
                                    autoCorrectEnabled = false,
                                    keyboardType = f.keyboard,
                                    imeAction = if (l == 2) ImeAction.Done else ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { requesters[l + 1].requestFocus() },
                                    onDone = { focus.clearFocus() },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .focusRequester(requesters[l])
                                    .onFocusChanged { fs ->
                                        if (fs.isFocused) onFocus(state, l)
                                        else if (state.focused == l) { state.focused = -1; state.touch() }
                                    }
                                    .semantics { contentDescription = f.label },
                                decorationBox = { inner ->
                                    Box {
                                        if (shown.isEmpty()) BasicText(f.placeholder, style = TextStyle(color = Color(0x47F1E8D8), fontSize = 17.sp, fontWeight = FontWeight.Medium))
                                        inner()
                                    }
                                },
                            )
                        }
                    }
                }
            }
            TwCta(state, if (g.compact) 48 else 52) {
                state.enterUser()
                focus.clearFocus()
                state.touch()
                pullOut(state, 0f, view)
            }
        }
    }
}

private fun rubber(x: Float, limit: Float): Float {
    if (x <= limit) return x
    val over = x - limit
    return limit + over / (1 + over / 120f)
}

private fun onFocus(state: TwState, l: Int) {
    state.enterUser()
    val prev = state.uLine.v.roundToInt()
    state.focused = l
    state.touch()
    val len = min(state.values[l].length, MAX_COLS)
    val dur = if (state.reduced) 0f else 0.45f
    state.uLine.tween(l.toFloat(), dur, state.now, EASE)
    state.uCol.tween(len.toFloat(), dur, state.now, EASE)
    if (l != prev) state.uBellAt = state.now
    state.rev.intValue++
}

private fun onType(state: TwState, l: Int, v: String, view: android.view.View) {
    state.enterUser()
    val old = state.values[l]
    state.values[l] = v
    state.uLines[l] = if (l == 2) "•".repeat(v.length) else v
    state.uN[l] = v.length
    state.touch()
    val col = min(v.length, MAX_COLS)
    if (v.length > old.length && v.length <= MAX_COLS) {
        state.uStrikeAt = state.now
        state.uBar = twBarFor(l, v.length - 1)
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (v.length == MAX_COLS - 3) state.uBellAt = state.now
    }
    if (state.reduced) state.uCol.snap(col.toFloat())
    else state.uCol.spring(col.toFloat(), damping = 14f, stiffness = 700f, mass = 0.5f)
    state.rev.intValue++
}

private fun pullOut(state: TwState, velocity: Float, view: android.view.View) {
    val dur = if (state.reduced) 0f else (520f - velocity * 0.12f).coerceIn(260f, 520f) / 1000f
    state.uFlex.tween(0f, dur, state.now, EASE)
    state.uPull.tween(TYPEWRITER_SHEET_AWAY, dur, state.now, PULL_EASE) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        )
        state.uDone.tween(1f, 0.3f, state.now, EASE)
        state.feedAt = state.now + 0.9f
    }
}

private fun feedBlank(state: TwState) {
    state.resetForm()
    state.uLine.snap(0f)
    state.uCol.snap(0f)
    state.uPull.tween(0f, if (state.reduced) 0f else 0.9f, state.now, EASE_OUT)
    state.lastTouch = state.now
}

/** "Pull the sheet": a cream pill that turns to "Draft started" once the sheet is out. */
@Composable
private fun TwCta(state: TwState, height: Int, onTap: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .graphicsLayer {
                val s = 1f - 0.03f * state.pose().press
                scaleX = s
                scaleY = s
            }
            .cssBoxShadow(Color(0x59000000), 1.dp, 2.dp, (height / 2).dp)
            .cssBoxShadow(Color(0x59000000), 10.dp, 28.dp, (height / 2).dp)
            .clip(shape)
            .background(Brush.verticalGradient(0f to Color(0xFFF7EFDF), 0.55f to Color(0xFFEAD9BB), 1f to Color(0xFFD9C39B)))
            .drawBehind {
                // inset 0 1px 0 rgba(255,255,255,0.7): a crescent of light along the top edge.
                val r = CornerRadius(size.height / 2)
                val pill = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r)) }
                val lower = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 1.dp.toPx(), size.width, size.height + 1.dp.toPx(), r)) }
                clipPath(pill) { drawPath(Path.combine(PathOperation.Difference, pill, lower), Color(0xB3FFFFFF)) }
            }
            .pointerInput(state) {
                detectTapGestures(
                    onPress = {
                        state.enterUser()
                        state.touch()
                        state.uPress.tween(1f, 0.12f, state.now, EASE_OUT)
                        state.rev.intValue++
                        tryAwaitRelease()
                        state.uPress.tween(0f, 0.16f, state.now, EASE_OUT)
                        state.rev.intValue++
                    },
                    onTap = { onTap() },
                )
            }
            .semantics { contentDescription = "Pull the sheet" },
        contentAlignment = Alignment.Center,
    ) {
        val ink = Color(TwColors.INK)
        Row(
            Modifier.graphicsLayer {
                val dn = state.pose().done
                alpha = 1f - dn
                translationY = -6f * dn * density
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(Modifier.size(17.dp)) {
                // A sheet with an up arrow (arrow.up.doc).
                val u = size.width / 17f
                val stroke = Stroke(width = 1.6f * u, cap = StrokeCap.Round)
                val doc = Path().apply {
                    moveTo(3.5f * u, 2f * u); lineTo(10f * u, 2f * u); lineTo(13.5f * u, 5.5f * u)
                    lineTo(13.5f * u, 15f * u); lineTo(3.5f * u, 15f * u); close()
                }
                drawPath(doc, ink, style = stroke)
                drawLine(ink, Offset(8.5f * u, 12.5f * u), Offset(8.5f * u, 6.5f * u), 1.6f * u, StrokeCap.Round)
                drawLine(ink, Offset(6.2f * u, 8.8f * u), Offset(8.5f * u, 6.5f * u), 1.6f * u, StrokeCap.Round)
                drawLine(ink, Offset(10.8f * u, 8.8f * u), Offset(8.5f * u, 6.5f * u), 1.6f * u, StrokeCap.Round)
            }
            BasicText("Pull the sheet", style = TextStyle(color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
        }
        Row(
            Modifier.graphicsLayer {
                val dn = state.pose().done
                alpha = dn
                translationY = 6f * (1f - dn) * density
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(Modifier.size(16.dp)) {
                val u = size.width / 16f
                val tick = Path().apply { moveTo(3f * u, 8.5f * u); lineTo(6.5f * u, 12f * u); lineTo(13f * u, 4.5f * u) }
                drawPath(tick, ink, style = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            BasicText("Draft started", style = TextStyle(color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}
