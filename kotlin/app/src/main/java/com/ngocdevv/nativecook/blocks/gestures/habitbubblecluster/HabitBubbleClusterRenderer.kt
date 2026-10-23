package com.ngocdevv.nativecook.blocks.gestures.habitbubblecluster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal data class HabitBubbleClusterFinger(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var alpha: Double = 0.0,
)

internal data class HabitBubbleClusterLabel(
    var index: Int = -1,
    var atMs: Double = -1_000_000.0,
)

internal class HabitBubbleClusterRenderer(context: Context) {
    private val tokens = HabitBubbleClusterTokens
    private val regular = Typeface.createFromAsset(
        context.assets,
        "habitbubblecluster/habitbubblecluster_Inter-Regular.ttf",
    )
    private val medium = Typeface.createFromAsset(
        context.assets,
        "habitbubblecluster/habitbubblecluster_Inter-Medium.ttf",
    )
    private val semibold = Typeface.createFromAsset(
        context.assets,
        "habitbubblecluster/habitbubblecluster_Inter-SemiBold.ttf",
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var page: Bitmap? = null
    private var frosted: Bitmap? = null
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var glass33: HabitBubbleClusterGlass33? = null

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        safeTop: Float,
        safeBottom: Float,
        originX: Float,
        originY: Float,
        simulation: HabitBubbleClusterSimulation,
        finger: HabitBubbleClusterFinger,
        label: HabitBubbleClusterLabel,
        reducedMotion: Boolean,
    ) {
        ensureBackdrop(width, height)
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        page?.let { canvas.drawBitmap(it, 0f, 0f, paint) }

        canvas.save()
        canvas.translate(originX, originY)
        drawQuiet(canvas, simulation, originX, originY)
        simulation.entities.indices.forEach { index ->
            if (index == simulation.quietIndex) return@forEach
            val radius = simulation.radius[index].toFloat()
            if (radius < 1f) return@forEach
            val entity = simulation.entities[index]
            val hue = tokens.Hues.getValue(entity.habit.hue)
            val x = simulation.x[index].toFloat()
            val y = simulation.y[index].toFloat()
            val charge = if (simulation.ring == index) simulation.charge.toFloat() else 0f
            val done = simulation.done[index].toFloat()
            val held = if (simulation.held == index) 1f else 0f
            drawGlass(
                canvas = canvas,
                x = x - radius,
                y = y - radius,
                width = radius * 2f,
                height = radius * 2f,
                radius = radius,
                tint = hue.tint,
                tintStrength = 0.17f + 0.10f * max(charge, done),
                thickness = 0.30f + 0.50f * held,
                glowX = simulation.fingerX.toFloat(),
                glowY = simulation.fingerY.toFloat(),
                glow = if (reducedMotion) 0f else 0.8f * held,
                pageOffsetX = originX,
                pageOffsetY = originY,
            )
            val level = max(charge, done)
            drawLiquid(
                canvas = canvas,
                x = x,
                y = y,
                radius = radius,
                level = level,
                top = hue.top,
                bottom = hue.bottom,
                clockMs = simulation.clockMs,
                calm = if (reducedMotion) 0f else 1f - done,
            )
            if (level > 0.002f) {
                stroke(Color.WHITE, 1.2f, 0.22f + 0.20f * level)
                canvas.drawCircle(x, y, radius - 0.75f, paint)
            }
            if (simulation.burst == index && !reducedMotion) {
                val progress = clamp01(simulation.burstMs / HabitBubbleClusterTokens.Ripple.durationMs).toFloat()
                val rippleRadius = radius + 4f + HabitBubbleClusterTokens.Ripple.reach.toFloat() * easeOut(progress)
                stroke(Color.parseColor(hue.top), 1.5f * (1f - progress) + 0.5f, 0.7f * (1f - progress))
                canvas.drawCircle(x, y, rippleRadius, paint)
            }
        }
        drawHoldRing(canvas, simulation)
        drawLabel(canvas, width, originX, simulation, label, reducedMotion)
        if (finger.alpha > 0.01) {
            fill(Color.parseColor(tokens.Finger))
            canvas.drawCircle(finger.x.toFloat(), finger.y.toFloat(), HabitBubbleClusterTokens.Layout.fingerRadius.toFloat(), paint)
            stroke(Color.parseColor(tokens.FingerRing), 1f)
            canvas.drawCircle(finger.x.toFloat(), finger.y.toFloat(), HabitBubbleClusterTokens.Layout.fingerRadius.toFloat() - 0.5f, paint)
        }
        canvas.restore()

        drawChrome(canvas, width, height, safeTop, safeBottom, originY, simulation)
    }

    private fun ensureBackdrop(width: Float, height: Float) {
        val bitmapWidth = max(1, width.toInt())
        val bitmapHeight = max(1, height.toInt())
        if (bitmapWidth == cachedWidth && bitmapHeight == cachedHeight) return
        cachedWidth = bitmapWidth
        cachedHeight = bitmapHeight

        page?.recycle()
        frosted?.recycle()
        page = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val background = Canvas(bitmap)
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = LinearGradient(
                0f,
                0f,
                0f,
                height,
                intArrayOf(Color.parseColor("#101317"), Color.parseColor("#0D0F12"), Color.parseColor("#121110")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            background.drawRect(0f, 0f, width, height, paint)
            drawRadial(
                background,
                width * 0.12f,
                height * 0.22f,
                width * 0.95f,
                intArrayOf(Color.parseColor("#3382AA96"), Color.parseColor("#125A786E"), Color.TRANSPARENT),
                width,
                height,
            )
            drawRadial(
                background,
                width * 0.90f,
                height * 0.70f,
                width * 0.95f,
                intArrayOf(Color.parseColor("#2BDCBE8C"), Color.parseColor("#0FA07850"), Color.TRANSPARENT),
                width,
                height,
            )
            paint.shader = null
            paint.strokeWidth = 1f
            for (y in 0 until bitmapHeight step 3) {
                for (x in 0 until bitmapWidth step 3) {
                    val hash = ((x * 73856093) xor (y * 19349663)) and 255
                    paint.color = if (hash > 127) Color.argb(5, 255, 255, 255) else Color.argb(4, 0, 0, 0)
                    background.drawPoint(x.toFloat(), y.toFloat(), paint)
                }
            }
        }
        val smallWidth = max(1, bitmapWidth / 3)
        val smallHeight = max(1, bitmapHeight / 3)
        val small = Bitmap.createScaledBitmap(page!!, smallWidth, smallHeight, true)
        frosted = Bitmap.createScaledBitmap(small, bitmapWidth, bitmapHeight, true)
        if (small !== frosted) small.recycle()
        if (Build.VERSION.SDK_INT >= 33) {
            glass33 = HabitBubbleClusterGlass33(frosted!!)
        }
    }

    private fun drawRadial(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        colors: IntArray,
        width: Float,
        height: Float,
    ) {
        paint.shader = RadialGradient(
            x,
            y,
            radius,
            colors,
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun drawGlass(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        tint: FloatArray,
        tintStrength: Float,
        thickness: Float,
        glowX: Float,
        glowY: Float,
        glow: Float,
        pageOffsetX: Float = 0f,
        pageOffsetY: Float = 0f,
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            glass33?.draw(
                canvas,
                x,
                y,
                width,
                height,
                radius,
                tint,
                tintStrength,
                thickness,
                glowX,
                glowY,
                glow,
                pageOffsetX,
                pageOffsetY,
            )
            return
        }
        val bounds = RectF(x, y, x + width, y + height)
        canvas.save()
        canvas.clipPath(path.apply {
            reset()
            addRoundRect(bounds, radius, radius, Path.Direction.CW)
        })
        frosted?.let {
            canvas.drawBitmap(it, -pageOffsetX, -pageOffsetY, paint.apply { alpha = 255; shader = null })
        }
        fill(
            Color.rgb(
                (tint[0] * 255).toInt(),
                (tint[1] * 255).toInt(),
                (tint[2] * 255).toInt(),
            ),
            0.11f + tintStrength * 0.34f,
        )
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = LinearGradient(
            x,
            y,
            x,
            y + min(28f, height / 2f),
            intArrayOf(Color.argb((40 + 35 * thickness).toInt(), 255, 255, 255), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, radius, radius, paint)
        canvas.restore()
        stroke(Color.WHITE, 1f, 0.12f + 0.12f * thickness)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        if (glow > 0f) {
            paint.shader = RadialGradient(
                glowX,
                glowY,
                46f,
                intArrayOf(Color.argb((42f * glow).toInt(), 255, 255, 255), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(bounds, radius, radius, paint)
            paint.shader = null
        }
    }

    private fun drawLiquid(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        level: Float,
        top: String,
        bottom: String,
        clockMs: Double,
        calm: Float,
    ) {
        if (level <= 0.002f) return
        val surface = y + radius - 2f * radius * level
        path.reset()
        val steps = 24
        for (step in 0..steps) {
            val px = x - radius + 2f * radius * step / steps
            val wave =
                sin(clockMs / HabitBubbleClusterTokens.Liquid.period1 + step * 0.55) * HabitBubbleClusterTokens.Liquid.amplitude1 +
                    sin(clockMs / HabitBubbleClusterTokens.Liquid.period2 - step * 0.9) * HabitBubbleClusterTokens.Liquid.amplitude2
            val py = surface + wave.toFloat() * sin(PI * clamp01(level.toDouble())).toFloat() * calm
            if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.lineTo(x + radius, y + radius)
        path.lineTo(x - radius, y + radius)
        path.close()
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(x, y, radius - 1.5f, Path.Direction.CW) })
        paint.reset()
        paint.isAntiAlias = true
        paint.alpha = 235
        paint.shader = LinearGradient(
            x,
            surface - 4f,
            x,
            y + radius,
            Color.parseColor(top),
            Color.parseColor(bottom),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, paint)
        canvas.restore()
        paint.shader = null
    }

    private fun drawQuiet(
        canvas: Canvas,
        simulation: HabitBubbleClusterSimulation,
        originX: Float,
        originY: Float,
    ) {
        val index = simulation.quietIndex
        val radius = simulation.radius[index].toFloat()
        if (radius < 1f) return
        val x = simulation.x[index].toFloat()
        val y = simulation.y[index].toFloat()
        val tint = tokens.Hues.getValue("glass").tint
        drawGlass(
            canvas,
            x - radius,
            y - radius,
            radius * 2f,
            radius * 2f,
            radius,
            tint,
            0.04f,
            0.2f,
            x,
            y,
            0f,
            originX,
            originY,
        )

        rect.set(x - radius * 0.84f, y - radius * 0.84f, x + radius * 0.84f, y + radius * 0.84f)
        path.reset()
        path.moveTo(x, y)
        path.arcTo(rect, 150f, 140f)
        path.close()
        fill(Color.BLACK, 0.22f)
        canvas.drawPath(path, paint)

        text(9f, semibold, Color.parseColor(tokens.Ink), 0.80f, Paint.Align.CENTER)
        val upper = Path().apply {
            addArc(RectF(x - radius * 0.68f, y - radius * 0.68f, x + radius * 0.68f, y + radius * 0.68f), 205f, 130f)
        }
        val upperOffset = (pathLength(upper) - paint.measureText("QUIET HOURS")) / 2f
        canvas.drawTextOnPath("QUIET HOURS", upper, max(0f, upperOffset), 0f, paint)
        val lowerRadius = radius * 0.68f + 7f * radius / 55f
        val lower = Path().apply {
            addArc(RectF(x - lowerRadius, y - lowerRadius, x + lowerRadius, y + lowerRadius), 20f, 140f)
        }
        val lowerOffset = (pathLength(lower) - paint.measureText("22:00–07:00")) / 2f
        canvas.drawTextOnPath("22:00–07:00", lower, max(0f, lowerOffset), 0f, paint)
        fill(Color.parseColor(tokens.Ink), 0.9f)
        canvas.drawCircle(x, y, 3f * radius / 55f, paint)
    }

    private fun pathLength(source: Path): Float {
        return android.graphics.PathMeasure(source, false).length
    }

    private fun drawHoldRing(canvas: Canvas, simulation: HabitBubbleClusterSimulation) {
        if (simulation.ring < 0 || simulation.charge <= 0.001) return
        val index = simulation.ring
        val radius = simulation.radius[index].toFloat() + HabitBubbleClusterTokens.Layout.ringGap.toFloat()
        val x = simulation.x[index].toFloat()
        val y = simulation.y[index].toFloat()
        rect.set(x - radius, y - radius, x + radius, y + radius)
        stroke(Color.WHITE, HabitBubbleClusterTokens.Layout.ring.toFloat(), 0.12f)
        canvas.drawCircle(x, y, radius, paint)
        stroke(Color.WHITE, HabitBubbleClusterTokens.Layout.ring.toFloat(), 0.90f)
        canvas.drawArc(rect, -90f, 360f * simulation.charge.toFloat(), false, paint)
    }

    private fun drawLabel(
        canvas: Canvas,
        width: Float,
        originX: Float,
        simulation: HabitBubbleClusterSimulation,
        label: HabitBubbleClusterLabel,
        reducedMotion: Boolean,
    ) {
        if (label.index !in simulation.entities.indices) return
        val elapsed = simulation.clockMs - label.atMs
        val enter = if (reducedMotion) 1f else easeOut((elapsed / HabitBubbleClusterTokens.Label.enterMs).toFloat())
        val exit = clamp01((elapsed - HabitBubbleClusterTokens.Label.enterMs - HabitBubbleClusterTokens.Label.holdMs) / HabitBubbleClusterTokens.Label.exitMs).toFloat()
        val alpha = min(clamp01(elapsed / HabitBubbleClusterTokens.Label.enterMs).toFloat(), 1f - exit)
        if (alpha <= 0.004f) return
        val index = label.index
        val entity = simulation.entities[index]
        val title = entity.habit.name
        val subtitle = if (simulation.doneTarget[index] > 0.5) {
            "Done today · tap to reopen"
        } else {
            "Hold to mark it done"
        }
        text(14f, semibold, Color.parseColor(tokens.Ink), 1f, Paint.Align.LEFT)
        val titleWidth = paint.measureText(title)
        text(12f, regular, Color.parseColor(tokens.InkSoft), 1f, Paint.Align.LEFT)
        val subtitleWidth = paint.measureText(subtitle)
        val labelWidth = max(titleWidth, subtitleWidth) + 32f
        var left = simulation.x[index].toFloat() - labelWidth / 2f
        left = max(-originX + HabitBubbleClusterTokens.Layout.gutter.toFloat(), min(left, width - originX - HabitBubbleClusterTokens.Layout.gutter.toFloat() - labelWidth))
        val top = simulation.y[index].toFloat() - simulation.radius[index].toFloat() -
            HabitBubbleClusterTokens.Layout.labelGap.toFloat() - HabitBubbleClusterTokens.Layout.labelHeight.toFloat() +
            if (reducedMotion) 0f else HabitBubbleClusterTokens.Label.rise.toFloat() * (1f - enter)
        val box = RectF(left, top, left + labelWidth, top + HabitBubbleClusterTokens.Layout.labelHeight.toFloat())
        fill(Color.parseColor("#1A1D21"), alpha)
        canvas.drawRoundRect(box, 16f, 16f, paint)
        stroke(Color.WHITE, 1f, 0.12f * alpha)
        canvas.drawRoundRect(box, 16f, 16f, paint)
        text(14f, semibold, Color.parseColor(tokens.Ink), alpha, Paint.Align.LEFT)
        canvas.drawText(title, left + 16f, top + 22f, paint)
        text(12f, regular, Color.parseColor(tokens.InkSoft), alpha, Paint.Align.LEFT)
        canvas.drawText(subtitle, left + 16f, top + 40f, paint)
    }

    private fun drawChrome(
        canvas: Canvas,
        width: Float,
        height: Float,
        safeTop: Float,
        safeBottom: Float,
        originY: Float,
        simulation: HabitBubbleClusterSimulation,
    ) {
        val headerY = safeTop + 4f
        drawGlass(
            canvas,
            HabitBubbleClusterTokens.Layout.gutter.toFloat(),
            headerY,
            HabitBubbleClusterTokens.Layout.segmentWidth.toFloat(),
            HabitBubbleClusterTokens.Layout.control.toFloat(),
            HabitBubbleClusterTokens.Layout.control.toFloat() / 2f,
            tokens.Hues.getValue("glass").tint,
            0.04f,
            0.2f,
            0f,
            0f,
            0f,
        )
        val itemWidth = (HabitBubbleClusterTokens.Layout.segmentWidth - 8.0) / 2.0
        val selectedLeft = HabitBubbleClusterTokens.Layout.gutter + 4.0 + itemWidth * simulation.tab
        fill(Color.parseColor(tokens.Ink))
        canvas.drawRoundRect(
            RectF(
                selectedLeft.toFloat(),
                headerY + 4f,
                (selectedLeft + itemWidth).toFloat(),
                headerY + HabitBubbleClusterTokens.Layout.control.toFloat() - 4f,
            ),
            18f,
            18f,
            paint,
        )
        tokens.Tabs.forEachIndexed { index, title ->
            text(
                size = 14f,
                typeface = semibold,
                color = Color.parseColor(if (simulation.tab == index) tokens.SelectedInk else tokens.InkSoft),
                align = Paint.Align.CENTER,
            )
            val centerX = HabitBubbleClusterTokens.Layout.gutter + 4.0 + itemWidth * (index + 0.5)
            canvas.drawText(title, centerX.toFloat(), headerY + 27f, paint)
        }
        val profileX = width - HabitBubbleClusterTokens.Layout.gutter.toFloat() - HabitBubbleClusterTokens.Layout.control.toFloat()
        drawGlass(
            canvas,
            profileX,
            headerY,
            HabitBubbleClusterTokens.Layout.control.toFloat(),
            HabitBubbleClusterTokens.Layout.control.toFloat(),
            HabitBubbleClusterTokens.Layout.control.toFloat() / 2f,
            tokens.Hues.getValue("glass").tint,
            0.04f,
            0.2f,
            0f,
            0f,
            0f,
        )
        text(14f, semibold, Color.parseColor(tokens.Ink), align = Paint.Align.CENTER)
        canvas.drawText(tokens.Monogram, profileX + HabitBubbleClusterTokens.Layout.control.toFloat() / 2f, headerY + 27f, paint)

        val dateTop = originY + HabitBubbleClusterTokens.Layout.dateDrop.toFloat()
        text(24f, semibold, Color.parseColor(tokens.Ink), align = Paint.Align.CENTER)
        paint.letterSpacingCompat(-0.3f / 24f)
        canvas.drawText(tokens.Date, width / 2f, dateTop + 24f, paint)
        paint.letterSpacingCompat(0f)
        text(14f, regular, Color.parseColor(tokens.InkSoft), align = Paint.Align.CENTER)
        canvas.drawText(tokens.Now, width / 2f, dateTop + 48f, paint)

        val bottomY = height - max(safeBottom, HabitBubbleClusterTokens.Layout.gutter.toFloat()) - 8f - HabitBubbleClusterTokens.Layout.pill.toFloat()
        drawRoundControl(canvas, HabitBubbleClusterTokens.Layout.gutter.toFloat(), bottomY)
        drawCalendar(canvas, HabitBubbleClusterTokens.Layout.gutter.toFloat() + 22f, bottomY + 22f)
        drawRoundControl(canvas, width - HabitBubbleClusterTokens.Layout.gutter.toFloat() - 44f, bottomY)
        drawPlus(canvas, width - HabitBubbleClusterTokens.Layout.gutter.toFloat() - 22f, bottomY + 22f)

        val count = simulation.count()
        text(15f, semibold, Color.parseColor(tokens.Ink), align = Paint.Align.LEFT)
        val countText = count.done.toString() + " of " + count.total
        val countWidth = paint.measureText(countText)
        text(15f, regular, Color.parseColor(tokens.InkSoft), align = Paint.Align.LEFT)
        val suffix = "done today"
        val suffixWidth = paint.measureText(suffix)
        val pillWidth = 20f + 8f + countWidth + 8f + suffixWidth + 32f
        val pillLeft = (width - pillWidth) / 2f
        drawGlass(
            canvas,
            pillLeft,
            bottomY,
            pillWidth,
            HabitBubbleClusterTokens.Layout.pill.toFloat(),
            HabitBubbleClusterTokens.Layout.pill.toFloat() / 2f,
            tokens.Hues.getValue("glass").tint,
            0.04f,
            0.2f,
            0f,
            0f,
            0f,
        )
        val ringX = pillLeft + 26f
        val ringY = bottomY + 22f
        stroke(Color.parseColor(tokens.Hairline), 2.5f)
        canvas.drawCircle(ringX, ringY, 8f, paint)
        val share = if (count.total == 0) 0f else count.done.toFloat() / count.total
        stroke(Color.parseColor(tokens.Ink), 2.5f)
        rect.set(ringX - 8f, ringY - 8f, ringX + 8f, ringY + 8f)
        canvas.drawArc(rect, -90f, 360f * share, false, paint)
        text(15f, semibold, Color.parseColor(tokens.Ink), align = Paint.Align.LEFT)
        val countX = ringX + 16f
        canvas.drawText(countText, countX, bottomY + 27f, paint)
        text(15f, regular, Color.parseColor(tokens.InkSoft), align = Paint.Align.LEFT)
        canvas.drawText(suffix, countX + countWidth + 8f, bottomY + 27f, paint)
    }

    private fun drawRoundControl(canvas: Canvas, left: Float, top: Float) {
        drawGlass(
            canvas,
            left,
            top,
            HabitBubbleClusterTokens.Layout.control.toFloat(),
            HabitBubbleClusterTokens.Layout.control.toFloat(),
            HabitBubbleClusterTokens.Layout.control.toFloat() / 2f,
            tokens.Hues.getValue("glass").tint,
            0.04f,
            0.2f,
            0f,
            0f,
            0f,
        )
    }

    private fun drawCalendar(canvas: Canvas, x: Float, y: Float) {
        stroke(Color.parseColor(tokens.Ink), 1.6f)
        rect.set(x - 8f, y - 7f, x + 8f, y + 8f)
        canvas.drawRoundRect(rect, 2f, 2f, paint)
        canvas.drawLine(x - 8f, y - 2f, x + 8f, y - 2f, paint)
        canvas.drawLine(x - 4f, y - 9f, x - 4f, y - 5f, paint)
        canvas.drawLine(x + 4f, y - 9f, x + 4f, y - 5f, paint)
    }

    private fun drawPlus(canvas: Canvas, x: Float, y: Float) {
        stroke(Color.parseColor(tokens.Ink), 1.8f)
        canvas.drawLine(x - 7f, y, x + 7f, y, paint)
        canvas.drawLine(x, y - 7f, x, y + 7f, paint)
    }

    private fun fill(color: Int, alpha: Float = 1f) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = (Color.alpha(color) * clamp01(alpha.toDouble())).toInt()
    }

    private fun stroke(color: Int, width: Float, alpha: Float = 1f) {
        fill(color, alpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
    }

    private fun text(
        size: Float,
        typeface: Typeface,
        color: Int,
        alpha: Float = 1f,
        align: Paint.Align = Paint.Align.LEFT,
    ) {
        fill(color, alpha)
        paint.textSize = size
        paint.typeface = typeface
        paint.textAlign = align
    }

    private fun Paint.letterSpacingCompat(spacing: Float) {
        letterSpacing = spacing
    }

    private fun clamp01(value: Double): Double = max(0.0, min(1.0, value))

    private fun easeOut(progress: Float): Float {
        val clamped = max(0f, min(1f, progress))
        return 1f - (1f - clamped).pow(3)
    }
}

@RequiresApi(33)
private class HabitBubbleClusterGlass33(frosted: Bitmap) {
    private val shader = RuntimeShader(
        """
        uniform shader bg;
        uniform float4 uRect;
        uniform float uRadius;
        uniform float4 uTint;
        uniform float uThick;
        uniform float3 uGlow;
        uniform float2 uPageOffset;

        float sdBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 xy) {
            float2 b = uRect.zw * 0.5;
            float2 p = xy - (uRect.xy + b);
            float r = min(uRadius, min(b.x, b.y));
            float d = sdBox(p, b, r);
            float coverage = 1.0 - smoothstep(-0.5, 0.5, d);
            if (coverage <= 0.0) return half4(0.0);

            float e = 0.5;
            float2 n = float2(
                sdBox(p + float2(e, 0.0), b, r) - sdBox(p - float2(e, 0.0), b, r),
                sdBox(p + float2(0.0, e), b, r) - sdBox(p - float2(0.0, e), b, r)
            );
            n /= max(length(n), 0.0001);
            float bevel = min(12.0, min(b.x, b.y));
            float inside = clamp(-d / bevel, 0.0, 1.0);
            float lens = (1.0 - inside) * (1.0 - inside);
            float bend = 7.0 + 7.0 * uThick;
            float2 sampleAt = xy - n * lens * bend - p * 0.025 + uPageOffset;
            float spread = lens * (1.2 + uThick);
            float3 color = float3(
                bg.eval(sampleAt - n * spread).r,
                bg.eval(sampleAt).g,
                bg.eval(sampleAt + n * spread).b
            );
            color = color * 0.8 + 0.03;
            color = mix(color, uTint.rgb, uTint.a * 0.55) + uTint.rgb * uTint.a * 0.32;
            float rim = 1.0 - smoothstep(0.0, 1.2, -d);
            float band = (1.0 - smoothstep(0.0, 7.0, -d)) * lens;
            float up = pow(max(dot(n, float2(-0.34, -0.94)), 0.0), 1.4);
            float down = pow(max(dot(n, float2(0.3, 0.95)), 0.0), 2.0);
            float light = 0.12 + 0.6 * up + 0.24 * down;
            color += rim * light * (0.9 + 0.3 * uThick);
            color += band * light * 0.16 * (1.0 + uThick);
            color += mix(uTint.rgb, float3(1.0), 0.5) * rim * uTint.a * 0.6;
            color += (1.0 - smoothstep(-b.y, -b.y + min(28.0, b.y), p.y)) * 0.03;
            color *= 1.0 - 0.1 * smoothstep(1.6, 5.0, -d) * (1.0 - smoothstep(5.0, 10.0, -d));
            float2 finger = xy - uGlow.xy;
            color += uGlow.z * 0.16 * exp(-dot(finger, finger) / (2.0 * 46.0 * 46.0));
            return half4(half3(color * coverage), half(coverage));
        }
        """.trimIndent(),
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val background = BitmapShader(frosted, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        background.setLocalMatrix(Matrix())
        shader.setInputShader("bg", background)
        paint.shader = shader
    }

    fun draw(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        tint: FloatArray,
        tintStrength: Float,
        thickness: Float,
        glowX: Float,
        glowY: Float,
        glow: Float,
        pageOffsetX: Float,
        pageOffsetY: Float,
    ) {
        shader.setFloatUniform("uRect", x, y, width, height)
        shader.setFloatUniform("uRadius", radius)
        shader.setFloatUniform("uTint", tint[0], tint[1], tint[2], tintStrength)
        shader.setFloatUniform("uThick", thickness)
        shader.setFloatUniform("uGlow", glowX, glowY, glow)
        shader.setFloatUniform("uPageOffset", pageOffsetX, pageOffsetY)
        canvas.drawRect(x - 1f, y - 1f, x + width + 1f, y + height + 1f, paint)
    }
}
