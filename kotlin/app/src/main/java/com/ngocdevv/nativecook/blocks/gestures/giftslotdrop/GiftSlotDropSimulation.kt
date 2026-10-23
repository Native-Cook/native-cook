package com.ngocdevv.nativecook.blocks.gestures.giftslotdrop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

internal enum class GiftSlotDropPhase { Choosing, Pulling, Swiping, Inserting, Reading, Offer, Closing }

internal enum class GiftSlotDropHaptic { Select, Armed, Insert, Success, Light }

internal data class GiftSlotDropReleasePose(val x: Float = 0f, val y: Float = 0f)

internal data class GiftSlotDropMilestones(
    val sunk: Float,
    val read: Float,
    val risen: Float,
    val turned: Float,
    val shown: Float,
)

internal fun giftSlotDropMilestones(reducedMotion: Boolean): GiftSlotDropMilestones {
    if (reducedMotion) {
        val read = GiftSlotDropTokens.REDUCED_MS + GiftSlotDropTokens.Sequence.read
        return GiftSlotDropMilestones(
            sunk = GiftSlotDropTokens.REDUCED_MS,
            read = read,
            risen = read,
            turned = read + GiftSlotDropTokens.REDUCED_MS,
            shown = read + GiftSlotDropTokens.REDUCED_MS,
        )
    }
    val sunk = GiftSlotDropTokens.Sequence.sink
    val read = sunk + GiftSlotDropTokens.Sequence.read
    val risen = read + GiftSlotDropTokens.Sequence.rise
    val turned = risen + GiftSlotDropTokens.Sequence.pause + GiftSlotDropTokens.Sequence.flip
    return GiftSlotDropMilestones(sunk, read, risen, turned, turned + GiftSlotDropTokens.Sequence.detailsIn)
}

private class GiftSlotDropSpringValue(initial: Float) {
    var value = initial
        private set
    private var target = initial
    private var velocity = 0f
    private var durationSeconds = 0.4f
    private var dampingRatio = 0.85f
    private var active = false

    fun snap(next: Float) {
        value = next
        target = next
        velocity = 0f
        active = false
    }

    fun animateTo(next: Float, spring: GiftSlotDropTokens.Spring, initialVelocity: Float = velocity) {
        target = next
        durationSeconds = max(0.05f, spring.durationMs / 1_000f)
        dampingRatio = spring.dampingRatio
        velocity = initialVelocity
        active = true
    }

    fun step(seconds: Float) {
        if (!active) return
        val omega = 5.2f / durationSeconds
        val acceleration = -omega * omega * (value - target) - 2f * dampingRatio * omega * velocity
        velocity += acceleration * seconds
        value += velocity * seconds
        if (abs(value - target) < 0.001f && abs(velocity) < 0.01f) snap(target)
    }
}

internal class GiftSlotDropSimulation(
    private val onHaptic: (GiftSlotDropHaptic) -> Unit,
) {
    var layout = GiftSlotDropLayout(393f, 852f, 0f, 0f)
    var clockMs = 0f
        private set
    var phase = GiftSlotDropPhase.Choosing
        private set
    var selected = GiftSlotDropTokens.START_INDEX
        private set
    var armed = false
        private set
    var titleAt = -1_000_000f
        private set
    var titleWas = 0
        private set
    var releasedAt = -1f
        private set
    var releasedFrom = GiftSlotDropReleasePose()
        private set
    var closeAt = -1f
        private set
    var ghostX = 0f
        private set
    var ghostY = 0f
        private set
    var ghostOpacity = 0f
        private set
    var buttonScale = 1f
        private set

    private val offsetSpring = GiftSlotDropSpringValue(GiftSlotDropTokens.START_INDEX.toFloat())
    private val pullXSpring = GiftSlotDropSpringValue(0f)
    private val pullYSpring = GiftSlotDropSpringValue(0f)
    private val liftSpring = GiftSlotDropSpringValue(1f)

    val offset: Float get() = offsetSpring.value
    val pullX: Float get() = pullXSpring.value
    val pullY: Float get() = pullYSpring.value
    val lift: Float get() = liftSpring.value

    private var touchActive = false
    private var scriptedGesture = false
    private var touchMode = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchOffset = GiftSlotDropTokens.START_INDEX.toFloat()
    private var touchOnCard = false
    private var scriptOn = false
    private var scriptTimeMs = 0f
    private var quietMs = 0f
    private var realTouching = false
    private var successFired = false
    private var buttonPressed = false

    fun start(autoPlay: Boolean) {
        resetRest()
        scriptOn = autoPlay
    }

    fun setButtonPressed(pressed: Boolean) {
        buttonPressed = pressed
    }

    fun elapsedSinceRelease(): Float = if (releasedAt < 0f) -1f else clockMs - releasedAt

    fun takeOver() {
        if (scriptOn && scriptedGesture && touchActive) {
            setArmed(false)
            up(0f, 0f)
        }
        ghostOpacity = 0f
        scriptOn = false
        quietMs = 0f
        realTouching = true
        scriptedGesture = false
    }

    fun letGo() {
        realTouching = false
        quietMs = 0f
    }

    fun down(x: Float, y: Float, scripted: Boolean) {
        if (phase != GiftSlotDropPhase.Choosing) return
        touchOnCard = abs(x - layout.centerX) < GiftSlotDropTokens.Layout.cardW / 2f + 12f &&
            abs(y - layout.cardY) < GiftSlotDropTokens.Layout.cardH / 2f + 12f
        touchActive = true
        scriptedGesture = scripted
        touchMode = 0
        touchStartX = x
        touchStartY = y
        touchOffset = offset
        offsetSpring.snap(offset)
        if (touchOnCard) liftSpring.animateTo(GiftSlotDropTokens.Drag.lift, GiftSlotDropTokens.liftSpring)
    }

    fun move(x: Float, y: Float) {
        if (!touchActive) return
        val dx = x - touchStartX
        val dy = y - touchStartY
        if (touchMode == 0 && hypot(dx, dy) > GiftSlotDropTokens.Drag.decide) {
            touchMode = if (abs(dx) > abs(dy) || !touchOnCard || dy < 0f) 2 else 1
            setPhase(if (touchMode == 1) GiftSlotDropPhase.Pulling else GiftSlotDropPhase.Swiping)
            if (touchMode == 2) liftSpring.animateTo(1f, GiftSlotDropTokens.liftSpring)
        }
        if (touchMode == 2) {
            var next = touchOffset - dx / layout.spacing
            if (next < 0f) next *= 0.3f
            val end = GiftSlotDropTokens.gifts.lastIndex.toFloat()
            if (next > end) next = end + (next - end) * 0.3f
            offsetSpring.snap(next)
        } else if (touchMode == 1) {
            pullXSpring.snap(dx * GiftSlotDropTokens.Drag.sideResist)
            val resisted = if (dy < 0f) {
                dy * GiftSlotDropTokens.Drag.upResist
            } else {
                min(dy, GiftSlotDropTokens.Drag.full + (dy - GiftSlotDropTokens.Drag.full) * 0.2f)
            }
            pullYSpring.snap(resisted)
            setArmed(dy >= GiftSlotDropTokens.Drag.commit)
        }
    }

    fun up(velocityX: Float, velocityY: Float) {
        if (!touchActive) return
        touchActive = false
        liftSpring.animateTo(1f, GiftSlotDropTokens.liftSpring)
        if (touchMode == 2) {
            val fling = if (abs(velocityX) > GiftSlotDropTokens.Drag.swipeVelocity) -sign(velocityX) * 0.5f else 0f
            snapTo((offset + fling).roundToInt(), -velocityX / layout.spacing)
            setPhase(GiftSlotDropPhase.Choosing)
        } else if (touchMode == 1 && armed) {
            setArmed(false)
            release()
        } else {
            if (touchMode == 1) {
                pullXSpring.animateTo(0f, GiftSlotDropTokens.backSpring, velocityX)
                pullYSpring.animateTo(0f, GiftSlotDropTokens.backSpring, velocityY)
            }
            setPhase(GiftSlotDropPhase.Choosing)
        }
    }

    fun activate() {
        if (phase != GiftSlotDropPhase.Choosing) return
        scriptedGesture = false
        release()
    }

    fun stepGift(direction: Int) {
        if (phase == GiftSlotDropPhase.Choosing) snapTo(selected + direction, 0f)
    }

    fun close() {
        if (phase != GiftSlotDropPhase.Offer) return
        closeAt = clockMs
        setPhase(GiftSlotDropPhase.Closing)
        haptic(GiftSlotDropHaptic.Light)
    }

    fun step(frameSeconds: Float, autoPlay: Boolean, reducedMotion: Boolean) {
        val seconds = frameSeconds.coerceIn(0f, GiftSlotDropTokens.MAX_FRAME_SECONDS)
        val frameMs = seconds * 1_000f
        clockMs += frameMs
        offsetSpring.step(seconds)
        pullXSpring.step(seconds)
        pullYSpring.step(seconds)
        liftSpring.step(seconds)
        val targetButton = if (buttonPressed) 0.97f else 1f
        val duration = if (buttonPressed) 100f else 160f
        buttonScale += (targetButton - buttonScale) * (1f - exp(-frameMs / duration))
        advanceRelease(reducedMotion)
        advanceClose(reducedMotion)
        if (autoPlay && !reducedMotion) advanceAutoplay(frameMs)
    }

    private fun release() {
        releasedFrom = GiftSlotDropReleasePose(pullX, pullY)
        releasedAt = clockMs
        successFired = false
        setPhase(GiftSlotDropPhase.Inserting)
        haptic(GiftSlotDropHaptic.Insert)
    }

    private fun snapTo(index: Int, velocity: Float) {
        val target = index.coerceIn(0, GiftSlotDropTokens.gifts.lastIndex)
        offsetSpring.animateTo(target.toFloat(), GiftSlotDropTokens.snapSpring, velocity)
        if (target != selected) {
            selected = target
            haptic(GiftSlotDropHaptic.Select)
        }
    }

    private fun titleOf(nextPhase: GiftSlotDropPhase, nextArmed: Boolean): Int = when (nextPhase) {
        GiftSlotDropPhase.Inserting, GiftSlotDropPhase.Reading -> 2
        GiftSlotDropPhase.Offer -> 3
        else -> if (nextArmed) 1 else 0
    }

    private fun retitle(before: Int, after: Int) {
        if (before == after) return
        titleWas = before
        titleAt = clockMs
    }

    private fun setPhase(next: GiftSlotDropPhase) {
        if (phase == next) return
        retitle(titleOf(phase, armed), titleOf(next, armed))
        phase = next
    }

    private fun setArmed(next: Boolean) {
        if (armed == next) return
        retitle(titleOf(phase, armed), titleOf(phase, next))
        armed = next
        if (next) haptic(GiftSlotDropHaptic.Armed)
    }

    private fun haptic(event: GiftSlotDropHaptic) {
        if (!scriptedGesture) onHaptic(event)
    }

    private fun advanceRelease(reducedMotion: Boolean) {
        if (releasedAt < 0f) return
        val elapsed = elapsedSinceRelease()
        val milestones = giftSlotDropMilestones(reducedMotion)
        if (phase == GiftSlotDropPhase.Inserting && elapsed >= milestones.sunk) setPhase(GiftSlotDropPhase.Reading)
        if (phase == GiftSlotDropPhase.Reading && elapsed >= milestones.read) setPhase(GiftSlotDropPhase.Offer)
        if (!successFired && elapsed >= milestones.turned) {
            successFired = true
            haptic(GiftSlotDropHaptic.Success)
        }
    }

    private fun advanceClose(reducedMotion: Boolean) {
        if (closeAt < 0f) return
        val duration = if (reducedMotion) GiftSlotDropTokens.REDUCED_MS else GiftSlotDropTokens.Close.out + GiftSlotDropTokens.Close.back
        if (clockMs - closeAt < duration) return
        closeAt = -1f
        releasedAt = -1f
        pullXSpring.snap(0f)
        pullYSpring.snap(0f)
        setPhase(GiftSlotDropPhase.Choosing)
    }

    private fun advanceAutoplay(frameMs: Float) {
        if (!scriptOn) {
            val resting = phase == GiftSlotDropPhase.Offer ||
                (phase == GiftSlotDropPhase.Choosing && abs(offset - offset.roundToInt()) < 0.01f)
            quietMs = if (!realTouching && resting) quietMs + frameMs else 0f
            if (quietMs >= GiftSlotDropTokens.RESUME_MS && phase == GiftSlotDropPhase.Offer) {
                close()
                quietMs = 0f
                return
            }
            if (quietMs >= GiftSlotDropTokens.RESUME_MS && selected != GiftSlotDropTokens.START_INDEX) {
                snapTo(GiftSlotDropTokens.START_INDEX, 0f)
                quietMs = 0f
                return
            }
            if (quietMs >= GiftSlotDropTokens.RESUME_MS && phase == GiftSlotDropPhase.Choosing) {
                scriptOn = true
                scriptTimeMs = 0f
                quietMs = 0f
            }
            return
        }

        val previous = scriptTimeMs
        val next = previous + frameMs
        GiftSlotDropTokens.scriptSwipes.forEach { runSwipe(previous, next, it) }
        if (previous < GiftSlotDropTokens.scriptPullDown && next >= GiftSlotDropTokens.scriptPullDown) {
            ghostX = layout.centerX + 12f
            ghostY = layout.cardY + 40f
            ghostOpacity = 1f
            down(ghostX, ghostY, true)
        }
        if (next > GiftSlotDropTokens.scriptPullDown && previous < GiftSlotDropTokens.scriptPullUp) {
            val progress = giftSlotDropEase(
                (next - GiftSlotDropTokens.scriptPullDown - 120f) /
                    (GiftSlotDropTokens.scriptPullUp - GiftSlotDropTokens.scriptPullDown - 220f),
            )
            ghostX = layout.centerX + 12f - 14f * progress
            ghostY = layout.cardY + 40f + GiftSlotDropTokens.scriptPullBy * progress
            move(ghostX, ghostY)
        }
        if (previous < GiftSlotDropTokens.scriptPullUp && next >= GiftSlotDropTokens.scriptPullUp) {
            up(0f, 0f)
            ghostOpacity = 0f
        }
        if (previous < GiftSlotDropTokens.scriptDone && next >= GiftSlotDropTokens.scriptDone) close()
        scriptTimeMs = if (next >= GiftSlotDropTokens.LOOP_MS) next - GiftSlotDropTokens.LOOP_MS else next
    }

    private fun runSwipe(previous: Float, next: Float, swipe: GiftSlotDropScriptSwipe) {
        val from = swipe.at
        val to = from + swipe.duration
        val startX = layout.centerX - swipe.deltaX / 2f
        val endX = layout.centerX + swipe.deltaX / 2f
        if (previous < from && next >= from) {
            ghostX = startX
            ghostY = layout.cardY
            ghostOpacity = 1f
            down(startX, layout.cardY, true)
        }
        if (next > from && previous < to) {
            val progress = giftSlotDropEase((next - from) / (to - from))
            ghostX = startX + (endX - startX) * progress
            ghostY = layout.cardY + 4f * sin(PI.toFloat() * progress)
            move(ghostX, ghostY)
        }
        if (previous < to && next >= to) {
            up((endX - startX) / (to - from) * 1_000f * 0.6f, 0f)
            ghostOpacity = 0f
        }
    }

    private fun resetRest() {
        clockMs = 0f
        phase = GiftSlotDropPhase.Choosing
        selected = GiftSlotDropTokens.START_INDEX
        armed = false
        titleAt = -1_000_000f
        titleWas = 0
        releasedAt = -1f
        releasedFrom = GiftSlotDropReleasePose()
        closeAt = -1f
        offsetSpring.snap(GiftSlotDropTokens.START_INDEX.toFloat())
        pullXSpring.snap(0f)
        pullYSpring.snap(0f)
        liftSpring.snap(1f)
        touchActive = false
        scriptedGesture = false
        touchMode = 0
        scriptTimeMs = 0f
        quietMs = 0f
        successFired = false
        ghostOpacity = 0f
        buttonScale = 1f
    }
}

internal fun giftSlotDropClamp(value: Float): Float = value.coerceIn(0f, 1f)

internal fun giftSlotDropEase(value: Float): Float {
    val clamped = giftSlotDropClamp(value)
    return if (clamped < 0.5f) {
        4f * clamped * clamped * clamped
    } else {
        1f - (-2f * clamped + 2f).let { it * it * it } / 2f
    }
}

internal fun giftSlotDropEaseOut(value: Float): Float = giftSlotDropBezier(0.23f, 1f, 0.32f, 1f, value)

internal fun giftSlotDropEaseInOut(value: Float): Float = giftSlotDropBezier(0.77f, 0f, 0.175f, 1f, value)

private fun giftSlotDropBezier(x1: Float, y1: Float, x2: Float, y2: Float, value: Float): Float {
    val input = giftSlotDropClamp(value)
    val cx = 3f * x1
    val bx = 3f * (x2 - x1) - cx
    val ax = 1f - cx - bx
    val cy = 3f * y1
    val by = 3f * (y2 - y1) - cy
    val ay = 1f - cy - by
    var t = input
    repeat(8) {
        val x = ((ax * t + bx) * t + cx) * t - input
        val derivative = (3f * ax * t + 2f * bx) * t + cx
        if (abs(x) >= 0.00001f && abs(derivative) >= 0.000001f) t -= x / derivative
    }
    t = giftSlotDropClamp(t)
    return ((ay * t + by) * t + cy) * t
}

internal fun giftSlotDropSettle(milliseconds: Float, duration: Float): Float {
    if (milliseconds <= 0f) return 0f
    val time = milliseconds / duration
    if (time >= 1.6f) return 1f
    return 1f - exp(-5.2f * time) * cos(4.4f * time)
}
