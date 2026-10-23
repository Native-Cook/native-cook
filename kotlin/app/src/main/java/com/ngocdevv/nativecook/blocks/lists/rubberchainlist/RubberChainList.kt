package com.ngocdevv.nativecook.blocks.lists.rubberchainlist

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/** One show in the list. */
data class ChainEvent(
    val id: String,
    val title: String,
    val venue: String,
    val city: String,
    val month: String,
    val day: String,
    val times: String,
    val price: String,
    val blurb: String,
)

private val Events = listOf(
    ChainEvent("photo-1459749411175-04bf5292ceea", "Saltwater Choir", "Harbor Hall", "Lisbon", "OCT", "18", "Doors 19:30 · Show 20:30", "from €28", "Twelve voices and a brass quartet, playing the new record from start to finish."),
    ChainEvent("photo-1563841930606-67e2bce48b78", "Neon Orchard", "The Lantern", "Porto", "OCT", "21", "Doors 20:00 · Show 21:00", "from €22", "A synth-pop four-piece on their first tour since the debut sold out twice."),
    ChainEvent("photo-1533174072545-7a4b6ad7a6c3", "Marigold Static", "Canal Room", "Amsterdam", "OCT", "24", "Doors 19:00 · Show 20:15", "from €30", "Fuzz guitars, a string section and a light show built from old slide projectors."),
    ChainEvent("photo-1493225457124-a3eb161ffa5f", "The Quiet Engines", "Vault 9", "Berlin", "OCT", "29", "Doors 22:00 · Set 23:30", "from €26", "An all-night live techno set from the duo who never play the same show twice."),
    ChainEvent("photo-1470229722913-7c0e2dbbafd3", "Ivy Lane Ensemble", "Glasshouse", "Copenhagen", "NOV", "02", "Doors 18:30 · Show 19:30", "from €35", "Chamber jazz in a greenhouse, with the audience seated among the palms."),
    ChainEvent("photo-1429962714451-bb934ecdc4ec", "Hollow Moon", "Riverside Shed", "Ghent", "NOV", "06", "Doors 19:30 · Show 20:30", "from €24", "Slow, heavy and very loud. Earplugs at the door, free of charge."),
    ChainEvent("photo-1514525253161-7a46d19cd819", "Copper & Kin", "Old Tram Depot", "Vienna", "NOV", "09", "Doors 19:00 · Show 20:00", "from €32", "A family folk band of three generations, back with fiddles and a full choir."),
    ChainEvent("photo-1565035010268-a3816f98589a", "Velvet Signal", "Sala Norte", "Madrid", "NOV", "14", "Doors 21:00 · Show 22:00", "from €27", "Dream pop under a ceiling of mirror balls, with support from Lake Vesper."),
    ChainEvent("photo-1524368535928-5b5e00ddc76b", "North Atlas", "Pier 4", "Hamburg", "NOV", "20", "Doors 19:30 · Show 20:45", "from €29", "Post-rock on the water: a three-hour set as the ferries go by."),
    ChainEvent("photo-1540039155733-5bb30b53aa14", "Paper Tigers Club", "The Annex", "Brussels", "NOV", "27", "Doors 20:00 · Show 21:00", "from €18", "Garage punk in a room for two hundred. Expect to leave with less voice."),
    ChainEvent("photo-1506157786151-b8491531f063", "Lumen Field Day", "Cinder Park", "Milan", "DEC", "04", "Gates 14:00 · Last set 23:00", "from €40", "Nine acts, two stages and a winter market between them."),
    ChainEvent("photo-1522158637959-30385a09e0da", "Saint Ember", "Chapel Works", "Dublin", "DEC", "12", "Doors 19:00 · Show 20:00", "from €25", "Candlelit piano and voice, closing the year where the songs were written."),
)

private fun thumb(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=220&h=220&q=80"

private val BACKGROUND = Color(0xFF0E0D12)
private val ROW = Color(0xFF18171D)
private val ROW_PRESSED = Color(0xFF26242D)
private val CHIP = Color(0xFF24222A)
private val TEXT = Color(0xFFF3EEE6)
private val MUTED = Color(0xFF8D8896)
private val ACCENT = Color(0xFFFFB547)

// Layout, in dp.
private const val SIDE = 16f
private const val ROW_H = 92.0
private const val GAP = 10.0
private const val EXTRA = 132.0
private const val THUMB = 68f
private const val RADIUS = 22f
private const val HEADER_H = 128f
private const val FADE = 28f

// The chain: each row is held to the list by its own spring and lags behind it while the list
// moves, LAG s of the list's speed for every row it is from the leading edge (up to LAG_MAX dp
// per row, RANK_MAX rows), so a fast list opens up like a stretched spring and closes again.
private const val LAG = 0.0105
private const val LAG_MAX = 42.0
private const val RANK_MAX = 9.0
private const val LINK_STIFFNESS = 110.0
private const val LINK_DAMPING = 14.7
private const val SPEED_SMOOTH = 14.0
// Past either end each row gives a little more than the one before it, OVER_SPREAD more per row
// from the end that is being pulled.
private const val OVER_SPREAD = 0.2
// The list: iOS-like deceleration, resistance past the ends over RUBBER_SPAN of the screen, and
// a critically damped spring back.
private const val DECELERATION = 2.1
private const val EDGE_STIFFNESS = 180.0
private const val EDGE_DAMPING = 26.8
private const val RUBBER_SPAN = 0.45
private const val DRAG_SLOP = 6f
private const val TAP_MS = 500L
// Opening a row: a spring on its height, with a little overshoot.
private const val OPEN_STIFFNESS = 210.0
private const val OPEN_DAMPING = 22.0
private const val PRESS_IN = 0.09
private const val PRESS_OUT = 0.22

// autoPlay (seconds).
private const val LOOP = 9.0
private const val FLING_UP_AT = 0.8
private const val FLING_DOWN_AT = 3.3
private const val FLING_SPEED = 3600.0
private const val PRESS_AT = 5.5
private const val OPEN_AT = 5.65
private const val CLOSE_AT = 7.4
private const val DEMO_ROW = 1
private const val USER_HOLD = 3.5
// The first fling waits this long after the screen opens, so the list rests while it arrives.
private const val ENTRY_DELAY = 1.2
// Only the rows that fit on screen are built with the screen; the rest follow one per frame after
// REST_AFTER s (or at once on the first touch), so the first frame stays light.
private const val REST_AFTER = 0.36
// The content settles in once: the header's words fade in and the rows fade up ENTER_RISE dp.
private const val ENTER_TIME = 0.32
private const val ENTER_RISE = 8f
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private fun clamp(x: Double, lo: Double, hi: Double) = min(hi, max(lo, x))
private fun rubber(x: Double, span: Double) = (1 - 1 / ((x * 0.55) / span + 1)) * span
private fun unrubber(r: Double, span: Double): Double {
    val k = min(0.999, r / span)
    return (span / 0.55) * (1 / (1 - k) - 1)
}

/** Colour between two, mixed with gamma 2.2 as Reanimated's `interpolateColor` mixes them. */
private fun mixColor(from: Color, to: Color, t: Float): Color {
    fun channel(a: Float, b: Float): Float {
        val linear = a.pow(2.2f) + (b.pow(2.2f) - a.pow(2.2f)) * t
        return (linear.pow(1f / 2.2f) * 255f).roundToInt() / 255f
    }
    return Color(channel(from.red, to.red), channel(from.green, to.green), channel(from.blue, to.blue))
}

/** The list and its rows, shared by the frame loop, the touches and the drawing. */
private class ChainMotion(val count: Int) {
    // The list's offset: 0 at the top, negative scrolled down; its speed and a smoothed speed.
    var scroll = 0.0
    var velocity = 0.0
    var smooth = 0.0
    var dragging = false
    var dragFrom = 0.0
    var last = 0.0
    // Each row's lag behind the list and its speed, how open it is, and where it is drawn.
    val lags = DoubleArray(count)
    val lagSpeeds = DoubleArray(count)
    val opens = DoubleArray(count)
    val openSpeeds = DoubleArray(count)
    val ys = DoubleArray(count) { it * (ROW_H + GAP) }
    // The press highlight of each row, 0…1.
    val glows = DoubleArray(count)
    var openIndex = -1
    var pressed = -1
    var byUser = false

    var clock = 0.0
    var looping = false
    var hold = 0.0
    var elapsed = 0.0
    // Whether the rows have been put at their resting places under the header yet.
    var placed = false
    // How many rows are built so far (see REST_AFTER), past the ones that fit on the first screen.
    var built by mutableIntStateOf(0)
    var firstRows = 0

    /** How far the content has settled in, 0 … 1 (eased). */
    fun shown(reduced: Boolean): Float = EaseOut.transform(clamp(elapsed / if (reduced) 0.2 else ENTER_TIME, 0.0, 1.0).toFloat())

    // The list's place on screen, in dp.
    var top = 0.0
    var bottom = 0.0

    // Bumped once a frame so the rows draw again.
    var frame by mutableLongStateOf(0L)

    /** The lowest the list may go, with the open rows' extra height. */
    fun floorOf(): Double {
        var open = 0.0
        for (e in opens) open += max(0.0, e)
        val content = count * (ROW_H + GAP) - GAP + open * EXTRA + 24
        return min(0.0, bottom - top - content)
    }

    fun bounded(raw: Double): Double {
        // At least 1 dp, so a stage not yet measured never divides by zero.
        val span = max(1.0, (bottom - top) * RUBBER_SPAN)
        val floor = floorOf()
        if (raw > 0) return rubber(raw, span)
        if (raw < floor) return floor - rubber(floor - raw, span)
        return raw
    }

    /** The row under a point, from where the rows are drawn. */
    fun rowAt(y: Double): Int {
        for (i in 0 until count) {
            if (y >= ys[i] && y <= ys[i] + ROW_H + EXTRA * max(0.0, opens[i])) return i
        }
        return -1
    }
}

/** One frame of the list: the script, the coasting list, the rows opening and the chain. */
private fun advance(m: ChainMotion, dt: Double, reducedMotion: Boolean, toggle: (Int) -> Unit) {
    val viewTop = m.top
    val viewBottom = m.bottom

    // The script flings and taps as a hand would.
    if (!m.dragging && m.hold > 0) {
        m.hold -= dt
        if (m.hold <= 0 && m.looping) {
            m.clock = 0.0
            if (m.openIndex >= 0) m.openIndex = -1
            // Home first, so the loop starts from the top.
            if (m.scroll < -1) m.velocity = FLING_SPEED
        }
    } else if (m.looping && !m.dragging) {
        val before = m.clock
        val t = (before + dt) % LOOP
        m.clock = t
        fun passed(at: Double) = before < at && t >= at
        if (passed(FLING_UP_AT) || passed(FLING_DOWN_AT)) {
            m.byUser = false
            m.velocity = if (passed(FLING_UP_AT)) -FLING_SPEED else FLING_SPEED
        }
        if (passed(PRESS_AT)) m.pressed = DEMO_ROW
        if (passed(OPEN_AT)) {
            m.pressed = -1
            m.byUser = false
            toggle(DEMO_ROW)
        }
        if (passed(CLOSE_AT) && m.openIndex == DEMO_ROW) {
            m.byUser = false
            toggle(DEMO_ROW)
        }
    }

    // The list itself.
    val floor = m.floorOf()
    if (!m.dragging) {
        val s = m.scroll
        var v = m.velocity
        if (s > 0.01 || s < floor - 0.01) {
            val edge = if (s > 0) 0.0 else floor
            v += (EDGE_STIFFNESS * (edge - s) - EDGE_DAMPING * v) * dt
            var next = s + v * dt
            if ((s > 0 && next < 0) || (s < floor && next > floor)) {
                next = edge
                v = 0.0
            }
            m.scroll = if (reducedMotion) edge else next
        } else {
            v *= exp(-DECELERATION * dt)
            if (abs(v) < 4) v = 0.0
            m.scroll = s + v * dt
        }
        m.velocity = v
    } else {
        m.velocity += ((m.scroll - m.last) / dt - m.velocity) * 0.5
    }
    m.last = m.scroll
    m.smooth += (m.velocity - m.smooth) * (1 - exp(-dt * SPEED_SMOOTH))

    // The open rows' heights.
    for (i in 0 until m.count) {
        val aim = if (m.openIndex == i) 1.0 else 0.0
        if (reducedMotion) {
            m.opens[i] = clamp(m.opens[i] + sign(aim - m.opens[i]) * dt * 5, min(aim, m.opens[i]), max(aim, m.opens[i]))
            m.openSpeeds[i] = 0.0
        } else {
            m.openSpeeds[i] += (OPEN_STIFFNESS * (aim - m.opens[i]) - OPEN_DAMPING * m.openSpeeds[i]) * dt
            m.opens[i] += m.openSpeeds[i] * dt
        }
    }
    // The highlight comes up fast under the finger and fades out slower.
    for (i in 0 until m.count) {
        val on = m.pressed == i
        val step = dt / (if (on) PRESS_IN else PRESS_OUT)
        m.glows[i] = if (on) min(1.0, m.glows[i] + step) else max(0.0, m.glows[i] - step)
    }

    // Each row: its place in the list, how far past an end the list is, and its lag.
    val s = m.scroll
    val over = if (s > 0) s else if (s < floor) s - floor else 0.0
    val v = if (reducedMotion) 0.0 else m.smooth
    val pitch = ROW_H + GAP
    var y = viewTop + s
    for (i in 0 until m.count) {
        val at = y + m.lags[i]
        // Rows count from the edge the list is moving away from: from the top while it moves up.
        val rank = if (v < 0) clamp((at - viewTop) / pitch, 0.0, RANK_MAX) else clamp((viewBottom - at - ROW_H) / pitch, 0.0, RANK_MAX)
        val aim = -sign(v) * min(abs(v) * LAG, LAG_MAX) * rank
        m.lagSpeeds[i] += (LINK_STIFFNESS * (aim - m.lags[i]) - LINK_DAMPING * m.lagSpeeds[i]) * dt
        m.lags[i] += m.lagSpeeds[i] * dt
        if (reducedMotion) {
            m.lags[i] = 0.0
            m.lagSpeeds[i] = 0.0
        }
        // Past an end, the rows further from it give more.
        val fromEnd = if (over > 0) clamp((at - viewTop) / pitch, 0.0, RANK_MAX) else clamp((viewBottom - at - ROW_H) / pitch, 0.0, RANK_MAX)
        val stretch = if (reducedMotion) 0.0 else over * fromEnd * OVER_SPREAD
        m.ys[i] = y + m.lags[i] + stretch
        y += pitch + EXTRA * max(0.0, m.opens[i])
    }
}

/**
 * A list of upcoming shows whose rows hang together like a chain of springs. Flung, the rows
 * drift apart, each lagging a little more than the one before it, and catch up with a springy
 * settle when the list slows; pulled past either end, the gaps open like a stretched spring.
 * Tapping a row highlights it and opens it on a spring to show the times, a line about the show
 * and its price.
 *
 * @param autoPlay Flings the list down and back up, then opens a row and closes it again.
 * @param onOpen Called with a show when its row is opened by hand.
 */
@Composable
fun RubberChainList(
    modifier: Modifier = Modifier,
    events: List<ChainEvent> = Events,
    autoPlay: Boolean = false,
    onOpen: ((ChainEvent) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val count = events.size
    val view = LocalView.current
    val density = LocalDensity.current
    val opening by rememberUpdatedState(onOpen)
    val motion = remember(events) { ChainMotion(count) }

    // Read during composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    val listTop = safeTop + HEADER_H

    /** Opens a row, closing the one that was open; or closes it if it is the open one. */
    fun toggle(index: Int) {
        val next = if (motion.openIndex == index) -1 else index
        motion.openIndex = next
        if (!motion.byUser) return
        if (next == index) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            opening?.invoke(events[index])
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        // The first fling waits for the screen to settle; later loops run from 0.
        motion.clock = -ENTRY_DELAY
        motion.hold = 0.0
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1.0 / 60 else min((now - last) / 1e9, 0.064)
                last = now
                if (dt > 0) {
                    motion.elapsed += dt
                    if (motion.elapsed >= REST_AFTER && motion.built < count) motion.built = max(motion.built, motion.firstRows) + 1
                }
                if (dt > 0 && motion.bottom > 0) advance(motion, dt, reducedMotion, ::toggle)
                motion.frame = now
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(BACKGROUND)
            .semantics { contentDescription = "$count upcoming shows. Tap a show to open it." }
            .pointerInput(motion, reducedMotion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    val m = motion
                    m.built = count
                    // A touch catches a moving list, as on iOS, and lights the row under it.
                    m.hold = USER_HOLD
                    m.velocity = 0.0
                    m.pressed = m.rowAt(start.y / density.density.toDouble())
                    var panning = false
                    var released = false
                    try {
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            val dx = (change.position.x - start.x) / density.density
                            val dy = (change.position.y - start.y) / density.density
                            if (!change.pressed) {
                                released = true
                                if (panning) {
                                    change.consume()
                                    if (m.dragging) {
                                        m.velocity = if (reducedMotion) 0.0 else tracker.calculateVelocity().y / density.density.toDouble()
                                        m.dragging = false
                                    }
                                } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                    val index = m.rowAt(change.position.y / density.density.toDouble())
                                    if (index >= 0) {
                                        m.byUser = true
                                        toggle(index)
                                    }
                                }
                                continue
                            }
                            if (!panning && sqrt(dx * dx + dy * dy) >= DRAG_SLOP) {
                                panning = true
                                m.hold = USER_HOLD
                                m.byUser = true
                                m.pressed = -1
                                val s = m.scroll
                                val span = max(1.0, (m.bottom - m.top) * RUBBER_SPAN)
                                val floor = m.floorOf()
                                m.dragFrom = when {
                                    s > 0 -> unrubber(s, span)
                                    s < floor -> floor - unrubber(floor - s, span)
                                    else -> s
                                }
                                m.last = s
                                m.dragging = true
                            }
                            if (panning) {
                                change.consume()
                                if (m.dragging) {
                                    m.hold = USER_HOLD
                                    m.scroll = m.bounded(m.dragFrom + dy)
                                }
                            }
                        }
                    } finally {
                        // Also when the gesture is cut off (cancelled pointer input): a finger lost
                        // to the system lets go of the list and the highlight.
                        m.pressed = -1
                        if (m.dragging) m.dragging = false
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        SideEffect {
            motion.top = listTop.toDouble()
            motion.bottom = (height - safeBottom).toDouble()
            // Every row starts at its resting place under the header, from the very first frame.
            if (!motion.placed) {
                motion.placed = true
                for (i in 0 until count) motion.ys[i] = listTop + i * (ROW_H + GAP)
            }
        }
        // Built with the screen: the rows that fit on it (the rest follow, see REST_AFTER).
        val firstRows = min(count, ceil((height - listTop) / (ROW_H + GAP)).toInt() + 1)
        motion.firstRows = firstRows
        val builtRows = max(motion.built, firstRows)
        val rowWidth = max(0f, width - SIDE * 2)

        // The rows settle in once: they fade up a few points when the screen opens.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    motion.frame
                    val shown = motion.shown(reducedMotion)
                    alpha = shown
                    translationY = if (reducedMotion) 0f else ((1f - shown) * ENTER_RISE).dp.toPx()
                },
        ) {
            events.forEachIndexed { index, event ->
                if (index < builtRows) ChainRow(event, index, motion, rowWidth, reducedMotion)
            }
        }

        // The header covers the rows that scroll under it; only its words fade in.
        Column(
            Modifier
                .fillMaxWidth()
                .height(listTop.dp)
                .background(BACKGROUND)
                .padding(start = (SIDE + 4).dp, end = (SIDE + 4).dp, top = (safeTop + 18).dp)
                .graphicsLayer {
                    motion.frame
                    alpha = motion.shown(reducedMotion)
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("LIVE NEAR YOU", style = TextStyle(color = ACCENT, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
            BasicText("Tonight & After", style = TextStyle(color = TEXT, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            BasicText("$count shows · October to December", style = TextStyle(color = MUTED, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }
        Box(
            Modifier
                .offset(y = (listTop - 1).dp)
                .fillMaxWidth()
                .height(FADE.dp)
                .background(Brush.verticalGradient(listOf(BACKGROUND, BACKGROUND.copy(alpha = 0f)))),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((safeBottom + FADE * 2).dp)
                .background(Brush.verticalGradient(listOf(BACKGROUND.copy(alpha = 0f), BACKGROUND))),
        )
    }
}

/** One show: the card grows on a spring when opened, and the details fade in inside it. */
@Composable
private fun ChainRow(event: ChainEvent, index: Int, motion: ChainMotion, width: Float, reducedMotion: Boolean) {
    val context = LocalContext.current
    Box(
        Modifier
            .graphicsLayer {
                motion.frame
                translationX = SIDE.dp.toPx()
                translationY = motion.ys[index].toFloat().dp.toPx()
            }
            .width(width.dp)
            .height((ROW_H + EXTRA).dp)
            .drawBehind {
                motion.frame
                val open = max(0.0, motion.opens[index])
                drawRoundRect(
                    mixColor(ROW, ROW_PRESSED, motion.glows[index].toFloat()),
                    size = Size(size.width, ((ROW_H + EXTRA * open) * density).toFloat()),
                    cornerRadius = CornerRadius(RADIUS.dp.toPx()),
                )
            },
    ) {
        Row(
            Modifier.fillMaxWidth().height(ROW_H.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = remember(event.id) { ImageRequest.Builder(context).data(thumb(event.id)).crossfade(250).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(THUMB.dp).clip(RoundedCornerShape(16.dp)).background(CHIP),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(event.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = TEXT, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp))
                BasicText("${event.venue} · ${event.city}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
            Column(
                Modifier.size(52.dp, 60.dp).background(CHIP, RoundedCornerShape(14.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(event.month, style = TextStyle(color = ACCENT, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
                BasicText(event.day, style = TextStyle(color = TEXT, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
            }
        }
        Column(
            Modifier
                .padding(start = 14.dp, end = 14.dp, top = (ROW_H - 2).dp)
                .graphicsLayer {
                    motion.frame
                    val e = clamp(motion.opens[index], 0.0, 1.0)
                    alpha = (e * e).toFloat()
                    translationY = if (reducedMotion) 0f else ((1 - e) * -10).toFloat().dp.toPx()
                },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(event.times, style = TextStyle(color = ACCENT, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
            BasicText(event.blurb, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(color = TEXT.copy(alpha = 0.78f), fontSize = 14.sp, lineHeight = 20.sp))
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(event.price, style = TextStyle(color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Box(Modifier.height(36.dp).background(ACCENT, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    BasicText("Tickets", style = TextStyle(color = Color(0xFF1A1408), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
