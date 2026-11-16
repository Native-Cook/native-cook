package com.ngocdevv.nativecook.blocks.media.extrusionrecapstory

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.media.extrusionrecapstory.ExtrusionRecapStoryMotion as M
import com.ngocdevv.nativecook.blocks.media.extrusionrecapstory.ExtrusionRecapStoryTheme as Theme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class ExtrusionRecapStoryHit { NONE, CLOSE, PLAY, PEEK }

/**
 * The screen's drawing (RN `ExtrusionStory`): the swept solid and its field from the AGSL shader (API 33+; a gradient and offset copies
 * of the glyph below), a quarter-resolution bloom, each page's copy (the stat's digits rolling), the next card's peek label, the header
 * and the play / pause button. Everything is in dp: the caller scales the canvas by the density.
 */
internal class ExtrusionRecapStoryRenderer(
    assets: AssetManager,
    private val pages: List<ExtrusionRecapStoryPage>,
    private val atlas: Bitmap?,
    private val layout: Theme.Layout,
    private val w: Double,
    private val h: Double,
    private val fontScale: Double,
    private val hasClose: Boolean,
    private val hasPlay: Boolean,
) {
    private val display = Typeface.createFromAsset(assets, "extrusionrecapstory/extrusionrecapstory_Onest-ExtraBold.ttf")
    private val body = Typeface.createFromAsset(assets, "extrusionrecapstory/extrusionrecapstory_Onest-Regular.ttf")
    private val s = layout.s
    private val count = pages.size

    private val palette = pages.map {
        Triple(Theme.rgb(it.fieldA) to Theme.rgb(it.fieldB), Theme.rgb(it.face), it.direction.map { d -> d.toFloat() }.toFloatArray())
    }

    private var main: ExtrusionRecapStoryShader? = null
    private var bloomShader: ExtrusionRecapStoryShader? = null
    private var shaderFailed = false
    private val node: RenderNode? = if (Build.VERSION.SDK_INT >= 33) RenderNode("ExtrusionRecapStoryBloom") else null
    private var blurSigma = -1f
    private val u = ExtrusionRecapStoryUniforms()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rr = RectF()
    private val tmpPath = Path()
    private val starPath = PathParser.createPathFromPathData("M12 1L13.8 8.2L20 4L15.8 10.2L23 12L15.8 13.8L20 20L13.8 15.8L12 23L10.2 15.8L4 20L8.2 13.8L1 12L8.2 10.2L4 4L10.2 8.2Z")
    private val closePath = PathParser.createPathFromPathData("M3 3L17 17M17 3L3 17")
    private val playPath = PathParser.createPathFromPathData("M5 3L17 10L5 17Z")
    private val pausePath = PathParser.createPathFromPathData("M5 3H7V17H5ZM13 3H15V17H13Z")
    private val chevron = PathParser.createPathFromPathData("M6 3.5L10.5 8L6 12.5")
    private val glyphs = pages.map { ExtrusionRecapStorySdf.outlinePath(it.glyph) }

    // hit rects
    private val peekTop = (layout.cardBottom + Theme.GAP * s).toFloat()
    val peekRect = RectF(0f, peekTop, w.toFloat(), h.toFloat())
    val closeRect = RectF(
        (Theme.EDGE * s - 12 * s).toFloat(), layout.headerY.toFloat(), (Theme.EDGE * s - 12 * s + 44).toFloat(), (layout.headerY + 44).toFloat(),
    )
    val playRect = RectF(
        (w - Theme.EDGE * s - 44).toFloat(), (layout.headerY + 44).toFloat(), (w - Theme.EDGE * s).toFloat(), (layout.headerY + 88).toFloat(),
    )

    fun hit(x: Double, y: Double, e: ExtrusionRecapStoryEngine): ExtrusionRecapStoryHit {
        val fx = x.toFloat()
        val fy = y.toFloat()
        return when {
            hasClose && closeRect.contains(fx, fy) -> ExtrusionRecapStoryHit.CLOSE
            hasPlay && playRect.contains(fx, fy) -> ExtrusionRecapStoryHit.PLAY
            count > 1 && peekRect.contains(fx, fy) -> ExtrusionRecapStoryHit.PEEK
            else -> ExtrusionRecapStoryHit.NONE
        }
    }

    // ---- shader uniforms ------------------------------------------------------------------------------------------------------

    private fun fillUniforms(e: ExtrusionRecapStoryEngine, density: Float) {
        val c = max(1, count)
        val p = M.mod(e.progress.value, c.toDouble())
        val i = floor(p).toInt()
        val n = (i + 1) % c
        val a = palette[i]
        val b = palette[n]
        val after = palette[(n + 1) % c]
        u.width = w.toFloat(); u.height = h.toFloat()
        u.centerX = Theme.HERO_X.toFloat(); u.centerY = (layout.heroY / s).toFloat()
        u.direction = a.third; u.nextDirection = b.third
        u.q = (p - i).toFloat()
        u.reduced = if (e.reduce) 1f else 0f
        u.current = i.toFloat(); u.next = n.toFloat(); u.count = count.toFloat()
        u.fieldA = a.first.first; u.fieldB = a.first.second
        u.nextA = b.first.first; u.nextB = b.first.second
        u.afterB = after.first.second
        u.faceA = a.second; u.faceB = b.second
        u.steerX = e.steerX.value.toFloat(); u.steerY = e.steerY.value.toFloat()
        u.phase = (if (e.reduce) 0.0 else (e.elapsed % M.LOOP) / M.LOOP).toFloat()
        u.age = e.age.toFloat()
        u.steps = if (e.speed > 2) 40f else 64f
        u.loft = 1f
        u.px = (Theme.REF_W / w / density).toFloat()
        u.cardBottom = (layout.cardBottom / s).toFloat()
        u.radius = Theme.CARD_RADIUS.toFloat()
        u.peek = Theme.PEEK.toFloat()
        u.stretch = e.limit.value.toFloat()
        u.bloom = 0f
    }

    private fun drawShaders(c: Canvas, e: ExtrusionRecapStoryEngine, density: Float): Boolean {
        val bmp = atlas ?: return false
        if (Build.VERSION.SDK_INT < 33 || shaderFailed || !c.isHardwareAccelerated) return false
        if (main == null) {
            try {
                main = ExtrusionRecapStoryShader(bmp)
                bloomShader = ExtrusionRecapStoryShader(bmp)
            } catch (error: IllegalArgumentException) {
                Log.w("ExtrusionRecapStory", "Runtime shader unavailable; using the gradient fallback", error)
                shaderFailed = true
                return false
            }
        }
        val sh = main ?: return false
        fillUniforms(e, density)
        sh.apply(u)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), sh.paint)
        // the bloom: the same solid at a quarter of the size, blurred, very faint
        val bl = bloomShader
        val n = node
        if (!e.reduce && bl != null && n != null) {
            val wpx = ceil(w * density / 4).toInt().coerceAtLeast(1)
            val hpx = ceil(h * density / 4).toInt().coerceAtLeast(1)
            u.width = wpx.toFloat(); u.height = hpx.toFloat(); u.bloom = 1f
            bl.apply(u)
            n.setPosition(0, 0, wpx, hpx)
            val sigma = 2f * density
            if (sigma != blurSigma) {
                blurSigma = sigma
                n.setRenderEffect(RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP))
            }
            n.setAlpha(0.16f)
            val rc = n.beginRecording(wpx, hpx)
            try {
                rc.drawRect(0f, 0f, wpx.toFloat(), hpx.toFloat(), bl.paint)
            } finally {
                n.endRecording()
            }
            c.save()
            c.scale(4f / density, 4f / density)
            c.drawRenderNode(n)
            c.restore()
        }
        return true
    }

    /** Below API 33: the field as a gradient, the solid as offset copies of the glyph in the page's colours. */
    private fun drawFallback(c: Canvas, e: ExtrusionRecapStoryEngine) {
        val cc = max(1, count)
        val p = M.mod(e.progress.value, cc.toDouble())
        val i = floor(p).toInt()
        val n = (i + 1) % cc
        val q = (p - i).coerceIn(0.0, 1.0)
        fun mixC(a: Int, b: Int, t: Double): Int {
            fun ch(sh: Int) = (((a shr sh) and 255) + (((b shr sh) and 255) - ((a shr sh) and 255)) * t).roundToInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        val fa = mixC(pages[i].fieldA, pages[n].fieldA, M.smooth(0.25, 0.75, q))
        val fb = mixC(pages[i].fieldB, pages[n].fieldB, M.smooth(0.25, 0.75, q))
        fill.shader = LinearGradient(0f, 0f, 0f, layout.cardBottom.toFloat(), fa, fb, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        fill.shader = null
        val face = mixC(pages[i].face, pages[n].face, q)
        val dirx = pages[i].direction[0] + (pages[n].direction[0] - pages[i].direction[0]) * q
        val diry = pages[i].direction[1] + (pages[n].direction[1] - pages[i].direction[1]) * q
        val len = Math.hypot(dirx, diry)
        val (path, longest) = if (q < 0.5) glyphs[i] else glyphs[n]
        val k = (224.0 / longest * 360.0 / 256.0 * s).toFloat()
        val steps = 28
        for (z in steps downTo 0) {
            val t = z.toDouble() / steps
            val shade = 1.0 - 0.45 * t
            fill.color = mixC(0xFF000000.toInt(), face, shade * 0.9 + 0.1)
            c.save()
            c.translate(((Theme.HERO_X + dirx / len * 110 * t) * s).toFloat(), ((layout.heroY / s + diry / len * 110 * t) * s).toFloat())
            c.scale(k * (1f - 0.1f * t.toFloat()), k * (1f - 0.1f * t.toFloat()))
            c.drawPath(path, fill)
            c.restore()
        }
        fill.color = mixC(pages[n].fieldB, pages[(n + 1) % cc].fieldB, 0.0)
        val r = (Theme.CARD_RADIUS * s).toFloat()
        c.drawRoundRect(RectF(0f, (layout.cardBottom + Theme.GAP * s).toFloat(), w.toFloat(), (h + r).toFloat()), r, r, fill)
    }

    // ---- text helpers ---------------------------------------------------------------------------------------------------------

    private fun withAlpha(color: Int, a: Double) = (((color ushr 24) * M.clamp(a)).roundToInt() shl 24) or (color and 0xFFFFFF)

    private fun mixColor(a: Int, b: Int, t: Double): Int {
        fun ch(sh: Int) = (((a shr sh) and 255) + (((b shr sh) and 255) - ((a shr sh) and 255)) * t).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun baselineIn(top: Float, lineHeight: Float): Float {
        val fm = text.fontMetrics
        return top + (lineHeight - (fm.descent - fm.ascent)) / 2 - fm.ascent
    }

    /** Greedy word wrap of [str] (its own line breaks kept) to [maxWidth] with the current paint. */
    private fun wrap(str: String, maxWidth: Float): List<String> {
        val out = ArrayList<String>()
        for (para in str.split("\n")) {
            var line = ""
            for (word in para.split(" ")) {
                val cand = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && text.measureText(cand) > maxWidth) { out.add(line); line = word } else line = cand
            }
            out.add(line)
        }
        return out
    }

    private fun setTracking(sizePx: Float, trackingPx: Double) { text.letterSpacing = (trackingPx / sizePx).toFloat() }

    // ---- draw -----------------------------------------------------------------------------------------------------------------

    fun draw(c: Canvas, e: ExtrusionRecapStoryEngine, density: Float) {
        fill.shader = null
        fill.color = Theme.PAGE
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        if (atlas != null) {
            if (!drawShaders(c, e, density)) drawFallback(c, e)
            for (i in pages.indices) drawPageCopy(c, e, i)
            if (count > 1) for (i in pages.indices) drawPeek(c, e, i)
        }
        drawHeader(c, e)
    }

    private fun drawPageCopy(c: Canvas, e: ExtrusionRecapStoryEngine, i: Int) {
        val page = pages[i]
        val progress = e.progress.value
        val a = if (!e.reduce) M.pageAlpha(progress, i, count) else {
            val p = M.mod(progress, count.toDouble())
            val bi = floor(p).toInt()
            val q = p - bi
            if (i == bi) 1 - q else if (i == (bi + 1) % count) q else 0.0
        }
        val o = if (e.reduce) a else M.clamp((a - 0.2) / 0.8)
        val opacity = o * o * (3 - 2 * o)
        if (opacity <= 0.003) return
        val top = (if (page.stat != null && fontScale < 1.4) layout.titleY + 170 * s else layout.titleY).toFloat() +
            (if (e.reduce) 0f else (M.copyShift(a, e.age, page.stat != null) * s).toFloat())
        val left = (Theme.EDGE * s).toFloat()
        val width = (w - 2 * Theme.EDGE * s).toFloat()
        var y = top
        // the stat: digits that roll up into place as the page arrives
        if (page.stat != null) {
            val size = (78 * s).toFloat()
            val row = (size * 1.06 * fontScale).toFloat()
            text.typeface = display
            text.textSize = size
            text.letterSpacing = 0f
            text.fontFeatureSettings = "'tnum'"
            text.textAlign = Paint.Align.LEFT
            val digits = page.stat.toString()
            val widths = FloatArray(digits.length) { text.measureText(digits[it].toString()) }
            var x = left + (width - widths.sum()) / 2
            for ((order, d) in digits.withIndex()) {
                val arrival = if (e.reduce) 1.0 else M.digitArrival(progress, i, count, order)
                val dy = (-row * arrival).toFloat()
                c.save()
                c.clipRect(x, y, x + widths[order], y + row)
                text.color = withAlpha(Theme.INK, opacity)
                val lh = (size * 1.06).toFloat()
                val prevDigit = (d - '0' + 9) % 10
                c.drawText(prevDigit.toString(), x, baselineIn(y + dy, lh), text)
                c.drawText(d.toString(), x, baselineIn(y + dy + lh, lh), text)
                c.restore()
                x += widths[order]
            }
            text.fontFeatureSettings = ""
            y += row
        }
        // the headline
        val hs = ((if (page.stat != null) 32.0 else Theme.DISPLAY_SIZE) * s * fontScale).toFloat()
        val hl = ((if (page.stat != null) 34.0 else Theme.DISPLAY_LINE) * s).toFloat()
        text.typeface = display
        text.textSize = hs
        setTracking(hs, Theme.DISPLAY_TRACKING * s)
        text.textAlign = Paint.Align.CENTER
        val pass = if (e.reduce) 0.0 else M.highlightPass(e.age)
        text.color = withAlpha(mixColor(Theme.INK, Theme.LIGHT_INK, pass), opacity)
        for (line in wrap(page.headline, width)) {
            c.drawText(line, left + width / 2, baselineIn(y, hl), text)
            y += hl
        }
        text.letterSpacing = 0f
        // the caption
        if (page.caption != null) {
            y += (22 * s).toFloat()
            val cs = (Theme.CAPTION_SIZE * s * fontScale).toFloat()
            val cl = (19 * s).toFloat()
            text.typeface = body
            text.textSize = cs
            text.color = withAlpha(Theme.CAPTION, opacity)
            for (line in wrap(page.caption, width)) {
                c.drawText(line, left + width / 2, baselineIn(y, cl), text)
                y += cl
            }
        }
    }

    private fun peekTitle(page: ExtrusionRecapStoryPage): String {
        val headline = page.headline.replace("\n", " ")
        return if (page.stat != null) "${page.stat} $headline" else headline
    }

    private fun drawPeek(c: Canvas, e: ExtrusionRecapStoryEngine, i: Int) {
        val a = M.pageAlpha(e.progress.value, i, count)
        if (a <= 0.003) return
        val shift = if (e.reduce) 0.0 else M.peekShift(e.progress.value, i, count, e.age) * s
        val rowTop = peekTop + shift.toFloat()
        val rowH = layout.peekRow.toFloat()
        val pad = (Theme.EDGE * s).toFloat()
        val prefix = if (i == count - 1) Theme.REPLAY else Theme.UP_NEXT
        val title = peekTitle(pages[(i + 1) % count])
        text.textAlign = Paint.Align.LEFT
        text.letterSpacing = 0f
        text.typeface = body
        text.textSize = (13 * s).toFloat()
        text.color = withAlpha(Theme.PEEK_MUTE, a)
        val pw = text.measureText(prefix)
        c.drawText(prefix, pad, baselineIn(rowTop, rowH), text)
        text.typeface = display
        val ts = (15 * s).toFloat()
        text.textSize = ts
        setTracking(ts, -0.2 * s)
        text.color = withAlpha(Theme.PEEK_INK, a)
        val tx = pad + pw + (8 * s).toFloat()
        val avail = (w - pad - tx - (16 * s) - 0f).toFloat()
        var t = title
        if (text.measureText(t) > avail) {
            while (t.length > 1 && text.measureText("$t…") > avail) t = t.dropLast(1)
            t = "$t…"
        }
        c.drawText(t, tx, baselineIn(rowTop, rowH), text)
        text.letterSpacing = 0f
        // the chevron
        c.save()
        val k = (16 * s / 16).toFloat()
        c.translate((w - pad - 16 * s).toFloat(), rowTop + rowH / 2 - (8 * s).toFloat())
        c.scale(k, k)
        stroke.color = withAlpha(Theme.PEEK_INK, a)
        stroke.strokeWidth = 1.8f
        c.drawPath(chevron, stroke)
        c.restore()
    }

    private fun drawHeader(c: Canvas, e: ExtrusionRecapStoryEngine) {
        val ink = Theme.INK
        val rowTop = layout.headerY.toFloat()
        stroke.color = ink
        stroke.strokeWidth = 1.8f
        if (hasClose) {
            c.save()
            c.translate(closeRect.centerX() - 10, closeRect.centerY() - 10)
            c.drawPath(closePath, stroke)
            c.restore()
        }
        // the wordmark: Lilt (star) Music
        val bs = (Theme.BRAND_SIZE * s).toFloat()
        text.typeface = display
        text.textSize = bs
        text.letterSpacing = 0f
        text.textAlign = Paint.Align.LEFT
        text.color = ink
        val gap = (4 * s).toFloat()
        val star = (24 * s).toFloat()
        val lw = text.measureText(Theme.BRAND)
        val mw = text.measureText("Music")
        var x = ((w - (lw + gap + star + gap + mw)) / 2).toFloat()
        c.drawText(Theme.BRAND, x, baselineIn(rowTop, 44f), text)
        x += lw + gap
        c.save()
        c.translate(x, rowTop + 22 - star / 2)
        c.scale(star / 24f, star / 24f)
        fill.color = ink
        c.drawPath(starPath, fill)
        c.restore()
        x += star + gap
        c.drawText("Music", x, baselineIn(rowTop, 44f), text)
        // the count
        val cs = (Theme.COUNT_SIZE * s).toFloat()
        text.typeface = body
        text.textSize = cs
        text.textAlign = Paint.Align.RIGHT
        c.drawText("${e.activePage + 1} / $count", (w - Theme.EDGE * s).toFloat(), baselineIn(rowTop, 44f), text)
        // play / pause
        if (hasPlay) {
            val off = e.reduce || e.screenReader || e.paused
            c.save()
            c.translate(playRect.centerX() - 10, playRect.centerY() - 10)
            fill.color = ink
            c.drawPath(if (off) playPath else pausePath, fill)
            c.restore()
        }
    }
}
