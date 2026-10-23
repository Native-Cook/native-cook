package com.ngocdevv.nativecook.blocks.inputs.rainbowoutlinesearch

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 350f
private const val HEIGHT = 170f

// The three shapes the outline takes, all centred in the box.
private const val PILL_WIDTH = 330f
private const val PILL_HEIGHT = 64f
private const val CARD_WIDTH = 330f
private const val CARD_HEIGHT = 116f
private const val CARD_RADIUS = 24f
private const val LINE_WIDTH = 290f
private const val LINE_HEIGHT = 2.5f

// How far the middle of the line rises as it fades.
private const val LINE_BOW = 9f
private const val GLOW_WIDTH = 8f
private const val GLOW_BLUR = 6f
private const val SHADOW_BLUR = 14f
private const val SHADOW_DROP = 8f
private const val STROKE = 2.5f

// Centre of the search icon from the pill's left end.
private const val ICON_X = 44f
private const val TEXT_X = 20f

// Centre of the tools from the card's bottom edge.
private const val TOOL_Y = 34f
private const val FONT = 16f
private val Ink = Color(0xFF1F1F1F)
private val Caret = Color(0xFF4285F4)

// Positions on the outline run 0…1 from the right end of the top edge, leftwards, down the left
// side, back along the bottom and up the right side.
private const val RIBBON_FROM = 0.27f
private const val RIBBON_TO = 0.86f
private const val RIBBON_PIECES = 72

// How much of the colour wheel the ribbon shows at once; the wheel turns once per loop.
private const val RAMP_SPAN = 0.9f
private const val STREAK_FROM = 0.62f
private const val STREAK_LENGTH = 0.24f
private const val STREAK_PIECES = 30

// Whole laps of the streak per loop, so the loop closes.
private const val STREAK_LAPS = 4f

// autoPlay (seconds). Keys land KEY apart on average, stretched by RHYTHM in turn.
private const val OPEN_AT = 0.8f

// From the tap on the pill to the first key.
private const val TYPE_AFTER = 0.75f
private const val KEY = 0.052f
private val RHYTHM = floatArrayOf(1f, 0.72f, 1.3f, 0.85f, 1.12f, 0.64f, 1.42f, 0.95f)

// From the last key to the tap on the arrow.
private const val SEND_AFTER = 0.85f
private const val LOOP = 7.6f

// After the field is left empty, the card closes and the script waits this long.
private const val RESUME_AFTER = 3f

private const val BLINK = 0.53f
private const val PRESS = 0.12f
private const val OPEN_TIME = 0.45f
private const val FOLD_TIME = 0.36f
private const val GONE_TIME = 0.55f

// From the line fading out to the outline starting to draw.
private const val DRAW_AFTER = 0.15f
private const val DRAW_TIME = 0.95f

// Opens past its size and settles back.
private val Snap = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)
private val Sweep = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)

// How far a button shrinks under a finger, and the arrow under the script's.
private const val PRESSED = 0.92f
private const val SCRIPT_PRESSED = 0.88f

private val Green = rgba(52, 168, 83, 1f)
private val Yellow = rgba(251, 188, 4, 1f)
private val Red = rgba(234, 67, 53, 1f)
private val Violet = rgba(161, 66, 244, 1f)
private val Blue = rgba(66, 133, 244, 1f)
private val White = rgba(255, 255, 255, 1f)

// The card's resting border.
private val Pale = rgba(201, 215, 245, 1f)
private val Wheel = listOf(Green, Yellow, Red, Violet, Blue)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private val FieldStyle = TextStyle(
    color = Ink,
    fontSize = FONT.sp,
    lineHeight = 24.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private val PlaceholderStyle = FieldStyle.copy(color = Color(0xFF9AA0A6))
private val GhostStyle = FieldStyle.copy(color = Color.Transparent)
private val Selection = TextSelectionColors(handleColor = Caret, backgroundColor = Caret.copy(alpha = 0.4f))

private fun ms(seconds: Float) = (seconds * 1000f).roundToInt()

/** Waits until [at] seconds after [origin], a reading of `System.nanoTime()`. */
private suspend fun until(origin: Long, at: Float) {
    val wait = origin + (at * 1e9).toLong() - System.nanoTime()
    if (wait > 0) delay(wait / 1_000_000)
}

private fun mix(from: Color, to: Color, t: Float) = Color(
    from.red + (to.red - from.red) * t,
    from.green + (to.green - from.green) * t,
    from.blue + (to.blue - from.blue) * t,
)

/** The colour wheel at [x] turns: green, yellow, red, violet, blue and round to green again. */
private fun ramp(x: Float): Color {
    val at = (x - floor(x)) * Wheel.size
    val index = min(Wheel.size - 1, at.toInt())
    return mix(Wheel[index], Wheel[(index + 1) % Wheel.size], at - index)
}

private fun clamp01(t: Float) = min(1f, max(0f, t))

private fun smooth(from: Float, to: Float, x: Float): Float {
    val t = clamp01((x - from) / (to - from))
    return t * t * (3f - 2f * t)
}

private class OutlineBox(val w: Float, val h: Float, val r: Float)

/** The outline [o] of the way from pill to card and [f] of the way from there to the flat line. */
private fun outlineBox(o: Float, f: Float): OutlineBox {
    val w = lerp(lerp(PILL_WIDTH, CARD_WIDTH, o), LINE_WIDTH, f)
    val h = lerp(lerp(PILL_HEIGHT, CARD_HEIGHT, o), LINE_HEIGHT, f)
    val r = lerp(lerp(PILL_HEIGHT / 2f, CARD_RADIUS, o), LINE_HEIGHT / 2f, f)
    return OutlineBox(w, h, max(0.01f, min(r, min(h / 2f, w / 2f))))
}

/** The point [u] (in turns) of the way round a rounded rectangle centred on [centre]. */
private fun outlinePoint(u: Float, box: OutlineBox, centre: Offset): Offset {
    val r = box.r
    val across = max(0f, box.w - 2f * r)
    val down = max(0f, box.h - 2f * r)
    val corner = (Math.PI / 2).toFloat() * r
    val left = centre.x - box.w / 2f
    val right = centre.x + box.w / 2f
    val top = centre.y - box.h / 2f
    val bottom = centre.y + box.h / 2f
    var s = (u - floor(u)) * (2f * across + 2f * down + 4f * corner)
    if (s < across) return Offset(right - r - s, top)
    s -= across
    if (s < corner) return Offset(left + r - r * sin(s / r), top + r - r * cos(s / r))
    s -= corner
    if (s < down) return Offset(left, top + r + s)
    s -= down
    if (s < corner) return Offset(left + r - r * cos(s / r), bottom - r + r * sin(s / r))
    s -= corner
    if (s < across) return Offset(left + r + s, bottom)
    s -= across
    if (s < corner) return Offset(right - r + r * sin(s / r), bottom - r + r * cos(s / r))
    s -= corner
    if (s < down) return Offset(right, bottom - r - s)
    s -= down
    return Offset(right - r + r * cos(s / r), top + r - r * sin(s / r))
}

/**
 * Strokes the outline between two positions in short pieces, each in the colour (opacity included)
 * [tone] gives for its place (0…1) along the stretch.
 */
private inline fun DrawScope.ribbon(
    box: OutlineBox,
    centre: Offset,
    from: Float,
    to: Float,
    pieces: Int,
    tone: (Float) -> Color,
) {
    val step = (to - from) / pieces
    for (index in 0 until pieces) {
        val tint = tone((index + 0.5f) / pieces)
        if (tint.alpha <= 0.004f) continue
        // A hair longer than its share, so no seam shows between two pieces.
        drawLine(
            color = tint,
            start = outlinePoint(from + step * index, box, centre),
            end = outlinePoint(from + step * (index + 1.08f), box, centre),
            strokeWidth = STROKE,
        )
    }
}

// Icons, as outlines in a 20 dp box (the magnifying glass and its sparkle in 28 dp).
private val GlassIcon = outline(
    "M19.47 13.65 C19.2 16.73 17.07 19.33 14.1 20.2 C11.13 21.06 7.93 20.02 6.05 17.56 C4.17 15.11 3.98 11.75 5.59 9.11 " +
        "C7.19 6.46 10.25 5.07 13.3 5.61 M17.6 18.6 L23.4 24.4",
)
private val SparkleIcon = outline(
    "M21 1.5 C21.6 4.57 23.43 6.4 26.5 7 C23.43 7.6 21.6 9.43 21 12.5 C20.4 9.43 18.57 7.6 15.5 7 C18.57 6.4 20.4 4.57 21 1.5 Z",
)
private val CloseIcon = outline("M5.5 5.5 L14.5 14.5 M14.5 5.5 L5.5 14.5")
private val ImageIcon = outline(
    "M5 3.5 L15 3.5 C15.83 3.5 16.5 4.17 16.5 5 L16.5 15 C16.5 15.83 15.83 16.5 15 16.5 L5 16.5 C4.17 16.5 3.5 15.83 3.5 15 " +
        "L3.5 5 C3.5 4.17 4.17 3.5 5 3.5 Z M3.5 13.5 L7 10 L10 13 L12.5 10.5 L16.5 14.5",
)
private val LensIcon = outline(
    "M3.5 7 L3.5 5.5 C3.5 4.4 4.4 3.5 5.5 3.5 L7 3.5 M13 3.5 L14.5 3.5 C15.6 3.5 16.5 4.4 16.5 5.5 L16.5 7 " +
        "M16.5 13 L16.5 14.5 C16.5 15.6 15.6 16.5 14.5 16.5 L13 16.5 M7 16.5 L5.5 16.5 C4.4 16.5 3.5 15.6 3.5 14.5 L3.5 13 " +
        "M12.6 10 C12.6 11.44 11.44 12.6 10 12.6 C8.56 12.6 7.4 11.44 7.4 10 C7.4 8.56 8.56 7.4 10 7.4 C11.44 7.4 12.6 8.56 12.6 10 Z",
)
private val MicIcon = outline(
    "M10 2.5 C11.38 2.5 12.5 3.62 12.5 5 L12.5 9.5 C12.5 10.88 11.38 12 10 12 C8.62 12 7.5 10.88 7.5 9.5 L7.5 5 " +
        "C7.5 3.62 8.62 2.5 10 2.5 Z M5 9.5 C5 12.26 7.24 14.5 10 14.5 C12.76 14.5 15 12.26 15 9.5 M10 14.5 L10 17.5",
)
private val ArrowIcon = outline("M10 15.5 L10 4.5 M5.5 9 L10 4.5 L14.5 9")

private fun outline(data: String) = PathParser().parsePathString(data).toPath()

/** A stroke with a soft edge, for the glow of the line. Drawn in the current (dp) units. */
private class SoftStroke(color: Color, width: Float, sigma: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        this.color = color.toArgb()
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    fun draw(canvas: android.graphics.Canvas, path: Path, alpha: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        paint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawPath(path.asAndroidPath(), paint)
    }
}

/** The numbers the outline is drawn from, read per frame without recomposing. */
private class OutlineMotion {
    /** 0 pill, 1 card. */
    val open = Animatable(0f)

    /** 0 card, 1 flat line. */
    val fold = Animatable(0f)

    /** How far the line has bowed and faded. */
    val gone = Animatable(0f)

    /** How much of the pill's outline is drawn. */
    val draw = Animatable(1f)

    /** When the clock of the colours started, as a frame time. */
    var started = System.nanoTime()

    /** Seconds on that clock. Written once a frame: reading it is what redraws the outline. */
    val time = mutableFloatStateOf(0f)

    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.1f), sigma = SHADOW_BLUR)
    val glow = SoftStroke(Blue, GLOW_WIDTH, sigma = GLOW_BLUR)
    val line = Path()

    /** Under reduced motion ([still]) the shape jumps half way through a fade instead of morphing. */
    fun shape(still: Boolean) = if (still) (if (open.value < 0.5f) 0f else 1f) else open.value

    /** That fade: out until the shape jumps, then in again. */
    fun veil(still: Boolean) = if (still) abs(2f * open.value - 1f) else 1f

    /** Opacity of what the card holds. */
    fun content(still: Boolean) =
        clamp01((shape(still) - 0.55f) / 0.45f) * clamp01(1f - fold.value * 4f) * veil(still)

    /** Opacity of the search icon. */
    fun icon(still: Boolean) =
        clamp01(1f - shape(still) * 3f) * clamp01((draw.value - 0.6f) / 0.4f) * veil(still)
}

/** The outline and everything made of light: shadow and fill of the card, the folded line, the two ribbons. */
private fun DrawScope.paint(
    motion: OutlineMotion,
    time: Float,
    box: OutlineBox,
    o: Float,
    f: Float,
    gone: Float,
    drawn: Float,
    veil: Float,
) {
    val centre = Offset(WIDTH / 2f, HEIGHT / 2f)
    val left = centre.x - box.w / 2f
    val top = centre.y - box.h / 2f
    val corner = Offset(left, top)
    val extent = Size(box.w, box.h)
    val radius = CornerRadius(box.r)
    val card = clamp01(o) * veil
    val folded = clamp01(f)

    if (card > 0f && gone <= 0f) {
        drawIntoCanvas {
            motion.shadow.draw(it.nativeCanvas, left, top + SHADOW_DROP, box.w, box.h, box.r, alpha = card * (1f - folded))
        }
        // The white card takes the colour of the line it folds into.
        drawRoundRect(mix(White, Blue, folded).copy(alpha = card), corner, extent, radius)
        drawRoundRect(Pale.copy(alpha = card * (1f - folded)), corner, extent, radius, style = Stroke(1.5f))
    }

    // The folded card is a line of light: it glows, bows upwards and fades.
    if (folded > 0f) {
        val line = motion.line
        line.rewind()
        line.moveTo(left, centre.y)
        line.quadraticTo(centre.x, centre.y - LINE_BOW * 2f * gone, left + box.w, centre.y)
        drawIntoCanvas { motion.glow.draw(it.nativeCanvas, line, alpha = 0.6f * folded * (1f - gone) * veil) }
        if (gone > 0f) {
            drawPath(
                line,
                Blue.copy(alpha = clamp01((1f - gone) * veil)),
                style = Stroke(LINE_HEIGHT, cap = StrokeCap.Round),
            )
        }
    }

    // The pill: a ribbon over the top left, round the left cap and along the bottom, fading to the right.
    val closed = (1f - clamp01(o * 2.5f)) * veil
    if (closed > 0f && drawn > 0f) {
        // It is drawn from its right end; `front` is how far the drawing has come.
        val front = 1f - drawn * 1.1f
        val lead = min(1f, (1f - drawn) * 4f)
        ribbon(box, centre, RIBBON_FROM, RIBBON_TO, RIBBON_PIECES) { q ->
            val rest = smooth(0f, 0.14f, q) * (1f - smooth(0.45f, 1f, q))
            // While it is drawn, a bright head runs ahead of the faded ribbon it leaves behind.
            val head = lead * (1f - smooth(0f, 0.3f, q - front))
            ramp(q * RAMP_SPAN - time / LOOP).copy(alpha = clamp01(max(rest, head) * clamp01((q - front) / 0.1f) * closed))
        }
    }

    // The card: a green, yellow and red streak travelling round the pale border.
    val streak = card * (1f - folded)
    if (streak > 0f) {
        val from = STREAK_FROM - STREAK_LAPS * time / LOOP
        ribbon(box, centre, from, from + STREAK_LENGTH, STREAK_PIECES) { q ->
            val tone = if (q < 0.5f) mix(Green, Yellow, q * 2f) else mix(Yellow, Red, q * 2f - 1f)
            tone.copy(alpha = smooth(0f, 0.3f, q) * (1f - smooth(0.7f, 1f, q)) * streak)
        }
    }
}

/**
 * A search pill drawn as a partial rainbow outline, with colours that slide along it. A tap snaps it
 * open into an input card whose border carries a coloured streak round and round. On send the card
 * folds flat into a line of light that bows and fades, and the outline is drawn again from the right.
 *
 * @param value Controlled text. Leave it null and the component keeps its own.
 * @param autoPlay Opens the pill, types the script, sends it and starts over.
 * @param script The question autoPlay types.
 * @param sendColor Fill of the send button.
 * @param onSend Called with the question when the arrow or the keyboard's search key is pressed.
 */
@Composable
fun RainbowOutlineSearch(
    value: String? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Ask anything",
    script: String = "Best hikes near Lisbon in May",
    sendColor: Color = Color(0xFF2F5FD0),
    onValueChange: ((String) -> Unit)? = null,
    onSend: ((String) -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onImage: (() -> Unit)? = null,
    onLens: (() -> Unit)? = null,
    onMic: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val motion = remember { OutlineMotion() }

    // The script and the way back to the pill outlive a recomposition, so they read these.
    val latestValue by rememberUpdatedState(value)
    val latestScript by rememberUpdatedState(script)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnSend by rememberUpdatedState(onSend)
    val latestOnOpen by rememberUpdatedState(onOpen)
    val latestOnClose by rememberUpdatedState(onClose)

    var inner by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(autoPlay && value == null) }
    var caretOn by remember { mutableStateOf(true) }
    // The arrow is held down by the script.
    var pressed by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }
    // The card is folding or the pill is being drawn: taps wait.
    var busy by remember { mutableStateOf(false) }
    // Counts closings, to start and cancel what follows one.
    var run by remember { mutableIntStateOf(0) }

    val text = value ?: inner

    fun current() = latestValue ?: inner

    fun edit(new: String) {
        if (new == current()) return
        if (latestValue == null) inner = new
        latestOnValueChange?.invoke(new)
    }

    fun openUp(byHand: Boolean) {
        if (opened || busy) return
        opened = true
        scope.launch { motion.open.animateTo(1f, tween(ms(OPEN_TIME), easing = if (still) EaseOutStrong else Snap)) }
        latestOnOpen?.invoke()
        if (byHand) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            focusRequester.requestFocus()
        }
    }

    /** Folds the card away, after sending what it holds or because it was dismissed. */
    fun close(sending: Boolean, byHand: Boolean) {
        if (byHand) {
            // A touch on the card's buttons takes over from the script as well.
            playing = false
            pressed = false
        }
        if (!opened || busy || (sending && current().isBlank())) return
        if (sending) latestOnSend?.invoke(current().trim()) else latestOnClose?.invoke()
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (focused) focusManager.clearFocus()
        pressed = false
        busy = true
        run += 1
    }

    LaunchedEffect(still) {
        // Under reduced motion the clock stands still, and the colours with it.
        if (still) return@LaunchedEffect
        while (true) {
            withFrameNanos { motion.time.floatValue = ((it - motion.started) / 1e9).toFloat() }
        }
    }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        // Every step is timed from the start of its loop, so the period does not drift.
        var origin = System.nanoTime()
        while (true) {
            // The colours and the streak run on the loop's clock, so every loop shows the same frames.
            motion.started = origin
            until(origin, OPEN_AT)
            // A touch that took over is only cancelled here a frame later: no step may slip in before that.
            if (!playing) return@LaunchedEffect
            openUp(byHand = false)
            var at = OPEN_AT + TYPE_AFTER
            val question = latestScript
            for (index in question.indices) {
                until(origin, at)
                if (!playing) return@LaunchedEffect
                edit(question.substring(0, index + 1))
                at += KEY * RHYTHM[index % RHYTHM.size]
            }
            at += SEND_AFTER
            until(origin, at - PRESS)
            if (!playing) return@LaunchedEffect
            pressed = true
            until(origin, at)
            if (!playing) return@LaunchedEffect
            close(sending = true, byHand = false)
            val back = FOLD_TIME + GONE_TIME + DRAW_AFTER + DRAW_TIME
            origin += (max(LOOP, at + back + OPEN_AT) * 1e9).toLong()
            until(origin, 0f)
        }
    }

    // The way back to the pill: fold flat, bow and fade, then draw the outline again.
    LaunchedEffect(run) {
        if (!busy) return@LaunchedEffect
        val origin = System.nanoTime()
        if (still) {
            launch { motion.open.animateTo(0f, tween(ms(OPEN_TIME), easing = EaseOutStrong)) }
            until(origin, OPEN_TIME)
            edit("")
        } else {
            launch { motion.fold.animateTo(1f, tween(ms(FOLD_TIME), easing = EaseInOut)) }
            until(origin, FOLD_TIME)
            edit("")
            launch { motion.gone.animateTo(1f, tween(ms(GONE_TIME), easing = EaseOutStrong)) }
            until(origin, FOLD_TIME + GONE_TIME + DRAW_AFTER)
            // Nothing is on screen now: back to the pill, not yet drawn.
            motion.open.snapTo(0f)
            motion.fold.snapTo(0f)
            motion.gone.snapTo(0f)
            motion.draw.snapTo(0f)
            launch { motion.draw.animateTo(1f, tween(ms(DRAW_TIME), easing = Sweep)) }
            until(origin, FOLD_TIME + GONE_TIME + DRAW_AFTER + DRAW_TIME)
        }
        opened = false
        busy = false
    }

    LaunchedEffect(focused, opened, busy, playing) {
        // Left empty, the card closes; closed, the script resumes after a while.
        if (!autoPlay || value != null || playing || focused || busy) return@LaunchedEffect
        delay(ms(RESUME_AFTER).toLong())
        if (focused || current().isNotEmpty()) return@LaunchedEffect
        if (opened) close(sending = false, byHand = false) else playing = true
    }

    LaunchedEffect(text, playing, opened) {
        // The caret stays solid while keys arrive and blinks once they stop.
        caretOn = true
        if (!playing || !opened) return@LaunchedEffect
        while (true) {
            delay(ms(BLINK).toLong())
            caretOn = !caretOn
        }
    }

    val blink by animateFloatAsState(if (caretOn) 1f else 0f, tween(80, easing = EaseOut), label = "caret")
    val hold by animateFloatAsState(
        targetValue = if (pressed) SCRIPT_PRESSED else 1f,
        animationSpec = tween(ms(PRESS), easing = EaseOut),
        label = "send",
    )

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, _, _ ->
                val shape = motion.shape(still)
                val fold = motion.fold.value
                paint(
                    motion,
                    time = motion.time.floatValue,
                    box = outlineBox(shape, fold),
                    o = shape,
                    f = fold,
                    gone = motion.gone.value,
                    drawn = motion.draw.value,
                    veil = motion.veil(still),
                )
            }
        }

        SearchIcon(
            Modifier
                .offset(((WIDTH - PILL_WIDTH) / 2f + ICON_X - 14f).dp, (HEIGHT / 2f - 14f).dp)
                .graphicsLayer {
                    val icon = motion.icon(still)
                    scaleX = 0.8f + 0.2f * icon
                    scaleY = 0.8f + 0.2f * icon
                    alpha = icon
                },
        )

        // What the open card holds: the field, a close button, and the tools along the bottom.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
                .graphicsLayer {
                    val scale = 0.94f + 0.06f * clamp01(motion.shape(still))
                    scaleX = scale
                    scaleY = scale
                    alpha = motion.content(still)
                }
                .then(if (opened) Modifier else Modifier.clearAndSetSemantics {}),
        ) {
            Box(
                Modifier.offset(TEXT_X.dp, 16.dp).size((CARD_WIDTH - TEXT_X - 56f).dp, 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    BasicText(placeholder, style = PlaceholderStyle, overflow = TextOverflow.Ellipsis, maxLines = 1)
                }
                // autoPlay's caret hangs off an invisible copy of the text; a focused field shows its own.
                Row(
                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true).clearAndSetSemantics {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Non-breaking spaces, so that a space at the end moves the caret too.
                    BasicText(text.replace(' ', ' '), style = GhostStyle, softWrap = false, maxLines = 1)
                    Box(
                        Modifier
                            // Before the placeholder's first letter, not on it.
                            .offset(x = if (text.isEmpty()) (-2).dp else 1.dp)
                            .size(2.dp, 20.dp)
                            .graphicsLayer { alpha = if (playing && opened && !focused) blink else 0f }
                            .background(Caret, RoundedCornerShape(1.dp)),
                    )
                }
                CompositionLocalProvider(LocalTextSelectionColors provides Selection) {
                    BasicTextField(
                        value = text,
                        onValueChange = { edit(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                focused = it.isFocused
                                // A touch takes the field over from the script.
                                if (it.isFocused && playing) {
                                    playing = false
                                    edit("")
                                }
                            }
                            .semantics { contentDescription = placeholder },
                        textStyle = FieldStyle,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                close(sending = true, byHand = true)
                                // The search key puts the keyboard away even when there is nothing to send.
                                focusManager.clearFocus()
                            },
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(Caret),
                    )
                }
            }

            OutlineTool(CloseIcon, Color(0xFF5F6368), "Close", Modifier.centredAt(CARD_WIDTH - 28f, 28f, 36f)) {
                close(sending = false, byHand = true)
            }
            OutlineTool(ImageIcon, Ink, "Add an image", Modifier.centredAt(32f, CARD_HEIGHT - TOOL_Y, 36f)) {
                onImage?.invoke()
            }
            OutlineTool(LensIcon, Ink, "Search with the camera", Modifier.centredAt(70f, CARD_HEIGHT - TOOL_Y, 36f)) {
                onLens?.invoke()
            }
            OutlineTool(MicIcon, Ink, "Dictate", Modifier.centredAt(CARD_WIDTH - 88f, CARD_HEIGHT - TOOL_Y, 36f)) {
                onMic?.invoke()
            }
            OutlinePressable(
                label = "Send",
                onClick = { close(sending = true, byHand = true) },
                modifier = Modifier.centredAt(CARD_WIDTH - 36f, CARD_HEIGHT - TOOL_Y, 40f),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = hold
                            scaleY = hold
                        }
                        .background(sendColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlineIcon(ArrowIcon, Color.White, width = 2f)
                }
            }
        }

        // The card takes no touches while it is closed or folding: this lies over it and keeps them.
        if (!opened || busy) {
            Box(Modifier.align(Alignment.Center).size(CARD_WIDTH.dp, CARD_HEIGHT.dp).pointerInput(Unit) {})
        }

        if (!opened && !busy) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(PILL_WIDTH.dp, PILL_HEIGHT.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        // A touch takes over from the script.
                        playing = false
                        openUp(byHand = true)
                    }
                    .semantics { contentDescription = "Search" },
            )
        }
    }
}

/** A square of [side] dp with its centre at ([x], [y]). */
private fun Modifier.centredAt(x: Float, y: Float, side: Float) =
    offset((x - side / 2f).dp, (y - side / 2f).dp).size(side.dp)

/** A magnifying glass open at the top right, where a four-point sparkle sits. */
@Composable
private fun SearchIcon(modifier: Modifier) {
    Canvas(modifier.size(28.dp)) {
        surface(0.dp) { _, _, _, _ ->
            drawPath(GlassIcon, Ink, style = Stroke(2.6f, cap = StrokeCap.Round))
            drawPath(SparkleIcon, Ink)
        }
    }
}

/** One of the icons drawn in a 20 dp box, as a line [width] dp thick with round ends. */
@Composable
private fun OutlineIcon(icon: Path, tint: Color, width: Float) {
    Canvas(Modifier.size(20.dp)) {
        surface(0.dp) { _, _, _, _ ->
            drawPath(icon, tint, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun OutlineTool(icon: Path, tint: Color, label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinePressable(label, onClick, modifier) { OutlineIcon(icon, tint, width = 1.7f) }
}

/** A button of the card: scales down while pressed. */
@Composable
private fun OutlinePressable(label: String, onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
