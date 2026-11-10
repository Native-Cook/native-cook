package com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose

import android.graphics.*
import kotlin.math.*

/** Software-safe counterpart of vcd-shader.ts: the same three softly shaded flat forms. */
internal class VitaminCapsuleDoseFallback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drop = Path().apply {
        moveTo(0f, 0.75f); cubicTo(-0.08f, 0.54f, -0.6f, 0.16f, -0.6f, -0.2f)
        cubicTo(-0.6f, -1f, 0.6f, -1f, 0.6f, -0.2f); cubicTo(0.6f, 0.16f, 0.08f, 0.54f, 0f, 0.75f); close()
    }
    private val capsule = Path().apply { addRoundRect(-1f, -.44f, 1f, .44f, .44f, .44f, Path.Direction.CW) }
    private val mint = LinearGradient(-0.6f,-0.5f,0.8f,0.6f,
        intArrayOf(Color.rgb(213,241,226),Color.rgb(198,230,213),Color.rgb(164,201,185)),null,Shader.TileMode.CLAMP)
    private val blue = LinearGradient(-1f,-.44f,0f,.44f,
        intArrayOf(Color.rgb(128,186,255),Color.rgb(38,130,255),Color.rgb(34,105,210)),null,Shader.TileMode.CLAMP)
    private val white = LinearGradient(0f,-.44f,1f,.44f,
        intArrayOf(Color.WHITE,Color.rgb(247,249,253),Color.rgb(214,223,234)),null,Shader.TileMode.CLAMP)
    private val liquid = LinearGradient(-.6f,-.75f,.6f,.8f,
        intArrayOf(Color.rgb(198,225,255),Color.rgb(159,204,255),Color.rgb(123,172,221)),null,Shader.TileMode.CLAMP)
    private val highlight = RadialGradient(-.22f,-.25f,.12f,Color.argb(95,255,255,255),Color.TRANSPARENT,Shader.TileMode.CLAMP)
    fun draw(canvas: Canvas, l: VitaminCapsuleDoseLayout, f: VitaminCapsuleDoseMotion.Frame) {
        val scale = max(.001, if (f.take > 0) 1 - f.take else f.reform)
        canvas.save(); canvas.translate(l.cx.toFloat(), l.cy.toFloat()); canvas.scale((l.unit * scale).toFloat(),(l.unit * scale).toFloat())
        fun shape(s: Double, opacity: Double) {
            if (opacity <= 0) return
            canvas.saveLayerAlpha(-1.6f,-1.6f,1.6f,1.6f,(opacity * 255).toInt())
            paint.color=Color.WHITE; paint.alpha=255
            if (s < .5) {
                canvas.save(); canvas.rotate(-45f)
                canvas.save(); canvas.clipRect(-1.2f,-.6f,0f,.6f); paint.shader=blue; canvas.drawPath(capsule,paint); canvas.restore()
                canvas.save(); canvas.clipRect(0f,-.6f,1.2f,.6f); paint.shader=white; canvas.drawPath(capsule,paint); canvas.restore()
                canvas.restore()
            } else if (s < 1.5) { paint.shader=mint; canvas.drawOval(-.8f,-.62f,.8f,.62f,paint) }
            else { paint.shader=liquid; canvas.drawPath(drop,paint) }
            paint.shader=highlight; canvas.drawCircle(-.22f,-.25f,.12f,paint)
            paint.shader=null; canvas.restore()
        }
        shape(f.a,1-f.k); shape(f.b,f.k)
        canvas.restore(); paint.shader=null; paint.alpha=255
    }
}
