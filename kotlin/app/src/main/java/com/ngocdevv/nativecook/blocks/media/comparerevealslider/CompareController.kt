package com.ngocdevv.nativecook.blocks.media.comparerevealslider

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.abs
import kotlin.math.exp

private const val MODE_AUTO = 0
private const val MODE_MANUAL = 1
private const val MODE_RETURN = 2
private const val GRAB_R = 40f
private const val GLIDE_S = 0.2f
private const val STEP = 0.1f
private const val SPRING_DONE_S = 3f

private fun quadInOut(u: Float) = if (u < 0.5f) 2f * u * u else 1f - (-2f * u + 2f) * (-2f * u + 2f) / 2f

private class Tween(private val state: MutableFloatState) {
    private var active = false
    private var from = 0f
    private var to = 0f
    private var at = 0.0
    private var duration = 0f
    private var ease: (Float) -> Float = ::quadInOut

    fun start(target: Float, seconds: Float, now: Double, curve: (Float) -> Float = ::quadInOut) {
        if (seconds <= 0f) {
            state.floatValue = target
            active = false
            return
        }
        from = state.floatValue
        to = target
        at = now
        duration = seconds
        ease = curve
        active = true
    }

    fun stop() { active = false }

    fun step(now: Double) {
        if (!active) return
        val u = clamp01(((now - at) / duration).toFloat())
        state.floatValue = from + (to - from) * ease(u)
        if (u >= 1f) active = false
    }
}

/**
 * The divider and everything that follows it. Frame callback and pointer input both run on the main
 * thread; the values are states read only in draw and layer lambdas.
 */
@Stable
internal class CompareController(
    film: Boolean,
    private val reduced: Boolean,
    private val onPress: () -> Unit,
    private val onStop: () -> Unit,
) {
    var film = film
    var widthDp = 1f
    var ready = false

    val pos = mutableFloatStateOf(REST)
    val press = mutableFloatStateOf(0f)
    val stretch = mutableFloatStateOf(0f)
    val evening = mutableFloatStateOf(0f)
    val ghost = mutableFloatStateOf(0f)
    val glint = mutableFloatStateOf(0f)
    val selected = mutableIntStateOf(0)

    private val grab = mutableFloatStateOf(0f)
    private val pressTween = Tween(press)
    private val ghostTween = Tween(ghost)
    private val eveningTween = Tween(evening)
    private val grabTween = Tween(grab)
    private val posTween = Tween(pos)

    private var mode = if (film) MODE_AUTO else MODE_MANUAL
    private var clock = 0.0
    private var loopStart = -1.0
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var prev = REST
    private var holding = false
    private var fingerX = 0f
    private var side = 0

    private var springing = false
    private var springFrom = 0f
    private var springTo = 0f
    private var springV = 0f
    private var springStart = 0.0

    private fun startSpring(to: Float, velocity: Float, now: Double) {
        posTween.stop()
        springFrom = pos.floatValue
        springTo = to
        springV = velocity
        springStart = now
        springing = true
    }

    private fun stopPos() {
        springing = false
        posTween.stop()
    }

    private fun syncSelected() {
        val next = if (evening.floatValue > 0.5f) 1 else 0
        if (next != eveningSide) {
            eveningSide = next
            selected.intValue = next
        }
    }

    private var eveningSide = 0

    fun frame(now: Double) {
        val dt = clamp((now - clock).toFloat(), 1f / 240f, 0.05f)
        clock = now
        if (mode == MODE_AUTO) {
            if (!film) {
                mode = MODE_MANUAL
                pos.floatValue = REST
                press.floatValue = 0f
                stretch.floatValue = 0f
                ghost.floatValue = 0f
                evening.floatValue = 0f
            } else {
                var t = 0f
                if (ready) {
                    if (loopStart < 0.0) loopStart = now
                    t = ((now - loopStart) % LOOP).toFloat()
                }
                pos.floatValue = filmPos(t)
                press.floatValue = filmPress(t)
                stretch.floatValue = stretchFor(filmVelocity(t))
                ghost.floatValue = filmGhost(t)
                evening.floatValue = filmEvening(t)
                glint.floatValue = glintAt(t)
            }
        } else {
            pressTween.step(now)
            ghostTween.step(now)
            eveningTween.step(now)
            grabTween.step(now)
            posTween.step(now)
            if (springing) {
                val s = (now - springStart).toFloat()
                if (s >= SPRING_DONE_S) {
                    pos.floatValue = springTo
                    springing = false
                } else {
                    pos.floatValue = springAt(springFrom, springTo, springV, s)
                }
            }
            if (holding) {
                pos.floatValue = rubber((fingerX + grab.floatValue) / widthDp)
                val nowSide = if (pos.floatValue < STOP_MIN) -1 else if (pos.floatValue > STOP_MAX) 1 else 0
                if (nowSide != 0 && nowSide != side) onStop()
                side = nowSide
            } else if (film) {
                if (mode == MODE_MANUAL && now - lastTouch > IDLE_S) {
                    startSpring(REST, 0f, now)
                    eveningTween.start(0f, RETURN_S, now, ::easeInOutCubic)
                    returnAt = now
                    mode = MODE_RETURN
                } else if (mode == MODE_RETURN && now - returnAt > RETURN_S + 0.5f) {
                    loopStart = now
                    stopPos()
                    pressTween.stop()
                    ghostTween.stop()
                    eveningTween.stop()
                    pos.floatValue = REST
                    stretch.floatValue = 0f
                    mode = MODE_AUTO
                }
            }
            val p = pos.floatValue
            val target = stretchFor((p - prev) / dt)
            val k = 1f - exp(-dt / SPEED_TAU)
            val s = stretch.floatValue + (target - stretch.floatValue) * k
            stretch.floatValue = if (abs(s) < 0.002f) 0f else s
            glint.floatValue = glintAt((now % LOOP).toFloat())
        }
        prev = pos.floatValue
        syncSelected()
    }

    /** A finger anywhere: the film stops where it is. */
    private fun take() {
        lastTouch = clock
        if (mode == MODE_MANUAL) return
        mode = MODE_MANUAL
        stopPos()
        pressTween.start(0f, 0.16f, clock)
        ghostTween.start(0f, 0.16f, clock)
    }

    fun down(xDp: Float) {
        take()
        stopPos()
        holding = true
        side = 0
        fingerX = xDp
        val d = pos.floatValue * widthDp - xDp
        grabTween.stop()
        grab.floatValue = d
        if (abs(d) > GRAB_R) grabTween.start(0f, if (reduced) 0f else GLIDE_S, clock)
        pressTween.start(1f, 0.12f, clock)
        onPress()
    }

    fun move(xDp: Float) {
        fingerX = xDp
        lastTouch = clock
    }

    /** [velocityDp] is the finger's speed along x, dp/s. */
    fun up(velocityDp: Float) {
        if (!holding) return
        holding = false
        side = 0
        lastTouch = clock
        pressTween.start(0f, 0.18f, clock)
        val p = pos.floatValue
        val v = velocityDp / widthDp
        if (reduced) {
            posTween.start(clamp(p, STOP_MIN, STOP_MAX), 0.16f, clock)
        } else {
            startSpring(landing(p, v), v, clock)
        }
    }

    fun choose(index: Int) {
        selected.intValue = index
        eveningSide = index
        lastTouch = clock
        if (mode != MODE_MANUAL) {
            mode = MODE_MANUAL
            stopPos()
            pressTween.start(0f, 0.16f, clock)
            ghostTween.start(0f, 0.16f, clock)
        }
        eveningTween.start(index.toFloat(), if (reduced) 0.2f else EVENING_S, clock, ::easeInOutCubic)
    }

    fun nudge(direction: Int) {
        lastTouch = clock
        mode = MODE_MANUAL
        pressTween.stop()
        ghostTween.stop()
        ghost.floatValue = 0f
        press.floatValue = 0f
        springing = false
        posTween.start(clamp(pos.floatValue + direction * STEP, STOP_MIN, STOP_MAX), 0.2f, clock)
    }
}
