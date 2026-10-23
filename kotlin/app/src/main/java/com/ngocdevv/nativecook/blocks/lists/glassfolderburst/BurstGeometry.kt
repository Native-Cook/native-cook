package com.ngocdevv.nativecook.blocks.lists.glassfolderburst

import android.graphics.Path
import kotlin.math.*

internal object BurstGeometry {
    fun front(f: BurstPose.Folder, path: Path) {
        val y = -34 + 12 * f.open
        val wide = 6 * f.open
        fun x(value: Double) = (f.x + value * f.sx).toFloat()
        fun y(value: Double) = (f.y + value * f.sy).toFloat()
        path.reset()
        path.moveTo(x(-66 - wide), y(y))
        path.cubicTo(x(-66 - wide), y(y - 6.6274), x(-60.6274 - wide), y(y - 12), x(-54 - wide), y(y - 12))
        path.lineTo(x(-28 - wide * .5), y(y - 12))
        path.cubicTo(x(-20.6667 - wide / 3), y(y - 12), x(-13.3333 - wide / 6), y(y), x(-6.0), y(y))
        path.lineTo(x(54 + wide), y(y))
        path.cubicTo(x(60.6274 + wide), y(y), x(66 + wide), y(y + 5.3726), x(66 + wide), y(y + 12))
        path.lineTo(x(66.0), y(36.0))
        path.cubicTo(x(66.0), y(44.8366), x(58.8366), y(52.0), x(50.0), y(52.0))
        path.lineTo(x(-50.0), y(52.0))
        path.cubicTo(x(-58.8366), y(52.0), x(-66.0), y(44.8366), x(-66.0), y(36.0))
        path.close()
    }
    fun storedLight(f: BurstPose.Folder, o: BurstPose.Orb): Double {
        val qx = (o.x - f.x) / f.sx
        val qy = (o.y - f.y) / f.sy
        val rr = max(.01, o.r / f.sy)
        val start = -28 - 3 * f.open
        val top = -46 + 12 * f.open + 12 * BurstPose.smooth((qx - start) / (-6 - start))
        return BurstPose.smooth((qy - top + rr) / (2 * rr)) *
            (1 - BurstPose.smooth((abs(qx) - 60) / (6 + rr))) *
            (1 - BurstPose.smooth((qy - 52) / rr)) * o.glow * BurstPose.smooth(o.r / .1)
    }
}
