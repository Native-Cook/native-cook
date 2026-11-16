package com.ngocdevv.nativecook.blocks.modals.glasspourmenu

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.modals.glasspourmenu.GlassPourMenuTheme as Theme
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Below API 33 there is no AGSL, so the page and the glass are drawn from paths and gradients: the flower as five discs through O (one
 * radial ramp where a petal lies alone, the deeper ramp where two overlap, the crisp brown outline on one side of each circle and the thin
 * crimson fold line on the other, near the centre), and the glass as one unioned shape (bead, neck, panel, satellite) filled with the
 * magenta / pink tint, a lit lower rim, a darker upper inner rim, a Fresnel stroke, a soft shadow, the white-hot pour and the clear
 * selection lozenge. No refraction and no frost blur of the page. Coordinates are dp.
 */
internal class GlassPourMenuFallback {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val shape = Path()
    private val part = Path()

    // the flower in its own coordinates (the page's q space, before the 1.253 world scale)
    private val circles = Array(5) { i -> Path().apply { addCircle(Theme.DISCS[i][0].toFloat(), Theme.DISCS[i][1].toFloat(), hypot(Theme.DISCS[i][0], Theme.DISCS[i][1]).toFloat(), Path.Direction.CW) } }
    private val overlaps = ArrayList<Path>().also { list ->
        for (i in 0 until 5) for (j in i + 1 until 5) {
            val p = Path(circles[i])
            if (p.op(circles[j], Path.Op.INTERSECT) && !p.isEmpty) list.add(p)
        }
    }
    private val crisp = Array(5) { i ->
        val dx = Theme.DISCS[i][0]
        val dy = Theme.DISCS[i][1]
        val r = hypot(dx, dy)
        val nAngle = Math.toDegrees(atan2(-dx / r, dy / r)) // angle of n = (d.y, -d.x)
        Path().apply {
            rect.set((dx - r).toFloat(), (dy - r).toFloat(), (dx + r).toFloat(), (dy + r).toFloat())
            arcTo(rect, (nAngle - 90).toFloat(), 180f, true)
        }
    }
    private val foldLines = Array(5) { i ->
        val dx = Theme.DISCS[i][0]
        val dy = Theme.DISCS[i][1]
        val r = hypot(dx, dy)
        val nAngle = Math.toDegrees(atan2(-dx / r, dy / r))
        Path().apply {
            rect.set((dx - r).toFloat(), (dy - r).toFloat(), (dx + r).toFloat(), (dy + r).toFloat())
            arcTo(rect, (nAngle + 90).toFloat(), 180f, true)
        }
    }
    private val one = RadialGradient(
        0f, 0f, 380f, intArrayOf(Theme.petalCore, Theme.petalCore, Theme.petalLilac, Theme.petalRose, Theme.petalCoral, Theme.petalPeach),
        floatArrayOf(0f, 44f / 380, 100f / 380, 180f / 380, 260f / 380, 1f), Shader.TileMode.CLAMP,
    )
    private val two = RadialGradient(
        0f, 0f, 300f, intArrayOf(Theme.petalCore, Theme.petalCore, Theme.foldLight, Theme.foldDeep, Theme.foldEnd, Theme.petalCoral),
        floatArrayOf(0f, 44f / 300, 95f / 300, 160f / 300, 215f / 300, 1f), Shader.TileMode.CLAMP,
    )
    private val shadowBlur = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)

    private fun withAlpha(c: Int, a: Double) = Color.argb((a.coerceIn(0.0, 1.0) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    private fun mixC(a: Int, b: Int, t: Double): Int {
        val k = t.coerceIn(0.0, 1.0)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt(),
        )
    }

    /** The flower, turned by `twist` rad about O. */
    fun page(c: Canvas, w: Float, h: Float, ox: Float, oy: Float, twist: Double) {
        c.drawColor(Theme.cream)
        c.save()
        c.translate(ox, oy)
        c.rotate(Math.toDegrees(twist).toFloat())
        c.scale(1.253f, 1.253f)
        fill.shader = one
        for (p in circles) c.drawPath(p, fill)
        fill.shader = two
        for (p in overlaps) c.drawPath(p, fill)
        fill.shader = null
        stroke.color = withAlpha(Theme.edge, 0.92)
        stroke.strokeWidth = 0.9f
        for (p in crisp) c.drawPath(p, stroke)
        c.save()
        part.rewind()
        part.addCircle(0f, 0f, 190f, Path.Direction.CW)
        c.clipPath(part)
        stroke.color = withAlpha(Theme.fold, 0.8)
        stroke.strokeWidth = 0.7f
        for (p in foldLines) c.drawPath(p, stroke)
        c.restore()
        c.restore()
    }

    /** The glass. `alpha` fades it (Reduce Motion's endpoints). */
    fun glass(c: Canvas, w: Float, h: Float, s: GlassPourMenuGlassState, ox: Float, oy: Float, dir: Int, tint: Int, alpha: Float) {
        val g = s.g
        val d = dir.toFloat()
        fun px(x: Double) = (ox + x).toFloat()
        fun py(y: Double) = (oy + d * y).toFloat()
        shape.rewind()
        shape.addCircle(px(g.beadX), py(g.beadY), g.beadR.toFloat(), Path.Direction.CW)
        if (g.neckR > 0.001) {
            val y0 = py(min(g.neckA, g.neckB))
            val y1 = py(max(g.neckA, g.neckB))
            rect.set(px(-g.neckR), min(y0, y1), px(g.neckR), max(y0, y1))
            part.rewind()
            part.addRoundRect(rect, g.neckR.toFloat(), g.neckR.toFloat(), Path.Direction.CW)
            shape.op(part, Path.Op.UNION)
        }
        rect.set(px(g.boxX - g.boxW), py(g.boxY) - g.boxH.toFloat(), px(g.boxX + g.boxW), py(g.boxY) + g.boxH.toFloat())
        part.rewind()
        part.addRoundRect(rect, g.boxR.toFloat(), g.boxR.toFloat(), Path.Direction.CW)
        shape.op(part, Path.Op.UNION)
        if (s.satellite[2] > 0.001) {
            part.rewind()
            part.addCircle(px(s.satellite[0]), py(s.satellite[1]), s.satellite[2].toFloat(), Path.Direction.CW)
            shape.op(part, Path.Op.UNION)
        }
        val layer = if (alpha < 0.999f) c.saveLayerAlpha(0f, 0f, w, h, (255 * alpha).toInt()) else -1
        // shadow and halo
        fill.color = withAlpha(Theme.shadow, 0.22 * (0.4 + 0.6 * g.thick))
        fill.maskFilter = shadowBlur
        c.save()
        c.translate(0f, 5f * d)
        c.drawPath(shape, fill)
        c.restore()
        fill.maskFilter = null
        // the tint: magenta drop -> pink panel (lighter toward its bottom)
        val panel = mixC(Theme.panelTint, Theme.panelLow, 0.5)
        val body = mixC(mixC(tint, panel, g.open), Theme.hot, g.hot * 0.5)
        val top = py(min(g.beadY - g.beadR, g.boxY - g.boxH))
        val bottom = py(max(g.beadY + g.beadR, g.boxY + g.boxH))
        val a = 0.56 + 0.22 * g.frost - 0.12 * g.hot
        fill.shader = LinearGradient(
            0f, min(top, bottom), 0f, max(top, bottom),
            intArrayOf(withAlpha(mixC(tint, Theme.panelTint, g.open), a), withAlpha(body, a), withAlpha(mixC(body, Theme.panelLow, g.open), a + 0.04)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawPath(shape, fill)
        fill.shader = null
        c.save()
        c.clipPath(shape)
        // light from above: a darker upper inner rim, a bright caustic along the lower rim
        val lo = if (dir == 1) bottom else top
        val up = if (dir == 1) top else bottom
        fill.shader = LinearGradient(0f, lo, 0f, lo - 14f * d, withAlpha(Theme.caustic, 0.8), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(ox - 400f, min(lo, lo - 14f * d), ox + 400f, max(lo, lo - 14f * d), fill)
        fill.shader = LinearGradient(0f, up, 0f, up + 12f * d, withAlpha(Theme.dropDeep, 0.34 * (1 - g.open)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(ox - 400f, min(up, up + 12f * d), ox + 400f, max(up, up + 12f * d), fill)
        fill.shader = null
        // the clear selection lozenge
        if (s.lens[2] > 0.001) {
            val hw = max(g.boxW - (16 + (7 - 16) * s.lens[2]), 1.0)
            val hh = s.lens[1] * (0.6 + 0.34 * s.lens[2])
            rect.set(px(g.boxX - hw), py(s.lens[0]) - hh.toFloat(), px(g.boxX + hw), py(s.lens[0]) + hh.toFloat())
            fill.color = withAlpha(Theme.rim, (0.30 + 0.35 * s.lens[3]) * s.lens[2])
            c.drawRoundRect(rect, hh.toFloat(), hh.toFloat(), fill)
            stroke.color = withAlpha(Theme.rim, 0.55 * s.lens[2])
            stroke.strokeWidth = 1f
            c.drawRoundRect(rect, hh.toFloat(), hh.toFloat(), stroke)
        }
        c.restore()
        // the Fresnel rim
        stroke.color = withAlpha(Theme.rim, 0.55)
        stroke.strokeWidth = 1.1f
        c.drawPath(shape, stroke)
        if (layer >= 0) c.restoreToCount(layer)
    }
}
