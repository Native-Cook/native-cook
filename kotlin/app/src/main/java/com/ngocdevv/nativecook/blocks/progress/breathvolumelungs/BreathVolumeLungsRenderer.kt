package com.ngocdevv.nativecook.blocks.progress.breathvolumelungs

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import com.ngocdevv.nativecook.blocks.progress.breathvolumelungs.BreathVolumeLungsMotion as M
import com.ngocdevv.nativecook.blocks.progress.breathvolumelungs.BreathVolumeLungsTheme as Theme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Draws the whole screen onto a Canvas whose unit is dp (the composable scales it by the density). */
internal class BreathVolumeLungsRenderer(assets: AssetManager, private val l: Theme.Layout, private val reduce: Boolean, private val fontScale: Double) {
    private val regular = Typeface.createFromAsset(assets, "breathvolumelungs/breathvolumelungs_Urbanist-Regular.ttf")
    private val medium = Typeface.createFromAsset(assets, "breathvolumelungs/breathvolumelungs_Urbanist-Medium.ttf")
    private val fields = BreathVolumeLungsFields.make()
    private val atlas: Bitmap = fields.toBitmap()
    private val s = l.s
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val rect = RectF()
    private val resetIcon: Path = PathParser().parsePathString("M23 11A9 9 0 0 0 7 7L4 10M4 4v6h6M5 17a9 9 0 0 0 16 4l3-3m0 6v-6h-6").toPath().asAndroidPath()
    private val closeIcon: Path = PathParser().parsePathString("m4 4 12 12M16 4 4 16").toPath().asAndroidPath()
    private val heatA = Theme.rgb(Theme.HEAT0)
    private val heatB = Theme.rgb(Theme.HEAT1)
    private val heatC = Theme.rgb(Theme.HEAT2)
    private val ground3 = Theme.rgb(Theme.GROUND)
    private val grid3 = Theme.rgb(Theme.GRID)
    private val dot3 = Theme.rgb(Theme.DOT)
    private val line3 = Theme.rgb(Theme.LINE)
    private val knob3 = Theme.rgb(Theme.KNOB_HIGH)
    private val crossings = M.litreTimes(Theme.CAPACITY)

    private val panel = floatArrayOf(l.cardX.toFloat(), 0f, l.cardW.toFloat(), l.cardH.toFloat())
    private val lattice = floatArrayOf((l.w / fields.cols).toFloat(), (l.pitchY * 32 / fields.cols).toFloat(), l.heroTop.toFloat(), fields.cols.toFloat())
    private val atlasInfo = floatArrayOf(fields.cols.toFloat(), fields.rows.toFloat(), fields.width.toFloat(), (Theme.DOT_CORE * s).toFloat())
    private val readout = floatArrayOf(l.digitCx.toFloat(), l.digitCy.toFloat(), l.digitDx.toFloat(), l.digitDy.toFloat())

    private var shader: RuntimeShader? = null
    private var shaderFailed = false
    private val shaderPaint = Paint()
    private var packedTenth = Int.MIN_VALUE
    private var packed = FloatArray(40)
    private var packedFrom = ""
    private var packedTo = ""

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun ensureShader(): RuntimeShader? {
        if (Build.VERSION.SDK_INT < 33 || shaderFailed) return null
        shader?.let { return it }
        return try {
            val rs = RuntimeShader(BreathVolumeLungsShaders.MAIN)
            rs.setInputShader("atlas", BitmapShader(atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            shaderPaint.shader = rs
            shader = rs
            rs
        } catch (t: Throwable) {
            Log.w("BreathVolumeLungs", "AGSL unavailable, using the gradient fallback", t)
            shaderFailed = true
            null
        }
    }

    private class U(
        val value: Double, val now: Double, val p: Double, val mix: Double, val packAge: Double, val heat: DoubleArray,
        val lastAir: Double, val flow: Double, val a: String, val b: String,
    )

    private fun uniforms(e: BreathVolumeLungsEngine): U {
        val value = M.clamp(e.p) * e.maxVolume
        val now = e.clock % M.LOOP
        val tenth = floor(value * 10 + 0.00001).toInt()
        val mix = M.smooth((value * 10 - tenth) / 0.75)
        if (tenth != packedTenth) {
            packedTenth = tenth
            packedFrom = fmt(tenth / 10.0).takeLast(4)
            packedTo = fmt(min(e.maxVolume, (tenth + 1) / 10.0)).takeLast(4)
            packed = BreathVolumeLungsGlyphs.packReadout(packedFrom, packedTo)
        }
        var packAge = -1.0
        if (e.phase == 0 || e.phase == 6) {
            for (c in crossings) if (now >= c && now - c < 0.24) packAge = now - c
        } else packAge = e.clock - e.lastPack
        val lastAir = if (reduce) 0.0 else if (e.phase == 0 || e.phase == 6) M.lastAirAt(now) else 1 - M.smooth((e.clock - e.airPeak) / 0.4)
        return U(value, now, M.clamp(e.p), mix, packAge, M.heatAt(M.clamp(e.p), heatA, heatB, heatC), lastAir, e.flow, packedFrom, packedTo)
    }

    fun draw(c: Canvas, e: BreathVolumeLungsEngine) {
        val u = uniforms(e)
        val cardY = l.cardY
        panel[1] = cardY.toFloat()
        val rs = ensureShader()
        if (rs != null) drawShader(c, e, u, rs) else drawFallback(c, e, u)
        drawChrome(c, e, u)
    }

    private fun f3(rs: RuntimeShader, name: String, v: DoubleArray) = rs.setFloatUniform(name, v[0].toFloat(), v[1].toFloat(), v[2].toFloat())

    private fun drawShader(c: Canvas, e: BreathVolumeLungsEngine, u: U, rs: RuntimeShader) {
        rs.setFloatUniform("resolution", l.w.toFloat(), l.h.toFloat())
        rs.setFloatUniform("lattice", lattice)
        rs.setFloatUniform("atlasInfo", atlasInfo)
        rs.setFloatUniform("readout", readout)
        rs.setFloatUniform("panel", panel)
        val now = u.now
        rs.setFloatUniform(
            "state", u.p.toFloat(), (if (reduce) 0.0 else e.flow).toFloat(), (if (reduce) 0.0 else now).toFloat(),
            (if (reduce) 0.0 else M.breatheAt(now)).toFloat(),
        )
        rs.setFloatUniform(
            "feedback", u.packAge.toFloat(), e.pressed.x.toFloat(), (if (reduce) 0.0 else e.overshoot.x).toFloat(), if (reduce) 1f else 0f,
        )
        rs.setFloatUniform("knobInfo", e.knobX0.toFloat(), e.knobX1.toFloat(), e.knobY.toFloat(), (Theme.KNOB_R * s).toFloat())
        rs.setFloatUniform("glyphRows", packed.copyOfRange(0, 32))
        rs.setFloatUniform("glyphX", packed.copyOfRange(32, 36))
        rs.setFloatUniform("glyphKind", packed.copyOfRange(36, 40))
        rs.setFloatUniform("glyphMix", u.mix.toFloat())
        f3(rs, "ground", ground3)
        f3(rs, "gridInk", grid3)
        f3(rs, "dotInk", dot3)
        f3(rs, "heatInk", u.heat)
        f3(rs, "lineInk", line3)
        f3(rs, "knobInk", knob3)
        rs.setFloatUniform("p3", 0f)
        rs.setFloatUniform("lastAir", u.lastAir.toFloat())
        c.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), shaderPaint)
    }

    // ---------- fallback below API 33: the same lattice, drawn with plain canvas calls ----------
    private fun argb(v: DoubleArray, a: Double = 1.0): Int =
        (((a.coerceIn(0.0, 1.0) * 255).toInt()) shl 24) or ((v[0].coerceIn(0.0, 1.0) * 255).toInt() shl 16) or
            ((v[1].coerceIn(0.0, 1.0) * 255).toInt() shl 8) or (v[2].coerceIn(0.0, 1.0) * 255).toInt()

    private fun cell(i: Int, j: Int, ch: Int, glow: Boolean = false): Double {
        val off = (j * fields.width + i + (if (glow) fields.cols else 0)) * 4 + ch
        return (fields.bytes[off].toInt() and 255) / 255.0
    }

    private fun pileThresholdAt(p: Double): Double {
        val f = M.clamp(p).pow(0.8) * 63
        val ia = floor(f).toInt()
        val ib = min(63, ia + 1)
        fun t(n: Int): Double {
            val off = (fields.rows * fields.width + n) * 4
            return (((fields.bytes[off].toInt() and 255) * 256 + (fields.bytes[off + 1].toInt() and 255)) / 65535.0)
        }
        val fr = f - floor(f)
        return (t(ia) * (1 - fr) + t(ib) * fr) * 1.22 - 0.22
    }

    private fun drawFallback(c: Canvas, e: BreathVolumeLungsEngine, u: U) {
        c.drawColor(argb(ground3))
        val cols = fields.cols
        val pitchX = lattice[0].toDouble()
        val pitchY = lattice[1].toDouble()
        val topRow = 7.0 * cols / 32
        val reducedF = if (reduce) 1.0 else 0.0
        val th = M.thresholdAt(u.p, if (reduce) 0.0 else M.breatheAt(u.now))
        val pth = pileThresholdAt(M.clamp(if (reduce) 1.0 else u.p))
        val dotR = atlasInfo[3].toDouble()
        val gridCol = argb(doubleArrayOf(ground3[0] + grid3[0], ground3[1] + grid3[1], ground3[2] + grid3[2]))
        for (j in 0 until fields.rows) {
            val cy = l.heroTop + (j - topRow + 0.5) * pitchY
            if (cy < -pitchY || cy > l.h) continue
            for (i in 0 until cols) {
                val cx = (i + 0.5) * pitchX
                fill.color = gridCol
                c.drawCircle(cx.toFloat(), cy.toFloat(), (0.95 * s).toFloat(), fill)
                val body = cell(i, j, 0)
                val rad = cell(i, j, 1)
                val pressure = if (reduce) 1.0 else M.smooth((body - th) / 0.22)
                val reducedAlpha = 1 - u.p * reducedF
                val lit = pressure * rad * reducedAlpha
                if (lit > 0.02) {
                    fill.color = argb(dot3, lit)
                    c.drawCircle(cx.toFloat(), cy.toFloat(), (dotR * rad * pressure).toFloat().coerceAtLeast(0.2f), fill)
                }
                val pileB = cell(i, j, 2)
                val sparse = cell(i, j, 2, true)
                val rf = max(M.smooth((pileB - pth) / 0.22) * (if (pileB > 0.002) 1.0 else 0.0), sparse)
                if (rf > 0.02) {
                    val a = rf * (1 + (max(u.p, sparse) - 1) * reducedF)
                    fill.color = argb(dot3, a)
                    c.drawCircle(cx.toFloat(), cy.toFloat(), (dotR * rf).toFloat(), fill)
                }
            }
        }
        // two outlet streams
        if (!reduce && abs(u.flow) > 0.01) {
            val outlet = floor(11.0 * cols / 32 + 0.5)
            val xs = doubleArrayOf((outlet + 0.5) * pitchX, (cols - outlet - 0.5) * pitchX)
            val top = l.heroTop + (17.5 - 7.0) * pitchY
            val end = l.heroTop + (29.0 - 7.0) * pitchY
            val dir = if (u.flow >= 0) 1.0 else -1.0
            val a = (0.75 * min(1.0, abs(u.flow) * 4)).coerceIn(0.0, 1.0)
            fill.color = argb(dot3, a)
            var n = floor(top / pitchY).toInt() - 1
            while (n * pitchY < end + pitchY) {
                val y = pitchY * (n + 0.5 + (u.now * dir * 11.0) % 1.0)
                if (y in top..end) for (x in xs) c.drawCircle(x.toFloat(), y.toFloat(), (dotR * 0.85).toFloat(), fill)
                n++
            }
        }
        // readout
        val masks = BreathVolumeLungsGlyphs.MASKS
        val str = if (u.mix > 0.5) u.b else u.a
        val adv = str.map { if (it == '.') BreathVolumeLungsGlyphs.ADVANCE_POINT else BreathVolumeLungsGlyphs.ADVANCE_DIGIT }
        val total = adv.sum()
        var acc = 0.0
        fill.color = argb(dot3)
        str.forEachIndexed { idx, ch ->
            val center = acc + adv[idx] / 2 - total / 2
            acc += adv[idx]
            val g = BreathVolumeLungsGlyphs.glyphIndex(ch.code)
            for (r in 0 until 7) {
                val m = masks[g * 7 + r]
                for (b in 0 until 9) if ((m shr b) and 1 == 1) {
                    val x = l.digitCx + (center + (b - 4) * 0.5) * l.digitDx
                    val y = l.digitCy + (r - 3) * l.digitDy
                    c.drawCircle(x.toFloat(), y.toFloat(), (Theme.DIGIT_R * s).toFloat(), fill)
                }
            }
        }
        // card: layered gradients stand in for the glass
        val rr = (Theme.CARD_R * s).toFloat()
        rect.set(l.cardX.toFloat(), l.cardY.toFloat(), (l.cardX + l.cardW).toFloat(), (l.cardY + l.cardH).toFloat())
        val top = l.cardY.toFloat()
        val h = l.cardH.toFloat()
        fill.shader = LinearGradient(
            0f, top, 0f, top + h,
            intArrayOf(argb(doubleArrayOf(0.42, 0.40, 0.64)), argb(doubleArrayOf(0.294, 0.282, 0.545)), argb(doubleArrayOf(0.0745, 0.106, 0.361)), argb(doubleArrayOf(0.008, 0.010, 0.066)), argb(doubleArrayOf(0.008, 0.010, 0.066))),
            floatArrayOf(0f, 0.05f, 0.15f, 0.40f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(rect, rr, rr, fill)
        fill.shader = LinearGradient(
            0f, top + h * 0.6f, 0f, top + h,
            argb(u.heat, 0.0), argb(u.heat, (0.24 + 0.45 * u.p).coerceIn(0.0, 1.0) * 0.8), Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(rect, rr, rr, fill)
        fill.shader = null
        stroke.strokeWidth = (1.1 * s).toFloat()
        stroke.color = 0x66 shl 24 or 0x7C6CF0
        c.drawRoundRect(rect, rr, rr, stroke)
        // track, breath line, knob
        val ky = e.knobY.toFloat()
        val kx = (e.knobX0 + (e.knobX1 - e.knobX0) * u.p * (1 - reducedF)).toFloat()
        fill.color = 0x99 shl 24 or 0x595A8C
        var tx = e.knobX0
        while (tx < l.cardX + l.cardW - 20 * s) { if (tx > l.cardX + 32 * s) c.drawCircle(tx.toFloat(), ky, (0.55 * s).toFloat(), fill); tx += 6.6 * s }
        val lineEnd = min(kx + 93 * s, l.cardX + l.cardW - 24 * s).toFloat()
        stroke.color = Theme.LINE
        stroke.strokeWidth = (0.9 * s).toFloat()
        c.drawLine(kx - (42 * s).toFloat(), ky, lineEnd, ky, stroke)
        fill.color = Theme.LINE
        c.drawCircle(lineEnd, ky, (2.6 * s).toFloat(), fill)
        val kr = (Theme.KNOB_R * s * (1 - 0.06 * e.pressed.x)).toFloat()
        fill.color = 0xFF0B0D16.toInt()
        c.drawCircle(kx, ky, kr, fill)
        c.save()
        c.clipRect(kx - kr, ky - kr, kx, ky + kr)
        fill.color = argb(knob3)
        c.drawCircle(kx, ky, kr, fill)
        c.restore()
        stroke.color = 0xCCBABCD4.toInt()
        stroke.strokeWidth = (0.6 * s).toFloat()
        c.drawCircle(kx, ky, kr + (3.5 * s).toFloat(), stroke)
    }

    // ---------- text and the two buttons (shared by both paths) ----------
    private fun fit(str: String, tf: Typeface, size: Double, maxW: Double): Float {
        text.typeface = tf
        text.textSize = size.toFloat()
        val w = text.measureText(str)
        return if (w > maxW && w > 0) (size * maxW / w).toFloat() else size.toFloat()
    }

    private fun drawText(
        c: Canvas, str: String, tf: Typeface, size: Double, color: Int, alpha: Double, align: Paint.Align, anchorX: Double, top: Double,
        maxW: Double = Double.MAX_VALUE, tabular: Boolean = false,
    ) {
        if (alpha <= 0.003) return
        text.typeface = tf
        text.fontFeatureSettings = if (tabular) "tnum" else null
        text.textSize = fit(str, tf, size, maxW)
        text.color = color
        text.alpha = (((color ushr 24) and 255) * alpha.coerceIn(0.0, 1.0)).toInt()
        text.textAlign = align
        c.drawText(str, anchorX.toFloat(), (top - text.ascent()).toFloat(), text)
    }

    private fun drawChrome(c: Canvas, e: BreathVolumeLungsEngine, u: U) {
        val fa = e.fontAlpha()
        val k = l.k
        val cardY = l.cardY
        val ink = Theme.TITLE
        // title and unit
        drawText(c, Theme.TITLE_TEXT, medium, l.titleSize, ink, fa, Paint.Align.CENTER, l.w / 2, l.titleBase - l.titleSize, l.w - 40 * s)
        drawText(c, Theme.UNIT, regular, l.labelSize, ink, fa, Paint.Align.CENTER, l.labelCx, l.labelBase - l.labelSize, l.w - 160 * s)
        // card counters
        val numSize = Theme.FONT_CARD * s * min(1.0, 1.25 / max(1.0, fontScale))
        val numTop = cardY + (Theme.LEFT_TOP - Theme.FONT_CARD) * s * k
        val labTop = cardY + (Theme.LABEL_TOP - Theme.FONT_CARD_SMALL) * s * k
        val inset = (Theme.CARD_X + Theme.CARD_INSET) * s
        drawText(c, String.format(Locale.US, "%.2f", e.elapsed), regular, numSize, ink, fa, Paint.Align.LEFT, inset, numTop, tabular = true)
        drawText(c, String.format(Locale.US, "%.2f", max(0.0, e.flow)), regular, numSize, ink, fa, Paint.Align.RIGHT, l.w - inset, numTop, tabular = true)
        val labSize = max(11.0, Theme.FONT_CARD_SMALL * s)
        drawText(c, Theme.LEFT_LABEL, regular, labSize, Theme.CARD_DIM, fa, Paint.Align.LEFT, inset, labTop, 68 * s)
        drawText(c, Theme.RIGHT_LABEL, regular, labSize, Theme.CARD_DIM, fa, Paint.Align.RIGHT, l.w - inset, labTop, 64 * s)
        // dot mark
        fill.color = Theme.DOT
        fill.alpha = (255 * fa).toInt()
        for (r in 0 until 4) for (x in Theme.DOT_XS[r]) {
            c.drawCircle((l.w / 2 + (Theme.DOT_CX - 201 + x * Theme.DOT_PITCH) * s).toFloat(), (cardY + Theme.DOT_YS[r] * s * k).toFloat(), (Theme.DOT_R * s).toFloat(), fill)
        }
        fill.alpha = 255
        // captions
        val ca = e.captionAlpha()
        val capTop = cardY + Theme.CAPTION_TOP * s * k - Theme.FONT_CAPTION * s
        val capW = l.w - 208 * s
        drawText(c, Theme.CAPTION, regular, Theme.FONT_CAPTION * s, ink, fa * M.clamp(1 - 2 * ca), Paint.Align.CENTER, l.w / 2, capTop, capW)
        drawText(c, Theme.CAPTION_DONE, regular, Theme.FONT_CAPTION * s, ink, fa * M.clamp(2 * ca - 1), Paint.Align.CENTER, l.w / 2, capTop, capW)
        // buttons
        if (fa <= 0.003) return
        val saved = if (fa < 1.0) c.saveLayerAlpha(0f, 0f, l.w.toFloat(), l.h.toFloat(), (fa * 255).toInt()) else -1
        val h = e.buttonH
        val cy = e.buttonY + h / 2
        // reset
        run {
            val sc = (1 - e.resetPress.x * 0.03).toFloat()
            c.save()
            c.scale(sc, sc, (e.resetX + e.resetW / 2).toFloat(), cy.toFloat())
            fill.color = Theme.RESET
            val rad = min(30 * s, min(e.resetW / 2, h / 2)).toFloat()
            rect.set(e.resetX.toFloat(), e.buttonY.toFloat(), (e.resetX + e.resetW).toFloat(), (e.buttonY + h).toFloat())
            c.drawRoundRect(rect, rad, rad, fill)
            c.save()
            c.translate((e.resetX + e.resetW / 2 - 13 * s).toFloat(), (cy - 13 * s + Theme.ICON_DROP * s).toFloat())
            val isc = (26 * s / 28).toFloat()
            c.scale(isc, isc)
            stroke.color = Theme.TITLE
            stroke.strokeWidth = 1.7f
            c.drawPath(resetIcon, stroke)
            c.restore()
            c.restore()
        }
        // finish
        run {
            val sc = (1 - e.finishPress.x * 0.03).toFloat()
            val fcx = e.finishX + e.finishW / 2
            c.save()
            c.scale(sc, sc, fcx.toFloat(), cy.toFloat())
            fill.color = Theme.FINISH
            val rad = min(29 * s, min(e.finishW / 2, h / 2)).toFloat()
            rect.set(e.finishX.toFloat(), e.buttonY.toFloat(), (e.finishX + e.finishW).toFloat(), (e.buttonY + h).toFloat())
            c.drawRoundRect(rect, rad, rad, fill)
            val tx = e.finishX + 40 * s
            val avail = e.finishW - 52 * s - 36 * s
            text.typeface = regular
            text.fontFeatureSettings = null
            text.textSize = fit(Theme.FINISH_TEXT, regular, Theme.FONT_PILL * s, avail)
            text.color = Theme.TITLE
            text.alpha = 255
            text.textAlign = Paint.Align.LEFT
            val fm = text.fontMetrics
            c.drawText(Theme.FINISH_TEXT, tx.toFloat(), (cy - (fm.ascent + fm.descent) / 2).toFloat(), text)
            val dcx = e.finishX + e.finishW - 12 * s - 18 * s
            val dcy = cy + Theme.ICON_DROP * s
            fill.color = Theme.X_DISC
            c.drawCircle(dcx.toFloat(), dcy.toFloat(), (18 * s).toFloat(), fill)
            c.save()
            c.translate((dcx - 9 * s).toFloat(), (dcy - 9 * s).toFloat())
            val xsc = (18 * s / 20).toFloat()
            c.scale(xsc, xsc)
            stroke.color = Theme.X_INK
            stroke.strokeWidth = 1.4f
            c.drawPath(closeIcon, stroke)
            c.restore()
            c.restore()
        }
        if (saved >= 0) c.restoreToCount(saved)
    }
}
