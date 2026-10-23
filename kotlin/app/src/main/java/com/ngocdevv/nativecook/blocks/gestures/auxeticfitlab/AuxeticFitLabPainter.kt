package com.ngocdevv.nativecook.blocks.gestures.auxeticfitlab

import android.graphics.*
import android.os.Build
import java.util.Locale
import kotlin.math.*

internal class AuxeticFitLabPainter(private val resources: AuxeticFitLabResources) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val plateRect = RectF(-32f, -32f, 32f, 32f)
    private val ambientRect = RectF(-64f, -64f, 64f, 64f)
    private val paperRect = RectF(0f, 0f, 393f, 1024f)
    // Float alpha on the isolated source-over union; never 6% per cell.
    private val ambientLayer = Paint().apply { colorFilter = ColorMatrixColorFilter(floatArrayOf(
        1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, .06f, 0f)) }
    private val fonts = listOf(400, 500, 600).associateWith { weight ->
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
        else Typeface.create(if (weight >= 500) "sans-serif-medium" else "sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
    private val mono = Typeface.create("monospace", Typeface.NORMAL)
    private val trackedWidths = HashMap<String, FloatArray>()

    fun draw(c: Canvas, f: AuxeticFitLabFrame, saved: Boolean, height: Float, bottom: Float) {
        c.drawColor(tint(0xF6F8F4))
        paint.shader = null; paint.colorFilter = null; paint.alpha = 255; paint.style = Paint.Style.FILL
        c.drawBitmap(resources.paper, null, paperRect, paint)
        val geometry = AuxeticFitLabGeometry(f.angle)
        val isolated = c.saveLayer(0f, 0f, 393f, height, ambientLayer)
        for (row in 0..<3) for (column in 0..<4) {
            val center = geometry.center(column, row)
            c.save(); c.translate(center.x.toFloat(), (center.y + 7).toFloat())
            c.rotate((if ((column + row) % 2 == 0) f.angle else -f.angle).toFloat())
            c.drawBitmap(resources.ambientShadow, null, ambientRect, paint); c.restore()
        }
        c.restoreToCount(isolated)
        for (row in 0..<3) for (column in 0..<4) {
            val center = geometry.center(column, row)
            c.save(); c.translate(center.x.toFloat(), (center.y + 1).toFloat())
            c.rotate((if ((column + row) % 2 == 0) f.angle else -f.angle).toFloat())
            paint.alpha = 255; c.drawBitmap(resources.tightShadow, null, plateRect, paint); c.restore()
        }
        for (row in 0..<3) for (column in 0..<4) {
            val center = geometry.center(column, row)
            c.save(); c.translate(center.x.toFloat(), center.y.toFloat())
            c.rotate((if ((column + row) % 2 == 0) f.angle else -f.angle).toFloat())
            paint.alpha = 255; c.drawBitmap(resources.plate, null, plateRect, paint); c.restore()
        }
        fill(0x2B9794)
        for (pin in geometry.pins()) c.drawCircle(pin.x.toFloat(), pin.y.toFloat(), 1.7f, paint)
        val handles = AuxeticFitLabMath.handles(f); val d = geometry.spacing
        arrow(c, 196.5, 399 - (1.5 * d + 7), 196.5, max(.5, 399 - (1.5 * d + 23)), 0x2B9794, .60 * f.arrowGain)
        arrow(c, 196.5, 399 + (1.5 * d + 7), 196.5, min(851.5, 399 + (1.5 * d + 23)), 0x2B9794, .60 * f.arrowGain)
        val leftEdge = handles.leftCenter.x - 11 * f.left.scale; val rightEdge = handles.rightCenter.x + 11 * f.right.scale
        arrow(c, max(.5, leftEdge - 2), 399.0, max(.5, leftEdge - 15), 399.0, 0xB88655, .78 * f.arrowGain)
        arrow(c, min(392.5, rightEdge + 2), 399.0, min(392.5, rightEdge + 15), 399.0, 0xB88655, .78 * f.arrowGain)
        housing(c, handles.leftCenter, f.left.scale); housing(c, handles.rightCenter, f.right.scale)

        label(c, "HINGE / MATERIAL LAB", 24f, 84.5f, 10.5f, 600, tracking = 1.8f)
        label(c, "Room to move", 24f, 124f, 30f, 600, tracking = -.6f)
        label(c, "A rigid pattern with a softer fit.", 24f, 161f, 13f, 400, 0x798885)
        label(c, "SAMPLE 01", 24f, 241f, 10.5f, 500, 0x798885, monospace = true)
        label(c, "Rotating squares", 369f, 241f, 12f, 400, align = Paint.Align.RIGHT)
        fill(0x798885, .28); c.drawRect(24f, 565.7f, 369f, 566.3f, paint)
        fill(0x798885, .22); c.drawRect(196.2f, 586f, 196.8f, 653f, paint)
        val metrics = AuxeticFitLabMath.metrics(f.angle)
        metric(c, "Extension", metrics.extensionPercent, 24f)
        metric(c, "Open area", metrics.openAreaPercent, 213f)
        label(c, "Pull a handle. Both directions grow.", 24f, 697f, 12.5f, 400, 0x798885)
        val y = 782 - bottom
        fill(0x213C3B); rect.set(24f, y - 27, 369f, y + 27); c.drawRoundRect(rect, 27f, 27f, paint)
        val title = if (saved) "Fit saved" else "Save fit"
        label(c, title, if (saved) 207f else 196.5f, y, 15.5f, 600, 0xF6F8F4, align = Paint.Align.CENTER)
        if (saved) {
            text.textSize = 15.5f; text.typeface = fonts.getValue(600)
            val x = 196.5f - text.measureText(title) / 2 - 10.5f
            stroke(0xF6F8F4, 1.6f); path.rewind(); path.moveTo(x - 5, y); path.lineTo(x - 1.5f, y + 3.5f); path.lineTo(x + 5, y - 4); c.drawPath(path, paint)
        }
    }
    private fun metric(c: Canvas, title: String, value: Double, x: Float) {
        label(c, title, x, 595f, 11.5f, 400, 0x798885)
        label(c, number(value) + "%", x, 631f, 30f, 600, tabular = true)
    }
    private fun housing(c: Canvas, center: AuxeticFitLabPoint, scale: Double) {
        fill(0x2B9794); c.drawCircle(center.x.toFloat(), center.y.toFloat(), (11 * scale).toFloat(), paint)
        stroke(0xFFFFFF, (1.5 * scale).toFloat()); c.drawLine((center.x - 1.5 * scale).toFloat(), center.y.toFloat(),
            (center.x + 1.5 * scale).toFloat(), center.y.toFloat(), paint)
    }
    private fun arrow(c: Canvas, x0: Double, y0: Double, x1: Double, y1: Double, rgb: Int, alpha: Double) {
        val distance = hypot(x1 - x0, y1 - y0)
        if (distance <= .01) return
        val ux = (x1 - x0) / distance; val uy = (y1 - y0) / distance; val head = min(4.0, distance)
        val bx = x1 - ux * head; val by = y1 - uy * head
        stroke(rgb, .8f, alpha); c.drawLine(x0.toFloat(), y0.toFloat(), bx.toFloat(), by.toFloat(), paint)
        fill(rgb, alpha); path.rewind(); path.moveTo(x1.toFloat(), y1.toFloat())
        path.lineTo((bx - uy * 2.5).toFloat(), (by + ux * 2.5).toFloat())
        path.lineTo((bx + uy * 2.5).toFloat(), (by - ux * 2.5).toFloat()); path.close(); c.drawPath(path, paint)
    }
    private fun label(c: Canvas, value: String, x: Float, y: Float, size: Float, weight: Int, rgb: Int = 0x213C3B,
        align: Paint.Align = Paint.Align.LEFT, tracking: Float = 0f, monospace: Boolean = false, tabular: Boolean = false) {
        text.shader = null; text.color = tint(rgb); text.textSize = size; text.typeface = if (monospace) mono else fonts.getValue(weight)
        text.textAlign = align; text.fontFeatureSettings = if (tabular) "tnum" else null
        val fm = text.fontMetrics; val baseline = y - (fm.ascent + fm.descent) / 2
        if (tracking == 0f) c.drawText(value, x, baseline, text) else {
            val key = "$value:$size:$weight"
            val widths = trackedWidths.getOrPut(key) { FloatArray(value.length).also { text.getTextWidths(value, it) } }
            val width = widths.sum() + (value.length - 1) * tracking
            var at = x - when (align) { Paint.Align.CENTER -> width / 2; Paint.Align.RIGHT -> width; else -> 0f }
            text.textAlign = Paint.Align.LEFT
            value.indices.forEach { i -> c.drawText(value, i, i + 1, at, baseline, text); at += widths[i] + tracking }
        }
    }
    private fun fill(rgb: Int, alpha: Double = 1.0) {
        paint.shader = null; paint.colorFilter = null; paint.style = Paint.Style.FILL; paint.color = tint(rgb, alpha)
    }
    private fun stroke(rgb: Int, width: Float, alpha: Double = 1.0) {
        fill(rgb, alpha); paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND
    }
    private fun tint(rgb: Int, alpha: Double = 1.0) = Color.argb((alpha * 255).roundToInt().coerceIn(0, 255), rgb shr 16 and 255, rgb shr 8 and 255, rgb and 255)
    companion object { fun number(value: Double) = String.format(Locale.US, "%.1f", value) }
}
