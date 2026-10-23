package com.ngocdevv.nativecook.blocks.media.wavescrubslider

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrixColorFilter
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.media.wavescrubslider.WaveScrubSliderArt.Icon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

/*
 Wave Scrub Slider — a photo editor's contrast control drawn as a living wave. A port of the
 SwiftUI block (WaveScrubSlider.swift); the numbers are the same.

 Look: a dusk lake (drawn in code) fills the top 60 % of the screen; a near-black panel
 (#0E0E0E, top corners 24) slides over its foot with an orange toolbar (back, undo, redo, more,
 SHARE), a strip of seven tools with Contrast selected, and a card (#161616, corners 18) holding
 a perforated grille, the label CONTRAST, the value, and the slider. The slider is one line:
 left of the thumb an orange (#F39A1C) wave with a soft glow, right of it a grey (#4A4A4A)
 ripple. The waves tighten towards the right (2 to 15 cycles per track width) and tighten more
 as the value rises; the orange part's height grows with the value up to 15 dp (it rises from
 the line over the first 30 % of the track), the grey ripple is 1.2 + 5 × value dp; both meet
 the line at the thumb (a 14 × 42 pill). The crests always travel right at 1 cycle a second, so
 the line wiggles even when the thumb is still; at 0 only the faint grey ripple is left.
 The photo is graded live: contrast 1 → 1.45, saturation 1 → 1.3.

 autoPlay timeline (LOOP = 8 s; the scripted finger, eased in and out between keys):
   0.0 – 0.6   rest at 0: a flat line and the grey ripple.
   0.6 – 2.2   up to +62.
   2.2 – 2.9   hold (the wave keeps travelling).
   2.9 – 3.9   up to +100.
   3.9 – 5.0   hold.
   5.0 – 6.6   back to 0.
   6.6 – 8.0   rest. The drift is a whole number of cycles per loop, so 8.0 is the frame at 0.0.

 Touch: press anywhere on the card and drag; the thumb glides to the finger (0.18 s) and then
 follows it, grows 12 % and gets an orange halo; a tick haptic at each wave cycle passed.
 2 s after release the value eases back to 0 (0.8 s) and the loop starts again (on a whole
 second of the drift, so the wave never jumps).
 Reduced motion (animator duration scale 0): no loop and no drift; the screen shows +60 still,
 and dragging still works.
 */

private typealias M = WaveScrubSliderModel
private typealias A = WaveScrubSliderArt

private val ORANGE = rgb(0xF39A1C)
private val INK = rgb(0x0E0E0E)
private const val REDUCED_VALUE = 0.6
private const val CARD_H = 128f

private fun nowSeconds() = System.nanoTime() / 1e9

private class WaveScrubController(val reduced: Boolean, autoPlay: Boolean) {
    /** The drift's t = 0; the loop always starts a whole number of seconds after it. */
    val origin = nowSeconds()
    var autoStart by mutableStateOf(if (autoPlay && !reduced) origin else null)
    var manual by mutableDoubleStateOf(if (reduced) REDUCED_VALUE else 0.0)
    /** A glide of the thumb to the finger: when, and from which value. */
    var glide by mutableStateOf<DoubleArray?>(null)
    /** The ease back to 0: when, and from which value. */
    var returning by mutableStateOf<DoubleArray?>(null)
    var dragging by mutableStateOf(false)
    var lastTick by mutableIntStateOf(0)
    var frame by mutableDoubleStateOf(origin)
    var resume: Job? = null

    fun value(at: Double): Double {
        val start = autoStart
        if (start != null && at >= start && !reduced) return M.scripted((at - start) % M.LOOP)
        val r = returning
        if (r != null) return r[1] * (1 - M.easeInOut((at - r[0]) / 0.8))
        val g = glide
        if (g != null) {
            val u = (at - g[0]) / 0.18
            if (u < 1) return manual + (g[1] - manual) * (1 - u).pow(3)
        }
        return manual
    }

    fun phase(at: Double) = if (reduced) 0.0 else (at - origin) * M.DRIFT
}

private class WaveScrubLayout(val w: Float, val h: Float, bottom: Float) {
    val rowTop = h - max(bottom, 16f) - 48
    val cardTop = rowTop - 20 - CARD_H
    val cardX = 16f
    val cardW = w - 32
    val stripTop = cardTop - 20 - 52
    val row1 = stripTop - 12 - 16
    val panelTop = row1 - 30
    val photoH = panelTop + 24
    val trackX0 = cardX + 22
    val trackW = cardW - 44
    val waveY = cardTop + 82
}

@Composable
fun WaveScrubSlider(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val c = remember(reduced) { WaveScrubController(reduced, autoPlay) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) {
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            val now = nowSeconds()
            c.returning = doubleArrayOf(now, c.manual)
            c.glide = null
            // Restart on a whole second of the drift.
            c.autoStart = c.origin + ceil(now - c.origin + 0.8)
            c.manual = 0.0
        }
    }

    val thumbScale by animateFloatAsState(if (c.dragging) 1.12f else 1f, spring(dampingRatio = 0.6f, stiffness = 440f), label = "thumb")
    val halo by animateFloatAsState(if (c.dragging) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 440f), label = "halo")

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val l = remember(maxWidth, maxHeight, bottom) { WaveScrubLayout(maxWidth.value, maxHeight.value, bottom) }
        val painter = remember { WaveScrubPainter() }

        Canvas(Modifier.fillMaxSize()) {
            val now = c.frame
            val v = c.value(now)
            withTransform({ scale(density, density, pivot = Offset.Zero) }) {
                drawRect(INK, size = Size(l.w, l.h))
                painter.photo(this, l, v)
                painter.panel(this, l)
                painter.grille(this, l)
                painter.wave(this, l, v, c.phase(now))
                // The tool strip's dot under Contrast: on once the value leaves 0.
                if (v > 0.004) drawCircle(ORANGE, 2f, Offset(16 + (l.w - 32) / 7 * 2.5f, l.stripTop + 48))
                painter.thumb(this, l, v, thumbScale, halo)
            }
        }

        // SHARE, in the toolbar.
        BasicText(
            "SHARE",
            Modifier.offset(x = (l.w - 20 - 80).dp, y = (l.row1 - 9).dp).width(80.dp),
            style = TextStyle(color = ORANGE, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, textAlign = TextAlign.End),
        )
        // The card's label and value.
        val label by remember(c) { derivedStateOf { c.value(c.frame).let { M.label(it) to (it > 0.004) } } }
        Row(
            Modifier.offset(x = (l.cardX + 18).dp, y = (l.cardTop + 14).dp).width((l.cardW - 36).dp).height(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("CONTRAST", style = TextStyle(color = rgb(0x8C8C8C), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp))
            Spacer(Modifier.weight(1f))
            BasicText(
                label.first,
                style = TextStyle(
                    color = if (label.second) ORANGE else Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace,
                ),
            )
        }

        // The card takes the finger.
        Box(
            Modifier
                .offset(x = l.cardX.dp, y = l.cardTop.dp)
                .size(l.cardW.dp, CARD_H.dp)
                .semantics {
                    contentDescription = "Contrast"
                    stateDescription = M.label(c.manual)
                }
                .pointerInput(c, l) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun target(x: Float) = A.clamp01(((x / density) - 22.0) / l.trackW)
                        c.resume?.cancel()
                        val now = nowSeconds()
                        val current = c.value(now)
                        c.autoStart = null
                        c.returning = null
                        c.dragging = true
                        c.lastTick = A.tick(current)
                        val first = target(down.position.x)
                        c.glide = if (abs(first - current) * l.trackW > 12) doubleArrayOf(now, current) else null
                        fun follow(x: Float) {
                            val t = target(x)
                            c.manual = t
                            val tick = A.tick(t)
                            if (tick != c.lastTick) {
                                c.lastTick = tick
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        follow(down.position.x)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            follow(change.position.x)
                            change.consume()
                        }
                        c.dragging = false
                        scheduleResume()
                    }
                },
        )
    }
}

/** Draws the screen in dp; the native canvas does the colour grade and the blurs. */
private class WaveScrubPainter {
    private val gradePaint = android.graphics.Paint()
    private val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = android.graphics.Paint.Cap.ROUND
        color = ORANGE.copy(alpha = 0.55f).toArgbInt()
        // SwiftUI's blur radius 6 as a Gaussian sigma; Android's radius ≈ (sigma − 0.5) / 0.577.
        maskFilter = BlurMaskFilter((6f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
    }
    private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.Black.copy(alpha = 0.55f).toArgbInt()
        maskFilter = BlurMaskFilter((6f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
    }
    private var grilleFor = -1f
    private var dots: List<Pair<Offset, Float>> = emptyList()

    fun photo(d: DrawScope, l: WaveScrubLayout, v: Double) = with(d) {
        val canvas = drawContext.canvas.nativeCanvas
        val m = M.grade(v)
        for (i in intArrayOf(4, 9, 14)) m[i] *= 255f
        gradePaint.colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(m))
        canvas.saveLayer(0f, 0f, l.w, l.photoH, gradePaint)
        A.photo(this, l.w.toDouble(), l.photoH.toDouble())
        canvas.restore()
        // The status bar reads on a light shade at the top.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0f)), 0f, 120f), size = Size(l.w, 120f))
    }

    /** The panel's still parts: its sheet, the toolbar, the tool strip, the card and the bottom bar. */
    fun panel(d: DrawScope, l: WaveScrubLayout) = with(d) {
        drawRoundRect(INK, Offset(0f, l.panelTop), Size(l.w, l.h - l.panelTop + 40), CornerRadius(24f))
        // Grabber.
        drawRoundRect(Color.White.copy(alpha = 0.14f), Offset(l.w / 2 - 18, l.panelTop + 8), Size(36f, 4f), CornerRadius(2f))
        // Toolbar.
        A.draw(this, Icon.Back, 28f, l.row1, ORANGE)
        A.draw(this, Icon.Undo, 70f, l.row1, ORANGE)
        A.draw(this, Icon.Redo, 108f, l.row1, ORANGE.copy(alpha = 0.35f))
        A.draw(this, Icon.More, l.w - 104, l.row1, ORANGE)
        // Tool strip.
        val tools = arrayOf(Icon.Auto, Icon.Light, Icon.Contrast, Icon.Color, Icon.Vignette, Icon.Grain, Icon.Sharpen)
        val cell = (l.w - 32) / tools.size
        tools.forEachIndexed { i, t ->
            val cx = 16 + cell * (i + 0.5f)
            val cy = l.stripTop + 22
            if (i == 2) drawRoundRect(rgb(0x242424), Offset(cx - 22, cy - 22), Size(44f, 44f), CornerRadius(12f))
            A.draw(this, t, cx, cy, if (i == 2) Color.White else Color.White.copy(alpha = 0.38f), 22f)
            if (i == 1) drawCircle(ORANGE, 2f, Offset(cx, l.stripTop + 48))
        }
        // Card.
        drawRoundRect(rgb(0x161616), Offset(l.cardX, l.cardTop), Size(l.cardW, CARD_H), CornerRadius(18f))
        // Bottom bar.
        val bar = arrayOf(Icon.Crop, Icon.Brush, Icon.Dial, Icon.Layers, Icon.Sky)
        val bc = (l.w - 32) / bar.size
        bar.forEachIndexed { i, t ->
            A.draw(this, t, 16 + bc * (i + 0.5f), l.rowTop + 22, if (i == 2) Color.White else Color.White.copy(alpha = 0.45f))
        }
        drawCircle(Color.White, 2f, Offset(16 + bc * 2.5f, l.rowTop + 44))
    }

    /** The card's perforated grille: dots every 8 dp, fading out at the card's edges. */
    fun grille(d: DrawScope, l: WaveScrubLayout) = with(d) {
        val gw = l.cardW
        val gh = CARD_H - 34
        if (grilleFor != gw) {
            grilleFor = gw
            val list = mutableListOf<Pair<Offset, Float>>()
            var y = 6f
            while (y < gh) {
                var x = 6f + (if ((y / 8).toInt() % 2 == 0) 0f else 4f)
                while (x < gw) {
                    val edge = minOf(x, gw - x, y, gh - y)
                    list.add(Offset(x, y) to (0.1 * M.smoothstep(0.0, 28.0, edge.toDouble())).toFloat())
                    x += 8
                }
                y += 8
            }
            dots = list
        }
        val ox = l.cardX
        val oy = l.cardTop + 34
        for ((p, a) in dots) drawCircle(Color.White.copy(alpha = a), 1.1f, Offset(ox + p.x, oy + p.y))
    }

    private val active = Path()
    private val rest = Path()
    private val restStroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val activeStroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun wave(d: DrawScope, l: WaveScrubLayout, v: Double, phase: Double) = with(d) {
        val tw = l.trackW.toDouble()
        val thumb = v * tw
        fun y(x: Double) = (l.waveY - M.height(x / tw, v, phase, tw)).toFloat()
        active.reset()
        rest.reset()
        val step = 1.5
        var x = 0.0
        active.moveTo(l.trackX0, y(0.0))
        while (x < thumb) {
            x = minOf(x + step, thumb)
            active.lineTo(l.trackX0 + x.toFloat(), y(x))
        }
        rest.moveTo(l.trackX0 + thumb.toFloat(), y(thumb))
        while (x < tw) {
            x = minOf(x + step, tw)
            rest.lineTo(l.trackX0 + x.toFloat(), y(x))
        }
        drawPath(rest, rgb(0x4A4A4A), style = restStroke)
        if (thumb > 0.5) {
            drawContext.canvas.nativeCanvas.drawPath(active.asAndroidPath(), glowPaint)
            drawPath(active, ORANGE, style = activeStroke)
        }
    }

    fun thumb(d: DrawScope, l: WaveScrubLayout, v: Double, scale: Float, halo: Float) = with(d) {
        val cx = l.trackX0 + (v * l.trackW).toFloat()
        val cy = l.waveY
        withTransform({ scale(scale, scale, pivot = Offset(cx, cy)) }) {
            if (halo > 0.01f) {
                drawCircle(
                    Brush.radialGradient(listOf(ORANGE.copy(alpha = 0.35f), ORANGE.copy(alpha = 0f)), center = Offset(cx, cy), radius = 34f),
                    34f, Offset(cx, cy), alpha = halo.coerceIn(0f, 1f),
                )
            }
            drawContext.canvas.nativeCanvas.drawRoundRect(cx - 7, cy - 21 + 2, cx + 7, cy + 21 + 2, 7f, 7f, shadowPaint)
            drawRoundRect(rgb(0x1E1E1E), Offset(cx - 7, cy - 21), Size(14f, 42f), CornerRadius(7f))
            drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(cx - 6.5f, cy - 20.5f), Size(13f, 41f), CornerRadius(6.5f), style = Stroke(1f))
            drawRoundRect(ORANGE, Offset(cx - 1.5f, cy - 12), Size(3f, 24f), CornerRadius(1.5f))
        }
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(alpha, red, green, blue)

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
