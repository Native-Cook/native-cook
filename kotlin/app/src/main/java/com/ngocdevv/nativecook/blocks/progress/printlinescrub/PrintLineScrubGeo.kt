package com.ngocdevv.nativecook.blocks.progress.printlinescrub

import android.content.res.AssetManager
import com.ngocdevv.nativecook.blocks.progress.printlinescrub.PrintLineScrubMotion as M
import com.ngocdevv.nativecook.blocks.progress.printlinescrub.PrintLineScrubTheme as Theme
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

/** The figure's silhouette table: at each of the 1536 source rows, the pixel endpoints of up to six disjoint islands (12 numbers). */
internal class PrintLineScrubSilhouette private constructor(private val rows: ShortArray) {
    /** The 12 span endpoints at source row fraction v (0..1), as fractions of the source width; zeros outside the figure. */
    fun at(v: Double, out: FloatArray) {
        if (v < 0 || v >= 1) {
            out.fill(0f)
            return
        }
        val offset = min(FIGURE_H - 1, floor(v * FIGURE_H).toInt()) * 12
        for (i in 0 until 12) out[i] = (rows[offset + i].toInt() and 0xFFFF) / FIGURE_W.toFloat()
    }

    companion object {
        const val FIGURE_W = 1024
        const val FIGURE_H = 1536

        fun load(assets: AssetManager): PrintLineScrubSilhouette {
            val bytes = assets.open("printlinescrub/printlinescrub_silhouette.bin").use { it.readBytes() }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val rows = ShortArray(buf.remaining())
            buf.get(rows)
            return PrintLineScrubSilhouette(rows)
        }
    }
}

/** Layout numbers the drawing needs (RN `pls-uniforms.ts` `Geo`), screen dp. */
internal class PrintLineScrubGeo(val l: Theme.Layout) {
    val s = l.s
    val w = l.w
    val h = l.h
    val axisX = l.x(Theme.STAGE_CX) // screen x of the figure's axis
    val baseY = l.y(Theme.STAGE_Y_BASE) // screen y of the base's top face (world y = 0)
    val perY = Theme.STAGE_UNIT * s // screen pt per world unit of height (the line's travel)
    val u = perY / CAM_COS // pt per world unit across
    val capL = l.x(Theme.STAGE_CX - Theme.CAP_W / 2)
    val capW = Theme.CAP_W * s
    val capH = Theme.CAP_H * s
    val capR = Theme.CAP_R * s
    val capYMin = l.yt(Theme.HEADER_Y + Theme.HEADER_H) + Theme.RANGE_INSET * s + capH / 2
    val capYMax = l.yb(Theme.DOCK_Y) - Theme.RANGE_INSET * s - capH / 2
    val barX = Theme.BAR_X * s // dotted bar box, relative to the capsule's top-left
    val barY = Theme.BAR_Y * s
    val barW = Theme.BAR_W * s
    val barH = Theme.BAR_H * s
    val pillD = Theme.PILL_D * s
    val hdrY = l.yt(Theme.HEADER_Y)
    val dockY = l.yb(Theme.DOCK_Y)

    /** screen y of the cut plane on the figure's axis */
    fun planeY(hh: Double) = baseY - perY * hh

    /** centre y of the capsule: it sits `duck` below the line, kept inside the header and the dock */
    fun capsuleCy(hh: Double, duck: Double): Double {
        val y = planeY(hh) + duck * s
        return if (y < capYMin) capYMin else if (y > capYMax) capYMax else y
    }

    /** x of the pill's centre, relative to the bar box's left */
    fun pillX(hh: Double): Double {
        val r = pillD / 2
        return r + (barW - pillD) * M.clamp01(M.percentAt(hh) / 100)
    }

    companion object {
        private const val PITCH = 20 * PI / 180
        val CAM_COS = cos(PITCH)
        val CAM_SIN = sin(PITCH)
    }
}
