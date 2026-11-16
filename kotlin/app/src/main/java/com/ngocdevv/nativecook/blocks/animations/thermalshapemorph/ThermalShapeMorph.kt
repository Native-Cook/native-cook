package com.ngocdevv.nativecook.blocks.animations.thermalshapemorph

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** autoPlay: the five morphs play by themselves every LOOP seconds. */
const val LOOP = 8.0

// Layout, in dp from the emblem's centre (which sits 40 dp above the screen's middle).
private const val CENTER_SHIFT = -40f
private const val RADIUS = 104.0
private const val EYEBROW_Y = -186f
private const val NAME_Y = 184f
private const val LINE_Y = 218f
private const val DOTS_Y = 258f
private const val HINT_Y = 296f
private const val DRAG_PER_STATE = 120.0
private const val RETURN_FOR = 0.9
private const val USER_HOLD = 2.0

// The loop: rest, then five morphs of MORPH_FOR seconds, STEP apart.
private const val REST_FOR = 0.8
private const val MORPH_FOR = 1.2
private const val STEP = 1.45
private const val SAMPLES = 180

private class ThermalState(
    val name: String,
    val line: String,
    /** 0 hot … 1 cool. */
    val cool: Double,
    /** Direction of the bright side of the fill, degrees (y down). */
    val angle: Double,
    val coreX: Double,
    val coreY: Double,
    val coreRadius: Double,
    val coreAlpha: Double,
)

private val States = listOf(
    ThermalState("Spark", "Quick, bright and ready to move", 0.0, 100.0, 0.0, 0.06, 0.78, 0.95),
    ThermalState("Steady", "Even, grounded and calm", 1.0, 55.0, 0.25, 0.38, 0.62, 0.8),
    ThermalState("Glow", "Warm, full and generous", 0.0, -75.0, 0.12, -0.34, 0.66, 0.9),
    ThermalState("Bloom", "Open and slowly growing", 1.0, -160.0, 0.0, 0.0, 0.85, 0.9),
    ThermalState("Drift", "Loose, easy, going with it", 0.8, -320.0, 0.3, 0.28, 0.6, 0.75),
)

private val Hot = intArrayOf(0x14040E, 0x6E0A1F, 0xD8231C, 0xFF7418, 0xFFCF3A, 0xFFF7DA)
private val Cold = intArrayOf(0x060619, 0x17168A, 0x3434E0, 0x6E78FF, 0xBFC4FF, 0xF7F6FF)
private val RampStops = floatArrayOf(0f, 0.18f, 0.40f, 0.62f, 0.82f, 1f)
private const val RIM_DARK = 0xFF2A1F
private const val RIM_HOT = 0xFF7A3A
private const val RIM_COOL = 0x8A7BFF
private const val VIOLET = 0x4A3AFF
private const val WARM_GLOW = 0xFF5A36
private const val ACCENT_HOT = 0xFF8A3D
private const val ACCENT_COOL = 0x8C8CFF
private const val DOT_DIM = 0x34304A

/** One moment: segment start state i, outline mix m, colour/caption mix q, loop time t. */
private class ThermalPose(val i: Int, val m: Double, val q: Double, val t: Double) {
    val u: Double get() = i + m

    fun captionAlpha(k: Int): Double = when (k) {
        i -> 1 - smoothstep(0.3, 0.5, q)
        wrap(i + 1) -> smoothstep(0.5, 0.7, q)
        else -> 0.0
    }

    fun captionShift(k: Int): Double = when (k) {
        i -> -8 * smoothstep(0.3, 0.5, q)
        wrap(i + 1) -> 8 * (1 - smoothstep(0.5, 0.7, q))
        else -> 0.0
    }

    companion object {
        fun of(u: Double, t: Double): ThermalPose {
            val f = floor(u)
            return ThermalPose(wrap(f.toInt()), u - f, clamp(u - f), t)
        }

        fun auto(t: Double): ThermalPose {
            for (k in 0 until 5) {
                val s = REST_FOR + STEP * k
                if (t < s) return ThermalPose(k, 0.0, 0.0, t)
                if (t < s + MORPH_FOR) {
                    val p = (t - s) / MORPH_FOR
                    return ThermalPose(k, easeInOutBack(p), easeInOutCubic(p), t)
                }
            }
            return ThermalPose(0, 0.0, 0.0, t)
        }
    }
}

/** A move of the morph position started by a finger (or the ease back to rest). */
private class ThermalManual(
    val from: Double,
    val to: Double,
    val start: Double,
    val duration: Double,
    val back: Boolean,
    val tFrom: Double,
    val tTo: Double,
)

/** The clock: plain fields read by the frame loop and the draw; [tick] invalidates the drawing. */
private class ThermalClock {
    val tick = mutableLongStateOf(0L)
    var now = 0.0
    var origin = -1.0
    var manual: ThermalManual? = null
    var autoPlay = false
    var reduced = false
    var dragging = false
    var returning = false
    var lastTouch = -1e9
    var dragBase = 0.0
    var lastWhole = 0

    fun step(nanos: Long, autoPlay: Boolean, reduced: Boolean) {
        val s = nanos / 1e9
        if (origin < 0) origin = s
        now = s
        this.autoPlay = autoPlay
        this.reduced = reduced
        val man = manual
        if (autoPlay && man != null && !dragging) {
            if (returning) {
                if (s >= man.start + man.duration) {
                    manual = null
                    returning = false
                    origin = s
                }
            } else if (s - lastTouch >= USER_HOLD) {
                // 2 s after the last touch: ease back to the resting star, then restart the loop.
                val u = pose().u
                val home = (u / 5).roundToInt() * 5.0
                returning = true
                manual = ThermalManual(u, home, s, if (reduced) 0.0 else RETURN_FOR, false,
                    man.tTo, if (man.tTo < LOOP / 2) 0.0 else LOOP)
            }
        }
        tick.longValue = nanos
    }

    fun pose(): ThermalPose {
        val man = manual
        if (man != null) {
            val k = clamp((now - man.start) / max(man.duration, 0.0001))
            val e = if (man.back) easeOutBack(k) else easeInOutCubic(k)
            val u = man.from + (man.to - man.from) * e
            val t = when {
                reduced -> 0.0
                autoPlay -> man.tFrom + (man.tTo - man.tFrom) * easeInOutCubic(k)
                else -> ((now - origin) % LOOP)
            }
            return ThermalPose.of(u, t)
        }
        if (reduced) return ThermalPose(0, 0.0, 0.0, 0.0)
        val t = (now - max(origin, 0.0)).mod(LOOP)
        return if (autoPlay) ThermalPose.auto(t) else ThermalPose(0, 0.0, 0.0, t)
    }

    fun touchDown() {
        val p = pose()
        dragging = true
        returning = false
        lastTouch = now
        dragBase = p.u
        lastWhole = floor(p.u + 0.5).toInt()
        manual = ThermalManual(p.u, p.u, now, 0.0, false, p.t, p.t)
    }

    /** Scrubs by [dx] dp; true when the position crosses a whole state. */
    fun drag(dx: Double): Boolean {
        val man = manual ?: return false
        lastTouch = now
        val u = dragBase - dx / DRAG_PER_STATE
        manual = ThermalManual(u, u, now, 0.0, false, man.tFrom, man.tTo)
        val whole = floor(u + 0.5).toInt()
        if (whole != lastWhole) {
            lastWhole = whole
            return true
        }
        return false
    }

    fun release(moved: Boolean) {
        dragging = false
        if (moved) settle(Math.round(pose().u).toDouble(), 0.45) else tap()
    }

    fun tap() {
        returning = false
        settle(floor(pose().u + 0.02) + 1, 0.75)
    }

    private fun settle(target: Double, duration: Double) {
        val p = pose()
        val t = manual?.tTo ?: p.t
        lastTouch = now
        manual = ThermalManual(p.u, target, now, if (reduced) 0.0 else duration, true, t, t)
    }
}

/**
 * An energy check-in emblem for an invented wellness app, Vessa: one glowing shape filled like a
 * thermal camera image morphs star → diamond → circle → clover → blob and back, swapping its hot
 * and cool palettes, while the state's name crossfades under it. Tap for the next state, drag
 * sideways to scrub the morph.
 *
 * @param autoPlay Plays the five morphs by themselves every [LOOP] seconds.
 */
@Composable
fun ThermalShapeMorph(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val clock = remember { ThermalClock() }
    val painter = remember { ThermalPainter() }
    val auto = rememberUpdatedState(autoPlay)
    val still = rememberUpdatedState(reduced)

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now -> clock.step(now, auto.value, still.value) }
        }
    }

    val density = LocalDensity.current.density
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF07060B))
            .semantics {
                role = Role.Button
                contentDescription = "Energy check-in emblem. Tap for the next state."
                onClick {
                    clock.tap()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    clock.touchDown()
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = (change.position.x - down.position.x) / density
                        if (abs(dx) > 6f) moved = true
                        if (moved) {
                            change.consume()
                            if (clock.drag(dx.toDouble())) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                    if (!moved) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    clock.release(moved)
                }
            },
    ) {
        val cy = maxHeight / 2 + CENTER_SHIFT.dp
        Canvas(Modifier.fillMaxSize()) {
            clock.tick.longValue
            val pose = clock.pose()
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(density, density)
                painter.draw(native, size.width / density, size.height / density, pose)
                native.restore()
            }
        }
        CenteredText("VESSA · ENERGY CHECK-IN", EyebrowStyle, cy + EYEBROW_Y.dp)
        States.forEachIndexed { k, state ->
            val layer = Modifier.graphicsLayer {
                clock.tick.longValue
                val pose = clock.pose()
                alpha = pose.captionAlpha(k).toFloat()
                translationY = pose.captionShift(k).toFloat() * density
            }
            val lineLayer = Modifier.graphicsLayer {
                clock.tick.longValue
                val pose = clock.pose()
                alpha = pose.captionAlpha(k).toFloat()
                translationY = pose.captionShift(k).toFloat() * 0.6f * density
            }
            CenteredText(state.name, NameStyle, cy + NAME_Y.dp, layer)
            CenteredText(state.line, LineStyle, cy + LINE_Y.dp, lineLayer)
        }
        CenteredText("Tap to shift · drag to scrub", HintStyle, cy + HINT_Y.dp)
    }
}

/** A single line centred on x and on [y] (from the top of the screen). */
@Composable
private fun CenteredText(text: String, style: TextStyle, y: Dp, extra: Modifier = Modifier) {
    Box(
        Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (constraints.maxWidth - placeable.width) / 2,
                        (y.toPx() - placeable.height / 2f).roundToInt(),
                    )
                }
            },
    ) {
        Text(text = text, style = style, maxLines = 1, softWrap = false, modifier = extra)
    }
}

private val NoPadding = PlatformTextStyle(includeFontPadding = false)
private val EyebrowStyle = TextStyle(color = Color(0xFF7D7896), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.2.sp, platformStyle = NoPadding)
private val NameStyle = TextStyle(color = Color(0xFFF3F0FA), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, platformStyle = NoPadding)
private val LineStyle = TextStyle(color = Color(0xFF9D98B5), fontSize = 15.sp, platformStyle = NoPadding)
private val HintStyle = TextStyle(color = Color(0xFF4F4A66), fontSize = 12.sp, fontWeight = FontWeight.Medium, platformStyle = NoPadding)

// MARK: Drawing

/** Radius (in units of R) of state [k] at angle [a] (radians, y down). */
private fun shapeRadius(k: Int, a: Double): Double = when (wrap(k)) {
    0 -> {
        val p = 0.72
        1.06 * (abs(cos(a)).pow(p) + abs(sin(a)).pow(p)).pow(-1 / p)
    }
    1 -> {
        val n = 3.4
        val b = a - PI / 4
        0.80 * (abs(cos(b)).pow(n) + abs(sin(b)).pow(n)).pow(-1 / n)
    }
    2 -> 0.86
    3 -> 0.84 * (1 - 0.12 * cos(4 * a) - 0.04 * cos(8 * a))
    else -> 0.85 * (1 + 0.075 * cos(2 * a + 0.6) + 0.06 * sin(3 * a + 1.1) + 0.045 * cos(5 * a + 2.0))
}

private class ThermalPainter {
    private val radii = DoubleArray(SAMPLES)
    private val xs = FloatArray(SAMPLES)
    private val ys = FloatArray(SAMPLES)
    private val shape = Path()
    private val scaled = Path()
    private val ramp = IntArray(6)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val blurs = HashMap<Float, BlurMaskFilter>()
    private val rect = RectF()

    private fun blur(sigma: Float) = blurs.getOrPut(sigma) { BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) }

    /** Clears what a previous layer left on the paint (shader, alpha, filter, blend). */
    private fun Paint.fresh(style: Paint.Style = Paint.Style.FILL): Paint {
        reset()
        isAntiAlias = true
        this.style = style
        return this
    }

    private fun radii(pose: ThermalPose) {
        val tau = 2 * PI
        val w1 = tau * 2 * pose.t / LOOP
        val w2 = tau * 3 * pose.t / LOOP
        val swell = 1 + 0.035 * sin(PI * pose.q)
        for (j in 0 until SAMPLES) {
            val a = tau * j / SAMPLES
            val r0 = shapeRadius(pose.i, a)
            val r1 = shapeRadius(pose.i + 1, a)
            val living = 1 + 0.010 * sin(3 * a + w1) + 0.006 * sin(5 * a - w2)
            radii[j] = (r0 + (r1 - r0) * pose.m) * living * swell * RADIUS
        }
    }

    /** A smooth closed path through the sampled outline, scaled about the centre. */
    private fun outline(path: Path, cx: Float, cy: Float, scale: Double) {
        for (j in 0 until SAMPLES) {
            val a = 2 * PI * j / SAMPLES
            val r = radii[j] * scale
            xs[j] = (cx + r * cos(a)).toFloat()
            ys[j] = (cy + r * sin(a)).toFloat()
        }
        path.rewind()
        path.moveTo((xs[SAMPLES - 1] + xs[0]) / 2, (ys[SAMPLES - 1] + ys[0]) / 2)
        for (j in 0 until SAMPLES) {
            val n = (j + 1) % SAMPLES
            path.quadTo(xs[j], ys[j], (xs[j] + xs[n]) / 2, (ys[j] + ys[n]) / 2)
        }
        path.close()
    }

    fun draw(canvas: android.graphics.Canvas, w: Float, h: Float, pose: ThermalPose) {
        val cx = w / 2
        val cy = h / 2 + CENTER_SHIFT
        val a = States[pose.i]
        val b = States[wrap(pose.i + 1)]
        val q = pose.q
        val tau = 2 * PI
        val cool = lerp(a.cool, b.cool, q)
        // Angles keep turning one way round the loop: the last state's 100° is written as −260°.
        val angleB = if (pose.i == 4) -260.0 else b.angle
        val angle = lerp(a.angle, angleB, q) + 8 * sin(tau * pose.t / LOOP)
        val coreX = lerp(a.coreX, b.coreX, q) + 0.07 * sin(tau * pose.t / LOOP)
        val coreY = lerp(a.coreY, b.coreY, q) + 0.07 * cos(tau * 2 * pose.t / LOOP)
        val coreRadius = lerp(a.coreRadius, b.coreRadius, q)
        val coreAlpha = lerp(a.coreAlpha, b.coreAlpha, q)
        for (k in 0 until 6) ramp[k] = mixRgb(Hot[k], Cold[k], cool)
        val breath = 1 + 0.018 * sin(tau * 2 * pose.t / LOOP)

        // Stage: a dark violet vignette.
        fill.fresh().shader = RadialGradient(
            cx, cy, h * 0.75f,
            intArrayOf(argb(0x120E1F, 1.0), argb(0x07060B, 1.0), argb(0x040307, 1.0)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, fill)

        radii(pose)
        outline(shape, cx, cy, 1.0)
        val rad = Math.toRadians(angle)
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val r = RADIUS.toFloat()
        val darkX = cx - dx * r * 0.9f
        val darkY = cy - dy * r * 0.9f
        val brightX = cx + dx * r * 0.95f
        val brightY = cy + dy * r * 0.95f

        // Glows, added together in their own layer, which then sits on the stage.
        canvas.saveLayer(null, null)
        outline(scaled, cx, cy, 1.3 * breath)
        glow.fresh().apply {
            color = argb(WARM_GLOW, lerp(0.42, 0.32, cool))
            maskFilter = blur(44f)
            xfermode = add
        }
        canvas.drawPath(scaled, glow)
        outline(scaled, cx, cy, 1.09 * breath)
        glow.fresh().apply {
            color = argb(VIOLET, 1.0)
            maskFilter = blur(24f)
            xfermode = add
        }
        canvas.drawPath(scaled, glow)
        glow.fresh(Paint.Style.STROKE).apply {
            color = argb(VIOLET, 1.0)
            strokeWidth = 16f
            maskFilter = blur(9f)
            xfermode = add
        }
        canvas.drawPath(shape, glow)
        canvas.restore()

        // Body: the thermal ramp, the hot core and a cooler band along the edge.
        canvas.save()
        canvas.clipPath(shape)
        fill.fresh().shader = LinearGradient(
            darkX, darkY, brightX, brightY,
            IntArray(6) { argb(ramp[it], 1.0) }, RampStops, Shader.TileMode.CLAMP,
        )
        canvas.drawPath(shape, fill)
        val coreCx = (cx + coreX * RADIUS).toFloat()
        val coreCy = (cy + coreY * RADIUS).toFloat()
        val cr = (coreRadius * RADIUS).toFloat()
        fill.fresh().shader = RadialGradient(
            coreCx, coreCy, cr,
            intArrayOf(
                argb(ramp[5], coreAlpha), argb(ramp[5], coreAlpha * 0.9), argb(ramp[4], coreAlpha * 0.7),
                argb(ramp[3], coreAlpha * 0.25), argb(ramp[3], 0.0),
            ),
            floatArrayOf(0f, 0.22f, 0.5f, 0.78f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(coreCx, coreCy, cr, fill)
        glow.fresh(Paint.Style.STROKE).apply {
            color = argb(ramp[1], 0.6)
            strokeWidth = 24f
            maskFilter = blur(10f)
        }
        canvas.drawPath(shape, glow)
        canvas.restore()

        // Rim: a soft glow and a thin hot line, red on the dark side.
        val rimBright = mixRgb(RIM_HOT, RIM_COOL, cool)
        val rimColors = intArrayOf(argb(RIM_DARK, 1.0), argb(mixRgb(RIM_DARK, rimBright, 0.5), 1.0), argb(rimBright, 1.0))
        glow.fresh(Paint.Style.STROKE).apply {
            shader = LinearGradient(darkX, darkY, brightX, brightY, rimColors, null, Shader.TileMode.CLAMP)
            alpha = (0.9 * 255).roundToInt()
            strokeWidth = 6f
            maskFilter = blur(4f)
        }
        canvas.drawPath(shape, glow)
        fill.fresh(Paint.Style.STROKE).apply {
            shader = LinearGradient(darkX, darkY, brightX, brightY, rimColors, null, Shader.TileMode.CLAMP)
            strokeWidth = 2f
        }
        canvas.drawPath(shape, fill)

        // Dots: one per state, the shown one stretched into a pill.
        val uC = pose.i + q
        val accent = mixRgb(ACCENT_HOT, ACCENT_COOL, cool)
        val dotsY = cy + DOTS_Y
        val base = 6f
        val grow = 14f
        val gap = 10f
        val widths = FloatArray(5)
        val on = DoubleArray(5)
        for (j in 0 until 5) {
            var d = abs(uC - j)
            d = min(d, 5 - d)
            on[j] = max(0.0, 1 - d)
            widths[j] = base + grow * on[j].toFloat()
        }
        var x = cx - (widths.sum() + gap * 4) / 2
        for (j in 0 until 5) {
            fill.fresh().color = argb(mixRgb(DOT_DIM, accent, on[j]), 1.0)
            rect.set(x, dotsY - base / 2, x + widths[j], dotsY + base / 2)
            canvas.drawRoundRect(rect, base / 2, base / 2, fill)
            x += widths[j] + gap
        }
    }
}

// MARK: Math

private fun wrap(k: Int) = ((k % 5) + 5) % 5
private fun clamp(x: Double) = min(max(x, 0.0), 1.0)
private fun lerp(a: Double, b: Double, k: Double) = a + (b - a) * k
private fun smoothstep(a: Double, b: Double, x: Double): Double {
    val k = clamp((x - a) / (b - a))
    return k * k * (3 - 2 * k)
}
private fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

/** About 3 % anticipation and overshoot. */
private fun easeInOutBack(x: Double): Double {
    val c2 = 0.9 * 1.525
    return if (x < 0.5) {
        (2 * x).pow(2) * ((c2 + 1) * 2 * x - c2) / 2
    } else {
        ((2 * x - 2).pow(2) * ((c2 + 1) * (x * 2 - 2) + c2) + 2) / 2
    }
}

/** Lands with about 4 % overshoot. */
private fun easeOutBack(x: Double): Double {
    val c1 = 1.1
    val c3 = c1 + 1
    return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2)
}

private fun mixRgb(a: Int, b: Int, k: Double): Int {
    fun ch(c: Int, s: Int) = (c shr s) and 0xFF
    fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * k).roundToInt().coerceIn(0, 255)
    return (m(16) shl 16) or (m(8) shl 8) or m(0)
}

private fun argb(rgb: Int, alpha: Double): Int =
    ((clamp(alpha) * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)

@Preview
@Composable
private fun ThermalShapeMorphPreview() {
    ThermalShapeMorph(autoPlay = true)
}
