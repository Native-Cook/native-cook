package com.ngocdevv.nativecook.blocks.lists.luckytaskhub
import android.graphics.*
/** Numeric adapter keeps the reference drawing in its original 720-pixel coordinates. */
internal class LuckyTaskHubCanvas(val native: Canvas) {
    data class RoundRect(val rect: RectF, val radius: Float)
    class Shape {
        val native = Path()
        fun moveTo(x: Number, y: Number) = native.moveTo(x.toFloat(), y.toFloat())
        fun lineTo(x: Number, y: Number) = native.lineTo(x.toFloat(), y.toFloat())
        fun quadTo(x1: Number, y1: Number, x2: Number, y2: Number) = native.quadTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())
        fun cubicTo(x1: Number, y1: Number, x2: Number, y2: Number, x3: Number, y3: Number) = native.cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x3.toFloat(), y3.toFloat())
        fun arcToOval(r: RectF, start: Number, sweep: Number, force: Boolean) = native.arcTo(r, start.toFloat(), sweep.toFloat(), force)
        fun close() = native.close()
    }
    fun save() {
        native.save()
    }
    fun restore() {
        native.restore()
    }
    fun saveLayer(p: Paint) {
        native.saveLayer(null, p)
    }
    fun translate(x: Number, y: Number) = native.translate(x.toFloat(), y.toFloat())
    fun scale(x: Number, y: Number) = native.scale(x.toFloat(), y.toFloat())
    fun rotate(degrees: Number, x: Number, y: Number) = native.rotate(degrees.toFloat(), x.toFloat(), y.toFloat())
    fun skew(x: Number, y: Number) = native.skew(x.toFloat(), y.toFloat())
    fun drawRRect(r: RoundRect, p: Paint) = native.drawRoundRect(r.rect, r.radius, r.radius, p)
    fun clipRRect(r: RoundRect) {
        native.clipPath(Path().apply {
            addRoundRect(r.rect, r.radius, r.radius, Path.Direction.CW)
        }
        )
    }
    fun clipRect(r: RectF) {
        native.clipRect(r)
    }
    fun drawRect(r: RectF, p: Paint) = native.drawRect(r, p)
    fun drawOval(r: RectF, p: Paint) = native.drawOval(r, p)
    fun drawCircle(x: Number, y: Number, r: Number, p: Paint) = native.drawCircle(x.toFloat(), y.toFloat(), r.toFloat().coerceAtLeast(0f), p)
    fun drawLine(x1: Number, y1: Number, x2: Number, y2: Number, p: Paint) = native.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), p)
    fun drawPath(s: Shape, p: Paint) = native.drawPath(s.native, p)
    fun drawColor(color: Int) = native.drawColor(color)
}
