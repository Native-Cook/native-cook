package com.ngocdevv.nativecook.blocks.cards.expandingtripcard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ExpandingTripCardDay(
    val label: String,
    val date: String,
    val activity: String,
    val duration: String,
    val distance: String,
)

/**
 * @param title The name in capitals; a line break splits it over two lines.
 * @param length The number in the pink badge; [lengthUnit] is the word under it.
 * @param photo Address of the photo.
 * @param tint The flat colour shown until the photo arrives.
 * @param days The day cards shown once the trip is open.
 */
class ExpandingTripCardTrip(
    val title: String,
    val dates: String,
    val length: String,
    val arrivalPlace: String,
    val arrivalTime: String,
    val photo: String,
    val tint: Color,
    val days: List<ExpandingTripCardDay>,
    val lengthUnit: String = "DAYS",
    val arrivalLabel: String = "ARRIVAL",
)

private const val WIDTH = 340f
private const val HEIGHT = 600f
private const val RADIUS = 28f

private const val CARD_WIDTH = 232f
private const val CARD_HEIGHT = 290f
private const val CARD_LEFT = 22f
private const val CARD_TOP = 108f
private const val CARD_RADIUS = 10f

// Distance from one card to the next.
private const val STEP = 244f

// Past either end the row follows the finger at a third of its speed.
private const val RESISTANCE = 0.3f

// Seconds of travel a release is credited with, as SwiftUI predicts the end of a drag.
private const val THROW = 0.25f

// The line under the row, and the notch that points up at the current card.
private const val LINE_Y = 424f
private const val NOTCH_X = 46f
private const val NOTCH_HEIGHT = 9f
private const val ARRIVAL_TOP = 446f

// How far the arrival details of a neighbouring card sit to the side.
private const val ARRIVAL_SHIFT = 36f
private const val TAB_Y = 548f

// Open: the title's place and size, and the day cards.
private val TITLE_INSET = Offset(16f, 16f)
private val OPEN_TITLE = Offset(24f, 78f)
private const val OPEN_TITLE_SCALE = 1.13f
private const val ACTIVITIES_Y = 304f
private const val DAY_TOP = 322f
private const val DAY_LEFT = 24f
private const val DAY_WIDTH = 146f
private const val DAY_HEIGHT = 172f
private const val MAP_WIDTH = 122f
private const val MAP_HEIGHT = 56f
private const val DAY_GAP = 12f
private const val DAY_RISE = 120f

private const val SLIDE_STIFFNESS = 170f
private const val SLIDE_DAMPING = 24f
private const val OPEN_STIFFNESS = 170f
private const val OPEN_DAMPING = 22f

// The day cards spring up one after another, on a clock that runs while the trip opens.
private const val DAY_AT = 0.28f
private const val DAY_GAP_SECONDS = 0.09f
private const val DAY_STIFFNESS = 210f
private const val DAY_DAMPING = 19f
private const val STAGE_SECONDS = 1.3f
private const val STAGE_LEAVE = 0.3f
private const val REDUCED_MS = 200

// autoPlay (seconds): next trip, back to the first, open it, close it.
private const val NEXT_AT = 0.8f
private const val BACK_AT = 2.3f
private const val OPEN_AT = 3.8f
private const val CLOSE_AT = 6.2f
private const val LOOP = 8f

// After a touch the loop waits this long, returns to the first card, and starts over.
private const val IDLE = 2.5f
private const val SETTLE = 1.2f

private val Ink = Color(0xFF14171A)
private val Mute = Color(0xFF9A9FA6)
private val Line = Color(0xFFE1E3E7)
private val Mint = Color(0xFF5ED3B0)
private val Blue = Color(0xFF7F9CF5)
private val Pink = Color(0xFFF0688A)
private val PinkPale = Color(0xFFFDE4EA)

private const val SEARCH_ICON =
    "M9 2.8 C12.42 2.8 15.2 5.58 15.2 9 C15.2 12.42 12.42 15.2 9 15.2 C5.58 15.2 2.8 12.42 2.8 9 C2.8 5.58 5.58 2.8 9 2.8 Z M13.6 13.6 L17.5 17.5"
private const val BACK_ICON = "M12.5 4.5 L7 10 L12.5 15.5"
private const val BOOT_ICON =
    "M5 3 L9 3 L9 8.5 L13.5 10.5 C14.6 11 15.2 11.8 15.2 13 L15.2 13.8 L3.6 13.8 L3.6 10.5 C3.6 9.4 5 8.6 5 7 Z M2.8 15.6 L15.6 15.6"
private const val ARRIVAL_ICON = "M5 5 L13 13 M13 7 L13 13 L7 13 M4 16.5 L16 16.5"
private const val TRIPS_ICON =
    "M11 3.5 C15.14 3.5 18.5 6.86 18.5 11 C18.5 15.14 15.14 18.5 11 18.5 C6.86 18.5 3.5 15.14 3.5 11 C3.5 6.86 6.86 3.5 11 3.5 Z M3.5 11 L18.5 11 M11 3.5 C8 6 8 16 11 18.5 C14 16 14 6 11 3.5"
private const val SAVED_ICON = "M6.5 4 L15.5 4 L15.5 18 L11 14.5 L6.5 18 Z"
private const val ALERTS_ICON = "M6 15 L6 10 C6 7 8 5 11 5 C14 5 16 7 16 10 L16 15 L17.5 16.5 L4.5 16.5 Z M9.5 18.5 L12.5 18.5"

// The little map on a day card: water, a route and a pin with its dot.
private const val MAP_WATER = "M70 0 L126 0 L126 30 C110 34 96 22 84 26 C76 28 72 12 70 0 Z"
private const val MAP_ROUTE = "M10 44 C30 30 44 46 62 34 C74 26 80 30 88 22"
private const val MAP_PIN = "M88 24 C84 19 82.5 17 82.5 14.5 C82.5 11.5 85 9 88 9 C91 9 93.5 11.5 93.5 14.5 C93.5 17 92 19 88 24 Z"
private const val MAP_DOT = "M88 12.7 C89 12.7 89.8 13.5 89.8 14.5 C89.8 15.5 89 16.3 88 16.3 C87 16.3 86.2 15.5 86.2 14.5 C86.2 13.5 87 12.7 88 12.7 Z"

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun tripText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val HeadingStyle = tripText(13f, 16f, FontWeight.ExtraBold, Ink, 1.2f)
private val ArrivalLabelStyle = tripText(8f, 11f, FontWeight.ExtraBold, Pink, 1f)
private val ArrivalPlaceStyle = tripText(12.5f, 16f, FontWeight.ExtraBold, Ink, 0.8f)
private val ArrivalTimeStyle = tripText(11f, 15f, FontWeight.Normal, Mute)
private val TabStyle = tripText(9f, 12f, FontWeight.Bold, Ink).copy(textAlign = TextAlign.Center)
private val TitleStyle = tripText(23f, 27f, FontWeight.ExtraBold, Color.White, 1.5f)
private val DatesStyle = tripText(10f, 12f, FontWeight.Bold, Color.White, 1f)
private val LengthStyle = tripText(13f, 14f, FontWeight.ExtraBold, Color.White)
private val LengthUnitStyle = tripText(5.5f, 7f, FontWeight.ExtraBold, Color.White, 0.5f)
private val ActivitiesStyle = tripText(10f, 13f, FontWeight.ExtraBold, Color.White, 1.5f)
private val DayLabelStyle = tripText(7.5f, 10f, FontWeight.ExtraBold, Ink, 0.8f)
private val DayDateStyle = tripText(17f, 22f, FontWeight.Bold, Ink)
private val DayActivityStyle = tripText(10f, 13f, FontWeight.SemiBold, Ink)
private val DayFactStyle = tripText(9f, 12f, FontWeight.Bold, Ink)

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

/**
 * How far day card number [order] has come up, [stage] seconds after the trip was opened: a
 * spring of mass 1 released at rest, so it runs a little past its place and settles.
 */
private fun dayAt(stage: Float, order: Int, reduced: Boolean): Float {
    val t = stage - DAY_AT - order * DAY_GAP_SECONDS
    if (t <= 0f) return 0f
    if (reduced) return min(1f, t / 0.2f)
    val decay = DAY_DAMPING / 2f
    val omega = sqrt(DAY_STIFFNESS - decay * decay)
    return 1f - exp(-decay * t) * (cos(omega * t) + decay / omega * sin(omega * t))
}

/** The animated numbers, and where the row is heading. */
private class TripMotion(private val scope: CoroutineScope) {
    /** Index of the card at the left of the row; fractional while the row moves. */
    val pos = mutableFloatStateOf(0f)
    val open = mutableFloatStateOf(0f)

    /** Seconds since the trip was opened: the clock the day cards arrive on. */
    val stage = mutableFloatStateOf(0f)
    val openIndex = mutableIntStateOf(0)
    val isOpen = mutableStateOf(false)
    var target = 0
    var pressed = false
    var dragFrom = 0
    var dragged = 0f
    private var sliding: Job? = null
    private var growing: Job? = null
    private var staging: Job? = null

    fun hold() {
        sliding?.cancel()
    }

    fun slide(to: Int, reduced: Boolean, velocity: Float = 0f) {
        target = to
        sliding?.cancel()
        if (reduced) {
            pos.floatValue = to.toFloat()
            return
        }
        sliding = scope.launch {
            animate(
                pos.floatValue, to.toFloat(), velocity,
                spring(SLIDE_DAMPING / (2f * sqrt(SLIDE_STIFFNESS)), SLIDE_STIFFNESS),
            ) { value, _ -> pos.floatValue = value }
        }
    }

    private fun grow(to: Float, reduced: Boolean, stageTo: Float, stageSeconds: Float) {
        val spec: AnimationSpec<Float> = when {
            reduced -> tween(REDUCED_MS, easing = EaseOutStrong)
            else -> spring(OPEN_DAMPING / (2f * sqrt(OPEN_STIFFNESS)), OPEN_STIFFNESS)
        }
        growing?.cancel()
        growing = scope.launch { animate(open.floatValue, to, 0f, spec) { value, _ -> open.floatValue = value } }
        staging?.cancel()
        staging = scope.launch {
            animate(
                stage.floatValue, stageTo,
                animationSpec = tween((stageSeconds * 1000).toInt(), easing = LinearEasing),
            ) { value, _ -> stage.floatValue = value }
        }
    }

    fun openCard(index: Int, reduced: Boolean) {
        openIndex.intValue = index
        isOpen.value = true
        grow(1f, reduced, STAGE_SECONDS, STAGE_SECONDS - stage.floatValue)
    }

    fun close(reduced: Boolean) {
        isOpen.value = false
        grow(0f, reduced, 0f, STAGE_LEAVE)
    }
}

/**
 * A white panel with a row of tall photo cards that snaps card by card; a notch in the line under
 * the row and the arrival details follow the current card. Tapping that card grows it from its
 * place until the photo fills the panel: its title moves to the top left, the rest fades out and a
 * row of white day cards springs up from the bottom. A close button brings everything back.
 *
 * @param autoPlay Plays the interaction by itself: next trip, back, open, close.
 * @param onSelect Called with the index of the trip the row snaps to.
 * @param onOpen Called with the index of the trip that was opened.
 * @param onClose Called with the index of the trip that was closed.
 */
@Composable
fun ExpandingTripCard(
    trips: List<ExpandingTripCardTrip> = ExpandingTripCardTrips,
    autoPlay: Boolean = false,
    heading: String = "TRIPS",
    activitiesLabel: String = "ACTIVITIES",
    tabLabel: String = "Trips",
    onSelect: ((index: Int) -> Unit)? = null,
    onOpen: ((index: Int) -> Unit)? = null,
    onClose: ((index: Int) -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val count = trips.size
    if (count == 0) return
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val motion = remember { TripMotion(scope) }
    // Counts touches: a change restarts the autoPlay loop after a pause.
    var touches by remember { mutableIntStateOf(0) }
    val select by rememberUpdatedState(onSelect)
    val opened by rememberUpdatedState(onOpen)
    val panelShadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.16f), sigma = 24f) }
    val line = remember { Path() }

    LaunchedEffect(auto, touches, count) {
        if (!auto || count < 2) return@LaunchedEffect
        if (touches > 0) {
            // After a touch the loop waits, returns to the first card and starts over.
            delay((IDLE * 1000).toLong())
            if (motion.pressed) return@LaunchedEffect
            if (motion.isOpen.value) motion.close(reduced)
            motion.slide(0, reduced)
            delay((SETTLE * 1000).toLong())
        }
        while (true) {
            delay((NEXT_AT * 1000).toLong())
            motion.slide(1, reduced)
            delay(((BACK_AT - NEXT_AT) * 1000).toLong())
            motion.slide(0, reduced)
            delay(((OPEN_AT - BACK_AT) * 1000).toLong())
            motion.openCard(0, reduced)
            delay(((CLOSE_AT - OPEN_AT) * 1000).toLong())
            motion.close(reduced)
            delay(((LOOP - CLOSE_AT) * 1000).toLong())
        }
    }

    val pos = motion.pos
    val open = motion.open
    val stage = motion.stage
    val openIndex = motion.openIndex.intValue
    val isOpen = motion.isOpen.value

    fun rubber(value: Float): Float {
        val last = count - 1f
        return when {
            value < 0f -> value * RESISTANCE
            value > last -> last + (value - last) * RESISTANCE
            else -> value
        }
    }

    val drag = rememberDraggableState { delta ->
        motion.dragged += delta
        pos.floatValue = rubber(motion.dragFrom - motion.dragged / (STEP * density))
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .drawBehind {
                surface(0.dp) { _, _, w, h -> drawIntoCanvas { panelShadow.draw(it.nativeCanvas, 0f, 16f, w, h, RADIUS) } }
            }
            .clip(RoundedCornerShape(RADIUS.dp))
            .background(Color.White),
    ) {
        // What is behind the open card fades over the first half of its growth.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - min(1f, open.floatValue.coerceIn(0f, 1f) * 2f) }) {
            Box(
                Modifier
                    .offset((WIDTH - 22f - 36f).dp, 22.dp)
                    .size(36.dp)
                    .background(Mint, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { onSearch?.invoke() },
                    )
                    .semantics { contentDescription = "Search" },
                contentAlignment = Alignment.Center,
            ) {
                TripIcon(SEARCH_ICON, 20f, Color.White, 1.7f)
            }
            BasicText(heading, style = HeadingStyle, maxLines = 1, modifier = Modifier.offset(CARD_LEFT.dp, 76.dp))

            // A hairline across the panel with a bump under the card that is current; the bump
            // shrinks as its card leaves and the next one's grows.
            Canvas(Modifier.fillMaxSize()) {
                surface(0.dp) { _, _, _, _ ->
                    line.rewind()
                    line.moveTo(0f, LINE_Y)
                    for (i in 0 until count) {
                        val height = NOTCH_HEIGHT * max(0f, 1f - abs(i - pos.floatValue))
                        val x = CARD_LEFT + NOTCH_X + (i - pos.floatValue) * STEP
                        if (height <= 0.01f || x <= 16f || x >= WIDTH - 16f) continue
                        line.lineTo(x - 16f, LINE_Y)
                        line.cubicTo(x - 8f, LINE_Y, x - 6f, LINE_Y - height, x, LINE_Y - height)
                        line.cubicTo(x + 6f, LINE_Y - height, x + 8f, LINE_Y, x + 16f, LINE_Y)
                    }
                    line.lineTo(WIDTH, LINE_Y)
                    drawPath(line, Line, style = Stroke(1.2f))
                }
            }

            trips.forEachIndexed { i, trip ->
                Row(
                    Modifier
                        .offset(CARD_LEFT.dp, ARRIVAL_TOP.dp)
                        .size((WIDTH - CARD_LEFT * 2f).dp, 42.dp)
                        .graphicsLayer {
                            alpha = max(0f, 1f - abs(i - pos.floatValue) * 1.6f)
                            translationX = ((i - pos.floatValue) * ARRIVAL_SHIFT).dp.toPx()
                        },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).background(PinkPale, CircleShape), contentAlignment = Alignment.Center) {
                        TripIcon(ARRIVAL_ICON, 20f, Pink, 1.6f)
                    }
                    Column {
                        BasicText(trip.arrivalLabel, style = ArrivalLabelStyle, maxLines = 1)
                        BasicText(trip.arrivalPlace, style = ArrivalPlaceStyle, maxLines = 1)
                        BasicText(trip.arrivalTime, style = ArrivalTimeStyle, maxLines = 1)
                    }
                }
            }

            Row(
                Modifier.offset((68f - 11f).dp, (TAB_Y - 11f).dp).size((204f + 22f).dp, 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TripIcon(TRIPS_ICON, 22f, Ink, 1.5f)
                TripIcon(SAVED_ICON, 22f, Color(0xFFB9BCC2), 1.5f)
                TripIcon(ALERTS_ICON, 22f, Color(0xFFB9BCC2), 1.5f)
            }
            BasicText(
                tabLabel,
                style = TabStyle,
                maxLines = 1,
                modifier = Modifier.offset((68f - 30f).dp, (TAB_Y + 20f - 6f).dp).width(60.dp),
            )
        }

        // The row: a horizontal drag moves it, a tap opens the current card or brings a neighbour forward.
        Box(
            Modifier
                .offset(0.dp, CARD_TOP.dp)
                .size(WIDTH.dp, CARD_HEIGHT.dp)
                .semantics { contentDescription = "${trips[0].title.replace('\n', ' ')}, ${trips[0].dates}" }
                .pointerInput(count, isOpen) {
                    if (isOpen) return@pointerInput
                    detectTapGestures { at ->
                        // The card under the finger: the current one opens, a neighbour comes forward.
                        val index = floor((at.x / density - CARD_LEFT) / STEP + motion.target).toInt()
                        if (index == motion.target) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            motion.openCard(index, reduced)
                            opened?.invoke(index)
                        } else if (index in 0 until count) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            motion.slide(index, reduced)
                            select?.invoke(index)
                        }
                        touches++
                    }
                }
                .draggable(
                    state = drag,
                    enabled = !isOpen,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        motion.pressed = true
                        motion.hold()
                        motion.dragFrom = motion.target
                        motion.dragged = 0f
                        touches++
                    },
                    onDragStopped = { velocityPx ->
                        motion.pressed = false
                        // Pixels per second to cards per second.
                        val velocity = -velocityPx / (STEP * density)
                        // A swipe moves one card at most, however far it is thrown.
                        val thrown = (motion.dragFrom - motion.dragged / (STEP * density) + velocity * THROW).roundToInt()
                        val target = thrown.coerceIn(motion.dragFrom - 1, motion.dragFrom + 1).coerceIn(0, count - 1)
                        motion.slide(target, reduced, velocity)
                        if (target != motion.dragFrom) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            select?.invoke(target)
                        }
                        touches++
                    },
                ),
        )

        trips.forEachIndexed { index, trip ->
            val isOpened = index == openIndex
            // 0 for a card in the row, 1 for the photo filling the panel. Under reduced motion the
            // card jumps to the panel's size halfway through the fade.
            fun grown() = when {
                !isOpened -> 0f
                reduced -> if (open.floatValue > 0.5f) 1f else 0f
                else -> open.floatValue
            }
            Box(
                Modifier
                    .zIndex(if (isOpened) 1f else 0f)
                    .layout { measurable, _ ->
                        val g = grown()
                        val width = (CARD_WIDTH + (WIDTH - CARD_WIDTH) * g).dp.roundToPx()
                        val height = (CARD_HEIGHT + (HEIGHT - CARD_HEIGHT) * g).dp.roundToPx()
                        val left = CARD_LEFT + (index - pos.floatValue) * STEP
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) {
                            placeable.place((left * (1f - g)).dp.roundToPx(), (CARD_TOP * (1f - g)).dp.roundToPx())
                        }
                    }
                    .graphicsLayer {
                        alpha = if (isOpened) 1f else 1f - min(1f, open.floatValue.coerceIn(0f, 1f) * 2f)
                        shape = RoundedCornerShape((CARD_RADIUS + (RADIUS - CARD_RADIUS) * grown().coerceIn(0f, 1f)).dp)
                        clip = true
                    }
                    .background(trip.tint),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current).data(trip.photo).crossfade(200).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Brush.verticalGradient(listOf(rgba(0, 0, 0, 0.34f), rgba(0, 0, 0, 0f)))),
                )
                Column(
                    Modifier.graphicsLayer {
                        val g = grown()
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = (TITLE_INSET.x + (OPEN_TITLE.x - TITLE_INSET.x) * g).dp.toPx()
                        translationY = (TITLE_INSET.y + (OPEN_TITLE.y - TITLE_INSET.y) * g).dp.toPx()
                        scaleX = 1f + (OPEN_TITLE_SCALE - 1f) * g
                        scaleY = scaleX
                    },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(trip.title, style = TitleStyle, softWrap = false)
                    BasicText(trip.dates, style = DatesStyle, maxLines = 1)
                }
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 14.dp)
                        .graphicsLayer { alpha = 1f - min(1f, max(0f, grown()) * 2.5f) },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(38.dp).background(Blue, CircleShape), contentAlignment = Alignment.Center) {
                        TripIcon(BOOT_ICON, 18f, Color.White, 1.4f)
                    }
                    Column(
                        Modifier.size(38.dp).background(Pink, CircleShape),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BasicText(trip.length, style = LengthStyle, maxLines = 1)
                        BasicText(trip.lengthUnit, style = LengthUnitStyle, maxLines = 1)
                    }
                }

                if (isOpened) {
                    BasicText(
                        activitiesLabel,
                        style = ActivitiesStyle,
                        maxLines = 1,
                        modifier = Modifier
                            .offset(DAY_LEFT.dp, ACTIVITIES_Y.dp)
                            .graphicsLayer { alpha = ((stage.floatValue - 0.2f) / 0.25f).coerceIn(0f, 1f) },
                    )
                    trip.days.forEachIndexed { order, day ->
                        TripDay(
                            day,
                            Modifier
                                .offset((DAY_LEFT + order * (DAY_WIDTH + DAY_GAP)).dp, DAY_TOP.dp)
                                .graphicsLayer {
                                    val e = dayAt(stage.floatValue, order, reduced)
                                    alpha = (e * 2.5f).coerceIn(0f, 1f)
                                    if (!reduced) translationY = ((1f - e) * DAY_RISE).dp.toPx()
                                },
                        )
                    }
                    Box(
                        Modifier
                            .offset(20.dp, 22.dp)
                            .size(36.dp)
                            .graphicsLayer { alpha = (open.floatValue.coerceIn(0f, 1f) * 2f - 1f).coerceIn(0f, 1f) }
                            .background(rgba(20, 24, 28, 0.36f), CircleShape)
                            .clickable(
                                enabled = isOpen,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    motion.close(reduced)
                                    touches++
                                    onClose?.invoke(openIndex)
                                },
                            )
                            .semantics { contentDescription = "Close" },
                        contentAlignment = Alignment.Center,
                    ) {
                        TripIcon(BACK_ICON, 20f, Color.White, 1.8f)
                    }
                }
            }
        }
    }
}

@Composable
private fun TripDay(day: ExpandingTripCardDay, modifier: Modifier) {
    val water = remember { svgPath(MAP_WATER) }
    val route = remember { svgPath(MAP_ROUTE) }
    val pin = remember { svgPath(MAP_PIN) }
    val dot = remember { svgPath(MAP_DOT) }
    Box(modifier.size(DAY_WIDTH.dp, DAY_HEIGHT.dp).background(Color.White, RoundedCornerShape(CARD_RADIUS.dp))) {
        BasicText(day.label, style = DayLabelStyle, maxLines = 1, modifier = Modifier.offset(12.dp, 12.dp))
        BasicText(day.date, style = DayDateStyle, maxLines = 1, modifier = Modifier.offset(12.dp, 25.dp))
        BasicText(day.activity, style = DayActivityStyle, maxLines = 1, modifier = Modifier.offset(12.dp, 62.dp))
        Box(
            Modifier
                .offset(12.dp, 82.dp)
                .size(MAP_WIDTH.dp, MAP_HEIGHT.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFCFE8C6))
                .drawBehind {
                    surface(0.dp) { _, _, _, _ ->
                        drawPath(water, Color(0xFFB9DEF0))
                        drawPath(route, Color.White, style = Stroke(2f, cap = StrokeCap.Round))
                        drawPath(pin, Color(0xFF1C1F24))
                        drawPath(dot, Color.White)
                    }
                },
        )
        Row(
            Modifier.offset(12.dp, 148.dp).size(MAP_WIDTH.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(day.duration, style = DayFactStyle, maxLines = 1)
            BasicText(day.distance, style = DayFactStyle, maxLines = 1)
        }
    }
}

/** An icon stroked from an SVG path in a square of [size] dp. */
@Composable
private fun TripIcon(svg: String, size: Float, color: Color, width: Float) {
    val path = remember(svg) { svgPath(svg) }
    Box(
        Modifier.size(size.dp).drawBehind {
            surface(0.dp) { _, _, _, _ ->
                drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        },
    )
}

private fun photoUrl(id: String) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=900&q=80"

val ExpandingTripCardTrips = listOf(
    ExpandingTripCardTrip(
        title = "SEILAND\nNORWAY", dates = "3 AUG - 9 AUG", length = "6",
        arrivalPlace = "OSLO LUFTHAVN", arrivalTime = "3 Aug, 12:00 PM",
        photo = photoUrl("1464822759023-fed622ff2c3b"), tint = Color(0xFF6F8AA0),
        days = listOf(
            ExpandingTripCardDay("DAY 1", "3 Aug.", "Hiking", "8 hours", "22 km"),
            ExpandingTripCardDay("DAY 2", "4 Aug.", "Kayaking", "5 hours", "14 km"),
            ExpandingTripCardDay("DAY 3", "5 Aug.", "Fishing", "4 hours", "6 km"),
        ),
    ),
    ExpandingTripCardTrip(
        title = "MALLORCA\nSPAIN", dates = "18 AUG - 25 AUG", length = "7",
        arrivalPlace = "PALMA DE MALLORCA", arrivalTime = "18 Aug, 9:40 AM",
        photo = photoUrl("1506929562872-bb421503ef21"), tint = Color(0xFF3FB3B8),
        days = listOf(
            ExpandingTripCardDay("DAY 1", "18 Aug.", "Sailing", "6 hours", "30 km"),
            ExpandingTripCardDay("DAY 2", "19 Aug.", "Snorkelling", "3 hours", "2 km"),
            ExpandingTripCardDay("DAY 3", "20 Aug.", "Cycling", "5 hours", "48 km"),
        ),
    ),
    ExpandingTripCardTrip(
        title = "DOLOMITES\nITALY", dates = "2 SEP - 8 SEP", length = "6",
        arrivalPlace = "VENICE MARCO POLO", arrivalTime = "2 Sep, 2:15 PM",
        photo = photoUrl("1501785888041-af3ef285b470"), tint = Color(0xFF4AA39C),
        days = listOf(
            ExpandingTripCardDay("DAY 1", "2 Sep.", "Rowing", "2 hours", "4 km"),
            ExpandingTripCardDay("DAY 2", "3 Sep.", "Hiking", "7 hours", "18 km"),
            ExpandingTripCardDay("DAY 3", "4 Sep.", "Climbing", "6 hours", "9 km"),
        ),
    ),
)
