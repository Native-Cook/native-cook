package com.ngocdevv.nativecook.blocks.inputs.swipereplycomposer

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.BreakIterator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PANEL_WIDTH = 350f
private const val PANEL_HEIGHT = 412f
private const val PANEL_RADIUS = 40f
private const val PAD_X = 22f

private const val TITLE_TOP = 20f
private const val TITLE_HEIGHT = 30f
private const val TITLE_PAD = 12f
private const val TITLE_GAP = 6f

// The old title leaves upwards by this much while the new one arrives from as far below.
private const val ROLL = 8f

private const val ANSWER_TOP = 68f
private const val ANSWER_WIDTH = PANEL_WIDTH - PAD_X * 2f
private const val PARAGRAPH_GAP = 16f
private const val ANSWER_SIZE = 15f
private const val ANSWER_LINE = 22f

// The pull approaches this distance but never reaches it.
private const val PULL_LIMIT = 110f
private const val RESISTANCE = 0.55f

// Pull at which the reply arrow pops in and a release quotes the paragraph.
private const val THRESHOLD = 44f

// Where autoPlay's swipe ends.
private const val SCRIPT_PULL = 56f
private const val ARROW_SIZE = 28f
private const val ARROW_GLYPH = 16f

// Scale and opacity the arrow reaches just before the threshold, and its scale while it is away.
private const val ARROW_HINT = 0.55f
private const val ARROW_HINT_ALPHA = 0.7f
private const val ARROW_REST = 0.01f
private const val BAR_WIDTH = 2.5f
private const val BAR_GAP = 10f
private const val BAR_INSET = 3f

// How much the other paragraphs fade.
private const val DIM = 0.68f

private const val COMPOSER_X = 18f
private const val COMPOSER_WIDTH = PANEL_WIDTH - COMPOSER_X * 2f
private const val COMPOSER_TOP = 352f
private const val COMPOSER_GAP = 6f
private const val PLUS_SIZE = 38f
private const val FIELD_HEIGHT = 40f
private const val FIELD_PAD_X = 14f
private const val FIELD_FONT = 15f

// A placeholder that does not fit shrinks this far before it is cut.
private const val PLACEHOLDER_SHRINK = 0.85f
private const val SEND_SIZE = 30f
private const val SEND_SLOT = 40f
private const val BRANCH_WIDTH = 78f
private const val BRANCH_SCALE = 0.6f
private const val FIELD_WIDE = COMPOSER_WIDTH - PLUS_SIZE - COMPOSER_GAP
private const val FIELD_NARROW = FIELD_WIDE - BRANCH_WIDTH - COMPOSER_GAP
private const val PRESS_SCALE = 0.86f
private const val CLEAR_RISE = 6f
private const val CARET_WIDTH = 2f
private const val CARET_HEIGHT = 18f
private const val CARET_GAP = 1f

private const val QUOTE_HEIGHT = 36f
private const val QUOTE_RADIUS = 14f
private const val QUOTE_TOP = COMPOSER_TOP - 8f - QUOTE_HEIGHT
private const val QUOTE_PAD = 14f
private const val QUOTE_GAP = 9f
private const val QUOTE_BAR = 16f
private const val DISMISS_SLOT = 36f
private const val DISMISS_SIZE = 22f

// Hidden, the preview sits this far down, behind the composer.
private const val QUOTE_DROP = 26f
private const val QUOTE_FLY = 70f

// The paragraph going back, the preview, the field's width and the Branch pill.
private const val SETTLE_STIFFNESS = 300f
private const val SETTLE_DAMPING = 26f

// The reply arrow popping in past the threshold, with overshoot.
private const val POP_STIFFNESS = 520f
private const val POP_DAMPING = 18f

// Reduced motion replaces both springs with a short ease-out.
private const val REDUCED_MS = 200

// The autoPlay loop, in seconds from its start.
private const val SWIPE_AT = 0.8

// A scripted swipe: how long the pull takes, when it arms and when the finger lets go.
private const val DRAG_MS = 650
private const val ARM_AFTER = 0.3
private const val RELEASE_AFTER = 0.95
private const val CARET_AT = 2.5
private const val TYPE_AT = 2.7

// The whole reply is typed in this time; a short one is typed no slower than SLOWEST_KEY per letter.
private const val TYPE_SPAN = 1.0
private const val SLOWEST_KEY = 0.075
private const val SEND_AT = 4.3
private const val SECOND_SWIPE_AT = 5.3
private const val DISMISS_AT = 7.3
private const val LOOP = 8.8

// How long autoPlay holds a button down before it acts, in seconds.
private const val PRESS_TIME = 0.12
private const val FADE_MS = 200
private const val ROLL_MS = 300
private const val FLY_MS = 280

// After the last touch, autoPlay waits this long.
private const val RESUME_AFTER_MS = 3000L

private val Panel = Color(0xFF1E1E1C)
private val Raised = Color(0xFF282826)
private val Field = Color(0xFF2E2E2B)
private val SendIdle = Color(0xFF3A3A37)
private val Ink = Color(0xFFECEAE4)
private val Quote = Color(0xFFC9C7C1)
private val Mute = Color(0xFF8A8A86)
private val Coral = Color(0xFFE8743B)

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val DragEase = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)
private val RollEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val FlyEase = CubicBezierEasing(0.4f, 0f, 1f, 1f)

private fun <T> springOr(reducedMotion: Boolean, stiffness: Float, damping: Float): AnimationSpec<T> =
    if (reducedMotion) tween(REDUCED_MS, easing = EaseOut) else spring(damping / (2f * sqrt(stiffness)), stiffness)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun uiText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val ChipStyle = uiText(13f, 18f, Ink, FontWeight.Medium)
private val QuoteStyle = uiText(13f, 18f, Quote)
private val FieldStyle = uiText(FIELD_FONT, 20f, Ink)
private val PlaceholderStyle = uiText(FIELD_FONT, 20f, Mute)

// The line height only opens space between the lines: a paragraph stays as tall as its own text,
// so the bar beside it and the gap to the next paragraph keep their size.
private val AnswerStyle = TextStyle(
    color = Ink,
    fontSize = ANSWER_SIZE.sp,
    lineHeight = ANSWER_LINE.sp,
    fontFamily = FontFamily.Serif,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val PlaceholderSize = TextAutoSize.StepBased(
    minFontSize = (FIELD_FONT * PLACEHOLDER_SHRINK).sp,
    maxFontSize = FIELD_FONT.sp,
    stepSize = 0.25.sp,
)

/** What autoPlay is pressing. */
private enum class Press { Send, Dismiss }

/** The paragraph's offset for a finger that has moved [distance] to the right: it follows less and less. */
private fun resist(distance: Float): Float {
    val travelled = max(0f, distance) * RESISTANCE
    return (1f - 1f / (travelled / PULL_LIMIT + 1f)) * PULL_LIMIT
}

private val Jitter = doubleArrayOf(0.0, 0.45, -0.2, 0.6, 0.1, -0.3, 0.75)

/** The user-perceived characters of [text], so an emoji is typed with one key. */
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

/**
 * One number a paragraph is drawn with. A change of state either puts it at its new place at once,
 * which is how it follows a finger, or takes it there on a curve; a value the change leaves where
 * it was keeps the animation it is in. Read while drawing, so nothing is recomposed.
 */
private class Follower(initial: Float, private val scope: CoroutineScope) {
    private val state = mutableFloatStateOf(initial)
    private var target = initial
    private var velocity = 0f
    private var job: Job? = null

    val value get() = state.floatValue

    fun go(to: Float, spec: AnimationSpec<Float>?) {
        if (to == target) return
        target = to
        job?.cancel()
        job = null
        if (spec == null) {
            velocity = 0f
            state.floatValue = to
            return
        }
        job = scope.launch {
            animate(state.floatValue, to, velocity, spec) { now, speed ->
                state.floatValue = now
                velocity = speed
            }
            velocity = 0f
        }
    }
}

private class ParagraphMotion(arrowRest: Float, scope: CoroutineScope) {
    /** How far the text is pulled to the right, in dp. */
    val shift = Follower(0f, scope)
    val bar = Follower(0f, scope)
    val dim = Follower(0f, scope)
    val arrowScale = Follower(arrowRest, scope)
    val arrowAlpha = Follower(0f, scope)
    val arrowX = Follower(-ARROW_SIZE / 2f, scope)
}

/** The swipe in progress. Plain fields: they only move the followers, which are what is drawn. */
private class Swipe {
    /** The paragraph under the finger and how far it has been pulled, after resistance. */
    var active: Int? = null
    var pull = 0f

    /** The pull has passed the threshold: releasing now quotes the paragraph. */
    var armed = false
}

/**
 * A dark chat panel where any paragraph of the assistant's answer can be swiped right to reply to
 * it. The paragraph follows the finger with resistance, a coral reply arrow pops in beside it past
 * a threshold and the rest of the answer dims; on release it springs back with a coral bar that
 * marks it as quoted, a quote preview slides up out of the composer, the placeholder changes and a
 * "Branch" pill appears beside the field. The quote can be answered, dismissed, or branched into a
 * new thread.
 *
 * @param value The field's value when the caller owns it; left null, the component keeps its own.
 * @param onValueChange Called with every change of the field's text.
 * @param autoPlay Swipes, replies and dismisses by itself in a loop. Never opens the keyboard.
 * @param branchTitle The title after "Branch" is tapped.
 * @param paragraphs The assistant's answer, one swipeable paragraph each.
 * @param quotePlaceholder The placeholder while a paragraph is quoted.
 * @param script What autoPlay types as its reply.
 * @param onSend Called with the trimmed message and the index of the quoted paragraph, if any.
 * @param onQuote Called with the index of the paragraph that was swiped, or null when the quote is dismissed.
 * @param onBranch Called with the index of the quoted paragraph when "Branch" is tapped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwipeReplyComposer(
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    title: String = "Weekend in Lisbon",
    branchTitle: String = "New branch",
    paragraphs: List<String> = listOf(
        "Two days is enough for Lisbon if you stay central and walk.",
        "Start in Alfama before the trams fill up, then ride the 28 up to Graça for the best light over the river.",
        "Keep Belém for the second morning: the tower, the monastery and a warm custard tart.",
    ),
    placeholder: String = "Reply to Atlas",
    quotePlaceholder: String = "Reply to this part…",
    branchLabel: String = "Branch",
    script: String = "And if it rains?",
    onSend: ((message: String, quoted: Int?) -> Unit)? = null,
    onQuote: ((index: Int?) -> Unit)? = null,
    onBranch: ((index: Int) -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val settle = remember(reducedMotion) { springOr<Float>(reducedMotion, SETTLE_STIFFNESS, SETTLE_DAMPING) }
    val settleDp = remember(reducedMotion) { springOr<Dp>(reducedMotion, SETTLE_STIFFNESS, SETTLE_DAMPING) }
    val pop = remember(reducedMotion) { springOr<Float>(reducedMotion, POP_STIFFNESS, POP_DAMPING) }
    val drag = remember { tween<Float>(DRAG_MS, easing = DragEase) }
    // Under reduced motion the arrow only fades, so it waits at full size.
    val arrowRest = if (reducedMotion) 1f else ARROW_REST

    // The timers and gestures below outlive a recomposition: they read the caller's latest props through these.
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnSend by rememberUpdatedState(onSend)
    val currentOnQuote by rememberUpdatedState(onQuote)
    val currentOnBranch by rememberUpdatedState(onBranch)
    val currentScript by rememberUpdatedState(script)
    val currentParagraphs by rememberUpdatedState(paragraphs)
    val auto by rememberUpdatedState(autoPlay)
    val motions by rememberUpdatedState(
        remember(paragraphs.size, arrowRest) { List(paragraphs.size) { ParagraphMotion(arrowRest, scope) } },
    )

    var draft by remember { mutableStateOf("") }
    // What autoPlay has typed so far; the caller's value is never touched by the script.
    var typed by remember { mutableStateOf("") }
    // True while autoPlay owns the component.
    var playing by remember { mutableStateOf(autoPlay) }
    var caretOn by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val swipe = remember { Swipe() }
    var quoted by remember { mutableStateOf<Int?>(null) }
    // The preview's text, kept while the preview slides away.
    var quoteText by remember { mutableStateOf("") }
    // The preview is flying up into a new branch.
    var flying by remember { mutableStateOf(false) }
    var branched by remember { mutableStateOf(false) }
    // The sent text is on its way out of the field.
    var clearing by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf<Press?>(null) }
    var run by remember { mutableIntStateOf(0) }
    var sends by remember { mutableIntStateOf(0) }
    var branches by remember { mutableIntStateOf(0) }
    var resume by remember { mutableIntStateOf(0) }
    // When the last key landed and the frame being drawn, both in nanoseconds: the caret blinks on their difference.
    var lastKey by remember { mutableLongStateOf(0L) }
    var caretClock by remember { mutableLongStateOf(0L) }
    // Where the script's text ends: the drawn caret stands just after it.
    var caretX by remember { mutableFloatStateOf(0f) }

    fun write(next: String) {
        if (currentValue == null) draft = next
        currentOnValueChange?.invoke(next)
    }

    /** Puts every paragraph where the state has it: at once when [spec] is null, as under a finger, otherwise on [spec]. */
    fun pose(spec: AnimationSpec<Float>?) {
        // 0…1: how far the pull is towards the threshold.
        val progress = min(1f, swipe.pull / THRESHOLD)
        motions.forEachIndexed { index, motion ->
            val isActive = swipe.active == index
            val isQuoted = quoted == index
            // The others dim as the pull approaches the threshold, and stay dim while a paragraph is quoted.
            val dimmed = when {
                quoted != null -> if (isQuoted) 0f else 1f
                swipe.active != null -> if (isActive) 0f else progress
                else -> 0f
            }
            motion.shift.go(if (isActive) swipe.pull else 0f, spec)
            motion.bar.go(if (isQuoted) 1f else if (isActive) progress else 0f, spec)
            motion.dim.go(dimmed, spec)
            motion.arrowScale.go(if (isActive) (if (swipe.armed) 1f else ARROW_HINT * progress) else arrowRest, spec)
            motion.arrowAlpha.go(if (isActive) (if (swipe.armed) 1f else ARROW_HINT_ALPHA * progress) else 0f, spec)
            // Centred in the gap the paragraph has opened to the left of its bar.
            motion.arrowX.go(if (isActive) (swipe.pull - BAR_GAP - ARROW_SIZE) / 2f else -ARROW_SIZE / 2f, spec)
        }
    }

    fun reset() {
        typed = ""
        caretOn = false
        pressed = null
        clearing = false
        flying = false
        swipe.pull = 0f
        swipe.armed = false
        swipe.active = null
        quoted = null
        branched = false
        pose(settle)
        sends += 1
        branches += 1
    }

    /** A touch stops the script and puts the panel back at rest. */
    fun takeOver() {
        if (!playing) return
        playing = false
        reset()
        run += 1
    }

    /** For buttons: true when the touch only stopped autoPlay, so the button should do nothing else. */
    fun handOver(): Boolean {
        if (!playing) return false
        takeOver()
        resume += 1
        return true
    }

    /** The paragraph springs back, marked as quoted, and the composer changes to answer it. */
    fun quote(index: Int, byHand: Boolean) {
        val text = currentParagraphs.getOrNull(index) ?: return
        if (byHand) currentOnQuote?.invoke(index)
        quoteText = text
        swipe.pull = 0f
        swipe.active = null
        swipe.armed = false
        quoted = index
        pose(settle)
    }

    fun release() {
        swipe.pull = 0f
        swipe.active = null
        swipe.armed = false
        pose(settle)
    }

    fun dismissQuote(byHand: Boolean) {
        if (byHand) {
            if (handOver()) return
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            currentOnQuote?.invoke(null)
            resume += 1
        }
        quoted = null
        pose(settle)
    }

    fun branch() {
        if (handOver()) return
        val index = quoted ?: return
        if (flying) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        currentOnBranch?.invoke(index)
        flying = true
        branches += 1
    }

    fun send() {
        val message = (if (playing) typed else currentValue ?: draft).trim()
        if (clearing || message.isEmpty()) return
        if (!playing) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            currentOnSend?.invoke(message, quoted)
        }
        caretOn = false
        clearing = true
        sends += 1
    }

    /** The finger is [distance] dp to the right of where it went down on paragraph [index]. */
    fun swipeTo(index: Int, distance: Float) {
        takeOver()
        if (quoted == index || flying) return
        swipe.active = index
        swipe.pull = resist(distance)
        pose(null)
        val isArmed = swipe.pull >= THRESHOLD
        if (isArmed != swipe.armed) {
            if (isArmed) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            swipe.armed = isArmed
            pose(pop)
        }
    }

    fun swipeEnd(index: Int) {
        if (swipe.active != index) return
        if (swipe.armed) quote(index, byHand = true) else release()
        resume += 1
    }

    LaunchedEffect(autoPlay) {
        playing = autoPlay
        reset()
        run += 1
    }

    // The autoPlay loop. Every step waits for its own moment after the loop began, so the period stays LOOP.
    LaunchedEffect(run) {
        if (!playing || currentParagraphs.size <= 1) return@LaunchedEffect

        // One scripted swipe: the pull grows as if a finger dragged it, arms on the way, holds, and lets go.
        suspend fun swipeByScript(index: Int, start: Long, at: Double) {
            sleepUntil(start, at)
            if (!reducedMotion) {
                swipe.active = index
                swipe.pull = SCRIPT_PULL
                pose(drag)
                sleepUntil(start, at + ARM_AFTER)
                swipe.armed = true
                pose(pop)
            }
            sleepUntil(start, at + RELEASE_AFTER)
            quote(index, byHand = false)
        }

        var start = System.nanoTime()
        while (true) {
            val text = currentScript
            val keys = letters(text)
            val times = rhythm(keys)
            swipeByScript(1, start, SWIPE_AT)
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
            sleepUntil(start, SEND_AT - PRESS_TIME)
            pressed = Press.Send
            sleepUntil(start, SEND_AT)
            pressed = null
            send()
            swipeByScript(currentParagraphs.lastIndex, start, SECOND_SWIPE_AT)
            sleepUntil(start, DISMISS_AT - PRESS_TIME)
            pressed = Press.Dismiss
            sleepUntil(start, DISMISS_AT)
            pressed = null
            dismissQuote(byHand = false)
            sleepUntil(start, LOOP)
            start += (LOOP * 1e9).toLong()
        }
    }

    // The sent text has left the field: empty it, and the quote goes with it.
    LaunchedEffect(sends) {
        if (!clearing) return@LaunchedEffect
        delay(FADE_MS.toLong())
        if (playing) typed = "" else write("")
        clearing = false
        quoted = null
        pose(settle)
        resume += 1
    }

    // The preview has flown off: the quote is over and the panel is now the new branch.
    LaunchedEffect(branches) {
        if (!flying) return@LaunchedEffect
        delay(FLY_MS.toLong())
        quoted = null
        branched = true
        pose(settle)
        flying = false
        resume += 1
    }

    // autoPlay comes back once nothing has been touched for a while and the panel is at rest.
    LaunchedEffect(resume) {
        if (!auto || playing || focused) return@LaunchedEffect
        delay(RESUME_AFTER_MS)
        if (focused || (currentValue ?: draft).isNotEmpty() || quoted != null || swipe.active != null) return@LaunchedEffect
        playing = true
        reset()
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
    val quoting = quoted != null
    val previewShown = quoting && !flying

    // Read where they are used, in layout and drawing, so a spring does not recompose the panel.
    val fly = remember(reducedMotion) {
        if (reducedMotion) tween<Float>(REDUCED_MS, easing = EaseOut) else tween(FLY_MS, easing = FlyEase)
    }
    val previewAlpha = animateFloatAsState(if (previewShown) 1f else 0f, if (flying) fly else settle, label = "preview")
    // Hidden, it waits behind the composer; flying into a branch, it leaves upwards.
    val previewY = animateFloatAsState(
        targetValue = if (reducedMotion) 0f else if (flying) -QUOTE_FLY else if (quoting) 0f else QUOTE_DROP,
        animationSpec = if (flying) fly else settle,
        label = "previewY",
    )
    val fieldWidth = animateDpAsState(if (quoting) FIELD_NARROW.dp else FIELD_WIDE.dp, settleDp, label = "field")
    val branchShown = animateFloatAsState(if (quoting) 1f else 0f, settle, label = "branch")
    val swap = animateFloatAsState(if (quoting) 1f else 0f, tween(FADE_MS, easing = EaseOut), label = "placeholder")
    val sendOn = animateFloatAsState(if (hasText) 1f else 0f, tween(FADE_MS, easing = EaseOut), label = "send")
    // The sent text rises and fades; once the field is empty it is back in place at once.
    val clear = animateFloatAsState(
        targetValue = if (clearing) 1f else 0f,
        animationSpec = if (clearing) tween(FADE_MS, easing = EaseIn) else snap(),
        label = "clear",
    )
    val rise = if (reducedMotion) 0f else CLEAR_RISE

    // The placeholder comes and goes at once with the text, except when the quote changes at the
    // same moment (a reply was sent): then it fades in while the two placeholders trade places.
    val placeholderShown = !hasText && !caretOn
    val placeholderAlpha = remember { Animatable(1f) }
    var placeholderQuoting by remember { mutableStateOf(false) }
    LaunchedEffect(placeholderShown, quoting) {
        val to = if (placeholderShown) 1f else 0f
        if (quoting != placeholderQuoting) {
            placeholderQuoting = quoting
            placeholderAlpha.animateTo(to, tween(FADE_MS, easing = EaseOut))
        } else {
            placeholderAlpha.snapTo(to)
        }
    }

    val arrow = remember { sendPath() }
    val branchArrow = remember { branchPath() }
    val cross = remember { crossPath() }

    Box(
        modifier
            .size(PANEL_WIDTH.dp, PANEL_HEIGHT.dp)
            .clip(RoundedCornerShape(PANEL_RADIUS.dp))
            .background(Panel),
    ) {
        TitleChip(
            title = title,
            branchTitle = branchTitle,
            branched = branched,
            glyph = branchArrow,
            reducedMotion = reducedMotion,
            modifier = Modifier.offset(PAD_X.dp, TITLE_TOP.dp),
        )

        Column(
            Modifier.offset(PAD_X.dp, ANSWER_TOP.dp).width(ANSWER_WIDTH.dp),
            verticalArrangement = Arrangement.spacedBy(PARAGRAPH_GAP.dp),
        ) {
            paragraphs.forEachIndexed { index, text ->
                val motion = motions[index]
                Box(
                    Modifier
                        .fillMaxWidth()
                        // A one-line paragraph still has the arrow's height.
                        .heightIn(min = ARROW_SIZE.dp)
                        .pointerInput(index) {
                            // The detector reports what the finger moves after the touch slop; the pull is
                            // measured from where the finger went down, so the slop is counted in.
                            var travelled = 0f
                            var moving = false
                            detectHorizontalDragGestures(
                                onDragStart = { moving = false },
                                onDragEnd = { swipeEnd(index) },
                                onDragCancel = { swipeEnd(index) },
                            ) { change, amount ->
                                change.consume()
                                if (!moving) {
                                    moving = true
                                    travelled = sign(amount) * viewConfiguration.touchSlop
                                }
                                travelled += amount
                                swipeTo(index, travelled / density)
                            }
                        }
                        .semantics(mergeDescendants = true) {
                            customActions = listOf(
                                CustomAccessibilityAction("Reply to this part") {
                                    quote(index, byHand = true)
                                    true
                                },
                            )
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    ReplyArrow(
                        Modifier.graphicsLayer {
                            translationX = motion.arrowX.value.dp.toPx()
                            scaleX = motion.arrowScale.value
                            scaleY = motion.arrowScale.value
                            alpha = motion.arrowAlpha.value.coerceIn(0f, 1f)
                        },
                    )
                    BasicText(
                        text = text,
                        style = AnswerStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = motion.shift.value.dp.toPx()
                                alpha = (1f - DIM * motion.dim.value).coerceIn(0f, 1f)
                                // The bar is drawn left of the text's own bounds, where a faded layer would cut it off.
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            .drawBehind {
                                val inset = BAR_INSET.dp.toPx()
                                val width = BAR_WIDTH.dp.toPx()
                                drawRoundRect(
                                    color = Coral,
                                    topLeft = Offset(-BAR_GAP.dp.toPx(), inset),
                                    size = Size(width, size.height - inset * 2f),
                                    cornerRadius = CornerRadius(width / 2f),
                                    alpha = motion.bar.value.coerceIn(0f, 1f),
                                )
                            },
                    )
                }
            }
        }

        // Before the composer, so the preview comes out from behind it.
        Row(
            Modifier
                .offset(COMPOSER_X.dp, QUOTE_TOP.dp)
                .size(COMPOSER_WIDTH.dp, QUOTE_HEIGHT.dp)
                .graphicsLayer {
                    alpha = previewAlpha.value.coerceIn(0f, 1f)
                    translationY = previewY.value.dp.toPx()
                }
                .background(Raised, RoundedCornerShape(QUOTE_RADIUS.dp))
                .padding(start = QUOTE_PAD.dp)
                .then(if (previewShown) Modifier else Modifier.clearAndSetSemantics {}),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(BAR_WIDTH.dp, QUOTE_BAR.dp).background(Coral, CircleShape))
            BasicText(
                text = quoteText,
                style = QuoteStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = QUOTE_GAP.dp),
            )
            Pressable(
                onClick = { dismissQuote(byHand = true) },
                modifier = Modifier.size(DISMISS_SLOT.dp, QUOTE_HEIGHT.dp),
                label = "Dismiss quote",
                enabled = previewShown,
                dipped = pressed == Press.Dismiss,
            ) {
                Box(Modifier.size(DISMISS_SIZE.dp).background(Field, CircleShape), contentAlignment = Alignment.Center) {
                    Glyph(cross, 9f, Mute, 1.6f)
                }
            }
        }

        Box(Modifier.offset(COMPOSER_X.dp, COMPOSER_TOP.dp).size(COMPOSER_WIDTH.dp, FIELD_HEIGHT.dp)) {
            Pressable(
                onClick = { if (!handOver()) onAttach?.invoke() },
                modifier = Modifier.align(Alignment.CenterStart).size(PLUS_SIZE.dp),
                label = "Attach",
            ) {
                Box(Modifier.matchParentSize().background(Field, CircleShape))
                Box(Modifier.size(14.dp, 1.6.dp).background(Quote, CircleShape))
                Box(Modifier.size(1.6.dp, 14.dp).background(Quote, CircleShape))
            }

            Box(
                Modifier
                    .offset(x = (PLUS_SIZE + COMPOSER_GAP).dp)
                    .layout { measurable, _ ->
                        val width = fieldWidth.value.roundToPx()
                        val height = FIELD_HEIGHT.dp.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) { placeable.place(0, 0) }
                    }
                    .background(Field, CircleShape),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Each placeholder keeps the width of the field it belongs to, so neither is laid out
                // again while the field's width springs.
                Box(
                    Modifier
                        .padding(start = FIELD_PAD_X.dp)
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .graphicsLayer { alpha = placeholderAlpha.value }
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(
                        text = placeholder,
                        style = PlaceholderStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = PlaceholderSize,
                        modifier = Modifier
                            .width((FIELD_WIDE - FIELD_PAD_X - SEND_SLOT).dp)
                            .graphicsLayer { alpha = 1f - swap.value },
                    )
                    BasicText(
                        text = quotePlaceholder,
                        style = PlaceholderStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = PlaceholderSize,
                        modifier = Modifier
                            .width((FIELD_NARROW - FIELD_PAD_X - SEND_SLOT).dp)
                            .graphicsLayer { alpha = swap.value },
                    )
                }

                BasicTextField(
                    value = value ?: draft,
                    onValueChange = { write(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = FIELD_PAD_X.dp, end = SEND_SLOT.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused == focused) return@onFocusChanged
                            focused = state.isFocused
                            if (focused) takeOver()
                            resume += 1
                        }
                        .graphicsLayer {
                            alpha = if (playing) 0f else 1f - clear.value
                            translationY = -rise.dp.toPx() * clear.value
                        }
                        .semantics { contentDescription = if (quoting) quotePlaceholder else placeholder },
                    textStyle = FieldStyle,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    // The send key also puts the keyboard away, as a one-line field does when it is submitted.
                    keyboardActions = KeyboardActions(
                        onSend = {
                            send()
                            focusManager.clearFocus()
                        },
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(Coral),
                    decorationBox = { text ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { text() }
                    },
                )

                if (playing) {
                    // The script's text, with the caret autoPlay draws after the last letter, since
                    // the field itself is not focused.
                    BasicText(
                        text = typed,
                        style = FieldStyle,
                        maxLines = 1,
                        softWrap = false,
                        onTextLayout = { caretX = it.getCursorRect(it.layoutInput.text.length).left },
                        modifier = Modifier
                            .padding(start = FIELD_PAD_X.dp)
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .drawWithContent {
                                drawContent()
                                val caret = if (caretOn) blink((caretClock - lastKey) / 1e9f) else 0f
                                if (caret > 0f) {
                                    val height = CARET_HEIGHT.dp.toPx()
                                    drawRoundRect(
                                        color = Coral,
                                        topLeft = Offset(caretX + CARET_GAP.dp.toPx(), (size.height - height) / 2f),
                                        size = Size(CARET_WIDTH.dp.toPx(), height),
                                        cornerRadius = CornerRadius(1.dp.toPx()),
                                        alpha = caret,
                                    )
                                }
                            }
                            .graphicsLayer {
                                alpha = 1f - clear.value
                                translationY = -rise.dp.toPx() * clear.value
                            }
                            .clearAndSetSemantics {},
                    )

                    // While autoPlay runs, a touch on the field hands it to the keyboard.
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

                Pressable(
                    onClick = { if (!handOver()) send() },
                    modifier = Modifier.align(Alignment.CenterEnd).size(SEND_SLOT.dp, FIELD_HEIGHT.dp),
                    label = "Send",
                    dipped = pressed == Press.Send,
                ) {
                    Canvas(Modifier.size(SEND_SIZE.dp)) {
                        surface(0.dp) { _, _, w, h ->
                            val on = sendOn.value
                            drawCircle(lerp(SendIdle, Coral, on), radius = w / 2f, center = Offset(w / 2f, h / 2f))
                            drawPath(
                                arrow,
                                lerp(Mute, Panel, on),
                                style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                    }
                }
            }

            Pressable(
                onClick = { branch() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer {
                        val shown = branchShown.value
                        val scale = if (reducedMotion) 1f else BRANCH_SCALE + (1f - BRANCH_SCALE) * shown
                        alpha = shown.coerceIn(0f, 1f)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    }
                    .size(BRANCH_WIDTH.dp, FIELD_HEIGHT.dp),
                enabled = quoting && !flying,
            ) {
                Row(
                    Modifier.matchParentSize().background(Field, CircleShape),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(branchArrow, 14f, Coral, 1.6f)
                    Spacer(Modifier.width(5.dp))
                    BasicText(
                        text = branchLabel,
                        style = ChipStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

/**
 * The capsule with the thread's title. Branching rolls the title out upwards and the new one in
 * from below, lets the branch glyph in ahead of it, and takes the capsule to its new width.
 */
@Composable
private fun TitleChip(
    title: String,
    branchTitle: String,
    branched: Boolean,
    glyph: Path,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val roll = animateFloatAsState(if (branched) 1f else 0f, tween(ROLL_MS, easing = RollEase), label = "roll")
    // Under reduced motion the two titles only cross-fade.
    val travel = if (reducedMotion) 0f else ROLL
    Layout(
        content = {
            Glyph(
                glyph, 14f, Coral, 1.6f,
                Modifier.graphicsLayer {
                    alpha = roll.value
                    scaleX = 0.5f + 0.5f * roll.value
                    scaleY = 0.5f + 0.5f * roll.value
                },
            )
            BasicText(
                text = title,
                style = ChipStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - roll.value
                    translationY = -travel.dp.toPx() * roll.value
                },
            )
            BasicText(
                text = branchTitle,
                style = ChipStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    alpha = roll.value
                    translationY = travel.dp.toPx() * (1f - roll.value)
                },
            )
        },
        modifier = modifier
            .height(TITLE_HEIGHT.dp)
            .clip(CircleShape)
            .background(Raised)
            .clearAndSetSemantics { contentDescription = if (branched) branchTitle else title },
    ) { measurables, constraints ->
        val (mark, from, to) = measurables.map { it.measure(Constraints()) }
        val shown = roll.value
        val pad = TITLE_PAD.dp.roundToPx()
        val lead = mark.width + TITLE_GAP.dp.roundToPx()
        val width = pad * 2 + (from.width + (lead + to.width - from.width) * shown).roundToInt()
        val height = constraints.maxHeight
        layout(width, height) {
            val x = pad + (lead * shown).roundToInt()
            mark.place(pad, (height - mark.height) / 2)
            from.place(x, (height - from.height) / 2)
            to.place(x, (height - to.height) / 2)
        }
    }
}

/** The coral disc with the reply arrow that grows beside a paragraph being pulled. */
@Composable
private fun ReplyArrow(modifier: Modifier = Modifier) {
    val path = remember { replyPath() }
    Canvas(modifier.size(ARROW_SIZE.dp)) {
        surface(0.dp) { _, _, w, h ->
            drawCircle(Coral, radius = w / 2f, center = Offset(w / 2f, h / 2f))
            translate((w - ARROW_GLYPH) / 2f, (h - ARROW_GLYPH) / 2f) {
                drawPath(path, Panel, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** A stroked icon drawn from dp coordinates in a square of [box] dp. */
@Composable
private fun Glyph(path: Path, box: Float, color: Color, width: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.size(box.dp)) {
        surface(0.dp) { _, _, _, _ ->
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * A control that dips while a finger presses it, and while autoPlay does ([dipped]). One that is
 * not [enabled] takes no touches at all.
 */
@Composable
private fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    dipped: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    val dip = animateFloatAsState(
        targetValue = if (dipped) PRESS_SCALE else 1f,
        animationSpec = tween((PRESS_TIME * 1000).toInt(), easing = EaseOut),
        label = "dip",
    )
    val touch = if (enabled) {
        Modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = press.value * dip.value
                scaleY = press.value * dip.value
            }
            .then(touch),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Reply arrow in a 16 × 16 box: a stem that rises and turns right into an arrowhead. */
private fun replyPath() = Path().apply {
    moveTo(3f, 12.5f)
    lineTo(3f, 10.5f)
    quadraticTo(3f, 6.5f, 7f, 6.5f)
    lineTo(13f, 6.5f)
    moveTo(9.6f, 3f)
    lineTo(13.1f, 6.5f)
    lineTo(9.6f, 10f)
}

/** Branching arrow in a 14 × 14 box: a stem going down with an arrow leaving it to the right. */
private fun branchPath() = Path().apply {
    moveTo(3.5f, 1.5f)
    lineTo(3.5f, 12.5f)
    moveTo(3.5f, 4.5f)
    quadraticTo(3.5f, 8.5f, 7.5f, 8.5f)
    lineTo(12f, 8.5f)
    moveTo(9.6f, 6f)
    lineTo(12.1f, 8.5f)
    lineTo(9.6f, 11f)
}

/** Up arrow in the 30 × 30 send button. */
private fun sendPath() = Path().apply {
    moveTo(15f, 21f)
    lineTo(15f, 9.5f)
    moveTo(10f, 14.25f)
    lineTo(15f, 9.25f)
    lineTo(20f, 14.25f)
}

/** Cross in a 9 × 9 box. */
private fun crossPath() = Path().apply {
    moveTo(0.8f, 0.8f)
    lineTo(8.2f, 8.2f)
    moveTo(8.2f, 0.8f)
    lineTo(0.8f, 8.2f)
}
