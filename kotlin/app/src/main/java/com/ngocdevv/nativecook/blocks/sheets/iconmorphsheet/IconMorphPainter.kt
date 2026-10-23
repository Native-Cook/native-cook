package com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet

import android.graphics.*
import android.os.Build
import kotlin.math.*

/** Draw-phase renderer. Geometry is in density-independent points, as in the original canvas. */
internal class IconMorphPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val path = Path()
    private val shader = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(ICON_MORPH_AGSL) else null
    private val white = Color.rgb(244, 245, 248)
    private val muted = Color.rgb(154, 156, 166)
    private var opacity = 1.0

    companion object {
        val titles = arrayOf("Send with zero fees", "We keep every send safe", "Say it with an envelope", "Link your bank and go")
        val subtitles = arrayOf("Friends or your own bank, always free.", "We check the recipient for reported fraud\nbefore your money moves.",
            "Wrap a gift or a thank-you in a note.", "Running low? We top up from your account\nthe moment you send.")
        val images = arrayOf("A zero with gold coins", "A siren with a shield", "An open envelope with a note", "A bank passbook with a check")
        val labels = arrayOf("Send $24.00", "Next", "Got it")
    }
    private fun color(hex: String) = Color.parseColor(hex)
    private fun fill(c: Int, alpha: Double = 1.0) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = c
        paint.alpha = (Color.alpha(c) * alpha * opacity).roundToInt().coerceIn(0, 255)
    }
    private fun gradient(top: Double, bottom: Double, colors: IntArray) {
        fill(Color.WHITE)
        paint.shader = LinearGradient(0f, top.toFloat(), 0f, bottom.toFloat(), colors, null, Shader.TileMode.CLAMP)
    }
    private fun rect(c: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double) {
        c.drawRoundRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), r.toFloat(), r.toFloat(), paint)
    }
    private fun text(c: Canvas, value: String, x: Double, top: Double, size: Double, line: Double,
                     bold: Boolean = false, tint: Int = white, alpha: Double = 1.0, centered: Boolean = true) {
        if (alpha <= 0) return
        textPaint.typeface = if (bold) IconMorphAssets.bold else IconMorphAssets.regular
        textPaint.textSize = size.toFloat()
        textPaint.color = tint
        textPaint.alpha = (255 * alpha * opacity).roundToInt().coerceIn(0, 255)
        textPaint.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
        val metrics = textPaint.fontMetrics
        val baseline = top + (line - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent
        c.drawText(value, x.toFloat(), baseline.toFloat(), textPaint)
    }
    private fun line(c: Canvas, x1: Double, y1: Double, x2: Double, y2: Double, width: Double, tint: Int) {
        fill(tint)
        paint.strokeWidth = width.toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), paint)
    }
    private fun coin(c: Canvas, x: Double, y: Double, size: Double) {
        gradient(y, y + size, intArrayOf(color("#FFF17A"), color("#FFE23A"), color("#CBA614")))
        rect(c, x, y, size, size, size / 2)
        fill(color("#FFF5B8"))
        rect(c, x + size / 4, y + size / 12, size / 2, size / 12, 1.0)
    }
    private fun puck(c: Canvas, cx: Double, cy: Double, kind: Int, press: Double) {
        c.save()
        c.scale((1 - 0.08 * press).toFloat(), (1 - 0.08 * press).toFloat(), cx.toFloat(), cy.toFloat())
        val size = if (kind == 0) 32.0 else 36.0
        path.reset()
        path.addCircle(cx.toFloat(), cy.toFloat(), (size / 2).toFloat(), Path.Direction.CW)
        c.clipPath(path)
        gradient(cy - size / 2, cy + size / 2, intArrayOf(color(if (kind == 0) "#23242C" else "#25262E"), color(if (kind == 0) "#17181D" else "#1A1B21")))
        rect(c, cx - size / 2, cy - size / 2, size, size, size / 2)
        fill(Color.WHITE, 0.1)
        rect(c, cx - (size - 12) / 2, cy - size / 2 + 1, size - 12, 1.0, 1.0)
        val ink = color("#C9CBD3")
        when (kind) {
            0 -> {
                line(c, cx - 4.6, cy - 4.6, cx + 4.6, cy + 4.6, 2.25, ink)
                line(c, cx - 4.6, cy + 4.6, cx + 4.6, cy - 4.6, 2.25, ink)
            }
            1 -> {
                line(c, cx - 7, cy, cx + 7, cy, 2.25, ink)
                line(c, cx - 7, cy, cx - 1, cy - 6, 2.25, ink)
                line(c, cx - 7, cy, cx - 1, cy + 6, 2.25, ink)
            }
            else -> {
                fill(ink)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.25f
                c.drawCircle(cx.toFloat(), cy.toFloat(), 7.4f, paint)
                line(c, cx, cy, cx, cy + 4, 2.25, ink)
                fill(ink)
                c.drawCircle(cx.toFloat(), (cy - 4).toFloat(), 1.125f, paint)
            }
        }
        c.restore()
    }
    private fun page(c: Canvas, l: IconMorphLayout, f: IconMorphFrame) {
        opacity = 1 - f.open * 0.3
        puck(c, 34.0, l.headerY, 1, if (f.puck == 1) f.puckPress else 0.0)
        puck(c, l.width - 34, l.headerY, 2, if (f.puck == 2) f.puckPress else 0.0)
        coin(c, l.width / 2 - 26, l.headerY - 6, 12.0)
        text(c, "noku", l.width / 2 - 8, l.headerY - 12, 17.0, 24.0, true, centered = false)
        val av = l.avatarSize
        val ax = (l.width - av) / 2
        val ay = l.avatarY - av / 2
        c.save()
        path.reset()
        path.addRoundRect(ax.toFloat(), ay.toFloat(), (ax + av).toFloat(), (ay + av).toFloat(), if (l.compact) 12f else 14f, if (l.compact) 12f else 14f, Path.Direction.CW)
        c.clipPath(path)
        fill(Color.WHITE)
        paint.isFilterBitmap = true
        c.drawBitmap(IconMorphAssets.avatar, null, RectF(ax.toFloat(), ay.toFloat(), (ax + av).toFloat(), (ay + av).toFloat()), paint)
        c.restore()
        if (!l.compact) {
            text(c, "Mina P.", l.width / 2 - 8, l.nameY - 10, 13.0, 20.0, tint = color("#8E909A"))
            val x = l.width / 2 + 21
            val y = l.nameY - 6
            fill(color("#3D63F5"))
            path.reset()
            path.moveTo((x + 6).toFloat(), y.toFloat())
            path.lineTo((x + 11).toFloat(), (y + 2).toFloat())
            path.lineTo((x + 11).toFloat(), (y + 6).toFloat())
            path.quadTo((x + 11).toFloat(), (y + 9).toFloat(), (x + 6).toFloat(), (y + 12).toFloat())
            path.quadTo((x + 1).toFloat(), (y + 9).toFloat(), (x + 1).toFloat(), (y + 6).toFloat())
            path.lineTo((x + 1).toFloat(), (y + 2).toFloat())
            path.close()
            c.drawPath(path, paint)
            line(c, x + 3.7, y + 5.8, x + 5.3, y + 7.4, 1.5, Color.WHITE)
            line(c, x + 5.3, y + 7.4, x + 8.3, y + 4.4, 1.5, Color.WHITE)
        }
        text(c, "To Mina Park", l.width / 2, l.titleY - l.titleLine / 2, l.titleSize, l.titleLine, true)
        text(c, "$24.00", l.width / 2, l.titleY + l.titleLine / 2, l.titleSize, l.titleLine, true)
        if (!l.compact) text(c, "Send it now?", l.width / 2, l.titleY + l.titleLine * 1.5, l.titleSize, l.titleLine, true, color("#6E707A"))
        if (l.balanceVisible) {
            fill(color("#16171C"))
            rect(c, 16.0, l.balanceTop, l.width - 32, 48.0, 16.0)
            fill(Color.WHITE, 0.05)
            rect(c, 32.0, l.balanceTop, l.width - 64, 1.0, 0.0)
            coin(c, 28.0, l.balanceTop + 10, 28.0)
            text(c, "noku balance", 68.0, l.balanceTop + 4, 15.0, 20.0, true, centered = false)
            text(c, "$182.40 available", 68.0, l.balanceTop + 24, 13.0, 20.0, tint = color("#8E909A"), centered = false)
            line(c, l.width - 41, l.balanceTop + 22, l.width - 36, l.balanceTop + 27, 2.25, color("#6E707A"))
            line(c, l.width - 36, l.balanceTop + 27, l.width - 31, l.balanceTop + 22, 2.25, color("#6E707A"))
        }
        opacity = 1.0
    }

    fun draw(c: Canvas, l: IconMorphLayout, f: IconMorphFrame, times: DoubleArray, density: Float, reduced: Boolean, autoPlay: Boolean) {
        c.drawColor(color("#0E0E12"))
        c.save()
        c.scale(density, density)
        page(c, l, f)
        val travel = l.travel(f, reduced)
        val top = l.height - l.sheetHeight(f.position) + travel
        c.save()
        if (reduced) c.saveLayerAlpha(0f, 0f, l.width.toFloat(), l.height.toFloat(), (255 * f.open).roundToInt().coerceIn(0, 255))
        path.reset()
        path.addRoundRect(0f, top.toFloat(), l.width.toFloat(), (l.height + travel + 28).toFloat(), 28f, 28f, Path.Direction.CW)
        fill(Color.BLACK, 0.25)
        paint.setShadowLayer(24f, 0f, -8f, Color.argb(64, 0, 0, 0))
        c.drawPath(path, paint)
        c.clipPath(path)
        gradient(top, top + 240, intArrayOf(color("#2A2C33"), color("#24262C")))
        c.drawPaint(paint)
        fill(Color.WHITE, 0.06)
        rect(c, 28.0, top, l.width - 56, 1.0, 0.0)
        c.save()
        c.translate(0f, travel.toFloat())
        for (i in 0..3) {
            val distance = i - f.position
            val alpha = f.contents * (1 - min(1.0, abs(distance) * (if (reduced) 2.2 else 1.4)))
            if (alpha <= 0) continue
            val x = l.width / 2 + (if (reduced) 0.0 else distance * l.width)
            val lines = subtitles[i].split('\n')
            val y = l.copyBottom - 32 - 20 * lines.size
            text(c, titles[i], x, y, 20.0, 24.0, true, alpha = alpha)
            lines.forEachIndexed { index, value -> text(c, value, x, y + 32 + 20 * index, 14.0, 20.0, tint = muted, alpha = alpha) }
        }
        c.save()
        c.translate((if (reduced) 0.0 else -f.edge).toFloat(), (l.stageTop + (if (reduced) 0.0 else 24 * (1 - f.stage))).toFloat())
        val grow = if (reduced) 1.0 else 0.92 + 0.08 * f.stage
        c.scale(grow.toFloat(), grow.toFloat(), (l.width / 2).toFloat(), (l.stageHeight / 2).toFloat())
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            shader.setFloatUniform("uCentre", (l.width / 2).toFloat(), (l.stageHeight / 2).toFloat())
            shader.setFloatUniform("uUnit", l.unit.toFloat())
            shader.setFloatUniform("uPixelRatio", density)
            shader.setFloatUniform("uCamera", 9f)
            IconMorphPose.frame(f.position, times, f.bounce, f.lean, f.press, reduced).forEach { (name, values) ->
                shader.setFloatUniform(name, FloatArray(values.size) { values[it].toFloat() })
            }
            fill(Color.WHITE, f.contents)
            paint.shader = shader
            c.drawRect(0f, 0f, l.width.toFloat(), l.stageHeight.toFloat(), paint)
        } else {
            IconMorphFallback.draw(c, l.width / 2, l.stageHeight / 2, l.unit, round(f.position).toInt(), f.contents)
        }
        c.restore()
        var dotX = (l.width - 54) / 2
        for (i in 0..3) {
            val weight = max(0.0, 1 - abs(f.position - i))
            val width = 6 + 12 * weight
            val tint = Color.rgb((90 + (244 - 90) * weight).roundToInt(), (93 + (245 - 93) * weight).roundToInt(), (102 + (248 - 102) * weight).roundToInt())
            fill(tint, f.contents)
            rect(c, dotX, l.dotsTop, width, 6.0, 3.0)
            dotX += width + 6
        }
        c.restore()
        opacity = f.contents
        puck(c, l.width - 32, top + 28, 0, if (f.puck == 0) f.puckPress else 0.0)
        opacity = 1.0
        // Fog is pinned to the button, so the moving contents sink into it.
        gradient(l.buttonTop - 20, l.buttonTop - 4, intArrayOf(color("#0024262C"), color("#FF24262C")))
        c.drawRect(0f, (l.buttonTop - 20).toFloat(), l.width.toFloat(), l.height.toFloat(), paint)
        if (reduced) c.restore()
        c.restore()
        button(c, l, f, reduced)
        if (autoPlay && !reduced && f.markerOpacity > 0) {
            val x = if (f.markerKind == 2) l.width / 2 + f.markerX - 201 else l.width / 2
            val y = if (f.markerKind == 0) l.buttonTop + 28 else if (f.markerKind == 2) l.stageTop + l.stageHeight - (744 - f.markerY) else l.stageTop + l.stageHeight / 2
            val r = 18 * (1 - 0.14 * f.markerPress)
            fill(Color.WHITE, f.markerOpacity * 0.22)
            c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), paint)
            fill(Color.WHITE, f.markerOpacity * 0.35)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            c.drawCircle(x.toFloat(), y.toFloat(), (r - 0.5).toFloat(), paint)
        }
        c.restore()
    }
    private fun button(c: Canvas, l: IconMorphLayout, f: IconMorphFrame, reduced: Boolean) {
        c.save()
        val scale = (1 - 0.03 * f.buttonPress).toFloat()
        c.scale(scale, scale, (l.width / 2).toFloat(), (l.buttonTop + 28).toFloat())
        fill(color("#FFE23A"), 0.14)
        paint.setShadowLayer(20f, 0f, 6f, Color.argb(36, 255, 226, 58))
        rect(c, 16.0, l.buttonTop, l.width - 32, 56.0, 28.0)
        path.reset()
        path.addRoundRect(16f, l.buttonTop.toFloat(), (l.width - 16).toFloat(), (l.buttonTop + 56).toFloat(), 28f, 28f, Path.Direction.CW)
        c.clipPath(path)
        gradient(l.buttonTop, l.buttonTop + 56, intArrayOf(color("#FFEC4F"), color("#FFDD1F")))
        rect(c, 16.0, l.buttonTop, l.width - 32, 56.0, 28.0)
        fill(Color.WHITE, 0.55)
        rect(c, 36.0, l.buttonTop, l.width - 72, 1.0, 0.0)
        fill(color("#806000"), 0.26)
        rect(c, 36.0, l.buttonTop + 55, l.width - 72, 1.0, 0.0)
        fill(color("#4D3D00"), max(0.0, f.buttonPress) * 0.09)
        rect(c, 16.0, l.buttonTop, l.width - 32, 56.0, 28.0)
        for (i in 0..2) {
            val state = IconMorphTimeline.buttonLabel(f, i)
            text(c, labels[i], l.width / 2, l.buttonTop + 16 + (if (reduced) 0.0 else state[1]), 17.0, 24.0, true, color("#17181D"), state[0])
        }
        c.restore()
    }
}
