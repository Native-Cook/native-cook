package com.ngocdevv.nativecook.blocks.inputs.holdtospeakinput

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 350f
private const val HEIGHT = 52f
private const val RADIUS = 22f

// Text and level bars start this far from the pill's ends.
private const val INSET = 22f

// The canvas overflows the pill by this much on every side to fit the aurora.
private val BLEED = 40.dp

private const val CHIP_HEIGHT = 36f
private const val CHIP_GAP = 8f
private const val CHIPS_TO_PILL = 16f

// Top of the pill: three rows of chips and the gap under them.
private const val PILL_Y = CHIP_HEIGHT * 3f + CHIP_GAP * 2f + CHIPS_TO_PILL
private const val CHIP_RISE = 10f

// The rows come back one after another, the bottom one first, over this long; they leave together.
private const val CHIPS_IN = 0.6f
private const val CHIPS_OUT = 0.22f
private const val CHIP_STAGGER = 0.22f

private const val HINT_WIDTH = 160f
private const val HINT_HEIGHT = 36f

// Between the hint and the pill while the finger rests on it.
private const val HINT_GAP = 14f

private const val BAR_WIDTH = 2f
private const val BAR_PITCH = 7f
private const val BAR_MIN = 3f
private const val BAR_MAX = 14f

// A press shorter than this is a tap, which focuses the field.
private const val HOLD_DELAY = 0.18f

// Sliding up this far arms the cancel; coming back down past the second line disarms it.
private const val ARM_AT = -40f
private const val DISARM_AT = -28f
private const val MAX_LIFT = 72f

// The label changes side once the finger is this far past the middle.
private const val SIDE_SLACK = 20f

// The mic keeps this far from the pill's ends.
private const val MIC_MARGIN = 30f

// How long the transcript stays in the field, and how far above it starts.
private const val TRANSCRIPT_HOLD = 1.4f
private const val DROP_FROM = 12f

// The aurora at rest: where it sits (share of the width), how far it sways, and how it breathes.
private const val REST_X = 0.6f
private const val REST_SWAY = 0.1f
private const val REST_GLOW = 0.5f
private const val REST_PULSE = 0.1f
private const val REST_SPREAD = 1.7f
private const val REST_SINK = 4f

// Half the length of the lit stretch while it is gathered under the finger.
private const val GLOW_REACH = 110f
private const val BREATH = 3.2f
private const val BLINK = 1.2f

// autoPlay period, seconds. The aurora breathes three times and the caret blinks eight times in it.
private const val LOOP = 9.6f

// After a touch, or after the field loses focus, the loop waits this long before it starts over.
private const val RESUME = 3f

/**
 * The finger of the autoPlay loop: seconds, x as a share of the pill's width, y in dp (up is
 * negative). The first pass speaks and lets go; the second slides up and cancels.
 */
private val ScriptedPasses = arrayOf(
    arrayOf(
        floatArrayOf(0.8f, 0.8f, 0f), floatArrayOf(1.5f, 0.8f, 0f), floatArrayOf(2.1f, 0.22f, 0f),
        floatArrayOf(2.5f, 0.22f, 0f), floatArrayOf(3.0f, 0.56f, 0f), floatArrayOf(3.4f, 0.56f, 0f),
    ),
    arrayOf(
        floatArrayOf(6.0f, 0.76f, 0f), floatArrayOf(6.6f, 0.76f, 0f), floatArrayOf(7.0f, 0.6f, 0f),
        floatArrayOf(7.4f, 0.6f, -64f), floatArrayOf(8.0f, 0.6f, -64f),
    ),
)

/** Writes the scripted finger at [t] into [out] as x and y; false while it is lifted. */
private fun scriptedFinger(t: Float, out: FloatArray): Boolean {
    for (pass in ScriptedPasses) {
        if (t < pass.first()[0] || t >= pass.last()[0]) continue
        for (i in 1 until pass.size) {
            if (t >= pass[i][0]) continue
            val a = pass[i - 1]
            val b = pass[i]
            val u = (t - a[0]) / (b[0] - a[0])
            val eased = u * u * (3f - 2f * u)
            out[0] = a[1] + (b[1] - a[1]) * eased
            out[1] = a[2] + (b[2] - a[2]) * eased
            return true
        }
    }
    return false
}

private val BarRates = floatArrayOf(9.1f, 7.3f, 11.2f, 8.4f, 10.3f)
private val BarRipples = floatArrayOf(17.0f, 13.6f, 19.3f, 15.1f, 12.2f)
private val BarPhases = floatArrayOf(0.3f, 1.9f, 4.1f, 2.6f, 5.2f)

/** The microphone's stand, in a 24 dp box; its body is a rounded rect drawn with it. */
private val MicStand = Path().apply {
    moveTo(6.5f, 10.5f)
    lineTo(6.5f, 11.5f)
    arcTo(Rect(6.5f, 6f, 17.5f, 17f), 180f, -180f, false)
    lineTo(17.5f, 10.5f)
    moveTo(12f, 17f)
    lineTo(12f, 20f)
    moveTo(8.5f, 20f)
    lineTo(15.5f, 20f)
}

private val ChevronMark = Path().apply {
    moveTo(1f, 8.5f)
    lineTo(6f, 3.5f)
    lineTo(11f, 8.5f)
}

private val CrossMark = Path().apply {
    moveTo(1.5f, 1.5f)
    lineTo(10.5f, 10.5f)
    moveTo(10.5f, 1.5f)
    lineTo(1.5f, 10.5f)
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun speakText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val FieldStyle = speakText(16f, 21f, Color(0xFFEDEDED))
private val PlaceholderStyle = speakText(16f, 21f, Color(0xFF9A9A9A))
private val LabelStyle = speakText(16f, 21f, Color(0xFF7A7A7A))
private val HintStyle = speakText(14f, 18f, Color(0xFF9A9A9A))
private val ChipStyle = speakText(15f, 20f, Color(0xFFBDBDBD), FontWeight.Medium)
private val ChipLitStyle = speakText(15f, 20f, Color(0xFFDCDCDC), FontWeight.Medium)

private enum class Phase { Idle, Pressing, Listening, Sent, Clearing }

/** What a frame decided; the flag is "armed" for [Arm] and "by a finger" for [Send]. */
private enum class Signal { Tap, ListenStart, Arm, Send, Cancel, Clear }

/** A value that follows its target like a spring (mass 1, stiffness omega², damping 2·zeta·omega). */
private class Chase(var x: Float) {
    var v = 0f

    fun jump(to: Float) {
        x = to
        v = 0f
    }

    fun step(target: Float, omega: Float, zeta: Float, dt: Float) {
        v += (omega * omega * (target - x) - 2f * zeta * omega * v) * dt
        x += v * dt
    }
}

/** Moves [x] towards [target], covering the same share of the distance in the same time at any frame rate. */
private fun approach(x: Float, target: Float, rate: Float, dt: Float) = x + (target - x) * (1f - exp(-rate * dt))

private fun micSlot(x: Float) = x.coerceIn(MIC_MARGIN, WIDTH - MIC_MARGIN)

/** How far a row of chips has come in, 0…1: [order] 0 is the first to arrive. */
private fun rowProgress(progress: Float, order: Int, rows: Int): Float {
    val window = 1f - CHIP_STAGGER * (rows - 1)
    val p = ((progress - order * CHIP_STAGGER) / window).coerceIn(0f, 1f)
    return 1f - (1f - p).pow(3)
}

/** Per-frame state: a finger (real or scripted) and everything that eases after it. */
private class Sim {
    var time = 0f
    var clock = 0f
    var looping = false

    /** Seconds the loop still waits after a touch. */
    var hold = 0f
    var phase = Phase.Idle
    var down = false

    /** The current run was started by the loop, not by a finger. */
    var scripted = false
    var px = WIDTH / 2f
    var py = 0f
    var downAt = 0f
    var listenAt = 0f
    var sentAt = 0f
    var armed = false
    var labelRight = false

    var listen = 0f
    var field = 1f
    var cancel = 0f
    var side = 0f
    var power = 0f
    var chips = 1f
    val micX = Chase(WIDTH / 2f)
    val lift = Chase(0f)
    val glowX = Chase(WIDTH * REST_X)
    val drop = Chase(0f)
    val pop = Chase(1f)
    private val finger = FloatArray(2)

    // Written last in a frame: reading it is what redraws the component.
    private val tick = mutableFloatStateOf(0f)

    /** Subscribes the caller to the next frame. */
    fun watch(): Sim {
        tick.floatValue
        return this
    }

    fun press(x: Float, scripted: Boolean) {
        down = true
        this.scripted = scripted
        px = x
        py = 0f
        downAt = time
        armed = false
        phase = Phase.Pressing
        if (!scripted) hold = RESUME
    }

    fun move(x: Float, y: Float) {
        px = x
        py = y
    }

    fun release(emit: (Signal, Boolean) -> Unit) {
        if (!down) return
        down = false
        when {
            phase == Phase.Pressing -> {
                phase = Phase.Idle
                if (!scripted) emit(Signal.Tap, true)
            }
            phase == Phase.Listening && armed -> {
                phase = Phase.Idle
                if (!scripted) emit(Signal.Cancel, true)
            }
            phase == Phase.Listening -> {
                show()
                emit(Signal.Send, !scripted)
            }
        }
    }

    /** A text lands in the field, stays a moment and clears. */
    fun show() {
        phase = Phase.Sent
        sentAt = time
        field = 0f
        drop.jump(-DROP_FROM)
    }

    fun advance(dt: Float, engaged: Boolean, still: Boolean, emit: (Signal, Boolean) -> Unit) {
        time += dt

        if (engaged || (!scripted && phase != Phase.Idle)) hold = RESUME
        if (hold > 0f) {
            // The loop was interrupted: its own press ends without sending anything.
            if (down && scripted) {
                down = false
                phase = Phase.Idle
            }
            hold -= dt
            if (hold <= 0f && looping) clock = 0f
        } else if (looping) {
            clock += dt
            if (clock >= LOOP) clock -= LOOP
            if (scriptedFinger(clock, finger)) {
                if (!down) press(finger[0] * WIDTH, scripted = true)
                move(finger[0] * WIDTH, finger[1])
            } else if (down && scripted) {
                release(emit)
            }
        }

        when {
            phase == Phase.Pressing && time - downAt >= HOLD_DELAY -> {
                phase = Phase.Listening
                listenAt = time
                labelRight = px < WIDTH / 2f
                side = if (labelRight) 1f else 0f
                micX.jump(micSlot(px))
                pop.jump(0.5f)
                if (!scripted) emit(Signal.ListenStart, true)
            }
            phase == Phase.Listening -> {
                val now = py < (if (armed) DISARM_AT else ARM_AT)
                if (now != armed) {
                    armed = now
                    if (!scripted) emit(Signal.Arm, now)
                }
                // The label keeps to the side the finger is not on.
                if (px > WIDTH / 2f + SIDE_SLACK) labelRight = false
                if (px < WIDTH / 2f - SIDE_SLACK) labelRight = true
            }
            phase == Phase.Sent && time - sentAt >= TRANSCRIPT_HOLD -> phase = Phase.Clearing
            phase == Phase.Clearing && field < 0.03f -> {
                phase = Phase.Idle
                emit(Signal.Clear, true)
            }
        }

        val listening = phase == Phase.Listening
        listen = approach(listen, if (listening) 1f else 0f, 16f, dt)
        field = approach(field, if (listening || phase == Phase.Clearing) 0f else 1f, 12f, dt)
        cancel = approach(cancel, if (listening && armed) 1f else 0f, 14f, dt)
        side = approach(side, if (labelRight) 1f else 0f, 18f, dt)
        power = approach(power, if (listening) 1f else 0f, 8f, dt)
        chips = if (phase == Phase.Idle) min(1f, chips + dt / CHIPS_IN) else max(0f, chips - dt / CHIPS_OUT)
        if (listening) micX.step(micSlot(px), 30f, 0.9f, dt)
        lift.step(if (listening) py.coerceIn(-MAX_LIFT, 0f) else 0f, 22f, 0.75f, dt)
        val rest = WIDTH * (REST_X + (if (still) 0f else REST_SWAY * sin(TAU * time / BREATH)))
        glowX.step(if (listening) micX.x else rest, 12f, 0.9f, dt)
        drop.step(0f, 18f, 0.55f, dt)
        pop.step(1f, 20f, 0.5f, dt)
        tick.floatValue = time
    }
}

/** One colour set of the aurora: the blurred light behind the pill and the lit stretch of its hairline. */
private class Aurora(colors: List<Color>) {
    private val matrix = Matrix()
    private val glow = gradient(colors, 0f)
    private val edge = gradient(colors, 0.15f)
    private val wide = blurred(14f)
    private val tight = blurred(5f)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    // The stretch runs from -1 to 1 so that a matrix can place it: [ends] is the alpha it fades to.
    private fun gradient(colors: List<Color>, ends: Float) = LinearGradient(
        -1f, 0f, 1f, 0f,
        intArrayOf(
            colors[0].copy(alpha = ends).toArgb(), colors[0].toArgb(), colors[1].toArgb(),
            colors[2].toArgb(), colors[2].copy(alpha = ends).toArgb(),
        ),
        floatArrayOf(0f, 0.24f, 0.5f, 0.76f, 1f),
        Shader.TileMode.CLAMP,
    )

    private fun blurred(sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    /** Centres the lit stretch on [x] and makes it [reach] long on either side. */
    fun place(x: Float, reach: Float) {
        matrix.setScale(reach, 1f)
        matrix.postTranslate(x, 0f)
        glow.setLocalMatrix(matrix)
        edge.setLocalMatrix(matrix)
        wide.shader = glow
        tight.shader = glow
        line.shader = edge
    }

    /** A wide soft halo and a tight bright one, both sunk by [sink] towards the bottom edge. */
    fun drawGlow(canvas: android.graphics.Canvas, x: Float, y: Float, sink: Float, alpha: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        wide.alpha = (alpha * 0.55f * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(x - 2f, y - 2f + sink, x + WIDTH + 2f, y + HEIGHT + 2f + sink, RADIUS + 2f, RADIUS + 2f, wide)
        tight.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(
            x - 1f, y - 1f + sink * 0.5f, x + WIDTH + 1f, y + HEIGHT + 1f + sink * 0.5f, RADIUS + 1f, RADIUS + 1f, tight,
        )
    }

    fun drawEdge(canvas: android.graphics.Canvas, x: Float, y: Float, alpha: Float) {
        line.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(x + 0.5f, y + 0.5f, x + WIDTH - 0.5f, y + HEIGHT - 0.5f, RADIUS - 0.5f, RADIUS - 0.5f, line)
    }
}

/**
 * A chat field that is also a push-to-talk button. Tap it to type; press and hold it to speak: the
 * suggestion chips step aside, level bars and "Listening" replace the placeholder, a mic sits under
 * the finger and the aurora on the pill's edge gathers there and follows it. Slide up to arm the
 * cancel, release in place to drop the transcript into the field.
 *
 * Nothing is recorded here: the bars are a drawn pattern. Start and stop a real recorder from
 * [onRecordStart] and [onRecordEnd], and hand its result in as [transcript].
 *
 * @param value The field's text. Leave it out and the component keeps the text itself.
 * @param autoPlay Holds, speaks and releases by itself, then holds again and cancels, in a loop.
 * @param suggestionRows The suggestion chips, one list per row, top row first.
 * @param highlighted The chip drawn with the gradient outline.
 * @param transcript What a finished recording puts in the field.
 * @param glowColors The aurora along the pill's edge, left to right.
 * @param cancelColors The same while the cancel is armed.
 * @param onSend Called with the text when the keyboard's send key is pressed or a recording is released.
 * @param onRecordEnd Called when the finger lifts; `true` when the recording was cancelled.
 */
@Composable
fun HoldToSpeakInput(
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Start typing or hold to speak.",
    suggestionRows: List<List<String>> = listOf(
        listOf("Plan my day"), listOf("Schedule", "Reschedule"), listOf("Find time", "Create todo list"),
    ),
    highlighted: String = "Plan my day",
    listeningLabel: String = "Listening",
    slideHint: String = "Slide up to cancel",
    cancelHint: String = "Release to cancel",
    transcript: String = "Plan my day around the 3 pm review",
    glowColors: List<Color> = listOf(Color(0xFF2FD6C3), Color(0xFF7BE36A), Color(0xFFF2D94A)),
    cancelColors: List<Color> = listOf(Color(0xFFFF8A3D), Color(0xFFFF4D4F), Color(0xFFFF3B6B)),
    onSend: ((String) -> Unit)? = null,
    onSuggestion: ((String) -> Unit)? = null,
    onRecordStart: (() -> Unit)? = null,
    onRecordEnd: ((cancelled: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }

    val sim = remember { Sim() }
    var own by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    // The text in the field was typed by hand, so the loop must leave it alone.
    var typed by remember { mutableStateOf(false) }
    val current = value ?: own

    fun store(text: String) {
        if (value == null) own = text
        onValueChange?.invoke(text)
    }

    // Applies what a frame decided: text, focus, haptics and callbacks.
    val latest = rememberUpdatedState<(Signal, Boolean) -> Unit> { signal, flag ->
        when (signal) {
            Signal.Tap -> {
                store("")
                typed = false
                focus.requestFocus()
            }
            Signal.ListenStart -> {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onRecordStart?.invoke()
            }
            Signal.Arm -> view.performHapticFeedback(
                if (flag) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK,
            )
            Signal.Send -> {
                store(transcript)
                typed = false
                if (flag) {
                    view.performHapticFeedback(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                        else HapticFeedbackConstants.CONTEXT_CLICK,
                    )
                    onRecordEnd?.invoke(false)
                    onSend?.invoke(transcript)
                }
            }
            Signal.Cancel -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onRecordEnd?.invoke(true)
            }
            Signal.Clear -> store("")
        }
    }
    val emit: (Signal, Boolean) -> Unit = remember { { signal, flag -> latest.value(signal, flag) } }

    LaunchedEffect(auto) { sim.looping = auto }

    LaunchedEffect(reducedMotion) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            sim.advance(dt, engaged = focused || typed, still = reducedMotion, emit)
        }
    }

    fun submit() {
        val sent = current.trim()
        store("")
        typed = false
        focusManager.clearFocus()
        if (sent.isNotEmpty()) onSend?.invoke(sent)
    }

    fun suggest(label: String) {
        store(label)
        typed = false
        sim.scripted = false
        sim.show()
        onSuggestion?.invoke(label)
    }

    val glow = remember(glowColors) { Aurora(glowColors) }
    val stop = remember(cancelColors) { Aurora(cancelColors) }
    val chipsLive by remember { derivedStateOf { sim.watch().chips > 0.9f } }
    val caretHidden = focused || current.isNotEmpty()

    Box(modifier.size(WIDTH.dp, (PILL_Y + HEIGHT).dp)) {
        Column(
            Modifier.size(WIDTH.dp, (PILL_Y - CHIPS_TO_PILL).dp),
            verticalArrangement = Arrangement.spacedBy(CHIP_GAP.dp, Alignment.Bottom),
        ) {
            suggestionRows.forEachIndexed { index, row ->
                Row(
                    Modifier.graphicsLayer {
                        val shown = rowProgress(sim.watch().chips, suggestionRows.size - 1 - index, suggestionRows.size)
                        alpha = shown
                        translationY = if (reducedMotion) 0f else ((1f - shown) * CHIP_RISE).dp.toPx()
                    },
                    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
                ) {
                    for (label in row) {
                        SpeakChip(label, lit = label == highlighted, outline = glowColors, enabled = chipsLive) { suggest(label) }
                    }
                }
            }
        }

        // The aurora behind the pill, the pill, and its hairline.
        Box(Modifier.offset(0.dp, PILL_Y.dp).size(WIDTH.dp, HEIGHT.dp).bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                val s = sim.watch()
                surface(BLEED) { x, y, _, _ ->
                    val breath = if (reducedMotion) 0f else sin(TAU * s.time / BREATH + 1.2f)
                    val alpha = (REST_GLOW + REST_PULSE * breath) * (1f - s.power) + s.power
                    // At rest the light lies wide along the bottom edge; held, it gathers under the finger.
                    val reach = GLOW_REACH * (REST_SPREAD - (REST_SPREAD - 1f) * s.power)
                    val sink = REST_SINK * (1f - s.power)
                    glow.place(x + s.glowX.x, reach)
                    stop.place(x + s.glowX.x, reach)
                    drawIntoCanvas {
                        glow.drawGlow(it.nativeCanvas, x, y, sink, alpha * (1f - s.cancel))
                        stop.drawGlow(it.nativeCanvas, x, y, sink, alpha * s.cancel * 0.9f)
                    }
                    drawRoundRect(Color(0xFF161616), Offset(x, y), Size(WIDTH, HEIGHT), CornerRadius(RADIUS))
                    drawRoundRect(
                        rgba(255, 255, 255, 0.07f),
                        Offset(x + 0.5f, y + 0.5f),
                        Size(WIDTH - 1f, HEIGHT - 1f),
                        CornerRadius(RADIUS - 0.5f),
                        style = Stroke(1f),
                    )
                    drawIntoCanvas {
                        val lit = 0.45f + 0.55f * s.power
                        glow.drawEdge(it.nativeCanvas, x, y, lit * (1f - s.cancel))
                        stop.drawEdge(it.nativeCanvas, x, y, lit * s.cancel)
                    }
                }
            }
        }

        BasicTextField(
            value = current,
            onValueChange = {
                store(it)
                typed = it.isNotEmpty()
            },
            modifier = Modifier
                .offset(INSET.dp, PILL_Y.dp)
                .size((WIDTH - INSET * 2f).dp, HEIGHT.dp)
                .graphicsLayer {
                    val s = sim.watch()
                    alpha = s.field
                    translationY = if (reducedMotion) 0f else s.drop.x.dp.toPx()
                }
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused },
            textStyle = FieldStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            cursorBrush = SolidColor(Color(0xFF4C8DFF)),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (current.isEmpty()) BasicText(placeholder, style = PlaceholderStyle, maxLines = 1)
                    inner()
                }
            },
        )

        // The caret of the resting field; the system draws its own once the field is focused.
        Box(
            Modifier
                .offset((INSET - 1f).dp, (PILL_Y + HEIGHT / 2f - 10f).dp)
                .size(2.dp, 20.dp)
                .graphicsLayer {
                    val s = sim.watch()
                    val blink = (0.5f + 3f * sin(TAU * s.time / BLINK)).coerceIn(0f, 1f)
                    alpha = if (caretHidden) 0f else s.field * blink
                }
                .background(Color(0xFF4C8DFF), RoundedCornerShape(1.dp)),
        )

        for (right in listOf(false, true)) {
            Row(
                Modifier
                    .offset(INSET.dp, PILL_Y.dp)
                    .size((WIDTH - INSET * 2f).dp, HEIGHT.dp)
                    .graphicsLayer {
                        val s = sim.watch()
                        alpha = s.listen * (if (right) s.side else 1f - s.side)
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp, if (right) Alignment.End else Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeakBars(sim, still = reducedMotion)
                BasicText(listeningLabel, style = LabelStyle, maxLines = 1)
            }
        }

        Canvas(
            Modifier.size(24.dp).graphicsLayer {
                val s = sim.watch()
                val scale = if (reducedMotion) 1f else s.pop.x
                translationX = (s.micX.x - 12f).dp.toPx()
                translationY = (PILL_Y + HEIGHT / 2f - 12f + s.lift.x * 0.2f).dp.toPx()
                scaleX = scale
                scaleY = scale
                alpha = s.listen * (1f - s.cancel)
            },
        ) {
            surface(0.dp) { _, _, _, _ ->
                drawRoundRect(Color.White, Offset(9f, 3f), Size(6f, 11f), CornerRadius(3f))
                drawPath(MicStand, Color.White, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        Box(
            Modifier.size(HINT_WIDTH.dp, HINT_HEIGHT.dp).graphicsLayer {
                val s = sim.watch()
                translationX = (s.micX.x - HINT_WIDTH / 2f).coerceIn(0f, WIDTH - HINT_WIDTH).dp.toPx()
                translationY = (PILL_Y - HINT_GAP - HINT_HEIGHT + s.lift.x).dp.toPx()
                alpha = s.listen
            },
        ) {
            SpeakHint(slideHint, cross = false, Color(0xFF9A9A9A), Modifier.graphicsLayer { alpha = 1f - sim.watch().cancel })
            SpeakHint(cancelHint, cross = true, Color(0xFFE8E8E8), Modifier.graphicsLayer { alpha = sim.watch().cancel })
        }

        // A touch on the resting field: let go at once to type, keep it down to speak. Once the field
        // has the focus this layer is gone and touches reach the text.
        if (!focused) {
            Box(
                Modifier
                    .offset(0.dp, PILL_Y.dp)
                    .size(WIDTH.dp, HEIGHT.dp)
                    .semantics {
                        contentDescription = placeholder
                        role = Role.Button
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val first = awaitFirstDown()
                            first.consume()
                            sim.press(first.position.x / density, scripted = false)
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == first.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    sim.move(change.position.x / density, (change.position.y - first.position.y) / density)
                                }
                            } finally {
                                sim.release(emit)
                            }
                        }
                    },
            )
        }
    }
}

/** Five level bars that move like a voice; armed, or on the way out, they flatten into dots. */
@Composable
private fun SpeakBars(sim: Sim, still: Boolean) {
    Canvas(Modifier.size((BAR_PITCH * 4f + BAR_WIDTH).dp, BAR_MAX.dp)) {
        val s = sim.watch()
        val time = if (still) 0.4f else s.time - s.listenAt
        val strength = (1f - s.cancel) * s.listen
        surface(0.dp) { _, _, _, _ ->
            for (i in 0 until 5) {
                val wave = 0.5f + 0.34f * sin(time * BarRates[i] + BarPhases[i]) + 0.22f * sin(time * BarRipples[i] + BarPhases[4 - i])
                val h = BAR_MIN + (BAR_MAX - BAR_MIN) * wave.coerceIn(0f, 1f) * strength
                drawRoundRect(
                    rgba(255, 255, 255, 0.92f),
                    Offset(i * BAR_PITCH, (BAR_MAX - h) / 2f),
                    Size(BAR_WIDTH, h),
                    CornerRadius(BAR_WIDTH / 2f),
                )
            }
        }
    }
}

@Composable
private fun SpeakHint(label: String, cross: Boolean, color: Color, modifier: Modifier) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(12.dp)) {
            surface(0.dp) { _, _, _, _ ->
                drawPath(
                    if (cross) CrossMark else ChevronMark,
                    color,
                    style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        BasicText(label, Modifier.height(18.dp), style = HintStyle.copy(color = color), maxLines = 1, softWrap = false)
    }
}

/** A suggestion chip: scales down while pressed, with a light haptic. */
@Composable
private fun SpeakChip(label: String, lit: Boolean, outline: List<Color>, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    val outlined = if (lit) {
        Modifier.border(1.dp, Brush.horizontalGradient(outline.map { it.copy(alpha = 0.8f) }), CircleShape)
    } else {
        Modifier
    }
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(CHIP_HEIGHT.dp)
            .background(if (lit) Color(0xFF1B201E) else Color(0xFF1E1E1E), CircleShape)
            .then(outlined)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = if (lit) ChipLitStyle else ChipStyle, maxLines = 1)
    }
}
