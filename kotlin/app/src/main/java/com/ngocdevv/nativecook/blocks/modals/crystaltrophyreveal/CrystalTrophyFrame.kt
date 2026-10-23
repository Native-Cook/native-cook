package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

import kotlin.math.*

/** Canvas points, with the same safe-area composition as the source. */
internal class CrystalTrophyFrame(val width: Double, val height: Double, top: Double, bottom: Double) {
    val k = min(1.0, max(0.72, (height - top - bottom) / 778))
    val shareWidth = min(354.0, width - 48)
    val shareX = (width - shareWidth) / 2
    val shareY = height - bottom - 72
    val bodyTop = shareY - round(40 * k) - 44
    val originX = width / 2
    val originY = bodyTop - round(76 * k)
    val unit = 108 * k
    val titleSize = round(68 * k)
    val titleLine = round(62 * k)
    val dateSize = round(20 * k)
    val closeX = width - 56
    val closeY = top + 4
    private val lift = max(0.0, top + round(188 * k) + dateSize * 0.5 + sin(8 * PI / 180) * dateSize * 4.8 + 16 - project(0.0, 2.9, 0.0, 0.0)[1])
    val titleY = top + round(104 * k) - lift
    val dateY = top + round(188 * k) - lift

    fun project(x: Double, y: Double, z: Double, yaw: Double): DoubleArray {
        val x1 = x * cos(yaw) + z * sin(yaw)
        val z1 = -x * sin(yaw) + z * cos(yaw)
        val y2 = y * cos(0.15) - z1 * sin(0.15)
        val z2 = y * sin(0.15) + z1 * cos(0.15)
        val scale = 11 / (11 - z2)
        return doubleArrayOf(originX + unit * x1 * scale, originY - unit * y2 * scale)
    }

    fun stagePoint(s: CrystalTrophyState, x: Double, y: Double): DoubleArray {
        val centerY = originY - 1.45 * unit
        return doubleArrayOf(originX + (x - originX) / s.cameraScale,
            centerY + (y - centerY - s.cameraY) / s.cameraScale)
    }
}
