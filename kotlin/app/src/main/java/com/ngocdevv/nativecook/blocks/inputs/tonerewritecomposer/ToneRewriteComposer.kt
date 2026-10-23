package com.ngocdevv.nativecook.blocks.inputs.tonerewritecomposer

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ToneIcon { Pencil, Check, Smile, Shrink, Heart }

data class Tone(val label: String, val icon: ToneIcon)

private const val WIDTH = 354f
private const val HEIGHT = 332f
private const val RADIUS = 30f

// Inset of the chips and of the field from the panel's edge.
private const val INSET = 14f
private const val BAR_H = 52f
private const val STAGE_H = 176f
private const val BUBBLE_MAX = 258f
private const val BUBBLE_PAD_X = 15f
private const val BUBBLE_PAD_Y = 9f
private const val BUBBLE_R = 19f
private const val CHIP_H = 32f
private const val CHIP_GAP = 8f
private const val FIELD_H = 44f
private const val FIELD_R = 14f
private const val SEND = 30f

// The canvas of the shadows overflows the panel by this much on every side.
private val BLEED = 64.dp

private val Blue = Color(0xFF2D7FF9)
private val Action = Color(0xFF1A73F0)
private val Ink = Color(0xFF1C1E21)

// One pass of the band of light, and of the star's pulse, in milliseconds.
private const val SHIMMER = 1100
private const val BAND = 80f

// How far the band travels in one pass, centred on the bubble.
private const val SWEEP = 360f
private const val WRITE_DELAY = 200L

// The rewrite appears this many letters at a time, this often.
private const val WRITE_STEP = 2
private const val WRITE_TICK = 40L

// Share of a finger's travel the bubble follows, and the travel that turns the page.
private const val PULL_SHARE = 0.4f
private const val SWIPE_DISTANCE = 40f

/** The same spring as the other stacks, from its mass, stiffness and damping. */
private fun physicalSpring(mass: Float, stiffness: Float, damping: Float) =
    spring<Float>(dampingRatio = damping / (2f * sqrt(stiffness * mass)), stiffness = stiffness / mass)

private val SizeSpring = physicalSpring(1f, 240f, 21f)
private val PullSpring = physicalSpring(1f, 300f, 24f)
private val DotSpring = physicalSpring(1f, 380f, 20f)

// autoPlay loop, in seconds from its start.
private const val DRIFT_AT = 0.8f

// The chip row takes this long to reach its end, and this long to come back (milliseconds).
private const val DRIFT = 1400
private const val DRIFT_BACK = 600
private const val PRESS_AT = 2.65f
private const val CHOOSE_AT = 2.85f
private const val SWIPE_AT = 6.6f
private const val SWIPE_TIME = 0.25f

// How far the script pulls the bubble before it lets go.
private const val SWIPE_REACH = 38f
private const val CANCEL_PRESS_AT = 7.5f
private const val CANCEL_AT = 7.7f
private const val LOOP = 9f

// After the sheet is left at rest, the loop waits this long before it starts again.
private const val RESUME = 3f

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

// Outlines, the same strings in the three stacks: 20 dp boxes, and 14 dp boxes for the chips.
private fun outline(d: String) = PathParser().parsePathString(d).toPath()

private val Spark = outline(
    "M 8.4 3.6 C 8.4 9.06 10.74 11.4 16.2 11.4 C 10.74 11.4 8.4 13.74 8.4 19.2 C 8.4 13.74 6.06 11.4 0.6 11.4 C 6.06 11.4 8.4 9.06 8.4 3.6 Z M 16.4 0.8 C 16.4 3.18 17.42 4.2 19.8 4.2 C 17.42 4.2 16.4 5.22 16.4 7.6 C 16.4 5.22 15.38 4.2 13 4.2 C 15.38 4.2 16.4 3.18 16.4 0.8 Z",
)
private val Star = outline(
    "M 10 1.4 C 10 7.42 12.58 10 18.6 10 C 12.58 10 10 12.58 10 18.6 C 10 12.58 7.42 10 1.4 10 C 7.42 10 10 7.42 10 1.4 Z",
)
private val Arrow = outline("M 10 15.2 L 10 5 M 5.8 9.2 L 10 5 L 14.2 9.2")
private val Again = outline(
    "M 4.01 8.4 C 4.69 5.88 6.86 4.05 9.46 3.82 C 12.06 3.6 14.52 5.02 15.62 7.38 M 12.95 6.02 L 15.62 7.38 M 16.29 4.46 L 15.62 7.38 M 15.99 11.6 C 15.31 14.12 13.14 15.95 10.54 16.18 C 7.94 16.4 5.48 14.98 4.38 12.62 M 7.05 13.98 L 4.38 12.62 M 3.71 15.54 L 4.38 12.62",
)
private val ToneIcons = mapOf(
    ToneIcon.Pencil to outline("M 2.6 11.4 L 2.6 9.2 L 9.5 2.3 L 11.7 4.5 L 4.8 11.4 Z M 8 3.8 L 10.2 6"),
    ToneIcon.Check to outline("M 2.4 7.5 L 5.4 10.5 L 11.6 3.7"),
    ToneIcon.Smile to outline(
        "M 12.4 7 C 12.4 9.98 9.98 12.4 7 12.4 C 4.02 12.4 1.6 9.98 1.6 7 C 1.6 4.02 4.02 1.6 7 1.6 C 9.98 1.6 12.4 4.02 12.4 7 Z M 5.2 5.9 L 5.21 5.9 M 8.8 5.9 L 8.81 5.9 M 9.63 8.33 C 9.15 9.35 8.13 10 7 10 C 5.87 10 4.85 9.35 4.37 8.33",
    ),
    ToneIcon.Shrink to outline("M 1.2 7 L 5.8 7 M 3.6 4.6 L 6 7 L 3.6 9.4 M 12.8 7 L 8.2 7 M 10.4 4.6 L 8 7 L 10.4 9.4"),
    ToneIcon.Heart to outline(
        "M 7 11.9 C 7 11.9 1.9 8.9 1.9 5.4 C 1.9 3.7 3.2 2.5 4.7 2.5 C 5.7 2.5 6.5 3 7 3.8 C 7.5 3 8.3 2.5 9.3 2.5 C 10.8 2.5 12.1 3.7 12.1 5.4 C 12.1 8.9 7 11.9 7 11.9 Z",
    ),
)

private val DefaultTones = listOf(
    Tone("Rephrase", ToneIcon.Pencil),
    Tone("Fix grammar", ToneIcon.Check),
    Tone("Make funnier", ToneIcon.Smile),
    Tone("Shorter", ToneIcon.Shrink),
    Tone("Make supportive", ToneIcon.Heart),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun sheetText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = sheetText(15f, 20f, Ink, FontWeight.SemiBold)
private val CancelStyle = sheetText(15f, 20f, Action)
private val ApplyStyle = sheetText(15f, 20f, Action, FontWeight.SemiBold)
private val BubbleStyle = sheetText(15f, 20f, Color.White)
private val ChipStyle = sheetText(13f, 17f, Ink, FontWeight.Medium)
private val FieldStyle = sheetText(15f, 20f, Ink)
private val PlaceholderStyle = sheetText(15f, 20f, Color(0xFF8A8F99))

private enum class Phase { Idle, Thinking, Done }

/** Waits until [seconds] after [start], a `System.nanoTime()` reading. */
private suspend fun reach(start: Long, seconds: Float) {
    val wait = (start + (seconds * 1e9).toLong() - System.nanoTime()) / 1_000_000
    if (wait > 0) delay(wait)
}

@Composable
private fun Icon(
    outline: Path,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Float = 1.5f,
    brush: Brush? = null,
    filled: Boolean = false,
) {
    Canvas(modifier.size(size)) {
        scale(density, density, pivot = Offset.Zero) {
            val style = if (filled) Fill else Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (brush != null) drawPath(outline, brush, style = style) else drawPath(outline, color, style = style)
        }
    }
}

@Composable
private fun Chip(tone: Tone, picked: Boolean, dimmed: Boolean, held: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val down by interaction.collectIsPressedAsState()
    // A chip dips under a finger, or under the script's.
    val dip by animateFloatAsState(if (down || held) 0.94f else 1f, tween(130, easing = EaseOutStrong), label = "dip")
    // The other chips step back while the rewrite is made.
    val alpha by animateFloatAsState(if (dimmed) 0.45f else 1f, tween(200, easing = EaseOut), label = "dim")
    val fill by animateColorAsState(if (picked) Color(0xFFE1EDFF) else Color.White, tween(200, easing = EaseOut), label = "fill")
    val edge by animateColorAsState(
        if (picked) rgba(45, 127, 249, 0.45f) else rgba(30, 50, 110, 0.07f),
        tween(200, easing = EaseOut),
        label = "edge",
    )
    val color by animateColorAsState(if (picked) Action else Ink, tween(200, easing = EaseOut), label = "color")

    Row(
        Modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = dip
                scaleY = dip
            }
            .height(CHIP_H.dp)
            .background(fill, CircleShape)
            .border(1.dp, edge, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ToneIcons.getValue(tone.icon), color, size = 14.dp, strokeWidth = 1.4f)
        BasicText(tone.label, style = ChipStyle.copy(color = color), maxLines = 1)
    }
}

/** One of the dots that tell the versions apart; they pop in once a rewrite exists. */
@Composable
private fun Dot(slot: Int, active: Boolean, shown: Boolean, reducedMotion: Boolean, onClick: () -> Unit) {
    val pop = remember { Animatable(0f) }
    val strength by animateFloatAsState(if (active) 1f else 0.25f, tween(200, easing = EaseOut), label = "dot")

    LaunchedEffect(shown, reducedMotion) {
        if (shown && !reducedMotion) {
            delay(150L + slot * 70L)
            pop.animateTo(1f, DotSpring)
        } else {
            pop.animateTo(if (shown) 1f else 0f, tween(200, easing = EaseOut))
        }
    }

    Box(
        Modifier
            .graphicsLayer {
                val t = pop.value
                alpha = t.coerceIn(0f, 1f) * strength
                if (!reducedMotion) {
                    scaleX = 0.3f + 0.7f * t
                    scaleY = 0.3f + 0.7f * t
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = shown,
                onClick = onClick,
            )
            .size(6.dp)
            .background(Blue, CircleShape),
    )
}

/**
 * Rewrite sheet for a chat draft. The draft sits in the middle as a blue bubble above a row of
 * tone chips and an instruction field. Choosing a tone (or sending an instruction) dissolves the
 * bubble into a shimmering shape, then the rewrite is written into it and the bubble grows to
 * fit; dots under it tell the versions apart and a swipe on the bubble goes back to the original.
 *
 * @param value The instruction field's text when the parent owns it. Leave it null and the sheet keeps its own.
 * @param autoPlay Picks the last tone by itself in a loop, without the keyboard, until the sheet is touched.
 * @param draft The message being rewritten.
 * @param rewritten What the rewrite comes back as. Change it from [onSend] to show another result.
 * @param tones The chips. autoPlay picks the last one.
 * @param thinkTime Seconds the bubble shimmers before the result is written.
 * @param onSend Called with the instruction when a chip is chosen or the field is sent.
 * @param onApply Called with the version on screen.
 */
@Composable
fun ToneRewriteComposer(
    modifier: Modifier = Modifier,
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    title: String = "Rewrite",
    cancelLabel: String = "Cancel",
    applyLabel: String = "Apply",
    draft: String = "It's getting ugly out here",
    rewritten: String = "Things are getting tough out here, but we can get through this together.",
    tones: List<Tone> = DefaultTones,
    placeholder: String = "Describe your changes",
    thinkTime: Float = 1.25f,
    onSend: ((String) -> Unit)? = null,
    onApply: ((String) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val controlled = value != null
    val auto = autoPlay && !controlled && !reducedMotion

    var inner by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    // A touch hands the sheet to the user until it has been left at rest for a while.
    var taken by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.Idle) }
    // A rewrite exists next to the original.
    var hasResult by remember { mutableStateOf(false) }
    // The version on screen: 0 is the draft, 1 the rewrite.
    var index by remember { mutableIntStateOf(0) }
    // Letters of the rewrite written so far, and how many rewrites were asked for and have come back.
    var written by remember { mutableIntStateOf(0) }
    var requests by remember { mutableIntStateOf(0) }
    var answers by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    // The instruction the sheet is working on, or has answered.
    var instruction by remember { mutableStateOf<String?>(null) }
    // Sizes of the two versions' bubbles, measured from their text.
    var sizes by remember { mutableStateOf(listOf(IntSize.Zero, IntSize.Zero)) }
    // The script's finger.
    var heldChip by remember { mutableStateOf<Int?>(null) }
    var heldCancel by remember { mutableStateOf(false) }
    // The chip row has been scrolled to its end by the script.
    var drifted by remember { mutableStateOf(false) }

    val text = value ?: inner
    // The script is running.
    val playing = auto && !taken
    val thinking = phase == Phase.Thinking
    // The field holds the instruction being worked on: its button turns into "again".
    val answered = phase != Phase.Idle && instruction == text
    val idle = auto && taken && !focused && text.isEmpty() && phase == Phase.Idle
    val last = tones.lastIndex
    val lastLabel = tones.lastOrNull()?.label ?: ""

    val bubbleW = remember { Animatable(0f) }
    val bubbleH = remember { Animatable(0f) }
    val pull = remember { Animatable(0f) }
    val beat = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val chipScroll = rememberScrollState()

    /** Back to the draft alone, with an empty field. */
    fun reset() {
        phase = Phase.Idle
        hasResult = false
        index = 0
        chosen = null
        instruction = null
        drifted = false
        inner = ""
    }

    /** The bubble dissolves, and the result arrives [thinkTime] later. */
    fun begin(request: String, slot: Int?) {
        chosen = slot
        instruction = request
        phase = Phase.Thinking
        requests += 1
    }

    /** Any touch takes the sheet away from the script. */
    fun interrupt() {
        if (!playing) return
        taken = true
        heldChip = null
        heldCancel = false
    }

    fun write(next: String) {
        if (!controlled) inner = next
        onValueChange?.invoke(next)
    }

    fun rewrite(raw: String, slot: Int?) {
        val request = raw.trim()
        if (request.isEmpty() || thinking) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        onSend?.invoke(request)
        begin(request, slot ?: chosen?.takeIf { tones.getOrNull(it)?.label == request })
    }

    fun show(slot: Int) {
        if (!hasResult || thinking || slot == index || slot !in 0..1) return
        index = slot
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    // The bubble takes the size of the version it shows.
    val target = sizes[index]
    LaunchedEffect(target, reducedMotion) {
        if (target == IntSize.Zero) return@LaunchedEffect
        val w = with(density) { target.width.toDp().value }
        val h = with(density) { target.height.toDp().value }
        if (bubbleW.value == 0f) {
            bubbleW.snapTo(w)
            bubbleH.snapTo(h)
        } else {
            val spec = if (reducedMotion) tween(200, easing = EaseOut) else SizeSpring
            launch { bubbleW.animateTo(w, spec) }
            launch { bubbleH.animateTo(h, spec) }
        }
    }

    // While the rewrite is made a band of light crosses the bubble.
    LaunchedEffect(thinking, thinkTime) {
        if (!thinking) return@LaunchedEffect
        beat.snapTo(0f)
        launch { beat.animateTo(1f, infiniteRepeatable(tween(SHIMMER, easing = LinearEasing), RepeatMode.Restart)) }
        delay((thinkTime * 1000).toLong())
        written = 0
        hasResult = true
        index = 1
        phase = Phase.Done
        answers += 1
    }

    // The field's arrows turn twice in that time, ending as the result lands.
    LaunchedEffect(requests) {
        if (requests > 0) spin.animateTo(720f * requests, tween((thinkTime * 1000).toInt(), easing = EaseInOut))
    }

    // The result is written a few letters at a time, once the bubble has opened.
    LaunchedEffect(answers) {
        if (answers == 0) return@LaunchedEffect
        delay(WRITE_DELAY)
        var count = 0
        while (count < rewritten.length) {
            count += WRITE_STEP
            written = count
            delay(WRITE_TICK)
        }
    }

    // The script moves the chip row by hand: a timed scroll to its end, and back.
    LaunchedEffect(drifted) {
        chipScroll.animateScrollTo(
            if (drifted) chipScroll.maxValue else 0,
            tween(if (drifted) DRIFT else DRIFT_BACK, easing = EaseInOut),
        )
    }

    // One loop after another, every step at its own moment after the loop began.
    LaunchedEffect(playing, last, lastLabel) {
        if (!playing) return@LaunchedEffect
        var start = System.nanoTime()
        while (true) {
            reach(start, DRIFT_AT)
            drifted = true
            reach(start, PRESS_AT)
            heldChip = last
            reach(start, CHOOSE_AT)
            heldChip = null
            inner = lastLabel
            begin(lastLabel, last)
            reach(start, SWIPE_AT)
            launch { pull.animateTo(SWIPE_REACH, tween((SWIPE_TIME * 1000).toInt(), easing = EaseOut)) }
            reach(start, SWIPE_AT + SWIPE_TIME)
            index = 0
            launch { pull.animateTo(0f, PullSpring) }
            reach(start, CANCEL_PRESS_AT)
            heldCancel = true
            reach(start, CANCEL_AT)
            heldCancel = false
            reset()
            reach(start, LOOP)
            start += (LOOP * 1e9).toLong()
        }
    }

    // The loop starts over a few seconds after the sheet is left at rest.
    LaunchedEffect(idle) {
        if (!idle) return@LaunchedEffect
        delay((RESUME * 1000).toLong())
        taken = false
    }

    val onTouch by rememberUpdatedState { interrupt() }
    val onTurn by rememberUpdatedState { step: Int -> show(index + step) }
    val shadows = remember {
        object {
            val panel = SoftRoundRect(rgba(52, 72, 130, 0.2f), sigma = 22f)
            val field = SoftRoundRect(rgba(52, 72, 130, 0.08f), sigma = 5f)
        }
    }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    drawIntoCanvas {
                        shadows.panel.draw(it.nativeCanvas, x, y + 16f, w, h, RADIUS)
                        shadows.field.draw(it.nativeCanvas, x + INSET, y + h - INSET - FIELD_H + 2f, w - INSET * 2, FIELD_H, FIELD_R)
                    }
                }
            }
        }

        Column(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(RADIUS.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFE9F2FF), Color(0xFFF3ECFB)))),
        ) {
            Box(Modifier.fillMaxWidth().height(BAR_H.dp).padding(horizontal = 18.dp)) {
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Spark,
                        Blue,
                        filled = true,
                        brush = Brush.linearGradient(listOf(Blue, Color(0xFFA35CFF)), Offset.Zero, Offset(20f, 20f)),
                    )
                    BasicText(title, style = TitleStyle, maxLines = 1)
                }

                val cancelSource = remember { MutableInteractionSource() }
                val cancelDown by cancelSource.collectIsPressedAsState()
                val cancelAlpha by animateFloatAsState(
                    if (cancelDown || heldCancel) 0.35f else 1f,
                    tween(130, easing = EaseOut),
                    label = "cancel",
                )
                BasicText(
                    cancelLabel,
                    style = CancelStyle,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer { alpha = cancelAlpha }
                        .clickable(interactionSource = cancelSource, indication = null, role = Role.Button) {
                            interrupt()
                            onCancel?.invoke()
                            if (text.isNotEmpty()) onValueChange?.invoke("")
                            reset()
                        },
                )

                val applySource = remember { MutableInteractionSource() }
                val applyDown by applySource.collectIsPressedAsState()
                val applyAlpha by animateFloatAsState(if (applyDown) 0.35f else 1f, tween(130, easing = EaseOut), label = "apply")
                // Pale until there is something to apply.
                val applyColor by animateColorAsState(
                    if (hasResult) Action else Color(0xFFAEBBD2),
                    tween(250, easing = EaseOut),
                    label = "applyColor",
                )
                BasicText(
                    applyLabel,
                    style = ApplyStyle.copy(color = applyColor),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer { alpha = applyAlpha }
                        .clickable(interactionSource = applySource, indication = null, enabled = hasResult, role = Role.Button) {
                            interrupt()
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onApply?.invoke(if (index == 1) rewritten else draft)
                        },
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(STAGE_H.dp)
                    // A sideways pull on the bubble turns to the other version.
                    .pointerInput(reducedMotion) {
                        var travel = 0f
                        val release = {
                            if (travel > SWIPE_DISTANCE) onTurn(-1) else if (travel < -SWIPE_DISTANCE) onTurn(1)
                            scope.launch { pull.animateTo(0f, if (reducedMotion) tween(200, easing = EaseOut) else PullSpring) }
                            Unit
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { travel = 0f },
                            onDragEnd = release,
                            onDragCancel = release,
                        ) { change, amount ->
                            change.consume()
                            travel += amount.toDp().value
                            onTouch()
                            scope.launch { pull.snapTo(travel * PULL_SHARE) }
                        }
                    }
                    .semantics { contentDescription = if (index == 1) rewritten else draft },
            ) {
                Box(
                    Modifier.matchParentSize().graphicsLayer { translationX = pull.value.dp.toPx() },
                    contentAlignment = Alignment.Center,
                ) {
                    val blueAlpha by animateFloatAsState(if (thinking) 0f else 1f, tween(220, easing = EaseOut), label = "blue")
                    Box(
                        Modifier.size(bubbleW.value.dp, bubbleH.value.dp).clip(RoundedCornerShape(BUBBLE_R.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.matchParentSize().graphicsLayer { alpha = blueAlpha }.background(Blue))
                        Box(
                            Modifier.matchParentSize().graphicsLayer { alpha = 1f - blueAlpha }.background(Color(0xFFCFDCF7)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .width(BAND.dp)
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        if (!reducedMotion) translationX = ((beat.value - 0.5f) * SWEEP).dp.toPx()
                                    }
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(rgba(255, 255, 255, 0f), rgba(255, 255, 255, 0.85f), rgba(255, 255, 255, 0f)),
                                        ),
                                    ),
                            )
                            Icon(
                                Star,
                                rgba(45, 127, 249, 0.75f),
                                Modifier.graphicsLayer {
                                    if (!reducedMotion) {
                                        val pulse = 1f + 0.18f * sin(beat.value * 2f * PI.toFloat())
                                        rotationZ = 90f * beat.value
                                        scaleX = pulse
                                        scaleY = pulse
                                    }
                                },
                                filled = true,
                            )
                        }
                    }

                    // The letters still to come hold their place, so the lines do not move.
                    val result = buildAnnotatedString {
                        append(rewritten.take(written))
                        withStyle(SpanStyle(color = Color.Transparent)) { append(rewritten.drop(written)) }
                    }
                    listOf(AnnotatedString(draft), result).forEachIndexed { slot, content ->
                        val shown = slot == index && !thinking
                        val alpha by animateFloatAsState(
                            if (shown) 1f else 0f,
                            tween(180, delayMillis = if (shown) 120 else 0, easing = EaseOut),
                            label = "version",
                        )
                        // Each version is laid out at rest size even while hidden: the bubble's size comes from it.
                        BasicText(
                            content,
                            style = BubbleStyle,
                            modifier = Modifier
                                .widthIn(max = BUBBLE_MAX.dp)
                                .onSizeChanged { size -> if (sizes[slot] != size) sizes = sizes.toMutableList().also { it[slot] = size } }
                                .graphicsLayer { this.alpha = alpha }
                                .padding(horizontal = BUBBLE_PAD_X.dp, vertical = BUBBLE_PAD_Y.dp)
                                .clearAndSetSemantics {},
                        )
                    }
                }

                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(2) { slot ->
                        Dot(slot, active = slot == index, shown = hasResult, reducedMotion = reducedMotion) {
                            interrupt()
                            show(slot)
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height((CHIP_H + 2f).dp)
                    .horizontalScroll(chipScroll)
                    .padding(horizontal = INSET.dp),
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tones.forEachIndexed { slot, tone ->
                    Chip(tone, picked = chosen == slot, dimmed = thinking && chosen != slot, held = heldChip == slot) {
                        interrupt()
                        write(tone.label)
                        rewrite(tone.label, slot)
                    }
                }
            }

            Row(
                Modifier
                    .padding(start = INSET.dp, end = INSET.dp, top = 12.dp)
                    .fillMaxWidth()
                    .height(FIELD_H.dp)
                    .background(Color.White, RoundedCornerShape(FIELD_R.dp))
                    .padding(start = 14.dp, end = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { write(it) },
                    singleLine = true,
                    textStyle = FieldStyle,
                    cursorBrush = SolidColor(Action),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            interrupt()
                            rewrite(text, null)
                        },
                    ),
                    decorationBox = { input ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) BasicText(placeholder, style = PlaceholderStyle, maxLines = 1)
                            input()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            if (state.isFocused == focused) return@onFocusChanged
                            focused = state.isFocused
                            // A touch on the field ends the script: the sheet goes back to rest for the keyboard.
                            if (focused && playing) {
                                interrupt()
                                reset()
                            }
                        },
                )

                val sendSource = remember { MutableInteractionSource() }
                val sendDown by sendSource.collectIsPressedAsState()
                val sendDip by animateFloatAsState(if (sendDown) 0.94f else 1f, tween(130, easing = EaseOutStrong), label = "send")
                val disc by animateColorAsState(
                    if (text.isEmpty()) Color(0xFFDBE3F1) else Blue,
                    tween(150, easing = EaseOut),
                    label = "disc",
                )
                // The glyph going out of the round button shrinks away.
                val swap by animateFloatAsState(if (answered) 1f else 0f, tween(200, easing = EaseOutStrong), label = "swap")
                Box(
                    Modifier
                        .size(SEND.dp)
                        .graphicsLayer {
                            scaleX = sendDip
                            scaleY = sendDip
                        }
                        .clickable(
                            interactionSource = sendSource,
                            indication = null,
                            enabled = text.isNotEmpty() && !thinking,
                            role = Role.Button,
                        ) {
                            interrupt()
                            rewrite(text, null)
                        }
                        .semantics { contentDescription = if (answered) "Rewrite again" else "Rewrite" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.matchParentSize().graphicsLayer { alpha = 1f - swap }.background(disc, CircleShape))
                    Icon(
                        Arrow,
                        Color.White,
                        Modifier.graphicsLayer {
                            alpha = 1f - swap
                            if (!reducedMotion) {
                                scaleX = 1f - 0.5f * swap
                                scaleY = 1f - 0.5f * swap
                            }
                        },
                        strokeWidth = 1.9f,
                    )
                    Icon(
                        Again,
                        Action,
                        Modifier.graphicsLayer {
                            alpha = swap
                            if (!reducedMotion) {
                                rotationZ = spin.value
                                scaleX = 0.5f + 0.5f * swap
                                scaleY = 0.5f + 0.5f * swap
                            }
                        },
                        strokeWidth = 1.7f,
                    )
                }
            }
        }
    }
}
