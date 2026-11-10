package com.ngocdevv.nativecook.blocks.sheets.messagebottleshare

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.sheets.messagebottleshare.MessageBottleShareMotion as M
import kotlin.math.*

@RequiresApi(33)
internal class MessageBottleShareRuntime {
    private val shader = RuntimeShader(MessageBottleShareShader.SOURCE)
    fun shader(l: M.Layout, f: M.Frame, t: Double): Shader {
        val b = f.bottle
        shader.setFloatUniform("uRes", l.w.toFloat(), l.h.toFloat())
        shader.setFloatUniform("uHorizon", l.horizon.toFloat())
        shader.setFloatUniform("uPos", b.x.toFloat(), b.y.toFloat())
        shader.setFloatUniform("uAngle", b.angle.toFloat())
        shader.setFloatUniform("uScale", b.scale.toFloat())
        shader.setFloatUniform("uFloat", b.floating.toFloat())
        shader.setFloatUniform("uAlpha", b.alpha.toFloat())
        shader.setFloatUniform("uSplash", l.landX.toFloat(), l.landY.toFloat(), f.splash.toFloat())
        shader.setFloatUniform("uT", t.toFloat())
        shader.setFloatUniform("uLoop", 10f)
        shader.setFloatUniform("uPx", 1f)
        return shader
    }
}

internal class MessageBottleShareArt(context: Context) {
    private val regular = Typeface.createFromAsset(context.assets, "messagebottleshare/messagebottleshare_Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(context.assets, "messagebottleshare/messagebottleshare_Inter-Medium.ttf")
    private val semibold = Typeface.createFromAsset(context.assets, "messagebottleshare/messagebottleshare_Inter-SemiBold.ttf")
    private val serif = Typeface.createFromAsset(context.assets, "messagebottleshare/messagebottleshare_InstrumentSerif-Regular.ttf")
    private val runtime = if (Build.VERSION.SDK_INT >= 33) MessageBottleShareRuntime() else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val arrow = Path().apply { moveTo(3f, 15f); lineTo(15f, 3f); moveTo(7f, 3f); lineTo(15f, 3f); lineTo(15f, 11f) }
    private val plus = Path().apply { moveTo(9f, 3f); lineTo(9f, 15f); moveTo(3f, 9f); lineTo(15f, 9f) }
    private var sky: Shader? = null
    private var sea: Shader? = null
    private var cachedHeight = 0.0
    private val body = RectF(-0.8f, -0.32f, 0.3f, 0.32f)
    private val neck = RectF(0.2f, -0.11f, 1.0f, 0.11f)
    private val cork = RectF(0.98f, -0.1f, 1.18f, 0.1f)
    private val roll = RectF(-0.79f, -0.17f, 0.39f, 0.17f)

    private fun fill(color: Int, alpha: Double = 1.0): Paint {
        paint.reset(); paint.isAntiAlias = true
        paint.color = color; paint.alpha = (255*alpha.coerceIn(0.0, 1.0)).roundToInt()
        return paint
    }
    private fun text(c: Canvas, value: String, x: Double, y: Double, size: Float, face: Typeface,
                     color: Int, alpha: Double = 1.0, lineHeight: Float = size, centered: Boolean = false) {
        textPaint.typeface = face; textPaint.textSize = size; textPaint.color = color
        textPaint.alpha = (255*alpha.coerceIn(0.0, 1.0)).roundToInt()
        val fm = textPaint.fontMetrics
        val baseline = y.toFloat() + (lineHeight - (fm.descent-fm.ascent))/2 - fm.ascent
        c.drawText(value, x.toFloat()-(if (centered) textPaint.measureText(value)/2 else 0f), baseline, textPaint)
    }
    private fun fallback(c: Canvas, l: M.Layout, f: M.Frame) {
        if (cachedHeight != l.h) {
            cachedHeight = l.h
            sky = LinearGradient(0f, 0f, 0f, l.horizon.toFloat(), intArrayOf(0xffc7c2e6.toInt(), 0xfffcd6bd.toInt()), null, Shader.TileMode.CLAMP)
            sea = LinearGradient(0f, l.horizon.toFloat(), 0f, l.h.toFloat(), intArrayOf(0xfff2ccc7.toInt(), 0xff9e9ec7.toInt(), 0xff3d6685.toInt()), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, l.w.toFloat(), l.horizon.toFloat(), fill(Color.WHITE).apply { shader = sky })
        c.drawRect(0f, l.horizon.toFloat(), l.w.toFloat(), l.h.toFloat(), fill(Color.WHITE).apply { shader = sea })
        c.drawCircle((l.w*0.64).toFloat(), (l.horizon-34).toFloat(), 15f, fill(0xffffead1.toInt(), 0.5))
        val b = f.bottle
        if (b.alpha > 0) {
            c.save(); c.translate(b.x.toFloat(), b.y.toFloat()); c.rotate((b.angle*180/PI).toFloat()); c.scale(b.scale.toFloat(), b.scale.toFloat())
            c.drawRoundRect(body, 0.14f, 0.14f, fill(0xff77b3a3.toInt(), b.alpha*0.8))
            c.drawRoundRect(neck, 0.1f, 0.1f, fill(0xff77b3a3.toInt(), b.alpha*0.8))
            c.drawRoundRect(roll, 0.17f, 0.17f, fill(0xfff7edd4.toInt(), b.alpha))
            c.drawLine(-0.18f, -0.17f, -0.18f, 0.17f, fill(0xffb82e24.toInt(), b.alpha).apply { strokeWidth = 0.03f })
            c.drawRoundRect(cork, 0.03f, 0.03f, fill(0xffa67a50.toInt(), b.alpha))
            c.drawRoundRect(body, 0.14f, 0.14f, fill(Color.WHITE, b.alpha*0.5).apply { style = Paint.Style.STROKE; strokeWidth = 0.012f })
            c.restore()
        }
        if (f.splash in 0.0..3.0) for (i in 0..2) {
            val age = f.splash-i*0.25
            if (age > 0) {
                val r = (8+30*age).toFloat()
                c.save(); c.translate(l.landX.toFloat(), l.landY.toFloat()); c.scale(1f, 0.22f)
                c.drawCircle(0f, 0f, r, fill(Color.WHITE, 0.45*(1-age/3).pow(2)).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f+age.toFloat() })
                c.restore()
            }
        }
    }
    fun draw(c: Canvas, l: M.Layout, f: M.Frame, t: Double, sentChanged: Double, now: Double,
             pressed: Int, pressAmount: Double, reduced: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && runtime != null) {
            c.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), fill(Color.WHITE).apply { shader = runtime.shader(l, f, t) })
        } else fallback(c, l, f)
        val ink = 0xff2a2238.toInt(); val cream = 0xfffbf4ee.toInt()
        c.save()
        if (pressed == 1) {
            val scale = if (reduced) 1f else (1-0.04*pressAmount).toFloat()
            c.scale(scale, scale, 43f, (l.top+22).toFloat())
        }
        text(c, "Cancel", 16.0, l.top+11.5, 17f, regular, ink,
            if (pressed == 1 && reduced) 1-0.28*pressAmount else 1.0, 21f)
        c.restore()
        val incoming = M.cssEase(M.seg(now-sentChanged, 0.12, 0.44))
        val outgoing = 1-M.cssEase(M.seg(now-sentChanged, 0.0, 0.32))
        val a = if (f.sent) outgoing else incoming
        val b = if (f.sent) incoming else outgoing
        text(c, "Cast your letter", 24.0, l.titleTop+6*(1-a), 42f, serif, ink, a, 48f)
        text(c, "To Maya · 214 words · written at the lake", 24.0, l.titleTop+50+6*(1-a), 14f, regular, ink, a*0.62, 20f)
        text(c, "On its way", 24.0, l.titleTop+6*(1-b), 42f, serif, ink, b, 48f)
        text(c, "Maya will find it at dawn.", 24.0, l.titleTop+50+6*(1-b), 14f, regular, ink, b*0.62, 20f)
        c.save()
        if (pressed == 2 && !reduced) c.scale((1-0.04*pressAmount).toFloat(), (1-0.04*pressAmount).toFloat(), (l.w/2).toFloat(), (l.ctaTop+28).toFloat())
        val buttonAlpha = if (pressed == 2 && reduced) 1-0.28*pressAmount else 1.0
        c.drawRoundRect(24f, l.ctaTop.toFloat(), (l.w-24).toFloat(), (l.ctaTop+56).toFloat(), 28f, 28f, fill(ink, buttonAlpha))
        val title = if (f.sent) "Write Another" else "Cast Off"
        textPaint.typeface = semibold; textPaint.textSize = 16f
        val total = 18+8+textPaint.measureText(title)
        val left = l.w/2-total/2
        c.save(); c.translate(left.toFloat(), (l.ctaTop+19).toFloat())
        c.drawPath(if (f.sent) plus else arrow, fill(cream, buttonAlpha).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
        c.restore()
        text(c, title, left+26, l.ctaTop+18, 16f, semibold, cream, buttonAlpha, 20f)
        c.restore()
        text(c, "or throw the bottle", l.w/2, l.hintTop+6*(1-a), 13f, regular, Color.WHITE, 0.82*a, 20f, true)
        val r = (22*(1-0.12*f.fingerPress)).toFloat()
        c.drawCircle(f.fingerX.toFloat(), f.fingerY.toFloat(), r, fill(ink, 0.12*f.finger))
        c.drawCircle(f.fingerX.toFloat(), f.fingerY.toFloat(), r, fill(Color.WHITE, 0.6*f.finger).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f })
    }
}
