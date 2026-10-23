package com.ngocdevv.nativecook.blocks.text.fallingwordstickers

import android.content.res.AssetManager
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Falling Word Stickers: a studio's landing screen whose call to action, "your next big project starts
 here", is six pastel word stickers that drop from above the screen, bounce and settle into a pile
 at the bottom, tilted and overlapping.

 Layout (dp, W × H the whole screen): the words are Inter Tight Medium at S = 0.13 W (51 dp on a
 393 dp screen), "big" at 1.4 S; a sticker is the word's advance plus 0.24 em on each side, 1.12 em
 tall, corners 0.1 em, the baseline 0.262 em below its centre (half-way between cap height and the
 descender of "p", read from the font). The pile rests on a floor 20 dp above the bottom safe area:
 "your", "next" and "big" on the floor (centres at 0.165, 0.48 and 0.82 W, kept 8 dp inside the
 screen), "project" on the first two (0.36 W, 1.06 S up), "starts" on "big" (0.80 W, 1.52 S up) and
 "here" on "starts" (0.70 W, 2.58 S up), tilted −5°, 3°, −3°, −6°, 5° and −12°.

 Timeline of the self-playing loop (LOOP = 6 s), gravity g = 2600 dp/s²:
   0.00–1.20  The pile at rest (the first frame).
   1.20–1.95  The floor gives way: from the top sticker down, 0.05 s apart, each pops up at 250 dp/s
              and falls out of the bottom of the screen under g, spinning at its own rate (30–70°/s)
              and drifting sideways.
   2.00–2.70  The words drop in reading order, 0.14 s apart, from just above the screen: free fall
              under g to their place, sliding in from their own side offset (20–50 dp, ease-out
              cubic) and turning from their own spin (20–55°) to their resting tilt.
   on impact  Two bounces with restitution 0.3 (about 60 dp and 5 dp high) and a wobble of ±7°
              (sin 18 t · e^(−5 t), faded out over 1.6 s). The last word has settled by 5.1 s.
   5.10–6.00  At rest again; 6 s is the same frame as 0.

 Touch: a sticker can be picked up (haptic, it lifts and grows 5%) and dragged; it leans with the
 finger's speed and is thrown on release with the finger's velocity. Thrown stickers fall under the
 same gravity, bounce off the screen's sides and come to rest on their own floor height with
 restitution 0.3, a spring pulling them back to their tilt; a hard landing gives a haptic. A tap
 without a drag makes the sticker hop (900 dp/s up, with a spin). 2 s after the last touch the
 stickers glide back to the pile (0.6 s, ease-in-out) and the loop starts over. Reduced motion: the
 pile stays at rest and does not move.
*/

/** The self-playing loop, in seconds. */
const val LOOP = 6f

private val INK = Color(0xFF141414)
private val PAPER = Color(0xFFF3F2EE)

private val COLORS = intArrayOf(0xFFF2655B.toInt(), 0xFFFF9A6B.toInt(), 0xFF6FD0F0.toInt(), 0xFFD9A0F4.toInt(), 0xFFB9F0E6.toInt(), 0xFF9BE58B.toInt())

/** Per word: centre x (fraction of W), height of its bottom above the floor (in S), tilt (degrees). */
private val PILE = arrayOf(
    floatArrayOf(0.165f, 0f, -5f),
    floatArrayOf(0.48f, 0f, 3f),
    floatArrayOf(0.82f, 0f, -3f),
    floatArrayOf(0.36f, 1.06f, -6f),
    floatArrayOf(0.80f, 1.52f, 5f),
    floatArrayOf(0.70f, 2.58f, -12f),
)
private val DRIFTS = floatArrayOf(-40f, 30f, -20f, 50f, -30f, 40f)
private val SPINS = floatArrayOf(-35f, 28f, -20f, 40f, -30f, 55f)
private val EXIT_SPINS = floatArrayOf(-50f, 40f, -30f, 60f, -45f, 70f)

private const val GRAVITY = 2600f
private const val BOUNCE = 0.3f
private const val IDLE = 2f
private const val GLIDE = 0.6f
private const val RAD = (Math.PI / 180).toFloat()

private fun easeOutCubic(t: Float) = 1f - (1f - t).pow(3)
private fun easeInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun seconds(from: Long, to: Long) = (to - from) / 1e9f

/**
 * A studio's landing screen whose call to action is six pastel word stickers that drop in, bounce
 * and settle into a pile; pick one up and throw it.
 */
@Composable
fun FallingWordStickers(
    modifier: Modifier = Modifier,
    badge: String = "Open for new briefs",
    headline: List<String> = listOf("Product sites that", "say it straight —", "clear, direct,", "no fluff."),
    subtitle: String = "A two-person studio for sites and apps.",
    action: String = "Start a project →",
    /** The six words of the pile, in reading order. */
    words: List<String> = listOf("your", "next", "big", "project", "starts", "here"),
    /** Drops the words again every [LOOP] seconds. */
    autoPlay: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val model = remember(context) { FallingWordStickersModel(context.assets, view) }
    val family = remember(model) { FontFamily(model.typeface) }

    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var touched by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(false) }
    val paused = reduced || (!autoPlay && !active)

    FallingWordStickersSystemBars()
    LaunchedEffect(Unit) { model.start(System.nanoTime()) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (isActive) withFrameNanos { now = System.nanoTime() }
    }
    // Keeps the clock running after a touch when the loop is off: two seconds of rest, then the glide.
    LaunchedEffect(touched) {
        if (touched == 0) return@LaunchedEffect
        delay(((IDLE + GLIDE + 0.1f) * 1000).toLong())
        if (!model.isDragging) active = false
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PAPER)
            .pointerInput(reduced, autoPlay) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (reduced) return@awaitEachGesture
                    val start = System.nanoTime()
                    if (!model.begin(down.position / density, start, autoPlay)) return@awaitEachGesture
                    active = true
                    touched++
                    now = start
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        model.move(change.position / density, System.nanoTime())
                    }
                    model.end(System.nanoTime())
                    touched++
                }
            },
    ) {
        val width = maxWidth.value
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding()
        val bottom = insets.calculateBottomPadding().value
        val headlineSize = model.fit(headline, width - 48f, 36f)

        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = top + 16.dp)) {
            Row(
                Modifier
                    .height(26.dp)
                    .background(INK, CircleShape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(6.dp).background(Color(0xFF3DDC84), CircleShape))
                BasicText(
                    badge.uppercase(),
                    style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, platformStyle = NoPadding),
                    maxLines = 1,
                )
            }
            Column(Modifier.padding(top = 20.dp)) {
                headline.forEach { line ->
                    Box(Modifier.height((1.08f * headlineSize).dp), contentAlignment = Alignment.CenterStart) {
                        BasicText(
                            line,
                            style = TextStyle(color = INK, fontSize = headlineSize.sp, fontFamily = family, letterSpacing = (-0.02f).em, platformStyle = NoPadding),
                            maxLines = 1,
                        )
                    }
                }
            }
            BasicText(
                subtitle,
                style = TextStyle(color = INK.copy(alpha = 0.55f), fontSize = 15.sp, fontFamily = family, platformStyle = NoPadding),
                maxLines = 2,
                modifier = Modifier.padding(top = 14.dp),
            )
            Box(
                Modifier
                    .padding(top = 24.dp)
                    .height(52.dp)
                    .background(INK, CircleShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onAction?.invoke() }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(action, style = TextStyle(color = Color.White, fontSize = 17.sp, fontFamily = family, platformStyle = NoPadding), maxLines = 1)
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = words.joinToString(" ") },
        ) {
            model.prepare(words, size.width / density, size.height / density, bottom)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                model.draw(c, now, autoPlay && !reduced)
                c.restore()
            }
        }
    }
}

private val NoPadding = PlatformTextStyle(includeFontPadding = false)

/** The screen is light, so the bar icons are dark while the block is open and put back when it closes. */
@Composable
private fun FallingWordStickersSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}

/** The stickers: their layout, the self-playing fall, and the physics a touch hands them to (in dp). */
private class FallingWordStickersModel(assets: AssetManager, private val view: View) {
    class Pose(var x: Float, var y: Float, var a: Float) {
        fun copy() = Pose(x, y, a)
    }

    class Sticker(
        val word: String,
        val color: Int,
        val size: Float,
        val width: Float,
        val height: Float,
        /** Where it rests in the pile (centre) and how it tilts there (radians). */
        val rest: Pose,
        val drift: Float,
        val spin: Float,
        val wobble: Float,
        val exitSpin: Float,
    )

    private enum class Mode { AUTO, PHYSICS, GLIDING }

    val typeface: Typeface = Typeface.createFromAsset(assets, "fallingwordstickers/fallingwordstickers_InterTight-Medium.ttf")
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = this@FallingWordStickersModel.typeface }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Cap height and the depth of "p", per unit of size, from the font's outlines. */
    private val capRatio: Float
    private val descentRatio: Float

    init {
        text.textSize = 100f
        val bounds = Rect()
        text.getTextBounds("H", 0, 1, bounds)
        capRatio = -bounds.top / 100f
        text.getTextBounds("p", 0, 1, bounds)
        descentRatio = bounds.bottom / 100f
    }

    var stickers: List<Sticker> = emptyList()
        private set
    private var key: Any? = null
    private var screenW = 0f
    private var screenH = 0f

    private var mode = Mode.AUTO
    private var loopStart = 0L
    private var poses = mutableListOf<Pose>()
    private var velocity = mutableListOf<Pose>()
    private var floored = mutableListOf<Boolean>()
    private var order = mutableListOf<Int>()
    private var last = 0L
    private var dragged: Int? = null
    private var grab = Offset.Zero
    private var finger = Offset.Zero
    private var fingerVelocity = Offset.Zero
    private var fingerAt = 0L
    private var downAt = 0L
    private var travel = 0f
    private var released = Long.MIN_VALUE / 2
    private var glideFrom = listOf<Pose>()
    private var glideStart = 0L
    val isDragging get() = dragged != null

    fun start(at: Long) {
        loopStart = at
        last = at
    }

    fun width(string: String, size: Float): Float {
        text.textSize = size
        return text.measureText(string)
    }

    /** The largest size up to [cap] at which every line fits [width]. */
    fun fit(lines: List<String>, width: Float, cap: Float): Float {
        val widest = lines.maxOfOrNull { width(it, 100f) } ?: 0f
        return if (widest > 0f) min(cap, 100f * width / widest) else cap
    }

    fun prepare(words: List<String>, w: Float, h: Float, bottom: Float) {
        val k = listOf(words, w, h, bottom)
        if (k == key) return
        key = k
        screenW = w
        screenH = h
        val s = 0.13f * w
        val floor = h - bottom - 20f
        stickers = words.take(6).mapIndexed { i, word ->
            val pile = PILE[i]
            val size = if (i == 2) 1.4f * s else s
            val width = width(word, size) + 0.48f * size
            val height = 1.12f * size
            val x = (pile[0] * w).coerceIn(width / 2f + 8f, max(width / 2f + 8f, w - width / 2f - 8f))
            Sticker(
                word, COLORS[i], size, width, height,
                Pose(x, floor - pile[1] * s - height / 2f, pile[2] * RAD),
                DRIFTS[i], SPINS[i] * RAD, (if (SPINS[i] > 0f) 7f else -7f) * RAD, EXIT_SPINS[i] * RAD,
            )
        }
        order = stickers.indices.toMutableList()
        poses = stickers.map { it.rest.copy() }.toMutableList()
        velocity = stickers.map { Pose(0f, 0f, 0f) }.toMutableList()
        floored = stickers.map { true }.toMutableList()
    }

    /** Where sticker [i] is [t] seconds into the loop. */
    fun pose(i: Int, t: Float): Pose {
        val c = stickers[i]
        val g = GRAVITY
        val exit = 1.2f + 0.05f * (stickers.size - 1 - i)
        val drop = 2.0f + 0.14f * i
        if (t < exit) return c.rest.copy()
        if (t < drop) {
            val tau = t - exit
            val y = c.rest.y - 250f * tau + 0.5f * g * tau * tau
            return Pose(c.rest.x + 1.5f * c.drift * tau, min(y, screenH + 400f), c.rest.a + c.exitSpin * tau)
        }
        val tau = t - drop
        val y0 = -max(c.width, c.height) / 2f - 20f
        val fall = sqrt(2f * (c.rest.y - y0) / g)
        if (tau < fall) {
            val p = tau / fall
            return Pose(c.rest.x + c.drift * (1f - easeOutCubic(p)), y0 + 0.5f * g * tau * tau, c.rest.a + c.spin * (1f - p))
        }
        val after = tau - fall
        val v1 = BOUNCE * g * fall
        val t1 = 2f * v1 / g
        val v2 = BOUNCE * v1
        val t2 = 2f * v2 / g
        var height = 0f
        if (after < t1) {
            height = v1 * after - 0.5f * g * after * after
        } else if (after < t1 + t2) {
            val u = after - t1
            height = v2 * u - 0.5f * g * u * u
        }
        val wobble = c.wobble * sin(18f * after) * exp(-5f * after) * (1f - smoothstep(0f, 1.6f, after))
        return Pose(c.rest.x, c.rest.y - height, c.rest.a + wobble)
    }

    private fun loopTime(at: Long, autoPlay: Boolean) = if (autoPlay) seconds(loopStart, at).mod(LOOP) else 0f

    fun draw(c: android.graphics.Canvas, at: Long, autoPlay: Boolean) {
        if (stickers.isEmpty()) return
        update(at, autoPlay)
        for (i in order) {
            val s = stickers[i]
            val p = poses[i]
            if (p.y - max(s.width, s.height) >= screenH) continue
            val up = if (dragged == i) 1f else 0f
            val scale = 1f + 0.05f * up
            val rect = RectF(-s.width / 2f, -s.height / 2f, s.width / 2f, s.height / 2f)
            val r = 0.1f * s.size

            // Shadow: the same shape, blurred (sigma 6, 10 when lifted) and dropped 4 dp (12 lifted).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                fill.color = android.graphics.Color.BLACK
                fill.alpha = ((0.14f + 0.06f * up) * 255f).roundToInt()
                fill.maskFilter = BlurMaskFilter(sigmaToRadius(6f + 4f * up), BlurMaskFilter.Blur.NORMAL)
                c.save()
                c.translate(p.x, p.y + 4f + 8f * up)
                c.rotate(p.a / RAD)
                c.scale(scale, scale)
                c.drawRoundRect(rect, r, r, fill)
                c.restore()
                fill.maskFilter = null
            }

            c.save()
            c.translate(p.x, p.y)
            c.rotate(p.a / RAD)
            c.scale(scale, scale)
            fill.color = s.color
            c.drawRoundRect(rect, r, r, fill)
            text.textSize = s.size
            text.color = 0xFF141414.toInt()
            c.drawText(s.word, -s.width / 2f + 0.24f * s.size, (capRatio - descentRatio) / 2f * s.size, text)
            c.restore()
        }
    }

    private fun update(at: Long, autoPlay: Boolean) {
        when (mode) {
            Mode.AUTO -> {
                val t = loopTime(at, autoPlay)
                for (i in stickers.indices) poses[i] = pose(i, t)
            }
            Mode.PHYSICS -> {
                var dt = min(seconds(last, at), 0.05f)
                while (dt > 0f) {
                    val step = min(dt, 1f / 120f)
                    simulate(step)
                    dt -= step
                }
                if (dragged == null && seconds(released, at) >= IDLE) {
                    mode = Mode.GLIDING
                    glideFrom = poses.map { it.copy() }
                    glideStart = at
                    order = stickers.indices.toMutableList()
                }
            }
            Mode.GLIDING -> {
                val k = easeInOut(min(seconds(glideStart, at) / GLIDE, 1f))
                for (i in stickers.indices) {
                    val a = glideFrom[i]
                    val b = stickers[i].rest
                    poses[i] = Pose(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.a + (b.a - a.a) * k)
                }
                if (k >= 1f) {
                    mode = Mode.AUTO
                    loopStart = at
                }
            }
        }
        last = at
    }

    private fun simulate(dt: Float) {
        for (i in stickers.indices) {
            val c = stickers[i]
            val p = poses[i]
            if (i == dragged) {
                val target = c.rest.a + (fingerVelocity.x * 0.0005f).coerceIn(-0.45f, 0.45f)
                p.a += (target - p.a) * min(1f, dt * 12f)
                continue
            }
            val v = velocity[i]
            v.y += GRAVITY * dt
            p.x += v.x * dt
            p.y += v.y * dt
            // A spring pulls the tilt back to the pile's.
            v.a += (-(p.a - c.rest.a) * 30f - v.a * 6f) * dt
            p.a += v.a * dt
            val half = c.width / 2f
            if (p.x < half) { p.x = half; v.x = abs(v.x) * 0.5f }
            if (p.x > screenW - half) { p.x = screenW - half; v.x = -abs(v.x) * 0.5f }
            if (floored[i] && p.y > c.rest.y) {
                p.y = c.rest.y
                if (v.y > 700f) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                v.y = if (v.y > 60f) -v.y * BOUNCE else 0f
                v.x *= 0.8f
            }
            if (floored[i] && p.y >= c.rest.y - 0.5f && v.y == 0f) v.x *= exp(-8f * dt)
        }
    }

    /** Picks up the sticker under the finger; false when there is none. */
    fun begin(point: Offset, at: Long, autoPlay: Boolean): Boolean {
        if (stickers.isEmpty()) return false
        update(at, autoPlay)
        val hit = order.reversed().firstOrNull { contains(point, it) } ?: return false
        if (mode == Mode.AUTO) {
            val t = loopTime(at, autoPlay)
            velocity = stickers.indices.map { i ->
                val a = pose(i, t - 1f / 60f)
                val b = pose(i, t)
                Pose((b.x - a.x) * 60f, (b.y - a.y) * 60f, (b.a - a.a) * 60f)
            }.toMutableList()
        } else if (mode == Mode.GLIDING) {
            velocity = stickers.map { Pose(0f, 0f, 0f) }.toMutableList()
        }
        floored = stickers.indices.map { poses[it].y <= stickers[it].rest.y + 1f }.toMutableList()
        mode = Mode.PHYSICS
        dragged = hit
        floored[hit] = true
        order.remove(hit)
        order.add(hit)
        grab = Offset(poses[hit].x - point.x, poses[hit].y - point.y)
        finger = point
        fingerVelocity = Offset.Zero
        fingerAt = at
        downAt = at
        travel = 0f
        last = at
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        return true
    }

    fun move(point: Offset, at: Long) {
        val i = dragged ?: return
        val dt = max(seconds(fingerAt, at), 1f / 240f)
        val v = Offset((point.x - finger.x) / dt, (point.y - finger.y) / dt)
        fingerVelocity = Offset(fingerVelocity.x * 0.6f + v.x * 0.4f, fingerVelocity.y * 0.6f + v.y * 0.4f)
        travel += hypot(point.x - finger.x, point.y - finger.y)
        finger = point
        fingerAt = at
        poses[i].x = point.x + grab.x
        poses[i].y = min(point.y + grab.y, stickers[i].rest.y)
    }

    fun end(at: Long) {
        val i = dragged ?: return
        dragged = null
        released = at
        if (travel < 8f && seconds(downAt, at) < 0.3f) {
            // A tap: the sticker hops.
            velocity[i] = Pose(0f, -900f, if (stickers[i].spin > 0f) 4f else -4f)
        } else {
            val still = seconds(fingerAt, at) > 0.1f
            val vx = if (still) 0f else fingerVelocity.x.coerceIn(-3000f, 3000f)
            val vy = if (still) 0f else fingerVelocity.y.coerceIn(-3000f, 3000f)
            velocity[i] = Pose(vx, vy, vx * 0.004f)
        }
    }

    private fun contains(point: Offset, i: Int): Boolean {
        val c = stickers[i]
        val p = poses[i]
        val dx = point.x - p.x
        val dy = point.y - p.y
        val x = dx * cos(-p.a) - dy * sin(-p.a)
        val y = dx * sin(-p.a) + dy * cos(-p.a)
        return abs(x) <= c.width / 2f + 6f && abs(y) <= c.height / 2f + 6f
    }
}
