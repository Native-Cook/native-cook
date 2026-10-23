package com.ngocdevv.nativecook.blocks.cards.trailroutecard

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val WIDTH = 340f
private const val HEIGHT = 368f
private const val RADIUS = 34f
private val Ink = Color(0xFF151515)
private val Mute = Color(0xFF9A9A9A)

// The card's canvas overflows it by this much on every side to fit its shadow.
private val BLEED = 100.dp

private const val PHOTO_INSET = 11f
private const val PHOTO_WIDTH = WIDTH - PHOTO_INSET * 2f
private const val PHOTO_HEIGHT = 240f
private const val PHOTO_RADIUS = 26f
private const val TITLE_LEFT = 30f
private const val TITLE_TOP = 194f
private const val PILL_LEFT = 168f
private const val PILL_TOP = 193f
private const val PILL_WIDTH = 138f
private const val PILL_HEIGHT = 36f
private const val PILL_RADIUS = 12f
private const val PRESS_SCALE = 0.96f

// How fast the pill follows the loop's finger, per second.
private const val PRESS_RATE = 22f

private const val LEFT = 25f
private const val DIFFICULTY_TOP = 270f
private const val BARS_GAP = 7f
private const val BAR_WIDTH = 3.5f
private const val BAR_SPACING = 2.5f
private val BAR_HEIGHTS = floatArrayOf(6f, 9f, 12f)

// How much taller a bar gets for a moment as it lights.
private const val BAR_BUMP = 0.3f

// Progress along the trail at which each bar lights, and the share of it the fade takes.
private val BAR_AT = floatArrayOf(0.06f, 0.42f, 0.78f)
private const val BAR_FADE = 0.04f
private const val NOTE_TOP = 290f
private const val RULE_TOP = 311f
private const val STATS_TOP = 319f
private const val STATS_WIDTH = 171f
private val STAT_LEFTS = floatArrayOf(25f, 88f, 146f)

// Heights of the trail at even steps along it, 0…1.
private val PROFILE = floatArrayOf(0.1f, 0.22f, 0.18f, 0.4f, 0.55f, 0.48f, 0.72f, 0.9f, 1f, 0.7f, 0.46f, 0.3f)
private const val PROFILE_BASE = 352f
private const val PROFILE_HEIGHT = 38f

// A silhouette without an outline: a line through the labels would read as struck-out text.
private const val PROFILE_TOP = 0.3f
private const val PROFILE_BOTTOM = 0.05f

private const val MAP_LEFT = 220f
private const val MAP_TOP = 269f
private const val MAP_WIDTH = 104f
private const val MAP_HEIGHT = 81f
private const val MAP_RADIUS = 26f
private val MapColor = Color(0xFFF3F3F3)

// The route in the map's own points: its start, then three points for each cubic curve.
private val ROUTE = floatArrayOf(
    24f, 54f,
    16f, 50f, 16f, 44f, 22f, 40f,
    28f, 34f, 30f, 24f, 40f, 26f,
    48f, 28f, 48f, 36f, 56f, 34f,
    64f, 32f, 68f, 26f, 76f, 30f,
    84f, 34f, 88f, 40f, 84f, 44f,
    80f, 50f, 74f, 58f, 66f, 56f,
    58f, 54f, 52f, 48f, 44f, 52f,
)

// Straight pieces each curve is cut into.
private const val ROUTE_STEPS = 16
private const val ROUTE_STROKE = 1.8f
private const val ROUTE_FAINT = 0.14f
private const val DOT_RADIUS = 3.2f
private const val DOT_RING = 4.8f
private const val FLAG_POLE = 13f
private const val FLAG_WIDTH = 8f
private const val FLAG_DROP = 6f

// A walk, in seconds from the tap: the figures drop to zero, the trail is walked, the flag pops,
// and after a look at the result the route fades back to its outline.
private const val HIKE_AT = 0.1f
private const val HIKE_TIME = 4f

// Share of the walk spent speeding up, and again slowing down.
private const val PACE_RAMP = 0.15f

// The flag goes up as the dot comes to a stop.
private const val FLAG_AT = 3.8f
private const val RESET_AT = 5.4f
private const val RESET_TIME = 0.7f
private const val RUN_TIME = RESET_AT + RESET_TIME
private const val INK_FADE = 0.25f
private const val RISE_TIME = 0.6f

// The figures blink down to this opacity as they restart from zero.
private const val STATS_DIP = 0.35f
private const val STATS_FADE = 0.3f
private const val ZOOM = 0.08f

// The flag's pop: a spring from 0 to 1.
private const val FLAG_MASS = 1f
private const val FLAG_STIFFNESS = 260f
private const val FLAG_DAMPING = 14f

// autoPlay loop (seconds): a finger presses the pill, lifts, and the walk plays out.
private const val PRESS_AT = 0.8f
private const val PRESS_TIME = 0.14f
private const val LOOP = 8f

// After a walk started by hand the loop waits this long before it restarts.
private const val USER_HOLD = 2f

// The figures are redrawn when the share of their full values moves by one of these steps.
private const val SHARE_STEPS = 2000

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun trailText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = trailText(13.5f, 18f, FontWeight.Medium, Color.White)
private val RangeStyle = trailText(10.5f, 14f, FontWeight.Normal, rgba(255, 255, 255, 0.6f))
private val PillStyle = trailText(14f, 18f, FontWeight.Medium, Color.White, 0.4f)
private val DifficultyStyle = trailText(14f, 18f, FontWeight.SemiBold, Ink)
private val NoteStyle = trailText(10.5f, 14f, FontWeight.Normal, Mute)
private val StatValueStyle = trailText(14f, 18f, FontWeight.Medium, Ink)
private val StatLabelStyle = trailText(9.5f, 12f, FontWeight.Normal, Mute)

/** The route as a polyline: each curve of [ROUTE] cut into short straight pieces. */
private val RoutePoints: List<Offset> = buildList {
    add(Offset(ROUTE[0], ROUTE[1]))
    var index = 2
    while (index + 5 < ROUTE.size) {
        val from = last()
        for (step in 1..ROUTE_STEPS) {
            val t = step.toFloat() / ROUTE_STEPS
            val u = 1f - t
            add(
                Offset(
                    u * u * u * from.x + 3f * u * u * t * ROUTE[index] + 3f * u * t * t * ROUTE[index + 2] + t * t * t * ROUTE[index + 4],
                    u * u * u * from.y + 3f * u * u * t * ROUTE[index + 1] + 3f * u * t * t * ROUTE[index + 3] + t * t * t * ROUTE[index + 5],
                ),
            )
        }
        index += 6
    }
}

/** Length of the route up to each of its points. */
private val RouteLengths: FloatArray = FloatArray(RoutePoints.size).also { lengths ->
    for (index in 1 until RoutePoints.size) {
        val step = RoutePoints[index] - RoutePoints[index - 1]
        lengths[index] = lengths[index - 1] + hypot(step.x, step.y)
    }
}

/** Rewrites the path as the route from its start to [progress] of its length; returns the point it reaches. */
private fun Path.routeLine(progress: Float): Offset {
    val reach = progress.coerceIn(0f, 1f) * RouteLengths.last()
    rewind()
    var end = RoutePoints[0]
    moveTo(end.x, end.y)
    for (index in 1 until RoutePoints.size) {
        if (RouteLengths[index] <= reach) {
            end = RoutePoints[index]
        } else {
            val t = (reach - RouteLengths[index - 1]) / (RouteLengths[index] - RouteLengths[index - 1])
            end = RoutePoints[index - 1] + (RoutePoints[index] - RoutePoints[index - 1]) * t
        }
        lineTo(end.x, end.y)
        if (RouteLengths[index] > reach) break
    }
    return end
}

private fun smooth(t: Float): Float {
    val p = t.coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}

private fun easeOut(t: Float): Float {
    val p = 1f - t.coerceIn(0f, 1f)
    return 1f - p * p * p
}

/** The walk's pace, 0…1: a short run-up, a steady stride and a short slow-down. */
private fun pace(t: Float): Float {
    val p = t.coerceIn(0f, 1f)
    return when {
        p < PACE_RAMP -> p * p / (2f * PACE_RAMP * (1f - PACE_RAMP))
        p > 1f - PACE_RAMP -> 1f - (1f - p) * (1f - p) / (2f * PACE_RAMP * (1f - PACE_RAMP))
        else -> (p - PACE_RAMP / 2f) / (1f - PACE_RAMP)
    }
}

/** A spring let go at 0 and settling at 1, [t] seconds on: it overshoots by about a fifth. */
private fun pop(t: Float): Float {
    if (t <= 0f) return 0f
    val decay = FLAG_DAMPING / (2f * FLAG_MASS)
    val omega = sqrt(FLAG_STIFFNESS / FLAG_MASS - decay * decay)
    return 1f - exp(-decay * t) * (cos(omega * t) + decay / omega * sin(omega * t))
}

/** Share of the whole climb done at [progress] along the trail: only the way up counts. */
private fun climbShare(progress: Float): Float {
    val at = progress.coerceIn(0f, 1f) * (PROFILE.size - 1)
    var climbed = 0f
    var total = 0f
    for (index in 0 until PROFILE.size - 1) {
        val gain = max(0f, PROFILE[index + 1] - PROFILE[index])
        total += gain
        climbed += gain * (at - index).coerceIn(0f, 1f)
    }
    return climbed / total
}

private fun distanceText(km: Float): String {
    val tenths = (km * 10f).roundToInt()
    return "${tenths / 10}.${tenths % 10}km"
}

private fun durationText(minutes: Int) = "${minutes / 60}h ${minutes % 60}m"

/** Everything the card draws, worked out from how far into the walk it is; [run] is negative at rest. */
private class TrailState(run: Float, reduced: Boolean) {
    val running = run >= 0f
    private val reset = if (running) smooth((run - RESET_AT) / RESET_TIME) else 1f

    /** 0…1 along the trail. Under reduced motion nothing travels: the finished route fades in, then out. */
    val progress = if (!running) 0f else if (reduced) 1f else pace((run - HIKE_AT) / HIKE_TIME)

    /** Share of the figures shown: they stand at their full values at rest. */
    val share = if (running) progress else 1f
    val statsOpacity = if (!running || reduced) 1f else STATS_DIP + (1f - STATS_DIP) * smooth(run / STATS_FADE)

    /** Opacity of what the walk draws: the black route and the elevation profile. */
    val ink = if (running) smooth(run / INK_FADE) * (1f - reset) else 0f

    /** 0…1: the profile's height as it rises from its baseline. */
    val rise = if (!running || reduced) 1f else easeOut(run / RISE_TIME)
    val dot = abs(1f - 2f * reset)
    val dotAtStart = reset >= 0.5f

    /** Scale of the flag, past 1 while it pops. */
    val flag = if (!running) 0f else (if (reduced) smooth(run / INK_FADE) else pop(run - FLAG_AT)) * (1f - reset)
    val zoom = if (!running || reduced) 1f else 1f + ZOOM * progress * (1f - reset)

    /** 0…1 for a difficulty bar: all lit at rest, lighting one by one along the walk. */
    fun barLight(bar: Int) = if (running) ((progress - BAR_AT[bar]) / BAR_FADE).coerceIn(0f, 1f) else 1f
}

private class TrailSim {
    var clock = 0f

    /** The loop's own finger is on the pill. */
    var scripted = false

    /** A touch started this walk: the loop waits for it to end, then [hold] more seconds. */
    var touched = false
    var hold = 0f

    /** Seconds into the walk, or -1 at rest. */
    val run = mutableFloatStateOf(-1f)

    /** 0…1: how far the loop's finger has pressed the pill in. */
    val press = mutableFloatStateOf(0f)
}

/**
 * A trail card: a mountain photo with the trail's name and a frosted "Directions" pill on its dark
 * lower band, then the difficulty, three figures and a small drawing of the route. Tapping the
 * pill walks the trail: the route draws itself with a dot travelling along it, the figures count
 * up in step, an elevation profile rises behind them, the photo slowly zooms and a flag pops at
 * the end, before the route fades back to its faint outline.
 *
 * @param autoPlay Walks the trail by itself every 8 s.
 * @param photo Address of a landscape photo, about 4 : 3.
 * @param photoTint Flat colour shown until the photo arrives.
 * @param level How many of the three difficulty bars are lit, 0…3.
 * @param elevationM Metres climbed over the whole trail.
 * @param accent Colour of the lit bars, the elevation profile and the flag.
 */
@Composable
fun TrailRouteCard(
    autoPlay: Boolean = false,
    title: String = "Embercrest Ridge",
    range: String = "Silverpine Mountains",
    photo: String = "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=636&h=480&q=80",
    photoTint: Color = Color(0xFF656778),
    buttonLabel: String = "Directions",
    difficulty: String = "Hard",
    level: Int = 3,
    note: String = "First climbed in 1886",
    distanceKm: Float = 12.4f,
    distanceLabel: String = "Distance",
    elevationM: Int = 870,
    elevationLabel: String = "Elevation",
    durationMin: Int = 285,
    durationLabel: String = "Duration",
    accent: Color = Color(0xFFE0672F),
    onDirections: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val view = LocalView.current
    val sim = remember { TrailSim() }
    val farShadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.1f), sigma = 30f) }
    val nearShadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.04f), sigma = 4f) }
    val walked = remember { Path() }
    val area = remember { Path() }
    val outline = remember { Path().apply { routeLine(1f) } }
    val cloth = remember {
        val end = RoutePoints.last()
        Path().apply {
            moveTo(end.x, end.y - FLAG_POLE)
            lineTo(end.x + FLAG_WIDTH, end.y - FLAG_POLE + FLAG_DROP / 2f)
            lineTo(end.x, end.y - FLAG_POLE + FLAG_DROP)
            close()
        }
    }

    fun state() = TrailState(sim.run.floatValue, reducedMotion)

    LaunchedEffect(auto) {
        sim.clock = 0f
        sim.scripted = false
        sim.touched = false
        if (auto) sim.run.floatValue = -1f
    }

    LaunchedEffect(reducedMotion) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            if (dt <= 0f) continue
            var run = sim.run.floatValue
            if (run >= 0f) {
                val now = run + dt
                // The flag of a walk started by hand lands with a haptic.
                val flagAt = if (reducedMotion) 0f else FLAG_AT
                if (sim.touched && run <= flagAt && now > flagAt) {
                    val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
                    view.performHapticFeedback(confirm)
                }
                run = if (now < RUN_TIME) now else -1f
            }
            if (sim.touched) {
                if (run < 0f) {
                    sim.hold -= dt
                    if (sim.hold <= 0f) {
                        sim.touched = false
                        sim.clock = 0f
                    }
                }
            } else if (looping) {
                var t = sim.clock + dt
                if (t >= LOOP) t -= LOOP
                sim.clock = t
                val down = t >= PRESS_AT && t < PRESS_AT + PRESS_TIME
                // The loop's finger lifts: the walk starts, as it does after a tap.
                if (sim.scripted && !down && run < 0f) run = max(0f, t - PRESS_AT - PRESS_TIME)
                sim.scripted = down
            }
            sim.press.floatValue += ((if (sim.scripted) 1f else 0f) - sim.press.floatValue) * (1f - exp(-dt * PRESS_RATE))
            sim.run.floatValue = run
        }
    }

    val share by remember(reducedMotion) {
        derivedStateOf { (state().share * SHARE_STEPS).roundToInt().toFloat() / SHARE_STEPS }
    }
    val stats = listOf(
        distanceText(distanceKm * share) to distanceLabel,
        "${(elevationM * climbShare(share)).roundToInt()}m" to elevationLabel,
        durationText((durationMin * share).roundToInt()) to durationLabel,
    )

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    drawIntoCanvas {
                        farShadow.draw(it.nativeCanvas, x, y + 22f, w, h, RADIUS)
                        nearShadow.draw(it.nativeCanvas, x, y + 2f, w, h, RADIUS)
                    }
                }
            }
        }
        Box(Modifier.matchParentSize().background(Color.White, RoundedCornerShape(RADIUS.dp)))

        Box(
            Modifier
                .offset(PHOTO_INSET.dp, PHOTO_INSET.dp)
                .size(PHOTO_WIDTH.dp, PHOTO_HEIGHT.dp)
                .clip(RoundedCornerShape(PHOTO_RADIUS.dp))
                .background(photoTint)
                .clearAndSetSemantics {},
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(photo).crossfade(200).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val zoom = state().zoom
                    scaleX = zoom
                    scaleY = zoom
                },
            )
            // The band the title and the pill sit on.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.42f to rgba(18, 15, 12, 0f), 0.68f to rgba(18, 15, 12, 0.6f), 1f to rgba(18, 15, 12, 0.94f)),
                ),
            )
        }

        Column(
            Modifier.offset(TITLE_LEFT.dp, TITLE_TOP.dp).width((PILL_LEFT - TITLE_LEFT - 8f).dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            BasicText(title, style = TitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(range, style = RangeStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        LaunchedEffect(pressed) {
            if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val pressScale by animateFloatAsState(
            targetValue = if (pressed) PRESS_SCALE else 1f,
            animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
            label = "press",
        )
        val pillShape = RoundedCornerShape(PILL_RADIUS.dp)
        Box(
            Modifier
                .offset(PILL_LEFT.dp, PILL_TOP.dp)
                .size(PILL_WIDTH.dp, PILL_HEIGHT.dp)
                .graphicsLayer {
                    val scale = pressScale * (1f - (1f - PRESS_SCALE) * sim.press.floatValue)
                    scaleX = scale
                    scaleY = scale
                }
                .background(rgba(255, 255, 255, 0.22f), pillShape)
                .border(1.dp, rgba(255, 255, 255, 0.12f), pillShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    // A tap on the pill: the walk starts unless one is under way, and the loop waits for it.
                    onDirections?.invoke()
                    if (sim.run.floatValue < 0f) {
                        sim.run.floatValue = 0f
                        sim.touched = true
                        sim.hold = USER_HOLD
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(buttonLabel, style = PillStyle, maxLines = 1)
        }

        Row(
            Modifier.offset(LEFT.dp, DIFFICULTY_TOP.dp).clearAndSetSemantics { contentDescription = difficulty },
            horizontalArrangement = Arrangement.spacedBy(BARS_GAP.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicText(difficulty, style = DifficultyStyle, maxLines = 1)
            Row(
                Modifier.padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(BAR_SPACING.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (bar in 0 until 3) {
                    // One difficulty bar: lit at rest, lighting in its turn along the walk.
                    Canvas(
                        Modifier.size(BAR_WIDTH.dp, BAR_HEIGHTS[bar].dp).graphicsLayer {
                            val light = if (bar < level) state().barLight(bar) else 0f
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleY = 1f + BAR_BUMP * sin(PI.toFloat() * light)
                        },
                    ) {
                        val light = if (bar < level) state().barLight(bar) else 0f
                        val corner = CornerRadius(1.dp.toPx())
                        drawRoundRect(rgba(0, 0, 0, 0.12f), cornerRadius = corner)
                        drawRoundRect(accent, cornerRadius = corner, alpha = light)
                    }
                }
            }
        }

        BasicText(
            note,
            style = NoteStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(LEFT.dp, NOTE_TOP.dp).width(STATS_WIDTH.dp),
        )
        Box(Modifier.offset(LEFT.dp, RULE_TOP.dp).size(STATS_WIDTH.dp, 1.dp).background(Color(0xFFE6E6E6)))

        // The climb so far, drawn behind the figures.
        Canvas(Modifier.offset(LEFT.dp, (PROFILE_BASE - PROFILE_HEIGHT - 2f).dp).size(STATS_WIDTH.dp, (PROFILE_HEIGHT + 2f).dp)) {
            val now = state()
            if (now.ink <= 0f) return@Canvas
            scale(density, density, pivot = Offset.Zero) {
                val base = PROFILE_HEIGHT + 2f
                val last = PROFILE.size - 1
                fun x(index: Int) = STATS_WIDTH * index / last
                fun y(index: Int) = base - PROFILE_HEIGHT * PROFILE[index] * now.rise
                area.rewind()
                area.moveTo(x(0), y(0))
                for (index in 1 until last) {
                    area.quadraticTo(x(index), y(index), (x(index) + x(index + 1)) / 2f, (y(index) + y(index + 1)) / 2f)
                }
                area.lineTo(x(last), y(last))
                area.lineTo(STATS_WIDTH, base)
                area.lineTo(0f, base)
                area.close()
                // Only the part already walked shows.
                clipRect(0f, 0f, STATS_WIDTH * now.progress, base) {
                    drawPath(
                        area,
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = PROFILE_TOP), accent.copy(alpha = PROFILE_BOTTOM)),
                            startY = base - PROFILE_HEIGHT,
                            endY = base,
                        ),
                        alpha = now.ink,
                    )
                }
            }
        }

        Box(
            Modifier.matchParentSize().clearAndSetSemantics {
                contentDescription = "${distanceText(distanceKm)}, ${elevationM}m, ${durationText(durationMin)}"
            },
        ) {
            stats.forEachIndexed { column, (value, label) ->
                Column(
                    Modifier.offset(STAT_LEFTS[column].dp, STATS_TOP.dp).graphicsLayer { alpha = state().statsOpacity },
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    BasicText(value, style = StatValueStyle, maxLines = 1)
                    BasicText(label, style = StatLabelStyle, maxLines = 1)
                }
            }
        }

        // The route's outline, the part already walked, the flag at its end and the dot.
        Canvas(
            Modifier
                .offset(MAP_LEFT.dp, MAP_TOP.dp)
                .size(MAP_WIDTH.dp, MAP_HEIGHT.dp)
                .background(MapColor, RoundedCornerShape(MAP_RADIUS.dp))
                .clearAndSetSemantics {},
        ) {
            val now = state()
            scale(density, density, pivot = Offset.Zero) {
                val stroke = Stroke(ROUTE_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(outline, rgba(0, 0, 0, ROUTE_FAINT), style = stroke)
                val reached = walked.routeLine(now.progress)
                if (now.ink > 0f) drawPath(walked, Ink, alpha = now.ink, style = stroke)

                if (now.flag > 0f) {
                    val end = RoutePoints.last()
                    val alpha = min(1f, now.flag)
                    scale(now.flag, now.flag, pivot = end) {
                        drawLine(Ink, end, Offset(end.x, end.y - FLAG_POLE), 1.6f, StrokeCap.Round, alpha = alpha)
                        drawPath(cloth, accent, alpha = alpha)
                    }
                }

                val dot = if (now.dotAtStart) RoutePoints[0] else reached
                drawCircle(MapColor, DOT_RING, dot, alpha = now.dot)
                drawCircle(Ink, DOT_RADIUS, dot, alpha = now.dot)
            }
        }
    }
}
