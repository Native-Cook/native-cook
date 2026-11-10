package com.ngocdevv.nativecook.blocks.modals.champagnersvp

import kotlin.math.*

internal object ChampagneRsvpMotion {
    const val LOOP = 10.0
    const val REST_LEAN = 0.05
    val MEET = asin(min(0.9, (1.7 - 0.84) / (2 * 2.62)))
    fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun clinkLeanAt(tau: Double): Double {
        if (tau < 0) return -REST_LEAN
        val k = tau / 1.2
        if (k >= 1) return -REST_LEAN
        return -REST_LEAN + (MEET + REST_LEAN) * (1 - easeInOut(k)) - 0.035 * sin(PI * min(1.0, k / 0.12))
    }
    data class Frame(
        var lean: Double = -REST_LEAN, var clink: Double = 0.0,
        var burst: Double = 0.0, var phase: Double = 0.0, var done: Boolean = false,
        var finger: Double = 0.0, var fingerPress: Double = 0.0,
    )
    fun at(t: Double): Frame {
        val k = seg(t, 1.1, 1.6)
        return Frame(
            lean = if (t >= 1.1 && t < 1.6) -REST_LEAN + (MEET + REST_LEAN) * k * k else if (t >= 1.6) clinkLeanAt(t - 1.6) else -REST_LEAN,
            clink = if (t > 1.6 && t < 2.5) seg(t, 1.6, 2.5) else 0.0,
            burst = if (t > 1.6 && t < 3.0) seg(t, 1.6, 3.0) else 0.0,
            done = t >= 1.75 && t < 8.0,
            finger = easeOut(seg(t, 0.85, 0.98)) * (1 - easeOut(seg(t, 1.14, 1.3))),
            fingerPress = easeOut(seg(t, 0.98, 1.08)) * (1 - easeOut(seg(t, 1.12, 1.26))),
            phase = t / LOOP * 6,
        )
    }
}

internal class ChampagneRsvpLayout(val w: Float, val h: Float, top: Float, bottom: Float) {
    val linkTop = h - max(bottom, 16f) - 4 - 44
    val ctaTop = linkTop - 4 - 56
    val lineTop = ctaTop - 32 - 22
    val venueTop = top + 32
    val namesTop = venueTop + 16 + 8
    val dateTop = namesTop + 48 + 4
    private val room0 = dateTop + 22 + 24
    private val room1 = lineTop - 24
    val heroS = min(min(w * 0.26f, (room1 - room0) / 3), 104f)
    val feetY = room1 - 8
    val left = w / 2 - 0.85f * heroS
    val right = w / 2 + 0.85f * heroS
    val stageX = w / 2 - heroS * 2.4f
    val stageY = feetY - heroS * 2.9f
    fun inStage(x: Float, y: Float) = x >= stageX && x <= stageX + heroS * 4.8f && y >= stageY && y <= stageY + heroS * 3
}
