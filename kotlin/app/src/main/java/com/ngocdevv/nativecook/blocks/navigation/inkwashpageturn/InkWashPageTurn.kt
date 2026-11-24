// Ink Wash Page Turn — a poetry reader whose pages turn by ink bleeding through wet paper.
//
// Inkwell shows one short poem to a page. The three pages are baked into bitmaps once (IwpPages.kt)
// and one AGSL shader (IwpShader.kt) draws the turn: from the point the finger touched, a noisy,
// fibrous front spreads across the paper, dark with ink at its edge, and the next page shows through
// behind it. The turn is one number, progress, so a swipe scrubs it: left for the next page, right
// for the one before, and a release completes the turn or lets it draw back, carrying the finger's
// speed. A tap turns the page by itself from where it lands.
//
// autoPlay: LOOP = 10 s. A ghost finger swipes page 1 to 2 from the lower right, 2 to 3 from the upper
// left, then taps the middle and page 1 blooms back; every curve is a function of the loop time, so
// frame 10 s equals frame 0. A touch takes over at once; IDLE seconds after the last touch the reader
// bleeds back to page 1 if it is elsewhere and the loop restarts at t = 0. Reduced motion: pages
// change at once, no bleed and no loop.
//
// Thread model: one frame callback writes the turn and the ghost; only draw and graphicsLayer lambdas
// read them, so a frame recomposes nothing, and a frame in which nothing moved invalidates nothing.
package com.ngocdevv.nativecook.blocks.navigation.inkwashpageturn

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val COPY_APP = "Inkwell"
private const val COPY_BOOK = "Small Hours"
private const val COPY_OF = "of 3"
private const val COPY_HINT = "Swipe to turn the page"

private val TEXT_INK = Color(0xFF26231E)
private val MUTE_INK = Color(0xFF8C8576)
private val PAPER = Color(PAPER_ARGB)
private const val GHOST = 46f
/** the page dots: their size, pitch and the moving pill's width, dp */
private const val DOT = 6f
private const val PITCH = 16f
private const val PILL = 18f
private const val MODE_AUTO = 0
/** a finger is down, or was a moment ago */
private const val MODE_TOUCH = 1
/** bleeding back to page 1 before the loop restarts */
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

/** the clock, the script and the finger; everything on the main thread */
private class IwpEngine {
    /** bumped when something on screen changed; the draw and graphicsLayer lambdas read it */
    var tick by mutableIntStateOf(0)
        private set

    var auto = false
    var reduced = false
    var width = 1f
    var height = 1f

    private var clock = 0.0
    private var mode = MODE_AUTO
    private var autoStart = 0.0
    // the turn: the page on show, the page arriving, how far the ink has spread, where it started
    var from = 0
        private set
    var to = 1
        private set
    var progress = 0.0
        private set
    var originX = 0f
        private set
    var originY = 0f
        private set
    // a finger's turn
    private var down = false
    private var grab = false
    private var travel = 0f
    private var lastTouch = 0.0
    // the settle after a release (or a tap, or the way back to page 1)
    private var running = false
    private var runAt = 0.0
    private var runFrom = 0.0
    private var runSpeed = 0.0
    private var runTarget = 1.0
    private var runTap = false
    // the ghost finger
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
    private val shown = FloatArray(10) { Float.NaN }

    private fun startRun(x0: Double, speed: Double, target: Double, tap: Boolean) {
        runFrom = x0
        runSpeed = speed
        runTarget = target
        runTap = tap
        runAt = clock
        running = true
    }

    /** a touch landed; true if it took the page (and so gets a haptic) */
    fun begin(x: Float, y: Float): Boolean {
        val wasAuto = mode == MODE_AUTO
        mode = MODE_TOUCH
        down = true
        grab = false
        travel = 0f
        lastTouch = clock
        ghostFade.to(0f, 0.15f)
        hintFade.to(0f, 0.25f)
        if (running) return false
        val p = progress
        if (wasAuto && p > 0.0 && p < 1.0) {
            // the loop was mid-turn: let that turn finish by itself
            startRun(p, 0.0, 1.0, false)
            return false
        }
        if (p >= 1.0) {
            from = to
            progress = 0.0
        }
        grab = true
        originX = x
        originY = y
        return true
    }

    fun update(dx: Float, dy: Float) {
        lastTouch = clock
        if (!grab) return
        travel = maxOf(travel, abs(dx), abs(dy))
        // left for the next page, right for the one before; crossing back over the start swaps them
        to = (from + if (dx <= 0f) 1 else PAGES - 1) % PAGES
        if (reduced) return
        progress = scrub(abs(dx) / (TURN_SCRUB * width))
    }

    /** the finger lifted at x with the drag dx and velocity vx (dp/s); true if a turn was committed by the drag */
    fun end(x: Float, dx: Float, vx: Float): Boolean {
        var haptic = false
        if (grab) {
            grab = false
            val tapped = travel < TURN_TAP
            if (tapped) to = (from + if (x < width * TURN_BACK_ZONE) PAGES - 1 else 1) % PAGES
            val speed = (if (dx <= 0f) -vx else vx) / (TURN_SCRUB * width) * (if (tapped) 0.0 else 1.0)
            val commit = tapped || progress + TURN_PROJECT * speed > TURN_COMMIT || (reduced && abs(dx) > 40f)
            if (reduced) {
                if (commit) {
                    from = to
                    haptic = true
                }
                progress = 0.0
            } else {
                startRun(if (tapped) 0.0 else progress, speed.coerceIn(-4.0, 4.0), if (commit) 1.0 else 0.0, tapped)
                haptic = commit && !tapped
            }
        }
        down = false
        lastTouch = clock
        return haptic
    }

    /** cancelled mid-drag: draw back */
    fun cancel() {
        if (grab) {
            grab = false
            if (progress > 0.0) startRun(progress, 0.0, 0.0, false)
        }
        down = false
        lastTouch = clock
    }

    private fun backToLoop(now: Double) {
        autoStart = now
        mode = MODE_AUTO
        hintFade.to(1f, 0.4f)
    }

    private fun advance(now: Double) {
        if (mode == MODE_AUTO) {
            if (!auto) {
                autoStart = now
                return
            }
            val t = (((now - autoStart) % LOOP) + LOOP) % LOOP
            val k = turnAt(t)
            from = k
            to = (k + 1) % PAGES
            when (k) {
                0 -> {
                    originX = width * T_FIRST.x.toFloat()
                    originY = height * T_FIRST.y.toFloat()
                    progress = swipeAt(t, T_FIRST)
                }
                1 -> {
                    originX = width * T_SECOND.x.toFloat()
                    originY = height * T_SECOND.y.toFloat()
                    progress = swipeAt(t, T_SECOND)
                }
                else -> {
                    originX = width * T_TAP_X.toFloat()
                    originY = height * T_TAP_Y.toFloat()
                    progress = tapAt(t)
                }
            }
            ghostX = ghostXAt(t).toFloat() * width
            ghostY = ghostYAt(t).toFloat() * height
            ghostFade.snap(ghostAlpha(t).toFloat())
            ghostP = ghostPress(t).toFloat()
            return
        }

        if (running) {
            val tau = now - runAt
            val until = if (runTap) TAP_SETTLE_UNTIL else SETTLE_UNTIL
            progress = settle(
                runFrom, runSpeed, runTarget, tau,
                if (runTap) TURN_TAP_OMEGA else TURN_OMEGA,
                if (runTap) TAP_SETTLE_FROM else SETTLE_FROM,
                until,
            )
            if (tau >= until) {
                running = false
                if (runTarget == 1.0) {
                    // the turn is done: the page that arrived is the page on show
                    from = to
                    to = (to + 1) % PAGES
                }
                progress = 0.0
                if (mode == MODE_RETURN) backToLoop(now)
            }
            return
        }

        if (mode == MODE_TOUCH && auto && !down && now - lastTouch >= IDLE) {
            if (from == 0) {
                // already on page 1: the loop picks up from its rest
                backToLoop(now)
            } else {
                mode = MODE_RETURN
                to = 0
                originX = width * T_TAP_X.toFloat()
                originY = height * T_TAP_Y.toFloat()
                startRun(0.0, 0.0, 1.0, true)
            }
        }
    }

    fun step(dt: Float) {
        clock += dt
        ghostFade.step(dt)
        hintFade.step(dt)
        advance(clock)
        publish()
    }

    /** one redraw only when a number the screen shows has changed */
    fun publish() {
        val now = floatArrayOf(from.toFloat(), to.toFloat(), progress.toFloat(), originX, originY, ghostX, ghostY, ghostA, ghostP, hint)
        if (!now.contentEquals(shown)) {
            now.copyInto(shown)
            tick += 1
        }
    }
}

/** how much page `index` is the one on show, 0…1: the number and the dots change over the middle of a turn */
private fun weight(index: Int, from: Int, to: Int, progress: Double): Float {
    val e = smooth(seg(progress, 0.25, 0.75))
    return ((if (index == from) 1.0 - e else 0.0) + (if (index == to) e else 0.0)).toFloat()
}

@Composable
fun InkWashPageTurn(modifier: Modifier = Modifier, autoPlay: Boolean = true) {
    val reduced = rememberLiveReducedMotion()
    val view = LocalView.current
    BlockSystemBars(darkIcons = true)

    BoxWithConstraints(modifier.fillMaxSize().background(PAPER)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val compact = height < 740f
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding()
        val bottom = insets.calculateBottomPadding()
        val density = LocalDensity.current.density
        val scale = pageScale(density)
        val engine = remember { IwpEngine() }

        val pages by produceState<List<Bitmap>?>(null, width, height, top, bottom, scale) {
            value = withContext(Dispatchers.Default) { bakePages(width, height, top.value, bottom.value, scale) }
        }
        val bleed = remember(pages) {
            pages?.let { list ->
                val runtime = if (Build.VERSION.SDK_INT >= 33) runCatching { IwpRuntimeBleed(list, scale) }.getOrNull() else null
                runtime ?: IwpDrawnBleed(list)
            }
        }
        val plain = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
        val full = remember { RectF() }

        SideEffect {
            engine.auto = autoPlay && !reduced
            engine.reduced = reduced
            engine.width = width
            engine.height = height
        }
        LaunchedEffect(engine) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.016f else min(0.05f, (now - last) / 1_000_000_000f)
                    last = now
                    engine.step(dt)
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            engine.tick
            val list = pages ?: return@Canvas
            val turn = bleed ?: return@Canvas
            val p = engine.progress
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                if (p <= 0.0 || p >= 1.0) {
                    // at rest the picture is exactly one page
                    full.set(0f, 0f, size.width, size.height)
                    canvas.drawBitmap(list[if (p <= 0.0) engine.from else engine.to], null, full, plain)
                } else {
                    val ox = engine.originX
                    val oy = engine.originY
                    val fx = max(ox, width - ox)
                    val fy = max(oy, height - oy)
                    val saved = canvas.save()
                    canvas.scale(size.width / width, size.height / height)
                    turn.draw(canvas, width, height, ox, oy, sqrt(fx * fx + fy * fy), engine.from, engine.to, p.toFloat())
                    canvas.restoreToCount(saved)
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(engine) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        first.consume()
                        val tracker = VelocityTracker()
                        tracker.addPosition(first.uptimeMillis, first.position)
                        val start = first.position
                        if (engine.begin(start.x / density, start.y / density)) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        var ended = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == first.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val dx = (change.position.x - start.x) / density
                                if (!change.pressed) {
                                    ended = true
                                    change.consume()
                                    val velocity = tracker.calculateVelocity()
                                    if (engine.end(change.position.x / density, dx, velocity.x / density)) {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    }
                                    break
                                }
                                change.consume()
                                engine.update(dx, (change.position.y - start.y) / density)
                            }
                        } finally {
                            if (!ended) engine.cancel()
                        }
                    }
                },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = top + if (compact) 8.dp else 14.dp),
        ) {
            Label(COPY_APP, 15f, FontWeight.SemiBold, TEXT_INK, Modifier.alignByBaseline(), spacing = 0.1f)
            Label(COPY_BOOK, 15f, FontWeight.Normal, MUTE_INK, Modifier.alignByBaseline().padding(start = 8.dp).weight(1f))
            Box(Modifier.alignByBaseline(), contentAlignment = Alignment.TopEnd) {
                for (index in 0 until PAGES) {
                    Label(
                        "${index + 1} $COPY_OF", 13f, FontWeight.Medium, MUTE_INK,
                        Modifier.graphicsLayer {
                            engine.tick
                            alpha = weight(index, engine.from, engine.to, engine.progress)
                        },
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = maxOf(bottom, 12.dp) + if (compact) 10.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width((PITCH * (PAGES - 1) + DOT).dp).height(DOT.dp)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(PAGES) {
                        Box(Modifier.size(DOT.dp).background(rgba(38, 35, 30, 0.2f), CircleShape))
                    }
                }
                Box(
                    Modifier
                        .width(PILL.dp)
                        .height(DOT.dp)
                        .graphicsLayer {
                            engine.tick
                            val e = smooth(seg(engine.progress, 0.25, 0.75))
                            val at = engine.from + (engine.to - engine.from) * e
                            translationX = (at.toFloat() * PITCH - (PILL - DOT) / 2f) * density
                            scaleX = 1f + 0.35f * sin(PI * e).toFloat()
                        }
                        .background(TEXT_INK, RoundedCornerShape((DOT / 2f).dp)),
                )
            }
            Label(
                COPY_HINT, 13f, FontWeight.Medium, MUTE_INK,
                Modifier
                    .padding(top = 12.dp)
                    .graphicsLayer {
                        engine.tick
                        alpha = engine.hint
                    },
            )
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

/** a soft ink-dark touch on the paper: a faint halo, a thin ring and a darker core */
@Composable
private fun GhostFinger(modifier: Modifier) {
    Box(modifier.size(GHOST.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(GHOST.dp)
                .cssBoxShadow(rgba(38, 35, 30, 0.16f), offsetY = 0.dp, blur = 16.dp, radius = (GHOST / 2f).dp),
        ) {
            val outer = size.minDimension / 2f
            val ring = 1.5.dp.toPx()
            val inner = outer - ring
            val c = Offset(outer, outer)
            drawCircle(rgba(38, 35, 30, 0.08f), outer, c)
            // inset 0 0 10px rgba(38,35,30,0.1): strongest at the ring, gone towards the middle
            val sigma = 5.dp.toPx()
            val stops = Array(9) { i ->
                val f = i / 8f
                val fromEdge = (1f - f) * inner
                f to rgba(38, 35, 30, 0.1f * edge(fromEdge / sigma))
            }
            drawCircle(Brush.radialGradient(*stops, center = c, radius = inner), inner, c)
            drawCircle(rgba(38, 35, 30, 0.34f), outer - ring / 2f, c, style = Stroke(ring))
        }
        Box(Modifier.size(14.dp).background(rgba(38, 35, 30, 0.3f), CircleShape))
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
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            fontFeatureSettings = "tnum",
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}
