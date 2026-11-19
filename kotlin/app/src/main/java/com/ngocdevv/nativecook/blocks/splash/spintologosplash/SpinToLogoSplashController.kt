package com.ngocdevv.nativecook.blocks.splash.spintologosplash

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The block's state machine (SpinToLogoSplash.tsx), stepped once per display frame on the main thread. It owns the film
 * time `t`, the turn `yaw` and the ghost finger's visibility `live`; touches change its fields, and every frame reads
 * them back. Times are in nanoseconds of the frame clock.
 */
internal class SpinController(private val film: Boolean, private val reduced: Boolean) {
    var t = if (reduced) REDUCED_T else 0f
        private set
    var yaw = 0f
        private set
    var live = if (film) 1f else 0f
        private set
    var mode = if (reduced) MODE_STORY else if (film) MODE_AUTO else MODE_MANUAL
        private set

    private var clock = 0L
    private var loopStart = -1L
    private var storyStart = -1L
    private var lastActivity = -1L
    var dragging = false
        private set
    private var spinning = false
    private var yawFrom = 0f

    // yaw animations
    private var yawAnim = ANIM_NONE
    private var animT0 = 0L
    private var animFrom = 0f
    private var animTo = 0f
    private var animV0 = 0f
    private var tFrom = 0f
    private var liveFadeT0 = -1L
    private var liveFadeFrom = 1f

    /** Called with true at each 45° detent while a finger turns; false for the unlock (success). */
    var onHaptic: (Boolean) -> Unit = {}
    private var lastDetent = 0

    fun frame(now: Long, ready: Boolean) {
        clock = now
        stepAnimations(now)
        when (mode) {
            MODE_AUTO -> {
                if (!film) {
                    mode = MODE_MANUAL; live = 0f; return
                }
                if (!ready) {
                    t = 0f; return
                }
                if (loopStart < 0) loopStart = now
                val tt = ((now - loopStart) / 1e9 % LOOP).toFloat()
                t = tt
                yaw = filmYaw(tt)
            }
            MODE_STORY -> {
                if (reduced) {
                    t = REDUCED_T; return
                }
                if (lastActivity < 0) lastActivity = now
                val tt = min(STORY_T0 + (now - storyStart) / 1e9f, SpinPhase.HOLD_END)
                t = tt
                if (tt >= SpinPhase.HOLD_END && film && now - lastActivity >= IDLE_MS * 1_000_000) {
                    yawAnim = ANIM_NONE
                    yaw = 360f
                    loopStart = now - (SpinPhase.HOLD_END * 1e9).toLong()
                    live = 1f
                    mode = MODE_AUTO
                }
            }
            MODE_MANUAL -> {
                if (lastActivity < 0 || dragging || spinning) lastActivity = now
                if (abs(yaw) >= 360f) {
                    unlock(); return
                }
                if (!film || now - lastActivity < IDLE_MS * 1_000_000) return
                mode = MODE_RETURN
                yawAnim = ANIM_RETURN
                animT0 = now
                animFrom = yaw
                tFrom = t
            }
            else -> Unit
        }
    }

    private fun stepAnimations(now: Long) {
        if (liveFadeT0 >= 0) {
            val k = clamp01((now - liveFadeT0) / 220e6f)
            live = liveFadeFrom * (1 - k)
            if (k >= 1f) liveFadeT0 = -1
        }
        val s = (now - animT0) / 1e9f
        when (yawAnim) {
            ANIM_DECAY -> {
                // Reanimated's withDecay: v = v0·d^ms, x = x0 + v0/1000 · d(1 − d^ms)/(1 − d)
                val ms = s * 1000f
                val kv = DECEL.pow(ms)
                yaw = animFrom + animV0 / 1000f * DECEL * (1 - kv) / (1 - DECEL)
                if (abs(animV0 * kv) < 1f) {
                    yawAnim = ANIM_NONE; spinning = false
                }
            }
            ANIM_SPRING -> {
                // A damped spring settling in about 0.5 s with a damping ratio of 0.85.
                val zeta = 0.85f
                val w0 = 11f
                val wd = w0 * sqrt(1 - zeta * zeta)
                val d = animFrom - animTo
                val e = exp(-zeta * w0 * s)
                yaw = animTo + d * e * (cos(wd * s) + zeta * w0 / wd * sin(wd * s))
                if (s > 1.2f) {
                    yaw = animTo; yawAnim = ANIM_NONE
                }
            }
            ANIM_RETURN -> {
                val k = clamp01(s * 1000f / RETURN_MS)
                val e = EASE_IN_OUT.transform(k)
                yaw = lerp(animFrom, 0f, e)
                t = lerp(tFrom, 0f, e)
                if (k >= 1f) {
                    yawAnim = ANIM_NONE
                    t = 0f
                    loopStart = -1
                    live = 1f
                    mode = MODE_AUTO
                }
            }
        }
        val detent = floor(yaw / DETENT_DEG).toInt()
        if (detent != lastDetent) {
            if (mode == MODE_MANUAL && (dragging || spinning)) onHaptic(true)
            lastDetent = detent
        }
    }

    private fun unlock() {
        dragging = false
        spinning = false
        animFrom = yaw
        animTo = if (yaw < 0) -360f else 360f
        animT0 = clock
        yawAnim = ANIM_SPRING
        t = STORY_T0
        storyStart = clock
        lastActivity = clock
        mode = MODE_STORY
        onHaptic(false)
    }

    /** A finger came down anywhere (the pan's onBegin). */
    fun touch() {
        lastActivity = -1
        when (mode) {
            MODE_RETURN -> {
                yawAnim = ANIM_NONE
                mode = MODE_MANUAL
            }
            MODE_AUTO -> {
                val tt = t
                if (tt < SpinPhase.TURN_END) {
                    liveFadeT0 = clock
                    liveFadeFrom = live
                    mode = MODE_MANUAL
                } else if (tt < SpinPhase.HOLD_END) {
                    storyStart = clock - ((tt - STORY_T0) * 1e9).toLong()
                    mode = MODE_STORY
                }
            }
        }
    }

    fun dragStart() {
        if (mode != MODE_MANUAL) return
        if (yawAnim != ANIM_RETURN) yawAnim = ANIM_NONE
        dragging = true
        spinning = false
        yawFrom = yaw
    }

    fun dragUpdate(translationX: Float) {
        if (mode != MODE_MANUAL) return
        yaw = yawFrom + translationX * YAW_PER_PT
    }

    fun dragEnd(velocityX: Float) {
        dragging = false
        if (mode != MODE_MANUAL || reduced) return
        spinning = true
        yawAnim = ANIM_DECAY
        animT0 = clock
        animFrom = yaw
        animV0 = velocityX * YAW_PER_PT
    }

    fun skip() {
        touch()
        if (mode == MODE_MANUAL) unlock()
    }

    companion object {
        const val MODE_AUTO = 0
        const val MODE_MANUAL = 1
        const val MODE_STORY = 2
        const val MODE_RETURN = 3
        private const val ANIM_NONE = 0
        private const val ANIM_DECAY = 1
        private const val ANIM_SPRING = 2
        private const val ANIM_RETURN = 3
        const val STORY_T0 = SpinPhase.TURN_END - 0.05f
        const val REDUCED_T = 10f
        private const val DETENT_DEG = 45f
        private const val DECEL = 0.996f
        private val EASE_IN_OUT = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    }
}
