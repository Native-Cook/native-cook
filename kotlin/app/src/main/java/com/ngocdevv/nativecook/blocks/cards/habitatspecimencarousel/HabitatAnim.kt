package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A value that can be set, or moved by `withTiming` / `withSpring` like Reanimated's shared values,
 * and is stepped once a frame by the motion clock. A new animation starts from the current value;
 * a spring started over a running spring keeps its velocity, as Reanimated hands it on.
 */
internal class HabitatAnim(var value: Float = 0f) {
    private enum class Mode { Still, Timing, DurationSpring, PhysicalSpring }

    private var mode = Mode.Still
    var velocity = 0f
        private set
    private var target = value
    private var from = value
    private var elapsed = 0f
    private var duration = 1f
    private var easing: Easing = LinearEasing
    private var zeta = 1f
    private var omega0 = 0f
    private var stiffness = 0f
    private var initialEnergy = 0f
    private var x0 = 0f
    private var v0 = 0f

    val running get() = mode != Mode.Still

    /** `cancelAnimation(x)`: the value stays where it is. */
    fun cancel() {
        mode = Mode.Still
        velocity = 0f
    }

    fun set(next: Float) {
        mode = Mode.Still
        velocity = 0f
        value = next
        target = next
    }

    /** `withTiming(to, { duration, easing })`. */
    fun timing(to: Float, ms: Float, easing: Easing = LinearEasing) {
        mode = Mode.Timing
        from = value
        target = to
        elapsed = 0f
        duration = ms / 1000f
        this.easing = easing
        velocity = 0f
    }

    /** `withSpring(to, { duration, dampingRatio })`: the stiffness is solved so the spring's energy falls to the threshold after 1.5 x the duration. */
    fun spring(to: Float, ms: Float, dampingRatio: Float, always: Boolean = false) {
        if (always) { set(to); return }
        val carried = if (mode == Mode.DurationSpring || mode == Mode.PhysicalSpring) velocity else 0f
        x0 = value - to
        v0 = carried
        target = to
        zeta = dampingRatio
        duration = ms / 1000f
        stiffness = stiffnessFor(x0, v0, duration, zeta)
        omega0 = sqrt(stiffness)
        initialEnergy = 0.5f * stiffness * x0 * x0 + 0.5f * v0 * v0
        elapsed = 0f
        velocity = v0
        if (initialEnergy > 0f) mode = Mode.DurationSpring else set(to)
    }

    /** `withSpring(to, { damping, mass, stiffness, velocity })`. */
    fun spring(to: Float, damping: Float, mass: Float, stiffness: Float, velocity: Float = 0f, always: Boolean = false) {
        if (always) { set(to); return }
        val carried = if (mode == Mode.DurationSpring || mode == Mode.PhysicalSpring) this.velocity else 0f
        x0 = value - to
        v0 = carried + velocity
        target = to
        omega0 = sqrt(stiffness / mass)
        zeta = damping / (2f * sqrt(stiffness * mass))
        this.stiffness = stiffness / mass
        elapsed = 0f
        this.velocity = v0
        mode = Mode.PhysicalSpring
    }

    fun step(dt: Float) {
        when (mode) {
            Mode.Still -> Unit
            Mode.Timing -> {
                elapsed += dt
                val p = clamp(elapsed / duration)
                value = from + (target - from) * easing.transform(p)
                if (p >= 1f) { value = target; mode = Mode.Still }
            }
            Mode.DurationSpring, Mode.PhysicalSpring -> {
                elapsed += dt
                val t = elapsed
                if (zeta < 1f) {
                    val omega1 = omega0 * sqrt(1f - zeta * zeta)
                    val envelope = exp(-zeta * omega0 * t)
                    val a = (v0 + zeta * omega0 * x0) / omega1
                    val fragment = envelope * (x0 * cos(omega1 * t) + a * sin(omega1 * t))
                    value = target + fragment
                    velocity = -zeta * omega0 * fragment + envelope * (a * omega1 * cos(omega1 * t) - x0 * omega1 * sin(omega1 * t))
                } else {
                    val envelope = exp(-omega0 * t)
                    val b = v0 + omega0 * x0
                    value = target + envelope * (x0 + b * t)
                    velocity = -omega0 * envelope * (x0 + b * t) + envelope * b
                }
                val dx = target - value
                val rest = if (mode == Mode.DurationSpring) {
                    (0.5f * stiffness * dx * dx + 0.5f * velocity * velocity) / initialEnergy <= ENERGY_THRESHOLD
                } else {
                    abs(dx) < REST_DISPLACEMENT && abs(velocity) < REST_SPEED
                }
                if (rest) { value = target; velocity = 0f; mode = Mode.Still }
            }
        }
    }

    private companion object {
        const val ENERGY_THRESHOLD = 6e-9f
        const val REST_DISPLACEMENT = 0.01f
        const val REST_SPEED = 0.01f

        fun stiffnessFor(x0: Float, v0: Float, durationS: Float, zeta: Float): Float {
            if (x0 == 0f && v0 == 0f) return 0f
            val settling = durationS * 1.5
            fun diff(k: Double): Double {
                val omega = sqrt(k) * zeta
                val a = x0 + (v0 + x0 * omega) * settling
                val e = exp(-omega * settling)
                val xt = a * e
                val vt = a * e * -omega + (v0 + x0 * omega) * e
                val e0 = 0.5 * k * x0 * x0 + 0.5 * v0 * v0
                val et = 0.5 * k * xt * xt + 0.5 * vt * vt
                return et / e0 - ENERGY_THRESHOLD
            }
            var lo = Math.ulp(1.0)
            var hi = 8e3
            val rising = diff(hi) >= diff(lo)
            var k = (lo + hi) / 2
            var left = 100
            while (abs(diff(k)) > ENERGY_THRESHOLD * 1e-3 && left > 0) {
                left -= 1
                if ((diff(k) < 0) == rising) lo = k else hi = k
                k = (lo + hi) / 2
            }
            return if (k.isFinite()) k.toFloat() else 170f
        }
    }
}
