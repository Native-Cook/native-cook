package com.ngocdevv.nativecook.blocks.animations.shatterglasstransition

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * @param id An Unsplash photo id (`photo-…`), portrait.
 */
data class ShatterPhoto(val id: String, val title: String, val line: String)

private val Photos = listOf(
    ShatterPhoto("photo-1711560705654-325ba859c38b", "Ember Nursery", "A cloud of newborn stars, 7,500 light-years out"),
    ShatterPhoto("photo-1543007168-5fa9b3c5f5fb", "Sea Nettle", "Drifting twelve metres down, open Pacific"),
    ShatterPhoto("photo-1759179075678-d65678f1050e", "Mercury Fold", "Liquid chrome, study no. 4"),
    ShatterPhoto("photo-1628944682084-831f35256163", "Mauve Stinger", "Night swarm off the Atlantic shelf"),
)

private const val PHOTO_PX = 1000
private fun photoUrl(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$PHOTO_PX&q=80"

// The fracture: RAYS cracks run out from the point of impact, crossed by rings at RINGS dp
// (jittered), so the glass breaks into small shards near the impact and large ones far from it.
private const val RAYS = 13
private val Rings = doubleArrayOf(0.0, 20.0, 52.0, 104.0, 180.0, 285.0, 425.0, 610.0, 850.0, 1150.0)
private const val RAY_JITTER = 0.55
private const val RING_JITTER = 0.3
// Rings from this one outwards split their shards in two along a diagonal.
private const val SPLIT_FROM = 5

// Timeline of a break, in seconds: the cracks race out over CRACK_TIME, the glass holds until
// FALL_AT, then the shards fall; by END every shard has left the screen or faded.
private const val CRACK_TIME = 0.14f
private const val FALL_AT = 0.26f
private const val END = 1.5f
private const val FADE_TAIL = 0.3f
// Shards: gravity in dp/s², the outward push (strongest at the impact, falling off over
// PUSH_REACH dp), and each shard's start delayed by its distance from the impact.
private const val GRAVITY = 2600f
private const val PUSH_NEAR = 560.0
private const val PUSH_FAR = 140.0
private const val PUSH_REACH = 260.0
private const val DELAY_PER_PT = 0.00032
// The next photo waits behind: it starts this much larger and darker and settles as it shows.
private const val REVEAL_ZOOM = 0.06f
private const val REVEAL_DIM = 0.5f
// The whole pane shakes on impact: a damped oscillation of SHAKE dp.
private const val SHAKE = 5f

// Mending (swiping back): the previous photo's shards rise from where they would be REVERSE_FROM
// seconds into a fall and fit back together, following the finger across MEND_SPAN of the width.
private const val REVERSE_FROM = 0.78f
private const val MEND_SPAN = 0.75f
private const val MEND_TIME = 1f
private val MendEase = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
// The pan starts after 14 dp of sideways travel; a tap lifts within 300 ms.
private const val PAN_SLOP = 14f
private const val TAP_MS = 300L

// autoPlay (seconds): three breaks, one mend back, two more breaks: four photos forward, so the
// loop ends on the photo it started from.
private const val LOOP = 13.2f

private class Cue(val at: Float, val mend: Boolean, val x: Float, val y: Float)

private val Cues = listOf(
    Cue(0.6f, false, 0.62f, 0.38f),
    Cue(2.8f, false, 0.3f, 0.56f),
    Cue(5.0f, false, 0.55f, 0.3f),
    Cue(7.2f, true, 0.5f, 0.45f),
    Cue(8.9f, false, 0.42f, 0.42f),
    Cue(11.0f, false, 0.68f, 0.6f),
)
private const val USER_HOLD = 4f

private val Sans = FontFamily.SansSerif

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

private fun smooth(x: Float): Float {
    val t = clamp01(x)
    return t * t * (3f - 2f * t)
}

/** A broken pane: its cracks, and its shards with how each one flies. */
private class Shatter(val x: Float, val y: Float) {
    /** Each crack as a flat list of points from the impact outwards, and their distances. */
    val rays = ArrayList<FloatArray>()
    val reach = ArrayList<FloatArray>()
    val polys = ArrayList<Path>()
    val cx = ArrayList<Float>()
    val cy = ArrayList<Float>()
    val vx = ArrayList<Float>()
    val vy = ArrayList<Float>()
    val delay = ArrayList<Float>()
    val spin = ArrayList<Float>()
    val flip = ArrayList<Float>()
    val grow = ArrayList<Float>()
}

/** A polygon (flat points) cut to the rectangle 0…w × 0…h (Sutherland–Hodgman). */
private fun clipToRect(points: DoubleArray, w: Double, h: Double): DoubleArray {
    var out = points
    for (edge in 0 until 4) {
        val input = out
        val next = ArrayList<Double>()
        val n = input.size / 2
        if (n == 0) break
        for (i in 0 until n) {
            val ax = input[i * 2]
            val ay = input[i * 2 + 1]
            val bx = input[((i + 1) % n) * 2]
            val by = input[((i + 1) % n) * 2 + 1]
            // Signed distance inside the edge: left x ≥ 0, right x ≤ w, top y ≥ 0, bottom y ≤ h.
            val da = when (edge) { 0 -> ax; 1 -> w - ax; 2 -> ay; else -> h - ay }
            val db = when (edge) { 0 -> bx; 1 -> w - bx; 2 -> by; else -> h - by }
            if (da >= 0) { next.add(ax); next.add(ay) }
            if ((da >= 0) != (db >= 0)) {
                val t = da / (da - db)
                next.add(ax + (bx - ax) * t)
                next.add(ay + (by - ay) * t)
            }
        }
        out = next.toDoubleArray()
    }
    return out
}

/**
 * Breaks a w × h pane from (px, py): its cracks and its shards, the same for the same seed, drawn
 * from the same generator as the React Native version so every break matches it.
 */
private fun buildShatter(px: Double, py: Double, w: Double, h: Double, seed: Int): Shatter {
    var state = (seed.toLong() * 2654435761L) % 4294967296L
    fun random(): Double {
        state = (state * 1664525L + 1013904223L) % 4294967296L
        return state / 4294967296.0
    }
    val turn = random() * PI * 2
    val verts = ArrayList<DoubleArray>()
    val shatter = Shatter(px.toFloat(), py.toFloat())
    for (i in 0 until RAYS) {
        val angle = turn + ((i + (random() - 0.5) * RAY_JITTER) * PI * 2) / RAYS
        val ring = DoubleArray(Rings.size * 2)
        val dist = FloatArray(Rings.size)
        for (j in Rings.indices) {
            val r = if (j == 0) 0.0 else Rings[j] * (1 + (random() - 0.5) * RING_JITTER)
            val a = angle + (random() - 0.5) * 0.14
            ring[j * 2] = px + cos(a) * r
            ring[j * 2 + 1] = py + sin(a) * r
            dist[j] = r.toFloat()
        }
        verts.add(ring)
        shatter.rays.add(FloatArray(ring.size) { ring[it].toFloat() })
        shatter.reach.add(dist)
    }
    fun add(points: DoubleArray) {
        val poly = clipToRect(points, w, h)
        val n = poly.size / 2
        if (n < 3) return
        var cx = 0.0
        var cy = 0.0
        for (k in 0 until n) {
            cx += poly[k * 2]
            cy += poly[k * 2 + 1]
        }
        cx /= n
        cy /= n
        val dx = cx - px
        val dy = cy - py
        val d = max(1.0, hypot(dx, dy))
        val near = exp(-d / PUSH_REACH)
        val push = (PUSH_FAR + PUSH_NEAR * near) * (0.7 + 0.6 * random())
        val path = Path()
        path.moveTo(poly[0].toFloat(), poly[1].toFloat())
        for (k in 1 until n) path.lineTo(poly[k * 2].toFloat(), poly[k * 2 + 1].toFloat())
        path.close()
        shatter.polys.add(path)
        shatter.cx.add(cx.toFloat())
        shatter.cy.add(cy.toFloat())
        shatter.vx.add(((dx / d) * push + (random() - 0.5) * 90).toFloat())
        shatter.vy.add(((dy / d) * push * 0.6 - 180 * random()).toFloat())
        shatter.delay.add((d * DELAY_PER_PT + random() * 0.05).toFloat())
        shatter.spin.add(((random() - 0.5) * 2 * (2.5 + 5 * near)).toFloat())
        shatter.flip.add(((random() - 0.5) * 2 * (3 + 7 * near)).toFloat())
        shatter.grow.add((0.08 + 0.3 * near * random()).toFloat())
    }
    for (j in 0 until Rings.size - 1) {
        for (i in 0 until RAYS) {
            val a = verts[i]
            val b = verts[(i + 1) % RAYS]
            if (j == 0) {
                add(doubleArrayOf(px, py, a[2], a[3], b[2], b[3]))
            } else if (j >= SPLIT_FROM) {
                val p = doubleArrayOf(a[j * 2], a[j * 2 + 1], a[j * 2 + 2], a[j * 2 + 3], b[j * 2 + 2], b[j * 2 + 3], b[j * 2], b[j * 2 + 1])
                if (random() > 0.5) {
                    add(doubleArrayOf(p[0], p[1], p[2], p[3], p[4], p[5]))
                    add(doubleArrayOf(p[0], p[1], p[4], p[5], p[6], p[7]))
                } else {
                    add(doubleArrayOf(p[0], p[1], p[2], p[3], p[6], p[7]))
                    add(doubleArrayOf(p[2], p[3], p[4], p[5], p[6], p[7]))
                }
            } else {
                add(doubleArrayOf(a[j * 2], a[j * 2 + 1], a[j * 2 + 2], a[j * 2 + 3], b[j * 2 + 2], b[j * 2 + 3], b[j * 2], b[j * 2 + 1]))
            }
        }
    }
    return shatter
}

/** A value moved on the engine's clock from one number to another, then a completion. */
private class Timing(val from: Float, val to: Float, val start: Float, val duration: Float, val curve: (Float) -> Float, val done: () -> Unit)

/** The pane's state, stepped once a frame like the RN frame callback, and the gestures that drive it. */
private class ShatterMotion(val count: Int) {
    // Bumped every frame; the picture reads it, so it is drawn again.
    var tick by mutableLongStateOf(0L)
    // 0 at rest, 1 breaking the front photo, 2 mending the previous one over it.
    var mode = 0
    var current = 0
    // Seconds into a break; and 0 … 1 through a mend.
    var time = 0f
    var mend = 0f
    var shatter: Shatter? = null
    private var timeMotion: Timing? = null
    private var mendMotion: Timing? = null
    private var seed = 1
    var scrubbing = false
    var clock = 0f
    var looping = false
    var hold = 0f
    var elapsed = 0f
    var reduced = false
    var width = 0f
    var height = 0f
    // The photo whose caption shows.
    var shown by mutableIntStateOf(0)
    var onThud: (Boolean) -> Unit = {}
    var onChange: (Int) -> Unit = {}

    fun step(dt: Float) {
        elapsed += dt
        runLoop(dt)
        timeMotion?.let { m ->
            val (value, finished) = evaluate(m)
            time = value
            if (finished) {
                timeMotion = null
                m.done()
            }
        }
        mendMotion?.let { m ->
            val (value, finished) = evaluate(m)
            mend = value
            if (finished) {
                mendMotion = null
                m.done()
            }
        }
        tick += 1
    }

    private fun evaluate(m: Timing): Pair<Float, Boolean> {
        val p = if (m.duration > 0f) clamp01((elapsed - m.start) / m.duration) else 1f
        return (m.from + (m.to - m.from) * m.curve(p)) to (p >= 1f)
    }

    private fun runLoop(dt: Float) {
        if (scrubbing) return
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f) clock = 0f
            return
        }
        if (!looping) return
        val before = clock
        val t = (before + dt) % LOOP
        clock = t
        for (cue in Cues) {
            val passed = (before < cue.at && t >= cue.at) || (t < before && (cue.at > before || cue.at <= t))
            if (!passed) continue
            if (!cue.mend) strike(cue.x * width, cue.y * height, false)
            else if (beginMend(cue.x * width, cue.y * height)) settleMend(1f, false, MEND_TIME)
        }
    }

    private fun announce(index: Int) {
        shown = index
        onChange(index)
    }

    /** Breaks the front photo from (x, y). */
    fun strike(x: Float, y: Float, user: Boolean) {
        if (mode != 0 || width == 0f) return
        seed += 1
        shatter = buildShatter(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble(), seed)
        mode = 1
        time = 0f
        val next = (current + 1) % count
        announce(next)
        if (user) onThud(true)
        timeMotion = Timing(0f, END, elapsed, END, { it }) {
            current = next
            mode = 0
            time = 0f
        }
    }

    /** Starts mending the previous photo from shards broken at (x, y); `mend` drives it. */
    fun beginMend(x: Float, y: Float): Boolean {
        if (mode != 0 || width == 0f) return false
        seed += 1
        shatter = buildShatter(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble(), seed)
        mend = 0f
        mode = 2
        return true
    }

    private fun finishMend(user: Boolean) {
        val back = (current - 1 + count) % count
        current = back
        mode = 0
        mend = 0f
        mendMotion = null
        announce(back)
        if (user) onThud(false)
    }

    fun settleMend(to: Float, user: Boolean, seconds: Float) {
        mendMotion = Timing(mend, to, elapsed, seconds, MendEase::transform) {
            if (to == 1f) {
                finishMend(user)
            } else {
                mode = 0
                mend = 0f
            }
        }
    }

    /** Ends a break or a mend in flight at once, so a new touch always gets a response. */
    fun settleNow() {
        if (mode == 1) {
            timeMotion = null
            current = (current + 1) % count
            mode = 0
            time = 0f
        } else if (mode == 2 && !scrubbing) {
            mendMotion = null
            if (mend > 0.5f) {
                finishMend(false)
            } else {
                mode = 0
                mend = 0f
            }
        }
    }

    fun panStart(translationX: Float, x: Float, y: Float) {
        hold = USER_HOLD
        settleNow()
        // Swiping back (to the right) mends the previous photo under the finger.
        if (translationX > 0f && beginMend(x, y)) {
            scrubbing = true
            mendMotion = null
        }
    }

    fun panUpdate(translationX: Float) {
        if (scrubbing) mend = clamp01(translationX / (width * MEND_SPAN))
    }

    fun panEnd(translationX: Float, rawVelocityX: Float, x: Float, y: Float) {
        // A velocity tracker with too few samples can answer NaN; treat it as a still finger.
        val velocityX = if (rawVelocityX.isFinite()) rawVelocityX else 0f
        if (scrubbing) {
            scrubbing = false
            val projected = mend + (velocityX / (width * MEND_SPAN)) * 0.2f
            val to = if (projected > 0.5f) 1f else 0f
            val left = if (to == 1f) 1f - mend else mend
            settleMend(to, true, max(0.18f, left * 0.7f))
            return
        }
        // A hard swipe forward breaks the glass where the finger first landed.
        if (velocityX < -700f || translationX < -width * 0.3f) strike(x, y, true)
    }

    /** A pan the system took away settles the mend back. */
    fun panCancel() {
        if (scrubbing) {
            scrubbing = false
            settleMend(0f, true, 0.26f)
        }
    }

    fun tap(x: Float, y: Float) {
        hold = USER_HOLD
        settleNow()
        strike(x, y, true)
    }
}

/**
 * A full-screen photo behind glass. Tapping (or a hard swipe) cracks it from the touch point, and
 * the pane breaks into shards that fall and spin away with the light catching their edges,
 * showing the next photo underneath. Swiping back fits the previous photo's shards together.
 *
 * @param autoPlay Breaks to the next photo every couple of seconds, and once mends back, on a 13.2-second loop.
 * @param onChange Called with the index of the photo that ends up in front.
 */
@Composable
fun ShatterGlassTransition(
    modifier: Modifier = Modifier,
    photos: List<ShatterPhoto> = Photos,
    autoPlay: Boolean = false,
    onChange: ((Int) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val changed by rememberUpdatedState(onChange)
    val motion = remember(photos) { ShatterMotion(photos.size) }
    motion.reduced = reduced
    motion.onChange = { changed?.invoke(it) }
    motion.onThud = { heavy ->
        view.performHapticFeedback(if (heavy) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val images = remember(photos) { mutableStateMapOf<String, Bitmap>() }
    val paints = remember { ShatterPaints() }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(motion, autoPlay) {
        motion.looping = autoPlay
        motion.clock = 0f
        motion.hold = 0f
    }

    // Every photo decoded up front, so a break never waits for the next one.
    LaunchedEffect(photos, context) {
        val loader = SingletonImageLoader.get(context)
        for (photo in photos) {
            launch {
                // A software bitmap, so it can fill shards through a shader.
                val request = ImageRequest.Builder(context).data(photoUrl(photo.id)).allowHardware(false).build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                images[photo.id] = result.image.toBitmap()
            }
        }
    }

    LaunchedEffect(motion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) motion.step(dt)
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF05060A))
            .semantics { contentDescription = "${photos[motion.shown].title}. Tap to break the glass, swipe right to mend it." }
            .pointerInput(motion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    motion.hold = USER_HOLD
                    var panning = false
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val dx = (change.position.x - start.x) / density.density
                        if (!change.pressed) {
                            if (panning) {
                                motion.panEnd(dx, tracker.calculateVelocity().x / density.density, start.x / density.density, start.y / density.density)
                            } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                motion.tap(change.position.x / density.density, change.position.y / density.density)
                            }
                            released = true
                            continue
                        }
                        if (!panning && abs(dx) >= PAN_SLOP) {
                            panning = true
                            motion.panStart(dx, start.x / density.density, start.y / density.density)
                        }
                        if (panning) {
                            change.consume()
                            motion.panUpdate(dx)
                        }
                    }
                    if (panning && !released) motion.panCancel()
                }
            },
    ) {
        motion.width = maxWidth.value
        motion.height = maxHeight.value

        Canvas(Modifier.fillMaxSize()) {
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { drawPane(it.nativeCanvas, motion, photos.map { p -> images[p.id] }, paints) }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height((safeTop + 90f).dp)
                .background(Brush.verticalGradient(listOf(Color(3, 4, 8).copy(alpha = 0.45f), Color(3, 4, 8).copy(alpha = 0f)))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height((safeBottom + 220f).dp)
                .background(Brush.verticalGradient(listOf(Color(3, 4, 8).copy(alpha = 0f), Color(3, 4, 8).copy(alpha = 0.7f)))),
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = (safeTop + 14f).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "BEHIND GLASS",
                style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.4.sp),
            )
            BasicText(
                "Tap to break · swipe back to mend",
                style = TextStyle(color = Color.White.copy(alpha = 0.62f), fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
            )
        }

        // The caption fades out quickly and the next one in, a little later and slower.
        AnimatedContent(
            targetState = motion.shown,
            transitionSpec = { fadeIn(tween(420, delayMillis = 200)) togetherWith fadeOut(tween(140)) },
            contentAlignment = Alignment.BottomStart,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 34f).dp),
            label = "caption",
        ) { index ->
            val photo = photos[index]
            Column(Modifier.fillMaxWidth()) {
                BasicText(
                    buildAnnotatedString {
                        append((index + 1).toString().padStart(2, '0'))
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.5f))) { append(" / ${photos.size.toString().padStart(2, '0')}") }
                    },
                    style = TextStyle(color = Color.White, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.2.sp, fontFeatureSettings = "tnum"),
                )
                BasicText(
                    photo.title,
                    modifier = Modifier.padding(top = 8.dp),
                    style = TextStyle(color = Color.White, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.8).sp),
                )
                BasicText(
                    photo.line,
                    modifier = Modifier.padding(top = 4.dp),
                    style = TextStyle(color = Color.White.copy(alpha = 0.72f), fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp),
                )
            }
        }
    }
}

// MARK: drawing

private class ShatterPaints {
    val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.WHITE
    }
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.WHITE
    }
    val line = Paint(glow).apply { strokeWidth = 1.1f }
    val bruise = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val dim = Paint().apply { color = android.graphics.Color.BLACK }
    val cracks = Path()
    val rect = RectF()
    val matrix = Matrix()
    val shaders = HashMap<Bitmap, BitmapShader>()
}

// roundToInt throws on NaN, so a stray NaN alpha draws nothing instead.
private fun alphaOf(a: Float) = if (a.isNaN()) 0 else (a.coerceIn(0f, 1f) * 255f).roundToInt()

/** One frame of the pane: the next photo waiting behind, the front photo whole, cracked or in shards, and the cracks. */
private fun drawPane(canvas: android.graphics.Canvas, m: ShatterMotion, images: List<Bitmap?>, paints: ShatterPaints) {
    m.tick
    val w = m.width
    val h = m.height
    canvas.drawColor(0xFF05060A.toInt())
    if (w <= 0f || images.isEmpty()) return
    val n = images.size
    val mode = m.mode
    val front = m.current
    // A mend plays a break backwards from REVERSE_FROM seconds, on the previous photo.
    val t = if (mode == 2) REVERSE_FROM * (1f - m.mend) else m.time
    val top = images[if (mode == 2) (front - 1 + n) % n else front]
    val under = if (mode == 0) null else images[if (mode == 2) front else (front + 1) % n]
    val reveal = smooth((t - FALL_AT) / (END - FALL_AT - 0.2f))
    if (under != null) {
        drawCover(canvas, under, w, h, if (m.reduced) 1f else 1f + REVEAL_ZOOM * (1f - reveal), 1f, paints)
        paints.dim.alpha = alphaOf(REVEAL_DIM * (1f - reveal))
        canvas.drawRect(0f, 0f, w, h, paints.dim)
    }
    if (top == null) return
    val s = m.shatter
    if (mode == 0 || s == null) {
        drawCover(canvas, top, w, h, 1f, 1f, paints)
        return
    }
    // The pane shakes once on impact.
    val shake = if (m.reduced) 0f else SHAKE * exp(-t * 16f) * sin(t * 70f)
    canvas.save()
    canvas.translate(shake, shake * 0.4f)
    val fade = 1f - smooth((t - (END - FADE_TAIL)) / FADE_TAIL)
    // While mending, the shards fade in as they come.
    val enter = if (mode == 2) clamp01(m.mend / 0.18f) else 1f
    if (m.reduced || t < FALL_AT) {
        // Reduced motion: nothing falls; the cracked photo fades to the next one.
        val alpha = if (m.reduced) 1f - smooth((t - FALL_AT) / 0.6f) else 1f
        drawCover(canvas, top, w, h, 1f, alpha * enter, paints)
    } else {
        drawShards(canvas, s, top, w, h, t, fade * enter, paints)
    }
    if (t < FALL_AT + if (m.reduced) 0.6f else 0f) {
        val reach = hypot(w, h) * smooth(t / CRACK_TIME)
        drawCracks(canvas, s, reach, enter * if (m.reduced) 1f - smooth((t - FALL_AT) / 0.6f) else 1f, paints)
    }
    canvas.restore()
}

private fun drawCover(canvas: android.graphics.Canvas, image: Bitmap, w: Float, h: Float, zoom: Float, alpha: Float, paints: ShatterPaints) {
    if (alpha <= 0f) return
    val s = max(w / image.width, h / image.height) * zoom
    val dw = image.width * s
    val dh = image.height * s
    paints.rect.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
    paints.photo.alpha = alphaOf(alpha)
    canvas.drawBitmap(image, null, paints.rect, paints.photo)
}

/** The cracks, drawn out to `reach` dp from the impact. */
private fun drawCracks(canvas: android.graphics.Canvas, s: Shatter, reach: Float, alpha: Float, paints: ShatterPaints) {
    if (alpha <= 0f || reach <= 0f) return
    val path = paints.cracks
    path.reset()
    for (i in s.rays.indices) {
        val ray = s.rays[i]
        val dist = s.reach[i]
        path.moveTo(ray[0], ray[1])
        for (j in 1 until dist.size) {
            if (dist[j - 1] >= reach) break
            val f = min(1f, (reach - dist[j - 1]) / max(1f, dist[j] - dist[j - 1]))
            val x0 = ray[(j - 1) * 2]
            val y0 = ray[(j - 1) * 2 + 1]
            path.lineTo(x0 + (ray[j * 2] - x0) * f, y0 + (ray[j * 2 + 1] - y0) * f)
        }
    }
    // The rings join neighbouring cracks once both have run past them.
    val count = s.rays.size
    for (i in 0 until count) {
        val a = s.rays[i]
        val b = s.rays[(i + 1) % count]
        for (j in 1 until s.reach[i].size - 2) {
            if (s.reach[i][j] * 1.25f > reach || s.reach[(i + 1) % count][j] * 1.25f > reach) break
            path.moveTo(a[j * 2], a[j * 2 + 1])
            path.lineTo(b[j * 2], b[j * 2 + 1])
        }
    }
    paints.glow.alpha = alphaOf(0.16f * alpha)
    canvas.drawPath(path, paints.glow)
    paints.line.alpha = alphaOf(0.9f * alpha)
    canvas.drawPath(path, paints.line)
    // A frosted bruise where the glass was struck.
    paints.bruise.alpha = alphaOf(0.4f * alpha)
    canvas.drawCircle(s.x, s.y, 4f + 10f * clamp01(reach / 60f), paints.bruise)
}

/** The shards of `image` at `time` seconds into the fall. */
private fun drawShards(canvas: android.graphics.Canvas, s: Shatter, image: Bitmap, w: Float, h: Float, time: Float, alpha: Float, paints: ShatterPaints) {
    if (alpha <= 0f) return
    val scale = max(w / image.width, h / image.height)
    val shader = paints.shaders.getOrPut(image) { BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    paints.matrix.setScale(scale, scale)
    paints.matrix.postTranslate((w - image.width * scale) / 2f, (h - image.height * scale) / 2f)
    shader.setLocalMatrix(paints.matrix)
    paints.fill.shader = shader
    paints.fill.alpha = alphaOf(alpha)
    for (k in s.polys.indices) {
        val tt = max(0f, time - FALL_AT - s.delay[k])
        val x = s.cx[k] + s.vx[k] * tt
        val y = s.cy[k] + s.vy[k] * tt + 0.5f * GRAVITY * tt * tt
        if (y > h + 260f) continue
        val spin = s.spin[k] * tt
        val flip = s.flip[k] * tt
        val facing = cos(flip)
        val grow = 1f + s.grow[k] * min(1f, tt * 1.5f)
        val path = s.polys[k]
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(spin * 180f / PI.toFloat())
        // Turning about its own axis, a shard narrows to an edge and comes back mirrored.
        canvas.scale(grow * if (abs(facing) < 0.04f) 0.04f * (if (facing < 0f) -1f else 1f) else facing, grow)
        canvas.translate(-s.cx[k], -s.cy[k])
        canvas.drawPath(path, paints.fill)
        // Darker the more it is turned away, as glass seen at a slant.
        paints.shade.alpha = alphaOf(alpha * (0.55f * (1f - abs(facing)) + (if (facing < 0f) 0.18f else 0f) + 0.08f * min(1f, tt * 4f)))
        canvas.drawPath(path, paints.shade)
        // Light glints along the edges when the shard turns through the light.
        val glint = abs(sin(flip + spin * 0.5f)).pow(6)
        paints.edge.alpha = alphaOf(alpha * if (tt > 0f) 0.28f + 0.7f * glint else 0.75f)
        canvas.drawPath(path, paints.edge)
        canvas.restore()
    }
}

/** True when the user turned animations off in system settings ("Remove animations"). */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
