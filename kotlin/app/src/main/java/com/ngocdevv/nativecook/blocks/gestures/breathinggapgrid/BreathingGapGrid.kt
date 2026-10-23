package com.ngocdevv.nativecook.blocks.gestures.breathinggapgrid

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The grid: columns of square cells PITCH apart, centred on the screen. Closed up there are four,
// PITCH_SHARE of the width apart so the outer two run half off the edges, and a tile fills DENSE
// of its cell. Opened out the grid zooms out to AIRY_COLUMNS whole columns, a tile fills AIRY of
// its cell, and the margins at both edges are exactly as wide as the gaps between the tiles.
private const val PITCH_SHARE = 0.3f
private const val DENSE = 0.88f
private const val AIRY_COLUMNS = 8
private const val AIRY = 0.56f
// A pinch in by this factor goes all the way from closed to open.
private const val PINCH_RANGE = 2.4f
// Past either end a pinch keeps following at this share of its pull, never further than OVERSHOOT.
private const val RUBBER = 0.25f
private const val OVERSHOOT = 0.15f
// A second finger has to change the spread between the two by this share before a pinch begins.
private const val PINCH_SLOP = 0.03f
// Each column drifts upward at its own speed, in cells per second, from its own phase; the eight
// values repeat across the columns.
private val Drift = floatArrayOf(0.22f, 0.34f, 0.27f, 0.38f, 0.3f, 0.24f, 0.36f, 0.29f)
private val Phase = floatArrayOf(0.1f, 0.62f, 0.35f, 0.83f, 0.48f, 0.2f, 0.71f, 0.04f)
private val LANES = Drift.size
// The type tiles are set at TYPE_SIZE and scaled so their widest line fills TYPE_FILL of the tile.
private const val TYPE_SIZE = 100f
private const val TYPE_FILL = 0.62f
// A photo's lower half darkens towards its bottom edge by up to this alpha.
private const val SHADE_FROM = 0.5f
private const val SHADE_STRENGTH = 0x47

// Arrival: the tiles grow in from SEED of their size, rippling out from the middle of the screen
// over ENTER_SPREAD s, each over ENTER_EACH s; the drift starts BURST times faster and settles to
// its own speed over BURST_TIME s. A photo fades in over IMAGE_FADE s once it has loaded.
private const val ENTER_DELAY = 0.08f
private const val ENTER_SPREAD = 0.7f
private const val ENTER_EACH = 0.65f
private const val SEED = 0.55f
private const val BURST = 5f
private const val BURST_TIME = 1.8f
private const val IMAGE_FADE = 0.45f
private const val CHROME_DELAY = 450L
private const val CHROME_TIME = 600

// The caption's lane: as the gap opens, the rows within PART_RANGE of the middle part by up to
// PART dp either side, and a tile whose edge comes within CLEAR dp of the line fades out over a
// further CLEAR_SOFT dp.
private const val CAPTION_AT = 0.5f
private const val PART = 26f
private const val PART_RANGE = 150f
private const val PART_SOFT = 18f
private const val CLEAR = 6f
private const val CLEAR_SOFT = 18f
private const val LANE_HEIGHT = 120f
private const val LANE_STRENGTH = 0xB3
// The caption's letters arrive one after another once the gap is CAPTION_FROM open, each rising
// LETTER_RISE dp out of a LETTER_BLUR dp blur; rules then draw out from it to the margins.
private const val CAPTION_FROM = 0.35f
private const val LETTER_SPREAD = 0.6f
private const val LETTER_RISE = 12f
private const val LETTER_BLUR = 5f
private const val CAPTION_SIZE = 13f
private const val CAPTION_TRACKING = 3.2f
private const val RULE_GAP = 16f
private const val RULE_MARGIN = 20f

// Snapping the gap after a pinch: the pinch's speed carries it this far ahead, in seconds.
private const val PINCH_THROW = 0.15f
private const val GAP_SPRING_MS = 520f
private const val GAP_SPRING_RATIO = 0.86f
// A dragged wall keeps gliding, losing this share of its speed every millisecond.
private const val GLIDE_DECAY = 0.997f

// An open tile: its side margin, and where its centre sits as a share of the screen.
private const val OPEN_MARGIN = 24f
private const val OPEN_AT = 0.42f
private const val OPEN_SPRING_MS = 520f
private const val OPEN_SPRING_RATIO = 0.88f
// Behind an open tile the wall blurs by up to WALL_BLUR and dims by WALL_DIM, and a glow in the
// tile's own colour spreads GLOW_BLUR dp below it.
private const val WALL_BLUR = 10f
private const val WALL_DIM = 0.8f
private const val GLOW = 0.5f
private const val GLOW_BLUR = 34f
private const val GLOW_DROP = 20f
// Dragging an open tile: it follows the finger, shrinking by up to DRAG_SHRINK over SHRINK_SHARE
// of the screen's height pulled down, and resisting at LIFT_RUBBER when pushed up. The wall comes
// back by AWAY_RELIEF as the tile is pulled AWAY_SHARE of the height away.
private const val DRAG_SHRINK = 0.45f
private const val SHRINK_SHARE = 0.6f
private const val LIFT_RUBBER = 0.3f
private const val AWAY_SHARE = 0.4f
private const val AWAY_RELIEF = 0.75f
private const val DISMISS_DISTANCE = 110f
private const val DISMISS_SPEED = 650f
private const val DRAG_SPRING_MS = 460f
private const val DRAG_SPRING_RATIO = 0.9f
// The open tile's note and title sit DETAIL_GAP dp below it and rise CAPTION_RISE dp into place.
private const val DETAIL_GAP = 22f
private const val CAPTION_RISE = 16f
// The wall darkens towards the top and bottom edges, so the labels there stay legible.
private const val EDGE_FADE = 96f
private const val EDGE_STRENGTH = 0xE6

// autoPlay loop (seconds), with the columns always drifting: the gap opens, closes, a tile opens
// and closes again.
private const val LOOP = 11f
private const val OPEN_GAP_AT = 1.4f
private const val CLOSE_GAP_AT = 4.8f
private const val OPEN_TILE_AT = 6.6f
private const val CLOSE_TILE_AT = 8.8f
private const val GAP_TIME = 1200f
private val EaseInOut = androidx.compose.animation.core.CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
// Reanimated's default timing curve, used where the React Native version gives none.
private fun quadInOut(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f
private const val REDUCED_MS = 200f
// After a touch the loop waits this long before it starts over.
private const val USER_HOLD = 3f
// A tap is a touch lifted within this long without turning into a drag.
private const val TAP_MS = 400L
private const val DRAG_SLOP = 6f

private const val NONE = -1000

private class Loaded(val image: Bitmap, val at: Float)

private class Layout(
    val width: Float,
    val height: Float,
    val pitch: Float,
    val side: Float,
    val time: Float,
    val scroll: Float,
    val part: Float,
)

private fun clamp01(value: Float) = value.coerceIn(0f, 1f)

private fun easeOut(p: Float) = 1f - (1f - p) * (1f - p) * (1f - p)

/**
 * Which tile fills cell (c, r): a fixed shuffle. With the 87 tiles here, the nearest repeat of any
 * tile is at least eight cells away, further than the grid shows when closed up.
 */
private fun tileAt(c: Int, r: Int, n: Int) = (c * 3 + r * 8).mod(n)

private fun lane(c: Int) = c.mod(LANES)

/** The grid's spacing for a gap `spread` open (0 closed up, 1 opened out). */
private fun layoutOf(width: Float, height: Float, spread: Float, time: Float, scroll: Float): Layout {
    val g = spread.coerceIn(-OVERSHOOT, 1f + OVERSHOOT)
    val dense = width * PITCH_SHARE
    val airy = width / (AIRY_COLUMNS + 1 - AIRY)
    // Zooming evenly: the pitch changes by the same factor for every step of the gap.
    val pitch = dense * (airy / dense).pow(g)
    val side = pitch * (DENSE + (AIRY - DENSE) * g)
    val p = clamp01(g)
    return Layout(width, height, pitch, side, time, scroll, p * p * (3f - 2f * p))
}

/** The middle of cell (c, r): its column drifts and scrolls in cells; rows part around the caption. */
private fun cellCentre(c: Int, r: Int, l: Layout): Offset {
    val m = lane(c)
    val x = l.width / 2f + (c - 1.5f) * l.pitch
    val y = l.height / 2f + (r + Phase[m] - l.time * Drift[m] + l.scroll) * l.pitch
    val d = y - l.height * CAPTION_AT
    val reach = max(0f, 1f - abs(d) / PART_RANGE)
    return Offset(x, y + (d / (abs(d) + PART_SOFT)) * PART * l.part * reach * reach)
}

/** How many columns have their middle on screen. */
private fun columnsShown(width: Float, pitch: Float) = 2 * floor(width / 2f / pitch + 0.5f).toInt()

/**
 * A value driven per frame the way Reanimated drives `withSpring({ duration, dampingRatio })` and
 * `withTiming`, so the gap and the open tile move on the same curves as in the React Native version.
 */
private class DrivenValue {
    // Snapshot state, so the layers that read it are drawn again as it moves.
    private val state = mutableFloatStateOf(0f)
    var value: Float
        get() = state.floatValue
        private set(next) {
            state.floatValue = next
        }
    private var mode = 0 // 0 still, 1 spring, 2 timing
    private var to = 0f
    private var velocity = 0f
    private var zeta = 0f
    private var omega0 = 0f
    private var omega1 = 0f
    private var stiffness = 0f
    private var initialEnergy = 0f
    private var from = 0f
    private var time = 0f
    private var duration = 0f
    private var easing: (Float) -> Float = { it }

    /** Puts the value somewhere at once, stopping whatever was moving it. */
    fun set(next: Float) {
        value = next
        mode = 0
        velocity = 0f
    }

    fun springTo(target: Float, speed: Float, durationMs: Float, dampingRatio: Float) {
        // A spring already heading to the same place carries on as it was.
        if (mode == 1 && to == target) return
        var v = (if (mode == 1) velocity else 0f) + speed
        // Speed that would carry the first frame away from the target is dropped.
        if ((target > value && v < 0f) || (target < value && v > 0f)) v = 0f
        val x0 = value - target
        mode = 1
        to = target
        velocity = v
        zeta = dampingRatio
        stiffness = stiffnessFor(x0, v, durationMs, dampingRatio)
        omega0 = sqrt(stiffness)
        omega1 = if (zeta < 1f) omega0 * sqrt(1f - zeta * zeta) else 0f
        initialEnergy = energy(x0, speed)
    }

    fun timeTo(target: Float, durationMs: Float, curve: (Float) -> Float) {
        mode = 2
        from = value
        to = target
        time = 0f
        duration = durationMs / 1000f
        velocity = 0f
        easing = curve
    }

    fun step(dt: Float) {
        when (mode) {
            1 -> {
                val x0 = value - to
                val v0 = velocity
                if (zeta < 1f) {
                    val sin1 = sin(omega1 * dt)
                    val cos1 = cos(omega1 * dt)
                    val envelope = exp(-zeta * omega0 * dt)
                    val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
                    value = to + frag
                    velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
                } else {
                    val envelope = exp(-omega0 * dt)
                    value = to + envelope * (x0 + (v0 + omega0 * x0) * dt)
                    velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
                }
                if (initialEnergy == 0f || energy(to - value, velocity) / initialEnergy <= ENERGY_THRESHOLD) {
                    value = to
                    velocity = 0f
                    mode = 0
                }
            }
            2 -> {
                time += dt
                val p = clamp01(time / duration)
                value = from + (to - from) * easing(p)
                if (p >= 1f) mode = 0
            }
        }
    }

    private fun energy(x: Float, v: Float) = 0.5f * stiffness * x * x + 0.5f * v * v

    companion object {
        const val ENERGY_THRESHOLD = 6e-9f

        /** The stiffness Reanimated solves for so a spring of mass 1 settles in 1.5 × the duration. */
        fun stiffnessFor(x0: Float, v0: Float, durationMs: Float, zeta: Float): Float {
            if (x0 == 0f && v0 == 0f) return 0f
            val settling = durationMs * 1.5 / 1000.0
            fun diff(k: Double): Double {
                val omega = sqrt(k) * zeta
                val a = x0 + (v0 + x0 * omega) * settling
                val e = exp(-omega * settling)
                val xt = a * e
                val vt = a * e * -omega + (v0 + x0 * omega) * e
                val e0 = 0.5 * k * x0 * x0 + 0.5 * v0 * v0
                val et = 0.5 * k * xt * xt + 0.5 * vt * vt
                return et / e0 - ENERGY_THRESHOLD
            }
            var lo = Math.ulp(1.0)
            var hi = 8e3
            val direction = if (diff(hi) >= diff(lo)) 1 else -1
            var current = (lo + hi) / 2
            var left = 100
            while (abs(diff(current)) > ENERGY_THRESHOLD * 1e-3 && left > 0) {
                left -= 1
                if (diff(current) * direction < 0) lo = current else hi = current
                current = (lo + hi) / 2
            }
            return current.toFloat()
        }
    }
}

/** Everything the frame loop, the gestures and the pictures share. */
private class GridMotion(count: Int) {
    // Seconds since the wall first appeared. Both pictures read it, so they are drawn every frame.
    var elapsed by mutableFloatStateOf(0f)
    // The photos, each with the moment it arrived, so it can fade in.
    val loaded = arrayOfNulls<Loaded>(count)
    // The larger copy of the tile that is open, and which tile it belongs to.
    var sharp: Loaded? = null
    var sharpIndex = -1
    // 0 closed up … 1 opened out.
    val spread = DrivenValue()
    var pinchFrom = 0f
    var pinching = false
    // Seconds of drift so far; it slows to a stop while a tile is open.
    var time = 0f
    // The user's own drag of the wall, in cells, and its glide after release in cells per second.
    var scroll = 0f
    var glide = 0f
    var dragFrom = 0f
    var dragPitch = 1f
    // 0 shut, 1 open, and the cell it applies to.
    val open = DrivenValue()
    var openC = NONE
    var openR = 0
    var isOpen = false
    // How far the open tile has been pulled from its place, in dp.
    val dragX = DrivenValue()
    val dragY = DrivenValue()
    var liftFromX = 0f
    var liftFromY = 0f
    var dragMode = 0
    var clock = 0f
    var looping = false
    var hold = 0f
    var columns = 0
    // The screen, in dp, and the safe area at its top and bottom.
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f

    fun layout(time: Float = this.time, scroll: Float = this.scroll) = layoutOf(width, height, spread.value, time, scroll)

    /** How far the open tile has been pulled away, 0 … 1, and how much of the wall it still hides. */
    fun away() = clamp01((max(0f, dragY.value) + abs(dragX.value) * 0.4f) / (height * AWAY_SHARE))

    fun wall() = clamp01(open.value) * (1f - AWAY_RELIEF * away())
}

/**
 * A wall of square tiles in four drifting columns, photos mixed with set type. Pinching opens the
 * gap between the tiles: the wall zooms out to eight whole columns, and the rows part around the
 * middle for a line of text that arrives a letter at a time; a release settles on closed or open.
 * The wall can be dragged, and tapping a tile lifts it out over the blurred wall with its title;
 * the open tile follows the finger, and a pull down puts it back in its cell.
 *
 * @param tiles What the columns are filled with, repeated as far as they run.
 * @param caption The line that opens up between the tiles once the gap is wide.
 * @param autoPlay Opens the gap, closes it, opens a tile, closes it and starts over, while the columns drift.
 * @param onOpen Called with a tile when it is opened.
 */
@Composable
fun BreathingGapGrid(
    modifier: Modifier = Modifier,
    tiles: List<GridTile> = GridTiles,
    caption: String = "Room to breathe",
    autoPlay: Boolean = false,
    background: Color = Color.Black,
    textColor: Color = Color(0xFFF2F1EC),
    onOpen: ((GridTile) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val count = tiles.size
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val opening by rememberUpdatedState(onOpen)
    val motion = remember(tiles) { GridMotion(count) }
    val paints = remember(tiles, caption) { GridPaints(tiles, caption) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Read during composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect {
        motion.top = safeTop
        motion.bottom = safeBottom
    }

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.clock = 0f
        motion.hold = 0f
    }

    // The labels fade in once the tiles are on their way.
    val chrome = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(CHROME_DELAY)
        chrome.animateTo(1f, tween(CHROME_TIME, easing = EaseOutStrong))
    }

    val loader = remember(context) { SingletonImageLoader.get(context) }
    suspend fun fetch(uri: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(Size.ORIGINAL)
            // A software bitmap can carry mipmaps, which keep the small tiles from shimmering.
            .allowHardware(false)
            .build()
        val result = loader.execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap().also { it.setHasMipMap(true) }
    }

    // Each photo arrives on its own; until then its tile shows its tint.
    LaunchedEffect(motion) {
        tiles.forEachIndexed { i, tile ->
            if (tile !is GridTile.Photo) return@forEachIndexed
            launch {
                val image = fetch(tile.uri) ?: return@launch
                motion.loaded[i] = Loaded(image, motion.elapsed)
            }
        }
    }

    fun committed() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun opened(index: Int, user: Boolean) {
        val tile = tiles[index]
        if (tile is GridTile.Photo && tile.full != null && motion.sharpIndex != index) {
            scope.launch {
                val image = fetch(tile.full) ?: return@launch
                motion.sharp = Loaded(image, motion.elapsed)
                motion.sharpIndex = index
            }
        }
        if (!user) return
        committed()
        opening?.invoke(tile)
    }

    fun setGap(to: Float, velocity: Float, timed: Boolean) {
        when {
            reducedMotion -> motion.spread.timeTo(to, REDUCED_MS, ::quadInOut)
            timed -> motion.spread.timeTo(to, GAP_TIME, EaseInOut::transform)
            else -> motion.spread.springTo(to, velocity, GAP_SPRING_MS, GAP_SPRING_RATIO)
        }
    }

    fun settleOpen(to: Float) {
        motion.isOpen = to == 1f
        if (reducedMotion) motion.open.timeTo(to, REDUCED_MS, EaseOutStrong::transform)
        else motion.open.springTo(to, 0f, OPEN_SPRING_MS, OPEN_SPRING_RATIO)
    }

    // The open tile returns to its place, carrying the finger's speed.
    fun settleDrag(vx: Float, vy: Float) {
        if (reducedMotion) {
            motion.dragX.timeTo(0f, REDUCED_MS, EaseOutStrong::transform)
            motion.dragY.timeTo(0f, REDUCED_MS, EaseOutStrong::transform)
            return
        }
        motion.dragX.springTo(0f, vx, DRAG_SPRING_MS, DRAG_SPRING_RATIO)
        motion.dragY.springTo(0f, vy, DRAG_SPRING_MS, DRAG_SPRING_RATIO)
    }

    fun openCell(c: Int, r: Int, user: Boolean) {
        motion.openC = c
        motion.openR = r
        motion.dragX.set(0f)
        motion.dragY.set(0f)
        settleOpen(1f)
        opened(tileAt(c, r, count), user)
    }

    fun touched() {
        motion.hold = USER_HOLD
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        val m = motion
        while (true) {
            withFrameNanos { frameTime ->
                val dt = if (last < 0) 1f / 60f else min((frameTime - last) / 1e9f, 0.064f)
                last = frameTime
                if (dt <= 0f || m.width == 0f) return@withFrameNanos
                m.spread.step(dt)
                m.open.step(dt)
                m.dragX.step(dt)
                m.dragY.step(dt)
                // A selection tick each time a pinch brings another pair of columns into view.
                val shown = columnsShown(m.width, layoutOf(m.width, m.height, m.spread.value, 0f, 0f).pitch)
                if (m.columns != 0 && shown != m.columns && m.pinching) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                m.columns = shown

                val now = m.elapsed + dt
                m.elapsed = now
                if (!reducedMotion) {
                    // The drift arrives fast and settles to its own pace; it slows to a stop under an open tile.
                    val p = min(1f, now / BURST_TIME)
                    val burst = 1f + (BURST - 1f) * (1f - p) * (1f - p)
                    m.time += dt * burst * (1f - clamp01(m.open.value))
                }
                val v = m.glide
                if (v != 0f) {
                    val pitch = layoutOf(m.width, m.height, m.spread.value, 0f, 0f).pitch
                    m.scroll += v * dt
                    val next = v * GLIDE_DECAY.pow(dt * 1000f)
                    m.glide = if (abs(next * pitch) < 4f) 0f else next
                }
                if (m.hold > 0f) {
                    if (!m.isOpen) m.hold -= dt
                    if (m.hold <= 0f) m.clock = 0f
                    return@withFrameNanos
                }
                if (!m.looping) return@withFrameNanos
                val before = m.clock
                val t = (before + dt) % LOOP
                m.clock = t
                fun passed(at: Float) = (before < at && t >= at) || (t < before && (at > before || at <= t))
                if (passed(OPEN_GAP_AT)) setGap(1f, 0f, true)
                if (passed(CLOSE_GAP_AT)) setGap(0f, 0f, true)
                if (passed(OPEN_TILE_AT)) {
                    // The photo in the second column nearest where an open tile sits.
                    val l = m.layout()
                    val near = ((m.height * OPEN_AT - cellCentre(1, 0, l).y) / l.pitch).roundToInt()
                    var row = near
                    for (step in intArrayOf(0, 1, -1, 2, -2)) {
                        if (tiles[tileAt(1, near + step, count)] is GridTile.Photo) {
                            row = near + step
                            break
                        }
                    }
                    openCell(1, row, false)
                }
                if (passed(CLOSE_TILE_AT) && m.isOpen) settleOpen(0f)
            }
        }
    }

    fun pinchStart() {
        if (motion.isOpen) return
        touched()
        motion.pinching = true
        motion.pinchFrom = motion.spread.value
    }

    fun pinchUpdate(scale: Float) {
        if (motion.isOpen) return
        val next = motion.pinchFrom - ln(max(scale, 0.01f)) / ln(PINCH_RANGE)
        // Past either end it follows at a quarter of the pull.
        motion.spread.set(if (next > 1f) 1f + (next - 1f) * RUBBER else if (next < 0f) next * RUBBER else next)
    }

    fun pinchEnd(scale: Float, velocity: Float) {
        motion.pinching = false
        if (motion.isOpen) return
        // The pinch's speed in gap per second.
        val speed = -velocity / (ln(PINCH_RANGE) * max(scale, 0.01f))
        val ahead = motion.spread.value + speed * PINCH_THROW
        setGap(if (ahead > 0.5f) 1f else 0f, speed, false)
        committed()
    }

    fun panStart() {
        touched()
        motion.glide = 0f
        if (motion.isOpen || motion.open.value > 0.5f) {
            // Caught on its way back, the tile is held where it is.
            motion.dragMode = 1
            if (!motion.isOpen) settleOpen(1f)
            motion.liftFromX = motion.dragX.value
            motion.liftFromY = motion.dragY.value
            return
        }
        motion.dragMode = 0
        motion.dragFrom = motion.scroll
        motion.dragPitch = layoutOf(motion.width, motion.height, motion.spread.value, 0f, 0f).pitch
    }

    fun panUpdate(tx: Float, ty: Float) {
        if (motion.dragMode == 1) {
            motion.dragX.set(motion.liftFromX + tx)
            val y = motion.liftFromY + ty
            motion.dragY.set(if (y > 0f) y else y * LIFT_RUBBER)
            return
        }
        motion.scroll = motion.dragFrom + ty / motion.dragPitch
    }

    fun panEnd(vx: Float, vy: Float) {
        if (motion.dragMode == 1) {
            val shut = vy > DISMISS_SPEED || (motion.dragY.value > DISMISS_DISTANCE && vy > -DISMISS_SPEED / 3f)
            if (shut) {
                settleOpen(0f)
                committed()
            }
            settleDrag(vx, vy)
            return
        }
        if (!reducedMotion) motion.glide = vy / motion.dragPitch
    }

    fun tapAt(x: Float, y: Float) {
        touched()
        motion.glide = 0f
        if (motion.isOpen) {
            settleOpen(0f)
            settleDrag(0f, 0f)
            return
        }
        // The cell under the finger: the column by x, then whichever nearby row is closest.
        val l = motion.layout()
        val c = ((x - motion.width / 2f) / l.pitch + 1.5f).roundToInt()
        val guess = ((y - cellCentre(c, 0, l).y) / l.pitch).roundToInt()
        var best = guess
        var off = Float.MAX_VALUE
        for (r in guess - 1..guess + 1) {
            val d = abs(cellCentre(c, r, l).y - y)
            if (d < off) {
                off = d
                best = r
            }
        }
        if (off > l.side / 2f + (l.pitch - l.side) / 2f) return
        openCell(c, best, true)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(background)
            .semantics { contentDescription = caption }
            .pointerInput(motion, reducedMotion) {
                val d = density.density
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)
                    // 0 waiting, 1 dragging with one finger, 2 two fingers down, 3 nothing more to do.
                    var mode = 0
                    var many = false
                    var startSpan = 0f
                    var pinchActive = false
                    var scale = 1f
                    val scaleTracker = VelocityTracker1D(isDataDifferential = false)
                    var panStartAt = down.position
                    var panId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            val lifted = event.changes.firstOrNull { it.id == panId } ?: event.changes.first()
                            when {
                                mode == 1 -> {
                                    tracker.addPointerInputChange(lifted)
                                    val velocity = tracker.calculateVelocity()
                                    panEnd(velocity.x / d, velocity.y / d)
                                }
                                mode == 2 && pinchActive -> pinchEnd(scale, scaleTracker.calculateVelocity())
                                mode == 0 && !many && lifted.uptimeMillis - down.uptimeMillis <= TAP_MS ->
                                    tapAt(lifted.position.x / d, lifted.position.y / d)
                            }
                            motion.pinching = false
                            event.changes.forEach(PointerInputChange::consume)
                            break
                        }
                        if (pressed.size >= 2) {
                            many = true
                            if (mode == 1) {
                                // A second finger ends a drag; the pinch takes over from here.
                                panEnd(0f, 0f)
                            }
                            if (mode != 2 && mode != 3) {
                                mode = 2
                                startSpan = span(pressed[0], pressed[1])
                            }
                            if (mode == 2) {
                                scale = if (startSpan > 0f) span(pressed[0], pressed[1]) / startSpan else 1f
                                if (!pinchActive && abs(scale - 1f) > PINCH_SLOP) {
                                    pinchActive = true
                                    pinchStart()
                                }
                                if (pinchActive) {
                                    scaleTracker.addDataPoint(pressed[0].uptimeMillis, scale)
                                    pinchUpdate(scale)
                                }
                            }
                            event.changes.forEach(PointerInputChange::consume)
                            continue
                        }
                        // One finger down.
                        if (mode == 2) {
                            if (pinchActive) pinchEnd(scale, scaleTracker.calculateVelocity())
                            mode = 3
                        }
                        val change = pressed[0]
                        if (mode == 0 && change.id != panId) {
                            panId = change.id
                            panStartAt = change.position
                        }
                        if (change.id == panId) tracker.addPointerInputChange(change)
                        val tx = (change.position.x - panStartAt.x) / d
                        val ty = (change.position.y - panStartAt.y) / d
                        if (mode == 0 && !many && sqrt(tx * tx + ty * ty) >= DRAG_SLOP) {
                            mode = 1
                            panStart()
                        }
                        if (mode == 1) {
                            change.consume()
                            panUpdate(tx, ty)
                        }
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        motion.width = width
        motion.height = height

        // The wall, blurred behind an open tile (API 31 and up).
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val wall = motion.wall()
                    renderEffect = if (wall > 0.002f && !reducedMotion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val radius = sigmaToRadius(WALL_BLUR * wall * density.density)
                        BlurEffect(radius, radius, TileMode.Clamp)
                    } else {
                        null
                    }
                },
        ) {
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { drawWall(it.nativeCanvas, motion, tiles, paints, background, textColor, reducedMotion) }
            }
        }
        // The dimmed wall, the open tile, the darkened edges and the readout.
        Canvas(Modifier.fillMaxSize()) {
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { drawFront(it.nativeCanvas, motion, tiles, paints, background, textColor, chrome.value) }
            }
        }

        val label = TextStyle(
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.SansSerif,
        )
        Row(
            Modifier
                .offset(y = (safeTop + 14f).dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .graphicsLayer { alpha = chrome.value },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("STUDIO NOTES", Modifier.weight(1f), style = label)
            BasicText("FRAMES", Modifier.weight(1f), style = label.copy(textAlign = TextAlign.Center))
            Spacer(Modifier.weight(1f))
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = (safeBottom + 18f).dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .graphicsLayer { alpha = chrome.value },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("PINCH", Modifier.weight(1f), style = label)
            BasicText("DRAG", Modifier.weight(1f), style = label.copy(textAlign = TextAlign.Center))
            BasicText("TAP", Modifier.weight(1f), style = label.copy(textAlign = TextAlign.End))
        }
    }
}

private fun span(a: PointerInputChange, b: PointerInputChange) = hypot(a.position.x - b.position.x, a.position.y - b.position.y)

private fun sansSerif(weight: Int, fallbackBold: Boolean): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, if (fallbackBold) Typeface.BOLD else Typeface.NORMAL)
    }

/** The paints, fonts and measurements the pictures reuse every frame. */
private class GridPaints(tiles: List<GridTile>, caption: String) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val type = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TYPE_SIZE
        typeface = sansSerif(700, true)
    }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f
        typeface = sansSerif(500, false)
    }
    val captionInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = CAPTION_SIZE
        typeface = sansSerif(600, true)
    }
    val meta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = sansSerif(500, false)
    }
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SANS_SERIF, 300, false)
        } else {
            Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
    }
    val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    // The darkening over a photo's lower half, in the tile's own unit square.
    val veil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, SHADE_FROM, 0f, 1f, 0, android.graphics.Color.argb(SHADE_STRENGTH, 0, 0, 0), Shader.TileMode.CLAMP)
    }
    val band = Paint()
    var bandFor = Float.NaN
    val top = Paint()
    val bottom = Paint()
    var edgesFor = Float.NaN
    val src = android.graphics.Rect()
    val dst = RectF()

    // Each type tile's line widths, measured once rather than every frame.
    val typeWidths: List<FloatArray> = tiles.map { tile ->
        if (tile is GridTile.Type) FloatArray(tile.lines.size) { type.measureText(tile.lines[it]) } else FloatArray(0)
    }

    // The caption's letters and where each starts.
    val letters: List<String> = caption.uppercase().map { it.toString() }
    val letterAt = FloatArray(letters.size)
    val captionWidth: Float

    init {
        var x = 0f
        letters.forEachIndexed { i, letter ->
            letterAt[i] = x
            x += advance(captionInk, letter) + CAPTION_TRACKING
        }
        captionWidth = x - CAPTION_TRACKING
    }
}

private fun Color.argb(alpha: Float) = copy(alpha = alpha).toArgb()

private fun drawTile(
    canvas: android.graphics.Canvas,
    tile: GridTile,
    loaded: Loaded?,
    widths: FloatArray,
    paints: GridPaints,
    x: Float,
    y: Float,
    side: Float,
    alpha: Float,
    now: Float,
    sharper: Loaded? = null,
) {
    if (alpha <= 0.002f || side <= 0.5f) return
    val paint = paints.fill
    when (tile) {
        is GridTile.Photo -> {
            val shown = if (loaded != null) clamp01((now - loaded.at) / IMAGE_FADE) else 0f
            if (shown < 1f) {
                paint.color = tile.tint.argb(alpha)
                canvas.drawRect(x, y, x + side, y + side, paint)
            }
            if (loaded != null && shown > 0f) {
                // The middle square of the photo, sampled smoothly at any size.
                paints.dst.set(x, y, x + side, y + side)
                drawSquare(canvas, loaded.image, paints, alpha * shown)
                if (sharper != null) {
                    // The larger copy, once it arrives, fades in over the small one.
                    drawSquare(canvas, sharper.image, paints, alpha * clamp01((now - sharper.at) / IMAGE_FADE))
                }
                paints.veil.alpha = (alpha * shown * 255f).roundToInt()
                canvas.save()
                canvas.translate(x, y)
                canvas.scale(side, side)
                canvas.drawRect(0f, 0f, 1f, 1f, paints.veil)
                canvas.restore()
            }
        }
        is GridTile.Type -> {
            paint.color = tile.paper.argb(alpha)
            canvas.drawRect(x, y, x + side, y + side, paint)
            val widest = widths.maxOrNull() ?: return
            val lineHeight = TYPE_SIZE * 0.86f
            val block = lineHeight * tile.lines.size
            val scale = min(side * TYPE_FILL / widest, side * TYPE_FILL / block)
            val ink = paints.type
            ink.color = tile.ink.argb(alpha)
            canvas.save()
            canvas.translate(x + side / 2f, y + side / 2f)
            canvas.scale(scale, scale)
            tile.lines.forEachIndexed { i, line ->
                val baseline = -block / 2f + lineHeight * (i + 1) - TYPE_SIZE * 0.14f
                canvas.drawText(line, -widths[i] / 2f, baseline, ink)
            }
            canvas.restore()
        }
    }
}

/** A photo's middle square into `paints.dst`. */
private fun drawSquare(canvas: android.graphics.Canvas, image: Bitmap, paints: GridPaints, alpha: Float) {
    val w = image.width
    val h = image.height
    val s = min(w, h)
    paints.src.set((w - s) / 2, (h - s) / 2, (w - s) / 2 + s, (h - s) / 2 + s)
    paints.art.alpha = (alpha * 255f).roundToInt()
    canvas.drawBitmap(image, paints.src, paints.dst, paints.art)
}

// Skia's `measureText`, which the React Native version spaces letters by, gives a glyph's bounds
// rather than its advance, and on the iPhone it was made on those bounds stand clear of the ink:
// each letter there takes about two points more than its advance. This keeps the same spacing.
private const val GLYPH_PAD = 2f

/** A letter's advance as the React Native version measures it; a lone space is the gap between two letters. */
private fun advance(paint: Paint, letter: String): Float =
    if (letter != " ") paint.measureText(letter) + GLYPH_PAD else paint.measureText("a a") - paint.measureText("aa")

/** Draws text a letter at a time, `tracking` dp apart. */
private fun drawTracked(canvas: android.graphics.Canvas, text: String, x: Float, y: Float, tracking: Float, paint: Paint) {
    var at = x
    for (letter in text) {
        val s = letter.toString()
        canvas.drawText(s, at, y, paint)
        at += advance(paint, s) + tracking
    }
}

/** The wall of tiles and the caption's lane: everything that blurs behind an open tile. */
private fun drawWall(
    canvas: android.graphics.Canvas,
    m: GridMotion,
    tiles: List<GridTile>,
    paints: GridPaints,
    background: Color,
    textColor: Color,
    reducedMotion: Boolean,
) {
    val width = m.width
    val height = m.height
    if (width <= 0f) return
    val count = tiles.size
    val now = m.elapsed
    val l = m.layout()
    val o = m.open.value
    val lifted = clamp01(o)
    val oc = m.openC
    val or = m.openR
    val reach = hypot(width / 2f, height / 2f)
    val captionY = height * CAPTION_AT

    val span = width / 2f + l.side
    val firstC = floor(1.5f - span / l.pitch).toInt()
    val lastC = ceil(1.5f + span / l.pitch).toInt()
    for (c in firstC..lastC) {
        val lane = lane(c)
        val shift = Phase[lane] - l.time * Drift[lane] + l.scroll
        val first = floor((-height / 2f - l.side - PART) / l.pitch - shift).toInt()
        val last = ceil((height / 2f + l.side + PART) / l.pitch - shift).toInt()
        for (r in first..last) {
            if (o > 0.001f && c == oc && r == or) continue
            val centre = cellCentre(c, r, l)
            // Arrival, rippling out from the middle by where the cell started.
            val start = hypot(centre.x - width / 2f, (r + Phase[lane]) * l.pitch) / reach
            val grown = if (reducedMotion) clamp01(now / 0.3f) else easeOut(clamp01((now - ENTER_DELAY - start * ENTER_SPREAD) / ENTER_EACH))
            if (grown <= 0f) continue
            // Tiles give way to the caption's lane as the gap opens.
            val clearance = abs(centre.y - captionY) - l.side / 2f
            val clear = clamp01((clearance - CLEAR) / CLEAR_SOFT)
            val alpha = grown * (1f - l.part * (1f - clear))
            val side = l.side * (SEED + (1f - SEED) * grown)
            val index = tileAt(c, r, count)
            drawTile(canvas, tiles[index], m.loaded[index], paints.typeWidths[index], paints, centre.x - side / 2f, centre.y - side / 2f, side, alpha, now)
        }
    }

    // The caption, in a darkened lane, a letter at a time.
    val reveal = clamp01((l.part - CAPTION_FROM) / (1f - CAPTION_FROM)) * (1f - lifted)
    if (reveal <= 0.001f) return
    if (paints.bandFor != captionY) {
        paints.band.shader = LinearGradient(
            0f, captionY - LANE_HEIGHT / 2f, 0f, captionY + LANE_HEIGHT / 2f,
            intArrayOf(background.argb(0f), background.argb(LANE_STRENGTH / 255f), background.argb(0f)),
            null,
            Shader.TileMode.CLAMP,
        )
        paints.bandFor = captionY
    }
    paints.band.alpha = (reveal * 255f).roundToInt()
    canvas.drawRect(0f, captionY - LANE_HEIGHT / 2f, width, captionY + LANE_HEIGHT / 2f, paints.band)
    val left = width / 2f - paints.captionWidth / 2f
    val baseline = captionY + CAPTION_SIZE * 0.36f
    val n = paints.letters.size
    val ink = paints.captionInk
    for (i in 0 until n) {
        val p = clamp01(reveal * (1f + LETTER_SPREAD) - (if (n > 1) i / (n - 1f) else 0f) * LETTER_SPREAD)
        if (p <= 0f) continue
        val e = easeOut(p)
        ink.color = textColor.argb(e)
        val blur = if (reducedMotion) 0f else (1f - e) * LETTER_BLUR
        // Mask filters on a hardware canvas need API 28; below that the letters only fade and rise.
        ink.maskFilter = if (blur > 0.1f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            BlurMaskFilter(sigmaToRadius(blur), BlurMaskFilter.Blur.NORMAL)
        } else {
            null
        }
        val rise = if (reducedMotion) 0f else (1f - e) * LETTER_RISE
        canvas.drawText(paints.letters[i], left + paints.letterAt[i], baseline + rise, ink)
    }
    ink.maskFilter = null
    // Rules drawn out from the caption to the margins, once most of it has arrived.
    val ruled = easeOut(clamp01((reveal - 0.45f) / 0.55f))
    if (ruled > 0f) {
        val rule = paints.fill
        rule.color = textColor.argb(0.45f * ruled)
        val full = left - RULE_GAP - RULE_MARGIN
        val length = full * ruled
        canvas.drawRect(left - RULE_GAP - length, captionY - 0.5f, left - RULE_GAP, captionY + 0.5f, rule)
        canvas.drawRect(width - left + RULE_GAP, captionY - 0.5f, width - left + RULE_GAP + length, captionY + 0.5f, rule)
    }
}

/** The dimmed wall, the open tile with its glow and title, the darkened edges and the readout. */
private fun drawFront(
    canvas: android.graphics.Canvas,
    m: GridMotion,
    tiles: List<GridTile>,
    paints: GridPaints,
    background: Color,
    textColor: Color,
    chrome: Float,
) {
    val width = m.width
    val height = m.height
    if (width <= 0f) return
    val count = tiles.size
    val now = m.elapsed
    val l = m.layout()
    val o = m.open.value
    val lifted = clamp01(o)
    val dx = m.dragX.value
    val dy = m.dragY.value
    val away = m.away()
    val wall = m.wall()
    val oc = m.openC
    val or = m.openR
    val openSide = width - OPEN_MARGIN * 2f
    val openY = height * OPEN_AT

    if (wall > 0.002f) {
        paints.fill.color = background.argb(WALL_DIM * wall)
        canvas.drawRect(0f, 0f, width, height, paints.fill)
    }

    if (o > 0.001f && oc != NONE) {
        // The open tile, between its cell and the middle of the screen, following the finger.
        val from = cellCentre(oc, or, l)
        val shrink = 1f - DRAG_SHRINK * clamp01(dy / (height * SHRINK_SHARE))
        val s = l.side + (openSide * shrink - l.side) * o
        val cx = from.x + (width / 2f + dx - from.x) * o
        val cy = from.y + (openY + dy - from.y) * o
        val index = tileAt(oc, or, count)
        val tile = tiles[index]
        // A glow in the tile's own colour, spreading below it (mask filters need API 28 here).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val glow = paints.glow
            glow.color = (if (tile is GridTile.Photo) tile.tint else (tile as GridTile.Type).paper).argb(GLOW * lifted * (1f - away))
            glow.maskFilter = BlurMaskFilter(sigmaToRadius(GLOW_BLUR * max(0.2f, s / openSide)), BlurMaskFilter.Blur.NORMAL)
            canvas.drawRect(cx - s * 0.45f, cy - s * 0.45f + GLOW_DROP * lifted, cx + s * 0.45f, cy + s * 0.45f + GLOW_DROP * lifted, glow)
        }
        val larger = if (m.sharpIndex == index) m.sharp else null
        drawTile(canvas, tile, m.loaded[index], paints.typeWidths[index], paints, cx - s / 2f, cy - s / 2f, s, 1f, now, larger)

        // Its note and title, riding just below it.
        val shown = clamp01((o - 0.55f) / 0.45f) * (1f - clamp01(away * 3f))
        if (shown > 0.01f) {
            val e = easeOut(shown)
            val top = cy + s / 2f + DETAIL_GAP + (1f - e) * CAPTION_RISE
            paints.meta.color = textColor.argb(0.6f * e)
            drawTracked(canvas, tile.note.uppercase(), cx - s / 2f, top + 12f, 1f, paints.meta)
            paints.title.color = textColor.argb(e)
            canvas.drawText(tile.title, cx - s / 2f, top + 12f + 8f + 30f, paints.title)
        }
    }

    // The top and bottom edges sink into the background, so the labels there stay legible.
    val topEdge = m.top + EDGE_FADE
    val bottomEdge = height - m.bottom - EDGE_FADE
    if (paints.edgesFor != topEdge * 10000f + bottomEdge) {
        paints.top.shader = LinearGradient(0f, 0f, 0f, topEdge, background.argb(EDGE_STRENGTH / 255f), background.argb(0f), Shader.TileMode.CLAMP)
        paints.bottom.shader = LinearGradient(0f, bottomEdge, 0f, height, background.argb(0f), background.argb(EDGE_STRENGTH / 255f), Shader.TileMode.CLAMP)
        paints.edgesFor = topEdge * 10000f + bottomEdge
    }
    canvas.drawRect(0f, 0f, width, topEdge, paints.top)
    canvas.drawRect(0f, bottomEdge, width, height, paints.bottom)

    // The columns on screen and the live width of the gap, right-aligned with the labels' edge.
    val sizes = layoutOf(width, height, m.spread.value, 0f, 0f)
    val readout = "${columnsShown(width, sizes.pitch)} COL · GAP ${(sizes.pitch - sizes.side).roundToInt()} PT"
    paints.label.color = textColor.argb(chrome)
    canvas.drawText(readout, width - 20f - paints.label.measureText(readout), m.top + 26f, paints.label)
}
