package com.ngocdevv.nativecook.blocks.pickers.wavefieldpad

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.*

/** CPU approximation for API 26–32 and devices where RuntimeShader creation fails. */
internal class WaveFieldPadFallback {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=1.5f }
    private val shape=Path(); private val path=Path(); private val rect=RectF(24f,356f,378f,710f)
    private fun hash(x:Int,y:Int):Double { val v=sin(x*12.9898+y*78.233)*43758.5453;return v-floor(v) }
    private fun field(x:Double,y:Double,n:Double,m:Double):Double = cos(n*PI*x/354)*cos(m*PI*y/354)-cos(m*PI*x/354)*cos(n*PI*y/354)
    private fun ripple(x:Double,y:Double,r:WaveFieldPadMotion.Ripple):Pair<Double,Double> {
        if(r.amplitude<=.001)return 0.0 to 0.0
        var dx=0.0;var dy=0.0
        val origins=arrayOf(r.x to r.y,-r.x to r.y,(708-r.x) to r.y,r.x to -r.y,r.x to (708-r.y))
        for(i in origins.indices){val o=origins[i];if(i>0&&hypot(o.first-r.x,o.second-r.y)<1)continue;val a=x-o.first;val b=y-o.second;val len=hypot(a,b)+.001;val d=(len-520*r.age)/12;val g=r.amplitude*(if(i==0)1.0 else .55)*exp(-d*d);dx+=a/len*g;dy+=b/len*g}
        return dx to dy
    }
    private fun warp(x:Double,y:Double,f:WaveFieldPadMotion.Frame,p:WaveFieldPadRenderModel.Values):Pair<Double,Double> {
        val dx=x-p.dentX;val dy=y-p.dentY;val e=exp(-(dx*dx+dy*dy)/(2*70*70))*f.depth*1.65/70
        val a=ripple(x,y,f.rip0);val b=ripple(x,y,f.rip1)
        // Keep the CPU fallback in the same sheet space as the AGSL path: the dent and
        // the reflected ripples displace the sampling point away from their source.
        return (x-dx*e-a.first-b.first) to (y-dy*e-a.second-b.second)
    }
    fun draw(canvas:Canvas,f:WaveFieldPadMotion.Frame,reduced:Boolean) {
        val p=WaveFieldPadRenderModel.values(f,1.0,reduced)
        paint.color=0x55000000;canvas.drawRoundRect(RectF(24f,364f,378f,721f),28f,28f,paint)
        shape.reset();val wx=f.wall.u.toFloat();val wy=f.wall.v.toFloat();shape.moveTo(52f,356f)
        shape.cubicTo(130f,356f,272f,356f,350f,356f);shape.quadTo(378f,356f,378f,384f)
        shape.cubicTo(378f+max(0f,wx),450f,378f+max(0f,wx),600f,378f,682f);shape.quadTo(378f,710f,350f,710f)
        shape.cubicTo(272f,710f+max(0f,wy),130f,710f+max(0f,wy),52f,710f);shape.quadTo(24f,710f,24f,682f)
        shape.cubicTo(24f+min(0f,wx),600f,24f+min(0f,wx),450f,24f,384f);shape.quadTo(24f,356f,52f,356f);shape.close()
        paint.color=0xFF232227.toInt();canvas.drawPath(shape,paint);canvas.save();canvas.clipPath(shape)
        paint.alpha=255;paint.shader=RadialGradient((24+p.puckX).toFloat(),(356+p.puckY).toFloat(),160f,intArrayOf(0x38F0801E,0x182A2220,0x00232227),null,Shader.TileMode.CLAMP);canvas.drawRect(rect,paint);paint.shader=null
        stroke.color=0x34EACAA0;stroke.strokeWidth=1.3f
        for(i in 1 until 14)for(vertical in listOf(true,false)){path.reset();for(j in 0..40){val x=if(vertical)i*354.0/14 else j*354.0/40;val y=if(vertical)j*354.0/40 else i*354.0/14;val q=warp(x,y,f,p);if(j==0)path.moveTo((24+q.first).toFloat(),(356+q.second).toFloat()) else path.lineTo((24+q.first).toFloat(),(356+q.second).toFloat())};canvas.drawPath(path,stroke)}
        for(cx in listOf(0.0,354.0)){stroke.color=0x48FFE2B0;stroke.strokeWidth=2f;canvas.drawCircle((24+cx).toFloat(),533f,(hypot(p.puckX-cx,p.puckY-177)-44*f.scale).coerceAtLeast(0.0).toFloat(),stroke);stroke.color=0x38FFE2B0;canvas.drawCircle((24+cx).toFloat(),533f,56f,stroke)}
        // Deterministic grains slide toward nodal lines as settle grows, then move with the membrane.
        for(iy in 0 until 45)for(ix in 0 until 45){
            if(hash(ix,iy)>.67)continue
            val x=(ix+.2+.6*hash(ix+17,iy))*354/45;val y=(iy+.2+.6*hash(ix,iy+31))*354/45
            val z=field(x,y,p.modeN,p.modeM);val e=.5;val gx=(field(x+e,y,p.modeN,p.modeM)-field(x-e,y,p.modeN,p.modeM))/(2*e);val gy=(field(x,y+e,p.modeN,p.modeM)-field(x,y-e,p.modeN,p.modeM))/(2*e);val den=max(gx*gx+gy*gy,.000144)
            val distance=(z/den*f.settle*.7);val length=hypot(gx*distance,gy*distance);val k=if(length>26)26/length else 1.0
            val shimmer=if(reduced)0.0 else (hash(ix+f.jitter.toInt(),iy)-.5)*2
            val q=warp(x-gx*distance*k+shimmer,y-gy*distance*k-shimmer,f,p)
            val near=(1-hypot(q.first-p.puckX,q.second-p.puckY)/140).coerceIn(0.0,1.0);paint.color=Color.argb(150,(246+9*near).toInt(),(226-25*near).toInt(),(190-70*near).toInt());canvas.drawCircle((24+q.first).toFloat(),(356+q.second).toFloat(),.9f,paint)
        }
        stroke.strokeWidth=2f
        for(r in listOf(f.rip0,f.rip1))if(r.amplitude>.01){stroke.color=Color.argb((min(1.0,r.amplitude/10)*130).toInt(),255,201,120);canvas.drawCircle((24+r.x).toFloat(),(356+r.y).toFloat(),(520*r.age).toFloat(),stroke)}
        if(f.ring>0){stroke.color=0x90FFC978.toInt();stroke.strokeWidth=5f;canvas.drawCircle((24+p.puckX).toFloat(),(356+p.puckY).toFloat(),(420*(1-(1-f.ring).pow(1.6))).toFloat(),stroke)}
        canvas.restore();stroke.color=0x45FFE2B0;stroke.strokeWidth=1f;canvas.drawPath(shape,stroke)
    }
}
