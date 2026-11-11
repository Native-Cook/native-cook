package com.ngocdevv.nativecook.blocks.media.glassvoicecapsule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** RenderNode filters blur image pixels, rather than a bitmap's alpha mask. */
@RequiresApi(31)
private class GlassVoiceCapsuleEffects {
    private val nodes = HashMap<String, RenderNode>()
    fun blur(canvas: Canvas, key: String, bounds: RectF, radius: Float, draw: (Canvas) -> Unit) {
        val left = floor(bounds.left).toInt(); val top = floor(bounds.top).toInt()
        val right = ceil(bounds.right).toInt(); val bottom = ceil(bounds.bottom).toInt()
        val node = nodes.getOrPut(key) { RenderNode("GlassVoiceCapsule-$key") }
        node.setPosition(left, top, right, bottom)
        node.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL))
        val recording = node.beginRecording(max(1, right - left), max(1, bottom - top))
        recording.translate(-left.toFloat(), -top.toFloat()); draw(recording); node.endRecording()
        canvas.drawRenderNode(node)
    }
}

internal class GlassVoiceCapsulePainter(context: Context) {
    private val bitmap = context.assets.open("glassvoicecapsule/glassvoicecapsule_spheres.jpg").use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888
        })?.also { image -> image.prepareToDraw() }
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val semibold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(regular, 600, false) else Typeface.create(regular, Typeface.BOLD)
    private val effects = if (Build.VERSION.SDK_INT >= 31) GlassVoiceCapsuleEffects() else null
    private val softwareLayers = HashMap<String, Bitmap>()
    private var fallbackBlur = 0f
    private var width = 393.0
    private var height = 852.0
    private var center = GlassVoiceCapsulePoint(196.5, 488.0)
    private var angle = 0.0
    private var frame = GlassVoiceCapsuleFrame()

    private fun color(hex: Int, alpha: Double = 1.0) = Color.argb((alpha.coerceIn(0.0, 1.0) * 255).roundToInt(), (hex shr 16) and 255, (hex shr 8) and 255, hex and 255)
    private fun paint(hex: Int, alpha: Double = 1.0, stroke: Float = 0f): Paint {
        fill.reset(); fill.isAntiAlias = true; fill.isFilterBitmap = true
        fill.color = color(hex, alpha); fill.style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL
        fill.strokeWidth = stroke; fill.strokeCap = Paint.Cap.ROUND
        if (fallbackBlur > 0) fill.maskFilter = BlurMaskFilter(fallbackBlur, BlurMaskFilter.Blur.NORMAL)
        return fill
    }
    private fun box(x: Double, y: Double, w: Double, h: Double) = RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
    private fun capsule(rect: RectF) = Path().apply { addRoundRect(rect, rect.height() / 2, rect.height() / 2, Path.Direction.CW) }
    private fun inset(rect: RectF, amount: Float) = RectF(rect).apply { inset(amount, amount) }
    private fun blur(canvas: Canvas, key: String, bounds: RectF, radius: Float, draw: (Canvas) -> Unit) {
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated) effects?.blur(canvas, key, bounds, radius, draw)
        else {
            // The permitted pre-31 image fallback is sharp, but light and shadow stay soft.
            if (key.endsWith("-image")) { draw(canvas); return }
            val left = floor(bounds.left).toInt(); val top = floor(bounds.top).toInt()
            val w = max(1, ceil(bounds.right).toInt() - left)
            val h = max(1, ceil(bounds.bottom).toInt() - top)
            val old = softwareLayers[key]
            val bitmap = if (old != null && old.width >= w && old.height >= h) old else {
                Bitmap.createBitmap(((w + 63) / 64) * 64, ((h + 63) / 64) * 64, Bitmap.Config.ARGB_8888)
                    .also { softwareLayers[key] = it }
            }
            bitmap.eraseColor(Color.TRANSPARENT)
            val layer = Canvas(bitmap)
            layer.translate(-left.toFloat(), -top.toFloat())
            val previous = fallbackBlur; fallbackBlur = radius; draw(layer); fallbackBlur = previous
            canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), imagePaint)
        }
    }
    private fun label(canvas: Canvas, label: String, x: Double, y: Double, size: Float, alpha: Double = 1.0, bold: Boolean = false, digits: Boolean = false) {
        text.color = color(0xFFFFFF, alpha); text.textSize = size
        text.typeface = if (bold) semibold else regular; text.textAlign = Paint.Align.CENTER
        text.fontFeatureSettings = if (digits) "tnum" else null
        canvas.drawText(label, x.toFloat(), (y - (text.ascent() + text.descent()) / 2).toFloat(), text)
    }
    fun draw(canvas: Canvas, w: Double, h: Double, f: GlassVoiceCapsuleFrame, offset: GlassVoiceCapsulePoint, reduced: Boolean) {
        width = w; height = h; frame = f
        center = GlassVoiceCapsulePoint(w / 2 + offset.x, h * 488 / 852 + f.floatY + offset.y)
        angle = if (reduced) 0.0 else f.angle + (offset.x / 14).coerceIn(-8.0, 8.0)
        bitmap?.let {
            val cover = max(w / it.width, h / it.height); val iw = it.width * cover; val ih = it.height * cover
            canvas.drawBitmap(it, null, box((w - iw) / 2, (h - ih) / 2, iw, ih), imagePaint)
        }
        val scrim = paint(0xFFFFFF)
        scrim.shader = LinearGradient(0f, 0f, 0f, (220 * h / 852).toFloat(), color(0x20324F, 0.28), color(0x20324F, 0.0), Shader.TileMode.CLAMP)
        canvas.drawRect(box(0.0, 0.0, w, 220 * h / 852), scrim)
        label(canvas, "Say it out loud", w / 2, 92 * h / 852, 28f, bold = true)
        label(canvas, "Tap the mic to start a note", w / 2, 132 * h / 852, 15f, 0.72 * max(0.0, 1 - f.listening - f.saved))
        label(canvas, "Listening…", w / 2, 132 * h / 852, 15f, 0.72 * f.listening)
        label(canvas, "Saved to Drafts", w / 2, 132 * h / 852, 15f, 0.72 * f.saved)
        canvas.save(); canvas.translate(center.x.toFloat(), center.y.toFloat())
        canvas.rotate(angle.toFloat()); canvas.scale(f.scale.toFloat(), f.scale.toFloat())
        glass(canvas, box(-f.width / 2, -f.height / 2, f.width, f.height), 1.30, false, 0.0)
        canvas.save(); canvas.translate((-109 * f.amount).toFloat(), 0f)
        canvas.scale(f.micPulse.toFloat(), f.micPulse.toFloat()); mic(canvas, f.micRed); canvas.restore()
        if (f.content > 0.001) {
            canvas.save(); canvas.scale(f.timerScale.toFloat(), f.timerScale.toFloat())
            val whole = f.seconds.toInt()
            val swap = if (f.digitRise && whole > 0) GlassVoiceCapsuleMath.clamp(f.digitAge / 0.18) else 1.0
            timer(canvas, whole, 4 * (1 - swap), f.content * swap)
            if (swap < 1) timer(canvas, whole - 1, -4 * swap, f.content * (1 - swap))
            canvas.restore(); canvas.save()
            val x = f.width / 2 - 50
            canvas.translate(x.toFloat(), 0f); canvas.scale(f.droplet.toFloat(), f.droplet.toFloat())
            val layer = canvas.saveLayerAlpha(-110f, -110f, 110f, 110f, (255 * f.content).roundToInt())
            glass(canvas, box(-34.0, -34.0, 68.0, 68.0), 1.28, true, x)
            canvas.drawRoundRect(-12f, -12f, 12f, 12f, 7f, 7f, paint(0xFFFFFF))
            canvas.restoreToCount(layer); canvas.restore()
        }
        canvas.restore()
    }
    private fun timer(canvas: Canvas, seconds: Int, y: Double, opacity: Double) {
        val minutes = (seconds / 60).toString().padStart(2, '0'); val remainder = (seconds % 60).toString().padStart(2, '0')
        label(canvas, "$minutes:$remainder", 4.0, y, 32f, opacity, bold = true, digits = true)
    }
    private fun sample(canvas: Canvas, zoom: Double, localX: Double, droplet: Boolean) {
        val image = bitmap ?: return
        canvas.save()
        val extra = if (droplet) frame.droplet else 1.0
        val inverse = (1 / max(0.01, frame.scale * extra)).toFloat()
        canvas.scale(inverse, inverse); canvas.rotate(-angle.toFloat())
        val radians = angle * PI / 180
        val worldX = center.x + localX * cos(radians) * frame.scale; val worldY = center.y + localX * sin(radians) * frame.scale
        val cover = max(width / 1024, height / 1536); val w = 1024 * cover; val h = 1536 * cover
        canvas.drawBitmap(image, null, box(((width - w) / 2 - worldX) * zoom, ((height - h) / 2 - worldY) * zoom, w * zoom, h * zoom), imagePaint)
        canvas.restore()
    }
    private fun glass(canvas: Canvas, rect: RectF, zoom: Double, droplet: Boolean, localX: Double) {
        val prefix = if (droplet) "stop" else "body"
        val outline = capsule(rect); val expanded = inset(rect, -80f)
        blur(canvas, "$prefix-shadow", expanded, 22f) { c ->
            c.save(); c.translate(0f, 12f); c.drawPath(outline, paint(0x0B1830, 0.30)); c.restore()
        }
        blur(canvas, "$prefix-contact", expanded, 6f) { c ->
            c.save(); c.translate(0f, 3f); c.drawPath(outline, paint(0x0B1830, 0.18)); c.restore()
        }
        canvas.save(); canvas.clipPath(outline)
        blur(canvas, "$prefix-image", inset(rect, -10f), 1.0f) { c -> sample(c, zoom, localX, droplet) }
        // Separate magnified image, masked by a feathered nine-dp lens band.
        val rimLayer = canvas.saveLayer(rect, null)
        sample(canvas, 1.42, localX, droplet)
        val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        val maskLayer = canvas.saveLayer(rect, maskPaint)
        blur(canvas, "$prefix-rim-mask", inset(rect, -6f), 1.5f) { c -> c.drawPath(capsule(inset(rect, 4.5f)), paint(0xFFFFFF, stroke = 9f)) }
        canvas.restoreToCount(maskLayer); canvas.restoreToCount(rimLayer)
        canvas.drawPath(outline, paint(0xFFFFFF, 0.07))
        val sheen = paint(0xFFFFFF)
        sheen.shader = LinearGradient(0f, rect.top, 0f, rect.top + rect.height() * 0.46f, color(0xFFFFFF, 0.26), color(0xFFFFFF, 0.0), Shader.TileMode.CLAMP)
        canvas.drawPath(outline, sheen)
        if (!droplet) {
            val r = GlassVoiceCapsuleMath.clamp(frame.amount); val colors = intArrayOf(0xFF8A3D, 0xF23FA6, 0x3D8BFF)
            repeat(3) { i ->
                val strength = if (i == 1) 1.0 else r
                val drift = 10 * sin(4 * PI * frame.time / LOOP + i * 2.1) * r
                val x = rect.left + rect.width() * (0.22 + i * 0.28) + drift; val h = 14 + 46 * frame.level
                blur(canvas, "glow-$i", expanded, 20f) { c ->
                    c.drawOval(box(x - rect.width() * 0.21, rect.bottom - 10 - h / 2, rect.width() * 0.42, h), paint(colors[i], (0.25 + 0.65 * frame.level) * strength))
                }
            }
            blur(canvas, "glow-violet", expanded, 20f) { c ->
                c.drawOval(box(-rect.width() * 0.21, rect.bottom - 42.0, rect.width() * 0.42, 40.0), paint(0x8E5BFF, 0.30 * frame.level * r))
            }
            if (frame.sheen >= 0) {
                val x = rect.left - 30 + (rect.width() + 60) * frame.sheen
                blur(canvas, "sweep", expanded, 5f) { c -> c.drawRect(box(x - 15, rect.top.toDouble(), 30.0, rect.height().toDouble()), paint(0xFFFFFF, 0.35 * sin(PI * frame.sheen))) }
            }
        }
        if (!droplet) {
            blur(canvas, "$prefix-inner-shadow", expanded, 6f) { c -> c.drawPath(capsule(RectF(rect).apply { offset(0f, -3f) }), paint(0x0B1830, 0.18, 6f)) }
        }
        val rim = paint(0xFFFFFF, stroke = 1.5f)
        rim.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(color(0xFFFFFF, 0.88), color(0xFFFFFF, 0.12), color(0xFFFFFF, 0.46)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(capsule(inset(rect, 0.75f)), rim)
        canvas.drawPath(capsule(inset(rect, 2f)), paint(0xFFFFFF, 0.22, 0.75f))
        blur(canvas, "$prefix-blip", expanded, 1f) { c ->
            c.save(); c.translate(rect.left + 26, rect.top + 13); c.rotate(-18f)
            c.drawOval(-10f, -3.5f, 10f, 3.5f, paint(0xFFFFFF, 0.75)); c.restore()
        }
        canvas.drawOval(rect.right - 36, rect.bottom - 14.5f, rect.right - 24, rect.bottom - 9.5f, paint(0xFFFFFF, 0.35))
        canvas.restore()
    }
    private fun mic(canvas: Canvas, red: Double) {
        val ink = Color.rgb(255, (255 + (77 - 255) * red).roundToInt(), (255 + (94 - 255) * red).roundToInt())
        canvas.drawRoundRect(-5f, -14f, 5f, 4f, 5f, 5f, paint(ink))
        val path = Path().apply {
            moveTo(-9f, -3f); lineTo(-9f, 0f); cubicTo(-9f, 12f, 9f, 12f, 9f, 0f); lineTo(9f, -3f)
            moveTo(0f, 9f); lineTo(0f, 14f); moveTo(-5f, 14f); lineTo(5f, 14f)
        }
        canvas.drawPath(path, paint(ink, stroke = 2f))
    }
}
