package com.ngocdevv.nativecook.blocks.media.recordstackdeck

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Record Stack Deck — a crate of ten records shown as a vertical 3D deck; hold one and it rises,
 flattens, and its vinyl slides half out of the sleeve, spinning. A port of the SwiftUI block
 (RecordStackDeck.swift); the numbers are the same.

 Look: black screen lit from above and below by the colour of the record at the focus. The
 sleeves (squares 0.78 × the screen width, covers drawn in code, invented albums) lie stacked at
 42 % of the height: the one at the focus is seen edge-on as a thin strip, the others open up
 with distance (the cosine of the tilt is 0.02 + 0.08 a − 0.0065 a², a = |offset|) and sit
 26 a + 11 a² dp from the focus, each titled in small type above its strip. Each sleeve is drawn
 through a perspective homography (camera 1400 dp away, as Core Animation's m34 = −1/1400 in the
 Swift version). A white ring marks the focus. Under the deck a large caption over a fade.

 autoPlay timeline (LOOP = 10 s, RecordStackDeckMotion.kt):
   0.0 – 0.4    rest on the first record.
   0.4 – 1.1    one record down (ease-out-back), 1.1 – 1.9 two more (ease-out).
   1.9 – 2.8    the ring fills as if held: sixteen ticks light up around it.
   2.8 – 3.6    the record rises out of the deck and flattens (ease-out-back) while the others
                dim and spread; 3.6 – 4.2 its vinyl slides half out to the right (the sleeve
                moves left), spinning at 200 °/s with the cover as its label; the caption
                changes to the album's title.
   6.6 – 8.0    the vinyl slides back in and the sleeve returns to the deck.
   8.0 – 9.7    a long flick through seven records (ease-out-quart) to the first one again:
                the crate has turned once, so 10.0 is the same frame as 0.0.

 Touch: drag up or down to turn the deck (44 dp per record, a tick per record); on release it
 is thrown by its speed and settles on a record (critically damped spring, ω 12). Tap a strip to
 bring it to the focus. Hold the focus (0.8 s, the ring fills) or tap it to open the record; tap
 anywhere to put it back. 2 s after the last touch the record goes back, the deck settles on the
 first record and the loop starts again.
 Reduced motion: no loop and no spin; opening and closing cross-fade in 0.25 s; the deck still
 follows a finger and settles without being thrown.
 */

private fun nowSeconds() = System.nanoTime() / 1e9

private class DeckPose(
    val scroll: Double,
    val ring: Double,
    /** 0 in the deck, 1 lifted flat, 2 vinyl half out. */
    val open: Double,
    /** The vinyl's angle in degrees. */
    val spin: Double,
    /** Reduced motion only: the open scene's cross-fade (0 deck, 1 open). */
    val fade: Double?,
)

private class DeckController(val reduced: Boolean) {
    var autoStart by mutableStateOf<Double?>(null)
    /** The settling deck: since when, from, with what speed, to. */
    var settle by mutableStateOf(doubleArrayOf(0.0, 0.0, 0.0, 0.0))
    var dragScroll by mutableStateOf<Double?>(null)
    /** The ring being held: when it started and how long it takes to fill. */
    var hold by mutableStateOf<DoubleArray?>(null)
    /** Opening (true) or closing (false) since a time. */
    var openMove by mutableStateOf<Pair<Double, Boolean>?>(null)
    var touching = false
    var busy by mutableStateOf(false)
    var frame by mutableDoubleStateOf(nowSeconds())
    var resume: Job? = null
    var holdJob: Job? = null

    fun openValue(at: Double): Double {
        val m = openMove ?: return 0.0
        val e = max(0.0, at - m.first)
        if (e >= DeckMotion.OPEN_LENGTH) return if (m.second) 2.0 else 0.0
        return if (m.second) DeckMotion.opening(e) else DeckMotion.closing(e)
    }

    fun pose(at: Double): DeckPose {
        val start = autoStart
        if (start != null && at >= start) {
            val t = (at - start) % DeckMotion.LOOP
            return DeckPose(DeckMotion.loopScroll(t), DeckMotion.loopRing(t), DeckMotion.loopOpen(t), 200 * t, null)
        }
        val s = settle
        val scroll = dragScroll ?: DeckMotion.settle(s[1], s[2], s[3], max(0.0, at - s[0]))
        val h = hold
        val ring = if (h != null) DeckMotion.clamp01((at - h[0]) / h[1]) else 0.0
        var open = openValue(at)
        var fade: Double? = null
        if (reduced) {
            val m = openMove
            fade = if (m != null) {
                val f = DeckMotion.clamp01((at - m.first) / 0.25)
                if (m.second) f else 1 - f
            } else 0.0
            open = 2.0
        }
        return DeckPose(scroll, ring, open, if (reduced) 0.0 else 200 * (at % 1800), fade)
    }

    /** Freezes whatever plays into the hand-driven state. */
    fun takeOver(now: Double) {
        resume?.cancel()
        val start = autoStart ?: return
        autoStart = null
        if (now < start) return
        val t = (now - start) % DeckMotion.LOOP
        val s = DeckMotion.loopScroll(t)
        settle = doubleArrayOf(now, s, 0.0, Math.round(s).toDouble())
        val o = DeckMotion.loopOpen(t)
        openMove = if (o > 0) {
            // Carry on the open record; a closing one finishes closing.
            val closingNow = t >= 6.6
            val e = if (closingNow) t - 6.6 else min(t - 2.8, DeckMotion.OPEN_LENGTH)
            Pair(now - e, !closingNow)
        } else null
        hold = null
    }
}

@Composable
fun RecordStackDeck(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val c = remember(reduced) { DeckController(reduced) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) { if (autoPlay && !reduced) c.autoStart = nowSeconds() }
    LaunchedEffect(c, c.busy) {
        c.frame = nowSeconds()
        if (!reduced || c.busy) while (true) withFrameNanos { c.frame = nowSeconds() }
    }

    fun markIdle(seconds: Double) {
        scope.launch {
            delay((seconds * 1000).toLong())
            if (!c.touching) c.busy = false
        }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) {
            c.busy = c.touching
            return
        }
        c.resume = scope.launch {
            delay(2000)
            if (c.touching) return@launch
            var now = nowSeconds()
            val m = c.openMove
            if (m != null && m.second) {
                // Still opening: let it finish first.
                if (now - m.first < DeckMotion.OPEN_LENGTH) now = m.first + DeckMotion.OPEN_LENGTH
                c.openMove = Pair(now, false)
                now += DeckMotion.OPEN_LENGTH
            }
            val s = c.pose(now).scroll
            val n = DeckMotion.COUNT.toDouble()
            c.settle = doubleArrayOf(now, s, 0.0, Math.round(s / n) * n)
            c.autoStart = now + 0.8
        }
    }

    fun openRecord(now: Double) {
        c.hold = null
        c.openMove = Pair(now, true)
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (reduced) {
            c.busy = true
            markIdle(0.4)
        }
        scheduleResume()
    }

    fun closeRecord(now: Double) {
        c.openMove = Pair(now, false)
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (reduced) {
            c.busy = true
            markIdle(0.4)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val d = density.density
        val w = maxWidth.value
        val h = maxHeight.value
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val s = 0.78f * w
        val cy = h * DeckMotion.FOCUS_Y.toFloat()
        val captionTop = h - max(bottom, 16f) - 40 - 92

        // The covers, drawn once into bitmaps; the deck draws them through a perspective matrix.
        val covers = remember(s, d) {
            val px = (s * d).roundToInt().coerceAtLeast(1)
            List(DeckMotion.COUNT) { i ->
                ImageBitmap(px, px).also { bmp ->
                    CanvasDrawScope().draw(Density(d), layoutDirection, androidx.compose.ui.graphics.Canvas(bmp), Size(px.toFloat(), px.toFloat())) {
                        scale(d, d, Offset.Zero) { deckCover(i, s) }
                    }
                }
            }
        }
        val painter = remember { DeckPainter() }
        val pose by remember(c) { derivedStateOf { c.pose(c.frame) } }
        val focus by remember(c) { derivedStateOf { ((Math.round(pose.scroll) % DeckMotion.COUNT).toInt() + DeckMotion.COUNT) % DeckMotion.COUNT } }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(c, w, h) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var now = nowSeconds()
                        c.touching = true
                        c.takeOver(now)
                        c.busy = true
                        val dragFrom = c.pose(now).scroll
                        var lastTick = floor(dragFrom + 0.5).toInt()
                        var moved = false
                        val m0 = c.openMove
                        // Holding the focus fills the ring; when it is full the record opens.
                        if ((m0 == null || (!m0.second && c.openValue(now) == 0.0)) && abs(down.position.y / d - cy) < 34) {
                            c.openMove = null
                            c.hold = doubleArrayOf(now, 0.8)
                            c.holdJob?.cancel()
                            c.holdJob = scope.launch {
                                delay(800)
                                if (c.touching && c.hold != null) openRecord(nowSeconds())
                            }
                        }
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            last = change.position
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (c.openMove?.second == true) continue
                            val dy = (change.position.y - down.position.y) / d
                            if (!moved && abs(dy) > 8) {
                                moved = true
                                c.hold = null
                                c.holdJob?.cancel()
                            }
                            if (moved) {
                                change.consume()
                                val sc = dragFrom - dy / DeckMotion.DRAG_UNIT
                                c.dragScroll = sc
                                val k = floor(sc + 0.5).toInt()
                                if (k != lastTick) {
                                    lastTick = k
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                        now = nowSeconds()
                        c.touching = false
                        c.holdJob?.cancel()
                        val open = c.openMove
                        val hd = c.hold
                        if (moved) {
                            val sc = c.dragScroll ?: dragFrom
                            val v = if (reduced) 0.0 else -tracker.calculateVelocity().y / d / DeckMotion.DRAG_UNIT
                            c.settle = doubleArrayOf(now, sc, v, Math.round(sc + v * 0.25).toDouble())
                            c.dragScroll = null
                        } else if (open != null && open.second) {
                            // A tap on an open record puts it back.
                            if (now - open.first >= DeckMotion.OPEN_LENGTH) closeRecord(now)
                        } else if (hd != null) {
                            // A tap on the focus: the ring finishes quickly, then the record opens.
                            val progress = DeckMotion.clamp01((now - hd[0]) / hd[1])
                            c.hold = doubleArrayOf(now - progress * 0.3, 0.3)
                            c.holdJob = scope.launch {
                                delay((max(0.0, (1 - progress) * 0.3) * 1000).toLong())
                                openRecord(nowSeconds())
                            }
                        } else {
                            // A tap on another strip brings it to the focus.
                            val sc = c.pose(now).scroll
                            var best = 0.0
                            var bestDistance = Double.MAX_VALUE
                            for (i in 0 until DeckMotion.COUNT) {
                                val off = DeckMotion.offset(i, sc)
                                val dist = abs(cy + DeckMotion.y(off) - last.y / d)
                                if (dist < bestDistance && DeckMotion.fade(off) > 0.3) {
                                    bestDistance = dist
                                    best = off
                                }
                            }
                            c.settle = doubleArrayOf(now, sc, 0.0, Math.round(sc + best).toDouble())
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        scheduleResume()
                        if (reduced) markIdle(0.5)
                    }
                },
        ) {
            painter.draw(this, pose, covers, focus, w, h, s, cy, top)
        }

        // A fade under the caption.
        Box(
            Modifier
                .offset(y = (captionTop - 70).dp)
                .width(w.dp)
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color(0x000A0A0C), 0.55f to Color(0xEB0A0A0C), 1f to Color(0xEB0A0A0C))),
        )

        // Captions.
        val album = deckAlbums[focus]
        Box(Modifier.offset(x = 24.dp, y = captionTop.dp).width((w - 48).dp)) {
            val bigStyle = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5 / 30).em, lineHeight = 36.sp)
            val subStyle = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
            Column(
                Modifier.graphicsLayer {
                    val p = pose
                    val a = p.fade?.let { 1 - it } ?: (1 - DeckMotion.smoothstep(0.2, 0.8, p.open))
                    alpha = a.toFloat()
                    translationY = (8 * (1 - a) * d).toFloat()
                },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText("Every album,\npressed to vinyl.", style = bigStyle)
                BasicText("Hold a record to play it.", style = subStyle)
            }
            Column(
                Modifier
                    .graphicsLayer {
                        val p = pose
                        val a = p.fade ?: DeckMotion.smoothstep(1.2, 1.8, p.open)
                        alpha = a.toFloat()
                        translationY = (8 * (1 - a) * d).toFloat()
                    }
                    .semantics { contentDescription = "Now spinning ${album.title} by ${album.artist}" },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText("Now spinning\n${album.title}", style = bigStyle, maxLines = 2)
                BasicText("${album.artist} · Side A", style = subStyle)
            }
        }

        // Header.
        Row(Modifier.offset(x = 24.dp, y = (top + 10).dp).width((w - 48).dp)) {
            BasicText("Crate", style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.weight(1f))
            BasicText(
                "10 RECORDS",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace, letterSpacing = (1.0 / 11).em,
                ),
                modifier = Modifier.offset(y = 3.dp),
            )
        }
    }
}

/** Draws the glow, the deck, the open record, the strip titles and the ring, in dp. */
private class DeckPainter {
    private val matrix = Matrix()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(24f), BlurMaskFilter.Blur.NORMAL)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
    private val src = FloatArray(8)
    private val dst = FloatArray(8)

    private fun glowColor(scroll: Double): Color {
        val n = DeckMotion.COUNT.toDouble()
        val sc = ((scroll % n) + n) % n
        val a = floor(sc).toInt() % DeckMotion.COUNT
        val b = (a + 1) % DeckMotion.COUNT
        return lerp(deckAlbums[a].glow, deckAlbums[b].glow, (sc - floor(sc)).toFloat())
    }

    fun draw(
        scope: androidx.compose.ui.graphics.drawscope.DrawScope,
        p: DeckPose,
        covers: List<ImageBitmap>,
        focus: Int,
        w: Float,
        h: Float,
        s: Float,
        cy: Float,
        top: Float,
    ) = with(scope) {
        val dens = density
        val deckAlpha = p.fade?.let { 1 - it } ?: 1.0
        val lifted = if (p.fade == null) min(p.open, 1.0) else 0.0
        val out = if (p.fade == null) DeckMotion.easeInOut(max(0.0, p.open - 1)) else 1.0
        scale(dens, dens, Offset.Zero) {
            // The focused record's colour, lighting the screen.
            val glow = glowColor(p.scroll)
            val boost = (1 + 0.5 * min(p.open, 1.0) * (p.fade ?: 1.0)).toFloat()
            drawRect(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.5f * boost), glow.copy(alpha = 0.12f * boost), glow.copy(alpha = 0f)),
                    center = Offset(w / 2, cy - h * 0.34f), radius = w * 0.95f,
                ),
                size = Size(w, h),
            )
            drawRect(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.3f * boost), glow.copy(alpha = 0f)), center = Offset(w / 2, h + 40), radius = w * 0.9f),
                size = Size(w, h),
            )
        }

        val canvas = drawContext.canvas.nativeCanvas
        fun sleeve(i: Int, x: Float, y: Float, k: Float, tilt: Double, alpha: Double, shadow: Double) {
            if (alpha <= 0.002) return
            val bmp = covers[i]
            val px = bmp.width.toFloat()
            src[0] = 0f; src[1] = 0f; src[2] = px; src[3] = 0f; src[4] = px; src[5] = px; src[6] = 0f; src[7] = px
            val half = (s * k / 2).toDouble()
            val corners = arrayOf(doubleArrayOf(-half, -half), doubleArrayOf(half, -half), doubleArrayOf(half, half), doubleArrayOf(-half, half))
            for (j in 0 until 4) {
                val q = DeckMotion.project(corners[j][0], corners[j][1], tilt)
                dst[j * 2] = ((x + q.first) * dens).toFloat()
                dst[j * 2 + 1] = ((y + q.second) * dens).toFloat()
            }
            if (shadow > 0.001 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val path = android.graphics.Path()
                path.moveTo(dst[0], dst[1] + 14 * dens)
                for (j in 1 until 4) path.lineTo(dst[j * 2], dst[j * 2 + 1] + 14 * dens)
                path.close()
                shadowPaint.alpha = (255 * 0.55 * shadow).roundToInt().coerceIn(0, 255)
                canvas.drawPath(path, shadowPaint)
            }
            matrix.setPolyToPoly(src, 0, dst, 0, 4)
            canvas.save()
            canvas.concat(matrix)
            bitmapPaint.alpha = (255 * alpha).roundToInt().coerceIn(0, 255)
            canvas.drawBitmap(bmp.asAndroidBitmap(), 0f, 0f, bitmapPaint)
            canvas.restore()
        }

        // The deck, farthest first; the picked sleeve goes on top of the vinyl.
        val order = (0 until DeckMotion.COUNT).sortedByDescending { abs(DeckMotion.offset(it, p.scroll)) }
        var picked: (() -> Unit)? = null
        for (i in order) {
            val off = DeckMotion.offset(i, p.scroll)
            val isPicked = i == focus && p.open > 0 && p.fade == null
            var tilt = DeckMotion.tilt(off)
            var y = DeckMotion.y(off)
            var k = 1.0
            var x = w / 2.0
            var alpha = DeckMotion.fade(off) * DeckMotion.topFade(cy + DeckMotion.y(off), top.toDouble()) * deckAlpha
            if (isPicked) {
                tilt *= 1 - lifted
                y *= 1 - lifted
                k = 1 - (1 - DeckMotion.FLAT_SCALE) * lifted
                x += DeckMotion.SLEEVE_SHIFT * s * DeckMotion.FLAT_SCALE * out
                val a = alpha
                val tt = tilt
                val yy = y
                val kk = k
                val xx = x
                picked = { sleeve(i, xx.toFloat(), (cy + yy).toFloat(), kk.toFloat(), tt, a, lifted) }
                continue
            } else if (p.fade == null) {
                val m = min(p.open, 1.0)
                y *= 1 + 0.3 * m
                alpha *= 1 - 0.85 * m
            }
            sleeve(i, x.toFloat(), (cy + y).toFloat(), k.toFloat(), tilt, alpha, 0.0)
        }

        // The vinyl behind the lifted sleeve.
        if (p.open > 1 || p.fade != null) {
            val f = s * DeckMotion.FLAT_SCALE.toFloat()
            scale(dens, dens, Offset.Zero) {
                val layerAlpha = (p.fade ?: 1.0).toFloat()
                if (layerAlpha > 0.001) {
                    drawContext.canvas.nativeCanvas.saveLayerAlpha(0f, 0f, w, h, (255 * layerAlpha).roundToInt())
                    deckVinyl(focus, Offset(w / 2 + DeckMotion.VINYL_SHIFT.toFloat() * f * out.toFloat(), cy), DeckMotion.VINYL_RADIUS.toFloat() * f, p.spin.toFloat())
                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }
        picked?.invoke()
        // Reduced motion: the open sleeve cross-fades in over the deck.
        val fade = p.fade
        if (fade != null && fade > 0) {
            sleeve(focus, (w / 2 + DeckMotion.SLEEVE_SHIFT * s * DeckMotion.FLAT_SCALE).toFloat(), cy, DeckMotion.FLAT_SCALE.toFloat(), 0.0, fade, 0.0)
        }

        scale(dens, dens, Offset.Zero) {
            // Each strip's title above it.
            val stripAlpha = deckAlpha * (1 - DeckMotion.smoothstep(0.0, 0.4, if (p.fade == null) p.open else 0.0))
            if (stripAlpha > 0.01) {
                val nc = drawContext.canvas.nativeCanvas
                for (i in 0 until DeckMotion.COUNT) {
                    val off = DeckMotion.offset(i, p.scroll)
                    val a = stripAlpha * DeckMotion.fade(off) * DeckMotion.topFade(cy + DeckMotion.y(off), top.toDouble()) *
                        (1 - DeckMotion.smoothstep(2.4, 3.4, abs(off)))
                    if (a <= 0.01) continue
                    val edge = (cy + DeckMotion.y(off) + DeckMotion.topEdge(s.toDouble(), DeckMotion.tilt(off))).toFloat()
                    val x0 = (w - s) / 2 + 2
                    val baseline = edge - 4 - titlePaint.fontMetrics.descent
                    titlePaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    titlePaint.color = Color.White.copy(alpha = a.toFloat()).toArgb()
                    val title = deckAlbums[i].title
                    nc.drawText(title, x0, baseline, titlePaint)
                    val tw = titlePaint.measureText(title)
                    titlePaint.typeface = android.graphics.Typeface.DEFAULT
                    titlePaint.color = Color.White.copy(alpha = (0.6 * a).toFloat()).toArgb()
                    nc.drawText("  " + deckAlbums[i].artist, x0 + tw, baseline, titlePaint)
                }
            }

            // The focus ring, and the ticks of a hold.
            val ringAlpha = (deckAlpha * (1 - DeckMotion.smoothstep(0.0, 0.4, if (p.fade == null) p.open else 0.0))).toFloat()
            if (ringAlpha > 0.01) {
                val ctr = Offset(w / 2, cy)
                val r = 12f + if (p.ring >= 1) 2f else 0f
                drawCircle(Color.White.copy(alpha = 0.9f * ringAlpha), r, ctr, style = Stroke(1.5f))
                if (p.ring > 0) {
                    drawArc(
                        Color.White.copy(alpha = ringAlpha), -90f, (360 * p.ring).toFloat(), false,
                        topLeft = Offset(ctr.x - r, ctr.y - r), size = Size(2 * r, 2 * r), style = Stroke(3f, cap = StrokeCap.Round),
                    )
                    for (k in 0 until 16) {
                        val on = DeckMotion.smoothstep(k / 16.0, (k + 1) / 16.0, p.ring)
                        if (on <= 0) continue
                        val a = -PI / 2 + k / 16.0 * 2 * PI
                        drawLine(
                            Color.White.copy(alpha = (0.9 * on).toFloat() * ringAlpha),
                            Offset(ctr.x + (cos(a) * (r + 6)).toFloat(), ctr.y + (sin(a) * (r + 6)).toFloat()),
                            Offset(ctr.x + (cos(a) * (r + 6 + 6 * on)).toFloat(), ctr.y + (sin(a) * (r + 6 + 6 * on)).toFloat()),
                            strokeWidth = 1.5f, cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}
