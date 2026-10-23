package com.ngocdevv.nativecook.blocks.text.kineticstatsstory

import android.content.res.AssetManager
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Kinetic Stats Story: a year-in-review story for a running app. Three cards on a drifting mesh
 gradient; the stats are set far bigger than the screen and travel through it.

 Layout (dp, W × H the whole screen, `top`/`bottom` the safe area): story bars at top + 8 (3 dp,
 12 dp in from the sides, 4 dp apart), a header row centred at top + 32 (label left, close button
 right), round buttons (36 dp) in the bottom corners, 16 dp from the sides and 12 dp above the safe
 area. All type is Inter Tight Bold.

 Timeline of the story (LOOP = 10 s, story time s):
   0.00–1.55  Cover at rest: kicker "Your year on the road" and "Rewind" / "’26" (sized to fill
              W − 48) at the bottom left, on the deep teal mesh. The first bar fills.
   1.55–2.00  The cover's three lines leave to the left (48 dp, ease-in cubic, 0.35 s each, 0.05 s
              apart) while they blur (sigma 0 → 10) and fade; the mesh turns orange (1.5–2.4).
   1.85–2.41  "This year you ran" / "1,284 kilometres." (30 sp) enter from the right (48 dp,
              ease-out cubic, 0.5 s, 0.06 s apart), blur 10 → 0.
   2.15–5.20  The number "1,284" (0.7 W, so about twice the screen wide) comes in from the right
              edge (W − 8 dp, 0.75 s, ease-out cubic) and from 2.75 slides out to the left, slowly
              at first (its width + 0.3 W + 24 dp, 2.45 s, ease-in cubic). Its digits roll like an
              odometer from 2.2 to 3.4 (ease-out cubic), each slot turning ten more times than the
              one left of it; a digit rises out (fading) as the next rises in from 1.25 cap heights
              below, inside a band clipped to the digits.
   4.95–5.40  The caption leaves to the left like the cover; the mesh shifts (4.9–5.8).
   5.35–8.85  "And 212" / "sunrise" / "runs." (0.4 W, lines 0.94 em apart) come in from the right
              (0.95 W, ease-out cubic, 0.85 s, 0.1 s apart) and drift left, each at its own speed,
              so that by 8.85 its end sits at 0.62 W.
   8.80–9.30  The lines leave to the left (0.7 W, ease-in cubic, 0.4 s, 0.05 s apart); the bars
              drain (8.85–9.45, ease-in-out) and the mesh returns to teal (8.8–9.9).
   9.20–9.84  The cover's lines come back in from the right; from 9.84 the frame is the one at 0.
 The mesh's five blobs move on paths with a period of 10 s (or 5 s), so the loop has no seam.

 Touch: a tap on the left third goes to the previous card, elsewhere to the next card (haptic);
 holding a finger down pauses the story, and it plays on from there on release. Without `autoPlay`
 the story waits on the cover and plays through once from a tap. Reduced motion: the story holds
 each card still (the number and the lines scaled to fit the width), taps cross-fade between cards
 in 0.3 s, and nothing plays by itself.
*/

/** One pass of the story, in seconds. */
const val LOOP = 10f

private object M {
    const val MARGIN = 24f
    const val SHIFT = 48f
    const val BLUR = 10f
    val CARDS = floatArrayOf(0f, 1.55f, 4.95f)
    val BARS = arrayOf(0f to 1.55f, 1.55f to 4.95f, 4.95f to 8.85f)
    const val DRAIN_START = 8.85f
    const val DRAIN = 0.6f
    const val TAP_TIME = 0.3f
    const val TAP_SLOP = 10f
    const val FADE = 0.3f
}

private class Blob(
    val x: Float, val y: Float, val ax: Float, val ay: Float,
    val fx: Float, val fy: Float, val px: Float, val py: Float, val r: Float,
)

/** Base colour then the five blobs, for the cover, the first card and the second card. */
private val PALETTES = arrayOf(
    longArrayOf(0x0d2433, 0x1f8f7d, 0x2c5da8, 0x5fd39a, 0x081520, 0xb9772c),
    longArrayOf(0xff6a00, 0x25d98c, 0xffb300, 0xff4400, 0xff8a00, 0xffd34d),
    longArrayOf(0xff7400, 0xffc02e, 0xff5000, 0x38e0a0, 0xffa000, 0xffe58a),
)

/** Centre (fractions of the screen), swing, frequency (turns per loop), phase and radius (fraction of the longer side). */
private val BLOBS = arrayOf(
    Blob(0.15f, 0.22f, 0.25f, 0.08f, 1f, 2f, 0.0f, 1.1f, 0.42f),
    Blob(0.85f, 0.35f, 0.20f, 0.12f, 1f, 1f, 2.1f, 0.4f, 0.48f),
    Blob(0.35f, 0.75f, 0.30f, 0.10f, 2f, 1f, 4.0f, 2.6f, 0.5f),
    Blob(0.90f, 0.90f, 0.15f, 0.08f, 1f, 2f, 1.3f, 3.7f, 0.4f),
    Blob(0.45f, 0.05f, 0.30f, 0.06f, 1f, 1f, 5.2f, 0.9f, 0.35f),
)

private fun clamp(x: Float) = x.coerceIn(0f, 1f)
private fun easeOutCubic(t: Float) = 1f - (1f - t).pow(3)
private fun easeInCubic(t: Float) = t * t * t
private fun easeInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = clamp((x - a) / (b - a))
    return t * t * (3f - 2f * t)
}

private fun seconds(from: Long, to: Long) = (to - from) / 1e9f

/** An element's offset, opacity and blur (sigma, dp) during a transition. */
private data class Look(val dx: Float = 0f, val alpha: Float = 1f, val blur: Float = 0f) {
    fun then(other: Look) = Look(dx + other.dx, alpha * other.alpha, blur + other.blur)

    companion object {
        /** Comes in from the right, sharpening. */
        fun enter(s: Float, start: Float, duration: Float): Look {
            val e = easeOutCubic(clamp((s - start) / duration))
            return Look(M.SHIFT * (1f - e), e, M.BLUR * (1f - e))
        }

        /** Leaves to the left, blurring. */
        fun leave(s: Float, start: Float, duration: Float): Look {
            val q = clamp((s - start) / duration)
            val e = easeInCubic(q)
            return Look(-M.SHIFT * e, 1f - q, M.BLUR * e)
        }
    }
}

/** How much of each card's palette the mesh shows. */
private fun meshWeights(s: Float): FloatArray {
    val c1 = smoothstep(1.5f, 2.4f, s) * (1f - smoothstep(4.9f, 5.8f, s))
    val c2 = smoothstep(4.9f, 5.8f, s) * (1f - smoothstep(8.8f, 9.9f, s))
    return floatArrayOf(1f - c1 - c2, c1, c2)
}

/**
 * A year-in-review story: three cards on a drifting mesh gradient, with stats set far bigger than
 * the screen that roll and travel through it. Tap to step, hold to pause.
 */
@Composable
fun KineticStatsStory(
    modifier: Modifier = Modifier,
    label: String = "Rewind ’26",
    kicker: String = "Your year on the road",
    title: List<String> = listOf("Rewind", "’26"),
    caption: List<String> = listOf("This year you ran", "1,284 kilometres."),
    /** The number of the first card; it rolls up to this value. */
    number: Int = 1284,
    lines: List<String> = listOf("And 212", "sunrise", "runs."),
    /** Plays the story by itself, over and over; one pass lasts [LOOP] seconds. */
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val type = remember(context) { KineticStatsStoryType(context.assets) }

    var now by remember { mutableLongStateOf(System.nanoTime()) }
    // The moment story time 0 fell on; story time frozen while a finger is down.
    var start by remember { mutableLongStateOf(System.nanoTime()) }
    var held by remember { mutableStateOf<Float?>(null) }
    var playing by remember { mutableStateOf(autoPlay) }
    // Reduced motion: the card shown, the one before it and when it changed.
    var still by remember { mutableStateOf(0) }
    var stillBefore by remember { mutableStateOf(0) }
    var stillChange by remember { mutableLongStateOf(0L) }
    var fading by remember { mutableStateOf(false) }

    fun storyTime(at: Long): Float {
        held?.let { return it }
        if (!playing) return 0f
        val raw = seconds(start, at)
        return if (autoPlay) raw.mod(LOOP) else min(raw, LOOP).mod(LOOP)
    }

    val paused = if (reduced) !fading else (held != null || !playing)
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (isActive) withFrameNanos { now = System.nanoTime() }
    }
    LaunchedEffect(Unit) { start = System.nanoTime() }
    // Without autoPlay the story stops on the cover when a pass ends.
    LaunchedEffect(start, playing) {
        if (autoPlay || !playing) return@LaunchedEffect
        val left = LOOP - seconds(start, System.nanoTime())
        delay((max(left, 0f) * 1000).toLong())
        if (held == null) playing = false
    }
    LaunchedEffect(stillChange) {
        if (!fading) return@LaunchedEffect
        delay((M.FADE * 1000).toLong())
        fading = false
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0D2433)),
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = insets.calculateBottomPadding().value

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "$kicker. ${title.joinToString(" ")}. ${caption.joinToString(" ")} ${lines.joinToString(" ")}"
                }
                .pointerInput(reduced, autoPlay) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downAt = System.nanoTime()
                        if (!reduced) held = storyTime(downAt)
                        var last = down.position
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            last = change.position
                            if (!change.pressed) break
                        }
                        val moved = (last - down.position).let { hypot(it.x, it.y) } / density
                        val tap = seconds(downAt, System.nanoTime()) < M.TAP_TIME && moved < M.TAP_SLOP
                        val forward = last.x >= size.width / 3f
                        if (reduced) {
                            stillBefore = still
                            still = if (forward) (still + 1) % 3 else max(still - 1, 0)
                            stillChange = System.nanoTime()
                            now = stillChange
                            fading = true
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            return@awaitEachGesture
                        }
                        val at = held ?: 0f
                        held = null
                        val t = System.nanoTime()
                        if (tap) {
                            val current = cardAt(at)
                            val target = if (forward) (if (current == 2) 8.8f else M.CARDS[current + 1]) else M.CARDS[max(current - 1, 0)]
                            start = t - (target * 1e9f).toLong()
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        } else {
                            start = t - (at * 1e9f).toLong()
                        }
                        now = t
                        playing = true
                    }
                },
        ) {
            val frame = Frame(size.width / density, size.height / density, top, bottom)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                val painter = Painter(c, type, frame, label, kicker, title, caption, number, lines)
                if (reduced) {
                    val k = clamp(seconds(stillChange, now) / M.FADE)
                    if (k < 1f) {
                        painter.still(stillBefore)
                        c.saveLayerAlpha(0f, 0f, frame.w, frame.h, (k * 255f).roundToInt())
                        painter.still(still)
                        c.restore()
                    } else {
                        painter.still(still)
                    }
                } else {
                    val s = storyTime(now)
                    painter.mesh(s, meshWeights(s))
                    painter.cover(s)
                    painter.first(s)
                    painter.second(s)
                    painter.chrome(s)
                }
                c.restore()
            }
        }
    }
}

private fun cardAt(s: Float) = when {
    s >= M.CARDS[2] && s < 9.2f -> 2
    s >= M.CARDS[1] && s < M.CARDS[2] -> 1
    else -> 0
}

private class Frame(val w: Float, val h: Float, val top: Float, val bottom: Float)

/** Inter Tight Bold, measured in dp. */
private class KineticStatsStoryType(assets: AssetManager) {
    val typeface: Typeface = Typeface.createFromAsset(assets, "kineticstatsstory/kineticstatsstory_InterTight-Bold.ttf")
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = this@KineticStatsStoryType.typeface }

    /** Cap height per unit of size, from the outline of "H". */
    val capRatio: Float = run {
        paint.textSize = 100f
        val bounds = Rect()
        paint.getTextBounds("H", 0, 1, bounds)
        if (bounds.height() > 0) bounds.height() / 100f else 0.7275f
    }

    fun capHeight(size: Float) = size * capRatio

    fun width(text: String, size: Float): Float {
        paint.textSize = size
        return paint.measureText(text)
    }

    /** The largest size up to [cap] at which [text] fits [width]. */
    fun fit(text: String, width: Float, cap: Float): Float {
        val unit = width(text, 100f)
        return if (unit > 0f) min(cap, 100f * width / unit) else cap
    }
}

private class Painter(
    val c: android.graphics.Canvas,
    val type: KineticStatsStoryType,
    val f: Frame,
    val label: String,
    val kicker: String,
    val title: List<String>,
    val caption: List<String>,
    val number: Int,
    val lines: List<String>,
) {
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = type.typeface }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.WHITE
    }

    fun still(card: Int) {
        val weights = when (card) {
            0 -> floatArrayOf(1f, 0f, 0f)
            1 -> floatArrayOf(0f, 1f, 0f)
            else -> floatArrayOf(0f, 0f, 1f)
        }
        mesh(floatArrayOf(0f, 3.5f, 7f)[card], weights)
        when (card) {
            0 -> cover(0f)
            1 -> first(3f, fit = true)
            else -> second(7f, fit = true)
        }
        chrome(floatArrayOf(0f, 4.95f, 8.85f)[card])
    }

    /** Draws [string] with its baseline at [baseline] and its left edge at [x] (plus the look's offset). */
    private fun draw(string: String, size: Float, x: Float, baseline: Float, look: Look = Look(), opacity: Float = 1f) {
        if (look.alpha <= 0.002f || string.isEmpty()) return
        text.textSize = size
        text.color = android.graphics.Color.WHITE
        text.alpha = (look.alpha * opacity * 255f).roundToInt().coerceIn(0, 255)
        // SwiftUI's blur radius is a sigma; Android's mask filter takes a radius (hardware canvases from API 28).
        text.maskFilter = if (look.blur > 0.05f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            BlurMaskFilter(sigmaToRadius(look.blur), BlurMaskFilter.Blur.NORMAL)
        } else {
            null
        }
        c.drawText(string, x + look.dx, baseline, text)
    }

    private fun mix(weights: FloatArray, index: Int): Int {
        var r = 0f
        var g = 0f
        var b = 0f
        for (p in PALETTES.indices) {
            val col = PALETTES[p][index]
            r += weights[p] * ((col shr 16) and 0xff)
            g += weights[p] * ((col shr 8) and 0xff)
            b += weights[p] * (col and 0xff)
        }
        return android.graphics.Color.rgb(r.roundToInt().coerceIn(0, 255), g.roundToInt().coerceIn(0, 255), b.roundToInt().coerceIn(0, 255))
    }

    fun mesh(s: Float, weights: FloatArray) {
        val w = f.w
        val h = f.h
        fill.shader = null
        fill.color = mix(weights, 0)
        c.drawRect(0f, 0f, w, h, fill)
        val reach = max(w, h)
        val phase = 2f * PI.toFloat() * s / LOOP
        BLOBS.forEachIndexed { i, blob ->
            val x = w * (blob.x + blob.ax * sin(blob.fx * phase + blob.px))
            val y = h * (blob.y + blob.ay * sin(blob.fy * phase + blob.py))
            val r = reach * blob.r
            val color = mix(weights, i + 1)
            val clear = color and 0x00ffffff
            val mid = (color and 0x00ffffff) or (0x99 shl 24)
            fill.shader = RadialGradient(x, y, r, intArrayOf(color, mid, clear), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, fill)
        }
        // A light scrim under the story bars and the header.
        fill.shader = LinearGradient(0f, 0f, 0f, f.top + 90f, 0x38000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, f.top + 90f, fill)
        fill.shader = null
    }

    fun cover(s: Float) {
        val size = type.fit(title.maxByOrNull { it.length } ?: "", f.w - 2f * M.MARGIN, 120f)
        val cap = type.capHeight(size)
        val baseline2 = f.h - f.bottom - 92f
        val baseline1 = baseline2 - 0.92f * size
        val kickerBaseline = baseline1 - cap - 22f
        val rows = listOf(Triple(kicker, 22f, kickerBaseline), Triple(title[0], size, baseline1), Triple(title.getOrElse(1) { "" }, size, baseline2))
        rows.forEachIndexed { i, row ->
            val look = if (s >= 9f) Look.enter(s, 9.2f + 0.07f * i, 0.5f) else Look.leave(s, 1.55f + 0.05f * i, 0.35f)
            draw(row.first, row.second, M.MARGIN, row.third, look, if (i == 0) 0.85f else 1f)
        }
    }

    fun first(s: Float, fit: Boolean = false) {
        if (s <= 1.8f || s >= 5.5f) return
        caption.forEachIndexed { i, line ->
            val look = if (fit) Look() else Look.enter(s, 1.85f + 0.06f * i, 0.5f).then(Look.leave(s, 4.95f + 0.05f * i, 0.4f))
            draw(line, 30f, M.MARGIN, f.top + 86f + 36f * i, look)
        }

        // The number: one slot per digit, as wide as the widest digit, and the group separators.
        val string = String.format(Locale.US, "%,d", number)
        var size = 0.7f * f.w
        var layout = slots(string, size)
        if (fit) {
            size *= min(1f, (f.w - 2f * M.MARGIN) / layout.second)
            layout = slots(string, size)
        }
        val cap = type.capHeight(size)
        val baseline = 0.6f * f.h + cap / 2f
        var x0 = M.MARGIN
        if (!fit) {
            if (s <= 2.15f || s >= 5.2f) return
            x0 = M.MARGIN +
                (f.w - 8f) * (1f - easeOutCubic(clamp((s - 2.15f) / 0.75f))) -
                (layout.second + 0.3f * f.w + M.MARGIN) * easeInCubic(clamp((s - 2.75f) / 2.45f))
        }
        val roll = if (fit) 1f else easeOutCubic(clamp((s - 2.2f) / 1.2f))
        val step = cap * 1.25f
        c.save()
        c.clipRect(0f, baseline - cap * 1.08f, f.w, baseline + cap * 0.08f)
        var digit = 0
        for (slot in layout.first) {
            val x = x0 + slot.x
            if (slot.char.isDigit()) {
                val target = slot.char.digitToInt().toFloat()
                val position = (target + 10f * digit) * roll
                val base = floor(position)
                val frac = position - base
                val a = (base.toInt() % 10).toString()
                val b = ((base.toInt() + 1) % 10).toString()
                // The outgoing digit rises and fades, the next one comes up from below.
                draw(a, size, x + (slot.width - type.width(a, size)) / 2f, baseline - frac * step, Look(alpha = 1f - frac))
                if (frac > 0.001f) draw(b, size, x + (slot.width - type.width(b, size)) / 2f, baseline + (1f - frac) * step, Look(alpha = frac))
                digit++
            } else {
                draw(slot.char.toString(), size, x, baseline)
            }
        }
        c.restore()
    }

    private class Slot(val char: Char, val x: Float, val width: Float)

    /** Digits get equal slots (the widest digit's advance), so the number does not jitter while it rolls. */
    private fun slots(string: String, size: Float): Pair<List<Slot>, Float> {
        val digit = (0..9).maxOf { type.width(it.toString(), size) }
        var x = 0f
        val list = string.map { ch ->
            val w = if (ch.isDigit()) digit else type.width(ch.toString(), size)
            Slot(ch, x, w).also { x += w }
        }
        return list to x
    }

    fun second(s: Float, fit: Boolean = false) {
        if (!fit && (s <= 5.3f || s >= 9.4f)) return
        var size = 0.4f * f.w
        val widest = lines.maxOfOrNull { type.width(it, size) } ?: 1f
        if (fit) size *= min(1f, (f.w - 2f * M.MARGIN) / widest)
        val pitch = 0.94f * size
        val cap = type.capHeight(size)
        // The block of lines is centred a little below the middle.
        val firstBaseline = 0.53f * f.h - pitch * (lines.size - 1) / 2f + cap / 2f
        lines.forEachIndexed { i, line ->
            val baseline = firstBaseline + pitch * i
            if (fit) {
                draw(line, size, M.MARGIN, baseline)
                return@forEachIndexed
            }
            val width = type.width(line, size)
            val speed = max(0f, M.MARGIN + width - 0.62f * f.w) / 3.5f
            val arrive = 5.35f + 0.1f * i
            val x = M.MARGIN +
                0.95f * f.w * (1f - easeOutCubic(clamp((s - arrive) / 0.85f))) -
                speed * max(0f, s - 5.35f)
            val out = clamp((s - 8.8f - 0.05f * i) / 0.4f)
            val look = Look(-0.7f * f.w * easeInCubic(out), 1f - smoothstep(0.3f, 1f, out), M.BLUR * easeInCubic(out))
            draw(line, size, x, baseline, look)
        }
    }

    fun chrome(s: Float) {
        val w = f.w
        // Story bars.
        val gap = 4f
        val inset = 12f
        val barWidth = (w - 2f * inset - gap * 2f) / 3f
        val drain = 1f - easeInOut(clamp((s - M.DRAIN_START) / M.DRAIN))
        fill.shader = null
        M.BARS.forEachIndexed { i, bar ->
            val left = inset + i * (barWidth + gap)
            val y = f.top + 8f
            fill.color = android.graphics.Color.argb(82, 255, 255, 255)
            c.drawRoundRect(RectF(left, y, left + barWidth, y + 3f), 1.5f, 1.5f, fill)
            val amount = clamp((s - bar.first) / (bar.second - bar.first)) * drain
            if (amount > 0f) {
                fill.color = android.graphics.Color.WHITE
                c.drawRoundRect(RectF(left, y, left + max(3f, barWidth * amount), y + 3f), 1.5f, 1.5f, fill)
            }
        }
        // Header: label and close button.
        val row = f.top + 32f
        draw(label, 15f, 16f, row + type.capHeight(15f) / 2f)
        val cx = w - 16f - 15f
        fill.color = android.graphics.Color.argb(56, 255, 255, 255)
        c.drawCircle(cx, row, 15f, fill)
        stroke.strokeWidth = 2f
        c.drawLine(cx - 4.5f, row - 4.5f, cx + 4.5f, row + 4.5f, stroke)
        c.drawLine(cx + 4.5f, row - 4.5f, cx - 4.5f, row + 4.5f, stroke)

        // Bottom corners: pause and share.
        val cy = f.h - f.bottom - 12f - 18f
        for (x in floatArrayOf(16f + 18f, w - 16f - 18f)) c.drawCircle(x, cy, 18f, fill)
        fill.color = android.graphics.Color.WHITE
        val left = 16f + 18f
        for (dx in floatArrayOf(-3.5f, 3.5f)) c.drawRoundRect(RectF(left + dx - 1.5f, cy - 6.5f, left + dx + 1.5f, cy + 6.5f), 1.5f, 1.5f, fill)
        val right = w - 16f - 18f
        stroke.strokeWidth = 1.8f
        val share = android.graphics.Path().apply {
            moveTo(right, cy + 2f); lineTo(right, cy - 8f)
            moveTo(right - 3.5f, cy - 4.5f); lineTo(right, cy - 8f); lineTo(right + 3.5f, cy - 4.5f)
            moveTo(right - 3f, cy - 2f); lineTo(right - 6f, cy - 2f); lineTo(right - 6f, cy + 7f)
            lineTo(right + 6f, cy + 7f); lineTo(right + 6f, cy - 2f); lineTo(right + 3f, cy - 2f)
        }
        c.drawPath(share, stroke)
    }
}
