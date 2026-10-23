package com.ngocdevv.nativecook.blocks.inputs.modeswitcherinput

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import com.ngocdevv.nativecook.shared.sweepStroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ModeIcon { Bolt, Scales, Bulb, Flask }

/**
 * @param label The name in the menu.
 * @param short The shorter name on the pill.
 * @param warm The colour this mode gives the glow and the border at the left end.
 * @param cool The colour at the right end.
 */
data class Mode(val label: String, val short: String, val icon: ModeIcon, val warm: Color, val cool: Color)

private const val WIDTH = 350f
private const val HEIGHT = 100f
private const val RADIUS = 22f

// The canvas of the glows overflows the composer by this much on every side.
private val BLEED = 160.dp
private const val CONTROL = 32f
private const val PILL = 30f

// Height of one line in a pill's rolling label.
private const val ROLL_H = 18f
private const val MENU_W = 172f
private const val ROW_H = 34f
private const val BLINK = 530L
private const val PLACEHOLDER_DELAY = 200

// The light on the box's rim: a stroke this wide, blurred by this much.
private const val RIM_WIDTH = 7f
private const val RIM_BLUR = 7f

// The glows swell and fade over this many seconds, and sway over twice that.
private const val BREATH_PERIOD = 5f
private const val SWAY_PERIOD = 10f

// How long the colours take to change, and the light to run around the border (milliseconds).
private const val SHIFT = 600
private const val LAP = 1200

// The light starts at the send button: degrees clockwise from three o'clock.
private const val LAP_START = 16f
private const val DOT_STEP = 13f

private val Ink = Color(0xFFE6E8EE)
private val Label = Color(0xFFC9CDD6)
private val Chip = Color(0xFF12141B)
private val Edge = Color(0xFF2A2D38)

/** The same spring as the other stacks, from its mass, stiffness and damping. */
private fun physicalSpring(mass: Float, stiffness: Float, damping: Float) =
    spring<Float>(dampingRatio = damping / (2f * sqrt(stiffness * mass)), stiffness = stiffness / mass)

private val RollSpring = physicalSpring(1f, 300f, 24f)
private val MenuSpring = physicalSpring(1f, 380f, 26f)

// autoPlay loop, in seconds from its start.
private const val OPEN_AT = 0.8f

// A scripted tap holds its button down this long before it acts.
private const val PRESS_LEAD = 0.15f

// After the pill is pressed: when the finger rests on a row, and when it chooses it.
private const val POINT_AFTER = 0.9f
private const val CHOOSE_AFTER = 1.15f
private const val FOCUS_AT = 2.9f
private const val TYPE_AT = 3.2f

// The longest the typing may take; a longer script is typed faster.
private const val TYPE_SPAN = 2.2f
private const val SEND_AT = 5.9f
private const val REOPEN_AT = 7.3f
private const val LOOP = 10f

// After the last touch, the loop waits this long before it starts again.
private const val RESUME = 3f

// Milliseconds before each key, used in turn: uneven like a hand, the same in every loop.
private val Rhythm = intArrayOf(90, 60, 110, 70, 50, 130, 80, 60, 100, 70, 150, 60, 90)

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

// Outlines in 16 dp boxes (the chevron in a 10 dp one), the same strings in the three stacks.
private fun outline(d: String) = PathParser().parsePathString(d).toPath()

private val Plus = outline("M 8 3.6 L 8 12.4 M 3.6 8 L 12.4 8")
private val Feather = outline(
    "M 12.8 3.2 C 9.6 2.6 6.6 3.8 5.4 6.4 C 4.8 7.8 4.9 9.4 5.4 10.6 L 8.6 10.6 C 10.6 10.2 12.2 8.8 12.8 6.8 C 13.2 5.6 13.2 4.4 12.8 3.2 Z M 2.8 13.2 L 9.6 6.4",
)
private val Wave = outline("M 2.8 6.6 L 2.8 9.4 M 5.4 4.6 L 5.4 11.4 M 8 2.8 L 8 13.2 M 10.6 5.2 L 10.6 10.8 M 13.2 6.8 L 13.2 9.2")
private val Plane = outline("M 13.6 2.4 L 2.4 6.9 L 6.9 9.1 L 9.1 13.6 Z")
private val Check = outline("M 3.4 8.4 L 6.6 11.4 L 12.6 4.8")
private val Chevron = outline("M 2.5 3.8 L 5 6.3 L 7.5 3.8")
private val ModeIcons = mapOf(
    ModeIcon.Bolt to outline("M 9 1.8 L 3.8 9 L 7.6 9 L 7 14.2 L 12.2 7 L 8.4 7 Z"),
    ModeIcon.Scales to outline(
        "M 8 2.6 L 8 13.4 M 4.8 13.4 L 11.2 13.4 M 3.2 4.6 L 12.8 4.6 M 1.6 9.2 L 3.4 4.8 L 5.2 9.2 C 5.2 10.03 4.39 10.7 3.4 10.7 C 2.41 10.7 1.6 10.03 1.6 9.2 M 10.8 9.2 L 12.6 4.8 L 14.4 9.2 C 14.4 10.03 13.59 10.7 12.6 10.7 C 11.61 10.7 10.8 10.03 10.8 9.2",
    ),
    ModeIcon.Bulb to outline(
        "M 6 10.4 C 4.6 9.6 3.8 8.2 3.8 6.6 C 3.8 4.3 5.7 2.4 8 2.4 C 10.3 2.4 12.2 4.3 12.2 6.6 C 12.2 8.2 11.4 9.6 10 10.4 L 10 11.6 L 6 11.6 Z M 6.6 13.8 L 9.4 13.8",
    ),
    ModeIcon.Flask to outline(
        "M 6 2.4 L 10 2.4 M 6.8 2.4 L 6.8 6.4 L 3.4 12 C 2.9 12.9 3.5 13.8 4.5 13.8 L 11.5 13.8 C 12.5 13.8 13.1 12.9 12.6 12 L 9.2 6.4 L 9.2 2.4 M 5 10.2 L 11 10.2",
    ),
)

private val DefaultModes = listOf(
    Mode("Quick answer", "Quick", ModeIcon.Bolt, Color(0xFFFFB648), Color(0xFFFF5C8A)),
    Mode("Balanced", "Balanced", ModeIcon.Scales, Color(0xFFFF7A59), Color(0xFF4D6BFF)),
    Mode("Deep think", "Deep think", ModeIcon.Bulb, Color(0xFF9B6BFF), Color(0xFF2FD3FF)),
    Mode("Research", "Research", ModeIcon.Flask, Color(0xFF54E08A), Color(0xFF19C7C0)),
)
private val DefaultTones = listOf("Normal", "Concise", "Formal", "Playful")

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun inputText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val FieldStyle = inputText(15f, 20f, Ink)
private val PlaceholderStyle = inputText(15f, 20f, Color(0xFF8A8F9C))
private val PillStyle = inputText(13f, ROLL_H, Label, FontWeight.Medium)
private val RowStyle = inputText(14f, 18f, Color(0xFFAEB3BF))

/** Two colours mixed channel by channel, like the other stacks do it. */
private fun mix(from: Color, to: Color, share: Float) = Color(
    from.red + (to.red - from.red) * share,
    from.green + (to.green - from.green) * share,
    from.blue + (to.blue - from.blue) * share,
)

/** Halfway to white: the colour of what a glow lights up. */
private fun pale(tint: Color) = mix(tint, Color.White, 0.55f)

/** The moment each letter of the script lands, in seconds from the start of the loop. */
private fun keyTimes(count: Int): List<Float> {
    var total = 0f
    val sums = List(count) { index ->
        total += Rhythm[index % Rhythm.size] / 1000f
        total
    }
    val pace = if (total > TYPE_SPAN) TYPE_SPAN / total else 1f
    return sums.map { TYPE_AT + it * pace }
}

/** Waits until [seconds] after [start], a `System.nanoTime()` reading. */
private suspend fun reach(start: Long, seconds: Float) {
    val wait = (start + (seconds * 1e9).toLong() - System.nanoTime()) / 1_000_000
    if (wait > 0) delay(wait)
}

/** Takes no room in its parent: the content hangs from the parent's top-left corner. */
private fun Modifier.overhang() = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) { placeable.place(0, 0) }
}

/** What the glows are drawn from: their clock, the colours they are leaving and heading for. */
private class AuraState(warm: Color, cool: Color) {
    // Written every frame: reading it is what redraws the canvas.
    val clock = mutableFloatStateOf(0f)
    var fromWarm = warm
    var fromCool = cool
    var toWarm = warm
    var toCool = cool
    val shift = Animatable(1f)

    /** The light that runs around the border after a send: 0 at the start of its lap, 1 at the end. */
    val lap = Animatable(1f)

    // The grid of dots behind the composer, in the canvas's coordinates.
    val dots: List<Offset> = buildList {
        var y = DOT_STEP / 2f
        while (y < HEIGHT + BLEED.value * 2f) {
            var x = DOT_STEP / 2f
            while (x < WIDTH + BLEED.value * 2f) {
                add(Offset(x, y))
                x += DOT_STEP
            }
            y += DOT_STEP
        }
    }
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RIM_WIDTH
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(RIM_BLUR), BlurMaskFilter.Blur.NORMAL)
        }
    }
    val glint = sweepStroke(
        0f to rgba(255, 255, 255, 0f),
        0.7f to rgba(255, 255, 255, 0f),
        0.93f to rgba(255, 255, 255, 0.55f),
        1f to Color.White,
    )
}

@Composable
private fun Glyph(outline: Path, modifier: Modifier = Modifier, color: Color = Label, size: Dp = 16.dp, filled: Boolean = false) {
    Canvas(modifier.size(size)) {
        scale(density, density, pivot = Offset.Zero) {
            if (filled) drawPath(outline, color) else drawPath(outline, color, style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A control's content dips under a finger, or under the script's. */
@Composable
private fun Dip(held: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val down by interaction.collectIsPressedAsState()
    val dip by animateFloatAsState(if (down || held) 0.94f else 1f, tween(130, easing = EaseOutStrong), label = "dip")
    Box(
        Modifier
            .graphicsLayer {
                scaleX = dip
                scaleY = dip
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * A pill's label: all the names stand one above the other and the strip rolls to the one in use,
 * while the pill takes that name's width.
 */
@Composable
private fun Roll(labels: List<String>, index: Int, reducedMotion: Boolean) {
    val density = LocalDensity.current
    var widths by remember { mutableStateOf(mapOf<Int, Int>()) }
    val width = remember { Animatable(0f) }
    val row = remember { Animatable(index.toFloat()) }
    val target = (widths[index] ?: 0).toFloat()

    LaunchedEffect(index, target, reducedMotion) {
        val spec = if (reducedMotion) tween(200, easing = EaseOut) else RollSpring
        // The first measure is taken as it is; after that the pill's width moves with the roll.
        if (width.value == 0f) width.snapTo(target) else launch { width.animateTo(target, spec) }
        launch { row.animateTo(index.toFloat(), spec) }
    }

    Box(Modifier.height(ROLL_H.dp).width(with(density) { width.value.coerceAtLeast(0f).toDp() }).clipToBounds()) {
        Column(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .graphicsLayer { translationY = -row.value * ROLL_H.dp.toPx() },
        ) {
            labels.forEachIndexed { slot, label ->
                Box(Modifier.height(ROLL_H.dp), contentAlignment = Alignment.CenterStart) {
                    BasicText(
                        label,
                        style = PillStyle,
                        maxLines = 1,
                        modifier = Modifier.onSizeChanged { size -> if (widths[slot] != size.width) widths = widths + (slot to size.width) },
                    )
                }
            }
        }
    }
}

/** The composer's own caret, shown while the script types. [beat] restarts the blink at every key. */
@Composable
private fun OwnCaret(beat: Int) {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(beat) {
        on = true
        while (true) {
            delay(BLINK)
            on = !on
        }
    }
    Box(Modifier.size(2.dp, 18.dp).graphicsLayer { alpha = if (on) 1f else 0f }.background(Ink, RoundedCornerShape(1.dp)))
}

/**
 * Dark prompt composer lit by two slow glows, with a hairline border that runs from one glow's
 * colour to the other's. Its mode pill opens a menu; choosing a mode rolls the pill's icon and
 * label and shifts glows and border to that mode's colours, and sending runs a light once around
 * the border.
 *
 * @param value The field's text when the parent owns it. Leave it null and the composer keeps its own.
 * @param autoPlay Changes mode, types [script] and sends it by itself in a loop, without the keyboard, until
 *   the composer is touched.
 * @param script What autoPlay types.
 * @param initialMode The mode at rest. autoPlay goes from it to the next one and back.
 * @param tones The tone pill steps through these.
 * @param onSend Called with the trimmed text and the index of the mode.
 */
@Composable
fun ModeSwitcherInput(
    modifier: Modifier = Modifier,
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Ask anything...",
    script: String = "Plan three days in Lisbon",
    modes: List<Mode> = DefaultModes,
    initialMode: Int = 1,
    tones: List<String> = DefaultTones,
    onSend: ((String, Int) -> Unit)? = null,
    onModeChange: ((Int) -> Unit)? = null,
    onToneChange: ((Int) -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val controlled = value != null
    val auto = autoPlay && !controlled && !reducedMotion && modes.size > 1
    val home = initialMode.coerceIn(0, modes.lastIndex)

    var inner by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    // A touch hands the composer to the user until it has been left alone for a while.
    var taken by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }
    // The script's stand-in for focus.
    var engaged by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf<Int?>(null) }
    var tone by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    // The script's finger.
    var heldPill by remember { mutableStateOf(false) }
    var heldRow by remember { mutableStateOf<Int?>(null) }
    var heldSend by remember { mutableStateOf(false) }
    // The text on its way out of the field, and how many messages have been sent.
    var sent by remember { mutableStateOf("") }
    var sends by remember { mutableIntStateOf(0) }

    val text = value ?: inner
    // The script is running: the field is not focused and the composer draws its own caret.
    val playing = auto && !taken
    val current = (mode ?: home).coerceIn(0, modes.lastIndex)
    val idle = auto && taken && !focused && text.isEmpty()
    val away = (home + 1) % modes.size
    val warm = modes[current].warm
    val cool = modes[current].cool

    val aura = remember { AuraState(warm, cool) }
    val menu = remember { Animatable(0f) }
    val ghost = remember { Animatable(1f) }

    /** Any touch takes the composer away from the script. */
    fun interrupt() {
        activity += 1
        if (!playing) return
        taken = true
        engaged = false
        heldPill = false
        heldRow = null
        heldSend = false
    }

    fun write(next: String) {
        if (!controlled) inner = next
        onValueChange?.invoke(next)
    }

    fun submit() {
        interrupt()
        val message = text.trim()
        if (message.isEmpty()) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        onSend?.invoke(message, current)
        write("")
        sent = message
        sends += 1
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            aura.clock.floatValue += min((frame - last) / 1e9f, 0.064f)
            last = frame
        }
    }

    // A new mode: the glows and the border leave the colours they show now for that mode's pair.
    LaunchedEffect(warm, cool, reducedMotion) {
        aura.fromWarm = mix(aura.fromWarm, aura.toWarm, aura.shift.value)
        aura.fromCool = mix(aura.fromCool, aura.toCool, aura.shift.value)
        aura.toWarm = warm
        aura.toCool = cool
        if (reducedMotion) {
            aura.shift.snapTo(1f)
        } else {
            aura.shift.snapTo(0f)
            aura.shift.animateTo(1f, tween(SHIFT, easing = EaseInOut))
        }
    }

    // The menu grows out of the pill's corner.
    LaunchedEffect(menuOpen, reducedMotion) {
        when {
            reducedMotion -> menu.animateTo(if (menuOpen) 1f else 0f, tween(200, easing = EaseOut))
            menuOpen -> menu.animateTo(1f, MenuSpring)
            else -> menu.animateTo(0f, tween(140, easing = EaseIn))
        }
    }

    // The text leaves the field and a light runs around the border.
    LaunchedEffect(sends) {
        if (sends == 0) return@LaunchedEffect
        launch {
            ghost.snapTo(0f)
            ghost.animateTo(1f, tween(250, easing = EaseOut))
        }
        aura.lap.snapTo(0f)
        aura.lap.animateTo(1f, tween(LAP, easing = EaseInOut))
    }

    // One loop after another, every step at its own moment after the loop began.
    LaunchedEffect(playing, script, home, away) {
        if (!playing) return@LaunchedEffect
        val keys = keyTimes(script.length)
        var start = System.nanoTime()

        /** Opens the menu from the pill, rests on a row, and chooses it. */
        suspend fun pick(slot: Int, seconds: Float) {
            reach(start, seconds)
            heldPill = true
            reach(start, seconds + PRESS_LEAD)
            heldPill = false
            menuOpen = true
            reach(start, seconds + POINT_AFTER)
            heldRow = slot
            reach(start, seconds + CHOOSE_AFTER)
            heldRow = null
            mode = slot
            menuOpen = false
        }

        while (true) {
            pick(away, OPEN_AT)
            reach(start, FOCUS_AT)
            engaged = true
            keys.forEachIndexed { index, key ->
                reach(start, key)
                inner = script.take(index + 1)
            }
            reach(start, SEND_AT - PRESS_LEAD)
            heldSend = true
            reach(start, SEND_AT)
            heldSend = false
            engaged = false
            inner = ""
            sent = script
            sends += 1
            pick(home, REOPEN_AT)
            reach(start, LOOP)
            start += (LOOP * 1e9).toLong()
        }
    }

    // The loop starts over from rest a few seconds after the last touch.
    LaunchedEffect(idle, activity) {
        if (!idle) return@LaunchedEffect
        delay((RESUME * 1000).toLong())
        menuOpen = false
        mode = home
        delay(900)
        taken = false
    }

    val menuShadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.5f), sigma = 18f) }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        // Glows, the dots they light up, the box, its border and the light running around it.
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    val time = aura.clock.floatValue
                    val k = aura.shift.value
                    val warmNow = mix(aura.fromWarm, aura.toWarm, k)
                    val coolNow = mix(aura.fromCool, aura.toCool, k)

                    // The glows breathe against each other and sway a little.
                    val breath = 0.5f + 0.5f * sin(time * TAU / BREATH_PERIOD)
                    val sway = sin(time * TAU / SWAY_PERIOD)
                    val bob = cos(time * TAU / BREATH_PERIOD)
                    val blooms = listOf(
                        Triple(Offset(x + 30f + 8f * sway, y + 16f + 5f * bob), 128f * (0.94f + 0.12f * breath), warmNow to 0.6f + 0.2f * breath),
                        Triple(Offset(x + w - 30f - 8f * sway, y + h - 16f - 5f * bob), 138f * (1.06f - 0.12f * breath), coolNow to 0.8f - 0.2f * breath),
                    )
                    for ((center, radius, look) in blooms) {
                        val (tint, strength) = look
                        drawCircle(
                            Brush.radialGradient(
                                0f to tint.copy(alpha = strength),
                                0.42f to tint.copy(alpha = strength * 0.38f),
                                1f to tint.copy(alpha = 0f),
                                center = center,
                                radius = radius,
                            ),
                            radius,
                            center,
                        )
                        // A grid of dots shows only where a glow falls on it.
                        drawPoints(
                            aura.dots,
                            PointMode.Points,
                            Brush.radialGradient(
                                0f to pale(tint).copy(alpha = 0.55f * strength),
                                0.85f to pale(tint).copy(alpha = 0f),
                                center = center,
                                radius = radius,
                            ),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                        )
                    }

                    // The two ends of the box are rimmed with light, as if the glows sat right behind it.
                    aura.rim.shader = LinearGradient(
                        x,
                        0f,
                        x + w,
                        0f,
                        intArrayOf(
                            warmNow.copy(alpha = 0.95f).toArgb(),
                            warmNow.copy(alpha = 0f).toArgb(),
                            coolNow.copy(alpha = 0f).toArgb(),
                            coolNow.copy(alpha = 0.95f).toArgb(),
                        ),
                        floatArrayOf(0f, 0.42f, 0.58f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                    drawIntoCanvas { it.nativeCanvas.drawRoundRect(x, y, x + w, y + h, RADIUS, RADIUS, aura.rim) }

                    drawRoundRect(Color(0xFF07080C), Offset(x, y), Size(w, h), CornerRadius(RADIUS))
                    drawRoundRect(
                        Brush.horizontalGradient(
                            0f to warmNow,
                            0.5f to Color(0xFFD9D4E6).copy(alpha = 0.55f),
                            1f to coolNow,
                            startX = x,
                            endX = x + w,
                        ),
                        Offset(x + 0.5f, y + 0.5f),
                        Size(w - 1f, h - 1f),
                        CornerRadius(RADIUS - 0.5f),
                        style = Stroke(1f),
                    )

                    // After a send a light runs once around the border, from the send button and back to it.
                    val run = aura.lap.value
                    if (run > 0f && run < 1f && !reducedMotion) {
                        val light = min(1f, min(run, 1f - run) * 5f)
                        val turns = (LAP_START + 360f * run) / 360f
                        drawIntoCanvas {
                            aura.glint.draw(it.nativeCanvas, x, y, w, h, RADIUS, 5f, turns, light * 0.25f)
                            aura.glint.draw(it.nativeCanvas, x, y, w, h, RADIUS, 1.5f, turns, light)
                        }
                    }
                }
            }
        }

        Box(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 13.dp).fillMaxWidth().height(22.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val hintShown = text.isEmpty()
            // The placeholder waits for the sent text to clear the field before it returns.
            val hint by animateFloatAsState(
                if (hintShown) 1f else 0f,
                if (hintShown) tween(150, delayMillis = PLACEHOLDER_DELAY, easing = EaseOut) else tween(0),
                label = "hint",
            )
            BasicText(placeholder, style = PlaceholderStyle, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = hint }.clearAndSetSemantics {})
            BasicText(
                sent,
                style = FieldStyle,
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer {
                        val t = ghost.value
                        alpha = 1f - t
                        if (!reducedMotion) translationY = -12.dp.toPx() * t
                    }
                    .clearAndSetSemantics {},
            )
            BasicTextField(
                value = text,
                onValueChange = { write(it) },
                singleLine = true,
                // While the script types, the letters are drawn by the row above it, with its caret.
                textStyle = if (playing) FieldStyle.copy(color = Color.Transparent) else FieldStyle,
                cursorBrush = SolidColor(Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (state.isFocused == focused) return@onFocusChanged
                        focused = state.isFocused
                        // A touch on the field ends the script: the field is emptied and the keyboard takes over.
                        if (focused) {
                            if (playing) inner = ""
                            menuOpen = false
                        }
                        interrupt()
                    }
                    .semantics { contentDescription = placeholder },
            )
            if (playing && engaged) {
                // A trailing space must keep its width in the row that mirrors the typed text.
                val mirror = if (text.endsWith(' ')) text.dropLast(1) + " " else text
                Row(
                    Modifier.clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(mirror, style = FieldStyle, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    OwnCaret(beat = text.length)
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().height(CONTROL.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pill = Modifier.height(PILL.dp).background(Chip, CircleShape).border(1.dp, Edge, CircleShape).padding(start = 8.dp, end = 7.dp)

            Dip(
                held = false,
                label = "Add",
                onClick = {
                    interrupt()
                    onAdd?.invoke()
                },
                modifier = Modifier.size(CONTROL.dp).background(Chip, CircleShape).border(1.dp, Edge, CircleShape),
            ) {
                Glyph(Plus)
            }

            Dip(
                held = false,
                label = "Tone",
                onClick = {
                    interrupt()
                    if (tones.isNotEmpty()) {
                        tone = (tone + 1) % tones.size
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onToneChange?.invoke(tone)
                    }
                },
                modifier = pill.semantics { stateDescription = tones.getOrElse(tone) { "" } },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Feather, size = 14.dp)
                    Roll(tones, tone, reducedMotion)
                    Glyph(Chevron, size = 10.dp)
                }
            }

            Box {
                Dip(
                    held = heldPill,
                    label = "Mode",
                    onClick = {
                        interrupt()
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        menuOpen = !menuOpen
                    },
                    modifier = pill.semantics { stateDescription = modes[current].label },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        // The icon rolls with the label, one mode above the other.
                        val row by animateFloatAsState(
                            current.toFloat(),
                            if (reducedMotion) tween(200, easing = EaseOut) else RollSpring,
                            label = "icon",
                        )
                        Box(Modifier.size(16.dp, ROLL_H.dp).clipToBounds()) {
                            Column(
                                Modifier
                                    .wrapContentHeight(Alignment.Top, unbounded = true)
                                    .graphicsLayer { translationY = -row * ROLL_H.dp.toPx() },
                            ) {
                                for (item in modes) {
                                    Box(Modifier.size(16.dp, ROLL_H.dp), contentAlignment = Alignment.Center) {
                                        Glyph(ModeIcons.getValue(item.icon))
                                    }
                                }
                            }
                        }
                        Roll(modes.map { it.short }, current, reducedMotion)
                        Glyph(Chevron, Modifier.graphicsLayer { rotationZ = 180f * menu.value }, size = 10.dp)
                    }
                }

                // The menu hangs under the pill, over the composer's bottom edge.
                Column(
                    Modifier
                        .overhang()
                        .offset(y = (CONTROL + 5f).dp)
                        .width(MENU_W.dp)
                        .graphicsLayer {
                            val m = menu.value
                            alpha = m.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0f)
                            if (!reducedMotion) {
                                translationY = -6.dp.toPx() * (1f - m)
                                scaleX = 0.86f + 0.14f * m
                                scaleY = 0.86f + 0.14f * m
                            }
                        }
                        .drawBehind {
                            val w = size.width / density
                            val h = size.height / density
                            scale(density, density, pivot = Offset.Zero) {
                                drawIntoCanvas { menuShadow.draw(it.nativeCanvas, 0f, 10f, w, h, 12f) }
                            }
                        }
                        .background(Color(0xFF14161D), RoundedCornerShape(12.dp))
                        .border(1.dp, Edge, RoundedCornerShape(12.dp))
                        .padding(6.dp)
                        .then(if (menuOpen) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    modes.forEachIndexed { slot, item ->
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val down = pressed || heldRow == slot
                        val lit = slot == current || slot == heldRow
                        // A row lights up under a finger, and more softly while it is the mode in use.
                        val fill by animateColorAsState(
                            when {
                                down -> Color(0xFF262A36)
                                slot == current && heldRow == null -> Color(0xFF1E212B)
                                else -> rgba(30, 33, 43, 0f)
                            },
                            tween(120, easing = EaseOut),
                            label = "row",
                        )
                        // The rows follow the panel in, one after another.
                        val shown by animateFloatAsState(
                            if (menuOpen) 1f else 0f,
                            if (menuOpen) tween(200, delayMillis = 50 + slot * 40, easing = EaseOut) else tween(100, easing = EaseOut),
                            label = "shown",
                        )
                        val color = if (lit) Color.White else Color(0xFFAEB3BF)
                        Row(
                            Modifier
                                .graphicsLayer {
                                    alpha = shown
                                    if (!reducedMotion) translationY = 6.dp.toPx() * (1f - shown)
                                }
                                .fillMaxWidth()
                                .height(ROW_H.dp)
                                .background(fill, RoundedCornerShape(8.dp))
                                .clickable(interactionSource = interaction, indication = null, enabled = menuOpen, role = Role.Button) {
                                    interrupt()
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    menuOpen = false
                                    if (slot != current) {
                                        mode = slot
                                        onModeChange?.invoke(slot)
                                    }
                                }
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Glyph(ModeIcons.getValue(item.icon), color = color)
                            BasicText(item.label, style = RowStyle.copy(color = color), maxLines = 1, modifier = Modifier.weight(1f))
                            // The mode in use is ticked.
                            Glyph(Check, Modifier.graphicsLayer { alpha = if (slot == current) 1f else 0f }, color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Dip(
                held = false,
                label = "Voice",
                onClick = {
                    interrupt()
                    onVoice?.invoke()
                },
                modifier = Modifier.size(36.dp, PILL.dp).background(Chip, CircleShape).border(1.dp, Edge, CircleShape),
            ) {
                Glyph(Wave)
            }

            Dip(
                held = heldSend,
                label = "Send",
                onClick = { submit() },
                modifier = Modifier
                    .size(CONTROL.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF7B4DFF), Color(0xFFFF7A59))), CircleShape),
            ) {
                Glyph(Plane, color = Color.White, filled = true)
            }
        }
    }
}
