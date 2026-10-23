package com.ngocdevv.nativecook.blocks.inputs.dockingpromptcard

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 350f
private const val HEIGHT = 440f
private const val RADIUS = 32f
private const val MARGIN = 16f

// The card in the middle of the panel, and docked at its bottom as a capsule.
private const val CARD_X = 18f
private const val CARD_Y = 114f
private const val CARD_WIDTH = 314f
private const val CARD_HEIGHT = 200f
private const val CARD_RADIUS = 20f
private const val DOCK_X = 14f
private const val DOCK_Y = 374f
private const val DOCK_WIDTH = 322f
private const val DOCK_HEIGHT = 52f
private const val DOCK_RADIUS = 26f

// The field inside the resting card. Docked, it fills the capsule.
private const val FIELD_X = 16f
private const val FIELD_Y = 100f
private const val FIELD_WIDTH = 282f
private const val FIELD_HEIGHT = 84f
private const val FIELD_RADIUS = 12f
private const val HEADING_Y = 50f
private const val HEADING_GAP = 5f

/** Centre of the plus and of the action button, from the field's bottom corners. */
private const val ICON_INSET = 22f

/** The box a button in the field is touched in. */
private const val BUTTON = 36f

private const val FONT = 15f
private const val LINE = 20f
private const val GAP = 4f
private const val TEXT_X = 14f
private const val TEXT_Y = 12f
private const val DOCK_TEXT_X = 50f
private const val TEXT_WIDTH = 222f

/** Room kept for the caret when a long sentence scrolls. */
private const val CARET_ROOM = 4f

private const val BUBBLE_TOP = 20f
private const val BUBBLE_MAX = 260f
private const val BUBBLE_PAD_X = 14f
private const val BUBBLE_PAD_Y = 8f
private const val BUBBLE_HEIGHT = LINE + BUBBLE_PAD_Y * 2f

private val Panel = Color(0xFF262626)
private val Glass = rgba(18, 14, 30, 0.4f)
private val Violet = Color(0xFF6A2BD9)
private val Magenta = Color(0xFFD63CFF)
private val Tint = Color(0xFFA77BFF)
private val BubbleEdge = Color(0xFF9A7BFF)

// autoPlay (seconds). Keys land `KEY` apart on average, stretched by `RHYTHM` in turn.
private const val TYPE_AT = 0.8f
private const val KEY = 0.06f
private val RHYTHM = floatArrayOf(1f, 0.72f, 1.3f, 0.85f, 1.12f, 0.64f, 1.42f, 0.95f)

/** From the last key to the tap on the arrow. */
private const val SEND_AFTER = 0.5f
private const val LOOP = 9f

/** After the field is left empty, the script waits this long before it starts again. */
private const val RESUME_AFTER = 3f

// What follows a send, counted from it.
private const val THINK_AFTER = 0.5f
private const val BACK_AFTER = 2.9f

/** From the card starting to rise to its heading starting to resolve. */
private const val REVEAL_AFTER = 0.35f

private const val BLINK = 0.53f
private const val PRESS = 0.12f
private const val FADE = 0.25f
private const val TINT_HOLD = 0.45f
private const val TINT_FADE = 0.4f
private const val PULSE = 0.45f
private const val PULSE_STAGGER = 0.15f
private const val PRESSED = 0.94f

/** The heading resolves over `REVEAL` seconds; each letter takes `REVEAL_SPAN` of that. */
private const val REVEAL = 0.9f
private const val REVEAL_SPAN = 0.35f
private const val REVEAL_BLUR = 5f
private const val REVEAL_SHIFT = 6f

/** The bubble rises this far above a straight path, and swells by this much, half way through its flight. */
private const val FLY_ARC = 18f
private const val FLY_SCALE = 0.05f

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private val Dock = physical(stiffness = 150f, damping = 19f)
private val Fly = physical(stiffness = 130f, damping = 17f)
private val Swap = physical(stiffness = 500f, damping = 24f)
private val Pop = physical(stiffness = 420f, damping = 22f)
private val Fade = tween<Float>(ms(FADE), easing = EaseOut)
private val Resolve = tween<Float>(ms(REVEAL), easing = LinearEasing)

/**
 * A spring of mass 1 given by its stiffness and damping, the numbers every stack of this block shares.
 * It drives a share between 0 and 1 that is stretched over hundreds of dp, so it settles much closer
 * to its target than Compose's default before it is snapped onto it.
 */
private fun physical(stiffness: Float, damping: Float) = spring(
    dampingRatio = damping / (2f * sqrt(stiffness)),
    stiffness = stiffness,
    visibilityThreshold = 0.0005f,
)

private fun ms(seconds: Float) = (seconds * 1000f).roundToInt()

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

/** How a streak fades from its centre to its rim: steep at first, then a long soft tail. */
private val AuroraFalloff = listOf(0f to 1f, 0.25f to 0.72f, 0.5f to 0.4f, 0.7f to 0.16f, 0.87f to 0.04f, 1f to 0f)

private class DockingPromptCardBlob(
    val x: Float,
    val y: Float,
    val rx: Float,
    val ry: Float,
    /** Degrees its long axis is turned clockwise. */
    val tilt: Float,
    /** How far its centre swings each way. */
    val dx: Float,
    val dy: Float,
    /** Whole circles per loop, so the loop closes. */
    val turns: Float,
    val phase: Float,
    color: Color,
    alpha: Float,
) {
    /** The falloff across a circle of radius 1, which is drawn stretched to the streak's size. */
    val brush = Brush.radialGradient(
        *AuroraFalloff.map { (at, share) -> at to color.copy(alpha = alpha * share) }.toTypedArray(),
        center = Offset.Zero,
        radius = 1f,
    )
}

private val AuroraBlobs = listOf(
    DockingPromptCardBlob(70f, 70f, 250f, 135f, -35f, 24f, 16f, 1f, 0f, Color(0xFF7A1FFF), 0.9f),
    DockingPromptCardBlob(300f, 350f, 240f, 125f, -30f, -20f, 18f, 1f, 1.9f, Color(0xFFC81FFF), 0.8f),
    DockingPromptCardBlob(280f, 90f, 170f, 110f, 25f, 16f, -20f, 2f, 4.1f, Color(0xFF4B1FD6), 0.65f),
    DockingPromptCardBlob(60f, 410f, 170f, 90f, 20f, 18f, 12f, 1f, 3.0f, Color(0xFFE040FB), 0.45f),
    DockingPromptCardBlob(175f, 225f, 210f, 90f, -40f, 14f, -14f, 1f, 5.2f, Color(0xFF5A14C8), 0.5f),
)

/** The magenta glow at the card's bottom edge, across a circle of radius 1. */
private val GlowBrush = Brush.radialGradient(
    listOf(Magenta.copy(alpha = 0.8f), Magenta.copy(alpha = 0f)),
    center = Offset.Zero,
    radius = 1f,
)

// Icons, as outlines in a 16 dp box (the chevron in 10 dp).
private val ChevronIcon = outline("M2.5 3.75 L5 6.25 L7.5 3.75")
private val PlusIcon = outline("M8 3.25 L8 12.75 M3.25 8 L12.75 8")
private val ArrowIcon = outline("M8 12.25 L8 3.75 M4.5 7.25 L8 3.75 L11.5 7.25")
private val MicIcon = outline(
    "M8 2 C9.1 2 10 2.9 10 4 L10 7.5 C10 8.6 9.1 9.5 8 9.5 C6.9 9.5 6 8.6 6 7.5 L6 4 C6 2.9 6.9 2 8 2 Z " +
        "M4 7.5 C4 9.71 5.79 11.5 8 11.5 C10.21 11.5 12 9.71 12 7.5 M8 11.5 L8 13.75 M6 13.75 L10 13.75",
)

private fun outline(data: String) = PathParser().parsePathString(data).toPath()

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun cardText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val SentenceStyle = cardText(FONT, LINE, Color.White)
private val PlaceholderStyle = cardText(FONT, LINE, Color.White.copy(alpha = 0.42f))
private val AssistantStyle = cardText(12.5f, 20f, Color.White, FontWeight.Medium)
private val HeadingStyle = cardText(20f, 28f, Color.White)

// The real field shows nothing of its own: no text, no caret, no selection handle.
private val FieldStyle = SentenceStyle.copy(color = Color.Transparent)
private val HiddenSelection = TextSelectionColors(handleColor = Color.Transparent, backgroundColor = Color.Transparent)

/** Where the sentence starts inside the panel while the card is at [p]. */
private fun textOrigin(p: Float) = Offset(
    lerp(CARD_X, DOCK_X, p) + lerp(FIELD_X, 0f, p) + lerp(TEXT_X, DOCK_TEXT_X, p),
    lerp(CARD_Y, DOCK_Y, p) + lerp(FIELD_Y, 0f, p) + lerp(TEXT_Y, (DOCK_HEIGHT - LINE) / 2f, p),
)

private fun cardRadius(p: Float) = max(0f, lerp(CARD_RADIUS, DOCK_RADIUS, p))

/** A Gaussian blur of [sigma] pixels where the platform has one (API 31); nothing below that. */
private fun blurOf(sigma: Float): RenderEffect? {
    // Half a pixel is the least Android blurs by, and it cannot be seen.
    if (sigma <= 0.5f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val radius = sigmaToRadius(sigma)
    return BlurEffect(radius, radius, TileMode.Decal)
}

/** Sizes the content from numbers (dp) read while it is measured, so an animated value reruns layout, not composition. */
private fun Modifier.sized(size: () -> Size) = layout { measurable, _ ->
    val wanted = size()
    val placeable = measurable.measure(
        Constraints.fixed(max(0, wanted.width.dp.roundToPx()), max(0, wanted.height.dp.roundToPx())),
    )
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** Moves the content by numbers (dp) read while its layer is updated, so an animated value redraws without a new layout. */
private fun Modifier.shift(by: () -> Offset) = graphicsLayer {
    val offset = by()
    translationX = offset.x.dp.toPx()
    translationY = offset.y.dp.toPx()
}

/** The card's glass, in dp: two translucent fills and a magenta glow along the bottom edge. */
private fun DrawScope.drawGlass() {
    val w = size.width / density
    val h = size.height / density
    scale(density, density, pivot = Offset.Zero) {
        drawRect(Glass, size = Size(w, h))
        drawRect(Color.White, size = Size(w, h), alpha = 0.05f)
        withTransform({
            translate(w * 0.56f, h + 4f)
            scale(w * 0.62f, min(72f, h * 0.62f), pivot = Offset.Zero)
        }) {
            drawCircle(GlowBrush, radius = 1f, center = Offset.Zero)
        }
    }
}

/** The card's 1 dp border, violet at the top and magenta at the bottom, kept inside its edge. */
private fun DrawScope.drawRim(radius: Float) {
    val w = size.width / density
    val h = size.height / density
    scale(density, density, pivot = Offset.Zero) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Violet.copy(alpha = 0.8f), Magenta.copy(alpha = 0.95f)), startY = 0f, endY = h,
            ),
            topLeft = Offset(0.5f, 0.5f),
            size = Size(w - 1f, h - 1f),
            cornerRadius = CornerRadius(max(0f, radius - 0.5f)),
            style = Stroke(1f),
        )
    }
}

/** The animated numbers of the block: every position, size and opacity on screen is read from them. */
private class DockingPromptCardMotion(private val scope: CoroutineScope) {
    /** 0 with the card in the middle of the panel, 1 with it docked at the bottom. */
    val dock = Animatable(0f)

    /** The bubble's way from the field (0) to the top right of the panel (1). */
    val fly = Animatable(0f)

    /** How far the heading has resolved, from left to right. */
    val reveal = Animatable(1f)
    val think = Animatable(0f)

    /** Opacity of the bubble while it fades. */
    val bubble = Animatable(0f)

    /** Starts an animation that outlives the step asking for it; a later one on the same number takes it over. */
    fun move(value: Animatable<Float, AnimationVector1D>, to: Float, spec: AnimationSpec<Float>) {
        scope.launch { value.animateTo(to, spec) }
    }

    /** Back to the resting card at once, whatever was going on. */
    fun rest() {
        scope.launch {
            think.snapTo(0f)
            bubble.snapTo(0f)
            fly.snapTo(0f)
            dock.snapTo(0f)
            reveal.snapTo(1f)
        }
    }
}

/**
 * An assistant prompt on a glass card over a drifting aurora. Each word is tinted violet as it is
 * typed. On send the sentence lifts out of the field and flies up into a message bubble while the
 * card shrinks to its field and docks at the bottom of the panel; after a moment of "thinking" the
 * card rises back and its heading resolves again from left to right.
 *
 * @param value Controlled text. Leave it null and the component keeps its own.
 * @param autoPlay Types the script, sends it, waits for the card to come back and starts over.
 * @param script The question autoPlay types.
 * @param accent Fill of the message bubble.
 * @param onSend Called with the message when the arrow or the return key is pressed.
 */
@Composable
fun DockingPromptCard(
    value: String? = null,
    autoPlay: Boolean = false,
    assistant: String = "AI Assistant",
    heading: String = "What are you working on?",
    placeholder: String = "Ask me anything",
    script: String = "Help me plan a product launch",
    accent: Color = Color(0xFF5B2BE0),
    onValueChange: ((String) -> Unit)? = null,
    onSend: ((String) -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    onMic: (() -> Unit)? = null,
    onAssistant: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val motion = remember { DockingPromptCardMotion(scope) }
    val controlled = value != null

    var inner by remember { mutableStateOf("") }
    // What the keyboard last reported, kept for its composing region.
    var field by remember { mutableStateOf(TextFieldValue()) }
    var focused by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(autoPlay && !controlled) }
    var caretOn by remember { mutableStateOf(true) }
    // The word being typed is still tinted.
    var fresh by remember { mutableStateOf(false) }
    // The arrow is held down by the script.
    var pressed by remember { mutableStateOf(false) }
    // A message is on its way: nothing else can be sent until the card is back.
    var busy by remember { mutableStateOf(false) }
    // Counts sends and resets, to start and cancel what follows a send.
    var run by remember { mutableIntStateOf(0) }
    var sent by remember { mutableStateOf("") }
    // Where the sentence stood in the field when it was sent.
    var from by remember { mutableStateOf(Offset.Zero) }
    // The bubble stands in for the words from the very frame the field empties.
    var bubbleShown by remember { mutableStateOf(false) }
    // Height of the bubble in pixels: the thinking dots hang under it.
    var bubbleHeight by remember { mutableIntStateOf(0) }

    // The effects below outlive many recompositions; they reach the newest text and callbacks through these.
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnSend by rememberUpdatedState(onSend)

    fun current() = latestValue ?: inner

    fun edit(new: String) {
        if (new == current()) return
        // A new word is tinted in the frame it appears; the effect on the text times the fade.
        fresh = new.isNotEmpty()
        if (latestValue == null) inner = new
        latestOnValueChange?.invoke(new)
    }

    /** Back to the resting card at once, whatever was going on. */
    fun reset() {
        busy = false
        pressed = false
        bubbleShown = false
        motion.rest()
        run += 1
        edit("")
    }

    fun send(byHand: Boolean) {
        val message = current().split(" ").filter { it.isNotEmpty() }.joinToString(" ")
        if (focused) focusManager.clearFocus()
        if (message.isEmpty() || busy) return
        busy = true
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        latestOnSend?.invoke(message)
        // The bubble takes the place of the words before anything moves.
        from = textOrigin(motion.dock.targetValue)
        sent = message
        pressed = false
        bubbleShown = true
        edit("")
        run += 1
    }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        // Every step is timed from the start of its loop, so the period does not drift.
        var origin = System.nanoTime()

        /** Waits for [at] seconds into the loop; false once a touch has taken the field over. */
        suspend fun until(at: Float): Boolean {
            val wait = origin + (at * 1e9).toLong() - System.nanoTime()
            if (wait > 0) delay(wait / 1_000_000)
            return playing
        }

        while (true) {
            var at = TYPE_AT
            var typed = ""
            for (character in script) {
                if (!until(at)) return@LaunchedEffect
                typed += character
                edit(typed)
                at += KEY * RHYTHM[(typed.length - 1) % RHYTHM.size]
            }
            at += SEND_AFTER
            if (!until(at - PRESS)) return@LaunchedEffect
            pressed = true
            if (!until(at)) return@LaunchedEffect
            send(byHand = false)
            // The card is back, and the heading resolved, before the loop is over.
            origin += (max(LOOP, at + BACK_AFTER + FADE + REVEAL_AFTER + REVEAL + TYPE_AT) * 1e9).toLong()
            if (!until(0f)) return@LaunchedEffect
        }
    }

    // What a send sets off: the flight and the docking, the thinking dots, and the way back.
    LaunchedEffect(run) {
        if (!busy) return@LaunchedEffect
        val mine = run

        /** Waits [seconds]; false once a reset or another send has replaced this one. */
        suspend fun pause(seconds: Float): Boolean {
            delay(ms(seconds).toLong())
            return run == mine
        }

        motion.move(motion.bubble, 1f, if (reducedMotion) Fade else snap())
        // One frame with the bubble over the words, so the flight starts from there.
        if (!pause(0.02f)) return@LaunchedEffect
        motion.move(motion.fly, 1f, if (reducedMotion) snap() else Fly)
        motion.move(motion.dock, 1f, if (reducedMotion) snap() else Dock)
        motion.move(motion.reveal, 0f, Fade)
        if (!pause(THINK_AFTER)) return@LaunchedEffect
        motion.move(motion.think, 1f, if (reducedMotion) Fade else Pop)
        if (!pause(BACK_AFTER - THINK_AFTER)) return@LaunchedEffect
        motion.move(motion.think, 0f, Fade)
        bubbleShown = false
        motion.move(motion.bubble, 0f, Fade)
        if (!pause(FADE)) return@LaunchedEffect
        motion.move(motion.dock, 0f, if (reducedMotion) snap() else Dock)
        motion.move(motion.fly, 0f, if (reducedMotion) snap() else Fly)
        if (!pause(REVEAL_AFTER)) return@LaunchedEffect
        motion.move(motion.reveal, 1f, if (reducedMotion) Fade else Resolve)
        busy = false
    }

    LaunchedEffect(focused, busy) {
        // The script resumes once the field has been left empty, and the card is back, for a while.
        if (!autoPlay || controlled || focused || playing || busy) return@LaunchedEffect
        delay(ms(RESUME_AFTER).toLong())
        if (current().isEmpty()) playing = true
    }

    val text = value ?: inner

    LaunchedEffect(text) {
        // The newest word keeps its tint until the keys have stopped for a moment.
        fresh = text.isNotEmpty()
        delay(ms(TINT_HOLD).toLong())
        fresh = false
    }

    LaunchedEffect(text, focused, playing) {
        // The caret stays solid while keys arrive and blinks once they stop.
        caretOn = true
        if (!focused && !playing) return@LaunchedEffect
        while (true) {
            delay(ms(BLINK).toLong())
            caretOn = !caretOn
        }
    }

    val words = text.split(" ").filter { it.isNotEmpty() }
    val ready = text.isNotBlank()
    val caret = focused || (playing && words.isNotEmpty())
    val spaced = text.endsWith(" ")
    val shown = if (field.text == text) field else TextFieldValue(text, TextRange(text.length))

    val blink = animateFloatAsState(if (caretOn) 1f else 0f, tween(80, easing = EaseOut), label = "caret")
    val hint = animateFloatAsState(
        targetValue = if (words.isEmpty()) 1f else 0f,
        animationSpec = if (words.isEmpty()) Fade else snap(),
        label = "placeholder",
    )
    val swap = animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = if (reducedMotion) tween(150, easing = EaseOut) else Swap,
        label = "swap",
    )
    val press = animateFloatAsState(if (pressed) 1f else 0f, tween(ms(PRESS), easing = EaseOut), label = "press")

    // The label and the heading are gone before the card is half way down.
    val fade = { clamp01(1f - motion.dock.value * 2.5f) }
    val panel = RoundedCornerShape(RADIUS.dp)

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .clip(panel)
            .background(Panel)
            .border(1.dp, Color.White.copy(alpha = 0.07f), panel),
    ) {
        DockingPromptCardAurora(reducedMotion, Modifier.matchParentSize())

        // The glass card, on its way from the middle of the panel to its dock as `dock` goes from 0 to 1.
        Box(
            Modifier
                .sized {
                    val p = motion.dock.value
                    Size(lerp(CARD_WIDTH, DOCK_WIDTH, p), lerp(CARD_HEIGHT, DOCK_HEIGHT, p))
                }
                .graphicsLayer {
                    val p = motion.dock.value
                    translationX = lerp(CARD_X, DOCK_X, p).dp.toPx()
                    translationY = lerp(CARD_Y, DOCK_Y, p).dp.toPx()
                    shape = RoundedCornerShape(cardRadius(p).dp.toPx())
                    clip = true
                }
                .drawWithContent {
                    drawGlass()
                    drawContent()
                    drawRim(cardRadius(motion.dock.value))
                },
        ) {
            DockingPromptCardPressable(
                onClick = { onAssistant?.invoke() },
                modifier = Modifier.offset(18.dp, 14.dp).graphicsLayer { alpha = fade() },
            ) {
                Row(
                    Modifier.height(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(assistant, style = AssistantStyle, maxLines = 1, softWrap = false)
                    DockingPromptCardIcon(ChevronIcon, box = 10f, width = 1.4f, color = Color.White)
                }
            }

            Box(
                Modifier.offset(y = HEADING_Y.dp).fillMaxWidth().height(28.dp).graphicsLayer { alpha = fade() },
                contentAlignment = Alignment.Center,
            ) {
                DockingPromptCardHeading(heading, reducedMotion, reveal = { motion.reveal.value })
            }

            // The field: a two-row box in the card, a slim capsule that fills it once docked.
            Box(
                Modifier
                    .sized {
                        val p = motion.dock.value
                        Size(lerp(FIELD_WIDTH, DOCK_WIDTH, p), lerp(FIELD_HEIGHT, DOCK_HEIGHT, p))
                    }
                    .shift {
                        val p = motion.dock.value
                        Offset(lerp(FIELD_X, 0f, p), lerp(FIELD_Y, 0f, p))
                    }
                    .drawBehind {
                        val p = motion.dock.value
                        drawRoundRect(
                            color = Color.White,
                            cornerRadius = CornerRadius(max(0f, lerp(FIELD_RADIUS, DOCK_RADIUS, p)).dp.toPx()),
                            alpha = clamp01(lerp(0.16f, 0.08f, p)),
                        )
                    }
                    .pointerInput(keyboard) {
                        detectTapGestures {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                    },
            ) {
                // The words on one line, the caret after the last one, and the real field that owns the text.
                Box(
                    Modifier
                        .shift {
                            val p = motion.dock.value
                            Offset(lerp(TEXT_X, DOCK_TEXT_X, p), lerp(TEXT_Y, (DOCK_HEIGHT - LINE) / 2f, p))
                        }
                        .size(TEXT_WIDTH.dp, LINE.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // It leaves at once when a letter arrives and fades back in when the field empties.
                    Box(
                        Modifier.graphicsLayer { alpha = if (words.isEmpty()) hint.value else 0f },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicText(placeholder, style = PlaceholderStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        DockingPromptCardCaret { if (caret && words.isEmpty()) blink.value else 0f }
                    }

                    DockingPromptCardLine(
                        caretShift = if (spaced) GAP else 0.5f,
                        modifier = Modifier.matchParentSize().clipToBounds(),
                    ) {
                        words.forEachIndexed { index, word ->
                            key(index) { DockingPromptCardWord(word, tinted = index == words.lastIndex && fresh) }
                        }
                        if (words.isNotEmpty()) DockingPromptCardCaret { if (caret) blink.value else 0f }
                    }

                    // The real field: it takes the keyboard, the words above are drawn from its text.
                    CompositionLocalProvider(LocalTextSelectionColors provides HiddenSelection) {
                        BasicTextField(
                            value = shown,
                            onValueChange = {
                                // The drawn caret is always after the last word, so the real one stays there too.
                                field = it.copy(selection = TextRange(it.text.length))
                                edit(it.text)
                            },
                            modifier = Modifier
                                .matchParentSize()
                                .focusRequester(focusRequester)
                                .onFocusChanged {
                                    focused = it.isFocused
                                    // A touch takes the field over from the script.
                                    if (it.isFocused && playing) {
                                        playing = false
                                        reset()
                                    }
                                }
                                .semantics { contentDescription = placeholder },
                            textStyle = FieldStyle,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(onSend = { send(byHand = true) }),
                            singleLine = true,
                            cursorBrush = SolidColor(Color.Transparent),
                        )
                    }
                }

                DockingPromptCardPressable(
                    onClick = { onAdd?.invoke() },
                    modifier = Modifier.shift {
                        val p = motion.dock.value
                        val inset = lerp(ICON_INSET, DOCK_HEIGHT / 2f, p)
                        Offset(inset - BUTTON / 2f, lerp(FIELD_HEIGHT, DOCK_HEIGHT, p) - inset - BUTTON / 2f)
                    },
                ) {
                    Box(Modifier.size(BUTTON.dp), contentAlignment = Alignment.Center) {
                        DockingPromptCardIcon(PlusIcon, box = 16f, width = 1.5f, color = Color.White.copy(alpha = 0.85f))
                    }
                }

                // A microphone while the field is empty, a white disc with an arrow once there is something to send.
                DockingPromptCardPressable(
                    onClick = { if (ready) send(byHand = true) else onMic?.invoke() },
                    modifier = Modifier.shift {
                        val p = motion.dock.value
                        val inset = lerp(ICON_INSET, DOCK_HEIGHT / 2f, p)
                        Offset(
                            lerp(FIELD_WIDTH, DOCK_WIDTH, p) - inset - BUTTON / 2f,
                            lerp(FIELD_HEIGHT, DOCK_HEIGHT, p) - inset - BUTTON / 2f,
                        )
                    },
                    label = if (ready) "Send" else "Dictate",
                ) {
                    Box(Modifier.size(BUTTON.dp), contentAlignment = Alignment.Center) {
                        DockingPromptCardIcon(
                            MicIcon,
                            box = 16f,
                            width = 1.4f,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.graphicsLayer {
                                val s = swap.value
                                val scale = if (reducedMotion) 1f else lerp(1f, 0.5f, s)
                                scaleX = scale
                                scaleY = scale
                                alpha = clamp01(1f - s)
                            },
                        )
                        Box(
                            Modifier
                                .graphicsLayer {
                                    val s = swap.value
                                    val held = lerp(1f, 0.86f, press.value)
                                    val scale = if (reducedMotion) held else lerp(0.5f, held, s)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = clamp01(s)
                                }
                                .size(28.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            DockingPromptCardIcon(ArrowIcon, box = 16f, width = 1.8f, color = Color(0xFF1B1626))
                        }
                    }
                }
            }
        }

        DockingPromptCardThinking(
            reducedMotion,
            Modifier.graphicsLayer {
                val on = motion.think.value
                // It grows out of its leading edge.
                val scale = if (reducedMotion) 1f else lerp(0.8f, 1f, on)
                transformOrigin = TransformOrigin(0f, 0.5f)
                scaleX = scale
                scaleY = scale
                alpha = clamp01(on)
                translationX = MARGIN.dp.toPx()
                translationY = (BUBBLE_TOP + 12f).dp.toPx() + bubbleHeight
            },
        )

        // The sent message: it leaves the field as bare text and lands at the top right as a bubble.
        BasicText(
            text = sent,
            style = SentenceStyle,
            modifier = Modifier
                .graphicsLayer {
                    val p = motion.fly.value
                    val lift = sin(PI.toFloat() * clamp01(p))
                    val swell = 1f + FLY_SCALE * lift
                    scaleX = swell
                    scaleY = swell
                    translationX = lerp(from.x - BUBBLE_PAD_X, WIDTH - MARGIN - size.width.toDp().value, p).dp.toPx()
                    translationY = (lerp(from.y - BUBBLE_PAD_Y, BUBBLE_TOP, p) - lift * FLY_ARC).dp.toPx()
                    // It appears in place of the words at once, and fades when the card comes back.
                    alpha = if (bubbleShown && !reducedMotion) 1f else motion.bubble.value
                }
                .onSizeChanged { bubbleHeight = it.height }
                .drawBehind {
                    val fill = clamp01((clamp01(motion.fly.value) - 0.15f) / 0.6f)
                    val radius = (BUBBLE_HEIGHT / 2f).dp.toPx()
                    val edge = 1.dp.toPx()
                    drawRoundRect(accent, cornerRadius = CornerRadius(radius), alpha = fill)
                    drawRoundRect(
                        color = BubbleEdge,
                        topLeft = Offset(edge / 2f, edge / 2f),
                        size = Size(size.width - edge, size.height - edge),
                        cornerRadius = CornerRadius(radius - edge / 2f),
                        style = Stroke(edge),
                        alpha = 0.7f * fill,
                    )
                }
                .widthIn(max = BUBBLE_MAX.dp)
                .padding(horizontal = BUBBLE_PAD_X.dp, vertical = BUBBLE_PAD_Y.dp)
                .heightIn(min = LINE.dp),
        )
    }
}

/** Five soft streaks of violet and magenta, each circling slowly once or twice per loop. */
@Composable
private fun DockingPromptCardAurora(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    // Seconds into the loop; the streaks stand still under reduced motion.
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        val started = withFrameNanos { it }
        while (true) {
            withFrameNanos { time = ((it - started) / 1e9 % LOOP).toFloat() }
        }
    }
    Canvas(modifier) {
        scale(density, density, pivot = Offset.Zero) {
            for (blob in AuroraBlobs) {
                val angle = TAU * blob.turns * time / LOOP + blob.phase
                withTransform({
                    translate(blob.x + blob.dx * sin(angle), blob.y + blob.dy * cos(angle))
                    rotate(blob.tilt, pivot = Offset.Zero)
                    scale(blob.rx, blob.ry, pivot = Offset.Zero)
                }) {
                    drawCircle(blob.brush, radius = 1f, center = Offset.Zero)
                }
            }
        }
    }
}

/** The heading, letter by letter: each one fades, sharpens and slides in a little after the one before. */
@Composable
private fun DockingPromptCardHeading(heading: String, reducedMotion: Boolean, reveal: () -> Float) {
    val words = remember(heading) { heading.split(" ").filter { it.isNotEmpty() } }
    val count = max(1, words.sumOf { it.length }).toFloat()
    Row(
        Modifier.wrapContentWidth(unbounded = true).clearAndSetSemantics { contentDescription = heading },
        horizontalArrangement = Arrangement.spacedBy(HEADING_GAP.dp),
    ) {
        // Position of the word's first letter among all letters.
        var start = 0
        for (word in words) {
            val first = start
            Row {
                word.forEachIndexed { index, letter ->
                    val begin = (first + index) / count * (1f - REVEAL_SPAN)
                    BasicText(
                        text = letter.toString(),
                        style = HeadingStyle,
                        modifier = Modifier.graphicsLayer {
                            val p = reveal()
                            val q = if (reducedMotion) clamp01(p) else clamp01((p - begin) / REVEAL_SPAN)
                            alpha = q
                            translationX = ((q - 1f) * REVEAL_SHIFT).dp.toPx()
                            renderEffect = blurOf(((1f - q) * REVEAL_BLUR).dp.toPx())
                        },
                    )
                }
            }
            start += word.length
        }
    }
}

/**
 * One line of words, with the caret (the last child) hung after the last one without taking room.
 * A sentence longer than the line slides left, so its end and the caret stay in view.
 */
@Composable
private fun DockingPromptCardLine(caretShift: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val words = placeables.dropLast(1)
        val spacing = GAP.dp.toPx()
        val total = words.sumOf { it.width } + spacing * max(0, words.size - 1)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            var x = -max(0f, total + CARET_ROOM.dp.toPx() - width)
            for (word in words) {
                word.place(x.roundToInt(), (height - word.height) / 2)
                x += word.width + spacing
            }
            placeables.lastOrNull()?.let { caret ->
                caret.place((x - spacing + caretShift.dp.toPx()).roundToInt(), (height - caret.height) / 2)
            }
        }
    }
}

@Composable
private fun DockingPromptCardWord(word: String, tinted: Boolean) {
    // The tint arrives at once and fades away.
    val color by animateColorAsState(
        targetValue = if (tinted) Tint else Color.White,
        animationSpec = if (tinted) snap() else tween(ms(TINT_FADE), easing = EaseOut),
        label = "tint",
    )
    BasicText(word, style = SentenceStyle, maxLines = 1, softWrap = false, color = { color })
}

@Composable
private fun DockingPromptCardCaret(alpha: () -> Float) {
    Box(
        Modifier
            .size(1.5.dp, 18.dp)
            .graphicsLayer { this.alpha = alpha() }
            .background(Color.White, RoundedCornerShape(0.75.dp)),
    )
}

/** Three dots in a capsule, each bobbing a beat after the one before. */
@Composable
private fun DockingPromptCardThinking(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    Row(
        modifier
            .size(52.dp, 30.dp)
            .background(Color.White.copy(alpha = 0.1f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val beat = pulse.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(ms(PULSE), easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(ms(index * PULSE_STAGGER)),
                ),
                label = "dot",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        alpha = if (reducedMotion) 1f else lerp(0.35f, 1f, beat.value)
                        translationY = (if (reducedMotion) 1f else lerp(1f, -2.5f, beat.value)).dp.toPx()
                    }
                    .background(Color.White, CircleShape),
            )
        }
    }
}

/** An outline drawn in a [box] dp square, stroked [width] dp wide with round caps and joins. */
@Composable
private fun DockingPromptCardIcon(path: Path, box: Float, width: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(box.dp)) {
        scale(size.width / box, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A tappable part of the card: it gives a little while it is pressed. */
@Composable
private fun DockingPromptCardPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable () -> Unit,
) {
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
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
    ) { content() }
}
