package com.ngocdevv.nativecook.blocks.media.filmstripreel

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.launch

/** One frame of the roll: an Unsplash photo, how it is framed, and what its print says. */
data class ReelFrame(val id: String, val portrait: Boolean, val title: String, val line: String) {
    // A portrait original loses a third of its height to the 3:2 frame, so it needs less width than
    // a landscape one for the same sharpness; both leave enough for the print.
    val uri: String get() = "https://images.unsplash.com/$id?auto=format&fit=crop&w=${if (portrait) 720 else 900}&q=80"
}

private fun p(id: String, title: String, line: String) = ReelFrame("photo-$id", true, title, line)
private fun l(id: String, title: String, line: String) = ReelFrame("photo-$id", false, title, line)

// A roll shot between the skatepark and the beach, three frames at a time.
val ReelFrames: List<ReelFrame> = listOf(
    l("1520045892732-304bc3ac5d8e", "Rail Grind", "Rooftop ledge, golden hour"),
    p("1723236900134-63561e5832b3", "Kickflip", "Blue sky over Marlow Park"),
    p("1597019558926-3eef445fdf60", "Bowl at Dusk", "Concrete bowls, Dune Lane"),
    l("1498330177096-689e3fb901ca", "The Barrel", "Reef break, six feet"),
    l("1529410804717-979530809e26", "Spray", "Offshore wind, Kelp Point"),
    l("1468931467769-06a09c69aad3", "Long Line", "North swell at noon"),
    p("1547447134-cd3f5c716030", "Yellow Wall", "A deck painted by hand"),
    p("1517124792960-0f4701b9bd20", "Deep End", "Pool session, Harlan Street"),
    p("1569135579442-d37b7a0ea74e", "Silhouette", "Last light, Brightwater"),
    l("1454123253751-1fe2b9e0c10d", "The Lip", "Shorebreak at low tide"),
    l("1461503312594-019be44dd599", "Green Room", "Cloud break, early"),
    l("1471079688237-3ac9a55f1d6f", "Glass", "Inside the tube"),
    p("1499083773823-5000fa2b23e4", "Drop In", "Indoor ramp, Unit 9"),
    p("1597769555495-c54a15cd8c3f", "Studio Air", "Kickflip on white"),
    p("1601247309037-a3e434b07906", "Teal Deck", "Flip on the boardwalk"),
    l("1550071510-35e941e4888e", "The Wall", "One rider, Kelp Point"),
    l("1486869801134-25b6bc85fc0d", "Gold Water", "Backlit, at dusk"),
    l("1556316782-1ec9b261c156", "Blue Peak", "Point break, west side"),
    p("1591311337241-cecfd26f1da1", "Crosswalk", "Ollie, midtown"),
    l("1536318431364-5cc762cfc8ec", "Wheels Up", "Rooftop, after the rain"),
    p("1503417680882-163c1609fd2f", "Handrail", "Noon at Pier 4"),
    l("1506988038666-496173f39dea", "Grey Morning", "Overcast, four feet"),
    l("1613450861177-c281f178b920", "Three Up", "Paddling out at sunset"),
    l("1457234068269-b58092525794", "Inside", "Barrel, Little Harbor"),
)

private val Night = Color(0xFF0B0A09)
private val Base = Color(0xFF1A1512)
private val Cream = Color(0xFFEFE6D8)
private val Orange = Color(0xFFFF7A1F)
private val PaperTone = Color(0xFFF5F2EB)
private val PrintInk = Color(0xFF1D1A17)
private val WindowTone = Color(0xFF0E0B09)
private val EmptyLine = Color(0xFF2A221C)
// The light table under the strip, brightest under the loupe.
private val Glow = Color(0xFFFFE6C4)

// The strip crosses the screen at ANGLE rad, its middle STRIP_Y of the way down. A frame is
// FRAME_W of the screen wide at 3:2, frames are FRAME_GAP dp apart, and the rails above and below
// are RAIL dp deep: EDGE dp of film, a perforation PERF_H dp tall, then the edge printing.
private const val ANGLE = -0.13f
private const val STRIP_Y = 0.5f
private const val FRAME_W = 0.58f
private const val FRAME_GAP = 14f
private const val RAIL = 34f
private const val EDGE = 4f
private const val PERF_H = 11f
private const val PERFS = 8
// The frame under the loupe stands GROW larger, its neighbours pushed apart to make room.
private const val GROW = 0.06f
private const val LOUPE = 0.66f
// Far frames darken by FADE_STEP per frame from the middle, up to FADE_MAX.
private const val FADE_STEP = 0.26f
private const val FADE_MAX = 0.62f

// Speed, in frames per second, smoothed over ~1/SMOOTH s: it shears the strip by SHEAR per frame/s,
// bows its ends up by BOW dp per frame/s, and blurs the frames along it by BLUR dp per frame/s.
private const val SMOOTH = 12f
private const val SHEAR = 0.012f
private const val SHEAR_MAX = 0.16f
private const val BOW = 1.4f
private const val BOW_MAX = 18f
private const val BLUR = 0.95f
private const val BLUR_MAX = 11f
// The blur is sampled with this many copies of the photo slid along the strip.
private const val BLUR_TAPS = 9

// Dragging winds the strip with the finger; on release it is thrown PROJECT s ahead at its speed
// (never more than THROW_MAX frames) and springs onto the nearest frame.
private const val PROJECT = 0.32f
private const val THROW_MAX = 9f
private const val SNAP_MS = 700f
private const val SNAP_RATIO = 0.86f

// The print: PRINT_MARGIN dp from the screen's sides, a BORDER dp white border and CAPTION dp of
// paper under the photo for its caption, turned PRINT_TILT rad, lifted on the lift spring; the
// strip behind dims by DIM and shrinks by SHRINK.
private const val PRINT_MARGIN = 26f
private const val BORDER = 12f
private const val CAPTION = 66f
private const val PRINT_TILT = 0.03f
private const val PRINT_Y = 0.47f
private const val LIFT_MS = 620f
private const val LIFT_RATIO = 0.74f
private const val DROP_MS = 520f
private const val DROP_RATIO = 0.92f
private const val DIM = 0.66f
private const val SHRINK = 0.04f
private const val SHADOW_SIGMA = 16f

// autoPlay: two runs of WIND frames, each taking WIND_TIME s on an ease-in-out, each followed by a
// print that rises and drops back; LOOP s in all, so the roll comes round to where it began.
private const val LOOP = 14f
private const val WIND = 12f
private const val WIND_TIME = 2.2f
private val Runs = floatArrayOf(0.5f, 7.0f)
private val Lifts = floatArrayOf(3.2f, 9.7f)
private val Drops = floatArrayOf(5.6f, 12.1f)
private const val USER_HOLD = 3f
private const val IMAGE_FADE = 0.4f
// A tap is a touch lifted within TAP_MS that never travelled PAN_SLOP dp.
private const val TAP_MS = 320L
private const val PAN_SLOP = 6f

private class Loaded(val image: Bitmap, val at: Float)

private fun clamp(value: Float, lo: Float, hi: Float) = min(hi, max(lo, value))

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** `value` wrapped into -n/2 … n/2, so the roll has no ends. */
private fun wrap(value: Float, n: Int) = value - n * floor(value / n + 0.5f)

private fun easeInOut(t: Float): Float {
    val c = clamp(t, 0f, 1f)
    return if (c < 0.5f) 16f * c * c * c * c * c else 1f - (-2f * c + 2f).pow(5) / 2f
}

// Reanimated's default timing curve, ease in and out (quadratic).
private fun quadInOut(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f

/** How far the script has wound the strip at `t`, in frames. */
private fun scriptWound(t: Float): Float {
    var wound = 0f
    for (start in Runs) wound += WIND * easeInOut((t - start) / WIND_TIME)
    return wound
}

/** Where a frame stands on screen: its middle, turn, scale and shear. */
private class Pose {
    var x = 0f
    var y = 0f
    var angle = 0f
    var scale = 1f
    var shear = 0f

    /** A frame `d` frames from the middle of the strip: where it stands, along a strip that bows with speed. */
    fun set(d: Float, pitch: Float, speed: Float, cx: Float, cy: Float, motion: Float): Pose {
        val near = max(0f, 1f - abs(d))
        scale = 1f + GROW * near
        val push = GROW * pitch / 2f
        val along = d * pitch + sign(d) * push * min(abs(d), 1f)
        // The ends lift as the strip runs, bending it into a shallow smile.
        val bow = clamp(abs(speed) * BOW, 0f, BOW_MAX) * motion
        val reach = pitch * 1.6f
        val across = -bow * (along / reach) * (along / reach)
        val tangent = atan(-2f * bow * along / (reach * reach))
        val c = cos(ANGLE)
        val s = sin(ANGLE)
        x = cx + along * c - across * s
        y = cy + along * s + across * c
        angle = ANGLE + tangent
        shear = clamp(-speed * SHEAR, -SHEAR_MAX, SHEAR_MAX) * motion
        return this
    }
}

/**
 * A value driven per frame the way Reanimated drives `withSpring({ duration, dampingRatio })` and
 * `withTiming`, so the strip and the print move on the same curves as in the React Native version.
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

    /** Puts the value somewhere at once, stopping whatever was moving it. */
    fun set(next: Float) {
        value = next
        stop()
    }

    /** Stops whatever is moving the value, where it is. */
    fun stop() {
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
        omega0 = kotlin.math.sqrt(stiffness)
        omega1 = if (zeta < 1f) omega0 * kotlin.math.sqrt(1f - zeta * zeta) else 0f
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
                val omega = kotlin.math.sqrt(k) * zeta
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
private class ReelMotion(val count: Int) {
    // Bumped every frame so the picture is drawn again.
    var tick by mutableFloatStateOf(0f)
    var now = 0f
    val loaded = arrayOfNulls<Loaded>(count)
    // How far the roll is wound, in frames (frame i sits in the middle when it equals i), and its
    // smoothed speed in frames per second.
    val offset = DrivenValue()
    var lastOffset = 0f
    var speed = 0f
    // The print: how far it is lifted (0 in the strip, 1 a print), and which frame it is.
    val lift = DrivenValue()
    var lifted = -1
    var printing = false
    var dragFrom = 0f
    // The self-playing loop: its clock, and the offset it winds from.
    var looping = false
    var clock = 0f
    var base = 0f
    var hold = 0f
    // The screen, in dp, and the safe area at its top and bottom.
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f

    val frameW get() = width * FRAME_W
    val frameH get() = frameW * 2f / 3f
    val pitch get() = frameW + FRAME_GAP

    /** The frame in the middle of the strip. */
    fun centred() = if (count == 0) 0 else Math.round(offset.value).mod(count)
}

/**
 * A strip of 35mm film crossing a dark light table at a slant: drawn perforations, frame numbers
 * and orange edge printing, a loupe of light in the middle where the frame under it stands a
 * little larger. Dragging winds the strip with momentum; at speed it shears and bows and the
 * frames blur along it. Tapping the centred frame lifts it out into a white-bordered print with a
 * caption while the strip dims behind; a tap drops it back.
 *
 * @param autoPlay Winds a dozen frames on, lifts the centred one into a print and drops it back, twice every 14 s.
 * @param roll Shown in the corner and on every print's stamp.
 * @param onPrint Called with a frame's index when it is lifted into a print.
 */
@Composable
fun FilmStripReel(
    modifier: Modifier = Modifier,
    frames: List<ReelFrame> = ReelFrames,
    autoPlay: Boolean = false,
    roll: String = "07",
    onPrint: ((Int) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val n = frames.size
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val printed by rememberUpdatedState(onPrint)
    val motion = remember(frames) { ReelMotion(n) }
    val paints = remember { ReelPaints() }

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

    // Each photo arrives on its own and fades in; until then its window stays dark.
    LaunchedEffect(motion) {
        val loader = SingletonImageLoader.get(context)
        frames.forEachIndexed { i, frame ->
            launch {
                val request = ImageRequest.Builder(context)
                    .data(frame.uri)
                    .size(Size.ORIGINAL)
                    // A software bitmap can carry mipmaps, which keep the far frames from shimmering.
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                motion.loaded[i] = Loaded(result.image.toBitmap().also { it.setHasMipMap(true) }, motion.now)
            }
        }
    }

    fun raise(user: Boolean) {
        val m = motion
        val index = m.centred()
        m.lifted = index
        m.printing = true
        if (reducedMotion) m.lift.timeTo(1f, 240f, ::quadInOut) else m.lift.springTo(1f, 0f, LIFT_MS, LIFT_RATIO)
        if (user) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        printed?.invoke(index)
    }

    fun drop(user: Boolean) {
        val m = motion
        m.printing = false
        if (reducedMotion) m.lift.timeTo(0f, 200f, ::quadInOut) else m.lift.springTo(0f, 0f, DROP_MS, DROP_RATIO)
        if (user) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
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
                m.now += dt
                m.offset.step(dt)
                m.lift.step(dt)

                if (m.hold > 0f) {
                    m.hold -= dt
                    if (m.hold <= 0f) {
                        // Pick the loop up where the strip stands: at rest, or holding a print.
                        val at = Math.round(m.offset.value).toFloat()
                        m.clock = if (m.printing) 4.4f else 0f
                        m.base = if (m.printing) at - WIND else at
                    }
                } else if (m.looping) {
                    val before = m.clock
                    val t = (before + dt) % LOOP
                    m.clock = t
                    // The loop winds two dozen frames in all, so it ends where it began, a whole roll on.
                    if (t < before) m.base += WIND * Runs.size
                    m.offset.set(m.base + scriptWound(t))
                    fun crossed(at: Float) = (before < at && t >= at) || (t < before && (at > before || at <= t))
                    for (at in Lifts) if (crossed(at)) raise(false)
                    for (at in Drops) if (crossed(at)) drop(false)
                }

                // Speed, wrapped so the roll coming round does not count as a jump.
                val o = m.offset.value
                val moved = wrap(o - m.lastOffset, n) / dt
                m.lastOffset = o
                m.speed = mix(m.speed, moved, 1f - exp(-SMOOTH * dt))
                m.tick = m.now
            }
        }
    }

    fun panStart() {
        val m = motion
        touched()
        if (m.printing) drop(true)
        m.offset.stop()
        m.dragFrom = m.offset.value
    }

    fun panUpdate(tx: Float, ty: Float) {
        val m = motion
        // Only the drag along the strip winds it.
        val along = tx * cos(ANGLE) + ty * sin(ANGLE)
        m.offset.set(m.dragFrom - along / m.pitch)
    }

    fun panEnd(vx: Float, vy: Float) {
        val m = motion
        touched()
        val velocity = -(vx * cos(ANGLE) + vy * sin(ANGLE)) / m.pitch
        val to = Math.round(m.offset.value + clamp(velocity * PROJECT, -THROW_MAX, THROW_MAX)).toFloat()
        m.offset.springTo(to, velocity, SNAP_MS, SNAP_RATIO)
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun tapAt(x: Float, y: Float) {
        val m = motion
        touched()
        if (m.printing) {
            drop(true)
            return
        }
        // Where the tap falls along and across the strip.
        val dx = x - m.width / 2f
        val dy = y - m.height * STRIP_Y
        val along = dx * cos(ANGLE) + dy * sin(ANGLE)
        val across = -dx * sin(ANGLE) + dy * cos(ANGLE)
        if (abs(across) > m.frameH / 2f + RAIL) return
        val steps = Math.round(along / m.pitch)
        val o = m.offset.value
        if (steps == 0 && abs(o - Math.round(o)) < 0.08f) {
            raise(true)
        } else {
            // A frame off to the side winds to the middle.
            m.offset.stop()
            m.offset.springTo((Math.round(o) + steps).toFloat(), 0f, SNAP_MS, SNAP_RATIO)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = "Film strip, roll $roll, $n frames. Drag to wind the film; tap the frame in the middle to lift it into a print" }
            .pointerInput(motion, reducedMotion) {
                val d = density.density
                // A pan (after PAN_SLOP dp of travel) races a tap (a touch lifted within TAP_MS).
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)
                    var panning = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val tx = (change.position.x - down.position.x) / d
                        val ty = (change.position.y - down.position.y) / d
                        if (!change.pressed) {
                            if (panning) {
                                val velocity = tracker.calculateVelocity()
                                panEnd(velocity.x / d, velocity.y / d)
                            } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS && motion.width > 0f) {
                                tapAt(change.position.x / d, change.position.y / d)
                            }
                            event.changes.forEach(PointerInputChange::consume)
                            break
                        }
                        if (!panning && hypot(tx, ty) >= PAN_SLOP) {
                            panning = true
                            panStart()
                        }
                        if (panning) {
                            change.consume()
                            panUpdate(tx, ty)
                        }
                    }
                }
            },
    ) {
        motion.width = maxWidth.value
        motion.height = maxHeight.value
        Canvas(Modifier.fillMaxSize()) {
            motion.tick
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { drawReel(it.nativeCanvas, motion, frames, roll, paints, reducedMotion) }
            }
        }
    }
}

private fun typeface(family: Typeface, weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(family, weight, false)
    } else {
        Typeface.create(family, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

/** The paints, fonts, the segment path and scratch objects the picture reuses every frame. */
private class ReelPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val glow = Paint()
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        // Hardware canvases only honour mask filters from API 28; below that the print has no shadow.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(SHADOW_SIGMA), BlurMaskFilter.Blur.NORMAL)
    }
    val code = text(8f, Typeface.MONOSPACE, 600)
    val label = text(11f, Typeface.SANS_SERIF, 500)
    val title = text(18f, Typeface.SANS_SERIF, 500)
    val line = text(12f, Typeface.SANS_SERIF, 400)
    val stamp = text(11f, Typeface.MONOSPACE, 700)
    val rect = RectF()
    val src = Rect()
    val pose = Pose()
    val segment = Path()
    private var segmentPitch = -1f
    private var glowKey = -1f

    private fun text(size: Float, family: Typeface, weight: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = typeface(family, weight)
    }

    /** A strip segment: film base with its perforations cut out, a frame wide, centred on the origin. */
    fun segment(pitch: Float, frameH: Float): Path {
        if (pitch == segmentPitch) return segment
        segmentPitch = pitch
        segment.reset()
        val half = pitch / 2f + 1f
        val height = frameH + RAIL * 2f
        segment.addRect(-half, -height / 2f, half, height / 2f, Path.Direction.CW)
        val perfW = pitch / PERFS * 0.5f
        for (k in 0 until PERFS) {
            val x = -pitch / 2f + (k + 0.25f) * (pitch / PERFS)
            for (y in floatArrayOf(-height / 2f + EDGE, height / 2f - EDGE - PERF_H)) {
                segment.addRoundRect(x, y, x + perfW, y + PERF_H, 2f, 2f, Path.Direction.CW)
            }
        }
        segment.fillType = Path.FillType.EVEN_ODD
        return segment
    }

    /** The light table's glow, made again only when the screen changes. */
    fun glow(cx: Float, cy: Float, width: Float) {
        val key = cx * 31f + cy + width * 7f
        if (key == glowKey) return
        glowKey = key
        val g = Glow.toArgb() and 0x00FFFFFF
        glow.shader = RadialGradient(
            cx, cy, width * 0.9f,
            intArrayOf(g or (0x80 shl 24), g or (0x1F shl 24), g),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
}

// Skia's `measureText`, which the React Native version spaces and aligns its labels by, gives a
// glyph's bounds rather than its advance, and on the iPhone it was made on those bounds stand clear
// of the ink: each run takes about two points more than its advance. This keeps the same spacing.
private const val GLYPH_PAD = 2f

private fun measure(paint: Paint, text: String) = paint.measureText(text) + GLYPH_PAD

private fun advance(paint: Paint, letter: String): Float =
    if (letter != " ") measure(paint, letter) else paint.measureText("a a") - paint.measureText("aa")

private fun trackedWidth(paint: Paint, text: String, tracking: Float): Float {
    var width = 0f
    for (letter in text) width += advance(paint, letter.toString()) + tracking
    return width - tracking
}

private fun drawTracked(canvas: android.graphics.Canvas, text: String, x: Float, y: Float, tracking: Float, paint: Paint) {
    var at = x
    for (letter in text) {
        val s = letter.toString()
        canvas.drawText(s, at, y, paint)
        at += advance(paint, s) + tracking
    }
}

private fun alphaOf(value: Float) = if (value.isNaN()) 0 else (clamp(value, 0f, 1f) * 255f).roundToInt()

private fun paint(paint: Paint, color: Color, alpha: Float = 1f): Paint {
    paint.color = color.toArgb()
    paint.alpha = alphaOf(alpha)
    return paint
}

/**
 * A photo cropped to fill the rect at (x, y, w, h), faded in once it has loaded.
 *
 * The RN version blurs it along the strip with a one-axis Gaussian image filter; here the same
 * Gaussian is sampled with copies of the photo slid along the strip and averaged.
 */
private fun drawPhoto(canvas: android.graphics.Canvas, loaded: Loaded?, x: Float, y: Float, w: Float, h: Float, now: Float, paints: ReelPaints, alpha: Float, blur: Float) {
    // A zero (or NaN) size would make the crop below NaN, and roundToInt throws on NaN.
    if (loaded == null || !(w > 0f) || !(h > 0f)) return
    val shown = alpha * clamp((now - loaded.at) / IMAGE_FADE, 0f, 1f)
    if (shown <= 0f) return
    val image = loaded.image
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val fit = max(w / iw, h / ih)
    val sw = w / fit
    val sh = h / fit
    paints.src.set(((iw - sw) / 2f).roundToInt(), ((ih - sh) / 2f).roundToInt(), ((iw + sw) / 2f).roundToInt(), ((ih + sh) / 2f).roundToInt())
    if (blur <= 0f) {
        paints.rect.set(x, y, x + w, y + h)
        paints.art.alpha = alphaOf(shown)
        canvas.drawBitmap(image, paints.src, paints.rect, paints.art)
        return
    }
    canvas.save()
    canvas.clipRect(x, y, x + w, y + h)
    if (shown < 1f) canvas.saveLayerAlpha(x, y, x + w, y + h, alphaOf(shown))
    // Each copy drawn over the ones before at its share of the weight so far: the result is the
    // weighted average of all of them.
    var total = 0f
    for (k in 0 until BLUR_TAPS) {
        val dx = (k.toFloat() / (BLUR_TAPS - 1) * 2f - 1f) * 2f * blur
        val weight = exp(-dx * dx / (2f * blur * blur))
        total += weight
        paints.art.alpha = alphaOf(weight / total)
        paints.rect.set(x + dx, y, x + dx + w, y + h)
        canvas.drawBitmap(image, paints.src, paints.rect, paints.art)
    }
    canvas.restore()
}

/** One frame of film: the base and its perforations, the photo, and the orange edge printing. */
private fun drawSegment(
    canvas: android.graphics.Canvas,
    m: ReelMotion,
    index: Int,
    pose: Pose,
    empty: Boolean,
    shade: Float,
    blur: Float,
    roll: String,
    paints: ReelPaints,
) {
    val frameW = m.frameW
    val frameH = m.frameH
    val pitch = m.pitch
    canvas.save()
    canvas.translate(pose.x, pose.y)
    canvas.rotate(pose.angle * 180f / PI.toFloat())
    canvas.scale(pose.scale, pose.scale)
    canvas.skew(pose.shear, 0f)
    canvas.drawPath(paints.segment(pitch, frameH), paint(paints.fill, Base))

    // The photo, blurred along the strip at speed; an empty window once it has been lifted out.
    val fx = -frameW / 2f
    val fy = -frameH / 2f
    canvas.save()
    canvas.clipRect(fx, fy, fx + frameW, fy + frameH)
    canvas.drawRect(fx, fy, fx + frameW, fy + frameH, paint(paints.fill, WindowTone))
    if (!empty) {
        drawPhoto(canvas, m.loaded[index], fx, fy, frameW, frameH, m.now, paints, 1f, if (blur > 0.4f) blur else 0f)
    } else {
        paints.stroke.strokeWidth = 1f
        canvas.drawRect(fx + 6f, fy + 6f, fx + frameW - 6f, fy + frameH - 6f, paint(paints.stroke, EmptyLine))
    }
    canvas.restore()

    // Edge printing: the frame number by the perforations above, the stock and roll below.
    val number = (index + 1).toString()
    val top = fy - RAIL + EDGE + PERF_H + 11f
    val bottom = fy + frameH + RAIL - EDGE - PERF_H - 4f
    paint(paints.code, Orange, 0.85f)
    canvas.drawText(number, fx + 4f, top, paints.code)
    canvas.drawText("▸ ${number}A", fx + frameW * 0.5f, top, paints.code)
    paints.code.alpha = alphaOf(0.6f)
    canvas.drawText("NC 400T  5207  $roll", fx + 4f, bottom, paints.code)
    canvas.drawText("${number}A", fx + frameW - 22f, bottom, paints.code)

    // Frames away from the loupe fall into the dark.
    if (shade > 0.005f) {
        canvas.drawRect(-pitch / 2f - 1f, -frameH / 2f - RAIL, pitch / 2f + 1f, frameH / 2f + RAIL, paint(paints.fill, Night, shade))
    }
    canvas.restore()
}

/** The print, flying from its window in the strip to the middle of the screen. */
private fun drawPrint(canvas: android.graphics.Canvas, m: ReelMotion, frames: List<ReelFrame>, roll: String, paints: ReelPaints, reducedMotion: Boolean) {
    val width = m.width
    val height = m.height
    val up = m.lift.value
    val upc = clamp(up, 0f, 1f)
    val motion = if (reducedMotion) 0f else 1f
    val lifting = m.lifted
    val from = paints.pose.set(wrap(lifting - m.offset.value, frames.size), m.pitch, m.speed, width / 2f, height * STRIP_Y, motion)
    val k = if (reducedMotion) 1f else up
    val printW = width - PRINT_MARGIN * 2f
    val photoW = printW - BORDER * 2f
    val photoH = photoW * 2f / 3f
    val s = mix(from.scale * (m.frameW / photoW), 1f, k)
    val border = BORDER * clamp(up, 0f, 1.2f)
    val caption = CAPTION * clamp(up, 0f, 1.2f)
    val alpha = if (reducedMotion) upc else 1f
    canvas.save()
    canvas.translate(mix(from.x, width / 2f, k), mix(from.y, height * PRINT_Y, k))
    canvas.rotate(mix(from.angle, PRINT_TILT, k) * 180f / PI.toFloat())
    canvas.scale(s, s)
    val left = -photoW / 2f - border
    val top = -photoH / 2f - border
    val paperW = photoW + border * 2f
    val paperH = photoH + border + caption
    // A soft shadow under the paper.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        paints.shadow.alpha = alphaOf(0.5f * upc * alpha)
        canvas.drawRect(left + 4f, top + 18f * upc, left + paperW - 4f, top + 18f * upc + paperH, paints.shadow)
    }
    canvas.drawRect(left, top, left + paperW, top + paperH, paint(paints.fill, PaperTone, alpha))
    drawPhoto(canvas, m.loaded[lifting], -photoW / 2f, -photoH / 2f, photoW, photoH, m.now, paints, alpha, 0f)
    // The date stamp, burnt in orange in the photo's corner, and the caption on the paper.
    val words = clamp((up - 0.55f) / 0.4f, 0f, 1f) * alpha
    if (words > 0.01f) {
        val stamp = "'98 7 ${((lifting % 28) + 1).toString().padStart(2, ' ')}"
        paint(paints.stamp, Orange, 0.9f * words)
        canvas.drawText(stamp, photoW / 2f - 12f - measure(paints.stamp, stamp), photoH / 2f - 12f, paints.stamp)
        val item = frames[lifting]
        paint(paints.title, PrintInk, words)
        canvas.drawText(item.title, left + BORDER + 2f, photoH / 2f + 30f, paints.title)
        paint(paints.line, PrintInk, 0.55f * words)
        canvas.drawText(item.line, left + BORDER + 2f, photoH / 2f + 48f, paints.line)
        val number = "${lifting + 1}A / $roll"
        paint(paints.label, PrintInk, 0.55f * words)
        drawTracked(canvas, number, photoW / 2f - trackedWidth(paints.label, number, 1f) - 2f, photoH / 2f + 30f, 1f, paints.label)
    }
    canvas.restore()
}

private fun drawReel(canvas: android.graphics.Canvas, m: ReelMotion, frames: List<ReelFrame>, roll: String, paints: ReelPaints, reducedMotion: Boolean) {
    val width = m.width
    val height = m.height
    val n = frames.size
    val motion = if (reducedMotion) 0f else 1f
    val cx = width / 2f
    val cy = height * STRIP_Y
    val up = m.lift.value
    val upc = clamp(up, 0f, 1f)
    canvas.drawColor(Night.toArgb())
    // RadialGradient throws on a radius of 0, which a zero-sized first layout would ask for.
    if (width <= 0f || height <= 0f) return

    // The light table, glowing under the loupe; it shows through the perforations.
    paints.glow(cx, cy, width)
    canvas.drawRect(0f, 0f, width, height, paints.glow)
    if (n == 0) return

    // The strip, shrinking back a little while a print is up; the middle frame drawn last.
    canvas.save()
    val back = 1f - SHRINK * upc * motion
    canvas.scale(back, back, cx, cy)
    val blur = clamp(abs(m.speed) * BLUR, 0f, BLUR_MAX) * motion
    val o = m.offset.value
    val order = (0 until n).filter { abs(wrap(it - o, n)) < 3.2f }.sortedByDescending { abs(wrap(it - o, n)) }
    for (i in order) {
        val d = wrap(i - o, n)
        val pose = paints.pose.set(d, m.pitch, m.speed, cx, cy, motion)
        val shade = min(FADE_MAX, max(0f, abs(d) - 0.15f) * FADE_STEP)
        drawSegment(canvas, m, i, pose, i == m.lifted && up > 0.001f, shade, blur, roll, paints)
    }
    canvas.restore()

    // The loupe: a ring of light over the middle frame.
    paints.stroke.strokeWidth = 1.25f
    canvas.drawCircle(cx, cy, m.frameW * LOUPE, paint(paints.stroke, Cream, 0.22f * (1f - upc)))
    canvas.drawCircle(cx, cy, m.frameW * LOUPE + 6f, paint(paints.stroke, Cream, 0.08f * (1f - upc)))

    // The room dims behind a print.
    if (up > 0.001f) canvas.drawRect(0f, 0f, width, height, paint(paints.fill, Night, DIM * upc))
    if (m.lifted >= 0 && up > 0.001f) drawPrint(canvas, m, frames, roll, paints, reducedMotion)

    // Corners: the roll, the frame count, and how to use it.
    val topY = m.top + 24f
    paint(paints.label, Cream, 0.75f)
    drawTracked(canvas, "ROLL $roll — COASTLINE", 20f, topY, 1.4f, paints.label)
    val count = "${(m.centred() + 1).toString().padStart(2, '0')}/$n"
    paints.label.alpha = alphaOf(0.5f)
    drawTracked(canvas, count, width - 20f - trackedWidth(paints.label, count, 1.4f), topY, 1.4f, paints.label)
    val hint = if (up > 0.5f) "TAP TO RETURN IT TO THE ROLL" else "DRAG TO WIND · TAP THE FRAME TO PRINT"
    paints.label.alpha = alphaOf(0.38f)
    drawTracked(canvas, hint, (width - trackedWidth(paints.label, hint, 1.4f)) / 2f, height - m.bottom - 22f, 1.4f, paints.label)
}
