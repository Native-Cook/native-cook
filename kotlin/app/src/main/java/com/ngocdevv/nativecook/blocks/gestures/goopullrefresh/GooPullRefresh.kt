package com.ngocdevv.nativecook.blocks.gestures.goopullrefresh

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
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
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** How a photo is cropped: its height over its width. */
enum class GooShape(val aspect: Float) { Portrait(1.3f), Square(1f), Landscape(0.76f) }

/** One photo in the feed. */
data class GooSighting(val id: String, val shape: GooShape, val title: String, val by: String, val likes: Int)

private val Feed = listOf(
    GooSighting("photo-1536589961747-e239b2abbec2", GooShape.Portrait, "Supervising the sofa delivery", "Ines Mora", 2184),
    GooSighting("photo-1616081352935-7daa6487b6ae", GooShape.Portrait, "Low pass over the reeds", "Tomas Hale", 932),
    GooSighting("photo-1498100152307-ce63fd6c5424", GooShape.Landscape, "Window seat, as always", "Lea Brandt", 3016),
    GooSighting("photo-1591055749071-927e6ddffc82", GooShape.Portrait, "Hovered a full minute, then gone", "Kit Okafor", 1207),
    GooSighting("photo-1596854372745-0906a0593bca", GooShape.Square, "Caught mid-judgement", "Noor Haddad", 2875),
    GooSighting("photo-1593893722928-17454e6d5fb8", GooShape.Landscape, "Gulls riding the cliff updraft", "Tomas Hale", 688),
    GooSighting("photo-1559624989-7b9303bd9792", GooShape.Portrait, "Found the one sunbeam in the flat", "Mika Sato", 4410),
    GooSighting("photo-1470167494176-c2e966a4eb3d", GooShape.Landscape, "The whole flock turned at once", "Ada Lind", 1540),
    GooSighting("photo-1647806422508-0322f33e270b", GooShape.Portrait, "Claimed the laundry basket", "Mika Sato", 6230),
    GooSighting("photo-1626444344029-5c680f7513c1", GooShape.Portrait, "Tiny, loud, very sure of itself", "Kit Okafor", 864),
    GooSighting("photo-1583795128727-6ec3642408f8", GooShape.Landscape, "Nap number four", "Joel Vance", 5122),
    GooSighting("photo-1549589465-442e6195e54b", GooShape.Portrait, "Talons out, eyes on the grass", "Ada Lind", 1993),
    GooSighting("photo-1582725461742-8ecd962c260d", GooShape.Portrait, "Back from the vet, still sulking", "Lea Brandt", 3384),
    GooSighting("photo-1554409011-d874157657b8", GooShape.Portrait, "Spotted the heron first", "Joel Vance", 1102),
)

private val Incoming = listOf(
    GooSighting("photo-1611843275167-a9bba9aa65dd", GooShape.Portrait, "Top of the bookshelf, finally", "Noor Haddad", 214) to
        GooSighting("photo-1567187549304-1fe8331879b5", GooShape.Portrait, "Same fence post every morning", "Tomas Hale", 230),
    GooSighting("photo-1597838816882-4435b1977fbe", GooShape.Square, "Morning stretch, full length", "Ines Mora", 388) to
        GooSighting("photo-1448227922836-6d05b3f8b663", GooShape.Square, "Wings folded, straight down", "Ada Lind", 96),
    GooSighting("photo-1552933529-e359b2477252", GooShape.Landscape, "Versus the curtain, round three", "Joel Vance", 402) to
        GooSighting("photo-1731650211720-54d314975a7b", GooShape.Landscape, "Two finches, one seed", "Kit Okafor", 142),
)

private val Chips = listOf("For you", "Cats", "Birds", "Nearby", "Following")

private fun unsplash(id: String, w: Int, h: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$w&h=$h&q=80"

private fun formatLikes(n: Int): String {
    if (n < 1000) return "$n"
    val text = String.format(Locale.US, "%.1f", n / 1000.0)
    return (if (text.endsWith(".0")) text.dropLast(2) else text) + "k"
}

// The page, the header, the text greys and the one accent: the liquid of the refresh drop.
private val PAGE = Color(0xFFF4F2EE)
private val HEADER = Color(0xFFFFFFFF)
private val INK = Color(0xFF1B1A18)
private val MUTED = Color(0xFF8C877F)
private val CHIP = Color(0xFFEFECE7)
private val AVATAR = Color(0xFFE6E1D9)
private val PHOTO_BLANK = Color(0xFFE7E3DC)
private val ACCENT = Color(0xFFFF5A2C)

// Layout, in dp: the header under the safe area, the feed's margins and each tile's parts. Tiles
// are a photo of the column's width (its height from the shape) over TEXT_H of title and byline.
private const val HEADER_H = 102f
private const val LIST_TOP = 12f
private const val SIDE = 12f
private const val COL_GAP = 10f
private const val ROW_GAP = 18f
private const val TEXT_H = 46f

// The liquid lives behind the header's bottom edge: a reservoir whose meniscus sags out under the
// edge as the feed is pulled (from MEN_REST to MEN_PULL dp over the first MEN_FULL dp of pull), on
// a springy surface (MEN_STIFFNESS, MEN_DAMPING per unit mass).
private const val GOO_BLUR = 4.5
private const val RES_RX = 30.0
private const val RES_RY = 14.0
private const val MEN_REST = -9.0
private const val MEN_PULL = 9.0
private const val MEN_FULL = 40.0
private const val MEN_STIFFNESS = 380.0
private const val MEN_DAMPING = 13.0
// Pulling draws a drop out of it, its centre at DROP_TRAVEL of the pull, growing from BEAD_R to
// DROP_R, on a neck of NECK_BEADS overlapping beads that thins from NECK_WIDE to NECK_THIN as the
// pull nears SNAP_PULL, where the neck breaks and the drop hangs free.
private const val DROP_TRAVEL = 0.62
private const val BEAD_R = 5.0
private const val DROP_R = 18.0
private const val NECK_BEADS = 10
private const val NECK_WIDE = 11.0
private const val NECK_THIN = 3.4
private const val SNAP_PULL = 108.0
// After the snap: the neck melts at NECK_CUT/s, the drop gets a SNAP_KICK dp/s push and springs
// to the middle of the gap, stretching with its speed and jiggling.
private const val NECK_CUT = 26.0
private const val SNAP_KICK = 260.0
private const val DROP_STIFFNESS = 260.0
private const val DROP_DAMPING = 18.0
private const val STRETCH_PER_SPEED = 1.0 / 1800
private const val JIGGLE = 0.16
private const val JIGGLE_HZ = 7.0
private const val JIGGLE_DECAY = 6.0
// Refreshing: the feed waits HOLD below the header while the drop becomes a gooey loader: a core
// of CORE_R with three droplets of SAT_R that swing out to ORBIT on a PULSE s breath as the trio
// turns at TURN deg/s. After REFRESH_TIME s they merge back over MERGE_TIME s into one drop of
// MERGED_R, which the header draws back up into itself before the feed closes.
private const val HOLD = 100.0
private const val CORE_R = 13.0
private const val SAT_R = 9.0
private const val ORBIT = 30.0
private const val PULSE = 1.1
private const val TURN = 250.0
private const val SPREAD_IN = 0.3
private const val REFRESH_TIME = 1.5
private const val MERGE_TIME = 0.35
private const val MERGED_R = 17.0
private const val RETURN_STIFFNESS = 200.0
private const val RETURN_DAMPING = 20.0
private const val REGROW = 0.5

// Scrolling: iOS-like deceleration, resistance past either end over RUBBER_SPAN of the screen.
private const val DECELERATION = 2.1
private const val EDGE_STIFFNESS = 180.0
private const val EDGE_DAMPING = 26.8
private const val RUBBER_SPAN = 0.5
private const val DRAG_SLOP = 2f
// The new row's arrival: a spring on how many rows are still hidden above the feed.
private const val REVEAL_STIFFNESS = 70.0
private const val REVEAL_DAMPING = 15.0

// autoPlay (seconds): pull, refresh, read down the feed, back to the top, rest.
private const val LOOP = 9.0
private const val PULL_FROM = 0.6
private const val PULL_TO = 1.8
private const val PULL_RAW = 300.0
private const val DOWN_FROM = 5.3
private const val DOWN_TO = 6.9
private const val DOWN_BY = 1100.0
private const val SWAP_AT = 7.0
private const val UP_FROM = 7.2
private const val UP_TO = 8.4
private const val USER_HOLD = 3.5

// The liquid's canvas: from GOO_ABOVE dp above the header's edge, GOO_SPAN dp tall.
private const val GOO_ABOVE = 60f
private const val GOO_SPAN = 520f

private fun clamp01(x: Double) = min(1.0, max(0.0, x))
private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
private fun easeOut(u: Double) = 1 - (1 - clamp01(u)).pow(3)
private fun easeInOut(u: Double): Double {
    val x = clamp01(u)
    return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
}

/** iOS-style resistance: `x` dp past an edge shows as at most `span`. */
private fun rubber(x: Double, span: Double) = (1 - 1 / ((x * 0.55) / span + 1)) * span

/** The finger travel that `rubber` turned into `r`. */
private fun unrubber(r: Double, span: Double): Double {
    val k = min(0.999, r / span)
    return (span / 0.55) * (1 / (1 - k) - 1)
}

/** How far the hidden rows reach down a column when `h` of them (a fraction mid-arrival) are hidden. */
private fun hiddenReach(prefix: DoubleArray, h: Double): Double {
    val n = prefix.size - 1
    if (n <= 0) return 0.0
    val x = min(n.toDouble(), max(0.0, h))
    val k = min(n - 1, floor(x).toInt())
    return prefix[k] + (prefix[k + 1] - prefix[k]) * (x - k)
}

/** Where the drop rests in a gap `gap` tall: in its middle, riding up under the header as it closes. */
private fun dropAim(bar: Double, gap: Double) = bar + min(gap / 2, gap - HOLD / 2)

/** One tile of the masonry: where it sits in its column and which rows it waits behind. */
private class GooTile(val item: GooSighting, val fresh: Boolean, val x: Float, val y: Float, val row: Int, val photo: Float)

private class GooLayout(val column: Float, val tiles: List<GooTile>, val prefix: DoubleArray, val total: Double)

/** Both columns: the incoming rows on top (the first to arrive nearest the feed), then the feed, each photo going to the shorter column. */
private fun layoutTiles(feed: List<GooSighting>, incoming: List<Pair<GooSighting, GooSighting>>, width: Float): GooLayout {
    val col = max(1f, (width - SIDE * 2 - COL_GAP) / 2)
    val tiles = ArrayList<GooTile>()
    val heights = floatArrayOf(0f, 0f)
    val rows = intArrayOf(0, 0)
    val prefix = arrayListOf(0.0)
    for (pair in incoming.reversed()) {
        val photo = (col * pair.first.shape.aspect).roundToInt().toFloat()
        for (c in 0..1) {
            tiles += GooTile(if (c == 0) pair.first else pair.second, true, SIDE + c * (col + COL_GAP), heights[c], rows[c], photo)
            heights[c] += photo + TEXT_H + ROW_GAP
            rows[c] += 1
        }
        prefix += heights[0].toDouble()
    }
    for (item in feed) {
        val c = if (heights[1] < heights[0] - 0.5f) 1 else 0
        val photo = (col * item.shape.aspect).roundToInt().toFloat()
        tiles += GooTile(item, false, SIDE + c * (col + COL_GAP), heights[c], rows[c], photo)
        heights[c] += photo + TEXT_H + ROW_GAP
        rows[c] += 1
    }
    return GooLayout(col, tiles, prefix.toDoubleArray(), (max(heights[0], heights[1]) - ROW_GAP).toDouble())
}

private enum class Phase { Idle, Free, Loading, Merging, Returning }

private enum class Mode { Drag, Free, Script }

/** The feed and the liquid, shared by the frame loop, the drag and the drawing. */
private class GooMotion(val hidden: Int) {
    // The feed's offset: 0 at the top, more when pulled down, less when scrolled.
    var scroll = 0.0
    var velocity = 0.0
    var mode = Mode.Free
    var dragFrom = 0.0
    // How far the feed may rest below the header: 0, or HOLD while refreshing.
    var ceiling = 0.0
    // How many rows are still hidden above the feed, and where that is heading.
    var head = hidden.toDouble()
    var headTarget = hidden.toDouble()
    var headVelocity = 0.0

    var phase = Phase.Idle
    var phaseTime = 0.0
    var men = MEN_REST
    var menV = 0.0
    var dropY = 0.0
    var dropV = 0.0
    var dropR = DROP_R
    var neck = 0.0
    var jiggleAge = 10.0
    var spin = 0.0
    var grow = 1.0
    var byUser = false

    var clock = 0.0
    var looping = false
    var hold = 0.0
    var segment = -1
    var segFrom = 0.0

    // The stage, in dp.
    var height = 0.0
    var barBottom = 0.0
    var bottomInset = 0.0
    var total = 0.0
    var prefix = doubleArrayOf(0.0)

    // Bumped once a frame so the tiles and the liquid draw again.
    var frame by mutableLongStateOf(0L)

    /** The lowest the feed can scroll, from how many rows are showing. */
    fun floorOf(h: Double): Double {
        val content = LIST_TOP + total - hiddenReach(prefix, h) + bottomInset + 16
        return min(0.0, height - barBottom - content)
    }

    /** Where a finger's travel puts the feed, with resistance past either end. */
    fun bounded(raw: Double): Double {
        val span = height * RUBBER_SPAN
        val bottom = floorOf(head)
        if (raw > ceiling) return ceiling + rubber(raw - ceiling, span)
        if (raw < bottom) return bottom - rubber(bottom - raw, span)
        return raw
    }

    /** The finger lets go: a free drop starts the refresh and the feed waits under the header. */
    fun release(v: Double) {
        velocity = v
        mode = Mode.Free
        if (phase == Phase.Free) {
            phase = Phase.Loading
            phaseTime = 0.0
            ceiling = HOLD
        }
    }

    /** Puts the arrived rows back above the feed while they are out of sight, so the loop can repeat. */
    fun restock() {
        if (head >= hidden - 0.01 || prefix.size <= hidden) return
        // The last of them must be above the header: nothing on screen may move.
        val lowest = barBottom + LIST_TOP + scroll + prefix[hidden] - ROW_GAP - hiddenReach(prefix, head)
        if (lowest > barBottom - 4) return
        scroll += prefix[hidden] - hiddenReach(prefix, head)
        head = hidden.toDouble()
        headTarget = hidden.toDouble()
        headVelocity = 0.0
    }
}

/**
 * One frame of the feed and the liquid. Returns which haptic to play: 1 when the neck snaps under
 * the hand, 2 when the header swallows the drop after a pull by hand, else 0.
 */
private fun advance(m: GooMotion, dt: Double, reducedMotion: Boolean): Int {
    var haptic = 0
    val bar = m.barBottom

    // The script stands in for a finger.
    if (m.mode != Mode.Drag && m.hold > 0) {
        m.hold -= dt
        if (m.hold <= 0 && m.looping) {
            m.restock()
            m.clock = if (abs(m.scroll) > 4) UP_FROM else 0.0
            m.segment = -1
        }
    } else if (m.looping && m.mode != Mode.Drag) {
        val before = m.clock
        val t = (before + dt) % LOOP
        m.clock = t
        val active = when {
            t >= PULL_FROM && t < PULL_TO -> 0
            t >= DOWN_FROM && t < DOWN_TO -> 1
            t >= UP_FROM && t < UP_TO -> 2
            else -> -1
        }
        if (active != m.segment) {
            if (m.segment == 0) {
                m.release(0.0)
            } else if (m.segment >= 1) {
                m.velocity = 0.0
                m.mode = Mode.Free
            }
            if (active >= 0) {
                m.byUser = false
                m.segFrom = m.scroll
                m.mode = Mode.Script
            }
            m.segment = active
        }
        if (before < SWAP_AT && t >= SWAP_AT) m.restock()
        if (active >= 0) {
            val next = when (active) {
                0 -> m.bounded(PULL_RAW * easeInOut((t - PULL_FROM) / (PULL_TO - PULL_FROM)))
                1 -> m.segFrom - DOWN_BY * easeOut((t - DOWN_FROM) / (DOWN_TO - DOWN_FROM))
                else -> m.segFrom * (1 - easeInOut((t - UP_FROM) / (UP_TO - UP_FROM)))
            }
            m.velocity = (next - m.scroll) / dt
            m.scroll = next
        }
    }

    // The feed coasts, and springs back from past either end.
    if (m.mode == Mode.Free) {
        val top = m.ceiling
        val bottom = m.floorOf(m.head)
        val s = m.scroll
        var v = m.velocity
        if (s > top + 0.01 || s < bottom - 0.01) {
            val edge = if (s > top) top else bottom
            v += (EDGE_STIFFNESS * (edge - s) - EDGE_DAMPING * v) * dt
            var next = s + v * dt
            if ((s > top && next < top) || (s < bottom && next > bottom)) {
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
    }

    // The new row arrives on a spring.
    val hv = m.headVelocity + (REVEAL_STIFFNESS * (m.headTarget - m.head) - REVEAL_DAMPING * m.headVelocity) * dt
    m.headVelocity = hv
    m.head = if (reducedMotion) m.headTarget else m.head + hv * dt

    // The liquid.
    val p = m.phase
    m.phaseTime += dt
    val gap = max(0.0, m.scroll)
    if (p == Phase.Idle && gap >= SNAP_PULL && m.mode != Mode.Free && m.grow >= 1) {
        // The neck breaks: the drop hangs free, jiggling, and the meniscus springs back behind the edge.
        m.phase = Phase.Free
        m.phaseTime = 0.0
        m.dropY = bar + gap * DROP_TRAVEL
        m.dropV = if (reducedMotion) 0.0 else SNAP_KICK
        m.dropR = DROP_R
        m.neck = 1.0
        m.jiggleAge = if (reducedMotion) 10.0 else 0.0
        if (m.byUser) haptic = 1
    }

    // The meniscus follows the pull while attached, sags to meet a returning drop, else rests.
    val menGoal = when (p) {
        Phase.Idle -> mix(MEN_REST, MEN_PULL, clamp01(gap / MEN_FULL) * m.grow)
        Phase.Returning -> MEN_PULL
        else -> MEN_REST
    }
    m.menV += (MEN_STIFFNESS * (menGoal - m.men) - MEN_DAMPING * m.menV) * dt
    m.men = if (reducedMotion) menGoal else m.men + m.menV * dt

    if (p != Phase.Idle) {
        // The drop hangs in the middle of the gap, riding up under the header as the gap closes;
        // returning, it is drawn up behind the header's edge.
        val aim = if (p == Phase.Returning) bar - MERGED_R - 10 else dropAim(bar, gap)
        val k = if (p == Phase.Returning) RETURN_STIFFNESS else DROP_STIFFNESS
        val c = if (p == Phase.Returning) RETURN_DAMPING else DROP_DAMPING
        m.dropV += (k * (aim - m.dropY) - c * m.dropV) * dt
        m.dropY = if (reducedMotion) aim else m.dropY + m.dropV * dt
        m.neck *= exp(-NECK_CUT * dt)
        m.jiggleAge += dt
    }
    if (p == Phase.Loading || p == Phase.Merging) m.spin = (m.spin + TURN * dt) % 360
    when {
        p == Phase.Loading -> {
            m.dropR = mix(DROP_R, CORE_R, easeOut(m.phaseTime / SPREAD_IN))
            if (m.phaseTime >= REFRESH_TIME) {
                m.phase = Phase.Merging
                m.phaseTime = 0.0
            }
        }
        p == Phase.Merging -> {
            m.dropR = mix(CORE_R, MERGED_R, easeInOut(m.phaseTime / MERGE_TIME))
            if (m.phaseTime >= MERGE_TIME) {
                // The new row is in: the header draws the drop back in.
                m.phase = Phase.Returning
                m.phaseTime = 0.0
                m.dropV = 0.0
            }
        }
        p == Phase.Returning && m.dropY + m.dropR < bar + m.men - 2 -> {
            // Swallowed: the feed closes and the new row slides in on top.
            m.phase = Phase.Idle
            m.phaseTime = 0.0
            m.grow = 0.0
            m.ceiling = 0.0
            m.headTarget = max(0.0, Math.round(m.headTarget).toDouble() - 1)
            if (m.mode == Mode.Free && m.velocity > 0) m.velocity = 0.0
            if (m.byUser) haptic = 2
        }
    }
    if (m.phase == Phase.Idle && m.grow < 1) m.grow = clamp01(m.grow + dt / REGROW)
    return haptic
}

/**
 * A two-column feed of cat and bird photos under a white header. Pulling the feed down draws a
 * drop of orange liquid out from behind the header's edge on a neck that thins as you pull; past
 * the threshold the neck snaps with a haptic and the drop hangs free, jiggling. Let go and it
 * becomes a gooey loader, three droplets breathing out of a core and back while the trio turns;
 * when the new photos are in, they merge into one drop that the header draws back in, the feed
 * closes and the new row slides in on top.
 *
 * @param sightings The photos already in the feed, newest first.
 * @param incoming The photos refreshes bring in, a row of two at a time in the order they arrive;
 *   the two of a row share a shape, so both columns move by the same amount.
 * @param autoPlay Pulls to refresh, waits for the new photos, scrolls down the feed and back up.
 * @param onRefresh Called when a pull commits to a refresh.
 */
@Composable
fun GooPullRefresh(
    modifier: Modifier = Modifier,
    sightings: List<GooSighting> = Feed,
    incoming: List<Pair<GooSighting, GooSighting>> = Incoming,
    autoPlay: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val refreshing by rememberUpdatedState(onRefresh)
    val motion = remember(sightings, incoming) { GooMotion(incoming.size) }

    // Read during composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.clock = 0.0
        motion.hold = 0.0
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1.0 / 60 else min((now - last) / 1e9, 0.064)
                last = now
                if (dt > 0 && motion.height > 0) {
                    when (advance(motion, dt, reducedMotion)) {
                        1 -> {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            refreshing?.invoke()
                        }
                        2 -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                motion.frame = now
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PAGE)
            .clipToBounds()
            .semantics { contentDescription = "Sightings feed. Pull down to refresh." }
            .pointerInput(motion, reducedMotion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    var panning = false
                    var released = false
                    val m = motion
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val dx = (change.position.x - start.x) / density.density
                        val dy = (change.position.y - start.y) / density.density
                        if (!change.pressed) {
                            released = true
                            if (panning && m.mode == Mode.Drag) {
                                change.consume()
                                m.hold = USER_HOLD
                                m.release(if (reducedMotion) 0.0 else tracker.calculateVelocity().y / density.density.toDouble())
                            }
                            continue
                        }
                        if (!panning && sqrt(dx * dx + dy * dy) >= DRAG_SLOP) {
                            panning = true
                            m.hold = USER_HOLD
                            m.byUser = true
                            m.segment = -1
                            // Caught where it is; past an edge, the finger takes up the stretch it would have made.
                            val s = m.scroll
                            val span = m.height * RUBBER_SPAN
                            val bottom = m.floorOf(m.head)
                            m.dragFrom = when {
                                s > m.ceiling -> m.ceiling + unrubber(s - m.ceiling, span)
                                s < bottom -> bottom - unrubber(bottom - s, span)
                                else -> s
                            }
                            m.mode = Mode.Drag
                        }
                        if (panning) {
                            change.consume()
                            if (m.mode == Mode.Drag) {
                                m.hold = USER_HOLD
                                m.scroll = m.bounded(m.dragFrom + dy)
                            }
                        }
                    }
                    // A finger lost to the system lets go of the feed.
                    if (!released && m.mode == Mode.Drag) m.release(0.0)
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val barBottom = safeTop + HEADER_H
        val listTop = barBottom + LIST_TOP
        val layout = remember(sightings, incoming, width) { layoutTiles(sightings, incoming, width) }
        SideEffect {
            motion.height = height.toDouble()
            motion.barBottom = barBottom.toDouble()
            motion.bottomInset = safeBottom.toDouble()
            motion.total = layout.total
            motion.prefix = layout.prefix
        }

        layout.tiles.forEachIndexed { index, tile ->
            GooTileView(
                tile = tile,
                width = layout.column,
                modifier = Modifier.graphicsLayer {
                    motion.frame
                    // 0 while hidden, 1 once in: each new row takes its turn as the hidden count falls.
                    val shown = if (tile.fresh) clamp01(tile.row - motion.head + 1).toFloat() else 1f
                    val y = listTop + motion.scroll + tile.y - hiddenReach(layout.prefix, motion.head)
                    // Tiles off screen are not drawn at all.
                    val onScreen = y < height && y + tile.photo + TEXT_H > 0
                    translationX = tile.x.dp.toPx()
                    translationY = y.toFloat().dp.toPx()
                    val s = if (reducedMotion) 1f else 0.92f + 0.08f * shown
                    scaleX = s
                    scaleY = s
                    alpha = if (onScreen) shown else 0f
                    transformOrigin = TransformOrigin.Center
                },
                key = index,
            )
        }

        GooLiquid(motion, width, barBottom, reducedMotion)

        GooHeader(width, safeTop)
    }
}

/**
 * The liquid: shapes in one layer that is blurred and then cut at an alpha threshold (a colour
 * matrix that steepens alpha), so shapes that come close melt together; then a glint on the drop.
 * Before Android 12 there is no render effect, and the shapes are drawn as they are.
 */
@Composable
private fun GooLiquid(motion: GooMotion, width: Float, barBottom: Float, reducedMotion: Boolean) {
    val density = LocalDensity.current
    val effect = remember(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sigma = (GOO_BLUR * density.density).toFloat()
            // Android takes a blur radius and turns it into sigma = 0.57735 · radius + 0.5.
            val radius = max(0.1f, (sigma - 0.5f) / 0.57735f)
            val a = ACCENT
            val matrix = floatArrayOf(
                0f, 0f, 0f, 0f, a.red * 255f,
                0f, 0f, 0f, 0f, a.green * 255f,
                0f, 0f, 0f, 0f, a.blue * 255f,
                0f, 0f, 0f, 18f, -7f * 255f,
            )
            RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(matrix),
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL),
            ).asComposeRenderEffect()
        } else {
            null
        }
    }
    val top = barBottom - GOO_ABOVE
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(GOO_SPAN.dp)
            .graphicsLayer {
                motion.frame
                translationY = top.dp.toPx()
                val active = !(motion.phase == Phase.Idle && motion.scroll < 0.5 && motion.men < MEN_REST + 3)
                alpha = if (active) 1f else 0f
                renderEffect = if (active) effect else null
            },
    ) {
        motion.frame
        scale(density.density, density.density, pivot = Offset.Zero) {
            drawLiquid(motion, width.toDouble(), GOO_ABOVE.toDouble(), if (effect == null) ACCENT else Color.Black, reducedMotion, glint = false)
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(GOO_SPAN.dp)
            .graphicsLayer { translationY = top.dp.toPx() },
    ) {
        motion.frame
        scale(density.density, density.density, pivot = Offset.Zero) {
            drawLiquid(motion, width.toDouble(), GOO_ABOVE.toDouble(), Color.Black, reducedMotion, glint = true)
        }
    }
}

/** The liquid's shapes (or, with [glint], only the drop's highlight), in dp, the header's edge at [bar]. */
private fun DrawScope.drawLiquid(m: GooMotion, width: Double, bar: Double, ink: Color, reducedMotion: Boolean, glint: Boolean) {
    val p = m.phase
    val gap = max(0.0, m.scroll)
    val men = m.men
    if (width <= 0 || (p == Phase.Idle && gap < 0.5 && men < MEN_REST + 3)) return
    val cx = width / 2
    // The liquid canvas starts GOO_ABOVE above the header's edge: shift the motion's screen ys.
    val shift = bar - m.barBottom
    fun oval(x: Double, y: Double, rx: Double, ry: Double, color: Color = ink) =
        drawOval(color, Offset((x - rx).toFloat(), (y - ry).toFloat()), Size((rx * 2).toFloat(), (ry * 2).toFloat()))
    fun neck(y0: Double, y1: Double, w: Double) {
        if (w < 0.3 || y1 <= y0) return
        for (k in 1..NECK_BEADS) {
            val t = k / (NECK_BEADS + 1.0)
            val r = w * (0.62 + 0.38 * abs(2 * t - 1).pow(1.4))
            oval(cx, mix(y0, y1, t), r, r)
        }
    }
    // A drop of radius `r` squashed by `s` (taller when positive), keeping about the same area.
    fun drop(cy: Double, r: Double, s: Double) = oval(cx, cy, r * (1 - s * 0.5), r * (1 + s))

    var gy = 0.0
    var gr = 0.0
    if (!glint) oval(cx, bar + men - RES_RY, RES_RX, RES_RY)
    if (p == Phase.Idle) {
        val q = clamp01(gap / SNAP_PULL)
        val r = mix(BEAD_R, DROP_R, q) * m.grow
        if (r > 0.5) {
            val cy = bar + gap * DROP_TRAVEL
            if (!glint) {
                val w = min(r * 0.85, mix(NECK_WIDE, NECK_THIN, q.pow(1.2))) * m.grow
                neck(bar + men, cy - r * 0.6, w)
                drop(cy, r, 0.0)
            }
            gy = cy
            gr = r
        }
    } else {
        val y = m.dropY + shift
        val r = m.dropR
        val age = m.jiggleAge
        val jiggle = JIGGLE * exp(-JIGGLE_DECAY * age) * sin(2 * PI * JIGGLE_HZ * age)
        val s = if (reducedMotion) 0.0 else max(-0.25, min(0.3, abs(m.dropV) * STRETCH_PER_SPEED + jiggle))
        val loader = p == Phase.Loading || p == Phase.Merging
        if (!glint) {
            if (p == Phase.Free) {
                neck(bar + men, y - r * 0.6, NECK_THIN * m.neck)
            } else if (p == Phase.Returning) {
                // A neck forms again as the drop nears the meniscus, and pulls it in.
                val near = clamp01(1 - (y - r - (bar + men)) / 36)
                neck(bar + men, y - r * 0.6, 7 * near)
            }
            drop(y, r, if (loader) 0.0 else s)
            if (loader) {
                // Three droplets breathe out of the core and back while the trio turns.
                val t = m.phaseTime
                val spread = if (p == Phase.Loading) easeOut(t / SPREAD_IN) else 1 - easeInOut(t / MERGE_TIME)
                val breath = if (reducedMotion) 0.0 else 0.5 - 0.5 * cos(2 * PI * (if (p == Phase.Loading) t else REFRESH_TIME + t) / PULSE)
                val orbit = ORBIT * spread * breath
                for (k in 0 until 3) {
                    val a = (m.spin + k * 120) * PI / 180
                    val sr = SAT_R * spread * (1 - 0.18 * breath)
                    if (sr > 0.3) oval(cx + cos(a) * orbit, y + sin(a) * orbit, sr, sr)
                }
            }
        }
        gy = y
        gr = r
    }
    // A soft highlight on the upper left of the drop, so it reads as liquid rather than a flat dot.
    if (glint && gr >= 6) {
        drawOval(
            Color.White.copy(alpha = 0.55f),
            Offset((cx - gr * 0.62).toFloat(), (gy - gr * 0.62).toFloat()),
            Size((gr * 0.6).toFloat(), (gr * 0.38).toFloat()),
        )
    }
}

@Composable
private fun GooHeader(width: Float, safeTop: Float) {
    Column(
        Modifier
            .width(width.dp)
            .height((safeTop + HEADER_H).dp)
            .background(HEADER)
            .padding(top = safeTop.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText("Sightings", style = TextStyle(color = INK, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            Box(Modifier.size(38.dp).background(CHIP, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Notifications, null, Modifier.size(18.dp), tint = INK)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clipToBounds()
                .padding(start = 18.dp, top = 6.dp)
                .wrapContentWidth(Alignment.Start, unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chips.forEachIndexed { index, chip ->
                Box(
                    Modifier
                        .height(34.dp)
                        .background(if (index == 0) INK else CHIP, RoundedCornerShape(17.dp))
                        .padding(horizontal = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(chip, style = TextStyle(color = if (index == 0) Color.White else INK, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
    // The hairline under the header.
    Box(
        Modifier
            .graphicsLayer { translationY = (safeTop + HEADER_H).dp.toPx() - 1f }
            .width(width.dp)
            .height((1f / LocalDensity.current.density).dp)
            .background(INK.copy(alpha = 0.12f)),
    )
}

/** One photo tile: the photo, a title line and the byline with its likes. */
@Composable
private fun GooTileView(tile: GooTile, width: Float, modifier: Modifier, key: Int) {
    val context = LocalContext.current
    val item = tile.item
    Column(modifier.width(width.dp).height((tile.photo + TEXT_H).dp)) {
        AsyncImage(
            model = remember(item.id, key, width) {
                ImageRequest.Builder(context)
                    .data(unsplash(item.id, (width * 2.5f).roundToInt(), (tile.photo * 2.5f).roundToInt()))
                    .crossfade(250)
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width.dp, tile.photo.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PHOTO_BLANK),
        )
        Row(
            Modifier.padding(top = 8.dp).height(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tile.fresh) Box(Modifier.size(7.dp).background(ACCENT, CircleShape))
            BasicText(
                item.title,
                Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = INK, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
            )
        }
        Row(
            Modifier.padding(top = 3.dp).height(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(17.dp).background(AVATAR, CircleShape), contentAlignment = Alignment.Center) {
                BasicText(item.by.take(1), style = TextStyle(color = INK, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            }
            BasicText(
                item.by,
                Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(12.dp), tint = MUTED)
            BasicText(formatLikes(item.likes), style = TextStyle(color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
        }
    }
}
