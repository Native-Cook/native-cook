package com.ngocdevv.nativecook.blocks.onboarding.curtainsplitwelcome

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
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

/** One scene: a two-line headline that is cut into the strips with the photo, a place and a photo. */
private class Scene(val line1: String, val line2: String, val place: String, val photo: String)

private fun photo(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=1200&q=80"

private val Scenes = listOf(
    Scene("Stay inside", "the drawing.", "Casa Ondara · Porto", photo("photo-1496865534669-25ec2a3a0fd3")),
    Scene("Concrete,", "softened.", "Halle Nord · Basel", photo("photo-1737442981890-0a87ef3ce9f5")),
    Scene("Light keeps", "the hours.", "Torre Luma · Valencia", photo("photo-1522404419647-18cb51cc5c7a")),
    Scene("Rooms with", "a spine.", "Maison Gris · Lyon", photo("photo-1536924491042-b0466800ce46")),
    Scene("Find your", "angle.", "Studio Okra · Kyoto", photo("photo-1543067361-9bf996edf6ff")),
)
private val COUNT = Scenes.size

private const val TITLE_SIZE = 40f
private const val TITLE_LEADING = 44f
private const val INDEX_SIZE = 56f
private const val PLACE_SIZE = 11f
private const val PLACE_TRACKING = 2.2f // dp between letters
private const val WHITE = 0xF6F4EF
private const val INK = 0x1A1916

// The blinds: six vertical strips. On the way to the next scene the even strips slide up and the
// odd ones down, each starting STAGGER of the transition after the one to its left, and while
// they move they part by up to GAP dp and darken, so the next scene shows between them.
private const val STRIPS = 6
private const val STAGGER = 0.09f
private const val GAP = 7f
private const val SHADE = 0.4f
private val StripEase = CubicBezierEasing(0.62f, 0f, 0.22f, 1f)
// The scene underneath starts a little larger and darker, and settles as it is uncovered.
private const val UNDER_SCALE = 0.08f
private const val UNDER_DIM = 0.55f

// Dragging: a full transition takes this share of the screen's height of finger travel.
private const val DRAG_SHARE = 0.55f
private const val DRAG_SLOP = 10f
private const val THROW_TIME = 0.25f // s of release speed added before choosing the scene
private const val FLICK = 1.1f // scenes per second: a release this fast turns the page whatever the distance
private const val RELEASE_MS = 650f
private const val RELEASE_RATIO = 1f
private const val TURN_MS = 1650f // the arrow and autoPlay
private val TurnEase = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)
private const val TAP_MS = 450L

// Layout, in dp.
private const val SIDE = 28f
private const val BUTTON = 60f

// autoPlay: one turn every PERIOD seconds, the first after a rest, so a LOOP of all five scenes
// starts and ends on the same picture.
private const val PERIOD = 3f
private const val FIRST_TURN = 1.3f
private val LOOP = PERIOD * COUNT
private const val USER_HOLD = 3.5f

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** How far strip `k` has gone, 0 at rest and 1 out of the screen, for a transition at `t`. */
private fun stripAt(t: Float, k: Int) = StripEase.transform(clamp01((t - k * STAGGER) / (1f - (STRIPS - 1) * STAGGER)))

/** `n` moved by whole cycles into [0, COUNT). */
private fun wrap(n: Int) = n.mod(COUNT)

/** JavaScript's Math.round: halves go up. */
private fun jsRound(x: Float) = floor(x + 0.5f)

/** True when the loop clock went past `at` between two frames (it may have wrapped). */
private fun crossed(before: Float, now: Float, at: Float) =
    if (now >= before) before < at && now >= at else at > before || at <= now

/** Reanimated's default timing curve, an ease-in-out quad. */
private fun inOutQuad(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f

// roundToInt() throws on NaN, so a NaN alpha draws nothing instead.
private fun argb(rgb: Int, alpha: Float) =
    ((if (alpha.isNaN()) 0 else (clamp01(alpha) * 255f).roundToInt()) shl 24) or (rgb and 0xFFFFFF)

/**
 * A value driven per frame the way Reanimated drives `withSpring({ duration, dampingRatio,
 * overshootClamping })` and `withTiming`, so the scenes move on the same curves as in the React
 * Native version.
 */
private class DrivenValue {
    // Read by the drawing, so it is drawn again as the value moves.
    private val state = mutableFloatStateOf(0f)
    var value: Float
        get() = state.floatValue
        set(next) {
            state.floatValue = next
        }
    private var mode = 0 // 0 still, 1 spring, 2 timing
    private var to = 0.0
    private var velocity = 0.0
    private var zeta = 0.0
    private var omega0 = 0.0
    private var omega1 = 0.0
    private var stiffness = 0.0
    private var initialEnergy = 0.0
    private var start = 0.0
    private var clamped = false
    private var from = 0f
    private var time = 0f
    private var duration = 0f
    private var curve: (Float) -> Float = ::inOutQuad

    fun stop() {
        mode = 0
        velocity = 0.0
    }

    fun springTo(target: Float, durationMs: Float, dampingRatio: Float, speed: Float, overshootClamping: Boolean) {
        var v = speed.toDouble()
        // Speed that would carry the first frame away from the target is dropped.
        if ((target > value && v < 0) || (target < value && v > 0)) v = 0.0
        val x0 = (value - target).toDouble()
        mode = 1
        to = target.toDouble()
        velocity = v
        start = x0
        clamped = overshootClamping
        zeta = dampingRatio.toDouble()
        stiffness = stiffnessFor(x0, v, durationMs / 1000.0, zeta)
        omega0 = sqrt(stiffness)
        omega1 = if (zeta < 1) omega0 * sqrt(1 - zeta * zeta) else 0.0
        // Reanimated measures the starting energy with the speed it was given.
        initialEnergy = energy(x0, speed.toDouble())
    }

    fun timeTo(target: Float, durationMs: Float, easing: (Float) -> Float = ::inOutQuad) {
        mode = 2
        from = value
        to = target.toDouble()
        time = 0f
        duration = durationMs / 1000f
        curve = easing
        velocity = 0.0
    }

    fun step(dt: Float) {
        when (mode) {
            1 -> {
                if (initialEnergy == 0.0 || !initialEnergy.isFinite()) {
                    value = to.toFloat()
                    mode = 0
                    return
                }
                val x0 = value - to
                val v0 = velocity
                val t = dt.toDouble()
                var next: Double
                if (zeta < 1) {
                    val sin1 = sin(omega1 * t)
                    val cos1 = cos(omega1 * t)
                    val envelope = exp(-zeta * omega0 * t)
                    val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
                    next = to + frag
                    velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
                } else {
                    val envelope = exp(-omega0 * t)
                    next = to + envelope * (x0 + (v0 + omega0 * x0) * t)
                    velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
                }
                // No overshoot: the spring stops once it would pass its target.
                val left = if (start >= 0) to else to + start
                val passed = clamped && (next < left || next > left + abs(start))
                if (passed || energy(to - next, velocity) / initialEnergy <= ENERGY_THRESHOLD) {
                    next = to
                    velocity = 0.0
                    mode = 0
                }
                value = next.toFloat()
            }
            2 -> {
                time += dt
                val p = clamp01(time / duration)
                value = from + (to.toFloat() - from) * curve(p)
                if (p >= 1f) mode = 0
            }
        }
    }

    private fun energy(x: Double, v: Double) = 0.5 * stiffness * x * x + 0.5 * v * v

    companion object {
        const val ENERGY_THRESHOLD = 6e-9

        /** The stiffness Reanimated solves for so a spring of mass 1 settles in 1.5 × the duration. */
        fun stiffnessFor(x0: Double, v0: Double, duration: Double, zeta: Double): Double {
            if (x0 == 0.0 && v0 == 0.0) return 0.0
            val settling = duration * 1.5
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
            return if (current.isFinite()) current else 170.0
        }
    }
}

/** Everything the frame loop, the gestures and the picture share, kept off Compose state. */
private class Motion {
    // Where we are, in scenes: the whole part is the scene on top, the rest how far its strips
    // have gone. It only grows or shrinks by one per turn and is folded back into one cycle.
    val position = DrivenValue()
    val press = DrivenValue()
    var clock = 0f
    var looping = false
    var hold = 0f
    var reduced = false
    var dragFrom = 0f
    var width = 1f
    var height = 1f
    var bottom = 0f

    /** Folds the position back into the first cycle when it rests on a whole scene. */
    fun fold() {
        val p = position.value
        if (abs(p - jsRound(p)) < 0.001f) position.value = wrap(jsRound(p).toInt()).toFloat()
    }

    fun turn(step: Int) {
        fold()
        val target = jsRound(position.value) + step
        position.timeTo(target, if (reduced) 450f else TURN_MS, TurnEase::transform)
    }

    fun onButton(x: Float, y: Float) = x > width - SIDE - BUTTON - 10f && y > height - bottom - 30f - BUTTON - 10f

    fun frame(dt: Float) {
        frameCallback(dt)
        position.step(dt)
        press.step(dt)
        // The picture turns the position into a scene index and an alpha; NaN would throw there.
        if (!position.value.isFinite()) {
            position.stop()
            position.value = 0f
        }
    }

    private fun frameCallback(dt: Float) {
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f) clock = 0f
            return
        }
        if (!looping) return
        val before = clock
        val now = (before + dt) % LOOP
        clock = now
        for (k in 0 until COUNT) {
            if (crossed(before, now, FIRST_TURN + k * PERIOD)) turn(1)
        }
    }
}

/** The photos as they arrive; read by the drawing so a photo shows once it is in. */
private class Photos {
    private val version = mutableFloatStateOf(0f)
    private val bitmaps = arrayOfNulls<Bitmap>(COUNT)

    operator fun get(index: Int): Bitmap? {
        version.floatValue
        return bitmaps[index]
    }

    operator fun set(index: Int, bitmap: Bitmap) {
        bitmaps[index] = bitmap
        version.floatValue += 1f
    }
}

private fun sans(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        when {
            weight >= 700 -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.SANS_SERIF
        }
    }

/** Paints for one screen density. Text is drawn in px; everything else in dp. */
private class Paints(val density: Float) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = argb(INK, 1f)
    }
    val imageMatrix = Matrix()
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sans(600)
        textSize = TITLE_SIZE * density
    }
    val index = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sans(500)
        textSize = INDEX_SIZE * density
    }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sans(700)
        textSize = PLACE_SIZE * density
    }
    val total = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sans(500)
        textSize = 15f * density
        fontFeatureSettings = "tnum"
    }
    val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sans(700)
        textSize = 12f * density
        letterSpacing = 4.5f / 12f
    }
    val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sans(600)
        textSize = 11f * density
        letterSpacing = 0.4f / 11f
    }
    val arrow = Path().apply {
        moveTo(11f, 19f)
        lineTo(11f, 3f)
        moveTo(4.5f, 9.5f)
        lineTo(11f, 3f)
        lineTo(17.5f, 9.5f)
    }

    /** The place names in tracked capitals, laid out glyph by glyph: each glyph's x in px. */
    val places: List<Pair<String, FloatArray>> = Scenes.map { scene ->
        val text = scene.place.uppercase()
        val widths = FloatArray(text.length)
        small.getTextWidths(text, widths)
        var x = SIDE * density
        text to FloatArray(text.length) { k -> x.also { x += widths[k] + PLACE_TRACKING * density } }
    }

    private var shadeFor = -1f
    private var shade: Shader? = null

    /** Shade at the top for the number and at the bottom for the headline. */
    fun shade(height: Float): Shader = shade?.takeIf { shadeFor == height } ?: LinearGradient(
        0f, 0f, 0f, height,
        intArrayOf(argb(0, 0x73 / 255f), argb(0, 0f), argb(0, 0f), argb(0, 0xC4 / 255f)),
        floatArrayOf(0f, 0.26f, 0.48f, 1f),
        Shader.TileMode.CLAMP,
    ).also {
        shade = it
        shadeFor = height
    }
}

/**
 * A welcome screen of architecture photos cut into six vertical strips. Turning to the next scene
 * slides the strips away like blinds, alternately up and down and one after another, and the
 * headline, cut by the same strips, goes with them. Dragging up or down scrubs the turn.
 *
 * @param autoPlay Turns to the next scene every few seconds, round and round.
 * @param onChange Called with the scene that comes into view after a swipe or a tap on the arrow.
 */
@Composable
fun CurtainSplitWelcome(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onChange: ((Int) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val changed by rememberUpdatedState(onChange)
    val motion = remember { Motion() }
    val photos = remember { Photos() }
    val paints = remember(density.density) { Paints(density.density) }

    // Read during composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    SideEffect { motion.reduced = reduced }

    LaunchedEffect(motion, autoPlay) {
        motion.looping = autoPlay
        motion.clock = 0f
        motion.hold = 0f
    }

    LaunchedEffect(motion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) motion.frame(dt)
            }
        }
    }

    LaunchedEffect(photos, context) {
        val loader = SingletonImageLoader.get(context)
        Scenes.forEachIndexed { i, scene ->
            launch {
                val request = ImageRequest.Builder(context).data(scene.photo).build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                photos[i] = result.image.toBitmap()
            }
        }
    }

    fun commit(index: Int) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        changed?.invoke(index)
    }

    fun release(speed: Float) {
        val m = motion
        val from = jsRound(m.dragFrom)
        val p = m.position.value
        // A flick goes on to the next whole scene in its direction; a slow release to the nearest.
        var thrown = jsRound(p + speed * THROW_TIME)
        if (speed > FLICK) thrown = floor(p + 0.001f) + 1f
        if (speed < -FLICK) thrown = ceil(p - 0.001f) - 1f
        val target = max(from - 1f, min(from + 1f, thrown))
        m.position.springTo(target, RELEASE_MS, RELEASE_RATIO, speed, overshootClamping = true)
        if (target != from) commit(wrap(target.toInt()))
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = Scenes.joinToString(", ") { "${it.line1} ${it.line2}" } }
            .pointerInput(motion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val d = density.density
                    val start = down.position
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    // One recogniser stands in for the race of a pan (after 10 dp up or down) and a
                    // tap (a touch lifted within 450 ms that never turned into a pan).
                    var panning = false
                    var released = false
                    motion.hold = USER_HOLD
                    if (motion.onButton(start.x / d, start.y / d)) motion.press.timeTo(1f, 100f)
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val dy = (change.position.y - start.y) / d
                        if (!change.pressed) {
                            released = true
                            if (panning) {
                                change.consume()
                                release(-tracker.calculateVelocity().y / d / (motion.height * DRAG_SHARE))
                            } else {
                                motion.press.timeTo(0f, 180f)
                                val x = change.position.x / d
                                val y = change.position.y / d
                                if (change.uptimeMillis - down.uptimeMillis <= TAP_MS && motion.onButton(x, y)) {
                                    motion.turn(1)
                                    commit(wrap(jsRound(motion.position.value).toInt() + 1))
                                }
                            }
                            continue
                        }
                        if (!panning && abs(dy) >= DRAG_SLOP) {
                            panning = true
                            motion.press.timeTo(0f, 180f)
                            motion.position.stop()
                            motion.fold()
                            motion.dragFrom = motion.position.value
                        }
                        if (panning) {
                            change.consume()
                            motion.hold = USER_HOLD
                            val from = motion.dragFrom
                            val moved = -dy / (motion.height * DRAG_SHARE)
                            // One scene per drag, either way.
                            motion.position.value = max(jsRound(from) - 1f, min(jsRound(from) + 1f, from + moved))
                        }
                    }
                    // A finger lost to the system still lets go of the strips.
                    if (!released) {
                        motion.press.timeTo(0f, 180f)
                        if (panning) release(0f)
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        SideEffect {
            motion.width = width
            motion.height = max(height, 1f)
            motion.bottom = safeBottom
        }
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas {
                val canvas = it.nativeCanvas
                canvas.save()
                canvas.scale(density.density, density.density)
                drawCurtain(canvas, motion.position.value, motion.press.value, width, height, safeTop, safeBottom, paints, photos, reduced)
                canvas.restore()
            }
        }
    }
}

/** The scene underneath settling in, the strips of the one on top, and the chrome over them. */
private fun drawCurtain(
    canvas: android.graphics.Canvas,
    position: Float,
    press: Float,
    width: Float,
    height: Float,
    top: Float,
    bottom: Float,
    paints: Paints,
    photos: Photos,
    reduced: Boolean,
) {
    val whole = floor(position)
    val phase = position - whole
    val upper = wrap(whole.toInt())
    val line2Y = height - bottom - 34f
    val lines = SceneLines(
        indexY = top + 30f + INDEX_SIZE * 0.72f,
        placeY = line2Y - TITLE_LEADING - TITLE_SIZE * 0.72f - 22f,
        line1Y = line2Y - TITLE_LEADING,
        line2Y = line2Y,
    )

    if (phase < 1e-6f) {
        // At rest the strips are closed up: the scene on top is all there is.
        drawScene(canvas, upper, 1f, width, height, lines, paints, photos)
    } else {
        canvas.save()
        val grow = if (reduced) 1f else 1f + UNDER_SCALE * (1f - StripEase.transform(phase))
        canvas.scale(grow, grow, width / 2f, height / 2f)
        drawScene(canvas, wrap(whole.toInt() + 1), 1f, width, height, lines, paints, photos)
        canvas.restore()
        paints.fill.shader = null
        paints.fill.color = argb(0, UNDER_DIM * (1f - phase))
        canvas.drawRect(0f, 0f, width, height, paints.fill)

        // Under reduced motion the strips stay in place and the scene on top fades out instead.
        val alpha = if (reduced) 1f - phase else 1f
        val column = width / STRIPS
        for (k in 0 until STRIPS) {
            val travel = if (reduced) 0f else stripAt(phase, k)
            val direction = if (k % 2 == 0) -1f else 1f
            val gap = GAP * sin(PI.toFloat() * travel)
            canvas.save()
            // The clip travels with the strip, since it is set in the strip's moved coordinates.
            canvas.translate(0f, direction * (height + 40f) * travel)
            canvas.clipRect(k * column + gap / 2f, 0f, (k + 1) * column - gap / 2f, height)
            drawScene(canvas, upper, alpha, width, height, lines, paints, photos)
            val shade = SHADE * sin(PI.toFloat() / 2f * travel)
            if (shade > 0f) {
                paints.fill.color = argb(0, shade * alpha)
                canvas.drawRect(0f, 0f, width, height, paints.fill)
            }
            canvas.restore()
        }
    }

    // The chrome stays put while the scenes turn behind it; text is drawn in px.
    val d = paints.density
    canvas.save()
    canvas.scale(1f / d, 1f / d)
    val totalMetrics = paints.total.fontMetrics
    paints.total.color = argb(WHITE, 0.7f)
    canvas.drawText("/ 0$COUNT", (SIDE + 74f) * d, (top + 30f + 4f) * d - totalMetrics.top, paints.total)
    val markMetrics = paints.mark.fontMetrics
    paints.mark.color = argb(WHITE, 1f)
    canvas.drawText("PLINTH", (width - SIDE) * d - paints.mark.measureText("PLINTH"), (top + 30f + 6f) * d - markMetrics.top, paints.mark)
    val hintMetrics = paints.hint.fontMetrics
    paints.hint.color = argb(WHITE, 0.7f)
    val hintWidth = paints.hint.measureText("Swipe up")
    canvas.drawText(
        "Swipe up",
        (width - SIDE - BUTTON / 2f) * d - hintWidth / 2f,
        (height - bottom - 30f - BUTTON - 14f) * d - hintMetrics.bottom,
        paints.hint,
    )
    canvas.restore()

    val cx = width - SIDE - BUTTON / 2f
    val cy = height - bottom - 30f - BUTTON / 2f
    val scale = 1f - 0.07f * press
    canvas.save()
    canvas.scale(scale, scale, cx, cy)
    paints.fill.color = argb(WHITE, 1f)
    canvas.drawCircle(cx, cy, BUTTON / 2f, paints.fill)
    canvas.translate(cx - 11f, cy - 11f)
    canvas.drawPath(paints.arrow, paints.stroke)
    canvas.restore()
}

private class SceneLines(val indexY: Float, val placeY: Float, val line1Y: Float, val line2Y: Float)

/** What one scene draws: its photo, the shade for the text, the number, the place and the headline. */
private fun drawScene(
    canvas: android.graphics.Canvas,
    index: Int,
    alpha: Float,
    width: Float,
    height: Float,
    lines: SceneLines,
    paints: Paints,
    photos: Photos,
) {
    val fill = paints.fill
    fill.shader = null
    fill.color = argb(INK, alpha)
    canvas.drawRect(0f, 0f, width, height, fill)
    val bitmap = photos[index]
    if (bitmap != null) {
        val iw = bitmap.width.toFloat()
        val ih = bitmap.height.toFloat()
        val fit = max(width / iw, height / ih)
        paints.imageMatrix.setScale(fit, fit)
        paints.imageMatrix.postTranslate((width - iw * fit) / 2f, (height - ih * fit) / 2f)
        paints.art.alpha = if (alpha.isNaN()) 0 else (clamp01(alpha) * 255f).roundToInt()
        canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        canvas.drawBitmap(bitmap, paints.imageMatrix, paints.art)
        canvas.restore()
    }
    fill.shader = paints.shade(height)
    fill.color = argb(0, alpha)
    canvas.drawRect(0f, 0f, width, height, fill)
    fill.shader = null

    val d = paints.density
    val scene = Scenes[index]
    canvas.save()
    canvas.scale(1f / d, 1f / d)
    paints.index.color = argb(WHITE, alpha)
    canvas.drawText("0${index + 1}", (SIDE - 3f) * d, lines.indexY * d, paints.index)
    val (place, xs) = paints.places[index]
    paints.small.color = argb(WHITE, 0.78f * alpha)
    for (k in place.indices) canvas.drawText(place, k, k + 1, xs[k], lines.placeY * d, paints.small)
    paints.title.color = argb(WHITE, alpha)
    canvas.drawText(scene.line1, (SIDE - 2f) * d, lines.line1Y * d, paints.title)
    canvas.drawText(scene.line2, (SIDE - 2f) * d, lines.line2Y * d, paints.title)
    canvas.restore()
}
