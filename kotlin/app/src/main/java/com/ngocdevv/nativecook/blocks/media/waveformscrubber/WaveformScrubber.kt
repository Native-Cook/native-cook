package com.ngocdevv.nativecook.blocks.media.waveformscrubber

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.launch
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

class WaveformTrack(
    val title: String,
    val artist: String,
    val album: String,
    /** Square cover art, also blurred behind the player. */
    val cover: String,
    val backdrop: String,
    /** Length of the track, in seconds. */
    val duration: Double,
)

private fun unsplash(id: String, w: Int, h: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$w&h=$h&q=80"
private const val PHOTO = "photo-1642019341061-f81d61659c62"

private val Track = WaveformTrack(
    title = "Afterglow Avenue",
    artist = "Juno Vale",
    album = "Night Transit",
    cover = unsplash(PHOTO, 960, 960),
    // Drawn under a heavy blur, so a small file is enough.
    backdrop = unsplash(PHOTO, 480, 960),
    duration = 240.0,
)

private val Ink = Color(0xFFF4F6FB)
private val BaseBar = Color(236 / 255f, 242 / 255f, 1f, 0.30f)
private fun mist(alpha: Float) = Color(236 / 255f, 242 / 255f, 1f, alpha)

// The waveform: BARS rounded bars across the screen less SIDE dp each side. A bar is BAR_FILL of
// its slot wide and at most BAR_HALF dp either side of the axis at rest.
private const val BARS = 60
private const val SIDE = 28.0
private const val BAR_FILL = 0.56
private const val BAR_HALF = 28.0
// Under the finger the bars grow by up to MAGNIFY (as a share of their height) and WIDEN (of their
// width), falling off over LENS_RADIUS dp like a bell curve: a loupe dragged along the track.
private const val MAGNIFY = 1.15
private const val WIDEN = 0.5
private const val LENS_RADIUS = 30.0
// The canvas: the bubble rides above the bars, the times sit under them.
private const val AXIS_Y = 116.0
private const val CANVAS_H = 206f
private const val TIMES_Y = 196f
private const val BUBBLE_H = 28.0
private const val BUBBLE_GAP = 12.0
private const val TAIL = 6.0

// While it plays each bar sways by up to DANCE of its height on two sines of its own, and the
// bars around the playhead throb on every beat at BPM.
private const val DANCE = 0.14
private const val BEAT = 0.22
private const val BPM = 104.0

// Dragging: the playhead follows the finger through a quick low-pass (FOLLOW per second). On
// release it is thrown THROW s ahead by the finger's speed and lands on the nearest bar's start
// with a spring that overshoots a touch. Past either end it is held back with rubber banding.
private const val FOLLOW = 32.0
private const val THROW = 0.12
private const val SNAP_STIFFNESS = 170.0
private const val SNAP_DAMPING = 19.0
private const val RUBBER = 7.0
private const val SEEK_STEP = 10.0
// A selection tick each time a scrub crosses a TICK_EVERY s mark.
private const val TICK_EVERY = 30.0

// The play button and its icon morph.
private const val BUTTON = 76f
private const val MORPH_MS = 420.0
private const val MORPH_RATIO = 0.68
private const val COVER_MS = 560.0
private const val COVER_RATIO = 0.74
private const val COVER_PAUSED = 0.86

// autoPlay script (seconds into LOOP). It starts paused at REST_AT s into the track, plays,
// scrubs forward to SCRUB_TO and back to SCRUB_BACK, lets go (the playhead snaps to the start of
// that bar, 44 s), plays on and pauses again exactly at REST_AT, so one period loops cleanly.
private const val LOOP = 12.0
private const val REST_AT = 47.8
private const val PLAY_AT = 0.5
private const val GRAB_AT = 3.4
private const val FORWARD_END = 5.3
private const val BACK_START = 5.7
private const val BACK_END = 7.3
private const val RELEASE_AT = 7.5
private const val PAUSE_AT = 11.3
private const val SCRUB_TO = 190.0
private const val SCRUB_BACK = 45.6
// A touch stops the script for USER_HOLD s, then the player settles back and the loop restarts.
private const val USER_HOLD = 4.0
private const val SETTLE_LEAD = 0.9

private const val IMAGE_FADE = 0.3

// The icon morph: a play triangle cut in two halves at its middle, and the two bars of pause,
// each four corners in the same order (top left, top right, bottom right, bottom left), centred
// on the button.
private val PlayLeft = doubleArrayOf(-7.0, -13.0, 3.5, -7.2, 3.5, 7.2, -7.0, 13.0)
private val PlayRight = doubleArrayOf(3.5, -7.2, 14.0, 0.0, 14.0, 0.0, 3.5, 7.2)
private val PauseLeft = doubleArrayOf(-10.0, -12.0, -3.0, -12.0, -3.0, 12.0, -10.0, 12.0)
private val PauseRight = doubleArrayOf(3.0, -12.0, 10.0, -12.0, 10.0, 12.0, 3.0, 12.0)
private const val ICON_CORNER = 2.6

/** cubic-bezier(0.65, 0, 0.35, 1), as the script eases its scrubs. */
private val EaseInOut = androidx.compose.animation.core.CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** Loudness of each bar, 0…1: a quiet intro, two choruses around a dip, and an outro. */
private val Levels = DoubleArray(BARS) { i ->
    val t = i / (BARS - 1.0)
    val shape = 0.32 +
        0.5 * exp(-((t - 0.36) / 0.16).pow(2)) +
        0.58 * exp(-((t - 0.74) / 0.14).pow(2)) +
        0.18 * sin(t * 9.2) * (1 - t)
    // A fixed hash, so the track looks the same every time.
    val grain = (sin(i * 127.1 + 311.7) * 43758.5453) % 1.0
    val jitter = 0.62 + 0.38 * abs(grain)
    min(1.0, max(0.14, shape * jitter * min(1.0, 0.35 + t * 5)))
}

/** 3:07 for 187 s. */
private fun clock(seconds: Double): String {
    val s = max(0.0, floor(seconds)).toInt()
    val r = s % 60
    return "${s / 60}:${if (r < 10) "0" else ""}$r"
}

/** Distance past an edge, rubber-banded: it gives a little at first and less and less after. */
private fun rubber(over: Double, limit: Double) = limit * (1 - 1 / (1 + over / limit))

private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

private fun quadInOut(t: Double) = if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2

/**
 * A value Reanimated would animate on its own: `withSpring({ duration, dampingRatio })`, with the
 * stiffness solved so the energy falls to the threshold after 1.5 × the duration, or a timing on
 * the default ease-in-out.
 */
private class WaveDriven(initial: Double) {
    var value = initial
        private set
    private var mode = 0 // 0 still, 1 spring, 2 timing
    private var to = 0.0
    private var velocity = 0.0
    private var zeta = 0.0
    private var omega0 = 0.0
    private var omega1 = 0.0
    private var stiffness = 0.0
    private var initialEnergy = 0.0
    private var from = 0.0
    private var time = 0.0
    private var duration = 0.0

    fun springTo(target: Double, durationMs: Double, dampingRatio: Double, speed: Double = 0.0) {
        var v = (if (mode == 1) velocity else 0.0) + speed
        // Velocity that would carry the first frame away from the target is dropped.
        if ((target > value && v < 0) || (target < value && v > 0)) v = 0.0
        val x0 = value - target
        mode = 1
        to = target
        velocity = v
        zeta = dampingRatio
        stiffness = stiffnessFor(x0, v, durationMs, dampingRatio)
        omega0 = sqrt(stiffness)
        omega1 = if (zeta < 1) omega0 * sqrt(1 - zeta * zeta) else 0.0
        // Reanimated measures the starting energy with the configured velocity alone.
        initialEnergy = energy(x0, speed)
    }

    fun timeTo(target: Double, durationMs: Double) {
        mode = 2
        from = value
        to = target
        time = 0.0
        duration = durationMs / 1000
        velocity = 0.0
    }

    fun step(dt: Double) {
        when (mode) {
            1 -> {
                val x0 = value - to
                val v0 = velocity
                if (zeta < 1) {
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
                if (initialEnergy == 0.0 || energy(to - value, velocity) / initialEnergy <= ENERGY_THRESHOLD) {
                    value = to
                    velocity = 0.0
                    mode = 0
                }
            }
            2 -> {
                time += dt
                val p = min(1.0, time / duration)
                value = from + (to - from) * quadInOut(p)
                if (p >= 1) mode = 0
            }
        }
    }

    private fun energy(x: Double, v: Double) = 0.5 * stiffness * x * x + 0.5 * v * v

    companion object {
        const val ENERGY_THRESHOLD = 6e-9

        fun stiffnessFor(x0: Double, v0: Double, durationMs: Double, zeta: Double): Double {
            if (x0 == 0.0 && v0 == 0.0) return 0.0
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
            return current
        }
    }
}

/** Everything the frame loop, the gestures and the picture share, kept off Compose state. */
private class WaveMotion(val duration: Double) {
    // Bumped once a frame; the drawing reads it, so it redraws every frame.
    var frame by mutableLongStateOf(0L)
    var width = 0.0
    val left get() = SIDE
    val span get() = max(1.0, width - SIDE * 2)
    val slot get() = span / BARS

    // Position in the track, seconds. `playing` is what the button shows.
    var progress = REST_AT
    var playing = false
    // 0…1 eases toward playing, so the bars start and stop dancing softly.
    var energy = 0.0
    var time = 0.0
    // The finger on the waveform, in dp, and the loupe that follows it.
    var dragging = false
    var fingerX = 0.0
    var lensX = 0.0
    var lens = 0.0
    var scrubSpeed = 0.0
    var lastHead = REST_AT
    // The snap after a release: a spring on the playhead toward `snapTarget`.
    var snapping = false
    var snapTarget = REST_AT
    var snapVelocity = 0.0
    val morph = WaveDriven(0.0)
    val cover = WaveDriven(0.0)
    val press = WaveDriven(0.0)
    val nudge = WaveDriven(0.0)
    var byUser = false
    var scripted = false
    var loopClock = 0.0
    var looping = false
    var hold = 0.0
    var grabbedAt = REST_AT
    var lastTick = -1
    var coverAt = -1.0
    var backdropAt = -1.0

    /** Seconds under an x on the waveform, rubber-banded past either end. */
    fun timeAt(x: Double): Double {
        val raw = (x - left) / span * duration
        if (raw < 0) return -rubber(-raw, RUBBER)
        if (raw > duration) return duration + rubber(raw - duration, RUBBER)
        return raw
    }

    fun xOf(seconds: Double) = left + seconds / duration * span

    fun fade(at: Double) = if (at < 0) 0f else ((time - at) / IMAGE_FADE).coerceIn(0.0, 1.0).toFloat()
}

/** One photo, downloaded once. */
private class WavePhotos {
    var cover by mutableStateOf<Bitmap?>(null)
    var backdrop by mutableStateOf<Bitmap?>(null)
    // Below API 31 there is no blur effect: a small copy, blurred by hand, is stretched instead.
    var softBackdrop by mutableStateOf<Bitmap?>(null)
}

/**
 * A night-drive player over a blurred neon street: the cover art, the title, and a waveform of
 * sixty rounded bars. Dragging across the waveform scrubs it: the bars under the finger swell like
 * a loupe passing over them, played bars fill with the accent, and a bubble rides above the finger
 * with the time under it. Letting go snaps the playhead to the start of a bar with a spring. The
 * play button morphs between play and pause, and while it plays the bars sway to the beat.
 *
 * @param autoPlay Plays, scrubs forward and back across the waveform, plays on and pauses, on a loop.
 * @param accent Colour of the played bars and the time bubble's text.
 * @param onSeek Called with the new position, in seconds, when a scrub is let go.
 */
@Composable
fun WaveformScrubber(
    modifier: Modifier = Modifier,
    track: WaveformTrack = Track,
    autoPlay: Boolean = false,
    accent: Pair<Color, Color> = Color(0xFFFF9A5C) to Color(0xFFFF3D7F),
    onSeek: ((Double) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val seeking by rememberUpdatedState(onSeek)
    // Every position is a share of the duration, and coerceIn(0, duration) throws below zero: a track
    // without a usable length plays as one second long.
    val motion = remember(track) { WaveMotion(if (track.duration.isFinite() && track.duration > 0) track.duration else 1.0) }
    val photos = remember(track) { WavePhotos() }
    val paints = remember { WavePaints() }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.loopClock = 0.0
        motion.hold = 0.0
    }

    LaunchedEffect(track, context) {
        val loader = SingletonImageLoader.get(context)
        suspend fun load(address: String): Bitmap? {
            val request = ImageRequest.Builder(context).data(address).allowHardware(false).build()
            return (loader.execute(request) as? SuccessResult)?.image?.toBitmap()
        }
        launch {
            photos.cover = load(track.cover)
            motion.coverAt = motion.time
        }
        launch {
            val image = load(track.backdrop) ?: return@launch
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) photos.softBackdrop = softened(image)
            photos.backdrop = image
            motion.backdropAt = motion.time
        }
    }

    fun impact() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    fun setPlaying(on: Boolean, user: Boolean) {
        val m = motion
        m.playing = on
        if (reducedMotion) {
            m.morph.timeTo(if (on) 1.0 else 0.0, 160.0)
            m.cover.timeTo(if (on) 1.0 else 0.0, 200.0)
        } else {
            m.morph.springTo(if (on) 1.0 else 0.0, MORPH_MS, MORPH_RATIO)
            m.cover.springTo(if (on) 1.0 else 0.0, COVER_MS, COVER_RATIO)
        }
        if (user) impact()
    }

    fun snapTo(seconds: Double, velocity: Double) {
        motion.snapTarget = seconds.coerceIn(0.0, motion.duration)
        motion.snapVelocity = velocity
        motion.snapping = true
    }

    fun beginScrub(x: Double, user: Boolean) {
        val m = motion
        m.byUser = user
        m.scripted = !user
        m.snapping = false
        m.dragging = true
        m.fingerX = x
        // The loupe opens where the finger lands rather than sliding in from the last scrub.
        if (m.lens < 0.05) m.lensX = x
        m.scrubSpeed = 0.0
        m.lastHead = m.progress
    }

    fun endScrub(velocityX: Double, user: Boolean) {
        val m = motion
        if (!m.dragging) return
        m.dragging = false
        m.scripted = false
        val throwBy = velocityX / m.span * m.duration * THROW
        val landing = (m.timeAt(m.fingerX) + throwBy).coerceIn(0.0, m.duration)
        // The start of the bar the playhead lands in.
        val barSeconds = m.duration / BARS
        val target = floor(landing / barSeconds + 0.5) * barSeconds
        if (reducedMotion) {
            m.progress = target
            m.snapping = false
        } else {
            snapTo(target, m.scrubSpeed)
        }
        if (user) {
            impact()
            seeking?.invoke(target)
        }
    }

    fun touched() {
        motion.hold = USER_HOLD
        // A scripted scrub in flight is handed over to the finger.
        if (motion.scripted) {
            motion.scripted = false
            motion.dragging = false
        }
    }

    fun seekBy(step: Double) {
        touched()
        val m = motion
        val from = if (m.snapping) m.snapTarget else m.progress
        if (reducedMotion) m.progress = (from + step).coerceIn(0.0, m.duration) else snapTo(from + step, 0.0)
        m.nudge.springTo(0.0, 380.0, 0.5, if (step > 0) 260.0 else -260.0)
        impact()
    }

    /** Where the scripted finger is, in seconds of the track, `t` s into the loop. */
    fun scriptAt(t: Double, from: Double): Double = when {
        t < FORWARD_END -> lerp(from, SCRUB_TO, EaseInOut.transform(((t - GRAB_AT) / (FORWARD_END - GRAB_AT)).toFloat()).toDouble())
        t < BACK_START -> SCRUB_TO + 1.6 * sin((t - FORWARD_END) / (BACK_START - FORWARD_END) * Math.PI)
        t < BACK_END -> lerp(SCRUB_TO, SCRUB_BACK, EaseInOut.transform(((t - BACK_START) / (BACK_END - BACK_START)).toFloat()).toDouble())
        else -> SCRUB_BACK
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        val m = motion
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1.0 / 60 else min((now - last) / 1e9, 0.064)
                last = now
                if (dt <= 0) return@withFrameNanos
                // Reanimated's own animations run whatever the waveform is doing.
                m.morph.step(dt)
                m.cover.step(dt)
                m.press.step(dt)
                m.nudge.step(dt)
                m.frame += 1
                if (m.span <= 1) return@withFrameNanos
                m.time += dt

                // The script, while nobody is touching.
                if (m.hold > 0) {
                    m.hold -= dt
                    if (m.hold <= 0 && m.looping) {
                        // Settle back to the resting state, then start the loop over.
                        if (m.playing) setPlaying(on = false, user = false)
                        snapTo(REST_AT, 0.0)
                        m.loopClock = -SETTLE_LEAD
                    }
                } else if (m.looping) {
                    val before = m.loopClock
                    var t = before + dt
                    if (t >= LOOP) {
                        t -= LOOP
                        // At the seam the player is paused at REST_AT; any drift is cleared without a visible jump.
                        if (!m.playing && !m.dragging && !m.snapping) m.progress = REST_AT
                    }
                    m.loopClock = t
                    fun crossed(at: Double) = before < at && t >= at
                    if (crossed(PLAY_AT) && !m.playing) setPlaying(on = true, user = false)
                    if (crossed(GRAB_AT)) {
                        m.grabbedAt = m.progress
                        beginScrub(m.xOf(m.progress), false)
                    }
                    if (m.scripted && t >= GRAB_AT && t < RELEASE_AT) m.fingerX = m.xOf(scriptAt(t, m.grabbedAt))
                    if (crossed(RELEASE_AT) && m.scripted) endScrub(0.0, false)
                    if (crossed(PAUSE_AT) && m.playing) setPlaying(on = false, user = false)
                }

                // The playhead.
                val head = m.progress
                if (m.dragging) {
                    val target = m.timeAt(m.fingerX)
                    m.progress = head + (target - head) * (1 - exp(-dt * FOLLOW))
                    // The playhead's own speed, in s/s, carried into the snap spring on release.
                    val speed = (m.progress - m.lastHead) / dt
                    m.scrubSpeed += (speed - m.scrubSpeed) * 0.4
                    m.lastHead = m.progress
                } else if (m.snapping) {
                    if (m.playing) m.snapTarget = min(m.duration, m.snapTarget + dt)
                    val v = m.snapVelocity + (SNAP_STIFFNESS * (m.snapTarget - head) - SNAP_DAMPING * m.snapVelocity) * dt
                    m.snapVelocity = v
                    m.progress = head + v * dt
                    if (abs(m.snapTarget - m.progress) < 0.01 && abs(v) < 0.4) {
                        m.progress = m.snapTarget
                        m.snapping = false
                    }
                } else if (m.playing) {
                    val next = head + dt
                    if (next >= m.duration) {
                        m.progress = m.duration
                        setPlaying(on = false, user = false)
                    } else {
                        m.progress = next
                    }
                }

                val wantEnergy = if (m.playing && !reducedMotion) (if (m.dragging) 0.35 else 1.0) else 0.0
                m.energy += (wantEnergy - m.energy) * (1 - exp(-dt * 4))
                val wantLens = if (m.dragging) 1.0 else 0.0
                m.lens += (wantLens - m.lens) * (1 - exp(-dt * (if (m.dragging) 16 else 9)))
                m.lensX += (m.fingerX - m.lensX) * (1 - exp(-dt * 26))

                // A tick each time a scrub by hand crosses a half-minute mark: detents on the track.
                val tick = if (m.dragging && m.byUser) floor(m.progress.coerceIn(0.0, m.duration) / TICK_EVERY).toInt() else -1
                if (tick >= 0 && m.lastTick >= 0 && tick != m.lastTick) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                m.lastTick = tick
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF05070C))) {
        val width = maxWidth.value
        val height = maxHeight.value
        motion.width = width.toDouble()
        val coverSize = max(0f, min(width - 80f, height * 0.36f))

        Backdrop(photos, motion)

        Column(Modifier.fillMaxSize().padding(top = (safeTop + 6f).dp, bottom = (safeBottom + 14f).dp)) {
            TopBar(track.album)

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Cover(photos, motion, coverSize, reducedMotion, track.album)
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = SIDE.toFloat().dp).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicText(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, fontFamily = FontFamily.SansSerif),
                )
                BasicText(
                    track.artist,
                    maxLines = 1,
                    style = TextStyle(color = mist(0.62f), fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif),
                )
            }

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .pullUp(14.dp)
                    .height(CANVAS_H.dp)
                    .semantics {
                        contentDescription = "Track position"
                        stateDescription = clock(motion.progress.coerceIn(0.0, motion.duration))
                    }
                    .pointerInput(motion) {
                        val tracker = VelocityTracker()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            tracker.resetTracking()
                            tracker.addPointerInputChange(down)
                            touched()
                            beginScrub((down.position.x / density.density).toDouble(), true)
                            var velocity = 0.0
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                change.consume()
                                if (!change.pressed) {
                                    velocity = tracker.calculateVelocity().x / density.density.toDouble()
                                    break
                                }
                                if (motion.dragging && motion.byUser) motion.fingerX = (change.position.x / density.density).toDouble()
                            }
                            if (motion.byUser) endScrub(velocity, true)
                        }
                    },
            ) {
                motion.frame
                scale(density.density, density.density, pivot = Offset.Zero) {
                    drawWave(motion, accent, reducedMotion, paints)
                }
            }

            Row(
                Modifier.fillMaxWidth().height(92.dp),
                horizontalArrangement = Arrangement.spacedBy(44.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SeekButton(-1, motion, "Back 10 seconds") { seekBy(-SEEK_STEP) }
                Canvas(
                    Modifier
                        .size(BUTTON.dp)
                        .graphicsLayer {
                            motion.frame
                            val s = (1 - 0.07 * motion.press.value).toFloat()
                            scaleX = s
                            scaleY = s
                        }
                        .semantics { contentDescription = "Play or pause" }
                        .pointerInput(motion) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                touched()
                                motion.press.timeTo(1.0, 110.0)
                                var success = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) {
                                        val p = change.position
                                        val inside = p.x >= 0 && p.y >= 0 && p.x <= size.width && p.y <= size.height
                                        success = inside && change.uptimeMillis - down.uptimeMillis <= 600
                                        break
                                    }
                                }
                                motion.press.springTo(0.0, 320.0, 0.6)
                                if (success) setPlaying(!motion.playing, true)
                            }
                        },
                ) {
                    motion.frame
                    scale(density.density, density.density, pivot = Offset.Zero) {
                        drawPlayButton(motion.morph.value, paints)
                    }
                }
                SeekButton(1, motion, "Forward 10 seconds") { seekBy(SEEK_STEP) }
            }

            BasicText(
                "Drag across the waveform to scrub",
                Modifier.fillMaxWidth().padding(top = 6.dp),
                style = TextStyle(
                    color = mist(0.4f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
        }
    }
}

/** Lays the content out [by] higher, taking that much less room: a negative top margin. */
private fun Modifier.pullUp(by: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, max(0, placeable.height - shift)) { placeable.place(0, -shift) }
}

/** A box blur of a small copy of the photo, for systems without a blur effect. */
private fun softened(source: Bitmap): Bitmap {
    val w = 36
    val h = max(1, (w * source.height.toFloat() / source.width).roundToInt())
    val small = Bitmap.createScaledBitmap(source, w, h, true)
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    val out = IntArray(w * h)
    repeat(3) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0
                var g = 0
                var b = 0
                var n = 0
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val c = pixels[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
                        r += (c shr 16) and 0xff
                        g += (c shr 8) and 0xff
                        b += c and 0xff
                        n += 1
                    }
                }
                out[y * w + x] = (0xff shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        out.copyInto(pixels)
    }
    small.setPixels(pixels, 0, w, 0, 0, w, h)
    return small
}

/** The photo, blurred and darkened like a dark blur view over it, then a fade over everything. */
@Composable
private fun Backdrop(photos: WavePhotos, motion: WaveMotion) {
    val density = LocalDensity.current
    val blur = remember(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = with(density) { 30.dp.toPx() }
            BlurEffect(radius, radius, TileMode.Clamp)
        } else {
            null
        }
    }
    val image = photos.softBackdrop ?: photos.backdrop
    if (image != null) {
        val bitmap = remember(image) { image.asImageBitmap() }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    motion.frame
                    alpha = motion.fade(motion.backdropAt)
                    if (photos.softBackdrop == null) renderEffect = blur
                },
        ) {
            // Cropped to fill the screen and 40 dp beyond it on every side.
            val bleed = 40.dp.toPx()
            val boxW = size.width + bleed * 2
            val boxH = size.height + bleed * 2
            val s = max(boxW / bitmap.width, boxH / bitmap.height)
            val w = bitmap.width * s
            val h = bitmap.height * s
            drawImage(
                bitmap,
                dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()),
                dstSize = androidx.compose.ui.unit.IntSize(w.roundToInt(), h.roundToInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
            )
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF101114).copy(alpha = 0.45f)))
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color(6 / 255f, 10 / 255f, 18 / 255f, 0.35f),
                0.4f to Color(6 / 255f, 10 / 255f, 18 / 255f, 0.1f),
                1f to Color(6 / 255f, 8 / 255f, 14 / 255f, 0.78f),
            ),
        ),
    )
}

@Composable
private fun TopBar(album: String) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(28.dp)) {
            scale(density, density, pivot = Offset.Zero) {
                val p = Path().apply {
                    moveTo(7f, 11f)
                    lineTo(14f, 18f)
                    lineTo(21f, 11f)
                }
                drawPath(p, Ink, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(
                "PLAYING FROM ALBUM",
                style = TextStyle(color = mist(0.55f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, fontFamily = FontFamily.SansSerif),
            )
            BasicText(
                album,
                maxLines = 1,
                style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif),
            )
        }
        Canvas(Modifier.size(28.dp)) {
            scale(density, density, pivot = Offset.Zero) {
                for (x in floatArrayOf(7f, 14f, 21f)) drawCircle(Ink, 2.1f, Offset(x, 14f))
            }
        }
    }
}

@Composable
private fun Cover(photos: WavePhotos, motion: WaveMotion, side: Float, reducedMotion: Boolean, album: String) {
    if (side <= 0f) return
    val shadow = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(153, 0, 0, 0)
            // A CSS box shadow, 50 pt of blur (sigma 25). Hardware canvases honour mask filters from API 28.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(25f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    Box(
        Modifier
            .size(side.dp)
            .graphicsLayer {
                motion.frame
                val s = if (reducedMotion) 1f else (COVER_PAUSED + (1 - COVER_PAUSED) * motion.cover.value).toFloat()
                scaleX = s
                scaleY = s
            },
    ) {
        // The shadow box: inset 14 dp at the sides and 18 at the top, 6 dp past the cover's foot. Its
        // shadow falls only outside it, as a CSS box shadow does.
        Canvas(Modifier.fillMaxSize().graphicsLayer { motion.frame; alpha = (0.35 + 0.65 * motion.cover.value).toFloat() }) {
            scale(density, density, pivot = Offset.Zero) {
                val box = RoundRect(14f, 18f, side - 14f, side + 6f, CornerRadius(28f))
                val boxPath = Path().apply { addRoundRect(box) }
                clipPath(boxPath, androidx.compose.ui.graphics.ClipOp.Difference) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        drawIntoCanvas { it.nativeCanvas.drawRoundRect(14f, 44f, side - 14f, side + 32f, 28f, 28f, shadow) }
                    }
                }
                drawPath(boxPath, Color.Black.copy(alpha = 0.2f))
            }
        }
        // Shows if the photo cannot load, so the card never turns into a bare shadow.
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Color(0xFF1B1F2C)))
        photos.cover?.let { bitmap ->
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                image,
                contentDescription = "$album cover art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { motion.frame; alpha = motion.fade(motion.coverAt) }
                    .clip(RoundedCornerShape(22.dp)),
            )
        }
    }
}

private fun DrawScope.clipPath(path: Path, op: androidx.compose.ui.graphics.ClipOp, block: DrawScope.() -> Unit) {
    drawContext.canvas.save()
    drawContext.canvas.clipPath(path, op)
    block()
    drawContext.canvas.restore()
}

/** Rewind or fast-forward: two arrowheads, nudged by the seek. */
@Composable
private fun SeekButton(direction: Int, motion: WaveMotion, label: String, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    Canvas(
        Modifier
            .size(52.dp)
            .graphicsLayer {
                motion.frame
                val n = motion.nudge.value.toFloat()
                translationX = (if (direction < 0) min(0f, n) else max(0f, n)) * density
            }
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) {
                            val p = change.position
                            val inside = p.x >= 0 && p.y >= 0 && p.x <= size.width && p.y <= size.height
                            if (inside && change.uptimeMillis - down.uptimeMillis <= 500) tap()
                            break
                        }
                    }
                }
            },
    ) {
        scale(density, density, pivot = Offset.Zero) {
            translate((52f - 30f) / 2f, (52f - 27f) / 2f) {
                val path = Path()
                for (dx in floatArrayOf(0f, 11f)) {
                    path.moveTo(4f + dx, 7.5f)
                    path.quadraticTo(4f + dx, 6f, 5.3f + dx, 6.8f)
                    path.lineTo(14f + dx, 12.6f)
                    path.quadraticTo(15f + dx, 13.3f, 14f + dx, 14f)
                    path.lineTo(5.3f + dx, 19.8f)
                    path.quadraticTo(4f + dx, 20.6f, 4f + dx, 19f)
                    path.close()
                }
                if (direction < 0) {
                    scale(-1f, 1f, pivot = Offset(15f, 13.5f)) { drawPath(path, Ink) }
                } else {
                    drawPath(path, Ink)
                }
            }
        }
    }
}

// MARK: Drawing

private class WavePaints {
    val bars = Path()
    val text = Paint(Paint.ANTI_ALIAS_FLAG)
    val bounds = Rect()
    val semiBold: Typeface = weighted(600)
    val bold: Typeface = weighted(700)
    val buttonShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(115, 0, 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(15f), BlurMaskFilter.Blur.NORMAL)
    }

    /** Skia's measured width: the ink bounds, rounded out, padded by one on each side. */
    fun width(s: String, typeface: Typeface, size: Float): Float {
        text.typeface = typeface
        text.textSize = size
        text.getTextBounds(s, 0, s.length, bounds)
        return if (bounds.width() > 0) bounds.width() + 2f else 0f
    }

    private fun weighted(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SANS_SERIF, weight, false)
        } else {
            Typeface.create("sans-serif", Typeface.BOLD)
        }
}

private fun DrawScope.drawText(paints: WavePaints, s: String, typeface: Typeface, size: Float, color: Color, x: Float, y: Float) {
    paints.text.typeface = typeface
    paints.text.textSize = size
    paints.text.color = color.toArgb()
    drawIntoCanvas { it.nativeCanvas.drawText(s, x, y, paints.text) }
}

private fun DrawScope.drawWave(m: WaveMotion, accent: Pair<Color, Color>, reducedMotion: Boolean, paints: WavePaints) {
    val duration = m.duration
    val bars = paints.bars
    bars.reset()
    // Every bar, its loudness swayed by the music and swollen by the loupe, in one path.
    val e = m.energy
    val now = m.time
    val loupe = if (reducedMotion) 0.0 else m.lens
    val lx = m.lensX
    val headBar = m.progress / duration * BARS
    val beat = now * BPM / 60
    val pulse = exp(-(beat - floor(beat)) * 5) * e
    var peak = 0.0
    var headHalf = BAR_HALF * 0.4
    for (i in 0 until BARS) {
        val cx = m.left + m.slot * (i + 0.5)
        val near = exp(-((cx - lx) / LENS_RADIUS).pow(2)) * loupe
        val sway = DANCE * e * (0.55 * sin(now * (5.1 + (i % 7) * 0.53) + i * 1.7) + 0.45 * sin(now * (3.3 + (i % 5) * 0.71) + i * 0.9))
        val throb = BEAT * pulse * exp(-((i - headBar) / 5).pow(2))
        val half = max(1.6, BAR_HALF * Levels[i] * (1 + sway + throb) * (1 + MAGNIFY * near))
        val w = m.slot * BAR_FILL * (1 + WIDEN * near)
        val r = (w / 2).toFloat()
        bars.addRoundRect(RoundRect((cx - w / 2).toFloat(), (AXIS_Y - half).toFloat(), (cx + w / 2).toFloat(), (AXIS_Y + half).toFloat(), CornerRadius(r)))
        if (near > 0.02) peak = max(peak, half)
        if (abs(i + 0.5 - headBar) < 0.5) headHalf = half
    }
    val lens = m.lens.toFloat()
    val at = m.progress.coerceIn(0.0, duration)
    val x = m.xOf(at).toFloat()

    drawPath(bars, BaseBar)
    // The loupe lights the bars it passes over.
    if (lens > 0.001f) {
        drawPath(
            bars,
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0f)),
                center = Offset(m.lensX.toFloat(), AXIS_Y.toFloat()),
                radius = (LENS_RADIUS * 2.2).toFloat(),
            ),
            alpha = lens.coerceAtMost(1f),
        )
    }
    clipRect(right = max(0f, m.xOf(m.progress).toFloat()), bottom = CANVAS_H) {
        drawPath(bars, Brush.horizontalGradient(listOf(accent.first, accent.second), startX = m.left.toFloat(), endX = (m.left + m.span).toFloat()))
    }
    val reach = (headHalf + 9).toFloat()
    drawRoundRect(
        Ink,
        topLeft = Offset(x - 1f, AXIS_Y.toFloat() - reach),
        size = androidx.compose.ui.geometry.Size(2f, reach * 2),
        cornerRadius = CornerRadius(1f),
        alpha = 0.55f + 0.45f * lens,
    )

    drawText(paints, clock(at), paints.semiBold, 12f, mist(0.7f), SIDE.toFloat(), TIMES_Y)
    val remaining = "-${clock(duration - at)}"
    val remainingX = (m.width - SIDE).toFloat() - paints.width(remaining, paints.semiBold, 12f)
    drawText(paints, remaining, paints.semiBold, 12f, mist(0.5f), remainingX, TIMES_Y)

    // The bubble: it rides above the tallest bar under the finger and shows the time under it,
    // only over the middle of the loupe's opening, so it pops in and out quickly.
    val k = ((m.lens - 0.35) / 0.5).coerceIn(0.0, 1.0)
    val opacity = (k * k * (3 - 2 * k)).toFloat()
    if (opacity <= 0.001f) return
    val label = clock(at)
    val bw = paints.width(label, paints.bold, 13f) + 22f
    val bx = min(m.width.toFloat() - 8f - bw, max(8f, x - bw / 2))
    val bottom = (AXIS_Y - max(BAR_HALF, peak) - BUBBLE_GAP).toFloat()
    val lift = if (reducedMotion) 0f else (1 - lens) * 10f
    val grow = if (reducedMotion) 1f else 0.7f + 0.3f * lens
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        nc.saveLayerAlpha(0f, 0f, m.width.toFloat(), CANVAS_H, (opacity * 255).roundToInt())
        nc.translate(x, bottom + lift)
        nc.scale(grow, grow)
        nc.translate(-x, -bottom)
    }
    drawRoundRect(
        Ink,
        topLeft = Offset(bx, bottom - TAIL.toFloat() - BUBBLE_H.toFloat()),
        size = androidx.compose.ui.geometry.Size(bw, BUBBLE_H.toFloat()),
        cornerRadius = CornerRadius(BUBBLE_H.toFloat() / 2),
    )
    val ax = min(bx + bw - 14f, max(bx + 14f, x))
    val tail = Path().apply {
        moveTo(ax - TAIL.toFloat() - 1f, bottom - TAIL.toFloat() - 0.5f)
        lineTo(ax, bottom)
        lineTo(ax + TAIL.toFloat() + 1f, bottom - TAIL.toFloat() - 0.5f)
        close()
    }
    drawPath(tail, Ink)
    drawText(paints, label, paints.bold, 13f, accent.second, bx + 11f, bottom - TAIL.toFloat() - BUBBLE_H.toFloat() / 2 + 4.6f)
    drawIntoCanvas { it.nativeCanvas.restore() }
}

/** The button: a white disc with a soft shadow, and the play/pause icon morphing on it. */
private fun DrawScope.drawPlayButton(morph: Double, paints: WavePaints) {
    val c = BUTTON / 2
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { it.nativeCanvas.drawCircle(c, c + 10f, c, paints.buttonShadow) }
    }
    drawCircle(Ink, c, Offset(c, c))
    drawPath(iconPath(morph), Color(0xFF0B0D14))
}

/**
 * The play/pause icon, [morph] of the way from play to pause, its corners rounded like Skia's
 * corner path effect: each corner cut back along both edges and joined by a curve through it.
 */
private fun iconPath(morph: Double): Path {
    val c = BUTTON / 2.0
    // The icon dips a little mid-morph, so the halves read as turning over rather than sliding.
    val k = 1 - 0.14 * sin(Math.PI * morph.coerceIn(0.0, 1.0))
    val path = Path()
    for (half in 0 until 2) {
        val from = if (half == 0) PlayLeft else PlayRight
        val to = if (half == 0) PauseLeft else PauseRight
        val xs = DoubleArray(4) { c + lerp(from[it * 2], to[it * 2], morph) * k }
        val ys = DoubleArray(4) { c + lerp(from[it * 2 + 1], to[it * 2 + 1], morph) * k }
        fun cut(i: Int, j: Int): Offset {
            val dx = xs[j] - xs[i]
            val dy = ys[j] - ys[i]
            val length = sqrt(dx * dx + dy * dy)
            if (length <= 0) return Offset(xs[i].toFloat(), ys[i].toFloat())
            val share = min(ICON_CORNER / length, 0.5)
            return Offset((xs[i] + dx * share).toFloat(), (ys[i] + dy * share).toFloat())
        }
        for (i in 0 until 4) {
            val enter = cut(i, (i + 3) % 4)
            val leave = cut(i, (i + 1) % 4)
            if (i == 0) path.moveTo(enter.x, enter.y) else path.lineTo(enter.x, enter.y)
            path.quadraticTo(xs[i].toFloat(), ys[i].toFloat(), leave.x, leave.y)
        }
        path.close()
    }
    return path
}
