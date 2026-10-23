package com.ngocdevv.nativecook.blocks.pickers.disctraypicker

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @param name Shown under the tray while the disc is in it.
 * @param colors The print: the first two colours blend corner to corner, the third and the fourth are soft spots on it.
 * @param sheen How strong the rainbow sheen is on this print, 0…1.
 */
data class TrayDisc(val name: String, val colors: List<Color>, val sheen: Float)

private const val WIDTH = 330f
private const val HEIGHT = 612f

private const val TITLE_Y = 12f

// The player: a rounded square with a round well in it.
private const val TRAY = 240f
private const val TRAY_Y = 44f
private const val TRAY_RADIUS = 42f
private const val WELL_SIZE = 202f
private const val WELL_X = WIDTH / 2f
private const val WELL_Y = TRAY_Y + TRAY / 2f
private const val CAPTION_Y = 308f

// A line under the tray rises this far as it fades in, and sinks as it fades out.
private const val CAPTION_SHIFT = 4f
private const val CAPTION_TIME = 0.2f

// A disc in the grid, the gap between two, and a disc in the tray.
private const val DISC = 94f
private const val GAP = 12f
private const val GRID_Y = 336f
private const val LOADED = 186f
private const val BUTTON_W = 126f
private const val BUTTON_H = 52f
private const val BUTTON_Y = 560f

// The flight (seconds): a dip under the finger, up to the tray, back down to the grid.
private const val DIP_TIME = 0.08f
private const val UP_TIME = 0.45f
private const val DOWN_TIME = 0.4f

// How far a tapped disc shrinks, and how far it swells as it lands.
private const val DIP = 0.05f
private const val SWELL = 0.03f

// How far the path bows sideways at its middle.
private const val BOW = 18f

// Degrees a second the print turns in the tray: once round in six seconds.
private const val SPIN_RATE = 60f

// The print keeps turning for this many turns, far longer than a disc stays in the tray.
private const val SPIN_TURNS = 1000

// The sheen turns this many degrees on the way to the tray, and sways this many in the grid.
private const val SHEEN_TURN = -40f
private const val SHIMMER = 5f
private const val SHIMMER_TIME = 4f

// autoPlay (seconds): rest, a disc every `STEP_GAP`, the tray emptied one gap after the last.
private const val REST = 0.9f
private const val STEP_GAP = 1.8f
private const val LOOP = 8f

// After a touch the loop waits this long before it starts over.
private const val RESUME_AFTER = 2f

private val FlyCurve = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

private val Discs = listOf(
    TrayDisc("Olive Hours", listOf(Color(0xFF3A3716), Color(0xFF16150A), Color(0xFFD9B24A), Color(0xFF6B5A1E)), 0.7f),
    TrayDisc("Crimson Static", listOf(Color(0xFF8F1020), Color(0xFF14040A), Color(0xFFF0364C), Color(0xFF3A0A12)), 1f),
    TrayDisc("Rose Signal", listOf(Color(0xFFF7A8E0), Color(0xFFD63FB8), Color(0xFFFFE1F6), Color(0xFFB3279A)), 0.55f),
    TrayDisc("Ember Coast", listOf(Color(0xFFFF5A1F), Color(0xFFC81E0C), Color(0xFFFFA040), Color(0xFF9C1408)), 0.6f),
    TrayDisc("Silver Tide", listOf(Color(0xFF2A2D34), Color(0xFF0B0C0F), Color(0xFFC9D0DA), Color(0xFF5A6170)), 0.95f),
    TrayDisc("Aqua Motel", listOf(Color(0xFF1A8F93), Color(0xFF0A4B52), Color(0xFFB5FFF4), Color(0xFF52D6CD)), 0.65f),
)

/** Two opposite rainbow wedges and two faint white ones between them, clockwise from 3 o'clock. */
private val Sheen: Brush = run {
    val clear = Color.White.copy(alpha = 0f)
    val rainbow = listOf(Color(0xFFFF2D55), Color(0xFFFFD60A), Color(0xFF30D158), Color(0xFF40C8FF), Color(0xFF7D5CFF))
    val stops = mutableListOf(0f to clear)
    for (start in listOf(0.07f, 0.57f)) {
        stops += (start - 0.07f) to clear
        stops += (start - 0.02f) to Color.White.copy(alpha = 0.3f)
        stops += (start + 0.03f) to clear
        stops += (start + 0.1f) to clear
        rainbow.forEachIndexed { k, color -> stops += (start + 0.13f + 0.03f * k) to color.copy(alpha = 0.85f) }
        stops += (start + 0.29f) to clear
    }
    stops += 1f to clear
    Brush.sweepGradient(*stops.toTypedArray(), center = Offset.Zero)
}

private val Vignette = Brush.radialGradient(0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f), center = Offset.Zero, radius = 1f)

private val UnitCircle = Path().apply { addOval(Rect(-1f, -1f, 1f, 1f)) }

private fun type(size: Float, color: Color, weight: FontWeight) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = type(17f, Color(0xFF1A1A1A), FontWeight.Bold)
private val CaptionStyle = type(13f, Color(0xFF8A8A8A), FontWeight.Medium)
private val ButtonStyle = type(15f, Color.White, FontWeight.Bold)

/** The centre of disc [index]'s place in the grid. */
private fun slot(index: Int): Offset {
    val pitch = DISC + GAP
    return Offset(WIDTH / 2f + (index % 3 - 1) * pitch, GRID_Y + DISC / 2f + (index / 3) * pitch)
}

/** What moves on one disc. */
private class DiscMotion(loaded: Boolean) {
    /** The way from the grid, 0, to the tray, 1. */
    val flight = Animatable(if (loaded) 1f else 0f)

    /** The dip as it is tapped, and the swell as it lands in the tray. */
    val press = Animatable(0f)
    val seat = Animatable(0f)

    /** The print's angle, in degrees. */
    val angle = Animatable(0f)

    /** The flight under way; a new one replaces it. */
    var job: Job? = null
}

/**
 * A disc player seen from above, over a grid of compact discs. Tapping a disc lifts it out of the
 * grid and flies it into the tray, where it grows to the well's size and turns under a rainbow sheen
 * that stays with the light; tapping another one swaps them in mid-air, and tapping the disc in the
 * tray sends it back to its place. The name of the loaded disc shows under the tray.
 *
 * @param prompt Shown under the tray while it is empty.
 * @param discs Up to six discs, laid out three to a row.
 * @param initial Index of the disc in the tray at first; null leaves the tray empty.
 * @param autoPlay Loads three discs one after the other and empties the tray by itself, in a loop.
 * @param onSelect Called with the index of the disc that goes into the tray, or null when the tray is emptied.
 * @param onContinue Called with the index of the loaded disc when the button is tapped.
 */
@Composable
fun DiscTrayPicker(
    title: String = "Welcome to Sidetrack",
    prompt: String = "Choose a disc",
    action: String = "Continue",
    discs: List<TrayDisc> = Discs,
    initial: Int? = null,
    autoPlay: Boolean = false,
    onSelect: ((index: Int?) -> Unit)? = null,
    onContinue: ((index: Int?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val count = min(discs.size, 6)
    val home = initial?.takeIf { it in 0 until count }
    val auto = autoPlay && !still && count > 1
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val chosen by rememberUpdatedState(onSelect)
    val continued by rememberUpdatedState(onContinue)

    val motion = remember(count) { List(count) { DiscMotion(it == home) } }
    // The disc in the tray, or null.
    var tray by remember(count) { mutableStateOf(home) }
    // The order the discs are drawn in; the one that moved last is on top.
    var order by remember(count) { mutableStateOf((0 until count).sortedBy { it == home }) }
    // Counts taps: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }
    // A clock for the shimmer.
    var seconds by remember { mutableFloatStateOf(0f) }
    val buttonPress = remember { Animatable(0f) }
    val trayShadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.1f), sigma = 15f) }

    // Up to 1 in `up` ms and back to 0 in `down`.
    suspend fun pulse(value: Animatable<Float, AnimationVector1D>, up: Int, down: Int) {
        value.animateTo(1f, tween(up, easing = LinearEasing))
        value.animateTo(0f, tween(down, easing = LinearEasing))
    }

    // Under reduced motion nothing travels: every change is a jump.
    suspend fun move(value: Animatable<Float, AnimationVector1D>, target: Float, spec: AnimationSpec<Float>) {
        if (still) value.snapTo(target) else value.animateTo(target, spec)
    }

    // Puts disc `index` in the tray (null empties it). The disc that was there flies back to its
    // place while the new one flies up. `byHand`: a finger chose it, so its landing is felt.
    fun select(index: Int?, byHand: Boolean = false) {
        if (index == tray || (index != null && index !in 0 until count)) return
        tray?.let { old ->
            val disc = motion[old]
            disc.job?.cancel()
            disc.job = scope.launch {
                if (index == null && !still) launch { pulse(disc.press, 80, 120) }
                // It stops turning and comes to rest the way it was printed, by the shorter way round.
                val turned = disc.angle.value % 360f
                disc.angle.snapTo(if (turned > 180f) turned - 360f else turned)
                disc.seat.snapTo(0f)
                launch { move(disc.angle, 0f, tween((DOWN_TIME * 1000).toInt(), easing = EaseOutStrong)) }
                move(disc.flight, 0f, tween((DOWN_TIME * 1000).toInt(), easing = FlyCurve))
            }
        }
        index?.let { new ->
            val disc = motion[new]
            disc.job?.cancel()
            disc.job = scope.launch {
                if (still) {
                    disc.flight.snapTo(1f)
                    return@launch
                }
                // A dip under the finger, then the lift.
                launch { pulse(disc.press, 80, 120) }
                delay((DIP_TIME * 1000).toLong())
                launch {
                    delay(((UP_TIME - 0.06f) * 1000).toLong())
                    pulse(disc.seat, 60, 220)
                }
                disc.flight.animateTo(1f, tween((UP_TIME * 1000).toInt(), easing = FlyCurve))
                if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                disc.angle.animateTo(
                    disc.angle.value + 360f * SPIN_TURNS,
                    tween((360f * SPIN_TURNS / SPIN_RATE * 1000).toInt(), easing = LinearEasing),
                )
            }
            order = order.filter { it != new } + new
        }
        tray = index
        chosen?.invoke(index)
    }

    fun touch() {
        touches += 1
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    // A tap on disc `index`: into the tray, or back out of it when it is the one loaded.
    fun pick(index: Int) {
        touch()
        select(if (tray == index) null else index, byHand = true)
    }

    LaunchedEffect(motion, still) {
        // A disc that starts in the tray is already turning.
        if (home != null && !still) {
            motion[home].job = scope.launch {
                motion[home].angle.animateTo(360f * SPIN_TURNS, tween((360f * SPIN_TURNS / SPIN_RATE * 1000).toInt(), easing = LinearEasing))
            }
        }
        if (still) return@LaunchedEffect
        var before = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            seconds += min((frame - before) / 1e9f, 0.064f)
            before = frame
        }
    }

    // The script a finger would follow: the second disc, the last, the one before it, then the tray is emptied.
    LaunchedEffect(auto, touches, motion) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, put the tray back the way it began and start over.
            delay((RESUME_AFTER * 1000).toLong())
            select(home)
            delay(700)
        }
        val picks = mutableListOf<Int>()
        for (pick in listOf(1, count - 1, count - 2)) {
            if (pick >= 0 && pick != (picks.lastOrNull() ?: home)) picks += pick
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var round = 0
        suspend fun until(seconds: Float) {
            val due = begin + ((round * LOOP + seconds) * 1e9).toLong()
            delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        }
        while (true) {
            picks.forEachIndexed { step, pick ->
                until(REST + step * STEP_GAP)
                select(pick)
            }
            until(REST + picks.size * STEP_GAP)
            select(home)
            round += 1
        }
    }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        Line(title, TitleStyle, TITLE_Y - 12f, 24f) { 1f to 0f }
        for (line in 0..count) {
            val shown by animateFloatAsState(
                if (line == (tray?.plus(1) ?: 0)) 1f else 0f,
                tween(if (still) 0 else (CAPTION_TIME * 1000).toInt(), easing = EaseOutStrong),
                label = "caption",
            )
            Line(if (line == 0) prompt else discs[line - 1].name, CaptionStyle, CAPTION_Y - 10f, 20f) { shown to CAPTION_SHIFT * (1f - shown) }
        }

        // The discs fly over the lines above, so the canvas comes after them.
        Canvas(Modifier.fillMaxSize()) {
            // Drawn in dp, like the other versions.
            scale(density, density, pivot = Offset.Zero) {
                drawTray(trayShadow)
                for (index in 0 until count) drawCircle(Color(0xFFE6E6E6), DISC / 2f, slot(index))
                for (index in order) {
                    val disc = motion[index]
                    drawDisc(
                        discs[index], index,
                        way = disc.flight.value,
                        press = disc.press.value,
                        seat = disc.seat.value,
                        angle = disc.angle.value,
                        seconds = if (still) null else seconds,
                    )
                }
            }
        }

        Box(
            Modifier
                .offset((WELL_X - LOADED / 2f).dp, (WELL_Y - LOADED / 2f).dp)
                .size(LOADED.dp)
                .clip(CircleShape)
                .clickable(enabled = tray != null, interactionSource = null, indication = null, role = Role.Button) { tray?.let(::pick) }
                .semantics { contentDescription = tray?.let { discs[it].name } ?: prompt },
        )
        for (index in 0 until count) {
            val at = slot(index)
            Box(
                Modifier
                    .offset((at.x - (DISC + GAP) / 2f).dp, (at.y - (DISC + GAP) / 2f).dp)
                    .size((DISC + GAP).dp)
                    .clickable(enabled = tray != index, interactionSource = null, indication = null, role = Role.Button) { pick(index) }
                    .semantics {
                        contentDescription = discs[index].name
                        selected = tray == index
                    },
            )
        }
        Box(
            Modifier
                .offset((WIDTH / 2f - BUTTON_W / 2f).dp, BUTTON_Y.dp)
                .size(BUTTON_W.dp, BUTTON_H.dp)
                .graphicsLayer {
                    val dip = 1f - 0.04f * buttonPress.value
                    scaleX = dip
                    scaleY = dip
                }
                .clip(CircleShape)
                .background(Color(0xFF111111))
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    touch()
                    if (!still) scope.launch { pulse(buttonPress, 80, 160) }
                    continued?.invoke(tray)
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(action, style = ButtonStyle, maxLines = 1)
        }
    }
}

/** A centred line of text in a box [height] dp tall whose top is at [y]; [look] gives its opacity and how far it is shifted down. */
@Composable
private fun BoxScope.Line(text: String, style: TextStyle, y: Float, height: Float, look: () -> Pair<Float, Float>) {
    Box(
        Modifier
            .offset(0.dp, y.dp)
            .size(WIDTH.dp, height.dp)
            .graphicsLayer {
                val (opacity, shift) = look()
                alpha = opacity
                translationY = shift.dp.toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = style, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
    }
}

private fun DrawScope.drawTray(shadow: SoftRoundRect) {
    val left = (WIDTH - TRAY) / 2f
    drawIntoCanvas { shadow.draw(it.nativeCanvas, left, TRAY_Y + 10f, TRAY, TRAY, TRAY_RADIUS) }
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFE1E1E1), Color(0xFFD5D5D5)), startY = TRAY_Y, endY = TRAY_Y + TRAY),
        topLeft = Offset(left, TRAY_Y),
        size = Size(TRAY, TRAY),
        cornerRadius = CornerRadius(TRAY_RADIUS),
    )
    val center = Offset(WELL_X, WELL_Y)
    val radius = WELL_SIZE / 2f
    drawCircle(Brush.radialGradient(listOf(Color(0xFFBABABA), Color(0xFFB0B0B0)), center = center, radius = radius), radius, center)
    drawCircle(Color(0xFFC6C6C6), radius - 1.5f, center, style = Stroke(3f))
    drawCircle(Color.Black.copy(alpha = 0.05f), radius - 3.5f, center, style = Stroke(1f))
    drawCircle(Color(0xFF9C9C9C), 5f, center)
}

/**
 * One compact disc, [way] of the way from its place in the grid to the tray.
 *
 * @param seconds The shimmer's clock, or null to hold the sheen still.
 */
private fun DrawScope.drawDisc(disc: TrayDisc, index: Int, way: Float, press: Float, seat: Float, angle: Float, seconds: Float?) {
    // Off the ground in the middle of the flight.
    val lift = sin(PI.toFloat() * way.coerceIn(0f, 1f))
    val from = slot(index)
    // The path bows away from the middle column, so two discs swapping pass side by side.
    val side = if (from.x < WIDTH / 2f) -1f else 1f
    val center = Offset(from.x + (WELL_X - from.x) * way + side * BOW * lift, from.y + (WELL_Y - from.y) * way)
    val radius = (DISC + (LOADED - DISC) * way) * (1f - DIP * press) * (1f + SWELL * seat) / 2f
    val colors = disc.colors + List(4) { Color.Gray }
    // The sheen belongs to the light: it shifts as the disc changes place, and shimmers a little at rest.
    val shimmer = if (seconds == null) 0f else SHIMMER * sin(TAU * (seconds / SHIMMER_TIME + index / 3f))
    val sheenAngle = SHEEN_TURN * way + shimmer * (1f - way)

    // A soft shadow that drops and spreads as the disc leaves the ground.
    val blur = 4f + 8f * lift + 3f * way
    val reach = radius + 1.5f * blur
    val shade = Color.Black.copy(alpha = 0.2f + 0.08f * lift)
    val ground = Offset(center.x, center.y + 3f + 10f * lift + 2f * way)
    drawCircle(
        Brush.radialGradient(0f to shade, (radius - 1.5f * blur) / reach to shade, 1f to Color.Transparent, center = ground, radius = reach),
        reach,
        ground,
    )

    withTransform({
        translate(center.x, center.y)
        scale(radius, radius, pivot = Offset.Zero)
    }) {
        clipPath(UnitCircle) {
            rotate(angle, pivot = Offset.Zero) {
                drawCircle(Brush.linearGradient(listOf(colors[0], colors[1]), start = Offset(-0.75f, -0.75f), end = Offset(0.75f, 0.75f)), 1f, Offset.Zero)
                drawCircle(Brush.radialGradient(listOf(colors[2], colors[2].copy(alpha = 0f)), center = Offset(0.42f, -0.36f), radius = 0.8f), 1f, Offset.Zero)
                drawCircle(Brush.radialGradient(listOf(colors[3], colors[3].copy(alpha = 0f)), center = Offset(-0.5f, 0.45f), radius = 0.75f), 1f, Offset.Zero)
                drawCircle(Color.White.copy(alpha = 0.16f), 0.65f, Offset(-0.3f, 0.45f), style = Stroke(0.035f))
                drawCircle(Color.Black.copy(alpha = 0.14f), 0.5f, Offset(0.4f, -0.55f), style = Stroke(0.05f))
            }
            rotate(sheenAngle, pivot = Offset.Zero) {
                drawCircle(Sheen, 1f, Offset.Zero, alpha = disc.sheen, blendMode = BlendMode.Screen)
            }
            drawCircle(Vignette, 1f, Offset.Zero)
        }
        drawCircle(Color.White.copy(alpha = 0.45f), 0.985f, Offset.Zero, style = Stroke(0.02f))

        // The hub: a white ring with a dark edge, a grey ring inside it and the hole.
        drawCircle(Color(0xFFF6F6F6), 0.32f, Offset.Zero)
        drawCircle(Color(0xFF1E1E1E), 0.32f, Offset.Zero, style = Stroke(0.022f))
        drawCircle(Color(0xFFCFCFCF), 0.21f, Offset.Zero)
        drawCircle(Color(0xFF8F8F8F), 0.21f, Offset.Zero, style = Stroke(0.016f))
        drawCircle(Color(0xFFA6A6A6), 0.105f, Offset.Zero)
        drawCircle(Color(0xFF2A2A2A), 0.105f, Offset.Zero, style = Stroke(0.02f))
    }
}
