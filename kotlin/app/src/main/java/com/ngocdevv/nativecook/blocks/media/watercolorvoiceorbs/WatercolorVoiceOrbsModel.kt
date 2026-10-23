package com.ngocdevv.nativecook.blocks.media.watercolorvoiceorbs

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The voices, the loop and the curves of the voice picker. Times in seconds. A port of WatercolorVoiceOrbsModel.swift. */
internal object OrbsModel {
    class Voice(
        val name: String,
        val line: String,
        /** Deep, mid and light colours of the orb (0…1 RGB). */
        val deep: FloatArray,
        val mid: FloatArray,
        val light: FloatArray,
        /** The direction the orb's bright side faces (y down), where in the noise it sits, and how much its middle is twisted. */
        val band: FloatArray,
        val offset: FloatArray,
        val swirl: Float,
    )

    val voices = listOf(
        Voice(
            "Lagoon", "Calm and clear",
            floatArrayOf(0.063f, 0.341f, 0.890f), floatArrayOf(0.267f, 0.659f, 0.969f), floatArrayOf(0.925f, 0.976f, 1.0f),
            floatArrayOf(0f, -1f), floatArrayOf(0f, 0f), 0f,
        ),
        Voice(
            "Dune", "Warm and unhurried",
            floatArrayOf(0.878f, 0.345f, 0.212f), floatArrayOf(0.973f, 0.643f, 0.400f), floatArrayOf(1.0f, 0.953f, 0.890f),
            floatArrayOf(0.7f, 0.7f), floatArrayOf(3.1f, 1.7f), 1.2f,
        ),
        Voice(
            "Moss", "Bright and curious",
            floatArrayOf(0.039f, 0.494f, 0.400f), floatArrayOf(0.341f, 0.831f, 0.627f), floatArrayOf(0.937f, 1.0f, 0.961f),
            floatArrayOf(-1f, 0.2f), floatArrayOf(-2.3f, 4.2f), -0.9f,
        ),
        Voice(
            "Dusk", "Low and thoughtful",
            floatArrayOf(0.329f, 0.220f, 0.859f), floatArrayOf(0.749f, 0.475f, 0.937f), floatArrayOf(1.0f, 0.925f, 0.984f),
            floatArrayOf(0.3f, 1f), floatArrayOf(5.2f, -3.3f), 1.6f,
        ),
    )

    const val LOOP = 10.0
    private class Step(val at: Double, val from: Double, val to: Double, val dur: Double)

    /** autoPlay steps: start, from, to, duration. The last one glides back to the first voice. */
    private val steps = listOf(Step(1.4, 0.0, 1.0, 0.85), Step(3.6, 1.0, 2.0, 0.85), Step(5.8, 2.0, 3.0, 0.85), Step(7.8, 3.0, 0.0, 1.0))
    /** The orb breathes four times a loop. */
    const val BREATH = 2.5
    /** The preview a voice "speaks" after it lands. */
    const val SPEAK_LENGTH = 1.1

    fun clamp(x: Double, a: Double = 0.0, b: Double = 1.0) = min(max(x, a), b)

    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }

    fun easeInOut(x: Double): Double {
        val t = clamp(x)
        return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    }

    fun easeOut(x: Double): Double {
        val t = clamp(x)
        return 1 - (1 - t).pow(3)
    }

    /** 1 → 1.02 → 1, four times a loop. */
    fun breathing(clock: Double) = 1 + 0.02 * (0.5 - 0.5 * cos(2 * PI * clock / BREATH))

    /** The orb's squash after a swipe starts (u seconds ago): it gives to about 0.94, then overshoots to about 1.02 and settles. */
    fun squash(u: Double) = if (u <= 0) 1.0 else 1 - 0.11 * exp(-2.4 * u) * sin(7.5 * u)

    private val syllables = arrayOf(
        doubleArrayOf(0.15, 0.8, 0.08), doubleArrayOf(0.38, 1.0, 0.1), doubleArrayOf(0.62, 0.7, 0.08), doubleArrayOf(0.85, 0.9, 0.1),
    )

    /** A short spoken preview: four syllables over 1.1 s, 0…1. */
    fun speak(u: Double): Double {
        if (u <= 0 || u >= SPEAK_LENGTH) return 0.0
        var v = 0.0
        for (s in syllables) {
            val d = (u - s[0]) / s[2]
            v += s[1] * exp(-d * d)
        }
        return min(1.0, v)
    }

    /** autoPlay at loop time e: v, seconds since the last swipe started, and since the last voice landed. */
    class Auto(val v: Double, val sinceSwipe: Double, val sinceLanding: Double)

    fun auto(e: Double): Auto {
        // Before the first step of a loop, the last step of the previous one is the latest.
        var last = steps.last()
        var lastAt = last.at - LOOP
        for (s in steps) if (e >= s.at) {
            last = s
            lastAt = s.at
        }
        val u = e - lastAt
        return Auto(last.from + (last.to - last.from) * easeInOut(u / last.dur), u, u - last.dur)
    }

    /** The two voices the picker is between and how far (0…1), the blended texture, and the mixed mid colour for the glow. */
    class Blend(val a: Voice, val b: Voice, val f: Float, val mid: FloatArray, val band: FloatArray, val offset: FloatArray, val swirl: Float)

    fun blend(v: Double): Blend {
        val x = clamp(v, 0.0, (voices.size - 1).toDouble())
        val i = min(floor(x).toInt(), voices.size - 2)
        val f = (x - i).toFloat()
        val a = voices[i]
        val b = voices[i + 1]
        val fc = smoothstep(0.0, 1.0, f.toDouble()).toFloat()
        fun mix(p: FloatArray, q: FloatArray, t: Float) = FloatArray(p.size) { p[it] + (q[it] - p[it]) * t }
        return Blend(a, b, f, mix(a.mid, b.mid, fc), mix(a.band, b.band, f), mix(a.offset, b.offset, f), a.swirl + (b.swirl - a.swirl) * f)
    }
}
