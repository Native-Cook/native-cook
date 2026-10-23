package com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral

import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.rest
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.entrance
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.loop
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.touchRipple
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.rubberPitch
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.FUSE
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.SETTLE
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.lerp
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.landedSpring
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.nearestRest
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.REST_PITCH
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.bezier
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.TAU
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.loosen
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.clamp01
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.coastYaw
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.progress
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.cubicOut
import com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral.ZeroTimeline.glintPulse
import kotlin.math.*

/** One clock owns the authored loop, immutable touch snapshots, and the return to autoplay. */
internal class ZeroMotion(private val autoPlay: Boolean, var reduced: Boolean, private val haptic: (Int) -> Unit) {
    enum class Action { Idle, Down, Drag, Hold, Coast, Tap, Refuse, ButtonDown, ButtonTurn }
    private data class Touch(
        var action: Action = Action.Idle,
        var started: Double = 0.0,
        var touched: Double = 0.0,
        var yaw: Double = -0.33,
        var pitch: Double = -0.1,
        var fuse: Double = 1.0,
        var scale: Double = 1.0,
        var velocity: Double = 0.0,
        var holdable: Boolean = false,
        var x: Double = 0.0,
        var y: Double = 0.0,
        var rippleX: Double = 0.0,
        var rippleY: Double = 0.0,
        var snapshot: ZeroFrame = ZeroFrame(),
        var caught: Double = -1.0,
    )
    var frame = if (reduced) rest(0.0, true) else entrance(0.9)
        private set
    var now = 0.0
        private set
    private var playhead = 0.9
    private var mode = 0
    private var touch = Touch()
    private var lastTick = -1.0
    var centreX = 214.0
    var centreY = 286.0
    var unit = 158.0
    val actionName get() = touch.action.name

    private fun catch(action: Action, holdable: Boolean = true, x: Double = centreX, y: Double = centreY) {
        touch = Touch(action, now, now, frame.yaw, frame.pitch, frame.fuse, frame.scale,
            holdable = holdable, x = x, y = y, snapshot = frame, caught = now)
        mode = 2
    }
    fun begin(x: Double, y: Double) {
        val dx = (x - centreX) / unit
        val dy = (y - centreY) / unit
        catch(Action.Down, dx * dx / 0.9 + dy * dy / 1.6 < 1, x, y)
    }
    fun change(x: Double, y: Double, translationX: Double, translationY: Double) {
        val s = touch
        s.x = x
        s.y = y
        if (hypot(translationX, translationY) < 6 && s.action != Action.Drag) return
        s.action = Action.Drag
        s.touched = now
        s.holdable = false
        s.yaw = s.snapshot.yaw + translationX / 110
        s.pitch = rubberPitch(s.snapshot.pitch - translationY / 330)
        s.fuse = 1.0
        s.scale = 1.0
    }
    fun release(velocityX: Double, cancelled: Boolean, x: Double, y: Double) {
        val s = touch
        val origin = touchRipple(x, y, frame.yaw, centreX, centreY, unit)
        var action = Action.Coast
        if (!cancelled && s.holdable && s.action == Action.Down) {
            action = Action.Tap
            haptic(0)
        }
        if (s.action == Action.Hold) {
            action = Action.Refuse
            if (!cancelled) haptic(2)
        }
        touch = s.copy(action = action, started = now, touched = now, yaw = frame.yaw, pitch = frame.pitch,
            rippleX = origin.first, rippleY = origin.second, fuse = frame.fuse, scale = frame.scale,
            velocity = if (cancelled) 0.0 else (velocityX / 110).coerceIn(-35.0, 35.0))
    }
    fun activateHero() {
        catch(Action.Tap)
        haptic(0)
    }
    fun pressButton() = catch(Action.ButtonDown, false)
    fun releaseButton(success: Boolean) {
        touch = touch.copy(action = if (success) Action.ButtonTurn else Action.Coast, started = now,
            touched = now, yaw = frame.yaw, pitch = frame.pitch, fuse = frame.fuse, scale = frame.scale, velocity = 0.0)
        if (success) haptic(3)
    }
    fun advance(dt: Double) {
        now += dt
        val previous = frame
        var f: ZeroFrame
        if (mode < 2) {
            if (reduced) {
                f = rest(0.0, true)
                val lightTime = now % 6
                if (autoPlay && now >= 6 && lightTime < 1.1) f.sweep = lerp(-1.0, 1.0, lightTime / 1.1)
            } else {
                val before = playhead
                val next = before + dt
                f = if (mode == 0) entrance(next) else loop(next)
                playhead = next % 12
                if (mode == 0) {
                    if (before < FUSE && next >= FUSE) haptic(2)
                    if (before < FUSE + 0.16 && next >= FUSE + 0.16) haptic(0)
                    if (next >= SETTLE - 1e-9) {
                        mode = if (autoPlay) 1 else 2
                        if (!autoPlay) {
                            f = loop(SETTLE)
                            val landedAt = now - max(0.0, next - SETTLE)
                            touch = touch.copy(yaw = f.yaw, started = landedAt, touched = landedAt)
                        }
                    }
                }
            }
        } else if (mode == 3) {
            val s = touch
            val t = now - s.started
            f = rest(0.0, reduced)
            f.yaw = landedSpring(t, s.yaw, nearestRest(s.yaw), 100.0, 18.0, 0.7)
            f.pitch = landedSpring(t, s.pitch, REST_PITCH, 160.0, 22.0, 0.7)
            f.fuse = lerp(s.fuse, 1.0, bezier(t / 0.3))
            f.scale = lerp(s.scale, 1.0, bezier(t / 0.3))
            if (t >= 0.7) {
                mode = 1
                playhead = 0.0
                f = rest(0.0, reduced)
            }
        } else {
            var s = touch
            if (s.action == Action.Down && s.holdable && now - s.started >= 0.18) {
                s = s.copy(action = Action.Hold, started = now)
                touch = s
                haptic(1)
            }
            f = touchFrame(s)
            val busy = s.action in listOf(Action.Down, Action.Drag, Action.Hold, Action.ButtonDown)
            val t = now - s.started
            val finished = (s.action == Action.Coast && t >= if (reduced) 0.3 else 1.88) ||
                (s.action in listOf(Action.Tap, Action.Refuse) && t >= 0.8) ||
                (s.action == Action.ButtonTurn && t >= 1.1)
            if (finished) touch = s.copy(action = Action.Idle, started = now, yaw = f.yaw, pitch = f.pitch, fuse = 1.0, scale = 1.0)
            if (autoPlay && !busy && now - s.touched >= 3.5) {
                mode = 3
                touch = s.copy(started = now, yaw = f.yaw, pitch = f.pitch, fuse = f.fuse, scale = f.scale)
            }
            val fast = dt > 0 && abs(f.yaw - previous.yaw) / dt > 1.4
            if (!reduced && s.action in listOf(Action.Drag, Action.Coast) && fast &&
                floor(f.yaw / (PI / 2)) != floor(previous.yaw / (PI / 2)) && now - lastTick >= 1.0 / 12) {
                lastTick = now
                haptic(0)
            }
        }
        frame = f
    }
    private fun touchFrame(s: Touch): ZeroFrame {
        val f = rest(now, reduced)
        val t = max(0.0, now - s.started)
        f.yaw = s.yaw
        f.pitch = s.pitch
        when (s.action) {
            Action.Idle -> f.yaw += if (reduced) 0.0 else 0.17 * sin(TAU * t / 6)
            Action.Down, Action.Drag -> {
                f.fuse = s.fuse
                f.scale = s.scale
            }
            Action.Hold -> {
                f.fuse = max(0.22, if (reduced) lerp(s.fuse, 0.22, clamp01(t / 0.2)) else s.fuse - 1 + loosen(t))
                f.scale = if (reduced) 1.0 else lerp(s.scale, 0.97, bezier(t / 0.22))
            }
            Action.Coast -> {
                f.yaw = if (reduced) lerp(s.yaw, nearestRest(s.yaw), bezier(t / 0.3)) else coastYaw(s.yaw, s.velocity, t)
                f.pitch = if (reduced) lerp(s.pitch, REST_PITCH, bezier(t / 0.3)) else landedSpring(t, s.pitch, REST_PITCH, 160.0, 22.0, 0.8)
                f.fuse = lerp(s.fuse, 1.0, bezier(t / 0.3))
                f.scale = lerp(s.scale, 1.0, bezier(t / 0.3))
            }
            Action.Tap, Action.Refuse -> {
                val dip = if (s.action == Action.Tap) 0.1 else 0.0
                val from = if (s.action == Action.Tap) 0.55 else s.fuse
                val release = max(0.0, t - dip)
                f.fuse = if (t < dip) lerp(s.fuse, 0.55, bezier(t / dip)) else if (reduced)
                    lerp(from, 1.0, progress(release, 0.0, 0.2)) else min(1.0, landedSpring(release, from, 1.0, 220.0, 18.0, 0.7))
                f.scale = if (reduced) 1.0 else landedSpring(t, s.scale, 1.0, 260.0, 14.0, 0.7)
                f.rippleX = s.rippleX
                f.rippleY = s.rippleY
                f.ripple = if (release > 0 && release < 0.7) cubicOut(release / 0.7) else 0.0
                f.flash = (if (s.action == Action.Tap) 0.3 else 0.35) * (1 - cubicOut(release / 0.6))
                f.glint = glintPulse(release)
            }
            Action.ButtonDown -> f.buttonPress = bezier(t / 0.12)
            Action.ButtonTurn -> {
                val p = clamp01(t / 1.1)
                f.yaw = s.yaw + if (reduced) 0.0 else TAU * bezier(p, 0.77, 0.0, 0.175, 1.0)
                f.buttonPress = if (reduced) 0.0 else landedSpring(t, 1.0, 0.0, 240.0, 20.0, 0.65)
                f.arrow = if (reduced) 0.0 else 4 * sin(PI * clamp01(t / 0.5))
                f.sweep = if (p < 1) lerp(-1.0, 1.0, p) else -1.0
                f.glint = glintPulse(t - 0.37)
                for (i in 0..2) f.twinkle[i] += if (reduced) 0.0 else 0.12 * sin(PI * p)
            }
        }
        val catchUp = bezier((now - s.caught) / 0.3)
        f.build = lerp(s.snapshot.build, 1.0, catchUp)
        f.drain = lerp(s.snapshot.drain, 0.0, catchUp)
        f.percent = lerp(s.snapshot.percent, 1.0, catchUp)
        f.dim = lerp(s.snapshot.dim, 0.0, catchUp)
        f.card = lerp(s.snapshot.card, 1.0, catchUp)
        f.cardOpacity = lerp(s.snapshot.cardOpacity, 1.0, catchUp)
        for (i in 0..2) {
            f.sparkles[i] = lerp(s.snapshot.sparkles[i], 1.0, catchUp)
            f.sparkleRotation[i] = lerp(s.snapshot.sparkleRotation[i], 0.0, catchUp)
            f.sparkleOpacity[i] = lerp(s.snapshot.sparkleOpacity[i], 1.0, catchUp)
            f.nearDustOpacity[i] *= 1 - f.dim
        }
        for (i in 0..4) {
            f.copy[i] = lerp(s.snapshot.copy[i], 1.0, catchUp)
            f.copyY[i] = lerp(s.snapshot.copyY[i], 0.0, catchUp)
            f.farDustOpacity[i] *= 1 - f.dim
        }
        return f
    }
}
