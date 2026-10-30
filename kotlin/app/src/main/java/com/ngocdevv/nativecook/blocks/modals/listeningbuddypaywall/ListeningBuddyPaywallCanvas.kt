package com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall

import android.graphics.*

/** Numeric bridge keeps source-space coordinates as doubles until the platform boundary. */
internal class ListeningBuddyPaywallCanvas(private val canvas: Canvas) {
    fun save() {
        canvas.save()
    }
    fun restore() {
        canvas.restore()
    }
    fun translate(x: Number, y: Number) {
        canvas.translate(x.toFloat(), y.toFloat())
    }
    fun scale(x: Number, y: Number) {
        canvas.scale(x.toFloat(), y.toFloat())
    }
    fun rotate(degrees: Number, x: Number, y: Number) {
        canvas.rotate(degrees.toFloat(), x.toFloat(), y.toFloat())
    }
    fun drawOval(rect: RectF, paint: Paint) {
        canvas.drawOval(rect, paint)
    }
    fun drawRect(rect: RectF, paint: Paint) {
        canvas.drawRect(rect, paint)
    }
    fun drawRRect(rect: Pair<RectF, Float>, paint: Paint) {
        canvas.drawRoundRect(rect.first, rect.second, rect.second, paint)
    }
    fun drawCircle(x: Number, y: Number, radius: Number, paint: Paint) {
        canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat().coerceAtLeast(0f), paint)
    }
    fun drawLine(x1: Number, y1: Number, x2: Number, y2: Number, paint: Paint) {
        canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), paint)
    }
    fun drawPath(path: ListeningBuddyPaywallPath, paint: Paint) {
        canvas.drawPath(path.path, paint)
    }
    fun clipPath(path: ListeningBuddyPaywallPath) {
        canvas.clipPath(path.path)
    }
    fun clipRect(rect: RectF) {
        canvas.clipRect(rect)
    }
    fun clipRRect(rect: Pair<RectF, Float>) {
        canvas.clipPath(Path().apply {
            addRoundRect(rect.first, rect.second, rect.second, Path.Direction.CW)
        })
    }
    fun drawText(value: String, x: Number, y: Number, paint: Paint) {
        canvas.drawText(value, x.toFloat(), y.toFloat(), paint)
    }
    fun drawBitmap(bitmap: Bitmap, x: Float, y: Float, paint: Paint?) {
        canvas.drawBitmap(bitmap, x, y, paint)
    }
}
