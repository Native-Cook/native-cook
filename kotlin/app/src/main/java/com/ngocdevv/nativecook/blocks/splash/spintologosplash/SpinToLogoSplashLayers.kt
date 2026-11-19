package com.ngocdevv.nativecook.blocks.splash.spintologosplash

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

// The Skia layers of the reference (SpinFloor.tsx and SpinMark.tsx) on an android.graphics.Canvas, drawn in dp: the
// caller scales the canvas by the density first.

private fun argb(a: Float, r: Int, g: Int, b: Int): Int =
    (clamp01(a) * 255f + 0.5f).toInt() shl 24 or (r shl 16) or (g shl 8) or b

private fun withAlpha(color: Long, a: Float): Int = (color.toInt() and 0xFFFFFF) or ((clamp01(a) * 255f + 0.5f).toInt() shl 24)

/** Under the 3D view: the paper, the shadows and the floor track. */
internal class SpinFloorPainter(private val w: Float, private val h: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bg = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(PAPER_TOP.toInt(), PAPER_MID.toInt(), PAPER_BOTTOM.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val pool = Paint().apply {
        shader = RadialGradient(
            w * 0.5f, h * 0.4f, w * 0.85f,
            intArrayOf(argb(0.75f, 255, 253, 249), argb(0f, 255, 253, 249)), null, Shader.TileMode.CLAMP,
        )
    }
    private val vignette = Paint().apply {
        shader = RadialGradient(
            w * 0.5f, h * 0.45f, max(w, h) * 0.75f,
            intArrayOf(argb(0f, 60, 48, 36), argb(0f, 60, 48, 36), argb(0.07f, 60, 48, 36)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val c = FloatArray(6)
    private val q = FloatArray(3)
    private val q2 = FloatArray(3)
    private val path = Path()

    private fun shadowSpot(canvas: Canvas, x: Float, y: Float, rx: Float, ry: Float, alpha: Float) {
        if (alpha < 0.004f || rx < 0.5f) return
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(1f, max(ry, 0.3f) / rx)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF000000.toInt()
        paint.shader = RadialGradient(
            0f, 0f, rx,
            intArrayOf(argb(alpha, 40, 32, 24), argb(alpha * 0.45f, 40, 32, 24), argb(0f, 40, 32, 24)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(0f, 0f, rx, paint)
        paint.shader = null
        canvas.restore()
    }

    private fun ring(r: Float, from: Float = 0f, sweep: Float = 1f, steps: Int = TRACK_STEPS, dir: Float = 1f): Path {
        path.rewind()
        for (s in 0..steps) {
            val a = dir * (from + s.toFloat() / steps * sweep) * PI.toFloat() * 2
            project(r * sin(a), 0f, r * cos(a), c, w, h, q)
            if (s == 0) path.moveTo(q[0], q[1]) else path.lineTo(q[0], q[1])
        }
        return path
    }

    fun draw(canvas: Canvas, t: Float, yaw: Float) {
        canvas.drawRect(0f, 0f, w, h, bg)
        canvas.drawRect(0f, 0f, w, h, pool)
        canvas.drawRect(0f, 0f, w, h, vignette)

        cameraAt(t, c)
        val ui = clamp01(posterUi(t))
        val vis = clamp01(stageVisible(t))
        val fill = trackFill(t, yaw)
        val pop = trackPop(t)

        if (vis > 0.01f) {
            project(0f, 0f, 0f, c, w, h, q)
            project(1.1f, 0f, 0f, c, w, h, q2)
            val rx = abs(q2[0] - q[0])
            project(0f, 0f, 1.1f, c, w, h, q2)
            val ry = abs(q2[1] - q[1])
            shadowSpot(canvas, q[0], q[1], rx * 1.05f, ry * 1.05f, 0.1f * vis)
            val base = Math.toRadians(yaw.toDouble()).toFloat()
            for (i in ARCHES.indices) {
                val ang = base + Math.toRadians(archYaw(t, i).toDouble()).toFloat()
                val ca = cos(ang)
                val sa = sin(ang)
                for (side in intArrayOf(-1, 1)) {
                    val r = ARCHES[i].r * side
                    val fx = r * ca
                    val fz = -r * sa
                    project(fx, 0f, fz, c, w, h, q)
                    project(fx + 0.17f, 0f, fz, c, w, h, q2)
                    val sx = abs(q2[0] - q[0])
                    project(fx, 0f, fz + 0.17f, c, w, h, q2)
                    val sy = abs(q2[1] - q[1])
                    shadowSpot(canvas, q[0], q[1] + sy * 0.15f, sx, sy + 1.5f, 0.42f * vis)
                }
            }
            val py = pearlY(t)
            val k = clamp01((PEARL_FLOAT - py) / (PEARL_FLOAT - PEARL_R))
            project(0f, 0f, 0f, c, w, h, q)
            val rr = PEARL_R * (1.9f - 0.9f * k)
            project(rr, 0f, 0f, c, w, h, q2)
            val sx = abs(q2[0] - q[0])
            project(0f, 0f, rr, c, w, h, q2)
            val sy = abs(q2[1] - q[1])
            shadowSpot(canvas, q[0], q[1], sx, sy + 1, (0.14f + 0.36f * k) * vis)
        }

        if (ui > 0.01f) {
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.25f
            paint.strokeCap = Paint.Cap.BUTT
            paint.color = argb(0.13f * ui, 29, 28, 26)
            canvas.drawPath(ring(TRACK_R), paint)
            paint.color = argb(0.2f * ui, 29, 28, 26)
            for (k in 0 until 12) {
                val a = k / 12f * PI.toFloat() * 2
                project((TRACK_R + 0.03f) * sin(a), 0f, (TRACK_R + 0.03f) * cos(a), c, w, h, q)
                project((TRACK_R + 0.07f) * sin(a), 0f, (TRACK_R + 0.07f) * cos(a), c, w, h, q2)
                canvas.drawLine(q[0], q[1], q2[0], q2[1], paint)
            }
            if (fill > 0.002f) {
                val dir = if (yaw < 0) -1f else 1f
                paint.strokeWidth = 3 + 2 * pop
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.color = withAlpha(INK, ui)
                canvas.drawPath(ring(TRACK_R, 0f, fill, trackSteps(fill), dir), paint)
                val a = dir * fill * PI.toFloat() * 2
                project(TRACK_R * sin(a), 0f, TRACK_R * cos(a), c, w, h, q)
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(PAPER_TOP, ui)
                canvas.drawCircle(q[0], q[1], 7 + 3 * pop, paint)
                paint.color = withAlpha(INK, ui)
                canvas.drawCircle(q[0], q[1], 4.5f + 2 * pop, paint)
            }
            if (pop > 0.01f) {
                val u = seg(t, SpinPhase.TURN_END - 0.05f, SpinPhase.TURN_END + 0.4f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.strokeCap = Paint.Cap.BUTT
                paint.color = withAlpha(INK, 0.3f * (1 - u) * ui)
                canvas.drawPath(ring(TRACK_R + 0.12f * u), paint)
            }
        }
    }
}

/** Where the mark lands on the welcome screen: the outer arch's radius (dp). */
internal const val MARK_FINAL_R = 40f

/** Over the 3D view: the ghost finger and the flat brand mark traced from the arches at the hand-off. */
internal class SpinMarkPainter(private val w: Float, private val h: Float, private val finalBase: Float) {
    private val paths = ArrayList<Path>()
    private val union = Path()
    private var cx = 0f
    private var cy = 0f
    private var r = 1f
    private var left = w
    private var right = 0f
    private var top = h
    private val anchorX = w / 2
    private val anchorY: Float
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gleamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val f = FloatArray(3)
    private val g = FloatArray(2)

    init {
        val c = FloatArray(6)
        cameraAt(SpinPhase.HANDOFF, c)
        val q = FloatArray(3)
        val n = 72
        for (arch in ARCHES) {
            val z = arch.b * 0.35f
            val p = Path()
            for (s in 0..n) {
                val th = s.toFloat() / n * PI.toFloat()
                project((arch.r + arch.a) * cos(th), (arch.r + arch.a) * sin(th), z, c, w, h, q)
                if (s == 0) p.moveTo(q[0], q[1]) else p.lineTo(q[0], q[1])
                left = min(left, q[0]); right = max(right, q[0]); top = min(top, q[1])
            }
            for (s in n downTo 0) {
                val th = s.toFloat() / n * PI.toFloat()
                project((arch.r - arch.a) * cos(th), (arch.r - arch.a) * sin(th), z, c, w, h, q)
                p.lineTo(q[0], q[1])
            }
            p.close()
            paths.add(p)
            union.addPath(p)
        }
        project(0f, PEARL_R, 0f, c, w, h, q)
        val focal = w / 2 / tan(HFOV_DEG * PI / 360).toFloat()
        val disc = Path().apply { addCircle(q[0], q[1], PEARL_R / q[2] * focal, Path.Direction.CW) }
        paths.add(disc)
        union.addPath(disc)
        project(0f, 0f, ARCHES[0].b * 0.35f, c, w, h, q)
        cx = q[0]; cy = q[1]
        project(ARCHES[0].r + ARCHES[0].a, 0f, ARCHES[0].b * 0.35f, c, w, h, q)
        r = abs(q[0] - cx)

        val c0 = FloatArray(6)
        cameraAt(0f, c0)
        project(0f, 0.2f, 0.6f, c0, w, h, q)
        anchorY = q[1] + 6
    }

    fun draw(canvas: Canvas, t: Float, live: Float) {
        val vis = markVisible(t)
        filmFinger(t, f)
        val fv = f[1] * clamp01(live)
        markGleam(t, g)
        val k = clamp01(markRise(t))
        if (vis > 0.004f) {
            val s = lerp(1f, MARK_FINAL_R / r, k)
            val x = lerp(cx, w / 2, k)
            val y = lerp(cy, finalBase, k)
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(s, s)
            canvas.translate(-cx, -cy)
            paint.style = Paint.Style.FILL
            paint.shader = null
            for (i in paths.indices) {
                paint.color = withAlpha(MARK_COLORS[i], vis)
                canvas.drawPath(paths[i], paint)
            }
            if (g[1] > 0.01f) {
                canvas.save()
                canvas.clipPath(union)
                val gx = lerp(left, right, g[0])
                val span = (right - left) * 0.22f
                gleamPaint.shader = LinearGradient(
                    gx - span, top, gx + span, cy,
                    intArrayOf(argb(0f, 255, 250, 240), argb(0.55f * g[1] * vis, 255, 250, 240), argb(0f, 255, 250, 240)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                )
                canvas.drawRect(left - 4, top - 4, right + 4, cy + 4, gleamPaint)
                canvas.restore()
            }
            canvas.restore()
        }
        if (fv > 0.004f) {
            val fx = anchorX + f[0] / YAW_PER_PT
            val fy = anchorY
            val lift = 1 - f[2]
            val rr = 22 * (1 + 0.12f * lift)
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                fx, fy + 3 + 4 * lift, rr * 1.5f,
                intArrayOf(argb(0.16f * fv, 40, 32, 24), argb(0.16f * fv, 40, 32, 24), argb(0f, 40, 32, 24)),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(fx, fy + 3 + 4 * lift, rr * 1.5f, paint)
            paint.shader = null
            paint.color = argb(0.62f * fv, 255, 255, 255)
            canvas.drawCircle(fx, fy, rr, paint)
            paint.color = argb(0.1f * fv, 29, 28, 26)
            canvas.drawCircle(fx, fy, rr * 0.62f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = argb(0.14f * fv, 29, 28, 26)
            canvas.drawCircle(fx, fy, rr, paint)
        }
    }
}
