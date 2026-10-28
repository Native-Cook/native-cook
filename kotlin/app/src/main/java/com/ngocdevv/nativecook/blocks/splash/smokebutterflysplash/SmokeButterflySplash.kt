package com.ngocdevv.nativecook.blocks.splash.smokebutterflysplash

import android.app.Activity
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val LOOP = 8.0
/** The story time of the held, formed pose; a touch blends to it and the loop resumes from it. */
private const val PEAK = 5.0
private const val IDLE = 2.0

/**
 * The launch splash of Driftwing, a wind-down app. On a dark stage lit softly from the top, a wisp of
 * cyan smoke rises, curls and condenses into a glowing veined butterfly; "Driftwing" and its tagline
 * fade in, the wings beat twice, then the butterfly blows apart into billowing smoke and sparkling
 * dust that clears back to the dark stage. The smoke is one AGSL shader (API 33+, a drawn fallback
 * below) masked by a butterfly distance field; the dust and type are drawn on top.
 *
 * Drag to stir the smoke and pull the butterfly apart; it re-forms on release, and 2 s later
 * dissolves and the loop carries on. Reduced motion: the formed butterfly, still.
 */
@Composable
fun SmokeButterflySplash(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(animationsOff(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = animationsOff(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    val clock = remember { mutableDoubleStateOf(0.0) }
    val touch = remember { SmokeButterflySplashTouch() }
    val awake = remember { mutableStateOf(false) }
    val running = !reduced && active && (autoPlay || awake.value)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var startNanos = -1L
        val base = clock.doubleValue
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                clock.doubleValue = base + (now - startNanos) / 1e9
            }
            if (!autoPlay && !touch.touching) {
                val release = touch.release
                if (release != null && clock.doubleValue > release + IDLE + (LOOP - PEAK) + 0.2) {
                    awake.value = false
                    break
                }
            }
        }
    }

    val painter = remember { SmokeButterflySplashPainter() }
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Driftwing. Breathe out. Let the day dissolve." }
            .pointerInput(reduced) {
                if (reduced) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val scale = min(size.width / 393f, size.height / 852f)
                    val ox = (size.width - 393f * scale) / 2f
                    val oy = (size.height - 852f * scale) / 2f
                    fun design(x: Float, y: Float) = Pair(((x - ox) / scale).toDouble(), ((y - oy) / scale).toDouble())
                    val start = design(down.position.x, down.position.y)
                    touch.begin(start.first, start.second, clock.doubleValue)
                    awake.value = true
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val point = design(change.position.x, change.position.y)
                            if (touch.move(point.first, point.second, clock.doubleValue)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    } finally {
                        touch.end(clock.doubleValue)
                    }
                }
            },
    ) {
        val state = SmokeButterflySplashMotion.frame(clock.doubleValue, touch, autoPlay, reduced)
        val canvas = drawContext.canvas.nativeCanvas
        painter.draw(canvas, size.width, size.height, state)
    }
}

private fun animationsOff(context: android.content.Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Touch state read by the frame; plain fields, so writing them never recomposes. */
internal class SmokeButterflySplashTouch {
    var touching = false
    var start = 0.0
    var engageFrom = 0.0
    var release: Double? = null
    /** Story time = clock - base (autoPlay wraps it). */
    var base = 0.0
    var resumed = false
    var fingerX = 196.5
    var fingerY = 360.0
    var velX = 0.0
    var velY = 0.0
    var dragX = 0.0
    var dragY = 0.0
    var pullRaw = 0.0
    var pullAtRelease = 0.0
    private var torn = false
    private var anchorX = 0.0
    private var anchorY = 0.0
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastClock = 0.0

    fun engage(clock: Double): Double {
        val r = release
        val held = touching || (r != null && clock < r + IDLE)
        if (!held) return 0.0
        return engageFrom + (1 - engageFrom) * SmokeButterflySplashMotion.easeOut((clock - start) / 0.45)
    }

    fun stir(clock: Double): Double {
        if (touching) return SmokeButterflySplashMotion.easeOut((clock - start) / 0.25)
        val r = release ?: return 0.0
        if (clock - r >= 4) return 0.0
        return exp(-(clock - r) / 0.45)
    }

    fun pull(clock: Double): Double {
        if (touching) return pullRaw
        val r = release ?: return 0.0
        val age = max(0.0, clock - r)
        val tau = 0.13
        return pullAtRelease * (1 + age / tau) * exp(-age / tau)
    }

    fun begin(x: Double, y: Double, clock: Double) {
        val current = engage(clock)
        val r = release
        if (r != null && clock >= r + IDLE) {
            base = r + IDLE - PEAK
            resumed = true
        }
        engageFrom = current
        touching = true
        start = clock
        release = null
        anchorX = x; anchorY = y
        lastX = x; lastY = y
        lastClock = clock
        fingerX = x; fingerY = y
        velX = 0.0; velY = 0.0
        dragX = 0.0; dragY = 0.0
        pullRaw = 0.0
        torn = false
    }

    /** Returns true when the wings tear (pull passes 0.6), for a haptic. */
    fun move(x: Double, y: Double, clock: Double): Boolean {
        val dt = clock - lastClock
        if (dt > 0.004) {
            velX += ((x - lastX) / dt - velX) * 0.3
            velY += ((y - lastY) / dt - velY) * 0.3
            lastX = x; lastY = y
            lastClock = clock
        }
        fingerX = x; fingerY = y
        dragX = x - anchorX; dragY = y - anchorY
        pullRaw = min(1.0, hypot(dragX, dragY) / 220)
        if (pullRaw > 0.6 && !torn) { torn = true; return true }
        if (pullRaw < 0.4) torn = false
        return false
    }

    fun end(clock: Double) {
        if (!touching) return
        touching = false
        release = clock
        pullAtRelease = pullRaw
    }
}

internal data class SmokeButterflySplashParams(
    var rise: Double = 0.0, var column: Double = 0.0, var halo: Double = 0.0, var form: Double = 0.0,
    var spread: Double = 0.0, var flap: Double = 0.0, var veins: Double = 0.0,
    var title: Double = 0.0, var tagline: Double = 0.0, var light: Double = 0.65, var ember: Double = 1.0,
) {
    fun mixed(o: SmokeButterflySplashParams, k: Double): SmokeButterflySplashParams {
        fun m(a: Double, b: Double) = a + (b - a) * k
        return SmokeButterflySplashParams(
            m(rise, o.rise), m(column, o.column), m(halo, o.halo), m(form, o.form), m(spread, o.spread),
            m(flap, o.flap), m(veins, o.veins), m(title, o.title), m(tagline, o.tagline), m(light, o.light), m(ember, o.ember),
        )
    }
}

internal class SmokeButterflySplashFrame(
    val params: SmokeButterflySplashParams,
    /** Continuous loop phase for the smoke's drift and the dust (seconds, 0 until LOOP). */
    val clock: Double,
    /** Story time that drives the sparkles' gather and burst. */
    val story: Double,
    val engage: Double = 0.0,
    val stir: Double = 0.0,
    val pull: Double = 0.0,
    val fingerX: Double = 196.5,
    val fingerY: Double = 360.0,
    val velX: Double = 0.0,
    val velY: Double = 0.0,
    val dragX: Double = 0.0,
    val dragY: Double = 0.0,
)

/** The loop's timeline as pure functions of the story time. */
internal object SmokeButterflySplashMotion {
    fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun easeOut(x: Double): Double { val u = x.coerceIn(0.0, 1.0); return 1 - (1 - u).pow(3) }
    fun easeIn(x: Double): Double { val u = x.coerceIn(0.0, 1.0); return u * u * u }
    fun easeInOut(x: Double): Double {
        val u = x.coerceIn(0.0, 1.0); return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
    }
    fun wrap(t: Double): Double { val r = t % LOOP; return if (r < 0) r + LOOP else r }
    fun beat(t: Double, a: Double, b: Double): Double {
        if (t <= a || t >= b) return 0.0
        val s = sin(PI * (t - a) / (b - a)); return s * s
    }

    fun params(t: Double): SmokeButterflySplashParams {
        val p = SmokeButterflySplashParams()
        p.rise = if (t < 6.5) easeOut(seg(t, 0.5, 2.1)) else 0.0
        p.column = p.rise * (1 - easeInOut(seg(t, 1.7, 2.9)))
        p.halo = easeOut(seg(t, 1.5, 2.8)) * (1 - easeInOut(seg(t, 6.0, 7.3)))
        p.form = easeInOut(seg(t, 1.6, 3.0)) * (1 - easeIn(seg(t, 5.0, 6.0)))
        p.spread = easeOut(seg(t, 5.0, 6.6)) * (1 - seg(t, 7.3, 7.4))
        p.flap = beat(t, 3.35, 3.9) + beat(t, 3.95, 4.5)
        p.veins = if (t < 6.5) easeOut(seg(t, 2.4, 3.2)) else 0.0
        p.title = easeOut(seg(t, 2.9, 3.4)) * (1 - easeIn(seg(t, 5.6, 6.1)))
        p.tagline = easeOut(seg(t, 3.05, 3.55)) * (1 - easeIn(seg(t, 5.65, 6.15)))
        // A faint incense-like thread at rest: it feeds the rising wisp and comes back at the end.
        p.ember = 1 - easeInOut(seg(t, 0.6, 1.6)) + easeInOut(seg(t, 6.6, 7.6))
        p.light = 0.65 + 0.35 * max(p.form, p.halo * 0.6)
        return p
    }

    fun frame(clock: Double, touch: SmokeButterflySplashTouch, autoPlay: Boolean, reduced: Boolean): SmokeButterflySplashFrame {
        if (reduced) return SmokeButterflySplashFrame(params(PEAK), PEAK, PEAK)
        val engage = touch.engage(clock)
        val release = touch.release
        var story = when {
            release != null && !touch.touching && clock >= release + IDLE -> {
                val base = release + IDLE - PEAK
                if (autoPlay) wrap(clock - base) else min(LOOP, clock - base)
            }
            autoPlay -> wrap(clock - touch.base)
            touch.resumed -> min(LOOP, clock - touch.base)
            else -> 0.0
        }
        if (story >= LOOP) story = 0.0
        var p = params(story)
        if (engage > 0) p = p.mixed(params(PEAK), engage)
        return SmokeButterflySplashFrame(
            p, if (autoPlay) wrap(clock) else story, story, engage, touch.stir(clock), touch.pull(clock),
            touch.fingerX, touch.fingerY, touch.velX, touch.velY, touch.dragX, touch.dragY,
        )
    }
}

private class SmokeButterflySplashPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderPaint = Paint()
    private val shader = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(smokeButterflySplashShader) else null
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun hash(x: Double): Double { val v = sin(x * 12.9898) * 43758.5453; return v - floor(v) }

    private fun ink(color: Int, alpha: Double): Paint {
        paint.reset(); paint.isAntiAlias = true
        paint.color = color
        paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        return paint
    }

    private fun dot(c: NativeCanvas, x: Double, y: Double, r: Double, color: Int, alpha: Double) {
        if (alpha <= 0.004) return
        val p = ink(color, alpha); p.xfermode = add
        c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), p)
    }

    fun draw(c: NativeCanvas, width: Float, height: Float, s: SmokeButterflySplashFrame) {
        val p = s.params
        val scale = min(width / 393f, height / 852f)
        val sh = shader
        if (sh != null && Build.VERSION.SDK_INT >= 33) {
            val speed = hypot(s.velX, s.velY)
            val k = if (speed > 1500) 1500 / speed else 1.0
            sh.setFloatUniform("uFrame", width, height, (2 * PI * s.clock / LOOP).toFloat(), p.light.toFloat())
            sh.setFloatUniform("uA", p.rise.toFloat(), p.column.toFloat(), p.halo.toFloat(), p.form.toFloat())
            sh.setFloatUniform("uB", p.spread.toFloat(), p.flap.toFloat(), p.veins.toFloat(), s.pull.toFloat())
            sh.setFloatUniform("uTouch", s.fingerX.toFloat(), s.fingerY.toFloat(), s.stir.toFloat(), p.ember.toFloat())
            sh.setFloatUniform("uDrag", (s.velX * k / 1500).toFloat(), (s.velY * k / 1500).toFloat(), s.dragX.toFloat(), s.dragY.toFloat())
            shaderPaint.shader = sh
            c.drawRect(0f, 0f, width, height, shaderPaint)
            c.save()
            c.translate((width - 393 * scale) / 2, (height - 852 * scale) / 2)
            c.scale(scale, scale)
        } else {
            c.drawColor(0xFF02040A.toInt())
            c.save()
            c.translate((width - 393 * scale) / 2, (height - 852 * scale) / 2)
            c.scale(scale, scale)
            fallback(c, s)
        }
        art(c, s)
        c.restore()
    }

    // API 26-32: the same story with soft blurred shapes instead of the smoke field.
    private fun fallback(c: NativeCanvas, s: SmokeButterflySplashFrame) {
        val p = s.params
        val bg = ink(0xFFFFFFFF.toInt(), 1.0)
        bg.shader = LinearGradient(0f, 0f, 0f, 852f, intArrayOf(0xFF061226.toInt(), 0xFF02040A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 393f, 852f, bg)
        val glow = ink(0xFFFFFFFF.toInt(), p.light * 0.85)
        glow.shader = RadialGradient(196.5f, -20f, 220f, intArrayOf(0xFF2E6CFF.toInt(), 0x002E6CFF), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 393f, 420f, glow)
        val theta = 2 * PI * s.clock / LOOP
        // Smoke puffs: the wisp, then a ring of puffs around the wings that spreads out.
        val puff = ink(0xFF3CCBFF.toInt(), 1.0)
        puff.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        val tip = 360 + 150 * (1.15 + (-0.05 - 1.15) * p.rise)
        val wisp = max(p.column, 0.55 * p.ember)
        for (i in 0 until 6) {
            val y = tip + i * 22
            puff.alpha = (wisp * (0.45 - i * 0.06) * 255).coerceIn(0.0, 255.0).toInt()
            c.drawCircle((196.5 + 10 * sin(theta + i)).toFloat(), y.toFloat(), (16 - i * 1.5).toFloat(), puff)
        }
        for (i in 0 until 14) {
            val a = i / 14.0 * 2 * PI + 0.4 * sin(theta + i)
            val r = (120 + 40 * hash(i + 3.0)) * (1 + 0.8 * p.spread)
            puff.alpha = (p.halo * (1 - 0.4 * p.spread) * 0.35 * 255).coerceIn(0.0, 255.0).toInt()
            c.drawCircle((196.5 + cos(a) * r).toFloat(), (360 + sin(a) * r * 0.55 - 40 * p.spread).toFloat(), (34 + 20 * p.spread).toFloat(), puff)
        }
        // The butterfly as flat wings that fade with form.
        if (p.form > 0.01) {
            val k = 1 - 0.62 * p.flap
            val wing = Path()
            for (side in listOf(1.0, -1.0)) {
                fun x(v: Double) = (196.5 + side * v * 150 * k).toFloat()
                fun y(v: Double) = (360 + v * 150).toFloat()
                wing.moveTo(x(0.04), y(-0.08)); wing.lineTo(x(0.92), y(-0.60)); wing.quadTo(x(1.0), y(-0.2), x(0.66), y(0.04)); wing.close()
                wing.moveTo(x(0.04), y(0.0)); wing.quadTo(x(0.70), y(0.10), x(0.55), y(0.48)); wing.quadTo(x(0.25), y(0.62), x(0.04), y(0.12)); wing.close()
            }
            val wp = ink(0xFFFFFFFF.toInt(), p.form * 0.92)
            wp.shader = LinearGradient(0f, 270f, 0f, 450f, intArrayOf(0xFF9EF4FF.toInt(), 0xFF1E7FD0.toInt()), null, Shader.TileMode.CLAMP)
            c.drawPath(wing, wp)
            val body = ink(0xFF0A1A33.toInt(), p.form)
            c.drawRoundRect(193.5f, 324f, 199.5f, 411f, 3f, 3f, body)
        }
    }

    private fun art(c: NativeCanvas, s: SmokeButterflySplashFrame) {
        val tau = 2 * PI
        // Ambient motes drifting up through the light; each rises once or twice per loop.
        for (i in 0 until 26) {
            val h1 = hash(i + 1.0); val h2 = hash(i + 11.0); val h3 = hash(i + 21.0)
            val phase = (h1 + s.clock / LOOP * (1 + i % 2)) % 1.0
            val x = 30 + 333 * h2 + 14 * sin(tau * phase + h3 * 6)
            val y = 830 - 720 * phase
            val twinkle = 0.6 + 0.4 * sin(tau * 3 * s.clock / LOOP + h1 * 6)
            val a = sin(PI * phase).pow(1.5) * (0.16 + 0.30 * h3) * twinkle
            dot(c, x, y, 0.5 + 0.8 * h3, 0xFFBFEFFF.toInt(), a)
        }
        // Sparkles: they gather into the wings as it forms and burst out and up as it dissolves.
        val hide = 1 - s.engage
        if (hide > 0.01) {
            for (i in 0 until 140) {
                val fi = i.toDouble()
                val side = if (i % 2 == 0) 1.0 else -1.0
                val u = hash(fi * 3.1 + 2); val v = hash(fi * 7.7 + 5); val h4 = hash(fi + 41); val h5 = hash(fi + 77)
                val ang = -1.25 + 2.3 * u
                val rad = (0.15 + 0.85 * sqrt(v)) * (if (ang < 0) 1.0 else 0.72)
                val ox = 196.5 + 150 * side * cos(ang) * rad
                val oy = 360 + 150 * sin(ang) * rad * 0.9
                var dx = ox - 196.5; var dy = oy - 360 + 20
                val len = max(1.0, hypot(dx, dy)); dx /= len; dy /= len
                val turn = (h4 - 0.5) * 0.8
                val ux = dx * cos(turn) - dy * sin(turn); val uy = dx * sin(turn) + dy * cos(turn)
                val twinkle = 0.55 + 0.45 * sin(tau * 24 * s.clock / LOOP + h5 * 6.28)
                val r = 0.6 + 1.2 * h5
                val color = if (i % 3 == 0) 0xFFCFF8FF.toInt() else 0xFF7FE3FF.toInt()
                val gStart = 1.8 + 0.4 * h4
                val g = SmokeButterflySplashMotion.seg(s.story, gStart, gStart + 0.8)
                if (g > 0 && g < 1) {
                    val away = (1 - SmokeButterflySplashMotion.easeOut(g)) * 90 * (0.5 + h5)
                    dot(c, ox + ux * away, oy + uy * away, r, color, sin(PI * g) * 0.8 * twinkle * hide)
                }
                val bStart = 5.0 + 0.35 * h4
                val b = SmokeButterflySplashMotion.seg(s.story, bStart, bStart + 1.5 + 0.4 * h5)
                if (b > 0 && b < 1) {
                    val travel = SmokeButterflySplashMotion.easeOut(b)
                    val dist = travel * (70 + 120 * h5)
                    val x = ox + ux * dist + 12 * sin(tau * b + h4 * 6)
                    val y = oy + uy * dist - 60 * travel
                    val a = sin(PI * b).pow(0.8) * twinkle * hide
                    if (h5 > 0.8) dot(c, x, y, r * 3, color, a * 0.22)
                    dot(c, x, y, r, color, a)
                }
            }
        }
        // Type.
        val p = s.params
        if (p.title > 0.002) {
            val y = 584 + 8 * (1 - p.title)
            val glow = ink(0xFF3CCBFF.toInt(), 0.55 * p.title)
            glow.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
            text(c, glow, "Driftwing", y, 40f, light, 1.5f)
            text(c, ink(0xFFE6F7FF.toInt(), p.title), "Driftwing", y, 40f, light, 1.5f)
        }
        if (p.tagline > 0.002) {
            text(c, ink(0xFF86A9C8.toInt(), p.tagline), "Breathe out. Let the day dissolve.", 624 + 8 * (1 - p.tagline), 15f, regular, 0f)
        }
        text(c, ink(0xFF4D6A8C.toInt(), 1.0), "SLEEP · BREATHE · DRIFT", 792.0, 11f, medium, 3f)
    }

    /** Centred text whose vertical centre sits on y; kerning in points like SwiftUI's. */
    private fun text(c: NativeCanvas, p: Paint, text: String, y: Double, size: Float, face: Typeface, kerning: Float) {
        p.typeface = face
        p.textSize = size
        p.letterSpacing = kerning / size
        p.textAlign = Paint.Align.CENTER
        val m = p.fontMetrics
        c.drawText(text, 196.5f, (y - (m.ascent + m.descent) / 2).toFloat(), p)
    }
}
