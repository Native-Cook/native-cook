package com.ngocdevv.nativecook.blocks.cards.ringboxreveal

import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Skia layers of RingLight.tsx on an android.graphics.Canvas, in stage coordinates (dp; the caller scales the
 * canvas by the density): the floor behind the 3D view, the reflection's fade over it, and the stone's glints.
 */
internal class RingBoxRevealLight {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val c = DoubleArray(6)
    private val b = DoubleArray(9)
    private val g = DoubleArray(2 * FP_N + 5)
    private val pt = DoubleArray(3)
    private val f = DoubleArray(6)
    private val n = DoubleArray(3)
    private val v = DoubleArray(3)
    private val cen = DoubleArray(3)
    private val matrix = Matrix()
    private val path = Path()
    private val dstIn = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

    private fun basis(p: RingPose, w: Double) {
        RingMotion.cameraAt(p.open, p.shot, p.t, c)
        RingMotion.cameraBasis(c, w, b)
    }

    /** The box's footprint, turned with the box and projected (FP_N points), then centre, half sizes, box height. */
    private fun floorGeom(w: Double, h: Double, yaw: Double): Boolean {
        val ins = RingMotion.BASE_R * 0.8
        val hw = RingMotion.BOX_W / 2 - ins
        val hd = RingMotion.BOX_D / 2 - ins
        val r = RingMotion.BOX_RV - ins
        val cy = cos(yaw)
        val sy = sin(yaw)
        var x0 = 1e9
        var x1 = -1e9
        var y0 = 1e9
        var y1 = -1e9
        for (j in 0 until FP_N) {
            val corner = j / FP_ARC
            val k = j % FP_ARC
            val a = (corner + k.toDouble() / (FP_ARC - 1)) * PI / 2
            val ccx = if (corner == 0 || corner == 3) hw - r else r - hw
            val ccz = if (corner == 0 || corner == 1) hd - r else r - hd
            val x = ccx + r * cos(a)
            val z = ccz + r * sin(a)
            if (!RingMotion.project(c, b, w, h, x * cy + z * sy, 0.0, -x * sy + z * cy, pt)) return false
            g[2 * j] = pt[0]
            g[2 * j + 1] = pt[1]
            x0 = min(x0, pt[0]); x1 = max(x1, pt[0])
            y0 = min(y0, pt[1]); y1 = max(y1, pt[1])
        }
        if (!RingMotion.project(c, b, w, h, 0.0, 0.0, 0.0, pt)) return false
        val ox = pt[0]
        val oy = pt[1]
        if (!RingMotion.project(c, b, w, h, 0.0, RingMotion.BASE_H, 0.0, pt)) return false
        g[2 * FP_N] = (x0 + x1) / 2
        g[2 * FP_N + 1] = (y0 + y1) / 2
        g[2 * FP_N + 2] = max(1.0, (x1 - x0) / 2)
        g[2 * FP_N + 3] = max(1.0, (y1 - y0) / 2)
        g[2 * FP_N + 4] = hypot(pt[0] - ox, pt[1] - oy)
        return true
    }

    /** The floor: the paper's gradient, a soft studio light and, with a footprint, the box's broad and contact shadows. */
    private fun drawFloor(canvas: Canvas, w: Float, h: Float, paperTop: Float, paperH: Float, withShadow: Boolean) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = 0xFF000000.toInt()
        paint.shader = LinearGradient(0f, paperTop, 0f, paperTop + paperH, opaque(RingMotion.BG_TOP), opaque(RingMotion.BG_BOTTOM), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * 0.42f, h * 0.22f, w * 0.9f, rgba(255, 253, 249, 0.8), rgba(255, 253, 249, 0.0), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        if (!withShadow) return
        val cx = g[2 * FP_N].toFloat()
        val cy = g[2 * FP_N + 1].toFloat()
        val a = g[2 * FP_N + 2].toFloat()
        val bb = g[2 * FP_N + 3].toFloat()
        // broad shadow: a soft pool under and a little in front of the box
        val sx = cy + bb * 0.12f
        matrix.reset()
        matrix.postTranslate(-cx, -sx)
        matrix.postScale(1f, bb / a * 1.15f)
        matrix.postTranslate(cx, sx)
        val pool = RadialGradient(
            cx, sx, a * 1.4f,
            intArrayOf(rgba(22, 24, 38, 0.2), rgba(22, 24, 38, 0.07), rgba(22, 24, 38, 0.0)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        pool.setLocalMatrix(matrix)
        paint.shader = pool
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        // contact: the footprint itself, dark and blurred
        path.reset()
        path.moveTo(g[0].toFloat(), g[1].toFloat())
        for (j in 1 until FP_N) path.lineTo(g[2 * j].toFloat(), g[2 * j + 1].toFloat())
        path.close()
        paint.color = rgba(14, 16, 28, 0.55)
        paint.maskFilter = BlurMaskFilter(max(0.5f, (max(1.2f, a * 0.03f) - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, paint)
        paint.maskFilter = null
    }

    /** The floor below the box's front edge, out to the stage's sides and down to its bottom. */
    private fun frontRegion(w: Float, h: Float): Path {
        var iL = 0
        var iR = 0
        var iB = 0
        for (j in 1 until FP_N) {
            if (g[2 * j] < g[2 * iL]) iL = j
            if (g[2 * j] > g[2 * iR]) iR = j
            if (g[2 * j + 1] > g[2 * iB + 1]) iB = j
        }
        var passes = iB == iR
        var j = iL
        while (j != iR) {
            if (j == iB) passes = true
            j = (j + 1) % FP_N
        }
        val dir = if (passes) 1 else -1
        val p = Path()
        p.moveTo(-2f, g[2 * iL + 1].toFloat())
        j = iL
        for (guard in 0..FP_N) {
            p.lineTo(g[2 * j].toFloat(), g[2 * j + 1].toFloat())
            if (j == iR) break
            j = (j + dir + FP_N) % FP_N
        }
        p.lineTo(w + 2, g[2 * iR + 1].toFloat())
        p.lineTo(w + 2, h + 2)
        p.lineTo(-2f, h + 2)
        p.close()
        return p
    }

    /** Behind the 3D view: the paper, the studio light and the box's shadows. */
    fun backdrop(canvas: Canvas, w: Float, h: Float, p: RingPose, paperTop: Float, paperH: Float, shadows: Boolean) {
        basis(p, w.toDouble())
        val ok = shadows && floorGeom(w.toDouble(), h.toDouble(), p.yaw)
        drawFloor(canvas, w, h, paperTop, paperH, ok)
    }

    /**
     * Over the 3D view: the floor in front of the box drawn again over its reflection, see-through where it meets the
     * box (it covers FLOOR_NEAR there) and solid a box height away.
     */
    fun floorFade(canvas: Canvas, w: Float, h: Float, p: RingPose, paperTop: Float, paperH: Float) {
        basis(p, w.toDouble())
        if (!floorGeom(w.toDouble(), h.toDouble(), p.yaw)) return
        val cx = g[2 * FP_N].toFloat()
        val cy = g[2 * FP_N + 1].toFloat()
        val a = g[2 * FP_N + 2].toFloat()
        val bb = g[2 * FP_N + 3].toFloat()
        if (cy - bb > h) return
        val k = max(0.3f, (0.9 * g[2 * FP_N + 4]).toFloat() / bb)
        canvas.save()
        canvas.clipPath(frontRegion(w, h))
        canvas.saveLayer(0f, 0f, w, h, null)
        drawFloor(canvas, w, h, paperTop, paperH, true)
        matrix.reset()
        matrix.postTranslate(-cx, -cy)
        matrix.postScale(1f, bb / a)
        matrix.postTranslate(cx, cy)
        val mask = RadialGradient(
            cx, cy, a * (1 + k),
            intArrayOf(0, 0, rgba(0, 0, 0, FLOOR_NEAR), rgba(0, 0, 0, 1.0)),
            floatArrayOf(0f, 0.98f / (1 + k), 1.06f / (1 + k), 1f),
            Shader.TileMode.CLAMP,
        )
        mask.setLocalMatrix(matrix)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = mask
        paint.xfermode = dstIn
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.xfermode = null
        paint.shader = null
        canvas.restore()
        canvas.restore()
    }

    /** Over the 3D view: the stone's glints, forty crown facets flashing as they mirror the studio's lights. */
    fun sparkle(canvas: Canvas, w: Float, h: Float, p: RingPose) {
        val vis = RingMotion.sparkleOf(p.open)
        if (vis <= 0.002) return
        val t = p.t
        val burst = RingMotion.clamp01(p.burst)
        val sa = p.sFrom
        val sb = p.sTo
        val sk = RingMotion.clamp01(p.sProg)
        val fire = TINTS[sa][3] + (TINTS[sb][3] - TINTS[sa][3]) * sk
        val tr = TINTS[sa][0] + (TINTS[sb][0] - TINTS[sa][0]) * sk
        val tg = TINTS[sa][1] + (TINTS[sb][1] - TINTS[sa][1]) * sk
        val tb = TINTS[sa][2] + (TINTS[sb][2] - TINTS[sa][2]) * sk
        val glow = (RingMotion.STONES[sa].glow + (RingMotion.STONES[sb].glow - RingMotion.STONES[sa].glow) * sk) * 0.1
        val k = cbrt(p.carat / RingMotion.CARAT_MODEL)
        basis(p, w.toDouble())
        val wd = w.toDouble()
        val hd = h.toDouble()
        val sc = RingMotion.STONE_C
        if (!RingMotion.project(c, b, wd, hd, sc.x, sc.y, sc.z, cen)) return
        val rpx = RingMotion.STONE_R * k * b[8] / cen[2]
        val rs = min(rpx, STAR_MAX)
        paint.reset()
        paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND
        setPlus(paint)
        // a soft white halo round the stone, stronger in the burst
        val halo = vis * (0.1 + glow + 0.45 * burst)
        val hr = (rpx * (2.2 + 2.5 * burst)).toFloat()
        val hx = cen[0].toFloat()
        val hy = (cen[1] - rpx * 0.3).toFloat()
        if (hr > 0.5f) {
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(hx, hy, hr, rgba(255, 255, 255, 0.5 * halo), rgba(255, 255, 255, 0.0), Shader.TileMode.CLAMP)
            canvas.drawCircle(hx, hy, hr, paint)
            paint.shader = null
        }
        val cyw = cos(p.yaw)
        val syw = sin(p.yaw)
        for (i in 0 until FACETS) {
            RingMotion.facet(i, RingMotion.STONE_R * k, RingMotion.STONE_CROWN * k, f)
            n[0] = f[0] * cyw + f[2] * syw
            n[1] = f[1]
            n[2] = -f[0] * syw + f[2] * cyw
            val wx = sc.x + f[3] * cyw + f[5] * syw
            val wy = sc.y + f[4]
            val wz = sc.z - f[3] * syw + f[5] * cyw
            v[0] = c[0] - wx
            v[1] = c[1] - wy
            v[2] = c[2] - wz
            val vl = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).takeIf { it > 0 } ?: 1.0
            v[0] /= vl; v[1] /= vl; v[2] /= vl
            // twinkle: each facet breathes on its own whole number of cycles a loop (so the film repeats)
            val cyc = 2 + floor(RingMotion.hash(i + 29.0) * 5)
            val tw = 0.75 + 0.25 * sin(2 * PI * cyc * t / RingMotion.LOOP + RingMotion.hash(i + 31.0) * 6.28)
            val gl = RingMotion.glint(n, v) * tw * vis * (1 + 2.4 * burst) +
                burst * 0.5 * RingMotion.hash(i + 41.0) * (if (i % 3 == 0) 1 else 0)
            if (gl < 0.04) continue
            if (!RingMotion.project(c, b, wd, hd, wx, wy, wz, pt)) continue
            val s = min(1.6, gl)
            val len = (rs * (0.5 + 1.5 * s)).toFloat()
            val fc = FIRE[i % FIRE.size]
            val fk = fire * 0.6 * (i % 2)
            val r = tr + (fc[0] - tr) * fk
            val gg = tg + (fc[1] - tg) * fk
            val bl = tb + (fc[2] - tb) * fk
            val a = min(1.0, 0.35 + 0.65 * s)
            val x = pt[0].toFloat()
            val y = pt[1].toFloat()
            paint.style = Paint.Style.STROKE
            paint.color = rgba(r.roundToInt(), gg.roundToInt(), bl.roundToInt(), a)
            paint.strokeWidth = max(0.6, rs * 0.06).toFloat()
            canvas.drawLine(x - len, y, x + len, y, paint)
            canvas.drawLine(x, y - len, x, y + len, paint)
            paint.strokeWidth = max(0.5, rs * 0.04).toFloat()
            val d = len * 0.42f
            canvas.drawLine(x - d, y - d, x + d, y + d, paint)
            canvas.drawLine(x - d, y + d, x + d, y - d, paint)
            paint.style = Paint.Style.FILL
            paint.color = rgba(255, 255, 255, 0.9 * a)
            canvas.drawCircle(x, y, max(0.8, rs * 0.09 * (0.6 + s)).toFloat(), paint)
        }
    }

    private fun setPlus(p: Paint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) p.blendMode = BlendMode.PLUS
        else p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    companion object {
        const val FACETS = 40
        const val FP_ARC = 9
        const val FP_N = FP_ARC * 4
        const val FLOOR_NEAR = 0.6
        const val STAR_MAX = 26.0

        /** Each stone's glint tint (sRGB 0..255) and how much spectral fire it throws. */
        val TINTS = arrayOf(
            doubleArrayOf(255.0, 255.0, 255.0, 1.0),
            doubleArrayOf(170.0, 196.0, 255.0, 0.2),
            doubleArrayOf(255.0, 214.0, 200.0, 0.35),
        )
        val FIRE = arrayOf(
            doubleArrayOf(255.0, 120.0, 110.0),
            doubleArrayOf(255.0, 214.0, 120.0),
            doubleArrayOf(140.0, 255.0, 190.0),
            doubleArrayOf(120.0, 190.0, 255.0),
            doubleArrayOf(210.0, 150.0, 255.0),
        )

        fun opaque(rgb: Int): Int = (0xFF shl 24) or rgb

        fun rgba(r: Int, g: Int, b: Int, a: Double): Int =
            ((a.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }
}
