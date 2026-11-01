package com.ngocdevv.nativecook.blocks.pickers.iconerascrubber

import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val ICON_ERA_RETURN_AFTER = 2.0
private const val ICON_ERA_SPRING_MASS = 4.0
private const val ICON_ERA_SPRING_ENERGY = 6e-9
private const val ICON_ERA_SPRING_EPSILON = 2.220446049250313e-16
private const val ICON_ERA_TAU = 6.283185307179586

private enum class IconEraAnimationKind { Idle, Timing, Spring }

/**
 * A scalar animation that follows Reanimated's duration spring equations. Reanimated's duration
 * is perceptual (the solver uses 1.5x duration), so using Compose's spring defaults here would
 * change the release/takeover feel substantially.
 */
private class IconEraAnimation(initial: Double) {
    var value: Double = initial
        private set
    var velocity: Double = 0.0
        private set
    private var kind = IconEraAnimationKind.Idle
    private var target = initial
    private var start = initial
    private var elapsed = 0.0
    private var duration = 0.0
    private var easing = IconEraTimingEasing.Cubic
    private var zeta = 1.0
    private var omega0 = 0.0
    private var omega1 = 0.0
    private var stiffness = 0.0
    private var initialEnergy = 0.0
    private var springDurationMs = 0.0
    private var springDampingRatio = 0.0

    val active: Boolean get() = kind != IconEraAnimationKind.Idle

    fun hold(at: Double) {
        value = at
        velocity = 0.0
        target = at
        kind = IconEraAnimationKind.Idle
    }

    fun cancel() {
        kind = IconEraAnimationKind.Idle
        velocity = 0.0
        target = value
    }

    fun timing(to: Double, seconds: Double, easing: IconEraTimingEasing) {
        if (abs(to - value) < 1e-9 || seconds <= 0.0) {
            hold(to)
            return
        }
        start = value
        target = to
        duration = seconds
        elapsed = 0.0
        this.easing = easing
        velocity = 0.0
        kind = IconEraAnimationKind.Timing
    }

    /** Start the same under-damped spring Reanimated builds from duration/dampingRatio. */
    fun spring(to: Double, durationMs: Double, dampingRatio: Double, configVelocity: Double) {
        // Reanimated's `isTriggeredTwice` keeps an identical active spring running from its
        // existing state. This matters for the lean spring, which receives the same velocity
        // target across several pointer samples while the finger is held still.
        if (
            kind == IconEraAnimationKind.Spring &&
            abs(to - target) < 1e-9 &&
            abs(durationMs - springDurationMs) < 1e-9 &&
            abs(dampingRatio - springDampingRatio) < 1e-9
        ) {
            return
        }

        val previousVelocity = if (kind == IconEraAnimationKind.Spring) velocity else 0.0
        if (abs(to - value) < 1e-9 && abs(previousVelocity + configVelocity) < 1e-9) {
            hold(to)
            return
        }

        var v0 = previousVelocity + configVelocity
        // Reanimated preserves inertia only when it points toward the new target.
        if ((to > value && v0 < 0.0) || (to < value && v0 > 0.0)) v0 = 0.0

        val x0 = value - to
        val k = iconEraSpringStiffness(x0, v0, dampingRatio, durationMs)
        target = to
        zeta = dampingRatio
        springDurationMs = durationMs
        springDampingRatio = dampingRatio
        stiffness = k
        omega0 = sqrt(k / ICON_ERA_SPRING_MASS)
        omega1 = if (zeta < 1.0) omega0 * sqrt(1.0 - zeta * zeta) else 0.0
        // Reanimated's initialEnergy uses config.velocity (before clipping/inertia addition).
        initialEnergy = iconEraEnergy(x0, configVelocity, k, ICON_ERA_SPRING_MASS)
        velocity = v0
        kind = IconEraAnimationKind.Spring
    }

    /** Advance by one frame. Returns true exactly when this animation settles this frame. */
    fun step(dtSeconds: Double): Boolean {
        when (kind) {
            IconEraAnimationKind.Idle -> return false
            IconEraAnimationKind.Timing -> {
                elapsed = min(duration, elapsed + max(0.0, dtSeconds))
                val p = (elapsed / duration).coerceIn(0.0, 1.0)
                value = start + (target - start) * iconEraTimingEase(p, easing)
                if (p >= 1.0) {
                    hold(target)
                    return true
                }
                return false
            }
            IconEraAnimationKind.Spring -> {
                val t = dtSeconds.coerceIn(0.0, 0.064)
                val x0 = value - target
                val v0 = velocity
                val positionAndVelocity = if (zeta < 1.0) {
                    val sine = sin(omega1 * t)
                    val cosine = cos(omega1 * t)
                    val envelope = exp(-zeta * omega0 * t)
                    val fragment = envelope * (
                        sine * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cosine
                    )
                    val nextValue = target + fragment
                    val nextVelocity = -zeta * omega0 * fragment + envelope * (
                        cosine * (v0 + zeta * omega0 * x0) - omega1 * x0 * sine
                    )
                    nextValue to nextVelocity
                } else {
                    val envelope = exp(-omega0 * t)
                    val fragment = envelope * (x0 + (v0 + omega0 * x0) * t)
                    val nextValue = target + fragment
                    val nextVelocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) +
                        envelope * (v0 + omega0 * x0)
                    nextValue to nextVelocity
                }
                value = positionAndVelocity.first
                velocity = positionAndVelocity.second
                val currentEnergy = iconEraEnergy(target - value, velocity, stiffness, ICON_ERA_SPRING_MASS)
                if (initialEnergy <= 0.0 || currentEnergy / initialEnergy <= ICON_ERA_SPRING_ENERGY) {
                    hold(target)
                    return true
                }
                return false
            }
        }
    }
}

private enum class IconEraTimingEasing { Default, Cubic }

private fun iconEraTimingEase(value: Double, easing: IconEraTimingEasing): Double {
    val x = value.coerceIn(0.0, 1.0)
    return when (easing) {
        IconEraTimingEasing.Default -> if (x < 0.5) 2.0 * x * x else 1.0 - (-2.0 * x + 2.0).let { it * it } / 2.0
        IconEraTimingEasing.Cubic -> iconEraCubicBezier(x, 0.77, 0.0, 0.175, 1.0)
    }
}

/** Solve the CSS cubic-bezier x curve by bisection, matching the RN easing's monotonic curve. */
private fun iconEraCubicBezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
    var low = 0.0
    var high = 1.0
    repeat(18) {
        val t = (low + high) * 0.5
        val bx = 3.0 * (1.0 - t) * (1.0 - t) * t * x1 +
            3.0 * (1.0 - t) * t * t * x2 + t * t * t
        if (bx < x) low = t else high = t
    }
    val t = (low + high) * 0.5
    return 3.0 * (1.0 - t) * (1.0 - t) * t * y1 +
        3.0 * (1.0 - t) * t * t * y2 + t * t * t
}

private fun iconEraEnergy(displacement: Double, velocity: Double, stiffness: Double, mass: Double): Double =
    0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity

private fun iconEraSpringStiffness(x0: Double, v0: Double, zeta: Double, durationMs: Double): Double {
    val settlingDuration = durationMs * 1.5 / 1000.0
    fun energyDifference(stiffness: Double): Double {
        val omega = sqrt(stiffness / ICON_ERA_SPRING_MASS) * zeta
        val x = (x0 + (v0 + x0 * omega) * settlingDuration) * exp(-omega * settlingDuration)
        val v = (x0 + (v0 + x0 * omega) * settlingDuration) * exp(-omega * settlingDuration) * -omega +
            (v0 + x0 * omega) * exp(-omega * settlingDuration)
        val initial = iconEraEnergy(x0, v0, stiffness, ICON_ERA_SPRING_MASS)
        return iconEraEnergy(x, v, stiffness, ICON_ERA_SPRING_MASS) / initial - ICON_ERA_SPRING_ENERGY
    }
    val precision = ICON_ERA_SPRING_ENERGY * 1e-3
    var minStiffness = ICON_ERA_SPRING_EPSILON
    var maxStiffness = 8e3
    val direction = if (energyDifference(maxStiffness) >= energyDifference(minStiffness)) 1.0 else -1.0
    var current = (minStiffness + maxStiffness) * 0.5
    repeat(100) {
        val diff = energyDifference(current)
        if (abs(diff) <= precision) return current
        if (diff * direction < 0.0) minStiffness = current else maxStiffness = current
        current = (minStiffness + maxStiffness) * 0.5
    }
    return current
}

/** Plain fields for the frame loop; only the small snapshot counters are Compose state. */
private class IconEraScrubberState(private val reduced: Boolean) {
    val frame = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
    val shown = mutableIntStateOf(IconEraScrubberMotion.FIRST.roundToInt())

    var clock = 0.0
        private set
    var time = 0.0
        private set
    private var phaseOffset = 0.0
    private var autoStart = 0.0
    private var lastTouch = -100.0
    private var resumeAsked = false
    private var stay = false
    private var mode = 1 // 0 auto, 1 hand-driven

    var year = IconEraScrubberMotion.FIRST
        private set
    var lean = 0.0
        private set
    var finger = 0.0
        private set
    var fingerX = 0.0
        private set
    var dragging = false
        private set
    var gliding = false
        private set
    private var dragOrigin = IconEraScrubberMotion.FIRST

    private val yearAnimation = IconEraAnimation(year)
    private val leanAnimation = IconEraAnimation(0.0)
    private val fingerAnimation = IconEraAnimation(0.0)

    fun configure(auto: Boolean) {
        mode = if (auto) 0 else 1
        autoStart = clock
        if (!auto) {
            yearAnimation.hold(year)
            gliding = false
        }
        wake.intValue += 1
    }

    /** Advance one frame. Returns true when the integer year changed while hand-driven. */
    fun step(dt: Double, auto: Boolean): Boolean {
        val boundedDt = dt.coerceIn(0.0, 0.05)
        clock += boundedDt
        var shouldTick = false

        if (mode == 0) {
            val t = positiveModulo(clock - autoStart, IconEraScrubberMotion.LOOP)
            val sample = IconEraScrubberMotion.frameAt(t)
            time = ICON_ERA_TAU * t / IconEraScrubberMotion.LOOP + phaseOffset
            year = sample.year
            lean = sample.lean
            finger = sample.finger
            fingerX = sample.fingerX
            yearAnimation.hold(year)
            leanAnimation.hold(lean)
            fingerAnimation.hold(finger)
        } else {
            if (!reduced) time += ICON_ERA_TAU * boundedDt / IconEraScrubberMotion.LOOP
            if (yearAnimation.active) {
                val finished = yearAnimation.step(boundedDt)
                year = yearAnimation.value
                if (finished) gliding = false
            }
            if (leanAnimation.active) {
                leanAnimation.step(boundedDt)
                lean = leanAnimation.value
            }
            if (fingerAnimation.active) {
                fingerAnimation.step(boundedDt)
                finger = fingerAnimation.value
            }
            if (dragging || gliding) lastTouch = clock
            if (auto && !stay && clock - lastTouch >= ICON_ERA_RETURN_AFTER) {
                if (!resumeAsked) {
                    resumeAsked = true
                    if (abs(year - IconEraScrubberMotion.FIRST) > 0.001) {
                        gliding = true
                        yearAnimation.timing(IconEraScrubberMotion.FIRST, 1.2, IconEraTimingEasing.Cubic)
                    }
                } else if (!gliding && abs(year - IconEraScrubberMotion.FIRST) < 0.002) {
                    resumeAsked = false
                    year = IconEraScrubberMotion.FIRST
                    yearAnimation.hold(year)
                    lean = 0.0
                    leanAnimation.hold(0.0)
                    phaseOffset = positiveModulo(time, ICON_ERA_TAU)
                    autoStart = clock
                    mode = 0
                }
            }
        }

        val nextShown = year.coerceIn(IconEraScrubberMotion.FIRST, IconEraScrubberMotion.LAST).roundToInt()
        if (nextShown != shown.intValue) {
            shown.intValue = nextShown
            shouldTick = mode == 1
        }
        frame.intValue += 1
        return shouldTick
    }

    fun takeOver() {
        lastTouch = clock
        resumeAsked = false
        if (mode != 0) return
        mode = 1
        fingerAnimation.timing(0.0, 0.15, IconEraTimingEasing.Default)
        leanAnimation.timing(0.0, 0.30, IconEraTimingEasing.Default)
    }

    fun beginRuler() {
        takeOver()
        yearAnimation.hold(year)
        gliding = false
        dragOrigin = year
        dragging = false
    }

    fun startDrag() {
        dragging = true
    }

    fun drag(translationX: Float, velocityX: Float, reducedMotion: Boolean) {
        if (!dragging) return
        lastTouch = clock
        var next = dragOrigin - translationX / IconEraScrubberTheme.Step
        if (next < IconEraScrubberMotion.FIRST) {
            next = IconEraScrubberMotion.FIRST - 0.6 * (1.0 - exp((next - IconEraScrubberMotion.FIRST) / 0.6))
        } else if (next > IconEraScrubberMotion.LAST) {
            next = IconEraScrubberMotion.LAST + 0.6 * (1.0 - exp(-(next - IconEraScrubberMotion.LAST) / 0.6))
        }
        year = next
        yearAnimation.hold(next)
        if (!reducedMotion) {
            val leanTarget = (-velocityX / IconEraScrubberTheme.Step / 30.0).coerceIn(-1.0, 1.0)
            leanAnimation.spring(leanTarget, 300.0, 0.8, 0.0)
        }
        wake.intValue += 1
    }

    fun finishRuler(velocityX: Float, reducedMotion: Boolean) {
        lastTouch = clock
        if (!reducedMotion) leanAnimation.spring(0.0, 500.0, 0.7, 0.0) else leanAnimation.hold(0.0)
        lean = leanAnimation.value
        if (!dragging) return
        dragging = false
        val velocity = -velocityX.toDouble() / IconEraScrubberTheme.Step
        val target = (year + 0.3 * velocity).roundToInt().toDouble().coerceIn(
            IconEraScrubberMotion.FIRST,
            IconEraScrubberMotion.LAST,
        )
        glideTo(target, velocity, reducedMotion)
        wake.intValue += 1
    }

    fun cancelRuler(reducedMotion: Boolean) {
        dragging = false
        if (!reducedMotion) leanAnimation.spring(0.0, 500.0, 0.7, 0.0) else leanAnimation.hold(0.0)
        lean = leanAnimation.value
        wake.intValue += 1
    }

    fun tapYear(x: Float, width: Float, reducedMotion: Boolean) {
        takeOver()
        val target = (year + (x - width / 2f) / IconEraScrubberTheme.Step).roundToInt().toDouble().coerceIn(
            IconEraScrubberMotion.FIRST,
            IconEraScrubberMotion.LAST,
        )
        glideTo(target, 0.0, reducedMotion)
        wake.intValue += 1
    }

    fun stepBy(direction: Int, reducedMotion: Boolean) {
        takeOver()
        val target = (year.roundToInt() + direction).toDouble().coerceIn(
            IconEraScrubberMotion.FIRST,
            IconEraScrubberMotion.LAST,
        )
        glideTo(target, 0.0, reducedMotion)
        wake.intValue += 1
    }

    fun setYearFromAccessibility(target: Double, reducedMotion: Boolean) {
        takeOver()
        glideTo(target.coerceIn(IconEraScrubberMotion.FIRST, IconEraScrubberMotion.LAST), 0.0, reducedMotion)
        wake.intValue += 1
    }

    fun setStay(value: Boolean) {
        stay = value
        if (value) mode = 1
    }

    private fun glideTo(target: Double, velocity: Double, reducedMotion: Boolean) {
        if (reducedMotion) {
            yearAnimation.timing(target, 0.30, IconEraTimingEasing.Cubic)
        } else {
            yearAnimation.spring(target, 700.0, 0.85, velocity)
        }
        gliding = yearAnimation.active
    }

    private fun positiveModulo(value: Double, divisor: Double): Double {
        val r = value % divisor
        return if (r < 0.0) r + divisor else r
    }

    fun needsFrames(auto: Boolean): Boolean =
        auto || (!reduced && mode == 1) || dragging || gliding ||
            yearAnimation.active || leanAnimation.active || fingerAnimation.active

    fun lightPhase(): Double = time
}

private data class IconEraScrubberFonts(val ruler: Typeface)

private fun iconEraScrubberFonts(context: android.content.Context): IconEraScrubberFonts = IconEraScrubberFonts(
    Typeface.createFromAsset(context.assets, IconEraScrubberTheme.MediumFont),
)

/**
 * The Postbox icon history screen. The shader and all overlays use the reference's dp geometry;
 * the native canvas is scaled once so RuntimeShader and the API 26 fallback share coordinates.
 */
@Composable
fun IconEraScrubber(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val owner = LocalLifecycleOwner.current
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val top = WindowInsets.safeDrawing.getTop(density) / density.density
    val bottom = WindowInsets.safeDrawing.getBottom(density) / density.density
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(IconEraScrubberTheme.Background),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val layout = remember(width, height, top, bottom) {
            IconEraScrubberTheme.makeLayout(width, height, top, bottom)
        }
        val state = remember(reduced) { IconEraScrubberState(reduced) }
        val fonts = remember(context) {
            FontFamily(
                Font(IconEraScrubberTheme.RegularFont, context.assets, FontWeight.Normal),
                Font(IconEraScrubberTheme.MediumFont, context.assets, FontWeight.Medium),
                Font(IconEraScrubberTheme.SemiBoldFont, context.assets, FontWeight.SemiBold),
            )
        }
        val canvasFonts = remember(context) { iconEraScrubberFonts(context) }
        val fallback = remember { IconEraScrubberFallback() }
        val rulerPaint = remember(canvasFonts) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = IconEraScrubberTheme.Tick.toArgb()
                strokeWidth = 1.5f
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
        }
        val majorRulerPaint = remember(canvasFonts) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = IconEraScrubberTheme.Mute.toArgb()
                strokeWidth = 1.5f
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
        }
        val rulerLabelPaint = remember(canvasFonts) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = IconEraScrubberTheme.Mute.toArgb()
                textSize = 12f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = canvasFonts.ruler
            }
        }
        val needlePaint = remember(canvasFonts) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = IconEraScrubberTheme.Accent.toArgb()
                strokeWidth = 2f
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
        }
        val fingerPaint = remember(canvasFonts) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x59FFFFFF
                style = android.graphics.Paint.Style.FILL
            }
        }
        val fingerStrokePaint = remember(canvasFonts) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt()
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.5f
            }
        }
        val shader = remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { RuntimeShader(ICON_ERA_SCRUBBER_AGSL) }.getOrNull()
            } else {
                null
            }
        }
        val shaderPaint = remember(shader) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        }

        LaunchedEffect(state, auto) {
            state.configure(auto)
        }

        LaunchedEffect(state, auto, active) {
            if (!active) return@LaunchedEffect
            var last = -1L
            while (true) {
                val keepRunning = withFrameNanos { nanos ->
                    val dt = if (last < 0L) 0.0 else min((nanos - last) / 1e9, 0.05)
                    last = nanos
                    if (state.step(dt, auto)) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                    state.needsFrames(auto)
                }
                if (!keepRunning) {
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                    last = -1L
                }
            }
        }

        val rootTouch = Modifier.pointerInput(state, auto, reduced) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.takeOver()
                state.wake.intValue += 1
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.none { it.pressed }) break
                }
                state.wake.intValue += 1
            }
        }

        Box(rootTouch.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                state.frame.intValue
                val era = IconEraScrubberMotion.eraOf(state.year)
                val phase = state.lightPhase()
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.scale(density.density, density.density)
                    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        shader.setFloatUniform("uObj", layout.iconX, layout.iconY, IconEraScrubberTheme.IconHalf, density.density)
                        shader.setFloatUniform("uEra", era.toFloat(), (0.14 + 0.35 * state.lean).toFloat(), (0.1 + 0.05 * abs(state.lean)).toFloat(), phase.toFloat())
                        shader.setFloatUniform("uPage", layout.width, layout.height, 1f, 0f)
                        canvas.nativeCanvas.drawRect(0f, 0f, layout.width, layout.height, shaderPaint)
                    } else {
                        fallback.draw(canvas.nativeCanvas, layout, state.year, state.lean, phase)
                    }
                    canvas.nativeCanvas.restore()
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = layout.titleTop.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    IconEraScrubberTheme.AppSub,
                    color = IconEraScrubberTheme.Mute,
                    fontFamily = fonts,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.4.sp,
                )
                Text(
                    IconEraScrubberTheme.AppName,
                    color = IconEraScrubberTheme.Ink,
                    fontFamily = fonts,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = layout.yearTop.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    state.shown.intValue.toString(),
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    color = IconEraScrubberTheme.Ink,
                    fontFamily = fonts,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 56.sp,
                    lineHeight = 64.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(top = 4.dp),
                ) {
                    IconEraEraWords(state, fonts, 0)
                    IconEraEraWords(state, fonts, 1)
                    IconEraEraWords(state, fonts, 2)
                    IconEraEraWords(state, fonts, 3)
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (layout.rulerTop - 24f).dp)
                    .height(96.dp)
                    .semantics {
                        contentDescription = "${IconEraScrubberTheme.AppName} icon by year"
                        stateDescription = "${state.shown.intValue}, ${IconEraScrubberTheme.Eras[IconEraScrubberMotion.eraOf(state.shown.intValue.toDouble()).roundToInt().coerceIn(0, 3)].name}"
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            state.shown.intValue.toFloat(),
                            IconEraScrubberMotion.FIRST.toFloat()..IconEraScrubberMotion.LAST.toFloat(),
                            steps = 17,
                        )
                        setProgress { requested ->
                            state.setYearFromAccessibility(requested.toDouble().roundToInt().toDouble(), reduced)
                            true
                        }
                        customActions = listOf(
                            CustomAccessibilityAction("Increment") {
                                state.stepBy(1, reduced)
                                true
                            },
                            CustomAccessibilityAction("Decrement") {
                                state.stepBy(-1, reduced)
                                true
                            },
                        )
                    }
                    .pointerInput(state, reduced, layout.width) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var activeDrag = false
                            var ended = false
                            var liftAt = down.uptimeMillis
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                liftAt = change.uptimeMillis
                                if (!change.pressed) {
                                    ended = true
                                    break
                                }
                                val distance = (change.position - down.position).getDistance()
                                if (!activeDrag && distance > 2f * density.density) {
                                    activeDrag = true
                                    // Pan.onBegin runs only after its 2 dp threshold in RN; a
                                    // short tap therefore leaves an in-flight glide untouched.
                                    state.beginRuler()
                                    state.startDrag()
                                }
                                if (activeDrag) {
                                    change.consume()
                                    val velocity = tracker.calculateVelocity().x / density.density
                                    state.drag((change.position.x - down.position.x) / density.density, velocity, reduced)
                                }
                            }
                            if (activeDrag) {
                                // Pan.finalize settles on cancellation as well as on pointer up.
                                state.finishRuler(tracker.calculateVelocity().x / density.density, reduced)
                            } else if (ended && liftAt - down.uptimeMillis <= 500L) {
                                // Pan.finalize also releases the lean spring when the
                                // exclusive tap wins, before Tap.onEnd starts the year glide.
                                state.cancelRuler(reduced)
                                state.tapYear(down.position.x / density.density, layout.width, reduced)
                            } else {
                                state.cancelRuler(reduced)
                            }
                            state.wake.intValue += 1
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    state.frame.intValue
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.save()
                        native.scale(density.density, density.density)
                        native.save()
                        native.translate(
                            (layout.width / 2f - ((state.year - IconEraScrubberMotion.FIRST) * IconEraScrubberTheme.Step)).toFloat(),
                            24f,
                        )
                        // unlabelled ticks past both ends, fading out, so the ruler runs off the screen at 2008 and 2026 too
                        val ghostAlpha = rulerPaint.alpha
                        for (away in 1..8) {
                            rulerPaint.alpha = (ghostAlpha * 0.55 * (1.0 - away / 9.0)).roundToInt()
                            for (year in intArrayOf(IconEraScrubberMotion.FIRST.roundToInt() - away, IconEraScrubberMotion.LAST.roundToInt() + away)) {
                                val x = ((year - IconEraScrubberMotion.FIRST) * IconEraScrubberTheme.Step).toFloat()
                                native.drawLine(x, 0f, x, 14f, rulerPaint)
                            }
                        }
                        rulerPaint.alpha = ghostAlpha
                        for (year in IconEraScrubberMotion.FIRST.roundToInt()..IconEraScrubberMotion.LAST.roundToInt()) {
                            val x = ((year - IconEraScrubberMotion.FIRST) * IconEraScrubberTheme.Step).toFloat()
                            val isMajor = year % 2 == 0
                            val h = if (isMajor) 24f else 14f
                            native.drawLine(x, 0f, x, h, if (isMajor) majorRulerPaint else rulerPaint)
                            if (isMajor) native.drawText("'${year.toString().takeLast(2)}", x, 24f + 8f + 12f, rulerLabelPaint)
                        }
                        native.restore()
                        native.drawLine(layout.width / 2f, 16f, layout.width / 2f, 56f, needlePaint)
                        val fingerAlpha = (0.85 * state.finger).coerceIn(0.0, 1.0)
                        if (fingerAlpha > 0.001) {
                            fingerPaint.alpha = (fingerAlpha * 0.35 * 255.0).roundToInt()
                            fingerStrokePaint.alpha = (fingerAlpha * 0.8 * 255.0).roundToInt()
                            val x = layout.width * 0.68f - state.fingerX.toFloat() * IconEraScrubberTheme.Step
                            native.drawCircle(x, 54f, 22f, fingerPaint)
                            native.drawCircle(x, 54f, 22f, fingerStrokePaint)
                        }
                        native.restore()
                    }
                }
            }
        }
    }
}

@Composable
private fun IconEraEraWords(state: IconEraScrubberState, fonts: FontFamily, index: Int) {
    val era = IconEraScrubberTheme.Eras[index]
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                state.frame.intValue
                val distance = IconEraScrubberMotion.eraOf(state.year) - index
                // the nearer era's words show, swapping in a quick fade at the change's middle
                val away = abs(distance)
                val opacity = ((0.53 - away) / 0.06).coerceIn(0.0, 1.0)
                alpha = (opacity * opacity).toFloat()
                translationY = (-14.0 * distance.coerceIn(-1.0, 1.0) * density).toFloat()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            era.name,
            color = IconEraScrubberTheme.Ink,
            fontFamily = fonts,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        )
        Text(
            era.line,
            color = IconEraScrubberTheme.Mute,
            fontFamily = fonts,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

