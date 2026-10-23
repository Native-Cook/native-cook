package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.graphics.Path
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Trail
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// A ribbon samples earlier times along the head's path. It stays continuous even when
// the head travels far in one frame, and grows longer naturally as the head speeds up.
internal class ReferralComet {
    val path = Path()
    private val samples = DoubleArray((Trail.samples + 1) * 2)
    private val radii = DoubleArray(Trail.samples + 1)
    private val normalX = DoubleArray(Trail.samples + 1)
    private val normalY = DoubleArray(Trail.samples + 1)

    fun ribbon(
        t: Double,
        initialRadius: Double,
        route: ReferralPath?,
        fromX: Double,
        fromY: Double,
        toX: Double,
        toY: Double,
    ) {
        val count = sampleTrail(t, initialRadius, route, fromX, fromY, toX, toY)
        if (count < 2) {
            if (count == 1 && initialRadius > 0) {
                path.addCircle(
                    samples[0].toFloat(),
                    samples[1].toFloat(),
                    radii[0].toFloat(),
                    Path.Direction.CW,
                )
            }
            return
        }
        traceEdges(count)
    }

    private fun sampleTrail(
        t: Double,
        initialRadius: Double,
        route: ReferralPath?,
        fromX: Double,
        fromY: Double,
        toX: Double,
        toY: Double,
    ): Int {
        var count = 0
        for (k in 0..Trail.samples) {
            val u = t - k * Trail.gap
            if (u < 0 || u >= 1) continue
            if (route != null) {
                route.sample(u, samples, count * 2)
            } else {
                val progress = ReferralMath.cubic(u)
                val remaining = 1 - progress
                samples[count * 2] =
                    remaining * remaining * fromX +
                    2 * remaining * progress * fromX +
                    progress * progress * toX
                samples[count * 2 + 1] =
                    remaining * remaining * fromY +
                    2 * remaining * progress * (toY + 18) +
                    progress * progress * toY
            }
            radii[count] = ReferralMath.ribbonWidth(k, u, t, initialRadius)
            count++
        }
        return count
    }

    // Push each sample out along its normal to form two continuous edges and a rounded head.
    private fun traceEdges(count: Int) {
        for (i in 0 until count) {
            val previous = max(i - 1, 0) * 2
            val next = min(i + 1, count - 1) * 2
            val dx = samples[previous] - samples[next]
            val dy = samples[previous + 1] - samples[next + 1]
            val length = hypot(dx, dy).takeIf { it != 0.0 } ?: 1.0
            normalX[i] = -dy / length
            normalY[i] = dx / length
        }
        path.moveTo(
            (samples[0] + normalX[0] * radii[0]).toFloat(),
            (samples[1] + normalY[0] * radii[0]).toFloat(),
        )
        for (i in 1 until count) {
            path.lineTo(
                (samples[i * 2] + normalX[i] * radii[i]).toFloat(),
                (samples[i * 2 + 1] + normalY[i] * radii[i]).toFloat(),
            )
        }
        for (i in count - 1 downTo 0) {
            path.lineTo(
                (samples[i * 2] - normalX[i] * radii[i]).toFloat(),
                (samples[i * 2 + 1] - normalY[i] * radii[i]).toFloat(),
            )
        }
        for (j in 1..5) {
            val angle = j * PI / 6
            path.lineTo(
                (samples[0] + radii[0] * (-normalX[0] * cos(angle) + normalY[0] * sin(angle)))
                    .toFloat(),
                (samples[1] + radii[0] * (-normalY[0] * cos(angle) - normalX[0] * sin(angle)))
                    .toFloat(),
            )
        }
        path.close()
    }
}
