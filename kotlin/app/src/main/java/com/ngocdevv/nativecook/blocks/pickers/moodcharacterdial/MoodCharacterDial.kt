package com.ngocdevv.nativecook.blocks.pickers.moodcharacterdial

import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The characters the stage can show. */
enum class MoodFigure { Joyful, Sleepy, Recovery, Satisfied, Foodie, Thoughtful, Nostalgic, Shopping, Scrolling }

/**
 * @param body the character's colour.
 * @param stage the panel behind the character.
 * @param from with [to], the two colours of the needle (top to bottom) and of the selected chip (left to right).
 */
data class Mood(val name: String, val figure: MoodFigure, val body: Color, val stage: Color, val from: Color, val to: Color)

// The width the sizes below were drawn for; the characters scale with a narrower screen.
private const val DESIGN = 402f
private const val MAX_WIDTH = 430f
private const val HEIGHT = 630f

// The pastel panel and its characters.
private const val CARD_H = 340f
private const val CARD_INSET = 8f
private const val CARD_RADIUS = 32f

// Distance between two characters, and how far a character's centre sits above the panel's bottom edge.
private const val FIGURE_STEP = 340f
private const val FIGURE_RISE = 125f
private const val BREATH_TIME = 4f

private const val TITLE_Y = 392f

// The bars: 31 across, sliding five places per mood.
private const val BARS_Y = 494f
private const val BARS_H = 96f
private const val NEEDLE_DROP = 6f
private const val BAR_GAP = 12f
private const val BARS_ASIDE = 15
private const val BARS_PER_MOOD = 5f
private const val BAR_LOW = 10f
private const val BAR_HIGH = 84f
private const val BELL_WIDTH = 95f

// The chips.
private const val CHIPS_Y = 598f
private const val CHIP_H = 48f
private const val CHIP_PAD = 22f
private const val CHIP_GAP = 12f
private const val CHIP_FONT = 15f

// Snapping.
private const val STIFFNESS = 220f
private const val DAMPING_RATIO = 0.74f
private const val GLIDE_TIME = 1.1f

// Seconds of the finger's speed the row coasts for.
private const val COAST = 0.2f
private const val BAND = 0.35f
private const val TAP_SLOP = 6f

// The dial has landed once it is this close (in moods) to its target.
private const val LAND_WITHIN = 0.03f
private const val FILL_IN = 0.2f
private const val FILL_OUT = 0.12f

// The landing's squash rings out within this long.
private const val SQUASH_TIME = 0.6f

// autoPlay (seconds): when each move starts, and how many moods from the resting one it goes to.
private const val LOOP = 8f

private class Step(val at: Float, val by: Int, val glide: Boolean)

private val Script = listOf(
    Step(1.0f, 1, false),
    Step(2.4f, 4, false),
    Step(4.1f, 5, false),
    Step(5.4f, 0, true),
)

// After a touch the loop waits this long, then takes `HOME_TIME` to return to the resting mood.
private const val RESUME_AFTER = 2f
private const val HOME_TIME = 1.2f

private val EaseInOut = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

private val Panel = Color(0xFF141B1C)
private val Ink = Color(0xFF2B1B17)
private val Blush = Color(0xFFFF8FA0).copy(alpha = 0.6f)
private val Rose = Color(0xFFE8384F)
private val Tongue = Color(0xFFF26A5E)

private val Moods = listOf(
    Mood("Joyful", MoodFigure.Joyful, Color(0xFF57C24A), Color(0xFFD3EBB4), Color(0xFF8FD96B), Color(0xFFF5D34A)),
    Mood("Sleepy", MoodFigure.Sleepy, Color(0xFFF5B70F), Color(0xFFEFE2B4), Color(0xFFF5C63C), Color(0xFFF29AC0)),
    Mood("Recovery Mode", MoodFigure.Recovery, Color(0xFFF26A5E), Color(0xFFCFD2C4), Color(0xFFC9A8F0), Color(0xFF9ED98A)),
    Mood("Satisfied", MoodFigure.Satisfied, Color(0xFF3FB5A8), Color(0xFFCDE6DF), Color(0xFF5FD0C0), Color(0xFFF5D34A)),
    Mood("Foodie", MoodFigure.Foodie, Color(0xFFF5980F), Color(0xFFF8D9A8), Color(0xFFF5A53C), Color(0xFFF26A5E)),
    Mood("Thoughtful", MoodFigure.Thoughtful, Color(0xFF5B8DEF), Color(0xFFCFDDF7), Color(0xFF7FA8F5), Color(0xFFB56BF0)),
    Mood("Nostalgic", MoodFigure.Nostalgic, Color(0xFFB265F0), Color(0xFFE4D2F7), Color(0xFFB56BF0), Color(0xFFF08A7A)),
    Mood("Shopping", MoodFigure.Shopping, Color(0xFFFF8FE0), Color(0xFFF9D5FB), Color(0xFFFF8FE0), Color(0xFFB56BF0)),
    Mood("Scroll Mode", MoodFigure.Scrolling, Color(0xFFF0434A), Color(0xFFF8C3B0), Color(0xFFF0434A), Color(0xFFF5980F)),
)

private val TitleStyle = TextStyle(
    color = Color.White,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val ChipStyle = TextStyle(
    fontSize = CHIP_FONT.sp,
    fontWeight = FontWeight.Light,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** The landing: the character dips 4% within 80 ms of arriving and rings out in about half a second. */
private fun squashAt(t: Float) = if (t <= 0f || t >= SQUASH_TIME) 0f else 0.04f / 0.524f * exp(-7f * t) * sin(14.5f * t)

/** What moves. */
private class DialMotion(home: Int) {
    /** The dial's position as a mood index; whole numbers are the snap points. */
    val pos = Animatable(home.toFloat())

    /** The selected chip's gradient: 0 an outline … 1 filled. */
    val fill = Animatable(1f)

    /** 0…1 over `SQUASH_TIME` from the moment the dial reaches its target. */
    val land = Animatable(1f)

    /** The mood the dial is at, or heading for. */
    var target by mutableIntStateOf(home)

    /** The chip that carries the gradient: the one the dial last landed on. */
    var filled by mutableIntStateOf(home)

    /** A move is under way; it ends when the dial first reaches its target. */
    var moving by mutableStateOf(false)
    var held by mutableStateOf(false)
    var seconds by mutableFloatStateOf(0f)

    /** The mood under the needle at the last frame, for the haptic ticks. */
    var ticked = home

    /** The moves come from a finger, so they tick; the script's do not. */
    var felt = false
}

/**
 * A mood picker in two halves. On top, a pastel panel holds one large flat character per mood, laid
 * out as a carousel. Below, on a near-black panel, a title, a row of thin bars under a bell-shaped
 * envelope with a gradient needle in the middle, and a row of outlined chips. Dragging anywhere moves
 * the chips with the finger, the bars under the fixed envelope and the characters one panel per mood,
 * while the panel's colour and the needle's gradient cross-fade. On release the row snaps a chip
 * under the needle; it fills with the mood's gradient and the character lands with a small squash.
 *
 * @param initial Index of the mood selected at first, and the one the self-playing demo rests on.
 * @param autoPlay Steps and flicks through the moods by itself, in a loop.
 * @param onSelect Called with the index of the mood the dial is sent to.
 */
@Composable
fun MoodCharacterDial(
    title: String = "Select your\nweekend mood",
    moods: List<Mood> = Moods,
    initial: Int = 1,
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still && moods.size > 1
    val last = moods.lastIndex
    val home = initial.coerceIn(0, last)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val motion = remember(moods.size, home) { DialMotion(home) }
    val selected by rememberUpdatedState(onSelect)
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    // Each chip's width, and its centre along the row.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val widths = remember(moods, density) { moods.map { measurer.measure(it.name, ChipStyle).size.width / density + CHIP_PAD * 2f } }
    val centres = remember(widths) {
        var x = 0f
        widths.map { width -> (x + width / 2f).also { x += width + CHIP_GAP } }
    }

    // The row's offset when the dial is at `p`: chip centres, joined by straight lines.
    fun offsetAt(p: Float): Float {
        if (centres.size < 2) return centres.firstOrNull() ?: 0f
        val i = floor(p).toInt().coerceIn(0, centres.size - 2)
        return centres[i] + (centres[i + 1] - centres[i]) * (p - i)
    }

    // The inverse of `offsetAt`.
    fun indexAt(x: Float): Float {
        if (centres.size < 2) return 0f
        var i = 0
        while (i < centres.size - 2 && x > centres[i + 1]) i++
        return i + (x - centres[i]) / (centres[i + 1] - centres[i])
    }

    // Sends the dial to mood `index`: on a spring that is softer the further it has to go, or, for the
    // script's long way home, on an ease-in-out glide.
    fun select(index: Int, velocity: Float = 0f, glide: Boolean = false) {
        val to = index.coerceIn(0, last)
        val far = abs(to - motion.pos.value)
        if (far > LAND_WITHIN) scope.launch { motion.fill.animateTo(0f, tween((FILL_OUT * 1000).toInt(), easing = LinearEasing)) }
        val changed = to != motion.target
        motion.target = to
        motion.moving = true
        scope.launch {
            if (still) {
                motion.pos.snapTo(to.toFloat())
            } else if (glide) {
                motion.pos.animateTo(to.toFloat(), tween((GLIDE_TIME * 1000).toInt(), easing = EaseInOut))
            } else {
                val stiffness = STIFFNESS / max(1f, far).pow(0.75f)
                motion.pos.animateTo(to.toFloat(), spring(dampingRatio = DAMPING_RATIO, stiffness = stiffness, visibilityThreshold = 0.0005f), initialVelocity = velocity)
            }
        }
        if (changed) selected?.invoke(to)
    }

    // Ticks as moods pass the needle, and notices the landing: the chip fills and the character squashes.
    LaunchedEffect(motion, last) {
        snapshotFlow { Triple(motion.pos.value, motion.moving, motion.held) }.collect { (p, moving, held) ->
            val tick = p.roundToInt().coerceIn(0, last)
            if (tick != motion.ticked) {
                motion.ticked = tick
                if (motion.felt) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            if (moving && !held && abs(p - motion.target) < LAND_WITHIN) {
                motion.moving = false
                motion.filled = motion.target
                launch { motion.fill.animateTo(1f, tween((FILL_IN * 1000).toInt(), easing = LinearEasing)) }
                launch {
                    motion.land.snapTo(0f)
                    motion.land.animateTo(1f, tween((SQUASH_TIME * 1000).toInt(), easing = LinearEasing))
                }
            }
        }
    }

    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        var before = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            motion.seconds += min((frame - before) / 1e9f, 0.064f)
            before = frame
        }
    }

    // The script a finger would follow: a step, a flick, a step, and a long glide home.
    LaunchedEffect(auto, touches, motion) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, go back to the resting mood, and start over from there.
            delay((RESUME_AFTER * 1000).toLong())
            if (motion.held) return@LaunchedEffect
            motion.felt = false
            select(home)
            delay((HOME_TIME * 1000).toLong())
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        while (true) {
            for (step in Script) {
                val due = begin + ((turn * LOOP + step.at) * 1e9).toLong()
                delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                select(home + step.by, glide = step.glide)
            }
            turn += 1
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(HEIGHT.dp), contentAlignment = Alignment.TopCenter) {
        val width = min(maxWidth.value, MAX_WIDTH)
        Box(
            Modifier
                .size(width.dp, HEIGHT.dp)
                .clipToBounds()
                .pointerInput(motion, centres, width) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tracker.resetTracking()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        // The finger catches the dial wherever it is.
                        touches += 1
                        motion.held = true
                        motion.felt = true
                        scope.launch { motion.pos.stop() }
                        val from = offsetAt(motion.pos.value)
                        var p = motion.pos.value
                        var dragged = false
                        var at = down.position
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            at = change.position
                            tracker.addPosition(change.uptimeMillis, at)
                            val moved = (at.x - down.position.x) / density
                            if (!dragged && abs(moved) > TAP_SLOP) {
                                dragged = true
                                motion.moving = true
                                scope.launch { motion.fill.animateTo(0f, tween((FILL_OUT * 1000).toInt(), easing = LinearEasing)) }
                            }
                            if (dragged) {
                                change.consume()
                                // Past either end the row follows the finger at a third of its speed.
                                val raw = indexAt(from - moved)
                                p = if (raw < 0f) raw * BAND else if (raw > last) last + (raw - last) * BAND else raw
                                scope.launch { motion.pos.snapTo(p) }
                            }
                        }
                        touches += 1
                        motion.held = false
                        if (dragged) {
                            // The row keeps the finger's speed and settles on the chip it would coast to.
                            val spacing = max(1f, offsetAt(p + 0.5f) - offsetAt(p - 0.5f))
                            val speed = -tracker.calculateVelocity().x / density / spacing
                            select((p + speed * COAST).roundToInt(), velocity = speed)
                        } else {
                            // A tap on a chip selects it; anywhere else the dial settles where it was caught.
                            val x = at.x / density - width / 2f + offsetAt(p)
                            val chip = if (abs(at.y / density - CHIPS_Y) <= CHIP_H / 2f + 8f) {
                                centres.indices.firstOrNull { abs(x - centres[it]) <= (widths[it] + CHIP_GAP) / 2f }
                            } else {
                                null
                            }
                            select(chip ?: p.roundToInt())
                        }
                    }
                },
        ) {
            // The pastel panel and the characters on it, clipped to the panel.
            Canvas(Modifier.size(width.dp, CARD_H.dp)) {
                val p = motion.pos.value
                val shrink = width / DESIGN
                val breath = if (still) 0f else 0.012f * sin(TAU * motion.seconds / BREATH_TIME)
                val squash = if (still) 0f else squashAt(motion.land.value * SQUASH_TIME)
                val card = Path().apply {
                    addRoundRect(RoundRect(Rect(CARD_INSET, 0f, width - CARD_INSET, CARD_H), CornerRadius(CARD_RADIUS)))
                }
                scale(this.density, this.density, pivot = Offset.Zero) {
                    clipPath(card) {
                        drawRect(tone(moods, p) { it.stage }, size = Size(width, CARD_H))
                        moods.forEachIndexed { index, mood ->
                            val away = index - p
                            if (abs(away) >= 1.7f) return@forEachIndexed
                            // Squash and breathe about the point where the character meets the panel's bottom edge.
                            val press = (if (index == motion.target) squash else 0f) - breath
                            withTransform({
                                translate(width / 2f + away * FIGURE_STEP * shrink, CARD_H)
                                scale(shrink * (1f + press), shrink * (1f - press), pivot = Offset.Zero)
                                translate(0f, -FIGURE_RISE)
                            }) {
                                drawFigure(mood.figure, mood.body, motion.seconds, clamp01(1f - abs(away) * 1.5f))
                            }
                        }
                    }
                }
            }

            BasicText(title, Modifier.offset(0.dp, (TITLE_Y - 36f).dp).size(width.dp, 72.dp), style = TitleStyle)

            // The bars slide under an envelope that stays put, so each grows towards the middle; the needle is fixed.
            Canvas(Modifier.offset(0.dp, (BARS_Y - BARS_H / 2f).dp).size(width.dp, BARS_H.dp)) {
                val p = motion.pos.value
                val middle = width / 2f
                val base = BARS_H - NEEDLE_DROP
                val shift = p * BARS_PER_MOOD
                val part = shift - floor(shift)
                scale(this.density, this.density, pivot = Offset.Zero) {
                    for (place in -BARS_ASIDE - 1..BARS_ASIDE + 1) {
                        val x = (place - part) * BAR_GAP
                        if (abs(x) > BARS_ASIDE * BAR_GAP + 0.5f) continue
                        val bell = exp(-(x / BELL_WIDTH).pow(2))
                        val tall = mix(BAR_LOW, BAR_HIGH, bell)
                        drawRoundRect(
                            Color.White.copy(alpha = mix(0.05f, 0.45f, bell)),
                            topLeft = Offset(middle + x - 1f, base - tall),
                            size = Size(2f, tall),
                            cornerRadius = CornerRadius(1f),
                        )
                    }
                    drawRoundRect(
                        Brush.verticalGradient(listOf(tone(moods, p) { it.from }, tone(moods, p) { it.to }), startY = 0f, endY = BARS_H),
                        topLeft = Offset(middle - 1.5f, 0f),
                        size = Size(3f, BARS_H),
                        cornerRadius = CornerRadius(1.5f),
                    )
                }
            }

            moods.forEachIndexed { index, mood ->
                Box(
                    Modifier
                        .offset {
                            val x = width / 2f + centres[index] - widths[index] / 2f - offsetAt(motion.pos.value)
                            IntOffset((x * this.density).roundToInt(), ((CHIPS_Y - CHIP_H / 2f) * this.density).roundToInt())
                        }
                        .size(widths[index].dp, CHIP_H.dp)
                        .drawBehind {
                            val on = if (motion.filled == index) motion.fill.value else 0f
                            val radius = CornerRadius(size.height / 2f)
                            drawRoundRect(Brush.horizontalGradient(listOf(mood.from, mood.to)), cornerRadius = radius, alpha = on)
                            val line = 1.dp.toPx()
                            drawRoundRect(
                                Color(0xFF5A6264),
                                topLeft = Offset(line / 2f, line / 2f),
                                size = Size(size.width - line, size.height - line),
                                cornerRadius = CornerRadius(size.height / 2f - line / 2f),
                                style = Stroke(line),
                                alpha = 1f - on,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        mood.name,
                        style = ChipStyle,
                        maxLines = 1,
                        color = { lerp(Color.White, Panel, if (motion.filled == index) motion.fill.value else 0f) },
                    )
                }
            }
        }
    }
}

/** A colour of the mood at `p`, cross-faded with its neighbour's in between. */
private fun tone(moods: List<Mood>, p: Float, pick: (Mood) -> Color): Color {
    if (moods.size < 2) return moods.firstOrNull()?.let(pick) ?: Color.Transparent
    val i = floor(p).toInt().coerceIn(0, moods.size - 2)
    return lerp(pick(moods[i]), pick(moods[i + 1]), clamp01(p - i))
}

// The characters

private val Line = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) {
    drawOval(color, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f))
}

private fun DrawScope.curve(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float) {
    val path = Path().apply {
        moveTo(x0, y0)
        quadraticTo(cx, cy, x1, y1)
    }
    drawPath(path, Ink, style = Line)
}

/** A rounded rectangle whose top edge is where the circle's would be. */
private fun DrawScope.block(width: Float, radius: Float, color: Color) {
    drawRoundRect(color, topLeft = Offset(-width / 2f, -165f), size = Size(width, 360f), cornerRadius = CornerRadius(radius))
}

/** A circle of radius 165 whose edge swells in and out `count` times by the share `depth`. */
private fun lumps(count: Float, depth: Float, turn: Float) = Path().apply {
    for (step in 0 until 96) {
        val angle = step / 96f * TAU
        val radius = 165f * (1f + depth * sin(count * angle + turn))
        if (step == 0) moveTo(cos(angle) * radius, sin(angle) * radius) else lineTo(cos(angle) * radius, sin(angle) * radius)
    }
    close()
}

private fun sparkle(x: Float, y: Float, size: Float) = Path().apply {
    moveTo(x, y - size)
    quadraticTo(x, y, x + size, y)
    quadraticTo(x, y, x, y + size)
    quadraticTo(x, y, x - size, y)
    quadraticTo(x, y, x, y - size)
    close()
}

private fun heart(x: Float, y: Float, s: Float) = Path().apply {
    moveTo(x, y + s * 0.9f)
    cubicTo(x - s * 0.5f, y + s * 0.55f, x - s, y + s * 0.15f, x - s, y - s * 0.2f)
    cubicTo(x - s, y - s * 0.75f, x - s * 0.3f, y - s * 0.9f, x, y - s * 0.45f)
    cubicTo(x + s * 0.3f, y - s * 0.9f, x + s, y - s * 0.75f, x + s, y - s * 0.2f)
    cubicTo(x + s, y + s * 0.15f, x + s * 0.5f, y + s * 0.55f, x, y + s * 0.9f)
    close()
}

/** Closed and curving down, closed and curving up, or open and looking towards `x`, `y` (each −1…1). */
private sealed interface Eyes {
    data object Asleep : Eyes
    data object Smiling : Eyes
    data class Open(val x: Float, val y: Float) : Eyes
}

private enum class Mouth { Round, Smile, Grin, Flat, Smirk, Lick }

private fun DrawScope.eyes(kind: Eyes) {
    for (x in listOf(-46f, 46f)) {
        when (kind) {
            Eyes.Asleep -> curve(x - 19f, -80f, x, -60f, x + 19f, -80f)
            Eyes.Smiling -> curve(x - 19f, -70f, x, -98f, x + 19f, -70f)
            is Eyes.Open -> {
                val px = x + kind.x * 7f
                val py = -78f + kind.y * 7f
                drawCircle(Color.White, 18f, Offset(x, -78f))
                drawCircle(Ink, 9f, Offset(px, py))
                drawCircle(Color.White, 3f, Offset(px - 3f, py - 3f))
            }
        }
    }
}

private fun DrawScope.mouth(kind: Mouth) {
    when (kind) {
        Mouth.Round -> {
            oval(0f, -24f, 14f, 17f, Ink)
            oval(0f, -15f, 9f, 6f, Tongue)
        }
        Mouth.Smile -> curve(-22f, -32f, 0f, -8f, 22f, -32f)
        Mouth.Lick -> {
            oval(12f, -16f, 10f, 9f, Rose)
            curve(-22f, -32f, 0f, -8f, 22f, -32f)
        }
        Mouth.Grin -> {
            val path = Path().apply {
                moveTo(-28f, -36f)
                lineTo(28f, -36f)
                quadraticTo(0f, 14f, -28f, -36f)
                close()
            }
            drawPath(path, Ink)
            drawPath(path, Ink, style = Stroke(width = 4f, join = StrokeJoin.Round))
            oval(0f, -18.5f, 11f, 5.5f, Tongue)
        }
        Mouth.Flat -> drawLine(Ink, Offset(-13f, -26f), Offset(13f, -26f), strokeWidth = 5.5f, cap = StrokeCap.Round)
        Mouth.Smirk -> curve(-14f, -30f, 6f, -14f, 18f, -34f)
    }
}

private fun DrawScope.cheeks() {
    drawCircle(Blush, 14f, Offset(-84f, -44f))
    drawCircle(Blush, 14f, Offset(84f, -44f))
}

/**
 * Draws one character around the origin, which is the centre of its 330 dp body. [near] is 1 when
 * the character is the one in the middle of the panel, and fades what floats around it.
 */
private fun DrawScope.drawFigure(figure: MoodFigure, body: Color, seconds: Float, near: Float) {
    when (figure) {
        MoodFigure.Joyful -> {
            drawPath(lumps(6f, 0.045f, 0f), body)
            cheeks()
            eyes(Eyes.Smiling)
            mouth(Mouth.Grin)
        }
        MoodFigure.Sleepy -> {
            drawCircle(body, 165f, Offset.Zero)
            eyes(Eyes.Asleep)
            mouth(Mouth.Round)
            // Three letters rise one after the other, growing and fading as they go.
            for (letter in 0 until 3) {
                val u = (seconds / 2f + letter / 3f) % 1f
                val size = 5f + 7f * u
                val x = 84f + 40f * u
                val y = -128f - 70f * u
                val z = Path().apply {
                    moveTo(x - size, y - size)
                    lineTo(x + size, y - size)
                    lineTo(x - size, y + size)
                    lineTo(x + size, y + size)
                }
                drawPath(
                    z,
                    Color(0xFF2B3FD6).copy(alpha = sin(Math.PI.toFloat() * u) * near),
                    style = Stroke(width = size * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        MoodFigure.Recovery -> {
            drawPath(lumps(4f, 0.06f, 0.6f), body)
            drawRoundRect(Color.White, topLeft = Offset(-4.5f, -143f), size = Size(9f, 30f), cornerRadius = CornerRadius(3f))
            drawRoundRect(Color.White, topLeft = Offset(-15f, -132.5f), size = Size(30f, 9f), cornerRadius = CornerRadius(3f))
            eyes(Eyes.Asleep)
            mouth(Mouth.Smile)
        }
        MoodFigure.Satisfied -> {
            block(300f, 110f, body)
            cheeks()
            eyes(Eyes.Smiling)
            mouth(Mouth.Smile)
        }
        MoodFigure.Foodie -> {
            drawPath(lumps(3f, 0.07f, 0f), body)
            cheeks()
            eyes(Eyes.Open(0f, 0.5f))
            mouth(Mouth.Lick)
        }
        MoodFigure.Thoughtful -> {
            block(290f, 145f, body)
            drawCircle(Color.White, 7f, Offset(92f, -108f))
            drawCircle(Color.White, 10f, Offset(116f, -138f))
            drawCircle(Color.White, 14f, Offset(146f, -172f))
            eyes(Eyes.Open(0.6f, -0.6f))
            mouth(Mouth.Flat)
        }
        MoodFigure.Nostalgic -> {
            drawPath(lumps(5f, 0.05f, 0.3f), body)
            drawPath(sparkle(92f, -118f, 15f), Color.White)
            drawPath(sparkle(116f, -88f, 8f), Color.White)
            eyes(Eyes.Open(-0.5f, -0.6f))
            mouth(Mouth.Smirk)
        }
        MoodFigure.Shopping -> {
            // A bag: two handles behind a rounded square, and a heart under the face.
            for (x in listOf(-32f, 32f)) {
                drawArc(
                    Rose, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(x - 26f, -189f), size = Size(52f, 52f), style = Stroke(width = 8f, cap = StrokeCap.Round),
                )
            }
            block(280f, 44f, body)
            eyes(Eyes.Smiling)
            mouth(Mouth.Smile)
            drawPath(heart(0f, 30f, 26f), Rose)
        }
        MoodFigure.Scrolling -> {
            drawPath(lumps(8f, 0.04f, 0f), body)
            eyes(Eyes.Open(0.8f, 0.2f))
            mouth(Mouth.Flat)
            // A phone held up at the side, seen from the back.
            withTransform({
                translate(100f, -24f)
                rotate(-10f, pivot = Offset.Zero)
            }) {
                drawRoundRect(Color(0xFF1E1E24), topLeft = Offset(-23f, -39f), size = Size(46f, 78f), cornerRadius = CornerRadius(10f))
                drawCircle(Color(0xFF3A3A44), 6.5f, Offset(-8.5f, -24.5f))
            }
        }
    }
}
