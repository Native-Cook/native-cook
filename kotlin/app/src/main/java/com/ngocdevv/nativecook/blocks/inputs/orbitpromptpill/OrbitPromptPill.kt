package com.ngocdevv.nativecook.blocks.inputs.orbitpromptpill

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A picture attached to the prompt: its address, and the flat colour shown until it arrives. */
data class OrbitPromptPillPhoto(val url: String, val tint: Color)

private const val WIDTH = 350f
private const val HEIGHT = 300f
private const val PILL_WIDTH = 330f
private const val PILL_HEIGHT = 52f
private const val PILL_X = (WIDTH - PILL_WIDTH) / 2f
private const val PILL_Y = (HEIGHT - PILL_HEIGHT) / 2f

// Centre of "+" from the pill's left end, and where the text starts.
private const val PLUS_X = 27f
private const val FIELD_X = 50f
private const val SEND_SIZE = 38f

// Centre of the send button, in the component's box.
private const val SEND_X = PILL_X + PILL_WIDTH - 7f - SEND_SIZE / 2f
private const val FIELD_WIDTH = PILL_WIDTH - FIELD_X - SEND_SIZE - 16f
private const val PRESS_DIP = 0.08f

// The pill grows from this scale as it fades in.
private const val INTRO_SCALE = 0.92f

// The canvas of the pill overflows it by this much on every side to fit its shadow.
private val BLEED = 30.dp

// Back to front: far pictures (each shifted by its depth, -1…1), chips, the pill, near pictures.
private const val BACK_LAYER = 2f
private const val CHIP_LAYER = 4f
private const val PILL_LAYER = 5f
private const val FRONT_LAYER = 8f

private const val MAX_TAGS = 6
private const val CHIP_HEIGHT = 22f

// Between the chips and the pill's top edge, and from the pill's left end to the first chip.
private const val CHIP_GAP = 5f
private const val CHIP_INSET = 30f

// A chip starts this far down, hidden behind the pill.
private const val CHIP_TRAVEL = 28f
private const val CHIP_STAGGER = 0.15f

private const val MAX_PHOTOS = 7
private const val PHOTO_RADIUS = 12f

// The ring: its radii, how far right of the pill's centre it sits, and its slant.
private const val ORBIT_X = 116f
private const val ORBIT_Y = 92f
private const val ORBIT_SHIFT = 6f
private const val TILT = -0.19f

// Where on the ring the first picture starts, and the direction pictures are nearest in.
private const val START_ANGLE = -1.12f
private const val NEAR_ANGLE = -0.7f

// Turns per second.
private const val SPEED = 0.2f

// A picture's scale halfway between far and near, and how much depth adds or takes.
private const val MID_SCALE = 0.87f
private const val DEPTH_SCALE = 0.25f

// The ring opens once the text has been left alone for this long.
private const val OPEN_DELAY = 0.3f

// Typed letters stay grey until the typing has paused for this long.
private const val SETTLE = 0.25f

// After the ring opens: when the light starts across the pill, and when the first picture leaves.
private const val SWEEP_DELAY = 0.55f
private const val SWEEP_TIME = 0.5f
private const val SWEEP_WIDTH = 110f
private const val BURST_DELAY = 0.7f
private const val BURST_STAGGER = 0.06f

// Sending: each picture takes this long to reach the button, one a little after the other, turning this much more.
private const val FOLD_TIME = 0.55f
private const val FOLD_STAGGER = 0.04f
private const val SPIRAL = 2.4f

// Scale per second the send button is kicked with when the pictures land, and its ring.
private const val PULSE_KICK = 5f
private const val RIPPLE_TIME = 0.5f
private const val RIPPLE_GROW = 0.9f
private const val BLINK = 1.1f

// autoPlay loop (seconds): typing starts, the send button is pressed, the prompt is sent.
private const val TYPE_AT = 0.8f
private const val PRESS_AT = 7.45f
private const val SEND_AT = 7.6f
private const val LOOP = 9.4f

// After a touch, or once the field is left empty, the loop waits this long before it starts over.
private const val RESUME = 3f

/** Seconds between letters as the loop types: uneven like a hand, the same every time. */
private val TypingRhythm = floatArrayOf(0.09f, 0.07f, 0.11f, 0.06f, 0.13f, 0.08f, 0.06f, 0.1f, 0.07f, 0.12f)

/** Side of each picture at scale 1, in dp. */
private val OrbitSizes = floatArrayOf(84f, 66f, 78f, 62f, 74f, 68f, 72f)

private val Ink = Color(0xFF0B0B0B)

private fun orbitPhoto(id: String) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=240&h=240&q=80"

private val Photos = listOf(
    OrbitPromptPillPhoto(orbitPhoto("1441974231531-c6227db76b6e"), Color(0xFF4A6332)),
    OrbitPromptPillPhoto(orbitPhoto("1507525428034-b723cf961d3e"), Color(0xFFC9906C)),
    OrbitPromptPillPhoto(orbitPhoto("1470071459604-3b5ec3a7fe05"), Color(0xFF5F7C4C)),
    OrbitPromptPillPhoto(orbitPhoto("1501785888041-af3ef285b470"), Color(0xFF2F8F9A)),
    OrbitPromptPillPhoto(orbitPhoto("1501594907352-04cda38ebc29"), Color(0xFFB5573C)),
    OrbitPromptPillPhoto(orbitPhoto("1472214103451-9374bd1c798e"), Color(0xFF8A8F45)),
    OrbitPromptPillPhoto(orbitPhoto("1526772662000-3f88f10405ff"), Color(0xFF6D808C)),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun orbitText(size: Float, lineHeight: Float, color: Color, weight: FontWeight) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val FieldStyle = orbitText(15f, 20f, Color(0xFF222222), FontWeight.Medium)
private val PlaceholderStyle = orbitText(15f, 20f, Color(0xFFA8A8A8), FontWeight.Medium)
private val ChipStyle = orbitText(11f, 14f, Color.White, FontWeight.Bold)

private enum class Stage { Closed, Open, Folding }

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

/** When the loop types letter [index] of the script; a new word takes a little longer. */
private fun typedAt(index: Int, script: String): Float {
    var t = TYPE_AT
    for (i in 0 until index) {
        t += TypingRhythm[i % TypingRhythm.size] + if (script[i] == ' ') 0.08f else 0f
    }
    return t
}

/** Per-frame state: the ring, the chips and everything that eases after them. */
private class Sim(var base: Int) {
    var time = 0f
    var clock = 0f
    var looping = false

    /** Seconds the loop still waits after a touch. */
    var hold = 0f

    /** Letters of the script the loop has typed, and whether the text in the field is the loop's. */
    var typed = 0
    var scripted = false
    var pressed = false
    var sent = false

    var hasText = false
    var lastEdit = -10f
    var stage = Stage.Closed
    var openAt = 0f
    var foldAt = 0f

    /** The ring folds into the send button (1) or back behind the pill (0). */
    var toSend = 0f
    var count = base
    val bornAt = FloatArray(MAX_PHOTOS)
    val gone = BooleanArray(MAX_TAGS)

    var intro = 0f
    var ink = 1f
    var fieldAlpha = 1f
    var sendPress = 0f
    val pulse = Chase(1f)
    var pulseAt = Float.POSITIVE_INFINITY
    var rippleAt = -10f
    var plusTurn = 0f
    val plusSpin = Chase(0f)
    val chips = Array(MAX_TAGS) { Chase(0f) }
    val out = Array(MAX_PHOTOS) { Chase(0f) }
    val slot = Array(MAX_PHOTOS) { Chase(0f) }

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

    fun edited(hasText: Boolean) {
        this.hasText = hasText
        lastEdit = time
    }

    fun attach(): Boolean {
        if (count >= MAX_PHOTOS || stage == Stage.Folding) return false
        bornAt[count] = time
        count += 1
        plusTurn += 90f
        return true
    }

    fun send() {
        hasText = false
        fieldAlpha = 0f
        if (stage == Stage.Open) {
            fold(1f)
            pulseAt = time + FOLD_TIME
        } else {
            pulseAt = time
        }
    }

    private fun fold(toSend: Float) {
        stage = Stage.Folding
        foldAt = time
        this.toSend = toSend
    }

    private fun close() {
        stage = Stage.Closed
        count = base
        for (i in 0 until MAX_PHOTOS) out[i].jump(0f)
        for (i in 0 until MAX_TAGS) gone[i] = false
    }

    fun advance(dt: Float, engaged: Boolean, script: String, still: Boolean, setText: (String) -> Unit) {
        time += dt

        if (engaged) hold = RESUME
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping) {
                clock = 0f
                typed = 0
                sent = false
            }
        } else if (looping) {
            clock += dt
            if (clock >= LOOP) {
                clock -= LOOP
                typed = 0
                sent = false
            }
            var letters = 0
            while (letters < script.length && clock >= typedAt(letters, script)) letters += 1
            if (clock < SEND_AT && letters != typed) {
                typed = letters
                scripted = true
                setText(script.take(letters))
                edited(letters > 0)
            }
            pressed = clock >= PRESS_AT && clock < SEND_AT
            if (clock >= SEND_AT && !sent) {
                sent = true
                scripted = false
                setText("")
                send()
            }
        }

        when {
            stage == Stage.Closed && ((hasText && time - lastEdit >= OPEN_DELAY) || count > base) -> {
                stage = Stage.Open
                openAt = time
                // Pictures added to an empty field come out at once; after typing they wait for the light.
                val wait = if (hasText) BURST_DELAY else 0f
                for (i in 0 until count) {
                    slot[i].jump(i.toFloat() / count)
                    bornAt[i] = time + wait + i * BURST_STAGGER
                }
            }
            stage == Stage.Open && !hasText && count <= base -> fold(0f)
            stage == Stage.Folding && time - foldAt >= FOLD_TIME + FOLD_STAGGER * count -> close()
        }
        if (time >= pulseAt) {
            pulseAt = Float.POSITIVE_INFINITY
            rippleAt = time
            pulse.v += PULSE_KICK
        }

        intro = approach(intro, 1f, 7f, dt)
        ink = approach(ink, if (time - lastEdit > SETTLE) 1f else 0f, 10f, dt)
        fieldAlpha = approach(fieldAlpha, 1f, 6f, dt)
        sendPress = approach(sendPress, if (pressed) 1f else 0f, 30f, dt)
        pulse.step(1f, 22f, 0.35f, dt)
        plusSpin.step(plusTurn, 16f, 0.6f, dt)
        for (i in 0 until MAX_TAGS) {
            val up = if (stage == Stage.Open && !gone[i] && time >= openAt + i * CHIP_STAGGER) 1f else 0f
            if (still) chips[i].x = approach(chips[i].x, up, 12f, dt) else chips[i].step(up, 15f, 0.6f, dt)
        }
        for (i in 0 until MAX_PHOTOS) {
            val live = if (stage != Stage.Closed && i < count && time >= bornAt[i]) 1f else 0f
            if (still) out[i].x = approach(out[i].x, live, 10f, dt) else out[i].step(live, 9.5f, 0.55f, dt)
            slot[i].step(i.toFloat() / max(count, 1), 8f, 0.8f, dt)
        }
        tick.floatValue = time
    }
}

private class Spot(val x: Float, val y: Float, val scale: Float, val alpha: Float, val depth: Float, val front: Boolean)

/** Where picture [index] is on the ring right now. */
private fun spot(s: Sim, index: Int, still: Boolean): Spot {
    val folding = s.stage == Stage.Folding
    val turn = if (still) 0f else SPEED * (s.time - s.openAt)
    val fold = if (folding) ((s.time - s.foldAt - index * FOLD_STAGGER) / FOLD_TIME).coerceIn(0f, 1f) else 0f
    val pull = fold * fold * fold
    val reach = (if (still) 1f else s.out[index].x) * (1f - pull)
    val angle = TAU * (s.slot[index].x + turn) + START_ANGLE + SPIRAL * pull
    val depth = sin(angle - NEAR_ANGLE)
    val ex = ORBIT_X * cos(angle) * reach
    val ey = ORBIT_Y * sin(angle) * reach
    val home = WIDTH / 2f + ORBIT_SHIFT
    return Spot(
        x = home + (SEND_X - home) * pull * s.toSend + ex * cos(TILT) - ey * sin(TILT),
        y = HEIGHT / 2f + ex * sin(TILT) + ey * cos(TILT),
        scale = (MID_SCALE + DEPTH_SCALE * depth) * (0.3f + 0.7f * reach.coerceIn(0f, 1f)),
        alpha = (s.out[index].x * (1f - pull) / 0.2f).coerceIn(0f, 1f),
        depth = depth,
        // Near pictures pass in front of the pill, far ones behind it; nothing is in front until it has left the pill.
        front = if (folding) s.toSend > 0.5f else depth > 0f && reach > 0.92f,
    )
}

/**
 * A prompt field whose attachments circle it. Once something is typed, tag chips rise from behind
 * the pill's top edge, a cyan light crosses it and the attached pictures burst out onto a tilted
 * orbit: behind the pill on the far side, in front of it on the near side, larger the nearer they
 * are. "+" adds a picture to the ring; sending spirals them all into the send button.
 *
 * @param value The field's text. Leave it out and the component keeps the text itself.
 * @param autoPlay Types [script], lets the pictures orbit, sends and starts over, in a loop.
 * @param script What the autoPlay loop types.
 * @param photos The pictures that can be attached; the first [attached] of them are on the ring from the start.
 * @param accent Colour of the tag chips and the caret.
 * @param onSend Called with the text and the number of pictures on the ring.
 * @param onAttach Called with the new number of pictures when "+" adds one.
 */
@Composable
fun OrbitPromptPill(
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Ask anything",
    script: String = "What's today's run?",
    tags: List<String> = listOf("Daily", "Trail", "Run"),
    photos: List<OrbitPromptPillPhoto> = Photos,
    attached: Int = 5,
    accent: Color = Color(0xFF2F7BEA),
    onSend: ((text: String, pictures: Int) -> Unit)? = null,
    onAttach: ((pictures: Int) -> Unit)? = null,
    onRemoveTag: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val base = min(attached, min(photos.size, MAX_PHOTOS))

    val sim = remember { Sim(base) }
    var own by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val current = value ?: own

    fun store(text: String) {
        if (value == null) own = text
        onValueChange?.invoke(text)
    }
    val setText by rememberUpdatedState<(String) -> Unit> { store(it) }
    val engaged by rememberUpdatedState(focused)

    LaunchedEffect(auto) { sim.looping = auto }

    LaunchedEffect(base) {
        if (sim.base != base) {
            sim.base = base
            sim.count = base
        }
    }

    LaunchedEffect(reducedMotion, script) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            val busy = engaged || (!sim.scripted && (sim.hasText || sim.count > sim.base))
            sim.advance(dt, busy, script, reducedMotion) { setText(it) }
        }
    }

    fun send() {
        val sent = current.trim()
        if (sent.isEmpty() && sim.count <= sim.base) return
        val pictures = if (sim.stage == Stage.Open) sim.count else 0
        store("")
        sim.touch()
        sim.scripted = false
        sim.send()
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        onSend?.invoke(sent, pictures)
    }

    // Typed letters are grey and turn to ink once the typing pauses; whole steps, so the field is not laid out every frame.
    val grey by remember { derivedStateOf { (34f + 126f * (1f - sim.watch().ink)).roundToInt() } }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .graphicsLayer {
                val scale = if (reducedMotion) 1f else INTRO_SCALE + (1f - INTRO_SCALE) * sim.watch().intro
                scaleX = scale
                scaleY = scale
            },
    ) {
        for (index in 0 until min(photos.size, MAX_PHOTOS)) {
            Picture(sim, index, photos[index], reducedMotion)
        }

        Row(
            Modifier
                .zIndex(CHIP_LAYER)
                .offset((PILL_X + CHIP_INSET).dp, (PILL_Y - CHIP_GAP - CHIP_HEIGHT).dp)
                .height(CHIP_HEIGHT.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            tags.take(MAX_TAGS).forEachIndexed { index, tag ->
                Chip(sim, index, tag, accent, reducedMotion) {
                    sim.touch()
                    sim.gone[index] = true
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onRemoveTag?.invoke(tag)
                }
            }
        }

        Box(
            Modifier
                .zIndex(PILL_LAYER)
                .offset(PILL_X.dp, PILL_Y.dp)
                .size(PILL_WIDTH.dp, PILL_HEIGHT.dp)
                .graphicsLayer {
                    alpha = sim.watch().intro
                    // The shadow reaches past the pill, so the fade must not clip the layer to its bounds.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        ) {
            PillBody(sim, reducedMotion)

            Box(
                Modifier
                    .offset((PLUS_X - 22f).dp, (PILL_HEIGHT / 2f - 22f).dp)
                    .size(44.dp)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        sim.touch()
                        if (sim.attach()) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onAttach?.invoke(sim.count)
                        }
                    }
                    .semantics { contentDescription = "Attach a picture" }
                    .graphicsLayer { rotationZ = sim.watch().plusSpin.x },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val grey = Color(0xFFB4B4B4)
                    drawLine(grey, Offset(12.dp.toPx(), 22.dp.toPx()), Offset(32.dp.toPx(), 22.dp.toPx()), 1.4.dp.toPx(), StrokeCap.Round)
                    drawLine(grey, Offset(22.dp.toPx(), 12.dp.toPx()), Offset(22.dp.toPx(), 32.dp.toPx()), 1.4.dp.toPx(), StrokeCap.Round)
                }
            }

            BasicTextField(
                value = current,
                onValueChange = {
                    if (it != current) {
                        store(it)
                        sim.scripted = false
                        sim.edited(it.isNotEmpty())
                    }
                },
                modifier = Modifier
                    .offset(FIELD_X.dp, 0.dp)
                    .size(FIELD_WIDTH.dp, PILL_HEIGHT.dp)
                    .graphicsLayer { alpha = sim.watch().fieldAlpha }
                    .onFocusChanged {
                        focused = it.isFocused
                        // A tap on the field takes it over from the loop: what the loop typed is cleared.
                        if (it.isFocused && sim.scripted) {
                            sim.scripted = false
                            store("")
                            sim.edited(false)
                        }
                    },
                textStyle = FieldStyle.copy(color = Color(grey, grey, grey)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        send()
                        focusManager.clearFocus()
                    },
                ),
                cursorBrush = SolidColor(accent),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (current.isEmpty()) BasicText(placeholder, style = PlaceholderStyle, maxLines = 1)
                        inner()
                    }
                },
            )

            // The loop's own caret, right after the last letter it typed.
            Row(
                Modifier.offset(FIELD_X.dp, 0.dp).size(FIELD_WIDTH.dp, PILL_HEIGHT.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(current, style = FieldStyle, maxLines = 1, modifier = Modifier.weight(1f, fill = false).alpha(0f))
                Box(
                    Modifier
                        .size(2.dp, 19.dp)
                        .graphicsLayer {
                            val s = sim.watch()
                            val sinceEdit = s.time - s.lastEdit
                            val blink = if (sinceEdit < 0.5f) 1f else (0.5f + 3f * sin(TAU * sinceEdit / BLINK)).coerceIn(0f, 1f)
                            alpha = if (s.scripted && s.hasText && !focused) blink else 0f
                        }
                        .background(accent, RoundedCornerShape(1.dp)),
                )
            }

            val sendSpot = Modifier
                .offset((SEND_X - PILL_X - SEND_SIZE / 2f).dp, ((PILL_HEIGHT - SEND_SIZE) / 2f).dp)
                .size(SEND_SIZE.dp)
            Box(
                sendSpot
                    .graphicsLayer {
                        val ripple = (sim.watch().time - sim.rippleAt) / RIPPLE_TIME
                        val grown = 1f + RIPPLE_GROW * ripple.coerceIn(0f, 1f)
                        scaleX = grown
                        scaleY = grown
                        alpha = if (ripple > 0f && ripple < 1f) 0.5f * max(0f, 1f - ripple) else 0f
                    }
                    .border(2.dp, Ink, CircleShape),
            )
            SendButton(sim, sendSpot) { send() }
        }
    }
}

/** One picture of the ring, its tint showing until the photo has loaded. */
@Composable
private fun Picture(sim: Sim, index: Int, photo: OrbitPromptPillPhoto, still: Boolean) {
    val s = sim.watch()
    val at = spot(s, index, still)
    val size = OrbitSizes[index]
    val shadow = remember { SoftRoundRect(Color.Black, sigma = 8f) }
    val context = LocalPlatformContext.current
    val request = remember(photo.url, context) { ImageRequest.Builder(context).data(photo.url).crossfade(200).build() }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .zIndex((if (at.front) FRONT_LAYER else BACK_LAYER) + at.depth)
            .size(size.dp)
            .graphicsLayer {
                translationX = (at.x - size / 2f).dp.toPx()
                translationY = (at.y - size / 2f).dp.toPx()
                scaleX = at.scale
                scaleY = at.scale
                alpha = at.alpha * s.intro
                // The shadow reaches past the picture, so the fade must not clip the layer to its bounds.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas {
                        shadow.draw(it.nativeCanvas, 0f, 5f, size, size, PHOTO_RADIUS, 0.1f + 0.1f * max(0f, at.depth))
                    }
                }
            }
            .clip(RoundedCornerShape(PHOTO_RADIUS.dp))
            .background(photo.tint),
    )
}

@Composable
private fun Chip(sim: Sim, index: Int, tag: String, accent: Color, still: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier
            .graphicsLayer {
                val up = sim.watch().chips[index].x
                translationY = if (still) 0f else ((1f - up) * CHIP_TRAVEL).dp.toPx()
                alpha = (up * 3f).coerceIn(0f, 1f)
            }
            .height(CHIP_HEIGHT.dp)
            .background(accent, CircleShape)
            // A chip can be removed only once it has risen clear of the pill.
            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                if (sim.chips[index].x > 0.9f) onRemove()
            }
            .semantics { contentDescription = "Remove $tag" }
            .padding(start = 9.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(tag, style = ChipStyle, maxLines = 1)
        Canvas(Modifier.size(8.dp)) {
            val a = 1.dp.toPx()
            val b = 7.dp.toPx()
            drawLine(Color.White, Offset(a, a), Offset(b, b), 1.6.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, Offset(b, a), Offset(a, b), 1.6.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** The pill itself: its shadow, the light that crosses it just before the pictures come out, and its hairline. */
@Composable
private fun PillBody(sim: Sim, still: Boolean) {
    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.07f), sigma = 10f) }
    val capsule = remember { Path() }
    Box(Modifier.fillMaxSize().bleed(BLEED)) {
        Canvas(Modifier.fillMaxSize()) {
            surface(BLEED) { x, y, w, h ->
                val s = sim.watch()
                val radius = CornerRadius(h / 2f)
                drawIntoCanvas { shadow.draw(it.nativeCanvas, x, y + 6f, w, h, h / 2f) }
                drawRoundRect(Color.White, Offset(x, y), Size(w, h), radius)

                val sweep = if (still || s.stage != Stage.Open) -1f else (s.time - s.openAt - SWEEP_DELAY) / SWEEP_TIME
                if (sweep > 0f && sweep < 1f) {
                    val left = x - SWEEP_WIDTH + (w + SWEEP_WIDTH) * sweep
                    capsule.rewind()
                    capsule.addRoundRect(RoundRect(Rect(x, y, x + w, y + h), radius))
                    clipPath(capsule) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color(0x007FE3FF),
                                0.35f to Color(0x8C7FE3FF),
                                0.5f to Color(0xD9C8F6FF),
                                0.65f to Color(0x8C7FE3FF),
                                1f to Color(0x007FE3FF),
                                startX = left,
                                endX = left + SWEEP_WIDTH,
                            ),
                            topLeft = Offset(left, y),
                            size = Size(SWEEP_WIDTH, h),
                        )
                    }
                }

                drawRoundRect(
                    Color(0xFFE6E6E6),
                    Offset(x + 0.5f, y + 0.5f),
                    Size(w - 1f, h - 1f),
                    CornerRadius(h / 2f - 0.5f),
                    style = Stroke(1f),
                )
            }
        }
    }
}

/** The round send button: it dips under a finger, and jumps when the pictures land in it. */
@Composable
private fun SendButton(sim: Sim, modifier: Modifier, onSend: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            if (it is PressInteraction.Press || it is PressInteraction.Release || it is PressInteraction.Cancel) {
                sim.touch()
                sim.pressed = it is PressInteraction.Press
            }
        }
    }
    Box(
        modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onSend)
            .semantics { contentDescription = "Send" }
            .graphicsLayer {
                val s = sim.watch()
                val scale = s.pulse.x * (1f - PRESS_DIP * s.sendPress)
                scaleX = scale
                scaleY = scale
            }
            .background(Ink, CircleShape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val arrow = Path().apply {
                moveTo(19.dp.toPx(), 26.5.dp.toPx())
                lineTo(19.dp.toPx(), 12.dp.toPx())
                moveTo(12.5.dp.toPx(), 18.5.dp.toPx())
                lineTo(19.dp.toPx(), 12.dp.toPx())
                lineTo(25.5.dp.toPx(), 18.5.dp.toPx())
            }
            drawPath(arrow, Color.White, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
