package com.ngocdevv.nativecook.blocks.inputs.voicenotecomposer

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 350f
private const val HEIGHT = 300f
private const val RADIUS = 32f
private const val PAD = 16f
private const val INCOMING_Y = 24f
private const val INCOMING_WIDTH = 166f
private const val INCOMING_HEIGHT = 40f
private const val PILL_HEIGHT = 44f

// Top of the composer row: the round plus, then the pill.
private const val ROW_Y = HEIGHT - PAD - PILL_HEIGHT
private const val PLUS = 40f
private const val PILL_X = PAD + PLUS + 8f
private const val PILL_WIDTH = WIDTH - PAD - PILL_X

// Inside the pill: the button at its right end, the discard cross, the right edge of the timer.
private const val MAIN_X = PILL_WIDTH - 4f - 36f
private const val DISCARD_X = MAIN_X - 30f
private const val TIMER_RIGHT = MAIN_X - 8f

// The waveform: sixteen bars a second, 2 points wide and 2 apart.
private const val RATE = 16f
private const val PITCH = 4f
private const val BAR_MIN = 3f
private const val BAR_MAX = 22f

// Seconds a new bar takes to reach its height.
private const val GROW = 0.12f

// Where the live bars are born and where they have faded out, inside the pill.
private const val LIVE_RIGHT = TIMER_RIGHT - 44f
private const val LIVE_LEFT = 14f
private const val LIVE_FADE = 18f
private const val LIVE_BARS = 44
private const val PREVIEW_X = 44f
private const val PREVIEW_BARS = 28
private const val RIPPLE = 0.35f
private const val SHORTEST_TAKE = 0.4f

// The sent bubble: 12 points of padding, the play button and the bars, then one line of text.
private const val BUBBLE_WIDTH = 220f
private const val BUBBLE_HEIGHT = 82f
private const val BUBBLE_GAP = 14f
private const val BUBBLE_BARS = 38
private const val BUBBLE_TEXT_Y = 52f
private const val BUBBLE_TEXT = BUBBLE_WIDTH - 24f - 40f
private const val TEXT_LINE = 18f
private const val SHIMMER_WIDTH = 48f
private const val SHIMMER = 1.1f

// The shimmer's windows: how wide each is, and how much ink its copy of the label adds.
private val SHIMMER_WINDOWS = listOf(48f to 0.3f, 22f to 0.45f)

// Seconds between the send and the transcript.
private const val GENERATE = 1.4f

// autoPlay (seconds): each press lasts PRESS, and the take is 2.4 s long. The loop is a whole
// number of seconds, so the drawn caret blinks in step with it.
private const val LOOP = 9f
private const val RECORD_AT = 0.8f
private const val STOP_AT = 3.2f
private const val PLAY_AT = 3.9f
private const val SEND_AT = 4.9f
private const val FADE_AT = 8.3f
private const val PRESS = 0.12f

// After a touch the script waits this long, then starts again from rest.
private const val RESUME_AFTER = 4f

private val Ink = Color(0xFF243447)
private val Mute = rgba(36, 52, 71, 0.55f)
private val Faint = rgba(36, 52, 71, 0.32f)
private val Frost = rgba(255, 255, 255, 0.55f)

// The darker disc behind stop, send and the bubble's play button.
private val Well = rgba(36, 52, 71, 0.1f)
private val Sky = arrayOf(0f to Color(0xFF8FA3C4), 0.36f to Color(0xFFC5B5C6), 0.68f to Color(0xFFF6C7A0), 1f to Color(0xFFF7B25E))

// The preview lands a little past its place as a bubble and settles.
private val LiftBack = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)
private val SoftBack = CubicBezierEasing(0.3f, 1.3f, 0.6f, 1f)

private object Icons {
    val plus = parse("M9 3.5V14.5M3.5 9H14.5")
    val cross = parse("M5 5L13 13M13 5L5 13")
    val arrow = parse("M9 14.5V3.5M4.5 8L9 3.5L13.5 8")

    // Five bars of a waveform: the icon that starts a take.
    val wave = parse(listOf(4f, 9f, 14f, 8f, 3f).mapIndexed { index, height -> "M${3 + 3 * index} ${9 - height / 2}V${9 + height / 2}" }.joinToString(""))
    val triangle = parse("M6 4.2L14 9L6 13.8Z")
    val pause = parse("M5.4 4.6H7.4V13.4H5.4ZM10.6 4.6H12.6V13.4H10.6Z")

    private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun noteText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val MessageStyle = noteText(15f, 20f, Ink)
private val PlaceholderStyle = noteText(15f, 20f, Mute)
private val TimerStyle = noteText(13f, 18f, Ink, FontWeight.Medium).copy(textAlign = TextAlign.End)
private val NoteStyle = noteText(13f, TEXT_LINE, Ink)
private val LabelStyle = noteText(13f, TEXT_LINE, Mute)
private val LengthStyle = noteText(12f, TEXT_LINE, Mute).copy(textAlign = TextAlign.End)

/** How loud sample [k] of the take is, 0…1: a fixed pattern with the rise and fall of speech. */
private fun level(k: Int): Float {
    val phrase = 0.55f + 0.45f * sin(k * 0.45f + 1f)
    val grain = 0.3f + 0.7f * abs(sin(k * 2.3f + 0.7f) * cos(k * 0.9f))
    return (phrase * grain * 1.3f).coerceIn(0.08f, 1f)
}

/** One swing that dies away within a third of a second. */
private fun wobble(t: Float) = if (t <= 0f) 0f else sin(22f * t) * exp(-9f * t)

/** Seconds as "0:07". */
private fun clock(seconds: Float): String {
    val whole = max(0, floor(seconds).toInt())
    return "${whole / 60}:${if (whole % 60 < 10) "0" else ""}${whole % 60}"
}

private suspend fun sleepUntil(nanos: Long) {
    val millis = (nanos - System.nanoTime()) / 1_000_000
    if (millis > 0) delay(millis)
}

/** The bubble at one instant: its box, its corners, the opacity of its fill and of what is written on it. */
private class BubbleFrame(lift: Float, unfold: Float, gone: Float, still: Boolean) {
    // It starts as the pill itself and lands a little past its place; a fading one drifts up.
    private val t = if (still) 1f else lift
    val height = PILL_HEIGHT + (BUBBLE_HEIGHT + TEXT_LINE * unfold - PILL_HEIGHT) * t
    val x = PILL_X + (WIDTH - PAD - BUBBLE_WIDTH - PILL_X) * t
    val y = ROW_Y + (-BUBBLE_GAP - BUBBLE_HEIGHT - TEXT_LINE * unfold) * t + if (still) 0f else -8f * gone
    val width = PILL_WIDTH + (BUBBLE_WIDTH - PILL_WIDTH) * t
    val radius = PILL_HEIGHT / 2f + (18f - PILL_HEIGHT / 2f) * t.coerceIn(0f, 1f)
    val fill = min(1f, max(lift, 0f) * 6f) * (1f - gone)
    val ink = ((lift - 0.6f) / 0.3f).coerceIn(0f, 1f) * (1f - gone)
}

private enum class Phase { Idle, Recording, Preview }

private enum class Control { Main, Play }

/** Seconds since the composer appeared, and the moments its drawing is timed from. */
private class Motion {
    var recordAt = -100f
    var stopAt = -100f

    /** When the preview and the bubble started playing; negative while they are still. */
    var previewPlayAt = -1f
    var bubblePlayAt = -1f
    var sentAt = -100f

    /** Seconds of the take in the composer, and of the one in the bubble. */
    var take = 1f
    var sent = 1f

    /** True from a send until the next take: the preview rides up with the bubble as it fades. */
    var carrying = false

    // Reading it is what redraws the composer.
    val time = mutableFloatStateOf(0f)
}

/**
 * Frosted chat composer on a sunset panel that records a voice note in place. The waveform icon
 * starts a take: bars scroll in from the right under a running timer. Stop turns it into a preview
 * with a play button and a scrubber; send lifts it out of the composer into a bubble that shows
 * "Generating transcript…" under a shimmer, then unfolds the transcript.
 *
 * Nothing is recorded: the waveform is a fixed pattern drawn as if it were live, and
 * [onRecordStart] / [onRecordEnd] are where a real recorder is connected.
 *
 * @param value Controlled text; leave null and the field keeps its own.
 * @param autoPlay Records, previews and sends a voice note in a loop, without opening the keyboard.
 * @param incoming The message already in the chat, above the composer.
 * @param transcript What the sent bubble reads once the transcript has arrived.
 * @param onSend Called with the typed text when the send button or the keyboard's send key is pressed.
 * @param onRecordEnd Called with the length of the take in seconds when stop is pressed.
 * @param onSendVoice Called with the length of the take in seconds when the preview is sent.
 */
@Composable
fun VoiceNoteComposer(
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    placeholder: String = "What’s on your mind",
    incoming: String = "How was the flight?",
    generatingLabel: String = "Generating transcript…",
    transcript: String = "Just landed in Lisbon. The light here is unreal!",
    onSend: ((String) -> Unit)? = null,
    onRecordStart: (() -> Unit)? = null,
    onRecordEnd: ((Float) -> Unit)? = null,
    onSendVoice: ((Float) -> Unit)? = null,
    onDiscard: (() -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val controlled = value != null
    val auto = autoPlay && !controlled && !reducedMotion

    var draft by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(Phase.Idle) }
    // True while the preview, or the sent bubble, plays.
    var previewPlaying by remember { mutableStateOf(false) }
    var bubblePlaying by remember { mutableStateOf(false) }
    var bubbleShown by remember { mutableStateOf(false) }
    var transcribed by remember { mutableStateOf(false) }
    // The length of the finished take and of the sent one.
    var takeSeconds by remember { mutableFloatStateOf(0f) }
    var sentSeconds by remember { mutableFloatStateOf(0f) }
    // The button the autoPlay script is pressing.
    var scriptPress by remember { mutableStateOf<Control?>(null) }
    // False once a touch has taken the composer from the autoPlay script.
    var handsOff by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var sends by remember { mutableIntStateOf(0) }
    var previewRuns by remember { mutableIntStateOf(0) }
    var bubbleRuns by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val motion = remember { Motion() }
    // 0…1 each: how much of the take, of the preview and of the typed text's send arrow shows.
    val rec = remember { Animatable(0f) }
    val pre = remember { Animatable(0f) }
    val arm = remember { Animatable(0f) }
    // 0…1 with an overshoot: the preview on its way from the composer to its place as a bubble.
    val lift = remember { Animatable(0f) }
    val unfold = remember { Animatable(0f) }
    // 0…1: the bubble fading away.
    val gone = remember { Animatable(1f) }

    val playing = auto && handsOff
    val text = value ?: draft
    val hasText = text.isNotEmpty()
    val seconds by remember { derivedStateOf { max(0f, floor(motion.time.floatValue - motion.recordAt)) } }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) motion.time.floatValue = (withFrameNanos { it } - start) / 1e9f
    }

    LaunchedEffect(hasText) { arm.animateTo(if (hasText) 1f else 0f, tween(150, easing = EaseOut)) }

    fun bubble() = BubbleFrame(lift.value, unfold.value, gone.value, reducedMotion)

    fun startRecording() {
        phase = Phase.Recording
        focusManager.clearFocus()
        motion.recordAt = motion.time.floatValue
        scope.launch { rec.animateTo(1f, tween(200, easing = EaseOut)) }
    }

    /** Ends the take and returns its length in seconds. */
    fun stopRecording(): Float {
        val now = motion.time.floatValue
        val length = max(SHORTEST_TAKE, now - motion.recordAt)
        motion.take = length
        motion.stopAt = now
        motion.carrying = false
        takeSeconds = length
        phase = Phase.Preview
        scope.launch { rec.animateTo(0f, tween(180, easing = EaseOut)) }
        scope.launch { pre.animateTo(1f, tween(220, easing = EaseOut)) }
        return length
    }

    fun stopPreview() {
        motion.previewPlayAt = -1f
        previewPlaying = false
    }

    fun togglePreview() {
        if (previewPlaying) {
            stopPreview()
            return
        }
        motion.previewPlayAt = motion.time.floatValue
        previewPlaying = true
        previewRuns += 1
    }

    fun stopBubble() {
        motion.bubblePlayAt = -1f
        bubblePlaying = false
    }

    fun toggleBubble() {
        if (bubblePlaying) {
            stopBubble()
            return
        }
        motion.bubblePlayAt = motion.time.floatValue
        bubblePlaying = true
        bubbleRuns += 1
    }

    fun discard() {
        phase = Phase.Idle
        stopPreview()
        scope.launch { pre.animateTo(0f, tween(200, easing = EaseOut)) }
    }

    /** The preview leaves the composer and becomes the bubble; the transcript follows by itself. Returns its length. */
    fun sendVoice(): Float {
        val length = motion.take
        motion.sent = length
        motion.sentAt = motion.time.floatValue
        motion.carrying = true
        sentSeconds = length
        phase = Phase.Idle
        stopPreview()
        stopBubble()
        transcribed = false
        bubbleShown = true
        scope.launch { pre.animateTo(0f, tween(300, easing = EaseOut)) }
        scope.launch {
            lift.snapTo(0f)
            lift.animateTo(1f, if (reducedMotion) tween(200, easing = EaseOut) else tween(500, easing = LiftBack))
        }
        scope.launch { unfold.snapTo(0f) }
        scope.launch { gone.snapTo(0f) }
        sends += 1
        return length
    }

    fun fadeBubble() {
        stopBubble()
        bubbleShown = false
        scope.launch { gone.animateTo(1f, tween(350, easing = EaseInOut)) }
    }

    /** Back to the empty composer, whatever it was doing. */
    fun reset() {
        phase = Phase.Idle
        stopPreview()
        scope.launch { rec.animateTo(0f, tween(200, easing = EaseOut)) }
        scope.launch { pre.animateTo(0f, tween(200, easing = EaseOut)) }
    }

    LaunchedEffect(sends) {
        if (sends == 0) return@LaunchedEffect
        delay((GENERATE * 1000).toLong())
        transcribed = true
        unfold.animateTo(1f, tween(450, easing = SoftBack))
    }

    LaunchedEffect(previewRuns) {
        if (!previewPlaying) return@LaunchedEffect
        delay((motion.take * 1000).toLong())
        stopPreview()
    }

    LaunchedEffect(bubbleRuns) {
        if (!bubblePlaying) return@LaunchedEffect
        delay((motion.sent * 1000).toLong())
        stopBubble()
    }

    // autoPlay: record, stop, play a little, send; the bubble fades before the loop closes.
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val epoch = System.nanoTime()
        var lap = 0
        while (true) {
            val start = epoch + (lap * LOOP * 1e9).toLong()
            val steps = listOf<Triple<Float, Control, () -> Unit>>(
                Triple(RECORD_AT, Control.Main) { startRecording() },
                Triple(STOP_AT, Control.Main) { stopRecording() },
                Triple(PLAY_AT, Control.Play) { togglePreview() },
                Triple(SEND_AT, Control.Main) { sendVoice() },
            )
            for ((at, control, step) in steps) {
                sleepUntil(start + (at * 1e9).toLong())
                scriptPress = control
                sleepUntil(start + ((at + PRESS) * 1e9).toLong())
                scriptPress = null
                step()
            }
            sleepUntil(start + (FADE_AT * 1e9).toLong())
            fadeBubble()
            lap += 1
            sleepUntil(epoch + (lap * LOOP * 1e9).toLong())
        }
    }

    // Any touch stops the script; it comes back a few seconds after the last one, or after a take
    // or a transcript that nobody touches. A take in progress is never cut short.
    LaunchedEffect(auto, handsOff, phase, transcribed, touches, text) {
        if (!auto || handsOff || phase == Phase.Recording) return@LaunchedEffect
        delay((RESUME_AFTER * 1000).toLong())
        if (focused) return@LaunchedEffect
        reset()
        fadeBubble()
        draft = ""
        handsOff = true
    }

    fun touch() {
        handsOff = false
        scriptPress = null
        touches += 1
    }

    fun edit(next: String) {
        if (!controlled) draft = next
        onValueChange?.invoke(next)
    }

    fun sendText() {
        if (!hasText) return
        edit("")
        focusManager.clearFocus()
        onSend?.invoke(text)
    }

    /** The button at the right end of the pill: it starts a take, stops it, or sends what is there. */
    fun pressMain() {
        touch()
        when {
            phase == Phase.Recording -> onRecordEnd?.invoke(stopRecording())
            phase == Phase.Preview -> onSendVoice?.invoke(sendVoice())
            hasText -> sendText()
            else -> {
                startRecording()
                onRecordStart?.invoke()
            }
        }
    }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp).clip(RoundedCornerShape(RADIUS.dp))) {
        // The sunset panel, the frosted shapes and every bar of the waveforms.
        Canvas(Modifier.fillMaxSize()) {
            scale(density, density, pivot = Offset.Zero) {
                val now = motion.time.floatValue
                val pillY = ROW_Y + PILL_HEIGHT / 2f
                drawRect(Brush.verticalGradient(colorStops = Sky, startY = 0f, endY = HEIGHT), size = Size(WIDTH, HEIGHT))
                drawRoundRect(Frost, Offset(PAD, INCOMING_Y), Size(INCOMING_WIDTH, INCOMING_HEIGHT), CornerRadius(18f))
                drawCircle(Frost, PLUS / 2f, Offset(PAD + PLUS / 2f, ROW_Y + 2f + PLUS / 2f))
                drawRoundRect(Frost, Offset(PILL_X, ROW_Y), Size(PILL_WIDTH, PILL_HEIGHT), CornerRadius(PILL_HEIGHT / 2f))

                // The take: a bar is born at the right end every sixteenth of a second and travels
                // left, fading out as it reaches the left end of the pill.
                if (rec.value > 0f) {
                    val fade = Brush.horizontalGradient(
                        listOf(Ink.copy(alpha = 0f), Ink), startX = PILL_X + LIVE_LEFT, endX = PILL_X + LIVE_LEFT + LIVE_FADE,
                    )
                    val head = max(now - motion.recordAt, 0f) * RATE
                    val newest = floor(head).toInt() - 1
                    for (k in max(0, newest - LIVE_BARS)..newest) {
                        val age = head - (k + 1)
                        val grown = 1f - (1f - min(age / (GROW * RATE), 1f)).pow(3)
                        bar(fade, PILL_X + LIVE_RIGHT - age * PITCH, pillY, level(k) * (if (reducedMotion) 1f else grown), rec.value)
                    }
                }

                val frame = bubble()
                // The preview: the same take, squeezed between the play button and its length.
                // Once sent, it rides up with the bubble as it fades.
                if (pre.value > 0f) {
                    take(
                        bars = PREVIEW_BARS,
                        x = PILL_X + PREVIEW_X + if (motion.carrying) frame.x - PILL_X else 0f,
                        y = pillY + if (motion.carrying) frame.y - ROW_Y else 0f,
                        seconds = motion.take,
                        played = if (motion.previewPlayAt < 0f) -1f else ((now - motion.previewPlayAt) / motion.take).coerceIn(0f, 1f),
                        settle = if (reducedMotion) -1f else now - motion.stopAt,
                        alpha = pre.value,
                    )
                }

                if (frame.fill > 0f) {
                    drawRoundRect(
                        Frost, Offset(frame.x, frame.y), Size(frame.width, frame.height), CornerRadius(frame.radius), alpha = frame.fill,
                    )
                    take(
                        bars = BUBBLE_BARS,
                        x = frame.x + 52f,
                        y = frame.y + 28f,
                        seconds = motion.sent,
                        played = if (motion.bubblePlayAt < 0f) -1f else ((now - motion.bubblePlayAt) / motion.sent).coerceIn(0f, 1f),
                        settle = -1f,
                        alpha = frame.ink,
                    )
                }
            }
        }

        Box(
            Modifier.offset(PAD.dp, INCOMING_Y.dp).height(INCOMING_HEIGHT.dp).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(incoming, style = MessageStyle, maxLines = 1)
        }

        // What sits on the sent bubble: its play button, the label or the transcript, and the length.
        Box(
            Modifier.width(BUBBLE_WIDTH.dp).graphicsLayer {
                val frame = bubble()
                translationX = frame.x.dp.toPx()
                translationY = frame.y.dp.toPx()
                alpha = frame.ink
            },
        ) {
            Box(Modifier.offset(12.dp, 12.dp)) {
                Tap(
                    label = if (bubblePlaying) "Stop" else "Play voice note",
                    size = 32f,
                    fill = Well,
                    enabled = bubbleShown,
                    onClick = {
                        touch()
                        toggleBubble()
                    },
                ) { PlayIcon(bubblePlaying) }
            }
            Box(
                Modifier
                    .offset(12.dp, BUBBLE_TEXT_Y.dp)
                    .size(BUBBLE_TEXT.dp, TEXT_LINE.dp)
                    .clipToBounds()
                    .graphicsLayer { alpha = 1f - min(1f, unfold.value * 3f) },
            ) {
                BasicText(generatingLabel, style = LabelStyle, maxLines = 1)
                if (!reducedMotion) {
                    // The shimmer: two windows, one inside the other, keep crossing the label and
                    // show a darker copy of it, so only the letters change.
                    for ((width, ink) in SHIMMER_WINDOWS) {
                        fun x(): Float {
                            val sweep = (max(motion.time.floatValue - motion.sentAt, 0f) % SHIMMER) / SHIMMER
                            return -SHIMMER_WIDTH + (BUBBLE_TEXT + SHIMMER_WIDTH) * sweep + (SHIMMER_WIDTH - width) / 2f
                        }
                        Box(Modifier.size(width.dp, TEXT_LINE.dp).graphicsLayer { translationX = x().dp.toPx() }.clipToBounds()) {
                            BasicText(
                                generatingLabel,
                                style = NoteStyle,
                                maxLines = 1,
                                modifier = Modifier
                                    .wrapContentWidth(Alignment.Start, unbounded = true)
                                    .graphicsLayer {
                                        translationX = -x().dp.toPx()
                                        alpha = ink
                                    }
                                    .clearAndSetSemantics {},
                            )
                        }
                    }
                }
            }
            if (transcribed) {
                // The transcript is uncovered from its first line down as the bubble grows.
                Box(
                    Modifier
                        .offset(12.dp, BUBBLE_TEXT_Y.dp)
                        .width(BUBBLE_TEXT.dp)
                        .graphicsLayer { alpha = (unfold.value * 1.5f).coerceIn(0f, 1f) }
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val shown = TEXT_LINE.dp.toPx() * (1f + unfold.value.coerceIn(0f, 1f))
                            layout(placeable.width, shown.roundToInt()) { placeable.place(0, 0) }
                        },
                ) {
                    BasicText(transcript, style = NoteStyle, maxLines = 2, modifier = Modifier.height((TEXT_LINE * 2f).dp))
                }
            }
            BasicText(
                clock(sentSeconds),
                style = LengthStyle,
                maxLines = 1,
                modifier = Modifier
                    .offset((BUBBLE_WIDTH - 12f - 36f).dp, 0.dp)
                    .width(36.dp)
                    .graphicsLayer { translationY = (bubble().height - 12f - TEXT_LINE).dp.toPx() },
            )
        }

        Box(Modifier.offset(PAD.dp, (ROW_Y + 2f).dp)) {
            Tap(label = "Attach", size = PLUS, onClick = { onAttach?.invoke() }) { Glyph(Icons.plus, 1.8f) }
        }

        BasicTextField(
            value = text,
            onValueChange = {
                touch()
                edit(it)
            },
            enabled = phase == Phase.Idle,
            modifier = Modifier
                .offset((PILL_X + 16f).dp, ROW_Y.dp)
                .size((PILL_WIDTH - 16f - 44f).dp, PILL_HEIGHT.dp)
                .graphicsLayer { alpha = (1f - rec.value) * (1f - pre.value) }
                .onFocusChanged { state ->
                    if (state.isFocused != focused) {
                        focused = state.isFocused
                        // A tap on the field takes the composer over from the script and starts from rest.
                        if (focused && playing) reset()
                        touch()
                    }
                }
                .semantics { contentDescription = placeholder },
            textStyle = MessageStyle,
            singleLine = true,
            cursorBrush = SolidColor(Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { sendText() }),
            decorationBox = { field ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (!hasText) BasicText(placeholder, style = PlaceholderStyle, maxLines = 1)
                    field()
                    if (playing && phase == Phase.Idle) {
                        // The script does not focus the field, so it draws the caret a focused one shows.
                        Box(
                            Modifier
                                .offset((-2).dp, 0.dp)
                                .size(1.5.dp, 20.dp)
                                .graphicsLayer { alpha = if (motion.time.floatValue % 1f < 0.55f) 1f else 0f }
                                .background(Ink, CircleShape),
                        )
                    }
                }
            },
        )

        Box(
            Modifier
                .offset((PILL_X + TIMER_RIGHT - 40f).dp, ROW_Y.dp)
                .size(40.dp, PILL_HEIGHT.dp)
                .graphicsLayer { alpha = rec.value },
            contentAlignment = Alignment.CenterEnd,
        ) {
            BasicText(clock(if (phase == Phase.Recording) seconds else takeSeconds), style = TimerStyle, maxLines = 1)
        }

        // The preview's length and its play button ride up with the bubble once it is sent.
        Box(
            Modifier
                .offset((PILL_X + DISCARD_X - 2f - 34f).dp, ROW_Y.dp)
                .size(34.dp, PILL_HEIGHT.dp)
                .graphicsLayer {
                    val frame = bubble()
                    alpha = pre.value
                    translationX = if (motion.carrying) (frame.x - PILL_X).dp.toPx() else 0f
                    translationY = if (motion.carrying) (frame.y - ROW_Y).dp.toPx() else 0f
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            BasicText(clock(takeSeconds), style = TimerStyle, maxLines = 1)
        }
        Box(
            Modifier.offset((PILL_X + 4f).dp, (ROW_Y + 4f).dp).graphicsLayer {
                val frame = bubble()
                alpha = pre.value
                translationX = if (motion.carrying) (frame.x - PILL_X).dp.toPx() else 0f
                translationY = if (motion.carrying) (frame.y - ROW_Y).dp.toPx() else 0f
            },
        ) {
            Tap(
                label = if (previewPlaying) "Stop" else "Play",
                held = scriptPress == Control.Play,
                enabled = phase == Phase.Preview,
                onClick = {
                    touch()
                    togglePreview()
                },
            ) { PlayIcon(previewPlaying) }
        }

        Box(Modifier.offset((PILL_X + DISCARD_X).dp, (ROW_Y + 7f).dp).graphicsLayer { alpha = pre.value }) {
            Tap(
                label = "Discard",
                size = 30f,
                enabled = phase == Phase.Preview,
                onClick = {
                    touch()
                    discard()
                    onDiscard?.invoke()
                },
            ) { Glyph(Icons.cross, 1.7f, Mute) }
        }

        Box(Modifier.offset((PILL_X + MAIN_X).dp, (ROW_Y + 4f).dp)) {
            Tap(
                label = if (phase == Phase.Recording) "Stop recording" else if (phase == Phase.Preview || hasText) "Send" else "Record a voice note",
                held = scriptPress == Control.Main,
                onClick = { pressMain() },
            ) {
                fun idle() = (1f - rec.value) * (1f - pre.value)
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = max(rec.value, max(pre.value, idle() * arm.value)) }.background(Well, CircleShape))
                Glyph(
                    Icons.wave, 1.8f,
                    modifier = Modifier.graphicsLayer {
                        val wave = idle() * (1f - arm.value)
                        alpha = wave
                        scaleX = 0.6f + 0.4f * wave
                        scaleY = 0.6f + 0.4f * wave
                    },
                )
                Box(
                    Modifier
                        .size(12.dp)
                        .graphicsLayer {
                            alpha = rec.value
                            scaleX = 0.6f + 0.4f * rec.value
                            scaleY = 0.6f + 0.4f * rec.value
                        }
                        .background(Ink, RoundedCornerShape(3.5.dp)),
                )
                Glyph(
                    Icons.arrow, 1.9f,
                    modifier = Modifier.graphicsLayer {
                        val arrow = max(pre.value, idle() * arm.value)
                        alpha = arrow
                        scaleX = 0.6f + 0.4f * arrow
                        scaleY = 0.6f + 0.4f * arrow
                    },
                )
            }
        }
    }
}

/** A bar of the waveform: a round-capped stroke 2 points wide, [amount] of the way to full height. */
private fun DrawScope.bar(brush: Brush, x: Float, y: Float, amount: Float, alpha: Float) {
    val half = (BAR_MIN + (BAR_MAX - BAR_MIN) * amount) / 2f - 1f
    drawLine(brush, Offset(x + 1f, y - half), Offset(x + 1f, y + half), 2f, StrokeCap.Round, alpha = alpha)
}

/**
 * A finished take as [bars] bars. While it plays ([played] 0…1, negative when it does not), the
 * part behind the playhead keeps its ink. [settle] is the time since the take was stopped.
 */
private fun DrawScope.take(bars: Int, x: Float, y: Float, seconds: Float, played: Float, settle: Float, alpha: Float) {
    val samples = max(1, floor(seconds * RATE).toInt())
    for (index in 0 until bars) {
        // Stopping sends one ripple along the bars before they stand still.
        val ripple = 1f + RIPPLE * wobble(settle - index * 0.008f)
        val after = played >= 0f && index >= played * bars
        bar(SolidColor(if (after) Faint else Ink), x + index * PITCH, y, level(index * samples / bars) * ripple, alpha)
    }
    if (played >= 0f) {
        val head = x + played * bars * PITCH - 1f
        drawRoundRect(Ink, Offset(head - 0.75f, y - 14f), Size(1.5f, 28f), CornerRadius(0.75f), alpha = alpha)
    }
}

@Composable
private fun Glyph(path: Path, width: Float, color: Color = Ink, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun PlayIcon(playing: Boolean) {
    Canvas(Modifier.size(18.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            val path = if (playing) Icons.pause else Icons.triangle
            drawPath(path, Ink)
            drawPath(path, Ink, style = Stroke(1.6f, join = StrokeJoin.Round))
        }
    }
}

/** A tappable part of the composer: dips while pressed (or while the script presses it), with a light haptic. */
@Composable
private fun Tap(
    label: String,
    onClick: () -> Unit,
    size: Float = 36f,
    fill: Color = Color.Transparent,
    held: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val dip by animateFloatAsState(if (pressed || held) 0.86f else 1f, tween(PRESS_SCALE_MS, easing = EaseOutStrong), label = "press")
    Box(
        Modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = dip
                scaleY = dip
            }
            .background(fill, CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
