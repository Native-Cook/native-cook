package com.ngocdevv.nativecook.blocks.text.scrambledecodeheadline

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Scramble Decode Headline — a release-notes hero whose headline keeps decoding itself. A port of
 the SwiftUI block (ScrambleDecodeHeadline.swift); the timeline is the same:

 Letters are numbered left to right, line by line, spaces left out (k = 0…26 for the default copy
 "Always / shipping / something / new."). A window of letters scrambles: letter j of the window
 turns into violet glyphs 0.014·j s after the window starts, flickers to a new seeded glyph every
 0.05 s, and settles back into its white letter `hold + settle·j` s after the start, so the
 decode sweeps left to right. A settled letter cools from violet to white over 0.15 s. The violet
 glow behind the headline follows the share of letters that are scrambled. A scrambled glyph
 wider than 1.5 times its letter's slot is scaled down towards that width, to 72 % at most.

 autoPlay timeline (LOOP = 8 s):
   0.00         clean headline (also the first look).
   0.50         "Always" (k 0–5)            hold 0.32 s, settle 0.055 s per letter.
   1.60         "ething" (k 17–22)          same.
   2.70         "ng" + "new." (k 21–26)     same, across a line break.
   3.60, 3.75   "ipping" (k 8–13), then "som" (k 14–16): two windows overlapping.
   5.00         the whole headline (k 0–26) hold 0.35 s, settle 0.045 s per letter → clean at 6.52.
   6.52 … 8.00  clean, identical to t = 0.

 Touch: a tap scrambles the whole headline at once and decodes it (hold 0.35 s, settle 0.045 s).
 Dragging over the headline scrambles the letters within 36 dp of the finger; each settles 0.35 s
 after the finger leaves it. 2 s after the last touch the loop starts again from its clean frame.
 Reduced motion: no loop and no scrambling; the headline stays clean.
 */

data class ScrambleDecodeHeadlineWindow(
    /** Seconds into the loop. */
    val start: Double,
    /** First and last letter, counted without spaces. */
    val first: Int,
    val last: Int,
    val hold: Double = 0.32,
    val settle: Double = 0.055,
)

private const val LOOP = 8.0
private const val FLICKER = 0.05
private const val COOL = 0.15
private const val ONSET = 0.014
private const val SCRUB_RADIUS = 36.0
private const val SCRUB_LINGER = 0.35
private const val MAX_SIZE = 76f
private const val TRACKING = -0.03f // em
private const val LEADING = 1.0f // line height in em
private const val FONT_ASSET = "scrambledecodeheadline/scrambledecodeheadline_SpaceGrotesk-Medium.ttf"
private const val CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%&!?*+=<>/"

private val Background = Color(0xFF101114)
private val Ink = Color(0xFFF2F2F2)
private val Violet = Color(0xFF7C5CFF)

private val DefaultWindows = listOf(
    ScrambleDecodeHeadlineWindow(0.5, 0, 5),
    ScrambleDecodeHeadlineWindow(1.6, 17, 22),
    ScrambleDecodeHeadlineWindow(2.7, 21, 26),
    ScrambleDecodeHeadlineWindow(3.6, 8, 13),
    ScrambleDecodeHeadlineWindow(3.75, 14, 16),
    ScrambleDecodeHeadlineWindow(5.0, 0, 26, hold = 0.35, settle = 0.045),
)

private fun nowSeconds() = System.nanoTime() / 1e9

/** Whether a letter shows a scrambled glyph, the glyph step, and how long ago it settled (for the cool-down). */
private data class ScrambleLetter(val scrambled: Boolean, val step: Long, val settledAgo: Double)

private class ScrambleDecodeHeadlineController(val windows: List<ScrambleDecodeHeadlineWindow>, val reduced: Boolean) {
    var autoStart by mutableStateOf<Double?>(null)
    /** A tap's whole-headline decode. */
    var tapAt by mutableStateOf<Double?>(null)
    /** When each letter was last under the finger. */
    val touched = mutableStateMapOf<Int, Double>()
    var finger by mutableStateOf<Offset?>(null)
    var frame by mutableDoubleStateOf(nowSeconds())
    var resume: Job? = null

    fun letter(k: Int, slot: ScrambleSlot, at: Double): ScrambleLetter {
        if (reduced) return ScrambleLetter(false, 0, Double.POSITIVE_INFINITY)
        val start = autoStart
        if (start != null && at >= start) {
            val e = (at - start) % LOOP
            return state(k, windows, e, (e / FLICKER).toLong())
        }
        val step = (at / FLICKER).toLong()
        var best = ScrambleLetter(false, step, Double.POSITIVE_INFINITY)
        val tap = tapAt
        if (tap != null) {
            best = state(k, listOf(ScrambleDecodeHeadlineWindow(0.0, 0, Int.MAX_VALUE, hold = 0.35, settle = 0.045)), at - tap, step)
            if (best.scrambled) return best
        }
        val f = finger
        if (f != null && hypot(slot.cx - f.x, slot.cy - f.y) < SCRUB_RADIUS) return ScrambleLetter(true, step, 0.0)
        val t = touched[k]
        if (t != null) {
            val ago = at - t - SCRUB_LINGER
            if (ago < 0) return ScrambleLetter(true, step, 0.0)
            best = best.copy(settledAgo = min(best.settledAgo, ago))
        }
        return best
    }

    companion object {
        /** Letter k under a set of windows, [e] seconds into their timeline. */
        fun state(k: Int, windows: List<ScrambleDecodeHeadlineWindow>, e: Double, step: Long): ScrambleLetter {
            var settledAgo = Double.POSITIVE_INFINITY
            for (w in windows) {
                if (k < w.first || k > w.last) continue
                val j = (k - w.first).toDouble()
                val on = w.start + j * ONSET
                val off = w.start + w.hold + j * w.settle
                if (e >= on && e < off) return ScrambleLetter(true, step, 0.0)
                if (e >= off) settledAgo = min(settledAgo, e - off)
            }
            return ScrambleLetter(false, step, settledAgo)
        }
    }
}

/** A letter's slot: its character and centre, in dp. */
private data class ScrambleSlot(val char: Char, val cx: Double, val cy: Double, val width: Double)

/** Where every letter of the headline sits: the font size that fits the longest line, and each letter's slot. */
private class ScrambleLayout(lines: List<String>, typeface: Typeface, width: Float, height: Float) {
    val fontSize: Float
    val slots: List<ScrambleSlot>
    val top: Float
    val bottom: Float
    val centerY: Float

    init {
        // Advances of each character at 1 dp, measured one by one (no kerning, as in every stack).
        val paint = Paint().apply { this.typeface = typeface; textSize = 1000f }
        val advances = lines.map { line -> line.map { paint.measureText(it.toString()) / 1000f } }
        val unitWidths = advances.map { a -> a.sum() + TRACKING * max(0, a.size - 1) }
        val size = min(MAX_SIZE, (width - 48) / unitWidths.max())
        val lineHeight = size * LEADING
        val blockHeight = lineHeight * lines.size
        centerY = height * 0.43f
        top = centerY - blockHeight / 2
        bottom = top + blockHeight
        val list = mutableListOf<ScrambleSlot>()
        lines.forEachIndexed { li, line ->
            var x = 24f
            val y = top + lineHeight * (li + 0.5f)
            line.forEachIndexed { ci, ch ->
                val w = advances[li][ci] * size
                if (ch != ' ') list += ScrambleSlot(ch, (x + w / 2).toDouble(), y.toDouble(), w.toDouble())
                x += w + TRACKING * size
            }
        }
        fontSize = size
        slots = list
    }
}

/** A seeded number in 0..<1 (the same 32-bit integer hash in every stack). */
private fun hash(i: Int, salt: Int): Double {
    var x = i * 374_761_393 + salt * 668_265_263
    x = (x xor (x ushr 13)) * 1_274_126_177
    x = x xor (x ushr 16)
    return (x.toLong() and 0xffffffffL).toDouble() / 4_294_967_296.0
}

/** The seeded glyph letter k shows at a flicker step. */
private fun glyph(k: Int, step: Long): Char = CHARSET[(hash(k, step.toInt()) * CHARSET.length).toInt()]

@Composable
fun ScrambleDecodeHeadline(
    modifier: Modifier = Modifier,
    lines: List<String> = listOf("Always", "shipping", "something", "new."),
    badge: String = "Release notes · v2.4",
    subtitle: String = "Small, careful improvements every Friday, to the tools your team opens every morning.",
    action: String = "Read what's new",
    windows: List<ScrambleDecodeHeadlineWindow> = DefaultWindows,
    autoPlay: Boolean = false,
    onAction: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val typeface = remember { Typeface.createFromAsset(context.assets, FONT_ASSET) }
    val family = remember { FontFamily(Font(FONT_ASSET, context.assets)) }
    val c = remember(windows, reduced) { ScrambleDecodeHeadlineController(windows, reduced) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) {
        if (autoPlay && !reduced) c.autoStart = nowSeconds()
        if (reduced) return@LaunchedEffect
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            c.tapAt = null
            c.touched.clear()
            c.autoStart = nowSeconds()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Background)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val density = LocalDensity.current.density
        val layout = remember(width, height, lines) { ScrambleLayout(lines, typeface, width, height) }
        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    heading()
                    contentDescription = lines.joinToString(" ")
                }
                .pointerInput(c) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        c.resume?.cancel()
                        c.autoStart = null
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val d = change.position - down.position
                            if (!moved && hypot(d.x, d.y) / density > 6f) {
                                moved = true
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            if (moved) {
                                val p = change.position / density
                                c.finger = p
                                val now = nowSeconds()
                                layout.slots.forEachIndexed { k, slot ->
                                    if (hypot(slot.cx - p.x, slot.cy - p.y) < SCRUB_RADIUS) c.touched[k] = now
                                }
                                change.consume()
                            }
                        }
                        if (!moved) {
                            c.tapAt = nowSeconds()
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                        c.finger = null
                        scheduleResume()
                    }
                },
        ) {
            drawHeadline(c, layout, typeface, paint, density)
        }

        // Badge above the headline.
        Row(
            Modifier
                .offset(x = 24.dp, y = (layout.top - 28 - 24).dp)
                .height(28.dp)
                .background(Ink.copy(alpha = 0.06f), CircleShape)
                .border(1.dp, Ink.copy(alpha = 0.10f), CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(6.dp).background(Violet, CircleShape))
            BasicText(badge, style = TextStyle(fontFamily = family, fontSize = 13.sp, color = Ink.copy(alpha = 0.75f)), maxLines = 1)
        }

        BasicText(
            subtitle,
            style = TextStyle(fontFamily = family, fontSize = 17.sp, lineHeight = 25.7.sp, color = Ink.copy(alpha = 0.55f)),
            modifier = Modifier
                .offset(x = 24.dp, y = (layout.bottom + 24).dp)
                .width(min(width - 48, 320f).dp),
        )

        Row(
            Modifier
                .offset(x = 24.dp, y = (height - max(bottomInset, 20f) - 16 - 56).dp)
                .width((width - 48).dp)
                .height(56.dp)
                .background(Ink, CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onAction() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BasicText(action, style = TextStyle(fontFamily = family, fontSize = 17.sp, color = Background), maxLines = 1)
            Spacer(Modifier.width(10.dp))
            // A right arrow, 15 dp, like the SF Symbol on iOS.
            Canvas(Modifier.size(15.dp)) {
                val s = size.width / 15f
                val stroke = 2f * s
                drawLine(Background, Offset(1.5f * s, 7.5f * s), Offset(13.5f * s, 7.5f * s), stroke, StrokeCap.Round)
                drawLine(Background, Offset(8.5f * s, 2.5f * s), Offset(13.5f * s, 7.5f * s), stroke, StrokeCap.Round)
                drawLine(Background, Offset(8.5f * s, 12.5f * s), Offset(13.5f * s, 7.5f * s), stroke, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.drawHeadline(
    c: ScrambleDecodeHeadlineController,
    layout: ScrambleLayout,
    typeface: Typeface,
    paint: Paint,
    density: Float,
) {
    val now = c.frame
    val states = layout.slots.mapIndexed { k, slot -> c.letter(k, slot, now) }
    val activity = states.count { it.scrambled }.toFloat() / max(1, states.size)

    // The glow behind the headline breathes with how much of it is scrambled.
    val r = size.width * 0.95f
    drawRect(
        Brush.radialGradient(
            listOf(Violet.copy(alpha = 0.10f + 0.22f * activity), Violet.copy(alpha = 0f)),
            center = Offset(size.width * 0.3f, layout.centerY * density),
            radius = r,
        ),
    )

    paint.typeface = typeface
    paint.textSize = layout.fontSize * density
    val canvas = drawContext.canvas.nativeCanvas
    // The text box's centre sits 0.346 em above the baseline (ascent 0.984, descent 0.292 em).
    val drop = 0.346f * paint.textSize
    layout.slots.forEachIndexed { k, slot ->
        val s = states[k]
        val ch: Char
        val color: Color
        if (s.scrambled) {
            ch = glyph(k, s.step)
            color = Violet
        } else {
            ch = slot.char
            val cool = min(1.0, s.settledAgo / COOL).toFloat()
            color = if (cool >= 1f) Ink else lerp(Violet, Ink, cool)
        }
        paint.color = color.toArgb()
        val x = slot.cx.toFloat() * density
        val y = slot.cy.toFloat() * density
        // A glyph much wider than its letter's slot is shrunk towards 1.5 slot widths (to 72 % at most), so it hardly runs into its neighbours.
        val fit = if (s.scrambled) max(0.72f, min(1f, (slot.width * 1.5).toFloat() * density / max(1f, paint.measureText(ch.toString())))) else 1f
        canvas.save()
        canvas.scale(fit, fit, x, y)
        canvas.drawText(ch.toString(), x, y + drop, paint)
        canvas.restore()
    }
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
