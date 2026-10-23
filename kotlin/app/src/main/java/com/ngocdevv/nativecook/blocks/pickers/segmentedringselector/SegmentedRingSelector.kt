package com.ngocdevv.nativecook.blocks.pickers.segmentedringselector

import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The picture an item shows on the menu ring while it is not selected. */
enum class RingGlyph { Paint, Wheel, Steering, Lock, Battery, Gauge, Car, Music, Reset }

/**
 * @param tier the small line under the name.
 * @param swatch the option's block on the outer ring.
 * @param accent the colour the wedge and the small line take while the option is chosen.
 */
data class RingOption(val name: String, val tier: String, val swatch: Color, val accent: Color)

data class RingItem(val label: String, val glyph: RingGlyph, val options: List<RingOption>)

private const val SIZE = 340f
private const val CENTER = SIZE / 2f

// Radii, from the disc outwards.
private const val DISC_RADIUS = 92f
private const val RING_OUTER = 134f
private const val ITEM_RADIUS = 113f
private const val BAND_INNER = 142f
private const val BAND_OUTER = 155f
private const val LIT_OUTER = 168f

// Degrees.
private const val ITEM_STEP = 40f
private const val WEDGE_HALF = 22f
private const val BLOCK_STEP = 12f
private const val BLOCK_HALF = 5.4f
private const val LIT_HALF = 6.6f

// The band is whole down to `FADE_START` from 12 o'clock and gone by `FADE_END`.
private const val FADE_START = 60f
private const val FADE_END = 150f
private const val BLOCKS_ASIDE = 13

// How much later each block further from the index opens, as a share of the fan.
private const val FAN_STAGGER = 0.03f
private const val TAB_HALF = 3f
private const val TAB_RISE = 8f
private const val NAME_SLIDE = 6f

// Snapping: a spring with a response of 0.4 s and a damping ratio of 0.75.
private const val SNAP_STIFFNESS = 246.74f
private const val SNAP_DAMPING_RATIO = 0.75f

// Seconds of the finger's speed a ring coasts for.
private const val COAST = 0.2f

// Dp a finger may move before a tap becomes a drag.
private const val TAP_SLOP = 4f

// autoPlay (seconds): when each move starts, which ring it turns and by how many places.
private const val LOOP = 8f

private class Step(val at: Float, val menu: Boolean, val by: Int)

private val Script = listOf(
    Step(1.0f, false, 1),
    Step(1.45f, false, 1),
    Step(2.7f, true, 1),
    Step(4.1f, false, 1),
    Step(5.2f, true, -1),
    Step(5.75f, false, -2),
)

// After a touch the loop waits this long, then takes `HOME_TIME` for each ring to return.
private const val RESUME_AFTER = 2f
private const val HOME_TIME = 0.7f

private val Slate = Color(0xFF3A4046)
private val RingGrey = Color(0xFFD6D6D6)
private val Band = Color(0xFF9AAEB0)

private val Items = listOf(
    RingItem(
        "Paint", RingGlyph.Paint,
        listOf(
            RingOption("Solar Orange", "SIGNATURE", Color(0xFFFFA400), Color(0xFFF5470A)),
            RingOption("Glacier Blue", "SIGNATURE", Color(0xFF5BC0F5), Color(0xFF1E7FD6)),
            RingOption("Moss Green", "HERITAGE", Color(0xFF8CC152), Color(0xFF4E8F2E)),
            RingOption("Graphite", "CLASSIC", Color(0xFF7A828C), Color(0xFF4A525C)),
        ),
    ),
    RingItem(
        "Wheels", RingGlyph.Wheel,
        listOf(
            RingOption("Ten Spoke", "FORGED", Color(0xFFD9A441), Color(0xFFB8791F)),
            RingOption("Turbine", "FORGED", Color(0xFFC9CED3), Color(0xFF6C7A89)),
            RingOption("Mesh", "CAST", Color(0xFF5A6068), Color(0xFF2F3338)),
        ),
    ),
    RingItem(
        "Steering", RingGlyph.Steering,
        listOf(
            RingOption("Light", "COMFORT", Color(0xFF7ED6C8), Color(0xFF1FA391)),
            RingOption("Balanced", "COMFORT", Color(0xFF5BC0F5), Color(0xFF1E7FD6)),
            RingOption("Heavy", "SPORT", Color(0xFFF58A6B), Color(0xFFE0362C)),
        ),
    ),
    RingItem(
        "Locks", RingGlyph.Lock,
        listOf(
            RingOption("Auto Lock", "SECURE", Color(0xFF8CC152), Color(0xFF4E8F2E)),
            RingOption("Walk Away", "SECURE", Color(0xFFFFC44D), Color(0xFFD68A00)),
            RingOption("Valet", "LIMITED", Color(0xFFF58A6B), Color(0xFFE0362C)),
        ),
    ),
    RingItem(
        "Charge", RingGlyph.Battery,
        listOf(
            RingOption("Daily 80%", "BATTERY CARE", Color(0xFF8CC152), Color(0xFF4E8F2E)),
            RingOption("Long Trip", "FULL CHARGE", Color(0xFF5BC0F5), Color(0xFF1E7FD6)),
            RingOption("Off Peak", "SCHEDULED", Color(0xFFB79CF2), Color(0xFF7A4FD6)),
        ),
    ),
    RingItem(
        "Drive", RingGlyph.Gauge,
        listOf(
            RingOption("Comfort", "ROAD", Color(0xFF5BC0F5), Color(0xFF1E7FD6)),
            RingOption("Sport", "ROAD", Color(0xFFFFA400), Color(0xFFF5470A)),
            RingOption("Track", "CIRCUIT", Color(0xFFF5606B), Color(0xFFD61E3C)),
        ),
    ),
    RingItem(
        "Body", RingGlyph.Car,
        listOf(
            RingOption("Coupe", "FIXED ROOF", Color(0xFFC9CED3), Color(0xFF6C7A89)),
            RingOption("Spider", "OPEN ROOF", Color(0xFFFFC44D), Color(0xFFD68A00)),
        ),
    ),
    RingItem(
        "Sound", RingGlyph.Music,
        listOf(
            RingOption("Studio", "FLAT", Color(0xFFB79CF2), Color(0xFF7A4FD6)),
            RingOption("Concert", "WIDE", Color(0xFFF59AC4), Color(0xFFD63A8A)),
            RingOption("Lounge", "WARM", Color(0xFFFFC44D), Color(0xFFD68A00)),
        ),
    ),
    RingItem(
        "Reset", RingGlyph.Reset,
        listOf(
            RingOption("Keep Setup", "NO CHANGE", Color(0xFFC9CED3), Color(0xFF6C7A89)),
            RingOption("Factory", "ERASE ALL", Color(0xFFF5606B), Color(0xFFD61E3C)),
        ),
    ),
)

private val LabelStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, platformStyle = PlatformTextStyle(includeFontPadding = false))
private val NameStyle = TextStyle(color = Slate, fontSize = 22.sp, fontWeight = FontWeight.Light, letterSpacing = 0.4.sp, platformStyle = PlatformTextStyle(includeFontPadding = false))
private val TierStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, platformStyle = PlatformTextStyle(includeFontPadding = false))

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun smooth(t: Float) = t * t * (3f - 2f * t)

/** [index] brought into 0..<count, for a ring that goes round. */
private fun wrap(index: Int, count: Int) = ((index % count) + count) % count

/** [value] brought into −span/2…span/2: the short way round. */
private fun wrapped(value: Float, span: Float) = value - span * floor((value + span / 2f) / span)

/** A point [radius] from the centre at [angle] degrees, 0 at 12 o'clock and growing clockwise. */
private fun pointAt(angle: Float, radius: Float): Offset {
    val turn = angle * Math.PI.toFloat() / 180f
    return Offset(CENTER + radius * sin(turn), CENTER - radius * cos(turn))
}

/** A point's angle around the centre in degrees: 0 at 12 o'clock, growing clockwise, −180…180. */
private fun bearing(x: Float, y: Float) = atan2(x - CENTER, CENTER - y) * 180f / Math.PI.toFloat()

/** The piece of a ring between two angles and two radii. */
private fun sector(from: Float, to: Float, inner: Float, outer: Float) = Path().apply {
    val steps = max(2, ceil((to - from) / 2f).toInt())
    for (n in 0..steps) {
        val at = pointAt(mix(from, to, n / steps.toFloat()), outer)
        if (n == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
    }
    for (n in 0..steps) {
        val at = pointAt(mix(to, from, n / steps.toFloat()), inner)
        lineTo(at.x, at.y)
    }
    close()
}

/** The colour a tenth darker, for the foot of a block. */
private fun shade(colour: Color) = Color(colour.red * 0.9f, colour.green * 0.9f, colour.blue * 0.9f)

/** What moves. */
private class RingMotion(count: Int, home: Int, option: Int) {
    /**
     * The menu ring's position as an item index, and the outer ring's as an option index. Both keep
     * counting past the last one: the rings go round.
     */
    val menu = Animatable(home.toFloat())
    val value = Animatable(option.toFloat())

    /** The item under the index, and each item's chosen place on its outer ring. */
    var item by mutableIntStateOf(home)
    val chosen = mutableStateListOf<Int>().apply { repeat(count) { add(if (it == home) option else 0) } }

    /** A ring is held. */
    var held = false

    /** Where each ring was at the last frame, for the haptic ticks. */
    var menuTick = home
    var valueTick = option

    /** The turns come from a finger, so they tick; the script's do not. */
    var felt = false
}

/**
 * Two concentric wheels around a white disc, with one fixed index at 12 o'clock. The inner, grey ring
 * carries the items as glyphs; the one under the index sits in a coloured wedge with its name in
 * white. The outer ring is a band of small blocks, the options of that item: the block under the
 * index stands taller in its own colour, and the disc names it. Dragging either ring turns it, and it
 * snaps on a spring; when the item changes, the outer blocks fan shut towards 12 and open again.
 *
 * @param initialItem Index of the item under the index at first; [initialOption] is its chosen option.
 * @param autoPlay Steps the two rings by itself, in a loop.
 * @param onSelect Called with the item's index and its option's index when either ring is sent to a new place.
 */
@Composable
fun SegmentedRingSelector(
    items: List<RingItem> = Items,
    initialItem: Int = 0,
    initialOption: Int = 0,
    autoPlay: Boolean = false,
    onSelect: ((item: Int, option: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still && items.size > 1
    val home = initialItem.coerceIn(0, items.lastIndex)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val motion = remember(items, home, initialOption) { RingMotion(items.size, home, initialOption) }
    val selected by rememberUpdatedState(onSelect)
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    fun snap(ring: Animatable<Float, AnimationVector1D>, to: Float, velocity: Float) {
        scope.launch {
            if (still) ring.snapTo(to)
            else ring.animateTo(to, spring(dampingRatio = SNAP_DAMPING_RATIO, stiffness = SNAP_STIFFNESS, visibilityThreshold = 0.0005f), initialVelocity = velocity)
        }
    }

    // Turns the menu ring until place `position` is under the index.
    fun selectItem(position: Int, velocity: Float = 0f) {
        snap(motion.menu, position.toFloat(), velocity)
        val index = wrap(position, items.size)
        selected?.invoke(index, wrap(motion.chosen[index], items[index].options.size))
    }

    // Turns the outer ring until place `position` is under the index, and keeps it as the item's choice.
    fun selectOption(position: Int, velocity: Float = 0f) {
        motion.chosen[motion.item] = position
        snap(motion.value, position.toFloat(), velocity)
        selected?.invoke(motion.item, wrap(position, items[motion.item].options.size))
    }

    // The option the item on the other side of the hand-over has chosen.
    fun otherOption(off: Float): RingOption {
        val next = wrap(motion.item + if (off < 0f) -1 else 1, items.size)
        return items[next].options[wrap(motion.chosen[next], items[next].options.size)]
    }

    // The wedge's colour: the chosen option's, cross-fading with the next option's as the outer ring
    // turns and with the next item's as the menu ring does.
    fun tone(): Color {
        val options = items[motion.item].options
        val value = motion.value.value
        val k = floor(value).toInt()
        val off = motion.menu.value - motion.menu.value.roundToInt()
        val own = lerp(options[wrap(k, options.size)].accent, options[wrap(k + 1, options.size)].accent, smooth(value - k))
        return lerp(own, otherOption(off).accent, abs(off))
    }

    // Ticks as items and blocks pass the index, and hands the outer ring over to the item that has come under it.
    LaunchedEffect(motion) {
        launch {
            snapshotFlow { motion.menu.value.roundToInt() }.collect { now ->
                if (now == motion.menuTick) return@collect
                motion.menuTick = now
                motion.item = wrap(now, items.size)
                motion.valueTick = motion.chosen[motion.item]
                motion.value.snapTo(motion.chosen[motion.item].toFloat())
                if (motion.felt) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        snapshotFlow { motion.value.value.roundToInt() }.collect { now ->
            if (now == motion.valueTick) return@collect
            motion.valueTick = now
            if (motion.felt) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // The script a finger would follow: two options on, the next item, one option on, and back.
    LaunchedEffect(auto, touches, motion) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, turn both rings back to the resting choice, and start over from there.
            delay((RESUME_AFTER * 1000).toLong())
            if (motion.held) return@LaunchedEffect
            motion.felt = false
            selectItem(motion.menuTick + wrapped((home - motion.menuTick).toFloat(), items.size.toFloat()).roundToInt())
            delay((HOME_TIME * 1000).toLong())
            selectOption(motion.valueTick + wrapped((initialOption - motion.valueTick).toFloat(), items[home].options.size.toFloat()).roundToInt())
            delay((HOME_TIME * 1000).toLong())
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        while (true) {
            // The items that are out of sight go back to their first option.
            for (index in items.indices) if (index != motion.item) motion.chosen[index] = 0
            for (step in Script) {
                val due = begin + ((turn * LOOP + step.at) * 1e9).toLong()
                delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                if (step.menu) selectItem(motion.menuTick + step.by) else selectOption(motion.chosen[motion.item] + step.by)
            }
            turn += 1
        }
    }

    Box(
        modifier
            .size(SIZE.dp)
            .pointerInput(motion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position / density
                    val reach = hypot(start.x - CENTER, start.y - CENTER)
                    if (reach <= DISC_RADIUS) return@awaitEachGesture
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    // The finger catches the ring it lands on, wherever that ring is.
                    val onMenu = reach <= RING_OUTER + 3f
                    val ring = if (onMenu) motion.menu else motion.value
                    val step = if (onMenu) ITEM_STEP else BLOCK_STEP
                    touches += 1
                    motion.held = true
                    motion.felt = true
                    scope.launch { ring.stop() }
                    val from = ring.value
                    var position = from
                    var at = start
                    var angle = bearing(start.x, start.y)
                    var turned = 0f
                    var dragged = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        at = change.position / density
                        if (!dragged && hypot(at.x - start.x, at.y - start.y) > TAP_SLOP) {
                            dragged = true
                            angle = bearing(at.x, at.y)
                        }
                        if (dragged) {
                            change.consume()
                            val now = bearing(at.x, at.y)
                            turned += wrapped(now - angle, 360f)
                            angle = now
                            position = from - turned / step
                            scope.launch { ring.snapTo(position) }
                        }
                    }
                    touches += 1
                    motion.held = false
                    var speed = 0f
                    val target = if (dragged) {
                        // Degrees a second around the centre, from the finger's speed across its radius.
                        val velocity = tracker.calculateVelocity()
                        val arm = Offset(at.x - CENTER, at.y - CENTER)
                        val far = max(40f, hypot(arm.x, arm.y))
                        val spin = (arm.x * velocity.y - arm.y * velocity.x) / density / (far * far) * 180f / Math.PI.toFloat()
                        speed = -spin / step
                        (position + speed * COAST).roundToInt()
                    } else {
                        // A tap brings the item or the block under it to the index.
                        (position + bearing(start.x, start.y) / step).roundToInt()
                    }
                    if (onMenu) selectItem(target, speed) else selectOption(target, speed)
                }
            },
    ) {
        // The outer blocks, the grey ring, the wedge with its tab, and the disc.
        Canvas(Modifier.fillMaxSize()) {
            val menu = motion.menu.value
            val value = motion.value.value
            val options = items[motion.item].options
            val off = menu - menu.roundToInt()
            // The blocks fan shut towards 12 as the menu leaves an item, and open again as it reaches the next.
            val fan = smooth(clamp01(1f - 2.5f * abs(off)))
            val other = otherOption(off)
            scale(density, density, pivot = Offset.Zero) {
                val first = floor(value).toInt() - BLOCKS_ASIDE
                for (pass in 0..1) {
                    for (j in first..first + BLOCKS_ASIDE * 2 + 1) {
                        val d = j - value
                        // The block under the index stands taller, a little wider, in its own colour.
                        val lit = clamp01(1.5f - 2f * abs(d))
                        if ((lit > 0f) != (pass == 1)) continue
                        // Blocks nearer the index open first.
                        val open = clamp01((fan - FAN_STAGGER * abs(d)) / (1f - FAN_STAGGER * BLOCKS_ASIDE))
                        val angle = d * BLOCK_STEP * if (lit >= 1f) 1f else open
                        val fade = clamp01((FADE_END - abs(d) * BLOCK_STEP) / (FADE_END - FADE_START)) * max(open, lit)
                        if (fade <= 0f) continue
                        val half = mix(BLOCK_HALF, LIT_HALF, lit)
                        val outer = mix(BAND_OUTER, LIT_OUTER, lit)
                        val path = sector(angle - half, angle + half, BAND_INNER, outer)
                        drawPath(path, Band, alpha = (if (abs(j) % 2 == 0) 0.7f else 0.45f) * fade * (1f - lit))
                        if (lit > 0f) {
                            val swatch = options[wrap(j, options.size)].swatch
                            drawPath(
                                path,
                                Brush.linearGradient(
                                    listOf(lerp(swatch, other.swatch, abs(off)), lerp(shade(swatch), shade(other.swatch), abs(off))),
                                    start = pointAt(angle, outer),
                                    end = pointAt(angle, BAND_INNER),
                                ),
                                alpha = lit,
                            )
                        }
                    }
                }

                drawCircle(RingGrey, (DISC_RADIUS + RING_OUTER) / 2f, Offset(CENTER, CENTER), style = Stroke(width = RING_OUTER - DISC_RADIUS))

                val wedge = sector(-WEDGE_HALF, WEDGE_HALF, DISC_RADIUS - 1f, RING_OUTER).apply {
                    val left = pointAt(-TAB_HALF, RING_OUTER - 0.5f)
                    val tip = pointAt(0f, RING_OUTER + TAB_RISE)
                    val right = pointAt(TAB_HALF, RING_OUTER - 0.5f)
                    moveTo(left.x, left.y)
                    lineTo(tip.x, tip.y)
                    lineTo(right.x, right.y)
                    close()
                }
                drawPath(wedge, tone())
                // A little darker towards the disc.
                drawPath(
                    wedge,
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.08f)), startY = CENTER - RING_OUTER, endY = CENTER - DISC_RADIUS),
                )

                // The two glyphs no icon set shares are drawn here, where their items stand.
                items.forEachIndexed { index, item ->
                    if (item.glyph != RingGlyph.Wheel && item.glyph != RingGlyph.Steering) return@forEachIndexed
                    val d = wrapped(index - menu, items.size.toFloat())
                    drawGlyph(item.glyph, pointAt(d * ITEM_STEP, ITEM_RADIUS), 1f - clamp01(1f - abs(d) * 2.2f))
                }

                val shadow = Offset(CENTER, CENTER + 6f)
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Black.copy(alpha = 0.2f), 0.72f to Color.Black.copy(alpha = 0.2f), 1f to Color.Transparent,
                        center = shadow, radius = DISC_RADIUS + 24f,
                    ),
                    radius = DISC_RADIUS + 24f, center = shadow,
                )
                drawCircle(Color.White, DISC_RADIUS, Offset(CENTER, CENTER))
            }
        }

        items.forEachIndexed { index, item ->
            val symbol = symbolOf(item.glyph)
            // Under the index the glyph gives way to the item's name in white.
            if (symbol != null) {
                Image(
                    symbol,
                    contentDescription = null,
                    modifier = Modifier
                        .offset {
                            val at = pointAt(wrapped(index - motion.menu.value, items.size.toFloat()) * ITEM_STEP, ITEM_RADIUS)
                            IntOffset(((at.x - 13f) * density).roundToInt(), ((at.y - 13f) * density).roundToInt())
                        }
                        .size(26.dp)
                        .graphicsLayer { alpha = 1f - clamp01(1f - abs(wrapped(index - motion.menu.value, items.size.toFloat())) * 2.2f) },
                    colorFilter = ColorFilter.tint(Slate),
                )
            }
            Box(
                Modifier
                    .offset {
                        val at = pointAt(wrapped(index - motion.menu.value, items.size.toFloat()) * ITEM_STEP, ITEM_RADIUS)
                        IntOffset(((at.x - 40f) * density).roundToInt(), ((at.y - 15f) * density).roundToInt())
                    }
                    .size(80.dp, 30.dp)
                    .graphicsLayer { alpha = clamp01(1f - abs(wrapped(index - motion.menu.value, items.size.toFloat())) * 2.2f) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(item.label, style = LabelStyle, maxLines = 1)
            }
        }

        // The option's name and the small line under it, on the disc. Between two options one leaves
        // upwards as the next arrives from below; half-way between two items the disc is empty.
        val options = items[motion.item].options
        options.forEachIndexed { index, option ->
            Column(
                Modifier
                    .offset((CENTER - DISC_RADIUS).dp, (CENTER + 2f - 26f).dp)
                    .size((DISC_RADIUS * 2f).dp, 52.dp)
                    .graphicsLayer {
                        val d = wrapped(index - motion.value.value, options.size.toFloat())
                        val seen = clamp01(1f - 2.5f * abs(motion.menu.value - motion.menu.value.roundToInt()))
                        alpha = clamp01(1f - 2f * abs(d)) * seen
                        translationY = sign(d) * NAME_SLIDE * min(1f, 2f * abs(d)) * density
                    },
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(option.name, style = NameStyle, maxLines = 1)
                BasicText(option.tier, style = TierStyle, maxLines = 1, color = { tone() })
            }
        }
    }
}

private fun symbolOf(glyph: RingGlyph): ImageVector? = when (glyph) {
    RingGlyph.Paint -> Icons.Filled.Palette
    RingGlyph.Lock -> Icons.Filled.Lock
    RingGlyph.Battery -> Icons.Filled.BatteryChargingFull
    RingGlyph.Gauge -> Icons.Filled.Speed
    RingGlyph.Car -> Icons.Filled.DirectionsCar
    RingGlyph.Music -> Icons.Filled.MusicNote
    RingGlyph.Reset -> Icons.Filled.Replay
    RingGlyph.Wheel, RingGlyph.Steering -> null
}

/** The two glyphs no icon set shares: a spoked wheel and a steering wheel. */
private fun DrawScope.drawGlyph(glyph: RingGlyph, centre: Offset, alpha: Float) {
    if (alpha <= 0f) return
    if (glyph == RingGlyph.Wheel) {
        drawCircle(Slate, 11f, centre, alpha = alpha, style = Stroke(width = 2.6f))
        for (n in 0 until 8) {
            val turn = n / 8f * TAU
            drawLine(Slate, centre, Offset(centre.x + 11f * sin(turn), centre.y - 11f * cos(turn)), strokeWidth = 1.3f, alpha = alpha)
        }
        drawCircle(Slate, 2.8f, centre, alpha = alpha)
    } else {
        drawCircle(Slate, 11f, centre, alpha = alpha, style = Stroke(width = 2.8f))
        drawLine(Slate, Offset(centre.x - 11f, centre.y - 1f), Offset(centre.x + 11f, centre.y - 1f), strokeWidth = 2.6f, alpha = alpha)
        drawLine(Slate, centre, Offset(centre.x, centre.y + 11f), strokeWidth = 2.6f, alpha = alpha)
        drawCircle(Slate, 4f, Offset(centre.x, centre.y - 1f), alpha = alpha)
    }
}
