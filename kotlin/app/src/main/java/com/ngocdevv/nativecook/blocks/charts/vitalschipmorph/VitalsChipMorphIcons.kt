package com.ngocdevv.nativecook.blocks.charts.vitalschipmorph

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Small icon atlas. [size] is the icon's actual reference-pixel width, with [cx]/[cy] at its centre. */
internal object VitalsChipMorphIcons {
    private const val INK = 0xFF000000.toInt()
    private const val FAINT = 0xFFB9B8C0.toInt()
    private const val LINK = 0xFF1E88E5.toInt()
    private const val ICON_GREY = 0xFF6E6E73.toInt()
    private const val CHECK = 0xFF1E8DE6.toInt()
    private const val HEART = 0xFFF42E59.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val bounds = RectF()
    private val summaryGradient = LinearGradient(
        0f,
        3f,
        0f,
        21.35f,
        0xFFFF5C95.toInt(),
        0xFFF0246A.toInt(),
        Shader.TileMode.CLAMP,
    )
    private val fitnessRings = arrayOf(floatArrayOf(12.6f, .75f), floatArrayOf(8.4f, .63f), floatArrayOf(4.2f, .33f))
    private val fitnessTracks = intArrayOf(0xFFF6C0CE.toInt(), 0xFFC4EDC4.toInt(), 0xFFB5E5F1.toInt())
    private val fitnessColours = intArrayOf(0xFFFA0232.toInt(), 0xFF05DD04.toInt(), 0xFF02C1DB.toInt())
    private val vitalsCentres = arrayOf(floatArrayOf(15f, 7f), floatArrayOf(7f, 15f), floatArrayOf(23f, 15f), floatArrayOf(15f, 23f))
    private val targetRadii = floatArrayOf(6.6f, 4.4f, 2.2f)
    private val dotPoints = arrayOf(
        floatArrayOf(9.5f, 3.6f, 1f),
        floatArrayOf(3.6f, 9.5f, .55f),
        floatArrayOf(15.4f, 9.5f, .55f),
        floatArrayOf(9.5f, 15.4f, .55f),
    )
    private val boldTypeface = if (Build.VERSION.SDK_INT >= 28) {
        Typeface.create(Typeface.DEFAULT, 700, false)
    } else {
        Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val heartPath: Path = PathParser.createPathFromPathData(
        "M12 21.35 10.55 20C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09A6.04 6.04 0 0 1 16.5 3C19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.5L12 21.35Z",
    ) ?: Path()

    /** Supported keys: summary, sleep, readiness, fitness, heart, vitals, target, dots, bed,
     * check, chevron, mini-heart, mini-lungs, mini-temp, mini-oxygen, mini-wave, longevity, browse. */
    fun draw(canvas: Canvas, key: String, cx: Double, cy: Double, size: Double, color: ComposeColor = VitalsChipMorphTheme.colors.heart) {
        canvas.save()
        when (key) {
            "summary" -> transform(canvas, cx, cy, size, 30.0, 40.0) { summary(canvas) }
            "sleep" -> transform(canvas, cx, cy, size, 32.0, 32.0) { sleep(canvas) }
            "readiness" -> transform(canvas, cx, cy, size, 32.0, 32.0) { readiness(canvas) }
            "fitness" -> transform(canvas, cx, cy, size, 32.0, 32.0) { fitness(canvas) }
            "heart" -> transform(canvas, cx, cy, size, 24.0, 24.0) { heart(canvas, color.toArgb()) }
            "vitals" -> transform(canvas, cx, cy, size, 30.0, 30.0) { vitals(canvas) }
            "target" -> transform(canvas, cx, cy, size, 19.0, 19.0) { target(canvas) }
            "dots" -> transform(canvas, cx, cy, size, 19.0, 19.0) { dots(canvas) }
            "bed" -> transform(canvas, cx, cy, size, 22.0, 16.0) { bed(canvas) }
            "check" -> transform(canvas, cx, cy, size, 20.0, 20.0) { check(canvas) }
            "chevron" -> transform(canvas, cx, cy, size, 12.0, 16.0) { chevron(canvas) }
            "mini-heart" -> transform(canvas, cx, cy, size, 14.0, 14.0) { miniHeart(canvas) }
            "mini-lungs" -> transform(canvas, cx, cy, size, 14.0, 14.0) { lungs(canvas) }
            "mini-temp" -> transform(canvas, cx, cy, size, 14.0, 14.0) { temperature(canvas) }
            "mini-oxygen" -> transform(canvas, cx, cy, size, 14.0, 14.0) { oxygen(canvas) }
            "mini-wave" -> transform(canvas, cx, cy, size, 14.0, 14.0) { wave(canvas) }
            "longevity" -> transform(canvas, cx, cy, size, 26.0, 26.0) { longevity(canvas) }
            "browse" -> transform(canvas, cx, cy, size, 24.0, 24.0) { browse(canvas) }
        }
        canvas.restore()
    }

    private inline fun transform(canvas: Canvas, cx: Double, cy: Double, width: Double, baseW: Double, baseH: Double, draw: () -> Unit) {
        val s = (width / baseW).toFloat()
        canvas.translate(cx.toFloat() - width.toFloat() / 2f, cy.toFloat() - baseH.toFloat() * s / 2f)
        canvas.scale(s, s)
        draw()
    }

    private fun solid(color: Int, style: Paint.Style = Paint.Style.FILL, stroke: Float = 1f) = paint.apply {
        shader = null
        alpha = 255
        this.color = color
        this.style = style
        strokeWidth = stroke
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
    }

    private fun summary(c: Canvas) {
        c.save()
        c.translate(4f, 1f)
        c.scale(.92f, .92f)
        paint.apply {
            alpha = 255
            style = Paint.Style.FILL
            shader = summaryGradient
        }
        c.drawPath(heartPath, paint)
        c.restore()
        solid(FAINT)
        paint.alpha = (255 * .75f).toInt()
        roundRect(c, 2f, 27f, 28f, 29f, 1f)
        roundRect(c, 2f, 33.5f, 19f, 35.5f, 1f)
        paint.alpha = 255
    }

    private fun sleep(c: Canvas) {
        arc(c, 16f, 16f, 13f, 268f, 358f, 0xFF1FCFB0.toInt(), 5.2f)
        arc(c, 16f, 16f, 13f, 4f, 58f, 0xFFF47A5C.toInt(), 5.2f)
        arc(c, 16f, 16f, 13f, 64f, 262f, 0xFF3D63F0.toInt(), 5.2f)
        text(c, "84", 16f, 20.3f, 12f, 700, INK, true)
    }

    private fun readiness(c: Canvas) {
        arc(c, 16f, 18f, 12f, -78f, 78f, 0xFFDFDFE9.toInt(), 4.6f, Paint.Cap.ROUND)
        arc(c, 16f, 18f, 12f, -2f, 33f, 0xFFB2D3F2.toInt(), 4.6f, Paint.Cap.ROUND)
        solid(0xFF1592EA.toInt())
        c.drawCircle(16f + 12f * sin(PI.toFloat() / 6f), 18f - 12f * cos(PI.toFloat() / 6f), 2.6f, paint)
        text(c, "7", 15.5f, 25f, 11f, 700, 0xFF1592EA.toInt(), true)
    }

    private fun fitness(c: Canvas) {
        for (i in fitnessRings.indices) {
            circleStroke(c, 16f, 16f, fitnessRings[i][0], fitnessTracks[i], 3.8f)
            arc(c, 16f, 16f, fitnessRings[i][0], 0f, 360f * fitnessRings[i][1], fitnessColours[i], 3.8f, Paint.Cap.ROUND)
        }
    }
    private fun vitals(canvas: Canvas) {
        vitalsCentres.forEachIndexed { index, point ->
            circleStroke(canvas, point[0], point[1], 4.8f, if (index == 0) 0xFFDF61D2.toInt() else 0xFF378DDD.toInt(), 2.8f)
        }
    }

    private fun target(canvas: Canvas) {
        solid(ICON_GREY)
        paint.alpha = (255 * .9f).toInt()
        canvas.drawCircle(9.5f, 9.5f, 8.6f, paint)
        solid(Color.WHITE, Paint.Style.STROKE, .9f)
        paint.alpha = (255 * .55f).toInt()
        targetRadii.forEach { radius ->
            canvas.drawCircle(9.5f, 9.5f, radius, paint)
        }
        solid(Color.WHITE)
        canvas.drawCircle(9.5f, 9.5f, .9f, paint)
    }

    private fun dots(canvas: Canvas) {
        dotPoints.forEach { point ->
            solid(ICON_GREY, Paint.Style.STROKE, 1.5f)
            paint.alpha = (255 * point[2]).toInt()
            canvas.drawCircle(point[0], point[1], 2.7f, paint)
        }
    }

    private fun bed(canvas: Canvas) {
        solid(ICON_GREY)
        roundRect(canvas, 2f, 1f, 20f, 7f, 1.6f)
        solid(Color.WHITE)
        paint.alpha = (255 * .4f).toInt()
        roundRect(canvas, 4f, 3f, 10f, 6f, 1f)
        roundRect(canvas, 12f, 3f, 18f, 6f, 1f)
        solid(ICON_GREY)
        roundRect(canvas, .5f, 7f, 21.5f, 13f, 1.4f)
        roundRect(canvas, .5f, 12f, 2.9f, 15.5f, .6f)
        roundRect(canvas, 19.1f, 12f, 21.5f, 15.5f, .6f)
    }

    private val checkPath = PathParser.createPathFromPathData("M5.6 10.4 8.6 13.3 14.4 7")!!
    private val chevronPath = PathParser.createPathFromPathData("m3.5 2.5 5 5.5-5 5.5")!!
    private val leftLung = PathParser.createPathFromPathData("M6.2 2v4.2C6.2 9 4.8 12 2.6 12 1.3 12 1 10.8 1 9.6 1 7 2.6 4 4.6 4c.9 0 1.6.6 1.6 1.6")!!
    private val rightLung = PathParser.createPathFromPathData("M7.8 2v4.2C7.8 9 9.2 12 11.4 12 12.7 12 13 10.8 13 9.6 13 7 11.4 4 9.4 4c-.9 0-1.6.6-1.6 1.6")!!
    private val dropPath = PathParser.createPathFromPathData("M6 1.2C4.4 3.6 2.4 6 2.4 8.4a3.6 3.6 0 0 0 7.2 0C9.6 6 7.6 3.6 6 1.2Z")!!
    private val wavePath = PathParser.createPathFromPathData("M2.2 7.6 3.8 5.2 5.4 8.8 7 5.2 8.6 8.8 10.2 5.2 11.8 7.4")!!
    private val petalPath = PathParser.createPathFromPathData("M13 13 L13 1.6 A11.4 11.4 0 0 1 17.4 2.5 Z")!!

    private fun check(canvas: Canvas) {
        solid(CHECK)
        canvas.drawCircle(10f, 10f, 10f, paint)
        solid(Color.WHITE, Paint.Style.STROKE, 2.2f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(checkPath, paint)
    }

    private fun chevron(canvas: Canvas) {
        solid(FAINT, Paint.Style.STROKE, 1.8f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(chevronPath, paint)
    }

    private fun heart(canvas: Canvas, color: Int) {
        solid(color)
        canvas.drawPath(heartPath, paint)
    }

    private fun miniHeart(canvas: Canvas) {
        canvas.save()
        canvas.scale(14f / 24f, 14f / 24f)
        heart(canvas, LINK)
        canvas.restore()
    }

    private fun lungs(canvas: Canvas) {
        solid(LINK)
        canvas.drawPath(leftLung, paint)
        canvas.drawPath(rightLung, paint)
    }

    private fun temperature(canvas: Canvas) {
        solid(LINK, Paint.Style.STROKE, 1.1f)
        roundRect(canvas, 5.1f, .8f, 8.9f, 9.8f, 1.9f)
        solid(LINK)
        canvas.drawCircle(7f, 11.2f, 2.4f, paint)
        canvas.drawRect(6.4f, 4f, 7.6f, 10f, paint)
    }

    private fun oxygen(canvas: Canvas) {
        solid(LINK, Paint.Style.STROKE, 1.2f)
        canvas.drawPath(dropPath, paint)
        solid(LINK)
        canvas.drawCircle(6f, 8.6f, 1.5f, paint)
        text(canvas, "2", 11.4f, 13.4f, 4.4f, 700, LINK, true)
    }

    private fun wave(canvas: Canvas) {
        solid(LINK)
        roundRect(canvas, .6f, 2.2f, 13.4f, 11.8f, 1.6f)
        solid(Color.WHITE, Paint.Style.STROKE, 1f)
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(wavePath, paint)
    }

    private fun longevity(canvas: Canvas) {
        solid(INK)
        canvas.drawCircle(13f, 13f, 12f, paint)
        for (index in 0 until 8) {
            canvas.save()
            canvas.rotate((index * 45 + 12).toFloat(), 13f, 13f)
            solid(Color.WHITE)
            paint.alpha = (255 * .92f).toInt()
            canvas.drawPath(petalPath, paint)
            canvas.restore()
        }
        solid(Color.WHITE)
        canvas.drawCircle(13f, 13f, 4.2f, paint)
        solid(INK)
        canvas.drawCircle(13f, 13f, 2.6f, paint)
    }

    private fun browse(canvas: Canvas) {
        solid(INK)
        for (row in 0..1) {
            for (column in 0..1) {
                roundRect(canvas, 1f + column * 12, 1f + row * 12, 11f + column * 12, 11f + row * 12, 2.2f)
            }
        }
    }

    private fun circleStroke(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, width: Float) {
        solid(color, Paint.Style.STROKE, width)
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun arc(canvas: Canvas, cx: Float, cy: Float, radius: Float, from: Float, to: Float, color: Int, width: Float, cap: Paint.Cap = Paint.Cap.BUTT) {
        solid(color, Paint.Style.STROKE, width)
        paint.strokeCap = cap
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(bounds, from - 90f, to - from, false, paint)
    }

    private fun text(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, weight: Int, color: Int, center: Boolean) {
        solid(color)
        paint.textSize = size
        paint.typeface = if (weight >= 600) boldTypeface else Typeface.DEFAULT
        val left = x - if (center) paint.measureText(value) / 2f else 0f
        canvas.drawText(value, left, baseline, paint)
    }

    private fun roundRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        bounds.set(left, top, right, bottom)
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }
}
