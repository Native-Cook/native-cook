package com.ngocdevv.nativecook.blocks.inputs.modelpickerprompt

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.BreakIterator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class ModelPickerPromptModel(
    val name: String,
    /** One line under the name in the menu. */
    val detail: String,
)

private const val WIDTH = 350f
private const val HEIGHT = 500f
private const val GLOW_Y = 236f
private const val GLOW_RADIUS = 250f

private const val TITLE_Y = 150f
private const val TITLE_HEIGHT = 48f
private const val TITLE_GAP = 9f
private const val MARK_SIZE = 30f

// While generating, the name rises and shrinks to make room for the answer.
private const val TITLE_LIFT = 42f
private const val TITLE_LIFT_SCALE = 0.86f

// SwiftUI's shadow radius is the blur's sigma.
private const val MARK_GLOW_SIGMA = 9f
private const val TITLE_GLOW_SIGMA = 10f

private const val ANSWER_TOP = 136f
private const val ANSWER_LINE = 10f
private const val ANSWER_GAP = 9f
private val ANSWER_WIDTHS = floatArrayOf(262f, 290f, 184f)

// A line starts this narrow, from its left end.
private const val ANSWER_SCALE = 0.2f

// The band of light, in widths of a line: where its centre starts, how far it travels, and its half-width.
private const val BAND_FROM = -0.4f
private const val BAND_TRAVEL = 1.8f
private const val BAND_HALF = 0.35f

private const val BOX_X = 14f
private const val BOX_TOP = 196f
private const val BOX_WIDTH = 322f
private const val BOX_HEIGHT = 100f
private const val BOX_RADIUS = 18f
private const val BOX_GLOW_SIGMA = 16f
private const val PAD_X = 16f
private const val PAD_TOP = 14f
private const val TEXT_WIDTH = BOX_WIDTH - PAD_X * 2f
private const val ENTER_BLUR = 12f
private const val ENTER_SCALE = 0.92f

// The layers of the box and of the menu overflow them by this much on every side, so the glow, the
// shadow and the entrance blur are not cut off while a layer is translucent.
private val BLEED = 72.dp

private const val FONT_SIZE = 16f
private const val PLACEHOLDER_SIZE = 15f
private const val LINE = 21f
private const val MAX_LINES = 2
private const val CARET_WIDTH = 2f
private const val CARET_HEIGHT = 19f
private const val CARET_GAP = 1f

// Width kept free at the end of a line for the drawn caret.
private const val CARET_ROOM = 3f

// Centre line and height of the row of controls along the bottom of the box.
private const val ROW_Y = 77f
private const val ROW_HEIGHT = 30f
private const val BUTTON_HIT = 40f
private const val PLUS_X = 24f
private const val ACTION_X = 299f
private const val MODEL_RIGHT = 276f

// Room for the longest model name in the label.
private const val MODEL_NAME = 110f
private const val MODEL_GAP = 5f
private const val CHEVRON_WIDTH = 8f
private const val ROLL = 9f
private const val SEND_RADIUS = 9f
private const val RING_SIZE = 26f
private const val RING_ARC = 0.78f
private const val RING_STROKE = 1.8f
private const val STOP_SIZE = 9f
private const val STOP_RADIUS = 2.5f
private const val SWAP_SCALE = 0.4f
private const val PRESS_SCALE = 0.88f

private const val MAX_CHIPS = 4
private const val CHIP_GAP = 14f
private const val CHIP_HEIGHT = 30f
private const val CHIP_RADIUS = 9f
private const val CHIP_SPACING = 6f
private const val CHIP_FLY_X = 44f
private const val CHIP_FLY_Y = 30f

private const val MENU_GAP = 6f
private const val MENU_WIDTH = 262f
private const val MENU_ROW = 50f
private const val MENU_PAD = 6f
private const val MENU_RADIUS = 14f
private const val MENU_SCALE = 0.9f
private const val MENU_SHADOW_SIGMA = 18f
private const val MENU_SHADOW_Y = 12f
private const val ROW_RADIUS = 9f

// How white a menu row turns while it is pressed, or when autoPlay is about to choose it.
private const val ROW_LIGHT = 0.08f

// The name rising above the answer and coming back.
private const val LIFT_STIFFNESS = 220f
private const val LIFT_DAMPING = 22f

// Waveform, send button and stop ring trading places.
private const val SWAP_STIFFNESS = 420f
private const val SWAP_DAMPING = 24f

// A chip landing in the row, with a little overshoot.
private const val CHIP_STIFFNESS = 300f
private const val CHIP_DAMPING = 20f
private const val MENU_STIFFNESS = 420f
private const val MENU_DAMPING = 28f

// Reduced motion replaces every spring with a short ease-out.
private const val REDUCED_MS = 200

// The entrance, from the first appearance.
private const val ENTER_BOX_AT_MS = 100L
private const val ENTER_BOX_MS = 500
private const val ENTER_CHIPS_AT_MS = 300L
private const val ENTER_CHIP_EVERY_MS = 120L

// The autoPlay loop, in seconds from its start.
private const val CARET_AT = 0.5
private const val TYPE_AT = 0.8

// The whole script is typed in this time; a short one is typed no slower than SLOWEST_KEY per letter.
private const val TYPE_SPAN = 1.5
private const val SLOWEST_KEY = 0.06
private const val PRESS_AT = 2.75
private const val PRESS = 0.12
private const val MENU_AT = 5.9
private const val FLASH = 0.16
private const val CHOOSE_AT = 7.0
private const val LOOP = 8.6

private const val FADE_MS = 200
private const val PRESS_MS = 120
private const val ROLL_MS = 300
private const val MENU_CLOSE_MS = 160
private const val ROW_LIGHT_MS = 120
private const val GLOW_MS = 600
private const val ANSWER_AT_MS = 150
private const val ANSWER_STAGGER_MS = 150
private const val ANSWER_GROW_MS = 350
private const val SHIMMER_SWEEP = 1.2f

// Seconds per turn of the stop ring.
private const val RING_TURN = 1.6f

// After the last touch, autoPlay waits this long.
private const val RESUME_AFTER_MS = 3000L

private val Coral = Color(0xFFD97757)
private val Ember = Color(0xFFC6613F)
private val BoxFill = Color(0xFF2F2D2B)
private val Ink = Color(0xFFF3EFEA)
private val Placeholder = Color(0xFF8F8B86)
private val Control = Color(0xFFA8A39D)
private val ChipText = Color(0xFFCFCAC4)
private val ChipBorder = Color(0xFF55514D)
private val MenuFill = Color(0xFF262422)
private val MenuBorder = Color(0xFF3F3B38)
private val Answer = rgba(255, 255, 255, 0.13f)
private val AnswerLight = rgba(255, 255, 255, 0.42f)
private val BoxBorder = Coral.copy(alpha = 0.3f)
private val BoxGlow = Coral.copy(alpha = 0.22f)
private val MarkGlow = Coral.copy(alpha = 0.7f)
private val TitleGlow = rgba(255, 255, 255, 0.35f)
private val MenuShadow = rgba(0, 0, 0, 0.5f)
private val Selection = TextSelectionColors(handleColor = Coral, backgroundColor = Coral.copy(alpha = 0.3f))

// The warm light behind everything, and the layer that brightens it while the answer is on its
// way. Both are drawn in a square of twice the radius, so they fade to nothing before its edges.
private val Glow = Brush.radialGradient(
    0f to Ember.copy(alpha = 0.34f),
    0.5f to Ember.copy(alpha = 0.12f),
    1f to Ember.copy(alpha = 0f),
    center = Offset(GLOW_RADIUS, GLOW_RADIUS),
    radius = GLOW_RADIUS,
)
private val GlowBoost = Brush.radialGradient(
    listOf(Ember.copy(alpha = 0.3f), Ember.copy(alpha = 0f)),
    center = Offset(GLOW_RADIUS, GLOW_RADIUS),
    radius = GLOW_RADIUS,
)

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val RollEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

private val FadeSpec = tween<Float>(FADE_MS, easing = EaseOut)
private val RollSpec = tween<Float>(ROLL_MS, easing = RollEasing)

/** A spring of mass 1, or a short ease-out under reduced motion. */
private fun springSpec(stiffness: Float, damping: Float, reducedMotion: Boolean): AnimationSpec<Float> =
    if (reducedMotion) {
        tween(REDUCED_MS, easing = EaseOut)
    } else {
        spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.001f)
    }

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun promptText(
    size: Float,
    lineHeight: Float,
    color: Color = Color.Unspecified,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily? = null,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFamily = family,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = promptText(38f, 46f, Color.White, FontWeight.Medium, FontFamily.Serif)
private val FieldStyle = promptText(FONT_SIZE, LINE, Ink, family = FontFamily.Serif)
private val PlaceholderStyle = promptText(PLACEHOLDER_SIZE, LINE, Placeholder)

// Its colour follows the menu, so it is given where the label is drawn.
private val ModelStyle = promptText(13f, 16f, weight = FontWeight.Medium)
private val ChipStyle = promptText(13f, 16f, ChipText)
private val MenuNameStyle = promptText(14f, 17f, Ink, FontWeight.Medium)
private val MenuDetailStyle = promptText(12f, 14f, Placeholder)

/** What autoPlay is pressing. */
private enum class Press { Send, Model }

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

/** A button coming in or leaving its slot: it fades while it scales between [from] and its full size. */
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
        val height = min(placeable.height, (MAX_LINES * LINE).sp.roundToPx())
        layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
    }

/**
 * Dark prompt box of an assistant, glowing on a warm vignette under its name. The box holds the
 * text, a plus, a model picker and one button that changes with the state: a waveform while the
 * field is empty, a coral send button once there is text, and a stop ring while the answer is
 * "generating" (the name lifts and three lines of a faux answer shimmer in under it). The model
 * label opens a small menu of models; choosing one closes it and rolls the label.
 *
 * @param value The field's value when the caller owns it; left null, the component keeps its own.
 * @param onValueChange Called with every change of the field's text.
 * @param autoPlay Types [script], sends it, waits for the answer and changes the model, by itself. Never opens the keyboard.
 * @param script What autoPlay types.
 * @param initialModel Index of the model selected at first.
 * @param chips Up to four shortcuts under the box.
 * @param generateFor How many seconds the stop ring turns after a send, unless [onStop] ends it.
 * @param onSend Called with the trimmed message when the send button or the keyboard's send key is used.
 * @param onStop Called when the stop ring is tapped while generating.
 * @param onModelChange Called with the index of the model chosen in the menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelPickerPrompt(
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    name: String = "Atlas",
    placeholder: String = "How can I help you today?",
    script: String = "Plan three days in Lisbon in May",
    models: List<ModelPickerPromptModel> = listOf(
        ModelPickerPromptModel("Swift", "Fastest for everyday tasks"),
        ModelPickerPromptModel("Balanced", "Smart and quick for most work"),
        ModelPickerPromptModel("Deep", "Thinks longer on hard problems"),
    ),
    initialModel: Int = 0,
    chips: List<String> = listOf("Create", "Write", "Learn", "Code"),
    generateFor: Double = 2.4,
    onSend: ((String) -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    onModelChange: ((Int) -> Unit)? = null,
    onChip: ((String) -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // The timers below outlive a recomposition: they read the caller's latest props through these.
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentScript by rememberUpdatedState(script)
    val currentModels by rememberUpdatedState(models)
    val currentInitialModel by rememberUpdatedState(initialModel)
    val currentGenerateFor by rememberUpdatedState(generateFor)
    val currentOnSend by rememberUpdatedState(onSend)
    val currentOnStop by rememberUpdatedState(onStop)
    val currentOnModelChange by rememberUpdatedState(onModelChange)
    val auto by rememberUpdatedState(autoPlay)

    var draft by remember { mutableStateOf("") }
    // What autoPlay has typed so far; the caller's value is never touched by the script.
    var typed by remember { mutableStateOf("") }
    // True while autoPlay owns the component.
    var playing by remember { mutableStateOf(autoPlay) }
    var caretOn by remember { mutableStateOf(false) }
    // When the last key landed and the frame being drawn, both in nanoseconds: the caret blinks on their difference.
    var lastKey by remember { mutableLongStateOf(0L) }
    var caretClock by remember { mutableLongStateOf(0L) }
    // Where the script's text ends: the drawn caret stands just after it.
    var caretSpot by remember { mutableStateOf(Rect.Zero) }
    var focused by remember { mutableStateOf(false) }
    // How many chips have flown in.
    var chipsIn by remember { mutableIntStateOf(0) }
    // The sent text is fading out of the field.
    var sending by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    // Seconds since the answer began. It only advances while generating: the band of light and the
    // stop ring stay where they were while they fade.
    var answerClock by remember { mutableFloatStateOf(0f) }
    var menuOpen by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf<Int?>(null) }
    // The menu row autoPlay lights before choosing it.
    var flashed by remember { mutableStateOf<Int?>(null) }
    var pressed by remember { mutableStateOf<Press?>(null) }
    // Each sequence below starts over when its counter changes.
    var run by remember { mutableIntStateOf(0) }
    var generations by remember { mutableIntStateOf(0) }
    var resume by remember { mutableIntStateOf(0) }

    fun selection() = (model ?: currentInitialModel).coerceIn(0, max(currentModels.size - 1, 0))

    fun write(next: String) {
        if (currentValue == null) draft = next
        currentOnValueChange?.invoke(next)
    }

    fun reset() {
        typed = ""
        caretOn = false
        pressed = null
        flashed = null
        menuOpen = false
        sending = false
        generating = false
        generations += 1
    }

    /** A touch stops the script and leaves the box at rest. */
    fun takeOver() {
        if (!playing) return
        playing = false
        reset()
        run += 1
    }

    /** The sent text has faded: it leaves the field. */
    fun clearSent() {
        if (playing) typed = "" else write("")
        sending = false
    }

    fun send() {
        val message = (if (playing) typed else currentValue ?: draft).trim()
        if (generating || sending || message.isEmpty()) return
        if (!playing) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            currentOnSend?.invoke(message)
        }
        sending = true
        caretOn = false
        answerClock = 0f
        generating = true
        generations += 1
    }

    fun stop() {
        if (!generating) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        currentOnStop?.invoke()
        // Stopped before the sent text had finished fading: nothing else would clear it.
        if (sending) clearSent()
        generating = false
        generations += 1
        resume += 1
    }

    fun choose(index: Int, byHand: Boolean) {
        if (byHand) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (index != selection()) currentOnModelChange?.invoke(index)
        }
        model = index
        flashed = null
        menuOpen = false
    }

    LaunchedEffect(autoPlay) {
        playing = autoPlay
        reset()
        run += 1
    }

    // The box fades in from a blur while it scales up, then the chips fly in one at a time.
    val enter = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reducedMotion) {
            chipsIn = MAX_CHIPS
            return@LaunchedEffect
        }
        delay(ENTER_BOX_AT_MS)
        launch { enter.animateTo(1f, tween(ENTER_BOX_MS, easing = EaseOut)) }
        delay(ENTER_CHIPS_AT_MS - ENTER_BOX_AT_MS)
        for (count in 1..MAX_CHIPS) {
            chipsIn = count
            delay(ENTER_CHIP_EVERY_MS)
        }
    }

    // The autoPlay loop. Every step waits for its own moment after the loop began, so the period stays LOOP.
    LaunchedEffect(run) {
        if (!playing) return@LaunchedEffect
        val mine = run
        var start = System.nanoTime()

        // A touch can land while a step is already waking up: a run that is no longer the current one ends here.
        suspend fun at(seconds: Double) {
            sleepUntil(start, seconds)
            if (run != mine) throw CancellationException()
        }

        while (true) {
            val text = currentScript
            val keys = letters(text)
            val times = rhythm(keys)
            at(CARET_AT)
            lastKey = System.nanoTime()
            caretOn = true
            var end = 0
            for (index in keys.indices) {
                at(TYPE_AT + times[index])
                end += keys[index].length
                typed = text.substring(0, end)
                lastKey = System.nanoTime()
            }
            at(PRESS_AT)
            pressed = Press.Send
            at(PRESS_AT + PRESS)
            pressed = null
            send()
            at(MENU_AT - PRESS)
            pressed = Press.Model
            at(MENU_AT)
            pressed = null
            menuOpen = true
            // The second model, then back to the first on the next loop: two loops end where they started.
            val target = if (selection() == 0 && currentModels.size > 1) 1 else 0
            at(CHOOSE_AT - FLASH)
            flashed = target
            at(CHOOSE_AT)
            choose(target, byHand = false)
            at(LOOP)
            start += (LOOP * 1e9).toLong()
        }
    }

    // The sent text fades and is cleared; the answer "generates" for a while, then the box is at rest again.
    LaunchedEffect(generations) {
        if (!generating) return@LaunchedEffect
        val mine = generations
        delay(FADE_MS.toLong())
        // As in the loop above: a send that was stopped or reset while this was waking up is over.
        if (generations != mine) return@LaunchedEffect
        clearSent()
        delay(max(0L, (currentGenerateFor * 1000).toLong() - FADE_MS))
        if (generations != mine) return@LaunchedEffect
        generating = false
        resume += 1
    }

    // autoPlay comes back once nothing has been touched for a while and the box is at rest.
    LaunchedEffect(resume) {
        if (!auto || playing || focused) return@LaunchedEffect
        delay(RESUME_AFTER_MS)
        if (focused || (currentValue ?: draft).isNotEmpty() || generating || menuOpen) return@LaunchedEffect
        playing = true
        run += 1
    }

    // The menu opening or closing counts as a touch: the wait before autoPlay returns starts over.
    LaunchedEffect(Unit) {
        snapshotFlow { menuOpen }.drop(1).collect { resume += 1 }
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
        while (true) withFrameNanos { caretClock = System.nanoTime() }
    }

    LaunchedEffect(generating, reducedMotion) {
        if (!generating || reducedMotion) return@LaunchedEffect
        val since = withFrameNanos { it }
        while (true) withFrameNanos { answerClock = (it - since) / 1e9f }
    }

    val shown = if (playing) typed else value ?: draft
    val hasText = shown.isNotEmpty()
    val selected = (model ?: initialModel).coerceIn(0, max(models.size - 1, 0))
    val mode = if (generating) 2 else if (hasText) 1 else 0

    val liftSpec = remember { springSpec(LIFT_STIFFNESS, LIFT_DAMPING, reducedMotion) }
    val swapSpec = remember { springSpec(SWAP_STIFFNESS, SWAP_DAMPING, reducedMotion) }
    val chipSpec = remember { springSpec(CHIP_STIFFNESS, CHIP_DAMPING, reducedMotion) }
    val menuSpec = remember { springSpec(MENU_STIFFNESS, MENU_DAMPING, reducedMotion) }
    val swapFrom = if (reducedMotion) 1f else SWAP_SCALE

    // Read where they are used, in drawing, so a spring does not recompose the component.
    val lift = animateFloatAsState(if (generating) 1f else 0f, liftSpec, label = "lift")
    val glowBoost = animateFloatAsState(if (generating) 1f else 0f, tween(GLOW_MS, easing = EaseInOut), label = "glow")
    // The sent text fades out; the empty field that follows it is simply there.
    val sentAlpha = animateFloatAsState(if (sending) 0f else 1f, if (sending) FadeSpec else snap(), label = "sent")
    val waveShown = animateFloatAsState(if (mode == 0) 1f else 0f, swapSpec, label = "waveform")
    val sendShown = animateFloatAsState(if (mode == 1) 1f else 0f, swapSpec, label = "send")
    val stopShown = animateFloatAsState(if (mode == 2) 1f else 0f, swapSpec, label = "stop")
    val pressSpec = remember { tween<Float>(PRESS_MS, easing = EaseOut) }
    val sendDip = animateFloatAsState(if (pressed == Press.Send) PRESS_SCALE else 1f, pressSpec, label = "sendDip")
    val modelDip = animateFloatAsState(if (pressed == Press.Model) PRESS_SCALE else 1f, pressSpec, label = "modelDip")
    val menuShown = animateFloatAsState(
        targetValue = if (menuOpen) 1f else 0f,
        animationSpec = if (menuOpen) menuSpec else tween(MENU_CLOSE_MS, easing = EaseOut),
        label = "menu",
    )
    // The menu stays in the tree until it has faded out.
    val menuVisible by remember { derivedStateOf { menuOpen || menuShown.value > 0f } }

    // The placeholder fades when text comes or goes; the script's caret simply takes its place.
    val placeholderShown = remember { Animatable(if (hasText) 0f else 1f) }
    var hadText by remember { mutableStateOf(hasText) }
    LaunchedEffect(hasText, caretOn) {
        val target = if (hasText || caretOn) 0f else 1f
        if (hasText != hadText) {
            hadText = hasText
            placeholderShown.animateTo(target, FadeSpec)
        } else if (placeholderShown.targetValue != target) {
            placeholderShown.snapTo(target)
        }
    }

    // The model's name rolls when it changes. The chevron and the label's colour follow the menu:
    // on the roll's curve when a new choice closes it, on a plain fade otherwise.
    var labelFrom by remember { mutableIntStateOf(selected) }
    var labelTo by remember { mutableIntStateOf(selected) }
    val roll = remember { Animatable(1f) }
    val menuTint = remember { Animatable(0f) }
    LaunchedEffect(menuOpen, selected) {
        val rolling = selected != labelTo
        if (rolling) {
            labelFrom = labelTo
            labelTo = selected
            roll.snapTo(0f)
        }
        launch { roll.animateTo(1f, RollSpec) }
        menuTint.animateTo(if (menuOpen) 1f else 0f, if (rolling) RollSpec else FadeSpec)
    }

    val titleStyle = remember(density) {
        val blur = sigmaToRadius(with(density) { TITLE_GLOW_SIGMA.dp.toPx() })
        TitleStyle.copy(shadow = Shadow(TitleGlow, Offset.Zero, blur))
    }
    val boxGlow = remember { SoftRoundRect(BoxGlow, sigma = BOX_GLOW_SIGMA) }
    val menuShadow = remember { SoftRoundRect(MenuShadow, sigma = MENU_SHADOW_SIGMA) }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        // The glow is wider than the component and overflows it on both sides.
        Canvas(
            Modifier
                .offset((WIDTH / 2f - GLOW_RADIUS).dp, (GLOW_Y - GLOW_RADIUS).dp)
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size((GLOW_RADIUS * 2f).dp),
        ) {
            surface(0.dp) { _, _, w, h ->
                drawRect(Glow, size = Size(w, h))
                drawRect(GlowBoost, size = Size(w, h), alpha = glowBoost.value)
            }
        }

        Box(
            Modifier
                .offset(0.dp, (TITLE_Y - TITLE_HEIGHT / 2f).dp)
                .size(WIDTH.dp, TITLE_HEIGHT.dp)
                .graphicsLayer {
                    val lifted = lift.value
                    val scale = if (reducedMotion) 1f else 1f - (1f - TITLE_LIFT_SCALE) * lifted
                    scaleX = scale
                    scaleY = scale
                    translationY = (-TITLE_LIFT * lifted).dp.toPx()
                }
                .clearAndSetSemantics { contentDescription = name },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TITLE_GAP.dp)) {
                MarkIcon(MARK_SIZE, Coral, MarkGlow, MARK_GLOW_SIGMA)
                BasicText(
                    text = name,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        AnswerLines(
            generating = generating,
            reducedMotion = reducedMotion,
            clock = { answerClock },
            modifier = Modifier.offset((BOX_X + PAD_X).dp, ANSWER_TOP.dp),
        )

        Box(
            Modifier
                .offset(BOX_X.dp, BOX_TOP.dp)
                .size(BOX_WIDTH.dp, BOX_HEIGHT.dp)
                .bleed(BLEED)
                .graphicsLayer {
                    val entered = enter.value
                    alpha = entered
                    val scale = ENTER_SCALE + (1f - ENTER_SCALE) * entered
                    scaleX = scale
                    scaleY = scale
                    // The blur needs API 31; below that the box fades and scales. It is dropped
                    // once the entrance is over, so the text is drawn sharp.
                    renderEffect = if (entered < 1f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val radius = sigmaToRadius(ENTER_BLUR.dp.toPx() * (1f - entered))
                        BlurEffect(radius, radius, TileMode.Decal)
                    } else {
                        null
                    }
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                surface(BLEED) { x, y, w, h ->
                    drawIntoCanvas { boxGlow.draw(it.nativeCanvas, x, y, w, h, BOX_RADIUS) }
                    drawRoundRect(BoxFill, Offset(x, y), Size(w, h), CornerRadius(BOX_RADIUS))
                    drawRoundRect(
                        color = BoxBorder,
                        topLeft = Offset(x + 0.5f, y + 0.5f),
                        size = Size(w - 1f, h - 1f),
                        cornerRadius = CornerRadius(BOX_RADIUS - 0.5f),
                        style = Stroke(1f),
                    )
                }
            }

            Box(Modifier.fillMaxSize().padding(BLEED)) {
                Box(Modifier.offset(PAD_X.dp, PAD_TOP.dp).width(TEXT_WIDTH.dp)) {
                    BasicText(
                        text = placeholder,
                        style = PlaceholderStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = placeholderShown.value },
                    )

                    // The selection takes the caret's colour, as a tint does.
                    CompositionLocalProvider(LocalTextSelectionColors provides Selection) {
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
                                .graphicsLayer { alpha = if (playing) 0f else sentAlpha.value },
                            textStyle = FieldStyle,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    // The send key also puts the keyboard away, which lets autoPlay come back.
                                    send()
                                    focusManager.clearFocus()
                                },
                            ),
                            maxLines = MAX_LINES,
                            cursorBrush = SolidColor(Coral),
                        )
                    }

                    if (playing) {
                        // The script's text, with the caret autoPlay draws after the last letter,
                        // since the field itself is not focused.
                        BasicText(
                            text = typed,
                            style = FieldStyle,
                            onTextLayout = { caretSpot = it.getCursorRect(it.layoutInput.text.length) },
                            modifier = Modifier
                                .graphicsLayer { alpha = sentAlpha.value }
                                .lastLines()
                                .drawWithContent {
                                    drawContent()
                                    val caret = if (caretOn) blink((caretClock - lastKey) / 1e9f) else 0f
                                    if (caret > 0f) {
                                        val height = CARET_HEIGHT.dp.toPx()
                                        drawRoundRect(
                                            color = Coral,
                                            topLeft = Offset(caretSpot.left + CARET_GAP.dp.toPx(), caretSpot.center.y - height / 2f),
                                            size = Size(CARET_WIDTH.dp.toPx(), height),
                                            cornerRadius = CornerRadius(1.dp.toPx()),
                                            alpha = caret,
                                        )
                                    }
                                }
                                .width((TEXT_WIDTH - CARET_ROOM).dp),
                        )
                    }
                }

                PressButton(
                    label = "Attach",
                    onClick = { onAttach?.invoke() },
                    modifier = Modifier
                        .offset((PLUS_X - BUTTON_HIT / 2f).dp, (ROW_Y - BUTTON_HIT / 2f).dp)
                        .size(BUTTON_HIT.dp),
                ) {
                    Box(Modifier.size(15.dp, 1.6.dp).background(Control, CircleShape))
                    Box(Modifier.size(1.6.dp, 15.dp).background(Control, CircleShape))
                }

                PressButton(
                    label = "Model",
                    state = models.getOrNull(selected)?.name ?: "",
                    onClick = { menuOpen = !menuOpen },
                    modifier = Modifier
                        .offset((MODEL_RIGHT - MODEL_NAME - MODEL_GAP - CHEVRON_WIDTH).dp, (ROW_Y - ROW_HEIGHT / 2f).dp)
                        .graphicsLayer {
                            scaleX = modelDip.value
                            scaleY = modelDip.value
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        },
                ) {
                    ModelLabel(
                        leaving = models.getOrNull(labelFrom)?.name ?: "",
                        arriving = models.getOrNull(labelTo)?.name ?: "",
                        roll = { roll.value },
                        tint = { menuTint.value },
                        reducedMotion = reducedMotion,
                    )
                }

                // One slot, three buttons: the waveform, the send button and the stop ring trade places.
                Box(Modifier.offset((ACTION_X - BUTTON_HIT / 2f).dp, (ROW_Y - BUTTON_HIT / 2f).dp).size(BUTTON_HIT.dp)) {
                    PressButton(
                        label = "Dictate",
                        enabled = mode == 0,
                        onClick = { onVoice?.invoke() },
                        modifier = Modifier.matchParentSize().graphicsLayer { swapIn(waveShown.value, swapFrom) },
                    ) {
                        WaveformIcon(Control)
                    }

                    PressButton(
                        label = "Send",
                        enabled = mode == 1,
                        onClick = { send() },
                        modifier = Modifier.matchParentSize().graphicsLayer { swapIn(sendShown.value, swapFrom, sendDip.value) },
                    ) {
                        ArrowIcon(Color.White, Modifier.background(Coral, RoundedCornerShape(SEND_RADIUS.dp)))
                    }

                    PressButton(
                        label = "Stop",
                        enabled = mode == 2,
                        onClick = { stop() },
                        modifier = Modifier.matchParentSize().graphicsLayer { swapIn(stopShown.value, swapFrom) },
                    ) {
                        StopRing(turns = { if (reducedMotion) 0f else answerClock / RING_TURN })
                    }
                }
            }
        }

        Row(
            Modifier
                .offset(0.dp, (BOX_TOP + BOX_HEIGHT + CHIP_GAP).dp)
                .size(WIDTH.dp, CHIP_HEIGHT.dp),
            horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING.dp, Alignment.CenterHorizontally),
        ) {
            chips.take(MAX_CHIPS).forEachIndexed { index, label ->
                val arrived = animateFloatAsState(if (index < chipsIn) 1f else 0f, chipSpec, label = "chip")
                PressButton(
                    enabled = index < chipsIn,
                    onClick = { onChip?.invoke(label) },
                    modifier = Modifier.graphicsLayer {
                        val landed = arrived.value
                        alpha = landed.coerceIn(0f, 1f)
                        if (!reducedMotion) {
                            translationX = (CHIP_FLY_X * (1f - landed)).dp.toPx()
                            translationY = (CHIP_FLY_Y * (1f - landed)).dp.toPx()
                        }
                    },
                ) {
                    Row(
                        Modifier
                            .height(CHIP_HEIGHT.dp)
                            .border(1.dp, ChipBorder, RoundedCornerShape(CHIP_RADIUS.dp))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ChipIcon(index, Control)
                        BasicText(label, style = ChipStyle, maxLines = 1)
                    }
                }
            }
        }

        if (menuOpen) {
            // A touch outside the menu closes it.
            Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onTap = { menuOpen = false }) })
        }

        if (menuVisible) {
            Box(
                Modifier
                    .offset((BOX_X + BOX_WIDTH - MENU_WIDTH).dp, (BOX_TOP + BOX_HEIGHT + MENU_GAP).dp)
                    .size(MENU_WIDTH.dp, (models.size * MENU_ROW + MENU_PAD * 2f).dp)
                    .bleed(BLEED)
                    .graphicsLayer {
                        val open = menuShown.value
                        alpha = open.coerceIn(0f, 1f)
                        val scale = if (reducedMotion) 1f else MENU_SCALE + (1f - MENU_SCALE) * open
                        scaleX = scale
                        scaleY = scale
                        // It grows out of its own top-right corner, under the model label.
                        val bleed = BLEED.toPx()
                        transformOrigin = TransformOrigin((size.width - bleed) / size.width, bleed / size.height)
                    },
            ) {
                Canvas(Modifier.matchParentSize()) {
                    surface(BLEED) { x, y, w, h ->
                        drawIntoCanvas { menuShadow.draw(it.nativeCanvas, x, y + MENU_SHADOW_Y, w, h, MENU_RADIUS) }
                        drawRoundRect(MenuFill, Offset(x, y), Size(w, h), CornerRadius(MENU_RADIUS))
                        drawRoundRect(
                            color = MenuBorder,
                            topLeft = Offset(x + 0.5f, y + 0.5f),
                            size = Size(w - 1f, h - 1f),
                            cornerRadius = CornerRadius(MENU_RADIUS - 0.5f),
                            style = Stroke(1f),
                        )
                    }
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(BLEED)
                        // A touch on the open menu's own edge is not a touch outside it.
                        .then(if (menuOpen) Modifier.pointerInput(Unit) { detectTapGestures() } else Modifier)
                        .padding(MENU_PAD.dp),
                ) {
                    models.forEachIndexed { index, item ->
                        MenuRow(
                            model = item,
                            selected = index == selected,
                            lit = flashed == index,
                            enabled = menuOpen,
                            onClick = { choose(index, byHand = true) },
                        )
                    }
                }
            }
        }

        // While autoPlay runs, a touch stops it; on the text it also hands the field to the keyboard.
        if (playing) {
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tap ->
                            takeOver()
                            val x = tap.x.toDp().value
                            val y = tap.y.toDp().value
                            val onText = x >= BOX_X && x < BOX_X + BOX_WIDTH &&
                                y >= BOX_TOP && y < BOX_TOP + BOX_HEIGHT - ROW_HEIGHT
                            if (onText) {
                                focusRequester.requestFocus()
                                keyboard?.show()
                            }
                            resume += 1
                        },
                    )
                },
            )
        }
    }
}

/** Three lines of a faux answer: each grows in from the left, and a band of light keeps crossing them. */
@Composable
private fun AnswerLines(generating: Boolean, reducedMotion: Boolean, clock: () -> Float, modifier: Modifier = Modifier) {
    val shown = ANSWER_WIDTHS.indices.map { index ->
        animateFloatAsState(
            targetValue = if (generating) 1f else 0f,
            animationSpec = if (generating) {
                tween(ANSWER_GROW_MS, delayMillis = ANSWER_AT_MS + index * ANSWER_STAGGER_MS, easing = EaseOut)
            } else {
                FadeSpec
            },
            label = "answer",
        )
    }
    val height = ANSWER_WIDTHS.size * ANSWER_LINE + (ANSWER_WIDTHS.size - 1) * ANSWER_GAP
    Canvas(modifier.size(ANSWER_WIDTHS.max().dp, height.dp)) {
        surface(0.dp) { _, _, _, _ ->
            val sweep = if (reducedMotion) 0.5f else (clock() / SHIMMER_SWEEP) % 1f
            val centre = BAND_FROM + BAND_TRAVEL * sweep
            ANSWER_WIDTHS.forEachIndexed { index, width ->
                val grown = shown[index].value
                if (grown <= 0f) return@forEachIndexed
                val top = index * (ANSWER_LINE + ANSWER_GAP)
                val stretch = if (reducedMotion) 1f else ANSWER_SCALE + (1f - ANSWER_SCALE) * grown
                scale(scaleX = stretch, scaleY = 1f, pivot = Offset(0f, top + ANSWER_LINE / 2f)) {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Answer, AnswerLight, Answer),
                            start = Offset((centre - BAND_HALF) * width, 0f),
                            end = Offset((centre + BAND_HALF) * width, 0f),
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(width, ANSWER_LINE),
                        cornerRadius = CornerRadius(ANSWER_LINE / 2f),
                        alpha = grown,
                    )
                }
            }
        }
    }
}

/**
 * The model's name and a chevron. The name rolls upwards when it changes ([roll] 0…1 carries
 * [leaving] out and [arriving] in); the chevron turns over and both brighten with [tint] while the
 * menu is open.
 */
@Composable
private fun ModelLabel(leaving: String, arriving: String, roll: () -> Float, tint: () -> Float, reducedMotion: Boolean) {
    val color = remember(tint) { ColorProducer { lerp(Control, Ink, tint()) } }
    Row(
        Modifier.clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MODEL_GAP.dp),
    ) {
        // A fixed slot: the name on its way out stays right-aligned beside the one coming in.
        Box(Modifier.size(MODEL_NAME.dp, ROW_HEIGHT.dp).clipToBounds(), contentAlignment = Alignment.CenterEnd) {
            BasicText(
                text = leaving,
                style = ModelStyle,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.wrapContentWidth(Alignment.End, unbounded = true).graphicsLayer {
                    val rolled = roll()
                    alpha = 1f - rolled
                    if (!reducedMotion) translationY = (-ROLL * rolled).dp.toPx()
                },
            )
            BasicText(
                text = arriving,
                style = ModelStyle,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.wrapContentWidth(Alignment.End, unbounded = true).graphicsLayer {
                    val rolled = roll()
                    alpha = rolled
                    if (!reducedMotion) translationY = (ROLL * (1f - rolled)).dp.toPx()
                },
            )
        }
        ChevronIcon(color = { lerp(Control, Ink, tint()) }, modifier = Modifier.graphicsLayer { rotationZ = 180f * tint() })
    }
}

/** A square inside an open ring that turns slowly while the answer is on its way. */
@Composable
private fun StopRing(turns: () -> Float) {
    Canvas(Modifier.size(BUTTON_HIT.dp)) {
        surface(0.dp) { _, _, w, h ->
            drawArc(
                color = Coral,
                startAngle = turns() * 360f - 90f,
                sweepAngle = RING_ARC * 360f,
                useCenter = false,
                topLeft = Offset((w - RING_SIZE) / 2f, (h - RING_SIZE) / 2f),
                size = Size(RING_SIZE, RING_SIZE),
                style = Stroke(RING_STROKE, cap = StrokeCap.Round),
            )
            drawRoundRect(
                color = Coral,
                topLeft = Offset((w - STOP_SIZE) / 2f, (h - STOP_SIZE) / 2f),
                size = Size(STOP_SIZE, STOP_SIZE),
                cornerRadius = CornerRadius(STOP_RADIUS),
            )
        }
    }
}

/** A row of the menu: it lights up while it is pressed, or when autoPlay is about to choose it. */
@Composable
private fun MenuRow(model: ModelPickerPromptModel, selected: Boolean, lit: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val light = animateFloatAsState(
        targetValue = if (pressed || lit) ROW_LIGHT else 0f,
        animationSpec = tween(ROW_LIGHT_MS, easing = EaseOut),
        label = "light",
    )
    // The check moves to the chosen row while the menu fades away.
    val check = animateFloatAsState(if (selected) 1f else 0f, tween(MENU_CLOSE_MS, easing = EaseOut), label = "check")
    Row(
        Modifier
            .fillMaxWidth()
            .height(MENU_ROW.dp)
            .drawBehind { drawRoundRect(Color.White, cornerRadius = CornerRadius(ROW_RADIUS.dp.toPx()), alpha = light.value) }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(model.name, style = MenuNameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(model.detail, style = MenuDetailStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(20.dp))
        CheckIcon(Coral, Modifier.graphicsLayer { alpha = check.value })
    }
}

/**
 * A tappable part of the block: it dips while pressed. One that is not [enabled] takes no touches
 * at all, so the button under it in the same slot can.
 */
@Composable
private fun PressButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    state: String? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    val touch = if (enabled) {
        Modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics {
                if (label != null) contentDescription = label
                if (state != null) stateDescription = state
            }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .then(touch),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
