package com.ngocdevv.nativecook.blocks.text.weightbreathingglyph

import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Weight Breathing Glyph: a type specimen of one giant capital S that breathes between hairline and
 black along a weight axis, with the glyph's skeleton (its points and the lines between them) drawn
 over it like a font editor.

 The outline is Inter Tight's S at its three masters (weights 100, 400 and 900), kept here as point
 lists (font units, 2048 to the em). The font has no avar table, so a weight between two masters is
 exactly the linear blend of their points, which is what the variable font itself draws. A contour is
 TrueType quadratics: two off-curve points in a row have an implied on-curve point half-way.

 Layout (dp, W × H the whole screen, on #ebeae6): a black pill "Type specimen 01" and "● Variable"
 at top + 12; "U+0053" and "wght 100–900" at top + 56; the S scaled to fit W − 48 at its widest
 (black) advance and centred in the room between top + 72 and the panel, with hairline guides at
 its cap height and baseline; below, the readout (the weight, 56 sp monospaced, the style name and
 "wght"), the axis (ticks every 100, a black knob at the weight) and a caption.

 Drawing: the outline at 900 as a ghost (0.75 dp, ink 28%), the current outline filled #0b0b0b,
 then the skeleton in white with a difference blend (API 29+; mid grey below), so it reads dark on
 the paper and light on the letter: the control polygon (0.75 dp), on-curve points as dots (r 3.2)
 and off-curve points as rings (r 2.4).

 Timeline of the self-playing loop (LOOP = 8 s, two breaths of 4 s):
   weight = 100 + 800 · (½ − ½ cos(2π t / 4 s)), so 100 (the resting state, hairline) at 0, 4 and 8 s
   and 900 (black) at 2 and 6 s, easing in and out at both ends like a sine.

 Touch: dragging sideways sets the weight directly (the axis's width, W − 48, spans 100 to 900),
 with a haptic tick each time it crosses a hundred. 2 s after the finger lifts the weight eases back
 to 100 (0.8 s, ease-in-out) and the loop starts over. Reduced motion: the S stands still at 400
 (Regular) and the loop is off; dragging still sets the weight, and it stays where it is left.
*/

/** The self-playing loop, in seconds. */
const val LOOP = 8f

private const val BREATH = 4f
private const val IDLE = 2f
private const val BACK = 0.8f
private const val PAPER = 0xFFEBEAE6.toInt()
private const val INK = 0xFF0B0B0B.toInt()
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private fun seconds(from: Long, to: Long) = (to - from) / 1e9f

private fun ink(alpha: Float) = android.graphics.Color.argb((alpha * 255f).roundToInt(), 0x0B, 0x0B, 0x0B)

private fun styleName(weight: Float): String {
    val names = listOf("Thin", "ExtraLight", "Light", "Regular", "Medium", "SemiBold", "Bold", "ExtraBold", "Black")
    return names[((weight / 100f).roundToInt() - 1).coerceIn(0, 8)]
}

/**
 * A type specimen of one giant S breathing between hairline and black, with its points and control
 * lines drawn over it; drag sideways to set the weight.
 */
@Composable
fun WeightBreathingGlyph(
    modifier: Modifier = Modifier,
    badge: String = "Type specimen 01",
    tag: String = "Variable",
    caption: String = "Drag sideways to set the weight",
    /** Breathes the weight by itself; the pattern repeats every [LOOP] seconds. */
    autoPlay: Boolean = false,
) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()

    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var loopStart by remember { mutableLongStateOf(System.nanoTime()) }
    // The weight a finger set (-1: none), and the ease back to rest after it lifts.
    var touched by remember { mutableStateOf(-1f) }
    var returning by remember { mutableStateOf(false) }
    var backFrom by remember { mutableStateOf(100f) }
    var backStart by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var lifts by remember { mutableIntStateOf(0) }

    fun weightAt(at: Long): Float {
        if (touched >= 0f) return touched
        if (returning) return backFrom + (100f - backFrom) * EaseInOut.transform(min(seconds(backStart, at) / BACK, 1f))
        if (reduced) return 400f
        if (!autoPlay) return 100f
        val t = seconds(loopStart, at).mod(LOOP)
        return 100f + 800f * (0.5f - 0.5f * cos(2f * PI.toFloat() * t / BREATH))
    }

    val paused = reduced || (!autoPlay && !returning)
    WeightBreathingGlyphSystemBars()
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (isActive) withFrameNanos { now = System.nanoTime() }
    }
    LaunchedEffect(Unit) { loopStart = System.nanoTime() }
    // 2 s after the finger lifts the weight eases back to rest and the loop starts over.
    LaunchedEffect(lifts) {
        if (lifts == 0 || reduced || touched < 0f) return@LaunchedEffect
        delay((IDLE * 1000).toLong())
        if (dragging) return@LaunchedEffect
        backFrom = touched
        backStart = System.nanoTime()
        now = backStart
        touched = -1f
        returning = true
        delay((BACK * 1000).toLong())
        if (dragging) return@LaunchedEffect
        loopStart = System.nanoTime()
        now = loopStart
        returning = false
    }

    val painter = remember { WeightBreathingGlyphPainter() }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(PAPER)),
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = insets.calculateBottomPadding().value
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "The letter S"
                    stateDescription = "Weight ${weightAt(now).roundToInt()}"
                }
                .pointerInput(reduced, autoPlay) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val base = weightAt(System.nanoTime())
                        dragging = true
                        returning = false
                        val span = (size.width / density - 48f).coerceAtLeast(1f)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val dx = (change.position.x - down.position.x) / density
                            val next = (base + dx * 800f / span).coerceIn(100f, 900f)
                            val previous = if (touched >= 0f) touched else base
                            if ((previous / 100f).toInt() != (next / 100f).toInt()) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            touched = next
                            now = System.nanoTime()
                        }
                        dragging = false
                        lifts++
                    }
                },
        ) {
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                painter.draw(c, size.width / density, size.height / density, top, bottom, weightAt(now), badge, tag, caption)
                c.restore()
            }
        }
    }
}

/** The screen is light, so the bar icons are dark while the block is open and put back when it closes. */
@Composable
private fun WeightBreathingGlyphSystemBars() {
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

private class WeightBreathingGlyphPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mono: Typeface = Typeface.MONOSPACE
    private val monoMedium: Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.MONOSPACE, 500, false) else Typeface.MONOSPACE

    private fun setText(size: Float, color: Int, medium: Boolean = false, tracking: Float = 0f) {
        text.textSize = size
        text.color = color
        text.typeface = if (medium) monoMedium else mono
        text.letterSpacing = tracking / size
    }

    /** The baseline that centres the current text's line box on [y], as SwiftUI's centre anchors do. */
    private fun centred(y: Float): Float {
        val fm = text.fontMetrics
        return y - (fm.ascent + fm.descent) / 2f
    }

    private fun small(string: String, x: Float, baseline: Float, align: Paint.Align, c: android.graphics.Canvas) {
        setText(10f, ink(0.45f))
        text.textAlign = align
        c.drawText(string, x, baseline, text)
        text.textAlign = Paint.Align.LEFT
    }

    fun draw(c: android.graphics.Canvas, w: Float, h: Float, top: Float, bottom: Float, weight: Float, badge: String, tag: String, caption: String) {
        fill.color = PAPER
        c.drawRect(0f, 0f, w, h, fill)

        // Header.
        setText(11f, android.graphics.Color.WHITE, medium = true, tracking = 1f)
        val label = badge.uppercase()
        val pill = RectF(24f, top + 12f, 24f + text.measureText(label) + 24f, top + 38f)
        fill.color = INK
        c.drawRoundRect(pill, 13f, 13f, fill)
        text.textAlign = Paint.Align.CENTER
        c.drawText(label, pill.centerX(), centred(pill.centerY()), text)
        setText(11f, INK, medium = true, tracking = 1f)
        text.textAlign = Paint.Align.RIGHT
        val tagLabel = tag.uppercase()
        c.drawText(tagLabel, w - 24f, centred(pill.centerY()), text)
        text.textAlign = Paint.Align.LEFT
        c.drawCircle(w - 24f - text.measureText(tagLabel) - 11f, pill.centerY(), 3f, fill)
        setText(10f, ink(0.45f))
        small("U+0053", 24f, centred(top + 56f), Paint.Align.LEFT, c)
        small("wght 100–900", w - 24f, centred(top + 56f), Paint.Align.RIGHT, c)

        // Panel, laid out from the bottom up.
        val floor = h - bottom - 16f
        val trackY = floor - 44f
        val readout = floor - 76f
        val panelTop = readout - 56f

        // The glyph: fitted to the black advance and to the room above the panel, centred on its advance.
        val roomTop = top + 72f
        val roomBottom = panelTop - 16f
        val k = min((w - 48f) / BLACK_ADVANCE, (roomBottom - roomTop) / 1600f)
        val baseline = (roomTop + roomBottom) / 2f + (1510f - 18f) * k / 2f
        val (points, advance) = outlineAt(weight)
        val x0 = w / 2f - advance * k / 2f
        val ghostX0 = w / 2f - BLACK_ADVANCE * k / 2f

        // Guides at cap height and baseline.
        setText(10f, ink(0.45f))
        val descent = text.fontMetrics.descent
        for ((y, guide) in listOf(baseline - 1490f * k to "Cap height 1490", baseline to "Baseline 0")) {
            fill.color = ink(0.18f)
            c.drawRect(0f, y - 0.25f, w, y + 0.25f, fill)
            small(guide, 24f, y - 4f - descent, Paint.Align.LEFT, c)
        }

        val ghost = outlineAt(900f).first
        stroke.color = ink(0.28f)
        stroke.strokeWidth = 0.75f
        c.drawPath(contour(ghost, ghostX0, baseline, k), stroke)
        fill.color = INK
        c.drawPath(contour(points, x0, baseline, k), fill)

        // The skeleton, in white with a difference blend.
        val difference = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val skeleton = if (difference) android.graphics.Color.WHITE else android.graphics.Color.rgb(0x88, 0x88, 0x88)
        if (difference) {
            stroke.blendMode = BlendMode.DIFFERENCE
            fill.blendMode = BlendMode.DIFFERENCE
        }
        stroke.color = skeleton
        fill.color = skeleton
        val n = ON.size
        val polygon = Path()
        for (i in 0 until n) {
            val x = x0 + points[2 * i] * k
            val y = baseline - points[2 * i + 1] * k
            if (i == 0) polygon.moveTo(x, y) else polygon.lineTo(x, y)
        }
        polygon.close()
        c.drawPath(polygon, stroke)
        stroke.strokeWidth = 1f
        for (i in 0 until n) {
            val x = x0 + points[2 * i] * k
            val y = baseline - points[2 * i + 1] * k
            if (ON[i]) c.drawCircle(x, y, 3.2f, fill) else c.drawCircle(x, y, 2.4f, stroke)
        }
        if (difference) {
            stroke.blendMode = null
            fill.blendMode = null
        }

        // Readout.
        setText(56f, INK, medium = true)
        c.drawText("${weight.roundToInt()}", 24f, readout, text)
        setText(17f, INK, medium = true)
        text.textAlign = Paint.Align.RIGHT
        c.drawText(styleName(weight), w - 24f, readout, text)
        text.textAlign = Paint.Align.LEFT
        small("wght", w - 24f, readout - 24f, Paint.Align.RIGHT, c)

        // Axis: a hairline, a tick every 100 (taller at 100, 400 and 900) and the knob.
        val span = w - 48f
        fill.color = ink(0.3f)
        c.drawRect(24f, trackY - 0.5f, 24f + span, trackY + 0.5f, fill)
        for (step in 0..8) {
            val x = 24f + span * step / 8f
            val major = step == 0 || step == 3 || step == 8
            val tall = if (major) 10f else 6f
            fill.color = ink(if (major) 0.6f else 0.3f)
            c.drawRect(x - 0.5f, trackY - tall, x + 0.5f, trackY, fill)
        }
        setText(10f, ink(0.45f))
        val labelY = centred(floor - 26f)
        small("100", 24f, labelY, Paint.Align.LEFT, c)
        small("400", 24f + span * 300f / 800f, labelY, Paint.Align.CENTER, c)
        small("900", w - 24f, labelY, Paint.Align.RIGHT, c)
        val knobX = 24f + span * (weight - 100f) / 800f
        fill.color = INK
        c.drawRoundRect(RectF(knobX - 2f, trackY - 16f, knobX + 2f, trackY + 8f), 2f, 2f, fill)
        setText(11f, ink(0.5f))
        c.drawText(caption, 24f, floor - text.fontMetrics.descent, text)
    }

    /** The closed quadratic contour through [points] (font units), placed on screen. */
    private fun contour(points: FloatArray, x0: Float, baseline: Float, k: Float): Path {
        val path = Path()
        val n = ON.size
        fun x(i: Int) = x0 + points[2 * i] * k
        fun y(i: Int) = baseline - points[2 * i + 1] * k
        val first = ON.indexOfFirst { it }
        if (first < 0) return path
        path.moveTo(x(first), y(first))
        var i = 1
        while (i <= n) {
            val index = (first + i) % n
            if (ON[index]) {
                path.lineTo(x(index), y(index))
                i += 1
            } else {
                val next = (first + i + 1) % n
                if (ON[next]) {
                    path.quadTo(x(index), y(index), x(next), y(next))
                    i += 2
                } else {
                    path.quadTo(x(index), y(index), (x(index) + x(next)) / 2f, (y(index) + y(next)) / 2f)
                    i += 1
                }
            }
        }
        path.close()
        return path
    }
}

private const val BLACK_ADVANCE = 1316f
private val ADVANCES = floatArrayOf(1254f, 1210f, 1316f)

/** Inter Tight's S at weights 100, 400 and 900 (font units, y up), as x, y pairs. */
private val THIN = floatArrayOf(
    622f, -24f, 486f, -24f, 283f, 71f, 165f, 240f, 158f, 350f, 204f, 350f, 211f, 253f, 317f, 106f, 500f, 24f, 622f, 24f, 751f, 24f, 943f, 113f, 1050f, 273f, 1050f, 378f, 1050f, 458f, 980f, 579f, 831f, 673f, 710f, 710f, 542f, 762f, 366f, 817f, 192f, 999f, 192f, 1128f, 192f, 1238f, 307f, 1412f, 507f, 1512f, 634f, 1512f, 762f, 1512f, 955f, 1410f, 1069f, 1233f, 1076f, 1118f, 1030f, 1118f, 1019f, 1270f, 807f, 1466f, 634f, 1466f, 521f, 1466f, 341f, 1379f, 238f, 1226f, 238f, 1128f, 238f, 1053f, 306f, 932f, 446f, 841f, 552f, 808f, 722f, 756f, 818f, 726f, 959f, 652f, 1051f, 560f, 1096f, 447f, 1096f, 378f, 1096f, 262f, 977f, 80f, 764f, -24f,
)
private val REGULAR = floatArrayOf(
    604f, -26f, 444f, -26f, 210f, 77f, 77f, 262f, 68f, 384f, 254f, 384f, 262f, 300f, 361f, 190f, 515f, 136f, 604f, 136f, 707f, 136f, 872f, 204f, 968f, 326f, 968f, 408f, 968f, 482f, 884f, 574f, 749f, 632f, 670f, 654f, 488f, 706f, 313f, 756f, 110f, 943f, 110f, 1094f, 110f, 1219f, 245f, 1406f, 476f, 1510f, 618f, 1510f, 762f, 1510f, 986f, 1407f, 1118f, 1230f, 1122f, 1118f, 946f, 1118f, 933f, 1228f, 748f, 1350f, 612f, 1350f, 514f, 1350f, 366f, 1286f, 284f, 1174f, 284f, 1102f, 284f, 1023f, 382f, 926f, 517f, 872f, 572f, 856f, 722f, 814f, 780f, 798f, 923f, 744f, 1056f, 652f, 1142f, 509f, 1142f, 404f, 1142f, 284f, 1016f, 89f, 776f, -26f,
)
private val BLACK = floatArrayOf(
    678f, -18f, 484f, -18f, 195f, 97f, 35f, 337f, 34f, 524f, 418f, 524f, 422f, 456f, 483f, 362f, 594f, 314f, 672f, 314f, 737f, 314f, 825f, 346f, 870f, 403f, 870f, 440f, 870f, 474f, 827f, 525f, 729f, 567f, 646f, 584f, 496f, 616f, 299f, 658f, 72f, 861f, 72f, 1036f, 72f, 1178f, 226f, 1391f, 497f, 1510f, 674f, 1510f, 855f, 1510f, 1118f, 1389f, 1261f, 1170f, 1262f, 1024f, 874f, 1024f, 869f, 1097f, 772f, 1178f, 674f, 1178f, 612f, 1178f, 532f, 1149f, 494f, 1097f, 494f, 1064f, 494f, 1029f, 535f, 976f, 624f, 937f, 694f, 922f, 816f, 896f, 939f, 870f, 1116f, 784f, 1229f, 669f, 1282f, 525f, 1282f, 440f, 1282f, 293f, 1137f, 88f, 867f, -18f,
)

/** On-curve (true) or off-curve (false), point by point; the S is one contour. */
private val ON = intArrayOf(1, 0, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0).map { it == 1 }

/** The outline at [weight] (100 … 900): its points and its advance. */
private fun outlineAt(weight: Float): Pair<FloatArray, Float> {
    val w = weight.coerceIn(100f, 900f)
    val low = w <= 400f
    val a = if (low) THIN else REGULAR
    val b = if (low) REGULAR else BLACK
    val t = if (low) (w - 100f) / 300f else (w - 400f) / 500f
    val advance = if (low) ADVANCES[0] + (ADVANCES[1] - ADVANCES[0]) * t else ADVANCES[1] + (ADVANCES[2] - ADVANCES[1]) * t
    return FloatArray(a.size) { a[it] + (b[it] - a[it]) * t } to advance
}
