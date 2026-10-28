package com.ngocdevv.nativecook.blocks.media.fisheyememorywall

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One period of the wall (308 × 440 pt at 4 px / pt), drawn once (a port of FisheyeMemoryWallAtlas.swift).
 * The lens shader samples it with repeat tiling, so the wall never ends.
 */
internal object FmwAtlas {
    const val GUTTER_COLOR = 0x151922L

    val bitmap: Bitmap by lazy { render() }

    private fun argb(v: Long, a: Double = 1.0): Int =
        ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or (v.toInt() and 0xFFFFFF)

    private fun argb(v: Double, a: Double = 1.0) = argb(v.toLong(), a)

    private fun render(): Bitmap {
        val s = FmwModel.ATLAS_SCALE.toFloat()
        val bmp = Bitmap.createBitmap(
            (FmwModel.PERIOD_W * FmwModel.ATLAS_SCALE).toInt(),
            (FmwModel.PERIOD_H * FmwModel.ATLAS_SCALE).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(bmp)
        c.scale(s, s)
        c.drawColor(argb(GUTTER_COLOR))
        for (tile in FmwModel.TILES) draw(tile, c)
        return bmp
    }

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun draw(tile: FmwTile, c: Canvas) {
        val r = RectF(tile.x.toFloat(), tile.y.toFloat(), (tile.x + tile.width).toFloat(), (tile.y + tile.height).toFloat())
        val rad = tile.radius.toFloat()
        val shape = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }
        c.save()
        c.clipPath(shape)
        // The design's unit square covers the tile, centred.
        val side = max(tile.width, tile.height)
        val ox = tile.x + tile.width / 2 - side / 2
        val oy = tile.y + tile.height / 2 - side / 2
        c.save()
        if (tile.flip) c.scale(-1f, 1f, r.centerX(), 0f)
        for (op in FmwModel.DESIGNS[tile.design]) drawOp(op, c, r, ox, oy, side, tile)
        c.restore()
        if (tile.tint > 0) c.drawRect(r, paint().apply { color = argb(FmwModel.TINTS[tile.tint], 0.12) })
        c.restore()
        // A hairline of light along the edge.
        c.save()
        c.clipPath(shape)
        val inner = RectF(r.left + 0.5f, r.top + 0.5f, r.right - 0.5f, r.bottom - 0.5f)
        c.drawRoundRect(inner, rad - 0.5f, rad - 0.5f, paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = argb(0xFFFFFFL, 0.10)
        })
        c.restore()
    }

    private fun drawOp(op: DoubleArray, c: Canvas, r: RectF, ox: Double, oy: Double, side: Double, tile: FmwTile) {
        fun x(u: Double) = (ox + u * side).toFloat()
        fun y(v: Double) = (oy + v * side).toFloat()
        val p = paint()
        when (op[0].toInt()) {
            0 -> {
                p.color = argb(0x000000L)
                p.shader = LinearGradient(
                    0f, r.top, 0f, r.bottom,
                    intArrayOf(argb(op[1]), argb(op[2]), argb(op[3])), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
                c.drawRect(r, p)
            }
            1 -> {
                p.color = argb(op[4], op[5])
                c.drawCircle(x(op[1]), y(op[2]), (op[3] * side).toFloat(), p)
            }
            2 -> {
                p.color = argb(op[5], op[6])
                val rx = op[3] * side
                val ry = op[4] * side
                c.drawOval(x(op[1]) - rx.toFloat(), y(op[2]) - ry.toFloat(), x(op[1]) + rx.toFloat(), y(op[2]) + ry.toFloat(), p)
            }
            3 -> {
                p.color = argb(op[6], op[7])
                val w = op[3] * side
                val h = op[4] * side
                val rad = min(op[5] * side, min(w / 2, h / 2)).toFloat()
                c.drawRoundRect(x(op[1]), y(op[2]), x(op[1]) + w.toFloat(), y(op[2]) + h.toFloat(), rad, rad, p)
            }
            4 -> {
                p.color = argb(op[1], op[2])
                val path = Path()
                path.moveTo(x(op[3]), y(op[4]))
                var i = 5
                while (i + 1 < op.size) {
                    path.lineTo(x(op[i]), y(op[i + 1]))
                    i += 2
                }
                path.close()
                c.drawPath(path, p)
            }
            5 -> {
                p.color = argb(op[6], op[7])
                p.style = Paint.Style.STROKE
                p.strokeWidth = (op[5] * side).toFloat()
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(x(op[1]), y(op[2]), x(op[3]), y(op[4]), p)
            }
            6 -> {
                p.color = argb(0x000000L)
                p.shader = RadialGradient(
                    x(op[1]), y(op[2]), (op[3] * side).toFloat(),
                    argb(op[4], op[5]), argb(op[4], 0.0), Shader.TileMode.CLAMP,
                )
                c.drawCircle(x(op[1]), y(op[2]), (op[3] * side).toFloat(), p)
            }
            else -> {
                // The tile's date: the month over the day, centred (not mirrored).
                c.save()
                if (tile.flip) c.scale(-1f, 1f, r.centerX(), 0f)
                text(c, FmwModel.MONTHS[tile.month], 0.17 * side, 700, 0xD2553FL, 0.02 * side, r.centerX(), y(0.33))
                text(c, "${tile.day}", 0.42 * side, 800, 0x1F2430L, -0.02 * side, r.centerX(), y(0.63))
                c.restore()
            }
        }
    }

    /** Draws a line of text centred on (x, y). */
    private fun text(c: Canvas, s: String, size: Double, weight: Int, color: Long, tracking: Double, x: Float, y: Float) {
        val p = paint()
        p.color = argb(color)
        p.textSize = size.toFloat()
        p.typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false) else Typeface.DEFAULT_BOLD
        p.letterSpacing = (tracking / size).toFloat()
        p.textAlign = Paint.Align.CENTER
        val bounds = Rect()
        p.getTextBounds("H", 0, 1, bounds)
        c.drawText(s, x, y + bounds.height() / 2f, p)
    }
}
