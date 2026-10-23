package com.ngocdevv.nativecook.blocks.text.ropetexttrail

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Rope Text Trail — a sentence that behaves like a rope of letters on grained paper. A port of the
 SwiftUI block (RopeTextTrail.swift); the numbers are the same.

 The sentence is a chain: its head (a small orange dot just ahead of the closing dash) moves, and every
 letter sits on the path the head has drawn, its centre placed by its own advance behind the head and
 turned to the path's tangent. The baseline runs half an x-height under the path, so the path threads
 through the middle of the lowercase letters.

 Resting curve: y = cy − A·sin(2πu) from x = 20 to x = W − 20 (u = 0 … 1, cy = 50 % of the height),
 A solved so the curve is the rope's length (sentence + 9 dp gap + dot) plus 20 dp, the rope centred on it.

 autoPlay timeline (LOOP = 10 s, e = seconds into the loop):
   0.0 … 1.5   rest. The rope sways once over the rest window (7.7 → 11.5, wrapping): the middle of the
               curve sags up to 9 dp and lifts back, a(φ) = sin(2πφ)·sin(πφ) / 0.77, so it starts and stops still.
   1.5 … 7.7   the head draws its path: up the right side, a loop over the top leftwards, down across the
               middle, a loop along the bottom leftwards, up the left edge, then along the resting curve itself
               back to its place. Arc length follows easeInOutSine, so it starts and arrives at rest. Speed m =
               speed / 700 dp/s (≤ 1) stretches the spacing by up to 10 %, and the tail letters shrink by up to
               18 % and fade by up to 40 % (linearly less towards the head).
   7.7 … 10.0  rest; t = 10 is the same frame as t = 0 (the sway is zero at both ends of its window).

 Touch: dragging anywhere grabs the head; it glides onto the finger (time constant 0.14 s) and the rope
 follows the path it draws (a haptic on grab). On release every letter springs to its place on the
 resting curve (0.9 s each, damped spring with a 4 % overshoot, the head first and the tail 0.3 s later).
 2 s after the release the loop starts again from rest.
 Reduced motion: the rope rests on the curve without swaying and the loop is off; a drag still pulls it,
 and on release it fades back onto the curve (0.35 s).
 */

private const val LOOP = 10.0
private const val MOVE_START = 1.5
private const val MOVE_END = 7.7
private const val FONT_SIZE = 32f
private const val DOT_GAP = 9.0
private const val MARGIN = 20.0
private const val RELAX_SPAN = 1.2
private const val FOLLOW = 0.14
private const val REGULAR_ASSET = "ropetexttrail/ropetexttrail_InstrumentSerif-Regular.ttf"
private const val ITALIC_ASSET = "ropetexttrail/ropetexttrail_InstrumentSerif-Italic.ttf"

private val Paper = Color(0xFFF2EFE8)
private val Ink = Color(0xFF1A1814)
private val Accent = Color(0xFFFF4D1F)

private fun nowSeconds() = System.nanoTime() / 1e9

private class Placement(var x: Double, var y: Double, var angle: Double, var scale: Double = 1.0, var opacity: Double = 1.0)

private class Rope(val letters: List<Placement>, val headX: Double, val headY: Double)

/** A polyline measured by arc length; points along it are sampled with a smoothed tangent. */
private class Polyline(val xs: DoubleArray, val ys: DoubleArray) {
    val lengths = DoubleArray(xs.size)

    init {
        for (i in 1 until xs.size) lengths[i] = lengths[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
    }

    val length get() = if (lengths.isEmpty()) 0.0 else lengths[lengths.size - 1]

    fun point(s0: Double, out: DoubleArray) {
        if (xs.size < 2) {
            out[0] = xs.firstOrNull() ?: 0.0; out[1] = ys.firstOrNull() ?: 0.0; return
        }
        val s = s0.coerceIn(0.0, length)
        var lo = 0
        var hi = lengths.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (lengths[mid] <= s) lo = mid else hi = mid
        }
        val span = lengths[hi] - lengths[lo]
        val t = if (span > 0) (s - lengths[lo]) / span else 0.0
        out[0] = xs[lo] + (xs[hi] - xs[lo]) * t
        out[1] = ys[lo] + (ys[hi] - ys[lo]) * t
    }

    private val a = DoubleArray(2)
    private val b = DoubleArray(2)

    /** The point at arc length [s] in out[0..1] and the tangent's angle (over ±4 dp) in out[2]. */
    fun sample(s: Double, out: DoubleArray) {
        point(s, out)
        point(s - 4, a)
        point(s + 4, b)
        out[2] = atan2(b[1] - a[1], b[0] - a[0])
    }

    companion object {
        fun of(points: List<DoubleArray>) = Polyline(DoubleArray(points.size) { points[it][0] }, DoubleArray(points.size) { points[it][1] })
    }
}

private class RopeFonts(context: Context) {
    val regular: Typeface = Typeface.createFromAsset(context.assets, REGULAR_ASSET)
    val italic: Typeface = Typeface.createFromAsset(context.assets, ITALIC_ASSET)
}

private class RopeLayout(text: String, accent: String, val width: Double, val height: Double, fonts: RopeFonts) {
    val chars = text.toList()
    val accentMask = BooleanArray(chars.size)
    val advances: DoubleArray
    val centers: DoubleArray
    val textLength: Double
    val xHeight: Double
    val x0 = MARGIN
    val x1 = width - MARGIN
    val cy = height * 0.5
    val amp: Double
    val tailStart: Double
    val headRest: Double
    val rest: Polyline
    val master: Polyline
    val masterStart: Double
    val masterEnd: Double

    init {
        val from = text.indexOf(accent)
        if (from >= 0) for (i in from until from + accent.length) accentMask[i] = true
        val paint = Paint().apply { textSize = FONT_SIZE }
        advances = DoubleArray(chars.size) { i ->
            paint.typeface = if (accentMask[i]) fonts.italic else fonts.regular
            paint.measureText(chars[i].toString()).toDouble()
        }
        var s = 0.0
        centers = DoubleArray(chars.size) { i -> (s + advances[i] / 2).also { s += advances[i] } }
        textLength = s
        paint.typeface = fonts.regular
        val bounds = Rect()
        paint.getTextBounds("x", 0, 1, bounds)
        xHeight = -bounds.top.toDouble()

        val ropeLength = s + DOT_GAP + 4
        // Solve the amplitude so the curve is the rope's length plus 20 dp.
        val target = ropeLength + 20
        var lo = 0.0
        var hi = 160.0
        repeat(40) {
            val mid = (lo + hi) / 2
            if (curveLength(mid) < target) lo = mid else hi = mid
        }
        amp = (lo + hi) / 2
        rest = Polyline.of(curve(amp, 0.0, 200))
        tailStart = (rest.length - ropeLength) / 2
        headRest = tailStart + s + DOT_GAP

        // The autoplay path: a lead-in, the resting curve up to the head, the drawn loops, the resting curve again.
        val w = width
        val h = height
        val pts = mutableListOf<DoubleArray>()
        val o = DoubleArray(3)
        rest.sample(0.0, o)
        val a0 = o[2]
        var k = 240.0
        while (k > 0) {
            pts += doubleArrayOf(rest.xs[0] - cos(a0) * k, rest.ys[0] - sin(a0) * k)
            k -= 20
        }
        for (i in rest.xs.indices) if (rest.lengths[i] < headRest) pts += doubleArrayOf(rest.xs[i], rest.ys[i])
        pts += pointOnRest(headRest)
        masterStart = Polyline.of(pts).length
        val controls = listOf(
            pointOnRest(headRest - 40), pointOnRest(headRest),
            doubleArrayOf(0.89 * w, 0.375 * h), doubleArrayOf(0.78 * w, 0.235 * h),
            doubleArrayOf(0.56 * w, 0.18 * h), doubleArrayOf(0.34 * w, 0.22 * h),
            doubleArrayOf(0.22 * w, 0.33 * h), doubleArrayOf(0.30 * w, 0.45 * h),
            doubleArrayOf(0.55 * w, 0.535 * h), doubleArrayOf(0.76 * w, 0.63 * h),
            doubleArrayOf(0.78 * w, 0.76 * h), doubleArrayOf(0.62 * w, 0.835 * h),
            doubleArrayOf(0.38 * w, 0.82 * h), doubleArrayOf(0.19 * w, 0.73 * h),
            doubleArrayOf(0.11 * w, 0.615 * h),
            pointOnRest(0.0), pointOnRest(40.0),
        )
        for (seg in 1 until controls.size - 2) {
            for (j in 1..24) pts += catmullRom(controls[seg - 1], controls[seg], controls[seg + 1], controls[seg + 2], j / 24.0)
        }
        // The drawn part ends on the resting curve's first point, so from there the arc runs as on the curve.
        val drawn = Polyline.of(pts).length
        for (i in 1 until rest.xs.size) if (rest.lengths[i] < headRest + 60) pts += doubleArrayOf(rest.xs[i], rest.ys[i])
        master = Polyline.of(pts)
        masterEnd = drawn + headRest
    }

    private fun pointOnRest(s: Double): DoubleArray {
        val o = DoubleArray(2)
        rest.point(s, o)
        return o
    }

    fun curve(amp: Double, sway: Double, count: Int): List<DoubleArray> = (0..count).map { i ->
        val u = i.toDouble() / count
        doubleArrayOf(x0 + (x1 - x0) * u, cy - amp * sin(2 * PI * u) + 9 * sway * sin(PI * u))
    }

    private fun curveLength(amp: Double): Double = Polyline.of(curve(amp, 0.0, 400)).length

    /** The rope lying on the resting curve, swayed by [sway] (−1 … 1). */
    fun restRope(sway: Double): Rope {
        val line = if (sway == 0.0) rest else Polyline.of(curve(amp, sway, 120))
        val o = DoubleArray(3)
        val letters = centers.map { c ->
            line.sample(tailStart + c, o)
            Placement(o[0], o[1], o[2])
        }
        line.point(headRest, o)
        return Rope(letters, o[0], o[1])
    }

    /** The rope at [e] seconds into the autoplay loop. */
    fun autoRope(e: Double): Rope {
        if (e < MOVE_START || e >= MOVE_END) {
            val phi = (if (e >= MOVE_END) e - MOVE_END else e + LOOP - MOVE_END) / (LOOP - (MOVE_END - MOVE_START))
            return restRope(sway(phi))
        }
        val tau = (e - MOVE_START) / (MOVE_END - MOVE_START)
        val head = masterStart + easeInOutSine(tau) * (masterEnd - masterStart)
        val speed = (masterEnd - masterStart) * PI / 2 * sin(PI * tau) / (MOVE_END - MOVE_START)
        return ropeOn(master, head, min(1.0, speed / 700))
    }

    /** The rope along a drawn line whose head is at arc length [head], moving with [m] (0 … 1). */
    fun ropeOn(line: Polyline, head: Double, m: Double): Rope {
        val stretch = 1 + 0.10 * m
        val n = max(chars.size - 1, 1).toDouble()
        val o = DoubleArray(3)
        val letters = centers.mapIndexed { i, c ->
            line.sample(head - DOT_GAP - (textLength - c) * stretch, o)
            val tail = 1 - i / n
            Placement(o[0], o[1], o[2], 1 - 0.18 * m * tail, 1 - 0.40 * m * tail)
        }
        line.point(head, o)
        return Rope(letters, o[0], o[1])
    }

    companion object {
        /** The sway's amount over its window, φ = 0 … 1: a sag and a lift that start and end still. */
        fun sway(phi: Double) = sin(2 * PI * phi) * sin(PI * phi) / 0.77

        fun easeInOutSine(t: Double) = -(cos(PI * t) - 1) / 2

        fun catmullRom(p0: DoubleArray, p1: DoubleArray, p2: DoubleArray, p3: DoubleArray, t: Double): DoubleArray {
            val t2 = t * t
            val t3 = t2 * t
            fun f(a: Double, b: Double, c: Double, d: Double) =
                0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
            return doubleArrayOf(f(p0[0], p1[0], p2[0], p3[0]), f(p0[1], p1[1], p2[1], p3[1]))
        }
    }
}

/** The block's state between frames. [frame] is the only Compose state the drawing reads. */
private class RopeController(val reduced: Boolean, val autoPlay: Boolean) {
    var frame by mutableDoubleStateOf(nowSeconds())
    var autoStart: Double? = null
    var touching = false
    val trail = ArrayList<DoubleArray>()
    var fingerX = 0.0
    var fingerY = 0.0
    var grabDx = 0.0
    var grabDy = 0.0
    var grabTime = 0.0
    var lastTime = 0.0
    var speed = 0.0
    var relaxStart: Double? = null
    var relaxFrom: Rope? = null
    var restSince = nowSeconds()
    var fadeStart = -10.0
    var resume: Job? = null

    fun rope(layout: RopeLayout, now: Double): Rope {
        if (touching) return touchRope(layout, now)
        relaxStart?.let { start ->
            val rest = layout.restRope(0.0)
            if (reduced) return rest
            val elapsed = now - start
            if (elapsed < RELAX_SPAN) return relaxRope(rest, elapsed)
        }
        val start = autoStart
        if (start != null && now >= start && !reduced) return layout.autoRope((now - start) % LOOP)
        if (!autoPlay && !reduced) {
            // Without the loop the resting rope keeps swaying, one sway every 3.8 s.
            val phi = ((now - restSince) / 3.8) % 1.0
            return layout.restRope(RopeLayout.sway(phi))
        }
        return layout.restRope(0.0)
    }

    fun grab(rope: Rope, x: Double, y: Double, now: Double) {
        touching = true
        relaxStart = null
        relaxFrom = null
        autoStart = null
        // The line so far: behind the tail along its direction, through the letters, to the head.
        trail.clear()
        rope.letters.firstOrNull()?.let { first ->
            val back = first.angle + PI
            var k = 300.0
            while (k > 0) {
                trail += doubleArrayOf(first.x + cos(back) * k, first.y + sin(back) * k)
                k -= 20
            }
        }
        for (p in rope.letters) trail += doubleArrayOf(p.x, p.y)
        trail += doubleArrayOf(rope.headX, rope.headY)
        fingerX = x
        fingerY = y
        grabDx = x - rope.headX
        grabDy = y - rope.headY
        grabTime = now
        lastTime = now
        speed = 0.0
    }

    /** The rope while a finger holds it; the head glides onto the finger and the line grows behind it. */
    fun touchRope(layout: RopeLayout, now: Double): Rope {
        if (trail.isEmpty()) return layout.restRope(0.0)
        val k = exp(-max(0.0, now - grabTime) / FOLLOW)
        val hx = fingerX - grabDx * k
        val hy = fingerY - grabDy * k
        val last = trail[trail.size - 1]
        val dist = hypot(hx - last[0], hy - last[1])
        val dt = now - lastTime
        if (dt > 0) {
            speed += (dist / dt - speed) * min(1.0, dt / 0.12)
            lastTime = now
        }
        if (dist > 0.75) trail += doubleArrayOf(hx, hy)
        // Keep only what the rope can reach.
        val keep = layout.textLength * 1.3 + 400
        var line = Polyline.of(trail)
        if (line.length > keep * 1.5) {
            var drop = 0
            while (drop < trail.size - 2 && line.length - line.lengths[drop] > keep) drop++
            trail.subList(0, drop).clear()
            line = Polyline.of(trail)
        }
        return layout.ropeOn(line, line.length, min(1.0, speed / 700))
    }

    fun release(rope: Rope, now: Double) {
        touching = false
        relaxFrom = rope
        relaxStart = now
        restSince = now + RELAX_SPAN
        fadeStart = now
    }

    /** The rope springing back from the release snapshot to [rest], the head first. */
    fun relaxRope(rest: Rope, elapsed: Double): Rope {
        val from = relaxFrom ?: return rest
        val n = max(rest.letters.size - 1, 1).toDouble()
        val letters = rest.letters.mapIndexed { i, r ->
            val f = from.letters[i]
            val delay = 0.3 * (1 - i / n)
            val p = spring((elapsed - delay) / 0.9)
            var da = r.angle - f.angle
            da = atan2(sin(da), cos(da))
            val q = min(1.0, p)
            Placement(
                f.x + (r.x - f.x) * p, f.y + (r.y - f.y) * p, f.angle + da * p,
                f.scale + (1 - f.scale) * q, f.opacity + (1 - f.opacity) * q,
            )
        }
        val ph = spring(elapsed / 0.9)
        return Rope(letters, from.headX + (rest.headX - from.headX) * ph, from.headY + (rest.headY - from.headY) * ph)
    }

    /** Under reduced motion the rope fades back onto the curve after a release. */
    fun fadeIn(now: Double): Double {
        if (!reduced || touching) return 1.0
        val t = now - fadeStart
        return if (t < 0.35) max(0.0, t / 0.35) else 1.0
    }

    companion object {
        /** A damped spring from 0 to 1 over x = 0 … 1, with a 4 % overshoot; exactly 0 before and 1 after. */
        fun spring(x: Double): Double = when {
            x <= 0 -> 0.0
            x >= 1 -> 1.0
            else -> 1 - exp(-6 * x) * cos(5.65 * x)
        }
    }
}

@Composable
fun RopeTextTrail(
    modifier: Modifier = Modifier,
    text: String = "words follow you wherever you go —",
    accent: String = "you",
    label: String = "A LINE OF THOUGHT",
    issue: String = "No. 07",
    hint: String = "Drag to write in the air",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val fonts = remember { RopeFonts(context) }
    val c = remember(reduced, autoPlay) { RopeController(reduced, autoPlay) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) {
        if (autoPlay && !reduced) c.autoStart = nowSeconds()
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    RopeTextTrailSystemBars()

    BoxWithConstraints(modifier.fillMaxSize().background(Paper)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val density = LocalDensity.current.density
        val layout = remember(width, height, text, accent) { RopeLayout(text, accent, width, height, fonts) }
        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = FONT_SIZE } }

        RopeTextTrailGrain(Modifier.fillMaxSize())

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = text }
                .pointerInput(c, layout) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val now = nowSeconds()
                        c.resume?.cancel()
                        val current = c.rope(layout, now)
                        c.grab(current, down.position.x / density.toDouble(), down.position.y / density.toDouble(), now)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            c.fingerX = change.position.x / density.toDouble()
                            c.fingerY = change.position.y / density.toDouble()
                            change.consume()
                        }
                        val end = nowSeconds()
                        c.release(c.touchRope(layout, end), end)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        c.resume?.cancel()
                        if (autoPlay && !reduced) {
                            c.resume = scope.launch {
                                delay(2000)
                                c.relaxStart = null
                                c.autoStart = nowSeconds()
                            }
                        }
                    }
                },
        ) {
            drawRope(c, layout, fonts, paint, density)
        }

        // Header: a tracked label and the issue number over a hairline.
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (top + 16).dp)
                .padding(horizontal = 24.dp),
        ) {
            val style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.6.sp, color = Ink.copy(alpha = 0.6f))
            Row(Modifier.fillMaxWidth()) {
                BasicText(label, style = style, maxLines = 1)
                Spacer(Modifier.weight(1f))
                BasicText(issue, style = style, maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ink.copy(alpha = 0.18f)))
        }

        // The hint.
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (height - max(bottomInset.toDouble(), 20.0) - 32).dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(Accent, CircleShape))
            Spacer(Modifier.width(10.dp))
            BasicText(
                hint,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp, color = Ink.copy(alpha = 0.6f)),
                maxLines = 1,
            )
        }
    }
}

private fun DrawScope.drawRope(c: RopeController, layout: RopeLayout, fonts: RopeFonts, paint: Paint, density: Float) {
    val now = c.frame
    val rope = c.rope(layout, now)
    val fade = c.fadeIn(now)
    val canvas = drawContext.canvas.nativeCanvas
    val ink = Ink.toArgb()
    val accent = Accent.toArgb()
    canvas.save()
    canvas.scale(density, density)
    for ((i, p) in rope.letters.withIndex()) {
        val ch = layout.chars[i]
        if (ch == ' ') continue
        paint.typeface = if (layout.accentMask[i]) fonts.italic else fonts.regular
        paint.color = if (layout.accentMask[i]) accent else ink
        paint.alpha = (255 * p.opacity * fade).roundToInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(p.x.toFloat(), p.y.toFloat())
        canvas.rotate(Math.toDegrees(p.angle).toFloat())
        canvas.scale(p.scale.toFloat(), p.scale.toFloat())
        canvas.drawText(ch.toString(), (-layout.advances[i] / 2).toFloat(), (layout.xHeight / 2).toFloat(), paint)
        canvas.restore()
    }
    canvas.restore()
    // The head: an orange dot with a soft halo.
    val head = Offset((rope.headX * density).toFloat(), (rope.headY * density).toFloat())
    drawCircle(Accent.copy(alpha = 0.16f * fade.toFloat()), radius = 9 * density, center = head)
    drawCircle(Accent.copy(alpha = fade.toFloat()), radius = 3.5f * density, center = head)
}

/** Fine paper grain: a fixed scatter of tiny ink dots, drawn once. */
@Composable
private fun RopeTextTrailGrain(modifier: Modifier) {
    Canvas(modifier) {
        var seed = 0x9E3779B9uL
        fun next(): Double {
            seed = seed * 6364136223846793005uL + 1442695040888963407uL
            return (seed shr 33).toDouble() / (1L shl 31).toDouble()
        }
        val w = size.width / density
        val h = size.height / density
        val count = (w * h / 110).toInt()
        repeat(count) {
            val x = next() * w
            val y = next() * h
            val r = 0.35 + next() * 0.55
            val a = 0.04 + next() * 0.07
            drawCircle(Ink.copy(alpha = a.toFloat()), radius = (r * density).toFloat(), center = Offset((x * density).toFloat(), (y * density).toFloat()))
        }
    }
}

/** The paper is light, so the system bars get dark icons while the block is open (the gallery uses light ones). */
@Composable
private fun RopeTextTrailSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
