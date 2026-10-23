package com.ngocdevv.nativecook.blocks.forms.glowtogglechecklist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import kotlin.math.ceil

/** Two shader surfaces and cached, ink-centred Inter glyphs. No per-frame font rasterization. */
internal class GlowChecklistRenderer(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val typeface = GlowChecklistFont.load(context)
    private val title = TextRun("SUCCESS", 40f, 4.5f, true)
    private val labels = LABELS.map { TextRun(it, 21f, 2.8f, false) }
    private val gpu = if (Build.VERSION.SDK_INT >= 33) Surfaces() else null
    private val clip = Path()
    private val curtain = Paint().apply {
        shader = LinearGradient(0f, 30f, 0f, 40f, Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun draw(canvas: Canvas, width: Float, height: Float, density: Float, s: GlowChecklistState) {
        val k = minOf(1f, width / 402f, height / 700f)
        canvas.save()
        canvas.scale(density, density)
        val opacityLayer = if (s.opacity < 1) canvas.saveLayerAlpha(0f, 0f, width, height, (s.opacity * 255).toInt()) else -1
        if (gpu != null) gpu.draw(canvas, width, height, density, k, s) else fallback(canvas, width, height, k, s)
        if (s.present > 0.02) {
            canvas.save()
            canvas.translate(width / 2, height / 2 - 158 * k * s.open.toFloat())
            val scale = k * (1 - 0.05f * s.press.toFloat())
            canvas.scale(scale, scale)
            val p = ((s.present - 0.02) / 0.18).coerceIn(0.0, 1.0)
            val visibility = (p * p * (3 - 2 * p)).toFloat()
            canvas.save()
            canvas.clipRect(-144f, 30f, 144f, 406f)
            val layer = canvas.saveLayer(-144f, 30f, 144f, 406f, null)
            for (i in 0..3) {
                val y = 30f + 316f * s.open.toFloat() - (376f - ROWS[i])
                labels[i].draw(canvas, -118f, y, visibility, null)
            }
            canvas.drawRect(-144f, 30f, 144f, 406f, curtain)
            canvas.restoreToCount(layer)
            canvas.restore()
            clip.reset()
            val half = 144f * s.present.toFloat().coerceIn(0f, 1f)
            clip.addRoundRect(RectF(-half, -30f, half, 30f), minOf(22f, half), minOf(22f, half), Path.Direction.CW)
            canvas.clipPath(clip)
            title.draw(canvas, 0f, 0f, visibility, s.letters)
            canvas.restore()
        }
        if (opacityLayer >= 0) canvas.restoreToCount(opacityLayer)
        canvas.restore()
    }

    private inner class TextRun(text: String, size: Float, tracking: Float, centred: Boolean) {
        private val paths = Array(text.length) { Path() }
        private val x = FloatArray(text.length)
        private val centres = FloatArray(text.length)
        private val bitmaps: Array<Bitmap>
        private val baseline: Float
        private val originY: Float
        private val origins: FloatArray

        init {
            val font = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = this@GlowChecklistRenderer.typeface
                textSize = size
                isLinearText = true
                hinting = Paint.HINTING_OFF
            }
            var cursor = 0f
            var left = Float.POSITIVE_INFINITY
            var right = Float.NEGATIVE_INFINITY
            var top = Float.POSITIVE_INFINITY
            var bottom = Float.NEGATIVE_INFINITY
            val bounds = Array(text.length) { RectF() }
            for (i in text.indices) {
                font.getTextPath(text, i, i + 1, 0f, 0f, paths[i])
                paths[i].computeBounds(bounds[i], true)
                val b = bounds[i]
                left = minOf(left, cursor + b.left)
                right = maxOf(right, cursor + b.right)
                top = minOf(top, b.top)
                bottom = maxOf(bottom, b.bottom)
                x[i] = cursor
                centres[i] = cursor + b.centerX()
                cursor += font.measureText(text, i, i + 1) + tracking
            }
            val shift = if (centred) -(left + right) / 2 else -left
            baseline = -(top + bottom) / 2
            originY = top + baseline - 18f
            origins = FloatArray(text.length)
            bitmaps = Array(text.length) { i ->
                x[i] += shift
                centres[i] += shift
                origins[i] = bounds[i].left - 18f
                val b = Bitmap.createBitmap(ceil((bounds[i].width() + 36) * 3).toInt(), ceil((bottom - top + 36) * 3).toInt(), Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                c.scale(3f, 3f)
                c.translate(-origins[i], -originY + baseline)
                val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 243, 239) }
                ink.alpha = 77
                ink.maskFilter = BlurMaskFilter((5f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
                c.drawPath(paths[i], ink)
                ink.alpha = 89
                ink.maskFilter = BlurMaskFilter((1.5f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
                c.drawPath(paths[i], ink)
                ink.alpha = 255
                ink.maskFilter = null
                c.drawPath(paths[i], ink)
                b
            }
        }

        fun draw(canvas: Canvas, left: Float, cy: Float, visibility: Float, letters: DoubleArray?) {
            for (i in bitmaps.indices) {
                val progress = letters?.get(i)?.toFloat()?.coerceIn(0f, 1f) ?: 1f
                val scale = if (letters != null) 0.25f + 0.75f * progress else 1f
                paint.shader = null
                paint.alpha = (255 * visibility * minOf(1f, progress / 0.4f)).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(left, cy)
                canvas.scale(scale, scale, centres[i], 0f)
                val b = bitmaps[i]
                canvas.drawBitmap(b, null, RectF(x[i] + origins[i], originY, x[i] + origins[i] + b.width / 3f, originY + b.height / 3f), paint)
                canvas.restore()
            }
            paint.alpha = 255
        }
    }

    @android.annotation.TargetApi(33)
    private class Surfaces {
        private val halo = RuntimeShader(GlowChecklistShaders.HALO)
        private val glass = RuntimeShader(GlowChecklistShaders.GLASS)
        private val node = RenderNode("glow-checklist-halo")
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val leads = FloatArray(4)
        private val trails = FloatArray(4)
        private val holds = FloatArray(4)
        private val rows = FloatArray(4)

        private fun common(shader: RuntimeShader, width: Float, height: Float, k: Float, s: GlowChecklistState) {
            val open = s.open.toFloat().coerceIn(0f, 1f)
            val present = s.present.toFloat().coerceIn(0f, 1f)
            shader.setFloatUniform("uCentre", width / 2, height / 2 - 158 * k * open)
            shader.setFloatUniform("uScale", k * (1 - 0.05f * s.press.toFloat().coerceIn(0f, 1f)))
            shader.setFloatUniform("uCap", 144 * present, 30f, minOf(22f, 144 * present))
            shader.setFloatUniform("uSlab", 142f, 30f + 316f * open, 12f)
            shader.setFloatUniform("uPresent", present)
            shader.setFloatUniform("uOpen", open)
            shader.setFloatUniform("uCalm", s.tip.toFloat().coerceIn(0f, 1f))
            shader.setFloatUniform("uColour", GlowChecklistColour.at(s.hue, s.tip))
            shader.setFloatUniform("uTime", if (s.still) 0f else s.clock.toFloat())
            shader.setFloatUniform("uStill", if (s.still) 1f else 0f)
        }

        fun draw(canvas: Canvas, width: Float, height: Float, density: Float, k: Float, s: GlowChecklistState) {
            common(halo, width, height, k, s)
            common(glass, width, height, k, s)
            val tip = s.tip.toFloat().coerceIn(0f, 1f)
            val baseReach = 60f + 24f * s.open.toFloat().coerceIn(0f, 1f) + 12f * s.fill.toFloat().coerceIn(0f, 3f)
            halo.setFloatUniform("uScreen", width, height)
            halo.setFloatUniform("uRaster", 3f)
            halo.setFloatUniform("uReach", baseReach + (160 - baseReach) * tip + maxOf(0f, s.tip.toFloat() - 1) * 160)
            halo.setFloatUniform("uFill", s.fill.toFloat())
            halo.setFloatUniform("uSmokeColour", GlowChecklistColour.at(s.hue, 0.0))
            halo.setFloatUniform("uSmokeReach", baseReach)
            halo.setFloatUniform("uSurge", if (s.still) 0f else s.surge.toFloat())
            val w = ceil(width * density / 3).toInt()
            val h = ceil(height * density / 3).toInt()
            node.setPosition(0, 0, w, h)
            val low = node.beginRecording(w, h)
            low.scale(density, density)
            paint.shader = halo
            low.drawRect(0f, 0f, width / 3, height / 3, paint)
            node.endRecording()
            node.setUseCompositingLayer(true, null)
            canvas.save()
            canvas.scale(3 / density, 3 / density)
            canvas.drawRenderNode(node)
            canvas.restore()

            glass.setFloatUniform("uRaster", 1f)
            glass.setFloatUniform("uRemainder", GlowChecklistColour.at(s.hue, 0.0))
            glass.setFloatUniform("uFill", s.fill.toFloat())
            glass.setFloatUniform("uTip", tip)
            glass.setFloatUniform("uFlash", s.flash.toFloat().coerceIn(0f, 1f))
            glass.setFloatUniform("uGlare", if (s.still) -1f else s.glare.toFloat())
            for (i in 0..3) {
                rows[i] = 30 + 316 * s.open.toFloat().coerceIn(0f, 1f) - (376 - ROWS[i])
                leads[i] = s.knobs[i].lead.toFloat()
                trails[i] = s.knobs[i].trail.toFloat()
                holds[i] = s.knobs[i].hold.toFloat()
            }
            glass.setFloatUniform("uRows", rows)
            glass.setFloatUniform("uLead", leads)
            glass.setFloatUniform("uTrail", trails)
            glass.setFloatUniform("uHold", holds)
            glass.setFloatUniform("uSwitch", 74f, 44f, 24f, 18f)
            glass.setFloatUniform("uTravel", 3f, 20f)
            paint.shader = glass
            canvas.drawRect(0f, 0f, width, height, paint)
            paint.shader = null
        }
    }

    // API 26–32: a still coloured aura, rim-lit glass, progress cells and live ceramic switches.
    private fun fallback(canvas: Canvas, width: Float, height: Float, k: Float, s: GlowChecklistState) {
        if (s.present < 0.02) return
        val rgb = GlowChecklistColour.at(s.hue, s.tip)
        val hue = Color.rgb((rgb[0] * 255).toInt(), (rgb[1] * 255).toInt(), (rgb[2] * 255).toInt())
        canvas.save()
        canvas.translate(width / 2, height / 2 - 158 * k * s.open.toFloat())
        canvas.scale(k, k)
        val bottom = 30 + 316 * s.open.toFloat()
        val half = 144 * s.present.toFloat()
        paint.shader = RadialGradient(0f, (bottom - 30) / 2, 300f, intArrayOf(hue, Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(-340f, -200f, 340f, bottom + 200, paint)
        paint.shader = null
        if (s.open > 0.001) {
            paint.color = Color.rgb(32, 34, 33)
            canvas.drawRoundRect(-142f, 0f, 142f, bottom, 12f, 12f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = hue
            canvas.drawRoundRect(-142f, 0f, 142f, bottom, 12f, 12f, paint)
            paint.style = Paint.Style.FILL
            canvas.save()
            canvas.clipRect(-142f, 30f, 142f, bottom)
            for (i in 0..3) {
                val y = bottom - (376 - ROWS[i])
                val on = ((s.knobs[i].lead + s.knobs[i].trail) / 2).toFloat().coerceIn(0f, 1f)
                paint.color = if (on > 0.5f) Color.rgb(34, 176, 74) else Color.rgb(106, 106, 110)
                canvas.drawRoundRect(74f, y - 12, 118f, y + 12, 12f, 12f, paint)
                paint.color = Color.rgb(244, 243, 239)
                canvas.drawCircle(86 + 20 * on, y, 9f, paint)
            }
            canvas.restore()
        }
        clip.reset()
        clip.addRoundRect(RectF(-half, -30f, half, 30f), minOf(22f, half), minOf(22f, half), Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        paint.color = Color.rgb((rgb[0] * 60).toInt(), (rgb[1] * 60).toInt(), (rgb[2] * 60).toInt())
        canvas.drawPaint(paint)
        paint.color = Color.rgb(42, 142, 65)
        canvas.drawRect(-144f, -30f, -144 + 288 * (s.fill / 4).toFloat().coerceIn(0f, 1f), 30f, paint)
        paint.color = Color.argb(80, 0, 0, 0)
        for (i in 1..7) if (i / 8f < s.fill / 4) canvas.drawRect(-144 + i * 36f, -30f, -143 + i * 36f, 30f, paint)
        canvas.restore()
        paint.color = hue
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawPath(clip, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    companion object {
        val LABELS = listOf("DISCIPLINE", "DEDICATION", "HARDWORK", "CONSISTENCY")
        val ROWS = floatArrayOf(98f, 166f, 234f, 302f)
    }
}
