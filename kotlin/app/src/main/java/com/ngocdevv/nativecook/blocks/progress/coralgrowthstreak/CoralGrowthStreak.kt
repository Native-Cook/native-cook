// Coral Growth Streak — a habit's week, kept as a reef that grows a ring for every day done.
//
// Tidepool's streak screen: a round window onto a sea bed where a Gray-Scott reaction-diffusion
// simulation on the GPU (CgsReef.kt, constants in CgsMotion.kt) grows brain coral. Each day that is
// completed fills its dot, rolls the count and opens the next ring of the reef with a few seeds; the
// coral fills the ring and stops. The seventh day sends a band of light across it.
//
// autoPlay: LOOP = 10 s. Seven days are completed 0.9 s apart, the bloom crosses, the reef holds, then
// the coral dissolves from the rim inwards while the dots drain; the field is cleared at t = 0, so
// frame 10 s equals frame 0. A touch on the reef takes over at once: the whole window opens and coral
// grows where the finger is. IDLE seconds after the last touch the reef dissolves and the loop
// restarts at t = 0. Reduced motion: the full week, a still reef, no loop.
//
// Thread model: one frame callback advances the clock, decides the frame's steps and seeds and hands
// one small array to the reef's render thread; only graphicsLayer and draw lambdas read the moving
// numbers, so a frame recomposes nothing.
package com.ngocdevv.nativecook.blocks.progress.coralgrowthstreak

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val COPY_APP = "Tidepool"
private const val COPY_WEEK = "Week 47"
private const val COPY_HABIT = "Morning swim"
private const val COPY_LINE = "A ring of coral for every day you show up."
private const val COPY_UNIT = "day streak"
private const val COPY_BEST = "Best 21"
private const val COPY_HINT = "Touch the reef to plant coral"
private val COPY_DAYS = listOf("M", "T", "W", "T", "F", "S", "S")
private val COPY_STATS = listOf("This month" to "18 of 24", "Average" to "34 min", "Water" to "17°")

private val INK = Color(0xFFEAF6F4)
private val MUTE = rgba(234, 246, 244, 0.62f)
private val CORAL = Color(0xFFF4705F)
private val DEEP = Color(0xFF05222B)
private val PALE_07 = rgba(214, 240, 236, 0.07f)
private val PALE_10 = rgba(214, 240, 236, 0.10f)
private val PALE_16 = rgba(214, 240, 236, 0.16f)
private val PALE_18 = rgba(214, 240, 236, 0.18f)
private val PALE_20 = rgba(214, 240, 236, 0.20f)
/** the count's digit height, dp */
private const val DIGIT = 56f
private const val DOT = 36f
private const val MODE_AUTO = 0
private const val MODE_TOUCH = 1
private const val MODE_RETURN = 2
/** the still frame shown under reduced motion: after the bloom, before the fade */
private const val STILL = 8.3

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

/** the clock, the script, the finger and the frame handed to the reef; everything on the main thread */
private class CgsEngine {
    /** bumped once a frame; the graphicsLayer and draw lambdas read it */
    var tick by mutableIntStateOf(0)
        private set

    var auto = false
    /** true once the reef (or its fallback) is known: the loop waits at t = 0 until then */
    var gate = false
    var reefDp = 1f
    var canStep: () -> Boolean = { true }
    var onFrame: (FloatArray) -> Unit = {}

    var clock = 0.0
        private set
    private var mode = MODE_AUTO
    private var autoStart = 0.0
    private var prevT = -1.0
    /** steps run since the field was last cleared, and the next day to seed */
    private var stepsDone = 0
    private var nextDay = 0
    private var epoch = 0
    private var returnAt = 0.0
    private var lastTouch = 0.0
    private var down = false
    /** the finger on the reef, dp in the window, and where it last planted */
    private var penX = 0f
    private var penY = 0f
    private var plantX = -100f
    private var plantY = -100f
    private val out = FloatArray(Frame.LENGTH)

    // what the views and the fallback read
    var time = 0.0
        private set
    var level = 1f
        private set
    var bloom = 0f
        private set
    var ring = 0f
        private set
    private var phase = 0f
    private val hintFade = Fade(1f)
    val hint get() = hintFade.value
    val touches = FloatArray(TOUCHES * 4)
    private var touchCursor = 0

    private fun addTouch(x: Float, y: Float, born: Double) {
        val slot = touchCursor % TOUCHES
        touches[slot * 4] = x
        touches[slot * 4 + 1] = y
        touches[slot * 4 + 2] = born.toFloat()
        touches[slot * 4 + 3] = 1f
        touchCursor = slot + 1
    }

    /** the view shows the bare sea bed here whatever the field holds, so clearing it changes nothing on screen */
    private fun cut() {
        epoch += 1
        stepsDone = 0
        nextDay = 0
        touches.fill(0f)
    }

    /** true if the touch landed on the reef */
    fun touchDown(x: Float, y: Float): Boolean {
        val dx = x / reefDp - 0.5f
        val dy = y / reefDp - 0.5f
        if (dx * dx + dy * dy > 0.25f) return false
        mode = MODE_TOUCH
        down = true
        lastTouch = clock
        hintFade.to(0f, 0.25f)
        penX = x
        penY = y
        plantX = -100f
        plantY = -100f
        return true
    }

    fun touchMove(x: Float, y: Float) {
        if (!down) return
        penX = x
        penY = y
        lastTouch = clock
    }

    fun touchUp() {
        if (!down) return
        down = false
        lastTouch = clock
    }

    /** reduced motion: the full week, a still reef */
    fun still() {
        mode = MODE_AUTO
        down = false
        prevT = -1.0
        cut()
        time = STILL
        level = 1f
        bloom = 0f
        ring = ringFrac(DAYS).toFloat()
        hintFade.snap(1f)
        tick += 1
    }

    fun step(dt: Float) {
        val before = clock
        clock += dt
        val now = clock
        hintFade.step(dt)
        phase = ((now / LOOP) % 1.0).toFloat()

        var steps = 0
        var open = ringFrac(DAYS)
        var seedDay = -1
        var plant = false
        if (mode == MODE_AUTO) {
            if (!gate || !auto) {
                autoStart = now
                prevT = -1.0
                time = 0.0
                level = 1f
                bloom = 0f
                ring = 0f
                open = 0.0
            } else {
                val t = (((now - autoStart) % LOOP) + LOOP) % LOOP
                if (t < prevT) cut()
                // fixed steps, however long the frame was, counted from the cut
                val done = stepsDone
                steps = if (canStep()) max(0, min(SIM_MAX_STEPS, floor(t * SIM_STEP_HZ).toInt() - done)) else 0
                val day = nextDay
                if (day < DAYS && steps > 0) {
                    val at = DAY_STEP[day]
                    if (done >= at) {
                        // the day's seeds go in with this frame's first step, which is step `at` exactly
                        seedDay = day
                        nextDay = day + 1
                    } else if (done + steps > at) {
                        steps = at - done
                    }
                }
                stepsDone = done + steps
                open = ringFrac(nextDay)
                time = t
                level = levelAt(t).toFloat()
                bloom = bloomAt(t).toFloat()
                ring = ringSoftAt(t).toFloat()
                prevT = t
            }
        } else {
            steps = if (canStep()) min(SIM_MAX_STEPS, floor(now * SIM_STEP_HZ).toInt() - floor(before * SIM_STEP_HZ).toInt()) else 0
            stepsDone += steps
            if (mode == MODE_TOUCH) {
                level = min(1f, level + dt * 4f)
                bloom = 0f
                ring = min(ringFrac(DAYS).toFloat(), ring + dt * 2f)
                if (down && steps > 0) {
                    val dx = penX - plantX
                    val dy = penY - plantY
                    // one seed where the finger lands, then one every 5 dp of travel
                    if (dx * dx + dy * dy > 25f) {
                        plant = true
                        plantX = penX
                        plantY = penY
                        addTouch(penX, penY, now)
                    }
                }
                if (auto && !down && now - lastTouch >= IDLE) {
                    mode = MODE_RETURN
                    returnAt = now
                }
            } else {
                val since = now - returnAt
                level = 1f - smooth(seg(since, 0.0, RETURN_EASE)).toFloat()
                if (since >= RETURN_TOTAL) {
                    cut()
                    autoStart = now
                    prevT = -1.0
                    time = 0.0
                    level = 1f
                    ring = 0f
                    mode = MODE_AUTO
                    hintFade.to(1f, 0.4f)
                    steps = 0
                    open = 0.0
                }
            }
        }

        out[Frame.EPOCH] = epoch.toFloat()
        out[Frame.STEPS] = steps.toFloat()
        out[Frame.RADIUS] = (open * SIM_GRID / 2.0).toFloat()
        out[Frame.LEVEL] = level
        out[Frame.BLOOM] = bloom
        out[Frame.PHASE] = phase
        val seeds = if (seedDay >= 0) SEEDS[seedDay] else null
        for (i in 0 until SIM_SEED_SLOTS) {
            val o = Frame.SEEDS + i * 4
            if (seeds != null && i * 2 < seeds.size) {
                out[o] = seeds[i * 2]
                out[o + 1] = seeds[i * 2 + 1]
                out[o + 2] = 1f
            } else if (plant && i == 0) {
                out[o] = penX / reefDp
                out[o + 1] = penY / reefDp
                out[o + 2] = 1f
            } else {
                out[o] = 0f
                out[o + 1] = 0f
                out[o + 2] = 0f
            }
            out[o + 3] = 0f
        }
        onFrame(out)
        tick += 1
    }
}

private class Inset(val color: Color, val y: Dp, val blur: Dp)

/** CSS inset box-shadows, baked once per size into one bitmap and drawn over the content */
private fun Modifier.insetShadows(radius: Dp, vararg layers: Inset): Modifier = drawWithCache {
    val w = size.width.roundToInt()
    val h = size.height.roundToInt()
    var image: ImageBitmap? = null
    if (w > 0 && h > 0) {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val r = radius.toPx()
        val shape = android.graphics.Path().apply {
            addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(shape)
        for (layer in layers) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = layer.color.toArgb()
                val sigma = layer.blur.toPx() / 2f
                if (sigma > 0f) maskFilter = BlurMaskFilter(max(0.1f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
            }
            val dy = layer.y.toPx()
            val margin = layer.blur.toPx() * 3f + kotlin.math.abs(dy) + 2f
            val ring = android.graphics.Path().apply {
                fillType = android.graphics.Path.FillType.EVEN_ODD
                addRect(-margin, -margin, w + margin, h + margin, android.graphics.Path.Direction.CW)
                addRoundRect(0f, dy, w.toFloat(), h + dy, r, r, android.graphics.Path.Direction.CW)
            }
            canvas.drawPath(ring, paint)
        }
        image = bitmap.asImageBitmap()
    }
    onDrawWithContent {
        drawContent()
        image?.let { drawImage(it) }
    }
}

@Composable
fun CoralGrowthStreak(modifier: Modifier = Modifier, autoPlay: Boolean = true) {
    val reduced = rememberLiveReducedMotion()
    val view = LocalView.current
    BlockSystemBars(darkIcons = false)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(DEEP)
            .background(Brush.verticalGradient(0f to Color(0xFF0B3540), 0.5f to Color(0xFF072A34), 1f to Color(0xFF041C24))),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val compact = height < 740f
        /** the reef window's diameter, dp */
        val reef = max(200f, minOf(320f, width - 76f, height * 0.36f)).roundToInt().toFloat()
        val density = LocalDensity.current.density
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val engine = remember { CgsEngine() }
        val reefView = remember { arrayOfNulls<CgsReefView>(1) }
        /** null until the GL reef has answered */
        var gpu by remember { mutableStateOf<Boolean?>(null) }
        val still = reduced || gpu == false
        val baked by produceState<CgsBaked?>(null, still) {
            if (still && value == null) value = withContext(Dispatchers.Default) { bakeFallback() }
        }
        val painter = remember { CgsFallbackPainter() }

        SideEffect {
            engine.auto = autoPlay && !reduced
            engine.gate = gpu != null
            engine.reefDp = reef
            engine.canStep = { reefView[0]?.canStep ?: true }
            engine.onFrame = { frame -> reefView[0]?.present(frame) }
        }
        LaunchedEffect(engine, reduced) {
            if (reduced) {
                // a new GL view has to report again if motion comes back
                gpu = null
                engine.gate = false
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

        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = insets.calculateTopPadding() + if (compact) 8.dp else 14.dp,
                    bottom = maxOf(insets.calculateBottomPadding(), 12.dp) + if (compact) 6.dp else 14.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Label(COPY_APP, 15f, FontWeight.SemiBold, INK, Modifier.alignByBaseline(), spacing = 0.2f)
                    Label(COPY_WEEK, 13f, FontWeight.Medium, MUTE, Modifier.alignByBaseline())
                }
                Label(
                    COPY_HABIT, if (compact) 26f else 32f, FontWeight.Bold, INK,
                    Modifier.padding(top = if (compact) 6.dp else 14.dp),
                    spacing = -0.6f, line = if (compact) 30f else 38f,
                )
                Label(COPY_LINE, 15f, FontWeight.Normal, MUTE, Modifier.padding(top = 4.dp), line = 20f)
            }

            // the window's rim: a pale ring with a top edge highlight, a near and a far shadow
            val rim = reef + 20f
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(rim.dp)
                    .graphicsLayer {
                        engine.tick
                        val s = 1f + 0.02f * sin(PI * engine.bloom).toFloat()
                        scaleX = s
                        scaleY = s
                    }
                    .cssBoxShadow(rgba(0, 8, 12, 0.55f), offsetY = 26.dp, blur = 48.dp, radius = (rim / 2f).dp)
                    .cssBoxShadow(rgba(0, 8, 12, 0.4f), offsetY = 4.dp, blur = 10.dp, radius = (rim / 2f).dp)
                    .background(PALE_10, CircleShape)
                    .border(1.dp, PALE_18, CircleShape)
                    .insetShadows((rim / 2f).dp, Inset(rgba(255, 255, 255, 0.28f), 1.dp, 0.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(reef.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A3440)),
                ) {
                    if (still) {
                        Canvas(Modifier.fillMaxSize()) {
                            engine.tick
                            val art = baked ?: return@Canvas
                            drawIntoCanvas {
                                painter.draw(
                                    it.nativeCanvas, art, size.width, reef, engine.ring, engine.level,
                                    engine.bloom, engine.clock, engine.touches,
                                )
                            }
                        }
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                lateinit var created: CgsReefView
                                created = CgsReefView(ctx) { ok ->
                                    if (ok) created.animate().alpha(1f).setDuration(220).start()
                                    gpu = ok
                                }
                                created.alpha = 0f
                                reefView[0] = created
                                created
                            },
                            onRelease = { released ->
                                if (reefView[0] === released) reefView[0] = null
                                released.release()
                            },
                        )
                    }
                    // the glass over the water: shaded under the rim's upper lip, a faint light along the lower edge
                    Box(
                        Modifier
                            .fillMaxSize()
                            .insetShadows(
                                (reef / 2f).dp,
                                Inset(rgba(0, 10, 14, 0.6f), 10.dp, 22.dp),
                                Inset(rgba(214, 240, 236, 0.14f), (-2).dp, 5.dp),
                            )
                            .pointerInput(engine, reduced) {
                                if (reduced) return@pointerInput
                                awaitEachGesture {
                                    val first = awaitFirstDown(requireUnconsumed = false)
                                    if (!engine.touchDown(first.position.x / density, first.position.y / density)) return@awaitEachGesture
                                    first.consume()
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == first.id } ?: break
                                            if (!change.pressed) break
                                            change.consume()
                                            engine.touchMove(change.position.x / density, change.position.y / density)
                                        }
                                    } finally {
                                        engine.touchUp()
                                    }
                                }
                            },
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Box(
                    Modifier
                        .height(DIGIT.dp)
                        .defaultMinSize(minWidth = 34.dp)
                        .clipToBounds(),
                ) {
                    Column(
                        Modifier
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                            .graphicsLayer {
                                engine.tick
                                translationY = -DIGIT * density * (countAt(engine.time) * engine.level).toFloat()
                            },
                    ) {
                        for (n in 0..7) {
                            Box(Modifier.height(DIGIT.dp), contentAlignment = Alignment.CenterStart) {
                                Label("$n", 52f, FontWeight.Bold, INK, spacing = -1f, line = DIGIT)
                            }
                        }
                    }
                }
                Label(COPY_UNIT, 17f, FontWeight.SemiBold, INK, Modifier.padding(start = 8.dp, bottom = 10.dp).weight(1f))
                Box(
                    Modifier
                        .padding(bottom = 9.dp)
                        .height(28.dp)
                        .background(PALE_10, RoundedCornerShape(14.dp))
                        .border(Dp.Hairline, PALE_20, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Label(COPY_BEST, 13f, FontWeight.SemiBold, INK)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until DAYS) DayDot(i, engine)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .cssBoxShadow(rgba(0, 8, 12, 0.3f), offsetY = 12.dp, blur = 24.dp, radius = 20.dp)
                    .background(PALE_07, RoundedCornerShape(20.dp))
                    .border(Dp.Hairline, PALE_16, RoundedCornerShape(20.dp))
                    .insetShadows(20.dp, Inset(rgba(255, 255, 255, 0.12f), 1.dp, 0.dp))
                    .padding(vertical = 14.dp)
                    .height(IntrinsicSize.Min),
            ) {
                COPY_STATS.forEachIndexed { i, (label, value) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .drawBehind {
                                if (i > 0) drawRect(PALE_18, size = androidx.compose.ui.geometry.Size(1f, size.height))
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Label(value, 17f, FontWeight.SemiBold, INK)
                        Label(label, 12f, FontWeight.Medium, MUTE, Modifier.padding(top = 3.dp))
                    }
                }
            }

            Label(
                COPY_HINT, 13f, FontWeight.Medium, INK,
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        engine.tick
                        alpha = engine.hint * 0.7f
                    },
                align = TextAlign.Center,
            )
        }
    }
}

/** one day of the week: its letter and a dot that fills when the day is done */
@Composable
private fun DayDot(index: Int, engine: CgsEngine) {
    Column(Modifier.width(DOT.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(COPY_DAYS[index], 12f, FontWeight.SemiBold, MUTE, Modifier.padding(bottom = 8.dp))
        Box(
            Modifier
                .size(DOT.dp)
                .background(PALE_07, CircleShape)
                .border(1.dp, PALE_16, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(DOT.dp)
                    .graphicsLayer {
                        engine.tick
                        val u = seg(engine.time, dayTime(index), dayTime(index) + T_DOT)
                        val k = engine.level
                        alpha = (min(1.0, u * 3.0) * k).toFloat()
                        val s = max(0.01, easeOutBack(u) * (0.6 + 0.4 * k)).toFloat()
                        scaleX = s
                        scaleY = s
                    }
                    .cssBoxShadow(rgba(244, 112, 95, 0.35f), offsetY = 4.dp, blur = 10.dp, radius = (DOT / 2f).dp)
                    .background(CORAL, CircleShape)
                    .insetShadows(
                        (DOT / 2f).dp,
                        Inset(rgba(255, 226, 200, 0.6f), 1.dp, 0.dp),
                        Inset(rgba(150, 40, 60, 0.35f), (-3).dp, 6.dp),
                    ),
            )
            // a check mark: the right and bottom edges of a small box, turned 45°
            Canvas(
                Modifier
                    .offset(y = (-1.5).dp)
                    .size(7.dp, 13.dp)
                    .graphicsLayer {
                        engine.tick
                        val u = seg(engine.time, dayTime(index) + 0.12, dayTime(index) + T_DOT + 0.1)
                        alpha = (u * engine.level).toFloat()
                        rotationZ = 45f
                        val s = (0.5 + 0.5 * easeOutBack(u)).toFloat()
                        scaleX = s
                        scaleY = s
                    },
            ) {
                val line = 2.5.dp.toPx()
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width - line / 2f, 0f)
                    lineTo(size.width - line / 2f, size.height - line / 2f)
                    lineTo(0f, size.height - line / 2f)
                }
                drawPath(path, Color(0xFF4A0F1E), style = Stroke(line, cap = StrokeCap.Butt, join = StrokeJoin.Round))
            }
        }
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
    align: TextAlign = TextAlign.Unspecified,
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
            textAlign = align,
            letterSpacing = with(density) { spacing.dp.toSp() },
            lineHeight = if (line > 0f) with(density) { line.dp.toSp() } else TextStyle.Default.lineHeight,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            fontFeatureSettings = "tnum",
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}
