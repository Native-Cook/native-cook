package com.ngocdevv.nativecook.blocks.inputs.jellyattachcapsule

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

enum class JellyAttachCapsuleKind(internal val label: String) {
    Video("Video"),
    Camera("Camera"),
    Photo("Photo"),
}

private const val WIDTH = 262f
private const val HEIGHT = 64f
private const val PAD = 12f
private const val BUTTON = 40f

// The canvas overflows the capsule by this much on every side to fit its shadow.
private val BLEED = 64.dp
private const val FIELD_X = PAD + BUTTON + 10f
private const val FIELD_WIDTH = WIDTH - FIELD_X - PAD
private const val SHEEN_WIDTH = 70f

/** Centre of the plus (0) and of the three buttons spread evenly to its right. */
private fun slot(index: Int) = PAD + BUTTON / 2f + index * (WIDTH - 2f * PAD - BUTTON) / 3f

// The kick: a sine that dies away. The tilt peaks near 5 degrees a fifth of a second in.
private const val TILT = 10.5f
private const val TILT_OMEGA = 5.24f
private const val TILT_DECAY = 3f
private const val SQUASH = 0.1f
private const val SQUASH_OMEGA = 15f
private const val SQUASH_DECAY = 6.5f

// The buttons pop out with a little overshoot (mass 1, stiffness 260, damping 23) and come home
// without one (mass 1, stiffness 300, damping 34).
private const val OPEN_STIFFNESS = 260f
private val OPEN_RATIO = 23f / (2f * sqrt(OPEN_STIFFNESS))
private const val CLOSE_STIFFNESS = 300f
private val CLOSE_RATIO = 34f / (2f * sqrt(CLOSE_STIFFNESS))
private const val FADE = 0.16f

// The field waits for the buttons to be home before it wipes in.
private const val WIPE_DELAY = 0.22f
private const val WIPE = 0.34f
private const val SHEEN = 0.52f

// A picked button swells by this much, and the capsule closes this long after the tap.
private const val PULSE = 0.22f
private const val PULSE_SECONDS = 0.28f
private const val PICK = 0.3f

// autoPlay (seconds): open, look, pick the camera, close, rest.
private const val LOOP = 5f
private const val OPEN_AT = 0.8f
private const val PICK_AT = 2.5f
private const val SCRIPT_PICK = 2
private const val PRESS = 0.12f

// After a touch the script waits this long, then starts again from rest.
private const val RESUME_AFTER = 4f

private val Capsule = Color(0xFF5B2FF5)
private val ButtonFill = Color(0xFF7B5BFA)
private val FieldFill = Color(0xFF7453F7)
private val Placeholder = Color(0xFFB5A8FC)

/** An outline icon in an 18-point box: a rounded box, an optional round lens, and one open line. */
private class Glyph(val box: FloatArray, val lens: FloatArray?, line: String) {
    val line: Path = PathParser().parsePathString(line).toPath()
}

private val Glyphs = listOf(
    // Video camera: the body and the flared lens at its right.
    Glyph(floatArrayOf(2.2f, 5f, 9.6f, 8f, 2.2f), null, "M11.8 8.2L15.8 5.9L15.8 12.1L11.8 9.8"),
    // Photo camera: the body, the hump of the viewfinder, the lens.
    Glyph(floatArrayOf(2.2f, 5.4f, 13.6f, 9.4f, 2.4f), floatArrayOf(9f, 10.1f, 2.5f), "M6.4 5.4L7.4 3.4L10.6 3.4L11.6 5.4"),
    // Picture: the frame, the sun, the hills.
    Glyph(floatArrayOf(2.8f, 2.8f, 12.4f, 12.4f, 2.8f), floatArrayOf(6.7f, 6.7f, 1.3f), "M3.2 12.6L6.9 9.3L9.8 11.9L11.7 10.4L14.8 13"),
)

private val InputStyle = TextStyle(
    color = Color.White,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** A kick that swings out and dies away: 0 before it, and back to 0 after a second or so. */
private fun wobble(t: Float, omega: Float, decay: Float) = if (t <= 0f) 0f else sin(omega * t) * exp(-decay * t)

private suspend fun sleepUntil(nanos: Long) {
    val millis = (nanos - System.nanoTime()) / 1_000_000
    if (millis > 0) delay(millis)
}

/** Seconds since the capsule appeared, and the moments its wobble and its field are timed from. */
private class Motion {
    // The last two kicks, so a quick second tap adds to the first wobble instead of cutting it.
    var kickAt = -100f
    var kickTurn = 0f
    var earlierAt = -100f
    var earlierTurn = 0f
    var openAt = -100f
    var closeAt = -100f
    var pickAt = -100f
    var picked = 0

    // Reading it is what redraws the capsule.
    val time = mutableFloatStateOf(0f)

    fun tilt() = TILT * (
        kickTurn * wobble(time.floatValue - kickAt, TILT_OMEGA, TILT_DECAY) +
            earlierTurn * wobble(time.floatValue - earlierAt, TILT_OMEGA, TILT_DECAY)
        )

    fun squash() = SQUASH * (
        wobble(time.floatValue - kickAt, SQUASH_OMEGA, SQUASH_DECAY) + wobble(time.floatValue - earlierAt, SQUASH_OMEGA, SQUASH_DECAY)
        )

    /** 0…1 while the button at [index] is being picked, 1 otherwise. */
    fun pulse(index: Int) = if (picked == index) ((time.floatValue - pickAt) / PULSE_SECONDS).coerceIn(0f, 1f) else 1f
}

/**
 * Violet message capsule made of jelly. A tap on its plus tilts and squashes the whole capsule,
 * turns the plus into a cross, dissolves the message field and pops three attachment buttons out
 * of the plus one after another; closing wobbles it the other way and wipes the field back in.
 *
 * @param value Controlled text; leave null and the field keeps its own.
 * @param autoPlay Opens the capsule, picks the camera and closes again in a loop, without opening the keyboard.
 * @param onSend Called with the text when the keyboard's send key is pressed; the field clears.
 * @param onPick Called when one of the three attachment buttons is tapped; the capsule closes after its pulse.
 */
@Composable
fun JellyAttachCapsule(
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Message",
    onSend: ((String) -> Unit)? = null,
    onPick: ((JellyAttachCapsuleKind) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val focusManager = LocalFocusManager.current
    val controlled = value != null
    val auto = autoPlay && !controlled && !reducedMotion

    var draft by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    // The button the autoPlay script is pressing: 0 is the plus, -1 is none.
    var scriptPress by remember { mutableIntStateOf(-1) }
    // False once a touch has taken the capsule from the autoPlay script.
    var handsOff by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var picks by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val motion = remember { Motion() }
    val spin = remember { Animatable(0f) }

    val playing = auto && handsOff
    val text = value ?: draft

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) motion.time.floatValue = (withFrameNanos { it } - start) / 1e9f
    }

    /** Opens or closes, and kicks the jelly: the right end swings up to open and dips to close. */
    fun move(next: Boolean) {
        if (next == open) return
        val now = motion.time.floatValue
        motion.earlierAt = motion.kickAt
        motion.earlierTurn = motion.kickTurn
        motion.kickAt = now
        motion.kickTurn = if (next) -1f else 1f
        if (next) {
            motion.openAt = now
            focusManager.clearFocus()
        } else {
            motion.closeAt = now
        }
        open = next
    }

    /** The picked button pulses, then the capsule closes. */
    fun pick(index: Int) {
        motion.picked = index
        motion.pickAt = motion.time.floatValue
        picks += 1
    }

    LaunchedEffect(open, reducedMotion) {
        spin.animateTo(if (open) 1f else 0f, if (reducedMotion) tween(150) else spring(OPEN_RATIO, OPEN_STIFFNESS))
    }

    LaunchedEffect(picks) {
        if (picks == 0) return@LaunchedEffect
        delay((PICK * 1000).toLong())
        move(false)
    }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val epoch = System.nanoTime()
        var lap = 0
        while (true) {
            val start = epoch + (lap * LOOP * 1e9).toLong()
            sleepUntil(start + (OPEN_AT * 1e9).toLong())
            scriptPress = 0
            sleepUntil(start + ((OPEN_AT + PRESS) * 1e9).toLong())
            scriptPress = -1
            move(true)
            sleepUntil(start + (PICK_AT * 1e9).toLong())
            scriptPress = SCRIPT_PICK
            sleepUntil(start + ((PICK_AT + PRESS) * 1e9).toLong())
            scriptPress = -1
            pick(SCRIPT_PICK)
            lap += 1
            sleepUntil(epoch + (lap * LOOP * 1e9).toLong())
        }
    }

    // Any touch stops the script; it comes back a few seconds after the last one.
    LaunchedEffect(auto, handsOff, touches, text) {
        if (!auto || handsOff) return@LaunchedEffect
        delay((RESUME_AFTER * 1000).toLong())
        if (focused) return@LaunchedEffect
        move(false)
        draft = ""
        handsOff = true
    }

    fun touch() {
        handsOff = false
        scriptPress = -1
        touches += 1
    }

    fun edit(next: String) {
        if (!controlled) draft = next
        onValueChange?.invoke(next)
    }

    fun send() {
        if (text.isEmpty()) return
        edit("")
        focusManager.clearFocus()
        onSend?.invoke(text)
    }

    val shadow = remember { SoftRoundRect(rgba(91, 47, 245, 0.45f), sigma = 15f) }

    // The jelly swings about the plus, where the finger is, and squashes about its middle.
    Box(
        modifier.size(WIDTH.dp, HEIGHT.dp).graphicsLayer {
            transformOrigin = TransformOrigin(slot(0) / WIDTH, 0.5f)
            rotationZ = if (reducedMotion) 0f else motion.tilt()
        },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val squash = if (reducedMotion) 0f else motion.squash()
                scaleX = 1f + 0.6f * squash
                scaleY = 1f - squash
            },
        ) {
            Box(Modifier.matchParentSize().bleed(BLEED)) {
                Canvas(Modifier.fillMaxSize()) {
                    surface(BLEED) { x, y, w, h ->
                        drawIntoCanvas { shadow.draw(it.nativeCanvas, x + 14f, y + 24f, w - 28f, h - 12f, (h - 12f) / 2f) }
                        drawRoundRect(Capsule, Offset(x, y), Size(w, h), CornerRadius(h / 2f))
                    }
                }
            }

            // Opening dissolves the field; closing wipes it back in from the left under a sheen.
            Box(
                Modifier
                    .offset(FIELD_X.dp, PAD.dp)
                    .graphicsLayer {
                        alpha = if (open) 1f - ((motion.time.floatValue - motion.openAt) / FADE).coerceIn(0f, 1f) else 1f
                        shape = CircleShape
                        clip = true
                    }
                    .background(FieldFill)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val sinceClose = motion.time.floatValue - motion.closeAt - WIPE_DELAY
                        val wipe = if (open || reducedMotion) 1f else 1f - (1f - (sinceClose / WIPE).coerceIn(0f, 1f)).pow(3)
                        layout((FIELD_WIDTH.dp.toPx() * wipe).roundToInt(), placeable.height) { placeable.place(0, 0) }
                    },
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = {
                        touch()
                        edit(it)
                    },
                    enabled = !open,
                    modifier = Modifier
                        .size(FIELD_WIDTH.dp, BUTTON.dp)
                        .padding(horizontal = 16.dp)
                        .onFocusChanged { state ->
                            if (state.isFocused != focused) {
                                focused = state.isFocused
                                touch()
                            }
                        }
                        .semantics { contentDescription = placeholder },
                    textStyle = InputStyle,
                    singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { field ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) BasicText(placeholder, style = InputStyle.copy(color = Placeholder), maxLines = 1)
                            field()
                        }
                    },
                )
                Box(
                    Modifier
                        .size(SHEEN_WIDTH.dp, BUTTON.dp)
                        .graphicsLayer {
                            val sheen = if (open || reducedMotion) {
                                1f
                            } else {
                                ((motion.time.floatValue - motion.closeAt - WIPE_DELAY) / SHEEN).coerceIn(0f, 1f)
                            }
                            translationX = (-SHEEN_WIDTH + (FIELD_WIDTH + SHEEN_WIDTH) * sheen).dp.toPx()
                        }
                        .background(Brush.horizontalGradient(listOf(rgba(255, 255, 255, 0f), rgba(255, 255, 255, 0.3f), rgba(255, 255, 255, 0f)))),
                )
            }

            for (kind in JellyAttachCapsuleKind.entries) {
                val index = kind.ordinal + 1
                AttachButton(
                    index = index,
                    label = kind.label,
                    motion = motion,
                    open = open,
                    held = scriptPress == index,
                    still = reducedMotion,
                    onClick = {
                        touch()
                        pick(index)
                        onPick?.invoke(kind)
                    },
                )
            }

            Box(Modifier.offset(PAD.dp, PAD.dp)) {
                Round(
                    label = if (open) "Close attachments" else "Attach",
                    held = scriptPress == 0,
                    onClick = {
                        touch()
                        move(!open)
                    },
                ) {
                    Canvas(Modifier.size(18.dp).graphicsLayer { rotationZ = 45f * spin.value }) {
                        scale(density, density, pivot = Offset.Zero) {
                            drawLine(Color.White, Offset(9f, 3.8f), Offset(9f, 14.2f), 1.8f, StrokeCap.Round)
                            drawLine(Color.White, Offset(3.8f, 9f), Offset(14.2f, 9f), 1.8f, StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }
}

/** One of the three buttons that live under the plus until the capsule opens. */
@Composable
private fun AttachButton(index: Int, label: String, motion: Motion, open: Boolean, held: Boolean, still: Boolean, onClick: () -> Unit) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(open, still) {
        val to = if (open) 1f else 0f
        if (still) {
            pop.animateTo(to, tween(150))
            return@LaunchedEffect
        }
        // They leave one after another, and come home in the opposite order.
        delay(if (open) 60L + 80L * (index - 1) else 70L * (3 - index))
        pop.animateTo(to, if (open) spring(OPEN_RATIO, OPEN_STIFFNESS) else spring(CLOSE_RATIO, CLOSE_STIFFNESS))
    }
    val glyph = Glyphs[index - 1]
    Box(
        Modifier.offset(PAD.dp, PAD.dp).graphicsLayer {
            val out = pop.value
            val grown = if (still) 1f else (0.4f + 0.6f * out) * (1f + PULSE * sin(PI.toFloat() * motion.pulse(index)))
            alpha = out.coerceIn(0f, 1f)
            translationX = ((slot(index) - slot(0)) * (if (still) 1f else out)).dp.toPx()
            scaleX = grown
            scaleY = grown
        },
    ) {
        Round(label = label, held = held, enabled = open, onClick = onClick) {
            // A ring leaves the button that was picked.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val pulse = motion.pulse(index)
                        alpha = 0.6f * (1f - pulse)
                        scaleX = 1f + 0.5f * pulse
                        scaleY = 1f + 0.5f * pulse
                    }
                    .border(1.5.dp, Color.White, CircleShape),
            )
            Canvas(Modifier.size(18.dp)) {
                scale(density, density, pivot = Offset.Zero) {
                    val outline = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val box = glyph.box
                    drawRoundRect(Color.White, Offset(box[0], box[1]), Size(box[2], box[3]), CornerRadius(box[4]), style = outline)
                    glyph.lens?.let { drawCircle(Color.White, it[2], Offset(it[0], it[1]), style = outline) }
                    drawPath(glyph.line, Color.White, style = outline)
                }
            }
        }
    }
}

/** A round button of the capsule: dips while pressed (or while the script presses it), with a light haptic. */
@Composable
private fun Round(label: String, held: Boolean, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val dip by animateFloatAsState(if (pressed || held) 0.88f else 1f, tween(PRESS_SCALE_MS, easing = EaseOutStrong), label = "press")
    Box(
        Modifier
            .size(BUTTON.dp)
            .graphicsLayer {
                scaleX = dip
                scaleY = dip
            }
            .background(ButtonFill, CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
