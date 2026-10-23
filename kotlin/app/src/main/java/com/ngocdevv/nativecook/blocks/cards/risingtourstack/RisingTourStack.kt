package com.ngocdevv.nativecook.blocks.cards.risingtourstack

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @param city Set in huge capitals above the stack.
 * @param photo Address of the photo.
 * @param tint The flat colour shown until the photo arrives.
 */
class RisingTourDestination(
    val city: String,
    val title: String,
    val text: String,
    val rating: String,
    val price: String,
    val photo: String,
    val tint: Color,
)

private const val WIDTH = 340f
private const val HEIGHT = 600f
private const val RADIUS = 32f

private const val BAR_Y = 36f

// The name is set large in the system serif and squeezed sideways into a condensed face.
private const val NAME_Y = 104f
private const val NAME_SIZE = 88f
private const val NAME_LINE = 104f
private const val NAME_SQUEEZE = 0.62f

// Each letter fades in over LETTER_SECONDS while it slides in from this offset, LETTER_GAP after the one before.
private const val LETTER_FROM_X = 22f
private const val LETTER_FROM_Y = -10f
private const val LETTER_SECONDS = 0.45f
private const val LETTER_GAP = 0.07f
private const val TYPE_SECONDS = 1.2f

// The previous name is gone after this long.
private const val NAME_FADE = 0.18f

private const val CARD_WIDTH = 316f
private const val CARD_HEIGHT = 290f
private const val CARD_TOP = 232f
private const val CARD_RADIUS = 22f
private const val PHOTO_INSET = 4f
private const val PHOTO_HEIGHT = 178f
private const val PHOTO_RADIUS = 18f

// Each place further back shows this much above the card in front, and is this much narrower.
private const val PEEK = 22f
private const val SHRINK = 0.115f

// Degrees the third card leans.
private const val LEAN = 3f

// Points a thrown card travels, and degrees it turns on the way.
private const val AWAY = 560f
private const val AWAY_TURN = 7f

// How far below its place the deck starts when it is dealt.
private const val RISE = 380f

// A card's face clears as it comes forward: the grey over it is this many times its depth.
private const val COVER_RATE = 1.2f
private val CoverNear = Color(0xFF9AA3A3)
private val CoverFar = Color(0xFF7D8787)

private const val STACK_TOP = 180f
private const val BUTTONS_TOP = 536f
private const val BUTTON_HEIGHT = 44f
private const val BUTTONS_RISE = 8f

// A release throws the card past this distance or this speed (points, points per second).
private const val THROW_DISTANCE = 80f
private const val THROW_SPEED = 500f

// The front of the stack follows a spring of mass 1, damped just short of critically: it must
// not run past its target, or the next card would start to leave.
private const val FRONT_STIFFNESS = 144f
private const val FRONT_DAMPING = 23.5f

// Dealing (seconds from its start): the deck rises on one spring, the cards behind pop on another.
private const val RISE_AT = 0.1f
private const val RISE_STIFFNESS = 120f
private const val RISE_DAMPING = 17f
private const val POP_AT = 0.55f
private const val POP_GAP = 0.17f
private const val POP_STIFFNESS = 260f
private const val POP_DAMPING = 16f
private const val BUTTONS_AT = 0.95f
private const val BUTTONS_SECONDS = 0.35f
private const val DEAL_SECONDS = 1.8f

// Pause between the last card leaving and the next deal.
private const val DEAL_PAUSE = 0.45f
private const val HEART_STIFFNESS = 420f
private const val HEART_DAMPING = 13f

// autoPlay (seconds): the first throw, then one per destination spread evenly, then the deal.
private const val FIRST = 0.8f
private const val TAIL = 3.2f
private const val LOOP = 8f

// After a touch the loop waits this long before it deals again and starts over.
private const val IDLE = 2.5f

private val Star = Color(0xFFFFE14D)
private val Heart = Color(0xFFFF5A79)

private const val BACK_ICON = "M17 10 L3 10 M8.5 4.5 L3 10 L8.5 15.5"
private const val SEARCH_ICON =
    "M9 2.8 C12.42 2.8 15.2 5.58 15.2 9 C15.2 12.42 12.42 15.2 9 15.2 C5.58 15.2 2.8 12.42 2.8 9 C2.8 5.58 5.58 2.8 9 2.8 Z M13.6 13.6 L17.5 17.5"
private const val MAP_ICON = "M1.5 4 L6.5 2 L11.5 4 L16.5 2 L16.5 14 L11.5 16 L6.5 14 L1.5 16 Z M6.5 2 L6.5 14 M11.5 4 L11.5 16"
private const val STAR_ICON =
    "M8 0.8 L9.82 5.49 L14.85 5.78 L10.95 8.96 L12.23 13.83 L8 11.1 L3.77 13.83 L5.05 8.96 L1.15 5.78 L6.18 5.49 Z"
private const val HEART_ICON =
    "M10 17 C4.5 13.2 2 10.4 2 7.3 C2 4.9 3.9 3.2 6.1 3.2 C7.7 3.2 9.1 4 10 5.4 C10.9 4 12.3 3.2 13.9 3.2 C16.1 3.2 18 4.9 18 7.3 C18 10.4 15.5 13.2 10 17 Z"

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun tourText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val HeadingStyle = tourText(13f, 16f, FontWeight.Normal, rgba(255, 255, 255, 0.9f))
private val LetterStyle = tourText(NAME_SIZE, NAME_LINE, FontWeight.Normal, Color.White).copy(fontFamily = FontFamily.Serif)
private val RatingStyle = tourText(12f, 15f, FontWeight.SemiBold, Color.White)
private val TitleStyle = tourText(15f, 18f, FontWeight.SemiBold, Color(0xFF14171A), -0.2f)
private val PriceStyle = tourText(14f, 17f, FontWeight.Bold, Color(0xFF14171A))
private val BodyStyle = tourText(10f, 14f, FontWeight.Normal, Color(0xFF6A7076))
private val AvailableStyle = tourText(13f, 16f, FontWeight.Medium, Color(0xFF86E3A6))
private val MapStyle = tourText(13f, 16f, FontWeight.Medium, Color.White)

private val NumberOrCommand = Regex("[MLCZ]|-?\\d+(?:\\.\\d+)?")

private fun svgPath(svg: String): Path {
    val path = Path()
    val tokens = NumberOrCommand.findAll(svg).map { it.value }.toList()
    var at = 0
    fun number() = tokens[at++].toFloat()
    while (at < tokens.size) {
        when (tokens[at++]) {
            "M" -> path.moveTo(number(), number())
            "L" -> path.lineTo(number(), number())
            "C" -> path.cubicTo(number(), number(), number(), number(), number(), number())
            else -> path.close()
        }
    }
    return path
}

/** Where a spring of mass 1 released at rest has got to after [t] seconds, on its way from 0 to 1. */
private fun springAt(t: Float, stiffness: Float, damping: Float): Float {
    if (t <= 0f) return 0f
    val decay = damping / 2f
    val omega = sqrt(stiffness - decay * decay)
    return 1f - exp(-decay * t) * (cos(omega * t) + decay / omega * sin(omega * t))
}

/** How far letter number [order] has arrived, [type] seconds after the name changed. */
private fun letterAt(type: Float, order: Int): Float {
    val p = ((type - order * LETTER_GAP) / LETTER_SECONDS).coerceIn(0f, 1f)
    return 1f - (1f - p).pow(3)
}

/** The buttons fade in at the end of a deal, and out while the last card leaves. */
private fun buttonsAt(front: Float, deal: Float, count: Int): Float {
    val p = ((deal - BUTTONS_AT) / BUTTONS_SECONDS).coerceIn(0f, 1f)
    val leaving = ((front - (count - 1)) / 0.3f).coerceIn(0f, 1f)
    return min(1f - (1f - p).pow(3), 1f - leaving)
}

/**
 * @param shift Vertical offset from the front card's place, applied after scale and rotation about the centre.
 * @param cover The grey that hides a card's face while it waits behind.
 * @param coverFar How much of the darker grey is over it.
 */
private class TourPose(
    val shift: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float,
    val cover: Float,
    val coverFar: Float,
)

/** Where card [i] is: [front] is the index of the card at the front and [deal] the seconds since the deck was dealt. */
private fun poseOf(i: Int, front: Float, deal: Float, reduced: Boolean): TourPose {
    val rel = i - front
    if (rel < 0f) {
        // Thrown: up and away, turning a little. Under reduced motion it fades where it is.
        val gone = min(1f, -rel)
        return TourPose(
            shift = if (reduced) 0f else -gone * AWAY,
            scale = 1f,
            rotation = if (reduced) 0f else gone * AWAY_TURN,
            opacity = if (reduced) 1f - gone else 1f,
            cover = 0f,
            coverFar = 0f,
        )
    }
    val moving = if (reduced) DEAL_SECONDS else deal
    val rise = springAt(moving - RISE_AT, RISE_STIFFNESS, RISE_DAMPING)
    // A card behind stays hidden under the front one until its turn to pop up.
    val pop = if (i == 0) 1f else springAt(moving - POP_AT - (i - 1) * POP_GAP, POP_STIFFNESS, POP_DAMPING)
    val depth = min(rel, 2f) * pop
    val scale = 1f - SHRINK * depth
    return TourPose(
        // Scaling about the centre lifts the top edge; the shift puts it back, PEEK above the card in front.
        shift = -PEEK * depth - (1f - scale) * CARD_HEIGHT / 2f + (1f - rise) * RISE,
        scale = scale,
        rotation = LEAN * (depth - 1f).coerceIn(0f, 1f),
        opacity = if (rel > 2.5f) 0f else if (reduced) min(1f, deal / 0.2f) else 1f,
        cover = (depth * COVER_RATE).coerceIn(0f, 1f),
        coverFar = (depth - 1f).coerceIn(0f, 1f),
    )
}

/** The animated numbers, and where the stack is heading. */
private class TourMotion(private val scope: CoroutineScope, dealt: Boolean) {
    /** Index of the card at the front; fractional while one is on its way out. */
    val front = mutableFloatStateOf(0f)

    /** Seconds since the deck was dealt, since the name changed, and since the heart was tapped. */
    val deal = mutableFloatStateOf(if (dealt) DEAL_SECONDS else 0f)
    val type = mutableFloatStateOf(if (dealt) TYPE_SECONDS else 0f)
    val heart = mutableFloatStateOf(1f)
    val nameIndex = mutableIntStateOf(0)
    val prevIndex = mutableIntStateOf(-1)

    /** The card the stack is heading for; the number of cards once the last one is thrown. */
    val at = mutableIntStateOf(0)
    var pressed = false
    var dragged = 0f
    private var moving: Job? = null
    private var dealing: Job? = null
    private val clocks = HashMap<MutableFloatState, Job>()

    fun run(clock: MutableFloatState, seconds: Float) {
        clocks[clock]?.cancel()
        clocks[clock] = scope.launch {
            animate(0f, seconds, animationSpec = tween((seconds * 1000).toInt(), easing = LinearEasing)) { value, _ ->
                clock.floatValue = value
            }
        }
    }

    fun set(value: Float) {
        moving?.cancel()
        front.floatValue = value
    }

    fun settle(reduced: Boolean, velocity: Float = 0f) {
        moving?.cancel()
        val target = at.intValue.toFloat()
        if (reduced) {
            front.floatValue = target
            return
        }
        moving = scope.launch {
            animate(
                front.floatValue, target, velocity,
                spring(FRONT_DAMPING / (2f * sqrt(FRONT_STIFFNESS)), FRONT_STIFFNESS),
            ) { value, _ -> front.floatValue = value }
        }
    }

    fun dealDeck() {
        dealing?.cancel()
        at.intValue = 0
        set(0f)
        prevIndex.intValue = -1
        nameIndex.intValue = 0
        run(deal, DEAL_SECONDS)
        run(type, TYPE_SECONDS)
    }

    /** Throws the front card. The next destination takes its place, or the deck is dealt again. */
    fun advance(count: Int, reduced: Boolean, velocity: Float = 0f): Int {
        if (at.intValue >= count) return -1
        prevIndex.intValue = at.intValue
        at.intValue += 1
        nameIndex.intValue = if (at.intValue < count) at.intValue else -1
        run(type, TYPE_SECONDS)
        settle(reduced, velocity)
        if (at.intValue == count) {
            dealing?.cancel()
            dealing = scope.launch {
                delay((DEAL_PAUSE * 1000).toLong())
                dealDeck()
            }
        }
        return at.intValue % count
    }
}

/**
 * A dark panel with a destination in huge condensed serif capitals and a stack of three tour cards.
 * The deck is dealt on appear: the letters arrive one after another, the front card rises from the
 * bottom edge, the two cards behind it pop up and the buttons fade in. Swiping the front card up
 * sends it away and brings the next destination forward; after the last one the deck is dealt again.
 *
 * @param autoPlay Swipes through the destinations by itself and deals the deck again, in a loop.
 * @param onChange Called with the index of the destination that comes to the front.
 * @param onLike Called with the destination's index and whether it is now liked.
 */
@Composable
fun RisingTourStack(
    destinations: List<RisingTourDestination> = RisingTourDestinations,
    autoPlay: Boolean = false,
    heading: String = "Your Destination",
    availableLabel: String = "Available",
    mapLabel: String = "See on Map",
    onChange: ((index: Int) -> Unit)? = null,
    onLike: ((index: Int, liked: Boolean) -> Unit)? = null,
    onMap: ((index: Int) -> Unit)? = null,
    onBook: ((index: Int) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val count = destinations.size
    if (count == 0) return
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    // A self-playing deck starts dealt, so its loop begins and ends on the same frame.
    val motion = remember { TourMotion(scope, dealt = auto) }
    var liked by remember { mutableStateOf(setOf<Int>()) }
    // Counts touches: a change restarts the autoPlay loop after a pause.
    var touches by remember { mutableIntStateOf(0) }
    val changed by rememberUpdatedState(onChange)
    val panelShadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.22f), sigma = 24f) }
    val cardShadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.22f), sigma = 14f) }

    LaunchedEffect(auto) {
        if (!auto) motion.dealDeck()
    }

    LaunchedEffect(auto, touches, count) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // After a touch the loop waits, throws what is left of the deck and starts over.
            delay((IDLE * 1000).toLong())
            if (motion.pressed) return@LaunchedEffect
            if (motion.at.intValue != 0) {
                while (motion.at.intValue < count) motion.advance(count, reduced)
                delay((TAIL * 1000).toLong())
            }
        }
        val gap = if (count > 1) (LOOP - TAIL - FIRST) / (count - 1) else 0f
        while (true) {
            for (i in 0 until count) {
                delay(((if (i == 0) FIRST else gap) * 1000).toLong())
                motion.advance(count, reduced)
            }
            delay((TAIL * 1000).toLong())
        }
    }

    val front = motion.front
    val deal = motion.deal
    val type = motion.type
    val shown = motion.at.intValue.coerceIn(0, count - 1)
    val current = destinations[shown]
    val isLiked = shown in liked

    // Swiping up anywhere on the stack throws the front card.
    val drag = rememberDraggableState { delta ->
        if (motion.at.intValue < count) {
            motion.dragged += delta / density
            // Only upwards: the card follows the finger point for point.
            motion.set(motion.at.intValue + (-motion.dragged / AWAY).coerceIn(0f, 1f))
        }
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .drawBehind {
                surface(0.dp) { _, _, w, h -> drawIntoCanvas { panelShadow.draw(it.nativeCanvas, 0f, 16f, w, h, RADIUS) } }
            }
            .clip(RoundedCornerShape(RADIUS.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2B3238), Color(0xFF1C2126)))),
    ) {
        Row(
            Modifier.offset(16.dp, (BAR_Y - 18f).dp).width((WIDTH - 32f).dp).height(36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TourPress("Back", { onBack?.invoke() }) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { TourIcon(BACK_ICON, 20f, 1.6f) }
            }
            TourPress("Search", { onSearch?.invoke() }) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { TourIcon(SEARCH_ICON, 20f, 1.6f) }
            }
        }
        Box(
            Modifier.offset(60.dp, (BAR_Y - 18f).dp).size((WIDTH - 120f).dp, 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(heading, style = HeadingStyle, maxLines = 1)
        }

        // The name: the previous one fades while the letters of the next arrive one by one.
        destinations.forEachIndexed { index, item ->
            Row(
                Modifier
                    .offset(0.dp, (NAME_Y - NAME_LINE / 2f).dp)
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
                    .graphicsLayer { scaleX = NAME_SQUEEZE }
                    .clearAndSetSemantics {},
            ) {
                item.city.forEachIndexed { order, letter ->
                    BasicText(
                        letter.toString(),
                        style = LetterStyle,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            if (motion.nameIndex.intValue == index) {
                                val e = letterAt(type.floatValue, order)
                                alpha = e
                                if (!reduced) {
                                    translationX = ((1f - e) * LETTER_FROM_X).dp.toPx()
                                    translationY = ((1f - e) * LETTER_FROM_Y).dp.toPx()
                                }
                            } else {
                                alpha = if (motion.prevIndex.intValue == index) 1f - min(1f, type.floatValue / NAME_FADE) else 0f
                            }
                        },
                    )
                }
            }
        }

        for (index in destinations.indices.reversed()) {
            val item = destinations[index]
            Box(
                Modifier
                    .offset(((WIDTH - CARD_WIDTH) / 2f).dp, CARD_TOP.dp)
                    .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
                    .graphicsLayer {
                        val pose = poseOf(index, front.floatValue, deal.floatValue, reduced)
                        alpha = pose.opacity
                        translationY = pose.shift.dp.toPx()
                        rotationZ = pose.rotation
                        scaleX = pose.scale
                        scaleY = pose.scale
                    }
                    .drawBehind {
                        surface(0.dp) { _, _, w, h -> drawIntoCanvas { cardShadow.draw(it.nativeCanvas, 0f, 10f, w, h, CARD_RADIUS) } }
                    }
                    .background(Color.White, RoundedCornerShape(CARD_RADIUS.dp))
                    .clearAndSetSemantics {},
            ) {
                TourPhoto(
                    item.photo, item.tint,
                    Modifier
                        .offset(PHOTO_INSET.dp, PHOTO_INSET.dp)
                        .size((CARD_WIDTH - PHOTO_INSET * 2f).dp, PHOTO_HEIGHT.dp)
                        .clip(RoundedCornerShape(PHOTO_RADIUS.dp)),
                )
                Row(
                    Modifier.offset(14.dp, 14.dp).size(60.dp, 28.dp).background(rgba(38, 42, 46, 0.6f), CircleShape),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TourIcon(STAR_ICON, 16f, 0f, fill = Star)
                    BasicText(item.rating, style = RatingStyle, maxLines = 1)
                }
                BasicText(
                    item.title,
                    style = TitleStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(20.dp, 196.dp).width(200.dp),
                )
                Box(
                    Modifier.offset((CARD_WIDTH - 20f - 62f).dp, 199.dp).size(62.dp, 30.dp).background(Color(0xFFEEEFF0), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(item.price, style = PriceStyle, maxLines = 1)
                }
                BasicText(
                    item.text,
                    style = BodyStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(20.dp, 243.dp).width((CARD_WIDTH - 40f).dp),
                )
                // The grey that hides the card's face while it waits behind.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = poseOf(index, front.floatValue, deal.floatValue, reduced).cover }
                        .background(CoverNear, RoundedCornerShape(CARD_RADIUS.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = poseOf(index, front.floatValue, deal.floatValue, reduced).coverFar }
                            .background(CoverFar, RoundedCornerShape(CARD_RADIUS.dp)),
                    )
                }
            }
        }

        Box(
            Modifier
                .offset(0.dp, STACK_TOP.dp)
                .size(WIDTH.dp, (BUTTONS_TOP - STACK_TOP).dp)
                .semantics { contentDescription = "${current.city}: ${current.title}, ${current.price}" }
                .draggable(
                    state = drag,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        motion.pressed = true
                        motion.dragged = 0f
                        touches++
                    },
                    onDragStopped = { velocityPx ->
                        motion.pressed = false
                        if (motion.at.intValue < count) {
                            val up = -velocityPx / density
                            if (-motion.dragged > THROW_DISTANCE || up > THROW_SPEED) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                // Faster than this and even this spring would overshoot.
                                val limit = sqrt(FRONT_STIFFNESS) * (motion.at.intValue + 1 - front.floatValue)
                                val index = motion.advance(count, reduced, min(limit, up / AWAY))
                                if (index >= 0) changed?.invoke(index)
                            } else {
                                motion.settle(reduced)
                            }
                        }
                        touches++
                    },
                ),
        )

        Row(
            Modifier
                .offset(0.dp, BUTTONS_TOP.dp)
                .fillMaxWidth()
                .height(BUTTON_HEIGHT.dp)
                .graphicsLayer {
                    val visible = buttonsAt(front.floatValue, deal.floatValue, count)
                    alpha = visible
                    if (!reduced) translationY = ((1f - visible) * BUTTONS_RISE).dp.toPx()
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            TourPress(availableLabel, { onBook?.invoke(shown) }) {
                Box(
                    Modifier.size(88.dp, BUTTON_HEIGHT.dp).background(Color(0xFF1D3B2B), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(availableLabel, style = AvailableStyle, maxLines = 1)
                }
            }
            TourPress(mapLabel, { onMap?.invoke(shown) }) {
                Row(
                    Modifier.size(140.dp, BUTTON_HEIGHT.dp).background(Color(0xFF30363C), CircleShape).padding(start = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(32.dp).background(Color(0xFF474E55), CircleShape), contentAlignment = Alignment.Center) {
                        TourIcon(MAP_ICON, 18f, 1.4f)
                    }
                    BasicText(mapLabel, style = MapStyle, maxLines = 1)
                }
            }
            TourPress(
                if (isLiked) "Unlike" else "Like",
                {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    liked = if (isLiked) liked - shown else liked + shown
                    motion.run(motion.heart, 1f)
                    touches++
                    onLike?.invoke(shown, !isLiked)
                },
            ) {
                Box(Modifier.size(BUTTON_HEIGHT.dp).background(Color(0xFF30363C), CircleShape), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.graphicsLayer {
                            val pop = if (reduced) 1f else 0.7f + 0.3f * springAt(motion.heart.floatValue, HEART_STIFFNESS, HEART_DAMPING)
                            scaleX = pop
                            scaleY = pop
                        },
                    ) {
                        TourIcon(HEART_ICON, 20f, 1.5f, stroke = if (isLiked) Heart else Color.White, fill = if (isLiked) Heart else null)
                    }
                }
            }
        }
    }
}

/** An icon drawn from an SVG path in a square of [size] dp, stroked, filled, or both. */
@Composable
private fun TourIcon(svg: String, size: Float, width: Float, stroke: Color = Color.White, fill: Color? = null) {
    val path = remember(svg) { svgPath(svg) }
    Box(
        Modifier.size(size.dp).drawBehind {
            surface(0.dp) { _, _, _, _ ->
                if (fill != null) drawPath(path, fill)
                if (width > 0f) drawPath(path, stroke, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        },
    )
}

/** A remote photo cropped to fill, over a flat tint until it arrives. */
@Composable
private fun TourPhoto(url: String, tint: Color, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current).data(url).crossfade(200).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(tint),
    )
}

/** A tappable part of the panel: scales down a little while pressed. */
@Composable
private fun TourPress(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(130, easing = EaseOutStrong), label = "press")
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) { content() }
}

private fun photoUrl(id: String) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=800&q=80"

val RisingTourDestinations = listOf(
    RisingTourDestination(
        city = "PARIS",
        title = "Eiffel Tower 2nd Floor or Summit Guided Tour",
        text = "Make the best use of your precious time in Paris with pre-reserved tickets to the Eiffel Tower. Choose from a guided tour of the 2nd floor or the summit.",
        rating = "4.5",
        price = "€ 99",
        photo = photoUrl("1502602898657-3e91760cbb34"),
        tint = Color(0xFF8A7F9C),
    ),
    RisingTourDestination(
        city = "ROME",
        title = "Colosseum Arena Floor and Roman Forum Tour",
        text = "Step onto the arena floor through the gladiators' gate, then follow your guide across the Roman Forum and up the Palatine Hill.",
        rating = "4.8",
        price = "€ 64",
        photo = photoUrl("1552832230-c0197dd311b5"),
        tint = Color(0xFF8D8478),
    ),
    RisingTourDestination(
        city = "VENICE",
        title = "Grand Canal Gondola Ride with Live Commentary",
        text = "Glide under the Rialto Bridge and along the quiet side canals while your gondolier points out the palaces that line the water.",
        rating = "4.6",
        price = "€ 38",
        photo = photoUrl("1523906834658-6e24ef2386f9"),
        tint = Color(0xFF7FA3AD),
    ),
)
