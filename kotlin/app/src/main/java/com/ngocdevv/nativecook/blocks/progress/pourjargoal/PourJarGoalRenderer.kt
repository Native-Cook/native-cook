package com.ngocdevv.nativecook.blocks.progress.pourjargoal

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.progress.pourjargoal.PourJarGoalMotion as M
import com.ngocdevv.nativecook.blocks.progress.pourjargoal.PourJarGoalTheme as Theme
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Draws the whole screen into an android Canvas already scaled to dp: the page + jar by the AGSL shader (or a gradient fallback), the rest as 2D. */
internal class PourJarGoalRenderer(assets: AssetManager, private val fontScale: Double) {
    private val instrument = Typeface.createFromAsset(assets, "pourjargoal/pourjargoal_InstrumentSerif-Regular.ttf")
    private val regular = Typeface.createFromAsset(assets, "pourjargoal/pourjargoal_Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(assets, "pourjargoal/pourjargoal_Inter-Medium.ttf")
    private val semibold = Typeface.createFromAsset(assets, "pourjargoal/pourjargoal_Inter-SemiBold.ttf")
    private val mono = Typeface.createFromAsset(assets, "pourjargoal/pourjargoal_JetBrainsMono-Medium.ttf")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
    private val rect = RectF()
    private val path = Path()
    private val uniforms = PourJarGoalUniforms()
    private var liquid: PourJarGoalLiquidShader? = null
    private var wallBitmap: Bitmap? = null
    private val fieldPx = IntArray(128)
    private val sdk33 = Build.VERSION.SDK_INT >= 33

    private fun sizeOf(base: Double, s: Double, scale: Double = fontScale) = (base * s * scale).toFloat()

    private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)

    private fun sigmaRadius(sigma: Double) = max(0.5, (sigma - 0.5) / 0.57735).toFloat()

    private fun withAlpha(color: Int, a: Double) = (color and 0xFFFFFF) or ((M.clamp(a) * 255).toInt() shl 24)

    private fun textWidth(tf: Typeface, size: Float, s: String): Float {
        text.typeface = tf
        text.textSize = size
        text.maskFilter = null
        return text.measureText(s)
    }

    private fun drawText(c: Canvas, s: String, x: Double, y: Double, tf: Typeface, size: Float, color: Int, alpha: Double = 1.0, blur: Double = 0.0) {
        if (alpha <= 0.002) return
        text.typeface = tf
        text.textSize = size
        text.color = withAlpha(color, alpha * ((color ushr 24) / 255.0))
        text.maskFilter = if (blur > 0.5) BlurMaskFilter(sigmaRadius(blur), BlurMaskFilter.Blur.NORMAL) else null
        c.drawText(s, x.toFloat(), y.toFloat(), text)
        text.maskFilter = null
    }

    private fun shadowRRect(c: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double, dy: Double, sigma: Double, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.maskFilter = BlurMaskFilter(sigmaRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        rect.set(x.toFloat(), (y + dy).toFloat(), (x + w).toFloat(), (y + dy + h).toFloat())
        c.drawRoundRect(rect, r.toFloat(), r.toFloat(), paint)
        paint.maskFilter = null
    }

    private fun fillRRect(c: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        c.drawRoundRect(rect, r.toFloat(), r.toFloat(), paint)
    }

    private fun strokeRRect(c: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double, stroke: Double, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke.toFloat()
        paint.color = color
        rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        c.drawRoundRect(rect, r.toFloat(), r.toFloat(), paint)
        paint.style = Paint.Style.FILL
    }

    // --- page + jar -----------------------------------------------------------------------------------------------------------

    private fun bubbles(e: PourJarGoalEngine, meanY: Double, out: FloatArray) {
        val table = e.vessel
        val time = e.bubbleTime
        for (i in 0 until 12) {
            val seed = i + 1
            val pose = Theme.BUBBLE_SEEDS[i]
            val radius = pose[2]
            val range = max(table.height - meanY - 20, 10.0)
            var x = pose[0] * table.width
            val col = M.clamp(Math.round((x / table.width) * 63).toDouble(), 1.0, 62.0).toInt()
            val slope = (e.heights[col + 1] - e.heights[col - 1]) / ((table.width / 63) * 2)
            val y = table.height - 18 - ((pose[1] * range + time * (10 + radius * 5)) % range)
            x += (sin(seed * 3.1 + time * 2) - sin(seed * 3.1)) * 2 + slope * time * 2
            val row = M.clamp(floor((y / table.height) * 128), 0.0, 127.0).toInt()
            val lens = if (min(x - table.left[row], table.right[row] - x) < 30) 0.85 else 1.0
            out[i * 4] = x.toFloat()
            out[i * 4 + 1] = y.toFloat()
            out[i * 4 + 2] = radius.toFloat()
            out[i * 4 + 3] = lens.toFloat()
        }
    }

    private fun drawLiquidPage(c: Canvas, l: Theme.Layout, e: PourJarGoalEngine, reduced: Boolean) {
        val range = max(1.0, e.max - e.min)
        val level = M.clamp((e.amount - e.min) / range)
        val meanY = e.vessel.volumeToY(level)
        if (sdk33) {
            val u = uniforms
            u.width = l.w.toFloat(); u.height = l.h.toFloat()
            u.jarX = l.jarX.toFloat(); u.jarY = l.jarY.toFloat(); u.jarW = l.jarW.toFloat(); u.jarH = l.jarH.toFloat()
            u.vesselW = e.vessel.width.toFloat(); u.vesselH = e.vessel.height.toFloat()
            u.radiusTop = Theme.VESSEL_TOP_R.toFloat(); u.radiusBottom = Theme.VESSEL_BOTTOM_R.toFloat()
            u.meanY = meanY.toFloat(); u.level = level.toFloat()
            u.bubbleTime = e.bubbleTime.toFloat()
            u.bubbleCount = if (reduced) 0f else 12f
            bubbles(e, meanY, u.bubbles)
            u.fingerX = e.dropX.toFloat(); u.fingerY = e.fingerY.toFloat()
            u.stream = e.stream.toFloat(); u.dropY = e.dropY.toFloat(); u.dropRadius = e.dropRadius.toFloat()
            u.impact = e.impact.toFloat(); u.ambient = 1f; u.reduced = if (reduced) 1f else 0f
            u.pageColor = rgb(Theme.PAGE); u.surfaceColor = rgb(Theme.SURFACE); u.depthColor = rgb(Theme.DEPTH)
            val shader = liquid ?: run {
                val wall = wallBitmap ?: pourJarGoalPackRows(listOf(e.vessel.left, e.vessel.right), 0.0, e.vessel.width).also { wallBitmap = it }
                PourJarGoalLiquidShader(wall).also { liquid = it }
            }
            for (i in 0 until 64) {
                val encoded = Math.round(M.clamp((e.heights[i] + 64) / 128) * 65535).toInt()
                fieldPx[2 * i] = (encoded shr 8) shl 24
                fieldPx[2 * i + 1] = (encoded and 255) shl 24
            }
            val field = Bitmap.createBitmap(fieldPx, 128, 1, Bitmap.Config.ARGB_8888)
            shader.apply(u, field)
            c.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), shader.paint)
        } else drawFallbackPage(c, l, e, meanY, level)
    }

    /** API < 33: the same screen in gradients and paths (no refraction, back rim or caustics). */
    private fun drawFallbackPage(c: Canvas, l: Theme.Layout, e: PourJarGoalEngine, meanY: Double, level: Double) {
        val w = l.w.toFloat(); val h = l.h.toFloat()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Theme.PAGE, Theme.PAGE, 0xFFF7F8FB.toInt(), 0xFFF7F8FB.toInt()), floatArrayOf(0f, 0.10f, 0.61f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val sx = l.jarW / e.vessel.width
        val sy = l.jarH / e.vessel.height
        // pool of light under the jar
        paint.color = withAlpha(0xFFFF7554.toInt(), 0.14 * level / 0.75 + 0.05 * e.impact)
        paint.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        rect.set((l.jarX + l.jarW * 0.2).toFloat(), (l.jarY + l.jarH + 4).toFloat(), (l.jarX + l.jarW * 0.8).toFloat(), (l.jarY + l.jarH + 20 * l.s).toFloat())
        c.drawOval(rect, paint)
        paint.maskFilter = null
        val jar = Path()
        for (row in 0 until 128) {
            val x = (l.jarX + e.vessel.left[row] * sx).toFloat(); val y = (l.jarY + (row + 0.5) / 128 * e.vessel.height * sy).toFloat()
            if (row == 0) jar.moveTo(x, y) else jar.lineTo(x, y)
        }
        for (row in 127 downTo 0) jar.lineTo((l.jarX + e.vessel.right[row] * sx).toFloat(), (l.jarY + (row + 0.5) / 128 * e.vessel.height * sy).toFloat())
        jar.close()
        paint.color = 0x8FFDFEFF.toInt()
        c.drawPath(jar, paint)
        c.save()
        c.clipPath(jar)
        val lipY = e.vessel.volumeToY(level)
        val row = M.clamp(floor(lipY / e.vessel.height * 128), 0.0, 127.0).toInt()
        val left = e.vessel.left[row]; val right = e.vessel.right[row]
        val surface = Path()
        for (i in 0 until 64) {
            val u = i / 63.0
            val rest = (sin(u * 6.2831853 + 0.2) * 1.9 + sin(u * 12.5663706 + 1.2) * 1.4)
            val y = (l.jarY + (lipY - e.heights[i] - rest) * sy).toFloat()
            val x = (l.jarX + (left + u * (right - left)) * sx).toFloat()
            if (i == 0) surface.moveTo(x, y) else surface.lineTo(x, y)
        }
        surface.lineTo((l.jarX + l.jarW).toFloat(), (l.jarY + l.jarH).toFloat())
        surface.lineTo(l.jarX.toFloat(), (l.jarY + l.jarH).toFloat())
        surface.close()
        paint.shader = LinearGradient(0f, (l.jarY + lipY * sy).toFloat(), 0f, (l.jarY + l.jarH).toFloat(), Theme.SURFACE, Theme.DEPTH, Shader.TileMode.CLAMP)
        c.drawPath(surface, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0xB3FFE8D9.toInt()
        c.drawPath(surface, paint)
        paint.style = Paint.Style.FILL
        // key-light streak
        paint.color = 0x99FFFFFF.toInt()
        rect.set((l.jarX + 31 * sx).toFloat(), (l.jarY + 18 * sy).toFloat(), (l.jarX + 39 * sx).toFloat(), (l.jarY + 168 * sy).toFloat())
        c.drawRoundRect(rect, 4f, 4f, paint)
        if (e.stream > 0.1) {
            paint.color = Theme.SURFACE
            c.drawRect((l.jarX + l.jarW / 2 - e.stream * sx / 2).toFloat(), (l.jarY + 10 * sy).toFloat(), (l.jarX + l.jarW / 2 + e.stream * sx / 2).toFloat(), (l.jarY + lipY * sy).toFloat(), paint)
        }
        if (e.dropRadius > 0.1) {
            paint.color = Theme.SURFACE
            c.drawCircle((l.jarX + e.dropX * sx).toFloat(), (l.jarY + e.dropY * sy).toFloat(), (e.dropRadius * sx).toFloat(), paint)
        }
        c.restore()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = 0xFFB0BAC7.toInt()
        c.drawPath(jar, paint)
        paint.style = Paint.Style.FILL
    }

    // --- UI ------------------------------------------------------------------------------------------------------------------------

    private class Fonts(val s: Double, val fs: Double, val largestType: Boolean, val largeType: Boolean) {
        val numberSize = min(Theme.DISPLAY_SIZE * s * min(fs, 1.08), 76.0).toFloat()
        val bodySize = (Theme.BODY_SIZE * s * fs).toFloat()
        val labelSize = (Theme.LABEL_SIZE * s * fs).toFloat()
        val titleSize = (Theme.TITLE_SIZE * s * (if (largestType) fs else min(fs, 1.4))).toFloat()
        val buttonSize = (16 * s * (if (largestType) fs else min(fs, 1.5))).toFloat()
        val statSize = (Theme.STAT_SIZE * s * fs).toFloat()
        val monoSize = (max(10 * s, 10.0) * min(fs, 1.35)).toFloat()
        val toastSize = (14 * s * (if (largeType) fs else 1.0)).toFloat()
    }

    fun draw(c: Canvas, l: Theme.Layout, e: PourJarGoalEngine, reduced: Boolean) {
        drawLiquidPage(c, l, e, reduced)
        val s = l.s
        val f = Fonts(s, fontScale, l.largestType, l.largeType)
        val range = max(1.0, e.max - e.min)
        val ink = Theme.INK
        val mute = Theme.MUTE
        val headCY = l.headerY + 22 * s
        val title = Theme.TITLE
        val titleW = textWidth(semibold, f.titleSize, title).toDouble()
        val large = l.largeType

        // header discs and glyphs
        shadowRRect(c, l.x(41.0) - 19 * s, headCY - 19 * s, 38 * s, 38 * s, 19 * s, 2 * s, 4 * s, 0x0B101D29)
        fillRRect(c, l.x(41.0) - 19 * s, headCY - 19 * s, 38 * s, 38 * s, 19 * s, Theme.WHITE)
        fillRRect(c, l.x(361.0) - 19 * s, headCY - 19 * s, 38 * s, 38 * s, 19 * s, Theme.WHITE)
        path.reset()
        path.moveTo(l.x(43.0).toFloat(), (l.headerY + 17 * s).toFloat())
        path.rLineTo((-5 * s).toFloat(), (5 * s).toFloat())
        path.rLineTo((5 * s).toFloat(), (5 * s).toFloat())
        paint.style = Paint.Style.STROKE; paint.strokeWidth = (1.4 * s).toFloat(); paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.color = ink
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        for (x in intArrayOf(355, 361, 367)) c.drawCircle(l.x(x.toDouble()).toFloat(), headCY.toFloat(), (0.95 * s).toFloat(), paint)

        // title / toast pill share the header
        val e0 = M.clamp(e.toast)
        val titleFade = if (large) 1.0 else 1 - M.segment(e0, 0.0, 0.35)
        val titleShift = if (large || reduced) 0.0 else -5 * s * M.segment(e0, 0.0, 0.35)
        if (l.largestType) {
            for ((index, line) in title.split(' ').withIndex()) {
                drawText(c, line, W(l) / 2 - textWidth(semibold, f.titleSize, line) / 2, l.headerY + f.titleSize * (0.75 + index * 1.1) + titleShift, semibold, f.titleSize, ink, titleFade)
            }
        } else drawText(c, title, W(l) / 2 - titleW / 2, l.headerY + 28 * s + titleShift, semibold, f.titleSize, ink, titleFade)

        // scale ticks
        val sy = l.jarH / e.vessel.height
        val sxJar = l.jarW / e.vessel.width
        for (tick in doubleArrayOf(1.0, 0.75, 0.5, 0.25)) {
            val hot = Math.abs((e.amount - e.min) / range - tick) < 0.01
            val y = l.jarY + max(if (tick == 1.0) 12.0 else 0.0, e.vessel.volumeToY(tick)) * sy + f.monoSize / 3
            drawText(c, "${Math.round(tick * 100)}%", l.jarX + l.jarW + 16 * s, y, mono, f.monoSize, if (hot) ink else mute)
        }

        // amount
        val level = M.clamp((e.amount - e.min) / range)
        val warm = M.segment(level, 0.9, 1.0)
        val warmInk = (0xFF shl 24) or (Math.round(16 + 26 * warm).toInt() shl 16) or (Math.round(29 - 8 * warm).toInt() shl 8) or Math.round(41 - 23 * warm).toInt()
        drawAmount(c, l, e, f, warmInk, reduced)

        // subtitle
        val pct = M.percentage(e.percentAmount, e.min, e.max)
        if (l.largestType) {
            val a = "of ${M.money(e.max)}"
            drawText(c, a, W(l) / 2 - textWidth(regular, f.bodySize, a) / 2, l.subtitleY + f.bodySize, regular, f.bodySize, mute)
            drawText(c, "$pct%", W(l) / 2 - textWidth(regular, f.bodySize, "75%") / 2, l.subtitleY + f.bodySize * 2.2, regular, f.bodySize, mute)
        } else {
            val fixed = textWidth(regular, f.bodySize, "of ${M.money(e.max)} · 75%")
            drawText(c, "of ${M.money(e.max)} · $pct%", W(l) / 2 - fixed / 2, l.subtitleY + 13 * s, regular, f.bodySize, mute)
        }

        // cards
        val cardW = if (large) Theme.BUTTONS_W * s else Theme.CARD_W * s
        val cardH = if (large) (l.cardH - 8 * s) / 2 else l.cardH
        val card2X = if (large) l.x(Theme.CARD_X) else l.x(Theme.CARD_X + Theme.CARD_W + Theme.CARD_GAP)
        val card2Y = if (large) l.cardsY + cardH + 8 * s else l.cardsY
        val remaining = M.money(e.max - e.statAmount)
        val weeks = "${Math.ceil(max(0.0, Math.round(e.max - e.statAmount).toDouble()) / 40).toInt()} weeks"
        val cards = listOf(Triple(l.x(Theme.CARD_X) to l.cardsY, Theme.REMAINING, remaining), Triple(card2X to card2Y, Theme.WEEKLY, weeks))
        for ((pos, label, value) in cards) {
            val (cx, cy) = pos
            val r = Theme.CARD_RADIUS * s
            shadowRRect(c, cx, cy, cardW, cardH, r, 1 * s, 1 * s, 0x0A101D29)
            shadowRRect(c, cx, cy, cardW, cardH, r, 5 * s, 12 * s, 0x08101D29)
            fillRRect(c, cx, cy, cardW, cardH, r, Theme.WHITE)
            strokeRRect(c, cx, cy, cardW, cardH, r, 0.5 * s, Theme.OUTLINE)
            drawText(c, label, cx + 16 * s, cy + (if (large) 12 + 12 * fontScale * 0.75 else 24.0) * s, medium, f.labelSize, mute)
            drawText(c, value, cx + 16 * s, cy + (if (large) cardH / s - 16 else 56.0) * s, semibold, f.statSize, ink)
        }

        // buttons
        val press = if (reduced) 1.0 else 1 - 0.03 * M.clamp(e.touching - 1.0)
        c.save()
        c.scale(press.toFloat(), press.toFloat(), l.x(139.0).toFloat(), (l.buttonY + l.buttonH / 2).toFloat())
        fillRRect(c, l.x(Theme.BUTTONS_X), l.buttonY, Theme.PRIMARY_W * s, l.buttonH, Theme.PILL_RADIUS * s, ink)
        drawText(c, Theme.POUR, l.x(Theme.BUTTONS_X) + Theme.PRIMARY_W * s / 2 - textWidth(semibold, f.buttonSize, Theme.POUR) / 2, l.buttonY + l.buttonH / 2 + f.buttonSize * 0.35, semibold, f.buttonSize, Theme.WHITE)
        c.restore()
        fillRRect(c, l.x(266.0), l.buttonY, 114 * s, l.buttonH, Theme.PILL_RADIUS * s, Theme.WHITE)
        strokeRRect(c, l.x(266.0), l.buttonY, 114 * s, l.buttonH, Theme.PILL_RADIUS * s, 0.5 * s, Theme.OUTLINE)
        val autoLabel = if (e.autoEnabled) (if (l.largestType) "On" else Theme.AUTO_ON) else Theme.AUTO
        drawText(c, autoLabel, l.x(323.0) - textWidth(semibold, f.buttonSize, autoLabel) / 2, l.buttonY + l.buttonH / 2 + f.buttonSize * 0.35, semibold, f.buttonSize, ink)

        // toast
        val toastText = "${M.money(e.delta)} poured into ${Theme.GOAL}"
        if (!large) drawPill(c, l, e, f, headCY, titleW, toastText, reduced) else drawLargeToast(c, l, e, f, toastText, reduced, sxJar)
    }

    private fun W(l: Theme.Layout) = l.w

    private fun drawPill(c: Canvas, l: Theme.Layout, e: PourJarGoalEngine, f: Fonts, headCY: Double, titleW: Double, toastText: String, reduced: Boolean) {
        val s = l.s
        val pe = M.clamp(e.toast)
        if (pe <= 0.001) return
        val pillMaxW = l.x(339.0) - l.x(63.0) - 8 * s
        val pillW = min(pillMaxW, textWidth(medium, f.toastSize, toastText).toDouble() + 52 * s)
        val w = titleW + 28 * s + (pillW - titleW - 28 * s) * pe
        val h = (30 + 10 * pe) * s
        val left = l.w / 2 - pillW / 2
        val fill = M.segment(pe, 0.0, 0.35)
        if (fill > 0.002) {
            shadowRRect(c, l.w / 2 - w / 2, headCY - h / 2, w, h, h / 2, 6 * s, 10 * s, withAlpha(0xFF15202C.toInt(), 0.16 * pe * fill))
            fillRRect(c, l.w / 2 - w / 2, headCY - h / 2, w, h, h / 2, withAlpha(Theme.TOAST, fill))
        }
        val content = M.segment(pe, 0.6, 1.0)
        if (content <= 0.002) return
        val shift = if (reduced) 0.0 else 5 * s * (1 - content)
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(Theme.DEPTH, content)
        c.drawCircle((left + 20 * s).toFloat(), (headCY + shift).toFloat(), ((if (reduced) 11.0 else 6 + 5 * content) * s).toFloat(), paint)
        path.reset()
        path.moveTo((left + 16 * s).toFloat(), (headCY + shift).toFloat())
        path.rLineTo((3 * s).toFloat(), (3 * s).toFloat())
        path.rLineTo((5 * s).toFloat(), (-5 * s).toFloat())
        paint.style = Paint.Style.STROKE; paint.strokeWidth = (1.5 * s).toFloat(); paint.color = withAlpha(Theme.WHITE, content)
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        drawText(c, toastText, left + 38 * s, headCY + f.toastSize * 0.36 + shift, medium, f.toastSize, Theme.WHITE, content)
    }

    private fun drawLargeToast(c: Canvas, l: Theme.Layout, e: PourJarGoalEngine, f: Fonts, toastText: String, reduced: Boolean, sx: Double) {
        val s = l.s
        val a = M.clamp(e.toast)
        if (a <= 0.002) return
        val dy = if (reduced) 0.0 else -38 * s * (1 - a)
        val toastH = f.toastSize * 3.5 + 20 * s
        val money = M.money(e.delta)
        shadowRRect(c, l.jarX, l.jarY + 17 * s + dy, l.jarW, toastH, 24 * s, 9 * s, 11 * s, withAlpha(0xFF15202C.toInt(), 0.19 * a))
        fillRRect(c, l.jarX, l.jarY + 17 * s + dy, l.jarW, toastH, 24 * s, withAlpha(Theme.TOAST, a))
        val cy = l.jarY + 25 * s + f.toastSize * 0.5 + dy
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(Theme.DEPTH, a)
        c.drawCircle((l.jarX + 22 * s).toFloat(), cy.toFloat(), (11.5 * s).toFloat(), paint)
        path.reset()
        path.moveTo((l.jarX + 18 * s).toFloat(), cy.toFloat())
        path.rLineTo((3 * s).toFloat(), (3 * s).toFloat())
        path.rLineTo((5 * s).toFloat(), (-5 * s).toFloat())
        paint.style = Paint.Style.STROKE; paint.strokeWidth = (1.5 * s).toFloat(); paint.color = withAlpha(Theme.WHITE, a)
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        val top = l.jarY + 25 * s + dy
        drawText(c, money, l.jarX + 42 * s, top + f.toastSize, medium, f.toastSize, Theme.WHITE, a)
        drawText(c, "poured", l.w / 2 - textWidth(medium, f.toastSize, "poured") / 2, top + f.toastSize * 2.12, medium, f.toastSize, Theme.WHITE, a)
        val into = "into ${Theme.GOAL}"
        drawText(c, into, l.w / 2 - textWidth(medium, f.toastSize, into) / 2, top + f.toastSize * 3.24, medium, f.toastSize, Theme.WHITE, a)
        if (toastText.isEmpty()) return
    }

    // --- rolling amount ------------------------------------------------------------------------------------------------------------

    private class Slot(val char: String, val place: Int, val x: Double)

    private fun drawAmount(c: Canvas, l: Theme.Layout, e: PourJarGoalEngine, f: Fonts, ink: Int, reduced: Boolean) {
        val size = f.numberSize
        val cx = l.w / 2
        val baseline = l.amountBaseline
        val value = e.displayAmount
        text.typeface = instrument
        text.textSize = size
        text.maskFilter = null
        var digitW = 0f
        for (ch in '0'..'9') digitW = max(digitW, text.measureText(ch.toString()))
        digitW -= size * 0.025f
        val dollarW = text.measureText("$") - size * 0.025f
        val commaW = text.measureText(",")
        val length = max(1, floor(max(1.0, e.max)).toLong().toString().length)
        val slots = ArrayList<Slot>()
        var x = dollarW.toDouble()
        slots.add(Slot("$", 0, 0.0))
        for (n in length - 1 downTo 0) {
            var place = 1
            repeat(n) { place *= 10 }
            slots.add(Slot("", place, x))
            x += digitW
            if (n > 0 && n % 3 == 0) {
                slots.add(Slot(",", 0, x))
                x += commaW
            }
        }
        val total = x
        val threshold = Math.pow(10.0, (length - 1).toDouble())
        val rollOpacity = if (threshold == 1.0) 1.0 else M.segment(value, threshold - 1, threshold)
        val compactOpacity = 1 - rollOpacity
        c.save()
        c.scale(0.92f, 1f, cx.toFloat(), baseline.toFloat())
        if (compactOpacity > 0.002) {
            val s = M.money(value)
            drawText(c, s, cx - textWidth(instrument, size, s) / 2, baseline, instrument, size, ink, compactOpacity)
        }
        if (rollOpacity > 0.002) {
            for (slot in slots) {
                val sx = cx - total / 2 + slot.x
                if (slot.place == 0) {
                    drawText(c, slot.char, sx, baseline, instrument, size, ink, rollOpacity)
                    continue
                }
                if (reduced) {
                    val d = floor(max(0.0, value) / slot.place) % 10
                    drawText(c, d.toInt().toString(), sx, baseline, instrument, size, ink, rollOpacity)
                    continue
                }
                val d = M.digitAt(value, slot.place)
                val fr = d - floor(d)
                val u = M.clamp((fr - 0.7) / 0.3)
                val k = u * u * (3 - 2 * u)
                val travel = size * 0.28
                val first = (floor(d).toInt() % 10).toString()
                val next = ((floor(d).toInt() + 1) % 10).toString()
                val ua = min(k / 0.6, 1.0)
                val a0 = 1 - ua * ua * (3 - 2 * ua)
                val ub = max((k - 0.4) / 0.6, 0.0)
                val a1 = ub * ub * (3 - 2 * ub)
                drawText(c, first, sx, baseline - k * travel, instrument, size, ink, a0 * rollOpacity, max(0.001, k * size * 0.045))
                drawText(c, next, sx, baseline + (1 - k) * travel, instrument, size, ink, a1 * rollOpacity, max(0.001, (1 - k) * size * 0.045))
            }
        }
        c.restore()
    }

}
