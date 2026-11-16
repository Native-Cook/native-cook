package com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash.TokenFusionSplashTheme as Theme
import kotlin.math.max
import kotlin.math.min

/**
 * The splash's drawing: the live signed-distance shader over the whole screen (API 33+), or below it a path-and-gradient version of
 * the same picture (the page's rings, the lit violet body, flat coins with their glyphs, a neck between a coin and the body that
 * thins as it stretches, the flood as one growing disc); then the wordmark in Outfit, which fades in and rises into place.
 * Everything is in dp: the caller scales the canvas by the density.
 */
internal class TokenFusionSplashRenderer(assets: AssetManager) {
    private val atlas: Bitmap = TokenFusionSplashAtlas.make(listOf(Theme.MARK) + Theme.TOKENS.map { it.glyph })
    private val outfit = Typeface.createFromAsset(assets, "tokenfusionsplash/tokenfusionsplash_Outfit-Regular.ttf")
    private val pass: TokenFusionSplashShaderPass? = if (Build.VERSION.SDK_INT >= 33) {
        try {
            TokenFusionSplashShaderPass(atlas)
        } catch (error: IllegalArgumentException) {
            Log.w("TokenFusionSplash", "Runtime shader unavailable; using the Canvas fallback", error)
            null
        }
    } else null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = outfit
        color = Theme.inkInt
        letterSpacing = Theme.WORDMARK_TRACKING.toFloat()
    }
    private val glyphPaths = (listOf(Theme.MARK) + Theme.TOKENS.map { it.glyph }).map { data ->
        try {
            PathParser.createPathFromPathData(data)
        } catch (error: RuntimeException) {
            Path()
        }
    }
    private val glyphBounds = glyphPaths.map { p -> RectF().also { p.computeBounds(it, true) } }
    private val work = Path()

    private fun col(c: FloatArray, a: Float = 1f) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), (c[0] * 255).toInt(), (c[1] * 255).toInt(), (c[2] * 255).toInt())

    fun draw(c: Canvas, w: Float, h: Float, scene: TokenFusionScene, lag: TokenFusionScene, layout: Theme.Layout, opacity: Float) {
        val outer = if (opacity < 0.999f) c.saveLayerAlpha(0f, 0f, w, h, (255 * opacity.coerceIn(0f, 1f)).toInt()) else -1
        val p = pass
        if (p != null && Build.VERSION.SDK_INT >= 33) p.draw(c, w, h, scene, lag, layout) else drawFallback(c, w, h, scene, layout)
        if (outer >= 0) c.restoreToCount(outer)
        // the wordmark: it rises into place as it fades in
        if (scene.word > 0.004) {
            val s = layout.scale
            text.textSize = (Theme.WORDMARK_SIZE * s).toFloat()
            text.color = Theme.inkInt
            text.alpha = (255 * scene.word.coerceIn(0.0, 1.0)).toInt()
            val top = layout.offsetY + scene.wordY * s
            val baseline = (top - text.fontMetrics.ascent).toFloat()
            c.drawText(Theme.WORDMARK, (w - text.measureText(Theme.WORDMARK)) / 2, baseline, text)
        }
    }

    private fun drawFallback(c: Canvas, w: Float, h: Float, scene: TokenFusionScene, layout: Theme.Layout) {
        val s = layout.scale.toFloat()
        val ox = layout.offsetX.toFloat()
        val oy = layout.offsetY.toFloat()
        fill.shader = null
        fill.color = Theme.pageInt
        c.drawRect(0f, 0f, w, h, fill)
        // the page's rings, drawn as hairlines about the centre, scaled with the story
        stroke.strokeWidth = 1.2f
        stroke.color = col(Theme.ring, 0.9f)
        val rs = scene.ringScale.toFloat()
        for (r in floatArrayOf(162f, 240f, 320f, 406f)) c.drawCircle(ox + Theme.CORE_X.toFloat() * s, oy + Theme.CORE_Y.toFloat() * s, r * rs * s, stroke)
        val cx = (ox + scene.x * s).toFloat()
        val cy = (oy + scene.y * s).toFloat()
        val r = (scene.r * s).toFloat()
        // the soft light round the fused body
        fill.shader = RadialGradient(cx, cy, r * 1.9f + 1f, col(Theme.core, 0.16f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.9f + 1f, fill)
        // the necks: a coin that is close enough to the body is joined to it by a bridge that thins toward its middle
        for (co in scene.coins) {
            if (co.reach <= 0.001 || co.r < 0.5) continue
            val bx = (ox + co.x * s).toFloat()
            val by = (oy + co.y * s).toFloat()
            stroke.strokeWidth = ((8 + 16 * (1 - co.reach)) * s * co.reach.toFloat()).toFloat().coerceAtLeast(0.5f)
            stroke.color = col(Theme.core)
            c.drawLine(cx, cy, bx, by, stroke)
        }
        // the body: lit from the upper left, deeper violet to the lower right
        val lit = RadialGradient(
            cx - r * 0.35f, cy - r * 0.4f, r * 1.5f + 1f, intArrayOf(col(Theme.hot), col(Theme.core), col(Theme.shade)),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP,
        )
        fill.shader = lit
        c.drawCircle(cx, cy, r, fill)
        fill.shader = null
        // the mark, cut out of the body in the page's colour as the story flows
        if (scene.mark > 0.004 && scene.r < 400) drawGlyph(c, 0, cx, cy, (scene.markSize * 1.3333 * s / 2).toFloat(), Theme.inkInt, scene.mark.toFloat())
        // the coins: flat discs with their glyph in ink
        for ((i, co) in scene.coins.withIndex()) {
            if (co.r < 0.5) continue
            val bx = (ox + co.x * s).toFloat()
            val by = (oy + co.y * s).toFloat()
            val cr = (co.r * s).toFloat()
            fill.color = col(Theme.tokenColors[i])
            c.drawCircle(bx, by, cr, fill)
            if (co.r > 10) {
                c.save()
                c.translate(bx, by)
                c.rotate(Math.toDegrees(co.lean).toFloat())
                drawGlyph(c, i + 1, 0f, 0f, cr * 0.72f, Theme.inkInt, min(1.0, (co.r - 10) / 8).toFloat())
                c.restore()
            }
        }
    }

    /** A glyph of the atlas (a 0..100 vector) fitted to a box of half-size `half`, centred on (x, y). */
    private fun drawGlyph(c: Canvas, slot: Int, x: Float, y: Float, half: Float, color: Int, alpha: Float) {
        val b = glyphBounds[slot]
        val side = max(b.width(), b.height()).coerceAtLeast(1f)
        c.save()
        c.translate(x, y)
        c.scale(half * 2 / side, half * 2 / side)
        c.translate(-b.centerX(), -b.centerY())
        work.set(glyphPaths[slot])
        fill.color = color
        fill.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        c.drawPath(work, fill)
        fill.alpha = 255
        c.restore()
    }
}
