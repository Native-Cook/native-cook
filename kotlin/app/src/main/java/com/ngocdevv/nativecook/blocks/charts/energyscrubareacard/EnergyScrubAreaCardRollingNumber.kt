package com.ngocdevv.nativecook.blocks.charts.energyscrubareacard

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

internal class RollingNumber {
    companion object { const val roll = 0.15; const val reveal = roll * 1.5; val reel = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0") }
    private data class Tween(val from: Double, val to: Double, val at: Double, val duration: Double) {
        fun value(now: Double): Double { val u = if (duration == 0.0) 1.0 else Motion.clamp((now - at) / duration); return from + (to - from) * (1 - (1 - u).pow(3)) }
        fun retarget(goal: Double, now: Double, duration: Double) = Tween(value(now), goal, now, duration)
    }
    private data class Digit(var goal: Int, var present: Boolean, var position: Tween, var shown: Tween, var changed: Double = -100.0)
    private var digits = mutableListOf<Digit>(); private var places = emptyList<Int>()
    private var digitWidth = 0f; private var dotWidth = 0f; private var size = 0f; private var height = 0f; private var tracking = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun update(value: Double, decimals: Int, wholeDigits: Int, fontSize: Float, lineHeight: Float, weight: Int, color: Color, tracking: Float = 0f, still: Boolean, now: Double): Float {
        val scale = 10.0.pow(decimals).toInt(); val number = floor(abs(value) * scale + 0.5).toInt()
        paint.textSize = fontSize; paint.fontFeatureSettings = "tnum"; paint.color = color.toArgb(); paint.typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false) else if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        height = lineHeight; this.tracking = tracking
        if (size != fontSize || places.size != wholeDigits + decimals) { size = fontSize; digitWidth = ceil(paint.measureText("0") + tracking); dotWidth = paint.measureText(".") + tracking; places = (wholeDigits + decimals - 1 downTo 0).map { 10.0.pow(it).toInt() }; digits.clear() }
        if (digits.isEmpty()) digits = places.map { place ->
            val next = number / place % 10; val present = place <= scale || number >= place
            Digit(next, present, Tween(next.toDouble(), next.toDouble(), now, 0.0), Tween(1.0, if (present) 1.0 else 0.0, now, if (still) 0.0 else reveal))
        }.toMutableList()
        digits.forEachIndexed { i, digit ->
            val next = number / places[i] % 10; val from = (digit.goal % 10 + 10) % 10; val step = (next - from + 15) % 10 - 5
            if (step != 0) { digit.goal += step; val quick = floor(now * 1000) - floor(digit.changed * 1000) < roll * 1000; digit.changed = now; digit.position = digit.position.retarget(digit.goal.toDouble(), now, if (still || quick) 0.0 else roll) }
            val present = places[i] <= scale || number >= places[i]
            if (present != digit.present) { digit.present = present; digit.shown = digit.shown.retarget(if (present) 1.0 else 0.0, now, if (still) 0.0 else reveal) }
        }
        return dotWidth + digits.sumOf { digitWidth * it.shown.value(now) }.toFloat()
    }
    fun draw(scope: DrawScope, x: Float, y: Float, decimals: Int, now: Double) = with(scope) {
        var left = x; val scale = 10.0.pow(decimals).toInt()
        fun text(value: String, x: Float, top: Float, center: Boolean) {
            val baseline = top + (height - paint.fontMetrics.ascent - paint.fontMetrics.descent) / 2f
            drawIntoCanvas { it.nativeCanvas.drawText(value, x - if (center) paint.measureText(value) / 2f else 0f, baseline, paint) }
        }
        digits.forEachIndexed { i, digit ->
            if (places[i] == scale / 10) { text(".", left, y, false); left += dotWidth }
            val shown = digit.shown.value(now).toFloat(); val width = digitWidth * shown
            clipRect(left, y, left + width, y + height) {
                val oldAlpha = paint.alpha; paint.alpha = (oldAlpha * shown).toInt()
                val at = ((digit.position.value(now) % 10) + 10) % 10
                for (row in 0..10) text(reel[row], left + digitWidth / 2f + tracking / 2f, y + (row - at).toFloat() * height, true)
                paint.alpha = oldAlpha
            }
            left += width
        }
    }
}
