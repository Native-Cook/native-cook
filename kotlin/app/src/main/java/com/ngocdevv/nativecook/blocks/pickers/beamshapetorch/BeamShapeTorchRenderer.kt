package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.util.Log
import com.ngocdevv.nativecook.blocks.pickers.beamshapetorch.BeamShapeTorchMotion as M
import com.ngocdevv.nativecook.blocks.pickers.beamshapetorch.BeamShapeTorchTheme as Theme
import kotlin.math.max

internal enum class BeamShapeTorchGlyph { Torch, Spot }

/**
 * The control's drawing (RN `BeamControl`'s canvas), layer for layer on a transparent canvas the host stacks over the page: the page
 * following the light (shade, a veil, a ceiling halo), the capsule's ambient and contact shadows, the capsule itself (the beam shader),
 * its hairline rim, and, clipped to the capsule, the dotted arc, the arc, the sun and the glyph. Everything is in dp: the caller
 * scales the canvas by the density.
 */
internal class BeamShapeTorchRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val capsule = Path()
    private val arcP = Path()
    private val dotArcP = Path()
    private val sunP = BeamShapeTorchShapes.sun()
    private val torchCap = BeamShapeTorchShapes.torchCap()
    private val torchBody = BeamShapeTorchShapes.torchBody()
    private val spotLens = BeamShapeTorchShapes.spotLens()
    private val spotHead = BeamShapeTorchShapes.spotHead()
    private val spotStem = BeamShapeTorchShapes.spotStem()
    private val fallback by lazy { BeamShapeTorchFallback() }
    private val shader: BeamShapeTorchShaderPass? = if (Build.VERSION.SDK_INT >= 33) {
        try {
            BeamShapeTorchShaderPass()
        } catch (error: IllegalArgumentException) {
            Log.w("BeamShapeTorch", "Runtime shader unavailable; using the Canvas fallback", error)
            null
        }
    } else null

    private var blurS = -1.0
    private var ambientBlur: BlurMaskFilter? = null
    private var contactBlur: BlurMaskFilter? = null
    private val glyphBlur10 = blur(10.0)
    private val glyphBlur8 = blur(8.0)

    /** A Skia blur sigma as the platform's blur radius (it converts back with sigma = 0.57735 r + 0.5). */
    private fun blur(sigma: Double) = BlurMaskFilter(max(((sigma - 0.5) / 0.57735).toFloat(), 0.1f), BlurMaskFilter.Blur.NORMAL)

    fun draw(
        c: Canvas,
        l: Theme.Layout,
        e: BeamShapeTorchEngine,
        th: Theme.Resolved,
        glyph: BeamShapeTorchGlyph,
        density: Float,
    ) {
        val k = e.look
        val s = l.s
        if (blurS != s) {
            blurS = s
            ambientBlur = blur(Theme.AMBIENT_BLUR * s * 0.5)
            contactBlur = blur(Theme.CONTACT_BLUR * s * 0.5)
        }
        val o = M.opticsOf(k.b, k.w, e.aMin, e.aMax)
        val cr = e.capRect(k)
        val sc = e.innerScale(k)
        val show = e.innerShow(k)
        val cx = l.cap.x + l.cap.w / 2
        val top = l.cap.y
        val lensY = l.iy(Theme.LENS_Y)
        val w = l.w.toFloat()
        val h = l.h.toFloat()

        val fade = e.reducedFade.value
        val outer = if (fade < 0.999) c.saveLayerAlpha(0f, 0f, w, h, (255 * fade.coerceIn(0.0, 1.0)).toInt()) else -1

        // ---- the page follows the light: shade when the lamp is off, a veil round the capsule while it burns, a halo at the top
        // edge where the pencil lands. (Over a transparent page layer these read as plain alpha, which is what they are here.)
        if (th.spill > 0) {
            val darkA = M.SPILL_DARK * th.spill * (1 - M.clamp01(k.hot))
            if (darkA > 0.002) {
                fill.shader = null
                fill.color = Theme.shadow
                fill.alpha = (255 * darkA).toInt()
                c.drawRect(0f, 0f, w, h, fill)
            }
            val glowA = M.SPILL_GLOW * th.spill * M.clamp01(k.b) * M.clamp01(k.w) * show
            if (glowA > 0.002) {
                val r = (M.SPILL_GLOW_RADIUS * s).toFloat()
                fill.shader = RadialGradient(
                    cx.toFloat(), (top + M.SPILL_GLOW_FROM * s).toFloat(), r,
                    intArrayOf(Theme.spill, Theme.spill, Color.argb(0, 255, 255, 255)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                )
                fill.alpha = (255 * glowA).toInt()
                c.drawRect(0f, 0f, w, h, fill)
            }
            val haloA = M.SPILL_HALO * th.spill * M.landing(o.reach, k.w, k.b) * show
            if (haloA > 0.002) {
                val r = (M.SPILL_HALO_RADIUS * s).toFloat()
                fill.shader = RadialGradient(
                    cx.toFloat(), top.toFloat(), r, intArrayOf(Theme.ceiling, Color.argb(0, 0xF6, 0xC9, 0xE6)), null,
                    Shader.TileMode.CLAMP,
                )
                fill.alpha = (255 * haloA).toInt()
                c.drawRect(0f, 0f, w, h, fill)
            }
            fill.shader = null
            fill.alpha = 255
        }

        // ---- the capsule floats: an ambient shadow that sinks as it opens and deepens with the lamp off, and a tight contact shadow
        BeamShapeTorchShapes.squircle(capsule, cr.x, cr.y, cr.w, cr.h, cr.r)
        fill.color = Theme.shadow
        fill.alpha = (255 * M.mix(M.SHADOW_OFF, M.SHADOW_ON, M.clamp01(k.b))).toInt()
        fill.maskFilter = ambientBlur
        c.save()
        c.translate(0f, (Theme.AMBIENT_Y * s * M.smooth(0.2, 1.0, k.m)).toFloat())
        c.drawPath(capsule, fill)
        c.restore()
        fill.color = Theme.shadow
        fill.alpha = (255 * Theme.CONTACT_ALPHA).toInt()
        fill.maskFilter = contactBlur
        c.save()
        c.translate(0f, (Theme.CONTACT_Y * s).toFloat())
        c.drawPath(capsule, fill)
        c.restore()
        fill.maskFilter = null
        fill.alpha = 255

        // ---- the capsule: black glass lit by the beam
        val lensX = cx
        val lensYs = top + (lensY - top) * sc
        val sS = s * sc
        val sh = shader
        if (sh != null && Build.VERSION.SDK_INT >= 33) {
            sh.draw(
                c, capsule, lensX, lensYs, sS, k, o, cr.x, cr.y, cr.w, cr.h, cr.r, show, e.reduce, th.glass, th.light, th.fringe, density,
            )
        } else {
            c.save()
            c.clipPath(capsule)
            fallback.draw(
                c, cr.x.toFloat(), cr.y.toFloat(), cr.w.toFloat(), cr.h.toFloat(), lensX.toFloat(), lensYs.toFloat(), sS.toFloat(),
                show, k, o, th.glass, th.light, e.reduce,
            )
            c.restore()
        }
        // the glass edge, stroked at one physical pixel
        stroke.shader = null
        stroke.color = Theme.rim
        stroke.alpha = (255 * 0.06).toInt()
        stroke.strokeWidth = 1f / density
        stroke.pathEffect = null
        stroke.xfermode = null
        c.drawPath(capsule, stroke)

        // ---- the content: shrinks toward the island with the capsule, fades before it reaches it
        if (show > 0.003) {
            c.save()
            c.clipPath(capsule)
            val inner = if (show < 0.999) c.saveLayerAlpha(cr.x.toFloat() - 2f, cr.y.toFloat() - 2f, (cr.x + cr.w).toFloat() + 2f, (cr.y + cr.h).toFloat() + 2f, (255 * show).toInt()) else -1
            c.translate(cx.toFloat(), top.toFloat())
            c.scale(sc.toFloat(), sc.toFloat())
            c.translate(-cx.toFloat(), -top.toFloat())
            val arcColor = th.arc
            // screen blend: where the pencil lands on the ceiling the arc melts into the light instead of reading as a dark bar
            BeamShapeTorchShapes.arc(dotArcP, cx, lensY - o.reachMax * s, o.hwMax * s, o.sagMax * s)
            stroke.color = arcColor
            stroke.alpha = (255 * o.dots * 0.85).toInt()
            stroke.strokeWidth = (Theme.DOTS_SIZE * s).toFloat()
            stroke.pathEffect = DashPathEffect(floatArrayOf(0.01f, (Theme.DOTS_PITCH * s).toFloat()), 0f)
            stroke.xfermode = screen
            if (stroke.alpha > 1) c.drawPath(dotArcP, stroke)
            stroke.pathEffect = null
            BeamShapeTorchShapes.arc(arcP, cx, lensY - o.reach * s, o.hw * s, o.sag * s)
            stroke.color = arcColor
            stroke.alpha = (255 * o.arcAlpha * 0.78).toInt()
            stroke.strokeWidth = (Theme.ARC_STROKE * s).toFloat()
            c.drawPath(arcP, stroke)
            stroke.xfermode = null
            // the sun at the arc's end (the handle)
            val g = M.arcGeometry(cx, lensY - o.reach * s, o.hw * s, o.sag * s)
            val along = Theme.SUN_ALONG * s
            val grab = e.sunGrab.value
            c.save()
            c.translate((g.ex + g.tx * along).toFloat(), (g.ey + g.ty * along).toFloat())
            val sunScale = (s * (1 + 0.25 * grab)).toFloat()
            c.scale(sunScale, sunScale)
            stroke.color = arcColor
            stroke.alpha = (255 * kotlin.math.min(o.arcAlpha * 0.95 + 0.25 * grab, 1.0)).toInt()
            stroke.strokeWidth = Theme.SUN_STROKE.toFloat()
            c.drawPath(sunP, stroke)
            c.restore()
            // the glyph under the lens
            c.save()
            c.translate(l.ix(Theme.GLYPH_X).toFloat(), l.iy(Theme.GLYPH_Y).toFloat())
            c.scale(s.toFloat(), s.toFloat())
            drawGlyph(c, glyph, M.clamp01((o.glyph - 0.45) / 0.55), M.clamp01(k.hot))
            c.restore()
            if (inner >= 0) c.restoreToCount(inner)
            c.restore()
        }
        if (outer >= 0) c.restoreToCount(outer)
    }

    private fun drawGlyph(c: Canvas, glyph: BeamShapeTorchGlyph, lit: Double, hot: Double) {
        fill.shader = null
        val halo = (if (glyph == BeamShapeTorchGlyph.Torch) 0.6 else 0.5) * hot
        val head = if (glyph == BeamShapeTorchGlyph.Torch) torchBody else spotHead
        val lens = if (glyph == BeamShapeTorchGlyph.Torch) torchCap else spotLens
        // the glyph glows in its own beam's light; off, it falls to 45 %
        if (halo > 0.004) {
            fill.color = Theme.glyphLow
            fill.alpha = (255 * halo).toInt()
            fill.maskFilter = glyphBlur10
            c.drawPath(head, fill)
            fill.color = Theme.glyphTop
            fill.alpha = (255 * halo).toInt()
            fill.maskFilter = glyphBlur8
            c.drawPath(lens, fill)
            fill.maskFilter = null
        }
        fill.color = Theme.glyphOff
        c.drawPath(lens, fill)
        c.drawPath(head, fill)
        if (glyph == BeamShapeTorchGlyph.Spot) c.drawPath(spotStem, fill)
        if (lit > 0.004) {
            val a = (255 * lit).toInt()
            fill.color = Theme.glyphTop
            fill.alpha = a
            c.drawPath(lens, fill)
            val y0 = if (glyph == BeamShapeTorchGlyph.Torch) 11f else 10f
            val y1 = if (glyph == BeamShapeTorchGlyph.Torch) Theme.GLYPH_H.toFloat() else 36f
            fill.shader = LinearGradient(0f, y0, 0f, y1, Theme.glyphTop, Theme.glyphLow, Shader.TileMode.CLAMP)
            fill.alpha = a
            c.drawPath(head, fill)
            if (glyph == BeamShapeTorchGlyph.Spot) {
                fill.shader = null
                fill.color = Theme.glyphLow
                fill.alpha = a
                c.drawPath(spotStem, fill)
            }
            fill.shader = null
        }
        fill.alpha = 255
    }
}
