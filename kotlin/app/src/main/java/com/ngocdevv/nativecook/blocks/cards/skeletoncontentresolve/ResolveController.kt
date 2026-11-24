package com.ngocdevv.nativecook.blocks.cards.skeletoncontentresolve

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.min

private const val MODE_AUTO = 0
private const val MODE_MANUAL = 1
private const val MODE_RETURN = 2
private const val RETURN_LOAD_S = 0.9f
private const val SPRING_DONE_S = 2.5f

/** A timing towards a value, eased in and out like Reanimated's default. */
private class Tween(private val state: MutableFloatState) {
    private var active = false
    private var from = 0f
    private var to = 0f
    private var at = 0.0
    private var duration = 0f

    fun start(target: Float, seconds: Float, now: Double) {
        from = state.floatValue
        to = target
        at = now
        duration = seconds
        active = true
    }

    fun stop() { active = false }

    fun step(now: Double) {
        if (!active) return
        val u = clamp01(((now - at) / duration).toFloat())
        state.floatValue = from + (to - from) * (if (u < 0.5f) 2f * u * u else 1f - (-2f * u + 2f) * (-2f * u + 2f) / 2f)
        if (u >= 1f) active = false
    }
}

/**
 * The screen's clocks and everything that moves. All of it runs on the main thread (frame callback
 * and pointer input), and the values are states read only in draw and layer lambdas.
 */
@Stable
internal class ResolveController(
    film: Boolean,
    private val onArm: () -> Unit,
    private val onLand: () -> Unit,
) {
    var film = film
    val sinceHide = mutableFloatStateOf(-1f)
    val sinceShow = mutableFloatStateOf(SETTLED)
    val offset = mutableFloatStateOf(0f)
    val reveal = mutableFloatStateOf(0f)
    val ghost = mutableFloatStateOf(0f)
    val fingerDy = mutableFloatStateOf(0f)
    val data = mutableIntStateOf(0)

    private var mode = if (film) MODE_AUTO else MODE_MANUAL
    private var clock = 0.0
    private var loopStart = -1.0
    private var tau = 0f
    private var lastTouch = 0.0
    private var holding = false
    private var armed = false

    private var hideAt = -1.0
    private var showAt = -1.0
    private var target = 0
    private var landed = true

    private var springing = false
    private var springFrom = 0f
    private var springTo = 0f
    private var springAt = 0.0
    private val revealTween = Tween(reveal)
    private val ghostTween = Tween(ghost)

    private fun springTo(to: Float, now: Double) {
        springFrom = offset.floatValue
        springTo = to
        springAt = now
        springing = true
    }

    private fun begin(now: Double, next: Int, load: Float) {
        hideAt = now
        showAt = now + load
        target = next
        landed = false
        sinceHide.floatValue = 0f
        sinceShow.floatValue = -1f
    }

    fun frame(now: Double) {
        clock = now
        if (mode == MODE_AUTO) {
            if (!film) {
                mode = MODE_MANUAL
                return
            }
            if (loopStart < 0.0) loopStart = now
            val t = ((now - loopStart) % LOOP).toFloat()
            tau = t
            sinceHide.floatValue = filmSinceHide(t)
            sinceShow.floatValue = filmSinceShow(t)
            offset.floatValue = filmOffset(t)
            reveal.floatValue = filmReveal(t)
            ghost.floatValue = filmGhost(t)
            fingerDy.floatValue = filmFinger(t)
            data.intValue = filmData(t)
            return
        }
        if (springing) {
            val s = (now - springAt).toFloat()
            if (s >= SPRING_DONE_S) {
                offset.floatValue = springTo
                springing = false
            } else {
                offset.floatValue = offsetSpring(springFrom, springTo, s)
            }
        }
        revealTween.step(now)
        ghostTween.step(now)

        val ss = if (now >= showAt) min(SETTLED.toDouble(), now - showAt).toFloat() else -1f
        if (ss < 0f && hideAt >= 0.0) sinceHide.floatValue = (now - hideAt).toFloat()
        sinceShow.floatValue = ss
        if (hideAt >= 0.0 && now - hideAt >= SWAP_AFTER) data.intValue = target
        if (ss >= 0f && !landed) {
            landed = true
            springTo(0f, now)
            onLand()
        }
        if (!film || holding) return
        val idle = ss >= SETTLED
        if (mode == MODE_MANUAL && idle && now - lastTouch > IDLE_S) {
            mode = MODE_RETURN
            springTo(0f, now)
            revealTween.start(0f, 0.16f, now)
            if (target != 0) begin(now, 0, RETURN_LOAD_S)
        } else if (mode == MODE_RETURN && idle && target == 0 && data.intValue == 0) {
            loopStart = now
            tau = 0f
            springing = false
            revealTween.stop()
            ghostTween.stop()
            mode = MODE_AUTO
        }
    }

    /** A finger anywhere: the film stops, and whatever it was loading goes on loading. */
    fun take() {
        val now = clock
        lastTouch = now
        if (mode == MODE_MANUAL) return
        if (mode == MODE_AUTO) {
            val t = tau
            val r = filmRelease(t)
            if (r < 0f) {
                hideAt = -1.0
                showAt = -1.0
                target = 0
                landed = true
                springTo(0f, now)
            } else {
                val s = filmResolve(t)
                hideAt = now - (t - r)
                showAt = now + (s - t)
                target = filmTarget(t)
                landed = t >= s
                springTo(if (t < s) HOLD else 0f, now)
            }
            revealTween.start(0f, 0.16f, now)
            ghostTween.start(0f, 0.16f, now)
        }
        mode = MODE_MANUAL
    }

    fun dragStart() { holding = true }

    fun drag(dy: Float) {
        lastTouch = clock
        if (sinceShow.floatValue < RESOLVE_TOTAL) return
        val o = pullFor(dy)
        springing = false
        revealTween.stop()
        offset.floatValue = o
        reveal.floatValue = clamp01(o / THRESHOLD)
        val nowArmed = reveal.floatValue >= 1f
        if (nowArmed && !armed) onArm()
        armed = nowArmed
    }

    fun release() {
        if (!holding) return
        holding = false
        armed = false
        val now = clock
        lastTouch = now
        if (sinceShow.floatValue < RESOLVE_TOTAL) return
        if (offset.floatValue >= THRESHOLD) {
            begin(now, 1 - target, LOAD_S)
            springTo(HOLD, now)
        } else {
            springTo(0f, now)
            revealTween.start(0f, 0.18f, now)
        }
    }

    /** The accessibility action. */
    fun refresh() {
        take()
        if (sinceShow.floatValue < RESOLVE_TOTAL) return
        begin(clock, 1 - target, LOAD_S)
        springTo(HOLD, clock)
    }
}
