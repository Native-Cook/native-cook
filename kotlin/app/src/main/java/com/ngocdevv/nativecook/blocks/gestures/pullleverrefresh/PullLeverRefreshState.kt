package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class PullLeverRefreshStage {
    REST,
    PUMPING,
    FULL,
    BREWING,
    DONE,
}

internal data class PullLeverRefreshReadout(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
)

internal class PullLeverRefreshState(
    parentScope: CoroutineScope,
    private val autoPlay: Boolean,
    private val reduceMotion: Boolean,
    private val onRefresh: suspend () -> Unit,
    private val haptic: (PullLeverRefreshHaptic) -> Unit,
) {
    private val owner = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)

    var angle by mutableFloatStateOf(PullLeverRefreshTokens.Pump.Rest)
        private set
    var bar by mutableFloatStateOf(0f)
        private set
    var brew by mutableFloatStateOf(0f)
        private set
    var cupOffset by mutableFloatStateOf(0f)
        private set
    var cupAlpha by mutableFloatStateOf(1f)
        private set
    var puff by mutableFloatStateOf(0f)
        private set
    var ventPuff by mutableFloatStateOf(0f)
        private set
    var clock by mutableFloatStateOf(0f)
        private set
    var strokes by mutableIntStateOf(0)
        private set
    var stage by mutableStateOf(PullLeverRefreshStage.REST)
        private set
    var readout by mutableStateOf(PullLeverRefreshReadout("ESPRESSO", "Pull a shot", "Pump the lever to 9 bar"))
        private set

    private var layout = PullLeverRefreshMachineLayout(0f, 0f, 1f)
    private var lastFrame = 0L
    private var demoEpoch = 0L
    private var quietUntil = 0L
    private var brewEpoch = 0L
    private var dragging = false
    private var angleDriven = false
    private var lastPointerAngle = 0f
    private var dragAngle = PullLeverRefreshTokens.Pump.Rest
    private var rearmed = true
    private var angleVelocity = 0f
    private var barVelocity = 0f
    private var targetAngle = PullLeverRefreshTokens.Pump.Rest
    private var targetBar = 0f
    private var manualBrew = false
    private var brewStartAngle = PullLeverRefreshTokens.Pump.Bottom

    init {
        scope.launch { frameLoop() }
    }

    fun updateLayout(value: PullLeverRefreshMachineLayout) {
        layout = value
    }

    fun beginTouch(x: Float, y: Float) {
        quietUntil = SystemClock.uptimeMillis() + PullLeverRefreshTokens.Timing.Resume
        demoEpoch = 0L
        if (stage == PullLeverRefreshStage.BREWING) return
        if (stage == PullLeverRefreshStage.DONE) reset(animated = !reduceMotion)
        dragging = true
        angleVelocity = 0f
        val distance = pullLeverRefreshLeverDistance(x, y, layout)
        angleDriven = distance >= PullLeverRefreshTokens.Machine.FingerAt * layout.scale * PullLeverRefreshTokens.Pump.GrabFrom
        lastPointerAngle = pullLeverRefreshAngleAt(x, y, layout)
        dragAngle = angle
    }

    fun dragTo(x: Float, y: Float, deltaY: Float) {
        if (!dragging || stage == PullLeverRefreshStage.BREWING) return
        val raw = if (angleDriven) {
            val pointerAngle = pullLeverRefreshAngleAt(x, y, layout)
            val delta = shortestAngle(pointerAngle - lastPointerAngle)
            lastPointerAngle = pointerAngle
            dragAngle + delta
        } else {
            dragAngle + deltaY * (180f / (PI.toFloat() * PullLeverRefreshTokens.Machine.FingerAt))
        }
        dragAngle = raw
        val p = PullLeverRefreshTokens.Pump
        angle = rubberBand(raw, p.Rest - p.OverUp, p.Bottom + p.OverDown, p.OverSoftness)
        if (rearmed && angle >= p.StrokeAt) {
            strokes = min(strokes + 1, p.Strokes)
            targetBar = strokes * p.BarPerStroke
            ventPuff = 1f
            stage = if (strokes == p.Strokes) PullLeverRefreshStage.FULL else PullLeverRefreshStage.PUMPING
            rearmed = false
            haptic(if (strokes == p.Strokes) PullLeverRefreshHaptic.FULL else PullLeverRefreshHaptic.STROKE)
        } else if (!rearmed && angle <= p.RearmAt) {
            rearmed = true
        }
    }

    fun endTouch(velocityY: Float, cancelled: Boolean) {
        if (!dragging) return
        dragging = false
        quietUntil = SystemClock.uptimeMillis() + PullLeverRefreshTokens.Timing.Resume
        if (!cancelled && strokes >= PullLeverRefreshTokens.Pump.Strokes) {
            startBrew()
            return
        }
        angleVelocity = velocityY * (180f / (PI.toFloat() * PullLeverRefreshTokens.Machine.FingerAt))
        targetAngle = PullLeverRefreshTokens.Pump.Rest
    }

    fun activate() {
        quietUntil = SystemClock.uptimeMillis() + PullLeverRefreshTokens.Timing.Resume
        if (stage == PullLeverRefreshStage.BREWING) return
        if (stage == PullLeverRefreshStage.DONE) {
            reset(animated = !reduceMotion)
            return
        }
        strokes = PullLeverRefreshTokens.Pump.Strokes
        bar = PullLeverRefreshTokens.Brew.Bar
        targetBar = bar
        haptic(PullLeverRefreshHaptic.FULL)
        startBrew()
    }

    fun dispose() {
        scope.cancel()
    }

    private suspend fun frameLoop() {
        while (coroutineContext.isActive) {
            withFrameNanos { frame ->
                if (lastFrame == 0L) {
                    lastFrame = frame
                    demoEpoch = frame
                }
                val dt = min((frame - lastFrame) / 1_000_000_000f, 1f / 20f)
                lastFrame = frame
                clock += dt
                val demoAllowed = autoPlay && !reduceMotion && !dragging &&
                    SystemClock.uptimeMillis() >= quietUntil && !manualBrew
                if (demoAllowed) {
                    if (demoEpoch == 0L) demoEpoch = frame
                    val t = ((frame - demoEpoch) / 1_000_000f % LOOP) / 1000f
                    applyDemo(t)
                } else if (!dragging) {
                    updateManual(frame, dt)
                }
                updateSprings(dt)
                updateReadout()
            }
        }
    }

    private fun applyDemo(t: Float) {
        manualBrew = false
        val p = PullLeverRefreshTokens.Pump
        if (t < 4.01f) {
            brew = 0f
            cupOffset = 0f
            cupAlpha = 1f
            puff = 0f
            var finished = 0
            var scriptedAngle = p.Rest
            var scriptedBar = 0f
            var activePuff = 0f
            PullLeverRefreshTokens.Demo.Strokes.forEachIndexed { index, stroke ->
                val to = p.Bottom + 1.5f
                val crossShare = ((p.StrokeAt - p.Rest) / (to - p.Rest)).coerceIn(0f, 1f)
                val crossTime = stroke[0] + (stroke[1] - stroke[0]) * inverseEaseOut(crossShare)
                if (t >= crossTime) {
                    finished = index + 1
                    scriptedBar += p.BarPerStroke * springProgress(
                        seconds = t - crossTime,
                        damping = p.NeedleDamping,
                        stiffness = p.NeedleStiffness,
                    )
                    activePuff = max(activePuff, 1f - (t - crossTime) / (PullLeverRefreshTokens.Timing.Puff / 1000f))
                }
                when {
                    t < stroke[0] -> Unit
                    t < stroke[1] -> {
                        scriptedAngle = lerp(p.Rest, to, easeOut((t - stroke[0]) / (stroke[1] - stroke[0])))
                    }
                    t < stroke[2] -> {
                        scriptedAngle = to
                    }
                    else -> scriptedAngle = springValue(t - stroke[2], to, p.Rest)
                }
            }
            angle = scriptedAngle
            ventPuff = activePuff.coerceIn(0f, 1f)
            strokes = finished
            bar = scriptedBar
            targetBar = finished * p.BarPerStroke
            stage = when {
                finished == 0 -> PullLeverRefreshStage.REST
                finished < p.Strokes -> PullLeverRefreshStage.PUMPING
                else -> PullLeverRefreshStage.FULL
            }
            return
        }
        if (t < 8.61f) {
            val progress = ((t - 4.01f) / 4.6f).coerceIn(0f, 1f)
            stage = PullLeverRefreshStage.BREWING
            strokes = 3
            brew = progress
            angle = lerp(p.Bottom + 1.5f, p.Rest, leverEase(progress))
            bar = lerp(PullLeverRefreshTokens.Brew.Bar, PullLeverRefreshTokens.Brew.EndBar, pressureEase(progress))
            cupOffset = 0f
            cupAlpha = 1f
            puff = 0f
            ventPuff = 0f
            return
        }
        stage = PullLeverRefreshStage.DONE
        strokes = 3
        brew = 1f
        angle = p.Rest
        bar = lerp(
            PullLeverRefreshTokens.Brew.EndBar,
            0f,
            easeInOut(((t - 8.61f) / (PullLeverRefreshTokens.Timing.Settle / 1000f)).coerceIn(0f, 1f)),
        )
        val swap = PullLeverRefreshTokens.Demo.Swap
        if (t < swap) {
            cupOffset = 0f
            cupAlpha = 1f
            puff = min((t - 8.61f) / 0.7f, 1f)
        } else {
            val local = t - swap
            when {
                local < 0.38f -> {
                    val q = easeInOut(local / 0.38f)
                    cupOffset = q * PullLeverRefreshTokens.Timing.CupTravel
                    cupAlpha = 1f - q
                }
                local < 0.84f -> {
                    val q = easeOut((local - 0.38f) / 0.46f)
                    cupOffset = (q - 1f) * PullLeverRefreshTokens.Timing.CupTravel
                    cupAlpha = q
                    brew = 1f - q
                }
                else -> resetImmediate()
            }
        }
    }

    private fun updateManual(frame: Long, dt: Float) {
        if (stage != PullLeverRefreshStage.BREWING) return
        val elapsed = (frame - brewEpoch) / 1_000_000f
        val duration = if (reduceMotion) PullLeverRefreshTokens.Timing.Reduced else PullLeverRefreshTokens.Brew.Pour
        val progress = (elapsed / duration).coerceIn(0f, 1f)
        brew = progress
        angle = lerp(brewStartAngle, PullLeverRefreshTokens.Pump.Rest, leverEase(progress))
        bar = lerp(PullLeverRefreshTokens.Brew.Bar, PullLeverRefreshTokens.Brew.EndBar, pressureEase(progress))
        ventPuff = max(ventPuff - dt / (PullLeverRefreshTokens.Timing.Puff / 1000f), 0f)
        if (progress >= 1f) {
            stage = PullLeverRefreshStage.DONE
            manualBrew = false
            puff = 1f
            targetBar = 0f
            scope.launch { onRefresh() }
            haptic(PullLeverRefreshHaptic.READY)
        }
    }

    private fun updateSprings(dt: Float) {
        if (dragging || (autoPlay && !reduceMotion && SystemClock.uptimeMillis() >= quietUntil)) return
        val p = PullLeverRefreshTokens.Pump
        val angleAcceleration = (targetAngle - angle) * p.HomeStiffness - angleVelocity * p.HomeDamping
        angleVelocity += angleAcceleration * dt
        angle += angleVelocity * dt
        val barAcceleration = (targetBar - bar) * p.NeedleStiffness - barVelocity * p.NeedleDamping
        barVelocity += barAcceleration * dt
        bar += barVelocity * dt
    }

    private fun startBrew() {
        manualBrew = true
        brewStartAngle = angle
        stage = PullLeverRefreshStage.BREWING
        brew = 0f
        bar = PullLeverRefreshTokens.Brew.Bar
        targetBar = bar
        targetAngle = PullLeverRefreshTokens.Pump.Rest
        brewEpoch = lastFrame
    }

    private fun reset(animated: Boolean) {
        manualBrew = false
        stage = PullLeverRefreshStage.REST
        strokes = 0
        brew = 0f
        puff = 0f
        ventPuff = 0f
        targetBar = 0f
        targetAngle = PullLeverRefreshTokens.Pump.Rest
        if (!animated) resetImmediate()
    }

    private fun resetImmediate() {
        manualBrew = false
        stage = PullLeverRefreshStage.REST
        strokes = 0
        brew = 0f
        puff = 0f
        ventPuff = 0f
        bar = 0f
        targetBar = 0f
        angle = PullLeverRefreshTokens.Pump.Rest
        targetAngle = angle
        cupOffset = 0f
        cupAlpha = 1f
        rearmed = true
    }

    private fun updateReadout() {
        val next = when (stage) {
            PullLeverRefreshStage.REST -> PullLeverRefreshReadout("ESPRESSO", "Pull a shot", "Pump the lever to 9 bar")
            PullLeverRefreshStage.PUMPING -> PullLeverRefreshReadout("ESPRESSO", "Building pressure", "${bar.toInt().coerceAtLeast(0)} bar")
            PullLeverRefreshStage.FULL -> PullLeverRefreshReadout("ESPRESSO", "Building pressure", "Release to brew")
            PullLeverRefreshStage.BREWING -> PullLeverRefreshReadout(
                "ESPRESSO",
                "Brewing",
                "${max(1, (brew * PullLeverRefreshTokens.Brew.Seconds).toInt())} s",
            )
            PullLeverRefreshStage.DONE -> PullLeverRefreshReadout("ESPRESSO", "Espresso ready", "9 bar · 27 s · 36 g")
        }
        if (next != readout) readout = next
    }
}

internal enum class PullLeverRefreshHaptic { STROKE, FULL, READY }

@Composable
internal fun rememberPullLeverRefreshState(
    autoPlay: Boolean,
    reduceMotion: Boolean,
    onRefresh: (suspend () -> Unit)?,
): PullLeverRefreshState {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val state = remember(autoPlay, reduceMotion, onRefresh, view) {
        PullLeverRefreshState(scope, autoPlay, reduceMotion, onRefresh ?: {}, view::pullLeverRefreshHaptic)
    }
    DisposableEffect(state) { onDispose(state::dispose) }
    return state
}

private fun View.pullLeverRefreshHaptic(value: PullLeverRefreshHaptic) {
    val feedback = when (value) {
        PullLeverRefreshHaptic.STROKE -> HapticFeedbackConstants.CLOCK_TICK
        PullLeverRefreshHaptic.FULL -> HapticFeedbackConstants.CONTEXT_CLICK
        PullLeverRefreshHaptic.READY -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(feedback)
}

private fun shortestAngle(value: Float): Float = ((value + 180f) % 360f + 360f) % 360f - 180f

private fun rubberBand(value: Float, minimum: Float, maximum: Float, softness: Float): Float = when {
    value < minimum -> minimum - softness * (minimum - value) / (softness + minimum - value)
    value > maximum -> maximum + softness * (value - maximum) / (softness + value - maximum)
    else -> value
}

private fun lerp(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress.coerceIn(0f, 1f)

private fun easeOut(value: Float): Float {
    return cubicBezier(value, .23f, 1f, .32f, 1f)
}

private fun easeInOut(value: Float): Float {
    return cubicBezier(value, .77f, 0f, .175f, 1f)
}

private fun leverEase(value: Float): Float = cubicBezier(value, .25f, .1f, .25f, 1f)

private fun pressureEase(value: Float): Float = cubicBezier(value, .45f, 0f, .6f, 1f)

private fun inverseEaseOut(value: Float): Float {
    var low = 0f
    var high = 1f
    repeat(10) {
        val middle = (low + high) * .5f
        if (easeOut(middle) < value) low = middle else high = middle
    }
    return (low + high) * .5f
}

private fun cubicBezier(value: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val target = value.coerceIn(0f, 1f)
    var low = 0f
    var high = 1f
    repeat(10) {
        val t = (low + high) * .5f
        val x = cubic(t, x1, x2)
        if (x < target) low = t else high = t
    }
    return cubic((low + high) * .5f, y1, y2)
}

private fun cubic(t: Float, a: Float, b: Float): Float {
    val one = 1f - t
    return 3f * one * one * t * a + 3f * one * t * t * b + t * t * t
}

private fun springValue(seconds: Float, from: Float, target: Float): Float {
    val response = 1f - springProgress(
        seconds = seconds,
        damping = PullLeverRefreshTokens.Pump.HomeDamping,
        stiffness = PullLeverRefreshTokens.Pump.HomeStiffness,
    )
    return target + (from - target) * response
}

private fun springProgress(seconds: Float, damping: Float, stiffness: Float): Float {
    val omega = sqrt(stiffness)
    val ratio = damping / (2f * omega)
    val damped = omega * sqrt(max(1f - ratio * ratio, .001f))
    val envelope = kotlin.math.exp(-ratio * omega * seconds)
    val response = envelope * (cos(damped * seconds) + ratio * omega / damped * sin(damped * seconds))
    return 1f - response
}
