@file:Suppress("DEPRECATION")

package com.ngocdevv.nativecook.blocks.cards.elasticfoodcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The anchored ribbon and hand share one bounded world-space alpha before their shadow blurs. */
internal class ElasticFoodCardsHandShadow(private val context: Context, private val renderer: ElasticFoodCardsHandRenderer) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val composite = Paint()
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val castNode: Any? = if (Build.VERSION.SDK_INT >= 31) RenderNode("ElasticFoodCardsCast").apply {
        setPosition(0, 0, 393, 480); setClipToBounds(false)
    } else null
    private val contactNode: Any? = if (Build.VERSION.SDK_INT >= 31) RenderNode("ElasticFoodCardsContact").apply { setClipToBounds(false) } else null
    private val blackEffect: Any? = if (Build.VERSION.SDK_INT >= 31) RenderEffect.createColorFilterEffect(PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)) else null
    private var lastBlur = Float.NaN
    private var contactWidth = Float.NaN; private var contactHeight = Float.NaN; private var contactBlur = Float.NaN
    private val fallback = lazy(LazyThreadSafetyMode.NONE) { WorldBlur() }
    private val contacts = lazy(LazyThreadSafetyMode.NONE) { ContactAtlas() }
    init { if (Build.VERSION.SDK_INT < 31) { fallback.value; contacts.value } }

    private fun bitmapPaint(alpha: Double, plus: Boolean, black: Boolean = false) {
        paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true
        paint.color = if (black) Color.BLACK else Color.WHITE; paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        if (plus) { if (Build.VERSION.SDK_INT >= 29) paint.blendMode = BlendMode.PLUS else paint.xfermode = add }
    }
    fun draw(c: Canvas, f: ElasticFoodCardsFrame, m: ElasticFoodCardsHandMaterial, inletX: Double, inletY: Double) {
        c.save(); c.translate(m.castX.toFloat(), m.castY.toFloat())
        if (Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated && castNode != null) {
            val node = castNode as RenderNode
            val recording = node.beginRecording(393, 480)
            recording.clipRect(0f, 0f, 393f, 480f)
            renderer.draw(recording, f, m.weights, inletX, inletY, 480f)
            node.endRecording()
            val blur = m.castBlur.toFloat()
            if (lastBlur != blur) {
                node.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, blackEffect as RenderEffect, Shader.TileMode.DECAL)); lastBlur = blur
            }
            node.setAlpha(m.castAlpha.toFloat()); c.drawRenderNode(node)
        } else fallback.value.cast(c, f, m, inletX, inletY)
        c.restore()
        val x = (f.handBX - m.contactWidth / 2).toFloat()
        val y = (f.handBY + 5 - m.closure - m.contactHeight / 2).toFloat()
        c.save(); c.translate(x, y)
        if (m.contactAlpha > 0) {
            if (Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated && contactNode != null) {
                val node = contactNode as RenderNode
                val w = m.contactWidth.toFloat(); val h = m.contactHeight.toFloat(); val blur = m.contactBlur.toFloat()
                if (contactWidth != w || contactHeight != h || contactBlur != blur) {
                    val pad = ceil(blur * 4 + 2).toInt(); val rw = ceil(w).toInt() + pad * 2; val rh = ceil(h).toInt() + pad * 2
                    node.setPosition(-pad, -pad, rw - pad, rh - pad)
                    val recording = node.beginRecording(rw, rh)
                    bitmapPaint(1.0, false, true); recording.drawOval(RectF(pad.toFloat(), pad.toFloat(), pad + w, pad + h), paint)
                    node.endRecording(); node.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL))
                    contactWidth = w; contactHeight = h; contactBlur = blur
                }
                node.setAlpha(m.contactAlpha.toFloat()); c.drawRenderNode(node)
            } else {
                c.translate((m.contactWidth / 2).toFloat(), (m.contactHeight / 2).toFloat()); contacts.value.draw(c, m)
            }
        }
        c.restore()
    }
    fun dispose() {
        if (fallback.isInitialized()) fallback.value.dispose()
        if (contacts.isInitialized()) contacts.value.dispose()
        if (Build.VERSION.SDK_INT >= 31) { (castNode as? RenderNode)?.discardDisplayList(); (contactNode as? RenderNode)?.discardDisplayList() }
    }

    /** API 26–30: reusable half-resolution world mask, transparent 64pt pad, platform Gaussian. */
    private inner class WorldBlur {
        private val width = 261; private val height = 304
        private fun bitmap() = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
        private val pose = bitmap(); private val mask = bitmap(); private val output = bitmap()
        private val poseCanvas = Canvas(pose).apply { scale(0.5f, 0.5f); translate(64f, 64f); clipRect(0f, 0f, 393f, 480f) }
        private val maskCanvas = Canvas(mask)
        private val outputRect = RectF(-64f, -64f, -64f + width * 2f, -64f + height * 2f)
        private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var rs: RenderScript? = null; private var script: ScriptIntrinsicBlur? = null
        private var input: Allocation? = null; private var result: Allocation? = null
        private val pixels = IntArray(width * height); private val a = IntArray(width * height); private val b = IntArray(width * height)
        init {
            if (Build.VERSION.SDK_INT <= 30) try {
                val engine = RenderScript.create(context)
                rs = engine; script = ScriptIntrinsicBlur.create(engine, Element.U8_4(engine))
                input = Allocation.createFromBitmap(engine, mask, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT)
                result = Allocation.createTyped(engine, input!!.type); script!!.setInput(input)
            } catch (_: RuntimeException) { releaseScript() }
        }
        fun cast(c: Canvas, f: ElasticFoodCardsFrame, m: ElasticFoodCardsHandMaterial, inletX: Double, inletY: Double) {
            mask.eraseColor(Color.TRANSPARENT)
            for (i in 0..2) if (m.weights[i] > 0) {
                pose.eraseColor(Color.TRANSPARENT)
                renderer.drawPose(poseCanvas, i, f, inletX, inletY)
                p.reset(); p.isFilterBitmap = true; p.alpha = (m.weights[i] * 255).roundToInt()
                if (Build.VERSION.SDK_INT >= 29) p.blendMode = BlendMode.PLUS else p.xfermode = add
                maskCanvas.drawBitmap(pose, 0f, 0f, p)
            }
            maskCanvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC_IN)
            val sigma = m.castBlur * 0.5
            val intrinsic = script
            if (intrinsic != null) try {
                input!!.copyFrom(mask)
                // AOSP intrinsic: sigma = 0.4 * radius + 0.6, maximum radius 25.
                intrinsic.setRadius(((sigma - 0.6) / 0.4).toFloat().coerceIn(0.001f, 25f))
                intrinsic.forEach(result); result!!.copyTo(output)
            } catch (_: RuntimeException) { releaseScript(); boxBlur(sigma) }
            else boxBlur(sigma)
            bitmapPaint(m.castAlpha, false, true); c.drawBitmap(output, null, outputRect, paint)
        }
        /** Three linear-time box passes approximate Gaussian if the platform intrinsic is unavailable. */
        private fun boxBlur(sigma: Double) {
            mask.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) a[i] = pixels[i] ushr 24
            var lower = floor(sqrt(4 * sigma * sigma + 1)).toInt(); if (lower % 2 == 0) lower--
            val upper = lower + 2
            val count = ((12*sigma*sigma - 3*lower*lower - 12*lower - 9) / (-4.0*lower-4)).roundToInt().coerceIn(0, 3)
            for (pass in 0..2) {
                val radius = ((if (pass < count) lower else upper) - 1) / 2; val divisor = 2*radius+1
                for (y in 0 until height) {
                    val row = y*width; var sum = 0
                    for (x in 0..radius) sum += a[row+x]
                    for (x in 0 until width) {
                        b[row+x] = (sum + divisor/2)/divisor
                        if (x-radius >= 0) sum -= a[row+x-radius]
                        if (x+radius+1 < width) sum += a[row+x+radius+1]
                    }
                }
                for (x in 0 until width) {
                    var sum = 0; for (y in 0..radius) sum += b[y*width+x]
                    for (y in 0 until height) {
                        a[y*width+x] = (sum + divisor/2)/divisor
                        if (y-radius >= 0) sum -= b[(y-radius)*width+x]
                        if (y+radius+1 < height) sum += b[(y+radius+1)*width+x]
                    }
                }
            }
            for (i in pixels.indices) pixels[i] = a[i] shl 24
            output.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        private fun releaseScript() { result?.destroy(); input?.destroy(); script?.destroy(); rs?.destroy(); result = null; input = null; script = null; rs = null }
        fun dispose() { releaseScript(); pose.recycle(); mask.recycle(); output.recycle() }
    }
    private data class Mask(val bitmap: Bitmap, val bounds: RectF)
    private inner class ContactAtlas {
        private val masks = Array(11) { gi -> Array(11) { li ->
            val g = gi / 10.0; val lift = li / 10.0
            val w = (54 - 14*g)*(1-0.25*lift); val h = (18-6*g)*(1-0.35*lift)
            val source = Bitmap.createBitmap(ceil(w*2).toInt()+4, ceil(h*2).toInt()+4, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(source); canvas.scale(2f, 2f)
            canvas.drawOval(RectF(1f, 1f, (1+w).toFloat(), (1+h).toFloat()), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
            val sigma = 5-2*g+1.5*lift
            val p = Paint().apply { maskFilter = BlurMaskFilter(((sigma*2-0.5)*sqrt(3.0)).toFloat(), BlurMaskFilter.Blur.NORMAL) }
            val offset = IntArray(2); val alpha = source.extractAlpha(p, offset).apply { density = Bitmap.DENSITY_NONE; prepareToDraw() }
            source.recycle()
            val x = (-w/2-1 + offset[0]/2f).toFloat(); val y = (-h/2-1 + offset[1]/2f).toFloat()
            Mask(alpha, RectF(x, y, x+alpha.width/2f, y+alpha.height/2f))
        } }
        private fun drawMask(c: Canvas, mask: Mask, alpha: Double) { if (alpha > 0) { bitmapPaint(alpha, true, true); c.drawBitmap(mask.bitmap, null, mask.bounds, paint) } }
        fun draw(c: Canvas, m: ElasticFoodCardsHandMaterial) {
            val g = m.closure*10; val l = m.lift*10
            val gi = floor(g).toInt().coerceIn(0, 9); val li = floor(l).toInt().coerceIn(0, 9); val gx = g-gi; val lx = l-li
            composite.reset(); composite.alpha = (m.contactAlpha*255).roundToInt()
            val layer = c.saveLayer(-60f, -50f, 60f, 50f, composite)
            drawMask(c, masks[gi][li], (1-gx)*(1-lx)); drawMask(c, masks[gi+1][li], gx*(1-lx))
            drawMask(c, masks[gi][li+1], (1-gx)*lx); drawMask(c, masks[gi+1][li+1], gx*lx)
            c.restoreToCount(layer)
        }
        fun dispose() { masks.forEach { row -> row.forEach { it.bitmap.recycle() } } }
    }
}
