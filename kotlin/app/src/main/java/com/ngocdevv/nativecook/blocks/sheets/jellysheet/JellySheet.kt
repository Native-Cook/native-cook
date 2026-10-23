package com.ngocdevv.nativecook.blocks.sheets.jellysheet

import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tanh
import kotlinx.coroutines.launch

private fun unsplash(id: String, w: Int, h: Int) =
    "https://images.unsplash.com/$id?auto=format&fit=crop&w=$w&h=$h&q=80"

private val Hero = unsplash("photo-1549294413-26f195200c16", 1100, 2300)

private class Room(val name: String, val price: String, val photo: String)

private val Rooms = listOf(
    Room("Pool Villa", "€312", unsplash("photo-1562778612-e1e0cda9915c", 330, 380)),
    Room("Garden Suite", "€248", unsplash("photo-1587870306141-4f19861e6c73", 330, 380)),
    Room("Sea Loft", "€286", unsplash("photo-1586611292717-f828b167408c", 330, 380)),
)

private val Amenities: List<Pair<String, ImageVector>> = listOf(
    "Pool" to Icons.Filled.Pool,
    "Breakfast" to Icons.Filled.Coffee,
    "Wi-Fi" to Icons.Filled.Wifi,
    "Bar" to Icons.Filled.WineBar,
)

// Colours: a warm paper sheet, one terracotta accent.
private val Sheet = Color(0xFFF7F3EE)
private val Ink = Color(0xFF1D1B18)
private val Muted = Color(0xFF736D65)
private val Line = Color(0xFFE5DED4)
private val Tile = Color(0xFFEEE7DD)
private val Accent = Color(0xFFB4532A)
private val Grabber = Color(0xFFCFC5B8)

// The sheet's three resting places, as the y of its top edge: the peek leaves PEEK_SHOW dp of
// the sheet above the footer; half sits at HALF of the screen; full stops FULL_GAP below the
// top inset.
private const val FOOTER = 84f
private const val PEEK_SHOW = 124f
private const val HALF = 0.47f
private const val FULL_GAP = 100f
private const val CORNER = 28f

// The jelly edge is a membrane: NODES points across the width, pinned at both corners, each
// lifted d dp off the sheet's straight edge. Each node is tied to its rest place (ANCHOR, 1/s²)
// and to its neighbours (TENSION), with damping of its own (DAMP) and between neighbours
// (SHEAR_DAMP, which calms the short ripples first), so the edge rings at about 3 Hz in one
// soft arch, never in a zig-zag. It is stepped in fixed substeps of SUBSTEP s.
private const val NODES = 21
private const val ANCHOR = 60.0
private const val TENSION = 12000.0
private const val DAMP = 2.0
private const val SHEAR_DAMP = 170.0
private const val SUBSTEP = 1.0 / 720
// What moves it: the air the sheet moves through holds every node back by LAG × the sheet's
// speed (dp/s²), so the edge trails a rising sheet; the nodes' own weight carries INERTIA × the
// sheet's acceleration (smoothed over ACCEL_SMOOTH s), so the edge swells as it stops; a finger
// holds the nodes under it (a bell GRIP of the width wide) with a spring (HOLD, HOLD_DAMP), so
// the edge near the finger leads and the rest trails; dragged past an end detent, the finger
// lifts them by STRETCH_PULL of the stretch. A tap on the head kicks the nodes under it up at
// KICK dp/s.
private const val LAG = 4.5
private const val INERTIA = 0.45
private const val ACCEL_SMOOTH = 0.04
private const val GRIP = 0.22
private const val HOLD = 900.0
private const val HOLD_DAMP = 40.0
private const val STRETCH_PULL = 0.55
private const val KICK = 320.0
// Drawn through a soft limit, so the edge never lifts or sags more than LIFT_MAX dp.
private const val LIFT_MAX = 34.0
private const val SAMPLES = 64
// The content under the edge gives with the middle of it by this share, so a sag never covers it.
private const val CONTENT_GIVE = 0.5f
// The grabber stretches with the edge's local slope, up to GRAB_STRETCH dp, and tilts with it.
private const val GRAB_W = 40f
private const val GRAB_STRETCH = 14f

// The sheet itself: a spring to its detent (ζ ≈ 0.75, a small overshoot that sets the jelly
// going), with the release velocity handed in. A release is thrown THROW seconds ahead to pick
// the detent; anything faster than FLICK dp/s moves one detent in its direction at least.
private const val STIFFNESS = 230f
private const val DAMPING = 23f
private const val THROW = 0.18f
private const val FLICK = 900f
private const val RUBBER = 0.35f
private const val PAN_SLOP = 4f
private const val TAP_SLOP = 10f
// A tap this far into the sheet's head opens it a detent.
private const val TAP_HEAD = 90f
// The photo behind, when the sheet is fully open: scaled back and blurred.
private const val PHOTO_SCALE = 0.9f
private const val PHOTO_RADIUS = 26f
private const val IMAGE_FADE = 0.35f

// autoPlay (seconds). Each move is a finger dragging from one detent towards the next for
// `time` seconds at x (share of the width), letting go with the speed it had.
const val LOOP = 11f

private class Move(val at: Float, val time: Float, val to: Int, val x: Float)

private val Moves = listOf(
    Move(0.8f, 0.55f, 1, 0.66f),
    Move(3.0f, 0.5f, 2, 0.36f),
    Move(5.6f, 0.45f, 1, 0.58f),
    Move(7.6f, 0.42f, 0, 0.44f),
)
// Where the script picks up again after a touch, by the detent the sheet rests on.
private val ResumeAt = floatArrayOf(0f, 2.2f, 4.8f)
private const val USER_HOLD = 4f
// Share of a scripted drag the finger covers before letting go; the spring does the rest.
private const val DRAG_SHARE = 0.8f

/** The share of a finger's hold on node i, for a finger at fx on a sheet `width` wide. */
private fun grip(i: Int, fx: Double, width: Double): Double {
    val x = width * i / (NODES - 1)
    val k = (x - fx) / max(1.0, width * GRIP)
    return exp(-k * k)
}

/** The jelly edge's nodes, pinned at both ends. */
private class Membrane {
    val d = DoubleArray(NODES)
    val u = DoubleArray(NODES)
    var acc = 0.0
    var speed = 0.0
    var accel = 0.0

    fun flatten() {
        d.fill(0.0)
        u.fill(0.0)
    }

    /**
     * Advances it by `dt` s. `speed` is the sheet's speed (dp/s, down positive), `accel` its
     * acceleration; `held` says a finger holds it at fx, lifting the nodes under it to `lift`.
     */
    fun step(dt: Double, speed: Double, accel: Double, held: Boolean, fx: Double, lift: Double, width: Double) {
        val n = NODES
        acc = min(acc + dt, 0.05)
        while (acc >= SUBSTEP) {
            acc -= SUBSTEP
            // Velocities first (from this substep's places), then places: semi-implicit Euler.
            for (i in 1 until n - 1) {
                var a = TENSION * (d[i - 1] + d[i + 1] - 2 * d[i]) +
                    SHEAR_DAMP * (u[i - 1] + u[i + 1] - 2 * u[i]) -
                    ANCHOR * d[i] -
                    DAMP * u[i] +
                    LAG * speed +
                    INERTIA * accel
                if (held) {
                    val w = grip(i, fx, width)
                    a += w * (HOLD * (lift - d[i]) - HOLD_DAMP * u[i])
                }
                u[i] += a * SUBSTEP
            }
            for (i in 1 until n - 1) d[i] += u[i] * SUBSTEP
        }
        // A guard: anything not finite (a bad frame time) puts the edge back flat.
        if (d.any { !it.isFinite() } || u.any { !it.isFinite() }) flatten()
    }

    /** The edge's lift at x (dp): a Catmull-Rom curve through the nodes, through a soft limit. */
    fun liftAt(x: Float, width: Float): Float {
        val n = NODES
        val f = (x / max(1f, width) * (n - 1)).toDouble().coerceIn(0.0, (n - 1).toDouble())
        val i = min(n - 2, floor(f).toInt())
        val t = f - i
        val p0 = d[max(0, i - 1)]
        val p1 = d[i]
        val p2 = d[i + 1]
        val p3 = d[min(n - 1, i + 2)]
        val t2 = t * t
        val t3 = t2 * t
        val raw = 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
        return (LIFT_MAX * tanh(raw / LIFT_MAX)).toFloat()
    }
}

private fun rubber(over: Float) = over * RUBBER / (1f + abs(over) / 400f)

/** Everything the frame loop, the gestures and the drawing share. Lengths in dp. */
private class JellyMotion {
    var y = 0f
    var v = 0f
    var target = 0f
    var detent = 0
    var dragging = false
    var dragFrom = 0f
    var dragSpeed = 0f
    var fingerX = 0f
    val membrane = Membrane()
    // A kick waiting for the next frame: where (x) and how hard (dp/s).
    var kickX = 0f
    var kickV = 0.0
    var ready = false
    // autoPlay's clock and the scripted finger.
    var clock = 0f
    var looping = false
    var hold = 0f
    var scripted = -1
    var scriptFrom = 0f
    // Seconds since the block appeared; everything that moves reads it, so it is drawn every frame.
    var elapsed by mutableFloatStateOf(0f)
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f
    val detents = FloatArray(3)

    val contentTop get() = y - min(0f, membrane.liftAt(width / 2f, width)) * CONTENT_GIVE
    val footer get() = FOOTER + bottom

    /** 0 at half or below, 1 fully open. */
    val open get() = ((detents[1] - y) / (detents[1] - detents[2])).coerceIn(0f, 1f)
}

/**
 * A hotel's page: a tall photo with a booking sheet over it. The sheet's top edge is jelly — it
 * bulges towards the finger while dragged and wobbles when let go — and it rests at three heights:
 * a peek with the hotel's name, half with the stay, full with the rooms. Fully open, the photo
 * behind blurs and sinks back.
 *
 * @param autoPlay Drags the sheet up to half, to full, and back down to the peek, over and over.
 * @param onDetentChange Called with the detent the sheet settles on: 0 peek, 1 half, 2 full.
 */
@Composable
fun JellySheet(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onDetentChange: ((Int) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val detentChanged by rememberUpdatedState(onDetentChange)
    val motion = remember { JellyMotion() }
    val photos = remember { mutableStateMapOf<String, Pair<ImageBitmap, Float>>() }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(motion, autoPlay) { motion.looping = autoPlay }

    LaunchedEffect(context) {
        val loader = SingletonImageLoader.get(context)
        (listOf(Hero) + Rooms.map { it.photo }).forEach { address ->
            launch {
                val request = ImageRequest.Builder(context).data(address).build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                photos[address] = result.image.toBitmap().asImageBitmap() to motion.elapsed
            }
        }
    }

    /** Lets go of the sheet with speed `speed` towards detent `to`. */
    fun release(to: Int, speed: Float, byUser: Boolean) {
        val m = motion
        m.dragging = false
        m.v = speed
        if (to != m.detent) {
            if (byUser) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            detentChanged?.invoke(to)
        }
        m.detent = to
        m.target = m.detents[to]
    }

    /** The detent a release at y with speed `speed` lands on. */
    fun pick(at: Float, speed: Float): Int {
        val d = motion.detents
        val thrown = at + speed * THROW
        var best = 0
        for (k in 1 until d.size) if (abs(d[k] - thrown) < abs(d[best] - thrown)) best = k
        // A flick always moves at least one detent its way from where the sheet is now.
        if (abs(speed) > FLICK) {
            var near = 0
            for (k in 1 until d.size) if (abs(d[k] - at) < abs(d[near] - at)) near = k
            val step = if (speed < 0f) 1 else -1
            val further = (near + step).coerceIn(0, d.size - 1)
            if (if (speed < 0f) best < further else best > further) best = further
        }
        return best
    }

    /** Where a drag puts the sheet: with the finger, stretching past the end detents. */
    fun follow(raw: Float): Float {
        val top = motion.detents[2]
        val bottom = motion.detents[0]
        return when {
            raw < top -> top - rubber(top - raw)
            raw > bottom -> bottom + rubber(raw - bottom)
            else -> raw
        }
    }

    fun touched() {
        val m = motion
        m.hold = USER_HOLD
        // A scripted drag under way is let go of where it is, and springs back to its detent.
        if (m.scripted >= 0) {
            m.scripted = -1
            m.dragging = false
            m.v = m.dragSpeed
        }
    }

    fun advance(dt: Float) {
        val m = motion
        m.elapsed += dt
        val width = m.width

        // The script: a finger drags the sheet towards the next detent and lets go.
        if (m.hold > 0f) {
            m.hold -= dt
            if (m.hold <= 0f) m.clock = ResumeAt[m.detent]
        } else if (m.looping) {
            val before = m.clock
            val t = (before + dt) % LOOP
            m.clock = t
            Moves.forEachIndexed { k, move ->
                val end = move.at + move.time
                if (m.scripted == k) {
                    val u = ((t - move.at) / move.time).coerceIn(0f, 1f)
                    val span = (m.detents[move.to] - m.scriptFrom) * DRAG_SHARE
                    m.y = m.scriptFrom + span * (0.4f * u + 0.6f * u * u)
                    m.dragSpeed = span * (0.4f + 1.2f * u) / move.time
                    m.fingerX = move.x * width
                    if (t >= end || t < before) {
                        m.scripted = -1
                        release(move.to, m.dragSpeed, false)
                    }
                } else if (m.scripted < 0 && before < move.at && t >= move.at) {
                    m.scripted = k
                    m.scriptFrom = m.y
                    m.dragging = true
                    m.fingerX = move.x * width
                }
            }
        }

        // The sheet: with the finger, or on its spring.
        var speed: Float
        if (m.dragging) {
            speed = m.dragSpeed
        } else if (reducedMotion) {
            val next = m.y + (m.target - m.y) * (1f - exp(-dt * 14f))
            speed = (next - m.y) / dt
            m.y = next
            m.v = speed
        } else {
            speed = m.v + (STIFFNESS * (m.target - m.y) - DAMPING * m.v) * dt
            m.v = speed
            m.y += speed * dt
        }

        // The jelly edge.
        val mem = m.membrane
        if (reducedMotion) {
            mem.flatten()
        } else {
            if (m.kickV != 0.0) {
                for (i in 1 until NODES - 1) mem.u[i] += m.kickV * grip(i, m.kickX.toDouble(), width.toDouble())
                m.kickV = 0.0
            }
            val held = m.dragging
            val stretch = if (!held) 0f else if (m.y < m.detents[2]) m.detents[2] - m.y else if (m.y > m.detents[0]) m.detents[0] - m.y else 0f
            val v = if (speed.isFinite()) speed.toDouble().coerceIn(-6000.0, 6000.0) else 0.0
            // The sheet's acceleration, smoothed so a jittery finger does not shake the edge.
            val raw = (v - mem.speed) / dt
            mem.speed = v
            mem.accel += (raw.coerceIn(-40000.0, 40000.0) - mem.accel) * (1 - exp(-dt / ACCEL_SMOOTH))
            if (!mem.accel.isFinite()) mem.accel = 0.0
            mem.step(dt.toDouble(), v, mem.accel, held, m.fingerX.toDouble(), (stretch * STRETCH_PULL).toDouble(), width.toDouble())
        }
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f && motion.ready) advance(dt)
            }
        }
    }

    val sheetPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val path = remember { Path() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .semantics { contentDescription = "Booking details for Casa Albera. Drag up for the rooms, down to close" }
            // One recogniser stands in for the exclusive pan (after 4 dp) and tap (within 10 dp)
            // on the sheet. Touches on the photo above the sheet or on the footer belong to neither.
            .pointerInput(motion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val m = motion
                    val start = down.position / density.density
                    if (start.y < m.contentTop || start.y >= m.height - m.footer) return@awaitEachGesture
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    touched()
                    m.fingerX = start.x
                    var panning = false
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val p = change.position / density.density
                        val dx = p.x - start.x
                        val dy = p.y - start.y
                        if (!change.pressed) {
                            released = true
                            if (panning) {
                                change.consume()
                                val vy = tracker.calculateVelocity().y / density.density
                                release(pick(m.y, vy), vy, true)
                            } else if (hypot(dx, dy) <= TAP_SLOP && p.y - m.contentTop <= TAP_HEAD) {
                                // A tap on the sheet's head opens it a detent; from full it goes back to half.
                                m.fingerX = p.x
                                // The tap sends a ripple out from under the finger as the sheet moves off.
                                m.kickX = p.x
                                m.kickV = KICK
                                release(if (m.detent == 2) 1 else m.detent + 1, 0f, true)
                            }
                            continue
                        }
                        if (!panning && hypot(dx, dy) >= PAN_SLOP) {
                            panning = true
                            // The sheet is caught wherever it is, mid-spring included.
                            m.dragFrom = m.y
                            m.dragSpeed = 0f
                            m.dragging = true
                        }
                        if (panning) {
                            change.consume()
                            m.y = follow(m.dragFrom + dy)
                            m.dragSpeed = tracker.calculateVelocity().y / density.density
                            m.fingerX = p.x
                        }
                    }
                    // A drag lost to the system still lets go of the sheet.
                    if (panning && !released) release(pick(m.y, 0f), 0f, true)
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        SideEffect {
            val m = motion
            m.width = width
            m.height = height
            m.top = safeTop
            m.bottom = safeBottom
            // The detents follow the layout; the sheet starts at the peek.
            m.detents[0] = height - (FOOTER + safeBottom) - PEEK_SHOW
            m.detents[1] = height * HALF
            m.detents[2] = safeTop + FULL_GAP
            if (!m.ready) {
                m.y = m.detents[m.detent]
                m.ready = true
            }
            m.target = m.detents[m.detent]
        }

        HeroPhoto(motion, photos[Hero], reducedMotion, safeTop)

        // The back, share and heart buttons fade out as the sheet opens fully.
        Row(
            Modifier
                .offset(y = (safeTop + 6f).dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .graphicsLayer { alpha = (1f - motion.open * 1.6f).coerceAtLeast(0f).also { motion.elapsed } },
        ) {
            Glass(Icons.AutoMirrored.Filled.ArrowBack)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Glass(Icons.Filled.Share)
                Glass(Icons.Outlined.FavoriteBorder)
            }
        }

        // The photo count rides just above the sheet while it is low.
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(-16.dp.roundToPx(), ((motion.y - 46f).dp.toPx()).roundToInt().also { motion.elapsed }) }
                .graphicsLayer {
                    val m = motion
                    alpha = ((m.y - m.detents[1]) / (m.detents[0] - m.detents[1])).coerceIn(0f, 1f).also { m.elapsed }
                }
                .height(28.dp)
                .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(14.dp))
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(13.dp), tint = Color.White)
            BasicText(
                "1 / 24",
                style = TextStyle(color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            )
        }

        // The sheet's top: the corners pinned and round, the edge between them riding the membrane,
        // with a soft shadow; the grabber rides the edge in the middle, tilting and stretching with it.
        Canvas(Modifier.fillMaxSize()) {
            val m = motion
            m.elapsed
            if (!m.ready) return@Canvas
            scale(density.density, density.density, pivot = Offset.Zero) {
                val top = m.y
                if (!top.isFinite()) return@scale
                val mem = m.membrane
                fun edge(x: Float) = top - mem.liftAt(x, width)
                path.rewind()
                path.moveTo(0f, top + CORNER)
                path.quadTo(0f, top, CORNER, edge(CORNER))
                for (k in 1..SAMPLES) {
                    val x = CORNER + (width - 2f * CORNER) * k / SAMPLES
                    path.lineTo(x, edge(x))
                }
                path.quadTo(width, top, width, top + CORNER)
                path.lineTo(width, height + 80f)
                path.lineTo(0f, height + 80f)
                path.close()
                drawIntoCanvas { canvas ->
                    sheetPaint.color = Sheet.toArgb()
                    // Skia's shadow in the RN version blurs with a sigma of 22; the canvas is in dp.
                    sheetPaint.setShadowLayer(sigmaToRadius(22f), 0f, -4f, Color.Black.copy(alpha = 0.28f).toArgb())
                    canvas.nativeCanvas.drawPath(path, sheetPaint)
                }
                val mid = width / 2f
                val rise = mem.liftAt(mid, width)
                val slope = (mem.liftAt(mid + 12f, width) - mem.liftAt(mid - 12f, width)) / 24f
                val gw = GRAB_W + min(GRAB_STRETCH, abs(slope) * 60f + abs(rise) * 0.12f)
                translate(mid, top - rise + 11.5f) {
                    rotate(Math.toDegrees(-atan(slope.toDouble())).toFloat(), pivot = Offset.Zero) {
                        drawRoundRect(
                            Grabber,
                            topLeft = Offset(-gw / 2f, -2.5f),
                            size = androidx.compose.ui.geometry.Size(gw, 5f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f),
                        )
                    }
                }
            }
        }

        // The content rides the sheet, and the jelly nudges it a little so it feels part of it.
        SheetContent(
            photos = photos,
            elapsed = { motion.elapsed },
            modifier = Modifier.offset {
                IntOffset(0, (motion.contentTop.dp.toPx()).roundToInt().also { motion.elapsed })
            },
        )

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((FOOTER + safeBottom).dp)
                .background(Sheet)
                .padding(start = 20.dp, end = 20.dp, bottom = safeBottom.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    buildAnnotatedString {
                        append("€248")
                        withStyle(SpanStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)) {
                            append(" / night")
                        }
                    },
                    style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum"),
                )
                BasicText("12 – 16 May · 2 guests", style = TextStyle(color = Muted, fontSize = 12.5.sp))
            }
            Box(
                Modifier
                    .height(52.dp)
                    .background(Accent, RoundedCornerShape(26.dp))
                    .padding(horizontal = 34.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Reserve", style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        // The footer's hairline.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(y = -(FOOTER + safeBottom).dp)
                .fillMaxWidth()
                .height(1.dp / density.density)
                .background(Line),
        )
    }
}

/**
 * The photo, which scales back, rounds its corners, drops a little and blurs as the sheet opens
 * fully. The blur needs Android 12; below it the photo only darkens.
 */
@Composable
private fun HeroPhoto(motion: JellyMotion, hero: Pair<ImageBitmap, Float>?, reducedMotion: Boolean, top: Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val o = motion.open.also { motion.elapsed }
                if (!reducedMotion) {
                    val s = 1f - (1f - PHOTO_SCALE) * o
                    scaleX = s
                    scaleY = s
                    translationY = (o * top * 0.4f).dp.toPx()
                }
                shape = RoundedCornerShape((o * PHOTO_RADIUS).dp)
                clip = true
            }
            .background(Color(0xFF1D2A2C)),
    ) {
        if (hero != null) {
            Image(
                hero.first,
                null,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ((motion.elapsed - hero.second) / IMAGE_FADE).coerceIn(0f, 1f) },
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height((top + 110f).dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Black.copy(alpha = 0f)))),
        )
        // The dark blur crossfades in as the sheet opens fully.
        val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = motion.open.also { motion.elapsed } }) {
            if (hero != null && blurs) {
                Image(hero.first, null, Modifier.fillMaxSize().blur(14.dp), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (blurs) 0.42f else 0.6f)))
        }
    }
}

/** A round frosted button on the photo. */
@Composable
private fun Glass(icon: ImageVector) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = Color.White)
    }
}

/** What the sheet holds, top to bottom: the hotel, the stay, the rooms, what's included. */
@Composable
private fun SheetContent(
    photos: Map<String, Pair<ImageBitmap, Float>>,
    elapsed: () -> Float,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 30.dp, start = 20.dp, end = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "Casa Albera",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = Ink, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier
                    .height(28.dp)
                    .background(Tile, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.Star, null, Modifier.size(11.dp), tint = Ink)
                BasicText(
                    "4.92",
                    style = TextStyle(color = Ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                )
            }
        }
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Filled.Place, null, Modifier.size(13.dp), tint = Muted)
            BasicText("Praia do Norte, Comporta · 214 reviews", style = TextStyle(color = Muted, fontSize = 14.5.sp))
        }

        Label("Your stay")
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .border(1.dp, Line, RoundedCornerShape(16.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StayCell("Check-in", "Tue 12 May")
            Box(Modifier.width(1.dp).height(34.dp).background(Line))
            StayCell("Check-out", "Sat 16 May")
            Box(Modifier.width(1.dp).height(34.dp).background(Line))
            StayCell("Guests", "2 adults")
        }

        Label("Rooms")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Rooms.forEach { room ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val shown = photos[room.photo]
                    Box(
                        Modifier
                            .padding(bottom = 5.dp)
                            .fillMaxWidth()
                            .height(116.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Tile),
                    ) {
                        if (shown != null) {
                            Image(
                                shown.first,
                                null,
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = ((elapsed() - shown.second) / 0.3f).coerceIn(0f, 1f) },
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    BasicText(room.name, style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                    BasicText("from ${room.price}", style = TextStyle(color = Muted, fontSize = 12.5.sp, fontFeatureSettings = "tnum"))
                }
            }
        }

        Label("Included")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Amenities.forEach { (label, icon) ->
                Column(
                    Modifier.width(72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(46.dp).background(Tile, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(19.dp), tint = Ink)
                    }
                    BasicText(label, style = TextStyle(color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                }
            }
        }

        BasicText(
            "Twelve whitewashed rooms between the rice fields and the dunes, a ten-minute walk from the sea.",
            modifier = Modifier.padding(top = 20.dp),
            style = TextStyle(color = Muted, fontSize = 14.5.sp, lineHeight = 21.sp),
        )
    }
}

@Composable
private fun Label(text: String) {
    BasicText(
        text.uppercase(),
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
        style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StayCell(label: String, value: String) {
    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText(label, style = TextStyle(color = Muted, fontSize = 12.sp))
        BasicText(value, style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp), maxLines = 1)
    }
}
