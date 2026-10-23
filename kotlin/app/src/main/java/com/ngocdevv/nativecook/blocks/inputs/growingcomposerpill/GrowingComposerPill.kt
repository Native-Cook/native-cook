package com.ngocdevv.nativecook.blocks.inputs.growingcomposerpill

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.BreakIterator
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PANEL_WIDTH = 350f
private const val PANEL_HEIGHT = 420f
private const val PANEL_RADIUS = 40f

private const val WIDTH = 320f

// Height of the single-line pill and of the status pill.
private const val PILL = 48f
private const val STATUS_WIDTH = 140f
private const val CARD_RADIUS = 22f

private const val FONT_SIZE = 16f
private const val LINE = 21f
private const val MAX_LINES = 6

private const val PAD_TOP = 13.5f
private const val PAD_X = 18f

// Where the text starts while it shares the pill with the buttons.
private const val INLINE_X = 46f
private const val INLINE_WIDTH = WIDTH - INLINE_X * 2f
private const val FULL_WIDTH = WIDTH - PAD_X * 2f

// The card opens this much before the text would touch the button on the right.
private const val WRAP_MARGIN = 8f

// Everything in the open card that is not text: the padding above it and the row of buttons below.
private const val CHROME = 67f
private const val CARET_WIDTH = 2f
private const val CARET_HEIGHT = 19f
private const val CARET_GAP = 1f

// Width kept free at the end of a line for the drawn caret.
private const val CARET_ROOM = 3f

// Distance of a button's centre from the card's side and bottom edges.
private const val BUTTON_INSET = 24f
private const val BUTTON_HIT = 44f
private const val BUTTON_EDGE = BUTTON_INSET - BUTTON_HIT / 2f
private const val SEND_SIZE = 30f
private const val SWAP_SCALE = 0.4f
private const val PRESS_SCALE = 0.86f

// The tinted shadow under the card, and the room the canvas needs around the card to hold it.
private const val SHADOW_SIGMA = 14f
private const val SHADOW_Y = 10f
private val BLEED = 56.dp

// Height, width and corner radius of the card: a soft spring with a little overshoot.
private const val GROW_STIFFNESS = 260f
private const val GROW_DAMPING = 24f

// Microphone and send button trading places.
private const val SWAP_STIFFNESS = 420f
private const val SWAP_DAMPING = 24f

// Reduced motion replaces both springs with a short ease-out.
private const val REDUCED_MS = 200

// The autoPlay loop, in seconds from its start.
private const val CARET_AT = 0.5
private const val TYPE_AT = 0.8

// The whole script is typed in this time; a short one is typed no slower than SLOWEST_KEY per letter.
private const val TYPE_SPAN = 4.5
private const val SLOWEST_KEY = 0.06
private const val PRESS_AT = 5.75
private const val SEND_AT = 5.87
private const val LOOP = 9.4

private const val FADE_MS = 220

// The status label leaves faster than it came, before the placeholder returns.
private const val STATUS_FADE_MS = 120
private const val DIP_MS = 120

// From the send to the collapse into the status pill.
private const val COLLAPSE_AFTER_MS = 260L
private const val THINK_FOR_MS = 1870L
private const val SHIMMER_DELAY = 0.25f
private const val SHIMMER_SWEEP = 0.8f

// After the field is left empty and unfocused, autoPlay waits this long.
private const val RESUME_AFTER_MS = 3000L

private val Ink = Color(0xFF111114)
private val Sent = Color(0xFFA9A9B4)
private val Placeholder = Color(0xFF9C9CA8)
private val IconInk = Color(0xFF4A4A52)
private val Caret = Color(0xFF2D6BFF)
private val SendFill = Color(0xFF0E0E10)
private val Shadow = rgba(0x5a, 0x3f, 0xc0, 0.2f)

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private fun <T> growSpec(reducedMotion: Boolean): AnimationSpec<T> =
    if (reducedMotion) tween(REDUCED_MS, easing = EaseOut) else spring(GROW_DAMPING / (2f * sqrt(GROW_STIFFNESS)), GROW_STIFFNESS)

private fun swapSpec(reducedMotion: Boolean): AnimationSpec<Float> =
    if (reducedMotion) tween(REDUCED_MS, easing = EaseOut) else spring(SWAP_DAMPING / (2f * sqrt(SWAP_STIFFNESS)), SWAP_STIFFNESS)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun composerText(color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = FONT_SIZE.sp,
    lineHeight = LINE.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val FieldStyle = composerText(Ink)
private val SentStyle = composerText(Sent)
private val PlaceholderStyle = composerText(Placeholder)
private val StatusStyle = composerText(Ink, FontWeight.Medium)

private enum class Phase { Compose, Sending, Thinking }

private class Blob(val color: Color, val x: Float, val y: Float, val radius: Float)

private val Blobs = listOf(
    Blob(Color(0xFFA9D4F7), 0.22f, 0.1f, 230f),
    Blob(Color(0xFFC9B8F5), 0.98f, 0.22f, 240f),
    Blob(Color(0xFFF5B8D8), 0.82f, 0.96f, 250f),
    Blob(Color(0xFFFAFAFF), 0.06f, 0.6f, 190f),
    Blob(Color(0xFFB3A2F0), 0.3f, 1.08f, 170f),
)

private val Jitter = doubleArrayOf(0.0, 0.45, -0.2, 0.6, 0.1, -0.3, 0.75)

/** The user-perceived characters of [text], so an emoji is typed with one key and lit as one letter. */
private fun letters(text: String): List<String> {
    val breaks = BreakIterator.getCharacterInstance()
    breaks.setText(text)
    val out = ArrayList<String>()
    var from = breaks.first()
    var to = breaks.next()
    while (to != BreakIterator.DONE) {
        out += text.substring(from, to)
        from = to
        to = breaks.next()
    }
    return out
}

/**
 * Seconds after typing starts at which each of [keys] lands: uneven like a person's typing
 * (a beat after a space, a pause after punctuation) but the same every time.
 */
private fun rhythm(keys: List<String>): DoubleArray {
    val weights = DoubleArray(keys.size) { index ->
        if (index == 0) return@DoubleArray 0.0
        val previous = keys[index - 1]
        var weight = 1 + Jitter[index % Jitter.size]
        if (previous == " ") weight += 0.6
        if (previous.length == 1 && previous[0] in ",.?!") weight += 3.2
        weight
    }
    val unit = min(TYPE_SPAN / max(weights.sum(), 1.0), SLOWEST_KEY)
    var at = 0.0
    return DoubleArray(weights.size) { index ->
        at += weights[index] * unit
        at
    }
}

/** Opacity of one status letter while the band of light, [sweep] 0…1 across the word, passes it. */
private fun shimmer(index: Int, count: Int, sweep: Float): Float {
    if (sweep < 0f || count <= 0) return 1f
    val x = (index + 0.5f) / count
    val centre = -0.25f + 1.5f * sweep
    val distance = (x - centre) / 0.2f
    return 1f - 0.72f * exp(-distance * distance)
}

/** Solid while keys are landing; after half a second without one it blinks like the system caret. */
private fun blink(idle: Float): Float {
    if (idle <= 0.5f) return 1f
    val t = (idle - 0.5f) % 1f
    return when {
        t < 0.12f -> 1f - t / 0.12f
        t < 0.5f -> 0f
        t < 0.62f -> (t - 0.5f) / 0.12f
        else -> 1f
    }
}

/** Waits for the moment [seconds] after [start] (a `System.nanoTime()`), however late the last step ran. */
private suspend fun sleepUntil(start: Long, seconds: Double) {
    val left = start + (seconds * 1e9).toLong() - System.nanoTime()
    if (left > 0) delay(left.nanoseconds)
}

/** A button coming in or leaving: it fades while it scales between [from] and its full size. */
private fun GraphicsLayerScope.swapIn(shown: Float, from: Float, dip: Float = 1f) {
    alpha = shown.coerceIn(0f, 1f)
    val scale = (from + (1f - from) * shown) * dip
    scaleX = scale
    scaleY = scale
}

/**
 * Keeps the last [MAX_LINES] lines of a longer text in view. Only the top and bottom are clipped:
 * a space typed at the end of a line, and the caret after it, may hang past the text's width.
 */
private fun Modifier.lastLines() = this
    .drawWithContent { clipRect(left = -size.width, right = size.width * 2f) { this@drawWithContent.drawContent() } }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = min(placeable.height, (MAX_LINES * LINE).dp.roundToPx())
        layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
    }

/**
 * Chat composer that starts as a white pill and grows with its text. While the text fits beside the
 * buttons it stays a pill; once it does not, the pill opens into a card whose height springs one
 * line at a time, the corners ease from a full pill to a rounded rectangle and the buttons drop
 * onto their own row. The send button replaces the microphone with the first character. Sending
 * fades the text, collapses the card into a short status pill with a shimmer, then brings the empty
 * field back.
 *
 * @param value The field's value when the caller owns it; left null, the component keeps its own.
 * @param onValueChange Called with every change of the field's text.
 * @param autoPlay Types [script] by itself, sends it and starts over. Never opens the keyboard.
 * @param script What autoPlay types.
 * @param status Shown in the collapsed pill after sending.
 * @param onSend Called with the trimmed message when the send button or the keyboard's send key is used.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrowingComposerPill(
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Ask anything",
    script: String = "Can you help me find a great housewarming gift for my friend? maybe handmade, ceramic dinnerware, in white and tan under \$100",
    status: String = "Thinking…",
    onSend: ((String) -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val measurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }

    // The timers below outlive a recomposition: they read the caller's latest props through these.
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnSend by rememberUpdatedState(onSend)
    val currentScript by rememberUpdatedState(script)
    val auto by rememberUpdatedState(autoPlay)

    var draft by remember { mutableStateOf("") }
    // What autoPlay has typed so far; the caller's value is never touched by the script.
    var typed by remember { mutableStateOf("") }
    // True while autoPlay owns the field.
    var playing by remember { mutableStateOf(autoPlay) }
    var caretOn by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.Compose) }
    var focused by remember { mutableStateOf(false) }
    // Lines the keyboard's text takes and lines the script takes, each measured from its own layout.
    var fieldLines by remember { mutableIntStateOf(1) }
    var scriptLines by remember { mutableIntStateOf(1) }
    // Where the script's text ends: the drawn caret stands just after it.
    var caretSpot by remember { mutableStateOf(Rect.Zero) }
    // The send button's dip when autoPlay presses it.
    var pressed by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }
    var sends by remember { mutableIntStateOf(0) }
    var resume by remember { mutableIntStateOf(0) }
    // When the last key landed and the frame being drawn, both in nanoseconds: the caret blinks on their difference.
    var lastKey by remember { mutableLongStateOf(0L) }
    var caretClock by remember { mutableLongStateOf(0L) }
    // The band of light's place across the status, 0…1, or -1 while there is none.
    var sweep by remember { mutableFloatStateOf(-1f) }

    fun write(next: String) {
        if (currentValue == null) draft = next
        currentOnValueChange?.invoke(next)
    }

    fun reset() {
        typed = ""
        caretOn = false
        pressed = false
        phase = Phase.Compose
        sends += 1
    }

    /** A touch stops the script and leaves an empty field behind. */
    fun takeOver() {
        if (!playing) return
        playing = false
        reset()
        run += 1
    }

    fun send() {
        val message = (if (playing) typed else currentValue ?: draft).trim()
        if (phase != Phase.Compose || message.isEmpty()) return
        if (!playing) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            currentOnSend?.invoke(message)
        }
        phase = Phase.Sending
        sends += 1
    }

    LaunchedEffect(autoPlay) {
        playing = autoPlay
        reset()
        run += 1
    }

    // The autoPlay loop. Every step waits for its own moment after the loop began, so the period stays LOOP.
    LaunchedEffect(run) {
        if (!playing) return@LaunchedEffect
        var start = System.nanoTime()
        while (true) {
            val text = currentScript
            val keys = letters(text)
            val times = rhythm(keys)
            sleepUntil(start, CARET_AT)
            lastKey = System.nanoTime()
            caretOn = true
            var end = 0
            for (index in keys.indices) {
                sleepUntil(start, TYPE_AT + times[index])
                end += keys[index].length
                typed = text.substring(0, end)
                lastKey = System.nanoTime()
            }
            sleepUntil(start, PRESS_AT)
            pressed = true
            sleepUntil(start, SEND_AT)
            pressed = false
            send()
            sleepUntil(start, LOOP)
            start += (LOOP * 1e9).toLong()
        }
    }

    // The text has started to fade: clear it, show the status for a while, then return to the empty field.
    LaunchedEffect(sends) {
        if (phase != Phase.Sending) return@LaunchedEffect
        delay(COLLAPSE_AFTER_MS)
        if (playing) typed = "" else write("")
        caretOn = false
        phase = Phase.Thinking
        delay(THINK_FOR_MS)
        phase = Phase.Compose
    }

    // autoPlay comes back once the field has been left empty and unfocused for a while.
    LaunchedEffect(resume) {
        if (!auto || playing || focused) return@LaunchedEffect
        delay(RESUME_AFTER_MS)
        if (focused || (currentValue ?: draft).isNotEmpty() || phase != Phase.Compose) return@LaunchedEffect
        playing = true
        run += 1
    }

    // Android keeps a field focused after its keyboard is dismissed. Here leaving the keyboard is
    // leaving the field, which is what lets autoPlay come back.
    val imeVisible = WindowInsets.isImeVisible
    var imeSeen by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeSeen = true
        } else if (imeSeen) {
            imeSeen = false
            if (focused) focusManager.clearFocus()
        }
    }

    LaunchedEffect(caretOn) {
        if (!caretOn) return@LaunchedEffect
        while (true) withFrameNanos { caretClock = it }
    }

    val shown = if (playing) typed else value ?: draft
    val hasText = shown.isNotEmpty()
    val thinking = phase == Phase.Thinking

    LaunchedEffect(thinking, reducedMotion) {
        // Leaving the status, the band stays where it was while the label fades.
        if (!thinking) return@LaunchedEffect
        sweep = -1f
        if (reducedMotion) return@LaunchedEffect
        val since = System.nanoTime()
        while (true) {
            withFrameNanos { frame ->
                val elapsed = (frame - since) / 1e9f - SHIMMER_DELAY
                sweep = if (elapsed > 0f) (elapsed / SHIMMER_SWEEP) % 1f else -1f
            }
        }
    }

    // The text no longer fits beside the buttons: it moves to the top and the buttons to their own row.
    val naturalWidth = remember(shown, measurer) { measurer.measure(shown, FieldStyle, softWrap = false).size.width }
    val expanded = !thinking &&
        (shown.contains('\n') || naturalWidth > with(density) { (INLINE_WIDTH - WRAP_MARGIN).dp.toPx() })
    val lines = min(if (playing) scriptLines else fieldLines, MAX_LINES)
    val textWidth = if (expanded) FULL_WIDTH else INLINE_WIDTH

    val growDp = remember(reducedMotion) { growSpec<Dp>(reducedMotion) }
    val growFloat = remember(reducedMotion) { growSpec<Float>(reducedMotion) }
    val swap = remember(reducedMotion) { swapSpec(reducedMotion) }
    val swapFrom = if (reducedMotion) 1f else SWAP_SCALE

    // Read where they are used, in layout and drawing, so a spring does not recompose the composer.
    val cardWidth = animateDpAsState(if (thinking) STATUS_WIDTH.dp else WIDTH.dp, growDp, label = "width")
    val cardHeight = animateDpAsState(if (expanded) (lines * LINE + CHROME).dp else PILL.dp, growDp, label = "height")
    val radius = animateDpAsState(if (expanded) CARD_RADIUS.dp else (PILL / 2f).dp, growDp, label = "radius")
    val textX = animateDpAsState(if (expanded) PAD_X.dp else INLINE_X.dp, growDp, label = "textX")
    val plusAlpha = animateFloatAsState(if (thinking) 0f else 1f, growFloat, label = "plus")
    val editorAlpha = animateFloatAsState(
        targetValue = if (phase == Phase.Compose) 1f else 0f,
        // Coming back from the status, the field waits for the status to clear.
        animationSpec = tween(FADE_MS, delayMillis = if (phase == Phase.Compose) STATUS_FADE_MS else 0, easing = EaseOut),
        label = "editor",
    )
    val statusAlpha = animateFloatAsState(
        targetValue = if (thinking) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (thinking) FADE_MS else STATUS_FADE_MS,
            delayMillis = if (thinking) STATUS_FADE_MS else 0,
            easing = EaseOut,
        ),
        label = "status",
    )
    val dip = animateFloatAsState(if (pressed) PRESS_SCALE else 1f, tween(DIP_MS, easing = EaseOut), label = "dip")

    val micShown = remember { Animatable(if (hasText) 0f else 1f) }
    val sendShown = remember { Animatable(if (hasText) 1f else 0f) }
    var hadText by remember { mutableStateOf(hasText) }
    LaunchedEffect(hasText, thinking, reducedMotion) {
        // The two trade places on their own spring when text appears or goes; when only the status
        // comes or leaves, the microphone moves with the card.
        val spec = if (hasText != hadText) swap else growFloat
        hadText = hasText
        launch { micShown.animateTo(if (hasText || thinking) 0f else 1f, spec) }
        launch { sendShown.animateTo(if (hasText && !thinking) 1f else 0f, spec) }
    }

    val shadow = remember { SoftRoundRect(Shadow, sigma = SHADOW_SIGMA) }
    val statusLetters = remember(status) { letters(status) }

    Box(modifier.size(PANEL_WIDTH.dp, PANEL_HEIGHT.dp), contentAlignment = Alignment.Center) {
        Backdrop(Modifier.matchParentSize())

        Box(
            Modifier.layout { measurable, _ ->
                val width = cardWidth.value.roundToPx()
                val height = cardHeight.value.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            },
        ) {
            Box(Modifier.matchParentSize().bleed(BLEED)) {
                Canvas(Modifier.fillMaxSize()) {
                    surface(BLEED) { x, y, w, h ->
                        val r = radius.value.value
                        drawIntoCanvas { shadow.draw(it.nativeCanvas, x, y + SHADOW_Y, w, h, r) }
                        drawRoundRect(Color.White, Offset(x, y), Size(w, h), CornerRadius(r))
                    }
                }
            }

            Box(
                Modifier.matchParentSize().graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(radius.value)
                },
            ) {
                // The field keeps its own size whatever room the card has at this moment of its
                // spring: the text lands in its final lines at once and only its place eases over.
                Box(
                    Modifier
                        .offset { IntOffset(textX.value.roundToPx(), PAD_TOP.dp.roundToPx()) }
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .width(textWidth.dp)
                        .graphicsLayer { alpha = editorAlpha.value },
                ) {
                    BasicText(
                        text = placeholder,
                        style = PlaceholderStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(if (hasText || caretOn) 0f else 1f),
                    )

                    BasicTextField(
                        value = value ?: draft,
                        onValueChange = { write(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused == focused) return@onFocusChanged
                                focused = state.isFocused
                                if (focused) takeOver()
                                resume += 1
                            }
                            .alpha(if (playing) 0f else 1f),
                        textStyle = if (phase == Phase.Compose) FieldStyle else SentStyle,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        maxLines = MAX_LINES,
                        onTextLayout = { fieldLines = it.lineCount },
                        cursorBrush = SolidColor(Caret),
                    )

                    if (playing) {
                        // The script's text, with the caret autoPlay draws after the last letter,
                        // since the field itself is not focused.
                        BasicText(
                            text = typed,
                            style = if (phase == Phase.Compose) FieldStyle else SentStyle,
                            onTextLayout = {
                                scriptLines = it.lineCount
                                caretSpot = it.getCursorRect(it.layoutInput.text.length)
                            },
                            modifier = Modifier
                                .lastLines()
                                .drawWithContent {
                                    drawContent()
                                    val caret = if (caretOn) blink((caretClock - lastKey) / 1e9f) else 0f
                                    if (caret > 0f) {
                                        val height = CARET_HEIGHT.dp.toPx()
                                        drawRoundRect(
                                            color = Caret,
                                            topLeft = Offset(caretSpot.left + CARET_GAP.dp.toPx(), caretSpot.center.y - height / 2f),
                                            size = Size(CARET_WIDTH.dp.toPx(), height),
                                            cornerRadius = CornerRadius(1.dp.toPx()),
                                            alpha = caret,
                                        )
                                    }
                                }
                                .width((textWidth - CARET_ROOM).dp),
                        )
                    }
                }

                ComposerButton(
                    label = "Attach",
                    enabled = true,
                    onClick = { onAttach?.invoke() },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(BUTTON_EDGE.dp, -BUTTON_EDGE.dp)
                        .graphicsLayer { alpha = plusAlpha.value.coerceIn(0f, 1f) },
                ) {
                    Box(Modifier.size(16.dp, 1.7.dp).background(IconInk, CircleShape))
                    Box(Modifier.size(1.7.dp, 16.dp).background(IconInk, CircleShape))
                }

                ComposerButton(
                    label = "Dictate",
                    enabled = !hasText,
                    onClick = { onVoice?.invoke() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(-BUTTON_EDGE.dp, -BUTTON_EDGE.dp)
                        .graphicsLayer { swapIn(micShown.value, swapFrom) },
                ) {
                    MicIcon()
                }

                ComposerButton(
                    label = "Send",
                    enabled = hasText && phase == Phase.Compose,
                    onClick = { send() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(-BUTTON_EDGE.dp, -BUTTON_EDGE.dp)
                        .graphicsLayer { swapIn(sendShown.value, swapFrom, dip.value) },
                ) {
                    SendIcon()
                }

                // The status, one letter at a time so a band of light can sweep across it.
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .wrapContentSize(unbounded = true)
                        .graphicsLayer { alpha = statusAlpha.value }
                        .clearAndSetSemantics { if (thinking) contentDescription = status },
                ) {
                    statusLetters.forEachIndexed { index, letter ->
                        BasicText(
                            text = letter,
                            style = StatusStyle,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.graphicsLayer { alpha = shimmer(index, statusLetters.size, sweep) },
                        )
                    }
                }
            }

            // While autoPlay runs, a touch anywhere on the composer hands the field to the keyboard.
            if (playing) {
                Box(
                    Modifier.matchParentSize().pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                takeOver()
                                focusRequester.requestFocus()
                                keyboard?.show()
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * One of the composer's round buttons in its 44 dp touch area: it dips while pressed. A button that
 * is not [enabled] takes no touches at all, so the one under it can.
 */
@Composable
private fun ComposerButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    val touch = if (enabled) {
        Modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(BUTTON_HIT.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(touch),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Outline microphone in a 20 × 20 box. */
@Composable
private fun MicIcon() {
    val path = remember {
        Path().apply {
            addRoundRect(RoundRect(Rect(7.25f, 2.5f, 12.75f, 12f), CornerRadius(2.75f)))
            arcTo(Rect(Offset(10f, 9.5f), 5.5f), startAngleDegrees = 180f, sweepAngleDegrees = -180f, forceMoveTo = true)
            moveTo(10f, 15f)
            lineTo(10f, 17.75f)
        }
    }
    Canvas(Modifier.size(20.dp)) {
        surface(0.dp) { _, _, _, _ ->
            drawPath(path, IconInk, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Up arrow in the 30 × 30 send button. */
@Composable
private fun SendIcon() {
    val path = remember {
        Path().apply {
            moveTo(15f, 21f)
            lineTo(15f, 9.5f)
            moveTo(10f, 14.25f)
            lineTo(15f, 9.25f)
            lineTo(20f, 14.25f)
        }
    }
    Canvas(Modifier.size(SEND_SIZE.dp).background(SendFill, CircleShape)) {
        surface(0.dp) { _, _, _, _ ->
            drawPath(path, Color.White, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The pastel panel the composer floats on: a lavender-to-sky wash with soft blobs of colour. */
@Composable
private fun Backdrop(modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(PANEL_RADIUS.dp))) {
        surface(0.dp) { _, _, w, h ->
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFCFC2F8), Color(0xFFB4D0F8)),
                    start = Offset.Zero,
                    end = Offset(w, h),
                ),
                size = Size(w, h),
            )
            for (blob in Blobs) {
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(blob.color, blob.color.copy(alpha = 0f)),
                        center = Offset(blob.x * w, blob.y * h),
                        radius = blob.radius,
                    ),
                    size = Size(w, h),
                )
            }
        }
    }
}
