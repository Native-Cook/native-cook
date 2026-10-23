package com.ngocdevv.nativecook.blocks.text.gleamlineintro

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 Gleam Line Intro — the opening of a journaling app: short lines of a serif on black, each word
 arriving with a gleam, then fading for the next beat. A port of the SwiftUI block
 (GleamLineIntro.swift); the numbers are the same.

 Text: Newsreader (a static instance at weight 360, optical size 60), 26 sp, left aligned at 32 dp with
 a 1.3 line height, the block centred on 46 % of the height. Settled words are warm grey #D8D2C8;
 italic words are the emphasis. Three thin progress segments at the top fill with the beats; a hint
 ("Tap to continue") sits at the bottom during the first two beats, a white "Begin writing" pill on
 the last one.

 A word arriving, p = (t − its start) / 0.7 s:
   opacity     smoothstep(0, 0.6, p)
   blur        5 · (1 − easeOutCubic(p / 0.8)) (a Gaussian sigma; BlurMaskFilter takes a radius)
   drift       rises 7 dp into place, easeOutCubic over p = 0 … 1
   gleam       g = exp(−((p − 0.42) / 0.2)²): its colour runs to white and a white glow (sigma 8,
               60 % · g) blooms behind it, so with the words staggered a highlight sweeps along the line.
 A word leaving over d seconds, q = (t − its fade) / d: opacity × (1 − smoothstep(q)), blur + 4·q,
 drifting 6 dp up.

 autoPlay timeline (LOOP = 10 s); the loop starts and ends on the last beat, fully shown:
   0.0 … 1.2   rest: "Keep them here, / one line a day." and the Begin pill.
   1.2 … 1.8   that beat and the pill fade out (0.6 s); the progress segments empty.
   1.9 … 4.9   beat 1, "Some days are loud. / Most of them go unwritten.", word by word every 0.16 s;
               it fades out at 4.9 (0.6 s). The hint fades in at 1.6 and out at 8.6 (0.4 s each).
   5.5 … 8.65  beat 2, three lines arriving at 5.5, 6.3 and 7.1 (a word every 0.12 s), dimmed away line
               by line from 7.9, 8.05 and 8.2 (0.45 s each).
   8.7 … 9.94  beat 3 again, a word every 0.09 s; the pill fades in from 9.3 to 9.8. t = 10 = t = 0.
   Segment k fills linearly while its beat plays: 1.9 → 4.9, 5.5 → 8.6, 8.7 → 9.9.

 Touch: a tap skips to the next beat: what is on screen freezes and fades out in 0.35 s, then the
 next beat arrives as in the loop and holds. 2 s after the last tap the last beat is brought back
 the same way (if it is not already showing) and the loop carries on from rest. A haptic per tap.
 Reduced motion: the last beat stands still and the loop is off; taps still step through the beats,
 the words fading in without blur, drift or gleam.
 */

private const val LOOP = 10.0
private const val FONT_SIZE = 26f
private const val WORD_SPAN = 0.7
private const val REGULAR_ASSET = "gleamlineintro/gleamlineintro_Newsreader.ttf"
private const val ITALIC_ASSET = "gleamlineintro/gleamlineintro_NewsreaderItalic.ttf"

private val Settled = Color(0xFFD8D2C8)
private val HintGrey = Color(0xFF8A857D)

private fun nowSeconds() = System.nanoTime() / 1e9

private fun smoothstep(a: Double, b: Double, x: Double): Double {
    val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

private fun easeOutCubic(t: Double) = 1 - (1 - t.coerceIn(0.0, 1.0)).pow(3)

// Android's blur takes a radius; the numbers here are Gaussian sigmas (as in SwiftUI and Skia).
private fun sigmaToRadius(sigma: Double) = ((sigma - 0.5) / 0.57735).coerceAtLeast(0.01).toFloat()

/** A beat's times in the loop. */
private class Beat(
    /** When its first word starts, and the latest loop time that still shows it whole. */
    val reveal: Double,
    val hold: Double,
    /** When each line starts, and the gap between its words. */
    val lineStarts: List<Double>,
    val stagger: Double,
    /** When each line fades, and over how long; the last beat fades at 1.2 of the next loop. */
    val fades: List<Double>,
    val fadeSpan: Double,
)

private val Beats = listOf(
    Beat(1.9, 4.85, listOf(1.9), 0.16, listOf(4.9), 0.6),
    Beat(5.5, 7.85, listOf(5.5, 6.3, 7.1), 0.12, listOf(7.9, 8.05, 8.2), 0.45),
    Beat(8.7, 9.999, listOf(8.7), 0.09, listOf(1.2), 0.6),
)

/** The beat after the one on screen at loop time [e]. */
private fun nextBeat(e: Double) = when {
    e < 1.2 -> 0
    e < 4.9 -> 1
    e < 8.7 -> 2
    else -> 0
}

/** A word's look at a loop time: opacity, blur (sigma), vertical drift and gleam. */
private class Look(var alpha: Double = 0.0, var blur: Double = 0.0, var dy: Double = 0.0, var gleam: Double = 0.0)

/** A beat with one start counts its words through the beat ([index]); one with a start per line through the line ([place]). */
private fun look(beat: Int, line: Int, index: Int, place: Int, e: Double, reduced: Boolean): Look {
    val b = Beats[beat]
    var s: Double
    var f: Double
    if (b.lineStarts.size == 1) {
        s = b.lineStarts[0] + index * b.stagger
        f = b.fades[0]
    } else {
        s = b.lineStarts[min(line, b.lineStarts.size - 1)] + place * b.stagger
        f = b.fades[min(line, b.fades.size - 1)]
    }
    if (beat == 2) {
        // The last beat is whole at the start of the loop and fades at 1.2; later it arrives again.
        if (e < 5) s -= LOOP else f += LOOP
    }
    val p = (e - s) / WORD_SPAN
    if (p <= 0) return Look()
    val look = Look(alpha = smoothstep(0.0, 0.6, p))
    if (!reduced) {
        look.blur = 5 * (1 - easeOutCubic(min(1.0, p / 0.8)))
        look.dy = 7 * (1 - easeOutCubic(min(1.0, p)))
        look.gleam = if (p < 1.2) exp(-((p - 0.42) / 0.2).pow(2)) else 0.0
    }
    if (e >= f) {
        val q = (e - f) / b.fadeSpan
        if (q >= 1) return Look()
        val k = smoothstep(0.0, 1.0, q)
        look.alpha *= 1 - k
        if (!reduced) {
            look.blur += 4 * q
            look.dy -= 6 * k
        }
    }
    return look
}

/** The chrome at loop time [e]: the segments' fills, the hint's and the pill's opacity. */
private class Chrome(val fills: DoubleArray, val hint: Double, val pill: Double)

private fun chrome(e: Double): Chrome {
    val fills = when {
        e < 1.2 -> doubleArrayOf(1.0, 1.0, 1.0)
        e < 1.8 -> {
            val k = 1 - smoothstep(1.2, 1.8, e)
            doubleArrayOf(k, k, k)
        }
        else -> listOf(1.9 to 4.9, 5.5 to 8.6, 8.7 to 9.9).map { (a, b) -> ((e - a) / (b - a)).coerceIn(0.0, 1.0) }.toDoubleArray()
    }
    val hint = smoothstep(1.6, 2.0, e) * (1 - smoothstep(8.6, 9.0, e))
    val pill = if (e < 5) 1 - smoothstep(1.2, 1.6, e) else smoothstep(9.3, 9.8, e)
    return Chrome(fills, hint, pill)
}

private class Word(val text: String, val italic: Boolean, val x: Float, val baseline: Float, val line: Int, val index: Int, val place: Int)

/** Every word of every beat; *…* marks italics, and lines wrap greedily if one is too long. */
private fun layoutBeats(texts: List<String>, width: Double, height: Double, regular: Paint, italic: Paint): List<List<Word>> {
    val fm = regular.fontMetrics
    val ascent = -fm.ascent.toDouble()
    val descent = fm.descent.toDouble()
    val lineHeight = 1.3 * FONT_SIZE
    val left = 32.0
    val maxWidth = width - 64
    val space = regular.measureText(" ").toDouble()
    return texts.map { text ->
        val lines = mutableListOf<MutableList<Triple<String, Boolean, Double>>>()
        for (raw in text.split("\n")) {
            var line = mutableListOf<Triple<String, Boolean, Double>>()
            var w = 0.0
            var inItalic = false
            for (token in raw.split(" ").filter { it.isNotEmpty() }) {
                var word = token
                if (word.startsWith("*")) { inItalic = true; word = word.drop(1) }
                val isItalic = inItalic
                if (word.endsWith("*")) { inItalic = false; word = word.dropLast(1) }
                val a = (if (isItalic) italic else regular).measureText(word).toDouble()
                if (line.isNotEmpty() && w + space + a > maxWidth) {
                    lines += line
                    line = mutableListOf()
                    w = 0.0
                }
                w += (if (line.isEmpty()) 0.0 else space) + a
                line += Triple(word, isItalic, a)
            }
            lines += line
        }
        val top = height * 0.46 - lines.size * lineHeight / 2
        var count = 0
        lines.flatMapIndexed { li, line ->
            val baseline = top + li * lineHeight + (lineHeight - ascent - descent) / 2 + ascent
            var x = left
            line.mapIndexed { wi, (word, isItalic, a) ->
                Word(word, isItalic, x.toFloat(), baseline.toFloat(), li, count++, wi).also { x += a + space }
            }
        }
    }
}

private class GleamController(val reduced: Boolean, val autoPlay: Boolean) {
    var frame by mutableDoubleStateOf(nowSeconds())
    var autoStart: Double? = null
    /** A beat shown by hand: which, and when it was asked for. */
    var manualBeat = -1
    var manualAt = 0.0
    /** What was on screen when the last change was asked for, frozen at that loop time, and when. */
    var outgoingE: Double? = null
    var outgoingAt = 0.0
    var resume: Job? = null

    /** The loop time that shows what is asked for. */
    fun loopTime(now: Double): Double {
        if (manualBeat >= 0) {
            val b = Beats[manualBeat]
            return min(b.reveal + max(0.0, now - manualAt - 0.35) - 0.001, b.hold)
        }
        val start = autoStart
        if (start != null && now >= start && !reduced) return (now - start) % LOOP
        return 0.0
    }

    /** The frozen outgoing time and how much of it still shows. */
    fun outgoing(now: Double): Pair<Double, Double>? {
        val e = outgoingE ?: return null
        val k = smoothstep(0.0, 0.35, now - outgoingAt)
        return if (k < 1) e to 1 - k else null
    }

    fun show(beat: Int, now: Double) {
        outgoingE = loopTime(now)
        outgoingAt = now
        autoStart = null
        manualBeat = beat
        manualAt = now
    }

    fun chromeAt(now: Double): Chrome {
        val c = chrome(loopTime(now))
        val out = outgoing(now) ?: return c
        val was = chrome(out.first)
        val k = out.second
        return Chrome(DoubleArray(3) { c.fills[it] * (1 - k) + was.fills[it] * k }, c.hint * (1 - k) + was.hint * k, c.pill * (1 - k) + was.pill * k)
    }
}

@Composable
fun GleamLineIntro(
    modifier: Modifier = Modifier,
    beats: List<String> = listOf(
        "Some days are loud.\nMost of them go *unwritten.*",
        "A thought on the train.\nA name you almost forgot.\nThe light at six o’clock.",
        "Keep them here,\n*one line a day.*",
    ),
    hint: String = "Tap to continue",
    action: String = "Begin writing",
    autoPlay: Boolean = false,
    onAction: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val regularFace = remember { Typeface.createFromAsset(context.assets, REGULAR_ASSET) }
    val italicFace = remember { Typeface.createFromAsset(context.assets, ITALIC_ASSET) }
    val c = remember(reduced, autoPlay) { GleamController(reduced, autoPlay) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) {
        if (autoPlay && !reduced) c.autoStart = nowSeconds()
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    fun tap() {
        val now = nowSeconds()
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        c.show(nextBeat(c.loopTime(now)), now)
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            if (c.manualBeat != 2) {
                c.show(2, nowSeconds())
                delay(1700)
            }
            c.outgoingE = null
            c.manualBeat = -1
            c.autoStart = nowSeconds()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(c) { detectTapGestures { tap() } },
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val density = LocalDensity.current.density
        val paints = remember {
            Pair(
                Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regularFace; textSize = FONT_SIZE },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = italicFace; textSize = FONT_SIZE },
            )
        }
        val words = remember(beats, width, height) { layoutBeats(beats, width, height, paints.first, paints.second) }
        val draw = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = FONT_SIZE } }

        Canvas(Modifier.fillMaxSize().semantics { contentDescription = beats.last().replace("*", "").replace("\n", " ") }) {
            val now = c.frame
            c.outgoing(now)?.let { (e, k) -> drawWords(words, e, k, c.reduced, draw, regularFace, italicFace, density) }
            drawWords(words, c.loopTime(now), 1.0, c.reduced, draw, regularFace, italicFace, density)
        }

        // Progress segments.
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (top + 14).dp)
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (i in 0 until 3) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .drawBehind {
                            val r = CornerRadius(size.height / 2)
                            drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = r)
                            val f = c.chromeAt(c.frame).fills[i].toFloat()
                            if (f > 0) drawRoundRect(Color.White.copy(alpha = 0.85f), size = Size(size.width * f, size.height), cornerRadius = r)
                        },
                )
            }
        }

        BasicText(
            hint,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, color = HintGrey, textAlign = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (height - max(bottomInset.toDouble(), 20.0) - 16 - 28 - 9).dp)
                .graphicsLayer { alpha = c.chromeAt(c.frame).hint.toFloat() },
        )

        Box(
            Modifier
                .offset(x = 32.dp, y = (height - max(bottomInset.toDouble(), 20.0) - 16 - 56).dp)
                .width((width - 64).dp)
                .height(56.dp)
                .graphicsLayer { alpha = c.chromeAt(c.frame).pill.toFloat() }
                .background(Color.White, RoundedCornerShape(28.dp))
                .semantics { role = Role.Button }
                .pointerInput(c) {
                    // The pill answers only once it is shown; before that a touch on it is a tap on the screen.
                    detectTapGestures { if (c.chromeAt(nowSeconds()).pill > 0.9) onAction() else tap() }
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(action, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black), maxLines = 1)
        }
    }
}

private fun DrawScope.drawWords(
    beats: List<List<Word>>,
    e: Double,
    fade: Double,
    reduced: Boolean,
    paint: Paint,
    regular: Typeface,
    italic: Typeface,
    density: Float,
) {
    val canvas = drawContext.canvas.nativeCanvas
    val blurOk = Build.VERSION.SDK_INT >= 28
    canvas.save()
    canvas.scale(density, density)
    for ((bi, words) in beats.withIndex()) {
        for (w in words) {
            val l = look(bi, w.line, w.index, w.place, e, reduced)
            val alpha = l.alpha * fade
            if (alpha < 0.004) continue
            paint.typeface = if (w.italic) italic else regular
            val y = (w.baseline + l.dy).toFloat()
            if (l.gleam > 0.01) {
                paint.color = Color.White.toArgb()
                paint.alpha = (255 * 0.6 * l.gleam * alpha).roundToInt().coerceIn(0, 255)
                paint.maskFilter = if (blurOk) BlurMaskFilter(sigmaToRadius(8.0), BlurMaskFilter.Blur.NORMAL) else null
                if (blurOk) canvas.drawText(w.text, w.x, y, paint)
            }
            val g = l.gleam.toFloat()
            paint.color = Color(
                Settled.red + (1 - Settled.red) * g,
                Settled.green + (1 - Settled.green) * g,
                Settled.blue + (1 - Settled.blue) * g,
            ).toArgb()
            paint.alpha = (255 * alpha).roundToInt().coerceIn(0, 255)
            paint.maskFilter = if (blurOk && l.blur > 0.05) BlurMaskFilter(sigmaToRadius(l.blur), BlurMaskFilter.Blur.NORMAL) else null
            canvas.drawText(w.text, w.x, y, paint)
            paint.maskFilter = null
        }
    }
    canvas.restore()
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
