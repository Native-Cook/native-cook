package com.ngocdevv.nativecook.blocks.inputs.foldingmessagebar

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PANEL_W = 370f
private const val PANEL_H = 330f
private const val PANEL_R = 34f

// Inset of the bar and of the thread from the panel's edge.
private const val MARGIN = 12f
private const val BAR_H = 58f
private const val BAR_R = 16f
private const val BAR_PAD = 10f
private const val FIELD_H = 38f
private const val FIELD_R = 10f
private const val GAP = 10f
private const val TEXT_LEFT = 39f
private const val TOOL_GAP = 14f
private const val AVATAR = 22f
private const val BUBBLE_R = 12f

// A bubble stops this far from the opposite edge of the thread.
private const val BUBBLE_INSET = 56f

// Space between the bar and the message above it, at rest and for a sent bubble.
private const val THREAD_GAP = 16f
private const val SENT_GAP = 14f

// How far the thread moves up for a sent bubble: its label, the bubble and the gap.
private const val LIFT = 101f

// The sent bubble starts this far down, tucked behind the bar.
private const val SENT_DROP = 44f
private const val FOLD_SHIFT = 14f
private const val FOLD = 260
private const val FOLD_STAGGER = 80
private const val UNFOLD_STAGGER = 90
private const val MIC_DELAY = 300
private const val PLACEHOLDER_DELAY = 180

// How long a sent bubble takes to fade, and how long the thread waits before it drops back.
private const val FADE = 220
private const val DROP_DELAY = 160
private const val BLINK = 530L

private val Caret = Color(0xFF5FDBE8)
private val Tool = Color(0xFFD5E3E3)
private val Teal = Color(0xFF17B6C5)

/** The same spring as the other stacks, from its mass, stiffness and damping. */
private fun physicalSpring(mass: Float, stiffness: Float, damping: Float) =
    spring<Float>(dampingRatio = damping / (2f * sqrt(stiffness * mass)), stiffness = stiffness / mass)

private val UnfoldSpring = physicalSpring(1f, 320f, 22f)
private val GrowSpring = physicalSpring(1f, 260f, 20f)
private val LiftSpring = physicalSpring(1f, 200f, 22f)

// autoPlay loop, in seconds from its start.
private const val FOCUS_AT = 0.8f
private const val TYPE_AT = 1.3f

// The longest the typing may take; a longer script is typed faster.
private const val TYPE_SPAN = 2.6f
private const val PRESS_AT = 4.2f
private const val SEND_AT = 4.4f
private const val CLEAR_AT = 6.7f
private const val LOOP = 8f

// After the field is left empty, the loop waits this long before it starts again.
private const val RESUME = 3f

// Milliseconds before each key, used in turn: uneven like a hand, the same in every loop.
private val Rhythm = intArrayOf(90, 60, 110, 70, 50, 130, 80, 60, 100, 70, 150, 60, 90)

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

// Outlines drawn in a 20 dp box (the tick in a 10 dp one), the same strings in the three stacks.
private fun outline(d: String) = PathParser().parsePathString(d).toPath()

private val Smiley = outline(
    "M 17.6 10 C 17.6 14.2 14.2 17.6 10 17.6 C 5.8 17.6 2.4 14.2 2.4 10 C 2.4 5.8 5.8 2.4 10 2.4 C 14.2 2.4 17.6 5.8 17.6 10 Z M 7.5 8.4 L 7.51 8.4 M 12.5 8.4 L 12.51 8.4 M 13.53 11.95 C 12.89 13.32 11.52 14.2 10 14.2 C 8.48 14.2 7.11 13.32 6.47 11.95",
)
private val Clip = outline(
    "M 14.6 9.93 L 9.86 14.67 C 8.61 15.92 6.58 15.92 5.33 14.67 C 4.08 13.42 4.08 11.39 5.33 10.14 L 10.85 4.63 C 11.71 3.77 13.1 3.77 13.96 4.63 C 14.82 5.49 14.82 6.88 13.96 7.74 L 8.8 12.9 C 8.33 13.37 7.57 13.37 7.1 12.9 C 6.63 12.43 6.63 11.67 7.1 11.2 L 10.99 7.31",
)
private val Picture = outline(
    "M 5.4 3.6 L 14.6 3.6 C 16.04 3.6 17.2 4.76 17.2 6.2 L 17.2 13.8 C 17.2 15.24 16.04 16.4 14.6 16.4 L 5.4 16.4 C 3.96 16.4 2.8 15.24 2.8 13.8 L 2.8 6.2 C 2.8 4.76 3.96 3.6 5.4 3.6 Z M 8.7 8 C 8.7 8.77 8.07 9.4 7.3 9.4 C 6.53 9.4 5.9 8.77 5.9 8 C 5.9 7.23 6.53 6.6 7.3 6.6 C 8.07 6.6 8.7 7.23 8.7 8 Z M 3.4 14.6 L 7.4 11 L 10.4 13.8 M 9.6 13 L 12.6 10.4 L 16.8 14",
)
private val Camera = outline(
    "M 4.8 5.6 L 15.2 5.6 C 16.53 5.6 17.6 6.67 17.6 8 L 17.6 14 C 17.6 15.33 16.53 16.4 15.2 16.4 L 4.8 16.4 C 3.47 16.4 2.4 15.33 2.4 14 L 2.4 8 C 2.4 6.67 3.47 5.6 4.8 5.6 Z M 6.9 5.6 L 8 3.8 L 12 3.8 L 13.1 5.6 M 12.9 10.9 C 12.9 12.5 11.6 13.8 10 13.8 C 8.4 13.8 7.1 12.5 7.1 10.9 C 7.1 9.3 8.4 8 10 8 C 11.6 8 12.9 9.3 12.9 10.9 Z",
)
private val Mic = outline(
    "M 10 2.4 C 11.44 2.4 12.6 3.56 12.6 5 L 12.6 9.2 C 12.6 10.64 11.44 11.8 10 11.8 C 8.56 11.8 7.4 10.64 7.4 9.2 L 7.4 5 C 7.4 3.56 8.56 2.4 10 2.4 Z M 15.2 9.2 C 15.2 12.07 12.87 14.4 10 14.4 C 7.13 14.4 4.8 12.07 4.8 9.2 M 10 14.4 L 10 17.4",
)
private val Plane = outline("M 4.2 3.9 L 16.8 10 L 4.2 16.1 L 6.4 10 Z M 6.4 10 L 10.8 10")
private val Tick = outline("M 2.6 5.2 L 4.4 7 L 7.5 3.2")

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun barText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val NameStyle = barText(12.5f, 16f, Color.White, FontWeight.Medium)
private val MessageStyle = barText(13.5f, 19f, Color.White)
private val TimeStyle = barText(10.5f, 14f, Color(0xFF7F9BA2))
private val SentStyle = barText(13.5f, 19f, Color(0xFF0C8F9D), FontWeight.Medium)
private val SentTimeStyle = barText(10.5f, 14f, Color(0xFF7CC7D0))
private val FieldStyle = barText(14f, 18f, Color.White)
private val PlaceholderStyle = barText(14f, 18f, Color(0xFFC9DADD))

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

/** The last message sent; [count] tells two sends of the same text apart. */
private data class Outbox(val text: String, val count: Int)

private class ToolSpec(val outline: Path, val label: String, val onClick: (() -> Unit)?)

@Composable
private fun Icon(outline: Path, color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, strokeWidth: Float = 1.5f) {
    Canvas(modifier.size(size)) {
        scale(density, density, pivot = Offset.Zero) {
            drawPath(outline, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A plain head-and-shoulders figure in a disc. */
@Composable
private fun Avatar(fill: Color, figure: Color) {
    val disc = remember { Path().apply { addOval(Rect(0f, 0f, AVATAR, AVATAR)) } }
    Canvas(Modifier.size(AVATAR.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            clipPath(disc) {
                drawCircle(fill, radius = 11f, center = Offset(11f, 11f))
                drawCircle(figure, radius = 3.8f, center = Offset(11f, 8.4f))
                drawOval(figure, topLeft = Offset(3.5f, 14.1f), size = Size(15f, 13f))
            }
        }
    }
}

/** One attachment tool: it slides right and fades while a message is being written. */
@Composable
private fun ToolButton(spec: ToolSpec, index: Int, count: Int, active: Boolean, reducedMotion: Boolean) {
    val folded = remember { Animatable(if (active) 1f else 0f) }

    LaunchedEffect(active, reducedMotion) {
        // The camera leaves first and the paperclip comes back first.
        if (active) {
            folded.animateTo(1f, tween(FOLD, delayMillis = (count - 1 - index) * FOLD_STAGGER, easing = EaseOutStrong))
        } else if (reducedMotion) {
            folded.animateTo(0f, tween(FOLD, delayMillis = index * UNFOLD_STAGGER, easing = EaseOutStrong))
        } else {
            delay(index * UNFOLD_STAGGER.toLong())
            folded.animateTo(0f, UnfoldSpring)
        }
    }

    Icon(
        spec.outline,
        Tool,
        Modifier
            .graphicsLayer {
                val t = folded.value
                alpha = (1f - t).coerceIn(0f, 1f)
                if (!reducedMotion) {
                    translationX = FOLD_SHIFT.dp.toPx() * t
                    scaleX = 1f - 0.2f * t
                    scaleY = 1f - 0.2f * t
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !active,
                role = Role.Button,
                onClickLabel = spec.label,
                onClick = { spec.onClick?.invoke() },
            )
            .semantics { contentDescription = spec.label },
    )
}

/** The bar's own caret, shown while the script types. [beat] restarts the blink at every key. */
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
    Box(Modifier.size(2.dp, 18.dp).graphicsLayer { alpha = if (on) 1f else 0f }.background(Caret, RoundedCornerShape(1.dp)))
}

/**
 * Chat composer on a teal panel. While a message is written its three attachment tools fold away
 * to the right and the microphone turns into a send plane; sending lifts the text out of the field
 * as a white bubble that grows from the composer's top-right corner, and the tools unfold again.
 *
 * @param value The field's text when the parent owns it. Leave it null and the bar keeps its own.
 * @param autoPlay Writes and sends [script] by itself in a loop, without the keyboard, until the field is tapped.
 * @param script What autoPlay types.
 * @param senderName Sender of the message already in the thread, shown with [message] and [time].
 * @param ownName Label over a sent message.
 * @param onSend Called with the trimmed text when the plane is tapped or the keyboard's send key is pressed.
 * @param onMic Called when the button is tapped while the field is empty.
 */
@Composable
fun FoldingMessageBar(
    modifier: Modifier = Modifier,
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Message...",
    script: String = "Here is my order ID 1209475822",
    senderName: String = "Customer Service",
    message: String = "Can you send me your order ID so I can check right away?",
    time: String = "09:52 PM",
    ownName: String = "You",
    onSend: ((String) -> Unit)? = null,
    onMic: (() -> Unit)? = null,
    onEmoji: (() -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
    onPhoto: (() -> Unit)? = null,
    onCamera: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val controlled = value != null
    val auto = autoPlay && !controlled && !reducedMotion

    var inner by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    // A touch on the field hands the bar to the user until it has been left empty for a while.
    var taken by remember { mutableStateOf(false) }
    // The script's stand-in for focus, and its finger on the button.
    var engaged by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var outbox by remember { mutableStateOf<Outbox?>(null) }
    var shown by remember { mutableStateOf(false) }

    val text = value ?: inner
    // The script is running: the field is not focused and the bar draws its own caret.
    val playing = auto && !taken
    // Folded: someone is writing, or has written.
    val active = if (playing) engaged else focused || text.isNotEmpty()
    val idle = auto && taken && !focused && text.isEmpty()

    // The placeholder waits for the sent text to clear the field before it returns.
    val hint by animateFloatAsState(
        if (active) 0f else 1f,
        tween(150, delayMillis = if (active) 0 else PLACEHOLDER_DELAY, easing = EaseOut),
        label = "hint",
    )
    // The microphone comes back last, after the tools have unfolded.
    val morph by animateFloatAsState(
        if (active) 1f else 0f,
        tween(200, delayMillis = if (active) 0 else MIC_DELAY, easing = EaseOutStrong),
        label = "morph",
    )
    val ghost = remember { Animatable(1f) }
    val grow = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val label = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }

    // The text leaves the field and comes back as a bubble above the bar.
    LaunchedEffect(outbox) {
        if (outbox == null) return@LaunchedEffect
        launch {
            ghost.snapTo(0f)
            ghost.animateTo(1f, tween(200, easing = EaseOut))
        }
        launch {
            if (reducedMotion) {
                grow.snapTo(1f)
            } else {
                grow.snapTo(0f)
                grow.animateTo(1f, GrowSpring)
            }
        }
        launch {
            fade.snapTo(0f)
            fade.animateTo(1f, tween(200, easing = EaseOut))
        }
        // The label follows its bubble in.
        launch {
            label.snapTo(0f)
            label.animateTo(1f, tween(220, delayMillis = 220, easing = EaseOut))
        }
        launch { lift.animateTo(1f, if (reducedMotion) tween(250, easing = EaseOut) else LiftSpring) }
    }

    LaunchedEffect(shown) {
        if (shown) return@LaunchedEffect
        launch { fade.animateTo(0f, tween(FADE, easing = EaseOut)) }
        launch { label.animateTo(0f, tween(FADE, easing = EaseOut)) }
        launch {
            if (reducedMotion) {
                lift.animateTo(0f, tween(250, easing = EaseOut))
            } else {
                // On the way down the thread waits for the sent bubble to fade.
                delay(DROP_DELAY.toLong())
                lift.animateTo(0f, LiftSpring)
            }
        }
    }

    // One loop after another, every step at its own moment after the loop began.
    LaunchedEffect(playing, script) {
        if (!playing) return@LaunchedEffect
        val keys = keyTimes(script.length)
        var start = System.nanoTime()
        while (true) {
            reach(start, FOCUS_AT)
            engaged = true
            keys.forEachIndexed { index, key ->
                reach(start, key)
                inner = script.take(index + 1)
            }
            reach(start, PRESS_AT)
            pressed = true
            reach(start, SEND_AT)
            pressed = false
            engaged = false
            inner = ""
            outbox = Outbox(script, (outbox?.count ?: 0) + 1)
            shown = true
            reach(start, CLEAR_AT)
            shown = false
            reach(start, LOOP)
            start += (LOOP * 1e9).toLong()
        }
    }

    // The loop starts over from rest a few seconds after the field is left empty.
    LaunchedEffect(idle) {
        if (!idle) return@LaunchedEffect
        delay((RESUME * 1000).toLong())
        shown = false
        delay(700)
        taken = false
    }

    fun write(next: String) {
        if (!controlled) inner = next
        onValueChange?.invoke(next)
    }

    fun submit() {
        val sentText = text.trim()
        if (sentText.isEmpty()) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        onSend?.invoke(sentText)
        write("")
        outbox = Outbox(sentText, (outbox?.count ?: 0) + 1)
        shown = true
    }

    val tools = listOf(
        ToolSpec(Clip, "Attach a file", onAttach),
        ToolSpec(Picture, "Photo library", onPhoto),
        ToolSpec(Camera, "Camera", onCamera),
    )
    // A trailing space must keep its width in the row that mirrors the typed text.
    val mirror = if (text.endsWith(' ')) text.dropLast(1) + " " else text
    val panel = RoundedCornerShape(PANEL_R.dp)
    val bubble = RoundedCornerShape(BUBBLE_R.dp)

    Box(
        modifier
            .size(PANEL_W.dp, PANEL_H.dp)
            .clip(panel)
            .background(Brush.verticalGradient(0f to Color(0xFF010B0E), 0.5f to Color(0xFF00222A), 1f to Color(0xFF003E4C))),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .graphicsLayer { translationY = -LIFT.dp.toPx() * lift.value }
                .padding(start = MARGIN.dp, bottom = (MARGIN + BAR_H + THREAD_GAP).dp)
                .widthIn(max = (PANEL_W - MARGIN * 2 - BUBBLE_INSET).dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.height(AVATAR.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(fill = Color(0xFFE8F1F2), figure = Color(0xFF1C4A52))
                BasicText(senderName, style = NameStyle, maxLines = 1)
            }
            Column(
                Modifier
                    .background(rgba(255, 255, 255, 0.11f), bubble)
                    .border(1.dp, rgba(255, 255, 255, 0.06f), bubble)
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                BasicText(message, style = MessageStyle)
                Row(
                    Modifier.height(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Tick,
                        Color(0xFF1B3D44),
                        Modifier.background(Color(0xFF7F9BA2), RoundedCornerShape(2.5.dp)),
                        size = 10.dp,
                        strokeWidth = 1.4f,
                    )
                    BasicText(time, style = TimeStyle, maxLines = 1)
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = MARGIN.dp, bottom = (MARGIN + BAR_H + SENT_GAP).dp)
                .widthIn(max = (PANEL_W - MARGIN * 2 - BUBBLE_INSET).dp)
                .then(if (shown) Modifier else Modifier.clearAndSetSemantics {}),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                Modifier.height(AVATAR.dp).graphicsLayer { alpha = label.value },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(ownName, style = NameStyle, maxLines = 1)
                Avatar(fill = Teal, figure = Color.White)
            }
            Column(
                Modifier
                    // It starts small at the composer's top-right corner, behind the bar.
                    .graphicsLayer {
                        val g = grow.value
                        alpha = fade.value
                        transformOrigin = TransformOrigin(1f, 1f)
                        translationY = SENT_DROP.dp.toPx() * (1f - g)
                        scaleX = 0.3f + 0.7f * g
                        scaleY = 0.3f + 0.7f * g
                    }
                    .background(Color.White, bubble)
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicText(outbox?.text ?: "", style = SentStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(time, style = SentTimeStyle, maxLines = 1)
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = MARGIN.dp)
                .size((PANEL_W - MARGIN * 2).dp, BAR_H.dp)
                .background(Color(0xFF1C454D), RoundedCornerShape(BAR_R.dp))
                .border(1.dp, rgba(255, 255, 255, 0.07f), RoundedCornerShape(BAR_R.dp))
                .padding(BAR_PAD.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP.dp),
        ) {
            Box(Modifier.weight(1f).height(FIELD_H.dp).background(Color(0xFF375C64), RoundedCornerShape(FIELD_R.dp))) {
                Box(
                    Modifier.matchParentSize().padding(start = TEXT_LEFT.dp, end = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(
                        placeholder,
                        style = PlaceholderStyle,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { alpha = hint }.clearAndSetSemantics {},
                    )
                    BasicText(
                        outbox?.text ?: "",
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
                        cursorBrush = SolidColor(Caret),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                if (state.isFocused == focused) return@onFocusChanged
                                focused = state.isFocused
                                // A touch on the field ends the script: the field is emptied and the keyboard takes over.
                                if (focused && playing) {
                                    taken = true
                                    engaged = false
                                    pressed = false
                                    inner = ""
                                    shown = false
                                }
                            }
                            .semantics { contentDescription = placeholder },
                    )
                    if (playing && engaged) {
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

                Icon(
                    Smiley,
                    Tool,
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = { onEmoji?.invoke() },
                        )
                        .semantics { contentDescription = "Emoji" },
                )

                Row(
                    Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(TOOL_GAP.dp),
                ) {
                    tools.forEachIndexed { index, spec ->
                        ToolButton(spec, index, tools.size, active, reducedMotion)
                    }
                }
            }

            val interaction = remember { MutableInteractionSource() }
            val down by interaction.collectIsPressedAsState()
            // The button dips under a finger, or under the script's.
            val dip by animateFloatAsState(if (down || pressed) 0.9f else 1f, tween(130, easing = EaseOutStrong), label = "dip")
            Box(
                Modifier
                    .size(FIELD_H.dp)
                    .graphicsLayer {
                        scaleX = dip
                        scaleY = dip
                    }
                    .background(Color.White, RoundedCornerShape(FIELD_R.dp))
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            if (playing) return@clickable
                            if (text.isEmpty()) onMic?.invoke() else submit()
                        },
                    )
                    .semantics { contentDescription = if (active) "Send" else "Record a voice message" },
                contentAlignment = Alignment.Center,
            ) {
                // The glyph going out shrinks and turns away.
                Icon(
                    Mic,
                    Teal,
                    Modifier.graphicsLayer {
                        alpha = 1f - morph
                        if (!reducedMotion) {
                            rotationZ = 30f * morph
                            scaleX = 1f - 0.5f * morph
                            scaleY = 1f - 0.5f * morph
                        }
                    },
                    strokeWidth = 1.7f,
                )
                Icon(
                    Plane,
                    Teal,
                    Modifier.graphicsLayer {
                        alpha = morph
                        if (!reducedMotion) {
                            rotationZ = -30f * (1f - morph)
                            scaleX = 0.5f + 0.5f * morph
                            scaleY = 0.5f + 0.5f * morph
                        }
                    },
                    strokeWidth = 1.7f,
                )
            }
        }

        Box(Modifier.matchParentSize().border(1.dp, rgba(255, 255, 255, 0.06f), panel))
    }
}
