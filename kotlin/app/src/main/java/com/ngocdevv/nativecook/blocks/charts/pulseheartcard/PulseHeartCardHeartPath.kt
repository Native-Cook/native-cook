package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import androidx.compose.ui.graphics.Path
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** The Material-style heart on a 24 x 24 grid ("M12 21.35 10.55 20C5.4 15.36 2 12.28 2 8.5 ..."), scaled by [s]. */
internal fun pulseHeartCardHeartPath(s: Float): Path {
    fun x(v: Float) = v * s
    return Path().apply {
        moveTo(x(12f), x(21.35f))
        lineTo(x(10.55f), x(20f))
        cubicTo(x(5.4f), x(15.36f), x(2f), x(12.28f), x(2f), x(8.5f))
        cubicTo(x(2f), x(5.42f), x(4.42f), x(3f), x(7.5f), x(3f))
        cubicTo(x(9.24f), x(3f), x(10.91f), x(3.81f), x(12f), x(5.09f))
        // A6.04 6.04 0 0 1 16.5 3: a clockwise arc of radius 6.04 from (12, 5.09) to (16.5, 3), in one cubic.
        val r = 6.04
        val x0 = 12.0
        val y0 = 5.09
        val x1 = 16.5
        val y1 = 3.0
        val halfX = (x1 - x0) / 2
        val halfY = (y1 - y0) / 2
        val halfLength = sqrt(halfX * halfX + halfY * halfY)
        val h = sqrt(r * r - halfLength * halfLength)
        // The centre lies below the chord (the lobe is convex upwards).
        val cx = (x0 + x1) / 2 + h * -halfY / halfLength
        val cy = (y0 + y1) / 2 + h * halfX / halfLength
        val a0 = atan2(y0 - cy, x0 - cx)
        var a1 = atan2(y1 - cy, x1 - cx)
        if (a1 < a0) a1 += 2 * Math.PI
        val k = 4.0 / 3.0 * tan((a1 - a0) / 4)
        cubicTo(
            x((x0 - k * r * sin(a0)).toFloat()), x((y0 + k * r * cos(a0)).toFloat()),
            x((x1 + k * r * sin(a1)).toFloat()), x((y1 - k * r * cos(a1)).toFloat()),
            x(x1.toFloat()), x(y1.toFloat()),
        )
        cubicTo(x(19.58f), x(3f), x(22f), x(5.42f), x(22f), x(8.5f))
        cubicTo(x(22f), x(12.28f), x(18.6f), x(15.36f), x(13.45f), x(20f))
        lineTo(x(12f), x(21.35f))
        close()
    }
}
