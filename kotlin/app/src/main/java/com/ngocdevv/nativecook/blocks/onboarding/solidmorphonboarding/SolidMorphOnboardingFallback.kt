package com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding.SolidMorphOnboardingMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding.SolidMorphOnboardingTheme as Theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Below API 33 (or when the shader cannot be made): the ground and the page UI from the atlas cross-fading between pages, and the solid drawn as a
 * ceramic plate on a dish seen from above at an angle: a soft floor shadow, the dish, the plate (a disc or a rounded polygon) with its side and a lit
 * top, the dark wedge on the split page, and the mark engraved in a darker tone, turning with the lathe. No ray marching, so no real bevels, ambient
 * occlusion or morph between outlines (the two outlines cross-fade), no iris, no copy slide.
 */
internal class SolidMorphOnboardingFallback {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val glyphPaths = HashMap<String, Path>()
    private val src = android.graphics.Rect()
    private val dst = RectF()

    private fun lerp(a: Int, b: Int, t: Double): Int {
        val k = t.coerceIn(0.0, 1.0)
        fun ch(x: Int, y: Int) = Math.round(x + (y - x) * k).toInt()
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    private fun polygon(cx: Float, cy: Float, r: Float, sides: Int, round: Float) {
        path.rewind()
        if (sides < 3) {
            path.addCircle(cx, cy, r, Path.Direction.CW)
            return
        }
        for (i in 0 until sides) {
            val a = -PI / 2 + 2 * PI * i / sides
            val x = (cx + r * cos(a)).toFloat()
            val y = (cy + r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    fun draw(c: Canvas, atlas: Bitmap?, e: SolidMorphOnboardingEngine, pages: List<SolidMorphOnboardingPage>, l: Theme.Layout, spin: Double) {
        val st = M.pageMix(e.p.value, pages.size)
        val f = M.smooth(0.3, 0.7, st.t)
        val from = pages[st.from]
        val to = pages[st.to]
        val ground = lerp(if (from.dark) Theme.ink else Theme.paper, if (to.dark) Theme.ink else Theme.paper, f)
        fill.shader = null
        fill.color = ground
        c.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), fill)
        // the solid
        val cx = l.heroX.toFloat()
        val cy = l.heroY.toFloat()
        val r = (l.heroRadius * 0.8).toFloat()
        val squash = 0.46f
        val light = if (from.dark) 0.0 else 1.0
        val lightMix = light + ((if (to.dark) 0.0 else 1.0) - light) * f
        fill.color = Color.argb((0.22 * (0.4 + 0.6 * lightMix) * 255).toInt(), 0, 0, 0)
        c.drawOval(RectF(cx - r * 1.2f, cy + r * 0.35f, cx + r * 1.2f, cy + r * 0.85f), fill)
        val gap = ((22.0 - 16.0 * M.smooth(0.0, 0.3, st.t) + 16.0 * M.smooth(0.7, 1.0, st.t)) * l.s).toFloat() - e.cushion.value.toFloat() * 18f * l.s.toFloat()
        // the dish
        fill.shader = LinearGradient(cx, cy, cx, cy + r * 0.9f, Theme.clay, Theme.clayShade, Shader.TileMode.CLAMP)
        c.drawOval(RectF(cx - r * 1.05f, cy + gap * 0.2f - r * squash, cx + r * 1.05f, cy + gap * 0.2f + r * squash + 8f), fill)
        for ((page, alpha) in listOf(st.from to (1 - f), st.to to f)) {
            if (alpha <= 0.01) continue
            val spec = pages[page].solid
            c.save()
            c.translate(cx, cy - gap * 0.6f)
            c.scale(1f, squash)
            polygon(0f, 0f, r, spec.sides, spec.roundness.toFloat())
            fill.shader = null
            fill.color = Color.argb((alpha * 255).toInt(), 0x77, 0x77, 0x7D)
            c.save()
            c.translate(0f, r * 0.12f / squash)
            fill.pathEffect = if (spec.sides >= 3) CornerPathEffect(r * 0.15f) else null
            c.drawPath(path, fill)
            c.restore()
            fill.shader = RadialGradient(-r * 0.3f, -r * 0.3f, r * 1.4f, intArrayOf(Theme.clay, Theme.clayShade), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            fill.alpha = (alpha * 255).toInt()
            c.drawPath(path, fill)
            fill.pathEffect = null
            fill.shader = null
            if (spec.split) {
                c.save()
                c.clipPath(path)
                fill.color = Color.argb((alpha * 0.92 * 255).toInt(), Color.red(Theme.glaze), Color.green(Theme.glaze), Color.blue(Theme.glaze))
                c.drawOval(RectF(-r * 0.05f, -r * 0.95f, r * 1.05f, r * 0.1f), fill)
                c.restore()
            }
            // the engraved mark, turning with the lathe
            val glyph = glyphPaths.getOrPut(pages[page].glyph) { PathParser.createPathFromPathData(pages[page].glyph) }
            val relief = (1 - M.smooth(0.1, 0.45, st.t)).takeIf { page == st.from } ?: M.smooth(0.55, 0.9, st.t)
            c.save()
            val k = r * 1.0f / 100f
            c.rotate((((st.from + M.ease(st.t)) * spin * 360) % 360).toFloat())
            c.scale(k, k)
            c.translate(-50f, -50f)
            stroke.color = Color.argb((alpha * relief.coerceIn(0.0, 1.0) * 0.7 * 255).toInt(), 20, 20, 24)
            stroke.strokeWidth = 6f
            c.drawPath(glyph, stroke)
            c.restore()
            c.restore()
        }
        // the page UI over the ground (the copy cross-fades)
        if (atlas != null) {
            val ph = atlas.height / pages.size
            for ((page, alpha) in listOf(st.from to (1 - f), st.to to f)) {
                if (alpha <= 0.01) continue
                src.set(0, page * ph, atlas.width, (page + 1) * ph)
                dst.set(0f, 0f, l.w.toFloat(), l.h.toFloat())
                bmpPaint.alpha = (alpha * 255).toInt()
                c.drawBitmap(atlas, src, dst, bmpPaint)
            }
        }
    }
}
