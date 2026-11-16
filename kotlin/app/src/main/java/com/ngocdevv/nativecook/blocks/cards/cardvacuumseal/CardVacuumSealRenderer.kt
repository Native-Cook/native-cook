package com.ngocdevv.nativecook.blocks.cards.cardvacuumseal

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.cards.cardvacuumseal.CardVacuumSealTheme as Theme
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The whole screen of the RN block drawn in one canvas, in the RN view order: the page and its violet bloom, the header, the three tiles
 * (the Protect tile is tugged at the lock, dips under a finger, and its disc, shield mark and label morph with the film's state), the
 * revenue panel, the recent-transactions panel, and last the card with its film over everything. The card is drawn once into a bitmap
 * (the film samples it, as RN snapshots the wrapped view). Everything is in dp: the caller scales the canvas by the density.
 * Text boxes follow the RN ones: a line's top is the design box top and its baseline is the font's ascent below it (no font padding).
 */
internal class CardVacuumSealRenderer(private val assets: AssetManager) {
    private val medium = Typeface.createFromAsset(assets, "cardvacuumseal/cardvacuumseal_HankenGrotesk-Medium.ttf")
    private val semi = Typeface.createFromAsset(assets, "cardvacuumseal/cardvacuumseal_HankenGrotesk-SemiBold.ttf")
    private val avatars: List<Bitmap?> = listOf("tidewell", "oren").map { decode("cardvacuumseal/cardvacuumseal_avatar-$it.png") }
    private val crease: Bitmap? = decode("cardvacuumseal/cardvacuumseal_cvs-creases.png")
    private val fallback by lazy { CardVacuumSealFallback() }

    private fun decode(name: String): Bitmap? = try {
        assets.open(name).use { BitmapFactory.decodeStream(it) }
    } catch (error: Exception) {
        Log.w("CardVacuumSeal", "Image unavailable: $name", error)
        null
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val clip = Path()

    private fun path(d: String): Path = try {
        PathParser.createPathFromPathData(d)
    } catch (error: RuntimeException) {
        Path()
    }

    private val backArrow = path("M19.5 12H5.2M11 6.2L5.2 12l5.8 5.8")
    private val closeGlyph = path("M7.5 7.5l9 9M16.5 7.5l-9 9")
    private val eye = path("M2.6 12c2.3-4.1 5.6-6.2 9.4-6.2s7.1 2.1 9.4 6.2c-2.3 4.1-5.6 6.2-9.4 6.2S4.9 16.1 2.6 12z")
    private val shield = path("M12 2.6l7.4 2.9c.5.2.8.6.8 1.1v4.9c0 4.6-3 8.6-7.6 10.1-.4.1-.8.1-1.2 0C6.8 20.1 3.8 16.1 3.8 11.5V6.6c0-.5.3-.9.8-1.1L12 2.6z")
    private val markOpen = path("M12 8.4v5.2")
    private val markSealed = path("M9.2 12.4l2 2 3.8-4.4")
    private val nut = path(
        "M10.6 3.3c.9-.5 1.9-.5 2.8 0l5.6 3.2c.9.5 1.4 1.4 1.4 2.4v6.2c0 1-.5 1.9-1.4 2.4l-5.6 3.2c-.9.5-1.9.5-2.8 0L5 17.5c-.9-.5-1.4-1.4-1.4-2.4V8.9c0-1 .5-1.9 1.4-2.4l5.6-3.2z",
    )

    // ---- the card, drawn once per size ----
    private var cardBitmap: Bitmap? = null
    private var cardKey = ""
    private var pass: Any? = null

    private fun cardFor(s: Double, density: Float): Bitmap {
        val key = "$s|$density"
        cardBitmap?.let { if (cardKey == key) return it }
        val wPx = ceil(Theme.CARD_W * s * density).toInt().coerceAtLeast(2)
        val hPx = ceil(Theme.CARD_H * s * density).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = (Theme.CARD_W * s).toFloat()
        val h = (Theme.CARD_H * s).toFloat()
        c.scale(wPx / w, hPx / h)
        val r = (Theme.RADIUS_CARD * s).toFloat()
        clip.rewind()
        rect.set(0f, 0f, w, h)
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        c.clipPath(clip)
        fill.shader = LinearGradient(
            -0.0089f * w, 0.6083f * h, 1.0088f * w, 0.3917f * h, intArrayOf(Theme.cardLight, Theme.cardMid, Theme.cardDark),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        // the wordmark and the holder, in the card's own Medium (not scaled by the system font size, like RN's maxFontSizeMultiplier 1)
        val x = (Theme.CARD_PAD_X * s).toFloat()
        font(medium, Theme.WORDMARK_SIZE * s, Theme.WORDMARK_TRACK, Theme.letter)
        c.drawText(Theme.BANK, x, lineTop((Theme.CARD_WORD_BASE - Theme.WORDMARK_SIZE) * s), text)
        font(medium, Theme.HOLDER_SIZE * s, Theme.HOLDER_TRACK, Theme.holderInk)
        c.drawText(Theme.HOLDER, x, lineTop((Theme.CARD_HOLDER_BASE - Theme.HOLDER_SIZE) * s), text)
        cardBitmap = bmp
        cardKey = key
        pass = null
        return bmp
    }

    private fun shaderPass(card: Bitmap): CardVacuumSealShaderPass? {
        if (Build.VERSION.SDK_INT < 33) return null
        (pass as? CardVacuumSealShaderPass)?.let { return it }
        return try {
            CardVacuumSealShaderPass(card, crease).also { pass = it }
        } catch (error: IllegalArgumentException) {
            Log.w("CardVacuumSeal", "Runtime shader unavailable; using the Canvas fallback", error)
            null
        }
    }

    // ---- text helpers ----
    /** the paint set to a font, a size in dp, a tracking in em and a colour (alpha kept) */
    private fun font(tf: Typeface, size: Double, track: Double, color: Int) {
        text.typeface = tf
        text.textSize = size.toFloat()
        text.letterSpacing = track.toFloat()
        text.color = color
        text.fontFeatureSettings = null
    }

    /** the baseline of a line whose box starts at `top` (RN text with includeFontPadding off: the first line sits its ascent below the top) */
    private fun lineTop(top: Double): Float = (top - text.fontMetrics.ascent).toFloat()

    /** the baseline of a line centred on `cy` in its own box */
    private fun lineCentre(cy: Double): Float {
        val fm = text.fontMetrics
        return (cy - (fm.descent - fm.ascent) / 2 - fm.ascent).toFloat()
    }

    private fun band(a: Double, b: Double, x: Double): Double {
        val v = min(1.0, max(0.0, (x - a) / (b - a)))
        return v * v * (3 - 2 * v)
    }

    private fun gradientDisc(c: Canvas, cx: Float, cy: Float, d: Float, colors: IntArray, x0: Float, y0: Float, x1: Float, y1: Float) {
        fill.shader = LinearGradient(cx - d / 2 + x0 * d, cy - d / 2 + y0 * d, cx - d / 2 + x1 * d, cy - d / 2 + y1 * d, colors, null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, d / 2, fill)
        fill.shader = null
    }

    /** a soft gloss on a disc: a white radial lobe upper left (centre .32 d / .26 d, radii .62 d / .55 d), as the key light would leave on lacquer */
    private fun gloss(c: Canvas, cx: Float, cy: Float, d: Float) {
        c.save()
        clip.rewind()
        clip.addCircle(cx, cy, d / 2, Path.Direction.CW)
        c.clipPath(clip)
        c.translate(cx - d / 2 + d * 0.32f, cy - d / 2 + d * 0.26f)
        c.scale(1f, 0.55f / 0.62f)
        fill.shader = RadialGradient(
            0f, 0f, d * 0.62f, intArrayOf(Color.argb(107, 255, 255, 255), Color.argb(20, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(-d, -d, d, d, fill)
        fill.shader = null
        c.restore()
    }

    /** an icon on the 24 x 24 grid, centred at (cx, cy) and drawn `size` dp wide; strokes are in grid units */
    private fun glyph(c: Canvas, p: Path, cx: Float, cy: Float, size: Float, filled: Boolean, color: Int, strokeW: Float = 0f, alpha: Double = 1.0) {
        c.save()
        c.translate(cx - size / 2, cy - size / 2)
        c.scale(size / 24, size / 24)
        if (filled) {
            fill.color = color
            c.drawPath(p, fill)
        } else {
            stroke.color = color
            stroke.alpha = (255 * alpha.coerceIn(0.0, 1.0)).toInt()
            stroke.strokeWidth = strokeW
            c.drawPath(p, stroke)
        }
        c.restore()
    }

    /** `fs` is the system font scale (capped at 1.3 by the caller, like RN's maxFontSizeMultiplier) */
    fun draw(c: Canvas, w: Float, h: Float, density: Float, l: Theme.Layout, e: CardVacuumSealEngine, screenAlpha: Float, fs: Double) {
        val s = l.s
        val sf = s.toFloat()
        val layer = if (screenAlpha < 0.999f) c.saveLayerAlpha(0f, 0f, w, h, (255 * screenAlpha.coerceIn(0f, 1f)).toInt()) else -1
        val look = e.look
        // ---- the page and the violet light the room throws on the upper left ----
        fill.color = Theme.page
        c.drawRect(0f, 0f, w, h, fill)
        c.save()
        c.scale(1f, 1.15f / 0.95f)
        val bloom = intArrayOf(
            Color.argb(51, Color.red(Theme.bloom), Color.green(Theme.bloom), Color.blue(Theme.bloom)),
            Color.argb(15, Color.red(Theme.bloom), Color.green(Theme.bloom), Color.blue(Theme.bloom)),
            Color.argb(0, Color.red(Theme.bloom), Color.green(Theme.bloom), Color.blue(Theme.bloom)),
        )
        fill.shader = RadialGradient(0f, 0f, w * 0.95f, bloom, floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        c.restore()

        // ---- header ----
        val hy = l.yt(Theme.HEADER_CY)
        glyph(c, backArrow, ((Theme.HEADER_ARROW_X - 11) * s + 22).toFloat(), hy.toFloat(), (Theme.HEADER_ARROW_SIZE * s).toFloat(), false, Theme.ink, 1.9f)
        font(semi, Theme.TITLE_SIZE * s * fs, Theme.TITLE_TRACK, Theme.ink)
        c.drawText(Theme.TITLE, (w - text.measureText(Theme.TITLE)) / 2, lineTop(hy - Theme.TITLE_SIZE * s * 0.62), text)
        val closeCx = ((Theme.HEADER_CLOSE_X + Theme.HEADER_CLOSE_D / 2) * s).toFloat()
        gradientDisc(c, closeCx, hy.toFloat(), (Theme.HEADER_CLOSE_D * s).toFloat(), intArrayOf(Theme.lilacLight, Theme.lilac, Theme.lilac), 0.2f, 0f, 0.8f, 1f)
        glyph(c, closeGlyph, closeCx, hy.toFloat(), 18f * sf, false, Theme.ink, 2.2f)

        // ---- tiles ----
        val tileW = (Theme.TILES_W * s).toFloat()
        val tugPt = (-CardVacuumSealMotion.TUG_PT * s).toFloat()
        val discD = (Theme.TILES_DISC_D * s).toFloat()
        for (i in 0..2) {
            val tx = ((Theme.CARD_X + i * (Theme.TILES_W + Theme.TILES_GAP)) * s).toFloat()
            val ty = l.yt(Theme.TILES_Y).toFloat()
            c.save()
            if (i == 1) {
                val k = (1 - 0.04 * look.press).toFloat()
                c.translate(0f, tugPt * look.tug.toFloat())
                c.scale(k, k, tx + tileW / 2, ty + tileW / 2)
            } else {
                c.translate(0f, tugPt * look.tugSide.toFloat())
            }
            rect.set(tx, ty, tx + tileW, ty + tileW)
            fill.color = Theme.tile
            val rt = (Theme.RADIUS_TILE * s).toFloat()
            c.drawRoundRect(rect, rt, rt, fill)
            val dcx = tx + tileW / 2
            val dcy = ty + (Theme.TILES_DISC_TOP * s).toFloat() + discD / 2
            when (i) {
                0 -> {
                    gradientDisc(c, dcx, dcy, discD, Theme.DISC_VIOLET, 0.15f, 0.1f, 0.85f, 0.95f)
                    gloss(c, dcx, dcy, discD)
                    glyph(c, eye, dcx, dcy, 24f * sf, true, Theme.ink)
                    fill.color = Color.rgb(0xB2, 0x5C, 0xF6)
                    c.drawCircle(dcx, dcy, 3.1f * sf, fill)
                    fill.color = Theme.ink
                    c.drawCircle(dcx, dcy, 1.15f * sf, fill)
                }
                1 -> protectDisc(c, dcx, dcy, discD, sf, e)
                else -> {
                    gradientDisc(c, dcx, dcy, discD, Theme.DISC_ICE, 0.2f, 0.05f, 0.8f, 1f)
                    gloss(c, dcx, dcy, discD)
                    glyph(c, nut, dcx, dcy, 26f * sf, true, Theme.ink)
                    fill.color = Theme.blue
                    c.drawCircle(dcx, dcy, 3.2f * 26f * sf / 24f, fill)
                }
            }
            // the label, in a row whose box starts at (labelBase - size) design pt
            font(medium, Theme.TILE_SIZE * s * fs, Theme.TILE_TRACK, Theme.ink)
            val labelBase = lineTop(ty + (Theme.TILES_LABEL_BASE - Theme.TILE_SIZE) * s)
            if (i == 1) {
                morphLabel(c, tx, labelBase, tileW, e)
            } else {
                val label = if (i == 0) Theme.DETAILS else Theme.SETTINGS
                c.drawText(label, tx + (tileW - text.measureText(label)) / 2, labelBase, text)
            }
            c.restore()
        }

        // ---- revenue ----
        val px = (Theme.CARD_X * s).toFloat()
        val pw = (Theme.CARD_W * s).toFloat()
        val ry = l.yt(Theme.REV_Y)
        val rh = (Theme.REV_H * s).toFloat()
        val rt = (Theme.RADIUS_TILE * s).toFloat()
        val shade = Theme.panelShade(Theme.REV_Y, Theme.REV_Y + Theme.REV_H)
        fill.shader = LinearGradient(0f, ry.toFloat(), 0f, ry.toFloat() + rh, shade[0], shade[1], Shader.TileMode.CLAMP)
        rect.set(px, ry.toFloat(), px + pw, ry.toFloat() + rh)
        c.drawRoundRect(rect, rt, rt, fill)
        fill.shader = null
        val bx = px + (Theme.REV_PAD_X * s).toFloat()
        val by = ry.toFloat() + (Theme.REV_BADGE_Y * s).toFloat()
        val bs = (Theme.REV_BADGE * s).toFloat()
        rect.set(bx, by, bx + bs, by + bs)
        fill.color = Theme.gold
        val rb = (Theme.RADIUS_BADGE * s).toFloat()
        c.drawRoundRect(rect, rb, rb, fill)
        font(semi, Theme.SMALL_SIZE * s * fs, 0.0, Theme.amberInk)
        c.drawText("$", bx + (bs - text.measureText("$")) / 2, lineCentre((by + bs / 2).toDouble()), text)
        font(medium, Theme.LABEL_SIZE * s * fs, Theme.LABEL_TRACK, Theme.ink)
        c.drawText(
            Theme.REVENUE, px + ((Theme.REV_PAD_X + Theme.REV_BADGE + 9) * s).toFloat(),
            lineTop(ry + (Theme.REV_BADGE_Y + Theme.REV_BADGE / 2) * s - Theme.LABEL_SIZE * s * 0.62), text,
        )
        font(medium, Theme.FIGURE_SIZE * s * fs, Theme.FIGURE_TRACK, Theme.ink)
        text.fontFeatureSettings = "'tnum'"
        c.drawText(Theme.REVENUE_VALUE, px + ((Theme.REV_PAD_X - 2) * s).toFloat(), lineTop(ry + (Theme.REV_FIGURE_BASE - Theme.FIGURE_SIZE * 0.92) * s), text)
        text.fontFeatureSettings = null
        fill.color = Theme.ink
        val n = Theme.BARS.size
        for ((i, bh) in Theme.BARS.withIndex()) {
            val x = px + ((Theme.REV_BARS_X0 - Theme.CARD_X + (Theme.REV_BARS_X1 - Theme.REV_BARS_X0) * i / (n - 1)) * s).toFloat()
            val base = ry.toFloat() + (Theme.REV_BARS_BASE * s).toFloat()
            if (bh <= 3) {
                c.drawCircle(x, base - 1.5f * sf, 1.6f * sf, fill)
            } else {
                rect.set(x - 1.5f * sf, base - (bh * s).toFloat(), x + 1.5f * sf, base)
                c.drawRoundRect(rect, 1.5f * sf, 1.5f * sf, fill)
            }
        }

        // ---- recent transactions (the panel runs on past the screen's bottom, like the pin's) ----
        val ly = l.yt(Theme.LIST_Y)
        val contentH = l.contentH
        val lh = ((contentH - ly) + 40 * s).toFloat()
        val lShade = Theme.panelShade(Theme.LIST_Y, Theme.LIST_Y + (contentH - ly) / s + 40)
        fill.shader = LinearGradient(0f, ly.toFloat(), 0f, ly.toFloat() + lh, lShade[0], lShade[1], Shader.TileMode.CLAMP)
        rect.set(px, ly.toFloat(), px + pw, ly.toFloat() + lh)
        c.drawRoundRect(rect, rt, rt, fill)
        fill.shader = null
        val padX = (Theme.LIST_PAD_X * s).toFloat()
        val headTop = ly + (Theme.LIST_HEAD_BASE - Theme.HEADING_SIZE * 0.95) * s
        // the header row aligns its two texts on one baseline: the taller (the heading) sets it
        font(semi, Theme.HEADING_SIZE * s * fs, Theme.HEADING_TRACK, Theme.ink)
        val headBase = lineTop(headTop)
        font(medium, Theme.LABEL_SIZE * s * fs, Theme.LABEL_TRACK, Theme.ink)
        val seeW = text.measureText(Theme.SEE_ALL)
        c.drawText(Theme.SEE_ALL, px + pw - padX - seeW, headBase, text)
        font(semi, Theme.HEADING_SIZE * s * fs, Theme.HEADING_TRACK, Theme.ink)
        var title = Theme.LIST
        val room = pw - padX * 2 - seeW - Theme.LIST_TEXT_GAP.toFloat()
        val natural = text.measureText(title)
        if (natural > room) text.textSize *= max(0.85f, room / natural)
        while (title.length > 1 && text.measureText(title) > room) title = title.dropLast(2) + "…"
        c.drawText(title, px + padX, headBase, text)
        for ((i, row) in Theme.ROWS.withIndex()) {
            val rowTop = ly + (Theme.LIST_ROW_Y + i * Theme.LIST_ROW_H) * s
            val a = (Theme.LIST_AVATAR * s).toFloat()
            avatars.getOrNull(i)?.let { av ->
                c.save()
                clip.rewind()
                clip.addCircle(px + padX + a / 2, rowTop.toFloat() + a / 2, a / 2, Path.Direction.CW)
                c.clipPath(clip)
                rect.set(px + padX, rowTop.toFloat(), px + padX + a, rowTop.toFloat() + a)
                c.drawBitmap(av, null, rect, bitmapPaint)
                c.restore()
            }
            val tx = px + padX + a + (12 * s).toFloat()
            val textRight = (px + pw - (Theme.LIST_PAD_X + Theme.LIST_AMOUNT_W) * s - Theme.LIST_TEXT_GAP).toFloat()
            font(medium, Theme.NAME_SIZE * s * fs, Theme.NAME_TRACK, Theme.ink)
            c.drawText(ellipsize(row.name, textRight - tx), tx, lineTop(rowTop + 2 * s), text)
            font(medium, Theme.SMALL_SIZE * s * fs, 0.0, Theme.inkMute)
            c.drawText(ellipsize(row.note, textRight - tx), tx, lineTop(rowTop + 24 * s), text)
            val right = px + pw - padX
            font(semi, Theme.AMOUNT_SIZE * s * fs, Theme.AMOUNT_TRACK, Theme.ink)
            text.fontFeatureSettings = "'tnum'"
            c.drawText(row.amount, right - text.measureText(row.amount), lineTop(rowTop + 2 * s), text)
            font(medium, Theme.SMALL_SIZE * s * fs, 0.0, Theme.green)
            c.drawText(row.status, right - text.measureText(row.status), lineTop(rowTop + 24 * s), text)
        }

        // ---- the card and its film, over everything (the sheet falls through the header's bottom and the tiles' top) ----
        val cardX = (Theme.CARD_X * s).toFloat()
        val cardY = l.yt(Theme.CARD_Y).toFloat()
        val cardW = (Theme.CARD_W * s).toFloat()
        val cardH = (Theme.CARD_H * s).toFloat()
        val card = cardFor(s, density)
        val sp = shaderPass(card)
        if (sp != null && Build.VERSION.SDK_INT >= 33) {
            val cw = ((Theme.PAD_L + Theme.CARD_W + Theme.PAD_R) * s).toFloat()
            val ch = ((Theme.PAD_T + Theme.CARD_H + Theme.PAD_B) * s).toFloat()
            c.save()
            c.translate(cardX - (Theme.PAD_L * s).toFloat(), cardY - (Theme.PAD_T * s).toFloat())
            val g = CardVacuumSealFilmGeom(
                s, Theme.PAD_L + Theme.CARD_W / 2, Theme.PAD_T + Theme.CARD_H / 2, Theme.CARD_W / 2, Theme.CARD_H / 2, Theme.RADIUS_CARD,
                Theme.FILM_LIP, Theme.FILM_EAR, card.width / Theme.CARD_W,
            )
            sp.draw(c, cw, ch, g, e, Theme.film)
            c.restore()
        } else {
            fallback.draw(c, card, cardX, cardY, cardW, cardH, sf, e)
        }
        if (layer >= 0) c.restoreToCount(layer)
    }

    private fun ellipsize(s: String, room: Float): String {
        if (text.measureText(s) <= room) return s
        var t = s
        while (t.length > 1 && text.measureText("$t…") > room) t = t.dropLast(1)
        return "$t…"
    }

    /** The Protect disc: red, the amber growing from the centre with the label's state, the pale flash on press, one glint, the shield. */
    private fun protectDisc(c: Canvas, cx: Float, cy: Float, d: Float, sf: Float, e: CardVacuumSealEngine) {
        val look = e.look
        gradientDisc(c, cx, cy, d, Theme.DISC_RED, 0.2f, 0.05f, 0.8f, 1f)
        val k = max(e.labelK.value, 0.001).toFloat()
        if (k > 0.002f) {
            c.save()
            c.scale(k, k, cx, cy)
            gradientDisc(c, cx, cy, d, Theme.DISC_AMBER, 0.2f, 0.05f, 0.8f, 1f)
            c.restore()
        }
        fill.color = Color.argb((255 * 0.75 * min(1.0, look.flash).coerceAtLeast(0.0)).toInt(), Color.red(Theme.redFlash), Color.green(Theme.redFlash), Color.blue(Theme.redFlash))
        c.drawCircle(cx, cy, d / 2, fill)
        fill.color = Color.argb((255 * 0.5 * min(1.0, look.discGlint).coerceAtLeast(0.0)).toInt(), 255, 255, 255)
        c.drawCircle(cx, cy, d / 2, fill)
        gloss(c, cx, cy, d)
        glyph(c, shield, cx, cy, 26f * sf, true, Theme.ink)
        val o = band(0.0, 0.45, e.labelK.value)
        c.save()
        c.scale(1f, (1 - 0.6 * o).toFloat(), cx, cy)
        glyph(c, markOpen, cx, cy, 26f * sf, false, Theme.red, 2.1f, 1 - o)
        c.restore()
        val kk = band(0.5, 1.0, e.labelK.value)
        c.save()
        val sc = (0.75 + 0.25 * kk).toFloat()
        c.scale(sc, sc, cx, cy)
        glyph(c, markSealed, cx, cy, 26f * sf, false, Theme.amberInk, 2.1f, kk)
        c.restore()
    }

    /** "Protect Card" <-> "Protected" as ONE word that changes its ending: the shared stem stays and slides to stay centred, the old ending drops out before the new one rises in. */
    private fun morphLabel(c: Canvas, tx: Float, base: Float, width: Float, e: CardVacuumSealEngine) {
        val from = Theme.PROTECT
        val to = Theme.PROTECTED
        var n = 0
        while (n < from.length && n < to.length && from[n] == to[n]) n++
        val stem = from.substring(0, n)
        val out = from.substring(n)
        val inn = to.substring(n)
        val ws = text.measureText(stem)
        val wa = text.measureText(out)
        val wb = text.measureText(inn)
        val k = e.labelK.value
        val dy = if (e.reduce) 0f else (5 * e.geom.s).toFloat()
        val left = ((width - ws - wa + (wa - wb) * band(0.1, 0.9, k)) / 2).toFloat()
        val baseAlpha = Color.alpha(text.color)
        c.drawText(stem, tx + left, base, text)
        val o = band(0.0, 0.45, k)
        text.alpha = (baseAlpha * (1 - o)).toInt()
        c.drawText(out, tx + left + ws, base + dy * o.toFloat(), text)
        val i = band(0.5, 1.0, k)
        text.alpha = (baseAlpha * i).toInt()
        c.drawText(inn, tx + left + ws, base - dy * (1 - i).toFloat(), text)
        text.alpha = baseAlpha
    }

}
