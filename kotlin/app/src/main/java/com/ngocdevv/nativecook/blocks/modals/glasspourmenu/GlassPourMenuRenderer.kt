package com.ngocdevv.nativecook.blocks.modals.glasspourmenu

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.modals.glasspourmenu.GlassPourMenuMotion as M
import com.ngocdevv.nativecook.blocks.modals.glasspourmenu.GlassPourMenuTheme as Theme
import kotlin.math.PI
import kotlin.math.max

/**
 * The screen's drawing (RN `GlassPourMenu`'s canvas): the flower and the glass in one shader pass (or the path fallback), the three
 * strokes of the button's glyph (a menu that turns into a cross), and the rows (label, icon, the rule over Delete) that appear only
 * once the panel is frosted. Under Reduce Motion the closed and the opened glass are two endpoints cross-faded. Everything is in dp:
 * the caller scales the canvas by the density.
 */
internal class GlassPourMenuRenderer(assets: AssetManager) {
    private val typeface = Typeface.createFromAsset(assets, "glasspourmenu/glasspourmenu_Inter-Medium.ttf")
    private val shader: GlassPourMenuShaderPass? = if (Build.VERSION.SDK_INT >= 33) {
        try {
            GlassPourMenuShaderPass()
        } catch (error: IllegalArgumentException) {
            Log.w("GlassPourMenu", "Runtime shader unavailable; using the Canvas fallback", error)
            null
        }
    } else null
    private val fallback by lazy { GlassPourMenuFallback() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = this@GlassPourMenuRenderer.typeface
        fontFeatureSettings = "'kern' 0, 'liga' 0"
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconPaths = HashMap<String, Path>()
    private val labels = HashMap<String, String>()
    private var labelKey = ""

    private fun iconFor(name: String?): Path? {
        if (name == null) return null
        return iconPaths.getOrPut(name) {
            try {
                PathParser.createPathFromPathData(Theme.ICONS[name] ?: name)
            } catch (error: RuntimeException) {
                Path()
            }
        }
    }

    private fun measure(s: String) = text.measureText(s)

    /** Rows' labels, shortened with an ellipsis to fit the panel (measured once per size). */
    private fun label(item: GlassPourMenuItem, g: Theme.Geometry, size: Double): String {
        val key = "$size|${g.panelHalf}"
        if (key != labelKey) {
            labelKey = key
            labels.clear()
        }
        return labels.getOrPut(item.key) {
            text.textSize = size.toFloat()
            val max = g.panelHalf * 2 - Theme.LABEL_X - Theme.ICON_X - Theme.ICON - 12
            var value = item.label
            if (measure(value) <= max) return@getOrPut value
            while (value.isNotEmpty() && measure("$value…") > max) value = value.dropLast(1)
            "$value…"
        }
    }

    fun draw(
        c: Canvas,
        w: Float,
        h: Float,
        e: GlassPourMenuEngine,
        labelSize: Double,
        optics: FloatArray,
        tint: FloatArray,
        tintInt: Int,
    ) {
        val ox = e.ox
        val oy = e.oy
        val dir = e.direction
        val twist = e.twistAngle()
        val sh = shader
        val g = e.g
        if (e.reduce) {
            val fade = e.fade.value.toFloat().coerceIn(0f, 1f)
            val closedGlass = M.glassAt(0.0, 0.0, g)
            val closed = GlassPourMenuGlassState(closedGlass, doubleArrayOf(g.rows[0], 18.0, 0.0, 0.0), 0.0, doubleArrayOf(0.0, 0.0, 0.0))
            val openGlass = M.glassAt(1.0, 0.0, g)
            val opened = GlassPourMenuGlassState(openGlass, doubleArrayOf(e.lensY.value, 18.0, e.lensA.value, e.flash.value), 0.0, doubleArrayOf(0.0, 0.0, 0.0))
            if (sh != null && Build.VERSION.SDK_INT >= 33) {
                sh.draw(c, w, h, 0, closed, ox, oy, dir, optics, tint, 0.0, 1f)
            } else {
                fallback.page(c, w, h, ox.toFloat(), oy.toFloat(), 0.0)
                fallback.glass(c, w, h, closed, ox.toFloat(), oy.toFloat(), dir, tintInt, 1f)
            }
            glyph(c, ox, oy, dir, closedGlass.glyphK, closedGlass.beadY, 0, (1 - fade).toDouble())
            if (fade > 0.002f) {
                if (sh != null && Build.VERSION.SDK_INT >= 33) {
                    sh.draw(c, w, h, 1, opened, ox, oy, dir, optics, tint, 0.0, fade)
                } else {
                    val layer = c.saveLayerAlpha(0f, 0f, w, h, (255 * fade).toInt())
                    fallback.page(c, w, h, ox.toFloat(), oy.toFloat(), 0.0)
                    fallback.glass(c, w, h, opened, ox.toFloat(), oy.toFloat(), dir, tintInt, 1f)
                    c.restoreToCount(layer)
                }
                glyph(c, ox, oy, dir, 1.0, g.beadY, 1, fade.toDouble())
            }
            rows(c, e, labelSize, null, e.fade.value)
            return
        }
        val look = e.look()
        val state = GlassPourMenuGlassState(
            look, doubleArrayOf(e.lensY.value, 18 + 2 * e.dwell.value, e.lensA.value, e.flash.value), e.specTime() * 1.5, e.satellite(look),
        )
        if (sh != null && Build.VERSION.SDK_INT >= 33) {
            sh.draw(c, w, h, 0, state, ox, oy, dir, optics, tint, twist, 1f)
        } else {
            fallback.page(c, w, h, ox.toFloat(), oy.toFloat(), twist)
            fallback.glass(c, w, h, state, ox.toFloat(), oy.toFloat(), dir, tintInt, 1f)
        }
        glyph(c, ox, oy, dir, look.glyphK, look.beadY, -1, 1.0)
        rows(c, e, labelSize, look, 0.0)
    }

    /** The button's menu glyph turning into a cross: the outer bars close onto the middle one (which shrinks and fades), then turn +-45 deg about the same centre. */
    private fun glyph(c: Canvas, ox: Double, oy: Double, dir: Int, k: Double, beadY: Double, endpoint: Int, alpha: Double) {
        val y = if (endpoint == 1) beadY else beadY
        val close = M.smooth(0.0, 0.55, k)
        val turn = M.smooth(0.3, 1.0, k) + max(0.0, k - 1)
        for (line in -1..1) {
            c.save()
            c.translate(ox.toFloat(), (oy + dir * y + line * Theme.GLYPH_GAP * (1 - close)).toFloat())
            c.rotate((line * turn * PI / 4 * 180 / PI).toFloat())
            c.scale(if (line == 0) max(0.001, 1 - close).toFloat() else M.mix(1.0, 0.74, turn).toFloat(), 1f)
            val a = (if (line == 0) 1 - M.smooth(0.0, 0.4, k) else 1.0) * alpha
            stroke.color = Theme.glyph
            stroke.alpha = (255 * a.coerceIn(0.0, 1.0)).toInt()
            stroke.strokeWidth = Theme.GLYPH_STROKE.toFloat()
            c.drawLine((-Theme.GLYPH_W / 2).toFloat(), 0f, (Theme.GLYPH_W / 2).toFloat(), 0f, stroke)
            c.restore()
        }
    }

    private fun rows(c: Canvas, e: GlassPourMenuEngine, labelSize: Double, look: GlassPourMenuGlass?, fade: Double) {
        val g = e.g
        val dir = e.direction
        val ox = e.ox
        val oy = e.oy
        for ((i, item) in e.items.withIndex()) {
            val appear = if (look == null) fade else look.rows.getOrElse(i) { 0.0 }
            val a = appear * (if (item.disabled) 0.35 else 1.0)
            if (a < 0.004) continue
            val color = if (item.destructive) Theme.danger else Theme.ink
            val y = oy + dir * g.rows[i]
            c.save()
            c.translate(0f, (if (look == null) 0.0 else -4.0 * dir * (1 - appear)).toFloat())
            text.textSize = labelSize.toFloat()
            text.color = color
            text.alpha = (255 * a.coerceIn(0.0, 1.0)).toInt()
            c.drawText(label(item, g, labelSize), (ox - g.panelHalf + Theme.LABEL_X).toFloat(), (y + labelSize * Theme.CAP_RATIO / 2).toFloat(), text)
            val icon = iconFor(item.icon)
            if (icon != null) {
                c.save()
                c.translate((ox + g.panelHalf - Theme.ICON_X - Theme.ICON / 2).toFloat(), (y - Theme.ICON / 2).toFloat())
                c.scale((Theme.ICON / 24).toFloat(), (Theme.ICON / 24).toFloat())
                stroke.color = color
                stroke.alpha = (255 * a.coerceIn(0.0, 1.0)).toInt()
                stroke.strokeWidth = (Theme.STROKE * 24 / Theme.ICON).toFloat()
                c.drawPath(icon, stroke)
                c.restore()
            }
            if (item.destructive && i > 0) {
                val ry = (y - dir * g.rowPitch / 2).toFloat()
                stroke.color = Theme.rule
                stroke.alpha = (255 * 0.35 * a.coerceIn(0.0, 1.0)).toInt()
                stroke.strokeWidth = 0.5f
                c.drawLine((ox - g.panelHalf + Theme.RULE_INSET).toFloat(), ry, (ox + g.panelHalf - Theme.RULE_INSET).toFloat(), ry, stroke)
            }
            c.restore()
        }
        text.alpha = 255
        stroke.alpha = 255
    }
}
