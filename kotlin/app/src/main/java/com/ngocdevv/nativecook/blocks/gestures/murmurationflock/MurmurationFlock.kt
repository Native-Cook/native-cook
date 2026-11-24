// Murmuration Flock — a breathing session led by twenty thousand starlings.
//
// Dusk's wind-down screen: an OpenGL ES 3 pass (MfGl.kt, shaders in MfShaders.kt) places a flock
// against the evening sky, a dark ribbon that folds and streams. On the inhale it gathers into a soft
// circle, on the exhale it lets go and streams out again.
//
// autoPlay: LOOP = 12 s. Every bird's place is a pure function of its seeds and the loop time; a ghost
// finger parts the flock during the exhale, and what it pushed is blended out in the rest and cleared
// at the cut, so frame 12 s equals frame 0. A real touch takes over at once: birds flee and swirl
// around it while the film slows to a quarter. IDLE seconds after the last touch the film runs to the
// nearer end of the loop and restarts at t = 0. Reduced motion: the circle, still, no loop.
//
// Thread model: one frame callback advances the clock and hands one small array to the flock's render
// thread; labels and the timer are graphicsLayer lambdas of the film time, so a frame recomposes
// nothing. Without OpenGL ES 3 the block draws MfFallback.kt's 900 strokes instead.
package com.ngocdevv.nativecook.blocks.gestures.murmurationflock

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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class MurmurationCopy(
    val app: String = "Dusk",
    val session: String = "Evening wind-down",
    val settle: String = "Settle",
    val inhale: String = "Inhale",
    val exhale: String = "Exhale",
    val pace: String = "4 in, 1 hold, 4 out",
    val hint: String = "Touch the flock",
)

private val INK = Color(0xFFFFF4E6)
private val MUTE = rgba(255, 244, 230, 0.72f)
/** the timer's digit height, dp */
private const val DIGIT = 24f
private const val GHOST = 46f
private const val MODE_AUTO = 0
private const val MODE_TOUCH = 1
private const val MODE_RETURN = 2
private val DIGITS = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0)

/** Reanimated's default timing: a value eased towards a target over a duration */
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

/** the clock, the script, the finger and the frame handed to the flock; everything on the main thread */
private class MfEngine {
    /** bumped once a frame; the graphicsLayer and draw lambdas read it */
    var tick by mutableIntStateOf(0)
        private set

    var auto = false
    /** true once the flock (or its fallback) is known: the loop waits at t = 0 until then */
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
    private var touchOn = 0f
    private val g = DoubleArray(3)

    /** the film time the labels read */
    var film = 0.0
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
        ghostFade.to(0f, 0.15f)
        hintFade.to(0f, 0.25f)
    }

    fun move(x: Float, y: Float) {
        fingerX = x
        fingerY = y
        lastTouch = clock
    }

    fun end() {
        down = false
        lastTouch = clock
    }

    private fun fill(t: Double, dt: Float, hold: Float, tx: Float, ty: Float, on: Float) {
        frame[Frame.SERIAL] = serial.toFloat()
        frame[Frame.EPOCH] = epoch.toFloat()
        frame[Frame.T] = t.toFloat()
        frame[Frame.DT] = dt
        frame[Frame.HOLD] = hold
        frame[Frame.TOUCH_X] = tx
        frame[Frame.TOUCH_Y] = ty
        frame[Frame.TOUCH_ON] = on
        frame[Frame.WIDTH] = width
        frame[Frame.HEIGHT] = height
        frame[Frame.CENTRE_X] = centreX
        frame[Frame.CENTRE_Y] = centreY
        frame[Frame.UNIT] = unit
    }

    /** reduced motion: one still frame, the circle */
    fun still() {
        mode = MODE_AUTO
        down = false
        film = STILL
        ghostFade.snap(0f)
        serial = 1
        fill(STILL, 0f, 1f, 0f, 0f, 0f)
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

        if (mode == MODE_AUTO) {
            if (!gate || !auto) {
                autoStart = now
                t = 0.0
                ghostFade.snap(0f)
            } else {
                t = (now - autoStart) % LOOP
                if (t < prevFilm) epoch += 1
                ghostAt(t, g)
                tx = (g[0] * width).toFloat()
                ty = (g[1] * height).toFloat()
                aim = g[2].toFloat()
                ghostX = tx
                ghostY = ty
                ghostFade.snap(g[2].toFloat())
                hold = 1f - smooth(seg(t, T.SETTLE_A, T.SETTLE_B)).toFloat()
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
        fill(t, dt, hold, tx, ty, touchOn)
        onFrame(frame)
        tick += 1
    }
}

/** the ones digit's place on its strip */
private fun onesOf(s: Double) = ((s % 10) + 10) % 10

/** the tens digit's place: it rolls while the ones go from 9 to 10 */
private fun tensOf(s: Double) = (floor(s / 10) % 10) + max(0.0, onesOf(s) - 9)

@Composable
fun MurmurationFlock(modifier: Modifier = Modifier, autoPlay: Boolean = true, copy: MurmurationCopy = MurmurationCopy()) {
    val reduced = rememberLiveReducedMotion()
    val view = LocalView.current
    BlockSystemBars(darkIcons = false)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF2C3163))
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF2C3163),
                    0.3f to Color(0xFF5B4A82),
                    0.56f to Color(0xFFA9668A),
                    0.8f to Color(0xFFE58F73),
                    1f to Color(0xFFF6BE7E),
                ),
            ),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val compact = height < 740f
        val unit = min(width * Flock.SCALE_W, height * Flock.SCALE_H)
        val centreX = width * Flock.CENTRE_X
        val centreY = height * Flock.CENTRE_Y
        val density = LocalDensity.current.density
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val engine = remember { MfEngine() }
        val flockView = remember { arrayOfNulls<MfFlockView>(1) }
        /** null until the GL flock has answered */
        var gpu by remember { mutableStateOf<Boolean?>(null) }
        val fallback = gpu == false
        val painter = remember { MfFallbackPainter() }

        SideEffect {
            engine.auto = autoPlay && !reduced
            engine.gate = gpu != null
            engine.width = width
            engine.height = height
            engine.centreX = centreX
            engine.centreY = centreY
            engine.unit = unit
            engine.onFrame = { frame -> flockView[0]?.present(frame) }
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

        val guide = unit * Ring.RADIUS.toFloat() * 2f + 34f
        Box(
            Modifier
                .offset((centreX - guide / 2f).dp, (centreY - guide / 2f).dp)
                .size(guide.dp)
                .graphicsLayer {
                    engine.tick
                    val k = guideAt(engine.film).toFloat()
                    alpha = 0.3f * k
                    val s = 0.92f + 0.08f * k
                    scaleX = s
                    scaleY = s
                }
                .border(1.dp, rgba(255, 240, 222, 0.9f), CircleShape),
        )

        if (fallback) {
            Canvas(Modifier.fillMaxSize()) {
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
                    lateinit var created: MfFlockView
                    created = MfFlockView(ctx) { ok ->
                        if (ok) created.animate().alpha(1f).setDuration(220).start()
                        gpu = ok
                    }
                    created.alpha = 0f
                    flockView[0] = created
                    created
                },
                onRelease = { released ->
                    if (flockView[0] === released) flockView[0] = null
                    released.release()
                },
            )
        }

        // the ridges: a 400 × 160 drawing stretched to the width and 30 % of the height
        val ridgeH = (height * 0.3f).roundToInt().toFloat()
        Canvas(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(ridgeH.dp),
        ) {
            val sx = size.width / 400f
            val sy = size.height / 160f
            val far = Path().apply {
                moveTo(0f, 34f * sy)
                cubicTo(44f * sx, 18f * sy, 82f * sx, 24f * sy, 124f * sx, 36f * sy)
                cubicTo(170f * sx, 50f * sy, 208f * sx, 20f * sy, 256f * sx, 16f * sy)
                cubicTo(306f * sx, 12f * sy, 352f * sx, 36f * sy, 400f * sx, 24f * sy)
                lineTo(400f * sx, 160f * sy)
                lineTo(0f, 160f * sy)
                close()
            }
            val near = Path().apply {
                moveTo(0f, 66f * sy)
                cubicTo(60f * sx, 48f * sy, 104f * sx, 60f * sy, 158f * sx, 66f * sy)
                cubicTo(214f * sx, 72f * sy, 262f * sx, 50f * sy, 316f * sx, 54f * sy)
                cubicTo(350f * sx, 56f * sy, 376f * sx, 64f * sy, 400f * sx, 60f * sy)
                lineTo(400f * sx, 160f * sy)
                lineTo(0f, 160f * sy)
                close()
            }
            drawPath(far, Color(0xFF6E4468), alpha = 0.72f)
            drawPath(near, Color(0xFF2E2340))
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = insets.calculateTopPadding() + if (compact) 8.dp else 14.dp,
                    bottom = maxOf(insets.calculateBottomPadding(), 12.dp) + if (compact) 8.dp else 16.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Label(copy.app, 17f, FontWeight.Bold, INK)
                    Label(copy.session, 14f, FontWeight.Medium, MUTE, Modifier.padding(top = 2.dp))
                }
                Row(Modifier.height(DIGIT.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label("4:", 19f, FontWeight.SemiBold, INK, line = DIGIT)
                    Digit(engine, tens = true, density)
                    Digit(engine, tens = false, density)
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
                    Word(engine, 0, copy.settle, density)
                    Word(engine, 1, copy.inhale, density)
                    Word(engine, 2, copy.exhale, density)
                }
                Label(copy.pace, 15f, FontWeight.Medium, INK, Modifier.padding(top = 2.dp))
                Label(
                    copy.hint, 13f, FontWeight.Medium, INK,
                    Modifier
                        .padding(top = 8.dp)
                        .graphicsLayer {
                            engine.tick
                            alpha = engine.hint * 0.8f
                        },
                )
            }
        }

        GhostFinger(
            Modifier.graphicsLayer {
                engine.tick
                translationX = (engine.ghostX - GHOST / 2f) * density
                translationY = (engine.ghostY - GHOST / 2f) * density
                alpha = (engine.ghostA * 0.9f).coerceIn(0f, 1f)
            },
        )

        // one finger: the birds flee and swirl around it
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(engine, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        first.consume()
                        engine.begin(first.position.x / density, first.position.y / density)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == first.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                engine.move(change.position.x / density, change.position.y / density)
                            }
                        } finally {
                            engine.end()
                        }
                    }
                },
        )
    }
}

/** one digit of the timer: a strip of 0…9, 0 that slides under a 24 dp window */
@Composable
private fun Digit(engine: MfEngine, tens: Boolean, density: Float) {
    Box(Modifier.height(DIGIT.dp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer {
                    engine.tick
                    val s = sessionAt(engine.film)
                    translationY = -DIGIT * density * (if (tens) tensOf(s) else onesOf(s)).toFloat()
                },
        ) {
            for (d in DIGITS) {
                Box(Modifier.height(DIGIT.dp), contentAlignment = Alignment.Center) {
                    Label("$d", 19f, FontWeight.SemiBold, INK, line = DIGIT)
                }
            }
        }
    }
}

/** Settle, Inhale or Exhale: crossfades with a small rise */
@Composable
private fun Word(engine: MfEngine, index: Int, label: String, density: Float) {
    Label(
        label, 46f, FontWeight.Bold, INK,
        Modifier.graphicsLayer {
            engine.tick
            val k = wordAt(engine.film, index).toFloat()
            alpha = k
            translationY = 8f * (1f - k) * density
            val s = 0.97f + 0.03f * k
            scaleX = s
            scaleY = s
        },
        spacing = -0.8f, line = 56f,
    )
}

@Composable
private fun GhostFinger(modifier: Modifier) {
    Box(
        modifier
            .size(GHOST.dp)
            .background(rgba(255, 244, 230, 0.14f), CircleShape)
            .border(1.5.dp, rgba(255, 244, 230, 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(14.dp).background(rgba(255, 244, 230, 0.55f), CircleShape))
    }
}

/** text in dp, so the system's font scale does not move the page */
@Composable
private fun Label(
    text: String,
    size: Float,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    spacing: Float = 0f,
    line: Float = 0f,
) {
    val density = LocalDensity.current
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            color = color,
            fontSize = with(density) { size.dp.toSp() },
            fontWeight = weight,
            letterSpacing = with(density) { spacing.dp.toSp() },
            lineHeight = if (line > 0f) with(density) { line.dp.toSp() } else TextStyle.Default.lineHeight,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            fontFeatureSettings = "tnum",
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}
