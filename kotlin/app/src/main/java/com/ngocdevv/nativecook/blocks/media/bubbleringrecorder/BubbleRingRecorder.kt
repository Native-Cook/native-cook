package com.ngocdevv.nativecook.blocks.media.bubbleringrecorder

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import com.ngocdevv.nativecook.blocks.media.bubbleringrecorder.BubbleRingMotion as M

/*
 Bubble Ring Recorder — a voice recorder whose outline wobbles like a soap bubble with the voice.
 A port of the SwiftUI block (BubbleRingRecorder.swift); the numbers are the same.

 Look: a charcoal screen (#141416, a soft lighter pool behind the orb). Top left a big thin timer
 (Barlow Condensed ExtraLight 104 sp, the minutes in white 42 %, ":ss" in white) with a caption
 under it; a three-line menu top right. In the middle a glowing red record orb (radius 20 % of the
 width, #FF5C7C → #FF1F4B → #D4093A, "RECORD" set vertically in it) inside a faint 1 dp circle
 (radius 29 % of the width). Under it three small glyphs (note, waveform, list), then a list of
 three takes: "Untitled take" (the last take, with its waveform), "Chorus hum", "Kitchen demo".

 Recording: the orb collapses into a 9 dp red dot that pulses with the level; the faint circle
 becomes a 2.25 dp white line whose radius is pushed in and out by four low harmonics scaled by
 the level (up to ±14 %), with a thinner grey ghost of the shape 0.22 s behind and a soft glow.
 The timer counts, the first row turns into a live "Recording" row whose waveform fills left to
 right. Stopping: the orb springs back out of the dot (stiffness 170, damping 16, ~8 % overshoot),
 the ring gets a jelly kick (radius −7 %, wobble 8 %, e^(−4.5u)·sin(11u)) and fades back to the
 faint circle over 0.6 s; the row shows the new take.

 autoPlay timeline (LOOP = 9 s):
   0.0 – 1.2   rest; the orb's glow breathes (period 2.25 s, four per loop).
   1.2         tap: a ripple leaves the orb (0.7 s); the orb collapses into the dot (0.5 s,
               ease-in-out) while the ring turns white (0.45 s).
   1.2 – 6.4   a scripted 5.2 s take: nine syllables of a voice, a quiet floor between them.
   6.4         tap on the dot: a red ripple (0.6 s), the orb springs back, the ring jiggles out.
   7.4 – 9.0   rest; at 9.0 the frame is the one at 0.0.

 Touch: tap the orb (or the dot) to start or stop a take. Hold anywhere while recording and the
 ring bulges towards the finger and the voice gets louder. 2 s after the last touch the loop
 starts again from rest (a take left running stops itself 8 s after the last touch).
 Reduced motion: no breathing, no wobble; start and stop cross-fade the orb and the dot over
 0.25 s and the ring is a plain circle while recording.
 */

private const val FONT_ASSET = "bubbleringrecorder/bubbleringrecorder_BarlowCondensed-ExtraLight.ttf"
private const val ROW_HEIGHT = 68f
private const val BAR_COUNT = 30
private val Background = Color(0xFF141416)
private val Pool = Color(0xFF29292B)
private val Red = Color(0xFFFF1F4B)
private val RedText = Color(0xFFFF4D6D)

private fun nowSeconds() = System.nanoTime() / 1e9

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun time(seconds: Double): String {
    val s = seconds.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** Positions, in dp, for a screen of [w] × [h]. */
private class BubbleRingLayout(val w: Float, val h: Float, val top: Float, val bottom: Float) {
    val orb = 0.2 * w
    val ring = 0.29 * w
    val rowsTop: Float
    val iconsY: Float
    val cx = w / 2
    val cy: Float

    init {
        val timerBottom = top + 4 + 146
        val listBottom = h - max(bottom, 16f) - 4
        rowsTop = listBottom - 3 * ROW_HEIGHT
        iconsY = rowsTop - 66
        cy = (timerBottom + iconsY - 12) / 2
    }
}

private class Shown(val f: M.Frame, val bulge: Double, val bulgeAngle: Double, val lastTake: Double)

private class BubbleRingController(val autoPlay: Boolean, val reduced: Boolean) {
    var appear = nowSeconds()
    /** The loop's t = 0 while autoPlay runs, and when its first stop happens. */
    var autoStart by mutableStateOf<Double?>(null)
    var firstAutoStop by mutableDoubleStateOf(Double.MAX_VALUE)
    /** A take made by hand. */
    var recStart by mutableStateOf<Double?>(null)
    var recStop by mutableStateOf<Double?>(null)
    var lastTake by mutableDoubleStateOf(M.TAKE)
    /** A finger held on the screen: x, y (dp) and when. */
    var press by mutableStateOf<DoubleArray?>(null)
    /** The release: x, y, when, how far the pull had got. */
    var released by mutableStateOf<DoubleArray?>(null)
    var reducedLive by mutableStateOf(false)
    var frame by mutableDoubleStateOf(nowSeconds())
    var resume: Job? = null

    val recording get() = recStart != null && recStop == null

    fun clock(now: Double): M.Clock {
        val c = now - appear
        val start = autoStart
        if (start != null && now >= start) {
            val t = now - start
            val e = t - floor(t / M.LOOP) * M.LOOP
            return M.autoClock(e, c, lastTake, now >= firstAutoStop)
        }
        val rs = recStart ?: return M.Clock(null, null, lastTake, c)
        val s = now - rs
        val stop = recStop
        if (stop != null) {
            val u = now - stop
            if (u > 3) return M.Clock(null, null, lastTake, c)
            return M.Clock(s, u, lastTake, c)
        }
        return M.Clock(s, null, lastTake, c)
    }

    /** The finger's pull on the ring: 0…1, x, y. */
    fun pull(now: Double): DoubleArray? {
        press?.let { return doubleArrayOf(M.easeOut((now - it[2]) / 0.2), it[0], it[1]) }
        released?.let {
            val a = it[3] * (1 - M.easeOut((now - it[2]) / 0.35))
            return if (a > 0.001) doubleArrayOf(a, it[0], it[1]) else null
        }
        return null
    }

    fun shown(now: Double, layout: BubbleRingLayout): Shown {
        val k = clock(now)
        val f = M.frame(k, layout.orb, reduced)
        var bulge = 0.0
        var angle = 0.0
        val p = pull(now)
        if (!reduced && p != null && f.presence > 0) {
            val dx = p[1] - layout.cx
            val dy = p[2] - layout.cy
            val reach = M.smoothstep(0.3 * layout.ring, 0.9 * layout.ring, hypot(dx, dy))
            bulge = 0.16 * p[0] * f.presence * reach
            angle = atan2(dy, dx)
            // A finger on the screen is a louder voice.
            f.wobble += 0.04 * p[0] * f.presence
            f.ringScale += 0.03 * p[0] * f.presence
            f.level = min(1.0, f.level + 0.35 * p[0])
            if (f.recording > 0) f.orbRadius = M.DOT_RADIUS * (1 + 0.35 * f.level)
        }
        return Shown(f, bulge, angle, k.lastTake)
    }

    /** Freezes whatever autoPlay shows into the hand-made state. */
    fun takeOver(now: Double) {
        resume?.cancel()
        if (autoStart == null) return
        val k = clock(now)
        recStart = k.sinceStart?.let { now - it }
        recStop = k.sinceStop?.let { now - it }
        lastTake = k.lastTake
        autoStart = null
    }

    /** Starts or stops a take; true when it started one. */
    fun toggle(now: Double): Boolean {
        val rs = recStart
        val started = if (recording && rs != null) {
            lastTake = now - rs
            recStop = now
            false
        } else {
            recStart = now
            recStop = null
            true
        }
        if (reduced) reducedLive = true
        return started
    }

    /** Starts the loop inside its rest, in step with the breathing glow. */
    fun restart(now: Double) {
        val m = floor((now - appear - 7.4) / M.BREATH)
        val start = appear + m * M.BREATH
        autoStart = start
        firstAutoStop = start + M.LOOP + M.STOP_AT
        recStart = null
        recStop = null
    }
}

@Composable
fun BubbleRingRecorder(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val family = remember { FontFamily(Font(FONT_ASSET, context.assets)) }
    val c = remember(reduced) { BubbleRingController(autoPlay, reduced) }
    val scope = rememberCoroutineScope()
    val painter = remember { BubbleRingPainter() }

    LaunchedEffect(c) {
        c.appear = nowSeconds()
        c.frame = c.appear
        if (autoPlay && !reduced) {
            c.autoStart = c.appear
            c.firstAutoStop = c.appear + M.STOP_AT
        }
    }
    val live = !reduced || c.reducedLive || c.press != null
    LaunchedEffect(c, live) {
        if (!live) return@LaunchedEffect
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    fun haptic(started: Boolean) {
        view.performHapticFeedback(if (started) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.LONG_PRESS)
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (reduced && c.reducedLive && !c.recording) {
            scope.launch {
                delay(400)
                if (!c.recording) c.reducedLive = false
            }
        }
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            if (c.recording) {
                // A take left running stops itself 8 s after the last touch.
                delay(6000)
                haptic(c.toggle(nowSeconds()))
                delay(2000)
            }
            c.restart(nowSeconds())
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Background)) {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val layout = remember(maxWidth, maxHeight, top, bottom) { BubbleRingLayout(maxWidth.value, maxHeight.value, top, bottom) }
        val density = LocalDensity.current.density
        val w = layout.w

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    role = Role.Button
                    contentDescription = if (c.recording) "Stop recording" else "Record"
                }
                .pointerInput(c, layout) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = down.position / density
                        val pressedAt = nowSeconds()
                        c.takeOver(pressedAt)
                        c.press = doubleArrayOf(start.x.toDouble(), start.y.toDouble(), pressedAt)
                        c.released = null
                        var last = start
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            last = change.position / density
                            c.press = doubleArrayOf(last.x.toDouble(), last.y.toDouble(), pressedAt)
                        }
                        val now = nowSeconds()
                        val amount = c.pull(now)?.get(0) ?: 0.0
                        c.released = doubleArrayOf(last.x.toDouble(), last.y.toDouble(), now, amount)
                        c.press = null
                        val moved = (last - start).getDistance()
                        val d = hypot(start.x - layout.cx, start.y - layout.cy)
                        if (moved < 10 && now - pressedAt < 0.4 && d < layout.ring * 1.15) haptic(c.toggle(now))
                        scheduleResume()
                    }
                },
        ) {
            painter.draw(this, c.shown(c.frame, layout), layout, density)
        }

        // Timer and caption.
        val secs by remember(c, layout) { derivedStateOf { c.shown(c.frame, layout).f.seconds.toInt() } }
        Column(Modifier.offset(x = 24.dp, y = (top + 4).dp)) {
            Row(Modifier.height(112.dp), verticalAlignment = Alignment.CenterVertically) {
                val style = TextStyle(fontFamily = family, fontSize = 104.sp)
                BasicText("${secs / 60}", style = style.copy(color = Color.White.copy(alpha = 0.42f)), maxLines = 1)
                BasicText(":%02d".format(secs % 60), style = style.copy(color = Color.White), maxLines = 1)
            }
            Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
                val caption = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
                BasicText(
                    "Tap the orb to record",
                    Modifier.graphicsLayer { alpha = 1f - liveness(c.shown(c.frame, layout).f) },
                    style = caption.copy(color = Color.White.copy(alpha = 0.5f)),
                    maxLines = 1,
                )
                Row(
                    Modifier.graphicsLayer { alpha = liveness(c.shown(c.frame, layout).f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .graphicsLayer { alpha = (0.55 + 0.45 * c.shown(c.frame, layout).f.level).toFloat() }
                            .background(Red, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicText("Recording · 48 kHz", style = caption.copy(color = RedText), maxLines = 1)
                }
            }
        }

        // The list's titles; the icons, waveforms and dividers are drawn on the canvas.
        BasicText(
            "RECORDINGS",
            Modifier.offset(x = 24.dp, y = (layout.rowsTop - 26).dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, color = Color.White.copy(alpha = 0.4f)),
        )
        val textWidth = (w - 256).dp
        val lastTake by remember(c, layout) { derivedStateOf { time(c.shown(c.frame, layout).lastTake) } }
        Box(Modifier.offset(x = 74.dp, y = layout.rowsTop.dp).width(textWidth).height(ROW_HEIGHT.dp)) {
            BubbleRingTitles(
                "Untitled take", "$lastTake · Today, 9:41", Color.White,
                Modifier.graphicsLayer { alpha = 1f - liveness(c.shown(c.frame, layout).f) },
            )
            BubbleRingTitles(
                "Recording", "${time(secs.toDouble())} · Now", RedText,
                Modifier.graphicsLayer { alpha = liveness(c.shown(c.frame, layout).f) },
            )
        }
        BubbleRingRows.forEachIndexed { i, row ->
            Box(Modifier.offset(x = 74.dp, y = (layout.rowsTop + (i + 1) * ROW_HEIGHT).dp).width(textWidth).height(ROW_HEIGHT.dp)) {
                BubbleRingTitles(row.title, row.detail, Color.White, Modifier)
            }
        }
    }
}

/** 0 at rest, 1 while a take records (follows the ring's presence). */
private fun liveness(f: M.Frame) = if (f.recording > 0) f.presence.toFloat() else 0f

private class BubbleRingRow(val title: String, val detail: String, val seed: Double)

private val BubbleRingRows = listOf(
    BubbleRingRow("Chorus hum", "0:14 · Oct 12", 1.3),
    BubbleRingRow("Kitchen demo", "0:34 · Oct 10", 4.1),
)

@Composable
private fun BubbleRingTitles(title: String, detail: String, tint: Color, modifier: Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        BasicText(
            title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = tint),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        BasicText(
            detail, style = TextStyle(fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.45f), fontFeatureSettings = "tnum"),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Draws the background, the orb, the ring, the glyphs and the list's icons and waveforms. */
private class BubbleRingPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val letters = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD
    }
    private val path = Path()
    private val blurs = mutableMapOf<Int, BlurMaskFilter>()

    /** A blur of SwiftUI radius [r] (taken as a Gaussian sigma), in pixels. */
    private fun blur(r: Float): BlurMaskFilter = blurs.getOrPut((r * 4).toInt()) {
        BlurMaskFilter(max(0.1f, (r - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
    }

    private fun Color.argb(alpha: Double) = copy(alpha = (this.alpha * alpha).toFloat().coerceIn(0f, 1f)).toArgb()

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, s: Shown, l: BubbleRingLayout, d: Float) {
        val f = s.f
        val canvas = scope.drawContext.canvas.nativeCanvas
        val cx = l.cx * d
        val cy = l.cy * d
        // A lighter pool behind the orb.
        scope.drawRect(Background)
        scope.drawRect(Brush.radialGradient(listOf(Pool, Pool.copy(alpha = 0f)), center = Offset(cx, cy), radius = l.w * 0.85f * d))

        // The orb's glow, and the dot's.
        val r = f.orbRadius
        glow.maskFilter = blur(26 * d)
        glow.color = Red.argb((0.42 + 0.22 * f.glow) * f.orbness)
        canvas.drawCircle(cx, cy, (r * 1.12 * d).toFloat(), glow)
        if (f.orbness < 1) {
            glow.maskFilter = blur(9 * d)
            glow.color = Red.argb(0.55 * (1 - f.orbness))
            canvas.drawCircle(cx, cy, (r * (2.0 + 0.8 * f.level) * d).toFloat(), glow)
        }
        // The orb itself.
        val rr = (r * d).toFloat()
        scope.drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFF5C7C), 0.55f to Color(0xFFFF1F4B), 1f to Color(0xFFD4093A),
                center = Offset(cx - 0.3f * rr, cy - 0.35f * rr), radius = 1.45f * rr,
            ),
            radius = rr, center = Offset(cx, cy),
        )
        // "RECORD", set vertically, shrinking and fading with the orb.
        val scale = r / l.orb
        val textAlpha = M.smoothstep(0.55, 0.95, f.orbness)
        if (textAlpha > 0.01) {
            val step = 0.19 * l.orb * scale * d
            letters.textSize = (11 * scale * d).toFloat()
            letters.color = Color.White.argb(0.88 * textAlpha)
            val fm = letters.fontMetrics
            val mid = (fm.ascent + fm.descent) / 2
            "RECORD".forEachIndexed { i, ch ->
                canvas.drawText(ch.toString(), cx, (cy + (i - 2.5) * step).toFloat() - mid, letters)
            }
        }

        // The ring: a faint circle at rest, a wobbling white outline while recording.
        ring(path, cx, cy, l.ring * f.ringScale * d, f.wobble, f.shapeTime, s.bulge * l.ring * d, s.bulgeAngle)
        if (f.presence > 0.01) {
            val ghost = Path()
            ring(ghost, cx, cy, l.ring * (f.ringScale - 0.01) * d, f.ghostWobble, f.shapeTime - M.GHOST_LAG, s.bulge * l.ring * 0.8 * d, s.bulgeAngle)
            stroke.strokeWidth = 1.25f * d
            stroke.color = Color.White.argb(0.32 * f.presence)
            canvas.drawPath(ghost, stroke)
            glowStroke.maskFilter = blur(5 * d)
            glowStroke.strokeWidth = 5 * d
            glowStroke.color = Color.White.argb(0.35 * f.presence)
            canvas.drawPath(path, glowStroke)
        }
        stroke.strokeWidth = ((1 + 1.25 * f.presence) * d).toFloat()
        stroke.color = Color.White.argb(0.1 + 0.9 * f.presence)
        canvas.drawPath(path, stroke)

        // A tap's ripple: white out of the orb when a take starts, red out of the dot when it stops.
        f.ripple?.let { p ->
            val e = M.easeOut(p)
            val radius = if (f.rippleIsStop) M.DOT_RADIUS + (l.orb * 1.3 - M.DOT_RADIUS) * e else l.orb * (1 + 0.45 * e)
            stroke.strokeWidth = 1.5f * d
            stroke.color = (if (f.rippleIsStop) Red else Color.White).argb(0.45 * (1 - p))
            canvas.drawCircle(cx, cy, (radius * d).toFloat(), stroke)
        }

        glyphs(canvas, l.w / 2 * d, l.iconsY * d, d)
        list(canvas, f, s.lastTake, l, d)
    }

    /** A closed outline of 180 points: radius × (1 + wobble × shape), plus a bump towards a finger. */
    private fun ring(p: Path, cx: Float, cy: Float, radius: Double, wobble: Double, time: Double, bulge: Double, bulgeAngle: Double) {
        p.reset()
        val n = 180
        for (i in 0..n) {
            val th = 2 * PI * (i % n) / n
            var r = radius * (1 + wobble * M.shape(th, time))
            if (bulge > 0) {
                var dd = th - bulgeAngle
                dd = atan2(sin(dd), cos(dd))
                r += bulge * exp(-(dd / 0.55) * (dd / 0.55))
            }
            val x = (cx + r * cos(th)).toFloat()
            val y = (cy + r * sin(th)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    private fun glyphs(canvas: android.graphics.Canvas, x: Float, y: Float, d: Float) {
        val dim = Color.White.argb(0.45)
        stroke.strokeWidth = 1.6f * d
        stroke.color = dim
        // A note.
        val nx = x - 44 * d
        path.reset()
        path.moveTo(nx + 2 * d, y + 6 * d)
        path.lineTo(nx + 2 * d, y - 9 * d)
        path.lineTo(nx + 7 * d, y - 6 * d)
        canvas.drawPath(path, stroke)
        fill.color = dim
        canvas.drawOval(nx - 4 * d, y + 3 * d, nx + 2.5f * d, y + 8.5f * d, fill)
        // A waveform (the selected one).
        stroke.strokeWidth = 1.5f * d
        stroke.color = android.graphics.Color.WHITE
        floatArrayOf(4f, 10f, 16f, 8f, 18f, 10f, 5f).forEachIndexed { i, h ->
            val bx = x + (i - 3) * 3.4f * d
            canvas.drawLine(bx, y - h / 2 * d, bx, y + h / 2 * d, stroke)
        }
        // A list.
        stroke.strokeWidth = 1.6f * d
        stroke.color = dim
        val lx = x + 44 * d
        floatArrayOf(14f, 10f, 14f).forEachIndexed { i, w ->
            val ly = y + (-6 + i * 6) * d
            canvas.drawLine(lx + (-7 + 14 - w) * d, ly, lx + 7 * d, ly, stroke)
        }
    }

    private fun list(canvas: android.graphics.Canvas, f: M.Frame, lastTake: Double, l: BubbleRingLayout, d: Float) {
        val w = l.w
        val live = liveness(f).toDouble()
        // Dividers.
        fill.color = Color.White.argb(0.07)
        for (i in 0..3) {
            val y = (l.rowsTop + i * ROW_HEIGHT) * d
            canvas.drawRect(24 * d, y, (w - 24) * d, y + d, fill)
        }
        for (i in 0..2) {
            val mid = (l.rowsTop + i * ROW_HEIGHT + ROW_HEIGHT / 2) * d
            val ix = 42 * d
            val liveRow = if (i == 0) live else 0.0
            // The icon: a play button, or a pulsing red dot while the first row records.
            stroke.strokeWidth = d
            stroke.color = Color.White.argb(0.18 * (1 - liveRow))
            canvas.drawCircle(ix, mid, 17.5f * d, stroke)
            if (liveRow > 0) {
                stroke.color = Red.argb(0.5 * liveRow)
                canvas.drawCircle(ix, mid, 17.5f * d, stroke)
                fill.color = Red.argb(liveRow)
                canvas.drawCircle(ix, mid, (5 * (1 + 0.3 * f.level) * d).toFloat(), fill)
            }
            fill.color = Color.White.argb(1 - liveRow)
            path.reset()
            val tx = ix - 5 * d + 1.5f * d
            path.moveTo(tx, mid - 6 * d)
            path.lineTo(tx + 10 * d, mid)
            path.lineTo(tx, mid + 6 * d)
            path.close()
            canvas.drawPath(path, fill)
            // The waveform: 30 bars, 2 dp wide, 4 dp apart, 3…22 dp tall; dots where nothing is recorded yet.
            val wx = (w - 168) * d
            val bars: Array<Double?> = if (i == 0) {
                M.bars(BAR_COUNT, if (f.recording > 0) f.seconds else lastTake)
            } else {
                Array(BAR_COUNT) { j -> M.staticBar(j, BubbleRingRows[i - 1].seed) }
            }
            val color = if (i == 0) Color.White.argb(0.55 + 0.35 * live) else Color.White.argb(0.3)
            bars.forEachIndexed { j, v ->
                val bx = wx + j * 4 * d
                if (v != null) {
                    val h = ((3 + 19 * v) * d).toFloat()
                    fill.color = color
                    canvas.drawRoundRect(bx, mid - h / 2, bx + 2 * d, mid + h / 2, d, d, fill)
                } else {
                    fill.color = Color.White.argb(0.15)
                    canvas.drawCircle(bx + d, mid, d, fill)
                }
            }
            // The row's menu.
            fill.color = Color.White.argb(0.45)
            for (k in -1..1) canvas.drawCircle((w - 30) * d, mid + k * 6 * d, 1.5f * d, fill)
        }
        // The screen's menu, top right: 20, 20 and 13 dp lines, 1.6 dp thick, right-aligned.
        fill.color = Color.White.argb(0.85)
        floatArrayOf(20f, 20f, 13f).forEachIndexed { i, lw ->
            val y = (l.top + 37.4f + i * 6.6f) * d
            canvas.drawRoundRect((w - 26 - lw) * d, y - 0.8f * d, (w - 26) * d, y + 0.8f * d, 0.8f * d, 0.8f * d, fill)
        }
    }
}
