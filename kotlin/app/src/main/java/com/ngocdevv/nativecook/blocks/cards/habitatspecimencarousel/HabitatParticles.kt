package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Particles that outlive a signature move, with their physics in seconds (FX clock). Distances are
 * in `unit`s (points at a 344 pt creature). At this scale a metre is about 330 points: a leaf falls
 * at under a metre a second, as a real one does; drops fall at about half real gravity, a touch of
 * slow motion that keeps the spray readable.
 *
 * This is the analytic leaf physics of the reference's HabitatParticles.ts, used when a frame is
 * frozen for review (the live particle world is HabitatParticleWorld).
 */
private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = max(0f, min(1f, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)
}

private fun hash(n: Float): Float {
    val s = sin(n * 127.1f + 311.7f) * 43758.5453f
    return s - floor(s)
}

/** Gravity on a drop of water, in units per second^2. */
internal const val DROP_GRAVITY = 1500f

private class LeafPath(val x: Float, val y: Float, val angle: Float, val roll: Float)

/** A leaf's steady fall speed once its burst is spent. */
private fun leafFall(i: Int, unit: Float): Float = (210 + 70 * hash(i * 7.31f + 5)) * unit

/**
 * One leaf thrown out of the bulb. A leaf is mostly drag: its burst dies away exponentially while it
 * settles to a terminal fall, and once slow it flutters like a pendulum: it swings side to side,
 * rises a little at each end of the swing, tilts into it and rolls on its long axis (2.5D). Its
 * spin from the burst decays into that flat, falling attitude.
 */
private fun leafPath(i: Int, t: Float, unit: Float): LeafPath {
    fun h(k: Int) = hash(i * 7.31f + k)
    val launch = -PI.toFloat() / 2 + ((i + 0.5f) / LEAVES - 0.5f) * 2.6f + (h(2) - 0.5f) * 0.24f
    val speed = (220 + 90 * h(3)) * unit
    val drag = 2.3f + 0.8f * h(4)
    val fall = leafFall(i, unit)
    val wind = (14 + 14 * h(6)) * unit
    val omega = (PI.toFloat() * 2) / (0.85f + 0.45f * h(7))
    val phase = PI.toFloat() * 2 * h(8)
    val swing = (11 + 11 * h(9)) * unit
    val decay = exp(-drag * t)
    val spent = (1 - decay) / drag
    val flutter = (1 - decay) * (1 - decay)
    val sway = omega * t + phase
    val spin = (h(10) - 0.5f) * 24
    val tumble = (h(11) * PI.toFloat() * 2) + spin * spent
    // The burst's spin dies into a near-flat attitude (long axis across the fall).
    val flat = jsRoundF(tumble / PI.toFloat()) * PI.toFloat()
    return LeafPath(
        x = (cos(launch) * speed - wind) * spent + wind * t + swing * flutter * sin(sway),
        y = (sin(launch) * speed - fall) * spent + fall * t + swing * 0.22f * flutter * cos(2 * sway),
        angle = tumble + (flat - tumble) * flutter + 0.55f * flutter * cos(sway),
        roll = (h(12) - 0.5f) * 18 * spent + 0.75f * flutter * sin(sway),
    )
}

private fun jsRoundF(v: Float): Float = floor(v + 0.5f)

/**
 * Every leaf for one frame: position and attitude (x, y, angle, roll) and its state (opacity, size,
 * how settled on the ground, layer: 0 behind the creature, 1 in front). A leaf lands on the floor
 * at its own depth, comes to rest flat in a quarter of a second, lies there, then fades.
 */
internal fun leafUniforms(seconds: Float, originX: Float, originY: Float, floorY: Float, unit: Float): Pair<FloatArray, FloatArray> {
    val leaf = FloatArray(LEAVES * 4)
    val state = FloatArray(LEAVES * 4)
    val ending = 1 - smooth(FX_S - 0.5f, FX_S, seconds)
    for (i in 0 until LEAVES) {
        fun h(k: Int) = hash(i * 7.31f + k)
        val born = 0.56f + 0.022f * i + 0.02f * h(1)
        val age = seconds - born
        if (age <= 0 || ending <= 0) continue
        // Some fall behind the creature, the rest in front of it: the floor recedes for the far ones.
        val behind = h(13) < 0.4f
        val depth = (if (behind) -(4 + 14 * h(14)) else 3 + 18 * h(14)) * unit
        val ground = floorY + depth - originY
        // First touch of the floor. Once the burst is spent the fall is close to steady, so Newton's
        // method from the steady-fall estimate converges in a few steps (the flutter never reverses it).
        val fall = leafFall(i, unit)
        var landed = max(0.3f, ground / fall)
        for (k in 0 until 5) {
            val y = leafPath(i, landed, unit).y - ground
            val slope = (leafPath(i, landed + 0.01f, unit).y - ground - y) / 0.01f
            landed = max(0.3f, landed - y / max(slope, fall * 0.2f))
        }
        val scale = (1 + depth / (260 * unit)) * (0.62f + 0.38f * smooth(0f, 0.14f, age))
        if (age < landed) {
            val p = leafPath(i, age, unit)
            leaf[i * 4] = originX + p.x; leaf[i * 4 + 1] = originY + p.y; leaf[i * 4 + 2] = p.angle; leaf[i * 4 + 3] = p.roll
            state[i * 4] = smooth(0f, 0.06f, age) * ending; state[i * 4 + 1] = scale; state[i * 4 + 2] = 0f; state[i * 4 + 3] = if (behind) 0f else 1f
            continue
        }
        // On the floor: a short slide in the direction it was moving, then flat and still.
        val touch = leafPath(i, landed, unit)
        val after = leafPath(i, landed + 0.02f, unit)
        val rest = 1 - (1 - min(1f, (age - landed) / 0.26f)).pow(3)
        val lying = jsRoundF(touch.angle / PI.toFloat()) * PI.toFloat() + (h(15) - 0.5f) * 0.5f
        val rock = 0.14f * sin(rest * PI.toFloat() * 2) * (1 - rest)
        val roll = touch.roll + ((if (sign(cos(touch.roll)) > 0) 1.05f else PI.toFloat() - 1.05f) - touch.roll) * rest
        leaf[i * 4] = originX + touch.x + (after.x - touch.x) * 2.5f * rest
        leaf[i * 4 + 1] = originY + ground
        leaf[i * 4 + 2] = touch.angle + (lying - touch.angle) * rest + rock
        leaf[i * 4 + 3] = roll
        state[i * 4] = (1 - smooth(0.55f, 1.05f, age - landed)) * ending; state[i * 4 + 1] = scale; state[i * 4 + 2] = rest; state[i * 4 + 3] = if (behind) 0f else 1f
    }
    return leaf to state
}
