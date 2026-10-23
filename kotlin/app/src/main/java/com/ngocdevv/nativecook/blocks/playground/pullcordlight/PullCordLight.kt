package com.ngocdevv.nativecook.blocks.playground.pullcordlight

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private const val PHOTO = "https://images.unsplash.com/photo-1529047033375-f402d3da24ca?auto=format&fit=crop&w=900&h=1950&q=80"

// The lamp hangs from a ceiling point just above the screen on a wire WIRE_SHARE of the screen's
// height long, and swings about that point as a damped pendulum.
private const val PIVOT_Y = -24.0
private const val WIRE_SHARE = 0.27
private const val GRAVITY = 1800.0 // dp/s²
private const val SWING_DAMPING = 1.7 // 1/s
// While held, the lamp follows the finger's angle through a stiff spring, so letting go hands its
// angular speed to the pendulum. Past MAX_TILT it is held back with rubber banding.
private const val HOLD_STIFFNESS = 260.0
private const val HOLD_DAMPING = 26.0
private const val MAX_TILT = 0.55

// The shade, in dp, with its top at the end of the wire: a dome SHADE_RIM wide at its mouth.
private const val SHADE_RIM = 76f
private const val SHADE_DEPTH = 84f
private const val BULB_R = 15f

// The pull cord: a chain of SEGMENTS verlet links CORD dp long, hanging from inside the rim
// beside the bulb, with a wooden bead on the end that weighs BEAD_WEIGHT times a link.
private const val SEGMENTS = 15
private const val CORD = 190.0
private const val CORD_X = 26.0
private const val CORD_Y = SHADE_DEPTH + 2.0
private const val BEAD_WEIGHT = 2.6
private const val SUBSTEPS = 3
private const val ITERATIONS = 14
private const val AIR = 0.9955 // velocity kept per substep
// Pulled, the links between the ceiling and the finger stretch up to MAX_STRETCH; past that the
// finger is followed with rubber banding. Let go, the stretch springs back at RECOIL per second.
private const val MAX_STRETCH = 1.32
private const val RECOIL = 6.0
// Pulling the bead PULL_TO_CLICK dp below where it hangs arms the switch; letting go clicks it.
private const val PULL_TO_CLICK = 62.0
private const val GRAB_RADIUS = 38.0
// How strongly a stretched cord tugs the lamp, and the little kick the switch gives it.
private const val TUG = 0.02
private const val CLICK_KICK = 0.09

// The light: it comes up at LIGHT_ON per second through a few quick flickers, and dies at
// LIGHT_OFF per second while the filament glows a little longer.
private const val LIGHT_ON = 9.0
private const val LIGHT_OFF = 5.5
private const val FILAMENT_OFF = 2.4
private const val CONE_SPREAD = 1.05f // half-width at the far end, in screen widths
private const val CONE_LENGTH = 1.05f // in screen heights

// Night grading and lamp grading of the same photo (4×5 colour matrices, the last column an offset).
private val Night = floatArrayOf(0.11f, 0.15f, 0.03f, 0f, 0.004f, 0.11f, 0.17f, 0.05f, 0f, 0.008f, 0.14f, 0.22f, 0.15f, 0f, 0.02f, 0f, 0f, 0f, 1f, 0f)
private val Lamp = floatArrayOf(1.12f, 0.07f, 0f, 0f, -0.03f, 0.03f, 0.97f, 0.02f, 0f, -0.03f, 0f, 0.03f, 0.72f, 0f, -0.04f, 0f, 0f, 0f, 1f, 0f)

// autoPlay (seconds into LOOP): pull the cord on, push the lamp into a swing, pull it off, rest.
private const val LOOP = 10.0
private val PullOn = doubleArrayOf(0.7, 1.55, 1.75)
private val Swing = doubleArrayOf(3.2, 3.8)
private val PullOff = doubleArrayOf(5.9, 6.75, 6.95)
private const val PULL_BY_X = 7.0
private const val PULL_BY_Y = 96.0
private const val SWING_TO = 0.3
// Over the last SETTLE s the motion is damped harder, so the loop seam meets a still room.
private const val SETTLE = 0.9
private const val USER_HOLD = 4.0

private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private fun ease(t: Double) = EaseInOut.transform(t.coerceIn(0.0, 1.0).toFloat()).toDouble()

private fun rubber(over: Double, limit: Double) = limit * (1 - 1 / (1 + over / limit))

/** An alpha rounded to thousandths, as the RN version prints it into a colour string. */
private fun alpha(value: Double) = (value.coerceIn(0.0, 1.0) * 1000).roundToInt() / 1000f

/** A bell, 1 at `at`, gone `width` either side. */
private fun bump(t: Double, at: Double, width: Double) = exp(-((t - at) / width).pow(2))

/** The light's level `since` s after it was switched on: three quick dips, then a faint shimmer. */
private fun flicker(since: Double, now: Double): Double {
    val dips = 0.62 * bump(since, 0.09, 0.045) + 0.4 * bump(since, 0.24, 0.04) + 0.2 * bump(since, 0.43, 0.05)
    val shimmer = 0.012 * sin(now * 13.1) * sin(now * 3.7)
    return max(0.0, 1 - dips + shimmer)
}

/** Reanimated's `interpolateColor`: each channel mixed in gamma 2.2 and rounded. */
private fun mix(from: Long, to: Long, t: Double): Color {
    val k = t.coerceIn(0.0, 1.0)
    fun channel(shift: Int): Float {
        val a = (((from shr shift) and 0xff) / 255.0).pow(2.2)
        val b = (((to shr shift) and 0xff) / 255.0).pow(2.2)
        return ((a + (b - a) * k).pow(1 / 2.2) * 255).roundToInt() / 255f
    }
    return Color(channel(16), channel(8), channel(0))
}

private fun mix(a: Long, b: Long, c: Long, middle: Double, t: Double) =
    if (t <= middle) mix(a, b, t / middle) else mix(b, c, (t - middle) / (1 - middle))

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)

/** The lamp, its cord and its light, shared by the frame loop, the touches and the drawing. */
private class LampMotion(initiallyOn: Boolean) {
    // Bumped once a frame; the drawing reads it, so it redraws every frame.
    var frame by mutableLongStateOf(0L)
    var width = 0.0
    var height = 0.0
    val pivotX get() = width / 2
    val wire get() = height * WIRE_SHARE
    val link = CORD / SEGMENTS
    // Moment arm of the pendulum: the wire plus half the shade.
    val arm get() = wire + SHADE_DEPTH * 0.6

    // The lamp's angle (rad, positive swings it left) and its speed.
    var angle = 0.0
    var spin = 0.0
    var lampHeld = false
    var lampTarget = 0.0
    var lampGrabFrom = 0.0
    var lampAngleFrom = 0.0
    // The rope: x, y, previous x, previous y for each particle, flat.
    var rope = DoubleArray(0)
    // The particle held by a finger (−1 for none), where the finger wants it, and the offset of the grab.
    var grabbed = -1
    var goalX = 0.0
    var goalY = 0.0
    var offsetX = 0.0
    var offsetY = 0.0
    var stretch = 1.0
    var armed = false
    // The light: `on` is the switch, `level` follows it, `glow` adds the flicker, `filament` the bulb.
    var on = initiallyOn
    var level = if (initiallyOn) 1.0 else 0.0
    var glow = level
    var filament = level
    var switchedAt = -10.0
    var time = 0.0
    var byUser = false
    var loopClock = 0.0
    // Where the scripted finger took the bead.
    var takenX = 0.0
    var takenY = 0.0
    var looping = false
    var hold = 0.0

    /** Where the cord hangs from on the shade, for the lamp at angle `a`. */
    fun anchorX(a: Double) = pivotX + CORD_X * cos(a) - (wire + CORD_Y) * sin(a)
    fun anchorY(a: Double) = PIVOT_Y + CORD_X * sin(a) + (wire + CORD_Y) * cos(a)

    /** The rope hangs straight down from its anchor whenever the screen is measured. */
    fun measure(w: Double, h: Double) {
        if (w == width && h == height && rope.isNotEmpty()) return
        width = w
        height = h
        rope = DoubleArray((SEGMENTS + 1) * 4)
        for (i in 0..SEGMENTS) {
            val x = pivotX + CORD_X
            val y = PIVOT_Y + wire + CORD_Y + i * link
            rope[i * 4] = x
            rope[i * 4 + 1] = y
            rope[i * 4 + 2] = x
            rope[i * 4 + 3] = y
        }
    }
}

/** The room photo and its two gradings, drawn through colour filters. */
private class RoomPaints {
    val night = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = graded(Night) }
    val lit = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = graded(Lamp) }
    val mask = Paint()
    val layer = Paint()
    val fill = RectF()
    val cone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        // Hardware canvases honour mask filters from API 28; below that the light has hard sides.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(16f), BlurMaskFilter.Blur.NORMAL)
    }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(2.5f), BlurMaskFilter.Blur.NORMAL)
    }
    val cord = Path()
    val conePath = Path()
    val corePath = Path()
    var coneFor = Offset.Zero

    companion object {
        /** Android's colour matrices take their offsets in 0…255, Skia's in 0…1. */
        fun graded(m: FloatArray): ColorMatrixColorFilter {
            val a = m.copyOf()
            for (row in 0 until 4) a[row * 5 + 4] *= 255f
            return ColorMatrixColorFilter(ColorMatrix(a))
        }
    }
}

/**
 * A dark bedroom at dusk with a pendant lamp hanging from the ceiling and its pull cord, a chain
 * of links simulated as a rope. Pulling the cord down stretches it; letting go past the click
 * switches the lamp: the bulb glows, a warm cone of light fades in under the shade and the room
 * comes up in lamplight with a soft flicker, while the cord bounces and swings. Pull again to switch
 * it off. The lamp itself can be grabbed and swung, and the cone sweeps the room with it.
 *
 * @param photo The room, drawn dark and lit from the same photo.
 * @param initiallyOn Starts with the light on.
 * @param autoPlay Pulls the cord to switch the light on, swings the lamp, and pulls it off again, on a loop.
 * @param onToggle Called when a pull switches the light.
 */
@Composable
fun PullCordLight(
    modifier: Modifier = Modifier,
    photo: String = PHOTO,
    initiallyOn: Boolean = false,
    autoPlay: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val toggling by rememberUpdatedState(onToggle)
    val motion = remember { LampMotion(initiallyOn) }
    val paints = remember { RoomPaints() }
    var room by remember { mutableStateOf<Bitmap?>(null) }
    val insets = WindowInsets.safeDrawing
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.loopClock = 0.0
        motion.hold = 0.0
    }

    LaunchedEffect(photo, context) {
        val request = ImageRequest.Builder(context).data(photo).build()
        room = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    fun flip(user: Boolean) {
        val m = motion
        val next = !m.on
        m.on = next
        if (next) m.switchedAt = m.time
        if (!reducedMotion) m.spin += if (next) CLICK_KICK else -CLICK_KICK
        if (user) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            toggling?.invoke(next)
        }
    }

    fun grabCord(index: Int, fingerX: Double, fingerY: Double, user: Boolean) {
        val m = motion
        m.byUser = user
        m.grabbed = index
        m.offsetX = m.rope[index * 4] - fingerX
        m.offsetY = m.rope[index * 4 + 1] - fingerY
        m.goalX = m.rope[index * 4]
        m.goalY = m.rope[index * 4 + 1]
        m.armed = false
    }

    fun moveCord(fingerX: Double, fingerY: Double) {
        motion.goalX = fingerX + motion.offsetX
        motion.goalY = fingerY + motion.offsetY
    }

    fun releaseCord() {
        val m = motion
        if (m.grabbed < 0) return
        m.grabbed = -1
        if (m.armed) flip(m.byUser)
        m.armed = false
    }

    /** The lamp's angle with the finger at (x, y): the angle of the line from the ceiling point. */
    fun fingerAngle(x: Double, y: Double) = atan2(-(x - motion.pivotX), max(1.0, y - PIVOT_Y))

    fun grabLamp(x: Double, y: Double, user: Boolean) {
        val m = motion
        m.byUser = user
        m.lampHeld = true
        m.lampGrabFrom = fingerAngle(x, y)
        m.lampAngleFrom = m.angle
        m.lampTarget = m.angle
    }

    fun moveLamp(x: Double, y: Double) {
        val m = motion
        val raw = m.lampAngleFrom + fingerAngle(x, y) - m.lampGrabFrom
        val over = abs(raw) - MAX_TILT
        m.lampTarget = if (over > 0) sign(raw) * (MAX_TILT + rubber(over, 0.2)) else raw
    }

    fun touched() {
        val m = motion
        m.hold = USER_HOLD
        // Hand a scripted pull or push over to the finger.
        if (m.grabbed >= 0 && !m.byUser) m.grabbed = -1
        if (m.lampHeld && !m.byUser) m.lampHeld = false
        m.armed = false
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        val m = motion
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (last < 0) 1.0 / 60 else min((nanos - last) / 1e9, 0.04)
                last = nanos
                val r = m.rope
                if (dt <= 0 || r.isEmpty()) return@withFrameNanos
                val now = m.time + dt
                m.time = now

                // The script, while nobody is touching.
                var settle = 0.0
                if (m.hold > 0) {
                    m.hold -= dt
                    if (m.hold <= 0 && m.looping) {
                        m.loopClock = 0.0
                        // A light left on by hand is switched off by the next pull of the loop.
                        if (m.on) m.loopClock = PullOff[0] - 0.4
                    }
                } else if (m.looping && !(m.grabbed >= 0 && m.byUser) && !(m.lampHeld && m.byUser)) {
                    val before = m.loopClock
                    var t = before + dt
                    if (t >= LOOP) t -= LOOP
                    m.loopClock = t
                    fun crossed(at: Double) = before < at && t >= at
                    // Two pulls of the cord: down to PULL_BY from where the bead was taken, a beat held, and let go.
                    for (pull in arrayOf(PullOn, PullOff)) {
                        if (crossed(pull[0])) {
                            val o = SEGMENTS * 4
                            m.takenX = r[o]
                            m.takenY = r[o + 1]
                            grabCord(SEGMENTS, r[o], r[o + 1], false)
                        }
                        if (t >= pull[0] && t < pull[2] && m.grabbed == SEGMENTS && !m.byUser) {
                            val p = ease(min(1.0, (t - pull[0]) / (pull[1] - pull[0])))
                            moveCord(m.takenX + PULL_BY_X * p, m.takenY + PULL_BY_Y * p)
                        }
                        if (crossed(pull[2])) releaseCord()
                    }
                    // A push on the lamp, let go mid-swing.
                    if (crossed(Swing[0])) {
                        m.lampHeld = true
                        m.byUser = false
                        m.lampTarget = m.angle
                    }
                    if (m.lampHeld && !m.byUser && t >= Swing[0] && t < Swing[1]) {
                        m.lampTarget = SWING_TO * ease((t - Swing[0]) / (Swing[1] - Swing[0]))
                    }
                    if (crossed(Swing[1])) m.lampHeld = false
                    if (t > LOOP - SETTLE) settle = (t - (LOOP - SETTLE)) / SETTLE
                    if (t < before) {
                        // The seam: everything has come to rest; clear what is left so each loop starts the same.
                        m.angle = 0.0
                        m.spin = 0.0
                        settle = 1.0
                    }
                }

                // The light.
                val wantOn = if (m.on) 1.0 else 0.0
                val rate = if (m.on) LIGHT_ON else LIGHT_OFF
                m.level += (wantOn - m.level) * (1 - exp(-dt * (if (reducedMotion) 6.0 else rate)))
                val fl = if (reducedMotion || !m.on) 1.0 else flicker(now - m.switchedAt, now)
                m.glow = m.level * fl
                val fRate = if (m.on) LIGHT_ON * 1.4 else FILAMENT_OFF
                m.filament += (wantOn * fl - m.filament) * (1 - exp(-dt * fRate))

                // The lamp: a damped pendulum, pulled toward the finger while held and tugged by the cord.
                val a = m.angle
                var accel = -(GRAVITY / m.arm) * sin(a) - SWING_DAMPING * (1 + settle * 6) * m.spin
                if (reducedMotion) accel = -(GRAVITY / m.arm) * sin(a) - 9 * m.spin
                if (m.lampHeld) accel = HOLD_STIFFNESS * (m.lampTarget - a) - HOLD_DAMPING * m.spin

                // Stretch of the held stretch of cord: toward what the finger asks for, or back to 1.
                val g = m.grabbed
                val ax0 = m.anchorX(a)
                val ay0 = m.anchorY(a)
                var tx = m.goalX
                var ty = m.goalY
                if (g > 0) {
                    val dx = tx - ax0
                    val dy = ty - ay0
                    val d = max(1.0, hypot(dx, dy))
                    val natural = g * m.link
                    val limit = natural * MAX_STRETCH
                    if (d > limit) {
                        val reach = limit + rubber(d - limit, 46.0)
                        tx = ax0 + dx / d * reach
                        ty = ay0 + dy / d * reach
                    }
                    m.stretch = (d / natural).coerceIn(1.0, MAX_STRETCH)
                    // The click is armed once the held link is far enough below where it hangs.
                    val pulled = ty - (ay0 + g * m.link)
                    if (!m.armed && pulled > PULL_TO_CLICK) {
                        m.armed = true
                        if (m.byUser) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    } else if (m.armed && pulled < PULL_TO_CLICK - 10) {
                        m.armed = false
                    }
                    // A stretched cord pulls the shade toward the finger.
                    val pull = (m.stretch - 1) * 1.6
                    val fx = dx / d * pull
                    val fy = dy / d * pull
                    val rx = ax0 - m.pivotX
                    val ry = ay0 - PIVOT_Y
                    accel += (fy * rx - fx * ry) * TUG * (if (reducedMotion) 0.3 else 1.0)
                } else {
                    m.stretch = 1 + (m.stretch - 1) * exp(-dt * RECOIL)
                }
                m.spin += accel * dt
                m.angle = a + m.spin * dt

                // The rope, in substeps: verlet integration, then the links pulled back to their lengths.
                val a1 = m.angle
                val ax1 = m.anchorX(a1)
                val ay1 = m.anchorY(a1)
                val h = dt / SUBSTEPS
                val keep = AIR - settle * 0.03 - (if (reducedMotion) 0.02 else 0.0)
                val s = m.stretch
                val link = m.link
                val p = m.rope
                val n = SEGMENTS + 1
                for (step in 1..SUBSTEPS) {
                    val f = step.toDouble() / SUBSTEPS
                    val topX = ax0 + (ax1 - ax0) * f
                    val topY = ay0 + (ay1 - ay0) * f
                    for (i in 1 until n) {
                        if (i == g) continue
                        val o = i * 4
                        val vx = (p[o] - p[o + 2]) * keep
                        val vy = (p[o + 1] - p[o + 3]) * keep
                        p[o + 2] = p[o]
                        p[o + 3] = p[o + 1]
                        p[o] += vx
                        p[o + 1] += vy + GRAVITY * h * h
                    }
                    p[0] = topX
                    p[1] = topY
                    p[2] = topX
                    p[3] = topY
                    if (g > 0) {
                        val o = g * 4
                        p[o + 2] = p[o]
                        p[o + 3] = p[o + 1]
                        p[o] += (tx - p[o]) * f
                        p[o + 1] += (ty - p[o + 1]) * f
                    }
                    repeat(ITERATIONS) {
                        for (i in 0 until n - 1) {
                            val a0 = i * 4
                            val b0 = a0 + 4
                            val wa = if (i == 0 || i == g) 0.0 else 1.0
                            val wb = if (i + 1 == g) 0.0 else if (i + 1 == n - 1) 1 / BEAD_WEIGHT else 1.0
                            val sum = wa + wb
                            if (sum == 0.0) continue
                            val dx = p[b0] - p[a0]
                            val dy = p[b0 + 1] - p[a0 + 1]
                            val d = max(0.0001, hypot(dx, dy))
                            val rest = link * (if (g > 0 && i >= g) 1.0 else s)
                            val k = (d - rest) / d / sum
                            p[a0] += dx * k * wa
                            p[a0 + 1] += dy * k * wa
                            p[b0] -= dx * k * wb
                            p[b0 + 1] -= dy * k * wb
                        }
                    }
                }
                if (settle >= 1) {
                    // At the seam: hang the rope straight, at rest.
                    for (i in 0 until n) {
                        val o = i * 4
                        p[o] = ax1
                        p[o + 1] = ay1 + i * link
                        p[o + 2] = p[o]
                        p[o + 3] = p[o + 1]
                    }
                }
                m.frame += 1
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF05060A))
            .semantics {
                contentDescription = "Pendant lamp. Pull the cord to switch the light."
                onClick {
                    touched()
                    flip(true)
                    true
                }
            },
    ) {
        motion.measure(maxWidth.value.toDouble(), maxHeight.value.toDouble())

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(motion) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val m = motion
                        val x = (down.position.x / density.density).toDouble()
                        val y = (down.position.y / density.density).toDouble()
                        val p = m.rope
                        if (p.isNotEmpty()) {
                            // The nearest link of the cord, the bead first.
                            var best = -1
                            var bestDistance = GRAB_RADIUS
                            for (i in SEGMENTS downTo 3) {
                                val d = hypot(p[i * 4] - x, p[i * 4 + 1] - y)
                                if (d < bestDistance - (if (i == SEGMENTS) 8 else 0)) {
                                    best = i
                                    bestDistance = d
                                }
                            }
                            if (best > 0) {
                                touched()
                                grabCord(best, x, y, true)
                            } else {
                                // Or the lamp: the point in the shade's own frame.
                                val a = m.angle
                                val dx = x - m.pivotX
                                val dy = y - PIVOT_Y
                                val lx = dx * cos(a) + dy * sin(a)
                                val ly = -dx * sin(a) + dy * cos(a) - m.wire
                                if (abs(lx) < SHADE_RIM + 18 && ly > -40 && ly < SHADE_DEPTH + 40) {
                                    touched()
                                    grabLamp(x, y, true)
                                }
                            }
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val fx = (change.position.x / density.density).toDouble()
                                val fy = (change.position.y / density.density).toDouble()
                                if (m.grabbed >= 0 && m.byUser) {
                                    change.consume()
                                    moveCord(fx, fy)
                                } else if (m.lampHeld && m.byUser) {
                                    change.consume()
                                    moveLamp(fx, fy)
                                }
                            }
                        } finally {
                            // Also when the gesture is cut off (cancelled pointer input), so the cord
                            // or the lamp is never left held and the loop can take over again.
                            if (m.byUser) {
                                if (m.grabbed >= 0) releaseCord()
                                if (m.lampHeld) m.lampHeld = false
                            }
                        }
                    }
                },
        ) {
            motion.frame
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawRoom(motion, room, paints)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = (safeBottom + 26f).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                "LINEN ROOM · PENDANT",
                style = TextStyle(color = rgba(255, 240, 220, 0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, fontFamily = FontFamily.SansSerif),
            )
            Box(Modifier.height(44.dp)) {
                val title = TextStyle(color = Color(0xFFFFF3E2), fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, fontFamily = FontFamily.SansSerif)
                BasicText("Lights off", Modifier.graphicsLayer { motion.frame; alpha = (1 - motion.level).toFloat() }, style = title, softWrap = false)
                BasicText("Lights on", Modifier.graphicsLayer { motion.frame; alpha = motion.glow.toFloat().coerceIn(0f, 1f) }, style = title, softWrap = false)
            }
            BasicText(
                "Pull the cord · Swing the lamp",
                Modifier.graphicsLayer { motion.frame; alpha = (0.45 + 0.25 * motion.level).toFloat() },
                style = TextStyle(color = Color(0xFFF3E6D4), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif),
            )
        }
    }
}

// MARK: Drawing

private fun DrawScope.drawRoom(m: LampMotion, room: Bitmap?, paints: RoomPaints) {
    val width = m.width.toFloat()
    val height = m.height.toFloat()
    // Gradients need a real radius: nothing to draw until the height is measured and finite.
    if (width <= 0f || height <= 0f || !height.isFinite() || m.rope.isEmpty()) return
    val glow = m.glow
    val pivotX = width / 2
    val wire = m.wire.toFloat()

    if (room != null && !room.isRecycled) {
        // The photo cropped to fill the screen.
        val s = max(width / room.width, height / room.height)
        val w = room.width * s
        val h = room.height * s
        paints.fill.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        drawIntoCanvas { it.nativeCanvas.drawBitmap(room, null, paints.fill, paints.night) }
        // The lit room: brightest where the cone lands, so it sweeps the room as the lamp swings.
        if (glow > 0.001) {
            val d = m.wire + SHADE_DEPTH + m.height * 0.42
            val pool = Offset((m.pivotX - d * sin(m.angle)).toFloat(), (PIVOT_Y + d * cos(m.angle)).toFloat())
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                paints.layer.alpha = (glow * 255).roundToInt().coerceIn(0, 255)
                nc.saveLayer(0f, 0f, width, height, paints.layer)
                nc.drawBitmap(room, null, paints.fill, paints.lit)
                drawRect(
                    Brush.radialGradient(
                        0f to Color.White,
                        0.45f to Color.White.copy(alpha = 0.5f),
                        1f to Color.White.copy(alpha = 0.12f),
                        center = pool,
                        radius = height * 0.8f,
                    ),
                    blendMode = BlendMode.DstIn,
                )
                nc.restore()
            }
        }
    } else {
        drawRect(Color(0xFF07090D))
    }
    // A vignette that keeps the corners of the room in shadow.
    drawRect(
        Brush.radialGradient(
            0f to Color.Black.copy(alpha = 0f),
            0.55f to Color.Black.copy(alpha = 0.25f),
            1f to Color.Black.copy(alpha = 0.72f),
            center = Offset(width / 2, height * 0.42f),
            radius = height * 0.72f,
        ),
    )

    val filament = m.filament
    val halo = listOf(rgba(255, 214, 150, alpha(0.75 * filament)), rgba(255, 190, 120, 0f))
    val angle = m.angle.toFloat()

    // The cone of light, which turns with the lamp.
    translate(pivotX, PIVOT_Y.toFloat()) {
        rotateRad(angle, pivot = Offset.Zero) {
            translate(0f, wire) {
                val coneTop = SHADE_DEPTH + 4
                val coneEnd = height * CONE_LENGTH
                val coneHalf = width * CONE_SPREAD
                if (paints.coneFor != Offset(width, height)) {
                    paints.coneFor = Offset(width, height)
                    paints.conePath.reset()
                    paints.conePath.moveTo(-SHADE_RIM * 0.86f, coneTop)
                    paints.conePath.lineTo(SHADE_RIM * 0.86f, coneTop)
                    paints.conePath.lineTo(coneHalf, coneEnd)
                    paints.conePath.lineTo(-coneHalf, coneEnd)
                    paints.conePath.close()
                    paints.corePath.reset()
                    paints.corePath.moveTo(-SHADE_RIM * 0.5f, coneTop)
                    paints.corePath.lineTo(SHADE_RIM * 0.5f, coneTop)
                    paints.corePath.lineTo(coneHalf * 0.42f, coneEnd * 0.8f)
                    paints.corePath.lineTo(-coneHalf * 0.42f, coneEnd * 0.8f)
                    paints.corePath.close()
                }
                if (glow > 0.001) {
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        paints.cone.shader = LinearGradient(
                            0f, coneTop, 0f, coneEnd,
                            intArrayOf(
                                rgba(255, 220, 160, alpha(0.5 * glow)).toArgb(),
                                rgba(255, 196, 120, alpha(0.2 * glow)).toArgb(),
                                rgba(255, 180, 110, 0f).toArgb(),
                            ),
                            floatArrayOf(0f, 0.35f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                        nc.drawPath(paints.conePath.asAndroidPath(), paints.cone)
                    }
                }
                if (filament > 0.001) {
                    drawCircle(Brush.radialGradient(halo, center = Offset(0f, SHADE_DEPTH + 10), radius = 150f), 150f, Offset(0f, SHADE_DEPTH + 10), blendMode = BlendMode.Screen)
                }
                if (glow > 0.001) {
                    drawIntoCanvas { canvas ->
                        paints.cone.shader = LinearGradient(
                            0f, coneTop, 0f, coneEnd * 0.8f,
                            rgba(255, 238, 205, alpha(0.4 * glow)).toArgb(),
                            rgba(255, 220, 170, 0f).toArgb(),
                            Shader.TileMode.CLAMP,
                        )
                        canvas.nativeCanvas.drawPath(paints.corePath.asAndroidPath(), paints.cone)
                    }
                }
            }
        }
    }

    // The cord through the midpoints between links, so it bends smoothly.
    val p = m.rope
    val cord = paints.cord
    cord.reset()
    cord.moveTo(p[0].toFloat(), p[1].toFloat())
    for (i in 1 until SEGMENTS) {
        val o = i * 4
        cord.quadraticTo(p[o].toFloat(), p[o + 1].toFloat(), ((p[o] + p[o + 4]) / 2).toFloat(), ((p[o + 1] + p[o + 5]) / 2).toFloat())
    }
    val last = SEGMENTS * 4
    cord.lineTo(p[last].toFloat(), p[last + 1].toFloat())

    // The cord's shadow on the wall.
    if (glow > 0.003) {
        translate((5 * glow).toFloat(), (4 * glow).toFloat()) {
            drawIntoCanvas { canvas ->
                paints.shadow.color = rgba(0, 0, 0, alpha(0.38 * glow)).toArgb()
                canvas.nativeCanvas.drawPath(cord.asAndroidPath(), paints.shadow)
            }
        }
    }

    // The wire and the shade.
    val roomLight = 0.16 + 0.84 * glow
    translate(pivotX, PIVOT_Y.toFloat()) {
        rotateRad(angle, pivot = Offset.Zero) {
            drawLine(Color(0xFF2A2E38), Offset.Zero, Offset(0f, wire + 4), strokeWidth = 1.6f)
            translate(0f, wire) {
                drawRect(Color(0xFF1D1A16), topLeft = Offset(-9f, -2f), size = androidx.compose.ui.geometry.Size(18f, 14f))
                val dome = Path().apply {
                    moveTo(-11f, 10f)
                    cubicTo(-38f, 12f, -SHADE_RIM + 2, 40f, -SHADE_RIM, SHADE_DEPTH)
                    lineTo(SHADE_RIM, SHADE_DEPTH)
                    cubicTo(SHADE_RIM - 2, 40f, 38f, 12f, 11f, 10f)
                    close()
                }
                drawPath(
                    dome,
                    Brush.horizontalGradient(
                        0f to mix(0x0a0c11, 0x2a2622, roomLight),
                        0.32f to mix(0x2c3243, 0x5d564d, roomLight),
                        0.7f to mix(0x3c4660, 0x3b352f, roomLight),
                        1f to mix(0x1a1f2c, 0x1d1a17, roomLight),
                        startX = -SHADE_RIM,
                        endX = SHADE_RIM,
                    ),
                )
                drawOval(mix(0x05060a, 0xffd9a0, glow), topLeft = Offset(-SHADE_RIM, SHADE_DEPTH - 6), size = androidx.compose.ui.geometry.Size(SHADE_RIM * 2, 12f))
            }
        }
    }

    // The cord and its bead.
    drawPath(cord, mix(0x80869a, 0xefe2cc, roomLight), style = Stroke(2.2f, cap = StrokeCap.Round))
    val turn = atan2(p[last + 1] - p[last - 3], p[last] - p[last - 4]) - Math.PI / 2
    translate(p[last].toFloat(), p[last + 1].toFloat()) {
        rotateRad(turn.toFloat(), pivot = Offset.Zero) {
            val bead = Path().apply {
                moveTo(0f, -2f)
                cubicTo(5f, -2f, 7.5f, 10f, 7f, 17f)
                cubicTo(6.5f, 25f, 3.5f, 28f, 0f, 28f)
                cubicTo(-3.5f, 28f, -6.5f, 25f, -7f, 17f)
                cubicTo(-7.5f, 10f, -5f, -2f, 0f, -2f)
                close()
            }
            drawPath(
                bead,
                Brush.horizontalGradient(listOf(mix(0x6c5847, 0xd49a62, roomLight), mix(0x2a2019, 0x7a4b28, roomLight)), startX = -7f, endX = 7f),
            )
        }
    }

    // The bulb and its glow.
    translate(pivotX, PIVOT_Y.toFloat()) {
        rotateRad(angle, pivot = Offset.Zero) {
            translate(0f, wire) {
                if (filament > 0.001) {
                    drawCircle(Brush.radialGradient(halo, center = Offset(0f, SHADE_DEPTH + 2), radius = 34f), 34f, Offset(0f, SHADE_DEPTH + 2), blendMode = BlendMode.Screen)
                }
                drawCircle(mix(0x4d525e, 0xffb36b, 0xfff6e2, 0.35, filament), BULB_R, Offset(0f, SHADE_DEPTH + 1))
                drawCircle(Color.White.copy(alpha = 0.35f), 3.2f, Offset(-4.5f, SHADE_DEPTH - 3))
            }
        }
    }
}
