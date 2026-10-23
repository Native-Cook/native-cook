package com.ngocdevv.nativecook.blocks.inputs.morphingmicpill

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The little picture at the right of a suggestion card. */
enum class MorphingMicPillGlyph { Ball, Medal, Court }

/** A question the panel offers, and the answer it shows for it. */
data class MorphingMicPillSuggestion(
    val question: String,
    val glyph: MorphingMicPillGlyph,
    val lead: String,
    val detailTitle: String,
    val detail: String,
)

private const val WIDTH = 350f
private const val HEIGHT = 400f
private const val RADIUS = 36f

// Cards keep this far from the panel's sides.
private const val INSET = 22f
private const val HEADING_Y = 30f
private const val CARDS_Y = 72f
private const val CARD_HEIGHT = 62f
private const val CARD_GAP = 8f
private const val ANSWER_INSET = 28f
private const val ANSWER_Y = 36f

// Rows and lines climb this far as they come in.
private const val RISE = 10f
private const val PRESS_DIP = 0.04f

// Top of the bottom bar, and the height of its three controls at rest.
private const val BAR_Y = 334f
private const val BAR_HEIGHT = 40f

// Centre of the round buttons, from the panel's sides.
private const val SIDE_X = 42f

// The capsule's width as a mic, thinking, recording and as a text field.
private const val MIC_WIDTH = 76f
private const val THINK_WIDTH = 126f
private const val RECORD_WIDTH = 138f
private const val RECORD_HEIGHT = 46f
private const val FIELD_WIDTH = 210f
private const val FIELD_HEIGHT = 44f

// The mic slides this far left to make room for the level bars.
private const val RECORD_SHIFT = 40f

// A tap on the capsule only nudges it, as a hint to hold: dp per second added to its width.
private const val NUDGE = 260f

// A press shorter than this is a tap.
private const val HOLD_DELAY = 0.2f

private const val BAR_WIDTH = 2.5f
private const val BAR_PITCH = 7f
private const val BAR_MIN = 4f
private const val BAR_MAX = 18f

// The veil's front edge is this soft, and takes this long to climb the panel.
private const val VEIL_EDGE = 110f
private const val VEIL_IN = 0.7f
private const val SHEEN_PERIOD = 1.6f
private const val SHEEN_WIDTH = 300f
private const val SHEEN_TILT = 18f

// Degrees per second a card is thrown at when the veil's front passes it.
private const val WOBBLE_KICK = 52f
private const val PULSE = 0.9f

// How long the cards, and the answer's lines, take to arrive; both leave faster.
private const val CARDS_IN = 0.6f
private const val ANSWER_IN = 1f
private const val ANSWER_DELAY = 0.15f
private const val CONTENT_OUT = 0.2f
private const val STAGGER = 0.18f

// autoPlay period, seconds.
private const val LOOP = 8.8f

// After a touch, or once the field is closed, the loop waits this long before it starts over.
private const val RESUME = 3f

// The canvas of the panel overflows it by this much on every side to fit its shadow.
private val PANEL_BLEED = 80.dp

// The box the capsule is centred in: room for the text field and for the springs' overshoot.
private const val CAPSULE_ROOM_WIDTH = 250f
private const val CAPSULE_ROOM_HEIGHT = 60f

/** The autoPlay loop, as seconds to action: press and tap the first card, hold the mic for a moment, press and tap "+". */
private val Script = arrayOf(0.8f to 0, 0.95f to 1, 4.6f to 2, 6.0f to 3, 6.9f to 4, 7.05f to 5)

/** Where the veil's front has to be for the heading and each card to feel it. */
private val WobbleLines = floatArrayOf(
    HEADING_Y - 20f,
    CARDS_Y - 10f,
    CARDS_Y + CARD_HEIGHT + CARD_GAP - 10f,
    CARDS_Y + (CARD_HEIGHT + CARD_GAP) * 2f - 10f,
)

private val BarRates = floatArrayOf(9.1f, 7.3f, 11.2f, 8.4f, 10.3f, 6.7f, 12.1f)
private val BarRipples = floatArrayOf(17.0f, 13.6f, 19.3f, 15.1f, 12.2f, 18.4f, 14.3f)
private val BarPhases = floatArrayOf(0.3f, 1.9f, 4.1f, 2.6f, 5.2f, 3.3f, 0.9f)

private val Ink = Color(0xFF1C1B19)

private val DefaultSuggestions = listOf(
    MorphingMicPillSuggestion(
        question = "Why is grass so different\nin tennis?",
        glyph = MorphingMicPillGlyph.Ball,
        lead = "Grass plays faster and lower than other surfaces, so points stay shorter and timing matters more.",
        detailTitle = "What changes",
        detail = "Balls skid and rise less after landing, and a good serve gets through the court quickly.",
    ),
    MorphingMicPillSuggestion(
        question = "What makes a serve\nan ace?",
        glyph = MorphingMicPillGlyph.Medal,
        lead = "An ace is a legal serve the receiver cannot touch, so the point is over after a single shot.",
        detailTitle = "Where it lands",
        detail = "Most aces go out wide or down the centre line, where the receiver's reach is longest.",
    ),
    MorphingMicPillSuggestion(
        question = "How does clay change\nthe game?",
        glyph = MorphingMicPillGlyph.Court,
        lead = "Clay slows the ball down and makes it bounce higher, so rallies run long and patience wins.",
        detailTitle = "What changes",
        detail = "Players slide into their shots, and heavy topspin is rewarded more than raw pace.",
    ),
)

/** The microphone's stand, in a 20 dp box; its body is a rounded rect drawn with it. */
private val MicStand = Path().apply {
    moveTo(5.2f, 8.6f)
    lineTo(5.2f, 9.4f)
    arcTo(Rect(5.2f, 4.6f, 14.8f, 14.2f), 180f, -180f, false)
    lineTo(14.8f, 8.6f)
    moveTo(10f, 14.2f)
    lineTo(10f, 17f)
    moveTo(7f, 17f)
    lineTo(13f, 17f)
}

// The icons of the round buttons, each in a 40 dp box.

private val PlusIcon = Path().apply {
    moveTo(13f, 20f)
    lineTo(27f, 20f)
    moveTo(20f, 13f)
    lineTo(20f, 27f)
}

private val KeyXs = floatArrayOf(14.6f, 18.2f, 21.8f, 25.4f)

private val SendArrow = Path().apply {
    moveTo(20f, 27f)
    lineTo(20f, 13.5f)
    moveTo(14.5f, 19f)
    lineTo(20f, 13.5f)
    lineTo(25.5f, 19f)
}

/** The arrow at the right of a card, in a 14 dp box. */
private val CardArrow = Path().apply {
    moveTo(2f, 7f)
    lineTo(12f, 7f)
    moveTo(8f, 3f)
    lineTo(12f, 7f)
    lineTo(8f, 11f)
}

private val Ribbon = Path().apply {
    moveTo(12.5f, 7.5f)
    lineTo(18f, 18f)
    lineTo(23.5f, 7.5f)
}

private val CourtLines = Path().apply {
    addRect(Rect(10.5f, 13.5f, 25.5f, 22.5f))
    moveTo(18f, 13.5f)
    lineTo(18f, 22.5f)
    moveTo(13.5f, 18f)
    lineTo(22.5f, 18f)
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun pillText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val HeadingStyle = pillText(15f, 20f, Ink, FontWeight.SemiBold).copy(textAlign = TextAlign.Center)
private val QuestionStyle = pillText(13f, 17.5f, Ink, FontWeight.Medium)
private val AnswerQuestionStyle = pillText(12f, 15f, Color(0xFF8A8782), FontWeight.Medium)
private val AnswerLeadStyle = pillText(16f, 22f, Ink)
private val AnswerTitleStyle = pillText(14f, 17f, Ink, FontWeight.SemiBold)
private val AnswerDetailStyle = pillText(13f, 18.5f, Color(0xFF5C5953))
private val LetterStyle = pillText(12f, 16f, Color.White, FontWeight.Medium)
private val FieldStyle = pillText(15f, 20f, Color.White)
private val PlaceholderStyle = pillText(15f, 20f, Color(0xFF8E8E93))

/** A value that follows its target like a spring (mass 1, stiffness omega², damping 2·zeta·omega). */
private class Chase(var x: Float) {
    var v = 0f

    fun step(target: Float, omega: Float, zeta: Float, dt: Float) {
        v += (omega * omega * (target - x) - 2f * zeta * omega * v) * dt
        x += v * dt
    }
}

/** Moves [x] towards [target], covering the same share of the distance in the same time at any frame rate. */
private fun approach(x: Float, target: Float, rate: Float, dt: Float) = x + (target - x) * (1f - exp(-rate * dt))

/** Top of the veil for a sweep that is [progress] (0…1) done: it starts under the panel and ends above it. */
private fun veilFront(progress: Float): Float {
    val eased = 1f - (1f - progress).pow(3)
    return HEIGHT - (HEIGHT + VEIL_EDGE) * eased
}

/** How far a row has come in, 0…1: [order] 0 is the first to arrive. */
private fun rowProgress(progress: Float, order: Int, rows: Int): Float {
    val window = 1f - STAGGER * (rows - 1)
    val p = ((progress - order * STAGGER) / window).coerceIn(0f, 1f)
    return 1f - (1f - p).pow(3)
}

/** Opacity of one letter of the thinking label: a bright band runs through the word again and again. */
private fun shimmer(index: Int, count: Int, time: Float): Float {
    val cycle = time / SHEEN_PERIOD
    val band = (cycle - floor(cycle)) * (count + 8) - 4f
    val d = index - band
    return 0.45f + 0.55f * exp(-d * d / 5f)
}

/** Per-frame state: what the panel is doing and everything that eases after it. */
private class Sim {
    var time = 0f
    var clock = 0f

    /** The next line of the autoPlay script. */
    var step = 0
    var looping = false

    /** Seconds the loop still waits after a touch. */
    var hold = 0f

    /** The suggestion whose answer is laid out; -1 for a typed question. Composed, so it is a state. */
    var asked by mutableIntStateOf(0)
    var answering = false
    var thinking = false
    var thinkAt = 0f
    var micDown = false
    var micByLoop = false
    var micAt = 0f
    var recording = false
    var recordAt = 0f

    /** The control under the finger: 0…2 a card, 3 "+", 4 the keyboard button; -1 none. */
    var pressed = -1

    var veilIn = 0f
    var veilAlpha = 0f

    /** Heading and cards, tilted by the veil's front as it passes them. */
    val wobble = Array(4) { Chase(0f) }
    val kicked = BooleanArray(4)
    var cards = 1f
    var answer = 0f
    val width = Chase(MIC_WIDTH)
    val height = Chase(BAR_HEIGHT)
    val sides = Chase(1f)
    var think = 0f
    var rec = 0f
    var type = 0f
    val press = FloatArray(5)

    // Written last in a frame: reading it is what redraws the component.
    private val tick = mutableFloatStateOf(0f)

    /** Subscribes the caller to the next frame. */
    fun watch(): Sim {
        tick.floatValue
        return this
    }

    fun touch() {
        hold = RESUME
    }

    fun ask(index: Int) {
        if (thinking) return
        asked = index
        answering = false
        thinking = true
        thinkAt = time
        veilIn = 0f
        veilAlpha = 1f
        kicked.fill(false)
    }

    fun reset() {
        thinking = false
        answering = false
    }

    fun micPress(byLoop: Boolean) {
        if (thinking) return
        micDown = true
        micByLoop = byLoop
        micAt = time
    }

    /** True when a recording ended; a short tap only nudges the capsule. */
    fun micRelease(): Boolean {
        if (!micDown) return false
        micDown = false
        if (recording) {
            recording = false
            return true
        }
        width.v += NUDGE
        return false
    }

    private fun run(action: Int) {
        when (action) {
            0 -> pressed = 0
            1 -> {
                pressed = -1
                ask(0)
            }
            2 -> micPress(byLoop = true)
            3 -> micRelease()
            4 -> pressed = 3
            else -> {
                pressed = -1
                reset()
            }
        }
    }

    fun advance(dt: Float, typing: Boolean, thinkingTime: Float, still: Boolean, recordStarted: () -> Unit) {
        time += dt

        // The loop stays away while a finger is at work, and until the answer it asked for has been read.
        if (typing || (micDown && !micByLoop) || (hold > 0f && thinking)) hold = RESUME
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping) {
                // The loop starts over from the suggestions.
                clock = 0f
                step = 0
                pressed = -1
                micDown = false
                recording = false
                reset()
            }
        } else if (looping) {
            clock += dt
            if (clock >= LOOP) {
                clock -= LOOP
                step = 0
            }
            while (step < Script.size && clock >= Script[step].first) {
                run(Script[step].second)
                step += 1
            }
        }

        if (thinking && time - thinkAt >= thinkingTime) {
            thinking = false
            answering = true
            answer = -ANSWER_DELAY
        }
        if (micDown && !recording && time - micAt >= HOLD_DELAY) {
            recording = true
            recordAt = time
            if (!micByLoop) recordStarted()
        }

        if (thinking) {
            veilIn = if (still) 1f else min(1f, veilIn + dt / VEIL_IN)
        } else if (veilAlpha < 0.01f) {
            veilIn = 0f
        }
        veilAlpha = approach(veilAlpha, if (thinking) 1f else 0f, 5f, dt)
        val front = veilFront(veilIn)
        for (i in 0 until 4) {
            if (thinking && !still && !kicked[i] && front < WobbleLines[i]) {
                kicked[i] = true
                wobble[i].v += if (i % 2 == 0) WOBBLE_KICK else -WOBBLE_KICK
            }
            wobble[i].step(0f, 13f, 0.3f, dt)
        }
        answer = if (answering) min(1f, answer + dt / ANSWER_IN) else max(0f, answer - dt / CONTENT_OUT)
        // The cards wait for the answer to be gone before they come back.
        if (answering) {
            cards = max(0f, cards - dt / CONTENT_OUT)
        } else if (answer <= 0f) {
            cards = min(1f, cards + dt / CARDS_IN)
        }

        val wide = if (typing) FIELD_WIDTH else if (thinking) THINK_WIDTH else if (recording) RECORD_WIDTH else MIC_WIDTH
        width.step(wide, 17f, 0.55f, dt)
        height.step(if (typing) FIELD_HEIGHT else if (recording) RECORD_HEIGHT else BAR_HEIGHT, 17f, 0.55f, dt)
        sides.step(if (thinking) 0f else 1f, 15f, 0.5f, dt)
        think = approach(think, if (thinking && !typing) 1f else 0f, 14f, dt)
        rec = approach(rec, if (recording) 1f else 0f, 14f, dt)
        type = approach(type, if (typing) 1f else 0f, 14f, dt)
        for (i in press.indices) {
            press[i] = approach(press[i], if (pressed == i) 1f else 0f, 30f, dt)
        }
        tick.floatValue = time
    }
}

/** The soft shadows under the panel, the cards, the round buttons and the capsule. */
private class PillShadows {
    val panel = SoftRoundRect(Color.Black.copy(alpha = 0.1f), sigma = 24f)
    val card = SoftRoundRect(rgba(60, 50, 40, 0.09f), sigma = 7f)
    val side = SoftRoundRect(rgba(60, 50, 40, 0.14f), sigma = 6f)
    val capsule = SoftRoundRect(Color.Black.copy(alpha = 0.22f), sigma = 8f)
}

/**
 * An assistant's bottom bar whose black mic capsule changes shape with what it is doing. Tap a
 * suggestion and a frosted veil sweeps up over the cards, which wobble as it passes, the round
 * buttons step back and the capsule stretches into a "Thinking…" pill; then the veil lifts on the
 * answer and the capsule springs back. Hold the capsule and it swells and shows level bars; the
 * keyboard button stretches it into a text field, and "+" brings the suggestions back.
 *
 * Nothing is recorded here: the bars are a drawn pattern. Start and stop a real recorder from
 * [onRecordStart] and [onRecordEnd].
 *
 * @param value The field's text. Leave it out and the component keeps the text itself.
 * @param autoPlay Taps the first suggestion, waits for the answer, holds the mic and starts over, in a loop.
 * @param typedLead The answer to a question that was typed instead of picked.
 * @param thinkingTime Seconds the capsule spends thinking before the answer shows.
 * @param onSuggestion Called with the suggestion's index when a card is tapped.
 * @param onSend Called with the typed question when it is sent.
 * @param onNew Called when "+" brings the suggestions back.
 */
@Composable
fun MorphingMicPill(
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    heading: String = "You might want to ask",
    suggestions: List<MorphingMicPillSuggestion> = DefaultSuggestions,
    typedLead: String = "Here is a short answer to get you started. Ask a follow-up to go deeper.",
    typedDetailTitle: String = "Next",
    typedDetail: String = "Tap + to go back to the suggestions and pick another question.",
    placeholder: String = "Ask anything",
    thinkingLabel: String = "Thinking…",
    thinkingTime: Float = 2.1f,
    onSuggestion: ((Int) -> Unit)? = null,
    onSend: ((String) -> Unit)? = null,
    onNew: (() -> Unit)? = null,
    onRecordStart: (() -> Unit)? = null,
    onRecordEnd: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }

    val sim = remember { Sim() }
    val shadows = remember { PillShadows() }
    var own by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    // The capsule is a text field.
    var typing by remember { mutableStateOf(false) }
    // The question that was typed last.
    var typedQuestion by remember { mutableStateOf("") }
    val current = value ?: own

    val thinkingFor by rememberUpdatedState(thinkingTime)
    val recordStarted by rememberUpdatedState {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onRecordStart?.invoke()
    }
    val recordEnded by rememberUpdatedState(onRecordEnd)

    fun store(text: String) {
        if (value == null) own = text
        onValueChange?.invoke(text)
    }

    LaunchedEffect(auto) { sim.looping = auto }

    LaunchedEffect(reducedMotion) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            sim.advance(dt, typing = typing, thinkingTime = thinkingFor, still = reducedMotion) { recordStarted() }
        }
    }

    // The field can only take the focus once it is enabled, a composition after the button was tapped.
    LaunchedEffect(typing) {
        if (typing) focus.requestFocus()
    }

    fun press(index: Int, down: Boolean) {
        if (down) {
            sim.touch()
            sim.pressed = index
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else if (sim.pressed == index) {
            sim.pressed = -1
        }
    }

    fun send() {
        val question = current.trim()
        store("")
        focusManager.clearFocus()
        typing = false
        if (question.isEmpty()) return
        typedQuestion = question
        sim.touch()
        sim.ask(-1)
        onSend?.invoke(question)
    }

    // The keyboard button opens the field; once there is text in it, it sends.
    fun keyboardTapped() {
        if (!typing) {
            typing = true
        } else if (current.isBlank()) {
            store("")
            focusManager.clearFocus()
            typing = false
        } else {
            send()
        }
    }

    val cardsLive by remember { derivedStateOf { sim.watch().let { it.cards > 0.9f && !it.thinking } } }
    val sidesLive by remember { derivedStateOf { sim.watch().sides.x.coerceIn(0f, 1f) > 0.9f } }
    val item = suggestions.getOrNull(sim.asked)

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        Box(Modifier.matchParentSize().bleed(PANEL_BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(PANEL_BLEED) { x, y, _, _ ->
                    drawIntoCanvas { shadows.panel.draw(it.nativeCanvas, x, y + 14f, WIDTH, HEIGHT, RADIUS) }
                    drawRoundRect(Color(0xFFF7F6F3), Offset(x, y), Size(WIDTH, HEIGHT), CornerRadius(RADIUS))
                }
            }
        }

        // Everything under the capsule, cut to the panel's corners so the veil stays inside them.
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(RADIUS.dp))) {
            Box(
                Modifier
                    .offset(0.dp, HEADING_Y.dp)
                    .size(WIDTH.dp, 20.dp)
                    .graphicsLayer {
                        val s = sim.watch()
                        alpha = rowProgress(s.cards, 0, 4)
                        rotationZ = s.wobble[0].x
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(heading, style = HeadingStyle, maxLines = 1)
            }

            suggestions.take(3).forEachIndexed { index, suggestion ->
                PillCard(
                    sim = sim,
                    index = index,
                    item = suggestion,
                    shadow = shadows.card,
                    still = reducedMotion,
                    enabled = cardsLive,
                    onPress = { down -> press(index, down) },
                ) {
                    sim.touch()
                    sim.ask(index)
                    onSuggestion?.invoke(index)
                }
            }

            // The answer, where the cards were: its four lines arrive one after another.
            Column(
                Modifier
                    .offset(ANSWER_INSET.dp, ANSWER_Y.dp)
                    .size((WIDTH - ANSWER_INSET * 2f).dp, (BAR_Y - ANSWER_Y).dp),
            ) {
                AnswerLine(sim, 0, reducedMotion, (item?.question ?: typedQuestion).replace("\n", " "), AnswerQuestionStyle, 12f)
                AnswerLine(sim, 1, reducedMotion, item?.lead ?: typedLead, AnswerLeadStyle, 19f)
                AnswerLine(sim, 2, reducedMotion, item?.detailTitle ?: typedDetailTitle, AnswerTitleStyle, 8f)
                AnswerLine(sim, 3, reducedMotion, item?.detail ?: typedDetail, AnswerDetailStyle, 0f)
            }

            PillSideButton(sim, 3, SIDE_X, shadows.side, sidesLive, "New question", { down -> press(3, down) }, {
                sim.touch()
                sim.reset()
                onNew?.invoke()
            }) {
                drawPath(PlusIcon, Ink, style = Stroke(1.7f, cap = StrokeCap.Round))
            }
            // The keyboard button turns into the send button while the field is open.
            PillSideButton(sim, 4, WIDTH - SIDE_X, shadows.side, sidesLive, if (typing) "Send" else "Keyboard", { down -> press(4, down) }, {
                sim.touch()
                keyboardTapped()
            }) { type ->
                val keys = 1f - type
                drawRoundRect(Ink, Offset(11f, 14f), Size(18f, 12f), CornerRadius(3f), style = Stroke(1.4f), alpha = keys)
                for (x in KeyXs) {
                    drawCircle(Ink, 0.8f, Offset(x, 17.7f), alpha = keys)
                    drawCircle(Ink, 0.8f, Offset(x, 20.4f), alpha = keys)
                }
                drawLine(Ink, Offset(16f, 23f), Offset(24f, 23f), 1.4f, StrokeCap.Round, alpha = keys)
                drawPath(SendArrow, Ink, alpha = type, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // The veil: a tinted sheet with a soft front edge rising from the bar, and a pale sheen drifting across it.
            Canvas(Modifier.matchParentSize().graphicsLayer { alpha = sim.watch().veilAlpha }) {
                val s = sim.watch()
                surface(0.dp) { _, _, _, _ ->
                    val front = veilFront(s.veilIn)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color(0x00A9A3BD),
                            VEIL_EDGE / (HEIGHT + VEIL_EDGE) to Color(0xFFA9A3BD).copy(alpha = 0.52f),
                            1f to Color(0xFFA9A3BD).copy(alpha = 0.52f),
                            startY = front,
                            endY = front + HEIGHT + VEIL_EDGE,
                        ),
                        topLeft = Offset(0f, front),
                        size = Size(WIDTH, HEIGHT + VEIL_EDGE),
                    )
                    val sheen = if (reducedMotion) 0.3f else (s.time - s.thinkAt) / SHEEN_PERIOD
                    val left = -SHEEN_WIDTH * 1.3f + (WIDTH + SHEEN_WIDTH * 1.6f) * (sheen - floor(sheen))
                    // Taller than the panel, so the tilted band has no corner inside it.
                    rotate(SHEEN_TILT, pivot = Offset(left + SHEEN_WIDTH / 2f, HEIGHT * 0.5f)) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color(0x00FFD6EC),
                                0.3f to Color(0xFFFFD6EC).copy(alpha = 0.34f),
                                0.5f to Color(0xFFC4E8FF).copy(alpha = 0.34f),
                                0.7f to Color(0xFFD6FFEC).copy(alpha = 0.28f),
                                1f to Color(0x00D6FFEC),
                                startX = left,
                                endX = left + SHEEN_WIDTH,
                            ),
                            topLeft = Offset(left, -HEIGHT * 0.3f),
                            size = Size(SHEEN_WIDTH, HEIGHT * 1.6f),
                            alpha = if (front < 0f) 1f else max(0f, 1f - front / HEIGHT),
                        )
                    }
                }
            }
        }

        // The black capsule: mic, level bars, the "Thinking…" label or the text field, whichever it is now.
        Box(
            Modifier
                .offset((WIDTH / 2f - CAPSULE_ROOM_WIDTH / 2f).dp, (BAR_Y + BAR_HEIGHT / 2f - CAPSULE_ROOM_HEIGHT / 2f).dp)
                .size(CAPSULE_ROOM_WIDTH.dp, CAPSULE_ROOM_HEIGHT.dp),
            contentAlignment = Alignment.Center,
        ) {
            val hold = if (typing) {
                Modifier
            } else {
                Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        first.consume()
                        if (!sim.micDown || sim.micByLoop) {
                            sim.touch()
                            sim.micPress(byLoop = false)
                        }
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == first.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                            }
                        } finally {
                            if (sim.micRelease()) recordEnded?.invoke()
                        }
                    }
                }
            }
            Box(
                Modifier
                    // Sized by the springs: only the capsule itself takes the touch, not the room around it.
                    .layout { measurable, _ ->
                        val s = sim.watch()
                        val w = max(BAR_HEIGHT, s.width.x).dp.roundToPx()
                        val h = max(30f, s.height.x).dp.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(w, h) { placeable.place(0, 0) }
                    }
                    .drawBehind {
                        val s = sim.watch()
                        surface(0.dp) { _, _, w, h ->
                            drawIntoCanvas { shadows.capsule.draw(it.nativeCanvas, 0f, 5f, w, h, h / 2f) }
                            drawRoundRect(Color(0xFF111111), size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
                            drawRoundRect(
                                Color.White,
                                Offset(0.75f, 0.75f),
                                Size(w - 1.5f, h - 1.5f),
                                CornerRadius(h / 2f - 0.75f),
                                style = Stroke(1.5f),
                                alpha = s.think.coerceIn(0f, 1f),
                            )
                        }
                    }
                    .semantics { contentDescription = "Hold to speak" }
                    .then(hold),
                contentAlignment = Alignment.Center,
            ) {
                // The contents are laid over the capsule at their own size, so the widest of them can't stretch it.
                Canvas(
                    Modifier.requiredSize(20.dp).graphicsLayer {
                        val s = sim.watch()
                        translationX = (-RECORD_SHIFT * s.rec).dp.toPx()
                        alpha = (1f - s.think) * (1f - s.type)
                    },
                ) {
                    surface(0.dp) { _, _, _, _ ->
                        drawRoundRect(Color.White, Offset(7.5f, 2f), Size(5f, 9.5f), CornerRadius(2.5f))
                        drawPath(MicStand, Color.White, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }

                Canvas(
                    Modifier.requiredSize((BAR_PITCH * 6f + BAR_WIDTH).dp, BAR_MAX.dp).graphicsLayer {
                        val s = sim.watch()
                        translationX = (RECORD_SHIFT * 0.45f).dp.toPx()
                        alpha = s.rec * (1f - s.think)
                    },
                ) {
                    val s = sim.watch()
                    val time = if (reducedMotion) 0.4f else s.time - s.recordAt
                    surface(0.dp) { _, _, _, _ ->
                        for (i in 0 until 7) {
                            val wave = 0.5f + 0.34f * sin(time * BarRates[i] + BarPhases[i]) + 0.22f * sin(time * BarRipples[i] + BarPhases[6 - i])
                            val h = BAR_MIN + (BAR_MAX - BAR_MIN) * wave.coerceIn(0f, 1f) * s.rec
                            drawRoundRect(Color.White, Offset(i * BAR_PITCH, (BAR_MAX - h) / 2f), Size(BAR_WIDTH, h), CornerRadius(BAR_WIDTH / 2f))
                        }
                    }
                }

                Row(
                    Modifier.wrapContentSize(unbounded = true).graphicsLayer { alpha = sim.watch().think },
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(14.dp)) {
                        val s = sim.watch()
                        val beat = if (reducedMotion) 0.3f else s.time - s.thinkAt
                        surface(0.dp) { _, _, _, _ ->
                            drawCircle(Color.White, 5.5f, Offset(7f, 7f), style = Stroke(1.6f))
                            drawCircle(Color.White, 2.5f * (0.85f + 0.3f * sin(TAU * beat / PULSE)), Offset(7f, 7f))
                        }
                    }
                    Row {
                        thinkingLabel.forEachIndexed { index, letter ->
                            BasicText(
                                letter.toString(),
                                Modifier.graphicsLayer {
                                    val s = sim.watch()
                                    alpha = shimmer(index, thinkingLabel.length, if (reducedMotion) 0.3f else s.time - s.thinkAt)
                                },
                                style = LetterStyle,
                                maxLines = 1,
                            )
                        }
                    }
                }

                BasicTextField(
                    value = current,
                    onValueChange = { store(it) },
                    modifier = Modifier
                        .requiredWidth((FIELD_WIDTH - 36f).dp)
                        .graphicsLayer { alpha = sim.watch().type }
                        .focusRequester(focus)
                        .onFocusChanged {
                            // A field left empty folds back into the mic capsule.
                            if (focused && !it.isFocused && current.isEmpty()) typing = false
                            focused = it.isFocused
                        },
                    enabled = typing,
                    textStyle = FieldStyle,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (current.isEmpty()) BasicText(placeholder, style = PlaceholderStyle, maxLines = 1)
                            inner()
                        }
                    },
                )
            }
        }
    }
}

/** A suggestion card: it comes in with its row, tilts when the veil passes, and dips under a finger. */
@Composable
private fun PillCard(
    sim: Sim,
    index: Int,
    item: MorphingMicPillSuggestion,
    shadow: SoftRoundRect,
    still: Boolean,
    enabled: Boolean,
    onPress: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressChanged by rememberUpdatedState(onPress)
    LaunchedEffect(pressed) { pressChanged(pressed) }

    Row(
        Modifier
            .offset(INSET.dp, (CARDS_Y + index * (CARD_HEIGHT + CARD_GAP)).dp)
            .size((WIDTH - INSET * 2f).dp, CARD_HEIGHT.dp)
            .graphicsLayer {
                val s = sim.watch()
                val shown = rowProgress(s.cards, index + 1, 4)
                val scale = 1f - PRESS_DIP * s.press[index]
                alpha = shown
                translationY = if (still) 0f else ((1f - shown) * RISE).dp.toPx()
                rotationZ = s.wobble[index + 1].x
                scaleX = scale
                scaleY = scale
                // Fading must not cut the shadow off at the card's edges.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawBehind {
                surface(0.dp) { _, _, w, h ->
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, 4f, w, h, 16f) }
                    drawRoundRect(Color.White, size = Size(w, h), cornerRadius = CornerRadius(16f))
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(item.question, Modifier.weight(1f), style = QuestionStyle, maxLines = 2)
        Canvas(Modifier.size(36.dp)) {
            surface(0.dp) { _, _, _, _ -> drawGlyph(item.glyph) }
        }
        Canvas(Modifier.size(14.dp)) {
            surface(0.dp) { _, _, _, _ ->
                drawPath(CardArrow, Ink, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** One line of the answer, with the gap under it. */
@Composable
private fun AnswerLine(sim: Sim, order: Int, still: Boolean, text: String, style: TextStyle, gap: Float) {
    BasicText(
        text,
        Modifier
            .padding(bottom = gap.dp)
            .graphicsLayer {
                val shown = rowProgress(max(0f, sim.watch().answer), order, 4)
                alpha = shown
                translationY = if (still) 0f else ((1f - shown) * RISE).dp.toPx()
            },
        style = style,
    )
}

/** One of the two white round buttons beside the capsule; [icon] is drawn in a 40 dp box and told how open the field is. */
@Composable
private fun PillSideButton(
    sim: Sim,
    index: Int,
    x: Float,
    shadow: SoftRoundRect,
    enabled: Boolean,
    label: String,
    onPress: (Boolean) -> Unit,
    onClick: () -> Unit,
    icon: DrawScope.(type: Float) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressChanged by rememberUpdatedState(onPress)
    LaunchedEffect(pressed) { pressChanged(pressed) }

    Canvas(
        Modifier
            .offset((x - BAR_HEIGHT / 2f).dp, BAR_Y.dp)
            .size(BAR_HEIGHT.dp)
            .graphicsLayer {
                val s = sim.watch()
                val shown = s.sides.x.coerceIn(0f, 1f)
                val scale = (0.5f + 0.5f * s.sides.x) * (1f - PRESS_DIP * 1.5f * s.press[index])
                alpha = shown * (1f - 0.55f * s.rec)
                scaleX = scale
                scaleY = scale
                // Fading must not cut the shadow off at the button's edges.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        val s = sim.watch()
        surface(0.dp) { _, _, w, h ->
            drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, 3f, w, h, h / 2f) }
            drawCircle(Color.White, h / 2f, Offset(w / 2f, h / 2f))
            icon(s.type.coerceIn(0f, 1f))
        }
    }
}

/** A suggestion's picture on its tinted disc, in a 36 dp box. */
private fun DrawScope.drawGlyph(glyph: MorphingMicPillGlyph) {
    val centre = Offset(18f, 18f)
    when (glyph) {
        MorphingMicPillGlyph.Ball -> {
            drawCircle(Color(0xFFF6EBE8), 18f, centre)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFF0796C), Color(0xFFD9463B), Color(0xFFB5342C)), center = Offset(15f, 14f), radius = 15f,
                ),
                10f,
                centre,
            )
            drawOval(rgba(255, 255, 255, 0.45f), Offset(12.5f, 11.5f), Size(5f, 3.6f))
        }
        MorphingMicPillGlyph.Medal -> {
            drawCircle(Color(0xFFF7F0DE), 18f, centre)
            drawPath(Ribbon, Color(0xFFE5484D), style = Stroke(3.2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFD978), Color(0xFFF2B84B), Color(0xFFD99A2B)), center = Offset(16.5f, 20f), radius = 9f,
                ),
                6.5f,
                Offset(18f, 22f),
            )
            drawCircle(Color(0xFFC98A1E).copy(alpha = 0.7f), 3.5f, Offset(18f, 22f), style = Stroke(1f))
        }
        MorphingMicPillGlyph.Court -> {
            drawCircle(Color(0xFFF8EBE2), 18f, centre)
            drawRoundRect(Color(0xFFE0703A), Offset(8f, 11f), Size(20f, 14f), CornerRadius(2.5f))
            drawPath(CourtLines, rgba(255, 255, 255, 0.92f), style = Stroke(1f))
        }
    }
}
