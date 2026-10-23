package com.ngocdevv.nativecook.blocks.text.bluredgetypein

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// A writing app's welcome screen on near-black. A four-line headline in tall condensed capitals is
// typed in letter by letter: the last four characters behind the moving front are blurred, faint
// and spread out to the right, and sharpen and close up as the front moves on. An orange caret
// rides the front (and pulses while the headline holds) and a thin bar under the headline fills
// with it. After a hold the headline is taken back the same way from its end. Dragging sideways
// scrubs the front. A port of the SwiftUI block, number for number; each letter is drawn on its
// own with a blur mask filter (API 28+; below that the letters only fade).
//
// The front `f` counts characters over the whole headline (spaces and line breaks included, so a
// line break is a short pause). A character `k` with d = f - k <= 0 is not drawn; with
// 0 < d < 4, u = d / 4: opacity u, blur 0.16 em × (1 - u)², shifted right 0.3 em × (1 - u)²;
// from d >= 4 it is sharp and in place.
//
// autoPlay timeline (LOOP = 8 s), with N characters and full = N + 4:
//   0.0-0.5   empty (the rest state)
//   0.5-3.4   types in: f 0 -> full, linear (about 14 characters a second, as in the reference)
//   3.4-6.4   holds, sharp, while the caret pulses three times (opacity 0.55 + 0.45 cos, 1 s)
//   6.4-7.6   taken back from the end: f full -> 0, linear (about 34 characters a second)
//   7.6-8.5   empty (wrapping round to 0.5), the caret pulsing once at the start of the line
// While the front moves the caret is fully on. Something always moves, so a screen recording
// never sits on a stale frame.
// The caret is shown the whole loop; it sits 0.3 em ahead of the front while letters are blurred
// and closes up to the text at either end (its lead is scaled by min(f, 1) and by the share of
// the edge still to type), so at rest it stands at the start of the line.
//
// Touch: dragging sideways moves the front 1 character per 8 dp from where it was (eased over
// 0.12 s), with a tick at each word's end. 2 s after the finger lifts the headline is taken back
// over 0.9 s and the loop starts over.

/** Seconds. */
private const val LOOP = 8f
/** Characters behind the front that are still blurred. */
private const val EDGE = 4f
private const val TYPE_START = 0.5f
private const val TYPE_END = 3.4f
private const val ERASE_START = 6.4f
private const val ERASE_END = 7.6f
/** The caret pulses every PULSE seconds while the headline holds, and fades in over CARET_FADE when a finger takes over. */
private const val PULSE = 1f
private const val CARET_FADE = 0.08f
/** Dp of drag per character. */
private const val DRAG_STEP = 8f
private const val USER_HOLD = 2f
private const val SETTLE = 0.9f
/** In ems of the headline size. */
private const val MAX_BLUR = 0.16f
private const val MAX_SHIFT = 0.3f
private const val SIZE_LIMIT = 104f

private val BACKGROUND = Color(0xFF070707)
private val INK = Color.White
private val CARET = Color(0xFFFF5B2E)

private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EASE_IN_OUT = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** The front `t` seconds into the loop, for a headline whose last front is `full`. */
private fun frontAt(t: Float, full: Float): Float {
    if (t < ERASE_START) return full * clamp01((t - TYPE_START) / (TYPE_END - TYPE_START))
    return full * (1 - clamp01((t - ERASE_START) / (ERASE_END - ERASE_START)))
}

/** The caret's opacity `t` seconds into the loop. */
private fun caretAt(t: Float): Float {
    if (t > TYPE_END && t < ERASE_START) return 0.55f + 0.45f * cos(2 * PI.toFloat() * (t - TYPE_END) / PULSE)
    if (t < TYPE_START || t > ERASE_END) {
        val r = if (t > ERASE_END) t - ERASE_END else t + LOOP - ERASE_END
        return 0.55f + 0.45f * cos(2 * PI.toFloat() * r / (LOOP - ERASE_END + TYPE_START))
    }
    return 1f
}

/** One character of the headline; x and advance in dp. An empty character is a line break. */
private class Glyph(val character: String, val x: Float, val advance: Float, val line: Int, val index: Int)

private class TypeLayout(val size: Float, val glyphs: List<Glyph>, val lineCount: Int) {
    /** Distance between baselines, and from the caps' top to the first baseline (dp). */
    val step get() = size * 0.86f
    val cap get() = size * 0.7f
    val height get() = cap + (lineCount - 1) * step
    val full get() = glyphs.size + EDGE

    /** Where the front `f` is: the line it is on and its x (dp). */
    fun caret(f: Float): Pair<Int, Float> {
        if (glyphs.isEmpty()) return 0 to 0f
        val k = floor(f).toInt().coerceIn(0, glyphs.size - 1)
        val g = glyphs[k]
        return g.line to g.x + clamp01(f - k) * g.advance
    }
}

/**
 * The lines at the size that makes the widest span `width` dp, at most [SIZE_LIMIT]. A line break
 * between lines is a character too (a pause), placed at the end of its line.
 */
private fun typeLayout(lines: List<String>, typeface: Typeface, width: Float, density: Float): TypeLayout {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = 100f
    }
    val widest = lines.maxOf { paint.measureText(it) }
    val size = min(SIZE_LIMIT, floor(100f * width / max(widest, 1f)))
    paint.textSize = size * density
    val glyphs = mutableListOf<Glyph>()
    lines.forEachIndexed { row, text ->
        val lineWidth = paint.measureText(text) / density
        for (i in text.indices) {
            val x = paint.measureText(text, 0, i) / density
            val next = if (i + 1 < text.length) paint.measureText(text, 0, i + 1) / density else lineWidth
            glyphs += Glyph(text[i].toString(), x, next - x, row, glyphs.size)
        }
        if (row < lines.size - 1) glyphs += Glyph("", lineWidth, 0f, row, glyphs.size)
    }
    return TypeLayout(size, glyphs, lines.size)
}

/** A number animating towards a target, sampled against the frame clock (nanoseconds). */
private class Tween(value: Float) {
    private var from = value
    private var to = value
    private var start = 0L
    private var duration = 0f
    private var easing: Easing = EASE_OUT

    fun value(at: Long): Float {
        if (duration <= 0f) return to
        val t = clamp01((at - start) / 1e9f / duration)
        return from + (to - from) * easing.transform(t)
    }

    fun retarget(target: Float, at: Long, duration: Float, easing: Easing) {
        from = value(at)
        to = target
        start = at
        this.duration = duration
        this.easing = easing
    }

    fun set(value: Float) {
        from = value
        to = value
        duration = 0f
    }
}

/**
 * @param autoPlay Types the headline in and takes it back by itself, every [LOOP] seconds.
 */
@Composable
fun BlurEdgeTypeIn(
    modifier: Modifier = Modifier,
    lines: List<String> = listOf("ONE WORD", "AT A TIME.", "THEN THE", "NEXT ONE."),
    label: String = "Daily pages",
    day: String = "Day 12",
    message: String = "A quiet editor that shows only the line you are on. Everything else can wait.",
    action: String = "Start writing",
    hint: String = "Drag sideways to scrub",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val auto by rememberUpdatedState(autoPlay)

    val typeface = remember(context) {
        runCatching { Typeface.createFromAsset(context.assets, "bluredgetypein/bluredgetypein_BebasNeue-Regular.ttf") }
            .getOrDefault(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    }

    // The frame clock, in nanoseconds; read only in draw blocks.
    val clock = remember { mutableLongStateOf(System.nanoTime()) }
    var origin by remember { mutableLongStateOf(System.nanoTime()) }
    // True from a touch until the headline is back at rest and the loop has started over.
    var manual by remember { mutableStateOf(false) }
    val front = remember { Tween(0f) }
    val caretShown = remember { Tween(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    LaunchedEffect(reducedMotion, manual) {
        if (reducedMotion && !manual) return@LaunchedEffect
        while (true) withFrameNanos { clock.longValue = it.coerceAtLeast(clock.longValue) }
    }

    // 2 s after the last touch the headline is taken back, and the loop starts over from empty.
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        delay((USER_HOLD * 1000).toLong())
        if (dragging) return@LaunchedEffect
        front.retarget(0f, clock.longValue, SETTLE, EASE_IN_OUT)
        delay((SETTLE * 1000).toLong())
        if (dragging) return@LaunchedEffect
        origin = clock.longValue
        manual = false
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        val layout = remember(lines, typeface, maxWidth) { typeLayout(lines, typeface, maxWidth.value - 48f, density.density) }

        fun frontNow(at: Long): Float {
            if (manual) return front.value(at)
            if (!auto || reduced) return layout.full
            return frontAt((((at - origin) / 1e9f) % LOOP).coerceAtLeast(0f), layout.full)
        }

        fun caretOpacity(at: Long): Float {
            if (manual) return caretShown.value(at)
            if (!auto || reduced) return 0f
            return caretAt((((at - origin) / 1e9f) % LOOP).coerceAtLeast(0f))
        }

        /** How many words are complete with the front at `f`. */
        fun words(f: Float): Int = layout.glyphs.count {
            (it.character == " " || it.character.isEmpty() || it.index == layout.glyphs.size - 1) && it.index < f - EDGE + 1
        }

        val paint = remember(typeface, layout) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = layout.size * density.density
                color = INK.toArgb()
            }
        }
        // Blur filters by radius, in quarter pixels.
        val filters = remember { HashMap<Int, BlurMaskFilter>() }

        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(reducedMotion, layout) {
                    if (reducedMotion) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val now = clock.longValue
                        val from = frontNow(now)
                        caretShown.set(caretOpacity(now))
                        caretShown.retarget(1f, now, CARET_FADE, LinearEasing)
                        manual = true
                        dragging = true
                        front.set(from)
                        var lastWord = words(from)
                        touches++
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val dx = (change.position.x - down.position.x) / density.density
                            val target = (from + dx / DRAG_STEP).coerceIn(0f, layout.full)
                            front.retarget(target, clock.longValue, 0.12f, EASE_OUT)
                            val w = words(target)
                            if (w != lastWord) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            lastWord = w
                        }
                        dragging = false
                        touches++
                    }
                },
        ) {
            Row(Modifier.padding(top = insets.calculateTopPadding() + 12.dp).padding(horizontal = 24.dp).fillMaxWidth()) {
                val style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = INK.copy(alpha = 0.55f))
                Text(label, style = style)
                Spacer(Modifier.weight(1f))
                Text(day, style = style)
            }

            Spacer(Modifier.weight(1f))

            // The headline: each letter drawn on its own (it may spill past the box while blurred).
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(layout.height.dp)
                    .semantics { contentDescription = lines.joinToString(" ") }
                    .drawBehind {
                        val at = clock.longValue
                        val f = frontNow(at)
                        val d = density.density
                        val size = layout.size
                        drawIntoCanvas { canvas ->
                            for (g in layout.glyphs) {
                                if (g.character.isEmpty() || g.character == " ") continue
                                val behind = f - g.index
                                if (behind <= 0f) continue
                                val u = clamp01(behind / EDGE)
                                val k = (1 - u) * (1 - u)
                                val blur = MAX_BLUR * size * k
                                val x = 24f + g.x + MAX_SHIFT * size * k
                                val baseline = layout.cap + g.line * layout.step
                                paint.alpha = (u * 255f).roundToInt().coerceIn(0, 255)
                                paint.maskFilter = if (blur > 0.05f && Build.VERSION.SDK_INT >= 28) {
                                    val r = sigmaToRadius(blur * d)
                                    filters.getOrPut((r * 4).roundToInt()) { BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL) }
                                } else {
                                    null
                                }
                                canvas.nativeCanvas.drawText(g.character, x * d, baseline * d, paint)
                            }
                        }
                        // The caret, just ahead of the newest letter.
                        val caret = caretOpacity(at)
                        if (caret > 0.001f) {
                            val (line, cx) = layout.caret(f)
                            val lead = clamp01(f) * clamp01((layout.full - f) / EDGE)
                            val x = 24f + cx + MAX_SHIFT * size * lead + 4f * clamp01(f)
                            val top = line * layout.step
                            drawRoundRect(
                                CARET.copy(alpha = caret),
                                topLeft = Offset(x * d, (top - 0.04f * size) * d),
                                size = Size(4f * d, (layout.cap + 0.08f * size) * d),
                                cornerRadius = CornerRadius(2f * d),
                            )
                        }
                    },
            )

            // The bar under the headline, filled to the front.
            Box(
                Modifier
                    .padding(top = 28.dp)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .drawBehind {
                        val p = min(1f, frontNow(clock.longValue) / layout.full)
                        val r = CornerRadius(size.height / 2)
                        drawRoundRect(INK.copy(alpha = 0.14f), cornerRadius = r)
                        drawRoundRect(INK.copy(alpha = 0.8f), size = Size(size.width * p, size.height), cornerRadius = r)
                    },
            )

            Text(
                message,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 24.dp),
                style = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, color = INK.copy(alpha = 0.62f)),
            )

            // Twice the room below as above, so the headline sits high.
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(INK, CircleShape)
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = BACKGROUND))
            }

            Text(
                hint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(bottom = insets.calculateBottomPadding() + 12.dp),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = INK.copy(alpha = 0.4f), textAlign = TextAlign.Center),
            )
        }
    }
}
