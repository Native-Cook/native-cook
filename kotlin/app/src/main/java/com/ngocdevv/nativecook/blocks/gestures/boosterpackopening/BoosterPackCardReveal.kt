package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import kotlin.math.abs
import kotlin.math.min

/** What the card stack reports to the outside: sounds and haptics at its discrete moments. */
internal interface CardRevealEvents {
    fun nextCard(fromIndex: Int)

    fun revealCard(index: Int, rarity: CardRarity)

    fun cardHaptic()
}

/**
 * The five-card stack after the pack has opened: which card is on top, the tilt a slow drag gives
 * the stack, the top card's exit, and the clock of the arriving card's reveal.
 */
internal class CardReveal(
    clock: AnimationClock,
    private val width: Double,
    private val height: Double,
    private val postCutMs: Animated,
    private val events: CardRevealEvents,
) {
    var reducedMotion = false

    /** False while the block is off screen or the app is in the background. */
    var enabled = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) hold()
        }

    var index = 0
        private set
    private var announcedIndex = -1
    val yaw = Animated(clock, 0.0)
    val pitch = Animated(clock, 0.0)
    private var startYaw = 0.0
    private var startPitch = 0.0
    var dragging = false
        private set
    var exiting = false
        private set
    val exit = Animated(clock, 0.0)
    var exitDirection = 1
        private set
    val revealMs = Animated(clock, RARE_REVEAL_MS)
    private val cardWidth = width * ReferenceLayout.Card.WIDTH
    private val cardHeight = height * ReferenceLayout.Card.HEIGHT
    private var wasOpening = false

    val ready: Boolean get() = enabled && postCutMs.value >= OPENING_DURATION_MS - 1
    val busy: Boolean
        get() = exiting || (isRareCard(index) && !reducedMotion && revealMs.value < RARE_INTERACTION_MS)

    /** The stack fades in over the card that rose out of the pack. */
    val opacity: Double get() = clamp((postCutMs.value - 1490) / 320, 0.0, 1.0)

    /** Runs once per frame, after the clocks have advanced. */
    fun react() {
        val opening = postCutMs.value < OPENING_DURATION_MS - 1
        if (opening && !wasOpening) reset()
        wasOpening = opening

        // One event at the actual reveal boundary, including the first card after opening.
        val next = if (ready && !exiting) index else -1
        if (next >= 0 && next != announcedIndex) {
            announcedIndex = next
            events.revealCard(next, PACK_CARD_RARITIES[next])
            // The first card has no exit before it; give it the same arrival beat.
            if (next == 0 && !reducedMotion) {
                revealMs.set(0.0)
                revealMs.timing(RARE_REVEAL_MS, RARE_REVEAL_MS, always = true)
            }
        }
    }

    fun reset() {
        // Cancellation runs BEFORE resets. A stale exit callback cannot skip a card
        // or bring the previous pack back after replay, resize, or losing focus.
        exit.cancel()
        yaw.cancel()
        pitch.cancel()
        revealMs.cancel()
        index = 0
        announcedIndex = -1
        yaw.set(0.0)
        pitch.set(0.0)
        dragging = false
        exiting = false
        exit.set(0.0)
        exitDirection = 1
        revealMs.set(RARE_REVEAL_MS)
    }

    // Keep the currently displayed card when the screen loses focus.
    private fun hold() {
        dragging = false
        exiting = false
        exit.set(0.0)
        yaw.set(0.0)
        pitch.set(0.0)
        revealMs.set(RARE_REVEAL_MS)
    }

    private fun settle() {
        dragging = false
        yaw.spring(0.0, duration = 400.0, dampingRatio = 0.8)
        pitch.spring(0.0, duration = 400.0, dampingRatio = 0.8)
    }

    /** The top card leaves and the next one becomes the top: what a tap, a flick or an arrow does. */
    fun advance(direction: Int = 1, velocity: Double = 0.0) {
        if (!ready || busy || index >= CARD_COUNT - 1) return
        dragging = false
        yaw.cancel()
        pitch.cancel()
        revealMs.cancel()
        exitDirection = direction
        exiting = true
        exit.set(0.0)
        events.nextCard(index)
        events.cardHaptic()
        val finish = {
            index += 1
            exit.set(0.0)
            exiting = false
            yaw.set(0.0)
            pitch.set(0.0)
            revealMs.set(0.0)
            revealMs.timing(RARE_REVEAL_MS, RARE_REVEAL_MS, always = true)
        }
        if (velocity != 0.0 && !reducedMotion) {
            exit.spring(
                1.0,
                duration = 280.0,
                dampingRatio = 1.0,
                velocity = min(8.0, abs(velocity) / width),
                overshootClamping = true,
                onFinish = finish,
            )
        } else {
            exit.timing(
                1.0,
                duration = if (reducedMotion) 160.0 else CARD_EXIT_MS,
                easing = Easing.EaseOut,
                always = true,
                onFinish = finish,
            )
        }
    }

    fun begin(x: Double, y: Double) {
        if (!ready || busy) return
        val top = (height - cardHeight) / 2
        if (abs(x - width / 2) > cardWidth / 2 + 18 || y < top - 18 || y > top + cardHeight + 18) return
        yaw.cancel()
        pitch.cancel()
        startYaw = yaw.value
        startPitch = pitch.value
        dragging = true
    }

    fun update(translationX: Double, translationY: Double) {
        if (!dragging || busy || !ready) return
        val tilt = dragCardTilt(translationX, translationY, cardWidth)
        yaw.set(if (reducedMotion) 0.0 else clamp(startYaw + tilt.yaw, -0.56, 0.56))
        pitch.set(if (reducedMotion) 0.0 else clamp(startPitch + tilt.pitch, -0.16, 0.16))
    }

    fun end(translationX: Double, translationY: Double, velocityX: Double, success: Boolean) {
        if (!dragging) return
        if (success && ready && index < CARD_COUNT - 1 &&
            shouldAdvanceCard(translationX, translationY, velocityX, cardWidth)
        ) {
            advance(if (translationX >= 0) 1 else -1, velocityX)
        } else {
            settle()
        }
    }

    fun cancel() {
        if (dragging) settle()
    }
}
