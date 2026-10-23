package com.ngocdevv.nativecook.blocks.charts.glowtipscrubber

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*

internal class RollingPrice {
    companion object { const val roll = 0.14; val reel = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0") }
    private data class Digit(var goal: Int, var from: Double, var to: Double, var at: Double = -100.0, var duration: Double = 0.0, var changed: Double = -100.0) {
        fun value(now: Double): Double { val u = if (duration == 0.0) 1.0 else Motion.clamp((now - at) / duration); return from + (to - from) * (1 - (1 - u).pow(3)) }
    }
    private var digits = emptyList<Digit>(); private var pattern = ""
    fun draw(scope: DrawScope, value: Double, pattern: String, size: Float, height: Float, weight: Int, tracking: Float, color: Color, still: Boolean, now: Double, x: Float = 0f, y: Float = 0f) = with(scope) {
        val decimals = if ('.' in pattern) pattern.substringAfter('.').length else 0; val scale = 10.0.pow(decimals); val number = floor(abs(value) * scale + 0.5).toLong(); val count = pattern.count { it == '0' }; val places = (count - 1 downTo 0).map { 10.0.pow(it).toLong() }
        if (this@RollingPrice.pattern != pattern) { this@RollingPrice.pattern = pattern; digits = places.map { val next = (number / it % 10).toInt(); Digit(next, next.toDouble(), next.toDouble()) } }
        digits.forEachIndexed { i, digit ->
            val next = (number / places[i] % 10).toInt(); val from = (digit.goal % 10 + 10) % 10; val step = (next - from + 15) % 10 - 5
            if (step != 0) { digit.goal += step; val quick = floor(now * 1000) - floor(digit.changed * 1000) < roll * 1000; digit.changed = now; digit.from = digit.value(now); digit.to = digit.goal.toDouble(); digit.at = now; digit.duration = if (still || quick) 0.0 else roll }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color.toArgb(); fontFeatureSettings = "tnum"; typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false) else Typeface.DEFAULT_BOLD }
        val width = ceil(paint.measureText("0") + tracking); val baseline = (height - paint.fontMetrics.ascent - paint.fontMetrics.descent) / 2
        fun text(value: String, x: Float, top: Float, center: Boolean) { drawIntoCanvas { it.nativeCanvas.drawText(value, x - if (center) paint.measureText(value) / 2 else 0f, top + baseline, paint) } }
        var left = x; var digit = 0
        for (char in pattern) {
            if (char != '0') { val label = char.toString(); text(label, left, y, false); left += paint.measureText(label) + tracking }
            else { val at = ((digits[digit].value(now) % 10) + 10) % 10; clipRect(left, y, left + width, y + height) { for (row in 0..10) text(reel[row], left + width / 2 + tracking / 2, y + (row - at).toFloat() * height, true) }; left += width; digit++ }
        }
    }
}
