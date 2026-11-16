package com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve.ObjectSigninDissolveMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve.ObjectSigninDissolveTheme as Theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * The screen's drawing (RN `PageBack`, `PageFront`, the wipe, `PearLayer`), pass for pass: the dark page, the light page as one
 * layer cut to the wipe disc by a circle drawn into a dstIn layer (nothing of the dark page can peek out at its edge), then the pear.
 * Everything is in dp: the caller scales the canvas by the density.
 */
internal class ObjectSigninDissolveRenderer(assets: AssetManager) {
    private val serif = Typeface.createFromAsset(assets, "objectsignindissolve/objectsignindissolve_InstrumentSerif-Italic.ttf")
    private val regular = Typeface.createFromAsset(assets, "objectsignindissolve/objectsignindissolve_Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(assets, "objectsignindissolve/objectsignindissolve_Inter-Medium.ttf")
    private val semi = Typeface.createFromAsset(assets, "objectsignindissolve/objectsignindissolve_Inter-SemiBold.ttf")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        fontFeatureSettings = "'kern' 0, 'liga' 0"
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dstIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clipPath = Path()
    private val iconPath = Path()

    private val shaderPass: ObjectSigninDissolveShaderPass? = if (Build.VERSION.SDK_INT >= 33) {
        try {
            ObjectSigninDissolveShaderPass()
        } catch (error: IllegalArgumentException) {
            Log.w("ObjectSigninDissolve", "Runtime shader unavailable; using the Canvas fallback", error)
            null
        }
    } else null
    private val fallback by lazy { ObjectSigninDissolveFallback() }

    // ---- the layout, measured once per screen size ----
    private class Frame(
        val l: Theme.Layout,
        val cy: Double,
        val wordW: Float,
        val tagW: Float,
        val btnW: Float,
        val passW: Float,
        val phoneW: Float,
        val legal: List<Piece>,
        val legalBW: Float,
    )

    private class Piece(val s: String, val x: Float, val link: Boolean, val w: Float)

    private var frame: Frame? = null
    private var frameKey = ""

    private fun measure(tf: Typeface, size: Double, s: String): Float {
        text.typeface = tf
        text.textSize = size.toFloat()
        return text.measureText(s)
    }

    private fun frameFor(l: Theme.Layout, insetKey: String): Frame {
        val key = "${l.w}|${l.h}|$insetKey"
        frame?.let { if (frameKey == key) return it }
        val sc = l.s
        val pieces = listOf(Theme.LEGAL_A, Theme.TERMS, Theme.AND, Theme.PRIVACY)
        val widths = pieces.map { measure(regular, Theme.LEGAL_SIZE * sc, it) }
        var lx = l.w.toFloat() / 2 - widths.sum() / 2
        val laid = pieces.mapIndexed { i, p ->
            val x = lx
            lx += widths[i]
            Piece(p, x, i == 1 || i == 3, widths[i])
        }
        val f = Frame(
            l = l,
            cy = l.yc(Theme.PEAR_CY),
            wordW = measure(serif, Theme.WORDMARK_SIZE * sc, Theme.WORDMARK),
            tagW = measure(regular, Theme.TAGLINE_SIZE * sc, Theme.TAGLINE),
            btnW = measure(semi, Theme.BUTTON_SIZE * sc, Theme.CONTINUE),
            passW = measure(medium, Theme.HALF_SIZE * sc, Theme.PASSKEY),
            phoneW = measure(medium, Theme.HALF_SIZE * sc, Theme.PHONE),
            legal = laid,
            legalBW = measure(regular, Theme.LEGAL_SIZE * sc, Theme.LEGAL_B),
        )
        frame = f
        frameKey = key
        return f
    }

    private fun mixInt(a: Int, b: Int, t: Double): Int {
        val k = t.coerceIn(0.0, 1.0)
        fun ch(x: Int, y: Int) = (x + (y - x) * k).roundToInt()
        return Color.rgb(
            ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)),
        )
    }

    /**
     * One frame. `c` is scaled to dp; `density` is pixels per dp. `lightMix` / `reduce`: Reduce Motion's cross-fade of the light
     * page. `pressed`: the button being pressed (0..2) or -1.
     */
    fun draw(
        c: Canvas,
        l: Theme.Layout,
        insetKey: String,
        density: Float,
        engine: ObjectSigninDissolveEngine,
        pressed: Int,
    ) {
        val f = frameFor(l, insetKey)
        val w = l.w.toFloat()
        val h = l.h.toFloat()
        val k = engine.look
        val reduce = engine.reduce
        drawBack(c, f, 0, engine.glow)
        drawFront(c, f, 0, engine.glow, pressed)
        val wipeR = engine.wipeRadius().toFloat()
        if (wipeR > 0.5f) {
            val outer = if (reduce) c.saveLayerAlpha(0f, 0f, w, h, (255 * engine.lightMix.coerceIn(0.0, 1.0)).toInt()) else -1
            c.save()
            // a path clip a few points wider only skips the work while the disc is closed; the edge is the circle's own coverage
            clipPath.rewind()
            clipPath.addCircle(M.WIPE_CX.toFloat(), h + M.WIPE_DROP.toFloat(), wipeR + 3f, Path.Direction.CW)
            c.clipPath(clipPath)
            val layer = c.saveLayer(0f, 0f, w, h, null)
            drawBack(c, f, 1, engine.glow)
            drawFront(c, f, 1, engine.glow, pressed)
            c.saveLayer(0f, 0f, w, h, dstIn)
            circle.color = Color.WHITE
            c.drawCircle(M.WIPE_CX.toFloat(), h + M.WIPE_DROP.toFloat(), wipeR, circle)
            c.restore()
            c.restoreToCount(layer)
            c.restore()
            if (outer >= 0) c.restoreToCount(outer)
        }
        drawPear(c, f, w, h, k, engine, density)
    }

    private fun drawPear(c: Canvas, f: Frame, w: Float, h: Float, k: ObjectSigninDissolveLook, engine: ObjectSigninDissolveEngine, density: Float) {
        val s = f.l.s.toFloat()
        val cy = f.cy.toFloat()
        val reach = M.wipeReach(f.l.w, f.l.h)
        val wipeR = if (engine.reduce) 0f else M.wipeRadius(k.wipeP, reach).toFloat()
        val light = if (engine.reduce) engine.lightMix.toFloat() else 0f
        val pass = shaderPass
        if (pass != null && Build.VERSION.SDK_INT >= 33) {
            pass.draw(c, w, cy, s, h, k, engine.clock, wipeR, light, 1f / density)
        } else {
            fallback.draw(c, w, cy, s, h, k, engine.clock, wipeR, light, engine.reduce)
        }
    }

    // ---- the page, once per theme ----

    private fun drawBack(c: Canvas, f: Frame, i: Int, glow: ObjectSigninDissolveGlow) {
        val l = f.l
        val w = l.w.toFloat()
        val ground = Theme.ground[i]
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.color = ground
        c.drawRect(0f, 0f, w, l.h.toFloat(), fill)
        val base = Theme.pool[i]
        val tinted = mixInt(base, Theme.poolGlow[i], if (i == 0) 0.16 else 0.35)
        val poolColor = mixInt(base, tinted, glow.pool)
        val cx = w / 2
        val cy = f.cy.toFloat()
        val r = (Theme.POOL_R * l.s * 1.55).toFloat()
        fill.isDither = true
        fill.shader = RadialGradient(cx, cy, r, intArrayOf(poolColor, ground), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill)
        // one pulse when the skin closes at the stalk
        val z = glow.zip
        if (z >= 0 && z <= M.PULSE_POOL) {
            val u = z / M.PULSE_POOL
            val a = M.PULSE_A * (1 - u) * (1 - u)
            val pr = ((M.PULSE_R0 + (M.PULSE_R1 - M.PULSE_R0) * (1 - (1 - u) * (1 - u))) * l.s).toFloat()
            fill.shader = RadialGradient(cx, cy, pr, intArrayOf(Theme.poolGlow[i], Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            fill.alpha = (255 * a).roundToInt()
            c.drawCircle(cx, cy, pr, fill)
        }
        fill.shader = null
        fill.alpha = 255
        fill.isDither = false
    }

    private fun drawText(c: Canvas, tf: Typeface, size: Double, s: String, x: Float, y: Float, color: Int) {
        text.typeface = tf
        text.textSize = size.toFloat()
        text.shader = null
        text.color = color
        c.drawText(s, x, y, text)
    }

    private fun drawFront(c: Canvas, f: Frame, i: Int, glow: ObjectSigninDissolveGlow, pressed: Int) {
        val l = f.l
        val sc = l.s
        val w = l.w.toFloat()
        val ink = Theme.ink[i]
        val wordY = l.yt(Theme.WORDMARK_BASELINE).toFloat()
        val tagY = l.yt(Theme.TAGLINE_BASELINE).toFloat()
        val pillX = l.x(Theme.PILL_X).toFloat()
        val pillY = l.yb(Theme.PILL_Y).toFloat()
        val pillW = (Theme.PILL_W * sc).toFloat()
        val pillH = (Theme.PILL_H * sc).toFloat()
        val pillR = (Theme.PILL_R * sc).toFloat()
        val hw = (Theme.HALF_W * sc).toFloat()
        val hy = l.yb(Theme.HALF_Y).toFloat()
        val hh = (Theme.HALF_H * sc).toFloat()
        val hr = (Theme.HALF_R * sc).toFloat()
        val hx0 = l.x(Theme.PILL_X).toFloat()
        val hx1 = hx0 + pillW - hw

        // theme toggle: a moon on the dark page, a sun on the light one
        val themeX = l.x(Theme.THEME_CX).toFloat()
        val themeY = l.yt(Theme.THEME_CY).toFloat()
        fill.shader = null
        fill.color = Theme.themeBtn[i]
        c.drawCircle(themeX, themeY, (Theme.THEME_D / 2 * sc).toFloat(), fill)
        if (i == 0) {
            fill.color = Theme.moon
            c.drawCircle(themeX, themeY, 8.5f, fill)
            fill.color = Theme.themeBtn[0]
            c.drawCircle(themeX + 5f, themeY - 3.5f, 7.4f, fill)
        } else {
            fill.color = Theme.sun
            c.drawCircle(themeX, themeY, 5.4f, fill)
            iconPath.rewind()
            for (n in 0 until 8) {
                val a = n * PI / 4
                iconPath.moveTo(themeX + (cos(a) * 8.6).toFloat(), themeY + (sin(a) * 8.6).toFloat())
                iconPath.lineTo(themeX + (cos(a) * 11.2).toFloat(), themeY + (sin(a) * 11.2).toFloat())
            }
            stroke.color = Theme.sun
            stroke.strokeWidth = 1.6f
            c.drawPath(iconPath, stroke)
        }

        // wordmark: ink, then the shell's tint on its top as the skin comes off, then a sweep as the skin closes
        val wordX = w / 2 - f.wordW / 2
        val wordSize = Theme.WORDMARK_SIZE * sc
        drawText(c, serif, wordSize, Theme.WORDMARK, wordX, wordY, ink)
        val wordA = glow.word.coerceIn(0.0, 1.0) * 0.5
        if (wordA > 0.004) {
            val top = wordY - (wordSize * 0.7).toFloat()
            text.shader = LinearGradient(
                0f, top, 0f, top + (wordSize * 0.9).toFloat(),
                intArrayOf(Theme.shellWord, Color.argb(0, 255, 140, 200)), null, Shader.TileMode.CLAMP,
            )
            text.alpha = (255 * wordA).roundToInt()
            text.typeface = serif
            text.textSize = wordSize.toFloat()
            c.drawText(Theme.WORDMARK, wordX, wordY, text)
            text.shader = null
        }
        val z = glow.zip
        if (z >= 0 && z <= M.PULSE_WORD) {
            val sweepX = (w / 2 - f.wordW / 2 - 40 + (z / M.PULSE_WORD) * (f.wordW + 80)).toFloat()
            text.shader = LinearGradient(
                sweepX - 40f, 0f, sweepX + 40f, 0f,
                intArrayOf(Color.argb(0, 255, 255, 255), Color.WHITE, Color.argb(0, 255, 255, 255)), null, Shader.TileMode.CLAMP,
            )
            text.alpha = (255 * 0.5 * sin(PI * z / M.PULSE_WORD)).roundToInt()
            text.typeface = serif
            text.textSize = wordSize.toFloat()
            c.drawText(Theme.WORDMARK, wordX, wordY, text)
            text.shader = null
        }
        text.alpha = 255
        drawText(c, regular, Theme.TAGLINE_SIZE * sc, Theme.TAGLINE, w / 2 - f.tagW / 2, tagY, Theme.mute[i])

        // primary pill
        val pillCx = pillX + pillW / 2
        val pillCy = pillY + pillH / 2
        c.save()
        if (pressed == 0) c.scale(0.97f, 0.97f, pillCx, pillCy)
        fill.color = Theme.pill[i]
        rect.set(pillX, pillY, pillX + pillW, pillY + pillH)
        c.drawRoundRect(rect, pillR, pillR, fill)
        val edge = glow.pill.coerceIn(0.0, 1.0) * 0.6
        if (edge > 0.004) {
            c.save()
            clipPath.rewind()
            clipPath.addRoundRect(rect, pillR, pillR, Path.Direction.CW)
            c.clipPath(clipPath)
            fill.color = Theme.shellPill
            fill.alpha = (255 * edge).roundToInt()
            c.drawRect(pillX, pillY, pillX + pillW, pillY + 1.2f, fill)
            fill.alpha = 255
            c.restore()
        }
        icon(c, 0, pillCx - (f.btnW / 2 + 17), pillCy, 1.7f, Theme.pillInk[i])
        drawText(c, semi, Theme.BUTTON_SIZE * sc, Theme.CONTINUE, pillCx - f.btnW / 2 + 12, pillCy + (5.6 * sc).toFloat(), Theme.pillInk[i])
        c.restore()

        // two half buttons
        for (n in 0..1) {
            val x = if (n == 0) hx0 else hx1
            val tw = if (n == 0) f.passW else f.phoneW
            val label = if (n == 0) Theme.PASSKEY else Theme.PHONE
            val cx = x + hw / 2
            val cy = hy + hh / 2
            c.save()
            if (pressed == n + 1) c.scale(0.97f, 0.97f, cx, cy)
            fill.color = Theme.half[i]
            rect.set(x, hy, x + hw, hy + hh)
            c.drawRoundRect(rect, hr, hr, fill)
            icon(c, n + 1, cx - (tw / 2 + 14), cy, 1.6f, Theme.halfInk[i])
            drawText(c, medium, Theme.HALF_SIZE * sc, label, cx - tw / 2 + 12, cy + (5 * sc).toFloat(), Theme.halfInk[i])
            c.restore()
        }

        // legal
        val y1 = l.yb(Theme.LEGAL_Y1).toFloat()
        val y2 = l.yb(Theme.LEGAL_Y2).toFloat()
        for (p in f.legal) {
            drawText(c, regular, Theme.LEGAL_SIZE * sc, p.s, p.x, y1, if (p.link) Theme.mute[i] else Theme.legal[i])
            if (p.link) {
                fill.color = Theme.legal[i]
                c.drawRect(p.x, y1 + 2.2f, p.x + p.w, y1 + 2.2f + 0.9f, fill)
            }
        }
        drawText(c, regular, Theme.LEGAL_SIZE * sc, Theme.LEGAL_B, w / 2 - f.legalBW / 2, y2, Theme.legal[i])
    }

    /** The three button glyphs: mail, key, phone (0, 1, 2), 1.6 to 1.7 pt strokes, drawn about (x, y). */
    private fun icon(c: Canvas, kind: Int, x: Float, y: Float, width: Float, color: Int) {
        iconPath.rewind()
        when (kind) {
            0 -> {
                rect.set(x - 8, y - 6, x + 8, y + 6)
                iconPath.addRoundRect(rect, 2.5f, 2.5f, Path.Direction.CW)
                iconPath.moveTo(x - 7, y - 4.2f)
                iconPath.lineTo(x, y + 1)
                iconPath.lineTo(x + 7, y - 4.2f)
            }
            1 -> {
                iconPath.addCircle(x - 4, y, 4f, Path.Direction.CW)
                iconPath.moveTo(x, y)
                iconPath.lineTo(x + 8, y)
                iconPath.moveTo(x + 5, y)
                iconPath.lineTo(x + 5, y + 3.5f)
                iconPath.moveTo(x + 8, y)
                iconPath.lineTo(x + 8, y + 3)
            }
            else -> {
                rect.set(x - 5, y - 8, x + 5, y + 8)
                iconPath.addRoundRect(rect, 2.6f, 2.6f, Path.Direction.CW)
                iconPath.moveTo(x - 1.6f, y + 5)
                iconPath.lineTo(x + 1.6f, y + 5)
            }
        }
        stroke.color = color
        stroke.strokeWidth = width
        c.drawPath(iconPath, stroke)
    }
}
