package com.ngocdevv.nativecook.blocks.lists.slotreellist

import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Slot Reel List — "Supper Slots", a spin-for-dinner picker. Three drums (mains, sides, drinks) turn
 behind a cherry-enamel cabinet's window; each is a list wrapped round a cylinder, so its rows
 squeeze and darken as they turn away, and blur with speed. Drag a drum to scroll it (it snaps to a
 row with a bounce); pull the chrome lever to spin all three: they stop one after another with a
 clunk, and the winning row lights up with a sweep while the card below rolls to the new supper.

 autoPlay (LOOP = 9 s, SlotReelListMotion.kt): rest 0–1.3 · lever 1.3–1.9 · spin 1.9–4.8 (stops
 3.6, 4.1, 4.6) · win sweep 4.8–5.8 · hold 5.8–7.0 · drums roll back to their resting rows
 7.0–8.4 · rest. A touch takes over at once; 2 s after the last one the drums roll back (0.9 s)
 and the loop starts again. Reduced motion: no loop, spins land at once, no sweep.

 Drawing: one Canvas — a baked back bitmap, one AGSL shader for the three drums (banded bitmap
 slices below Android 13), a baked front bitmap (shadows, glass, payline), and the lever (a few
 shapes with reused paints).
*/

private const val MODE_AUTO = 0
private const val MODE_USER = 1
private const val MODE_RETURN = 2
private const val IDLE = 2.0
private const val RETURN = 0.9

private const val FEEL_TICK = 1
private const val FEEL_CLUNK = 2
private const val FEEL_WIN = 4

private val Cream = Color(0xFFF5EDE0)
private val CreamSoft = Color(0x9EF5EDE0)
private val CreamFaint = Color(0x66F5EDE0)
private val Gold = Color(0xFFF0B54A)
private val Wall = Color(0xFF1B120E)
private val CardColor = Color(0xFF2A1B16)

private class SlotPick(val key: String, val main: String, val line: String, val minutes: Int, val kcal: Int)

private fun pickOf(rows: IntArray): SlotPick {
    val main = SlotMenu[0][rows[0]].name.replace('\n', ' ')
    val side = SlotMenu[1][rows[1]].name.replace('\n', ' ').lowercase()
    val drink = SlotMenu[2][rows[2]].name.replace('\n', ' ').lowercase()
    val kcal = SlotKcal[0][rows[0]] + SlotKcal[1][rows[1]] + SlotKcal[2][rows[2]]
    return SlotPick(rows.joinToString("-"), main, "with $side and a $drink", SlotMinutes[rows[0]] + 10, kcal)
}

/** The machine's state: the loop, the drums under touch, the lever. Main thread only. */
private class SlotEngine(private val l: SlotLayout, private val reduced: Boolean) {
    var mode = MODE_AUTO
    var clock = 0.0
    var now = 0.0
    var lastTouch = 0.0

    // Per drum under touch: a free offset (maybe on a spring), or a lever spin.
    private val free = DoubleArray(3) { SlotRestRows[it].toDouble() }
    private val springAt = DoubleArray(3) { Double.NaN }
    private val springFrom = DoubleArray(3)
    private val springV = DoubleArray(3)
    private val springTo = DoubleArray(3)
    private val spinning = BooleanArray(3)
    private val spinStart = DoubleArray(3)
    private val spinDur = DoubleArray(3) { 1.0 }
    private val spinBase = DoubleArray(3)
    private val spinDist = DoubleArray(3)
    private var spinCount = 0
    private var winAt = -10.0

    var leverHeld = false
    var lever = 0.0
    private var leverFrom = 0.0
    private var leverAt = -10.0
    var dragging = -1
    private var returnAt = 0.0
    private val returnFrom = DoubleArray(3)
    private val shown = SlotRestRows.copyOf()

    // What is drawn.
    val off = DoubleArray(3) { SlotRestRows[it].toDouble() }
    val blur = FloatArray(3)
    var glow = 0f
    var sweep = -1f
    var leverAngle = 0.0
    private var first = true
    private val next = DoubleArray(3)

    /** Haptics asked for this frame (FEEL_* bits). */
    var feel = 0
    val rows = mutableStateOf(SlotRestRows.copyOf())
    val frame = mutableIntStateOf(0)

    private fun freeValue(c: Int): Double {
        val at = springAt[c]
        if (at.isNaN()) return free[c]
        val k = now - at
        if (k > 1.4) {
            springAt[c] = Double.NaN
            free[c] = springTo[c]
            return free[c]
        }
        return slotSnapSpring(k, springFrom[c], springV[c], springTo[c])
    }

    private fun setFree(c: Int, v: Double) {
        springAt[c] = Double.NaN
        free[c] = v
    }

    private fun offsetNow(c: Int): Double =
        if (spinning[c]) spinBase[c] + slotSpinRows(now - spinStart[c], spinDur[c], spinDist[c]) else freeValue(c)

    /** Takes the machine from the loop where it is: drums keep their offsets, the lever its angle. */
    fun touched() {
        lastTouch = now
        if (mode == MODE_USER) return
        for (c in 0 until 3) {
            setFree(c, if (mode == MODE_AUTO) slotAutoOffset(c, clock) else off[c])
            spinning[c] = false
        }
        // A lever caught mid-swing keeps swinging from where it is.
        leverFrom = leverAngle
        leverAt = now
        winAt = -10.0
        mode = MODE_USER
    }

    private fun startSpin() {
        val at = now
        spinCount += 1
        for (c in 0 until 3) {
            val o = offsetNow(c)
            val from = Math.round(o).toDouble()
            val to = floor(slotHash(spinCount * 3.1 + c * 7.7) * SLOT_ROWS)
            val final = from + slotSpinDistance(slotWrap(from, SLOT_ROWS.toDouble()), to, SlotTurns[c])
            if (reduced) {
                setFree(c, final)
                spinning[c] = false
                continue
            }
            springAt[c] = Double.NaN
            spinning[c] = true
            spinBase[c] = o
            spinDist[c] = final - o
            spinStart[c] = at + 0.06 * c
            spinDur[c] = SlotStops[c] - SLOT_SPIN_AT
        }
        winAt = if (reduced) at else at + 0.12 + spinDur[2] + 0.2
    }

    fun leverStart() {
        touched()
        leverHeld = true
        lever = leverAngle
        feel = feel or FEEL_TICK
    }

    fun leverMove(translationY: Float) {
        lastTouch = now
        val raw = max(0f, translationY) / (l.arm * 1.6f)
        // Past the bottom it resists (rubber band).
        val over = max(0f, raw - 1)
        lever = SLOT_LEVER_MAX * (min(raw, 1f) + over / (1 + over * 4) * 0.15f)
    }

    fun leverEnd() {
        if (!leverHeld) return
        leverHeld = false
        lastTouch = now
        leverFrom = lever
        leverAt = now
        if (lever > SLOT_LEVER_MAX * 0.55) startSpin()
    }

    fun reelStart(x0: Float) {
        touched()
        val c = floor(x0 / (l.colW + l.gap)).toInt().coerceIn(0, 2)
        if (spinning[c]) {
            setFree(c, offsetNow(c))
            spinning[c] = false
        }
        setFree(c, freeValue(c))
        dragging = c
    }

    fun reelMove(deltaY: Float) {
        val c = dragging
        if (c < 0) return
        lastTouch = now
        setFree(c, free[c] - deltaY / l.rowH)
    }

    fun reelEnd(velocityY: Float) {
        val c = dragging
        if (c < 0) return
        dragging = -1
        lastTouch = now
        val v = -velocityY / l.rowH.toDouble()
        val o = free[c]
        val target = Math.round(o + v * 0.22).toDouble()
        if (reduced) {
            setFree(c, target)
        } else {
            springAt[c] = now
            springFrom[c] = o
            springV[c] = v
            springTo[c] = target
        }
        if (target != Math.round(o).toDouble()) feel = feel or FEEL_TICK
    }

    private fun show(pick: IntArray) {
        if (pick[0] != shown[0] || pick[1] != shown[1] || pick[2] != shown[2]) {
            pick.copyInto(shown)
            rows.value = pick.copyOf()
        }
    }

    /** One frame; returns whether the frame loop must keep running. */
    fun step(dt: Double, auto: Boolean): Boolean {
        val at = now + dt
        now = at
        val o = next
        var lv: Double
        var gl = 0f
        var sw = -1f
        var settled = true
        var springing = false

        when (mode) {
            MODE_AUTO -> {
                val t = if (auto) (clock + dt) % LOOP else 0.0
                clock = t
                for (c in 0 until 3) o[c] = slotAutoOffset(c, t)
                lv = slotAutoLever(t)
                gl = slotAutoGlow(t).toFloat()
                sw = slotAutoSweep(t).toFloat()
                show(if (t >= SLOT_SWEEP_AT && t < 8.4) SlotWinRows else SlotRestRows)
            }
            MODE_RETURN -> {
                val p = slotEaseInOutCubic((at - returnAt) / RETURN)
                for (c in 0 until 3) {
                    // To the nearest offset that shows the resting row.
                    val d = slotWrap(SlotRestRows[c] - returnFrom[c] + SLOT_ROWS / 2.0, SLOT_ROWS.toDouble()) - SLOT_ROWS / 2.0
                    o[c] = returnFrom[c] + d * p
                }
                lv = slotLeverSpring(at - leverAt, leverFrom)
                gl = glow * (1 - min(1.0, dt * 8)).toFloat()
                if (p >= 1) {
                    mode = MODE_AUTO
                    clock = 0.0
                }
            }
            else -> {
                var stopped = false
                for (c in 0 until 3) {
                    if (spinning[c]) {
                        val end = spinStart[c] + spinDur[c]
                        if (at - dt < end && at >= end) stopped = true
                        if (at > end + 0.9) {
                            setFree(c, spinBase[c] + spinDist[c])
                            spinning[c] = false
                        } else {
                            settled = false
                        }
                    }
                    if (!springAt[c].isNaN()) springing = true
                    o[c] = offsetNow(c)
                }
                if (stopped) feel = feel or FEEL_CLUNK
                lv = if (leverHeld) lever else slotLeverSpring(at - leverAt, leverFrom)
                val k = at - winAt
                if (k >= 0 && k < 2.6) {
                    if (k < dt) feel = feel or FEEL_WIN
                    gl = (slotEaseOutCubic(k / 0.2) * (1 - slotEaseOutCubic((k - 2.2) / 0.3))).toFloat()
                    sw = if (!reduced && k <= 1) slotEaseInOutCubic(k).toFloat() else -1f
                    settled = false
                }
                for (c in 0 until 3) if (abs(o[c] - Math.round(o[c])) > 0.01) settled = false
                if (dragging >= 0 || leverHeld) settled = false
                if (abs(lv) > 1e-3) settled = false
                if (settled) {
                    show(IntArray(3) { slotWrap(Math.round(o[it]).toDouble(), SLOT_ROWS.toDouble()).toInt() })
                    if (auto && at - lastTouch > IDLE) {
                        o.copyInto(returnFrom)
                        returnAt = at
                        leverFrom = lv
                        leverAt = at
                        mode = MODE_RETURN
                    }
                }
            }
        }

        // Blur from each drum's speed: the distance it covers in ~1/30 s.
        var same = !first && abs(lv - leverAngle) < 1e-4 && abs(gl - glow) < 1e-4f && sw == sweep
        for (c in 0 until 3) {
            val v = (o[c] - off[c]) / max(dt, 0.001)
            val b = if (reduced) 0f else min(0.9, abs(v) * 0.033).toFloat()
            if (abs(o[c] - off[c]) > 1e-5 || abs(b - blur[c]) > 1e-4f) same = false
            blur[c] = b
            off[c] = o[c]
        }
        first = false
        leverAngle = lv
        glow = gl
        sweep = sw
        if (!same) frame.intValue += 1
        return auto || !same || springing || dragging >= 0 || leverHeld || (mode == MODE_USER && !settled)
    }
}

/** A drag that starts after [minDistance] dp: start(at the down point), move(delta, total translation), end(velocity), all in dp. */
private suspend fun PointerInputScope.slotDrag(
    minDistance: Float,
    start: (Offset) -> Unit,
    move: (Offset, Offset) -> Unit,
    end: (Offset) -> Unit,
) {
    val d = density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var dragging = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                val total = change.position - down.position
                if (!dragging && total.getDistance() > minDistance * d) {
                    dragging = true
                    start(down.position / d)
                }
                if (dragging) {
                    move((change.position - change.previousPosition) / d, total / d)
                    change.consume()
                }
                if (!change.pressed) break
            }
        } finally {
            if (dragging) {
                val v = tracker.calculateVelocity()
                end(Offset(v.x / d, v.y / d))
            }
        }
    }
}

/**
 * "Supper Slots": three drums of dishes, sides and drinks wrapped round cylinders; drag a drum or
 * pull the lever to spin all three to tonight's supper.
 *
 * @param autoPlay pulls the lever, spins to a supper, lights the win, rolls back and starts over.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlotReelList(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val density = LocalDensity.current
    val view = LocalView.current
    val top = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.getTop(density),
        WindowInsets.displayCutout.getTop(density),
    ) / density.density
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density) / density.density

    BoxWithConstraints(modifier.fillMaxSize().background(Wall)) {
        val w = maxWidth.value
        val h = maxHeight.value
        val art by produceState<SlotArt?>(null, w, h, top, bottom) {
            value = withContext(Dispatchers.Default) { slotBakeArt(w, h, top, bottom, density.density) }
        }
        val a = art ?: return@BoxWithConstraints
        val l = a.layout
        val engine = remember(a, reduced) { SlotEngine(l, reduced) }
        val running = remember(engine) { mutableStateOf(true) }
        val wake = { running.value = true }

        val isRunning by running
        LaunchedEffect(engine, auto, isRunning) {
            if (!isRunning) return@LaunchedEffect
            var last = -1L
            while (true) {
                val keep = withFrameNanos { t ->
                    val dt = if (last < 0) 1.0 / 60 else min((t - last) / 1e9, 0.05)
                    last = t
                    val keep = engine.step(dt, auto)
                    if (engine.feel != 0) {
                        if (engine.mode == MODE_USER) slotFeel(view, engine.feel)
                        engine.feel = 0
                    }
                    keep
                }
                if (!keep) {
                    running.value = false
                    break
                }
            }
        }

        val shader = remember(a) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching {
                    RuntimeShader(SLOT_REEL_AGSL).apply {
                        setInputShader("strip", BitmapShader(a.strip, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                        setFloatUniform("origin", l.win.x, l.win.y)
                        setFloatUniform("win", l.win.w, l.win.h)
                        setFloatUniform("colW", l.colW)
                        setFloatUniform("gap", l.gap)
                        setFloatUniform("rowH", l.rowH)
                        setFloatUniform("radius", l.radius)
                        setFloatUniform("rows", SLOT_ROWS.toFloat())
                        setFloatUniform("px", a.px)
                    }
                }.getOrNull()
            } else {
                null
            }
        }
        val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
        val fallback = remember(a) { SlotReelFallback(a) }
        val lever = remember(l) { SlotLeverPaints(l) }
        val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
        val dst = remember { RectF() }

        Canvas(Modifier.fillMaxSize()) {
            engine.frame.intValue
            val d = density.density
            scale(d, d, pivot = Offset.Zero) {
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    dst.set(0f, 0f, l.w, l.h)
                    c.drawBitmap(a.back, null, dst, bitmapPaint)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || shader == null) {
                        fallback.draw(c, engine.off, engine.glow, engine.sweep)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("off", engine.off[0].toFloat(), engine.off[1].toFloat(), engine.off[2].toFloat())
                    shader.setFloatUniform("blur", engine.blur[0], engine.blur[1], engine.blur[2])
                    shader.setFloatUniform("sweep", engine.sweep)
                    shader.setFloatUniform("glow", engine.glow)
                    drawRect(brush, Offset(l.win.x, l.win.y), Size(l.win.w, l.win.h))
                }
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    val p = SLOT_FRONT_PAD
                    dst.set(l.win.x - p, l.win.y - p, l.win.x + l.win.w + p, l.win.y + l.win.h + p)
                    c.drawBitmap(a.front, null, dst, bitmapPaint)
                    lever.draw(c, engine.leverAngle)
                }
            }
        }

        // The drums' and the lever's touch areas.
        Box(
            Modifier
                .offset { IntOffset(l.win.x.dp.roundToPx(), l.win.y.dp.roundToPx()) }
                .size(l.win.w.dp, l.win.h.dp)
                .semantics { contentDescription = "Reels: drag one to scroll it" }
                .pointerInput(engine) {
                    slotDrag(
                        4f,
                        start = { p -> engine.reelStart(p.x); wake() },
                        move = { delta, _ -> engine.reelMove(delta.y); wake() },
                        end = { v -> engine.reelEnd(v.y); wake() },
                    )
                },
        )
        val leverTop = l.pivotY - l.arm - 30
        Box(
            Modifier
                .offset { IntOffset((l.w - 56).dp.roundToPx(), leverTop.dp.roundToPx()) }
                .size(56.dp, (l.arm * 2 + 60).dp)
                .semantics { contentDescription = "Lever: pull down to spin" }
                .pointerInput(engine) {
                    slotDrag(
                        2f,
                        start = { engine.leverStart(); wake() },
                        move = { _, total -> engine.leverMove(total.y); wake() },
                        end = { engine.leverEnd(); wake() },
                    )
                },
        )

        // Header.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = (top + 14).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("SUPPER SLOTS", style = TextStyle(color = Gold, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp))
            BasicText("Spin for dinner", style = TextStyle(color = Cream, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp))
            BasicText("3 pulls left tonight · Serves 2", style = TextStyle(color = CreamSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        SlotCard(
            engine,
            reduced,
            Modifier.padding(start = 14.dp, end = 14.dp, top = l.cardY.dp, bottom = (bottom + 12).dp),
        )
    }
}

private fun slotFeel(view: View, feel: Int) {
    when {
        feel and FEEL_WIN != 0 -> view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        )
        feel and FEEL_CLUNK != 0 -> view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        feel and FEEL_TICK != 0 -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

/** The lever: an arm from the hub to the knob; tipping toward the viewer shortens the arm and swells the knob. */
private class SlotLeverPaints(private val l: SlotLayout) {
    private val matrix = Matrix()
    private val rect = RectF()
    // The knob's soft shadow (a Gaussian of sigma 7 round a disc), as a radial ramp scaled to the knob.
    private val shadowShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0x73000000, 0x61000000, 0x3A000000, 0x12000000, 0x00000000),
        floatArrayOf(0.03f, 0.28f, 0.52f, 0.76f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = shadowShader }
    private val arm = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            l.pivotX - 7, 0f, l.pivotX + 7, 0f,
            intArrayOf(0xFF5D6368.toInt(), 0xFFF4F6F7.toInt(), 0xFFB4BABE.toInt(), 0xFF4A4F53.toInt()),
            floatArrayOf(0f, 0.35f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val knobShader = RadialGradient(
        0f, 0f, 30f,
        intArrayOf(0xFFFFB4A4.toInt(), 0xFFE2302C.toInt(), 0xFF9C1418.toInt(), 0xFF56080B.toInt()),
        floatArrayOf(0f, 0.3f, 0.75f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = knobShader }

    fun draw(c: android.graphics.Canvas, a: Double) {
        val z = max(0.0, sin(a)).toFloat()
        val y = (l.pivotY - l.arm * cos(a)).toFloat()
        val r = 15 * (1 + 0.32f * z)
        val w = 9 * (1 + 0.2f * z)
        val sx = l.pivotX + 4 + 6 * z
        val sy = y + 6 + 10 * z
        val sr = r + 14
        matrix.setScale(sr, sr)
        matrix.postTranslate(sx, sy)
        shadowShader.setLocalMatrix(matrix)
        c.drawCircle(sx, sy, sr, shadow)
        rect.set(l.pivotX - w / 2, min(l.pivotY, y), l.pivotX + w / 2, min(l.pivotY, y) + abs(y - l.pivotY))
        c.drawRoundRect(rect, 4f, 4f, arm)
        matrix.setTranslate(l.pivotX - r * 0.35f, y - r * 0.4f)
        knobShader.setLocalMatrix(matrix)
        c.drawCircle(l.pivotX, y, r, knob)
    }
}

@Composable
private fun SlotCard(engine: SlotEngine, reduced: Boolean, modifier: Modifier) {
    val pick = pickOf(engine.rows.value)
    val shape = RoundedCornerShape(26.dp)
    val rise = with(LocalDensity.current) { 25.dp.roundToPx() }
    Column(
        modifier
            .fillMaxSize()
            .cssBoxShadow(Color(0x59000000), 16.dp, 36.dp, 26.dp)
            .cssBoxShadow(Color(0x66000000), 1.dp, 2.dp, 26.dp)
            .background(CardColor, shape)
            .border(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(0x1AFFE6D2), 0.08f to Color.Transparent), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText("TONIGHT'S SUPPER", style = TextStyle(color = CreamFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
            Box(Modifier.fillMaxWidth().height(92.dp).clipToBounds()) {
                AnimatedContent(
                    targetState = pick,
                    contentKey = { it.key },
                    transitionSpec = {
                        if (reduced) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (fadeIn(tween(320, easing = EaseOutStrong)) + slideInVertically(tween(320, easing = EaseOutStrong)) { rise }) togetherWith
                                (fadeOut(tween(220)) + slideOutVertically(tween(220)) { -rise })
                        }
                    },
                    label = "supper",
                ) { p ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText(p.main, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = Cream, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp))
                        BasicText(p.line, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = CreamSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SlotChip("${p.minutes} min")
                            SlotChip("${p.kcal} kcal")
                            SlotChip("Serves 2")
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SlotButton("Cook this", primary = true)
            SlotButton("Save combo", primary = false)
        }
    }
}

@Composable
private fun SlotChip(text: String) {
    BasicText(
        text,
        modifier = Modifier
            .background(Color(0x14F5EDE0), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = TextStyle(color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    )
}

@Composable
private fun RowScope.SlotButton(label: String, primary: Boolean) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(24.dp)
    var m = Modifier
        .weight(1f)
        .height(48.dp)
        .graphicsLayer {
            val k = if (pressed) 0.97f else 1f
            scaleX = k; scaleY = k
        }
    m = if (primary) {
        m.cssBoxShadow(Color(0x40F0B54A), 6.dp, 16.dp, 24.dp).background(Gold, shape)
    } else {
        m.border(1.dp, Color(0x38F5EDE0), shape)
    }
    Box(
        m.clickable(interactionSource = source, indication = null, role = Role.Button) {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (primary) Color(0xFF2B1708) else Cream,
                fontSize = 16.sp,
                fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.Bold,
            ),
        )
    }
}
