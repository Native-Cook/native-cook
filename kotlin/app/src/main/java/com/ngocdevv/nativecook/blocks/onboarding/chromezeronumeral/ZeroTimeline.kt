package com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral

import kotlin.math.*

/** Seconds-based choreography and closed-form gesture physics from zero-timeline.ts. */
internal object ZeroTimeline {
    const val TAU = PI * 2
    const val REST_YAW = -0.33
    const val REST_PITCH = -0.1
    const val FUSE = 3.86
    const val SETTLE = 5.54
    fun clamp01(n: Double) = n.coerceIn(0.0, 1.0)
    fun progress(t: Double, start: Double, duration: Double) = clamp01((t - start) / duration)
    fun lerp(a: Double, b: Double, p: Double) = a + (b - a) * p
    fun cubicOut(p: Double): Double {
        val q = 1 - clamp01(p)
        return 1 - q * q * q
    }
    fun bezier(p: Double, x1: Double = 0.23, y1: Double = 1.0, x2: Double = 0.32, y2: Double = 1.0): Double {
        if (p <= 1e-9) return 0.0
        if (p >= 1 - 1e-9) return 1.0
        var lo = 0.0
        var hi = 1.0
        var u = p
        repeat(15) {
            val inv = 1 - u
            val x = 3 * inv * inv * u * x1 + 3 * inv * u * u * x2 + u * u * u
            if (x < p) lo = u else hi = u
            u = (lo + hi) / 2
        }
        val inv = 1 - u
        return 3 * inv * inv * u * y1 + 3 * inv * u * u * y2 + u * u * u
    }
    fun spring(t: Double, from: Double, to: Double, stiffness: Double, damping: Double, velocity: Double = 0.0): Double {
        if (t <= 0) return from
        val decay = damping / 2
        val frequency = sqrt(max(0.001, stiffness - decay * decay))
        val displacement = from - to
        return to + exp(-decay * t) * (displacement * cos(frequency * t) +
            (velocity + decay * displacement) / frequency * sin(frequency * t))
    }
    fun landedSpring(t: Double, from: Double, to: Double, stiffness: Double, damping: Double,
                     end: Double = 1.2, velocity: Double = 0.0): Double {
        if (t >= end) return to
        return lerp(spring(t, from, to, stiffness, damping, velocity), to, cubicOut(progress(t, end - 0.18, 0.18)))
    }
    fun nearestRest(yaw: Double) = REST_YAW + floor((yaw - REST_YAW) / TAU + 0.5) * TAU
    fun decayTravel(velocity: Double, elapsed: Double) = velocity / 2.6 * (1 - exp(-2.6 * max(0.0, elapsed)))
    fun coastYaw(yaw: Double, velocity: Double, elapsed: Double): Double {
        if (elapsed < 0.95) return yaw + decayTravel(velocity, elapsed)
        val at = yaw + decayTravel(velocity, 0.95)
        return landedSpring(elapsed - 0.95, at, nearestRest(at), 100.0, 18.0, 0.93, velocity * exp(-2.6 * 0.95))
    }
    fun rubberPitch(pitch: Double): Double {
        val excess = max(0.0, abs(pitch) - 0.35)
        return sign(pitch) * (min(abs(pitch), 0.35) + 0.12 * (1 - exp(-excess / 0.12)))
    }
    fun loosen(t: Double) = 1 - 0.78 * (1 - exp(-max(t, 0.0) / 0.26))
    fun glintPulse(t: Double) = if (t < 0 || t >= 0.3) 0.0 else sin(PI * t / 0.3)
    fun project(x: Double, y: Double, z: Double, yaw: Double, pitch: Double, scale: Double,
                centreX: Double, centreY: Double, unit: Double): Pair<Double, Double> {
        val x1 = scale * (x * cos(yaw) + z * sin(yaw))
        val z1 = scale * (-x * sin(yaw) + z * cos(yaw))
        val y2 = y * scale * cos(pitch) - z1 * sin(pitch)
        val z2 = y * scale * sin(pitch) + z1 * cos(pitch)
        val k = 7 / (7 - z2)
        return Pair(centreX + unit * x1 * k, centreY - unit * y2 * k)
    }
    fun touchRipple(x: Double, y: Double, yaw: Double, centreX: Double, centreY: Double, unit: Double): Pair<Double, Double> {
        return Pair(((x - centreX) / (unit * max(cos(yaw), 0.3))).coerceIn(-0.64, 0.64),
            (-(y - centreY) / unit).coerceIn(-1.0, 1.0))
    }
    fun rest(t: Double = 0.0, reduced: Boolean = false): ZeroFrame {
        val f = ZeroFrame()
        for (i in 0..2) {
            val wave = sin(PI * t / 2.4 + i * PI / 3)
            f.twinkle[i] = if (reduced) 1.0 else 1 - 0.1 * wave * wave
        }
        f.yaw += if (reduced) 0.0 else 0.17 * sin(TAU * t / 6)
        f.drift = if (reduced) 0.0 else sin(TAU * t / 12)
        return f
    }
    fun loop(seconds: Double): ZeroFrame {
        val t = (floor(((seconds % 12 + 12) % 12) * 1e9 + 0.5) / 1e9) % 12
        val f = rest(t)
        if (t >= 0.9 && t < 2) {
            f.fuse = 1 - bezier(progress(t, 0.9, 0.26))
            f.drain = progress(t, 1.16, 0.62)
            f.percent = 1 - bezier(progress(t, 0.9, 0.32))
            f.dim = bezier(progress(t, 0.9, 0.4))
            f.yaw = lerp(f.yaw, 0.38, bezier(progress(t, 1.16, 0.62)))
            f.cardExit = sin(PI * progress(t, 0.9, 0.9))
            for (i in 0..2) {
                f.sparkleOpacity[i] = 1 - progress(t, 0.9, 0.18)
                f.sparkles[i] = 1 - 0.4 * bezier(progress(t, 0.9, 0.18))
            }
            for (i in 0..4) {
                val p = progress(t, 0.9 + (4 - i) * 0.03, 0.2)
                f.copy[i] = 1 - p
                f.copyY[i] = 8 * bezier(p)
            }
        } else if (t >= 2 && t < SETTLE) {
            f.build = progress(t, 2.0, 1.4)
            f.fuse = 0.0
            f.percent = 0.0
            f.dim = 1.0
            f.yaw = 0.38
            f.scale = lerp(1.0, 0.955, bezier(progress(t, 3.4, 0.46)))
            f.sweep = lerp(-1.0, 1.0, progress(t, 3.4, 0.46))
            for (i in 0..2) {
                f.sparkles[i] = 0.4
                f.sparkleRotation[i] = -PI / 2
                f.sparkleOpacity[i] = 0.0
            }
            for (i in 0..4) {
                f.copy[i] = 0.0
                f.copyY[i] = 16.0
            }
            if (t >= FUSE) {
                val p = t - FUSE
                val pour = 1 - min(1.0, p / 0.72)
                f.fuse = 1 - pour * pour
                f.ripple = if (p < 0.76) cubicOut(p / 0.76) else 0.0
                f.flash = 0.55 * (if (p < 0.09) p / 0.09 else 1 - cubicOut((p - 0.09) / 0.6))
                f.dim = 1 - bezier(p / 0.6)
                f.scale = landedSpring(p, 0.955, 1.0, 260.0, 14.0)
                f.yaw = landedSpring(p, 0.38, REST_YAW, 60.0, 9.0, 1.5) +
                    0.17 * sin(TAU * t / 6) * bezier(p / 1.68)
                f.sweep = if (p < 0.9) lerp(-1.0, 1.0, p / 0.9) else -1.0
                f.percent = if (p < 0.16) 0.0 else landedSpring(p - 0.16, 0.0, 1.0, 170.0, 15.0)
                f.glint = max(glintPulse(p), glintPulse(p - 0.3))
                for (i in 0..2) {
                    val at = p - 0.42 - i * 0.08
                    val pop = if (at < 0) 0.0 else landedSpring(at, 0.0, 1.0, 180.0, 15.0, 0.9)
                    f.sparkles[i] = 0.4 + 0.6 * pop
                    f.sparkleRotation[i] = -(1 - pop) * PI / 2
                    f.sparkleOpacity[i] = clamp01(at / 0.16)
                }
                for (i in 0..4) {
                    val enter = bezier(progress(p, 0.36 + i * 0.06, 0.52))
                    f.copy[i] = enter
                    f.copyY[i] = 16 * (1 - enter)
                }
            }
        } else if (t >= 5.94 && t < 5.94 + 2.4) {
            val at = t - 5.94
            val dragged = progress(at, 0.1, 0.42)
            val initialYaw = REST_YAW + 0.17 * sin(TAU * 5.94 / 6)
            val releaseYaw = initialYaw + 150.0 / 110
            f.yaw = if (at <= 0.52) initialYaw + dragged * 150 / 110 else coastYaw(releaseYaw, 2800.0 / 110, at - 0.52)
            if (at > 2.1) f.yaw += 0.17 * sin(TAU * t / 6) * bezier(progress(at, 2.1, 0.3))
        } else if (t >= 8.64 && t < 8.64 + 1.4) {
            val at = floor((t - 8.64) * 1e9 + 0.5) / 1e9
            if (at < 0.7) {
                f.fuse = loosen(at)
                f.scale = lerp(1.0, 0.97, bezier(at / 0.22))
            } else {
                val release = at - 0.7
                f.fuse = min(1.0, landedSpring(release, loosen(0.7), 1.0, 220.0, 18.0, 0.6))
                f.scale = landedSpring(release, 0.97, 1.0, 260.0, 14.0, 0.6)
                f.ripple = cubicOut(release / 0.7)
                val releaseYaw = rest(8.64 + 0.7).yaw
                val origin = project(-0.42, -0.2, 0.26, releaseYaw, REST_PITCH, 0.97, 214.0, 286.0, 158.0)
                val ripple = touchRipple(origin.first, origin.second, releaseYaw, 214.0, 286.0, 158.0)
                f.rippleX = ripple.first
                f.rippleY = ripple.second
                f.flash = 0.35 * (1 - cubicOut(release / 0.6))
                f.glint = glintPulse(release)
            }
        } else if (t >= 10.24 && t < 10.24 + 1.4) {
            val at = t - 10.24
            f.buttonPress = if (at < 0.16) bezier(at / 0.12) else landedSpring(at - 0.16, 1.0, 0.0, 240.0, 20.0, 0.65)
            if (at >= 0.16) {
                val p = progress(at, 0.16, 1.1)
                f.yaw += TAU * bezier(p, 0.77, 0.0, 0.175, 1.0)
                f.sweep = if (p < 1) lerp(-1.0, 1.0, p) else -1.0
                f.arrow = 4 * sin(PI * progress(at, 0.16, 0.5))
                f.glint = glintPulse(at - 0.53)
                for (i in 0..2) f.twinkle[i] += 0.12 * sin(PI * p)
            }
        }
        for (i in 0..4) f.farDustOpacity[i] *= 1 - f.dim
        for (i in 0..2) f.nearDustOpacity[i] *= 1 - f.dim
        return f
    }
    fun entrance(playhead: Double): ZeroFrame {
        if (playhead >= 2) return loop(playhead)
        val f = loop(2.0)
        val at = floor((playhead - 0.9 - 0.2) * 1e9 + 0.5) / 1e9
        f.card = if (at <= 0) 0.0 else landedSpring(at, 0.0, 1.0, 140.0, 17.0, 0.9)
        f.cardOpacity = if (at < 0) 0.0 else 1.0
        return f
    }
}
