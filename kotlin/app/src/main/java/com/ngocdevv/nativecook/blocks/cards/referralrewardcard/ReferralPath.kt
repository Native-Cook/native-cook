package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// Build the length table only when layout or the capsule launch point changes.
internal class ReferralPath(
    val originX: Double,
    val originY: Double,
    centerX: Double,
    centerY: Double,
) {
    private val sampleX = DoubleArray(sampleCount)
    private val sampleY = DoubleArray(sampleCount)
    private val lengths = DoubleArray(sampleCount)
    val launchLength: Double
    val orbitEnd: Double
    val length: Double
    val launch: Double
    val orbit: Double
    val sling: Double
    val total: Double
    private var cursor = 0

    private companion object {
        val segments = 120
        val sampleCount = segments * 3 + 1

        // Speeds in dp/ms: the ring catches the launch, then accelerates into the sling.
        val launchSpeed = 1.3
        val catchSpeed = 0.45
        val slingSpeed = 1.1
        val launchSpeedSum = 1.75
        val orbitSpeedSum = 1.55
    }

    // Three degrees per chord keeps the ring smooth without sampling geometry during drawing.
    init {
        sampleX[0] = originX
        sampleY[0] = originY
        val joinX = centerX - Size.ring
        // A 1.2-ring vertical reach keeps even a short capsule launch tangent to the ring.
        curve(originX, originY, joinX, centerY + max((originY - centerY) * 0.5, 50.4), joinX, centerY)
        launchLength = lengths[cursor]
        for (i in 1..segments) {
            val angle = PI + i / segments.toDouble() * PI * 2
            add(
                if (i == segments) {
                    joinX
                } else {
                    centerX + Size.ring * cos(angle)
                },
                if (i == segments) {
                    centerY
                } else {
                    centerY + Size.ring * sin(angle)
                },
            )
        }
        orbitEnd = lengths[cursor]
        // This control point pulls the tail upward; it lands six dp below the points center.
        curve(joinX, centerY, joinX, centerY - 63, centerX, centerY - (Size.pointsRise - 6))
        length = lengths[cursor]
        // Constant acceleration: distance is duration times the mean speed.
        launch = 2 * launchLength / launchSpeedSum
        orbit = 2 * (orbitEnd - launchLength) / orbitSpeedSum
        sling = 2 * (length - orbitEnd) / slingSpeed
        total = launch + orbit + sling
    }

    private fun add(
        x: Double,
        y: Double,
    ) {
        val next = cursor + 1
        sampleX[next] = x
        sampleY[next] = y
        lengths[next] = lengths[cursor] + hypot(x - sampleX[cursor], y - sampleY[cursor])
        cursor = next
    }

    private fun curve(
        x: Double,
        y: Double,
        controlX: Double,
        controlY: Double,
        endX: Double,
        endY: Double,
    ) {
        for (i in 1..segments) {
            val u = i / segments.toDouble()
            val m = 1 - u
            add(m * m * x + 2 * m * u * controlX + u * u * endX, m * m * y + 2 * m * u * controlY + u * u * endY)
        }
    }

    // The ring catches the launch, accelerates it around the orbit, then lets it slow to rest.
    fun distance(time: Double): Double {
        val t = time.coerceIn(0.0, total)
        if (t < launch) return launchSpeed * t + (catchSpeed - launchSpeed) * t * t / (2 * launch)
        if (t < launch + orbit) {
            val u = t - launch
            return launchLength + catchSpeed * u + (slingSpeed - catchSpeed) * u * u / (2 * orbit)
        }
        val u = t - launch - orbit
        return orbitEnd + slingSpeed * u - slingSpeed * u * u / (2 * sling)
    }

    // u is elapsed time, not distance: the ribbon's earlier samples share the head's motion.
    // Caller-owned storage keeps point allocations out of the drawing pass.
    fun sample(
        u: Double,
        output: DoubleArray,
        at: Int = 0,
    ) {
        val distance = distance(u * total)
        var lower = 0
        var upper = sampleCount - 1
        while (upper - lower > 1) {
            val middle = (lower + upper) ushr 1
            if (lengths[middle] < distance) lower = middle else upper = middle
        }
        val fraction = (distance - lengths[lower]) / (lengths[upper] - lengths[lower])
        output[at] = sampleX[lower] + (sampleX[upper] - sampleX[lower]) * fraction
        output[at + 1] = sampleY[lower] + (sampleY[upper] - sampleY[lower]) * fraction
    }
}
