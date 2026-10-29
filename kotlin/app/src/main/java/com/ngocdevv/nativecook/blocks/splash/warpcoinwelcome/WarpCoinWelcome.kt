package com.ngocdevv.nativecook.blocks.splash.warpcoinwelcome

import android.app.Activity
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val LOOP = 7.0
private const val IDLE = 2.0

/**
 * The welcome screen of Arcwell, an invented bank. A slow star field drifts out of a vanishing
 * point behind a large brushed-silver coin; the warp spins up, the coin dives into it, comes back
 * out of the warp toward the viewer, swoops close and lands with a settle while the streaks slow
 * back down to stars. 340 seeded particles in a tunnel are drawn as lines from their previous to
 * their current projected position, so speed is length; the coin is a thick disc with a reeded rim,
 * a concentric brushed face whose highlight follows the light and an embossed mark.
 *
 * Drag up to warp faster (down drifts back); the coin tilts with the drag. Tap the coin to spin it
 * once. 2 s after the last touch the loop starts again from rest. Reduced motion: the rest pose, still.
 */
@Composable
fun WarpCoinWelcome(autoPlay: Boolean = false) {
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
    val touch = remember { WarpCoinWelcomeTouch() }
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
                if (release != null && clock.doubleValue > release + IDLE + 1.2) {
                    awake.value = false
                    break
                }
            }
        }
    }

    val painter = remember { WarpCoinWelcomePainter() }
    Box(Modifier.fillMaxSize().background(Color(0xFF05060B))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Arcwell. Banking at the speed of light." }
                .pointerInput(reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val k = size.width / 393f
                        val height = size.height / k.toDouble()
                        touch.begin(down.position.x / k.toDouble(), down.position.y / k.toDouble(), clock.doubleValue)
                        awake.value = true
                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (touch.move(change.position.x / k.toDouble(), change.position.y / k.toDouble())) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                change.consume()
                            } while (event.changes.any { it.pressed })
                        } finally {
                            val tapped = touch.end(clock.doubleValue, WarpCoinWelcomeMotion.vanishY(height) + 162)
                            view.performHapticFeedback(
                                if (tapped) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.CLOCK_TICK,
                            )
                        }
                    }
                },
        ) {
            val state = if (reduced) {
                WarpCoinWelcomeMotion.restFrame()
            } else {
                WarpCoinWelcomeMotion.frame(clock.doubleValue, touch, autoPlay)
            }
            val canvas = drawContext.canvas.nativeCanvas
            val k = size.width / 393f
            canvas.save()
            canvas.scale(k, k)
            painter.draw(canvas, size.height / k.toDouble(), state)
            canvas.restore()
        }
        WarpCoinWelcomeChrome()
    }
}

private fun animationsOff(context: android.content.Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** The static product screen over the scene: page indicator, header, headline and the two buttons. */
@Composable
private fun WarpCoinWelcomeChrome() {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(5) { i ->
                Box(
                    Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = if (i == 0) 0.9f else 0.22f)),
                )
            }
        }
        Row(
            Modifier.padding(start = 16.dp, top = 10.dp).height(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val logo = remember { WarpCoinWelcomePainter() }
            Canvas(Modifier.size(18.dp)) {
                logo.drawLogo(drawContext.canvas.nativeCanvas, size.width / 2, size.height / 2, size.width)
            }
            Text("Welcome to Arcwell", color = Color.White, fontSize = 15.sp)
        }
        Column(Modifier.padding(start = 24.dp, top = 14.dp)) {
            listOf("BANKING AT", "THE SPEED", "OF LIGHT").forEach { line ->
                Box(Modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
                    Text(line, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 36.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChromeButton("Log in", Color(0xFF1E2127), Color.White, Modifier.weight(1f))
            ChromeButton("Sign up", Color(0xFFF4F5F7), Color(0xFF0A0B0E), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChromeButton(title: String, fill: Color, ink: Color, modifier: Modifier) {
    Box(
        modifier.height(52.dp).clip(CircleShape).background(fill).clickable {},
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Touch state read by the frame; plain fields, so writing them never recomposes. */
internal class WarpCoinWelcomeTouch {
    var touching = false
    var start = 0.0
    var engageFrom = 0.0
    var release: Double? = null
    /** Loop time = clock - base (wrapped). */
    var base = 0.0
    /** Loop time held while the finger drives the scene. */
    var heldStory = 0.0
    var dragX = 0.0
    var dragY = 0.0
    var releaseX = 0.0
    var releaseY = 0.0
    var spinStart = -10.0
    /** Travel bookkeeping: the last frame's travel, and the offset added to the loop's travel after a resume. */
    var lastTravel = 0.0
    var lastClock = -1.0
    var lastStory = 0.0
    var travelOffset = 0.0
    var resumeApplied = true
    var lastSpeed = WarpCoinWelcomeMotion.REST_SPEED
    private var anchorX = 0.0
    private var anchorY = 0.0
    private var downX = 0.0
    private var downY = 0.0
    private var moved = 0.0
    private var full = false

    /** The travel the last frame's speed adds up to now, so a resume carries on without a jump. */
    fun lastSpeedStep(clock: Double): Double =
        if (lastClock < 0) 0.0 else lastSpeed * min(0.1, max(0.0, clock - lastClock))

    /** 0 → 1 blend from the loop's pose to the finger's control pose. */
    fun engage(clock: Double): Double {
        if (touching) return engageFrom + (1 - engageFrom) * WarpCoinWelcomeMotion.easeOut((clock - start) / 0.45)
        val r = release ?: return 0.0
        val resume = r + IDLE
        if (clock < resume) return engageFrom + (1 - engageFrom) * WarpCoinWelcomeMotion.easeOut((clock - start) / 0.45)
        return 1 - WarpCoinWelcomeMotion.easeOut((clock - resume) / 0.45)
    }

    /** The drag the control pose follows: live while touching, then a critically damped return to zero. */
    fun dragX(clock: Double): Double = if (touching) dragX else releaseX * dragDecay(clock)
    fun dragY(clock: Double): Double = if (touching) dragY else releaseY * dragDecay(clock)
    private fun dragDecay(clock: Double): Double {
        val r = release ?: return 0.0
        val age = max(0.0, clock - r)
        val tau = 0.16
        return (1 + age / tau) * exp(-age / tau)
    }

    fun held(clock: Double): Boolean {
        val r = release
        return touching || (r != null && clock < r + IDLE)
    }

    fun begin(x: Double, y: Double, clock: Double) {
        val current = engage(clock)
        if (!held(clock)) heldStory = lastStory
        engageFrom = current
        touching = true
        start = clock
        release = null
        resumeApplied = false
        anchorX = x
        anchorY = y
        downX = x
        downY = y
        moved = 0.0
        dragX = 0.0
        dragY = 0.0
        full = false
    }

    /** Returns true when the drag passes full warp, for a haptic. */
    fun move(x: Double, y: Double): Boolean {
        dragX = x - anchorX
        dragY = y - anchorY
        moved = max(moved, hypot(x - downX, y - downY))
        val c = WarpCoinWelcomeMotion.control(dragY)
        if (c > 0.9 && !full) {
            full = true
            return true
        }
        if (c < 0.7) full = false
        return false
    }

    /** Returns true when the touch was a tap on the coin (it spins). */
    fun end(clock: Double, coinY: Double): Boolean {
        if (!touching) return false
        touching = false
        release = clock
        releaseX = dragX
        releaseY = dragY
        val tap = moved < 10 && clock - start < 0.35 && abs(downX - 196.5) < 150 && abs(downY - coinY) < 95
        if (tap && clock - spinStart > 1.0) {
            spinStart = clock
            return true
        }
        return false
    }
}

/** ln of the depth (1 at rest), world offset from the vanishing point at depth 1, angles in radians. */
internal data class WarpCoinWelcomePose(
    val lnZ: Double = 0.0,
    val x: Double = 0.0,
    val y: Double = 162.0,
    val pitch: Double = 1.13,
    val roll: Double = 0.0,
    val twist: Double = 0.0,
    val spin: Double = 0.0,
    val hop: Double = 0.0,
) {
    fun mixed(o: WarpCoinWelcomePose, k: Double): WarpCoinWelcomePose {
        fun m(a: Double, b: Double) = a + (b - a) * k
        return WarpCoinWelcomePose(
            m(lnZ, o.lnZ), m(x, o.x), m(y, o.y), m(pitch, o.pitch), m(roll, o.roll),
            m(twist, o.twist), m(spin, o.spin), m(hop, o.hop),
        )
    }
}

internal class WarpCoinWelcomeFrame(
    /** Continuous loop phase for twinkle and hover (seconds, 0 ..< LOOP). */
    val phase: Double = 0.0,
    /** Distance travelled through the tunnel (depth units; only its fraction matters). */
    val travel: Double = 0.0,
    /** Signed speed in depth units per second. */
    val speed: Double = WarpCoinWelcomeMotion.REST_SPEED,
    val pose: WarpCoinWelcomePose = WarpCoinWelcomePose(),
    val glint: Double = 0.0,
    val vanishShift: Double = 0.0,
    val hover: Double = 1.0,
) {
    /** 0 at rest speed, 1 at full warp. */
    val warp: Double
        get() = min(1.0, max(0.0, (abs(speed) - WarpCoinWelcomeMotion.REST_SPEED) / WarpCoinWelcomeMotion.BOOST))
}

/** The loop's timeline as pure functions of the loop time. */
internal object WarpCoinWelcomeMotion {
    /** One tunnel length per loop at rest. */
    const val REST_SPEED = 1.0 / 7.0
    /** Extra speed at full warp: 9 extra tunnel lengths over the loop, spread by the warp curve (∫w = 3.45 s). */
    const val BOOST = 9.0 / 3.45

    fun vanishY(height: Double) = 0.45 * height

    fun clamp01(x: Double) = min(max(x, 0.0), 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeOut(x: Double): Double { val u = clamp01(x); return 1 - (1 - u).pow(3) }
    fun easeIn(x: Double): Double { val u = clamp01(x); return u * u * u }
    fun easeInOut(x: Double): Double {
        val u = clamp01(x)
        return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
    }
    fun smooth(x: Double): Double { val u = clamp01(x); return u * u * (3 - 2 * u) }
    fun smoother(x: Double): Double { val u = clamp01(x); return u * u * u * (u * (u * 6 - 15) + 10) }
    fun wrap(t: Double): Double {
        val r = t % LOOP
        return if (r < 0) r + LOOP else r
    }

    /** Warp level: smoothstep up over 0.8–2.0 s, full until 3.9 s, smoothstep down over 3.9–5.8 s. */
    fun warp(t: Double): Double = when {
        t < 0.8 || t >= 5.8 -> 0.0
        t < 2.0 -> smooth((t - 0.8) / 1.2)
        t < 3.9 -> 1.0
        else -> 1 - smooth((t - 3.9) / 1.9)
    }

    /** ∫₀ᵗ warp: the closed form of the smoothstep's integral is x³ − x⁴/2. */
    fun warpArea(t: Double): Double {
        fun f(x: Double) = x * x * x - x * x * x * x / 2
        return when {
            t < 0.8 -> 0.0
            t < 2.0 -> 1.2 * f((t - 0.8) / 1.2)
            t < 3.9 -> 0.6 + (t - 2.0)
            t < 5.8 -> { val x = (t - 3.9) / 1.9; 2.5 + 1.9 * (x - f(x)) }
            else -> 3.45
        }
    }

    /** Travel over one loop is exactly 10 tunnel lengths, so every particle is back where it started. */
    fun travel(t: Double) = REST_SPEED * t + BOOST * warpArea(t)
    fun speed(t: Double) = REST_SPEED + BOOST * warp(t)

    /**
     * Step response of a spring (damping ratio 0.62, period 1.0 s) from 1 to 0 with zero start velocity,
     * forced to exactly 0 after 1.8 s so the loop closes.
     */
    fun settle(tau: Double): Double {
        if (tau <= 0) return 1.0
        val zeta = 0.62
        val wn = 2 * PI / 1.0
        val wd = wn * sqrt(1 - zeta * zeta)
        val decay = zeta * wn
        val v = exp(-decay * tau) * (cos(wd * tau) + decay / wd * sin(wd * tau))
        return v * (1 - smooth((tau - 1.4) / 0.4))
    }

    fun pose(t: Double): WarpCoinWelcomePose {
        val far = ln(9.0)
        val dip = ln(0.93)
        val close = ln(0.62)
        return when {
            t < 2.65 -> {
                val a = easeOut(seg(t, 0.85, 1.15))
                val f = easeIn(seg(t, 1.15, 2.25))
                val h = easeInOut(seg(t, 2.25, 2.65))
                WarpCoinWelcomePose(
                    lnZ = dip * a + (far - dip) * f,
                    pitch = 1.13 - 0.12 * a + 0.10 * f,
                    roll = 0.25 * f,
                    twist = -0.9 * f + 1.8 * h,
                )
            }
            t < 3.6 -> {
                val r = smoother(seg(t, 2.65, 3.6))
                WarpCoinWelcomePose(
                    lnZ = far + (close - far) * r,
                    x = -50 * r,
                    y = 162 - 52 * r,
                    pitch = 1.11 + 0.15 * r,
                    roll = 0.25 - 0.53 * r,
                    twist = 0.9 - 0.55 * r,
                )
            }
            else -> {
                val s = settle(t - 3.6)
                WarpCoinWelcomePose(
                    lnZ = close * s,
                    x = -50 * s,
                    y = 162 - 52 * s,
                    pitch = 1.13 + 0.13 * s,
                    roll = -0.28 * s,
                    twist = 0.35 * s,
                )
            }
        }
    }

    fun glint(t: Double): Double {
        if (t <= 2.05 || t >= 2.95) return 0.0
        val s = sin(PI * (t - 2.05) / 0.9)
        return s * s
    }

    /** Warp control from the vertical drag: up is faster (1 = full warp), down drifts back (to −0.5). */
    fun control(dragY: Double): Double {
        val c = -dragY / 240
        if (c > 1) {
            val over = c - 1
            return 1 + over / (1 + over * 2) * 0.5
        }
        return max(-0.5, c)
    }

    fun controlPose(dx: Double, dy: Double): WarpCoinWelcomePose {
        val c = control(dy)
        return WarpCoinWelcomePose(
            lnZ = ln(1 + 0.25 * max(c, 0.0)),
            x = dx * 0.15,
            pitch = 1.13 - 0.22 * c,
            roll = min(max(dx / 300, -1.0), 1.0) * 0.35,
        )
    }

    fun controlSpeed(dy: Double): Double {
        val c = control(dy)
        return if (c >= 0) REST_SPEED + BOOST * c else REST_SPEED + 0.8 * c
    }

    fun restFrame() = WarpCoinWelcomeFrame(hover = 0.0)

    /** One frame from the clock (seconds since the screen appeared) and the touch state. */
    fun frame(clock: Double, touch: WarpCoinWelcomeTouch, autoPlay: Boolean): WarpCoinWelcomeFrame {
        val engage = touch.engage(clock)
        val dx = touch.dragX(clock)
        val dy = touch.dragY(clock)
        val held = touch.held(clock)

        val story: Double
        if (held) {
            story = touch.heldStory
        } else {
            val r = touch.release
            if (r != null && !touch.resumeApplied) {
                // Resume: the loop restarts from rest, and its travel continues from where the finger left it.
                touch.base = r + IDLE
                touch.resumeApplied = true
                touch.travelOffset = touch.lastTravel + touch.lastSpeedStep(clock) - travel(wrap(clock - touch.base))
            }
            story = if (autoPlay) wrap(clock - touch.base) else 0.0
        }

        var pose = pose(story)
        var speed = speed(story)
        if (engage > 0) {
            pose = pose.mixed(controlPose(dx, dy), engage)
            speed += (controlSpeed(dy) - speed) * engage
        }
        val u = (clock - touch.spinStart) / 1.0
        if (u > 0 && u < 1) {
            pose = pose.copy(spin = pose.spin + 2 * PI * easeInOut(u), hop = pose.hop + 14 * sin(PI * u))
        }

        val travelNow = if (held) {
            val dt = if (touch.lastClock < 0) 0.0 else min(0.1, max(0.0, clock - touch.lastClock))
            touch.lastTravel + speed * dt
        } else {
            travel(story) + touch.travelOffset
        }
        touch.lastTravel = travelNow
        touch.lastClock = clock
        touch.lastStory = story
        touch.lastSpeed = speed

        return WarpCoinWelcomeFrame(
            phase = wrap(clock),
            travel = travelNow,
            speed = speed,
            pose = pose,
            glint = glint(story) * (1 - engage),
            vanishShift = -dx * 0.12 * engage,
        )
    }
}

/** The tunnel, the coin and the glint, drawn with android.graphics in a 393-wide design space. */
internal class WarpCoinWelcomePainter {
    private companion object {
        const val COUNT = 340
        const val FOCAL = 393.0
        const val RADIUS = 134.0
        const val THICKNESS = 30.0
        const val SEGMENTS = 120

        fun hash(x: Double): Double {
            val v = sin(x * 12.9898) * 43758.5453
            return v - floor(v)
        }

        fun normalize(x: Double, y: Double, z: Double): DoubleArray {
            val l = sqrt(x * x + y * y + z * z)
            return doubleArrayOf(x / l, y / l, z / l)
        }

        /** The streak's hot core: its colour 35 % of the way to white. */
        fun lighten(hex: Int): Int {
            fun ch(shift: Int): Int {
                val v = ((hex shr shift) and 255).toDouble()
                return (v + (255 - v) * 0.35).roundToInt() shl shift
            }
            return ch(16) or ch(8) or ch(0)
        }

        fun argb(hex: Int, alpha: Double): Int {
            val a = (min(1.0, max(0.0, alpha)) * 255).roundToInt()
            return (a shl 24) or (hex and 0xFFFFFF)
        }

        fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

        /** Direction to the light (upper left, in front) and the half vector with the view direction. */
        val LIGHT = normalize(-0.35, -0.85, -0.40)
        val HALF = normalize(LIGHT[0], LIGHT[1], LIGHT[2] - 1)

        val FACE_COLORS = intArrayOf(
            0xFFF4F5F7.toInt(), 0xFFC9CCD2.toInt(), 0xFF9599A1.toInt(), 0xFF8A8E96.toInt(), 0xFFA3A7AE.toInt(), 0xFFD2D5DA.toInt(),
            0xFFF4F5F7.toInt(), 0xFFC9CCD2.toInt(), 0xFF9599A1.toInt(), 0xFF8A8E96.toInt(), 0xFFA3A7AE.toInt(), 0xFFD2D5DA.toInt(),
            0xFFF4F5F7.toInt(),
        )
        val FACE_STOPS = floatArrayOf(0f, 0.08f, 0.18f, 0.25f, 0.32f, 0.42f, 0.5f, 0.58f, 0.68f, 0.75f, 0.82f, 0.92f, 1f)
    }

    private class Particle(val r: Double, val phase: Double, val size: Double, val hex: Int, val core: Int, val cos: Double, val sin: Double)

    private val particles = Array(COUNT) { i ->
        val fi = i.toDouble()
        val h1 = hash(fi * 1.37 + 0.11)
        val h2 = hash(fi * 2.71 + 3.3)
        val h3 = hash(fi * 0.913 + 7.7)
        val h4 = hash(fi * 5.17 + 1.9)
        val h5 = hash(fi * 3.33 + 9.1)
        val hex = when {
            h4 < 0.40 -> 0x4A66FF
            h4 < 0.54 -> 0x8A5CFF
            h4 < 0.78 -> 0xE8EEFF
            h4 < 0.87 -> 0x4FD8FF
            h4 < 0.95 -> 0xB4FF5A
            else -> 0xFF5AD8
        }
        val angle = 2 * PI * h1
        Particle(0.05 + 0.70 * h2, h3, h5, hex, lighten(hex), cos(angle), sin(angle))
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val markArch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.2f
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.ROUND
    }
    private val markBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.12f
        strokeCap = Paint.Cap.BUTT
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0x000000, 0.5) }
    private val path = Path()
    private val matrix = Matrix()
    private val values = FloatArray(9)
    private val front = IntArray(COUNT)
    private val sweepMatrix = Matrix()
    private val faceSweep = SweepGradient(0f, 0f, FACE_COLORS, FACE_STOPS)
    private val markSweep = SweepGradient(0f, 0f, FACE_COLORS, FACE_STOPS)

    /** The Arcwell mark in unit coordinates: an arch with a bar, like a rounded A. */
    private val archPath = Path().apply {
        moveTo(-0.40f, 0.48f)
        lineTo(-0.40f, 0.03f)
        for (j in 1..32) {
            val a = PI + PI * j / 32
            lineTo((0.40 * cos(a)).toFloat(), (0.03 + 0.40 * sin(a)).toFloat())
        }
        lineTo(0.40f, 0.48f)
    }
    private val barPath = Path().apply {
        moveTo(-0.40f, 0.19f)
        lineTo(0.40f, 0.19f)
    }

    fun draw(c: NativeCanvas, height: Double, s: WarpCoinWelcomeFrame) {
        val vpX = 196.5 + s.vanishShift
        val vpY = WarpCoinWelcomeMotion.vanishY(height)
        val warp = s.warp
        val w = 393f
        val h = height.toFloat()

        // Backdrop: near black with a deep blue light at the end of the tunnel that brightens with speed.
        fill.shader = null
        fill.color = 0xFF05060B.toInt()
        c.drawRect(0f, 0f, w, h, fill)
        radial(c, vpX, vpY, 260.0, 0x1B2A8F, 0.10 + 0.14 * warp, w, h)
        radial(c, vpX, vpY, 140.0, 0x2B3FD0, 0.03 + 0.05 * warp, w, h)

        // Coin placement (weak perspective) and its depth in the tunnel.
        val coin = CoinGeometry(s.pose, vpX, vpY, s.hover, s.phase)
        val coinDepth = 0.115 * coin.z
        val splits = coinDepth > 0.2

        var fronts = 0
        val trail = min(0.85, max(0.006, abs(s.speed) * 0.24))
        val forward = s.speed >= 0
        val shift = s.travel - floor(s.travel)
        val twinkleT = 2 * PI * s.phase / LOOP
        for (i in 0 until COUNT) {
            val p = particles[i]
            var u = p.phase + shift
            u -= floor(u)
            val z = 1.04 - u
            if (splits && z < coinDepth) {
                front[fronts++] = i
                continue
            }
            streak(c, p, i, z, trail, forward, vpX, vpY, warp, twinkleT)
        }

        // Scrims keep the headline and the buttons legible over the streaks.
        fill.color = 0xFF000000.toInt()
        fill.shader = LinearGradient(0f, 0f, 0f, 300f, argb(0x05060B, 0.78), argb(0x05060B, 0.0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, 300f, fill)
        fill.shader = LinearGradient(0f, h - 230f, 0f, h, argb(0x05060B, 0.0), argb(0x05060B, 0.7), Shader.TileMode.CLAMP)
        c.drawRect(0f, h - 230f, w, h, fill)
        fill.shader = null

        drawCoin(c, coin, warp)

        for (j in 0 until fronts) {
            val i = front[j]
            val p = particles[i]
            var u = p.phase + shift
            u -= floor(u)
            streak(c, p, i, 1.04 - u, trail, forward, vpX, vpY, warp, twinkleT)
        }

        if (s.glint > 0.01) {
            val g = s.glint
            val cx = coin.cx.toFloat()
            val cy = coin.cy.toFloat()
            glow.color = 0xFF000000.toInt()
            glow.shader = RadialGradient(cx, cy, 16f, argb(0xFFFFFF, 0.8 * g), argb(0x9FB2FF, 0.0), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, 16f, glow)
            glow.shader = null
            glow.color = argb(0xFFFFFF, 0.9 * g)
            c.drawOval(cx - 34f * g.toFloat(), cy - 1.2f, cx + 34f * g.toFloat(), cy + 1.2f, glow)
            c.drawOval(cx - 1.2f, cy - 22f * g.toFloat(), cx + 1.2f, cy + 22f * g.toFloat(), glow)
        }
    }

    private fun radial(c: NativeCanvas, x: Double, y: Double, r: Double, hex: Int, alpha: Double, w: Float, h: Float) {
        fill.color = 0xFF000000.toInt()
        fill.shader = RadialGradient(x.toFloat(), y.toFloat(), r.toFloat(), argb(hex, alpha), argb(hex, 0.0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
    }

    private fun streak(
        c: NativeCanvas, p: Particle, i: Int, z: Double, trail: Double, forward: Boolean,
        vpX: Double, vpY: Double, warp: Double, twinkleT: Double,
    ) {
        val tailZ = if (forward) min(1.04, z + trail) else max(0.04, z - trail)
        val reach = FOCAL * p.r
        val rho = reach / z
        val rhoTail = reach / tailZ
        if (min(rho, rhoTail) > 560) return
        val fade = WarpCoinWelcomeMotion.smooth((1.04 - z) / 0.24)
        val twinkle = 0.78 + 0.22 * sin(twinkleT * (3 + i % 4) + 6.28 * p.size)
        val alpha = min(1.0, (0.35 + 0.65 * p.size) * fade * (0.8 + 0.45 * warp) * twinkle)
        if (alpha < 0.01) return
        var width = max(0.9, (0.5 + 1.4 * p.size) * (0.4 + 0.6 * (1.04 - z)))
        if (p.size > 0.94) width *= 2.4
        val hx = (vpX + p.cos * rho).toFloat()
        val hy = (vpY + p.sin * rho).toFloat()
        val tx = (vpX + p.cos * rhoTail).toFloat()
        val ty = (vpY + p.sin * rhoTail).toFloat()
        val length = abs(rho - rhoTail)
        if (length > 3) {
            // A soft halo and a core, both fading toward the tail (the vanishing point side).
            line.strokeWidth = (width * 2.6 + 1.2).toFloat()
            line.color = 0xFF000000.toInt()
            line.shader = LinearGradient(tx, ty, hx, hy, argb(p.hex, 0.0), argb(p.hex, alpha * 0.16), Shader.TileMode.CLAMP)
            c.drawLine(tx, ty, hx, hy, line)
            line.strokeWidth = width.toFloat()
            line.shader = LinearGradient(tx, ty, hx, hy, argb(p.hex, 0.0), argb(p.core, alpha), Shader.TileMode.CLAMP)
            c.drawLine(tx, ty, hx, hy, line)
            line.shader = null
        } else {
            if (p.size > 0.75) {
                glow.shader = null
                glow.color = argb(p.hex, alpha * 0.2)
                c.drawCircle(hx, hy, (width * 2).toFloat(), glow)
            }
            line.shader = null
            line.strokeWidth = width.toFloat()
            line.color = argb(p.core, alpha)
            c.drawLine(tx, ty, hx, hy, line)
        }
    }

    private class CoinGeometry(pose: WarpCoinWelcomePose, vpX: Double, vpY: Double, hover: Double, phase: Double) {
        val z = exp(pose.lnZ)
        val scale = 1 / z
        val cx: Double
        val cy: Double
        /** Coin axes in camera space: e1 (face x), e2 (face y, down), e3 (face normal, toward the camera at rest). */
        val e1: DoubleArray
        val e2: DoubleArray
        val e3: DoubleArray
        val opacity = 1 - 0.75 * WarpCoinWelcomeMotion.smooth((z - 3) / 6)

        init {
            val w = 2 * PI * phase / LOOP
            val y = pose.y + hover * 3 * sin(w)
            val roll = pose.roll + hover * 0.03 * sin(w + 1.3)
            val pitch = pose.pitch + hover * 0.02 * sin(2 * w)
            cx = vpX + pose.x * scale
            cy = vpY + y * scale - pose.hop * scale
            fun rotate(vx: Double, vy: Double, vz: Double): DoubleArray {
                var x = vx
                var yy = vy
                var zz = vz
                // Twist about the face normal (the z axis before the tilt).
                var nx = x * cos(pose.twist) - yy * sin(pose.twist)
                yy = x * sin(pose.twist) + yy * cos(pose.twist)
                x = nx
                // Spin about the face's vertical axis.
                nx = x * cos(pose.spin) + zz * sin(pose.spin)
                zz = -x * sin(pose.spin) + zz * cos(pose.spin)
                x = nx
                // Pitch: the top edge tips away from the camera.
                val ny = yy * cos(pitch) + zz * sin(pitch)
                zz = -yy * sin(pitch) + zz * cos(pitch)
                yy = ny
                // Roll about the view axis.
                nx = x * cos(roll) - yy * sin(roll)
                yy = x * sin(roll) + yy * cos(roll)
                x = nx
                return doubleArrayOf(x, yy, zz)
            }
            e1 = rotate(1.0, 0.0, 0.0)
            e2 = rotate(0.0, 1.0, 0.0)
            e3 = rotate(0.0, 0.0, -1.0)
        }
    }

    private fun setAffine(m: Matrix, a: Double, b: Double, cc: Double, d: Double, tx: Double, ty: Double) {
        values[0] = a.toFloat(); values[1] = cc.toFloat(); values[2] = tx.toFloat()
        values[3] = b.toFloat(); values[4] = d.toFloat(); values[5] = ty.toFloat()
        values[6] = 0f; values[7] = 0f; values[8] = 1f
        m.setValues(values)
    }

    private fun drawCoin(c: NativeCanvas, coin: CoinGeometry, warp: Double) {
        val k = RADIUS * coin.scale
        val n = THICKNESS / 2 * coin.scale
        val isFront = coin.e3[2] < 0
        val normal = if (isFront) coin.e3 else doubleArrayOf(-coin.e3[0], -coin.e3[1], -coin.e3[2])
        val layer = if (coin.opacity < 0.999) {
            c.saveLayerAlpha(null, (coin.opacity * 255).roundToInt())
        } else {
            c.save()
        }

        // Soft ambient shadow under the coin, cut out of the streaks.
        setAffine(
            matrix, coin.e1[0] * k * 1.1, coin.e1[1] * k * 1.1, coin.e2[0] * k * 1.1, coin.e2[1] * k * 1.1,
            coin.cx - coin.e3[0] * n * 2, coin.cy - coin.e3[1] * n * 2 + 10 * coin.scale,
        )
        path.reset()
        path.addOval(-1f, -1f, 1f, 1f, Path.Direction.CW)
        path.transform(matrix)
        shadow.maskFilter = BlurMaskFilter(max(0.5, 22 * coin.scale).toFloat(), BlurMaskFilter.Blur.NORMAL)
        c.drawPath(path, shadow)

        // Reeded rim: one quad per facet on the side facing the camera, lit by the light and its specular.
        val ux = coin.e1[0] * k
        val uy = coin.e1[1] * k
        val vx = coin.e2[0] * k
        val vy = coin.e2[1] * k
        val nx = coin.e3[0] * n
        val ny = coin.e3[1] * n
        val step = 2 * PI / SEGMENTS
        val radial = DoubleArray(3)
        fill.shader = null
        for (i in 0 until SEGMENTS) {
            val a0 = i * step
            val mid = a0 + step / 2
            radial[0] = cos(mid) * coin.e1[0] + sin(mid) * coin.e2[0]
            radial[1] = cos(mid) * coin.e1[1] + sin(mid) * coin.e2[1]
            radial[2] = cos(mid) * coin.e1[2] + sin(mid) * coin.e2[2]
            if (radial[2] > 0.02) continue
            val lambert = max(0.0, dot(radial, LIGHT))
            val spec = max(0.0, dot(radial, HALF)).pow(24)
            val ridge = if (i % 2 == 0) 1.0 else 0.74
            val facing = max(0.0, -radial[2])
            val g = min(1.0, (0.40 + 0.42 * lambert + 0.22 * facing) * ridge * 0.92 + 0.55 * spec)
            val cool = 0.14 * warp * (1 - lambert)
            val r = (min(1.0, g * 0.98 + cool * 0.3) * 255).roundToInt()
            val gg = (min(1.0, g * 0.99 + cool * 0.45) * 255).roundToInt()
            val b = (min(1.0, g * 1.02 + cool) * 255).roundToInt()
            fill.color = (0xFF shl 24) or (r shl 16) or (gg shl 8) or b
            val b0 = a0 - step * 0.3
            val b1 = a0 + step * 1.3
            val p0x = coin.cx + cos(b0) * ux + sin(b0) * vx
            val p0y = coin.cy + cos(b0) * uy + sin(b0) * vy
            val p1x = coin.cx + cos(b1) * ux + sin(b1) * vx
            val p1y = coin.cy + cos(b1) * uy + sin(b1) * vy
            path.reset()
            path.moveTo((p0x + nx).toFloat(), (p0y + ny).toFloat())
            path.lineTo((p1x + nx).toFloat(), (p1y + ny).toFloat())
            path.lineTo((p1x - nx).toFloat(), (p1y - ny).toFloat())
            path.lineTo((p0x - nx).toFloat(), (p0y - ny).toFloat())
            path.close()
            c.drawPath(path, fill)
        }

        // The face, drawn in its own unit-circle coordinates.
        val side = if (isFront) 1.0 else -1.0
        val e1 = doubleArrayOf(side * coin.e1[0], side * coin.e1[1], side * coin.e1[2])
        setAffine(matrix, e1[0] * k, e1[1] * k, coin.e2[0] * k, coin.e2[1] * k, coin.cx + side * nx, coin.cy + side * ny)
        // Light and half vector projected on the face: the brushed highlight runs along the half vector.
        val phi = atan2(dot(HALF, coin.e2), dot(HALF, e1))
        var lx = dot(LIGHT, e1)
        var ly = dot(LIGHT, coin.e2)
        val ll = max(0.0001, sqrt(lx * lx + ly * ly))
        lx /= ll
        ly /= ll
        val lambert = max(0.0, dot(normal, LIGHT))
        c.save()
        c.concat(matrix)
        drawFace(c, phi, lx, ly, lambert, warp)
        c.restore()
        c.restoreToCount(layer)
    }

    private fun drawFace(c: NativeCanvas, phi: Double, lx: Double, ly: Double, lambert: Double, warp: Double) {
        val deg = Math.toDegrees(phi).toFloat()
        sweepMatrix.setRotate(deg)
        faceSweep.setLocalMatrix(sweepMatrix)
        fill.color = 0xFF000000.toInt()
        fill.shader = faceSweep
        c.drawCircle(0f, 0f, 1f, fill)
        fill.shader = null
        // Lathe lines.
        stroke.shader = null
        stroke.strokeWidth = 0.012f
        for (j in 0 until 10) {
            val r = 0.12 + 0.085 * j
            stroke.color = if (j % 2 == 0) argb(0xFFFFFF, 0.06) else argb(0x000000, 0.05)
            c.drawCircle(0f, 0f, r.toFloat(), stroke)
        }
        // Raised lip and the groove inside it.
        stroke.strokeWidth = 0.1f
        stroke.color = 0xFF000000.toInt()
        stroke.shader = LinearGradient(
            (lx * 0.95).toFloat(), (ly * 0.95).toFloat(), (-lx * 0.95).toFloat(), (-ly * 0.95).toFloat(),
            intArrayOf(argb(0xFFFFFF, 0.5), argb(0xFFFFFF, 0.0), argb(0x000000, 0.35)), null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(0f, 0f, 0.95f, stroke)
        stroke.shader = null
        stroke.strokeWidth = 0.014f
        stroke.color = argb(0x000000, 0.3)
        c.drawCircle(0f, 0f, 0.888f, stroke)
        stroke.strokeWidth = 0.01f
        stroke.color = argb(0xFFFFFF, 0.35)
        c.drawCircle(0f, 0f, 0.872f, stroke)

        // Embossed mark: a dark copy away from the light, a light copy toward it, then the mark itself.
        val d = 0.026
        c.save()
        c.translate((-lx * d).toFloat(), (-ly * d).toFloat())
        strokeMark(c, argb(0x2A2D33, 0.6), null)
        c.restore()
        c.save()
        c.translate((lx * d).toFloat(), (ly * d).toFloat())
        strokeMark(c, argb(0xFFFFFF, 0.55), null)
        c.restore()
        sweepMatrix.setRotate(deg + 90f)
        markSweep.setLocalMatrix(sweepMatrix)
        strokeMark(c, 0xFF000000.toInt(), markSweep)

        // Shade by how much the face turns from the light, and pick up the warp's blue on the far edge.
        fill.color = argb(0x000000, 0.5 * (1 - lambert))
        c.drawCircle(0f, 0f, 1f, fill)
        if (warp > 0.01) {
            fill.color = 0xFF000000.toInt()
            fill.shader = RadialGradient(0f, -1f, 1.3f, argb(0x6E86FF, 0.30 * warp), argb(0x6E86FF, 0.0), Shader.TileMode.CLAMP)
            c.drawCircle(0f, 0f, 1f, fill)
            fill.shader = null
        }
        stroke.strokeWidth = 0.012f
        stroke.color = argb(0xFFFFFF, 0.5)
        c.drawCircle(0f, 0f, 0.995f, stroke)
    }

    private fun strokeMark(c: NativeCanvas, color: Int, shader: Shader?) {
        markArch.shader = shader
        markBar.shader = shader
        markArch.color = color
        markBar.color = color
        c.drawPath(archPath, markArch)
        c.drawPath(barPath, markBar)
        markArch.shader = null
        markBar.shader = null
    }

    fun drawLogo(c: NativeCanvas, x: Float, y: Float, size: Float) {
        c.save()
        c.translate(x, y)
        c.scale(size, size)
        strokeMark(c, 0xFFFFFFFF.toInt(), null)
        c.restore()
    }
}
