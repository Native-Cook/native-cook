package com.ngocdevv.nativecook.blocks.progress.setupchecklistallset

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.roundToInt

/** Adapters only convert Double reference coordinates to native Canvas's Float API. */
internal object SetupChecklistAllSetDrawing {
    data class Gradient(val x0: Double, val x1: Double, val colors: List<String>, val alphas: List<Double>)
    class Paint {
        val native = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        var gradient: Gradient? = null
        fun setAntiAlias(enabled: Boolean) { native.isAntiAlias = enabled }
        fun setColor(value: Int) { native.color = value }
        fun setAlphaf(value: Double) { native.alpha = (value.coerceIn(0.0,1.0)*255).roundToInt() }
        fun prepared(): android.graphics.Paint {
            gradient?.let { g ->
                native.shader = LinearGradient(g.x0.toFloat(),0f,g.x1.toFloat(),0f,
                    g.colors.mapIndexed { index, hex -> color(hex,g.alphas[index]) }.toIntArray(),
                    if (g.colors.size == 3) floatArrayOf(0f,0.5f,1f) else null,Shader.TileMode.CLAMP)
            }
            return native
        }
    }
    fun color(hex: String, alpha: Double = 1.0) = (Color.parseColor(hex) and 0x00ffffff) or ((alpha.coerceIn(0.0,1.0)*255).roundToInt() shl 24)
    class Font(context: Context, weight: String, val size: Double) {
        val native = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.createFromAsset(context.assets,"setupchecklistallset/setupchecklistallset_Inter-$weight.ttf")
            textSize = size.toFloat()
            isSubpixelText = true
        }
        fun width(string: String) = native.measureText(string).toDouble()
    }
    data class Rounded(val rect: RectF, val radius: Float)
    class Point(x: Number,y: Number) { val x = x.toDouble(); val y = y.toDouble() }
    class Path {
        val native = android.graphics.Path()
        fun moveTo(x: Number,y: Number) { native.moveTo(x.toFloat(),y.toFloat()) }
        fun lineTo(x: Number,y: Number) { native.lineTo(x.toFloat(),y.toFloat()) }
        fun quadTo(x: Number,y: Number,ex: Number,ey: Number) { native.quadTo(x.toFloat(),y.toFloat(),ex.toFloat(),ey.toFloat()) }
        fun cubicTo(x: Number,y: Number,x2: Number,y2: Number,ex: Number,ey: Number) { native.cubicTo(x.toFloat(),y.toFloat(),x2.toFloat(),y2.toFloat(),ex.toFloat(),ey.toFloat()) }
        fun close() { native.close() }
        fun addRRect(rounded: Rounded) { native.addRoundRect(rounded.rect,rounded.radius,rounded.radius,android.graphics.Path.Direction.CW) }
        fun addCircle(x: Number,y: Number,r: Number) { native.addCircle(x.toFloat(),y.toFloat(),r.toFloat(),android.graphics.Path.Direction.CW) }
        fun addArc(rect: RectF,start: Number,sweep: Number) { native.addArc(rect,start.toFloat(),sweep.toFloat()) }
    }
    class Surface(val native: Canvas) {
        fun save() { native.save() }
        fun restore() { native.restore() }
        fun translate(x: Number,y: Number) { native.translate(x.toFloat(),y.toFloat()) }
        fun scale(x: Number,y: Number) { native.scale(x.toFloat(),y.toFloat()) }
        fun rotate(degrees: Number) { native.rotate(degrees.toFloat()) }
        fun clipRect(rect: RectF) { native.clipRect(rect) }
        fun clipRRect(rounded: Rounded) { val path = Path(); path.addRRect(rounded); native.clipPath(path.native) }
        fun drawColor(color: Int) { native.drawColor(color) }
        fun drawCircle(x: Number,y: Number,r: Number,p: Paint) { native.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),p.prepared()) }
        fun drawOval(rect: RectF,p: Paint) { native.drawOval(rect,p.prepared()) }
        fun drawRect(rect: RectF,p: Paint) { native.drawRect(rect,p.prepared()) }
        fun drawRRect(rect: Rounded,p: Paint) { native.drawRoundRect(rect.rect,rect.radius,rect.radius,p.prepared()) }
        fun drawLine(x: Number,y: Number,ex: Number,ey: Number,p: Paint) { native.drawLine(x.toFloat(),y.toFloat(),ex.toFloat(),ey.toFloat(),p.prepared()) }
        fun drawPath(path: Path,p: Paint) { native.drawPath(path.native,p.prepared()) }
    }
}
