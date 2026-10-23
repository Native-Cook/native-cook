package com.ngocdevv.nativecook.blocks.charts.vitalschipmorph

import android.graphics.Canvas
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.util.SparseArray
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Text set in reference pixels. [capTop] is the actual cap-height top, not the font box top. */
internal object VitalsChipMorphText {
    private val bounds = Rect()
    private val fontMetrics = Paint.FontMetrics()
    private val typefaces = SparseArray<Typeface>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        isDither = true
        fontFeatureSettings = "tnum"
    }

    fun draw(
        canvas: Canvas,
        text: String,
        x: Double,
        capTop: Double,
        size: Double,
        weight: Int,
        color: Color,
        ls: Double = 0.0,
        center: Boolean = false,
        blurSigma: Double = 0.0,
        rounded: Boolean = false,
    ): Double {
        if (text.isEmpty()) return 0.0
        paint.textSize = size.toFloat()
        paint.color = color.toArgb()
        paint.typeface = typeface(weight, rounded)
        paint.letterSpacing = if (size == 0.0) 0f else (ls / size).toFloat()
        // A fixed capital H defines the cap metric. Measuring the content itself would make
        // digits, lowercase rows, and descenders jump relative to their shared cap coordinate.
        paint.getTextBounds("H", 0, 1, bounds)
        val width = measured(text)
        var left = x.toFloat()
        if (center) left -= width.toFloat() / 2f
        // Android reports glyph bounds relative to the baseline. This makes the actual cap top
        // coincide with the reference coordinate across the system sans-serif fallbacks.
        val baseline = capTop.toFloat() - bounds.top
        if (blurSigma > 0.0) paint.maskFilter = BlurMaskFilter(((blurSigma - .5) / .57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
        canvas.drawText(text, left, baseline, paint)
        paint.maskFilter = null
        return width
    }

    fun measure(text: String, size: Double, weight: Int, ls: Double = 0.0, rounded: Boolean = false): Double {
        paint.textSize = size.toFloat()
        paint.typeface = typeface(weight, rounded)
        paint.letterSpacing = if (size == 0.0) 0f else (ls / size).toFloat()
        return measured(text)
    }

    fun lineHeight(size: Double, weight: Int, rounded: Boolean = false): Double {
        paint.textSize = size.toFloat()
        paint.typeface = typeface(weight, rounded)
        paint.letterSpacing = 0f
        paint.getFontMetrics(fontMetrics)
        return (fontMetrics.bottom - fontMetrics.top).toDouble()
    }

    /** SVG-style text uses a supplied baseline rather than a cap top. */
    fun drawBaseline(
        canvas: Canvas,
        text: String,
        x: Double,
        baseline: Double,
        size: Double,
        weight: Int,
        color: Color,
        center: Boolean = false,
        blurSigma: Double = 0.0,
    ): Double {
        if (text.isEmpty()) return 0.0
        paint.textSize = size.toFloat()
        paint.color = color.toArgb()
        paint.typeface = typeface(weight, rounded = false)
        paint.letterSpacing = 0f
        val width = measured(text)
        if (blurSigma > 0.0) paint.maskFilter = BlurMaskFilter(((blurSigma - .5) / .57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
        canvas.drawText(text, x.toFloat() - if (center) width.toFloat() / 2f else 0f, baseline.toFloat(), paint)
        paint.maskFilter = null
        return width
    }

    fun width(text: String, size: Double, weight: Int, ls: Double = 0.0): Double = measure(text, size, weight, ls)

    private fun measured(text: String): Double {
        return paint.measureText(text).toDouble()
    }

    private fun typeface(weight: Int, rounded: Boolean): Typeface {
        val key = weight * 2 + if (rounded) 1 else 0
        typefaces[key]?.let { return it }
        val family = if (rounded) "sans-serif-rounded" else "sans-serif"
        val typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.create(family, Typeface.NORMAL), weight, false)
        } else if (weight >= 600) {
            Typeface.create(family, Typeface.BOLD)
        } else if (weight >= 500 && !rounded) {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        } else {
            Typeface.create(family, Typeface.NORMAL)
        }
        typefaces.put(key, typeface)
        return typeface
    }
}
