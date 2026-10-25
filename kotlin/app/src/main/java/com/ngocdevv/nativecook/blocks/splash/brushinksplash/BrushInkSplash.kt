package com.ngocdevv.nativecook.blocks.splash.brushinksplash

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Brush Ink Splash — the launch screen of "Sumi", a journaling app. On warm rice paper a loaded
 brush lands with a blot and paints an ensō clockwise, thinning and running dry into bristle
 streaks, then writes the wordmark "Sumi" stroke by stroke; the ink bleeds into the fibres at every
 edge, a few drops lie where the brush landed and flicked off. Then the sheet slides up to reveal
 the journal's home screen, and a fresh sheet fades back in. The loop is in
 BrushInkSplashMotion.kt (LOOP = 8 s).

 The strokes are one triangle mesh (Canvas.drawVertices) whose texture coordinates are stroke
 coordinates (distance along the paint clock, position across the stroke), shaded by INK_AGSL: so
 the wet tip, ragged edges, dry streaks, pooled rims and bleeding all follow the brush, not the
 screen.

 Touch: draw with a finger and the same brush paints under it, thinner the faster it moves,
 drying out along a long stroke; lift and the ink bleeds (0.6 s). A new touch starts a new stroke.
 2 s after the last touch the ink fades (0.9 s) and the loop starts again. Reduced motion: the
 painted splash, still; drawing still works, and the bleed appears at once.
 */

private val Ink = Color(0xFF1F1D22)
private val Mute = Color(0xFF7E766B)
internal val InkSheet = Color(0xFFF3EEE4)
private const val IDLE = 2f
private const val RESET = 0.9f
// The finger's brush: half-width from W_MAX (slow) to W_MIN (fast), easing toward it; at most
// MAX_POINTS points, one every STEP dp; dry over DRY_LENGTH dp.
private const val W_MAX = 9f
private const val W_MIN = 3.2f
private const val MAX_POINTS = 220
private const val STEP = 2.5f
private const val DRY_LENGTH = 700f

private val PAGES = listOf(
    Triple("Rain on the tram window", "Thu", "312"),
    Triple("Tea with my mother", "Tue", "540"),
    Triple("First frost at the allotment", "Sun", "198"),
    Triple("A walk to the harbour", "Fri", "421"),
)

// Seconds from a fixed origin, so a float keeps millisecond precision however long the device has run.
private val InkOrigin = System.nanoTime()

private fun nowSeconds() = (System.nanoTime() - InkOrigin) / 1e9f

private class InkEngine(val reduced: Boolean, val plan: InkPlan) {
    var clock = 0f
    var user = false
    var lastTouch = 0f
    var resetAt = -1f
    var drawing = false
    // The splash under a finger (frozen where the loop was, fading away).
    var head = 0f
    var bleed = 0f
    val ink = InkTrack(0f)
    val slide = InkTrack(0f)
    val paper = InkTrack(1f)
    var landedAt = Float.NaN
    var flickedAt = Float.NaN
    // The finger's stroke.
    val xs = FloatArray(MAX_POINTS)
    val ys = FloatArray(MAX_POINTS)
    val hws = FloatArray(MAX_POINTS)
    var n = 0
    val mesh = InkMesh()
    val strokeBleed = InkTrack(0f)
    val strokeAlpha = InkTrack(0f)
    var running = false

    private val pose = InkPose()
    private var poseAt = Float.NaN
    private var poseClock = Float.NaN

    fun busy(now: Float): Boolean {
        if (drawing) return true
        if (reduced) return !strokeBleed.done(now)
        return user
    }

    fun pose(now: Float): InkPose {
        if (now == poseAt && clock == poseClock) return pose
        poseAt = now
        poseClock = clock
        if (reduced && !user) {
            pose.setPainted(plan)
            return pose
        }
        if (!user) {
            pose.setLoop(clock, plan)
            return pose
        }
        pose.head = head
        pose.bleed = bleed
        pose.ink = ink.value(now)
        pose.slide = slide.value(now)
        pose.paper = paper.value(now)
        pose.landed = if (landedAt.isNaN()) -1f else now - landedAt
        pose.flicked = if (flickedAt.isNaN()) -1f else now - flickedAt
        return pose
    }

    private fun touched(now: Float) {
        lastTouch = now
        resetAt = -1f
        if (user) return
        val p = InkPose().apply { if (reduced) setPainted(plan) else setLoop(clock, plan) }
        head = p.head
        bleed = p.bleed
        ink.set(p.ink)
        slide.set(p.slide)
        paper.set(p.paper)
        landedAt = if (p.landed >= 0f) now - p.landed else Float.NaN
        flickedAt = if (p.flicked >= 0f) now - p.flicked else Float.NaN
        user = true
        if (reduced) return
        // Clear the sheet for the finger: the splash fades, a slid sheet comes back.
        ink.timing(now, 0f, 0.3f, InkEaseOutCurve)
        slide.timing(now, 0f, 0.3f, InkEaseOutCurve)
        paper.timing(now, 1f, 0.3f, InkEaseOutCurve)
    }

    private fun rebuild() {
        mesh.clear()
        if (n >= 2) inkAddStroke(mesh, xs, ys, hws, n, 0, 0f, 1.7f)
    }

    fun step(now: Float, dt: Float, auto: Boolean) {
        if (!user) {
            if (auto) clock = (clock + dt) % LOOP
            return
        }
        if (reduced || drawing) return
        if (resetAt < 0f && now - lastTouch > IDLE) {
            strokeAlpha.timing(now, 0f, RESET, InkEaseInOutCurve)
            ink.timing(now, 0f, RESET, InkEaseInOutCurve)
            resetAt = now + RESET + 0.05f
        } else if (resetAt > 0f && now >= resetAt) {
            resetAt = -1f
            n = 0
            mesh.clear()
            clock = 0f
            user = false
        }
    }

    fun begin(now: Float, x: Float, y: Float) {
        touched(now)
        drawing = true
        n = 1
        xs[0] = x
        ys[0] = y
        hws[0] = W_MAX * 1.15f
        strokeBleed.set(0f)
        strokeAlpha.set(1f)
        rebuild()
    }

    fun move(now: Float, x: Float, y: Float, speed: Float) {
        lastTouch = now
        if (n >= MAX_POINTS) return
        val d = hypot(x - xs[n - 1], y - ys[n - 1])
        if (d < STEP) return
        // Faster strokes are thinner; the width eases toward its target so it never jumps.
        val target = W_MAX - (W_MAX - W_MIN) * min(1f, speed / 1600f)
        val w = hws[n - 1] + (target - hws[n - 1]) * 0.25f
        xs[n] = x
        ys[n] = y
        hws[n] = w
        n += 1
        rebuild()
    }

    /** The finger lifted; returns true for the haptic (a real stroke, not a dab). */
    fun end(now: Float): Boolean {
        drawing = false
        lastTouch = now
        if (reduced) strokeBleed.set(1f) else strokeBleed.timing(now, 1f, 0.6f, InkEaseOutCurve)
        return n > 8
    }

    fun strokeCap() = (if (n > 0) hws[0] else W_MAX) * 1.2f
    fun strokeEnd() = (if (n > 0) hws[n - 1] else W_MAX) * 1.2f
}

private class InkArt(val paper: ImageBitmap?)

/**
 * The launch screen of a journaling app painted in sumi ink: a brush lands, paints an ensō that
 * runs dry into bristle streaks, writes the wordmark, the ink bleeds into the rice paper, and the
 * sheet slides away to the home screen. Draw with a finger to paint with the same brush.
 *
 * @param autoPlay paints the mark and the wordmark, reveals the home screen and starts over, on a loop of [LOOP] s.
 */
@Composable
fun BrushInkSplash(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(density) / density.density
    val bottom = insets.getBottom(density) / density.density
    BoxWithConstraints(modifier.fillMaxSize().background(InkSheet)) {
        val layout = remember(maxWidth, maxHeight, top, bottom) { inkLayout(maxWidth.value, maxHeight.value, top, bottom) }
        InkHome(top, bottom)
        InkScene(layout, autoPlay && !reduced, reduced)
    }
}

private val HomeEnso = PathParser().parsePathString("M8 22.5A10 10 0 1 1 22.6 22").toPath()

/** The journal's home screen, under the splash. */
@Composable
private fun InkHome(top: Float, bottom: Float) {
    Column(
        Modifier
            .fillMaxSize()
            .background(InkSheet)
            .padding(start = 24.dp, end = 24.dp, top = (top + 14f).dp, bottom = (max(bottom, 16f) + 8f).dp),
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(30.dp)) {
                scale(density, density, pivot = Offset.Zero) {
                    drawPath(HomeEnso, Ink, style = Stroke(3.2f, cap = StrokeCap.Round))
                }
            }
            BasicText("Sumi", style = TextStyle(color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(36.dp).background(Color(0xFFD9CDB8), CircleShape), contentAlignment = Alignment.Center) {
                BasicText("AK", style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        }
        BasicText(
            "Saturday, 25 October",
            Modifier.padding(top = 4.dp, bottom = 20.dp),
            style = TextStyle(color = Mute, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .cssBoxShadow(Color(60, 45, 25, 31), 1.dp, 2.dp, 20.dp)
                .cssBoxShadow(Color(60, 45, 25, 20), 10.dp, 24.dp, 20.dp)
                .background(Color(0xFFFBF8F1), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText("TODAY'S PAGE", style = TextStyle(color = Color(0xFFA1533A), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp))
            BasicText(
                "What did the light look like this morning?",
                style = TextStyle(color = Ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp, fontFamily = FontFamily.Serif),
            )
            Box(
                Modifier.padding(top = 4.dp).height(40.dp).background(Ink, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Start writing", style = TextStyle(color = Color(0xFFF6F1E7), fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        BasicText(
            "Recent pages",
            Modifier.padding(top = 28.dp, bottom = 6.dp),
            style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold),
        )
        val hairline = (1f / LocalDensity.current.density).dp
        for ((title, day, words) in PAGES) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(title, style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                BasicText("$day · $words words", style = TextStyle(color = Mute, fontSize = 13.sp, fontFeatureSettings = "tnum"))
            }
            Box(Modifier.fillMaxWidth().height(hairline).background(Color(31, 29, 34, 38)))
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("12-day streak", style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .padding(0.4.dp)
                            .then(if (i < 6) Modifier.background(Ink, CircleShape) else Modifier)
                            .border(1.2.dp, Ink, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun InkScene(layout: InkLayout, auto: Boolean, reduced: Boolean) {
    val density = LocalDensity.current
    val d = density.density
    val view = LocalView.current
    val splash = remember(layout) { inkBuildSplash(layout) }
    val engine = remember(reduced, splash) { InkEngine(reduced, splash.plan) }
    val tick = remember { mutableFloatStateOf(nowSeconds()) }
    val wake = remember { mutableIntStateOf(0) }
    val art by produceState<InkArt?>(null, layout, d) {
        value = withContext(Dispatchers.Default) { InkArt(inkBakePaper(layout, min(min(d, 3f), 2f))?.asImageBitmap()) }
    }
    val shaders = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(INK_AGSL) to RuntimeShader(INK_AGSL) }.getOrNull()
        } else {
            null
        }
    }
    val paints = remember(splash, shaders) { InkPaints(splash, shaders) }

    LaunchedEffect(engine, auto, wake.intValue) {
        engine.running = true
        var last = -1L
        try {
            while (true) {
                val keepGoing = withFrameNanos { nanos ->
                    val now = (nanos - InkOrigin) / 1e9f
                    val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                    last = nanos
                    engine.step(now, dt, auto)
                    tick.floatValue = now
                    auto || engine.busy(now)
                }
                if (!keepGoing) break
            }
        } finally {
            engine.running = false
        }
    }

    fun wakeUp() {
        tick.floatValue = nowSeconds()
        if (!engine.running) wake.intValue += 1
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = engine.pose(tick.floatValue)
                alpha = p.paper.coerceIn(0f, 1f)
                translationY = -p.slide * layout.height * d
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Sumi. Draw on the paper with the brush." }
                .pointerInput(engine) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        engine.begin(nowSeconds(), down.position.x / d, down.position.y / d)
                        wakeUp()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (change.position != change.previousPosition) {
                                val v = tracker.calculateVelocity()
                                engine.move(nowSeconds(), change.position.x / d, change.position.y / d, hypot(v.x, v.y) / d)
                                tick.floatValue = nowSeconds()
                            }
                            change.consume()
                        }
                        if (engine.end(nowSeconds())) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        wakeUp()
                    }
                },
        ) {
            val now = tick.floatValue
            val p = engine.pose(now)
            art?.paper?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
            scale(d, d, pivot = Offset.Zero) { inkDrawScene(splash, engine, p, now, paints) }
        }
        BasicText(
            "A page a day, in ink.",
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset { IntOffset(0, (layout.taglineTop * d).roundToInt()) }
                .graphicsLayer {
                    val p = engine.pose(tick.floatValue)
                    alpha = (p.ink * p.bleed).coerceIn(0f, 1f)
                    translationY = (1f - p.bleed) * 6f * d
                },
            style = TextStyle(color = Color(0xFF5D564F), fontSize = 17.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center),
        )
        BasicText(
            "Syncing 214 pages",
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset { IntOffset(0, (layout.footerTop * d).roundToInt()) }
                .graphicsLayer {
                    val p = engine.pose(tick.floatValue)
                    alpha = (p.ink * p.bleed).coerceIn(0f, 1f)
                    translationY = (1f - p.bleed) * 6f * d
                },
            style = TextStyle(color = Mute, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, fontFeatureSettings = "tnum", textAlign = TextAlign.Center),
        )
    }
}

/** The paints of the moving parts and the fixed per-stroke uniforms, made once. Coordinates are dp. */
private class InkPaints(splash: InkSplash, shaders: Pair<Any, Any>?) {
    val splashShader = shaders?.first
    val strokeShader = shaders?.second
    val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val landing = inkDrops(0, 0f)
    val flick = inkDrops(1, splash.ensoEndAngle)
    val drops = Path()
    val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F1D22.toInt() }
    val dropBleed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1F1D22.toInt()
        maskFilter = BlurMaskFilter(((2.5f - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
    }
    // Below Android 13: the strokes as plain ink, and their bleed as a blurred grey copy.
    val solid = Path()
    val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F1D22.toInt() }
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF423F45.toInt()
        maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && splashShader != null && strokeShader != null) {
            val p = splash.plan
            (splashShader as RuntimeShader).apply {
                setFloatUniform("starts", p.starts)
                setFloatUniform("lengths", p.lengths)
                setFloatUniform("dry", p.dry)
                setFloatUniform("caps", p.caps)
                setFloatUniform("ends", p.ends)
            }
            splashPaint.shader = splashShader
            (strokeShader as RuntimeShader).apply {
                setFloatUniform("head", 1e5f)
                setFloatUniform("starts", FloatArray(INK_MAX_STROKES) { if (it == 0) 0f else 1e6f })
                setFloatUniform("lengths", FloatArray(INK_MAX_STROKES) { if (it == 0) DRY_LENGTH else 1f })
                setFloatUniform("dry", FloatArray(INK_MAX_STROKES) { if (it == 0) 0.8f else 0f })
                setFloatUniform("caps", FloatArray(INK_MAX_STROKES) { 1f })
                setFloatUniform("ends", FloatArray(INK_MAX_STROKES) { 1f })
            }
            strokePaint.shader = strokeShader
        }
    }

    val capsBuffer = FloatArray(INK_MAX_STROKES) { 1f }
    val endsBuffer = FloatArray(INK_MAX_STROKES) { 1f }
}

private fun alpha255(a: Float) = (a * 255f).roundToInt().coerceIn(0, 255)

private fun DrawScope.inkDrawScene(splash: InkSplash, e: InkEngine, p: InkPose, now: Float, k: InkPaints) {
    drawIntoCanvas { c ->
        val n = c.nativeCanvas
        val shaded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && k.splashShader != null && k.strokeShader != null
        val mesh = splash.mesh
        if (shaded && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (p.ink >= 0.002f) {
                val s = k.splashShader as RuntimeShader
                s.setFloatUniform("head", p.head)
                s.setFloatUniform("bleed", p.bleed)
                s.setFloatUniform("alpha", p.ink)
                n.drawVertices(
                    android.graphics.Canvas.VertexMode.TRIANGLES, mesh.vertexFloats, mesh.verts, 0, mesh.texs, 0,
                    null, 0, mesh.indices, 0, mesh.indexCount, k.splashPaint,
                )
            }
        } else {
            inkFallback(n, mesh, p.head, p.bleed, p.ink, k)
        }

        // Drops where the brush landed and where it flicked off the end of the circle.
        k.drops.rewind()
        if (p.landed >= 0f) {
            val g = 0.6f + 0.4f * inkEaseOut(p.landed / 0.15f)
            for (dp in k.landing) k.drops.addCircle(splash.ensoStartX + dp[0], splash.ensoStartY + dp[1], dp[2] * g, Path.Direction.CW)
        }
        if (p.flicked >= 0f) {
            for (i in k.flick.indices) {
                val at = p.flicked - i * 0.04f
                if (at < 0f) continue
                val g = 0.6f + 0.4f * inkEaseOut(at / 0.12f)
                k.drops.addCircle(splash.ensoEndX + k.flick[i][0], splash.ensoEndY + k.flick[i][1], k.flick[i][2] * g, Path.Direction.CW)
            }
        }
        if (!k.drops.isEmpty) {
            val bleedA = p.ink * p.bleed * 0.35f
            if (bleedA > 0.002f) {
                k.dropBleed.alpha = alpha255(bleedA)
                n.drawPath(k.drops, k.dropBleed)
            }
            val a = p.ink * 0.9f
            if (a > 0.002f) {
                k.dropPaint.alpha = alpha255(a)
                n.drawPath(k.drops, k.dropPaint)
            }
        }

        // The finger's stroke.
        val alpha = e.strokeAlpha.value(now)
        if (e.n >= 2 && alpha > 0.002f && e.user) {
            val bleed = e.strokeBleed.value(now)
            if (shaded && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val s = k.strokeShader as RuntimeShader
                k.capsBuffer[0] = e.strokeCap()
                k.endsBuffer[0] = e.strokeEnd()
                s.setFloatUniform("caps", k.capsBuffer)
                s.setFloatUniform("ends", k.endsBuffer)
                s.setFloatUniform("bleed", bleed)
                s.setFloatUniform("alpha", alpha)
                val m = e.mesh
                n.drawVertices(
                    android.graphics.Canvas.VertexMode.TRIANGLES, m.vertexFloats, m.verts, 0, m.texs, 0,
                    null, 0, m.indices, 0, m.indexCount, k.strokePaint,
                )
            } else {
                inkFallback(n, e.mesh, 1e5f, bleed, alpha, k)
            }
        }
    }
}

/**
 * Below Android 13: the mesh's quads that the brush has passed, filled with flat ink in a layer of
 * the ink's opacity, over a blurred grey copy for the bleed. No texture, dry streaks or wet tip.
 */
private fun inkFallback(n: android.graphics.Canvas, mesh: InkMesh, head: Float, bleed: Float, alpha: Float, k: InkPaints) {
    if (alpha < 0.002f) return
    val path = k.solid
    path.rewind()
    val v = mesh.verts
    val t = mesh.texs
    var i = 0
    // Vertices come in pairs across the stroke; a quad joins one pair to the next of the same stroke.
    while (i + 7 < mesh.vertexFloats) {
        val sameStroke = kotlin.math.abs(t[i + 1] - t[i + 5]) < 0.01f
        if (sameStroke && t[i + 4] <= head) {
            // The mesh reaches `margin` half-widths out (room for the bleed); the ink covers one.
            val f = 2f / max(0.01f, t[i + 3] - t[i + 1])
            fun cx(j: Int) = (v[j] + v[j + 2]) / 2f
            fun cy(j: Int) = (v[j + 1] + v[j + 3]) / 2f
            path.moveTo(cx(i) + (v[i] - cx(i)) * f, cy(i) + (v[i + 1] - cy(i)) * f)
            path.lineTo(cx(i + 4) + (v[i + 4] - cx(i + 4)) * f, cy(i + 4) + (v[i + 5] - cy(i + 4)) * f)
            path.lineTo(cx(i + 4) + (v[i + 6] - cx(i + 4)) * f, cy(i + 4) + (v[i + 7] - cy(i + 4)) * f)
            path.lineTo(cx(i) + (v[i + 2] - cx(i)) * f, cy(i) + (v[i + 3] - cy(i)) * f)
            path.close()
        }
        i += 4
    }
    if (path.isEmpty) return
    if (bleed > 0.002f) {
        k.halo.alpha = alpha255(bleed * alpha * 0.35f)
        n.drawPath(path, k.halo)
    }
    n.saveLayerAlpha(null, alpha255(alpha))
    k.solidPaint.alpha = 255
    n.drawPath(path, k.solidPaint)
    n.restore()
}
