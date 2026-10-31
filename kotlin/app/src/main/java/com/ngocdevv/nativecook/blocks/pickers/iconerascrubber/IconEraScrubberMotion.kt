package com.ngocdevv.nativecook.blocks.pickers.iconerascrubber

import kotlin.math.pow

/** Pure year/era motion ported from ies-motion.ts. */
object IconEraScrubberMotion {
    const val FIRST = 2008.0
    const val LAST = 2026.0
    const val LOOP = 10.0

    // The eras change over a year and a half of the ruler, so a finger can stop half-way through.
    private val keys = doubleArrayOf(FIRST, 2011.5, 2013.0, 2018.0, 2019.5, 2023.5, 2025.0, LAST)
    private val eras = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 2.0, 2.0, 3.0, 3.0)

    val moves = arrayOf(
        doubleArrayOf(1.0, 2.0, 2012.3),
        doubleArrayOf(2.5, 3.3, 2016.0),
        doubleArrayOf(3.6, 4.6, 2020.0),
        doubleArrayOf(4.9, 5.8, 2024.3),
        doubleArrayOf(6.3, 6.9, LAST),
        doubleArrayOf(7.8, 9.4, FIRST),
    )

    data class Frame(
        val year: Double,
        val lean: Double,
        val finger: Double,
        val fingerX: Double,
    )

    fun clamp01(value: Double): Double = when {
        value < 0.0 -> 0.0
        value > 1.0 -> 1.0
        else -> value
    }

    fun seg(time: Double, start: Double, end: Double): Double = clamp01((time - start) / (end - start))

    fun smooth(a: Double, b: Double, x: Double): Double {
        val u = clamp01((x - a) / (b - a))
        return u * u * (3.0 - 2.0 * u)
    }

    fun easeInOut(value: Double): Double {
        val x = clamp01(value)
        return if (x < 0.5) 4.0 * x * x * x else 1.0 - (-2.0 * x + 2.0).pow(3.0) / 2.0
    }

    fun easeOut(value: Double): Double {
        val x = clamp01(value)
        val u = 1.0 - x
        return 1.0 - u * u * u
    }

    /** The era at a year: 0 glossy, 1 flat, 2 soft, 3 glass. */
    fun eraOf(year: Double): Double {
        if (year <= keys[0]) return 0.0
        for (i in 1 until keys.size) {
            if (year <= keys[i]) {
                val u = (year - keys[i - 1]) / (keys[i] - keys[i - 1])
                return eras[i - 1] + (eras[i] - eras[i - 1]) * u
            }
        }
        return 3.0
    }

    fun yearAt(time: Double): Double {
        var year = FIRST
        for (i in moves.indices) {
            val move = moves[i]
            if (time >= move[0]) {
                val from = year
                // The flick back glides out (ease-out); the drags follow the finger (ease-in-out).
                val k = if (i == moves.lastIndex) {
                    easeOut(seg(time, move[0], move[1]))
                } else {
                    easeInOut(seg(time, move[0], move[1]))
                }
                year = from + (move[2] - from) * k
            }
        }
        return year
    }

    fun frameAt(time: Double): Frame {
        val year = yearAt(time)
        val velocity = (yearAt(time + 0.03) - yearAt(time - 0.03)) / 0.06
        var finger = 0.0
        var fingerX = 0.0
        for (i in moves.indices) {
            val move = moves[i]
            val start = move[0]
            val end = if (i == moves.lastIndex) start + 0.25 else move[1]
            val p = easeOut(seg(time, start - 0.12, start)) *
                (1.0 - easeOut(seg(time, end, end + 0.12)))
            if (p > 0.0) {
                finger = p
                val from = if (i == 0) FIRST else moves[i - 1][2]
                fingerX = if (i == moves.lastIndex) {
                    -2.5 * easeOut(seg(time, start, start + 0.25))
                } else {
                    year - from
                }
            }
        }
        return Frame(year, (velocity * 0.18).coerceIn(-1.0, 1.0), finger, fingerX)
    }
}
