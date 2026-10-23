package com.ngocdevv.nativecook.blocks.cards.destinationswipestack

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.drop

enum class DestinationIcon { Snow, Sun, Hills, Sail }

/**
 * @param subtitle Second line of the title, in grey.
 * @param tag The small pill on the photo.
 * @param tint Flat colour shown until the photo arrives.
 * @param glow Colour of the glow behind the stack while this card is in front.
 */
data class Destination(
    val title: String,
    val subtitle: String,
    val caption: String,
    val tag: String,
    val photo: String,
    val tint: Color,
    val glow: Color,
    val icon: DestinationIcon,
)

private const val WIDTH = 340f
private const val HEIGHT = 615f

// Centre of the stack, measured from the top of the component.
private const val CENTER_Y = 270f
private const val CARD_WIDTH = 236f
private const val CARD_HEIGHT = 342f
private const val CARD_RADIUS = 20f
private const val ICON_SIZE = 18f

// The cards may reach this far past the sides and the top before they are cut.
private const val BLEED = 40f

// Height of the strip at the bottom where the dropped card fades into the canvas.
private const val FADE = 96f
private const val GLOW_RADIUS = 250f
private const val GLOW_STRETCH = 1.15f

// The glow's canvas overflows the component by this much on every side.
private val GLOW_BLEED = 100.dp

// A card's layer is this much larger than the card on every side, so its shadow and blur are not cut.
private const val CARD_BLEED = 64f

/** Where a card rests in a place of the deck: offset from the centre, angle in degrees, scale. */
private class Slot(val x: Float, val y: Float, val rotation: Float, val scale: Float, val ghost: Float)

private val GhostA = Slot(16f, -8f, 12f, 0.97f, 0.34f)
private val GhostB = Slot(-18f, 2f, -15f, 0.94f, 0.2f)
private const val DROP_X = 12f
private const val DROP_Y = 359f
private const val DROP_ROTATION = -7f
private const val DROP_SCALE = 0.84f
private const val DROP_ALPHA = 0.5f
private const val DROP_BLUR = 5f

// Seconds one swipe takes from the first tilt to the last ghost settling.
private const val SWIPE = 1.1f
private const val SWIPE_REDUCED = 0.35f

// autoPlay (seconds): the default deck of four goes round once per LOOP, the first swipe starting
// at FIRST and the next ones every STEP.
private const val LOOP = 8f
private const val FIRST = 0.8f
private const val STEP = LOOP / 4f

// After a touch the loop waits this long before it starts again.
private const val USER_HOLD = 2.5f

// The spring the arriving cards turn on.
private const val STIFFNESS = 160f
private const val DAMPING = 15f
private const val MASS = 1f

// The spring that brings a card back when it is let go too early.
private const val RETURN_STIFFNESS = 260f
private const val RETURN_DAMPING = 24f

// Share of the finger's travel the front card follows, sideways and down, and upwards.
private const val DRAG_GAIN = 0.55f
private const val DRAG_GAIN_UP = 0.2f

// Degrees of tilt per dp of sideways drag.
private const val DRAG_TILT = 0.06f

// Finger travel that sends the card away on release.
private const val COMMIT = 56f

private fun photo(id: String) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=600&h=460&q=80"

private val Destinations = listOf(
    Destination(
        title = "Ama Dablam",
        subtitle = "Himalaya",
        caption = "A sharp granite peak above the Khumbu valley, best seen at first light.",
        tag = "12 days · $2,490",
        photo = photo("1454496522488-7a8e488e8606"),
        tint = Color(0xFF8A9BB4),
        glow = Color(0xFFA9A4F5),
        icon = DestinationIcon.Snow,
    ),
    Destination(
        title = "Fire Valley",
        subtitle = "Nevada",
        caption = "Red sandstone walls and an empty road running straight into the heat.",
        tag = "4 days · $640",
        photo = photo("1500530855697-b586d89ba3ee"),
        tint = Color(0xFF9A5A44),
        glow = Color(0xFFF5A95C),
        icon = DestinationIcon.Sun,
    ),
    Destination(
        title = "Fairy Glen",
        subtitle = "Isle of Skye",
        caption = "Grassy cones and a spiral of stones in the hills above the village.",
        tag = "3 days · $380",
        photo = photo("1472214103451-9374bd1c798e"),
        tint = Color(0xFF5C8A3A),
        glow = Color(0xFF9FD67C),
        icon = DestinationIcon.Hills,
    ),
    Destination(
        title = "Blue Lagoon",
        subtitle = "Maldives",
        caption = "Water villas on a ring of coral, a short seaplane hop from the capital.",
        tag = "7 days · $3,150",
        photo = photo("1514282401047-d79a71a590e8"),
        tint = Color(0xFF3FB6C4),
        glow = Color(0xFF62D3D6),
        icon = DestinationIcon.Sail,
    ),
)

/** Polylines of an icon in a 20 × 20 box, as x, y pairs. */
private fun strokes(icon: DestinationIcon): List<FloatArray> = when (icon) {
    DestinationIcon.Snow -> listOf(
        floatArrayOf(10f, 2.5f, 10f, 17.5f),
        floatArrayOf(3.5f, 6.25f, 16.5f, 13.75f),
        floatArrayOf(3.5f, 13.75f, 16.5f, 6.25f),
        floatArrayOf(7.8f, 4.2f, 10f, 6.2f, 12.2f, 4.2f),
        floatArrayOf(13.92f, 5.19f, 13.29f, 8.1f, 16.12f, 9f),
        floatArrayOf(16.12f, 11f, 13.29f, 11.9f, 13.92f, 14.81f),
        floatArrayOf(12.2f, 15.8f, 10f, 13.8f, 7.8f, 15.8f),
        floatArrayOf(6.08f, 14.81f, 6.71f, 11.9f, 3.88f, 11f),
        floatArrayOf(3.88f, 9f, 6.71f, 8.1f, 6.08f, 5.19f),
    )
    DestinationIcon.Sun -> listOf(
        floatArrayOf(10f, 1.8f, 10f, 3.8f),
        floatArrayOf(10f, 16.2f, 10f, 18.2f),
        floatArrayOf(1.8f, 10f, 3.8f, 10f),
        floatArrayOf(16.2f, 10f, 18.2f, 10f),
        floatArrayOf(4.2f, 4.2f, 5.6f, 5.6f),
        floatArrayOf(15.8f, 4.2f, 14.4f, 5.6f),
        floatArrayOf(4.2f, 15.8f, 5.6f, 14.4f),
        floatArrayOf(15.8f, 15.8f, 14.4f, 14.4f),
    )
    DestinationIcon.Hills -> listOf(
        floatArrayOf(1.8f, 16.2f, 7.4f, 6.2f, 10.6f, 11.4f, 12.9f, 8.2f, 18.2f, 16.2f, 1.8f, 16.2f),
    )
    DestinationIcon.Sail -> listOf(
        floatArrayOf(10f, 2.6f, 10f, 12f),
        floatArrayOf(10f, 3.4f, 15.6f, 12f, 10f, 12f),
        floatArrayOf(8.4f, 6f, 4.6f, 12f, 8.4f, 12f, 8.4f, 6f),
        floatArrayOf(2.8f, 14.6f, 17.2f, 14.6f, 14.6f, 17.6f, 5.4f, 17.6f, 2.8f, 14.6f),
    )
}

/** Circles of an icon in the same box, as centre x, centre y, radius. */
private fun circles(icon: DestinationIcon): List<FloatArray> = when (icon) {
    DestinationIcon.Sun -> listOf(floatArrayOf(10f, 10f, 3.3f))
    DestinationIcon.Hills -> listOf(floatArrayOf(14.9f, 4.6f, 1.5f))
    else -> emptyList()
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun cardText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    textAlign = TextAlign.Center,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = cardText(25f, 26f, FontWeight.Light, Color.White, 0.3f)
private val SubtitleStyle = cardText(25f, 26f, FontWeight.Light, Color(0xFFB4B4C0), 0.3f)
private val TagStyle = cardText(10f, 13f, FontWeight.Medium, rgba(255, 255, 255, 0.92f))
private val CaptionStyle = cardText(10f, 13.5f, FontWeight.Normal, Color(0xFF8C8C98))

// Long titles shrink to 0.7 of their size before they are cut.
private val TitleFit = TextAutoSize.StepBased(minFontSize = 17.5.sp, maxFontSize = 25.sp, stepSize = 0.5.sp)

private class Pose {
    var x = 0f
    var y = 0f
    var rotation = 0f
    var scale = 1f

    /** Opacity of the whole card. */
    var alpha = 1f

    /** Opacity of the grey body a card shows while it waits behind. */
    var ghost = GhostA.ghost

    /** Opacity of the dark face with the photo and the texts. */
    var face = 0f
    var blur = 0f
    var z = 1f
}

/**
 * Per-frame state of the deck. [pos] counts the cards swiped away so far; its fraction is how far
 * the running swipe has come.
 */
private class Deck {
    val pos = mutableFloatStateOf(0f)
    var target = 0f

    /** True while the loop is the one moving the deck. */
    var live = false

    /** Swipes asked for by a finger that the next frame has to start. */
    var requests = 0

    /** Where the loop started counting from, and its clock. */
    var base = 0f
    var clock = 0f

    /** Seconds the loop still waits after a touch. */
    var hold = 0f
    var touching = false
    val dragX = mutableFloatStateOf(0f)
    val dragY = mutableFloatStateOf(0f)
    var velocityX = 0f
    var velocityY = 0f
}

private fun wrapIndex(index: Int, count: Int) = ((index % count) + count) % count

private fun segment(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOut(t: Float) = 1f - (1f - t).pow(3)

private fun easeInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (2f - 2f * t).pow(3) / 2f

private fun smooth(t: Float) = t * t * (3f - 2f * t)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Step response of the arriving cards' spring [seconds] after it is released: 0 → 1 with one overshoot. */
private fun spring(seconds: Float): Float {
    if (seconds <= 0f) return 0f
    val omega = sqrt(STIFFNESS / MASS)
    val zeta = DAMPING / (2f * sqrt(STIFFNESS * MASS))
    val damped = omega * sqrt(1f - zeta * zeta)
    return 1f - exp(-zeta * omega * seconds) * (cos(damped * seconds) + zeta * omega / damped * sin(damped * seconds))
}

/** The spring started [delay] into a swipe of which [part] (0…1) has passed, landing exactly on 1. */
private fun settle(part: Float, delay: Float) = mix(spring((part - delay) * SWIPE), 1f, smooth(segment(part, 0.85f, 1f)))

/** Cards swiped away [clock] seconds into the autoPlay loop. */
private fun script(clock: Float, count: Int): Float {
    var done = 0f
    for (index in 0 until count) {
        done += segment(clock, FIRST + STEP * index, FIRST + STEP * index + SWIPE)
    }
    return done
}

/**
 * Place of card [index] in the deck: 0 in front, 1 and 2 the ghosts behind, `count - 1` the dropped
 * card, and below 0 on its way down.
 */
private fun relative(index: Int, pos: Float, count: Int): Float {
    var place = (index - pos) % count
    if (place <= -1f) place += count
    if (place > count - 1f) place -= count
    return place
}

/** Share of the glow that belongs to the card at [place]. */
private fun glowWeight(place: Float) = smooth(max(0f, 1f - abs(place)))

private fun pose(at: Float, count: Int, fingerX: Float, fingerY: Float, reduced: Boolean): Pose {
    // Reduced motion: every card jumps to its next place half-way while the deck fades through.
    var place = at
    var dragX = fingerX
    var dragY = fingerY
    var fade = 1f
    if (reduced) {
        val nearest = Math.round(place).toFloat()
        fade = 1f - 2f * abs(place - nearest)
        place = nearest
        dragX = 0f
        dragY = 0f
    }

    val p = Pose()
    val last = count - 1f
    if (place <= 0f) {
        // The front card, on its way down: a quick tilt one way, then the fall turns it the other way.
        val t = -place
        val fall = easeInOut(segment(t, 0f, 0.62f))
        p.x = DROP_X * fall + dragX
        p.y = DROP_Y * fall + dragY
        p.rotation = 10f * easeOut(segment(t, 0f, 0.28f)) + (DROP_ROTATION - 10f) * easeInOut(segment(t, 0.22f, 0.8f)) +
            dragX * DRAG_TILT
        p.scale = mix(1f, DROP_SCALE, fall)
        p.alpha = mix(1f, DROP_ALPHA, smooth(segment(t, 0.1f, 0.62f)))
        p.blur = DROP_BLUR * smooth(segment(t, 0.08f, 0.6f))
        p.face = 1f
        p.z = 4f
    } else if (place <= 1f) {
        // The first ghost turns upright and becomes the front card, with a small lift on the way.
        val part = 1f - place
        val s = settle(part, 0.1f)
        p.x = GhostA.x * (1f - s)
        p.y = GhostA.y * (1f - s)
        p.rotation = GhostA.rotation * (1f - s)
        p.scale = mix(GhostA.scale, 1f, s) + 0.035f * sin(PI.toFloat() * segment(part, 0.1f, 0.75f))
        p.face = easeOut(segment(part, 0.08f, 0.5f))
        p.z = 3f
    } else if (place <= 2f) {
        // The second ghost swings over to the first ghost's angle a moment later.
        val s = settle(2f - place, 0.2f)
        p.x = mix(GhostB.x, GhostA.x, s)
        p.y = mix(GhostB.y, GhostA.y, s)
        p.rotation = mix(GhostB.rotation, GhostA.rotation, s)
        p.scale = mix(GhostB.scale, GhostA.scale, s)
        p.ghost = mix(GhostB.ghost, GhostA.ghost, s)
        p.z = 2f
    } else if (place > last - 1f && last - place < 0.3f) {
        // The dropped card sinks out of sight before it comes back as a ghost.
        val gone = segment(last - place, 0f, 0.3f)
        p.x = DROP_X
        p.y = DROP_Y + 26f * gone
        p.rotation = DROP_ROTATION
        p.scale = DROP_SCALE
        p.alpha = DROP_ALPHA * (1f - gone)
        p.blur = DROP_BLUR
        p.face = 1f
        p.z = 4f
    } else {
        // A new ghost swings in at the back of the stack. Cards further back stay hidden.
        val part = 3f - place
        val s = settle(part, 0.36f)
        p.x = GhostB.x - 14f * (1f - s)
        p.y = GhostB.y + 10f * (1f - s)
        p.rotation = GhostB.rotation - 16f * (1f - s)
        p.scale = GhostB.scale - 0.03f * (1f - s)
        p.alpha = easeOut(segment(part, 0.36f, 0.8f))
        p.ghost = GhostB.ghost
    }
    p.alpha *= fade
    return p
}

/**
 * A deck of destination cards. A sideways or downward swipe sends the front card away: it tilts,
 * drops below the stack and stays there blurred and dim, while the ghost card behind it turns
 * upright and becomes solid, the other ghosts shuffle their angles and the glow behind the stack
 * takes the new card's colour. The deck is a ring: after the last card the first one comes back.
 *
 * @param items The cards, front first. At least four.
 * @param autoPlay Swipes a card away every two seconds, as if a finger did it.
 * @param canvas Colour of the surface the stack sits on: the dropped card fades into it at the bottom edge.
 * @param onChange Called with the index of the card that is now in front.
 * @param onOpen Called with the index of the front card when its arrow button is tapped.
 */
@Composable
fun DestinationSwipeStack(
    items: List<Destination> = Destinations,
    autoPlay: Boolean = false,
    canvas: Color = Color(0xFFF4F4F7),
    onChange: ((index: Int) -> Unit)? = null,
    onOpen: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val count = items.size
    val swipe = if (reducedMotion) SWIPE_REDUCED else SWIPE
    val view = LocalView.current
    val changed by rememberUpdatedState(onChange)

    val deck = remember { Deck() }

    LaunchedEffect(auto) {
        deck.hold = 0f
        deck.clock = 0f
        deck.base = Math.round(deck.pos.floatValue).toFloat()
    }

    LaunchedEffect(count, swipe) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            var p = deck.pos.floatValue
            if (looping && deck.hold <= 0f) {
                val loop = STEP * count
                deck.clock += dt
                if (deck.clock >= loop) deck.clock -= loop
                p = deck.base + script(deck.clock, count)
                deck.target = p
                deck.live = true
            } else {
                if (deck.live) {
                    // A touch took over: the swipe the loop was in the middle of finishes first.
                    deck.target = ceil(p - 0.000001f)
                    deck.live = false
                }
                if (deck.requests > 0) {
                    deck.target = min(deck.target + deck.requests, floor(p) + 2f)
                    deck.requests = 0
                }
                if (p < deck.target) {
                    p = min(deck.target, p + dt / swipe)
                } else if (deck.hold > 0f && !deck.touching) {
                    deck.hold -= dt
                    if (deck.hold <= 0f) {
                        p = wrapIndex(p.roundToInt(), count).toFloat()
                        deck.target = p
                        deck.base = p
                        deck.clock = 0f
                    }
                }
            }
            deck.pos.floatValue = p

            if (deck.touching) {
                deck.velocityX = 0f
                deck.velocityY = 0f
            } else {
                deck.velocityX += (-RETURN_STIFFNESS * deck.dragX.floatValue - RETURN_DAMPING * deck.velocityX) * dt
                deck.velocityY += (-RETURN_STIFFNESS * deck.dragY.floatValue - RETURN_DAMPING * deck.velocityY) * dt
                deck.dragX.floatValue += deck.velocityX * dt
                deck.dragY.floatValue += deck.velocityY * dt
            }
        }
    }

    val front by remember(count) { derivedStateOf { wrapIndex(deck.pos.floatValue.roundToInt(), count) } }
    LaunchedEffect(count) {
        snapshotFlow { wrapIndex(deck.pos.floatValue.roundToInt(), count) }.drop(1).collect { changed?.invoke(it) }
    }

    // A touch takes over from the loop, which starts again from rest a little later.
    fun next() {
        deck.hold = USER_HOLD
        deck.requests += 1
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next destination") {
                        next()
                        true
                    },
                )
            },
    ) {
        // The soft light behind the stack in each card's colour, as strong as that card is close to the front.
        Box(Modifier.matchParentSize().bleed(GLOW_BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(GLOW_BLEED) { x, y, _, _ ->
                    val p = deck.pos.floatValue
                    val center = Offset(x + WIDTH / 2f, y + CENTER_Y)
                    // The glow swells a little while the deck is moving.
                    val swell = 1f + 0.07f * sin(PI.toFloat() * (p - floor(p)))
                    scale(swell, GLOW_STRETCH * swell, pivot = center) {
                        for ((index, item) in items.withIndex()) {
                            val weight = glowWeight(relative(index, p, count))
                            if (weight <= 0f) continue
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to item.glow.copy(alpha = 0.62f),
                                    0.55f to item.glow.copy(alpha = 0.4f),
                                    1f to item.glow.copy(alpha = 0f),
                                    center = center,
                                    radius = GLOW_RADIUS,
                                ),
                                radius = GLOW_RADIUS,
                                center = center,
                                alpha = weight,
                            )
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .matchParentSize()
                .drawWithContent {
                    // Cut at the bottom edge only: the ghosts and a dragged card may reach past the sides.
                    val bleed = BLEED.dp.toPx()
                    clipRect(-bleed, -bleed, size.width + bleed, size.height) { this@drawWithContent.drawContent() }
                    val fade = FADE.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(canvas.copy(alpha = 0f), canvas),
                            startY = size.height - fade,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, size.height - fade),
                        size = Size(size.width, fade),
                    )
                }
                .pointerInput(Unit) {
                    var dx = 0f
                    var dy = 0f
                    var active = false
                    detectDragGestures(
                        onDragStart = {
                            dx = 0f
                            dy = 0f
                            deck.hold = USER_HOLD
                            // A swipe that is still running finishes first.
                            active = abs(deck.pos.floatValue - Math.round(deck.pos.floatValue)) < 0.001f
                        },
                        onDragEnd = {
                            if (deck.touching) {
                                deck.touching = false
                                if (abs(dx) > COMMIT || dy > COMMIT) next()
                            }
                        },
                        onDragCancel = { deck.touching = false },
                    ) { change, amount ->
                        change.consume()
                        if (active) {
                            dx += amount.x / density
                            dy += amount.y / density
                            deck.touching = true
                            deck.hold = USER_HOLD
                            deck.dragX.floatValue = dx * DRAG_GAIN
                            deck.dragY.floatValue = dy * (if (dy > 0f) DRAG_GAIN else DRAG_GAIN_UP)
                        }
                    }
                },
        ) {
            items.forEachIndexed { index, item ->
                DeckCard(
                    item = item,
                    isFront = index == front,
                    current = {
                        pose(relative(index, deck.pos.floatValue, count), count, deck.dragX.floatValue, deck.dragY.floatValue, reducedMotion)
                    },
                    onOpen = { onOpen?.invoke(index) },
                )
            }
        }
    }
}

/** One card: the grey body it shows as a ghost, and the dark face that fades in over it. */
@Composable
private fun DeckCard(item: Destination, isFront: Boolean, current: () -> Pose, onOpen: () -> Unit) {
    val rank by remember(current) { derivedStateOf { current().z } }
    val shape = RoundedCornerShape(CARD_RADIUS.dp)
    val shadow = remember { SoftRoundRect(rgba(24, 24, 40, 0.3f), sigma = 22f) }
    val layer = Modifier.size((CARD_WIDTH + CARD_BLEED * 2f).dp, (CARD_HEIGHT + CARD_BLEED * 2f).dp)

    Box(
        Modifier
            .zIndex(rank)
            .offset(((WIDTH - CARD_WIDTH) / 2f - CARD_BLEED).dp, (CENTER_Y - CARD_HEIGHT / 2f - CARD_BLEED).dp)
            // The layer is wider than the component, so it must not be squeezed into it.
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .then(layer)
            .graphicsLayer {
                val p = current()
                translationX = p.x.dp.toPx()
                translationY = p.y.dp.toPx()
                rotationZ = p.rotation
                scaleX = p.scale
                scaleY = p.scale
                alpha = p.alpha
                // Blurring a layer needs Android 12; older versions drop the card sharp.
                renderEffect = if (p.blur > 0.05f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val radius = sigmaToRadius(p.blur.dp.toPx())
                    BlurEffect(radius, radius, TileMode.Decal)
                } else {
                    null
                }
            }
            .then(if (isFront) Modifier else Modifier.clearAndSetSemantics {}),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
                .graphicsLayer { alpha = current().ghost }
                .background(Color(0xFF3A3A46), shape),
        ) {
            Glyph(item.icon, rgba(255, 255, 255, 0.9f), Modifier.offset(14.dp, 14.dp))
        }

        Box(layer.graphicsLayer { alpha = current().face }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                surface(CARD_BLEED.dp) { x, y, w, h -> drawIntoCanvas { shadow.draw(it.nativeCanvas, x, y + 16f, w, h, CARD_RADIUS) } }
            }
            Box(
                Modifier
                    .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF2C2C34), Color(0xFF17171B))), shape)
                    .border(1.dp, rgba(255, 255, 255, 0.07f), shape),
            ) {
                Glyph(item.icon, Color(0xFF8D8D99), Modifier.offset(14.dp, 14.dp))

                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset((-12).dp, 12.dp)
                        .size(24.dp)
                        .background(rgba(255, 255, 255, 0.16f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = isFront,
                            role = Role.Button,
                            onClick = onOpen,
                        )
                        .semantics { contentDescription = "Open ${item.title}" },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val unit = 1.dp.toPx()
                        val ink = rgba(255, 255, 255, 0.88f)
                        val head = Path().apply {
                            moveTo(10.2f * unit, 8.8f * unit)
                            lineTo(15.2f * unit, 8.8f * unit)
                            lineTo(15.2f * unit, 13.8f * unit)
                        }
                        drawLine(ink, Offset(8.8f * unit, 15.2f * unit), Offset(15.2f * unit, 8.8f * unit), 1.3f * unit, StrokeCap.Round)
                        drawPath(head, ink, style = Stroke(1.3f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }

                Column(
                    Modifier
                        .offset(12.dp, 47.dp)
                        .width((CARD_WIDTH - 24f).dp)
                        .semantics(mergeDescendants = true) {},
                ) {
                    BasicText(
                        item.title.uppercase(),
                        Modifier.fillMaxWidth().height(26.dp).wrapContentHeight(),
                        style = TitleStyle,
                        maxLines = 1,
                        autoSize = TitleFit,
                    )
                    BasicText(
                        item.subtitle.uppercase(),
                        Modifier.fillMaxWidth().height(26.dp).wrapContentHeight(),
                        style = SubtitleStyle,
                        maxLines = 1,
                        autoSize = TitleFit,
                    )
                }

                // The photo over a flat tint of its main colour, faded in when it arrives.
                Box(
                    Modifier
                        .offset(6.dp, 112.dp)
                        .size((CARD_WIDTH - 12f).dp, 172.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(item.tint),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current).data(item.photo).crossfade(200).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 7.dp)
                            .height(20.dp)
                            .background(rgba(22, 22, 26, 0.82f), CircleShape)
                            .padding(horizontal = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(5.dp).background(item.glow, CircleShape))
                        BasicText(item.tag, style = TagStyle, maxLines = 1)
                    }
                }

                BasicText(
                    item.caption,
                    Modifier.offset(((CARD_WIDTH - 196f) / 2f).dp, 295.dp).width(196.dp),
                    style = CaptionStyle,
                    maxLines = 2,
                )
            }
        }
    }
}

/** A small line icon, drawn from the same strokes in every stack. */
@Composable
private fun Glyph(icon: DestinationIcon, color: Color, modifier: Modifier = Modifier) {
    val lines = remember(icon) { strokes(icon) }
    val rings = remember(icon) { circles(icon) }
    Canvas(modifier.size(ICON_SIZE.dp)) {
        val unit = size.width / 20f
        val path = Path()
        for (line in lines) {
            for (index in line.indices step 2) {
                if (index == 0) path.moveTo(line[0] * unit, line[1] * unit) else path.lineTo(line[index] * unit, line[index + 1] * unit)
            }
        }
        val stroke = Stroke(1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path, color, style = stroke)
        for (ring in rings) drawCircle(color, ring[2] * unit, Offset(ring[0] * unit, ring[1] * unit), style = stroke)
    }
}
