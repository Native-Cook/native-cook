package com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall

import android.graphics.Path
import android.graphics.RectF

internal class ListeningBuddyPaywallPath {
    val path = Path()
    fun moveTo(x: Number, y: Number) {
        path.moveTo(x.toFloat(), y.toFloat())
    }
    fun lineTo(x: Number, y: Number) {
        path.lineTo(x.toFloat(), y.toFloat())
    }
    fun cubicTo(x1: Number, y1: Number, x2: Number, y2: Number, x3: Number, y3: Number) {
        path.cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x3.toFloat(), y3.toFloat())
    }
    fun quadTo(x1: Number, y1: Number, x2: Number, y2: Number) {
        path.quadTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())
    }
    fun addArc(rect: RectF, start: Number, sweep: Number) {
        path.addArc(rect, start.toFloat(), sweep.toFloat())
    }
    fun close() {
        path.close()
    }
}
