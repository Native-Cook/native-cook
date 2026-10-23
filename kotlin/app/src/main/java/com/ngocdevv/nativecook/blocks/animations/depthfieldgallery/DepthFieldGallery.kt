package com.ngocdevv.nativecook.blocks.animations.depthfieldgallery

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The two scenes, and the inks of the cards.
private class Palette(val bg: Color, val ink: Color)

private val Light = Palette(Color(0xFFFFFFFF), Color(0xFF111111))
private val Dark = Palette(Color(0xFF0E0E0E), Color(0xFFF0F0EA))
private val CardInk = Color(0xFF1C1C1C)
private val MarkInk = Color(0xFF0B0B0B)
private val DotInk = Color(0xFFF4F4F0)

// The field: FIELD cards through a tunnel DEPTH units long that repeats behind itself, a unit
// being about a card's width. Every HERO_EVERY-th card floats near the middle as a stop for the
// camera; the rest are scattered SPREAD_X by SPREAD_Y units around them, clear of the middle.
private const val FIELD = 160
private const val HERO_EVERY = 4
private const val DEPTH = 64f
private const val HERO_X = 0.8
private const val HERO_Y = 1.3
private const val SPREAD_X = 3.0
private const val SPREAD_Y = 4.8
private const val CLEAR_X = 1.0
private const val CLEAR_Y = 1.7
// Each card is turned by up to these angles (radians) and sways a little as it floats.
private const val YAW = 0.14
private const val PITCH = 0.1
private const val ROLL = 0.035
private const val SWAY = 0.03f
// The lens: its focal length as a share of the screen's width. A card the camera stops at
// fills FRAME_SHARE of the width, a little off the middle. Cards fade in between FAR and
// FAR_FADE units away and out between NEAR_FADE and NEAR.
private const val FOCAL = 0.95f
private const val FRAME_SHARE = 0.62f
private const val FRAME_OFF_X = 0.07
private const val FRAME_OFF_Y = 0.05
private const val FAR = 12f
private const val FAR_FADE = 9f
private const val NEAR = 0.35f
private const val NEAR_FADE = 0.8f

// The self-playing reel: the camera flies on to the next stop ahead over FLIGHT ms on an
// ease-in-out curve, arriving DRIFT units short of it, then drifts in the rest of the way over
// HOLD ms; every TURN_EVERY stops the lights turn over on the way.
private const val FLIGHT = 950f
private const val HOLD = 900f
private const val DRIFT = 0.2f
private const val DRIFT_SIDE = 0.06f
private const val TURN_EVERY = 6
// Arrival: the camera starts ARRIVE units back and flies in over ARRIVE_TIME ms.
private const val ARRIVE = 3f
private const val ARRIVE_TIME = 1500f
// By hand: a drag flies DRAG_RATE units per dp and pans PAN_RATE units per dp; on release the
// camera flies to the stop nearest where the drag was heading THROW s ahead.
private const val DRAG_RATE = 0.014f
private const val PAN_RATE = 0.006f
private const val THROW = 0.25f
private const val DRAG_SLOP = 4f
private const val TAP_MS = 400L
private const val SNAP_SPRING_MS = 750f
private const val SNAP_SPRING_RATIO = 0.9f
private const val FLY_SPRING_MS = 950f
private const val FLY_SPRING_RATIO = 0.92f
private const val USER_HOLD = 3f
private const val REDUCED_MS = 250f

// Turning the lights over by hand: the new scene spreads from the switch over WIPE_TIME ms.
private const val WIPE_TIME = 900f
private val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private fun quadOut(t: Float) = 1f - (1f - t) * (1f - t)
private const val EDGE_FADE = 64f
private const val CHROME_DELAY = 500L
private const val CHROME_TIME = 600
private const val CAPTION_IN = 0.35f
private const val CAPTION_RISE = 8f
private const val IMAGE_FADE = 0.4f
// How many full-size photos are kept: the card in view, the next one and the last two.
private const val LARGE_KEEP = 4
// The photos' prints are a little harder than the originals.
private const val CONTRAST = 1.2f

private class Loaded(val image: Bitmap, val at: Float)

private class Large(val image: Bitmap, val at: Float, val card: Int)

/** Where one card floats in the field. Sizes are in units, angles in radians. */
private class Placement(
    val x: Float,
    val y: Float,
    val z: Float,
    val width: Float,
    val aspect: Float,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val card: Int,
    val hero: Boolean,
    val frameX: Float,
    val frameY: Float,
)

private class Camera(val x: Float, val y: Float, val z: Float)

private class Shown(val index: Int, val depth: Float, val matrix: FloatArray, val quad: FloatArray, val alpha: Float)

/** A small seeded generator (mulberry32), so the field is the same every time. */
private fun random(seed: Int): () -> Double {
    var s = seed
    return {
        s += 0x6d2b79f5
        var t = s
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

private class CardSize(val width: Double, val aspect: Float)

private fun sizeOf(card: DepthCard) = when (card) {
    is DepthCard.Photo -> CardSize(0.9, 1.22f)
    is DepthCard.Poster -> CardSize(0.85, 1.32f)
    is DepthCard.Dots -> CardSize(0.78, 1.12f)
    is DepthCard.Glyph -> CardSize(0.8, 1.18f)
    is DepthCard.Chip -> CardSize(0.72, 0.78f)
    is DepthCard.Mark -> CardSize(1.0, 0.62f)
}

private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/** The first step from `from` up that walks through all `n` cards before it repeats one. */
private fun coprime(n: Int, from: Int): Int {
    var step = from
    while (gcd(step, n) != 1) step += 1
    return step
}

private fun scatter(cards: List<DepthCard>): List<Placement> {
    val r = random(11)
    val heroStep = coprime(cards.size, 7)
    val restStep = coprime(cards.size, 5)
    return List(FIELD) { i ->
        val hero = i % HERO_EVERY == 0
        // The stops take every card in turn; the cards around them are dealt from a different shuffle.
        val card = if (hero) (i / HERO_EVERY * heroStep) % cards.size else (i * restStep + 3) % cards.size
        val size = sizeOf(cards[card])
        var x = (r() * 2 - 1) * HERO_X
        var y = (r() * 2 - 1) * HERO_Y
        if (!hero) {
            do {
                x = (r() * 2 - 1) * SPREAD_X
                y = (r() * 2 - 1) * SPREAD_Y
            } while (abs(x) < CLEAR_X && abs(y) < CLEAR_Y)
        }
        // Drawn in the same order as the React Native version, so the field comes out the same.
        val z = (i + r() * 0.4) * DEPTH / FIELD
        val width = size.width * (if (hero) 1.0 else 0.55 + r() * 0.5)
        val yaw = (r() * 2 - 1) * YAW
        val pitch = (r() * 2 - 1) * PITCH
        val roll = (r() * 2 - 1) * ROLL
        val frameX = (r() * 2 - 1) * FRAME_OFF_X
        val frameY = (r() * 2 - 1) * FRAME_OFF_Y
        Placement(
            x.toFloat(), y.toFloat(), z.toFloat(), width.toFloat(), size.aspect,
            yaw.toFloat(), pitch.toFloat(), roll.toFloat(), card, hero, frameX.toFloat(), frameY.toFloat(),
        )
    }
}

private fun clamp01(value: Float) = value.coerceIn(0f, 1f)

private fun easeOut(p: Float) = 1f - (1f - p) * (1f - p) * (1f - p)

private fun mod(value: Float, by: Float) = ((value % by) + by) % by

/** Mixes two colours channel by channel, as the React Native version mixes #rrggbb. */
private fun mix(a: Color, b: Color, t: Float) = Color(
    (a.red + (b.red - a.red) * t),
    (a.green + (b.green - a.green) * t),
    (a.blue + (b.blue - a.blue) * t),
)

/**
 * Where the camera stands to frame a card: far enough back for it to fill FRAME_SHARE of the
 * width, a little off the middle, at the copy of the tunnel nearest `near` (or the next one ahead).
 */
private fun framing(p: Placement, width: Float, height: Float, near: Float, ahead: Boolean): Camera {
    val distance = FOCAL * p.width / FRAME_SHARE
    val gap = p.z - distance - near
    return Camera(
        p.x - p.frameX * distance / FOCAL,
        p.y - p.frameY * height / width * (distance / FOCAL),
        near + if (ahead) mod(gap, DEPTH) else mod(gap + DEPTH / 2f, DEPTH) - DEPTH / 2f,
    )
}

/**
 * Every card the camera can see, far to near: the perspective matrix that maps its face (in
 * hundredths of a unit about its middle) onto the screen, its corners there, and how faded it is.
 */
private fun frame(field: List<Placement>, cam: Camera, width: Float, height: Float, now: Float, sway: Float): List<Shown> {
    val shown = ArrayList<Shown>()
    val f = width * FOCAL
    for (i in field.indices) {
        val p = field[i]
        val depth = mod(p.z - cam.z, DEPTH)
        if (depth < NEAR || depth > FAR) continue
        val wave = sway * sin(now * 0.7f + i * 1.7f)
        val cx = p.x - cam.x
        val cy = p.y - cam.y + wave * SWAY
        val cz = depth
        val yaw = p.yaw + wave * 0.03f
        // The card's axes after it turns, and the plane they span projected through the lens.
        val cY = cos(yaw)
        val sY = sin(yaw)
        val cP = cos(p.pitch)
        val sP = sin(p.pitch)
        val cR = cos(p.roll)
        val sR = sin(p.roll)
        val k = 1f / 100f
        val ax = (cY * cR + sY * sP * sR) * k
        val ay = cP * sR * k
        val az = (-sY * cR + cY * sP * sR) * k
        val bx = (-cY * sR + sY * sP * cR) * k
        val by = cP * cR * k
        val bz = (sY * sR + cY * sP * cR) * k
        val mx = width / 2f
        val my = height / 2f
        val matrix = floatArrayOf(
            (mx * az + f * ax) / cz,
            (mx * bz + f * bx) / cz,
            (mx * cz + f * cx) / cz,
            (my * az + f * ay) / cz,
            (my * bz + f * by) / cz,
            (my * cz + f * cy) / cz,
            az / cz,
            bz / cz,
            1f,
        )
        val w = p.width * 50f
        val h = p.width * p.aspect * 50f
        val quad = FloatArray(8)
        var behind = false
        for (corner in 0 until 4) {
            val u = if (corner == 0 || corner == 3) -w else w
            val v = if (corner < 2) -h else h
            val z = matrix[6] * u + matrix[7] * v + 1f
            if (z <= 0.05f) behind = true
            quad[corner * 2] = (matrix[0] * u + matrix[1] * v + matrix[2]) / z
            quad[corner * 2 + 1] = (matrix[3] * u + matrix[4] * v + matrix[5]) / z
        }
        if (behind) continue
        val left = min(min(quad[0], quad[2]), min(quad[4], quad[6]))
        val right = max(max(quad[0], quad[2]), max(quad[4], quad[6]))
        val top = min(min(quad[1], quad[3]), min(quad[5], quad[7]))
        val bottom = max(max(quad[1], quad[3]), max(quad[5], quad[7]))
        if (right < 0f || left > width || bottom < 0f || top > height) continue
        val alpha = clamp01((FAR - depth) / (FAR - FAR_FADE)) * clamp01((depth - NEAR) / (NEAR_FADE - NEAR))
        shown.add(Shown(i, depth, matrix, quad, alpha))
    }
    shown.sortByDescending { it.depth }
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

/** One stretch of a timed move: where to, over how long, on which curve. */
private class Segment(val to: Float, val seconds: Float, val curve: (Float) -> Float)

/**
 * A value driven per frame the way Reanimated drives `withSpring({ duration, dampingRatio })`,
 * `withTiming` and `withSequence`, so the camera moves on the same curves as in React Native.
 */
private class DrivenValue {
    var value = 0f
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
    private var segment: Segment? = null
    private val queue = ArrayDeque<Segment>()

    /** Puts the value somewhere at once, stopping whatever was moving it. */
    fun set(next: Float) {
        value = next
        mode = 0
        velocity = 0f
        queue.clear()
    }

    fun springTo(target: Float, speed: Float, durationMs: Float, dampingRatio: Float) {
        // A spring already heading to the same place carries on as it was.
        if (mode == 1 && to == target) return
        // A spring takes over a spring's speed; a timed move has none to give, and Reanimated
        // then drops the new speed too.
        var v = when (mode) {
            1 -> velocity + speed
            2 -> 0f
            else -> speed
        }
        // Speed that would carry the first frame away from the target is dropped.
        if ((target > value && v < 0f) || (target < value && v > 0f)) v = 0f
        val x0 = value - target
        queue.clear()
        mode = 1
        to = target
        velocity = v
        zeta = dampingRatio
        stiffness = stiffnessFor(x0, v, durationMs, dampingRatio)
        omega0 = sqrt(stiffness)
        omega1 = if (zeta < 1f) omega0 * sqrt(1f - zeta * zeta) else 0f
        initialEnergy = energy(x0, speed)
    }

    fun timeTo(vararg segments: Segment) {
        queue.clear()
        queue.addAll(segments)
        mode = 2
        velocity = 0f
        next()
    }

    private fun next() {
        val s = queue.removeFirstOrNull()
        if (s == null) {
            mode = 0
            return
        }
        segment = s
        from = value
        time = 0f
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
                val s = segment ?: return
                time += dt
                val p = clamp01(time / s.seconds)
                value = from + (s.to - from) * s.curve(p)
                if (p >= 1f) next()
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
private class FieldMotion(count: Int, startStop: Int, dark: Boolean) {
    // Seconds since the field first appeared. The picture reads it, so it is drawn every frame.
    var elapsed by mutableFloatStateOf(0f)
    val loaded = arrayOfNulls<Loaded>(count)
    val large = ArrayList<Large>()
    // The cards whose full-size copy is on its way.
    val fetching = HashSet<Int>()
    // The camera, in units; its depth runs on for ever and the field repeats around it.
    val camX = DrivenValue()
    val camY = DrivenValue()
    val camZ = DrivenValue()
    var fromX = 0f
    var fromZ = 0f
    // The card the camera is on, and when it got there, for the caption.
    var focus = startStop
    var focusAt = 0f
    // The card it was on before, whose caption is on its way out.
    var lastFocus = startStop
    // 0 light, 1 dark, and where it is headed.
    val theme = DrivenValue().apply { set(if (dark) 1f else 0f) }
    var darkOn = dark
    // The montage: time into the current shot, and how many shots so far.
    var shotClock = 0f
    var shots = 0
    var looping = false
    var hold = 0f
    var started = false
    // The screen, in dp, and the safe area at its top and bottom.
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f

    fun camera() = Camera(camX.value, camY.value, camZ.value)
}

/**
 * A field of cards floating in space, photos printed in black and white or lime among pieces of
 * a dotted identity. By itself the camera flies from card to card, drifting in on each, and now
 * and then the lights turn over on the way. Dragging flies the camera through the field and lands
 * it on the nearest card; tapping a card flies to it. The switch turns the lights over by hand,
 * the new scene spreading from it across the screen.
 *
 * @param cards What the cards in the field show; they repeat as far as the field runs.
 * @param autoPlay Flies from card to card, drifting in on each, and turns the lights over now and then.
 * @param dark Starts on the dark scene.
 * @param accent The identity's colour: the dotted cards, the lime prints and the switch.
 * @param onFocus Called with a card when the camera is sent to it by hand.
 */
@Composable
fun DepthFieldGallery(
    modifier: Modifier = Modifier,
    cards: List<DepthCard> = DepthCards,
    autoPlay: Boolean = false,
    dark: Boolean = false,
    accent: Color = Color(0xFFD8F05A),
    onFocus: ((DepthCard) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusing by rememberUpdatedState(onFocus)
    val scope = rememberCoroutineScope()
    val field = remember(cards) { scatter(cards) }
    // The cards the camera stops at.
    val stops = remember(field) { field.indices.filter { field[it].hero } }
    val paints = remember(cards, accent) { FieldPaints(cards, accent) }
    val motion = remember(cards) { FieldMotion(cards.size, stops.firstOrNull() ?: 0, dark) }

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
        motion.shotClock = 0f
        motion.hold = 0f
    }

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
            // A software bitmap can carry mipmaps, which keep the far, small cards from shimmering.
            .allowHardware(false)
            .build()
        val result = loader.execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap().also { it.setHasMipMap(true) }
    }

    // Each photo arrives on its own and fades in; until then its card shows its paper.
    LaunchedEffect(motion) {
        cards.forEachIndexed { i, card ->
            if (card !is DepthCard.Pictured) return@forEachIndexed
            launch {
                val image = fetch(card.uri) ?: return@launch
                motion.loaded[i] = Loaded(image, motion.elapsed)
            }
        }
    }

    // Fetches the full-size copies of these cards' photos, keeping only the last few.
    fun sharpen(wanted: List<Int>) {
        for (card in wanted) {
            val item = cards[card] as? DepthCard.Pictured ?: continue
            val full = item.full ?: continue
            if (card in motion.fetching || motion.large.any { it.card == card }) continue
            motion.fetching.add(card)
            scope.launch {
                val image = fetch(full)
                motion.fetching.remove(card)
                if (image == null) return@launch
                motion.large.removeAll { it.card == card }
                motion.large.add(Large(image, motion.elapsed, card))
                while (motion.large.size > LARGE_KEEP) motion.large.removeAt(0)
            }
        }
    }

    // The card at a stop and the one at the stop after it.
    fun sharpenAt(index: Int) {
        val k = stops.indexOf(index)
        val after = if (k >= 0) stops[(k + 1) % stops.size] else index
        sharpen(listOf(field[index].card, field[after].card))
    }

    fun setFocus(index: Int) {
        if (index == motion.focus) return
        sharpenAt(index)
        motion.lastFocus = motion.focus
        motion.focusAt = motion.elapsed
        motion.focus = index
    }

    fun turn(user: Boolean) {
        val next = !motion.darkOn
        motion.darkOn = next
        motion.theme.timeTo(Segment(if (next) 1f else 0f, (if (reducedMotion) REDUCED_MS else WIPE_TIME) / 1000f, EaseInOut::transform))
        if (user) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun eased(to: Camera) {
        motion.camX.timeTo(Segment(to.x, REDUCED_MS / 1000f, EaseOutStrong::transform))
        motion.camY.timeTo(Segment(to.y, REDUCED_MS / 1000f, EaseOutStrong::transform))
        motion.camZ.timeTo(Segment(to.z, REDUCED_MS / 1000f, EaseOutStrong::transform))
    }

    // Sends the camera to frame a card on a spring, carrying the finger's speed.
    fun flyTo(index: Int, durationMs: Float, ratio: Float, vx: Float, vz: Float) {
        val to = framing(field[index], motion.width, motion.height, motion.camZ.value, false)
        setFocus(index)
        if (reducedMotion) {
            eased(to)
            return
        }
        motion.camX.springTo(to.x, vx, durationMs, ratio)
        motion.camY.springTo(to.y, 0f, durationMs, ratio)
        motion.camZ.springTo(to.z, vz, durationMs, ratio)
    }

    // The reel: the camera flies on to a card, arriving a little short and to one side, and
    // drifts in the rest of the way while it holds.
    fun glideTo(index: Int, flightMs: Float) {
        val to = framing(field[index], motion.width, motion.height, motion.camZ.value, true)
        val side = sin(index * 12.9898f) * DRIFT_SIDE
        setFocus(index)
        if (reducedMotion) {
            eased(to)
            return
        }
        val fly = flightMs / 1000f
        val drift = (HOLD + 200f) / 1000f
        motion.camX.timeTo(Segment(to.x - side, fly, EaseInOut::transform), Segment(to.x, drift, ::quadOut))
        motion.camY.timeTo(Segment(to.y, fly, EaseInOut::transform))
        motion.camZ.timeTo(Segment(to.z - DRIFT, fly, EaseInOut::transform), Segment(to.z, drift, ::quadOut))
    }

    // The next stop ahead of the camera.
    fun nextStop(): Int {
        var best = stops[0]
        var gap = Float.MAX_VALUE
        for (index in stops) {
            val ahead = framing(field[index], motion.width, motion.height, motion.camZ.value, true).z - motion.camZ.value
            if (ahead > DRIFT * 2f && ahead < gap) {
                gap = ahead
                best = index
            }
        }
        return best
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
                m.camX.step(dt)
                m.camY.step(dt)
                m.camZ.step(dt)
                m.theme.step(dt)
                m.elapsed += dt
                if (!m.started) {
                    // Arrival: the camera starts well back and flies in on the first card.
                    m.started = true
                    val to = framing(field[stops[0]], m.width, m.height, 0f, true)
                    m.camX.set(to.x)
                    m.camY.set(to.y)
                    m.camZ.set(to.z - if (reducedMotion) 0f else ARRIVE)
                    glideTo(stops[0], ARRIVE_TIME)
                    sharpenAt(stops[0])
                    m.shotClock = -ARRIVE_TIME / 1000f
                    return@withFrameNanos
                }
                if (m.hold > 0f) {
                    m.hold -= dt
                    if (m.hold <= 0f) m.shotClock = 0f
                    return@withFrameNanos
                }
                if (!m.looping) return@withFrameNanos
                val t = m.shotClock + dt
                if (t < HOLD / 1000f) {
                    m.shotClock = t
                    return@withFrameNanos
                }
                m.shotClock = -FLIGHT / 1000f
                m.shots += 1
                if (m.shots % TURN_EVERY == 0) turn(false)
                glideTo(nextStop(), FLIGHT)
            }
        }
    }

    fun panStart() {
        touched()
        // Caught wherever the camera is.
        motion.camX.set(motion.camX.value)
        motion.camY.set(motion.camY.value)
        motion.camZ.set(motion.camZ.value)
        motion.fromX = motion.camX.value
        motion.fromZ = motion.camZ.value
    }

    fun panUpdate(tx: Float, ty: Float) {
        // Up flies on, down flies back; sideways pans, the cards following the finger.
        motion.camZ.set(motion.fromZ - ty * DRAG_RATE)
        motion.camX.set(motion.fromX - tx * PAN_RATE)
    }

    fun panEnd(velocityX: Float, velocityY: Float) {
        touched()
        val vz = -velocityY * DRAG_RATE
        val vx = -velocityX * PAN_RATE
        // The stop nearest where the drag was heading.
        val aimZ = motion.camZ.value + vz * THROW
        val aimX = motion.camX.value + vx * THROW
        var best = stops[0]
        var score = Float.MAX_VALUE
        for (index in stops) {
            val to = framing(field[index], motion.width, motion.height, aimZ, false)
            val off = abs(to.z - aimZ) + abs(to.x - aimX) * 0.5f
            if (off < score) {
                score = off
                best = index
            }
        }
        flyTo(best, SNAP_SPRING_MS, SNAP_SPRING_RATIO, vx, vz)
        focusing?.invoke(cards[field[best].card])
    }

    fun tapAt(x: Float, y: Float) {
        touched()
        val switchX = motion.width - 20f - 22f
        val switchY = motion.height - motion.bottom - 30f
        if (hypot(x - switchX, y - switchY) < 30f) {
            turn(true)
            return
        }
        // The nearest card under the finger.
        val shown = frame(field, motion.camera(), motion.width, motion.height, motion.elapsed, if (reducedMotion) 0f else 1f)
        for (i in shown.indices.reversed()) {
            if (shown[i].alpha > 0.3f && inside(shown[i].quad, x, y)) {
                flyTo(shown[i].index, FLY_SPRING_MS, FLY_SPRING_RATIO, 0f, 0f)
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                focusing?.invoke(cards[field[shown[i].index].card])
                return
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Depth field gallery. Drag to fly through the cards; tap a card to fly to it"
                customActions = listOf(CustomAccessibilityAction("Turn the lights over") { turn(true); true })
            }
            .pointerInput(motion, reducedMotion) {
                val d = density.density
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)
                    var panning = false
                    var many = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (pressed.size > 1) {
                            // A drag takes one finger only; a second one ends it where it is.
                            if (panning) panEnd(0f, 0f)
                            panning = false
                            many = true
                        }
                        if (!change.pressed) {
                            tracker.addPointerInputChange(change)
                            if (panning) {
                                val velocity = tracker.calculateVelocity()
                                panEnd(velocity.x / d, velocity.y / d)
                            } else if (!many && change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                tapAt(change.position.x / d, change.position.y / d)
                            }
                            event.changes.forEach(PointerInputChange::consume)
                            break
                        }
                        if (many) continue
                        tracker.addPointerInputChange(change)
                        val tx = (change.position.x - down.position.x) / d
                        val ty = (change.position.y - down.position.y) / d
                        if (!panning && sqrt(tx * tx + ty * ty) >= DRAG_SLOP) {
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
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas {
                    drawField(it.nativeCanvas, motion, field, cards, paints, accent, chrome.value, reducedMotion)
                }
            }
        }
    }
}

private fun sansSerif(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        when {
            weight >= 600 -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            weight <= 300 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            else -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
    }

/** The prints, paints and fonts the picture reuses every frame. */
private class FieldPaints(cards: List<DepthCard>, accent: Color) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f
        typeface = sansSerif(500)
    }
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f
        typeface = sansSerif(300)
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 100f
        typeface = sansSerif(500)
    }
    val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 100f
        typeface = sansSerif(700)
    }
    val fade = Paint()
    val path = Path()
    val rect = RectF()
    val matrix = Matrix()
    val imageMatrix = Matrix()

    // The photos' prints: black and white, or black to lime, with a little more contrast.
    private fun tone(red: Float, green: Float, blue: Float): ColorMatrixColorFilter {
        val values = FloatArray(20)
        floatArrayOf(red, green, blue).forEachIndexed { row, c ->
            values[row * 5] = 0.2126f * CONTRAST * c
            values[row * 5 + 1] = 0.7152f * CONTRAST * c
            values[row * 5 + 2] = 0.0722f * CONTRAST * c
            // Android's colour matrix offsets are in 0…255, Skia's in 0…1.
            values[row * 5 + 4] = 0.5f * (1f - CONTRAST) * c * 255f
        }
        values[18] = 1f
        return ColorMatrixColorFilter(values)
    }

    val mono = tone(1f, 1f, 1f)
    val lime = tone(accent.red, accent.green, accent.blue)

    // The dots each card draws: a pattern, an icon, or none.
    val dots: List<List<Dot>> = cards.map { card ->
        when (card) {
            is DepthCard.Dots -> DotPatterns[card.pattern % DotPatterns.size]
            is DepthCard.Glyph -> dotted(card.glyph)
            else -> emptyList()
        }
    }
}

private fun Color.argb(alpha: Float = 1f) = copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()

// Skia's `measureText`, which the React Native version spaces letters by, gives a glyph's bounds
// rather than its advance, and on the iPhone it was made on those bounds stand clear of the ink:
// each letter there takes about two points more than its advance. This keeps the same spacing.
private const val GLYPH_PAD = 2f

/** A letter's advance as the React Native version measures it; a lone space is the gap between two letters. */
private fun advance(paint: Paint, letter: String): Float =
    if (letter != " ") paint.measureText(letter) + GLYPH_PAD else paint.measureText("a a") - paint.measureText("aa")

private fun trackedWidth(paint: Paint, text: String, tracking: Float): Float {
    var width = 0f
    for (letter in text) width += advance(paint, letter.toString()) + tracking
    return width - tracking
}

/** Draws text a letter at a time, `tracking` dp apart. */
private fun drawTracked(canvas: android.graphics.Canvas, text: String, x: Float, y: Float, tracking: Float, paint: Paint) {
    var at = x
    for (letter in text) {
        val s = letter.toString()
        canvas.drawText(s, at, y, paint)
        at += advance(paint, s) + tracking
    }
}

/** Text set at `size` from a 100 dp font, so it stays sharp at any scale. */
private fun drawSized(canvas: android.graphics.Canvas, text: String, paint: Paint, size: Float, x: Float, y: Float) {
    canvas.save()
    canvas.translate(x, y)
    canvas.scale(size / 100f, size / 100f)
    canvas.drawText(text, 0f, 0f, paint)
    canvas.restore()
}

/** A photo cropped to fill `rect`, printed, sampled smoothly at any size and faded in once it arrives. */
private fun drawPrint(canvas: android.graphics.Canvas, image: Bitmap, at: Float, rect: RectF, print: ColorMatrixColorFilter, now: Float, paints: FieldPaints) {
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val fit = max(rect.width() / iw, rect.height() / ih)
    val paint = paints.art
    paint.colorFilter = print
    paint.alpha = (clamp01((now - at) / IMAGE_FADE) * 255f).roundToInt()
    canvas.save()
    canvas.clipRect(rect)
    paints.imageMatrix.setScale(fit, fit)
    paints.imageMatrix.postTranslate(rect.centerX() - iw * fit / 2f, rect.centerY() - ih * fit / 2f)
    canvas.drawBitmap(image, paints.imageMatrix, paint)
    canvas.restore()
}

private fun drawPhoto(canvas: android.graphics.Canvas, loaded: Loaded, rect: RectF, print: ColorMatrixColorFilter, now: Float, sharper: Large?, paints: FieldPaints) {
    drawPrint(canvas, loaded.image, loaded.at, rect, print, now, paints)
    // The full-size copy, once it arrives, fades in over the field-sized one.
    if (sharper != null) drawPrint(canvas, sharper.image, sharper.at, rect, print, now, paints)
}

/** A card's face, w by h about its middle. */
private fun drawCard(
    canvas: android.graphics.Canvas,
    card: DepthCard,
    dots: List<Dot>,
    loaded: Loaded?,
    sharper: Large?,
    paints: FieldPaints,
    accent: Color,
    w: Float,
    h: Float,
    dark: Float,
    now: Float,
) {
    val corner = min(w, h) * 0.045f
    val rect = RectF(-w / 2f, -h / 2f, w / 2f, h / 2f)
    val fill = paints.fill
    fill.shader = null
    val ink = paints.ink

    when (card) {
        is DepthCard.Photo -> {
            fill.color = if (card.print == DepthPrint.Lime) accent.argb() else Color(0xFFD6D6D2).argb()
            canvas.drawRoundRect(rect, corner, corner, fill)
            if (loaded != null) {
                canvas.save()
                paints.path.reset()
                paints.path.addRoundRect(rect, corner, corner, Path.Direction.CW)
                canvas.clipPath(paints.path)
                drawPhoto(canvas, loaded, rect, if (card.print == DepthPrint.Lime) paints.lime else paints.mono, now, sharper, paints)
                canvas.restore()
            }
            return
        }
        is DepthCard.Poster -> {
            // A print in a brushed grey frame: two lines of type over a portrait, the wordmark below.
            fill.shader = LinearGradient(0f, -h / 2f, 0f, h / 2f, Color(0xFFE4E4E2).argb(), Color(0xFF8D8D8B).argb(), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, corner, corner, fill)
            fill.shader = null
            val inset = w * 0.07f
            val paper = RectF(-w / 2f + inset, -h / 2f + inset, w / 2f - inset, h / 2f - inset)
            fill.color = Color(0xFFFAFAF8).argb()
            canvas.drawRect(paper, fill)
            val text = paints.text
            text.color = Color(0xFF151515).argb()
            val size = paper.width() * 0.11f
            listOf(card.headline.first, card.headline.second).forEachIndexed { i, line ->
                drawSized(canvas, line, text, size, paper.left + paper.width() * 0.07f, paper.top + paper.width() * 0.07f + size * (0.9f + i * 1.05f))
            }
            val portrait = RectF(
                paper.left + paper.width() * 0.22f,
                paper.top + paper.height() * 0.36f,
                paper.left + paper.width() * 0.78f,
                paper.top + paper.height() * 0.86f,
            )
            if (loaded != null) drawPhoto(canvas, loaded, portrait, paints.mono, now, sharper, paints)
            val bold = paints.bold
            bold.color = Color(0xFF151515).argb()
            drawSized(canvas, "aloft", bold, paper.width() * 0.07f, paper.left + paper.width() * 0.74f, paper.top + paper.height() * 0.95f)
            return
        }
        is DepthCard.Dots -> {
            fill.color = accent.argb()
            canvas.drawRoundRect(rect, corner, corner, fill)
            ink.color = CardInk.argb()
            for (dot in dots) canvas.drawCircle(dot.x * w, dot.y * w, dot.r * w, ink)
            return
        }
        else -> Unit
    }

    if (card is DepthCard.Chip) {
        // A dark, faintly green photograph's worth of ground under two glassy rows.
        fill.shader = RadialGradient(-w * 0.15f, -h * 0.25f, max(w, h), Color(0xFF2C3329).argb(), Color(0xFF0B0C0B).argb(), Shader.TileMode.CLAMP)
    } else {
        fill.color = (if (card is DepthCard.Mark) MarkInk else CardInk).argb()
    }
    canvas.drawRoundRect(rect, corner, corner, fill)
    fill.shader = null
    if (dark > 0f) {
        // On the dark scene the dark cards keep a faint edge.
        paints.stroke.color = Color.White.argb(0.1f * dark)
        canvas.drawRoundRect(rect, corner, corner, paints.stroke)
    }
    when (card) {
        is DepthCard.Glyph -> {
            ink.color = DotInk.argb()
            for (dot in dots) canvas.drawCircle(dot.x * w, dot.y * w, dot.r * w, ink)
        }
        is DepthCard.Chip -> {
            listOf(card.first, card.second).forEachIndexed { i, row ->
                val pillTop = -h * 0.2f + i * h * 0.26f
                val pillHeight = h * 0.19f
                val pill = RectF(-w * 0.36f, pillTop, w * 0.36f, pillTop + pillHeight)
                fill.color = Color.White.argb(0.1f)
                canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, fill)
                val cy = pill.top + pillHeight / 2f
                ink.color = accent.argb()
                canvas.drawCircle(pill.left + pillHeight / 2f, cy, pillHeight * 0.32f, ink)
                val text = paints.text
                text.color = DotInk.argb()
                drawSized(canvas, row, text, pillHeight * 0.42f, pill.left + pillHeight * 1.05f, cy + pillHeight * 0.15f)
            }
        }
        is DepthCard.Mark -> {
            // The wordmark, with a square of dots after it.
            val bold = paints.bold
            bold.color = DotInk.argb()
            drawSized(canvas, card.word, bold, h * 0.32f, -w / 2f + w * 0.09f, h * 0.11f)
            ink.color = accent.argb()
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    canvas.drawCircle(w / 2f - w * 0.2f + col * w * 0.045f, -h * 0.09f + row * h * 0.09f, h * 0.022f, ink)
                }
            }
        }
        else -> Unit
    }
}

private fun drawField(
    canvas: android.graphics.Canvas,
    m: FieldMotion,
    field: List<Placement>,
    cards: List<DepthCard>,
    paints: FieldPaints,
    accent: Color,
    chromeIn: Float,
    reducedMotion: Boolean,
) {
    val width = m.width
    val height = m.height
    if (width <= 0f) return
    val now = m.elapsed
    val cam = m.camera()
    val t = m.theme.value
    val arrive = if (reducedMotion) 1f else easeOut(clamp01(now / 0.6f))
    // The switch, bottom right; the new scene spreads from its middle.
    val switchX = width - 20f - 22f
    val switchY = height - m.bottom - 30f
    val reach = hypot(max(switchX, width - switchX), max(switchY, height - switchY))
    // The new scene spreads from the switch; under reduced motion it fades in instead.
    val radius = if (reducedMotion) 0f else t * reach

    val fill = paints.fill
    fill.shader = null
    fill.color = (if (reducedMotion) mix(Light.bg, Dark.bg, t) else Light.bg).argb()
    canvas.drawRect(0f, 0f, width, height, fill)
    if (radius > 0f) {
        fill.color = Dark.bg.argb()
        canvas.drawCircle(switchX, switchY, radius, fill)
    }

    for (item in frame(field, cam, width, height, now, if (reducedMotion) 0f else 1f)) {
        val alpha = item.alpha * arrive
        if (alpha <= 0.004f) continue
        val p = field[item.index]
        if (alpha < 0.995f) {
            // Fading in or out: the card is drawn on its own layer, just big enough for it.
            val q = item.quad
            val left = min(min(q[0], q[2]), min(q[4], q[6]))
            val top = min(min(q[1], q[3]), min(q[5], q[7]))
            val right = max(max(q[0], q[2]), max(q[4], q[6]))
            val bottom = max(max(q[1], q[3]), max(q[5], q[7]))
            canvas.saveLayerAlpha(left - 2f, top - 2f, right + 2f, bottom + 2f, (alpha * 255f).roundToInt())
        } else {
            canvas.save()
        }
        paints.matrix.setValues(item.matrix)
        canvas.concat(paints.matrix)
        val large = m.large.lastOrNull { it.card == p.card }
        drawCard(canvas, cards[p.card], paints.dots[p.card], m.loaded[p.card], large, paints, accent, p.width * 100f, p.width * p.aspect * 100f, t, now)
        canvas.restore()
    }

    // Labels, the caption and the switch, in each scene's ink, cut by the spreading edge.
    val current = cards[field[m.focus].card]
    val last = cards[field[m.lastFocus].card]
    fun numbered(card: Int) = "${(card + 1).toString().padStart(2, '0')} / ${cards.size.toString().padStart(2, '0')}"
    val count = numbered(field[m.focus].card)
    val lastCount = numbered(field[m.lastFocus].card)
    val fresh = easeOut(clamp01((now - m.focusAt) / CAPTION_IN))

    fun paintChrome(palette: Palette, alpha: Float) {
        val fade = paints.fade
        fade.shader = LinearGradient(0f, 0f, 0f, m.top + EDGE_FADE, palette.bg.argb(0xD9 / 255f), palette.bg.argb(0f), Shader.TileMode.CLAMP)
        fade.alpha = (alpha * 255f).roundToInt()
        canvas.drawRect(0f, 0f, width, m.top + EDGE_FADE, fade)
        val low = height - m.bottom - EDGE_FADE - 40f
        fade.shader = LinearGradient(0f, low, 0f, height, palette.bg.argb(0f), palette.bg.argb(0xE6 / 255f), Shader.TileMode.CLAMP)
        fade.alpha = (alpha * 255f).roundToInt()
        canvas.drawRect(0f, low, width, height, fade)

        val label = paints.label
        val title = paints.title
        label.color = palette.ink.argb(alpha * chromeIn)
        val top = m.top + 26f
        drawTracked(canvas, "ALOFT STUDIO", 20f, top, 1f, label)
        drawTracked(canvas, "DEPTH FIELD", width / 2f - trackedWidth(label, "DEPTH FIELD", 1f) / 2f, top, 1f, label)
        // The card the camera is on, numbered top right and named bottom left: the new one rises
        // in as the last one rises away.
        val base = height - m.bottom - 22f
        fun caption(card: DepthCard, number: String, shown: Float, rise: Float) {
            if (shown <= 0.01f) return
            label.color = palette.ink.argb(alpha * chromeIn * shown)
            drawTracked(canvas, number, width - 20f - trackedWidth(label, number, 1f), top + rise * 0.5f, 1f, label)
            label.color = palette.ink.argb(alpha * chromeIn * shown * 0.6f)
            drawTracked(canvas, card.note.uppercase(), 20f, base - 36f + rise, 1f, label)
            title.color = palette.ink.argb(alpha * chromeIn * shown)
            canvas.drawText(card.title, 20f, base + rise, title)
        }
        if (last != current || lastCount != count) caption(last, lastCount, 1f - fresh, -fresh * CAPTION_RISE)
        caption(current, count, fresh, (1f - fresh) * CAPTION_RISE)

        // The switch: a track, and a lime knob that slides across as the lights turn over.
        val track = paints.stroke
        track.color = palette.ink.argb(0.5f * alpha * chromeIn)
        paints.rect.set(switchX - 22f, switchY - 12f, switchX + 22f, switchY + 12f)
        canvas.drawRoundRect(paints.rect, 12f, 12f, track)
        val knob = paints.ink
        knob.color = accent.argb(alpha * chromeIn)
        val knobX = switchX - 10f + 20f * t
        canvas.drawCircle(knobX, switchY, 8f, knob)
        knob.color = CardInk.argb()
        canvas.drawCircle(knobX, switchY, 2.2f, knob)
    }

    if (reducedMotion) {
        if (t < 1f) paintChrome(Light, 1f - t)
        if (t > 0f) paintChrome(Dark, t)
    } else {
        val circle = paints.path
        if (radius < reach) {
            canvas.save()
            circle.reset()
            circle.addCircle(switchX, switchY, max(0f, radius), Path.Direction.CW)
            canvas.clipOutPath(circle)
            paintChrome(Light, 1f)
            canvas.restore()
        }
        if (radius > 0f) {
            canvas.save()
            circle.reset()
            circle.addCircle(switchX, switchY, radius, Path.Direction.CW)
            canvas.clipPath(circle)
            paintChrome(Dark, 1f)
            canvas.restore()
        }
    }
}
