package com.ngocdevv.nativecook.blocks.progress.printlinescrub

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.ngocdevv.nativecook.blocks.progress.printlinescrub.PrintLineScrubMotion as M
import com.ngocdevv.nativecook.blocks.progress.printlinescrub.PrintLineScrubTheme as Theme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The screen's drawing (RN `PrintLineScrub`'s canvas), layer for layer: the scene (ground, grid, the sculpt printed in matte red up to
 * the line and an unprinted mesh above it, the section at the cut), the gizmo at the seam, the strip the capsule throws, the header and
 * dock as glass over the page, and the frosted capsule: soft shadows, the scene seen through a lens, a white tint, rims, the label,
 * the dotted bar made of matter, the pill. Everything is in dp: the caller scales the canvas by the density.
 */
internal class PrintLineScrubRenderer(assets: AssetManager) {
    private val regular = Typeface.createFromAsset(assets, "printlinescrub/printlinescrub_Urbanist-Regular.ttf")
    private val medium = Typeface.createFromAsset(assets, "printlinescrub/printlinescrub_Urbanist-Medium.ttf")
    val silhouette = PrintLineScrubSilhouette.load(assets)
    private val solid: Bitmap? = decode(assets, "printlinescrub/printlinescrub_frillwarden-solid.webp")
    private val ghost: Bitmap? = decode(assets, "printlinescrub/printlinescrub_frillwarden-slicer.webp")
    val ready get() = solid != null && ghost != null

    private fun decode(assets: AssetManager, name: String): Bitmap? = try {
        assets.open(name).use { BitmapFactory.decodeStream(it) }
    } catch (error: Exception) {
        Log.w("PrintLineScrub", "Image unavailable: $name", error)
        null
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { fontFeatureSettings = "'kern' 0, 'liga' 0" }
    private val rr = RectF()
    private val path = Path()
    private val spans = FloatArray(12)

    // ---- the icons, 24 x 24 grid ----
    private fun line(x0: Float, y0: Float, x1: Float, y1: Float) = Path().apply {
        moveTo(x0, y0)
        lineTo(x1, y1)
    }
    private val markPaths = listOf(
        line(5.5f, 14.5f, 14.5f, 5.5f), line(9f, 19f, 19f, 9f), line(5.5f, 9.5f, 9.5f, 5.5f),
    )
    private val chevron = PathParser.createPathFromPathData("M7 9.5l5 5 5-5")
    private val cursor = PathParser.createPathFromPathData("M5.2 3.6 L19.6 10.9 L12.9 12.8 L10.7 19.6 Z")
    private val handPalm = PathParser.createPathFromPathData("M8.6 11v5.2c0 2.2 1.7 3.8 4.4 3.8 2.8 0 4.4-1.9 4.4-4.4V11.6M8.6 13.6l-2.2-2")
    private val handFingers = listOf(
        line(8.6f, 11f, 8.6f, 6.4f), line(11.6f, 10.6f, 11.6f, 4.6f), line(14.6f, 10.8f, 14.6f, 5.6f), line(17.4f, 11.6f, 17.4f, 8.4f),
    )
    private val axisRed = line(0f, 0f, Theme.GIZ_STUB.toFloat(), (-Theme.GIZ_STUB * 0.2).toFloat())
    private val axisGreen = line(0f, 0f, (-Theme.GIZ_STUB * 0.55).toFloat(), (Theme.GIZ_STUB * 0.62).toFloat())
    private val arrowHead = Path().apply {
        moveTo(0f, -Theme.GIZ_HEAD.toFloat())
        lineTo((-Theme.GIZ_HEAD * 0.55).toFloat(), 0f)
        lineTo((Theme.GIZ_HEAD * 0.55).toFloat(), 0f)
        close()
    }

    // ---- shaders / blur (API 33 / 31) ----
    private val scenes: Array<PrintLineScrubSceneShader>? = if (Build.VERSION.SDK_INT >= 33 && solid != null && ghost != null) {
        try {
            Array(4) { PrintLineScrubSceneShader(solid, ghost) }
        } catch (error: IllegalArgumentException) {
            Log.w("PrintLineScrub", "Runtime shader unavailable; using the Canvas fallback", error)
            null
        }
    } else null
    private val barShader: PrintLineScrubBarShader? = if (scenes != null) PrintLineScrubBarShader() else null
    private val blurs: Array<PrintLineScrubBlur>? = if (Build.VERSION.SDK_INT >= 31) Array(3) { PrintLineScrubBlur() } else null
    private val fallback: PrintLineScrubSceneFallback? = if (scenes == null && solid != null && ghost != null) {
        PrintLineScrubSceneFallback(solid, ghost)
    } else null

    // ---- blur mask filters per scale ----
    private var blurS = -1.0
    private var bf12: BlurMaskFilter? = null
    private var bf10: BlurMaskFilter? = null
    private var bf4: BlurMaskFilter? = null
    private var bf2: BlurMaskFilter? = null

    /** A Skia blur sigma as the platform's blur radius (it converts back with sigma = 0.57735 r + 0.5). */
    private fun blur(sigma: Double) = BlurMaskFilter(max(((sigma - 0.5) / 0.57735).toFloat(), 0.1f), BlurMaskFilter.Blur.NORMAL)

    private fun sceneState(g: PrintLineScrubGeo, e: PrintLineScrubEngine, density: Float, glass: Boolean, cy: Double, k: Double): PrintLineScrubSceneState {
        val hh = e.h.value
        val figureH = Theme.FIGURE_HEIGHT * g.s
        val figureW = figureH * PrintLineScrubSilhouette.FIGURE_W / PrintLineScrubSilhouette.FIGURE_H
        val figureY = g.baseY + (Theme.FIGURE_TOP - Theme.STAGE_Y_BASE) * g.s
        val row = (g.planeY(hh) - figureY) / figureH
        silhouette.at(row, spans)
        val yawMax = Math.toRadians(4.0)
        return PrintLineScrubSceneState(
            figure = floatArrayOf((g.axisX - figureW / 2).toFloat(), figureY.toFloat(), figureW.toFloat(), figureH.toFloat()),
            spans = spans,
            ox = g.axisX.toFloat(),
            oy = g.baseY.toFloat(),
            u = g.u.toFloat(),
            camCos = PrintLineScrubGeo.CAM_COS.toFloat(),
            camSin = PrintLineScrubGeo.CAM_SIN.toFloat(),
            yaw = max(-yawMax, min(yawMax, e.yaw.value)).toFloat(),
            h = hh.toFloat(),
            band = (e.band() * g.s).toFloat(),
            heat = e.heatNow().toFloat(),
            bead = e.bead.toFloat(),
            tint = M.clamp01(M.percentAt(hh) / 100).toFloat(),
            px = density,
            glass = glass,
            lens = if (glass) floatArrayOf((g.capL + g.capW / 2).toFloat(), cy.toFloat(), (g.capW / 2 * k).toFloat(), (g.capH / 2 * k).toFloat()) else floatArrayOf(0f, 0f, 1f, 1f),
            lensR = if (glass) (g.capR * k).toFloat() else 1f,
            lensK = if (glass) Theme.GLASS_LENS.toFloat() else 1f,
            refr = if (glass) (Theme.GLASS_REFRACTION * g.s).toFloat() else 0f,
        )
    }

    private fun drawScene(c: Canvas, idx: Int, l: Float, t: Float, r: Float, b: Float, st: PrintLineScrubSceneState) {
        val sc = scenes
        if (sc != null && Build.VERSION.SDK_INT >= 33) sc[idx].draw(c, l, t, r, b, st) else fallback?.draw(c, l, t, r, b, st)
    }

    fun draw(c: Canvas, g: PrintLineScrubGeo, e: PrintLineScrubEngine, density: Float) {
        if (blurS != g.s) {
            blurS = g.s
            bf12 = blur(12 * g.s)
            bf10 = blur(10 * g.s)
            bf4 = blur(4 * g.s)
            bf2 = blur(2 * g.s)
        }
        val s = g.s.toFloat()
        val w = g.w.toFloat()
        val hgt = g.h.toFloat()
        val hh = e.h.value
        val cy = e.cy()
        val sc = 1 + 0.02 * e.press.value
        val planeScreenY = g.planeY(hh)

        // ---- the scene, the gizmo at the seam, the strip the floating capsule throws on the figure ----
        val full = sceneState(g, e, density, false, cy, 1.0)
        drawScene(c, 0, 0f, 0f, w, hgt, full)
        drawGizmo(c, g, e, planeScreenY)
        c.save()
        c.translate(0f, (cy + g.capH / 2 - 14 * g.s + 18 * g.s * (1 + 0.1 * e.press.value)).toFloat())
        fill.color = Theme.shadow(0.08)
        fill.maskFilter = bf12
        rr.set((g.capL + 10 * g.s).toFloat(), 0f, (g.capL + g.capW - 10 * g.s).toFloat(), (g.capH * 0.5).toFloat())
        c.drawRoundRect(rr, (g.capR * 0.5).toFloat(), (g.capR * 0.5).toFloat(), fill)
        fill.maskFilter = null
        c.restore()

        // ---- the header: glass over the grid ----
        val hdrX = g.l.x(Theme.HEADER_X)
        val hdrW = Theme.HEADER_W * g.s
        val hdrH = Theme.HEADER_H * g.s
        glassStrip(c, g, 1, hdrX, g.hdrY, hdrW, hdrH, Theme.HEADER_R * g.s, density, e)
        fill.color = Theme.white
        val logoCx = hdrX + (Theme.LOGO_X + Theme.LOGO_D / 2) * g.s
        val logoCy = g.hdrY + (Theme.LOGO_Y + Theme.LOGO_D / 2) * g.s
        c.drawCircle(logoCx.toFloat(), logoCy.toFloat(), (Theme.LOGO_D / 2 * g.s).toFloat(), fill)
        c.save()
        c.translate(logoCx.toFloat(), logoCy.toFloat())
        c.scale(0.8f * s, 0.8f * s)
        c.translate(-12f, -12f)
        stroke.color = Theme.iconInk
        stroke.strokeWidth = 3.2f
        for (p in markPaths) c.drawPath(p, stroke)
        c.restore()
        // the file name set with tracking, glyph by glyph
        text.typeface = medium
        text.textSize = (Theme.FILE_SIZE * g.s).toFloat()
        text.color = Theme.inkSoft
        val tr = Theme.FILE_TRACKING * Theme.FILE_SIZE * g.s
        var fx = hdrX + Theme.FILE_X * g.s
        val fy = g.hdrY + hdrH / 2 + 0.36 * Theme.FILE_SIZE * g.s
        var lastGlyphX = fx
        for (ch in Theme.FILE) {
            val str = ch.toString()
            c.drawText(str, fx.toFloat(), fy.toFloat(), text)
            lastGlyphX = fx
            fx += text.measureText(str) + tr
        }
        c.save()
        c.translate((lastGlyphX + 14 * g.s + 8 * g.s).toFloat(), (g.hdrY + hdrH / 2 - 5.2 * g.s).toFloat())
        c.scale(0.52f * s, 0.52f * s)
        c.translate(-12f, -9f)
        stroke.color = Theme.inkSoft
        stroke.strokeWidth = 2.2f
        c.drawPath(chevron, stroke)
        c.restore()
        val wsW = Theme.WS_W * g.s
        val wsH = Theme.WS_H * g.s
        val wsX = hdrX + hdrW - 4 * g.s - wsW
        val wsY = g.hdrY + (hdrH - wsH) / 2
        fill.color = Theme.yellow
        rr.set(wsX.toFloat(), wsY.toFloat(), (wsX + wsW).toFloat(), (wsY + wsH).toFloat())
        c.drawRoundRect(rr, (Theme.WS_R * g.s).toFloat(), (Theme.WS_R * g.s).toFloat(), fill)
        text.textSize = (Theme.WS_SIZE * g.s).toFloat()
        text.color = Theme.ink
        val wsLabelW = text.measureText(Theme.WORKSPACE)
        c.drawText(Theme.WORKSPACE, (wsX + (wsW - wsLabelW) / 2).toFloat(), (wsY + wsH / 2 + 0.35 * Theme.WS_SIZE * g.s).toFloat(), text)

        // ---- the dock: three tools ----
        val dockX = g.l.x(Theme.DOCK_X)
        val dockW = (Theme.DOCK_BTN_X0 * 2 + 3 * Theme.DOCK_BTN_D + 2 * Theme.DOCK_BTN_GAP) * g.s
        glassStrip(c, g, 2, dockX, g.dockY, dockW, Theme.DOCK_H * g.s, Theme.DOCK_R * g.s, density, e)
        dockButtons(c, g, dockX + Theme.DOCK_BTN_X0 * g.s, g.dockY + (Theme.DOCK_H * g.s - Theme.DOCK_BTN_D * g.s) / 2)

        // ---- the capsule: soft shadows, the frosted scene seen through a lens, tint, rim ----
        val cw = g.capW * sc
        val ch = g.capH * sc
        val capLeft = g.capL + (g.capW - cw) / 2
        val capTop = cy - ch / 2
        val capR = g.capR * sc
        rr.set(capLeft.toFloat(), capTop.toFloat(), (capLeft + cw).toFloat(), (capTop + ch).toFloat())
        path.rewind()
        path.addRoundRect(rr, capR.toFloat(), capR.toFloat(), Path.Direction.CW)
        c.save()
        c.translate(0f, ((12 + 8 * e.press.value) * g.s).toFloat())
        fill.color = Theme.shadow(0.13)
        fill.maskFilter = bf12
        c.drawPath(path, fill)
        c.restore()
        c.save()
        c.translate(0f, 2f * s)
        fill.color = Theme.shadow(0.16)
        fill.maskFilter = bf2
        c.drawPath(path, fill)
        c.restore()
        fill.maskFilter = null
        c.save()
        c.clipPath(path)
        val frost = RectF((g.capL - 30).toFloat(), (cy - g.capH / 2 - 30).toFloat(), (g.capL + g.capW + 30).toFloat(), (cy + g.capH / 2 + 30).toFloat())
        val glass = sceneState(g, e, density, true, cy, sc)
        val frosted = blurs?.get(0)?.draw(c, density, frost.left, frost.top, frost.right, frost.bottom, (Theme.GLASS_FROST * g.s).toFloat()) { rc ->
            drawScene(rc, 3, frost.left, frost.top, frost.right, frost.bottom, glass)
            drawGizmo(rc, g, e, planeScreenY)
        } ?: false
        if (!frosted) {
            drawScene(c, 3, frost.left, frost.top, frost.right, frost.bottom, glass)
            drawGizmo(c, g, e, planeScreenY)
        }
        // white tint: brighter at the top, calmer below
        fill.shader = LinearGradient(
            0f, (cy - g.capH / 2).toFloat(), 0f, (cy + g.capH / 2).toFloat(), Color.argb(102, 255, 255, 255), Color.argb(56, 255, 255, 255),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(frost, fill)
        fill.shader = null
        // inner shadow on the lower rim
        stroke.color = Theme.shadow(0.10)
        stroke.strokeWidth = 4f * s
        stroke.maskFilter = bf2
        c.drawPath(path, stroke)
        stroke.maskFilter = null
        c.restore()
        // the rim: bright at the top, and lit orange by the hot layer where the capsule's edge is nearest the line
        stroke.shader = LinearGradient(
            0f, (cy - g.capH / 2).toFloat(), 0f, (cy + g.capH / 2).toFloat(),
            intArrayOf(Color.argb(242, 255, 255, 255), Color.argb(97, 255, 255, 255), Color.argb(51, 255, 255, 255)), null, Shader.TileMode.CLAMP,
        )
        stroke.strokeWidth = s
        c.drawPath(path, stroke)
        val prox = M.clamp01(1 - abs(planeScreenY - (cy - g.capH / 2)) / (70 * g.s))
        val rimHeat = M.clamp01(0.12 + 0.4 * prox + 0.35 * e.heat.value)
        stroke.shader = LinearGradient(
            0f, (planeScreenY - 70 * g.s).toFloat(), 0f, (planeScreenY + 70 * g.s).toFloat(),
            intArrayOf(Color.argb(0, 255, 106, 58), Color.argb((255 * 0.9 * rimHeat).roundToInt(), 255, 150, 70), Color.argb(0, 255, 106, 58)),
            null, Shader.TileMode.CLAMP,
        )
        stroke.strokeWidth = 1.6f * s
        c.drawPath(path, stroke)
        stroke.shader = null

        // ---- the capsule's content: label, the bar made of matter, the pill ----
        c.save()
        val cxc = (g.capL + g.capW / 2).toFloat()
        c.translate(cxc, cy.toFloat())
        c.scale(sc.toFloat(), sc.toFloat())
        c.translate(-cxc, -cy.toFloat())
        val labelY = (cy - g.capH / 2 + Theme.CAP_LABEL_Y * g.s).toFloat()
        val labelX = (g.capL + Theme.CAP_LABEL_X * g.s).toFloat()
        text.typeface = regular
        text.textSize = (Theme.CAP_LABEL_SIZE * g.s).toFloat()
        text.color = Theme.ink
        val held = e.held.value
        if (1 - held > 0.004) {
            text.alpha = (255 * (1 - held)).roundToInt()
            c.drawText(Theme.TITLE, labelX, labelY, text)
        }
        if (held > 0.004) {
            text.color = Theme.ink
            text.alpha = (255 * held).roundToInt()
            c.drawText("${Theme.LAYER} ${M.layerAt(hh)} ${Theme.OF}", labelX, labelY, text)
        }
        text.alpha = 255
        val barTop = cy - g.capH / 2
        val bs = barShader
        if (bs != null && Build.VERSION.SDK_INT >= 33) {
            bs.draw(
                c, (g.capL + g.barX - 6).toFloat(), barTop.toFloat(), (g.capL + g.barX + g.barW + 6).toFloat(), (barTop + g.capH).toFloat(),
                (g.capL + g.barX).toFloat(), (barTop + g.barY).toFloat(), s, (g.barW / g.s).toFloat(), (g.barH / g.s).toFloat(),
                (g.pillX(hh) / g.s).toFloat(), (e.over * g.s).toFloat(), e.tail.toFloat(), e.flash.toFloat(),
            )
        } else {
            drawBarFallback(c, g, e, barTop)
        }
        val pillCx = (g.capL + g.barX + g.pillX(hh)).toFloat()
        val pillCy = (barTop + g.barY + g.barH / 2).toFloat()
        fill.color = Theme.shadow(0.16)
        fill.maskFilter = bf4
        c.drawCircle(pillCx, pillCy + 2f * s, Theme.PILL_D.toFloat() / 2 * s, fill)
        fill.maskFilter = null
        fill.color = Theme.white
        c.drawCircle(pillCx, pillCy, Theme.PILL_D.toFloat() / 2 * s, fill)
        text.typeface = medium
        val pct = "${M.percentAt(hh).roundToInt()}%"
        text.color = Theme.ink
        c.drawText(pct, pillCx - text.measureText(pct) / 2, (pillCy + 0.35 * Theme.CAP_LABEL_SIZE * g.s).toFloat(), text)
        c.restore()
    }

    /** The blue arrow with its red and green stubs, rising from the seam; it stretches on touch and with speed and turns with the finger. */
    private fun drawGizmo(c: Canvas, g: PrintLineScrubGeo, e: PrintLineScrubEngine, planeY: Double) {
        val s = g.s.toFloat()
        val len = (min(Theme.GIZ_MAX, Theme.GIZ_REST + (Theme.GIZ_TOUCH - Theme.GIZ_REST) * e.press.value + 0.06 * abs(e.speed)) * g.s).toFloat()
        val a = (255 * (0.7 + 0.3 * e.press.value)).roundToInt()
        val layer = c.saveLayerAlpha(g.axisX.toFloat() - 80f * s, planeY.toFloat() - 160f * s, g.axisX.toFloat() + 80f * s, planeY.toFloat() + 160f * s, a)
        c.translate(g.axisX.toFloat(), planeY.toFloat())
        c.scale(1f, e.gizDir.toFloat())
        c.save()
        c.scale(s, s)
        stroke.strokeWidth = 2f
        stroke.color = Theme.axisRed
        c.drawPath(axisRed, stroke)
        stroke.color = Theme.axisGreen
        c.drawPath(axisGreen, stroke)
        c.restore()
        stroke.color = Theme.arrow
        stroke.strokeWidth = (Theme.GIZ_SHAFT * g.s).toFloat()
        val shaftEnd = -len + Theme.GIZ_HEAD.toFloat() * s
        c.drawLine(0f, 0f, 0f, shaftEnd, stroke)
        c.save()
        c.translate(0f, shaftEnd)
        c.scale(s, s)
        fill.color = Theme.arrow
        c.drawPath(arrowHead, fill)
        c.restore()
        c.restoreToCount(layer)
    }

    /** A strip of Liquid Glass over the page: a soft shadow, blur, tint, a bright upper rim. `idx`: which blur node (1 header, 2 dock). */
    private fun glassStrip(c: Canvas, g: PrintLineScrubGeo, idx: Int, x: Double, y: Double, w: Double, h: Double, r: Double, density: Float, e: PrintLineScrubEngine) {
        val s = g.s.toFloat()
        rr.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        path.rewind()
        path.addRoundRect(rr, r.toFloat(), r.toFloat(), Path.Direction.CW)
        c.save()
        c.translate(0f, 6f * s)
        fill.color = Theme.shadow(0.10)
        fill.maskFilter = bf10
        c.drawPath(path, fill)
        c.restore()
        fill.maskFilter = null
        c.save()
        c.clipPath(path)
        val pad = 30f * s
        val l = x.toFloat() - pad
        val t = y.toFloat() - pad
        val rgt = (x + w).toFloat() + pad
        val btm = (y + h).toFloat() + pad
        val blurred = blurs?.get(idx)?.draw(c, density, l, t, rgt, btm, 10f * s) { rc ->
            drawScene(rc, idx, l, t, rgt, btm, sceneState(g, e, density, false, 0.0, 1.0))
        } ?: false
        if (!blurred) drawScene(c, idx, l, t, rgt, btm, sceneState(g, e, density, false, 0.0, 1.0))
        fill.color = Color.argb(87, 255, 255, 255)
        c.drawRect(l, t, rgt, btm, fill)
        c.restore()
        stroke.shader = LinearGradient(
            0f, y.toFloat(), 0f, (y + h).toFloat(), intArrayOf(Color.argb(242, 255, 255, 255), Color.argb(102, 255, 255, 255), Color.argb(64, 255, 255, 255)),
            null, Shader.TileMode.CLAMP,
        )
        stroke.strokeWidth = s
        c.drawPath(path, stroke)
        stroke.shader = null
    }

    /** The three tools in the dock: select (yellow), hand (pale blue), more. */
    private fun dockButtons(c: Canvas, g: PrintLineScrubGeo, x: Double, y: Double) {
        val s = g.s.toFloat()
        val d = (Theme.DOCK_BTN_D * g.s).toFloat()
        val step = ((Theme.DOCK_BTN_D + Theme.DOCK_BTN_GAP) * g.s).toFloat()
        val x0 = x.toFloat()
        val y0 = y.toFloat()
        fill.color = Theme.yellow
        c.drawCircle(x0 + d / 2, y0 + d / 2, d / 2, fill)
        c.save()
        c.translate(x0 + d / 2, y0 + d / 2)
        c.scale(0.8f * s, 0.8f * s)
        c.translate(-12f, -12f)
        fill.color = Theme.iconInk
        c.drawPath(cursor, fill)
        stroke.color = Theme.iconInk
        stroke.strokeWidth = 1.4f
        c.drawPath(cursor, stroke)
        c.restore()
        fill.color = Theme.handBlue
        c.drawCircle(x0 + step + d / 2, y0 + d / 2, d / 2, fill)
        c.save()
        c.translate(x0 + step + d / 2, y0 + d / 2)
        c.scale(0.8f * s, 0.8f * s)
        c.translate(-12f, -12f)
        stroke.color = Theme.iconInk
        stroke.strokeWidth = 2.4f
        for (p in handFingers) c.drawPath(p, stroke)
        c.drawPath(handPalm, stroke)
        c.restore()
        fill.color = Color.argb(179, 255, 255, 255)
        c.drawCircle(x0 + 2 * step + d / 2, y0 + d / 2, d / 2, fill)
        fill.color = Theme.iconInk
        c.drawCircle(x0 + 2 * step + d / 2, y0 + d / 2, 2.2f * s, fill)
    }

    /** Below API 33 there is no dot shader: a solid yellow bar up to the pill and a row of dots beyond it. */
    private fun drawBarFallback(c: Canvas, g: PrintLineScrubGeo, e: PrintLineScrubEngine, barTop: Double) {
        val s = g.s.toFloat()
        val x0 = (g.capL + g.barX).toFloat()
        val y0 = (barTop + g.barY).toFloat()
        val px = g.pillX(e.h.value).toFloat()
        fill.color = Theme.yellow
        rr.set(x0, y0 + g.barH.toFloat() * 0.1f, x0 + px, y0 + g.barH.toFloat() * 0.9f)
        c.drawRoundRect(rr, g.barH.toFloat() * 0.4f, g.barH.toFloat() * 0.4f, fill)
        val pitch = 5f * s
        var x = x0 + px + 22f * s
        while (x < x0 + g.barW.toFloat() - 4f * s) {
            c.drawCircle(x, y0 + g.barH.toFloat() / 2, 1.6f * s, fill)
            x += pitch
        }
    }
}
