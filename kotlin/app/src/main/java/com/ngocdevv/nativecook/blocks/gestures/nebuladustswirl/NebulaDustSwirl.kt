// Nebula Dust Swirl — a sleep sound playing under forty thousand grains of light (NebulaDustSwirl.tsx).
//
// Drift's now-playing screen: an OpenGL ES 3 pass (NdsGl.kt, shaders in NdsShaders.kt) turns a slow
// spiral nebula, a warm core and two cool arms in a tilted disc, every grain added as light, nearer
// grains larger. A timer ring frames the core; the sleep timer and the track sit under it.
//
// The reusable piece is this composable: give it the track, the timer's state and your labels.
//
// autoPlay: LOOP = 10 s. The nebula turns half a turn per loop (its grains come in pairs on opposite
// arms, so the frame at 10 s equals the frame at 0). A ghost finger stirs the arms along an arc round
// the core from 1.2 to 6 s; the stirred dust springs back, what is left is blended out from 8.6 to
// 9.4 s and cleared at the cut. A real touch takes over at once: the dust swirls round the finger and
// is dragged along with it, while the film slows to 0.4×. IDLE seconds after the last touch the film
// eases to the nearer end of the loop and restarts at t = 0. Reduced motion: the rest pose, still.
//
// Thread model: one frame callback advances the clock and hands one small array to the dust's render
// thread; the timer and the glow are graphicsLayer / draw lambdas of the film time, so a frame
// recomposes nothing. Without OpenGL ES 3 the block draws NdsFallback.kt's 1,370 grains instead.
package com.ngocdevv.nativecook.blocks.gestures.nebuladustswirl

import android.graphics.BlurMaskFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class NebulaTrack(
    val app: String = "Drift",
    val section: String = "Sleep sounds",
    val title: String = "Low Tide Hum",
    val detail: String = "Brown noise · 8 layers",
    val hint: String = "Drag to stir the dust",
)

private val INK = Color(0xFFF6EEE6)
private val MUTE = rgba(246, 238, 230, 0.66f)
/** the timer's digit height, dp */
private const val DIGIT = 54f
private const val GHOST = 46f
private const val MODE_AUTO = 0
private const val MODE_TOUCH = 1
private const val MODE_RETURN = 2
private val DIGITS = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0)

/** Reanimated's default withTiming (ease in-out quad), stepped by the engine */
private class Fade(initial: Float) {
    var value = initial
        private set
    private var from = initial
    private var to = initial
    private var length = 0f
    private var elapsed = 0f

    fun snap(v: Float) {
        value = v
        to = v
        length = 0f
    }

    fun to(target: Float, seconds: Float) {
        from = value
        to = target
        length = seconds
        elapsed = 0f
    }

    fun step(dt: Float) {
        if (length <= 0f) return
        elapsed += dt
        val p = elapsed / length
        if (p >= 1f) {
            snap(to)
            return
        }
        val e = if (p < 0.5f) 2f * p * p else 1f - 2f * (1f - p) * (1f - p)
        value = from + (to - from) * e
    }
}

/** the clock, the script, the finger and the frame handed to the dust; everything on the main thread */
private class NdsEngine {
    /** bumped once a frame; the graphicsLayer and draw lambdas read it */
    var tick by mutableIntStateOf(0)
        private set

    var auto = false
    /** true once the dust (or its fallback) is known: the loop waits at t = 0 until then */
    var gate = false
    var width = 1f
    var height = 1f
    var centreX = 0f
    var centreY = 0f
    var unit = 1f
    var onFrame: (FloatArray) -> Unit = {}

    private var clock = 0.0
    private var mode = MODE_AUTO
    private var autoStart = 0.0
    private var prevFilm = 0.0
    private var epoch = 0
    private var serial = 0
    private var down = false
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var returnFrom = 0.0
    private var fingerX = 0f
    private var fingerY = 0f
    private var fingerVX = 0f
    private var fingerVY = 0f
    private var touchOn = 0f
    private val g = DoubleArray(3)

    /** the film time the labels read */
    var film = STILL
        private set
    var ghostX = 0f
        private set
    var ghostY = 0f
        private set
    private val ghostFade = Fade(0f)
    private val hintFade = Fade(1f)
    val ghostA get() = ghostFade.value
    val hint get() = hintFade.value
    val frame = FloatArray(Frame.LENGTH)

    fun begin(x: Float, y: Float) {
        mode = MODE_TOUCH
        down = true
        lastTouch = clock
        fingerX = x
        fingerY = y
        fingerVX = 0f
        fingerVY = 0f
        ghostFade.to(0f, 0.15f)
        hintFade.to(0f, 0.25f)
    }

    /** the finger at x, y moving at vx, vy (dp, dp/s) */
    fun move(x: Float, y: Float, vx: Float, vy: Float) {
        val cap = Touch.MAX_SPEED
        fingerX = x
        fingerY = y
        fingerVX = vx.coerceIn(-cap, cap)
        fingerVY = vy.coerceIn(-cap, cap)
        lastTouch = clock
    }

    fun end() {
        down = false
        fingerVX = 0f
        fingerVY = 0f
        lastTouch = clock
    }

    private fun fill(t: Double, dt: Float, hold: Float, tx: Float, ty: Float, on: Float, vx: Float, vy: Float) {
        frame[Frame.SERIAL] = serial.toFloat()
        frame[Frame.EPOCH] = epoch.toFloat()
        frame[Frame.T] = t.toFloat()
        frame[Frame.DT] = dt
        frame[Frame.HOLD] = hold
        frame[Frame.TOUCH_X] = tx
        frame[Frame.TOUCH_Y] = ty
        frame[Frame.TOUCH_ON] = on
        frame[Frame.PUSH_X] = vx
        frame[Frame.PUSH_Y] = vy
        frame[Frame.WIDTH] = width
        frame[Frame.HEIGHT] = height
        frame[Frame.CENTRE_X] = centreX
        frame[Frame.CENTRE_Y] = centreY
        frame[Frame.UNIT] = unit
    }

    /** reduced motion: one still frame, the rest pose */
    fun still() {
        mode = MODE_AUTO
        down = false
        film = STILL
        ghostFade.snap(0f)
        serial = 1
        epoch = 0
        fill(STILL, 0f, 1f, 0f, 0f, 0f, 0f, 0f)
        onFrame(frame)
        tick += 1
    }

    fun step(dt: Float) {
        clock += dt
        val now = clock
        ghostFade.step(dt)
        hintFade.step(dt)
        var t = film
        var hold = 1f
        var aim = 0f
        var tx = fingerX
        var ty = fingerY
        var vx = fingerVX
        var vy = fingerVY

        if (mode == MODE_AUTO) {
            if (!gate || !auto) {
                autoStart = now
                t = 0.0
                ghostFade.snap(0f)
                vx = 0f
                vy = 0f
            } else {
                t = (now - autoStart) % LOOP
                if (t < prevFilm) epoch += 1
                ghostAt(t, g)
                tx = (centreX + g[0] * unit).toFloat()
                ty = (centreY + g[1] * unit).toFloat()
                // the ghost's speed, so it drags the dust like a finger would
                val cap = Touch.MAX_SPEED
                vx = ((tx - ghostX) / max(dt, 0.001f)).coerceIn(-cap, cap)
                vy = ((ty - ghostY) / max(dt, 0.001f)).coerceIn(-cap, cap)
                if (g[2] < 0.001) {
                    vx = 0f
                    vy = 0f
                }
                aim = g[2].toFloat()
                ghostX = tx
                ghostY = ty
                ghostFade.snap(g[2].toFloat())
                hold = holdAt(t).toFloat()
            }
        } else if (mode == MODE_TOUCH) {
            t = (t + dt * Touch.FILM_RATE) % LOOP
            aim = if (down) 1f else 0f
            if (auto && !down && now - lastTouch >= IDLE) {
                returnFrom = t
                returnAt = now
                mode = MODE_RETURN
            }
        } else {
            val u = seg(now - returnAt, 0.0, RETURN)
            val from = returnFrom
            val to = if (from > LOOP / 2) LOOP else 0.0
            t = from + (to - from) * smooth(u)
            hold = 1f - smooth(u).toFloat()
            vx = 0f
            vy = 0f
            if (u >= 1.0) {
                t = 0.0
                autoStart = now
                epoch += 1
                mode = MODE_AUTO
                hintFade.to(1f, 0.4f)
            }
        }

        prevFilm = t
        film = t
        touchOn += (aim - touchOn) * (1f - exp(-dt * Touch.EASE))
        serial += 1
        fill(t, dt, hold, tx, ty, touchOn, vx, vy)
        onFrame(frame)
        tick += 1
    }
}

/** the ones digit's place on its strip */
private fun onesOf(s: Double) = ((s % 10) + 10) % 10

/** the tens digit's place: it rolls while the ones go from 9 to 10 */
private fun tensOf(s: Double) = (floor(s / 10) % 10) + max(0.0, onesOf(s) - 9)

@Composable
fun NebulaDustSwirl(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    track: NebulaTrack = NebulaTrack(),
    /** seconds left on the sleep timer when the screen opens (keep ten or more in its minute: the loop counts ten down) */
    remaining: Double = SESSION,
    /** the whole session, seconds */
    total: Double = SESSION_TOTAL,
) {
    val reduced = rememberLiveReducedMotion()
    val view = LocalView.current
    BlockSystemBars(darkIcons = false)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1D))
            .clipToBounds(),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val compact = height < 740f
        val unit = min(width * Dust.SCALE_W, height * Dust.SCALE_H)
        val centreX = width * Dust.CENTRE_X
        val centreY = height * Dust.CENTRE_Y
        val minutes = floor(remaining / 60).toInt()
        val density = LocalDensity.current.density
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val engine = remember { NdsEngine() }
        val dustView = remember { arrayOfNulls<NdsDustView>(1) }
        /** null until the GL dust has answered */
        var gpu by remember { mutableStateOf<Boolean?>(null) }
        val fallback = gpu == false
        val painter = remember { NdsFallbackPainter() }

        SideEffect {
            engine.auto = autoPlay && !reduced
            engine.gate = gpu != null
            engine.width = width
            engine.height = height
            engine.centreX = centreX
            engine.centreY = centreY
            engine.unit = unit
            engine.onFrame = { frame -> dustView[0]?.present(frame) }
        }
        LaunchedEffect(engine, reduced) {
            if (reduced) {
                engine.still()
                return@LaunchedEffect
            }
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.016f else min(0.05f, (now - last) / 1_000_000_000f)
                    last = now
                    engine.step(dt)
                }
            }
        }
        // a still frame sent before the GL view existed is sent again once it does
        LaunchedEffect(gpu, reduced) {
            if (reduced && gpu == true) engine.still()
        }

        // the sky, and the cool haze of the disc (flattened to the disc's tilt)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFF0A0E1D),
                    0.45f to Color(0xFF121834),
                    0.8f to Color(0xFF1B1630),
                    1f to Color(0xFF140F22),
                ),
            )
            val c = Offset(centreX * density, centreY * density)
            val r = unit * 1.25f * density
            withTransform({ scale(1f, 0.62f, pivot = c) }) {
                drawCircle(
                    Brush.radialGradient(
                        0f to rgba(96, 128, 196, 0.2f),
                        0.55f to rgba(70, 90, 160, 0.08f),
                        1f to rgba(40, 50, 100, 0f),
                        center = c,
                        radius = r,
                    ),
                    radius = r,
                    center = c,
                )
            }
        }
        // the warm breath of the core
        val glowPaint = remember(density) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                // hardware canvases only honour mask filters from API 28
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    maskFilter = BlurMaskFilter(sigmaToRadius(6f * density), BlurMaskFilter.Blur.NORMAL)
                }
            }
        }
        val glowShader = remember(density, centreX, centreY, unit) {
            RadialGradient(
                centreX * density, centreY * density, unit * 0.68f * density,
                intArrayOf(rgba(255, 196, 140, 0.34f).toArgb(), rgba(214, 132, 128, 0.12f).toArgb(), rgba(120, 70, 110, 0f).toArgb()),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
            )
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    engine.tick
                    alpha = (0.82 + 0.18 * glowAt(engine.film)).toFloat()
                },
        ) {
            engine.tick
            val r = unit * (0.62f + 0.05f * glowAt(engine.film).toFloat()) * density
            glowPaint.shader = glowShader
            drawIntoCanvas { it.nativeCanvas.drawCircle(centreX * density, centreY * density, r, glowPaint) }
        }

        if (fallback) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                engine.tick
                drawIntoCanvas {
                    val canvas = it.nativeCanvas
                    val saved = canvas.save()
                    canvas.scale(density, density)
                    painter.draw(canvas, engine.frame)
                    canvas.restoreToCount(saved)
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    lateinit var created: NdsDustView
                    created = NdsDustView(ctx) { ok ->
                        if (ok) created.animate().alpha(1f).setDuration(220).start()
                        gpu = ok
                    }
                    created.alpha = 0f
                    dustView[0] = created
                    created
                },
                onRelease = { released ->
                    if (dustView[0] === released) dustView[0] = null
                    released.release()
                },
            )
        }

        // the timer ring round the core, from the top, clockwise
        val ring = unit * 0.5f
        val done = (1 - remaining / total).coerceIn(0.0, 1.0).toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(centreX * density, centreY * density)
            val r = ring * density
            drawCircle(rgba(246, 238, 230, 0.16f), radius = r, center = c, style = Stroke(1f * density))
            if (done > 0f) {
                drawArc(
                    rgba(255, 226, 196, 0.72f),
                    startAngle = -90f,
                    sweepAngle = 360f * done,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(2 * r, 2 * r),
                    style = Stroke(1.6f * density, cap = StrokeCap.Round),
                )
            }
        }
        val headA = -PI / 2 + 2 * PI * done
        val headX = centreX + ring * cos(headA).toFloat()
        val headY = centreY + ring * sin(headA).toFloat()
        Box(
            Modifier
                .offset((headX - 5f).dp, (headY - 5f).dp)
                .graphicsLayer {
                    engine.tick
                    alpha = (0.55 + 0.45 * glowAt(engine.film)).toFloat()
                }
                .size(10.dp)
                .cssBoxShadow(rgba(255, 214, 170, 0.9f), 0.dp, 10.dp, 5.dp)
                .background(Color(0xFFFFE6CC), CircleShape),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = insets.calculateTopPadding() + if (compact) 8.dp else 14.dp,
                    bottom = maxOf(insets.calculateBottomPadding(), 12.dp) + if (compact) 6.dp else 12.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NdsLabel(track.app, 20f, FontWeight.Bold, INK, Modifier.alignByBaseline(), spacing = -0.2f)
                NdsLabel(track.section, 15f, FontWeight.Medium, MUTE, Modifier.alignByBaseline())
            }
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(DIGIT.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NdsLabel("$minutes:", 48f, FontWeight.SemiBold, INK, spacing = -1f, line = DIGIT)
                    Digit(engine, tens = true, density, remaining, minutes)
                    Digit(engine, tens = false, density, remaining, minutes)
                }
                NdsLabel(
                    "left of ${(total / 60).roundToInt()} min", 15f, FontWeight.Medium, MUTE,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    center = true,
                )
                TrackCard(track, compact, density)
                NdsLabel(
                    track.hint, 13f, FontWeight.Medium, INK,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .graphicsLayer {
                            engine.tick
                            alpha = (engine.hint * 0.8f).coerceIn(0f, 1f)
                        },
                    center = true,
                )
            }
        }

        // the ghost finger
        Box(
            Modifier
                .graphicsLayer {
                    engine.tick
                    translationX = (engine.ghostX - GHOST / 2f) * density
                    translationY = (engine.ghostY - GHOST / 2f) * density
                    alpha = (engine.ghostA * 0.9f).coerceIn(0f, 1f)
                }
                .size(GHOST.dp)
                .background(rgba(246, 238, 230, 0.12f), CircleShape)
                .border(1.5.dp, rgba(246, 238, 230, 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(14.dp).background(rgba(246, 238, 230, 0.5f), CircleShape))
        }

        // one finger anywhere: the dust swirls round it and is dragged along
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(engine, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        first.consume()
                        val tracker = VelocityTracker()
                        tracker.addPosition(first.uptimeMillis, first.position)
                        engine.begin(first.position.x / density, first.position.y / density)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == first.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val v = tracker.calculateVelocity()
                                engine.move(change.position.x / density, change.position.y / density, v.x / density, v.y / density)
                            }
                        } finally {
                            engine.end()
                        }
                    }
                },
        )
    }
}

/** the playing track: a gradient cover, its title and detail, and the pause button */
@Composable
private fun TrackCard(track: NebulaTrack, compact: Boolean, density: Float) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .padding(top = if (compact) 14.dp else 22.dp)
            .fillMaxWidth()
            .cssBoxShadow(rgba(0, 0, 0, 0.3f), 1.dp, 2.dp, 22.dp)
            .cssBoxShadow(rgba(0, 0, 0, 0.32f), 16.dp, 36.dp, 22.dp)
            .background(rgba(30, 30, 58, 0.55f), shape)
            .border((1f / density).dp, rgba(246, 238, 230, 0.18f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = 52f * density
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        0f to Color(0xFFF2C49A),
                        0.35f to Color(0xFFB56F86),
                        0.72f to Color(0xFF3A3566),
                        1f to Color(0xFF1E1B38),
                        start = Offset(0.2f * cover, 0.1f * cover),
                        end = Offset(0.9f * cover, cover),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).background(rgba(255, 240, 224, 0.85f), CircleShape))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            NdsLabel(track.title, 17f, FontWeight.SemiBold, INK, ellipsis = true)
            NdsLabel(track.detail, 14f, FontWeight.Medium, MUTE, Modifier.padding(top = 2.dp), ellipsis = true)
        }
        Row(
            Modifier
                .size(48.dp)
                .cssBoxShadow(rgba(0, 0, 0, 0.3f), 1.dp, 2.dp, 24.dp)
                .cssBoxShadow(rgba(0, 0, 0, 0.25f), 8.dp, 18.dp, 24.dp)
                .background(Color(0xFFF6EEE6), CircleShape),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(2) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(16.dp)
                        .background(Color(0xFF1E1B38), RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/** one digit of the sleep timer's seconds: a strip of 0…9, 0 that slides under a 54 dp window */
@Composable
private fun Digit(engine: NdsEngine, tens: Boolean, density: Float, remaining: Double, minutes: Int) {
    Box(Modifier.height(DIGIT.dp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer {
                    engine.tick
                    val s = max(sessionAt(engine.film, remaining) - minutes * 60, 0.0)
                    translationY = -DIGIT * density * (if (tens) tensOf(s) else onesOf(s)).toFloat()
                },
        ) {
            for (d in DIGITS) {
                Box(Modifier.height(DIGIT.dp), contentAlignment = Alignment.Center) {
                    NdsLabel("$d", 48f, FontWeight.SemiBold, INK, spacing = -1f, line = DIGIT)
                }
            }
        }
    }
}

/** text in dp, so the system's font scale does not move the page */
@Composable
private fun NdsLabel(
    text: String,
    size: Float,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    spacing: Float = 0f,
    line: Float = 0f,
    center: Boolean = false,
    ellipsis: Boolean = false,
) {
    val density = LocalDensity.current
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        softWrap = false,
        overflow = if (ellipsis) TextOverflow.Ellipsis else TextOverflow.Clip,
        style = TextStyle(
            color = color,
            fontSize = with(density) { size.dp.toSp() },
            fontWeight = weight,
            letterSpacing = with(density) { spacing.dp.toSp() },
            lineHeight = if (line > 0f) with(density) { line.dp.toSp() } else TextStyle.Default.lineHeight,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            fontFeatureSettings = "tnum",
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}
