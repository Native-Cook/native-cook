package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Owns the gesture and autoplay state; all values are advanced from one frame clock. */
internal class GlassDiscOnboardingController(
    private val layout: GlassDiscOnboardingLayout,
    private val autoPlay: Boolean,
    private val reducedMotion: Boolean,
    private val onHaptic: (Haptic) -> Unit,
) {
    enum class Haptic { Grab, Commit, Back }

    private enum class Phase { Rest, Pulling, Opening, Open, Gather, Home }

    private val domeRadius = layout.dp(GlassDiscOnboardingTheme.Layout.DomeRadius)
    private val lensRadius = layout.dp(GlassDiscOnboardingTheme.Layout.LensRadius)
    private var phase = Phase.Rest
    private var centerX = layout.domeX
    private var centerY = layout.domeY
    private var radius = domeRadius
    private var magnification = GlassDiscOnboardingTheme.Layout.Magnification
    private var rim = 1f
    private var hint = 1f
    private var route = 0f
    private var grabY = 0f
    private var pull = 0f

    private var phaseElapsed = 0f
    private var startX = centerX
    private var startY = centerY
    private var startRadius = radius
    private var startMagnification = magnification
    private var startRim = rim
    private var startRoute = route
    private var homeVelocityY = 0f
    private var homeHintFrom = hint

    private var ghostX = 0f
    private var ghostY = 0f
    private var ghostOpacity = 0f
    private var ghostPressed = 0f
    private var demoClock = 0f
    private var paused = !autoPlay || reducedMotion
    private var touching = false
    private var quiet = 0f
    private var tidy = -1f
    private var silent = autoPlay
    private var pendingBack = false

    val isOpen: Boolean get() = phase == Phase.Open

    val needsFrames: Boolean
        get() = (autoPlay && !reducedMotion) || phase == Phase.Opening || phase == Phase.Gather ||
            phase == Phase.Home || (phase == Phase.Open && route < 0.999f)

    fun snapshot() = GlassDiscOnboardingFrame(
        centerX = centerX,
        centerY = centerY,
        radius = radius,
        magnification = magnification,
        rim = rim,
        hint = hint,
        route = route,
        ghostX = ghostX,
        ghostY = ghostY,
        ghostOpacity = ghostOpacity,
        ghostPressed = ghostPressed,
        isOpen = isOpen,
    )

    fun onPointerDown(x: Float, y: Float) {
        touching = true
        interruptDemo()
        if (isOpen && isBackPoint(x, y)) {
            pendingBack = true
            return
        }
        grab(x, y, haptic = true)
    }

    fun onPointerMove(x: Float, y: Float) {
        if (pendingBack && !isBackPoint(x, y)) pendingBack = false
        drag(x, y)
    }

    fun onPointerUp(velocityY: Float, cancelled: Boolean) {
        if (pendingBack && !cancelled) startBack(haptic = true)
        pendingBack = false
        if (phase == Phase.Pulling) release(if (cancelled) 0f else velocityY, haptic = true)
        touching = false
        quiet = 0f
    }

    fun accessibilityEnter() {
        interruptDemo()
        if (phase == Phase.Rest) {
            centerX = layout.domeX
            centerY = layout.lensY
            radius = lensRadius
            startOpen(haptic = true)
        }
    }

    fun accessibilityBack() {
        interruptDemo()
        startBack(haptic = true)
    }

    fun update(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        updateMotion(dt)
        if (!autoPlay || reducedMotion) return
        if (paused) {
            updatePaused(dt)
            return
        }

        val previous = demoClock
        val next = previous + dt
        runDemo(previous, min(next, LOOP))
        demoClock = if (next >= LOOP) next - LOOP else next
    }

    private fun updateMotion(dt: Float) {
        when (phase) {
            Phase.Opening -> {
                phaseElapsed += dt
                val duration = if (reducedMotion) GlassDiscOnboardingMotion.ReducedOpenDuration else GlassDiscOnboardingMotion.OpenDuration
                val e = easeInOut(phaseElapsed / duration)
                radius = lerp(startRadius, layout.cover + layout.dp(40f), e)
                magnification = lerp(startMagnification, 1f, e)
                rim = lerp(startRim, 0f, e)
                hint = lerp(hint, 0f, easeOut(dt / GlassDiscOnboardingMotion.HintDuration))
                if (phaseElapsed >= duration) {
                    radius = layout.cover + layout.dp(40f)
                    magnification = 1f
                    rim = 0f
                    phase = Phase.Open
                    phaseElapsed = 0f
                }
            }

            Phase.Open -> {
                if (route < 1f) {
                    phaseElapsed += dt
                    val p = (phaseElapsed - GlassDiscOnboardingMotion.RouteDelay) /
                        if (reducedMotion) GlassDiscOnboardingMotion.ReducedDuration else GlassDiscOnboardingMotion.RouteDuration
                    route = easeInOut(p)
                }
            }

            Phase.Gather -> {
                phaseElapsed += dt
                val duration = if (reducedMotion) GlassDiscOnboardingMotion.ReducedDuration else GlassDiscOnboardingMotion.GatherDuration
                val e = easeInOut(phaseElapsed / duration)
                centerX = lerp(startX, layout.domeX, e)
                centerY = lerp(startY, layout.lensY, e)
                radius = lerp(startRadius, lensRadius, e)
                magnification = lerp(startMagnification, GlassDiscOnboardingTheme.Layout.Magnification, e)
                rim = lerp(startRim, 1f, e)
                route = lerp(startRoute, 0f, easeOut(phaseElapsed / 0.16f))
                if (phaseElapsed >= duration) beginHome(velocityY = 0f, hintFrom = 0f)
            }

            Phase.Home -> {
                phaseElapsed += dt
                val duration = if (reducedMotion) GlassDiscOnboardingMotion.ReducedDuration else GlassDiscOnboardingMotion.HomeDuration
                val t = (phaseElapsed / duration).coerceIn(0f, 1f)
                if (reducedMotion) {
                    val e = easeOut(t)
                    centerX = lerp(startX, layout.domeX, e)
                    centerY = lerp(startY, layout.domeY, e)
                    radius = lerp(startRadius, domeRadius, e)
                } else {
                    centerX = spring(startX, layout.domeX, 0f, phaseElapsed, duration)
                    centerY = spring(startY, layout.domeY, homeVelocityY, phaseElapsed, duration)
                    radius = spring(startRadius, domeRadius, 0f, phaseElapsed, duration)
                }
                val hintProgress = (phaseElapsed - 0.2f) / GlassDiscOnboardingMotion.HintDuration
                hint = lerp(homeHintFrom, 1f, easeOut(hintProgress))
                if (t >= 1f) resetRest()
            }

            Phase.Rest, Phase.Pulling -> Unit
        }
    }

    private fun updatePaused(dt: Float) {
        if (!touching && phase != Phase.Pulling) quiet += dt
        if (tidy < 0f && quiet >= GlassDiscOnboardingMotion.ResumeDelay) {
            tidy = 0f
            silent = true
            if (phase == Phase.Open) startBack(haptic = false)
        }
        if (tidy >= 0f) {
            tidy += dt
            if (phase == Phase.Open) startBack(haptic = false)
            if (tidy >= GlassDiscOnboardingMotion.TidyDelay && phase == Phase.Rest) {
                tidy = -1f
                demoClock = 0f
                paused = false
            }
        }
    }

    private fun interruptDemo() {
        quiet = 0f
        tidy = -1f
        silent = false
        if (!paused) {
            paused = true
            ghostOpacity = 0f
            ghostPressed = 0f
            if (phase == Phase.Pulling) beginHome(velocityY = 0f, hintFrom = hint)
        }
    }

    private fun grab(x: Float, y: Float, haptic: Boolean): Boolean {
        if (phase != Phase.Rest) return false
        val dx = x - layout.domeX
        val dy = y - layout.domeY
        if (dx * dx + dy * dy > domeRadius * domeRadius) return false
        phase = Phase.Pulling
        grabY = y
        pull = 0f
        if (haptic && !silent) onHaptic(Haptic.Grab)
        return true
    }

    private fun drag(x: Float, y: Float) {
        if (phase != Phase.Pulling) return
        val span = max(1f, grabY - layout.lensY)
        val progress = (grabY - y) / span
        pull = progress
        if (progress <= 0f) {
            centerX = layout.domeX
            centerY = layout.domeY + (y - grabY) * GlassDiscOnboardingMotion.Rubber
            radius = domeRadius
            hint = 1f
            return
        }
        val e = smooth(progress)
        centerX = lerp(layout.domeX, x, e)
        centerY = lerp(layout.domeY, y, e)
        radius = lerp(domeRadius, lensRadius, e)
        hint = max(0f, 1f - progress * 3f)
    }

    private fun release(velocityY: Float, haptic: Boolean) {
        if (phase != Phase.Pulling) return
        if (pull >= GlassDiscOnboardingMotion.Commit || velocityY < GlassDiscOnboardingMotion.Flick * layout.density) {
            startOpen(haptic)
        } else {
            beginHome(velocityY, hint)
        }
    }

    private fun startOpen(haptic: Boolean) {
        phase = Phase.Opening
        phaseElapsed = 0f
        startRadius = radius
        startMagnification = magnification
        startRim = rim
        if (haptic && !silent) onHaptic(Haptic.Commit)
    }

    private fun startBack(haptic: Boolean) {
        if (phase != Phase.Open) return
        phase = Phase.Gather
        phaseElapsed = 0f
        startX = centerX
        startY = centerY
        startRadius = radius
        startMagnification = magnification
        startRim = rim
        startRoute = route
        hint = 0f
        if (haptic && !silent) onHaptic(Haptic.Back)
    }

    private fun beginHome(velocityY: Float, hintFrom: Float) {
        phase = Phase.Home
        phaseElapsed = 0f
        startX = centerX
        startY = centerY
        startRadius = radius
        homeVelocityY = velocityY
        homeHintFrom = hintFrom
    }

    private fun resetRest() {
        phase = Phase.Rest
        phaseElapsed = 0f
        centerX = layout.domeX
        centerY = layout.domeY
        radius = domeRadius
        magnification = GlassDiscOnboardingTheme.Layout.Magnification
        rim = 1f
        hint = 1f
        route = 0f
    }

    private fun runDemo(t0: Float, t1: Float) {
        updateGhost(t1)
        val path = GlassDiscOnboardingMotion.DemoPath
        if (crossed(t0, t1, GlassDiscOnboardingMotion.FingerDown)) {
            val first = path.first()
            grab(first[1] * layout.width, first[2] * layout.height, haptic = false)
        }
        if (phase == Phase.Pulling && t1 >= path.first()[0] && t1 <= path.last()[0] + 0.05f) {
            val point = demoPoint(t1)
            ghostX = point.first
            ghostY = point.second
            drag(point.first, point.second)
        }
        if (crossed(t0, t1, GlassDiscOnboardingMotion.Release)) release(0f, haptic = false)
        if (crossed(t0, t1, GlassDiscOnboardingMotion.Tap)) startBack(haptic = false)
    }

    private fun updateGhost(time: Float) {
        val first = GlassDiscOnboardingMotion.DemoPath.first()
        val backX = layout.dp(GlassDiscOnboardingTheme.Layout.Edge + GlassDiscOnboardingTheme.Layout.SearchHeight / 2f)
        val backY = layout.top + layout.dp(GlassDiscOnboardingTheme.Layout.SearchY + GlassDiscOnboardingTheme.Layout.SearchHeight / 2f)
        when {
            time < GlassDiscOnboardingMotion.FingerIn -> ghostOpacity = 0f
            time < GlassDiscOnboardingMotion.FingerDown -> {
                ghostX = first[1] * layout.width
                ghostY = first[2] * layout.height
                ghostOpacity = easeOut((time - GlassDiscOnboardingMotion.FingerIn) / GlassDiscOnboardingMotion.FingerDuration)
                ghostPressed = 0f
            }
            time < GlassDiscOnboardingMotion.Release -> {
                ghostOpacity = 1f
                ghostPressed = easeOut((time - GlassDiscOnboardingMotion.FingerDown) / GlassDiscOnboardingMotion.FingerDuration)
            }
            time < GlassDiscOnboardingMotion.Release + GlassDiscOnboardingMotion.FingerDuration -> {
                ghostOpacity = 1f - easeOut((time - GlassDiscOnboardingMotion.Release) / GlassDiscOnboardingMotion.FingerDuration)
                ghostPressed = 1f - easeOut((time - GlassDiscOnboardingMotion.Release) / GlassDiscOnboardingMotion.FingerDuration)
            }
            time < GlassDiscOnboardingMotion.TapIn -> {
                ghostOpacity = 0f
                ghostPressed = 0f
            }
            time < GlassDiscOnboardingMotion.Tap -> {
                ghostX = backX
                ghostY = backY
                ghostOpacity = easeOut((time - GlassDiscOnboardingMotion.TapIn) / GlassDiscOnboardingMotion.FingerDuration)
                ghostPressed = easeOut((time - GlassDiscOnboardingMotion.TapIn - 0.08f) / 0.1f)
            }
            time < GlassDiscOnboardingMotion.Tap + 0.21f -> {
                ghostX = backX
                ghostY = backY
                ghostOpacity = 1f - easeOut((time - GlassDiscOnboardingMotion.Tap - 0.06f) / GlassDiscOnboardingMotion.FingerDuration)
                ghostPressed = 0f
            }
            else -> ghostOpacity = 0f
        }
    }

    private fun demoPoint(time: Float): Pair<Float, Float> {
        val path = GlassDiscOnboardingMotion.DemoPath
        for (index in 0 until path.lastIndex) {
            val a = path[index]
            val b = path[index + 1]
            if (time in a[0]..b[0]) {
                val u = (time - a[0]) / (b[0] - a[0])
                val e = ((1.0 - cos(PI * u)) / 2.0).toFloat()
                return lerp(a[1], b[1], e) * layout.width to lerp(a[2], b[2], e) * layout.height
            }
        }
        return path.last()[1] * layout.width to path.last()[2] * layout.height
    }

    private fun isBackPoint(x: Float, y: Float): Boolean {
        val edge = layout.dp(GlassDiscOnboardingTheme.Layout.Edge)
        val size = layout.dp(GlassDiscOnboardingTheme.Layout.SearchHeight)
        val top = layout.top + layout.dp(GlassDiscOnboardingTheme.Layout.SearchY)
        return x in edge..(edge + size) && y in top..(top + size)
    }

    private fun spring(from: Float, to: Float, velocity: Float, elapsed: Float, duration: Float): Float {
        if (elapsed >= duration) return to
        val zeta = GlassDiscOnboardingMotion.HomeDamping
        val omega = 10f / duration
        val damped = omega * sqrt(max(0.0001f, 1f - zeta * zeta))
        val displacement = from - to
        val envelope = exp(-zeta * omega * elapsed)
        val wave = displacement * cos(damped * elapsed) +
            (velocity + zeta * omega * displacement) / damped * sin(damped * elapsed)
        return to + envelope * wave
    }

    private fun easeOut(value: Float): Float = GlassDiscOnboardingMotion.EaseOut.transform(value.coerceIn(0f, 1f))
    private fun easeInOut(value: Float): Float = GlassDiscOnboardingMotion.EaseInOut.transform(value.coerceIn(0f, 1f))
    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun crossed(from: Float, to: Float, value: Float): Boolean = from < value && to >= value
    private fun lerp(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress.coerceIn(0f, 1f)
}
