package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Owns the deterministic gesture state machine; the Compose view owns frame invalidation. */
internal class GlassDiscOnboardingEngine(
    widthPx: Float,
    heightPx: Float,
    density: Float,
    ready: Boolean = true,
    autoPlay: Boolean = true,
    reduceMotion: Boolean = false,
    private val onHaptic: (GlassDiscOnboardingHaptic) -> Unit = {},
) {
    private var widthPx = max(1f, widthPx)
    private var heightPx = max(1f, heightPx)
    private var density = max(MinimumDensity, density)
    private val simulation = GlassDiscOnboardingSimulation(this.widthPx, this.heightPx, this.density)
    private val discX = GlassDiscOnboardingAnimation(this.widthPx / 2f)
    private val discY = GlassDiscOnboardingAnimation(domeY())
    private val discRadius = GlassDiscOnboardingAnimation(domeRadius())
    private val ring = GlassDiscOnboardingAnimation(0f)
    private val reveal = GlassDiscOnboardingAnimation(0f)
    private val textureMode = GlassDiscOnboardingAnimation(0f)
    private val introAlpha = GlassDiscOnboardingAnimation(1f)
    private val hintAlpha = GlassDiscOnboardingAnimation(1f)
    private val headlineAlpha = GlassDiscOnboardingAnimation(0f)
    private val restAlpha = GlassDiscOnboardingAnimation(0f)
    private val nudge = GlassDiscOnboardingAnimation(0f)
    private var stretch = 0f
    private var glint = 0f
    private var glintAngle = 0f
    private var spreadBoost = 0f
    private var squash = 0f
    private var clockMillis = 0f
    private var landedAtMillis = FarPast
    private var glintAtMillis = FarPast
    private var lastDiscY = discY.value
    private var phase = Phase.Intro
    private var dragStartY = 0f
    private var dragPull = 0f
    private var dragTicks = 0
    private var dragScripted = false
    private var fingerSampleX = 0f
    private var fingerSampleY = 0f
    private var fingerSampleMillis = 0L
    private var fingerX = 0f
    private var fingerY = 0f
    private var fingerAlpha = 0f
    private var landingDelayMillis = -1f
    private var scriptTimeMillis = 0f
    private var scriptPaused = false
    private var scriptRealDown = false
    private var scriptQuietMillis = 0f
    private var scriptSegment = 0
    private var resumeReturning = false
    private var wasReducedMotion = reduceMotion

    var stage: GlassDiscOnboardingStage = GlassDiscOnboardingStage.Intro
        private set

    var ready: Boolean = ready
    var autoPlay: Boolean = autoPlay
    var reduceMotion: Boolean = reduceMotion
        set(value) {
            if (field == value) {
                return
            }
            field = value
            wasReducedMotion = value
            if (value) {
                showReducedMotionFinal()
            } else {
                resetLoopRest()
            }
        }

    init {
        if (reduceMotion) {
            showReducedMotionFinal()
        }
    }

    fun resize(widthPx: Float, heightPx: Float, density: Float) {
        this.widthPx = max(1f, widthPx)
        this.heightPx = max(1f, heightPx)
        this.density = max(MinimumDensity, density)
        simulation.resize(this.widthPx, this.heightPx, this.density)
        if (reduceMotion) {
            showReducedMotionFinal()
        } else {
            resetLoopRest()
        }
    }

    fun update(dtSeconds: Float, nowMillis: Long) {
        if (!ready) {
            return
        }
        if (wasReducedMotion != reduceMotion) {
            wasReducedMotion = reduceMotion
            if (reduceMotion) {
                showReducedMotionFinal()
            } else {
                resetLoopRest()
            }
        }
        if (reduceMotion) {
            return
        }
        val elapsedSeconds = dtSeconds.coerceIn(0f, MaximumFrameSeconds)
        val elapsedMillis = elapsedSeconds * MillisPerSecond
        clockMillis += elapsedMillis
        updateAnimations(elapsedSeconds)
        updateLanding(elapsedMillis)
        updateMaterial(elapsedMillis)
        simulation.step(elapsedMillis, reduceMotion)
        if (!autoPlay) {
            return
        }
        if (scriptPaused) {
            updatePausedScript(elapsedMillis)
            return
        }
        runScript(elapsedMillis, nowMillis)
    }

    fun onTouchDown(x: Float, y: Float, nowMillis: Long) {
        if (!ready || reduceMotion) {
            return
        }
        pauseForRealTouch()
        touchBegin(x, y, scripted = false, nowMillis = nowMillis)
    }

    fun onTouchMove(x: Float, y: Float, nowMillis: Long) {
        if (!ready || reduceMotion) {
            return
        }
        touchMove(x, y, nowMillis)
    }

    fun onTouchUp(velocityY: Float, cancelled: Boolean, nowMillis: Long) {
        if (!ready || reduceMotion) {
            return
        }
        touchEnd(velocityY, cancelled)
        scriptPaused = true
        scriptRealDown = false
        scriptQuietMillis = 0f
        scriptSegment = 0
        fingerAlpha = 0f
        fingerSampleMillis = nowMillis
    }

    fun enter(nowMillis: Long) {
        if (!ready || reduceMotion || phase != Phase.Intro) {
            return
        }
        pauseAfterAction(nowMillis)
        commit(scripted = false, velocityY = 0f)
    }

    fun startOver(nowMillis: Long) {
        if (!ready || reduceMotion || phase != Phase.Community) {
            return
        }
        pauseAfterAction(nowMillis)
        rewind(scripted = false, velocityY = 0f)
    }

    /** Pauses the scripted finger for a control touch without starting a disc or bubble gesture. */
    fun interruptAutoPlay(nowMillis: Long, touchDown: Boolean) {
        if (!ready || reduceMotion) {
            return
        }
        if (touchDown) {
            pauseForRealTouch()
            fingerSampleMillis = nowMillis
            return
        }
        scriptPaused = true
        scriptRealDown = false
        scriptQuietMillis = 0f
        scriptSegment = 0
        fingerAlpha = 0f
        fingerSampleMillis = nowMillis
    }

    fun interruptAutoPlay(nowMillis: Long) {
        interruptAutoPlay(nowMillis, touchDown = false)
    }

    fun snapshot(): GlassDiscOnboardingFrame {
        val displayedRadius = discRadius.value * (1f - squash)
        val displayedY = discY.value - nudge.value
        return GlassDiscOnboardingFrame(
            density = density,
            stage = stage,
            phase = phase.code,
            discX = discX.value,
            discY = displayedY,
            discRadius = displayedRadius,
            ring = ring.value.coerceIn(0f, 1f),
            reveal = reveal.value.coerceIn(0f, 1f),
            textureMode = textureMode.value,
            introAlpha = introAlpha.value.coerceIn(0f, 1f),
            hintAlpha = hintAlpha.value.coerceIn(0f, 1f),
            headlineAlpha = headlineAlpha.value.coerceIn(0f, 1f),
            restAlpha = restAlpha.value.coerceIn(0f, 1f),
            nudge = nudge.value,
            stretch = stretch,
            glint = glint,
            glintAngle = glintAngle,
            spreadBoost = spreadBoost,
            squash = squash,
            clockMillis = clockMillis,
            landedAtMillis = landedAtMillis,
            completion = completion(),
            isDragging = phase == Phase.Pulling || phase == Phase.BackDrag,
            finger = GlassDiscOnboardingFingerFrame(fingerX, fingerY, fingerAlpha),
            bubbles = simulation.snapshot(),
            pressureRings = pressureRings(discRadius.value),
        )
    }

    private fun updateAnimations(elapsedSeconds: Float) {
        discX.update(elapsedSeconds)
        discY.update(elapsedSeconds)
        discRadius.update(elapsedSeconds)
        ring.update(elapsedSeconds)
        reveal.update(elapsedSeconds)
        textureMode.update(elapsedSeconds)
        introAlpha.update(elapsedSeconds)
        hintAlpha.update(elapsedSeconds)
        headlineAlpha.update(elapsedSeconds)
        restAlpha.update(elapsedSeconds)
        nudge.update(elapsedSeconds)
    }

    private fun updateLanding(elapsedMillis: Float) {
        if (landingDelayMillis < 0f) {
            return
        }
        landingDelayMillis -= elapsedMillis
        if (landingDelayMillis > 0f) {
            return
        }
        landingDelayMillis = -1f
        if (phase != Phase.Landing) {
            return
        }
        emitHaptic(GlassDiscOnboardingHaptic.Land, dragScripted)
        land()
    }

    private fun updateMaterial(elapsedMillis: Float) {
        val currentY = discY.value
        val velocityY = if (elapsedMillis > 0f) {
            (currentY - lastDiscY) / elapsedMillis * MillisPerSecond
        } else {
            0f
        }
        lastDiscY = currentY
        val targetStretch = if (reduceMotion) {
            0f
        } else {
            min(
                GlassDiscOnboardingMotion.UPGRADE.stretchMax,
                abs(velocityY / density) * GlassDiscOnboardingMotion.UPGRADE.stretchPerSpeed,
            )
        }
        stretch += (targetStretch - stretch) * GlassDiscOnboardingMotion.UPGRADE.stretchFollow
        spreadBoost = if (reduceMotion) {
            0f
        } else {
            min(
                GlassDiscOnboardingMotion.UPGRADE.dispersionMax,
                abs(velocityY / density) * GlassDiscOnboardingMotion.UPGRADE.dispersionPerSpeed,
            )
        }
        updateGlint()
        updateSquash()
    }

    private fun updateGlint() {
        var glintTime = clockMillis - glintAtMillis
        if ((phase == Phase.Intro || phase == Phase.Community) && glintTime > GlassDiscOnboardingMotion.UPGRADE.glintMs) {
            glintTime = clockMillis % GlassDiscOnboardingMotion.UPGRADE.glintEveryMs
        }
        if (
            !reduceMotion &&
            glintTime >= 0f &&
            glintTime < GlassDiscOnboardingMotion.UPGRADE.glintMs &&
            (phase == Phase.Intro || phase == Phase.Landing || phase == Phase.Community)
        ) {
            val progress = glintTime / GlassDiscOnboardingMotion.UPGRADE.glintMs
            glint = sin(PI.toFloat() * progress)
            glintAngle = GlassDiscOnboardingMotion.UPGRADE.glintFrom +
                (GlassDiscOnboardingMotion.UPGRADE.glintTo - GlassDiscOnboardingMotion.UPGRADE.glintFrom) *
                (1f - (1f - progress).pow(3))
            return
        }
        glint = 0f
    }

    private fun updateSquash() {
        if (reduceMotion) {
            squash = 0f
            return
        }
        var total = 0f
        for (pulse in GlassDiscOnboardingMotion.UPGRADE.pulses) {
            val progress = (
                clockMillis - landedAtMillis - GlassDiscOnboardingMotion.BURST.delayMs - pulse
                ) / GlassDiscOnboardingMotion.UPGRADE.pulseMs
            if (progress > 0f && progress < 1f) {
                total += sin(PI.toFloat() * min(1f, progress * 3f)) * (1f - progress)
            }
        }
        squash = min(1f, total) * GlassDiscOnboardingMotion.UPGRADE.pulseSquash
    }

    private fun place(pullPx: Float) {
        val thresholdPx = dp(GlassDiscOnboardingMotion.PULL.threshold)
        val rubberPx = dp(GlassDiscOnboardingMotion.PULL.rubber)
        val progress = min(1f, pullPx / thresholdPx)
        val over = max(0f, pullPx - thresholdPx)
        val extra = rubberPx * over / (over + rubberPx)
        discX.snapTo(widthPx * curve(GlassDiscOnboardingTheme.LAYOUT.pullT, GlassDiscOnboardingTheme.LAYOUT.pullX, progress))
        discRadius.snapTo(widthPx * curve(GlassDiscOnboardingTheme.LAYOUT.pullT, GlassDiscOnboardingTheme.LAYOUT.pullR, progress))
        discY.snapTo(heightPx * curve(GlassDiscOnboardingTheme.LAYOUT.pullT, GlassDiscOnboardingTheme.LAYOUT.pullY, progress) - extra)
        val ringAmount = if (progress < GlassDiscOnboardingMotion.PULL.ringPeakAt) {
            smooth(progress / GlassDiscOnboardingMotion.PULL.ringPeakAt)
        } else {
            1f - smooth(
                (progress - GlassDiscOnboardingMotion.PULL.ringPeakAt) /
                    (GlassDiscOnboardingMotion.PULL.ringOutAt - GlassDiscOnboardingMotion.PULL.ringPeakAt),
            )
        }
        ring.snapTo(ringAmount)
        reveal.snapTo(
            smooth(
                (progress - GlassDiscOnboardingMotion.PULL.revealFrom) /
                    (GlassDiscOnboardingMotion.PULL.revealTo - GlassDiscOnboardingMotion.PULL.revealFrom),
            ),
        )
        introAlpha.snapTo(1f - smooth(progress / GlassDiscOnboardingMotion.PULL.introOutBy))
        hintAlpha.snapTo(1f - smooth(progress / GlassDiscOnboardingMotion.PULL.hintOutBy))
    }

    private fun placeBack(pullPx: Float) {
        discY.snapTo(beadRestY() + pullPx)
        discRadius.snapTo(
            beadRestRadius() + (backRadius() - beadRestRadius()) *
                smooth(pullPx / dp(GlassDiscOnboardingMotion.BACK.growBy)),
        )
    }

    private fun touchBegin(x: Float, y: Float, scripted: Boolean, nowMillis: Long) {
        when (phase) {
            Phase.Community -> {
                if (
                    hypot(x - widthPx / 2f, y - beadRestY()) <
                    beadRestRadius() + dp(GlassDiscOnboardingTokens.VIEW.beadTouchPadding)
                ) {
                    phase = Phase.BackDrag
                    dragStartY = y
                    dragPull = 0f
                    dragTicks = 0
                    dragScripted = scripted
                    return
                }
                fingerSampleX = x
                fingerSampleY = y
                fingerSampleMillis = nowMillis
                simulation.beginFinger(x, y)
            }
            Phase.Intro -> {
                if (y < discY.value - discRadius.value - dp(GlassDiscOnboardingTokens.VIEW.domeTouchPadding)) {
                    return
                }
                phase = Phase.Pulling
                textureMode.snapTo(0f)
                dragStartY = y
                dragPull = 0f
                dragTicks = 0
                dragScripted = scripted
                if (!reduceMotion) {
                    nudge.springTo(
                        target = dp(GlassDiscOnboardingMotion.UPGRADE.nudge),
                        durationMillis = GlassDiscOnboardingMotion.UPGRADE.nudgeSpring.duration,
                        dampingRatio = GlassDiscOnboardingMotion.UPGRADE.nudgeSpring.dampingRatio,
                    )
                }
            }
            else -> Unit
        }
    }

    private fun touchMove(x: Float, y: Float, nowMillis: Long) {
        when (phase) {
            Phase.Community -> {
                val elapsed = max(MinimumFingerMillis, nowMillis - fingerSampleMillis).toFloat() / MillisPerSecond
                val velocityX = (x - fingerSampleX) / elapsed
                val velocityY = (y - fingerSampleY) / elapsed
                fingerSampleX = x
                fingerSampleY = y
                fingerSampleMillis = nowMillis
                simulation.moveFinger(x, y, velocityX, velocityY)
            }
            Phase.Pulling -> {
                val pull = max(0f, dragStartY - y)
                val ticks = floor(
                    min(1f, pull / dp(GlassDiscOnboardingMotion.PULL.threshold)) /
                        GlassDiscOnboardingMotion.PULL.tickEvery,
                ).toInt()
                if (ticks > dragTicks) {
                    emitHaptic(GlassDiscOnboardingHaptic.Tick, dragScripted)
                }
                dragPull = pull
                dragTicks = max(ticks, dragTicks)
                place(pull)
            }
            Phase.BackDrag -> {
                val pull = max(0f, y - dragStartY)
                dragPull = pull
                placeBack(pull)
            }
            else -> Unit
        }
    }

    private fun touchEnd(velocityY: Float, cancelled: Boolean) {
        when (phase) {
            Phase.Community -> simulation.endFinger()
            Phase.Pulling -> finishPull(velocityY, cancelled)
            Phase.BackDrag -> finishBackDrag(velocityY, cancelled)
            else -> Unit
        }
    }

    private fun finishPull(velocityY: Float, cancelled: Boolean) {
        val progress = dragPull / dp(GlassDiscOnboardingMotion.PULL.threshold)
        val velocityDp = velocityY / density
        if (
            !cancelled &&
            (
                progress >= GlassDiscOnboardingMotion.PULL.commitAt ||
                    velocityDp < GlassDiscOnboardingMotion.PULL.flickVelocity && progress > GlassDiscOnboardingMotion.PULL.flickMin
                )
        ) {
            commit(dragScripted, velocityY)
            return
        }
        phase = Phase.Intro
        nudge.springTo(
            target = 0f,
            durationMillis = GlassDiscOnboardingMotion.UPGRADE.nudgeSpring.duration,
            dampingRatio = GlassDiscOnboardingMotion.UPGRADE.nudgeSpring.dampingRatio,
            velocity = -velocityY,
        )
        discX.springTo(
            target = widthPx / 2f,
            durationMillis = GlassDiscOnboardingMotion.SPRING.back.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.back.dampingRatio,
        )
        discY.springTo(
            target = domeY(),
            durationMillis = GlassDiscOnboardingMotion.SPRING.back.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.back.dampingRatio,
            velocity = velocityY,
        )
        discRadius.springTo(
            target = domeRadius(),
            durationMillis = GlassDiscOnboardingMotion.SPRING.back.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.back.dampingRatio,
        )
        ring.tweenTo(0f, GlassDiscOnboardingMotion.FADE.settle)
        reveal.tweenTo(0f, GlassDiscOnboardingMotion.FADE.settle)
        introAlpha.tweenTo(1f, GlassDiscOnboardingMotion.FADE.settle)
        hintAlpha.tweenTo(1f, GlassDiscOnboardingMotion.FADE.settle)
    }

    private fun finishBackDrag(velocityY: Float, cancelled: Boolean) {
        val velocityDp = velocityY / density
        if (
            !cancelled &&
            (
                dragPull >= dp(GlassDiscOnboardingMotion.BACK.commitBy) ||
                    velocityDp > GlassDiscOnboardingMotion.BACK.flickVelocity && dragPull > dp(GlassDiscOnboardingMotion.BACK.flickMin)
                )
        ) {
            rewind(dragScripted, velocityY)
            return
        }
        phase = Phase.Community
        discY.springTo(
            target = beadRestY(),
            durationMillis = GlassDiscOnboardingMotion.SPRING.bead.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.bead.dampingRatio,
            velocity = velocityY,
        )
        discRadius.springTo(
            target = beadRestRadius(),
            durationMillis = GlassDiscOnboardingMotion.SPRING.bead.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.bead.dampingRatio,
        )
    }

    private fun commit(scripted: Boolean, velocityY: Float) {
        phase = Phase.Landing
        stage = GlassDiscOnboardingStage.Moving
        dragScripted = scripted
        emitHaptic(GlassDiscOnboardingHaptic.Commit, scripted)
        nudge.springTo(
            target = 0f,
            durationMillis = GlassDiscOnboardingMotion.UPGRADE.nudgeSpring.duration,
            dampingRatio = GlassDiscOnboardingMotion.UPGRADE.nudgeSpring.dampingRatio,
        )
        glintAtMillis = clockMillis
        ring.tweenTo(0f, GlassDiscOnboardingMotion.FADE.settle)
        introAlpha.tweenTo(0f, GlassDiscOnboardingMotion.FADE.settle)
        hintAlpha.snapTo(0f)
        if (reduceMotion) {
            reveal.snapTo(0f)
            discX.snapTo(widthPx / 2f)
            discY.snapTo(beadY())
            discRadius.snapTo(beadRadius())
            land()
            return
        }
        reveal.tweenTo(0f, GlassDiscOnboardingMotion.FADE.settle)
        discX.springTo(
            target = widthPx / 2f,
            durationMillis = GlassDiscOnboardingMotion.SPRING.bead.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.bead.dampingRatio,
        )
        discRadius.springTo(
            target = beadRadius(),
            durationMillis = GlassDiscOnboardingMotion.SPRING.bead.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.bead.dampingRatio,
        )
        discY.springTo(
            target = beadY(),
            durationMillis = GlassDiscOnboardingMotion.SPRING.bead.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.bead.dampingRatio,
            velocity = velocityY,
        )
        landingDelayMillis = (GlassDiscOnboardingMotion.FADE.settle + LandingThresholdDelayMillis).toFloat()
    }

    private fun land() {
        landedAtMillis = clockMillis
        textureMode.snapTo(1f)
        reveal.snapTo(1f)
        val headlineDuration = if (reduceMotion) {
            GlassDiscOnboardingMotion.FADE.reducedMs
        } else {
            GlassDiscOnboardingMotion.FADE.headlineMs
        }
        headlineAlpha.tweenTo(
            target = 1f,
            durationMillis = headlineDuration,
            easing = GlassDiscOnboardingAnimation.Easing.CubicOut,
        )
        restAlpha.tweenTo(
            target = 1f,
            durationMillis = if (reduceMotion) GlassDiscOnboardingMotion.FADE.reducedMs else GlassDiscOnboardingMotion.FADE.restMs,
            delayMillis = if (reduceMotion) 0 else GlassDiscOnboardingMotion.FADE.restDelay,
            easing = GlassDiscOnboardingAnimation.Easing.QuadOut,
            onComplete = ::toCommunity,
        )
        simulation.launch(widthPx / 2f, beadY(), reduceMotion)
        val riseDuration = if (reduceMotion) 0 else GlassDiscOnboardingMotion.RISE.ms
        discY.springTo(
            target = beadY(),
            durationMillis = GlassDiscOnboardingMotion.SPRING.bead.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.bead.dampingRatio,
            thenTweenTarget = beadRestY(),
            thenTweenMillis = riseDuration,
        )
        discRadius.springTo(
            target = beadRadius(),
            durationMillis = GlassDiscOnboardingMotion.SPRING.bead.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.bead.dampingRatio,
            thenTweenTarget = beadRestRadius(),
            thenTweenMillis = riseDuration,
        )
    }

    private fun rewind(scripted: Boolean, velocityY: Float) {
        if (phase == Phase.Rewinding) {
            return
        }
        phase = Phase.Rewinding
        stage = GlassDiscOnboardingStage.Moving
        dragScripted = scripted
        landingDelayMillis = -1f
        emitHaptic(GlassDiscOnboardingHaptic.Back, scripted)
        headlineAlpha.tweenTo(0f, GlassDiscOnboardingMotion.FADE.outMs)
        restAlpha.tweenTo(0f, GlassDiscOnboardingMotion.FADE.outMs)
        reveal.tweenTo(0f, GlassDiscOnboardingMotion.FADE.settle)
        introAlpha.tweenTo(
            target = 1f,
            durationMillis = GlassDiscOnboardingMotion.FADE.introBack,
            delayMillis = GlassDiscOnboardingMotion.FADE.outMs,
        )
        hintAlpha.tweenTo(
            target = 1f,
            durationMillis = GlassDiscOnboardingMotion.FADE.introBack,
            delayMillis = GlassDiscOnboardingMotion.FADE.outMs,
        )
        simulation.leave()
        if (reduceMotion) {
            discY.snapTo(domeY())
            discRadius.snapTo(domeRadius())
            toIntro()
            return
        }
        discRadius.springTo(
            target = domeRadius(),
            durationMillis = GlassDiscOnboardingMotion.SPRING.dome.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.dome.dampingRatio,
        )
        discY.springTo(
            target = domeY(),
            durationMillis = GlassDiscOnboardingMotion.SPRING.dome.duration,
            dampingRatio = GlassDiscOnboardingMotion.SPRING.dome.dampingRatio,
            velocity = velocityY,
            onComplete = ::toIntro,
        )
    }

    private fun toIntro() {
        phase = Phase.Intro
        textureMode.snapTo(0f)
        stage = GlassDiscOnboardingStage.Intro
    }

    private fun toCommunity() {
        if (phase != Phase.Landing) {
            return
        }
        phase = Phase.Community
        stage = GlassDiscOnboardingStage.Community
    }

    private fun pauseForRealTouch() {
        if (!scriptPaused && scriptSegment != 0) {
            touchEnd(0f, cancelled = true)
        }
        fingerAlpha = 0f
        scriptPaused = true
        scriptRealDown = true
        scriptQuietMillis = 0f
        scriptSegment = 0
        resumeReturning = false
    }

    private fun pauseAfterAction(nowMillis: Long) {
        scriptPaused = true
        scriptRealDown = false
        scriptQuietMillis = 0f
        scriptSegment = 0
        resumeReturning = false
        fingerAlpha = 0f
        fingerSampleMillis = nowMillis
    }

    private fun updatePausedScript(elapsedMillis: Float) {
        if (scriptRealDown) {
            scriptQuietMillis = 0f
            return
        }
        scriptQuietMillis += elapsedMillis
        if (scriptQuietMillis < ResumeAfterTouchMillis) {
            return
        }
        if (!resumeReturning) {
            resumeReturning = true
            when (phase) {
                Phase.Pulling -> touchEnd(0f, cancelled = true)
                Phase.BackDrag -> touchEnd(0f, cancelled = true)
                Phase.Landing, Phase.Community -> rewind(scripted = true, velocityY = 0f)
                else -> Unit
            }
        }
        if (phase == Phase.Intro && simulation.phase == GlassDiscOnboardingSimulation.Hidden) {
            resetLoopRest()
            scriptPaused = false
            scriptTimeMillis = 0f
            scriptQuietMillis = 0f
            scriptSegment = 0
            resumeReturning = false
        }
    }

    private fun runScript(elapsedMillis: Float, nowMillis: Long) {
        val time = scriptTimeMillis
        val segment = when {
            time >= GlassDiscOnboardingMotion.AUTOPLAY.pullStart && time < GlassDiscOnboardingMotion.AUTOPLAY.pullEnd -> 1
            time >= GlassDiscOnboardingMotion.AUTOPLAY.sweepStart && time < GlassDiscOnboardingMotion.AUTOPLAY.sweepEnd -> 2
            time >= GlassDiscOnboardingMotion.AUTOPLAY.backStart && time < GlassDiscOnboardingMotion.AUTOPLAY.backEnd -> 3
            else -> 0
        }
        if (segment != scriptSegment) {
            if (scriptSegment != 0) {
                val endTime = when (scriptSegment) {
                    1 -> GlassDiscOnboardingMotion.AUTOPLAY.pullEnd.toFloat()
                    2 -> GlassDiscOnboardingMotion.AUTOPLAY.sweepEnd.toFloat()
                    else -> GlassDiscOnboardingMotion.AUTOPLAY.backEnd.toFloat()
                }
                val end = scriptedPosition(scriptSegment, endTime)
                touchMove(end.first, end.second, nowMillis)
                touchEnd(0f, cancelled = false)
            }
            if (segment == 0) {
                fingerAlpha = 0f
            } else {
                val start = scriptedPosition(segment, time)
                fingerX = start.first
                fingerY = start.second
                fingerAlpha = 1f
                touchBegin(start.first, start.second, scripted = true, nowMillis = nowMillis)
            }
        }
        if (segment != 0) {
            val position = scriptedPosition(segment, time)
            fingerX = position.first
            fingerY = position.second
            fingerAlpha = 1f
            touchMove(position.first, position.second, nowMillis)
        }
        var next = if (
            GlassDiscOnboardingMotion.DEBUG_FREEZE_MS >= 0 &&
            time >= GlassDiscOnboardingMotion.DEBUG_FREEZE_MS
        ) {
            time
        } else {
            time + elapsedMillis
        }
        if (next >= GlassDiscOnboardingMotion.LOOP) {
            // Reset every animated clock as well as the state so frame P is byte-for-byte frame zero.
            resetLoopRest()
            next = 0f
        }
        scriptTimeMillis = next
        scriptSegment = if (next == 0f) 0 else segment
    }

    private fun scriptedPosition(segment: Int, timeMillis: Float): Pair<Float, Float> {
        if (segment == 1) {
            val progress = (
                timeMillis - GlassDiscOnboardingMotion.AUTOPLAY.pullStart
                ) / (GlassDiscOnboardingMotion.AUTOPLAY.pullEnd - GlassDiscOnboardingMotion.AUTOPLAY.pullStart)
            return widthPx * GlassDiscOnboardingMotion.AUTOPLAY.startX to
                heightPx * GlassDiscOnboardingMotion.AUTOPLAY.startY -
                dp(GlassDiscOnboardingMotion.AUTOPLAY.pullBy) * smooth(progress)
        }
        if (segment == 2) {
            val progress = smooth(
                (timeMillis - GlassDiscOnboardingMotion.AUTOPLAY.sweepStart) /
                    (GlassDiscOnboardingMotion.AUTOPLAY.sweepEnd - GlassDiscOnboardingMotion.AUTOPLAY.sweepStart),
            )
            return widthPx * (
                GlassDiscOnboardingTokens.VIEW.autoplaySweepStartX +
                    GlassDiscOnboardingTokens.VIEW.autoplaySweepWidth * progress
                ) to heightPx * (
                GlassDiscOnboardingTokens.VIEW.autoplaySweepY -
                    GlassDiscOnboardingTokens.VIEW.autoplaySweepArc * sin(progress * PI.toFloat())
                )
        }
        val progress = (
            timeMillis - GlassDiscOnboardingMotion.AUTOPLAY.backStart
            ) / (GlassDiscOnboardingMotion.AUTOPLAY.backEnd - GlassDiscOnboardingMotion.AUTOPLAY.backStart)
        return widthPx / 2f + dp(GlassDiscOnboardingTokens.VIEW.autoplayBackOffsetX) to
            beadRestY() + dp(GlassDiscOnboardingMotion.AUTOPLAY.backBy) * smooth(progress)
    }

    private fun resetLoopRest() {
        phase = Phase.Intro
        stage = GlassDiscOnboardingStage.Intro
        discX.snapTo(widthPx / 2f)
        discY.snapTo(domeY())
        discRadius.snapTo(domeRadius())
        ring.snapTo(0f)
        reveal.snapTo(0f)
        textureMode.snapTo(0f)
        introAlpha.snapTo(1f)
        hintAlpha.snapTo(1f)
        headlineAlpha.snapTo(0f)
        restAlpha.snapTo(0f)
        nudge.snapTo(0f)
        stretch = 0f
        glint = 0f
        glintAngle = 0f
        spreadBoost = 0f
        squash = 0f
        clockMillis = 0f
        landedAtMillis = FarPast
        glintAtMillis = FarPast
        lastDiscY = discY.value
        dragPull = 0f
        dragTicks = 0
        landingDelayMillis = -1f
        fingerX = 0f
        fingerY = 0f
        fingerAlpha = 0f
        simulation.hide()
    }

    private fun showReducedMotionFinal() {
        phase = Phase.Community
        stage = GlassDiscOnboardingStage.Community
        discX.snapTo(widthPx / 2f)
        discY.snapTo(beadRestY())
        discRadius.snapTo(beadRestRadius())
        ring.snapTo(0f)
        reveal.snapTo(1f)
        textureMode.snapTo(1f)
        introAlpha.snapTo(0f)
        hintAlpha.snapTo(0f)
        headlineAlpha.snapTo(1f)
        restAlpha.snapTo(1f)
        nudge.snapTo(0f)
        stretch = 0f
        glint = 0f
        spreadBoost = 0f
        squash = 0f
        landingDelayMillis = -1f
        fingerAlpha = 0f
        scriptPaused = true
        scriptRealDown = false
        scriptSegment = 0
        simulation.showFinal()
    }

    private fun pressureRings(radius: Float): List<GlassDiscOnboardingPressureRingFrame> {
        if (reduceMotion) {
            return emptyList()
        }
        val sinceBurst = clockMillis - landedAtMillis - GlassDiscOnboardingMotion.BURST.delayMs
        return buildList {
            for (delay in GlassDiscOnboardingMotion.UPGRADE.pulses) {
                val progress = (sinceBurst - delay.toFloat()) /
                    GlassDiscOnboardingMotion.UPGRADE.pulseMs.toFloat()
            if (progress <= 0f || progress >= 1f) {
                    continue
            }
            val eased = 1f - (1f - progress).pow(3)
                add(
                    GlassDiscOnboardingPressureRingFrame(
                        radius = radius + dp(GlassDiscOnboardingMotion.UPGRADE.pulseGrow) * eased,
                        strokeWidth = dp(
                            GlassDiscOnboardingTokens.VIEW.pressureStrokeStart * (1f - progress) +
                                GlassDiscOnboardingTokens.VIEW.pressureStrokeEnd,
                        ),
                        alpha = GlassDiscOnboardingTokens.VIEW.pressureAlpha * (1f - progress),
                    ),
                )
            }
        }
    }

    private fun completion(): Float {
        return when (phase) {
            Phase.Intro -> 0f
            Phase.Pulling -> min(1f, dragPull / dp(GlassDiscOnboardingMotion.PULL.threshold))
            Phase.Landing, Phase.Community -> 1f
            Phase.BackDrag -> 1f - min(1f, dragPull / dp(GlassDiscOnboardingMotion.BACK.commitBy))
            Phase.Rewinding -> introAlpha.value.coerceIn(0f, 1f).let { 1f - it }
        }
    }

    private fun emitHaptic(haptic: GlassDiscOnboardingHaptic, scripted: Boolean) {
        if (!scripted) {
            onHaptic(haptic)
        }
    }

    private fun domeRadius(): Float = widthPx * GlassDiscOnboardingTheme.LAYOUT.domeRadius

    private fun domeY(): Float = heightPx * GlassDiscOnboardingTheme.LAYOUT.domeTop + domeRadius()

    private fun beadY(): Float = heightPx * GlassDiscOnboardingTheme.LAYOUT.beadY

    private fun beadRestY(): Float = heightPx * GlassDiscOnboardingTheme.LAYOUT.beadRestY

    private fun beadRadius(): Float = dp(GlassDiscOnboardingTheme.LAYOUT.beadRadius)

    private fun beadRestRadius(): Float = dp(GlassDiscOnboardingTheme.LAYOUT.beadRestRadius)

    private fun backRadius(): Float = widthPx * GlassDiscOnboardingTheme.LAYOUT.backRadius

    private fun dp(value: Float): Float = value * density

    private enum class Phase(val code: Int) {
        Intro(0),
        Pulling(1),
        Landing(2),
        Community(3),
        BackDrag(4),
        Rewinding(5),
    }

    companion object {
        /** The reference retains 3500 ms; product behavior explicitly resumes after 2000 ms. */
        const val ResumeAfterTouchMillis = GlassDiscOnboardingTokens.PORT.resumeAfterTouchMillis
        private const val MillisPerSecond = 1000f
        private const val MaximumFrameSeconds = GlassDiscOnboardingTokens.PORT.maximumFrameSeconds
        private const val MinimumDensity = 0.1f
        private const val MinimumFingerMillis = 8L
        private const val LandingThresholdDelayMillis = 160
        private const val FarPast = -1_000_000_000f

        private fun smooth(value: Float): Float {
            val clamped = value.coerceIn(0f, 1f)
            return clamped * clamped * (3f - 2f * clamped)
        }

        private fun curve(times: FloatArray, values: FloatArray, time: Float): Float {
            if (time <= times.first()) {
                return values.first()
            }
            if (time >= times.last()) {
                return values.last()
            }
            var index = 0
            while (index < times.size - 2 && time > times[index + 1]) {
                index += 1
            }
            val progress = (time - times[index]) / (times[index + 1] - times[index])
            val first = values[max(0, index - 1)]
            val second = values[index]
            val third = values[index + 1]
            val fourth = values[min(values.lastIndex, index + 2)]
            return 0.5f * (
                2f * second +
                    (-first + third) * progress +
                    (2f * first - 5f * second + 4f * third - fourth) * progress * progress +
                    (-first + 3f * second - 3f * third + fourth) * progress * progress * progress
                )
        }
    }
}
