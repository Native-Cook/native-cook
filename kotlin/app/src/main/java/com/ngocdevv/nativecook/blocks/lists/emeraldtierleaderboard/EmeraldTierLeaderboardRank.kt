package com.ngocdevv.nativecook.blocks.lists.emeraldtierleaderboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.max

private const val SETTLE_DURATION = 420f

/**
 * useEmeraldRankMotion: the demo owns a clock; input owns a separate, monotonic rank journey.
 *
 * Everything the Reanimated hook kept in shared values lives in plain fields of this one object,
 * so no callback captures a stale copy (the rememberUpdatedState trap): handlers, the frame tick
 * and the animator all read the same live fields. Only [rankPhase] and [responseAllowed] are
 * compose state, read from draw and graphicsLayer lambdas.
 */
internal class EmeraldRankMotion(
    private val autoPlay: Boolean,
    private val previewTime: Float?,
    reduced: Boolean,
    width: Float,
    private val onHaptic: () -> Unit,
) {
    /** True while the system asks for reduced motion (or the preview forces it). Read by the frame derivation. */
    var reduced by mutableStateOf(reduced)

    /** Screen width of the rank journey: a full pull is this many screen dp. */
    var width: Float = width

    private val initialTime = clamp(previewTime ?: 0f, 0f, LOOP)

    var rankPhase by mutableFloatStateOf(emeraldDemoPhase(initialTime / LOOP))
        private set
    var responseAllowed by mutableStateOf(true)
        private set

    /** Seconds on the demo clock; read by the league focus. */
    var demoTime: Float = initialTime
        private set

    /** True while a finger (or a replay) owns the journey instead of the demo clock. */
    var manual = false
        private set

    private var demoAnchor = -1.0
    private var lastTick = -1.0
    private var animating = false
    private var dragging = false
    private var target = 0
    private var dragStart = 0f
    private var dragTranslation = 0f
    private var holdRemaining = 2f
    private var generation = 0
    private var needsRecovery = false
    private var hapticArmed = false
    private var foreground = true
    private var focused = false
    private var prevCommitted = false
    private val animator = EmeraldAnimator()
    private val applyRank: (Float) -> Unit = { rankPhase = it }

    private fun settle(destination: Int, velocity: Float, notify: Boolean) {
        val owner = ++generation
        animator.cancel()
        manual = true
        animating = true
        dragging = false
        target = destination
        responseAllowed = destination == 1
        hapticArmed = notify && destination == 1
        demoAnchor = -1.0
        holdRemaining = 2f
        animator.startSpring(
            rankPhase, destination.toFloat(), clamp(velocity, -4f, 4f), SETTLE_DURATION,
            if (abs(velocity) > .35f) .88f else 1f, true,
        ) { finished ->
            if (!finished || generation != owner) return@startSpring
            animating = false
            holdRemaining = 2f
        }
    }

    private fun takeLead(preserveHaptic: Boolean) {
        val current = rankPhase
        val owner = ++generation
        animator.cancel()
        manual = true
        animating = true
        dragging = false
        target = 1
        responseAllowed = true
        if (!preserveHaptic) hapticArmed = true
        demoAnchor = -1.0
        holdRemaining = 2f
        // Constant phase speed feeds the same eased choreography used by the demo.
        animator.startTiming(current, 1f, max(120f, TAKEOVER_MS * (1 - clamp(current)))) { finished ->
            if (!finished || generation != owner) return@startTiming
            animating = false
            holdRemaining = 2f
        }
    }

    /** replayOnUI: every replay button, the Leagues tab and the Tournaments tab. */
    fun replay() {
        if (previewTime != null || !focused || !foreground) return
        if (reduced) {
            generation += 1
            animator.cancel()
            manual = true; animating = false; dragging = false; target = 1
            responseAllowed = false; demoAnchor = -1.0; holdRemaining = 2f
            hapticArmed = rankPhase < .5f
            rankPhase = 1f
            return
        }
        val continuing = manual && animating && target == 1
        if (rankPhase < .999f || animating) {
            takeLead(continuing)
            return
        }
        // A completed replay travels back visibly; another tap can redirect this return instantly.
        val owner = ++generation
        animator.cancel()
        manual = true; animating = true; dragging = false; target = 0
        responseAllowed = false; hapticArmed = false; demoAnchor = -1.0
        animator.startTiming(rankPhase, 0f, 900f) { finished ->
            if (finished && generation == owner) takeLead(false)
        }
    }

    private fun suspend() {
        needsRecovery = manual || animating || dragging
        generation += 1
        animator.cancel()
        animating = false; dragging = false; hapticArmed = false
        demoAnchor = -1.0; lastTick = -1.0
    }

    private fun recover() {
        if (previewTime != null || !focused || !foreground) return
        demoAnchor = -1.0; lastTick = -1.0
        if (!needsRecovery) return
        needsRecovery = false
        if (reduced) {
            rankPhase = emeraldSnapTarget(rankPhase, 0f).toFloat()
            return
        }
        settle(emeraldSnapTarget(rankPhase, 0f), 0f, false)
    }

    /** useFocusEffect and AppState collapse into the host lifecycle: resumed means focused and in the foreground. */
    fun setResumed(resumed: Boolean) {
        if (resumed == focused && resumed == foreground) return
        focused = resumed
        foreground = resumed
        if (resumed) recover() else suspend()
    }

    /**
     * Re-run after a Reduced Motion change. useFocusEffect depends on `reduced`, so its cleanup
     * (suspend) and setup (recover) run again, then the reduced effect snaps to the nearest rest.
     */
    fun onReducedChanged() {
        if (previewTime != null) return
        if (focused && foreground) {
            suspend()
            recover()
        }
        if (!reduced) return
        generation += 1
        animator.cancel()
        rankPhase = emeraldSnapTarget(rankPhase, 0f).toFloat()
        manual = true; animating = false; dragging = false; hapticArmed = false
        responseAllowed = false; target = if (rankPhase >= .5f) 1 else 0
        holdRemaining = 2f; demoAnchor = -1.0
    }

    /** The previewTime effect: a frozen frame at a given second of the loop. */
    fun applyPreview() {
        val pt = previewTime ?: return
        generation += 1
        animator.cancel()
        manual = false; animating = false; dragging = false; hapticArmed = false
        val seconds = clamp(pt, 0f, LOOP)
        needsRecovery = false
        demoTime = seconds; demoAnchor = -1.0
        responseAllowed = seconds < DEMO_RETURN_START
        rankPhase = emeraldDemoPhase(seconds / LOOP)
    }

    /** The frame callback (useFrameCallback). Also advances the running animation and checks the haptic reaction. */
    fun frame(nowMs: Double) {
        animator.advance(nowMs, applyRank)
        demoClock(nowMs)
        // useAnimatedReaction: hapticArmed && !dragging && rankPhase >= RECAST_SEAT_PHASE
        val committed = hapticArmed && !dragging && rankPhase >= RECAST_SEAT_PHASE
        if (committed && !prevCommitted && focused && foreground) {
            hapticArmed = false
            onHaptic()
        }
        prevCommitted = committed
    }

    private fun demoClock(nowMs: Double) {
        if (previewTime != null || reduced || !focused || !foreground) return
        val previous = lastTick
        val elapsed = if (previous < 0) 0f else max(0.0, (nowMs - previous) / 1000).toFloat()
        lastTick = nowMs
        if (dragging || animating || !autoPlay) return
        if (manual) {
            val remaining = max(0f, holdRemaining - elapsed)
            holdRemaining = remaining
            if (remaining > 0) return
            // Resume at an identical rest pose, then use the demo's guided return.
            demoTime = if (target == 1) DEMO_RETURN_START else 0f
            demoAnchor = -1.0
            manual = false
        }
        if (demoAnchor < 0) demoAnchor = nowMs - demoTime * 1000
        val seconds = (((nowMs - demoAnchor) / 1000) % LOOP).toFloat()
        demoTime = seconds
        responseAllowed = seconds < DEMO_RETURN_START
        rankPhase = emeraldDemoPhase(seconds / LOOP)
    }

    // --- Gesture.Pan().enabled(previewTime === undefined && !reduced) ---

    val panEnabled: Boolean get() = previewTime == null && !reduced

    fun onPanStart(translationY: Float) {
        if (!focused || !foreground) return
        generation += 1
        animator.cancel()
        manual = true; animating = false; dragging = true; hapticArmed = false
        responseAllowed = false; demoAnchor = -1.0
        dragStart = emeraldUnrubberPhase(rankPhase)
        dragTranslation = translationY
    }

    fun onPanUpdate(translationY: Float) {
        if (!dragging || !focused || !foreground) return
        rankPhase = emeraldRubberPhase(dragStart + -(translationY - dragTranslation) / width)
    }

    fun onPanEnd(velocityY: Float) {
        if (!dragging) return
        val velocity = -velocityY / width
        settle(emeraldSnapTarget(rankPhase, velocity), velocity, true)
    }

    fun onPanFinalize() {
        if (!dragging) return
        // Cancellation settles too; it cannot leave the two portraits suspended together.
        settle(emeraldSnapTarget(rankPhase, 0f), 0f, false)
    }
}
