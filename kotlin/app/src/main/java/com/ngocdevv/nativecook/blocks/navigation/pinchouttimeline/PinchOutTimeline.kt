package com.ngocdevv.nativecook.blocks.navigation.pinchouttimeline

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @param mine Written by the person holding the phone: a white bubble at the right.
 * @param photo A square photo sent instead of text.
 */
data class Message(val mine: Boolean = false, val text: String? = null, val photo: String? = null)

/** A piece of a card's title; a piece with [key] is underlined. */
data class TitlePiece(val text: String, val key: Boolean = false)

/**
 * @param label On the day's card: "Tuesday, Jun 25".
 * @param weekday With [date], the two lines shown alone on the page while the day opens.
 * @param stamp Above the conversation: "Tue, Jun 25 at 1:03 PM".
 * @param earlier The last lines of the message before, out of focus under the top bar.
 * @param photos Up to two photos that peek over the bottom edge of the card.
 */
data class Day(
    val label: String,
    val weekday: String,
    val date: String,
    val stamp: String,
    val earlier: List<String> = emptyList(),
    val title: List<TitlePiece>,
    val photos: List<String> = emptyList(),
    val messages: List<Message>,
)

private const val BALLOONS = "https://images.unsplash.com/photo-1641128324972-af3212f0f6bd?auto=format&fit=crop&w=360&h=360&q=80"
private const val LAGOON = "https://images.unsplash.com/photo-1573843981267-be1999ff37cd?auto=format&fit=crop&w=360&h=360&q=80"

val SampleDays = listOf(
    Day(
        label = "Saturday, Jun 22",
        weekday = "Saturday",
        date = "June 22",
        stamp = "Sat, Jun 22 at 9:12 AM",
        title = listOf(TitlePiece("Introduction to "), TitlePiece("Mira’s values", key = true)),
        messages = listOf(
            Message(mine = true, text = "Hi"),
            Message(text = "Hello Mira, I’m Sol. I’d love to know what matters most to you these days."),
            Message(mine = true, text = "Slow mornings"),
            Message(text = "That sounds lovely. Unhurried time has a way of making room for everything else. What does a slow morning look like when it goes just right?"),
            Message(mine = true, text = "Tea, a walk, no phone"),
            Message(text = "A warm cup, some fresh air and nothing asking for your attention. I’ll remember that. Is there anything that tends to get in the way of mornings like these?"),
        ),
    ),
    Day(
        label = "Tuesday, Jun 25",
        weekday = "Tuesday",
        date = "June 25",
        stamp = "Tue, Jun 25 at 1:03 PM",
        earlier = listOf("and I hope that feeling of ease stays close,", "a small calm to carry through your week."),
        title = listOf(TitlePiece("Shared a "), TitlePiece("balloon sunrise", key = true), TitlePiece(", playing "), TitlePiece("Harbor Racers", key = true)),
        photos = listOf(LAGOON, BALLOONS),
        messages = listOf(
            Message(mine = true, text = "Yo"),
            Message(text = "Hey Mira, good to see you. How is your day going so far?"),
            Message(mine = true, text = "Doing well"),
            Message(text = "I’m so glad to hear it! A day that starts gently often stays that way, and I know how much a calm rhythm matters to you. What has been helping you feel settled today?"),
            Message(mine = true, photo = BALLOONS),
            Message(text = "Oh, that’s a lovely one! All those balloons drifting over the valley in the first light, in no hurry at all. I can see why you kept it. Are you still up for a round of Harbor Racers tonight, or is today for slower things?"),
        ),
    ),
    Day(
        label = "Wednesday, Jun 26",
        weekday = "Wednesday",
        date = "June 26",
        stamp = "Wed, Jun 26 at 8:40 PM",
        earlier = listOf("Sleep well, Mira. The balloons will still be", "there in the morning, and so will I."),
        title = listOf(TitlePiece("Options for discussing "), TitlePiece("Harbor Racers", key = true)),
        messages = listOf(
            Message(mine = true, text = "Quick question"),
            Message(text = "Of course, Mira. What’s on your mind this evening?"),
            Message(mine = true, text = "How do I tell Jo I need a break from Harbor Racers?"),
            Message(text = "It’s kind of you to think it through first. You could say it plainly and warmly: you love playing together, and you need a few quiet evenings. Or offer a smaller ritual in its place, like one short race on Sundays."),
            Message(mine = true, text = "The Sunday idea"),
            Message(text = "I like it too. It keeps what you share, at a gentler pace. Shall we try the words out together first?"),
        ),
    ),
)

private val Page = listOf(Color(0xFFE5E5E5), Color(0xFFEBD2CE), Color(0xFFD9ADA9))
private val Ink = Color(0xFF1E1C1F)
private val Mute = Color(0xFF8E8A8C)
private val Placeholder = Color(0xFFA39A9B)
private val Glass = Color.White.copy(alpha = 0.45f)
private val CardFill = Color.White.copy(alpha = 0.38f)
private val CardEdge = Color.White.copy(alpha = 0.5f)
private val PhotoTint = Color(0xFFD8CFC9)
private val FingerFill = rgba(30, 28, 31, 0.16f)
private val FingerEdge = Color.White.copy(alpha = 0.7f)

// The conversation.
private const val SIDE = 22f
// From the safe area's top edge to the time stamp, and between two messages.
private const val CHAT_TOP = 70f
private const val MESSAGE_GAP = 22f
private const val TEXT_TRAIL = 56f
private const val BUBBLE_RADIUS = 18f
private const val PHOTO = 90f
private const val INPUT = 40f
private const val INPUT_SIDE = 11f
private const val INPUT_LIFT = 2f
private const val DOWN = 34f
private const val DOWN_LIFT = 22f

// The days.
private const val MARGIN = 16f
private const val CARDS_TOP = 25f
private const val GAP = 8f
private const val RADIUS = 20f
private const val CARD_MIN = 150f
private const val CARD_MAX = 240f
private const val CARD_PAD = 22f
private const val THUMB = 84f
// Where each of the two photos on a card stands, and its tilt in degrees.
private val Thumbs = listOf(72f to -8f, 165f to 6f)
// The room the two lines under the cards take, above the safe area's bottom edge.
private const val FOOTER = 52f

// Going out: the conversation shrinks to this scale on its day's card while the fingers close; the
// spring finishes the move when they lift. The other cards arrive from SLIDE dp away.
private const val SHEET_SCALE = 0.44f
private const val SHEET_BLUR = 14f
// Mass 1, stiffness 180, damping 19.
private const val STIFFNESS = 180f
private const val DAMPING_RATIO = 0.7081f
// How far the fingers must have taken it for the release to go on to the days.
private const val COMMIT = 0.3f
private const val SLIDE = 80f
private const val SLIDE_SCALE = 0.9f
// What is on the cards shows after they have landed, top card first.
private const val REVEAL_AFTER = 0.32f
private const val REVEAL_STAGGER = 0.06f
private const val REVEAL_IN = 0.3f
private const val REVEAL_BLUR = 6f
private const val REVEAL_END = 0.75f
private const val REVEAL_OUT = 0.15f
private const val THUMB_RISE = 34f

// Going in, in seconds from the tap: the cards blow up and blur away, the date shows alone, the
// conversation arrives message by message.
private const val BLOW = 0.32f
private const val GROW = 2.3f
private const val FLY = 0.55f
private const val BLOW_BLUR = 16f
private const val DATE_AT = 0.22f
private const val DATE_FADE = 0.2f
private const val DATE_HOLD = 0.35f
private const val CHAT_AT = 0.85f
private const val CHAT_STAGGER = 0.03f
private const val CHAT_ITEM_IN = 0.25f
private const val CHAT_CLEAR = 0.35f
private const val CHAT_FROM = 0.97f
private const val CHAT_BLUR = 10f
// The stagger stops growing after this many messages, so a long day still arrives in time.
private const val CHAT_ITEMS = 8
private const val ENTER_END = 1.4f
// A spread of the fingers takes a card this far into the blow-up, and past half of it the day opens.
private const val SCRUB = 0.8f
// With reduced motion the two screens cross-fade.
private const val FADE = 0.25f

// The loop: fingers close on the conversation, lift, and later one taps the day's card.
const val LOOP = 9f
private const val PINCH_AT = 1.2f
private const val PINCH_FOR = 0.5f
private const val PINCH_TO = 0.62f
private val PinchEase = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
private const val TAP_AT = 5f
private const val FINGER_IN = 0.25f
private const val FINGER_OUT = 0.2f
private const val FINGER_SIZE = 44f
// Half the distance between the two fingers before they close, across and down.
private const val FINGER_REACH_X = 58f
private const val FINGER_REACH_Y = 78f
private const val USER_HOLD = 3f
private const val HOME = 0.7f

// What two fingers on the screen are doing.
private const val GRIP_NONE = 0
private const val GRIP_CHAT = 1
private const val GRIP_CARD = 2
private const val GRIP_DONE = 3

// The icons, drawn in boxes of 16 dp.
private fun strokes(vararg lines: List<Pair<Float, Float>>) = Path().apply {
    lines.forEach { line ->
        line.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
    }
}

private val Collapse = strokes(
    listOf(1f to 1f, 6.5f to 6.5f),
    listOf(6.5f to 2.5f, 6.5f to 6.5f, 2.5f to 6.5f),
    listOf(15f to 15f, 9.5f to 9.5f),
    listOf(9.5f to 13.5f, 9.5f to 9.5f, 13.5f to 9.5f),
)
private val ArrowDown = strokes(listOf(8f to 2.5f, 8f to 13.5f), listOf(3f to 8.5f, 8f to 13.5f, 13f to 8.5f))
private val Plus = strokes(listOf(8f to 2f, 8f to 14f), listOf(2f to 8f, 14f to 8f))
private val Wave = strokes(
    listOf(2f to 6.5f, 2f to 9.5f),
    listOf(5f to 4f, 5f to 12f),
    listOf(8f to 1.5f, 8f to 14.5f),
    listOf(11f to 4.5f, 11f to 11.5f),
    listOf(14f to 6.5f, 14f to 9.5f),
)

// The bubble's tail, in a box of 14 × 13 that hangs 4 dp over its bottom right corner.
private val Tail = Path().apply {
    moveTo(4f, 0f)
    cubicTo(4f, 6f, 7f, 10f, 14f, 12f)
    cubicTo(8f, 13f, 3f, 11f, 0f, 8f)
    close()
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun face(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal, serif: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFamily = if (serif) FontFamily.Serif else FontFamily.Default,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val StampStyle = face(12f, 16f, Mute).copy(textAlign = TextAlign.Center)
private val PlaceholderStyle = face(15f, 20f, Placeholder)
private val CardLabelStyle = face(11f, 14f, Mute)
private val FootnoteStyle = face(10f, 14f, Mute)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

private fun smooth(x: Float, from: Float, to: Float): Float {
    val t = clamp01((x - from) / (to - from))
    return t * t * (3f - 2f * t)
}

private fun easeIn(t: Float): Float {
    val c = clamp01(t)
    return c * c * c
}

private fun easeOut(t: Float): Float {
    val c = 1f - clamp01(t)
    return 1f - c * c * c
}

/** How visible the conversation's piece number [index] is, [enter] seconds after a card was tapped. */
private fun arrived(enter: Float, index: Int) = smooth(enter - CHAT_AT - min(index, CHAT_ITEMS) * CHAT_STAGGER, 0f, CHAT_ITEM_IN)

/** A blur of [sigma] pixels, from Android 12 on; before that the screens only fade and scale. */
private fun blurOf(sigma: Float): RenderEffect? =
    if (Build.VERSION.SDK_INT >= 31 && sigma > 0.3f) sigmaToRadius(sigma).let { BlurEffect(it, it, TileMode.Decal) } else null

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Where things are on a screen of a given size, in dp. */
private class Frame(val width: Float, val height: Float, val top: Float, val bottom: Float) {
    val cardsTop = top + CARDS_TOP
    val cardHeight = ((height - cardsTop - bottom - FOOTER - GAP * 2) / 3f).coerceIn(CARD_MIN, CARD_MAX)
    val cardWidth = width - MARGIN * 2
    val pitch = cardHeight + GAP
    val inputTop = height - bottom - INPUT_LIFT - INPUT
    fun cardTop(index: Int) = cardsTop + index * pitch
}

/** A card's box in the list, how round and visible it is, and how far it has moved and grown about its middle. */
private class Pose(val left: Float, val top: Float, val width: Float, val height: Float, val radius: Float, val alpha: Float, val shift: Float, val scale: Float)

private fun pose(index: Int, frame: Frame, out: Float, enter: Float, picked: Int, scroll: Float, still: Boolean): Pose {
    val top = frame.cardTop(index)
    if (still) return Pose(MARGIN, top, frame.cardWidth, frame.cardHeight, RADIUS, clamp01(out), 0f, 1f)
    val k = easeIn(enter / BLOW)
    val leaving = 1f - smooth(enter, BLOW * 0.3f, BLOW)
    if (picked == index) {
        // The sheet: the whole screen at 0, this card at 1; opening, it grows over the screen again.
        val middle = top - scroll + frame.cardHeight / 2f
        return Pose(
            left = MARGIN * out,
            top = scroll + (top - scroll) * out,
            width = frame.width + (frame.cardWidth - frame.width) * out,
            height = frame.height + (frame.cardHeight - frame.height) * out,
            radius = RADIUS * smooth(out, 0f, 0.5f),
            alpha = smooth(out, 0.1f, 0.7f) * leaving,
            shift = (frame.height / 2f - middle) * k,
            scale = 1f + (GROW - 1f) * k,
        )
    }
    // The others come in from above and below, and leave the same way.
    val side = if (index < picked) -1f else 1f
    return Pose(
        left = MARGIN,
        top = top,
        width = frame.cardWidth,
        height = frame.cardHeight,
        radius = RADIUS,
        alpha = smooth(out, 0.25f, 0.85f) * leaving,
        shift = side * (SLIDE * (1f - out) + frame.height * FLY * k),
        scale = (SLIDE_SCALE + (1f - SLIDE_SCALE) * out) * (1f + (GROW - 1f) * 0.3f * k),
    )
}

/**
 * A journal-like conversation that pinches out into a list of days. Closing two fingers shrinks the
 * conversation onto its day's card while the other days slide in around it; tapping a card (or
 * spreading two fingers on it) blows the cards up, shows the date alone for a moment, and brings
 * that day's conversation in message by message.
 *
 * @param initialDay The day whose conversation is open at the start.
 * @param autoPlay Pinches out to the days and taps back into the conversation by itself every [LOOP] seconds.
 * @param pageColors The page, top to bottom: the first colour holds to 60% of the height.
 * @param onSelect Called when a day's conversation opens.
 */
@Composable
fun PinchOutTimeline(
    modifier: Modifier = Modifier,
    days: List<Day> = SampleDays,
    initialDay: Int = 1,
    autoPlay: Boolean = false,
    placeholder: String = "Share with Sol…",
    footnote: String = "Chronicles are only available to subscribers",
    unlockLabel: String = "Unlock your chronicles",
    pageColors: List<Color> = Page,
    ink: Color = Ink,
    onSelect: ((index: Int, day: Day) -> Unit)? = null,
    onUnlock: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The loop and the gestures outlive a recomposition: they read what the caller passes through these.
    val list by rememberUpdatedState(days)
    val home by rememberUpdatedState(initialDay.coerceIn(0, days.lastIndex))
    val selected by rememberUpdatedState(onSelect)

    // The day whose conversation is drawn, and the card it shrinks onto and grows out of.
    var day by remember { mutableIntStateOf(initialDay.coerceIn(0, days.lastIndex)) }
    var picked by remember { mutableIntStateOf(initialDay.coerceIn(0, days.lastIndex)) }
    // Which of the two screens takes the touches.
    var timeline by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // The loop's fingers are on the screen, half way out.
    var held by remember { mutableStateOf(false) }
    // 0 with the conversation open, 1 with the days; follows the fingers in between.
    val out = remember { Animatable(0f) }
    // Seconds since the cards landed.
    val reveal = remember { Animatable(0f) }
    // Seconds since a card was tapped; 0 when no day is opening.
    val enter = remember { Animatable(0f) }
    // The loop's fingers: the two that close, the one that taps, and how far that one is pressed.
    val fingers = remember { Animatable(0f) }
    val tapShown = remember { Animatable(0f) }
    val tapDown = remember { Animatable(0f) }
    val scroll = rememberScrollState()

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** The fingers lift: the spring carries the screen on to the days, or back to the conversation. */
    fun release(toDays: Boolean) {
        held = false
        timeline = toDays
        scope.launch {
            out.animateTo(
                if (toDays) 1f else 0f,
                if (reducedMotion) tween((FADE * 1000).toInt(), easing = PinchEase) else spring(DAMPING_RATIO, STIFFNESS),
            )
        }
        scope.launch {
            if (toDays) {
                reveal.animateTo(REVEAL_END, tween((REVEAL_END * 1000).toInt(), (REVEAL_AFTER * 1000).toInt(), LinearEasing))
            } else {
                reveal.animateTo(0f, tween((REVEAL_OUT * 1000).toInt(), easing = LinearEasing))
            }
        }
    }

    /** Plays the way in from [from] seconds, and ends on the conversation. */
    fun play(from: Float) {
        scope.launch {
            enter.snapTo(from)
            // Anything that takes over stops this here, and the swap below with it.
            enter.animateTo(ENTER_END, tween(((ENTER_END - from) * 1000).toInt(), easing = LinearEasing))
            // The conversation is complete under the last frame of the way in: swap the two at once.
            out.snapTo(0f)
            reveal.snapTo(0f)
            enter.snapTo(0f)
            timeline = false
        }
    }

    /** A day opens, from its card. */
    fun open(index: Int, auto: Boolean, from: Float = 0f) {
        if (!auto) tick()
        picked = index
        day = index
        selected?.invoke(index, list[index])
        if (reducedMotion) release(false) else play(from)
    }

    /** The state every loop starts from, set at once. */
    suspend fun rest() {
        held = false
        out.snapTo(0f)
        reveal.snapTo(0f)
        enter.snapTo(0f)
        fingers.snapTo(0f)
        tapShown.snapTo(0f)
        tapDown.snapTo(0f)
        picked = home
        day = home
        timeline = false
        scroll.scrollTo(0)
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        scope.launch {
            fingers.snapTo(0f)
            tapShown.snapTo(0f)
        }
        if (held) release(true)
        touches++
    }

    DisposableEffect(view) {
        // The page is light: dark icons in the status bar while it shows.
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            // After a touch: back to the first day's conversation, then the loop again.
            delay((USER_HOLD * 1000).toLong())
            while (enter.value > 0f) delay(100)
            if (timeline) {
                picked = home
                day = home
                release(false)
                delay((HOME * 1000).toLong())
            }
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        val fingerIn = tween<Float>((FINGER_IN * 1000).toInt(), easing = PinchEase)
        val fingerOut = tween<Float>((FINGER_OUT * 1000).toInt(), easing = PinchEase)
        while (true) {
            rest()
            until(PINCH_AT - FINGER_IN)
            launch { fingers.animateTo(1f, fingerIn) }

            until(PINCH_AT)
            held = true
            launch {
                out.snapTo(0f)
                out.animateTo(PINCH_TO, tween((PINCH_FOR * 1000).toInt(), easing = PinchEase))
            }

            until(PINCH_AT + PINCH_FOR)
            launch { fingers.animateTo(0f, fingerOut) }
            release(true)

            until(TAP_AT - FINGER_IN)
            launch { tapShown.animateTo(1f, fingerIn) }

            until(TAP_AT)
            launch {
                tapDown.animateTo(1f, tween(90, easing = PinchEase))
                tapDown.animateTo(0f, fingerOut)
            }
            launch {
                delay(120)
                tapShown.animateTo(0f, fingerOut)
            }
            // The step sets what it starts from: the days, landed and written.
            out.snapTo(1f)
            reveal.snapTo(REVEAL_END)
            timeline = true
            open(home, auto = true)

            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to pageColors[0], 0.6f to pageColors[0], 0.85f to pageColors[1], 1f to pageColors[2])),
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val frame by rememberUpdatedState(
            Frame(maxWidth.value, maxHeight.value, insets.calculateTopPadding().value, max(insets.calculateBottomPadding().value, 12f)),
        )
        val shown = days[day.coerceIn(0, days.lastIndex)]
        val last = shown.messages.size + 1

        /** Two fingers are down, [centroidY] dp from the top: what they have hold of. */
        fun pinchStart(centroidY: Float, density: Float): Int {
            if (enter.value > 0f) return GRIP_DONE
            if (!timeline) return GRIP_CHAT
            // The card under the fingers is the one that opens.
            val index = floor((centroidY + scroll.value / density - frame.cardsTop) / frame.pitch).toInt()
            if (index !in list.indices) return GRIP_DONE
            picked = index
            day = index
            return GRIP_CARD
        }

        fun pinchChange(grip: Int, scale: Float) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (grip == GRIP_CHAT) {
                    out.snapTo(clamp01((1f - scale) / (1f - SHEET_SCALE)))
                } else if (grip == GRIP_CARD && !reducedMotion) {
                    enter.snapTo(clamp01((scale - 1f) / (GROW - 1f)) * BLOW * SCRUB)
                }
            }
        }

        fun pinchEnd(grip: Int, scale: Float) {
            if (grip == GRIP_CHAT) {
                release(clamp01((1f - scale) / (1f - SHEET_SCALE)) > COMMIT)
            } else if (grip == GRIP_CARD) {
                val from = clamp01((scale - 1f) / (GROW - 1f)) * BLOW * SCRUB
                if (reducedMotion) {
                    if (scale > 1.2f) open(picked, auto = false)
                } else if (from > BLOW * SCRUB * 0.5f) {
                    open(picked, auto = false, from = from)
                } else {
                    // A spread that did not go far enough: the cards come back.
                    scope.launch { enter.animateTo(0f, tween((max(from, 0.05f) * 1000).toInt())) }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // Seen before the list and the cards, so that two fingers never scroll or tap.
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touched()
                        var grip = GRIP_NONE
                        var startSpan = 1f
                        var scale = 1f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val span = (a - b).getDistance()
                                if (grip == GRIP_NONE) {
                                    startSpan = max(span, 1f)
                                    grip = pinchStart((a.y + b.y) / 2f / density, density)
                                }
                                if (grip != GRIP_DONE) {
                                    scale = span / startSpan
                                    pinchChange(grip, scale)
                                }
                                event.changes.forEach { it.consume() }
                            } else if (grip != GRIP_NONE) {
                                // One of the two lifted: the pinch is over, and the finger left does nothing.
                                if (grip != GRIP_DONE) pinchEnd(grip, scale)
                                grip = GRIP_DONE
                                event.changes.forEach { it.consume() }
                            }
                            if (pressed.isEmpty()) break
                        }
                    }
                },
        ) {
            Conversation(shown, last, frame, ink, placeholder, out, enter, picked, scroll, reducedMotion)

            Days(days, frame, ink, timeline, reducedMotion, out, reveal, enter, picked, scroll) { index ->
                if (enter.value == 0f) open(index, auto = false)
            }

            // The date, alone on the page while the day opens.
            Column(
                Modifier
                    .offset(y = (frame.height * 0.453f - 34f).dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val e = enter.value
                        alpha = smooth(e, DATE_AT, DATE_AT + DATE_FADE) * (1f - smooth(e, DATE_AT + DATE_FADE + DATE_HOLD, DATE_AT + DATE_FADE * 2 + DATE_HOLD))
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val style = face(26f, 34f, ink, serif = true)
                BasicText(shown.weekday, style = style)
                BasicText(shown.date, style = style)
            }

            Column(
                Modifier
                    .offset(y = (frame.height - frame.bottom - 14f - 32f).dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = if (reducedMotion) clamp01(out.value) else smooth(out.value, 0.7f, 1f) * (1f - smooth(enter.value, 0f, 0.15f))
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicText(footnote, style = FootnoteStyle, maxLines = 1)
                BasicText(
                    "$unlockLabel ›",
                    style = face(11f, 16f, ink, FontWeight.SemiBold),
                    maxLines = 1,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = timeline,
                        role = Role.Button,
                    ) { onUnlock?.invoke() },
                )
            }

            // The round button stays through the pinch, and leaves and returns with the conversation's last piece.
            Box(
                Modifier
                    .offset(((frame.width - DOWN) / 2f).dp, (frame.inputTop - DOWN_LIFT - DOWN).dp)
                    .size(DOWN.dp)
                    .graphicsLayer {
                        val e = enter.value
                        alpha = if (e > 0f) max(1f - smooth(e, 0f, 0.15f), arrived(e, last)) else 1f
                    }
                    .background(Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(ArrowDown, ink, size = 14f)
            }

            Box(
                Modifier
                    .offset((SIDE - 14f).dp, (frame.top + 1f - 14f).dp)
                    .size(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = if (timeline) "Open the day" else "Show all days",
                    ) {
                        if (enter.value == 0f) {
                            if (timeline) {
                                open(picked, auto = false)
                            } else {
                                tick()
                                release(true)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Collapse, Mute, stroke = 1.4f)
            }
            Box(
                Modifier
                    .offset((frame.width - SIDE - 16f - 14f).dp, (frame.top + 1f - 14f).dp)
                    .size(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "More",
                    ) { onMenu?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(16.dp)) {
                    listOf(2.5f, 8f, 13.5f).forEach { x -> drawCircle(Mute, 1.4.dp.toPx(), Offset(x.dp.toPx(), 8.dp.toPx())) }
                }
            }

            if (autoPlay) {
                val half = FINGER_SIZE / 2f
                val centerX = frame.width / 2f
                val centerY = frame.height * 0.52f
                val targetY = frame.cardTop(home) + frame.cardHeight / 2f
                listOf(-1f, 1f).forEach { side ->
                    Finger(
                        Modifier.graphicsLayer {
                            val reach = 1f - (1f - SHEET_SCALE) * min(out.value, PINCH_TO)
                            alpha = fingers.value
                            translationX = (centerX - half + side * FINGER_REACH_X * reach).dp.toPx()
                            translationY = (centerY - half + side * FINGER_REACH_Y * reach).dp.toPx()
                        },
                    )
                }
                Finger(
                    Modifier.graphicsLayer {
                        alpha = tapShown.value
                        translationX = (centerX - half).dp.toPx()
                        translationY = (targetY - half).dp.toPx()
                        scaleX = 1f - 0.18f * tapDown.value
                        scaleY = scaleX
                    },
                )
            }
        }
    }
}

/** One of the loop's fingers. */
@Composable
private fun Finger(modifier: Modifier) {
    Box(modifier.size(FINGER_SIZE.dp).background(FingerFill, CircleShape).border(1.dp, FingerEdge, CircleShape))
}

@Composable
private fun Glyph(path: Path, color: Color, size: Float = 16f, stroke: Float = 1.5f) {
    Canvas(Modifier.size(size.dp)) {
        val k = this.size.width / 16f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(stroke.dp.toPx() / k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun Photo(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(url, context) { ImageRequest.Builder(context).data(url).crossfade(200).build() }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.background(PhotoTint))
}

/** The open day: what was said, and the bar to say more. */
@Composable
private fun Conversation(
    day: Day,
    last: Int,
    frame: Frame,
    ink: Color,
    placeholder: String,
    out: Animatable<Float, AnimationVector1D>,
    enter: Animatable<Float, AnimationVector1D>,
    picked: Int,
    scroll: ScrollState,
    still: Boolean,
) {
    val reply = face(16f, 22f, ink, serif = true)
    val bubble = face(15f, 20f, ink)

    /** A piece arrives in its turn when a day opens; the [early] ones go first when the fingers close. */
    fun piece(index: Int, early: Boolean = false) = Modifier.graphicsLayer {
        val e = enter.value
        alpha = if (e > 0f) arrived(e, index) else if (early) 1f - smooth(out.value, 0f, 0.3f) else 1f
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = out.value
                val e = enter.value
                var blur = 0f
                if (still) {
                    alpha = 1f - clamp01(p)
                } else if (e > 0f) {
                    // Arriving: its pieces fade in by themselves while it comes into focus.
                    val clear = smooth(e, CHAT_AT, CHAT_AT + CHAT_CLEAR)
                    scaleX = CHAT_FROM + (1f - CHAT_FROM) * clear
                    scaleY = scaleX
                    blur = CHAT_BLUR * (1f - clear)
                } else {
                    // Leaving: it shrinks towards the middle of its day's card.
                    val middle = frame.cardTop(picked) - scroll.value / density + frame.cardHeight / 2f
                    alpha = 1f - smooth(p, 0.12f, 0.62f)
                    translationY = ((middle - frame.height / 2f) * p).dp.toPx()
                    scaleX = 1f - (1f - SHEET_SCALE) * p
                    scaleY = scaleX
                    blur = SHEET_BLUR * smooth(p, 0f, 0.5f)
                }
                renderEffect = blurOf(blur.dp.toPx())
            },
    ) {
        val earlier = day.earlier.takeLast(2)
        Column(
            Modifier
                .offset(SIDE.dp, (frame.top - 10f).dp)
                .width((frame.width - SIDE * 2).dp)
                .then(piece(0))
                .blur(2.5.dp, BlurredEdgeTreatment.Unbounded),
        ) {
            earlier.forEachIndexed { at, line ->
                BasicText(
                    line,
                    style = reply.copy(color = ink.copy(alpha = if (at == earlier.lastIndex) 0.4f else 0.1f)),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }

        Box(Modifier.size(frame.width.dp, (frame.inputTop - 6f).dp).clipToBounds()) {
            Column(
                Modifier
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .padding(top = (frame.top + CHAT_TOP).dp),
                verticalArrangement = Arrangement.spacedBy(MESSAGE_GAP.dp),
            ) {
                BasicText(day.stamp, style = StampStyle, maxLines = 1, modifier = Modifier.fillMaxWidth().then(piece(0, early = true)))
                day.messages.forEachIndexed { at, message ->
                    val arriving = piece(at + 1)
                    when {
                        message.photo != null ->
                            Box(Modifier.fillMaxWidth().padding(horizontal = SIDE.dp).then(arriving), contentAlignment = Alignment.CenterEnd) {
                                Photo(message.photo, Modifier.size(PHOTO.dp).clip(RoundedCornerShape(8.dp)))
                            }
                        message.mine ->
                            Box(Modifier.fillMaxWidth().padding(horizontal = SIDE.dp).then(arriving), contentAlignment = Alignment.CenterEnd) {
                                Box(
                                    Modifier
                                        .widthIn(max = (frame.width * 0.7f).dp)
                                        .heightIn(min = 36.dp)
                                        .drawBehind {
                                            drawRoundRect(Color.White, cornerRadius = CornerRadius(BUBBLE_RADIUS.dp.toPx()))
                                            val unit = density
                                            val left = size.width - 10.dp.toPx()
                                            val top = size.height - 12.dp.toPx()
                                            withTransform({
                                                translate(left, top)
                                                scale(unit, unit, pivot = Offset.Zero)
                                            }) { drawPath(Tail, Color.White) }
                                        }
                                        .padding(horizontal = 15.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    BasicText(message.text.orEmpty(), style = bubble)
                                }
                            }
                        else ->
                            BasicText(
                                message.text.orEmpty(),
                                style = reply,
                                modifier = Modifier.padding(start = SIDE.dp).width((frame.width - SIDE - TEXT_TRAIL).dp).then(arriving),
                            )
                    }
                }
            }
        }

        Row(
            Modifier
                .offset(INPUT_SIDE.dp, frame.inputTop.dp)
                .size((frame.width - INPUT_SIDE * 2).dp, INPUT.dp)
                .then(piece(last, early = true)),
        ) {
            Box(Modifier.size(INPUT.dp).background(Glass, CircleShape), contentAlignment = Alignment.Center) {
                Glyph(Plus, Placeholder)
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.weight(1f).fillMaxHeight().background(Glass, CircleShape).padding(start = 16.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(placeholder, style = PlaceholderStyle, maxLines = 1, modifier = Modifier.weight(1f))
                Glyph(Wave, Placeholder)
            }
        }
    }
}

/** The list of days. The picked one is also the sheet the conversation shrinks into. */
@Composable
private fun Days(
    days: List<Day>,
    frame: Frame,
    ink: Color,
    timeline: Boolean,
    still: Boolean,
    out: Animatable<Float, AnimationVector1D>,
    reveal: Animatable<Float, AnimationVector1D>,
    enter: Animatable<Float, AnimationVector1D>,
    picked: Int,
    scroll: ScrollState,
    onCard: (Int) -> Unit,
) {
    val title = face(19f, 26f, ink, serif = true)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = if (still) null else blurOf((BLOW_BLUR * easeIn(enter.value / BLOW)).dp.toPx()) }
            .verticalScroll(scroll, enabled = timeline),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height((frame.cardTop(days.size) - GAP + FOOTER + frame.bottom).dp)
                // The cards' surfaces: the picked one is drawn from the whole screen down to its card.
                .drawBehind {
                    for (index in days.indices) {
                        val pose = pose(index, frame, out.value, enter.value, picked, scroll.value / density, still)
                        if (pose.alpha <= 0f) continue
                        val width = pose.width * pose.scale
                        val height = pose.height * pose.scale
                        val left = pose.left + (pose.width - width) / 2f
                        val top = pose.top + pose.shift + (pose.height - height) / 2f
                        val radius = (pose.radius * pose.scale).dp.toPx()
                        val edge = 1.dp.toPx() * pose.scale
                        drawRoundRect(
                            CardFill.copy(alpha = CardFill.alpha * clamp01(pose.alpha)),
                            Offset(left.dp.toPx(), top.dp.toPx()),
                            Size(width.dp.toPx(), height.dp.toPx()),
                            CornerRadius(radius),
                        )
                        drawRoundRect(
                            CardEdge.copy(alpha = CardEdge.alpha * clamp01(pose.alpha)),
                            Offset(left.dp.toPx() + edge / 2f, top.dp.toPx() + edge / 2f),
                            Size(width.dp.toPx() - edge, height.dp.toPx() - edge),
                            CornerRadius(max(radius - edge / 2f, 0f)),
                            style = Stroke(edge),
                        )
                    }
                },
        ) {
            days.forEachIndexed { index, day ->
                val top = frame.cardTop(index)
                Box(
                    Modifier
                        .offset(MARGIN.dp, top.dp)
                        .size(frame.cardWidth.dp, frame.cardHeight.dp)
                        // What is written on the card moves with its surface.
                        .graphicsLayer {
                            val pose = pose(index, frame, out.value, enter.value, picked, scroll.value / density, still)
                            alpha = clamp01(pose.alpha)
                            translationX = (pose.left - MARGIN).dp.toPx()
                            translationY = (pose.top - top + pose.shift).dp.toPx()
                            scaleX = pose.scale
                            scaleY = pose.scale
                            clip = true
                            shape = RoundedCornerShape(RADIUS.dp)
                        }
                        .semantics { contentDescription = "${day.label}. ${day.title.joinToString("") { it.text }}" }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            // The cards are there while the conversation is open, unseen: they take no touches then.
                            enabled = timeline,
                            role = Role.Button,
                        ) { onCard(index) },
                ) {
                    Column(
                        Modifier
                            .padding(top = CARD_PAD.dp, start = CARD_PAD.dp, end = CARD_PAD.dp)
                            .graphicsLayer {
                                val shown = if (still) 1f else smooth(reveal.value - index * REVEAL_STAGGER, 0f, REVEAL_IN)
                                alpha = shown
                                renderEffect = blurOf((REVEAL_BLUR * (1f - shown)).dp.toPx())
                            },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BasicText(day.label, style = CardLabelStyle, maxLines = 1)
                        BasicText(
                            buildAnnotatedString {
                                day.title.forEach { piece ->
                                    if (piece.key) withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(piece.text) } else append(piece.text)
                                }
                            },
                            style = title,
                            maxLines = 2,
                        )
                    }
                    Box(
                        Modifier.matchParentSize().graphicsLayer {
                            val up = if (still) 1f else easeOut((reveal.value - 0.1f) / 0.45f)
                            alpha = clamp01(up * 2f)
                            translationY = (THUMB_RISE * (1f - up)).dp.toPx()
                        },
                    ) {
                        day.photos.take(Thumbs.size).forEachIndexed { at, photo ->
                            Photo(
                                photo,
                                Modifier
                                    .offset(Thumbs[at].first.dp, (frame.cardHeight - THUMB / 2f).dp)
                                    .size(THUMB.dp)
                                    .graphicsLayer { rotationZ = Thumbs[at].second }
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}
