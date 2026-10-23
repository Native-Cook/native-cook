package com.ngocdevv.nativecook.blocks.text.karaokelyricshighlight

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Karaoke Lyrics Highlight — a "now playing" lyrics screen that sings its chorus word by word. A
 port of the SwiftUI block (KaraokeLyricsHighlight.swift); the numbers are the same.

 Look: an invented song ("Paper Satellites" by Lumen Fair) on a drifting violet–magenta field.
 Big left-aligned lines in Poppins Bold 32 dp (rows 38 dp, 22 dp between lines) fill the screen;
 the line being sung sits with its top at 30 % of the screen height. A header (art, title,
 artist, a small live waveform) sits at the top and a slim scrubber with times at the bottom;
 the lyrics fade out under both.

 Per line, by its distance d from the focus (negative: already sung):
   sung ink   white × 1 at the focus, down to 0.25 one line above;
   unsung ink white × 0.35 at the focus, 0.25 one line below, 0.15 two below;
   blur       2.5 one line above, 1.5 per line below (up to 3), as a Gaussian sigma.
 A word fills left to right with white: a soft 16 dp edge, and a glow (sigma 5) on the 28 dp
 behind the edge. A word being sung lifts 2 dp and grows 3 % (in 0.12 s, out 0.2 s); the held
 "Ohhh," fills over 0.95 s and its letters wave (2.5 dp, 1.6 Hz, 0.7 rad apart).

 autoPlay timeline (LOOP = 10 s, see KaraokeLyricsHighlightModel.kt for the word times):
   0.0 / 2.5 / 5.0 / 7.5   a chorus line starts; its words fill over the next ~2 s.
   +1.95 … +2.5            the list scrolls one line up (ease-out-back, small overshoot).
   9.5 … 10.0              the playhead glides from 1:21 back to 1:12 (the chorus loops).
   10.0                    one chorus further down: the same frame as t = 0.
 The background blobs drift once per loop and the header waveform's bars move a whole number of
 times per loop.

 Touch: dragging scrolls the lyrics freely; the fill pauses and the lines un-blur while the finger
 is down (0.25 s), and blur again after release (0.4 s). Tapping a line jumps the song to it: the
 loop carries on from that line, the list gliding there (0.55 s, ease-out-back) and its fill
 restarting. 2 s after a drag, the list eases back to the chorus's first line over 0.6 s (the
 fill rewinds with it) and the loop starts again.
 Reduced motion: no loop, no drift, no lift or wave; the first line shows fully sung. Dragging
 still scrolls and a tap moves the focus to a line, sung at once.
 */

private const val FONT_ASSET = "karaokelyricshighlight/karaokelyricshighlight_Poppins-Bold.ttf"

private fun nowSeconds() = System.nanoTime() / 1e9

/** What the screen shows at a moment. */
private class KaraokeShown(
    val scroll: Double,
    /** The line being sung and how far into its slot; while returning, blended towards [restLine]. */
    val line: Int,
    val tau: Double,
    val restLine: Int,
    val rest: Double,
    val playhead: Double,
    /** Loop time for the drift and the waveform. */
    val clock: Double,
    /** 1 normally, 0 while a finger is dragging the lyrics. */
    val blurScale: Double,
)

private class KaraokeController(val reduced: Boolean, val layout: () -> KaraokeLayout?) {
    /** The loop's t = 0, and the absolute line it starts on. */
    var autoStart by mutableStateOf<Double?>(null)
    var origin by mutableIntStateOf(0)
    /** A tap's glide: when, and the difference in lines between where the list was and the tapped line. */
    var correctionAt by mutableStateOf<Double?>(null)
    var correctionLines by mutableDoubleStateOf(0.0)
    /** The manual state: scroll offset in dp, the line being sung and how far, the playhead. */
    var manualOffset by mutableDoubleStateOf(0.0)
    var frozenLine by mutableIntStateOf(0)
    var frozenTau by mutableDoubleStateOf(if (reduced) KaraokeTimeline.SLOT else 0.0)
    var frozenPlayhead by mutableDoubleStateOf(KaraokeSong.CHORUS_AT)
    /** The ease back: when, from which scroll position, from which playhead. */
    var returning by mutableStateOf<DoubleArray?>(null)
    var pressedAt by mutableStateOf<Double?>(null)
    var releasedAt by mutableStateOf<Double?>(null)
    var frame by mutableDoubleStateOf(nowSeconds())
    var resume: Job? = null

    fun shown(at: Double): KaraokeShown {
        var blurScale = 1.0
        val pressed = pressedAt
        if (pressed != null) {
            blurScale = 1 - KaraokeTimeline.smoothstep(0.0, 0.25, at - pressed)
            val released = releasedAt
            if (released != null) blurScale = max(blurScale, KaraokeTimeline.smoothstep(0.0, 0.4, at - released))
        }
        val start = autoStart
        if (start != null && at >= start && !reduced) {
            val t = at - start
            val cycles = floor(t / KaraokeTimeline.LOOP)
            val e = t - cycles * KaraokeTimeline.LOOP
            val base = origin + 4 * cycles.toInt()
            var s = base + KaraokeTimeline.scroll(e)
            val c = correctionAt
            if (c != null) {
                val u = (at - c) / 0.55
                if (u < 1) s += correctionLines * (1 - KaraokeTimeline.easeOutBack(u))
            }
            val k = KaraokeTimeline.singingLine(e)
            val tau = e - k * KaraokeTimeline.SLOT
            return KaraokeShown(s, base + k, tau, base + k, 0.0, KaraokeTimeline.playhead(e), e, blurScale)
        }
        val r = returning
        if (r != null) {
            val u = KaraokeTimeline.easeInOut((at - r[0]) / 0.6)
            return KaraokeShown(
                r[1] + (origin - r[1]) * u, frozenLine, frozenTau, origin, u,
                r[2] + (KaraokeSong.CHORUS_AT - r[2]) * u, 0.0, blurScale,
            )
        }
        val l = layout()
        val s = l?.position(manualOffset) ?: 0.0
        return KaraokeShown(s, frozenLine, frozenTau, frozenLine, 0.0, frozenPlayhead, 0.0, blurScale)
    }

    /** Freezes whatever plays into the manual state. */
    fun takeOver(now: Double) {
        resume?.cancel()
        val l = layout() ?: return
        val s = shown(now)
        manualOffset = l.offset(s.scroll)
        frozenLine = if (s.rest >= 1) s.restLine else s.line
        frozenTau = if (s.rest >= 1) 0.0 else s.tau
        frozenPlayhead = s.playhead
        autoStart = null
        returning = null
        correctionAt = null
    }
}

/** How much word [w] of absolute line [i] is filled. */
private fun fill(i: Int, w: KaraokeWord, s: KaraokeShown): Double {
    fun base(line: Int, tau: Double) = if (i < line) 1.0 else if (i > line) 0.0 else KaraokeTimeline.fill(w, tau)
    val a = base(s.line, s.tau)
    if (s.rest <= 0) return a
    return a + (base(s.restLine, 0.0) - a) * s.rest
}

private fun time(seconds: Double): String {
    val s = seconds.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun KaraokeLyricsHighlight(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val typeface = remember { Typeface.createFromAsset(context.assets, FONT_ASSET) }
    val family = remember { FontFamily(Font(FONT_ASSET, context.assets)) }
    // The current layout, for the controller and the resume; a plain holder, not state.
    val layoutRef = remember { arrayOfNulls<KaraokeLayout>(1) }
    val c = remember(reduced) { KaraokeController(reduced) { layoutRef[0] } }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) {
        if (autoPlay && !reduced) c.autoStart = nowSeconds()
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            val l = layoutRef[0] ?: return@launch
            val now = nowSeconds()
            val s = l.position(c.manualOffset)
            // The nearest first line of the chorus.
            c.origin = Math.round(s / 4).toInt() * 4
            c.returning = doubleArrayOf(now, s, c.frozenPlayhead)
            c.autoStart = now + 0.6
            c.correctionAt = null
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val density = LocalDensity.current.density
        val layout = remember(width, typeface) { KaraokeLayout(width, typeface) }
        layoutRef[0] = layout
        val painter = remember { KaraokePainter(typeface) }
        val headerBottom = top + 12 + 48
        val scrubTop = height - max(bottomInset, 20f) - 44

        // Background: three drifting blobs and a darkening towards the bottom.
        Canvas(Modifier.fillMaxSize()) {
            val clock = c.shown(c.frame).clock
            val a = 2 * PI * clock / KaraokeTimeline.LOOP
            val k = if (reduced) 0.0 else 1.0
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF12062A))
            fun blob(x: Double, y: Double, r: Float, color: Color) =
                drawRect(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = Offset(x.toFloat(), y.toFloat()), radius = r))
            blob(w * (0.25 + k * 0.08 * sin(a)), h * (0.22 + k * 0.05 * cos(a)), w * 0.95f, Color(0xFFD53A9D).copy(alpha = 0.75f))
            blob(w * (0.8 + k * 0.07 * cos(a)), h * (0.7 + k * 0.06 * sin(a)), w * 1.0f, Color(0xFF5A1FA8).copy(alpha = 0.9f))
            blob(w * (0.92 - k * 0.05 * sin(a)), h * 0.12, w * 0.6f, Color(0xFFFF7A59).copy(alpha = 0.35f))
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.45f))))
        }

        // Lyrics.
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = KaraokeSong.chorus.joinToString(", ") { l -> l.joinToString(" ") { it.text } } }
                .pointerInput(c, layout) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        c.takeOver(nowSeconds())
                        val startOffset = c.manualOffset
                        c.pressedAt = null
                        c.releasedAt = null
                        var moved = false
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            last = change.position
                            val d = (change.position - down.position) / density
                            if (!moved && hypot(d.x, d.y) > 6f) {
                                moved = true
                                c.pressedAt = nowSeconds()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            if (moved) {
                                c.manualOffset = startOffset - d.y
                                change.consume()
                            }
                        }
                        val now = nowSeconds()
                        if (moved) {
                            c.releasedAt = now
                            scheduleResume()
                        } else {
                            // Tap: jump the song to the line under the finger; the loop carries on from it.
                            val y = last.y / density
                            val s = layout.position(c.manualOffset)
                            val focus = height * 0.3
                            val first = floor(s).toInt() - 4
                            var hit: Int? = null
                            for (i in first..first + 12) {
                                val lineTop = focus + layout.top(i) - layout.offset(s)
                                if (y >= lineTop - KaraokeLayout.GAP / 2 && y < lineTop + layout.line(i).height + KaraokeLayout.GAP / 2) hit = i
                            }
                            if (hit == null) {
                                scheduleResume()
                            } else {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                val k = ((hit % 4) + 4) % 4
                                if (reduced) {
                                    c.manualOffset = layout.offset(hit.toDouble())
                                    c.frozenLine = hit
                                    c.frozenTau = KaraokeTimeline.SLOT
                                } else {
                                    c.origin = hit - k
                                    c.autoStart = now - k * KaraokeTimeline.SLOT
                                    c.correctionLines = s - hit
                                    c.correctionAt = now
                                    c.pressedAt = null
                                }
                            }
                        }
                    }
                },
        ) {
            painter.draw(this, c.shown(c.frame), layout, reduced, density, height * 0.3f, headerBottom, scrubTop)
        }

        // Header.
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .offset(y = (top + 12).dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Album art: a gradient square with a small orbit.
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF7A59), Color(0xFFD53A9D), Color(0xFF2B1055)))),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(26.dp).border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape))
                Box(Modifier.offset(x = 9.dp, y = (-9).dp).size(6.dp).background(Color.White, CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(KaraokeSong.TITLE, style = TextStyle(fontFamily = family, fontSize = 17.sp, color = Color.White), maxLines = 1)
                Spacer(Modifier.height(2.dp))
                BasicText(KaraokeSong.ARTIST, style = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f)), maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            // A small live waveform: five bars, each moving a whole number of times per loop.
            Canvas(Modifier.size(27.dp, 18.dp)) {
                val clock = c.shown(c.frame).clock
                val cycles = doubleArrayOf(3.0, 5.0, 4.0, 6.0, 3.0)
                for (b in 0 until 5) {
                    val h = if (reduced) 8.0 else 6 + 12 * (0.5 + 0.5 * sin(2 * PI * cycles[b] * clock / KaraokeTimeline.LOOP + b * 1.3))
                    val bh = h.toFloat() * density
                    drawRoundRect(
                        Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(b * 6 * density, (size.height - bh) / 2),
                        size = Size(3 * density, bh),
                        cornerRadius = CornerRadius(1.5f * density),
                    )
                }
            }
        }

        // Scrubber.
        val w = width - 48
        Canvas(
            Modifier
                .offset(x = 24.dp, y = (scrubTop - 6).dp)
                .size(w.dp, 16.dp),
        ) {
            val s = c.shown(c.frame)
            val len = KaraokeSong.LENGTH
            val p = (s.playhead / len).toFloat()
            val a = (KaraokeSong.CHORUS_AT / len).toFloat()
            val b = ((KaraokeSong.CHORUS_AT + KaraokeTimeline.LOOP - KaraokeTimeline.REWIND) / len).toFloat()
            val px = size.width
            val y0 = 6 * density
            drawRoundRect(Color.White.copy(alpha = 0.2f), Offset(0f, y0), Size(px, 4 * density), CornerRadius(2 * density))
            drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(0f, y0), Size(px * p, 4 * density), CornerRadius(2 * density))
            // The chorus being looped, marked above the track.
            drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(px * a, 0f), Size(px * (b - a), 2 * density), CornerRadius(density))
            drawCircle(Color.White, 6 * density, Offset(px * p, y0 + 2 * density))
        }
        val elapsed by remember(c) { derivedStateOf { time(c.shown(c.frame).playhead) } }
        val left by remember(c) { derivedStateOf { "-" + time(KaraokeSong.LENGTH - c.shown(c.frame).playhead) } }
        Row(
            Modifier
                .offset(x = 24.dp, y = (scrubTop + 14).dp)
                .width(w.dp),
        ) {
            val style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f), fontFeatureSettings = "tnum")
            BasicText(elapsed, style = style)
            Spacer(Modifier.weight(1f))
            BasicText(left, style = style)
        }
    }
}

/** Draws the lyric lines with the native canvas: layers for the soft fill edge, the glow and the fades. */
private class KaraokePainter(typeface: Typeface) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val blurs = mutableMapOf<Int, BlurMaskFilter>()

    /**
     * A Gaussian blur of the given sigma as a mask filter (Android's radius ≈ (sigma − 0.5) / 0.577).
     * The canvas is already scaled to dp and a mask filter follows the canvas matrix, so [sigma] is
     * in dp: multiplying it by the density as well blurred everything 2.6 times too much.
     */
    private fun blur(sigma: Float): BlurMaskFilter? {
        if (sigma < 0.12f) return null
        val key = (sigma * 8).roundToInt()
        return blurs.getOrPut(key) { BlurMaskFilter(max(0.1f, (key / 8f - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL) }
    }

    fun draw(
        scope: DrawScope,
        s: KaraokeShown,
        layout: KaraokeLayout,
        reduced: Boolean,
        density: Float,
        focus: Float,
        headerBottom: Float,
        scrubTop: Float,
    ) {
        val canvas = scope.drawContext.canvas.nativeCanvas
        val w = scope.size.width
        val h = scope.size.height
        val screenHeight = h / density
        canvas.saveLayer(0f, 0f, w, h, null)
        canvas.save()
        canvas.scale(density, density)
        paint.textSize = KaraokeLayout.SIZE.toFloat()
        val size = KaraokeLayout.SIZE
        val first = floor(s.scroll).toInt() - 4
        for (i in first..first + 12) {
            val line = layout.line(i)
            val top = focus + layout.top(i) - layout.offset(s.scroll)
            if (top > screenHeight + 20 || top + line.height < -20) continue
            val d = i - s.scroll
            val lineBlur = blur((KaraokeTimeline.blur(d) * s.blurScale).toFloat())
            val sungA = KaraokeTimeline.sungAlpha(d)
            val unsungA = KaraokeTimeline.unsungAlpha(d)
            for (pw in line.words) {
                val f = fill(i, pw.word, s)
                val lift = if (reduced || s.rest > 0 || i != s.line) 0.0 else KaraokeTimeline.lift(pw.word, s.tau)
                // The caps' middle sits on the middle of the row.
                val baseline = (top + pw.row * KaraokeLayout.ROW_HEIGHT + KaraokeLayout.ROW_HEIGHT / 2 + 0.35 * size - 2 * lift).toFloat()
                val rect = RectF((pw.x - 4).toFloat(), (baseline - size * 1.1).toFloat(), (pw.x + pw.width + 4).toFloat(), (baseline + size * 0.4).toFloat())
                canvas.save()
                // Grow 3 % about the word's centre.
                val grow = (1 + 0.03 * lift).toFloat()
                canvas.scale(grow, grow, rect.centerX(), (baseline - 0.35 * size).toFloat())

                fun text(alpha: Double, filter: BlurMaskFilter?) {
                    paint.color = android.graphics.Color.WHITE
                    paint.alpha = (255 * alpha.coerceIn(0.0, 1.0)).roundToInt()
                    paint.maskFilter = filter
                    if (pw.word.held) {
                        pw.letters.forEachIndexed { li, (ch, ox) ->
                            val wave = 2.5 * sin(2 * PI * 1.6 * s.tau - li * 0.7) * lift
                            canvas.drawText(ch.toString(), (pw.x + ox).toFloat(), (baseline - wave).toFloat(), paint)
                        }
                    } else {
                        canvas.drawText(pw.word.text, pw.x.toFloat(), baseline, paint)
                    }
                    paint.maskFilter = null
                }

                if (f < 1) text(unsungA, lineBlur)
                if (f >= 1) {
                    text(sungA, lineBlur)
                } else if (f > 0) {
                    val edge = (pw.x + f * pw.width).toFloat()
                    canvas.saveLayer(rect, null)
                    text(sungA, lineBlur)
                    maskPaint.shader = LinearGradient(
                        edge - 16, 0f, edge + 8, 0f,
                        intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                    )
                    canvas.drawRect(rect, maskPaint)
                    canvas.restore()
                    // The glowing leading edge.
                    val glowRect = RectF(rect).apply { inset(-10f, -10f) }
                    canvas.saveLayer(glowRect, null)
                    text(0.9 * sungA, blur(5f))
                    maskPaint.shader = LinearGradient(
                        edge - 28, 0f, edge + 4, 0f,
                        intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT),
                        floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP,
                    )
                    canvas.drawRect(glowRect, maskPaint)
                    canvas.restore()
                }
                canvas.restore()
            }
        }
        canvas.restore()
        // The lyrics fade out under the header and above the scrubber.
        maskPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.WHITE, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT),
            floatArrayOf(headerBottom / screenHeight, (headerBottom + 56) / screenHeight, (scrubTop - 64) / screenHeight, (scrubTop - 12) / screenHeight),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, maskPaint)
        canvas.restore()
    }
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
