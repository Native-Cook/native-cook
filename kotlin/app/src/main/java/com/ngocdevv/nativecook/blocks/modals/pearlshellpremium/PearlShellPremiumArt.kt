package com.ngocdevv.nativecook.blocks.modals.pearlshellpremium

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class PearlShellPremiumLayout(val w: Double, val h: Double, top: Double, bottom: Double) {
    val barTop = top
    val fineTop = h-max(bottom, 16.0)-18
    val ctaTop = fineTop-8-56
    val plansTop = ctaTop-16-64
    val featTop = plansTop-24-3*22-2*8
    val titleTop = featTop-12-44
    private val room0 = top+44
    private val room = titleTop-room0
    val heroX = w/2
    val heroY = room0+room*0.55
    val heroS = minOf(w*0.33, room*0.42, 132.0)
}

@RequiresApi(33)
internal class PearlShellPremiumRuntime {
    private val shader = RuntimeShader(PearlShellPremiumShader.source)
    val paint = Paint()
    fun draw(c: Canvas, l: PearlShellPremiumLayout, f: PearlShellPremiumMotion.Frame, density: Float) {
        shader.setFloatUniform("uRes", l.w.toFloat(), l.h.toFloat())
        shader.setFloatUniform("uC", l.heroX.toFloat(), l.heroY.toFloat())
        shader.setFloatUniform("uS", l.heroS.toFloat())
        shader.setFloatUniform("uYaw", f.yaw.toFloat())
        shader.setFloatUniform("uOpen", f.open.toFloat())
        shader.setFloatUniform("uGlow", f.glow.toFloat())
        shader.setFloatUniform("uLift", f.lift.toFloat())
        shader.setFloatUniform("uSpin", f.spin.toFloat())
        shader.setFloatUniform("uPhase", f.phase.toFloat())
        shader.setFloatUniform("uPx", 1/density)
        shader.setFloatUniform("uDensity", 1f)
        paint.alpha = 255; paint.shader = shader
        c.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), paint)
    }
}

internal class PearlShellPremiumArt(context: Context) {
    private val runtime = if (Build.VERSION.SDK_INT >= 33) PearlShellPremiumRuntime() else null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Color.rgb(243, 245, 251)
    private val mute = Color.rgb(140, 151, 174)
    private val pearl = Color.rgb(233, 228, 246)
    private val page = Color.rgb(10, 17, 33)
    private val fonts = listOf("InstrumentSerif-Regular", "Inter-Regular", "Inter-Medium", "Inter-SemiBold").associateWith {
        Typeface.createFromAsset(context.assets, "pearlshellpremium/pearlshellpremium_$it.ttf")
    }
    private val textPaints = mutableMapOf<String, Paint>()
    private val widths = mutableMapOf<String, Float>()
    private val close = Path().apply { moveTo(17f, 17f); lineTo(27f, 27f); moveTo(27f, 17f); lineTo(17f, 27f) }
    private val features = listOf("Every sleep story and soundscape", "Listen offline, anywhere", "New sessions every week")
    init {
        // Fonts and glyph metrics are allocated once, outside draw calls.
        measure("Tidal Plus", "InstrumentSerif-Regular", 40f)
        measure("Restore", "Inter-Medium", 15f)
        features.forEach { measure(it, "Inter-Regular", 15f) }
        listOf("Yearly", "Monthly").forEach { measure(it, "Inter-Medium", 14f) }
        measure("Save 44%", "Inter-SemiBold", 11f, 0.3f)
        listOf("$39.99", "$5.99").forEach { measure(it, "Inter-SemiBold", 18f) }
        measure("Start Free Week", "Inter-SemiBold", 17f)
        listOf("Then $39.99 a year. Cancel anytime.", "Then $5.99 a month. Cancel anytime.").forEach { measure(it, "Inter-Regular", 13f) }
    }
    private fun measure(text: String, face: String, size: Float, spacing: Float = 0f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts[face]; textSize = size
        }
        textPaints[text] = paint
        widths[text] = paint.measureText(text)+spacing*(text.length-1)
    }
    // Android Paint has no letterSpacing; the only tracked label is drawn glyph by glyph.
    private fun text(c: Canvas, text: String, x: Double, cy: Double, color: Int = ink, alpha: Double = 1.0, centered: Boolean = false) {
        val paint = textPaints.getValue(text)
        paint.color = color; paint.alpha = (255*alpha).roundToInt().coerceIn(0, 255)
        val fm = paint.fontMetrics
        val y = cy.toFloat()-(fm.ascent+fm.descent)/2
        val start = x.toFloat()-if (centered) widths.getValue(text)/2 else 0f
        if (text == "Save 44%") {
            var cursor = start
            text.forEach { ch ->
                val s = ch.toString()
                c.drawText(s, cursor, y, paint)
                cursor += paint.measureText(s)+0.3f
            }
        } else c.drawText(text, start, y, paint)
    }
    private fun fill(color: Int, alpha: Double = 1.0) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = color
        p.alpha = (alpha*255).roundToInt().coerceIn(0, 255)
    }
    private fun rect(c: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double) {
        c.drawRoundRect(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat(), r.toFloat(), r.toFloat(), p)
    }
    fun draw(c: Canvas, l: PearlShellPremiumLayout, f: PearlShellPremiumMotion.Frame, density: Float, plan: Int, press: Double, reduced: Boolean, closePress: Double, restorePress: Double) {
        c.save(); c.scale(density, density)
        if (Build.VERSION.SDK_INT >= 33 && runtime != null) runtime.draw(c, l, f, density)
        else fallback(c, l, f)
        c.save(); c.translate(8f, l.barTop.toFloat())
        if (!reduced) c.scale((1-0.04*closePress).toFloat(), (1-0.04*closePress).toFloat(), 22f, 22f)
        fill(ink, if (reduced) 1-0.28*closePress else 1.0)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.8f; p.strokeCap = Paint.Cap.ROUND
        c.drawPath(close, p); c.restore()
        c.save()
        if (!reduced) c.scale((1-0.04*restorePress).toFloat(), (1-0.04*restorePress).toFloat(), (l.w-16-widths.getValue("Restore")/2).toFloat(), (l.barTop+22).toFloat())
        text(c, "Restore", l.w-16-widths.getValue("Restore"), l.barTop+22, mute, if (reduced) 1-0.28*restorePress else 1.0)
        c.restore()
        text(c, "Tidal Plus", l.w/2, l.titleTop+22, centered = true)
        for (i in 0..2) {
            val k = (f.glow*1.5-i*0.2).coerceIn(0.0, 1.0)
            val alpha = 0.55+0.45*k
            val tw = widths.getValue(features[i]).toDouble()
            val start = (l.w-tw-14)/2+4*(1-k)
            fill(pearl, alpha*(0.35+0.65*k))
            c.drawCircle((start+3).toFloat(), (l.featTop+i*30+11).toFloat(), 3f, p)
            text(c, features[i], start+14, l.featTop+i*30+11, alpha = alpha)
        }
        val pw = (l.w-44)/2
        for (i in 0..1) {
            val x = 16+i*(pw+12)
            fill(ink, if (i == plan) 0.14 else 0.06); rect(c, x, l.plansTop, pw, 64.0, 20.0)
            fill(ink, if (i == plan) 0.55 else 0.14); p.style = Paint.Style.STROKE; p.strokeWidth = 1f
            rect(c, x+0.5, l.plansTop+0.5, pw-1, 63.0, 19.5)
            text(c, if (i == 0) "Yearly" else "Monthly", x+17, l.plansTop+21, mute)
            if (i == 0) text(c, "Save 44%", x+pw-17-widths.getValue("Save 44%"), l.plansTop+21, pearl)
            text(c, if (i == 0) "$39.99" else "$5.99", x+17, l.plansTop+43)
        }
        c.save()
        if (!reduced) c.scale((1-0.04*press).toFloat(), (1-0.04*press).toFloat(), (l.w/2).toFloat(), (l.ctaTop+28).toFloat())
        fill(ink, if (reduced) 1-0.28*press else 1.0); rect(c, 16.0, l.ctaTop, l.w-32, 56.0, 28.0)
        text(c, "Start Free Week", l.w/2, l.ctaTop+28, page, if (reduced) 1-0.28*press else 1.0, true)
        c.restore()
        text(c, if (plan == 0) "Then $39.99 a year. Cancel anytime." else "Then $5.99 a month. Cancel anytime.", l.w/2, l.fineTop+9, mute, centered = true)
        val r = (22*(1-0.12*f.fingerPress)).toFloat()
        val fy = (l.heroY-l.heroS*(0.05+0.75*f.fingerY)).toFloat()
        fill(ink, 0.14*f.finger); c.drawCircle(l.heroX.toFloat(), fy, r, p)
        fill(ink, 0.5*f.finger); p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f
        c.drawCircle(l.heroX.toFloat(), fy, r, p)
        c.restore()
    }
    private fun fallback(c: Canvas, l: PearlShellPremiumLayout, f: PearlShellPremiumMotion.Frame) {
        // Pre-Android 13: a simple drawn pearl and hinged valve, with the same controls and motion.
        fill(page); c.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), p)
        c.save(); c.translate(l.heroX.toFloat(), l.heroY.toFloat()); c.scale(l.heroS.toFloat(), l.heroS.toFloat())
        fill(Color.rgb(152, 165, 195)); c.drawOval(-1f, -0.18f, 1f, 0.45f, p)
        fill(pearl); c.drawCircle(0f, (-0.08-f.lift).toFloat(), 0.27f, p)
        c.save(); c.rotate((-f.open*50).toFloat(), 0f, -0.45f)
        fill(Color.rgb(199, 203, 228)); c.drawOval(-1f, -0.4f, 1f, 0.08f, p)
        c.restore(); c.restore()
    }
}
