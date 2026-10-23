package com.ngocdevv.nativecook.blocks.inputs.rotatingsuggestionsinput

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
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.text.BreakIterator
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PANEL_WIDTH = 350f
private const val PANEL_HEIGHT = 400f
private const val PANEL_RADIUS = 40f
private const val TITLE_Y = 36f
private const val MARK_Y = 108f
private const val MARK_SIZE = 60f

private const val SLOTS = 3
private const val STACK_TOP = 176f
private const val PILL_HEIGHT = 34f
private const val PILL_GAP = 8f
private const val PILL_PAD_LEFT = 11f
private const val PILL_PAD_RIGHT = 14f
private const val ICON_SIZE = 15f
private const val ICON_GAP = 7f
private const val PILL_FONT = 14f

// How far a text travels on its way out and on its way in.
private const val SLIDE = 6f

private const val PLUS_X = 32f
private const val FIELD_X = 56f
private const val FIELD_TOP = 306f
private const val FIELD_WIDTH = 278f
private const val FIELD_HEIGHT = 44f
private const val FIELD_PAD_X = 16f
private const val FIELD_FONT = 15f
private const val SEND_SIZE = 32f
private const val FOOTNOTE_Y = 372f

private const val LIFT_SCALE = 1.05f
private const val LIFT_RISE = 5f
private const val FLOWN_SCALE = 0.6f
private const val RETURN_SCALE = 0.8f
private const val POP_SCALE = 0.4f
private const val SEND_DIP = 0.86f
private const val CLEAR_RISE = 6f
private const val TOUCH_SCALE = 0.96f

// The canvases overflow the pills and the field by this much on every side to fit their shadows.
private val PILL_BLEED = 36.dp
private val FIELD_BLEED = 20.dp

// The picked pill rising off the stack (mass 1).
private const val LIFT_STIFFNESS = 380f
private const val LIFT_DAMPING = 20f

// The send button appearing, and a pill returning to its slot (mass 1).
private const val POP_STIFFNESS = 420f
private const val POP_DAMPING = 24f
private val LIFT_RATIO = LIFT_DAMPING / (2f * sqrt(LIFT_STIFFNESS))
private val POP_RATIO = POP_DAMPING / (2f * sqrt(POP_STIFFNESS))

// A picked suggestion, in seconds from the tap.
private const val LIFT = 0.16
private const val FLY = 0.3

// The first letter lands just before the pill has vanished into the field.
private const val TYPE_AT = 0.4
private const val TYPE_SPAN = 0.8
private const val SLOWEST_KEY = 0.045
private const val PRESS_AT = 1.9
private const val PRESS = 0.12
private const val CLEAR = 0.2

// The autoPlay loop, in seconds from its start.
private const val ROTATE_EVERY = 1.4

// How long one text takes to leave and the next to arrive, and how far apart the pills start.
private const val CHANGE = 0.35
private const val STAGGER = 0.1
private val ROTATE_AT = doubleArrayOf(0.8, 2.2)
private const val PICK_AT = 3.2
private const val PICK_SLOT = 2

// The last rotation, back to the first set.
private const val HOME_AT = 6.85
private const val LOOP = 8.4
private const val RESUME_AFTER = 3.0

// Plain fades: the placeholder, and every state change under reduced motion.
private const val FADE = 0.2

private val ChangeEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

// Slow off the stack, fast into the field.
private val FlyEasing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.4f)

private val Panel = Color(0xFFF7F7F8)
private val Ink = Color(0xFF141416)
private val Mark = Color(0xFFE9E9ED)
private val PillInk = Color(0xFF55555A)
private val PillIcon = Color(0xFF8E8E93)
private val Plus = Color(0xFF8E8E93)
private val Placeholder = Color(0xFF9A9AA0)
private val CaretBlue = Color(0xFF2D6BFF)
private val SendFill = Color(0xFF0E0E10)
private val Footnote = Color(0xFFA4A4AA)
private val FootnoteLink = Color(0xFF6E6E73)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun type(
    size: Float,
    lineHeight: Float,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    textAlign = align,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = type(15f, 20f, Ink, FontWeight.SemiBold, TextAlign.Center)
private val PillStyle = type(PILL_FONT, 18f, PillInk)
private val FieldStyle = type(FIELD_FONT, 20f, Ink)
private val PlaceholderStyle = type(FIELD_FONT, 20f, Placeholder)
private val FootnoteStyle = type(11f, 14f, Footnote, align = TextAlign.Center)

/** Where the picked pill is on its way into the field. */
private enum class Stage { Idle, Lifted, Flown, Away }

private fun ms(seconds: Double) = (seconds * 1000).roundToInt()

/** How long is left until [seconds] after [start]: a step that ran late does not push the ones after it. */
private fun until(start: Long, seconds: Double) = (start - System.nanoTime()).nanoseconds + seconds.seconds

/**
 * Sleeps, then goes on only while [live] holds. A sequence that was replaced is cancelled a frame
 * later, when its effect restarts; if it wakes up in between it must not take another step.
 */
private suspend inline fun pause(time: Duration, live: () -> Boolean) {
    delay(time)
    if (!live()) awaitCancellation()
}

private fun pillTop(slot: Int) = STACK_TOP + slot * (PILL_HEIGHT + PILL_GAP)

/** Where each letter of [text] ends, so typing counts an emoji as one letter and never splits it. */
private fun letterEnds(text: String): List<Int> {
    val letters = BreakIterator.getCharacterInstance().apply { setText(text) }
    return buildList {
        var end = letters.next()
        while (end != BreakIterator.DONE) {
            add(end)
            end = letters.next()
        }
    }
}

/** Solid while letters are landing; after half a second without one it blinks like the system caret. */
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

private fun stageSpec(stage: Stage, still: Boolean, threshold: Float): AnimationSpec<Float> = when {
    still -> tween(ms(FADE), easing = EaseOut)
    stage == Stage.Lifted -> spring(LIFT_RATIO, LIFT_STIFFNESS, threshold)
    stage == Stage.Flown -> tween(ms(FLY), easing = FlyEasing)
    else -> spring(POP_RATIO, POP_STIFFNESS, threshold)
}

/** One pill's way into the field and back: offsets in dp, scaled from its leading edge. */
private class PillMotion {
    val scale = Animatable(1f)
    val dx = Animatable(0f)
    val dy = Animatable(0f)
    val alpha = Animatable(1f)

    /** 0 on the stack, 1 while the pill hangs above it: it deepens the shadow. */
    val lift = Animatable(0f)

    /** The picked pill rises a little, then drops onto the start of the field's text. */
    suspend fun moveTo(stage: Stage, slot: Int, still: Boolean) {
        val flown = stage == Stage.Flown
        val size = when {
            still -> 1f
            stage == Stage.Lifted -> LIFT_SCALE
            flown -> FLOWN_SCALE
            stage == Stage.Away -> RETURN_SCALE
            else -> 1f
        }
        val x = if (flown && !still) FIELD_PAD_X - PILL_PAD_LEFT else 0f
        val y = when {
            still -> 0f
            stage == Stage.Lifted -> -LIFT_RISE
            flown -> FIELD_TOP + FIELD_HEIGHT / 2f - (pillTop(slot) + PILL_HEIGHT / 2f)
            else -> 0f
        }
        val shown = if (flown || stage == Stage.Away) 0f else 1f
        val raised = if (stage == Stage.Lifted) 1f else 0f
        if (stage == Stage.Away) {
            // Invisible by now: it goes back to its slot at once, ready to return.
            scale.snapTo(size)
            dx.snapTo(x)
            dy.snapTo(y)
            alpha.snapTo(shown)
            lift.snapTo(raised)
            return
        }
        coroutineScope {
            launch { scale.animateTo(size, stageSpec(stage, still, 0.001f)) }
            launch { dx.animateTo(x, stageSpec(stage, still, 0.01f)) }
            launch { dy.animateTo(y, stageSpec(stage, still, 0.01f)) }
            launch { alpha.animateTo(shown, stageSpec(stage, still, 0.001f)) }
            launch { lift.animateTo(raised, stageSpec(stage, still, 0.001f)) }
        }
    }
}

/** What a pill shows: its text, the one on its way out, and the width that follows them. */
private class PillText(label: String, width: Float) {
    var shown by mutableStateOf(label)
    var leaving by mutableStateOf<String?>(null)

    /** 0 → 1 while one text leaves and the next arrives. */
    val change = Animatable(1f)
    val width = Animatable(width)
}

// SwiftUI's shadow radius is the blur's sigma. A pill fades from its resting shadow to the lifted one.
private class Shadows {
    val rest = SoftRoundRect(rgba(0, 0, 0, 0.07f), sigma = 3f)
    val lifted = SoftRoundRect(rgba(0, 0, 0, 0.16f), sigma = 10f)
    val field = SoftRoundRect(rgba(0, 0, 0, 0.06f), sigma = 5f)
}

private fun Path.star(x: Float, y: Float, r: Float) {
    moveTo(x, y - r)
    quadraticTo(x, y, x + r, y)
    quadraticTo(x, y, x, y + r)
    quadraticTo(x, y, x - r, y)
    quadraticTo(x, y, x, y - r)
    close()
}

private fun Path.bar(x: Float, y: Float, height: Float) {
    addRoundRect(RoundRect(Rect(Offset(x, y), Size(2.6f, height)), CornerRadius(1.2f)))
}

private object Icons {
    /** Four-pointed sparkle in a 15 × 15 box. */
    val mark = Path().apply { star(7.5f, 7.5f, 7f) }

    /** The same sparkle with a small one at its shoulder. */
    val sparkles = Path().apply {
        star(6.5f, 8.5f, 6f)
        star(12f, 3.2f, 2.8f)
    }

    /** Three rising bars in a 15 × 15 box. */
    val bars = Path().apply {
        bar(1.8f, 8.5f, 5f)
        bar(6.2f, 5.2f, 8.3f)
        bar(10.6f, 1.8f, 11.7f)
    }

    /** Speech bubble outline in a 15 × 15 box. */
    val bubble = Path().apply {
        moveTo(4.6f, 11f)
        lineTo(4.2f, 11f)
        quadraticTo(1.6f, 11f, 1.6f, 8.4f)
        lineTo(1.6f, 4.8f)
        quadraticTo(1.6f, 2.2f, 4.2f, 2.2f)
        lineTo(10.8f, 2.2f)
        quadraticTo(13.4f, 2.2f, 13.4f, 4.8f)
        lineTo(13.4f, 8.4f)
        quadraticTo(13.4f, 11f, 10.8f, 11f)
        lineTo(7.8f, 11f)
        lineTo(4.6f, 13.6f)
        close()
    }

    /** Up arrow in the 32 × 32 send button. */
    val arrow = Path().apply {
        moveTo(16f, 22.2f)
        lineTo(16f, 10.4f)
        moveTo(10.8f, 15.4f)
        lineTo(16f, 10.2f)
        lineTo(21.2f, 15.4f)
    }
}

/**
 * Assistant composer with three suggestion pills stacked above the field. The suggestions rotate by
 * themselves: each pill's text slides down and fades while the next one comes in from just above,
 * the three pills a beat apart, and every pill resizes to fit its new text. Tapping a pill lifts it,
 * drops it into the field, where its text appears letter by letter beside a send button, and sends it.
 *
 * @param value The field's value when the caller owns it; left null, the component keeps its own.
 * @param autoPlay Rotates through the sets and picks one suggestion per loop by itself. Never opens the keyboard.
 * @param suggestions Sets of three suggestions, top to bottom. The stack shows one set at a time.
 * @param footnoteLinks Parts of the footnote drawn darker, as links.
 * @param onSelect Called with a suggestion when its pill is tapped.
 * @param onSend Called with the trimmed message when it is sent: by the send button, the keyboard, or a tapped pill.
 */
@Composable
fun RotatingSuggestionsInput(
    value: String? = null,
    autoPlay: Boolean = false,
    title: String = "Nova",
    placeholder: String = "Ask anything",
    suggestions: List<List<String>> = listOf(
        listOf("Make an image of a rocket", "Top books of the year", "Discuss the theory of relativity"),
        listOf("Show the concept of time travel", "Top books of the year", "Explain weather versus climate"),
        listOf("Capture a serene mountain lake", "Fashion trends for next season", "What are the Seven Wonders?"),
        listOf("Render a superhero in action", "Current environmental issues", "How do neural networks work?"),
    ),
    footnote: String = "By continuing you agree to the Terms and Privacy Policy",
    footnoteLinks: List<String> = listOf("Terms", "Privacy Policy"),
    onValueChange: ((String) -> Unit)? = null,
    onSelect: ((String) -> Unit)? = null,
    onSend: ((String) -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    var draft by remember { mutableStateOf("") }
    var set by remember { mutableIntStateOf(0) }
    var lastRotation by remember { mutableLongStateOf(System.nanoTime()) }
    // True while autoPlay follows its script; otherwise the sets simply rotate.
    var scripted by remember { mutableStateOf(autoPlay) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var pickedByHand by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(Stage.Idle) }
    // The caret drawn while a suggestion is typed into an unfocused field.
    var caretOn by remember { mutableStateOf(false) }
    var lastKey by remember { mutableLongStateOf(0L) }
    // The send button's dip when a picked suggestion sends itself.
    var pressed by remember { mutableStateOf(false) }
    // The sent text is on its way out of the field.
    var clearing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // Each sequence below starts over when its counter changes.
    var run by remember { mutableIntStateOf(0) }
    var picks by remember { mutableIntStateOf(0) }
    var sends by remember { mutableIntStateOf(0) }
    var resume by remember { mutableIntStateOf(0) }

    // The sequences outlive the composition that started them: they read the latest props through these.
    val owned by rememberUpdatedState(value)
    val sets by rememberUpdatedState(suggestions)
    val auto by rememberUpdatedState(autoPlay)
    val changed by rememberUpdatedState(onValueChange)
    val selected by rememberUpdatedState(onSelect)
    val sent by rememberUpdatedState(onSend)

    val text = value ?: draft
    val hasText = text.isNotEmpty()

    fun current() = owned ?: draft

    fun edit(next: String) {
        if (owned == null) draft = next
        changed?.invoke(next)
    }

    fun label(slot: Int) = sets.getOrNull(set)?.getOrNull(slot).orEmpty()

    fun stopScript() {
        if (!scripted) return
        scripted = false
        run += 1
    }

    fun cancelPick() {
        // Only a suggestion that was on its way into the field is taken out again.
        val typing = picked != null
        picked = null
        stage = Stage.Idle
        caretOn = false
        pressed = false
        clearing = false
        picks += 1
        if (typing && current().isNotEmpty()) edit("")
    }

    fun advance(next: Int? = null) {
        lastRotation = System.nanoTime()
        if (sets.isEmpty()) return
        set = next ?: ((set + 1) % sets.size)
    }

    fun pick(slot: Int, byHand: Boolean) {
        val suggestion = label(slot)
        if (picked != null || clearing || suggestion.isEmpty()) return
        if (byHand) {
            stopScript()
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            selected?.invoke(suggestion)
        }
        pickedByHand = byHand
        picked = slot
        stage = Stage.Lifted
        picks += 1
    }

    fun send(byHand: Boolean) {
        val message = current().trim()
        if (clearing || message.isEmpty()) return
        if (byHand) {
            if (picked == null) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            sent?.invoke(message)
        }
        clearing = true
        sends += 1
    }

    // A new autoPlay value starts over from the first set.
    LaunchedEffect(autoPlay) {
        scripted = autoPlay
        cancelPick()
        set = 0
        run += 1
    }

    // Rotation. Under autoPlay every step waits for its own moment after the loop began, so the
    // period stays LOOP; otherwise the sets change every ROTATE_EVERY seconds.
    val lap = run
    LaunchedEffect(lap) {
        val live = { run == lap }
        if (!scripted) {
            while (true) {
                val wait = ROTATE_EVERY - (System.nanoTime() - lastRotation) / 1e9
                when {
                    wait > 0 -> pause(wait.seconds, live)
                    picked == null -> advance()
                    // A picked pill brings the next set itself.
                    else -> pause(0.2.seconds, live)
                }
            }
        }
        if (set != 0) {
            advance(0)
            pause(ROTATE_EVERY.seconds, live)
        }
        var start = System.nanoTime()
        while (true) {
            for (at in ROTATE_AT) {
                pause(until(start, at), live)
                advance()
            }
            pause(until(start, PICK_AT), live)
            pick(PICK_SLOT, byHand = false)
            pause(until(start, HOME_AT), live)
            advance(0)
            // The next loop starts exactly one period after this one, however late its steps ran.
            start += LOOP.seconds.inWholeNanoseconds
            pause(until(start, 0.0), live)
        }
    }

    // A picked pill lifts, drops into the field, types itself there, waits a beat and sends.
    val turn = picks
    LaunchedEffect(turn) {
        val live = { picks == turn }
        val slot = picked ?: return@LaunchedEffect
        if (stage != Stage.Lifted) return@LaunchedEffect
        val suggestion = label(slot)
        val start = System.nanoTime()
        pause(until(start, LIFT), live)
        stage = Stage.Flown
        pause(until(start, TYPE_AT), live)
        caretOn = true
        val letters = letterEnds(suggestion)
        val key = min(SLOWEST_KEY, TYPE_SPAN / letters.size)
        letters.forEachIndexed { index, end ->
            edit(suggestion.take(end))
            lastKey = System.nanoTime()
            pause(until(start, TYPE_AT + key * (index + 1)), live)
        }
        stage = Stage.Away
        pause(until(start, PRESS_AT), live)
        pressed = true
        pause(until(start, PRESS_AT + PRESS), live)
        pressed = false
        caretOn = false
        send(byHand = pickedByHand)
        pause(until(start, PRESS_AT + PRESS + CLEAR + 0.02), live)
        // The next set arrives; the pill returns to its slot with its new text, in step with the others.
        advance()
        pause((slot * STAGGER).seconds, live)
        picked = null
        stage = Stage.Idle
        resume += 1
    }

    // The sent text has left the field: empty it and let the placeholder return.
    val dispatch = sends
    LaunchedEffect(dispatch) {
        if (!clearing) return@LaunchedEffect
        pause(CLEAR.seconds) { sends == dispatch }
        edit("")
        clearing = false
        if (picked == null) resume += 1
    }

    // autoPlay's script comes back once nothing has been touched for a while and the field is empty and unfocused.
    val idle = resume
    LaunchedEffect(idle) {
        if (!auto || scripted || focused) return@LaunchedEffect
        pause(RESUME_AFTER.seconds) { resume == idle }
        if (focused || current().isNotEmpty() || picked != null) return@LaunchedEffect
        scripted = true
        run += 1
    }

    val pop = if (reducedMotion) tween(ms(FADE), easing = EaseOut) else spring(POP_RATIO, POP_STIFFNESS, 0.001f)
    val arm by animateFloatAsState(if (hasText) 1f else 0f, pop, visibilityThreshold = 0.001f, label = "send")
    val dip by animateFloatAsState(if (pressed) SEND_DIP else 1f, tween(ms(PRESS), easing = EaseOut), label = "dip")
    val hint by animateFloatAsState(if (hasText) 0f else 1f, tween(ms(FADE), easing = EaseOut), label = "placeholder")
    // The sent text leaves on a curve; the emptied field is back at once.
    val clear by animateFloatAsState(
        targetValue = if (clearing) 1f else 0f,
        animationSpec = if (clearing) tween(ms(CLEAR), easing = EaseIn) else snap(),
        label = "clear",
    )

    val motions = remember { List(SLOTS) { PillMotion() } }
    val shadows = remember { Shadows() }
    val note = remember(footnote, footnoteLinks) {
        buildAnnotatedString {
            append(footnote)
            for (link in footnoteLinks) {
                val at = if (link.isEmpty()) -1 else footnote.indexOf(link)
                if (at >= 0) addStyle(SpanStyle(color = FootnoteLink), at, at + link.length)
            }
        }
    }

    Box(modifier.size(PANEL_WIDTH.dp, PANEL_HEIGHT.dp).background(Panel, RoundedCornerShape(PANEL_RADIUS.dp))) {
        BasicText(
            text = title,
            style = TitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(y = (TITLE_Y - 10f).dp)
                .size(PANEL_WIDTH.dp, 20.dp)
                .wrapContentHeight(unbounded = true),
        )

        Glyph(
            path = Icons.mark,
            color = Mark,
            size = MARK_SIZE,
            box = 15f,
            modifier = Modifier.offset(((PANEL_WIDTH - MARK_SIZE) / 2f).dp, (MARK_Y - MARK_SIZE / 2f).dp),
        )

        for (slot in 0 until SLOTS) {
            val motion = motions[slot]
            val pose = if (picked == slot) stage else Stage.Idle
            LaunchedEffect(pose, reducedMotion) { motion.moveTo(pose, slot, reducedMotion) }
            Pressable(
                onClick = { pick(slot, byHand = true) },
                enabled = picked == null && !clearing,
                modifier = Modifier
                    .zIndex(if (picked == slot) 1f else 0f)
                    .offset(FIELD_X.dp, pillTop(slot).dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        scaleX = motion.scale.value
                        scaleY = motion.scale.value
                        translationX = motion.dx.value.dp.toPx()
                        translationY = motion.dy.value.dp.toPx()
                        alpha = motion.alpha.value.coerceIn(0f, 1f)
                        // The shadow lies outside the pill's bounds: fading through a buffer would crop it.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
            ) {
                Pill(
                    label = label(slot),
                    slot = slot,
                    lift = { motion.lift.value },
                    hidden = { motion.alpha.value == 0f },
                    shadows = shadows,
                    reducedMotion = reducedMotion,
                )
            }
        }

        Pressable(
            onClick = { onAttach?.invoke() },
            label = "Attach",
            modifier = Modifier.offset((PLUS_X - 22f).dp, FIELD_TOP.dp).size(44.dp),
        ) {
            Box(Modifier.size(15.dp, 1.6.dp).background(Plus, CircleShape))
            Box(Modifier.size(1.6.dp, 15.dp).background(Plus, CircleShape))
        }

        Box(Modifier.offset(FIELD_X.dp, FIELD_TOP.dp).size(FIELD_WIDTH.dp, FIELD_HEIGHT.dp)) {
            Box(Modifier.matchParentSize().bleed(FIELD_BLEED)) {
                Canvas(Modifier.fillMaxSize()) {
                    surface(FIELD_BLEED) { x, y, w, h ->
                        drawIntoCanvas { shadows.field.draw(it.nativeCanvas, x, y + 2f, w, h, h / 2f) }
                        drawRoundRect(Color.White, Offset(x, y), Size(w, h), CornerRadius(h / 2f))
                    }
                }
            }

            BasicText(
                text = placeholder,
                style = PlaceholderStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = FIELD_PAD_X.dp)
                    .graphicsLayer { alpha = hint },
            )

            BasicTextField(
                value = text,
                onValueChange = { edit(it) },
                modifier = Modifier
                    .offset(x = FIELD_PAD_X.dp)
                    .size((FIELD_WIDTH - FIELD_PAD_X - FIELD_HEIGHT).dp, FIELD_HEIGHT.dp)
                    .onFocusChanged { state ->
                        if (state.isFocused != focused) {
                            focused = state.isFocused
                            // A tap on the field takes it over from the script, and from a suggestion on its way in.
                            if (focused && scripted) {
                                stopScript()
                                cancelPick()
                            }
                            resume += 1
                        }
                    }
                    .graphicsLayer {
                        alpha = 1f - clear
                        translationY = if (reducedMotion) 0f else (-CLEAR_RISE * clear).dp.toPx()
                    },
                textStyle = FieldStyle,
                singleLine = true,
                cursorBrush = SolidColor(CaretBlue),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        send(byHand = true)
                        focusManager.clearFocus()
                    },
                ),
                decorationBox = { field ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { field() }
                },
            )

            if (caretOn && !focused) {
                val typed = remember(text, density) {
                    measurer.measure(text, FieldStyle, softWrap = false, maxLines = 1)
                        .getHorizontalPosition(text.length, usePrimaryDirection = true) / density.density
                }
                Caret(
                    lastKey = { lastKey },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        // It stops at the send button when the text runs past the field.
                        .offset(x = min(FIELD_PAD_X + typed + 1f, FIELD_WIDTH - FIELD_HEIGHT).dp),
                )
            }

            Pressable(
                onClick = { send(byHand = true) },
                enabled = hasText && !clearing,
                label = "Send",
                modifier = Modifier
                    .offset(x = (FIELD_WIDTH - FIELD_HEIGHT).dp)
                    .size(FIELD_HEIGHT.dp)
                    .graphicsLayer {
                        val grown = if (reducedMotion) 1f else POP_SCALE + (1f - POP_SCALE) * arm
                        scaleX = dip * grown
                        scaleY = dip * grown
                        alpha = arm.coerceIn(0f, 1f)
                    },
            ) {
                Box(Modifier.size(SEND_SIZE.dp).background(SendFill, CircleShape))
                Glyph(path = Icons.arrow, color = Color.White, size = SEND_SIZE, box = 32f, stroke = 2f)
            }
        }

        BasicText(
            text = note,
            style = FootnoteStyle,
            maxLines = 1,
            // Shrinks to four fifths before it is cut.
            autoSize = TextAutoSize.StepBased(minFontSize = 8.8.sp, maxFontSize = 11.sp, stepSize = 0.2.sp),
            modifier = Modifier
                .offset(y = (FOOTNOTE_Y - 7f).dp)
                .size(PANEL_WIDTH.dp, 14.dp)
                .wrapContentHeight(unbounded = true),
        )
    }
}

/**
 * One suggestion: an icon and a text that slides out below as the next one comes in from above.
 *
 * @param lift 0…1: how far the pill hangs above the stack, for its shadow.
 * @param hidden The pill has gone into the field: its next text is set while nobody can see it.
 */
@Composable
private fun Pill(
    label: String,
    slot: Int,
    lift: () -> Float,
    hidden: () -> Boolean,
    shadows: Shadows,
    reducedMotion: Boolean,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fitted = remember(label, density) {
        val chrome = PILL_PAD_LEFT + ICON_SIZE + ICON_GAP + PILL_PAD_RIGHT
        val width = measurer.measure(label, PillStyle, softWrap = false, maxLines = 1).size.width / density.density
        min(FIELD_WIDTH, chrome + ceil(width))
    }
    val text = remember { PillText(label, fitted) }

    LaunchedEffect(label, fitted) {
        // Nothing moves when the pill first appears, or when the next set repeats its text.
        if (label == text.shown && fitted == text.width.targetValue && text.change.value == 1f) return@LaunchedEffect
        if (hidden()) {
            text.leaving = null
            text.shown = label
            text.change.snapTo(1f)
            text.width.snapTo(fitted)
            return@LaunchedEffect
        }
        val spec = tween<Float>(ms(CHANGE), delayMillis = ms(slot * STAGGER), easing = ChangeEasing)
        if (label != text.shown) {
            text.leaving = text.shown
            text.shown = label
            text.change.snapTo(0f)
        }
        coroutineScope {
            launch { text.width.animateTo(fitted, spec) }
            text.change.animateTo(1f, spec)
        }
        text.leaving = null
    }

    val slide = if (reducedMotion) 0f else SLIDE

    Box(Modifier.size(text.width.value.dp, PILL_HEIGHT.dp)) {
        Box(Modifier.matchParentSize().bleed(PILL_BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(PILL_BLEED) { x, y, w, h ->
                    val up = lift().coerceIn(0f, 1f)
                    drawIntoCanvas {
                        shadows.rest.draw(it.nativeCanvas, x, y + 1f, w, h, h / 2f, alpha = 1f - up)
                        shadows.lifted.draw(it.nativeCanvas, x, y + 7f, w, h, h / 2f, alpha = up)
                    }
                    drawRoundRect(Color.White, Offset(x, y), Size(w, h), CornerRadius(h / 2f))
                }
            }
        }

        // The capsule crops the texts while it resizes around them.
        Box(Modifier.matchParentSize().clip(CircleShape)) {
            val icon = Modifier.align(Alignment.CenterStart).offset(x = PILL_PAD_LEFT.dp)
            when (slot % SLOTS) {
                0 -> Glyph(Icons.sparkles, PillIcon, ICON_SIZE, box = 15f, modifier = icon)
                1 -> Glyph(Icons.bars, PillIcon, ICON_SIZE, box = 15f, modifier = icon)
                else -> Glyph(Icons.bubble, PillIcon, ICON_SIZE, box = 15f, modifier = icon, stroke = 1.4f)
            }
            text.leaving?.let { old ->
                PillLabel(
                    text = old,
                    modifier = Modifier.clearAndSetSemantics {}.graphicsLayer {
                        val t = text.change.value
                        alpha = 1f - t
                        translationY = (slide * t).dp.toPx()
                    },
                )
            }
            PillLabel(
                text = text.shown,
                modifier = Modifier.graphicsLayer {
                    val t = text.change.value
                    alpha = t
                    translationY = (-slide * (1f - t)).dp.toPx()
                },
            )
        }
    }
}

/** A pill's text at its full width, whatever the pill's own width is at the moment. */
@Composable
private fun BoxScope.PillLabel(text: String, modifier: Modifier) {
    BasicText(
        text = text,
        style = PillStyle,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .offset(x = (PILL_PAD_LEFT + ICON_SIZE + ICON_GAP).dp)
            .wrapContentWidth(Alignment.Start, unbounded = true)
            .then(modifier),
    )
}

/** The caret of a field that types by itself, blinking once the letters stop. */
@Composable
private fun Caret(lastKey: () -> Long, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { now = System.nanoTime() }
    }
    Box(
        modifier
            .size(2.dp, 18.dp)
            .graphicsLayer { alpha = blink((now - lastKey()) / 1e9f) }
            .background(CaretBlue, RoundedCornerShape(1.dp)),
    )
}

/** A path drawn in a square [box] of its own units, scaled to [size] dp; stroked when [stroke] is set. */
@Composable
private fun Glyph(path: Path, color: Color, size: Float, box: Float, modifier: Modifier = Modifier, stroke: Float = 0f) {
    Canvas(modifier.size(size.dp)) {
        scale(this.size.width / box, pivot = Offset.Zero) {
            drawPath(
                path = path,
                color = color,
                style = if (stroke > 0f) Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round) else Fill,
            )
        }
    }
}

/** A tappable part of the composer: it gives a little while pressed. */
@Composable
private fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) TOUCH_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (label == null) Modifier else Modifier.semantics { contentDescription = label }),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
