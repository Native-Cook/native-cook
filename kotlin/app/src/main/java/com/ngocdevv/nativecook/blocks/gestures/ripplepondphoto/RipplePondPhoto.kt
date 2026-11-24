// Ripple Pond Photo — a travel journal's photo page whose water answers the finger.
//
// The page is a full-bleed photo of a boat on still water with the entry's title over it. The water
// is a real 2D wave simulation on the GPU (RppWater.kt, constants in RppMotion.kt): a tap drops a
// ring, a drag leaves a wake, rings bounce off the screen's edges and die down, and the photo is seen
// through the surface. The title and the notes are Compose text above the water, so they stay sharp.
//
// autoPlay: LOOP = 10 s. A ghost finger taps twice, drags a wake across, taps once more, then the
// water is damped flat and cleared at t = 0, so frame 10 s equals frame 0. A touch takes over at once
// (the ghost fades); IDLE seconds after the last touch the water is calmed and the loop restarts at
// t = 0. Reduced motion: the still photo, no water and no loop.
//
// Thread model: one frame callback advances the clock, runs the script, collects the frame's drops
// and hands one small array to the water's render thread. Only graphicsLayer lambdas read the moving
// numbers, so a frame recomposes nothing.
package com.ngocdevv.nativecook.blocks.gestures.ripplepondphoto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val PHOTO = "ripplepondphoto/ripplepondphoto_rpp-cove.jpg"

private const val COPY_JOURNAL = "Halcyon"
private const val COPY_DAY = "Day 6 of 11"
private const val COPY_TITLE = "Still water at\nCala Brisa"
private const val COPY_DATE = "Thursday 12 June · 7:40 am"
private const val COPY_NOTE = "Anchored off the south beach. Not a breath of wind until nine, and the bottom clear at four metres."
private const val COPY_FACTS = "Water 23° · Wind calm"
private const val COPY_HINT = "Touch the water"

private val INK = Color(0xFFF4FBFA)
private val DEEP = Color(0xFF062E33)
private const val GHOST = 46f
/** taps that can be pressing into the water at once (the other two drop slots carry the wake) */
private const val TAP_SLOTS = 2
private const val MODE_AUTO = 0
private const val MODE_TOUCH = 1
private const val MODE_RETURN = 2

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

/** the clock, the script, the finger and the frame handed to the water; everything on the main thread */
private class RppEngine {
    /** bumped once a frame; the graphicsLayer lambdas read it */
    var tick by mutableIntStateOf(0)
        private set

    var auto = false
    /** true once the water (or its absence) is known: the loop waits at t = 0 until then */
    var gate = false
    var width = 1f
    var height = 1f
    var onFrame: (FloatArray) -> Unit = {}

    private var clock = 0.0
    private var ringZero = 0.0
    private var mode = MODE_AUTO
    private var autoStart = 0.0
    private var prevT = -1.0
    private var stepsDone = -1L
    private var epoch = 0
    private var returnAt = 0.0
    private var lastTouch = 0.0
    private var down = false
    /** the pointer that cuts the wake (the finger, or the ghost during the scripted drag), dp */
    private var penX = 0f
    private var penY = 0f
    private var wakeX = -1f
    private var wakeY = -1f
    private val tapBorn = LongArray(TAP_SLOTS) { -1L }
    private val tapU = FloatArray(TAP_SLOTS)
    private val tapV = FloatArray(TAP_SLOTS)
    private val rings = FloatArray(RING_COUNT * 4)
    private var ringCursor = 0
    private var ringGap = 0f
    private var level = 1f
    private val out = FloatArray(Frame.LENGTH)

    var ghostX = 0f
        private set
    var ghostY = 0f
        private set
    var ghostP = 0f
        private set
    private val ghostFade = Fade(0f)
    private val hintFade = Fade(1f)
    val ghostA get() = ghostFade.value
    val hint get() = hintFade.value

    /** start a tap at (u, v) on step `at`, in a free slot or over the oldest */
    private fun addTap(at: Long, u: Float, v: Float) {
        var slot = 0
        var oldest = Long.MAX_VALUE
        for (i in 0 until TAP_SLOTS) {
            if (tapBorn[i] < oldest) {
                oldest = tapBorn[i]
                slot = i
            }
        }
        tapBorn[slot] = at
        tapU[slot] = u
        tapV[slot] = v
    }

    /** add a ring for the fallback shader, over the oldest */
    private fun addRing(x: Float, y: Float, strength: Float) {
        val slot = ringCursor % RING_COUNT
        rings[slot * 4] = x
        rings[slot * 4 + 1] = y
        rings[slot * 4 + 2] = (clock - ringZero).toFloat()
        rings[slot * 4 + 3] = strength
        ringCursor = slot + 1
    }

    /** the water is flat here, so clearing it changes nothing on screen */
    private fun cut() {
        epoch += 1
        tapBorn.fill(-1L)
        rings.fill(0f)
        ringZero = clock
        wakeX = -1f
    }

    fun touchDown(x: Float, y: Float) {
        mode = MODE_TOUCH
        down = true
        lastTouch = clock
        ghostFade.to(0f, 0.15f)
        hintFade.to(0f, 0.25f)
        penX = x
        penY = y
        wakeX = -1f
        addTap(max(0L, stepsDone), x / width, y / height)
        addRing(x, y, 1f)
    }

    fun touchMove(x: Float, y: Float) {
        penX = x
        penY = y
        lastTouch = clock
    }

    fun touchUp() {
        down = false
        lastTouch = clock
    }

    /** reduced motion: nothing moves and nothing is drawn over the photo */
    fun still() {
        ghostFade.snap(0f)
        hintFade.snap(1f)
        mode = MODE_AUTO
        down = false
        prevT = -1.0
        stepsDone = -1L
        cut()
        tick += 1
    }

    fun step(dt: Float) {
        clock += dt
        val now = clock
        ghostFade.step(dt)
        hintFade.step(dt)

        // fixed steps, however long the frame was
        val target = floor(now * SIM_STEP_HZ).toLong()
        if (stepsDone < 0 || target - stepsDone > SIM_STEP_HZ / 4) stepsDone = target - 1
        val a = stepsDone
        val steps = min(SIM_MAX_STEPS.toLong(), target - a).toInt()
        val b = a + steps
        stepsDone = b

        var damp = SIM_DAMP
        var wake = false
        when (mode) {
            MODE_AUTO -> {
                if (!gate || !auto) {
                    autoStart = now
                    prevT = -1.0
                    ghostFade.snap(0f)
                } else {
                    val t = (((now - autoStart) % LOOP) + LOOP) % LOOP
                    if (t < prevT) cut()
                    for (tap in T_TAPS) {
                        if (prevT < tap[0] && t >= tap[0]) {
                            addTap(a, tap[1].toFloat(), tap[2].toFloat())
                            addRing(tap[1].toFloat() * width, tap[2].toFloat() * height, 1f)
                        }
                    }
                    val which = ghostTap(t)
                    if (which >= 0) {
                        ghostX = T_TAPS[which][1].toFloat() * width
                        ghostY = T_TAPS[which][2].toFloat() * height
                    } else {
                        val u = seg(t, T_DRAG_START, T_DRAG_END)
                        ghostX = dragX(u) * width
                        ghostY = dragY(u) * height
                    }
                    ghostFade.snap(ghostAlpha(t))
                    ghostP = ghostPress(t)
                    if (t >= T_DRAG_START && t < T_DRAG_END) {
                        penX = ghostX
                        penY = ghostY
                        wake = true
                    } else {
                        wakeX = -1f
                    }
                    damp = dampAt(t)
                    level = 1f - smooth(seg(t, T_CALM_START, T_CALM_END))
                    prevT = t
                }
            }
            MODE_TOUCH -> {
                wake = down
                if (!down) wakeX = -1f
                level = 1f
                if (auto && !down && now - lastTouch >= IDLE) {
                    mode = MODE_RETURN
                    returnAt = now
                }
            }
            else -> {
                val since = now - returnAt
                val k = smooth(seg(since, 0.0, RETURN_EASE))
                damp = SIM_DAMP + (SIM_CALM_DAMP - SIM_DAMP) * k
                level = 1f - k
                if (since >= RETURN_TOTAL) {
                    cut()
                    autoStart = now
                    prevT = -1.0
                    mode = MODE_AUTO
                    hintFade.to(1f, 0.4f)
                }
            }
        }

        // the frame: its drops are two tap slots, then the wake's midpoint and head
        out[Frame.EPOCH] = epoch.toFloat()
        out[Frame.STEPS] = steps.toFloat()
        out[Frame.C2] = SIM_C2
        out[Frame.DAMP] = damp
        var o = Frame.DROPS
        for (i in 0 until TAP_SLOTS) {
            val born = tapBorn[i]
            var depth = 0.0
            if (born >= 0 && steps > 0) {
                depth = DROP_TAP_DEPTH * (tapShare((b - born).toDouble() / DROP_TAP_STEPS) - tapShare((a - born).toDouble() / DROP_TAP_STEPS))
            }
            out[o++] = tapU[i]
            out[o++] = tapV[i]
            out[o++] = DROP_TAP_RADIUS
            out[o++] = depth.toFloat()
        }
        for (i in 0 until TAP_SLOTS) if (tapBorn[i] >= 0 && b - tapBorn[i] >= DROP_TAP_STEPS) tapBorn[i] = -1L

        var depth = 0f
        var fromX = penX
        var fromY = penY
        if (wake && steps > 0) {
            if (wakeX >= 0f) {
                fromX = wakeX
                fromY = wakeY
                val travel = sqrt((penX - fromX) * (penX - fromX) + (penY - fromY) * (penY - fromY))
                // a faster finger cuts deeper
                if (travel > 0.5f) depth = max(DROP_WAKE_MAX, DROP_WAKE_PER_PT * travel)
                // the fallback gets a small ring every 28 dp of wake
                ringGap += travel
                if (ringGap > 28f) {
                    ringGap = 0f
                    addRing(penX, penY, 0.3f)
                }
            }
            if (depth != 0f || wakeX < 0f) {
                wakeX = penX
                wakeY = penY
            }
        }
        out[o++] = (fromX + penX) / 2f / width
        out[o++] = (fromY + penY) / 2f / height
        out[o++] = DROP_WAKE_RADIUS
        out[o++] = depth / 2f
        out[o++] = penX / width
        out[o++] = penY / height
        out[o++] = DROP_WAKE_RADIUS
        out[o] = depth / 2f
        out[Frame.CLOCK] = (now - ringZero).toFloat()
        out[Frame.LEVEL] = level
        rings.copyInto(out, Frame.RINGS)
        onFrame(out)
        tick += 1
    }
}

@Composable
fun RipplePondPhoto(modifier: Modifier = Modifier, autoPlay: Boolean = true) {
    val reduced = rememberLiveReducedMotion()
    val context = LocalContext.current
    val view = LocalView.current
    BlockSystemBars(darkIcons = false)

    val photo by produceState<Bitmap?>(null, context) {
        value = withContext(Dispatchers.IO) {
            context.assets.open(PHOTO).use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(DEEP)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val rows = remember(width, height) { gridRows(width, height) }
        val engine = remember { RppEngine() }
        val water = remember { arrayOfNulls<RppWaterView>(1) }

        SideEffect {
            engine.auto = autoPlay && !reduced
            engine.width = width
            engine.height = height
            engine.onFrame = { frame -> water[0]?.present(frame) }
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

        val bitmap = photo
        if (bitmap != null) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (!reduced) {
                key(bitmap, rows) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            lateinit var created: RppWaterView
                            created = RppWaterView(ctx, bitmap, rows) { ok ->
                                engine.gate = true
                                if (ok) created.animate().alpha(1f).setDuration(220).start()
                            }
                            created.alpha = 0f
                            water[0] = created
                            created
                        },
                        onRelease = { released ->
                            if (water[0] === released) water[0] = null
                            released.release()
                        },
                    )
                }
            }
        }

        // the touch surface, above the water's view
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(engine, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        first.consume()
                        engine.touchDown(first.position.x / density, first.position.y / density)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == first.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            engine.touchMove(change.position.x / density, change.position.y / density)
                        }
                        engine.touchUp()
                    }
                },
        )

        // the page's text sits above the water, on two soft scrims
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding()
        val bottom = insets.calculateBottomPadding()
        val narrow = height < 700f
        val titleSize = if (narrow) 28f else 32f
        Box(
            Modifier
                .fillMaxWidth()
                .height(top + (if (narrow) 170.dp else 200.dp))
                .background(Brush.verticalGradient(0f to rgba(6, 46, 51, 0.62f), 0.55f to rgba(6, 46, 51, 0.3f), 1f to rgba(6, 46, 51, 0f))),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(bottom + (if (narrow) 170.dp else 200.dp))
                .background(Brush.verticalGradient(0f to rgba(4, 38, 44, 0f), 0.5f to rgba(4, 38, 44, 0.34f), 1f to rgba(4, 38, 44, 0.7f))),
        )

        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = top + 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label(COPY_JOURNAL, 15f, FontWeight.SemiBold, INK, Modifier.alignByBaseline(), spacing = 0.2f)
                Label(COPY_DAY, 13f, FontWeight.Medium, INK.copy(alpha = 0.74f), Modifier.alignByBaseline())
            }
            Label(COPY_TITLE, titleSize, FontWeight.Bold, INK, Modifier.padding(top = 22.dp), spacing = -0.6f, line = titleSize + 4f, lines = 2)
            Label(COPY_DATE, 15f, FontWeight.Medium, INK.copy(alpha = 0.8f), Modifier.padding(top = 8.dp))
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = maxOf(bottom, 14.dp) + 14.dp),
        ) {
            Label(COPY_NOTE, 15f, FontWeight.Normal, INK.copy(alpha = 0.9f), line = 21f, lines = 3)
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(COPY_FACTS, 13f, FontWeight.Medium, INK.copy(alpha = 0.74f))
                Label(
                    COPY_HINT, 13f, FontWeight.Medium, INK,
                    Modifier.graphicsLayer {
                        engine.tick
                        alpha = engine.hint * 0.62f
                    },
                )
            }
        }

        GhostFinger(
            Modifier.graphicsLayer {
                engine.tick
                translationX = (engine.ghostX - GHOST / 2f) * density
                translationY = (engine.ghostY - GHOST / 2f) * density
                val s = 1f - 0.16f * engine.ghostP
                scaleX = s
                scaleY = s
                alpha = engine.ghostA.coerceIn(0f, 1f)
            },
        )
    }
}

/** the normal distribution's edge, as a CSS blur falls off */
private fun edge(x: Float) = 1f / (1f + exp(1.7f * x))

/** a soft white touch: a faint halo, a thin ring and a brighter core, no dark edge */
@Composable
private fun GhostFinger(modifier: Modifier) {
    Box(modifier.size(GHOST.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(GHOST.dp)
                .cssBoxShadow(Color.White.copy(alpha = 0.38f), offsetY = 0.dp, blur = 18.dp, radius = (GHOST / 2f).dp),
        ) {
            val outer = size.minDimension / 2f
            val ring = 1.5.dp.toPx()
            val inner = outer - ring
            val c = Offset(outer, outer)
            drawCircle(Color.White.copy(alpha = 0.10f), outer, c)
            // inset 0 0 10px rgba(255,255,255,0.22): strongest at the ring, gone towards the middle
            val sigma = 5.dp.toPx()
            val stops = Array(9) { i ->
                val f = i / 8f
                val fromEdge = (1f - f) * inner
                f to Color.White.copy(alpha = 0.22f * edge(fromEdge / sigma))
            }
            drawCircle(Brush.radialGradient(*stops, center = c, radius = inner), inner, c)
            drawCircle(Color.White.copy(alpha = 0.7f), outer - ring / 2f, c, style = Stroke(ring))
        }
        Box(
            Modifier
                .size(14.dp)
                .cssBoxShadow(Color.White.copy(alpha = 0.6f), offsetY = 0.dp, blur = 8.dp, radius = 7.dp)
                .background(Color.White.copy(alpha = 0.62f), CircleShape),
        )
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
    lines: Int = 1,
) {
    val density = LocalDensity.current
    Text(
        text = text,
        modifier = modifier,
        maxLines = lines,
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
