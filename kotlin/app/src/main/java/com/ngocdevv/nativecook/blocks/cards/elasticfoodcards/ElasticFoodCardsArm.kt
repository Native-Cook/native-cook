package com.ngocdevv.nativecook.blocks.cards.elasticfoodcards

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Double precision geometry shared by the colored ribbon and its world-space caster. */
internal object ElasticFoodCardsArmMath {
    val anchors = arrayOf(137.0 to 363.0, 138.0 to 344.0, 132.0 to 344.0)
    private val shaftCenters = doubleArrayOf(138.5, 140.0, 133.0)
    fun inletX(pose: Int, centerX: Double = 196.5) = centerX - 0.47 * (shaftCenters[pose] - 125)
    /** WindowInsets cutout bounds are window-relative; map through screen into the block. */
    fun cutoutInletX(centerInWindow: Double?, windowScreenX: Double, viewScreenX: Double, scale: Double): Double {
        if (centerInWindow == null || !centerInWindow.isFinite() || !scale.isFinite() || scale <= 0) return 196.5
        val x = (windowScreenX + centerInWindow - viewScreenX) / scale
        return if (x.isFinite() && x >= 0 && x <= 393) x else 196.5
    }
    fun cutoutInletY(bottomInWindow: Double?, windowScreenY: Double, viewScreenY: Double, baseY: Double, scale: Double): Double {
        if (bottomInWindow == null || !bottomInWindow.isFinite() || !scale.isFinite() || scale <= 0) return -8.0
        val y = (windowScreenY + bottomInWindow - viewScreenY - baseY) / scale
        return if (y.isFinite() && y > -8 && y < 852) y else -8.0
    }
    fun wrist(pose: Int, bx: Double, by: Double, angle: Double): Pair<Double, Double> {
        val p = anchors[pose]; val r = angle * PI / 180; val x = 0.47 * (125 - p.first); val y = 0.47 * (202 - p.second)
        return (bx + cos(r) * x - sin(r) * y) to (by + sin(r) * x + cos(r) * y)
    }
    /** One mesh remains vertical through the camera before bending below its lower bound. */
    fun mesh(ax: Double, inletY: Double, wx: Double, wy: Double, angle: Double, out: DoubleArray): Double {
        val lead = inletY > -8.0
        val alpha = ribbon(ax, inletY, wx, wy, angle, out, if (lead) 4 else 0)
        if (lead) {
            out[0] = ax + 58.75; out[1] = -8.0
            out[2] = ax - 58.75; out[3] = -8.0
        }
        return alpha
    }
    /** Rows are right/left, so Android's TL→BR mesh diagonal matches Swift's r0→l1. */
    fun ribbon(ax: Double, ay: Double, wx: Double, wy: Double, angle: Double, out: DoubleArray, offset: Int = 0): Double {
        if (wy <= ay) return 0.0
        val r = angle * PI / 180; val reach = hypot(wx - ax, wy - ay) * 0.34
        val c1x = ax; val c1y = ay + reach
        val c2x = wx + sin(r) * reach; val c2y = wy - cos(r) * reach
        for (row in 0..8) {
            val t = row / 8.0; val u = 1 - t
            val x = u*u*u*ax + 3*u*u*t*c1x + 3*u*t*t*c2x + t*t*t*wx
            val y = u*u*u*ay + 3*u*u*t*c1y + 3*u*t*t*c2y + t*t*t*wy
            val dx = 3*u*u*(c1x-ax) + 6*u*t*(c2x-c1x) + 3*t*t*(wx-c2x)
            val dy = 3*u*u*(c1y-ay) + 6*u*t*(c2y-c1y) + 3*t*t*(wy-c2y)
            val length = max(0.001, hypot(dx, dy)); val nx = 58.75 * dy / length; val ny = -58.75 * dx / length
            val i = offset + row * 4
            out[i] = x + nx; out[i+1] = y + ny; out[i+2] = x - nx; out[i+3] = y - ny
        }
        return ElasticFoodCardsMath.smoother((wy-ay)/16)
    }
}

/** Each pose first composites arm and body with SrcOver; only complete poses are added. */
internal class ElasticFoodCardsHandRenderer(private val images: Array<Bitmap>) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val group = Paint()
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val world = RectF(0f, 0f, 393f, 852f)
    private val coords = Array(3) { DoubleArray(40) }
    private val vertices = Array(3) { FloatArray(40) }
    private val imageBounds = RectF(0f, 0f, 117.5f, 188f)
    private val strips = Array(3) { index ->
        val source = images[index]
        val strip = Bitmap.createBitmap(source, 0, 200, source.width, 1)
        // Flip once to cancel the mesh's reversed columns; row 200 remains constant vertically.
        Bitmap.createBitmap(strip, 0, 0, strip.width, 1, Matrix().apply { setScale(-1f, 1f) }, true).apply {
            density = Bitmap.DENSITY_NONE; prepareToDraw()
        }.also { if (it !== strip) strip.recycle() }
    }
    fun drawPose(c: Canvas, index: Int, f: ElasticFoodCardsFrame, inletX: Double, inletY: Double) {
        val wrist = ElasticFoodCardsArmMath.wrist(index, f.handBX, f.handBY, f.handAngle)
        val ax = ElasticFoodCardsArmMath.inletX(index, inletX)
        val lead = inletY > -8.0
        val alpha = ElasticFoodCardsArmMath.mesh(ax, inletY, wrist.first, wrist.second, f.handAngle, coords[index])
        p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true; p.color = Color.WHITE
        if (alpha > 0) {
            for (j in 0 until (if (lead) 40 else 36)) vertices[index][j] = coords[index][j].toFloat()
            p.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            c.drawBitmapMesh(strips[index], 1, if (lead) 9 else 8, vertices[index], 0, null, 0, p)
        }
        val anchor = ElasticFoodCardsArmMath.anchors[index]
        c.save(); c.translate(f.handBX.toFloat(), f.handBY.toFloat()); c.rotate(f.handAngle.toFloat())
        c.translate((-anchor.first * 0.47).toFloat(), (-anchor.second * 0.47).toFloat())
        c.clipRect(0f, (199 * 0.47).toFloat(), 117.5f, (365 * 0.47).toFloat())
        p.alpha = 255; c.drawBitmap(images[index], null, imageBounds, p); c.restore()
    }
    fun draw(c: Canvas, f: ElasticFoodCardsFrame, weights: DoubleArray, inletX: Double, inletY: Double, height: Float = 852f) {
        world.bottom = height
        val outer = c.saveLayer(world, null)
        for (i in 0..2) if (weights[i] > 0) {
            group.reset(); group.alpha = (weights[i] * 255).toInt().coerceIn(0, 255)
            if (Build.VERSION.SDK_INT >= 29) group.blendMode = BlendMode.PLUS else group.xfermode = add
            val pose = c.saveLayer(world, group)
            drawPose(c, i, f, inletX, inletY); c.restoreToCount(pose)
        }
        c.restoreToCount(outer)
    }
    fun dispose() { strips.forEach { it.recycle() } }
}
