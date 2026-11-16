package com.ngocdevv.nativecook.blocks.pickers.heatshapepicker

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/** One outline is reused by every lighting pass. All dimensions here are in dp. */
internal class HeatShapePickerArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val filters = listOf(.8f, 1.5f, 8f, 12f, 20f).associateWith {
        BlurMaskFilter(sigmaToRadius(it), BlurMaskFilter.Blur.NORMAL)
    }
    private var fallback: Bitmap? = null
    private var fallbackCanvas: Canvas? = null
    private val m = HeatShapePickerModel
    private val glyphPaths = m.modes.take(4).map { path(it, 38.0, 34.0, 15.0 / 110, null) }
    private val glyphGradients = m.modes.take(4).map {
        LinearGradient(38f, 34f - 17.25f, 38f, 34f + 17.25f, m.gradient(it.h), null, Shader.TileMode.CLAMP)
    }

    fun dispose() { fallback?.recycle(); fallback = null; fallbackCanvas = null }

    private fun path(pose: HeatShapePickerPose, cx: Double, cy: Double, scale: Double, time: Double?, bulge: Double = 0.0, direction: Double = 0.0): Path {
        val path = Path()
        for (j in 0 until 240) {
            val theta = j * 2 * PI / 240
            val delta = atan2(sin(theta - direction), cos(theta - direction))
            val r = m.radius(theta, pose.p, pose.c, pose.k, pose.phi, time) * scale * (1 + .10 * bulge * exp(-(delta / .6).pow(2)))
            val x = (cx + r * sin(theta)).toFloat(); val y = (cy - r * cos(theta)).toFloat()
            if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun stroke(canvas: Canvas, path: Path, color: Int, width: Float, blur: Float, opacity: Double = 1.0) {
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = width
        paint.color = color; paint.alpha = (Color.alpha(color) * opacity).toInt().coerceIn(0, 255)
        paint.maskFilter = filters[blur]
        canvas.drawPath(path, paint)
    }

    private fun fill(canvas: Canvas, path: Path, shader: Shader, opacity: Double) {
        paint.maskFilter = null; paint.style = Paint.Style.FILL
        paint.color = Color.WHITE; paint.shader = shader; paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    fun draw(canvas: Canvas, frame: HeatShapePickerFrame, layout: HeatShapePickerLayout, reduced: Boolean, dx: Double, dy: Double, grip: Double, direction: Double) {
        if (Build.VERSION.SDK_INT >= 28) {
            drawScene(canvas, frame, layout, reduced, dx, dy, grip, direction)
        } else {
            // API 26–27 ignore mask filters on hardware canvases. Reuse a software surface
            // at dp resolution so the actual blurred layers survive on those devices too.
            val w = ceil(layout.width).toInt().coerceAtLeast(1); val h = ceil(layout.height).toInt().coerceAtLeast(1)
            if (fallback?.width != w || fallback?.height != h) {
                dispose()
                fallback = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                fallbackCanvas = Canvas(fallback!!)
            }
            val software = fallbackCanvas!!
            software.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawScene(software, frame, layout, reduced, dx, dy, grip, direction)
            canvas.drawBitmap(fallback!!, 0f, 0f, bitmapPaint)
        }
    }

    private fun drawScene(canvas: Canvas, frame: HeatShapePickerFrame, layout: HeatShapePickerLayout, reduced: Boolean, dx: Double, dy: Double, grip: Double, direction: Double) {
        val cx = layout.width / 2 + if (reduced) 0.0 else dx
        val cy = layout.y(372.0) + if (reduced) 0.0 else dy
        val scale = layout.sx * if (reduced) 1.0 else 1 + .08 * grip
        val r = 110 * layout.sx
        val glow = frame.glow * (1 + .4 * grip)
        for (layer in frame.layers) {
            if (layer.opacity <= .001) continue
            val pose = layer.pose
            val outline = path(pose, cx, cy, scale, if (reduced) null else frame.time, if (reduced) 0.0 else grip, direction)
            val opacity = layer.opacity
            paint.maskFilter = null; paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            paint.shader = RadialGradient(cx.toFloat(), cy.toFloat(), 250f, intArrayOf(m.ramp(pose.h + .5, .22), m.ramp(pose.h + .5, 0.0)), null, Shader.TileMode.CLAMP)
            paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx.toFloat(), cy.toFloat(), 250f, paint)
            stroke(canvas, outline, m.color(0x5546FF, .80 * glow), 34f, 20f, opacity)
            stroke(canvas, outline, m.color(0xFF4A1E, .90 * glow), 14f, 8f, opacity)
            fill(canvas, outline, LinearGradient(cx.toFloat(), (cy - 1.15 * r).toFloat(), cx.toFloat(), (cy + 1.15 * r).toFloat(), m.gradient(pose.h), null, Shader.TileMode.CLAMP), opacity)
            canvas.save()
            canvas.clipPath(outline)
            fill(canvas, outline, RadialGradient((cx - .22 * r).toFloat(), (cy - .30 * r).toFloat(), (.95 * r).toFloat(), intArrayOf(m.color(0xFFFFFF, .26), m.color(0xFFFFFF, 0.0)), null, Shader.TileMode.CLAMP), opacity)
            stroke(canvas, outline, m.ramp(pose.h - .25, .55), 22f, 12f, opacity)
            canvas.restore()
            stroke(canvas, outline, m.color(0xFF2A12), 2.2f, .8f, opacity)
            val hot = m.clamp(1 - abs((pose.h - floor(pose.h)) - .56) / .2)
            stroke(canvas, outline, m.color(0xFFFFFF, .55 * hot), 1f, 0f, opacity)
            if (!reduced && frame.morph > 0 && frame.morph < 1) {
                paint.shader = null; paint.style = Paint.Style.FILL; paint.maskFilter = filters[1.5f]
                paint.color = m.ramp(pose.h + .1, sin(PI * frame.morph) * .9)
                paint.alpha = (paint.alpha * opacity).toInt().coerceIn(0, 255)
                for (j in 0 until 16) {
                    val theta = j * PI / 8 + PI / 20
                    val delta = atan2(sin(theta - direction), cos(theta - direction))
                    val edge = m.radius(theta, pose.p, pose.c, pose.k, pose.phi, frame.time) * scale * (1 + .10 * grip * exp(-(delta / .6).pow(2)))
                    val distance = edge + 6 + 70 * m.out(frame.morph)
                    val seed = sin(j * 12.9898 + 3.1) * 43758.5453
                    val dot = 1.4 + 1.2 * (seed - floor(seed))
                    canvas.drawCircle((cx + distance * sin(theta)).toFloat(), (cy - distance * cos(theta)).toFloat(), dot.toFloat(), paint)
                }
            }
        }
        ring(canvas, frame.ring, layout)
    }

    private fun ring(canvas: Canvas, index: Double, layout: HeatShapePickerLayout) {
        val x = layout.x(28 + 87 * index).toFloat(); val y = layout.y(676.0).toFloat()
        paint.style = Paint.Style.STROKE; paint.color = Color.WHITE
        paint.shader = LinearGradient(x, y, x + 76, y + 76, m.color(0xFF2A12), m.color(0x5546FF), Shader.TileMode.CLAMP)
        paint.strokeWidth = 6f; paint.maskFilter = filters[8f]; paint.alpha = 153
        canvas.drawRoundRect(x, y, x + 76, y + 76, 24f, 24f, paint)
        paint.strokeWidth = 1.6f; paint.maskFilter = null; paint.alpha = 255
        canvas.drawRoundRect(x, y, x + 76, y + 76, 24f, 24f, paint)
        paint.shader = null
    }

    fun glyph(canvas: Canvas, index: Int, selected: Double) {
        val outline = glyphPaths[index]
        stroke(canvas, outline, m.color(0xFFFFFF, .70 * (1 - selected)), 1.8f, 0f)
        fill(canvas, outline, glyphGradients[index], selected)
        stroke(canvas, outline, m.color(0xFF2A12), 1.2f, 0f, selected)
    }
}
