package com.ngocdevv.nativecook.blocks.progress.geodefocusreward

import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class GeodeFocusRewardPainter(resources: GeodeFocusRewardResources) {
    private val art = GeodeFocusRewardArt(resources)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val layer = Paint()
    private val path = Path()
    private val rect = RectF()
    private val card = RectF(16f, 136f, 377f, 744f)
    private val cardPath = Path().apply { addRoundRect(card, 27f, 27f, Path.Direction.CW) }
    private val cardGradient = LinearGradient(196.5f, 136f, 196.5f, 744f, intArrayOf(tint(0x08090A), tint(0x232421), tint(0x080909)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    private val buttonGradient = LinearGradient(32f, 648f, 361f, 648f, intArrayOf(tint(0xD9A849), tint(0xB6CC55), tint(0x74DD4C)), null, Shader.TileMode.CLAMP)
    private val fonts = listOf(400, 500, 600).associateWith { weight ->
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
        else Typeface.create(if (weight >= 500) "sans-serif-medium" else "sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
    private val trackingWidths = HashMap<String, FloatArray>()
    fun draw(c: Canvas, frame: GeodeFocusRewardFrame, driver: GeodeFocusRewardDriver, now: Double, reduced: Boolean, height: Float, bottom: Float) {
        c.drawColor(tint(0x07090A))
        label(c, "Stillvale", 24f, 90f, 23f, 500, alpha = .1, align = Paint.Align.LEFT)
        label(c, "Today", 24f, 220f, 19f, 500, alpha = .1, align = Paint.Align.LEFT)
        round(c, 24f, 287f, 345f, 188f, 20f, tint(0x59615D, .035))
        val bars = floatArrayOf(42f, 66f, 53f, 89f, 73f, 104f, 118f)
        bars.forEachIndexed { i, h -> round(c, 97.5f + 30 * i, 447.5f - h, 18f, h, 9f, tint(0xB6CC55, .1)) }
        repeat(3) { i ->
            val x = floatArrayOf(74f, 196.5f, 319f)[i]; val y = 794 - bottom
            icon(c, i, x, y - 9, .1)
            label(c, arrayOf("Today", "Stones", "You")[i], x, y + 14f, 10f, 500, alpha = .1)
        }
        val opacity = driver.rewardOpacity(now)
        if (opacity > 0) {
            val save = if (opacity < .99999) { layer.alpha = byte(opacity); c.saveLayer(0f, 0f, 393f, height, layer) } else c.save()
            paint.shader = cardGradient; paint.alpha = 255; paint.style = Paint.Style.FILL; c.drawRoundRect(card, 27f, 27f, paint)
            paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = .7f; paint.color = tint(0xFFFFFF, .07); c.drawRoundRect(card, 27f, 27f, paint); paint.style = Paint.Style.FILL
            c.save(); c.clipPath(cardPath)
            val quiet = driver.quietOpacity(now)
            val heroLayer = if (quiet < .99999) { layer.alpha = byte(quiet); c.saveLayer(card, layer) } else c.save()
            art.draw(c, frame.hero); c.restoreToCount(heroLayer); c.restore()
            val a = frame.copy.restAlpha
            label(c, "Stone earned", 196.5f, 216f, 20f, 600, alpha = a)
            label(c, "Hold to reveal your stone", 196.5f, 663f, 10f, 600, 0x999D96, a, tracking = .4f)
            val copy = frame.copy
            label(c, "New stone", 196.5f, (182.5 + copy.resultY[0]).toFloat(), 12f, 400, 0x999D96, copy.resultAlpha[0])
            label(c, "Mosslight", 196.5f, (212 + copy.resultY[0]).toFloat(), 22f, 600, alpha = copy.resultAlpha[0])
            label(c, "Seven focused sessions.", 196.5f, (243.5 + copy.resultY[1]).toFloat(), 12.5f, 400, 0x999D96, copy.resultAlpha[1])
            label(c, "A little more room to think.", 196.5f, (260.5 + copy.resultY[1]).toFloat(), 12.5f, 400, 0x999D96, copy.resultAlpha[1])
            paint.shader = null; paint.color = tint(0xB6CC55, .82 * copy.resultAlpha[2]); repeat(7) { i -> c.drawCircle(157.5f + i * 13, (285 + copy.resultY[2]).toFloat(), 2.5f, paint) }
            val dy = copy.resultY[3].toFloat(); val ca = copy.resultAlpha[3]
            paint.shader = buttonGradient; paint.alpha = byte(ca); rect.set(32f, 626 + dy, 361f, 670 + dy); c.drawRoundRect(rect, 22f, 22f, paint); paint.shader = null; paint.alpha = 255
            label(c, if (driver.kept) "Added to collection" else "Keep stone", if (driver.kept) 205f else 196.5f, 648 + dy, 14f, 500, 0x18250D, ca)
            if (driver.kept) { text.textSize = 14f; text.typeface = fonts.getValue(500); val x = 205 - text.measureText("Added to collection") / 2 - 19
                stroke(c, 0x18250D, ca, 1.5f) { moveTo(x, 648 + dy); lineTo(x + 4, 652 + dy); lineTo(x + 11, 643 + dy) } }
            label(c, "Replay", 196.5f, 701 + dy, 12.5f, 400, 0x999D96, ca)
            stroke(c, 0x999D96, 1.0, 1.3f) { moveTo(35.5f, 155.5f); lineTo(44.5f, 164.5f); moveTo(44.5f, 155.5f); lineTo(35.5f, 164.5f) }
            label(c, "Skip", 340f, 160f, 12.5f, 500, 0x999D96)
            c.restoreToCount(save)
        }
        if (driver.collapsed) {
            round(c, 108.5f, 416f, 176f, 48f, 24f, tint(0x232923))
            icon(c, 1, 133f, 440f, 1.0)
            label(c, "View stone", 208f, 440f, 15f, 500)
        }
    }
    private fun label(c: Canvas, value: String, x: Float, y: Float, size: Float, weight: Int, rgb: Int = 0xECEDE5,
        alpha: Double = 1.0, align: Paint.Align = Paint.Align.CENTER, tracking: Float = 0f) {
        if (alpha <= 0) return
        text.color = tint(rgb, alpha); text.textSize = size; text.typeface = fonts.getValue(weight); text.textAlign = align
        val f = text.fontMetrics; val baseline = y - (f.ascent + f.descent) / 2
        if (tracking == 0f) c.drawText(value, x, baseline, text) else {
            val widths = trackingWidths.getOrPut(value) { FloatArray(value.length).also { text.getTextWidths(value, it) } }; val width = widths.sum() + (value.length - 1) * tracking
            var at = x - when (align) { Paint.Align.CENTER -> width / 2; Paint.Align.RIGHT -> width; else -> 0f }; text.textAlign = Paint.Align.LEFT
            value.indices.forEach { i -> c.drawText(value, i, i + 1, at, baseline, text); at += widths[i] + tracking }
        }
    }
    private fun round(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) { paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color; rect.set(x, y, x + w, y + h); c.drawRoundRect(rect, r, r, paint) }
    private fun stroke(c: Canvas, rgb: Int, alpha: Double, width: Float, block: Path.() -> Unit) { paint.shader = null; paint.style = Paint.Style.STROKE; paint.color = tint(rgb, alpha); paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; path.rewind(); path.block(); c.drawPath(path, paint); paint.style = Paint.Style.FILL }
    private fun icon(c: Canvas, i: Int, x: Float, y: Float, a: Double) {
        stroke(c, 0xECEDE5, a, 1.2f) {
            when (i) {
                0 -> { addCircle(x, y, 4.5f, Path.Direction.CW); repeat(8) { k -> val t = k * PI / 4; moveTo(x + cos(t).toFloat() * 7, y + sin(t).toFloat() * 7); lineTo(x + cos(t).toFloat() * 9, y + sin(t).toFloat() * 9) } }
                1 -> { repeat(6) { k -> val t = k * PI / 3 - PI / 2; val px = x + cos(t).toFloat() * 8; val py = y + sin(t).toFloat() * 8; if (k == 0) moveTo(px, py) else lineTo(px, py) }; close() }
                else -> { addCircle(x, y, 9f, Path.Direction.CW); addCircle(x, y - 3, 2.7f, Path.Direction.CW); moveTo(x - 5, y + 6); quadTo(x, y, x + 5, y + 6) }
            }
        }
    }
    private fun byte(a: Double) = (a * 255).roundToInt().coerceIn(0, 255)
    private fun tint(rgb: Int, a: Double = 1.0) = Color.argb(byte(a), rgb shr 16 and 255, rgb shr 8 and 255, rgb and 255)
}
