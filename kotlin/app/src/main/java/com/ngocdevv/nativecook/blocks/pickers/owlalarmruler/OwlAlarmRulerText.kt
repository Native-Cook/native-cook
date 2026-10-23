package com.ngocdevv.nativecook.blocks.pickers.owlalarmruler

import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.max

/** Cached Android text metrics matching the one-line React Native Text nodes in the reference. */
internal object OwlAlarmRulerText {
    data class Metric(
        val width: Float,
        val height: Float,
        val baseline: Float,
    )

    val headline = paint(OwlAlarmRulerTheme.type.headline)
    val time = paint(OwlAlarmRulerTheme.type.time)
    val timeTabular = TextPaint(time).apply {
        fontFeatureSettings = "'tnum'"
    }
    val meridiem = paint(OwlAlarmRulerTheme.type.meridiem)
    val caption = paint(OwlAlarmRulerTheme.type.caption)
    val cta = paint(OwlAlarmRulerTheme.type.cta).apply {
        fontFeatureSettings = "'tnum'"
    }

    val hours = arrayOf("12", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
    val tens = arrayOf("0", "1", "2", "3", "4", "5", "0")
    val ones = arrayOf("0", "5", "0")

    data class RuntimeMetrics(
        val headline: Metric,
        val time: Metric,
        val meridiem: Metric,
        val caption: Metric,
        val cta: Metric,
        val hourWidths: FloatArray,
        val maxHourWidth: Float,
        val digitWidth: Float,
        val colonWidth: Float,
    )

    private val metricsByDensity = mutableMapOf<Float, RuntimeMetrics>()
    private var lastDensity = Float.NaN
    private var lastMetrics: RuntimeMetrics? = null

    fun metrics(density: Float): RuntimeMetrics {
        if (density == lastDensity) {
            return checkNotNull(lastMetrics)
        }
        val metrics = metricsByDensity.getOrPut(density) {
            val hourWidths = FloatArray(12) { index ->
                metric(hours[index], time, includeFontPadding = false, density = density).width
            }
            RuntimeMetrics(
                headline = metric("You chose your goal.", headline, includeFontPadding = true, density = density),
                time = metric("12", time, includeFontPadding = false, density = density),
                meridiem = metric("PM", meridiem, includeFontPadding = true, density = density),
                caption = metric(OwlAlarmRulerTheme.copy.caption, caption, includeFontPadding = true, density = density),
                cta = metric("Set alarm for 1:05 PM", cta, includeFontPadding = true, density = density),
                hourWidths = hourWidths,
                maxHourWidth = hourWidths.maxOrNull() ?: 0f,
                digitWidth = metric("0", timeTabular, includeFontPadding = false, density = density).width,
                colonWidth = metric(":", time, includeFontPadding = false, density = density).width,
            )
        }
        lastDensity = density
        lastMetrics = metrics
        return metrics
    }

    private fun paint(spec: OwlAlarmRulerTheme.TextStyleSpec): TextPaint {
        return TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            color = spec.color
            textSize = spec.size.toFloat()
            typeface = weightedTypeface(spec.weight.toInt())
            letterSpacing = (spec.ls / spec.size).toFloat()
            isLinearText = false
        }
    }

    private fun metric(
        value: String,
        paint: TextPaint,
        includeFontPadding: Boolean,
        density: Float,
    ): Metric {
        val physicalPaint = TextPaint(paint).apply {
            textSize = paint.textSize * density
        }
        val desiredWidth = Layout.getDesiredWidth(value, physicalPaint)
        val layoutWidth = max(1, ceil(desiredWidth.toDouble()).toInt())
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, physicalPaint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(includeFontPadding)
            .setMaxLines(1)
            .build()
        return Metric(
            width = layoutWidth.toFloat() / density,
            height = layout.height.toFloat() / density,
            baseline = layout.getLineBaseline(0).toFloat() / density,
        )
    }

    private fun weightedTypeface(weight: Int): Typeface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
        }
        val style = if (weight >= 700) Typeface.BOLD else Typeface.NORMAL
        return Typeface.create("sans-serif", style)
    }
}
