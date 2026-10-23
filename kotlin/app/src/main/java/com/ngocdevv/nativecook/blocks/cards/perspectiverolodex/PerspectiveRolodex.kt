package com.ngocdevv.nativecook.blocks.cards.perspectiverolodex

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch

/**
 * @param category One word, shown between "Because" and "Matters" while the card is in front.
 * @param caption A line under the title once the card is open.
 * @param photo Address of a photo, about 1 : 0.93.
 * @param tint Flat colour shown until the photo arrives.
 */
data class RolodexItem(val title: String, val category: String, val caption: String, val photo: String, val tint: Color)

/** How the cards already seen leave: lying back face up towards the viewer, or tipping forward. */
enum class RolodexFall { Back, Forward }

// The camera: its focal length in screen widths, and the height (as a share of the screen) it
// looks along, which is where the middle of the card in front sits.
private const val FOCAL = 2.24f
private const val EYE = 0.42f
// A card is one unit wide; this is its height and the radius of its corners.
private const val ASPECT = 0.93f
private const val RADIUS = 0.06f
// The card in front stands this far from the camera.
private const val FRONT_Z = 4f

// Where a card sits for `d` = its index minus the scroll. 0 is in front; the cards already seen
// (negative d) have come towards the viewer and down. Y is the height of the card's middle (down
// is positive), Z its distance, pitch its lean in degrees, positive tipping the top edge towards
// the viewer. Seen cards either lie back face up, overlapping like cards dealt on a table in front
// of the viewer (Back), or tip forward with their top edges towards the lens (Forward).
private const val NEAR_FROM = -5

private class NearPath(val y: FloatArray, val z: FloatArray, val pitch: FloatArray)

private val NearBack = NearPath(
    y = floatArrayOf(1.4f, 1.3f, 1.16f, 1.0f, 0.74f, 0f),
    z = floatArrayOf(0.95f, 1.4f, 1.9f, 2.45f, 3.25f, FRONT_Z),
    pitch = floatArrayOf(-78f, -75f, -72f, -68f, -62f, -4f),
)
private val NearForward = NearPath(
    y = floatArrayOf(2.6f, 1.85f, 1.36f, 1.04f, 0.82f, 0f),
    z = floatArrayOf(1.15f, 1.55f, 1.98f, 2.32f, 2.62f, FRONT_Z),
    pitch = floatArrayOf(70f, 62f, 52f, 46f, 40f, -4f),
)

private fun nearPath(fall: RolodexFall) = if (fall == RolodexFall.Back) NearBack else NearForward

// Cards already seen darken as they come nearer, up to a limit, and the bottom of the screen
// sinks into the background over BOTTOM_FADE dp so the labels stay clear.
private const val NEAR_SHADE = 0.14f
private const val NEAR_SHADE_MAX = 0.45f
private const val BOTTOM_FADE = 240f
// The cards still to come stand in a line that climbs away behind the one in front, each a step
// higher and further, so only a sliver of each shows above the next.
private const val FAR_Y = -0.13f
private const val FAR_Z = 4.3f
private const val FAR_STEP_Y = -0.19f
private const val FAR_STEP_Z = 0.31f
private const val FAR_PITCH = -7f
// The card falling out of the front stays upright a little longer before it tips: the share of
// the way, raised to this power, that its lean has turned.
private const val TIP_LAG = 1.8f
// Cards further back darken a little, up to a limit.
private const val FAR_SHADE = 0.035f
private const val FAR_SHADE_MAX = 0.42f
// The range of `d` that cards wrap around in: a card leaving the bottom comes back at the far end.
private const val WRAP_FROM = -4.5f
// How far, in cards, the far end of the stack fades in.
private const val FAR_FADE = 1.5f
// A card is dropped once any corner comes this close to the lens.
private const val NEAR_CLIP = 0.2f

// Finger travel that moves the stack by one card, as a share of the card in front's height.
private const val DRAG_STEP = 0.8f
private const val DRAG_SLOP = 6f
// The snap after a release: a spring on the scroll position, in cards.
private const val MASS = 1f
private const val STIFFNESS = 170f
private const val DAMPING = 24f
// Seconds of speed a release throws the stack ahead by.
private const val THROW_TIME = 0.18f

// The self-playing turn: a card comes forward every STEP_EVERY seconds over STEP_TIME.
private const val STEP_EVERY = 1.4f
private const val STEP_TIME = 0.46f
// After a touch the loop waits this long before it starts again.
private const val USER_HOLD = 3f

// An open card: where its middle sits as a share of the screen, and its side margin.
private const val OPEN_AT = 0.4f
private const val OPEN_MARGIN = 20f
private const val OPEN_SPRING_MS = 560f
private const val OPEN_SPRING_RATIO = 0.86f
private const val REDUCED_MS = 200f
// While a card opens the rest of the stack parts: the far cards rise, the near ones drop.
private const val OPEN_RISE = 1.4f
private const val OPEN_DROP = 1.2f
// A drag down by this share of the screen closes an open card all the way.
private const val DISMISS_SHARE = 0.45f
private const val DISMISS_SPEED = 600f
private const val DISMISS_KEEP = 0.65f
private const val OPEN_RUBBER = 0.2f
private const val CAPTION_RISE = 18f
// A tap is a touch lifted within this long without turning into a drag.
private const val TAP_MS = 400L

// The labels: their size, and how far the middle word rolls per card.
private const val LABEL_SIZE = 12f
private const val LINE = 16f
private const val IMAGE_FADE = 0.35f

private fun photo(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=760&h=706&q=82"

private val Items = listOf(
    RolodexItem("Swell", "Ocean", "A wave held at the top of its curl.", photo("photo-1616141893496-fbc65370493e"), Color(0xFF245D6E)),
    RolodexItem("Seat No. 4", "Design", "A black shell chair on white.", photo("photo-1581539250439-c96689b516dd"), Color(0xFFDCDCDC)),
    RolodexItem("Ember", "Colour", "Red light, one face, no shadow.", photo("photo-1727341556787-7292f7515aec"), Color(0xFFC42A1C)),
    RolodexItem("Red Room", "Light", "A neon cube left in the trees.", photo("photo-1629440951880-8c81df77e2d0"), Color(0xFF5A1810)),
    RolodexItem("Open Field", "Space", "One cloud over a green hill.", photo("photo-1648219124133-8e53e8a9577c"), Color(0xFF7DA35A)),
    RolodexItem("Plain Oak", "Craft", "A bentwood chair against yellow.", photo("photo-1640938776314-4d303f8a1380"), Color(0xFFD8B832)),
    RolodexItem("Bloom", "Nature", "Orange gerberas in a white jar.", photo("photo-1604435719097-7f73bfc6304a"), Color(0xFF8AA3A6)),
    RolodexItem("Level −2", "Motion", "A white car under strip lights.", photo("photo-1611760399750-bf3b95ac8f0a"), Color(0xFF3F3F3F)),
    RolodexItem("Signal", "Culture", "A sneaker drawn in pink neon.", photo("photo-1628558422453-576457df4b0d"), Color(0xFF5B2A5C)),
    RolodexItem("Flare", "Fashion", "Red silk against a burning sky.", photo("photo-1767049606807-2fcc71c865f2"), Color(0xFF7C6A6C)),
    RolodexItem("Tulip", "Form", "A white pedestal, a red seat.", photo("photo-1659246973603-748505504837"), Color(0xFFE4E1DC)),
    RolodexItem("Soft", "Beauty", "Skin, close, in morning light.", photo("photo-1643630661247-2474f10e4f70"), Color(0xFFCAA38C)),
    RolodexItem("Noon", "Style", "A black shirt against a yellow wall.", photo("photo-1680967494203-789f70ddcfdf"), Color(0xFFF0C02A)),
    RolodexItem("Midnight", "Detail", "Dutch flowers out of the dark.", photo("photo-1783680599080-abe4d5634c3a"), Color(0xFF2A2118)),
    RolodexItem("High Sky", "Freedom", "Red wool under a blue noon.", photo("photo-1662532577856-e8ee8b138a8b"), Color(0xFF2D55C8)),
    RolodexItem("Loop", "Material", "A ceramic ring in sand tones.", photo("photo-1643569556871-91ec60671ed7"), Color(0xFFD9D0C3)),
    RolodexItem("Navy", "Comfort", "One moulded chair, grey room.", photo("photo-1748887522222-ae67e761fb64"), Color(0xFFC9CCD0)),
    RolodexItem("Scarlet", "Feeling", "A hand to the cheek, red behind.", photo("photo-1727341557531-343cd4364501"), Color(0xFFB5201A)),
    RolodexItem("Arcs", "Balance", "Half circles leaning on a wall.", photo("photo-1620987278429-ab178d6eb547"), Color(0xFFC9A27A)),
    RolodexItem("Cut Out", "Shape", "Black wool on a white page.", photo("photo-1603189343302-e603f7add05a"), Color(0xFFEFEFEF)),
    RolodexItem("Peach", "Warmth", "A tufted armchair, soft light.", photo("photo-1722357533644-9b20e356ba5f"), Color(0xFFE8CDB2)),
    RolodexItem("Eclipse", "Mood", "A profile against blue light.", photo("photo-1691679769924-4430510fbc4f"), Color(0xFF0C1733)),
)

private class Loaded(val image: Bitmap, val at: Float)

private class Shown(
    val index: Int,
    val z: Float,
    val matrix: FloatArray,
    val quad: FloatArray,
    val alpha: Float,
    val shade: Float,
)

private fun clamp01(value: Float) = value.coerceIn(0f, 1f)

/** `x` moved by whole turns of `n` into [from, from + n). */
private fun wrap(x: Float, n: Int, from: Float) = x - n * floor((x - from) / n)

private fun wrapIndex(x: Float, n: Int) = wrap(x, n, 0f).roundToInt().mod(n)

private fun easeInOut(p: Float) = if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).pow(3) / 2f

/** One part (0 y, 1 z, 2 pitch) of the card's place at a whole step `i` of the path. */
private fun key(i: Int, near: NearPath, part: Int): Float {
    if (i <= 0) {
        val k = max(0, i - NEAR_FROM)
        return when (part) {
            0 -> near.y[k]
            1 -> near.z[k]
            else -> near.pitch[k]
        }
    }
    return when (part) {
        0 -> FAR_Y + FAR_STEP_Y * (i - 1)
        1 -> FAR_Z + FAR_STEP_Z * (i - 1)
        else -> FAR_PITCH
    }
}

/** Catmull-Rom through four values, at `t` between the middle two. */
private fun spline(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    return 0.5f * (2f * b + (c - a) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (3f * b - a - 3f * c + d) * t3)
}

/** Where a card sits for `d`, smoothly between the steps of the path, along one part of it. */
private fun placePart(d: Float, near: NearPath, part: Int): Float {
    val i = floor(d).toInt()
    val t = d - i
    return spline(key(i - 1, near, part), key(i, near, part), key(i + 1, near, part), key(i + 2, near, part), t)
}

private fun placePitch(d: Float, near: NearPath): Float {
    if (d > -1f && d < 0f) {
        // Falling out of the front: the lean follows the fall late.
        val s = (-d).pow(TIP_LAG)
        return near.pitch[-1 - NEAR_FROM] * s + near.pitch[-NEAR_FROM] * (1f - s)
    }
    return placePart(d, near, 2)
}

private val Corners = floatArrayOf(-50f, -50f * ASPECT, 50f, -50f * ASPECT, 50f, 50f * ASPECT, -50f, 50f * ASPECT)

/**
 * Every card the camera sees, far to near: the perspective matrix that maps its face (a hundred
 * units wide, about its middle) onto the screen, its corners there, how faded and how dark it is.
 */
private fun frame(count: Int, scroll: Float, open: Float, openIndex: Int, width: Float, height: Float, fall: RolodexFall): List<Shown> {
    val near = nearPath(fall)
    val f = width * FOCAL
    val cx = width / 2f
    val cy = height * EYE
    val shown = ArrayList<Shown>(count)
    val farEnd = WRAP_FROM + count
    for (index in 0 until count) {
        val d = wrap(index - scroll, count, WRAP_FROM)
        var y = placePart(d, near, 0)
        var z = placePart(d, near, 1)
        var pitch = placePitch(d, near)
        var alpha = clamp01((farEnd - d) / FAR_FADE)
        var shade = if (d > 0f) min(FAR_SHADE_MAX, max(0f, d - 0.5f) * FAR_SHADE) else min(NEAR_SHADE_MAX, -d * NEAR_SHADE)
        if (open > 0f) {
            val o = min(open, 1f)
            if (index == openIndex) {
                // Upright, nearly the width of the screen, a little above the middle.
                val openZ = f / (width - OPEN_MARGIN * 2f)
                val openY = ((height * OPEN_AT - cy) * openZ) / f
                y += (openY - y) * open
                z += (openZ - z) * open
                pitch *= 1f - o
                shade *= 1f - o
            } else {
                y += (if (d > 0f) -OPEN_RISE else OPEN_DROP) * o
                alpha *= max(0f, 1f - o * 1.4f)
            }
        }
        if (alpha <= 0.004f) continue
        val rad = pitch * PI.toFloat() / 180f
        val s = sin(rad)
        val c = cos(rad)
        val k = 1f / 100f
        val matrix = floatArrayOf(f * k / z, cx * k * s / z, cx, 0f, (cy * k * s + f * k * c) / z, cy + f * y / z, 0f, k * s / z, 1f)
        val quad = FloatArray(8)
        var behind = false
        for (corner in 0 until 4) {
            val u = Corners[corner * 2]
            val v = Corners[corner * 2 + 1]
            val w = matrix[7] * v + 1f
            if (w * z <= NEAR_CLIP) behind = true
            quad[corner * 2] = (matrix[0] * u + matrix[1] * v + matrix[2]) / w
            quad[corner * 2 + 1] = (matrix[4] * v + matrix[5]) / w
        }
        if (behind) continue
        val top = min(min(quad[1], quad[3]), min(quad[5], quad[7]))
        val bottom = max(max(quad[1], quad[3]), max(quad[5], quad[7]))
        if (bottom < 0f || top > height) continue
        // The open card is drawn last, over everything.
        shown.add(Shown(index, if (index == openIndex && open > 0.01f) 0f else z, matrix, quad, alpha, shade))
    }
    shown.sortByDescending { it.z }
    return shown
}

/** Whether a point lies inside a convex quad given corner by corner. */
private fun inside(quad: FloatArray, x: Float, y: Float): Boolean {
    var sign = 0f
    for (i in 0 until 4) {
        val j = (i + 1) % 4
        val cross = (quad[j * 2] - quad[i * 2]) * (y - quad[i * 2 + 1]) - (quad[j * 2 + 1] - quad[i * 2 + 1]) * (x - quad[i * 2])
        if (cross == 0f) continue
        if (sign == 0f) sign = Math.signum(cross) else if (Math.signum(cross) != sign) return false
    }
    return true
}

/**
 * A value driven per frame the way Reanimated drives `withSpring({ duration, dampingRatio })` and
 * `withTiming`, so the open card moves on the same curve as in the React Native version.
 */
private class DrivenValue {
    // Read by the caption's layer, so it is redrawn as the value moves.
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

    fun timeTo(target: Float, durationMs: Float) {
        mode = 2
        from = value
        to = target
        time = 0f
        duration = durationMs / 1000f
        velocity = 0f
    }

    fun step(dt: Float) {
        when (mode) {
            1 -> {
                val x0 = value - to
                val v0 = velocity
                val t = dt
                if (zeta < 1f) {
                    val sin1 = sin(omega1 * t)
                    val cos1 = cos(omega1 * t)
                    val envelope = exp(-zeta * omega0 * t)
                    val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
                    value = to + frag
                    velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
                } else {
                    val envelope = exp(-omega0 * t)
                    value = to + envelope * (x0 + (v0 + omega0 * x0) * t)
                    velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
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
                value = from + (to - from) * EaseOutStrong.transform(p)
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

/** Everything the frame loop, the gestures and the picture share, kept off Compose state. */
private class RolodexMotion(val count: Int) {
    // `scroll` counts cards that have come forward; it is kept within one turn of the stack.
    var scroll = 0f
    var seen = 0f
    var velocity = 0f
    var target = 0f
    var dragging = false
    var dragFrom = 0f
    // 0 shut, 1 open; the card it applies to.
    val open = DrivenValue()
    var openIndex = -1
    var isOpen = false
    var openFrom = 0f
    // What a drag moves: 0 the stack, 1 the open card.
    var dragMode = 0
    // A finger, not the loop, moved the stack last; only then does it tick.
    var byUser = false
    var clock = 0f
    var looping = false
    var hold = 0f
    // The self-playing step under way: where it started, and how long ago (−1 for none).
    var stepFrom = 0f
    var stepT = -1f
    // Seconds since the block appeared. The picture reads it, so it is drawn again every frame.
    var elapsed by mutableFloatStateOf(0f)
    var lastWhole = 0
    val loaded = arrayOfNulls<Loaded>(count)
    // The screen, in dp, and the safe area at its top and bottom.
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f
}

/**
 * An editorial rolodex of photo cards on a black screen. The card in front stands a little above
 * the middle; the cards to come wait behind it in a tall stack that climbs away to the top of the
 * screen, a sliver of each showing; and the cards already seen lie back towards the viewer, large
 * and leaning, until they leave the bottom edge. Dragging turns the stack and a release snaps a
 * card to the front; the stack never ends. Tapping the card in front stands it up, lifts it out to
 * nearly the full width and shows its title; a drag down puts it back.
 *
 * @param items The cards, in the order they come forward. Sixteen or more keep the far stack reaching the top.
 * @param autoPlay Brings the next card forward every 1.4 s, like a rolodex turned by hand.
 * @param fall How the cards already seen leave the front.
 * @param onOpen Called with the index of a card when it opens.
 */
@Composable
fun PerspectiveRolodex(
    modifier: Modifier = Modifier,
    items: List<RolodexItem> = Items,
    autoPlay: Boolean = false,
    fall: RolodexFall = RolodexFall.Back,
    background: Color = Color.Black,
    textColor: Color = Color(0xFFF2F1EC),
    onOpen: ((Int) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val count = items.size
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val opening by rememberUpdatedState(onOpen)
    // The card whose title is shown while it is open.
    var shown by remember { mutableIntStateOf(0) }
    val motion = remember(items) { RolodexMotion(count) }

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
        motion.stepT = -1f
    }

    // Each photo arrives on its own and fades in; until then its card shows its tint.
    LaunchedEffect(motion, context) {
        val loader = SingletonImageLoader.get(context)
        items.forEachIndexed { i, item ->
            launch {
                val request = ImageRequest.Builder(context)
                    .data(item.photo)
                    .size(Size.ORIGINAL)
                    // A software bitmap can carry mipmaps, which keep the far, small cards from shimmering.
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                val bitmap = result.image.toBitmap()
                bitmap.setHasMipMap(true)
                motion.loaded[i] = Loaded(bitmap, motion.elapsed)
            }
        }
    }

    fun opened(index: Int) {
        shown = index
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        opening?.invoke(index)
    }

    fun settleOpen(to: Float, speed: Float) {
        motion.isOpen = to == 1f
        if (reducedMotion) motion.open.timeTo(to, REDUCED_MS) else motion.open.springTo(to, speed, OPEN_SPRING_MS, OPEN_SPRING_RATIO)
    }

    fun openFront(front: Float) {
        motion.target = front
        motion.openIndex = wrapIndex(front, count)
        settleOpen(1f, 0f)
        opened(wrapIndex(front, count))
    }

    fun touched() {
        motion.hold = USER_HOLD
        motion.clock = 0f
        motion.byUser = true
        if (motion.stepT >= 0f) {
            // A step under way hands its motion to the spring and lands where it was going.
            motion.stepT = -1f
            motion.target = motion.stepFrom + 1f
        }
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) advance(motion, dt, reducedMotion)
                // A selection tick each time another card reaches the front under the user's hand.
                val whole = motion.scroll.roundToInt()
                if (whole != motion.lastWhole && motion.byUser && motion.open.value < 0.5f) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                motion.lastWhole = whole
            }
        }
    }

    val paints = remember { RolodexPaints() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Rolodex of $count photo cards. Swipe down to bring the next card forward; tap the front card to open it."
            }
            .pointerInput(motion, fall, reducedMotion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    var panning = false
                    var released = false
                    var lastY = 0f
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val dx = (change.position.x - start.x) / density.density
                        val dy = (change.position.y - start.y) / density.density
                        if (!change.pressed) {
                            if (panning) {
                                change.consume()
                                val velocityY = tracker.calculateVelocity().y / density.density
                                panEnd(motion, velocityY, ::settleOpen)
                            } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                touched()
                                tapAt(motion, change.position.x / density.density, change.position.y / density.density, fall, ::settleOpen, ::openFront)
                            }
                            released = true
                            continue
                        }
                        if (!panning && sqrt(dx * dx + dy * dy) >= DRAG_SLOP) {
                            panning = true
                            touched()
                            if (motion.isOpen || motion.open.value > 0.5f) {
                                motion.dragMode = 1
                                motion.openFrom = motion.open.value
                            } else {
                                motion.dragMode = 0
                                motion.dragFrom = motion.scroll
                                motion.seen = motion.scroll
                                motion.dragging = true
                            }
                        }
                        if (panning) {
                            change.consume()
                            if (dy != lastY) panUpdate(motion, dy)
                            lastY = dy
                        }
                    }
                    // A finger lost to the system still lets go of the stack.
                    if (panning && !released) panEnd(motion, 0f, ::settleOpen)
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        motion.width = width
        motion.height = height
        val openHeight = (width - OPEN_MARGIN * 2f) * ASPECT

        Canvas(Modifier.fillMaxSize()) {
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { drawRolodex(it.nativeCanvas, motion, items, fall, paints, background, textColor) }
            }
        }

        val item = items.getOrElse(shown) { items[0] }
        Column(
            Modifier
                .offset(y = (height * OPEN_AT + openHeight / 2f + 22f).dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    val o = motion.open.value
                    alpha = clamp01((o - 0.55f) / 0.45f)
                    translationY = ((1f - min(o, 1f)) * CAPTION_RISE).dp.toPx()
                },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(
                "${(shown + 1).toString().padStart(2, '0')} — ${item.category}".uppercase(),
                style = TextStyle(color = textColor.copy(alpha = 0.6f), fontSize = 12.sp, letterSpacing = 0.4.sp, fontFamily = FontFamily.SansSerif),
            )
            BasicText(
                item.title,
                style = TextStyle(color = textColor, fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.6).sp, fontFamily = FontFamily.SansSerif),
            )
            BasicText(
                item.caption,
                style = TextStyle(color = textColor.copy(alpha = 0.7f), fontSize = 15.sp, lineHeight = 21.sp, fontFamily = FontFamily.SansSerif),
            )
        }
    }
}

/** One frame of the stack: the drag, the self-playing step or the snapping spring, and the open card. */
private fun advance(m: RolodexMotion, dt: Float, reducedMotion: Boolean) {
    m.elapsed += dt
    m.open.step(dt)
    if (m.dragging) {
        m.velocity += ((m.scroll - m.seen) / dt - m.velocity) * 0.5f
        m.seen = m.scroll
        return
    }
    if (m.hold > 0f) {
        // An open card keeps the loop waiting until it is put back.
        if (!m.isOpen) m.hold -= dt
        if (m.hold <= 0f) m.clock = 0f
    } else if (m.looping && m.stepT < 0f) {
        m.clock += dt
        if (m.clock >= STEP_EVERY) {
            m.clock = 0f
            m.byUser = false
            m.stepFrom = m.target.roundToInt().toFloat()
            m.stepT = 0f
        }
    }
    if (m.stepT >= 0f) {
        val t = m.stepT + dt
        val p = min(1f, t / STEP_TIME)
        val next = m.stepFrom + easeInOut(p)
        m.velocity = (next - m.scroll) / dt
        m.scroll = next
        if (p >= 1f) {
            m.stepT = -1f
            m.target = m.stepFrom + 1f
            m.velocity = 0f
        } else {
            m.stepT = t
        }
    } else if (reducedMotion) {
        m.scroll = m.target
        m.velocity = 0f
    } else {
        val v = m.velocity + ((STIFFNESS * (m.target - m.scroll) - DAMPING * m.velocity) / MASS) * dt
        m.velocity = v
        m.scroll += v * dt
    }
    // Whole turns of the stack change nothing on screen, so the numbers are kept small.
    if (m.stepT < 0f) {
        val turns = floor(m.target / m.count) * m.count
        if (turns != 0f) {
            m.scroll -= turns
            m.target -= turns
        }
    }
    m.seen = m.scroll
}

private fun panUpdate(m: RolodexMotion, translationY: Float) {
    if (m.dragMode == 1) {
        val dismiss = max(1f, m.height * DISMISS_SHARE)
        val p = m.openFrom - translationY / dismiss
        m.open.set(if (p > 1f) 1f + (p - 1f) * OPEN_RUBBER else max(0f, p))
        return
    }
    val frontHeight = m.width * FOCAL / FRONT_Z * ASPECT
    val step = max(1f, frontHeight * DRAG_STEP)
    m.scroll = m.dragFrom + translationY / step
}

private fun panEnd(m: RolodexMotion, velocityY: Float, settleOpen: (Float, Float) -> Unit) {
    if (m.dragMode == 1) {
        val dismiss = max(1f, m.height * DISMISS_SHARE)
        val shut = velocityY > DISMISS_SPEED || m.open.value < DISMISS_KEEP
        settleOpen(if (shut) 0f else 1f, -velocityY / dismiss)
        return
    }
    m.dragging = false
    m.target = (m.scroll + m.velocity * THROW_TIME).roundToInt().toFloat()
}

private fun tapAt(
    m: RolodexMotion,
    x: Float,
    y: Float,
    fall: RolodexFall,
    settleOpen: (Float, Float) -> Unit,
    openFront: (Float) -> Unit,
) {
    if (m.isOpen) {
        settleOpen(0f, 0f)
        return
    }
    // The card under the finger: the nearest one whose outline holds the point.
    val cards = frame(m.count, m.scroll, 0f, -1, m.width, m.height, fall)
    for (i in cards.indices.reversed()) {
        if (!inside(cards[i].quad, x, y)) continue
        val d = wrap(cards[i].index - m.scroll, m.count, WRAP_FROM)
        // The card that was tapped, even if the stack was still turning under the finger.
        val front = (m.scroll + d).roundToInt().toFloat()
        if (abs(d) < 0.5f) openFront(front) else m.target = front
        return
    }
}

private class RolodexPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = LABEL_SIZE
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SANS_SERIF, 300, false)
        } else {
            Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
    }
    val sink = Paint()
    var sinkFor = Float.NaN
    val cardPath = Path().apply {
        val w = 100f
        val h = 100f * ASPECT
        addRoundRect(-w / 2f, -h / 2f, w / 2f, h / 2f, RADIUS * 100f, RADIUS * 100f, Path.Direction.CW)
    }
    val matrix = Matrix()
    val imageMatrix = Matrix()
    val values = FloatArray(9)
}

private fun Color.withAlpha(alpha: Float) = copy(alpha = alpha).toArgb()

private fun drawRolodex(
    canvas: android.graphics.Canvas,
    m: RolodexMotion,
    items: List<RolodexItem>,
    fall: RolodexFall,
    paints: RolodexPaints,
    background: Color,
    textColor: Color,
) {
    val width = m.width
    val height = m.height
    if (width <= 0f) return
    val now = m.elapsed
    val at = m.scroll
    val o = m.open.value

    paints.fill.color = background.toArgb()
    canvas.drawRect(0f, 0f, width, height, paints.fill)

    val chrome = 1f - 0.6f * clamp01(o)
    val ink = paints.ink
    ink.color = textColor.withAlpha(chrome)
    val topLine = m.top + 14f
    val bottomLine = height - m.bottom - 10f
    // The middle label at the top sits behind the stack, which climbs past it.
    drawLabel(canvas, "Content", width / 2f, topLine, 0.5f, ink)

    for (card in frame(m.count, at, o, m.openIndex, width, height, fall)) {
        val q = card.quad
        if (card.alpha < 0.995f) {
            val left = min(min(q[0], q[2]), min(q[4], q[6]))
            val top = min(min(q[1], q[3]), min(q[5], q[7]))
            val right = max(max(q[0], q[2]), max(q[4], q[6]))
            val bottom = max(max(q[1], q[3]), max(q[5], q[7]))
            canvas.saveLayerAlpha(left - 2f, top - 2f, right + 2f, bottom + 2f, (card.alpha * 255f).roundToInt())
        } else {
            canvas.save()
        }
        paints.matrix.setValues(card.matrix)
        canvas.concat(paints.matrix)
        drawCard(canvas, items[card.index], m.loaded[card.index], card.shade, now, paints)
        canvas.restore()
    }

    // The near cards sink into the background towards the bottom edge.
    if (paints.sinkFor != height) {
        paints.sink.shader = LinearGradient(
            0f, height - BOTTOM_FADE, 0f, height,
            background.withAlpha(0f), background.withAlpha(0xF2 / 255f),
            Shader.TileMode.CLAMP,
        )
        paints.sinkFor = height
    }
    canvas.drawRect(0f, height - BOTTOM_FADE, width, height, paints.sink)

    drawLabel(canvas, "Selected", OPEN_MARGIN, topLine, 0f, ink)
    drawLabel(canvas, "For You", width - OPEN_MARGIN, topLine, 1f, ink)
    drawLabel(canvas, "Because", OPEN_MARGIN, bottomLine, 0f, ink)
    drawLabel(canvas, "Matters", width - OPEN_MARGIN, bottomLine, 1f, ink)
    // The middle word rolls up a line per card: the front card's category.
    canvas.save()
    canvas.clipRect(0f, bottomLine - LINE, width, bottomLine + 5f)
    val front = at.roundToInt()
    for (k in front - 1..front + 1) {
        val d = k - at
        val fade = clamp01(1f - abs(d) / 0.7f)
        if (fade <= 0f) continue
        ink.color = textColor.withAlpha(fade * chrome)
        drawLabel(canvas, items[wrapIndex(k.toFloat(), m.count)].category, width / 2f, bottomLine + d * LINE, 0.5f, ink)
    }
    canvas.restore()
}

/** The card's face: its tint, then its photo cropped to fill once it arrives, then its shade. */
private fun drawCard(canvas: android.graphics.Canvas, item: RolodexItem, loaded: Loaded?, shade: Float, now: Float, paints: RolodexPaints) {
    val w = 100f
    val h = 100f * ASPECT
    canvas.save()
    canvas.clipPath(paints.cardPath)
    paints.fill.color = item.tint.toArgb()
    canvas.drawRect(-w / 2f, -h / 2f, w / 2f, h / 2f, paints.fill)
    if (loaded != null) {
        val image = loaded.image
        val iw = image.width.toFloat()
        val ih = image.height.toFloat()
        val fit = max(w / iw, h / ih)
        paints.imageMatrix.setScale(fit, fit)
        paints.imageMatrix.postTranslate(-iw * fit / 2f, -ih * fit / 2f)
        paints.art.alpha = (clamp01((now - loaded.at) / IMAGE_FADE) * 255f).roundToInt()
        canvas.drawBitmap(image, paints.imageMatrix, paints.art)
    }
    if (shade > 0.004f) {
        paints.fill.color = Color.Black.withAlpha(shade)
        canvas.drawRect(-w / 2f, -h / 2f, w / 2f, h / 2f, paints.fill)
    }
    canvas.restore()
}

/** Text at `x`, set to its left (align 0), middle (0.5) or right (1). */
private fun drawLabel(canvas: android.graphics.Canvas, text: String, x: Float, y: Float, align: Float, paint: Paint) {
    val width = paint.measureText(text)
    canvas.drawText(text, x - width * align, y, paint)
}
