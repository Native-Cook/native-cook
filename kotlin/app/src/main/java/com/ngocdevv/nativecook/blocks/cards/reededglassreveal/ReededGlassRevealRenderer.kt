package com.ngocdevv.nativecook.blocks.cards.reededglassreveal

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
import com.ngocdevv.nativecook.blocks.cards.reededglassreveal.ReededGlassRevealMotion as M
import com.ngocdevv.nativecook.blocks.cards.reededglassreveal.ReededGlassRevealTheme as Theme
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** The portrait and its two defocused copies (the glass refracts the soft one, the capsule's ends the hazed one). */
internal class ReededGlassRevealImages(val portrait: Bitmap, val soft: Bitmap, val haze: Bitmap)

/**
 * The screen in one canvas, in the RN order: the page, the glass over the portrait, the ground the capsule and the panel stand on, the capsule's
 * shadow, the panel, the capsule's glass; then (once the glass is ready) the capsule's card thumbnail, title, the number that resolves with the glass,
 * the swap button, the panel's title, plus button and rows. Everything is in dp: the caller scales the canvas by the density.
 */
internal class ReededGlassRevealRenderer(assets: AssetManager) {
    private val light = Typeface.createFromAsset(assets, "reededglassreveal/reededglassreveal_Urbanist-Light.ttf")
    private val regular = Typeface.createFromAsset(assets, "reededglassreveal/reededglassreveal_Urbanist-Regular.ttf")
    private var glassPass: Any? = null
    private var capsulePass: Any? = null
    private var passKey: Any? = null
    private val fallback by lazy { ReededGlassRevealFallback() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()
    private val clip = Path()

    private fun path(d: String): Path = try {
        PathParser.createPathFromPathData(d)
    } catch (error: RuntimeException) {
        Path()
    }

    private val swapPath = path("M1 4.3 H17 M1 4.3 L5.2 0.9 M17 7.7 H1 M17 7.7 L12.8 11.1")
    private val plusPath = path("M6.15 0.7 V11.6 M0.7 6.15 H11.6")
    private val vidlyPath = path("M8 5.2 L11.5 11 L15 5.2")
    private val loopPath = path("M7 11 C7 8.6 9.4 8.6 11 11 C12.6 13.4 15 13.4 15 11 C15 8.6 12.6 8.6 11 11 C9.4 13.4 7 13.4 7 11 Z")
    private val sablePath = path("M3.9 0.6 L1.6 2.3 H3.4 L1.1 4.4")
    private val netA = path("M2.75 0 H5.4 L3.6 5.5 H2.75 A2.75 2.75 0 0 1 2.75 0 Z")
    private val netB = path("M5.4 0 H6.25 A2.75 2.75 0 0 1 6.25 5.5 H3.6 Z")

    private class Glyph(val ch: Char, val x: Double, val group: Int)
    private class Glyphs(val digits: List<Glyph>, val stars: List<Double>, val clearC: Double, val restC: Double)

    /** the number's glyphs on the reference grid: x of each character, rest and cleared (Urbanist Light advances) */
    private val glyphs: Glyphs = run {
        val size = Theme.NUM_SIZE
        val out = ArrayList<Glyph>()
        var x = Theme.NUM_X
        Theme.FULL.forEachIndexed { gi, g ->
            for (ch in g) {
                out.add(Glyph(ch, x, gi))
                x += Theme.ADV.getValue(ch) * size
            }
            x += Theme.ADV.getValue(' ') * size
        }
        val clearC = out.first { it.group == 2 }.x
        val stars = ArrayList<Double>()
        var sx = Theme.STARS_X
        for (i in 0 until 4) {
            stars.add(sx)
            sx += Theme.ADV.getValue('*') * size
        }
        Glyphs(out, stars, clearC, Theme.NUM_REST_X2644)
    }

    // ---- the glass and the capsule ----

    private fun glass(c: Canvas, img: ReededGlassRevealImages, l: Theme.Layout, e: ReededGlassRevealEngine, glints: Double, area: FloatArray) {
        val look = e.look
        if (Build.VERSION.SDK_INT >= 33) {
            val pass = glassPassFor(img, l)
            if (pass != null) {
                pass.draw(c, area[0], area[1], area[2], area[3], look, e.reduce, glints)
                return
            }
        }
        fallback.glass(c, img.portrait, img.soft, l, look, area)
    }

    private fun glassPassFor(img: ReededGlassRevealImages, l: Theme.Layout): ReededGlassRevealGlassPass? {
        if (Build.VERSION.SDK_INT < 33) return null
        ensurePasses(img, l)
        return glassPass as? ReededGlassRevealGlassPass
    }

    private fun ensurePasses(img: ReededGlassRevealImages, l: Theme.Layout) {
        val key = Triple(img, l.w, l.h)
        if (passKey == key) return
        passKey = key
        glassPass = null
        capsulePass = null
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                glassPass = ReededGlassRevealGlassPass(img.portrait, img.soft, l)
                capsulePass = ReededGlassRevealCapsulePass(img.haze, l)
            } catch (error: IllegalArgumentException) {
                Log.w("ReededGlassReveal", "Runtime shader unavailable; using the Canvas fallback", error)
                glassPass = null
                capsulePass = null
            }
        }
    }

    // ---- the frame ----

    /** `contentAlpha` is the 220 ms fade-in of what waits for the glass; `shown` says it may be drawn at all. */
    fun draw(
        c: Canvas, w: Float, h: Float, l: Theme.Layout, e: ReededGlassRevealEngine, img: ReededGlassRevealImages?, shown: Boolean, contentAlpha: Float, glints: Double,
    ) {
        val s = l.s
        val look = e.look
        fill.shader = null
        fill.color = Theme.wall
        c.drawRect(0f, 0f, w, h, fill)
        val cap = floatArrayOf(l.x(Theme.CAPSULE.x).toFloat(), l.yb(Theme.CAPSULE.y).toFloat(), (Theme.CAPSULE.w * s).toFloat(), (Theme.CAPSULE.h * s).toFloat())
        // the glass ends behind the capsule: below its middle it melts into the dark ground the capsule and the panel stand on
        val groundTop = cap[1] + cap[3] * 0.3f
        val groundSolid = cap[1] + cap[3] - 2 * s.toFloat()
        if (img != null) {
            c.save()
            val areaH = ceil(groundSolid + 1.0).toFloat()
            c.clipRect(0f, 0f, w, areaH)
            glass(c, img, l, e, glints, floatArrayOf(0f, 0f, w, areaH))
            c.restore()
            // the ground, the capsule's shadow, the panel, the capsule's glass
            fill.shader = LinearGradient(0f, groundTop, 0f, groundSolid, Theme.groundClear, Theme.ground, Shader.TileMode.CLAMP)
            c.drawRect(0f, groundTop, w, h, fill)
            fill.shader = null
            val sigma = (Theme.SHADOW_BLUR * s).toFloat()
            fill.maskFilter = BlurMaskFilter(max(0.1f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
            fill.color = Color.argb((Theme.SHADOW_ALPHA * 255).toInt(), Color.red(Theme.capsuleShadow), Color.green(Theme.capsuleShadow), Color.blue(Theme.capsuleShadow))
            rect.set(cap[0] + 6 * s.toFloat(), cap[1] + (Theme.SHADOW_DY * s).toFloat(), cap[0] + 6 * s.toFloat() + cap[2] - 12 * s.toFloat(), cap[1] + (Theme.SHADOW_DY * s).toFloat() + cap[3])
            c.drawRoundRect(rect, (Theme.CAPSULE_R * s).toFloat(), (Theme.CAPSULE_R * s).toFloat(), fill)
            fill.maskFilter = null
            val panelY = l.yb(Theme.PANEL_Y).toFloat()
            fill.color = Theme.panel
            rect.set(l.x(Theme.PANEL_X).toFloat(), panelY, l.x(Theme.PANEL_X).toFloat() + (Theme.PANEL_W * s).toFloat(), h + 60f)
            c.drawRoundRect(rect, (Theme.PANEL_R * s).toFloat(), (Theme.PANEL_R * s).toFloat(), fill)
            val capEndL = cap[0] + 6.0
            val capEndR = cap[0] + cap[2] - 6.0
            val depthL = M.depthAt(look.depths, capEndL, l.edges)
            val depthR = M.depthAt(look.depths, capEndR, l.edges)
            val cp = capsulePass as? ReededGlassRevealCapsulePass
            if (Build.VERSION.SDK_INT >= 33 && cp != null) cp.draw(c, cap, look, depthL, depthR) else fallback.capsule(c, cap, l, look)
        }
        if (shown && img != null) {
            val layer = if (contentAlpha < 0.999f) c.saveLayerAlpha(0f, 0f, w, h, (255 * contentAlpha.coerceIn(0f, 1f)).toInt()) else -1
            content(c, l, e)
            if (layer >= 0) c.restoreToCount(layer)
        }
    }

    // ---- the content: capsule and panel ----

    private fun font(tf: Typeface, size: Double, track: Double, color: Int, alpha: Double = 1.0) {
        text.typeface = tf
        text.textSize = size.toFloat()
        text.letterSpacing = track.toFloat()
        text.color = color
        text.alpha = (alpha.coerceIn(0.0, 1.0) * Color.alpha(color)).toInt()
        text.fontFeatureSettings = null
    }

    private fun band(a: Double, b: Double, x: Double): Double {
        val v = min(1.0, max(0.0, (x - a) / (b - a)))
        return v * v * (3 - 2 * v)
    }

    /** 0 (masked) .. 1 (clear) for a glyph under glass of depth d; the digits' spring overshoot clamps away */
    private fun rollOf(d: Double): Double {
        val v = min(max((0.9 - d) / 0.8, 0.0), 1.0)
        return v * v * (3 - 2 * v)
    }

    /** how far "2644" has slid (0 masked place .. 1 its place in the full number), from the glass of rib 5 */
    private fun slideOf(depths: DoubleArray, edges: DoubleArray, x: Double) = min(1.1, max(-0.1, 1 - M.depthAt(depths, x, edges)))

    private fun content(c: Canvas, l: Theme.Layout, e: ReededGlassRevealEngine) {
        val s = l.s
        val look = e.look
        val reduce = e.reduce
        // the card thumbnail inside the capsule
        thumb(c, l, look)
        font(regular, Theme.TITLE_SIZE * s, Theme.TITLE_TRACK, Theme.ink)
        c.drawText(Theme.TITLE1, l.x(Theme.TITLE1_X).toFloat(), l.yb(Theme.TITLE1_BASE).toFloat(), text)
        number(c, l, look, reduce)
        // the swap button: a white disc; it scales while pressed (a dip in opacity with Reduce Motion), its glyph turns with the front
        val bx = l.x(Theme.SWAP_CX).toFloat()
        val by = l.yb(Theme.SWAP_CY).toFloat()
        val bd = (Theme.SWAP_D * s).toFloat()
        val heldV = e.held.value
        c.save()
        if (!reduce) {
            val k = (look.press * heldV).toFloat()
            c.scale(k, k, bx, by)
        }
        fill.color = Theme.swap
        if (reduce) fill.alpha = (255 * heldV.coerceIn(0.0, 1.0)).toInt()
        c.drawCircle(bx, by, bd / 2, fill)
        fill.alpha = 255
        c.save()
        if (!reduce) c.rotate(look.glyph.toFloat(), bx, by)
        icon(c, swapPath, bx, by, Theme.SWAP_GLYPH_W, Theme.SWAP_GLYPH_H, s, Theme.swapGlyph, Theme.SWAP_STROKE, if (reduce) heldV else 1.0)
        c.restore()
        c.restore()
        // the panel
        font(regular, Theme.TITLE_SIZE * s, Theme.TITLE_TRACK, Theme.ink)
        c.drawText(Theme.TITLE2, l.x(Theme.TITLE2_X).toFloat(), l.yb(Theme.TITLE2_BASE).toFloat(), text)
        val px = l.x(Theme.PLUS_CX).toFloat()
        val py = l.yb(Theme.PLUS_CY).toFloat()
        val pressed = e.plusPressed.value
        c.save()
        if (!reduce) {
            val k = pressed.toFloat()
            c.scale(k, k, px, py)
        }
        fill.color = Theme.plusWell
        if (reduce) fill.alpha = (255 * pressed.coerceIn(0.0, 1.0)).toInt()
        c.drawCircle(px, py, (Theme.PLUS_D * s / 2).toFloat(), fill)
        fill.alpha = 255
        icon(c, plusPath, px, py, Theme.PLUS_GLYPH, Theme.PLUS_GLYPH, s, Theme.ink, Theme.PLUS_STROKE, if (reduce) pressed else 1.0)
        c.restore()
        for ((i, row) in Theme.ROWS.withIndex()) {
            val g = Theme.ROWS_GEO[i]
            val wx = l.x(Theme.ROW_WELL_CX).toFloat()
            val wy = l.yb(g.cy).toFloat()
            fill.color = Theme.well
            c.drawCircle(wx, wy, (Theme.ROW_WELL_D * s / 2).toFloat(), fill)
            if (i == 0) vidly(c, wx, wy, s) else loop(c, wx, wy, s)
            val base = l.yb(g.base).toFloat()
            font(light, Theme.ROW_SIZE * s, 0.0, Theme.ink)
            c.drawText(row.name, l.x(Theme.ROW_NAME_X).toFloat(), base, text)
            font(light, Theme.ROW_SIZE * s, 0.0, Theme.mute)
            c.drawText(row.followers, l.x(Theme.ROW_FOLLOWERS_X).toFloat(), base, text)
            font(regular, Theme.ROW_SIZE * s, 0.0, Theme.ink)
            c.drawText(row.value, (l.x(Theme.ROW_VALUE_RIGHT) - text.measureText(row.value)).toFloat(), base, text)
        }
    }

    /** an icon on its own viewBox grid, centred at (cx, cy), scaled by S times `k`; strokes are in grid units */
    private fun icon(c: Canvas, p: Path, cx: Float, cy: Float, gw: Double, gh: Double, s: Double, color: Int, strokeW: Double, alpha: Double, k: Double = 1.0) {
        c.save()
        c.translate(cx - (gw * s * k / 2).toFloat(), cy - (gh * s * k / 2).toFloat())
        c.scale((s * k).toFloat(), (s * k).toFloat())
        stroke.color = color
        stroke.alpha = (255 * alpha.coerceIn(0.0, 1.0)).toInt()
        stroke.strokeWidth = strokeW.toFloat()
        c.drawPath(p, stroke)
        c.restore()
    }

    private fun vidly(c: Canvas, cx: Float, cy: Float, s: Double) {
        c.save()
        c.translate(cx - (Theme.MARK1_W * s / 2).toFloat(), cy - (Theme.MARK1_H * s / 2).toFloat())
        c.scale(s.toFloat(), s.toFloat())
        fill.color = Theme.vidly
        rect.set(0f, 0f, Theme.MARK1_W.toFloat(), Theme.MARK1_H.toFloat())
        c.drawRoundRect(rect, Theme.MARK1_R.toFloat(), Theme.MARK1_R.toFloat(), fill)
        stroke.color = Theme.ink
        stroke.alpha = 255
        stroke.strokeWidth = 2.1f
        c.drawPath(vidlyPath, stroke)
        c.restore()
    }

    private fun loop(c: Canvas, cx: Float, cy: Float, s: Double) {
        val d = Theme.MARK2_D
        c.save()
        c.translate(cx - (d * s / 2).toFloat(), cy - (d * s / 2).toFloat())
        c.scale(s.toFloat(), s.toFloat())
        fill.color = Theme.loopDisc
        c.drawCircle((d / 2).toFloat(), (d / 2).toFloat(), (d / 2).toFloat(), fill)
        stroke.strokeWidth = 1.7f
        stroke.alpha = 255
        for ((dx, dy, col) in listOf(Triple(-0.7f, -0.6f, Theme.loopCyan), Triple(0.7f, 0.6f, Theme.loopPink), Triple(0f, 0f, Theme.ink))) {
            c.save()
            c.translate(dx, dy)
            stroke.color = col
            c.drawPath(loopPath, stroke)
            c.restore()
        }
        c.restore()
    }

    /** the bank card thumbnail inside the capsule (Sable, frosted sage); decorative */
    private fun thumb(c: Canvas, l: Theme.Layout, look: ReededGlassRevealLook) {
        val s = l.s
        val t = Theme.THUMB
        val left = l.x(t.x).toFloat()
        val top = l.yb(t.y).toFloat()
        val tw = (t.w * s).toFloat()
        val th = (t.h * s).toFloat()
        val r = (Theme.THUMB_R * s).toFloat()
        rect.set(left, top, left + tw, top + th)
        c.save()
        clip.rewind()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        c.clipPath(clip)
        fill.shader = LinearGradient(left, top, left + tw, top + th, intArrayOf(Theme.thumbA, Theme.thumbB, Theme.thumbC), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(rect, fill)
        // the card's frost drops to 60 % when the glass above it is clear
        val frost = 0.6 + 0.4 * min(1.0, max(0.0, look.mean))
        fill.shader = LinearGradient(
            left + 0.1f * tw, top, left + 0.6f * tw, top + 0.7f * th, Color.argb((0.22 * frost * 255).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP,
        )
        c.drawRect(rect, fill)
        fill.shader = null
        fun put(str: String, tf: Typeface, size: Double, base: Double, x: Double, color: Int) {
            font(tf, size * s, 0.0, color)
            c.drawText(str, left + (x * s).toFloat(), top + (base * s).toFloat(), text)
        }
        put(Theme.THUMB_BRAND_TEXT, regular, 4.2, 13.6, 6.0, Theme.thumbInk)
        put(Theme.THUMB_GROUPS, regular, 4.0, 30.4, 6.0, Theme.thumbMute)
        put(Theme.THUMB_LAST4, regular, 6.3, 30.9, 37.5, Theme.thumbInk)
        put(Theme.THUMB_EXP_LABEL, regular, 3.8, 44.4, 6.0, Theme.thumbMute)
        put(Theme.THUMB_EXP, regular, 3.8, 49.9, 6.0, Theme.thumbInk)
        // the marks
        c.save()
        c.translate(left + (72.6 * s).toFloat(), top + (9 * s).toFloat())
        c.scale((s * 1.25).toFloat(), (s * 1.25).toFloat())
        stroke.color = Theme.thumbInk
        stroke.alpha = 255
        stroke.strokeWidth = 0.75f
        c.drawPath(sablePath, stroke)
        c.restore()
        c.save()
        c.translate(left + (68.2 * s).toFloat(), top + (42.5 * s).toFloat())
        c.scale((s * 1.32).toFloat(), (s * 1.32).toFloat())
        fill.color = Theme.netA
        c.drawPath(netA, fill)
        fill.color = Theme.netB
        c.drawPath(netB, fill)
        c.restore()
        c.restore()
        // a hairline border
        stroke.color = Color.argb((0.32 * 255).toInt(), 255, 255, 255)
        stroke.alpha = (0.32 * 255).toInt()
        stroke.strokeWidth = 0.5f
        rect.inset(0.25f, 0.25f)
        c.drawRoundRect(rect, r, r, stroke)
    }

    /** the number rolls glyph by glyph as the glass clears over it, like an odometer (see the RN comments) */
    private fun number(c: Canvas, l: Theme.Layout, look: ReededGlassRevealLook, reduce: Boolean) {
        val s = l.s
        val size = Theme.NUM_SIZE
        val edges = l.edges
        val depths = look.depths
        val base = l.yb(Theme.NUM_BASE)
        val restL = glyphs.restC
        val dxDesign = glyphs.clearC - glyphs.restC
        // stars roll up and out as the glass clears above them
        for (x in glyphs.stars) {
            val at = l.x(x + Theme.ADV.getValue('*') * size / 2)
            val p = rollOf(M.depthAt(depths, at, edges))
            val a = 1 - band(0.0, if (reduce) 0.5 else 0.55, p)
            font(light, size * s, 0.0, Theme.mute, a)
            c.drawText("*", l.x(x).toFloat(), (l.yb(Theme.NUM_BASE + Theme.STARS_DROP) + if (reduce) 0.0 else -p * 10 * s).toFloat(), text)
        }
        // the new glyph rolls up from below once its star has mostly gone; under a peek window it shows at 25 %
        val slideAt = l.x(110.0)
        for (d in glyphs.digits) {
            if (d.group == 2) continue
            val at = l.x(d.x + Theme.ADV.getValue(d.ch) * size / 2)
            val right = d.x + Theme.ADV.getValue(d.ch) * size
            val gateAt = if (right + 1.5 > restL) (right + 1.5 - restL) / dxDesign else -1.0
            var p = rollOf(M.depthAt(depths, at, edges))
            if (gateAt >= 0) p = min(p, band(min(gateAt, 0.9), min(gateAt + 0.1, 0.99), slideOf(depths, edges, slideAt)))
            val o = band(if (reduce) 0.5 else 0.4, 1.0, p)
            val a = min(o, 1 - 0.75 * look.peek)
            font(light, size * s, 0.0, Theme.mute, a)
            c.drawText(d.ch.toString(), l.x(d.x).toFloat(), (base + if (reduce) 0.0 else (1 - p) * 12 * s).toFloat(), text)
        }
        // "2644" slides from its masked place to its place in the full number, on the glass of rib 5
        if (reduce) {
            font(light, size * s, 0.0, Theme.mute, band(0.5, 1.0, look.mean))
            c.drawText(Theme.LAST4, l.x(glyphs.restC).toFloat(), base.toFloat(), text)
            font(light, size * s, 0.0, Theme.mute, 1 - band(0.0, 0.5, look.mean))
            c.drawText(Theme.LAST4, l.x(glyphs.clearC).toFloat(), base.toFloat(), text)
        } else {
            val slide = slideOf(depths, edges, slideAt)
            font(light, size * s, 0.0, Theme.mute)
            c.drawText(Theme.LAST4, (l.x(glyphs.restC) + slide * dxDesign * s).toFloat(), base.toFloat(), text)
        }
    }
}
