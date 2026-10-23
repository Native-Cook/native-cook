package com.ngocdevv.nativecook.blocks.cards.staybookingcard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop

/**
 * @param area Floor area in square feet.
 * @param price Price per night in whole dollars.
 * @param tint The deep colour the photo sinks into under the texts; also shown flat until the photo arrives.
 */
data class Stay(
    val name: String,
    val address: String,
    val beds: Int,
    val baths: Int,
    val area: Int,
    val price: Int,
    val photo: String,
    val tint: Color,
)

private const val WIDTH = 330f
private const val HEIGHT = 440f
private const val RADIUS = 26f
private const val PAD = 16f

// The photo is wider than the card, so it can lag behind its slide without showing an edge.
private const val PHOTO_WIDTH = WIDTH * 1.6f

// Share of a card's width the photo stays behind over one stay.
private const val PARALLAX = 0.3f

// The photo is blurred from BLUR_FROM down, fully so at BLUR_TO; the tint starts at TINT_Y.
private const val BLUR = 14f
private const val BLUR_FROM = 250f
private const val BLUR_TO = 304f
private const val TINT_Y = 215f

private const val DOTS_Y = 267f
private const val NAME_Y = 293f
private const val ADDRESS_Y = 324f
private const val FACTS_Y = 349f

// The row with the price and the button.
private const val ROW_Y = 382f
private const val ROW_HEIGHT = 42f
private const val PRICE_WIDTH = 82f
private const val BUTTON_WIDTH = 204f
private const val DIGIT = 12.5f
private const val PRICE_LINE = 26f

// How far the check moves left to make room for "Reserved", and where that word sits.
private const val CHECK_SHIFT = 37f
private const val RESERVED_SHIFT = 12f

// Dp the name travels over one stay; each line below travels STAGGER more.
private const val TRAVEL = 70f
private const val STAGGER = 34f

// The spring that carries the stays to rest (mass 1).
private const val STIFFNESS = 150f
private const val DAMPING = 20f

// Finger travel that changes one stay.
private const val DRAG_SPAN = 260f

// A release lands on the stay the slide would reach after this many seconds at its speed.
private const val THROW_TIME = 0.18f

// Seconds the button takes to confirm, and to go back when the stay changes.
private const val RESERVE = 1.5f
private const val UNRESERVE = 0.35f

// autoPlay (seconds): a swipe at FIRST and every STEP until the last stay, which is reserved one
// step later and kept for KEEP before the swipe back to the first. With three stays one round
// takes LOOP.
private const val LOOP = 9.6f
private const val FIRST = 0.8f
private const val STEP = 2.2f
private const val KEEP = 2.4f

// After a touch the loop waits this long before it starts again.
private const val USER_HOLD = 2.5f

// The card's shadow is drawn on a canvas this much larger than the card on every side.
private val SHADOW_BLEED = 70.dp

private fun photo(id: String) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=1056&h=880&q=80"

private val Stays = listOf(
    Stay("The Hill Guest House", "Alpweg 12, Lauterbrunnen, 3822", 2, 1, 1150, 620, photo("1464822759023-fed622ff2c3b"), Color(0xFF2F4A1C)),
    Stay("Casa Caldera", "Nomikou 7, Oia, Santorini 847 02", 3, 2, 1480, 895, photo("1570077188670-e3a8d69ac5ff"), Color(0xFF1C3A5E)),
    Stay("Lagoon Water Villa", "Jetty 3, Baa Atoll, Maldives", 1, 1, 920, 740, photo("1573843981267-be1999ff37cd"), Color(0xFF0E4A52)),
)

private enum class StayIcon { Map, Bed, Bath, Area }

/** Polylines of an icon in a 16 × 16 box, as x, y pairs. */
private fun strokes(icon: StayIcon): List<FloatArray> = when (icon) {
    StayIcon.Map -> listOf(
        floatArrayOf(2f, 4f, 6f, 2.5f, 10f, 4f, 14f, 2.5f, 14f, 12f, 10f, 13.5f, 6f, 12f, 2f, 13.5f, 2f, 4f),
        floatArrayOf(6f, 2.5f, 6f, 12f),
        floatArrayOf(10f, 4f, 10f, 13.5f),
    )
    StayIcon.Bed -> listOf(
        floatArrayOf(1.8f, 13f, 1.8f, 4.5f),
        floatArrayOf(1.8f, 10.5f, 14.2f, 10.5f),
        floatArrayOf(14.2f, 13f, 14.2f, 8.2f, 7.2f, 8.2f, 7.2f, 10.5f),
        floatArrayOf(3.4f, 8.4f, 5.6f, 8.4f),
    )
    StayIcon.Bath -> listOf(
        floatArrayOf(1.5f, 8.5f, 14.5f, 8.5f),
        floatArrayOf(2.5f, 8.5f, 3.2f, 12f, 4.6f, 13f, 11.4f, 13f, 12.8f, 12f, 13.5f, 8.5f),
        floatArrayOf(4f, 8.5f, 4f, 3.6f, 5.2f, 2.6f, 6.6f, 3.6f),
    )
    StayIcon.Area -> listOf(
        floatArrayOf(2.5f, 2.5f, 13.5f, 2.5f, 13.5f, 13.5f, 2.5f, 13.5f, 2.5f, 2.5f),
        floatArrayOf(5.5f, 10.5f, 10.5f, 5.5f),
        floatArrayOf(8f, 5.5f, 10.5f, 5.5f, 10.5f, 8f),
        floatArrayOf(5.5f, 8f, 5.5f, 10.5f, 8f, 10.5f),
    )
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val Soft = rgba(255, 255, 255, 0.74f)

private fun stayText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, align: TextAlign = TextAlign.Start) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    textAlign = align,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val NameStyle = stayText(19f, 24f, FontWeight.SemiBold, Color.White)
private val AddressStyle = stayText(13f, 18f, FontWeight.Normal, Soft)
private val FactStyle = stayText(12.5f, 17f, FontWeight.Normal, Soft)
private val PriceStyle = stayText(20f, PRICE_LINE, FontWeight.Medium, Color.White, TextAlign.Center)
private val ButtonStyle = stayText(14.5f, 20f, FontWeight.SemiBold, Color(0xFF111111))

/** Per-frame state of the card. [pos] is the stay in view, with fractions while it slides. */
private class Pager {
    val pos = mutableFloatStateOf(0f)
    val target = mutableFloatStateOf(0f)

    /** Stays per second. */
    var velocity = 0f

    /** The stay that is reserved, or −1, and how far its button has come, 0…1. */
    val reserved = mutableIntStateOf(-1)
    val confirm = mutableFloatStateOf(0f)

    /** Where the loop started counting from, and its clock. */
    var base = 0f
    var clock = 0f

    /** Seconds the loop still waits after a touch. */
    var hold = 0f
    var touching = false

    /** Where the stays were when the finger took them. */
    var grabbed = 0f
    var previous = 0f
}

private fun wrapIndex(index: Int, count: Int) = ((index % count) + count) % count

private fun segment(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOut(t: Float) = 1f - (1f - t).pow(3)

private fun easeInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (2f - 2f * t).pow(3) / 2f

/** Ease-out that runs about 10% past its end before it settles. */
private fun easeOutBack(t: Float) = 1f + 2.70158f * (t - 1f).pow(3) + 1.70158f * (t - 1f).pow(2)

private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Where stay [index] stands: 0 in view, 1 one card to the right, −1 one card to the left. */
private fun centered(index: Int, pos: Float, count: Int): Float {
    var place = (index - pos) % count
    if (place <= -count / 2f) place += count
    if (place > count / 2f) place -= count
    return place
}

/** How much the stay at [place] is the one in view, 0…1. */
private fun weight(place: Float) = smooth(1f - abs(place))

/** Opacity of text line number [order] of the stay at [place]: the lower the line, the sooner it goes. */
private fun lineFade(place: Float, order: Int) = smooth(1f - abs(place) * (2.2f + 0.5f * order))

/** Reduced motion: the photos stay in place and the one further away fades over the nearer one. */
private fun crossFade(place: Float) = if (abs(place) <= 0.5f) 1f else max(0f, 1f - abs(place))

/** Opacity of a digit that rolls through its column at [place]: it fades only as it leaves. */
private fun rollFade(place: Float) = smooth((0.75f - abs(place)) / 0.3f)

/** Each column of the price changes during its own part of the slide, the last digit first. */
private fun columnPosition(pos: Float, column: Int, digits: Int): Float {
    val whole = floor(pos)
    val from = 0.1f * (digits - 1 - column)
    return whole + easeInOut(segment(pos - whole, from, from + 0.6f))
}

/** The character of [price] in [column] of a right-aligned field [digits] wide. */
private fun digit(price: Int, column: Int, digits: Int): String {
    val text = price.toString()
    val index = column - (digits - text.length)
    return if (index < 0) "" else text[index].toString()
}

/**
 * A photo card for a place to stay: its lower part sinks, blurred, into a deep tint that carries the
 * name, the address, three facts, the price and a "Reserve Now" button. A sideways swipe changes the
 * stay: the photo slides with parallax, the texts follow one after another, the tint changes colour
 * and the price rolls like a counter. A tap on the button shrinks it to a circle, draws a check and
 * widens it again as "Reserved".
 *
 * @param stays The stays, in a ring. At least three.
 * @param autoPlay Swipes through the stays and reserves the last one, as if a finger did it.
 * @param onChange Called with the index of the stay the card is settling on.
 * @param onReserve Called with the index of the stay when it is reserved.
 */
@Composable
fun StayBookingCard(
    stays: List<Stay> = Stays,
    autoPlay: Boolean = false,
    bedsLabel: String = "Bed",
    bathsLabel: String = "Baths",
    areaLabel: String = "Sqft",
    currency: String = "$",
    reserveLabel: String = "Reserve Now",
    reservedLabel: String = "Reserved",
    onChange: ((index: Int) -> Unit)? = null,
    onReserve: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val count = stays.size
    val digits = stays.maxOf { it.price }.toString().length
    val view = LocalView.current
    val changed by rememberUpdatedState(onChange)

    val pager = remember { Pager() }

    LaunchedEffect(auto) {
        pager.hold = 0f
        pager.clock = 0f
        pager.base = pager.target.floatValue
    }

    LaunchedEffect(count) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            if (dt <= 0f) continue
            var p = pager.pos.floatValue
            var goal = pager.target.floatValue

            if (looping && pager.hold <= 0f) {
                val reserveAt = FIRST + STEP * (count - 1)
                val backAt = reserveAt + KEEP
                val loop = LOOP + STEP * (count - 3)
                pager.clock += dt
                if (pager.clock >= loop) {
                    // Once round the ring: the same stays under smaller numbers.
                    pager.clock -= loop
                    p -= count
                    pager.previous -= count
                }
                var swipes = if (pager.clock >= backAt) 1f else 0f
                for (index in 0 until count - 1) if (pager.clock >= FIRST + STEP * index) swipes += 1f
                goal = pager.base + swipes
                pager.reserved.intValue =
                    if (pager.clock >= reserveAt && pager.clock < backAt) wrapIndex(pager.base.roundToInt() + count - 1, count) else -1
            } else if (pager.hold > 0f && !pager.touching) {
                pager.hold -= dt
                if (pager.hold <= 0f) {
                    val settled = pager.confirm.floatValue == 0f || pager.confirm.floatValue == 1f
                    if (p == goal && settled) {
                        p = wrapIndex(p.roundToInt(), count).toFloat()
                        pager.previous = p
                        goal = p
                        pager.base = p
                        pager.clock = 0f
                    } else {
                        pager.hold = 0.01f
                    }
                }
            }

            if (pager.touching) {
                // The finger moves the stays; keep a smoothed speed for the release.
                pager.velocity += ((p - pager.previous) / dt - pager.velocity) * min(1f, dt * 30f)
            } else {
                pager.velocity += (STIFFNESS * (goal - p) - DAMPING * pager.velocity) * dt
                p += pager.velocity * dt
                if (abs(goal - p) < 0.0004f && abs(pager.velocity) < 0.004f) {
                    p = goal
                    pager.velocity = 0f
                }
            }

            // The button confirms while its stay is the one in view, and goes back as soon as it is not.
            val taken = pager.reserved.intValue
            val here = taken >= 0 && taken == wrapIndex(goal.roundToInt(), count)
            pager.confirm.floatValue =
                if (here) min(1f, pager.confirm.floatValue + dt / RESERVE) else max(0f, pager.confirm.floatValue - dt / UNRESERVE)

            pager.pos.floatValue = p
            pager.previous = p
            pager.target.floatValue = goal
        }
    }

    val current by remember(count) { derivedStateOf { wrapIndex(pager.target.floatValue.roundToInt(), count) } }
    val booked by remember { derivedStateOf { pager.reserved.intValue >= 0 && pager.reserved.intValue == current } }
    LaunchedEffect(count) {
        snapshotFlow { wrapIndex(pager.target.floatValue.roundToInt(), count) }.drop(1).collect { changed?.invoke(it) }
    }

    fun place(index: Int) = centered(index, pager.pos.floatValue, count)
    val shadow = remember { SoftRoundRect(rgba(30, 34, 40, 0.22f), sigma = 26f) }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        Box(Modifier.matchParentSize().bleed(SHADOW_BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(SHADOW_BLEED) { x, y, w, h -> drawIntoCanvas { shadow.draw(it.nativeCanvas, x, y + 18f, w, h, RADIUS) } }
            }
        }

        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(RADIUS.dp))
                .pointerInput(count) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            // A touch takes over from the loop, which starts again from rest a little later.
                            pager.touching = true
                            pager.grabbed = pager.pos.floatValue
                            pager.hold = USER_HOLD
                        },
                        onDragEnd = {
                            pager.touching = false
                            pager.hold = USER_HOLD
                            val home = Math.round(pager.grabbed).toFloat()
                            val landing = Math.round(pager.pos.floatValue + pager.velocity * THROW_TIME).toFloat()
                            val next = landing.coerceIn(home - 1f, home + 1f)
                            if (next != Math.round(pager.target.floatValue).toFloat()) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            pager.target.floatValue = next
                        },
                        onDragCancel = { pager.touching = false },
                    ) { change, amount ->
                        change.consume()
                        pager.hold = USER_HOLD
                        // One stay at most per swipe.
                        val home = Math.round(pager.grabbed).toFloat()
                        pager.pos.floatValue = (pager.pos.floatValue - amount / density / DRAG_SPAN).coerceIn(home - 1f, home + 1f)
                    }
                },
        ) {
            // One slide per stay: it moves a full card per stay while the photo inside it lags behind.
            stays.forEachIndexed { index, stay ->
                val far by remember(index, count) { derivedStateOf { abs(place(index)) > 0.5f } }
                Box(
                    Modifier
                        .zIndex(if (far) 1f else 0f)
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = if (reducedMotion) 0f else place(index) * size.width
                            alpha = if (reducedMotion) crossFade(place(index)) else 1f
                            clip = true
                        },
                ) {
                    Box(
                        Modifier
                            .offset((-(PHOTO_WIDTH - WIDTH) / 2f).dp, 0.dp)
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .size(PHOTO_WIDTH.dp, HEIGHT.dp)
                            .graphicsLayer { translationX = if (reducedMotion) 0f else (-place(index) * WIDTH * PARALLAX).dp.toPx() }
                            .background(stay.tint),
                    ) {
                        Photo(stay, Modifier.fillMaxSize())
                        // A blurred copy that fades in towards the bottom (sharp below Android 12, which cannot blur a layer).
                        Photo(
                            stay,
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        Brush.verticalGradient(
                                            BLUR_FROM / HEIGHT to Color.Transparent,
                                            BLUR_TO / HEIGHT to Color.Black,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                                .blur(BLUR.dp),
                        )
                    }
                }
            }

            // The tint under the texts, in the colour of the stay in view; the dots above it.
            Canvas(Modifier.zIndex(2f).fillMaxSize()) {
                surface(0.dp) { _, _, _, _ ->
                    for ((index, stay) in stays.withIndex()) {
                        val near = weight(place(index))
                        if (near <= 0f) continue
                        drawRect(
                            Brush.verticalGradient(
                                0f to stay.tint.copy(alpha = 0f),
                                0.38f to stay.tint.copy(alpha = 0.55f),
                                0.66f to stay.tint.copy(alpha = 0.92f),
                                1f to stay.tint.copy(alpha = 0.97f),
                                startY = TINT_Y,
                                endY = HEIGHT,
                            ),
                            topLeft = Offset(0f, TINT_Y),
                            size = Size(WIDTH, HEIGHT - TINT_Y),
                            alpha = near,
                        )
                    }
                    var total = 5f * (count - 1)
                    for (index in 0 until count) total += 6f + 10f * weight(place(index))
                    var x = (WIDTH - total) / 2f
                    for (index in 0 until count) {
                        val on = weight(place(index))
                        drawRoundRect(Color.White.copy(alpha = 0.45f + 0.55f * on), Offset(x, DOTS_Y), Size(6f + 10f * on, 6f), CornerRadius(3f))
                        x += 6f + 10f * on + 5f
                    }
                }
            }

            // Name, address and facts of each stay: each line travels a little further than the one above.
            stays.forEachIndexed { index, stay ->
                fun line(order: Int, y: Float) = Modifier
                    .zIndex(3f)
                    .offset(PAD.dp, y.dp)
                    .graphicsLayer {
                        translationX = if (reducedMotion) 0f else (place(index) * (TRAVEL + STAGGER * order)).dp.toPx()
                        alpha = lineFade(place(index), order)
                    }
                BasicText(stay.name, line(0, NAME_Y), style = NameStyle, maxLines = 1, softWrap = false)
                Row(
                    line(1, ADDRESS_Y).height(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(StayIcon.Map)
                    BasicText(stay.address, style = AddressStyle, maxLines = 1, softWrap = false)
                }
                Row(line(2, FACTS_Y).size((WIDTH - PAD * 2f).dp, 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { Fact(StayIcon.Bed, bedsLabel, stay.beds) }
                    Box(Modifier.size(1.dp, 20.dp).background(rgba(255, 255, 255, 0.25f)))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Fact(StayIcon.Bath, bathsLabel, stay.baths) }
                    Box(Modifier.size(1.dp, 20.dp).background(rgba(255, 255, 255, 0.25f)))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Fact(StayIcon.Area, areaLabel, stay.area) }
                }
            }

            // The price: each digit rolls in its own column, the last one first.
            Row(
                Modifier
                    .zIndex(3f)
                    .offset(PAD.dp, ROW_Y.dp)
                    .size(PRICE_WIDTH.dp, ROW_HEIGHT.dp)
                    .background(rgba(255, 255, 255, 0.2f), CircleShape)
                    .border(1.dp, rgba(255, 255, 255, 0.12f), CircleShape)
                    .clearAndSetSemantics { contentDescription = "$currency${stays[current].price} per night" },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(currency, style = PriceStyle, maxLines = 1)
                for (column in 0 until digits) {
                    Box(Modifier.size(DIGIT.dp, PRICE_LINE.dp).clipToBounds()) {
                        stays.forEachIndexed { index, stay ->
                            BasicText(
                                digit(stay.price, column, digits),
                                Modifier.matchParentSize().graphicsLayer {
                                    val at = centered(index, columnPosition(pager.pos.floatValue, column, digits), count)
                                    translationY = if (reducedMotion) 0f else (at * PRICE_LINE).dp.toPx()
                                    alpha = if (reducedMotion) max(0f, 1f - abs(at)) else rollFade(at)
                                },
                                style = PriceStyle,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            ReserveButton(
                tint = stays[current].tint,
                label = reserveLabel,
                doneLabel = reservedLabel,
                booked = booked,
                reduced = reducedMotion,
                confirm = { pager.confirm.floatValue },
                onClick = {
                    pager.hold = USER_HOLD
                    if (pager.reserved.intValue == current) {
                        pager.reserved.intValue = -1
                    } else {
                        pager.reserved.intValue = current
                        onReserve?.invoke(current)
                    }
                },
                modifier = Modifier.zIndex(3f).offset((WIDTH - PAD - BUTTON_WIDTH).dp, ROW_Y.dp),
            )
        }
    }
}

/** The white pill: it shrinks to a circle, draws a check, and widens again with the confirmation. */
@Composable
private fun ReserveButton(
    tint: Color,
    label: String,
    doneLabel: String,
    booked: Boolean,
    reduced: Boolean,
    confirm: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val press by animateFloatAsState(if (pressed) PRESS_SCALE else 1f, tween(PRESS_SCALE_MS, easing = EaseOutStrong), label = "press")
    // The check mark, in a 16 × 16 box.
    val check = remember {
        Path().apply {
            moveTo(2f, 8.5f)
            lineTo(6f, 12.5f)
            lineTo(14.5f, 3.5f)
        }
    }
    val measure = remember { PathMeasure().apply { setPath(check, false) } }
    val drawn = remember { Path() }

    Box(
        modifier
            .size(BUTTON_WIDTH.dp, ROW_HEIGHT.dp)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (booked) doneLabel else label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            surface(0.dp) { _, _, w, h ->
                val t = confirm()
                val shrink = easeInOut(segment(t, 0f, 0.22f))
                val widen = easeOutBack(segment(t, 0.6f, 0.92f))
                val width = if (reduced) BUTTON_WIDTH else BUTTON_WIDTH - (BUTTON_WIDTH - ROW_HEIGHT) * (shrink - widen)
                drawRoundRect(Color.White, Offset((w - width) / 2f, 0f), Size(width, h), CornerRadius(h / 2f))
                val stroke = easeOut(segment(t, 0.24f, 0.52f))
                if (stroke > 0f) {
                    drawn.rewind()
                    measure.getSegment(0f, measure.length * stroke, drawn, true)
                    translate(w / 2f - 8f - CHECK_SHIFT * widen, h / 2f - 8f) {
                        drawPath(drawn, tint, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
        BasicText(label, Modifier.graphicsLayer { alpha = 1f - segment(confirm(), 0f, 0.12f) }, style = ButtonStyle, maxLines = 1)
        BasicText(
            doneLabel,
            Modifier.offset(RESERVED_SHIFT.dp, 0.dp).graphicsLayer { alpha = segment(confirm(), 0.72f, 0.95f) },
            style = ButtonStyle.copy(color = tint),
            maxLines = 1,
        )
    }
}

@Composable
private fun Fact(icon: StayIcon, label: String, value: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon)
        BasicText(
            buildAnnotatedString {
                append("$label: ")
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append(value.toString()) }
            },
            style = FactStyle,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The photo, faded in when it arrives. */
@Composable
private fun Photo(stay: Stay, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current).data(stay.photo).crossfade(200).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** A small line icon, drawn from the same strokes in every stack. */
@Composable
private fun Glyph(icon: StayIcon) {
    val lines = remember(icon) { strokes(icon) }
    Canvas(Modifier.size(14.dp)) {
        val unit = size.width / 16f
        val path = Path()
        for (line in lines) {
            for (index in line.indices step 2) {
                if (index == 0) path.moveTo(line[0] * unit, line[1] * unit) else path.lineTo(line[index] * unit, line[index + 1] * unit)
            }
        }
        drawPath(path, Soft, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
