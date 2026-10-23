package com.ngocdevv.nativecook.blocks.media.photovortexmorph

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlinx.coroutines.launch

/** One photo of the set; `shape` is how the original is framed, so it is fetched wide enough to crop. */
data class VortexPhoto(val id: String, val shape: Shape) {
    enum class Shape { Portrait, Square, Landscape }

    // A landscape original loses two thirds of its width to a portrait card, so it is fetched wider.
    val uri: String
        get() {
            val width = when (shape) {
                Shape.Portrait -> 420
                Shape.Square -> 480
                Shape.Landscape -> 720
            }
            return "https://images.unsplash.com/$id?auto=format&fit=crop&w=$width&q=80"
        }
}

private fun p(id: String) = VortexPhoto("photo-$id", VortexPhoto.Shape.Portrait)
private fun sq(id: String) = VortexPhoto("photo-$id", VortexPhoto.Shape.Square)
private fun ls(id: String) = VortexPhoto("photo-$id", VortexPhoto.Shape.Landscape)

// Fashion, film stills, galleries and rooms, dealt so neighbours differ. The last tops the stack.
val VortexPhotos: List<VortexPhoto> = listOf(
    ls("1515634928627-2a4e0dae3ddf"), p("1613915617430-8ab0fd7c6baf"), ls("1606819717115-9159c900370b"),
    ls("1618221195710-dd6b41faaea6"), ls("1577190651915-bf62d54d5b36"), p("1549298222-1c31e8915347"),
    ls("1518998053901-5348d3961a04"), p("1618220179428-22790b461013"), ls("1632187981988-40f3cbaeef5e"),
    p("1645996830739-8fe3df27c33f"), ls("1569783721854-33a99b4c0bae"), sq("1586023492125-27b2c045efd7"),
    p("1529798856831-427dfd0a1ab1"), p("1541130292430-a832637ddc0d"), ls("1554907984-15263bfd63bd"),
    ls("1664711942326-2c3351e215e6"), ls("1723723467478-eb4667116137"), sq("1553544260-f87e671974ee"),
    sq("1582555172866-f73bb12a2ab3"), p("1606744837616-56c9a5c6a6eb"), ls("1585951237318-9ea5e175b891"),
    ls("1538329972958-465d6d2144ed"), p("1565876427310-0695a4ff03b7"), sq("1616046229478-9901c5536a45"),
    ls("1486693128850-a77436e7ba3c"), ls("1545989253-02cc26577f88"), p("1583847268964-b28dc8f51f92"),
    ls("1585951237313-1979e4df7385"), p("1522878308970-972ec5eedc0d"), p("1564078516393-cf04bd966897"),
    ls("1532800783378-1bed60adaf58"), ls("1605429523419-d828acb941d9"), p("1599696848652-f0ff23bc911f"),
    ls("1496680392913-a0417ec1a0ad"), ls("1507643179773-3e975d7ac515"), ls("1606744888344-493238951221"),
    ls("1596972812018-6385b8bd3e13"), p("1535385793343-27dff1413c5a"), p("1696332415657-9fbe802a8875"),
    p("1580478491436-fd6a937acc9e"),
)

// The five layouts, in the order a tap walks through them.
private const val VORTEX = 0
private const val DRIFT = 1
private const val STACK = 2
private const val STRIP = 3
private const val GRID = 4
private const val LAYOUTS = 5
private val Names = arrayOf("Vortex", "Drift", "Stack", "Strip", "Grid")
private val Lines = arrayOf("Forty frames in orbit", "Scattered through depth", "Gathered into one", "Fanned along a line", "Laid edge to edge")

private val Paper = Color(0xFFF3F2EE)
private val CardTone = Color(0xFFDCD9D2)

// Vortex: cards on a ring RING_RADIUS·width from the middle, each turned TWIST rad past its spoke
// so they overlap like petals. It turns by itself at one full turn per LOOP; a drag turns it with
// the finger and a flick keeps it spinning, the extra speed fading by SPIN_DECAY per second.
private const val RING_RADIUS = 0.25f
private const val RING_CARD_W = 0.27f
private const val RING_CARD_H = 0.355f
private const val TWIST = -0.5f
private const val SPIN_DECAY = 1.4f
private const val MAX_SPIN = 14f
// A fast spin opens the ring by up to RING_OPEN of its radius.
private const val RING_OPEN = 0.16f

// Drift: the cards float in a box DRIFT_X by DRIFT_Y (shares of the screen) and DRIFT_Z deep
// (shares of the width), seen from CAMERA widths away. The box turns about the vertical axis at
// DRIFT_TURN rad/s; a drag turns it (DRIFT_DRAG rad per dp) and tips it (DRIFT_TIP rad per dp).
private const val DRIFT_X = 0.7f
private const val DRIFT_Y = 0.42f
private const val DRIFT_Z = 0.7f
private const val CAMERA = 1.6f
private const val DRIFT_CARD_W = 0.135f
private const val DRIFT_CARD_H = 0.175f
private const val DRIFT_TURN = 0.11f
private const val DRIFT_DRAG = 0.005f
private const val DRIFT_TIP = 0.0035f
private const val DRIFT_TIP_MAX = 0.45f
private const val DRIFT_DECAY = 1.8f
// Each card bobs BOB dp, three times per LOOP, so a recording of one loop repeats exactly.
private const val BOB = 5f

// Stack: one pile in the middle, the cards under the top one off by up to STACK_JITTER dp and
// STACK_TILT rad. A drag pulls the pile, which follows with resistance and springs back.
private const val STACK_W = 0.42f
private const val STACK_H = 0.56f
private const val STACK_JITTER = 4f
private const val STACK_TILT = 0.05f

// Strip: the cards side by side, each showing a sliver STRIP_STEP·width wide, one open in full.
// A drag slides which card is open, STRIP_DRAG dp per card.
private const val STRIP_W = 0.34f
private const val STRIP_H = 0.45f
private const val STRIP_STEP = 0.012f
private const val STRIP_DRAG = 9f

// Grid: GRID_COLS by GRID_ROWS tiles over the whole screen, GRID_GAP dp apart.
private const val GRID_COLS = 5
private const val GRID_ROWS = 8
private const val GRID_GAP = 2f

// Each card travels on its own spring: stiffness STIFFNESS times 0.8 to 1.2, damping ratio DAMPING.
// On the way it is spun TWIRL rad/s and tipped TIP rad/s, so the cards tumble a little.
private const val STIFFNESS = 150f
private const val DAMPING = 0.74f
private const val TWIRL = 2.6f
private const val TIP = 1.6f
private const val SUBSTEP = 1f / 120f
// A drag on the pile or the grid follows the finger with this resistance, never past PULL_MAX dp.
private const val PULL = 0.55f
private const val PULL_MAX = 140f
private const val RELEASE_MS = 650f
private const val RELEASE_RATIO = 0.62f
private const val SNAP_MS = 520f
private const val SNAP_RATIO = 0.9f

// autoPlay: the vortex turns for 2.4 s, then the layouts follow at these times (s) and the loop
// starts over every LOOP s, back on a settled vortex. The strip slides open card PEEK_CARD and back.
private const val LOOP = 14.4f
private val ScriptAt = floatArrayOf(2.4f, 5.0f, 7.0f, 9.4f, 11.8f)
private val ScriptTo = intArrayOf(DRIFT, STACK, STRIP, GRID, VORTEX)
private const val PEEK_AT = 7.9f
private const val PEEK_BACK = 8.6f
private const val PEEK_CARD = 27f
// Where the loop stands just after each layout arrives, for picking it up after a touch.
private val ScriptStart = floatArrayOf(0f, 2.4f, 5.0f, 7.0f, 9.4f)
private const val USER_HOLD = 3f
// A tap is a touch lifted within this long without travelling PAN_SLOP dp.
private const val TAP_MS = 350L
private const val PAN_SLOP = 6f

// Captions: the new one rises CAPTION_RISE dp into place over CAPTION_IN s.
private const val CAPTION_IN = 0.45f
private const val CAPTION_RISE = 10f
private const val IMAGE_FADE = 0.35f
private const val TRACKING = 1.2f

// A card's pose: centre, size, turn in the plane, tip about the vertical and horizontal axes, and
// its place in the drawing order. Positions and velocities are kept side by side.
private const val X = 0
private const val Y = 1
private const val W = 2
private const val H = 3
private const val ROLL = 4
private const val YAW = 5
private const val PITCH = 6
private const val LAYER = 7
private const val K = 8

private class Loaded(val image: Bitmap, val at: Float)

/** Where each card floats in the drift, in shares of the box, and how it is tipped there. */
private class Seed(
    val x: Float, val y: Float, val z: Float,
    val yaw: Float, val pitch: Float, val roll: Float,
    val stiff: Float, val spin: Float, val phase: Float,
)

/** The same small seeded generator as the React Native version (mulberry32), so the drift matches. */
private class Mulberry(seed: Int) {
    private var s = seed

    fun next(): Float {
        s += 0x6d2b79f5
        var t = s
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0).toFloat()
    }
}

// The React Native version shuffles the drift's cells with a random sort; its result, and the
// draws the sort spent, are kept here so every stack scatters the cards the same way.
private val Shuffled = intArrayOf(
    27, 31, 7, 14, 26, 39, 29, 2, 21, 37, 25, 22, 28, 33, 16, 3, 36, 19, 8, 24,
    10, 13, 4, 0, 6, 11, 17, 23, 9, 34, 32, 1, 12, 18, 5, 35, 38, 15, 20, 30,
)
private const val SHUFFLE_DRAWS = 170

/** The drift is dealt on a jittered 5 by 8 grid, so the cards spread evenly but not in rows. */
private fun sow(count: Int): List<Seed> {
    val r = Mulberry(23)
    val order = if (count == Shuffled.size) {
        repeat(SHUFFLE_DRAWS) { r.next() }
        Shuffled.toList()
    } else {
        (0 until count).sortedBy { r.next() }
    }
    return List(count) { i ->
        val cell = order[i]
        val col = cell % GRID_COLS
        val row = (cell / GRID_COLS) % GRID_ROWS
        val x = ((col + 0.15f + r.next() * 0.7f) / GRID_COLS) * 2f - 1f
        val y = ((row + 0.15f + r.next() * 0.7f) / GRID_ROWS) * 2f - 1f
        val z = r.next() * 2f - 1f
        val yaw = (r.next() * 2f - 1f) * 0.55f
        val pitch = (r.next() * 2f - 1f) * 0.35f
        val roll = (r.next() * 2f - 1f) * 0.3f
        val stiff = 0.8f + r.next() * 0.4f
        val spin = if (r.next() < 0.5f) -1f else 1f
        val phase = r.next() * PI.toFloat() * 2f
        Seed(x, y, z, yaw, pitch, roll, stiff, spin, phase)
    }
}

private fun clamp(value: Float, lo: Float, hi: Float) = min(hi, max(lo, value))

/** Resistance past an edge: the further it is pulled, the less it gives, never past `max`. */
private fun rubber(offset: Float, max: Float): Float {
    val sign = if (offset < 0f) -1f else 1f
    val d = abs(offset) * PULL
    return sign * max * (1f - 1f / (d / max + 1f))
}

private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

private fun wrapAngle(a: Float): Float {
    val t = PI.toFloat() * 2f
    return a - t * floor((a + PI.toFloat()) / t)
}

/** Everything a layout's poses depend on besides the card. */
private class Scene {
    var width = 0f
    var height = 0f
    var spin = 0f
    var spinSpeed = 0f
    var turn = 0f
    var tip = 0f
    var pullX = 0f
    var pullY = 0f
    var open = 0f
    var bob = 0f
}

/** Writes card `i`'s pose in `layout` into `out`. */
private fun pose(layout: Int, i: Int, n: Int, seed: Seed, s: Scene, out: FloatArray) {
    val cx = s.width / 2f
    val cy = s.height / 2f
    val sw = s.width
    when (layout) {
        VORTEX -> {
            val open = 1f + RING_OPEN * tanh(abs(s.spinSpeed) / 6f)
            val a = i.toFloat() / n * PI.toFloat() * 2f + s.spin
            out[X] = cx + cos(a) * RING_RADIUS * sw * open
            out[Y] = cy + sin(a) * RING_RADIUS * sw * open
            out[W] = RING_CARD_W * sw
            out[H] = RING_CARD_H * sw
            out[ROLL] = a + PI.toFloat() / 2f + TWIST
            out[YAW] = 0f
            out[PITCH] = 0f
            out[LAYER] = i.toFloat()
        }
        DRIFT -> {
            // The box turned and tipped, then seen through the lens.
            val bx = seed.x * DRIFT_X * sw
            val by = seed.y * DRIFT_Y * s.height + sin(s.bob + seed.phase) * BOB
            val bz = seed.z * DRIFT_Z * sw
            val cT = cos(s.turn)
            val sT = sin(s.turn)
            val x1 = bx * cT + bz * sT
            val z1 = -bx * sT + bz * cT
            val cP = cos(s.tip)
            val sP = sin(s.tip)
            val y2 = by * cP - z1 * sP
            val z2 = by * sP + z1 * cP
            val d = CAMERA * sw
            val k = d / max(d + z2, d * 0.3f)
            out[X] = cx + x1 * k
            out[Y] = cy + y2 * k
            out[W] = DRIFT_CARD_W * sw * k
            out[H] = DRIFT_CARD_H * sw * k
            out[ROLL] = seed.roll
            out[YAW] = seed.yaw + s.turn * 0.8f
            out[PITCH] = seed.pitch - s.tip * 0.8f
            out[LAYER] = -z2
        }
        STACK -> {
            val top = i == n - 1
            out[X] = cx + s.pullX + if (top) 0f else sin(seed.phase) * STACK_JITTER
            out[Y] = cy + s.pullY + if (top) 0f else cos(seed.phase * 1.3f) * STACK_JITTER
            out[W] = STACK_W * sw
            out[H] = STACK_H * sw
            out[ROLL] = if (top) 0f else seed.roll * (STACK_TILT / 0.3f)
            out[YAW] = 0f
            out[PITCH] = 0f
            out[LAYER] = i.toFloat()
        }
        STRIP -> {
            // Each card lies over the one before, showing only a sliver of it; the cards after the
            // open one move right by a card's width, so it shows in full. The last card always does.
            val step = STRIP_STEP * sw
            val w = STRIP_W * sw
            val gap = (w - step) * clamp(n - 1 - s.open, 0f, 1f)
            val total = (n - 1) * step + w + gap
            val left = cx - total / 2f + s.pullX
            out[X] = left + i * step + (w - step) * clamp(i - s.open, 0f, 1f) + w / 2f
            out[Y] = cy + s.pullY
            out[W] = w
            out[H] = STRIP_H * sw
            out[ROLL] = 0f
            out[YAW] = 0f
            out[PITCH] = 0f
            out[LAYER] = i.toFloat()
        }
        else -> {
            val col = i % GRID_COLS
            val row = i / GRID_COLS
            val tw = (s.width - GRID_GAP * (GRID_COLS - 1)) / GRID_COLS
            val th = (s.height - GRID_GAP * (GRID_ROWS - 1)) / GRID_ROWS
            out[X] = col * (tw + GRID_GAP) + tw / 2f + s.pullX
            out[Y] = row * (th + GRID_GAP) + th / 2f + s.pullY
            out[W] = tw
            out[H] = th
            out[ROLL] = 0f
            out[YAW] = 0f
            out[PITCH] = 0f
            out[LAYER] = i.toFloat()
        }
    }
}

/** How long card `i` waits before leaving for `layout`, in seconds, so the cards go in a wave. */
private fun delayFor(layout: Int, i: Int, n: Int, s: Scene, state: FloatArray): Float = when (layout) {
    DRIFT -> i.toFloat() / n * 0.45f
    STACK -> {
        val dx = state[i * K + X] - s.width / 2f
        val dy = state[i * K + Y] - s.height / 2f
        min(0.3f, hypot(dx, dy) / s.height * 0.5f)
    }
    STRIP -> (n - 1 - i).toFloat() / n * 0.28f
    GRID -> ((i % GRID_COLS) + i / GRID_COLS) * 0.032f
    else -> i.toFloat() / n * 0.32f
}

/**
 * A value driven per frame the way Reanimated drives `withSpring({ duration, dampingRatio })` and
 * `withTiming`, so the pulls, the tip and the strip move on the same curves as in the React Native version.
 */
private class DrivenValue(initial: Float = 0f) {
    var value = initial
        private set
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

    val moving get() = mode != 0

    /** Puts the value somewhere at once, stopping whatever was moving it. */
    fun set(next: Float) {
        value = next
        mode = 0
        velocity = 0f
    }

    fun springTo(target: Float, speed: Float, durationMs: Float, dampingRatio: Float) {
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
        initialEnergy = energy(x0, v)
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
                val p = clamp(time / duration, 0f, 1f)
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

/** Everything the frame loop, the gestures and the picture share. */
private class VortexMotion(val count: Int) {
    // Bumped every frame so the picture is drawn again.
    var tick by mutableFloatStateOf(0f)
    // Seconds since the block appeared, and the poses and velocities of every card.
    var elapsed = 0f
    val seeds = sow(count)
    val state = FloatArray(count * K * 2)
    var fresh = true
    // Whether each card has left for the current layout yet; empty until the first change.
    var gone: BooleanArray? = null
    val loaded = arrayOfNulls<Loaded>(count)
    var layout = VORTEX
    var previous = VORTEX
    var changedAt = 0f
    // Scene values the layouts read.
    val scene = Scene()
    var turnSpeed = 0f
    val tip = DrivenValue()
    val pullX = DrivenValue()
    val pullY = DrivenValue()
    val open = DrivenValue((count - 1).toFloat())
    // Gesture bookkeeping.
    var dragging = false
    var lastAngle = 0f
    var dragFrom = 0f
    var tipFrom = 0f
    // The vortex is drawn twice, with the overlap seam on opposite sides, once it has settled.
    var settled = true
    // Under reduced motion the cards fade out, jump and fade in.
    val fade = DrivenValue(1f)
    var fadingTo = -1
    var jump = -1
    // The self-playing loop.
    var clock = 0f
    var looping = false
    var hold = 0f
    // The screen, in dp, and the safe area at its top and bottom.
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f
    val target = FloatArray(K)
}

/**
 * Forty photos on a sheet of paper that morph between five layouts, each card travelling on its
 * own spring, a little after its neighbour and tumbling on the way: a spinning vortex, a drift
 * through depth, a single stack, a strip of slivers and a grid edge to edge. A tap moves on to the
 * next layout; a drag spins the vortex, turns the drift, pulls the stack or the grid, and slides
 * the strip open card by card.
 *
 * @param photos The photos, forty of them for the 5 by 8 grid; the last one tops the stack.
 * @param autoPlay Cycles vortex, drift, stack, strip and grid by itself, with holds, every 14.4 s.
 * @param background The paper the photos lie on.
 * @param onLayoutChange Called with the layout's index whenever a tap moves to the next one.
 */
@Composable
fun PhotoVortexMorph(
    modifier: Modifier = Modifier,
    photos: List<VortexPhoto> = VortexPhotos,
    autoPlay: Boolean = false,
    background: Color = Paper,
    onLayoutChange: ((Int) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val n = photos.size
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val changing by rememberUpdatedState(onLayoutChange)
    val motion = remember(photos) { VortexMotion(n) }
    val paints = remember { VortexPaints() }

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

    // Each photo arrives on its own and fades in; until then its card shows the card tone.
    LaunchedEffect(motion) {
        val loader = SingletonImageLoader.get(context)
        photos.forEachIndexed { i, photo ->
            launch {
                val request = ImageRequest.Builder(context)
                    .data(photo.uri)
                    .size(Size.ORIGINAL)
                    // A software bitmap can carry mipmaps, which keep the small cards from shimmering.
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                val image = result.image.toBitmap().also { it.setHasMipMap(true) }
                motion.loaded[i] = Loaded(image, motion.elapsed)
            }
        }
    }

    /** Moves on to `to`: every card waits its turn in the wave, then leaves with a little tumble. */
    fun goTo(to: Int, user: Boolean) {
        val m = motion
        if (user) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        changing?.invoke(to)
        if (reducedMotion) {
            m.fade.timeTo(0f, 160f, EaseOutStrong::transform)
            m.fadingTo = to
            return
        }
        m.previous = m.layout
        m.layout = to
        m.changedAt = m.elapsed
        m.gone = BooleanArray(n)
        // Each layout starts from rest: the drift square on, the stack and grid unpulled, the strip on its last card.
        m.pullX.set(0f)
        m.pullY.set(0f)
        if (to == DRIFT) {
            m.scene.turn = -0.2f
            m.turnSpeed = 0f
            m.tip.set(0f)
        }
        if (to == STRIP) m.open.set((n - 1).toFloat())
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        val m = motion
        val s = m.scene
        while (true) {
            withFrameNanos { frameTime ->
                val dt = if (last < 0) 1f / 60f else min((frameTime - last) / 1e9f, 0.064f)
                last = frameTime
                if (dt <= 0f || m.width == 0f) return@withFrameNanos
                val now = m.elapsed + dt
                m.elapsed = now
                m.tip.step(dt)
                m.pullX.step(dt)
                m.pullY.step(dt)
                m.open.step(dt)
                m.fade.step(dt)
                if (m.fadingTo >= 0 && !m.fade.moving) {
                    // Faded out: jump, then fade back in.
                    m.jump = m.fadingTo
                    m.fadingTo = -1
                    m.fade.timeTo(1f, 220f, EaseOutStrong::transform)
                }

                // The script.
                if (m.hold > 0f) {
                    m.hold -= dt
                    if (m.hold <= 0f) m.clock = ScriptStart[m.layout]
                } else if (m.looping) {
                    val before = m.clock
                    val t = (before + dt) % LOOP
                    m.clock = t
                    fun crossed(at: Float) = (before < at && t >= at) || (t < before && (at > before || at <= t))
                    for (k in ScriptAt.indices) if (crossed(ScriptAt[k])) goTo(ScriptTo[k], false)
                    if (crossed(PEEK_AT) && m.layout == STRIP) m.open.springTo(PEEK_CARD, 0f, SNAP_MS, SNAP_RATIO)
                    if (crossed(PEEK_BACK) && m.layout == STRIP) m.open.springTo((n - 1).toFloat(), 0f, SNAP_MS, SNAP_RATIO)
                }

                // The vortex turns by itself, plus whatever a flick left it; the drift turns slowly.
                if (!m.dragging) {
                    val base = if (reducedMotion) 0f else PI.toFloat() * 2f / LOOP
                    s.spin = wrapAngle(s.spin + (base + s.spinSpeed) * dt)
                    s.spinSpeed *= exp(-SPIN_DECAY * dt)
                    if (m.layout == DRIFT) {
                        s.turn += ((if (reducedMotion) 0f else DRIFT_TURN) + m.turnSpeed) * dt
                        m.turnSpeed *= exp(-DRIFT_DECAY * dt)
                    }
                }

                s.width = m.width
                s.height = m.height
                s.tip = m.tip.value
                s.pullX = m.pullX.value
                s.pullY = m.pullY.value
                s.open = m.open.value
                s.bob = now * 3f / LOOP * PI.toFloat() * 2f
                val current = m.layout
                val from = m.previous
                val since = now - m.changedAt
                val target = m.target
                val list = m.state
                val flags = m.gone
                val reset = m.jump
                var worst = 0f
                for (i in 0 until n) {
                    val o = i * K
                    val v = n * K + o
                    val leaving = since >= delayFor(current, i, n, s, list)
                    // A jump under reduced motion lands on the layout it jumps to.
                    pose(if (reset >= 0) reset else if (leaving) current else from, i, n, m.seeds[i], s, target)
                    if (m.fresh || reset >= 0) {
                        // First frame, or a jump under reduced motion: straight to the pose, at rest.
                        for (c in 0 until K) {
                            list[o + c] = target[c]
                            list[v + c] = 0f
                        }
                        continue
                    }
                    if (leaving && flags != null && !flags[i]) {
                        // Leaving: a little spin and tip, a different way for every card.
                        flags[i] = true
                        val seed = m.seeds[i]
                        list[v + ROLL] += seed.spin * TWIRL * seed.stiff
                        list[v + YAW] += -seed.spin * TIP * (2f - seed.stiff)
                    }
                    // Angles take the short way round.
                    list[o + ROLL] = target[ROLL] + wrapAngle(list[o + ROLL] - target[ROLL])
                    val k = STIFFNESS * m.seeds[i].stiff * (if (current == STACK) 0.55f + 0.6f * i / max(1, n - 1) else 1f)
                    val c = 2f * DAMPING * sqrt(k)
                    var left = dt
                    while (left > 1e-6f) {
                        val h = min(SUBSTEP, left)
                        left -= h
                        for (q in 0 until K) {
                            val a = k * (target[q] - list[o + q]) - c * list[v + q]
                            list[v + q] += a * h
                            list[o + q] += list[v + q] * h
                        }
                    }
                    worst = max(worst, abs(list[o + X] - target[X]) + abs(list[o + Y] - target[Y]))
                }
                m.fresh = false
                if (reset >= 0) {
                    m.layout = reset
                    m.previous = reset
                    m.jump = -1
                }
                m.settled = current == VORTEX && worst < 8f
                m.tick = now
            }
        }
    }

    fun touched() {
        motion.hold = USER_HOLD
    }

    fun panStart(x: Float, y: Float) {
        val m = motion
        touched()
        m.dragging = true
        m.lastAngle = atan2(y - m.height / 2f, x - m.width / 2f)
        m.dragFrom = if (m.layout == STRIP) m.open.value else m.scene.turn
        m.tipFrom = m.tip.value
        m.scene.spinSpeed = 0f
        m.turnSpeed = 0f
    }

    fun panUpdate(x: Float, y: Float, tx: Float, ty: Float) {
        val m = motion
        when (m.layout) {
            VORTEX -> {
                // The ring turns with the finger about its middle.
                val a = atan2(y - m.height / 2f, x - m.width / 2f)
                m.scene.spin += wrapAngle(a - m.lastAngle)
                m.lastAngle = a
            }
            DRIFT -> {
                m.scene.turn = m.dragFrom + tx * DRIFT_DRAG
                m.tip.set(clamp(m.tipFrom - ty * DRIFT_TIP, -DRIFT_TIP_MAX, DRIFT_TIP_MAX))
            }
            STRIP -> {
                // Past either end the strip gives less and less.
                val raw = m.dragFrom - tx / STRIP_DRAG
                val over = if (raw < 0f) raw else if (raw > n - 1) raw - (n - 1) else 0f
                m.open.set(raw - over + rubber(over * STRIP_DRAG, 60f) / STRIP_DRAG)
            }
            else -> {
                m.pullX.set(rubber(tx, PULL_MAX))
                m.pullY.set(rubber(ty, PULL_MAX))
            }
        }
    }

    fun panEnd(x: Float, y: Float, vx: Float, vy: Float) {
        val m = motion
        touched()
        m.dragging = false
        when (m.layout) {
            VORTEX -> {
                // The finger's speed about the middle carries on as spin.
                val rx = x - m.width / 2f
                val ry = y - m.height / 2f
                val r2 = max(rx * rx + ry * ry, 40f * 40f)
                m.scene.spinSpeed = if (reducedMotion) 0f else clamp((rx * vy - ry * vx) / r2, -MAX_SPIN, MAX_SPIN)
            }
            DRIFT -> {
                m.turnSpeed = if (reducedMotion) 0f else vx * DRIFT_DRAG
                m.tip.springTo(0f, -vy * DRIFT_TIP, RELEASE_MS, RELEASE_RATIO)
            }
            STRIP -> {
                // Lands on the card nearest where the throw was heading.
                val v = (-vx / STRIP_DRAG).let { if (it.isNaN()) 0f else it }
                val aim = clamp(m.open.value + v * 0.2f, 0f, max(0, n - 1).toFloat()).let { if (it.isNaN()) 0f else it }.roundToInt().toFloat()
                m.open.springTo(aim, v, SNAP_MS, SNAP_RATIO)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            else -> {
                m.pullX.springTo(0f, vx * 0.3f, RELEASE_MS, RELEASE_RATIO)
                m.pullY.springTo(0f, vy * 0.3f, RELEASE_MS, RELEASE_RATIO)
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics {
                role = Role.Button
                contentDescription = "Photo vortex. Tap to morph the photos into the next layout; drag to spin, turn or slide them"
            }
            .pointerInput(motion, reducedMotion) {
                val d = density.density
                // A pan (after PAN_SLOP dp of travel) races a tap (a touch lifted within TAP_MS).
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)
                    var panning = false
                    val id = down.id
                    try { while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == id } ?: break
                        tracker.addPointerInputChange(change)
                        val x = change.position.x / d
                        val y = change.position.y / d
                        val tx = (change.position.x - down.position.x) / d
                        val ty = (change.position.y - down.position.y) / d
                        if (!change.pressed) {
                            if (panning) {
                                val velocity = tracker.calculateVelocity()
                                panEnd(x, y, velocity.x / d, velocity.y / d)
                            } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS && motion.width > 0f) {
                                touched()
                                goTo((motion.layout + 1) % LAYOUTS, true)
                            }
                            event.changes.forEach(PointerInputChange::consume)
                            break
                        }
                        if (!panning && hypot(tx, ty) >= PAN_SLOP) {
                            panning = true
                            panStart(x, y)
                        }
                        if (panning) {
                            change.consume()
                            panUpdate(x, y, tx, ty)
                        }
                    } } finally {
                        // A drag that was cancelled or lost its pointer never reached panEnd; let the vortex turn again.
                        if (panning) motion.dragging = false
                    }
                }
            },
    ) {
        motion.width = maxWidth.value
        motion.height = maxHeight.value
        Canvas(Modifier.fillMaxSize()) {
            motion.tick
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { drawScene(it.nativeCanvas, motion, paints, background.toArgb(), reducedMotion) }
            }
        }
    }
}

private fun sansSerif(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

/** The paints, fonts and scratch objects the picture reuses every frame. */
private class VortexPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val matrix = Matrix()
    val values = FloatArray(9)
    val src = Rect()
    val dst = RectF()
    val label = ink(11f, 500)
    val title = ink(30f, 500)
    val line = ink(13f, 400)

    // White set to difference, so the labels read dark on the paper and light on photos (API 29 and
    // up); below that they are drawn in the paper's darkest ink instead.
    private fun ink(size: Float, weight: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = sansSerif(weight)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            color = android.graphics.Color.WHITE
            blendMode = BlendMode.DIFFERENCE
        } else {
            color = 0xFF0C0D11.toInt()
        }
    }
}

// Skia's `measureText`, which the React Native version spaces letters by, gives a glyph's bounds
// rather than its advance, and on the iPhone it was made on those bounds stand clear of the ink:
// each letter there takes about two points more than its advance. This keeps the same spacing.
private const val GLYPH_PAD = 2f

private fun advance(paint: Paint, letter: String): Float =
    if (letter != " ") paint.measureText(letter) + GLYPH_PAD else paint.measureText("a a") - paint.measureText("aa")

private fun trackedWidth(paint: Paint, text: String): Float {
    var width = 0f
    for (letter in text) width += advance(paint, letter.toString()) + TRACKING
    return width - TRACKING
}

private fun drawTracked(canvas: android.graphics.Canvas, text: String, x: Float, y: Float, paint: Paint) {
    var at = x
    for (letter in text) {
        val s = letter.toString()
        canvas.drawText(s, at, y, paint)
        at += advance(paint, s) + TRACKING
    }
}

private fun alpha(paint: Paint, value: Float) {
    // roundToInt throws on NaN, so a NaN alpha draws nothing instead.
    paint.alpha = if (value.isNaN()) 0 else (clamp(value, 0f, 1f) * 255f).roundToInt()
}

/** A photo cropped to fill its card, under the card's perspective matrix, faded in once it arrives. */
private fun drawCard(canvas: android.graphics.Canvas, m: VortexMotion, i: Int, paints: VortexPaints, cardAlpha: Float) {
    val s = m.state
    val o = i * K
    val w = s[o + W]
    val h = s[o + H]
    // Written so a NaN size is skipped too: the crop below rounds it with roundToInt, which throws on NaN.
    if (!(w >= 0.5f) || !(h >= 0.5f)) return
    val x = s[o + X]
    val y = s[o + Y]
    val focal = m.width * 2.2f
    val cY = cos(s[o + YAW])
    val sY = sin(s[o + YAW])
    val cP = cos(s[o + PITCH])
    val sP = sin(s[o + PITCH])
    val cR = cos(s[o + ROLL])
    val sR = sin(s[o + ROLL])
    val ax = cY * cR + sY * sP * sR
    val ay = cP * sR
    val az = -sY * cR + cY * sP * sR
    val bx = -cY * sR + sY * sP * cR
    val by = cP * cR
    val bz = sY * sR + cY * sP * cR
    val v = paints.values
    v[0] = ax + x * az / focal
    v[1] = bx + x * bz / focal
    v[2] = x
    v[3] = ay + y * az / focal
    v[4] = by + y * bz / focal
    v[5] = y
    v[6] = az / focal
    v[7] = bz / focal
    v[8] = 1f
    paints.matrix.setValues(v)
    canvas.save()
    canvas.concat(paints.matrix)
    paints.dst.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
    paints.fill.color = CardTone.toArgb()
    alpha(paints.fill, cardAlpha)
    canvas.drawRect(paints.dst, paints.fill)
    val loaded = m.loaded[i]
    if (loaded != null) {
        val image = loaded.image
        val iw = image.width.toFloat()
        val ih = image.height.toFloat()
        val fit = max(w / iw, h / ih)
        val sw = w / fit
        val sh = h / fit
        paints.src.set(((iw - sw) / 2f).roundToInt(), ((ih - sh) / 2f).roundToInt(), ((iw + sw) / 2f).roundToInt(), ((ih + sh) / 2f).roundToInt())
        alpha(paints.art, cardAlpha * clamp((m.elapsed - loaded.at) / IMAGE_FADE, 0f, 1f))
        canvas.drawBitmap(image, paints.src, paints.dst, paints.art)
    }
    canvas.restore()
}

/** The layout's number in the top corner and its name and line at the bottom, `rise` dp low. */
private fun drawCaption(canvas: android.graphics.Canvas, paints: VortexPaints, index: Int, shown: Float, rise: Float, width: Float, top: Float, base: Float) {
    if (shown <= 0.01f) return
    val count = "%02d / %02d".format(index + 1, LAYOUTS)
    alpha(paints.label, shown)
    drawTracked(canvas, count, width - 20f - trackedWidth(paints.label, count), top + rise * 0.5f, paints.label)
    alpha(paints.title, shown)
    canvas.drawText(Names[index], 18f, base - 22f + rise, paints.title)
    alpha(paints.line, shown * 0.62f)
    canvas.drawText(Lines[index], 20f, base + rise, paints.line)
}

private fun drawScene(canvas: android.graphics.Canvas, m: VortexMotion, paints: VortexPaints, background: Int, reducedMotion: Boolean) {
    val width = m.width
    val height = m.height
    val n = m.count
    canvas.drawColor(background)
    if (m.fresh) return
    val cardAlpha = m.fade.value

    if (m.settled) {
        // A ring of overlapping cards has a seam where the last card meets the first. It is drawn
        // twice, the seam on opposite sides, and each copy shows only the half away from its seam.
        val cx = width / 2f
        val cy = height / 2f
        val degrees = m.scene.spin * 180f / PI.toFloat()
        val far = hypot(width, height)
        for (side in intArrayOf(-1, 1)) {
            // The half-plane on one side of the line through the middle across card 0's spoke.
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(degrees)
            canvas.clipRect(if (side < 0) -far else 0f, -far, if (side < 0) 0f else far, far)
            canvas.rotate(-degrees)
            canvas.translate(-cx, -cy)
            val start = if (side < 0) 0 else n / 2
            for (k in 0 until n) drawCard(canvas, m, (start + k) % n, paints, cardAlpha)
            canvas.restore()
        }
    } else {
        val order = (0 until n).sortedBy { m.state[it * K + LAYER] }
        for (i in order) drawCard(canvas, m, i, paints, cardAlpha)
    }

    // The labels, in white set to difference, so they read dark on the paper and light on photos.
    val top = m.top + 22f
    alpha(paints.label, 1f)
    drawTracked(canvas, "KINETIC ARCHIVE", 20f, top, paints.label)
    val fresh = if (reducedMotion) 1f else easeOut(clamp((m.elapsed - m.changedAt) / CAPTION_IN, 0f, 1f))
    val base = height - m.bottom - 26f
    if (m.layout != m.previous && fresh < 1f) {
        drawCaption(canvas, paints, m.previous, 1f - fresh, -fresh * CAPTION_RISE, width, top, base)
    }
    drawCaption(canvas, paints, m.layout, fresh, (1f - fresh) * CAPTION_RISE, width, top, base)
    alpha(paints.label, 0.5f)
    val hint = "TAP TO MORPH"
    drawTracked(canvas, hint, width - 20f - trackedWidth(paints.label, hint), base, paints.label)
}
