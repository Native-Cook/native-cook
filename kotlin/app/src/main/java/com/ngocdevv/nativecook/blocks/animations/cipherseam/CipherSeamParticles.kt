package com.ngocdevv.nativecook.blocks.animations.cipherseam

import kotlin.math.PI
import kotlin.math.pow

/**
 * The seeded particles of `CipherSeam.tsx`, built with the same `hash` and constants so every one
 * lands where it does in React Native.
 */
internal object CipherSeamParticles {
    /** `DUST` has 660 points; `i % 3` is the brightness layer a point belongs to. */
    const val DUST_COUNT = 660
    const val SPARK_COUNT = 320

    /** `DUST[i].x`: where along the lane the point starts, 0 to 1. */
    val dustX = DoubleArray(DUST_COUNT) { CipherSeamTimeline.hash(it + 1) }

    /** `DUST[i].y`: a triangular spread (two hashes), densest on the card's centre line. */
    val dustY = DoubleArray(DUST_COUNT) { CipherSeamTimeline.hash(it + 501) + CipherSeamTimeline.hash(it + 701) - 1 }

    /** `DUST[i].r`: the point's radius, mostly small. */
    val dustRadius = DoubleArray(DUST_COUNT) { 0.42 + CipherSeamTimeline.hash(it + 901).pow(4) * 0.7 }

    /** `DUST[i].phase`: where the point is in its slow bob and twinkle. */
    val dustPhase = DoubleArray(DUST_COUNT) { CipherSeamTimeline.hash(it + 1301) * PI * 2 }

    /** `SPARKS[i].y`: the height of the spark inside the card's height, 0 to 1. */
    val sparkY = DoubleArray(SPARK_COUNT) { CipherSeamTimeline.hash(it + 2101) }

    /** `SPARKS[i].speed`: how many times a second the spark's life repeats. */
    val sparkSpeed = DoubleArray(SPARK_COUNT) { 0.7 + CipherSeamTimeline.hash(it + 2301) * 1.1 }

    /** `SPARKS[i].offset`: where in its life the spark is at time 0. */
    val sparkOffset = DoubleArray(SPARK_COUNT) { CipherSeamTimeline.hash(it + 2501) }

    /** `SPARKS[i].r`: the spark's radius at birth. */
    val sparkRadius = DoubleArray(SPARK_COUNT) { 0.5 + CipherSeamTimeline.hash(it + 2701) * 0.8 }

    /** `SPARKS[i].reach`: how far into the token side the spark travels. */
    val sparkReach = DoubleArray(SPARK_COUNT) { CipherSeamTimeline.hash(it + 2901) }
}
