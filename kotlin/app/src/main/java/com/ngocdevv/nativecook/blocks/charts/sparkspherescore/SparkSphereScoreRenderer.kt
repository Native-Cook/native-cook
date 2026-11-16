package com.ngocdevv.nativecook.blocks.charts.sparkspherescore

import android.content.res.AssetManager
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.charts.sparkspherescore.SparkSphereScoreMotion as M
import com.ngocdevv.nativecook.blocks.charts.sparkspherescore.SparkSphereScoreTheme as Theme
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Where a touch landed. */
internal enum class SparkSphereScoreHit { NONE, DISC, RULER, BACK, INFO, PREV }

@RequiresApi(33)
private class SparkSphereScoreDiscShader {
    val shader = RuntimeShader(SparkSphereScoreShaders.DISC)
    val paint = Paint().apply { shader = this@SparkSphereScoreDiscShader.shader }

    fun apply(u: SparkSphereScoreDiscUniforms) {
        shader.setFloatUniform("uGeo", u.geo)
        shader.setFloatUniform("uMemA", u.memA)
        shader.setFloatUniform("uMemB", u.memB)
        shader.setFloatUniform("uDim", u.dim)
        shader.setFloatUniform("uS1", u.s1)
        shader.setFloatUniform("uS2", u.s2)
        shader.setFloatUniform("uLook", u.look)
        shader.setFloatUniform("uClock", u.clock)
        shader.setFloatUniform("uCoreO", u.coreO)
        shader.setFloatUniform("uCoreI", u.coreI)
        shader.setFloatUniform("uHazeC", u.hazeC)
        shader.setFloatUniform("uRimC", u.rimC)
        shader.setFloatUniform("uShape", u.shape)
        shader.setFloatUniform("uMisc", u.misc)
        shader.setFloatUniform("uBase", u.base)
        shader.setFloatUniform("uProbe", u.probe)
        shader.setFloatUniform("uBand", u.band)
        shader.setFloatUniform("uRing", u.ring)
    }
}

/** Draws the whole screen onto an android Canvas whose unit is dp (the composable scales it). */
internal class SparkSphereScoreRenderer(assets: AssetManager, private val l: Theme.Layout, private val reduce: Boolean) {
    private val faces = HashMap<String, Typeface>().apply {
        fun load(key: String, file: String) {
            put(key, Typeface.createFromAsset(assets, "sparkspherescore/sparkspherescore_$file.ttf"))
        }
        load("FigtreeMedium", "Figtree-Medium")
        load("FigtreeSemi", "Figtree-SemiBold")
        load("FigtreeBold", "Figtree-Bold")
        load("BarlowSemi", "BarlowSemiCondensed-SemiBold")
        load("BarlowBold", "BarlowSemiCondensed-Bold")
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val ground = Paint().apply { isDither = true }
    private val rect = RectF()
    private val path = Path()
    private val col = DoubleArray(3)
    private val blurs = HashMap<Int, BlurMaskFilter>()

    private var shader: Any? = null
    private var shaderFailed = false

    private val s = l.s
    private val sc = l.sc

    // ---- fonts and their measurements ----
    private fun size(t: SparkSphereScoreType, k: Double): Double = max(t.size * k, min(t.size, 11.0))

    private fun setFont(t: SparkSphereScoreType, sizePt: Double, color: Int, align: Paint.Align = Paint.Align.LEFT, alpha: Double = 1.0) {
        text.typeface = faces[t.family]
        text.textSize = sizePt.toFloat()
        text.letterSpacing = t.tracking.toFloat()
        text.color = color
        text.alpha = (((color ushr 24) and 255) * alpha).toInt().coerceIn(0, 255)
        text.textAlign = align
        text.maskFilter = null
    }

    private fun draw(c: Canvas, str: String, x: Double, base: Double) = c.drawText(str, x.toFloat(), base.toFloat(), text)

    private val scoreSize = Theme.SCORE.size * l.ts * l.sc
    private val deltaNumSize = Theme.DELTA_NUM.size * l.ts * l.sc
    private val deltaWordSize = Theme.DELTA_TEXT.size * l.ts * l.sc
    private val cursorSize = Theme.CURSOR_T.size * s
    private val cursorUnitSize = cursorSize * 0.78
    private val scoreWidths = DoubleArray(10)
    private val dotW: Double
    private val slotW: Double
    private val deltaWidths = HashMap<Char, Double>()
    private val cursorWidths = HashMap<Char, Double>()
    private val cursorUnitWidths = HashMap<Char, Double>()
    private val wYounger: Double
    private val wOlder: Double

    private fun widthOf(t: SparkSphereScoreType, sizePt: Double, str: String): Double {
        setFont(t, sizePt, 0xFFFFFFFF.toInt())
        text.letterSpacing = 0f
        return text.measureText(str).toDouble()
    }

    init {
        for (i in 0..9) scoreWidths[i] = widthOf(Theme.SCORE, scoreSize, i.toString())
        dotW = widthOf(Theme.SCORE, scoreSize, ".")
        slotW = scoreWidths[0]
        for (ch in "0123456789.-+") {
            deltaWidths[ch] = widthOf(Theme.DELTA_NUM, deltaNumSize, ch.toString())
            cursorWidths[ch] = widthOf(Theme.CURSOR_T, cursorSize, ch.toString())
        }
        cursorUnitWidths['x'] = widthOf(Theme.CURSOR_T, cursorUnitSize, "x")
        wYounger = widthOf(Theme.DELTA_TEXT, deltaWordSize, Theme.YOUNGER)
        wOlder = widthOf(Theme.DELTA_TEXT, deltaWordSize, Theme.OLDER)
    }

    // ---- the card's body copy, wrapped once ----
    private val bodySize = Theme.CARD_BODY_T.size * s
    private val bodyLines: List<List<String>> = Theme.BANDS.map { wrap(it.body, l.cardW - 2 * l.cardPadX, l.lines) }

    private fun wrap(body: String, maxW: Double, maxLines: Int): List<String> {
        setFont(Theme.CARD_BODY_T, bodySize, 0xFFFFFFFF.toInt())
        val words = body.split(' ')
        val out = ArrayList<String>()
        var line = ""
        for (w in words) {
            val trial = if (line.isEmpty()) w else "$line $w"
            if (text.measureText(trial) <= maxW || line.isEmpty()) line = trial else { out.add(line); line = w }
        }
        if (line.isNotEmpty()) out.add(line)
        if (out.size > maxLines) {
            var last = out[maxLines - 1] + " " + out.drop(maxLines).joinToString(" ")
            while (last.isNotEmpty() && text.measureText("$last…") > maxW) last = last.dropLast(1)
            return out.take(maxLines - 1) + (last.trimEnd() + "…")
        }
        return out
    }

    // ---- static art ----
    private val back = Path().apply {
        val bx = l.x(Theme.BACK_X)
        val by = l.yA(Theme.BACK_CY)
        val bw = Theme.BACK_W * s
        val bh = Theme.BACK_H * sc * 0.5
        moveTo((bx + bw).toFloat(), (by - bh).toFloat()); lineTo(bx.toFloat(), by.toFloat()); lineTo((bx + bw).toFloat(), (by + bh).toFloat())
    }
    private val chevL = Path().apply {
        val x = l.x(Theme.CHEV_L)
        val y = l.yA(Theme.CHEV_CY)
        val w = Theme.CHEV_W * s
        val h = Theme.CHEV_H * sc / 2
        moveTo((x + w).toFloat(), (y - h).toFloat()); lineTo(x.toFloat(), y.toFloat()); lineTo((x + w).toFloat(), (y + h).toFloat())
    }
    private val chevR = Path().apply {
        val x = l.x(Theme.CHEV_R)
        val y = l.yA(Theme.CHEV_CY)
        val w = Theme.CHEV_W * s
        val h = Theme.CHEV_H * sc / 2
        moveTo(x.toFloat(), (y - h).toFloat()); lineTo((x + w).toFloat(), y.toFloat()); lineTo(x.toFloat(), (y + h).toFloat())
    }
    private val infoCx = l.x(Theme.INFO_CX)
    private val infoCy = l.yA(Theme.INFO_CY)
    private val blob = Path().apply {
        val cx = l.x(Theme.ROW_BLOB_CX)
        val d = Theme.ROW_BLOB_W * s
        val h = Theme.ROW_BLOB_H * sc
        val n = 28
        for (i in 0..n) {
            val a = i.toDouble() / n * PI * 2
            val r = 1 + 0.16 * cos(2 * a + 0.6) + 0.1 * cos(3 * a + 1.4) + 0.05 * cos(5 * a)
            val x = (cx + d / 2 * r * cos(a)).toFloat()
            val y = (l.rowCy + h / 2 * r * sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private val labelMinX = l.x(16.0)
    private val labelMaxX = l.x(386.0)
    private val slowFrom = l.x(70.0)
    private val slowTo = l.x(82.0)
    private val fastFrom = l.x(320.0)
    private val fastTo = l.x(332.0)
    private val rulerTop = l.rowCy - 14 * sc
    private val rulerZoneH = l.tickLabelBase + 8 * sc - rulerTop

    /** Hit zones of the RN block's touch targets. */
    fun hit(x: Double, y: Double, hasBack: Boolean, hasInfo: Boolean, hasPrev: Boolean): SparkSphereScoreHit {
        if (hasBack && abs(x - (l.x(Theme.BACK_X) + 8)) <= 22 && abs(y - l.yA(Theme.BACK_CY)) <= 22) return SparkSphereScoreHit.BACK
        if (hasInfo && abs(x - l.x(Theme.INFO_CX)) <= 22 && abs(y - l.yA(Theme.INFO_CY)) <= 22) return SparkSphereScoreHit.INFO
        if (hasPrev && abs(x - (l.x(Theme.CHEV_L) + 4)) <= 22 && abs(y - l.yA(Theme.CHEV_CY)) <= 22) return SparkSphereScoreHit.PREV
        if (abs(x - l.discCx) <= l.discR && abs(y - l.discCy) <= l.discR) return SparkSphereScoreHit.DISC
        if (y >= rulerTop && y <= rulerTop + rulerZoneH) return SparkSphereScoreHit.RULER
        return SparkSphereScoreHit.NONE
    }

    fun rulerBounds() = doubleArrayOf(rulerTop, rulerTop + rulerZoneH)

    private fun a8(a: Double) = (a.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()

    private fun argb(a: Double, r: Double, g: Double, b: Double) =
        (a8(a) shl 24) or (a8(r) shl 16) or (a8(g) shl 8) or a8(b)

    private fun withAlpha(color: Int, alpha: Double) = (color and 0x00FFFFFF) or (a8(alpha) shl 24)

    fun draw(c: Canvas, e: SparkSphereScoreEngine) {
        val w = l.width.toFloat()
        val hh = l.height.toFloat()
        // ground: near-black at the top, the charcoal of the lower half below the sphere
        ground.shader = LinearGradient(
            0f, (l.discCy + 54 * sc).toFloat(), 0f, (l.discCy + 192 * sc).toFloat(), Theme.GROUND0, Theme.GROUND1, Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, hh, ground)

        drawDisc(c, e)

        // the card, with its notch under the cursor
        val nx = M.clamp(l.rulerX0 + ((e.vCard - M.V_MIN) / M.V_STEP) * l.rulerPitch + Theme.NOTCH_OFFSET * s, l.cardX + 30 * s, l.cardX + l.cardW - 30 * s)
        fill.shader = null
        fill.color = Theme.CARD
        rect.set(l.cardX.toFloat(), l.cardTop.toFloat(), (l.cardX + l.cardW).toFloat(), (l.cardTop + l.cardH).toFloat())
        c.drawRoundRect(rect, l.cardR.toFloat(), l.cardR.toFloat(), fill)
        val top = l.cardTop
        path.reset()
        path.moveTo((nx - l.notchHalf).toFloat(), (top + 1).toFloat())
        path.lineTo((nx - 1.8).toFloat(), (top - l.notchH + 2.2).toFloat())
        path.quadTo(nx.toFloat(), (top - l.notchH - 0.4).toFloat(), (nx + 1.8).toFloat(), (top - l.notchH + 2.2).toFloat())
        path.lineTo((nx + l.notchHalf).toFloat(), (top + 1).toFloat())
        path.close()
        c.drawPath(path, fill)

        drawRuler(c, e)

        // icons
        stroke.shader = null
        stroke.color = Theme.INK
        stroke.strokeWidth = (Theme.BACK_STROKE * s).toFloat()
        c.drawPath(back, stroke)
        stroke.strokeWidth = (3 * s).toFloat()
        c.drawPath(chevL, stroke)
        stroke.color = Theme.DIM
        c.drawPath(chevR, stroke)
        stroke.color = Theme.MUTE_ICON
        stroke.strokeWidth = (Theme.INFO_STROKE * s).toFloat()
        c.drawCircle(infoCx.toFloat(), infoCy.toFloat(), (Theme.INFO_D * s / 2).toFloat(), stroke)
        stroke.strokeWidth = (2.4 * s).toFloat()
        c.drawLine(infoCx.toFloat(), (infoCy - 1.4 * s).toFloat(), infoCx.toFloat(), (infoCy + 6 * s).toFloat(), stroke)
        stroke.strokeWidth = (2.6 * s).toFloat()
        c.drawLine(infoCx.toFloat(), (infoCy - 6 * s).toFloat(), infoCx.toFloat(), (infoCy - 5.9 * s).toFloat(), stroke)

        // the cursor label's span decides what fades: a legend is gone before the label reaches it
        val curV = M.clamp(e.v.value, M.V_MIN, M.V_MAX)
        val numStr = String.format(Locale.US, "%.1f", curV)
        val numW = numStr.sumOf { cursorWidths[it] ?: 0.0 }
        val unitW = cursorUnitWidths['x'] ?: 0.0
        val grab = e.grab.value
        val labelHalf = (numW + unitW) / 2 * (1 + 0.08 * grab)
        val labelCX = M.clamp(e.cursorX(), labelMinX + labelHalf, labelMaxX - labelHalf)
        val slowA = M.smoothstep(slowFrom, slowTo, labelCX - labelHalf)
        val fastA = 1 - M.smoothstep(fastFrom, fastTo, labelCX + labelHalf)

        fill.color = withAlpha(Theme.SLOW_DOT, slowA)
        c.drawCircle(l.x(Theme.ROW_DOT_X).toFloat(), l.rowCy.toFloat(), (Theme.ROW_DOT_D * s / 2).toFloat(), fill)
        fill.color = withAlpha(Theme.FAST_BLOB, fastA)
        c.drawPath(blob, fill)

        drawScore(c, e)
        drawDelta(c, e)

        // the cursor's value label (the unit smaller), growing a little under the finger
        c.save()
        c.translate(labelCX.toFloat(), l.cursorBase.toFloat())
        c.scale((1 + 0.08 * grab).toFloat(), (1 + 0.08 * grab).toFloat())
        c.translate(-labelCX.toFloat(), -l.cursorBase.toFloat())
        val lx = labelCX - (numW + unitW) / 2
        setFont(Theme.CURSOR_T, cursorSize, Theme.CURSOR)
        draw(c, numStr, lx, l.cursorBase)
        setFont(Theme.CURSOR_T, cursorUnitSize, Theme.CURSOR)
        draw(c, "x", lx + numW, l.cursorBase)
        c.restore()

        drawStaticText(c, e, slowA, fastA)
    }

    // ---- the ruler: 81 ticks, the cursor and the colour that comes from the sphere ----
    private fun drawRuler(c: Canvas, e: SparkSphereScoreEngine) {
        val age = e.pulseAge
        val rr = if (age < 0.5) age * M.RIPPLE_SPEED else -1.0
        val rip = if (age < 0.5) M.RIPPLE_AMOUNT * exp(-age / 0.2) else 0.0
        M.colourAt(e.vFilm, col)
        val tint = M.smoothstep(0.08, 0.45, abs(e.vFilm - M.V_REST))
        val cr = M.mix(0.93, col[0], tint)
        val cg = M.mix(0.95, col[1], tint)
        val cb = M.mix(0.96, col[2], tint)
        val tickRgb = Theme.rgb(Theme.TICK)
        val markRgb = Theme.rgb(Theme.TICK_MARK)
        val cursorX = e.cursorX()
        val grab = e.grab.value
        val hw = 0.5 * l.tickW
        fill.shader = null
        for (idx in 0 until Theme.RULER_COUNT) {
            val tx = l.rulerX0 + idx * l.rulerPitch
            val dxc = tx - cursorX
            val dnear = abs(dxc)
            val trl = if (dxc > 0) exp(-dxc / (3.1 * l.rulerPitch)) else exp(-dnear / (0.9 * l.rulerPitch))
            val rse = 1 + 0.30 * exp(-(dnear / 30.0).pow(2)) * (0.4 + 0.6 * grab)
            val ripple = if (rr >= 0) rip * exp(-((abs(dnear - rr)) / 26.0).pow(2)) * exp(-rr / 150.0) else 0.0
            val th = 0.5 * l.tickH * rse + ripple
            val base = if (idx % 10 == 0) markRgb else tickRgb
            val k = M.clamp(trl, 0.0, 1.0) * 0.9
            fill.color = argb(1.0, M.mix(base[0], cr, k), M.mix(base[1], cg, k), M.mix(base[2], cb, k))
            val r = min(hw, 1.0).toFloat()
            rect.set((tx - hw).toFloat(), (l.rulerCy - th).toFloat(), (tx + hw).toFloat(), (l.rulerCy + th).toFloat())
            c.drawRoundRect(rect, r, r, fill)
        }
        val ch = 0.5 * (l.cursorH * (1 + 0.2 * grab))
        fill.color = Theme.CURSOR
        rect.set((cursorX - 0.5 * l.cursorW).toFloat(), (l.rulerCy - ch).toFloat(), (cursorX + 0.5 * l.cursorW).toFloat(), (l.rulerCy + ch).toFloat())
        c.drawRoundRect(rect, 1f, 1f, fill)
    }

    // ---- the score: odometer digits, the point ----
    private fun blurFor(sigma: Double): BlurMaskFilter? {
        if (sigma <= 0.55 || Build.VERSION.SDK_INT < 28) return null
        val radius = ((sigma - 0.5) / 0.57735)
        val key = (radius * 4).toInt()
        return blurs.getOrPut(key) { BlurMaskFilter(max(key / 4f, 0.25f), BlurMaskFilter.Blur.NORMAL) }
    }

    private fun drawSlot(c: Canvas, score: Double, k: Int, x: Double, vis: Double) {
        val cell = scoreSize * 0.62
        val q = if (k == 0) score * 10 else if (k == 1) score else score / 10
        val pos: Double = if (reduce) Math.round(q).toDouble() else {
            val fl = floor(q)
            if (k == 0) q else fl + M.smoothstep(1 - 0.1.pow(k), 1.0, q - fl)
        }
        val f = pos - floor(pos)
        val frac = if (k == 0) M.smoothstep(0.7, 1.0, f) else f
        val cur = (((floor(pos + 1e-6).toInt() % 10) + 10) % 10)
        val next = (((floor(pos + 1e-6).toInt() + 1) % 10) + 10) % 10
        val travel = cell * 0.45
        val xc = x + (slotW - scoreWidths[cur]) / 2
        val xn = x + (slotW - scoreWidths[next]) / 2
        val aCur = (1 - M.smoothstep(0.0, 0.6, frac)) * vis
        val aNext = M.smoothstep(0.4, 1.0, frac) * vis
        val base = l.centreBase(Theme.SCORE_BASE)
        setFont(Theme.SCORE, scoreSize, Theme.INK, alpha = aCur)
        if (reduce) {
            draw(c, cur.toString(), xc, base)
            return
        }
        text.maskFilter = blurFor(max(0.001, frac * cell * 0.07))
        draw(c, cur.toString(), xc, base - frac * travel)
        setFont(Theme.SCORE, scoreSize, Theme.INK, alpha = aNext)
        text.maskFilter = blurFor(max(0.001, (1 - frac) * cell * 0.07))
        draw(c, next.toString(), xn, base + (1 - frac) * travel)
        text.maskFilter = null
    }

    private fun drawScore(c: Canvas, e: SparkSphereScoreEngine) {
        val sv = M.scoreOf(e.vScore)
        val tensVis = M.smoothstep(9.0, 9.95, sv)
        val gLeft = l.discCx - (tensVis * slotW + slotW + dotW + slotW) / 2
        val xTens = gLeft
        val xOnes = gLeft + tensVis * slotW
        val xDot = xOnes + slotW
        val xTenths = xDot + dotW
        val base = l.centreBase(Theme.SCORE_BASE)
        c.save()
        c.clipRect(
            (l.discCx - 4 * slotW).toFloat(), (base - scoreSize * 0.711 - scoreSize * 0.24).toFloat(),
            (l.discCx + 4 * slotW).toFloat(), (base + scoreSize * 0.18).toFloat(),
        )
        drawSlot(c, sv, 2, xTens, tensVis)
        drawSlot(c, sv, 1, xOnes, 1.0)
        setFont(Theme.SCORE, scoreSize, Theme.INK)
        draw(c, ".", xDot, base)
        drawSlot(c, sv, 0, xTenths, 1.0)
        c.restore()
    }

    // ---- the difference to the calendar ----
    private fun drawDelta(c: Canvas, e: SparkSphereScoreEngine) {
        val dv = M.scoreOf(e.vCard) - M.SCORE_CHRONO
        val str = String.format(Locale.US, "%.1f", abs(dv))
        val older = dv > 0.049
        val numW = str.sumOf { deltaWidths[it] ?: 0.0 }
        val gap = 4 * l.ts * sc
        val total = numW + gap + (if (older) wOlder else wYounger)
        val x = l.discCx - total / 2
        M.colourAt(e.vCard, col)
        val color = argb(1.0, col[0], col[1], col[2])
        val base = l.centreBase(Theme.DELTA_BASE)
        setFont(Theme.DELTA_NUM, deltaNumSize, color)
        text.letterSpacing = 0f
        draw(c, str, x, base)
        setFont(Theme.DELTA_TEXT, deltaWordSize, color)
        draw(c, if (older) Theme.OLDER else Theme.YOUNGER, x + numW + gap, base)
    }

    // ---- static type ----
    private fun drawStaticText(c: Canvas, e: SparkSphereScoreEngine, slowA: Double, fastA: Double) {
        val cx = l.width / 2
        setFont(Theme.TITLE, size(Theme.TITLE, s), Theme.INK, Paint.Align.CENTER); draw(c, Theme.TITLE_COPY, cx, l.yA(Theme.TITLE_BASE))
        setFont(Theme.SUBTITLE, size(Theme.SUBTITLE, s), Theme.MUTE, Paint.Align.CENTER); draw(c, Theme.SUBTITLE_COPY, cx, l.yA(Theme.SUBTITLE_BASE))
        setFont(Theme.DATE, size(Theme.DATE, s), Theme.INK, Paint.Align.CENTER); draw(c, Theme.DATE_COPY, cx, l.yA(Theme.DATE_BASE))
        setFont(Theme.SCORE_LABEL, size(Theme.SCORE_LABEL, l.ts * sc), Theme.INK_SOFT, Paint.Align.CENTER)
        draw(c, Theme.SCORE_LABEL_COPY, l.discCx, l.centreBase(Theme.LABEL_BASE))
        setFont(Theme.PACE, size(Theme.PACE, s), Theme.INK); draw(c, Theme.PACE_COPY, l.x(Theme.PACE_X), l.paceBase)
        setFont(Theme.END, size(Theme.END, s), Theme.LABEL, alpha = slowA); draw(c, Theme.SLOW, l.x(Theme.ROW_SLOW_X), l.rowBase)
        setFont(Theme.END, size(Theme.END, s), Theme.LABEL, Paint.Align.RIGHT, fastA); draw(c, Theme.FAST, l.x(Theme.ROW_FAST_RIGHT), l.rowBase)
        for ((i, label) in Theme.ENDS.withIndex()) {
            setFont(Theme.TICK_LABEL, size(Theme.TICK_LABEL, s), Theme.LABEL, Paint.Align.CENTER)
            draw(c, label, l.rulerX0 + i * 40 * l.rulerPitch, l.tickLabelBase)
        }
        for ((i, band) in Theme.BANDS.withIndex()) {
            val a = e.bandAlpha[min(i, e.bandAlpha.size - 1)].value
            if (a <= 0.001) continue
            setFont(Theme.CARD_TITLE_T, size(Theme.CARD_TITLE_T, s), Theme.CARD_TITLE, alpha = a)
            draw(c, band.title, l.cardX + l.cardPadX - 0.4, l.cardTitleBase)
            setFont(Theme.CARD_BODY_T, bodySize, Theme.CARD_BODY, alpha = a)
            val lh = Theme.CARD_BODY_T.lineHeight * l.k
            for ((n, line) in bodyLines[i].withIndex()) draw(c, line, l.cardX + l.cardPadX, l.cardBodyBase + n * lh)
        }
    }

    // ---- the disc ----
    private fun drawDisc(c: Canvas, e: SparkSphereScoreEngine) {
        val r = l.discR
        if (Build.VERSION.SDK_INT >= 33 && !shaderFailed && c.isHardwareAccelerated) {
            try {
                val sh = (shader as? SparkSphereScoreDiscShader) ?: SparkSphereScoreDiscShader().also { shader = it }
                sh.apply(e.disc)
                c.drawRect(
                    (l.discCx - 1.5 * r).toFloat(), (l.discCy - 1.5 * r).toFloat(), (l.discCx + 1.5 * r).toFloat(), (l.discCy + 1.5 * r).toFloat(),
                    sh.paint,
                )
                return
            } catch (error: IllegalArgumentException) {
                Log.w("SparkSphereScore", "Runtime shader unavailable; using the gradient fallback", error)
                shaderFailed = true
            }
        }
        drawDiscFallback(c, e)
    }

    private fun hash33(a: Double, b: Double, cc: Double, out: DoubleArray) {
        var x = frac(a * 0.1031); var y = frac(b * 0.1030); var z = frac(cc * 0.0973)
        val d = x * (y + 33.33) + y * (x + 33.33) + z * (z + 33.33)
        x += d; y += d; z += d
        out[0] = frac((x + y) * z)
        out[1] = frac((x + z) * y)
        out[2] = frac((y + z) * x)
    }

    private fun frac(x: Double) = x - floor(x)
    private fun ss(a: Double, b: Double, x: Double) = M.smoothstep(a, b, x)

    private val h1 = DoubleArray(3)
    private val h2 = DoubleArray(3)
    private val h3 = DoubleArray(3)
    private val addPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }

    /** Below API 33: the same sphere from gradients, a membrane path and the lattice's sparks drawn as discs (no curl field). */
    private fun drawDiscFallback(c: Canvas, e: SparkSphereScoreEngine) {
        val u = e.disc
        val sc2 = l.discScale
        val cx = l.discCx.toFloat()
        val cy = l.discCy.toFloat()
        val heat = u.s1[0].toDouble(); val cold = u.s1[1].toDouble(); val fillAmt = u.s1[2].toDouble()
        val swirl = u.s1[3].toDouble()
        fun rimMul(phi: Double): Double =
            1 + u.memA[0] * cos(2 * phi + u.memA[1]) + u.memA[2] * cos(3 * phi + u.memA[3]) +
                u.memB[0] * cos(4 * phi + u.memB[1]) + u.memB[2] * cos(5 * phi + u.memB[3])
        // shadow
        val rr = (M.R0 * sc2).toFloat()
        fill.shader = RadialGradient(
            cx, cy, rr * 1.25f,
            intArrayOf(withAlpha(0, 0.8), withAlpha(0, 0.29), withAlpha(0, 0.014), 0),
            floatArrayOf(0.8f, 0.888f, 0.976f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(cx, cy, rr * 1.25f, fill)
        // the membrane outline
        path.reset()
        val n = 120
        for (i in 0..n) {
            val phi = i.toDouble() / n * 2 * PI
            val rad = M.R0 * sc2 * rimMul(phi) * u.dim[3]
            val x = cx + (rad * cos(phi)).toFloat()
            val y = cy + (rad * sin(phi)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        // body: the hollow, the haze deepening toward the rim, the inner glow
        val stops = 14
        val colors = IntArray(stops)
        val pos = FloatArray(stops)
        val a0 = M.mix(0.5, 0.2, M.clamp(fillAmt / 0.85, 0.0, 1.0))
        for (i in 0 until stops) {
            val rho = i.toDouble() / (stops - 1)
            val g = ss(a0, 0.96, rho).pow(1.25)
            val k = M.clamp(g * u.misc[2], 0.0, 1.0)
            val depth = (1 - rho) * M.R0
            val glow = exp(-(depth / max(u.shape[2].toDouble(), 1.0)).pow(1.4)) * u.shape[3]
            colors[i] = argb(
                1.0,
                M.mix(u.base[0].toDouble(), u.hazeC[0].toDouble(), k) + u.rimC[0] * glow,
                M.mix(u.base[1].toDouble(), u.hazeC[1].toDouble(), k) + u.rimC[1] * glow,
                M.mix(u.base[2].toDouble(), u.hazeC[2].toDouble(), k) + u.rimC[2] * glow,
            )
            pos[i] = rho.toFloat()
        }
        fill.shader = RadialGradient(cx, cy, rr, colors, pos, Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        fill.shader = null
        // sparks: the slots of the polar lattice, existing as the pin's density at rest, a flat fill when hot, all when cold
        val ph = 2 * PI * (u.clock[0] + u.s2[0])
        val cs = cos(swirl)
        val sn = sin(swirl)
        val jitA = 0.5 * 5.0 * (1 - cold)
        val epiAmp = u.s2[3] * (1 - 0.85 * cold)
        c.save()
        c.clipPath(path)
        for (k in 0..40) {
            val mk = max(3.0, floor(2 * PI * (k + 0.5) + 0.5)).toInt()
            val tw = frac(k * 0.381966)
            for (j in 0 until mk) {
                hash33(k.toDouble(), j.toDouble(), 3.17, h1)
                val tha = 2 * PI * (j + 0.5 + tw) / mk
                val rk = (k + 0.5) * 5.0
                val bx = rk * cos(tha) + jitA * 2 * (h1[1] - 0.5)
                val by = rk * sin(tha) + jitA * 2 * (h1[2] - 0.5)
                val qb = sqrt(bx * bx + by * by) / M.R0
                val clmp = 0.35 + 1.1 * (0.5 + 0.5 * sin(0.041 * bx + 1.3 + 2 * PI * u.clock[0]) * sin(0.047 * by - 0.7 - 2 * PI * u.clock[0]))
                val pr = (0.06 + 0.94 * ss(0.34, 0.80, qb)) * (1 + 0.10 * ss(0.88, 1.0, qb)) * 0.50 * clmp
                val pf = 0.80 * (1 - u.clock[3] * (1 - ss(u.ring[2].toDouble(), u.ring[3].toDouble(), qb)))
                var pe = M.mix(pr, pf, fillAmt)
                pe = M.mix(pe, 1.0, cold) * (1 - ss(0.96, 1.04, qb))
                if (pe - h1[0] <= 0.0) continue
                hash33(k.toDouble(), j.toDouble(), 7.31, h2)
                hash33(k.toDouble(), j.toDouble(), 11.7, h3)
                val k1 = 1 + floor(h3[0] * 3)
                val k2 = 1 + floor(h3[1] * 3)
                val ex = epiAmp * (0.45 + 0.55 * h2[2]) * cos(k1 * ph + 2 * PI * h3[1])
                val ey = epiAmp * (0.45 + 0.55 * h2[2]) * sin(k2 * ph + 2 * PI * h3[0])
                val sx = bx + ex
                val sy = by + ey
                val qs = sqrt(sx * sx + sy * sy) / M.R0
                val zs = (if (h2[2] < 0.5) -1.0 else 1.0) * sqrt(max(0.0, 1 - min(qb, 1.0) * min(qb, 1.0)))
                val bokeh = h3[2] < 0.05 && zs > 0.18
                var d = if (bokeh) 4.6 + 2.6 * h2[1] else 0.9 + 4.7 * h2[0].pow(1.9)
                d = M.mix(d, 1.6 + 0.9 * h2[0], cold) * (1 + 0.45 * max(zs, 0.0))
                val a0s = M.mix((0.28 + 0.62 * h2[1].pow(1.1)) * (0.40 + 0.60 * ss(0.30, 0.72, qb)), 0.62, cold)
                val sizeF = 0.62 + 0.38 * ss(1.0, 2.6, d) - 0.2 * ss(5.0, 9.0, d)
                val av = a0s * sizeF * (if (zs < 0.0) 0.55 else 1.0) * u.look[0] * (1 + u.look[3] * 0.25)
                val cc = M.clamp(av * (if (bokeh) 0.5 else 0.85), 0.0, 0.86)
                val mixK = ss(0.25, 0.9, qs)
                addPaint.color = argb(
                    cc,
                    M.mix(u.coreI[0].toDouble(), u.coreO[0].toDouble(), mixK),
                    M.mix(u.coreI[1].toDouble(), u.coreO[1].toDouble(), mixK),
                    M.mix(u.coreI[2].toDouble(), u.coreO[2].toDouble(), mixK),
                )
                val px = cx + ((cs * sx - sn * sy) * sc2).toFloat()
                val py = cy + ((sn * sx + cs * sy) * sc2).toFloat()
                c.drawCircle(px, py, max((0.5 * d * sc2).toFloat(), 0.4f), addPaint)
            }
        }
        c.restore()
        // the rim film
        stroke.shader = null
        stroke.color = argb(M.clamp(u.rimC[3].toDouble() * (1 + u.misc[1]), 0.0, 1.0), u.rimC[0].toDouble(), u.rimC[1].toDouble(), u.rimC[2].toDouble())
        stroke.strokeWidth = ((u.shape[0] + 0.5 * u.shape[1]) * sc2).toFloat()
        c.drawPath(path, stroke)
    }
}
