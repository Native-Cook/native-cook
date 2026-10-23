package com.ngocdevv.nativecook.blocks.charts.vitalschipmorph

import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow

/** The three screen bodies drawn in the 1206-pixel reference coordinate space. */
internal object VitalsChipMorphPages {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val readinessBody = arrayOf(
        "You had a good night’s sleep last", "night and all your other metrics are",
        "typical. It looks like you’re rested", "and ready for today.",
    )
    private val fitnessHeading = arrayOf("Your Exercise Ring", "Looks Good")
    private val fitnessBody = arrayOf("Your Exercise ring is ahead of", "where it usually is by now.")
    private val heartHeading = arrayOf("Cardio Fitness", "Trending Up")
    private val heartBody = arrayOf(
        "On average, your cardio fitness",
        "went up over the last month. This is",
        "great for your long-term health.",
    )
    private val checkGroups = arrayOf(
        doubleArrayOf(280.0, 370.0),
        doubleArrayOf(562.0, 642.0),
        doubleArrayOf(836.0, 915.0),
    )
    private val checkIcons = arrayOf("target", "dots", "bed")
    private val checkSizes = doubleArrayOf(57.0, 57.0, 66.0)
    private val miniChartDots = arrayOf(
        doubleArrayOf(720.0, 2099.0),
        doubleArrayOf(802.0, 2059.0),
        doubleArrayOf(887.0, 2097.0),
        doubleArrayOf(972.0, 2087.0),
        doubleArrayOf(1057.0, 2074.0),
    )
    private val miniChartIcons = arrayOf("mini-heart", "mini-lungs", "mini-temp", "mini-oxygen", "mini-wave")
    private val axisLabels = arrayOf("12 AM", "6 AM", "12 PM", "6 PM")
    private val moveBars = doubleArrayOf(22.0, 36.0, 70.0, 44.0, 58.0, 30.0, 24.0, 22.0, 20.0)
    private val exerciseBars = doubleArrayOf(14.0, 26.0, 66.0, 60.0, 18.0, 22.0, 30.0, 16.0, 14.0)
    private val heartBars = arrayOf(
        doubleArrayOf(34.0, 678.0, 697.0), doubleArrayOf(41.25, 670.0, 688.0), doubleArrayOf(48.5, 670.0, 694.0), doubleArrayOf(55.75, 665.0, 695.0), doubleArrayOf(63.0, 670.0, 697.0),
        doubleArrayOf(70.25, 677.0, 698.0), doubleArrayOf(77.5, 685.0, 699.0), doubleArrayOf(84.75, 676.0, 699.0), doubleArrayOf(92.0, 687.0, 699.0), doubleArrayOf(99.25, 686.0, 703.0),
        doubleArrayOf(106.5, 687.0, 703.0), doubleArrayOf(113.75, 696.0, 703.0), doubleArrayOf(121.0, 696.0, 703.0), doubleArrayOf(128.25, 660.0, 698.0), doubleArrayOf(135.5, 670.0, 694.0),
        doubleArrayOf(142.75, 681.0, 695.0), doubleArrayOf(150.0, 680.0, 701.0), doubleArrayOf(157.25, 687.0, 700.0), doubleArrayOf(164.5, 680.0, 700.0), doubleArrayOf(171.75, 681.0, 703.0),
    )
    private var sleepyContent: Bitmap? = null
    private var refPerLogicalPoint = 3.0
    private var refPerPixel = 1.0
    private var dividerDash = DashPathEffect(floatArrayOf(6f, 7.8f), 0f)
    private val shapeBounds = RectF()

    fun configureScale(refToPx: Double, density: Double) {
        val logicalPoint = density / refToPx
        if (logicalPoint != refPerLogicalPoint) {
            refPerLogicalPoint = logicalPoint
            dividerDash = DashPathEffect(
                floatArrayOf((2 * refPerLogicalPoint).toFloat(), (2.6 * refPerLogicalPoint).toFloat()),
                0f,
            )
        }
        refPerPixel = 1 / refToPx
    }

    fun draw(canvas: Canvas, lo: Int, hi: Int, u: Double, quiet: Boolean) {
        readiness(canvas, lo, hi, u, quiet)
        fitness(canvas, lo, hi, u, quiet)
        heart(canvas, lo, hi, u, quiet)
    }

    private fun readiness(c: Canvas, lo: Int, hi: Int, u: Double, quiet: Boolean) {
        layer(c, 0, 0, lo, hi, u, quiet) { checks(c) }
        layer(c, 0, 1, lo, hi, u, quiet) { text(c, "Ready", 96.0, 1366.0, T.heading) }
        readinessBody.forEachIndexed { index, value -> layer(c, 0, index + 2, lo, hi, u, quiet) { text(c, value, 96.0, 1480.0 + index * 78.0, T.body) } }
        layer(c, 0, 6, lo, hi, u, quiet) { text(c, "Raising Your Score", 96.0, 1891.0, T.section) }
        layer(c, 0, 7, lo, hi, u, quiet) {
            linkRow(c, 99.0, 2021.0, "Vitals, Overnight", 520.0)
            text(c, "Typical", 96.0, 2085.0, T.value)
            miniChart(c)
        }
        layer(c, 0, 8, lo, hi, u, quiet) {
            linkRow(c, 99.0, 2284.0, "Sleep Score", 412.0)
            c.drawBitmap(sleepyBitmap(), 60f, 2080f, null)
        }
    }

    private fun fitness(c: Canvas, lo: Int, hi: Int, u: Double, quiet: Boolean) {
        fitnessHeading.forEachIndexed { i, value -> layer(c, 1, i + 1, lo, hi, u, quiet) { text(c, value, 96.0, 1366.0 + i * 108.0, T.heading) } }
        fitnessBody.forEachIndexed { i, value -> layer(c, 1, i + 3, lo, hi, u, quiet) { text(c, value, 96.0, 1590.0 + i * 78.0, T.body) } }
        layer(c, 1, 5, lo, hi, u, quiet) {
            fitnessChart(c)
            text(c, "375/500 CAL", 620.0, 1852.0, T.stat, C.movePink)
            text(c, "19/30 MIN", 620.0, 1919.0, T.stat, C.exerciseText)
            text(c, "4/12 HRS", 620.0, 1987.0, T.stat, C.standCyan)
        }
        layer(c, 1, 6, lo, hi, u, quiet) {
            metric(c, 96.0, "Steps", "5,253", "Avg: 5,053", C.heartDark)
            metric(c, 620.0, "Cardio Fitness", "38 VO₂ max", "High", C.heartDark)
        }
    }

    private fun heart(c: Canvas, lo: Int, hi: Int, u: Double, quiet: Boolean) {
        heartHeading.forEachIndexed { i, value -> layer(c, 2, i + 1, lo, hi, u, quiet) { text(c, value, 96.0, 1366.0 + i * 108.0, T.heading) } }
        heartBody.forEachIndexed { i, value -> layer(c, 2, i + 3, lo, hi, u, quiet) { text(c, value, 96.0, 1578.0 + i * 78.0, T.body) } }
        layer(c, 2, 6, lo, hi, u, quiet) {
            text(c, "43–120 BPM", 96.0, 1907.0, T.chartTitle)
            heartChart(c)
            text(c, "12AM", 96.0, 2169.0, T.axis)
            text(c, "12AM", 1040.0, 2169.0, T.axis)
        }
        layer(c, 2, 7, lo, hi, u, quiet) {
            metricLabel(c, 96.0, 2247.0, "Resting Heart Rate")
            text(c, "55 BPM", 96.0, 2316.0, T.metricValue, C.supportValue)
            metricLabel(c, 636.0, 2247.0, "Recovery HRV")
            text(c, "54 MS", 636.0, 2316.0, T.metricValue, C.supportValue)
        }
    }

    private inline fun layer(c: Canvas, screen: Int, index: Int, lo: Int, hi: Int, u: Double, quiet: Boolean, block: () -> Unit) {
        val alpha: Double
        val x: Double
        if (lo == hi) {
            if (lo != screen) return
            alpha = 1.0
            x = 0.0
        } else if (screen == hi) {
            val arriving = if (quiet) seg(u, .5, 1.0) else easeOut(seg(u, .38 + .03 * index, .86 + .03 * index))
            alpha = arriving
            x = if (quiet) 0.0 else 72.0 * (1.0 - arriving)
        } else if (screen == lo) {
            val leaving = if (quiet) seg(u, 0.0, .5) else easeInOut(seg(u, .02 * index, .4 + .02 * index))
            alpha = 1.0 - leaving
            x = if (quiet) 0.0 else -72.0 * leaving
        } else {
            return
        }
        if (alpha <= 0.0) return
        c.save()
        c.translate(x.toFloat(), 0f)
        c.saveLayerAlpha(null, (255.0 * alpha).toInt().coerceIn(0, 255))
        block()
        c.restore()
        c.restore()
    }

    private fun checks(c: Canvas) {
        checkGroups.forEachIndexed { i, group ->
            VitalsChipMorphIcons.draw(c, checkIcons[i], group[0], 1206.0, checkSizes[i])
            VitalsChipMorphIcons.draw(c, "check", group[1], 1206.0, 50.0)
        }
        fill(C.hairline)
        c.drawRect(465f, 1176f, (465 + 2 * refPerPixel).toFloat(), 1236f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (1.4 * refPerLogicalPoint).toFloat()
        paint.color = C.hairline.toArgb()
        paint.pathEffect = dividerDash
        c.drawLine((742 + refPerLogicalPoint).toFloat(), 1176f, (742 + refPerLogicalPoint).toFloat(), 1236f, paint)
        paint.pathEffect = null
    }

    private fun linkRow(c: Canvas, x: Double, top: Double, label: String, chevronX: Double) {
        VitalsChipMorphIcons.draw(c, "check", x + 22.5, top + .35 * T.link.size * 3, 45.0)
        text(c, label, x + 50.0, top, T.link)
        VitalsChipMorphIcons.draw(c, "chevron", chevronX + 18.0, top + 21.0, 36.0)
    }

    private fun miniChart(c: Canvas) {
        fill(C.bandLine)
        shapeBounds.set(668f, 2006f, 1112f, 2015f)
        c.drawRoundRect(shapeBounds, 4.5f, 4.5f, paint)
        fill(C.band)
        c.drawRect(668f, 2023f, 1112f, 2129f, paint)
        fill(C.bandLine)
        shapeBounds.set(668f, 2137f, 1112f, 2146f)
        c.drawRoundRect(shapeBounds, 4.5f, 4.5f, paint)
        miniChartDots.forEach { p ->
            fill(Color.White)
            c.drawCircle(p[0].toFloat(), p[1].toFloat(), 13f, paint)
            stroke(C.link, 6.0)
            c.drawCircle(p[0].toFloat(), p[1].toFloat(), 13f, paint)
        }
        miniChartIcons.forEachIndexed { i, key -> VitalsChipMorphIcons.draw(c, key, miniChartDots[i][0], 2186.0, 42.0) }
    }

    private fun sleepRing(c: Canvas) {
        c.saveLayerAlpha(null, (255 * .8).toInt())
        arc(c, 1003.0, 2372.0, 80.0, 268.0, 358.0, C.sleepTeal, 30.0, 3.5)
        arc(c, 1003.0, 2372.0, 80.0, 4.0, 58.0, C.sleepCoral, 30.0, 3.5)
        arc(c, 1003.0, 2372.0, 80.0, 64.0, 262.0, C.sleepBlue, 30.0, 3.5)
        VitalsChipMorphText.drawBaseline(c, "84", 1003.0, 2394.5, 63.0, 700, C.muted, center = true, blurSigma = 3.5)
        paint.maskFilter = null
        c.restore()
    }

    private fun softText(c: Canvas, value: String, x: Double, top: Double) {
        val size = T.value.size * 3.0
        VitalsChipMorphText.drawBaseline(c, value, x, top + (.953 - .247) * size, size, 700, C.ink.copy(alpha = .72f), blurSigma = 3.5)
    }

    /** Hardware Canvas ignores [BlurMaskFilter] on several API 26–30 devices.  Cache the two
     * static under-glass items into a software bitmap once, then composite that bitmap per layer. */
    private fun sleepyBitmap(): Bitmap {
        sleepyContent?.let { return it }
        return synchronized(this) {
            sleepyContent ?: Bitmap.createBitmap(1100, 450, Bitmap.Config.ARGB_8888).also { bitmap ->
                val software = Canvas(bitmap)
                software.translate(-60f, -2080f)
                softText(software, "High", 96.0, 2342.0)
                sleepRing(software)
                sleepyContent = bitmap
            }
        }
    }

    private fun fitnessChart(c: Canvas) {
        fill(C.hairline.copy(alpha = .6f))
        c.drawRect(96f, 1880f, 492f, 1881.5f, paint)
        c.drawRect(96f, 1968f, 492f, 1969.5f, paint)
        moveBars.forEachIndexed { i, h -> rounded(c, 226.0 + 10.0 * i, 1880.0 - h, 4.2, h, 2.1, C.movePink) }
        exerciseBars.forEachIndexed { i, h -> rounded(c, 226.0 + 10.0 * i, 1968.0 - h, 4.2, h, 2.1, C.exerciseText) }
        repeat(5) { i ->
            fill(C.standCyan.copy(alpha = if (i == 4) .3f else 1f))
            c.drawCircle((242 + 20 * i).toFloat(), 2006f, 7f, paint)
        }
        val axisCapTop = 2052.0 + .247 * T.axis.size * 3.0
        axisLabels.forEachIndexed { i, value -> text(c, value, 96.0 + 135.0 * i, axisCapTop, T.axis) }
    }

    private fun metric(c: Canvas, x: Double, title: String, value: String, sub: String, colour: Color) {
        metricLabel(c, x, 2160.0, title, colour)
        text(c, value, x, 2229.0, T.metricValue)
        text(c, sub, x, 2306.0, T.metricSub)
    }

    private fun metricLabel(c: Canvas, x: Double, top: Double, value: String, colour: Color = C.heartDark) {
        val w = text(c, value, x, top, T.metricLink, colour)
        val size = T.metricLink.size * 3
        val lineHeight = VitalsChipMorphText.lineHeight(size, T.metricLink.weight.toInt())
        val centreY = top - .247 * size + maxOf(lineHeight, 48.0) / 2
        VitalsChipMorphIcons.draw(c, "chevron", x + w + 24.0, centreY, 36.0)
    }

    private fun heartChart(c: Canvas) {
        heartBars.forEach { b -> rounded(c, (b[0] - 32.0) * 3.0 + 96.0, b[1] * 3.0, 11.4, (b[2] - b[1]) * 3.0, 5.7, C.chartPink.copy(alpha = .82f)) }
        repeat(33) { i ->
            fill(C.faint.copy(alpha = .55f))
            c.drawCircle((96.0 + (2.7 + i * 10.35) * 3.0).toFloat(), 2133f, 2.1f, paint)
        }
        fill(C.faint.copy(alpha = .7f))
        c.drawRect(102f, 2121f, 105f, 2142f, paint)
        c.drawRect(1098f, 2121f, 1101f, 2142f, paint)
        val last = heartBars.last()
        val cursorX = ((last[0] - 32.0 + 1.9) * 3.0 + 96.0).toFloat()
        val cursorY = ((last[1] - 3.0) * 3.0).toFloat()
        fill(Color.White)
        c.drawCircle(cursorX, cursorY, 9.9f, paint)
        stroke(C.heart, 7.2)
        c.drawCircle(cursorX, cursorY, 9.9f, paint)
    }

    private fun text(c: Canvas, value: String, x: Double, top: Double, spec: VitalsChipMorphTheme.TextSpec, color: Color = spec.color): Double = VitalsChipMorphText.draw(c, value, x, top, spec.size * 3.0, spec.weight.toInt(), color, spec.ls * 3.0)
    private fun fill(color: Color) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        // Set the supplied colour last so its RN opacity survives the paint reset.
        paint.color = color.toArgb()
        paint.maskFilter = null
        paint.pathEffect = null
    }

    private fun stroke(color: Color, width: Double) {
        fill(color)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width.toFloat()
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun rounded(c: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double, color: Color) {
        fill(color)
        shapeBounds.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        c.drawRoundRect(shapeBounds, r.toFloat(), r.toFloat(), paint)
    }
    private fun arc(c: Canvas, cx: Double, cy: Double, r: Double, from: Double, to: Double, color: Color, width: Double, blurSigma: Double = 0.0) {
        stroke(color, width)
        if (blurSigma > 0.0) paint.maskFilter = BlurMaskFilter(((blurSigma - .5) / .57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
        shapeBounds.set((cx - r).toFloat(), (cy - r).toFloat(), (cx + r).toFloat(), (cy + r).toFloat())
        c.drawArc(shapeBounds, (from - 90).toFloat(), (to - from).toFloat(), false, paint)
        paint.maskFilter = null
    }
    private fun seg(value: Double, start: Double, end: Double): Double {
        return ((value - start) / (end - start)).coerceIn(0.0, 1.0)
    }

    private fun easeOut(value: Double): Double {
        return 1.0 - (1.0 - value).pow(3.0)
    }

    private fun easeInOut(value: Double): Double {
        return if (value < 0.5) {
            4 * value * value * value
        } else {
            1 - (-2 * value + 2).pow(3.0) / 2
        }
    }
    private object C {
        val ink = VitalsChipMorphTheme.colors.ink
        val muted = VitalsChipMorphTheme.colors.muted
        val faint = VitalsChipMorphTheme.colors.faint
        val hairline = VitalsChipMorphTheme.colors.hairline
        val link = VitalsChipMorphTheme.colors.link
        val band = VitalsChipMorphTheme.colors.band
        val bandLine = VitalsChipMorphTheme.colors.bandLine
        val sleepTeal = VitalsChipMorphTheme.colors.sleepTeal
        val sleepCoral = VitalsChipMorphTheme.colors.sleepCoral
        val sleepBlue = VitalsChipMorphTheme.colors.sleepBlue
        val movePink = VitalsChipMorphTheme.colors.movePink
        val exerciseText = VitalsChipMorphTheme.colors.exerciseText
        val standCyan = VitalsChipMorphTheme.colors.standCyan
        val heartDark = VitalsChipMorphTheme.colors.heartDark
        val chartPink = VitalsChipMorphTheme.colors.chartPink
        val heart = VitalsChipMorphTheme.colors.heart
        val supportValue = VitalsChipMorphTheme.colors.supportValue
    }
    private object T {
        val heading get() = VitalsChipMorphTheme.type.heading
        val body get() = VitalsChipMorphTheme.type.body
        val section get() = VitalsChipMorphTheme.type.section
        val link get() = VitalsChipMorphTheme.type.link
        val value get() = VitalsChipMorphTheme.type.value
        val stat get() = VitalsChipMorphTheme.type.stat
        val metricLink get() = VitalsChipMorphTheme.type.metricLink
        val metricValue get() = VitalsChipMorphTheme.type.metricValue
        val metricSub get() = VitalsChipMorphTheme.type.metricSub
        val axis get() = VitalsChipMorphTheme.type.axis
        val chartTitle get() = VitalsChipMorphTheme.type.chartTitle
    }
}
