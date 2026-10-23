package com.ngocdevv.nativecook.blocks.gestures.giftslotdrop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class GiftSlotDropPhase {
    Choosing,
    Pulling,
    Swiping,
    Inserting,
    Activating,
    Offer,
    Closing,
}

internal enum class GiftSlotDropHaptic {
    Select,
    Armed,
    Insert,
    Success,
    Light,
}

internal data class GiftSlotDropReleasePose(
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
)

internal data class GiftSlotDropConfetti(
    var x: Float = 0f,
    var y: Float = 0f,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f,
    var angle: Float = 0f,
    var spin: Float = 0f,
    var life: Float = 0f,
    var colour: Int = 0,
)

private class GiftSlotDropSpring(
    initial: Float,
) {
    var value = initial
    var velocity = 0f
    var target = initial
    var stiffness = Tokens.SPRINGS.getValue("snap").stiffness
    var damping = Tokens.SPRINGS.getValue("snap").damping
    var mass = Tokens.SPRINGS.getValue("snap").mass
    var active = false

    fun snap(next: Float) {
        value = next
        target = next
        velocity = 0f
        active = false
    }

    fun animateTo(next: Float, spring: Tokens.Spring, initialVelocity: Float = velocity) {
        target = next
        stiffness = spring.stiffness
        damping = spring.damping
        mass = spring.mass
        velocity = initialVelocity
        active = true
    }

    fun step(seconds: Float) {
        if (!active) {
            return
        }
        val displacement = value - target
        val acceleration = (-stiffness * displacement - damping * velocity) / mass
        velocity += acceleration * seconds
        value += velocity * seconds
        if (abs(value - target) < 0.001f && abs(velocity) < 0.01f) {
            snap(target)
        }
    }
}

internal data class GiftSlotDropMilestones(
    val sunk: Float,
    val printStart: Float,
    val printed: Float,
    val risen: Float,
)

internal fun giftSlotDropMilestones(reducedMotion: Boolean): GiftSlotDropMilestones {
    if (reducedMotion) {
        val sunk = Tokens.reducedMs.toFloat()
        val printStart = sunk + Tokens.activateMs
        val printed = printStart + Tokens.reducedMs
        return GiftSlotDropMilestones(sunk, printStart, printed, printed)
    }
    val sunk = Tokens.insertCentreMs + Tokens.insertSinkMs
    val printStart = sunk + Tokens.activateMs
    val printed = printStart + Tokens.printFeeds * (Tokens.printFeedMs + Tokens.printFeedGap)
    return GiftSlotDropMilestones(sunk.toFloat(), printStart.toFloat(), printed.toFloat(), printed + 700f)
}

internal class GiftSlotDropSimulation(
    private val onHaptic: (GiftSlotDropHaptic) -> Unit,
) {
    var width = Tokens.DESIGN_WIDTH
    var clockMs = 0f
        private set
    var phase = GiftSlotDropPhase.Choosing
        private set
    var selected = Tokens.START_INDEX
        private set
    var armed = false
        private set
    var releasedAt = -1f
        private set
    var releasedFrom = GiftSlotDropReleasePose()
        private set
    var closeAt = -1f
        private set

    private val offsetSpring = GiftSlotDropSpring(Tokens.START_INDEX.toFloat())
    private val pullXSpring = GiftSlotDropSpring(0f)
    private val pullYSpring = GiftSlotDropSpring(0f)
    private val liftSpring = GiftSlotDropSpring(1f)

    val offset: Float
        get() = offsetSpring.value
    val pullX: Float
        get() = pullXSpring.value
    val pullY: Float
        get() = pullYSpring.value
    val lift: Float
        get() = liftSpring.value

    val confetti = List(Tokens.printConfetti) { GiftSlotDropConfetti() }

    private var touchActive = false
    private var touchScripted = false
    private var touchMode = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchOffset = Tokens.START_INDEX.toFloat()
    private var touchOnCard = false
    private var confettiFired = false

    private var scriptOn = false
    private var scriptTimeMs = 0f
    private var quietMs = 0f
    private var realTouching = false

    fun start(autoPlay: Boolean) {
        scriptOn = autoPlay
        scriptTimeMs = 0f
        quietMs = 0f
        resetRest()
    }

    fun elapsedSinceRelease(): Float {
        if (releasedAt < 0f) {
            return -1f
        }
        return clockMs - releasedAt
    }

    fun takeOver() {
        if (scriptOn && touchScripted && touchActive) {
            setArmed(false)
            up(0f, 0f)
        }
        touchScripted = false
        scriptOn = false
        quietMs = 0f
        realTouching = true
    }

    fun letGo() {
        realTouching = false
        quietMs = 0f
    }

    fun down(x: Float, y: Float, scripted: Boolean) {
        if (phase != GiftSlotDropPhase.Choosing) {
            return
        }
        touchOnCard = abs(x - width / 2f) < Tokens.LAYOUT.cardW / 2f + Tokens.LAYOUT.cardHitSlop &&
            abs(y - Tokens.LAYOUT.cardY) < Tokens.LAYOUT.cardH / 2f + Tokens.LAYOUT.cardHitSlop
        touchActive = true
        touchScripted = scripted
        touchMode = 0
        touchStartX = x
        touchStartY = y
        touchOffset = offset
        offsetSpring.snap(offset)
        if (touchOnCard) {
            liftSpring.animateTo(Tokens.DRAG.lift, Tokens.SPRINGS.getValue("lift"))
        }
    }

    fun move(x: Float, y: Float) {
        if (!touchActive) {
            return
        }
        val dx = x - touchStartX
        val dy = y - touchStartY
        if (touchMode == 0 && hypot(dx, dy) > Tokens.DRAG.decide) {
            touchMode = if (abs(dx) > abs(dy) || !touchOnCard || dy < 0f) 2 else 1
            phase = if (touchMode == 1) GiftSlotDropPhase.Pulling else GiftSlotDropPhase.Swiping
            if (touchMode == 2) {
                liftSpring.animateTo(1f, Tokens.SPRINGS.getValue("lift"))
            }
        }
        if (touchMode == 2) {
            var next = touchOffset - dx / Tokens.LAYOUT.spacing
            if (next < 0f) {
                next *= 0.3f
            }
            val end = Tokens.GIFTS.lastIndex.toFloat()
            if (next > end) {
                next = end + (next - end) * 0.3f
            }
            offsetSpring.snap(next)
        } else if (touchMode == 1) {
            pullXSpring.snap(dx * 0.3f)
            pullYSpring.snap(if (dy < 0f) dy * Tokens.DRAG.upResist else dy)
            setArmed(dy >= Tokens.DRAG.commit)
        }
    }

    fun up(velocityX: Float, velocityY: Float) {
        if (!touchActive) {
            return
        }
        touchActive = false
        liftSpring.animateTo(1f, Tokens.SPRINGS.getValue("lift"))
        if (touchMode == 2) {
            val fling = if (abs(velocityX) > Tokens.DRAG.swipeVelocity) -sign(velocityX) * 0.5f else 0f
            snapTo((offset + fling).roundToInt(), -velocityX / Tokens.LAYOUT.spacing)
            phase = GiftSlotDropPhase.Choosing
            return
        }
        if (touchMode == 1 && armed) {
            setArmed(false)
            release()
            return
        }
        if (touchMode == 1) {
            pullXSpring.animateTo(0f, Tokens.SPRINGS.getValue("back"), velocityX)
            pullYSpring.animateTo(0f, Tokens.SPRINGS.getValue("back"), velocityY)
        }
        phase = GiftSlotDropPhase.Choosing
    }

    fun activate() {
        if (phase != GiftSlotDropPhase.Choosing) {
            return
        }
        touchScripted = false
        pullXSpring.snap(0f)
        pullYSpring.snap(0f)
        release()
    }

    fun stepGift(direction: Int) {
        if (phase == GiftSlotDropPhase.Choosing) {
            snapTo(selected + direction, 0f)
        }
    }

    fun close() {
        if (phase != GiftSlotDropPhase.Offer) {
            return
        }
        closeAt = clockMs
        phase = GiftSlotDropPhase.Closing
        haptic(GiftSlotDropHaptic.Light)
    }

    fun step(frameSeconds: Float, autoPlay: Boolean, reducedMotion: Boolean) {
        val seconds = min(Tokens.MAX_FRAME_SECONDS, max(0f, frameSeconds))
        val frameMs = seconds * 1000f
        clockMs += frameMs
        offsetSpring.step(seconds)
        pullXSpring.step(seconds)
        pullYSpring.step(seconds)
        liftSpring.step(seconds)
        advanceRelease(reducedMotion)
        advanceClose(reducedMotion)
        advanceConfetti(seconds, reducedMotion)
        if (autoPlay) {
            advanceAutoplay(frameMs)
        }
    }

    private fun release() {
        releasedFrom = GiftSlotDropReleasePose(pullX, pullY, pullScale(pullY) * lift)
        releasedAt = clockMs
        closeAt = -1f
        confettiFired = false
        phase = GiftSlotDropPhase.Inserting
        haptic(GiftSlotDropHaptic.Insert)
    }

    private fun snapTo(index: Int, velocity: Float) {
        val target = index.coerceIn(0, Tokens.GIFTS.lastIndex)
        offsetSpring.animateTo(target.toFloat(), Tokens.SPRINGS.getValue("snap"), velocity)
        if (target != selected) {
            selected = target
            haptic(GiftSlotDropHaptic.Select)
        }
    }

    private fun setArmed(next: Boolean) {
        if (armed == next) {
            return
        }
        armed = next
        if (next) {
            haptic(GiftSlotDropHaptic.Armed)
        }
    }

    private fun haptic(event: GiftSlotDropHaptic) {
        if (!touchScripted) {
            onHaptic(event)
        }
    }

    private fun advanceRelease(reducedMotion: Boolean) {
        if (releasedAt < 0f) {
            return
        }
        val elapsed = elapsedSinceRelease()
        val milestones = giftSlotDropMilestones(reducedMotion)
        if (phase == GiftSlotDropPhase.Inserting && elapsed >= milestones.sunk) {
            phase = GiftSlotDropPhase.Activating
        }
        if (phase == GiftSlotDropPhase.Activating && elapsed >= milestones.printStart) {
            phase = GiftSlotDropPhase.Offer
        }
        if (!confettiFired && elapsed >= milestones.printed) {
            confettiFired = true
            spawnConfetti(reducedMotion)
            haptic(GiftSlotDropHaptic.Success)
        }
    }

    private fun advanceClose(reducedMotion: Boolean) {
        if (closeAt < 0f) {
            return
        }
        val duration = if (reducedMotion) Tokens.reducedMs else Tokens.closeMs + Tokens.closeReturnMs
        if (clockMs - closeAt < duration) {
            return
        }
        closeAt = -1f
        releasedAt = -1f
        pullXSpring.snap(0f)
        pullYSpring.snap(0f)
        phase = GiftSlotDropPhase.Choosing
    }

    private fun spawnConfetti(reducedMotion: Boolean) {
        if (reducedMotion) {
            return
        }
        confetti.forEachIndexed { index, piece ->
            val angle = (-PI / 2 + (hash(index, 1) - 0.5f) * 1.9f).toFloat()
            val speed = 380f + hash(index, 2) * 520f
            piece.x = width / 2f + (hash(index, 3) - 0.5f) * Tokens.LAYOUT.slotW * 0.8f
            piece.y = Tokens.LAYOUT.slotY - 4f
            piece.velocityX = cos(angle) * speed
            piece.velocityY = sin(angle) * speed
            piece.angle = hash(index, 4) * PI.toFloat()
            piece.spin = (hash(index, 5) - 0.5f) * 18f
            piece.life = Tokens.printConfettiLife * (0.7f + 0.3f * hash(index, 6))
            piece.colour = floor(hash(index, 7) * 3f).toInt()
        }
    }

    private fun advanceConfetti(seconds: Float, reducedMotion: Boolean) {
        if (reducedMotion) {
            return
        }
        confetti.forEach { piece ->
            if (piece.life <= 0f) {
                return@forEach
            }
            piece.velocityY += Tokens.printGravity * seconds
            piece.velocityX *= 0.985f
            piece.x += piece.velocityX * seconds
            piece.y += piece.velocityY * seconds
            piece.angle += piece.spin * seconds
            piece.life -= seconds
        }
    }

    private fun advanceAutoplay(frameMs: Float) {
        if (!scriptOn) {
            quietMs = if (!realTouching) quietMs + frameMs else 0f
            if (quietMs >= Tokens.touchResumeAfterMs && phase == GiftSlotDropPhase.Offer && !realTouching) {
                close()
                return
            }
            if (quietMs >= Tokens.touchResumeAfterMs && selected != Tokens.START_INDEX) {
                snapTo(Tokens.START_INDEX, 0f)
                return
            }
            val atRest = phase == GiftSlotDropPhase.Choosing &&
                abs(offset - Tokens.START_INDEX) < 0.01f &&
                abs(pullX) < 0.01f &&
                abs(pullY) < 0.01f
            if (quietMs >= Tokens.touchResumeAfterMs && atRest) {
                scriptOn = true
                scriptTimeMs = 0f
                quietMs = 0f
                clockMs = 0f
            }
            return
        }

        val previous = scriptTimeMs
        val held = Tokens.QA_HOLD_AT
        val next = if (held != null && previous >= held) previous else previous + frameMs
        Tokens.scriptSwipes.forEach { swipe ->
            runSwipe(previous, next, swipe)
        }
        if (previous < Tokens.scriptPullDown && next >= Tokens.scriptPullDown) {
            down(width / 2f + 12f, Tokens.LAYOUT.cardY + 30f, true)
        }
        if (next > Tokens.scriptPullDown && previous < Tokens.scriptPullUp) {
            val duration = Tokens.scriptPullUp - Tokens.scriptPullDown - 220f
            val progress = ease((next - Tokens.scriptPullDown - 120f) / duration)
            move(
                width / 2f + 12f - 23f * progress,
                Tokens.LAYOUT.cardY + 30f + Tokens.scriptPullBy * progress,
            )
        }
        if (previous < Tokens.scriptPullUp && next >= Tokens.scriptPullUp) {
            up(0f, 0f)
        }
        if (previous < Tokens.scriptDone && next >= Tokens.scriptDone) {
            close()
        }
        if (next >= Tokens.LOOP_MS) {
            resetRest()
            scriptTimeMs = next - Tokens.LOOP_MS
            return
        }
        scriptTimeMs = next
    }

    private fun runSwipe(previous: Float, next: Float, swipe: Tokens.Swipe) {
        val from = swipe.at.toFloat()
        val to = from + swipe.ms
        val startX = width / 2f - swipe.dx / 2f
        val endX = width / 2f + swipe.dx / 2f
        if (previous < from && next >= from) {
            down(startX, Tokens.LAYOUT.cardY, true)
        }
        if (next > from && previous < to) {
            val progress = ease((next - from) / (to - from))
            move(
                startX + (endX - startX) * progress,
                Tokens.LAYOUT.cardY + 4f * sin(PI.toFloat() * min(1f, (next - from) / (to - from))),
            )
        }
        if (previous < to && next >= to) {
            val velocity = (endX - startX) / (to - from) * 1000f * 0.6f
            up(velocity, 0f)
        }
    }

    private fun resetRest() {
        clockMs = 0f
        phase = GiftSlotDropPhase.Choosing
        selected = Tokens.START_INDEX
        armed = false
        releasedAt = -1f
        releasedFrom = GiftSlotDropReleasePose()
        closeAt = -1f
        offsetSpring.snap(Tokens.START_INDEX.toFloat())
        pullXSpring.snap(0f)
        pullYSpring.snap(0f)
        liftSpring.snap(1f)
        touchActive = false
        touchScripted = false
        touchMode = 0
        confettiFired = false
        confetti.forEach { it.life = 0f }
    }
}

internal fun giftSlotDropClamp(value: Float): Float {
    return value.coerceIn(0f, 1f)
}

internal fun giftSlotDropEase(value: Float): Float {
    val clamped = giftSlotDropClamp(value)
    if (clamped < 0.5f) {
        return 4f * clamped * clamped * clamped
    }
    val shifted = -2f * clamped + 2f
    return 1f - shifted * shifted * shifted / 2f
}

internal fun giftSlotDropSpring(value: Float, stiffness: Float, damping: Float): Float {
    if (value <= 0f) {
        return 0f
    }
    val frequency = sqrt(stiffness)
    val ratio = min(0.999f, damping / (2f * frequency))
    val damped = frequency * sqrt(1f - ratio * ratio)
    return 1f - kotlin.math.exp(-ratio * frequency * value) *
        (cos(damped * value) + ratio * frequency / damped * sin(damped * value))
}

internal fun giftSlotDropBackOut(value: Float): Float {
    val clamped = giftSlotDropClamp(value)
    val shifted = clamped - 1f
    return 1f + (Tokens.printFeedEaseBack + 1f) * shifted * shifted * shifted +
        Tokens.printFeedEaseBack * shifted * shifted
}

private fun ease(value: Float): Float {
    return giftSlotDropEase(value)
}

private fun pullScale(y: Float): Float {
    return 1f - (1f - Tokens.DRAG.minScale) * giftSlotDropClamp(y / Tokens.DRAG.full)
}

private fun hash(index: Int, salt: Int): Float {
    val raw = sin(index * 127.1 + salt * 311.7) * 43758.5453
    return (raw - floor(raw)).toFloat()
}
