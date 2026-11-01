package com.ngocdevv.nativecook.blocks.onboarding.droneunfoldconnect

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

internal object DroneUnfoldConnectMotion {
    const val LOOP = 10f
    const val LIFT = 0.9f
    const val STICK_R = 44f
    const val TILT_MAX = 0.35f
    const val DRIFT = 0.2f
    const val YAW = 0.5f
    fun clamp01(v: Float) = v.coerceIn(0f, 1f)
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    fun smooth(a: Float, b: Float, x: Float): Float {
        val u = seg(x, a, b)
        return u * u * (3 - 2 * u)
    }
    fun easeInOut(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeOut(x: Float): Float { val u = 1 - x; return 1 - u * u * u }
    fun stickAt(t: Float): Pair<Float, Float> = Pair(
        easeInOut(seg(t, 4.2f, 4.9f)) - easeInOut(seg(t, 5.5f, 6f)),
        -(easeInOut(seg(t, 6.1f, 6.6f)) - easeInOut(seg(t, 6.9f, 7.3f))),
    )
    data class Frame(
        val unfold: Float, val spin: Float, val lift: Float, val pitch: Float,
        val roll: Float, val drift: Float, val light: Float, val connected: Float,
        val stickX: Float, val stickY: Float, val pressConnect: Float,
        val finger: Float, val fingerAt: Int,
    )
    fun frameAt(t: Float): Frame {
        val unfold = easeInOut(seg(t, 1f, 2.2f)) * (1 - easeInOut(seg(t, 8.6f, 9.6f)))
        val spin = smooth(2.2f, 2.9f, t) * (1 - smooth(8.2f, 8.8f, t))
        val flying = easeOut(seg(t, 2.9f, 3.8f)) * (1 - easeInOut(seg(t, 7.4f, 8.4f)))
        val bob = 0.05f * sin((2 * PI * 3 * t / LOOP).toFloat()) * flying
        val s = stickAt(t - 0.12f)
        val light = smooth(2.3f, 2.5f, t) * (1 - smooth(9.4f, 9.6f, t))
        val connected = smooth(2.9f, 3.8f, t) * (1 - smooth(7.4f, 7.7f, t))
        fun tap(a: Float) = easeOut(seg(t, a - 0.08f, a)) * (1 - easeOut(seg(t, a + 0.06f, a + 0.2f)))
        fun near(a: Float, b: Float) = easeOut(seg(t, a - 0.25f, a)) * (1 - easeOut(seg(t, b, b + 0.2f)))
        val onConnect = near(0.75f, 0.95f)
        val onStick = near(4.1f, 7.35f)
        val st = stickAt(t)
        return Frame(unfold, spin, LIFT * flying + bob, -s.second * TILT_MAX * flying,
            -s.first * TILT_MAX * flying, s.first * DRIFT * flying, light, connected,
            st.first, st.second, tap(0.8f), clamp01(onConnect + onStick), if (onConnect > 0.001f) 0 else 1)
    }
    // Reanimated 4 duration-based spring: mass 4, perceptual duration ×1.5, energy threshold 6e-9.
    private fun springOmega(duration: Double, ratio: Double): Double {
        val settling = duration * 1.5
        val mass = 4.0
        fun energyDifference(stiffness: Double): Double {
            val omega = kotlin.math.sqrt(stiffness / mass) * ratio
            val e = kotlin.math.exp(-omega * settling)
            val x = (1 + omega * settling) * e
            val v = (1 + omega * settling) * e * (-omega) + omega * e
            return (stiffness * x * x + mass * v * v) / stiffness - 6e-9
        }
        var lo = Math.ulp(1.0)
        var hi = 8000.0
        var mid = (lo + hi) / 2
        for (i in 0 until 100) {
            val f = energyDifference(mid)
            if (kotlin.math.abs(f) <= 6e-12) break
            if (f > 0) lo = mid else hi = mid
            mid = (lo + hi) / 2
        }
        return kotlin.math.sqrt(mid / mass)
    }
    private val releaseOmega = springOmega(0.35, 0.9)
    private val takeoverOmega = springOmega(0.3, 1.0)
    fun stickSpring(elapsed: Float, takeover: Boolean): Float {
        val t = elapsed.toDouble()
        val z = if (takeover) 1.0 else 0.9
        val omega = if (takeover) takeoverOmega else releaseOmega
        val e = kotlin.math.exp(-z * omega * t)
        val x: Double
        val v: Double
        if (z < 1) {
            val wd = omega * kotlin.math.sqrt(1 - z * z)
            val frag = e * (kotlin.math.cos(wd * t) + z * omega / wd * kotlin.math.sin(wd * t))
            x = frag
            v = -z * omega * frag + e * (kotlin.math.cos(wd * t) * z * omega - wd * kotlin.math.sin(wd * t))
        } else {
            x = e * (1 + omega * t)
            v = -omega * e * (1 + omega * t) + e * omega
        }
        return if (x * x + v * v / (omega * omega) <= 6e-9) 0f else x.toFloat()
    }

}
