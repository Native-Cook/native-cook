package com.ngocdevv.nativecook.blocks.pickers.gooeyradialpicker

import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/** The moods the picker offers by default, each a small drawn face. */
enum class Mood(val label: String) {
    Happy("Happy"),
    Joyful("Joyful"),
    Calm("Calm"),
    Playful("Playful"),
    Surprised("Surprised"),
    Unsure("Unsure"),
    Sad("Sad"),
    Sleepy("Sleepy"),
}

// The square the picker is laid out in, and the room for the shadow round it.
private const val SIZE = 344f
private const val BLEED = 44f

// Radius of the disc in the middle, of an option's circle, and of the ring the options sit on.
private const val DISC = 80f
private const val DOT = 35f
private const val RING = 123f

// A joined circle moves this far towards the disc.
private const val REACH = 4f

// Radius of a face in the middle and on an option.
private const val LARGE = 40f
private const val SMALL = 14.5f

// The marker: its radius and the width of its line.
private const val MARKER = 13f
private const val MARKER_LINE = 7f
private const val DEAD_ZONE = 28f

// The simulation's step, in seconds, and the longest a frame is counted as.
private const val TICK = 1f / 240f
private const val LONGEST_FRAME = 0.25f

private class Springy(val stiffness: Float, val damping: Float)

private val Join = Springy(260f, 13f)
private val Part = Springy(300f, 26f)
private val Pop = Springy(300f, 19f)
private val Shrink = Springy(900f, 60f)
private val Wobble = Springy(400f, 16f)

// The speed the disc's radius gets when the marker reaches another option, in dp a second.
private const val KNOCK = 60f

// The new face pops out once the marker has stayed on an option this long.
private const val POP_AFTER = 0.22f
private const val LEAD_TIME = 0.03f
private const val LAG_TIME = 0.08f

// autoPlay (seconds). Each move: when it starts, how much of a turn it covers, how long it takes.
private const val LOOP = 8f
private const val SWEEP_TIME = 0.8f
private const val STEP_TIME = 0.6f

private class Move(val at: Float, val turn: Float, val time: Float)

private val Script = listOf(
    Move(1f, 0.5f, SWEEP_TIME),
    Move(2.7f, 0.125f, STEP_TIME),
    Move(4.1f, 0.125f, STEP_TIME),
    Move(5.5f, 0.25f, SWEEP_TIME),
)

// After a touch the loop waits this long before it starts over.
private const val RESUME_AFTER = 2f

private const val PI = Math.PI.toFloat()

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Cubic ease-in-out, for the marker's journeys. */
private fun inOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Everything that moves, stepped in small fixed steps on every frame. Lengths are in dp. */
private class Sim(val count: Int, rest: Int) {
    /** The option the marker is nearest to, and the one whose face is in the middle. */
    var hot = rest
    var shown = rest

    /** How far each option is joined to the disc, 0…1. */
    val pull = FloatArray(count)
    var face = 1f

    /** The wobble of the disc's radius. */
    var bump = 0f

    // The marker's head and tail, measured from the middle of the picker.
    var leadX = 0f
    var leadY = 0f
    var lagX = 0f
    var lagY = 0f
    var finger = false

    /** Where the marker points, as an angle round the ring (0 is 3 o'clock, growing clockwise). */
    private var theta = angle(rest)

    // The marker's journey when it travels by itself.
    private var from = 0f
    private var to = 0f
    private var elapsed = 0f
    private var duration = 0f
    private val speed = FloatArray(count)
    private var faceSpeed = 0f
    private var faceTarget = 1f
    private var bumpSpeed = 0f

    /** Seconds since the marker last reached another option. */
    private var calm = 0f
    private var fingerX = 0f
    private var fingerY = 0f

    private val step get() = 2f * PI / max(count, 1)

    init {
        settle()
    }

    fun angle(index: Int) = index * step - PI / 2f

    /** The marker's angle becomes exactly the resting option's: the same place, without the turns the loop has added up. */
    fun home(rest: Int) {
        if (duration == 0f && !finger && hot == rest) theta = angle(rest)
    }

    /** Sends the marker [sectors] options round the ring, clockwise when positive. */
    fun travel(sectors: Int, time: Float) {
        finger = false
        // Counted from where the marker is going, if it is still on its way.
        val base = if (duration > 0f) to else theta
        from = theta
        to = ((base + PI / 2f) / step).roundToInt() * step - PI / 2f + sectors * step
        elapsed = 0f
        duration = time
    }

    /** Sends the marker to option [index] the short way round. */
    fun travelTo(index: Int, time: Float) {
        var sectors = (index - hot) % count
        if (sectors > count / 2) sectors -= count
        if (sectors < -count / 2) sectors += count
        travel(sectors, time)
    }

    /** A finger is at [x], [y], measured from the middle of the picker. */
    fun point(x: Float, y: Float) {
        duration = 0f
        finger = true
        fingerX = x
        fingerY = y
        // Too near the middle the direction means nothing.
        if (hypot(x, y) > DEAD_ZONE) theta = atan2(y, x)
    }

    /** The finger lifted: the marker settles under the option it reached. */
    fun release() {
        finger = false
        theta = angle(hot)
    }

    fun advance(passed: Float, still: Boolean) {
        if (passed <= 0f) return
        val steps = max(1, ceil(passed / TICK).toInt())
        repeat(steps) { move(passed / steps) }
        // Under reduced motion nothing flows: every change is a jump.
        if (still) settle()
    }

    private fun move(h: Float) {
        if (duration > 0f) {
            elapsed += h
            val t = min(1f, elapsed / duration)
            theta = from + (to - from) * inOut(t)
            if (t >= 1f) duration = 0f
        }

        val nearest = (((theta + PI / 2f) / step).roundToInt() % count + count) % count
        if (nearest != hot) {
            // The marker reached another option: the face shrinks away and the disc gets a knock.
            hot = nearest
            calm = 0f
            faceTarget = 0f
            bumpSpeed += KNOCK
        }
        calm += h
        if (faceTarget == 0f && calm >= POP_AFTER) {
            shown = hot
            faceTarget = 1f
        }

        for (i in 0 until count) {
            // Joining is loose and overshoots; letting go is quick.
            val spring = if (i == hot) Join else Part
            speed[i] += (spring.stiffness * ((if (i == hot) 1f else 0f) - pull[i]) - spring.damping * speed[i]) * h
            pull[i] += speed[i] * h
        }
        val spring = if (faceTarget > 0.5f) Pop else Shrink
        faceSpeed += (spring.stiffness * (faceTarget - face) - spring.damping * faceSpeed) * h
        face += faceSpeed * h
        bumpSpeed += (-Wobble.stiffness * bump - Wobble.damping * bumpSpeed) * h
        bump += bumpSpeed * h

        // The marker heads for the finger, or for just below the circle it points at. Its head follows
        // closely and its tail lags, so a fast move smears it into a pill.
        val near = 1f - exp(-h / LEAD_TIME)
        val far = 1f - exp(-h / LAG_TIME)
        leadX += (goalX() - leadX) * near
        leadY += (goalY() - leadY) * near
        lagX += (leadX - lagX) * far
        lagY += (leadY - lagY) * far
    }

    private fun goalX() = if (finger) fingerX else cos(theta) * RING

    private fun goalY() = if (finger) fingerY else sin(theta) * RING + DOT

    private fun settle() {
        for (i in 0 until count) {
            pull[i] = if (i == hot) 1f else 0f
            speed[i] = 0f
        }
        shown = hot
        face = 1f
        faceSpeed = 0f
        faceTarget = 1f
        bump = 0f
        bumpSpeed = 0f
        leadX = goalX()
        leadY = goalY()
        lagX = leadX
        lagY = leadY
    }
}

/**
 * A radial picker made of drops. A white disc in the middle shows the chosen mood large; the options
 * sit round it as small translucent circles with grey faces. The chosen one is opaque, in colour, and
 * joined to the disc by a neck, as two drops that touch. Dragging round the ring (or tapping an
 * option) moves an orange marker: the neck lets go of the circle it leaves and flows out to each
 * circle the marker reaches, and the face in the middle shrinks to a dot and pops back as the new one.
 *
 * @param options The options, clockwise from 12 o'clock.
 * @param selection The option chosen at first; [autoPlay] comes back to it at the end of every loop.
 * @param autoPlay Moves the marker round the ring by itself, in a loop.
 * @param onChange Called with the index of the option the marker reached.
 */
@Composable
fun GooeyRadialPicker(
    options: List<Mood> = Mood.entries,
    selection: Int = 2,
    autoPlay: Boolean = false,
    onChange: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val count = options.size
    if (count == 0) return
    val auto = autoPlay && !still && count > 1
    val rest = selection.coerceIn(0, count - 1)
    val view = LocalView.current
    val changed by rememberUpdatedState(onChange)

    val sim = remember(count, rest) { Sim(count, rest) }
    var selected by remember(sim) { mutableIntStateOf(rest) }
    // Counts frames, so the canvas is drawn again after each step.
    var frame by remember { mutableLongStateOf(0L) }
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }
    // A finger is on the picker.
    var holding by remember { mutableStateOf(false) }
    val shadow = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF8FA3B5).toArgb()
            maskFilter = BlurMaskFilter(sigmaToRadius(16f), BlurMaskFilter.Blur.NORMAL)
        }
    }

    LaunchedEffect(sim, still) {
        var before = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val hot = sim.hot
            sim.advance(min((now - before) / 1e9f, LONGEST_FRAME), still)
            before = now
            if (sim.hot != hot) {
                selected = sim.hot
                if (sim.finger) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                changed?.invoke(sim.hot)
            }
            frame += 1
        }
    }

    // The script a finger would follow: half a turn against the clock, two single steps, and the rest
    // of the turn back to where the loop started.
    LaunchedEffect(auto, touches, holding, sim) {
        if (!auto || holding) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, go back to the first option and start over.
            delay((RESUME_AFTER * 1000).toLong())
            sim.travelTo(rest, SWEEP_TIME)
            delay((SWEEP_TIME * 1000).toLong())
        }
        // Every move is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        while (true) {
            sim.home(rest)
            var gone = 0
            Script.forEachIndexed { n, move ->
                val due = begin + ((turn * LOOP + move.at) * 1e9).toLong()
                delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                // The last move finishes the turn, whatever the number of options.
                val sectors = if (n == Script.lastIndex) (count - gone % count) % count else max(1, (move.turn * count).roundToInt())
                gone += sectors
                if (sectors > 0) sim.travel(-sectors, move.time)
            }
            turn += 1
        }
    }

    Box(
        modifier
            .size(SIZE.dp)
            .semantics {
                contentDescription = "Mood"
                stateDescription = options[selected.coerceIn(0, count - 1)].label
            }
            .pointerInput(sim) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    holding = true
                    touches += 1
                    sim.point(down.position.x / density - SIZE / 2f, down.position.y / density - SIZE / 2f)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        sim.point(change.position.x / density - SIZE / 2f, change.position.y / density - SIZE / 2f)
                        change.consume()
                    }
                    sim.release()
                    holding = false
                    touches += 1
                }
            },
    ) {
        // The canvas is larger than the picker, so the shadow has room.
        Canvas(Modifier.fillMaxSize().bleed(BLEED.dp)) {
            // Reading the frame count here draws the canvas again after each step.
            if (frame < 0L) return@Canvas
            // Drawn in dp round the middle of the picker.
            scale(density, density, pivot = Offset.Zero) {
                translate(BLEED + SIZE / 2f, BLEED + SIZE / 2f) { drawPicker(sim, options, shadow) }
            }
        }
    }
}

private fun DrawScope.drawPicker(sim: Sim, options: List<Mood>, shadow: Paint) {
    val count = sim.count
    val disc = DISC + sim.bump
    val centers = List(count) { i ->
        val angle = sim.angle(i)
        val radius = RING - REACH * sim.pull[i].coerceIn(0f, 1.2f)
        Offset(cos(angle) * radius, sin(angle) * radius)
    }
    val necks = List(count) { i -> if (sim.pull[i] > 0.02f) neck(disc, centers[i], DOT, 0.5f * min(sim.pull[i], 1.3f)) else null }

    for (center in centers) drawCircle(Color.White, DOT, center, alpha = 0.55f)

    // The disc, the necks and the circles they hold are one shape with one shadow: its parts are
    // blurred into one layer, so they do not darken each other where they overlap.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val reach = RING + DOT + BLEED
            native.saveLayerAlpha(-reach, -reach, reach, reach, (0.45f * 255).roundToInt())
            native.translate(12f, 16f)
            shadow.alpha = 255
            native.drawCircle(0f, 0f, disc, shadow)
            for (i in 0 until count) {
                val path = necks[i] ?: continue
                native.drawPath(path.asAndroidPath(), shadow)
                shadow.alpha = (clamp01(sim.pull[i] * 2.5f) * 255).roundToInt()
                native.drawCircle(centers[i].x, centers[i].y, DOT, shadow)
                shadow.alpha = 255
            }
            native.restore()
        }
    }
    drawCircle(Color.White, disc, Offset.Zero)
    for (i in 0 until count) {
        val path = necks[i] ?: continue
        drawPath(path, Color.White)
        drawCircle(Color.White, DOT, centers[i], alpha = clamp01(sim.pull[i] * 2.5f))
    }

    for (i in 0 until count) drawFace(options[i], centers[i], SMALL, clamp01(sim.pull[i]))
    if (sim.face > 0.03f && sim.shown in 0 until count) drawFace(options[sim.shown], Offset.Zero, LARGE * sim.face, 1f)

    drawMarker(sim.lagX, sim.lagY, sim.leadX, sim.leadY)
}

/**
 * The bridge between a circle of radius [r1] at the origin and one of radius [r2] at [c2]: two curves
 * that leave each circle along its edge and meet in between. [v] is how far round the circles the
 * bridge takes hold, 0 a thread … 0.5 a thick neck.
 */
private fun neck(r1: Float, c2: Offset, r2: Float, v: Float): Path? {
    val d = hypot(c2.x, c2.y)
    if (d <= abs(r1 - r2) || v <= 0f) return null
    // Where the two circles cross, when they overlap.
    val overlap = d < r1 + r2
    val u1 = if (overlap) acos((r1 * r1 + d * d - r2 * r2) / (2f * r1 * d)) else 0f
    val u2 = if (overlap) acos((r2 * r2 + d * d - r1 * r1) / (2f * r2 * d)) else 0f
    val base = atan2(c2.y, c2.x)
    val spread = acos((r1 - r2) / d)
    val a1 = base + u1 + (spread - u1) * v
    val a2 = base - u1 - (spread - u1) * v
    val a3 = base + PI - u2 - (PI - u2 - spread) * v
    val a4 = base - PI + u2 + (PI - u2 - spread) * v
    fun polar(center: Offset, angle: Float, radius: Float) = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
    val p1 = polar(Offset.Zero, a1, r1)
    val p2 = polar(Offset.Zero, a2, r1)
    val p3 = polar(c2, a3, r2)
    val p4 = polar(c2, a4, r2)
    // The curves' handles lie along the circles' edges, longer when the bridge is longer.
    val handle = min(v * 2.4f, hypot(p1.x - p3.x, p1.y - p3.y) / (r1 + r2)) * min(1f, d * 2f / (r1 + r2))
    val h1 = polar(p1, a1 - PI / 2f, r1 * handle)
    val h2 = polar(p2, a2 + PI / 2f, r1 * handle)
    val h3 = polar(p3, a3 + PI / 2f, r2 * handle)
    val h4 = polar(p4, a4 - PI / 2f, r2 * handle)
    return Path().apply {
        moveTo(p1.x, p1.y)
        cubicTo(h1.x, h1.y, h3.x, h3.y, p3.x, p3.y)
        lineTo(p4.x, p4.y)
        cubicTo(h4.x, h4.y, h2.x, h2.y, p2.x, p2.y)
        close()
    }
}

/** The marker: a ring at rest, stretched into a pill between its tail and its head as it moves. */
private fun DrawScope.drawMarker(tailX: Float, tailY: Float, headX: Float, headY: Float) {
    val length = hypot(headX - tailX, headY - tailY)
    // The hole closes as the pill gets longer.
    val line = mix(MARKER_LINE, MARKER, clamp01(length / 26f))
    val r = MARKER - line / 2f
    val ux = if (length > 0.01f) (headX - tailX) / length else 1f
    val uy = if (length > 0.01f) (headY - tailY) / length else 0f
    val k = 0.5523f * r
    // A point `along` the pill's axis and `across` it from the head or the tail.
    fun x(head: Boolean, along: Float, across: Float) = (if (head) headX else tailX) + ux * along - uy * across
    fun y(head: Boolean, along: Float, across: Float) = (if (head) headY else tailY) + uy * along + ux * across
    val path = Path().apply {
        moveTo(x(false, 0f, r), y(false, 0f, r))
        lineTo(x(true, 0f, r), y(true, 0f, r))
        cubicTo(x(true, k, r), y(true, k, r), x(true, r, k), y(true, r, k), x(true, r, 0f), y(true, r, 0f))
        cubicTo(x(true, r, -k), y(true, r, -k), x(true, k, -r), y(true, k, -r), x(true, 0f, -r), y(true, 0f, -r))
        lineTo(x(false, 0f, -r), y(false, 0f, -r))
        cubicTo(x(false, -k, -r), y(false, -k, -r), x(false, -r, -k), y(false, -r, -k), x(false, -r, 0f), y(false, -r, 0f))
        cubicTo(x(false, -r, k), y(false, -r, k), x(false, -k, r), y(false, -k, r), x(false, 0f, r), y(false, 0f, r))
        close()
    }
    drawPath(path, Color(0xFFF2B45A), alpha = 0.8f, style = Stroke(line))
}

/** A face's colours: in colour, or the grey of an option that is not chosen. */
private class Palette(val fill: Color, val side: Color, val ink: Color, val cheek: Color)

private val InColor = Palette(Color(0xFFFBDD2E), Color(0xFFF2B03A), Color(0xFF4A3A2A), Color(0xFFF79E86))
private val InGrey = Palette(Color(0xFFC9CDD2), Color(0xFFAEB2B8), Color(0xFF8E9399), Color(0x00F79E86))

private enum class Eye { Dot, Up, Down, Line }

private enum class Mouth { Smile, Grin, Round, Slant, Frown, Flat }

private class Look(val left: Eye, val right: Eye, val mouth: Mouth, val cheeks: Boolean)

private fun look(mood: Mood) = when (mood) {
    Mood.Happy -> Look(Eye.Dot, Eye.Dot, Mouth.Smile, true)
    Mood.Joyful -> Look(Eye.Up, Eye.Up, Mouth.Grin, false)
    Mood.Calm -> Look(Eye.Down, Eye.Down, Mouth.Smile, true)
    Mood.Playful -> Look(Eye.Dot, Eye.Up, Mouth.Grin, true)
    Mood.Surprised -> Look(Eye.Dot, Eye.Dot, Mouth.Round, false)
    Mood.Unsure -> Look(Eye.Dot, Eye.Dot, Mouth.Slant, false)
    Mood.Sad -> Look(Eye.Dot, Eye.Dot, Mouth.Frown, false)
    Mood.Sleepy -> Look(Eye.Line, Eye.Line, Mouth.Flat, false)
}

/** A mood's face, drawn in units of its radius [r] round [center]. [tint] 0 draws it grey, 1 in colour. */
private fun DrawScope.drawFace(mood: Mood, center: Offset, r: Float, tint: Float) {
    fun at(x: Float, y: Float) = Offset(center.x + x * r, center.y + y * r)
    val look = look(mood)
    val ink = lerp(InGrey.ink, InColor.ink, tint)
    val pen = Stroke(0.1f * r, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // A second disc behind gives the face a thick edge, down and to the right.
    drawCircle(lerp(InGrey.side, InColor.side, tint), r, at(0.07f, 0.09f))
    drawCircle(lerp(InGrey.fill, InColor.fill, tint), r, center)
    if (look.cheeks && tint > 0f) {
        drawCircle(InColor.cheek, 0.14f * r, at(-0.6f, 0.2f), alpha = tint)
        drawCircle(InColor.cheek, 0.14f * r, at(0.6f, 0.2f), alpha = tint)
    }

    val y = -0.14f
    for ((eye, x) in listOf(look.left to -0.34f, look.right to 0.34f)) {
        val line = Path()
        when (eye) {
            Eye.Dot -> drawCircle(ink, 0.11f * r, at(x, y))
            Eye.Up -> {
                val (from, bend, end) = Triple(at(x - 0.15f, y + 0.06f), at(x, y - 0.2f), at(x + 0.15f, y + 0.06f))
                line.moveTo(from.x, from.y)
                line.quadraticTo(bend.x, bend.y, end.x, end.y)
            }
            Eye.Down -> {
                val (from, bend, end) = Triple(at(x - 0.15f, y - 0.04f), at(x, y + 0.2f), at(x + 0.15f, y - 0.04f))
                line.moveTo(from.x, from.y)
                line.quadraticTo(bend.x, bend.y, end.x, end.y)
            }
            Eye.Line -> {
                line.moveTo(at(x - 0.14f, y).x, at(x - 0.14f, y).y)
                line.lineTo(at(x + 0.14f, y).x, at(x + 0.14f, y).y)
            }
        }
        if (eye != Eye.Dot) drawPath(line, ink, style = pen)
    }

    val mouth = Path()
    fun Path.from(x: Float, y: Float) = at(x, y).let { moveTo(it.x, it.y) }
    fun Path.line(x: Float, y: Float) = at(x, y).let { lineTo(it.x, it.y) }
    fun Path.bend(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(at(cx, cy).x, at(cx, cy).y, at(x, y).x, at(x, y).y)
    when (look.mouth) {
        Mouth.Smile -> {
            mouth.from(-0.28f, 0.26f)
            mouth.bend(0f, 0.6f, 0.28f, 0.26f)
        }
        Mouth.Grin -> {
            mouth.from(-0.34f, 0.24f)
            mouth.line(0.34f, 0.24f)
            mouth.cubicTo(at(0.32f, 0.7f).x, at(0.32f, 0.7f).y, at(-0.32f, 0.7f).x, at(-0.32f, 0.7f).y, at(-0.34f, 0.24f).x, at(-0.34f, 0.24f).y)
            mouth.close()
            drawPath(mouth, ink)
        }
        Mouth.Round -> {
            drawOval(ink, topLeft = at(-0.14f, 0.22f), size = Size(0.28f * r, 0.36f * r))
            return
        }
        Mouth.Slant -> {
            mouth.from(-0.22f, 0.46f)
            mouth.line(0.22f, 0.34f)
        }
        Mouth.Frown -> {
            mouth.from(-0.26f, 0.5f)
            mouth.bend(0f, 0.22f, 0.26f, 0.5f)
        }
        Mouth.Flat -> {
            mouth.from(-0.2f, 0.4f)
            mouth.line(0.2f, 0.4f)
        }
    }
    drawPath(mouth, ink, style = pen)
}
