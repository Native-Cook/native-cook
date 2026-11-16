package com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge.FlipFocusBadgeMotion as M
import com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge.FlipFocusBadgeTheme as Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The screen in one canvas, in the RN order: the page's film (the backdrop shader) with the progress line, the badge (the ray-marched shader, or the
 * fallback drawing), the top row, the line's labels and the hint, and the sheet with its date, rolling total, caption and segmented control. Everything
 * is in dp: the caller scales the canvas by the density.
 */
internal class FlipFocusBadgeRenderer(private val assets: AssetManager) {
    private fun face(path: String) = Typeface.createFromAsset(assets, path)
    private val medium = face(Theme.FONT_MEDIUM)
    private val semibold = face(Theme.FONT_SEMIBOLD)
    private val bold = face(Theme.FONT_BOLD)
    private val fallback = FlipFocusBadgeFallback(bold)
    private var atlas: Bitmap? = null
    private var atlasTried = false
    private var badgePass: Any? = null
    private var backdropPass: Any? = null
    private var shadersFailed = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()
    private val clip = Path()

    // the icons, in the 24 x 24 viewBox of the RN SVGs
    private fun pd(d: String): Path = try {
        PathParser.createPathFromPathData(d)
    } catch (error: RuntimeException) {
        Path()
    }

    private val flameBody = pd("M12.3 1.6C13.4 5.3 18.4 8 18.4 13.4C18.4 17.7 15.7 21 12.1 21C8.5 21 5.7 18.1 5.7 14.3C5.7 11.8 6.9 10.1 8.3 8.9C8.5 10.5 9.3 11.6 10.4 12.1C10 8.4 10.5 4.6 12.3 1.6Z")
    private val flameCore = pd("M12.1 12.2C13 13.6 14.6 14.8 14.6 16.8C14.6 18.4 13.5 19.5 12.1 19.5C10.7 19.5 9.6 18.4 9.6 16.9C9.6 15.7 10.4 14.8 11.2 14C11.6 13.4 11.9 12.9 12.1 12.2Z")
    private val cup = pd("M6.2 3.2H17.8V8.6C17.8 11.9 15.3 14.4 12 14.4C8.7 14.4 6.2 11.9 6.2 8.6V3.2Z")
    private val handles = pd("M6.2 5H3.4V7.2C3.4 9.1 4.8 10.6 6.7 10.9M17.8 5H20.6V7.2C20.6 9.1 19.2 10.6 17.3 10.9")
    private val stem = pd("M10.6 14.2H13.4V17.6H10.6Z")
    private val base = pd("M7.8 19.2C7.8 18.4 8.4 17.8 9.2 17.8H14.8C15.6 17.8 16.2 18.4 16.2 19.2V20.8H7.8V19.2Z")
    private val question = pd("M8.6 9.4C8.6 7.4 10.1 6.1 12 6.1C13.9 6.1 15.4 7.3 15.4 9.1C15.4 10.4 14.6 11.1 13.7 11.7C12.9 12.2 12.5 12.6 12.5 13.6")
    private val gear = Path().apply {
        // eight rounded teeth round a hole (RN GearIcon)
        val teeth = 8
        val ro = 10.0
        val ri = 7.8
        fun p(r: Double, ang: Double) = floatArrayOf((12 + r * sin(ang)).toFloat(), (12 - r * cos(ang)).toFloat())
        for (i in 0 until teeth) {
            val a = i.toDouble() / teeth * PI * 2
            val w = 0.2
            val v = 0.33
            val s0 = p(ri, a - PI / teeth + 0.02)
            if (i == 0) moveTo(s0[0], s0[1]) else lineTo(s0[0], s0[1])
            val s1 = p(ri, a - v)
            lineTo(s1[0], s1[1])
            val s2 = p(ro, a - w)
            lineTo(s2[0], s2[1])
            val s3 = p(ro, a + w)
            lineTo(s3[0], s3[1])
            val s4 = p(ri, a + v)
            lineTo(s4[0], s4[1])
        }
        close()
        addCircle(12f, 12f, 3.5f, Path.Direction.CW)
        fillType = Path.FillType.EVEN_ODD
    }

    private fun atlas(): Bitmap? {
        if (atlasTried) return atlas
        atlasTried = true
        atlas = try {
            val o = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            assets.open(Theme.ATLAS_ASSET).use { BitmapFactory.decodeStream(it, null, o) }
        } catch (error: Exception) {
            Log.w("FlipFocusBadge", "Glyph atlas unavailable", error)
            null
        }
        return atlas
    }

    private fun badge(): FlipFocusBadgeBadgePass? {
        if (Build.VERSION.SDK_INT < 33 || shadersFailed) return null
        (badgePass as? FlipFocusBadgeBadgePass)?.let { return it }
        val bmp = atlas() ?: return null
        return try {
            FlipFocusBadgeBadgePass(bmp).also { badgePass = it }
        } catch (error: IllegalArgumentException) {
            Log.w("FlipFocusBadge", "Runtime shader unavailable; using the Canvas fallback", error)
            shadersFailed = true
            null
        }
    }

    private fun backdrop(): FlipFocusBadgeBackdropPass? {
        if (Build.VERSION.SDK_INT < 33 || shadersFailed) return null
        (backdropPass as? FlipFocusBadgeBackdropPass)?.let { return it }
        return try {
            FlipFocusBadgeBackdropPass().also { backdropPass = it }
        } catch (error: IllegalArgumentException) {
            Log.w("FlipFocusBadge", "Runtime shader unavailable; using the Canvas fallback", error)
            shadersFailed = true
            null
        }
    }

    private fun a(c: Int, alpha: Double) = Color.argb((alpha.coerceIn(0.0, 1.0) * Color.alpha(c)).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    /** a line of text whose baseline falls at `y`; `x` is its left, centre or right per `align`; `tnum` = tabular figures */
    private fun txt(c: Canvas, str: String, t: Theme.Type, sz: Double, color: Int, alpha: Double, x: Double, y: Double, align: Paint.Align, tnum: Boolean = false) {
        if (alpha <= 0.002) return
        text.typeface = when (t.font) {
            Theme.FONT_BOLD -> bold
            Theme.FONT_SEMIBOLD -> semibold
            else -> medium
        }
        text.textSize = sz.toFloat()
        text.letterSpacing = t.tracking.toFloat()
        text.fontFeatureSettings = if (tnum) "tnum" else ""
        text.textAlign = align
        text.color = a(color, alpha)
        c.drawText(str, x.toFloat(), y.toFloat(), text)
    }

    private fun measure(str: String, t: Theme.Type, sz: Double, tnum: Boolean): Double {
        text.typeface = when (t.font) {
            Theme.FONT_BOLD -> bold
            Theme.FONT_SEMIBOLD -> semibold
            else -> medium
        }
        text.textSize = sz.toFloat()
        text.letterSpacing = t.tracking.toFloat()
        text.fontFeatureSettings = if (tnum) "tnum" else ""
        text.textAlign = Paint.Align.LEFT
        return text.measureText(str).toDouble()
    }

    private fun icon(c: Canvas, x: Float, y: Float, size: Float, body: (Canvas) -> Unit) {
        c.save()
        c.translate(x, y)
        c.scale(size / 24f, size / 24f)
        body(c)
        c.restore()
    }

    private fun fillPath(c: Canvas, p: Path, color: Int) {
        fill.shader = null
        fill.color = color
        c.drawPath(p, fill)
    }

    private fun strokePath(c: Canvas, p: Path, color: Int, width: Float) {
        stroke.shader = null
        stroke.color = color
        stroke.strokeWidth = width
        c.drawPath(p, stroke)
    }

    /** a crossfade of two lines (labels and hints): the old one is gone by the half-way point, the new one arrives after it */
    private fun crossfade(
        c: Canvas, aStr: String, bStr: String, k: Double, t: Theme.Type, l: Theme.Layout, baseline: Double, color: Int, align: Paint.Align, x: Double, slide: Double,
    ) {
        val sz = t.size * l.s
        txt(c, aStr, t, sz, color, max(1 - 2 * k, 0.0), x, baseline - slide * k, align)
        txt(c, bStr, t, sz, color, max(2 * k - 1, 0.0), x, baseline + slide * (1 - k), align)
    }

    fun draw(c: Canvas, w: Float, h: Float, density: Float, l: Theme.Layout, e: FlipFocusBadgeEngine, reduce: Boolean) {
        val s = l.s
        val look = e.look
        val cosYaw = abs(cos(look.yaw * FlipFocusBadgeGeometry.D2R))
        // the page's film: ground, contact shadow
        val shadowX = l.x(Theme.CENTRE_X).toFloat()
        val shadowY = l.yc(398.0).toFloat()
        val ramp0 = l.yc(150.0).toFloat()
        val ramp1 = l.yc(400.0).toFloat()
        val hw = (95 * s * (0.4 + 0.6 * cosYaw)).toFloat()
        val hh = (26 * s).toFloat()
        val amt = (0.35 + 0.1 * cosYaw).toFloat()
        val bp = backdrop()
        if (bp != null && Build.VERSION.SDK_INT >= 33) bp.draw(c, w, h, shadowX, shadowY, hw, hh, amt, ramp0, ramp1)
        else fallback.backdrop(c, w, h, shadowX, shadowY, hw, hh, amt, ramp0, ramp1)

        // the progress line
        val trackX = l.x(Theme.PROGRESS_X)
        val trackW = Theme.PROGRESS_W * s
        val trackH = Theme.PROGRESS_H * s
        val trackY = l.yc(Theme.PROGRESS_Y)
        val trackR = (trackH / 2).toFloat()
        c.save()
        rect.set(trackX.toFloat(), trackY.toFloat(), (trackX + trackW).toFloat(), (trackY + trackH).toFloat())
        clip.reset()
        clip.addRoundRect(rect, trackR, trackR, Path.Direction.CW)
        c.clipPath(clip)
        fill.shader = null
        fill.color = Theme.track
        c.drawRoundRect(rect, trackR, trackR, fill)
        val fillW = max(e.pFill * trackW, trackH)
        rect.set(trackX.toFloat(), trackY.toFloat(), (trackX + fillW).toFloat(), (trackY + trackH).toFloat())
        fill.color = Theme.fill
        c.drawRoundRect(rect, trackR, trackR, fill)
        if (e.pSweep >= 0) {
            val o = 0.6 * sin(PI * e.pSweep)
            val sx = (trackX + max(e.pSweep, 0.0) * (trackW + 44 * s) - 44 * s).toFloat()
            fill.shader = LinearGradient(sx, 0f, sx + (44 * s).toFloat(), 0f, intArrayOf(0x00FFFFFF, Color.WHITE, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            fill.alpha = (o * 255).toInt()
            c.drawRect(sx, trackY.toFloat(), sx + (44 * s).toFloat(), (trackY + trackH).toFloat(), fill)
            fill.shader = null
            fill.alpha = 255
        }
        c.restore()

        // the badge, in its 300-high viewport
        val badgeTop = (l.yc(Theme.CENTRE_Y) - 150 * s).toFloat()
        val fadeA = e.fade.value
        c.save()
        c.translate(0f, badgeTop)
        if (fadeA < 0.999) c.saveLayerAlpha(0f, 0f, w, (300 * s).toFloat(), (fadeA.coerceIn(0.0, 1.0) * 255).toInt())
        val pass = badge()
        if (pass != null && Build.VERSION.SDK_INT >= 33) pass.draw(c, w, (300 * s).toFloat(), s.toFloat(), density, look, reduce)
        else fallback.draw(c, s.toFloat(), look)
        if (fadeA < 0.999) c.restore()
        c.restore()

        topRow(c, l, e)
        // the labels at the ends of the line, and the hint
        val tickBase = l.yc(461.3)
        val colLabel = Theme.inkLabel
        crossfade(c, "${e.labA}h", "${e.labB}h", e.pLabel, Theme.tTick, l, tickBase, colLabel, Paint.Align.LEFT, trackX, 0.0)
        crossfade(c, "${e.labA + 1}h", "${e.labB + 1}h", e.pLabel, Theme.tTick, l, tickBase, colLabel, Paint.Align.RIGHT, trackX + trackW, 0.0)
        crossfade(c, Theme.HINT_IDLE, Theme.HINT_FOCUS, e.pHint, Theme.tHint, l, l.yc(521.2), Theme.inkHint, Paint.Align.CENTER, l.w / 2, 8 * s)
        sheet(c, l, e)
    }

    private fun topRow(c: Canvas, l: Theme.Layout, e: FlipFocusBadgeEngine) {
        val s = l.s
        fun chip(b: Theme.Box, pad: Double, gap: Double, n: Int, draw: (Canvas, Float, Float) -> Unit) {
            val left = b.x * s
            val top = l.yt(b.y)
            rect.set(left.toFloat(), top.toFloat(), (left + b.w * s).toFloat(), (top + b.h * s).toFloat())
            fill.shader = null
            fill.color = Theme.chip
            c.drawRoundRect(rect, (b.h / 2 * s).toFloat(), (b.h / 2 * s).toFloat(), fill)
            val cy = top + b.h * s / 2
            val ix = left + pad * s
            draw(c, ix.toFloat(), (cy - 12 * s).toFloat())
            txt(c, n.toString(), Theme.tCount, Theme.tCount.size * s, Theme.ink, 1.0, ix + 24 * s + gap * s, cy + 0.364 * Theme.tCount.size * s, Paint.Align.LEFT)
        }
        chip(Theme.chipFlame, 6.0, 3.0, Theme.STREAK) { cv, x, y ->
            icon(cv, x, y, (24 * s).toFloat()) {
                fillPath(it, flameBody, Theme.flame)
                fillPath(it, flameCore, Color.parseColor("#FFB454"))
            }
        }
        chip(Theme.chipTrophy, 8.8, 5.0, Theme.TROPHIES) { cv, x, y ->
            icon(cv, x, y, (24 * s).toFloat()) {
                fillPath(it, cup, Color.WHITE)
                strokePath(it, handles, Color.WHITE, 1.9f)
                fillPath(it, stem, Color.WHITE)
                fillPath(it, base, Color.WHITE)
            }
        }
        for (i in 0..1) {
            val b = if (i == 0) Theme.discHelp else Theme.discGear
            val left = b.x * s
            val top = l.yt(b.y)
            val dim = if (e.discPressed == i) 0.7 else 1.0
            rect.set(left.toFloat(), top.toFloat(), (left + b.w * s).toFloat(), (top + b.h * s).toFloat())
            fill.shader = null
            fill.color = a(Theme.disc, dim)
            c.drawRoundRect(rect, (b.h / 2 * s).toFloat(), (b.h / 2 * s).toFloat(), fill)
            val size = ((if (i == 0) 22.0 else 23.0) * s).toFloat()
            val ix = (left + (b.w * s - size) / 2).toFloat()
            val iy = (top + (b.h * s - size) / 2).toFloat()
            c.saveLayerAlpha(rect, (dim * 255).toInt())
            if (i == 0) {
                icon(c, ix, iy, size) {
                    fill.shader = null
                    fill.color = Color.WHITE
                    it.drawCircle(12f, 12f, 11f, fill)
                    strokePath(it, question, Color.parseColor("#161616"), 2.3f)
                    fill.color = Color.parseColor("#161616")
                    it.drawCircle(12.5f, 17.4f, 1.35f, fill)
                }
            } else {
                icon(c, ix, iy, size) {
                    fillPath(it, gear, Color.WHITE)
                    strokePath(it, gear, Color.WHITE, 1.2f)
                }
            }
            c.restore()
        }
    }

    private fun sheet(c: Canvas, l: Theme.Layout, e: FlipFocusBadgeEngine) {
        val s = l.s
        val sw = Theme.SHEET_W * s
        val sh = Theme.SHEET_H * s
        c.save()
        c.translate(l.x(Theme.SHEET_X).toFloat(), l.sheetTop.toFloat())
        rect.set(0f, 0f, sw.toFloat(), sh.toFloat())
        val r = (Theme.SHEET_R * s).toFloat()
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        c.clipPath(clip)
        fill.shader = null
        fill.color = Theme.sheet
        c.drawRect(rect, fill)
        fun local(y: Double) = (y - Theme.SHEET_Y) * s
        // the grabber
        rect.set(((Theme.SHEET_W - Theme.GRABBER_W) / 2 * s).toFloat(), local(Theme.GRABBER_Y).toFloat(), ((Theme.SHEET_W + Theme.GRABBER_W) / 2 * s).toFloat(), (local(Theme.GRABBER_Y) + Theme.GRABBER_H * s).toFloat())
        fill.color = Theme.grabber
        c.drawRoundRect(rect, (Theme.GRABBER_H / 2 * s).toFloat(), (Theme.GRABBER_H / 2 * s).toFloat(), fill)
        val range = Theme.RANGES[e.range]
        txt(c, range.date, Theme.tDate, Theme.tDate.size * s, Theme.inkDate, e.dateFade.value, sw / 2, local(673.4), Paint.Align.CENTER)
        if (e.range == 0) rollTotal(c, e.rollFrom, e.rollTo, e.pRoll, s, sw, local(728.4))
        else txt(c, range.total, Theme.tTotal, Theme.tTotal.size * s, Theme.ink, e.totalFade.value, sw / 2, local(728.4), Paint.Align.CENTER, true)
        txt(c, Theme.CAPTION, Theme.tCaption, Theme.tCaption.size * s, Theme.inkMute, 1.0, sw / 2, local(760.4), Paint.Align.CENTER)
        segmented(c, s, e, local(Theme.SEG_Y))
        c.restore()
    }

    /** "10h 24m" -> "11h 00m": only the digits that change roll, right to left, 90 ms apart */
    private fun rollTotal(c: Canvas, from: String, to: String, k: Double, s: Double, sw: Double, baseline: Double) {
        val t = Theme.tTotal
        val sz = t.size * s
        val lh = t.lineHeight * s
        val len = max(from.length, to.length)
        val fa = from.padEnd(len, ' ')
        val ta = to.padEnd(len, ' ')
        val slots = IntArray(len)
        var slot = 0
        for (i in len - 1 downTo 0) {
            if (fa[i] != ta[i]) {
                slots[i] = slot
                slot += 1
            } else slots[i] = -1
        }
        val widths = DoubleArray(len) { measure(ta[it].toString(), t, sz, true) }
        var x = (sw - widths.sum()) / 2
        val top = baseline - (lh - 1.21 * sz) / 2 - 0.969 * sz
        for (i in 0 until len) {
            if (slots[i] < 0) {
                txt(c, ta[i].toString(), t, sz, Theme.ink, 1.0, x, baseline, Paint.Align.LEFT, true)
            } else {
                val p = M.rollAt(k, slots[i])
                c.save()
                c.clipRect(x.toFloat(), top.toFloat(), (x + widths[i]).toFloat(), (top + lh).toFloat())
                txt(c, fa[i].toString(), t, sz, Theme.ink, 1 - min(p * 1.3, 1.0), x, baseline - p * lh, Paint.Align.LEFT, true)
                txt(c, ta[i].toString(), t, sz, Theme.ink, min(p * 1.3, 1.0), x, baseline + (1 - p) * lh, Paint.Align.LEFT, true)
                c.restore()
            }
            x += widths[i]
        }
    }

    private fun segmented(c: Canvas, s: Double, e: FlipFocusBadgeEngine, top: Double) {
        val itemW = (Theme.SEG_W - 2 * Theme.SEG_PAD - 3 * Theme.SEG_GAP) / 4
        val left = (Theme.SEG_X - Theme.SHEET_X) * s
        rect.set(left.toFloat(), top.toFloat(), (left + Theme.SEG_W * s).toFloat(), (top + Theme.SEG_H * s).toFloat())
        fill.shader = null
        fill.color = Theme.seg
        c.drawRoundRect(rect, (Theme.SEG_R * s).toFloat(), (Theme.SEG_R * s).toFloat(), fill)
        val itemTop = top + Theme.SEG_PAD * s + ((Theme.SEG_H - 2 * Theme.SEG_PAD - Theme.SEG_ITEM_H) * s) / 2
        val rr = (Theme.SEG_ITEM_R * s).toFloat()
        for (i in 0..3) {
            val x = left + (Theme.SEG_PAD + i * (itemW + Theme.SEG_GAP)) * s
            rect.set(x.toFloat(), itemTop.toFloat(), (x + itemW * s).toFloat(), (itemTop + Theme.SEG_ITEM_H * s).toFloat())
            fill.color = Theme.segOff
            c.drawRoundRect(rect, rr, rr, fill)
        }
        val px = left + e.pill.value
        rect.set(px.toFloat(), itemTop.toFloat(), (px + itemW * s).toFloat(), (itemTop + Theme.SEG_ITEM_H * s).toFloat())
        fill.color = Theme.segOn
        c.drawRoundRect(rect, rr, rr, fill)
        val cy = top + Theme.SEG_H * s / 2
        for (i in 0..3) {
            val x = left + (Theme.SEG_PAD + i * (itemW + Theme.SEG_GAP)) * s + itemW * s / 2
            txt(c, Theme.SEGMENTS[i], Theme.tSeg, Theme.tSeg.size * s, if (i == e.range) Theme.ink else Theme.inkSeg, 1.0, x, cy + 0.364 * Theme.tSeg.size * s, Paint.Align.CENTER)
        }
    }
}
