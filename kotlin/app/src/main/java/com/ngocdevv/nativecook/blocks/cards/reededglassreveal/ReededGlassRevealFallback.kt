package com.ngocdevv.nativecook.blocks.cards.reededglassreveal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.cards.reededglassreveal.ReededGlassRevealTheme as Theme

/**
 * Below API 33 there is no shader: the glass is the portrait drawn through 22 clipped flutes (a deep flute shows a mirrored, magnified strip of the
 * scene about its own centre, a flat one the sharp photo, cross-faded by its depth), each with the cylinder's dark flanks, a faint glint on its lit
 * crest and a dark seam; the capsule is a dark vertical gradient with the pale haze of its refracting ends, the 1 dp highlight along its top that the
 * front's specular travels, and the white button's and the card's glow. No real refraction, chroma split or noisy glints.
 */
internal class ReededGlassRevealFallback {
    private val img = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()
    private val rect = RectF()

    fun glass(c: Canvas, portrait: Bitmap, soft: Bitmap, l: Theme.Layout, look: ReededGlassRevealLook, area: FloatArray) {
        dst.set(l.imgX.toFloat(), l.imgY.toFloat(), (l.imgX + l.imgW).toFloat(), (l.imgY + l.imgH).toFloat())
        for (i in 0 until Theme.RIBS) {
            val e0 = l.edges[i].toFloat()
            val e1 = l.edges[i + 1].toFloat()
            val cx = (e0 + e1) / 2
            val hw = (e1 - e0) / 2
            val da = ReededGlassRevealMotion.clamp01(look.depths[i])
            c.save()
            c.clipRect(e0, area[1], e1, area[1] + area[3])
            // the flat flute shows the photo as it is
            img.alpha = 255
            c.drawBitmap(portrait, null, dst, img)
            if (da > 0.01) {
                // the deep flute: a mirrored strip of the soft photo about its own centre
                c.save()
                c.scale(-0.787f, 1f, cx, 0f)
                img.alpha = (da * 255).toInt()
                c.drawBitmap(soft, null, dst, img)
                c.restore()
                // dark flanks, a lit crest
                fill.shader = LinearGradient(
                    e0, 0f, e1, 0f, intArrayOf(Color.argb((da * 0.3 * 255).toInt(), 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb((da * 0.3 * 255).toInt(), 0, 0, 0)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                )
                c.drawRect(e0, area[1], e1, area[1] + area[3], fill)
                fill.shader = LinearGradient(
                    cx - hw * 0.75f, 0f, cx - hw * 0.15f, 0f, intArrayOf(Color.argb(0, 255, 255, 255), Color.argb((da * 0.14 * 255).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                )
                c.drawRect(e0, area[1], e1, area[1] + area[3], fill)
                fill.shader = null
                fill.color = Color.argb((da * 0.3 * 255).toInt(), 0, 0, 0)
                c.drawRect(e0 - 0.35f, area[1], e0 + 0.35f, area[1] + area[3], fill)
            }
            c.restore()
        }
    }

    fun capsule(c: Canvas, cap: FloatArray, l: Theme.Layout, look: ReededGlassRevealLook) {
        val s = l.s.toFloat()
        val r = (Theme.CAPSULE_R * l.s).toFloat()
        rect.set(cap[0], cap[1], cap[0] + cap[2], cap[1] + cap[3])
        c.save()
        c.clipRect(rect)
        fill.shader = LinearGradient(0f, cap[1], 0f, cap[1] + cap[3], Theme.capsuleTop, Theme.capsuleLow, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r, r, fill)
        // the thick ends: the pale wall's grey, fading inward
        val reach = 40f * s
        for (side in 0..1) {
            val x0 = if (side == 0) cap[0] else cap[0] + cap[2]
            val x1 = if (side == 0) cap[0] + reach else cap[0] + cap[2] - reach
            fill.shader = LinearGradient(
                x0, 0f, x1, 0f, Color.argb((0.7 * 255).toInt(), Color.red(Theme.capsuleEnd), Color.green(Theme.capsuleEnd), Color.blue(Theme.capsuleEnd)),
                Color.argb(0, Color.red(Theme.capsuleEnd), Color.green(Theme.capsuleEnd), Color.blue(Theme.capsuleEnd)), Shader.TileMode.CLAMP,
            )
            c.drawRoundRect(rect, r, r, fill)
        }
        // the white button and the card light the glass around them
        val bx = l.x(Theme.SWAP_CX).toFloat()
        val by = l.yb(Theme.SWAP_CY).toFloat()
        fill.shader = RadialGradient(bx, by, 60f * s, Color.argb((0.16 * 255).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawRect(rect, fill)
        val tx = l.x(Theme.THUMB.x + Theme.THUMB.w / 2).toFloat()
        val ty = l.yb(Theme.THUMB.y + Theme.THUMB.h / 2).toFloat()
        fill.shader = RadialGradient(tx, ty, 70f * s, Color.argb((0.14 * 255).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawRect(rect, fill)
        // a 1 dp highlight along the top edge: the front's specular continues here
        val front = look.lag.toFloat()
        fill.shader = LinearGradient(
            front - 70f * s, 0f, front + 70f * s, 0f, intArrayOf(Color.argb((0.3 * 255).toInt(), 255, 255, 255), Color.argb((0.75 * 255).toInt(), 255, 255, 255), Color.argb((0.3 * 255).toInt(), 255, 255, 255)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(cap[0] + r * 0.5f, cap[1], cap[0] + cap[2] - r * 0.5f, cap[1] + 1.1f, fill)
        fill.shader = null
        c.restore()
    }
}
