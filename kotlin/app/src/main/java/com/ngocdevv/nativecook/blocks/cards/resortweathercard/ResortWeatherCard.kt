package com.ngocdevv.nativecook.blocks.cards.resortweathercard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @param temperature Degrees Celsius, between -99 and 99: the display ticks to it, and colder resorts get more snow.
 * @param likes The count in the pill on the photo.
 * @param runs The three facts under the name: this one, [distance] and [lifts].
 * @param photo Address of the photo, and [thumbnail] of its small copy for the strip.
 * @param tint The flat colour shown until the photo arrives.
 */
class ResortWeatherCardResort(
    val name: String,
    val region: String,
    val temperature: Int,
    val likes: String,
    val runs: String,
    val distance: String,
    val lifts: String,
    val photo: String,
    val thumbnail: String,
    val tint: Color,
)

class ResortWeatherCardFan(val initials: String, val color: Color)

private const val WIDTH = 340f
private const val HEIGHT = 600f
private const val RADIUS = 34f

private const val HEADER_Y = 38f

// The strip of thumbnails: their side and corner at rest and when picked.
private const val THUMB_Y = 96f
private const val THUMB = 44f
private const val THUMB_PICKED = 60f
private const val THUMB_RADIUS = 12f
private const val THUMB_PICKED_RADIUS = 16f
private const val THUMB_GAP = 8f

private const val CARD_TOP = 138f
private val CARD = Size(320f, 440f)
private const val CARD_RADIUS = 36f

// The photo inside the card.
private val PHOTO = Rect(6f, 6f, 314f, 256f)
private const val PHOTO_RADIUS = 30f
private const val DOTS_Y = 272f
private const val NAME_Y = 302f
private const val REGION_Y = 330f
private val FACTS = Rect(20f, 364f, 300f, 416f)
private val Fact = Color(0xFF6C6F73)

// On the photo: the temperature pill, the fans (smallest first) and the pill with the count.
private val PILL = Size(178f, 40f)
private val FAN_SPOTS = listOf(Triple(14f, 170f, 36f), Triple(60f, 192f, 40f), Triple(50f, 142f, 46f))
private val LIKES = Size(118f, 32f)

// The seven-segment display: a glyph's box, the distance between slots, and the stroke.
private val GLYPH = Size(11f, 20f)
private const val GLYPH_PITCH = 16f
private const val DEGREE_X = 49f
private const val DEGREE_WIDTH = 6f
private const val UNIT_X = 60f
private const val SEGMENT = 1.8f
private const val SEGMENT_GAP = 1.6f
private val DISPLAY = Size(73f, 22f)

// Lit segments of 0…9, one bit each: a (top), b, c, d (bottom), e, f, g (middle).
private val DIGITS = intArrayOf(
    0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110, 0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111,
)
private const val MINUS = 0b1000000
private const val DEGREE = 0b1100011
private const val UNIT = 0b0111001

// A change (seconds from the tap): the photo wipes, the display ticks, the texts roll.
private const val WIPE_SECONDS = 0.75f

// Height of the soft band at the wipe's edge.
private const val FEATHER = 90f
private const val TICK_AT = 0.05f
private const val TICK_SECONDS = 0.8f
private const val NAME_AT = 0.1f
private const val REGION_AT = 0.16f
private const val FACTS_AT = 0.22f
private const val FACTS_GAP = 0.05f
private const val ROLL_SECONDS = 0.4f
private const val CHANGE_SECONDS = 0.9f

// The selection follows a spring of mass 1.
private const val PICK_STIFFNESS = 170f
private const val PICK_DAMPING = 20f

// Snow: how many flakes a resort gets for its temperature.
private const val FLAKES = 56
private const val FLAKES_AT_ZERO = 12f
private const val FLAKES_PER_DEGREE = 3f
private const val FLAKES_LEAST = 8f

// autoPlay (seconds): the next resort every LOOP / count, starting at FIRST.
private const val FIRST = 0.8f
private const val LOOP = 8.8f

// After a touch the loop waits this long, returns to the first resort, and starts over.
private const val IDLE = 2.5f
private const val SETTLE = 1.2f

private const val BACK_ICON = "M17 10 L3 10 M8.5 4.5 L3 10 L8.5 15.5"
private const val PIN_ICON =
    "M7 1.5 C9.5 1.5 11.5 3.5 11.5 6 C11.5 8.5 9.5 10.5 7 10.5 C4.5 10.5 2.5 8.5 2.5 6 C2.5 3.5 4.5 1.5 7 1.5 Z M7 10.5 L7 15"
private const val FLAKE_ICON = "M7 1 L7 13 M1.8 4 L12.2 10 M1.8 10 L12.2 4"
private const val HEART_ICON =
    "M6 10.6 C2.6 8.2 1 6.5 1 4.5 C1 3 2.2 1.9 3.6 1.9 C4.6 1.9 5.4 2.4 6 3.3 C6.6 2.4 7.4 1.9 8.4 1.9 C9.8 1.9 11 3 11 4.5 C11 6.5 9.4 8.2 6 10.6 Z"
private const val RUNS_ICON =
    "M11.5 3.2 C12.3 3.2 12.9 3.8 12.9 4.6 C12.9 5.4 12.3 6 11.5 6 C10.7 6 10.1 5.4 10.1 4.6 C10.1 3.8 10.7 3.2 11.5 3.2 Z M4.5 7 L8.5 8.6 L11.6 7.8 M8.5 8.6 L10.6 12.4 L7 13.6 M2.5 13 L14.5 17 M13.2 16.6 L15.8 15.6"
private const val DISTANCE_ICON =
    "M2 15.5 L7.2 6 L10.2 11 L12 8.5 L16 15.5 Z M13.6 2.6 C14.6 2.6 15.4 3.4 15.4 4.4 C15.4 5.4 14.6 6.2 13.6 6.2 C12.6 6.2 11.8 5.4 11.8 4.4 C11.8 3.4 12.6 2.6 13.6 2.6 Z"
private const val LIFTS_ICON =
    "M3.5 4.5 L14.5 4.5 C15.6 4.5 16 5 16 6 L16 12 C16 13 15.6 13.5 14.5 13.5 L3.5 13.5 C2.4 13.5 2 13 2 12 L2 6 C2 5 2.4 4.5 3.5 4.5 Z M2 8 L16 8"

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun resortText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val CountryStyle = resortText(20f, 24f, FontWeight.Medium, Color.White)
private val MapStyle = resortText(13f, 16f, FontWeight.Medium, Color.White)
private val TemperatureStyle = resortText(10f, 13f, FontWeight.SemiBold, Color.White)
private val NoteStyle = resortText(10f, 13f, FontWeight.Normal, Color(0xFF8D9096))
private val LikesStyle = resortText(13f, 18f, FontWeight.Medium, Color.White)
private val NameStyle = resortText(23f, 30f, FontWeight.SemiBold, Color(0xFF1D1F22), -0.3f).copy(textAlign = TextAlign.Center)
private val RegionStyle = resortText(14f, 20f, FontWeight.Normal, Color(0xFF86898D)).copy(textAlign = TextAlign.Center)
private val FactStyle = resortText(12.5f, 18f, FontWeight.Medium, Fact)

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
 * @param x Where it starts across the photo, and [start] down its fall, both 0…1.
 * @param falls Whole falls and [swings] whole swings per loop, so every flake is back where it began when the loop ends.
 */
private class Flake(
    val x: Float,
    val start: Float,
    val falls: Float,
    val swings: Float,
    val sway: Float,
    val radius: Float,
    val phase: Float,
)

/** The flakes, from a fixed sequence (Park–Miller): the same snowfall every time. */
private val Snow: List<Flake> = run {
    var seed = 11L
    fun next(): Float {
        seed = seed * 16807L % 2147483647L
        return (seed / 2147483647.0).toFloat()
    }
    List(FLAKES) {
        Flake(
            x = next(), start = next(), falls = 1f + floor(next() * 3f), swings = 2f + floor(next() * 4f),
            sway = 3f + next() * 7f, radius = 0.8f + next() * 1.2f, phase = next() * TAU,
        )
    }
}

/**
 * How far the text of resort [i] has rolled, [t] seconds into its turn: the shift is in line
 * heights, below the line while arriving and above it once it has left. Returns shift and opacity.
 */
private fun rollOf(i: Int, from: Int, to: Int, t: Float): Pair<Float, Float> {
    val p = (t / ROLL_SECONDS).coerceIn(0f, 1f)
    val e = 1f - (1f - p).pow(3)
    return when (i) {
        to -> (1f - e) to (if (from == to) 1f else e)
        from -> -e to (1f - e)
        else -> 0f to 0f
    }
}

/** The number on the display [t] seconds into a change: it counts through every degree in between, slowing down. */
private fun degreesOf(from: Int, to: Int, t: Float): Int {
    val p = ((t - TICK_AT) / TICK_SECONDS).coerceIn(0f, 1f)
    val e = 1f - (1f - p) * (1f - p)
    return (from + (to - from) * e).roundToInt()
}

private fun flakeCount(temperature: Int) =
    min(FLAKES.toFloat(), max(FLAKES_LEAST, FLAKES_AT_ZERO - temperature * FLAKES_PER_DEGREE))

/** Adds the segments of a glyph whose box starts at [x] that [mask] lights, in the order a, b, c, d, e, f, g. */
private fun Path.addSegments(x: Float, width: Float, mask: Int) {
    val top = 1f
    val bottom = top + GLYPH.height
    val middle = (top + bottom) / 2f
    val left = x + 1f
    val right = left + width
    val gap = SEGMENT_GAP
    val segments = arrayOf(
        floatArrayOf(left + gap, top, right - gap, top),
        floatArrayOf(right, top + gap, right, middle - gap),
        floatArrayOf(right, middle + gap, right, bottom - gap),
        floatArrayOf(left + gap, bottom, right - gap, bottom),
        floatArrayOf(left, middle + gap, left, bottom - gap),
        floatArrayOf(left, top + gap, left, middle - gap),
        floatArrayOf(left + gap, middle, right - gap, middle),
    )
    for (bit in 0 until 7) {
        if (mask and (1 shl bit) == 0) continue
        moveTo(segments[bit][0], segments[bit][1])
        lineTo(segments[bit][2], segments[bit][3])
    }
}

/**
 * A ski resort on a light card inside a near-black panel: a strip of thumbnails picks the resort,
 * and the card shows its photo under a little snowfall, the temperature on a seven-segment
 * display, a cluster of fans, the name and three facts. Picking another resort wipes its photo in
 * from the top, ticks the display through the degrees in between and rolls the texts.
 *
 * @param fans The three round badges on the photo, smallest first.
 * @param autoPlay Picks the next resort by itself every 2.2 seconds.
 * @param onSelect Called with the index of the resort a thumbnail picks.
 */
@Composable
fun ResortWeatherCard(
    resorts: List<ResortWeatherCardResort> = ResortWeatherCardResorts,
    fans: List<ResortWeatherCardFan> = ResortWeatherCardFans,
    autoPlay: Boolean = false,
    country: String = "Austria",
    mapLabel: String = "Map",
    temperatureLabel: String = "Temperature",
    temperatureNote: String = "Currently",
    onSelect: ((index: Int) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onMap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val count = resorts.size
    if (count == 0) return
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Seconds since the card appeared: the clock the snow falls on.
    val time = remember { mutableFloatStateOf(0f) }
    // The resort that is leaving, the one that is arriving, and when that started.
    var from by remember { mutableIntStateOf(0) }
    var to by remember { mutableIntStateOf(0) }
    val changedAt = remember { mutableFloatStateOf(-CHANGE_SECONDS) }
    // The resort on show, as a number on a spring: thumbnails and page dots size themselves by it.
    val sel = remember { mutableFloatStateOf(0f) }
    val picking = remember { arrayOfNulls<Job>(1) }
    // Counts touches: a change restarts the autoPlay loop after a pause.
    var touches by remember { mutableIntStateOf(0) }

    // Seconds since the resort changed. Under reduced motion a change is a jump and the snow stands still.
    fun sinceChange() = if (reduced) CHANGE_SECONDS else min(CHANGE_SECONDS, time.floatValue - changedAt.floatValue)

    fun select(index: Int): Boolean {
        if (index == to || index !in 0 until count) return false
        from = to
        to = index
        changedAt.floatValue = time.floatValue
        picking[0]?.cancel()
        if (reduced) {
            sel.floatValue = index.toFloat()
        } else {
            picking[0] = scope.launch {
                animate(
                    sel.floatValue, index.toFloat(),
                    animationSpec = spring(PICK_DAMPING / (2f * sqrt(PICK_STIFFNESS)), PICK_STIFFNESS),
                ) { value, _ -> sel.floatValue = value }
            }
        }
        return true
    }

    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            time.floatValue += min((frame - last) / 1e9f, 0.064f)
            last = frame
        }
    }

    LaunchedEffect(auto, touches, count) {
        if (!auto || count < 2) return@LaunchedEffect
        if (touches > 0) {
            // After a touch the loop waits, returns to the first resort and starts over.
            delay((IDLE * 1000).toLong())
            if (select(0)) delay((SETTLE * 1000).toLong())
        }
        val gap = LOOP / count
        while (true) {
            for (i in 1..count) {
                delay(((if (i == 1) FIRST else gap) * 1000).toLong())
                select(i % count)
            }
            delay(((gap - FIRST) * 1000).toLong())
        }
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .background(Color(0xFF0F1114), RoundedCornerShape(RADIUS.dp))
            .border(1.dp, rgba(255, 255, 255, 0.07f), RoundedCornerShape(RADIUS.dp)),
    ) {
        Row(
            Modifier.offset(20.dp, (HEADER_Y - 15f).dp).size((WIDTH - 40f).dp, 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .height(30.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onBack?.invoke() }
                    .semantics { contentDescription = "Back, $country" },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ResortIcon(BACK_ICON, 20f, 20f, Color.White, 1.7f)
                BasicText(country, style = CountryStyle, maxLines = 1)
            }
            Row(
                Modifier
                    .size(74.dp, 30.dp)
                    .background(rgba(255, 255, 255, 0.1f), CircleShape)
                    .border(1.dp, rgba(255, 255, 255, 0.28f), CircleShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onMap?.invoke() },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ResortIcon(PIN_ICON, 14f, 16f, Color.White, 1.4f)
                BasicText(mapLabel, style = MapStyle, maxLines = 1)
            }
        }

        // The strip: the selected thumbnail is larger and outlined, and its neighbours make room.
        Row(
            Modifier.offset(0.dp, (THUMB_Y - THUMB_PICKED / 2f).dp).size(WIDTH.dp, THUMB_PICKED.dp),
            horizontalArrangement = Arrangement.spacedBy(THUMB_GAP.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            resorts.forEachIndexed { index, resort ->
                fun weight() = max(0f, 1f - abs(index - sel.floatValue))
                Box(
                    Modifier
                        .layout { measurable, _ ->
                            val side = (THUMB + (THUMB_PICKED - THUMB) * weight()).dp.roundToPx()
                            val placeable = measurable.measure(Constraints.fixed(side, side))
                            layout(side, side) { placeable.place(0, 0) }
                        }
                        .graphicsLayer {
                            alpha = 0.6f + 0.4f * weight()
                            shape = RoundedCornerShape((THUMB_RADIUS + (THUMB_PICKED_RADIUS - THUMB_RADIUS) * weight()).dp)
                            clip = true
                        }
                        .background(resort.tint)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                            touches++
                            if (select(index)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onSelect?.invoke(index)
                            }
                        }
                        .semantics {
                            contentDescription = resort.name
                            selected = index == to
                        }
                        .drawWithContent {
                            drawContent()
                            val radius = (THUMB_RADIUS + (THUMB_PICKED_RADIUS - THUMB_RADIUS) * weight()).dp.toPx()
                            val line = 2.dp.toPx()
                            drawRoundRect(
                                rgba(255, 255, 255, 0.5f),
                                topLeft = Offset(line / 2f, line / 2f),
                                size = Size(size.width - line, size.height - line),
                                cornerRadius = CornerRadius(radius - line / 2f),
                                alpha = weight(),
                                style = Stroke(line),
                            )
                        },
                ) {
                    ResortPhoto(resort.thumbnail, Modifier.fillMaxSize())
                }
            }
        }

        Box(
            Modifier
                .offset(((WIDTH - CARD.width) / 2f).dp, CARD_TOP.dp)
                .size(CARD.width.dp, CARD.height.dp)
                .background(Color(0xFFE9E9E9), RoundedCornerShape(CARD_RADIUS.dp)),
        ) {
            // The photo area: the arriving photo wiped in over the leaving one, the snow, and what sits on top.
            Box(
                Modifier
                    .offset(PHOTO.left.dp, PHOTO.top.dp)
                    .size(PHOTO.width.dp, PHOTO.height.dp)
                    .clip(RoundedCornerShape(PHOTO_RADIUS.dp)),
            ) {
                resorts.forEachIndexed { index, resort ->
                    ResortPhoto(
                        resort.photo,
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (index == to) 1f else 0f)
                            .graphicsLayer {
                                alpha = if (index == to || index == from) 1f else 0f
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .background(resort.tint)
                            // Only the arriving photo is cut by the wipe; the leaving one lies whole under it.
                            .drawWithContent {
                                drawContent()
                                if (index != to) return@drawWithContent
                                val p = (sinceChange() / WIPE_SECONDS).coerceIn(0f, 1f)
                                val wipe = if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).pow(3) / 2f
                                // The wipe's lower edge runs from the top to past the bottom, with a soft band above it.
                                val edge = (wipe * (PHOTO.height + FEATHER)).dp.toPx()
                                drawRect(
                                    Brush.verticalGradient(
                                        listOf(Color.Black, Color.Transparent),
                                        startY = edge - FEATHER.dp.toPx(),
                                        endY = edge,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    )
                }

                // Light snow over the photo: the same flakes on the same paths every LOOP seconds;
                // the colder the resort, the more of them are let in.
                Canvas(Modifier.fillMaxSize().zIndex(2f)) {
                    surface(0.dp) { _, _, _, _ ->
                        val shown = (sinceChange() / CHANGE_SECONDS).coerceIn(0f, 1f)
                        val before = flakeCount(resorts[from].temperature)
                        val flakes = before + (flakeCount(resorts[to].temperature) - before) * shown
                        val turn = time.floatValue % LOOP / LOOP
                        Snow.forEachIndexed { i, flake ->
                            val grown = (flakes - i).coerceIn(0f, 1f)
                            if (grown > 0f) {
                                val fall = (flake.start + flake.falls * turn) % 1f
                                val x = flake.x * PHOTO.width + flake.sway * sin(TAU * flake.swings * turn + flake.phase)
                                drawCircle(
                                    Color.White,
                                    radius = flake.radius * grown,
                                    center = Offset(x, -6f + fall * (PHOTO.height + 12f)),
                                    alpha = if (flake.radius < 1.2f) 0.5f else if (flake.radius < 1.6f) 0.7f else 0.9f,
                                )
                            }
                        }
                    }
                }

                // The temperature: a black pill with a seven-segment display and its label.
                Row(
                    Modifier
                        .zIndex(3f)
                        .offset(((PHOTO.width - PILL.width) / 2f).dp, 12.dp)
                        .size(PILL.width.dp, PILL.height.dp)
                        .background(Color(0xFF0B0D10), RoundedCornerShape(14.dp))
                        .padding(start = 16.dp)
                        .clearAndSetSemantics { contentDescription = "$temperatureLabel ${resorts[to].temperature} °C" },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ghost = remember { Path().apply { for (slot in 0 until 3) addSegments(slot * GLYPH_PITCH, GLYPH.width, 0b1111111) } }
                    val lit = remember { Path() }
                    // Three slots for the sign and the digits, then a degree sign and a C, each drawn from seven segments.
                    Canvas(Modifier.size(DISPLAY.width.dp, DISPLAY.height.dp)) {
                        surface(0.dp) { _, _, _, _ ->
                            val degrees = degreesOf(resorts[from].temperature, resorts[to].temperature, sinceChange())
                            val size = abs(degrees)
                            lit.rewind()
                            lit.addSegments(0f, GLYPH.width, if (size >= 10 && degrees < 0) MINUS else 0)
                            lit.addSegments(GLYPH_PITCH, GLYPH.width, if (size >= 10) DIGITS[size / 10 % 10] else if (degrees < 0) MINUS else 0)
                            lit.addSegments(GLYPH_PITCH * 2f, GLYPH.width, DIGITS[size % 10])
                            lit.addSegments(DEGREE_X, DEGREE_WIDTH, DEGREE)
                            lit.addSegments(UNIT_X, GLYPH.width, UNIT)
                            // The three slots also show their unlit segments, faintly, like a real display.
                            drawPath(ghost, rgba(255, 255, 255, 0.1f), style = Stroke(SEGMENT, cap = StrokeCap.Round))
                            drawPath(lit, Color.White, style = Stroke(SEGMENT, cap = StrokeCap.Round))
                        }
                    }
                    Column {
                        BasicText(temperatureLabel, style = TemperatureStyle, maxLines = 1)
                        BasicText(temperatureNote, style = NoteStyle, maxLines = 1)
                    }
                }

                fans.take(FAN_SPOTS.size).forEachIndexed { index, fan ->
                    val (x, y, side) = FAN_SPOTS[index]
                    Box(Modifier.zIndex(3f).offset(x.dp, y.dp).size(side.dp).clearAndSetSemantics {}) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(fan.color, CircleShape)
                                .border(2.dp, rgba(255, 255, 255, 0.9f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                fan.initials,
                                style = resortText(side * 0.34f, side * 0.44f, FontWeight.Bold, Color.White),
                                maxLines = 1,
                            )
                        }
                        Box(
                            Modifier.align(Alignment.BottomEnd).offset(3.dp, 2.dp).graphicsLayer {
                                scaleX = 1.2f
                                scaleY = 1.2f
                            },
                        ) {
                            ResortIcon(HEART_ICON, 12f, 12f, Color(0xFFF08A3C), 0f, filled = true)
                        }
                    }
                }

                Row(
                    Modifier
                        .zIndex(3f)
                        .offset((PHOTO.width - 12f - LIKES.width).dp, (PHOTO.height - 12f - LIKES.height).dp)
                        .size(LIKES.width.dp, LIKES.height.dp)
                        .background(rgba(28, 32, 36, 0.5f), CircleShape)
                        .border(1.dp, rgba(255, 255, 255, 0.3f), CircleShape),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ResortIcon(FLAKE_ICON, 14f, 14f, Color.White, 1.4f)
                    ResortRoll(resorts.map { it.likes }, LikesStyle, 18f, Modifier.width(78.dp)) { index ->
                        roll(rollOf(index, from, to, sinceChange() - NAME_AT), 18f)
                    }
                }
            }

            // Page dots: the current one is a short bar.
            Row(
                Modifier.offset(0.dp, (DOTS_Y - 2f).dp).size(CARD.width.dp, 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                for (index in resorts.indices) {
                    fun weight() = max(0f, 1f - abs(index - sel.floatValue))
                    Box(
                        Modifier
                            .layout { measurable, _ ->
                                val width = (4f + 10f * weight()).dp.roundToPx()
                                val placeable = measurable.measure(Constraints.fixed(width, 4.dp.roundToPx()))
                                layout(width, placeable.height) { placeable.place(0, 0) }
                            }
                            .graphicsLayer { alpha = 0.3f + 0.4f * weight() }
                            .background(Color(0xFF1D1F22), CircleShape),
                    )
                }
            }

            ResortRoll(resorts.map { it.name }, NameStyle, 30f, Modifier.offset(0.dp, (NAME_Y - 15f).dp).width(CARD.width.dp)) { index ->
                roll(rollOf(index, from, to, sinceChange() - NAME_AT), 30f)
            }
            ResortRoll(resorts.map { it.region }, RegionStyle, 20f, Modifier.offset(0.dp, (REGION_Y - 10f).dp).width(CARD.width.dp)) { index ->
                roll(rollOf(index, from, to, sinceChange() - REGION_AT), 20f)
            }

            Row(
                Modifier
                    .offset(FACTS.left.dp, FACTS.top.dp)
                    .size(FACTS.width.dp, FACTS.height.dp)
                    .background(Color(0xFFD8D8D8), CircleShape)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val columns = listOf(
                    Triple(RUNS_ICON, 78f, resorts.map { it.runs }),
                    Triple(DISTANCE_ICON, 56f, resorts.map { it.distance }),
                    Triple(LIFTS_ICON, 56f, resorts.map { it.lifts }),
                )
                columns.forEachIndexed { j, (icon, width, texts) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        ResortIcon(icon, 18f, 18f, Fact, 1.4f)
                        ResortRoll(texts, FactStyle, 18f, Modifier.width(width.dp)) { index ->
                            roll(rollOf(index, from, to, sinceChange() - FACTS_AT - j * FACTS_GAP), 18f)
                        }
                    }
                }
            }
        }
    }
}

private fun GraphicsLayerScope.roll(roll: Pair<Float, Float>, height: Float) {
    translationY = (roll.first * height).dp.toPx()
    alpha = roll.second
}

/**
 * One line of text per resort in the same place: the leaving one rolls up and out while the
 * arriving one rolls in from below. [layer] places the line of the resort with the given index.
 */
@Composable
private fun ResortRoll(texts: List<String>, style: TextStyle, height: Float, modifier: Modifier, layer: GraphicsLayerScope.(Int) -> Unit) {
    Box(modifier.height(height.dp).clip(RectangleShape)) {
        texts.forEachIndexed { index, text ->
            BasicText(
                text,
                style = style,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxSize().graphicsLayer { layer(index) },
            )
        }
    }
}

/** An icon from an SVG path in a box of [width] × [height] dp, stroked or filled. */
@Composable
private fun ResortIcon(svg: String, width: Float, height: Float, color: Color, stroke: Float, filled: Boolean = false) {
    val path = remember(svg) { svgPath(svg) }
    Box(
        Modifier.size(width.dp, height.dp).drawBehind {
            surface(0.dp) { _, _, _, _ ->
                if (filled) {
                    drawPath(path, color)
                } else {
                    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        },
    )
}

/** A remote photo cropped to fill; the caller paints the tint shown until it arrives. */
@Composable
private fun ResortPhoto(url: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current).data(url).crossfade(200).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

private fun photoUrl(id: String, width: Int) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=$width&q=80"

val ResortWeatherCardFans = listOf(
    ResortWeatherCardFan("LM", Color(0xFF6F8FD8)),
    ResortWeatherCardFan("AK", Color(0xFFD99A4E)),
    ResortWeatherCardFan("JT", Color(0xFF58A98C)),
)

val ResortWeatherCardResorts = listOf(
    ResortWeatherCardResort(
        name = "Obertauern Ski Resort", region = "Austria, Salzburg", temperature = -8, likes = "198 frosties",
        runs = "26 ski runs", distance = "112 km", lifts = "8 types",
        photo = photoUrl("1454496522488-7a8e488e8606", 800), thumbnail = photoUrl("1454496522488-7a8e488e8606", 160),
        tint = Color(0xFF8EA3BF),
    ),
    ResortWeatherCardResort(
        name = "St. Anton Ski Resort", region = "Austria, Tyrol", temperature = -14, likes = "342 frosties",
        runs = "88 ski runs", distance = "305 km", lifts = "9 types",
        photo = photoUrl("1519681393784-d120267933ba", 800), thumbnail = photoUrl("1519681393784-d120267933ba", 160),
        tint = Color(0xFF3A4A78),
    ),
    ResortWeatherCardResort(
        name = "Kitzsteinhorn Glacier", region = "Austria, Salzburg", temperature = 2, likes = "87 frosties",
        runs = "17 ski runs", distance = "61 km", lifts = "5 types",
        photo = photoUrl("1506905925346-21bda4d32df4", 800), thumbnail = photoUrl("1506905925346-21bda4d32df4", 160),
        tint = Color(0xFF7C8FB0),
    ),
    ResortWeatherCardResort(
        name = "Hintertux Ski Resort", region = "Austria, Tyrol", temperature = -11, likes = "256 frosties",
        runs = "34 ski runs", distance = "196 km", lifts = "7 types",
        photo = photoUrl("1531366936337-7c912a4589a7", 800), thumbnail = photoUrl("1531366936337-7c912a4589a7", 160),
        tint = Color(0xFF2F5A6E),
    ),
)
