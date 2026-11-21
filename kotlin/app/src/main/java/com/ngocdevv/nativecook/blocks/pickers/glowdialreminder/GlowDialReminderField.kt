package com.ngocdevv.nativecook.blocks.pickers.glowdialreminder

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build

/** Molten pigment field. AGSL carries the polar shear above API 33; the fallback preserves its gradient and drain. */
internal class GlowDialReminderField {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val runtime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(AGSL) else null

    fun draw(canvas: Canvas, card: RectF, scale: Float, off: Double, shear: Double, ring: Double) {
        val shader = runtime
        if (shader != null) {
            shader.setFloatUniform("uOff", off.toFloat())
            shader.setFloatUniform("uShear", shear.toFloat())
            shader.setFloatUniform("uRing", ring.toFloat())
            paint.shader = shader
            canvas.drawRect(card, paint)
            paint.shader = null
            return
        }
        paint.color = 0xFFC8A1C0.toInt(); canvas.drawRect(card, paint)
        val strength = (1.0 - off * .86).toFloat()
        paint.shader = RadialGradient(201f, 400f, 212f, intArrayOf(0xFF460C32.toInt(), 0xFFC12F6E.toInt(), 0xFFC8A1C0.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = (255 * strength).toInt(); canvas.drawOval(RectF(31f, 164f, 371f, 636f), paint)
        paint.alpha = 255; paint.shader = null
        if (off > .01) { paint.color = 0x775A1D45; canvas.drawCircle(306f, 680f, (41 + 12 * off).toFloat(), paint) }
    }

    private companion object {
        const val AGSL = """
            uniform float uOff;
            uniform float uShear;
            uniform float uRing;
            float3 c(float r, float g, float b) { return float3(r,g,b)/255.0; }
            float hash(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
            float noise(float2 p) { float2 i=floor(p),f=fract(p); f=f*f*(3.0-2.0*f); return mix(mix(hash(i),hash(i+float2(1,0)),f.x),mix(hash(i+float2(0,1)),hash(i+float2(1,1)),f.x),f.y); }
            half4 main(float2 p0) {
              float2 p = p0;
              float2 q=(p-float2(201,400))/float2(170,236);
              float a=atan(q.y,q.x), r=length(q);
              float turn=uShear*2.7*exp(-r*1.3);
              q=float2(cos(a-turn),sin(a-turn))*r;
              float n=(noise(q*5.0)-.5)*.10;
              float field=length(q)*(1.0+n+.12*uShear*sin(a*2.0+r*4.0));
              float3 edge=c(200,161,192), hot=c(212,88,158), core=c(70,12,50);
              float3 col=mix(hot,edge,smoothstep(.45,1.0,field));
              col=mix(core,col,smoothstep(.0,.46,field));
              col=mix(edge,col,1.0-uOff*.84);
              float rr=abs(field-(.55+.28*uRing));
              col=mix(col,c(255,230,244),exp(-rr*rr*900.0)*uRing*.24);
              float h=length(p-float2(306,680));
              col=mix(col,mix(c(90,29,69),hot,.45), (1.0-smoothstep(22.,52.,h))*uOff*.86);
              return half4(half3(col),1.0);
            }
        """
    }
}
