package com.ngocdevv.nativecook.blocks.playground.glassmarblestray

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// The wood is evaluated on the CPU at WOOD_DENSITY pixels per dp, then stretched into the bake.
private const val WOOD_DENSITY = 2.0

private fun fract(v: Double) = v - floor(v)

private fun hash(x: Double, y: Double) = fract(sin(x * 127.1 + y * 311.7) * 43758.5453)

private fun noise(x: Double, y: Double): Double {
    val ix = floor(x)
    val iy = floor(y)
    val fx = x - ix
    val fy = y - iy
    val ux = fx * fx * (3 - 2 * fx)
    val uy = fy * fy * (3 - 2 * fy)
    val a = hash(ix, iy) + (hash(ix + 1, iy) - hash(ix, iy)) * ux
    val b = hash(ix, iy + 1) + (hash(ix + 1, iy + 1) - hash(ix, iy + 1)) * ux
    return a + (b - a) * uy
}

private fun smooth(a: Double, b: Double, v: Double): Double {
    val t = min(1.0, max(0.0, (v - a) / (b - a)))
    return t * t * (3 - 2 * t)
}

/**
 * Wood: growth rings warped by value noise, stretched along the grain. The React Native SkSL,
 * evaluated in Double; `across` turns the grain a quarter turn. Returns the colour as ARGB.
 */
private fun wood(px: Double, py: Double, light: IntArray, dark: IntArray, seed: Double, across: Boolean, scale: Double): Int {
    val x = if (across) py else px
    val y = if (across) -px else py
    val qx = x * 0.012 * scale + seed
    val qy = y * 0.05 * scale + seed * 0.37
    val n = noise(qx * 0.5, qy * 3.0) * 0.6 + noise(qx * 1.1, qy * 8.0) * 0.3 + noise(qx * 2.7, qy * 19.0) * 0.1
    val rings = fract(qy * 1.4 + n * 2.4)
    val grain = smooth(0.0, 0.8, rings) * (1 - smooth(0.8, 1.0, rings))
    val fleck = noise(qx * 30.0, qy * 2.0)
    val t = min(1.0, max(0.0, 0.2 + 0.55 * grain + 0.25 * n + 0.1 * (fleck - 0.5)))
    val r = (dark[0] + (light[0] - dark[0]) * t).roundToInt()
    val g = (dark[1] + (light[1] - dark[1]) * t).roundToInt()
    val b = (dark[2] + (light[2] - dark[2]) * t).roundToInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun argb(r: Int, g: Int, b: Int, a: Double) = (((a.coerceIn(0.0, 1.0)) * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

/**
 * Bakes the tray's top once, on a CPU canvas at [density] pixels per dp: a mitred walnut frame with
 * bevelled edges and brass pins, an oak floor, and the carved cups. Runs off the main thread.
 */
internal fun bakeTrayTop(trayW: Double, trayH: Double, tray: Tray, density: Float): Bitmap {
    val bw = ceil(trayW * density).toInt()
    val bh = ceil(trayH * density).toInt()
    val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(density, density)
    val w = trayW.toFloat()
    val h = trayH.toFloat()
    val f = FRAME.toFloat()
    val corner = tray.corner.toFloat()

    // The two woods, at a coarser density: the frame boards (each pixel takes the board of its
    // nearest edge, the mitres falling on the diagonals) and the floor.
    val ww = ceil(trayW * WOOD_DENSITY).toInt()
    val wh = ceil(trayH * WOOD_DENSITY).toInt()
    val walnutLight = intArrayOf(138, 88, 52)
    val walnutDark = intArrayOf(62, 34, 18)
    val oakLight = intArrayOf(228, 190, 140)
    val oakDark = intArrayOf(186, 136, 88)
    val framePixels = IntArray(ww * wh)
    val floorPixels = IntArray(ww * wh)
    for (j in 0 until wh) {
        val py = (j + 0.5) / WOOD_DENSITY
        for (i in 0 until ww) {
            val px = (i + 0.5) / WOOD_DENSITY
            val top = py
            val bottom = trayH - py
            val left = px
            val right = trayW - px
            val nearest = min(min(top, bottom), min(left, right))
            if (nearest <= FRAME + 1) {
                // Boards: top, bottom (grain along), left, right (grain across).
                val k = when (nearest) {
                    top -> 0
                    bottom -> 1
                    left -> 2
                    else -> 3
                }
                framePixels[j * ww + i] = wood(px, py, walnutLight, walnutDark, 3.1 + k * 5.7, k >= 2, 1.0)
            }
            if (nearest >= FRAME - 1) {
                floorPixels[j * ww + i] = wood(px, py, oakLight, oakDark, 11.3, true, 0.6)
            }
        }
    }
    val frameWood = Bitmap.createBitmap(framePixels, ww, wh, Bitmap.Config.ARGB_8888)
    val floorWood = Bitmap.createBitmap(floorPixels, ww, wh, Bitmap.Config.ARGB_8888)
    val woodSrc = Rect(0, 0, ww, wh)
    val woodDst = RectF(0f, 0f, (ww / WOOD_DENSITY).toFloat(), (wh / WOOD_DENSITY).toFloat())
    val smoothPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val outer = Path().apply { addRoundRect(0f, 0f, w, h, OUTER_CORNER.toFloat(), OUTER_CORNER.toFloat(), Path.Direction.CW) }
    val inner = Path().apply { addRoundRect(f, f, w - f, h - f, corner, corner, Path.Direction.CW) }

    // The frame: four mitred boards, the long ones with the grain along them, and the mitre joints.
    canvas.save()
    canvas.clipPath(outer)
    canvas.drawBitmap(frameWood, woodSrc, woodDst, smoothPaint)
    val joint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = argb(30, 14, 6, 0.55)
    }
    canvas.drawLine(0f, 0f, f, f, joint)
    canvas.drawLine(w, 0f, w - f, f, joint)
    canvas.drawLine(0f, h, f, h - f, joint)
    canvas.drawLine(w, h, w - f, h - f, joint)
    canvas.restore()

    // Bevels: a lit outer edge on the upper left, a shaded one on the lower right.
    val bevel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(argb(255, 226, 190, 0.5), argb(255, 226, 190, 0.05), argb(20, 8, 2, 0.5)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }
    val oc = (OUTER_CORNER - 1).toFloat()
    canvas.drawRoundRect(1.25f, 1.25f, w - 1.25f, h - 1.25f, oc, oc, bevel)
    // The inner chamfer falls towards the floor: shaded on the lit side, lit on the far side.
    val chamfer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        shader = LinearGradient(f, f, w - f, h - f, argb(28, 12, 4, 0.6), argb(255, 220, 180, 0.35), Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(f - 2.5f, f - 2.5f, w - f + 2.5f, h - f + 2.5f, corner + 2.5f, corner + 2.5f, chamfer)

    // The floor: pale oak, grain running the long way.
    canvas.save()
    canvas.clipPath(inner)
    canvas.drawBitmap(floorWood, woodSrc, woodDst, smoothPaint)
    // The walls shade the floor along the lit side.
    val wallShade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 22f
        maskFilter = BlurMaskFilter(sigmaToRadius(6f), BlurMaskFilter.Blur.NORMAL)
        shader = LinearGradient(f, f, w - f, h - f, argb(50, 24, 8, 0.55), argb(50, 24, 8, 0.12), Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(f - 6, f - 6, w - f + 6, h - f + 6, corner + 6, corner + 6, wallShade)

    // Cups: shallow bowls, dark on the side towards the light and lit on the far rim.
    val cupR = CUP_R.toFloat()
    val bowl = Paint(Paint.ANTI_ALIAS_FLAG)
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    for (c in tray.cupX.indices) {
        val x = (trayW / 2 + tray.cupX[c]).toFloat()
        val y = (trayH / 2 + tray.cupY[c]).toFloat()
        bowl.shader = RadialGradient(
            x + 3, y + 3.5f, cupR * 1.15f,
            intArrayOf(argb(255, 236, 205, 0.32), argb(120, 70, 30, 0.12), argb(70, 36, 12, 0.5)),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, cupR, bowl)
        rim.shader = LinearGradient(x - cupR, y - cupR, x + cupR, y + cupR, argb(60, 30, 10, 0.55), argb(255, 240, 215, 0.55), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, cupR, rim)
    }
    // A satin varnish sheen across the floor.
    val sheen = Paint().apply {
        shader = LinearGradient(f, f, w * 0.7f, h * 0.6f, argb(255, 250, 235, 0.14), argb(255, 250, 235, 0.0), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(f, f, w - f, h - f, sheen)
    canvas.restore()

    // Brass pins at the mitres.
    val pin = Paint(Paint.ANTI_ALIAS_FLAG)
    for ((x, y) in listOf(f / 2 to f / 2, w - f / 2 to f / 2, f / 2 to h - f / 2, w - f / 2 to h - f / 2)) {
        pin.shader = RadialGradient(
            x - 1.2f, y - 1.2f, 3.6f,
            intArrayOf(0xFFFFF2C2.toInt(), 0xFFC99A45.toInt(), 0xFF6B4A1C.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, 2.8f, pin)
    }
    frameWood.recycle()
    floorWood.recycle()
    return bitmap
}
