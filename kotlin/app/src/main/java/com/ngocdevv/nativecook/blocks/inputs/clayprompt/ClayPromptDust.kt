package com.ngocdevv.nativecook.blocks.inputs.clayprompt

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.*

/** CPU transcription of clay-dust.ts: six rotating slots of 256 particles. */
internal class ClayPromptDust {
    private class Particle { var x=0.0;var y=0.0;var vx=0.0;var vy=0.0;var gx=0.0;var gy=0.0;var life=0.0;var age=0.0;var hue=0;var size=0.0;var mode=0;var seed=0.0 }
    private val particles=Array(1536){Particle()}
    private var slot=0
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{xfermode=PorterDuffXfermode(PorterDuff.Mode.ADD)}
    private fun hash(x:Double,y:Double):Double {val v=sin(x*127.1+y*311.7)*43758.5453;return v-floor(v)}
    private fun sm(a:Double,b:Double,x:Double)=ClayPromptMotion.smooth(a,b,x)
    fun emit(fx:Double,fy:Double,tx:Double,ty:Double,kind:Int,time:Double,br:Double){
        for(i in slot*256 until (slot+1)*256){
            val p=particles[i];val fi=i.toDouble();val r1=hash(fi,time);val r2=hash(fi+17,time*1.3);val r3=hash(fi+41,time*0.7)
            val a=r1*6.2831853;val dx=cos(a);val dy=sin(a);val disk=sqrt(r2)
            p.age=0.0;p.life=1.0;p.seed=r3;p.size=1.2+r3*r3*2.6
            if(kind>=0){p.mode=0;p.hue=kind;p.x=fx+dx*disk*12;p.y=fy+dy*disk*12;p.gx=tx+dx*disk*14;p.gy=ty+dy*disk*14
                val norm=hypot(tx-fx+0.001,ty-fy+0.001);val ax=(tx-fx+0.001)/norm;val ay=(ty-fy+0.001)/norm
                p.vx=ax*(120+r2*220)-ay*(r1-0.5)*520;p.vy=ay*(120+r2*220)+ax*(r1-0.5)*520-140-r3*160
            }else{p.mode=1;p.hue=floor(r3*3.999).toInt();val ring=br*(1.25+r2*0.9)+18;p.x=tx+dx*ring;p.y=ty+dy*ring;p.gx=tx;p.gy=ty;p.vx=-dy*(260+r1*200);p.vy=dx*(260+r1*200)}
        };slot=(slot+1)%6
    }
    fun step(dt:Double,g:Double,wipe:Double,bx:Double,by:Double,br:Double){
        for(p in particles){if(p.life<=0)continue;p.age+=dt
            if(p.mode==0){val tx=p.gx-p.x;val ty=p.gy-p.y;val pull=sm(0.0,0.35,p.age)*30
                val wander=1-sm(0.1,0.5,p.age)
                p.vx+=(tx*pull-p.vx*(2.2+pull*0.18)+sin(p.age*9+p.seed*20)*260*wander)*dt
                p.vy+=(ty*pull-p.vy*(2.2+pull*0.18)+cos(p.age*7+p.seed*13)*260*wander)*dt
                val near=sm(26.0,4.0,hypot(tx,ty));p.life=(p.life-dt*(0.45+4*near*sm(0.25,0.4,p.age))).coerceIn(0.0,1.0)
            }else{val ox=p.x-bx;val oy=p.y-by;val r=max(hypot(ox,oy),0.001);val dx=ox/r;val dy=oy/r;val open=sm(0.48,0.62,g)
                val ring=max(br,24.0)*(1.35+0.5*p.seed)*(1-open)+520*(0.6+p.seed)*open
                val swirl=520*(1-open*0.7)/(1+r/140);val hold=(ring-r)*(8+open*10)
                p.vx+=(-dy*swirl+dx*hold-p.vx*2.4)*dt;p.vy+=(dx*swirl+dy*hold-p.vy*2.4)*dt
                p.life=(p.life-dt*(0.35+1.4*open)).coerceIn(0.0,1.0)
            }
            p.x+=p.vx*dt;p.y+=p.vy*dt;p.life*=1-wipe
        }
    }
    private val tints=arrayOf(doubleArrayOf(1.0,0.63,0.35),doubleArrayOf(0.44,0.64,1.0),doubleArrayOf(1.0,0.85,0.42),doubleArrayOf(0.45,0.85,0.51))
    // Exact core/halo radial samples from the WebGPU fragment function, with additive blending.
    private val gradients=Array(4){h->val colors=IntArray(33){i->val r=i/32.0*4;val core=exp(-r*r*1.6);val a=(core+exp(-r*r*0.22)*0.32).coerceAtMost(1.0)
        val c=tints[h];fun channel(v:Double)=((v+(1-v)*core*0.65)*255).roundToInt().coerceIn(0,255)
        android.graphics.Color.argb((a*255).roundToInt(),channel(c[0]),channel(c[1]),channel(c[2]))}
        RadialGradient(0f,0f,4f,colors,null,Shader.TileMode.CLAMP)}
    fun draw(canvas:Canvas){for(p in particles){if(p.life<0.004)continue;val radius=p.size*(0.55+0.45*p.life);canvas.save();canvas.translate(p.x.toFloat(),p.y.toFloat());canvas.scale(radius.toFloat(),radius.toFloat());paint.shader=gradients[p.hue];paint.alpha=(p.life*255).toInt();canvas.drawRect(-4f,-4f,4f,4f,paint);canvas.restore()}}
}
