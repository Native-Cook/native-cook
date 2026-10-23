package com.ngocdevv.nativecook.blocks.text.accordionletters

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 Accordion Letters: four rows of one repeated capital (Anybody Black), each row a fixed width shared by
 its five letters, which squeeze to slivers and stretch to slabs like the bellows of an accordion.

 A row's letter widths are proportional to exp(G·s·(2 − j)) for letters j = 0 … 4, with G = 0.8, so at
 s = +1 the first letter is 24.5 times as wide as the last and at s = −1 the other way round; the widths
 always add up to the row (minus 5 dp gaps).

 Timeline of the self-playing loop (LOOP = 7.2 s, six beats of 1.2 s):
   beat b shows letter A, B, C (b mod 3) in its palette: blue with pale grey, black with yellow,
   orange-red with black; palettes and letters cut hard at the beat's start.
   In beat b every row flips s from σ to −σ (σ = +1 on even beats, −1 on odd), row r starting at
   0.15 + 0.07·r s and taking 0.7 s on an ease-in-out cubic, so the flip ripples down the rows; each
   beat ends where the next begins. Beat 6 is beat 0 again: A, blue, wide on the left — the resting state.

 Touch: dragging across the screen pulls the bulge to the finger: the letter under it grows widest
 (log-width 2.4·exp(−((j − f)/1.1)²), f the finger's place in letters) and each row follows 0.07 s after
 the one above; the drag's shape blends in over 0.25 s. A tap plays the next beat now (cut and flip),
 the drag's shape giving way to it over 0.25 s.
 2 s after the last touch the loop starts over from its first frame and the drag's shape blends out over
 0.5 s. Reduced motion: the letters sit at equal widths and a tap cuts to the next letter and palette.
*/

private object M {
    const val BEAT = 1.2f
    const val FLIP_AT = 0.15f
    const val FLIP = 0.7f
    const val ROW_LAG = 0.07f
    const val ROWS = 4
    const val COUNT = 5
    const val GAIN = 0.8f
    const val GAP = 5f
    const val MARGIN = 16f
    const val ROW_HEIGHT = 110f
    const val ROW_GAP = 14f
    const val BULGE = 2.4f
    const val BULGE_WIDTH = 1.1f
    const val BLEND_IN = 0.25f
    const val BLEND_OUT = 0.5f
    const val IDLE = 2f
    const val BLOCK_HEIGHT = ROWS * ROW_HEIGHT + (ROWS - 1) * ROW_GAP
}

/** The self-playing loop, in seconds. */
const val LOOP = M.BEAT * 6

private class Palette(val back: Color, val ink: Color)

private val Palettes = listOf(
    Palette(Color(0xFF0A47E8), Color(0xFFDCDCDC)),
    Palette(Color(0xFF0D0D0D), Color(0xFFFADB0A)),
    Palette(Color(0xFFFF4A1C), Color(0xFF111111)),
)

private val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private fun seconds(from: Long, to: Long) = (to - from) / 1e9f

private fun easeInOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

/** Log-widths of a row's letters for beat [b] at [tau]. */
private fun loopShape(b: Int, tau: Float, row: Int, out: FloatArray) {
    val sigma = if (b % 2 == 0) 1f else -1f
    val p = easeInOutCubic(((tau - M.FLIP_AT - M.ROW_LAG * row) / M.FLIP).coerceIn(0f, 1f))
    val s = sigma * (1f - 2f * p)
    val centre = (M.COUNT - 1) / 2f
    for (j in 0 until M.COUNT) out[j] = M.GAIN * s * (centre - j)
}

/** The finger's place along a row, in letters (0 … 4). */
private fun fingerIndex(x: Float, width: Float): Float {
    val fraction = (x - M.MARGIN) / (width - 2f * M.MARGIN)
    return fraction.coerceIn(0f, 1f) * M.COUNT - 0.5f
}

/** A number animating towards a target, sampled against the frame clock. */
private class Tween(val from: Float, val to: Float, val start: Long, val duration: Float, val easing: Easing) {
    fun value(at: Long): Float {
        if (duration <= 0f) return to
        val t = (seconds(start, at) / duration).coerceIn(0f, 1f)
        return from + (to - from) * easing.transform(t)
    }
}

private sealed interface Mode {
    data object Loop : Mode
    /** A beat played by a tap, from [start]; it holds at its end. */
    data class Beat(val beat: Int, val start: Long) : Mode
}

/** The finger's place along a row over the last second, so lower rows can follow a little later. */
private class Trail {
    private val times = ArrayList<Long>()
    private val values = ArrayList<Float>()

    fun add(value: Float, at: Long) {
        times += at
        values += value
        while (times.isNotEmpty() && seconds(times[0], at) > 1f) {
            times.removeAt(0)
            values.removeAt(0)
        }
    }

    /** The place at [at], interpolated between samples (the oldest before them, the newest after). */
    fun value(at: Long): Float {
        if (times.isEmpty()) return 0f
        if (at <= times[0]) return values[0]
        if (at >= times.last()) return values.last()
        for (i in 1 until times.size) {
            if (times[i] >= at) {
                val span = (times[i] - times[i - 1]).toFloat()
                return if (span > 0f) values[i - 1] + (values[i] - values[i - 1]) * (at - times[i - 1]) / span else values[i]
            }
        }
        return values.last()
    }
}

/** Letter outlines of Anybody Black at 100 units (baseline at y = 0) with their boxes. */
private class Glyphs(private val typeface: Typeface) {
    class Glyph(val path: Path, val box: RectF, val capHeight: Float)

    private val cache = HashMap<String, Glyph>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = this@Glyphs.typeface
        textSize = 100f
    }
    private val capHeight: Float by lazy {
        val p = Path().also { paint.getTextPath("H", 0, 1, 0f, 0f, it) }
        RectF().also { p.computeBounds(it, true) }.height()
    }

    fun glyph(letter: String): Glyph = cache.getOrPut(letter) {
        val s = letter.take(1)
        val path = Path().also { paint.getTextPath(s, 0, s.length, 0f, 0f, it) }
        Glyph(path, RectF().also { path.computeBounds(it, true) }, capHeight)
    }
}

/**
 * Four rows of one repeated capital whose letters squeeze to slivers and stretch to slabs like the
 * bellows of an accordion, the letter and its palette cutting hard on every beat.
 */
@Composable
fun AccordionLetters(
    modifier: Modifier = Modifier,
    letters: List<String> = listOf("A", "B", "C"),
    kicker: String = "Width axis",
    range: String = "12 – 300 %",
    title: String = "Every letter,\nevery width.",
    hint: String = "Drag across the rows · tap for the next letter",
    /** Plays the accordion by itself, every [LOOP] seconds round. */
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val glyphs = remember(context) {
        Glyphs(Typeface.createFromAsset(context.assets, "accordionletters/accordionletters_Anybody-Black.ttf"))
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val logs = remember { FloatArray(M.COUNT) }
    val trail = remember { Trail() }
    val playing by rememberUpdatedState(autoPlay)
    val shown by rememberUpdatedState(letters)

    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var loopStart by remember { mutableLongStateOf(System.nanoTime()) }
    var mode by remember { mutableStateOf<Mode>(Mode.Loop) }
    var blend by remember { mutableStateOf(Tween(0f, 0f, 0L, 0f, EaseOutStrong)) }
    var touches by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragged by remember { mutableStateOf(false) }
    /** The beat a drag froze, and how far into it. */
    var frozen by remember { mutableStateOf(0 to 0f) }
    var stillBeat by remember { mutableIntStateOf(0) }

    /** The beat on screen and how far into it (0 … 1.2 s). */
    fun beatAt(at: Long): Pair<Int, Float> {
        if (reduced) return stillBeat to M.BEAT
        if (dragged) return frozen
        return when (val m = mode) {
            is Mode.Beat -> m.beat to min(seconds(m.start, at), M.BEAT)
            Mode.Loop -> {
                if (!playing) return 0 to 0f
                val e = seconds(loopStart, at).mod(LOOP)
                val b = (e / M.BEAT).toInt().coerceAtMost(5)
                b to e - b * M.BEAT
            }
        }
    }

    val beat by remember { derivedStateOf { beatAt(now).first } }
    val fading by remember { derivedStateOf { blend.value(now) > 0f } }
    val paused = reduced || (!autoPlay && !dragging && !fading && mode == Mode.Loop)

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (isActive) withFrameNanos { now = System.nanoTime() }
    }
    LaunchedEffect(Unit) { loopStart = System.nanoTime() }
    // 2 s after the last touch the loop starts over and a drag's shape blends out.
    LaunchedEffect(touches) {
        if (touches == 0 || dragging) return@LaunchedEffect
        delay((M.IDLE * 1000).toLong())
        if (dragging) return@LaunchedEffect
        val at = System.nanoTime()
        loopStart = at
        mode = Mode.Loop
        stillBeat = 0
        if (dragged) {
            dragged = false
            blend = Tween(blend.value(at), 0f, at, M.BLEND_OUT, EaseInOut)
        }
        now = at
    }

    val palette = Palettes[beat % Palettes.size]
    val ink = palette.ink

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val blockTop = height * 0.47f - M.BLOCK_HEIGHT / 2f
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding()
        val bottom = max(insets.calculateBottomPadding().value, 16f)

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = shown[beat % shown.size].repeat(M.COUNT) }
                .pointerInput(width) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        touches++
                        var tick = Int.MIN_VALUE
                        // The finger has moved, so this touch is a drag, not a tap.
                        var swiping = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            if (reduced) continue
                            val at = System.nanoTime()
                            if ((change.position - down.position).getDistance() / density > 8f) swiping = true
                            if (!swiping) continue
                            if (!dragged) {
                                frozen = beatAt(at)
                                dragged = true
                                blend = Tween(blend.value(at), 1f, at, M.BLEND_IN, EaseOutStrong)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                            val f = fingerIndex(change.position.x / density, width)
                            trail.add(f, at)
                            val notch = f.roundToInt()
                            if (notch != tick) {
                                if (tick != Int.MIN_VALUE) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                tick = notch
                            }
                            change.consume()
                        }
                        val at = System.nanoTime()
                        if (reduced) {
                            stillBeat++
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        } else if (!swiping) {
                            // A tap: the next beat, now; a drag's shape gives way to it.
                            val b = beatAt(at).first
                            if (dragged) {
                                dragged = false
                                blend = Tween(blend.value(at), 0f, at, M.BLEND_IN, EaseOutStrong)
                            }
                            mode = Mode.Beat(b + 1, at)
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        dragging = false
                        touches++
                    }
                },
        ) {
            val at = now
            val (b, tau) = beatAt(at)
            val colours = Palettes[b % Palettes.size]
            val glyph = glyphs.glyph(shown[b % shown.size])
            val k = if (reduced) 0f else blend.value(at)
            val rowWidth = width - 2f * M.MARGIN - M.GAP * (M.COUNT - 1)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.drawColor(colours.back.toArgb())
                c.save()
                c.scale(density, density)
                paint.color = colours.ink.toArgb()
                for (row in 0 until M.ROWS) {
                    if (reduced) logs.fill(0f) else loopShape(b, tau, row, logs)
                    if (k > 0f) {
                        val f = trail.value(at - (M.ROW_LAG * row * 1e9f).toLong())
                        for (j in 0 until M.COUNT) {
                            val d = (j - f) / M.BULGE_WIDTH
                            logs[j] += (M.BULGE * exp(-d * d) - logs[j]) * k
                        }
                    }
                    var total = 0f
                    for (j in 0 until M.COUNT) total += exp(logs[j])
                    val baseline = blockTop + row * (M.ROW_HEIGHT + M.ROW_GAP) + M.ROW_HEIGHT
                    var x = M.MARGIN
                    for (j in 0 until M.COUNT) {
                        val w = exp(logs[j]) / total * rowWidth
                        val sx = w / glyph.box.width()
                        val sy = M.ROW_HEIGHT / glyph.capHeight
                        c.save()
                        c.translate(x - glyph.box.left * sx, baseline)
                        c.scale(sx, sy)
                        c.drawPath(glyph.path, paint)
                        c.restore()
                        x += w + M.GAP
                    }
                }
                c.restore()
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = top + 16.dp)
                .height(20.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(kicker.uppercase(), style = mono(12f, FontWeight.SemiBold, ink.copy(alpha = 0.75f), 2f), maxLines = 1)
            Spacer(Modifier.weight(1f).width(12.dp))
            BasicText(range, style = mono(12f, FontWeight.SemiBold, ink.copy(alpha = 0.75f), 2f), maxLines = 1)
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = (bottom + 12f).dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(
                    title,
                    style = TextStyle(color = ink, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, platformStyle = NoPadding),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    letters.forEachIndexed { index, l ->
                        BasicText(l, style = mono(15f, FontWeight.Bold, ink.copy(alpha = if (index == beat % letters.size) 1f else 0.35f), 0f))
                    }
                }
            }
            BasicText(hint.uppercase(), style = mono(10f, FontWeight.Medium, ink.copy(alpha = 0.6f), 1.4f), maxLines = 1)
        }
    }
}

private val NoPadding = PlatformTextStyle(includeFontPadding = false)

private fun mono(size: Float, weight: FontWeight, color: Color, tracking: Float) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    fontFamily = FontFamily.Monospace,
    letterSpacing = tracking.sp,
    platformStyle = NoPadding,
)
