package com.ngocdevv.nativecook.blocks.cards.frostheartrewards

import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.CLAIMS
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.LOOP
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.Scene
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.VALUES
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.clamp
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.ease
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.seg
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.tanh

/**
 * The frame callback and gesture handlers of FrostHeartRewards.tsx on one thread.
 * Modes: 0 demo, 1 manual polish/claim, 2 the Atelier sequence, 3 the blurred return.
 */
internal class FrostHeartRewardsDriver(private val autoPlay: Boolean, var reduced: Boolean) {
    /** Cue names: claim0..3, frost, tinkle, cutComplete, level, snap, impact. */
    var cue: (String) -> Unit = {}
    var announce: (balance: Int, level: Int, mask: Int) -> Unit = { _, _, _ -> }
    var session: (Boolean) -> Unit = {}

    private class Tween(var value: Double) {
        private var from = value; private var to = value; private var start = 0.0; private var duration = 0.0
        private var curve: (Double) -> Double = { it }
        var done: (() -> Unit)? = null
        val running get() = duration > 0
        fun animate(target: Double, seconds: Double, now: Double, easing: (Double) -> Double, then: (() -> Unit)? = null) {
            from = value; to = target; start = now; duration = seconds; curve = easing; done = then
            if (seconds <= 0) { value = target; duration = 0.0; then?.invoke() }
        }
        fun set(v: Double) { value = v; duration = 0.0 }
        fun step(now: Double) {
            if (duration <= 0) return
            val u = clamp((now - start) / duration)
            value = from + (to - from) * curve(u)
            if (u >= 1) { duration = 0.0; val d = done; done = null; d?.invoke() }
        }
    }
    private class Spring(val damping: Double, val stiffness: Double) {
        var value = 0.0; var velocity = 0.0; var active = false
        fun step(dt: Double) {
            if (!active) return
            var left = dt
            while (left > 0) {
                val h = min(left, 1.0 / 240)
                velocity += (-stiffness * value - damping * velocity) * h
                value += velocity * h
                left -= h
            }
            if (abs(value) < 0.0005 && abs(velocity) < 0.005) { value = 0.0; velocity = 0.0; active = false }
        }
    }
    private val quad = { u: Double -> if (u < 0.5) 2 * u * u else 1 - (-2 * u + 2) * (-2 * u + 2) / 2 }
    private val cubicOut = { u: Double -> FrostHeartRewardsMotion.outCubic(u) }

    var time = 0.0; private set
    var local = 0.0; private set
    private var clock = 0.0
    var interactive = false; private set
    var mode = 0; private set
    private var touchedAt = -10000.0
    private val claims = DoubleArray(5) { NEVER }
    private var finger = false
    private var moved = false
    private var holdWall = 0.0
    private var polishX = 0.0
    private val breath = Tween(0.0)
    private var breathX = 133.0
    private var breathY = 258.0
    private var target = 0
    private val charge = Tween(0.0)
    private val dragX = Spring(16.0, 130.0)
    private val dragY = Spring(16.0, 130.0)
    private val jellySpring = Spring(20.0, 230.0)
    var ring = 0.0; private set
    private var ringVelocity = 0.0
    private var reset = 0.0
    private val lowMotion = Tween(0.0)
    var touchSession = false; private set

    var sensorRoll = 0.0
    var sensorPitch = 0.0
    val jelly get() = jellySpring.value
    val tiltX get() = if (reduced) 0.0 else clamp(dragX.value + sensorRoll * 5, -8.0, 8.0)
    val tiltY get() = if (reduced) 0.0 else clamp(dragY.value + sensorPitch * 3, -8.0, 8.0)

    var scene: Scene = computeScene(); private set

    private fun wall() = System.nanoTime() / 1e9

    fun computeScene(): Scene {
        val s = FrostHeartRewardsMotion.sceneAt(time)
        if (!interactive) return s
        var balance = 1650.0
        for (i in 0 until 5) if (claims[i] >= 0) {
            val age = local - claims[i] - (if (reduced) 0.0 else 0.65)
            balance += VALUES[i] * ease(seg(age, 0.0, if (reduced) 0.16 else 0.18))
        }
        s.balance = balance
        s.remaining = maxOf(0.0, 65 - (balance - 1650))
        s.polish = if (reduced) 0.0 else charge.value
        s.polishX = polishX
        s.breath = if (time < 2.4) breath.value else 0.0
        s.breathX = breathX; s.breathY = breathY
        if (mode == 3) s.reset = reset
        if (reduced) {
            var from = 1650.0; var to = 1650.0; var blend = 1.0
            for (i in 0 until 5) if (claims[i] >= 0) {
                val age = local - claims[i]
                if (age >= 0.16) { from += VALUES[i]; to += VALUES[i] }
                else if (age >= 0) { to += VALUES[i]; blend = min(blend, ease(clamp(age / 0.16))) }
            }
            val lm = lowMotion.value
            s.balanceFrom = from; s.balanceTo = to; s.numberFade = blend; s.balance = to
            s.remaining = maxOf(0.0, 65 - (to - 1650))
            s.gem = lm; s.hue = lm; s.flip = lm; s.level = if (lm > 0.5) 4 else 3
            s.charge = 0.0; s.core = 0.0; s.display = 0.0; s.spotlight = lm; s.cut = lm
        }
        return s
    }

    /** Ages of the seven daily chips for their fill, press and check (the first two are always claimed). */
    fun chipAge(i: Int, s: Scene, live: Boolean): Double = when {
        i < 2 -> 1000.0
        live && interactive -> if (claims[i - 2] < 0) -1000.0 else local - claims[i - 2]
        else -> s.t - CLAIMS[i - 2]
    }
    /** Ages of the five claimable pearls; reduced motion shows none. */
    fun pearlAge(i: Int, s: Scene): Double = when {
        interactive -> if (claims[i] >= 0) (if (reduced) 0.0 else local) - claims[i] else -1000.0
        i == 3 -> s.t - 0.6
        i == 4 -> s.t - 0.95
        else -> -1000.0
    }
    fun claimed(i: Int) = i < 2 || (interactive && claims[i - 2] >= 0)

    private fun mask(): Pair<Int, Int> {
        var mask = 0; var value = 1650
        for (i in 0 until 5) if (claims[i] >= 0) { mask = mask or (1 shl i); value += VALUES[i] }
        return mask to value
    }
    fun report() { val (m, v) = mask(); announce(if (interactive) v else 1650, computeScene().level, m) }

    private fun takeOver() {
        if (!interactive) {
            for (i in 0 until 5) claims[i] = if (time < 9.9 && time >= CLAIMS[i]) local - (time - CLAIMS[i]) else NEVER
            mode = if (time >= 1.7 && time < 8.9) 2 else 1
            if (mode == 1) time = 0.0
            touchSession = true
            session(true)
        }
        if (mode == 3) { mode = 1; time = 0.0; charge.set(0.0) }
        interactive = true
        touchedAt = local
        reset = 0.0
    }

    fun claim(i: Int) {
        takeOver()
        if (claims[i] >= 0) return
        claims[i] = local
        cue("claim${min(i, 3)}")
        scene = computeScene()
    }

    /** Accessibility: reveal the next level from the heart, as the custom action does. */
    fun reveal() {
        takeOver()
        if (reduced) lowMotion.animate(1.0, 0.22, clock, quad) { report() }
        else { time = 1.7; mode = 2 }
        cue("frost")
    }
    fun bounceBear() { takeOver(); jellySpring.value = 1.0; jellySpring.velocity = 0.0; jellySpring.active = true; cue("impact") }

    fun down(x: Double, y: Double) {
        takeOver()
        finger = true; moved = false
        holdWall = wall()
        ringVelocity = 0.0
        polishX = clamp((x - 175) / 105, -1.2, 1.2)
        target = if (x > 75 && x < 283 && y > 225 && y < 367) 1 else 2
        if (target == 1 && time < 2.4) {
            breathX = x; breathY = y
            breath.animate(1.0, if (reduced) 0.22 else 0.30, clock, cubicOut)
        }
        scene = computeScene()
    }

    /** [tx]/[ty] are the finger's translation in dp, the reference's screen points. */
    fun move(x: Double, y: Double, tx: Double, ty: Double) {
        touchedAt = local
        if (target == 1 && time < 2.4) { breathX = x; breathY = y }
        polishX = clamp((x - 175) / 105, -1.2, 1.2)
        if (hypot(tx, ty) > 8) moved = true
        if (!moved || reduced) return
        dragX.value = 8 * tanh(tx / 95); dragX.velocity = 0.0; dragX.active = false
        dragY.value = 8 * tanh(ty / 95); dragY.velocity = 0.0; dragY.active = false
        ring = 0.61 * tanh(tx / 105)
        if (mode == 1 && !charge.running && charge.value > 0) charge.animate(0.0, 0.4, clock, quad)
    }

    /** [velocityX] in dp per second. */
    fun up(velocityX: Double, cancelled: Boolean) {
        if (moved && !reduced && !cancelled) ringVelocity = velocityX * 0.003
        if (!cancelled && !moved && wall() - holdWall < 0.25) {
            if (target == 2) {
                jellySpring.value = 1.0; jellySpring.velocity = 0.0; jellySpring.active = true
                cue("impact")
            } else if (reduced) {
                lowMotion.animate(if (lowMotion.value > 0.5) 0.0 else 1.0, 0.22, clock, quad) { report() }
                cue("level")
            }
        }
        report()
        finger = false
        breath.animate(0.0, 1.2, clock, cubicOut)
        touchedAt = local
        dragX.active = true; dragY.active = true
        charge.animate(0.0, if (reduced) 0.16 else 0.24, clock, quad)
    }

    /** Whether the frame loop has anything to do (the reference deactivates its frame callback). */
    val running get() = !reduced || touchSession

    fun step(dt: Double) {
        clock += dt
        breath.step(clock); charge.step(clock); lowMotion.step(clock)
        dragX.step(dt); dragY.step(dt); jellySpring.step(dt)
        if (ringVelocity != 0.0) {
            ring += ringVelocity * dt
            ringVelocity *= exp(-(1 - 0.985) * dt * 1000)
            if (ring <= -0.61 || ring >= 0.61) { ring = clamp(ring, -0.61, 0.61); ringVelocity = 0.0 }
            if (abs(ringVelocity) < 1e-4) ringVelocity = 0.0
        }
        local += dt
        val before = time
        if (!interactive) {
            time = if (autoPlay && !reduced) (before + dt) % LOOP else 0.0
            // untouched autoplay stays silent: the cut's ting and haptic answer a finger only
            scene = computeScene()
            return
        }
        if (finger && !moved && target == 1 && !reduced) {
            val age = wall() - holdWall - 0.18
            if (age > 0) charge.set(ease(clamp(age / 0.8)))
        }
        if (mode == 1 && time == 0.0 && computeScene().remaining <= 0 && !finger) {
            if (reduced) {
                time = 8.9
                lowMotion.animate(1.0, 0.22, clock, quad) { report() }
                cue("level")
            } else {
                time = 1.7; mode = 2; cue("frost")
            }
        }
        if (mode == 2) {
            val playing = time
            if (!finger) time = min(8.9, playing + dt)
            val after = time
            if (!reduced && playing < 3.8 && after >= 3.8) cue("cutComplete")
            if (playing < 2.4 && after >= 2.4) cue("frost")
            for (k in 0 until 12) {
                val at = 5.8 + k * 0.04
                if (k % 3 == 0 && playing < at && after >= at) cue("snap")
            }
            if (playing < 6.95 && after >= 6.95) {
                cue("level")
                val (m, v) = mask()
                announce(v, 4, m)
            }
            if (after >= 8.9) mode = 1
        }
        for (i in 0 until 5) {
            if (claims[i] < 0) continue
            val age = local - claims[i]
            if (age >= 0.65 && age - dt < 0.65 && !reduced) cue("tinkle")
            val settled = if (reduced) 0.16 else 0.83
            if (age >= settled && age - dt < settled) { val (m, v) = mask(); announce(v, scene.level, m) }
        }
        if (!finger && mode != 3 && local - touchedAt > 2) { mode = 3; reset = 0.0 }
        if (mode == 3) {
            reset = min(1.0, reset + dt / (if (reduced) 0.25 else 1.1))
            if (reset >= 1) {
                interactive = false
                mode = 0; time = 0.0
                charge.set(0.0); lowMotion.set(0.0)
                for (i in 0 until 5) claims[i] = NEVER
                ring = 0.0; ringVelocity = 0.0
                announce(1650, 3, 0)
                touchSession = false
                session(false)
            }
        }
        scene = computeScene()
    }

    companion object { const val NEVER = -1000.0 }
}
