package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The lever's single-owner state machine and animation clock.
 *
 * Drag updates are incremental pointer deltas. The state accumulates them so a renderer does not need
 * to retain gesture-local state. All public values are Compose snapshot state and are safe to read in
 * draw or graphics-layer lambdas.
 */
internal class PullLeverRefreshState(
    parentScope: CoroutineScope,
    private val autoPlay: Boolean,
    private val reduceMotion: Boolean,
    private val onRefresh: suspend () -> Unit,
    private val performHaptic: (PullLeverRefreshHaptic) -> Unit,
) {
    private enum class Phase {
        IDLE,
        PULLING,
        ARMED,
        REFRESHING,
        DONE,
    }

    private val ownerJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + ownerJob)
    private val animations = mutableMapOf<String, Job>()

    var angle by mutableFloatStateOf(PullLeverRefreshMotion.Pull.restAngle)
        private set

    var sway by mutableFloatStateOf(0f)
        private set

    var idle by mutableFloatStateOf(0f)
        private set

    var shake by mutableFloatStateOf(0f)
        private set

    var fill by mutableFloatStateOf(0f)
        private set

    var lit by mutableFloatStateOf(1f)
        private set

    var bloom by mutableFloatStateOf(1f)
        private set

    var glow by mutableFloatStateOf(0f)
        private set

    var breath by mutableFloatStateOf(0f)
        private set

    var pulse by mutableFloatStateOf(-1f)
        private set

    var ripple by mutableFloatStateOf(0f)
        private set

    var knobLit by mutableFloatStateOf(0f)
        private set

    var label by mutableStateOf(PullLeverRefreshLabel.NONE)
        private set

    val turn: Float
        get() = angle + sway + idle + shake

    val arcLit: Float
        get() {
            val leverDots = PullLeverRefreshTheme.Lever.dots.toFloat()
            val arcDots = PullLeverRefreshMotion.Light.arcDots.toFloat()
            val covered = floor(
                max(fill, 0f) * leverDots + PullLeverRefreshTokens.Rendering.quantizationEpsilon,
            )
            return round(covered * arcDots / leverDots)
        }

    private var phase = Phase.IDLE
    private var ticks = 0
    private var grabFrom = 0f
    private var dragDrop = 0f
    private var stretchTicks = 0
    private var refreshId = 0

    private var clock = 0f
    private var idleAmplitude = 1f
    private var demoTime = 0f
    private var demoPaused = !autoPlay
    private var realDown = false
    private var quiet = 0f
    private var scriptPulling = false
    private var scriptedRefresh = false
    private var disposed = false
    private var labelJob: Job? = null

    init {
        scope.launch {
            runClock()
        }
    }

    fun onTouchDown() {
        interruptDemo()
    }

    fun onTouchUp() {
        releaseTouch()
    }

    fun onDragStart() {
        dragDrop = 0f
        pullBegin(silent = false)
    }

    fun onDrag(deltaY: Float) {
        dragDrop += deltaY
        pullMove(drop = dragDrop, silent = false)
    }

    fun onDragEnd(
        velocityY: Float = 0f,
        cancelled: Boolean = false,
    ) {
        releaseTouch()
        pullEnd(
            silent = false,
            cancelled = cancelled,
            velocity = velocityY,
        )
    }

    fun refreshNow() {
        interruptDemo()
        releaseTouch()
        if (phase != Phase.IDLE) {
            return
        }
        fill = 1f
        startRefresh(
            silent = false,
            velocity = 0f,
        )
    }

    fun dispose() {
        disposed = true
        ownerJob.cancel()
        animations.clear()
        labelJob = null
    }

    private fun pullBegin(silent: Boolean) {
        val state = phase
        if (state == Phase.REFRESHING || state == Phase.DONE) {
            refreshId += 1
            scriptedRefresh = false
            haptic(PullLeverRefreshHaptic.CANCEL, silent)
            label = PullLeverRefreshLabel.NONE
            animateTiming(
                key = SWAY,
                from = { sway },
                set = { sway = it },
                target = 0f,
                durationMillis = PullLeverRefreshMotion.Timing.swayFade,
                easing = ::easeOut,
            )
            animateTiming(
                key = GLOW,
                from = { glow },
                set = { glow = it },
                target = 0f,
                durationMillis = PullLeverRefreshMotion.Timing.swayFade,
                easing = ::easeOut,
            )
            cancelAnimation(LIT)
            lit = 1f
        }
        cancelAnimation(ANGLE)
        cancelAnimation(FILL)
        lit = 1f
        val pull = PullLeverRefreshMotion.Pull
        val progress = ((angle - pull.restAngle) / (pull.maxAngle - pull.restAngle)).coerceIn(0f, 1f)
        grabFrom = progress * pull.leverPull
        fill = progress
        ticks = floor(
            progress * PullLeverRefreshTheme.Lever.dots +
                PullLeverRefreshTokens.Rendering.quantizationEpsilon,
        ).toInt()
        stretchTicks = 0
        phase = Phase.PULLING
    }

    private fun pullMove(
        drop: Float,
        silent: Boolean,
    ) {
        val state = phase
        if (state != Phase.PULLING && state != Phase.ARMED) {
            return
        }
        val pull = PullLeverRefreshMotion.Pull
        val progress = max(drop + grabFrom, 0f) / pull.leverPull
        val clamped = min(progress, 1f)
        val over = max(progress - 1f, 0f) * pull.leverPull
        val stretch = if (PullLeverRefreshMotion.REPLICA_ONLY) {
            0f
        } else {
            pull.overAngle * over / (over + pull.overSoftness)
        }
        angle = pull.restAngle + (pull.maxAngle - pull.restAngle) * clamped + stretch
        val currentStretchTicks = floor(stretch / pull.overTick).toInt()
        if (currentStretchTicks > stretchTicks) {
            haptic(PullLeverRefreshHaptic.STRETCH, silent)
        }
        stretchTicks = currentStretchTicks
        fill = clamped
        val covered = floor(
            clamped * PullLeverRefreshTheme.Lever.dots +
                PullLeverRefreshTokens.Rendering.quantizationEpsilon,
        ).toInt()
        if (covered > ticks && clamped < 1f) {
            haptic(PullLeverRefreshHaptic.TICK, silent)
        }
        ticks = covered
        if (clamped >= 1f && state == Phase.PULLING) {
            phase = Phase.ARMED
            haptic(PullLeverRefreshHaptic.ARMED, silent)
        } else if (clamped < 1f && state == Phase.ARMED) {
            phase = Phase.PULLING
        }
    }

    private fun pullEnd(
        silent: Boolean,
        cancelled: Boolean,
        velocity: Float,
    ) {
        val state = phase
        if (state == Phase.ARMED && !cancelled) {
            startRefresh(
                silent = silent,
                velocity = velocity,
            )
            return
        }
        if (state != Phase.PULLING && state != Phase.ARMED) {
            return
        }
        phase = Phase.IDLE
        val spring = PullLeverRefreshMotion.Spring.home
        animateSpringOrReduced(
            key = ANGLE,
            from = { angle },
            set = { angle = it },
            target = PullLeverRefreshMotion.Pull.restAngle,
            initialVelocity = velocity * PullLeverRefreshMotion.Pull.degPerPt,
            damping = spring.damping,
            stiffness = spring.stiffness,
            mass = spring.mass,
        )
        val duration = max(fill * PullLeverRefreshTheme.Lever.dots, 1f) *
            PullLeverRefreshMotion.Timing.dotMs /
            PullLeverRefreshTokens.Timing.fillReturnDivisor
        animateTiming(
            key = FILL,
            from = { fill },
            set = { fill = it },
            target = 0f,
            durationMillis = duration,
            easing = ::linear,
        )
    }

    private fun startRefresh(
        silent: Boolean,
        velocity: Float,
    ) {
        phase = Phase.REFRESHING
        refreshId += 1
        val id = refreshId
        stretchTicks = 0
        labelJob?.cancel()
        label = PullLeverRefreshLabel.REFRESHING
        haptic(PullLeverRefreshHaptic.RELEASE, silent)
        val pull = PullLeverRefreshMotion.Pull
        val thrownVelocity = -min(abs(velocity) * pull.degPerPt * pull.throwBoost, pull.maxThrow)
        val throwSpring = PullLeverRefreshMotion.Spring.throwUp
        animateSpringOrReduced(
            key = ANGLE,
            from = { angle },
            set = { angle = it },
            target = PullLeverRefreshMotion.Timing.upAngle,
            initialVelocity = thrownVelocity,
            damping = throwSpring.damping,
            stiffness = throwSpring.stiffness,
            mass = throwSpring.mass,
        )
        animateTiming(
            key = GLOW,
            from = { glow },
            set = { glow = it },
            target = 1f,
            durationMillis = if (reduceMotion) {
                PullLeverRefreshMotion.Timing.reduced
            } else {
                PullLeverRefreshMotion.Timing.glowRise
            },
            easing = ::easeOut,
        )
        val keep = 1f / PullLeverRefreshTheme.Lever.dots +
            PullLeverRefreshTokens.Rendering.quantizationEpsilon
        animateTiming(
            key = FILL,
            from = { fill },
            set = { fill = it },
            target = keep,
            durationMillis = (PullLeverRefreshTheme.Lever.dots - 1) * PullLeverRefreshMotion.Timing.dotMs,
            easing = ::linear,
        )
        if (!reduceMotion && !PullLeverRefreshMotion.REPLICA_ONLY) {
            startSway()
        }
        scriptedRefresh = silent
        if (!silent) {
            runRefresh(id)
        }
    }

    private fun runRefresh(id: Int) {
        scope.launch {
            try {
                onRefresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // The React Native reference resolves its visual state in finally, including request failures.
            } finally {
                if (coroutineContext.isActive && !disposed) {
                    finishRefresh(
                        silent = false,
                        id = id,
                    )
                }
            }
        }
    }

    private fun finishRefresh(
        silent: Boolean,
        id: Int,
    ) {
        if (phase != Phase.REFRESHING || id != refreshId) {
            return
        }
        scriptedRefresh = false
        phase = Phase.DONE
        animateTiming(
            key = SWAY,
            from = { sway },
            set = { sway = it },
            target = 0f,
            durationMillis = PullLeverRefreshMotion.Timing.swayFade,
            easing = ::easeOut,
        )
        cancelAnimation(FILL)
        animateTiming(
            key = FILL,
            from = { fill },
            set = { fill = it },
            target = 0f,
            durationMillis = PullLeverRefreshMotion.Timing.dotMs,
            easing = ::linear,
        )
        launchAnimation(ANGLE) {
            animateTimingValue(
                from = angle,
                set = { angle = it },
                target = if (reduceMotion) {
                    PullLeverRefreshMotion.Pull.restAngle
                } else {
                    PullLeverRefreshMotion.Timing.relaxAngle
                },
                durationMillis = PullLeverRefreshMotion.Timing.relaxMs,
                easing = ::travel,
            )
            val fall = PullLeverRefreshMotion.Spring.fall
            animateSpringOrReducedValue(
                from = angle,
                set = { angle = it },
                target = PullLeverRefreshMotion.Pull.restAngle,
                initialVelocity = 0f,
                damping = fall.damping,
                stiffness = fall.stiffness,
                mass = fall.mass,
            )
        }
        launchAnimation(LIT) {
            delay(PullLeverRefreshMotion.Timing.relaxMs.toLong())
            animateTimingValue(
                from = lit,
                set = { lit = it },
                target = 0f,
                durationMillis = PullLeverRefreshMotion.Timing.lightMs,
                easing = ::easeOut,
            )
            delay(PullLeverRefreshMotion.Timing.dimMs.toLong())
            animateTimingValue(
                from = lit,
                set = { lit = it },
                target = 1f,
                durationMillis = PullLeverRefreshMotion.Timing.lightMs,
                easing = ::easeOut,
            )
            completeRelight(silent)
        }
    }

    private fun completeRelight(silent: Boolean) {
        animateTiming(
            key = GLOW,
            from = { glow },
            set = { glow = it },
            target = 0f,
            durationMillis = PullLeverRefreshMotion.Timing.pulse / 2f,
            easing = ::easeOut,
        )
        if (reduceMotion) {
            launchAnimation(KNOB_LIT) {
                animateTimingValue(
                    from = knobLit,
                    set = { knobLit = it },
                    target = 1f,
                    durationMillis = PullLeverRefreshMotion.Timing.knobFlash,
                    easing = ::easeOut,
                )
                animateTimingValue(
                    from = knobLit,
                    set = { knobLit = it },
                    target = 0f,
                    durationMillis = PullLeverRefreshMotion.Timing.knobFade,
                    easing = ::easeOut,
                )
            }
        } else {
            pulse = 0f
            launchAnimation(PULSE) {
                animateTimingValue(
                    from = pulse,
                    set = { pulse = it },
                    target = 1f,
                    durationMillis = PullLeverRefreshMotion.Timing.pulse,
                    easing = ::travel,
                )
                pulse = -1f
                launchAnimation(KNOB_LIT) {
                    animateTimingValue(
                        from = knobLit,
                        set = { knobLit = it },
                        target = 1f,
                        durationMillis = PullLeverRefreshMotion.Timing.knobFlash,
                        easing = ::easeOut,
                    )
                    animateTimingValue(
                        from = knobLit,
                        set = { knobLit = it },
                        target = 0f,
                        durationMillis = PullLeverRefreshMotion.Timing.knobFade,
                        easing = ::easeOut,
                    )
                }
                ripple = 0f
                animateTiming(
                    key = RIPPLE,
                    from = { ripple },
                    set = { ripple = it },
                    target = 1f,
                    durationMillis = PullLeverRefreshMotion.Light.rippleMs,
                    easing = ::easeOut,
                )
            }
            launchAnimation(BLOOM) {
                animateTimingValue(
                    from = bloom,
                    set = { bloom = it },
                    target = PullLeverRefreshMotion.Timing.bloomScale,
                    durationMillis = PullLeverRefreshMotion.Timing.bloomRise,
                    easing = ::easeOut,
                )
                val spring = PullLeverRefreshMotion.Spring.bloom
                animateSpringValue(
                    from = bloom,
                    set = { bloom = it },
                    target = 1f,
                    initialVelocity = 0f,
                    damping = spring.damping,
                    stiffness = spring.stiffness,
                    mass = spring.mass,
                )
            }
            launchAnimation(SHAKE) {
                PullLeverRefreshMotion.Timing.shake.forEach { target ->
                    animateTimingValue(
                        from = shake,
                        set = { shake = it },
                        target = target,
                        durationMillis = PullLeverRefreshMotion.Timing.shakeStep,
                        easing = ::quadInOut,
                    )
                }
            }
        }
        announceDone(silent)
        phase = Phase.IDLE
    }

    private fun announceDone(silent: Boolean) {
        label = PullLeverRefreshLabel.UPDATED
        haptic(PullLeverRefreshHaptic.SUCCESS, silent)
        labelJob?.cancel()
        labelJob = scope.launch {
            delay(PullLeverRefreshMotion.Timing.doneLabel.toLong())
            label = PullLeverRefreshLabel.NONE
        }
    }

    private fun startSway() {
        launchAnimation(SWAY) {
            val amplitude = PullLeverRefreshMotion.Timing.swayAmplitude
            val halfPeriod = PullLeverRefreshMotion.Timing.swayHalfPeriod
            delay(halfPeriod.toLong())
            animateTimingValue(
                from = sway,
                set = { sway = it },
                target = amplitude,
                durationMillis = halfPeriod / 2f,
                easing = ::sinOut,
            )
            while (coroutineContext.isActive) {
                animateTimingValue(
                    from = sway,
                    set = { sway = it },
                    target = -amplitude,
                    durationMillis = halfPeriod,
                    easing = ::sinInOut,
                )
                animateTimingValue(
                    from = sway,
                    set = { sway = it },
                    target = amplitude,
                    durationMillis = halfPeriod,
                    easing = ::sinInOut,
                )
            }
        }
    }

    private suspend fun runClock() {
        var previousNanos = withFrameNanos { it }
        while (coroutineContext.isActive) {
            val nowNanos = withFrameNanos { it }
            val elapsed = (nowNanos - previousNanos) / NANOS_PER_SECOND
            previousNanos = nowNanos
            val delta = min(elapsed, MAX_FRAME_DELTA)
            tickClock(delta)
        }
    }

    private fun tickClock(delta: Float) {
        clock = if (autoPlay && !demoPaused) {
            demoTime
        } else {
            clock + delta
        }
        val idleTarget = if (
            phase == Phase.IDLE &&
            !realDown &&
            !scriptPulling &&
            !reduceMotion &&
            !PullLeverRefreshMotion.REPLICA_ONLY
        ) {
            1f
        } else {
            0f
        }
        idleAmplitude += (idleTarget - idleAmplitude) * min(
            delta * PullLeverRefreshTokens.Timing.idleResponse,
            1f,
        )
        idle = PullLeverRefreshMotion.Timing.idleAmplitude * idleAmplitude * sin(
            (2f * PI.toFloat() * clock) / PullLeverRefreshMotion.Timing.idlePeriod,
        )
        breath = if (reduceMotion) {
            0f
        } else {
            sin((2f * PI.toFloat() * clock) / PullLeverRefreshMotion.Light.haloBreath)
        }
        if (!autoPlay) {
            return
        }
        if (demoPaused) {
            if (!realDown) {
                quiet += delta
            }
            if (quiet >= PullLeverRefreshMotion.Demo.resumeMs / 1_000f && phase == Phase.IDLE) {
                demoTime = 0f
                demoPaused = false
            }
            return
        }
        val previous = demoTime
        val next = previous + delta
        scriptLever(
            previous = previous,
            next = next,
            script = PullLeverRefreshMotion.Demo.full,
        )
        if (previous < PullLeverRefreshMotion.Demo.resolve && next >= PullLeverRefreshMotion.Demo.resolve) {
            finishRefresh(
                silent = true,
                id = refreshId,
            )
        }
        scriptLever(
            previous = previous,
            next = next,
            script = PullLeverRefreshMotion.Demo.partial,
        )
        val loopSeconds = LOOP / 1_000f
        demoTime = if (next >= loopSeconds) {
            next - loopSeconds
        } else {
            next
        }
    }

    private fun scriptLever(
        previous: Float,
        next: Float,
        script: PullLeverRefreshMotion.Demo.Script,
    ) {
        if (previous < script.down && next >= script.down) {
            scriptPulling = true
            pullBegin(silent = true)
        }
        if (scriptPulling && next >= script.down && next < script.up) {
            pullMove(
                drop = scriptDrop(script, next),
                silent = true,
            )
        }
        if (scriptPulling && previous < script.up && next >= script.up) {
            scriptPulling = false
            val end = scriptDrop(script, script.moveEnd)
            val beforeEnd = scriptDrop(
                script,
                script.moveEnd - PullLeverRefreshTokens.Gesture.scriptedVelocityWindow,
            )
            pullEnd(
                silent = true,
                cancelled = false,
                velocity = (end - beforeEnd) /
                    PullLeverRefreshTokens.Gesture.scriptedVelocityWindow +
                    PullLeverRefreshTokens.Gesture.scriptedVelocityBoost,
            )
        }
    }

    private fun scriptDrop(
        script: PullLeverRefreshMotion.Demo.Script,
        time: Float,
    ): Float {
        val progress = (time - script.down) / (script.moveEnd - script.down)
        return PullLeverRefreshMotion.Pull.leverPull * script.depth * cosineEase(progress)
    }

    private fun interruptDemo() {
        realDown = true
        quiet = 0f
        if (demoPaused) {
            return
        }
        demoPaused = true
        if (scriptPulling) {
            scriptPulling = false
            pullEnd(
                silent = true,
                cancelled = true,
                velocity = 0f,
            )
        }
        if (phase == Phase.REFRESHING && scriptedRefresh) {
            scriptedRefresh = false
            runRefresh(refreshId)
        }
    }

    private fun releaseTouch() {
        realDown = false
        quiet = 0f
    }

    private fun haptic(
        kind: PullLeverRefreshHaptic,
        silent: Boolean,
    ) {
        if (!silent) {
            performHaptic(kind)
        }
    }

    private fun animateTiming(
        key: String,
        from: () -> Float,
        set: (Float) -> Unit,
        target: Float,
        durationMillis: Number,
        easing: (Float) -> Float = ::quadInOut,
    ) {
        launchAnimation(key) {
            animateTimingValue(
                from = from(),
                set = set,
                target = target,
                durationMillis = durationMillis.toFloat(),
                easing = easing,
            )
        }
    }

    private fun animateSpringOrReduced(
        key: String,
        from: () -> Float,
        set: (Float) -> Unit,
        target: Float,
        initialVelocity: Float,
        damping: Float,
        stiffness: Float,
        mass: Float,
    ) {
        launchAnimation(key) {
            animateSpringOrReducedValue(
                from = from(),
                set = set,
                target = target,
                initialVelocity = initialVelocity,
                damping = damping,
                stiffness = stiffness,
                mass = mass,
            )
        }
    }

    private suspend fun animateSpringOrReducedValue(
        from: Float,
        set: (Float) -> Unit,
        target: Float,
        initialVelocity: Float,
        damping: Float,
        stiffness: Float,
        mass: Float,
    ) {
        if (reduceMotion) {
            animateTimingValue(
                from = from,
                set = set,
                target = target,
                durationMillis = PullLeverRefreshMotion.Timing.reduced,
                easing = ::quadInOut,
            )
            return
        }
        animateSpringValue(
            from = from,
            set = set,
            target = target,
            initialVelocity = initialVelocity,
            damping = damping,
            stiffness = stiffness,
            mass = mass,
        )
    }

    private suspend fun animateTimingValue(
        from: Float,
        set: (Float) -> Unit,
        target: Float,
        durationMillis: Number,
        easing: (Float) -> Float,
    ) {
        val durationNanos = durationMillis.toDouble() * NANOS_PER_MILLISECOND
        if (durationNanos <= 0.0) {
            set(target)
            return
        }
        val startNanos = System.nanoTime()
        while (coroutineContext.isActive) {
            val frameNanos = withFrameNanos { it }
            val progress = ((frameNanos - startNanos) / durationNanos).toFloat().coerceIn(0f, 1f)
            set(from + (target - from) * easing(progress))
            if (progress >= 1f) {
                return
            }
        }
    }

    private suspend fun animateSpringValue(
        from: Float,
        set: (Float) -> Unit,
        target: Float,
        initialVelocity: Float,
        damping: Float,
        stiffness: Float,
        mass: Float,
    ) {
        val displacement = (from - target).toDouble()
        val velocity = initialVelocity.toDouble()
        val massValue = mass.toDouble()
        val stiffnessValue = stiffness.toDouble()
        val dampingValue = damping.toDouble()
        val naturalFrequency = sqrt(stiffnessValue / massValue)
        val dampingRatio = dampingValue / (2.0 * sqrt(stiffnessValue * massValue))
        val initialEnergy = 0.5 * massValue * velocity * velocity +
            0.5 * stiffnessValue * displacement * displacement
        if (initialEnergy == 0.0) {
            set(target)
            return
        }
        var previousNanos = System.nanoTime()
        var seconds = 0.0
        while (coroutineContext.isActive) {
            val frameNanos = withFrameNanos { it }
            val frameSeconds = (frameNanos - previousNanos) / NANOS_PER_SECOND_DOUBLE
            previousNanos = frameNanos
            seconds += frameSeconds.coerceIn(0.0, MAX_SPRING_FRAME_SECONDS)
            val sample = springSample(
                displacement = displacement,
                velocity = velocity,
                naturalFrequency = naturalFrequency,
                dampingRatio = dampingRatio,
                seconds = seconds,
            )
            set((target + sample.first).toFloat())
            val currentEnergy = 0.5 * massValue * sample.second * sample.second +
                0.5 * stiffnessValue * sample.first * sample.first
            if (currentEnergy / initialEnergy <= SPRING_ENERGY_THRESHOLD || seconds >= MAX_SPRING_SECONDS) {
                set(target)
                return
            }
        }
    }

    private fun launchAnimation(
        key: String,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        cancelAnimation(key)
        val job = scope.launch(block = block)
        animations[key] = job
        job.invokeOnCompletion {
            if (animations[key] === job) {
                animations.remove(key)
            }
        }
        return job
    }

    private fun cancelAnimation(key: String) {
        animations.remove(key)?.cancel()
    }

    private fun easeOut(progress: Float): Float {
        return PullLeverRefreshMotion.Timing.easeOut.transform(progress)
    }

    private fun travel(progress: Float): Float {
        return PullLeverRefreshMotion.Timing.easeInOut.transform(progress)
    }

    private companion object {
        const val ANGLE = "angle"
        const val SWAY = "sway"
        const val FILL = "fill"
        const val LIT = "lit"
        const val BLOOM = "bloom"
        const val GLOW = "glow"
        const val PULSE = "pulse"
        const val RIPPLE = "ripple"
        const val KNOB_LIT = "knobLit"
        const val SHAKE = "shake"
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val NANOS_PER_SECOND_DOUBLE = 1_000_000_000.0
        const val NANOS_PER_MILLISECOND = 1_000_000.0
        const val MAX_FRAME_DELTA = PullLeverRefreshTokens.Timing.maxFrameDelta
        const val MAX_SPRING_FRAME_SECONDS = 0.064
        const val MAX_SPRING_SECONDS = 5.0
        const val SPRING_ENERGY_THRESHOLD = 0.000000006
    }
}

@Composable
internal fun rememberPullLeverRefreshState(
    autoPlay: Boolean,
    reduceMotion: Boolean,
    onRefresh: (suspend () -> Unit)? = null,
): PullLeverRefreshState {
    val parentScope = rememberCoroutineScope()
    val view = LocalView.current
    val refresh by rememberUpdatedState(onRefresh)
    val currentView by rememberUpdatedState(view)
    val state = remember(autoPlay, reduceMotion) {
        PullLeverRefreshState(
            parentScope = parentScope,
            autoPlay = autoPlay,
            reduceMotion = reduceMotion,
            onRefresh = {
                val action = refresh
                if (action == null) {
                    delay(PullLeverRefreshMotion.Demo.fetchMs.toLong())
                } else {
                    action()
                }
            },
            performHaptic = { kind ->
                performPullLeverRefreshHaptic(currentView, kind)
            },
        )
    }
    DisposableEffect(state) {
        onDispose {
            state.dispose()
        }
    }
    return state
}

private fun performPullLeverRefreshHaptic(
    view: View,
    kind: PullLeverRefreshHaptic,
) {
    val feedback = when (kind) {
        PullLeverRefreshHaptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
        PullLeverRefreshHaptic.ARMED -> HapticFeedbackConstants.CONTEXT_CLICK
        PullLeverRefreshHaptic.RELEASE -> HapticFeedbackConstants.LONG_PRESS
        PullLeverRefreshHaptic.NUDGE -> HapticFeedbackConstants.CONTEXT_CLICK
        PullLeverRefreshHaptic.STRETCH -> HapticFeedbackConstants.CONTEXT_CLICK
        PullLeverRefreshHaptic.CANCEL -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        }
        PullLeverRefreshHaptic.SUCCESS -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
        }
    }
    view.performHapticFeedback(feedback)
}

private fun cosineEase(progress: Float): Float {
    val clamped = progress.coerceIn(0f, 1f)
    return (1f - cos(PI.toFloat() * clamped)) / 2f
}

private fun linear(progress: Float): Float {
    return progress
}

private fun quadInOut(progress: Float): Float {
    return if (progress < 0.5f) {
        2f * progress * progress
    } else {
        1f - ((-2f * progress + 2f) * (-2f * progress + 2f)) / 2f
    }
}

private fun sinOut(progress: Float): Float {
    return sin(progress * PI.toFloat() / 2f)
}

private fun sinInOut(progress: Float): Float {
    return -(cos(PI.toFloat() * progress) - 1f) / 2f
}

private fun springSample(
    displacement: Double,
    velocity: Double,
    naturalFrequency: Double,
    dampingRatio: Double,
    seconds: Double,
): Pair<Double, Double> {
    if (dampingRatio < 1.0) {
        val dampedFrequency = naturalFrequency * sqrt(1.0 - dampingRatio * dampingRatio)
        val decay = dampingRatio * naturalFrequency
        val coefficient = (velocity + decay * displacement) / dampedFrequency
        val exponential = exp(-decay * seconds)
        val cosine = cos(dampedFrequency * seconds)
        val sine = sin(dampedFrequency * seconds)
        val position = exponential * (displacement * cosine + coefficient * sine)
        val currentVelocity = exponential * (
            -decay * (displacement * cosine + coefficient * sine) +
                -displacement * dampedFrequency * sine +
                coefficient * dampedFrequency * cosine
            )
        return position to currentVelocity
    }
    val coefficient = velocity + naturalFrequency * displacement
    val exponential = exp(-naturalFrequency * seconds)
    val position = exponential * (displacement + coefficient * seconds)
    val currentVelocity = exponential * (
        coefficient - naturalFrequency * (displacement + coefficient * seconds)
        )
    return position to currentVelocity
}
