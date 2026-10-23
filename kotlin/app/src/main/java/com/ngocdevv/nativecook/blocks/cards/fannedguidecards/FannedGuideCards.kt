package com.ngocdevv.nativecook.blocks.cards.fannedguidecards

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * @param photo Address of the photo.
 * @param tint Flat colour shown until the photo arrives.
 */
data class GuidePlace(val name: String, val location: String, val photo: String, val tint: Color)

// The box the fan is laid out in; the side cards reach a little beyond it.
private const val WIDTH = 350f
private const val HEIGHT = 330f

private const val CARD_WIDTH = 200f
private const val CARD_HEIGHT = 300f
private const val RADIUS = 20f

// The white frame around the photo.
private const val FRAME = 8f
private const val PHOTO_WIDTH = 184f
private const val PHOTO_HEIGHT = 216f
private const val PHOTO_RADIUS = 14f
private const val NAME_HEIGHT = 34f
private const val BUTTON_HEIGHT = 34f

// The round badge in the photo's top right corner: its radius, its inset from the card's edges,
// the gap the photo leaves around it and the radius that rounds the cut's shoulders.
private const val BADGE = 16f
private const val BADGE_INSET = 6f
private const val BADGE_GAP = 4f
private const val SHOULDER = 5f

// The shadows' canvas overflows the card by this much on every side.
private val BLEED = 72.dp

/**
 * @param angle Degrees.
 * @param front 1 in front, 0 at the sides.
 */
private class Pose(val x: Float, val y: Float, val angle: Float, val scale: Float, val front: Float)

// The left, front and right slot.
private val Slots = listOf(
    Pose(-100f, -26f, -8f, 0.7f, 0f),
    Pose(0f, 6f, -3f, 0.9f, 1f),
    Pose(100f, 12f, 12f, 0.7f, 0f),
)

// The spring every card moves on (mass 1), and when it counts as settled.
private const val STIFFNESS = 180f
private const val DAMPING = 16f
private const val SETTLED = 1.4f

// In a swap the card that leaves the front starts this long after the one that comes in, and the
// third card is nudged this long after that.
private const val SWAP_STAGGER = 0.04f

// How far the third card leans away from a swap, in dp and degrees.
private const val KICK_X = 9f
private const val KICK_ANGLE = 4f

// Dragging the front card: it follows the finger, a third as much vertically, tilts with the
// distance, and goes to the side once released this far out.
private const val DRAG_Y = 0.3f
private const val TILT_PER_POINT = 0.07f
private const val TILT_MAX = 12f
private const val DRAG_THROW = 70f

// On appear the cards deal out from one pile.
private val DEAL_AT = floatArrayOf(0.35f, 0.43f, 0.51f)
private const val PILE_SCALE = 0.8f
private val PILE_ANGLES = floatArrayOf(-7f, 0f, 5f)

// autoPlay loop (seconds): taps on the right, left, left and right card bring every card to the
// front once and leave the fan as it started.
private val TAPS_AT = floatArrayOf(1.5f, 3.7f, 5.9f, 8.1f)
private val TAP_SIDES = intArrayOf(2, 0, 0, 2)
private const val LOOP = 8.8f

// After a touch the loop waits this long before it starts again from rest.
private const val USER_HOLD = 2f

private const val GRID_SIZE = 500f
private const val GRID_PITCH = 24f

/** A spring let go at 0 and heading for 1, [t] seconds on (zero before it starts). */
private fun springStep(t: Float): Float {
    if (t <= 0f) return 0f
    if (t >= SETTLED) return 1f
    val decay = DAMPING / 2f
    val pulse = sqrt(STIFFNESS - decay * decay)
    return 1f - exp(-decay * t) * (cos(pulse * t) + decay / pulse * sin(pulse * t))
}

/** The same spring struck at rest: it swings out and back to 0. */
private fun springKick(t: Float): Float {
    if (t <= 0f || t >= SETTLED) return 0f
    val decay = DAMPING / 2f
    val pulse = sqrt(STIFFNESS - decay * decay)
    return exp(-decay * t) * sin(pulse * t)
}

/**
 * @param slot 0 left, 1 front, 2 right.
 * @param at When it set off for its slot.
 * @param kick A nudge away from a swap it took no part in: its direction, and [kickAt] when it began.
 */
private class GuideCard(val slot: Int, val from: Pose, val at: Float, val kick: Float = 0f, val kickAt: Float = 0f)

/**
 * Where the three cards are and how they got there. A card's place on screen is a function of
 * time: it springs from the pose it had when it last changed slot to the pose of its slot.
 */
private class Deck(dealtAt: Float?) {
    /** The fan at rest, or, with [dealtAt], a pile that deals itself out from that moment. */
    val cards = Array(3) { index ->
        val slot = Slots[index]
        if (dealtAt == null) {
            GuideCard(index, slot, -10f)
        } else {
            GuideCard(index, Pose(0f, Slots[1].y, PILE_ANGLES[index], PILE_SCALE, slot.front), dealtAt + DEAL_AT[index])
        }
    }

    /** The card under the finger (or -1) and how far it has been dragged, in dp. */
    var dragCard = -1
    var dragX = 0f
    var dragY = 0f

    val front get() = cards.indexOfFirst { it.slot == 1 }

    fun pose(index: Int, time: Float, calm: Boolean): Pose {
        val card = cards[index]
        val to = Slots[card.slot]
        // Reduced motion: a card jumps to its slot.
        val p = if (calm) (if (time >= card.at) 1f else 0f) else springStep(time - card.at)
        val nudge = if (calm) 0f else card.kick * springKick(time - card.kickAt)
        val dragged = dragCard == index
        return Pose(
            x = card.from.x + (to.x - card.from.x) * p + KICK_X * nudge + if (dragged) dragX else 0f,
            y = card.from.y + (to.y - card.from.y) * p + if (dragged) dragY * DRAG_Y else 0f,
            angle = card.from.angle + (to.angle - card.from.angle) * p + KICK_ANGLE * nudge +
                if (dragged) (dragX * TILT_PER_POINT).coerceIn(-TILT_MAX, TILT_MAX) else 0f,
            scale = card.from.scale + (to.scale - card.from.scale) * p,
            front = card.from.front + (to.front - card.from.front) * p,
        )
    }

    /**
     * The card at [side] (0 left, 2 right) comes to the front, the front card takes its slot and
     * the third card leans away for a moment.
     */
    fun swap(side: Int, time: Float, calm: Boolean) {
        val coming = cards.indexOfFirst { it.slot == side }
        val leaving = front
        if (coming < 0 || leaving < 0) return
        val third = 3 - coming - leaving
        val arriving = GuideCard(1, pose(coming, time, calm), time)
        val departing = GuideCard(side, pose(leaving, time, calm), time + SWAP_STAGGER)
        cards[coming] = arriving
        cards[leaving] = departing
        cards[third] = cards[third].let {
            GuideCard(it.slot, it.from, it.at, kick = if (side == 2) -1f else 1f, kickAt = time + SWAP_STAGGER * 2f)
        }
        dragCard = -1
    }

    /** The dragged card is let go and springs back to its slot. */
    fun settle(time: Float, calm: Boolean) {
        val index = dragCard
        if (index < 0) return
        cards[index] = GuideCard(cards[index].slot, pose(index, time, calm), time)
        dragCard = -1
    }
}

/** Per-frame state: the loop's clock and the deck it taps. */
private class FanSim(calm: Boolean) {
    var clock = 0f
    var looping = false
    var touching = false

    /** Seconds the loop still waits after a touch. */
    var hold = 0f
    val deck = Deck(if (calm) null else 0f)

    // Written last in a frame: reading it is what moves the cards.
    val time = mutableFloatStateOf(0f)

    fun advance(dt: Float, calm: Boolean) {
        val now = time.floatValue + dt
        if (touching) {
            hold = USER_HOLD
        } else if (hold > 0f) {
            hold -= dt
            // Time is up: the loop starts again from rest.
            if (hold <= 0f && looping) clock = 0f
        } else if (looping) {
            var t = clock + dt
            if (t >= LOOP) t -= LOOP
            for (tap in TAPS_AT.indices) {
                if (clock < TAPS_AT[tap] && t >= TAPS_AT[tap]) deck.swap(TAP_SIDES[tap], now, calm)
            }
            clock = t
        }
        time.floatValue = now
    }
}

/** The photo's outline: a rounded rectangle whose top right corner curves in around the badge. */
private object PhotoShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val unit = density.density
        val w = size.width
        val h = size.height
        val r = PHOTO_RADIUS * unit
        // The badge's centre in the photo, the radius of the cut around it and of its shoulders.
        val cx = w + (FRAME - BADGE_INSET - BADGE) * unit
        val cy = (BADGE_INSET + BADGE - FRAME) * unit
        val cut = (BADGE + BADGE_GAP) * unit
        val f = SHOULDER * unit
        val reach = sqrt((cut + f) * (cut + f) - (cy - f) * (cy - f))
        val lean = Math.toDegrees(atan2(cy - f, reach).toDouble()).toFloat()
        fun circle(x: Float, y: Float, radius: Float) = Rect(Offset(x, y), radius)

        return Outline.Generic(
            Path().apply {
                moveTo(r, 0f)
                arcTo(circle(cx - reach, f, f), -90f, 90f + lean, false)
                arcTo(circle(cx, cy, cut), 180f + lean, -(90f + lean * 2f), false)
                arcTo(circle(w - f, cy + reach, f), 270f - lean, 90f + lean, false)
                arcTo(circle(w - r, h - r, r), 0f, 90f, false)
                arcTo(circle(r, h - r, r), 90f, 90f, false)
                arcTo(circle(r, r, r), 180f, 90f, false)
                close()
            },
        )
    }
}

// The arrow pointing up and to the right, in a 12-point box.
private val Arrow = Path().apply {
    moveTo(2f, 10f); lineTo(10f, 2f)
    moveTo(3.5f, 2f); lineTo(10f, 2f); lineTo(10f, 8.5f)
}

private val GridLines = Path().apply {
    var offset = -GRID_SIZE
    while (offset <= GRID_SIZE) {
        moveTo(offset, 0f); lineTo(offset + GRID_SIZE, GRID_SIZE)
        moveTo(offset + GRID_SIZE, 0f); lineTo(offset, GRID_SIZE)
        offset += GRID_PITCH
    }
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun guideText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TagStyle = guideText(9.5f, 12f, FontWeight.Medium, Color.White)
private val NameStyle = guideText(15f, 19f, FontWeight.Normal, Color(0xFF1C1C1C), -0.3f)
private val LocationStyle = guideText(10.5f, 14f, FontWeight.Normal, Color(0xFF8A8A8A))
private val ButtonStyle = guideText(12.5f, 16f, FontWeight.SemiBold, Color.White)
private val ButtonIdleStyle = ButtonStyle.copy(color = Color(0xFFA3A3A3))

/**
 * Three white photo cards fanned out, the middle one in front. Tapping a side card brings it to
 * the front on a spring while the front card takes its place and the third leans away; dragging the
 * front card sideways tilts it and, far enough, sends it to that side. The black "Book Now" pill
 * belongs to whichever card is in front.
 *
 * @param autoPlay Taps the side cards in turn, in a loop that ends where it began.
 * @param places Three places: the left, the front and the right card.
 * @param grid Draws the faint diagonal grid behind the cards.
 * @param onSelect Called with the place's index when a tap or a drag brings its card to the front.
 * @param onBook Called with the place's index when the pill of the front card is tapped.
 */
@Composable
fun FannedGuideCards(
    autoPlay: Boolean = false,
    places: List<GuidePlace> = listOf(
        GuidePlace(
            "Great Pyramid", "Giza, Egypt",
            "https://images.unsplash.com/photo-1503177119275-0aa32b3a9368?auto=format&fit=crop&w=400&h=470&q=80", Color(0xFFB5A794),
        ),
        GuidePlace(
            "Cappadocia", "Göreme, Turkey",
            "https://images.unsplash.com/photo-1641128324972-af3212f0f6bd?auto=format&fit=crop&w=400&h=470&q=80", Color(0xFF7E8681),
        ),
        GuidePlace(
            "Taj Mahal", "Agra, India",
            "https://images.unsplash.com/photo-1524492412937-b28074a5d7da?auto=format&fit=crop&w=400&h=470&q=80", Color(0xFF969FAF),
        ),
    ),
    tag: String = "Top rated",
    action: String = "Book Now",
    grid: Boolean = true,
    onSelect: ((index: Int) -> Unit)? = null,
    onBook: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val calm = rememberReducedMotion()
    val auto = autoPlay && !calm
    val view = LocalView.current
    val selected by rememberUpdatedState(onSelect)

    val sim = remember { FanSim(calm) }

    LaunchedEffect(auto) {
        sim.looping = auto
        sim.clock = 0f
        sim.hold = 0f
    }

    LaunchedEffect(calm) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            sim.advance(min((frame - last) / 1e9f, 0.064f), calm)
            last = frame
        }
    }

    fun bring(side: Int) {
        val next = sim.deck.cards.indexOfFirst { it.slot == side }
        sim.hold = USER_HOLD
        sim.deck.swap(side, sim.time.floatValue, calm)
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (next >= 0) selected?.invoke(next)
    }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp), contentAlignment = Alignment.Center) {
        if (grid) {
            // A diagonal grid that fades out towards its edges, so it needs no frame.
            Canvas(Modifier.requiredSize(GRID_SIZE.dp)) {
                scale(density, density, pivot = Offset.Zero) {
                    drawPath(
                        GridLines,
                        Brush.radialGradient(
                            listOf(rgba(125, 138, 130, 0.34f), rgba(125, 138, 130, 0f)),
                            center = Offset(GRID_SIZE / 2f, GRID_SIZE / 2f),
                            radius = GRID_SIZE / 2f,
                        ),
                        style = Stroke(1f),
                    )
                }
            }
        }

        places.take(3).forEachIndexed { index, place ->
            // The card that is more in front lies on top, so two cards trade places as they pass.
            val rank by remember(index, calm) {
                derivedStateOf { (sim.deck.pose(index, sim.time.floatValue, calm).front.coerceIn(0f, 1f) * 100f).roundToInt() + index }
            }
            val isFront by remember(index) {
                derivedStateOf {
                    sim.time.floatValue
                    sim.deck.front == index
                }
            }

            Box(
                Modifier
                    .zIndex(rank.toFloat())
                    .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
                    .graphicsLayer {
                        val pose = sim.deck.pose(index, sim.time.floatValue, calm)
                        translationX = pose.x.dp.toPx()
                        translationY = pose.y.dp.toPx()
                        rotationZ = pose.angle
                        scaleX = pose.scale
                        scaleY = pose.scale
                    }
                    .pointerInput(index) {
                        detectTapGestures {
                            val slot = sim.deck.cards[index].slot
                            if (slot != 1) bring(slot)
                        }
                    }
                    .pointerInput(index) {
                        // Travel is read in the card's own, scaled coordinates: bring it back to dp on screen.
                        var x = 0f
                        var y = 0f
                        val drop = {
                            if (sim.deck.dragCard == index) {
                                sim.touching = false
                                sim.hold = USER_HOLD
                                if (abs(x) > DRAG_THROW) bring(if (x > 0f) 2 else 0) else sim.deck.settle(sim.time.floatValue, calm)
                            }
                        }
                        detectDragGestures(
                            onDragStart = {
                                x = 0f
                                y = 0f
                            },
                            onDragEnd = drop,
                            onDragCancel = drop,
                        ) { change, amount ->
                            if (sim.deck.cards[index].slot != 1) return@detectDragGestures
                            change.consume()
                            x += amount.x * Slots[1].scale / density
                            y += amount.y * Slots[1].scale / density
                            sim.touching = true
                            sim.deck.dragCard = index
                            sim.deck.dragX = x
                            sim.deck.dragY = y
                        }
                    }
                    .semantics(mergeDescendants = true) { contentDescription = "${place.name}, ${place.location}. $tag" },
            ) {
                Sheet { sim.deck.pose(index, sim.time.floatValue, calm).front.coerceIn(0f, 1f) }

                Picture(
                    place,
                    Modifier.offset(FRAME.dp, FRAME.dp).size(PHOTO_WIDTH.dp, PHOTO_HEIGHT.dp).clip(PhotoShape),
                )

                Box(
                    Modifier
                        .offset((FRAME + 8f).dp, (FRAME + 8f).dp)
                        .height(20.dp)
                        .background(rgba(20, 20, 20, 0.55f), CircleShape)
                        .padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(tag, style = TagStyle, maxLines = 1)
                }

                Box(
                    Modifier
                        .offset((CARD_WIDTH - BADGE_INSET - BADGE * 2f).dp, BADGE_INSET.dp)
                        .size((BADGE * 2f).dp)
                        .background(Color(0xFF161616), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(12.dp)) {
                        scale(density, density, pivot = Offset.Zero) {
                            drawPath(Arrow, Color.White, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }

                Row(
                    Modifier.offset(14.dp, (FRAME + PHOTO_HEIGHT).dp).size((CARD_WIDTH - 28f).dp, NAME_HEIGHT.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(place.name, Modifier.weight(1f), NameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(place.location, style = LocationStyle, maxLines = 1)
                }

                BookButton(
                    label = action,
                    enabled = isFront,
                    front = { sim.deck.pose(index, sim.time.floatValue, calm).front.coerceIn(0f, 1f) },
                    onClick = { onBook?.invoke(index) },
                    modifier = Modifier.offset(FRAME.dp, (FRAME + PHOTO_HEIGHT + NAME_HEIGHT).dp),
                )
            }
        }
    }
}

/** The card's white sheet with its shadows; the front card casts a second, deeper one. */
@Composable
private fun Sheet(front: () -> Float) {
    val deep = remember { SoftRoundRect(rgba(28, 40, 34, 0.24f), sigma = 20f) }
    val low = remember { SoftRoundRect(rgba(28, 40, 34, 0.14f), sigma = 7f) }
    Box(Modifier.size(CARD_WIDTH.dp, CARD_HEIGHT.dp).bleed(BLEED)) {
        Canvas(Modifier.fillMaxSize()) {
            surface(BLEED) { x, y, w, h ->
                drawIntoCanvas {
                    deep.draw(it.nativeCanvas, x, y + 22f, w, h, RADIUS, alpha = front())
                    low.draw(it.nativeCanvas, x, y + 6f, w, h, RADIUS)
                }
                drawRoundRect(Color.White, Offset(x, y), Size(w, h), CornerRadius(RADIUS))
            }
        }
    }
}

/** A photo that fades in over its tint once it has loaded. */
@Composable
private fun Picture(place: GuidePlace, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(place.photo, context) { ImageRequest.Builder(context).data(place.photo).crossfade(200).build() }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(place.tint),
    )
}

/** A grey pill on the side cards; the black one fades onto the card that comes to the front. */
@Composable
private fun BookButton(label: String, enabled: Boolean, front: () -> Float, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    Box(
        modifier
            .size((CARD_WIDTH - FRAME * 2f).dp, BUTTON_HEIGHT.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(Color(0xFFEFEFEE), CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = ButtonIdleStyle, maxLines = 1)
        Box(
            Modifier.matchParentSize().graphicsLayer { alpha = front() }.background(Color(0xFF1B1B1B), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(label, style = ButtonStyle, maxLines = 1)
        }
    }
}
