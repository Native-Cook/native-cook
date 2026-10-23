// Shared geometry, completion cues and the exact nine-chunk transfer schedule.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import kotlin.math.*

internal object HorizonMath {
    const val HEIGHT = 240f
    const val LIFT = 46f
    const val TRANSFER = 5200.0
    const val CREST = 1400.0
    const val LOOP = 9700.0
    private val arrivals = doubleArrayOf(0.0, 400.0, 900.0, 1850.0, 2450.0, 3150.0, 3800.0, 4650.0, 4950.0)
    private val spans = doubleArrayOf(300.0, 400.0, 450.0, 550.0, 650.0, 550.0, 400.0, 250.0, 250.0)
    private val targets = floatArrayOf(.12f, .27f, .40f, .53f, .68f, .81f, .90f, .96f, 1f)
    fun part(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
    fun smooth(t: Float) = t * t * (3 - 2 * t)
    fun progress(clock: Float): Float {
        val ms = clock.coerceIn(0f, 1f) * TRANSFER
        var progress = 0f
        for (i in arrivals.indices) {
            if (ms < arrivals[i]) break
            val t = ((ms - arrivals[i]) / spans[i]).coerceIn(0.0, 1.0).toFloat()
            progress += (targets[i] - progress) * (1 - (1 - t) * (1 - t))
            if (t < 1) break
        }
        return progress
    }
    fun chunk(clock: Float): Int {
        val ms = clock.coerceIn(0f, 1f) * TRANSFER
        var result = -1
        for (i in arrivals.indices) { if (ms < arrivals[i]) break; result = i }
        return result
    }
    fun pulse(clock: Float): Float {
        val i = chunk(clock)
        return if (i < 0) 0f else max(0.0, 1 - (clock * TRANSFER - arrivals[i]) / 450).toFloat()
    }
    fun rubber(pull: Float) = .75f * pull / (1 + .75f * abs(pull) / 110)
    fun pull(shift: Float): Float {
        val bounded = shift.coerceIn(-109.999f, 109.999f)
        return bounded / (.75f * (1 - abs(bounded) / 110))
    }
    fun velocity(pull: Float, speed: Float) = speed * .75f / (1 + .75f * abs(pull) / 110).pow(2)
    fun cardScale(crest: Float, press: Float): Float {
        val inhale = part(crest, 0f, .12f)
        val release = part(crest, .3f, .7f)
        val squeeze = 1 - (1 - inhale).pow(2)
        val back = 1 - exp(-6 * release) * cos(9 * release)
        return 1 - .018f * squeeze * (1 - back) - .008f * press
    }
}
