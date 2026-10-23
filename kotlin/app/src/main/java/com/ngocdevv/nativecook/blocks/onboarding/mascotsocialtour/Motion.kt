package com.ngocdevv.nativecook.blocks.onboarding.mascotsocialtour

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** The four stories share the Swift reference's exact ten-second clock. */
const val LOOP = 10.0

internal object MascotSocialTourMotion {
    val titles = listOf("Hang out on\nyour phone", "Humanise\nconnection", "Build your\nown space", "Memories\nthat last")
    val bodies = listOf(
        "Realtime social widgets with friends,\nbecause not everything requires a DM.",
        "Stay close through simple, subtle\nmoments — no pressure, just presence.",
        "It's yours. Each widget adds a new way\nto interact. Play, share, organise.",
        "Each day is summarised in a scrap book\nstyle memory. No effort required.",
    )
    val anchors = listOf(0.8, 3.85, 6.35, 8.55)

    fun normalized(time: Double): Double = ((time % LOOP) + LOOP) % LOOP
    fun segment(t: Double, a: Double, b: Double): Double = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun ease(p: Double): Double = p * p * (3 - 2 * p)
    fun out(p: Double): Double = 1 - (1 - p).pow(3)
    fun mix(a: Double, b: Double, p: Double): Double = a + (b - a) * p
    fun spring(time: Double): Double {
        if (time >= 0.65) return 1.0
        val t = max(0.0, time)
        return 1 - exp(-9 * t) * (cos(15 * t) + 0.6 * sin(15 * t))
    }

    fun key(time: Double, vararg keys: Pair<Double, Double>): Double {
        if (time <= keys.first().first) return keys.first().second
        for (index in 1 until keys.size) {
            val a = keys[index - 1]
            val b = keys[index]
            if (time <= b.first) return mix(a.second, b.second, ease(segment(time, a.first, b.first)))
        }
        return keys.last().second
    }

    data class Pages(val from: Int, val to: Int, val progress: Double, val active: Int)
    private data class Cut(val start: Double, val end: Double, val from: Int, val to: Int)
    private val cuts = listOf(Cut(1.7, 2.0, 0, 1), Cut(4.1, 4.4, 1, 2), Cut(7.0, 7.3, 2, 3), Cut(9.45, 9.85, 3, 0))

    fun pages(t: Double): Pages {
        var current = 0
        for (cut in cuts) {
            if (t < cut.start) return Pages(current, current, 1.0, current)
            if (t < cut.end) return Pages(cut.from, cut.to, out(segment(t, cut.start, cut.end)), cut.to)
            current = cut.to
        }
        return Pages(current, current, 1.0, current)
    }
}

internal class MascotSocialTourClock {
    var epoch = System.nanoTime()
    var manualTime: Double? = null
    var dragOrigin = 0.0
    var translation = 0.0
    var dragging = false
    var releasedAt = 0L
    var releaseFrom = 0.0
    var releaseTo = 0.0

    fun shown(now: Long, autoPlay: Boolean, reducedMotion: Boolean, renderTime: Double?): Double {
        renderTime?.let { return MascotSocialTourMotion.normalized(it) }
        if (reducedMotion) return manualTime ?: 8.55
        manualTime?.let {
            if (dragging) return it
            val elapsed = max(0.0, (now - releasedAt) / 1e9)
            if (elapsed < 0.6) return MascotSocialTourMotion.mix(releaseFrom, releaseTo, MascotSocialTourMotion.spring(elapsed))
            if (elapsed < 2) return releaseTo
            if (elapsed < 2.55) {
                return MascotSocialTourMotion.mix(releaseTo, 0.0, MascotSocialTourMotion.ease(MascotSocialTourMotion.segment(elapsed, 2.0, 2.55)))
            }
            manualTime = null
            epoch = now
        }
        return if (autoPlay) MascotSocialTourMotion.normalized((now - epoch) / 1e9) else 0.0
    }

    fun pause(time: Double, now: Long = System.nanoTime()) {
        releaseFrom = time
        releaseTo = time
        manualTime = time
        releasedAt = now
    }
}
