package com.ngocdevv.nativecook.blocks.inputs.patternlockinput

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private const val BACKGROUND = 0xFF05060A.toInt()
private const val NEUTRAL = 0xFF6FE3FF.toInt()
private const val WRONG = 0xFFFF4D63.toInt()
private const val RIGHT = 0xFF59F5A6.toInt()
private val Text = Color(0xFFEEF3FF)
private val Muted = Color(214, 224, 245).copy(alpha = 0.5f)

// The line: three strokes, a wide soft glow, a narrower one and a bright core.
private const val GLOW_WIDTH = 16f
private const val CORE_WIDTH = 3.5f
// The open end bows against its motion by this much per dp/s, at most BOW_MAX dp.
private const val BOW = 0.04f
private const val BOW_MAX = 46f

/** Reanimated's `interpolateColor`: each channel mixed in linear light (gamma 2.2), alpha straight. */
private fun mix(from: Int, to: Int, t: Float): Int {
    val u = clamp(t, 0f, 1f)
    fun channel(shift: Int): Int {
        val a = ((from shr shift) and 0xff) / 255f
        val b = ((to shr shift) and 0xff) / 255f
        val l = a.pow(2.2f) + (b.pow(2.2f) - a.pow(2.2f)) * u
        return (l.pow(1f / 2.2f) * 255f).roundToInt().coerceIn(0, 255)
    }
    val alpha = (((from ushr 24) and 0xff) + ((((to ushr 24) and 0xff) - ((from ushr 24) and 0xff)) * u)).roundToInt()
    return (alpha shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

private fun withAlpha(colour: Int, alpha: Float) =
    (((colour ushr 24) * clamp(alpha, 0f, 1f)).roundToInt() shl 24) or (colour and 0xFFFFFF)

/**
 * A 3×3 pattern lock on a dark screen lit by its own glow. Drawing connects the dots with an
 * elastic, glowing line whose open end lags the finger like a rubber band; each dot pops with a
 * ripple as it is picked up, and dots passed over are picked up too. A wrong pattern turns red and
 * shakes the grid; the right one pulses, collapses into the centre and becomes an open padlock
 * with a check, then the grid comes back.
 *
 * @param pattern The pattern that unlocks, as dot numbers 0–8 read like a page. Defaults to a "Z".
 * @param autoPlay Draws a wrong pattern, then the right one, on a loop of 10 s.
 */
@Composable
fun PatternLockInput(
    modifier: Modifier = Modifier,
    pattern: List<Int> = PatternZ,
    autoPlay: Boolean = false,
    onUnlock: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val unlocking by rememberUpdatedState(onUnlock)
    val engine = remember {
        PatternEngine(object : PatternHost {
            override fun selection() {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }

            override fun wrong() {
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
            }

            override fun right() {
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
            }

            override fun unlocked() {
                unlocking?.invoke()
            }
        })
    }
    SideEffect {
        engine.reducedMotion = reducedMotion
        engine.pattern = pattern
    }
    // Each frame bumps `tick`; drawing and layers read it, so only they run again.
    var tick by remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf(PatternStatus.Idle) }
    var oldStatus by remember { mutableStateOf<PatternStatus?>(null) }

    LaunchedEffect(engine, autoPlay) { engine.setAutoPlay(autoPlay) }
    LaunchedEffect(engine) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else clamp((now - last) / 1e9f, 0f, 1f / 30f)
                last = now
                engine.step(dt)
                tick = now
                if (status != engine.status) status = engine.status
                if (oldStatus != engine.oldStatus) oldStatus = engine.oldStatus
            }
        }
    }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    val paints = remember { PatternPaints() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(BACKGROUND))
            .semantics { contentDescription = "Pattern lock" },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val grid = remember(width, height) { PatternGrid(width, height) }
        SideEffect { engine.grid = grid }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(engine) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density.density
                        engine.touchBegan(down.position.x / d, down.position.y / d)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                engine.touchMoved(change.position.x / d, change.position.y / d)
                            }
                        } finally {
                            // A cancelled gesture lets the pattern go too.
                            engine.touchEnded()
                        }
                    }
                },
        ) {
            tick
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                nc.scale(density.density, density.density)
                drawLock(nc, engine, grid, reducedMotion, paints)
                nc.restore()
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = max(safeTop + 40f, grid.cy - grid.spacing * 1.5f - 150f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText("PRIVATE NOTES", style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.4.sp, color = Muted))
            BasicText("Draw to unlock", style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, color = Text))
        }

        // The status under the grid: each new message rises in over 260 ms as the old one fades.
        Box(
            Modifier
                .fillMaxWidth()
                .offset(y = (grid.cy + grid.spacing * 1.5f + 54f).dp)
                .height(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            oldStatus?.let { old ->
                StatusText(old) {
                    tick
                    val t = clamp((engine.now - engine.oldStatusAt) / 0.14f, 0f, 1f)
                    1f - inOutQuad(t) to 0f
                }
            }
            StatusText(status) {
                tick
                val t = clamp((engine.now - engine.statusAt) / (if (reducedMotion) 0.2f else 0.26f), 0f, 1f)
                val e = if (reducedMotion) inOutQuad(t) else easeOut(t)
                e to if (reducedMotion) 0f else 25f * (1f - e)
            }
        }

        BasicText(
            "Forgot pattern?",
            Modifier.align(Alignment.BottomCenter).padding(bottom = (safeBottom + 26f).dp),
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Muted, textAlign = TextAlign.Center),
        )
    }
}

/** One status message, faded and lifted by [motion] (opacity, dp below its place). */
@Composable
private fun StatusText(status: PatternStatus, motion: () -> Pair<Float, Float>) {
    val colour = when (status) {
        PatternStatus.Idle -> Muted
        PatternStatus.Wrong -> Color(WRONG)
        PatternStatus.Right -> Color(RIGHT)
    }
    BasicText(
        status.copy,
        Modifier.graphicsLayer {
            val (alpha, below) = motion()
            this.alpha = alpha
            translationY = below.dp.toPx()
        },
        style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp, color = colour),
    )
}

/** Paints and paths reused every frame. */
private class PatternPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val line = android.graphics.Path()
    val rect = RectF()
    val auraColours = IntArray(9)
    val auraStops = FloatArray(9)
    val trimmed = android.graphics.Path()
    private val blurs = HashMap<Float, BlurMaskFilter>()

    /** A soft blur of [sigma] dp where the hardware canvas supports it (Android 9+). */
    fun blur(sigma: Float): BlurMaskFilter? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) blurs.getOrPut(sigma) { BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) } else null

    val shackle = android.graphics.Path().apply {
        moveTo(-18f, 0f)
        lineTo(-18f, -30f + 18f)
        arcTo(RectF(-18f, -30f, 18f, 6f), 180f, 180f, false)
        lineTo(18f, 0f)
    }
    val tick = android.graphics.Path().apply {
        moveTo(-12f, 1f)
        lineTo(-3.5f, 9.5f)
        lineTo(13f, -8f)
    }
    val shackleMeasure = PathMeasure(shackle, false)
    val tickMeasure = PathMeasure(tick, false)
}

private fun drawLock(nc: android.graphics.Canvas, engine: PatternEngine, grid: PatternGrid, reducedMotion: Boolean, paints: PatternPaints) {
    // The colour of the lock's light: neutral, red as `wrong` → 1, green as `right` → 1.
    val tone = mix(mix(NEUTRAL, WRONG, engine.wrong.value), RIGHT, engine.right.value)

    // A soft aura behind the grid in the lock's colour, brighter as the pattern grows.
    val aura = 0.13f + engine.glow.value * 0.1f + engine.pulse.value * 0.12f + engine.lock.value * 0.08f
    // Skia in React Native blends the gradient's colours before their alpha, so the light falls
    // off faster than the hardware canvas's premultiplied blend; the stops spell that curve out.
    for (k in 0..8) {
        val u = k / 8f
        paints.auraColours[k] = withAlpha(androidx.core.graphics.ColorUtils.blendARGB(tone, BACKGROUND, u), aura * (1f - u))
        paints.auraStops[k] = u
    }
    // RadialGradient throws on a radius that is not above 0 (a grid measured at no width).
    val auraRadius = grid.spacing * 2.6f
    if (auraRadius > 0f && auraRadius.isFinite()) {
        paints.fill.shader = RadialGradient(grid.cx, grid.cy, auraRadius, paints.auraColours, paints.auraStops, Shader.TileMode.CLAMP)
        nc.drawRect(0f, 0f, grid.width, grid.height, paints.fill)
        paints.fill.shader = null
    }

    nc.save()
    nc.translate(engine.shake, 0f)
    drawLine(nc, engine, grid, reducedMotion, tone, paints)
    for (k in 0 until 9) drawDot(nc, k, engine, grid, reducedMotion, tone, paints)
    drawPadlock(nc, engine, grid, reducedMotion, paints)
    nc.restore()

    // The virtual finger of autoPlay, so a recording shows what is drawing.
    val ghost = engine.ghostAlpha.value
    if (ghost > 0.001f) {
        paints.fill.color = withAlpha(0xFFFFFFFF.toInt(), 0.12f * ghost)
        nc.drawCircle(engine.fingerX, engine.fingerY, 24f, paints.fill)
        paints.stroke.strokeWidth = 1.4f
        paints.stroke.maskFilter = null
        paints.stroke.color = withAlpha(0xFFFFFFFF.toInt(), 0.45f * ghost)
        nc.drawCircle(engine.fingerX, engine.fingerY, 24f, paints.stroke)
    }
}

/** The pattern's line: through the picked dots, the last one springing in, then the open end. */
private fun drawLine(nc: android.graphics.Canvas, engine: PatternEngine, grid: PatternGrid, reducedMotion: Boolean, tone: Int, paints: PatternPaints) {
    val list = engine.selected
    if (list.isEmpty()) return
    val alpha = engine.lineAlpha.value * (1f - clamp(engine.collapse.value * 1.4f, 0f, 1f))
    if (alpha <= 0.001f) return
    val c = if (reducedMotion) 0f else engine.collapse.value
    val path = paints.line
    path.reset()
    var ax = 0f
    var ay = 0f
    list.forEachIndexed { k, d ->
        var x = grid.xs[d] + (grid.cx - grid.xs[d]) * c
        var y = grid.ys[d] + (grid.cy - grid.ys[d]) * c
        if (k == list.size - 1) {
            x += engine.snap[0]
            y += engine.snap[1]
        }
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        ax = x
        ay = y
    }
    val tail = engine.tail
    if (engine.stage == 0 && (engine.drawing || hypot(tail[0] - ax, tail[1] - ay) > 0.8f)) {
        // A rubber band: the open end bows back against the way it is moving.
        var bx = -tail[2] * BOW
        var by = -tail[3] * BOW
        val bend = hypot(bx, by)
        if (bend > BOW_MAX) {
            bx *= BOW_MAX / bend
            by *= BOW_MAX / bend
        }
        path.quadTo((ax + tail[0]) / 2f + bx, (ay + tail[1]) / 2f + by, tail[0], tail[1])
    }
    val pulse = engine.pulse.value
    val s = paints.stroke
    s.color = withAlpha(tone, 0.32f * alpha)
    s.strokeWidth = GLOW_WIDTH + pulse * 14f
    s.maskFilter = paints.blur(10f)
    nc.drawPath(path, s)
    s.color = withAlpha(tone, 0.55f * alpha)
    s.strokeWidth = 7f
    s.maskFilter = paints.blur(3f)
    nc.drawPath(path, s)
    s.color = withAlpha(0xFFF0FAFF.toInt(), 0.92f * alpha)
    s.strokeWidth = CORE_WIDTH + pulse * 2.5f
    s.maskFilter = null
    nc.drawPath(path, s)
}

/** One dot: an idle ring and core, and once hit a springy core, a halo and a ripple. */
private fun drawDot(nc: android.graphics.Canvas, index: Int, engine: PatternEngine, grid: PatternGrid, reducedMotion: Boolean, tone: Int, paints: PatternPaints) {
    val hitAt = engine.hits[index]
    val hit = !hitAt.isNaN()
    val age = if (hit) engine.now - hitAt else -1f
    val p = when {
        !hit -> 0f
        reducedMotion -> min(1f, age * 8f)
        age < 0f -> 0f
        else -> 1f - exp(-age * 13f) * cos(age * 21f)
    }
    val collapse = engine.collapse.value
    val c = if (reducedMotion) 0f else collapse
    val x = grid.xs[index] + (grid.cx - grid.xs[index]) * c
    val y = grid.ys[index] + (grid.cy - grid.ys[index]) * c
    // The dots fade as the padlock forms out of them (and, with reduced motion, as they would collapse).
    val fade = if (reducedMotion) collapse else min(1f, engine.lock.value * 3f)
    val opacity = max(0f, 1f - fade)
    if (opacity <= 0.001f) return
    val pulse = engine.pulse.value

    if (hit) {
        paints.fill.color = withAlpha(tone, opacity * clamp(p, 0f, 1f) * (0.55f + pulse * 0.45f))
        paints.fill.maskFilter = paints.blur(12f)
        nc.drawCircle(x, y, 15f + pulse * 10f, paints.fill)
        paints.fill.maskFilter = null
    }
    val s = paints.stroke
    s.maskFilter = null
    s.strokeWidth = 1.2f
    s.color = if (hit) withAlpha(tone, 0.45f * opacity) else withAlpha(0x21E8F0FF, opacity)
    nc.drawCircle(x, y, 22f * (1f - 0.25f * c), s)
    if (hit && age <= 0.65f) {
        val e = max(0f, age) / 0.65f
        s.strokeWidth = 2f
        s.color = withAlpha(tone, (1f - e) * (1f - e) * 0.8f * opacity)
        val r = if (reducedMotion) 22f else 4.5f + (46f - 4.5f) * (1f - (1f - e).pow(3))
        nc.drawCircle(x, y, r, s)
    }
    val idle = 0x8CE8F0FF.toInt()
    paints.fill.color = withAlpha(if (hit) mix(idle, tone, clamp(p, 0f, 1f)) else idle, opacity)
    nc.drawCircle(x, y, 4.5f + 4.5f * p + pulse * 2.5f * (if (hit) 1f else 0f), paints.fill)
}

/**
 * The padlock the dots collapse into: its body grows out of a dot-sized blob and sinks a little
 * to make room for the shackle, which draws on, then lifts and swings open.
 */
private fun drawPadlock(nc: android.graphics.Canvas, engine: PatternEngine, grid: PatternGrid, reducedMotion: Boolean, paints: PatternPaints) {
    val lock = engine.lock.value
    val opacity = clamp(if (reducedMotion) lock else lock * 3f, 0f, 1f)
    if (opacity <= 0.001f) return
    val e = if (reducedMotion) min(1f, lock) else lock
    val blob = 20f
    val w = blob + (66f - blob) * e
    val h = blob + (54f - blob) * e
    val radius = max(0f, blob / 2f + (15f - blob / 2f) * min(1f, e))
    nc.save()
    nc.translate(grid.cx, grid.cy)
    paints.rect.set(-w / 2f, -h / 2f + 14f * e, w / 2f, h / 2f + 14f * e)
    paints.fill.color = withAlpha(RIGHT, opacity * opacity * 0.5f)
    paints.fill.maskFilter = paints.blur(22f)
    nc.drawRoundRect(paints.rect, radius, radius, paints.fill)
    paints.fill.maskFilter = null

    val shackleEnd = clamp(if (reducedMotion) lock else lock * 1.6f - 0.6f, 0f, 1f)
    if (shackleEnd > 0f) {
        val o = if (reducedMotion) 0f else engine.open.value
        nc.save()
        // Pivot on the right leg's foot, so the left leg swings out of the body as it lifts.
        nc.translate(18f, -54f / 2f + 14f + 3f - 13f * o)
        nc.rotate(Math.toDegrees(-0.32 * o).toFloat())
        nc.translate(-18f, 0f)
        paints.trimmed.reset()
        paints.shackleMeasure.getSegment(0f, paints.shackleMeasure.length * shackleEnd, paints.trimmed, true)
        val s = paints.stroke
        s.maskFilter = null
        s.strokeWidth = 7.5f
        s.color = withAlpha(RIGHT, opacity)
        nc.drawPath(paints.trimmed, s)
        nc.restore()
    }
    paints.fill.color = withAlpha(RIGHT, opacity)
    nc.drawRoundRect(paints.rect, radius, radius, paints.fill)
    val checkEnd = clamp(engine.check.value, 0f, 1f)
    if (checkEnd > 0f) {
        nc.save()
        nc.translate(0f, 14f)
        paints.trimmed.reset()
        paints.tickMeasure.getSegment(0f, paints.tickMeasure.length * checkEnd, paints.trimmed, true)
        val s = paints.stroke
        s.strokeWidth = 5f
        s.color = withAlpha(BACKGROUND, opacity)
        nc.drawPath(paints.trimmed, s)
        nc.restore()
    }
    nc.restore()
}
