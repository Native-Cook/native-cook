package com.ngocdevv.nativecook.blocks.cards.frostheartrewards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.Scene
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.clamp
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.ease
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.out
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.seg
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsTheme as T
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Inter in four weights, measured with fractional advances like the reference's getGlyphWidths. */
internal class FrostHeartRewardsType(context: Context) {
    private val faces = listOf("Regular", "Medium", "SemiBold", "Bold").map {
        Typeface.createFromAsset(context.assets, "frostheartrewards/frostheartrewards_Inter-$it.ttf")
    }
    private val paints = HashMap<T.Font, Paint>()
    private val probe = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = Rect()

    fun paint(font: T.Font): Paint = paints.getOrPut(font) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = faces[font.weight]
            textSize = font.size.toFloat()
        }
    }
    fun face(weight: Int): Typeface = faces[weight]

    /** Left ink bearing, measured at 16× so integer bounds keep sub-point precision. */
    fun bearing(text: String, font: T.Font): Double {
        probe.typeface = faces[font.weight]
        probe.textSize = (font.size * 16).toFloat()
        probe.getTextBounds(text, 0, text.length, bounds)
        return bounds.left / 16.0
    }
    fun advances(text: String, font: T.Font): FloatArray = FloatArray(text.length).also { paint(font).getTextWidths(text, it) }
    fun width(text: String, font: T.Font) = advances(text, font).sum() + max(0, text.length - 1) * font.tracking

    /** Per-glyph drawing with tracking; [center] centres the run in a width, else the ink starts at x. */
    fun draw(c: Canvas, text: String, x: Double, y: Double, font: T.Font, color: Int, center: Double? = null, alpha: Double = 1.0, inkStart: Boolean = true) {
        if (alpha <= 0.001) return
        val p = paint(font)
        p.shader = null
        p.color = color
        p.alpha = (((color ushr 24) and 0xff) * alpha.coerceIn(0.0, 1.0)).roundToInt()
        val widths = advances(text, font)
        var xx = when {
            center != null -> x + (center - width(text, font)) / 2
            inkStart -> x - bearing(text, font)
            else -> x
        }
        for (i in text.indices) {
            c.drawText(text, i, i + 1, xx.toFloat(), y.toFloat(), p)
            xx += widths[i] + font.tracking
        }
    }
}

/** The fixed page (fhr-page.ts) rendered once to a bitmap, and the counters and chips drawn per frame. */
internal class FrostHeartRewardsPage(private val type: FrostHeartRewardsType) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val paths = HashMap<String, Path>()
    private val rect = RectF()
    private val measure = PathMeasure()
    private val trimmed = Path()

    private fun path(svg: String) = paths.getOrPut(svg) { PathParser.createPathFromPathData(svg) }
    private fun rr(c: Canvas, b: T.Box, r: Double, color: Int, alpha: Double = 1.0) {
        fill.shader = null; fill.color = color; fill.alpha = (255 * alpha.coerceIn(0.0, 1.0)).roundToInt()
        rect.set(b.x.toFloat(), b.y.toFloat(), (b.x + b.w).toFloat(), (b.y + b.h).toFloat())
        c.drawRoundRect(rect, r.toFloat(), r.toFloat(), fill)
    }
    private fun stroke(c: Canvas, svg: String, color: Int, w: Double) {
        line.shader = null; line.color = color; line.strokeWidth = w.toFloat()
        c.drawPath(path(svg), line)
    }
    private fun pill(c: Canvas, b: T.Pill, color: Int, font: T.Font, ink: Int = T.WHITE) {
        c.save()
        if (b.rotation != 0.0) c.rotate(b.rotation.toFloat(), (b.x + b.w / 2).toFloat(), (b.y + b.h / 2).toFloat())
        rr(c, T.Box(b.x, b.y, b.w, b.h), b.r, color)
        type.draw(c, b.text, b.x, b.baseline, font, ink, center = b.w)
        c.restore()
    }
    private fun label(c: Canvas, l: T.Label, font: T.Font, color: Int) = type.draw(c, l.text, l.x, l.baseline, font, color)

    /** Navigation, points labels, daily heading, filters, task card and tabs: everything that never moves. */
    fun bake(scale: Double): Bitmap {
        val w = (402 * scale).roundToInt().coerceAtLeast(1)
        val h = (874 * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(scale.toFloat(), scale.toFloat())
        drawBase(c)
        drawPoints(c)
        return bitmap
    }

    private fun drawPoints(c: Canvas) {
        T.POINT_BRANDS.forEachIndexed { i, b ->
            pill(c, b, if (i == 0) T.PINK else T.VIOLET, T.PILL.copy(size = if (i == 0) 10.8 else 12.8))
        }
        label(c, T.NEXT_TITLE_LABEL, T.NEXT_TITLE, T.TEXT)
        stroke(c, "M347 372 L350 375.5 L347 379", T.BODY, 1.6)
        label(c, T.AMOUNT_LABEL, T.AMOUNT, T.TEXT)
        label(c, T.OF150, T.FRACTION, T.MUTED)
    }

    private fun drawBase(c: Canvas) {
        stroke(c, T.BACK, T.PINK, 1.8); stroke(c, T.CHAT, T.TEXT, 1.6)
        label(c, T.TITLE, T.NAV, T.TEXT); pill(c, T.BRAND, T.PINK, T.NAV_BRAND)
        label(c, T.DAILY_HEADING, T.HEADING, T.TEXT); stroke(c, "M176 480 L182 486 L176 492", T.TEXT, 1.8)
        rr(c, T.FILTER_PANEL, T.FILTER_PANEL_RADIUS, T.PANEL)
        T.FILTER_PILLS.forEach { (b, selected) -> pill(c, b, if (selected) T.WHITE else T.CHIP, T.FILTER, if (selected) T.BLACK else T.BODY) }
        // The last pill fades into the panel at the right edge.
        fill.color = T.PANEL
        fill.shader = LinearGradient(354f, 0f, 394f, 0f, intArrayOf(0x001A1A1C, T.PANEL), null, Shader.TileMode.CLAMP)
        c.drawRect(354f, 590f, 399f, 636f, fill)
        fill.shader = null
        rr(c, T.TASK_CARD, T.TASK_RADIUS, T.CARD); rr(c, T.PEEK, T.PEEK_RADIUS, T.CARD)
        // The task's glass medallion is vector-drawn.
        val b = T.TASK_ICON
        fill.color = T.WHITE
        fill.shader = LinearGradient(b.x.toFloat(), b.y.toFloat(), (b.x + b.w).toFloat(), (b.y + b.h).toFloat(),
            intArrayOf(0xFFDFB5FF.toInt(), 0xFF9A42E8.toInt(), 0xFFF448F2.toInt(), 0xFFDDCAFF.toInt()), floatArrayOf(0f, 0.28f, 0.65f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(b.cx.toFloat(), b.cy.toFloat(), (b.w / 2).toFloat(), fill)
        fill.shader = null
        line.color = 0xFFF0DDFF.toInt(); line.strokeWidth = 1.3f
        c.drawCircle(b.cx.toFloat(), b.cy.toFloat(), (b.w / 2 - 1).toFloat(), line)
        type.draw(c, "lumi", b.x, b.y + 32, T.Font(17.5, T.BOLD, -1.1), T.WHITE, center = b.w)
        T.TASK_TITLES.forEach { label(c, it, T.TASK_TITLE, T.TEXT) }
        label(c, T.TASK_LINE1, T.TASK_BODY, T.BODY); label(c, T.TASK_LINE2, T.TASK_BODY, T.BODY)
        pill(c, T.TASK_REWARD_PILL, 0xFF49494B.toInt(), T.TASK_REWARD); pill(c, T.TASK_REWARD_UP, T.ORANGE, T.PILL)
        label(c, T.TASK_TIMER_LABEL, T.TASK_TIMER, T.TIMER); stroke(c, T.CLOCK, T.TIMER, 1.3)
        // The peek only needs its top arc: the tab bar covers the rest.
        fill.color = T.VIOLET; c.drawCircle(52f, 815f, 23f, fill)
        rr(c, T.TAB_BAR, 0.0, T.PANEL)
        stroke(c, T.HOME, T.ACTIVE_TAB, 1.9); stroke(c, T.LIST, T.TAB, 2.2); stroke(c, T.WALLET, T.TAB, 2.0)
        stroke(c, T.CART, T.TAB, 2.0); stroke(c, T.PROFILE, T.TAB, 1.8)
        pill(c, T.CART_BADGE_PILL, T.BADGE_RED, T.CART_BADGE)
    }

    // ----- Live state: balance, the next-level line and the daily chips. -----

    private fun balanceText(v: Double): String {
        val r = v.roundToInt()
        return "${r / 1000} ${(r % 1000).toString().padStart(3, '0')}"
    }

    /** Odometer digits: each column rolls 53 pt per digit and carries only through nines. */
    private fun balance(c: Canvas, s: Scene) {
        val f = T.BALANCE
        val widths = type.advances("1650", f)
        val x = doubleArrayOf(17.67, 17.67 + widths[0] + 10.5, 17.67 + widths[0] + widths[1] + 12, 17.67 + widths[0] + widths[1] + widths[2] + 13.5)
        val value = s.balance
        c.save()
        c.clipRect(12f, 396f, 172f, 449f)
        for (k in 0 until 4) {
            val power = 10.0.pow(3 - k)
            val integral = floor(value)
            val frac = value - integral
            val carry = if (k == 3) frac else if (integral % power == power - 1) frac else 0.0
            val shift = (floor(value / power) % 10 + carry) * -53
            val blur = abs(sin((value % 1) * PI)) * 0.75
            c.save()
            c.clipRect((x[k] - 1).toFloat(), 397f, (x[k] + 33).toFloat(), 448f)
            c.translate(0f, shift.toFloat())
            val first = floor(-shift / 53).toInt().coerceIn(0, 10)
            for (i in (first - 1).coerceAtLeast(0)..(first + 1).coerceAtMost(10)) {
                val d = (i % 10).toString()
                val px = x[k] - type.bearing(d, f)
                type.draw(c, d, px, 441.33 + i * 53, f, T.TEXT, inkStart = false)
                // A whisper of vertical motion blur while a column rolls.
                if (blur > 0.05) {
                    type.draw(c, d, px, 441.33 + i * 53 - blur, f, T.TEXT, alpha = 0.25, inkStart = false)
                    type.draw(c, d, px, 441.33 + i * 53 + blur, f, T.TEXT, alpha = 0.25, inkStart = false)
                }
            }
            c.restore()
        }
        c.restore()
    }

    /** Reduced Motion keeps the glyphs in place and fades the old balance into the new one. */
    private fun reducedBalance(c: Canvas, s: Scene) {
        val x = 17.67 - type.bearing("1", T.BALANCE)
        type.draw(c, balanceText(s.balanceFrom), x, 441.33, T.BALANCE, T.TEXT, alpha = 1 - s.numberFade, inkStart = false)
        type.draw(c, balanceText(s.balanceTo), x, 441.33, T.BALANCE, T.TEXT, alpha = s.numberFade, inkStart = false)
    }

    private fun chip(c: Canvas, i: Int, age: Double, reduced: Boolean) {
        val chip = T.CHIPS[i]
        val b = chip.body
        val fillAmount = if (i < 2) 1.0 else if (age < 0) 0.0 else if (reduced) 1.0 else ease(seg(age, 0.0, 0.18))
        val fillOpacity = if (i < 2) 1.0 else if (reduced) ease(seg(age, 0.0, 0.16)) else 1.0
        val scale = if (reduced || i < 2 || age < 0) 1.0 else if (age < 0.12) 1 - 0.03 * out(seg(age, 0.0, 0.12)) else 0.97 + 0.03 * out(seg(age, 0.12, 0.36))
        c.save()
        c.scale(scale.toFloat(), scale.toFloat(), b.cx.toFloat(), b.cy.toFloat())
        rr(c, b, T.CHIP_RADIUS, T.CHIP)
        if (fillAmount > 0 && fillOpacity > 0) {
            c.save()
            c.clipRect(b.x.toFloat(), b.y.toFloat(), (b.x + b.w * fillAmount).toFloat(), (b.y + b.h).toFloat())
            fill.color = T.WHITE
            fill.alpha = (255 * fillOpacity).roundToInt()
            fill.shader = LinearGradient(b.x.toFloat(), b.y.toFloat(), (b.x + b.w).toFloat(), (b.y + b.h).toFloat(), 0xFFE123D9.toInt(), 0xFFA63BEA.toInt(), Shader.TileMode.CLAMP)
            rect.set(b.x.toFloat(), b.y.toFloat(), (b.x + b.w).toFloat(), (b.y + b.h).toFloat())
            c.drawRoundRect(rect, T.CHIP_RADIUS.toFloat(), T.CHIP_RADIUS.toFloat(), fill)
            fill.shader = null
            c.restore()
        }
        val text = chip.value.toString()
        type.draw(c, text, b.cx - type.width(text, chip.font) / 2, chip.baseline, chip.font, T.TEXT, inkStart = false)
        val checkOpacity = if (i < 2) 1.0 else seg(age, 0.12, 0.18)
        if (checkOpacity > 0) {
            val checkScale = if (i < 2 || reduced) 1.0 else 0.95 + 0.05 * out(seg(age, 0.12, 0.28))
            val end = if (i < 2 || reduced) 1.0 else seg(age, 0.16, 0.32)
            c.save()
            c.translate((b.x + b.w - 8).toFloat(), (b.y + 8).toFloat())
            c.scale(checkScale.toFloat(), checkScale.toFloat())
            val a = (255 * checkOpacity).roundToInt()
            fill.shader = null
            fill.color = 0xFF161619.toInt(); fill.alpha = a; c.drawCircle(0f, 0f, 9f, fill)
            fill.color = 0xFF28272D.toInt(); fill.alpha = a; c.drawCircle(0f, 0f, 7.4f, fill)
            if (end > 0) {
                measure.setPath(path(T.CHECK), false)
                trimmed.reset()
                measure.getSegment(0f, (measure.length * end).toFloat(), trimmed, true)
                line.shader = null; line.color = T.WHITE; line.alpha = a; line.strokeWidth = 1.8f
                c.drawPath(trimmed, line)
                line.alpha = 255
            }
            c.restore()
        }
        c.restore()
    }

    /** One copy of the counters: the live scene, or the resting one while the return cross-fades. */
    fun drawState(c: Canvas, s: Scene, chipAges: DoubleArray, reduced: Boolean, alpha: Double) {
        if (alpha <= 0.001) return
        val layered = alpha < 0.999
        if (layered) c.saveLayerAlpha(0f, 380f, 402f, 568f, (255 * alpha).roundToInt())
        if (reduced) reducedBalance(c, s) else balance(c, s)
        val levelUp = ease(clamp((s.balance - 1714) / 6))
        val more = 1 - levelUp
        if (more > 0.001) {
            rr(c, T.UP, 8.5, T.ORANGE, more)
            type.draw(c, "UP", 279.5, 406.67, T.PILL, T.WHITE, alpha = more, inkStart = false)
            type.draw(c, "of 500", 310.33, 410.0, T.FRACTION, T.MUTED, alpha = more, inkStart = false)
            type.draw(c, "${s.remaining.roundToInt()} more", 192.33, 409.67, T.REMAINING, T.TEXT, alpha = more, inkStart = false)
        }
        if (levelUp > 0.001) type.draw(c, "Level up ›", 192.33, 409.67, T.REMAINING, T.PINK, alpha = levelUp, inkStart = false)
        for (i in T.CHIPS.indices) chip(c, i, chipAges[i], reduced)
        if (layered) c.restore()
    }

    companion object {
        /** RAYS_SKSL at rest (charge, burst, hue, angle and tilt all zero), evaluated once on the CPU. */
        fun rays(scale: Double): Bitmap {
            val w = (402 * scale).roundToInt()
            val h = (540 * scale).roundToInt()
            val pixels = IntArray(w * h)
            val angles = T.RAY_ANGLES.map { it * PI / 180 }
            val widths = DoubleArray(14) { 0.025 + (it % 3) * 0.009 }
            for (py in 0 until h) {
                val y = (py + 0.5) / scale
                val qy = y - 203
                for (px in 0 until w) {
                    val x = (px + 0.5) / scale
                    val qx = x - 177
                    val a = kotlin.math.atan2(qy, qx)
                    var beam = 0.0
                    for (i in 0 until 14) {
                        var d = abs(a - angles[i]); d = minOf(d, 2 * PI - d)
                        val z = d / widths[i]
                        beam += T.RAY_STRENGTHS[i] * exp(-0.5 * z * z)
                    }
                    val dist = sqrt(qx * qx + qy * qy)
                    beam *= clamp(dist / 145.0) * exp(-dist / 440.0) * clamp((510.0 - y) / 270.0)
                    val lum = beam * 0.082
                    var r = 0.07451 + lum; var g = r; var b = r
                    val gx = (x - 191) / 57; val gy = (y - 370) / 32
                    val glow = exp(-0.5 * (gx * gx + gy * gy))
                    r += 0.32 * glow; g += 0.1412 * glow; b += 0.36 * glow
                    val rx = (x - 266) / 29; val ry = (y - 345) / 43
                    val rim = exp(-0.5 * (rx * rx + ry * ry))
                    r += 0.36 * rim; g += 0.09 * rim; b += 0.31 * rim
                    val n = (FrostHeartRewardsMotion.hash(floor(x * 3) * 7.13 + floor(y * 3)) - 0.5) * 0.002
                    pixels[py * w + px] = (0xFF shl 24) or (channel(r + n) shl 16) or (channel(g + n) shl 8) or channel(b + n)
                }
            }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }
        private fun channel(v: Double) = (v.coerceIn(0.0, 1.0) * 255).roundToInt()
    }
}
