package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class HoldDragResizeSimulation(
    private val autoPlay: Boolean,
    var reducedMotion: Boolean,
) {
    var height = Tokens.sizes[0]
        private set
    var lag = Tokens.sizes[0]
        private set
    var held = 0.0
        private set
    var press = 0.0
        private set
    var clock = 0.0
        private set
    var coach = 1.0
        private set
    var coachProgress = 0.0
        private set
    var ghostX = 0.0
        private set
    var ghostY = 0.0
        private set
    var ghostOpacity = 0.0
        private set
    var ghostPress = 0.0
        private set
    var playing = true
        private set
    var near = 0
        private set
    var sizeIndex = 0
        private set
    var coachOn = true
        private set
    var touching = false
        private set
    var pressedControl = -1
        private set
    val events = mutableListOf<Haptic>()

    private var loopTime = 0.0
    private var quietTime = 0.0
    private var tidyTime = -1.0
    private var manualCoachTime = 0.0
    private var paused = !autoPlay
    private var userHeld = false
    private var startHeight = Tokens.sizes[0]
    private var heightVelocity = 0.0
    private var heldVelocity = 0.0
    private var heightTarget = Tokens.sizes[0]
    private var heightSpring = false
    private var heldTarget = 0.0
    private var reducedSnapTime = -1.0
    private var reducedSnapFrom = Tokens.sizes[0]
    private var controlPressProgress = 0.0
    private var controlPressTarget = 0.0

    val shown: Double
        get() = height + Tokens.Demo.coachGrow * coachProgress * coach

    enum class Haptic {
        Lift,
        Detent,
        Tap,
        Land,
    }

    fun step(delta: Double, width: Double, previewCentreY: Double) {
        val dt = min(max(delta, 0.0), Tokens.Timing.frameDeltaMax)
        if (Tokens.QA_STATE >= 0) {
            applyScript(Tokens.QA_T, width, previewCentreY)
            updateLag(dt)
            return
        }
        if (!autoPlay) {
            if (playing) {
                clock = (clock + dt) % (Tokens.LOOP_MS / 1000.0)
            }
            if (coachOn && !reducedMotion) {
                manualCoachTime = (manualCoachTime + dt) % 2.0
                coachProgress = when {
                    manualCoachTime < 0.7 -> ease(manualCoachTime / 0.7)
                    manualCoachTime < 1.5 -> 1.0 - ease((manualCoachTime - 0.7) / 0.8)
                    else -> 0.0
                }
            }
            stepHeightSpring(dt)
            stepHeldSpring(dt)
            stepControlPress(dt)
            updateLag(dt)
            return
        }
        if (paused) {
            stepPaused(dt)
        } else {
            val next = loopTime + dt / Tokens.QA_SLOWMO
            if (next >= Tokens.LOOP_MS / 1000.0) {
                resetLoop()
            } else {
                loopTime = next
                applyScript(loopTime, width, previewCentreY)
            }
        }
        stepHeightSpring(dt)
        stepHeldSpring(dt)
        stepControlPress(dt)
        updateLag(dt)
    }

    fun touchStart() {
        touching = true
        quietTime = 0.0
        tidyTime = -1.0
        if (!paused) {
            paused = true
            ghostOpacity = 0.0
            heldTarget = 0.0
            press = 0.0
        }
    }

    fun touchEnd() {
        touching = false
        quietTime = 0.0
    }

    fun pressPreview() {
        press = 1.0
    }

    fun cancelPreviewPress() {
        press = 0.0
    }

    fun beginHold(silent: Boolean = false) {
        heightSpring = false
        reducedSnapTime = -1.0
        startHeight = height
        userHeld = true
        heldTarget = if (reducedMotion) 0.0 else 1.0
        if (!silent) {
            events += Haptic.Lift
        }
    }

    fun moveHold(dy: Double, silent: Boolean = false) {
        if (!userHeld) {
            return
        }
        val before = height
        var next = rubber(startHeight + dy * Tokens.Resize.gain)
        Tokens.sizes.forEach { size ->
            val distance = size - next
            if (abs(distance) < Tokens.Upgrade.magnet) {
                val pull = 1.0 - abs(distance) / Tokens.Upgrade.magnet
                next += distance * Tokens.Upgrade.magnetPull * pull
            }
        }
        height = next
        near = nearest(next)
        if (!silent) {
            Tokens.sizes.forEach { size ->
                if ((before - size) * (next - size) < 0.0) {
                    events += Haptic.Detent
                }
            }
        }
    }

    fun endHold(velocityY: Double, silent: Boolean = false) {
        if (!userHeld) {
            return
        }
        userHeld = false
        heldTarget = 0.0
        press = 0.0
        val velocity = velocityY * Tokens.Resize.gain
        val target = nearest(height + velocity * Tokens.Resize.project)
        snapTo(target, velocity)
        if (!silent) {
            events += Haptic.Land
        }
    }

    fun tapPreview() {
        playing = !playing
        events += Haptic.Tap
    }

    fun dismissCoach() {
        coach = 0.0
        coachProgress = 0.0
        coachOn = false
    }

    fun stepSize(delta: Int) {
        val target = (nearest(height) + delta).coerceIn(0, Tokens.sizes.lastIndex)
        snapTo(target, 0.0)
    }

    fun collapse() {
        snapTo(0, 0.0)
    }

    fun controlDown(id: Int) {
        pressedControl = id
        controlPressTarget = 1.0
    }

    fun controlUp() {
        controlPressTarget = 0.0
    }

    fun controlScale(id: Int): Double {
        if (id != pressedControl || reducedMotion) {
            return 1.0
        }
        val eased = 1.0 - (1.0 - controlPressProgress) * (1.0 - controlPressProgress) * (1.0 - controlPressProgress)
        return 1.0 - (1.0 - Tokens.Upgrade.buttonPress) * eased
    }

    fun controlOpacity(id: Int): Double {
        if (id != pressedControl || !reducedMotion) {
            return 1.0
        }
        return 1.0 - 0.3 * controlPressProgress
    }

    private fun stepPaused(dt: Double) {
        if (!touching) {
            quietTime += dt
        }
        if (tidyTime < 0.0 && quietTime >= Tokens.RESUME_MS / 1000.0 && !userHeld) {
            tidyTime = 0.0
            reducedSnapFrom = height
            coachOn = true
            clock = 0.0
        }
        if (tidyTime >= 0.0) {
            tidyTime += dt
            val progress = (tidyTime / Tokens.Demo.tidy).coerceIn(0.0, 1.0)
            height = mix(reducedSnapFrom, Tokens.sizes[0], ease(progress))
            coach = ease(progress)
            coachProgress = 0.0
            held = 0.0
            heldTarget = 0.0
            if (progress >= 1.0) {
                resetLoop()
                paused = false
                tidyTime = -1.0
            }
        }
    }

    private fun applyScript(time: Double, width: Double, previewCentreY: Double) {
        clock = time
        coach = when {
            time < Tokens.Demo.gotItTap -> 1.0
            time < Tokens.Demo.gotItTap + Tokens.Timing.coachFade / 1000.0 -> {
                1.0 - ease((time - Tokens.Demo.gotItTap) / (Tokens.Timing.coachFade / 1000.0))
            }
            time < Tokens.Demo.coachBack -> 0.0
            else -> ease((time - Tokens.Demo.coachBack) / (Tokens.LOOP_MS / 1000.0 - Tokens.Demo.coachBack))
        }
        coachOn = coach > 0.001
        coachProgress = when {
            time < Tokens.Demo.coachDown -> 0.0
            time < Tokens.Demo.coachBottom -> ease(segment(time, Tokens.Demo.coachDown, Tokens.Demo.coachBottom))
            time < Tokens.Demo.coachUp -> 1.0 - ease(segment(time, Tokens.Demo.coachBottom, Tokens.Demo.coachUp))
            else -> 0.0
        }
        height = scriptedHeight(time)
        near = nearest(height)
        sizeIndex = near
        heldTarget = if (
            (time >= Tokens.Demo.growLifted && time < Tokens.Demo.growUp) ||
            (time >= Tokens.Demo.shrinkLifted && time < Tokens.Demo.shrinkUp)
        ) {
            if (reducedMotion) 0.0 else 1.0
        } else {
            0.0
        }
        val ghost = scriptedGhost(time, width, previewCentreY)
        ghostX = ghost.x
        ghostY = ghost.y
        ghostOpacity = ghost.opacity
        ghostPress = ghost.press
    }

    private fun scriptedHeight(time: Double): Double {
        val compact = Tokens.sizes[0]
        val large = Tokens.sizes[1]
        val full = Tokens.sizes[2]
        return when {
            time < Tokens.Demo.growLifted -> compact
            time < Tokens.Demo.growMid -> mix(compact, large, ease(segment(time, Tokens.Demo.growLifted, Tokens.Demo.growMid)))
            time < Tokens.Demo.growHold -> large
            time < Tokens.Demo.growEnd -> mix(large, full, ease(segment(time, Tokens.Demo.growHold, Tokens.Demo.growEnd)))
            time < Tokens.Demo.shrinkLifted -> full
            time < Tokens.Demo.shrinkEnd -> mix(full, compact, ease(segment(time, Tokens.Demo.shrinkLifted, Tokens.Demo.shrinkEnd)))
            else -> compact
        }
    }

    private fun scriptedGhost(time: Double, width: Double, previewCentreY: Double): Ghost {
        val centreX = width / 2.0
        if (time in Tokens.Demo.gotItShow..(Tokens.Demo.gotItTap + Tokens.Timing.ghostRelease / 1000.0)) {
            val opacity = envelope(time, Tokens.Demo.gotItShow, Tokens.Demo.gotItTap, Tokens.Timing.ghostRelease / 1000.0)
            val pressed = segment(time, Tokens.Demo.gotItShow, Tokens.Demo.gotItTap)
            return Ghost(
                Tokens.Coach.buttonX + Tokens.Coach.buttonWidth / 2.0,
                Tokens.Coach.buttonY + Tokens.Coach.buttonHeight / 2.0,
                opacity,
                if (time <= Tokens.Demo.gotItTap) pressed else 1.0 - segment(time, Tokens.Demo.gotItTap, Tokens.Demo.gotItTap + 0.12),
            )
        }
        val growRelease = Tokens.Demo.growUp + Tokens.Timing.ghostRelease / 1000.0
        if (time in Tokens.Demo.growDown..growRelease) {
            val progress = growProgress(time)
            val travel = (Tokens.sizes[2] - Tokens.sizes[0]) / Tokens.Resize.gain * progress
            val opacity = envelope(time, Tokens.Demo.growDown, Tokens.Demo.growUp, Tokens.Timing.ghostRelease / 1000.0)
            val pressed = if (time <= Tokens.Demo.growLifted) {
                segment(time, Tokens.Demo.growDown, Tokens.Demo.growLifted)
            } else if (time <= Tokens.Demo.growUp) {
                1.0
            } else {
                1.0 - segment(time, Tokens.Demo.growUp, growRelease)
            }
            return Ghost(centreX, previewCentreY + travel, opacity, pressed)
        }
        val shrinkRelease = Tokens.Demo.shrinkUp + Tokens.Timing.ghostRelease / 1000.0
        if (time in Tokens.Demo.shrinkDown..shrinkRelease) {
            val progress = if (time < Tokens.Demo.shrinkLifted) 0.0 else ease(segment(time, Tokens.Demo.shrinkLifted, Tokens.Demo.shrinkEnd))
            val fullCentre = previewCentreY + (Tokens.sizes[2] - Tokens.sizes[0]) / Tokens.Resize.gain
            val travel = (Tokens.sizes[2] - Tokens.sizes[0]) / Tokens.Resize.gain * progress
            val opacity = envelope(time, Tokens.Demo.shrinkDown, Tokens.Demo.shrinkUp, Tokens.Timing.ghostRelease / 1000.0)
            val pressed = if (time <= Tokens.Demo.shrinkLifted) {
                segment(time, Tokens.Demo.shrinkDown, Tokens.Demo.shrinkLifted)
            } else if (time <= Tokens.Demo.shrinkUp) {
                1.0
            } else {
                1.0 - segment(time, Tokens.Demo.shrinkUp, shrinkRelease)
            }
            return Ghost(centreX, fullCentre - travel, opacity, pressed)
        }
        return Ghost(0.0, 0.0, 0.0, 0.0)
    }

    private fun growProgress(time: Double): Double {
        val middle = (Tokens.sizes[1] - Tokens.sizes[0]) / (Tokens.sizes[2] - Tokens.sizes[0])
        return when {
            time < Tokens.Demo.growLifted -> 0.0
            time < Tokens.Demo.growMid -> middle * ease(segment(time, Tokens.Demo.growLifted, Tokens.Demo.growMid))
            time < Tokens.Demo.growHold -> middle
            else -> middle + (1.0 - middle) * ease(segment(time, Tokens.Demo.growHold, Tokens.Demo.growEnd))
        }
    }

    private fun snapTo(index: Int, velocity: Double) {
        sizeIndex = index
        near = index
        heightTarget = Tokens.sizes[index]
        if (reducedMotion) {
            reducedSnapFrom = height
            reducedSnapTime = 0.0
            heightSpring = false
        } else {
            heightVelocity = velocity
            heightSpring = true
            reducedSnapTime = -1.0
        }
    }

    private fun stepHeightSpring(dt: Double) {
        if (reducedSnapTime >= 0.0) {
            reducedSnapTime += dt
            val progress = (reducedSnapTime / (Tokens.Timing.reduced / 1000.0)).coerceIn(0.0, 1.0)
            height = mix(reducedSnapFrom, heightTarget, progress)
            if (progress >= 1.0) {
                reducedSnapTime = -1.0
            }
            return
        }
        if (!heightSpring || userHeld || !paused) {
            return
        }
        var remaining = dt
        while (remaining > 0.0) {
            val step = min(remaining, 1.0 / 120.0)
            val acceleration = (-Tokens.Spring.snapStiffness * (height - heightTarget) - Tokens.Spring.snapDamping * heightVelocity) / Tokens.Spring.snapMass
            heightVelocity += acceleration * step
            height += heightVelocity * step
            remaining -= step
        }
        if (abs(height - heightTarget) < 0.05 && abs(heightVelocity) < 0.05) {
            height = heightTarget
            heightVelocity = 0.0
            heightSpring = false
        }
    }

    private fun stepHeldSpring(dt: Double) {
        if (reducedMotion) {
            held = 0.0
            heldVelocity = 0.0
            return
        }
        val stiffness = if (heldTarget > held) Tokens.Spring.liftStiffness else Tokens.Spring.dropStiffness
        val damping = if (heldTarget > held) Tokens.Spring.liftDamping else Tokens.Spring.dropDamping
        val mass = if (heldTarget > held) Tokens.Spring.liftMass else Tokens.Spring.dropMass
        var remaining = dt
        while (remaining > 0.0) {
            val step = min(remaining, 1.0 / 120.0)
            val acceleration = (-stiffness * (held - heldTarget) - damping * heldVelocity) / mass
            heldVelocity += acceleration * step
            held += heldVelocity * step
            remaining -= step
        }
        if (abs(held - heldTarget) < 0.001 && abs(heldVelocity) < 0.001) {
            held = heldTarget
            heldVelocity = 0.0
        }
    }

    private fun updateLag(dt: Double) {
        lag = if (reducedMotion) {
            shown
        } else {
            lag + (shown - lag) * (1.0 - exp(-Tokens.Upgrade.follow * dt))
        }
    }

    private fun stepControlPress(dt: Double) {
        val duration = if (controlPressTarget > controlPressProgress) {
            Tokens.Upgrade.pressInMs / 1000.0
        } else {
            Tokens.Upgrade.pressOutMs / 1000.0
        }
        val direction = if (controlPressTarget > controlPressProgress) 1.0 else -1.0
        if (controlPressProgress != controlPressTarget) {
            controlPressProgress = (controlPressProgress + direction * dt / duration).coerceIn(0.0, 1.0)
        }
        if (controlPressProgress == 0.0 && controlPressTarget == 0.0) {
            pressedControl = -1
        }
    }

    private fun resetLoop() {
        loopTime = 0.0
        height = Tokens.sizes[0]
        heightTarget = height
        heightVelocity = 0.0
        heightSpring = false
        lag = height
        held = 0.0
        heldTarget = 0.0
        heldVelocity = 0.0
        press = 0.0
        clock = 0.0
        coach = 1.0
        coachProgress = 0.0
        coachOn = true
        ghostOpacity = 0.0
        ghostPress = 0.0
        near = 0
        sizeIndex = 0
        quietTime = 0.0
        pressedControl = -1
        controlPressProgress = 0.0
        controlPressTarget = 0.0
    }

    private fun rubber(value: Double): Double {
        val minimum = Tokens.sizes.first()
        val maximum = Tokens.sizes.last()
        if (value < minimum) {
            return minimum - min((minimum - value) * Tokens.Resize.rubber, Tokens.Resize.rubberMax)
        }
        if (value > maximum) {
            return maximum + min((value - maximum) * Tokens.Resize.rubber, Tokens.Resize.rubberMax)
        }
        return value
    }

    private fun nearest(value: Double): Int {
        var best = 0
        for (index in 1..Tokens.sizes.lastIndex) {
            if (abs(Tokens.sizes[index] - value) < abs(Tokens.sizes[best] - value)) {
                best = index
            }
        }
        return best
    }

    private fun ease(value: Double): Double {
        val progress = value.coerceIn(0.0, 1.0)
        return (1.0 - cos(PI * progress)) / 2.0
    }

    private fun segment(value: Double, start: Double, end: Double): Double {
        return ((value - start) / (end - start)).coerceIn(0.0, 1.0)
    }

    private fun envelope(value: Double, start: Double, end: Double, fade: Double): Double {
        return min(segment(value, start, start + Tokens.Demo.fingerFade), 1.0 - segment(value, end, end + fade))
    }

    private fun mix(start: Double, end: Double, progress: Double): Double {
        return start + (end - start) * progress
    }

    private data class Ghost(
        val x: Double,
        val y: Double,
        val opacity: Double,
        val press: Double,
    )
}
